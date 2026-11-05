package uz.dukeengine.rpg.skill;

import uz.dukeengine.core.save.SaveIn;
import uz.dukeengine.core.save.SaveOut;
import uz.dukeengine.core.GameConstants;

/**
 * What a caster has to cast out of, and what it can hold.
 *
 * <p>Whole points, never a fraction. That is not tidiness: mana is spent and
 * regained inside the simulation, and a simulation that adds floats together
 * thirty times a second is one where two machines drift apart — slowly, and
 * then all at once, in the frame where one of them can afford a meteor and
 * the other cannot.
 *
 * <p>Which is also why the trickle is counted the way it is. The rate is held
 * in <b>tenths of a point a second</b> and {@link #carry} holds what has
 * been earned towards the next whole point: each frame adds the rate to the
 * carry, and every time the carry reaches a second's worth of tenths one point
 * falls out. A hero on 70 tenths gets exactly 7 a second, on every machine,
 * forever -- where {@code mana += 7f / 30f} does not.
 *
 * <p>Tenths rather than whole points because of what a level is worth. Whole
 * points a second is too coarse a step to grow with: the smallest raise there
 * is would take a knight from three a second to four, which is a third again,
 * and by the tenth level he would be regenerating faster than the mage.
 */
final class ManaPool {

    /** A point is this many tenths; a rate is held in them. */
    static final int TENTHS = 10;

    /** A second's worth of a rate of one tenth, frame by frame: what a carry pays a whole point at. */
    static final int A_SECOND = TENTHS * GameConstants.LOGICFRAMES_PER_SECOND;

    private int mana;
    private int max;
    private int tenthsPerSecond;
    private int carry;

    /**
     * Whether this creature pays for what it casts.
     *
     * <p>Off unless it is given a pool: a hero by what he is made of, and a monster
     * whose kind names one where it is placed -- the three casting mages, see
     * the game's step that sets a creature's level. Every other monster casts free.
     */
    private boolean inUse;

    /**
     * The frame a cast was last refused for want of mana, or 0 for never.
     *
     * <p>A moment rather than a state, and stamped with its frame for the same
     * reason a cast is: the status line is rebuilt and sent every frame whether
     * anything happened or not, so without the stamp the client would sound the
     * refusal thirty times a second for as long as nothing else went on.
     *
     * <p>It is worth telling him at all because the alternative is silence. A
     * skill that is merely reloading says so — the socket is swept and counting —
     * but one he cannot pay for looks exactly like a key that did not register.
     */
    private int refusedFrame;

    /** See {@link SkillBook#poolOf}. */
    void poolOf(int max, int tenthsPerSecond) {
        int was = this.max;
        resize(max, tenthsPerSecond);
        if (was > 0 && this.max > was) {
            mana = Math.min(this.max, mana + (this.max - was));
        }
    }

    /** See {@link SkillBook#resize}. */
    void resize(int max, int tenthsPerSecond) {
        this.max = Math.max(0, max);
        this.tenthsPerSecond = Math.max(0, tenthsPerSecond);
        this.inUse = this.max > 0;
        mana = Math.min(mana, this.max);
    }

    void fill() {
        mana = max;
        carry = 0;
    }

    void restore(int points) {
        if (points > 0) {
            mana = Math.min(max, mana + points);
        }
    }

    int mana() {
        return mana;
    }

    int max() {
        return max;
    }

    int tenthsPerSecond() {
        return tenthsPerSecond;
    }

    int refusedFrame() {
        return refusedFrame;
    }

    boolean inUse() {
        return inUse;
    }

    boolean canAfford(int cost) {
        return !inUse || mana >= cost;
    }

    void refuse(int frame) {
        refusedFrame = frame;
    }

    void pay(int cost) {
        if (inUse) {
            mana = Math.max(0, mana - cost);
        }
    }

    /** What comes back each second: its own trickle and {@code auraTenths}; nothing without a pool. */
    float perSecond(int auraTenths) {
        return inUse ? (tenthsPerSecond + auraTenths) / (float) TENTHS : 0f;
    }

    /**
     * The trickle, counted in whole points against a frame carry: its own rate and {@code auraTenths}, the strongest
     * {@code MANA_AURA} lent to it, while the pool is not full. A pool with no trickle of its own still fills at the
     * aura's; no pool at all is given nothing. A second's worth of frames of the rate is exactly the rate, and no
     * rounding is carried from one second into the next.
     */
    void regenerate(int auraTenths) {
        int tenths = !inUse || mana >= max ? 0 : tenthsPerSecond + auraTenths;
        if (tenths <= 0) {
            carry = 0;
            return;
        }
        carry += tenths;
        while (carry >= A_SECOND && mana < max) {
            carry -= A_SECOND;
            mana++;
        }
    }

    void save(SaveOut out) {
        out.writeInt(mana).writeInt(max).writeInt(tenthsPerSecond).writeInt(carry).writeBoolean(inUse)
                .writeInt(refusedFrame);
    }

    void load(SaveIn in) {
        mana = in.readInt();
        max = in.readInt();
        tenthsPerSecond = in.readInt();
        carry = in.readInt();
        inUse = in.readBoolean();
        refusedFrame = in.readInt();
    }
}
