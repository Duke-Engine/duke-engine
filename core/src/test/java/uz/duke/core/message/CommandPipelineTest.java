package uz.duke.core.message;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.duke.core.GameLogic;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.ActiveBody;
import uz.duke.core.module.ModuleFactory;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ThingFactory;
import uz.duke.core.thing.ThingTemplate;

/** End-to-end: a command issued one frame is applied deterministically the next. */
class CommandPipelineTest {

    /** A logic that applies commands by pattern-matching the sealed hierarchy. */
    static final class CommandLogic extends GameLogic {
        int attacksHandled;

        CommandLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void onCommand(GameMessage command) {
            switch (command) {
                case GameMessage.MoveTo move -> {
                    for (var id : move.units()) {
                        var unit = findObject(id);
                        if (unit != null) {
                            unit.setPosition(move.destination());
                        }
                    }
                }
                case GameMessage.AttackObject ignored -> attacksHandled++;
                case GameMessage.StopMoving stop -> {
                    for (var id : stop.units()) {
                        var unit = findObject(id);
                        if (unit != null) {
                            unit.setPosition(Coord3D.ZERO);
                        }
                    }
                }
                case GameMessage.QueueProduction ignored -> {
                }
                case GameMessage.SetRallyPoint ignored -> {
                }
            }
        }

        @Override
        protected void simulate() {
        }
    }

    private CommandLogic logic;
    private ThingTemplate template;

    @BeforeEach
    void setUp() {
        var thingFactory = new ThingFactory(ModuleFactory.withDefaults());
        template = ThingTemplate.named("Unit")
                .module("ActiveBody", new ActiveBody.Data(100f))
                .build();
        thingFactory.addTemplate(template);
        logic = new CommandLogic(thingFactory);
        logic.init();
    }

    @Test
    void moveToCommandRepositionsUnitOnNextFrame() {
        GameObject unit = logic.createObject(template);
        var destination = new Coord3D(42f, 0f, 7f);

        logic.issueCommand(new GameMessage.MoveTo(0, List.of(unit.getId()), destination));
        assertEquals(Coord3D.ZERO, unit.getPosition()); // not applied until the frame runs

        logic.update();
        assertEquals(destination, unit.getPosition());
    }

    @Test
    void attackCommandIsDispatched() {
        GameObject unit = logic.createObject(template);
        logic.issueCommand(new GameMessage.AttackObject(0, List.of(unit.getId()), unit.getId()));
        logic.update();
        assertEquals(1, logic.attacksHandled);
    }

    @Test
    void commandsApplyInArrivalOrder() {
        GameObject unit = logic.createObject(template);
        logic.issueCommand(new GameMessage.MoveTo(0, List.of(unit.getId()), new Coord3D(10f, 0f, 0f)));
        logic.issueCommand(new GameMessage.StopMoving(0, List.of(unit.getId())));
        logic.update();
        // MoveTo then StopMoving: the later command wins, leaving the unit at origin.
        assertEquals(Coord3D.ZERO, unit.getPosition());
    }
}
