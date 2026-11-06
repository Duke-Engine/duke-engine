package uz.dukeengine.rpg.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.rpg.RpgWorld;
import uz.dukeengine.rpg.Seal;
import uz.dukeengine.rpg.fight.ArrowUpdate;
import uz.dukeengine.rpg.fight.Arrows;
import uz.dukeengine.rpg.fight.Bow;
import uz.dukeengine.rpg.fight.EyesOnly;
import uz.dukeengine.rpg.fight.FallingUpdate;
import uz.dukeengine.rpg.fight.Swing;

/** Shots in the air, a mark that is falling, a blow struck: saved mid-flight, loaded, and played on frame for frame. */
class FightResumeTest {

    private static RpgWorld rules() {
        return RpgSaveCheck.world(Map.of(), Seal.NONE)
                .template("Archer", new ActiveBody.Data(100f), new Bow.Data(null, 0f, -1f), new EyesOnly.Data(),
                        new Swing.Data())
                .template("Target", new ActiveBody.Data(500f))
                .template("Arrow", new ArrowUpdate.Data())
                .template("Meteor", new FallingUpdate.Data());
    }

    @Test
    void anArrowOnItsWayGoesOnToWhatItWasLoosedAtAndHurtsItOnce() {
        var saved = RpgSaveCheck.resumesOnlyWithItsModules(FightResumeTest::rules, world -> {
            var archer = world.put("Archer", world.heroes, 0f, 0f);
            var target = world.put("Target", world.monsters, 200f, 0f);
            assertTrue(Arrows.loose(archer, target, 12f, DamageType.FLAME, "Arrow", 120f, 0f, true));
        }, (world, frame) -> {
        }, 4, 40);
        assertTrue(saved.contains(ArrowUpdate.class.getName() + " 2 1 12.0 'FLAME"), saved);
    }

    @Test
    void aFreeShotKeepsWhatItHasLeftToTravelAndItsBurst() {
        RpgSaveCheck.resumesOnlyWithItsModules(FightResumeTest::rules, world -> {
            var archer = world.put("Archer", world.heroes, 0f, 0f);
            world.put("Target", world.monsters, 150f, 0f);
            world.put("Target", world.monsters, 165f, 12f);
            assertTrue(Arrows.looseAlong(archer, new Coord3D(300f, 0f, 0f), 9f, DamageType.NORMAL, "Arrow", 100f, 0f,
                    300f, 30f, 20));
        }, (world, frame) -> {
        }, 10, 40);
    }

    @Test
    void aWaveRemembersWhomItHasHurtAndPassesThemOnceMore() {
        RpgSaveCheck.resumesOnlyWithItsModules(FightResumeTest::rules, world -> {
            var archer = world.put("Archer", world.heroes, 0f, 0f);
            world.put("Target", world.monsters, 20f, 0f);
            world.put("Target", world.monsters, 60f, 3f);
            assertTrue(Arrows.waveAlong(archer, new Coord3D(200f, 0f, 0f), 7f, DamageType.NORMAL, "Arrow", 60f, 0f,
                    200f, 14f, 0));
        }, (world, frame) -> {
        }, 20, 60);
    }

    @Test
    void aMarkFallingLandsWhereAndWhenItWasGoingTo() {
        RpgSaveCheck.resumesOnlyWithItsModules(FightResumeTest::rules, world -> {
            var caster = world.put("Archer", world.heroes, 0f, 0f);
            world.put("Target", world.monsters, 100f, 0f);
            var meteor = world.put("Meteor", world.heroes, 100f, 0f);
            meteor.findModule(FallingUpdate.class).callDown(caster, 40f, 30f, 25);
        }, (world, frame) -> {
        }, 8, 30);
    }

    @Test
    void theBlowLastStruckIsRememberedByTheSwingThatStruckIt() {
        var saved = RpgSaveCheck.resumesOnlyWithItsModules(FightResumeTest::rules, world -> {
            var archer = world.put("Archer", world.heroes, 0f, 0f);
            var target = world.put("Target", world.monsters, 5f, 0f);
            world.run(3);
            archer.findModule(Swing.class).launch(archer, target, 4f, DamageType.NORMAL);
        }, (world, frame) -> {
        }, 5, 10);
        assertTrue(saved.contains(Swing.class.getName() + " 3"), saved);
    }

    @Test
    void aBowAndEyesHoldNothingAndAreNamedInTheSave() {
        var saved = RpgSaveCheck.resumes(FightResumeTest::rules, world -> world.put("Archer", world.heroes, 0f, 0f),
                (world, frame) -> {
                }, 3, 3);
        assertTrue(saved.contains("|1 " + Bow.class.getName() + "|2 " + EyesOnly.class.getName()), saved);
        assertEquals(1, List.of(saved.split("\n")).stream().filter(line -> line.startsWith("MODULES 1|")).count());
    }
}
