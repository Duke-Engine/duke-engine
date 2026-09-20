package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A level, the boss down and a new floor are noticed on the line -- once each, on
 * him, and off his own field rather than whoever's card is up.
 */
class RunMomentsTest {

    private static UnitBarReading line(int depth, int boss, int hero, int level) {
        return new UnitBarReading(depth, boss, hero, level, 0, 0, 0, 100, Map.of());
    }

    private static RunMoments.Moment on(String name, int unitId) {
        return new RunMoments.Moment(name, unitId);
    }

    @Test
    void theFirstSightOfHimIsAnArrival() {
        var moments = new RunMoments();

        assertEquals(List.of(on(Visuals.ARRIVED, 7)), moments.since(line(1, 0, 7, 1), List.of()));
        assertEquals(List.of(), moments.since(line(1, 0, 7, 1), List.of()), "and only the first");
    }

    @Test
    void aLevelIsNoticedOnceEachTime() {
        var moments = new RunMoments();
        moments.since(line(1, 0, 7, 1), List.of());

        assertEquals(List.of(on(Visuals.LEVEL_UP, 7)), moments.since(line(1, 0, 7, 2), List.of()));
        assertEquals(List.of(), moments.since(line(1, 0, 7, 2), List.of()), "not again next frame");
        assertEquals(List.of(on(Visuals.LEVEL_UP, 7)), moments.since(line(1, 0, 7, 3), List.of()),
                "and the next level is noticed too");
    }

    /**
     * A new floor is a new world, him in it with a new id, and whatever level he
     * carries down the stairs is not one he has just gained.
     */
    @Test
    void aNewFloorIsAnArrivalAndNotALevel() {
        var moments = new RunMoments();
        moments.since(line(1, 0, 7, 3), List.of());

        assertEquals(List.of(on(Visuals.ARRIVED, 40)), moments.since(line(2, 0, 40, 4), List.of()));
    }

    /** The line may stop naming the boss on the very frame he dies. */
    @Test
    void theBossFallingIsNoticedEvenOnceTheLineStopsNamingHim() {
        var moments = new RunMoments();
        moments.since(line(3, 55, 7, 5), List.of());

        assertEquals(List.of(on(Visuals.BOSS_DOWN, 7)), moments.since(line(3, 0, 7, 5), List.of(55)));
    }

    @Test
    void anythingElseDyingIsNotABoss() {
        var moments = new RunMoments();
        moments.since(line(3, 55, 7, 5), List.of());

        assertEquals(List.of(), moments.since(line(3, 55, 7, 5), List.of(12, 13)));
    }

    @Test
    void withoutAHeroOnTheLineNothingHappens() {
        var moments = new RunMoments();

        assertEquals(List.of(), moments.since(UnitBarReading.NOTHING, List.of(55)));
        assertEquals(List.of(), moments.since(line(1, 55, 0, 0), List.of(55)));
    }

    /** A new run whose hero happens to have the last one's id and floor has still arrived. */
    @Test
    void afterForgettingHeHasJustArrivedAgain() {
        var moments = new RunMoments();
        moments.since(line(1, 0, 7, 4), List.of());
        moments.forget();

        assertEquals(List.of(on(Visuals.ARRIVED, 7)), moments.since(line(1, 0, 7, 1), List.of()));
    }

    /** The blow that fells the boss gives him his level too: one look on him, the bigger. */
    @Test
    void oneMomentOnAManAndTheBiggest() {
        var moments = new RunMoments();
        moments.since(line(3, 55, 7, 5), List.of());
        var both = moments.since(line(3, 55, 7, 6), List.of(55));
        var sizes = Map.of(Visuals.LEVEL_UP, 1.0, Visuals.BOSS_DOWN, 1.5);

        assertEquals(2, both.size(), "both happened");
        assertEquals(List.of(on(Visuals.BOSS_DOWN, 7)),
                RunMoments.biggestOnEach(both, name -> sizes.getOrDefault(name, 0.0)));
    }
}
