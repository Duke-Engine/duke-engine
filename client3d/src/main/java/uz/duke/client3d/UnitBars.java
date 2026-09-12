package uz.duke.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import uz.duke.game.view.UnitView;

/**
 * The bars over everybody's heads — the part that knows about jME.
 *
 * <p>What they are made of and why is in {@link UnitBarLook}; what the game has
 * to say for them to be drawn is in {@link UnitBarReading}. This puts them on
 * the screen.
 *
 * <p><b>Drawn flat on the interface, not standing in the world.</b> The old bar
 * was a pair of billboarded quads hung off the creature, which is the obvious
 * thing and is wrong for the same reason it is wrong for a damage number: a
 * readout that shrinks because the thing it is about walked away is a readout
 * that stops being one. It also could not hold lettering — a name at forty paces
 * was four pixels tall — and the whole design turns on the reading sitting inside
 * the bar. So the creature's head is projected and the bar is punched out from
 * there, which is also what makes the design's pixel sizes mean anything.
 *
 * <p><b>Pooled, and nothing is rebuilt.</b> A floor can hold forty creatures and
 * each bar is a dozen small pieces, so the costs that matter are per frame rather
 * than per bar:
 *
 * <ul>
 * <li>every rectangle is the <em>same</em> unit quad, scaled. One mesh for the
 *     hundreds of them
 * <li>the marks across a bar are a mesh per <em>count</em>, not per creature —
 *     there are thirteen possible counts, so thirteen meshes are shared by
 *     everything alive
 * <li>the experience ring is a mesh per thirty-second of a turn, and only the
 *     hero has one at all
 * <li>lettering is only handed a new string when the string has changed. A
 *     {@code BitmapText} rebuilds its mesh on every {@code setText}, and a bar
 *     that said "30/30" thirty times a second was thirty rebuilds of a mesh that
 *     had not changed
 * <li>and a creature that is off the edge of the screen or behind the camera is
 *     not drawn at all. What is under fog never arrives: the snapshot is built
 *     from what the player can see
 * </ul>
 */
final class UnitBars {

    /** One creature to draw a bar over: which, how high it stands, and whose. */
    record Standing(UnitView view, float top, boolean his) {
    }

    /** How many steps the experience ring is cut into. */
    private static final int ARC_STEPS = 32;

    /** Every rectangle on screen is this, scaled. */
    private static final Mesh SQUARE = square();

    private final AssetManager assets;
    private final BitmapFont plain;
    /** The display face, for a boss's name, or null to letter it like the rest. */
    private final BitmapFont display;
    private final Node root = new Node("unit-bars");
    private final List<Bar> pool = new ArrayList<>();
    private final Map<Integer, Mesh> marks = new HashMap<>();
    private final Map<Integer, Mesh> arcs = new HashMap<>();
    private UnitBarLook look = UnitBarLook.NONE;

    UnitBars(AssetManager assets, BitmapFont plain, BitmapFont display) {
        this.assets = assets;
        this.plain = plain;
        this.display = display;
    }

    Node node() {
        return root;
    }

    void look(UnitBarLook look) {
        this.look = look == null ? UnitBarLook.NONE : look;
    }

    /** How many bars the pool has had to make — so a test can see it settle. */
    int madeSoFar() {
        return pool.size();
    }

    /** How many are up right now. */
    int showing() {
        int up = 0;
        for (var bar : pool) {
            if (bar.up) {
                up++;
            }
        }
        return up;
    }

    /** Take them all down: a new floor has nobody on it yet. */
    void clear() {
        for (var bar : pool) {
            put(bar);
        }
    }

    /**
     * Put a bar over each of them, and away the ones nobody needs this frame.
     *
     * <p>Projected every frame rather than placed once: the creature walks and
     * the camera pans, and a bar that stayed where the screen used to be would
     * belong to a patch of floor rather than to anybody.
     */
    void update(Camera camera, List<Standing> standing, UnitBarReading reading) {
        int at = 0;
        if (look.draws()) {
            for (var one : standing) {
                var onScreen = camera.getScreenCoordinates(
                        new Vector3f(one.view().x(), one.top(), one.view().y()));
                if (offScreen(camera, onScreen)) {
                    continue;
                }
                var bar = take(at++);
                dress(bar, one, reading);
                place(bar, onScreen.x, onScreen.y);
            }
        }
        for (int spare = at; spare < pool.size(); spare++) {
            put(pool.get(spare));
        }
    }

    /**
     * Behind the camera, or past the edge with room to spare.
     *
     * <p>The margin is generous on purpose: a bar is wider than the creature it
     * belongs to, so one whose feet are just off the left edge still has half a
     * bar that ought to show.
     */
    private boolean offScreen(Camera camera, Vector3f onScreen) {
        if (onScreen.z > 1f) {
            return true;
        }
        float margin = look.longest();
        return onScreen.x < -margin || onScreen.x > camera.getWidth() + margin
                || onScreen.y < -margin || onScreen.y > camera.getHeight() + margin;
    }

    // ---- one bar ----

    private static final class Bar {
        private final Node node = new Node("bar");
        private Geometry trough;
        private Geometry fill;
        private Geometry ticks;
        private Geometry manaTrough;
        private Geometry manaFill;
        private Geometry back;
        private Geometry rim;
        private Geometry arc;
        private BitmapText count;
        private BitmapText level;
        private BitmapText name;
        private BitmapText bossName;
        private boolean up;
        /** What was last handed to each piece, so nothing is handed it twice. */
        private String saidCount = "";
        private String saidLevel = "";
        private String saidName = "";
        /** Which of the two name pieces is the one showing. */
        private BitmapText lettered;
        private int cutInto = -1;
        private int arcStep = -1;
        private float wide = -1f;
    }

    /**
     * Say what this bar is about — and say as little as possible.
     *
     * <p>Every branch here is a guard against handing a piece something it
     * already has. That is not tidiness: a {@code BitmapText} builds a fresh mesh
     * every time it is given a string, and a mesh is swapped for another whenever
     * the marks or the ring change step. Doing either unconditionally is a
     * rebuild per creature per frame, which is the shape of cost that turns into
     * a warm laptop rather than into a dropped frame anybody would notice.
     */
    private void dress(Bar bar, Standing one, UnitBarReading reading) {
        var view = one.view();
        boolean boss = reading.isBoss(view.id());
        boolean hero = reading.isHero(view.id());
        float width = look.widthFor(view.maxHealth());

        if (bar.wide != width) {
            bar.wide = width;
            size(bar.trough, width, look.height());
            size(bar.manaTrough, width, look.manaHeight());
        }
        int cuts = look.segmentsFor(view.maxHealth());
        if (bar.cutInto != cuts) {
            bar.cutInto = cuts;
            bar.ticks.setMesh(marks.computeIfAbsent(cuts, UnitBars::markMesh));
        }
        size(bar.ticks, width, look.height());

        float left = Math.clamp(view.healthFraction(), 0f, 1f);
        size(bar.fill, Math.max(1f, width * left), look.height());
        bar.fill.getMaterial().setColor("Color", look.fill(one.his()));

        boolean pool = hero && reading.maxMana() > 0 && look.hasMana();
        show(bar.manaTrough, pool);
        show(bar.manaFill, pool);
        if (pool) {
            float held = Math.clamp(reading.mana() / (float) reading.maxMana(), 0f, 1f);
            size(bar.manaFill, Math.max(1f, width * held), look.manaHeight());
        }

        bar.rim.getMaterial().setColor("Color", look.rim(boss));
        int step = Math.round(reading.experienceOn(view.id()) * ARC_STEPS);
        show(bar.arc, hero && step > 0);
        if (hero && step > 0 && bar.arcStep != step) {
            bar.arcStep = step;
            bar.arc.setMesh(arcs.computeIfAbsent(step, this::arcMesh));
        }

        var reads = Math.round(view.health()) + "/" + Math.round(view.maxHealth());
        if (!bar.saidCount.equals(reads)) {
            bar.saidCount = reads;
            bar.count.setText(reads);
        }
        var rank = Integer.toString(reading.levelOn(view.id()));
        if (!bar.saidLevel.equals(rank)) {
            bar.saidLevel = rank;
            bar.level.setText(rank);
        }
        // A boss is torch-lit and set in the display face, which is the whole of
        // how one is told from an ordinary monster at a glance.
        var lettered = boss && bar.bossName != null ? bar.bossName : bar.name;
        var called = reading.nameOf(view.templateName());
        if (!bar.saidName.equals(called) || bar.lettered != lettered) {
            bar.saidName = called;
            bar.lettered = lettered;
            lettered.setText(called);
        }
        show(bar.name, lettered == bar.name);
        if (bar.bossName != null) {
            show(bar.bossName, lettered == bar.bossName);
        }
    }

    /** Where on the screen the whole assembly sits, measured from the creature. */
    private void place(Bar bar, float x, float y) {
        float width = bar.wide;
        float medallion = look.ring();
        float whole = medallion + look.ringGap() + width;
        // Centred on the creature, so the bar grows out from over its head rather
        // than out to one side of it.
        float barLeft = x - whole / 2f + medallion + look.ringGap();

        at(bar.trough, barLeft, y);
        at(bar.fill, barLeft, y);
        at(bar.ticks, barLeft, y);
        centre(bar.count, barLeft + width / 2f, y + (look.height() + look.countSize()) / 2f - 1f);

        float manaY = y - look.gap() - look.manaHeight();
        at(bar.manaTrough, barLeft, manaY);
        at(bar.manaFill, barLeft, manaY);

        // Level in the middle of the disc, and the disc centred on the bar's own
        // height rather than on the whole assembly: the mana bar comes and goes.
        float discX = x - whole / 2f + medallion / 2f;
        float discY = y + look.height() / 2f;
        at(bar.back, discX, discY);
        at(bar.rim, discX, discY);
        at(bar.arc, discX, discY);
        centre(bar.level, discX, discY + look.levelSize() / 2f - 1f);

        float under = bar.manaFill.getCullHint() == Spatial.CullHint.Always ? y : manaY;
        if (bar.lettered != null) {
            centre(bar.lettered, x, under - 2f);
        }
    }

    // ---- the pool ----

    /**
     * The {@code at}-th bar, made if the busiest frame so far was smaller.
     *
     * <p>By position rather than by creature: a bar carries nothing from one
     * frame to the next except what it was last told, and the guards in
     * {@link #dress} compare against that. So the pool settles at the size of the
     * busiest moment the game has seen and never looks anything up.
     */
    private Bar take(int at) {
        var bar = at < pool.size() ? pool.get(at) : make();
        bar.up = true;
        bar.node.setCullHint(Spatial.CullHint.Inherit);
        return bar;
    }

    private Bar make() {
        var bar = new Bar();
        bar.trough = piece(bar, look.troughColour(), 0f);
        bar.fill = piece(bar, look.fill(false), 1f);
        bar.ticks = piece(bar, look.tickColour(), 2f);
        bar.manaTrough = piece(bar, look.troughColour(), 0f);
        bar.manaFill = piece(bar, look.manaColour(), 1f);
        bar.back = piece(bar, look.faceColour(), 3f);
        bar.back.setMesh(disc(look.ring() / 2f));
        bar.rim = piece(bar, look.rim(false), 4f);
        bar.rim.setMesh(ring(look.ring() / 2f - look.ringEdge(), look.arc()));
        bar.arc = piece(bar, look.arcRgb(), 5f);
        bar.arc.setMesh(arcs.computeIfAbsent(ARC_STEPS, this::arcMesh));
        bar.count = lettering(bar, plain, look.countSize(), look.letteringColour());
        bar.level = lettering(bar, plain, look.levelSize(), look.letteringColour());
        bar.name = lettering(bar, plain, look.nameSize(false), look.letteringColour());
        // ★ TWO NAMES, ONE SHOWN. A BitmapText is built around its font and
        // cannot be handed another, and a boss is set in the display face while
        // everything else is set plainly -- so the choice cannot be made when the
        // bar is dressed unless both exist. A game that names no display face
        // gets one name and the plain one, which is what it asked for.
        bar.bossName = display == null ? null
                : lettering(bar, display, look.nameSize(true), look.rim(true));
        root.attachChild(bar.node);
        pool.add(bar);
        put(bar);
        return bar;
    }

    private void put(Bar bar) {
        bar.up = false;
        bar.node.setCullHint(Spatial.CullHint.Always);
    }

    private Geometry piece(Bar bar, ColorRGBA colour, float depth) {
        var geometry = new Geometry("bar-piece", SQUARE);
        var material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", colour);
        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        geometry.setMaterial(material);
        geometry.setQueueBucket(RenderQueue.Bucket.Gui);
        geometry.setLocalTranslation(0f, 0f, depth);
        bar.node.attachChild(geometry);
        return geometry;
    }

    private BitmapText lettering(Bar bar, BitmapFont font, float size, ColorRGBA colour) {
        var text = new BitmapText(font);
        text.setSize(size);
        text.setColor(colour);
        text.setQueueBucket(RenderQueue.Bucket.Gui);
        // In front of the stone of the bar, which is the only thing it could be
        // hidden behind -- they are all in the one bucket and sort by this.
        text.setLocalTranslation(0f, 0f, 6f);
        bar.node.attachChild(text);
        return text;
    }

    // ---- the small mechanics ----

    private static void size(Geometry geometry, float width, float height) {
        geometry.setLocalScale(Math.max(0.01f, width), Math.max(0.01f, height), 1f);
    }

    private static void at(Geometry geometry, float x, float y) {
        var was = geometry.getLocalTranslation();
        geometry.setLocalTranslation(x, y, was.z);
    }

    private static void show(Spatial spatial, boolean shown) {
        spatial.setCullHint(shown ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
    }

    private static void centre(BitmapText text, float middle, float top) {
        text.setLocalTranslation(middle - text.getLineWidth() / 2f, top,
                text.getLocalTranslation().z);
    }

    // ---- meshes, all of them shared ----

    /** The one rectangle. Everything square on screen is this, scaled. */
    private static Mesh square() {
        return meshOf(new float[] {0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 0f},
                new int[] {0, 1, 2, 0, 2, 3});
    }

    /**
     * The marks across a bar, in a square one unit across.
     *
     * <p>One mesh per <em>count</em> rather than per creature: the count is
     * between eight and twenty by the table's own design, so thirteen of these
     * are shared by every bar in the game however many creatures there are. They
     * are scaled to each bar like everything else.
     *
     * <p>The outer two are not drawn — the bar's own ends are already edges, and
     * a mark on top of one reads as a thicker edge rather than as a division.
     */
    private static Mesh markMesh(int cuts) {
        int lines = Math.max(0, cuts - 1);
        var points = new float[lines * 12];
        var order = new int[lines * 6];
        float thick = 1f / Math.max(1f, cuts * 9f);
        for (int line = 0; line < lines; line++) {
            float at = (line + 1f) / cuts - thick / 2f;
            int p = line * 12;
            points[p] = at;
            points[p + 1] = 0f;
            points[p + 3] = at + thick;
            points[p + 4] = 0f;
            points[p + 6] = at + thick;
            points[p + 7] = 1f;
            points[p + 9] = at;
            points[p + 10] = 1f;
            int o = line * 6;
            int v = line * 4;
            order[o] = v;
            order[o + 1] = v + 1;
            order[o + 2] = v + 2;
            order[o + 3] = v;
            order[o + 4] = v + 2;
            order[o + 5] = v + 3;
        }
        return meshOf(points, order);
    }

    private static Mesh disc(float radius) {
        return wedges(0f, radius, ARC_STEPS, ARC_STEPS);
    }

    private static Mesh ring(float radius, float thickness) {
        return wedges(radius - thickness, radius, ARC_STEPS, ARC_STEPS);
    }

    /**
     * The experience ring, filled to {@code step} thirty-seconds of a turn.
     *
     * <p>One mesh per step rather than a mesh rebuilt as the ring fills, and only
     * the hero has one at all — so the whole of this is at most thirty-two small
     * meshes for the life of the game, and filling the ring is swapping which one
     * a single geometry points at.
     *
     * <p>It starts at twelve o'clock and goes clockwise, which is the one thing
     * about it nobody has to be taught.
     */
    private Mesh arcMesh(int step) {
        return wedges(look.ring() / 2f - look.ringEdge() - look.arc(),
                look.ring() / 2f - look.ringEdge(), Math.clamp(step, 0, ARC_STEPS), ARC_STEPS);
    }

    /**
     * A fan of {@code taken} wedges out of {@code whole}, between two radii.
     *
     * <p>An inner radius of zero makes a disc, and anything else an annulus. The
     * angle runs from straight up and clockwise, which on a screen — where y
     * counts upward — means sine and cosine the other way round from the usual.
     */
    private static Mesh wedges(float inner, float outer, int taken, int whole) {
        int slices = Math.max(0, Math.min(taken, whole));
        var points = new float[slices * 12];
        var order = new int[slices * 6];
        for (int slice = 0; slice < slices; slice++) {
            double from = Math.PI / 2 - 2 * Math.PI * slice / whole;
            double to = Math.PI / 2 - 2 * Math.PI * (slice + 1) / whole;
            int p = slice * 12;
            corner(points, p, inner, from);
            corner(points, p + 3, outer, from);
            corner(points, p + 6, outer, to);
            corner(points, p + 9, inner, to);
            int o = slice * 6;
            int v = slice * 4;
            order[o] = v;
            order[o + 1] = v + 1;
            order[o + 2] = v + 2;
            order[o + 3] = v;
            order[o + 4] = v + 2;
            order[o + 5] = v + 3;
        }
        return meshOf(points, order);
    }

    private static void corner(float[] points, int at, float radius, double angle) {
        points[at] = (float) (Math.cos(angle) * radius);
        points[at + 1] = (float) (Math.sin(angle) * radius);
        points[at + 2] = 0f;
    }

    private static Mesh meshOf(float[] points, int[] order) {
        var mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(points));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(order));
        mesh.updateBound();
        return mesh;
    }
}
