package uz.dukeengine.rts.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.rts.RtsSimulation;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.rts.module.BuildLength;
import uz.dukeengine.rts.module.ProductionUpdate;
import uz.dukeengine.rts.module.RtsModules;

/**
 * How long a site and a factory's job take, asked every frame — the reference's {@code calcTimeToBuild}, worked out
 * from the side's power: slow while it is short, fast from the frame it is back, the work done kept.
 */
class BuildLengthTest {

    /** Says the length the test sets, or nothing at 0. */
    static final class Power extends Module implements BuildLength {
        int frames;

        Power(GameObject owner) {
            super(owner);
        }

        @Override
        public int framesToBuild(ThingTemplate thing) {
            return frames;
        }
    }

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

    private static World world(ThingFactory factory) {
        var world = new World(factory);
        world.init();
        world.setPathGrid(new PathGrid(60, 60));
        world.setPlacementRules(new PlacementRules(10f, 30f, 0.5f, 0.1f).siteAtOrder(true));
        return world;
    }

    @Test
    void aSiteRisesByTheLengthOfEachFrameItIsWorkedAndKeepsWhatWasDone() {
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(RtsTemplate.named("Dozer").geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(100f)).module(new MoveUpdate.Data(30f)).build());
        factory.addTemplate(RtsTemplate.named("Barracks").geometry(new Geometry.Box(20f, 20f, 16f))
                .module(new ActiveBody.Data(1000f)).buildCost(100).buildTimeFrames(300).build());
        var world = world(factory);
        int player = world.getPlayerList().addPlayer("Builder").getIndex();
        world.getRtsPlayer(player).deposit(1000);
        var dozer = world.createObject(factory.findTemplate("Dozer"));
        dozer.setPlayerIndex(player);
        dozer.setPosition(new Coord3D(186f, 300f, 0f));
        world.issueCommand(new GameMessage.Construct(player, dozer.getId(), "Barracks", new Coord3D(210f, 300f, 0f),
                0f));
        world.update();
        var site = world.getObjects().stream().filter(one -> one.getTemplate().name().equals("Barracks"))
                .findFirst().orElseThrow();
        var power = new Power(site);
        power.frames = 600;
        site.addModule(power);
        var building = site.findModule(ConstructionSite.class);

        while (building.progress() < 0.5f - 1e-4f) {
            world.update();
        }
        assertEquals(0.5f, building.progress(), 1e-3f, "at half, its 600 frames' length being half run");
        assertEquals(100f + 900f * 0.5f, site.getBody().getHealth(), 2f, "with half the health it rises by");

        power.frames = 300;
        int frames = 0;
        while (site.hasStatus(ObjectStatus.UNDER_CONSTRUCTION) && frames < 400) {
            world.update();
            frames++;
        }
        assertEquals(150, frames, "given 300 from then, whole 150 worked frames later");
    }

    private record Factory(RtsSimulation world, GameObject barracks, ThingTemplate soldier, Power power) {
        void run(int frames) {
            for (int frame = 0; frame < frames; frame++) {
                world.update();
            }
        }

        float progress() {
            var entries = barracks.findModule(ProductionUpdate.class).getEntries();
            return entries.isEmpty() ? -1f : entries.getFirst().progress();
        }
    }

    private static Factory factory() {
        var things = new ThingFactory(RtsModules.withDefaults());
        var soldier = RtsTemplate.named("Soldier").module(new ActiveBody.Data(50f)).buildCost(100)
                .buildTimeFrames(300).build();
        things.addTemplate(soldier);
        things.addTemplate(RtsTemplate.named("Barracks").module(new ActiveBody.Data(500f))
                .module(new ProductionUpdate.Data()).build());
        var world = world(things);
        int usa = world.getPlayerList().addPlayer("USA").getIndex();
        world.getRtsPlayer(usa).deposit(1000);
        var barracks = world.createObject(things.findTemplate("Barracks"));
        barracks.setPlayerIndex(usa);
        barracks.setPosition(new Coord3D(300f, 300f, 0f));
        var power = new Power(barracks);
        barracks.addModule(power);
        barracks.findModule(ProductionUpdate.class).queue(soldier);
        return new Factory(world, barracks, soldier, power);
    }

    @Test
    void aJobSlowedToTwiceItsLengthAndHalfRunIsDoneTheFrameItsLengthComesBack() {
        var line = factory();
        line.power().frames = 600;
        line.run(300);
        assertEquals(0.5f, line.progress(), 1e-4f);

        line.power().frames = 300;
        line.run(1);
        assertEquals(-1f, line.progress(), "done on its 301st frame");
    }

    @Test
    void aJobsProgressFallsBackWhenItsLengthGrowsAndItRunsOnToTheNewLength() {
        var line = factory();
        line.power().frames = 300;
        line.run(250);
        line.power().frames = 600;
        assertEquals(0.4167f, line.progress(), 1e-3f, "250 of 600");
        line.run(349);
        assertTrue(line.progress() > 0.99f, "not yet");
        line.run(1);
        assertEquals(-1f, line.progress(), "done 350 frames later");
    }

    @Test
    void noLengthGivenIsTheTemplatesBuildTime() {
        var line = factory();
        line.run(299);
        assertFalse(line.progress() < 0f);
        line.run(1);
        assertEquals(-1f, line.progress());
    }
}
