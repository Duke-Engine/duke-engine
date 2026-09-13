package uz.duke.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingSphere;
import com.jme3.light.PointLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Plays an effect's layers at the moment something happened, and puts everything
 * away when it is over.
 *
 * <p><b>Drawing only, and late.</b> The simulation decides that a skill went off,
 * where, and what it hit; this hangs a picture on that afterwards. Nothing here is
 * ever read back, every number may be as random as it likes, and a client that
 * drew nothing would play exactly the same game.
 *
 * <p><b>Three moments, and every layer belongs to one of them.</b> A skill is
 * CAST, and every layer of its look plays at the cast. A projectile FLIES, and its
 * trails, the glow it carries and the light it throws play for as long as it is in
 * the air; when it LANDS the rest of its layers — the burst, the flash, the ring —
 * play where it came down. Which layer is which follows from its type, so the
 * settings file never has to say.
 *
 * <p><b>Budgets, not targets.</b> Past the particle ceiling a new layer is drawn
 * with fewer particles and past the light ceiling it burns without lighting the
 * floor; anything too far off, under the fog, or off the edge of the screen is not
 * started at all. What is lost is always decoration and never an effect's meaning:
 * the ring still opens, the cast still happened.
 *
 * <p><b>Nothing outlives its moment.</b> A layer that has finished goes back to the
 * pool it came from and its light is put out. A world being rebuilt takes back
 * everything at once. The scene is the same size after an hour as after a minute.
 */
final class LayeredEffects {

    private static final Logger LOG = Logger.getLogger(LayeredEffects.class.getName());

    /** A creature nobody named. */
    static final int NOBODY = 0;

    /** The smallest pool a layer is lent from; the rest double. */
    private static final int SMALLEST = 4;

    /** What the effects need to know about the world they are drawn in. */
    interface Surroundings {

        /** The height of the floor under a point. */
        float floorAt(float x, float z);

        /** Where a creature stands this frame, on the floor, or null once it is gone. */
        Vector3f whereIs(int unitId);

        /** The other side's creatures within reach of a spot, as the caster's enemies. */
        int[] enemiesNear(float x, float z, float radius, int caster);

        /** Whether the player can see a spot right now. */
        boolean canSee(float x, float z);
    }

    /**
     * What a cast knows about itself.
     *
     * @param spot     where it happened, on the floor
     * @param from     where a run or a blink began, or null for a cast that did not
     *                 go anywhere
     * @param to       and where it ended
     * @param facingX  which way it went, across the floor — from the caster to the
     *                 spot, or along the run
     * @param facingZ  the other half of that
     * @param radius   how far the skill reached, or 0
     * @param on       whose it is, for a layer that follows somebody
     * @param by       who cast it
     */
    record Moment(Vector3f spot, Vector3f from, Vector3f to, float facingX, float facingZ,
            float radius, int on, int by) {

        boolean hasPath() {
            return from != null && to != null;
        }
    }

    private final AssetManager assets;
    private final Node root;
    private final Visuals visuals;
    private final LightPool lights;
    private final Surroundings world;
    private final int maxParticles;
    private final float maxDistance;
    private final Random dice;

    private final Map<Integer, Deque<ParticleLayer>> spare = new HashMap<>();
    private final List<Playing> playing = new ArrayList<>();
    private final Map<String, Texture> textures = new HashMap<>();
    private final Set<String> missing = new HashSet<>();
    private final Set<String> unmeasured = new HashSet<>();
    private Texture dot;
    private int particlesInUse;
    private int layersMade;

    /** One layer burning. */
    private static final class Playing {
        private final EffectLayer layer;
        private final ParticleLayer drawn;
        private final PointLight light;
        /** For an aura: what it is, on whom, so a second cast does not stack a second. */
        private final String key;
        private float clock;
        /** How long it lasts once started: an aura's run, a mark's stay, a path's laying. */
        private float span;
        /** When, after starting, it is finished and can be put away. */
        private float until;
        private int follows = NOBODY;
        private Spatial rides;
        private Vector3f offset = Vector3f.ZERO;
        private Vector3f anchor;
        private Vector3f lastAnchor;
        private float facingX;
        private float facingZ = 1f;
        private boolean emitting;
        private float owed;
        private int next;
        private float fall;
        private boolean local;
        private int projectile = NOBODY;
        /** What one of its sizes is: 1 for units, or the skill's reach. */
        private float unit = 1f;
        /** Everywhere a fed layer has let particles out, for the camera to test. */
        private BoundingBox bound;

        Playing(EffectLayer layer, ParticleLayer drawn, PointLight light, String key) {
            this.layer = layer;
            this.drawn = drawn;
            this.light = light;
            this.key = key;
        }
    }

    LayeredEffects(AssetManager assets, Node root, Visuals visuals, LightPool lights,
            Surroundings world, int maxParticles, float maxDistance) {
        this(assets, root, visuals, lights, world, maxParticles, maxDistance, new Random());
    }

    LayeredEffects(AssetManager assets, Node root, Visuals visuals, LightPool lights,
            Surroundings world, int maxParticles, float maxDistance, Random dice) {
        this.assets = assets;
        this.root = root;
        this.visuals = visuals;
        this.lights = lights;
        this.world = world;
        this.maxParticles = Math.max(0, maxParticles);
        this.maxDistance = maxDistance;
        this.dice = dice;
    }

    // ---- the three moments ----

    /** A skill went off: every layer of its look plays now. */
    void cast(String recipeName, Moment moment, Camera camera) {
        cast(recipeName, moment, camera, 1f);
    }

    /**
     * The same, drawn bigger or smaller than it is written: its sizes, its reach and
     * a pillar's height, all times {@code scale}. What lets a level and a boss falling
     * be one look at two sizes, rather than the same blocks written out twice.
     */
    void cast(String recipeName, Moment moment, Camera camera, float scale) {
        var recipe = visuals.effectNamed(recipeName);
        if (recipe == null || moment == null || moment.spot() == null || scale <= 0f) {
            return;
        }
        var layers = recipe.getLayers();
        for (int index = 0; index < layers.size(); index++) {
            var layer = layers.get(index);
            for (var place : placesFor(layer, moment)) {
                start(recipeName, index, layer, place, moment, camera, NOBODY, null, scale);
            }
        }
    }

    /**
     * A projectile is in the air: its trails, the glow it carries and its light
     * start, and go where it goes.
     */
    void flying(int projectileId, String recipeName, Spatial node, Vector3f offset,
            Camera camera) {
        var recipe = visuals.effectNamed(recipeName);
        if (recipe == null || node == null) {
            return;
        }
        var layers = recipe.getLayers();
        var at = node.localToWorld(offset == null ? Vector3f.ZERO : offset, null);
        var moment = new Moment(at, null, null, 0f, 1f, 0f, NOBODY, NOBODY);
        for (int index = 0; index < layers.size(); index++) {
            var layer = layers.get(index);
            if (!inFlight(layer)) {
                continue;
            }
            var place = new Place(at, NOBODY);
            start(recipeName, index, layer, place, moment, camera, projectileId,
                    new Riding(node, offset == null ? Vector3f.ZERO : offset.clone()), 1f);
        }
    }

    /**
     * It came down: what it carried stops being fed and burns out where it is, and
     * the rest of its layers play at the spot.
     */
    void landed(int projectileId, String recipeName, Vector3f where, Camera camera) {
        grounded(projectileId);
        var recipe = visuals.effectNamed(recipeName);
        if (recipe == null || where == null) {
            return;
        }
        var layers = recipe.getLayers();
        var moment = new Moment(where, null, null, 0f, 1f, 0f, NOBODY, NOBODY);
        for (int index = 0; index < layers.size(); index++) {
            var layer = layers.get(index);
            if (inFlight(layer)) {
                continue;
            }
            for (var place : placesFor(layer, moment)) {
                start(recipeName, index, layer, place, moment, camera, NOBODY, null, 1f);
            }
        }
    }

    /**
     * It left without landing — walked out of the light, or was never going to
     * burst. What it was trailing finishes burning where it is.
     */
    void grounded(int projectileId) {
        if (projectileId == NOBODY) {
            return;
        }
        for (var going : playing) {
            if (going.projectile == projectileId && going.rides != null) {
                stopFeeding(going);
                going.rides = null;
            }
        }
    }

    /**
     * Which layers a projectile carries while it flies, rather than leaves where it
     * lands: anything let out behind it, anything wrapped round it, and a light with
     * no time of its own.
     */
    static boolean inFlight(EffectLayer layer) {
        return EffectLayer.TRAIL.equals(layer.type()) && !EffectLayer.PATH.equals(layer.at())
                || EffectLayer.AURA.equals(layer.type())
                || EffectLayer.LIGHT.equals(layer.type()) && layer.seconds() <= 0f;
    }

    // ---- starting a layer ----

    private record Place(Vector3f at, int follows) {
    }

    private record Riding(Spatial node, Vector3f offset) {
    }

    /** Every place one layer happens for this moment — one, two, or one per enemy caught. */
    private List<Place> placesFor(EffectLayer layer, Moment moment) {
        var spot = floored(moment.spot());
        return switch (layer.at()) {
            case EffectLayer.FROM -> List.of(new Place(moment.hasPath()
                    ? floored(moment.from()) : spot, NOBODY));
            case EffectLayer.TO -> List.of(new Place(moment.hasPath()
                    ? floored(moment.to()) : spot, moment.on()));
            case EffectLayer.BOTH -> moment.hasPath()
                    ? List.of(new Place(floored(moment.from()), NOBODY),
                            new Place(floored(moment.to()), moment.on()))
                    : List.of(new Place(spot, moment.on()));
            case EffectLayer.CASTER -> {
                var caster = world == null ? null : world.whereIs(moment.by());
                yield List.of(new Place(caster != null ? caster : spot, moment.by()));
            }
            case EffectLayer.CAUGHT -> {
                var caught = new ArrayList<Place>();
                if (world != null) {
                    for (int id : world.enemiesNear(spot.x, spot.z, moment.radius(),
                            moment.by())) {
                        var where = world.whereIs(id);
                        if (where != null) {
                            caught.add(new Place(where, id));
                        }
                    }
                }
                yield caught;
            }
            default -> List.of(new Place(spot, moment.on()));
        };
    }

    private void start(String recipeName, int index, EffectLayer layer, Place place,
            Moment moment, Camera camera, int projectile, Riding riding, float scale) {
        float unit = unitFor(recipeName, layer, moment) * scale;
        if (!worthStarting(layer, unit, place.at(), camera)) {
            return;
        }
        boolean lasting = layer.lasting();
        boolean aura = EffectLayer.AURA.equals(layer.type());
        // An aura goes where its man goes, always; anything else only if it says so.
        boolean wears = aura || layer.follows();
        boolean follows = wears && place.follows() != NOBODY || riding != null;
        String key = aura && place.follows() != NOBODY
                ? recipeName + '#' + index + '@' + place.follows() : null;
        if (key != null && alreadyBurning(key)) {
            // The whirlwind is drawn again on every blow it lands, and every one of
            // those arrives as a cast. The aura it wears is the whole run's, from
            // the first -- a second one stacked on it would be twice as bright and
            // would outlast the skill by however long the blows went on.
            return;
        }

        ParticleLayer drawn = null;
        if (layer.draws()) {
            int wanted = layer.count();
            int room = maxParticles - particlesInUse;
            if (room <= 0) {
                return; // over the ceiling: this layer goes undrawn, the rest may not
            }
            drawn = borrow(Math.min(wanted, room));
            drawn.dress(layer, textureFor(layer.texture()));
        }
        PointLight light = null;
        if (EffectLayer.LIGHT.equals(layer.type())) {
            light = lights == null ? null : lights.take();
            if (light == null) {
                return; // every light is burning: this one glows in nobody's eyes
            }
        }

        var going = new Playing(layer, drawn, light, key);
        going.projectile = projectile;
        going.unit = unit;
        going.span = layer.seconds() > 0f ? layer.seconds()
                : visuals.getEffectSeconds(recipeName) > 0f
                        ? visuals.getEffectSeconds(recipeName) : layer.lifeMax();
        going.facingX = moment.facingX();
        going.facingZ = moment.facingZ();
        normaliseFacing(going);
        going.anchor = place.at().clone();
        going.lastAnchor = going.anchor.clone();
        if (riding != null) {
            going.rides = riding.node();
            going.offset = riding.offset();
        } else if (follows) {
            going.follows = place.follows();
        }
        going.local = wears && (going.follows != NOBODY || going.rides != null);
        going.emitting = layer.continuous();
        going.fall = layerFall(layer);
        going.until = untilFor(going, lasting);

        if (drawn != null) {
            lay(going, moment);
            root.attachChild(drawn.geometry());
            drawn.show(layer.delay() <= 0f);
        }
        playing.add(going);
        place(going, 0f);
    }

    /**
     * What one of a layer's sizes is: a unit, or the skill's reach.
     *
     * <p>The reach is the cast's when the cast knows it, and otherwise the one the
     * game gave this look -- a projectile landing does not carry the radius of the
     * blast it makes, but the skill that threw it does.
     */
    private float unitFor(String recipeName, EffectLayer layer, Moment moment) {
        if (!EffectLayer.REACH.equals(layer.measure())) {
            return 1f;
        }
        float reach = moment.radius() > 0f ? moment.radius() : visuals.getEffectReach(recipeName);
        if (reach > 0f) {
            return reach;
        }
        if (unmeasured.add(recipeName)) {
            LOG.warning(() -> recipeName + " has a layer measured in reach and nothing says"
                    + " how far it reaches -- drawn in units");
        }
        return 1f;
    }

    /** How long after starting it is over, as far as can be known now. */
    private static float untilFor(Playing going, boolean lasting) {
        var layer = going.layer;
        if (going.rides != null) {
            return Float.POSITIVE_INFINITY; // for as long as the thing flies
        }
        if (EffectLayer.TRAIL.equals(layer.type()) && EffectLayer.PATH.equals(layer.at())) {
            return going.span + layer.lifeMax();
        }
        if (layer.continuous()) {
            return going.span + layer.lifeMax();
        }
        if (lasting) {
            return going.span;
        }
        return layer.lifeMax();
    }

    /**
     * How far above its spot a layer starts, if it falls. Only what is carried can
     * fall -- a trail, a glow, a light -- since a burst is over before it could.
     */
    private static float layerFall(EffectLayer layer) {
        return EffectLayer.TRAIL.equals(layer.type()) || EffectLayer.AURA.equals(layer.type())
                || EffectLayer.LIGHT.equals(layer.type()) ? layer.fall() : 0f;
    }

    private boolean worthStarting(EffectLayer layer, float unit, Vector3f at, Camera camera) {
        if (at == null) {
            return false;
        }
        if (world != null && !inSight(at, reachOf(layer, unit) * 0.5f)) {
            return false; // under the fog: what nobody can see costs nothing
        }
        if (camera == null) {
            return true;
        }
        if (maxDistance > 0f && at.distanceSquared(camera.getLocation())
                > maxDistance * maxDistance) {
            return false; // two rooms away is a pixel, and the budget is the near fight's
        }
        if (layer.lasting()) {
            return true; // an aura may walk on screen; a burst that starts off it never will
        }
        var sphere = new BoundingSphere(reachOf(layer, unit), at);
        return camera.contains(sphere) != Camera.FrustumIntersect.Outside;
    }

    /**
     * Whether any of it would be in sight: its middle, or anywhere round it about as
     * far as most of it reaches. A fireball that bursts just behind a pillar still
     * throws its fire past the pillar -- and one that bursts where nobody can see
     * any of it still costs nothing.
     */
    private boolean inSight(Vector3f at, float around) {
        if (world.canSee(at.x, at.z)) {
            return true;
        }
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0;
            if (world.canSee(at.x + (float) Math.cos(angle) * around,
                    at.z + (float) Math.sin(angle) * around)) {
                return true;
            }
        }
        return false;
    }

    /**
     * How far anything a layer lets out can get from where it was born.
     *
     * @param unit what its sizes are counted in -- see {@link #unitFor}
     */
    static float reachOf(EffectLayer layer, float unit) {
        float life = layer.lifeMax();
        // Either way: a speed drawn inwards reaches as far as one thrown out.
        float thrown = Math.max(Math.abs(layer.speedMin()), Math.abs(layer.speedMax())) * life;
        float fell = Math.abs(layer.gravity()) * life * life * 0.5f;
        float size = Math.max(layer.sizeStart(), layer.sizeEnd()) * (1f + layer.sizeJitter())
                * unit;
        // A pillar's height is one of its sizes, and is counted as they are.
        float high = Math.abs(layer.height())
                * (EffectLayer.PILLAR.equals(layer.type()) ? unit : 1f);
        return thrown + fell + size + layer.radius() * unit + high + layer.fall() + 1f;
    }

    // ---- laying particles ----

    /** Write the particles a layer starts with: all of them, or none for one fed over time. */
    private void lay(Playing going, Moment moment) {
        var layer = going.layer;
        var drawn = going.drawn;
        if (EffectLayer.PILLAR.equals(layer.type())) {
            layPillar(going);
            drawn.reach(going.local ? Vector3f.ZERO : going.anchor, reachOf(layer, going.unit));
            drawn.upload();
            return;
        }
        if (layer.continuous() && !(EffectLayer.TRAIL.equals(layer.type())
                && EffectLayer.PATH.equals(layer.at()))) {
            drawn.reach(going.local ? Vector3f.ZERO : going.anchor, reachOf(layer, going.unit));
            drawn.upload();
            return; // it lets them out as it goes
        }
        int count = drawn.capacity;
        boolean path = EffectLayer.PATH.equals(layer.at()) && moment.hasPath();
        for (int i = 0; i < count; i++) {
            float along = count <= 1 ? 0f : i / (float) (count - 1);
            Vector3f born;
            float birth;
            if (path) {
                var from = floored(moment.from());
                var to = floored(moment.to());
                born = new Vector3f(FastMath.interpolateLinear(along, from.x, to.x), 0f,
                        FastMath.interpolateLinear(along, from.z, to.z));
                born.y = floorUnder(born.x, born.z);
                // Laid in the order he ran it, so the dust rises behind him.
                birth = going.span * along;
            } else {
                born = going.local ? new Vector3f() : going.anchor.clone();
                birth = 0f;
            }
            float life = lasting(going) && !layer.continuous()
                    ? going.span : between(layer.lifeMin(), layer.lifeMax());
            birthOne(going, i, born, birth, life, moment);
        }
        if (EffectLayer.BEAM.equals(layer.type()) && moment.hasPath()) {
            layBeam(going, moment);
        }
        drawn.reach(going.local ? Vector3f.ZERO : going.anchor, reachOf(layer, going.unit)
                + (moment.hasPath() ? moment.from().distance(moment.to()) : 0f));
        drawn.upload();
    }

    private boolean lasting(Playing going) {
        var type = going.layer.type();
        return EffectLayer.AURA.equals(type) || EffectLayer.MARK.equals(type);
    }

    /**
     * A pillar stands on the floor under where it happened -- as many columns as the
     * layer asks for and no more, because a column is light, and two drawn over one
     * another are twice as bright as the file said.
     */
    private void layPillar(Playing going) {
        var layer = going.layer;
        float way = EffectLayer.DOWN.equals(layer.direction()) ? -1f : 1f;
        var foot = going.local ? new Vector3f() : going.anchor;
        int columns = Math.min(layer.count(), going.drawn.capacity);
        for (int i = 0; i < columns; i++) {
            float life = between(layer.lifeMin(), layer.lifeMax());
            going.drawn.put(i, foot.x, foot.y, foot.z, 0f, 0f, 0f, 0f, life, dice.nextFloat(),
                    jitter(layer.sizeJitter()) * going.unit, 0f, way, 0f,
                    layer.height() * going.unit);
        }
    }

    /** A beam is laid once along the whole of what it joins. */
    private void layBeam(Playing going, Moment moment) {
        var from = floored(moment.from());
        var to = floored(moment.to());
        var along = to.subtract(from);
        float length = along.length();
        if (length < 0.001f) {
            return;
        }
        along.divideLocal(length);
        var middle = from.add(to).multLocal(0.5f);
        var layer = going.layer;
        for (int i = 0; i < going.drawn.capacity; i++) {
            float life = between(layer.lifeMin(), layer.lifeMax());
            going.drawn.put(i, middle.x, middle.y + layer.height(), middle.z, 0f, 0f, 0f,
                    0f, life, dice.nextFloat(), jitter(layer.sizeJitter()) * going.unit,
                    along.x, along.y, along.z, length);
        }
    }

    /** One particle: where in the disc, which way, how fast, how long, how big, which way up. */
    private void birthOne(Playing going, int index, Vector3f centre, float birth, float life,
            Moment moment) {
        var layer = going.layer;
        double angle = dice.nextDouble() * Math.PI * 2.0;
        float distance = layer.radius() * going.unit * (float) Math.sqrt(dice.nextDouble());
        float ox = centre.x + (float) Math.cos(angle) * distance;
        float oz = centre.z + (float) Math.sin(angle) * distance;
        float oy;
        if (going.local) {
            oy = layer.height();
        } else if (layer.lying() || going.rides == null) {
            // On the floor under its own spot rather than the middle's, so a ring
            // laid across the foot of a stair follows the step.
            oy = floorUnder(ox, oz) + layer.height();
        } else {
            oy = centre.y + layer.height();
        }

        var way = direction(layer, (float) Math.cos(angle), (float) Math.sin(angle), going);
        float speed = between(layer.speedMin(), layer.speedMax());
        float turn = (float) Math.toRadians(layer.turn()
                + (dice.nextFloat() * 2f - 1f) * 0.5f * layer.turnJitter());
        if (EffectLayer.ARC.equals(layer.type())) {
            // Top of the texture pointing the way the blow went.
            turn += (float) Math.atan2(-going.facingX, going.facingZ);
        }
        going.drawn.put(index, ox, oy, oz, way.x * speed, way.y * speed, way.z * speed,
                birth, life, dice.nextFloat(), jitter(layer.sizeJitter()) * going.unit,
                0f, 0f, 0f, turn);
    }

    /** Which way a particle leaves, before it is given a speed. */
    private Vector3f direction(EffectLayer layer, float outX, float outZ, Playing going) {
        Vector3f base = switch (layer.direction()) {
            case EffectLayer.UP -> new Vector3f(0f, 1f, 0f);
            case EffectLayer.DOWN -> new Vector3f(0f, -1f, 0f);
            case EffectLayer.OUT -> new Vector3f(outX, 0f, outZ);
            case EffectLayer.FORWARD -> new Vector3f(going.facingX, 0f, going.facingZ);
            case EffectLayer.BACK -> new Vector3f(-going.facingX, 0f, -going.facingZ);
            case EffectLayer.NONE -> new Vector3f();
            default -> randomUnit();
        };
        if (EffectLayer.NONE.equals(layer.direction()) || EffectLayer.ALL.equals(layer.direction())) {
            return base;
        }
        if (EffectLayer.OUT.equals(layer.direction())) {
            // Tilted up off the floor by up to the spread, so a ring of sparks is a
            // shallow dome rather than a flat disc nobody can see from above.
            float tilt = (float) Math.toRadians(dice.nextFloat() * layer.spread());
            return base.multLocal(FastMath.cos(tilt)).addLocal(0f, FastMath.sin(tilt), 0f)
                    .normalizeLocal();
        }
        return withinCone(base.normalizeLocal(), layer.spread());
    }

    private Vector3f withinCone(Vector3f axis, float spreadDegrees) {
        if (spreadDegrees <= 0f) {
            return axis;
        }
        float cosLimit = FastMath.cos((float) Math.toRadians(Math.min(180f, spreadDegrees)));
        float cosTheta = 1f - dice.nextFloat() * (1f - cosLimit);
        float sinTheta = FastMath.sqrt(Math.max(0f, 1f - cosTheta * cosTheta));
        float phi = dice.nextFloat() * FastMath.TWO_PI;
        var helper = Math.abs(axis.y) < 0.9f ? Vector3f.UNIT_Y : Vector3f.UNIT_X;
        var side = axis.cross(helper).normalizeLocal();
        var other = axis.cross(side).normalizeLocal();
        return axis.mult(cosTheta)
                .addLocal(side.multLocal(FastMath.cos(phi) * sinTheta))
                .addLocal(other.multLocal(FastMath.sin(phi) * sinTheta))
                .normalizeLocal();
    }

    private Vector3f randomUnit() {
        float z = dice.nextFloat() * 2f - 1f;
        float phi = dice.nextFloat() * FastMath.TWO_PI;
        float r = FastMath.sqrt(Math.max(0f, 1f - z * z));
        return new Vector3f(r * FastMath.cos(phi), z, r * FastMath.sin(phi));
    }

    // ---- every frame ----

    /** Move everything on, feed what is still being fed, and put away what is done. */
    void update(float tpf, Camera camera) {
        var going = playing.iterator();
        while (going.hasNext()) {
            var one = going.next();
            one.clock += tpf;
            float local = one.clock - one.layer.delay();
            if (local < 0f) {
                continue; // not yet: smoke waits for its fire
            }
            if (one.drawn != null) {
                one.drawn.show(true);
            }
            place(one, local);
            feed(one, local, tpf);
            fadeOutAnAura(one, local);
            lightUp(one, local);
            if (one.drawn != null) {
                one.drawn.time(local);
            }
            if (local >= one.until) {
                putAway(one);
                going.remove();
            }
        }
    }

    /** Where it is this frame: on the thing it rides, on the man it follows, or where it began. */
    private void place(Playing one, float local) {
        Vector3f now = null;
        if (one.rides != null) {
            if (one.rides.getParent() == null) {
                stopFeeding(one);
                one.rides = null;
            } else {
                now = one.rides.localToWorld(one.offset, null);
            }
        } else if (one.follows != NOBODY) {
            now = world == null ? null : world.whereIs(one.follows);
            if (now == null) {
                // He has gone -- died, or walked into the dark. What was round him
                // does not stay standing in the air where he was.
                stopFeeding(one);
                one.follows = NOBODY;
                one.until = Math.min(one.until, local + one.layer.lifeMax());
            }
        }
        if (now != null) {
            one.lastAnchor = one.anchor;
            one.anchor = now;
        }
        var at = one.anchor;
        if (one.fall > 0f) {
            float through = one.span <= 0f ? 1f : Math.clamp(local / one.span, 0f, 1f);
            // Slow and then sudden, as anything dropped from a height is.
            at = at.add(0f, one.fall * (1f - through * through), 0f);
        }
        if (one.drawn != null && one.local) {
            one.drawn.geometry().setLocalTranslation(at);
        }
        if (one.light != null) {
            one.light.setPosition(at.add(0f, Math.max(1f, one.layer.height()), 0f));
        }
    }

    /** A layer that lets particles out over time gets the ones it is owed. */
    private void feed(Playing one, float local, float tpf) {
        if (!one.emitting || one.drawn == null) {
            return;
        }
        if (one.rides == null && one.follows == NOBODY && local >= one.span) {
            stopFeeding(one);
            return;
        }
        one.owed += one.layer.rate() * tpf;
        int born = 0;
        var heading = one.anchor.subtract(one.lastAnchor);
        heading.y = 0f;
        if (heading.lengthSquared() > 0.0001f) {
            heading.normalizeLocal();
            one.facingX = heading.x;
            one.facingZ = heading.z;
        }
        var at = one.anchor;
        if (one.fall > 0f) {
            float through = one.span <= 0f ? 1f : Math.clamp(local / one.span, 0f, 1f);
            at = at.add(0f, one.fall * (1f - through * through), 0f);
        }
        var centre = one.local ? new Vector3f() : at;
        while (one.owed >= 1f && born < one.drawn.capacity) {
            one.owed -= 1f;
            float life = between(one.layer.lifeMin(), one.layer.lifeMax());
            birthOne(one, one.next++, centre, local, life,
                    new Moment(at, null, null, one.facingX, one.facingZ, 0f, NOBODY, NOBODY));
            born++;
        }
        if (born > 0) {
            if (!one.local) {
                // Round everywhere it has let particles out rather than round where
                // it is now: a trail is BEHIND what lays it, and a box that moved
                // with the fireball would have its own smoke culled.
                float reach = reachOf(one.layer, one.unit);
                var here = new BoundingBox(at.clone(), reach, reach, reach);
                one.bound = one.bound == null ? here : (BoundingBox) one.bound.merge(here);
                one.drawn.reach(one.bound);
            }
            one.drawn.upload();
        }
    }

    private void stopFeeding(Playing one) {
        if (!one.emitting && one.rides == null) {
            return;
        }
        one.emitting = false;
        float local = one.clock - one.layer.delay();
        one.until = Math.min(one.until, Math.max(0f, local) + one.layer.lifeMax());
    }

    /**
     * An aura that is running out says so.
     *
     * <p>For the last part of its run — as much of it as the layer's fade-out says
     * — it breathes, at the layer's own pulse. A shield that simply vanished would
     * be a shield the player was still standing behind when it went.
     */
    private void fadeOutAnAura(Playing one, float local) {
        if (one.drawn == null || !EffectLayer.AURA.equals(one.layer.type())
                || one.layer.pulseRate() <= 0f) {
            return;
        }
        float warning = one.span * one.layer.fadeOut();
        boolean ending = one.span - local <= warning;
        one.drawn.pulse(ending ? one.layer.pulseRate() : 0f, one.layer.pulseDepth());
    }

    /** A light flares in, holds, and fades, along the layer's own fade curve. */
    private void lightUp(Playing one, float local) {
        if (one.light == null) {
            return;
        }
        float span = one.rides != null || one.emitting ? Float.POSITIVE_INFINITY
                : Math.max(0.01f, one.until);
        float through = Float.isInfinite(span) ? 0.5f : Math.clamp(local / span, 0f, 1f);
        float in = one.layer.fadeIn() <= 0f ? 1f : smooth(0f, one.layer.fadeIn(), through);
        float out = one.layer.fadeOut() <= 0f || Float.isInfinite(span) ? 1f
                : 1f - smooth(1f - one.layer.fadeOut(), 1f, through);
        float strength = one.layer.lightPower() * in * out;
        int packed = one.layer.lightColour();
        one.light.setColor(new ColorRGBA(((packed >> 16) & 0xFF) / 255f * strength,
                ((packed >> 8) & 0xFF) / 255f * strength, (packed & 0xFF) / 255f * strength,
                1f));
        one.light.setRadius(Math.max(0.001f, one.layer.lightRadius()));
    }

    // ---- putting things away ----

    private void putAway(Playing one) {
        if (one.drawn != null) {
            one.drawn.show(false);
            one.drawn.geometry().removeFromParent();
            one.drawn.geometry().setLocalTranslation(0f, 0f, 0f);
            particlesInUse -= one.drawn.capacity;
            spare.computeIfAbsent(one.drawn.capacity, size -> new ArrayDeque<>()).push(one.drawn);
        }
        if (one.light != null && lights != null) {
            lights.give(one.light);
        }
    }

    /** Everything back in the box at once: a world being rebuilt has nothing burning. */
    void clear() {
        for (var one : playing) {
            putAway(one);
        }
        playing.clear();
    }

    private ParticleLayer borrow(int wanted) {
        int size = SMALLEST;
        while (size < wanted) {
            size *= 2;
        }
        // The pool is by size and a layer is lent at least as big as it asked;
        // what it did not ask for stays dark and is not counted against anybody.
        var pool = spare.computeIfAbsent(size, s -> new ArrayDeque<>());
        var drawn = pool.isEmpty() ? build(size) : pool.pop();
        particlesInUse += drawn.capacity;
        return drawn;
    }

    private ParticleLayer build(int size) {
        layersMade++;
        return new ParticleLayer(assets, size);
    }

    private boolean alreadyBurning(String key) {
        for (var one : playing) {
            if (key.equals(one.key) && one.clock - one.layer.delay() < one.until) {
                return true;
            }
        }
        return false;
    }

    // ---- small mechanics ----

    private Texture textureFor(String path) {
        if (path == null || path.isBlank()) {
            return softDot();
        }
        var known = textures.get(path);
        if (known != null) {
            return known;
        }
        try {
            var texture = assets.loadTexture(path);
            texture.setWrap(Texture.WrapMode.EdgeClamp);
            texture.setMinFilter(Texture.MinFilter.Trilinear);
            textures.put(path, texture);
            return texture;
        } catch (RuntimeException notThere) {
            if (missing.add(path)) {
                LOG.warning(() -> "an effect names a texture that is not there: " + path
                        + " -- drawn as a plain glow instead");
            }
            textures.put(path, softDot());
            return textures.get(path);
        }
    }

    private Texture softDot() {
        if (dot == null) {
            dot = ParticleLayer.softDot();
        }
        return dot;
    }

    private Vector3f floored(Vector3f at) {
        return new Vector3f(at.x, floorUnder(at.x, at.z), at.z);
    }

    private float floorUnder(float x, float z) {
        return world == null ? 0f : world.floorAt(x, z);
    }

    private static void normaliseFacing(Playing going) {
        float length = FastMath.sqrt(going.facingX * going.facingX
                + going.facingZ * going.facingZ);
        if (length < 0.0001f) {
            going.facingX = 0f;
            going.facingZ = 1f;
        } else {
            going.facingX /= length;
            going.facingZ /= length;
        }
    }

    private float between(float low, float high) {
        return low + (high - low) * dice.nextFloat();
    }

    private float jitter(float amount) {
        return Math.max(0.05f, 1f + (dice.nextFloat() * 2f - 1f) * amount);
    }

    private static float smooth(float edge0, float edge1, float x) {
        if (edge1 <= edge0) {
            return x >= edge1 ? 1f : 0f;
        }
        float t = Math.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    // ---- what a test may ask ----

    int playingCount() {
        return playing.size();
    }

    int particlesInUse() {
        return particlesInUse;
    }

    int layersMade() {
        return layersMade;
    }
}
