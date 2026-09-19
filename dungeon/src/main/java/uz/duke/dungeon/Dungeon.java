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
import uz.duke.dungeon.content.Hero;
import uz.duke.dungeon.content.Monster;
import uz.duke.dungeon.content.Projectile;
import uz.duke.dungeon.content.Prop;
import uz.duke.dungeon.gen.DungeonGenerator;
import uz.duke.dungeon.level.GrowableBody;
import uz.duke.dungeon.level.HeroAttributes;
import uz.duke.dungeon.level.HeroBuild;
import uz.duke.dungeon.level.HeroProgress;
import uz.duke.dungeon.level.Recovery;
import uz.duke.dungeon.loot.LootBag;
import uz.duke.dungeon.loot.LootTable;
import uz.duke.dungeon.loot.LootUpdate;
import uz.duke.dungeon.skill.CastSkill;
import uz.duke.dungeon.skill.MendingUpdate;
import uz.duke.dungeon.skill.SummoningUpdate;
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
 * ({@code data/units/}), how a dungeon is laid out is data ({@code data/maps/}),
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

    /** A game, the run loop that keeps it going, and the hero's progression. */
    public record Session(DukeGame game, DungeonRun run, HeroProgress progress,
            Orders orders) {
    }

    /**
     * The game's world on a caller-supplied map, with nothing placed in it yet.
     *
     * <p>Pulled out so that the generated dungeon and a test's purpose-built arena
     * are the same game — a combat test that wired up its own creatures would be
     * testing a world nobody plays.
     */
    public static Arena world(String asciiMap, DungeonSettings settings) {
        return world(asciiMap, settings, Content.units());
    }

    /** The same, for a caller with no interest in loot: an empty bag. */
    public static Arena world(String asciiMap, DungeonSettings settings, String creaturesIni) {
        return world(asciiMap, settings, creaturesIni, new LootBag());
    }

    /**
     * The same, on a creature file the caller supplies — the seam for asking what
     * a re-tuned creature does, next to {@link #newSession(long, DungeonSettings)}
     * for re-tuned generation.
     */
    public static Arena world(String asciiMap, DungeonSettings settings, String creaturesIni,
            LootBag bag) {
        return world(asciiMap, null, settings, creaturesIni, bag);
    }

    /**
     * The same, on a dungeon that has height in it.
     *
     * <p>{@code levelMap} is the second layer the generator draws — which storey
     * each cell stands on and where the stairs are. A hand-written arena passes
     * {@code null} and gets the flat world it drew.
     */
    public static Arena world(String asciiMap, String levelMap, DungeonSettings settings,
            String creaturesIni, LootBag bag) {
        var orders = new Orders();
        // No subtitle here: what this world is called depends on why it was built,
        // and only the caller knows — an endless descent, or one named stage. See
        // the two entry points below.
        var game = DukeGame.create("Duke Dungeon")
                .customModules(factory -> {
                    ScriptModule.registerScript(factory, "HeroBrain",
                            () -> new HeroBrain(settings, orders));
                    // One brain per kind, wired from the list the settings file
                    // names — so adding a monster is a file and no Java.
                    for (var kind : settings.monsters()) {
                        ScriptModule.registerScript(factory, kind.brainTag(),
                                () -> new MonsterBrain(kind, settings));
                    }
                    // Hero and monsters alike need a body that can grow: levels
                    // raise his, depth raises theirs, and the engine's fixes its
                    // maximum when the unit is built.
                    //
                    // A hero's is built with his first level's strength already in it,
                    // and so are his legs and his weapon below: the creature file holds
                    // what his attributes are added to, and a hero is the same hero
                    // wherever he is spawned. Anything that is not a hero is built
                    // exactly as its block says. See HeroBuild.
                    var rules = settings.attributeRules();
                    factory.register(GrowableBody.Data.class, (owner, data) -> new GrowableBody(owner,
                            HeroBuild.body(data, attributesOf(settings, owner), rules)));
                    factory.register(uz.duke.core.module.MoveUpdate.Data.class,
                            (owner, data) -> new uz.duke.core.module.MoveUpdate(owner,
                                    HeroBuild.legs(data, attributesOf(settings, owner), rules)));
                    factory.register(uz.duke.rts.module.WeaponUpdate.Data.class,
                            (owner, data) -> new uz.duke.rts.module.WeaponUpdate(owner,
                                    HeroBuild.weapon(data, attributesOf(settings, owner), rules)));
                    // Health coming back on its own, at the rate his block names --
                    // set by HeroProgress, as his mana is.
                    factory.register(Recovery.Data.class, (owner, data) -> new Recovery(owner));
                    // Which skills a unit has is the Skill blocks written inside its own:
                    // the SkillBook block says only that it has some.
                    factory.register(SkillBook.Data.class, (owner, data) -> new SkillBook(owner,
                            settings.skillsFor(owner.getTemplate().name()), settings));
                    // An archer's shots become things in the world. The engine's
                    // weapon still aims and reloads; these two decide what
                    // happens between letting go and landing.
                    factory.register(Bow.Data.class, (owner, data) -> new Bow(owner, data, settings));
                    // Stone stops his shots as well as his eyes.
                    factory.register(EyesOnly.Data.class, (owner, data) -> new EyesOnly(owner, settings));
                    factory.register(ArrowUpdate.Data.class, ArrowUpdate::new);
                    // A blast with a pause in the middle. The mark it leaves is a
                    // thing in the world like the arrow above, so the client draws
                    // the warning without being told anything special.
                    factory.register(FallingUpdate.Data.class, FallingUpdate::new);
                    // The meteor's mark turned round: holy light lying where it will
                    // land, and mending whoever it came down for when it does.
                    factory.register(MendingUpdate.Data.class, MendingUpdate::new);
                    // And a rift, which something of the dungeon's own climbs out of.
                    factory.register(SummoningUpdate.Data.class, SummoningUpdate::new);
                    // A monster's blow lands where it stands, as it always did.
                    // This is only how the brain finds out that it struck.
                    factory.register(Swing.Data.class, Swing::new);
                    // What a dead monster leaves lying about. The chest is a
                    // creature like any other -- it is in the world, so the client
                    // draws it without being told anything special.
                    factory.register(LootUpdate.Data.class, (owner, data) -> new LootUpdate(owner, bag,
                            settings.lootDrops().pickupRange(), settings.lootDrops().noteFrames()));
                })
                // A unit is one block, and its record is its word: a Monster block is a Monster.
                .templates(loader -> loader.type(Monster.class).type(Hero.class)
                        .type(Projectile.class).type(Prop.class))
                .loadUnits(creaturesIni)
                // How tall a storey stands is the World block's, and the engine lays
                // every map at it: this one and each floor after it.
                .world(settings.world())
                .mapFromText(asciiMap);

        if (levelMap != null) {
            // The height layer over the map that was just read. The engine owns
            // what a storey means and what may be walked between two of them;
            // this only hands it the picture the generator drew.
            uz.duke.core.pathfind.MapLoader.levels(game.getTerrain(), levelMap);
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
     * @param seed what the run's own dice are wound to — the loot and the floor's
     *             look. A stage carries the seed it was cut
     *             from so that it is the same run every time, not merely the same
     *             rooms
     */
    private static Session open(uz.duke.dungeon.gen.GeneratedDungeon floor, long seed,
            Floors floors, DungeonSettings settings, String subtitle) {
        // What he has put his levels into: a floor gives him a fresh body and a
        // fresh SkillBook, so what he has learnt has to live somewhere that
        // outlives both.
        var learnt = new uz.duke.dungeon.skill.SkillRanks(settings.progression().skillSpread());
        learnt.startWith(settings.skillsFor(settings.run().defaultHero()));
        var bag = new LootBag();
        var arena = world(floor.asciiMap(), floor.levelMap(), settings,
                Content.units(), bag);
        var game = arena.game().subtitle(subtitle);

        // Told which creature is the hero and everything his block says about him --
        // see DefaultHero and Hero.
        var progress = new HeroProgress(arena.hero(), settings.levelling(),
                settings.attributeRules(), settings.progression().levelUpBannerFrames(), bag);
        progress.playing(settings.playedHeroLook());
        progress.manaPerKill(settings.progression().manaPerKill());
        // Drawn from the run's seed as well, so a seed is the whole run: the same
        // one drops the same things off the same monsters.
        var drops = new LootTable(settings.loot(), seed, settings.lootDrops().dropPercent(),
                settings.lootDrops().bossDropPercent(), settings.lootDrops().valuePercentPerDepth());
        var run = new DungeonRun(arena.hero(), arena.dungeon(), floors, settings, progress,
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

        return new Session(game, run, progress, arena.orders());
    }

    /** The attributes of the hero this creature is, or none for anything that is not one. */
    private static HeroAttributes attributesOf(DungeonSettings settings,
            uz.duke.core.thing.GameObject owner) {
        return settings.heroNamed(owner.getTemplate().name()).attributes();
    }
}
