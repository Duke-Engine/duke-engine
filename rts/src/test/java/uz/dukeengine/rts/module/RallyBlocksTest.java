package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.pathfind.Block;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.RtsTemplate;

/** A factory's units stand beside its rally point on ground of their own, not on one another. */
class RallyBlocksTest {

    @Test
    void threeVehiclesMadeOneAfterAnotherStandOnBlocksOfTheirOwnRoundTheRallyPointTheFirstOnIt() {
        var things = new ThingFactory(RtsModules.withDefaults());
        var humvee = RtsTemplate.named("Humvee")
                .module(new ActiveBody.Data(200f))
                .module(new MoveUpdate.Data(30f))
                .geometry(new Geometry.Box(15f, 10f, 8f)) // a circle of 18: a 3 by 3 block
                .buildTimeFrames(30)
                .build();
        things.addTemplate(humvee);
        var warFactory = RtsTemplate.named("WarFactory")
                .module(new ActiveBody.Data(1000f))
                .module(new ProductionUpdate.Data(List.of(), List.of(),
                        new ProductionUpdate.Exit(new Coord3D(-10f, -30f, 0f), new Coord3D(53f, -30f, 0f), 0, 0),
                        null, null))
                .geometry(new Geometry.Box(40f, 40f, 20f))
                .build();
        things.addTemplate(warFactory);
        var logic = new ProductionTest.TestLogic(things);
        logic.init();
        logic.setPathGrid(new PathGrid(80, 80));
        int side = logic.getPlayerList().addPlayer("USA").getIndex();
        var factory = logic.createObject(warFactory);
        factory.setPlayerIndex(side);
        factory.setPosition(new Coord3D(200f, 200f, 0f));
        var production = factory.findModule(ProductionUpdate.class);
        var rally = new Coord3D(450f, 200f, 0f); // 250 away
        production.setRallyPoint(rally);
        for (int n = 0; n < 3; n++) {
            production.queue(humvee);
        }

        for (int frame = 0; frame < 1800; frame++) {
            logic.update();
        }

        var made = logic.getObjects().stream().filter(o -> o.getTemplate() == humvee).toList();
        assertEquals(3, made.size(), "all three made");
        made.forEach(m -> assertFalse(m.findModule(MoveUpdate.class).isMoving(), "all there: " + m.getPosition()));
        var rallyBlock = Block.of(18.03f, 10f, rally.x(), rally.y());
        assertEquals(rallyBlock, blockOf(made.getFirst()), "the first on the rally point's own block");
        for (int i = 0; i < made.size(); i++) {
            for (int j = i + 1; j < made.size(); j++) {
                assertFalse(blockOf(made.get(i)).overlaps(blockOf(made.get(j))),
                        "no two on one another: " + made.get(i).getPosition() + " and " + made.get(j).getPosition());
            }
            assertTrue(made.get(i).getPosition().distance(rally) < 45f, "round it: " + made.get(i).getPosition());
        }
    }

    private static Block blockOf(GameObject thing) {
        return Block.of(18.03f, 10f, thing.getPosition().x(), thing.getPosition().y());
    }
}
