package uz.duke.dungeon;

import java.awt.Color;
import uz.duke.dungeon.ai.HeroBrain;
import uz.duke.dungeon.ai.MonsterBrain;
import uz.duke.dungeon.content.Content;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.gen.DungeonGenerator;
import uz.duke.dungeon.level.GrowableBody;
import uz.duke.dungeon.level.HeroProgress;
import uz.duke.dungeon.run.DungeonRun;
import uz.duke.game.DukeGame;
import uz.duke.game.GamePlayer;
import uz.duke.game.script.ScriptModule;

/**
 * Duke Dungeon — the first game written on this engine.
 *
 * <p>A hero, a dungeon full of skeletons, and no way to save: die and the whole
 * thing is generated again from a new seed. Everything on screen is a coloured
 * shape — no models, no textures, no sound — because the question this game exists
 * to answer is whether the engine can be <em>played</em>, and the quickest way to
 * learn that is to strip away everything that could hide the answer.
 *
 * <p>This class is only assembly. What the creatures are is data
 * ({@code creatures.ini}), how a dungeon is laid out is data ({@code dungeon.ini}),
 * how they behave is {@link uz.duke.dungeon.ai}, and when a run ends is
 * {@link DungeonRun}. Nothing here is added to the engine — the game is definitions
 * and orders the engine already understands, which is the real test: if a game
 * needs new engine features to exist, the engine was not finished.
 */
public final class Dungeon {

    static final Color HERO_COLOUR = new Color(90, 170, 255);
    static final Color SKELETON_COLOUR = new Color(225, 95, 80);

    private Dungeon() {
    }

    /**
     * The hand-drawn room — one hero, three fixed skeletons, no run loop and no
     * behaviour scripts. Kept as the plainest proof the engine can be played: a
     * known world with known answers, built from its own frozen data so that
     * tuning the game can never move it. The real game is {@link #create(long)}.
     */
    public static DukeGame create() {
        var game = DukeGame.create("Duke Dungeon")
                .subtitle("one room, one hero, three skeletons")
                .loadUnits(Content.read(Content.FIXTURE_CREATURES))
                .mapFromText(Content.read(Content.FIXTURE_ROOM));

        var hero = game.addPlayer("Hero", HERO_COLOUR);
        var dungeon = game.addPlayer("Dungeon", SKELETON_COLOUR);
        game.enemies(hero, dungeon).localPlayer(hero);

        game.spawn("Hero", hero, 200f, 180f);

        // Placed by hand and far enough apart that they are fought one at a time.
        // A crowd would be a balance problem, and balance is not what this room
        // is trying to find out.
        game.spawn("Skeleton", dungeon, 90f, 60f)
                .spawn("Skeleton", dungeon, 200f, 50f)
                .spawn("Skeleton", dungeon, 310f, 60f);

        return game;
    }

    /** An empty world of the game's own making: its creatures, behaviour and sides. */
    public record Arena(DukeGame game, GamePlayer hero, GamePlayer dungeon) {
    }

    /** A game, the run loop that keeps it going, and the hero's progression. */
    public record Session(DukeGame game, DungeonRun run, HeroProgress progress) {
    }

    /**
     * The game's world on a caller-supplied map, with nothing placed in it yet.
     *
     * <p>Pulled out so that the generated dungeon and a test's purpose-built arena
     * are the same game — a combat test that wired up its own creatures would be
     * testing a world nobody plays.
     */
    public static Arena world(String asciiMap, DungeonSettings settings) {
        return world(asciiMap, settings, Content.read(Content.CREATURES));
    }

    /**
     * The same, on a creature file the caller supplies — the seam for asking what
     * a re-tuned creature does, next to {@link #newSession(long, DungeonSettings)}
     * for re-tuned generation.
     */
    public static Arena world(String asciiMap, DungeonSettings settings, String creaturesIni) {
        var game = DukeGame.create("Duke Dungeon")
                .subtitle("a different dungeon every run")
                .customModules(factory -> {
                    ScriptModule.registerScript(factory, "HeroBrain", () -> new HeroBrain(settings));
                    // One brain per kind, wired from the list the settings file
                    // names — so adding a monster is two blocks of INI and no Java.
                    for (var kind : settings.monsters()) {
                        ScriptModule.registerScript(factory, kind.brainTag(),
                                () -> new MonsterBrain(kind));
                    }
                    // Hero and monsters alike need a body that can grow: levels
                    // raise his, depth raises theirs, and the engine's fixes its
                    // maximum when the unit is built.
                    factory.register("GrowableBody",
                            (owner, data) -> new GrowableBody(owner, (GrowableBody.Data) data),
                            GrowableBody::parseData);
                })
                .loadUnits(creaturesIni)
                .loadUnits(Content.read(Content.MONSTERS))
                .mapFromText(asciiMap);

        var heroPlayer = game.addPlayer("Hero", HERO_COLOUR);
        var dungeonPlayer = game.addPlayer("Dungeon", SKELETON_COLOUR);
        game.enemies(heroPlayer, dungeonPlayer).localPlayer(heroPlayer);
        return new Arena(game, heroPlayer, dungeonPlayer);
    }

    /**
     * The real dungeon: a fresh layout drawn from {@code seed}, and a run loop that
     * generates the next one each time the hero dies. Same seed, same first dungeon
     * and same sequence of dungeons after it.
     */
    public static DukeGame create(long seed) {
        return newSession(seed).game();
    }

    /** The same, on settings already loaded — so a caller can read them once. */
    public static DukeGame create(long seed, DungeonSettings settings) {
        return newSession(seed, settings).game();
    }

    /** Build the game and expose its run loop (the entry point tests build on). */
    public static Session newSession(long seed) {
        return newSession(seed, DungeonSettings.load());
    }

    /** The same, on settings the caller supplies — the seam for testing a re-tuned game. */
    public static Session newSession(long seed, DungeonSettings settings) {
        var floor = DungeonGenerator.generate(seed, settings, 1);
        var arena = world(floor.asciiMap(), settings);
        var game = arena.game();

        var progress = new HeroProgress(arena.hero(), settings.levelling(),
                settings.levelUpBannerFrames());
        var run = new DungeonRun(arena.hero(), arena.dungeon(), seed, settings, progress);

        // The first floor is laid out the same way every later one is, so the
        // deep floors nobody plays as often cannot drift from the first.
        game.onStart(started -> run.openOn(started, floor));
        game.onTick(run::tick);
        game.onTick(progress::tick);

        return new Session(game, run, progress);
    }
}
