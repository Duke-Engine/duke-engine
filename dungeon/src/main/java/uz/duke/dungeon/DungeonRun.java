package uz.duke.dungeon;

import uz.duke.core.math.Coord3D;
import uz.duke.core.pathfind.MapLoader;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ObjectId;
import uz.duke.core.thing.ThingTemplate;
import uz.duke.game.DukeGame;
import uz.duke.game.GamePlayer;

/**
 * The run loop: one dungeon at a time, and a fresh one the moment the hero dies.
 *
 * <p>Duke Dungeon is a roguelike in the oldest sense — there is no saving and no
 * carrying anything forward. A run is the stretch between spawning at full health
 * and dying; when it ends the world is torn down and rebuilt from a new seed, and
 * the hero starts over with nothing but full health. Progress is not meant to
 * survive death, so none is kept.
 *
 * <p>This runs as a per-frame tick on the simulation thread, so it must stay
 * deterministic like everything else there: it reads the frame counter, never the
 * clock, and the seed of each new run is drawn from the last by the same
 * {@link DeterministicRng} chain. Given one starting seed, the entire sequence of
 * dungeons a session plays is fixed.
 *
 * <p>Rebuilding in place reuses the engine's save/load seam — {@code clearWorld}
 * then respawn — which is safe from a tick because the frame's own object updates
 * and reaping have already run by the time the callback fires. The players are
 * left untouched across runs, so ownership stays valid; only the objects and the
 * terrain are replaced.
 */
public final class DungeonRun {

    /** How the run is going right now. */
    public enum State {
        RUNNING,
        DEAD
    }

    /** A short pause on the death screen before the next dungeon appears. */
    static final int RESPAWN_DELAY_FRAMES = 60; // ~2 seconds at 30 Hz

    private final GamePlayer heroPlayer;
    private final GamePlayer dungeonPlayer;

    private long seed;
    private State state = State.RUNNING;
    private ObjectId heroId;
    private int deathFrame;
    private int runCount; // how many times a new dungeon has been generated after a death

    DungeonRun(GamePlayer heroPlayer, GamePlayer dungeonPlayer, long seed) {
        this.heroPlayer = heroPlayer;
        this.dungeonPlayer = dungeonPlayer;
        this.seed = seed;
    }

    /** Called every logic frame on the simulation thread. */
    void tick(DukeGame game) {
        switch (state) {
            case RUNNING -> whileRunning(game);
            case DEAD -> whileDead(game);
        }
    }

    private void whileRunning(DukeGame game) {
        var logic = game.getLogic();
        // Learn the hero the first time we run, then track him by id.
        if (heroId == null) {
            var hero = findHero(game);
            if (hero != null) {
                heroId = hero.getId();
            }
        }
        var hero = heroId == null ? null : logic.findObject(heroId);
        boolean dead = hero == null || hero.getBody().getHealth() <= 0f;
        if (dead) {
            state = State.DEAD;
            deathFrame = logic.getFrame();
            game.setBanner("You died");
        }
    }

    private void whileDead(DukeGame game) {
        if (game.getLogic().getFrame() - deathFrame < RESPAWN_DELAY_FRAMES) {
            return;
        }
        seed = DeterministicRng.advance(seed); // the next run is a different dungeon
        regenerate(game, DungeonGenerator.generate(seed));
        runCount++;
        state = State.RUNNING;
        game.setBanner("");
    }

    /** Tear the world down and lay out a freshly generated dungeon in its place. */
    private void regenerate(DukeGame game, GeneratedDungeon dungeon) {
        var logic = game.getLogic();
        logic.clearWorld();
        game.applyMapTerrain(MapLoader.fromText(dungeon.asciiMap()));

        ThingTemplate heroTemplate = logic.getThingFactory().findTemplate("Hero");
        ThingTemplate skeletonTemplate = logic.getThingFactory().findTemplate("Skeleton");

        var hero = logic.spawn(heroTemplate, world(dungeon.hero()), heroPlayer.getIndex());
        heroId = hero.getId(); // the new hero, at full health straight from the template
        for (var placement : dungeon.skeletons()) {
            logic.spawn(skeletonTemplate, world(placement), dungeonPlayer.getIndex());
        }
    }

    private static Coord3D world(GeneratedDungeon.Placement placement) {
        return new Coord3D(placement.x(), placement.y(), 0f);
    }

    private GameObject findHero(DukeGame game) {
        for (var object : game.getLogic().getObjects()) {
            if (object.getPlayerIndex() == heroPlayer.getIndex()
                    && object.getTemplate().getName().equals("Hero")) {
                return object;
            }
        }
        return null;
    }

    // ---- observation (for tests) ----

    State getState() {
        return state;
    }

    /** How many new dungeons this session has generated after a death (0 at first). */
    int getRunCount() {
        return runCount;
    }
}
