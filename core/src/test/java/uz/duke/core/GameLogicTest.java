package uz.duke.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GameLogicTest {

    private static final class CountingLogic extends GameLogic {
        int simulateCalls;

        @Override
        protected void simulate() {
            simulateCalls++;
        }
    }

    @Test
    void frameAdvancesAndDerivesGameTime() {
        var logic = new CountingLogic();
        logic.init();

        for (int i = 0; i < GameConstants.LOGICFRAMES_PER_SECOND; i++) {
            logic.update();
        }

        assertEquals(GameConstants.LOGICFRAMES_PER_SECOND, logic.getFrame());
        assertEquals(GameConstants.LOGICFRAMES_PER_SECOND, logic.simulateCalls);
        assertEquals(1.0f, logic.getGameTimeSeconds(), 1e-6f);
    }

    @Test
    void resetReturnsToFrameZero() {
        var logic = new CountingLogic();
        logic.init();
        logic.update();
        logic.update();
        assertEquals(2, logic.getFrame());

        logic.reset();
        assertEquals(0, logic.getFrame());
        assertFalse(logic.isInGame());
    }

    @Test
    void pauseFlagIsHonouredByCaller() {
        var logic = new CountingLogic();
        logic.init();
        assertFalse(logic.isGamePaused());
        logic.setGamePaused(true);
        assertTrue(logic.isGamePaused());
    }
}
