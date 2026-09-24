package uz.dukeengine.rts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplateLoader;
import uz.dukeengine.rts.construction.Construction;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.rts.module.ProductionUpdate;
import uz.dukeengine.rts.module.RtsModules;

/** What a side needs before it may make a thing, and how many it may have — decided by the simulation. */
class PrerequisitesTest {

    private static final class World extends RtsSimulation {
        World(ThingFactory factory) {
            super(factory);
        }

        @Override
        protected void onRtsCommand(GameMessage command) {
        }

        @Override
        protected void simulate() {
        }
    }

    private World world;
    private int side;

    @BeforeEach
    void setUp() {
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(RtsTemplate.named("WarFactory").module(new ActiveBody.Data(1000f))
                .module(new ProductionUpdate.Data()).buildCost(500).build());
        factory.addTemplate(RtsTemplate.named("SupplyCenter").module(new ActiveBody.Data(800f)).build());
        factory.addTemplate(RtsTemplate.named("SupplyDropZone").module(new ActiveBody.Data(800f)).build());
        factory.addTemplate(RtsTemplate.named("Crusader").module(new ActiveBody.Data(300f)).buildCost(900)
                .buildTimeFrames(30).requires("WarFactory").build());
        factory.addTemplate(RtsTemplate.named("Humvee").module(new ActiveBody.Data(200f)).buildCost(700)
                .requires("WarFactory").requires("SupplyCenter", "SupplyDropZone").build());
        factory.addTemplate(RtsTemplate.named("Paladin").module(new ActiveBody.Data(400f)).buildCost(1100)
                .requires("WarFactory").requiresWord("SCIENCE_PaladinTank").build());
        factory.addTemplate(RtsTemplate.named("Hero").module(new ActiveBody.Data(200f)).buildCost(1500)
                .buildTimeFrames(60).maxSimultaneous(1).build());
        factory.addTemplate(RtsTemplate.named("ParticleCannon").module(new ActiveBody.Data(2000f))
                .maxSimultaneousLinkKey("Superweapon").build());
        factory.addTemplate(RtsTemplate.named("NuclearMissile").module(new ActiveBody.Data(2000f))
                .maxSimultaneousLinkKey("Superweapon").build());
        factory.addTemplate(RtsTemplate.named("Spy").module(new ActiveBody.Data(100f))
                .buildability(Prerequisites.Buildability.ONLY_BY_COMPUTER).build());
        factory.addTemplate(RtsTemplate.named("Crate").module(new ActiveBody.Data(1f))
                .buildability(Prerequisites.Buildability.NO).build());
        factory.addTemplate(RtsTemplate.named("Beacon").module(new ActiveBody.Data(1f)).requires("Nothing")
                .maxSimultaneous(1).buildability(Prerequisites.Buildability.IGNORING_PREREQUISITES).build());
        factory.addTemplate(RtsTemplate.named("Dozer").module(new ActiveBody.Data(250f))
                .module(new MoveUpdate.Data(30f)).build());
        world = new World(factory);
        world.init();
        world.setPathGrid(new PathGrid(60, 60));
        side = world.getPlayerList().addPlayer("USA").getIndex();
        world.getRtsPlayer(side).deposit(10_000);
    }

    private GameObject stand(String template, float x) {
        return world.spawn(world.getThingFactory().findTemplate(template), new Coord3D(x, 100f, 0f), side);
    }

    @Test
    void theCrusaderIsRefusedUntilTheSideOwnsAFinishedWarFactory() {
        assertFalse(world.canBuild(side, "Crusader"), "no war factory");
        var factory = stand("WarFactory", 100f);
        factory.setStatus(ObjectStatus.UNDER_CONSTRUCTION);
        assertFalse(world.canBuild(side, "Crusader"), "a site still going up does not count");

        factory.clearStatus(ObjectStatus.UNDER_CONSTRUCTION);
        assertTrue(world.canBuild(side, "Crusader"), "finished, it does");

        factory.getBody().setHealth(0f);
        assertFalse(world.canBuild(side, "Crusader"), "and a dead one does not");
    }

    @Test
    void aRequirementOfTwoAlternativesIsMetByEither() {
        stand("WarFactory", 100f);
        assertFalse(world.canBuild(side, "Humvee"));

        var dropZone = stand("SupplyDropZone", 200f);
        assertTrue(world.canBuild(side, "Humvee"), "the drop zone meets it");
        dropZone.getBody().setHealth(0f);
        stand("SupplyCenter", 300f);
        assertTrue(world.canBuild(side, "Humvee"), "and so does the supply centre");
    }

    @Test
    void aPaladinNeedsTheScienceToo() {
        stand("WarFactory", 100f);
        assertFalse(world.canBuild(side, "Paladin"));

        world.grant(side, "SCIENCE_PaladinTank");

        assertTrue(world.canBuild(side, "Paladin"));
    }

    @Test
    void aSecondHeroIsRefusedWhileTheFirstLivesOrIsQueuedAndNothingIsCharged() {
        var factory = stand("WarFactory", 100f);
        var line = factory.findModule(ProductionUpdate.class);
        var hero = world.getThingFactory().findTemplate("Hero");
        int purse = world.getRtsPlayer(side).getMoney();

        assertTrue(line.queue(hero));
        assertFalse(line.queue(hero), "one is queued already");
        assertEquals(purse - 1500, world.getRtsPlayer(side).getMoney(), "the refused one cost nothing");

        for (int frame = 0; frame < 70; frame++) {
            world.update();
        }
        assertFalse(line.isProducing(), "the first came out");
        assertFalse(line.queue(hero), "and while it lives, no second");

        world.getObjects().stream().filter(thing -> thing.getTemplate().name().equals("Hero")).findFirst()
                .orElseThrow().getBody().setHealth(0f);
        assertTrue(line.queue(hero), "gone, another may be made");
    }

    @Test
    void templatesSharingALinkKeyShareTheCapTheMatchSets() {
        assertTrue(world.canBuild(side, "NuclearMissile"), "no cap set: as many as it likes");
        world.setCap("Superweapon", 1);
        stand("ParticleCannon", 100f);

        assertFalse(world.canBuild(side, "NuclearMissile"), "one superweapon of any kind is the match's limit");
        world.setCap("Superweapon", 2);
        assertTrue(world.canBuild(side, "NuclearMissile"));
        world.setCap("Superweapon", -1);
        assertTrue(world.canBuild(side, "ParticleCannon"));
    }

    @Test
    void whoMayMakeAThingIsSaidOnIt() {
        assertFalse(world.canBuild(side, "Crate"), "buildable by nobody");
        assertFalse(world.canBuild(side, "Spy"), "only a computer player");
        world.getRtsPlayer(side).setComputer(true);
        assertTrue(world.canBuild(side, "Spy"));
        assertTrue(world.canBuild(side, "Beacon"), "ignoring what it needs");
        stand("Beacon", 100f);
        assertTrue(world.canBuild(side, "Beacon"), "and how many, as the reference ignores both");
    }

    @Test
    void aTemplateTheGameSaysCountsAsAnotherMeetsItsRequirement() {
        world.getThingFactory().addTemplate(RtsTemplate.named("WarFactoryReskin")
                .module(new ActiveBody.Data(1000f)).build());
        stand("WarFactoryReskin", 100f);
        assertFalse(world.canBuild(side, "Crusader"));

        world.countsAs((owned, wanted) -> owned.equals(wanted) || owned.equals(wanted + "Reskin"));

        assertTrue(world.canBuild(side, "Crusader"));
    }

    @Test
    void aConstructOrderForABuildingTheSideMayNotMakeCostsNothing() {
        var dozer = stand("Dozer", 100f);
        world.setCap("Superweapon", 0);
        int purse = world.getRtsPlayer(side).getMoney();

        boolean taken = Construction.order(world, new GameMessage.Construct(side, dozer.getId(), "ParticleCannon",
                new Coord3D(300f, 300f, 0f), 0f), world.getPlacementRules());

        assertFalse(taken);
        assertEquals(purse, world.getRtsPlayer(side).getMoney());
    }

    @Test
    void aFileSaysWhatAThingNeeds() {
        var factory = new ThingFactory(RtsModules.withDefaults());
        RtsTemplate.register(new ThingTemplateLoader(factory)).load("""
                Object
                  Name = Overlord
                  Prerequisites = [WarFactory, SupplyCenter | SupplyDropZone]
                  RequiredWords = [SCIENCE_Overlord]
                  Buildability = ONLY_BY_COMPUTER
                  MaxSimultaneous = 1
                  MaxSimultaneousLinkKey = Tank
                End
                """, "test");

        var overlord = (Prerequisites) factory.findTemplate("Overlord");

        assertEquals(List.of("WarFactory", "SupplyCenter | SupplyDropZone"), overlord.prerequisites());
        assertEquals(List.of("SupplyCenter", "SupplyDropZone"),
                Prerequisites.alternatives(overlord.prerequisites().get(1)));
        assertEquals(List.of("SCIENCE_Overlord"), overlord.requiredWords());
        assertEquals(Prerequisites.Buildability.ONLY_BY_COMPUTER, overlord.buildability());
        assertEquals(1, overlord.maxSimultaneous());
        assertEquals("Tank", overlord.maxSimultaneousLinkKey());
    }
}
