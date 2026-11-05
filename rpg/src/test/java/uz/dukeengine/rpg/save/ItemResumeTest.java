package uz.dukeengine.rpg.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.save.ModuleStates;
import uz.dukeengine.core.save.UnsavedModuleException;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.rpg.RpgWorld;
import uz.dukeengine.rpg.Seal;
import uz.dukeengine.rpg.item.Affix;
import uz.dukeengine.rpg.item.GroundItem;
import uz.dukeengine.rpg.item.ItemErrand;
import uz.dukeengine.rpg.item.ItemUse;
import uz.dukeengine.rpg.item.KeyDrop;
import uz.dukeengine.rpg.item.Loot;
import uz.dukeengine.rpg.item.LootBag;
import uz.dukeengine.rpg.item.LootDrop;
import uz.dukeengine.rpg.item.LootTable;
import uz.dukeengine.rpg.skill.Skill;
import uz.dukeengine.rpg.skill.SkillEffect;

/** What lies on the floor, what a creature leaves when it falls, and a hero sent to pick a thing up: saved and played on. */
class ItemResumeTest {

    private static final Loot KEY = new Loot("key", "Key", "", "KEY", 0, 0, 1, "", "", 0, 0, 1, ItemUse.UNLOCK, "");

    private static final ItemErrand.Rules ERRAND = new ItemErrand.Rules(5f, 30, "Chest", 2, "full", "no use",
            "no way", 90, 300);

    private static Skill lent() {
        return new Skill("Hero", 'Q', SkillEffect.LIFESTEAL, 0f, 1.5f, 2f, 3f, 4f, 5f, 6, 7, 8, 9, 10, 11, 12, 13, 14,
                15, 16, 17, "proj ectile", "icons/q.png", "look", "anim", 1.25f, "Name | with bars", "Blurb\nlines", 2.5f,
                3.5f, 18, new java.util.LinkedHashMap<>(Map.of("Imp", 2)), 19, 20, 21, 22, List.of("town", "keep"),
                false, true);
    }

    private static final Loot RING = new Loot("ring", "Ring of 50% Haste", "icons/ring.png", "ATTACK", 12, 3, 2,
            "STRENGTH", "HEALTH", 4, 2, 2, ItemUse.NONE, "Chest", "FINGER", List.of(lent()),
            List.of(new Affix("CRIT_CHANCE", 5), new Affix("", 1)));

    private static LootBag bagWithAKey() {
        var bag = new LootBag();
        bag.take(KEY, 0, 0);
        return bag;
    }

    private static RpgWorld rules(Map<RpgWorld, LootBag> bags) {
        var world = RpgSaveCheck.world(Map.of(), Seal.NONE)
                .template("Chest", new GroundItem.Data())
                .template("Hero", new ActiveBody.Data(100f), new MoveUpdate.Data(60f))
                .template("Monster", new ActiveBody.Data(10f));
        bags.put(world, bagWithAKey());
        var table = new LootTable(List.of(RING), 100, 100, 10);
        world.getThingFactory().getModuleFactory()
                .revives(LootDrop.class, LootDrop.reviver(table))
                .revives(KeyDrop.class, KeyDrop.reviver(hero -> bags.get(hero.getWorld())))
                .revives(ItemErrand.class, ItemErrand.reviver(hero -> bags.get(hero.getWorld()), ERRAND));
        return world;
    }

    @Test
    void whatLiesOnTheFloorKeepsEverythingItHoldsIncludingTheSkillsItLends() {
        var bags = new java.util.IdentityHashMap<RpgWorld, LootBag>();
        var played = rules(bags);
        GroundItem.lay(played, "Chest", RING, new Coord3D(10f, 0f, 0f), played.monsters);
        GroundItem.lay(played, "Chest", KEY, new Coord3D(20f, 0f, 0f), played.monsters);
        played.run(3);

        var loaded = RpgSaveCheck.reloaded(() -> rules(bags), played);
        var lying = loaded.getObjects().stream().map(thing -> thing.findModule(GroundItem.class)).toList();
        assertEquals(RING, lying.get(0).getHolding());
        assertEquals(KEY, lying.get(1).getHolding());
        assertEquals(lent(), lying.get(0).getHolding().skills().getFirst());
        RpgSaveCheck.resumes(() -> rules(bags), world -> GroundItem.lay(world, "Chest", RING,
                new Coord3D(10f, 0f, 0f), world.monsters), (world, frame) -> {
                    if (frame == 6) {
                        world.getObjects().getFirst().findModule(GroundItem.class).take();
                    }
                }, 4, 6);
    }

    @Test
    void aMonsterThatDiesAfterTheLoadLeavesWhatTheSavedOneWouldHave() {
        var bags = new java.util.IdentityHashMap<RpgWorld, LootBag>();
        var saved = RpgSaveCheck.resumesOnlyWithItsModules(() -> rules(bags), world -> {
            var monster = world.put("Monster", world.monsters, 30f, 0f);
            monster.addModule(new LootDrop(monster, new LootTable(List.of(RING), 100, 100, 10), "Chest", 3, true));
        }, (world, frame) -> {
            if (frame == 8) {
                world.getObjects().getFirst().getBody().damage(50f);
            }
        }, 4, 10);
        assertTrue(saved.contains(LootDrop.class.getName() + " 'Chest 3 1"), saved);
    }

    @Test
    void aHeroWhoFallsAfterTheLoadLaysTheKeyFromTheBagTheGameGivesBack() {
        var bags = new java.util.IdentityHashMap<RpgWorld, LootBag>();
        var saved = RpgSaveCheck.resumesOnlyWithItsModules(() -> rules(bags), world -> {
            var hero = world.put("Hero", world.heroes, 0f, 0f);
            hero.addModule(new KeyDrop(hero, bags.get(world), "Chest", world.monsters));
        }, (world, frame) -> {
            if (frame == 8) {
                world.getObjects().getFirst().getBody().damage(500f);
            }
        }, 4, 10);
        assertTrue(saved.contains(KeyDrop.class.getName() + " 'Chest 2"), saved);
    }

    @Test
    void aHeroOnHisWayToAThingGoesOnAndTakesItIntoTheBagTheGameGivesBack() {
        var bags = new java.util.IdentityHashMap<RpgWorld, LootBag>();
        var saved = RpgSaveCheck.resumesOnlyWithItsModules(() -> rules(bags), world -> {
            var hero = world.put("Hero", world.heroes, 0f, 0f);
            var chest = GroundItem.lay(world, "Chest", RING, new Coord3D(120f, 0f, 0f), world.monsters);
            world.run(1);
            assertTrue(ItemErrand.pickUp(hero, chest, bags.get(world), ERRAND));
        }, (world, frame) -> {
        }, 20, 120);
        assertTrue(saved.contains(ItemErrand.class.getName() + " TAKE"), saved);
    }

    @Test
    void anErrandNobodyGivesABagForIsRefusedByName() {
        var bags = new java.util.IdentityHashMap<RpgWorld, LootBag>();
        var played = rules(bags);
        var hero = played.put("Hero", played.heroes, 0f, 0f);
        var chest = GroundItem.lay(played, "Chest", RING, new Coord3D(120f, 0f, 0f), played.monsters);
        played.run(1);
        ItemErrand.pickUp(hero, chest, bags.get(played), ERRAND);
        var line = ModuleStates.save(hero, played.getThingFactory().getModuleFactory());

        var loaded = rules(bags);
        loaded.getThingFactory().getModuleFactory()
                .revives(ItemErrand.class, ItemErrand.reviver(_ -> null, ERRAND));
        GameObject there = loaded.put("Hero", loaded.heroes, 0f, 0f);
        var refused = assertThrows(UnsavedModuleException.class,
                () -> ModuleStates.load(there, line, loaded.getThingFactory().getModuleFactory()));
        assertEquals(ItemErrand.class.getName(), refused.module());
    }
}
