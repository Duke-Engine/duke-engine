package uz.dukeengine.rpg.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.rpg.RpgWorld;
import uz.dukeengine.rpg.Seal;
import uz.dukeengine.rpg.item.GroundItem;
import uz.dukeengine.rpg.item.ItemUse;
import uz.dukeengine.rpg.item.Loot;
import uz.dukeengine.rpg.item.LootBag;

/** Kill what is counted, take the key the last of them leaves, give it to the gate, kill what the gate kept. */
class MissionTest {

    private static final Loot KEY = new Loot("key", "Key", "", "KEY", 0, 0, 1, "", "", 0, 0, 1, ItemUse.UNLOCK, "");

    private static RpgWorld world() {
        return new RpgWorld(Map.of(), Seal.NONE)
                .template("Guard", new ActiveBody.Data(10f))
                .template("Chest", new GroundItem.Data())
                .template("Gate", new GateUpdate.Data("", "", ""));
    }

    private static GroundItem lying(RpgWorld world) {
        return world.getObjects().stream().map(thing -> thing.findModule(GroundItem.class))
                .filter(item -> item != null).findFirst().orElse(null);
    }

    @Test
    void theLastDeathLaysTheKeyAndTheStepsFollowTheWorld() {
        var world = world();
        var gate = world.put("Gate", world.monsters, 500f, 0f);
        List<GameObject> guards = List.of(world.put("Guard", world.monsters, 10f, 0f),
                world.put("Guard", world.monsters, 40f, 0f));
        var bag = new LootBag();
        var mission = Mission.begin(world, KEY, "Chest", world.monsters, gate, guards, new Coord3D(0f, 0f, 0f));
        assertEquals(Mission.Step.CLEAR, mission.step(world, List.of(bag)));

        for (var guard : guards) {
            guard.getBody().damage(100f);
            world.run(1);
        }
        assertEquals(2, mission.killed());
        var key = lying(world);
        assertNotNull(key, "laid where the last fell");
        assertEquals(40f, key.getOwner().getPosition().x());
        assertEquals(Mission.Step.TAKE, mission.step(world, List.of(bag)));

        bag.take(key.getHolding(), 0, 0);
        assertEquals(Mission.Step.GIVE, mission.step(world, List.of(bag)));
        gate.findModule(GateUpdate.class).open();
        world.run(1);
        assertEquals(Mission.Step.KILL, mission.step(world, List.of(bag)));
    }

    @Test
    void aLeewayLaysTheKeyWithAFewStillStandingAndNoSecondOne() {
        var world = world();
        var guards = new java.util.ArrayList<GameObject>();
        for (int at = 0; at < 5; at++) {
            guards.add(world.put("Guard", world.monsters, 10f + 30f * at, 0f));
        }
        var mission = Mission.begin(world, KEY, "Chest", world.monsters, null, guards, new Coord3D(0f, 0f, 0f), 2);

        for (int at = 0; at < 3; at++) {
            guards.get(at).getBody().damage(100f);
            world.run(1);
        }
        var key = lying(world);
        assertNotNull(key, "laid with two still standing");
        assertEquals(70f, key.getOwner().getPosition().x(), "where the third fell");

        for (int at = 3; at < 5; at++) {
            guards.get(at).getBody().damage(100f);
            world.run(1);
        }
        assertEquals(1, world.getObjects().stream().filter(thing -> thing.findModule(GroundItem.class) != null)
                .count(), "no second key");
        assertEquals(mission.outside(), mission.killed());
    }

    @Test
    void withNothingCountedTheKeyLiesAtTheWayInAtOnce() {
        var world = world();
        var bag = new LootBag();
        var mission = Mission.begin(world, KEY, "Chest", world.monsters, null, List.of(), new Coord3D(7f, 3f, 0f));
        assertEquals(7f, lying(world).getOwner().getPosition().x());
        assertEquals(Mission.Step.KILL, mission.step(world, List.of(bag)), "no gate: the key opens nothing");
    }
}
