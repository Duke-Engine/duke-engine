package uz.dukeengine.core.partition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ModuleFactory;
import uz.dukeengine.core.thing.Footprint;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

/**
 * The world's partition keeps its things by where they stand and asks only those near a place — and answers every
 * question as a look at every thing does, thing for thing and in the same order, as things come, move and go.
 */
class SpatialIndexTest {

    private static final class World extends GameLogic {
        World(ThingFactory things) {
            super(things);
        }

        @Override
        protected void simulate() {
        }
    }

    private record Field(World world, PartitionManager everyThing, List<ThingTemplate> kinds) {
    }

    private static Field field() {
        var things = new ThingFactory(ModuleFactory.withDefaults());
        List<ThingTemplate> kinds = List.of(
                ThingTemplate.named("Speck").build(),
                ThingTemplate.named("Soldier").geometry(new Geometry.Cylinder(4f, 10f)).build(),
                ThingTemplate.named("Barracks").geometry(new Geometry.Box(40f, 25f, 20f)).build(),
                ThingTemplate.named("Bridge").geometry(new Geometry.Box(300f, 20f, 5f)).build(),
                ThingTemplate.named("Rock").geometry(new Geometry.Sphere(12f)).build());
        kinds.forEach(things::addTemplate);
        var world = new World(things);
        world.init();
        return new Field(world, new PartitionManager(world::getObjects), kinds);
    }

    private static Coord3D anywhere(SplittableRandom random, float across) {
        return new Coord3D((float) random.nextDouble(-across, across), (float) random.nextDouble(-across, across),
                (float) random.nextDouble(0, 30));
    }

    @Test
    void everyQuestionIsAnsweredAsALookAtEveryThingAnswersItAsThingsComeMoveAndGo() {
        var field = field();
        var world = field.world();
        var random = new SplittableRandom(20260930L);
        var things = new ArrayList<GameObject>();
        for (int i = 0; i < 400; i++) {
            var thing = world.createObject(field.kinds().get(random.nextInt(field.kinds().size())));
            thing.setPosition(anywhere(random, 2000f));
            thing.setOrientation((float) random.nextDouble(0, 6.28));
            things.add(thing);
        }
        var indexed = world.getPartition();
        var plain = field.everyThing();
        for (int round = 0; round < 300; round++) {
            for (int move = 0; move < 20; move++) {
                var thing = things.get(random.nextInt(things.size()));
                var at = thing.getPosition();
                thing.setPosition(random.nextInt(4) == 0 ? anywhere(random, 2000f)
                        : new Coord3D(at.x() + (float) random.nextDouble(-90, 90),
                                at.y() + (float) random.nextDouble(-90, 90), at.z()));
            }
            var center = anywhere(random, 2100f);
            float range = (float) random.nextDouble(0, round % 50 == 0 ? 5000 : 400);
            PartitionFilter odd = thing -> thing.getId().value() % 3 != 0;
            assertEquals(plain.objectsInRange(center, range, odd), indexed.objectsInRange(center, range, odd));
            assertSame(plain.closestObject(center, range, odd), indexed.closestObject(center, range, odd));

            var asker = things.get(random.nextInt(things.size()));
            var footprint = Footprint.of(asker, center);
            PartitionFilter others = thing -> thing != asker;
            assertEquals(plain.objectsOverlapping(footprint, others), indexed.objectsOverlapping(footprint, others));
            assertSame(plain.firstOverlapping(footprint, others), indexed.firstOverlapping(footprint, others));
            float reach = (float) random.nextDouble(0, 250);
            assertSame(plain.closestWithinReach(footprint, reach, others),
                    indexed.closestWithinReach(footprint, reach, others));
        }
    }

    @Test
    void aThingThatLeavesIsFoundNoMoreByItsIdOrWhereItStood() {
        var field = field();
        var world = field.world();
        var soldier = world.createObject(field.kinds().get(1));
        soldier.setPosition(new Coord3D(500f, 500f, 0f));
        assertSame(soldier, world.findObject(soldier.getId()));
        assertEquals(List.of(soldier), world.objectsInRange(new Coord3D(505f, 500f, 0f), 10f, thing -> true));

        world.destroyObject(soldier);
        world.update();
        assertNull(world.findObject(soldier.getId()));
        assertEquals(List.of(), world.objectsInRange(new Coord3D(505f, 500f, 0f), 10f, thing -> true));
        assertNull(world.findObject(new ObjectId(9999)));
        assertNull(world.findObject(null));
    }

    @Test
    void aWorldClearedIsEmptyToEveryQuestion() {
        var field = field();
        var world = field.world();
        world.createObject(field.kinds().get(2)).setPosition(new Coord3D(10f, 10f, 0f));
        world.clearWorld();
        assertEquals(List.of(), world.objectsInRange(Coord3D.ZERO, 100f, thing -> true));
        var again = world.createObject(field.kinds().get(1));
        again.setPosition(new Coord3D(10f, 10f, 0f));
        assertEquals(List.of(again), world.objectsInRange(Coord3D.ZERO, 100f, thing -> true));
    }
}
