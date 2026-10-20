package uz.dukeengine.client3d.rpg;

import static uz.dukeengine.client3d.rpg.PanelBrush.atDepth;
import static uz.dukeengine.client3d.rpg.PanelBrush.attach;
import static uz.dukeengine.client3d.rpg.PanelBrush.box;
import static uz.dukeengine.client3d.rpg.PanelMeshes.gradient;
import static uz.dukeengine.client3d.rpg.PanelMetrics.BAND;
import static uz.dukeengine.client3d.rpg.PanelMetrics.PAD;
import static uz.dukeengine.client3d.rpg.PanelMetrics.PORTRAIT;
import static uz.dukeengine.client3d.rpg.PanelMetrics.PORTRAIT_COLUMN;
import static uz.dukeengine.client3d.rpg.PanelMetrics.PORTRAIT_HEIGHT;
import static uz.dukeengine.client3d.rpg.PanelMetrics.VITALS_WIDTH;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.client3d.Lettering;
import uz.dukeengine.client3d.PanelSkin;
import uz.dukeengine.client3d.RangeLook;

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
 * left, the portrait and the vitals in the middle, the skills at the right, the
 * depth at the far end; the sections are separated by
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

    // ---- the layout, in the pixels the design was drawn at ----

    private static final float SLAB_HEIGHT = BAND + PAD * 2f;

    private static final float MINIMAP = BAND;
    private static final float DIVIDER = 3f;
    private static final float DIVIDER_MARGIN = 10f;
    private static final float PORTRAIT_GAP = 10f;

    /** The line above the bar, for something that has just happened. */
    private static final float NOTE_SIZE = 15f;

    /** How far the card floats over the slot it belongs to. */
    private static final float TIP_LIFT = 12f;

    /** What the game says the bar is made of and painted in; every colour is read off it. */
    private final PanelLook look;
    private final PanelColours colours;
    private final PanelBrush brush;
    private final PanelFocus focus = new PanelFocus();

    private final Node root = new Node("hero-panel");
    /** Everything but the slab: scaled and centred as one group. */
    private final Node slab = new Node("slab");
    private final Node contents = new Node("contents");
    /** Each block's own node, which all of that block hangs from, and which is culled for one left off the bar. */
    private final Map<PanelBlock, Node> holders = new EnumMap<>(PanelBlock.class);

    private final Portrait portrait;
    private final Gauges gauges;
    private final VitalsColumn vitals;
    private final OrderColumn orders;
    private final ItemGrid items;
    private final SkillRow skills;
    private final DepthMarker depth;

    /** How the block under the experience bar is drawn — see {@link StatLook}. */
    private final StatLook statLook;

    /**
     * The card over whichever slot or attribute the cursor is on.
     *
     * <p>Outside the bar's own node and drawn after it, because it hangs ABOVE
     * the bar: parented into the row it describes, it would be clipped by
     * everything the row is drawn under.
     */
    private final SkillTip tip;
    /** The cards the last line described, by key. */
    private Map<Character, SkillTip.Reading> tips = Map.of();
    /** The card over each attribute that has one, by its place in the game's list. */
    private Map<Integer, SkillTip.Reading> attributeTips = Map.of();
    /** The attribute the cursor is resting on, if any. */
    private Integer hoveredAttribute;

    private float screenWidth;
    private float screenHeight;
    private float scale = 1f;
    private boolean showing;

    /**
     * What the last status line said, so a second reader — the sounds — does not
     * have to parse it again.
     */
    private PanelReading reading;

    private Node minimapSocket;
    /** A line above the bar for something that just happened and will stop mattering. */
    private BitmapText note;

    /** Every block standing off the bar, for a click to be told from a click on the world. */
    private final List<Loose> loose = new ArrayList<>();
    private final List<Node> dividers = new ArrayList<>();

    HeroPanel(AssetManager assets, BitmapFont font, Node guiNode, float screenWidth, float screenHeight,
            PanelSkin skin, RangeLook aiming) {
        this(assets, font, guiNode, screenWidth, screenHeight, skin, aiming, IconLook.DEFAULT);
    }

    HeroPanel(AssetManager assets, BitmapFont font, Node guiNode, float screenWidth, float screenHeight,
            PanelSkin skin, RangeLook aiming, IconLook icons) {
        this(assets, font, null, guiNode, screenWidth, screenHeight, skin, aiming, icons, StatLook.DEFAULT,
                PanelLook.DEFAULTS);
    }

    /**
     * The same, told what face to set his NAME in.
     *
     * <p>One line of a panel, and it is the line that says which game this is: a
     * dungeon wants carved capitals over its hero and a skirmish game does not.
     * Everything else on the bar stays plain, because everything else is a number
     * or a word being read rather than a title being recognised.
     *
     * <p>Null falls back to the plain face, which is what the panel always did
     * and what the three other games this client serves will go on getting.
     */
    HeroPanel(AssetManager assets, BitmapFont font, BitmapFont display, Node guiNode,
            float screenWidth, float screenHeight, PanelSkin skin, RangeLook aiming, IconLook icons,
            StatLook stats, PanelLook look) {
        this.statLook = stats == null ? StatLook.DEFAULT : stats;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.look = look == null ? PanelLook.DEFAULTS : look;
        this.colours = new PanelColours(this.look, aiming);
        var lettering = Lettering.of(assets, this.look.lettering());
        var titleLettering = Lettering.of(assets, this.look.titleLettering());
        float letteringScale = legibleScale();
        this.brush = new PanelBrush(assets, font, display == null ? font : display,
                skin == null ? PanelSkin.NONE : skin, lettering, titleLettering, letteringScale, colours);
        guiNode.attachChild(root);
        // Beside the bar rather than inside it, and attached after, so it is drawn
        // over everything the bar is drawn under -- a card parented into the row
        // it describes would be clipped by the row.
        tip = new SkillTip(assets, font, guiNode, this.look, lettering, titleLettering, letteringScale);
        root.attachChild(slab);
        root.attachChild(contents);
        for (var block : PanelBlock.values()) {
            var holder = new Node("block-" + block.name().toLowerCase(Locale.ROOT));
            holders.put(block, holder);
            contents.attachChild(holder);
        }
        buildSlab();
        buildMinimapSocket();
        var hero = holders.get(PanelBlock.HERO);
        portrait = new Portrait(brush, colours, hero);
        gauges = new Gauges(brush, colours, hero);
        vitals = new VitalsColumn(brush, colours, statLook, hero);
        items = new ItemGrid(brush, colours, this.look, holders.get(PanelBlock.BAG));
        orders = new OrderColumn(brush, colours, focus, holders.get(PanelBlock.MINIMAP), this::layOut);
        skills = new SkillRow(brush, colours, focus, this.look, icons == null ? IconLook.DEFAULT : icons,
                holders.get(PanelBlock.SKILLS), this::layOut);
        depth = new DepthMarker(brush, colours, holders.get(PanelBlock.DEPTH));
        buildNote();
        // Placed now rather than when the first slot is carved: a hero with no
        // skills at all never carves one, and would sit in the screen's corner.
        layOut();
        hide();
    }

    /** The letter a slot's skill is pressed on: the one the game gave it, or its own key. */
    char letterOf(char key) {
        return focus.letterOf(key);
    }

    /** Whether this key is a skill the panel is showing as unaffordable — see {@link SkillRow#refusedForMana}. */
    boolean refusedForMana(char key) {
        return skills.refusedForMana(key);
    }

    /** Say so: flash the bar and leave a noise to be taken — see {@link Gauges#deny}. */
    void denyForMana(float seconds) {
        gauges.deny(seconds);
    }

    /** Take the refusal, if there is one waiting — see {@link Gauges#takeRefusal}. */
    boolean takeRefusal() {
        return gauges.takeRefusal();
    }

    /**
     * Show what the status line describes, or hide the panel if it describes
     * something else. Returns whether the panel took the line — the caller then
     * knows not to print it as text as well.
     */
    boolean show(String status, float seconds) {
        focus.clock = seconds;
        focus.readTheLetters(status);
        var reading = PanelReading.parse(status);
        this.reading = reading;
        if (reading == null) {
            hide();
            return false;
        }
        showing = true;
        root.setCullHint(Spatial.CullHint.Inherit);
        vitals.show(reading);
        gauges.show(reading, seconds);
        depth.show(reading.depth(), reading.depthWord());
        note.setText(reading.note());
        portrait.show(reading.face(), !reading.name().isBlank());
        orders.show(reading.orders(), reading.ordersAreHis());
        items.show(reading.items(), reading.itemsWord());
        tips = reading.tips();
        attributeTips = reading.attributeTips();
        skills.show(reading);
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

    /**
     * The status line as the panel read it.
     *
     * <p>Shared rather than parsed twice: the game's line carries a level, a
     * floor, and what he just picked up, and those are moments worth hearing as
     * well as worth drawing — see {@link GameSounds}. Two readers of one line
     * would drift the first time the line grew a field.
     */
    PanelReading reading() {
        return reading;
    }

    /** Whether the bar is on screen at all — nothing may be clicked while it is not. */
    boolean isShowing() {
        return showing;
    }

    /** Which skill is waiting to be pointed at something, so a rebuilt bar can be told. */
    Character armedKey() {
        return focus.armed;
    }

    /**
     * Say which skill is waiting for the player to click something, or {@code null}
     * for none. Its slot lights up and breathes, which is the only thing on screen
     * saying why the next click will not do what a click usually does.
     */
    void arm(Character key) {
        focus.armed = key;
    }

    /** Whether a skill looks castable — ready, not waiting for a level, and paid for. */
    boolean readyToCast(char key) {
        return skills.readyToCast(key);
    }

    // ---- what the mouse is over ----

    /** Whether a screen point is on the bar at all, and so not on the world. */
    boolean contains(float screenX, float screenY) {
        if (!showing) {
            return false;
        }
        if (screenY <= heightPixels()) {
            return true;
        }
        // And the blocks the game stood elsewhere, each on its own plate: a click on one is a click on the HUD.
        for (var block : loose) {
            if (screenX >= block.x() * scale && screenX <= (block.x() + block.width()) * scale
                    && screenY >= block.y() * scale && screenY <= (block.y() + block.height()) * scale) {
                return true;
            }
        }
        return false;
    }

    /**
     * How tall the bar stands in window pixels, or 0 when it is hidden.
     *
     * <p>What the world's bottom edge actually is, as far as anything reaching for
     * it is concerned.
     */
    float heightPixels() {
        // A bar with no blocks left on it is not drawn, and the world reaches the bottom of the window.
        return showing && !look.blocks().isEmpty() ? SLAB_HEIGHT * scale : 0f;
    }

    /**
     * Where the bar stands across the foot of the window, in the window's pixels from its top left — as tall as it
     * is drawn, squeezed into a window too narrow for its blocks or not — or null while it is hidden. The blocks the
     * game stood elsewhere are not in it.
     */
    Canvas.Box standing() {
        float tall = heightPixels();
        return tall <= 0f ? null : new Canvas.Box(0f, screenHeight - tall, screenWidth, tall);
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
        if (has(PanelBlock.SKILLS)) {
            var key = skills.slotAt(screenX / scale, screenY / scale);
            if (key != null) {
                return key;
            }
        }
        return orderAt(screenX, screenY);
    }

    /** The upgrade badge under a screen point, or {@code null} for none — see {@link SkillRow#badgeAt}. */
    Character badgeAt(float screenX, float screenY) {
        if (!showing || !has(PanelBlock.SKILLS)) {
            return null;
        }
        return skills.badgeAt(screenX / scale, screenY / scale);
    }

    /** Whether the panel is offering to put a point into that slot right now. */
    boolean canRaise(char key) {
        return skills.canRaise(key);
    }

    /** Whether a click on the slot or its key does anything: a passive's does not, though its card still shows. */
    boolean pressable(char key) {
        return skills.pressable(key);
    }

    /** The letter drawn in the slot's corner: its key, or nothing for a passive. */
    String keyLetter(char key) {
        return skills.keyLetter(key);
    }

    /** The order button under a screen point, if any — answered through the same door the skill sockets are. */
    Character orderAt(float screenX, float screenY) {
        if (!showing || !has(PanelBlock.MINIMAP)) {
            return null;
        }
        return orders.orderAt(screenX / scale, screenY / scale);
    }

    /** Whether that key is one of the order buttons rather than a skill. */
    boolean isAnOrder(char key) {
        return orders.isAnOrder(key);
    }

    /**
     * Whether that order button is wearing the mark of the one he is in — the cold
     * stone and the corner brackets an armed skill wears. For the tests.
     */
    boolean orderIsMarked(char key) {
        return orders.orderIsMarked(key);
    }

    /**
     * Whether the buttons may be pressed — that is, whether what is selected is
     * the player's to command.
     *
     * <p>Read back out by the client so that a key press is refused for the same
     * reason the button is drawn dim. One answer, from the game, rather than the
     * client working it out a second time and the two disagreeing on the frame the
     * selection changes.
     */
    boolean ordersAreHis() {
        return orders.areHis();
    }

    /** Light the slot the mouse is resting on, or none. */
    void hover(Character key) {
        focus.hovered = key;
        placeTip();
    }

    /** The attribute under a screen point, by its place in the game's list, or null. */
    Integer attributeAt(float screenX, float screenY) {
        if (!showing || !has(PanelBlock.HERO)) {
            return null;
        }
        return vitals.stats.attributeAt(screenX / scale, screenY / scale);
    }

    /** Rest the cursor on an attribute, or take it off one. */
    void hoverAttribute(Integer index) {
        this.hoveredAttribute = index;
        placeTip();
    }

    /**
     * Hang a live creature in the frame, or take the last one down.
     *
     * <p>Called <em>before</em> {@link #show}, which is what decides between this
     * and the drawing — see {@link Portrait#live}.
     */
    void live(Texture picture) {
        portrait.live(picture);
    }

    /** The panel is anchored to the bottom of the window, so a resize moves it. */
    void resize(float width, float height) {
        this.screenWidth = width;
        this.screenHeight = height;
        layOut();
    }

    // ---- the slab everything is cut out of ----

    private void buildSlab() {
        // Rebuilt on every resize, because it is the one piece whose width is the
        // window's rather than the design's.
        slab.detachAllChildren();
        var stone = brush.shaded("slab",
                gradient(Math.max(1f, screenWidth / Math.max(scale, 0.0001f)), SLAB_HEIGHT,
                        colours.slabTop, colours.slabHigh, colours.slabMid, colours.slabLow));
        attach(slab, stone, 0f, 0f, 0f);
        // The lit rim along the top: a slab of stone catching the room's light,
        // and the line that says the bar is a thing rather than a tint.
        attach(slab, brush.flat("rim", Math.max(1f, screenWidth / Math.max(scale, 0.0001f)), 3f,
                colours.slabRim), 0f, SLAB_HEIGHT - 3f, 1f);
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
        attach(node, brush.flat("groove", DIVIDER, BAND, colours.drop), 0f, 0f, 1f);
        if (!paintDivider(node)) {
            attach(node, brush.flat("catch", 1f, BAND, new ColorRGBA(1f, 1f, 1f, 0.05f)),
                    DIVIDER, 0f, 2f);
        }
        return node;
    }

    // ---- the minimap's socket ----

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
        holders.get(PanelBlock.MINIMAP).attachChild(minimapSocket);
        attach(minimapSocket, brush.flat("recess", MINIMAP + 4f, MINIMAP + 4f, colours.drop), -2f, -2f, 0f);
        attach(minimapSocket, brush.flat("lip", MINIMAP + 2f, MINIMAP + 2f, colours.socketRim), -1f, -1f, 1f);
        attach(minimapSocket, brush.flat("hole", MINIMAP, MINIMAP, colours.hole), 0f, 0f, 2f);
        // Over the hole rather than under it: the map is drawn into the socket by
        // the client's own minimap, at a depth this panel does not own, and a rim
        // painted underneath would be a rim nobody ever sees.
        brush.framed(minimapSocket, PanelSkin.MINIMAP, -3f, -3f, MINIMAP + 6f, MINIMAP + 6f, 3f);
    }

    /**
     * Where the minimap goes, in window pixels: {@code x, y, size} and the depth it is laid at, over the socket's hole
     * and under its frame at whatever scale the bar is drawn — or null for a bar the game built without its socket,
     * which leaves the minimap to stand on its own.
     *
     * <p>Handed out rather than drawn here so the two stay in step through a
     * resize without the minimap having to know the bar's arithmetic.
     */
    float[] minimapRect() {
        if (!has(PanelBlock.MINIMAP)) {
            return null;
        }
        // Through its block's holder, which is where a socket the game stood in a corner of its own has been moved to.
        var at = minimapSocket.getLocalTranslation();
        var holder = holders.get(PanelBlock.MINIMAP).getLocalTranslation();
        return new float[] {
            (contents.getLocalTranslation().x + holder.x + at.x) * scale,
            (contents.getLocalTranslation().y + holder.y + at.y) * scale,
            MINIMAP * scale,
            (contents.getLocalTranslation().z + holder.z + at.z + 2.5f) * scale, // its hole is at 2, its frame at 3
        };
    }

    /**
     * Put the card over the slot or the figure the cursor is on, or take it away.
     *
     * <p>Only over a SKILL or an ATTRIBUTE: the order buttons beside them are four
     * words a player learns once, and a card explaining "attack" every time his hand
     * passed the column would be the panel talking for the sake of it. An attribute
     * is the other way round — what one point of it is worth is exactly the thing
     * nobody can see by looking at it.
     */
    private void placeTip() {
        if (focus.hovered != null && tips.containsKey(focus.hovered)) {
            var box = skills.boxOf(focus.hovered);
            if (box != null) {
                // Above the socket, not over it: a card that covered the thing it
                // describes would hide the pips the moment they became the reason
                // to read it.
                tip.show(tips.get(focus.hovered), box[0], box[1] + box[2] + TIP_LIFT, scale, screenWidth);
                return;
            }
        }
        if (hoveredAttribute != null && attributeTips.containsKey(hoveredAttribute)) {
            // Over the whole bar, not over the row: the block sits low in a column
            // with a name, a title and a lettered bar above it, and a card opened just
            // over a row lay across all three and under their words. From the middle
            // column, so it stands over his primary and the list together.
            // Over the stone the block is laid on, wherever that is: the bar's own, or the plate of a block the
            // game stood somewhere else.
            tip.show(attributeTips.get(hoveredAttribute), vitals.stats.originX() + statLook.figureColumn(),
                    vitals.stats.originY() + BAND + PAD + TIP_LIFT, scale, screenWidth);
            return;
        }
        tip.hide();
    }

    /** Whether a card is on screen. For the tests. */
    boolean tipShowing() {
        return tip.showing();
    }

    /** How tall the card came out. For the tests. */
    float tipHeight() {
        return tip.heightDrawn();
    }

    /**
     * The line just above the bar, for a thing that has happened.
     *
     * <p>Not the banner: the banner interrupts and sits in the middle of the
     * screen, which is right for "you died" and wrong for "you picked up a sword".
     * This sits on the edge of what he is already looking at, and goes away on its
     * own — the game decides when by simply not sending it any more.
     */
    private void buildNote() {
        note = brush.text(NOTE_SIZE, colours.torch, 0f, SLAB_HEIGHT + 6f, look.designWidth(),
                BitmapFont.Align.Center);
        atDepth(note, 2f);
        root.attachChild(note);
    }

    /**
     * Where one block goes: {@code x} across the stone it is laid on, and where that lands on the screen — in the
     * design's pixels, from the foot of its own band — for a click to be measured against.
     */
    private interface Placing {
        void at(float x, float originX, float originY);
    }

    /**
     * One block: how wide it is, how tall the part of the band it actually fills is, how far up the band that part
     * starts, and how to put it down. The height and the foot are what a block standing on its own is plated to;
     * in the bar it is the band that is plated, and they are not asked for.
     */
    private record Block(float width, float height, float foot, Placing place) {
    }

    /** One block of the bar standing on its own: where its plate is, in the design's pixels from the window's foot. */
    private record Loose(float x, float y, float width, float height) {
    }

    /**
     * Put the bar together out of whatever blocks this card has.
     *
     * <p>A list rather than a chain of ifs, because the blocks are independently
     * optional — a creature's card has no bag and no skills, and with nothing
     * selected at all there is no portrait either. Four optional blocks is
     * sixteen arrangements, and the only way to write sixteen arrangements once
     * is to describe each block and let the loop space them.
     *
     * <p>A groove goes between every neighbouring pair and nowhere else, which is
     * what makes the bar close up rather than leave a hole where something used
     * to be.
     */
    private void layOut() {
        // A block the game draws nowhere is not drawn, whatever its own parts say about showing; and only a block
        // standing on its own is laid on stone of its own, so any plate from a layout before this one goes.
        for (var block : PanelBlock.values()) {
            holders.get(block).setCullHint(has(block) ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
            plate(block, null);
        }
        var blocks = new ArrayList<Block>();
        for (var block : look.blocks()) {
            blocks.add(blockOf(block));
        }

        float gap = DIVIDER + DIVIDER_MARGIN * 2f;
        float total = gap * Math.max(0, blocks.size() - 1);
        for (var block : blocks) {
            total += block.width();
        }

        // Measured first, and the scale settled from it: see scaleFor.
        this.scale = scaleFor(total);
        root.setLocalScale(scale);
        buildSlab();
        // No blocks left on the bar is no bar: what the game wanted is the blocks themselves, standing where it put
        // them, over a world with nothing across the bottom of it.
        slab.setCullHint(blocks.isEmpty() ? Spatial.CullHint.Always : Spatial.CullHint.Inherit);
        // Centred on the window rather than on the design, so it sits over the
        // middle of the bar however wide the window is.
        box(note, 0f, SLAB_HEIGHT + 6f + NOTE_SIZE, screenWidth / scale, NOTE_SIZE * 1.4f);

        // The bar spans the window; its contents are centred on it, so a wide
        // screen puts empty stone at both ends rather than all of it at one.
        float left = Math.max(PAD, (screenWidth / scale - total) / 2f);
        contents.setLocalTranslation(left, PAD, 1f);

        float x = 0f;
        for (int i = 0; i < blocks.size(); i++) {
            if (i > 0) {
                x += DIVIDER_MARGIN;
                placeDivider(i - 1, x);
                x += DIVIDER + DIVIDER_MARGIN;
            }
            holders.get(look.blocks().get(i)).setLocalTranslation(0f, 0f, 0f);
            blocks.get(i).place().at(x, left + x, PAD);
            x += blocks.get(i).width();
        }
        for (int spare = Math.max(0, blocks.size() - 1); spare < dividers.size(); spare++) {
            hideDivider(spare); // grooves the last card needed and this one does not
        }
        layOutLoose(left);
    }

    /**
     * The blocks that stand on their own, each hung from its corner of the window on a plate of the bar's own stone.
     *
     * <p>Hung from an edge rather than placed at a point, so a block keeps the corner it was put in when the window
     * changes size — which is the whole of what an anchor is for. Everything inside a block is laid out exactly as
     * it is laid out on the bar: the block's holder is moved, and nothing under it knows it has been.
     */
    private void layOutLoose(float left) {
        loose.clear();
        float across = screenWidth / scale;
        float up = screenHeight / scale;
        for (var place : look.places()) {
            var block = blockOf(place.block());
            float width = block.width();
            float height = block.height();
            float x = switch (place.anchor()) {
                case TOP_LEFT, LEFT, BOTTOM_LEFT -> PAD + place.x();
                case TOP, MIDDLE, BOTTOM -> (across - width) / 2f + place.x();
                case TOP_RIGHT, RIGHT, BOTTOM_RIGHT -> across - width - PAD - place.x();
            };
            float y = switch (place.anchor()) {
                case BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT -> PAD + place.y();
                case LEFT, MIDDLE, RIGHT -> (up - height) / 2f + place.y();
                case TOP_LEFT, TOP, TOP_RIGHT -> up - height - PAD - place.y();
            };
            // The holder carries the block's whole band; the part of it the block fills is what lands on the anchor.
            holders.get(place.block()).setLocalTranslation(x - left, y - block.foot() - PAD, 0f);
            plate(place.block(), block);
            block.place().at(0f, x, y - block.foot());
            loose.add(new Loose(x - PAD, y - PAD, width + PAD * 2f, height + PAD * 2f));
        }
    }

    /** Each block, as the bar lays it: how wide, how much of the band it fills, and where that part starts. */
    private Block blockOf(PanelBlock which) {
        return switch (which) {
            case MINIMAP -> new Block(MINIMAP + orders.width(), BAND, 0f, (x, ox, oy) -> {
                minimapSocket.setLocalTranslation(x, 0f, 0f);
                orders.place(x + MINIMAP + OrderColumn.ORDER_COLUMN_GAP,
                        ox + MINIMAP + OrderColumn.ORDER_COLUMN_GAP, oy);
            });
            case HERO -> new Block(PORTRAIT_COLUMN + PORTRAIT_GAP + VITALS_WIDTH, BAND, 0f, (x, ox, oy) -> {
                // The frame hangs from the top of the band and is CENTRED in its
                // column; the two gauges fill the column under it, and the identity
                // and the experience bar fill the height beside it.
                portrait.node.setLocalTranslation(x + (PORTRAIT_COLUMN - PORTRAIT) / 2f,
                        BAND - PORTRAIT_HEIGHT, 0f);
                gauges.node.setLocalTranslation(x, 0f, 0f);
                vitals.node.setLocalTranslation(x + PORTRAIT_COLUMN + PORTRAIT_GAP, 0f, 0f);
                vitals.place(ox + PORTRAIT_COLUMN + PORTRAIT_GAP, oy);
            });
            case BAG -> new Block(items.width(), items.height(), items.foot(), (x, ox, oy) -> items.place(x));
            case SKILLS -> new Block(skills.width(), skills.columnHeight(), skills.foot(), skills::place);
            case DEPTH -> new Block(DepthMarker.WIDTH, DepthMarker.HEIGHT, BAND / 2f - 24f,
                    (x, ox, oy) -> depth.node.setLocalTranslation(x, 0f, 0f));
        };
    }

    /**
     * The stone a block standing on its own is laid on, or none for one on the bar, which has the slab's.
     *
     * <p>The slab's own recipe — a shaded face with a lit rim along its top — so a block pulled off the bar still
     * reads as a piece of the same furniture rather than as something floating over the floor.
     */
    private void plate(PanelBlock which, Block block) {
        var holder = holders.get(which);
        var standing = holder.getChild("plate");
        if (standing != null) {
            holder.detachChild(standing);
        }
        if (block == null) {
            return;
        }
        var plate = new Node("plate");
        float width = block.width() + PAD * 2f;
        float height = block.height() + PAD * 2f;
        var stone = brush.shaded("plate-face",
                gradient(width, height, colours.slabTop, colours.slabHigh, colours.slabMid, colours.slabLow));
        attach(plate, stone, 0f, 0f, 0f);
        attach(plate, brush.flat("plate-rim", width, 3f, colours.slabRim), 0f, height - 3f, 0.1f);
        attach(holder, plate, -PAD, block.foot() - PAD, -0.5f);
    }

    /** Whether the game draws this block at all — on the bar, or standing on its own. */
    private boolean has(PanelBlock block) {
        return look.blocks().contains(block) || look.places().stream().anyMatch(place -> place.block() == block);
    }

    /**
     * How much the design is shrunk to reach this window, in the design's own
     * pixels — and never so little that the bar runs off the edge.
     *
     * <p>Two answers, and the smaller wins. The first is the design's: a share of
     * the width it was drawn at, held between a size below which it is unreadable
     * and one above which it is silly. That one is about <em>legibility</em>, and
     * it knows nothing about how wide the card in hand happens to be.
     *
     * <p>The second is arithmetic: what actually fits. It has to be asked because
     * the clamp cannot be told the answer in advance — a card carries whatever
     * blocks the game sent, so the bar is a different width for a hero and for a
     * skeleton, and any fixed floor is a number somebody has to re-check every
     * time anything on the bar changes size. It was re-checked exactly once, and
     * the portrait growing to the size the design draws it at was enough to push
     * the far end of a narrow window off the screen by two pixels.
     *
     * <p>So the floor is kept for what it is good at and the overrun is made
     * impossible rather than unlikely.
     */
    private float scaleFor(float total) {
        float fits = (screenWidth - PAD * 2f) / Math.max(1f, total);
        return Math.min(legibleScale(), fits);
    }

    /** As large as the window says, between the game's least and most: the bar's scale where nothing squeezes it. */
    private float legibleScale() {
        return Math.clamp(screenWidth / look.designWidth(), look.minScale(), look.maxScale());
    }

    /** Take a groove off the bar, for a card whose blocks do not need dividing. */
    private void hideDivider(int index) {
        if (index < dividers.size()) {
            dividers.get(index).setCullHint(Spatial.CullHint.Always);
        }
    }

    private void placeDivider(int index, float x) {
        while (dividers.size() <= index) {
            var line = divider();
            dividers.add(line);
            contents.attachChild(line);
        }
        dividers.get(index).setCullHint(Spatial.CullHint.Inherit);
        dividers.get(index).setLocalTranslation(x, 0f, 0f);
    }

    /**
     * Two ornaments standing on end, meeting in the middle of the band.
     *
     * <p>The picture is a rule that fades at one end and finishes in a device at
     * the other, drawn lying down. Stood up and mirrored about the middle it
     * becomes a carved line with a device at each end and nothing to see where the
     * two faded ends meet — which is why a gap there costs nothing and stretching
     * the ornament to close it would cost the ornament.
     *
     * <p>Its size is the picture's own, times the piece's scale: the ornament is a
     * fixed shape, so how long it stands is how heavily it is laid on, never a
     * stretch. Measured off the file, because the set's dividers are not one shape
     * — ninety-six pixels long and anywhere from ten to twenty-two across — and a
     * picture drawn at another's thickness is squashed or smeared across it.
     */
    private boolean paintDivider(Node node) {
        var painted = brush.skin.piece(PanelSkin.DIVIDER);
        if (painted == null) {
            return false;
        }
        var picture = brush.texture(painted.texture());
        if (picture == null) {
            return false;
        }
        float length = picture.getImage().getWidth() * painted.scale();
        float thickness = picture.getImage().getHeight() * painted.scale();
        // Wider than the groove it stands in, and centred on it: the margin
        // either side of a divider is there so the carving has somewhere to go.
        float across = (DIVIDER - thickness) * 0.5f;
        if (!brush.framed(node, PanelSkin.DIVIDER, across, 0f, thickness, length, 3f)) {
            return false;
        }
        // The same piece turned over about the top of the band, so the two devices
        // point away from each other and the two faded ends are the ones that meet.
        var mirrored = new Node("divider-mirrored");
        brush.framed(mirrored, PanelSkin.DIVIDER, across, 0f, thickness, length, 3f);
        mirrored.setLocalScale(1f, -1f, 1f);
        mirrored.setLocalTranslation(0f, BAND, 0f);
        node.attachChild(mirrored);
        return true;
    }
}
