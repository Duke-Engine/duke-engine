package uz.dukeengine.client3d.rpg;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.HudPart;
import uz.dukeengine.core.view.WorldSnapshot;

/** The RPG's part is found on the classpath, and its pause menu says how deep the run is and the hero's rank. */
class RpgHudTest {

    private static WorldSnapshot saying(String status) {
        return new WorldSnapshot(12, 0f, false, 0, 0, List.of(), List.of(), "", status, List.of(), true);
    }

    @Test
    void itIsFound() {
        assertTrue(HudPart.found().stream().anyMatch(RpgHud.class::isInstance), "listed in META-INF/services");
    }

    @Test
    void thePauseMenuSaysTheDepthAndTheRankOffTheCardAndNothingOfALineThatIsNotOne() {
        var facts = new RpgHud().pauseFacts(saying("name=Erika|rank=7-daraja|depth=III|depthWord=CHUQURLIK"));

        assertEquals(2, facts.size());
        assertArrayEquals(new String[] {"CHUQURLIK", "III"}, facts.get(0));
        assertArrayEquals(new String[] {"RANK", "7-daraja"}, facts.get(1));
        assertEquals(List.of(), new RpgHud().pauseFacts(saying("$ 500")));
    }
}
