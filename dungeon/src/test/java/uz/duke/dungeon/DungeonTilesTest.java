package uz.duke.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.AssetManager;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import org.junit.jupiter.api.Test;

/**
 * The dungeon's tiles really are on the classpath, really load, and really are
 * the size the placement code assumes.
 *
 * <p>Worth a test rather than a look at the screen, because every way this can go
 * wrong looks identical from a chair: a missing file, a texture the model asks
 * for under a name nobody shipped, a loader that is not registered, a tile of the
 * wrong size. All of them come out as "the dungeon is black", and none of them is
 * a rendering problem.
 *
 * <p>No window is opened. Asset loading needs no display, which is what makes
 * this possible at all — and means a broken asset fails in a build rather than in
 * front of a player.
 */
class DungeonTilesTest {

    /** Where the models live on the classpath, and the size one tile is authored at. */
    private static final String TILES = "models/tiles/dungeon/";
    private static final float TILE = 4f;

    private static AssetManager assets() {
        // Loads the default config: the classpath locator, the PNG loader, and
        // the glTF loader this kit needs, all of which jME registers itself.
        return new DesktopAssetManager(true);
    }

    private static BoundingBox boundsOf(Spatial model) {
        model.updateModelBound();
        model.updateGeometricState();
        return (BoundingBox) model.getWorldBound();
    }

    /** The floor tile loads, and is one tile across. */
    @Test
    void theFloorTileLoadsAtTheSizeWeAssume() {
        var floor = assets().loadModel(TILES + "floor.gltf");

        assertNotNull(floor);
        var bounds = boundsOf(floor);
        assertEquals(TILE / 2f, bounds.getXExtent(), 0.01f, "four units across");
        assertEquals(TILE / 2f, bounds.getZExtent(), 0.01f, "and four deep");
    }

    /**
     * A tile is exactly a cell once scaled, which is the whole reason this kit
     * fits: the map is 10-unit cells and the kit is 4-unit tiles, so one number
     * relates them and nothing has to be nudged into place.
     */
    @Test
    void aTileIsExactlyOneMapCellWhenScaled() {
        float cell = uz.duke.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;

        assertEquals(cell, TILE * (cell / TILE), 0.0001f);
        assertEquals(2.5f, cell / TILE, 0.0001f, "a round scale, not a fudge factor");
    }

    /**
     * The wall stands on one edge of its tile rather than filling it.
     *
     * <p>This is the fact the placement code is built on: a wall is put on the
     * boundary between an open cell and a solid one, not in the middle of the
     * solid cell. If a future version of the kit made walls tile-filling, the
     * dungeon would quietly grow walls half a cell out of place, and this is where
     * that would be caught.
     */
    @Test
    void theWallStandsOnAnEdgeAndIsTallerThanItIsDeep() {
        var bounds = boundsOf(assets().loadModel(TILES + "wall.gltf"));

        assertEquals(TILE / 2f, bounds.getXExtent(), 0.01f, "as wide as the tile");
        assertTrue(bounds.getZExtent() < TILE / 2f + 0.01f,
                "but only half as deep: it is an edge piece, " + bounds.getZExtent());
        // Exactly one module, which is the fact a storey rests on: ten world units
        // to a cell means ten to a storey, and one wall piece holds up a raised
        // floor with nothing left over.
        assertTrue(bounds.getYExtent() * 2f >= TILE - 0.01f,
                "a wall should stand at least as tall as a tile is wide, and this one is "
                        + bounds.getYExtent() * 2f);
    }

    /**
     * A kit need not ship a corner post, and this one does not.
     *
     * <p>Its corner piece is a length of wall bent round a right angle rather than
     * the quarter-tile plug the notch wants, and a bent wall dropped into the
     * notch stands across both of the walls that meet there. Naming no corner
     * leaves the notches open instead -- a small gap at the outside of a bend,
     * which is nothing anyone walks through.
     */
    @Test
    void aKitMayShipNoCornerPost() {
        var art = uz.duke.dungeon.content.DungeonSettings.load().tiles();

        assertNotNull(art.floor(), "the shipped file should still name a kit");
        assertNotNull(art.wall(), "and walls to go round it");
    }

    /**
     * The texture the models ask for is shipped under the name they ask for.
     *
     * <p>The kit's own download has the atlas under two different names in two
     * different folders, and the models reference neither of the obvious ones. A
     * model whose texture is missing still loads — it just renders untextured, so
     * nothing fails until someone looks at it.
     */
    @Test
    void everyTileIsTexturedRatherThanLoadingBlank() {
        for (var tile : new String[] {"floor", "wall", "stairs"}) {
            var model = assets().loadModel(TILES + tile + ".gltf");

            assertTrue(hasTexture(model), tile + " loaded without its texture");
        }
    }

    /**
     * The paths the settings file names really are the files that shipped.
     *
     * <p>The link nobody checks: the models load, the file names some models, and
     * nothing says those are the same models. A typo here loses the floor and
     * leaves a dungeon of empty air, at run time, with no error.
     */
    @Test
    void everyTileTheSettingsFileNamesIsThere() {
        var art = uz.duke.dungeon.content.DungeonSettings.load().tiles();

        assertNotNull(art.floor(), "the shipped file should name a kit");
        for (var path : new String[] {art.floor(), art.wall(), art.corner(), art.stairs()}) {
            if (path == null) {
                continue; // a kit is allowed to name no corner post and no stair
            }
            assertNotNull(assets().loadModel(path), path + " is named but not shipped");
        }
    }

    /** And the size in the file is the size the models were actually authored at. */
    @Test
    void theTileSizeInTheFileMatchesTheModels() {
        var art = uz.duke.dungeon.content.DungeonSettings.load().tiles();
        var bounds = boundsOf(assets().loadModel(art.floor()));

        assertEquals(art.tileSize() / 2f, bounds.getXExtent(), 0.01f,
                "the file says " + art.tileSize() + " but the tile is "
                        + bounds.getXExtent() * 2f + " across");
    }

    /**
     * The skill icons the settings file names are shipped where it says.
     *
     * <p>The one asset group with nothing else watching it. An icon goes from the
     * file, through the status line, to a panel that falls back to nothing when
     * the file will not load — so a moved or misspelt icon is an empty square in
     * the corner of the bar and not one word anywhere. The folder and the file
     * name are joined in the settings, so this asks the settings rather than
     * guessing at a path.
     */
    @Test
    void everySkillIconTheSettingsFileNamesIsThere() {
        var settings = uz.duke.dungeon.content.DungeonSettings.load();
        int checked = 0;

        for (var skill : settings.skillsFor("Hero")) {
            var path = settings.hudIcon(skill.icon());
            if (path.isBlank()) {
                continue; // a skill drawn with a word rather than a picture
            }
            assertNotNull(DungeonTilesTest.class.getClassLoader().getResource(path),
                    skill.key() + " asks for " + path + ", which is not shipped");
            checked++;
        }
        assertTrue(checked >= 4, "the hero has four skills with pictures, found " + checked);
    }

    // ---- the themed kits ----

    /**
     * Every piece of every theme is on the classpath and really loads.
     *
     * <p>Driven off the file rather than off a list here, so a fourth theme is
     * covered by being described. That matters more than it sounds: a theme names
     * a folder and a dozen paths, and every way of getting one wrong — a typo, a
     * file nobody copied, a loader that is not registered for the format — comes
     * out as the same thing on screen, which is a floor that is not there.
     */
    @Test
    void everyThemeShipsThePiecesItNames() {
        var themes = uz.duke.dungeon.content.DungeonSettings.load().themes();
        var assets = assets();

        for (var theme : themes.all()) {
            for (var variation : theme.tones()) {
                var tone = theme.toneWithPaths(variation);
                for (var path : new String[] {tone.floor(), tone.wall(), tone.corner()}) {
                    if (path == null) {
                        continue; // a kit is allowed to have no corner post
                    }
                    assertNotNull(assets.loadModel(path),
                            theme.name() + "/" + variation.name() + " names " + path
                                    + ", which is not shipped");
                }
            }
        }
    }

    /**
     * The stairs a theme names are shipped, and they really are stairs.
     *
     * <p>A flight of steps is the one piece whose meaning is a direction, and the
     * client works out both its size and which way it climbs by measuring the
     * model. That only works on a model that has a rise to measure: something flat
     * loaded under the name of a stair gives a climb direction of nothing at all,
     * and the player gets steps pointing whichever way the fallback guessed.
     */
    @Test
    void everyStairAThemeNamesIsShippedAndClimbs() {
        var themes = uz.duke.dungeon.content.DungeonSettings.load().themes();
        var assets = assets();
        int found = 0;

        for (var theme : themes.all()) {
            var path = theme.stairsPath();
            if (path == null) {
                continue; // a kit with no stair of its own gets steps built from blocks
            }
            var stairs = assets.loadModel(path);
            assertNotNull(stairs, theme.name() + " names " + path + ", which is not shipped");
            var bounds = boundsOf(stairs);
            assertTrue(bounds.getYExtent() > 0.05f,
                    theme.name() + "'s stair is flat: " + path + " has no rise to climb");
            found++;
        }
        assertTrue(found > 0, "no theme ships a stair, so nothing here was checked");
    }

    /**
     * And the sizes in the file are the sizes the models were authored at.
     *
     * <p>These are the numbers that cannot be checked by looking, because getting
     * one wrong gives a floor that is merely <em>wrong</em> rather than missing —
     * tiles that overlap, walls at the wrong height, a gap along every seam. They
     * were measured off the models to write down, and this is what keeps them
     * measured.
     */
    @Test
    void everyThemeIsTheSizeItSaysItIs() {
        var themes = uz.duke.dungeon.content.DungeonSettings.load().themes();
        var assets = assets();

        for (var theme : themes.all()) {
            var tone = theme.toneWithPaths(theme.tones().get(0));
            var floor = boundsOf(assets.loadModel(tone.floor()));
            assertEquals(theme.tileSize() / 2f, floor.getXExtent(), 0.05f,
                    theme.name() + " says its tiles are " + theme.tileSize()
                            + " but they are " + floor.getXExtent() * 2f);
            if (tone.wall() == null) {
                continue;
            }
            var wall = boundsOf(assets.loadModel(tone.wall()));
            assertEquals(theme.wallTileSize() / 2f, wall.getXExtent(), 0.05f,
                    theme.name() + " says its walls are " + theme.wallTileSize()
                            + " wide but they are " + wall.getXExtent() * 2f);
            // Loosely, and on purpose. WallHeight is where the roof is laid, which
            // is the height of the wall proper — a kit whose walls carry a moulded
            // top edge stands a few percent taller than the line a roof belongs on.
            // What this catches is the mistake that matters: a number off by a
            // factor, which puts the roof through the floor or into the sky.
            float stands = wall.getYExtent() * 2f;
            if (theme.wallHeight() <= 0f) {
                // Zero is not a claim about the model. WallHeight is where the lid
                // over the rock is laid, and a theme whose boundary is a line of
                // trees lays it on the ground — so the stone between the rooms
                // comes out as more forest floor rather than as a roof in the air.
                continue;
            }
            assertTrue(Math.abs(stands - theme.wallHeight()) < stands * 0.15f,
                    theme.name() + " says its walls stand " + theme.wallHeight()
                            + " but they stand " + stands);
        }
    }

    /**
     * A wall lifted by what the file says stands on the floor rather than in it.
     *
     * <p>Kits disagree about where a wall's origin is and the correction is a
     * number somebody worked out once. This is that number, checked: the bottom of
     * the lifted wall should land on the ground.
     */
    @Test
    void everyThemeLiftsItsWallsOntoTheFloor() {
        var themes = uz.duke.dungeon.content.DungeonSettings.load().themes();
        var assets = assets();

        for (var theme : themes.all()) {
            var tone = theme.toneWithPaths(theme.tones().get(0));
            if (tone.wall() == null) {
                continue;
            }
            var wall = boundsOf(assets.loadModel(tone.wall()));
            float bottom = wall.getCenter().y - wall.getYExtent() + theme.wallLift();
            // Within a tenth of the module it was modelled at. Not exactly zero,
            // because a wall need not be masonry: a theme whose boundary is a line
            // of trees has roots, and a root dips below the ground it grows out of.
            assertEquals(0f, bottom, theme.wallTileSize() * 0.1f,
                    theme.name() + " stands its walls " + bottom + " off the floor");
        }
    }

    /**
     * A themed creature's model is shipped, and carries the clips the file asks it
     * to play.
     *
     * <p>A clip named in the file and missing from the model is silent: the
     * creature stands in its bind pose and nothing says why. Which is exactly the
     * kind of thing worth failing a build over.
     */
    @Test
    void everyThemedCreatureCarriesTheClipsItIsGiven() {
        var themes = uz.duke.dungeon.content.DungeonSettings.load().themes();
        var assets = assets();

        for (var theme : themes.all()) {
            for (var themed : theme.monsters()) {
                var art = theme.monsterWithPaths(themed);
                var model = assets.loadModel(art.look().model());
                assertNotNull(model, art.look().model() + " is named but not shipped");
                var clips = clipsOf(model);
                for (var wanted : new String[] {art.look().idle(), art.look().walk(),
                    art.look().attack(), art.death()}) {
                    if (wanted == null) {
                        continue;
                    }
                    assertTrue(clips.contains(wanted),
                            theme.name() + "/" + art.template() + " asks for " + wanted
                                    + ", and the model carries " + clips);
                }
            }
        }
    }

    /** Every clip name in a model, wherever the loader hung the composer. */
    private static java.util.Set<String> clipsOf(Spatial model) {
        var composer = model.getControl(com.jme3.anim.AnimComposer.class);
        if (composer != null) {
            return composer.getAnimClipsNames();
        }
        if (model instanceof Node node) {
            for (var child : node.getChildren()) {
                var found = clipsOf(child);
                if (!found.isEmpty()) {
                    return found;
                }
            }
        }
        return java.util.Set.of();
    }

    private static boolean hasTexture(Spatial model) {
        if (model instanceof Geometry geometry) {
            var material = geometry.getMaterial();
            return material != null && material.getParams().stream()
                    .anyMatch(param -> param.getValue() instanceof com.jme3.texture.Texture);
        }
        if (model instanceof Node node) {
            return node.getChildren().stream().anyMatch(DungeonTilesTest::hasTexture);
        }
        return false;
    }
}
