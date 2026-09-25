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
    /** What {@link #playOn} says of a state that names no clip: chosen, and nothing played. */
    static final String STILL = "";

    private int state = NONE;
    private Visuals.ClipState chosen;
    /** The clip of its state it plays: the one drawn as the state began, or since, of idles, as the last ended. */
    private String picked;
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
        return choose(index, states, index < 0 ? null : states.get(index).clip(), length, frame, thing);
    }

    /** The same, playing {@code clip} of the state — the one drawn of its several. */
    boolean choose(int index, List<Visuals.ClipState> states, String clip, double length, int frame, int thing) {
        if (index == state) {
            return false;
        }
        picked = clip;
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

    /** The clip of state {@code index} it plays: drawn from its several as the state begins, kept while it lasts. */
    String pickFor(int index, Visuals.ClipState state, int frame, int thing) {
        return index == this.state && picked != null ? picked : Visuals.Pick.draw(state.picks(), thing, frame, null);
    }

    /**
     * Idles: each played once, and — at the game's frame it ended — another of them drawn, never the one just played,
     * drawn by that frame so every machine draws the same whenever it draws its frames. The clip it plays now.
     */
    String idleOn(AnimComposer composer, int frame, int thing) {
        while (chosen != null && chosen.idles() && length > 0) {
            int ends = since + (int) Math.ceil(length / (GameConstants.SECONDS_PER_LOGICFRAME * speed) - 1e-9);
            if (ends > frame) {
                break;
            }
            var next = Visuals.Pick.draw(chosen.picks(), thing, ends, picked);
            var anim = next == null ? null : composer.getAnimClip(next);
            if (anim == null || anim.getLength() <= 0) {
                break;
            }
            picked = next;
            since = ends;
            from = 0;
            length = anim.getLength();
        }
        return picked;
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
     * name a clip the model does not have ({@code missing} is told its name). Where they choose a state that names no
     * clip, the model plays nothing and stands in its own pose, its roles do not stand in, and nothing is missing:
     * {@link #STILL} — as the reference sets a state's model and clip together ({@code W3DModelDraw::setModelState}).
     *
     * @param playing the clip the composer was last set to, so an unchanged one is not set again
     */
    static String playOn(AnimComposer composer, WordClip clip, String playing, Visuals.UnitVisual look,
            Set<String> holding, int frame, int thing, Consumer<String> missing) {
        int index = look.clipStateFor(holding);
        if (index >= 0 && look.clipStates.get(index).clip() == null) {
            if (clip.choose(index, look.clipStates, 0, frame, thing)) {
                composer.removeCurrentAction(AnimComposer.DEFAULT_LAYER);
                var skin = composer.getSpatial() == null ? null
                        : AnimationLibrary.findControl(composer.getSpatial(), com.jme3.anim.SkinningControl.class);
                if (skin != null) {
                    skin.getArmature().applyInitialPose(); // its own pose, not wherever the last clip left it
                }
            }
            return STILL;
        }
        var drawn = index < 0 ? null : clip.pickFor(index, look.clipStates.get(index), frame, thing);
        var anim = drawn == null ? null : composer.getAnimClip(drawn);
        if (anim == null) {
            if (index >= 0) {
                missing.accept(drawn);
            }
            clip.choose(-1, look.clipStates, 0, frame, thing);
            return null;
        }
        clip.choose(index, look.clipStates, drawn, anim.getLength(), frame, thing);
        String name = clip.idleOn(composer, frame, thing);
        var action = composer.getCurrentAction();
        if (action == null || !name.equals(playing)) {
            action = composer.setCurrentAction(name, AnimComposer.DEFAULT_LAYER, true);
        }
        action.setSpeed(0);
        composer.setTime(AnimComposer.DEFAULT_LAYER, clip.timeAt(frame));
        return name;
    }

    /**
     * The clip a thing the world keeps dead plays: what the words it holds choose, as {@link #playOn} plays a living
     * thing's, else its {@code death} — as far into it as the game's frames since it {@code diedOn}, and then its last
     * frame, where it lies — on whatever model it wears now.
     *
     * @return the clip now on the composer, or {@code playing} where there is neither
     */
    static String playDead(AnimComposer composer, WordClip clip, String playing, Visuals.UnitVisual look,
            Set<String> holding, int frame, int thing, String death, int diedOn, Consumer<String> missing) {
        var chosen = playOn(composer, clip, playing, look, holding, frame, thing, missing);
        var fell = chosen != null || death == null ? null : composer.getAnimClip(death);
        if (fell == null) {
            return chosen != null ? chosen : playing;
        }
        var action = composer.getCurrentAction();
        if (action == null || !death.equals(playing)) {
            action = composer.setCurrentAction(death, AnimComposer.DEFAULT_LAYER, true);
        }
        action.setSpeed(0);
        double since = Math.max(0, frame - diedOn) * GameConstants.SECONDS_PER_LOGICFRAME;
        composer.setTime(AnimComposer.DEFAULT_LAYER, Math.min(since, Math.nextDown(fell.getLength())));
        return death;
    }

    /** How far into its clip it is in the game's frame {@code frame}, in seconds. */
    double timeAt(int frame) {
        if (chosen == null || length <= 0) {
            return 0;
        }
        double elapsed = Math.max(0, frame - since) * GameConstants.SECONDS_PER_LOGICFRAME * speed;
        return switch (chosen.idles() ? Visuals.ClipMode.ONCE : chosen.mode()) {
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
