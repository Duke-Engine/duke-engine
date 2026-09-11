package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.game.view.UnitView;

/**
 * Two snapshots say what happened to somebody's health, and it is subtraction.
 *
 * <p>Everything this has to get right is a case where the arithmetic is correct
 * and the answer is still wrong: a creature seen for the first time has not just
 * been healed for its whole bar, and a hero whose ceiling went up has levelled
 * rather than drunk something. Both would put a number on screen at exactly the
 * moment the player is reading a different one, and neither would ever throw.
 */
class HealthWatchTest {

    private static final int MINE = 1;
    private static final int THEIRS = 2;

    private static UnitView unit(int id, int player, float health, float max) {
        return new UnitView(id, "Skeleton", player, 10f, 20f, 0f, health, max,
                false, true, false, false, -1);
    }

    private static List<HealthWatch.Change> after(HealthWatch watch, UnitView... units) {
        return watch.since(List.of(units), MINE, 1f, List.of());
    }

    /** The same, with somebody announced dead on this frame. */
    private static List<HealthWatch.Change> afterKilling(HealthWatch watch,
            HealthWatch.Death death, UnitView... units) {
        return watch.since(List.of(units), MINE, 1f, List.of(death));
    }

    /** A creature arriving is not a creature being healed. */
    @Test
    void theFirstSightOfSomebodySaysNothing() {
        var watch = new HealthWatch();

        var changes = after(watch, unit(7, THEIRS, 60f, 60f));

        assertTrue(changes.isEmpty(),
                "a skeleton walking on with a full bar has not just been healed for 60");
    }

    /** Losing health is damage, and the number is how much. */
    @Test
    void healthGoingDownIsDamage() {
        var watch = new HealthWatch();
        after(watch, unit(7, THEIRS, 60f, 60f));

        var changes = after(watch, unit(7, THEIRS, 26f, 60f));

        assertEquals(1, changes.size());
        var hit = changes.get(0);
        assertEquals(34f, hit.amount(), 0.001f, "the number is the difference");
        assertEquals(false, hit.healed());
        assertEquals(false, hit.his(), "a skeleton is not one of his");
        assertEquals(10f, hit.x(), 0.001f, "and it belongs where the creature is now");
    }

    /** Gaining it is a heal, and the number is positive either way. */
    @Test
    void healthGoingUpIsAHeal() {
        var watch = new HealthWatch();
        after(watch, unit(1, MINE, 100f, 200f));

        var changes = after(watch, unit(1, MINE, 140f, 200f));

        assertEquals(1, changes.size());
        assertEquals(40f, changes.get(0).amount(), 0.001f, "always positive; healed says which way");
        assertTrue(changes.get(0).healed());
        assertTrue(changes.get(0).his(), "and this one is his, which is drawn differently");
    }

    /**
     * A ceiling that moved is a level, not a potion.
     *
     * <p>The hero's maximum rises when he levels and his health goes up with it.
     * That already has its own announcement, and a "+40" over his head in the same
     * instant would be the game saying two things about one moment.
     */
    @Test
    void aRisingCeilingIsNotAHeal() {
        var watch = new HealthWatch();
        after(watch, unit(1, MINE, 100f, 200f));

        var changes = after(watch, unit(1, MINE, 140f, 240f));

        assertTrue(changes.isEmpty(), "he levelled; nobody healed him");
    }

    /** A trickle is not news. */
    @Test
    void somethingTooSmallToBeWorthANumberIsNotDrawn() {
        var watch = new HealthWatch();
        watch.since(List.of(unit(1, MINE, 100f, 200f)), MINE, 5f, List.of());

        var small = watch.since(List.of(unit(1, MINE, 102f, 200f)), MINE, 5f, List.of());
        var enough = watch.since(List.of(unit(1, MINE, 110f, 200f)), MINE, 5f, List.of());

        assertTrue(small.isEmpty(), "two points of regeneration is not worth a number");
        assertEquals(8f, enough.get(0).amount(), 0.001f, "eight is");
    }

    /** Several at once are several numbers. */
    @Test
    void everybodyHurtInOneFrameGetsHisOwnNumber() {
        var watch = new HealthWatch();
        after(watch, unit(7, THEIRS, 60f, 60f), unit(8, THEIRS, 60f, 60f),
                unit(1, MINE, 200f, 200f));

        var changes = after(watch, unit(7, THEIRS, 40f, 60f), unit(8, THEIRS, 30f, 60f),
                unit(1, MINE, 180f, 200f));

        assertEquals(3, changes.size(), "a blast that catches three leaves three numbers");
    }

    /**
     * A creature that leaves is forgotten, so the same id on a later floor is a
     * first sighting rather than a resurrection.
     */
    @Test
    void somebodyWhoHasGoneIsForgotten() {
        var watch = new HealthWatch();
        after(watch, unit(7, THEIRS, 60f, 60f));
        after(watch); // it died and left the world

        var changes = after(watch, unit(7, THEIRS, 12f, 60f));

        assertTrue(changes.isEmpty(), "that is a different creature with a recycled number");
    }

    /** And a new world forgets everyone at once. */
    @Test
    void aNewWorldForgetsEveryone() {
        var watch = new HealthWatch();
        after(watch, unit(7, THEIRS, 60f, 60f));

        watch.forget();
        var changes = after(watch, unit(7, THEIRS, 12f, 60f));

        assertTrue(changes.isEmpty());
    }

    /**
     * The finishing blow gets its number, and it is the one the player most wants.
     *
     * <p>The case subtraction alone cannot see, and it was missing entirely. A
     * creature that dies is taken out of the world on the frame the blow lands, so
     * there is never a snapshot showing it with less health -- it is simply not
     * there, which from here looks exactly like one that walked into the dark. So
     * the world says outright who was killed, and what it had left is what the
     * blow took.
     */
    @Test
    void theBlowThatKillsCountsForWhatItTook() {
        var watch = new HealthWatch();
        after(watch, unit(7, THEIRS, 12f, 60f));

        // Dead, and gone from the world on the same frame.
        var changes = afterKilling(watch, new HealthWatch.Death(7, 40f, 50f, THEIRS));

        assertEquals(1, changes.size(), "the last blow is a blow");
        assertEquals(12f, changes.get(0).amount(), 0.001f, "worth what it had left");
        assertEquals(false, changes.get(0).healed());
        assertEquals(40f, changes.get(0).x(), 0.001f, "where it fell, which the world said");
        assertEquals(50f, changes.get(0).y(), 0.001f);
    }

    /**
     * And walking into the dark is not being killed.
     *
     * <p>The two look identical from here -- a creature that was in the list and
     * is not -- which is exactly why the death has to be announced rather than
     * inferred. Fog would otherwise hand out a number equal to a full health bar
     * every time something stepped out of sight.
     */
    @Test
    void somebodyWhoMerelyLeftIsNotKilled() {
        var watch = new HealthWatch();
        after(watch, unit(7, THEIRS, 60f, 60f));

        var changes = after(watch); // out of sight, and nothing said about it

        assertTrue(changes.isEmpty(), "it walked off; nobody hit it");
    }

    /** A death announced twice is one number, not two. */
    @Test
    void aDeathIsCountedOnce() {
        var watch = new HealthWatch();
        after(watch, unit(7, THEIRS, 12f, 60f));

        afterKilling(watch, new HealthWatch.Death(7, 0f, 0f, THEIRS));
        var again = afterKilling(watch, new HealthWatch.Death(7, 0f, 0f, THEIRS));

        assertTrue(again.isEmpty(), "it is already dead and already counted");
    }

    /** Somebody killed before anybody ever saw it leaves no number. */
    @Test
    void somebodyNeverSeenAliveLeavesNoNumber() {
        var watch = new HealthWatch();

        var changes = afterKilling(watch, new HealthWatch.Death(7, 0f, 0f, THEIRS));

        assertTrue(changes.isEmpty(), "there is no reading to say what the blow took");
    }
}
