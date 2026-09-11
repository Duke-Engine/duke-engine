package uz.duke.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.cursors.plugins.JmeCursor;
import com.jme3.input.InputManager;
import com.jme3.texture.Image;
import com.jme3.util.BufferUtils;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * What the mouse pointer looks like, and when.
 *
 * <p>The pointer was the operating system's white arrow, which says the same
 * thing over a skeleton as over a wall: nothing. Half of an RTS's controls are
 * "the right-click means something different here", and the only place a game can
 * say so before the click is the pointer.
 *
 * <p><b>Which pictures</b> is the game's — a dungeon's pointer is not a skirmish
 * game's — and they arrive through {@link Visuals} with the rest of its art.
 * <b>When each one</b> is the client's, because what is under the pointer is a
 * fact about this screen: a unit, the bar, a skill waiting to be aimed. The
 * client names the situations and the game paints them.
 *
 * <p><b>Two conversions that are easy to get silently wrong,</b> both pinned by
 * {@code CursorsTest}:
 *
 * <ul>
 *   <li>jME's cursor image is stored <em>bottom-up</em>: the last row of the
 *       buffer is the top row of the picture. Getting it backwards gives an
 *       upside-down pointer, which reads as a different picture rather than as a
 *       bug.
 *   <li>its hot spot is measured <em>from the bottom</em> — the windowing layer
 *       is handed {@code height - yHotSpot}. A game says where the tip is the way
 *       anyone looking at the file would, from the top left, and this turns it
 *       round.
 * </ul>
 *
 * <p>Nothing here is required. A game that names no pointers keeps the system
 * arrow, which is what every game had, and a file that will not load costs that
 * one situation and a line in the log.
 */
final class Cursors {

    private static final Logger LOG = Logger.getLogger(Cursors.class.getName());

    /** Over the world with nothing under it, and over the bar. The ordinary arrow. */
    static final String POINT = "Point";

    /** Over one of his own. */
    static final String FRIEND = "Friend";

    /** Over something a right-click would attack. */
    static final String ATTACK = "Attack";

    /** A skill is armed and this is somewhere it can go. */
    static final String AIM = "Aim";

    /** A skill is armed and this is not. */
    static final String DENY = "Deny";

    /**
     * One pointer.
     *
     * @param hotX how far from the <em>left</em> of the picture the tip is
     * @param hotY how far from the <em>top</em> of the picture the tip is — read
     *             off the file the way a person reads it, and turned round here
     */
    record Look(String image, int hotX, int hotY) {
    }

    private final AssetManager assets;
    private final InputManager input;
    private final Map<String, Look> looks;
    private final Map<String, JmeCursor> made = new HashMap<>();
    private final Set<String> missing = new HashSet<>();

    /** What is on screen now, so the same pointer is not set forty times a second. */
    private String showing;

    Cursors(AssetManager assets, InputManager input, Map<String, Look> looks) {
        this.assets = assets;
        this.input = input;
        this.looks = new LinkedHashMap<>(looks == null ? Map.of() : looks);
    }

    /** Whether the game named any pointer at all. */
    boolean any() {
        return !looks.isEmpty();
    }

    /**
     * Show the pointer for a situation, if the game named one.
     *
     * <p>Setting a cursor talks to the window, so it is done only when the answer
     * changes. A situation the game did not paint leaves whatever was there
     * rather than falling back to the system arrow: flicking to white and back as
     * the pointer crosses a creature would be worse than one picture serving two
     * situations.
     */
    void show(String situation) {
        if (situation == null || situation.equals(showing)) {
            return;
        }
        var cursor = cursorFor(situation);
        if (cursor == null) {
            return;
        }
        showing = situation;
        input.setMouseCursor(cursor);
    }

    private JmeCursor cursorFor(String situation) {
        if (made.containsKey(situation)) {
            return made.get(situation);
        }
        var look = looks.get(situation);
        if (look == null) {
            made.put(situation, null);
            return null;
        }
        JmeCursor cursor = null;
        try {
            cursor = build(assets.loadTexture(look.image()).getImage(), look);
        } catch (RuntimeException e) {
            if (missing.add(look.image())) {
                LOG.warning(() -> "pointer not found: " + look.image() + " (" + e.getMessage()
                        + ") — leaving the one that is there");
            }
        }
        made.put(situation, cursor);
        return cursor;
    }

    /**
     * One picture, turned into the shape jME hands the window.
     *
     * <p>Package-private and taking the image rather than a path so that the two
     * conversions can be checked without a window or a file.
     */
    static JmeCursor build(Image image, Look look) {
        int width = image.getWidth();
        int height = image.getHeight();
        var pixels = argb(image);
        var data = BufferUtils.createIntBuffer(width * height);
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                // Bottom-up: the buffer's last row is the picture's first.
                data.put((height - 1 - row) * width + column, pixels[row * width + column]);
            }
        }
        var cursor = new JmeCursor();
        cursor.setWidth(width);
        cursor.setHeight(height);
        cursor.setNumImages(1);
        cursor.setImagesData(data);
        cursor.setxHotSpot(clamp(look.hotX(), width));
        // From the bottom, because that is what is subtracted from the height
        // again on the way out.
        cursor.setyHotSpot(height - clamp(look.hotY(), height));
        cursor.setImagesDelay(IntBuffer.allocate(0));
        return cursor;
    }

    private static int clamp(int at, int size) {
        return Math.max(0, Math.min(size - 1, at));
    }

    /**
     * The picture's pixels as ARGB, row by row from the top.
     *
     * <p>jME keeps a loaded PNG as bytes in whatever order its format says, and
     * the two orders a PNG arrives in are the two handled here. Anything else is
     * drawn as nothing rather than as noise — a pointer of garbage is harder to
     * recognise as a missing case than no pointer at all.
     */
    private static int[] argb(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        var out = new int[width * height];
        var buffer = image.getData(0);
        if (buffer == null) {
            return out;
        }
        int stride = switch (image.getFormat()) {
            case RGBA8, ABGR8, BGRA8 -> 4;
            case RGB8, BGR8 -> 3;
            default -> 0;
        };
        if (stride == 0) {
            LOG.warning(() -> "a pointer in " + image.getFormat()
                    + " is not a format this can read — save it as RGBA8 PNG");
            return out;
        }
        for (int i = 0; i < out.length && (i + 1) * stride <= buffer.limit(); i++) {
            int at = i * stride;
            int one = buffer.get(at) & 0xFF;
            int two = buffer.get(at + 1) & 0xFF;
            int three = buffer.get(at + 2) & 0xFF;
            int four = stride == 4 ? buffer.get(at + 3) & 0xFF : 0xFF;
            out[i] = switch (image.getFormat()) {
                case RGBA8 -> (four << 24) | (one << 16) | (two << 8) | three;
                case ABGR8 -> (one << 24) | (four << 16) | (three << 8) | two;
                case BGRA8 -> (four << 24) | (three << 16) | (two << 8) | one;
                case RGB8 -> 0xFF000000 | (one << 16) | (two << 8) | three;
                default -> 0xFF000000 | (three << 16) | (two << 8) | one; // BGR8
            };
        }
        return out;
    }
}
