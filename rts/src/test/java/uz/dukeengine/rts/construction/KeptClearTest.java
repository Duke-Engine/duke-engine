package uz.dukeengine.rts.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.Kind;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.RtsSimulation;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.rts.module.RtsModules;

/** A depot kept a border from a heap, outline to outline — the reference's {@code SupplyBuildBorder}. */
class KeptClearTest {

    private static final Kind HEAP = Kind.of("HEAP");

    private static Placement.Fit depotAt(float gap, PlacementRules rules) {
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(RtsTemplate.named("Depot").geometry(new Geometry.Box(10f, 10f, 10f))
                .module(new ActiveBody.Data(500f)).build());
        factory.addTemplate(RtsTemplate.named("Heap").geometry(new Geometry.Cylinder(5f, 3f)).kindOf(HEAP)
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
        world.createObject(factory.findTemplate("Heap")).setPosition(new Coord3D(100f, 200f, 0f));
        // The heap's edge at 105, the depot's at its middle less 10.
        var place = new Coord3D(105f + gap + 10f, 200f, 0f);
        return Placement.check(world, world.getPathGrid(), factory.findTemplate("Depot"), place, 0f, rules);
    }

    @Test
    void aSiteWithinItsBorderOfAHeapIsRefused() {
        var rules = PlacementRules.DEFAULTS.keepClear("Depot", 40f, thing -> thing.isKindOf(HEAP));

        assertEquals(Placement.Fit.TOO_CLOSE_TO_KIND, depotAt(39f, rules));
        assertEquals(Placement.Fit.FITS, depotAt(41f, rules));
        assertEquals(Placement.Fit.FITS, depotAt(39f, PlacementRules.DEFAULTS), "a game without the rule");
        assertEquals(Placement.Fit.FITS,
                depotAt(39f, PlacementRules.DEFAULTS.keepClear("Barracks", 40f, thing -> thing.isKindOf(HEAP))),
                "another template's border");
    }
}
