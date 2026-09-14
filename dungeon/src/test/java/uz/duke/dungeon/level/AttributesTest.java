package uz.duke.dungeon.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.core.module.MoveUpdate;
import uz.duke.dungeon.content.Content;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.rts.module.WeaponUpdate;

/**
 * What an attribute is worth, asked without a dungeon.
 *
 * <p>The rules on their own, with round numbers of the test's choosing: a point of
 * strength is twelve health, of agility 0.15 speed, of intelligence five mana, and a
 * point of his primary is a point on his blow. What the shipped file says is checked
 * where it is read; what the world does with it is {@code HeroAttributesTest}.
 */
class AttributesTest {

    private static final AttributeRules RULES = new AttributeRules(1200, 15, 500, 100);

    private static final HeroAttributes KNIGHT = new HeroAttributes(Attribute.STRENGTH,
            Attributes.ofWhole(22, 10, 8), new Attributes(30, 12, 10));
    private static final HeroAttributes ARCHER = new HeroAttributes(Attribute.AGILITY,
            Attributes.ofWhole(10, 12, 8), new Attributes(16, 22, 10));

    private static final HeroBase KNIGHTS_BLOCK = new HeroBase(716f, 19.5f, 0f, 8f);
    private static final HeroBase ARCHERS_BLOCK = new HeroBase(430f, 27.2f, 0f, 2f);

    // ---- a point of each ----

    @Test
    void strengthIsHealthAgilityIsSpeedAndIntelligenceIsMana() {
        assertEquals(12, RULES.health(Attributes.ofWhole(1, 0, 0)));
        assertEquals(0.15f, RULES.speed(Attributes.ofWhole(0, 1, 0)), 0f);
        assertEquals(5, RULES.mana(Attributes.ofWhole(0, 0, 1)));
    }

    @Test
    void andNoAttributeGivesWhatAnotherGives() {
        assertEquals(0, RULES.health(Attributes.ofWhole(0, 9, 9)), "only strength is health");
        assertEquals(0f, RULES.speed(Attributes.ofWhole(9, 0, 9)), 0f, "only agility is speed");
        assertEquals(0, RULES.mana(Attributes.ofWhole(9, 9, 0)), "only intelligence is mana");
    }

    // ---- the primary ----

    @Test
    void hisPrimaryIsHisAttackAsWell() {
        var his = Attributes.ofWhole(22, 10, 8);

        assertEquals(22f, RULES.attack(his, Attribute.STRENGTH), 0f);
        assertEquals(10f, RULES.attack(his, Attribute.AGILITY), 0f);
        assertEquals(8f, RULES.attack(his, Attribute.INTELLIGENCE), 0f);
        assertEquals(0f, RULES.attack(his, null), 0f, "no primary, no blow out of his attributes");
    }

    /** A strength hero who finds strength gets both halves of it. */
    @Test
    void aStrengthHeroWhoFindsStrengthGetsHealthAndAttackBoth() {
        var without = HeroFigures.of(KNIGHTS_BLOCK, 10, KNIGHT, RULES, 1, HeroFigures.Found.NOTHING);
        var with = HeroFigures.of(KNIGHTS_BLOCK, 10, KNIGHT, RULES, 1, found(5, 0, 0));

        assertEquals(without.maxHealth() + 60f, with.maxHealth(), 0f, "five strength is sixty health");
        assertEquals(without.attack() + 5f, with.attack(), 0f,
                "and five on his swing, because strength is his primary");
        assertEquals(without.speed(), with.speed(), 0f, "and nothing on his legs");
        assertEquals(without.maxMana(), with.maxMana(), "or in his pool");
    }

    /** An archer who finds strength gets the health and nothing else. */
    @Test
    void anArcherWhoFindsStrengthGetsOnlyTheHealth() {
        var without = HeroFigures.of(ARCHERS_BLOCK, 40, ARCHER, RULES, 1, HeroFigures.Found.NOTHING);
        var with = HeroFigures.of(ARCHERS_BLOCK, 40, ARCHER, RULES, 1, found(5, 0, 0));

        assertEquals(without.maxHealth() + 60f, with.maxHealth(), 0f);
        assertEquals(without.attack(), with.attack(), 0f,
                "strength is not the archer's primary, so it is not his arrow");
    }

    /** Where agility, which is his, is both. */
    @Test
    void anArcherWhoFindsAgilityGetsSpeedAndAttackBoth() {
        var without = HeroFigures.of(ARCHERS_BLOCK, 40, ARCHER, RULES, 1, HeroFigures.Found.NOTHING);
        var with = HeroFigures.of(ARCHERS_BLOCK, 40, ARCHER, RULES, 1, found(0, 5, 0));

        assertEquals(without.speed() + 0.75f, with.speed(), 0.0001f, "five agility is 0.75 speed");
        assertEquals(without.attack() + 5f, with.attack(), 0f, "and five on his arrow");
        assertEquals(without.maxHealth(), with.maxHealth(), 0f, "and not a point of health");
    }

    // ---- what he is, all together ----

    @Test
    void theFiguresAreHisBlockAndHisAttributesTogether() {
        var knight = HeroFigures.of(KNIGHTS_BLOCK, 10, KNIGHT, RULES, 1, HeroFigures.Found.NOTHING);

        assertEquals(980f, knight.maxHealth(), 0f, "716 and twenty-two strength at twelve");
        assertEquals(21f, knight.speed(), 0.0001f, "19.5 and ten agility at 0.15");
        assertEquals(30f, knight.attack(), 0f, "8 and his twenty-two strength");
        assertEquals(50, knight.maxMana(), "10 and eight intelligence at five");

        var atFifteen = HeroFigures.of(KNIGHTS_BLOCK, 10, KNIGHT, RULES, 15,
                HeroFigures.Found.NOTHING);
        assertEquals(1484f, atFifteen.maxHealth(), 0f, "sixty-four strength at the fifteenth");
        assertEquals(72f, atFifteen.attack(), 0f);
    }

    @Test
    void whatHeFoundOnTheFloorIsAddedOnTop() {
        var found = new HeroFigures.Found(Attributes.NONE, 90, 25, 10);
        var bare = HeroFigures.of(KNIGHTS_BLOCK, 10, KNIGHT, RULES, 1, HeroFigures.Found.NOTHING);
        var with = HeroFigures.of(KNIGHTS_BLOCK, 10, KNIGHT, RULES, 1, found);

        assertEquals(bare.maxHealth() + 90f, with.maxHealth(), 0f, "a heart's flat health");
        assertEquals(bare.maxMana() + 25, with.maxMana(), "a manastone's flat mana");
        assertEquals(bare.attack() * 1.1f, with.attack(), 0.0001f, "and a blade's share of the blow");
    }

    // ---- levels ----

    @Test
    void aLevelRaisesAllThreeAndHisPrimaryMost() {
        for (var hero : new HeroAttributes[] {KNIGHT, ARCHER}) {
            var one = hero.atLevel(1);
            var two = hero.atLevel(2);
            int primaryGrew = two.of(hero.primary()) - one.of(hero.primary());
            for (var attribute : Attribute.values()) {
                int grew = two.of(attribute) - one.of(attribute);
                assertTrue(grew > 0, attribute + " did not grow");
                if (attribute != hero.primary()) {
                    assertTrue(primaryGrew > grew, hero.primary() + " grew no faster than " + attribute);
                }
            }
        }
    }

    @Test
    void theFirstLevelIsWhatHeStartsWith() {
        assertEquals(KNIGHT.base(), KNIGHT.atLevel(1));
    }

    /**
     * A fraction of a point a level is exact at every level.
     *
     * <p>1.8 fourteen times is 25.2. Held in tenths it is 252 and nothing else, and the
     * level is multiplied rather than summed, so a hero who took the fourteen levels at
     * once is the hero who took them one at a time.
     */
    @Test
    void aFractionOfAPointALevelIsExactAtEveryLevel() {
        var hero = new HeroAttributes(Attribute.STRENGTH, Attributes.NONE, new Attributes(18, 15, 30));

        assertEquals(new Attributes(252, 210, 420), hero.atLevel(15),
                "fourteen levels of 1.8, 1.5 and 3.0");
        var climbed = hero.atLevel(1);
        for (int level = 2; level <= 15; level++) {
            climbed = climbed.plus(hero.perLevel());
        }
        assertEquals(climbed, hero.atLevel(15), "one jump and fourteen steps are the same hero");
        assertEquals(25, hero.atLevel(15).whole(Attribute.STRENGTH),
                "and the panel rounds 25.2 down");
    }

    // ---- building him ----

    @Test
    void aHeroIsBuiltWithHisFirstLevelAlreadyInHim() {
        assertEquals(980f, HeroBuild.body(new GrowableBody.Data(716f), KNIGHT, RULES).maxHealth(), 0f);
        assertEquals(21f, HeroBuild.legs(new MoveUpdate.Data(19.5f), KNIGHT, RULES).speedPerSecond(),
                0.0001f);
        var weapon = HeroBuild.weapon(new WeaponUpdate.Data(8f, 11f, 34), KNIGHT, RULES);
        assertEquals(30f, weapon.damage(), 0f);
        assertEquals(11f, weapon.attackRange(), 0f, "and nothing else about the weapon moved");
        assertEquals(34, weapon.reloadFrames());
    }

    /** A creature that is nobody's hero comes through untouched: the same instance. */
    @Test
    void aCreatureThatIsNoHeroIsBuiltExactlyAsItsBlockSays() {
        var body = new GrowableBody.Data(60f);
        var legs = new MoveUpdate.Data(17f);
        var weapon = new WeaponUpdate.Data(7f, 10f, 30);

        assertSame(body, HeroBuild.body(body, HeroAttributes.NONE, RULES));
        assertSame(legs, HeroBuild.legs(legs, HeroAttributes.NONE, RULES));
        assertSame(weapon, HeroBuild.weapon(weapon, HeroAttributes.NONE, RULES));
    }

    // ---- the file ----

    @Test
    void theShippedHeroesHaveThePrimariesTheirPlayGivesThem() {
        var settings = DungeonSettings.load();

        assertEquals(Attribute.STRENGTH, settings.heroNamed("Knight").attributes().primary());
        assertEquals(Attribute.AGILITY, settings.heroNamed("Rogue").attributes().primary());
        assertEquals(Attribute.INTELLIGENCE, settings.heroNamed("Mage").attributes().primary());
        for (var hero : settings.heroes()) {
            var his = hero.attributes();
            for (var attribute : Attribute.values()) {
                if (attribute != his.primary()) {
                    assertTrue(his.perLevel().of(his.primary()) > his.perLevel().of(attribute),
                            hero.name() + "'s primary does not grow fastest");
                }
            }
        }
    }

    /** Changing a number in the file changes what it is worth, with nothing rebuilt. */
    @Test
    void theFileIsWhatDecidesIt() {
        var shipped = DungeonSettings.load();
        var text = Content.read(Content.SETTINGS);
        var knight = shipped.heroNamed("Knight").attributes().atLevel(1);

        var richer = DungeonSettings.parse(text.replace("HealthPerStrength = 12",
                "HealthPerStrength = 20"));
        assertEquals(shipped.attributeRules().health(knight) * 20 / 12,
                richer.attributeRules().health(knight), "a point of strength is worth what the file says");

        var stronger = DungeonSettings.parse(text.replace("  Strength = 22", "  Strength = 30"));
        assertEquals(300, stronger.heroNamed("Knight").attributes().base().strength());

        var quicker = DungeonSettings.parse(text.replace("  AgiPerLevel = 2.2", "  AgiPerLevel = 3.4"));
        assertEquals(34, quicker.heroNamed("Rogue").attributes().perLevel().agility());

        var swapped = DungeonSettings.parse(text.replace("  Primary = AGI", "  Primary = STR"));
        assertEquals(Attribute.STRENGTH, swapped.heroNamed("Rogue").attributes().primary());
    }

    /**
     * A decimal is read exactly, or not at all.
     *
     * <p>0.15 has no float: the nearest one times a hundred is 14.999999. It is read as
     * fifteen hundredths, and a figure with more places than its field keeps is refused
     * rather than quietly rounded into something the file did not say.
     */
    @Test
    void aDecimalIsReadExactlyOrNotAtAll() {
        var exact = DungeonSettings.parse("""
                DungeonAttributes Conversion
                  SpeedPerAgility = 0.15
                  DamagePerPrimary = 1.25
                End
                DungeonHero Solo
                  Primary = INT
                  Strength = 22.5
                  IntPerLevel = 1.8
                End
                """);

        assertEquals(15, exact.attributeRules().speedPerAgility());
        assertEquals(125, exact.attributeRules().damagePerPrimary());
        assertEquals(225, exact.heroNamed("Solo").attributes().base().strength());
        assertEquals(18, exact.heroNamed("Solo").attributes().perLevel().intelligence());

        assertThrows(RuntimeException.class, () -> DungeonSettings.parse("""
                DungeonAttributes Conversion
                  SpeedPerAgility = 0.155
                End
                """), "a third place is refused, not rounded");
        assertThrows(RuntimeException.class, () -> DungeonSettings.parse("""
                DungeonHero Solo
                  Primary = STR
                  Strength = 22.55
                End
                """), "and a second place on an attribute");
    }

    @Test
    void attributesWithNoPrimaryAreRefused() {
        assertThrows(RuntimeException.class, () -> DungeonSettings.parse("""
                DungeonHero Solo
                  Strength = 12
                End
                """), "a hero with attributes has to say which one he hits with");
    }

    private static HeroFigures.Found found(int strength, int agility, int intelligence) {
        return new HeroFigures.Found(Attributes.ofWhole(strength, agility, intelligence), 0, 0, 0);
    }
}
