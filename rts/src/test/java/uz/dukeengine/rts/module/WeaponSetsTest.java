package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.data.Binder;
import uz.dukeengine.core.data.DukeText;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.Kind;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.core.thing.ThingTemplateLoader;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.rts.event.WeaponFired;

/**
 * More than one weapon, and sets of them: which slot fires at what, and which set is in use.
 *
 * <p>Every test listens for {@link WeaponFired}, which now says which weapon fired, and asks nothing else of
 * the unit — what fired is the whole of the question.
 */
class WeaponSetsTest {

    private static final Kind VEHICLE = Kind.of("VEHICLE");
    private static final Kind INFANTRY = Kind.of("INFANTRY");

    /** Aircraft in the air are one class, everything else is ground. */
    private static final List<TargetRule> RULES = List.of(
            new TargetRule(List.of(VEHICLE), true, List.of("AIRBORNE_VEHICLE")),
            new TargetRule(List.of(), true, List.of()),
            new TargetRule(List.of(), false, List.of("GROUND")));

    private static Weapon weapon(String name, float damage, int reload, List<String> targets) {
        return new Weapon(name, damage, 60f, reload, 0, DamageType.NORMAL, 0f, true, targets, 0, 0, true);
    }

    private static final Weapon GUN = weapon("Gun", 10f, 5, List.of("GROUND"));
    private static final Weapon MISSILE = weapon("Missile", 30f, 30, List.of("AIRBORNE_VEHICLE"));

    private record Scene(CombatTest.CombatLogic logic, GameObject shooter, int enemy, List<String> fired) {

        GameObject enemy(String template, float x) {
            var thing = logic.createObject(logic.getThingFactory().findTemplate(template));
            thing.setPlayerIndex(enemy);
            thing.setPosition(new Coord3D(x, 0f, 0f));
            return thing;
        }

        void run(int frames) {
            for (int frame = 0; frame < frames; frame++) {
                logic.update();
                for (var event : logic.drainEvents()) {
                    if (event instanceof WeaponFired shot) {
                        fired.add(shot.weapon());
                    }
                }
            }
        }
    }

    private static Scene scene(WeaponUpdate.Data weapons, Weapon... library) {
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(RtsTemplate.named("Shooter").geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(100f)).module(weapons).build());
        factory.addTemplate(target("Helicopter", VEHICLE));
        factory.addTemplate(target("Tank", VEHICLE));
        factory.addTemplate(target("Soldier", INFANTRY));
        var logic = new CombatTest.CombatLogic(factory);
        logic.init();
        logic.setTargetRules(RULES);
        logic.addWeapons(List.of(library));
        var us = logic.getPlayerList().addPlayer("Us");
        var them = logic.getPlayerList().addPlayer("Them");
        us.setRelationshipTo(them, Relationship.ENEMIES);
        them.setRelationshipTo(us, Relationship.ENEMIES);
        var shooter = logic.createObject(factory.findTemplate("Shooter"));
        shooter.setPlayerIndex(us.getIndex());
        shooter.setPosition(Coord3D.ZERO);
        return new Scene(logic, shooter, them.getIndex(), new ArrayList<>());
    }

    private static ThingTemplate target(String name, Kind kind) {
        return RtsTemplate.named(name).geometry(new Geometry.Cylinder(3f, 6f)).kindOf(kind)
                .module(new ActiveBody.Data(1_000_000f)).build();
    }

    private static WeaponUpdate.Data oneSet(WeaponSlot... slots) {
        return WeaponUpdate.Data.sets(List.of(new WeaponSet(List.of(), List.of(slots))));
    }

    /** A Humvee: a machine gun for the ground, a missile for the air. */
    @Test
    void theMissileGoesAtTheAircraftAndTheGunAtTheTank() {
        var aircraft = scene(oneSet(new WeaponSlot("Gun"), new WeaponSlot("Missile")), GUN, MISSILE);
        aircraft.enemy("Helicopter", 20f).setStatus(ObjectStatus.AIRBORNE);
        aircraft.run(1);
        assertEquals(List.of("Missile"), aircraft.fired());

        var tank = scene(oneSet(new WeaponSlot("Gun"), new WeaponSlot("Missile")), GUN, MISSILE);
        tank.enemy("Tank", 20f);
        tank.run(1);
        assertEquals(List.of("Gun"), tank.fired());
    }

    /**
     * The missile reloading and the gun ready: the gun fires at the aircraft only if it can hit it. Readiness
     * beats damage — but never lets a weapon fire at what it cannot hit.
     */
    @Test
    void aReadyGunCoversAReloadingMissileOnlyIfItCanHitTheTarget() {
        var groundGun = scene(oneSet(new WeaponSlot("Gun"), new WeaponSlot("Missile")), GUN, MISSILE);
        groundGun.enemy("Helicopter", 20f).setStatus(ObjectStatus.AIRBORNE);
        groundGun.run(31);
        assertEquals(List.of("Missile", "Missile"), groundGun.fired(), "the gun is no use against it");

        var anyGun = weapon("Gun", 10f, 5, List.of("GROUND", "AIRBORNE_VEHICLE"));
        var bothHit = scene(oneSet(new WeaponSlot("Gun"), new WeaponSlot("Missile")), anyGun, MISSILE);
        bothHit.enemy("Helicopter", 20f).setStatus(ObjectStatus.AIRBORNE);
        bothHit.run(12);
        assertEquals(List.of("Missile", "Gun", "Gun", "Gun"), bothHit.fired(),
                "the missile first, for its damage, then the gun while it reloads");
    }

    /** The reference game's Comanche: its cannon on infantry, though the missiles would do more. */
    @Test
    void aSlotPreferredAgainstAKindWinsOutright() {
        var cannon = weapon("Cannon", 5f, 5, List.of());
        var rockets = weapon("Rockets", 50f, 5, List.of());
        var unit = new WeaponSlot("Cannon", List.of(INFANTRY), true);
        var scene = scene(oneSet(new WeaponSlot("Rockets"), unit), rockets, cannon);
        scene.enemy("Soldier", 20f);
        scene.run(1);
        assertEquals(List.of("Cannon"), scene.fired());

        var atATank = scene(oneSet(new WeaponSlot("Rockets"), unit), rockets, cannon);
        atATank.enemy("Tank", 20f);
        atATank.run(1);
        assertEquals(List.of("Rockets"), atATank.fired(), "and against anything else, damage decides");
    }

    /** Two slots that would deal the same: the lower one fires. */
    @Test
    void aTieGoesToTheLowerSlot() {
        var left = weapon("Left", 10f, 5, List.of());
        var right = weapon("Right", 10f, 5, List.of());
        var scene = scene(oneSet(new WeaponSlot("Left"), new WeaponSlot("Right")), left, right);
        scene.enemy("Tank", 20f);
        scene.run(1);
        assertEquals(List.of("Left"), scene.fired());
    }

    /** A slot the unit may not pick by itself waits for an order. */
    @Test
    void aSlotNotForTheUnitToPickIsUsedOnlyWhenOrdered() {
        var special = new WeaponSlot("Missile", List.of(), false);
        var scene = scene(oneSet(new WeaponSlot("Gun"), special), GUN, MISSILE);
        var helicopter = scene.enemy("Helicopter", 20f);
        helicopter.setStatus(ObjectStatus.AIRBORNE);
        scene.run(5);
        assertTrue(scene.fired().isEmpty(), "nothing it may pick by itself can hit it");

        assertTrue(scene.shooter().findModule(WeaponUpdate.class).attack(helicopter.getId()));
        scene.run(1);
        assertEquals(List.of("Missile"), scene.fired());
    }

    /**
     * Setting a condition word swaps to the set that names it; clearing it swaps back — and the weapon swapped
     * out keeps its clip, so a swap is no way round a reload.
     */
    @Test
    void aConditionWordSwapsTheSetAndEachWeaponKeepsItsClip() {
        var gun = new Weapon("Gun", 10f, 60f, 5, 0, DamageType.NORMAL, 0f, true, List.of(), 3, 90, true);
        var bigGun = weapon("BigGun", 40f, 5, List.of());
        var scene = scene(WeaponUpdate.Data.sets(List.of(
                new WeaponSet(List.of(), List.of(new WeaponSlot("Gun"))),
                new WeaponSet(List.of("UPGRADED"), List.of(new WeaponSlot("BigGun"))))), gun, bigGun);
        scene.enemy("Tank", 20f);
        var weapons = scene.shooter().findModule(WeaponUpdate.class);

        scene.run(1);
        assertEquals(List.of("Gun"), scene.fired());
        assertEquals(2, weapons.getRounds());

        scene.shooter().setCondition("UPGRADED");
        scene.run(1);
        assertEquals("BigGun", scene.fired().getLast(), "the set that names the word");

        scene.shooter().clearCondition("UPGRADED");
        assertEquals(2, weapons.getRounds(), "back to the gun, with the rounds it had left");
        scene.run(5);
        assertEquals("Gun", scene.fired().getLast());
        assertEquals(1, weapons.getRounds());
    }

    /** The most words that hold win: a set naming two beats one naming one of them. */
    @Test
    void theSetWithTheMostWordsThatHoldWins() {
        var scene = scene(WeaponUpdate.Data.sets(List.of(
                new WeaponSet(List.of(), List.of(new WeaponSlot("A"))),
                new WeaponSet(List.of("VETERAN"), List.of(new WeaponSlot("B"))),
                new WeaponSet(List.of("VETERAN", "UPGRADED"), List.of(new WeaponSlot("C"))))),
                weapon("A", 1f, 5, List.of()), weapon("B", 1f, 5, List.of()), weapon("C", 1f, 5, List.of()));
        scene.enemy("Tank", 20f);
        scene.shooter().setCondition("UPGRADED");
        scene.shooter().setCondition("VETERAN");
        scene.run(1);
        assertEquals(List.of("C"), scene.fired());
    }

    /** A unit whose one weapon is written in place fires it as before, with no name unless it gives one. */
    @Test
    void aWeaponWrittenInPlaceIsTheOneItAlwaysWas() {
        var unnamed = scene(new WeaponUpdate.Data(10f, 60f, 5));
        unnamed.enemy("Tank", 20f);
        unnamed.run(6);
        assertEquals(2, unnamed.fired().size());
        assertNull(unnamed.fired().getFirst(), "a weapon with no name of its own says none");

        var named = scene(new WeaponUpdate.Data(10f, 60f, 5, DamageType.NORMAL, 0f, true, List.of(),
                0, 0, 0, true, "Bow", List.of()));
        named.enemy("Tank", 20f);
        named.run(1);
        assertEquals(List.of("Bow"), named.fired());
    }

    /** Written as a game writes it: weapons as blocks of their own, linked by name from the slots. */
    @Test
    void weaponsAndSetsReadFromData() {
        var blocks = DukeText.parse("""
                Weapon
                  Name = HumveeGun
                  Damage = 10
                  AttackRange = 150
                  ReloadFrames = 3
                  ClipSize = 30
                  ClipReloadFrames = 60
                  Targets = [GROUND]
                End
                """, "weapons.duke");
        var gun = new Binder().bind(blocks.getFirst(), Weapon.class);
        assertEquals("HumveeGun", gun.name());
        assertEquals(30, gun.clipSize());
        assertTrue(gun.autoReload(), "yes unless it says no");

        var factory = new ThingFactory(RtsModules.withDefaults());
        var loaded = RtsTemplate.register(new ThingTemplateLoader(factory)).load("""
                Object
                  Name = Humvee
                  Modules = [
                    ActiveBody
                      MaxHealth = 240
                    End,
                    WeaponUpdate
                      WeaponSets = [
                        WeaponSet
                          Slots = [
                            WeaponSlot
                              Weapon = HumveeGun
                            End,
                            WeaponSlot
                              Weapon = HumveeMissile
                              PreferredAgainst = [VEHICLE]
                              AutoChoosable = No
                            End
                          ]
                        End,
                        WeaponSet
                          Conditions = [PLAYER_UPGRADE]
                          Slots = [
                            WeaponSlot
                              Weapon = HumveeGunUpgraded
                            End
                          ]
                        End
                      ]
                    End
                  ]
                End
                """, "humvee.duke");
        var data = loaded.getFirst().modules().stream()
                .filter(WeaponUpdate.Data.class::isInstance).map(WeaponUpdate.Data.class::cast)
                .findFirst().orElseThrow();
        assertEquals(2, data.weaponSets().size());
        var missile = data.weaponSets().getFirst().slots().get(1);
        assertEquals("HumveeMissile", missile.weapon());
        assertEquals(List.of(VEHICLE), missile.preferredAgainst());
        assertEquals(false, missile.autoChoosable());
        assertTrue(data.weaponSets().getFirst().slots().getFirst().autoChoosable(), "yes unless it says no");
        assertEquals(List.of("PLAYER_UPGRADE"), data.weaponSets().get(1).conditions());
    }
}
