package uz.dukeengine.core;

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

    private record Storeyed(String name, float levelHeight)
            implements uz.dukeengine.core.thing.WorldTemplate, uz.dukeengine.core.thing.Layered {
    }

    /** Every map laid in a layered world stands at its height: one laid before it was said, and one after. */
    @Test
    void aLayeredWorldLaysEveryMapAtItsHeight() {
        var logic = new CountingLogic();
        var first = new uz.dukeengine.core.pathfind.PathGrid(4, 4);
        logic.setPathGrid(first);
        logic.setWorld(new Storeyed("Tower", 10f));
        assertEquals(10f, first.getLevelHeight(), "a world said after the map still lays it");

        var next = new uz.dukeengine.core.pathfind.PathGrid(4, 4);
        logic.setPathGrid(next);
        assertEquals(10f, next.getLevelHeight(), "and every map after it");
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
