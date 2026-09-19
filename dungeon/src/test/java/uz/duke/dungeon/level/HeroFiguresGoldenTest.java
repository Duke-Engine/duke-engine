package uz.duke.dungeon.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The attribute arithmetic, held to the bit against what it came to before the list of
 * attributes moved into the file.
 *
 * <p>{@code golden/hero_figures.txt} was written by the code that knew only strength,
 * agility and intelligence by name: the three shipped heroes, every level from one to
 * fifteen, and six things found on the floor — nothing, each attribute alone, all of them
 * with health, mana and a blade together, and a blade alone. Every float is compared as
 * its bits, so a sum taken in another order or a division moved by one step is a failure
 * here and not a rounding nobody notices.
 *
 * <p>The heroes are written out below rather than read from {@code data/units/}, so
 * re-balancing the file does not break this. It is the arithmetic being held still, not
 * the balance.
 */
class HeroFiguresGoldenTest {

    private static final AttributeRules RULES = new AttributeRules(List.of(
            new Attribute("Strength", "STR", "", "", new Hundredths(1200), new Hundredths(0), new Hundredths(0)),
            new Attribute("Agility", "AGI", "", "", new Hundredths(0), new Hundredths(15), new Hundredths(0)),
            new Attribute("Intelligence", "INT", "", "", new Hundredths(0), new Hundredths(0), new Hundredths(500))), 100);

    private record Hero(String name, HeroBase block, int mana, HeroAttributes attributes) {
    }

    private record Find(String name, HeroFigures.Found found) {
    }

    /** As {@code creatures.ini} and {@code dungeon.ini} had them, in the file's order. */
    private static final List<Hero> HEROES = List.of(
            new Hero("Rogue", new HeroBase(430f, 27.2f, 0f, 2f), 40, new HeroAttributes(1,
                    Attributes.of(100, 120, 80), Attributes.of(16, 22, 10))),
            new Hero("Knight", new HeroBase(716f, 19.5f, 0f, 8f), 10, new HeroAttributes(0,
                    Attributes.of(220, 100, 80), Attributes.of(30, 12, 10))),
            new Hero("Mage", new HeroBase(284f, 23.65f, 0f, 2f), 70, new HeroAttributes(2,
                    Attributes.of(80, 90, 100), Attributes.of(12, 10, 20))));

    private static final List<Find> FINDS = List.of(
            new Find("NOTHING", HeroFigures.Found.NOTHING),
            new Find("STR5", new HeroFigures.Found(Attributes.ofWhole(5, 0, 0), 0, 0, 0)),
            new Find("AGI7", new HeroFigures.Found(Attributes.ofWhole(0, 7, 0), 0, 0, 0)),
            new Find("INT9", new HeroFigures.Found(Attributes.ofWhole(0, 0, 9), 0, 0, 0)),
            new Find("MIX", new HeroFigures.Found(Attributes.ofWhole(3, 4, 5), 40, 25, 14)),
            new Find("PCT", new HeroFigures.Found(Attributes.NONE, 0, 0, 22)));

    @Test
    void everyFigureIsWhatItWasToTheBit() throws IOException {
        var expected = golden();
        var actual = new ArrayList<String>();
        for (var hero : HEROES) {
            for (int level = 1; level <= 15; level++) {
                for (var find : FINDS) {
                    var f = HeroFigures.of(hero.block(), hero.mana(), hero.attributes(), RULES,
                            level, find.found());
                    actual.add(String.join("|", hero.name(), String.valueOf(level), find.name(),
                            f.attributes().at(0) + "," + f.attributes().at(1) + ","
                                    + f.attributes().at(2),
                            String.valueOf(f.bonusHealth()),
                            String.valueOf(Float.floatToIntBits(f.maxHealth())),
                            String.valueOf(Float.floatToIntBits(f.speed())),
                            String.valueOf(Float.floatToIntBits(f.attack())),
                            String.valueOf(f.maxMana())));
                }
            }
        }

        assertEquals(expected.size(), actual.size(), "a different number of figures");
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), actual.get(i), "line " + (i + 1) + " of the golden file");
        }
    }

    private static List<String> golden() throws IOException {
        try (InputStream in = HeroFiguresGoldenTest.class.getResourceAsStream(
                "/golden/hero_figures.txt")) {
            assertNotNull(in, "golden/hero_figures.txt is missing from the test resources");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> !line.isBlank())
                    .toList();
        }
    }
}
