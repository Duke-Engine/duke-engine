package uz.duke.client3d;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Quad;
import java.util.function.Function;
import uz.duke.core.pathfind.PathGrid;

/**
 * The ground and the rocks, and the ability to throw them away and lay down a
 * different world.
 *
 * <p>Split out from the client for one reason: it owns a rule that is easy to
 * break and was broken. Terrain used to be attached straight to the scene root
 * when the window opened, which quietly assumed a world is built once and lasts
 * forever. A game that lays out a new world — a roguelike beginning a new run —
 * broke that assumption, and the client went on drawing walls that no longer
 * existed while the units moved around a map the player could not see.
 *
 * <p>The rule is that rebuilding <b>replaces</b>. Everything goes into a node this
 * class owns, and every rebuild empties it first, so the scene holds one world's
 * worth of geometry no matter how many runs the player dies through. Keeping that
 * in a small class with a node and a grid — and no application, window or GPU —
 * is what lets a test hold it still.
 *
 * <p>Materials arrive through a factory rather than an {@code AssetManager} so the
 * shape of the scene can be checked without one.
 */
final class TerrainScene {

    private static final ColorRGBA GROUND = new ColorRGBA(0.16f, 0.22f, 0.13f, 1f);
    private static final ColorRGBA ROCK = new ColorRGBA(0.25f, 0.23f, 0.20f, 1f);

    /** Fallback size for a game that never set a map. */
    private static final float DEFAULT_WIDTH = 700f;
    private static final float DEFAULT_HEIGHT = 450f;

    private final Node root;
    private final Function<ColorRGBA, Material> material;

    TerrainScene(Node root, Function<ColorRGBA, Material> material) {
        this.root = root;
        this.material = material;
    }

    Node node() {
        return root;
    }

    /** Lay out {@code grid}, discarding whatever world was there before. */
    void rebuild(PathGrid grid) {
        root.detachAllChildren();

        float worldW = grid == null ? DEFAULT_WIDTH : grid.getWidth() * grid.getCellSize();
        float worldH = grid == null ? DEFAULT_HEIGHT : grid.getHeight() * grid.getCellSize();

        var ground = new Geometry("ground", new Quad(worldW, worldH));
        ground.setMaterial(material.apply(GROUND));
        ground.rotate(-FastMath.HALF_PI, 0, 0);
        ground.setLocalTranslation(0, 0, worldH);
        root.attachChild(ground);

        if (grid == null) {
            return;
        }
        float cell = grid.getCellSize();
        var rockMaterial = material.apply(ROCK);
        for (int cy = 0; cy < grid.getHeight(); cy++) {
            for (int cx = 0; cx < grid.getWidth(); cx++) {
                if (!grid.isBlocked(cx, cy)) {
                    continue;
                }
                var rock = new Geometry("rock", new Box(cell / 2f, 3f, cell / 2f));
                rock.setMaterial(rockMaterial);
                rock.setLocalTranslation((cx + 0.5f) * cell, 3f, (cy + 0.5f) * cell);
                root.attachChild(rock);
            }
        }
    }
}
