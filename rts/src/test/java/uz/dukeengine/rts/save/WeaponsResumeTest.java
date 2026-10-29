package uz.dukeengine.rts.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.message.OrderSource;
import uz.dukeengine.combat.module.Engaging;
import uz.dukeengine.combat.module.PointAttack;
import uz.dukeengine.combat.module.Shot;
import uz.dukeengine.combat.module.ShotListener;
import uz.dukeengine.combat.module.Weapon;
import uz.dukeengine.combat.module.WeaponSet;
import uz.dukeengine.combat.module.WeaponSlot;
import uz.dukeengine.combat.module.WeaponStatus;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.DeathType;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.save.ModuleStates;
import uz.dukeengine.core.save.SaveIn;
import uz.dukeengine.core.save.SaveOut;
import uz.dukeengine.core.save.UnsavedModuleException;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.rts.RtsLogic;
import uz.dukeengine.rts.RtsTemplate;

/**
 * Armed things that stand where they are, of two sides at war, saved mid-fight — mid-reload, mid-wind-up, on a locked
 * slot of a set their words chose, on a forced order, on the ground, between their looks — loaded, and played on frame
 * for frame as the world they were saved from; and the world loaded with their weapons made afresh parting from it.
 */
class WeaponsResumeTest {

    private static Weapon weapon(String name, float damage, int reload, int reloadMax, int clip, int clipReload) {
        return new Weapon(name, damage, 60f, reload, reloadMax, DamageType.NORMAL, 0f, true, List.of(), clip,
                clipReload, true, null);
    }

    /** A world whose {@code Shooter} carries {@code weapons}, a {@code Wall} that only stands, and the game's arms. */
    private static RtsLogic rules(ModuleData weapons, Weapon... armoury) {
        var logic = new RtsLogic();
        logic.getThingFactory().addTemplate(RtsTemplate.named("Shooter").geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(1_000_000f)).module(weapons).build());
        logic.getThingFactory().addTemplate(RtsTemplate.named("Wall").geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(1_000_000f)).build());
        logic.init();
        logic.addWeapons(List.of(armoury));
        return logic;
    }

    private static void sides(RtsLogic logic) {
        var red = logic.getPlayerList().addPlayer("Red");
        var blue = logic.getPlayerList().addPlayer("Blue");
        red.setRelationshipTo(blue, Relationship.ENEMIES);
        blue.setRelationshipTo(red, Relationship.ENEMIES);
    }

    private static GameObject spawn(RtsLogic logic, String template, int side, float x) {
        return logic.spawn(logic.getThingFactory().findTemplate(template), new Coord3D(x, 0f, 0f), side);
    }

    private static WeaponUpdate weaponsOf(RtsLogic logic, int id) {
        return logic.findObject(new ObjectId(id)).findModule(WeaponUpdate.class);
    }

    /** The world {@code setup} makes of {@code logic}, played {@code frames} frames: what a save then would find. */
    private static RtsLogic playedTo(RtsLogic logic, Consumer<RtsLogic> setup, int frames) {
        setup.accept(logic);
        for (int frame = 0; frame < frames; frame++) {
            logic.update();
        }
        return logic;
    }

    @Test
    void aClipEmptiedMidReloadReloadsOnAndItsDrawnWaitsGoOnAlike() {
        // Four rounds, 3..9 frames between them drawn from the world's numbers, 40 to refill: the four are spent by
        // frame 30, the reload under way.
        var clip = new WeaponUpdate.Data(1f, 100f, 3, DamageType.NORMAL, 0f, true, List.of(), 9, 4, 40, true);
        Consumer<RtsLogic> setup = logic -> {
            sides(logic);
            spawn(logic, "Shooter", 1, 0f);
            spawn(logic, "Shooter", 2, 40f);
        };
        assertEquals(WeaponStatus.RELOADING, weaponsOf(playedTo(rules(clip), setup, 30), 1).getStatus());
        SaveLoadCheck.resumesOnlyWithItsModules(() -> rules(clip), setup, (logic, frame) -> {
        }, 30, 150);
    }

    @Test
    void aWindUpUnderWayEndsInItsShotOnTheFrameItWould() {
        // Ten frames between shots, fifteen wound up before each: at frame 30 the second wind-up is half done.
        var windsUp = new WeaponUpdate.Data(1f, 100f, 10, DamageType.NORMAL, 0f, true, List.of(), 0, 0, 0, true,
                null, List.of(), DeathType.NORMAL, 0, 15);
        Consumer<RtsLogic> setup = logic -> {
            sides(logic);
            spawn(logic, "Shooter", 1, 0f);
            spawn(logic, "Shooter", 2, 40f);
        };
        assertEquals(WeaponStatus.PRE_ATTACK, weaponsOf(playedTo(rules(windsUp), setup, 30), 1).getStatus());
        SaveLoadCheck.resumesOnlyWithItsModules(() -> rules(windsUp), setup, (logic, frame) -> {
        }, 30, 120);
    }

    @Test
    void aSlotLockedAcrossASetItsWordsChoseStaysLockedUntilASetThatDoesNotShareIt() {
        var sets = WeaponUpdate.Data.sets(List.of(
                new WeaponSet(List.of(), List.of(new WeaponSlot("Gun"), new WeaponSlot("Cannon"))),
                new WeaponSet(List.of("UPGRADED"), List.of(new WeaponSlot("GunTwo"), new WeaponSlot("CannonTwo")),
                        true)));
        var armoury = new Weapon[] {weapon("Gun", 10f, 2, 4, 0, 0), weapon("Cannon", 3f, 2, 4, 3, 12),
                weapon("GunTwo", 10f, 2, 4, 0, 0), weapon("CannonTwo", 3f, 2, 4, 3, 12)};
        SaveLoadCheck.resumesOnlyWithItsModules(() -> rules(sets, armoury), logic -> {
            sides(logic);
            spawn(logic, "Shooter", 1, 0f);
            spawn(logic, "Wall", 2, 20f);
            assertTrue(weaponsOf(logic, 1).lock(1, WeaponUpdate.Lock.PERMANENTLY));
        }, (logic, frame) -> {
            var shooter = logic.findObject(new ObjectId(1));
            if (frame == 10) {
                shooter.setCondition("UPGRADED"); // shares the lock: the weaker cannon fires on
            } else if (frame == 70) {
                shooter.clearCondition("UPGRADED"); // the plain set does not: the lock goes
            }
        }, 25, 120);
    }

    @Test
    void aForcedAttackOnItsOwnSideFiresTheShotsItWasToldAndNoMore() {
        var gun = new WeaponUpdate.Data(5f, 100f, 4, DamageType.NORMAL);
        var told = rules(gun);
        sides(told);
        spawn(told, "Shooter", 1, 0f);
        var wall = spawn(told, "Wall", 1, 20f);
        assertTrue(weaponsOf(told, 1).attack(wall.getId(), true, OrderSource.PLAYER, 8));
        for (int frame = 0; frame < 60; frame++) {
            told.update();
        }
        assertEquals(1_000_000f - 8 * 5f, wall.getBody().getHealth(), "eight shots of five, then the attack is over");

        SaveLoadCheck.resumesOnlyWithItsModules(() -> rules(gun), logic -> {
            sides(logic);
            spawn(logic, "Shooter", 1, 0f);
            var own = spawn(logic, "Wall", 1, 20f);
            weaponsOf(logic, 1).attack(own.getId(), true, OrderSource.PLAYER, 8);
        }, (logic, frame) -> {
        }, 12, 120);
    }

    @Test
    void aGroundAttackGoesOnFiringOnItsPlace() {
        // Reaching 50, a blast of 40: the wall at 80 is out of its reach, but not of a blast at 45.
        var mortar = new WeaponUpdate.Data(5f, 50f, 6, DamageType.NORMAL, 40f);
        SaveLoadCheck.resumesOnlyWithItsModules(() -> rules(mortar), logic -> {
            sides(logic);
            spawn(logic, "Shooter", 1, 0f);
            spawn(logic, "Wall", 2, 80f);
            assertTrue(weaponsOf(logic, 1).attackGround(new Coord3D(45f, 0f, 0f)));
        }, (logic, frame) -> {
        }, 20, 120);
    }

    @Test
    void aThingLooksForATargetOnItsOwnClock() {
        // It looks every 60 frames, its first wait drawn longer; the wall walks into its reach at frame 30.
        var slow = new WeaponUpdate.Data(5f, 100f, 4, DamageType.NORMAL).targetScanFrames(60);
        SaveLoadCheck.resumesOnlyWithItsModules(() -> rules(slow), logic -> {
            sides(logic);
            spawn(logic, "Shooter", 1, 0f);
            spawn(logic, "Wall", 2, 300f);
        }, (logic, frame) -> {
            if (frame == 30) {
                logic.findObject(new ObjectId(2)).setPosition(new Coord3D(40f, 0f, 0f));
            }
        }, 20, 150);
    }

    @Test
    void aReloadSharedAndAClipPartRefilledByTheGameGoOnAlike() {
        var sets = WeaponUpdate.Data.sets(List.of(
                new WeaponSet(List.of(), List.of(new WeaponSlot("Gun"), new WeaponSlot("Cannon")))));
        var armoury = new Weapon[] {weapon("Gun", 10f, 3, 3, 4, 30), weapon("Cannon", 5f, 2, 2, 0, 0)};
        SaveLoadCheck.resumesOnlyWithItsModules(() -> rules(sets, armoury), logic -> {
            sides(logic);
            spawn(logic, "Shooter", 1, 0f);
            spawn(logic, "Wall", 2, 20f);
        }, (logic, frame) -> {
            var weapons = weaponsOf(logic, 1);
            if (frame % 11 == 0) {
                weapons.delaySlots(0, 9); // what a game's ShotListener.onSpent does
            }
            if (frame % 37 == 0) {
                weapons.refill(0.5f); // a jet half its time on the pad
            }
        }, 25, 150);
    }

    @Test
    void anAttackTowardAPointIsRefused() {
        var logic = rules(new WeaponUpdate.Data(5f, 100f, 4, DamageType.NORMAL));
        sides(logic);
        var shooter = spawn(logic, "Shooter", 1, 0f);
        shooter.findModule(WeaponUpdate.class).attackToward(new PointAttack() {
            @Override
            public Coord3D point(boolean windingUp) {
                return new Coord3D(30f, 0f, 0f);
            }

            @Override
            public GameObject struck(WeaponUpdate weapon, float reach, float facing) {
                return null;
            }

            @Override
            public boolean attacked() {
                return true;
            }
        });
        logic.update();
        var refused = assertThrows(UnsavedModuleException.class,
                () -> ModuleStates.save(shooter, logic.getThingFactory().getModuleFactory()));
        assertEquals(WeaponUpdate.class.getName(), refused.module());
    }

    /** What a game's launcher is handed, heard as it is fired. */
    private static final class Heard extends Module implements ShotListener {
        final List<Shot> shots = new ArrayList<>();

        Heard(GameObject owner) {
            super(owner);
        }

        @Override
        public void onFired(Shot shot, GameObject victim) {
            shots.add(shot);
        }
    }

    @Test
    void aShotInFlightAndAChaseAreWrittenAndReadBack() {
        var logic = rules(new WeaponUpdate.Data(5f, 100f, 4, DamageType.NORMAL, 12f), weapon("Gun", 7f, 2, 2, 0, 0));
        sides(logic);
        var shooter = spawn(logic, "Shooter", 1, 0f);
        spawn(logic, "Wall", 2, 30f);
        var heard = new Heard(shooter);
        shooter.addModule(heard);
        logic.update();
        var inPlace = heard.shots.getFirst();
        var game = new Shot(shooter.getId(), 1, logic.armoury().findWeapon("Gun"), 1, 7.5f, 2f, 1f, 4f, "Shell", true);
        for (var shot : List.of(inPlace, game)) {
            var out = new SaveOut();
            Shot.save(shot, logic, out);
            assertEquals(shot, Shot.read(new SaveIn(out.text(), logic, "Shot")));
        }
        var stranger = new Shot(shooter.getId(), 1, weapon("Nowhere", 1f, 1, 1, 0, 0), 0, 1f);
        assertThrows(UnsavedModuleException.class, () -> Shot.save(stranger, logic, new SaveOut()));

        for (var chase : java.util.Arrays.asList(new Engaging.Chase(new Coord3D(3f, 4f, 0f), true), null)) {
            var out = new SaveOut();
            Engaging.Chase.save(chase, out);
            var read = Engaging.Chase.read(new SaveIn(out.text(), logic, "Chase"));
            if (chase == null) {
                assertNull(read);
            } else {
                assertEquals(chase, read);
            }
        }
    }
}
