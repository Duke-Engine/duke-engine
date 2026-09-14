package uz.duke.dungeon.level;

import uz.duke.core.module.MoveUpdate;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ObjectId;
import uz.duke.dungeon.content.HeroLook;
import uz.duke.dungeon.loot.LootBag;
import uz.duke.game.DukeGame;
import uz.duke.game.GamePlayer;
import uz.duke.rts.module.ExperienceModule;

/**
 * The hero getting stronger: watches what he has killed and spends it on levels.
 *
 * <p>Experience is the engine's to count. {@code WeaponUpdate} already credits a
 * killer with its victim's worth, and how much a skeleton is worth is a line in
 * {@code creatures.ini} — so this reads that total rather than trying to notice
 * deaths for itself, which would mean watching the world and getting it wrong
 * whenever something died to anything else.
 *
 * <p>What a level buys is his attributes growing, and what those come to is worked
 * out in {@link HeroFigures} — the same arithmetic the panel prints. This is the one
 * place it is applied: to his body, his weapon, his legs and his pool. His first level
 * is already in the body when it is built (see {@link HeroBuild}); everything here is
 * what levels and finds add on top.
 *
 * <p>Nothing here survives a death. A new hero is a new object, which this notices by
 * his id, and everything resets — including the one piece of progress that would
 * otherwise outlive him: the weapon bonus lives on the <em>player</em>, and players are
 * not cleared between runs, so it has to be put back by hand or a new hero would start
 * swinging like the last one finished.
 *
 * <p>Deterministic: every figure is computed from the level and the bag in one step, out
 * of integers from the data file, and applied from a tick — after every object has
 * updated, on the same frame on every machine.
 */
public final class HeroProgress {

    private final GamePlayer heroPlayer;
    private final Levelling rules;
    private final AttributeRules attributeRules;
    private final int bannerFrames;

    /** What he has found on the floor, which moves his figures the way a level does. */
    private final LootBag loot;

    /** Which hero is being played, and everything his block says about him. */
    private HeroLook hero = HeroLook.NONE;

    /**
     * Mana returned for a kill, and 0 for a game that does not pay for them.
     *
     * <p>Off by default, and that is the interesting setting rather than the timid one.
     * Paying for kills makes mana a reward for fighting, which pulls against what it is
     * here for: a resource that makes him choose. With it off the only way to get mana
     * back is to wait, and waiting is the decision.
     */
    private int manaPerKill;

    /** How many items had been found when they were last applied. */
    private int lootStamp = -1;

    private ObjectId heroId;
    private int level = Levelling.FIRST_LEVEL;
    private int clearBannerAtFrame; // 0 when no message of ours is showing
    /** Experience earned on floors already left behind. */
    private int carriedExperience;
    /** This body's own total, remembered so it can be banked when he descends. */
    private int lastKnownExperience;

    /**
     * The speed this body's legs were built for.
     *
     * <p>Remembered rather than read back, because the engine's locomotor does not hand
     * its speed back. Compared exactly: it is recomputed from the same integers every
     * time, so it comes out as the same bits for as long as it has not moved.
     */
    private float speedOnThisBody = Float.NaN;

    public HeroProgress(GamePlayer heroPlayer, Levelling rules, AttributeRules attributeRules,
            int bannerFrames, LootBag loot) {
        this.heroPlayer = heroPlayer;
        this.rules = rules;
        this.attributeRules = attributeRules;
        this.bannerFrames = bannerFrames;
        this.loot = loot;
    }

    /**
     * A different hero is being played from here on.
     *
     * <p>Whoever calls this owes a fresh run — see {@code DungeonRun.startWith}. Nothing
     * here touches the hero standing in the world, because the hero standing in the
     * world is about to be replaced.
     */
    public void playing(HeroLook hero) {
        this.hero = hero == null ? HeroLook.NONE : hero;
    }

    /** The block of the hero being played. */
    public HeroLook getHero() {
        return hero;
    }

    /** How much mana a kill gives back; 0 turns it off. See {@link #manaPerKill}. */
    public void manaPerKill(int points) {
        this.manaPerKill = Math.max(0, points);
    }

    /** What he has picked up this run. */
    public LootBag getLoot() {
        return loot;
    }

    /** Called every logic frame on the simulation thread. */
    public void tick(DukeGame game) {
        var body = findHero(game);
        if (body == null) {
            return; // no hero to advance; the run loop owns the screen now
        }
        if (heroId == null) {
            carryOver(game, body); // the first hero of a run
        }
        // Something died worth experience, so something died. There is no kill
        // event on this side of the engine -- the experience module is rts's and
        // this game may not touch it -- so the rise IS the notice. Nothing else
        // in the dungeon grants experience, which is what makes the reading
        // sound rather than merely convenient.
        int now = experienceOf(body);
        if (manaPerKill > 0 && now > lastKnownExperience && heroId != null) {
            var book = body.findModule(uz.duke.dungeon.skill.SkillBook.class);
            if (book != null) {
                book.restoreMana(manaPerKill);
            }
        }
        lastKnownExperience = now;
        int earned = rules.levelFor(getExperience());
        if (earned > level) {
            promote(game, body, earned);
        }
        if (loot.getFound().size() != lootStamp) {
            lootStamp = loot.getFound().size();
            apply(game, body);
        }
        expireBanner(game);
    }

    /**
     * Everything back to nothing: a run has ended.
     *
     * <p>Told rather than inferred. Both dying and descending replace the hero object,
     * so noticing a new hero and resetting would wipe his levels every time he went down
     * a floor — which is the opposite of what a floor is for.
     */
    public void reset() {
        heroId = null;
        level = Levelling.FIRST_LEVEL;
        carriedExperience = 0;
        clearBannerAtFrame = 0;
        loot.clear();
        lootStamp = -1;
        speedOnThisBody = Float.NaN;
    }

    /**
     * A new hero on a deeper floor, who is the same hero: bring his fresh body up to
     * everything the last one had earned.
     *
     * <p>His experience total is on the module the old body carried, and the new one
     * starts at zero — so it is carried here and added to whatever the new body goes on
     * to earn.
     */
    public void carryOver(DukeGame game, GameObject body) {
        if (heroId != null) {
            carriedExperience += lastKnownExperience;
        }
        heroId = body.getId();
        lastKnownExperience = 0;
        // What the body was built as: his block at the first level, nothing found.
        speedOnThisBody = figuresAt(body, Levelling.FIRST_LEVEL, HeroFigures.Found.NOTHING).speed();
        lootStamp = loot.getFound().size();
        apply(game, body);
    }

    private void promote(DukeGame game, GameObject body, int earned) {
        level = earned;
        apply(game, body);
        game.setBanner("Level " + earned + "!");
        clearBannerAtFrame = game.getLogic().getFrame() + bannerFrames;
    }

    /**
     * Give this body exactly what his level and his bag are worth.
     *
     * <p>Every figure is assigned from one computation rather than nudged by what
     * changed, so skipping two levels at once, or a level and a find on the same frame,
     * comes out as the same hero as taking them one at a time.
     */
    private void apply(DukeGame game, GameObject body) {
        var now = figuresOf(body, found());
        var built = figuresAt(body, Levelling.FIRST_LEVEL, HeroFigures.Found.NOTHING);
        if (body.getBody() instanceof GrowableBody growable) {
            // Up to the figure and never down: a level or a find only ever adds, and
            // current health rises with the ceiling -- it is not a full heal, or
            // levelling mid-fight would be a free escape from losing one.
            float more = now.maxHealth() - growable.getMaxHealth();
            if (more > 0f) {
                growable.growMaxHealth(more);
            }
            // His own plate counts exactly like a breastplate he found, so the file's
            // floor on damage taken holds for a knight as it does for an archer who
            // has picked up four of them.
            growable.setDamageTaken(
                    rules.damageTakenWith(level, hero.armourPercent() + loot.armourPercent()));
        }
        var player = game.getLogic().getRtsPlayer(heroPlayer.getIndex());
        if (player != null) {
            // The weapon was built with his first-level blow; this is what the blow is
            // worth now, as a share of that. It belongs to the player rather than the
            // unit, which is exact while the player commands one hero.
            player.setWeaponDamageBonus(built.attack() > 0f ? now.attack() / built.attack() : 1f);
        }
        applySpeed(body, now.speed());
        applyMana(body, now.maxMana());
        var recovery = body.findModule(Recovery.class);
        if (recovery != null) {
            recovery.rate(hero.healthRegen());
        }
    }

    /**
     * New legs when his speed has moved.
     *
     * <p>The engine's locomotor fixes its speed when it is built and offers no way to
     * change it, so faster legs are a new locomotor put in the old one's place in the
     * update order. Only ever from a tick or between frames, never under a module's own
     * update — the object refuses a list changed under it. Wherever he was walking to he
     * is still walking to, planned again from where he stands.
     */
    private void applySpeed(GameObject body, float speed) {
        if (Float.compare(speed, speedOnThisBody) == 0) {
            return;
        }
        var legs = body.findModule(MoveUpdate.class);
        if (legs == null) {
            return;
        }
        var fresh = new MoveUpdate(body,
                new MoveUpdate.Data(speed, HeroBase.of(body.getTemplate()).turnRate()));
        var goal = legs.isMoving() ? legs.getGoal() : null;
        body.replaceModule(legs, fresh);
        if (goal != null) {
            fresh.moveTo(goal);
        }
        speedOnThisBody = speed;
    }

    /**
     * What he casts out of, handed to the book that spends it.
     *
     * <p>A hero whose file names no pool is left with none, and then nothing he casts
     * costs anything, which is how the game worked before any of this.
     */
    private void applyMana(GameObject body, int maxMana) {
        var book = body.findModule(uz.duke.dungeon.skill.SkillBook.class);
        if (book == null) {
            return;
        }
        boolean isNew = book.getMaxMana() <= 0;
        book.poolOf(maxMana, hero.manaRegen());
        if (isNew) {
            // A body he has only just been given: a new run, or the first frame on a
            // new floor. He arrives full, exactly as his health does -- a hero who
            // walked down a staircase and found himself unable to cast would be being
            // punished for the staircase.
            book.fillMana();
        }
    }

    /**
     * What he has picked up, as far as his figures are concerned.
     *
     * <p>Items give whole points of an attribute; the figures count in tenths.
     */
    public HeroFigures.Found found() {
        return new HeroFigures.Found(loot.attributes(attributeRules), loot.health(), loot.mana(),
                loot.attackPercent());
    }

    /**
     * What this body comes to at his level, with {@code found} — the bag, or
     * {@link HeroFigures.Found#NOTHING} for the figure without it.
     */
    public HeroFigures figuresOf(GameObject body, HeroFigures.Found found) {
        return figuresAt(body, level, found);
    }

    private HeroFigures figuresAt(GameObject body, int atLevel, HeroFigures.Found found) {
        return HeroFigures.of(HeroBase.of(body.getTemplate()), hero.maxMana(), hero.attributes(),
                attributeRules, atLevel, found);
    }

    private void expireBanner(DukeGame game) {
        if (clearBannerAtFrame > 0 && game.getLogic().getFrame() >= clearBannerAtFrame) {
            game.setBanner("");
            clearBannerAtFrame = 0;
        }
    }

    private GameObject findHero(DukeGame game) {
        for (var object : game.getLogic().getObjects()) {
            if (object.getPlayerIndex() == heroPlayer.getIndex()
                    && object.getTemplate().getName().equals(hero.name())) {
                return object;
            }
        }
        return null;
    }

    private static int experienceOf(GameObject hero) {
        var experience = hero.findModule(ExperienceModule.class);
        return experience == null ? 0 : experience.getExperience();
    }

    // ---- observation ----

    public int getLevel() {
        return level;
    }

    /** Everything earned this run, across every floor of it. */
    public int getExperience() {
        return carriedExperience + lastKnownExperience;
    }

    public int getExperienceIntoLevel() {
        return rules.xpIntoLevel(getExperience());
    }

    public int getExperienceForNextLevel() {
        return rules.xpToNextLevel(getExperience());
    }
}
