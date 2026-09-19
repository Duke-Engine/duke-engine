package uz.duke.dungeon.content;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import uz.duke.core.module.ModuleData;
import uz.duke.core.thing.Classified;
import uz.duke.core.thing.Geometry;
import uz.duke.core.thing.Kind;
import uz.duke.core.thing.Sighted;
import uz.duke.core.thing.Solid;
import uz.duke.core.thing.Titled;
import uz.duke.dungeon.level.AttributeRules;
import uz.duke.dungeon.level.Attributes;
import uz.duke.dungeon.level.HeroAttributes;
import uz.duke.dungeon.skill.Skill;

/**
 * A hero, as one {@code Hero} block writes him — the whole of him: what the engine builds
 * him from, what he is made of, what he looks like and carries, his face in the panel, and
 * his skills, each a {@code Skill} block inside this one.
 *
 * @param primary    which attribute he hits with, by its short name; none for a hero who
 *                   has no attributes
 * @param attributes each attribute he has, by its short name: what he starts with and what a
 *                   level adds, {@code STR = [10, 1.6]}
 * @param held       what he carries, each a {@code Held} block, in the order written
 */
public record Hero(String name, String displayName, Set<Kind> kindOf, float visionRange, Geometry geometry,
        List<ModuleData> modules,
        String title, float closeDistance, int armourPercent, int maxMana, int manaRegen, int healthRegen,
        String primary, Map<String, Growth> attributes,
        String model, String texture, float modelScale, float facing, List<String> animationsFrom,
        String idle, String walk, String attack, String hurt, String death, List<Held> held,
        PortraitArt portrait, List<Skill> skills) implements Solid, Sighted, Classified, Titled {

    /**
     * A number read to exact tenths, never through a float: a level of 1.8 fifteen times over
     * has to be 27 on every machine.
     */
    public record Tenths(int value) {
        public static Tenths of(String written) {
            try {
                return new Tenths(new BigDecimal(written.trim()).movePointRight(1).intValueExact());
            } catch (NumberFormatException | ArithmeticException wrong) {
                throw new IllegalArgumentException(written + " is not a number with at most one decimal place");
            }
        }
    }

    /** What he starts with in one attribute, and what a level adds to it. */
    public record Growth(Tenths base, Tenths perLevel) {
    }

    /** What a block leaves out. */
    static final Hero DEFAULTS = new Hero("", "", Set.of(), 0f, Geometry.POINT, List.of(),
            "", 0f, 0, 0, 0, 0,
            null, Map.of(),
            null, null, 1f, 90f, List.of(),
            null, null, null, null, null, List.of(),
            null, List.of());

    public Hero {
        displayName = displayName == null ? "" : displayName;
        kindOf = kindOf == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(kindOf));
        geometry = geometry == null ? Geometry.POINT : geometry;
        modules = modules == null ? List.of() : List.copyOf(modules);
        title = title == null ? "" : title;
        attributes = attributes == null ? Map.of() : attributes;
        animationsFrom = animationsFrom == null ? List.of() : List.copyOf(animationsFrom);
        held = held == null ? List.of() : List.copyOf(held);
        skills = skills == null ? List.of() : List.copyOf(skills);
    }

    /** What he is drawn as and what he is made of, with his attributes laid out as the rules order them. */
    public HeroLook look(AttributeRules rules) {
        return new HeroLook(name, title, closeDistance, armourPercent, maxMana, manaRegen, healthRegen,
                attributes(rules), model, texture, modelScale, facing, animationsFrom, idle, walk, attack,
                hurt, death, held);
    }

    /**
     * His attributes laid out in the rules' order, whatever order his block names them in. One
     * he has no line for is none of it, and never grows.
     */
    private HeroAttributes attributes(AttributeRules rules) {
        if (primary == null) {
            return HeroAttributes.NONE;
        }
        int size = rules.attributes().size();
        var base = new int[size];
        var perLevel = new int[size];
        for (var attribute : attributes.entrySet()) {
            int at = rules.indexOf(attribute.getKey());
            if (at >= 0) {
                base[at] = attribute.getValue().base().value();
                perLevel[at] = attribute.getValue().perLevel().value();
            }
        }
        return new HeroAttributes(rules.indexOf(primary), Attributes.of(base), Attributes.of(perLevel));
    }
}
