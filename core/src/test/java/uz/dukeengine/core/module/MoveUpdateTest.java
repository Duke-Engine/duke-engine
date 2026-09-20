package uz.dukeengine.core.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.TestCommand;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

class MoveUpdateTest {

    /** Routes Move/Halt commands to each unit's MoveUpdate module. */
    static final class MovementLogic extends GameLogic {
        MovementLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void onCommand(Command command) {
            switch (command) {
                case TestCommand.Move move -> forEach(move.units(), mover -> mover.moveTo(move.destination()));
                case TestCommand.Halt halt -> forEach(halt.units(), MoveUpdate::stop);
                default -> {
                }
            }
        }

        private void forEach(List<ObjectId> units, java.util.function.Consumer<MoveUpdate> action) {
            for (var id : units) {
                var unit = findObject(id);
                var mover = unit == null ? null : unit.findModule(MoveUpdate.class);
                if (mover != null) {
                    action.accept(mover);
                }
            }
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
                .module(new ActiveBody.Data(100f))
                .module(new MoveUpdate.Data(30f))
                .build();
        thingFactory.addTemplate(template);
        logic = new MovementLogic(thingFactory);
        logic.init();
    }

    @Test
    void unitWalksToGoalAndStops() {
        GameObject unit = logic.createObject(template);
        logic.issueCommand(new TestCommand.Move(0, List.of(unit.getId()), new Coord3D(10f, 0f, 0f)));

        for (int i = 0; i < 9; i++) {
            logic.update();
        }
        // After 9 frames (1 unit/frame) it is partway and still moving.
        assertEquals(9f, unit.getPosition().x(), 1e-4f);
        assertTrue(unit.findModule(MoveUpdate.class).isMoving());

        logic.update(); // 10th frame: arrives exactly and stops
        assertEquals(new Coord3D(10f, 0f, 0f), unit.getPosition());
        assertFalse(unit.findModule(MoveUpdate.class).isMoving());
    }

    @Test
    void stopCommandHaltsMovement() {
        GameObject unit = logic.createObject(template);
        logic.issueCommand(new TestCommand.Move(0, List.of(unit.getId()), new Coord3D(100f, 0f, 0f)));
        logic.update();
        logic.update();
        float xAfterTwo = unit.getPosition().x();

        logic.issueCommand(new TestCommand.Halt(0, List.of(unit.getId())));
        logic.update();
        logic.update();

        // No further progress once stopped.
        assertEquals(xAfterTwo, unit.getPosition().x(), 1e-4f);
        assertFalse(unit.findModule(MoveUpdate.class).isMoving());
    }

    /** The block's keys are the data's components: {@code Speed} is {@code speed}. */
    @Test
    void speedIsReadFromItsBlock() {
        var block = uz.dukeengine.core.data.DukeText.parse("MoveUpdate\n  Speed = 45\nEnd\n", "move.duke").getFirst();
        var data = new uz.dukeengine.core.data.Binder().bind(block, MoveUpdate.Data.class);
        assertEquals(45f, data.speed(), 1e-6f);
    }
}
