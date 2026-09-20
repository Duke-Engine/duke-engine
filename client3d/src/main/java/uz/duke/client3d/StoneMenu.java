package uz.duke.client3d;

import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Every screen that is not the game: a title over a slab, a column of lines, one
 * of them lit.
 *
 * <p>One component for the front menu, the pause menu and the settings, because
 * they are the same thing with different rows in it. A menu is rows that do
 * something; settings are rows that hold a value. Splitting them would be two
 * copies of the same navigation, the same lighting, the same layout arithmetic —
 * and two looks that drift apart the first time one of them is adjusted.
 *
 * <p>Drawn from {@link StoneCraft}, which is what the hero's bar is drawn from, so
 * a menu is made of the same stone as the panel it covers.
 *
 * <p>Keyboard and mouse both, always: up and down move, left and right change,
 * Enter takes, Escape backs out — and the same rows answer a cursor. A menu that
 * insists on one or the other is a menu somebody has to be told how to use.
 */
final class StoneMenu {

    /** The design these numbers were written against; everything scales from it. */
    private static final float DESIGN_HEIGHT = 600f;
    /** Below this the lettering stops being worth shrinking and the list scrolls. */
    private static final float LEAST_SCALE = 0.62f;

    private static final float ROW_HEIGHT = 44f;
    private static final float ROW_WIDTH = 560f;
    private static final float MENU_WIDTH = 330f;
    private static final float CONTROL_WIDTH = 190f;
    private static final float SLIDER_HEIGHT = 9f;
    /** How big a picture beside the list may be, and the least it is worth drawing at. */
    private static final float PICTURE_SIDE = 240f;
    private static final float PICTURE_LEAST = 90f;

    // ---- what a screen is made of ----

    /** One line of a screen. */
    sealed interface Row {
        String label();
    }

    /**
     * A line that does something when taken.
     *
     * @param picture what the row is a row about, drawn beside the list while the row is the lit one — a map's
     *     preview, a hero's portrait — as a path the game's assets are loaded by, or null for a row with none
     */
    record Action(String label, Runnable take, boolean danger, String picture) implements Row {
        Action(String label, Runnable take) {
            this(label, take, false, null);
        }

        Action(String label, Runnable take, boolean danger) {
            this(label, take, danger, null);
        }
    }

    /** A line holding one of a short list of choices, changed in place. */
    record Choice(String label, List<String> options, IntSupplier read, IntConsumer write)
            implements Row {
    }

    /**
     * The same, but too long a list to cycle through — so it opens.
     *
     * <p>Cycling is fine for two or three and unusable for fifteen: a player
     * looking for a resolution should see the resolutions, not press right until
     * one of them goes past.
     */
    record Opens(String label, List<String> options, IntSupplier read, IntConsumer write)
            implements Row {
    }

    /** A line holding a number from nothing to all of it, drawn as a bar. */
    record Level(String label, IntSupplier read, IntConsumer write, int step) implements Row {
    }

    /** A line that is only there to be read. */
    record Words(String label, String value) implements Row {
    }

    /**
     * A pair of buttons at the foot of a screen, cut into stone sockets.
     *
     * <p>Apart from the rows above them on purpose. Save and Cancel are not two
     * more settings -- they are what happens to the settings -- and drawn as rows
     * they read as a third and fourth thing to adjust.
     *
     *  note  what is pending, shown between the rows and the buttons, or
     *     empty when there is nothing waiting
     */
    record Buttons(String label, String take, Runnable onTake, String leave,
            Runnable onLeave, String note) implements Row {
    }

    // ---- state ----

    private final StoneCraft craft;
    private final BitmapFont titleFont;
    private final BitmapFont rowFont;
    private final Node root = new Node("stone-menu");
    private final Node sheet = new Node("stone-menu-sheet");

    private String title = "";
    private String subtitle = "";
    private String hint = "";
    private String corner = "";
    private List<Row> rows = List.of();
    private final List<Node> drawn = new ArrayList<>();
    /**
     * Where each row is on the screen, and where inside it the thing that can be
     * dragged or clicked sits.
     *
     * <p>Kept in screen pixels rather than in the sheet's own units, because a
     * cursor arrives in screen pixels and converting one number once beats
     * converting it at every comparison.
     */
    private record Hit(float x, float y, float width, float height,
            float controlX, float controlWidth) {
        boolean holds(Vector2f cursor) {
            return cursor.x >= x && cursor.x <= x + width
                    && cursor.y >= y && cursor.y <= y + height;
        }
    }

    private final List<Hit> hitBoxes = new ArrayList<>();
    /** The open list's options, kept apart so an index is never two things. */
    private final List<Hit> optionBoxes = new ArrayList<>();
    private static final Hit OFF_SCREEN = new Hit(-1f, -1f, 0f, 0f, 0f, 0f);
    /** Where the two buttons at the foot are, and which of them is under the hand. */
    private final Hit[] buttonBoxes = {OFF_SCREEN, OFF_SCREEN};
    private int buttonSide;
    /** The slider the hand has hold of, or -1 — it keeps it until the button comes up. */
    private int held = -1;
    private int chosen;
    /** Which row is open, or -1 — only an {@link Opens} row can be. */
    private int opened = -1;
    private int openedAt;
    /** How far the list is scrolled, in rows, when even the smallest will not fit. */
    private int scrolledBy;

    private float screenWidth;
    private float screenHeight;
    private float scale = 1f;
    private boolean overGame;

    StoneMenu(StoneCraft craft, BitmapFont titleFont, BitmapFont rowFont, Node guiNode,
            float screenWidth, float screenHeight) {
        this.craft = craft;
        this.titleFont = titleFont == null ? craft.font() : titleFont;
        this.rowFont = rowFont == null ? craft.font() : rowFont;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        root.setQueueBucket(RenderQueue.Bucket.Gui);
        root.attachChild(sheet);
        guiNode.attachChild(root);
        hide();
    }

    // ---- showing ----

    /**
     * Put a screen up.
     *
     * @param overGame  whether the world is behind it. The front menu is drawn on
     *     its own stone; a pause menu is drawn over the room the player stopped
     *     in, dimmed but still legible, so he does not forget where he was
     */
    void show(String title, String subtitle, List<Row> rows, String hint, String corner,
            boolean overGame) {
        this.title = title == null ? "" : title;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.rows = List.copyOf(rows);
        this.hint = hint == null ? "" : hint;
        this.corner = corner == null ? "" : corner;
        this.overGame = overGame;
        if (chosen >= this.rows.size() || !takeable(chosen)) {
            chosen = firstTakeable();
        }
        opened = -1;
        held = -1; // these are not the rows he had hold of
        rebuild();
        root.setCullHint(Spatial.CullHint.Never);
    }

    /** Redraw with the same rows — after a value changed under one of them. */
    void refresh() {
        if (isVisible()) {
            rebuild();
        }
    }

    void hide() {
        root.setCullHint(Spatial.CullHint.Always);
    }

    /** Take it off the screen for good — the window changed shape and this is stale. */
    void destroy() {
        root.removeFromParent();
    }

    boolean isVisible() {
        return root.getCullHint() != Spatial.CullHint.Always;
    }

    void resize(float width, float height) {
        this.screenWidth = width;
        this.screenHeight = height;
        refresh();
    }

    // ---- moving about ----

    void up() {
        step(-1);
    }

    void down() {
        step(1);
    }

    private void step(int by) {
        if (rows.isEmpty()) {
            return;
        }
        if (opened >= 0) {
            var open = (Opens) rows.get(opened);
            openedAt = Math.clamp(openedAt + by, 0, open.options().size() - 1);
            rebuild();
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            chosen = Math.floorMod(chosen + by, rows.size());
            if (takeable(chosen)) {
                break;
            }
        }
        keepChosenInView();
        rebuild();
    }

    /** Change the chosen row's value, if it has one. */
    void left() {
        nudge(-1);
    }

    void right() {
        nudge(1);
    }

    private void nudge(int by) {
        if (opened >= 0 || rows.isEmpty()) {
            return;
        }
        switch (rows.get(chosen)) {
            case Choice choice -> {
                int next = Math.floorMod(choice.read().getAsInt() + by, choice.options().size());
                choice.write().accept(next);
            }
            case Level level -> level.write().accept(
                    Math.clamp(level.read().getAsInt() + by * level.step(), 0, 100));
            // The pair at the foot is one row wide and two things across.
            case Buttons ignored -> buttonSide = Math.clamp(buttonSide + by, 0, 1);
            // A list too long to cycle is opened rather than stepped through, and
            // a line that only does something has nothing to nudge.
            case Opens ignored -> {
                return;
            }
            default -> {
                return;
            }
        }
        rebuild();
    }

    /**
     * Take the chosen row.
     *
     * @return whether anything happened, so a caller can make a noise about it
     */
    boolean enter() {
        if (rows.isEmpty()) {
            return false;
        }
        if (opened >= 0) {
            var open = (Opens) rows.get(opened);
            open.write().accept(openedAt);
            opened = -1;
            rebuild();
            return true;
        }
        switch (rows.get(chosen)) {
            case Action action -> {
                action.take().run();
                return true;
            }
            case Buttons buttons -> {
                (buttonSide == 0 ? buttons.onTake() : buttons.onLeave()).run();
                return true;
            }
            case Opens open -> {
                opened = chosen;
                openedAt = Math.clamp(open.read().getAsInt(), 0, open.options().size() - 1);
                rebuild();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * Back out of whatever is innermost.
     *
     * @return whether it was the open list that closed, so the screen itself stays
     */
    boolean escape() {
        if (opened < 0) {
            return false;
        }
        opened = -1;
        rebuild();
        return true;
    }

    boolean isOpen() {
        return opened >= 0;
    }

    private boolean takeable(int index) {
        return index >= 0 && index < rows.size() && !(rows.get(index) instanceof Words);
    }

    private int firstTakeable() {
        for (int i = 0; i < rows.size(); i++) {
            if (takeable(i)) {
                return i;
            }
        }
        return 0;
    }

    // ---- the mouse ----

    /**
     * Light whatever the cursor is over.
     *
     * @return whether that changed, so a caller can make a noise about it
     */
    boolean hover(Vector2f cursor) {
        if (opened >= 0) {
            int over = optionAt(cursor);
            if (over < 0 || over == openedAt) {
                return false;
            }
            openedAt = over;
            rebuild();
            return true;
        }
        int under = rowAt(cursor);
        if (under < 0 || under == chosen) {
            return false;
        }
        chosen = under;
        rebuild();
        return true;
    }

    /**
     * Take whatever the cursor is on, at the place it is on it.
     *
     * <p>A settings row is three things across: a nudge left, the control itself,
     * a nudge right. Clicking the bar of a slider sets it where the cursor is,
     * which is the one gesture everybody tries first and the reason a keyboard-only
     * settings screen feels broken rather than austere.
     *
     * @return whether the click landed on something
     */
    boolean click(Vector2f cursor) {
        if (opened >= 0) {
            int over = optionAt(cursor);
            if (over < 0) {
                opened = -1; // clicked away from it; that is a way of closing it
                rebuild();
                return false;
            }
            openedAt = over;
            return enter();
        }
        int under = rowAt(cursor);
        if (under < 0) {
            return false;
        }
        chosen = under;
        var row = rows.get(under);
        var box = hitBoxes.get(under);
        if (row instanceof Buttons) {
            for (int side = 0; side < 2; side++) {
                if (buttonBoxes[side].holds(cursor)) {
                    buttonSide = side;
                    return enter();
                }
            }
            return false; // the stone between the two buttons is not a button
        }
        if (box.controlWidth() > 0f && (row instanceof Level || row instanceof Choice)) {
            if (cursor.x < box.controlX()) {
                left();
                return true;
            }
            if (cursor.x > box.controlX() + box.controlWidth()) {
                right();
                return true;
            }
            float across = (cursor.x - box.controlX()) / box.controlWidth();
            switch (row) {
                case Level level -> {
                    held = under; // and it keeps it until he lets go
                    level.write().accept(Math.clamp(Math.round(across * 100f), 0, 100));
                    rebuild();
                }
                case Choice choice -> {
                    int cell = Math.clamp((int) (across * choice.options().size()),
                            0, choice.options().size() - 1);
                    choice.write().accept(cell);
                    rebuild();
                }
                default -> {
                    return false;
                }
            }
            return true;
        }
        return enter();
    }

    /**
     * Keep the slider the hand took following it.
     *
     * <p>A volume is found by ear, not by arithmetic: you pull it until the room
     * sounds right. Clicking a bar sets it and dragging is that same gesture gone
     * on, so the bar is held from the press until the release and the cursor is
     * allowed to wander off the row in between — off the end it simply sits at
     * nothing or at everything, which is what a slider does everywhere else.
     *
     * @return whether the value moved, so a caller can spare itself the redraw
     */
    boolean drag(Vector2f cursor) {
        if (held < 0 || held >= rows.size() || !(rows.get(held) instanceof Level level)) {
            return false;
        }
        var box = hitBoxes.get(held);
        if (box.controlWidth() <= 0f) {
            return false;
        }
        float across = (cursor.x - box.controlX()) / box.controlWidth();
        int value = Math.clamp(Math.round(across * 100f), 0, 100);
        if (value == level.read().getAsInt()) {
            return false;
        }
        level.write().accept(value);
        rebuild();
        return true;
    }

    /** Whether a slider is being pulled, and the frame should keep asking. */
    boolean isDragging() {
        return held >= 0;
    }

    /** He let go. */
    void release() {
        held = -1;
    }

    private int rowAt(Vector2f cursor) {
        for (int i = 0; i < hitBoxes.size(); i++) {
            if (hitBoxes.get(i).holds(cursor)) {
                return i;
            }
        }
        return -1;
    }

    private int optionAt(Vector2f cursor) {
        for (int i = 0; i < optionBoxes.size(); i++) {
            if (optionBoxes.get(i).holds(cursor)) {
                return i;
            }
        }
        return -1;
    }

    // ---- drawing ----

    private void rebuild() {
        sheet.detachAllChildren();
        drawn.clear();
        hitBoxes.clear();
        optionBoxes.clear();

        boolean settings = rows.stream().anyMatch(row ->
                row instanceof Choice || row instanceof Level || row instanceof Opens);
        float wanted = wantedHeight(settings);
        scale = Math.min(1f, screenHeight / Math.max(wanted, 1f));
        boolean scrolls = scale < LEAST_SCALE;
        if (scrolls) {
            scale = LEAST_SCALE;
        } else {
            scrolledBy = 0;
        }
        sheet.setLocalScale(scale);

        if (overGame) {
            drawDim();
        } else {
            drawBackdrop();
        }
        float top = drawTitle(settings);
        if (settings) {
            drawRows(top, ROW_WIDTH, scrolls);
        } else {
            drawItems(top, scrolls);
        }
        drawFooter();
    }

    /** How tall the whole screen wants to be, at full size. */
    private float wantedHeight(boolean settings) {
        float titleBlock = title.isEmpty() ? 0f : (settings ? 110f : 210f);
        float body = rows.size() * (settings ? ROW_HEIGHT : ROW_HEIGHT);
        return titleBlock + body + 70f;
    }

    private void drawBackdrop() {
        float w = screenWidth / scale;
        float h = screenHeight / scale;
        StoneCraft.attach(sheet, craft.shaded("gloom", w, h,
                craft.gloomTop, craft.gloomMiddle,
                craft.gloomTop), 0f, 0f, 0f);
        // Two torches, off the edges, throwing light in. What makes it a room
        // rather than a colour.
        float pool = h * 0.30f;
        StoneCraft.attach(sheet, craft.glow("torch-left", pool,
                craft.torch, 0.09f), -pool * 0.25f, h * 0.58f, 1f);
        StoneCraft.attach(sheet, craft.glow("torch-right", pool,
                craft.torch, 0.09f), w + pool * 0.25f, h * 0.58f, 1f);
    }

    private void drawDim() {
        // Dark enough to read a menu over, thin enough to see the room he stopped
        // in. A player who cannot see where he was has to remember instead.
        StoneCraft.attach(sheet, craft.flat("dim", screenWidth / scale, screenHeight / scale,
                craft.veil), 0f, 0f, 0f);
    }

    /** @return the y the rows should start below, in the sheet's own units */
    private float drawTitle(boolean settings) {
        float w = screenWidth / scale;
        float h = screenHeight / scale;
        if (title.isEmpty()) {
            return h * 0.72f;
        }
        float size = settings ? 30f : 46f;
        var text = craft.text(titleFont, size, craft.torch, 0f, 0f, w,
                BitmapFont.Align.Center);
        text.setText(title);
        float plaqueWidth = Math.min(w - 40f, craft.widthOf(titleFont, size, title) + 92f);
        float plaqueHeight = size + (subtitle.isEmpty() ? 30f : 54f);
        float plaqueY = h - (settings ? 44f : 74f) - plaqueHeight;
        var plaque = craft.slab("plaque", plaqueWidth, plaqueHeight);
        StoneCraft.attach(sheet, plaque, (w - plaqueWidth) / 2f, plaqueY, 2f);

        float textY = plaqueY + plaqueHeight - size - (subtitle.isEmpty() ? 16f : 14f);
        // The box places it; a translation on top of that would move it twice,
        // which puts a title off the top of the screen and leaves a bare slab.
        text.setBox(new com.jme3.font.Rectangle(0f, textY + size, w, size * 1.4f));
        StoneCraft.attach(sheet, text, 0f, 0f, 4f);
        if (!subtitle.isEmpty()) {
            var sub = craft.text(rowFont, 14f, craft.hint, 0f,
                    textY - 24f, w, BitmapFont.Align.Center);
            sub.setText(subtitle);
            // In front of the plaque, like the title. Attached plainly it lands
            // at z zero, which is behind the stone it is written on.
            StoneCraft.attach(sheet, sub, 0f, 0f, 4f);
        }
        return plaqueY - (settings ? 26f : 46f);
    }

    /** The front and pause menus: a column of lit lines. */
    private void drawItems(float top, boolean scrolls) {
        float w = screenWidth / scale;
        float left = (w - MENU_WIDTH) / 2f;
        int from = scrolls ? scrolledBy : 0;
        int fits = scrolls ? (int) (top / ROW_HEIGHT) : rows.size();
        for (int i = 0; i < rows.size(); i++) {
            float y = top - (i - from + 1) * ROW_HEIGHT;
            if (i < from || i >= from + fits) {
                hitBoxes.add(OFF_SCREEN);
                continue;
            }
            var row = rows.get(i);
            boolean lit = i == chosen;
            boolean danger = row instanceof Action action && action.danger();
            var node = new Node("item-" + i);
            if (row instanceof Words words) {
                // A line under a name saying what taking it means: read rather than taken, so it carries none of
                // the marks that say a row can be chosen, and is given the width of the screen rather than the
                // column's — a sentence is longer than a word.
                var said = craft.text(rowFont, 14f, craft.hint, (MENU_WIDTH - ROW_WIDTH) / 2f,
                        ROW_HEIGHT - 20f, ROW_WIDTH, BitmapFont.Align.Center);
                said.setText(words.value());
                StoneCraft.attach(node, said, 0f, 0f, 3f);
                StoneCraft.attach(sheet, node, left, y, 3f);
                drawn.add(node);
                hitBoxes.add(OFF_SCREEN);
                continue;
            }
            if (lit) {
                StoneCraft.attach(node, craft.shaded("lit", MENU_WIDTH, ROW_HEIGHT - 6f,
                        StoneCraft.fade(danger ? craft.blood : craft.torch, 0f),
                        StoneCraft.fade(danger ? craft.blood : craft.torch, 0.13f),
                        StoneCraft.fade(danger ? craft.blood : craft.torch, 0f)),
                        0f, 3f, 1f);
            }
            var mark = lit ? (danger ? craft.blood : craft.torch)
                    : craft.rule;
            StoneCraft.attach(node, craft.flat("rule-l", 30f, 1f, mark), 14f,
                    ROW_HEIGHT / 2f, 2f);
            StoneCraft.attach(node, craft.flat("rule-r", 30f, 1f, mark),
                    MENU_WIDTH - 44f, ROW_HEIGHT / 2f, 2f);
            if (lit) {
                StoneCraft.attach(node, craft.arrowhead("mark-l", 9f, true, mark),
                        52f, ROW_HEIGHT / 2f - 4.5f, 3f);
                StoneCraft.attach(node, craft.arrowhead("mark-r", 9f, false, mark),
                        MENU_WIDTH - 61f, ROW_HEIGHT / 2f - 4.5f, 3f);
            }
            var colour = lit
                    ? (danger ? craft.dangerLit : craft.torchHot)
                    : craft.row;
            var text = craft.text(titleFont, 19f, colour, 0f, ROW_HEIGHT / 2f - 12f,
                    MENU_WIDTH, BitmapFont.Align.Center);
            text.setText(row.label());
            StoneCraft.attach(node, text, 0f, 0f, 3f);

            StoneCraft.attach(sheet, node, left, y, 3f);
            drawn.add(node);
            hitBoxes.add(new Hit(left * scale, (y + 3f) * scale, MENU_WIDTH * scale,
                    (ROW_HEIGHT - 6f) * scale, 0f, 0f));
        }
        drawPicture(top, left, w);
    }

    /**
     * What the lit row is a row about, beside the column: the map that would be played, the hero that would be
     * taken. Only the lit one, because a wall of thumbnails is a screen to search rather than a choice to make.
     *
     * <p>Nothing at all where the window is too narrow to hold one beside the list, or where the game ships no
     * such picture: a name on its own is a row that still works.
     */
    private void drawPicture(float top, float left, float w) {
        if (chosen < 0 || chosen >= rows.size() || !(rows.get(chosen) instanceof Action action)
                || action.picture() == null) {
            return;
        }
        float side = Math.min(PICTURE_SIDE, (w - MENU_WIDTH) / 2f - 48f);
        if (side < PICTURE_LEAST) {
            return;
        }
        var picture = craft.picture("picture", side, action.picture());
        if (picture == null) {
            return;
        }
        var quad = (com.jme3.scene.shape.Quad) picture.getMesh();
        var node = new Node("picture-plate");
        StoneCraft.attach(node, craft.slab("picture-slab", quad.getWidth() + 12f, quad.getHeight() + 12f), 0f, 0f, 0f);
        StoneCraft.attach(node, picture, 6f, 6f, 2f);
        StoneCraft.attach(sheet, node, left + MENU_WIDTH + 28f, top - quad.getHeight() - 12f, 3f);
        drawn.add(node);
    }

    /** The settings: a label, a control, and what it currently says. */
    private void drawRows(float top, float width, boolean scrolls) {
        float w = screenWidth / scale;
        float left = (w - width) / 2f;
        int from = scrolls ? scrolledBy : 0;
        int fits = scrolls ? Math.max(1, (int) (top / ROW_HEIGHT)) : rows.size();
        for (int i = 0; i < rows.size(); i++) {
            float y = top - (i - from + 1) * ROW_HEIGHT;
            if (i < from || i >= from + fits) {
                hitBoxes.add(OFF_SCREEN);
                continue;
            }
            boolean lit = i == chosen && opened < 0;
            // While a list is open the rows behind it go quiet, so there is one
            // place to look rather than two lit at once.
            boolean dimmed = opened >= 0 && i != opened;
            if (rows.get(i) instanceof Buttons buttons) {
                drawButtons(buttons, left, y, width, lit);
                continue;
            }
            var node = new Node("row-" + i);
            if (lit) {
                StoneCraft.attach(node, craft.shaded("lit", width, ROW_HEIGHT - 4f,
                        StoneCraft.fade(craft.torch, 0.11f),
                        StoneCraft.fade(craft.torch, 0.02f),
                        StoneCraft.fade(craft.torch, 0f)), 0f, 2f, 1f);
            }
            StoneCraft.attach(node, craft.flat("groove", width, 1f,
                    craft.groove), 0f, 0f, 1f);

            var row = rows.get(i);
            var labelColour = dimmed ? craft.dim
                    : lit ? craft.torchHot : craft.row;
            var label = craft.text(titleFont, 15f, labelColour, 16f,
                    ROW_HEIGHT / 2f - 10f, width * 0.45f, BitmapFont.Align.Left);
            label.setText(row.label());
            StoneCraft.attach(node, label, 0f, 0f, 3f);

            float controlLeft = width * 0.46f;
            drawControl(node, row, controlLeft, lit, dimmed, i);

            StoneCraft.attach(sheet, node, left, y, 3f);
            drawn.add(node);
            hitBoxes.add(new Hit(left * scale, y * scale, width * scale,
                    ROW_HEIGHT * scale, (left + width * 0.46f) * scale,
                    CONTROL_WIDTH * scale));
        }
        if (opened >= 0) {
            drawOpenList(top, left, width);
        }
    }

    /**
     * The two buttons at the foot of the settings, in sockets rather than rows.
     *
     * <p>Cut from the same stone as the hero's skill slots, because they are the
     * same idea: a thing you press, sunk into the panel. Save is lit; Cancel is
     * left cold, so the eye lands on the one that keeps the work.
     */
    private void drawButtons(Buttons buttons, float left, float y, float width, boolean lit) {
        var node = new Node("buttons");
        float socketWidth = 150f;
        float socketHeight = ROW_HEIGHT - 12f;
        float gap = 18f;
        float from = (width - socketWidth * 2f - gap) / 2f;

        if (!buttons.note().isEmpty()) {
            var note = craft.text(rowFont, 12f, craft.torch, 0f,
                    ROW_HEIGHT - 6f, width, BitmapFont.Align.Center);
            note.setText(buttons.note());
            StoneCraft.attach(node, note, 0f, 0f, 3f);
        }
        // Two, side by side. The lit one is whichever the player is on: this row
        // is a pair, and left and right move between them.
        for (int side = 0; side < 2; side++) {
            float x = from + side * (socketWidth + gap);
            boolean onThis = lit && buttonSide == side;
            var socket = craft.slab("socket", socketWidth, socketHeight);
            StoneCraft.attach(node, socket, x, 0f, 1f);
            if (onThis) {
                StoneCraft.attach(node, craft.flat("socket-lit", socketWidth, socketHeight,
                        StoneCraft.fade(craft.torch, 0.16f)), x, 0f, 2f);
            }
            var word = craft.text(titleFont, 14f,
                    onThis ? craft.torchHot
                            : side == 0 ? craft.bone : craft.mute,
                    0f, socketHeight / 2f - 9f, socketWidth, BitmapFont.Align.Center);
            word.setText(side == 0 ? buttons.take() : buttons.leave());
            StoneCraft.attach(node, word, x, 0f, 3f);
            buttonBoxes[side] = new Hit((left + x) * scale, y * scale,
                    socketWidth * scale, socketHeight * scale, 0f, 0f);
        }
        StoneCraft.attach(sheet, node, left, y, 3f);
        hitBoxes.add(new Hit(left * scale, y * scale, width * scale,
                ROW_HEIGHT * scale, 0f, 0f));
    }

    private void drawControl(Node node, Row row, float x, boolean lit, boolean dimmed,
            int index) {
        var arrowColour = dimmed ? craft.rule
                : lit ? craft.torch : craft.quiet;
        boolean nudgeable = row instanceof Choice || row instanceof Level;
        if (nudgeable) {
            StoneCraft.attach(node, craft.arrowhead("less", 8f, false, arrowColour),
                    x - 16f, ROW_HEIGHT / 2f - 4f, 3f);
            StoneCraft.attach(node, craft.arrowhead("more", 8f, true, arrowColour),
                    x + CONTROL_WIDTH + 8f, ROW_HEIGHT / 2f - 4f, 3f);
        }
        switch (row) {
            case Choice choice -> {
                int at = Math.clamp(choice.read().getAsInt(), 0, choice.options().size() - 1);
                // Cells side by side, so a row of two or three is one glance. A
                // longer list, or longer words, belongs in an Opens.
                float each = CONTROL_WIDTH / choice.options().size();
                for (int o = 0; o < choice.options().size(); o++) {
                    boolean on = o == at;
                    StoneCraft.attach(node, craft.shaded("cell", each - 4f, 20f,
                            on ? craft.onTop : craft.stoneDeep,
                            on ? craft.onFoot : craft.stoneDeep),
                            x + o * each, ROW_HEIGHT / 2f - 10f, 2f);
                    var word = craft.text(rowFont, 12f,
                            on ? craft.torch : craft.quiet,
                            0f, ROW_HEIGHT / 2f - 8f, each - 4f, BitmapFont.Align.Center);
                    word.setText(choice.options().get(o));
                    StoneCraft.attach(node, word, x + o * each, 0f, 3f);
                }
            }
            case Level level -> {
                int value = Math.clamp(level.read().getAsInt(), 0, 100);
                StoneCraft.attach(node, craft.flat("trough", CONTROL_WIDTH, SLIDER_HEIGHT,
                        craft.stoneDeep), x, ROW_HEIGHT / 2f - 4f, 2f);
                if (value > 0) {
                    StoneCraft.attach(node, craft.shaded("fill",
                            (CONTROL_WIDTH - 2f) * value / 100f, SLIDER_HEIGHT - 2f,
                            craft.fillTop, craft.torch,
                            craft.fillFoot), x + 1f, ROW_HEIGHT / 2f - 3f, 3f);
                }
                var shown = craft.text(titleFont, 13f,
                        dimmed ? craft.dim : craft.bone,
                        0f, ROW_HEIGHT / 2f - 9f, 58f, BitmapFont.Align.Right);
                shown.setText(value == 0 ? "OFF" : value + "%");
                StoneCraft.attach(node, shown, x + CONTROL_WIDTH + 22f, 0f, 3f);
            }
            case Opens open -> {
                int at = Math.clamp(open.read().getAsInt(), 0, open.options().size() - 1);
                var shown = craft.text(titleFont, 14f,
                        dimmed ? craft.dim : craft.bone,
                        0f, ROW_HEIGHT / 2f - 9f, CONTROL_WIDTH, BitmapFont.Align.Center);
                shown.setText(open.options().isEmpty() ? "—" : open.options().get(at));
                StoneCraft.attach(node, shown, x, 0f, 3f);
                // A chevron rather than the two arrows: this one opens, and the
                // mark should say which of the two things a row does.
                StoneCraft.attach(node, craft.arrowhead("opens", 8f, true,
                        lit ? craft.torch : craft.quiet),
                        x + CONTROL_WIDTH + 8f, ROW_HEIGHT / 2f - 4f, 3f);
            }
            case Words words -> {
                var shown = craft.text(titleFont, 14f, craft.mute, 0f,
                        ROW_HEIGHT / 2f - 9f, CONTROL_WIDTH, BitmapFont.Align.Center);
                shown.setText(words.value());
                StoneCraft.attach(node, shown, x, 0f, 3f);
            }
            default -> {
                // An Action in a settings list is a button; its label is enough.
            }
        }
    }

    /** The open list, over everything, with the current choice marked. */
    private void drawOpenList(float top, float left, float width) {
        var open = (Opens) rows.get(opened);
        float rowHeight = 26f;
        int count = open.options().size();
        float listHeight = count * rowHeight + 12f;
        float listWidth = 240f;
        float x = left + width - listWidth - 20f;
        float y = Math.max(8f, top - (opened + 1) * ROW_HEIGHT - listHeight + ROW_HEIGHT);

        var list = new Node("open");
        StoneCraft.attach(list, craft.slab("list", listWidth, listHeight), 0f, 0f, 0f);
        for (int o = 0; o < count; o++) {
            float rowY = listHeight - 6f - (o + 1) * rowHeight;
            boolean lit = o == openedAt;
            if (lit) {
                StoneCraft.attach(list, craft.flat("lit", listWidth - 12f, rowHeight - 2f,
                        StoneCraft.fade(craft.torch, 0.16f)), 6f, rowY, 1f);
            }
            boolean current = o == Math.clamp(open.read().getAsInt(), 0, count - 1);
            if (current) {
                StoneCraft.attach(list, craft.arrowhead("dot", 6f, true, craft.torch),
                        12f, rowY + rowHeight / 2f - 3f, 2f);
            }
            var text = craft.text(titleFont, 13f,
                    lit ? craft.torchHot : craft.row,
                    24f, rowY + rowHeight / 2f - 8f, listWidth - 36f, BitmapFont.Align.Left);
            text.setText(open.options().get(o));
            StoneCraft.attach(list, text, 0f, 0f, 2f);
        }
        StoneCraft.attach(sheet, list, x, y, 8f);
        // The list owns the mouse while it is up, and its options are kept in
        // their own list: an index that could mean either a row or an option is
        // an index that eventually means the wrong one.
        for (int o = 0; o < count; o++) {
            float rowY = listHeight - 6f - (o + 1) * rowHeight;
            optionBoxes.add(new Hit((x + 6f) * scale, (y + rowY) * scale,
                    (listWidth - 12f) * scale, (rowHeight - 2f) * scale, 0f, 0f));
        }
    }

    private void drawFooter() {
        float w = screenWidth / scale;
        if (!hint.isEmpty()) {
            var line = craft.text(rowFont, 13f, craft.quiet, 0f, 14f, w,
                    BitmapFont.Align.Center);
            line.setText(hint);
            StoneCraft.attach(sheet, line, 0f, 0f, 5f);
        }
        if (!corner.isEmpty()) {
            var line = craft.text(rowFont, 12f, craft.footnote, 0f, 12f, w - 16f,
                    BitmapFont.Align.Right);
            line.setText(corner);
            StoneCraft.attach(sheet, line, 0f, 0f, 5f);
        }
    }

    /** Scroll so the chosen row is on screen, when the list is too long to fit. */
    private void keepChosenInView() {
        int fits = Math.max(1, (int) (screenHeight / (ROW_HEIGHT * LEAST_SCALE)) - 4);
        if (chosen < scrolledBy) {
            scrolledBy = chosen;
        } else if (chosen >= scrolledBy + fits) {
            scrolledBy = chosen - fits + 1;
        }
    }

    /** Which row is lit, for whatever wants to know without asking the screen. */
    int chosenIndex() {
        return chosen;
    }

}
