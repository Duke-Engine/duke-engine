package uz.dukeengine.client3d;

import com.jme3.anim.AnimComposer;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.Consumer;
import uz.dukeengine.core.GameConstants;

/**
 * Where one thing's clip stands as the words it holds choose it — the reference's condition states' {@code
 * Animation}, {@code AnimationMode} and {@code Flags} ({@code W3DModelDraw::adjustAnimation}). Worked out from the
 * game's frames alone, so every machine shows the same frame of it and a paused game holds it still.
 */
final class WordClip {

    private static final int NONE = -1;

    private int state = NONE;
    private Visuals.ClipState chosen;
    /** The game's frame the chosen state began. */
    private int since;
    /** Where in its clip it began, in seconds. */
    private double from;
    private double length;
    /** How fast it plays, times its own pace: drawn between its state's slowest and fastest as it starts. */
    private double speed = 1;

    /**
     * The state its words now best fit — an index into the look's states, or -1 for none — seen in the game's frame
     * {@code frame}, with a clip {@code length} seconds long.
     *
     * @return whether the state changed, and its clip is to be set on the model again
     */
    boolean choose(int index, List<Visuals.ClipState> states, double length, int frame, int thing) {
        if (index == state) {
            return false;
        }
        var next = index < 0 ? null : states.get(index);
        double fraction = chosen != null && next != null && keepsFrame(chosen, next) && this.length > 0
                ? timeAt(frame) / this.length : -1;
        state = index;
        chosen = next;
        since = frame;
        this.length = Math.max(0, length);
        if (next != null) {
            from = startOf(next, fraction, thing, frame);
            speed = next.slowest() == next.fastest() ? next.slowest()
                    : next.slowest() + new SplittableRandom(((long) thing << 32) ^ frame ^ 0x5DEECE66DL).nextDouble()
                            * (next.fastest() - next.slowest());
        }
        return true;
    }

    /** How fast the clip chosen plays, times its own pace. */
    double speed() {
        return speed;
    }

    /** Whether its words chose a clip, rather than leaving it to its roles. */
    boolean chosen() {
        return chosen != null;
    }

    /** The keep group of the state chosen, or null. */
    String keepGroup() {
        return chosen == null ? null : chosen.keepGroup();
    }

    /** How far through its clip it is in the game's frame {@code frame}, a share of its length; 0 for none. */
    double share(int frame) {
        return chosen == null || length <= 0 ? 0 : timeAt(frame) / length;
    }

    /** Whether its clip is one played once and has not yet played to its end: a look waiting for it is not left yet. */
    boolean holdsBack(int frame) {
        if (chosen == null || length <= 0) {
            return false;
        }
        return switch (chosen.mode()) {
            case ONCE -> timeAt(frame) < last();
            case ONCE_BACKWARDS -> timeAt(frame) > 0;
            default -> false;
        };
    }

    /**
     * The clip {@code look}'s words choose for what holds {@code holding}, set on {@code composer} at the game's frame
     * {@code frame} and held there — its time the frame's, never the window's — or null where they choose none, or
     * name a clip the model does not have ({@code missing} is told its name).
     *
     * @param playing the clip the composer was last set to, so an unchanged one is not set again
     */
    static String playOn(AnimComposer composer, WordClip clip, String playing, Visuals.UnitVisual look,
            Set<String> holding, int frame, int thing, Consumer<String> missing) {
        int index = look.clipStateFor(holding);
        var anim = index < 0 ? null : composer.getAnimClip(look.clipStates.get(index).clip());
        if (anim == null) {
            if (index >= 0) {
                missing.accept(look.clipStates.get(index).clip());
            }
            clip.choose(-1, look.clipStates, 0, frame, thing);
            return null;
        }
        String name = look.clipStates.get(index).clip();
        clip.choose(index, look.clipStates, anim.getLength(), frame, thing);
        var action = composer.getCurrentAction();
        if (action == null || !name.equals(playing)) {
            action = composer.setCurrentAction(name, AnimComposer.DEFAULT_LAYER, true);
        }
        action.setSpeed(0);
        composer.setTime(AnimComposer.DEFAULT_LAYER, clip.timeAt(frame));
        return name;
    }

    /** How far into its clip it is in the game's frame {@code frame}, in seconds. */
    double timeAt(int frame) {
        if (chosen == null || length <= 0) {
            return 0;
        }
        double elapsed = Math.max(0, frame - since) * GameConstants.SECONDS_PER_LOGICFRAME * speed;
        return switch (chosen.mode()) {
            case HOLD -> from;
            case ONCE -> Math.min(from + elapsed, last());
            case ONCE_BACKWARDS -> Math.max(from - elapsed, 0);
            case LOOP -> wrap(from + elapsed);
            case LOOP_BACKWARDS -> wrap(from - elapsed);
        };
    }

    /**
     * Where a state's clip begins: where it says; else as far through as the last one was, where the two keep the
     * frame together; else at its first frame, or its last for one played backwards. As the reference's, a start the
     * state names wins over the frame kept.
     */
    private double startOf(Visuals.ClipState state, double fraction, int thing, int frame) {
        if (state.start() != null) {
            return switch (state.start()) {
                case FIRST -> 0;
                case LAST -> last();
                case RANDOM -> new SplittableRandom(((long) thing << 32) ^ frame).nextDouble() * length;
            };
        }
        if (fraction >= 0) {
            return Math.min(fraction * length, last());
        }
        boolean backwards = state.mode() == Visuals.ClipMode.ONCE_BACKWARDS
                || state.mode() == Visuals.ClipMode.LOOP_BACKWARDS;
        return backwards ? last() : 0;
    }

    private static boolean keepsFrame(Visuals.ClipState was, Visuals.ClipState next) {
        return was.keepGroup() != null && was.keepGroup().equals(next.keepGroup());
    }

    /** The clip's last instant: its end itself is its start again to the model, which wraps a clip's time. */
    private double last() {
        return Math.nextDown(length);
    }

    private double wrap(double time) {
        double wrapped = time % length;
        return wrapped >= 0 ? wrapped : Math.min(wrapped + length, last()); // a length added back may round up to it
    }
}
