package uz.dukeengine.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleFactory;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

/**
 * Things far from every waker sleep: their modules do not run and they keep all they were, decided on the rule's frames
 * from where things stand — and a frame costs what is awake.
 */
class SleepTest {

    /** Counts its frames: what a brain, a step or a timer does each frame, a sleeper does not. */
    static final class Counter extends UpdateModule {
        record Data() implements ModuleData {
        }

        int frames;

        Counter(GameObject owner) {
            super(owner);
        }

        @Override
        public void update() {
            frames++;
        }
    }

    /** Strikes its mark once, on a frame the test names: an awake thing's blow landing on a sleeper mid-update. */
    static final class Striker extends UpdateModule {
        record Data() implements ModuleData {
        }

        GameObject mark;
        int onFrame = -1;

        Striker(GameObject owner) {
            super(owner);
        }

        @Override
        public void update() {
            if (mark != null && getOwner().getWorld().getFrame() == onFrame) {
                mark.getBody().damage(1f);
            }
        }
    }

    private static final class World extends GameLogic {
        World() {
            super(new ThingFactory(ModuleFactory.withDefaults()
                    .register(Counter.Data.class, (owner, data) -> new Counter(owner))
                    .register(Striker.Data.class, (owner, data) -> new Striker(owner))));
        }

        @Override
        protected void simulate() {
        }
    }

    private static World world() {
        var world = new World();
        world.init();
        world.getThingFactory().addTemplate(ThingTemplate.named("Hero").module(new Counter.Data())
                .module(new Striker.Data()).build());
        world.getThingFactory().addTemplate(ThingTemplate.named("Monster").module(new Counter.Data())
                .module(new ActiveBody.Data(10f)).build());
        world.setSleep(new Sleep(thing -> thing.getTemplate().name().equals("Hero"), 5, 10));
        return world;
    }

    /** A thing of {@code template} standing in cell {@code (cx, cy)} of the world's ten-unit cells. */
    private static GameObject put(World world, String template, int cx, int cy) {
        var thing = world.createObject(world.getThingFactory().findTemplate(template));
        thing.setPosition(new Coord3D(cx * 10f + 5f, cy * 10f + 5f, 0f));
        return thing;
    }

    private static int frames(GameObject thing) {
        return thing.findModule(Counter.class).frames;
    }

    private static void run(World world, int frames) {
        for (int i = 0; i < frames; i++) {
            world.update();
        }
    }

    @Test
    void aThingFarFromEveryWakerSleepsAndOneNearOneWakes() {
        var world = world();
        var hero = put(world, "Hero", 0, 0);
        var near = put(world, "Monster", 5, -5);
        var far = put(world, "Monster", 6, 0);
        run(world, 10);
        assertEquals(10, frames(hero));
        assertEquals(10, frames(near), "five cells off, the longer way across: awake");
        assertEquals(0, frames(far), "six: asleep, from the first frame");
        assertTrue(world.isAsleep(far));
        assertFalse(world.isAsleep(near));
    }

    @Test
    void aSleeperWakesOnTheRulesNextFrameAfterAWakerComesNearAndKeepsWhatItWas() {
        var world = world();
        var hero = put(world, "Hero", 0, 0);
        var far = put(world, "Monster", 30, 0);
        run(world, 12);
        hero.setPosition(new Coord3D(26 * 10f + 5f, 5f, 0f));
        run(world, 7); // frames 12 to 18: still asleep, the rule decides on 20
        assertEquals(0, frames(far));
        run(world, 1); // frame 19
        assertEquals(0, frames(far));
        run(world, 1); // frame 20: decided, and awake for it
        assertEquals(1, frames(far));

        hero.setPosition(new Coord3D(5f, 5f, 0f));
        run(world, 10); // frames 21 to 30: awake until 30, asleep from it
        assertEquals(10, frames(far));
        run(world, 30);
        assertEquals(10, frames(far), "and its count stands where it was");
    }

    @Test
    void aThingMadeBetweenDecisionsIsAwakeUntilTheNext() {
        var world = world();
        put(world, "Hero", 0, 0);
        run(world, 3);
        var late = put(world, "Monster", 40, 40);
        run(world, 7); // frames 3 to 9
        assertEquals(7, frames(late));
        run(world, 5); // frame 10 decides it asleep
        assertEquals(7, frames(late));
    }

    @Test
    void twoWorldsGivenTheSameMovesSleepAndWakeTheSameThingsOnTheSameFrames() {
        var one = world();
        var two = world();
        var worlds = List.of(one, two);
        var heroes = new ArrayList<GameObject>();
        for (var world : worlds) {
            heroes.add(put(world, "Hero", 0, 0));
            for (int i = 1; i <= 40; i++) {
                put(world, "Monster", i * 3, (i * 7) % 11);
            }
        }
        for (int frame = 0; frame < 200; frame++) {
            for (int w = 0; w < 2; w++) {
                heroes.get(w).setPosition(new Coord3D(frame * 0.6f * 10f, 20f, 0f)); // walking east, a cell in five
                worlds.get(w).update();
            }
            var asleepOne = one.getObjects().stream().map(one::isAsleep).toList();
            var asleepTwo = two.getObjects().stream().map(two::isAsleep).toList();
            assertEquals(asleepOne, asleepTwo, "frame " + frame);
        }
        assertEquals(one.checksum(), two.checksum());
    }

    @Test
    void fiveThousandSleepersCostNoFrameOfTheirOwn() {
        var world = world();
        var hero = put(world, "Hero", 0, 0);
        var near = new ArrayList<GameObject>();
        for (int i = 0; i < 10; i++) {
            near.add(put(world, "Monster", i % 5, i / 5));
        }
        var far = new ArrayList<GameObject>();
        for (int i = 0; i < 5000; i++) {
            far.add(put(world, "Monster", 100 + (i % 70) * 10, 100 + (i / 70) * 10));
        }
        run(world, 60);
        assertEquals(60, frames(hero));
        assertTrue(near.stream().allMatch(thing -> frames(thing) == 60));
        assertTrue(far.stream().allMatch(thing -> frames(thing) == 0), "not one frame run of five thousand");
    }

    @Test
    void aSleeperDestroyedOrStruckDownLeavesAtOnceAndOneStruckWakesUntilTheRuleDecidesAgain() {
        var world = world();
        put(world, "Hero", 0, 0);
        var destroyed = put(world, "Monster", 30, 0);
        var struck = put(world, "Monster", 40, 0);
        var killed = put(world, "Monster", 50, 0);
        run(world, 12); // frames 0 to 11, decided at 0 and 10
        assertTrue(world.isAsleep(struck));

        world.destroyObject(destroyed);
        struck.getBody().damage(1f);
        killed.getBody().damage(1000f);
        assertFalse(world.isAsleep(struck), "struck, it wakes");
        run(world, 1); // frame 12
        assertNull(world.findObject(destroyed.getId()), "destroyed asleep, gone the next frame");
        assertNull(world.findObject(killed.getId()), "a blow that kills wakes it to die");
        assertEquals(1, frames(struck), "awake from the frame after the blow");
        run(world, 8); // frames 13 to 20: 20 decides, and it is still far from every waker
        assertEquals(8, frames(struck));
        assertTrue(world.isAsleep(struck));
    }

    @Test
    void aBlowLandingOnASleeperWhileThingsUpdateWakesItFromTheNextFrame() {
        var world = world();
        var hero = put(world, "Hero", 0, 0);
        var far = put(world, "Monster", 30, 0);
        var striker = hero.findModule(Striker.class);
        striker.mark = far;
        striker.onFrame = 14;
        run(world, 15); // frames 0 to 14: struck on 14, while the things awake were being updated
        assertEquals(0, frames(far), "not updated the frame it was struck");
        assertFalse(world.isAsleep(far));
        run(world, 3); // frames 15 to 17
        assertEquals(3, frames(far));
    }

    @Test
    void aSleeperThatDiesOfNoBlowLeavesOnTheRulesNextFrame() {
        var world = world();
        put(world, "Hero", 0, 0);
        var far = put(world, "Monster", 30, 0);
        run(world, 12);
        far.getBody().setHealth(0f); // no blow: nothing wakes it
        run(world, 8); // frames 12 to 19
        assertEquals(far, world.findObject(far.getId()), "asleep, its death is not looked for yet");
        run(world, 1); // frame 20 decides who sleeps, and looks at every thing
        assertNull(world.findObject(far.getId()));
    }

    @Test
    void withNoRuleNothingSleeps() {
        var world = world();
        world.setSleep(null);
        var far = put(world, "Monster", 500, 500);
        run(world, 5);
        assertEquals(5, frames(far));
        assertFalse(world.isAsleep(far));
    }
}
