package uz.dukeengine.render;

import com.jme3.light.LightList;
import com.jme3.material.MatParam;
import com.jme3.material.MatParamTexture;
import com.jme3.material.Material;
import com.jme3.material.TechniqueDef;
import com.jme3.math.Matrix4f;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Caps;
import com.jme3.renderer.GeometryBatcher;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.queue.GeometryList;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.instancing.InstancedGeometry;
import com.jme3.util.ListMap;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Draws the geometries of a list that share a mesh and a material (the same one, or one with every parameter the same)
 * and take the same lights as one instanced call, whatever the game did to build its scene: the opaque bucket of every
 * view and every shadow map's casters pass through it, each with the geometries it culled. The list is the culling: a
 * geometry out of the view, or of the light, is not in it, and the instances of a call are exactly those that would have
 * been drawn one by one. Their matrices are read from the geometries at the call, so what moves, moves.
 *
 * <p>What is batched is what the instanced shader draws as the plain one does: a material with {@code UseInstancing}
 * (set true for the call, and left false) whose technique for this pass has the {@code INSTANCING} define; a mesh that
 * is not skinned, morphed or instanced; a geometry with no override of material parameters and a positive uniform
 * scale, as the instance's normal is turned by a quaternion. Everything else, and a model that stands fewer than
 * {@link #MINIMUM} times, is drawn as it is. A pass that forces a material batches only if that material takes
 * {@code UseInstancing} too ({@link SceneBuffers} does); one that does not (the shadow maps of a material with no
 * {@code PreShadow} technique) draws one by one.
 *
 * <p>shortcut: the lights of each geometry are found as for a call of its own, so a scene of many lights near its
 * things pays the filter for each as before, and every instance's matrix is written anew at each call, a still scene's too: sort the geometries
 * by cell and keep a cell's buffer until one of its geometries moves, when a list of tens of thousands costs more than the calls saved. {@link SceneBuffers} draws what moved since the last frame by itself, for its motion.
 */
public final class AutoInstancing implements GeometryBatcher {

    /** Fewer of one model than this are drawn one by one: the instanced shader is a program switch of its own. */
    static final int MINIMUM = 4;
    /**
     * How many materials that differ one model may have, and still be batched by the ones the first met. shortcut: the others are
     * drawn one by one, a model tinted differently each time; give such a model one material and a colour per instance when it matters.
     */
    private static final int KINDS = 4;
    /**
     * What is left on a material by drawing it, and is not what it is: the motion {@link SceneBuffers} told of the geometry it drew last,
     * and the switch to instances of this class.
     */
    private static final Set<String> LEFT_BY_DRAWING = Set.of("PreviousWorldMatrix", "PreviousViewProjection", "PreviousBoneMatrices", "UseInstancing");

    private static final String PARAM = "UseInstancing";
    private static final int NO = 0;
    private static final int OWN = 1;
    private static final int FORCED = 2;

    private final Pen pen = new Pen();
    private final Map<Group, Group> groups = new HashMap<>();
    private final List<Group> live = new ArrayList<>();
    private final List<Group> spareGroups = new ArrayList<>();
    private final List<Lot> spareLots = new ArrayList<>();
    private final Group probe = new Group();
    private final LightList scratch = new LightList(null);
    private final float[] one = new float[16];
    private Lot[] lotOf = new Lot[256];
    private Predicate<Geometry> apart;
    private String technique;
    private Material forced;
    private boolean busy;

    private AutoInstancing() {
    }

    /** Whether the GPU draws instances. */
    public static boolean supported(EnumSet<Caps> caps) {
        return caps.contains(Caps.MeshInstancing);
    }

    /** The batcher of this manager, made if it has none. Call it only where {@link #supported}. */
    public static AutoInstancing on(RenderManager renderManager) {
        if (renderManager.getGeometryBatcher() instanceof AutoInstancing found) {
            return found;
        }
        var made = new AutoInstancing();
        renderManager.setGeometryBatcher(made);
        return made;
    }

    /** The geometries this is true of are drawn one by one (those whose motion a pass tells apart); null, none are. */
    void apart(Predicate<Geometry> apart) {
        this.apart = apart;
    }

    @Override
    public void render(GeometryList list, RenderManager rm) {
        int n = list.size();
        // A list drawn while another is (a pass that draws a pass) is drawn as it is: the groups are one at a time.
        if (n < MINIMUM || busy) {
            for (int i = 0; i < n; i++) {
                rm.renderGeometry(list.get(i));
            }
            return;
        }
        if (lotOf.length < n) {
            lotOf = new Lot[Math.max(n, lotOf.length * 2)];
        }
        Arrays.fill(lotOf, 0, n, null);
        technique = rm.getForcedTechnique();
        forced = rm.getForcedMaterial();
        busy = true;
        try {
            group(list, n);
            split(list, rm);
            for (int i = 0; i < n; i++) {
                var lot = lotOf[i];
                if (lot == null) {
                    rm.renderGeometry(list.get(i));
                } else if (!lot.batched()) {
                    rm.renderGeometry(list.get(i), lot.lights);
                } else if (lot.at[0] == i) {
                    batch(lot, list, rm);
                }
            }
        } finally {
            release();
            busy = false;
        }
    }

    /** Sorts the geometries into groups of one mesh and level of detail. */
    private void group(GeometryList list, int n) {
        for (int i = 0; i < n; i++) {
            var geometry = list.get(i);
            if (geometry instanceof InstancedGeometry || geometry.isIgnoreTransform()) {
                continue;
            }
            probe.mesh = geometry.getMesh();
            probe.lod = geometry.getLodLevel();
            var group = groups.get(probe);
            if (group == null) {
                group = spareGroups.isEmpty() ? new Group() : spareGroups.removeLast();
                group.mesh = probe.mesh;
                group.lod = probe.lod;
                groups.put(group, group);
                live.add(group);
            }
            group.add(i);
        }
    }

    /**
     * A group that stands often enough is split into lots: the geometries of one material (the first it meets, or one equal
     * to it) that take the same lights. A lot of enough of them is drawn at once.
     */
    private void split(GeometryList list, RenderManager rm) {
        for (var group : live) {
            if (group.count < MINIMUM) {
                continue;
            }
            for (int k = 0; k < group.count; k++) {
                int i = group.at[k];
                var geometry = list.get(i);
                // The questions a geometry of a model that stands once is not asked are asked here.
                if (!geometry.getWorldMatParamOverrides().isEmpty() || !uniform(geometry.getWorldTransform().getScale())) {
                    continue;
                }
                var kind = group.kindOf(geometry.getMaterial(), technique, forced);
                if (kind == null || geometry.getBatchHint() == Spatial.BatchHint.Never || (apart != null && apart.test(geometry))) {
                    continue;
                }
                var lights = rm.lightsFor(geometry, scratch);
                Lot lot = null;
                for (var candidate : group.lots) {
                    if (candidate.kind == kind && candidate.sameLights(lights)) {
                        lot = candidate;
                        break;
                    }
                }
                if (lot == null) {
                    lot = spareLots.isEmpty() ? new Lot() : spareLots.removeLast();
                    lot.group = group;
                    lot.kind = kind;
                    lot.copyLights(lights);
                    group.lots.add(lot);
                }
                lot.add(i);
                lotOf[i] = lot;
            }
        }
    }

    private void batch(Lot lot, GeometryList list, RenderManager rm) {
        pen.use(lot.group, lot.kind.material, lot.count);
        var instances = pen.getTransformUserInstanceData();
        var fb = (FloatBuffer) instances.getData();
        fb.clear();
        for (int k = 0; k < lot.count; k++) {
            put(list.get(lot.at[k]), fb);
        }
        fb.flip();
        instances.updateData(fb);
        var material = lot.kind.material;
        var through = lot.kind.how == FORCED ? forced : null;
        material.setBoolean(PARAM, true);
        if (through != null) {
            through.setBoolean(PARAM, true);
        }
        try {
            rm.renderGeometry(pen, lot.lights);
        } finally {
            material.setBoolean(PARAM, false);
            if (through != null) {
                through.setBoolean(PARAM, false);
            }
        }
    }

    /** What the vertex shader reads: the world matrix by columns, and in the fourth row the quaternion that turns a normal. */
    private void put(Geometry geometry, FloatBuffer fb) {
        Matrix4f m = geometry.getWorldMatrix();
        Quaternion q = geometry.getWorldRotation();
        one[0] = m.m00;
        one[1] = m.m10;
        one[2] = m.m20;
        one[3] = -q.getX();
        one[4] = m.m01;
        one[5] = m.m11;
        one[6] = m.m21;
        one[7] = -q.getY();
        one[8] = m.m02;
        one[9] = m.m12;
        one[10] = m.m22;
        one[11] = -q.getZ();
        one[12] = m.m03;
        one[13] = m.m13;
        one[14] = m.m23;
        one[15] = q.getW();
        fb.put(one);
    }

    private void release() {
        for (var group : live) {
            for (var lot : group.lots) {
                lot.clear();
                spareLots.add(lot);
            }
            group.clear();
            spareGroups.add(group);
        }
        live.clear();
        groups.clear();
    }

    /** Whether a mesh and a material are drawn as the pass would draw them, instanced: not at all, by their own technique, or by the forced material. */
    static int how(Material material, Mesh mesh, String technique, Material forced) {
        var def = material.getMaterialDef();
        if (def.getMaterialParam(PARAM) == null || Boolean.TRUE.equals(material.getParamValue(PARAM))) {
            return NO;
        }
        if (mesh.isAnimated() || mesh.hasMorphTargets() || mesh.getInstanceCount() > 1 || mesh.getBuffer(VertexBuffer.Type.InstanceData) != null) {
            return NO;
        }
        if (technique != null) {
            var own = def.getTechniqueDefs(technique);
            if (own != null) {
                return instances(own) ? OWN : NO;
            }
        } else if (forced == null) {
            return instances(def.getTechniqueDefs(TechniqueDef.DEFAULT_TECHNIQUE_NAME)) ? OWN : NO;
        }
        return forced != null && forced.getMaterialDef().getMaterialParam(PARAM) != null ? FORCED : NO;
    }

    private static boolean instances(List<TechniqueDef> defs) {
        if (defs == null || defs.isEmpty()) {
            return false;
        }
        for (var def : defs) {
            if (def.getShaderParamDefineId(PARAM) == null) {
                return false;
            }
        }
        return true;
    }

    /** The normal of an instance is turned by a quaternion, which holds for a scale that is the same on every axis and not a mirror. */
    static boolean uniform(Vector3f scale) {
        return scale.x > 0f && Math.abs(scale.x - scale.y) <= 1e-5f * scale.x && Math.abs(scale.x - scale.z) <= 1e-5f * scale.x;
    }

    /** Whether two materials draw the same: one definition and render state, every parameter equal (a texture, the same one). */
    static boolean same(Material a, Material b) {
        if (a.getMaterialDef() != b.getMaterialDef()) {
            return false;
        }
        var state = a.getAdditionalRenderState();
        if (state != b.getAdditionalRenderState() && !state.equals(b.getAdditionalRenderState())) {
            return false;
        }
        var x = a.getParamsMap();
        var y = b.getParamsMap();
        int n = x.size();
        int m = y.size();
        int i = 0;
        int j = 0;
        // A copy keeps its parameters in their order and by the same names: the usual case, walked side by side.
        while (true) {
            while (i < n && left(x.getKey(i))) {
                i++;
            }
            while (j < m && left(y.getKey(j))) {
                j++;
            }
            if (i == n || j == m) {
                return i == n && j == m;
            }
            var p = x.getValue(i++);
            var q = y.getValue(j++);
            if (p.getName() != q.getName()) {
                return sameInAnyOrder(x, y);
            }
            if (p.getValue() != q.getValue() && !same(p, q)) {
                return false;
            }
        }
    }

    /** Whether the names are the same and the values equal, wherever each is in its material. */
    private static boolean sameInAnyOrder(ListMap<String, MatParam> x, ListMap<String, MatParam> y) {
        int compared = 0;
        for (int i = 0; i < x.size(); i++) {
            var param = x.getValue(i);
            if (!left(param.getName())) {
                var other = y.get(param.getName());
                if (other == null || !same(param, other)) {
                    return false;
                }
                compared++;
            }
        }
        for (int i = 0; i < y.size(); i++) {
            if (!left(y.getKey(i))) {
                compared--;
            }
        }
        return compared == 0;
    }

    private static boolean left(String name) {
        char first = name.charAt(0);
        return (first == 'P' || first == 'U') && LEFT_BY_DRAWING.contains(name);
    }

    private static boolean same(MatParam a, MatParam b) {
        if (a.getVarType() != b.getVarType()) {
            return false;
        }
        if (a instanceof MatParamTexture || b instanceof MatParamTexture) {
            return a instanceof MatParamTexture && b instanceof MatParamTexture && a.getValue() == b.getValue();
        }
        return Objects.equals(a.getValue(), b.getValue());
    }

    /** One mesh at one level of detail, and the places in the list of the geometries that are it. */
    private static final class Group {
        Mesh mesh;
        int lod;
        int[] at = new int[8];
        int count;
        final List<Kind> kinds = new ArrayList<>(1);
        final List<Lot> lots = new ArrayList<>(1);

        void add(int index) {
            if (count == at.length) {
                at = Arrays.copyOf(at, count * 2);
            }
            at[count++] = index;
        }

        /** The kind of this material, found or made; null where it is not drawn instanced, or is one kind too many. */
        Kind kindOf(Material material, String technique, Material forced) {
            for (var kind : kinds) {
                if (kind.material == material || same(kind.material, material)) {
                    return kind.how == NO ? null : kind;
                }
            }
            if (kinds.size() == KINDS) {
                return null;
            }
            var kind = new Kind(material, how(material, mesh, technique, forced));
            kinds.add(kind);
            return kind.how == NO ? null : kind;
        }

        void clear() {
            mesh = null;
            count = 0;
            kinds.clear();
            lots.clear();
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(mesh) * 31 + lod;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Group g && g.mesh == mesh && g.lod == lod;
        }
    }

    /** A material as drawn instanced, and how. */
    private record Kind(Material material, int how) {
    }

    /** The geometries of a group that are of one kind and take the same lights. */
    private static final class Lot {
        Group group;
        Kind kind;
        final LightList lights = new LightList(null);
        int[] at = new int[8];
        int count;

        boolean batched() {
            return count >= MINIMUM;
        }

        void add(int index) {
            if (count == at.length) {
                at = Arrays.copyOf(at, count * 2);
            }
            at[count++] = index;
        }

        boolean sameLights(LightList other) {
            if (other.size() != lights.size()) {
                return false;
            }
            for (int i = 0; i < other.size(); i++) {
                if (other.get(i) != lights.get(i)) {
                    return false;
                }
            }
            return true;
        }

        void copyLights(LightList from) {
            for (int i = 0; i < from.size(); i++) {
                lights.add(from.get(i));
            }
        }

        void clear() {
            group = null;
            kind = null;
            lights.clear();
            count = 0;
        }
    }

    /** What is drawn for a lot: its mesh with its material, the matrices of the lot as the instances. */
    private static final class Pen extends InstancedGeometry {
        private int instances;

        Pen() {
            super("auto instances");
        }

        void use(Group group, Material material, int count) {
            if (getMaxNumInstances() < count) {
                setMaxNumInstances(Math.max(count, getMaxNumInstances() * 2));
            }
            setMesh(group.mesh);
            setMaterial(material);
            lodLevel = group.lod;
            instances = count;
        }

        @Override
        public int getNumVisibleInstances() {
            return instances;
        }
    }
}
