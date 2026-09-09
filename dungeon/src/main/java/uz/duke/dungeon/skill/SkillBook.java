package uz.duke.dungeon.skill;

import java.util.List;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.UpdateModule;
import uz.duke.core.player.Relationship;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.World;
import uz.duke.rts.module.DamageModifier;

/**
 * The skills a hero has, what they are doing right now, and how long until he can
 * do them again.
 *
 * <p>Written here rather than built on the engine's {@code SpecialPowerModule},
 * which is a superweapon: one shape (area damage at a map point), values frozen
 * when the unit is built, no notion of a level unlocking anything, and four of
 * them on one unit are not tellable apart. Every one of those is something a
 * dungeon skill needs, so this follows {@code GrowableBody} and {@code HeroProgress}
 * — the game writes its own rule and the engine stays out of it.
 *
 * <p>What it does borrow is {@link DamageModifier}, and this module implements it
 * directly rather than attaching a buff module while {@code EMPOWER} lasts.
 * Attaching and detaching a module mid-frame is exactly what the engine's module
 * list refuses (rightly — it would silently skip whatever came next), and a flag
 * that expires is the same thing without the trap.
 *
 * <p>Determinism: cooldowns are frames counted down, never seconds; the victim of
 * a {@code STRIKE} is the nearest enemy with ties broken by object id; a
 * {@code DASH} walks with {@link StrictMath}. Nothing here asks the clock.
 */
public final class SkillBook extends UpdateModule implements DamageModifier {

    /** How far apart a dash checks the ground it is crossing. */
    private static final float DASH_STEP = 5f;

    private final List<Skill> skills;
    private final int[] cooldowns;

    /** Frames of {@code EMPOWER} left, and what it is worth while it lasts. */
    private int boostFrames;
    private int boostPercent;

    public SkillBook(GameObject owner, List<Skill> skills) {
        super(owner);
        this.skills = List.copyOf(skills);
        this.cooldowns = new int[skills.size()];
    }

    private static final uz.duke.core.ini.FieldParseTable<Object> NO_FIELDS =
            new uz.duke.core.ini.FieldParseTable<>();

    /**
     * The creature block carries no skill data — only the fact that this creature
     * has skills, which ones being {@code dungeon.ini}'s business. The block still
     * has to be read to its {@code End}, or the rest of the creature is parsed as
     * though it were inside one.
     */
    public static uz.duke.core.module.ModuleData parseData(uz.duke.core.ini.Ini ini) {
        ini.initFromIni(new Object(), NO_FIELDS);
        return null;
    }

    public List<Skill> getSkills() {
        return skills;
    }

    /** Frames until the skill on {@code key} is ready; 0 when it is. */
    public int cooldownOf(char key) {
        int slot = slotOf(key);
        return slot < 0 ? 0 : cooldowns[slot];
    }

    public boolean isReady(char key) {
        int slot = slotOf(key);
        return slot >= 0 && cooldowns[slot] <= 0;
    }

    /** Frames of extra damage left, for anything that wants to draw it. */
    public int getBoostFrames() {
        return boostFrames;
    }

    private int slotOf(char key) {
        for (int slot = 0; slot < skills.size(); slot++) {
            if (skills.get(slot).key() == key) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Cast the skill on {@code key} at a hero of {@code level}. Returns false and
     * does nothing if there is no such skill, it is still recharging, or the level
     * has not unlocked it — an ultimate refuses rather than fires weakly.
     */
    public boolean cast(char key, int level) {
        int slot = slotOf(key);
        if (slot < 0 || cooldowns[slot] > 0) {
            return false;
        }
        var skill = skills.get(slot);
        if (!skill.unlockedAt(level)) {
            return false;
        }
        var owner = getOwner();
        var world = owner.getWorld();
        if (world == null || owner.isEffectivelyDead()) {
            return false;
        }
        apply(skill, level, owner, world);
        cooldowns[slot] = skill.cooldownAt(level);
        return true;
    }

    private void apply(Skill skill, int level, GameObject owner, World world) {
        switch (skill.effect()) {
            case STRIKE -> {
                var victim = nearestEnemy(owner, world, skill.range());
                if (victim != null) {
                    victim.getBody().damage(skill.damageAt(level));
                }
            }
            case AREA_DAMAGE -> {
                for (var victim : enemiesWithin(owner, world, skill.radius())) {
                    victim.getBody().damage(skill.damageAt(level));
                }
            }
            case DASH -> owner.setPosition(dashEnd(owner, world, skill.distance()));
            case EMPOWER -> {
                // Re-casting refreshes rather than stacking: two overlapping copies
                // of the same buff is a question with no obvious answer, and the
                // cooldown already decides how often it can be had.
                boostFrames = skill.durationFrames();
                boostPercent = skill.boostAt(level);
            }
        }
    }

    /**
     * The nearest living enemy within {@code range}, measured surface to surface
     * as weapons measure it. Ties go to the lower object id — not for fairness but
     * for reproducibility: two equidistant skeletons must not be chosen by
     * whichever the world happens to list first.
     */
    private static GameObject nearestEnemy(GameObject owner, World world, float range) {
        GameObject best = null;
        float bestReach = Float.MAX_VALUE;
        for (var candidate : enemiesWithin(owner, world, range)) {
            float reach = World.reachBetween(owner, candidate);
            if (reach < bestReach
                    || (reach == bestReach && candidate.getId().value() < best.getId().value())) {
                best = candidate;
                bestReach = reach;
            }
        }
        return best;
    }

    private static List<GameObject> enemiesWithin(GameObject owner, World world, float radius) {
        int player = owner.getPlayerIndex();
        return world.objectsInRange(owner.getPosition(), radius, candidate ->
                candidate.getBody() != null
                        && !candidate.isEffectivelyDead()
                        && world.getRelationship(player, candidate.getPlayerIndex())
                                == Relationship.ENEMIES);
    }

    /**
     * How far forward the caster actually gets.
     *
     * <p>Walked in steps rather than jumped, and the last clear step is where he
     * lands. A dash that simply added the distance would put him inside a wall
     * whenever the room was too small for it — and a unit whose centre is in stone
     * is the bug that took a day to find the last time.
     */
    private static Coord3D dashEnd(GameObject owner, World world, float distance) {
        float facing = owner.getOrientation();
        float dx = (float) StrictMath.cos(facing);
        float dy = (float) StrictMath.sin(facing);
        var from = owner.getPosition();
        var landed = from;
        for (float gone = DASH_STEP; gone <= distance; gone += DASH_STEP) {
            var step = new Coord3D(from.x() + dx * gone, from.y() + dy * gone, from.z());
            if (world.isGroundBlocked(step) || world.findBlocker(owner, step) != null) {
                break; // as far as he gets; the rest of the dash is wall
            }
            landed = step;
        }
        return landed;
    }

    @Override
    public float damageMultiplier() {
        return boostFrames > 0 ? 1f + boostPercent / 100f : 1f;
    }

    @Override
    public void update() {
        for (int slot = 0; slot < cooldowns.length; slot++) {
            if (cooldowns[slot] > 0) {
                cooldowns[slot]--;
            }
        }
        if (boostFrames > 0) {
            boostFrames--;
        }
    }
}
