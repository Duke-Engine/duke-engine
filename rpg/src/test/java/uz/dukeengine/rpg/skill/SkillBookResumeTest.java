package uz.dukeengine.rpg.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.module.StatusUpdate;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.save.ModuleStates;
import uz.dukeengine.core.save.UnsavedModuleException;
import uz.dukeengine.rpg.RpgWorld;
import uz.dukeengine.rpg.Seal;
import uz.dukeengine.rpg.fight.ArrowUpdate;
import uz.dukeengine.rpg.save.RpgSaveCheck;

/** A hero's skills in the middle of what they do, saved, loaded and played on frame for frame as the saved ones would. */
class SkillBookResumeTest {

    private static final class Cast {
        char key = 'Q';
        SkillEffect effect = SkillEffect.STRIKE;
        float damage;
        float radius;
        float range;
        float distance;
        int boost;
        int duration;
        int tick;
        int cooldown = 90;
        int windUp;
        int mana;
        int stun;
        int healBelow;
        int maxSummoned;
        float speed;
        float heal;
        String projectile = "";
        String look = "";
        Map<String, Integer> summons = Map.of();

        Cast of(char key, SkillEffect effect) {
            this.key = key;
            this.effect = effect;
            return this;
        }

        Skill build() {
            var d = Skill.DEFAULTS;
            return new Skill("Hero", key, effect, damage, 0f, radius, range, distance, 0f, boost, 0, duration, tick,
                    0, cooldown, 0, d.maxRank(), d.levelPerRank(), windUp, mana, 0, projectile, d.icon(), look,
                    d.castAnim(), d.castSeconds(), d.name(), d.blurb(), speed, heal, healBelow, summons, maxSummoned,
                    100, stun, d.manaRegen());
        }
    }

    private static RpgWorld rules(Skill... skills) {
        return RpgSaveCheck.world(Map.of("Hero", List.of(skills), "Bearer", List.of(skills)), Seal.NONE)
                .template("Hero", new ActiveBody.Data(100f), new MoveUpdate.Data(60f), new SkillBook.Data(),
                        new StatusUpdate.Data())
                .template("Bearer", new ActiveBody.Data(100f), new SkillBook.Data())
                .template("Ally", new ActiveBody.Data(100f), new SkillBook.Data())
                .template("Skeleton", new ActiveBody.Data(100f), new StatusUpdate.Data())
                .template("Imp", new ActiveBody.Data(30f))
                .template("Arrow", new ArrowUpdate.Data())
                .template("Rift", new SummoningUpdate.Data())
                .template("Light", new MendingUpdate.Data());
    }

    private static final java.util.function.BiConsumer<RpgWorld, Integer> NONE = (world, frame) -> {
    };

    @Test
    void buffsCooldownsAndManaStandWhereTheyWereLeftAndCountDownAsBefore() {
        var empower = new Cast();
        empower.boost = 50;
        empower.duration = 60;
        empower.cooldown = 120;
        empower.mana = 5;
        var guard = new Cast().of('W', SkillEffect.GUARD);
        guard.boost = 40;
        guard.duration = 45;
        guard.cooldown = 100;
        var saved = RpgSaveCheck.resumesOnlyWithItsModules(() -> rules(empower.build(), guard.build()), world -> {
            var book = world.put("Hero", world.heroes, 0f, 0f).findModule(SkillBook.class);
            book.poolOf(30, 25);
            book.fillMana();
        }, (world, frame) -> {
            var book = world.getObjects().getFirst().findModule(SkillBook.class);
            if (frame == 2) {
                assertTrue(book.cast('Q', 1));
            }
            if (frame == 4) {
                assertTrue(book.cast('W', 1));
            }
        }, 10, 150);
        assertTrue(saved.contains(SkillBook.class.getName()), saved);
    }

    @Test
    void aChargeInFlightGoesOnCarryingWhatItHasCaught() {
        var charge = new Cast().of('Q', SkillEffect.SKEWER);
        charge.damage = 20;
        charge.radius = 10;
        charge.distance = 150;
        charge.speed = 60;
        charge.stun = 30;
        RpgSaveCheck.resumesOnlyWithItsModules(() -> rules(charge.build()), world -> {
            var knight = world.put("Hero", world.heroes, 0f, 0f);
            world.put("Skeleton", world.monsters, 30f, 0f);
            world.put("Skeleton", world.monsters, 60f, 0f);
            world.run(1);
            assertTrue(knight.findModule(SkillBook.class).cast('Q', 1, null, new Coord3D(150f, 0f, 0f)));
        }, NONE, 6, 90);
    }

    @Test
    void aShotBeingDrawnIsLoosedOnTheFrameItWasGoingTo() {
        var shot = new Cast().of('Q', SkillEffect.STRIKE);
        shot.damage = 15;
        shot.range = 100;
        shot.windUp = 25;
        shot.projectile = "Arrow";
        shot.speed = 80;
        RpgSaveCheck.resumesOnlyWithItsModules(() -> rules(shot.build()), world -> {
            var hero = world.put("Hero", world.heroes, 0f, 0f);
            world.put("Skeleton", world.monsters, 60f, 0f);
            world.run(1);
            assertTrue(hero.findModule(SkillBook.class).cast('Q', 1));
        }, NONE, 10, 60);
    }

    @Test
    void aWhirlwindKeepsTurningForTheFramesItHasLeft() {
        var whirl = new Cast().of('Q', SkillEffect.AREA_DAMAGE);
        whirl.damage = 5;
        whirl.radius = 40;
        whirl.duration = 90;
        whirl.tick = 10;
        whirl.look = "whirl";
        var saved = RpgSaveCheck.resumesOnlyWithItsModules(() -> rules(whirl.build()), world -> {
            var hero = world.put("Hero", world.heroes, 0f, 0f);
            world.put("Skeleton", world.monsters, 20f, 0f);
            world.run(1);
            assertTrue(hero.findModule(SkillBook.class).cast('Q', 1));
        }, NONE, 15, 100);
        assertTrue(saved.contains("'whirl"), saved);
    }

    @Test
    void aMountBeingMountedAndOneRiddenAreBothKept() {
        var horse = new Cast().of('M', SkillEffect.MOUNT);
        horse.boost = 100;
        horse.windUp = 10;
        horse.look = "Horse";
        for (int before : new int[] {5, 20}) {
            RpgSaveCheck.resumesOnlyWithItsModules(() -> rules(horse.build()), world -> {
                var hero = world.put("Hero", world.heroes, 0f, 0f);
                world.run(1);
                assertTrue(hero.findModule(SkillBook.class).cast('M', 1));
            }, NONE, before, 30);
        }
    }

    @Test
    void anAuraLentIsHeldForTheLifeItWasGivenAndLentAgainOnTheNextPulse() {
        var aura = new Cast().of('Q', SkillEffect.DAMAGE_AURA);
        aura.boost = 25;
        aura.radius = 50;
        var saved = RpgSaveCheck.resumesOnlyWithItsModules(() -> rules(aura.build()), world -> {
            world.put("Bearer", world.monsters, 0f, 0f);
            world.put("Ally", world.monsters, 30f, 0f);
        }, NONE, RpgWorld.RULES.auraPulseFrames() + 5, RpgWorld.RULES.buffLifeFrames() * 2);
        assertTrue(saved.contains(" DAMAGE_AURA 1 25 "), saved);
    }

    @Test
    void aRiftOpeningAndWhatClimbedOutOfItAreBothKept() {
        var summon = new Cast().of('Q', SkillEffect.SUMMON);
        summon.radius = 30;
        summon.windUp = 15;
        summon.duration = 120;
        summon.maxSummoned = 2;
        summon.projectile = "Rift";
        summon.summons = Map.of("Imp", 1);
        for (int before : new int[] {5, 25}) {
            var saved = RpgSaveCheck.resumesOnlyWithItsModules(() -> rules(summon.build()), world -> {
                var hero = world.put("Hero", world.heroes, 0f, 0f);
                world.run(1);
                assertTrue(hero.findModule(SkillBook.class).cast('Q', 1, null, new Coord3D(40f, 0f, 0f)));
            }, NONE, before, 150);
            assertTrue(saved.contains(before < 15 ? SummoningUpdate.class.getName() : Summoned.class.getName()),
                    saved);
        }
    }

    @Test
    void aLightComingDownMendsWhomItWasCalledOnWhenItLands() {
        var mend = new Cast().of('Q', SkillEffect.HEAL);
        mend.range = 100;
        mend.heal = 30;
        mend.windUp = 20;
        mend.healBelow = 100;
        mend.projectile = "Light";
        RpgSaveCheck.resumesOnlyWithItsModules(() -> rules(mend.build()), world -> {
            var hero = world.put("Hero", world.heroes, 0f, 0f);
            var patient = world.put("Ally", world.heroes, 30f, 0f);
            patient.getBody().damage(60f);
            world.run(1);
            assertTrue(hero.findModule(SkillBook.class).cast('Q', 1, patient.getId(), null));
        }, NONE, 8, 40);
    }

    @Test
    void whatABearerHasEarnedAndWhatItHoldsUpOnAreKept() {
        var blood = new Cast().of('Q', SkillEffect.DRAGON_BLOOD);
        blood.boost = 20;
        blood.heal = 3;
        var floor = new Cast().of('W', SkillEffect.UNYIELDING);
        floor.healBelow = 10;
        var souls = new Cast().of('E', SkillEffect.SOUL_DRINK);
        souls.tick = 2;
        souls.boost = 5;
        var stagger = new Cast().of('R', SkillEffect.STAGGER);
        stagger.damage = 5;
        stagger.stun = 10;
        stagger.duration = 30;
        RpgSaveCheck.resumesOnlyWithItsModules(
                () -> rules(blood.build(), floor.build(), souls.build(), stagger.build()), world -> {
                    var hero = world.put("Hero", world.heroes, 10f, 0f);
                    var skills = hero.findModule(SkillBook.class);
                    skills.setRank('Q', 2);
                    skills.setSouls(7);
                    hero.getBody().damage(60f);
                    world.run(2);
                }, (world, frame) -> {
                    if (frame % 7 == 0) {
                        world.getObjects().getFirst().getBody().damage(9f);
                    }
                }, 12, 80);
    }

    @Test
    void aBookIsRefusedByNameWhereItsTemplateNoLongerHasTheSkillsItWasSavedWith() {
        var played = rules(new Cast().build(), new Cast().of('W', SkillEffect.GUARD).build());
        var line = ModuleStates.save(played.put("Hero", played.heroes, 0f, 0f),
                played.getThingFactory().getModuleFactory());

        var changed = rules(new Cast().build());
        var refused = assertThrows(UnsavedModuleException.class, () -> ModuleStates.load(
                changed.put("Hero", changed.heroes, 0f, 0f), line, changed.getThingFactory().getModuleFactory()));
        assertEquals(SkillBook.class.getName(), refused.module());
    }
}
