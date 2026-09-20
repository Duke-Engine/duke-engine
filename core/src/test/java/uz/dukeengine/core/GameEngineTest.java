package uz.dukeengine.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GameEngineTest {

    /** An engine that stops itself once the logic reaches a target frame. */
    private static final class TestEngine extends GameEngine {
        private final int stopAtFrame;

        TestEngine(int stopAtFrame) {
            this.stopAtFrame = stopAtFrame;
        }

        @Override
        protected GameLogic createGameLogic() {
            return new GameLogic() {
                @Override
                protected void simulate() {
                }
            };
        }

        @Override
        protected GameClient createGameClient() {
            return new GameClient() {
                @Override
                protected void render() {
                }
            };
        }

        @Override
        public void update() {
            super.update();
            if (getLogic().getFrame() >= stopAtFrame) {
                setQuitting(true);
            }
        }
    }

    @Test
    void loopStepsLogicToTargetAndRendersAtLeastAsOften() {
        var engine = new TestEngine(GameConstants.LOGICFRAMES_PER_SECOND);
        engine.setMaxFps(0); // uncapped: run as fast as possible
        engine.init();
        engine.execute();

        // Logic never overshoots its target — the accumulator drains exactly.
        assertEquals(GameConstants.LOGICFRAMES_PER_SECOND, engine.getLogic().getFrame());
        // Uncapped, the client renders at least once per logic frame.
        assertTrue(engine.getClient().getFrame() >= engine.getLogic().getFrame());
    }

    @Test
    void pausedEngineDoesNotAdvanceLogic() {
        var engine = new TestEngine(5);
        engine.setMaxFps(0);
        engine.init();
        engine.getLogic().setGamePaused(true);

        // Run the loop on a separate thread; it can never reach the stop frame
        // while paused, so cancel it after a short spin of client frames.
        var runner = new Thread(engine::execute);
        runner.start();
        long deadline = System.nanoTime() + 100_000_000L; // 100ms
        while (System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        engine.setQuitting(true);
        try {
            runner.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertEquals(0, engine.getLogic().getFrame());
        assertTrue(engine.getClient().getFrame() > 0);
    }
}
