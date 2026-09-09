package uz.duke.core.thing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.core.module.ActiveBody;
import uz.duke.core.module.Locomotor;
import uz.duke.core.module.Module;
import uz.duke.core.module.UpdateModule;

/**
 * Composition that goes both ways: a unit can stop being what it was.
 *
 * <p>Modules could only ever be added, which quietly assumed a thing never
 * changes — fine while every unit is built once from a template and stays that
 * unit, and wrong for anything that grows, is upgraded, or loses a weapon.
 *
 * <p>What matters as much as removal working is that the frame's order survives
 * it. Modules tick in attachment order, and that order is part of what makes two
 * machines agree; a replacement that quietly went to the back of the queue would
 * reorder the simulation without anyone noticing until a desync.
 */
class ModuleCompositionTest {

    /** Records the order in which modules were ticked. */
    private static final class Ticker extends UpdateModule {
        private final String name;
        private final List<String> log;

        Ticker(GameObject owner, String name, List<String> log) {
            super(owner);
            this.name = name;
            this.log = log;
        }

        @Override
        public void update() {
            log.add(name);
        }
    }

    private static final class Legs extends UpdateModule implements Locomotor {
        Legs(GameObject owner) {
            super(owner);
        }

        @Override
        public void update() {
            // stands there
        }
    }

    private static GameObject object() {
        return new GameObject(new ObjectId(1), ThingTemplate.named("Thing").build());
    }

    @Test
    void aModuleCanBeTakenOffAgain() {
        var thing = object();
        var log = new ArrayList<String>();
        var first = new Ticker(thing, "first", log);
        var second = new Ticker(thing, "second", log);
        thing.addModule(first);
        thing.addModule(second);

        assertTrue(thing.removeModule(first));
        thing.updateModules();

        assertEquals(List.of("second"), log, "the removed module should not tick");
        assertFalse(thing.removeModule(first), "and removing it twice is not a removal");
    }

    @Test
    void removingTheBodyLeavesTheObjectWithoutOne() {
        var thing = object();
        var body = new ActiveBody(thing, new ActiveBody.Data(100f));
        thing.addModule(body);
        assertSame(body, thing.getBody());

        thing.removeModule(body);

        assertNull(thing.getBody(), "the body should be gone, not stale");
    }

    /**
     * Whether a thing can move decides whether the pathfinder treats it as
     * terrain, so losing its legs has to be noticed.
     */
    @Test
    void losingTheLastLocomotorMakesAThingScenery() {
        var thing = object();
        var legs = new Legs(thing);
        thing.addModule(legs);
        assertTrue(thing.isMobile());

        thing.removeModule(legs);

        assertFalse(thing.isMobile(), "nothing left that can move it");
    }

    @Test
    void aSecondLocomotorKeepsAThingMobile() {
        var thing = object();
        var legs = new Legs(thing);
        thing.addModule(legs);
        thing.addModule(new Legs(thing));

        thing.removeModule(legs);

        assertTrue(thing.isMobile(), "the other one can still move it");
    }

    /** The point of replace over remove-then-add: the order does not shift. */
    @Test
    void aReplacementKeepsThePlaceInTheFrameOrder() {
        var thing = object();
        var log = new ArrayList<String>();
        var first = new Ticker(thing, "first", log);
        var middle = new Ticker(thing, "middle", log);
        var last = new Ticker(thing, "last", log);
        thing.addModule(first);
        thing.addModule(middle);
        thing.addModule(last);

        thing.replaceModule(middle, new Ticker(thing, "swapped", log));
        thing.updateModules();

        assertEquals(List.of("first", "swapped", "last"), log,
                "the replacement should tick where the old one did, not at the end");
    }

    @Test
    void replacingSomethingThatIsNotThereIsRefused() {
        var thing = object();
        var stranger = new Ticker(thing, "stranger", new ArrayList<>());

        assertThrows(IllegalArgumentException.class,
                () -> thing.replaceModule(stranger, new Ticker(thing, "new", new ArrayList<>())));
    }

    /** A replacement body takes over cleanly rather than colliding with the old one. */
    @Test
    void aBodyCanBeSwappedForAnother() {
        var thing = object();
        var original = new ActiveBody(thing, new ActiveBody.Data(100f));
        thing.addModule(original);

        var bigger = new ActiveBody(thing, new ActiveBody.Data(250f));
        thing.replaceModule(original, bigger);

        assertSame(bigger, thing.getBody());
        assertEquals(250f, thing.getBody().getMaxHealth(), 0.001f);
    }

    /**
     * Changing the module list from inside a module's own update fails loudly.
     * The alternative — quietly skipping whichever module shuffled into the gap —
     * is a determinism fault that would only surface as a desync much later.
     */
    @Test
    void restructuringDuringAnUpdateIsRefusedRatherThanSilentlyWrong() {
        var thing = object();
        var log = new ArrayList<String>();
        var victim = new Ticker(thing, "victim", log);
        thing.addModule(new UpdateModule(thing) {
            @Override
            public void update() {
                thing.removeModule(victim);
            }
        });
        thing.addModule(victim);

        assertThrows(java.util.ConcurrentModificationException.class, thing::updateModules);
    }

    @Test
    void modulesReportWhatIsStillAttached() {
        var thing = object();
        Module ticker = new Ticker(thing, "one", new ArrayList<>());
        thing.addModule(ticker);

        assertEquals(1, thing.getModules().size());
        thing.removeModule(ticker);
        assertEquals(0, thing.getModules().size());
        assertNull(thing.findModule(Ticker.class));
    }
}
