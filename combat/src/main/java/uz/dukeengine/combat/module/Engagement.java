package uz.dukeengine.combat.module;

import uz.dukeengine.combat.message.OrderSource;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.ObjectId;

/**
 * What a weapon is set on: the thing it fires at, the place of ground it was ordered to fire on or the point it swings
 * toward, whose order that was, and whether it will look for a target at all. Plain state of the unit, changed on the
 * simulation thread as every order is; what is done about it is {@link WeaponUpdate}'s and {@link Firing}'s.
 */
final class Engagement {

    private ObjectId target;
    /** Whether its target was given it by a forced order: kept on though the target passes itself off. */
    private boolean forced;
    /** The place of the ground it was ordered to fire on, or null. */
    private Coord3D groundTarget;
    /** Whose order its target was, or null for one it found for itself — which slots it may use. */
    private OrderSource source;
    /** The attack toward a point it is on, or null. */
    private PointAttack pointAttack;
    /** The frame the wind-up of a blow toward a point ends on, or -1 for none under way. */
    private int pointWindEnds = -1;
    /** Whether its target is in range at any distance for the rest of this attack — see {@link Weapon#leechRange}. */
    private boolean leeching;
    /** Whether it never picks a target by itself — see {@link WeaponUpdate#acquiresNothing(boolean)}. */
    private boolean acquiresNothing;
    /** Shots it may yet fire at its ordered target before the attack is over; 0 for no limit. */
    private int shotsLeft;

    ObjectId target() {
        return target;
    }

    boolean forced() {
        return forced;
    }

    OrderSource source() {
        return source;
    }

    Coord3D groundTarget() {
        return groundTarget;
    }

    PointAttack pointAttack() {
        return pointAttack;
    }

    int pointWindEnds() {
        return pointWindEnds;
    }

    boolean leeching() {
        return leeching;
    }

    boolean acquiresNothing() {
        return acquiresNothing;
    }

    void acquiresNothing(boolean nothing) {
        this.acquiresNothing = nothing;
    }

    /** Firing on something, it is at work where it stands. */
    boolean busy() {
        return target != null || groundTarget != null;
    }

    /** An order to engage {@code target} taken, firing at most {@code maxShots} at it — 0 for no limit. */
    void order(ObjectId target, boolean forced, OrderSource by, int maxShots) {
        if (target == null || !target.equals(this.target)) {
            leeching = false; // a new attack: a reach kept for the last is not kept for this one
        }
        this.target = target;
        this.source = by;
        this.forced = forced;
        this.shotsLeft = Math.max(0, maxShots);
    }

    /** A target it found for itself. */
    void found(ObjectId target) {
        this.target = target;
        this.source = null;
        this.forced = false;
        this.shotsLeft = 0;
    }

    /** A shot fired at {@code at}, counted against the most it was ordered to fire at it: whether that was the last. */
    boolean lastShotAt(ObjectId at) {
        return shotsLeft > 0 && at.equals(target) && --shotsLeft == 0;
    }

    void fireOnGround(Coord3D place) {
        groundTarget = place;
    }

    void giveUpGround() {
        groundTarget = null;
    }

    /** Attack toward a point as {@code attack} says: whatever it was firing on let go, and a wind-up kept. */
    void swingToward(PointAttack attack) {
        groundTarget = null;
        target = null;
        forced = false;
        if (pointAttack == null) {
            pointWindEnds = -1;
        }
        pointAttack = attack.point(pointWindEnds >= 0) == null ? null : attack;
    }

    void giveUpPoint() {
        pointAttack = null;
    }

    void windPointUntil(int frame) {
        pointWindEnds = frame;
    }

    /** A reach kept for the rest of this attack, if {@code keeps}. */
    void keepReach(boolean keeps) {
        leeching |= keeps;
    }

    /** A blow toward the point went off: its target let go, and the attack on if it says so. */
    void swung(PointAttack attack) {
        target = null;
        pointAttack = attack.attacked() ? attack : null;
    }

    /** All of it but the attack toward a point, which is the game's code: its {@link WeaponUpdate} refuses that. */
    void save(uz.dukeengine.core.save.SaveOut out) {
        out.writeId(target).writeBoolean(forced).writePlace(groundTarget).writeEnum(source).writeInt(pointWindEnds)
                .writeBoolean(leeching).writeBoolean(acquiresNothing).writeInt(shotsLeft);
    }

    void load(uz.dukeengine.core.save.SaveIn in) {
        target = in.readId();
        forced = in.readBoolean();
        groundTarget = in.readPlace();
        source = in.readEnum(OrderSource.class);
        pointAttack = null;
        pointWindEnds = in.readInt();
        leeching = in.readBoolean();
        acquiresNothing = in.readBoolean();
        shotsLeft = in.readInt();
    }

    /** Let everything go: the attack is over. */
    void release() {
        pointAttack = null;
        pointWindEnds = -1;
        groundTarget = null;
        target = null;
        forced = false;
        leeching = false;
        shotsLeft = 0;
    }

    /** The same, but an attack toward a point: a walk goes on swinging. */
    void releaseKeepingPoint() {
        var keep = pointAttack;
        int winds = pointWindEnds;
        release();
        pointAttack = keep;
        pointWindEnds = winds;
    }
}
