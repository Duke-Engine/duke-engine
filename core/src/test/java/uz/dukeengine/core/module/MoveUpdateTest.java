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

    /**
     * A vehicle that turns slowly and only drives forward, sent to a point one body-length behind it,
     * arrives there. It used to circle the point — a point behind is always inside the turning circle — and
     * the progress check gave up on it short of the goal.
     */
    @Test
    void aSlowTurningVehicleReachesAPointJustBehindIt() {
        var thingFactory = new ThingFactory(ModuleFactory.withDefaults());
        var vehicle = ThingTemplate.named("Dozer")
                .module(new ActiveBody.Data(100f))
                .module(new MoveUpdate.Data(30f, 45f)) // one unit a frame; an eighth of a turn a second
                .build();
        thingFactory.addTemplate(vehicle);
        var world = new MovementLogic(thingFactory);
        world.init();
        var dozer = world.createObject(vehicle);
        dozer.setPosition(new Coord3D(50f, 50f, 0f));
        dozer.setOrientation(0f); // facing +x
        var behind = new Coord3D(35f, 50f, 0f); // fifteen units behind: one body-length

        world.issueCommand(new TestCommand.Move(0, List.of(dozer.getId()), behind));
        var mover = dozer.findModule(MoveUpdate.class);
        world.update(); // the order is applied at the start of a frame
        int frames = 1;
        while (mover.isMoving() && frames < 30 * 20) {
            world.update();
            frames++;
        }

        var at = dozer.getPosition();
        float off = (float) Math.sqrt((at.x() - behind.x()) * (at.x() - behind.x())
                + (at.y() - behind.y()) * (at.y() - behind.y()));
        assertTrue(off < 0.01f, "it got there, not " + off + " short, in " + frames + " frames");
        assertEquals(behind, mover.getGoal(), "and arrived rather than being given up on");
    }

    /** Walks {@code frames} frames and says how far along its way it went, frame by frame. */
    private float walk(GameObject unit, int frames) {
        float walked = 0f;
        for (int frame = 0; frame < frames; frame++) {
            var was = unit.getPosition();
            logic.update();
            walked += (float) Math.hypot(unit.getPosition().x() - was.x(), unit.getPosition().y() - was.y());
        }
        return walked;
    }

    @Test
    void aUnitSpedUpMidRouteGoesTheFasterWayItWasGoingWithoutPlanningAgain() {
        var grid = new uz.dukeengine.core.pathfind.PathGrid(40, 40);
        for (int cy = 0; cy < 30; cy++) {
            grid.setBlocked(20, cy, true); // a wall with a way round at the top: a route of corners
        }
        logic.setPathGrid(grid);
        var unit = logic.spawn(template, new Coord3D(50f, 50f, 0f), 1);
        var legs = unit.findModule(MoveUpdate.class);
        legs.setSpeed(25f, 0f);
        legs.moveTo(new Coord3D(350f, 50f, 0f));
        assertEquals(25f, walk(unit, 30), 0.01f, "a second at 25");

        legs.setSpeed(35f, 0f);
        float walked = 0f;
        for (int frame = 0; frame < 30; frame++) {
            walked += walk(unit, 1);
            assertFalse(legs.isWaitingForRoute(), "it asked for no new route");
        }

        assertEquals(35f, walked, 0.01f, "the next second at 35, along the way it had");
        for (int frame = 0; frame < 2000 && legs.isMoving(); frame++) {
            logic.update();
        }
        assertEquals(350f, unit.getPosition().x(), 0.5f, "and it gets where it was going");
    }

    @Test
    void aUnitSpedUpWhileLeavingItsMakerStillGoesOnToWhereItWasSent() {
        var unit = logic.spawn(template, new Coord3D(50f, 50f, 0f), 1);
        var legs = unit.findModule(MoveUpdate.class);
        legs.leave(new Coord3D(80f, 50f, 0f), new Coord3D(80f, 150f, 0f));
        walk(unit, 10); // on its first leg, through the door

        legs.setSpeed(35f, 0f);
        for (int frame = 0; frame < 600 && legs.isMoving(); frame++) {
            logic.update();
        }

        assertEquals(80f, unit.getPosition().x(), 0.5f);
        assertEquals(150f, unit.getPosition().y(), 0.5f, "on to the rally point, not stopped at the door");
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
    /**
     * Out at the far corner of a world four thousand cells a side — some forty-one thousand units from its origin — a
     * unit walks as it walks beside the origin: frame for frame the same way along, to within a hundredth of a unit,
     * and it stops where the one beside the origin stops.
     */
    @Test
    void aUnitFortyThousandUnitsOutWalksAsOneBesideTheOrigin() {
        var near = logic.spawn(template, new Coord3D(100f, 100f, 0f), 1);
        var far = logic.spawn(template, new Coord3D(40_900f, 40_880f, 0f), 1);
        near.findModule(MoveUpdate.class).setSpeed(7f, 0f); // a slow walk: a quarter of a unit a frame, less
        far.findModule(MoveUpdate.class).setSpeed(7f, 0f);
        near.findModule(MoveUpdate.class).moveTo(new Coord3D(100f + 30f, 100f + 17f, 0f));
        far.findModule(MoveUpdate.class).moveTo(new Coord3D(40_900f + 30f, 40_880f + 17f, 0f));
        for (int frame = 0; frame < 200; frame++) {
            var nearWas = near.getPosition();
            var farWas = far.getPosition();
            logic.update();
            float nearStep = nearWas.distance(near.getPosition());
            float farStep = farWas.distance(far.getPosition());
            assertEquals(nearStep, farStep, 0.01f, "frame " + frame + ": the same step out there");
        }
        // A float out there is a 256th of a unit: a walk of a hundred and fifty steps ends within a twentieth of one.
        assertEquals(near.getPosition().x() - 100f, far.getPosition().x() - 40_900f, 0.05f, "and ends where it ends");
        assertEquals(near.getPosition().y() - 100f, far.getPosition().y() - 40_880f, 0.05f);
    }
}
