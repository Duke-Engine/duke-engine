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
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Quad;
import com.jme3.util.BufferUtils;
import java.util.ArrayList;
import java.util.List;
import uz.duke.core.GameConstants;

/**
 * The hero's own corner of the screen: what he is, what is left of him, and what
 * he can cast.
 *
 * <p>Drawn rather than written. The rest of the client's HUD is a line of text,
 * which is right for an RTS where the interesting numbers are money and power and
 * a player reads them once a minute. A dungeon hero is read continuously and at a
 * glance — how much health is left, whether the ultimate is up — and a glance
 * cannot parse "Q ready  W 2s".
 *
 * <p>Like {@link Shell} and {@link Hotkeys}, the client keeps the mechanism and
 * the game says what goes in it. Everything here is driven by the snapshot's
 * status channel, which the engine carries and never reads; a game that puts
 * something else there, or nothing, gets the plain text HUD exactly as before.
 * The wording is the game's too — no English is written into this class, because
 * the labels are a dungeon's, not the engine's.
 *
 * <p>The line it reads is {@code name=..|rank=..|hp=n/n|xp=n/n|depth=..|
 * depthWord=..} followed by one {@code skill=} field per slot, each being
 * {@code KEY,ready}, {@code KEY,cool,framesLeft,framesTotal} or
 * {@code KEY,lock,label}. Anything that does not parse leaves the panel hidden,
 * so the format is a private arrangement between one game and this class rather
 * than a contract the engine has to keep.
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

    // ---- the layout, in the pixels the design was drawn at ----

    private static final float VITALS_WIDTH = 210f;
    private static final float BAR_HEIGHT = 16f;
    private static final float XP_HEIGHT = 9f;
    private static final float ROW_GAP = 7f;
    private static final float VITALS_PAD = 4f;
    private static final float SLOT = 60f;
    private static final float ULT_SLOT = 70f;
    private static final float SLOT_GAP = 9f;
    private static final float COLUMN_GAP = 28f;
    private static final float DEPTH_WIDTH = 60f;
    private static final float DROP_DEPTH = 3f;
    /** Clear of the control hint along the bottom of the screen. */
    private static final float FROM_BOTTOM = 46f;

    /** The key the design draws an ultimate for. Any other key gets an ordinary slot. */
    private static final char ULTIMATE_KEY = 'R';

    private final AssetManager assets;
    private final BitmapFont font;
    private final Node root = new Node("hero-panel");

    private BitmapText name;
    private BitmapText rank;
    private BitmapText health;
    private Geometry healthFill;
    private Geometry experienceFill;
    private BitmapText depthNumber;
    private BitmapText depthWord;

    private final List<Slot> slots = new ArrayList<>();
    /** The keys the slots were built for; a different set means rebuilding them. */
    private String builtFor = "";
    private float screenWidth;

    /** The skill waiting for the player to click something, if any. */
    private Character armed;
    /** Wall clock, for the armed slot's breathing — presentation only. */
    private float clock;

    HeroPanel(AssetManager assets, BitmapFont font, Node guiNode, float screenWidth) {
        this.assets = assets;
        this.font = font;
        this.screenWidth = screenWidth;
        guiNode.attachChild(root);
        buildVitals();
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
        if (reading == null) {
            hide();
            return false;
        }
        root.setCullHint(com.jme3.scene.Spatial.CullHint.Inherit);
        name.setText(reading.name);
        rank.setText(reading.rank);
        health.setText(Math.round(reading.health) + " / " + Math.round(reading.maxHealth));
        fillTo(healthFill, fraction(reading.health, reading.maxHealth));
        fillTo(experienceFill, fraction(reading.experience, reading.needed));
        depthNumber.setText(reading.depth);
        depthWord.setText(reading.depthWord);
        showSkills(reading.skills);
        return true;
    }

    void hide() {
        root.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
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

    /**
     * Set a bar to a fraction of its trough, taking an empty one away entirely
     * rather than squashing it to nothing — a geometry scaled to zero is a
     * degenerate bound, and a hero at no health is exactly when it happens.
     */
    private static void fillTo(Geometry bar, float fraction) {
        bar.setCullHint(fraction <= 0f
                ? com.jme3.scene.Spatial.CullHint.Always
                : com.jme3.scene.Spatial.CullHint.Inherit);
        if (fraction > 0f) {
            bar.setLocalScale(fraction, 1f, 1f);
        }
    }

    /** The panel is anchored to the bottom of the window, so a resize moves it. */
    void resize(float width) {
        this.screenWidth = width;
        layOut();
    }

    // ---- the vitals column ----

    private void buildVitals() {
        var column = new Node("vitals");
        root.attachChild(column);

        float top = VITALS_PAD + XP_HEIGHT + ROW_GAP + BAR_HEIGHT + ROW_GAP;
        name = text(13f, LABEL, 0f, top, VITALS_WIDTH, BitmapFont.Align.Left);
        rank = text(15f, BONE, 0f, top, VITALS_WIDTH, BitmapFont.Align.Right);
        column.attachChild(name);
        column.attachChild(rank);

        float healthY = VITALS_PAD + XP_HEIGHT + ROW_GAP;
        column.attachChild(bar(0f, healthY, VITALS_WIDTH, BAR_HEIGHT));
        healthFill = fill(VITALS_WIDTH - 2f, BAR_HEIGHT - 2f, BLOOD);
        healthFill.setLocalTranslation(1f, healthY + 1f, 1f);
        column.attachChild(healthFill);
        health = text(12f, BONE, 0f, healthY + BAR_HEIGHT - 2f, VITALS_WIDTH,
                BitmapFont.Align.Center);
        health.setLocalTranslation(0f, health.getLocalTranslation().y, 2f);
        column.attachChild(health);

        column.attachChild(bar(0f, VITALS_PAD, VITALS_WIDTH, XP_HEIGHT));
        experienceFill = fill(VITALS_WIDTH - 2f, XP_HEIGHT - 2f, ARCANE);
        experienceFill.setLocalTranslation(1f, VITALS_PAD + 1f, 1f);
        column.attachChild(experienceFill);
    }

    /** A sunken trough for a bar to sit in. */
    private Geometry bar(float x, float y, float width, float height) {
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

    private void buildDepth() {
        var column = new Node("depth");
        root.attachChild(column);
        depthWord = text(12f, LABEL, 0f, 6f, DEPTH_WIDTH, BitmapFont.Align.Center);
        depthNumber = text(30f, TORCH, 0f, 21f, DEPTH_WIDTH, BitmapFont.Align.Center);
        column.attachChild(depthWord);
        column.attachChild(depthNumber);
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
        private Geometry stone;
        private Geometry deadStone;
        private Geometry glyph;
        private Geometry sweep;
        private Geometry ring;
        private BitmapText seconds;
        private BitmapText locked;
        private float sweptTo = -1f;
        private Reading.State state = Reading.State.READY;

        private Slot(char key, float size) {
            this.key = key;
            this.size = size;
        }
    }

    private void showSkills(List<Reading.SkillReading> reading) {
        var keys = new StringBuilder();
        for (var skill : reading) {
            keys.append(skill.key);
        }
        if (!keys.toString().equals(builtFor)) {
            buildSlots(reading);
            builtFor = keys.toString();
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
        for (var skill : reading) {
            var slot = new Slot(skill.key, skill.key == ULTIMATE_KEY ? ULT_SLOT : SLOT);
            carve(slot);
            slots.add(slot);
            root.attachChild(slot.node);
        }
        layOut();
    }

    /** Cut one slot out of the stone: drop, edge, face, lit lip, inner shadow. */
    private void carve(Slot slot) {
        float size = slot.size;
        attach(slot.node, flat("drop", size + 4f, size + 4f, DROP), -2f, -2f - DROP_DEPTH, 0f);
        // A torch-coloured lip around the socket, shown only while this is the
        // skill the next click belongs to. Under the edge, so it reads as the
        // stone catching light rather than as a box drawn on top of it.
        slot.ring = flat("armed", size + 8f, size + 8f, TORCH);
        attach(slot.node, slot.ring, -4f, -4f, 1f);
        slot.ring.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
        attach(slot.node, flat("edge", size + 4f, size + 4f, EDGE), -2f, -2f, 2f);

        slot.stone = new Geometry("stone", gradient(size, size, STONE_LIT, STONE));
        slot.stone.setMaterial(vertexColoured());
        attach(slot.node, slot.stone, 0f, 0f, 3f);

        slot.deadStone = new Geometry("stone-dead",
                gradient(size, size, STONE_DEAD_LIT, STONE_DEAD));
        slot.deadStone.setMaterial(vertexColoured());
        attach(slot.node, slot.deadStone, 0f, 0f, 4f);

        // The carved illusion: light catches the top lip, shadow pools at the foot.
        attach(slot.node, flat("lip", size, 2f, new ColorRGBA(1f, 1f, 1f, 0.07f)),
                0f, size - 2f, 5f);
        attach(slot.node, flat("pool", size, 6f, new ColorRGBA(0f, 0f, 0f, 0.45f)),
                0f, 0f, 5f);

        slot.glyph = new Geometry("glyph", Glyphs.of(slot.key, size * 0.53f));
        slot.glyph.setMaterial(lines());
        attach(slot.node, slot.glyph, size / 2f, size / 2f, 6f);

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

    private void dress(Slot slot, Reading.SkillReading skill) {
        slot.state = skill.state;
        boolean locked = skill.state == Reading.State.LOCKED;
        boolean cooling = skill.state == Reading.State.COOLING;
        slot.deadStone.setCullHint(locked
                ? com.jme3.scene.Spatial.CullHint.Inherit : com.jme3.scene.Spatial.CullHint.Always);
        slot.glyph.getMaterial().setColor("Color",
                locked ? DEAD.mult(new ColorRGBA(1f, 1f, 1f, 0.5f))
                        : cooling ? GLYPH_COLD : TORCH);
        slot.locked.setText(locked ? skill.label : "");
        slot.seconds.setText(cooling ? skill.label : "");
        sweepTo(slot, cooling ? skill.left : 0f);
        light(slot);
    }

    /**
     * The armed slot's lip, breathing so it cannot be mistaken for the ordinary
     * ready glow. Slow — twice a second, between two thirds and full — because the
     * point is "this one is waiting", not "look at me".
     */
    private void light(Slot slot) {
        boolean waiting = armed != null && armed == slot.key;
        slot.ring.setCullHint(waiting
                ? com.jme3.scene.Spatial.CullHint.Inherit : com.jme3.scene.Spatial.CullHint.Always);
        if (!waiting) {
            return;
        }
        float breath = 0.83f + 0.17f * FastMath.sin(clock * FastMath.TWO_PI * 0.9f);
        slot.ring.getMaterial().setColor("Color",
                new ColorRGBA(TORCH.r, TORCH.g, TORCH.b, breath));
    }

    /** Rebuild the cooldown shadow, but only when it has actually moved. */
    private void sweepTo(Slot slot, float remaining) {
        if (Math.abs(remaining - slot.sweptTo) < 0.002f) {
            return;
        }
        slot.sweptTo = remaining;
        slot.sweep.setMesh(sweep(slot.size, remaining));
    }

    // ---- placing the whole thing ----

    private void layOut() {
        float skillsWidth = 0f;
        for (int i = 0; i < slots.size(); i++) {
            skillsWidth += slots.get(i).size + (i == 0 ? 0f : SLOT_GAP);
        }
        float total = VITALS_WIDTH + COLUMN_GAP + skillsWidth + COLUMN_GAP + DEPTH_WIDTH;
        root.setLocalTranslation(Math.max(10f, (screenWidth - total) / 2f), FROM_BOTTOM, 0f);

        float x = VITALS_WIDTH + COLUMN_GAP;
        for (var slot : slots) {
            slot.node.setLocalTranslation(x, 0f, 0f);
            x += slot.size + SLOT_GAP;
        }
        root.getChild("depth").setLocalTranslation(x - SLOT_GAP + COLUMN_GAP, 0f, 0f);
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
    private static Mesh gradient(float width, float height, ColorRGBA... stops) {
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
        var mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(positions));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indices));
        mesh.updateBound();
        return mesh;
    }

    // ---- materials & text ----

    private Material unshaded(ColorRGBA colour) {
        var material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", colour.clone());
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

    private Material lines() {
        var material = unshaded(TORCH);
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
        line.setColor(colour);
        line.setBox(new Rectangle(x, y + size, width, size * 1.4f));
        line.setAlignment(align);
        return line;
    }

    private static void attach(Node parent, com.jme3.scene.Spatial child,
            float x, float y, float z) {
        child.setLocalTranslation(x, y, z);
        parent.attachChild(child);
    }

    private static float fraction(float part, float whole) {
        return whole <= 0f ? 0f : Math.max(0f, Math.min(1f, part / whole));
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
            List<SkillReading> skills) {

        enum State { READY, COOLING, LOCKED }

        /** One slot: its key, its state, the words on it, and how much shadow is left. */
        record SkillReading(char key, State state, String label, float left) {
        }

        static Reading parse(String status) {
            if (status == null || !status.startsWith("name=")) {
                return null;
            }
            String name = "";
            String rank = "";
            String depth = "";
            String depthWord = "";
            var health = new float[] {0f, 0f};
            var experience = new float[] {0f, 0f};
            var skills = new ArrayList<SkillReading>();
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
                    case "hp" -> health = pair(value);
                    case "xp" -> experience = pair(value);
                    case "skill" -> skills.add(skill(value));
                    default -> {
                        return null; // a field this client does not know: not ours
                    }
                }
                if (health == null || experience == null || skills.contains(null)) {
                    return null;
                }
            }
            return new Reading(name, rank, health[0], health[1], experience[0], experience[1],
                    depth, depthWord, List.copyOf(skills));
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

        /** {@code Q,ready} — {@code W,cool,72,165} — {@code R,lock,5-daraja}. */
        private static SkillReading skill(String value) {
            var parts = value.split(",");
            if (parts.length < 2 || parts[0].length() != 1) {
                return null;
            }
            char key = parts[0].charAt(0);
            try {
                return switch (parts[1]) {
                    case "ready" -> new SkillReading(key, State.READY, "", 0f);
                    case "lock" -> parts.length < 3 ? null
                            : new SkillReading(key, State.LOCKED, parts[2], 1f);
                    case "cool" -> parts.length < 4 ? null : cooling(key,
                            Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                    default -> null;
                };
            } catch (NumberFormatException e) {
                return null;
            }
        }

        /**
         * Frames on the wire, seconds on the screen. The simulation counts frames
         * and must, but a player reading a number off a slot thinks in seconds —
         * and the conversion is the engine's own constant, not a second copy of it.
         */
        private static SkillReading cooling(char key, int left, int total) {
            float seconds = left / (float) GameConstants.LOGICFRAMES_PER_SECOND;
            var label = seconds >= 10f
                    ? String.valueOf(Math.round(seconds))
                    : String.format(java.util.Locale.ROOT, "%.1f", seconds);
            return new SkillReading(key, State.COOLING, label,
                    total <= 0 ? 0f : left / (float) total);
        }
    }

    /**
     * The four marks cut into the slots, as line drawings in a 24-by-24 square.
     *
     * <p>Drawn here rather than loaded, because an icon that is four straight lines
     * is smaller as four straight lines than as a file, and because a glyph that
     * is drawn takes the panel's colours without a second copy of the palette
     * living in an image.
     *
     * <p>They are pictures of what the skill is rather than of what it hits: an
     * arrow leaving, a burst around a centre, a stride, a star.
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

        /** Any key the design did not draw: a plain lozenge, so nothing is blank. */
        private static final float[][] PLAIN = {
            {12, 4, 19, 12, 12, 20, 5, 12, 12, 4},
        };

        private static float[][] ring() {
            var paths = new float[9][];
            var circle = new float[(16 + 1) * 2];
            for (int i = 0; i <= 16; i++) {
                float angle = FastMath.TWO_PI * i / 16f;
                circle[i * 2] = 12f + 4f * FastMath.cos(angle);
                circle[i * 2 + 1] = 12f + 4f * FastMath.sin(angle);
            }
            paths[0] = circle;
            float[][] rays = {
                {12, 3, 12, 6}, {12, 18, 12, 21}, {3, 12, 6, 12}, {18, 12, 21, 12},
                {5.6f, 5.6f, 7.7f, 7.7f}, {16.3f, 16.3f, 18.4f, 18.4f},
                {18.4f, 5.6f, 16.3f, 7.7f}, {7.7f, 16.3f, 5.6f, 18.4f},
            };
            System.arraycopy(rays, 0, paths, 1, rays.length);
            return paths;
        }

        /**
         * One glyph as a line mesh, centred on the origin and scaled to {@code size}.
         * The drawings are in screen order — y downwards, as a designer writes
         * them — and the flip to the client's upward y happens once, here.
         */
        private static Mesh of(char key, float size) {
            var paths = switch (key) {
                case 'Q' -> SHOT;
                case 'W' -> BURST;
                case 'E' -> DASH;
                case 'R' -> STAR;
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
}
