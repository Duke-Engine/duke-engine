package uz.dukeengine.client3d;

import com.jme3.system.AppSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import uz.dukeengine.game.DukeGame;

/**
 * Launches a {@link DukeGame} with the 3D client — the Unity-style "press
 * play" for a full 3D RTS:
 *
 * <pre>{@code
 * var game = DukeGame.create("My RTS").loadUnits(DukeGame.STARTER_UNITS)...;
 * var visuals = Visuals.create().unit("Tank", u -> u.model("Models/tank.gltf"));
 * Duke3D.launch(game, visuals);   // opens the 3D window, blocks until closed
 * }</pre>
 *
 * <p>The simulation runs deterministically on its own thread; the jME window is
 * pure presentation. Closing the window stops both.
 *
 * <p>Pass a {@link Shell} to say what the game puts in front of itself — its own
 * menu entries, or none at all. Without one it gets the client's standard menu,
 * which is what every game got before it could choose.
 *
 * <p><b>The client as a game talks to it.</b> {@link #of} makes one to keep: what it is launched with is said on it
 * before {@link #launch}, and what the game asks of the client while it runs — a sound, a volume — is asked of it
 * afterwards, from whichever thread the game's code is on. Nothing asked of it reaches the simulation.
 */
public final class Duke3D {

    private final DukeGame game;
    private final Visuals visuals;
    private Shell shell = Shell.standard();
    private Hotkeys hotkeys = Hotkeys.none();
    private int width = 1280;
    private int height = 720;
    private Painter painter;
    private CanvasInput input;
    private java.util.function.IntConsumer loading;

    /** The running client, once launched; what is asked of it before then waits here, in order. */
    private DukeRtsApp app;
    private final List<Consumer<DukeRtsApp>> waiting = new ArrayList<>();

    private Duke3D(DukeGame game, Visuals visuals) {
        this.game = game;
        this.visuals = visuals;
    }

    /** The client for this game, drawn as {@code visuals} says, to be launched and then talked to. */
    public static Duke3D of(DukeGame game, Visuals visuals) {
        return new Duke3D(game, visuals);
    }

    public Duke3D shell(Shell shell) {
        this.shell = shell == null ? Shell.standard() : shell;
        return this;
    }

    /**
     * With keys of the game's own — see {@link Hotkeys}. The client keeps its
     * standard controls; these are what a particular game adds on top.
     */
    public Duke3D hotkeys(Hotkeys hotkeys) {
        this.hotkeys = hotkeys == null ? Hotkeys.none() : hotkeys;
        return this;
    }

    /** The window's size for somebody who has never chosen one; what the player last chose wins. */
    public Duke3D window(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    /**
     * The game's own drawing, painted every frame over the world and the client's HUD — see {@link Canvas}. With
     * {@link Shell#drawnByTheGame} it is every screen there is.
     */
    public Duke3D canvas(Painter painter) {
        this.painter = painter;
        return this;
    }

    /** The game's first look at the raw input; what it takes goes no further — see {@link CanvasInput}. */
    public Duke3D input(CanvasInput input) {
        this.input = input;
        return this;
    }

    /**
     * Told how far a match has got loading, 0 to 100, each new figure once and rising, on the window's thread — the
     * canvas painted after every one — for a load screen of the game's own. Building the match is the first 40
     * (templates, the world, players, what the game placed, the match it assembled, the pathfinder); reading and
     * showing the card its art is the rest; 100 is ready. In a network game every machine's figure is shared: see
     * {@link DukeGame#onPeerLoadProgress}.
     */
    public Duke3D onLoading(java.util.function.IntConsumer percent) {
        this.loading = percent;
        return this;
    }

    public static void launch(DukeGame game, Visuals visuals) {
        of(game, visuals).launch();
    }

    public static void launch(DukeGame game, Visuals visuals, Shell shell) {
        of(game, visuals).shell(shell).launch();
    }

    public static void launch(DukeGame game, Visuals visuals, int width, int height) {
        of(game, visuals).window(width, height).launch();
    }

    public static void launch(DukeGame game, Visuals visuals, Shell shell, Hotkeys hotkeys) {
        of(game, visuals).shell(shell).hotkeys(hotkeys).launch();
    }

    public static void launch(DukeGame game, Visuals visuals, Shell shell, int width, int height) {
        of(game, visuals).shell(shell).window(width, height).launch();
    }

    public static void launch(DukeGame game, Visuals visuals, Shell shell, Hotkeys hotkeys,
            int width, int height) {
        of(game, visuals).shell(shell).hotkeys(hotkeys).window(width, height).launch();
    }

    /** Open the window and run the game in it; blocks until the window is closed. */
    public void launch() {
        // the simulation starts when the player presses Play — or at once, if the
        // game asked for no menu at all
        var client = new DukeRtsApp(game, visuals, shell, hotkeys, painter, input, loading);
        synchronized (waiting) {
            app = client;
            for (var task : waiting) {
                client.enqueue(() -> task.accept(client));
            }
            waiting.clear();
        }
        var settings = new AppSettings(true);
        settings.setTitle(game.getTitle());
        // saved display settings win over the caller's defaults
        // What the player last chose, from the file beside him. The caller's
        // numbers are only the answer for somebody who has never chosen.
        var chosen = new uz.dukeengine.client3d.GameSettings();
        chosen.inheritFrom(DukeRtsApp.PREFS, "volume", "fullscreen", "volEffects",
                "volVoice", "volMusic", "musicTrack");
        settings.setResolution(chosen.number("width", width), chosen.number("height", height));
        settings.setFullscreen(chosen.flag("fullscreen", false));
        settings.setVSync(true);
        client.setSettings(settings);
        client.setShowSettings(false);
        client.setDisplayStatView(false);
        // Frames per second is a developer's number. It sat over the line of
        // controls at the bottom of the screen, and neither could be read.
        client.setDisplayFps(false);
        client.setPauseOnLostFocus(false);
        client.start();

        try {
            client.awaitStop();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        client.currentGame().stop();
        var simThread = client.getSimThread();
        if (simThread != null) {
            try {
                simThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ---- what the game asks of the client while it runs, from any thread ----

    /**
     * Play a sound cue of the game's {@link SoundBank} now, flat — on its own channel, at no place — with the cue's
     * own rules kept: which of its files, its loudness, its gap, cutting off the last of itself. What a front end
     * plays when a button is pressed. A name the bank does not have plays nothing and is logged once.
     */
    public void sound(String cue) {
        later(client -> client.playFlat(cue));
    }

    /**
     * How loud a cue plays from now on against its own loudness, until changed: 1 is its own, 0 silences it, 6
     * six times as loud (as loud as the sound device goes).
     */
    public void cueVolume(String cue, float multiplier) {
        later(client -> client.cueVolume(cue, multiplier));
    }

    /**
     * The game's volume for a channel, 0 to 1 — music, sound, speech, the interface — multiplied with the
     * player's own setting for it rather than replacing it, as the reference keeps a script volume beside the
     * player's.
     */
    public void volume(SoundBank.Channel channel, float zeroToOne) {
        later(client -> client.gameVolume(channel, zeroToOne));
    }

    /**
     * Play this match: whatever is running stops, this one is built, its art read — the game's canvas drawn all the
     * while — and it starts. A match is a {@link DukeGame} played once: the game makes a fresh one for each, with
     * the players, sides, colours, teams, money and map its setup chose, and for a network game the session its
     * lobby made ({@link DukeGame#multiplayer}).
     */
    public void startMatch(DukeGame match) {
        later(client -> client.startMatch(match));
    }

    /**
     * End the match and go back to the front end: the simulation stops and is let go of, a network session with it,
     * and the game's own front end — or the client's menu — is what is shown.
     */
    public void frontEnd() {
        later(DukeRtsApp::backToFrontEnd);
    }

    /**
     * Whether a loaded match waits at 100 for the game to let it start — for a load screen that fades to black before
     * the match appears. Held, no frame of the match is stepped or drawn until {@link #releaseMatchStart}.
     */
    public void holdMatchStart(boolean hold) {
        later(client -> client.holdMatchStart(hold));
    }

    /** Let the match being loaded start once it is loaded — at once if it is there already. */
    public void releaseMatchStart() {
        later(DukeRtsApp::releaseMatchStart);
    }

    /** Do this on the client's own thread: at its next frame, or once it is launched. */
    private void later(Consumer<DukeRtsApp> task) {
        DukeRtsApp running;
        synchronized (waiting) {
            running = app;
            if (running == null) {
                waiting.add(task);
                return;
            }
        }
        running.enqueue(() -> task.accept(running));
    }
}
