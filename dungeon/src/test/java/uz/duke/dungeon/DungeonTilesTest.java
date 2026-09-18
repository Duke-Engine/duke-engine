package uz.duke.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
     * the corner of the bar and not one word anywhere. The path is the file's
     * own, so this asks the settings rather than guessing at one.
     */
    @Test
    void everySkillIconTheSettingsFileNamesIsThere() {
        var settings = uz.duke.dungeon.content.DungeonSettings.load();
        int checked = 0;

        // Every hero the file describes, not only the one it starts with. Three
        // of them have four skills each, and a picture named for a hero nobody
        // has played yet is exactly the one that would go missing unnoticed.
        for (var hero : settings.heroes()) {
            for (var skill : settings.skillsFor(hero.name())) {
                var path = skill.icon();
                if (path.isBlank()) {
                    continue; // a skill drawn with a word rather than a picture
                }
                assertNotNull(DungeonTilesTest.class.getClassLoader().getResource(path),
                        hero.name() + "'s " + skill.key() + " asks for " + path
                                + ", which is not shipped");
                checked++;
            }
        }
        assertTrue(checked >= 12, "three heroes with four skills each, found " + checked);
    }

    /**
     * The pictures on the order buttons and beside the figures are shipped too.
     *
     * <p>These used to be line drawings the client held under names the game spelt
     * out in Java, so there was nothing to ship and nothing to get wrong. Now they
     * are files like everything else, and a file is a thing that can be missing.
     */
    @Test
    void everyCommandAndFigurePictureIsThere() {
        var settings = uz.duke.dungeon.content.DungeonSettings.load();
        int checked = 0;

        var named = new java.util.ArrayList<String>();
        named.addAll(settings.hudOrderIcons());
        named.addAll(settings.hudStatIcons());
        // And every attribute's, which is named in its own block rather than the panel's.
        settings.attributeArt().forEach(attribute -> named.add(attribute.icon()));
        for (var path : named) {
            if (path.isBlank()) {
                continue; // a game that names none draws the letter instead
            }
            assertNotNull(DungeonTilesTest.class.getClassLoader().getResource(path),
                    path + " is named but not shipped");
            checked++;
        }
        assertTrue(checked >= 10, "four orders, three figures and three attributes, found "
                + checked);
    }

    /**
     * Every picture the panel draws is square, and the size its sheet was cut at.
     *
     * <p>A slot is square and a picture is stretched to fill it, so one that is
     * not comes out squashed — and there is nothing on screen to say whether the
     * drawing was made that way or the cut went wrong. The sizes are the cutter's
     * own, so this is the other end of {@code IconSheets}: it says what came out,
     * and this says what the game ships.
     */
    @Test
    void everyPictureIsSquareAndTheSizeItsSheetWasCutAt() throws java.io.IOException {
        int checked = 0;
        for (var folder : new String[][] {{"icons/skills/", "256"}, {"icons/commands/", "128"},
            {"icons/stats/", "64"}}) {
            for (var name : shipped(folder[0])) {
                var image = javax.imageio.ImageIO.read(
                        DungeonTilesTest.class.getClassLoader().getResource(folder[0] + name));
                assertNotNull(image, folder[0] + name + " is not readable as a picture");
                assertEquals(image.getWidth(), image.getHeight(),
                        folder[0] + name + " is " + image.getWidth() + "x" + image.getHeight()
                                + ", and a slot is square");
                assertEquals(Integer.parseInt(folder[1]), image.getWidth(),
                        folder[0] + name + " was cut at a different size from its sheet");
                assertTrue(image.getColorModel().hasAlpha(),
                        folder[0] + name + " carries no transparency, so it is a square"
                                + " of background sitting in the socket");
                checked++;
            }
        }
        assertTrue(checked >= 25,
                "ten skills, four orders, eight figures and three attributes, found " + checked);
    }

    /** What is actually in one of the game's icon folders. */
    private static java.util.List<String> shipped(String folder) throws java.io.IOException {
        var at = DungeonTilesTest.class.getClassLoader().getResource(folder);
        assertNotNull(at, folder + " is not on the classpath at all");
        try (var found = java.nio.file.Files.list(
                java.nio.file.Path.of(java.net.URI.create(at.toString())))) {
            return found.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".png"))
                    .sorted()
                    .toList();
        }
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
            for (var tone : theme.tones()) {
                for (var path : new String[] {tone.floor(), tone.wall(), tone.corner()}) {
                    if (path == null) {
                        continue; // a kit is allowed to have no corner post
                    }
                    assertNotNull(assets.loadModel(path),
                            theme.name() + "/" + tone.name() + " names " + path
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
            var path = theme.stairs();
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
            var tone = theme.tones().get(0);
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
            var tone = theme.tones().get(0);
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
            for (var art : theme.monsters()) {
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

    /**
     * A themed prop carries its own colours, because nothing else gives it any.
     *
     * <p>A creature is dressed from a skin the file names beside it; a kit prop is
     * not — a barrel points at the pack's atlas from inside its own glTF, and the
     * file says only which model to use. So "no texture named" has to mean
     * <em>the one it came with</em> rather than <em>none</em>, and a prop that
     * stopped carrying one would come out a plain white barrel-shaped nothing,
     * correctly lit and completely blank.
     */
    @Test
    void everyThemedPropCarriesTheColoursNobodyNamesForIt() {
        var themes = uz.duke.dungeon.content.DungeonSettings.load().themes();
        var assets = assets();

        for (var theme : themes.all()) {
            for (var art : theme.monsters()) {
                if (art.look().texture() != null) {
                    continue; // dressed from a skin of its own, like a creature
                }
                assertTrue(hasTexture(assets.loadModel(art.look().model())),
                        theme.name() + "/" + art.template() + " is drawn from "
                                + art.look().model() + ", which names no texture and is "
                                + "given none — it would render blank white");
            }
        }
    }

    /**
     * A theme is not always one picture, and the client may not assume it is.
     *
     * <p>An atlas kit gets one shared material for the whole floor, which is most
     * of what keeps six hundred tiles cheap. One skin for the whole <em>theme</em>
     * is a different claim and a false one: a forest is dirt from one pack and
     * trees from another, two atlases in one folder, and whichever piece loaded
     * first decided the picture for the lot. The trees came out wearing the floor's
     * atlas — grey lumps on white stalks.
     *
     * <p>So the skin is keyed by the texture, and this is the fact that makes that
     * necessary rather than tidy.
     */
    @Test
    void aThemeMayBeDrawnOnMoreThanOnePicture() {
        var themes = uz.duke.dungeon.content.DungeonSettings.load().themes();
        var assets = assets();
        var pictures = new java.util.HashSet<String>();

        for (var theme : themes.all()) {
            for (var tone : theme.tones()) {
                for (var path : new String[] {tone.floor(), tone.wall(), theme.stairs()}) {
                    if (path != null) {
                        pictures.add(theme.name() + " " + textureNameOf(assets.loadModel(path)));
                    }
                }
            }
        }
        assertTrue(pictures.stream().filter(named -> named.startsWith("Forest ")).count() > 1,
                "the forest is dirt from one pack and trees from another, and a client "
                        + "that skins a theme once would paint one over the other: " + pictures);
    }

    /** What a model's first texture is called, or {@code "none"}. */
    private static String textureNameOf(Spatial model) {
        if (model instanceof Geometry geometry && geometry.getMaterial() != null) {
            for (var param : geometry.getMaterial().getParams()) {
                if (param.getValue() instanceof com.jme3.texture.Texture texture
                        && texture.getKey() != null) {
                    return texture.getKey().getName();
                }
            }
        }
        if (model instanceof Node node) {
            for (var child : node.getChildren()) {
                var found = textureNameOf(child);
                if (!found.equals("none")) {
                    return found;
                }
            }
        }
        return "none";
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

    // ---- what the side of a raised block of rock is drawn with ----

    /**
     * A theme whose wall is a <em>thing</em> says what its rock is faced with, and
     * a theme whose wall is a surface does not.
     *
     * <p>Not a spelling check but a pairing. Masonry walls a two-storey block in
     * two courses and those courses <em>are</em> its sides; a tree is drawn once on
     * top, so what faces the rock has to be named or nothing draws it at all. The
     * two settings go together, and a theme that turns one on without the other is
     * the bug this was written after — trees standing in the air over the upper
     * floors.
     */
    @Test
    void everyThemeWhoseWallIsAThingSaysWhatFacesItsRock() {
        for (var theme : uz.duke.dungeon.content.DungeonSettings.load().themes().all()) {
            assertEquals(theme.standing().fillsRock(), theme.rockFace() != null,
                    theme.name() + " fills rock with a body but names no RockFace to close"
                            + " its sides (or names one it does not need)");
        }
    }

    /** And the model it names is shipped, and is shaped like a course of wall. */
    @Test
    void andWhatFacesItIsShippedAndIsShapedLikeAWall() {
        var assets = assets();
        int checked = 0;
        for (var theme : uz.duke.dungeon.content.DungeonSettings.load().themes().all()) {
            if (theme.rockFace() == null) {
                continue;
            }
            var model = assets.loadModel(theme.rockFace());
            assertNotNull(model, theme.rockFace() + " is named but not shipped");
            model.updateModelBound();
            model.updateGeometricState();
            var box = (com.jme3.bounding.BoundingBox) model.getWorldBound();
            // The client scales it so its HEIGHT fills one storey, uniformly, and
            // then how wide it comes out is the model's own business. Square is
            // what a modular wall is, and square is what lands it exactly one cell
            // wide: taller than it is wide leaves daylight between the courses,
            // wider than it is tall runs them across their neighbours.
            float tallness = box.getYExtent() / Math.max(0.001f, box.getXExtent());
            assertEquals(1f, tallness, 0.2f, theme.rockFace() + " is " + tallness
                    + " times as tall as it is wide, so a storey of it is not a cell wide");
            // And a slab rather than a block, or its own depth eats the cell behind.
            assertTrue(box.getZExtent() < box.getXExtent(),
                    theme.rockFace() + " is as deep as it is wide — that is a plinth,"
                            + " not a face");
            checked++;
        }
        assertTrue(checked > 0, "some theme should be naming one, or this test proves nothing");
    }

    /**
     * A theme whose walls stand up draws their tops in a different colour from the
     * floor.
     *
     * <p>Both are the same tile — the client lays a floor piece at the top of the
     * walls to roof the stone — facing the same way, so one sun shades them
     * identically and a player looking down a slope cannot tell which of the two he
     * is allowed to walk on. It was reported as "no depth", and no angle of light
     * can fix it: the two normals are the same normal.
     *
     * <p><b>Asked only of the themes with wall height</b>, and that is a correction
     * rather than an exemption. It was asked of all of them, the wood was given a
     * tint too, and the first player to see it asked why there was a shadow under
     * every tree. There was not — there was a tinted <em>square</em>, one per cell,
     * which is the grid that {@code WallClump}, {@code WallSpread} and
     * {@code WallVariety} exist to hide, handed straight back. In a cellar the tint
     * costs nothing because masonry is laid on cell boundaries and the grid is
     * already on show; where the lid lies at the floor's own height and a tree
     * stands on it, the tint is all you see of it.
     */
    @Test
    void everyThemeWhoseWallsStandUpTellsTheirTopsFromTheFloor() {
        int checked = 0;
        for (var theme : uz.duke.dungeon.content.DungeonSettings.load().themes().all()) {
            if (theme.wallHeight() <= 0f) {
                continue; // its lids lie on the ground; what marks them is what stands on them
            }
            assertNotEquals(0xFFFFFF, theme.capTint(),
                    theme.name() + " draws the top of its walls in exactly the floor's colours");
            checked++;
        }
        assertTrue(checked > 0, "some theme should have walls with height, or this proves nothing");
    }

    /**
     * And the dungeon's own sun is far enough off vertical to shade a wall.
     *
     * <p>Read off the file rather than off the client's fallback, which is
     * deliberately still the flat old light so that turning three constants into a
     * setting changed nobody's picture. The number that matters is the pitch, and
     * two things go wrong at once as it approaches 90: every upright face gets the
     * same share of the light, so nothing has any form, and everything flat gets
     * all of it and clips to white.
     */
    @Test
    void theSunIsFarEnoughOffVerticalToShadeAWall() {
        var settings = uz.duke.dungeon.content.DungeonSettings.load();
        double pitch = Math.toRadians(settings.sunPitch());

        assertTrue(Math.cos(pitch) > 0.6,
                "at " + settings.sunPitch() + " degrees the best-lit wall and the worst are "
                        + Math.cos(pitch) + " apart, which is not enough to read as a wall");
        // What a floor comes to: the sun's share of an upward face, plus the
        // ambient the kit's materials return. Over one and it clips to white.
        double kitAmbient = settings.sunAmbientPercent() / 100.0 * 0.55;
        assertTrue(Math.sin(pitch) + kitAmbient <= 1.0,
                "the ground comes out at " + (Math.sin(pitch) + kitAmbient) + " of full"
                        + " brightness, so it clips and stops being a surface");
    }
}
