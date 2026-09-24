package uz.dukeengine.client3d;

import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.game.view.CommandButton;

/**
 * What the player may do with whatever he has selected, drawn as a grid of buttons in the corner.
 *
 * <p>The engine had nothing of the sort. A dungeon's {@link HeroPanel} draws one hero's skills and reads
 * them out of a line of text, which works because the thing selected never changes; an RTS selects a
 * barracks and then a tank and wants a different set of orders each time. Without this, nothing could be
 * built, trained or ordered except from the keyboard.
 *
 * <p><b>It knows what none of the buttons mean.</b> Each is a picture, a word, a key and whether it may be
 * pressed — worked out by the game on the simulation thread and carried in the snapshot — and what goes
 * back is the button's own id. Training a rifleman, casting a spell and calling an airstrike are one thing
 * from here.
 *
 * <p>Rebuilt only when the buttons change, compared by what is drawn rather than by identity: a snapshot
 * arrives every frame and almost always says the same thing, and rebuilding a grid of textured quads sixty
 * times a second for a bar nobody touched is the sort of cost nobody goes looking for afterwards.
 */
final class CommandBar {

    /** How wide a button is, and the gap between two — a socket big enough to read an icon in. */
    private static final float BUTTON = 54f;
    private static final float GAP = 4f;

    /** Generals' own bar is three rows of five; the shape is a good one and this keeps it. */
    private static final int COLUMNS = 5;

    /** How far in from the corner the bar sits, clear of the edge of the screen. */
    private static final float MARGIN = 12f;

    /** Ready, and out of reach: a button that cannot be pressed is dimmed rather than taken away. */
    private static final ColorRGBA READY = new ColorRGBA(1f, 1f, 1f, 1f);
    private static final ColorRGBA BARRED = new ColorRGBA(0.4f, 0.4f, 0.4f, 0.7f);
    /** What is still to do on a button showing progress. */
    private static final ColorRGBA PROGRESS_SHADE = new ColorRGBA(0f, 0f, 0f, 0.6f);

    private final StoneCraft craft;
    private final BitmapFont font;
    private final Node root = new Node("command-bar");

    /** Where each button ended up, so a click can be answered without asking the scene graph. */
    private record Placed(CommandButton button, float left, float bottom) {

        boolean holds(float x, float y) {
            return x >= left && x < left + BUTTON && y >= bottom && y < bottom + BUTTON;
        }
    }

    private final List<Placed> placed = new ArrayList<>();
    private List<CommandButton> drawn = List.of();
    private float screenWidth;

    CommandBar(StoneCraft craft, BitmapFont font) {
        this.craft = craft;
        this.font = font;
    }

    Node node() {
        return root;
    }

    /**
     * Draw {@code buttons}, which is usually to do nothing at all.
     *
     * <p>The width is passed in rather than kept because a window is resizable and the bar is measured
     * from the right-hand edge.
     */
    void show(List<CommandButton> buttons, float screenWidth) {
        if (buttons.equals(drawn) && screenWidth == this.screenWidth) {
            return;
        }
        this.drawn = List.copyOf(buttons);
        this.screenWidth = screenWidth;
        root.detachAllChildren();
        placed.clear();
        if (buttons.isEmpty()) {
            return;
        }
        int rows = (buttons.size() + COLUMNS - 1) / COLUMNS;
        float width = COLUMNS * BUTTON + (COLUMNS + 1) * GAP;
        float height = rows * BUTTON + (rows + 1) * GAP;
        float left = Math.max(MARGIN, screenWidth - width - MARGIN);
        StoneCraft.attach(root, craft.slab("bar", width, height), left, MARGIN, 0f);

        for (int i = 0; i < buttons.size(); i++) {
            // Filled left to right and top down, so the order the game wrote them in is the order they are
            // read in — a bar whose buttons move about is a bar nobody can learn.
            float x = left + GAP + i % COLUMNS * (BUTTON + GAP);
            float y = MARGIN + height - GAP - (i / COLUMNS + 1f) * BUTTON - i / COLUMNS * GAP;
            placed.add(new Placed(buttons.get(i), x, y));
            drawButton(buttons.get(i), x, y);
        }
    }

    private void drawButton(CommandButton button, float x, float y) {
        StoneCraft.attach(root, craft.slab("socket", BUTTON, BUTTON), x, y, 1f);
        var colour = button.available() ? READY : BARRED;
        var picture = button.picture() == null ? null
                : craft.picture("icon", BUTTON - 8f, button.picture());
        if (picture != null) {
            picture.getMaterial().setColor("Color", colour);
            StoneCraft.attach(root, picture, x + 4f, y + 4f, 2f);
        } else {
            // No picture, or one that would not load: the word is what a button is for, and a socket with
            // a name in it is a button that still works. See StoneCraft.picture.
            var name = craft.text(font, 13f, colour, x + 4f, y + BUTTON / 2f - 8f, BUTTON - 8f,
                    BitmapFont.Align.Center);
            name.setText(shortened(button.label()));
            StoneCraft.attach(root, name, 0f, 0f, 2f);
        }
        if (button.progress() >= 0f && button.progress() < 1f) {
            // What is still to do, shaded from the top down: the reference sweeps a clock over its queue icons.
            float side = BUTTON - 8f;
            var shade = craft.flat("progress", side, side * (1f - button.progress()), PROGRESS_SHADE);
            shade.getMaterial().getAdditionalRenderState()
                    .setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
            StoneCraft.attach(root, shade, x + 4f, y + 4f + side * button.progress(), 2.5f);
        }
        if (button.hotkey() != null && !button.hotkey().isEmpty()) {
            var key = craft.text(font, 11f, colour, x + 4f, y + 2f, BUTTON - 8f, BitmapFont.Align.Right);
            key.setText(button.hotkey());
            StoneCraft.attach(root, key, 0f, 0f, 3f);
        }
    }

    /** As much of a name as fits in a socket, which is about six letters at this size. */
    private static String shortened(String label) {
        return label.length() <= 7 ? label : label.substring(0, 7);
    }

    /**
     * The button under a point of the screen, or null — which is also how a click is told from a click on
     * the world behind it, so an order is not given at the same time as a button is pressed.
     *
     * <p>Answered from where each button was laid rather than by picking the scene: the bar is a dozen
     * rectangles that have not moved since they were drawn, and a ray through a GUI node is a great deal
     * of machinery for a point-in-rectangle test.
     */
    CommandButton at(float screenX, float screenY) {
        for (var one : placed) {
            if (one.holds(screenX, screenY)) {
                return one.button();
            }
        }
        return null;
    }

    /** Whether anything is drawn at all — a bar with nothing in it takes no clicks. */
    boolean isEmpty() {
        return placed.isEmpty();
    }

    void hide() {
        root.setCullHint(Spatial.CullHint.Always);
    }

    void unhide() {
        root.setCullHint(Spatial.CullHint.Inherit);
    }
}
