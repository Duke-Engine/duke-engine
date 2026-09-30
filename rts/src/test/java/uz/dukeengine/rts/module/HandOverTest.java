package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.combat.module.ExperienceModule;

/** A side's things handed to an ally mid-match: everything kept for an owner follows them, on every machine alike. */
class HandOverTest {

    private static final int HANDED_AT = 10;

    /** One machine's match: a side with a factory at work, a power plant and three veteran tanks, and its ally. */
    private static final class Match {
        final ProductionTest.TestLogic logic;
        final ThingTemplate tank;
        final GameObject factory;
        final GameObject plant;
        final List<GameObject> tanks = new ArrayList<>();
        final int usa;
        final int ally;

        Match() {
            var things = new ThingFactory(RtsModules.withDefaults());
            tank = RtsTemplate.named("Crusader").geometry(new Geometry.Cylinder(3f, 4f))
                    .module(new ActiveBody.Data(480f))
                    .module(ExperienceModule.Data.ofThresholds(50, 0, 100, 200))
                    .buildCost(100).buildTimeFrames(20).build();
            var warFactory = RtsTemplate.named("WarFactory").geometry(new Geometry.Box(20f, 20f, 10f))
                    .module(new ActiveBody.Data(2000f)).module(new ProductionUpdate.Data()).build();
            var reactor = RtsTemplate.named("PowerPlant").geometry(new Geometry.Box(10f, 10f, 10f))
                    .module(new ActiveBody.Data(800f)).module(new PowerModule.Data(5, 0)).build();
            things.addTemplate(tank);
            things.addTemplate(warFactory);
            things.addTemplate(reactor);
            logic = new ProductionTest.TestLogic(things);
            logic.init();
            usa = logic.getPlayerList().addPlayer("USA").getIndex();
            ally = logic.getPlayerList().addPlayer("Ally").getIndex();
            logic.getRtsPlayer(usa).deposit(1000);
            factory = placed(warFactory, 100f, 100f);
            plant = placed(reactor, 160f, 100f);
            for (int one = 0; one < 3; one++) {
                var crusader = placed(tank, 100f + 20f * one, 200f);
                crusader.findModule(ExperienceModule.class).addExperience(100); // a veteran
                tanks.add(crusader);
            }
            factory.findModule(ProductionUpdate.class).queue(tank);
        }

        GameObject placed(ThingTemplate template, float x, float y) {
            var thing = logic.createObject(template);
            thing.setPlayerIndex(usa);
            thing.setPosition(new Coord3D(x, y, 0f));
            return thing;
        }

        void frame() {
            if (logic.getFrame() == HANDED_AT) {
                logic.handOverAll(usa, ally, true);
            }
            logic.update();
        }
    }

    @Test
    void theAllyOwnsAllFiveFromThatFrameAndTheQueueThePowerAndTheRanksGoWithThem() {
        var match = new Match();
        int veteran = match.tanks.getFirst().findModule(ExperienceModule.class).getLevel();
        while (match.logic.getFrame() <= HANDED_AT) {
            match.frame();
        }
        var five = new ArrayList<>(match.tanks);
        five.add(match.factory);
        five.add(match.plant);
        assertTrue(five.stream().allMatch(thing -> thing.getPlayerIndex() == match.ally), "the ally owns all five");
        assertEquals(5, PowerGrid.surplus(match.logic, match.ally), "its power counts the plant");
        assertEquals(0, PowerGrid.surplus(match.logic, match.usa));
        assertTrue(match.tanks.stream()
                .allMatch(one -> one.findModule(ExperienceModule.class).getLevel() == veteran && veteran > 0),
                "the tanks keep their rank");
        assertEquals(900, match.logic.getRtsPlayer(match.ally).getMoney(), "and the money with them");
        assertEquals(0, match.logic.getRtsPlayer(match.usa).getMoney());

        for (int frame = 0; frame < 20; frame++) {
            match.frame();
        }
        var built = match.logic.getObjects().stream()
                .filter(thing -> thing.getTemplate() == match.tank && !match.tanks.contains(thing)).toList();
        assertEquals(1, built.size(), "the factory's queue finished");
        assertEquals(match.ally, built.getFirst().getPlayerIndex(), "for the ally");
    }

    @Test
    void twoMachinesHandingOverOnTheSameFrameAgreeEveryFrame() {
        var here = new Match();
        var there = new Match();
        for (int frame = 0; frame < 60; frame++) {
            here.frame();
            there.frame();
            assertEquals(here.logic.checksum(), there.logic.checksum(), "frame " + frame);
        }
    }
}
