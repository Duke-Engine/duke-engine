package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.ArmorSet;
import uz.dukeengine.core.module.BodyModule;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.rts.event.WeaponFired;

/** What a thing is worth in a fight, chosen by the words it holds: its armour, its weapons' bonuses, its rank. */
class WordsInAFightTest {

    private static final DamageType ARMOR_PIERCING = DamageType.of("ARMOR_PIERCING");

    private static CombatTest.CombatLogic world() {
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(ThingTemplate.named("Tank")
                .module(new ActiveBody.Data(1000f, Map.of(), List.of(
                        new ArmorSet(List.of("Upgrade_X"), Map.of(ARMOR_PIERCING, 0.5f)))))
                .module(new WeaponUpdate.Data(10f, 60f, 12, DamageType.NORMAL, 10f))
                .module(new ExperienceModule.Data(50, List.of(10, 20), List.of(), false, List.of("VETERAN", "ELITE")))
                .build());
        factory.addTemplate(ThingTemplate.named("Target").module(new ActiveBody.Data(100_000f)).build());
        var world = new CombatTest.CombatLogic(factory);
        world.init();
        int ours = world.getPlayerList().addPlayer("Ours").getIndex();
        int theirs = world.getPlayerList().addPlayer("Theirs").getIndex();
        world.getPlayerList().getPlayer(ours).setRelationshipTo(world.getPlayerList().getPlayer(theirs),
                Relationship.ENEMIES);
        world.getPlayerList().getPlayer(theirs).setRelationshipTo(world.getPlayerList().getPlayer(ours),
                Relationship.ENEMIES);
        world.setWeaponBonuses(List.of(
                new WeaponBonus("VETERAN", WeaponBonus.Kind.RATE_OF_FIRE, 1.2f),
                new WeaponBonus("VETERAN", WeaponBonus.Kind.DAMAGE, 1.1f),
                new WeaponBonus("FRENZY", WeaponBonus.Kind.DAMAGE, 1.5f),
                new WeaponBonus("GARRISONED", WeaponBonus.Kind.RANGE, 1.5f),
                new WeaponBonus("GARRISONED", WeaponBonus.Kind.RADIUS, 2f)));
        return world;
    }

    private static GameObject put(CombatTest.CombatLogic world, String template, int side, float x) {
        return world.spawn(world.getThingFactory().findTemplate(template), new Coord3D(x, 100f, 0f), side);
    }

    /** Shots fired and harm done over four seconds at a target in reach. */
    private static float[] fire(CombatTest.CombatLogic world, GameObject tank, GameObject target) {
        tank.findModule(WeaponUpdate.class).attack(target.getId());
        int shots = 0;
        for (int frame = 0; frame < 120; frame++) {
            world.update();
            shots += (int) world.drainEvents().stream().filter(event -> event instanceof WeaponFired).count();
        }
        return new float[] {shots, 100_000f - target.getBody().getHealth()};
    }

    @Test
    void anArmourSetChosenByAWordHalvesWhatItNamesTheMomentTheWordIsSet() {
        var world = world();
        var tank = put(world, "Tank", 1, 100f);

        tank.getBody().damage(100f, ARMOR_PIERCING);
        assertEquals(900f, tank.getBody().getHealth(), 1e-4f, "no word: its plain armour");
        tank.setCondition("Upgrade_X");
        tank.getBody().damage(100f, ARMOR_PIERCING);

        assertEquals(850f, tank.getBody().getHealth(), 1e-4f, "the word set: half");
    }

    @Test
    void aVeteranTankFiresOneAndAFifthTimesAsOftenAndDealsATenthMore() {
        var plain = world();
        var veteran = world();
        var ordinary = put(plain, "Tank", 1, 100f);
        var seasoned = put(veteran, "Tank", 1, 100f);
        seasoned.setCondition("VETERAN");

        var before = fire(plain, ordinary, put(plain, "Target", 2, 140f));
        var after = fire(veteran, seasoned, put(veteran, "Target", 2, 140f));

        assertEquals(10f, before[0], 0f, "every 12 frames");
        assertEquals(12f, after[0], 0f, "every 10: 1.2 times as often");
        assertEquals(10f, before[1] / before[0], 1e-4f);
        assertEquals(11f, after[1] / after[0], 1e-4f, "and 1.1 times the damage a shot");
    }

    @Test
    void twoWordsBonusesMultiply() {
        var world = world();
        var tank = put(world, "Tank", 1, 100f);
        tank.setCondition("VETERAN");
        tank.setCondition("FRENZY");

        var done = fire(world, tank, put(world, "Target", 2, 140f));

        assertEquals(10f * 1.1f * 1.5f, done[1] / done[0], 1e-3f);
    }

    @Test
    void aWordMayReachFartherAndBlastWider() {
        var world = world();
        var tank = put(world, "Tank", 1, 100f);
        var far = put(world, "Target", 2, 100f + 80f);
        var beside = put(world, "Target", 2, 100f + 80f + 15f);
        fire(world, tank, far);
        assertEquals(100_000f, far.getBody().getHealth(), "80 away is past its 60");

        tank.setCondition("GARRISONED");
        fire(world, tank, far);

        assertTrue(far.getBody().getHealth() < 100_000f, "garrisoned, it reaches 90");
        assertTrue(beside.getBody().getHealth() < 100_000f, "and its blast of 10 is 20 wide, catching one 15 off");
    }

    @Test
    void aRankReachedSetsItsWordAndLetsTheLastOneGo() {
        var world = world();
        var tank = put(world, "Tank", 1, 100f);
        var rank = tank.findModule(ExperienceModule.class);

        rank.addExperience(10);
        assertTrue(tank.hasCondition("VETERAN"));
        rank.addExperience(10);

        assertTrue(tank.hasCondition("ELITE"));
        assertFalse(tank.hasCondition("VETERAN"), "one rank's word at a time");
    }

    @Test
    void raisingMostHealthKeepsTheShareAddsTheDifferenceOrLeavesItAsTheGameSays() {
        var world = world();
        var shares = List.of(BodyModule.MaxHealthChange.KEEP_SHARE, BodyModule.MaxHealthChange.ADD_DIFFERENCE,
                BodyModule.MaxHealthChange.KEEP_HEALTH);
        var expected = List.of(100f, 150f, 50f);
        for (int at = 0; at < shares.size(); at++) {
            var target = put(world, "Target", 2, 300f);
            target.getBody().setMaxHealth(100f, BodyModule.MaxHealthChange.KEEP_HEALTH);
            target.getBody().setHealth(50f);

            target.getBody().setMaxHealth(200f, shares.get(at));

            assertEquals(200f, target.getBody().getMaxHealth(), 0f);
            assertEquals(expected.get(at), target.getBody().getHealth(), 1e-4f, shares.get(at).name());
        }
    }

    @Test
    void aFileSaysWhichArmourAWordPutsOnAndWhichWordARankDoes() {
        var factory = new ThingFactory(RtsModules.withDefaults());
        uz.dukeengine.rts.RtsTemplate.register(new uz.dukeengine.core.thing.ThingTemplateLoader(factory)).load("""
                Object
                  Name = Crusader
                  Modules = [
                    ActiveBody
                      MaxHealth = 480
                      ArmorSets = [
                        ArmorSet
                          Conditions = [Upgrade_Composite]
                          Armor = [ARMOR_PIERCING = 0.75]
                        End
                      ]
                    End,
                    ExperienceModule
                      ExperienceValue = 50
                      ExperienceRequired = [100, 200, 400]
                      LevelWords = [VETERAN, ELITE, HEROIC]
                    End
                  ]
                End
                """, "test");

        var modules = factory.findTemplate("Crusader").modules();
        var body = (ActiveBody.Data) modules.get(0);
        var rank = (ExperienceModule.Data) modules.get(1);

        assertEquals(List.of(new ArmorSet(List.of("Upgrade_Composite"), Map.of(ARMOR_PIERCING, 0.75f))),
                body.armorSets());
        assertEquals(List.of("VETERAN", "ELITE", "HEROIC"), rank.levelWords());
    }

    @Test
    void theSameWordsGiveTheSameWorldOnTwoPeers() {
        var first = world();
        var second = world();
        for (var world : List.of(first, second)) {
            var tank = put(world, "Tank", 1, 100f);
            tank.setCondition("VETERAN");
            tank.setCondition("Upgrade_X");
            fire(world, tank, put(world, "Target", 2, 140f));
        }

        assertEquals(first.checksum(), second.checksum());
    }
}
