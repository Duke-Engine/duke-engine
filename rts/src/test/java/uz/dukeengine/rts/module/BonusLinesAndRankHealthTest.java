package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.data.Binder;
import uz.dukeengine.core.data.DukeText;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.BodyModule;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Kind;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplateLoader;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.combat.event.WeaponFired;
import uz.dukeengine.rts.save.GameSnapshot;
import uz.dukeengine.combat.module.ExperienceModule;
import uz.dukeengine.combat.module.TargetRule;
import uz.dukeengine.combat.module.Weapon;
import uz.dukeengine.combat.module.WeaponBonus;
import uz.dukeengine.combat.module.WeaponSet;
import uz.dukeengine.combat.module.WeaponSlot;
import uz.dukeengine.combat.module.WeaponUpdate;

/** Weapon bonuses summed as the reference sums them, a weapon's own lines, a rank's health, most health saved. */
class BonusLinesAndRankHealthTest {

    private static final Kind VEHICLE = Kind.of("VEHICLE");
    private static final Kind INFANTRY = Kind.of("INFANTRY");

    /** A machine gun with AP bullets of its own, beside a rocket launcher without: a Technical. */
    private static final Weapon GUN = new Weapon("MachineGun", 10f, 60f, 5, 0, DamageType.NORMAL, 0f, true,
            List.of("INFANTRY"), 0, 0, true, null, List.of(new WeaponBonus("AP_BULLETS", WeaponBonus.Kind.DAMAGE, 1.5f)));
    private static final Weapon ROCKETS = new Weapon("Rockets", 30f, 60f, 5, 0, DamageType.NORMAL, 0f, true,
            List.of("VEHICLE"), 0, 0, true, null);

    private static CombatTest.CombatLogic world() {
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(RtsTemplate.named("Technical").module(new ActiveBody.Data(200f))
                .module(WeaponUpdate.Data.sets(List.of(new WeaponSet(List.of(),
                        List.of(new WeaponSlot("MachineGun"), new WeaponSlot("Rockets"))))))
                .build());
        factory.addTemplate(RtsTemplate.named("Soldier").kindOf(INFANTRY).module(new ActiveBody.Data(100_000f)).build());
        factory.addTemplate(RtsTemplate.named("Tank").kindOf(VEHICLE).module(new ActiveBody.Data(100_000f)).build());
        factory.addTemplate(RtsTemplate.named("Ranger").module(new ActiveBody.Data(100f))
                .module(new ExperienceModule.Data(20, List.of(10, 20, 40), List.of(), false, List.of(),
                        List.of(1.2f, 1.3f, 1.5f)))
                .build());
        factory.addTemplate(RtsTemplate.named("Crusader").module(new ActiveBody.Data(480f)).build());
        var world = new CombatTest.CombatLogic(factory);
        world.init();
        world.setTargetRules(List.of(
                new TargetRule(List.of(VEHICLE), false, List.of("VEHICLE")),
                new TargetRule(List.of(INFANTRY), false, List.of("INFANTRY"))));
        world.addWeapons(List.of(GUN, ROCKETS));
        world.setWeaponBonuses(List.of(
                new WeaponBonus("VETERAN", WeaponBonus.Kind.DAMAGE, 1.1f),
                new WeaponBonus("PLAYER_UPGRADE", WeaponBonus.Kind.DAMAGE, 1.25f)));
        var us = world.getPlayerList().addPlayer("Us");
        var them = world.getPlayerList().addPlayer("Them");
        us.setRelationshipTo(them, Relationship.ENEMIES);
        them.setRelationshipTo(us, Relationship.ENEMIES);
        return world;
    }

    private static GameObject put(CombatTest.CombatLogic world, String template, int side, float x) {
        return world.spawn(world.getThingFactory().findTemplate(template), new Coord3D(x, 100f, 0f), side);
    }

    /** What one shot at {@code target} did, over a second of shooting. */
    private static float perShot(CombatTest.CombatLogic world, GameObject shooter, GameObject target) {
        shooter.findModule(WeaponUpdate.class).attack(target.getId());
        int shots = 0;
        for (int frame = 0; frame < 30; frame++) {
            world.update();
            shots += (int) world.drainEvents().stream().filter(event -> event instanceof WeaponFired).count();
        }
        return (100_000f - target.getBody().getHealth()) / shots;
    }

    @Test
    void aVeteranWithAUpgradeOf125PercentDealsOneAndThirtyFiveHundredths() {
        var world = world();
        var technical = put(world, "Technical", 1, 100f);
        technical.setCondition("VETERAN");
        technical.setCondition("PLAYER_UPGRADE");

        assertEquals(1.35f, world.weaponBonus(technical, WeaponBonus.Kind.DAMAGE), 1e-6f,
                "1 + 0.1 + 0.25, as the reference sums them, not 1.1 × 1.25 = 1.375");
    }

    @Test
    void aWeaponsOwnLineChangesItAndNotItsSiblingOnTheSameUnit() {
        var world = world();
        var technical = put(world, "Technical", 1, 100f);
        technical.setCondition("AP_BULLETS");

        assertEquals(15f, perShot(world, technical, put(world, "Soldier", 2, 140f)), 1e-3f,
                "the machine gun's own line: 10 × 1.5");
        assertEquals(30f, perShot(world, technical, put(world, "Tank", 2, 140f)), 1e-3f,
                "the rockets beside it: unchanged");
    }

    @Test
    void aRangerPromotedAtFullHealthGainsItsRungsShareOfMostHealthAndStaysFull() {
        var world = world();
        var ranger = put(world, "Ranger", 1, 100f);
        var rank = ranger.findModule(ExperienceModule.class);

        rank.addExperience(10);
        assertEquals(120f, ranger.getBody().getMaxHealth(), 1e-4f, "veteran: 120% of its most");
        assertEquals(120f, ranger.getBody().getHealth(), 1e-4f, "and still full");

        ranger.getBody().damage(60f);
        rank.addExperience(10);
        assertEquals(130f, ranger.getBody().getMaxHealth(), 1e-3f, "elite: 130% of what it had unranked");
        assertEquals(65f, ranger.getBody().getHealth(), 1e-3f, "at the half it was at");
    }

    @Test
    void aSaveOfAnUpgradedCrusaderLoadsAt680() {
        var world = world();
        var crusader = put(world, "Crusader", 1, 100f);
        crusader.getBody().setMaxHealth(680f, BodyModule.MaxHealthChange.ADD_DIFFERENCE);

        var loaded = world();
        GameSnapshot.load(GameSnapshot.save(world), loaded);

        var again = loaded.findObject(crusader.getId());
        assertEquals(680f, again.getBody().getMaxHealth(), 1e-4f);
        assertEquals(680f, again.getBody().getHealth(), 1e-4f);
    }

    @Test
    void aFileSaysAWeaponsOwnLinesAndWhatEachRungMakesOfHealth() {
        var gun = new Binder().bind(DukeText.parse("""
                Weapon
                  Name = TechnicalGun
                  Damage = 10
                  AttackRange = 150
                  Bonuses = [
                    WeaponBonus
                      Word = PLAYER_UPGRADE
                      Kind = DAMAGE
                      Multiplier = 1.25
                    End
                  ]
                End
                """, "weapons.duke").getFirst(), Weapon.class);
        assertEquals(List.of(new WeaponBonus("PLAYER_UPGRADE", WeaponBonus.Kind.DAMAGE, 1.25f)), gun.bonuses());

        var factory = new ThingFactory(RtsModules.withDefaults());
        RtsTemplate.register(new ThingTemplateLoader(factory)).load("""
                Object
                  Name = Ranger
                  Modules = [
                    ExperienceModule
                      ExperienceValue = 20
                      ExperienceRequired = [10, 20, 40]
                      LevelHealthBonus = [1.2, 1.3, 1.5]
                    End
                  ]
                End
                """, "test");
        var rank = (ExperienceModule.Data) factory.findTemplate("Ranger").modules().getFirst();
        assertEquals(List.of(1.2f, 1.3f, 1.5f), rank.levelHealthBonus());
    }
}
