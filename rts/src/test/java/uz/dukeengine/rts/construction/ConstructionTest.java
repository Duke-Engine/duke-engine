package uz.dukeengine.rts.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.pathfind.HeightMap;
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
 * Building in the world, headless: an order, a builder walking, a site rising the frame it arrives and a
 * building standing a build time later — or a refusal that costs nothing.
 *
 * <p>No game's data. A dozer and a barracks written here, so what is proved is the mechanism.
 */
class ConstructionTest {

    /** Ten seconds of work at SAGE's thirty frames a second. */
    private static final int BUILD_FRAMES = 300;
    private static final int COST = 500;

    /** The orders a window would send, applied as a game applies them. */
    private static final class World extends RtsSimulation {

        World(ThingFactory factory) {
            super(factory);
        }

        @Override
        protected void onRtsCommand(GameMessage command) {
            switch (command) {
                case GameMessage.Construct build -> construct(build);
                case GameMessage.CancelConstruction cancel -> cancelConstruction(cancel);
                case GameMessage.MoveTo move -> move.units().forEach(id ->
                        findObject(id).findModule(MoveUpdate.class).moveTo(move.destination()));
                default -> {
                }
            }
        }

        @Override
        protected void simulate() {
        }
    }

    private record Scene(World world, int player, GameObject dozer) {
    }

    private static Scene scene(PathGrid grid) {
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(RtsTemplate.named("Dozer")
                .geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(100f))
                .module(new MoveUpdate.Data(30f))
                .build());
        factory.addTemplate(RtsTemplate.named("Barracks")
                .geometry(new Geometry.Box(9f, 9f, 16f))
                .module(new ActiveBody.Data(600f))
                .buildCost(COST)
                .buildTimeFrames(BUILD_FRAMES)
                .build());
        var world = new World(factory);
        world.init();
        world.setPathGrid(grid);
        world.setPlacementRules(new PlacementRules(10f, 30f, 0.5f, 0.1f));
        int player = world.getPlayerList().addPlayer("Builder").getIndex();
        world.getRtsPlayer(player).deposit(1000);
        var dozer = world.createObject(factory.findTemplate("Dozer"));
        dozer.setPlayerIndex(player);
        dozer.setPosition(new Coord3D(100f, 100f, 0f));
        return new Scene(world, player, dozer);
    }

    private static Scene scene() {
        return scene(new PathGrid(40, 40));
    }

    private static final Coord3D PLACE = new Coord3D(205f, 205f, 0f);

    private static GameObject barracks(World world) {
        return world.getObjects().stream()
                .filter(one -> one.getTemplate().name().equals("Barracks")).findFirst().orElse(null);
    }

    private static void build(Scene scene, Coord3D place) {
        scene.world().issueCommand(new GameMessage.Construct(scene.player(), scene.dozer().getId(),
                "Barracks", place, 0f));
    }

    @Test
    void theMoneyGoesTheFrameTheOrderIsAccepted() {
        var scene = scene();
        build(scene, PLACE);
        assertEquals(1000, scene.world().getRtsPlayer(scene.player()).getMoney(), "sent, not yet applied");

        scene.world().update();
        assertEquals(1000 - COST, scene.world().getRtsPlayer(scene.player()).getMoney(), "taken as it is applied");
        assertNull(barracks(scene.world()), "and nothing stands there yet: the builder is on his way");
    }

    @Test
    void aSiteRisesTheFrameTheBuilderArrivesAndIsABuildingABuildTimeLater() {
        var scene = scene();
        build(scene, PLACE);
        int frame = 0;
        while (barracks(scene.world()) == null && frame < 1000) {
            scene.world().update();
            frame++;
        }
        var site = barracks(scene.world());
        assertNotNull(site, "a site rose");
        assertTrue(site.getWorld().isBeside(scene.dozer(), site), "the frame he arrived beside it");
        assertTrue(site.hasStatus(ObjectStatus.UNDER_CONSTRUCTION));
        assertEquals(60f, site.getBody().getHealth(), 1e-3f, "at a tenth of its health");
        assertEquals(scene.player(), site.getPlayerIndex(), "and his");

        for (int work = 0; work < BUILD_FRAMES; work++) {
            assertTrue(site.hasStatus(ObjectStatus.UNDER_CONSTRUCTION), "still going up at frame " + work);
            scene.world().update();
        }
        assertFalse(site.hasStatus(ObjectStatus.UNDER_CONSTRUCTION), "whole, a build time later");
        assertEquals(600f, site.getBody().getHealth(), 0.05f, "and at its full health");
    }

    @Test
    void aPlaceOverAnotherBuildingIsRefusedAndCostsNothing() {
        var scene = scene();
        var inTheWay = scene.world().createObject(scene.world().findTemplate("Barracks"));
        inTheWay.setPosition(PLACE);

        build(scene, new Coord3D(PLACE.x() + 5f, PLACE.y(), 0f));
        scene.world().update();

        assertEquals(1000, scene.world().getRtsPlayer(scene.player()).getMoney());
        assertEquals(Placement.Fit.IN_THE_WAY, scene.world().fits("Barracks", PLACE, 0f));
    }

    @Test
    void aPlaceOnASteepSlopeIsRefusedAndCostsNothing() {
        var grid = new PathGrid(40, 40);
        var steps = new int[41 * 41];
        for (int row = 0; row < 41; row++) {
            for (int column = 0; column < 41; column++) {
                steps[row * 41 + column] = column * 12; // 7.5 units a cell: over the barracks' 18 units, 13.5 > 10
            }
        }
        grid.setRelief(new HeightMap(41, 41, steps));
        var scene = scene(grid);

        build(scene, PLACE);
        scene.world().update();

        assertEquals(1000, scene.world().getRtsPlayer(scene.player()).getMoney());
        assertEquals(Placement.Fit.TOO_STEEP, scene.world().fits("Barracks", PLACE, 0f));
    }

    @Test
    void tooNearTheEdgeIsRefused() {
        assertEquals(Placement.Fit.TOO_NEAR_THE_EDGE,
                scene().world().fits("Barracks", new Coord3D(25f, 200f, 0f), 0f), "30 units kept clear");
        assertEquals(Placement.Fit.OFF_THE_MAP, scene().world().fits("Barracks", new Coord3D(3f, 200f, 0f), 0f));
        assertEquals(Placement.Fit.FITS, scene().world().fits("Barracks", PLACE, 0f));
    }

    @Test
    void aSiteDestroyedHalfBuiltLeavesNothing() {
        var scene = scene();
        build(scene, PLACE);
        while (barracks(scene.world()) == null) {
            scene.world().update();
        }
        for (int work = 0; work < BUILD_FRAMES / 2; work++) {
            scene.world().update();
        }
        barracks(scene.world()).getBody().damage(10_000f);
        scene.world().update();

        assertNull(barracks(scene.world()), "gone, and nothing in its place");
        assertEquals(1000 - COST, scene.world().getRtsPlayer(scene.player()).getMoney(), "and no refund for it");
    }

    @Test
    void aSiteCalledOffGivesBackTheShareTheGameSays() {
        var scene = scene();
        build(scene, PLACE);
        while (barracks(scene.world()) == null) {
            scene.world().update();
        }
        var site = barracks(scene.world());
        scene.world().issueCommand(new GameMessage.CancelConstruction(scene.player(), site.getId()));
        scene.world().update();

        assertNull(barracks(scene.world()));
        assertEquals(1000 - COST + COST / 2, scene.world().getRtsPlayer(scene.player()).getMoney(), "half back");
    }

    @Test
    void aBuilderSentElsewhereGivesTheOrderUpAndTheMoneyBack() {
        var scene = scene();
        build(scene, PLACE);
        scene.world().update();
        scene.world().issueCommand(new GameMessage.MoveTo(scene.player(), List.of(scene.dozer().getId()),
                new Coord3D(50f, 50f, 0f)));
        for (int frame = 0; frame < 5; frame++) {
            scene.world().update();
        }

        assertEquals(1000, scene.world().getRtsPlayer(scene.player()).getMoney(), "nothing was built");
        assertNull(barracks(scene.world()));
    }

    // ---- a box builder beside its own site ----

    /** A barracks-sized box site and a dozer that is a box too, turning slowly, as the RTS it was found in. */
    private static Scene boxScene(float dozerX, float dozerFacing) {
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(RtsTemplate.named("Dozer")
                .geometry(new Geometry.Box(7.5f, 5f, 6f))
                .module(new ActiveBody.Data(100f))
                .module(new MoveUpdate.Data(30f, 90f))
                .build());
        factory.addTemplate(RtsTemplate.named("Barracks")
                .geometry(new Geometry.Box(27.5f, 22.5f, 16f))
                .module(new ActiveBody.Data(600f))
                .buildCost(COST)
                .buildTimeFrames(BUILD_FRAMES)
                .build());
        var world = new World(factory);
        world.init();
        world.setPathGrid(new PathGrid(40, 40));
        world.setPlacementRules(new PlacementRules(10f, 30f, 0.5f, 0.1f));
        int player = world.getPlayerList().addPlayer("Builder").getIndex();
        world.getRtsPlayer(player).deposit(1000);
        var dozer = world.createObject(factory.findTemplate("Dozer"));
        dozer.setPlayerIndex(player);
        dozer.setPosition(new Coord3D(dozerX, PLACE.y(), 0f));
        dozer.setOrientation((float) StrictMath.toRadians(dozerFacing));
        return new Scene(world, player, dozer);
    }

    /** The barracks' right-hand wall stands at 205 + 27.5; the dozer's left end is 7.5 behind its middle. */
    private static final float WALL = PLACE.x() + 27.5f + 7.5f;

    /**
     * Put down right beside the builder — where a player very often puts a building — the site rises and the
     * builder does not move. It used to take a step first, and that step carried it into the footprint, where
     * it counted as standing on the spot and was sent out again.
     */
    @Test
    void aBoxBuilderAlreadyBesideTheSiteRaisesItWithoutMoving() {
        var scene = boxScene(WALL + 0.5f, 0f); // half a unit clear of the wall, facing away from it
        var before = scene.dozer().getPosition();
        build(scene, PLACE);
        scene.world().update();

        assertNotNull(barracks(scene.world()), "the site rose the frame the order was applied");
        assertEquals(before, scene.dozer().getPosition(), "and the builder never moved");
        assertEquals(1000 - COST, scene.world().getRtsPlayer(scene.player()).getMoney());
    }

    /** Standing a unit inside where the building goes, the builder steps out of it and raises it. */
    @Test
    void aBoxBuilderStandingOnTheSpotStepsOutAndRaisesIt() {
        for (float facing : new float[] {0f, 180f}) { // facing away from the site, and toward it
            var scene = boxScene(WALL - 1f, facing);
            build(scene, PLACE);
            for (int frame = 0; frame < 30 * 10 && barracks(scene.world()) == null; frame++) {
                scene.world().update();
            }
            var site = barracks(scene.world());
            assertNotNull(site, "risen, facing " + facing);
            float gap = uz.dukeengine.core.thing.Footprint.of(scene.dozer())
                    .separation(uz.dukeengine.core.thing.Footprint.of(site));
            assertTrue(gap >= 0f && gap <= 10f, "with the builder beside it, not in it: " + gap);
            assertEquals(1000 - COST, scene.world().getRtsPlayer(scene.player()).getMoney(), "and paid for once");
        }
    }

    /** The same orders on two machines build the same site on the same frame, and every frame after. */
    @Test
    void twoRunsOfTheSameOrdersEndOnTheSameHash() {
        var one = scene();
        var two = scene();
        build(one, PLACE);
        build(two, PLACE);
        for (int frame = 0; frame < 600; frame++) {
            one.world().update();
            two.world().update();
            assertEquals(one.world().checksum(), two.world().checksum(), "diverged at frame " + frame);
        }
        assertNotNull(barracks(one.world()));
    }
}
