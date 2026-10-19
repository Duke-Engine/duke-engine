package uz.dukeengine.client3d.rpg;

import java.util.List;
import uz.dukeengine.game.GameCamera;

/**
 * A few shots in turn, the camera the cutscene's and the screen letterboxed while they play ({@code GameCamera
 * .letterbox}): where each shot flies, what is said in it and how long it holds. The game plays it on its own clock;
 * this is what its files write.
 */
public record Cutscene(String name, List<Shot> shots) {

    public static final Cutscene DEFAULTS = new Cutscene("", List.of());

    public Cutscene {
        shots = shots == null ? List.of() : List.copyOf(shots);
    }

    /**
     * One shot: the camera flown to a zone's middle over {@code frames}, a conversation said, or both, and then held for
     * {@code hold} frames. Over the same frames it may turn to face {@code turn} degrees (0 north, as play's), tilt to
     * look down {@code pitch} degrees and stand {@code zoom} units back — each left as it is when not given — all eased
     * as {@code ease} says; or fly a {@code path} of keys through the zones it names ({@code GameCamera.along}).
     */
    public record Shot(String zone, int frames, int hold,
            @uz.dukeengine.core.data.Link(uz.dukeengine.rpg.story.Conversation.class) String says, float turn,
            float pitch, float zoom, GameCamera.Ease ease, List<Key> path) {

        public static final Shot DEFAULTS = new Shot("", 0, 0, "", Float.NaN, Float.NaN, Float.NaN,
                GameCamera.Ease.IN_OUT, List.of());

        public Shot {
            path = path == null ? List.of() : List.copyOf(path);
        }

        /** Whether it turns, tilts or draws the camera in or back, or flies a path. */
        public boolean framed() {
            return !Float.isNaN(turn) || !Float.isNaN(pitch) || !Float.isNaN(zoom) || !path.isEmpty();
        }

        /** How long the shot lasts, in frames. */
        public int length() {
            return Math.max(1, (zone.isBlank() && !framed() ? 0 : frames) + hold);
        }
    }

    /** One point of a shot's path: a zone's middle, and the turn, pitch and zoom there — each NaN for as it is. */
    public record Key(String zone, float turn, float pitch, float zoom) {

        public static final Key DEFAULTS = new Key("", Float.NaN, Float.NaN, Float.NaN);
    }
}
