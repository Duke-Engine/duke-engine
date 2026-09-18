package uz.duke.dungeon.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
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

    private static final int STR = 0;
    private static final int AGI = 1;
    private static final int INT = 2;

    private static final AttributeRules RULES = new AttributeRules(List.of(
            new Attribute("Strength", "STR", 1200, 0, 0),
            new Attribute("Agility", "AGI", 0, 15, 0),
            new Attribute("Intelligence", "INT", 0, 0, 500)), 100);

    private static final HeroAttributes KNIGHT = new HeroAttributes(STR,
            Attributes.ofWhole(22, 10, 8), Attributes.of(30, 12, 10));
    private static final HeroAttributes ARCHER = new HeroAttributes(AGI,
            Attributes.ofWhole(10, 12, 8), Attributes.of(16, 22, 10));

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

    /**
     * An attribute is whatever its block says a point is worth, and that can be more than
     * one figure: each is a sum over every attribute, not a figure owned by one.
     */
    @Test
    void anAttributeCanGiveSeveralFiguresAndTheyAddUp() {
        var rules = new AttributeRules(List.of(
                new Attribute("Strength", "STR", 1200, 0, 0),
                new Attribute("Vigour", "VIG", 500, 10, 200)), 100);
        var his = Attributes.ofWhole(10, 4);

        assertEquals(120 + 20, rules.health(his), "strength's and vigour's health together");
        assertEquals(0.4f, rules.speed(his), 0f);
        assertEquals(8, rules.mana(his));
    }

    // ---- the primary ----

    @Test
    void hisPrimaryIsHisAttackAsWell() {
        var his = Attributes.ofWhole(22, 10, 8);

        assertEquals(22f, RULES.attack(his, STR), 0f);
        assertEquals(10f, RULES.attack(his, AGI), 0f);
        assertEquals(8f, RULES.attack(his, INT), 0f);
        assertEquals(0f, RULES.attack(his, -1), 0f, "no primary, no blow out of his attributes");
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
            int primaryGrew = two.at(hero.primary()) - one.at(hero.primary());
            for (int attribute = 0; attribute < RULES.attributes().size(); attribute++) {
                int grew = two.at(attribute) - one.at(attribute);
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
        var hero = new HeroAttributes(STR, Attributes.NONE, Attributes.of(18, 15, 30));

        assertEquals(Attributes.of(252, 210, 420), hero.atLevel(15),
                "fourteen levels of 1.8, 1.5 and 3.0");
        var climbed = hero.atLevel(1);
        for (int level = 2; level <= 15; level++) {
            climbed = climbed.plus(hero.perLevel());
        }
        assertEquals(climbed, hero.atLevel(15), "one jump and fourteen steps are the same hero");
        assertEquals(25, hero.atLevel(15).whole(STR), "and the panel rounds 25.2 down");
    }

    /** A place nobody wrote is nothing, however long the list it is compared with. */
    @Test
    void aMissingPlaceIsNothingOfThatAttribute() {
        assertEquals(Attributes.NONE, Attributes.of(0, 0, 0));
        assertEquals(Attributes.of(5), Attributes.of(5, 0));
        assertEquals(Attributes.of(5).hashCode(), Attributes.of(5, 0).hashCode());
        assertEquals(0, Attributes.of(5).at(3));
        assertEquals(Attributes.of(5, 7), Attributes.of(5).plus(Attributes.of(0, 7)));
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
    void theShippedAttributesAreStrengthAgilityAndIntelligenceInThatOrder() {
        var rules = DungeonSettings.load().attributeRules();

        assertEquals(List.of("STR", "AGI", "INT"),
                rules.attributes().stream().map(Attribute::shortName).toList());
        assertEquals(1200, rules.attributes().get(STR).healthPerPoint());
        assertEquals(15, rules.attributes().get(AGI).speedPerPoint());
        assertEquals(500, rules.attributes().get(INT).manaPerPoint());
        assertEquals(100, rules.damagePerPrimary());
    }

    @Test
    void theShippedHeroesHaveThePrimariesTheirPlayGivesThem() {
        var settings = DungeonSettings.load();
        var rules = settings.attributeRules();

        assertEquals(rules.indexOf("STR"), settings.heroNamed("Knight").attributes().primary());
        assertEquals(rules.indexOf("AGI"), settings.heroNamed("Rogue").attributes().primary());
        assertEquals(rules.indexOf("INT"), settings.heroNamed("Mage").attributes().primary());
        for (var hero : settings.heroes()) {
            var his = hero.attributes();
            for (int attribute = 0; attribute < rules.attributes().size(); attribute++) {
                if (attribute != his.primary()) {
                    assertTrue(his.perLevel().at(his.primary()) > his.perLevel().at(attribute),
                            hero.name() + "'s primary does not grow fastest");
                }
            }
        }
    }

    /** Changing a number in the file changes what it is worth, with nothing rebuilt. */
    @Test
    void theFileIsWhatDecidesIt() {
        var shipped = DungeonSettings.load();
        var text = Content.settings();
        var knight = shipped.heroNamed("Knight").attributes().atLevel(1);

        var richer = DungeonSettings.parse(text.replace("  HealthPerPoint = 12",
                "  HealthPerPoint = 20"));
        assertEquals(shipped.attributeRules().health(knight) * 20 / 12,
                richer.attributeRules().health(knight), "a point of strength is worth what the file says");

        var stronger = DungeonSettings.parse(text.replace("  Attribute = STR 22 3.0",
                "  Attribute = STR 30 3.0"));
        assertEquals(300, stronger.heroNamed("Knight").attributes().base().at(STR));

        var quicker = DungeonSettings.parse(text.replace("  Attribute = AGI 12 2.2",
                "  Attribute = AGI 12 3.4"));
        assertEquals(34, quicker.heroNamed("Rogue").attributes().perLevel().at(AGI));

        var swapped = DungeonSettings.parse(text.replace("  Primary = AGI", "  Primary = STR"));
        assertEquals(STR, swapped.heroNamed("Rogue").attributes().primary());
    }

    /**
     * Another attribute is a block in the file and a line in a hero, and nothing else.
     *
     * <p>The whole promise of the list being data: vigour is invented here, in text, and
     * the knight who names it is the shipped knight with its health and mana on top — the
     * three shipped attributes keep their places, and the new one takes the next.
     */
    @Test
    void anAttributeTheFileAddsIsAHerosAttributeWithNoJava() {
        var text = Content.settings()
                .replace("  Primary = STR\n", "  Primary = STR\n  Attribute = VIG 10 1.0\n")
                + """

                World Dungeon
                  Attribute = Vigour
                    Short = VIG
                    Word = Quvvat
                    Icon = icons/stats/stat_vigour.png
                    HealthPerPoint = 3
                    ManaPerPoint = 2
                  End
                End
                """;
        var settings = DungeonSettings.parse(text);
        var rules = settings.attributeRules();

        assertEquals(List.of("STR", "AGI", "INT", "VIG"),
                rules.attributes().stream().map(Attribute::shortName).toList());
        var knight = settings.heroNamed("Knight").attributes();
        assertEquals(100, knight.base().at(3), "ten vigour, in tenths");
        var figures = HeroFigures.of(KNIGHTS_BLOCK, 10, knight, rules, 1, HeroFigures.Found.NOTHING);
        assertEquals(980f + 30f, figures.maxHealth(), 0f, "the shipped knight and ten vigour at three");
        assertEquals(50 + 20, figures.maxMana(), "and at two mana a point");
        assertEquals(0, settings.heroNamed("Rogue").attributes().base().at(3),
                "a hero who names none of it has none");

        var art = settings.attributeArt().get(3);
        assertEquals("Quvvat", art.word());
        assertEquals("icons/stats/stat_vigour.png", art.icon(), "the path as the file writes it");
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
                World Dungeon
                  Attributes = Conversion
                    DamagePerPrimary = 1.25
                  End
                  Attribute = Agility
                    Short = AGI
                    SpeedPerPoint = 0.15
                  End
                End
                Hero Solo
                  Primary = INT
                  Attribute = STR 22.5 0
                  Attribute = INT 0 1.8
                End
                """);
        var rules = exact.attributeRules();

        assertEquals(15, rules.attributes().get(rules.indexOf("AGI")).speedPerPoint());
        assertEquals(125, rules.damagePerPrimary());
        assertEquals(225, exact.heroNamed("Solo").attributes().base().at(STR));
        assertEquals(18, exact.heroNamed("Solo").attributes().perLevel().at(INT));

        assertThrows(RuntimeException.class, () -> DungeonSettings.parse("""
                World Dungeon
                  Attribute = Agility
                    Short = AGI
                    SpeedPerPoint = 0.155
                  End
                End
                """), "a third place is refused, not rounded");
        assertThrows(RuntimeException.class, () -> DungeonSettings.parse("""
                Hero Solo
                  Primary = STR
                  Attribute = STR 22.55 0
                End
                """), "and a second place on an attribute");
    }

    @Test
    void attributesWithNoPrimaryAreRefused() {
        assertThrows(RuntimeException.class, () -> DungeonSettings.parse("""
                Hero Solo
                  Attribute = STR 12 0
                End
                """), "a hero with attributes has to say which one he hits with");
    }

    /** A hero's line is which, what he starts with and what a level adds — all three. */
    @Test
    void aHerosAttributeLineHasToBeWhole() {
        for (var line : new String[] {
            "  Attribute = LUCK 5 1\n", // no attribute is called that
            "  Attribute = STR 12\n", // what a level adds is missing
            "  Attribute = STR 12 1 5\n", // and something is left over
            "  Attribute = STR 12 1\n  Attribute = Strength 3 0\n", // the same one twice
        }) {
            assertThrows(RuntimeException.class, () -> DungeonSettings.parse(
                    "Hero Solo\n  Primary = STR\n" + line + "End\n"), line);
        }
        assertThrows(RuntimeException.class, () -> DungeonSettings.parse("""
                Hero Solo
                  Primary = LUCK
                End
                """), "a primary has to be one of the file's attributes");
    }

    /** Two attributes answering to one name would leave a hero's line meaning either. */
    @Test
    void noTwoAttributesMayAnswerToOneName() {
        assertThrows(RuntimeException.class, () -> DungeonSettings.parse("""
                World Dungeon
                  Attribute = Might
                    Short = STR
                  End
                End
                """));
    }

    /** An item that gives an attribute names one the file has, and only such an item does. */
    @Test
    void anAttributeItemNamesAnAttributeTheFileHas() {
        var tome = DungeonSettings.parse("""
                World Dungeon
                  LootItem = Tome
                    Kind = ATTRIBUTE
                    Attribute = STR
                    Value = 3
                  End
                End
                """);
        assertTrue(tome.loot().stream().anyMatch(item -> item.attribute().equals("STR")));

        assertThrows(RuntimeException.class, () -> DungeonSettings.parse("""
                World Dungeon
                  LootItem = Tome
                    Kind = ATTRIBUTE
                    Attribute = LUCK
                    Value = 3
                  End
                End
                """), "no attribute is called that");
        assertThrows(RuntimeException.class, () -> DungeonSettings.parse("""
                World Dungeon
                  LootItem = Tome
                    Kind = HEALTH
                    Attribute = STR
                    Value = 3
                  End
                End
                """), "a heart gives health, and naming an attribute on it means nothing");
    }

    private static HeroFigures.Found found(int strength, int agility, int intelligence) {
        return new HeroFigures.Found(Attributes.ofWhole(strength, agility, intelligence), 0, 0, 0);
    }
}
