package uz.dukeengine.rpg.skill;

import uz.dukeengine.core.module.ModuleData;
import java.util.List;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.World;
import uz.dukeengine.rpg.Facing;
import uz.dukeengine.rpg.SightLine;
import uz.dukeengine.rpg.fight.FallingUpdate;
import uz.dukeengine.rpg.hero.LevelBonus;
import uz.dukeengine.rpg.fight.Arrows;
import uz.dukeengine.rpg.Seal;
import uz.dukeengine.combat.event.WeaponFired;
import uz.dukeengine.combat.module.DamageModifier;
import uz.dukeengine.combat.module.RateOfFireModifier;
import uz.dukeengine.combat.module.StatusUpdate;
import uz.dukeengine.combat.module.WeaponHold;
import uz.dukeengine.combat.module.WeaponUpdate;

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
 * <p>Determinism: cooldowns are frames counted down, never seconds; the victim of
 * a {@code STRIKE} is the nearest enemy with ties broken by object id, and the one
 * a {@code HASTE} is for the sturdiest near, down to the object id; a
 * {@code DASH} walks with {@link StrictMath}. Nothing here asks the clock.
 */
@ModuleGroup({ModuleGroups.COMBAT, ModuleGroups.MOVEMENT, ModuleGroups.EFFECT, ModuleGroups.BODY})
public final class SkillBook extends UpdateModule implements DamageModifier, RateOfFireModifier, WeaponHold,
        uz.dukeengine.core.module.DamageListener {

    /** It reads no fields; the block only says the unit has one. */
    public record Data() implements ModuleData {
    }

    /** How far apart a dash checks the ground it is crossing. */
    private static final float DASH_STEP = 5f;

    /** How far apart a blink checks for floor while it walks its landing back. */
    private static final float BLINK_STEP = 5f;

    private final List<Skill> skills;
    private final int[] cooldowns;

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

    private final Rules rules;

    /** Frames of {@code EMPOWER} left, and what it is worth while it lasts. */
    private int boostFrames;
    private int boostPercent;

    /** Frames of {@code GUARD} left, and how much of a blow it turns aside. */
    private int guardFrames;
    private int guardPercent;

    /**
     * What others have lent it and not yet let go of: one buff for each kind and lender, in the order they were first
     * given -- an aura, given again on each of its bearer's pulses (see {@link #lendTheAuras}), or a haste, given when
     * it is cast (see {@link #apply}). Plain state, the same on every machine: {@link #buffOf} reads it on the frame a
     * figure is wanted, and {@link #update} lets go of what has passed.
     */
    private final List<Buff> buffs = new java.util.ArrayList<>();

    /**
     * An {@code AREA_DAMAGE} that has not finished happening.
     *
     * <p>What it is worth is worked out when it is cast rather than when it lands,
     * so a whirlwind is not sharpened halfway through by the level he reaches
     * during it — and so that it goes on being his even if something about him
     * changes while it turns.
     */
    private int lastingFrames;
    private int lastingEvery;
    private int lastingNext;
    private float lastingDamage;
    private float lastingRadius;

    /** What it is drawn as, so each landing is drawn and not only the first. */
    private String lastingLook = "";

    /**
     * The frame of his last cast, and what it pointed his weapon at.
     *
     * <p>Both are for whatever chooses for the hero, which has to tell the
     * player's orders from everything else the hero does. A cast is the player's
     * most recent word and supersedes an attack order; the target a cast aims at
     * is the skill's own and must not be read as one.
     */
    private int lastCastFrame = Integer.MIN_VALUE;
    private ObjectId lastAimedAt;

    /**
     * Where the last cast should be DRAWN, and what it should be drawn as.
     *
     * <p>The simulation deciding what a thing looks like would be the wrong way
     * round, and this does not: it decides only WHERE, which nothing but the
     * simulation knows, and hands over the name of a block in the art file for
     * the rest. The client is free to draw nothing at all.
     *
     * <p>A list, because one cast is not always one place. A blink is two -- the
     * spot he left and the spot he arrived at -- and half a blink is a teleport
     * with a bug.
     *
     * <p>It is left standing rather than cleared each frame. What keeps a ring
     * from being drawn twice is the FRAME beside it, which the client compares
     * against the last one it drew; clearing this would mean a cast landing in a
     * frame the client happened to miss simply never being seen.
     */
    private final List<CastMark> castMarks = new java.util.ArrayList<>();

    /**
     * The frame the marks below were put there, which is NOT {@link #lastCastFrame}.
     *
     * <p>Two numbers that look like one and are not, and they were one until a
     * whirlwind proved it. {@code lastCastFrame} is when the PLAYER last spoke,
     * and whatever chooses for the hero reads it to decide whether a cast
     * has superseded a move order. A whirlwind lands eight times off a single
     * press, and moving that number on at each landing would have cancelled the
     * order he gave -- so he would have stopped walking, mid-ultimate, without
     * anybody touching the mouse.
     *
     * <p>This one is only ever read by the client, which compares it against the
     * last one it drew so that one landing is drawn once.
     */
    private int castMarkFrame = Integer.MIN_VALUE;

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

    // ---- a shot begun and not yet loosed ----

    /**
     * The heavy shot in progress: which skill, at what level, and at whom.
     *
     * <p>A skill that takes time to cast needs somewhere to be while it is being
     * cast, and this is it. Held rather than queued because a hero draws one arrow
     * at a time: casting again is refused by the cooldown, which started the moment
     * he committed.
     */
    private Skill drawing;
    private int drawnAtLevel;
    private ObjectId drawnFor;
    private int loosesIn;

    /**
     * What he has to cast out of, and what he can hold.
     *
     * <p>Whole points, never a fraction. That is not tidiness: mana is spent and
     * regained inside the simulation, and a simulation that adds floats together
     * thirty times a second is one where two machines drift apart — slowly, and
     * then all at once, in the frame where one of them can afford a meteor and
     * the other cannot.
     *
     * <p>Which is also why the trickle is counted the way it is. The rate is held
     * in <b>tenths of a point a second</b> and {@link #manaCarry} holds what has
     * been earned towards the next whole point: each frame adds the rate to the
     * carry, and every time the carry reaches a second's worth of tenths one point
     * falls out. A hero on 70 tenths gets exactly 7 a second, on every machine,
     * forever -- where {@code mana += 7f / 30f} does not.
     *
     * <p>Tenths rather than whole points because of what a level is worth. Whole
     * points a second is too coarse a step to grow with: the smallest raise there
     * is would take a knight from three a second to four, which is a third again,
     * and by the tenth level he would be regenerating faster than the mage.
     */
    private int mana;
    private int maxMana;
    private int manaTenthsPerSecond;
    private int manaCarry;
    /** The first frame a {@code STAGGER} may stagger him again. */
    private int staggerFrom;

    /** A blow on him: a {@code STAGGER} he bears stuns him and holds him at his flinch, if the blow is heavy enough. */
    @Override
    public void onDamage(DamageType type, float amount, uz.dukeengine.core.module.Death blow, int frame) {
        Skill stagger = null;
        for (var skill : passives()) {
            if (skill.effect() == SkillEffect.STAGGER) {
                stagger = skill;
                break;
            }
        }
        var owner = getOwner();
        if (stagger == null || frame < staggerFrom || owner.isEffectivelyDead() || stagger.stunFrames() <= 0
                || amount < stagger.damage() && (blow == null || !blow.critical())) {
            return;
        }
        daze(owner, stagger.stunFrames());
        owner.busy(stagger.castAnim(), stagger.stunFrames());
        staggerFrom = frame + stagger.stunFrames() + stagger.durationFrames();
    }

    /** Health a {@code HEAL} buff has paid toward its next whole point, counted as {@link #manaCarry} is. */
    private int healCarry;

    /** A point is this many tenths; the rate is held in them. See {@link #manaCarry}. */
    private static final int TENTHS = 10;

    /**
     * Whether this creature pays for what it casts.
     *
     * <p>Off unless it is given a pool: a hero by what he is made of, and a monster
     * whose kind names one where it is placed -- the three casting mages, see
     * the game's step that sets a creature's level. Every other monster casts free.
     */
    private boolean usesMana;

    /**
     * The frame a cast was last refused for want of mana, or 0 for never.
     *
     * <p>A moment rather than a state, and stamped with its frame for the same
     * reason a cast is: the status line is rebuilt and sent every frame whether
     * anything happened or not, so without the stamp the client would sound the
     * refusal thirty times a second for as long as nothing else went on.
     *
     * <p>It is worth telling him at all because the alternative is silence. A
     * skill that is merely reloading says so — the socket is swept and counting —
     * but one he cannot pay for looks exactly like a key that did not register.
     */
    private int refusedForManaFrame;

    /**
     * What this caster has called up and is still standing, or still climbing out -- in
     * the order it was called. A rift counts as well as what comes out of it, so a cast
     * made while one is still open cannot overshoot {@code MaxSummoned}.
     */
    private final List<ObjectId> summoned = new java.util.ArrayList<>();

    /**
     * The seal, which nothing a skill hurts or mends is found through, no haste or aura is lent across, and
     * no dash, blink or rift crosses: see {@link Seal}.
     */
    private final Seal seal;

    /**
     * Passives lent by what its bearer wears, by the id of the thing lending them, in the order they were put on —
     * see {@link #lendFrom}. Read wherever its own passives are: never cast, never in a slot.
     */
    private final java.util.Map<String, List<Skill>> lent = new java.util.LinkedHashMap<>();
    /** What it wears, and the bag's count of changes when it last looked: see {@link #lendFrom}. */
    private uz.dukeengine.rpg.item.LootBag wornFrom;
    private int wornVersion = -1;
    /** The souls a {@code SOUL_DRINK} it has drunk: every enemy its bearer killed while it had one. */
    private int souls;
    /**
     * A {@code SKEWER} under way: how far it has left to go, from where it set off (its side of a seal), what each
     * one caught takes, and who it has caught, in the order it caught them. On the book, so a frame taken from the
     * middle of one goes on from where it stood.
     */
    private static final class Charge {
        final Skill skill;
        final float each;
        final boolean critical;
        float left;
        final Coord3D from;
        final List<ObjectId> caught = new java.util.ArrayList<>();

        Charge(Skill skill, float each, boolean critical, float distance, Coord3D from) {
            this.skill = skill;
            this.each = each;
            this.critical = critical;
            this.left = distance;
            this.from = from;
        }
    }

    private Charge charge;

    /** Whether a charge is carrying him: his legs are not his own until it ends. */
    @Override
    public boolean keepsBusy() {
        return charge != null;
    }

    /**
     * One frame of the charge: he drives on {@code ProjectileSpeed} a second — the whole way at once where it names
     * none — catching every enemy within {@code Radius} of him, each hurt once as it is caught, and holding all of them
     * at his front as he goes. Stone, a seal, his fall or a stun end it where he stands; so does the end of its
     * {@code Distance}. What it caught is dropped at his front then, spread, stunned {@code StunFrames}.
     */
    private void driveTheCharge() {
        var owner = getOwner();
        var world = owner.getWorld();
        var going = charge;
        if (world == null || owner.isEffectivelyDead() || owner.hasStatus(ObjectStatus.DISABLED)) {
            endTheCharge(world);
            return;
        }
        float speed = going.skill.projectileSpeed();
        float step = speed <= 0f ? going.left : Math.min(going.left, speed * uz.dukeengine.core.GameConstants
                .SECONDS_PER_LOGICFRAME);
        float facing = owner.getOrientation();
        var here = owner.getPosition();
        // In steps no longer than a dash's, so a fast charge does not jump a wall or a body.
        for (float gone = 0f; gone < step; ) {
            float bit = Math.min(DASH_STEP, step - gone);
            var at = new Coord3D(here.x() + (float) StrictMath.cos(facing) * bit,
                    here.y() + (float) StrictMath.sin(facing) * bit, 0f);
            var next = new Coord3D(at.x(), at.y(), world.groundHeight(at));
            if (world.isGroundBlocked(next) || seal.parts(world, going.from, next)
                    || world.findBlocker(owner, next, thing -> going.caught.contains(thing.getId())) != null) {
                endTheCharge(world);
                return;
            }
            owner.setPosition(next);
            here = next;
            gone += bit;
            going.left -= bit;
            catchWhatHeMeets(world, going);
        }
        holdThemAtHisFront(world, going, 2);
        if (going.left <= 0.0001f) {
            endTheCharge(world);
        }
    }

    private void catchWhatHeMeets(World world, Charge going) {
        var owner = getOwner();
        for (var victim : enemiesWithin(owner, world, owner.getGeometry().footprintRadius() + going.skill.radius())) {
            if (going.caught.contains(victim.getId())) {
                continue;
            }
            going.caught.add(victim.getId());
            hurt(owner, victim, going.each, going.critical);
            drink(owner, going.each);
        }
    }

    /** Everyone caught moved to his front, spread, their legs stopped and held still {@code frames}. */
    private void holdThemAtHisFront(World world, Charge going, int frames) {
        var owner = getOwner();
        float reach = owner.getGeometry().footprintRadius() + going.skill.radius();
        var at = owner.getPosition();
        var front = new Coord3D(at.x() + (float) StrictMath.cos(owner.getOrientation()) * reach,
                at.y() + (float) StrictMath.sin(owner.getOrientation()) * reach, at.z());
        for (var id : going.caught) {
            var victim = world.findObject(id);
            if (victim == null || victim.isEffectivelyDead()) {
                continue;
            }
            placeNear(victim, world, front, going.from, owner.getOrientation());
            daze(victim, frames);
        }
    }

    /** The charge over: what it caught dropped at his front and stunned, and his legs his own again. */
    private void endTheCharge(World world) {
        var going = charge;
        charge = null;
        if (world != null && going != null) {
            holdThemAtHisFront(world, going, going.skill.stunFrames());
        }
    }

    /** Each skill's rank, by key, as the game says it — see {@link #setRank}. */
    private final java.util.Map<Character, Integer> ranks = new java.util.HashMap<>();
    /** Whether a {@code DRAGON_BLOOD} of its holds its bearer's damage scale, and the health it owes him. */
    private boolean holdsTheScale;
    private float bloodOwed;
    /** Whether an {@code UNYIELDING} of its has held its bearer's floor, and whether he has been beaten to it. */
    private boolean holdsTheFloor;
    private boolean beaten;

    public SkillBook(GameObject owner, List<Skill> skills, Rules rules, Seal seal) {
        super(owner);
        this.skills = List.copyOf(skills);
        this.cooldowns = new int[skills.size()];
        this.rules = rules;
        this.seal = seal;
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
        int was = maxMana;
        resize(max, tenthsPerSecond);
        if (was > 0 && maxMana > was) {
            mana = Math.min(maxMana, mana + (maxMana - was));
        }
    }

    /**
     * The same pool made bigger or smaller with nothing given: a bigger one is only room, and what no longer fits a
     * smaller one spills. What he carries resizes his pool this way — were it a gift, putting a manastone down and
     * picking it up again would be a drink.
     */
    public void resize(int max, int tenthsPerSecond) {
        this.maxMana = Math.max(0, max);
        this.manaTenthsPerSecond = Math.max(0, tenthsPerSecond);
        this.usesMana = this.maxMana > 0;
        mana = Math.min(mana, maxMana);
    }

    /** Fill him up: a new run, or a floor he has just walked onto. */
    public void fillMana() {
        mana = maxMana;
        manaCarry = 0;
    }

    /** Give some back, up to the brim -- a potion, or something he killed. */
    public void restoreMana(int points) {
        if (points > 0) {
            mana = Math.min(maxMana, mana + points);
        }
    }

    public int getMana() {
        return mana;
    }

    /** How fast it comes back, in tenths of a point a second. */
    public int getManaRegen() {
        return manaTenthsPerSecond;
    }

    /** The frame a cast was last refused for want of mana; 0 if none ever was. */
    public int getRefusedForManaFrame() {
        return refusedForManaFrame;
    }

    public int getMaxMana() {
        return maxMana;
    }

    /** Whether he could pay for the skill on {@code key} at this rank right now. */
    public boolean canAfford(char key, int level) {
        int slot = slotOf(key);
        return slot < 0 || !usesMana || mana >= skills.get(slot).manaAt(level);
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
        var world = getOwner().getWorld();
        if (world != null) {
            summoned.removeIf(id -> {
                var one = world.findObject(id);
                return one == null || one.isDestroyed() || one.isEffectivelyDead();
            });
        }
        return summoned.size();
    }

    /** A rift of its own has landed: what climbed out counts in its place. */
    public void risenInPlaceOf(ObjectId rift, ObjectId risen) {
        int at = summoned.indexOf(rift);
        if (at >= 0) {
            summoned.set(at, risen);
        }
    }

    /** Frames of extra damage left, for anything that wants to draw it. */
    public int getBoostFrames() {
        return boostFrames;
    }

    /**
     * Lend it {@code value} of {@code kind}, holding through frame {@code until}, from the creature whose object id is
     * {@code source}. It holds one buff for each kind and lender, so a lender giving again replaces its own -- where it
     * stands in the list, and whatever it was worth before: the latest word is the lender's.
     */
    public void give(SkillEffect kind, int source, int value, int until) {
        var buff = new Buff(kind, source, value, until);
        for (int at = 0; at < buffs.size(); at++) {
            var held = buffs.get(at);
            if (held.kind() == kind && held.source() == source) {
                buffs.set(at, buff);
                return;
            }
        }
        buffs.add(buff);
    }

    /**
     * The figure of {@code kind} it holds on {@code frame}: the largest worth among the buffs of that kind that still
     * hold then, and 0 for none. The largest and never the sum, so that a stronger lender leaving hands over to a
     * weaker one still in reach with no frame between that is worth nothing; whole numbers, so it is the same in any
     * order.
     */
    public int buffOf(SkillEffect kind, int frame) {
        int largest = 0;
        for (var buff : buffs) {
            if (buff.kind() == kind && buff.until() >= frame) {
                largest = Math.max(largest, buff.value());
            }
        }
        return largest;
    }

    /**
     * What it holds, in the order the buffs were first given. One whose last frame has passed is still in it until the
     * next {@link #update} lets go of it.
     */
    public List<Buff> getBuffs() {
        return List.copyOf(buffs);
    }

    /** The frame he last committed to a skill, or a long time ago if he never has. */
    public int getLastCastFrame() {
        return lastCastFrame;
    }

    /** What his last cast pointed his weapon at, or {@code null}. */
    public ObjectId getLastAimedAt() {
        return lastAimedAt;
    }

    /** Where his last cast wants drawing, and as what. Empty for most of a run. */
    public List<CastMark> getCastMarks() {
        return List.copyOf(castMarks);
    }

    /** When they were put there. Drawing only -- see the field's note. */
    public int getCastMarkFrame() {
        return castMarkFrame;
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
        if (level > 0 && wornFrom != null) {
            level += wornFrom.skillRanks(key); // what he wears raises a skill he has learnt, while he wears it
        }
        int slot = slotOf(key);
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
        if (usesMana && mana < skill.manaAt(level)) {
            // Refused before anything at all happens, which is the whole of what
            // "cannot afford" has to mean: no cooldown started, no mana taken,
            // no effect drawn and no gesture made. A skill that goes through the
            // motions and then does nothing is a skill the player believes he
            // cast.
            var world = getOwner().getWorld();
            refusedForManaFrame = world == null ? 0 : world.getFrame();
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
        if (!apply(skill, level, owner, world, at, towards)) {
            return false; // aimed at nothing it could reach; the cooldown is not spent
        }
        rememberTheCast(skill, owner, stood, towards);
        spend(slot, skill, level);
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

    /** Pay for the cast, and start the slot's cooldown. */
    private void spend(int slot, Skill skill, int level) {
        if (usesMana) {
            mana = Math.max(0, mana - skill.manaAt(level));
        }
        cooldowns[slot] = Math.max(Skill.MIN_COOLDOWN_FRAMES, skill.cooldownAt(level));
    }

    /**
     * Where this cast wants drawing.
     *
     * <p>Worked out from the effect rather than written in the file, because the
     * effect is what knows: a blast round him is drawn round him whatever its
     * numbers say, and there is no useful sense in which one hero's nova is
     * centred somewhere else. The same argument the client's aiming ring makes for
     * the rings the player aims with -- and it means a hero added next month gets
     * his effects by naming a block, with nothing here to keep in step.
     */
    private void rememberTheCast(Skill skill, GameObject owner, Coord3D stood, Coord3D towards) {
        castMarks.clear();
        if (!skill.hasLook()) {
            return;
        }
        var here = owner.getPosition();
        switch (skill.effect()) {
            // Round him, as wide as it actually reached.
            case AREA_DAMAGE -> markOn(skill, owner, skill.radius());
            // On him and no wider than the block says: these are about HIM, and a
            // ring the size of a room would claim they were about the room. His
            // rather than the floor's, for the same reason.
            case EMPOWER, GUARD, STRIKE, SKILLSHOT, WAVE -> markOn(skill, owner, 0f);
            // As wide as the pull reached.
            case PULL -> markOn(skill, owner, skill.radius());
            // Where he put it. Where a skillshot LANDS is its own burst and is
            // drawn by whatever it ran into, which is the point of a lane.
            case AREA_AT_SPOT -> mark(skill, towards == null ? here
                    : withinReach(owner, towards, skill.range()), skill.radius());
            // The warning circle, as wide as the blast that is coming.
            case METEOR -> mark(skill, towards == null ? here
                    : withinReach(owner, towards, skill.range()), skill.radius());
            // Both ends. For a dash it is the two feet of the run; for a blink it
            // is the whole of what the skill looks like.
            case DASH, BLINK, SKEWER -> {
                // The spot he left is a place and stays one -- it is the dust he
                // kicked up, and dust does not follow the man.
                mark(skill, stood, 0f);
                markOn(skill, owner, 0f);
            }
        }
    }

    /** A mark on a patch of floor, which stays there whatever happens next. */
    private void mark(Skill skill, Coord3D at, float radius) {
        remember(new CastMark(skill.look(), at.x(), at.y(), radius, ObjectId.INVALID));
    }

    /** A mark on a creature, which is his and goes where he goes. */
    private void markOn(Skill skill, GameObject owner, float radius) {
        var at = owner.getPosition();
        remember(new CastMark(skill.look(), at.x(), at.y(), radius, owner.getId()));
    }

    private void remember(CastMark mark) {
        castMarks.add(mark);
        var world = getOwner().getWorld();
        castMarkFrame = world == null ? castMarkFrame : world.getFrame();
    }

    /** @return whether it went off, which an aimed skill may decline */
    private boolean apply(Skill skill, int level, GameObject owner, World world,
            ObjectId at, Coord3D towards) {
        switch (skill.effect()) {
            case STRIKE -> {
                var victim = at == null
                        ? nearestEnemy(owner, world, skill.range())
                        : aimedAt(owner, world, at, skill.range());
                if (at != null && victim == null) {
                    return false;
                }
                if (victim == null) {
                    break; // nothing in reach; he has still spent the cast
                }
                if (skill.windUpFrames() > 0) {
                    beginDrawing(skill, level, owner, victim);
                } else {
                    land(skill, level, owner, victim);
                }
            }
            case AREA_DAMAGE -> {
                float each = damageOf(skill, level);
                strikeAround(owner, world, each, skill.radius());
                // A blast that also chills, if the file asks for one. A third
                // effect would have been the obvious move and the wrong one: this
                // IS the area skill, with one more thing true of it, and the
                // knight's whirlwind is untouched by having said nothing about it.
                chill(owner, world, skill.radius(), skill.slowFrames());
                if (skill.lasts()) {
                    // It has landed once already; the rest is the file's business.
                    // Re-casting refreshes rather than stacking, as EMPOWER does.
                    lastingFrames = skill.durationFrames();
                    lastingEvery = skill.tickFrames();
                    lastingNext = skill.tickFrames();
                    lastingDamage = each;
                    lastingRadius = skill.radius();
                    lastingLook = skill.look();
                }
            }
            case AREA_AT_SPOT -> {
                if (towards == null) {
                    return false; // it has to be put somewhere; nowhere is not a spot
                }
                // Pulled back to the edge of his reach rather than refused. The
                // client draws the ring and clamps the click to it, so a click
                // outside is a player asking for "as far that way as I can" --
                // and the two have to agree or the picture is a lie.
                var spot = withinReach(owner, towards, skill.range());
                float each = damageOf(skill, level);
                for (var victim : enemiesWithin(owner, world, spot, skill.radius())) {
                    hurt(owner, victim, each);
                    drink(owner, each);
                }
                // And leaves whoever it caught dragging his feet, if the file asks: the
                // mage's frost nova, dropped where he points rather than round himself.
                chill(owner, world, spot, skill.radius(), skill.slowFrames());
                world.post(new WeaponFired(world.getFrame(), owner.getId(), null,
                        owner.getPosition(), spot));
            }
            case WAVE -> {
                if (towards == null) {
                    return false;
                }
                Facing.turnToward(owner, towards);
                if (!Arrows.waveAlong(owner, towards, damageOf(skill, level), DamageType.NORMAL, skill.projectile(),
                        speedOf(skill), rules.muzzleOffset(), skill.distance(), skill.hitWidth(),
                        skill.stunFrames(), rolledCritical)) {
                    return false; // no wave to throw; the cooldown is not spent
                }
                world.post(new WeaponFired(world.getFrame(), owner.getId(), null, owner.getPosition(), towards));
            }
            case SKEWER -> {
                if (towards != null) {
                    Facing.turnToward(owner, towards);
                }
                var legs = owner.getLocomotor();
                if (legs != null) {
                    legs.stop();
                }
                float each = damageOf(skill, level);
                charge = new Charge(skill, each, rolledCritical, reachOf(skill, owner, towards),
                        owner.getPosition());
                // A charge of no speed is over in the frame it is cast, as a dash is.
                if (skill.projectileSpeed() <= 0f) {
                    while (charge != null) {
                        driveTheCharge();
                    }
                }
            }
            case PULL -> {
                var point = pullPoint(owner, world, skill.range());
                float each = damageOf(skill, level);
                for (var victim : enemiesWithin(owner, world, skill.radius())) {
                    placeNear(victim, world, point, owner.getPosition(), Float.NaN);
                    hurt(owner, victim, each);
                    drink(owner, each);
                    daze(victim, skill.stunFrames());
                }
            }
            case SKILLSHOT -> {
                if (towards == null) {
                    return false;
                }
                Facing.turnToward(owner, towards);
                // Radius is the burst where it lands, and zero leaves it an arrow:
                // both fly the same way and stop at the first body, and one of them
                // takes the rest of the room with it. StunFrames is how long whoever
                // it hurts stands dazed after, and zero leaves them free.
                if (!Arrows.looseAlong(owner, towards, damageOf(skill, level), DamageType.NORMAL,
                        skill.projectile(), speedOf(skill),
                        rules.muzzleOffset(), skill.range(), skill.radius(),
                        skill.stunFrames(), rolledCritical)) {
                    return false; // no arrow to throw; the cooldown is not spent
                }
                world.post(new WeaponFired(world.getFrame(), owner.getId(), null,
                        owner.getPosition(), towards));
            }
            case BLINK -> {
                if (towards == null) {
                    return false;
                }
                var landing = somewhereHeCanStand(owner, world,
                        withinReach(owner, towards, skill.distance()));
                if (landing == null || seal.parts(world, owner.getPosition(), landing)) {
                    // Nowhere along that line is floor, or where it comes down is over
                    // a seal (see {@link uz.dukeengine.rpg.Seal}); the cast is not spent.
                    return false;
                }
                var leaving = owner.getPosition();
                Facing.turnToward(owner, landing);
                owner.setPosition(landing);
                // Both ends, and the first of them read before he moves -- the
                // client flashes the place he left as well as the place he
                // arrived, and half a blink is a teleport with a bug.
                world.post(new WeaponFired(world.getFrame(), owner.getId(), null,
                        leaving, landing));
            }
            case METEOR -> {
                if (towards == null) {
                    return false;
                }
                var spot = withinReach(owner, towards, skill.range());
                if (!callDown(owner, world, skill, level, spot)) {
                    return false; // no such thing to drop; the cooldown is not spent
                }
            }
            case HEAL -> {
                if (!mend(owner, world, skill, at)) {
                    return false; // nobody it may mend; the cooldown is not spent
                }
            }
            case SUMMON -> {
                if (!summon(owner, world, skill, towards)) {
                    return false; // no room left, or no floor to open a rift on
                }
            }
            case HASTE -> {
                // The sturdiest of its own near it, or itself -- never nobody. Given to the hastened's own book as a
                // buff from the caster, holding DurationFrames with this frame the first: a second haste from the same
                // caster starts it again, and the strongest of those that hold is the one that counts, never the sum.
                var hastened = sturdiestNear(owner, world, skill.range());
                var its = hastened.findModule(SkillBook.class);
                its.give(SkillEffect.HASTE, owner.getId().value(), skill.boostAt(level),
                        world.getFrame() + skill.durationFrames() - 1);
                // It turns to the one it hastens and is seen casting, as a mending is; and the one it hastens wears
                // the haste's look for as long as it lasts -- the game gives the look its DurationFrames,
                // and its layers say Renews, which carries it on to a second haste's end.
                Facing.turnToward(owner, hastened);
                world.post(new WeaponFired(world.getFrame(), owner.getId(), null,
                        owner.getPosition(), hastened.getPosition()));
                if (skill.hasLook()) {
                    world.effect(skill.look(), hastened);
                }
            }
            case DASH -> {
                float facing = owner.getOrientation();
                if (towards != null) {
                    // Face where he was sent before he goes, so the model and the
                    // travel agree — and so the next thing he does looks that way.
                    Facing.turnToward(owner, towards);
                }
                var from = owner.getPosition();
                var end = dashEnd(owner, world, reachOf(skill, owner, towards));
                if (seal.parts(world, from, end)) {
                    // It comes down over a seal (see {@link uz.dukeengine.rpg.Seal}): refused, as a blink into
                    // a pillar is -- nothing spent, and not so much as turned. No charge
                    // crossing it, the trample below only ever finds his own side.
                    owner.setOrientation(facing);
                    return false;
                }
                owner.setPosition(end);
                // A charge hurts what it goes through; a sprint does not. Which of
                // the two it is, is a number in the file rather than a second
                // effect here — so the archer's sprint is untouched by having said
                // nothing about damage.
                if (skill.damage() > 0f) {
                    trample(owner, world, from, damageOf(skill, level), skill.radius());
                }
            }
            case EMPOWER -> {
                // Re-casting refreshes rather than stacking: two overlapping copies
                // of the same buff is a question with no obvious answer, and the
                // cooldown already decides how often it can be had.
                boostFrames = skill.durationFrames();
                boostPercent = skill.boostAt(level);
            }
            case GUARD -> {
                // The same bargain as EMPOWER above, and the same refresh. What it
                // is worth reaches his body through HeroProgress, which is the one
                // place that computes armour out of everything that goes into it —
                // his level, what he has found, what he was born in, and this.
                guardFrames = skill.durationFrames();
                guardPercent = Math.clamp(skill.boostAt(level), 0, 100);
            }
        }
        return true;
    }

    /**
     * Take aim. Nothing is hurt yet — that is the whole point of a wind-up.
     *
     * <p>He turns to the target and his weapon is pointed at it, so the archer on
     * screen is visibly drawing on something rather than standing idle while a
     * monster's health drops for no reason the player can see. Pointing the weapon
     * is not a cheat: he really is aiming at it, and if it is inside his ordinary
     * range he would have been shooting at it anyway.
     */
    private void beginDrawing(Skill skill, int level, GameObject owner, GameObject victim) {
        drawing = skill;
        drawnAtLevel = level;
        drawnFor = victim.getId();
        loosesIn = skill.windUpFrames();
        Facing.turnToward(owner, victim);
        var weapon = owner.findModule(WeaponUpdate.class);
        if (weapon != null) {
            weapon.attack(victim.getId());
            // Pointing his weapon is part of drawing, not an order. Said out loud
            // so his brain does not read it as one and send him chasing.
            lastAimedAt = victim.getId();
        }
    }

    /**
     * The drawn shot goes.
     *
     * <p>Its target may have died while he was drawing, and then the shot is simply
     * lost — he committed when he pressed the key, and the cooldown went with it.
     * That is the cost of a skill that takes time, and it is the reason it hits
     * harder than the one that does not.
     */
    private void looseTheDrawnShot() {
        var skill = drawing;
        var owner = getOwner();
        var world = owner.getWorld();
        drawing = null;
        if (world == null || owner.isEffectivelyDead()) {
            return;
        }
        var victim = world.findObject(drawnFor);
        if (victim == null || victim.isEffectivelyDead() || victim.getBody() == null) {
            return;
        }
        Facing.turnToward(owner, victim);
        land(skill, drawnAtLevel, owner, victim);
    }

    /**
     * Deal a strike's damage — as an arrow if the skill has one, otherwise where
     * the victim stands.
     *
     * <p>The figure is settled here rather than when the key was pressed, the same
     * instant an ordinary shot settles its own: what he is worth is what he is
     * worth as the string leaves his fingers.
     */
    private void land(Skill skill, int level, GameObject owner, GameObject victim) {
        float damage = damageOf(skill, level);
        if (skill.hasProjectile() && Arrows.loose(owner, victim, damage, DamageType.NORMAL,
                skill.projectile(), speedOf(skill), rules.muzzleOffset(), rolledCritical)) {
            // The client draws a muzzle flash and plays the shooting sound off this
            // — the same moment the bow announces, for the same reason.
            var world = owner.getWorld();
            world.post(new WeaponFired(world.getFrame(), owner.getId(), victim.getId(),
                    owner.getPosition(), victim.getPosition()));
            return;
        }
        hurt(owner, victim, damage);
        drink(owner, damage);
    }

    /**
     * What a skill hits for: its own figure at this rank, the ultimate's window if
     * one is open, and how much harder its caster's level makes it. Only a monster
     * carries a level of its own, so a hero's figure is untouched by that last.
     */
    private float damageOf(Skill skill, int rank) {
        // A critical strike rolls on a skill's damage as on a blow, once a cast.
        var owner = getOwner();
        float rolled = uz.dukeengine.combat.module.BlowRoll.of(owner, owner.getWorld());
        rolledCritical = rolled > 1f;
        return skill.damageAt(rank) * damageMultiplier() * bonusOf(owner) * rolled;
    }

    /**
     * Its level's bonus, read here as well as by the weapon. A skill deals its own
     * damage rather than going through a weapon, so without asking it would hit as
     * hard at the thirtieth level as at the first.
     */
    private static float bonusOf(GameObject owner) {
        var bonus = owner.findModule(LevelBonus.class);
        return bonus == null ? 1f : bonus.damageMultiplier();
    }

    /** How fast what a skill throws travels: its own figure, or the drawn arrow's. */
    private float speedOf(Skill skill) {
        return skill.projectileSpeed() > 0f ? skill.projectileSpeed() : rules.shotSpeed();
    }

    /**
     * The creature the player clicked, if it is still something he may hit from
     * where he stands. Everything the un-aimed path checks, asked of one named
     * thing instead of all of them.
     */
    private GameObject aimedAt(GameObject owner, World world, ObjectId at, float range) {
        var victim = world.findObject(at);
        if (victim == null || victim.getBody() == null || victim.isEffectivelyDead()) {
            return null;
        }
        if (world.getRelationship(owner.getPlayerIndex(), victim.getPlayerIndex())
                != Relationship.ENEMIES
                || seal.parts(world, owner.getPosition(), victim.getPosition())) {
            return null;
        }
        return World.reachBetween(owner, victim) <= range ? victim : null;
    }

    /**
     * A chosen spot, pulled back to the edge of what the skill can reach.
     *
     * <p>The same rule the client's ring draws, written once more here because the
     * simulation cannot take the client's word for anything: a command arrives
     * from a machine that may be running a different version of the game, or none.
     * What it must not do is <em>refuse</em> — the picture on screen says "as far
     * that way as you can", and the two have to say the same thing.
     */
    private static Coord3D withinReach(GameObject owner, Coord3D wanted, float reach) {
        var from = owner.getPosition();
        float away = from.distance(wanted);
        if (away <= reach || away <= 0.0001f) {
            return wanted;
        }
        float share = reach / away;
        return new Coord3D(from.x() + (wanted.x() - from.x()) * share,
                from.y() + (wanted.y() - from.y()) * share, wanted.z());
    }

    /**
     * How far a dash actually carries: the skill's distance, or the spot he was
     * pointed at if that is nearer. Sent two steps away he takes two steps —
     * being flung the full distance past a click is not what the click said.
     */
    private static float reachOf(Skill skill, GameObject owner, Coord3D towards) {
        return towards == null ? skill.distance()
                : Math.min(skill.distance(), owner.getPosition().distance(towards));
    }

    /**
     * The nearest living enemy within {@code range}, measured surface to surface
     * as weapons measure it. Ties go to the lower object id — not for fairness but
     * for reproducibility: two equidistant skeletons must not be chosen by
     * whichever the world happens to list first.
     */
    private GameObject nearestEnemy(GameObject owner, World world, float range) {
        GameObject best = null;
        float bestReach = Float.MAX_VALUE;
        for (var candidate : enemiesWithin(owner, world, range)) {
            float reach = World.reachBetween(owner, candidate);
            if (reach < bestReach
                    || (reach == bestReach && candidate.getId().value() < best.getId().value())) {
                best = candidate;
                bestReach = reach;
            }
        }
        return best;
    }

    /**
     * Whom a haste is for: of {@code caster}'s own side, living, carrying a book, within {@code range} of it, middle to
     * middle, on its side of a seal (see {@link uz.dukeengine.rpg.Seal}) and in its plain sight -- itself always among
     * them -- the one of the highest level, then the most health at its fullest, then the nearer, then the one the
     * world made first. Sturdiest rather than hardest-hitting: health is on every body, and a blow's worth is not. A
     * total order ending in the object id, so every machine picks the same one.
     */
    private GameObject sturdiestNear(GameObject caster, World world, float range) {
        var here = caster.getPosition();
        var sturdier = java.util.Comparator.<GameObject>comparingInt(LevelBonus::levelOf)
                .thenComparingDouble(one -> one.getBody().getMaxHealth())
                .thenComparingDouble(one -> -here.distance(one.getPosition()))
                .thenComparingInt(one -> -one.getId().value());
        return world.objectsInRange(here, range, one -> one.getPlayerIndex() == caster.getPlayerIndex()
                        && one.getBody() != null && !one.isEffectivelyDead()
                        && one.findModule(SkillBook.class) != null
                        && !seal.parts(world, here, one.getPosition()) && SightLine.clear(caster, one))
                .stream().max(sturdier).orElse(caster);
    }

    private List<GameObject> enemiesWithin(GameObject owner, World world, float radius) {
        return enemiesWithin(owner, world, owner.getPosition(), radius);
    }

    /**
     * The same, round a spot the player chose rather than round the caster -- and only on the caster's side of the
     * seal, wherever the spot is: dropped into the court from outside, it catches nobody in it.
     */
    private List<GameObject> enemiesWithin(GameObject owner, World world, Coord3D centre,
            float radius) {
        int player = owner.getPlayerIndex();
        var casterAt = owner.getPosition();
        return world.objectsInRange(centre, radius, candidate ->
                candidate.getBody() != null
                        && !candidate.isEffectivelyDead()
                        && world.getRelationship(player, candidate.getPlayerIndex())
                                == Relationship.ENEMIES
                        && !seal.parts(world, casterAt, candidate.getPosition()));
    }

    /**
     * Where the caster comes down.
     *
     * <p>He goes <em>over</em> what is between: a wall in the way is a wall he
     * clears, and a skeleton in the way is one he is past before it can turn
     * round. That is the whole point of the skill — an escape that any corridor
     * could cancel is not an escape, and a dash that stops at the first thing it
     * meets stops at the thing it was meant to get away from.
     *
     * <p>What he may not do is land inside something. So the arc is walked from
     * the far end back, and he comes down on the first clear spot at or before
     * where he was pointed: aimed into rock he falls short of it rather than into
     * it, and a unit whose centre is in stone stays the bug it was the day it took
     * an afternoon to find.
     */
    private static Coord3D dashEnd(GameObject owner, World world, float distance) {
        float facing = owner.getOrientation();
        float dx = (float) StrictMath.cos(facing);
        float dy = (float) StrictMath.sin(facing);
        var from = owner.getPosition();
        for (float gone = distance; gone >= DASH_STEP; gone -= DASH_STEP) {
            var overThere = new Coord3D(from.x() + dx * gone, from.y() + dy * gone, from.z());
            // On the floor he lands on, not the one he left. He clears whatever is
            // between, and that can include a step up: coming down on the storey
            // he took off from would put him inside its floor.
            var step = new Coord3D(overThere.x(), overThere.y(), world.groundHeight(overThere));
            if (!world.isGroundBlocked(step) && world.findBlocker(owner, step) == null) {
                return step;
            }
        }
        return from; // nowhere to come down: he stays where he is
    }

    /**
     * Leave whoever the blast caught dragging his feet.
     *
     * <p>Through the engine's own timed-status module rather than a list kept
     * here, and that is why it is four lines: {@code StatusUpdate} already counts
     * a status down and clears it, {@code MoveUpdate} already halves the step of
     * anything wearing {@code SLOWED}, and a monster that carries its own timer
     * thaws by itself -- including after the hero who chilled it is dead, which a
     * list kept in his skill book would have got wrong.
     *
     * <p>A creature whose file never asked for a {@code StatusUpdate} simply does
     * not slow. Better that than the game deciding what a creature is made of
     * behind its own file's back.
     */
    private void chill(GameObject owner, World world, float radius, int frames) {
        chill(owner, world, owner.getPosition(), radius, frames);
    }

    /** The same, round a spot he chose rather than round himself. */
    private void chill(GameObject owner, World world, Coord3D centre, float radius,
            int frames) {
        if (frames <= 0) {
            return;
        }
        for (var victim : enemiesWithin(owner, world, centre, radius)) {
            var timers = victim.findModule(StatusUpdate.class);
            if (timers != null) {
                timers.apply(ObjectStatus.SLOWED, frames);
            }
        }
    }

    /**
     * Where a blink actually puts him: the spot he chose, or the nearest place
     * short of it he could have stood.
     *
     * <p>Walked back from the far end the way a dash's landing is, and for the
     * same reason -- somewhere he could not stand is not somewhere he may appear.
     * What it does not do is stop at the first thing in between: a dash is
     * stopped by the wall, and this is bought precisely to ignore it.
     *
     * @return the landing, or null if nowhere along that line is floor -- in which
     *     case the cast is refused rather than quietly cancelled, so the cooldown
     *     survives a blink into a pillar
     */
    private static Coord3D somewhereHeCanStand(GameObject owner, World world, Coord3D wanted) {
        var from = owner.getPosition();
        float dx = wanted.x() - from.x();
        float dy = wanted.y() - from.y();
        float away = (float) StrictMath.sqrt(dx * dx + dy * dy);
        if (away <= 0.0001f) {
            return null; // he pointed at his own feet; that is not somewhere else
        }
        for (float gone = away; gone >= BLINK_STEP; gone -= BLINK_STEP) {
            float share = gone / away;
            var overThere = new Coord3D(from.x() + dx * share, from.y() + dy * share, from.z());
            // The floor he arrives on, not the one he left -- he crossed a storey
            // as easily as a wall, and coming down on the old height would bury him.
            var step = new Coord3D(overThere.x(), overThere.y(), world.groundHeight(overThere));
            if (!world.isGroundBlocked(step) && world.findBlocker(owner, step) == null) {
                return step;
            }
        }
        return null;
    }

    /**
     * Mark the floor, and start whatever is coming counting down.
     *
     * <p>The mark is a thing in the world and not a line on the caster's screen,
     * so it is spawned exactly as an arrow is: the client draws whatever the world
     * holds, the fog hides it like anything else, and everyone who can see that
     * patch of floor gets the same warning he does. A hint drawn for the caster
     * alone would have been half the skill.
     *
     * @return whether one was really called down; false leaves the cooldown unspent
     */
    private boolean callDown(GameObject owner, World world, Skill skill, int level,
            Coord3D spot) {
        if (!skill.hasProjectile()) {
            return false; // the file named nothing to drop
        }
        var thing = world.findTemplate(skill.projectile());
        if (thing == null) {
            return false;
        }
        var mark = world.spawn(thing,
                new Coord3D(spot.x(), spot.y(), world.groundHeight(spot)),
                owner.getPlayerIndex());
        var falling = mark.findModule(FallingUpdate.class);
        if (falling == null) {
            mark.markDestroyed();
            return false; // the template exists but is not something that falls
        }
        // Worth what it was worth when he called for it, like every other shot
        // here -- he may level, or die, in the second it spends on its way.
        falling.callDown(owner, damageOf(skill, level), skill.radius(), skill.windUpFrames());
        world.post(new WeaponFired(world.getFrame(), owner.getId(), null,
                owner.getPosition(), spot));
        return true;
    }

    /**
     * Call holy light down on one of its own, if he is still someone it may mend.
     *
     * <p>Asked again here rather than taken on the brain's word: the book is what spends
     * the cooldown, and a mending that landed on a whole skeleton or through a wall would
     * be the two of them disagreeing about the rule. See {@link Mending}.
     *
     * <p>Worth what it was worth when it was called for, as every shot here is, and
     * grown by its level as the healer's blows are.
     *
     * @return whether it was called down; false leaves the cooldown unspent
     */
    private boolean mend(GameObject owner, World world, Skill skill, ObjectId at) {
        var patient = at == null ? null : world.findObject(at);
        if (!skill.hasProjectile()
                || !Mending.canMend(owner, patient, skill.range(), skill.healBelowPercent(), seal)) {
            return false;
        }
        var thing = world.findTemplate(skill.projectile());
        if (thing == null) {
            return false;
        }
        var spot = patient.getPosition();
        var light = world.spawn(thing, new Coord3D(spot.x(), spot.y(), world.groundHeight(spot)),
                owner.getPlayerIndex());
        var mending = light.findModule(MendingUpdate.class);
        if (mending == null) {
            light.markDestroyed();
            return false; // the template exists but is not a light that mends
        }
        mending.callDown(patient, skill.heal() * bonusOf(owner), skill.windUpFrames());
        Facing.turnToward(owner, patient);
        world.post(new WeaponFired(world.getFrame(), owner.getId(), null,
                owner.getPosition(), spot));
        return true;
    }

    /**
     * Open rifts for what it calls up: every kind its {@code Summons} names, as many of
     * each as it says -- as many as there is room for under its ceiling, and floor round
     * it to open them on. See {@link Summoning} for where.
     *
     * <p>The kinds take the rifts in the order the file wrote them, so where there is
     * room for fewer than the cast asks, the first kinds are the ones that rise: two
     * swordsmen before two archers.
     *
     * @return whether one opened at all; false leaves the cooldown unspent
     */
    private boolean summon(GameObject owner, World world, Skill skill, Coord3D towards) {
        var rising = new java.util.ArrayList<String>();
        float body = 0f;
        for (var kind : skill.summons().entrySet()) {
            var creature = world.findTemplate(kind.getKey());
            if (creature == null) {
                continue;
            }
            for (int one = 0; one < kind.getValue(); one++) {
                rising.add(kind.getKey());
            }
            body = Math.max(body, uz.dukeengine.core.thing.Solid.of(creature).footprintRadius());
        }
        int room = Math.min(rising.size(), skill.maxSummoned() - summonedStanding());
        var rift = skill.hasProjectile() ? world.findTemplate(skill.projectile()) : null;
        if (room <= 0 || rift == null) {
            return false;
        }
        // Two that rise together stand a body apart -- the widest body of what rises.
        var spots = Summoning.spots(world, owner, towards, skill.radius(), room, 2f * body,
                rules.summonTurnDegrees(), rules.summonTurns(), seal);
        int opened = 0;
        for (var spot : spots) {
            var opening = world.spawn(rift, spot, owner.getPlayerIndex());
            var summoning = opening.findModule(SummoningUpdate.class);
            if (summoning == null) {
                opening.markDestroyed(); // the template exists but is not a rift
                continue;
            }
            summoning.open(owner, rising.get(opened), skill.durationFrames(),
                    skill.summonExperiencePercent(), skill.windUpFrames());
            summoned.add(opening.getId());
            opened++;
        }
        if (opened == 0) {
            return false;
        }
        if (towards != null) {
            Facing.turnToward(owner, towards);
        }
        world.post(new WeaponFired(world.getFrame(), owner.getId(), null,
                owner.getPosition(), spots.get(0)));
        return true;
    }

    /** A blow of his: it names him and his side, so a kill it makes is his to be credited with. */
    private void hurt(GameObject owner, GameObject victim, float amount) {
        hurt(owner, victim, amount, rolledCritical);
    }

    private static void hurt(GameObject owner, GameObject victim, float amount, boolean critical) {
        victim.getBody().damage(amount, DamageType.NORMAL, new uz.dukeengine.core.module.Death(
                uz.dukeengine.core.module.DeathType.NORMAL, owner.getId(), owner.getPlayerIndex(), critical));
    }

    /** Whether the last cast's damage was made critical by its roll — see {@link #damageOf}. */
    private boolean rolledCritical;

    /** The enemies within {@code radius} of the way from {@code from} to {@code to}, each once, in id order. */
    private List<GameObject> swept(GameObject owner, World world, Coord3D from, Coord3D to, float radius) {
        var found = new java.util.LinkedHashSet<GameObject>();
        float length = (float) StrictMath.sqrt((to.x() - from.x()) * (to.x() - from.x())
                + (to.y() - from.y()) * (to.y() - from.y()));
        int samples = Math.max(2, (int) Math.ceil(length / Math.max(1f, radius)));
        for (int sample = 0; sample <= samples; sample++) {
            float along = sample / (float) samples;
            found.addAll(enemiesWithin(owner, world, new Coord3D(from.x() + (to.x() - from.x()) * along,
                    from.y() + (to.y() - from.y()) * along, from.z()), radius));
        }
        var ordered = new java.util.ArrayList<>(found);
        ordered.sort(java.util.Comparator.comparingInt(thing -> thing.getId().value()));
        return ordered;
    }

    /** {@code reach} in front of him, or as near it as there is floor, walked back toward him from the far end. */
    private static Coord3D pullPoint(GameObject owner, World world, float reach) {
        float facing = owner.getOrientation();
        var from = owner.getPosition();
        for (float gone = Math.max(0f, reach); gone > 0f; gone -= DASH_STEP) {
            var at = new Coord3D(from.x() + (float) StrictMath.cos(facing) * gone,
                    from.y() + (float) StrictMath.sin(facing) * gone, 0f);
            var step = new Coord3D(at.x(), at.y(), world.groundHeight(at));
            if (!world.isGroundBlocked(step)) {
                return step;
            }
        }
        return from;
    }

    /**
     * Move {@code victim} to the first spot round {@code centre} — the centre, then rings a body wide, only the half of
     * each facing {@code ahead} where that is a number — where it can stand: not stone, no one in it, on the caster's
     * side of a seal. Left where it is where there is none.
     */
    private void placeNear(GameObject victim, World world, Coord3D centre, Coord3D casterAt, float ahead) {
        float spacing = Math.max(1f, victim.getGeometry().footprintRadius() * 2f);
        boolean onlyAhead = !Float.isNaN(ahead);
        for (int ring = 0; ring <= 6; ring++) {
            int around = ring == 0 ? 1 : ring * 8;
            // Ahead only, for what is carried at a front: straight on first, then either side in turn out to a
            // quarter turn -- never back toward him, where a body would stand in his way.
            var angles = new java.util.ArrayList<Double>();
            if (onlyAhead) {
                double step = 2 * StrictMath.PI / around;
                angles.add((double) ahead);
                for (int k = 1; ring > 0 && k <= around / 4; k++) {
                    angles.add(ahead + k * step);
                    angles.add(ahead - k * step);
                }
            } else {
                for (int i = 0; i < around; i++) {
                    angles.add(2 * StrictMath.PI * i / around);
                }
            }
            for (double angle : angles) {
                var at = new Coord3D(centre.x() + (float) (StrictMath.cos(angle) * ring * spacing),
                        centre.y() + (float) (StrictMath.sin(angle) * ring * spacing), 0f);
                var step = new Coord3D(at.x(), at.y(), world.groundHeight(at));
                // Still things by the grid's rule, and bodies -- the ones already placed among them -- by distance.
                if (!world.isGroundBlocked(step) && world.findBlocker(victim, step) == null
                        && !seal.parts(world, casterAt, step)
                        && world.objectsInRange(step, spacing * 0.9f, other -> other != victim
                                && other.getBody() != null && !other.isEffectivelyDead()).isEmpty()) {
                    victim.setPosition(step);
                    var legs = victim.getLocomotor();
                    if (legs != null) {
                        legs.stop();
                    }
                    return;
                }
            }
        }
    }

    /** Stunned {@code frames}: the engine's {@code DISABLED} on its own timers, as a stunning shot leaves it. */
    private static void daze(GameObject victim, int frames) {
        var timers = frames <= 0 || victim.isEffectivelyDead() ? null : victim.findModule(StatusUpdate.class);
        if (timers != null) {
            timers.apply(ObjectStatus.DISABLED, frames);
        }
    }

    /** Hurt everything of the other side within {@code radius} of him. */
    private void strikeAround(GameObject owner, uz.dukeengine.core.thing.World world,
            float each, float radius) {
        for (var victim : enemiesWithin(owner, world, radius)) {
            hurt(owner, victim, each);
            drink(owner, each);
        }
    }

    /**
     * Hurt whoever he went through, rather than only whoever he landed among.
     *
     * <p>Sampled along the line he travelled instead of taken at either end,
     * because the whole of a charge is the monsters between here and there. Three
     * samples rather than a swept shape: a charge is short, a monster is wide, and
     * an exact sweep would be a lot of arithmetic for a difference nobody could
     * see. Each victim is hurt once however many samples find it.
     *
     * <p>It does not <em>shove</em> anything. Pushing a body out of the way is a
     * question about collision and the navigation grid rather than about a skill,
     * and it is not one this answers.
     */
    private void trample(GameObject owner, uz.dukeengine.core.thing.World world,
            uz.dukeengine.core.math.Coord3D from, float each, float radius) {
        var to = owner.getPosition();
        // Ordered, so the same charge hurts the same monsters in the same order on
        // every machine -- enemiesWithin is already ordered, and this keeps it.
        var struck = new java.util.LinkedHashSet<GameObject>();
        for (int sample = 0; sample <= 2; sample++) {
            float along = sample / 2f;
            var at = new uz.dukeengine.core.math.Coord3D(
                    from.x() + (to.x() - from.x()) * along,
                    from.y() + (to.y() - from.y()) * along,
                    from.z() + (to.z() - from.z()) * along);
            struck.addAll(enemiesWithin(owner, world, at, radius));
        }
        for (var victim : struck) {
            hurt(owner, victim, each);
            drink(owner, each);
        }
    }

    /**
     * How much harder its blows and its skills land: an {@code EMPOWER} while it lasts, and the strongest
     * {@code DAMAGE_AURA} lent to it that still holds -- see {@link #auraOn}. The engine's weapon multiplies it into
     * each blow, and {@link #damageOf} into each skill; a mending is not damage and does not ask.
     */
    @Override
    public float damageMultiplier() {
        float empowered = boostFrames > 0 ? 1f + boostPercent / 100f : 1f;
        // A soul drink is added to the strongest aura, never maxed with it: it is his own, not lent.
        return empowered * (1f + (auraOn(getOwner(), SkillEffect.DAMAGE_AURA) + soulPercent()) / 100f);
    }

    /** Its passives: its own, and those lent by what its bearer wears. */
    private List<Skill> passives() {
        lookAtWhatIsWorn();
        // Its own at a rank the game said is 0 are not held; a worn thing's are held while it is worn.
        var all = new java.util.ArrayList<Skill>(skills.size());
        for (var skill : skills) {
            if (rankOf(skill) > 0) {
                all.add(skill);
            }
        }
        lent.values().forEach(all::addAll);
        return all;
    }

    /**
     * Lend it the passives of whatever {@code bag} has acting on its bearer — what is worn, or every slot of a bag that
     * wears nothing — each thing's {@link uz.dukeengine.rpg.item.Loot#skills}: put on, they are its; taken off, gone.
     * Looked at again whenever the bag changes. What a soul drink has drunk stays with this book, not the thing.
     */
    public void lendFrom(uz.dukeengine.rpg.item.LootBag bag) {
        this.wornFrom = bag;
        this.wornVersion = -1;
    }

    private void lookAtWhatIsWorn() {
        if (wornFrom == null || wornFrom.version() == wornVersion) {
            return;
        }
        wornVersion = wornFrom.version();
        lent.clear();
        for (var item : wornFrom.acting()) {
            // ponytail: only passives are lent; a worn thing that casts would want a slot and a key of its own.
            var passive = item.skills().stream().filter(skill -> skill.effect().isPassive()).toList();
            if (!passive.isEmpty()) {
                lent.merge(item.id(), passive, (was, more) -> java.util.stream.Stream.concat(was.stream(),
                        more.stream()).toList());
            }
        }
    }

    /** Its {@code SOUL_DRINK}, its own or lent, or null: the first. */
    public Skill soulDrink() {
        for (var skill : passives()) {
            if (skill.effect() == SkillEffect.SOUL_DRINK) {
                return skill;
            }
        }
        return null;
    }

    @Override
    public void onCreated() {
        holdTheFloor(); // before the first blow can land
    }

    /**
     * The rank a skill of {@code key} stands at, as the game keeps it (its {@code SkillRanks}): what a passive reads,
     * for a passive is never cast with one. The first where it is never said.
     */
    public void setRank(char key, int rank) {
        ranks.put(Character.toUpperCase(key), rank);
    }

    /** Its rank: what the game said, 0 for not held; the first where it never said. */
    private int rankOf(Skill skill) {
        int learnt = Math.max(0, ranks.getOrDefault(Character.toUpperCase(skill.key()), 1));
        return learnt > 0 && wornFrom != null ? learnt + wornFrom.skillRanks(skill.key()) : learnt;
    }

    /** A {@code DRAGON_BLOOD}'s armour held on his body's damage scale and its health given back, while he has one. */
    private void runTheBlood() {
        var body = getOwner().getBody();
        if (body == null) {
            return;
        }
        Skill blood = null;
        for (var skill : passives()) {
            if (skill.effect() == SkillEffect.DRAGON_BLOOD) {
                blood = skill;
                break;
            }
        }
        if (blood == null) {
            if (holdsTheScale) {
                body.setDamageScale(1f);
                holdsTheScale = false;
            }
            return;
        }
        // ponytail: it owns the damage scale while he has it; a game scaling the same body otherwise is overruled.
        holdsTheScale = true;
        int rank = rankOf(blood);
        body.setDamageScale(1f - Math.clamp(blood.boostAt(rank), 0, 100) / 100f);
        if (body.isDead() || blood.heal() <= 0f) {
            return;
        }
        // Whole points as they are owed, so a heal is told once a point rather than every frame.
        bloodOwed += blood.heal() * rank * uz.dukeengine.core.GameConstants.SECONDS_PER_LOGICFRAME;
        if (bloodOwed >= 1f) {
            float whole = (float) Math.floor(bloodOwed);
            bloodOwed -= whole;
            body.heal(whole);
        }
    }

    private Skill unyielding() {
        for (var skill : passives()) {
            if (skill.effect() == SkillEffect.UNYIELDING) {
                return skill;
            }
        }
        return null;
    }

    /** An {@code UNYIELDING}'s floor kept at its share of his most health, which may grow; let go once it is gone. */
    private void holdTheFloor() {
        var body = getOwner().getBody();
        if (body == null) {
            return;
        }
        var unyielding = unyielding();
        if (unyielding == null) {
            if (holdsTheFloor) {
                body.setLeastHealth(0f);
                holdsTheFloor = false;
            }
            return;
        }
        holdsTheFloor = true;
        body.setLeastHealth(body.getMaxHealth() * unyielding.healBelowPercent() / 100f);
        beaten |= atTheFloor(body);
    }

    private static boolean atTheFloor(uz.dukeengine.core.module.BodyModule body) {
        // A blow cut to the floor lands on it within a float's rounding.
        return body.getHealth() <= body.getLeastHealth() + 1e-3f;
    }

    /**
     * Whether its bearer has been beaten — an {@code UNYIELDING} of its held him at his floor — from the frame the blow
     * landed, and for good: what being beaten does is the game's to say.
     */
    public boolean isBeaten() {
        var body = getOwner().getBody();
        return beaten || holdsTheFloor && body != null && atTheFloor(body);
    }

    /** The souls it has drunk — see {@link SkillEffect#SOUL_DRINK}. */
    public int getSouls() {
        return souls;
    }

    /** The souls it has drunk, put back from a save. */
    public void setSouls(int souls) {
        this.souls = Math.max(0, souls);
    }

    /**
     * What its souls add to every blow and skill, in percent: its {@code SOUL_DRINK}'s {@code BoostPercent} for every
     * {@code TickFrames} souls, up to its {@code Damage} where that is above 0; nothing without one.
     */
    public int soulPercent() {
        var drink = soulDrink();
        if (drink == null || drink.tickFrames() <= 0) {
            return 0;
        }
        long percent = (long) (souls / drink.tickFrames()) * drink.boostPercent();
        return (int) (drink.damage() > 0f ? Math.min(percent, (long) drink.damage()) : Math.min(percent,
                Integer.MAX_VALUE));
    }

    /**
     * A death reaped, told to the book of whoever dealt it: an enemy its bearer killed is a soul for a {@code
     * SOUL_DRINK} it has. For the runtime's watcher of deaths ({@code GameLogic.onDied}); a death nobody dealt, one of
     * the killer's own side, or one dealt by a thing with no soul drink, counts for nothing.
     */
    public static void reaped(World world, uz.dukeengine.core.event.ObjectDied died) {
        var killer = died.killer() == null ? null : world.findObject(died.killer());
        var book = killer == null ? null : killer.findModule(SkillBook.class);
        if (book == null || book.soulDrink() == null
                || world.getRelationship(killer.getPlayerIndex(), died.playerIndex()) != Relationship.ENEMIES) {
            return;
        }
        book.souls++;
    }

    /**
     * The strongest aura of {@code kind} on {@code creature}, 0 for none: the largest worth -- a {@code BoostPercent},
     * or a {@code MANA_AURA}'s tenths of a point a second -- among the buffs of that kind its book holds on this frame,
     * each lent by a bearer of its side that reached it on a pulse (see {@link #lendTheAuras}). Nothing for a creature
     * that carries no book, as one without a {@code StatusUpdate} is not stunned.
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
     * <p>The one rule for what is on a creature, for whatever draws it to ask -- the circle its own book lays under it
     * ({@code wearTheMark}), and the HUD's row over the creature the player picks. It asks {@link #auraOn}, so a
     * picture of what is lent holds exactly while the figures do, and cannot disagree with them. Pure: asked when
     * wanted, nothing kept, and an answer that is the asker's own.
     */
    public static List<SkillEffect> aurasOn(GameObject creature) {
        var book = creature == null ? null : creature.findModule(SkillBook.class);
        if (book == null || creature.isEffectivelyDead()) {
            return List.of();
        }
        var on = new java.util.ArrayList<SkillEffect>();
        for (var kind : SkillEffect.values()) {
            // The pool first: a mana aura is not looked for round a creature it could change nothing on.
            if (kind.isAura() && (kind != SkillEffect.MANA_AURA || book.usesMana) && auraOn(creature, kind) > 0) {
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
        int share = auraOn(creature, SkillEffect.LIFESTEAL_AURA);
        for (var skill : book.passives()) {
            if (skill.effect() == SkillEffect.LIFESTEAL) {
                share += skill.boostAt(book.rankOf(skill));
            }
        }
        return share;
    }

    /** Health given back each second: a {@code DRAGON_BLOOD}'s at its rank, and the strongest {@code HEAL} buff on it. */
    public float healthPerSecond() {
        var world = getOwner().getWorld();
        float meal = world == null ? 0f : buffOf(SkillEffect.HEAL, world.getFrame()) / (float) TENTHS;
        for (var skill : passives()) {
            if (skill.effect() == SkillEffect.DRAGON_BLOOD) {
                return skill.heal() * rankOf(skill) + meal;
            }
        }
        return meal;
    }

    /** Mana that comes back each second: its pool's own trickle and the strongest {@code MANA_AURA} on it. */
    public float manaPerSecond() {
        return usesMana ? (manaTenthsPerSecond + auraOn(getOwner(), SkillEffect.MANA_AURA)) / (float) TENTHS : 0f;
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
        return guardFrames > 0 ? guardPercent : 0;
    }

    /** Frames of protection left, for anything that wants to draw it. */
    public int getGuardFrames() {
        return guardFrames;
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
        return drawing != null;
    }

    /**
     * The trickle, counted in whole points against a frame carry.
     *
     * <p>See the note on {@link #manaCarry} for why it is not a float. In short:
     * a second's worth of frames of the rate is exactly the rate, and no rounding
     * is carried from one second into the next.
     *
     * <p>The rate is its own and the strongest {@code MANA_AURA} lent to it that still
     * holds, read each frame the pool is not full -- see {@link #auraOn}. A pool with no
     * trickle of its own still fills at the aura's; no pool at all is given nothing.
     */
    private void regenerate() {
        int tenths = !usesMana || mana >= maxMana ? 0
                : manaTenthsPerSecond + auraOn(getOwner(), SkillEffect.MANA_AURA);
        if (tenths <= 0) {
            manaCarry = 0;
            return;
        }
        int aSecond = TENTHS * uz.dukeengine.core.GameConstants.LOGICFRAMES_PER_SECOND;
        manaCarry += tenths;
        while (manaCarry >= aSecond && mana < maxMana) {
            manaCarry -= aSecond;
            mana++;
        }
    }

    /**
     * A meal's health: the strongest {@code HEAL} buff on its list — tenths of a point a second, as a {@code MANA_AURA}
     * is for mana — paid in whole points against a frame carry while it holds.
     */
    private void digest() {
        var body = getOwner().getBody();
        var world = getOwner().getWorld();
        int tenths = body == null || world == null || body.isDead() ? 0 : buffOf(SkillEffect.HEAL, world.getFrame());
        if (tenths <= 0) {
            healCarry = 0;
            return;
        }
        int aSecond = TENTHS * uz.dukeengine.core.GameConstants.LOGICFRAMES_PER_SECOND;
        healCarry += tenths;
        int whole = healCarry / aSecond;
        if (whole > 0) {
            healCarry -= whole * aSecond;
            body.heal(whole);
        }
    }

    @Override
    public void update() {
        if (charge != null) {
            driveTheCharge();
        }
        holdTheFloor();
        runTheBlood();
        regenerate();
        digest();
        for (int slot = 0; slot < cooldowns.length; slot++) {
            if (cooldowns[slot] > 0) {
                cooldowns[slot]--;
            }
        }
        if (boostFrames > 0) {
            boostFrames--;
        }
        if (guardFrames > 0) {
            guardFrames--;
        }
        letGoOfTheBuffsThatHavePassed();
        lendTheAuras();
        wearTheAuras();
        wearTheMark();
        turnTheWhirlwind();
        if (drawing != null && --loosesIn <= 0) {
            looseTheDrawnShot();
        }
    }

    /**
     * Every buff whose last frame has gone by is let go of, and the rest keep their places. Nothing hangs on it --
     * {@link #buffOf} counts a buff by the frame it is asked on -- but it keeps the list to what is still held, which
     * is what whatever draws it has to show.
     */
    private void letGoOfTheBuffsThatHavePassed() {
        var world = getOwner().getWorld();
        if (world != null) {
            int frame = world.getFrame();
            buffs.removeIf(buff -> buff.until() < frame);
        }
    }

    /**
     * Its auras are lent: once every Combat {@code AuraPulseFrames}, on the frames its object id falls on -- as its
     * looks are worn, so a roomful of bearers are not all lending on one frame -- each aura its level has opened is
     * given, at the skill's worth ({@code BoostPercent}, or a {@code MANA_AURA}'s {@code ManaRegen}), to everyone of
     * its side within the skill's {@code Radius} of it, middle to middle, through walls but never across the keep's
     * shut gate, living and carrying a book -- itself among them -- to hold for Combat's {@code BuffLifeFrames}, this
     * frame the first. No hero is given one: no bearer stands on a hero's side, a passive being refused on a hero.
     *
     * <p>Stunned, it lends still; fallen, it lends no more, and what it lent runs out by itself. The life outlasts the
     * pulse, so what it lends to one still in reach holds on until the next pulse gives it again, whichever of the two
     * books is updated first on that frame.
     *
     * <p>ponytail: one buff a kind from each lender, so of two auras of one kind on one bearer the later in its file
     * is what is lent; no file gives a bearer two, and giving them weakest first would keep the stronger if one does.
     */
    private void lendTheAuras() {
        var owner = getOwner();
        var world = owner.getWorld();
        int pulse = rules.auraPulseFrames();
        if (world == null || owner.isEffectivelyDead()
                || world.getFrame() % pulse != Math.floorMod(owner.getId().value(), pulse)) {
            return;
        }
        var here = owner.getPosition();
        int until = world.getFrame() + rules.buffLifeFrames() - 1;
        for (var skill : passives()) {
            if (!skill.effect().isAura() || LevelBonus.rankOf(owner, skill) <= 0) {
                continue;
            }
            int worth = skill.effect() == SkillEffect.MANA_AURA ? skill.manaRegen() : skill.boostPercent();
            for (var one : world.objectsInRange(here, skill.radius(),
                    candidate -> candidate.getPlayerIndex() == owner.getPlayerIndex()
                            && !candidate.isEffectivelyDead() && !seal.parts(world, here, candidate.getPosition()))) {
                var book = one.findModule(SkillBook.class);
                if (book != null) {
                    book.give(skill.effect(), owner.getId().value(), worth, until);
                }
            }
        }
    }

    /**
     * Its auras are worn: each its level has opened and that says what it looks like is played on it every
     * {@code TickFrames} -- on the frames its object id falls on, as a brain's re-plans are, so a roomful are not all
     * played on one frame -- for as long as it lives, stunned or not. The game makes each last two
     * ticks, so it rides its bearer while it stands and is gone within two of its fall. An event: out of the checksum.
     */
    private void wearTheAuras() {
        var owner = getOwner();
        var world = owner.getWorld();
        if (world == null || owner.isEffectivelyDead()) {
            return;
        }
        for (var skill : passives()) {
            int beat = skill.tickFrames();
            if (skill.effect().isAura() && skill.hasLook() && beat > 0
                    && world.getFrame() % beat == Math.floorMod(owner.getId().value(), beat)
                    && LevelBonus.rankOf(owner, skill) > 0) {
                world.effect(skill.look(), owner);
            }
        }
    }

    /**
     * The circle under it is worn: every Combat {@code AuraMarkTickFrames} -- on the frames its object id falls on, as
     * {@link #wearTheAuras} does -- it asks {@link #aurasOn} of itself and, if it holds any aura lent to it, plays on
     * itself the look for how many kinds it holds: the first of the Combat block's {@code AuraMarkLooks} for one, the
     * second for two, and past the list's end its last. The game makes each last two beats, so it
     * rides the creature while an aura is lent to it and is gone within two of the last lapsing. A hero's book asks the
     * same and is answered none, as is a creature that has fallen. An event: out of the checksum.
     */
    private void wearTheMark() {
        var owner = getOwner();
        var world = owner.getWorld();
        var looks = rules.auraMarkLooks();
        int beat = rules.auraMarkTickFrames();
        if (world == null || looks.isEmpty() || beat <= 0
                || world.getFrame() % beat != Math.floorMod(owner.getId().value(), beat)) {
            return;
        }
        int kinds = aurasOn(owner).size();
        if (kinds > 0) {
            world.effect(looks.get(Math.min(kinds, looks.size()) - 1), owner);
        }
    }

    /**
     * A lasting {@code AREA_DAMAGE}, landing again.
     *
     * <p>It follows him, which is what makes it a whirlwind rather than a fire on
     * the floor: it is measured from wherever he is standing each time it lands,
     * so walking into a second group carries it with him and walking out of a
     * fight ends it for them.
     */
    private void turnTheWhirlwind() {
        if (lastingFrames <= 0) {
            return;
        }
        lastingFrames--;
        if (--lastingNext > 0) {
            return;
        }
        lastingNext = lastingEvery;
        var owner = getOwner();
        if (owner == null || owner.getWorld() == null) {
            return;
        }
        strikeAround(owner, owner.getWorld(), lastingDamage, lastingRadius);
        // And drawn each time it lands, not only the first. A whirlwind that
        // opened one ring and then turned in silence for four seconds is a skill
        // the player has to count frames to know is still going.
        //
        // The frame is moved on with it, since that is what the client compares
        // against to know it has not drawn this one already.
        if (lastingLook != null && !lastingLook.isBlank()) {
            castMarks.clear();
            remember(new CastMark(lastingLook, owner.getPosition().x(),
                    owner.getPosition().y(), lastingRadius, owner.getId()));
        }
    }
}
