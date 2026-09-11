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
import uz.duke.dungeon.loot.LootTable;
import uz.duke.dungeon.power.PowerChoice;
import uz.duke.dungeon.skill.SkillBook;
import uz.duke.dungeon.skill.Skills;
import uz.duke.game.DukeGame;
import uz.duke.game.GamePlayer;

/**
 * The run loop: one dungeon at a time, and a fresh one the moment the hero dies.
 *
 * <p>Duke Dungeon is a roguelike in the oldest sense — there is no saving and no
 * carrying anything forward. A run is the stretch between spawning at full health
 * and its ending, and it can end two ways: on a floor, or at the bottom of the
 * last one. Either way the world is torn down and rebuilt from a new seed and the
 * hero starts over with nothing but full health, because progress is not meant to
 * survive an ending of either sort.
 *
 * <p>That there are two is recent and is the shape of the game rather than a
 * detail of this class. The descent used to have no bottom: floors went down for
 * ever, each a little harder, and the only question one could ask was how much
 * further. It stops at the floor the last boss stands on — see {@code Bosses} in
 * the settings file, which is both who they are and how many floors there are.
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

    /**
     * How the run is going right now.
     *
     * <p>{@code WON} is the newest and the one that changes what this game is. A
     * descent with no bottom is a scoreboard: you go down until you stop, and the
     * only question a floor asks is how much further. A descent with a last floor
     * on it can be finished, and then every floor before it is on the way
     * somewhere — which is the difference between a run and a session.
     */
    public enum State {
        RUNNING,
        DEAD,
        WON
    }

    private final GamePlayer heroPlayer;
    private final GamePlayer dungeonPlayer;
    private final DungeonSettings settings;

    private final HeroProgress progress;
    private final PowerChoice powers;
    private final LootTable drops;

    private long seed;
    private State state = State.RUNNING;
    private ObjectId heroId;
    private ObjectId bossId;
    private int depth = 1;
    /** When the run ended, whether it was lost or won. */
    private int endedFrame;
    /** When the floor closes behind him, or 0 while the boss is still alive. */
    private int descendAtFrame;

    /** The themes the file described, and how this floor is drawn from them. */
    private final uz.duke.dungeon.content.Themes themes;
    private String look;
    private int runCount; // how many times a new dungeon has been generated after a death

    /** The standing orders his player has given; only the panel reads them. */
    private final uz.duke.dungeon.ai.Orders orders;

    public DungeonRun(GamePlayer heroPlayer, GamePlayer dungeonPlayer, long seed,
            DungeonSettings settings, HeroProgress progress, PowerChoice powers,
            LootTable drops) {
        this(heroPlayer, dungeonPlayer, seed, settings, progress, powers, drops,
                new uz.duke.dungeon.ai.Orders());
    }

    public DungeonRun(GamePlayer heroPlayer, GamePlayer dungeonPlayer, long seed,
            DungeonSettings settings, HeroProgress progress, PowerChoice powers,
            LootTable drops, uz.duke.dungeon.ai.Orders orders) {
        this.orders = orders;
        this.heroPlayer = heroPlayer;
        this.dungeonPlayer = dungeonPlayer;
        this.seed = seed;
        this.settings = settings;
        this.progress = progress;
        this.powers = powers;
        this.drops = drops;
        this.themes = settings.themes();
        this.look = lookOfThisFloor();
    }

    /**
     * How this floor is drawn, as the two names the client is told.
     *
     * <p>Looks and nothing else. It is worked out from the seed and the depth --
     * both of which the run already has -- with a generator of its own, so asking
     * what a floor looks like cannot move the world's dice by a step. Held rather
     * than recomputed because it is asked for every frame and settled once a floor.
     */
    private String lookOfThisFloor() {
        var chosen = themes.pick(seed, depth);
        return chosen == null ? null : chosen.asStatus();
    }

    /**
     * Put the first floor in the world.
     *
     * <p>Goes through the same placement every later floor does, so the one the
     * player always sees and the ones he rarely reaches cannot drift apart.
     */
    public void openOn(DukeGame game, GeneratedDungeon floor) {
        look = lookOfThisFloor();
        var placed = Spawner.place(game, heroPlayer, dungeonPlayer, floor, settings, depth, drops);
        heroId = placed.hero().getId();
        bossId = placed.boss() == null ? null : placed.boss().getId();
    }

    /** Called every logic frame on the simulation thread. */
    public void tick(DukeGame game) {
        switch (state) {
            case RUNNING -> whileRunning(game);
            case DEAD -> whileDead(game);
            case WON -> whileWon(game);
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
            endedFrame = logic.getFrame();
            game.setBanner("lost|" + settings.diedWord());
            return;
        }
        if (bossId != null && logic.findObject(bossId) == null && descendAtFrame == 0) {
            // Nothing below this one: the last boss is the end of the game rather
            // than the door to the next floor. The banner is the whole of what
            // says so, and it is the only thing in this run loop that is not a
            // beginning of something.
            if (settings.finalDepth() > 0 && depth >= settings.finalDepth()) {
                state = State.WON;
                endedFrame = logic.getFrame();
                game.setBanner("won|" + settings.wonWord());
                return;
            }
            // The floor is finished, but not left yet — see below.
            descendAtFrame = logic.getFrame() + settings.descendDelayFrames();
            game.setBanner("depth|" + settings.nextDepthWord(depth + 1));
        }
        if (descendAtFrame > 0 && logic.getFrame() >= descendAtFrame) {
            depth++;
            descend(game);
            game.setBanner("");
        }
        showStatus(game);
    }

    /**
     * What the hero's panel shows: where he is and what he has become.
     *
     * <p>Goes through the snapshot's status channel, which the engine carries and
     * never reads — depth and levels are this game's arithmetic and the engine has
     * no name for either. See {@link HeroStatus} for what is in the line.
     */
    /**
     * What the bar says this frame, which is whatever the player has picked out.
     *
     * <p>Three answers and they are tried in this order, which is the order of
     * what is most specific. His own is asked first and asked before the creature
     * is looked at at all: a dying hero is still the hero the bar is about, and
     * the run has a screen of its own for what happens next.
     */
    private void showStatus(DukeGame game) {
        var picked = orders.watchedBy(heroPlayer.getIndex());
        var creature = picked == null ? null : game.getLogic().findObject(picked);
        boolean his = creature != null && creature.getPlayerIndex() == heroPlayer.getIndex();
        if (his && Skills.heroOf(game.getLogic(), heroPlayer.getIndex()) == creature) {
            game.setStatus(HeroStatus.of(Skills.heroOf(game.getLogic(), heroPlayer.getIndex()),
                    progress, depth, settings, powers, game.getLogic().getFrame(), look,
                    orders.isHolding(heroPlayer.getIndex())));
            return;
        }
        if (creature != null && !creature.isEffectivelyDead()) {
            // Somebody else's creature, or one of his that is not the hero: the
            // card describes it, and the buttons are live only if he could give it
            // an order.
            game.setStatus(HeroStatus.creature(creature, depth, settings, look, his));
            return;
        }
        // Nothing selected, or what was selected has died: the bar keeps the floor
        // and loses the creature. A panel describing a corpse until the player
        // thinks to click somewhere is a panel that looks broken.
        game.setStatus(HeroStatus.nothing(depth, settings,
                progress.getLoot().noteAt(game.getLogic().getFrame()), look));
    }

    /**
     * Why the floor does not close the instant the boss falls.
     *
     * <p>Two reasons, and the first is a bug the second would have hidden: the
     * boss leaves something behind, and rebuilding the world in the same frame
     * takes it away again before anyone could walk to it. Beyond that, being
     * moved somewhere else the instant a fight ends reads as a glitch — a floor
     * wants a moment to have been finished in.
     */
    private void whileDead(DukeGame game) {
        if (game.getLogic().getFrame() - endedFrame < settings.respawnDelayFrames()) {
            return;
        }
        begin(game);
    }

    /**
     * Having finished it: the same clearing away as a death, after a longer look
     * at the word.
     *
     * <p>The two are one act with two names, which is worth saying because it
     * would be easy to think a win deserves machinery of its own. It does not: a
     * run that has ended is a run that has ended, and everything the hero earned
     * belonged to it. What a win gets that a death does not is time — long enough
     * to have been a win rather than an interruption.
     */
    private void whileWon(DukeGame game) {
        if (game.getLogic().getFrame() - endedFrame < settings.victoryFrames()) {
            return;
        }
        begin(game);
    }

    /** A fresh run: the first floor, a hero with nothing, and the banner cleared. */
    private void begin(DukeGame game) {
        // An ending is the end of everything, not just of this floor.
        depth = 1;
        descendAtFrame = 0;
        progress.reset();
        // It takes everything, the cards included. Told rather than inferred, for
        // the same reason progression is: descending replaces the hero too, and
        // there he keeps them.
        powers.reset();
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
    /**
     * The next floor's ground: what is stone, how high each cell stands, and how
     * far apart two storeys are.
     *
     * <p>All three arrive together because they are one map. A grid built from
     * the walls alone would lay the new floor out flat and leave the hero walking
     * through the storeys of the last one.
     */
    private static uz.duke.core.pathfind.PathGrid terrainOf(
            uz.duke.dungeon.gen.GeneratedDungeon floor, DungeonSettings settings) {
        var grid = MapLoader.fromText(floor.asciiMap());
        MapLoader.levels(grid, floor.levelMap());
        grid.setLevelHeight(settings.storeyHeight());
        return grid;
    }

    private void descend(DukeGame game) {
        descendAtFrame = 0;
        seed = DungeonGenerator.nextSeed(seed);
        var floor = DungeonGenerator.generate(seed, settings, depth);

        var logic = game.getLogic();
        logic.clearWorld();
        game.applyMapTerrain(terrainOf(floor, settings));

        var placed = Spawner.place(game, heroPlayer, dungeonPlayer, floor, settings, depth, drops);
        heroId = placed.hero().getId();
        bossId = placed.boss() == null ? null : placed.boss().getId();
        look = lookOfThisFloor();
        progress.carryOver(game, placed.hero());
        powers.carryOver(placed.hero());
    }

    /**
     * Him, out of everything his player owns.
     *
     * <p>The template check is not redundant beside the player check: his arrows
     * are his too, and one of them is not the hero. Which template that is comes
     * out of the file, because there is more than one hero now.
     */
    private GameObject findHero(DukeGame game) {
        for (var object : game.getLogic().getObjects()) {
            if (object.getPlayerIndex() == heroPlayer.getIndex()
                    && object.getTemplate().getName().equals(settings.playedHero())) {
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
