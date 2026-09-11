package uz.duke.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.font.Rectangle;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;
import com.jme3.util.BufferUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import uz.duke.core.GameConstants;

/**
 * The hero's own bar along the bottom of the screen: where he is, what he is,
 * what is left of him, and what he can cast.
 *
 * <p>Drawn rather than written. The rest of the client's HUD is a line of text,
 * which is right for an RTS where the interesting numbers are money and power and
 * a player reads them once a minute. A dungeon hero is read continuously and at a
 * glance — how much health is left, whether the ultimate is up — and a glance
 * cannot parse "Q ready  W 2s".
 *
 * <p>One slab, not a scattering of boxes. The minimap sits in a socket at the
 * left, the portrait and the vitals in the middle, the skills and the powers he
 * has taken at the right, the depth at the far end; the sections are separated by
 * a carved line rather than by empty screen. That is the Warcraft III arrangement,
 * and it is an arrangement rather than a decoration: everything a player looks at
 * between one click and the next is in one place, so his eyes travel a hand's
 * width instead of a screen's.
 *
 * <p>Like {@link Shell} and {@link Hotkeys}, the client keeps the mechanism and
 * the game says what goes in it. Everything here is driven by the snapshot's
 * status channel, which the engine carries and never reads; a game that puts
 * something else there, or nothing, gets the plain text HUD exactly as before.
 * The wording is the game's too — no English is written into this class, because
 * the labels are a dungeon's, not the engine's.
 *
 * <p>Laid out in the pixels the design was drawn at and then scaled to the window,
 * so the bar keeps its proportions on any screen instead of shrinking into a
 * corner of a wide one or overrunning a narrow one.
 *
 * <p>The look is stone carving: a slot is a lit top edge over a dark inner
 * shadow, sitting on its own drop. Three colours do all the work — torch for what
 * a skill is, blood for health, green for experience — so that the one thing that
 * changes colour, a skill going dead while it reloads, is the thing the eye
 * catches.
 */
final class HeroPanel {

    // ---- the palette, and the only place colour is decided ----

    private static final ColorRGBA STONE_DEEP = rgb(0x16130F);
    private static final ColorRGBA STONE = rgb(0x2B2620);
    private static final ColorRGBA STONE_LIT = rgb(0x3D362C);
    private static final ColorRGBA STONE_DEAD_LIT = rgb(0x231F1A);
    private static final ColorRGBA STONE_DEAD = rgb(0x191510);
    private static final ColorRGBA TORCH = rgb(0xE8A33D);
    private static final ColorRGBA BONE = rgb(0xD9CFBA);
    private static final ColorRGBA BLOOD = rgb(0xA8322B);
    private static final ColorRGBA ARCANE = rgb(0x5F8C7B);
    private static final ColorRGBA DEAD = rgb(0x4A443B);
    private static final ColorRGBA EDGE = rgb(0x100D0A);
    private static final ColorRGBA DROP = rgb(0x0A0806);
    private static final ColorRGBA GLYPH_COLD = rgb(0x6A6154);
    private static final ColorRGBA LABEL = rgb(0x8B8171);
    private static final ColorRGBA LOCK_LABEL = rgb(0x6E6555);
    private static final ColorRGBA SLAB_TOP = rgb(0x332C24);
    private static final ColorRGBA SLAB_MID = rgb(0x241F19);
    private static final ColorRGBA SLAB_LOW = rgb(0x1B1712);
    private static final ColorRGBA SLAB_RIM = rgb(0x4E4638);
    private static final ColorRGBA SOCKET_RIM = rgb(0x453D30);
    private static final ColorRGBA FLESH = rgb(0x6B5B45);

    // ---- the layout, in the pixels the design was drawn at ----

    /** The width the design was drawn for; everything is scaled from it. */
    private static final float DESIGN_WIDTH = 1180f;

    /** Below this the bar is unreadable, above it silly. Both are the design's. */
    private static final float MIN_SCALE = 0.55f;
    private static final float MAX_SCALE = 1.30f;

    private static final float PAD = 10f;
    /** The tallest thing in the bar — the minimap — and so the bar's own height. */
    private static final float BAND = 172f;
    private static final float SLAB_HEIGHT = BAND + PAD * 2f;

    private static final float MINIMAP = BAND;
    private static final float DIVIDER = 3f;
    private static final float DIVIDER_MARGIN = 10f;
    private static final float PORTRAIT = 96f;
    private static final float PORTRAIT_GAP = 10f;
    private static final float VITALS_WIDTH = 190f;
    private static final float BAR_HEIGHT = 17f;
    private static final float XP_HEIGHT = 10f;
    private static final float NAME_HEIGHT = 20f;
    private static final float STATS_HEIGHT = 16f;
    /** Space between one figure and the word after it. */
    private static final float STAT_GAP = 10f;
    private static final float SLOT = 62f;
    private static final float ULT_SLOT = 72f;
    private static final float SLOT_GAP = 9f;
    private static final float POWER_CHIP = 22f;
    private static final float POWER_GAP = 5f;
    private static final float POWERS_TOP_GAP = 7f;
    private static final float DEPTH_WIDTH = 96f;
    /** The depth numeral, alone and with the bottom of the descent beside it. */
    private static final float DEPTH_LARGE = 34f;
    private static final float DEPTH_SMALL = 19f;
    private static final float DROP_DEPTH = 3f;
    /** The line above the bar, for something that has just happened. */
    private static final float NOTE_SIZE = 15f;

    /** The key the design draws an ultimate for. Any other key gets an ordinary slot. */
    private static final char ULTIMATE_KEY = 'R';

    /**
     * How much of a slot the icon fills, leaving stone showing round it.
     *
     * <p>A picture pressed to the edges of a socket stops reading as something set
     * into stone; the margin is what makes it a carving rather than a sticker.
     */
    private static final float ICON_SHARE = 0.62f;

    private static final Logger LOG = Logger.getLogger(HeroPanel.class.getName());

    /** Icons the game named and the client could not find — warned about once each. */
    private final Set<String> missingIcons = new HashSet<>();

    private final AssetManager assets;
    private final BitmapFont font;
    private final Node root = new Node("hero-panel");
    /** Everything but the slab: scaled and centred as one group. */
    private final Node slab = new Node("slab");
    private final Node contents = new Node("contents");

    private BitmapText name;
    private BitmapText rank;
    private BitmapText health;
    private Geometry healthFill;
    private Geometry experienceFill;
    private BitmapText depthNumber;
    private BitmapText depthWord;
    private BitmapText powersWord;
    /** A line above the bar for something that just happened and will stop mattering. */
    private BitmapText note;
    private final List<BitmapText> statLabels = new ArrayList<>();
    private final List<BitmapText> statValues = new ArrayList<>();

    private final List<Slot> slots = new ArrayList<>();
    private final List<PowerChip> powerChips = new ArrayList<>();
    /** The keys the slots were built for; a different set means rebuilding them. */
    private String builtFor = "";
    /** The icons the power strip was built for, same idea. */
    private String powersBuiltFor = "";
    private float screenWidth;
    private float scale = 1f;
    private boolean showing;

    /**
     * What the last status line said, so a second reader — the level-up screen —
     * does not have to parse it again.
     */
    private Reading reading;

    /** The skill waiting for the player to click something, if any. */
    private Character armed;
    /** The slot the mouse is over, if any — it lights to say it can be clicked. */
    private Character hovered;
    /** Wall clock, for the armed slot's breathing — presentation only. */
    private float clock;

    HeroPanel(AssetManager assets, BitmapFont font, Node guiNode, float screenWidth) {
        this.assets = assets;
        this.font = font;
        this.screenWidth = screenWidth;
        guiNode.attachChild(root);
        root.attachChild(slab);
        root.attachChild(contents);
        buildSlab();
        buildMinimapSocket();
        buildPortrait();
        buildVitals();
        buildPowersLabel();
        buildNote();
        buildDepth();
        // Placed now rather than when the first slot is carved: a hero with no
        // skills at all never carves one, and would sit in the screen's corner.
        layOut();
        hide();
    }

    /**
     * Show what the status line describes, or hide the panel if it describes
     * something else. Returns whether the panel took the line — the caller then
     * knows not to print it as text as well.
     */
    boolean show(String status, float seconds) {
        this.clock = seconds;
        var reading = Reading.parse(status);
        this.reading = reading;
        if (reading == null) {
            hide();
            return false;
        }
        showing = true;
        root.setCullHint(Spatial.CullHint.Inherit);
        name.setText(reading.name);
        rank.setText(reading.rank);
        health.setText(Math.round(reading.health) + " / " + Math.round(reading.maxHealth));
        fillTo(healthFill, fraction(reading.health, reading.maxHealth));
        fillTo(experienceFill, fraction(reading.experience, reading.needed));
        depthNumber.setText(reading.depth);
        // A floor out of four is three times the lettering of a floor, and the
        // stone it is carved into did not get any wider. Sized to what it has to
        // say — and only when that changes, because setting a size marks the text
        // for re-layout whether or not it moved.
        float wanted = reading.depth.length() > 4 ? DEPTH_SMALL : DEPTH_LARGE;
        if (depthNumber.getSize() != wanted) {
            depthNumber.setSize(wanted);
        }
        depthWord.setText(reading.depthWord);
        powersWord.setText(reading.powersWord);
        note.setText(reading.note);
        showStats(reading.stats);
        showSkills(reading.skills);
        showPowers(reading.powers);
        return true;
    }

    /** Take the bar out of the scene, so a fresh one can be built at a new size. */
    void destroy() {
        root.removeFromParent();
    }

    void hide() {
        showing = false;
        reading = null;
        root.setCullHint(Spatial.CullHint.Always);
    }

    /** The level-up offer the last status line carried, or {@code null} for none. */
    /**
     * The status line as the panel read it.
     *
     * <p>Shared rather than parsed twice: the game's line carries a level, a
     * floor, and what he just picked up, and those are moments worth hearing as
     * well as worth drawing — see {@link GameSounds}. Two readers of one line
     * would drift the first time the line grew a field.
     */
    Reading reading() {
        return reading;
    }

    Reading.Offer offer() {
        return reading == null ? null : reading.offer();
    }

    /** Whether the bar is on screen at all — nothing may be clicked while it is not. */
    boolean isShowing() {
        return showing;
    }

    /** Which skill is waiting to be pointed at something, so a rebuilt bar can be told. */
    Character armedKey() {
        return armed;
    }

    /**
     * Say which skill is waiting for the player to click something, or {@code null}
     * for none. Its slot lights up and breathes, which is the only thing on screen
     * saying why the next click will not do what a click usually does.
     */
    void arm(Character key) {
        this.armed = key;
    }

    /**
     * Whether a skill looks castable — ready, and not waiting for a level.
     *
     * <p>Used to refuse to arm something that would only be refused a moment later
     * by the simulation, which is the real judge. With no panel on screen there is
     * no opinion to give, and the answer is yes.
     */
    boolean readyToCast(char key) {
        for (var slot : slots) {
            if (slot.key == key) {
                return slot.state == Reading.State.READY;
            }
        }
        return true;
    }

    // ---- what the mouse is over ----

    /** Whether a screen point is on the bar at all, and so not on the world. */
    boolean contains(float screenX, float screenY) {
        return showing && screenY <= heightPixels();
    }

    /**
     * How tall the bar stands in window pixels, or 0 when it is hidden.
     *
     * <p>What the world's bottom edge actually is, as far as anything reaching for
     * it is concerned.
     */
    float heightPixels() {
        return showing ? SLAB_HEIGHT * scale : 0f;
    }

    /**
     * The skill slot under a screen point, or {@code null} for none.
     *
     * <p>The bar is drawn in design pixels and scaled to the window, so a cursor
     * has to come the other way — out of the window and into the design — before
     * it can be compared with anything.
     */
    Character slotAt(float screenX, float screenY) {
        if (!showing) {
            return null;
        }
        for (var slot : slots) {
            if (hits(screenX / scale, screenY / scale, slot.atX, slot.atY, slot.size)) {
                return slot.key;
            }
        }
        return null;
    }

    /**
     * Whether a point is inside a square, in the design's own pixels.
     *
     * <p>Its own method because it is the part that can be wrong without looking
     * wrong: a slot that answers to clicks an inch from where it is drawn is a
     * bug nobody sees until they try to cast something.
     */
    static boolean hits(float x, float y, float left, float bottom, float size) {
        return x >= left && x <= left + size && y >= bottom && y <= bottom + size;
    }

    /** Light the slot the mouse is resting on, or none. */
    void hover(Character key) {
        this.hovered = key;
    }

    /**
     * Set a bar to a fraction of its trough, taking an empty one away entirely
     * rather than squashing it to nothing — a geometry scaled to zero is a
     * degenerate bound, and a hero at no health is exactly when it happens.
     */
    private static void fillTo(Geometry bar, float fraction) {
        bar.setCullHint(fraction <= 0f ? Spatial.CullHint.Always : Spatial.CullHint.Inherit);
        if (fraction > 0f) {
            bar.setLocalScale(fraction, 1f, 1f);
        }
    }

    /** The panel is anchored to the bottom of the window, so a resize moves it. */
    void resize(float width) {
        this.screenWidth = width;
        layOut();
    }

    // ---- the slab everything is cut out of ----

    private void buildSlab() {
        // Rebuilt on every resize, because it is the one piece whose width is the
        // window's rather than the design's.
        slab.detachAllChildren();
        var stone = new Geometry("slab",
                gradient(Math.max(1f, screenWidth / Math.max(scale, 0.0001f)), SLAB_HEIGHT,
                        SLAB_TOP, SLAB_MID, SLAB_LOW));
        stone.setMaterial(vertexColoured());
        attach(slab, stone, 0f, 0f, 0f);
        // The lit rim along the top: a slab of stone catching the room's light,
        // and the line that says the bar is a thing rather than a tint.
        attach(slab, flat("rim", Math.max(1f, screenWidth / Math.max(scale, 0.0001f)), 3f,
                SLAB_RIM), 0f, SLAB_HEIGHT - 3f, 1f);
    }

    /**
     * A carved line between two sections: dark in the middle, fading at both ends,
     * with a hairline of light down one side.
     *
     * <p>A groove rather than a border, because the sections are parts of one
     * stone. A drawn box around each would say they were four things sitting next
     * to each other, which is exactly what this bar is not.
     */
    private Node divider() {
        var node = new Node("divider");
        attach(node, flat("groove", DIVIDER, BAND, DROP), 0f, 0f, 1f);
        attach(node, flat("catch", 1f, BAND, new ColorRGBA(1f, 1f, 1f, 0.05f)),
                DIVIDER, 0f, 2f);
        return node;
    }

    // ---- the minimap's socket ----

    private Node minimapSocket;

    /**
     * The hole the minimap is drawn into: a black recess with a stone lip.
     *
     * <p>Only the frame is here. What goes in it belongs to the client's minimap,
     * which knows about worlds and cameras and has no business being rebuilt every
     * time a hero's health changes — so this says where it goes and the app puts
     * it there.
     */
    private void buildMinimapSocket() {
        minimapSocket = new Node("minimap-socket");
        contents.attachChild(minimapSocket);
        attach(minimapSocket, flat("recess", MINIMAP + 4f, MINIMAP + 4f, DROP), -2f, -2f, 0f);
        attach(minimapSocket, flat("lip", MINIMAP + 2f, MINIMAP + 2f, SOCKET_RIM), -1f, -1f, 1f);
        attach(minimapSocket, flat("hole", MINIMAP, MINIMAP, rgb(0x0C0A08)), 0f, 0f, 2f);
    }

    /**
     * Where the minimap goes, in window pixels: {@code x, y, size}.
     *
     * <p>Handed out rather than drawn here so the two stay in step through a
     * resize without the minimap having to know the bar's arithmetic.
     */
    float[] minimapRect() {
        var at = minimapSocket.getLocalTranslation();
        return new float[] {
            (contents.getLocalTranslation().x + at.x) * scale,
            (contents.getLocalTranslation().y + at.y) * scale,
            MINIMAP * scale,
        };
    }

    // ---- the portrait ----

    private Node portrait;

    /**
     * A square framed portrait with a figure cut into it.
     *
     * <p>Drawn from the same primitives as the rest rather than rendered from the
     * model. A second camera drawing a live hero into a texture is a real thing to
     * want and a real thing to pay for — a render target, a light rig of its own,
     * a frame's worth of work every frame for something that changes when he
     * levels — and what the frame is actually for is telling the player which
     * corner of the screen is his. A silhouette does that.
     */
    private void buildPortrait() {
        portrait = new Node("portrait");
        contents.attachChild(portrait);
        attach(portrait, flat("frame", PORTRAIT + 4f, PORTRAIT + 4f, DROP), -2f, -2f, 0f);
        var face = new Geometry("face", gradient(PORTRAIT, PORTRAIT, rgb(0x4A4034), rgb(0x241E17)));
        face.setMaterial(vertexColoured());
        attach(portrait, face, 0f, 0f, 1f);
        attach(portrait, flat("inner", PORTRAIT + 2f, 2f, rgb(0x5A4E3C)), -1f, PORTRAIT, 2f);

        // Head and shoulders, in the design's own 96-square coordinates.
        var head = new Geometry("head", disc(15f, 16));
        head.setMaterial(unshaded(FLESH));
        attach(portrait, head, 48f, PORTRAIT - 36f, 3f);
        var body = new Geometry("body", polygon(shoulders(), PORTRAIT));
        body.setMaterial(unshaded(FLESH));
        attach(portrait, body, 0f, 0f, 3f);

        // A bow held at his side: the one line saying which hero this is. The
        // limb bows out to the right and the string cuts straight back.
        var bow = new Geometry("bow", strokes(new float[][] {
            bowLimb(), {64, 26, 62, 62},
        }, PORTRAIT));
        bow.setMaterial(lines(TORCH));
        attach(portrait, bow, 0f, 0f, 4f);

        // Torch-coloured corner brackets, the mark of a framed thing.
        float[][] corners = {{3, 3}, {PORTRAIT - 12, 3}, {3, PORTRAIT - 12},
            {PORTRAIT - 12, PORTRAIT - 12}};
        for (int i = 0; i < corners.length; i++) {
            boolean left = i % 2 == 0;
            boolean low = i < 2;
            var bracket = new Geometry("corner", strokes(new float[][] {
                left ? new float[] {0, low ? 9 : 0, 0, low ? 0 : 9, 9, low ? 0 : 9}
                     : new float[] {9, low ? 9 : 0, 9, low ? 0 : 9, 0, low ? 0 : 9},
            }, 0f));
            bracket.setMaterial(lines(new ColorRGBA(TORCH.r, TORCH.g, TORCH.b, 0.75f)));
            attach(portrait, bracket, corners[i][0], corners[i][1], 5f);
        }
    }

    // ---- the vitals column ----

    private Node vitals;

    private void buildVitals() {
        vitals = new Node("vitals");
        contents.attachChild(vitals);

        // Spread over the band the way the design does: name at the top, the
        // stats at the foot, the two bars evenly between them.
        float slackPerGap = (BAND - NAME_HEIGHT - BAR_HEIGHT - XP_HEIGHT - STATS_HEIGHT) / 3f;
        float nameY = BAND - NAME_HEIGHT;
        float healthY = nameY - slackPerGap - BAR_HEIGHT;
        float experienceY = healthY - slackPerGap - XP_HEIGHT;

        name = text(15f, BONE, 0f, nameY, VITALS_WIDTH, BitmapFont.Align.Left);
        rank = text(14f, TORCH, 0f, nameY, VITALS_WIDTH, BitmapFont.Align.Right);
        vitals.attachChild(name);
        vitals.attachChild(rank);

        vitals.attachChild(trough(0f, healthY, VITALS_WIDTH, BAR_HEIGHT));
        healthFill = fill(VITALS_WIDTH - 2f, BAR_HEIGHT - 2f, BLOOD);
        healthFill.setLocalTranslation(1f, healthY + 1f, 1f);
        vitals.attachChild(healthFill);
        health = text(12f, BONE, 0f, healthY + BAR_HEIGHT - 3f, VITALS_WIDTH,
                BitmapFont.Align.Center);
        health.setLocalTranslation(0f, health.getLocalTranslation().y, 2f);
        vitals.attachChild(health);

        vitals.attachChild(trough(0f, experienceY, VITALS_WIDTH, XP_HEIGHT));
        experienceFill = fill(VITALS_WIDTH - 2f, XP_HEIGHT - 2f, ARCANE);
        experienceFill.setLocalTranslation(1f, experienceY + 1f, 1f);
        vitals.attachChild(experienceFill);
    }

    /**
     * The row of figures under the bars: a word and a number, as many as the game
     * sends.
     *
     * <p>Built to fit whatever arrives rather than to a fixed three, for the same
     * reason the skill row is: what a game counts is the game's business.
     */
    private void showStats(List<Reading.Stat> stats) {
        if (statLabels.size() != stats.size()) {
            for (var line : statLabels) {
                line.removeFromParent();
            }
            for (var line : statValues) {
                line.removeFromParent();
            }
            statLabels.clear();
            statValues.clear();
            float each = stats.isEmpty() ? VITALS_WIDTH : VITALS_WIDTH / stats.size();
            for (int i = 0; i < stats.size(); i++) {
                var label = text(12f, LABEL, i * each, 0f, each, BitmapFont.Align.Left);
                // Short of the next word by a clear space, or the figure and the
                // word after it run together into one unreadable string.
                var value = text(13f, BONE, i * each, 0f, each - STAT_GAP,
                        BitmapFont.Align.Right);
                statLabels.add(label);
                statValues.add(value);
                vitals.attachChild(label);
                vitals.attachChild(value);
            }
        }
        for (int i = 0; i < stats.size(); i++) {
            statLabels.get(i).setText(stats.get(i).word());
            statValues.get(i).setText(stats.get(i).value());
        }
    }

    /** A sunken trough for a bar to sit in. */
    private Geometry trough(float x, float y, float width, float height) {
        var geometry = flat("trough", width, height, STONE_DEEP);
        geometry.setLocalTranslation(x, y, 0f);
        return geometry;
    }

    /**
     * The lit part of a bar, drawn at full width and scaled down horizontally.
     * The gradient runs top to bottom, so scaling across it costs nothing.
     */
    private Geometry fill(float width, float height, ColorRGBA colour) {
        var geometry = new Geometry("fill",
                gradient(width, height, lighter(colour, 0.22f), colour, darker(colour, 0.30f)));
        geometry.setMaterial(vertexColoured());
        return geometry;
    }

    // ---- the depth marker ----

    private Node depth;

    /**
     * The line just above the bar, for a thing that has happened.
     *
     * <p>Not the banner: the banner interrupts and sits in the middle of the
     * screen, which is right for "you died" and wrong for "you picked up a sword".
     * This sits on the edge of what he is already looking at, and goes away on its
     * own — the game decides when by simply not sending it any more.
     */
    private void buildNote() {
        note = text(NOTE_SIZE, TORCH, 0f, SLAB_HEIGHT + 6f, DESIGN_WIDTH,
                BitmapFont.Align.Center);
        note.setLocalTranslation(0f, note.getLocalTranslation().y, 2f);
        root.attachChild(note);
    }

    private void buildDepth() {
        depth = new Node("depth");
        contents.attachChild(depth);
        depthWord = text(11f, LABEL, 0f, BAND / 2f - 20f, DEPTH_WIDTH, BitmapFont.Align.Center);
        depthNumber = text(DEPTH_LARGE, TORCH, 0f, BAND / 2f - 12f, DEPTH_WIDTH,
                BitmapFont.Align.Center);
        depth.attachChild(depthWord);
        depth.attachChild(depthNumber);
    }

    // ---- the skill row ----

    /**
     * One carved slot. The stone is built once; what changes every frame is the
     * glyph's colour, the shadow sweeping round as a cooldown runs off, and the
     * text over it.
     */
    private static final class Slot {
        private final char key;
        private final float size;
        private final Node node = new Node("slot");
        /**
         * Where it sits in the design, measured from the bar's own corner.
         *
         * <p>Recorded rather than read back off the scene graph: a slot's own
         * translation is relative to the row it is in, which is relative to the
         * block that is centred, and adding those up by hand at click time is how
         * a click lands one column to the left of where it looked.
         */
        private float atX;
        private float atY;
        private Geometry deadStone;
        private Geometry glyph;
        private Geometry sweep;
        private Geometry ring;
        private Geometry warm;
        private BitmapText seconds;
        private BitmapText locked;
        private float sweptTo = -1f;
        private Reading.State state = Reading.State.READY;

        private Slot(char key, float size) {
            this.key = key;
            this.size = size;
        }
    }

    private final Node skillRow = new Node("skills");

    private void showSkills(List<Reading.SkillReading> reading) {
        // The picture is part of what a slot IS, not part of what it is doing, so
        // a changed icon rebuilds the row exactly as a changed key does. Which is
        // also what lets an icon be swapped in the file and seen without a build.
        var carving = new StringBuilder();
        for (var skill : reading) {
            carving.append(skill.key()).append(skill.icon()).append(';');
        }
        if (!carving.toString().equals(builtFor)) {
            buildSlots(reading);
            builtFor = carving.toString();
        }
        for (int i = 0; i < slots.size() && i < reading.size(); i++) {
            dress(slots.get(i), reading.get(i));
        }
    }

    private void buildSlots(List<Reading.SkillReading> reading) {
        for (var slot : slots) {
            slot.node.removeFromParent();
        }
        slots.clear();
        if (skillRow.getParent() == null) {
            contents.attachChild(skillRow);
        }
        for (var skill : reading) {
            var slot = new Slot(skill.key(), skill.key() == ULTIMATE_KEY ? ULT_SLOT : SLOT);
            carve(slot, skill.icon());
            slots.add(slot);
            skillRow.attachChild(slot.node);
        }
        layOut();
    }

    /** Cut one slot out of the stone: drop, edge, face, lit lip, inner shadow. */
    private void carve(Slot slot, String icon) {
        float size = slot.size;
        attach(slot.node, flat("drop", size + 4f, size + 4f, DROP), -2f, -2f - DROP_DEPTH, 0f);
        // A torch-coloured lip around the socket, shown only while this is the
        // skill the next click belongs to. Under the edge, so it reads as the
        // stone catching light rather than as a box drawn on top of it.
        slot.ring = flat("armed", size + 8f, size + 8f, TORCH);
        attach(slot.node, slot.ring, -4f, -4f, 1f);
        slot.ring.setCullHint(Spatial.CullHint.Always);
        attach(slot.node, flat("edge", size + 4f, size + 4f, EDGE), -2f, -2f, 2f);

        var stone = new Geometry("stone", gradient(size, size, STONE_LIT, STONE));
        stone.setMaterial(vertexColoured());
        attach(slot.node, stone, 0f, 0f, 3f);

        slot.deadStone = new Geometry("stone-dead",
                gradient(size, size, STONE_DEAD_LIT, STONE_DEAD));
        slot.deadStone.setMaterial(vertexColoured());
        attach(slot.node, slot.deadStone, 0f, 0f, 4f);

        // The carved illusion: light catches the top lip, shadow pools at the foot.
        attach(slot.node, flat("lip", size, 2f, new ColorRGBA(1f, 1f, 1f, 0.07f)),
                0f, size - 2f, 5f);
        attach(slot.node, flat("pool", size, 6f, new ColorRGBA(0f, 0f, 0f, 0.45f)),
                0f, 0f, 5f);

        // The wash under the cursor. Over the stone but under everything that
        // means something, so hovering brightens the slot without hiding its state.
        slot.warm = flat("warm", size, size, new ColorRGBA(1f, 0.85f, 0.6f, 0.12f));
        attach(slot.node, slot.warm, 0f, 0f, 5.5f);
        slot.warm.setCullHint(Spatial.CullHint.Always);

        slot.glyph = picture(icon, size);
        if (slot.glyph == null) {
            slot.glyph = new Geometry("glyph", Glyphs.of(String.valueOf(slot.key), size * 0.53f));
            slot.glyph.setMaterial(lines(TORCH));
            attach(slot.node, slot.glyph, size / 2f, size / 2f, 6f);
        } else {
            // A quad grows from its own corner, so it is placed rather than centred.
            float inset = size * (1f - ICON_SHARE) / 2f;
            attach(slot.node, slot.glyph, inset, inset, 6f);
        }

        slot.sweep = new Geometry("sweep", new Mesh());
        slot.sweep.setMaterial(unshaded(new ColorRGBA(0.031f, 0.024f, 0.02f, 0.82f)));
        attach(slot.node, slot.sweep, 0f, 0f, 7f);

        slot.seconds = text(17f, BONE, 0f, size / 2f - 6f, size, BitmapFont.Align.Center);
        slot.seconds.setLocalTranslation(0f, slot.seconds.getLocalTranslation().y, 8f);
        slot.node.attachChild(slot.seconds);

        slot.locked = text(12f, LOCK_LABEL, 0f, size / 2f - 4f, size, BitmapFont.Align.Center);
        slot.locked.setLocalTranslation(0f, slot.locked.getLocalTranslation().y, 8f);
        slot.node.attachChild(slot.locked);

        var key = text(14f, BONE, 0f, 1f, size - 3f, BitmapFont.Align.Right);
        key.setText(String.valueOf(slot.key));
        key.setLocalTranslation(0f, key.getLocalTranslation().y, 8f);
        slot.node.attachChild(key);
    }

    /**
     * The game's own picture for a slot, or {@code null} to fall back to the letter
     * of the key.
     *
     * <p>The image is white and is <em>tinted</em> as it is drawn, exactly as the
     * letter was: one file then serves a slot that is ready, one that is reloading
     * and one that is still locked, and {@link #dress} goes on setting a colour
     * without caring which of the two it has.
     *
     * <p>A picture that will not load is not a reason to lose the panel. The game
     * names its own art and the client cannot check the spelling — so a missing one
     * is said once, in the log, and the slot falls back to what it looked like
     * before there were any pictures.
     */
    private Geometry picture(String icon, float size) {
        var texture = iconTexture(assets, icon, missingIcons);
        if (texture == null) {
            return null;
        }
        float side = size * ICON_SHARE;
        var quad = new Geometry("icon", new Quad(side, side));
        var material = unshaded(TORCH);
        material.setTexture("ColorMap", texture);
        quad.setMaterial(material);
        return quad;
    }

    /**
     * Load an icon, or give back {@code null} — for no name, or for a name nothing
     * answers to.
     *
     * <p>Static and handed everything it needs so that the fallback can be held
     * still by a test: what happens when the file is not there is the part of this
     * nobody exercises by playing.
     */
    static Texture iconTexture(AssetManager assets, String icon, Set<String> missing) {
        if (assets == null || icon == null || icon.isBlank()) {
            return null;
        }
        try {
            return assets.loadTexture(icon);
        } catch (RuntimeException e) {
            if (missing.add(icon)) {
                LOG.warning(() -> "skill icon not found: " + icon + " (" + e.getMessage()
                        + ") — drawing the key instead");
            }
            return null;
        }
    }

    private void dress(Slot slot, Reading.SkillReading skill) {
        slot.state = skill.state;
        boolean locked = skill.state == Reading.State.LOCKED;
        boolean cooling = skill.state == Reading.State.COOLING;
        slot.deadStone.setCullHint(locked ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        slot.glyph.getMaterial().setColor("Color", linear(
                locked ? DEAD.mult(new ColorRGBA(1f, 1f, 1f, 0.5f))
                        : cooling ? GLYPH_COLD : TORCH));
        slot.locked.setText(locked ? skill.label : "");
        slot.seconds.setText(cooling ? skill.label : "");
        sweepTo(slot, cooling ? skill.left : 0f);
        light(slot);
    }

    /**
     * The armed slot's lip, breathing so it cannot be mistaken for the ordinary
     * ready glow. Slow — twice a second, between two thirds and full — because the
     * point is "this one is waiting", not "look at me".
     *
     * <p>The hover wash is separate and quieter: it says a slot can be clicked,
     * which every ready one can, so it must not shout.
     */
    private void light(Slot slot) {
        boolean waiting = armed != null && armed == slot.key;
        slot.ring.setCullHint(waiting ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        slot.warm.setCullHint(hovered != null && hovered == slot.key
                && slot.state == Reading.State.READY
                ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        if (!waiting) {
            return;
        }
        float breath = 0.83f + 0.17f * FastMath.sin(clock * FastMath.TWO_PI * 0.9f);
        slot.ring.getMaterial().setColor("Color",
                linear(new ColorRGBA(TORCH.r, TORCH.g, TORCH.b, breath)));
    }

    /** Rebuild the cooldown shadow, but only when it has actually moved. */
    private void sweepTo(Slot slot, float remaining) {
        if (Math.abs(remaining - slot.sweptTo) < 0.002f) {
            return;
        }
        slot.sweptTo = remaining;
        slot.sweep.setMesh(sweep(slot.size, remaining));
    }

    // ---- the powers he has picked up ----

    /** One mark in the strip under the skills: an icon, and how many he holds. */
    private static final class PowerChip {
        private final Node node = new Node("power");
        private BitmapText count;
    }

    private final Node powerRow = new Node("powers");

    private void buildPowersLabel() {
        contents.attachChild(powerRow);
        powersWord = text(12f, LABEL, 0f, 4f, 70f, BitmapFont.Align.Left);
        powerRow.attachChild(powersWord);
    }

    private void showPowers(List<Reading.PowerReading> reading) {
        var signature = new StringBuilder();
        for (var power : reading) {
            signature.append(power.icon()).append(':').append(power.count()).append(',');
        }
        if (signature.toString().equals(powersBuiltFor)) {
            return;
        }
        powersBuiltFor = signature.toString();
        for (var chip : powerChips) {
            chip.node.removeFromParent();
        }
        powerChips.clear();
        float x = powersWord.getLocalTranslation().x + 62f;
        for (var power : reading) {
            var chip = new PowerChip();
            attach(chip.node, flat("chip-edge", POWER_CHIP, POWER_CHIP, SOCKET_RIM), 0f, 0f, 0f);
            attach(chip.node, flat("chip", POWER_CHIP - 2f, POWER_CHIP - 2f, rgb(0x241F19)),
                    1f, 1f, 1f);
            var glyph = new Geometry("chip-glyph", Glyphs.of(power.icon(), POWER_CHIP * 0.55f));
            glyph.setMaterial(lines(TORCH));
            attach(chip.node, glyph, POWER_CHIP / 2f, POWER_CHIP / 2f, 2f);
            if (power.count() > 1) {
                // The number rides the corner rather than replacing the icon:
                // which power it is matters more than how many of it he has.
                chip.count = text(11f, TORCH, 0f, -3f, POWER_CHIP + 6f, BitmapFont.Align.Right);
                chip.count.setText("x" + power.count());
                chip.count.setLocalTranslation(0f, chip.count.getLocalTranslation().y, 3f);
                chip.node.attachChild(chip.count);
            }
            chip.node.setLocalTranslation(x, 0f, 0f);
            powerRow.attachChild(chip.node);
            powerChips.add(chip);
            x += POWER_CHIP + POWER_GAP;
        }
    }

    // ---- placing the whole thing ----

    private void layOut() {
        this.scale = Math.clamp(screenWidth / DESIGN_WIDTH, MIN_SCALE, MAX_SCALE);
        root.setLocalScale(scale);
        buildSlab();
        // Centred on the window rather than on the design, so it sits over the
        // middle of the bar however wide the window is.
        note.setBox(new Rectangle(0f, SLAB_HEIGHT + 6f + NOTE_SIZE,
                screenWidth / scale, NOTE_SIZE * 1.4f));

        float skillsWidth = 0f;
        for (int i = 0; i < slots.size(); i++) {
            skillsWidth += slots.get(i).size + (i == 0 ? 0f : SLOT_GAP);
        }
        float gap = DIVIDER + DIVIDER_MARGIN * 2f;
        float who = PORTRAIT + PORTRAIT_GAP + VITALS_WIDTH;
        float total = MINIMAP + gap + who + gap + Math.max(skillsWidth, 160f) + gap + DEPTH_WIDTH;

        // The bar spans the window; its contents are centred on it, so a wide
        // screen puts empty stone at both ends rather than all of it at one.
        float left = Math.max(PAD, (screenWidth / scale - total) / 2f);
        contents.setLocalTranslation(left, PAD, 1f);

        float x = 0f;
        minimapSocket.setLocalTranslation(x, 0f, 0f);
        x += MINIMAP + DIVIDER_MARGIN;
        placeDivider(0, x);
        x += DIVIDER + DIVIDER_MARGIN;

        // The portrait sits at the top of the band, as the design draws it; the
        // vitals fill the whole height beside it.
        portrait.setLocalTranslation(x, BAND - PORTRAIT, 0f);
        vitals.setLocalTranslation(x + PORTRAIT + PORTRAIT_GAP, 0f, 0f);
        x += who + DIVIDER_MARGIN;
        placeDivider(1, x);
        x += DIVIDER + DIVIDER_MARGIN;

        // Skills and the powers strip are one column, centred in the band.
        float columnHeight = ULT_SLOT + POWERS_TOP_GAP + POWER_CHIP;
        float columnBottom = (BAND - columnHeight) / 2f;
        powerRow.setLocalTranslation(x, columnBottom, 0f);
        skillRow.setLocalTranslation(x, columnBottom + POWER_CHIP + POWERS_TOP_GAP, 0f);
        float slotX = 0f;
        float rowY = columnBottom + POWER_CHIP + POWERS_TOP_GAP;
        for (var slot : slots) {
            // Tops aligned, so an ultimate is bigger by hanging lower — which is
            // how the design draws it and how the eye finds it.
            slot.node.setLocalTranslation(slotX, ULT_SLOT - slot.size, 0f);
            slot.atX = left + x + slotX;
            slot.atY = PAD + rowY + ULT_SLOT - slot.size;
            slotX += slot.size + SLOT_GAP;
        }
        x += Math.max(skillsWidth, 160f) + DIVIDER_MARGIN;
        placeDivider(2, x);
        x += DIVIDER + DIVIDER_MARGIN;

        depth.setLocalTranslation(x, 0f, 0f);
    }

    private final List<Node> dividers = new ArrayList<>();

    private void placeDivider(int index, float x) {
        while (dividers.size() <= index) {
            var line = divider();
            dividers.add(line);
            contents.attachChild(line);
        }
        dividers.get(index).setLocalTranslation(x, 0f, 0f);
    }

    // ---- meshes ----

    /**
     * A flat rectangle of one colour. Everything in the panel that is not a
     * gradient, a glyph or a shadow is one of these.
     */
    private Geometry flat(String what, float width, float height, ColorRGBA colour) {
        var geometry = new Geometry(what, new Quad(width, height));
        geometry.setMaterial(unshaded(colour));
        return geometry;
    }

    /**
     * A rectangle shading evenly from the first colour at the top to the last at
     * the bottom, as a strip of vertex-coloured quads.
     */
    private static Mesh gradient(float width, float height, ColorRGBA... rawStops) {
        var stops = new ColorRGBA[rawStops.length];
        for (int i = 0; i < rawStops.length; i++) {
            stops[i] = linear(rawStops[i]);
        }
        int rows = stops.length;
        var positions = new float[rows * 2 * 3];
        var colours = new float[rows * 2 * 4];
        var indices = new int[(rows - 1) * 6];
        for (int row = 0; row < rows; row++) {
            float y = height * (1f - row / (float) (rows - 1));
            for (int side = 0; side < 2; side++) {
                int vertex = row * 2 + side;
                positions[vertex * 3] = side == 0 ? 0f : width;
                positions[vertex * 3 + 1] = y;
                positions[vertex * 3 + 2] = 0f;
                var colour = stops[row];
                colours[vertex * 4] = colour.r;
                colours[vertex * 4 + 1] = colour.g;
                colours[vertex * 4 + 2] = colour.b;
                colours[vertex * 4 + 3] = colour.a;
            }
            if (row < rows - 1) {
                int base = row * 2;
                int at = row * 6;
                indices[at] = base;
                indices[at + 1] = base + 1;
                indices[at + 2] = base + 3;
                indices[at + 3] = base;
                indices[at + 4] = base + 3;
                indices[at + 5] = base + 2;
            }
        }
        var mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(positions));
        mesh.setBuffer(VertexBuffer.Type.Color, 4, BufferUtils.createFloatBuffer(colours));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indices));
        mesh.updateBound();
        return mesh;
    }

    /** A filled circle, centred on its own origin — the portrait's head. */
    private static Mesh disc(float radius, int segments) {
        var positions = new float[(segments + 2) * 3];
        for (int i = 0; i <= segments; i++) {
            float angle = FastMath.TWO_PI * i / segments;
            positions[(i + 1) * 3] = FastMath.cos(angle) * radius;
            positions[(i + 1) * 3 + 1] = FastMath.sin(angle) * radius;
        }
        var indices = new int[segments * 3];
        for (int i = 0; i < segments; i++) {
            indices[i * 3 + 1] = i + 1;
            indices[i * 3 + 2] = i + 2;
        }
        return meshOf(positions, indices);
    }

    /**
     * A filled shape from a list of points written in screen order — y downward,
     * as a designer writes them — flipped once here.
     */
    private static Mesh polygon(float[] points, float height) {
        int count = points.length / 2;
        var positions = new float[count * 3];
        for (int i = 0; i < count; i++) {
            positions[i * 3] = points[i * 2];
            positions[i * 3 + 1] = height - points[i * 2 + 1];
        }
        var indices = new int[(count - 2) * 3];
        for (int i = 0; i < count - 2; i++) {
            indices[i * 3] = 0;
            indices[i * 3 + 1] = i + 1;
            indices[i * 3 + 2] = i + 2;
        }
        return meshOf(positions, indices);
    }

    /** Open line paths, in the same screen-order coordinates. */
    private static Mesh strokes(float[][] paths, float height) {
        int segments = 0;
        for (var path : paths) {
            segments += path.length / 2 - 1;
        }
        var positions = new float[segments * 2 * 3];
        int at = 0;
        for (var path : paths) {
            for (int i = 0; i + 3 < path.length; i += 2) {
                positions[at] = path[i];
                positions[at + 1] = height - path[i + 1];
                positions[at + 3] = path[i + 2];
                positions[at + 4] = height - path[i + 3];
                at += 6;
            }
        }
        var mesh = new Mesh();
        mesh.setMode(Mesh.Mode.Lines);
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(positions));
        mesh.updateBound();
        return mesh;
    }

    /**
     * Shoulders: a dome rather than a peak, so the figure reads as a person seen
     * from the front rather than as a roof.
     */
    private static float[] shoulders() {
        int steps = 12;
        var points = new float[(steps + 3) * 2];
        points[0] = 28f;
        points[1] = 96f;
        for (int i = 0; i <= steps; i++) {
            float angle = FastMath.PI * i / steps;
            points[(i + 1) * 2] = 48f - FastMath.cos(angle) * 20f;
            points[(i + 1) * 2 + 1] = 96f - 14f - FastMath.sin(angle) * 10f;
        }
        points[(steps + 2) * 2] = 68f;
        points[(steps + 2) * 2 + 1] = 96f;
        return points;
    }

    /** The bow's limb: a shallow curve from shoulder height down past his hip. */
    private static float[] bowLimb() {
        int steps = 10;
        var points = new float[(steps + 1) * 2];
        for (int i = 0; i <= steps; i++) {
            float along = i / (float) steps;
            points[i * 2] = 64f + FastMath.sin(FastMath.PI * along) * 8f;
            points[i * 2 + 1] = 26f + 36f * along;
        }
        return points;
    }

    private static Mesh meshOf(float[] positions, int[] indices) {
        var mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(positions));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indices));
        mesh.updateBound();
        return mesh;
    }

    /**
     * The shadow still lying over a slot: a wedge from twelve o'clock, clockwise,
     * covering the part of the cooldown that has not run off yet.
     *
     * <p>Cut to the slot's square rather than to a circle, because a round shadow
     * inside a square socket reads as a coin sitting on the stone rather than as
     * the stone itself being uncovered. The corners fall on segment boundaries, so
     * they come out sharp.
     */
    private static Mesh sweep(float size, float remaining) {
        if (remaining <= 0f) {
            return new Mesh();
        }
        int segments = Math.max(1, Math.round(48 * Math.min(1f, remaining)));
        float step = FastMath.TWO_PI * Math.min(1f, remaining) / segments;
        float half = size / 2f;
        var positions = new float[(segments + 2) * 3];
        positions[0] = half;
        positions[1] = half;
        for (int i = 0; i <= segments; i++) {
            float angle = i * step;
            float dx = FastMath.sin(angle);
            float dy = FastMath.cos(angle);
            float reach = half / Math.max(Math.abs(dx), Math.abs(dy));
            positions[(i + 1) * 3] = half + dx * reach;
            positions[(i + 1) * 3 + 1] = half + dy * reach;
        }
        var indices = new int[segments * 3];
        for (int i = 0; i < segments; i++) {
            indices[i * 3] = 0;
            indices[i * 3 + 1] = i + 1;
            indices[i * 3 + 2] = i + 2;
        }
        return meshOf(positions, indices);
    }

    // ---- materials & text ----

    private Material unshaded(ColorRGBA colour) {
        var material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", linear(colour));
        material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        material.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
        material.getAdditionalRenderState().setDepthTest(false);
        return material;
    }

    private Material vertexColoured() {
        var material = unshaded(ColorRGBA.White);
        material.setBoolean("VertexColor", true);
        return material;
    }

    private Material lines(ColorRGBA colour) {
        var material = unshaded(colour);
        material.getAdditionalRenderState().setLineWidth(2f);
        return material;
    }

    /**
     * A line of text in a box, so alignment does the placing. The size is the
     * design's pixel size; the font's own is whatever the client shipped.
     */
    private BitmapText text(float size, ColorRGBA colour, float x, float y, float width,
            BitmapFont.Align align) {
        var line = new BitmapText(font);
        line.setSize(size);
        line.setColor(linear(colour));
        line.setBox(new Rectangle(x, y + size, width, size * 1.4f));
        line.setAlignment(align);
        return line;
    }

    private static void attach(Node parent, Spatial child, float x, float y, float z) {
        child.setLocalTranslation(x, y, z);
        parent.attachChild(child);
    }

    private static float fraction(float part, float whole) {
        return whole <= 0f ? 0f : Math.max(0f, Math.min(1f, part / whole));
    }

    /**
     * The same colour, ready to go into a vertex buffer.
     *
     * <p>The client renders into an sRGB frame buffer, and a material's colour is
     * converted on its way to the shader — but a colour written straight into a
     * mesh is not, so the same hex came out two shades lighter as a gradient than
     * as a flat quad. Blood red arrived as pink. This is the conversion the
     * material path performs, done by hand for the path that skips it.
     */
    static ColorRGBA linear(ColorRGBA colour) {
        return new ColorRGBA(toLinear(colour.r), toLinear(colour.g), toLinear(colour.b),
                colour.a);
    }

    private static float toLinear(float channel) {
        return channel <= 0.04045f
                ? channel / 12.92f
                : (float) Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private static ColorRGBA rgb(int hex) {
        return new ColorRGBA(((hex >> 16) & 0xFF) / 255f, ((hex >> 8) & 0xFF) / 255f,
                (hex & 0xFF) / 255f, 1f);
    }

    private static ColorRGBA lighter(ColorRGBA colour, float towardsWhite) {
        return new ColorRGBA(
                colour.r + (1f - colour.r) * towardsWhite,
                colour.g + (1f - colour.g) * towardsWhite,
                colour.b + (1f - colour.b) * towardsWhite, colour.a);
    }

    private static ColorRGBA darker(ColorRGBA colour, float towardsBlack) {
        float keep = 1f - towardsBlack;
        return new ColorRGBA(colour.r * keep, colour.g * keep, colour.b * keep, colour.a);
    }

    // ---- what the status line says ----

    /**
     * The status line, read. Nothing here knows what a skill does — only what the
     * slot has to look like — which is the whole reason the game sends finished
     * words and the client sends none.
     */
    record Reading(String name, String rank, float health, float maxHealth,
            float experience, float needed, String depth, String depthWord,
            String powersWord, String note, List<Stat> stats, List<SkillReading> skills,
            List<PowerReading> powers, Offer offer) {

        enum State { READY, COOLING, LOCKED }

        /**
         * One slot: its key, the picture to draw in it, its state, the words on it,
         * and how much shadow is left.
         *
         * <p>{@code icon} is a path to an image, given by the game, and empty when
         * the game named none — which is not a failure: a slot with no picture is
         * drawn with the letter of its key, as every slot was before there were any
         * pictures. The client never invents one, because it has three other games
         * to serve and no idea where any of them keeps its art.
         */
        record SkillReading(char key, String icon, State state, String label, float left) {
        }

        /** One figure under the bars: what it is called and what it is. */
        record Stat(String word, String value) {
        }

        /** One mark in the powers strip: which drawing, and how many he holds. */
        record PowerReading(String icon, int count) {
        }

        /**
         * The level-up screen, when one is up: which offer it is, its words, its
         * cards.
         *
         * <p>{@code id} is the game's own name for this offer and means nothing
         * here beyond "not the same one as before" — which is all the client needs
         * to know it has already been answered.
         */
        record Offer(int id, String title, String hint, List<Card> cards) {
        }

        /** One card on the level-up screen. */
        record Card(String icon, String name, String description) {
        }

        static Reading parse(String status) {
            if (status == null || !status.startsWith("name=")) {
                return null;
            }
            String name = "";
            String rank = "";
            String depth = "";
            String depthWord = "";
            String powersWord = "";
            String note = "";
            var health = new float[] {0f, 0f};
            var experience = new float[] {0f, 0f};
            var skills = new ArrayList<SkillReading>();
            var stats = new ArrayList<Stat>();
            var powers = new ArrayList<PowerReading>();
            var cards = new ArrayList<Card>();
            var offerHead = new String[] {null, null, null};
            for (var field : status.split("\\|")) {
                int split = field.indexOf('=');
                if (split < 0) {
                    return null;
                }
                var value = field.substring(split + 1);
                switch (field.substring(0, split)) {
                    case "name" -> name = value;
                    case "rank" -> rank = value;
                    case "depth" -> depth = value;
                    case "depthWord" -> depthWord = value;
                    case "pwWord" -> powersWord = value;
                    case "note" -> note = value;
                    case "hp" -> health = pair(value);
                    case "xp" -> experience = pair(value);
                    case "skill" -> skills.add(skill(value));
                    case "stat" -> stats.add(stat(value));
                    case "pw" -> powers.add(power(value));
                    case "offer" -> offerHead[0] = value;
                    case "opt" -> cards.add(card(value));
                    // How the floor is drawn. Nothing on the panel, but the line
                    // is one line: a field this panel has no picture for still has
                    // to be a field it recognises, or it would refuse the whole
                    // thing as somebody else's.
                    case "look" -> { }
                    default -> {
                        return null; // a field this client does not know: not ours
                    }
                }
                if (health == null || experience == null || skills.contains(null)
                        || stats.contains(null) || powers.contains(null)
                        || cards.contains(null)) {
                    return null;
                }
            }
            return new Reading(name, rank, health[0], health[1], experience[0], experience[1],
                    depth, depthWord, powersWord, note, List.copyOf(stats), List.copyOf(skills),
                    List.copyOf(powers), offer(offerHead[0], cards));
        }

        private static float[] pair(String value) {
            var halves = value.split("/");
            if (halves.length != 2) {
                return null;
            }
            try {
                return new float[] {Float.parseFloat(halves[0]), Float.parseFloat(halves[1])};
            } catch (NumberFormatException e) {
                return null;
            }
        }

        /**
         * {@code Q,icon,ready} — {@code W,icon,cool,72,165} — {@code R,icon,lock,5-daraja}.
         *
         * <p>The icon comes second because it belongs to the slot rather than to
         * the state: a skill's picture does not change when it goes on cooldown,
         * and putting it before the state keeps the three states the same shape as
         * each other. It may be empty.
         */
        private static SkillReading skill(String value) {
            var parts = value.split(",", -1);
            if (parts.length < 3 || parts[0].length() != 1) {
                return null;
            }
            char key = parts[0].charAt(0);
            String icon = parts[1];
            try {
                return switch (parts[2]) {
                    case "ready" -> new SkillReading(key, icon, State.READY, "", 0f);
                    case "lock" -> parts.length < 4 ? null
                            : new SkillReading(key, icon, State.LOCKED, parts[3], 1f);
                    case "cool" -> parts.length < 5 ? null : cooling(key, icon,
                            Integer.parseInt(parts[3]), Integer.parseInt(parts[4]));
                    default -> null;
                };
            } catch (NumberFormatException e) {
                return null;
            }
        }

        /** {@code Zarba,34} — a word and whatever the game counts under it. */
        private static Stat stat(String value) {
            int comma = value.indexOf(',');
            return comma < 0 ? null
                    : new Stat(value.substring(0, comma), value.substring(comma + 1));
        }

        /** {@code shot,2} — which drawing, and how many of it. */
        private static PowerReading power(String value) {
            var parts = value.split(",");
            if (parts.length < 2) {
                return null;
            }
            try {
                return new PowerReading(parts[0], Integer.parseInt(parts[1]));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        /** {@code shot,O'tkir uch,Q zarari +25%} — a drawing and two lines of words. */
        private static Card card(String value) {
            var parts = value.split(",", 3);
            return parts.length < 3 ? null : new Card(parts[0], parts[1], parts[2]);
        }

        /** {@code 3,8-daraja,Bittasini tanlang}, and the cards that followed it. */
        private static Offer offer(String head, List<Card> cards) {
            if (head == null || cards.isEmpty()) {
                return null;
            }
            var parts = head.split(",", 3);
            if (parts.length < 3) {
                return null;
            }
            try {
                return new Offer(Integer.parseInt(parts[0]), parts[1], parts[2],
                        List.copyOf(cards));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        /**
         * Frames on the wire, seconds on the screen. The simulation counts frames
         * and must, but a player reading a number off a slot thinks in seconds —
         * and the conversion is the engine's own constant, not a second copy of it.
         */
        private static SkillReading cooling(char key, String icon, int left, int total) {
            float seconds = left / (float) GameConstants.LOGICFRAMES_PER_SECOND;
            var label = seconds >= 10f
                    ? String.valueOf(Math.round(seconds))
                    : String.format(java.util.Locale.ROOT, "%.1f", seconds);
            return new SkillReading(key, icon, State.COOLING, label,
                    total <= 0 ? 0f : left / (float) total);
        }
    }

    /**
     * The marks cut into the slots and the cards, as line drawings in a 24-by-24
     * square.
     *
     * <p>Drawn here rather than loaded, because an icon that is four straight lines
     * is smaller as four straight lines than as a file, and because a glyph that
     * is drawn takes the panel's colours without a second copy of the palette
     * living in an image.
     *
     * <p>They are pictures of what the thing is rather than of what it hits: an
     * arrow leaving, a burst around a centre, a stride, a star. The names are a
     * small vocabulary a game picks from, the same way it picks the words — so a
     * dungeon says "clock" and gets a clock without this class knowing what a
     * cooldown is.
     */
    private static final class Glyphs {

        private Glyphs() {
        }

        /** An arrow flying up and to the right — the shot that goes further. */
        private static final float[][] SHOT = {
            {4, 20, 20, 4}, {14, 4, 20, 4, 20, 10}, {9, 15, 6, 18},
        };

        /** A ring with rays off it — something going off where he stands. */
        private static final float[][] BURST = ring();

        /** A stride: a long step and the ground ahead of it. */
        private static final float[][] DASH = {
            {3, 18, 8, 18, 11, 12, 14, 21, 17, 6}, {18, 6, 21, 6},
        };

        /** A star, for the one that is not like the others. */
        private static final float[][] STAR = {
            {12, 2, 14.6f, 8.4f, 21, 11, 14.6f, 13.6f, 12, 20, 9.4f, 13.6f, 3, 11, 9.4f, 8.4f,
                12, 2},
        };

        /** A dial with a hand — time, and so a cooldown. */
        private static final float[][] CLOCK = clock();

        /** A heart — health coming back. */
        private static final float[][] HEART = {
            {12, 21, 5, 15, 3, 11, 4, 7, 8, 5.5f, 12, 8, 16, 5.5f, 20, 7, 21, 11, 19, 15, 12, 21},
        };

        /** A boot in stride — moving faster. */
        private static final float[][] BOOT = {
            {8, 3, 8, 14, 5, 17, 5, 20, 18, 20, 18, 17, 13, 14, 13, 3, 8, 3},
        };

        /** A plus — more of something. */
        private static final float[][] PLUS = {
            {12, 5, 12, 19}, {5, 12, 19, 12},
        };

        /** A doubling mark — the same thing again. */
        private static final float[][] TIMES = {
            {5, 6, 12, 13}, {12, 6, 5, 13}, {14, 18, 16, 16, 16, 20, 20, 20},
        };

        /** Anything the design did not draw: a plain lozenge, so nothing is blank. */
        private static final float[][] PLAIN = {
            {12, 4, 19, 12, 12, 20, 5, 12, 12, 4},
        };

        private static float[][] ring() {
            var paths = new float[9][];
            paths[0] = circle(12f, 12f, 4f, 16);
            float[][] rays = {
                {12, 3, 12, 6}, {12, 18, 12, 21}, {3, 12, 6, 12}, {18, 12, 21, 12},
                {5.6f, 5.6f, 7.7f, 7.7f}, {16.3f, 16.3f, 18.4f, 18.4f},
                {18.4f, 5.6f, 16.3f, 7.7f}, {7.7f, 16.3f, 5.6f, 18.4f},
            };
            System.arraycopy(rays, 0, paths, 1, rays.length);
            return paths;
        }

        private static float[][] clock() {
            return new float[][] {circle(12f, 12f, 9f, 20), {12, 7, 12, 12, 15, 14}};
        }

        private static float[] circle(float x, float y, float radius, int steps) {
            var points = new float[(steps + 1) * 2];
            for (int i = 0; i <= steps; i++) {
                float angle = FastMath.TWO_PI * i / steps;
                points[i * 2] = x + radius * FastMath.cos(angle);
                points[i * 2 + 1] = y + radius * FastMath.sin(angle);
            }
            return points;
        }

        /**
         * One glyph as a line mesh, centred on the origin and scaled to {@code size}.
         * The drawings are in screen order — y downwards, as a designer writes
         * them — and the flip to the client's upward y happens once, here.
         */
        private static Mesh of(String named, float size) {
            var paths = switch (named) {
                case "Q", "shot" -> SHOT;
                case "W", "burst" -> BURST;
                case "E", "dash" -> DASH;
                case "R", "star" -> STAR;
                case "clock" -> CLOCK;
                case "heart" -> HEART;
                case "boot" -> BOOT;
                case "plus" -> PLUS;
                case "times" -> TIMES;
                default -> PLAIN;
            };
            int segments = 0;
            for (var path : paths) {
                segments += path.length / 2 - 1;
            }
            var positions = new float[segments * 2 * 3];
            float scale = size / 24f;
            int at = 0;
            for (var path : paths) {
                for (int i = 0; i + 3 < path.length; i += 2) {
                    at = put(positions, at, path[i], path[i + 1], scale);
                    at = put(positions, at, path[i + 2], path[i + 3], scale);
                }
            }
            var mesh = new Mesh();
            mesh.setMode(Mesh.Mode.Lines);
            mesh.setBuffer(VertexBuffer.Type.Position, 3,
                    BufferUtils.createFloatBuffer(positions));
            mesh.updateBound();
            return mesh;
        }

        private static int put(float[] positions, int at, float x, float y, float scale) {
            positions[at] = (x - 12f) * scale;
            positions[at + 1] = (12f - y) * scale;
            positions[at + 2] = 0f;
            return at + 3;
        }
    }

    /** The glyph vocabulary, for anything else in the client that draws one. */
    static Mesh glyph(String named, float size) {
        return Glyphs.of(named, size);
    }
}
