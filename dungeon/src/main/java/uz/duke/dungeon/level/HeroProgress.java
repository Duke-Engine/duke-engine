package uz.duke.dungeon.level;

import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ObjectId;
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
 * <p>What experience <em>buys</em> is the game's, and not the engine's rank
 * system: {@code VeterancyLevel} is four fixed ranks with multipliers compiled
 * into an enum, which cannot express "ten levels, each worth this much health".
 * So the engine counts and {@link Levelling} decides.
 *
 * <p>Nothing here survives a death. A new hero is a new object, which this
 * notices by his id, and everything resets — including the one piece of progress
 * that would otherwise outlive him: the weapon bonus lives on the <em>player</em>,
 * and players are not cleared between runs, so it has to be put back by hand or
 * a new hero would start swinging like the last one finished.
 *
 * <p>Deterministic: integer arithmetic from the data file, the simulation's own
 * frame counter for timing the message, and each multiplier computed from the
 * level in one step rather than accumulated — so a hero at level 7 is the same
 * hero however he got there.
 */
public final class HeroProgress {

    private final GamePlayer heroPlayer;
    private final Levelling rules;
    private final int bannerFrames;

    private ObjectId heroId;
    private int level = Levelling.FIRST_LEVEL;
    private int clearBannerAtFrame; // 0 when no message of ours is showing
    /** Experience earned on floors already left behind. */
    private int carriedExperience;
    /** This body's own total, remembered so it can be banked when he descends. */
    private int lastKnownExperience;

    public HeroProgress(GamePlayer heroPlayer, Levelling rules, int bannerFrames) {
        this.heroPlayer = heroPlayer;
        this.rules = rules;
        this.bannerFrames = bannerFrames;
    }

    /** Called every logic frame on the simulation thread. */
    public void tick(DukeGame game) {
        var hero = findHero(game);
        if (hero == null) {
            return; // no hero to advance; the run loop owns the screen now
        }
        if (heroId == null) {
            carryOver(game, hero); // the first hero of a run
        }
        lastKnownExperience = experienceOf(hero);
        int earned = rules.levelFor(getExperience());
        if (earned > level) {
            promote(game, hero, earned);
        }
        expireBanner(game);
    }

    /**
     * Everything back to nothing: a run has ended.
     *
     * <p>Told rather than inferred. Both dying and descending replace the hero
     * object, so noticing a new hero and resetting would wipe his levels every
     * time he went down a floor — which is the opposite of what a floor is for.
     */
    public void reset() {
        heroId = null;
        level = Levelling.FIRST_LEVEL;
        carriedExperience = 0;
        clearBannerAtFrame = 0;
    }

    /**
     * A new hero on a deeper floor, who is the same hero: re-apply to his fresh
     * body and weapon everything the last one had earned.
     *
     * <p>His experience total is on the module the old body carried, and the new
     * one starts at zero — so it is carried here and added to whatever the new
     * body goes on to earn.
     */
    public void carryOver(DukeGame game, GameObject hero) {
        if (heroId != null) {
            carriedExperience += lastKnownExperience;
        }
        heroId = hero.getId();
        lastKnownExperience = 0;
        applyDamageBonus(game, level);
        applyArmour(hero, level);
        if (hero.getBody() instanceof GrowableBody body) {
            body.growMaxHealth(rules.bonusHealth(level));
        }
    }

    private void promote(DukeGame game, GameObject hero, int earned) {
        // Grow by the difference, so skipping two levels at once is worth two.
        float extraHealth = rules.bonusHealth(earned) - rules.bonusHealth(level);
        if (hero.getBody() instanceof GrowableBody body) {
            body.growMaxHealth(extraHealth);
        }
        level = earned;
        applyDamageBonus(game, earned);
        applyArmour(hero, earned);

        game.setBanner("Level " + earned + "!");
        clearBannerAtFrame = game.getLogic().getFrame() + bannerFrames;
    }

    /**
     * Set the weapon bonus to exactly what this level is worth.
     *
     * <p>Assigned rather than compounded: the bonus is a function of the level, so
     * it is computed from the level in one step. Accumulating it would drift as
     * levels stacked, and a hero at level seven must be the same hero however he
     * got there.
     *
     * <p>The bonus belongs to the player rather than the unit, which is exact
     * while the player commands one hero and would need revisiting the day he has
     * companions to share it with.
     */
    private void applyDamageBonus(DukeGame game, int atLevel) {
        var player = game.getLogic().getRtsPlayer(heroPlayer.getIndex());
        if (player != null) {
            player.setWeaponDamageBonus(rules.damageMultiplier(atLevel));
        }
    }

    private void applyArmour(GameObject hero, int atLevel) {
        if (hero.getBody() instanceof GrowableBody body) {
            body.setDamageTaken(rules.damageTakenMultiplier(atLevel));
        }
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
                    && object.getTemplate().getName().equals("Hero")) {
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
