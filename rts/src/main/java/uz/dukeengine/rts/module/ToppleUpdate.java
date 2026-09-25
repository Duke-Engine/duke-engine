package uz.dukeengine.rts.module;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.Death;
import uz.dukeengine.core.module.DeathType;
import uz.dukeengine.core.module.KeepsDead;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;

/**
 * A thing that topples away from what drives into it, then sinks into the ground and is gone — SAGE's {@code
 * ToppleUpdate}, and its trees ({@code W3DTreeBuffer::unitMoved}, {@code applyTopplingForce}, {@code
 * updateTopplingTree}): a mover of a crushing strength above this one's, coming within its own radius and this one's
 * reach, pushes it over away from itself; it falls ever faster to nearly flat, bounces a little until it lies still,
 * and — having a body — dies {@link #TOPPLED}, which the game is told as any death is; then it sinks its depth over its
 * frames, kept in the world dead, and is taken away.
 *
 * <p>Which things topple, how hard they are to push over, how fast they fall and how far and long they sink are the
 * game's; the reference's trees sink 10 over five seconds.
 */
@ModuleGroup(ModuleGroups.MOVEMENT)
public final class ToppleUpdate extends UpdateModule implements KeepsDead {

    /** The death a thing pushed over dies, as the reference's {@code DEATH_TOPPLED}. */
    public static final DeathType TOPPLED = DeathType.of("TOPPLED");

    /** What being pushed over deals — enough to kill — which an armour may name like any other. */
    public static final DamageType TOPPLE = DamageType.of("TOPPLE");

    /** The furthest reach a mover is looked for at: a reach past it is held to it. */
    static final float MOST_REACH = 50f;

    /** The reference's {@code ANGULAR_LIMIT}: just short of flat, for the slop of a model's base. */
    private static final float LIMIT = (float) (StrictMath.PI / 2 - StrictMath.PI / 64);

    /** {@code VELOCITY_BOUNCE_LIMIT}: a bounce slower than this, and it lies still. */
    private static final float STILL = 0.01f;

    /** SAGE's {@code HUGE_DAMAGE_AMOUNT}. */
    private static final float HUGE_DAMAGE = 999_999f;

    /**
     * @param crushedAbove  the crushing strength a mover must be above to push it over — the reference's trees, 1
     * @param reach         how far past a mover's own radius it is pushed from — the reference's {@code
     *                      TREE_RADIUS_APPROX}, 7
     * @param toppleSpeed   how hard a push is, in radians a frame: it starts falling at {@code velocityShare} of it
     *                      and gathers {@code accelerationShare} of it a frame — the reference's 20% and 1%
     * @param bounceShare   the share of its speed it bounces back with as it lands — 30%
     * @param sinkDepth     how far it sinks into the ground once it lies still
     * @param sinkFrames    over how many frames, then gone
     */
    public record Data(int crushedAbove, float reach, float toppleSpeed, float velocityShare, float accelerationShare,
            float bounceShare, float sinkDepth, int sinkFrames) implements ModuleData {

        static final Data DEFAULTS = new Data(1, 7f, 0.2f, 0.2f, 0.01f, 0.3f, 10f, 150);

        public Data {
            reach = Math.clamp(reach, 0f, MOST_REACH);
            sinkFrames = Math.max(1, sinkFrames);
        }
    }

    private enum State {
        UPRIGHT,
        FALLING,
        SINKING
    }

    private final Data data;
    private State state = State.UPRIGHT;
    private float velocity;
    private float acceleration;
    private float fallen;
    private int sunk;
    private float groundZ;
    private ObjectId pushedBy;
    private int pushedByPlayer = -1;

    public ToppleUpdate(GameObject owner, Data data) {
        super(owner);
        this.data = data;
    }

    /** Whether a mover of this crushing strength pushes it over, standing as it is. */
    public boolean topplesUnder(int crusherLevel) {
        return state == State.UPRIGHT && crusherLevel > data.crushedAbove() && !getOwner().isEffectivelyDead();
    }

    /** How far past a mover's own radius it is pushed from. */
    public float reach() {
        return data.reach();
    }

    /**
     * Pushed over by {@code by}, standing at {@code from}: turned to face away from it — as the reference turns a
     * toppling thing square to its fall — and falling that way from the next frame.
     */
    public void topple(GameObject by, Coord3D from) {
        if (state != State.UPRIGHT) {
            return;
        }
        var owner = getOwner();
        var here = owner.getPosition();
        float away = (float) StrictMath.atan2(here.y() - from.y(), here.x() - from.x());
        owner.setOrientation(away);
        velocity = data.toppleSpeed() * data.velocityShare();
        acceleration = data.toppleSpeed() * data.accelerationShare();
        pushedBy = by == null ? null : by.getId();
        pushedByPlayer = by == null ? -1 : by.getPlayerIndex();
        state = State.FALLING;
    }

    @Override
    public void update() {
        switch (state) {
            case UPRIGHT -> {
                // standing: a mover's CrushUpdate pushes it over
            }
            case FALLING -> fall();
            case SINKING -> sink();
        }
    }

    /** {@code updateTopplingTree}: one frame of the fall, and the bounce where it lands. */
    private void fall() {
        float step = Math.min(velocity, LIMIT - fallen);
        fallen += step;
        getOwner().setPitch(-fallen);
        if (fallen >= LIMIT && velocity > 0f) {
            velocity *= -data.bounceShare();
            if (Math.abs(velocity) < STILL) {
                liesStill();
            }
        } else {
            velocity += acceleration;
        }
    }

    /** Down: dead of it, where it has a body, and kept in the world while it sinks. */
    private void liesStill() {
        var owner = getOwner();
        state = State.SINKING;
        groundZ = owner.getPosition().z();
        owner.setKeepsOwnHeight(true);
        if (owner.getBody() != null) {
            owner.getBody().damage(HUGE_DAMAGE, TOPPLE, new Death(TOPPLED, pushedBy, pushedByPlayer));
        }
    }

    private void sink() {
        var owner = getOwner();
        sunk++;
        var here = owner.getPosition();
        owner.setPosition(new Coord3D(here.x(), here.y(), groundZ - data.sinkDepth() * sunk / data.sinkFrames()));
        if (sunk >= data.sinkFrames()) {
            owner.markDestroyed();
        }
    }

    @Override
    public boolean keepsDead() {
        return state == State.SINKING;
    }

    /** Whether it has begun to fall. */
    public boolean toppled() {
        return state != State.UPRIGHT;
    }
}
