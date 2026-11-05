package uz.dukeengine.rpg.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.save.SaveIn;
import uz.dukeengine.core.save.SaveOut;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.rpg.RpgWorld;
import uz.dukeengine.rpg.Seal;
import uz.dukeengine.rpg.item.GroundItem;
import uz.dukeengine.rpg.item.ItemUse;
import uz.dukeengine.rpg.item.Loot;
import uz.dukeengine.rpg.item.LootBag;
import uz.dukeengine.rpg.mission.GateUpdate;
import uz.dukeengine.rpg.mission.Mission;

/** The keep's gate, and the floor's mission counting the dead: saved mid-floor, loaded, and played on. */
class MissionResumeTest {

    private static final Loot KEY = new Loot("key", "Key", "", "KEY", 0, 0, 1, "", "", 0, 0, 1, ItemUse.UNLOCK, "");

    private static RpgWorld rules(AtomicReference<Mission> mission) {
        var world = RpgSaveCheck.world(Map.of(), Seal.NONE)
                .template("Guard", new ActiveBody.Data(10f))
                .template("Chest", new GroundItem.Data())
                .template("Gate", new GateUpdate.Data("OpenGate", "", ""))
                .template("OpenGate", new ActiveBody.Data(10f));
        world.getThingFactory().getModuleFactory().revives(Mission.Counted.class,
                (thing, in) -> Mission.Counted.reviver(mission.get()).revive(thing, in));
        return world;
    }

    @Test
    void aGateOpenedIsOpenedOnceAndNoSecondGateStandsInItsPlace() {
        RpgSaveCheck.resumesOnlyWithItsModules(() -> rules(new AtomicReference<>()), world -> {
            var gate = world.put("Gate", world.monsters, 50f, 0f);
            world.run(1);
            gate.findModule(GateUpdate.class).open();
        }, (world, frame) -> world.getObjects().stream().map(thing -> thing.findModule(GateUpdate.class))
                .filter(java.util.Objects::nonNull).forEach(GateUpdate::open), 0, 6);
    }

    @Test
    void aFloorMissionGoesOnCountingTheDeadAndLaysTheKeyWhereTheLastFell() {
        var played = rules(new AtomicReference<>());
        var gate = played.put("Gate", played.monsters, 500f, 0f);
        List<GameObject> guards = List.of(played.put("Guard", played.monsters, 10f, 0f),
                played.put("Guard", played.monsters, 40f, 0f));
        var mission = Mission.begin(played, KEY, "Chest", played.monsters, gate, guards, new Coord3D(0f, 0f, 0f));
        guards.getFirst().getBody().damage(100f);
        played.run(2);
        assertEquals(1, mission.killed());

        var saved = new SaveOut();
        mission.save(saved);
        var holder = new AtomicReference<Mission>();
        var again = Mission.restored(KEY, "Chest", played.monsters, gate.getId(),
                guards.stream().map(GameObject::getId).toList(), 0);
        again.load(new SaveIn(saved.text(), played, "Mission"));
        holder.set(again);
        var loaded = RpgSaveCheck.reloaded(() -> rules(holder), played);
        assertEquals(1, again.killed());
        assertTrue(RpgSaveCheck.stateOf(loaded).contains(Mission.Counted.class.getName()));

        for (var world : List.of(played, loaded)) {
            world.findObject(guards.get(1).getId()).getBody().damage(100f);
            world.run(2);
        }
        assertEquals(2, mission.killed());
        assertEquals(2, again.killed(), "the second death was told to the mission the load gave back");
        assertEquals(mission.step(played, List.of(new LootBag())), again.step(loaded, List.of(new LootBag())));
        assertEquals(RpgSaveCheck.stateOf(played), RpgSaveCheck.stateOf(loaded));
        assertEquals(played.checksum(), loaded.checksum());
    }
}
