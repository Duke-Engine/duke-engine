package uz.duke.core.client;

import java.util.Arrays;
import uz.duke.core.GameLogic;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.KindOf;

/**
 * A dependency-free {@link Renderer} that draws the world to a grid of
 * characters — a top-down minimap of the simulation.
 *
 * <p>World ground coordinates ({@code x} east, {@code y} north) are mapped onto a
 * {@code cols}×{@code rows} character grid spanning {@code worldWidth}×
 * {@code worldHeight}. Each visible object is a glyph chosen by its
 * {@link KindOf} — {@code B}uilding, {@code V}ehicle, {@code I}nfantry, else
 * {@code O} — upper-case for the viewing player's own units, lower-case for
 * everyone else. Fog of war is honoured: only objects the viewer can see appear.
 *
 * <p>This makes "rendering" real and testable without a GPU; a 3D backend is a
 * drop-in alternative {@link Renderer}.
 */
public final class AsciiRenderer implements Renderer {

    private final int cols;
    private final int rows;
    private final float worldWidth;
    private final float worldHeight;

    public AsciiRenderer(int cols, int rows, float worldWidth, float worldHeight) {
        this.cols = cols;
        this.rows = rows;
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
    }

    @Override
    public String render(GameLogic logic, int viewerPlayer) {
        var grid = new char[rows][cols];
        for (var row : grid) {
            Arrays.fill(row, '.');
        }

        for (var object : logic.getObjects()) {
            if (object.isContained() || !logic.canSee(viewerPlayer, object)) {
                continue;
            }
            int col = clamp((int) (object.getPosition().x() / worldWidth * cols), cols);
            int row = clamp((int) (object.getPosition().y() / worldHeight * rows), rows);
            grid[row][col] = glyph(object, viewerPlayer);
        }

        var sb = new StringBuilder(rows * (cols + 1));
        for (int r = 0; r < rows; r++) {
            sb.append(grid[r]);
            if (r < rows - 1) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static char glyph(GameObject object, int viewerPlayer) {
        char base;
        if (object.isKindOf(KindOf.STRUCTURE)) {
            base = 'B';
        } else if (object.isKindOf(KindOf.VEHICLE)) {
            base = 'V';
        } else if (object.isKindOf(KindOf.INFANTRY)) {
            base = 'I';
        } else {
            base = 'O';
        }
        return object.getPlayerIndex() == viewerPlayer ? base : Character.toLowerCase(base);
    }

    private static int clamp(int value, int size) {
        if (value < 0) {
            return 0;
        }
        return Math.min(value, size - 1);
    }
}
