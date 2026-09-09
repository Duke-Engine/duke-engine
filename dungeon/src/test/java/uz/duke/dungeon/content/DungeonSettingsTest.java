package uz.duke.dungeon.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    /** The point of the whole exercise: a different file is a different game. */
    @Test
    void changingTheFileChangesTheGameWithoutRecompiling() {
        var shipped = DungeonSettings.load();
        var retuned = DungeonSettings.parse("""
                DungeonGeneration Layout
                  MapWidth = 80
                  MapHeight = 60
                  MinRooms = 9
                  MaxRooms = 12
                  MinSkeletonsPerRoom = 1
                  MaxSkeletonsPerRoom = 2
                End
                DungeonCombat Behaviour
                  SkeletonSenseRadius = 250
                  SkeletonChaseRadius = 400
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
                DungeonCombat Behaviour
                  SkeletonSenseRadius = 120
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
                        DungeonGeneration Layout
                          MinRooms = 6
                          MaxRooms = 2
                        End
                        """));
        assertTrue(tooFewRooms.getMessage().contains("MaxRooms"), tooFewRooms.getMessage());

        assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse("""
                DungeonGeneration Layout
                  MapWidth = 8
                  MapHeight = 8
                  MaxRoomSize = 9
                End
                """), "rooms that cannot fit on the map should be caught");

        assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse("""
                DungeonCombat Behaviour
                  SkeletonSenseRadius = 200
                  SkeletonChaseRadius = 50
                End
                """), "giving up closer than you notice makes no sense");
    }

    /**
     * Fighters must close to inside every weapon's reach, not merely to the edge of
     * their own.
     *
     * <p>Stopping at your own maximum range parks you at the edge of it, and if your
     * reach is longer than your opponent's you end up standing outside his — which
     * is how the skeletons came to chip away at a hero who could not see them. One
     * distance, shorter than any weapon here, keeps a fight mutual no matter what
     * the weapons reach.
     */
    @Test
    void fightersCloseToInsideEveryWeaponsReach() {
        var settings = DungeonSettings.load();
        var creatures = Content.read(Content.CREATURES);

        var ranges = java.util.regex.Pattern.compile("AttackRange\\s*=\\s*(\\d+)")
                .matcher(creatures).results()
                .map(match -> Integer.parseInt(match.group(1)))
                .toList();
        assertTrue(ranges.size() >= 2, "both creatures should carry a weapon");
        for (int reach : ranges) {
            assertTrue(settings.closeDistance() < reach,
                    "CloseDistance " + settings.closeDistance()
                            + " must be inside every weapon's reach, but one reaches only " + reach);
        }
    }

    /** The creature data really is loadable content, not a file nobody reads. */
    @Test
    void theCreatureFilesArePresentAndDescribeBothSides() {
        var creatures = Content.read(Content.CREATURES);
        assertTrue(creatures.contains("Object Hero"));
        assertTrue(creatures.contains("Object Skeleton"));
        assertTrue(creatures.contains("Script:SkeletonBrain"),
                "the game's skeletons should carry their behaviour");

        var fixture = Content.read(Content.FIXTURE_CREATURES);
        assertTrue(fixture.contains("Object Hero"));
        assertTrue(fixture.contains("Speed = 0"),
                "the fixture's skeletons stand still, whatever the game's do");
        assertTrue(!fixture.contains("Script:"),
                "the fixture exercises the engine's mechanics with nothing layered on");
    }

    @Test
    void aMissingFileSaysWhichOne() {
        var missing = assertThrows(IllegalStateException.class, () -> Content.read("nope.ini"));
        assertTrue(missing.getMessage().contains("nope.ini"), missing.getMessage());
    }
}
