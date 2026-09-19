package uz.duke.game.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import uz.duke.game.DukeGame;

class ScriptModuleTest {

    /** A script that counts its lifecycle calls and walks east forever. */
    public static final class Walker extends UnitScript {
        static final AtomicInteger STARTS = new AtomicInteger();
        static final AtomicInteger UPDATES = new AtomicInteger();

        @Override
        public void onStart() {
            STARTS.incrementAndGet();
        }

        @Override
        public void onUpdate() {
            UPDATES.incrementAndGet();
            if (!isMoving()) {
                moveTo(position().x() + 50, position().y());
            }
        }
    }

    /** A script that always throws — must be disabled, not crash the sim. */
    public static final class Broken extends UnitScript {
        @Override
        public void onUpdate() {
            throw new IllegalStateException("bug in user code");
        }
    }

    private static final String INI = """
            Object
              Name = Runner
              KindOf = [INFANTRY, SELECTABLE]
              Modules = [
                ActiveBody
                  MaxHealth = 50
                End
                MoveUpdate
                  Speed = 30
                End
                ScriptModule
                  Name = Walker
                End
                ScriptModule
                  Name = Broken
                End
              ]
            End
            """;

    @Test
    void scriptRunsEachFrameAndBrokenScriptIsIsolated() {
        Walker.STARTS.set(0);
        Walker.UPDATES.set(0);

        var game = DukeGame.create("t")
                .loadUnits(INI)
                .customModules(mf -> {
                    ScriptModule.registerScript(mf, "Walker", Walker::new);
                    ScriptModule.registerScript(mf, "Broken", Broken::new);
                });
        var you = game.addPlayer("You", Color.BLUE);
        game.spawn("Runner", you, 0, 0);
        game.runHeadless(30);

        assertEquals(1, Walker.STARTS.get(), "onStart fires exactly once");
        assertEquals(30, Walker.UPDATES.get(), "onUpdate fires every logic frame");

        // the Walker script ordered movement through the script API
        var unit = game.getLogic().getObjects().get(0);
        assertTrue(unit.getPosition().x() > 10f, "script-issued move order should take effect");
        // and the Broken script did not stop the simulation
        assertEquals(30, game.getLogic().getFrame());
    }
}
