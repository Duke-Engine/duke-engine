package uz.dukeengine.client3d;

import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Quad;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import uz.dukeengine.core.pathfind.PathGrid;

/**
 * The ground of a map with no kit: a painted field or one flat plane, with a plinth under every raised room and a
 * block of rock where the stone is — and the rocks the fog hides.
 */
final class BlockGround {

    private static final ColorRGBA GROUND = new ColorRGBA(0.16f, 0.22f, 0.13f, 1f);
    static final ColorRGBA ROCK = new ColorRGBA(0.25f, 0.23f, 0.20f, 1f);

    /** Fallback size for a game that never set a map. */
    private static final float DEFAULT_WIDTH = 700f;
    private static final float DEFAULT_HEIGHT = 450f;

    /** Half the height of a stone block, which is drawn about a body tall. */
    private static final float BLOCK_HALF_HEIGHT = 3f;

    /** The user data a rock carries: the index of its cell. */
    private static final String CELL = "cell";

    private final Surfaces material;
    private final PaintedGround painted;
    private final boolean discovery;

    /**
     * Per-cell handles, kept only when there is discovery to apply. Indexed
     * {@code cy * width + cx}; a cell with no stone in it has no rock.
     */
    private Geometry[] rocks = new Geometry[0];
    /** The rock of each chunk of a painted map built a chunk at a time, each with its cell as user data. */
    private final Map<Integer, List<Geometry>> rocksByChunk = new HashMap<>();

    BlockGround(Surfaces material, PaintedGround painted, boolean discovery) {
        this.material = material;
        this.painted = painted;
        this.discovery = discovery;
    }

    /** Forget every rock, for a world about to be laid. */
    void clear() {
        rocks = new Geometry[0];
        rocksByChunk.clear();
    }

    /** Let go of the rock of a chunk let go. */
    void forget(int chunk) {
        rocksByChunk.remove(chunk);
    }

    /**
     * Lay {@code grid} on {@code root}: painted with {@code paint} where the map carries any, else on one plane (a
     * null grid gets the plane alone), and its stone.
     */
    void lay(PathGrid grid, GroundPaint paint, Node root) {
        if (grid != null && paint != null) {
            painted.lay(grid, paint, root);
        } else {
            float worldW = grid == null ? DEFAULT_WIDTH : grid.getWidth() * grid.getCellSize();
            float worldH = grid == null ? DEFAULT_HEIGHT : grid.getHeight() * grid.getCellSize();
            var plane = new Geometry("ground", new Quad(worldW, worldH));
            plane.setMaterial(material.of(GROUND, null));
            plane.rotate(-FastMath.HALF_PI, 0, 0);
            plane.setLocalTranslation(0, 0, worldH);
            root.attachChild(plane);
        }
        if (grid == null) {
            return;
        }
        rocks = discovery ? new Geometry[grid.getWidth() * grid.getHeight()] : new Geometry[0];
        layStone(grid, 0, 0, grid.getWidth() - 1, grid.getHeight() - 1, root, 0f, 0f, !painted.blended(paint), rock -> {
            if (discovery) {
                rocks[(int) rock.getUserData(CELL)] = rock;
            }
        });
    }

    /**
     * One chunk of a painted map, ground and stone, laid on {@code node} about its corner ({@code ox}, {@code oz}); its
     * rock kept with the chunk, to be let go with it.
     */
    void layChunk(int chunk, PathGrid grid, GroundPaint paint, int x0, int y0, int x1, int y1, Node node, float ox,
            float oz) {
        painted.layCells(grid, paint, x0, y0, x1, y1, node, ox, oz);
        var stone = new ArrayList<Geometry>();
        layStone(grid, x0, y0, x1, y1, node, ox, oz, !painted.blended(paint), stone::add);
        rocksByChunk.put(chunk, stone);
    }

    /**
     * The stone of the cells from ({@code x0}, {@code y0}) to ({@code x1}, {@code y1}) on a map drawn without a kit: a
     * block as tall as the tallest floor beside it on a stone cell — where {@code blocks}: a blended map's stone is the
     * relief and its cliffs, and draws nothing — and a plinth under a floor standing above the ground; hung on {@code
     * into}, placed less ({@code ox}, {@code oz}), each block handed to {@code each} with its cell.
     */
    private void layStone(PathGrid grid, int x0, int y0, int x1, int y1, Node into, float ox, float oz,
            boolean blocks, Consumer<Geometry> each) {
        float cell = grid.getCellSize();
        var stone = material.of(ROCK, null);
        var raised = material.of(GROUND, null);
        for (int cy = y0; cy <= y1; cy++) {
            for (int cx = x0; cx <= x1; cx++) {
                if (!grid.isTerrainBlocked(cx, cy)) {
                    // A room standing above the ground plane needs something under
                    // it, or its floor is a colour on the ground and the units
                    // walking about on it are in mid-air.
                    float ground = grid.storeyHeight(cx, cy);
                    if (ground > 0f) {
                        var plinth = new Geometry("plinth",
                                new Box(cell / 2f, ground / 2f, cell / 2f));
                        plinth.setMaterial(raised);
                        plinth.setLocalTranslation((cx + 0.5f) * cell - ox, ground / 2f,
                                (cy + 0.5f) * cell - oz);
                        into.attachChild(plinth);
                    }
                    continue;
                }
                if (!blocks) {
                    continue;
                }
                // Rock stands as tall as the tallest floor beside it, so a raised
                // room is walled in rather than looked over.
                float top = KitPlan.highestFloorAround(grid, cx, cy) + BLOCK_HALF_HEIGHT * 2f;
                var rock = new Geometry("rock", new Box(cell / 2f, top / 2f, cell / 2f));
                rock.setMaterial(stone);
                rock.setLocalTranslation((cx + 0.5f) * cell - ox, top / 2f, (cy + 0.5f) * cell - oz);
                rock.setUserData(CELL, cy * grid.getWidth() + cx);
                into.attachChild(rock);
                each.accept(rock);
            }
        }
    }

    /** Hide the rocks the fog has blacked out; {@code wide} is the map's width in cells. */
    void applyDiscovery(Discovery seen, int wide) {
        for (var stone : rocksByChunk.values()) {
            for (var rock : stone) {
                hide(seen, rock, rock.getUserData(CELL), wide);
            }
        }
        for (int index = 0; index < rocks.length; index++) {
            if (rocks[index] != null) {
                hide(seen, rocks[index], index, wide);
            }
        }
    }

    private static void hide(Discovery seen, Geometry rock, int index, int wide) {
        rock.setCullHint(seen.hidden(index % wide, index / wide)
                ? Spatial.CullHint.Always : Spatial.CullHint.Inherit);
    }
}
