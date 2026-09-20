package uz.dukeengine.core.message;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.TestCommand;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.ModuleFactory;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

/** End-to-end: a command issued one frame is applied deterministically the next. */
class CommandPipelineTest {

    /** A logic that applies commands by pattern-matching its own sealed hierarchy. */
    static final class CommandLogic extends GameLogic {
        int pingsHandled;

        CommandLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void onCommand(Command command) {
            if (!(command instanceof TestCommand testCommand)) {
                return;
            }
            switch (testCommand) {
                case TestCommand.Move move -> moveEach(move.units(), move.destination());
                case TestCommand.Halt halt -> moveEach(halt.units(), Coord3D.ZERO);
                case TestCommand.Ping ignored -> pingsHandled++;
            }
        }

        private void moveEach(List<uz.dukeengine.core.thing.ObjectId> units, Coord3D destination) {
            for (var id : units) {
                var unit = findObject(id);
                if (unit != null) {
                    unit.setPosition(destination);
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
                .module(new ActiveBody.Data(100f))
                .build();
        thingFactory.addTemplate(template);
        logic = new CommandLogic(thingFactory);
        logic.init();
    }

    @Test
    void moveCommandRepositionsUnitOnNextFrame() {
        GameObject unit = logic.createObject(template);
        var destination = new Coord3D(42f, 0f, 7f);

        logic.issueCommand(new TestCommand.Move(0, List.of(unit.getId()), destination));
        assertEquals(Coord3D.ZERO, unit.getPosition()); // not applied until the frame runs

        logic.update();
        assertEquals(destination, unit.getPosition());
    }

    @Test
    void payloadFreeCommandIsDispatched() {
        logic.issueCommand(new TestCommand.Ping(0, "hello"));
        logic.update();
        assertEquals(1, logic.pingsHandled);
    }

    @Test
    void commandsApplyInArrivalOrder() {
        GameObject unit = logic.createObject(template);
        logic.issueCommand(new TestCommand.Move(0, List.of(unit.getId()), new Coord3D(10f, 0f, 0f)));
        logic.issueCommand(new TestCommand.Halt(0, List.of(unit.getId())));
        logic.update();
        // Move then Halt: the later command wins, leaving the unit at origin.
        assertEquals(Coord3D.ZERO, unit.getPosition());
    }
}
