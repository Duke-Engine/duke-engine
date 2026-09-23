package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.pathfind.MapLoader;

/**
 * A game whose world is meant to look like different places at different times.
 *
 * <p>The client's side of that is small and worth pinning down anyway, because
 * the failures are quiet ones. A theme is a bundle of looks under a name; the
 * game says which name is current and the client holds the rest. Nothing here
 * knows what a depth is, or that the game calling itself a dungeon has floors —
 * three other apps launch this client and none of them has themes at all.
 */
class ThemedLookTest {

    private static final String ROOM = """
            #####
            #...#
            #...#
            #...#
            #####
            """;

    private static final class Stub implements TileSource {
        @Override
        public Spatial piece(String assetPath) {
            return new Node(assetPath);
        }
    }

    private static Tileset kit(String floor, String wall) {
        return Tileset.create().floor(floor).wall(wall).tileSize(4f).wallHeight(4f);
    }

    // ---- what a theme is ----

    /** A game with no themes has none, and everything about it is as it was. */
    @Test
    void aGameWithNoThemesHasNone() {
        var visuals = Visuals.create().unit("Skeleton", u -> u.scale(2f));

        assertFalse(visuals.hasThemes());
        assertNull(visuals.getTheme("SciFi"));
        assertNull(visuals.getTheme(null), "and nothing is not a theme either");
        assertEquals(2f, visuals.of("Skeleton").scale, 0.001f);
    }

    /** A theme is looked up by exactly the name it was given. */
    @Test
    void aThemeIsFoundByItsName() {
        var visuals = Visuals.create()
                .theme("SciFi,Bare", look -> look.tiles(kit("plate.obj", "bulkhead.obj")))
                .theme("Ruins,Fallen", look -> look.tiles(kit("flags.obj", "broken.obj")));

        assertTrue(visuals.hasThemes());
        assertEquals("plate.obj", visuals.getTheme("SciFi,Bare").getTiles().getFloor());
        assertEquals("broken.obj", visuals.getTheme("Ruins,Fallen").getTiles().getWall());
        assertNull(visuals.getTheme("SciFi"), "half a name is not the name");
    }

    /**
     * A theme speaks for the creatures it names and for no others.
     *
     * <p>Which is the whole of the arrangement: a floor that turns skeletons into
     * robots leaves everything else exactly as the game described it, and a
     * creature nobody themed is drawn the same way on every floor.
     */
    @Test
    void aThemeSpeaksOnlyForTheCreaturesItNames() {
        var visuals = Visuals.create()
                .unit("Skeleton", u -> u.scale(2f))
                .unit("Brute", u -> u.scale(5f))
                .theme("SciFi,Bare", look -> look.unit("Skeleton", u -> u.scale(4f)));

        var theme = visuals.getTheme("SciFi,Bare");
        assertNotNull(theme.of("Skeleton"), "it has an opinion about this one");
        assertEquals(4f, theme.of("Skeleton").scale, 0.001f);
        assertNull(theme.of("Brute"), "and none about this one");
        assertEquals(5f, visuals.of("Brute").scale, 0.001f,
                "so the game's own answer still stands");
    }

    /** What the dark is coloured is the theme's to say, or nobody's. */
    @Test
    void aThemeMaySayWhatColourNothingIs() {
        var visuals = Visuals.create()
                .theme("Ice,Blue", look -> look.fogTint(0x0A1830))
                .theme("Plain,Stone", look -> look.tiles(kit("floor.obj", "wall.obj")));

        assertEquals(0x0A1830, visuals.getTheme("Ice,Blue").getFogTint());
        assertNull(visuals.getTheme("Plain,Stone").getFogTint(),
                "a theme with no opinion leaves the game's own colour alone");
    }

    // ---- building a world from one ----

    /**
     * The pieces of a kind lying on the ground.
     *
     * <p>The lid over the rock is a floor tile as well -- the same asset, laid at
     * the top of the walls -- so anything counting floors has to say which it
     * means.
     */
    private static java.util.List<Spatial> onTheGround(Node root, String named) {
        return pieces(root, named).stream()
                .filter(piece -> piece.getLocalTranslation().y == 0f)
                .toList();
    }

    private static java.util.List<Spatial> pieces(Node root, String named) {
        var found = new java.util.ArrayList<Spatial>();
        for (var cell : root.getChildren()) {
            for (var piece : ((Node) cell).getChildren()) {
                if (piece.getName().equals(named)) {
                    found.add(piece);
                }
            }
        }
        return found;
    }

    /**
     * A rebuild may bring a different kit, and then the world is built of it.
     *
     * <p>Replacing, like every other rebuild: the old kit's pieces go with the old
     * world rather than lingering underneath the new one.
     */
    @Test
    void anotherKitBuildsAnotherWorld() {
        var root = new Node("terrain");
        var terrain = new TerrainScene(root, (colour, texture) -> null, true,
                kit("stone.obj", "stonewall.obj"), new Stub());
        var grid = MapLoader.fromText(ROOM);

        terrain.rebuild(grid);
        assertEquals(9, onTheGround(root, "stone.obj").size(), "nine cells of the first kit");

        terrain.rebuild(grid, kit("plate.obj", "bulkhead.obj"));

        assertEquals(9, onTheGround(root, "plate.obj").size(), "and nine of the second");
        assertEquals(0, pieces(root, "stone.obj").size(), "with none of the first left");
    }

    /** No kit at all falls back to the one the game started with. */
    @Test
    void noKitFallsBackToTheGamesOwn() {
        var root = new Node("terrain");
        var terrain = new TerrainScene(root, (colour, texture) -> null, true,
                kit("stone.obj", "stonewall.obj"), new Stub());

        terrain.rebuild(MapLoader.fromText(ROOM), null);

        assertEquals(9, onTheGround(root, "stone.obj").size(),
                "a game between themes still has a floor");
    }

    /**
     * A kit whose walls were modelled on a different module from its floors is
     * scaled by its own.
     *
     * <p>One of the kits this draws lays two-unit floor tiles inside four-unit
     * walls — a wall spans two tiles. Scaled by the floor's number it comes out
     * twice as wide as the cell it stands on; the whole point of the second number
     * is that it does not.
     */
    @Test
    void wallsOnTheirOwnModuleAreScaledByIt() {
        var root = new Node("terrain");
        var twoAndFour = Tileset.create().floor("floor.obj").wall("wall.obj")
                .tileSize(2f).wallTileSize(4f).wallHeight(4f);
        var terrain = new TerrainScene(root, (colour, texture) -> null, true, twoAndFour, new Stub());
        var grid = MapLoader.fromText(ROOM);

        terrain.rebuild(grid);

        float cell = grid.getCellSize();
        assertEquals(cell / 2f, onTheGround(root, "floor.obj").get(0).getLocalScale().x, 0.001f,
                "a two-unit tile is scaled to fill a cell");
        assertEquals(cell / 4f, pieces(root, "wall.obj").get(0).getLocalScale().x, 0.001f,
                "and a four-unit wall by half as much, so it spans one cell too");
    }

    /**
     * A kit that centres its walls on their own origin has them lifted onto the
     * floor rather than left half sunk into it.
     */
    @Test
    void wallsAreLiftedOntoTheFloorByWhatTheKitSays() {
        var root = new Node("terrain");
        var sunken = Tileset.create().floor("floor.obj").wall("wall.obj")
                .tileSize(2f).wallHeight(2f).wallLift(1f);
        var terrain = new TerrainScene(root, (colour, texture) -> null, true, sunken, new Stub());
        var grid = MapLoader.fromText(ROOM);

        terrain.rebuild(grid);

        float scale = grid.getCellSize() / 2f;
        assertEquals(1f * scale, pieces(root, "wall.obj").get(0).getLocalTranslation().y, 0.001f,
                "the lift is in model units, so it scales with everything else");
        assertEquals(9, onTheGround(root, "floor.obj").size(),
                "and the floors are not lifted with them");
    }

    /**
     * The shift moves a wall along its own facing, not along the world's.
     *
     * <p>Four walls of a room face four ways, so a shift that moved them all the
     * same way in world space would push one into the room and one out of it.
     */
    @Test
    void theShiftFollowsTheWallsOwnFacing() {
        var root = new Node("terrain");
        var shifted = Tileset.create().floor("floor.obj").wall("wall.obj")
                .tileSize(2f).wallHeight(2f).wallShift(-0.5f);
        var plain = Tileset.create().floor("floor.obj").wall("wall.obj")
                .tileSize(2f).wallHeight(2f);
        var grid = MapLoader.fromText(ROOM);

        var shiftedRoot = new Node("shifted");
        new TerrainScene(shiftedRoot, (colour, texture) -> null, true, shifted, new Stub()).rebuild(grid);
        var plainRoot = new Node("plain");
        new TerrainScene(plainRoot, (colour, texture) -> null, true, plain, new Stub()).rebuild(grid);

        // Every wall moved, and no two of them the same way — which is what
        // "along its own facing" means when the room has four sides.
        var moves = new java.util.HashSet<String>();
        var shiftedWalls = pieces(shiftedRoot, "wall.obj");
        var plainWalls = pieces(plainRoot, "wall.obj");
        assertEquals(plainWalls.size(), shiftedWalls.size());
        for (int i = 0; i < shiftedWalls.size(); i++) {
            var moved = shiftedWalls.get(i).getLocalTranslation()
                    .subtract(plainWalls.get(i).getLocalTranslation());
            assertNotEquals(0f, moved.length(), 0.001f, "a wall that did not move at all");
            moves.add(Math.round(moved.x) + "," + Math.round(moved.z));
        }
        assertTrue(moves.size() >= 4, "the walls all moved the same way: " + moves);
    }
}
