package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.content.ShippedBlock;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.rts.message.GameMessage;

/**
 * What the player is allowed to see, and the promise that it changes nothing.
 *
 * <p>Two halves meet here. Hiding the <em>ground</em> until the hero walks it is
 * the client's own layer ({@code Discovery}, tested where it lives). Hiding the
 * <em>creatures</em> standing on it is the engine's fog, which the game only has
 * to configure — and this is where that configuration is held still, because it is
 * a number in his template and nothing stops someone raising it back to
 * where it was, when the dungeon stops being dark.
 *
 * <p>The last test is the important one. Fog decides what a player is shown; it
 * must never decide what happens. Two runs of the same seed that differ only in
 * how far the hero can see have to come out bit-identical, or sight has quietly
 * become a game rule.
 */
class DungeonFogTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** A big empty room, so distances are the test's own. */
    private static String arena() {
        int width = 40;
        int height = 30;
        var text = new StringBuilder();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean edge = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                text.append(edge ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    /** The shipped creature file with the hero's sight changed to {@code range}. */
    private static String creaturesSeeing(int range) {
        var rogue = ShippedBlock.of("Rogue");
        return Content.units().replace(rogue.text(), rogue.with("VisionRange", range).text());
    }

    /** A hero and one skeleton, a chosen distance apart, with a chosen sight. */
    private static DukeGame fight(float skeletonX, int visionRange) {
        return fight(skeletonX, 150f, visionRange);
    }

    private static DukeGame fight(float skeletonX, float skeletonY, int visionRange) {
        var world = Dungeon.world(arena(), SETTINGS, creaturesSeeing(visionRange));
        var game = world.game();
        game.spawn("Rogue", world.hero(), 60f, 150f);
        game.spawn("Skeleton", world.dungeon(), skeletonX, skeletonY);
        game.runHeadless(1);
        return game;
    }

    private static boolean seesASkeleton(DukeGame game) {
        return game.getSnapshot().units().stream()
                .anyMatch(unit -> unit.templateName().equals("Skeleton"));
    }

    /** Editing the file really does change what he can see — the seam is real. */
    @Test
    void theHeroSightIsTheNumberInTheFile() {
        assertTrue(creaturesSeeing(80).contains("VisionRange = 80"));
        assertFalse(creaturesSeeing(80).contains("VisionRange = 600"));
        // Only his: a monster's own sight is its own block and must be untouched.
        assertTrue(creaturesSeeing(80).contains("VisionRange = 58"),
                "the skeleton's sight should not have been rewritten too");
    }

    /** Standing next to him, it is on screen. */
    @Test
    void aSkeletonInSightIsDrawn() {
        assertTrue(seesASkeleton(fight(100f, 80)),
                "forty units away and within sight, it should be on screen");
    }

    /**
     * Out of sight is out of the world entirely — not a last-known mark left
     * behind. The whole tension of a dungeon is not knowing where it went, and a
     * ghost on the floor answers the question the dark is supposed to ask.
     */
    @Test
    void aSkeletonOutOfSightIsNotDrawnAtAll() {
        var game = fight(300f, 80);

        assertFalse(seesASkeleton(game), "240 units away, well beyond sight");
        assertEquals(1, game.getSnapshot().units().size(), "only the hero is on screen");
    }

    /**
     * Ground he has walked keeps its walls but not its monsters. The engine's fog
     * is live sight only — it has no memory and must not grow one, because
     * remembering where a skeleton was is exactly the ghost above.
     */
    @Test
    void groundHeHasLeftGivesNothingAway() {
        var game = fight(300f, 80);
        var hero = game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals("Rogue"))
                .findFirst().orElseThrow();
        assertFalse(seesASkeleton(game), "240 away to begin with, and out of sight");

        // Carried rather than walked, and only for a frame at a time. Walking him
        // over would have him shoot it on the way and chase it about afterwards,
        // and then this would be a test about combat: what it asks is only whether
        // ground he has been on gives away what is standing on it.
        hero.setPosition(new Coord3D(280f, 150f, 0f));
        game.runHeadless(1);
        assertTrue(seesASkeleton(game), "standing next to it, he should see it");

        hero.setPosition(new Coord3D(60f, 150f, 0f));
        game.runHeadless(1);

        assertTrue(game.getLogic().getObjects().stream()
                        .anyMatch(object -> object.getTemplate().name().equals("Skeleton")
                                && !object.isEffectivelyDead()),
                "it is still down there — otherwise this proves nothing about fog");
        assertFalse(seesASkeleton(game),
                "he has been there, so the room is on his map — but not what is in it");
    }

    /**
     * Sight is a matter of drawing, never of playing.
     *
     * <p>Same seed, same everything, two very different views of it: one hero who
     * can see the whole floor and one who can barely see his own room. If those
     * two runs came out differently, fog would have become a rule of the game, and
     * every argument that the simulation is reproducible would be worth nothing.
     */
    @Test
    void howFarHeCanSeeChangesNothingThatHappens() {
        assertEquals(playedOut(600), playedOut(80),
                "the run must not depend on how much of it the player was shown");
        assertNotEquals("", playedOut(80), "and something must actually have happened");
    }

    /** A fixed run of the same world, reduced to what the simulation ended up as. */
    private static String playedOut(int visionRange) {
        var world = Dungeon.world(arena(), SETTINGS, creaturesSeeing(visionRange));
        var game = world.game();
        game.spawn("Rogue", world.hero(), 60f, 150f);
        for (int i = 0; i < 6; i++) {
            game.spawn("Skeleton", world.dungeon(), 120f + i * 40f, 130f + (i % 3) * 30f);
        }
        game.runHeadless(1);

        var signature = new StringBuilder();
        for (int step = 0; step < 12; step++) {
            game.runHeadless(50);
            // Read the view every step, exactly as the client does. Looking at the
            // game must not disturb it either.
            game.getSnapshot();
            signature.append(game.getLogic().getObjectCount()).append(':')
                    .append(game.getLogic().checksum()).append('|');
        }
        return signature.toString();
    }
}
