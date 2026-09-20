package uz.dukeengine.dungeon.content;

import uz.dukeengine.client3d.Held;
import uz.dukeengine.core.content.AnimationSet;
import uz.dukeengine.core.data.Clip;
import uz.dukeengine.core.data.Group;
import uz.dukeengine.core.data.Link;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.thing.Classified;
import uz.dukeengine.core.thing.Drawn;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.Kind;
import uz.dukeengine.core.thing.Sighted;
import uz.dukeengine.core.thing.Solid;
import uz.dukeengine.core.thing.Titled;
import uz.dukeengine.dungeon.level.AttributeRules;
import uz.dukeengine.dungeon.level.Attributes;
import uz.dukeengine.dungeon.level.HeroAttributes;
import uz.dukeengine.dungeon.skill.Skill;

/**
 * A hero, as one {@code Hero} block writes him — the whole of him: what the engine builds
 * him from, what he is made of, what he looks like and carries, his face in the panel, and
 * his skills, each a {@code Skill} block in its {@code Skills = [ … ]}.
 *
 * @param primary    which attribute he hits with, by its short name; none for a hero who
 *                   has no attributes
 * @param attributes each attribute he has, by its short name: what he starts with and what a
 *                   level adds, {@code STR = [10, 1.6]}
 * @param animations the {@link AnimationSet} he moves by; his own clips are the ones he plays differently
 * @param held       what he carries, each a {@code Held} block in {@code Held = [ … ]}, in the order written
 */
public record Hero(@Group("Identity") String name, String displayName, Set<Kind> kindOf,
        @Group("Body") float visionRange, Geometry geometry,
        @Group("Modules") List<ModuleData> modules,
        @Group("Identity") String title, @Group("Stats") float closeDistance, int armourPercent, int maxMana,
        int manaRegen, int healthRegen, String primary, Map<String, Growth> attributes,
        @Group("Look") String model, String texture, float modelScale, float facing,
        @Group("Animation") @Link(AnimationSet.class) String animations,
        @Clip String idle, @Clip String walk, @Clip String attack, @Clip String hurt, @Clip String death,
        @Group("Look") List<Held> held,
        @Group("Skills") PortraitArt portrait, List<Skill> skills) implements Solid, Sighted, Classified, Titled, Drawn {

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
            null, null, 1f, 90f, null,
            null, null, null, null, null, List.of(),
            null, List.of());

    public Hero {
        displayName = displayName == null ? "" : displayName;
        kindOf = kindOf == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(kindOf));
        geometry = geometry == null ? Geometry.POINT : geometry;
        modules = modules == null ? List.of() : List.copyOf(modules);
        title = title == null ? "" : title;
        attributes = attributes == null ? Map.of() : attributes;
        held = held == null ? List.of() : List.copyOf(held);
        skills = skills == null ? List.of() : List.copyOf(skills);
    }

    /**
     * What he is drawn as and what he is made of, with his attributes laid out as the rules order them,
     * moving by {@code set}: its libraries, and its clip wherever he names none of his own.
     */
    public HeroLook look(AttributeRules rules, AnimationSet set) {
        return new HeroLook(name, title, closeDistance, armourPercent, maxMana, manaRegen, healthRegen,
                attributes(rules), model, texture, modelScale, facing, set.libraries(),
                idle != null ? idle : set.idle(),
                walk != null ? walk : set.walk(),
                attack != null ? attack : set.attack(),
                hurt != null ? hurt : set.hurt(),
                death != null ? death : set.death(),
                held);
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
