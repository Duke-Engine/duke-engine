package uz.duke.dungeon;

import java.awt.Color;
import uz.duke.game.DukeGame;

/**
 * Duke Dungeon — the first game written on this engine.
 *
 * <p>One room, one hero, three skeletons, and nothing else: no generated levels,
 * no models, no sound. Everything on screen is a coloured shape. The point of
 * this step is to find out whether the engine can be <em>played</em>, and the
 * quickest way to learn that is to strip away everything that could hide it.
 *
 * <p>The whole game is data and orders. It defines what a hero and a skeleton
 * are in INI, lays out a room in text, and then sends two commands the engine
 * already understands. It adds nothing to the engine, which is the test: if a
 * game needs new engine features to exist, the engine was not finished.
 */
public final class Dungeon {

    private Dungeon() {
    }

    /**
     * A hand-drawn room: {@code #} is stone, everything else is floor. The two
     * pillars are there to be walked around — the engine's pathfinder has to
     * earn its place even in a single room.
     */
    private static final String ROOM = """
            ########################################
            #......................................#
            #......................................#
            #......................................#
            #......................................#
            #......................................#
            #......................................#
            #......................................#
            #......................................#
            #..........##............##............#
            #..........##............##............#
            #......................................#
            #......................................#
            #......................................#
            #......................................#
            #......................................#
            #......................................#
            #......................................#
            #......................................#
            #......................................#
            #......................................#
            #......................................#
            #......................................#
            ########################################
            """;

    /**
     * What lives in the dungeon.
     *
     * <p>Two things are worth knowing here. The hero sees the whole room, so
     * nothing is hidden by fog while the game is this small. And the skeletons
     * carry a locomotor even though their speed is zero: to the engine, a shaped
     * thing that cannot move is scenery and gets baked into the navigation grid —
     * skeletons would become walls, and the hero could never reach them. The
     * module is what makes them creatures rather than furniture.
     */
    static final String CREATURES = """
            Object Hero
              DisplayName = Hero
              KindOf = INFANTRY SELECTABLE CAN_ATTACK
              Geometry = CYLINDER
              GeometryMajorRadius = 4
              GeometryHeight = 12
              VisionRange = 600
              Body = ActiveBody Tag
                MaxHealth = 200
              End
              Update = MoveUpdate Tag
                Speed = 45
                TurnRate = 720
              End
              Update = WeaponUpdate Tag
                Damage = 14
                AttackRange = 8
                ReloadFrames = 15
              End
            End
            Object Skeleton
              DisplayName = Skeleton
              KindOf = INFANTRY SELECTABLE CAN_ATTACK
              Geometry = CYLINDER
              GeometryMajorRadius = 4
              GeometryHeight = 12
              VisionRange = 90
              Body = ActiveBody Tag
                MaxHealth = 60
              End
              Update = MoveUpdate Tag
                Speed = 0
              End
              Update = WeaponUpdate Tag
                Damage = 4
                AttackRange = 10
                ReloadFrames = 30
              End
            End
            """;

    static final Color HERO_COLOUR = new Color(90, 170, 255);
    static final Color SKELETON_COLOUR = new Color(225, 95, 80);

    /**
     * The hand-drawn room — one hero, three fixed skeletons, no run loop. Kept as
     * the plainest proof the engine can be played: a known world with known
     * answers. The real game is {@link #create(long)}.
     */
    public static DukeGame create() {
        var game = DukeGame.create("Duke Dungeon")
                .subtitle("one room, one hero, three skeletons")
                .loadUnits(CREATURES)
                .mapFromText(ROOM);

        var hero = game.addPlayer("Hero", HERO_COLOUR);
        var dungeon = game.addPlayer("Dungeon", SKELETON_COLOUR);
        game.enemies(hero, dungeon).localPlayer(hero);

        game.spawn("Hero", hero, 200f, 180f);

        // Placed by hand and far enough apart that they are fought one at a time.
        // A crowd would be a balance problem, and balance is not what this step
        // is trying to find out.
        game.spawn("Skeleton", dungeon, 90f, 60f)
                .spawn("Skeleton", dungeon, 200f, 50f)
                .spawn("Skeleton", dungeon, 310f, 60f);

        return game;
    }

    /** A game and the run loop that keeps it going — what a test needs to see both. */
    record Session(DukeGame game, DungeonRun run) {
    }

    /**
     * The real dungeon: a fresh layout drawn from {@code seed}, and a run loop
     * that generates the next one each time the hero dies. Same seed, same first
     * dungeon and same sequence of dungeons after it.
     */
    public static DukeGame create(long seed) {
        return newSession(seed).game();
    }

    /** Build the game and expose its run loop (the entry point tests build on). */
    static Session newSession(long seed) {
        var dungeon = DungeonGenerator.generate(seed);

        var game = DukeGame.create("Duke Dungeon")
                .subtitle("a different dungeon every run")
                .loadUnits(CREATURES)
                .mapFromText(dungeon.asciiMap());

        var heroPlayer = game.addPlayer("Hero", HERO_COLOUR);
        var dungeonPlayer = game.addPlayer("Dungeon", SKELETON_COLOUR);
        game.enemies(heroPlayer, dungeonPlayer).localPlayer(heroPlayer);

        game.spawn("Hero", heroPlayer, dungeon.hero().x(), dungeon.hero().y());
        for (var skeleton : dungeon.skeletons()) {
            game.spawn("Skeleton", dungeonPlayer, skeleton.x(), skeleton.y());
        }

        var run = new DungeonRun(heroPlayer, dungeonPlayer, seed);
        game.onTick(run::tick);
        return new Session(game, run);
    }
}
