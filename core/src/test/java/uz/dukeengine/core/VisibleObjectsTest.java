package uz.dukeengine.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleFactory;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.Sighted;
import uz.dukeengine.core.thing.Solid;
import uz.dukeengine.core.thing.ThingFactory;

/**
 * The things a player sees, worked out with the lookers sorted into squares of the ground once — as the snapshot asks —
 * give the answers {@code canSee} gives thing by thing, however the lookers are mixed: sights and fog ranges, sites,
 * things everyone sees round, the hidden, the carried, allies and lent sight.
 */
class VisibleObjectsTest {

    record Looker(String name, List<ModuleData> modules, float visionRange, float fogRange, float seenByAllWithin,
            Geometry geometry) implements Sighted, Solid {
    }

    @Test
    void theThingsAPlayerSeesAreTheOnesCanSeeSaysHeSeesOneByOne() {
        var factory = new ThingFactory(ModuleFactory.withDefaults());
        var kinds = List.of(
                new Looker("Scout", List.of(), 150f, -1f, 0f, new Geometry.Cylinder(3f, 6f)),
                new Looker("Ranger", List.of(), 100f, 400f, 0f, new Geometry.Cylinder(3f, 6f)),
                new Looker("Blind", List.of(), 0f, -1f, 0f, Geometry.POINT),
                new Looker("Cannon", List.of(), 200f, -1f, 60f, new Geometry.Box(20f, 20f, 20f)),
                new Looker("Drone", List.of(), 250f, 0f, 0f, new Geometry.Cylinder(2f, 2f)));
        kinds.forEach(factory::addTemplate);
        var world = new GameLogic(factory) {
            @Override
            protected void simulate() {
            }
        };
        world.init();
        for (int n = 0; n < 4; n++) {
            world.getPlayerList().addPlayer("Side " + n);
        }
        var players = world.getPlayerList();
        players.getPlayer(1).setRelationshipTo(players.getPlayer(2), Relationship.ALLIES);
        players.getPlayer(2).setRelationshipTo(players.getPlayer(1), Relationship.ALLIES);
        players.getPlayer(1).setRelationshipTo(players.getPlayer(3), Relationship.ENEMIES);
        players.getPlayer(3).setRelationshipTo(players.getPlayer(1), Relationship.ENEMIES);
        var random = new Random(15);
        var things = new ArrayList<GameObject>();
        for (int n = 0; n < 300; n++) {
            var thing = world.spawn(kinds.get(random.nextInt(kinds.size())),
                    new Coord3D(random.nextFloat() * 3000f, random.nextFloat() * 3000f, 0f), 1 + random.nextInt(3));
            switch (random.nextInt(12)) {
                case 0 -> thing.setStatus(ObjectStatus.UNDER_CONSTRUCTION);
                case 1 -> thing.setStatus(ObjectStatus.HIDDEN);
                case 2 -> thing.setContained(true);
                case 3 -> {
                    thing.setContained(true);
                    thing.setSeesOut(true);
                }
                case 4 -> thing.setFogRange(random.nextFloat() * 300f);
                default -> {
                }
            }
            things.add(thing);
        }
        world.setSharedSight((player, watcher) -> player == 3 && watcher.getId().value() % 7 == 0);

        for (int viewer = 1; viewer <= 3; viewer++) {
            var oneByOne = new ArrayList<GameObject>();
            for (var thing : world.getObjects()) {
                if (world.canSee(viewer, thing)) {
                    oneByOne.add(thing);
                }
            }
            assertEquals(oneByOne, world.getVisibleObjects(viewer), "side " + viewer);
        }
    }

    /**
     * The same, where no sight is lent and the world is wide: what a player sees is looked for only where his lookers'
     * reach falls and among his own — and it is still what canSee says of every thing, as things change hands, move,
     * die and leave, and a looker that sees half the world is among them.
     */
    @Test
    void inAWideWorldTheThingsAPlayerSeesAreFoundWhereHisLookersLookAndAreTheSame() {
        var factory = new ThingFactory(ModuleFactory.withDefaults());
        var kinds = List.of(
                new Looker("Scout", List.of(new uz.dukeengine.core.module.ActiveBody.Data(10f)), 150f, -1f, 0f,
                        new Geometry.Cylinder(3f, 6f)),
                new Looker("Rock", List.of(), 0f, -1f, 0f, new Geometry.Sphere(4f)),
                new Looker("Cannon", List.of(), 200f, -1f, 60f, new Geometry.Box(20f, 20f, 20f)),
                new Looker("Beacon", List.of(), 0f, 9000f, 0f, Geometry.POINT));
        kinds.forEach(factory::addTemplate);
        var world = new GameLogic(factory) {
            @Override
            protected void simulate() {
            }
        };
        world.init();
        for (int n = 0; n < 4; n++) {
            world.getPlayerList().addPlayer("Side " + n);
        }
        var players = world.getPlayerList();
        players.getPlayer(1).setRelationshipTo(players.getPlayer(2), Relationship.ALLIES);
        players.getPlayer(2).setRelationshipTo(players.getPlayer(1), Relationship.ALLIES);
        var random = new Random(41);
        // Rocks mostly, that see nothing: each side a few lookers, so its things are found where they look.
        java.util.function.Supplier<Looker> any = () -> {
            float pick = random.nextFloat();
            return kinds.get(pick < 0.01f ? 0 : pick < 0.015f ? 2 : 1);
        };
        for (int n = 0; n < 2000; n++) {
            world.spawn(any.get(), new Coord3D(random.nextFloat() * 20000f, random.nextFloat() * 20000f, 0f),
                    random.nextInt(4));
        }
        for (int round = 0; round < 40; round++) {
            var things = world.getObjects();
            for (int n = 0; n < 10; n++) {
                var thing = things.get(random.nextInt(things.size()));
                switch (random.nextInt(5)) {
                    case 0 -> thing.setPlayerIndex(random.nextInt(4));
                    case 1 -> thing.setPosition(new Coord3D(random.nextFloat() * 20000f, random.nextFloat() * 20000f,
                            0f));
                    case 2 -> {
                        if (thing.getBody() != null) {
                            thing.getBody().damage(100f);
                        }
                    }
                    case 3 -> world.destroyObject(thing);
                    default -> world.spawn(any.get(),
                            new Coord3D(random.nextFloat() * 20000f, random.nextFloat() * 20000f, 0f),
                            random.nextInt(4));
                }
            }
            if (round == 20) { // a looker that sees most of the world: its side's things looked for everywhere
                world.spawn(kinds.get(3), new Coord3D(10000f, 10000f, 0f), 0);
            }
            world.update();
            for (int viewer = 0; viewer < 4; viewer++) {
                var oneByOne = new ArrayList<GameObject>();
                var own = new ArrayList<GameObject>();
                for (var thing : world.getObjects()) {
                    if (world.canSee(viewer, thing)) {
                        oneByOne.add(thing);
                    }
                    if (thing.getPlayerIndex() == viewer) {
                        own.add(thing);
                    }
                }
                assertEquals(oneByOne, world.getVisibleObjects(viewer), "round " + round + ", side " + viewer);
                assertEquals(own, world.getObjectsOf(viewer), "round " + round + ", his own, side " + viewer);
            }
        }
    }
}
