package uz.duke.dungeon.run;

import uz.duke.core.math.Coord3D;
import uz.duke.core.pathfind.MapLoader;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ObjectId;
import uz.duke.core.thing.ThingTemplate;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.gen.DungeonGenerator;
import uz.duke.dungeon.gen.GeneratedDungeon;
import uz.duke.dungeon.level.HeroProgress;
import uz.duke.dungeon.skill.SkillBook;
import uz.duke.dungeon.skill.Skills;
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

    private final GamePlayer heroPlayer;
    private final GamePlayer dungeonPlayer;
    private final DungeonSettings settings;

    private final HeroProgress progress;

    private long seed;
    private State state = State.RUNNING;
    private ObjectId heroId;
    private ObjectId bossId;
    private int depth = 1;
    private int deathFrame;
    private int runCount; // how many times a new dungeon has been generated after a death

    public DungeonRun(GamePlayer heroPlayer, GamePlayer dungeonPlayer, long seed,
            DungeonSettings settings, HeroProgress progress) {
        this.heroPlayer = heroPlayer;
        this.dungeonPlayer = dungeonPlayer;
        this.seed = seed;
        this.settings = settings;
        this.progress = progress;
    }

    /**
     * Put the first floor in the world.
     *
     * <p>Goes through the same placement every later floor does, so the one the
     * player always sees and the ones he rarely reaches cannot drift apart.
     */
    public void openOn(DukeGame game, GeneratedDungeon floor) {
        var placed = Spawner.place(game, heroPlayer, dungeonPlayer, floor, settings, depth);
        heroId = placed.hero().getId();
        bossId = placed.boss() == null ? null : placed.boss().getId();
    }

    /** Called every logic frame on the simulation thread. */
    public void tick(DukeGame game) {
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
            return;
        }
        if (bossId != null && logic.findObject(bossId) == null) {
            // The floor is finished. Down one, keeping everything he has earned.
            depth++;
            descend(game);
            game.setBanner("Depth " + depth);
        }
        showStatus(game);
    }

    /**
     * The line the HUD shows: where he is and what he has become.
     *
     * <p>Goes through the snapshot's status channel, which the engine carries and
     * never reads — depth and levels are this game's arithmetic and the engine has
     * no name for either.
     */
    private void showStatus(DukeGame game) {
        var line = new StringBuilder("Depth %d    Level %d    xp %d/%d".formatted(
                depth, progress.getLevel(), progress.getExperienceIntoLevel(),
                progress.getExperienceForNextLevel()));
        var hero = Skills.heroOf(game.getLogic(), heroPlayer.getIndex());
        var book = hero == null ? null : hero.findModule(SkillBook.class);
        if (book != null && !book.getSkills().isEmpty()) {
            line.append("        ").append(Skills.bar(book, progress.getLevel()));
        }
        game.setStatus(line.toString());
    }

    private void whileDead(DukeGame game) {
        if (game.getLogic().getFrame() - deathFrame < settings.respawnDelayFrames()) {
            return;
        }
        // A death is the end of everything, not just of this floor.
        depth = 1;
        progress.reset();
        descend(game);
        runCount++;
        state = State.RUNNING;
        game.setBanner("");
    }

    /**
     * Down a floor: a new seed, a new layout, and tougher inhabitants — but the
     * same hero, still carrying what he has earned.
     *
     * <p>The distinction from a death is the whole point of depth, and it has to
     * be stated rather than inferred. Both replace the hero object, so anything
     * watching for a new hero to decide whether to reset would wipe his levels on
     * every floor: {@link HeroProgress} is told which of the two this is.
     */
    private void descend(DukeGame game) {
        seed = DungeonGenerator.nextSeed(seed);
        var floor = DungeonGenerator.generate(seed, settings, depth);

        var logic = game.getLogic();
        logic.clearWorld();
        game.applyMapTerrain(MapLoader.fromText(floor.asciiMap()));

        var placed = Spawner.place(game, heroPlayer, dungeonPlayer, floor, settings, depth);
        heroId = placed.hero().getId();
        bossId = placed.boss() == null ? null : placed.boss().getId();
        progress.carryOver(game, placed.hero());
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

    // ---- observation ----

    public State getState() {
        return state;
    }

    /** How many new dungeons this session has generated after a death (0 at first). */
    public int getRunCount() {
        return runCount;
    }

    /** Which floor the hero is on. One at the start of every run.  */
    public int getDepth() {
        return depth;
    }
}
