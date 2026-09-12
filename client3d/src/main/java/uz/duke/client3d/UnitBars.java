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

    /**
     * One creature to draw a bar over.
     *
     * @param view the creature, as the snapshot has it
     * @param top  the world height its bar floats at, over its head
     * @param foot the world height it stands on. A second projection rather than
     *             an offset from the first, because the name goes UNDER the
     *             creature and the distance between its feet and its head is a
     *             different number of pixels at every distance from the camera
     * @param his  whether it fights for the watching player
     */
    record Standing(UnitView view, float top, float foot, boolean his) {
    }

    /** How many steps the experience ring is cut into. */
    private static final int ARC_STEPS = 32;

    /**
     * How wide a mark across a bar is, in pixels.
     *
     * <p>Two, and the same two on every bar in the game. A mark is a ruler line:
     * it has to be seen and it must not be looked at, and the difference between
     * those two is about a pixel.
     */
    private static final float MARK_THICK = 2f;

    /** How far the dark keyline stands outside a bar, in pixels. */
    private static final float EDGE = 2f;

    /**
     * How strongly the unfilled part of the experience ring is drawn.
     *
     * <p>The same colour as the filled part, at a fifth of it. One colour and two
     * strengths rather than two colours: the ring says <em>two</em> things at
     * once — whose ring this is, which is the whole of how a boss is told apart,
     * and how far round it has gone — and a second colour for the second thing
     * would have the two arguing over which of them the eye answers first.
     */
    private static final float RING_TRACK = 0.22f;

    /** How far the shadow under a line of lettering is offset, in pixels. */
    private static final float SHADOW = 1f;

    /** Every rectangle on screen is this, scaled. */
    private static final Mesh SQUARE = square();

    private final AssetManager assets;
    private final BitmapFont plain;
    /** The display face, for a boss's name, or null to letter it like the rest. */
    private final BitmapFont display;
    private final Node root = new Node("unit-bars");
    private final List<Bar> pool = new ArrayList<>();
    /**
     * The marks, by the count AND the width they were cut for.
     *
     * <p>Keyed by both because they are cut in pixels rather than in a unit
     * square: a unit mesh stretched to a bar's width stretches its marks with it,
     * so a mark that is a hairline on a rat is three pixels on a boss and the bar
     * reads as separate blocks rather than as a divided one. There are as many
     * entries as there are distinct creature sizes -- a dozen or so -- and two
     * creatures of a size still share one.
     */
    private final Map<Long, Mesh> marks = new HashMap<>();
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
                var underneath = camera.getScreenCoordinates(
                        new Vector3f(one.view().x(), one.foot(), one.view().y()));
                var bar = take(at++);
                dress(bar, one, reading);
                place(bar, onScreen.x, onScreen.y, underneath.y);
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
        private Geometry edge;
        private Geometry trough;
        private Geometry fill;
        private Geometry ticks;
        private Geometry manaEdge;
        private Geometry manaTrough;
        private Geometry manaFill;
        private Geometry back;
        private Geometry rim;
        private Geometry arc;
        private Lettering count;
        private Lettering level;
        private Lettering name;
        private Lettering bossName;
        private boolean up;
        /** Which of the two name pieces is the one showing. */
        private Lettering lettered;
        private int cutInto = -1;
        private int arcStep = -1;
        private float wide = -1f;
    }

    /**
     * One line of lettering and the shadow under it.
     *
     * <p>Both, always, because a bar is drawn over the game world and the world
     * is whatever colour it happens to be. Bone lettering on a pale floor is not
     * dim, it is gone — and the floor changes with the theme, so no single
     * colour is safe. A dark copy one pixel down and across costs a second small
     * mesh and makes the reading hold on anything.
     *
     * <p>The two are driven together and there is no way to move one without the
     * other, which is the point of their being a pair rather than two fields.
     */
    private static final class Lettering {
        private final BitmapText shadow;
        private final BitmapText face;
        private String said = "";

        private Lettering(BitmapText shadow, BitmapText face) {
            this.shadow = shadow;
            this.face = face;
        }

        private void say(String words) {
            if (said.equals(words)) {
                return; // a BitmapText rebuilds its mesh on every setText
            }
            said = words;
            shadow.setText(words);
            face.setText(words);
        }

        private void colour(ColorRGBA colour) {
            face.setColor(colour);
        }

        private void size(float size) {
            if (face.getSize() != size) {
                shadow.setSize(size);
                face.setSize(size);
            }
        }

        private void centre(float middle, float bottom) {
            float left = middle - face.getLineWidth() / 2f;
            shadow.setLocalTranslation(left + SHADOW, bottom - SHADOW,
                    shadow.getLocalTranslation().z);
            face.setLocalTranslation(left, bottom, face.getLocalTranslation().z);
        }

        private void show(boolean shown) {
            UnitBars.show(shadow, shown);
            UnitBars.show(face, shown);
        }
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
            size(bar.edge, width + EDGE * 2f, look.height() + EDGE * 2f);
            size(bar.trough, width, look.height());
            size(bar.manaEdge, width + EDGE * 2f, look.manaHeight() + EDGE * 2f);
            size(bar.manaTrough, width, look.manaHeight());
        }
        int cuts = look.segmentsFor(view.maxHealth());
        if (bar.cutInto != cuts || bar.wide != width) {
            bar.cutInto = cuts;
            bar.ticks.setMesh(marks.computeIfAbsent(
                    cuts * 100_000L + Math.round(width), key -> markMesh(cuts, width)));
        }
        bar.ticks.setLocalScale(1f, look.height(), 1f);

        float left = Math.clamp(view.healthFraction(), 0f, 1f);
        size(bar.fill, Math.max(1f, width * left), look.height());
        bar.wide = width;
        bar.fill.getMaterial().setColor("Color", look.fill(one.his()));

        boolean pool = hero && reading.maxMana() > 0 && look.hasMana();
        show(bar.manaEdge, pool);
        show(bar.manaTrough, pool);
        show(bar.manaFill, pool);
        if (pool) {
            float held = Math.clamp(reading.mana() / (float) reading.maxMana(), 0f, 1f);
            size(bar.manaFill, Math.max(1f, width * held), look.manaHeight());
        }

        var ring = look.rim(boss);
        bar.rim.getMaterial().setColor("Color",
                new ColorRGBA(ring.r, ring.g, ring.b, RING_TRACK));
        bar.arc.getMaterial().setColor("Color", ring);
        int step = Math.round(reading.experienceOn(view.id()) * ARC_STEPS);
        show(bar.arc, hero && step > 0);
        if (hero && step > 0 && bar.arcStep != step) {
            bar.arcStep = step;
            bar.arc.setMesh(arcs.computeIfAbsent(step, this::arcMesh));
        }

        bar.count.say(Math.round(view.health()) + "/" + Math.round(view.maxHealth()));
        bar.level.say(Integer.toString(reading.levelOn(view.id())));
        bar.level.colour(boss ? look.rim(true) : look.letteringColour());
        // A boss is torch-lit and set in the display face, which is the whole of
        // how one is told from an ordinary monster at a glance.
        var lettered = boss && bar.bossName != null ? bar.bossName : bar.name;
        lettered.say(reading.nameOf(view.templateName()));
        bar.lettered = lettered;
        bar.name.show(lettered == bar.name);
        if (bar.bossName != null) {
            bar.bossName.show(lettered == bar.bossName);
        }
    }

    /** Where on the screen the whole assembly sits, measured from the creature. */
    private void place(Bar bar, float x, float y, float footY) {
        float width = bar.wide;
        float medallion = look.ring();
        float whole = medallion + look.ringGap() + width;
        // Centred on the creature, so the bar grows out from over its head rather
        // than out to one side of it.
        float barLeft = x - whole / 2f + medallion + look.ringGap();

        at(bar.edge, barLeft - EDGE, y - EDGE);
        at(bar.trough, barLeft, y);
        at(bar.fill, barLeft, y);
        at(bar.ticks, barLeft, y);
        bar.count.centre(barLeft + width / 2f,
                y + (look.height() - look.countSize()) / 2f + 1f);

        float manaY = y - look.gap() - look.manaHeight();
        at(bar.manaEdge, barLeft - EDGE, manaY - EDGE);
        at(bar.manaTrough, barLeft, manaY);
        at(bar.manaFill, barLeft, manaY);

        // Level in the middle of the disc, and the disc centred on the bar's own
        // height rather than on the whole assembly: the mana bar comes and goes.
        float discX = x - whole / 2f + medallion / 2f;
        float discY = y + look.height() / 2f;
        at(bar.back, discX, discY);
        at(bar.rim, discX, discY);
        at(bar.arc, discX, discY);
        bar.level.centre(discX, discY - look.levelSize() / 2f + 1f);

        // Under the CREATURE, not under the bar. The bar floats over its head and
        // a name hung off the bottom of that sits in the middle of the thing it
        // names; at its feet there is nothing else, and the eye reads downward
        // from the bar, past the creature, to what it is called.
        if (bar.lettered != null) {
            bar.lettered.centre(x, footY - 4f - look.nameSize(bar.lettered == bar.bossName));
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
        // The keyline first and furthest back. The same near-black as the marks,
        // and on purpose: both are the bar's own linework, and a second colour
        // would be a second thing to keep in step for no second decision.
        bar.edge = piece(bar, "edge", look.tickColour(), -1f);
        bar.trough = piece(bar, "trough", look.troughColour(), 0f);
        bar.fill = piece(bar, "fill", look.fill(false), 1f);
        bar.ticks = piece(bar, "ticks", look.tickColour(), 2f);
        bar.manaEdge = piece(bar, "manaEdge", look.tickColour(), -1f);
        bar.manaTrough = piece(bar, "manaTrough", look.troughColour(), 0f);
        bar.manaFill = piece(bar, "manaFill", look.manaColour(), 1f);
        bar.back = piece(bar, "back", look.faceColour(), 3f);
        bar.back.setMesh(disc(look.ring() / 2f));
        bar.rim = piece(bar, "rim", look.rim(false), 4f);
        bar.rim.setMesh(ring(look.ring() / 2f - look.ringEdge(), look.arc()));
        bar.arc = piece(bar, "arc", look.rim(false), 5f);
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
        bar.count.show(true);
        bar.level.show(true);
        root.attachChild(bar.node);
        pool.add(bar);
        put(bar);
        return bar;
    }

    private void put(Bar bar) {
        bar.up = false;
        bar.node.setCullHint(Spatial.CullHint.Always);
    }

    private Geometry piece(Bar bar, String name, ColorRGBA colour, float depth) {
        var geometry = new Geometry(name, SQUARE);
        var material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", colour);
        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        // ★ WITHOUT THIS, HALF THE BAR IS INVISIBLE. The pieces are stacked by
        // giving each a z of its own, which is how the interface layer sorts
        // them -- and z is also what the depth buffer tests. The first piece
        // drawn writes its depth, every piece behind it in the stack fails the
        // test, and what reaches the screen is the trough, the fill and the
        // marks with the whole medallion missing. Nothing is logged; the
        // geometry is built, placed and measurably the right size.
        material.getAdditionalRenderState().setDepthTest(false);
        material.getAdditionalRenderState().setDepthWrite(false);
        // ★ AND WITHOUT THIS, THE MEDALLION IS STILL MISSING. A flat shape has a
        // side it is seen from, decided by the order its corners are given in,
        // and a back-facing one is thrown away before it is drawn. The square
        // every bar is made of happens to be wound the right way round; the fan
        // of wedges the disc and its ring are made of is wound the other, so all
        // three of them were culled. On a layer where nothing has a back, having
        // one at all is the mistake -- so there is no side, and a wedge cut the
        // wrong way round is simply not a class of fault here any more.
        material.getAdditionalRenderState().setFaceCullMode(
                RenderState.FaceCullMode.Off);
        geometry.setMaterial(material);
        geometry.setQueueBucket(RenderQueue.Bucket.Gui);
        geometry.setLocalTranslation(0f, 0f, depth);
        bar.node.attachChild(geometry);
        return geometry;
    }

    private Lettering lettering(Bar bar, BitmapFont font, float size, ColorRGBA colour) {
        return new Lettering(line(bar, font, size, look.tickColour(), 6f),
                line(bar, font, size, colour, 7f));
    }

    private BitmapText line(Bar bar, BitmapFont font, float size, ColorRGBA colour, float depth) {
        var text = new BitmapText(font);
        text.setSize(size);
        text.setColor(colour);
        text.setQueueBucket(RenderQueue.Bucket.Gui);
        // In front of the stone of the bar, which is the only thing it could be
        // hidden behind -- they are all in the one bucket and sort by this.
        text.setLocalTranslation(0f, 0f, depth);
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
    private static Mesh markMesh(int cuts, float width) {
        int lines = Math.max(0, cuts - 1);
        var points = new float[lines * 12];
        var order = new int[lines * 6];
        float thick = MARK_THICK;
        for (int line = 0; line < lines; line++) {
            float at = (line + 1f) * width / cuts - thick / 2f;
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
