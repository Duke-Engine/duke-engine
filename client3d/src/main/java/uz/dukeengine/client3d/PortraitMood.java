package uz.dukeengine.client3d;

/**
 * The portrait's clock and its mood: what the creature in the frame should be
 * doing, and whether it is owed a frame yet.
 *
 * <p>Deliberately jME-free. Which clip a nearly-dead hero plays, and how often a
 * hundred-and-fifty-pixel picture is worth redrawing, are questions about the
 * game rather than about the graphics card — and answering them here means they
 * can be settled in a build instead of by watching a corner of a screen.
 *
 * <p>Everything it reads is already in hand: health, whether it has a target and
 * whether it died come out of the snapshot the client draws from, and the level
 * out of the status line the panel is already parsing. Nothing is asked of the
 * simulation, and nothing is written back to it.
 *
 * <p><b>The rate is a ceiling, not a target.</b> A portrait asked for twenty-four
 * frames a second gets at most that many: the owed time is accumulated and spent
 * when it passes a frame's worth, so at sixty ticks a second it draws on every
 * third one. Between those the viewport is switched off and costs nothing at all.
 */
final class PortraitMood {

    /**
     * How long a clip lasts, asked of whatever can measure one.
     *
     * <p>A one-shot has to end, and the honest length for "his death" is the
     * length of his death. Only the loaded model knows it, so it is handed in —
     * which also lets a test say how long a clip is without owning a model.
     */
    interface Lengths {

        /** Seconds, or 0 for a clip nothing has heard of. */
        float of(String clipName);
    }

    /** What a one-shot lasts when nothing can measure its clip. */
    private static final float UNMEASURED = 1f;

    /**
     * The most the portrait may be advanced in one step.
     *
     * <p>A loading screen, an alt-tab or a floor being built are all seconds in
     * which no portrait frame is drawn, and the owed time goes on adding up. Spent
     * in one step it would jump the animation forward by the whole stall, which
     * looks like the model teleporting into a different pose.
     */
    private static final float LONGEST_STEP = 0.25f;

    private final PortraitLook look;
    private final Lengths lengths;
    private final float period;

    private int subject = -1;
    /** The level as the status line last spelled it; empty until one is seen. */
    private String rank = "";
    private boolean attacking;
    private float health = 1f;

    private PortraitLook.State oneShot;
    private float oneShotLeft;
    private boolean dead;
    private float deathLeft;

    /** Time owed to the portrait, waiting for a frame to spend it on. */
    private float owed;
    /** Something changed that has to be seen now rather than at the next tick. */
    private boolean atOnce = true;

    PortraitMood(PortraitLook look, int framesPerSecond, Lengths lengths) {
        this.look = look;
        this.lengths = lengths;
        this.period = 1f / Math.max(1, framesPerSecond);
    }

    /**
     * A different creature is in the frame now.
     *
     * <p>Everything remembered is about the last one: his level, whether he was
     * fighting, whether he had fallen. Kept, they would have the new one going up
     * a level the moment he was clicked on — which is exactly what happens when
     * the player clicks a skeleton and clicks back.
     */
    void nowShowing(int subjectId) {
        if (subjectId == subject) {
            return;
        }
        subject = subjectId;
        rank = "";
        attacking = false;
        health = 1f;
        oneShot = null;
        oneShotLeft = 0f;
        dead = false;
        deathLeft = 0f;
        atOnce = true;
    }

    /**
     * What the world says about him this frame.
     *
     * @param rank the level as the panel read it, which is a word rather than a
     *     number because the game spells its own levels. Only its <em>changing</em>
     *     means anything here
     */
    void sees(boolean attacking, float healthFraction, String rank) {
        if (dead) {
            return; // he is past caring what the snapshot says
        }
        this.attacking = attacking;
        this.health = healthFraction;
        var now = rank == null ? "" : rank;
        if (now.equals(this.rank)) {
            return;
        }
        // Not on the first one. The first level seen is him being looked at, not
        // him going up — the same trap GameSounds pays for with the same guard.
        if (!this.rank.isEmpty()) {
            begin(PortraitLook.State.LEVEL_UP);
        }
        this.rank = now;
    }

    /**
     * He died — told outright, rather than guessed from his leaving the snapshot.
     *
     * <p>It has to be told, and it is the one thing here that could not be read
     * off the world: a dead creature is gone from the next snapshot, so by the
     * time the portrait could notice, there is nothing left to play a death on.
     */
    void died() {
        if (dead) {
            return;
        }
        dead = true;
        oneShot = null;
        float length = lengths.of(look.clip(PortraitLook.State.DEAD));
        deathLeft = length > 0f ? length : UNMEASURED;
        atOnce = true;
    }

    /**
     * Whether the frame still belongs to this creature although the card has moved
     * on.
     *
     * <p>Death takes the creature out of the snapshot, which takes it out of the
     * selection, which empties the card — all on the frame he falls. A portrait
     * that followed the card would therefore never draw a single frame of the
     * death it was given a clip for. So it keeps him, and goes on keeping him
     * after the clip has finished: what is in the frame then is where he fell,
     * which is the right thing for it to be showing.
     */
    boolean holding() {
        return dead;
    }

    /**
     * Seconds to advance the portrait by now, or 0 if it is not owed a frame.
     *
     * <p>Called once a frame, whether or not anything is drawn — the one-shots are
     * measured in real time rather than in portrait frames, or a death would last
     * three times as long as its clip.
     */
    float advance(float tpf) {
        if (oneShot != null) {
            oneShotLeft -= tpf;
            if (oneShotLeft <= 0f) {
                oneShot = null;
                atOnce = true; // back to standing on the frame the flourish ends
            }
        }
        if (dead && deathLeft > 0f) {
            deathLeft -= tpf;
        }
        owed += tpf;
        if (dead && deathLeft <= 0f && !atOnce) {
            owed = 0f;
            return 0f; // he has fallen; the frame holds what it last drew of him
        }
        if (!atOnce && owed < period) {
            return 0f;
        }
        float seconds = Math.min(owed, LONGEST_STEP);
        owed = 0f;
        atOnce = false;
        return seconds;
    }

    /**
     * Which of the five he is in.
     *
     * <p>Order is priority, and the one worth stating is that being nearly dead
     * beats being in a fight. He is in a fight for most of the game; he is at a
     * tenth of his health for the few seconds when the player has to decide
     * whether to run, and that is the moment the frame is for.
     */
    PortraitLook.State state() {
        if (dead) {
            return PortraitLook.State.DEAD;
        }
        if (oneShot != null) {
            return oneShot;
        }
        if (health <= look.hurtBelowPercent() / 100f) {
            return PortraitLook.State.HURT;
        }
        return attacking ? PortraitLook.State.FIGHT : PortraitLook.State.CALM;
    }

    /** The clip that state plays, or {@code null} if the game named none for it. */
    String clip() {
        return look.clip(state());
    }

    boolean loops() {
        return look.loops(state());
    }

    float speed() {
        return look.speed(state());
    }

    private void begin(PortraitLook.State state) {
        oneShot = state;
        float length = lengths.of(look.clip(state));
        oneShotLeft = length > 0f ? length : UNMEASURED;
        atOnce = true;
    }
}
