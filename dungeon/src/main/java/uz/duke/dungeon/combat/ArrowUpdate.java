package uz.duke.dungeon.combat;

import uz.duke.core.GameConstants;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.DamageType;
import uz.duke.core.module.ModuleData;
import uz.duke.core.module.UpdateModule;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ObjectId;
import uz.duke.core.thing.World;
import uz.duke.dungeon.power.PowerBook;
import uz.duke.rts.module.ExperienceModule;

/**
 * An arrow in the air: it chases what it was loosed at, and hurts it on arrival.
 *
 * <p>Chases rather than flies at a point, which is a decision and not an
 * oversight. A shot that led its target would miss whenever the target turned,
 * and a dungeon full of arrows thudding into walls behind fleeing monsters is a
 * different game — one where the hero's range is a suggestion. It follows, so the
 * shot always arrives; what the flight costs is time.
 *
 * <p>It carries the damage rather than working it out on landing. The figure was
 * final when the weapon let go of it, and the archer may have levelled — or died —
 * in the third of a second since.
 *
 * <p>No body and no geometry, on purpose. Nothing can shoot at it, because
 * weapons only acquire things with a body; and it passes through the world rather
 * than shouldering monsters aside on its way past.
 */
public final class ArrowUpdate extends UpdateModule {

    /**
     * How long an arrow may stay in the air before it is given up on.
     *
     * <p>Only a backstop. Every ordinary end — arrival, or a target that dies
     * first — happens long before this; what it catches is the case nobody
     * thought of, so that a stray arrow cannot circle the dungeon forever.
     */
    private static final int LONGEST_FLIGHT = 5 * GameConstants.LOGICFRAMES_PER_SECOND;

    private ObjectId target;
    private ObjectId shooter;
    private float damage;
    private DamageType damageType = DamageType.NORMAL;
    private float stepPerFrame;
    private int flownFor;

    /**
     * The run's powers, so that an arrow can pay its archer back.
     *
     * <p>Held by the arrow rather than looked up on landing because the arrow is
     * the only thing that knows how much it dealt, and the archer may be somebody
     * else's by then — a monster's shot must not heal the hero.
     */
    private final PowerBook powers;

    public ArrowUpdate(GameObject owner, ModuleData ignored, PowerBook powers) {
        super(owner);
        this.powers = powers;
    }

    /** The empty block that puts this on the arrow; a shot fills the rest in. */
    public static ModuleData parseData(uz.duke.core.ini.Ini ini) {
        ini.initFromIni(new Object(), NO_FIELDS);
        return null;
    }

    private static final uz.duke.core.ini.FieldParseTable<Object> NO_FIELDS =
            new uz.duke.core.ini.FieldParseTable<>();

    /** Send it after something, carrying what the weapon decided it was worth. */
    void loose(GameObject from, GameObject at, float carrying, DamageType type, float speed) {
        this.shooter = from.getId();
        this.target = at.getId();
        this.damage = carrying;
        this.damageType = type;
        this.stepPerFrame = speed * GameConstants.SECONDS_PER_LOGICFRAME;
        aimAt(at.getPosition());
    }

    @Override
    public void update() {
        var owner = getOwner();
        var world = owner.getWorld();
        if (world == null || target == null || ++flownFor > LONGEST_FLIGHT) {
            owner.markDestroyed();
            return;
        }
        var victim = world.findObject(target);
        if (victim == null || victim.isEffectivelyDead() || victim.getBody() == null) {
            owner.markDestroyed(); // it died on the way; the arrow has nothing to reach
            return;
        }

        // Measured wall to wall, the way a weapon measures range — so an arrow
        // arrives at a boss's flank rather than pressing on toward its middle.
        if (World.reachBetween(owner, victim) <= stepPerFrame) {
            strike(world, victim);
            return;
        }
        aimAt(victim.getPosition());
        var here = owner.getPosition();
        var toward = victim.getPosition();
        float dx = toward.x() - here.x();
        float dy = toward.y() - here.y();
        float distance = (float) StrictMath.sqrt(dx * dx + dy * dy);
        if (distance <= 0.0001f) {
            strike(world, victim);
            return;
        }
        owner.setPosition(new Coord3D(here.x() + dx / distance * stepPerFrame,
                here.y() + dy / distance * stepPerFrame, here.z()));
    }

    /** Point along the flight, so it is drawn as an arrow rather than a splinter. */
    private void aimAt(Coord3D toward) {
        var here = getOwner().getPosition();
        getOwner().setOrientation((float) StrictMath.atan2(
                toward.y() - here.y(), toward.x() - here.x()));
    }

    /**
     * Land, and take responsibility for the kill.
     *
     * <p>The weapon credited nobody when it let this go — its victim was alive at
     * the time — so if the arrow finishes something off, the archer's experience
     * is the arrow's to award.
     */
    private void strike(World world, GameObject victim) {
        victim.getBody().damage(damage, damageType);
        drinkFor(world.findObject(shooter));
        if (victim.isEffectivelyDead()) {
            var archer = world.findObject(shooter);
            var earned = victim.findModule(ExperienceModule.class);
            var his = archer == null ? null : archer.findModule(ExperienceModule.class);
            if (his != null && earned != null) {
                his.addExperience(earned.getExperienceValue());
            }
        }
        getOwner().markDestroyed();
    }

    /**
     * A share of what this arrow carried, back to whoever loosed it.
     *
     * <p>Only for a shooter who has skills — which in this dungeon means the hero,
     * and says so without the arrow having to be told who he is. Powers belong to
     * the run, and the run has one hero.
     */
    private void drinkFor(GameObject archer) {
        float share = powers == null ? 0f : powers.lifestealFraction();
        if (share <= 0f || archer == null || archer.getBody() == null
                || archer.findModule(uz.duke.dungeon.skill.SkillBook.class) == null) {
            return;
        }
        archer.getBody().heal(damage * share);
    }
}
