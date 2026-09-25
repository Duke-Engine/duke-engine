package uz.dukeengine.rts.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.RtsSimulation;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.rts.module.RtsModules;

/**
 * A builder keeps to its site until the site is whole — the reference's dozer keeping its build task
 * ({@code DOZER_TASK_BUILD}): it stays on ground it could not otherwise hold, is not asked aside by an ally's route,
 * and walks back to work when it is moved off.
 */
class BuilderKeepsToItsSiteTest {

    private static final int BUILD_FRAMES = 300;

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
        GameObject site() {
            return world.getObjects().stream().filter(one -> one.getTemplate().name().equals("Barracks"))
                    .findFirst().orElseThrow();
        }

        float progress() {
            return site().findModule(ConstructionSite.class).progress();
        }

        void run(int frames) {
            for (int frame = 0; frame < frames; frame++) {
                world.update();
            }
        }
    }

    private static MoveUpdate.Data treads() {
        return new MoveUpdate.Data(30f, 180f, 0f, 0f, 0f, 0f, 0.1f, 0f, 0f, 1f, false, MoveUpdate.Gait.TREADS);
    }

    /**
     * A dozer of radius 7 on treads standing at {@code x}, west of a barracks 200 long whose west face is at 184, put
     * down at its order: 176 is within a cell of its face with its block of ground over the barracks' own.
     */
    private static Scene scene(float x) {
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(RtsTemplate.named("Dozer").geometry(new Geometry.Cylinder(7f, 6f))
                .module(new ActiveBody.Data(100f)).module(treads()).build());
        factory.addTemplate(RtsTemplate.named("Barracks").geometry(new Geometry.Box(100f, 20f, 16f))
                .module(new ActiveBody.Data(600f)).buildCost(100).buildTimeFrames(BUILD_FRAMES).build());
        var world = new World(factory);
        world.init();
        world.setPathGrid(new PathGrid(60, 60));
        world.setPlacementRules(new PlacementRules(10f, 30f, 0.5f, 0.1f).siteAtOrder(true));
        int player = world.getPlayerList().addPlayer("Builder").getIndex();
        world.getRtsPlayer(player).deposit(1000);
        var dozer = world.createObject(factory.findTemplate("Dozer"));
        dozer.setPlayerIndex(player);
        dozer.setPosition(new Coord3D(x, 305f, 0f));
        world.issueCommand(new GameMessage.Construct(player, dozer.getId(), "Barracks", new Coord3D(284f, 305f, 0f),
                0f));
        return new Scene(world, player, dozer);
    }

    @Test
    void besideItsSiteItStaysWhereItStandsAndTheSiteGoesOnRising() {
        var scene = scene(176f);
        scene.run(1);
        scene.world().letPlaceGo(scene.dozer()); // as one that walked there holds no ground of its own
        var standing = scene.dozer().getPosition();
        scene.run(BUILD_FRAMES + 10);
        assertEquals(standing, scene.dozer().getPosition(), "it kept where it stood");
        assertFalse(scene.site().hasStatus(ObjectStatus.UNDER_CONSTRUCTION), "and the site is whole");
    }

    @Test
    void anAllysRouteThroughItsGroundGoesRoundItAndTheSiteGoesOnRising() {
        var scene = scene(174f);
        var grid = scene.world().getPathGrid();
        for (int cy = 0; cy < 60; cy++) {
            grid.setBlocked(14, cy, true); // a wall west of it: the way past the barracks' west face is over its ground
        }
        scene.run(30);
        var standing = scene.dozer().getPosition();
        var ally = scene.world().createObject(scene.world().getThingFactory().findTemplate("Dozer"));
        ally.setPlayerIndex(scene.player());
        ally.setPosition(new Coord3D(165f, 150f, 0f));
        ally.findModule(MoveUpdate.class).moveTo(new Coord3D(165f, 460f, 0f));
        scene.run(BUILD_FRAMES);
        assertEquals(standing, scene.dozer().getPosition(), "not asked aside");
        assertFalse(scene.site().hasStatus(ObjectStatus.UNDER_CONSTRUCTION), "the site went on rising");
        scene.run(1500);
        assertTrue(ally.getPosition().y() > 440f, "and the ally went round it: " + ally.getPosition());
    }

    @Test
    void shovedOffItsSiteItWalksBackAndTheSiteIsWholeWithinItsBuildTimeAndTheWalk() {
        var scene = scene(174f);
        scene.run(32);
        assertTrue(scene.progress() > 0.09f, "10% up: " + scene.progress());
        scene.dozer().setPosition(new Coord3D(160f, 305f, 0f)); // 17 off: not beside it
        scene.run(BUILD_FRAMES - 30 + 30);
        assertFalse(scene.site().hasStatus(ObjectStatus.UNDER_CONSTRUCTION), "whole within its time and the walk");
        assertTrue(scene.world().isBeside(scene.dozer(), scene.site()), "and it walked back beside it");
    }
}
