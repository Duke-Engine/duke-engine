package uz.duke.client3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import uz.duke.game.view.UnitView;

/**
 * What changed about anyone's health since the last time anybody looked.
 *
 * <p>The whole of where a damage number comes from. A snapshot says what is; two
 * snapshots say what happened, and for health that is not an inference — it is
 * subtraction, and it is exact.
 *
 * <p><b>Why not an event.</b> The rule in this codebase is that a client should
 * not have to work out what happened, and it is a good rule; this is the case it
 * does not cover. Health moves from half a dozen places, and the game owns only
 * some of them: a monster's swing lands inside the engine's own weapon, and
 * lifesteal lands inside an arrow. A game that posted an event per source would
 * be silent for exactly the blows the player is being hit by, and would have to
 * be added to again every time anything new could hurt anybody. Subtraction is
 * complete by construction.
 *
 * <p>What it costs is attribution — this knows how much and to whom, never by
 * whom — and a fight where two things land on the same creature in one frame is
 * read as one larger number. Both are the right trade for a number that is on
 * screen for under a second.
 *
 * <p>Pure, and stateful only in the sense that it remembers the last reading. No
 * jME, no clock: what happened is decided from two lists of numbers.
 */
final class HealthWatch {

    /**
     * One creature's health moving.
     *
     * @param amount how much, always positive — {@code healed} says which way
     * @param his    whether the creature is the watching player's own, which is
     *               the difference between a number he is dealing and one he is
     *               taking
     */
    record Change(int unitId, float x, float y, float amount, boolean healed, boolean his) {
    }

    /** What each creature had when last seen, and what its ceiling was. */
    private final Map<Integer, float[]> lastSeen = new HashMap<>();

    /**
     * Everything that has moved since the last call.
     *
     * <p>Three things deliberately produce nothing:
     *
     * <ul>
     *   <li><b>A creature seen for the first time.</b> Arriving with 60 health is
     *       not being healed for 60 — and every creature on a new floor would
     *       otherwise announce itself.
     *   <li><b>A creature whose maximum moved.</b> The hero's ceiling rises when
     *       he levels and his health goes up with it; that is a level, not a
     *       potion, and it has its own announcement already.
     *   <li><b>A change too small to be worth a number.</b> Regeneration a point
     *       at a time is not news, and a stream of "+1" would never stop.
     * </ul>
     *
     * <p>A creature that has left the world is forgotten, so its id coming back on
     * a later floor is a first sighting rather than a resurrection.
     */
    List<Change> since(List<UnitView> units, int localPlayer, float leastWorth) {
        var changes = new ArrayList<Change>();
        var stillHere = new HashMap<Integer, float[]>(units.size());
        for (var unit : units) {
            var reading = new float[] {unit.health(), unit.maxHealth()};
            var before = lastSeen.get(unit.id());
            stillHere.put(unit.id(), reading);
            if (before == null || before[1] != reading[1]) {
                continue;
            }
            float moved = reading[0] - before[0];
            if (Math.abs(moved) < leastWorth) {
                continue;
            }
            changes.add(new Change(unit.id(), unit.x(), unit.y(), Math.abs(moved),
                    moved > 0f, unit.playerIndex() == localPlayer));
        }
        lastSeen.clear();
        lastSeen.putAll(stillHere);
        return changes;
    }

    /** Forget everyone — a new world is a new set of creatures, ids and all. */
    void forget() {
        lastSeen.clear();
    }
}
