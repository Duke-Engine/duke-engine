package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.view.UnitView;

/** A place the game names open, far from every eye: in sight while listed, remembered once it is not (E103). */
class AlsoSeenTest {

    @Test
    void aNamedPlaceIsInSightWhileListedAndRememberedAfter() {
        var grid = new PathGrid(40, 40);
        var seen = new Discovery(grid);
        var hero = List.of(new UnitView(1, "Rogue", 0, 25f, 25f, 0f, 10f, 10f, false, true, false, false, -1));
        var palace = List.of(new Visuals.Seen(305f, 305f, 30f));

        seen.reveal(hero, 0, 20f, null, palace);
        seen.soften(1f / 30f);
        assertEquals(Discovery.State.VISIBLE, seen.stateAt(30, 30), "the palace, far from him, in sight");
        assertEquals(Discovery.State.UNSEEN, seen.stateAt(20, 20), "and between them, still nothing");

        seen.reveal(hero, 0, 20f, null, List.of());
        seen.soften(1f / 30f);
        assertEquals(Discovery.State.REMEMBERED, seen.stateAt(30, 30), "no longer listed: remembered");
        assertEquals(Discovery.State.VISIBLE, seen.stateAt(2, 2), "his own sight as ever");
    }
}
