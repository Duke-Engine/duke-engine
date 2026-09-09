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
 */
public enum SkillEffect {

    /** Damage the nearest enemy within range. */
    STRIKE,

    /** Damage every enemy within a radius of the caster. */
    AREA_DAMAGE,

    /** Move the caster forward along their facing — closing or escaping. */
    DASH,

    /** Raise the caster's own damage for a while. */
    EMPOWER
}
