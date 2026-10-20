package uz.dukeengine.client3d;

import com.jme3.audio.Listener;

import java.util.function.Supplier;

/** What the player chose on the settings screen, put into effect: his volumes, his camera shake and his track. */
final class PlayerChoices {

    /** What the player set, kept in a file beside him — see {@link GameSettings}. */
    private final GameSettings preferences;
    private final Visuals visuals;
    private final Listener listener;
    /** Every noise the game makes, once there are any. */
    private final Supplier<GameSounds> noises;
    /** What the effect lists play besides particle systems, which shakes the camera, once it is made. */
    private final Supplier<ListShow> listShow;

    PlayerChoices(GameSettings preferences, Visuals visuals, Listener listener, Supplier<GameSounds> noises,
            Supplier<ListShow> listShow) {
        this.preferences = preferences;
        this.visuals = visuals;
        this.listener = listener;
        this.noises = noises;
        this.listShow = listShow;
    }

    /**
     * Put every knob where the player last left it.
     *
     * <p>Four of them, because one is not enough for the same person: keeping the
     * music low while still hearing what is behind you is an ordinary thing to
     * want, and so is silencing a hero who will not stop talking. The screen's own
     * sounds follow the effects knob rather than getting a fifth — a menu click is
     * an effect that happens to be on a menu.
     */
    void applyVolume() {
        listener.setVolume(1f); // the knobs are per channel now; the listener is not
        var noises = this.noises.get();
        if (noises == null) {
            return;
        }
        var sounds = noises.sounds();
        sounds.masterVolume(preferences.number("volume", 100) / 100f);
        float effects = preferences.number("volEffects", 100) / 100f;
        sounds.volume(SoundBank.Channel.EFFECTS, effects);
        sounds.volume(SoundBank.Channel.UI, effects);
        sounds.volume(SoundBank.Channel.VOICE, preferences.number("volVoice", 100) / 100f);
        sounds.volume(SoundBank.Channel.MUSIC, preferences.number("volMusic", 60) / 100f);
        sounds.volume(SoundBank.Channel.AMBIENCE, preferences.number("volAmbience", 70) / 100f);
    }

    /** The player's camera shake — full, half or none — over the game's own. */
    static final float[] SHAKES = {1f, 0.5f, 0f};
    /** The game's shake scale as it was given, before the player's choice was put over it. */
    private float gameShake = Float.NaN;

    /** The share of the game's shake the player's choice keeps: 0 full, 1 half, 2 none. */
    static float playerShake(int choice) {
        return SHAKES[Math.clamp(choice, 0, SHAKES.length - 1)];
    }

    void applyShake() {
        if (Float.isNaN(gameShake)) {
            gameShake = visuals.getShakeScale();
        }
        visuals.shakeScale(gameShake * playerShake(preferences.number("shake", 0)));
        var listShow = this.listShow.get();
        if (listShow != null) {
            listShow.shakeScale(visuals.getShakeScale());
        }
    }

    /**
     * The track the player chose, or the first the game listed.
     *
     * <p>Music is the one channel where the choice is his rather than the
     * moment's: an effect belongs to whatever just happened, and what plays
     * underneath a dungeon for an hour is taste.
     */
    private SoundBank.Cue chosenTrack() {
        var tracks = visuals.getSounds().musicCues();
        if (tracks.isEmpty()) {
            return null;
        }
        return tracks.get(Math.clamp(preferences.number("musicTrack", 0), 0,
                tracks.size() - 1));
    }

    void playChosenMusic() {
        var track = chosenTrack();
        noises.get().sounds().music(track == null ? null : track.name());
    }
}
