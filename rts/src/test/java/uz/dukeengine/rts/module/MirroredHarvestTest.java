package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.RtsTemplate;

/**
 * A mover asked beside a box past another box, the four mirror images of one layout alike: a depot, 104 by 94, on the
 * line between a truck and its pile, 100 by 122 — the pile 330 off the depot's middle along both axes, the truck 100
 * off the other way, as the game measured it. One of the four once stopped 105.8 from its pile's middle and set off
 * for the same spot every second, for good.
 */
class MirroredHarvestTest {

    private static String loads(float sx, float sy) {
        var factory = new ThingFactory(RtsModules.withDefaults());
        var logic = new HarvestTest.TestLogic(factory);
        logic.init();
        int usa = logic.getPlayerList().addPlayer("USA").getIndex();
        logic.setPathGrid(new PathGrid(120, 120));
        var truck = RtsTemplate.named("Truck").geometry(new Geometry.Box(17f, 7f, 14f))
                .module(new ActiveBody.Data(100f))
                .module(new MoveUpdate.Data(40f, 90f, 240f, 50f, 180f, 0f, 0.1f, 0f, 15f, 1f, false,
                        MoveUpdate.Gait.WHEELS))
                .module(new HarvestUpdate.Data(300, 30, 0f, 30, 0, 0)).build();
        var depot = RtsTemplate.named("Depot").geometry(new Geometry.Box(52f, 47f, 20f))
                .module(new SupplyDepot.Data()).build();
        var pile = RtsTemplate.named("Pile").geometry(new Geometry.Box(50f, 61f, 10f))
                .module(new SupplyModule.Data(1000)).build();
        factory.addTemplate(truck);
        factory.addTemplate(depot);
        factory.addTemplate(pile);
        float cx = 600f;
        float cy = 600f;
        put(logic, depot, usa, cx, cy);
        var heap = put(logic, pile, usa, cx + sx * 330f, cy + sy * 330f);
        var harvester = put(logic, truck, usa, cx - sx * 100f, cy - sy * 100f);
        harvester.findModule(HarvestUpdate.class).workAt(heap);
        for (int frame = 0; frame < 1200; frame++) {
            logic.update();
            if (harvester.findModule(HarvestUpdate.class).getCarrying() > 0) {
                return null;
            }
        }
        return "(" + sx + ", " + sy + ") stood at " + harvester.getPosition() + ", "
                + uz.dukeengine.core.thing.World.reachBetween(harvester, heap) + " off the pile";
    }

    private static GameObject put(HarvestTest.TestLogic logic, uz.dukeengine.core.thing.ThingTemplate template,
            int side, float x, float y) {
        var thing = logic.createObject(template);
        thing.setPlayerIndex(side);
        thing.setPosition(new Coord3D(x, y, 0f));
        return thing;
    }

    @Test
    void eachMirrorImageReachesThePileAndLoads() {
        var stuck = new java.util.ArrayList<String>();
        for (float sx : new float[] {1f, -1f}) {
            for (float sy : new float[] {1f, -1f}) {
                var failed = loads(sx, sy);
                if (failed != null) {
                    stuck.add(failed);
                }
            }
        }
        assertTrue(stuck.isEmpty(), "every mirror image loads within 1200 frames: " + stuck);
    }
}
