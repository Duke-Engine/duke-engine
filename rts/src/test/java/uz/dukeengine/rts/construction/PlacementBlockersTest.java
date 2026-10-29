package uz.dukeengine.rts.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.Kind;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.RtsSimulation;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.rts.module.RtsModules;

/** What stands against a site, as a client lays its red marks under: what is in the way, then what is too close. */
class PlacementBlockersTest {

    private static final Kind HEAP = Kind.of("HEAP");

    @Test
    void theThingsInTheWayComeFirstThenThoseTooCloseEachOnce() {
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(RtsTemplate.named("Depot").geometry(new Geometry.Box(10f, 10f, 10f))
                .module(new ActiveBody.Data(500f)).build());
        factory.addTemplate(RtsTemplate.named("Heap").geometry(new Geometry.Cylinder(5f, 3f)).kindOf(HEAP)
                .module(new ActiveBody.Data(10f)).build());
        factory.addTemplate(RtsTemplate.named("Rock").geometry(new Geometry.Cylinder(4f, 3f))
                .module(new ActiveBody.Data(10f)).build());
        var world = new RtsSimulation(factory) {
            @Override
            protected void onRtsCommand(uz.dukeengine.rts.message.GameMessage command) {
            }

            @Override
            protected void simulate() {
            }
        };
        world.init();
        world.setPathGrid(new PathGrid(40, 40));
        GameObject heap = world.createObject(factory.findTemplate("Heap"));
        heap.setPosition(new Coord3D(100f, 200f, 0f));
        GameObject rock = world.createObject(factory.findTemplate("Rock"));
        rock.setPosition(new Coord3D(154f, 200f, 0f));
        var depot = factory.findTemplate("Depot");
        var rules = PlacementRules.DEFAULTS.keepClear("Depot", 40f, thing -> thing.isKindOf(HEAP));
        var site = new Coord3D(154f, 200f, 0f); // its edge 39 from the heap's

        assertEquals(Placement.Fit.IN_THE_WAY, Placement.check(world, world.getPathGrid(), depot, site, 0f, rules));
        assertEquals(List.of(rock, heap), Placement.blockers(world, depot, site, 0f, rules),
                "the rock in its way, then the heap it is kept clear of, though the heap is the older");
        assertEquals(List.of(rock), Placement.blockers(world, depot, site, 0f, PlacementRules.DEFAULTS),
                "without the rule, only the rock");
        assertEquals(List.of(), Placement.blockers(world, depot, new Coord3D(300f, 200f, 0f), 0f, rules),
                "nothing against a site in the clear");
    }
}
