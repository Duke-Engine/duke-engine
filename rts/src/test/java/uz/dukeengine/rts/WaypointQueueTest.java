package uz.dukeengine.rts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.message.CombatOrder;
import uz.dukeengine.combat.network.CombatOrderCodec;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.MoveUpdate;
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

    @Test
    void anAddedMoveTravelsTheWire() {
        var move = new CombatOrder.MoveTo(2, List.of(), A, false).appended();
        assertEquals(move, CombatOrderCodec.decode(CombatOrderCodec.encode(move)));
        var click = new CombatOrder.MoveTo(2, List.of(), A, true);
        assertEquals(click, CombatOrderCodec.decode(CombatOrderCodec.encode(click)));
    }
}
