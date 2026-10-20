package uz.dukeengine.rpg.skill;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.combat.module.DamageModifier;
import uz.dukeengine.combat.module.RateOfFireModifier;
import uz.dukeengine.combat.module.WeaponHold;
import uz.dukeengine.core.event.ObjectDied;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.DamageListener;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.Death;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.module.SpeedModifier;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.World;
import uz.dukeengine.rpg.Seal;
import uz.dukeengine.rpg.item.LootBag;

/**
 * The skills a hero has, what they are doing right now, and how long until he can
 * do them again.
 *
 * <p>Written here rather than built on the engine's {@code SpecialPowerModule},
 * which is a superweapon: one shape (area damage at a map point), values frozen
 * when the unit is built, no notion of a level unlocking anything, and four of
 * them on one unit are not tellable apart. Every one of those is something a
 * dungeon skill needs, so this follows {@code GrowableBody} and {@code HeroProgress}
 * — the game writes its own rule and the engine stays out of it.
 *
 * <p>What it does borrow is {@link DamageModifier}, and this module implements it
 * directly rather than attaching a buff module while {@code EMPOWER} lasts.
 * Attaching and detaching a module mid-frame is exactly what the engine's module
 * list refuses (rightly — it would silently skip whatever came next), and a flag
 * that expires is the same thing without the trap. A {@code HASTE} is held the
 * same way: a buff on the hastened's own book, the list an aura is lent onto,
 * and that book is a {@link RateOfFireModifier} for it -- the seam a hero's boots
 * ride through {@code AttackSpeed} -- and every monster carries one from birth.
 *
 * <p>The book is the module the creature carries; what it holds is kept by the parts it delegates to, each one thing:
 * its mana ({@link ManaPool}), what holds on it for a while ({@link Buffs}), what it bears ({@link Passives}), its
 * mount ({@link Mount}), where a cast is drawn ({@link CastMarks}), and each family of what it casts -- what hurts
 * ({@link Blows}), what is thrown ({@link Shots}), what goes off over an area ({@link Blasts}), what moves
 * ({@link Moves}, {@link Charge}), what is cast for its own side ({@link Allies}) -- and its auras ({@link Auras}).
 *
 * <p>Determinism: cooldowns are frames counted down, never seconds; the victim of
 * a {@code STRIKE} is the nearest enemy with ties broken by object id, and the one
 * a {@code HASTE} is for the sturdiest near, down to the object id; a
 * {@code DASH} walks with {@link StrictMath}. Nothing here asks the clock.
 */
@ModuleGroup({ModuleGroups.COMBAT, ModuleGroups.MOVEMENT, ModuleGroups.EFFECT, ModuleGroups.BODY})
public final class SkillBook extends UpdateModule implements DamageModifier, RateOfFireModifier, WeaponHold,
        DamageListener, SpeedModifier {

    /** It reads no fields; the block only says the unit has one. */
    public record Data() implements ModuleData {
    }

    /**
     * The game's numbers for what no skill says itself.
     *
     * @param muzzleOffset       how far in front of the caster what it throws appears
     * @param shotSpeed          how fast a thrown strike flies where its skill names no speed
     * @param summonTurnDegrees  how far round a rift is turned each time a spot will not do
     * @param summonTurns        how many turns are tried before a rift is given up
     * @param auraPulseFrames    how often a bearer lends its auras
     * @param buffLifeFrames     how long what it lends holds, which outlasts the pulse
     * @param auraMarkLooks      what a creature holding one, two… kinds of aura wears under it; none for nothing
     * @param auraMarkTickFrames how often that mark is played again
     */
    public record Rules(float muzzleOffset, float shotSpeed, float summonTurnDegrees, int summonTurns,
            int auraPulseFrames, int buffLifeFrames, List<String> auraMarkLooks, int auraMarkTickFrames) {

        public Rules {
            auraMarkLooks = auraMarkLooks == null ? List.of() : List.copyOf(auraMarkLooks);
        }
    }

    /**
     * One place a cast should be drawn: the art block's name, the spot, how wide,
     * and -- if it belongs to a creature rather than to a patch of floor -- whose
     * it is. A radius of zero means "as wide as the block itself says".
     *
     * <p><b>{@code on} is the difference between a place and a state.</b> Most of
     * what a cast draws happened AT somewhere: a nova went off here, a meteor is
     * coming down there, and the floor goes on being the floor whatever the man
     * who caused it does next. A guard is not that. It is a condition he is IN for
     * four seconds, and a disc left standing on the flagstone he cast it from is
     * drawing something that is not true -- it says the flagstone is protected.
     *
     * <p>{@link ObjectId#INVALID} for a mark that is a place. What is made of the
     * difference is entirely the client's affair: it is the one that knows a ring
     * from a disc, and only a disc that STAYS has anything to keep up with.
     */
    public record CastMark(String look, float x, float y, float radius, ObjectId on) {
    }

    private final List<Skill> skills;
    private final int[] cooldowns;
    /**
     * The seal, which nothing a skill hurts or mends is found through, no haste or aura is lent across, and
     * no dash, blink or rift crosses: see {@link Seal}.
     */
    private final Seal seal;

    private final ManaPool mana = new ManaPool();
    private final Buffs buffs = new Buffs();
    private final Passives passives;
    private final Mount mount;
    private final CastMarks marks;
    private final Blows blows;
    private final Shots shots;
    private final Blasts blasts;
    private final Moves moves;
    private final Allies allies;
    private final Auras auras;

    /**
     * The frame of his last cast.
     *
     * <p>For whatever chooses for the hero, which has to tell the player's orders from everything else the hero
     * does. A cast is the player's most recent word and supersedes an attack order.
     */
    private int lastCastFrame = Integer.MIN_VALUE;

    public SkillBook(GameObject owner, List<Skill> skills, Rules rules, Seal seal) {
        super(owner);
        this.skills = List.copyOf(skills);
        this.cooldowns = new int[skills.size()];
        this.seal = seal;
        this.passives = new Passives(owner, this.skills);
        this.mount = new Mount(owner);
        this.marks = new CastMarks(owner);
        this.blows = new Blows(this, seal, rules);
        this.shots = new Shots(owner, blows, rules);
        this.blasts = new Blasts(owner, blows, marks);
        this.moves = new Moves(owner, blows, seal, marks);
        this.allies = new Allies(owner, seal, rules);
        this.auras = new Auras(owner, passives, rules, seal);
    }

    /** His legs: the strongest {@code MOVE_SPEED} buff he holds, and the mount he rides. */
    @Override
    public float speedShare() {
        var world = getOwner().getWorld();
        int percent = world == null ? 0 : buffOf(SkillEffect.MOVE_SPEED, world.getFrame());
        var riding = mount.riding();
        if (riding != null) {
            percent = Math.max(percent, riding.boostAt(passives.rankOf(riding)));
        }
        return (100 + percent) / 100f;
    }

    /** Whether he rides now. */
    public boolean isRiding() {
        return mount.riding() != null;
    }

    /** Off his mount — a blow, an ordered attack, a second press: his legs his own, drawn as himself. */
    public void dismount() {
        mount.dismount();
    }

    /** A blow on him: a {@code STAGGER} he bears stuns him and holds him at his flinch, if the blow is heavy enough. */
    @Override
    public void onDamage(DamageType type, float amount, Death blow, int frame) {
        mount.onHurt();
        passives.onBlow(amount, blow, frame);
    }

    /** Whether a charge is carrying him: his legs are not his own until it ends. */
    @Override
    public boolean keepsBusy() {
        return moves.isCharging();
    }

    /**
     * What he casts out of, and how fast it comes back.
     *
     * <p>Pushed in from outside rather than read here, exactly as his armour and
     * his weapon bonus are: what a level is worth is {@link uz.dukeengine.rpg.hero.Levelling}'s
     * arithmetic, and this module has no idea what level its owner is. See
     * {@code HeroProgress}, which knows it for a hero, and the game's step that sets a creature's level,
     * which knows it for a monster: the two places that size a pool.
     *
     * <p>A pool that GROWS keeps whatever was in it and gains the difference, so
     * levelling up is a gift rather than a refill -- the same rule the body
     * follows when its maximum health grows.
     *
     * <p>A pool created from nothing gains nothing, and that is the difference
     * between capacity and contents. Whoever made the creature decides whether he
     * starts full: a hero does, on a new run and on every floor after it, and
     * {@code HeroProgress} is where that is said; a monster is met rested, and
     * the game's step that sets a creature's level fills it where it is placed or rises. Filling here
     * instead would mean a creature could never be given a pool it was not also
     * handed the contents of, which is a decision this module is in no position to
     * make.
     */
    public void poolOf(int max, int tenthsPerSecond) {
        mana.poolOf(max, tenthsPerSecond);
    }

    /**
     * The same pool made bigger or smaller with nothing given: a bigger one is only room, and what no longer fits a
     * smaller one spills. What he carries resizes his pool this way — were it a gift, putting a manastone down and
     * picking it up again would be a drink.
     */
    public void resize(int max, int tenthsPerSecond) {
        mana.resize(max, tenthsPerSecond);
    }

    /** Fill him up: a new run, or a floor he has just walked onto. */
    public void fillMana() {
        mana.fill();
    }

    /** Give some back, up to the brim -- a potion, or something he killed. */
    public void restoreMana(int points) {
        mana.restore(points);
    }

    public int getMana() {
        return mana.mana();
    }

    /** How fast it comes back, in tenths of a point a second. */
    public int getManaRegen() {
        return mana.tenthsPerSecond();
    }

    /** The frame a cast was last refused for want of mana; 0 if none ever was. */
    public int getRefusedForManaFrame() {
        return mana.refusedFrame();
    }

    public int getMaxMana() {
        return mana.max();
    }

    /** Whether he could pay for the skill on {@code key} at this rank right now. */
    public boolean canAfford(char key, int level) {
        int slot = slotOf(key);
        return slot < 0 || mana.canAfford(skills.get(slot).manaAt(level));
    }

    public List<Skill> getSkills() {
        return skills;
    }

    /** Frames until the skill on {@code key} is ready; 0 when it is. */
    public int cooldownOf(char key) {
        int slot = slotOf(key);
        return slot < 0 ? 0 : cooldowns[slot];
    }

    public boolean isReady(char key) {
        int slot = slotOf(key);
        return slot >= 0 && cooldowns[slot] <= 0;
    }

    /** The skill on {@code key}, or {@code null} if there is none. */
    public Skill skillOn(char key) {
        int slot = slotOf(key);
        return slot < 0 ? null : skills.get(slot);
    }

    /** How many of what it called up are standing, or still climbing out. */
    public int summonedStanding() {
        return allies.standing();
    }

    /** A rift of its own has landed: what climbed out counts in its place. */
    public void risenInPlaceOf(ObjectId rift, ObjectId risen) {
        allies.risenInPlaceOf(rift, risen);
    }

    /** Frames of extra damage left, for anything that wants to draw it. */
    public int getBoostFrames() {
        return buffs.boostFrames();
    }

    /**
     * Lend it {@code value} of {@code kind}, holding through frame {@code until}, from the creature whose object id is
     * {@code source}. It holds one buff for each kind and lender, so a lender giving again replaces its own -- where it
     * stands in the list, and whatever it was worth before: the latest word is the lender's.
     */
    public void give(SkillEffect kind, int source, int value, int until) {
        buffs.give(kind, source, value, until);
    }

    /**
     * The figure of {@code kind} it holds on {@code frame}: the largest worth among the buffs of that kind that still
     * hold then, and 0 for none. The largest and never the sum, so that a stronger lender leaving hands over to a
     * weaker one still in reach with no frame between that is worth nothing; whole numbers, so it is the same in any
     * order.
     */
    public int buffOf(SkillEffect kind, int frame) {
        return buffs.of(kind, frame);
    }

    /**
     * What it holds, in the order the buffs were first given. One whose last frame has passed is still in it until the
     * next {@link #update} lets go of it.
     */
    public List<Buff> getBuffs() {
        return buffs.held();
    }

    /** The frame he last committed to a skill, or a long time ago if he never has. */
    public int getLastCastFrame() {
        return lastCastFrame;
    }

    /** What his last cast pointed his weapon at, or {@code null}. */
    public ObjectId getLastAimedAt() {
        return shots.lastAimedAt();
    }

    /** Where his last cast wants drawing, and as what. Empty for most of a run. */
    public List<CastMark> getCastMarks() {
        return marks.marks();
    }

    /**
     * When they were put there -- which is not {@link #getLastCastFrame}: a whirlwind moves this on each time it
     * lands, and the player's last word stays where it was. Drawing only.
     */
    public int getCastMarkFrame() {
        return marks.frame();
    }

    private int slotOf(char key) {
        for (int slot = 0; slot < skills.size(); slot++) {
            if (skills.get(slot).key() == key) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Cast the skill on {@code key} at a hero of {@code level}. Returns false and
     * does nothing if there is no such skill, it is still recharging, it is never
     * cast at all, he is stunned, or the level has not unlocked it — an ultimate
     * refuses rather than fires weakly.
     */
    public boolean cast(char key, int level) {
        return cast(key, level, null, null);
    }

    /**
     * Cast at what the player pointed at.
     *
     * <p>An aimed skill that cannot reach what it was aimed at <em>refuses</em>,
     * leaving its cooldown untouched, rather than going off at something else. A
     * player who clicked one skeleton and hit a different one has been given a
     * skill he cannot aim, and the cooldown he wasted is the one he needed.
     *
     * @param at        the creature a {@code UNIT} skill was aimed at, or null for
     *                  the old behaviour of taking whatever is nearest
     * @param towards   where a {@code GROUND} skill was aimed, or null to use the
     *                  caster's current facing
     */
    public boolean cast(char key, int level, ObjectId at, Coord3D towards) {
        level = passives.raised(key, level); // what he wears raises a skill he has learnt, while he wears it
        int slot = slotOf(key);
        if (slot >= 0 && skills.get(slot).effect() == SkillEffect.MOUNT && mount.isUp()) {
            mount.dismount(); // a second press gets him off, whatever its cooldown
            return true;
        }
        if (slot < 0 || cooldowns[slot] > 0) {
            return false;
        }
        if (skills.get(slot).effect().isPassive()) {
            // Never cast: it holds for as long as he lives -- see SkillEffect.isPassive.
            // Refused before anything happens, so nothing is spent on a key that does
            // nothing.
            return false;
        }
        if (getOwner().hasStatus(ObjectStatus.DISABLED)) {
            // Stunned -- the engine's DISABLED, which his legs and his weapon
            // already stand still under. Refused before anything happens, as a
            // cast he cannot pay for is: no cooldown started, nothing taken. What
            // he cast before it goes on (a drawn shot still leaves, a whirlwind
            // still turns), and whatever chooses for him goes on choosing; he
            // only cannot act.
            return false;
        }
        var skill = skills.get(slot);
        var where = getOwner().getWorld();
        if (where != null && !skill.notIn().isEmpty()
                && where.zonesAt(getOwner().getPosition()).stream().anyMatch(skill.notIn()::contains)) {
            return false; // not here: a town refuses it, nothing spent
        }
        var riding = mount.riding();
        if (riding != null && skill.effect() != SkillEffect.MOUNT) {
            if (!riding.endsOnCast()) {
                return false; // refused while he rides
            }
            mount.dismount();
        }
        if (!mana.canAfford(skill.manaAt(level))) {
            // Refused before anything at all happens, which is the whole of what
            // "cannot afford" has to mean: no cooldown started, no mana taken,
            // no effect drawn and no gesture made. A skill that goes through the
            // motions and then does nothing is a skill the player believes he
            // cast.
            mana.refuse(where == null ? 0 : where.getFrame());
            return false;
        }
        if (level < 1) {
            // Unlearnt. The level handed in is what the player has PUT INTO this
            // skill rather than what he has reached himself -- see SkillRanks --
            // so nothing at all is the answer for three of his four slots at the
            // start of every run.
            return false;
        }
        var owner = getOwner();
        var world = owner.getWorld();
        if (world == null || owner.isEffectivelyDead()) {
            return false;
        }
        // Read before it goes off, because two of the effects move him and the
        // spot he LEFT is half of what a blink looks like.
        var stood = owner.getPosition();
        if (!apply(skill, level, world, at, towards)) {
            return false; // aimed at nothing it could reach; the cooldown is not spent
        }
        marks.rememberTheCast(skill, stood, towards);
        mana.pay(skill.manaAt(level));
        cooldowns[slot] = Math.max(Skill.MIN_COOLDOWN_FRAMES, skill.cooldownAt(level));
        lastCastFrame = world.getFrame();
        // He does one thing at a time. Casting is the player's latest word, so
        // the walk he was on stops here rather than going on underneath it — a
        // dash that lands him somewhere and then walks him back is not a dash.
        // The errand the walk was for is given up where the player's cast comes
        // in (Skills.cast), between frames: a monster's brain casts from its own
        // update, in which nothing may change what modules it has.
        var legs = owner.findModule(MoveUpdate.class);
        if (legs != null) {
            legs.stop();
        }
        return true;
    }

    /** @return whether it went off, which an aimed skill may decline */
    private boolean apply(Skill skill, int level, World world, ObjectId at, Coord3D towards) {
        return switch (skill.effect()) {
            case STRIKE -> shots.strike(skill, level, world, at);
            case SKILLSHOT -> shots.skillshot(skill, level, towards);
            case WAVE -> shots.wave(skill, level, towards);
            case METEOR -> shots.meteor(skill, level, world, towards);
            case AREA_DAMAGE -> blasts.around(skill, level, world);
            case AREA_AT_SPOT -> blasts.atSpot(skill, level, world, towards);
            case DASH -> moves.dash(skill, level, world, towards);
            case BLINK -> moves.blink(skill, world, towards);
            case SKEWER -> moves.skewer(skill, level, towards);
            case PULL -> moves.pull(skill, level, world);
            case PUSH -> moves.push(skill, level, world, towards);
            case HEAL -> allies.mend(skill, world, at);
            case SUMMON -> allies.summon(skill, world, towards);
            case HASTE -> allies.haste(skill, level, world);
            case MOUNT -> mount.begin(skill, world);
            case MODULE -> castByAModule(skill, towards);
            case EMPOWER -> {
                buffs.empower(skill, level);
                yield true;
            }
            case GUARD -> {
                buffs.guard(skill, level);
                yield true;
            }
            // Never cast: cast refuses a passive before it gets here.
            case LIFESTEAL, DAMAGE_AURA, MANA_AURA, LIFESTEAL_AURA, SOUL_DRINK, STAGGER, MOVE_SPEED, UNYIELDING,
                    DRAGON_BLOOD -> true;
        };
    }

    /** Whether the first of the caster's modules that casts skills cast {@code skill}; false where none does. */
    private boolean castByAModule(Skill skill, Coord3D towards) {
        for (var module : getOwner().modulesView()) {
            if (module instanceof SkillCaster caster) {
                return caster.cast(skill, towards, seal);
            }
        }
        return false;
    }

    /**
     * How much harder its blows and its skills land: an {@code EMPOWER} while it lasts, and the strongest
     * {@code DAMAGE_AURA} lent to it that still holds -- see {@link #auraOn}. The engine's weapon multiplies it into
     * each blow, and the book into each skill; a mending is not damage and does not ask.
     */
    @Override
    public float damageMultiplier() {
        // A soul drink is added to the strongest aura, never maxed with it: it is his own, not lent.
        return buffs.empowered() * (1f + (auraOn(getOwner(), SkillEffect.DAMAGE_AURA) + soulPercent()) / 100f);
    }

    /**
     * Lend it the passives of whatever {@code bag} has acting on its bearer — what is worn, or every slot of a bag that
     * wears nothing — each thing's {@link uz.dukeengine.rpg.item.Loot#skills}: put on, they are its; taken off, gone.
     * Looked at again whenever the bag changes. What a soul drink has drunk stays with this book, not the thing.
     */
    public void lendFrom(LootBag bag) {
        passives.lendFrom(bag);
    }

    /** Its {@code SOUL_DRINK}, its own or lent, or null: the first. */
    public Skill soulDrink() {
        return passives.first(SkillEffect.SOUL_DRINK);
    }

    @Override
    public void onCreated() {
        passives.holdTheFloor(); // before the first blow can land
    }

    /**
     * The rank a skill of {@code key} stands at, as the game keeps it (its {@code SkillRanks}): what a passive reads,
     * for a passive is never cast with one. The first where it is never said.
     */
    public void setRank(char key, int rank) {
        passives.setRank(key, rank);
    }

    /**
     * Whether its bearer has been beaten — an {@code UNYIELDING} of its held him at his floor — from the frame the blow
     * landed, and for good: what being beaten does is the game's to say.
     */
    public boolean isBeaten() {
        return passives.isBeaten();
    }

    /** The souls it has drunk — see {@link SkillEffect#SOUL_DRINK}. */
    public int getSouls() {
        return passives.souls();
    }

    /** The souls it has drunk, put back from a save. */
    public void setSouls(int souls) {
        passives.setSouls(souls);
    }

    /**
     * What its souls add to every blow and skill, in percent: its {@code SOUL_DRINK}'s {@code BoostPercent} for every
     * {@code TickFrames} souls, up to its {@code Damage} where that is above 0; nothing without one.
     */
    public int soulPercent() {
        return passives.soulPercent();
    }

    /**
     * A death reaped, told to the book of whoever dealt it: an enemy its bearer killed is a soul for a {@code
     * SOUL_DRINK} it has. For the runtime's watcher of deaths ({@code GameLogic.onDied}); a death nobody dealt, one of
     * the killer's own side, or one dealt by a thing with no soul drink, counts for nothing.
     */
    public static void reaped(World world, ObjectDied died) {
        var killer = died.killer() == null ? null : world.findObject(died.killer());
        var book = killer == null ? null : killer.findModule(SkillBook.class);
        if (book == null || book.soulDrink() == null
                || world.getRelationship(killer.getPlayerIndex(), died.playerIndex()) != Relationship.ENEMIES) {
            return;
        }
        book.passives.drinkASoul();
    }

    /**
     * The strongest aura of {@code kind} on {@code creature}, 0 for none: the largest worth -- a {@code BoostPercent},
     * or a {@code MANA_AURA}'s tenths of a point a second -- among the buffs of that kind its book holds on this frame,
     * each lent by a bearer of its side that reached it on a pulse. Nothing for a creature that carries no book, as
     * one without a {@code StatusUpdate} is not stunned.
     *
     * <p>Read from what it holds, never searched for round it: a creature stepping into reach, or a bearer appearing,
     * is lent within one Combat {@code AuraPulseFrames}, and one stepping out, or whose bearer falls, keeps what it was
     * lent at most Combat's {@code BuffLifeFrames}. A maximum of whole numbers, the same in any order.
     */
    public static int auraOn(GameObject creature, SkillEffect kind) {
        var book = creature == null ? null : creature.findModule(SkillBook.class);
        var world = creature == null ? null : creature.getWorld();
        return book == null || world == null ? 0 : book.buffOf(kind, world.getFrame());
    }

    /**
     * The kinds of aura on {@code creature} now, in {@link SkillEffect} order: each one whose {@link #auraOn} is above
     * 0 -- but a {@code MANA_AURA} only for a creature with a pool, for it fills pools and makes none, and changes
     * nothing on one without. Counted by kind, not by bearer: two summoners lend one might. Nothing for a creature
     * that carries no book, for a hero -- no aura is lent to him -- or for one that has fallen.
     *
     * <p>The one rule for what is on a creature, for whatever draws it to ask -- the circle its own book lays under it,
     * and the HUD's row over the creature the player picks. It asks {@link #auraOn}, so a
     * picture of what is lent holds exactly while the figures do, and cannot disagree with them. Pure: asked when
     * wanted, nothing kept, and an answer that is the asker's own.
     */
    public static List<SkillEffect> aurasOn(GameObject creature) {
        var book = creature == null ? null : creature.findModule(SkillBook.class);
        if (book == null || creature.isEffectivelyDead()) {
            return List.of();
        }
        var on = new ArrayList<SkillEffect>();
        for (var kind : SkillEffect.values()) {
            // The pool first: a mana aura is not looked for round a creature it could change nothing on.
            if (kind.isAura() && (kind != SkillEffect.MANA_AURA || book.mana.inUse()) && auraOn(creature, kind) > 0) {
                on.add(kind);
            }
        }
        return List.copyOf(on);
    }

    /**
     * How much faster its weapon fires while a {@code HASTE} holds on it: the engine divides every wait by this and
     * cuts it to whole frames -- 30 frames at 1.75 are 17. Read at each shot, from the strongest haste on its list that
     * holds on this frame, never the sum -- see {@link #buffOf}; 1 when none holds.
     */
    @Override
    public float rateOfFireMultiplier() {
        var world = getOwner().getWorld();
        return world == null ? 1f : 1f + buffOf(SkillEffect.HASTE, world.getFrame()) / 100f;
    }

    /**
     * {@code striker} landed a blow worth {@code dealt}: every {@code LIFESTEAL} among
     * its skills, and the strongest {@code LIFESTEAL_AURA} lent to it that still holds
     * -- see {@link #auraOn} -- give it back their shares of it, added, as health, never
     * above its maximum. Nobody, the dead, and a creature with neither get nothing.
     *
     * <p>Told rather than listening, by every place a blow lands: a swing where the
     * striker stands, which {@code Swing} hears the moment before the weapon lands it;
     * a shot when it arrives and each its burst catches, in {@code ArrowUpdate}; a
     * meteor's blast, for each it hurts, in {@code FallingUpdate}; and a skill's own
     * blow -- an area blow, a blast at a spot, a strike with no shot, a charge -- for
     * each it hurts, here in the book. The figure is what the blow was worth, not what
     * the victim had left: a kill is no special case.
     *
     * <p>Deterministic: whole percentages added up, its skills in the order the file
     * wrote them, one multiplication of the blow's own figure, on the simulation's
     * frame, and the body's own {@code heal}.
     */
    public static void drink(GameObject striker, float dealt) {
        var book = striker == null ? null : striker.findModule(SkillBook.class);
        if (book == null || dealt <= 0f || striker.isEffectivelyDead() || striker.getBody() == null) {
            return;
        }
        int share = lifestealPercent(striker);
        if (share > 0) {
            striker.getBody().heal(dealt * share / 100f);
        }
    }

    /**
     * The share of every blow {@code creature} drinks back, in percent: its own {@code LIFESTEAL}s and those lent by
     * what it wears, each at its rank, and the strongest {@code LIFESTEAL_AURA} on it. 0 for one with no book.
     */
    public static int lifestealPercent(GameObject creature) {
        var book = creature == null ? null : creature.findModule(SkillBook.class);
        if (book == null) {
            return 0;
        }
        return auraOn(creature, SkillEffect.LIFESTEAL_AURA) + book.passives.lifesteal();
    }

    /** Health given back each second: a {@code DRAGON_BLOOD}'s at its rank, and the strongest {@code HEAL} buff on it. */
    public float healthPerSecond() {
        var world = getOwner().getWorld();
        return passives.healthPerSecond(
                world == null ? 0f : buffOf(SkillEffect.HEAL, world.getFrame()) / (float) ManaPool.TENTHS);
    }

    /** Mana that comes back each second: its pool's own trickle and the strongest {@code MANA_AURA} on it. */
    public float manaPerSecond() {
        return mana.perSecond(auraOn(getOwner(), SkillEffect.MANA_AURA));
    }

    /**
     * How much of a blow he is currently turning aside, in percent.
     *
     * <p>Read rather than applied. A body's armour is a function of his level,
     * what he has found and what he was born in, and {@code HeroProgress} is the
     * one place that adds those up — a guard that set the armour itself would be
     * undone by the next level, and a level would end the guard.
     */
    public int getGuardPercent() {
        return buffs.guardPercent();
    }

    /** Frames of protection left, for anything that wants to draw it. */
    public int getGuardFrames() {
        return buffs.guardFrames();
    }

    /**
     * His bow keeps quiet while he is drawing a heavy shot.
     *
     * <p>Without this he loosed two arrows for one keypress: the skill points his
     * weapon at the target so that he is visibly taking aim, and his weapon —
     * which updates before this module does — took that as an order and fired the
     * ordinary shot on the spot. He has one bow and he is using it.
     *
     * <p>The reload runs on underneath, so his ordinary shooting resumes the frame
     * after the heavy one leaves rather than starting a fresh wait.
     */
    @Override
    public boolean holdingFire() {
        return shots.isDrawing() || mount.isUp(); // and nothing struck from the saddle
    }

    @Override
    public void update() {
        moves.driveTheCharge();
        mount.mountOn();
        passives.holdTheFloor();
        passives.runTheBlood();
        mana.regenerate(auraOn(getOwner(), SkillEffect.MANA_AURA));
        buffs.digest(getOwner());
        for (int slot = 0; slot < cooldowns.length; slot++) {
            if (cooldowns[slot] > 0) {
                cooldowns[slot]--;
            }
        }
        buffs.countDown(getOwner());
        auras.lend();
        auras.wear();
        auras.wearTheMark();
        blasts.turnTheWhirlwind();
        shots.drawOn();
    }
}
