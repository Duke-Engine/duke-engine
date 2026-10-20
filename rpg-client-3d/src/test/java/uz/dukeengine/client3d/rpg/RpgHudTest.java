package uz.dukeengine.client3d.rpg;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.HudPart;
import uz.dukeengine.core.view.UnitView;
import uz.dukeengine.core.view.WorldSnapshot;

/** The RPG's part is found on the classpath, and its pause menu says how deep the run is and the hero's rank. */
class RpgHudTest {

    private static WorldSnapshot saying(String status) {
        return saying(status, List.of());
    }

    private static WorldSnapshot saying(String status, List<UnitView> units) {
        return new WorldSnapshot(12, 0f, false, 0, 0, units, List.of(), "", status, List.of(), true);
    }

    private static UnitView unit(int id, int player) {
        return new UnitView(id, "Thing", player, 10f, 10f, 0f, 100f, 100f, false, true, false, false, -1);
    }

    /**
     * Single-hero control: with a skeleton picked out to look at, the player's orders go to the hero the bars'
     * field names — his own, standing — and with no hero named, to what he has selected of his own.
     */
    @Test
    void ordersGoToTheNamedHeroWhateverIsPickedOut() {
        var hero = unit(3, 1);
        var skeleton = unit(9, 2);
        var hud = new RpgHud();

        assertEquals(List.of(hero), hud.ordered(saying("hero=3,7,10,20,5,100", List.of(hero, skeleton)), 1,
                List.of()));
        assertNull(hud.ordered(saying("deep=3", List.of(hero, skeleton)), 1, List.of()), "no hero named");
        assertNull(hud.ordered(saying("hero=9,1,0,0,0,0", List.of(hero, skeleton)), 1, List.of()),
                "a hero not his");
        assertNull(hud.ordered(saying("hero=3,7,10,20,5,100", List.of(skeleton)), 1, List.of()), "fallen");
    }

    /**
     * The hero's frame is the hero's: a skeleton picked, the hero the card names stands in it; nothing picked, still
     * him; with no hero named, what is picked, as in any game.
     */
    @Test
    void thePortraitIsTheNamedHerosWhateverIsPicked() {
        var hero = unit(3, 1);
        var skeleton = unit(9, 2);
        var named = saying("hero=3,7,10,20,5,100", List.of(hero, skeleton));

        assertEquals(hero, RpgHud.portraitSubject(named, skeleton), "a monster picked");
        assertEquals(hero, RpgHud.portraitSubject(named, null), "nothing picked");
        assertEquals(skeleton, RpgHud.portraitSubject(saying("deep=3", List.of(hero, skeleton)), skeleton),
                "no hero named: the pick");
        assertNull(RpgHud.portraitSubject(saying("hero=3,7,10,20,5,100", List.of(skeleton)), skeleton),
                "the named hero fallen: nobody, not the pick");
    }

    @Test
    void itIsFound() {
        assertTrue(HudPart.found().stream().anyMatch(RpgHud.class::isInstance), "listed in META-INF/services");
    }

    @Test
    void thePauseMenuSaysTheDepthAndTheRankOffTheCardAndNothingOfALineThatIsNotOne() {
        var facts = new RpgHud().pauseFacts(saying("name=Erika|rank=7-daraja|depth=III|depthWord=CHUQURLIK"));

        assertEquals(2, facts.size());
        assertArrayEquals(new String[] {"CHUQURLIK", "III"}, facts.getFirst());
        assertArrayEquals(new String[] {"RANK", "7-daraja"}, facts.get(1));
        assertEquals(List.of(), new RpgHud().pauseFacts(saying("$ 500")));
    }
}
