package uz.duke.core.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.duke.core.GameLogic;
import uz.duke.core.math.Coord3D;
import uz.duke.core.message.GameMessage;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ThingFactory;
import uz.duke.core.thing.ThingTemplate;

class AIUpdateTest {

    /** Routes MoveTo/StopMoving commands to each unit's AIUpdate module. */
    static final class MovementLogic extends GameLogic {
        MovementLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void onCommand(GameMessage command) {
            switch (command) {
                case GameMessage.MoveTo move -> {
                    for (var id : move.units()) {
                        var ai = aiFor(id);
                        if (ai != null) {
                            ai.moveTo(move.destination());
                        }
                    }
                }
                case GameMessage.StopMoving stop -> {
                    for (var id : stop.units()) {
                        var ai = aiFor(id);
                        if (ai != null) {
                            ai.stop();
                        }
                    }
                }
                case GameMessage.AttackObject ignored -> {
                }
                case GameMessage.QueueProduction ignored -> {
                }
                case GameMessage.SetRallyPoint ignored -> {
                }
            }
        }

        private AIUpdate aiFor(uz.duke.core.thing.ObjectId id) {
            var unit = findObject(id);
            return unit == null ? null : unit.findModule(AIUpdate.class);
        }

        @Override
        protected void simulate() {
        }
    }

    private MovementLogic logic;
    private ThingTemplate template;

    @BeforeEach
    void setUp() {
        var thingFactory = new ThingFactory(ModuleFactory.withDefaults());
        // Speed 30 units/sec at 30Hz == exactly 1 unit per frame.
        template = ThingTemplate.named("Mover")
                .module("ActiveBody", new ActiveBody.Data(100f))
                .module("AIUpdate", new AIUpdate.Data(30f))
                .build();
        thingFactory.addTemplate(template);
        logic = new MovementLogic(thingFactory);
        logic.init();
    }

    @Test
    void unitWalksToGoalAndStops() {
        GameObject unit = logic.createObject(template);
        logic.issueCommand(new GameMessage.MoveTo(0, List.of(unit.getId()), new Coord3D(10f, 0f, 0f)));

        for (int i = 0; i < 9; i++) {
            logic.update();
        }
        // After 9 frames (1 unit/frame) it is partway and still moving.
        assertEquals(9f, unit.getPosition().x(), 1e-4f);
        assertTrue(unit.findModule(AIUpdate.class).isMoving());

        logic.update(); // 10th frame: arrives exactly and stops
        assertEquals(new Coord3D(10f, 0f, 0f), unit.getPosition());
        assertFalse(unit.findModule(AIUpdate.class).isMoving());
    }

    @Test
    void stopCommandHaltsMovement() {
        GameObject unit = logic.createObject(template);
        logic.issueCommand(new GameMessage.MoveTo(0, List.of(unit.getId()), new Coord3D(100f, 0f, 0f)));
        logic.update();
        logic.update();
        float xAfterTwo = unit.getPosition().x();

        logic.issueCommand(new GameMessage.StopMoving(0, List.of(unit.getId())));
        logic.update();
        logic.update();

        // No further progress once stopped.
        assertEquals(xAfterTwo, unit.getPosition().x(), 1e-4f);
        assertFalse(unit.findModule(AIUpdate.class).isMoving());
    }

    @Test
    void speedParsesFromIni() {
        var ini = uz.duke.core.ini.Ini.of("""
                Speed = 45
                End
                """, uz.duke.core.ini.Ini.registry());
        var data = (AIUpdate.Data) AIUpdate.parseData(ini);
        assertEquals(45f, data.speedPerSecond(), 1e-6f);
    }
}
