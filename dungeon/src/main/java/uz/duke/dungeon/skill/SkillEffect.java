package uz.duke.dungeon.skill;

/**
 * The kinds of thing a skill can do.
 *
 * <p>The one place skills need Java. Everything else about them — which hero has
 * them, which key casts them, how hard they hit, how long they take to come back,
 * how they grow with a level — is written in {@code dungeon.ini}, so a second hero
 * with a different four is a file change and nothing more.
 *
 * <p>These are the shapes a dungeon hero needs: hit one thing hard, hit
 * everything near you, drop something on a spot, fire something down a line, be
 * somewhere else, be briefly stronger, be briefly harder to kill. A new shape is
 * a constant here and one branch in {@link SkillBook}; a new <em>skill</em> is
 * neither, and that is the point of the split — a second hero is blocks of INI
 * and no Java at all.
 *
 * <p>Two of them do double duty rather than having been split in half, and the
 * file decides which: {@link #AREA_DAMAGE} lands once or goes on landing, and
 * {@link #DASH} carries him harmlessly or through whoever is in the way. Both are
 * the same shape at two settings, so a knight's whirlwind and an archer's sprint
 * cost no new constant here and leave every existing skill exactly as it was.
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

    /**
     * Damage every enemy within a radius of a chosen spot.
     *
     * <p>The difference from {@link #AREA_DAMAGE} is the whole of what makes it a
     * different skill to play: one is a panic button and the other is a shot you
     * have to place. It is the shape every game has and this one did not — a
     * blast the player aims, with a reach he has to respect and a radius he has to
     * judge.
     */
    AREA_AT_SPOT(Aim.OPEN_GROUND),

    /**
     * Send something flying in a direction, hitting whatever it meets.
     *
     * <p>The opposite bargain from {@link #STRIKE}, which picks a victim and whose
     * arrow then chases it and never misses. This one is aimed at a <em>place</em>
     * and forgets it at once: what it hits is whoever is standing in the way. It
     * can miss, which is the point — it is the skill that rewards the player for
     * reading where a monster is going rather than for clicking on it.
     */
    SKILLSHOT(Aim.OPEN_GROUND),

    /** Move the caster toward a chosen spot — closing or escaping. */
    DASH(Aim.OPEN_GROUND),

    /** Raise the caster's own damage for a while. */
    EMPOWER(Aim.SELF),

    /**
     * Take less damage for a while.
     *
     * <p>{@link #EMPOWER}'s mirror, and it had to be its own shape rather than a
     * negative one of it: that raises what he <em>deals</em>, through the player's
     * weapon bonus, and this lowers what he <em>takes</em>, through his body's
     * armour. Two different numbers on two different objects.
     *
     * <p>The shape a hero who has to walk into the room needs and an archer does
     * not. An archer's answer to being surrounded is to not be there; a knight's
     * is to be harder to kill while he is.
     */
    GUARD(Aim.SELF);

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
