package uz.duke.dungeon;

import java.awt.Color;
import uz.duke.dungeon.ai.HeroBrain;
import uz.duke.dungeon.ai.Orders;
import uz.duke.dungeon.ai.MonsterBrain;
import uz.duke.dungeon.combat.ArrowUpdate;
import uz.duke.dungeon.combat.FallingUpdate;
import uz.duke.dungeon.combat.Bow;
import uz.duke.dungeon.combat.EyesOnly;
import uz.duke.dungeon.combat.Swing;
import uz.duke.dungeon.content.Content;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.gen.DungeonGenerator;
import uz.duke.dungeon.level.GrowableBody;
import uz.duke.dungeon.level.HeroProgress;
import uz.duke.dungeon.loot.LootBag;
import uz.duke.dungeon.loot.LootTable;
import uz.duke.dungeon.loot.LootUpdate;
import uz.duke.dungeon.power.ChoosePower;
import uz.duke.dungeon.power.PowerBook;
import uz.duke.dungeon.power.PowerChoice;
import uz.duke.dungeon.skill.CastSkill;
import uz.duke.dungeon.skill.SkillBook;
import uz.duke.dungeon.skill.Skills;
import uz.duke.dungeon.run.DungeonRun;
import uz.duke.dungeon.run.Floors;
import uz.duke.dungeon.stage.Stage;
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

        game.spawn("Rogue", hero, 200f, 180f);

        // Placed by hand and far enough apart that they are fought one at a time.
        // A crowd would be a balance problem, and balance is not what this room
        // is trying to find out.
        game.spawn("Skeleton", dungeon, 90f, 60f)
                .spawn("Skeleton", dungeon, 200f, 50f)
                .spawn("Skeleton", dungeon, 310f, 60f);

        return game;
    }

    /** An empty world of the game's own making: its creatures, behaviour and sides. */
    /**
     * @param orders the standing orders the hero has been given -- held here
     *               because the command that sets one and the brain that obeys it
     *               have no other way to reach each other. See {@link Orders}
     */
    public record Arena(DukeGame game, GamePlayer hero, GamePlayer dungeon, Orders orders) {
    }

    /**
     * A game, the run loop that keeps it going, the hero's progression, and the
     * powers he is offered as he levels.
     */
    public record Session(DukeGame game, DungeonRun run, HeroProgress progress,
            PowerChoice powers, Orders orders) {
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
     * The same, for a caller with no interest in level-up powers — a fresh book,
     * empty and staying empty because nothing offers from it.
     */
    public static Arena world(String asciiMap, DungeonSettings settings, String creaturesIni) {
        return world(asciiMap, settings, creaturesIni,
                new PowerBook(settings.powerMinCooldownPercent()), new LootBag());
    }

    /**
     * The same, on a creature file the caller supplies — the seam for asking what
     * a re-tuned creature does, next to {@link #newSession(long, DungeonSettings)}
     * for re-tuned generation.
     */
    public static Arena world(String asciiMap, DungeonSettings settings, String creaturesIni,
            PowerBook powers, LootBag bag) {
        return world(asciiMap, null, settings, creaturesIni, powers, bag);
    }

    /**
     * The same, on a dungeon that has height in it.
     *
     * <p>{@code levelMap} is the second layer the generator draws — which storey
     * each cell stands on and where the stairs are. A hand-written arena passes
     * {@code null} and gets the flat world it drew.
     */
    public static Arena world(String asciiMap, String levelMap, DungeonSettings settings,
            String creaturesIni, PowerBook powers, LootBag bag) {
        var orders = new Orders();
        // No subtitle here: what this world is called depends on why it was built,
        // and only the caller knows — an endless descent, or one named stage. See
        // the two entry points below.
        var game = DukeGame.create("Duke Dungeon")
                .customModules(factory -> {
                    ScriptModule.registerScript(factory, "HeroBrain",
                            () -> new HeroBrain(settings, orders));
                    // One brain per kind, wired from the list the settings file
                    // names — so adding a monster is two blocks of INI and no Java.
                    for (var kind : settings.monsters()) {
                        ScriptModule.registerScript(factory, kind.brainTag(),
                                () -> new MonsterBrain(kind, settings));
                    }
                    // Hero and monsters alike need a body that can grow: levels
                    // raise his, depth raises theirs, and the engine's fixes its
                    // maximum when the unit is built.
                    factory.register("GrowableBody",
                            (owner, data) -> new GrowableBody(owner, (GrowableBody.Data) data),
                            GrowableBody::parseData);
                    // Which skills a hero has is not in his creature block — it is
                    // in dungeon.ini, under his template's name. So the block says
                    // only that he has some, and a second hero needs the same line
                    // and his own DungeonSkill blocks, and no code at all.
                    factory.register("SkillBook",
                            (owner, data) -> new SkillBook(owner,
                                    settings.skillsFor(owner.getTemplate().getName()), settings,
                                    powers),
                            SkillBook::parseData);
                    // An archer's shots become things in the world. The engine's
                    // weapon still aims and reloads; these two decide what
                    // happens between letting go and landing.
                    factory.register("Bow",
                            (owner, data) -> new Bow(owner, (Bow.Data) data, settings),
                            Bow::parseData);
                    // Stone stops his shots as well as his eyes.
                    factory.register("EyesOnly",
                            (owner, data) -> new EyesOnly(owner, settings), EyesOnly::parseData);
                    factory.register("ArrowUpdate",
                            (owner, data) -> new ArrowUpdate(owner, data, powers),
                            ArrowUpdate::parseData);
                    // A blast with a pause in the middle. The mark it leaves is a
                    // thing in the world like the arrow above, so the client draws
                    // the warning without being told anything special.
                    factory.register("FallingUpdate",
                            (owner, data) -> new FallingUpdate(owner, data, powers),
                            FallingUpdate::parseData);
                    // A monster's blow lands where it stands, as it always did.
                    // This is only how the brain finds out that it struck.
                    factory.register("Swing", Swing::new, Swing::parseData);
                    // What a dead monster leaves lying about. The chest is a
                    // creature like any other -- it is in the world, so the client
                    // draws it without being told anything special.
                    factory.register("LootUpdate",
                            (owner, data) -> new LootUpdate(owner, bag,
                                    settings.lootPickupRange(), settings.lootNoteFrames()),
                            LootUpdate::parseData);
                })
                .loadUnits(creaturesIni)
                .loadUnits(Content.read(Content.MONSTERS))
                .loadUnits(Content.read(Content.PROPS))
                .mapFromText(asciiMap);

        if (levelMap != null) {
            // The height layer over the map that was just read. The engine owns
            // what a storey means and what may be walked between two of them;
            // this only hands it the picture the generator drew.
            uz.duke.core.pathfind.MapLoader.levels(game.getTerrain(), levelMap);
            game.getTerrain().setLevelHeight(settings.storeyHeight());
        }

        var heroPlayer = game.addPlayer("Hero", HERO_COLOUR);
        var dungeonPlayer = game.addPlayer("Dungeon", SKELETON_COLOUR);
        game.enemies(heroPlayer, dungeonPlayer).localPlayer(heroPlayer);
        return new Arena(game, heroPlayer, dungeonPlayer, orders);
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

    /**
     * A stage: the one floor somebody built, and the boss on it is the end of the
     * game rather than a door down.
     *
     * <p>The same game in every other respect, and deliberately so — the stage was
     * cut from a generated floor, so handing it to the same assembly is what makes
     * "it plays exactly like the dungeon it came from" true by construction rather
     * than by care. What it swaps is where floors come from; see {@link Floors}.
     */
    public static DukeGame createStage(Stage stage, DungeonSettings settings) {
        return newStageSession(stage, settings).game();
    }

    /** Build the game and expose its run loop (the entry point tests build on). */
    public static Session newSession(long seed) {
        return newSession(seed, DungeonSettings.load());
    }

    /** The same, on settings the caller supplies — the seam for testing a re-tuned game. */
    public static Session newSession(long seed, DungeonSettings settings) {
        return open(DungeonGenerator.generate(seed, settings, 1), seed,
                Floors.generated(seed, settings), settings, "a different dungeon every run");
    }

    /** The stage's own session, for a test that wants at its run loop. */
    public static Session newStageSession(Stage stage, DungeonSettings settings) {
        var told = stage.name() == null || stage.name().isBlank() ? stage.id() : stage.name();
        var about = stage.description() == null || stage.description().isBlank()
                ? told : told + " — " + stage.description();
        return open(stage.floor(), stage.seed(), Floors.ofStage(stage), settings, about);
    }

    /**
     * Assemble the game around a first floor and a place for the next one to come
     * from.
     *
     * <p>One method for both kinds because everything below the floors is the same
     * game: the same creatures, the same hero, the same commands, the same loot
     * table drawn from the same seed. A second copy of this for stages would drift,
     * and what it drifted into would be a stage that no longer played like the
     * dungeon it was frozen from.
     *
     * @param seed what the run's own dice are wound to — the loot, the level-up
     *             cards and the floor's look. A stage carries the seed it was cut
     *             from so that it is the same run every time, not merely the same
     *             rooms
     */
    private static Session open(uz.duke.dungeon.gen.GeneratedDungeon floor, long seed,
            Floors floors, DungeonSettings settings, String subtitle) {
        // The book is built before the world because the hero's modules read it:
        // his skills ask it what they hit for, and his arrows what they give back.
        var book = new PowerBook(settings.powerMinCooldownPercent());
        // What he has put his levels into. Beside the power book and for the same
        // reason: a floor gives him a fresh body and a fresh SkillBook, so what he
        // has learnt has to live somewhere that outlives both.
        var learnt = new uz.duke.dungeon.skill.SkillRanks(settings.skillSpread());
        learnt.startWith(settings.skillsFor(settings.playedHero()));
        var bag = new LootBag();
        var arena = world(floor.asciiMap(), floor.levelMap(), settings,
                Content.read(Content.CREATURES), book, bag);
        var game = arena.game().subtitle(subtitle);

        // Told which creature is the hero and what he already wears: both are
        // per-hero and the file says them -- see DefaultHero and DungeonHero.
        var progress = new HeroProgress(arena.hero(), settings.levelling(),
                settings.levelUpBannerFrames(), bag, settings.playedHero(),
                settings.playedHeroLook().armourPercent());
        // Drawn from the run's seed as well, so a seed is the whole run: the same
        // one drops the same things off the same monsters.
        var drops = new LootTable(settings.loot(), seed, settings.lootDropPercent(),
                settings.lootBossDropPercent(), settings.lootValuePercentPerDepth());
        // Cards drawn from the run's own seed, so a seed is still a whole run:
        // the same one offers the same three at the same levels.
        var powers = new PowerChoice(book, settings.powers(), seed, settings.powerOfferCount());
        var run = new DungeonRun(arena.hero(), arena.dungeon(), floors, settings, progress, powers,
                drops, arena.orders(), learnt);

        // Q, W, E and R arrive as this game's own command, through the same queue
        // the standard orders use — so a keypress lands on a frame boundary and is
        // recorded, rather than reaching into the simulation from the input thread.
        game.onCommand(command -> {
            switch (command) {
                // The rank he has PUT INTO it, not the level he has reached. Four
                // slots that were all as strong as the hero are now four that
                // compete for his levels -- see SkillRanks.
                case CastSkill cast -> Skills.cast(game.getLogic(), cast,
                        learnt.rankOf(cast.key()));
                // And spending one of those levels, which is a click on the little
                // button beside a slot.
                case uz.duke.dungeon.skill.UpgradeSkill raise ->
                        Skills.raise(learnt, raise, progress.getLevel());
                // Picking a card is an order like any other: it lands on a frame
                // boundary rather than reaching in from whatever drew the screen.
                case ChoosePower choice -> powers.choose(choice.index(), choice.offerId(),
                        Skills.heroOf(game.getLogic(), choice.playerIndex()));
                // "Stand and pick no fights", which none of the engine's three
                // orders can say. See HoldGround.
                case uz.duke.dungeon.ai.HoldGround hold ->
                        arena.orders().hold(hold.playerIndex(), hold.stand());
                // "Go there and kill what you meet." See AttackMove.
                case uz.duke.dungeon.ai.AttackMove march ->
                        arena.orders().attackMove(march.playerIndex(), march.spot());
                // ★ The three plain orders call it off, and they CANNOT be heard
                // here: this handler is `onOtherCommand`, the engine's door for
                // commands it does not recognise, so a MoveTo is applied by rts
                // and never reaches it. Which is right -- they are the engine's
                // orders and it owns them. Where the errand hears about them is
                // HeroBrain, by noticing that its own walk has been taken off it;
                // see thePlayerHasSpokenSince.
                // Nothing in the world changes; the panel starts describing
                // something else. See Watching.
                case uz.duke.dungeon.run.Watching looking ->
                        arena.orders().watch(looking.playerIndex(), looking.unit());
                default -> {
                    // Not one of ours; rts has already said so.
                }
            }
        });

        // The first floor is laid out the same way every later one is, so the
        // deep floors nobody plays as often cannot drift from the first.
        game.onStart(started -> run.openOn(started, floor));
        game.onTick(run::tick);
        game.onTick(progress::tick);
        // After progression, which is what it watches: a level appearing is what
        // puts cards on the table.
        game.onTick(ignored -> powers.tick(progress.getLevel()));

        return new Session(game, run, progress, powers, arena.orders());
    }

}
