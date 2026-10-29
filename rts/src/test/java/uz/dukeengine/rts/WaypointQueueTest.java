package uz.dukeengine.rts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.message.CombatOrder;
import uz.dukeengine.combat.message.GameOrder;
import uz.dukeengine.combat.network.CombatOrderCodec;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.Locomotor;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;

/** Places queued with the waypoint key, walked in turn; a plain order in their stead — {@code MSG_ADD_WAYPOINT}. */
class WaypointQueueTest {

    private static final Coord3D A = new Coord3D(200f, 100f, 0f);
    private static final Coord3D B = new Coord3D(200f, 250f, 0f);
    private static final Coord3D C = new Coord3D(80f, 250f, 0f);

    private static boolean near(GameObject unit, Coord3D place) {
        return Math.abs(unit.getPosition().x() - place.x()) < 15f && Math.abs(unit.getPosition().y() - place.y()) < 15f;
    }

    /** A mover that says it moves only two frames after it is sent, and then for five: places sent are all it keeps. */
    static final class Slow extends UpdateModule implements Locomotor {
        record Data() implements ModuleData {
        }

        static final List<Coord3D> sent = new ArrayList<>();
        /** How many frames after it is sent it says it moves. */
        static int startFrames = 2;
        private int starting;
        private int walking;
        private boolean moving;

        Slow(GameObject owner) {
            super(owner);
        }

        @Override
        public void moveTo(Coord3D destination) {
            sent.add(destination);
            starting = startFrames;
            moving = false;
        }

        @Override
        public boolean isMoving() {
            return moving;
        }

        @Override
        public void update() {
            if (starting > 0 && --starting == 0) {
                moving = true;
                walking = 5;
            } else if (moving && --walking == 0) {
                moving = false;
            }
        }
    }

    private record Field(RtsLogic logic, int red, GameObject unit) {

        static Field of(String template, uz.dukeengine.core.module.ModuleData mover) {
            var logic = new RtsLogic();
            logic.getThingFactory().getModuleFactory().register(Slow.Data.class, (owner, _) -> new Slow(owner));
            logic.getThingFactory().addTemplate(RtsTemplate.named(template).geometry(new Geometry.Cylinder(3f, 3f))
                    .module(new ActiveBody.Data(100f)).module(mover).build());
            logic.init();
            logic.setPathGrid(new PathGrid(40, 40));
            int red = logic.getPlayerList().addPlayer("Red").getIndex();
            var unit = logic.createObject(logic.getThingFactory().findTemplate(template));
            unit.setPlayerIndex(red);
            unit.setPosition(new Coord3D(80f, 100f, 0f));
            return new Field(logic, red, unit);
        }

        void click(Coord3D place, boolean added) {
            var move = new CombatOrder.MoveTo(red, List.of(unit.getId()), place, true);
            logic.issueCommand(added ? move.appended() : move);
        }
    }

    @Test
    void placesAddedAreWalkedInTurnAndAPlainMoveClearsThem() {
        var logic = new RtsLogic();
        logic.getThingFactory().addTemplate(RtsTemplate.named("Tank").geometry(new Geometry.Cylinder(3f, 3f))
                .module(new ActiveBody.Data(100f)).module(new MoveUpdate.Data(60f)).build());
        logic.init();
        logic.setPathGrid(new PathGrid(40, 40));
        int red = logic.getPlayerList().addPlayer("Red").getIndex();
        var tank = logic.createObject(logic.getThingFactory().findTemplate("Tank"));
        tank.setPlayerIndex(red);
        tank.setPosition(new Coord3D(80f, 100f, 0f));
        var units = List.of(tank.getId());
        for (var place : List.of(A, B, C)) {
            logic.issueCommand(new CombatOrder.MoveTo(red, units, place, true).appended());
        }
        var reached = new ArrayList<Coord3D>();
        for (int frame = 0; frame < 900 && reached.size() < 3; frame++) {
            logic.update();
            var next = List.of(A, B, C).get(reached.size());
            if (near(tank, next)) {
                reached.add(next);
            }
        }
        assertEquals(List.of(A, B, C), reached, "A, then B, then C");
        for (int frame = 0; frame < 300 && tank.getLocomotor().isMoving(); frame++) {
            logic.update();
        }

        for (var place : List.of(A, B)) {
            logic.issueCommand(new CombatOrder.MoveTo(red, units, place, true).appended());
        }
        logic.update();
        assertEquals(List.of(B), logic.waypointsOf(tank));
        var straight = new Coord3D(80f, 120f, 0f);
        logic.issueCommand(new CombatOrder.MoveTo(red, units, straight, true));
        logic.update();
        assertTrue(logic.waypointsOf(tank).isEmpty(), "a plain click in their stead");
    }

    /** Three clicks a frame apart, as a person gives them, and a plain one after: it goes straight to the new place. */
    @Test
    void placesClickedAFrameApartAreWalkedInOrderAndAPlainClickAfterGoesStraightOn() {
        var field = Field.of("Tank", new MoveUpdate.Data(60f));
        var tank = field.unit();
        for (var place : List.of(A, B, C)) {
            field.click(place, true);
            field.logic().update();
        }
        var reached = new ArrayList<Coord3D>();
        for (int frame = 0; frame < 900 && reached.size() < 3; frame++) {
            field.logic().update();
            var next = List.of(A, B, C).get(reached.size());
            if (near(tank, next)) {
                reached.add(next);
            }
        }
        assertEquals(List.of(A, B, C), reached, "A, then B, then C");
        assertTrue(field.logic().waypointsOf(tank).isEmpty());

        var fresh = new Coord3D(150f, 100f, 0f);
        field.click(fresh, false);
        for (int frame = 0; frame < 900 && !near(tank, fresh); frame++) {
            field.logic().update();
        }
        assertTrue(near(tank, fresh), "a plain click sent it to the new place");
    }

    /** The first place is a walk from the frame it is sent, whether or not its mover has begun to say so. */
    @Test
    void aPlaceSentAFrameBeforeIsNotReplacedByTheNextBeforeItsMoverSaysItMoves() {
        Slow.sent.clear();
        var field = Field.of("Walker", new Slow.Data());
        for (var place : List.of(A, B, C)) {
            field.click(place, true);
            field.logic().update();
        }
        assertEquals(List.of(A), Slow.sent, "B and C wait behind A");
        assertEquals(List.of(B, C), field.logic().waypointsOf(field.unit()));

        for (int frame = 0; frame < 60; frame++) {
            field.logic().update();
        }
        assertEquals(List.of(A, B, C), Slow.sent, "in the order they were clicked");
        assertTrue(field.logic().waypointsOf(field.unit()).isEmpty());

        field.click(A, false);
        field.logic().update();
        assertEquals(List.of(A, B, C, A), Slow.sent, "a plain click goes at once");
    }

    /** A mover that takes frames to say it moves — a tank turning first — keeps A ahead of places clicked a frame on. */
    @Test
    void aPlaceSentIsNotReplacedThoughItsMoverTakesSeveralFramesToSayItMoves() {
        Slow.sent.clear();
        Slow.startFrames = 5;
        try {
            var field = Field.of("Walker", new Slow.Data());
            for (var place : List.of(A, B, C)) {
                field.click(place, true);
                field.logic().update();
            }
            for (int frame = 0; frame < 3; frame++) {
                field.logic().update();
            }
            assertEquals(List.of(A), Slow.sent, "B and C still wait behind A, though it has not yet said it moves");

            for (int frame = 0; frame < 60; frame++) {
                field.logic().update();
            }
            assertEquals(List.of(A, B, C), Slow.sent, "in the order they were clicked");
        } finally {
            Slow.startFrames = 2;
        }
    }

    /** One sent where it cannot go waits no longer than a mover may take to start, and the next place is walked. */
    @Test
    void aMoverThatNeverSaysItMovesLetsTheNextPlaceGoOnceItHasHadItsTimeToStart() {
        Slow.sent.clear();
        Slow.startFrames = 1_000;
        try {
            var field = Field.of("Walker", new Slow.Data());
            field.click(A, true);
            field.logic().update();
            field.click(B, true);
            field.logic().update();
            for (int frame = 0; frame < RtsSimulation.STARTING_FRAMES; frame++) {
                field.logic().update();
            }
            assertEquals(List.of(A, B), Slow.sent);
        } finally {
            Slow.startFrames = 2;
        }
    }

    @Test
    void aWordOrderNamingAUnitEndsItsPlacesAndNobodyElses() {
        Slow.sent.clear();
        var field = Field.of("Walker", new Slow.Data());
        var heard = new ArrayList<GameOrder>();
        field.logic().onOtherCommands(command -> heard.add((GameOrder) command));
        var other = field.logic().createObject(field.logic().getThingFactory().findTemplate("Walker"));
        other.setPlayerIndex(field.logic().getPlayerList().addPlayer("Blue").getIndex());
        for (var walker : List.of(field.unit(), other)) {
            for (var place : List.of(A, B)) {
                field.logic().issueCommand(new CombatOrder.MoveTo(walker.getPlayerIndex(), List.of(walker.getId()),
                        place, true).appended());
            }
            field.logic().update();
        }
        assertEquals(List.of(B), field.logic().waypointsOf(field.unit()));
        assertEquals(List.of(B), field.logic().waypointsOf(other));

        field.logic().issueCommand(new GameOrder(field.red(), "Enter", List.of(field.unit().getId(), other.getId()),
                A, null, 0));
        field.logic().update();

        assertTrue(field.logic().waypointsOf(field.unit()).isEmpty(), "the word is an order in their stead");
        assertEquals(List.of(B), field.logic().waypointsOf(other), "a unit of another side is not the issuer's to order");
        assertEquals(1, heard.size(), "and the game still hears it");
    }

    @Test
    void anAddedMoveTravelsTheWire() {
        var move = new CombatOrder.MoveTo(2, List.of(), A, false).appended();
        assertEquals(move, CombatOrderCodec.decode(CombatOrderCodec.encode(move)));
        var click = new CombatOrder.MoveTo(2, List.of(), A, true);
        assertEquals(click, CombatOrderCodec.decode(CombatOrderCodec.encode(click)));
    }
}
