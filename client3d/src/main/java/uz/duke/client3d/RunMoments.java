package uz.duke.client3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * The run's own moments, noticed: a level gained, the boss down, the hero arriving
 * on a floor.
 *
 * <p><b>Noticed, not announced.</b> The engine posts two events and neither is one
 * of these, and the simulation has no reason to post more: his level, the floor and
 * which creature is its boss are already on the status line. So each moment is a
 * comparison between this frame's line and the last one's, made here, and a client
 * that drew none of them would play exactly the same game.
 *
 * <p><b>Off his own field, never off the card.</b> The panel's rank is whoever is
 * selected, so a level gained while a skeleton was picked out would go unnoticed,
 * and then be noticed late, the moment he was picked again. The line's hero field is
 * his whatever the player is looking at.
 */
final class RunMoments {

    /**
     * One moment, and on whom.
     *
     * @param name   which, in the words a game gives it a look by -- see
     *               {@link Visuals#MOMENTS}
     * @param unitId who it happened to
     */
    record Moment(String name, int unitId) {
    }

    private int heroId;
    private int heroLevel;
    private int depth;
    private int bossId;

    /**
     * What happened since the last line.
     *
     * @param reading this frame's line
     * @param died    who left the world this frame, by id
     */
    List<Moment> since(UnitBarReading reading, List<Integer> died) {
        var found = new ArrayList<Moment>();
        int hero = reading.heroId();
        if (hero == 0) {
            return found; // a menu, or a game that says nothing of a hero
        }
        if (hero != heroId || reading.depth() != depth) {
            // A new floor is a new world, him included, and whatever level he
            // carries into it is not one he has just gained.
            found.add(new Moment(Visuals.ARRIVED, hero));
        } else if (reading.heroLevel() > heroLevel) {
            found.add(new Moment(Visuals.LEVEL_UP, hero));
        }
        // The line may stop naming the boss on the very frame he dies, so the last
        // one it named still counts.
        int boss = reading.bossId() != 0 ? reading.bossId() : bossId;
        if (boss != 0 && died.contains(boss)) {
            found.add(new Moment(Visuals.BOSS_DOWN, hero));
        }
        heroId = hero;
        heroLevel = reading.heroLevel();
        depth = reading.depth();
        bossId = reading.bossId();
        return found;
    }

    /** Nothing seen yet: whoever is on the next line has just arrived. */
    void forget() {
        heroId = 0;
        heroLevel = 0;
        depth = 0;
        bossId = 0;
    }

    /**
     * One moment on each man, the biggest -- the blow that fells a boss usually gives
     * him his level in the same breath, and two columns of light on one man are one
     * column too bright. Between two of a size, the first.
     *
     * @param size how big each moment is drawn
     */
    static List<Moment> biggestOnEach(List<Moment> moments, ToDoubleFunction<String> size) {
        var kept = new LinkedHashMap<Integer, Moment>();
        for (var moment : moments) {
            var held = kept.get(moment.unitId());
            if (held == null
                    || size.applyAsDouble(moment.name()) > size.applyAsDouble(held.name())) {
                kept.put(moment.unitId(), moment);
            }
        }
        return List.copyOf(kept.values());
    }
}
