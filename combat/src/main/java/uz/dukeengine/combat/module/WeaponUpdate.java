package uz.dukeengine.combat.module;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.DeathType;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.World;
import uz.dukeengine.combat.message.OrderSource;

/**
 * Fires at a target object, applying damage on a reload cycle — a lean fusion of
 * SAGE's {@code Weapon}/{@code WeaponTemplate}, its {@code WeaponSet} and the
 * {@code AIUpdate} attack state.
 *
 * <p>Given a target (typically from an {@code AttackObject} command), each frame
 * it counts down its reload, and when the target is a valid, non-allied,
 * in-range, living object it deals {@code damage} to the target's body and starts
 * the reload again. It deliberately does not move the owner into range — that is
 * {@link MoveUpdate}'s job — it only fires when able.
 *
 * <p>Targeting reads the world through {@link GameObject#getWorld()}, the role
 * SAGE's global {@code TheGameLogic} plays.
 *
 * <p>A weapon fires on the move unless its data says otherwise. {@code
 * AttackOnTheMove = No} is for the ones that have to be stood still for — a drawn
 * bow, a deployed gun — and it belongs to the weapon rather than to whatever is
 * steering the unit, because a weapon finds its own target and fires in one call:
 * a script that disarmed it would be undone before the script ran again.
 *
 * <p>{@link WeaponHold} is the same idea for a moment rather than a movement: any
 * module on the unit may say it is busy, and the weapon keeps quiet while it is.
 *
 * <p><b>What it may be fired at.</b> A weapon is built for some targets and useless against others: in the RTS
 * this was measured in, 47 of 363 weapons can hit aircraft, 27 cannot hit anything on the ground, and a
 * bulldozer's mine-clearing charge (range 5, damage 1) would otherwise attack every enemy that came near it.
 * So a weapon may name the classes it is fired at ({@link Data#targets}), and the game says which classes each
 * thing has ({@link TargetRule}). Acquiring a target, keeping one, and taking an order to attack one all ask
 * the same question — {@link #canFireAt} — and an order to attack something it cannot hit is refused.
 *
 * <p><b>Its clip.</b> Most RTS weapons fire from a clip, and the clip is the rate of fire: in the RTS this was
 * measured in, 314 of 363 weapons give a clip size, 112 hold one round — so they fire once a reload and never
 * wait their delay — 20 hold thirty (bursts, then a pause), and 13 are refilled only when their aircraft lands.
 * See {@link Clip} for the rule; {@link #getStatus}, {@link #getRounds} and {@link #refill} are the weapon's
 * side of it. Left out, a weapon has no clip and waits {@code ReloadFrames} after every shot, as it always did.
 *
 * <p><b>More than one weapon, and sets of them.</b> A block may write its one weapon in place, as every block
 * did, or give {@link Data#weaponSets}: in the RTS this was measured in, of 556 armed objects 185 carry a second
 * weapon, 65 a third, and 215 more than one set, swapped by a condition — an upgrade bought, a car bomb fitted,
 * a rank. The set in use is the one whose conditions the unit has ({@link GameObject#setCondition}), chosen by
 * the rule conditional models are; its slots link {@link Weapon}s the game gave the world by name. Which slot
 * fires is chosen again every time a target is weighed — see {@code Loadout.choose} — and each weapon keeps its own
 * clip and reload across a swap, so a swap cannot be used to skip one. A block with no sets has one weapon and
 * behaves exactly as it did.
 */
@ModuleGroup(ModuleGroups.COMBAT)
public final class WeaponUpdate extends UpdateModule implements uz.dukeengine.core.save.Saveable {

    /**
     * INI configuration: {@code Damage}, {@code AttackRange}, {@code ReloadFrames},
     * a {@link DamageType}, a {@code SplashRadius} for area damage, and whether the
     * weapon may be used on the move. The shorter forms default the trailing
     * fields, keeping existing call sites working.
     *
     * @param attackOnTheMove  whether it fires while its owner is walking. Yes for
     *     everything by default, which is what an RTS unit does and what every
     *     weapon did before this existed. No is for the weapons that have to be
     *     stood still for — a drawn bow, a deployed siege gun: the owner keeps its
     *     target and keeps reloading, but the shot waits until it stops.
     * @param targets  the classes it may be fired at, words the game's {@link TargetRule}s give things —
     *     {@code Targets = [GROUND, AIRBORNE_VEHICLE]}. None, the default, is anything at all, which is
     *     what every weapon was before this existed; the reference game reads none as ground only, and a
     *     game that wants that says so
     * @param reloadFrames  the wait after a shot that leaves rounds in the clip — after every shot, for a weapon
     *     with no clip. The reference's delay between shots
     * @param reloadFramesMax  the most that wait may be: with this above {@code ReloadFrames}, each wait is drawn
     *     from the two, both included, from the simulation's own random numbers. Left out, the wait is exactly
     *     {@code ReloadFrames} and nothing is drawn
     * @param clipSize  rounds before a reload. 0, the default, is no clip
     * @param clipReloadFrames  how long refilling an emptied clip takes, from the shot that emptied it
     * @param autoReload  whether it refills itself. Yes by default; no, and an emptied clip stays empty —
     *     {@link WeaponStatus#OUT} — until {@link #refill}
     * @param name  the name of the weapon written in place, which is what its shot sounds as
     *     ({@code fired.<name>}); none, the default, is a weapon with no name of its own
     * @param weaponSets  the unit's weapons, set by set. Given, they are its weapons and the one written in
     *     place is not read; a unit none of whose sets fits holds its fire. None, the default, is the one
     *     weapon written in place, as before
     * @param deathType  the death its killing blow deals — {@code DeathType = EXPLODED} — which the victim's
     *     die modules are told and its client sounds and draws by. {@code NORMAL} by default
     * @param targetScanFrames  how often a thing of it with no target looks for one, in frames — the reference's
     *     {@code MoodAttackCheckRate}, written per template (2 seconds there where a template leaves it out). 0, the
     *     default, looks as often as the world says ({@code Armoury.setTargetScanFrames})
     */
    public record Data(float damage, float attackRange, int reloadFrames,
            DamageType damageType, float splashRadius,
            boolean attackOnTheMove, List<String> targets,
            int reloadFramesMax, int clipSize, int clipReloadFrames, boolean autoReload,
            String name, List<WeaponSet> weaponSets, DeathType deathType, int targetScanFrames,
            int preAttackFrames) implements ModuleData {
        /** What a block leaves out: plain damage, no splash, a shot taken on the move, at anything, no clip. */
        static final Data DEFAULTS = new Data(0f, 0f, 0, DamageType.NORMAL, 0f, true, List.of(),
                0, 0, 0, true, null, List.of(), DeathType.NORMAL, 0, 0);

        public Data {
            damageType = damageType == null ? DamageType.NORMAL : damageType;
            deathType = deathType == null ? DeathType.NORMAL : deathType;
            targets = targets == null ? List.of() : List.copyOf(targets);
            weaponSets = weaponSets == null ? List.of() : List.copyOf(weaponSets);
            targetScanFrames = Math.max(0, targetScanFrames);
            preAttackFrames = Math.max(0, preAttackFrames);
        }

        /** A weapon written in place that winds up before no shot, as every one did before one could. */
        public Data(float damage, float attackRange, int reloadFrames, DamageType damageType, float splashRadius,
                boolean attackOnTheMove, List<String> targets, int reloadFramesMax, int clipSize,
                int clipReloadFrames, boolean autoReload, String name, List<WeaponSet> weaponSets,
                DeathType deathType, int targetScanFrames) {
            this(damage, attackRange, reloadFrames, damageType, splashRadius, attackOnTheMove, targets,
                    reloadFramesMax, clipSize, clipReloadFrames, autoReload, name, weaponSets, deathType,
                    targetScanFrames, 0);
        }

        /** A weapon that looks for a target as often as the world says, as every one did before one could say. */
        public Data(float damage, float attackRange, int reloadFrames, DamageType damageType, float splashRadius,
                boolean attackOnTheMove, List<String> targets, int reloadFramesMax, int clipSize,
                int clipReloadFrames, boolean autoReload, String name, List<WeaponSet> weaponSets,
                DeathType deathType) {
            this(damage, attackRange, reloadFrames, damageType, splashRadius, attackOnTheMove, targets,
                    reloadFramesMax, clipSize, clipReloadFrames, autoReload, name, weaponSets, deathType, 0);
        }

        public Data(float damage, float attackRange, int reloadFrames) {
            this(damage, attackRange, reloadFrames, DamageType.NORMAL, 0f);
        }

        public Data(float damage, float attackRange, int reloadFrames, DamageType damageType) {
            this(damage, attackRange, reloadFrames, damageType, 0f);
        }

        public Data(float damage, float attackRange, int reloadFrames, DamageType damageType,
                float splashRadius) {
            this(damage, attackRange, reloadFrames, damageType, splashRadius, true);
        }

        public Data(float damage, float attackRange, int reloadFrames, DamageType damageType,
                float splashRadius, boolean attackOnTheMove) {
            this(damage, attackRange, reloadFrames, damageType, splashRadius, attackOnTheMove, List.of());
        }

        public Data(float damage, float attackRange, int reloadFrames, DamageType damageType,
                float splashRadius, boolean attackOnTheMove, List<String> targets) {
            this(damage, attackRange, reloadFrames, damageType, splashRadius, attackOnTheMove, targets,
                    0, 0, 0, true);
        }

        public Data(float damage, float attackRange, int reloadFrames, DamageType damageType,
                float splashRadius, boolean attackOnTheMove, List<String> targets,
                int reloadFramesMax, int clipSize, int clipReloadFrames, boolean autoReload) {
            this(damage, attackRange, reloadFrames, damageType, splashRadius, attackOnTheMove, targets,
                    reloadFramesMax, clipSize, clipReloadFrames, autoReload, null, List.of());
        }

        public Data(float damage, float attackRange, int reloadFrames, DamageType damageType,
                float splashRadius, boolean attackOnTheMove, List<String> targets,
                int reloadFramesMax, int clipSize, int clipReloadFrames, boolean autoReload,
                String name, List<WeaponSet> weaponSets) {
            this(damage, attackRange, reloadFrames, damageType, splashRadius, attackOnTheMove, targets,
                    reloadFramesMax, clipSize, clipReloadFrames, autoReload, name, weaponSets, DeathType.NORMAL);
        }

        /** A unit whose weapons are these sets, with nothing written in place. */
        public static Data sets(List<WeaponSet> weaponSets) {
            return new Data(0f, 0f, 0, DamageType.NORMAL, 0f, true, List.of(), 0, 0, 0, true, null, weaponSets,
                    DeathType.NORMAL, 0);
        }

        /** The same, a thing of it looking for a target every {@code frames} frames — see {@link #targetScanFrames}. */
        public Data targetScanFrames(int frames) {
            return new Data(damage, attackRange, reloadFrames, damageType, splashRadius, attackOnTheMove, targets,
                    reloadFramesMax, clipSize, clipReloadFrames, autoReload, name, weaponSets, deathType, frames);
        }
    }

    /**
     * One weapon slot of the set in use as it stands: its index in the set, whether it fired, its status, and its clip
     * — what the reference's ammo pips are laid from ({@code WeaponSet::getAmmoPipShowingInfo}): the rounds it holds
     * full, its clip size, 0 for a weapon with no clip, and the rounds it holds now. A clip that reloads itself is
     * full again from the shot that emptied it, as the reference's is from the moment its reload begins ({@code
     * Weapon::reloadWithBonus}): {@link WeaponStatus#RELOADING} is what says the reload is still under way. The
     * weapon it carries is named, for a game to say which of its weapons show their rounds ({@code ShowsAmmoPips}).
     */
    public record SlotNow(int slot, boolean fired, WeaponStatus status, String weapon, int clipSize, int rounds) {

        /** A slot as it was told before its clip could be read: its weapon unnamed, and no clip. */
        public SlotNow(int slot, boolean fired, WeaponStatus status) {
            this(slot, fired, status, null, 0, 0);
        }
    }

    /**
     * How long a lock on one slot holds — the reference's {@code WeaponLockType}. While a lock holds, its slot alone
     * is weighed and fired, whatever the slot's sources.
     */
    public enum Lock {
        /**
         * Until the attack it was given for is over, its slot's clip is emptied, another lock, or a change of the set
         * in use to one that does not share it.
         */
        TEMPORARILY,
        /**
         * Until it is let go, another lock, or a change of the set in use to one that does not share it ({@link
         * WeaponSet#weaponLockSharedAcrossSets}).
         */
        PERMANENTLY
    }

    private final Loadout loadout;
    private final Engagement engagement = new Engagement();
    private final TargetScan scan;
    private final Firing firing;

    public WeaponUpdate(GameObject owner, Data data) {
        super(owner);
        this.loadout = new Loadout(owner, data);
        this.scan = new TargetScan(owner, loadout, data.targetScanFrames());
        this.firing = new Firing(owner, this, loadout, engagement);
    }

    // ---- what a game asks of it ----

    /**
     * Each weapon slot of the set in use: whether it fired in the game's frame {@code frame}, where it stands, and its
     * clip — the same on any frame asked but for {@code fired}.
     */
    public List<SlotNow> slotsNow(int frame) {
        var slots = new ArrayList<SlotNow>();
        for (var one : loadout.armed()) {
            slots.add(new SlotNow(one.index(), firing.firedOn(one.index(), frame), statusOf(one),
                    one.weapon().name(), one.clip().size(), one.clip().rounds()));
        }
        return slots;
    }

    /** Where a slot stands: winding up, or as its clip has it. */
    private WeaponStatus statusOf(Armed one) {
        return one.index() == firing.windingUp() ? WeaponStatus.PRE_ATTACK : one.clip().status();
    }

    /** Whether its primary weapon may fire now, is waiting between shots, is refilling its clip, or is out. */
    public WeaponStatus getStatus() {
        return getStatus(0);
    }

    /** The same, of the weapon in {@code slot} of the set in use; READY for a slot it does not have. */
    public WeaponStatus getStatus(int slot) {
        var armed = loadout.armed();
        return slot >= 0 && slot < armed.size() ? statusOf(armed.get(slot)) : WeaponStatus.READY;
    }

    /** Rounds left in its primary weapon's clip; 0 for a weapon with no clip, which counts none. */
    public int getRounds() {
        return getRounds(0);
    }

    /** The same, of the weapon in {@code slot} of the set in use; 0 for a slot it does not have. */
    public int getRounds(int slot) {
        var armed = loadout.armed();
        return slot >= 0 && slot < armed.size() ? armed.get(slot).clip().rounds() : 0;
    }

    /**
     * Fill every clip it has and make each weapon ready at once — what a game does when an aircraft lands at
     * base, or on a crate.
     */
    public void refill() {
        loadout.refill();
    }

    /**
     * Fill each clip it has to at least {@code share} of its rounds, rounded down, and make it ready if that is any
     * at all — the reference's jets refilled on their pads in proportion to the time there: a Raptor's clip of 4 is 2
     * after half its reload. A clip holding more keeps what it has; a weapon with no clip is left as it is.
     */
    public void refill(float share) {
        loadout.refill(share);
    }

    /**
     * Order it to engage {@code target} — refused, and whatever it was doing kept, when the target is
     * something none of its weapons may be fired at ({@link #canFireAt}).
     *
     * @return whether it took the order
     */
    public boolean attack(ObjectId target) {
        return attack(target, false);
    }

    /**
     * The same, {@code forced} — the player's forced attack, taken on a thing passing itself off to this side as none
     * of its targets ({@link GameObject#isDisguisedFrom}), which no plain order is; and kept on it.
     */
    public boolean attack(ObjectId target, boolean forced) {
        return attack(target, forced, OrderSource.PLAYER);
    }

    /** The same, an order of {@code source}'s: the slots it may pick are those the source may ({@link WeaponSlot}). */
    public boolean attack(ObjectId target, boolean forced, OrderSource source) {
        return attack(target, forced, source, 0);
    }

    /**
     * The same, firing at most {@code maxShots} at it, the attack over after them as any attack is over — the
     * reference's {@code aiAttackObject} with {@code maxShotsToFire}, a Patriot called in by another firing its {@code
     * AssistingClipSize}. 0 for no limit.
     */
    public boolean attack(ObjectId target, boolean forced, OrderSource source, int maxShots) {
        var world = getOwner().getWorld();
        var victim = world == null || target == null ? null : world.findObject(target);
        var by = source == null || source == OrderSource.NONE ? OrderSource.PLAYER : source;
        if (victim != null && !canFireAt(victim, forced, by)) {
            return false;
        }
        if (victim != null && Hands.cannotBackAway(getOwner()) && tooNear(victim, by)) {
            return false; // it cannot move off to where it may fire
        }
        engagement.order(target, forced, by, maxShots);
        return true;
    }

    /**
     * Lock its weapon to {@code slot} of the set in use — the reference's {@code Object::setWeaponLock}: that slot
     * alone is weighed and fired, for an order and for its own look alike, until the lock is let go. A temporary lock
     * leaves a permanent one standing.
     *
     * @return whether the set in use has that slot
     */
    public boolean lock(int slot, Lock lock) {
        return loadout.lock(slot, lock);
    }

    /** Let a lock go: a permanent one every lock, a temporary one only a temporary lock. */
    public void unlock(Lock lock) {
        loadout.unlock(lock);
    }

    /** The slot its weapon is locked to, or -1. */
    public int getLockedSlot() {
        return loadout.lockedSlot();
    }

    /**
     * Whether any weapon of the set in use may be fired at {@code victim} at all — its class, not its range or
     * its side: one of the classes the world's {@link TargetRule}s give it is among the ones a weapon names. A
     * weapon that names none may be fired at anything.
     */
    public boolean canFireAt(GameObject victim) {
        return canFireAt(victim, false);
    }

    /** The same, or, {@code forced}, whether it may be made to: what passes itself off is a forced target only. */
    public boolean canFireAt(GameObject victim, boolean forced) {
        return canFireAt(victim, forced, OrderSource.PLAYER);
    }

    /** The same, for an order of {@code source}'s: only the slots it may pick, or the locked one. */
    public boolean canFireAt(GameObject victim, boolean forced, OrderSource source) {
        return loadout.canFireAt(victim, forced, source);
    }

    /** Firing on something, it is at work where it stands: not asked to step aside. */
    @Override
    public boolean keepsBusy() {
        return engagement.busy();
    }

    /**
     * Fire on a place of the ground — the reference's force-attack on open ground — with the first weapon of the set in
     * use that may hit ground (its {@code Targets} name none, or {@code GROUND}), closing to its range on its own legs
     * if it must, until a new order lets it go ({@link #holdFire}). False, and nothing changed, for a unit with no such
     * weapon.
     */
    public boolean attackGround(Coord3D place) {
        if (place == null || Loadout.groundWeapon(loadout.armed()) == null) {
            return false;
        }
        holdFire();
        engagement.fireOnGround(place);
        return true;
    }

    /** The place of the ground it fires on, or null. */
    public Coord3D getGroundTarget() {
        return engagement.groundTarget();
    }

    /**
     * Every slot in use but {@code fromSlot} kept from firing for at least {@code frames} from now — the reference's
     * {@code ShareWeaponReloadTime}, all of a thing's weapons reloading when one fires, done by a module told of a shot
     * and the wait it left ({@link ShotListener#onSpent}).
     */
    public void delaySlots(int fromSlot, int frames) {
        loadout.delaySlots(fromSlot, frames);
    }

    /** How long slot {@code slot}'s weapon waits between shots, its least; -1 for a slot not in use. */
    public int reloadFramesOf(int slot) {
        return loadout.reloadFramesOf(slot);
    }

    /** How far its first weapon in use reaches now, its bonuses counted; 0 with none in use. */
    public float reach() {
        var armed = loadout.armed();
        return armed.isEmpty() ? 0f : Hands.range(getOwner(), armed.getFirst().weapon());
    }

    /**
     * Pick no target by itself — a hero who fights only when told: idle, it looks for nothing, and an attack, a swing
     * toward a point or on the ground, or a skill that aims it, works as ever and leaves it idle when it ends.
     * Deterministic state of the unit, set on the simulation thread as every order is.
     */
    public void acquiresNothing(boolean nothing) {
        engagement.acquiresNothing(nothing);
    }

    public boolean isAcquiringNothing() {
        return engagement.acquiresNothing();
    }

    /**
     * Attack toward a point as {@code attack} says, until it is over or a new order lets it go: whatever it was firing
     * on let go, and a wind-up toward a point it was already attacking kept. Asked at once whether it is over, as it
     * is each frame, so one over as it is given is none.
     */
    public void attackToward(PointAttack attack) {
        if (attack == null) {
            return;
        }
        engagement.swingToward(attack);
    }

    /** The attack toward a point it is on, or null. */
    public PointAttack getPointAttack() {
        return engagement.pointAttack();
    }

    /** Its target let go, as {@link #holdFire} does, but not an attack toward a point: a walk goes on swinging. */
    public void dropTarget() {
        engagement.releaseKeepingPoint();
        firing.stopWinding();
        loadout.unlock(Lock.TEMPORARILY);
    }

    /** Let its target go: the attack is over, and a lock until it was is let go with it. */
    public void holdFire() {
        engagement.release();
        firing.stopWinding();
        loadout.unlock(Lock.TEMPORARILY);
    }

    public boolean isAttacking() {
        return engagement.target() != null;
    }

    /** Whether its target was given it by an order — an attack, an attack-move's, a guard's — not picked by itself. */
    public boolean isTargetOrdered() {
        return engagement.target() != null && engagement.source() != null;
    }

    /**
     * Whether {@code victim} is close enough to fire on, surface to surface, as the weapon it would choose for it
     * measures it.
     *
     * <p>Asked rather than told: the range is the weapon's and whoever is steering the unit has no business
     * keeping a second copy of it. See {@link PursueUpdate}, which is the one caller.
     */
    public boolean isInRange(GameObject victim) {
        var chosen = chosenFor(victim);
        if (chosen == null) {
            return false;
        }
        if (engagement.leeching() && victim.getId().equals(engagement.target())) {
            return true; // its reach kept for the rest of the attack
        }
        float gap = Hands.rangeTo(getOwner(), victim);
        return gap <= Hands.range(getOwner(), chosen.weapon()) && !Hands.insideLeast(getOwner(), chosen.weapon(), gap);
    }

    /**
     * Whether {@code victim} is nearer than the weapon it would fire at it may fire — within its least range
     * ({@link Weapon#minimumAttackRange}), measured as its reach is.
     */
    public boolean isTooNear(GameObject victim) {
        return tooNear(victim, engagement.source());
    }

    private boolean tooNear(GameObject victim, OrderSource by) {
        var chosen = loadout.choose(victim, by);
        return chosen != null && Hands.insideLeast(getOwner(), chosen.weapon(), Hands.rangeTo(getOwner(), victim));
    }

    /** The slot it would fire at {@code victim} for its present order, or null — see {@code Loadout.choose}. */
    private Armed chosenFor(GameObject victim) {
        return loadout.choose(victim, engagement.source());
    }

    /**
     * The least range of the weapon it would fire at {@code victim}, whole — where a route to fight from nearer ends
     * ({@code Weapon::isGoalPosWithinAttackRange}, which adds back the quarter cell the range is undersized by); 0 for
     * none.
     */
    public float leastFor(GameObject victim) {
        var chosen = chosenFor(victim);
        return chosen == null ? 0f : Math.max(0f, chosen.weapon().minimumAttackRange());
    }

    /**
     * Where something in the air too near {@code victim} for the weapon it would fire goes to fire — the reference's
     * {@code Weapon::computeApproachTarget}: on the line from the victim through it, halfway between the weapon's
     * least range and its reach and the two outlines' radii beyond; past the victim instead, where it is heading
     * toward it, so it does not turn round. Null where it is not too near.
     */
    public Coord3D awayInTheAir(GameObject victim) {
        var owner = getOwner();
        var world = owner.getWorld();
        var chosen = world == null ? null : chosenFor(victim);
        if (chosen == null) {
            return null;
        }
        float least = Hands.least(owner, chosen.weapon());
        if (least <= world.cellSize() || Hands.rangeTo(owner, victim) >= least) {
            return null;
        }
        var at = owner.getPosition();
        var there = victim.getPosition();
        float dx = at.x() - there.x();
        float dy = at.y() - there.y();
        float span = (float) Math.sqrt(dx * dx + dy * dy);
        if (span < 1e-3f) {
            dx = (float) StrictMath.cos(owner.getOrientation());
            dy = (float) StrictMath.sin(owner.getOrientation());
            span = 1f;
        }
        dx /= span;
        dy /= span;
        double heading = owner.getOrientation() - StrictMath.atan2(-dy, -dx);
        heading = StrictMath.IEEEremainder(heading, 2 * Math.PI);
        if (Math.abs(heading) < Math.PI / 2) {
            dx = -dx; // heading toward it: on past it rather than back
            dy = -dy;
        }
        float away = (Hands.range(owner, chosen.weapon()) + least) / 2f
                + victim.getGeometry().footprintRadius() + owner.getGeometry().footprintRadius();
        return new Coord3D(there.x() + dx * away, there.y() + dy * away, at.z());
    }

    /** How far the weapon it would fire at {@code victim} reaches in this unit's hands; 0 where none may fire at it. */
    public float reachFor(GameObject victim) {
        var chosen = chosenFor(victim);
        return chosen == null ? 0f : Hands.range(getOwner(), chosen.weapon());
    }

    /**
     * Whether the weapon it would fire at {@code victim} is a contact weapon ({@link Weapon#isContact}): a unit closing
     * with it goes on to the victim itself and fires once the two touch, as the reference lets it pathfind into its
     * target ({@code AIAttackApproachTargetState::computePath}).
     */
    public boolean closesToTouch(GameObject victim) {
        var world = getOwner().getWorld();
        var chosen = world == null ? null : chosenFor(victim);
        return chosen != null && chosen.weapon().isContact(world.cellSize());
    }

    public ObjectId getTarget() {
        return engagement.target();
    }

    /**
     * The slot of the set in use it would fire at {@code victim} now — the one it weighs best for its present order,
     * or the locked one while a lock holds — so a game turns the turret that carries it, or the thing; -1 where none
     * may be fired at it.
     */
    public int slotFor(GameObject victim) {
        var chosen = chosenFor(victim);
        return chosen == null ? -1 : chosen.index();
    }

    /**
     * How often its first weapon strikes, in blows a second: its wait after a shot divided by everything that hastens
     * it in this unit's hands — the same figure every wait is drawn with. 0 for a unit with no weapon.
     */
    public float blowsPerSecond() {
        var slots = loadout.armed();
        if (slots.isEmpty()) {
            return 0f;
        }
        var weapon = slots.getFirst().weapon();
        return GameConstants.LOGICFRAMES_PER_SECOND * Hands.rateOfFire(getOwner(), weapon)
                / Math.max(1, weapon.reloadFrames());
    }

    // ---- each frame ----

    @Override
    public void update() {
        int wasWindingUp = firing.beginFrame(); // a frame that does not go on winding it up drops it
        loadout.noticeTheSetInUse();
        scan.noticeFallingIdle(engagement.target() != null);
        if (getOwner().isEffectivelyDead()
                || getOwner().isContained() && !firesFromInside(getOwner())
                || getOwner().hasStatus(ObjectStatus.DISABLED) || getOwner().hasStatus(ObjectStatus.SOLD)) {
            return; // dead, inside a transport that keeps it idle, disabled or being sold — hold fire
        }
        loadout.tick();

        var owner = getOwner();
        if (heldByAModule(owner)) {
            return; // busy with something else — see WeaponHold
        }
        var armed = loadout.armed();
        if (armed.isEmpty()) {
            return; // no set of its fits what it is now
        }
        boolean walking = Hands.isWalking(owner);
        if (walking && Loadout.noneFiresOnTheMove(armed)) {
            // Reloading on the way, and keeping whatever it was aimed at, but not
            // firing. This has to live here rather than in whatever is steering the
            // unit: a weapon acquires its own target and fires in the same call, so
            // anything outside it can only ever disarm it a frame too late.
            return;
        }
        var world = owner.getWorld();
        if (world == null || Loadout.allOut(armed)) {
            return; // an empty gun looks for nothing, and keeps what it had for when it is refilled
        }
        if (engagement.groundTarget() != null) {
            firing.onTheGround(world, armed, walking);
            return;
        }
        var swing = engagement.pointAttack();
        if (swing != null) {
            var toward = swing.point(engagement.pointWindEnds() >= 0);
            if (toward != null) {
                firing.toward(world, armed.getFirst(), walking, toward);
                return;
            }
            engagement.giveUpPoint(); // over
        }

        if (engagement.target() == null) {
            if (engagement.acquiresNothing() || !scan.looksNow(world)) {
                return; // between its looks for a target, or told never to look
            }
            var found = scan.acquire(world, armed);
            if (found == null) {
                return;
            }
            engagement.found(found);
        }

        var victim = keptTarget(world, owner);
        if (victim == null) {
            return;
        }
        var chosen = loadout.choose(armed, victim, engagement.source());
        if (chosen == null) {
            holdFire(); // it took off, or was never something this could hit
            return;
        }
        firing.engage(world, chosen, victim, walking, wasWindingUp);
    }

    /**
     * Its clips and lock, what it is set on, its wind-up and its next look; refused while it attacks toward a point,
     * which is the game's code.
     */
    @Override
    public void save(uz.dukeengine.core.save.SaveOut out) {
        var attack = engagement.pointAttack();
        if (attack != null) {
            throw new uz.dukeengine.core.save.UnsavedModuleException(WeaponUpdate.class.getName(), "thing "
                    + getOwner().getId().value() + " (" + getOwner().getTemplate().name() + ") is attacking toward a"
                    + " point (" + attack.getClass().getName() + "): that is code, which a save cannot hold");
        }
        loadout.save(out);
        engagement.save(out);
        firing.save(out);
        scan.save(out);
    }

    @Override
    public void load(uz.dukeengine.core.save.SaveIn in) {
        loadout.load(in);
        engagement.load(in);
        firing.load(in);
        scan.load(in);
    }

    /** Its target, if it is still one to fire on; else the attack is let go, and null. */
    private GameObject keptTarget(World world, GameObject owner) {
        var victim = world.findObject(engagement.target());
        if (victim == null || victim.isEffectivelyDead() || victim.getBody() == null) {
            holdFire();
            return null;
        }
        if (world.getRelationship(owner, victim) == Relationship.ALLIES
                && !(engagement.forced() && engagement.source() == OrderSource.PLAYER)) {
            holdFire(); // never on its own side or an ally's, but by a player's own forced order
            return null;
        }
        if (!engagement.forced() && victim.isDisguisedFrom(owner.getPlayerIndex())) {
            holdFire(); // passed off as none of its targets: kept on only by a forced order
            return null;
        }
        return victim;
    }

    /**
     * Land a shot a {@link ProjectileLauncher} carried, where it came down — exactly what a weapon's instant hit
     * does from the moment the shot is handed over: the direct hit on {@code victim}, if there is one and it is
     * still alive, scaled by its armour as always; the blast round {@code where}, by the weapon's radius and the
     * same rules; the kill experience to the shooter, if it is still there to take it; and {@link uz.dukeengine.combat.event.ShotLanded}.
     *
     * <p>A shot may come down where its victim no longer is — it moved, or a shell was aimed at a spot — and then
     * {@code victim} is {@code null} and only the blast lands. The shooter may be dead by now: the damage lands
     * all the same, with nobody to credit. Call it inside the simulation frame, as a launcher's own update is.
     *
     * @param from where it came from, so what is drawn where it landed faces the way it travelled; {@code null}
     *             for straight down
     */
    public static void land(World world, Shot shot, GameObject victim, Coord3D where, Coord3D from) {
        Landing.land(world, shot, victim, where, from);
    }

    /**
     * Where a shot at {@code victim} is aimed: at the thing, or where an {@link AimOffset} of its throws the aim —
     * what a launcher carrying a shot flies to, a shot at a thing that has one landing its blast there alone.
     */
    public static Coord3D aimPoint(GameObject victim) {
        return Landing.aimPoint(victim);
    }

    /** Halfway up a thing: where a shot that hit it at once is drawn landing. */
    public static Coord3D middleOf(GameObject thing) {
        return Landing.middleOf(thing);
    }

    /** The point of a thing nearest a blast, halfway up it: where the blast is drawn striking it. */
    public static Coord3D nearestOf(GameObject thing, Coord3D blast) {
        return Landing.nearestOf(thing, blast);
    }

    /**
     * Whether anything on this unit is holding its fire.
     *
     * <p>Walked in module order, which is fixed when the object is built — the
     * same rule {@link Hands} follows for its modifiers, and for the same reason.
     */
    private static boolean heldByAModule(GameObject owner) {
        // Asked of every armed thing every frame: found among its modules without copying them.
        return owner.anyModule(module -> module instanceof WeaponHold hold && hold.holdingFire());
    }

    /**
     * Whether a thing that is contained fires from its hold: asked of every {@link Hold} in the world, in the order the
     * world keeps its things — a transport's firing ports, a garrison, riders on top.
     */
    static boolean firesFromInside(GameObject passenger) {
        var world = passenger.getWorld();
        if (world == null) {
            return false;
        }
        for (var carrier : world.getObjects()) {
            for (var module : carrier.getModules()) {
                if (module instanceof Hold hold && hold.passengerFires(passenger)) {
                    return true;
                }
            }
        }
        return false;
    }
}
