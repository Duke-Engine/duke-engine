package uz.dukeengine.rts.module;

import java.util.EnumSet;
import java.util.Set;
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
public final class ToppleUpdate extends UpdateModule implements KeepsDead, uz.dukeengine.core.save.Saveable {

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
     * @param killWhenStartToppling dead the moment it is pushed, and never falls — the reference's {@code
     *                      KillWhenStartToppling}
     * @param leftOrRightOnly falling only to one side of its facing or the other, whichever is nearer the push, and
     *                      keeping its facing — a fence, the reference's {@code ToppleLeftOrRightOnly}
     * @param toppleEffect  what the world plays riding it as it starts to fall, anything it plays by name, or null —
     *                      the reference's {@code ToppleFX}
     * @param bounceEffect  the same as it bounces hard enough to be heard, {@code BounceFX}
     */
    public record Data(int crushedAbove, float reach, float toppleSpeed, float velocityShare, float accelerationShare,
            float bounceShare, float sinkDepth, int sinkFrames, boolean killWhenStartToppling,
            boolean leftOrRightOnly, String toppleEffect, String bounceEffect) implements ModuleData {

        static final Data DEFAULTS = new Data(1, 7f, 0.2f, 0.2f, 0.01f, 0.3f, 10f, 150);

        public Data {
            reach = Math.clamp(reach, 0f, MOST_REACH);
            sinkFrames = Math.max(1, sinkFrames);
        }

        /** Playing nothing as it falls or bounces. */
        public Data(int crushedAbove, float reach, float toppleSpeed, float velocityShare, float accelerationShare,
                float bounceShare, float sinkDepth, int sinkFrames, boolean killWhenStartToppling,
                boolean leftOrRightOnly) {
            this(crushedAbove, reach, toppleSpeed, velocityShare, accelerationShare, bounceShare, sinkDepth,
                    sinkFrames, killWhenStartToppling, leftOrRightOnly, null, null);
        }

        /** Falling whichever way it is pushed, and dying once it lies still. */
        public Data(int crushedAbove, float reach, float toppleSpeed, float velocityShare, float accelerationShare,
                float bounceShare, float sinkDepth, int sinkFrames) {
            this(crushedAbove, reach, toppleSpeed, velocityShare, accelerationShare, bounceShare, sinkDepth,
                    sinkFrames, false, false);
        }
    }

    /** How a push is to fall, as the reference's {@code ToppleOptions}. */
    public enum Option {
        /** Lies still the first time it reaches the ground — {@code TOPPLE_OPTIONS_NO_BOUNCE}. */
        NO_BOUNCE,
        /** Plays nothing as it bounces — {@code TOPPLE_OPTIONS_NO_FX}; its fall's start plays as ever, as there. */
        NO_FX
    }

    /** {@code VELOCITY_BOUNCE_SOUND_LIMIT}: a bounce slower than this plays nothing. */
    private static final float HEARD = 0.03f;

    private enum State {
        UPRIGHT,
        FALLING,
        SINKING
    }

    private final Data data;
    private State state = State.UPRIGHT;
    private boolean noBounce;
    private boolean noEffects;
    private float side;
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

    /** Pushed over by {@code by}, standing at {@code from}, as hard as its template says, as the other form. */
    public void topple(GameObject by, Coord3D from) {
        topple(by, from, data.toppleSpeed(), EnumSet.noneOf(Option.class));
    }

    /**
     * Pushed over by {@code by}, standing at {@code from}, {@code speed} radians a frame hard — the reference's {@code
     * applyTopplingForce}: turned to face away from it — as the reference turns a toppling thing square to its fall —
     * and falling that way from the next frame; one that falls {@link Data#leftOrRightOnly} keeps its facing and falls
     * to whichever side of it is nearer away, and one {@link Data#killWhenStartToppling} dies now instead. A thing
     * dead, or already pushed, is not pushed again.
     */
    public void topple(GameObject by, Coord3D from, float speed, Set<Option> options) {
        var owner = getOwner();
        if (state != State.UPRIGHT || owner.isEffectivelyDead()) {
            return;
        }
        pushedBy = by == null ? null : by.getId();
        pushedByPlayer = by == null ? -1 : by.getPlayerIndex();
        if (data.killWhenStartToppling()) {
            // Object::kill, as the reference's: the plain death, not TOPPLED
            if (owner.getBody() != null) {
                owner.getBody().damage(HUGE_DAMAGE, TOPPLE, new Death(DeathType.NORMAL, pushedBy, pushedByPlayer));
            }
            return;
        }
        var here = owner.getPosition();
        float dx = here.x() - from.x();
        float dy = here.y() - from.y();
        if (data.leftOrRightOnly()) {
            // angleClosestTo(facing + PI/2, facing - PI/2, push): to the left only where the push is nearer it
            side = dx * -owner.getFacingSin() + dy * owner.getFacingCos() > 0f ? -1f : 1f;
        } else {
            owner.setOrientation((float) StrictMath.atan2(dy, dx));
        }
        noBounce = options.contains(Option.NO_BOUNCE);
        noEffects = options.contains(Option.NO_FX);
        velocity = speed * data.velocityShare();
        acceleration = speed * data.accelerationShare();
        state = State.FALLING;
        play(data.toppleEffect());
    }

    private void play(String effect) {
        var world = getOwner().getWorld();
        if (effect != null && !effect.isBlank() && world != null) {
            world.effect(effect, getOwner());
        }
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
        if (side == 0f) {
            getOwner().setPitch(-fallen);
        } else {
            getOwner().setRoll(side * fallen);
        }
        if (fallen >= LIMIT && velocity > 0f) {
            velocity *= -data.bounceShare();
            if (noBounce || Math.abs(velocity) < STILL) {
                velocity = 0f;
                liesStill();
            } else if (Math.abs(velocity) >= HEARD && !noEffects) {
                play(data.bounceEffect());
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

    @Override
    public void save(uz.dukeengine.core.save.SaveOut out) {
        out.writeEnum(state).writeBoolean(noBounce).writeBoolean(noEffects).writeFloat(side).writeFloat(velocity)
                .writeFloat(acceleration).writeFloat(fallen).writeInt(sunk).writeFloat(groundZ).writeId(pushedBy)
                .writeInt(pushedByPlayer);
    }

    /** Its fall read back, and drawn again as far over as it had gone — the lean is drawing, so it is not saved. */
    @Override
    public void load(uz.dukeengine.core.save.SaveIn in) {
        state = in.readEnum(State.class);
        noBounce = in.readBoolean();
        noEffects = in.readBoolean();
        side = in.readFloat();
        velocity = in.readFloat();
        acceleration = in.readFloat();
        fallen = in.readFloat();
        sunk = in.readInt();
        groundZ = in.readFloat();
        pushedBy = in.readId();
        pushedByPlayer = in.readInt();
        if (state != State.UPRIGHT) {
            if (side == 0f) {
                getOwner().setPitch(-fallen);
            } else {
                getOwner().setRoll(side * fallen);
            }
        }
        if (state == State.SINKING) {
            getOwner().setKeepsOwnHeight(true);
        }
    }

    /** Whether it has begun to fall. */
    public boolean toppled() {
        return state != State.UPRIGHT;
    }
}
