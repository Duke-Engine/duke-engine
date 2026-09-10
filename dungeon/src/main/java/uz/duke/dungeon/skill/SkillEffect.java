package uz.duke.dungeon.skill;

/**
 * The kinds of thing a skill can do.
 *
 * <p>The one place skills need Java. Everything else about them — which hero has
 * them, which key casts them, how hard they hit, how long they take to come back,
 * how they grow with a level — is written in {@code dungeon.ini}, so a second hero
 * with a different four is a file change and nothing more.
 *
 * <p>Kept to four on purpose. These are the shapes a melee dungeon hero needs:
 * hit one thing hard, hit everything near you, be somewhere else, be briefly
 * stronger. A fifth shape is a new constant and one branch in
 * {@link SkillBook}; a fifth <em>skill</em> is neither.
 *
 * <p>Each carries what the player has to point at before it can be cast. That is
 * a property of the effect rather than of the skill: a strike is aimed at
 * something whatever its numbers say, and there is no useful sense in which one
 * hero's dash is aimed and another's is not.
 */
public enum SkillEffect {

    /** Damage one chosen enemy within range. */
    STRIKE(Aim.UNIT),

    /** Damage every enemy within a radius of the caster. */
    AREA_DAMAGE(Aim.SELF),

    /** Move the caster toward a chosen spot — closing or escaping. */
    DASH(Aim.OPEN_GROUND),

    /** Raise the caster's own damage for a while. */
    EMPOWER(Aim.SELF);

    /** What a player has to click before the cast can go through. */
    public enum Aim {
        /** Nothing: it goes off where he stands, the moment the key is pressed. */
        SELF,
        /** A creature. */
        UNIT,
        /**
         * A spot on the floor he could stand on and has already seen.
         *
         * <p>Not stone, because a leap that ends in rock is not a leap; and not
         * the unlit dark, because there the player cannot tell rock from room and
         * would be guessing rather than choosing. Somewhere he lit once and has
         * since forgotten still counts -- he knows what is there.
         */
        OPEN_GROUND
    }

    private final Aim aim;

    SkillEffect(Aim aim) {
        this.aim = aim;
    }

    /** What has to be pointed at for this effect to be cast. */
    public Aim aim() {
        return aim;
    }
}
