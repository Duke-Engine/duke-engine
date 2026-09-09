package uz.duke.dungeon.skill;

/**
 * One skill as the data file describes it, and what it comes to at a given level.
 *
 * <p>The rules and nothing else — no game object, no simulation — for the same
 * reason {@link uz.duke.dungeon.level.Levelling} is: what a skill does at level 7
 * is a question worth being able to ask without starting a dungeon.
 *
 * <p>A skill belongs to a hero template and a key, not to the game. There is one
 * hero today, but a roster is a list in a file rather than a rewrite: a second
 * hero with a different four is four more blocks of INI and no Java. Two heroes
 * may use the same {@link SkillEffect} with quite different numbers, which is the
 * point of separating the effect from the skill.
 *
 * <p>Growth is computed from the level in one step rather than accumulated level
 * by level. Repeated addition drifts, and a hero who reached level 7 by two routes
 * has to have the same skills.
 *
 * @param heroTemplate  the creature this skill belongs to
 * @param key           the key that casts it — Q, W, E or R by convention
 * @param effect        what it does
 * @param damage        damage at the first level ({@code STRIKE}, {@code AREA_DAMAGE})
 * @param damagePerLevel  damage added per level past the first
 * @param radius        how far {@code AREA_DAMAGE} reaches around the caster
 * @param range         how far {@code STRIKE} can find a victim
 * @param distance      how far {@code DASH} carries the caster
 * @param boostPercent  how much more damage {@code EMPOWER} grants, in percent
 * @param boostPerLevel that percentage's growth per level
 * @param durationFrames how long {@code EMPOWER} lasts
 * @param cooldownFrames how long before it can be cast again, at the first level
 * @param cooldownPerLevel  frames added per level — negative to sharpen with level
 * @param unlockLevel   the level it becomes usable at; an ultimate waits
 * @param windUpFrames  how long the caster spends preparing before it goes off —
 *     zero for a skill that is instant. A drawn shot has to be seen being drawn,
 *     or the monster simply loses health for no reason anyone can point at.
 * @param projectile    the creature a {@code STRIKE} becomes on its way, or empty
 *     to land where it stands. An arrow that crosses the room is the difference
 *     between a shot and an accusation.
 */
public record Skill(
        String heroTemplate,
        char key,
        SkillEffect effect,
        float damage,
        float damagePerLevel,
        float radius,
        float range,
        float distance,
        int boostPercent,
        int boostPerLevel,
        int durationFrames,
        int cooldownFrames,
        int cooldownPerLevel,
        int unlockLevel,
        int windUpFrames,
        String projectile) {

    /**
     * A cooldown can shorten with level but never vanish: a skill castable every
     * frame is not a skill, and a file is free to sharpen one too far by accident.
     */
    public static final int MIN_COOLDOWN_FRAMES = 1;

    /** Levels earned past the first — what every growth figure is multiplied by. */
    private int grown(int level) {
        return Math.max(0, level - 1);
    }

    public float damageAt(int level) {
        return damage + grown(level) * damagePerLevel;
    }

    public int boostAt(int level) {
        return boostPercent + grown(level) * boostPerLevel;
    }

    public int cooldownAt(int level) {
        return Math.max(MIN_COOLDOWN_FRAMES, cooldownFrames + grown(level) * cooldownPerLevel);
    }

    /** Whether a hero of this level may cast it at all. */
    public boolean unlockedAt(int level) {
        return level >= unlockLevel;
    }

    /** Whether it becomes something that has to cross the room to arrive. */
    public boolean hasProjectile() {
        return projectile != null && !projectile.isBlank();
    }
}
