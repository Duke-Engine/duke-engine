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
 * somewhere else, be briefly stronger, be briefly harder to kill -- and two that
 * no hero has and a monster does: mend one of your own, and call up more of them.
 * A new shape is
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
     *
     * <p>Name a {@code SlowFrames} and whoever it caught drags his feet as well, as
     * {@link #AREA_DAMAGE}'s do: the mage's frost nova is this, dropped where he
     * points.
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
     *
     * <p>Aimed at plain {@link Aim#GROUND} and not at {@link Aim#OPEN_GROUND},
     * which is the difference between pointing and going. A leap has to end
     * somewhere a man could stand, so a dash is refused a wall; a shot is aimed
     * ALONG a line and stops at whatever it meets, so a wall is a perfectly good
     * thing to point at -- it is where the shot will stop. Refusing it made the
     * most natural cast in the game, "fire down that corridor", a dead key.
     */
    SKILLSHOT(Aim.GROUND),

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
    GUARD(Aim.SELF),

    /**
     * Be somewhere else, at once, without crossing what is between.
     *
     * <p>It cannot land inside stone — somewhere he could not stand is not
     * somewhere he may appear — so the arrival is pulled back along the line
     * until it is floor, and if there is none the cast is refused with its
     * cooldown untouched rather than spent on standing still.
     *
     * <p><b>Where this actually differs from {@link #DASH}, since both of them
     * arrive in one frame.</b> The dash was already written to clear what is
     * between it and its landing, so "goes over the wall" is not the difference
     * and it would be pleasant nonsense to say that it is. Three things are:
     *
     * <ul>
     *   <li>It never hurts anything. A dash with a {@code Damage} tramples what
     *       it crossed; this has no such reading, so a blink cannot be tuned into
     *       a charge by a file.
     *   <li>It goes exactly where it was pointed, along the line from him to the
     *       click. A dash goes along his FACING, which is turned toward the click
     *       first and is near enough — but near enough is not the same thing, and
     *       an escape is a skill you want to land on the tile you picked.
     *   <li>It refuses rather than shrugging. A dash with nowhere to come down
     *       leaves him standing where he was and spends the cooldown anyway.
     * </ul>
     *
     * <p>The fourth difference is the client's and is the one a player will
     * actually name: a dash is drawn travelling and a blink is drawn as two
     * flashes with nothing in between.
     */
    BLINK(Aim.OPEN_GROUND),

    /**
     * Something falls on a chosen spot, a moment after it is called for.
     *
     * <p>The delay is the skill. {@link #AREA_AT_SPOT} lands the instant it is
     * cast, so the only question is where the monsters are NOW; this one asks
     * where they are going to be, and gives them the same warning it gives him —
     * the mark on the floor is a thing in the world, not a hint on his screen, and
     * a monster walking out of it is the skill being played against him.
     *
     * <p>{@code WindUpFrames} is how long the ground is marked. Nothing else about
     * it is new: the mark is an object like an arrow is an object, it counts down
     * like an arrow counts down, and the blast when it arrives is the blast every
     * other area skill uses.
     */
    METEOR(Aim.OPEN_GROUND),

    /**
     * Mend one of your own, a moment after it is called for.
     *
     * <p>A {@link #METEOR} turned round. Holy light is called down on whoever needs
     * it -- the caster's own side, below {@code HealBelowPercent} of his health, in
     * reach and in plain sight; see {@link Mending} -- and it lies on the floor under
     * him for {@code WindUpFrames} before it lands and gives back {@code Heal}. The
     * light is a thing in the world, as the meteor's mark is, so the client draws the
     * column coming down when it lands without being told anything, and the fog hides
     * it like anything else.
     *
     * <p>Aimed at a creature, and refused with its cooldown unspent if that creature
     * is nobody it may mend: a mending spent on a whole skeleton is a mending wasted.
     */
    HEAL(Aim.UNIT),

    /**
     * Call up creatures of your own round you, for a while.
     *
     * <p>{@code SummonCount} rifts open {@code Radius} away, toward where it was aimed
     * first and then turned aside in a fixed order, on open floor the caster can see --
     * never in stone, on another storey or on somebody; see {@link Summoning}. Each is a
     * thing in the world, as a meteor's mark is, and a {@code Summons} climbs out of it
     * {@code WindUpFrames} later. What climbs out lasts {@code DurationFrames} and then
     * falls down, is worth {@code SummonExperiencePercent} of its own kind, and hits as
     * hard as the depth made its caller hit.
     *
     * <p>No more than {@code MaxSummoned} of one caster's stand at once, rifts counted.
     * A cast that would open none is refused with its cooldown unspent.
     */
    SUMMON(Aim.SELF);

    /** What a player has to click before the cast can go through. */
    public enum Aim {
        /** Nothing: it goes off where he stands, the moment the key is pressed. */
        SELF,
        /** A creature. */
        UNIT,
        /**
         * Any spot at all, stone and unlit dark included.
         *
         * <p>For a skill that is POINTED rather than placed. What the player is
         * choosing is a direction, and every direction is a fair one: a shot
         * aimed into rock is a shot that stops at the rock, which is a thing he
         * may perfectly well want. The stricter {@link #OPEN_GROUND} below is for
         * the skills that put him somewhere, where the same click would be a
         * request to stand inside a wall.
         */
        GROUND,
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
