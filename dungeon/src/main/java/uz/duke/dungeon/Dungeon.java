package uz.duke.dungeon;

import java.awt.Color;
import uz.duke.dungeon.ai.HeroBrain;
import uz.duke.dungeon.ai.SkeletonBrain;
import uz.duke.dungeon.content.Content;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.gen.DungeonGenerator;
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

    /** A game and the run loop that keeps it going. */
    public record Session(DukeGame game, DungeonRun run) {
    }

    /**
     * The game's world on a caller-supplied map, with nothing placed in it yet.
     *
     * <p>Pulled out so that the generated dungeon and a test's purpose-built arena
     * are the same game — a combat test that wired up its own creatures would be
     * testing a world nobody plays.
     */
    public static Arena world(String asciiMap, DungeonSettings settings) {
        var game = DukeGame.create("Duke Dungeon")
                .subtitle("a different dungeon every run")
                .customModules(factory -> {
                    ScriptModule.registerScript(factory, "HeroBrain", () -> new HeroBrain(settings));
                    ScriptModule.registerScript(factory, "SkeletonBrain",
                            () -> new SkeletonBrain(settings));
                })
                .loadUnits(Content.read(Content.CREATURES))
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

    /** Build the game and expose its run loop (the entry point tests build on). */
    public static Session newSession(long seed) {
        return newSession(seed, DungeonSettings.load());
    }

    /** The same, on settings the caller supplies — the seam for testing a re-tuned game. */
    public static Session newSession(long seed, DungeonSettings settings) {
        var dungeon = DungeonGenerator.generate(seed, settings);
        var arena = world(dungeon.asciiMap(), settings);
        var game = arena.game();

        game.spawn("Hero", arena.hero(), dungeon.hero().x(), dungeon.hero().y());
        for (var skeleton : dungeon.skeletons()) {
            game.spawn("Skeleton", arena.dungeon(), skeleton.x(), skeleton.y());
        }

        var run = new DungeonRun(arena.hero(), arena.dungeon(), seed, settings);
        game.onTick(run::tick);
        return new Session(game, run);
    }
}
