package uz.duke.dungeon.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import uz.duke.dungeon.content.DungeonSettings;

/**
 * Who fills a floor, and where the way down is.
 *
 * <p>The kinds are drawn from the data file, so these ask about the drawing —
 * that it is the same for a seed, that depth changes who can appear, and that the
 * boss is somewhere the player can actually get to and recognise as the end.
 */
class MonsterPlacementTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    @Test
    void aSeedDrawsTheSameMonstersInTheSamePlaces() {
        var a = DungeonGenerator.generate(99L, SETTINGS, 4);
        var b = DungeonGenerator.generate(99L, SETTINGS, 4);

        assertEquals(a.monsters(), b.monsters());
        assertEquals(a.boss(), b.boss());
        assertEquals(a.bossRoom(), b.bossRoom());
    }

    /** Every monster placed is a kind the file describes — never an invented name. */
    @Test
    void everyMonsterIsAKindTheFileNames() {
        var known = new HashSet<String>();
        SETTINGS.monsters().forEach(kind -> known.add(kind.name()));

        for (long seed = 0; seed <= 60; seed++) {
            var floor = DungeonGenerator.generate(seed, SETTINGS, 5);
            for (var monster : floor.monsters()) {
                assertTrue(known.contains(monster.kind()),
                        "seed " + seed + " placed an unknown kind: " + monster.kind());
            }
            assertEquals(SETTINGS.bossKindAt(5), floor.boss().kind(),
                    "the floor should hold the boss the file names for its depth");
        }
    }

    /**
     * The shallow floors hold back the nastier kinds. Meeting everything the game
     * has on the first floor would leave depth with nothing to reveal.
     */
    @Test
    void theDeeperKindsOnlyAppearDeeper() {
        var shallow = new HashSet<String>();
        var deep = new HashSet<String>();
        for (long seed = 0; seed <= 60; seed++) {
            DungeonGenerator.generate(seed, SETTINGS, 1).monsters()
                    .forEach(m -> shallow.add(m.kind()));
            DungeonGenerator.generate(seed, SETTINGS, 9).monsters()
                    .forEach(m -> deep.add(m.kind()));
        }

        for (var kind : SETTINGS.monsters()) {
            if (kind.weight() > 0 && kind.minDepth() > 1) {
                assertFalse(shallow.contains(kind.name()),
                        kind.name() + " should not appear on the first floor");
            }
        }
        assertTrue(deep.size() > shallow.size(), "deeper floors should field more kinds");
    }

    /** More of them, the deeper you go — by the factor the file states. */
    @Test
    void deeperFloorsAreMoreCrowded() {
        int shallow = 0;
        int deep = 0;
        for (long seed = 0; seed <= 60; seed++) {
            shallow += DungeonGenerator.generate(seed, SETTINGS, 1).monsters().size();
            deep += DungeonGenerator.generate(seed, SETTINGS, 6).monsters().size();
        }

        assertTrue(deep > shallow,
                "depth 6 should be busier than depth 1, got " + deep + " against " + shallow);
    }

    /**
     * The boss stands at the end of the longest chain of corridors, and stands
     * alone: its room is the fight that gates the next floor, not somewhere the
     * player wanders into mid-brawl.
     */
    @Test
    void theBossWaitsAloneInTheFurthestRoom() {
        for (long seed = 0; seed <= 60; seed++) {
            var floor = DungeonGenerator.generate(seed, SETTINGS, 2);

            assertTrue(floor.bossRoom() > 0, "seed " + seed + ": never the room the hero starts in");

            var room = floor.rooms().get(floor.bossRoom());
            assertEquals(room.centerCellX() * 10 + 5, floor.boss().at().x(), 0.01f);
            assertEquals(room.centerCellY() * 10 + 5, floor.boss().at().y(), 0.01f);

            for (var monster : floor.monsters()) {
                assertFalse(inRoom(monster.at(), room),
                        "seed " + seed + ": something else is loitering in the boss room");
            }
        }
    }

    private static boolean inRoom(GeneratedDungeon.Placement at, GeneratedDungeon.Room room) {
        int cx = (int) Math.floor(at.x() / 10f);
        int cy = (int) Math.floor(at.y() / 10f);
        return cx >= room.x() && cx < room.x() + room.w()
                && cy >= room.y() && cy < room.y() + room.h();
    }

    /**
     * Retuning the file retunes who lives down there, with nothing recompiled — and
     * naming one kind edits that kind rather than deleting every other.
     *
     * <p>That distinction matters more than it sounds. Replacing the whole roster
     * reads identically in the file and fails a long way from the edit: a creature
     * definition asks for a behaviour nobody registered, because its kind quietly
     * stopped existing.
     */
    @Test
    void changingTheFileChangesWhoAppears() {
        var noRunners = DungeonSettings.parse("""
                DungeonMonster Runner
                  MinDepth = 99
                End
                """);

        boolean anyRunner = false;
        boolean anyoneElse = false;
        for (long seed = 0; seed <= 40; seed++) {
            for (var monster : DungeonGenerator.generate(seed, noRunners, 1).monsters()) {
                anyRunner |= monster.kind().equals("Runner");
                anyoneElse |= !monster.kind().equals("Runner");
            }
        }

        assertFalse(anyRunner, "the file pushed runners out of reach of the first floor");
        assertTrue(anyoneElse, "and left every other kind exactly where it was");
    }

    /** An override changes the kind it names and nothing else about it. */
    @Test
    void namingOneKindLeavesTheOthersStanding() {
        var tweaked = DungeonSettings.parse("""
                DungeonMonster Brute
                  SenseRadius = 500
                End
                """);

        assertEquals(SETTINGS.monsters().size(), tweaked.monsters().size(),
                "the roster should be the same size");
        assertEquals(500f, tweaked.monster("Brute").senseRadius(), 0.01f, "the named one changed");
        assertEquals(SETTINGS.monster("Runner").senseRadius(),
                tweaked.monster("Runner").senseRadius(), 0.01f, "and the rest did not");
    }
}
