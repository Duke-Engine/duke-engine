package uz.dukeengine.rts.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.RtsSimulation;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.rts.module.RtsModules;

/** The words a site holds while it goes up, as its builder arrives, works, leaves and comes back. */
class SiteWordsTest {

    private static final PlacementRules.SiteWords WORDS = new PlacementRules.SiteWords(
            "AWAITING_CONSTRUCTION", "PARTIALLY_CONSTRUCTED", "ACTIVELY_BEING_CONSTRUCTED");
    private static final int FRAMES = 30;

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

    private static List<String> held(GameObject site) {
        var words = new TreeSet<String>();
        for (var word : List.of(WORDS.awaiting(), WORDS.partlyBuilt(), WORDS.beingBuilt())) {
            if (site.hasCondition(word)) {
                words.add(word.substring(0, word.indexOf('_')));
            }
        }
        return List.copyOf(words);
    }

    @Test
    void theWordsChangeAsABuilderArrivesWorksLeavesAndComesBackAndAreGoneOnceItIsWhole() {
        var factory = new ThingFactory(RtsModules.withDefaults());
        var dozerType = RtsTemplate.named("Dozer").geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(100f)).build();
        var barracksType = RtsTemplate.named("Barracks").geometry(new Geometry.Box(9f, 9f, 16f))
                .module(new ActiveBody.Data(600f)).buildTimeFrames(FRAMES).build();
        factory.addTemplate(dozerType);
        factory.addTemplate(barracksType);
        var world = new World(factory);
        world.init();
        var dozer = world.createObject(dozerType);
        var beside = new Coord3D(213f, 200f, 0f);
        var away = new Coord3D(400f, 400f, 0f);
        dozer.setPosition(away);
        var site = world.createObject(barracksType);
        site.setPosition(new Coord3D(200f, 200f, 0f));
        site.setStatus(ObjectStatus.UNDER_CONSTRUCTION);
        var rules = new PlacementRules(10f, 30f, 0.5f, 0.1f, WORDS);
        site.addModule(new ConstructionSite(site, dozer.getId(), 0, rules));

        world.update();
        assertEquals(List.of("AWAITING"), held(site), "put down, nobody at work on it yet");

        dozer.setPosition(beside);
        world.update();
        assertEquals(List.of("ACTIVELY", "PARTIALLY"), held(site), "its builder at work");

        dozer.setPosition(away);
        world.update();
        assertEquals(List.of("PARTIALLY"), held(site), "called away: begun, not being built");

        dozer.setPosition(beside);
        world.update();
        assertEquals(List.of("ACTIVELY", "PARTIALLY"), held(site), "back at it");

        for (int frame = 0; frame < FRAMES && site.hasStatus(ObjectStatus.UNDER_CONSTRUCTION); frame++) {
            world.update();
        }
        assertEquals(List.of(), held(site), "whole: none of the three");
    }

    @Test
    void rulesThatNameNoWordsLeaveASiteWithoutAny() {
        assertEquals(PlacementRules.SiteWords.NONE, new PlacementRules(10f, 30f, 0.5f, 0.1f).words());
    }
}
