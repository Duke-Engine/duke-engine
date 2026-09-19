package uz.duke.dungeon.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The game is tuned by editing files, not by editing Java.
 *
 * <p>What is worth holding still is not any particular number — those are meant to
 * change — but that the numbers come from the files at all, that a changed file
 * really changes the game, and that a file which would produce a broken dungeon is
 * rejected while it can still be pointed at.
 */
class DungeonSettingsTest {

    @Test
    void theShippedSettingsLoadFromTheDataFile() {
        var settings = DungeonSettings.load();

        assertTrue(settings.mapWidth() > 0 && settings.mapHeight() > 0);
        assertTrue(settings.minRooms() >= 1);
        assertTrue(settings.maxRooms() >= settings.minRooms());
        assertTrue(settings.skeletonSenseRadius() > 0);
        assertTrue(settings.respawnDelayFrames() >= 0);
    }

    /** The world is one block: the engine reads its LevelHeight, and every section inside is the dungeon's. */
    @Test
    void theWorldIsOneBlockAndItsMapsStandAtItsHeight() {
        var settings = DungeonSettings.parse("""
                World Tower
                  LevelHeight = 12
                  Generation = Layout
                    MapWidth = 70
                  End
                End
                """);
        assertEquals(new DungeonWorld("Tower", 12f), settings.world());
        assertEquals(70, settings.mapWidth(), "a section is read as its own block was");

        var game = uz.duke.dungeon.Dungeon.world(".....\n.....\n", settings).game();
        game.runHeadless(1);
        assertEquals(12f, game.getTerrain().getLevelHeight(), "the engine lays the map at the world's height");
    }

    /** The point of the whole exercise: a different file is a different game. */
    @Test
    void changingTheFileChangesTheGameWithoutRecompiling() {
        var shipped = DungeonSettings.load();
        var retuned = DungeonSettings.parse("""
                World Dungeon
                  Generation = Layout
                    MapWidth = 80
                    MapHeight = 60
                    MinRooms = 9
                    MaxRooms = 12
                    MinSkeletonsPerRoom = 1
                    MaxSkeletonsPerRoom = 2
                  End
                  Combat = Behaviour
                    SkeletonSenseRadius = 250
                    SkeletonChaseRadius = 400
                  End
                End
                """);

        assertEquals(80, retuned.mapWidth());
        assertEquals(9, retuned.minRooms());
        assertEquals(250f, retuned.skeletonSenseRadius(), 0.001f);
        assertNotEquals(shipped.minRooms(), retuned.minRooms(),
                "the test would prove nothing if it happened to match the shipped file");
    }

    /** Fields a file leaves out keep their built-in value, so a partial file still loads. */
    @Test
    void unmentionedSettingsKeepTheirDefaults() {
        var shipped = DungeonSettings.load();
        var sparse = DungeonSettings.parse("""
                World Dungeon
                  Combat = Behaviour
                    SkeletonSenseRadius = 120
                  End
                End
                """);

        assertEquals(120f, sparse.skeletonSenseRadius(), 0.001f);
        assertEquals(shipped.mapWidth(), sparse.mapWidth(),
                "a file that says nothing about the map should not change it");
    }

    /** A file that cannot make a sensible dungeon is refused where it can be blamed. */
    @Test
    void nonsenseIsRejectedAtLoadTime() {
        var tooFewRooms = assertThrows(IllegalArgumentException.class,
                () -> DungeonSettings.parse("""
                        World Dungeon
                          Generation = Layout
                            MinRooms = 6
                            MaxRooms = 2
                          End
                        End
                        """));
        assertTrue(tooFewRooms.getMessage().contains("MaxRooms"), tooFewRooms.getMessage());

        assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse("""
                World Dungeon
                  Generation = Layout
                    MapWidth = 8
                    MapHeight = 8
                    MaxRoomSize = 9
                  End
                End
                """), "rooms that cannot fit on the map should be caught");

        assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse("""
                World Dungeon
                  Combat = Behaviour
                    SkeletonSenseRadius = 200
                    SkeletonChaseRadius = 50
                  End
                End
                """), "giving up closer than you notice makes no sense");
    }

    /**
     * The hero stops inside his own reach, not at the edge of it.
     *
     * <p>{@code CloseDistance} is his: each monster carries its own further down
     * the file, and those are checked where the monsters are. It used to be
     * everyone's, back when everything in the dungeon brawled and one short
     * distance served them all — a hero who shoots needs a long one, and the two
     * cannot be the same number any more.
     *
     * <p>Inside rather than equal, because stopping at maximum range parks him on
     * the edge of it: one step by either of them, and the target is outside and he
     * stands there doing nothing. The margin is what makes the range usable.
     */
    @Test
    void theHeroStopsInsideHisOwnReach() {
        var settings = DungeonSettings.load();
        var reach = Float.parseFloat(ShippedBlock.of(settings.playedHero()).value("AttackRange"));

        assertTrue(settings.closeDistance() < reach,
                "he stops at " + settings.closeDistance() + " but reaches only " + reach);
    }

    /** The creature data really is loadable content, not a file nobody reads. */
    @Test
    void theCreatureFilesArePresentAndDescribeBothSides() {
        var creatures = Content.units();
        assertTrue(creatures.contains("Hero\n  Name = Rogue\n"));
        assertTrue(creatures.contains("Monster\n  Name = Skeleton\n"));
        assertTrue(ShippedBlock.of("Skeleton").text().contains("    ScriptModule\n      Name = SkeletonBrain\n"),
                "the game's skeletons should carry their behaviour");

        var fixture = Content.read(Content.FIXTURE_CREATURES);
        assertTrue(fixture.contains("Object\n  Name = Rogue\n"));
        assertTrue(fixture.contains("Speed = 0"),
                "the fixture's skeletons stand still, whatever the game's do");
        assertTrue(!fixture.contains("ScriptModule"),
                "the fixture exercises the engine's mechanics with nothing layered on");
    }

    /**
     * The painted edges of the hero panel come out of the file as the paths it writes.
     *
     * <p>The client end of this is held still by {@code PanelSkinTest}; this is
     * the writing end, and the two together are the whole chain.
     */
    @Test
    void thePanelsPaintedEdgesAreReadAsWritten() {
        var skin = DungeonSettings.parse("""
                World Dungeon
                  Skin = Slot
                    Texture = ui/borders/default/border/panel-border-013.png
                    Inset = 10
                    Scale = 1.1
                    Tint = 0xC9A24B
                  End
                End
                """).skin();

        assertEquals(1, skin.size());
        var slot = skin.getFirst();
        assertEquals("Slot", slot.name());
        assertEquals("ui/borders/default/border/panel-border-013.png", slot.texture());
        assertEquals(10f, slot.inset(), 0.001f);
        assertEquals(1.1f, slot.scale(), 0.001f);
        assertEquals(0xC9A24B, slot.tint());
    }

    /**
     * And the shipped file really names some, each a picture that is shipped. A wrong
     * path fails as a missing file at the far end of the game, a long way from the
     * line that caused it, so it is caught here instead.
     */
    @Test
    void theShippedFilePaintsThePanel() {
        var skin = DungeonSettings.load().skin();

        assertTrue(skin.size() >= 5, "the panel has six parts and most should be painted");
        for (var piece : skin) {
            assertNotNull(DungeonSettingsTest.class.getClassLoader().getResource(piece.texture()),
                    piece.name() + " names a picture that is not shipped: " + piece.texture());
            assertTrue(piece.scale() > 0f, piece.name() + " drawn at no size at all");
        }
    }

    /**
     * Every pointer names a picture that is there, with its tip inside it.
     *
     * <p>Two mistakes that cost nothing at compile time and everything at run
     * time. A misspelt file is not an error -- the client keeps the pointer it has
     * rather than lose the mouse -- so it shows up as "the cursor sometimes does
     * not change", which nobody reports precisely and nobody finds quickly. And a
     * tip named outside its own picture is pulled back to the edge, so it cannot
     * crash; it can only be quietly wrong, and a click landing away from the arrow
     * is the most irritating kind of wrong there is.
     *
     * <p>The size is read out of the file rather than assumed, because a pack is
     * swapped by editing five lines and the next one will not be 32 pixels.
     */
    @Test
    void everyPointerNamesAPictureWithItsTipInsideIt() {
        var pointers = DungeonSettings.load().cursors();
        assertTrue(pointers.size() >= 5, "the client knows five situations: " + pointers.size());

        for (var pointer : pointers) {
            byte[] png;
            try (var file = Content.class.getResourceAsStream("/" + pointer.image())) {
                assertNotNull(file, pointer.name() + " names a picture that is not there: "
                        + pointer.image());
                png = file.readAllBytes();
            } catch (java.io.IOException e) {
                throw new AssertionError(pointer.image(), e);
            }
            int width = size(png, 16);
            int height = size(png, 20);
            assertTrue(pointer.hotX() >= 0 && pointer.hotX() < width,
                    pointer.name() + "'s tip is " + pointer.hotX() + " across a picture "
                            + width + " wide");
            assertTrue(pointer.hotY() >= 0 && pointer.hotY() < height,
                    pointer.name() + "'s tip is " + pointer.hotY() + " down a picture "
                            + height + " tall");
        }
    }

    /** A PNG says how big it is in four big-endian bytes of its header. */
    private static int size(byte[] png, int at) {
        return ((png[at] & 0xFF) << 24) | ((png[at + 1] & 0xFF) << 16)
                | ((png[at + 2] & 0xFF) << 8) | (png[at + 3] & 0xFF);
    }

    /** And the five the client draws are all painted. */
    @Test
    void everySituationTheClientKnowsIsPainted() {
        var named = DungeonSettings.load().cursors().stream()
                .map(DungeonSettings.CursorLook::name).toList();

        for (var situation : java.util.List.of("Point", "Friend", "Attack", "Aim", "Deny")) {
            assertTrue(named.contains(situation),
                    situation + " is a situation the client draws and the file does not paint: "
                            + named);
        }
    }

    @Test
    void aMissingFileSaysWhichOne() {
        var missing = assertThrows(IllegalStateException.class, () -> Content.read("nope.ini"));
        assertTrue(missing.getMessage().contains("nope.ini"), missing.getMessage());
    }

    /**
     * The shipped file really does set the order mark, rather than leaving the
     * client's own numbers in place.
     *
     * <p>A block whose name is misspelt is not an error anywhere: the file parses,
     * the client draws its default, and the whole point of putting the numbers in
     * the file — that they can be re-tuned by eye — is quietly gone. So the test
     * is that the file disagrees with the default, which is the one thing a
     * misspelt block cannot do.
     */
    @Test
    void theShippedFileSetsTheOrderMarkItself() {
        var settings = DungeonSettings.load();
        var shipped = new uz.duke.client3d.OrderMark(
                settings.markStartRadius(), settings.markEndRadius(), settings.markSeconds(),
                settings.markSize(), settings.markWidth(), settings.markHeight(),
                settings.markEasePower(), settings.markFadeFrom(), settings.markSpinDegrees(),
                settings.markBrightness(), settings.markRingRadius(), settings.markBlinks(),
                settings.markMoveColour(), settings.markAttackColour());

        assertTrue(shipped.startRadius() > shipped.endRadius(),
                "the arrowheads have to close on the click, not open away from it");
        assertTrue(shipped.seconds() > 0.2f && shipped.seconds() < 0.7f,
                "an acknowledgement, not an animation: " + shipped.seconds() + "s");
        assertTrue(shipped.easePower() > 1f, "a straight line reads as machinery");
        assertTrue(shipped.fadeFrom() > 0.3f,
                "it should travel at full strength and go out at the end");
        assertNotEquals(shipped.moveColour(), shipped.attackColour(),
                "walking and killing are not the same order");
        assertTrue(shipped.blinks() >= 1,
                "the ring on an attacked creature has to flash at least once");
        assertTrue(shipped.ringRadius() > 0f, "and be some size");
    }

    /**
     * Every skill in the shipped file can have its reach drawn.
     *
     * <p>The indicator's numbers are the skill's own — see {@code Main.rangeOf} —
     * so the way this goes wrong is a skill whose relevant figure was never filled
     * in: a strike with no Range, a blast with no Radius. Nothing refuses such a
     * file; the player simply gets a ring of nothing and no way to tell that from
     * a skill that reaches nowhere.
     */
    @Test
    void everySkillCarriesTheFigureItsRingIsDrawnFrom() {
        for (var skill : DungeonSettings.load().skills()) {
            float reach = switch (skill.effect()) {
                case STRIKE, AREA_AT_SPOT, SKILLSHOT, HEAL -> skill.range();
                case DASH, BLINK -> skill.distance();
                case METEOR -> skill.range();
                case AREA_DAMAGE, SUMMON -> skill.radius();
                case EMPOWER, GUARD -> 1f; // his own width; the look says how wide
            };
            assertTrue(reach > 0f, skill.heroTemplate() + "'s " + skill.key() + " is a "
                    + skill.effect() + " and has nothing to draw a ring from");
        }
    }

    /**
     * A shot down a lane says how WIDE it is, separately from how wide it bursts.
     *
     * <p>Two different numbers that were one, and being one made the picture lie.
     * The lane is drawn at the width of the thing in flight — what a player has
     * to step out of — and a fireball's burst is five times that, so drawing the
     * lane at the burst claimed the shot would sweep a band the width of a room.
     *
     * <p>The way this goes wrong now is silence: a skillshot with no
     * {@code HitWidth} is drawn as a lane of no width at all, which is a skill
     * whose indicator is invisible and looks exactly like one that is broken.
     */
    @Test
    void everyShotDownALaneSaysHowWideItIs() {
        for (var skill : DungeonSettings.load().skills()) {
            if (skill.effect() != uz.duke.dungeon.skill.SkillEffect.SKILLSHOT) {
                continue;
            }
            assertTrue(skill.hitWidth() > 0f, skill.heroTemplate() + "'s " + skill.key()
                    + " is a shot with no width, so its lane is drawn as nothing");
            assertTrue(skill.hitWidth() < skill.radius(), skill.heroTemplate() + "'s "
                    + skill.key() + " is as wide as it bursts, which is the picture that"
                    + " was wrong before there were two numbers");
        }
    }

    /**
     * The colour of aiming is COLD, and it is one number.
     *
     * <p>The armed slot on the bar and the rings on the floor are drawn in the
     * same colour, off the same line of the file — the panel is handed the same
     * {@code RangeLook} the rings use, so there is nothing here for two places to
     * disagree about. What this pins is the choice itself: that it is cold.
     *
     * <p>Gold was the obvious pick and the wrong one. Gold is the bar's own
     * colour — headings, rims, pips — so an armed slot drawn in it says "slightly
     * more gold than usual", which in a fight nobody notices. Everything in this
     * dungeon is stone, torch and gold, all of them warm; a cold colour has
     * nothing to blend into. The way this goes wrong is somebody tidying it back
     * towards the palette.
     */
    @Test
    void theColourOfAimingIsCold() {
        var settings = DungeonSettings.load();

        for (int colour : new int[] {settings.ringAllowColour(), settings.ringAreaColour()}) {
            int red = (colour >> 16) & 0xFF;
            int blue = colour & 0xFF;
            int green = (colour >> 8) & 0xFF;
            assertTrue(blue > red && green > red,
                    "0x" + Integer.toHexString(colour) + " is a warm colour, and warm is what"
                            + " the whole dungeon already is -- an armed slot drawn in it"
                            + " reads as more of the same");
        }
    }

    /** And the one that means "you cannot" is not, so it cannot be mistaken for it. */
    @Test
    void theColourOfRefusalIsNot() {
        var settings = DungeonSettings.load();
        int deny = settings.ringDenyColour();

        assertTrue(((deny >> 16) & 0xFF) > (deny & 0xFF),
                "refusal is drawn in the same family as permission, which is the one"
                        + " confusion a targeting colour must never allow");
    }

    /** And the file sets the ring's look itself rather than leaving the client's. */
    @Test
    void theShippedFileSetsTheSkillRingItself() {
        var settings = DungeonSettings.load();

        assertTrue(settings.ringSegments() >= 24, "a circle of twelve straight bits is a clock");
        assertTrue(settings.ringSelfRadius() > 0f, "a self-only skill still needs a ring");
        assertTrue(settings.ringSelfRadius() < 10f,
                "and it says 'only me' by being his size, not an area: "
                        + settings.ringSelfRadius());
        assertTrue(settings.ringFillAlpha() < settings.ringEdgeAlpha(),
                "the wash inside should be fainter than the ring itself");
        assertNotEquals(settings.ringAllowColour(), settings.ringDenyColour(),
                "yes and no are the two answers the ring gives");
    }
}
