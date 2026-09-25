package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.texture.Image;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A pointer is drawn the right way up, with its tip where the file says.
 *
 * <p>Two conversions, and both of them fail <em>silently</em>. jME keeps a cursor
 * bottom-up — the last row of the buffer is the top row of the picture — so
 * getting it backwards gives a pointer that is upside down, which reads as
 * somebody having chosen a strange drawing rather than as a bug. And the hot spot
 * it hands the windowing layer is measured from the bottom, so getting that
 * backwards puts the tip at the wrong end and every click lands a pointer's
 * height away from where it was aimed — which reads as the game being imprecise.
 *
 * <p>Neither shows up in a compiler, a screenshot or a crash, and both are one
 * character to get wrong. So the arithmetic is done on a picture whose every
 * pixel is known.
 */
class CursorsTest {

    /**
     * A picture with a single opaque pixel in its top-left corner, and nothing
     * else. Wherever that pixel ends up is where the top-left corner ended up.
     */
    private static Image cornerMarked(int width, int height) {
        var bytes = BufferUtils.createByteBuffer(width * height * 4);
        for (int i = 0; i < width * height * 4; i++) {
            bytes.put((byte) 0);
        }
        bytes.rewind();
        // RGBA8, row 0 column 0: opaque red.
        bytes.put(0, (byte) 0xFF).put(1, (byte) 0).put(2, (byte) 0).put(3, (byte) 0xFF);
        return new Image(Image.Format.RGBA8, width, height, bytes, ColorSpace.sRGB);
    }

    @Test
    void thePictureIsStoredBottomUp() {
        var cursor = Cursors.build(cornerMarked(8, 6), new Cursors.Look("x", 0, 0));
        var data = cursor.getImagesData();

        // jME hands the window the buffer's LAST row first, so the picture's top
        // row has to be the buffer's last.
        assertEquals(0xFFFF0000, data.get((6 - 1) * 8),
                "the picture's top-left should be in the buffer's last row");
        assertEquals(0, data.get(0), "and nothing should be in its first");
    }

    /**
     * The tip is where the file says, counted from the top.
     *
     * <p>What jME finally passes on is {@code height - yHotSpot}, and that is what
     * has to equal the row a person reading the picture would point at — so the
     * test asserts the number the window gets rather than the one stored.
     */
    @Test
    void theTipIsTurnedRoundOnTheWayIn() {
        var cursor = Cursors.build(cornerMarked(32, 32), new Cursors.Look("x", 3, 5));

        assertEquals(3, cursor.getXHotSpot(), "across is across, either way up");
        assertEquals(5, cursor.getHeight() - cursor.getYHotSpot(),
                "five down from the top is what the file said and what the window must get");
    }

    /** A tip named outside the picture is pulled back into it rather than crashing. */
    @Test
    void aTipOffThePictureIsBroughtBack() {
        var cursor = Cursors.build(cornerMarked(16, 16), new Cursors.Look("x", 900, -4));

        assertTrue(cursor.getXHotSpot() >= 0 && cursor.getXHotSpot() < 16,
                "was " + cursor.getXHotSpot());
        int fromTop = cursor.getHeight() - cursor.getYHotSpot();
        assertTrue(fromTop >= 0 && fromTop < 16, "was " + fromTop);
    }

    /** Colour and alpha survive the trip, which is what makes it a picture. */
    @Test
    void theColourIsCarriedThrough() {
        var cursor = Cursors.build(cornerMarked(4, 4), new Cursors.Look("x", 0, 0));

        assertEquals(0xFFFF0000, cursor.getImagesData().get((4 - 1) * 4),
                "opaque red in, opaque red out");
    }

    /**
     * And the right way up after going through the asset manager, which is where
     * it went wrong.
     *
     * <p>Everything above is arithmetic on an image built in memory, and all of it
     * passed while the shipped pointer stood on its head. The flip that was missed
     * happens before any of it: {@code loadTexture(String)} turns a picture upside
     * down, because a texture is sampled from the bottom and that is the right
     * default for every other picture this client loads. Two flips cancel into a
     * pointer on its head, and no test that starts after the loader can see it.
     *
     * <p>So this one starts at a file. A picture with one opaque corner is written
     * to disk, read back the way {@link Cursors} really reads one, and the corner
     * is looked for where jME will hand it to the window.
     */
    @Test
    void aPointerReadFromAFileIsTheRightWayUp() throws Exception {
        var folder = java.nio.file.Files.createTempDirectory("pointers");
        var file = folder.resolve("corner.png");
        var picture = new java.awt.image.BufferedImage(8, 6,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        picture.setRGB(0, 0, 0xFFFF0000); // opaque red, top-left and nowhere else
        javax.imageio.ImageIO.write(picture, "png", file.toFile());

        var assets = new com.jme3.asset.DesktopAssetManager(true);
        assets.registerLocator(folder.toAbsolutePath().toString(),
                com.jme3.asset.plugins.FileLocator.class);
        var cursors = new Cursors(assets, null,
                Map.of(Cursors.POINT, new Cursors.Look("corner.png", 0, 0)));

        var cursor = cursors.load(Cursors.POINT);
        assertNotNull(cursor, "the picture is there and should have loaded");
        assertEquals(0xFFFF0000, cursor.getImagesData().get((6 - 1) * 8),
                "the picture's top-left corner should be in the buffer's last row, "
                        + "which is the row jME hands the window first");
    }

    // ---- painting ----

    /**
     * A picture of the kind these packs ship: a white shape inside a black
     * outline, with an anti-aliased grey between them.
     */
    private static Image outlined() {
        var bytes = BufferUtils.createByteBuffer(3 * 1 * 4);
        bytes.rewind();
        int[] pixels = {0xFF000000, 0xFF808080, 0xFFFFFFFF}; // outline, edge, fill
        for (int pixel : pixels) {
            bytes.put((byte) ((pixel >> 16) & 0xFF)).put((byte) ((pixel >> 8) & 0xFF))
                    .put((byte) (pixel & 0xFF)).put((byte) ((pixel >>> 24) & 0xFF));
        }
        bytes.rewind();
        return new Image(Image.Format.RGBA8, 3, 1, bytes, ColorSpace.sRGB);
    }

    private static int pixelAt(com.jme3.cursors.plugins.JmeCursor cursor, int column) {
        return cursor.getImagesData().get(column);
    }

    /**
     * The tint colours the shape and leaves the outline alone.
     *
     * <p>The whole reason it is multiplied rather than painted over. White times a
     * colour is that colour; black times anything is still black. A pointer that
     * lost its keyline would be legible over stone and invisible over torchlight,
     * which is the one place a player most needs to see where he is pointing.
     */
    @Test
    void aTintColoursTheShapeAndKeepsTheOutline() {
        var green = Cursors.build(outlined(), new Cursors.Look("x", 0, 0, 0x7FBF6A));

        assertEquals(0xFF000000, pixelAt(green, 0), "the outline must stay black");
        assertEquals(0xFF7FBF6A, pixelAt(green, 2), "and the white shape takes the colour");
        // The grey along the edge comes out as a darker shade of the same colour,
        // which is what keeps the edge smooth instead of jagged.
        int edge = pixelAt(green, 1);
        assertTrue(((edge >> 16) & 0xFF) < 0x7F && ((edge >> 16) & 0xFF) > 0x30,
                "the anti-aliased edge should be a darker shade, was "
                        + Integer.toHexString(edge));
    }

    /** And what is transparent stays transparent, or the pointer grows a halo. */
    @Test
    void aTintNeverTouchesWhatIsSeeThrough() {
        var clear = Cursors.build(cornerMarked(4, 4), new Cursors.Look("x", 0, 0, 0xFF0000));

        assertEquals(0, pixelAt(clear, 1) >>> 24, "an empty pixel must stay empty");
    }

    /** White is "as drawn", so a pack that is already coloured is left alone. */
    @Test
    void whiteMeansLeaveItAsItWasDrawn() {
        var plain = Cursors.build(outlined(), new Cursors.Look("x", 0, 0));
        var white = Cursors.build(outlined(), new Cursors.Look("x", 0, 0, 0xFFFFFF));

        for (int column = 0; column < 3; column++) {
            assertEquals(pixelAt(plain, column), pixelAt(white, column));
        }
    }

    // ---- which pointer, and when ----

    /** Nothing under it but floor he could walk on. */
    private static Cursors.Over overOpenGround() {
        return new Cursors.Over(true, null, true, false, false, false);
    }

    @Test
    void overOpenGroundItIsThePlainPointer() {
        assertEquals(Cursors.POINT, Cursors.situationFor(overOpenGround()));
    }

    /**
     * And over somewhere he cannot put his feet, the same refusal a skill gets.
     *
     * <p>One drawing for one meaning: "the next click cannot do that <em>here</em>".
     * A wall refuses a walking order for the same reason it refuses a fire-trap,
     * and a player who has learnt the picture once has learnt it for both.
     */
    @Test
    void overStoneItRefuses() {
        assertEquals(Cursors.DENY, Cursors.situationFor(
                new Cursors.Over(true, null, false, false, false, false)));
    }

    @Test
    void overACreatureItSaysWhoseItIs() {
        assertEquals(Cursors.ATTACK, Cursors.situationFor(
                new Cursors.Over(true, null, false, false, true, false)));
        assertEquals(Cursors.FRIEND, Cursors.situationFor(
                new Cursors.Over(true, null, false, false, true, true)));
    }

    /**
     * Over something nothing he has selected may be fired at — a helicopter, and only tanks selected — the
     * attack would be refused, and the pointer says so the way it says any order would be.
     */
    @Test
    void overWhatNothingSelectedCanHitItRefuses() {
        assertEquals(Cursors.DENY, Cursors.situationFor(
                new Cursors.Over(true, null, true, false, true, false, false)));
        assertEquals(Cursors.ATTACK, Cursors.situationFor(
                new Cursors.Over(true, null, true, false, true, false, true)));
        assertEquals(Cursors.FRIEND, Cursors.situationFor(
                new Cursors.Over(true, null, true, false, true, true, false)),
                "his own is his own, whatever his guns can hit");
    }

    /**
     * An armed skill outranks whatever is standing there.
     *
     * <p>It is the reason the next click will not do what a click usually does, so
     * while one waits the pointer answers one question only: may it go here. A
     * pointer that went on saying "there is a monster there" would be answering a
     * question the player is not asking.
     */
    @Test
    void anArmedSkillOutranksWhatIsUnderThePointer() {
        assertEquals(Cursors.AIM, Cursors.situationFor(
                new Cursors.Over(true, Hotkeys.Aim.GROUND, true, false, true, false)));
        assertEquals(Cursors.DENY, Cursors.situationFor(
                new Cursors.Over(true, Hotkeys.Aim.GROUND, false, false, true, false)));
    }

    /**
     * ★ But a skill with nothing to point at does not touch the pointer.
     *
     * <p>Armed is not aiming, and treating the two as one word is what this is
     * here for. A skill that goes off round the man who casts it is held only so
     * its reach can be looked at before it is spent — there is nowhere to put it,
     * and the click that ends the holding casts it wherever it lands. So the
     * pointer went to "here, or not here" about a question the player was never
     * asked, and took itself off the creature he was actually looking at.
     *
     * <p>Every other aim is a question about WHERE, and every one of them still
     * takes the pointer.
     */
    @Test
    void aSkillWithNothingToPointAtLeavesThePointerAlone() {
        assertEquals(Cursors.ATTACK, Cursors.situationFor(
                new Cursors.Over(true, Hotkeys.Aim.NOW, true, false, true, false)),
                "holding a skill that goes off round him hid the monster under the cursor");
        assertEquals(Cursors.POINT, Cursors.situationFor(
                new Cursors.Over(true, Hotkeys.Aim.NOW, true, false, false, false)));

        for (var aim : Hotkeys.Aim.values()) {
            if (aim == Hotkeys.Aim.NOW) {
                continue;
            }
            assertEquals(Cursors.AIM, Cursors.situationFor(
                    new Cursors.Over(true, aim, true, false, true, false)),
                    aim + " asks the player where, so it has to take the pointer");
        }
    }

    /** The bar takes clicks and gives no orders, so it is plain. */
    @Test
    void overTheBarItIsPlainEvenWithACreatureBehindIt() {
        assertEquals(Cursors.POINT, Cursors.situationFor(
                new Cursors.Over(true, null, false, true, true, false)));
    }

    /** And a menu is on top of everything, including an armed skill. */
    @Test
    void aMenuIsOnTopOfEverything() {
        assertEquals(Cursors.POINT, Cursors.situationFor(
                new Cursors.Over(false, Hotkeys.Aim.GROUND, true, false, true, false)));
    }

    @Test
    void aGameThatNamesNoPointersHasNone() {
        assertNotNull(new Cursors(null, null, Map.of()));
        assertTrue(!new Cursors(null, null, Map.of()).any(),
                "every game but this one names none, and must keep the system arrow");
        assertTrue(new Cursors(null, null,
                Map.of(Cursors.POINT, new Cursors.Look("a.png", 0, 0))).any());
    }

    /**
     * Asking for a situation nothing was named for changes nothing.
     *
     * <p>Deliberate: flicking back to the white arrow as the pointer crosses a
     * creature would be worse than one picture serving two situations. A game may
     * paint three of the five and the other two simply keep what is there.
     */
    @Test
    void anUnnamedSituationLeavesThePointerAlone() {
        var cursors = new Cursors(null, null,
                Map.of(Cursors.POINT, new Cursors.Look("a.png", 0, 0)));

        cursors.show(Cursors.ATTACK); // would need an input manager if it did anything
        cursors.show(null);
    }
    /** Three pictures side by side, each one opaque colour: red, green and blue, two by two. */
    private static Image strip() {
        int[][] colours = {{0xFF, 0, 0}, {0, 0xFF, 0}, {0, 0, 0xFF}};
        var bytes = BufferUtils.createByteBuffer(6 * 2 * 4);
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 6; column++) {
                var colour = colours[column / 2];
                bytes.put((byte) colour[0]).put((byte) colour[1]).put((byte) colour[2]).put((byte) 0xFF);
            }
        }
        bytes.rewind();
        return new Image(Image.Format.RGBA8, 6, 2, bytes, ColorSpace.sRGB);
    }

    @Test
    void aStripOfThreeReachesTheWindowAsThreeImagesEachShownItsTime() {
        var cursor = Cursors.build(strip(),
                new Cursors.Look("move.png", 1, 1, 0xFFFFFF, 3, java.util.List.of(4, 8, 4)));

        assertEquals(3, cursor.getNumImages());
        assertEquals(2, cursor.getWidth(), "a picture is a third of the strip");
        assertEquals(0xFF00FF00, cursor.getImagesData().get(2 * 2), "the second image is the middle picture");
        var delays = cursor.getImagesDelay();
        assertEquals(java.util.List.of(67, 133, 67), java.util.List.of(delays.get(0), delays.get(1), delays.get(2)),
                "4, 8 and 4 sixtieths of a second, in the milliseconds the window counts in");
    }

    /** A tank of his selected, and what a click would do wherever the pointer is. */
    private static Cursors.Over withATank(boolean overUnit, boolean own, String order) {
        return new Cursors.Over(true, null, true, false, overUnit, own, true, true, order, null);
    }

    @Test
    void whatAClickWouldDoIsWhatThePointerSays() {
        assertEquals(Cursors.MOVE, Cursors.situationFor(withATank(false, false, null)), "open ground: he would go");
        assertEquals(java.util.List.of(Cursors.MOVE, Cursors.POINT),
                Cursors.situationsFor(withATank(false, false, null)), "the arrow where the game drew no Move");
        assertEquals(Cursors.ATTACK, Cursors.situationFor(withATank(true, false, null)), "an enemy");
        assertEquals(Cursors.FRIEND, Cursors.situationFor(withATank(true, true, null)), "the tank itself");
        assertEquals(java.util.List.of("Enter", Cursors.FRIEND),
                Cursors.situationsFor(withATank(true, true, "Enter")), "a transport it may board");
        assertEquals(Cursors.POINT, Cursors.situationFor(
                new Cursors.Over(true, null, true, false, false, false, true, false, null, null)),
                "nothing selected: nothing a click on the ground would order");
    }

    /** The game's word for a click on the ground is what the pointer shows there, Move and the arrow standing in. */
    @Test
    void theGroundsWordIsWhatThePointerSaysOverTheGround() {
        assertEquals(java.util.List.of("Steer", Cursors.MOVE, Cursors.POINT),
                Cursors.situationsFor(withATank(false, false, "Steer")));
    }

    /** A click on the ground with the game's word for it is that order, at the place; with none, no order of its own. */
    @Test
    void aClickOnTheGroundWithTheGamesWordSendsItsOrderThere() {
        var units = java.util.List.of(new uz.dukeengine.core.thing.ObjectId(7));
        var ground = new com.jme3.math.Vector3f(120f, 4f, 80f); // the scene's y is up

        var order = DukeRtsApp.groundOrder(1, units, "Steer", ground);

        assertEquals(new uz.dukeengine.rts.message.GameMessage.GameOrder(1, "Steer", units,
                new uz.dukeengine.core.math.Coord3D(120f, 80f, 4f), null, 0), order);
        assertEquals(null, DukeRtsApp.groundOrder(1, units, null, ground), "no word: the move it always was");
    }

    @Test
    void theViewScrollingShowsWhichWayOverEverythingElse() {
        assertEquals("E", Cursors.scrollDirection(1f, 0f), "against the right edge");
        assertEquals("NW", Cursors.scrollDirection(-1f, -1f));
        assertEquals(null, Cursors.scrollDirection(0f, 0f));
        var atTheRightEdge = new Cursors.Over(true, null, true, false, true, false, true, true, null, "E");
        assertEquals(java.util.List.of("Scroll-E", Cursors.ATTACK), Cursors.situationsFor(atTheRightEdge),
                "scrolling first, and what it is over where the game drew no scroll");
    }

    /** The pointer over the game's own bar, or below the world's part of the window, is over the panel. */
    @Test
    void overTheGamesBarWithSomethingSelectedItIsTheArrowAndAboveItAMove() {
        var onTheBar = new Cursors.Over(true, null, true, true, false, false, true, true, null, null);
        var aboveIt = new Cursors.Over(true, null, true, false, false, false, true, true, null, null);

        assertEquals(Cursors.POINT, Cursors.situationFor(onTheBar));
        assertEquals(Cursors.MOVE, Cursors.situationFor(aboveIt), "open ground above the bar");
        assertEquals("Scroll-S", Cursors.situationFor(new Cursors.Over(true, null, true, true, false, false, true,
                true, null, "S")), "a view being scrolled shows it first, over the bar too");
    }
}
