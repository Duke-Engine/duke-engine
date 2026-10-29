package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.input.KeyInput;
import com.jme3.scene.Node;
import com.jme3.system.AppSettings;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import uz.dukeengine.client3d.platform.ClientPlatform;

import uz.dukeengine.core.view.WorldSnapshot;
import uz.dukeengine.game.DukeGame;

/**
 * What is on the screen, and the client's own menus that put it there: the front menu, the questions a game asks
 * before it starts, the skirmish setup and the LAN lobby, the pause menu, and the settings screen.
 *
 * <p>Which entries and rows exist is the game's call ({@link Shell}); how they are laid out and walked is the
 * client's, out of the same stone as every other menu ({@link StoneMenu}).
 */
final class Screens {

    /** What the menus ask of the window they stand in. */
    interface Host {
        /** The match the window draws now. */
        DukeGame game();

        /** The match the front menu is about: the one it would play, whatever is drawn behind it. */
        DukeGame frontGame();

        WorldSnapshot snapshot();

        /** Play the match: read its art, or start it once read. */
        void startGame();

        /** The match let go, the game told first, and the front end shown. */
        void quitToTheMainMenu();

        /** The window closed. */
        void quitToTheDesktop();

        /** The front menu's match put back in place of the backdrop: it is about to be played. */
        void takeTheFrontMatchBack();

        /** The simulation frozen or let go — see {@code DukeRtsApp.setSimulationPaused}. */
        void paused(boolean paused);

        /** Done on the window's thread, at its next frame. */
        void later(Runnable task);

        /** Whether the game chooses all the music, the client none of its own. */
        boolean musicByGame();
    }

    private final Host host;
    private final Shell shell;
    private final Visuals visuals;
    private final Hotkeys hotkeys;
    /** What the player set, kept in a file beside him — see {@link GameSettings}. */
    private final GameSettings preferences;
    private final PlayerChoices choices;
    private final WindowDisplay windowDisplay;
    private final KeyBindings keyBindings;
    private final Controls controls;
    /** Every noise the game makes, once there are any. */
    private final Supplier<GameSounds> noises;
    private final List<HudPart> parts;
    private final Supplier<AppSettings> settings;
    private final AssetManager assetManager;
    private final Node guiNode;
    private final BitmapFont guiFont;
    /** A font the game named, or the client's own where it names none or it will not load. */
    private final Function<String, BitmapFont> fonts;
    private final com.jme3.system.Timer timer;

    private Screen screen = Screen.MENU;
    private Screen settingsReturn = Screen.MENU;
    private StoneMenu menu;
    /**
     * The lettering the game asked its menus to be set in.
     */
    private StoneCraft craft;

    Screens(Host host, Shell shell, Visuals visuals, Hotkeys hotkeys, GameSettings preferences, PlayerChoices choices,
            WindowDisplay windowDisplay, KeyBindings keyBindings, Controls controls, Supplier<GameSounds> noises,
            List<HudPart> parts, Supplier<AppSettings> settings, AssetManager assetManager, Node guiNode,
            BitmapFont guiFont, Function<String, BitmapFont> fonts, com.jme3.system.Timer timer, float width,
            float height) {
        this.host = host;
        this.shell = shell;
        this.visuals = visuals;
        this.hotkeys = hotkeys;
        this.preferences = preferences;
        this.choices = choices;
        this.windowDisplay = windowDisplay;
        this.keyBindings = keyBindings;
        this.controls = controls;
        this.noises = noises;
        this.parts = parts;
        this.settings = settings;
        this.assetManager = assetManager;
        this.guiNode = guiNode;
        this.guiFont = guiFont;
        this.fonts = fonts;
        this.timer = timer;
        this.menu = buildMenu(width, height);
    }

    /** What is on the screen now. */
    Screen now() {
        return screen;
    }

    /** The screen put up by the window itself: the match playing, or loading. */
    void set(Screen now) {
        screen = now;
    }

    /** The screen the settings were opened from, and go back to. */
    Screen settingsReturn() {
        return settingsReturn;
    }

    StoneMenu menu() {
        return menu;
    }

    /** The lettering the game asked its menus — and its command bar — to be set in. */
    StoneCraft craft() {
        if (craft == null) {
            craft = new StoneCraft(assetManager, guiFont, visuals.getMenuStyle());
        }
        return craft;
    }

    /**
     * The menu built again at the window's new size, and the screen that was up shown on it. Rebuilt rather than moved:
     * everything in it is laid out from the screen it was built against.
     */
    void rebuild(float width, float height) {
        menu.destroy();
        menu = buildMenu(width, height);
        switch (screen) {
            case MENU, FRONT -> showMainMenu();
            case PAUSED -> showPauseMenu();
            case SETTINGS -> showSettingsMenu(settingsReturn);
            case PLAYING -> menu.hide();
        }
    }

    // skirmish choice (null = the game's defaults)
    private String chosenMap;
    private final java.util.List<String> chosenFactions = new java.util.ArrayList<>();

    /**
     * Show the front menu the game asked for.
     *
     * <p>Which entries exist is the game's call ({@link Shell}); whether one of
     * them would mean anything is still the client's. Offering a LAN game to a
     * game that seats one player, or a map choice to one with a single map, would
     * be a dead button.
     */
    void showMainMenu() {
        if (shell.isDrawnByTheGame()) {
            screen = Screen.FRONT; // the game's own front end, on its canvas
            menu.hide();
            return;
        }
        if (shell.startsImmediately()) {
            host.startGame(); // this game has no front menu; nothing to come back to
            return;
        }
        screen = Screen.MENU;
        if (noises.get() != null && !host.musicByGame()) {
            noises.get().sounds().music(null); // the dungeon is behind him for now
        }
        screen = Screen.MENU;
        var items = new java.util.ArrayList<StoneMenu.Row>();
        for (var row : shell.frontRows()) {
            items.add(new StoneMenu.Action(row.label(), () -> takeFrontRow(row), false, row.picture()));
        }
        for (var chosen : shell.entries()) {
            var action = actionFor(chosen.getKey());
            if (action != null) {
                items.add(new StoneMenu.Action(chosen.getValue(), action));
            }
        }
        menu.show(host.frontGame().getTitle(), host.frontGame().getSubtitle(), items,
                say("UP DOWN choose    ENTER take"), version(), false);
    }

    /** A game's row on the front menu taken: its own work, then its question if it has one, else the menu again. */
    private void takeFrontRow(Shell.Option row) {
        if (row.taken() != null) {
            row.taken().run();
        }
        if (row.next() != null) {
            screen = Screen.MENU;
            showQuestion(row.next(), this::showMainMenu, host::startGame);
        } else if (screen == Screen.MENU) {
            showMainMenu();
        }
    }

    /**
     * What an entry does, or {@code null} when it would do nothing worth offering.
     */
    private Runnable actionFor(Shell.Entry entry) {
        return switch (entry) {
            // Play does not necessarily play. A game with something to ask first
            // asks it here — see showQuestion — and starts when it is answered.
            case PLAY -> shell.question() == null ? host::startGame : this::showQuestion;
            case SKIRMISH -> !host.frontGame().getMapChoices().isEmpty() && !host.frontGame().isMultiplayer()
                    ? this::showSkirmishMenu : null;
            case HOST_LAN -> host.frontGame().supportsMultiplayer() && !host.frontGame().isMultiplayer()
                    ? this::hostFlow : null;
            case JOIN_LAN -> host.frontGame().supportsMultiplayer() && !host.frontGame().isMultiplayer()
                    ? this::joinFlow : null;
            case SETTINGS -> () -> showSettingsMenu(Screen.MENU);
            case QUIT -> host::quitToTheDesktop;
        };
    }

    /**
     * The one thing the game wants settled before it will start.
     *
     * <p>Built from the same stone as every other menu, and answered the same way:
     * a column of words, one of which is taken. What it is <em>about</em> is the
     * game's — this client has never heard of a hero, a difficulty or a side.
     *
     * <p>There is no entry that skips it. That is the whole point of asking: a
     * question with a way past it is a setting, and the player would press Play,
     * get whatever a file happened to say, and never find out he had a choice.
     * Back returns to the front menu, which is a way out of the game rather than
     * a way into it without answering.
     *
     * <p>The answer is taken <em>before</em> the world runs a frame — the
     * simulation thread does not exist until the match starts — so what it does
     * needs no hopping between threads and cannot race the first frame.
     */
    private void showQuestion() {
        var question = shell.question();
        if (question == null) {
            host.startGame();
            return;
        }
        showQuestion(question, this::showMainMenu);
    }

    /**
     * One question of the path, and the way back out of it.
     *
     * <p>{@code back} is the screen before this one rather than the front menu,
     * so a path three deep is walked backwards a step at a time. Handed in rather
     * than remembered because that is exactly what it is: each screen already
     * knows what opened it, and a stack would be a second copy of the path that
     * could disagree with the first.
     */
    private void showQuestion(Shell.Question question, Runnable back) {
        screen = Screen.MENU;
        showQuestion(question, back, host::startGame);
    }

    /**
     * The same, ending in {@code done} once the last of its answers is taken, on whatever screen it is asked from —
     * so a question asked from the pause menu is still the pause menu's if the window is resized under it.
     */
    private void showQuestion(Shell.Question question, Runnable back, Runnable done) {
        var items = new java.util.ArrayList<StoneMenu.Row>();
        for (var option : question.options()) {
            items.add(new StoneMenu.Action(option.label(), () -> {
                if (option.taken() != null) {
                    option.taken().run();
                }
                // Either it narrows the choice further or it was the last of them.
                if (option.next() != null) {
                    showQuestion(option.next(), () -> showQuestion(question, back, done), done);
                } else {
                    done.run();
                }
            }, false, option.picture()));
            if (!option.blurb().isBlank()) {
                // Under the name rather than beside it: what he is choosing is the
                // word above, and the line below says what taking it means.
                items.add(new StoneMenu.Words("", option.blurb()));
            }
        }
        items.add(new StoneMenu.Action(say("Back"), back));
        menu.show(host.frontGame().getTitle(), question.title(), items,
                question.hint(), version(), false);
    }

    /**
     * Choose the map and cycle each player's faction before playing.
     */
    private void showSkirmishMenu() {
        var front = host.frontGame();
        var maps = front.getMapChoices();
        var factions = front.getFactionChoices();
        if (chosenMap == null && !maps.isEmpty()) {
            chosenMap = maps.getFirst();
        }
        while (chosenFactions.size() < 2 && !factions.isEmpty()) {
            chosenFactions.add(factions.get(chosenFactions.size() % factions.size()));
        }

        var items = new java.util.ArrayList<StoneMenu.Row>();
        // The map's own preview beside the row while it is lit: what a map looks like is most of what a
        // player is choosing between, and a column of names says none of it. A game that names no picture
        // gets the row it always got.
        items.add(new StoneMenu.Action("Map: " + chosenMap, () -> {
            chosenMap = maps.get((maps.indexOf(chosenMap) + 1) % maps.size());
            showSkirmishMenu();
        }, false, front.getMapPicture(chosenMap)));
        for (int p = 0; p < chosenFactions.size(); p++) {
            final int player = p;
            items.add(new StoneMenu.Action("Player " + (p + 1) + ": " + chosenFactions.get(p), () -> {
                int next = (factions.indexOf(chosenFactions.get(player)) + 1) % factions.size();
                chosenFactions.set(player, factions.get(next));
                showSkirmishMenu();
            }));
        }
        items.add(new StoneMenu.Action("Start match", () -> {
            front.selectSkirmish(chosenMap, chosenFactions);
            host.startGame();
        }));
        items.add(new StoneMenu.Action(say("Back"), this::showMainMenu));
        menu.show(front.getTitle(), "skirmish setup", items,
                "UP DOWN choose    ENTER take    ESC back", version(), false);
    }

    // ---- multiplayer flows (menu thread-hops: network blocks, jME must not) ----

    private void hostFlow() {
        host.takeTheFrontMatchBack();
        var game = host.game();
        int port = uz.dukeengine.game.MultiplayerSession.DEFAULT_PORT;
        int wanted = game.getMaxNetworkPlayers();
        showLobby(port, 0, wanted);
        new Thread(() -> {
            try {
                // The count comes back as each guest arrives, so the host can see
                // who is still missing instead of staring at "waiting…".
                game.hostMultiplayer(port, wanted, joined -> host.later(() -> showLobby(port, joined, wanted)));
                host.later(host::startGame);
            } catch (Exception _) {
                host.later(this::showMainMenu); // cancelled or failed — back to the menu
            }
        }, "duke-host").start();
    }

    private void showLobby(int port, int joined, int wanted) {
        var game = host.game();
        menu.show(game.getTitle(),
                "hosting on port " + port + " — " + (joined + 1) + " of " + wanted + " players in",
                java.util.List.of(new StoneMenu.Action("Cancel", () -> {
                    game.cancelHosting();
                    showMainMenu();
                })), "", version(), false);
    }

    private void joinFlow() {
        host.takeTheFrontMatchBack();
        var game = host.game();
        menu.show(game.getTitle(), "joining…", java.util.List.of(), "", version(), false);
        new Thread(() -> {
            var ip = ClientPlatform.get().prompt().ask("Host IP address:", "127.0.0.1");
            if (ip == null || ip.isBlank()) {
                host.later(this::showMainMenu);
                return;
            }
            try {
                game.joinMultiplayer(ip.trim(), uz.dukeengine.game.MultiplayerSession.DEFAULT_PORT);
                host.later(host::startGame);
            } catch (Exception _) {
                host.later(this::showMainMenu);
            }
        }, "duke-join").start();
    }

    void showPauseMenu() {
        screen = Screen.PAUSED;
        // Set here rather than queued for the simulation thread, and it has to be:
        // a paused engine does not step, so the task that would unpause it never
        // runs and the game never starts again. The flag takes no part in the
        // checksum -- pausing changes when frames happen, not what is in them.
        host.paused(true);
        var rows = new java.util.ArrayList<StoneMenu.Row>();
        // What he stopped in the middle of. Read off the game's own status line
        // rather than counted here, so a game that says nothing shows nothing —
        // and parsed from the snapshot rather than taken from the panel, which
        // has been put away by the time a menu is up.
        var snapshot = host.snapshot();
        for (var part : snapshot == null ? List.<HudPart>of() : parts) {
            for (var fact : part.pauseFacts(snapshot)) {
                rows.add(new StoneMenu.Words(fact[0], fact[1]));
            }
        }
        rows.add(new StoneMenu.Action(say("Resume"), this::resumeGame));
        rows.add(new StoneMenu.Action(say("Settings"), () -> showSettingsMenu(Screen.PAUSED)));
        for (var row : shell.pauseRows()) {
            rows.add(new StoneMenu.Action(row.label(), () -> takePauseRow(row), false, row.picture()));
        }
        // Marked, and asked about. They are the things on any of these screens
        // that cost him something he cannot get back.
        rows.add(new StoneMenu.Action(say("Quit to the main menu"),
                () -> confirmQuit("QUIT TO THE MAIN MENU?", host::quitToTheMainMenu), true));
        rows.add(new StoneMenu.Action(say("Quit to the desktop"),
                () -> confirmQuit("QUIT TO THE DESKTOP?", host::quitToTheDesktop), true));
        menu.show(say("PAUSED"), "", rows, say("ESC resume"), "", true);
    }

    /** A game's row on the pause menu taken: its own work, then its question if it has one, then back. */
    private void takePauseRow(Shell.Option row) {
        if (row.taken() != null) {
            row.taken().run();
        }
        if (row.next() != null) {
            showQuestion(row.next(), this::showPauseMenu, this::showPauseMenu);
        } else {
            showPauseMenu();
        }
    }

    /**
     * Ask before throwing a run away.
     *
     * <p>A second screen rather than a dialog over the first: this client has no
     * dialogs, and a menu that replaces a menu is the same mechanism doing the
     * same job. The safe answer is the one already lit.
     */
    private void confirmQuit(String question, Runnable quit) {
        menu.show(say(question), say("everything on this floor is lost"), java.util.List.of(
                        new StoneMenu.Action(say("Keep playing"), this::showPauseMenu),
                        new StoneMenu.Action(say("Quit"), quit, true)),
                say("ESC keep playing"), "", true);
    }


    /**
     * What the client calls itself, for the corner of the front menu.
     */
    private String version() {
        var version = getClass().getPackage().getImplementationVersion();
        return version == null ? "" : "v" + version;
    }

    /**
     * The menu, built with whatever lettering the game asked for.
     *
     * <p>Rebuilt rather than resized when the window changes, because everything
     * in it is laid out from the screen it was built against — see
     * {@link #rebuild}.
     */
    private StoneMenu buildMenu(float width, float height) {
        var style = visuals.getMenuStyle();
        var built = new StoneMenu(craft(), fonts.apply(style.titleFont()),
                fonts.apply(style.rowFont()), guiNode, width, height);
        built.placeAt(shell.menuSide(), shell.menuMargin());
        return built;
    }

    /** One of the client's fixed words, as the game says it — see {@link Shell#word}. */
    private String say(String english) {
        return shell.wordFor(english);
    }

    void resumeGame() {
        host.paused(false);
        menu.hide();
        screen = Screen.PLAYING;
    }

    // ---- settings ----

    /** The anti-aliasing a player may choose: none, or that many samples a pixel. */
    private static final int[] SAMPLES = {0, 2, 4};

    void showSettingsMenu(Screen returnTo) {
        settingsReturn = returnTo;
        screen = Screen.SETTINGS;
        var rows = new java.util.ArrayList<StoneMenu.Row>();
        rows.add(new StoneMenu.Choice(say("Fullscreen"), java.util.List.of(say("NO"), say("YES")),
                () -> preferences.flag("fullscreen", false) ? 1 : 0,
                to -> {
                    preferences.set("fullscreen", to == 1);
                    windowDisplay.fillTheScreen(to == 1); // seen at once, like a volume is heard
                }));
        // The real ones, from the card, rather than three the client invented —
        // a player who picks a size his monitor cannot show gets a black screen.
        var sizes = windowDisplay.screenSizes();
        rows.add(new StoneMenu.Opens(say("Size"),
                sizes.stream().map(size -> size.shown().replace("MONITOR", say("MONITOR"))).toList(),
                () -> WindowDisplay.indexOfSize(sizes, preferences.number("width", 0),
                        preferences.number("height", 0)),
                to -> {
                    preferences.set("width", sizes.get(to).width());
                    preferences.set("height", sizes.get(to).height());
                    windowDisplay.applyDisplaySettings();
                    menu.refresh(); // the row says what it now is
                }));
        // Taken by the window only as it is made, so it is kept for the next launch and said so below.
        rows.add(new StoneMenu.Choice(say("Anti-aliasing"), java.util.List.of(say("OFF"), "2×", "4×"),
                () -> indexOf(SAMPLES, preferences.number("samples", 0)),
                to -> preferences.set("samples", SAMPLES[to])));
        for (var row : shell.settingsRows()) {
            rows.add(new StoneMenu.Choice(row.word(), row.choices(),
                    () -> Math.clamp(preferences.number(row.key(), row.fallback()), 0, row.choices().size() - 1),
                    to -> {
                        preferences.set(row.key(), to);
                        row.chosen().accept(to);
                    }));
        }
        rows.add(new StoneMenu.Choice(say("Camera shake"), java.util.List.of(say("Full"), say("Half"), say("Off")),
                () -> Math.clamp(preferences.number("shake", 0), 0, PlayerChoices.SHAKES.length - 1),
                to -> {
                    preferences.set("shake", to);
                    choices.applyShake();
                }));
        rows.add(volumeRow(say("Volume"), "volume", 100));
        // Only offered by a game that has any: this client draws three others and
        // a knob for a channel with nothing on it is a dead button.
        if (!visuals.getSounds().isEmpty()) {
            rows.add(volumeRow(say("Effects"), "volEffects", 100));
            rows.add(volumeRow(say("Ambience"), "volAmbience", 70));
            rows.add(volumeRow(say("Voice"), "volVoice", 100));
            rows.add(volumeRow(say("Music"), "volMusic", 60));
            var tracks = visuals.getSounds().musicCues();
            if (tracks.size() > 1 && !host.musicByGame()) {
                // A list that drops open, not cells side by side: a track is
                // called "The Sentinel", and three of those across one row is
                // three names too small to read.
                rows.add(new StoneMenu.Opens(say("Track"),
                        tracks.stream().map(SoundBank.Cue::shown).toList(),
                        () -> Math.clamp(preferences.number("musicTrack", 0), 0, tracks.size() - 1),
                        to -> {
                            preferences.set("musicTrack", to);
                            choices.playChosenMusic();
                        }));
            }
        }
        // Every key the game uses and every control of the client's own, each in the game's words; one taken, the
        // next key pressed is its key.
        var keys = KeyBindings.keyLines(hotkeys, this::say);
        if (!keys.isEmpty()) {
            var actions = KeyBindings.rebindable(hotkeys);
            rows.add(new StoneMenu.Opens(say("Keys"), keys, () -> 0, to -> {
                if (to < actions.size()) {
                    keyBindings.await(actions.get(to));
                }
            }));
        }
        // The two buttons, in sockets rather than as two more rows: they are not
        // settings, they are what happens to the settings.
        rows.add(new StoneMenu.Buttons("", say("Save"), () -> {
            preferences.save();
            menu.refresh();
        }, say("Cancel"), this::undoSettings,
                preferences.number("samples", 0) != settings.get().getSamples() ? say("anti-aliasing from the next launch")
                        : preferences.dirty() ? say("unsaved changes") : ""));
        menu.show(say("SETTINGS"), "", rows,
                say("UP DOWN choose    LEFT RIGHT change    ESC back"), "",
                settingsReturn == Screen.PAUSED);
    }

    /**
     * One knob, worded and wired the same way as the rest.
     *
     * <p>Applied as it moves, because a volume has to be heard to be chosen, and
     * put back by Cancel — which is the promise the two buttons underneath make.
     */
    private StoneMenu.Row volumeRow(String word, String key, int fallback) {
        return new StoneMenu.Level(word, () -> preferences.number(key, fallback),
                to -> {
                    preferences.set(key, to);
                    choices.applyVolume();
                }, 5);
    }

    /**
     * Throw away what has not been saved, and put back what was already applied.
     *
     * <p>Everything on this screen takes effect while it is being chosen, so
     * cancelling is not only forgetting: the volume has to come back down and the
     * window has to go back to the shape it was.
     */
    private void undoSettings() {
        var pending = preferences.pending();
        preferences.cancel();
        for (var row : shell.settingsRows()) {
            if (pending.contains(row.key())) {
                row.chosen().accept(preferences.number(row.key(), row.fallback())); // the game told its old choice
            }
        }
        if (pending.contains("fullscreen")) {
            windowDisplay.fillTheScreen(preferences.flag("fullscreen", false));
        }
        if (pending.contains("width") || pending.contains("height")) {
            windowDisplay.applyDisplaySettings();
            menu.refresh(); // the row says what it now is
        }
        choices.applyVolume();
        choices.applyShake();
        if (pending.contains("musicTrack")) {
            choices.playChosenMusic();
        }
        leaveSettings();
    }

    private void leaveSettings() {
        if (settingsReturn == Screen.PAUSED) {
            showPauseMenu();
        } else {
            showMainMenu();
        }
    }

    private static int indexOf(int[] values, int value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == value) {
                return i;
            }
        }
        return 0;
    }

    /** A key on a menu: the pan keys and the arrows walk it, Enter and Space take, Escape goes back. */
    void key(int code, KeyMap.Key key) {
        if (keyBindings.capturing()) {
            if (keyBindings.capture(key)) {
                showSettingsMenu(settingsReturn); // the keys page again, with the key on it
            }
            return;
        }
        var control = controls.controlOn(key);
        if (control == KeyMap.Control.FULLSCREEN) {
            windowDisplay.toggleFullscreen();
            return;
        }
        if (menu.isVisible()) {
            Runnable step = code == KeyInput.KEY_UP || control == KeyMap.Control.PAN_UP ? menu::up
                    : code == KeyInput.KEY_DOWN || control == KeyMap.Control.PAN_DOWN ? menu::down
                    : code == KeyInput.KEY_LEFT || control == KeyMap.Control.PAN_LEFT ? menu::left
                    : code == KeyInput.KEY_RIGHT || control == KeyMap.Control.PAN_RIGHT ? menu::right : null;
            if (step != null) {
                step.run();
                noises.get().moment("menu_hover", timer.getTimeInSeconds());
                return;
            }
            if (code == KeyInput.KEY_RETURN || code == KeyInput.KEY_NUMPADENTER || code == KeyInput.KEY_SPACE) {
                if (menu.enter()) {
                    noises.get().moment("menu_click", timer.getTimeInSeconds());
                }
                return;
            }
        }
        if (code != KeyInput.KEY_ESCAPE) {
            return;
        }
        switch (screen) {
            case PAUSED -> {
                if (!menu.escape()) {
                    resumeGame();
                }
            }
            case SETTINGS -> {
                // The open list first, then the screen. Escaping the screen keeps what he has seen applied, as
                // closing the window does; Cancel is the one way back.
                if (!menu.escape()) {
                    preferences.keep();
                    leaveSettings();
                }
            }
            case PLAYING, MENU -> {
            }
        }
    }
}
