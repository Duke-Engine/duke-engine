package uz.dukeengine.rpg.save;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.rpg.RpgWorld;
import uz.dukeengine.rpg.Seal;
import uz.dukeengine.rpg.hero.AttackBonus;
import uz.dukeengine.rpg.hero.AttackSpeed;
import uz.dukeengine.rpg.hero.CriticalStrike;
import uz.dukeengine.rpg.hero.FountainUpdate;
import uz.dukeengine.rpg.hero.GrowableBody;
import uz.dukeengine.rpg.hero.LevelBonus;
import uz.dukeengine.rpg.hero.Recovery;

/** A hero's body, his recovery, what he carries and the level a creature stands at, saved and played on. */
class HeroResumeTest {

    private static RpgWorld rules() {
        return RpgSaveCheck.world(Map.of(), Seal.NONE)
                .template("Hero", new GrowableBody.Data(100f), new Recovery.Data())
                .template("Monster", new ActiveBody.Data(80f))
                .template("Fountain", new FountainUpdate.Data(40f, 10, 10, 10));
    }

    @Test
    void aGrownBodyKeepsItsCeilingItsArmourAndItsHealthAndRecoversOnTowardIt() {
        var saved = RpgSaveCheck.resumesOnlyWithItsModules(HeroResumeTest::rules, world -> {
            var hero = world.put("Hero", world.heroes, 0f, 0f);
            var body = (GrowableBody) hero.getBody();
            body.growMaxHealth(30f);
            body.setDamageTaken(0.5f);
            body.damage(80f);
            hero.findModule(Recovery.class).rate(55);
        }, (world, frame) -> {
        }, 17, 90);
        assertTrue(saved.contains(GrowableBody.class.getName()), saved);
        assertTrue(saved.contains(Recovery.class.getName() + " 55"), saved);
    }

    @Test
    void whatAHeroCarriesAndALevelAreMadeAgainWithTheirFigures() {
        var saved = RpgSaveCheck.resumesOnlyWithItsModules(HeroResumeTest::rules, world -> {
            var hero = world.put("Hero", world.heroes, 0f, 0f);
            var attack = new AttackBonus(hero);
            attack.multiplier(1.4f);
            hero.addModule(attack);
            var quick = new AttackSpeed(hero);
            quick.percent(35);
            hero.addModule(quick);
            var critical = new CriticalStrike(hero);
            critical.figures(30, 80);
            hero.addModule(critical);
            var monster = world.put("Monster", world.monsters, 50f, 0f);
            monster.addModule(new LevelBonus(monster, 7, 1.6f, 2.5f));
        }, (world, frame) -> {
        }, 6, 20);
        assertTrue(saved.contains(AttackBonus.class.getName() + " 1.4"), saved);
        assertTrue(saved.contains(AttackSpeed.class.getName() + " 1.35"), saved);
        assertTrue(saved.contains(CriticalStrike.class.getName() + " 30 80"), saved);
        assertTrue(saved.contains(LevelBonus.class.getName() + " 7 1.6 2.5"), saved);
    }

    @Test
    void aFountainHoldsNothingAndMendsAsBefore() {
        RpgSaveCheck.resumes(HeroResumeTest::rules, world -> {
            world.put("Fountain", world.heroes, 0f, 0f);
            world.put("Hero", world.heroes, 10f, 0f).getBody().damage(70f);
            world.put("Monster", world.heroes, 20f, 0f).getBody().damage(40f);
        }, (world, frame) -> {
        }, 13, 60);
    }
}
