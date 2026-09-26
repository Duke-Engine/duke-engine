package uz.dukeengine.rts.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.Kind;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.RtsSimulation;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.rts.module.RtsModules;

/**
 * What a site may stand over, and the game told the frame one is put down — the reference's {@code
 * BuildAssistant::isRemovableForConstruction} and {@code clearRemovableForConstruction}.
 */
class StandingOverTest {

    private static final Kind SHRUBBERY = Kind.of("SHRUBBERY");
    private static final Coord3D PLACE = new Coord3D(205f, 205f, 0f);

    private static final class World extends RtsSimulation {
        World(ThingFactory factory) {
            super(factory);
        }

        @Override
        protected void onRtsCommand(GameMessage command) {
            if (command instanceof GameMessage.Construct build) {
                construct(build);
            }
        }

        @Override
        protected void simulate() {
        }
    }

    private record Scene(World world, int player, GameObject dozer) {
    }

    private static Scene scene(PlacementRules rules) {
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(RtsTemplate.named("Dozer").geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(100f)).module(new MoveUpdate.Data(30f)).build());
        factory.addTemplate(RtsTemplate.named("Barracks").geometry(new Geometry.Box(9f, 9f, 16f))
                .module(new ActiveBody.Data(600f)).buildCost(100).buildTimeFrames(300).build());
        factory.addTemplate(RtsTemplate.named("Shrub").geometry(new Geometry.Cylinder(3f, 2f)).kindOf(SHRUBBERY)
                .module(new ActiveBody.Data(10f)).build());
        factory.addTemplate(RtsTemplate.named("Rock").geometry(new Geometry.Cylinder(3f, 2f))
                .module(new ActiveBody.Data(10f)).build());
        var world = new World(factory);
        world.init();
        world.setPathGrid(new PathGrid(40, 40));
        world.setPlacementRules(rules);
        int player = world.getPlayerList().addPlayer("Builder").getIndex();
        world.getRtsPlayer(player).deposit(1000);
        var dozer = world.createObject(factory.findTemplate("Dozer"));
        dozer.setPlayerIndex(player);
        dozer.setPosition(new Coord3D(100f, 100f, 0f));
        return new Scene(world, player, dozer);
    }

    private static void put(World world, String template) {
        world.createObject(world.getThingFactory().findTemplate(template)).setPosition(PLACE);
    }

    private static Placement.Fit fit(World world, PlacementRules rules) {
        return Placement.check(world, world.getPathGrid(), world.getThingFactory().findTemplate("Barracks"), PLACE, 0f,
                rules);
    }

    @Test
    void aSiteOverAThingOfAKindItMayStandOverFitsAndOverAnotherIsInTheWay() {
        var rules = new PlacementRules(10f, 30f, 0.5f, 0.1f).standsOver(Set.of(SHRUBBERY));
        var shrubbed = scene(rules).world();
        put(shrubbed, "Shrub");
        assertEquals(Placement.Fit.FITS, fit(shrubbed, rules));

        var rocky = scene(rules).world();
        put(rocky, "Rock");
        assertEquals(Placement.Fit.IN_THE_WAY, fit(rocky, rules));

        var plain = new PlacementRules(10f, 30f, 0.5f, 0.1f);
        assertEquals(Placement.Fit.IN_THE_WAY, fit(shrubbed, plain), "a shrub in the way where no kind is named");
    }

    @Test
    void theGameIsToldOnceTheFrameASiteIsPutDownWithTheSite() {
        var rules = new PlacementRules(10f, 30f, 0.5f, 0.1f).standsOver(Set.of(SHRUBBERY)).siteAtOrder(true);
        var scene = scene(rules);
        put(scene.world(), "Shrub");
        var placed = new ArrayList<GameObject>();
        var frames = new ArrayList<Integer>();
        scene.world().onPlaced(site -> {
            placed.add(site);
            frames.add(scene.world().getFrame());
        });
        int ordered = scene.world().getFrame();
        scene.world().issueCommand(new GameMessage.Construct(scene.player(), scene.dozer().getId(), "Barracks", PLACE,
                0f));
        for (int frame = 0; frame < 30; frame++) {
            scene.world().update();
        }
        assertEquals(1, placed.size(), "once");
        assertEquals("Barracks", placed.getFirst().getTemplate().name(), "with the site");
        assertEquals(List.of(ordered), frames, "the frame it was put down");
        assertSame(placed.getFirst(), scene.world().getObjects().stream()
                .filter(one -> one.getTemplate().name().equals("Barracks")).findFirst().orElseThrow());
    }
}
