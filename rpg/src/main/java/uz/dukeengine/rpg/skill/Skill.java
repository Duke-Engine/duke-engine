package uz.dukeengine.rpg.skill;

import java.util.Map;
import uz.dukeengine.core.data.Clip;
import uz.dukeengine.core.data.Link;
import uz.dukeengine.core.content.Effect;

/**
 * One skill as the data file describes it, and what it comes to at a given level.
 *
 * <p>The rules and nothing else — no game object, no simulation — for the same
 * reason {@link uz.dukeengine.rpg.hero.Levelling} is: what a skill does at level 7
 * is a question worth being able to ask without starting a dungeon.
 *
 * <p>A skill belongs to a hero template and a key, not to the game. There is one
 * hero today, but a roster is a list in a file rather than a rewrite: a second
 * hero with a different four is four more blocks of INI and no Java. Two heroes
 * may use the same {@link SkillEffect} with quite different numbers, which is the
 * point of separating the effect from the skill.
 *
 * <p>Growth is computed from the level in one step rather than accumulated level
 * by level. Repeated addition drifts, and a hero who reached level 7 by two routes
 * has to have the same skills.
 *
 * @param heroTemplate  the creature this skill belongs to
 * @param key           the key that casts it — Q, W, E or R by convention
 * @param effect        what it does
 * @param damage        damage at the first level ({@code STRIKE}, {@code AREA_DAMAGE})
 * @param damagePerLevel  damage added per level past the first
 * @param radius        how far {@code AREA_DAMAGE} reaches around the caster, and an
 *     aura round its bearer, middle to middle
 * @param range         how far {@code STRIKE} can find a victim, and a {@code HASTE}
 *     the one it hastens, middle to middle
 * @param distance      how far {@code DASH} carries the caster
 * @param hitWidth      how wide the thing a {@code SKILLSHOT} sends is, across
 *     the line of flight. Nothing in the simulation reads it -- an arrow hits
 *     whatever it comes within a step of, which is a property of its speed -- but
 *     the client draws the lane at it, and a lane drawn at anything else is the
 *     picture lying about what the shot will run into. Written down rather than
 *     worked out from the speed because the two are free to disagree and the
 *     PICTURE is the one the player trusts
 * @param boostPercent  what this skill is worth in percent — damage added by
 *     {@code EMPOWER}, damage avoided by {@code GUARD}, the share of every blow
 *     a {@code LIFESTEAL} or a {@code LIFESTEAL_AURA} gives back as health, how
 *     much faster a {@code HASTE} makes a weapon fire, and what a
 *     {@code DAMAGE_AURA} adds to every blow round it. One field because it is one
 *     question ("how much is it worth?") asked of mirrored effects and of
 *     passives that are worth a share of what is done
 * @param boostPerLevel that percentage's growth per level
 * @param durationFrames how long it lasts: {@code EMPOWER}'s extra damage,
 *     {@code GUARD}'s protection, a {@code HASTE}, or how long an
 *     {@code AREA_DAMAGE} goes on landing. Zero for a skill that happens and is over
 * @param tickFrames    how often a lasting {@code AREA_DAMAGE} lands, in frames.
 *     Zero lands it once, which is what every skill written before there was a
 *     whirlwind does — so the damage figure means "per landing" either way and no
 *     existing skill changed by a hair. For an aura it is the beat its {@code Look}
 *     is worn at, and the look lasts two beats
 * @param slowFrames    how long whoever is caught by an area blast drags
 *     his feet afterwards, or zero for a blast that only hurts. One number rather
 *     than a third effect, because a frost nova IS the area blast with one more
 *     thing true of it -- and a knight's whirlwind, having said nothing about it,
 *     is untouched by its existing
 * @param cooldownFrames how long before it can be cast again, at the first level
 * @param cooldownPerLevel  frames added per level — negative to sharpen with level
 * @param maxRank       how many points may go into it. Four for an ordinary
 *     skill and three for an ultimate, which is what makes fifteen levels come
 *     out exactly even across a hero's four
 * @param levelPerRank  the hero level its Nth rank waits for, as a multiple:
 *     4 means the first rank at level 4, the second at 8, the third at 12, and
 *     0 means it waits for nothing but a point. It is also what MAKES a skill an
 *     ultimate — there is no flag saying so, because "the one you have to grow
 *     into" is the whole of what the word means here
 * @param windUpFrames  how long the caster spends preparing before it goes off —
 *     zero for a skill that is instant. A drawn shot has to be seen being drawn,
 *     or the monster simply loses health for no reason anyone can point at.
 * @param projectile    the creature a {@code STRIKE} becomes on its way, or empty
 *     to land where it stands. An arrow that crosses the room is the difference
 *     between a shot and an accusation.
 * @param name          what the player is told it is called, in his own language.
 *     Empty for a skill nobody has named, which the panel then describes by its
 *     key alone rather than by inventing one
 * @param blurb         one or two sentences on what it does — what the tooltip
 *     says above the numbers. The numbers themselves are never in here: they are
 *     computed from the rank and would go stale the moment anything was retuned
 * @param look          the name of the {@code Effect} block that says what
 *     this one looks like going off -- the ring across the floor, the knock to
 *     the camera -- or empty for a skill that is drawn by nothing but whatever it
 *     throws. Named rather than described, so two skills may share a look and a
 *     fifth skill is a fifth block of INI. An aura's is worn rather than gone off:
 *     played on its bearer every {@code TickFrames}, and lasting two of them
 * @param icon          the picture the panel draws in this skill's slot, as a file
 *     beside the other art, or empty for the letter the key is called. Which
 *     drawing goes with which skill is a matter for the file: a fifth skill should
 *     be a fifth block of INI, and nothing in Java should have to learn its name.
 * @param projectileSpeed how fast what it throws travels, in units a second, or 0
 *     for the drawn arrow's own speed, which every hero's shot flies at. A
 *     monster's fireball is slower on purpose: slow enough to be stepped out of
 * @param heal          how much health a {@code HEAL} gives back when it lands
 * @param healBelowPercent a {@code HEAL} is only for someone below this share of his
 *     own health, so a whole skeleton is never mended and a cooldown never wasted
 * @param summons       what a {@code SUMMON} calls up, each kind and how many of it, in
 *     the order written: {@code [Skeleton = 2, Stalker = 2]} is two swordsmen and two
 *     archers a cast, and where the floor has room for fewer, the swordsmen are the
 *     ones that rise. One cast calls up the whole of it
 * @param maxSummoned   how many of one caster's may stand at once; a cast calls up no
 *     more than there is room for
 * @param summonExperiencePercent what killing one is worth, as a share of its own kind:
 *     a thing that was never placed on the floor should not be a well to draw from
 * @param stunFrames    how long whoever a {@code SKILLSHOT} hurts stands dazed after —
 *     the one it struck and everyone its burst caught — or zero for a shot that only
 *     hurts. The engine's own {@code DISABLED}, worn on the victim's own timers: its
 *     legs and its weapon stand still under it and its skills refuse. The fire mage's
 *     fireball has one and its ordinary fire does not
 * @param manaRegen     what a {@code MANA_AURA} adds to every pool of its own round it,
 *     in tenths of a point a second -- the heroes' word and unit for a trickle. Read by
 *     nothing else, and refused on anything else
 */
public record Skill(
        String heroTemplate,
        char key,
        SkillEffect effect,
        float damage,
        float damagePerLevel,
        float radius,
        float range,
        float distance,
        float hitWidth,
        int boostPercent,
        int boostPerLevel,
        int durationFrames,
        int tickFrames,
        int slowFrames,
        int cooldownFrames,
        int cooldownPerLevel,
        int maxRank,
        int levelPerRank,
        int windUpFrames,
        int manaCost,
        int manaCostPerLevel,
        String projectile,
        String icon,
        @Link(Effect.class) String look,
        @Clip String castAnim,
        float castSeconds,
        String name,
        String blurb,
        float projectileSpeed,
        float heal,
        int healBelowPercent,
        Map<String, Integer> summons,
        int maxSummoned,
        int summonExperiencePercent,
        int stunFrames,
        int manaRegen,
        java.util.List<String> notIn,
        boolean endsOnCast,
        boolean endsOnHurt) {

    public Skill {
        notIn = notIn == null ? java.util.List.of() : java.util.List.copyOf(notIn);
    }

    /** A skill refused nowhere, as every skill was before one could be; a mount ending on a cast and on a blow. */
    public Skill(String heroTemplate, char key, SkillEffect effect, float damage, float damagePerLevel, float radius,
            float range, float distance, float hitWidth, int boostPercent, int boostPerLevel, int durationFrames,
            int tickFrames, int slowFrames, int cooldownFrames, int cooldownPerLevel, int maxRank, int levelPerRank,
            int windUpFrames, int manaCost, int manaCostPerLevel, String projectile, String icon, String look,
            String castAnim, float castSeconds, String name, String blurb, float projectileSpeed, float heal,
            int healBelowPercent, Map<String, Integer> summons, int maxSummoned, int summonExperiencePercent,
            int stunFrames, int manaRegen) {
        this(heroTemplate, key, effect, damage, damagePerLevel, radius, range, distance, hitWidth, boostPercent,
                boostPerLevel, durationFrames, tickFrames, slowFrames, cooldownFrames, cooldownPerLevel, maxRank,
                levelPerRank, windUpFrames, manaCost, manaCostPerLevel, projectile, icon, look, castAnim, castSeconds,
                name, blurb, projectileSpeed, heal, healBelowPercent, summons, maxSummoned, summonExperiencePercent,
                stunFrames, manaRegen, java.util.List.of(), true, true);
    }

    /**
     * A cooldown can shorten with level but never vanish: a skill castable every
     * frame is not a skill, and a file is free to sharpen one too far by accident.
     */
    public static final int MIN_COOLDOWN_FRAMES = 1;

    /**
     * What a {@code Skill} block leaves out: a plain strike on a three-second cooldown, four
     * ranks deep, whose owner is the block it is written in.
     */
    static final Skill DEFAULTS = new Skill(null, '\0', SkillEffect.STRIKE, 0f, 0f, 0f, 0f, 0f, 0f,
            0, 0, 0, 0, 0, 90, 0, 4, 0, 0, 0, 0, "", "", "", "", 0f, "", "", 0f, 0f, 0, Map.of(), 0, 0, 0, 0);

    /** This skill as {@code owner}'s, its key the one a player presses. */
    public Skill ownedBy(String owner) {
        return new Skill(owner, Character.toUpperCase(key), effect, damage, damagePerLevel, radius, range,
                distance, hitWidth, boostPercent, boostPerLevel, durationFrames, tickFrames, slowFrames,
                cooldownFrames, cooldownPerLevel, maxRank, levelPerRank, windUpFrames, manaCost,
                manaCostPerLevel, projectile, icon, look, castAnim, castSeconds, name, blurb,
                projectileSpeed, heal, healBelowPercent, summons, maxSummoned,
                summonExperiencePercent, stunFrames, manaRegen, notIn, endsOnCast, endsOnHurt);
    }

    /** Levels earned past the first — what every growth figure is multiplied by. */
    private int grown(int level) {
        return Math.max(0, level - 1);
    }

    public float damageAt(int level) {
        return damage + grown(level) * damagePerLevel;
    }

    public int boostAt(int level) {
        return boostPercent + grown(level) * boostPerLevel;
    }

    /**
     * What it costs to cast at this rank.
     *
     * <p>Counted the way the damage is — see {@link #damageAt} — so a rank can be
     * made to cost more as it hits harder, or less as the caster learns it. Which
     * of the two a skill does is the file's to say, and the two read very
     * differently: a cost that climbs makes an upgraded ultimate something to
     * save for, and one that falls makes it something to lean on.
     *
     * <p>Never below nothing. A negative cost would give mana back for casting,
     * which is a different game.
     */
    public int manaAt(int level) {
        return Math.max(0, manaCost + grown(level) * manaCostPerLevel);
    }

    public int cooldownAt(int level) {
        return Math.max(MIN_COOLDOWN_FRAMES, cooldownFrames + grown(level) * cooldownPerLevel);
    }

    /**
     * The hero level the {@code rank}-th point in this one waits for.
     *
     * <p>One for anything ungated, which is to say "as soon as you have a point
     * to spend" — and level 1 is the first level, so that is no wait at all.
     */
    public int levelForRank(int rank) {
        return levelPerRank <= 0 ? 1 : rank * levelPerRank;
    }

    /**
     * Whether this is the one he has to grow into.
     *
     * <p>Asked of the numbers rather than of a flag beside them. A skill that
     * waits for the hero to reach a level IS an ultimate, and a second flag
     * saying so would be a second thing to keep in step with the first.
     */
    public boolean isUltimate() {
        return levelPerRank > 0;
    }

    /** Whether it becomes something that has to cross the room to arrive. */
    public boolean hasProjectile() {
        return projectile != null && !projectile.isBlank();
    }

    /**
     * Whether this one goes on happening after it is cast.
     *
     * <p>Both halves are required and that is the point: a duration with no tick
     * would be a skill that lasts and never lands, and a tick with no duration a
     * skill that lands for ever. An aura has a tick and no duration, so this is false
     * for it: its tick is the beat its look is worn at, not a landing.
     */
    public boolean lasts() {
        return durationFrames > 0 && tickFrames > 0;
    }

    /** Whether being caught by this also drags the victim's feet. */
    public boolean chills() {
        return slowFrames > 0;
    }

    /** Whether the file said what this one looks like going off. */
    public boolean hasLook() {
        return look != null && !look.isBlank();
    }
}
