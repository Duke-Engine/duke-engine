package uz.duke.dungeon.power;

/**
 * The kinds of thing a level-up power can be.
 *
 * <p>The one place powers need Java, exactly as {@link uz.duke.dungeon.skill.SkillEffect}
 * is for skills. Which powers exist, what they are called, what they say, how
 * much they are worth and how often they are offered are all written in
 * {@code dungeon.ini} — so a new power is a block in a file, and a new <em>kind</em>
 * of power is a constant here plus one branch where it is read.
 *
 * <p>Each says whether it is about one skill or about the hero. That is a
 * property of the effect and not of the power: sharpening a cooldown is
 * meaningless without saying whose, and there is no useful sense in which one
 * hero's boots are the boots of his Q.
 */
public enum PowerEffect {

    /** A skill hits harder, by a percentage of what it would have hit for. */
    SKILL_DAMAGE(Scope.SKILL),

    /** A skill comes back sooner, by a percentage of its cooldown. */
    COOLDOWN(Scope.SKILL),

    /** The hero walks faster, by a percentage of his authored speed. */
    MOVE_SPEED(Scope.HERO),

    /** A share of the damage he deals comes back to him as health. */
    LIFESTEAL(Scope.HERO),

    /** A skill may be cast this many more times before it starts recharging. */
    EXTRA_CHARGE(Scope.SKILL);

    /** What a power of this kind is about. */
    public enum Scope {
        /** One skill, named by its key — or every one of them, written {@code *}. */
        SKILL,
        /** The hero himself; no key means anything. */
        HERO
    }

    /** The key a {@link Scope#SKILL} power writes to mean "all four". */
    public static final char EVERY_SKILL = '*';

    private final Scope scope;

    PowerEffect(Scope scope) {
        this.scope = scope;
    }

    public Scope scope() {
        return scope;
    }

    /** Whether this kind is aimed at a skill rather than at the hero. */
    public boolean isAboutASkill() {
        return scope == Scope.SKILL;
    }
}
