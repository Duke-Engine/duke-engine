package uz.dukeengine.rts.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.SightCells.Sight;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.RtsSimulation;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.rts.module.RtsModules;

/**
 * A site refused on ground its side has never seen — the reference's {@code LBC_SHROUD}, which every path a person's
 * click reaches asks and a computer's own site search never does. Seen and in sight both build.
 */
class SeenGroundTest {

    /** The middle of sight cell (7, 7): no looker of the side's has been near it at the start. */
    private static final Coord3D PLACE = new Coord3D(300f, 300f, 0f);

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

        Placement.Fit fit() {
            return world.fits(player, "Barracks", PLACE, 0f);
        }

        boolean ordered() {
            int before = world.getRtsPlayer(player).getMoney();
            world.issueCommand(new GameMessage.Construct(player, dozer.getId(), "Barracks", PLACE, 0f));
            world.update();
            return world.getRtsPlayer(player).getMoney() < before;
        }

        void run(int frames) {
            for (int frame = 0; frame < frames; frame++) {
                world.update();
            }
        }
    }

    private static Scene scene(PlacementRules.SeenGround whose) {
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(RtsTemplate.named("Dozer").geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(100f)).module(new MoveUpdate.Data(30f)).build());
        factory.addTemplate(RtsTemplate.named("Scout").visionRange(30f).geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(100f)).build());
        factory.addTemplate(RtsTemplate.named("Barracks").geometry(new Geometry.Box(9f, 9f, 16f))
                .module(new ActiveBody.Data(600f)).buildCost(100).buildTimeFrames(300).build());
        var world = new World(factory);
        world.init();
        world.setPathGrid(new PathGrid(40, 40));
        world.setSightCells(40f, 150);
        world.setPlacementRules(new PlacementRules(10f, 30f, 0.5f, 0.1f).seenGround(whose));
        int player = world.getPlayerList().addPlayer("Builder").getIndex();
        world.getRtsPlayer(player).deposit(1000);
        var dozer = world.spawn(factory.findTemplate("Dozer"), new Coord3D(100f, 100f, 0f), player);
        world.update();
        return new Scene(world, player, dozer);
    }

    @Test
    void aSiteOnGroundItsSideHasNeverSeenIsRefusedAndOnceSeenIsAllowed() {
        var scene = scene(PlacementRules.SeenGround.PEOPLE);
        assertEquals(Placement.Fit.ON_UNSEEN_GROUND, scene.fit(), "the shroud's own reason");
        assertFalse(scene.ordered(), "and his order refused, at no cost");

        var scout = scene.world().spawn(scene.world().findTemplate("Scout"), new Coord3D(330f, 300f, 0f),
                scene.player()); // a cell off, looking a cell
        scene.run(1);
        assertEquals(Sight.IN_SIGHT, scene.world().getSightCells().sight(scene.player(), PLACE));
        assertEquals(Placement.Fit.FITS, scene.fit(), "in sight, it builds");

        scout.markDestroyed();
        scene.run(200);
        assertEquals(Sight.SEEN, scene.world().getSightCells().sight(scene.player(), PLACE));
        assertEquals(Placement.Fit.FITS, scene.fit(), "seen and no longer in sight, it builds as well");
        assertTrue(scene.ordered());
    }

    @Test
    void aComputersSiteIsSoughtWithTheShroudLeftOutUnlessTheRulesAskItOfEverySide() {
        var people = scene(PlacementRules.SeenGround.PEOPLE);
        people.world().getRtsPlayer(people.player()).setComputer(true);
        assertEquals(Placement.Fit.FITS, people.fit(), "a computer builds on ground it has never seen");
        assertTrue(people.ordered());

        var everyone = scene(PlacementRules.SeenGround.EVERY_SIDE);
        everyone.world().getRtsPlayer(everyone.player()).setComputer(true);
        assertEquals(Placement.Fit.ON_UNSEEN_GROUND, everyone.fit(), "unless the game asks it of every side");
    }

    @Test
    void nobodysSitesAreAskedAndNoSidesQuestionAsksNot() {
        var nobody = scene(PlacementRules.SeenGround.NOBODY);
        assertEquals(Placement.Fit.FITS, nobody.fit(), "rules that say nothing build anywhere, as ever");

        var people = scene(PlacementRules.SeenGround.PEOPLE);
        assertEquals(Placement.Fit.FITS, people.world().fits("Barracks", PLACE, 0f), "a question for no side");
    }

    @Test
    void aMapMarkedSeenAtTheStartIsGroundItsSideBuildsOn() {
        var scene = scene(PlacementRules.SeenGround.PEOPLE);
        scene.world().markMapSeen(scene.player());
        assertEquals(Placement.Fit.FITS, scene.fit());
    }
}
