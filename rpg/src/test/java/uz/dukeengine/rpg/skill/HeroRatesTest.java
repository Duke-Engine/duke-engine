package uz.dukeengine.rpg.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.rpg.RpgWorld;
import uz.dukeengine.rpg.Seal;
import uz.dukeengine.rpg.item.ItemUse;
import uz.dukeengine.rpg.item.Loot;
import uz.dukeengine.rpg.item.LootBag;
import uz.dukeengine.rpg.hero.AttackSpeed;
import uz.dukeengine.rpg.hero.HeroRates;

/** A hero's rates in one record, read off what acts on him: a worn thing's lifesteal, a quicker weapon. */
class HeroRatesTest {

    private static Skill lifesteal(int percent) {
        var d = Skill.DEFAULTS;
        return new Skill("Fang", 'Q', SkillEffect.LIFESTEAL, d.damage(), d.damagePerLevel(), d.radius(), d.range(),
                d.distance(), d.hitWidth(), percent, d.boostPerLevel(), d.durationFrames(), d.tickFrames(),
                d.slowFrames(), d.cooldownFrames(), d.cooldownPerLevel(), d.maxRank(), d.levelPerRank(),
                d.windUpFrames(), d.manaCost(), d.manaCostPerLevel(), d.projectile(), d.icon(), d.look(),
                d.castAnim(), d.castSeconds(), d.name(), d.blurb(), d.projectileSpeed(), d.heal(),
                d.healBelowPercent(), d.summons(), d.maxSummoned(), d.summonExperiencePercent(), d.stunFrames(),
                d.manaRegen());
    }

    @Test
    void aWornLifestealThingAndAQuickerWeaponAreReadBackFromTheOneRecord() {
        var world = new RpgWorld(Map.of(), Seal.NONE)
                .template("Hero", new ActiveBody.Data(100f), new WeaponUpdate.Data(10f, 20f, 30), new SkillBook.Data());
        var hero = world.put("Hero", world.heroes, 0f, 0f);
        assertEquals(1f, HeroRates.of(hero).blowsPerSecond(), 1e-4f, "a blow a second: 30 frames a wait");
        assertEquals(0, HeroRates.of(hero).lifestealPercent());

        var bag = new LootBag(List.of("Tumor"), 4, LootBag.JOIN, LootBag.TOP_LEVEL);
        hero.findModule(SkillBook.class).lendFrom(bag);
        bag.take(new Loot("fang", "Tish", "", "", 0, 1, 1, "", "", 0, 0, 1, ItemUse.NONE, "", "Tumor",
                List.of(lifesteal(12))), 0, 0);
        var quicker = new AttackSpeed(hero);
        hero.addModule(quicker);
        quicker.percent(50);

        var rates = HeroRates.of(hero);
        assertEquals(12, rates.lifestealPercent(), "the worn thing's lent lifesteal");
        assertEquals(1.5f, rates.blowsPerSecond(), 1e-4f, "half as quick again");
    }
}
