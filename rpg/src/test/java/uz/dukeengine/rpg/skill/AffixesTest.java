package uz.dukeengine.rpg.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.module.BlowRoll;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.rpg.RpgWorld;
import uz.dukeengine.rpg.Seal;
import uz.dukeengine.rpg.hero.Attribute;
import uz.dukeengine.rpg.hero.AttributeRules;
import uz.dukeengine.rpg.hero.Attributes;
import uz.dukeengine.rpg.hero.CriticalStrike;
import uz.dukeengine.rpg.hero.HeroAttributes;
import uz.dukeengine.rpg.hero.HeroBase;
import uz.dukeengine.rpg.hero.HeroFigures;
import uz.dukeengine.core.math.Hundredths;
import uz.dukeengine.rpg.item.Affix;
import uz.dukeengine.rpg.item.ItemUse;
import uz.dukeengine.rpg.item.Loot;
import uz.dukeengine.rpg.item.LootBag;
import uz.dukeengine.rpg.item.Stat;

/** A thing's affixes move figures while worn and none in the backpack; crit, move speed and a worn skill rank. */
class AffixesTest {

    private static final AttributeRules RULES = new AttributeRules(List.of(
            new Attribute("Strength", "STR", "", "", new Hundredths(1200), new Hundredths(0), new Hundredths(0))),
            100);

    private static Loot ring(List<Affix> affixes) {
        return new Loot("ring", "Uzuk", "", Stat.HEALTH, 10, 1, 1, "", "", 0, 0, 1, ItemUse.NONE, "", "Uzuk", List.of(),
                affixes);
    }

    private static LootBag bag() {
        return new LootBag(List.of("Uzuk"), 2, LootBag.JOIN, LootBag.TOP_LEVEL);
    }

    @Test
    void aWornThingWithTwoAffixesMovesTwoFiguresAndOneInTheBackpackMovesNone() {
        var bag = bag();
        bag.take(ring(List.of(new Affix(Stat.ATTACK, 9), new Affix(Stat.HEALTH, 30), new Affix("Strength", 3))), 0, 0);
        assertEquals(40, bag.health(), "its own ten and an affix's thirty");
        assertEquals(9, bag.attackPercent());
        assertEquals(30, bag.attributes(RULES).at(0), "an affix naming an attribute: three whole points");

        bag.shift(bag.wornPlace("Uzuk"));
        assertEquals(0, bag.health(), "carried, it moves nothing");
        assertEquals(0, bag.attackPercent());
    }

    @Test
    void aCriticalStrikeRollsOnTheWorldsRandomNumbersOnBlowsAndSkills() {
        var world = new RpgWorld(Map.of("Hero", List.of(area())), Seal.NONE)
                .template("Hero", new ActiveBody.Data(100f), new SkillBook.Data())
                .template("Skeleton", new ActiveBody.Data(1000f));
        var hero = world.put("Hero", world.heroes, 0f, 0f);
        var skeleton = world.put("Skeleton", world.monsters, 10f, 0f);
        var critical = new CriticalStrike(hero);
        hero.addModule(critical);
        world.run(1);

        assertEquals(1f, BlowRoll.of(hero, world), "no chance, no crit");
        critical.figures(100, 50);
        assertEquals(1.5f, BlowRoll.of(hero, world), 1e-6f, "every blow, half as hard again");

        hero.findModule(SkillBook.class).cast('Q', 1);
        assertEquals(1000f - 30f, skeleton.getBody().getHealth(), 1e-3f, "a skill's twenty, critical");
        assertTrue(world.drainEvents().stream().anyMatch(event -> event instanceof uz.dukeengine.core.event.ObjectHurt hurt
                && hurt.critical()), "its hurt told critical");
        critical.figures(0, 50);
        assertEquals(1f, BlowRoll.of(hero, world), "taken off: none");
    }

    @Test
    void moveSpeedIsAShareOfHisWholeSpeed() {
        var base = new HeroBase(400f, 30f, 0f, 2f);
        var hero = new HeroAttributes(0, Attributes.ofWhole(0), Attributes.ofWhole(0));
        float plain = HeroFigures.of(base, 0, hero, RULES, 1, HeroFigures.Found.NOTHING).speed();
        float quick = HeroFigures.of(base, 0, hero, RULES, 1,
                new HeroFigures.Found(Attributes.NONE, 0, 0, 0, 20)).speed();
        assertEquals(plain * 1.2f, quick, 1e-4f);
    }

    @Test
    void aWornThingRaisesASkillsRankWhileItIsWorn() {
        var bag = bag();
        bag.take(ring(List.of(new Affix(Stat.SKILL_RANK + ":Q", 1))), 0, 0);
        assertEquals(1, bag.skillRanks('q'));
        bag.shift(bag.wornPlace("Uzuk"));
        assertEquals(0, bag.skillRanks('Q'), "taken off: gone");
    }

    private static Skill area() {
        var d = Skill.DEFAULTS;
        return new Skill("Hero", 'Q', SkillEffect.AREA_DAMAGE, 20f, 0f, 30f, d.range(), d.distance(), d.hitWidth(),
                d.boostPercent(), d.boostPerLevel(), d.durationFrames(), d.tickFrames(), d.slowFrames(),
                d.cooldownFrames(), d.cooldownPerLevel(), d.maxRank(), d.levelPerRank(), d.windUpFrames(), 0, 0,
                d.projectile(), d.icon(), d.look(), d.castAnim(), d.castSeconds(), d.name(), d.blurb(),
                d.projectileSpeed(), d.heal(), d.healBelowPercent(), d.summons(), d.maxSummoned(),
                d.summonExperiencePercent(), d.stunFrames(), d.manaRegen());
    }
}
