package uz.dukeengine.rts.module;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.Death;
import uz.dukeengine.core.module.DeathType;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.Conditions;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.rts.event.ShotLanded;
import uz.dukeengine.rts.event.WeaponFired;
import uz.dukeengine.rts.message.OrderSource;
import uz.dukeengine.rts.player.RtsPlayer;

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
 * fires is chosen again every time a target is weighed — see {@link #choose} — and each weapon keeps its own
 * clip and reload across a swap, so a swap cannot be used to skip one. A block with no sets has one weapon and
 * behaves exactly as it did.
 */
@ModuleGroup(ModuleGroups.COMBAT)
public final class WeaponUpdate extends UpdateModule {

    private static final Logger LOG = Logger.getLogger(WeaponUpdate.class.getName());

    /** Weapon names a slot linked that the world did not have, said once each rather than once a frame. */
    private static final Set<String> MISSING = ConcurrentHashMap.newKeySet();

    /** What a slot preferred against its target counts as dealing: more than any weapon could. */
    private static final float PREFERRED = Float.MAX_VALUE;

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
     */
    public record Data(float damage, float attackRange, int reloadFrames,
            DamageType damageType, float splashRadius,
            boolean attackOnTheMove, List<String> targets,
            int reloadFramesMax, int clipSize, int clipReloadFrames, boolean autoReload,
            String name, List<WeaponSet> weaponSets, DeathType deathType) implements ModuleData {
        /** What a block leaves out: plain damage, no splash, a shot taken on the move, at anything, no clip. */
        static final Data DEFAULTS = new Data(0f, 0f, 0, DamageType.NORMAL, 0f, true, List.of(),
                0, 0, 0, true, null, List.of(), DeathType.NORMAL);

        public Data {
            damageType = damageType == null ? DamageType.NORMAL : damageType;
            deathType = deathType == null ? DeathType.NORMAL : deathType;
            targets = targets == null ? List.of() : List.copyOf(targets);
            weaponSets = weaponSets == null ? List.of() : List.copyOf(weaponSets);
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
                    DeathType.NORMAL);
        }
    }

    /** A weapon one slot of the set in use carries, which slot, and its clip: what is weighed when a target is. */
    private record Armed(int index, WeaponSlot slot, Weapon weapon, Clip clip) {
    }

    /** The slot the weapon written in place sits in: no preference, chosen as freely by the unit as by an order. */
    private static final WeaponSlot IN_PLACE = new WeaponSlot(null);

    private final List<WeaponSet> sets;
    /** The one weapon written in place, when there are no sets — every unit from before sets. */
    private final List<Armed> inPlace;
    /** Every weapon the sets have armed, by name, with its clip: kept across swaps, and ticking through them. */
    private final Map<String, Clip> clips = new LinkedHashMap<>();

    private ObjectId target;
    /** Whether its target was given it by a forced order: kept on though the target passes itself off. */
    private boolean forced;
    /** Whose order its target was, or null for one it found for itself — which slots it may use. */
    private OrderSource source;

    /**
     * How long a lock on one slot holds — the reference's {@code WeaponLockType}. While a lock holds, its slot alone
     * is weighed and fired, whatever the slot's sources.
     */
    public enum Lock {
        /** Until the attack it was given for is over, its slot's clip is emptied, or another lock. */
        TEMPORARILY,
        /** Until it is let go, another lock, or a change of the set in use. */
        PERMANENTLY
    }

    /** The slot of the set in use its weapon is locked to, or -1. */
    private int lockedSlot = -1;
    private Lock lock;
    /** The set in use when it last looked, by its index: a change lets every lock go, as the reference's does. */
    private int setInUse = -1;

    public WeaponUpdate(GameObject owner, Data data) {
        super(owner);
        this.sets = data.weaponSets();
        var written = Weapon.of(data);
        this.inPlace = sets.isEmpty()
                ? List.of(new Armed(0, IN_PLACE, written, clipOf(written)))
                : List.of();
    }

    private static Clip clipOf(Weapon weapon) {
        return new Clip(weapon.clipSize(), weapon.reloadFrames(), weapon.reloadFramesMax(), weapon.clipReloadFrames(),
                weapon.autoReload());
    }

    // ---- what a game asks of it ----

    /** The frame each slot of a set, by its index, last fired: for what a client draws, nothing the game decides. */
    private final java.util.Map<Integer, Integer> firedOn = new java.util.HashMap<>();

    /** One weapon slot of the set in use as it stands: its index in the set, whether it fired, and its status. */
    public record SlotNow(int slot, boolean fired, WeaponStatus status) {
    }

    /** Each weapon slot of the set in use: whether it fired in the game's frame {@code frame}, and where it stands. */
    public List<SlotNow> slotsNow(int frame) {
        var slots = new ArrayList<SlotNow>();
        for (var one : armed()) {
            slots.add(new SlotNow(one.index(), firedOn.getOrDefault(one.index(), -1) == frame, one.clip().status()));
        }
        return slots;
    }

    /** Whether its primary weapon may fire now, is waiting between shots, is refilling its clip, or is out. */
    public WeaponStatus getStatus() {
        return getStatus(0);
    }

    /** The same, of the weapon in {@code slot} of the set in use; READY for a slot it does not have. */
    public WeaponStatus getStatus(int slot) {
        var armed = armed();
        return slot >= 0 && slot < armed.size() ? armed.get(slot).clip().status() : WeaponStatus.READY;
    }

    /** Rounds left in its primary weapon's clip; 0 for a weapon with no clip, which counts none. */
    public int getRounds() {
        return getRounds(0);
    }

    /** The same, of the weapon in {@code slot} of the set in use; 0 for a slot it does not have. */
    public int getRounds(int slot) {
        var armed = armed();
        return slot >= 0 && slot < armed.size() ? armed.get(slot).clip().rounds() : 0;
    }

    /**
     * Fill every clip it has and make each weapon ready at once — what a game does when an aircraft lands at
     * base, or on a crate.
     */
    public void refill() {
        for (var armed : inPlace) {
            armed.clip().refill();
        }
        for (var clip : clips.values()) {
            clip.refill();
        }
    }

    /**
     * Fill each clip it has to at least {@code share} of its rounds, rounded down, and make it ready if that is any
     * at all — the reference's jets refilled on their pads in proportion to the time there: a Raptor's clip of 4 is 2
     * after half its reload. A clip holding more keeps what it has; a weapon with no clip is left as it is.
     */
    public void refill(float share) {
        for (var armed : inPlace) {
            armed.clip().refill(share);
        }
        for (var clip : clips.values()) {
            clip.refill(share);
        }
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
        var world = getOwner().getWorld();
        var victim = world == null || target == null ? null : world.findObject(target);
        var by = source == null || source == OrderSource.NONE ? OrderSource.PLAYER : source;
        if (victim != null && !canFireAt(victim, forced, by)) {
            return false;
        }
        this.target = target;
        this.source = by;
        this.forced = forced;
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
        if (lock == null || slotOf(armed(), slot) == null) {
            return false;
        }
        if (lock == Lock.PERMANENTLY || this.lock != Lock.PERMANENTLY) {
            this.lockedSlot = slot;
            this.lock = lock;
        }
        return true;
    }

    /** Let a lock go: a permanent one every lock, a temporary one only a temporary lock. */
    public void unlock(Lock lock) {
        if (this.lock != null && (lock == Lock.PERMANENTLY || this.lock == Lock.TEMPORARILY)) {
            this.lockedSlot = -1;
            this.lock = null;
        }
    }

    /** The slot its weapon is locked to, or -1. */
    public int getLockedSlot() {
        return lockedSlot;
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
        if (!forced && victim.isDisguisedFrom(getOwner().getPlayerIndex())) {
            return false;
        }
        var armed = armed();
        var locked = slotOf(armed, lockedSlot);
        if (locked != null) {
            return mayHit(locked.weapon(), victim);
        }
        for (var one : armed) {
            if (one.slot().pickedBy(source) && mayHit(one.weapon(), victim)) {
                return true;
            }
        }
        return false;
    }

    /** Firing on something, it is at work where it stands: not asked to step aside. */
    @Override
    public boolean keepsBusy() {
        return target != null;
    }

    /** Let its target go: the attack is over, and a lock until it was is let go with it. */
    public void holdFire() {
        this.target = null;
        this.forced = false;
        unlock(Lock.TEMPORARILY);
    }

    public boolean isAttacking() {
        return target != null;
    }

    /** Whether its target was given it by an order — an attack, an attack-move's, a guard's — not picked by itself. */
    public boolean isTargetOrdered() {
        return target != null && source != null;
    }

    /**
     * Whether {@code victim} is close enough to fire on, surface to surface, as the weapon it would choose for it
     * measures it.
     *
     * <p>Asked rather than told: the range is the weapon's and whoever is steering the unit has no business
     * keeping a second copy of it. See {@link PursueUpdate}, which is the one caller.
     */
    public boolean isInRange(GameObject victim) {
        if (victim == null) {
            return false;
        }
        var chosen = choose(armed(), victim, source);
        return chosen != null && rangeTo(getOwner(), victim) <= range(getOwner(), chosen.weapon());
    }

    /**
     * Whether the weapon it would fire at {@code victim} is a contact weapon ({@link Weapon#isContact}): a unit closing
     * with it goes on to the victim itself and fires once the two touch, as the reference lets it pathfind into its
     * target ({@code AIAttackApproachTargetState::computePath}).
     */
    public boolean closesToTouch(GameObject victim) {
        var world = getOwner().getWorld();
        var chosen = victim == null || world == null ? null : choose(armed(), victim, source);
        return chosen != null && chosen.weapon().isContact(world.cellSize());
    }

    public ObjectId getTarget() {
        return target;
    }

    // ---- each frame ----

    @Override
    public void update() {
        noticeTheSetInUse();
        if (getOwner().isEffectivelyDead()
                || getOwner().isContained() && !ContainModule.firesFromInside(getOwner())
                || getOwner().hasStatus(ObjectStatus.DISABLED) || getOwner().hasStatus(ObjectStatus.SOLD)) {
            return; // dead, inside a transport that keeps it idle, disabled or being sold — hold fire
        }
        tick();

        var owner = getOwner();
        if (heldByAModule(owner)) {
            return; // busy with something else — see WeaponHold
        }
        var armed = armed();
        if (armed.isEmpty()) {
            return; // no set of its fits what it is now
        }
        boolean walking = isWalking(owner);
        if (walking && noneFiresOnTheMove(armed)) {
            // Reloading on the way, and keeping whatever it was aimed at, but not
            // firing. This has to live here rather than in whatever is steering the
            // unit: a weapon acquires its own target and fires in the same call, so
            // anything outside it can only ever disarm it a frame too late.
            return;
        }
        var world = owner.getWorld();
        if (world == null || allOut(armed)) {
            return; // an empty gun looks for nothing, and keeps what it had for when it is refilled
        }

        if (target == null) {
            if (!scansNow(world, owner)) {
                return; // between its looks for a target
            }
            acquireTarget(world, owner, armed);
            if (target == null) {
                return;
            }
            source = null;
            forced = false;
        }

        var victim = world.findObject(target);
        if (victim == null || victim.isEffectivelyDead() || victim.getBody() == null) {
            holdFire();
            return;
        }
        if (world.getRelationship(owner.getPlayerIndex(), victim.getPlayerIndex()) == Relationship.ALLIES) {
            holdFire(); // never fire on allies
            return;
        }
        if (!forced && victim.isDisguisedFrom(owner.getPlayerIndex())) {
            holdFire(); // passed off as none of its targets: kept on only by a forced order
            return;
        }
        var chosen = choose(armed, victim, source);
        if (chosen == null) {
            holdFire(); // it took off, or was never something this could hit
            return;
        }
        if (rangeTo(owner, victim) > range(owner, chosen.weapon())) {
            return; // out of range — wait for movement to close in
        }
        if (walking && !chosen.weapon().attackOnTheMove()) {
            return; // this one is stood still for
        }
        if (chosen.clip().status() != WeaponStatus.READY) {
            return; // between shots, or refilling its clip
        }
        fire(world, owner, victim, chosen);
    }

    private void fire(uz.dukeengine.core.thing.World world, GameObject owner, GameObject victim, Armed chosen) {
        firedOn.put(chosen.index(), world.getFrame());
        var weapon = chosen.weapon();
        float wider = bonus(owner, weapon, WeaponBonus.Kind.RADIUS);
        var shot = new Shot(owner.getId(), owner.getPlayerIndex(), weapon, chosen.index(),
                dealt(owner, weapon, weapon.damage()), weapon.splashRadius() * wider,
                dealt(owner, weapon, weapon.secondaryDamage()), weapon.secondaryRadius() * wider);

        // A shot was fired either way — the reload runs and the moment is
        // announced — but whether it lands now is the launcher's to decide.
        boolean inFlight = handOver(owner, victim, shot);
        boolean thrownOff = offsetOf(victim) != null;
        if (!inFlight && !thrownOff) {
            victim.getBody().damage(shot.damage(), weapon.damageType(), blow(shot), middleOf(victim),
                    owner.getPosition()); // scaled by its armour
        }
        if (chosen.clip().fired(world.random(), rateOfFire(owner, weapon)) && chosen.index() == lockedSlot) {
            unlock(Lock.TEMPORARILY); // its clip is empty: a lock until then is over
        }
        world.post(new WeaponFired(world.getFrame(), owner.getId(), victim.getId(),
                owner.getPosition(), aimPoint(victim), weapon.name(), chosen.index(),
                weapon.isContact(world.cellSize()), shot.radius()));

        if (inFlight) {
            return; // nothing has been hit yet; the blast and the kill wait for land()
        }
        if (thrownOff) {
            var point = aimPoint(victim); // it missed: only its blast, round where it was aimed, may hurt the thing
            struck(world, shot, owner, null, point, point, owner.getPosition());
            return;
        }
        struck(world, shot, owner, victim, victim.getPosition(), middleOf(victim), owner.getPosition());
        if (victim.isEffectivelyDead()) {
            holdFire();
        }
    }

    /**
     * Land a shot a {@link ProjectileLauncher} carried, where it came down — exactly what a weapon's instant hit
     * does from the moment the shot is handed over: the direct hit on {@code victim}, if there is one and it is
     * still alive, scaled by its armour as always; the blast round {@code where}, by the weapon's radius and the
     * same rules; the kill experience to the shooter, if it is still there to take it; and {@link ShotLanded}.
     *
     * <p>A shot may come down where its victim no longer is — it moved, or a shell was aimed at a spot — and then
     * {@code victim} is {@code null} and only the blast lands. The shooter may be dead by now: the damage lands
     * all the same, with nobody to credit. Call it inside the simulation frame, as a launcher's own update is.
     *
     * @param from where it came from, so what is drawn where it landed faces the way it travelled; {@code null}
     *             for straight down
     */
    public static void land(uz.dukeengine.core.thing.World world, Shot shot, GameObject victim, Coord3D where,
            Coord3D from) {
        var shooter = world.findObject(shot.shooter());
        var hit = victim == null || victim.isEffectivelyDead() || victim.getBody() == null || offsetOf(victim) != null
                ? null : victim;
        if (hit != null) {
            hit.getBody().damage(shot.damage(), shot.weapon().damageType(), blow(shot), middleOf(hit),
                    from == null ? where : from);
        }
        struck(world, shot, shooter, hit, where, where, from == null ? where : from);
    }

    /**
     * Where a shot at {@code victim} is aimed: at the thing, or where an {@link AimOffset} of its throws the aim —
     * what a launcher carrying a shot flies to, a shot at a thing that has one landing its blast there alone.
     */
    public static Coord3D aimPoint(GameObject victim) {
        var offset = offsetOf(victim);
        return offset == null ? victim.getPosition() : victim.getPosition().add(offset);
    }

    /** The first aim offset a module of {@code victim} gives now, or null. */
    private static Coord3D offsetOf(GameObject victim) {
        for (var module : victim.getModules()) {
            if (module instanceof AimOffset thrower && thrower.aimOffset() != null) {
                return thrower.aimOffset();
            }
        }
        return null;
    }

    /** Halfway up a thing: where a shot that hit it at once is drawn landing. */
    static Coord3D middleOf(GameObject thing) {
        var at = thing.getPosition();
        return new Coord3D(at.x(), at.y(), at.z() + thing.getGeometry().height() / 2f);
    }

    /** The point of a thing nearest a blast, halfway up it: where the blast is drawn striking it. */
    static Coord3D nearestOf(GameObject thing, Coord3D blast) {
        var middle = middleOf(thing);
        return uz.dukeengine.core.thing.Footprint.of(thing)
                .nearestTo(new Coord3D(blast.x(), blast.y(), middle.z()));
    }

    /** The death a shot deals if it kills, and whose it is: its weapon's, its shooter's, and the side it fired for. */
    private static Death blow(Shot shot) {
        return new Death(shot.weapon().deathType(), shot.shooter(), shot.side());
    }

    /**
     * Everything that follows the direct hit, one way for a shot that hit at once and one that was carried: the
     * blast, the kill experience, and the moment it landed.
     */
    private static void struck(uz.dukeengine.core.thing.World world, Shot shot, GameObject shooter,
            GameObject victim, Coord3D where, Coord3D shown, Coord3D from) {
        if (shot.radius() > 0f || shot.secondaryRadius() > 0f) {
            splash(world, shot, shooter, victim, where);
        }
        if (victim != null && victim.isEffectivelyDead() && shooter != null) {
            grantKillExperience(shooter, victim);
        }
        world.post(new ShotLanded(world.getFrame(), shot.shooter(),
                victim == null ? null : victim.getId(), shot.weapon().name(), shown, from,
                shot.radius()));
    }

    /** A frame has passed for every weapon it has, carried or swapped out: reloads run on through a swap. */
    private void tick() {
        for (var armed : inPlace) {
            armed.clip().tick();
        }
        for (var clip : clips.values()) {
            clip.tick();
        }
    }

    // ---- which weapons, and which of them ----

    /**
     * The weapons of the set in use, slot by slot: the one written in place where there are no sets, else the
     * set whose conditions the unit has, the most of them winning — {@link Conditions#bestFit}, the rule a
     * template's models are chosen by. A slot whose weapon the world does not have is left out, said once.
     */
    private List<Armed> armed() {
        if (sets.isEmpty()) {
            return inPlace;
        }
        var said = new ArrayList<List<String>>(sets.size());
        for (var set : sets) {
            said.add(set.conditions());
        }
        int fits = Conditions.bestFit(said, getOwner().getConditions());
        if (fits < 0) {
            return List.of();
        }
        var world = getOwner().getWorld();
        var slots = sets.get(fits).slots();
        var armed = new ArrayList<Armed>(slots.size());
        for (int index = 0; index < slots.size(); index++) {
            var slot = slots.get(index);
            var weapon = world instanceof uz.dukeengine.rts.RtsSimulation rts ? rts.findWeapon(slot.weapon()) : null;
            if (weapon == null) {
                if (MISSING.add(String.valueOf(slot.weapon()))) {
                    LOG.warning(() -> getOwner().getTemplate().name() + "'s weapon set names the weapon '"
                            + slot.weapon() + "', which the game never gave the world — see addWeapons");
                }
                continue;
            }
            armed.add(new Armed(index, slot, weapon, clips.computeIfAbsent(weapon.name(), name -> clipOf(weapon))));
        }
        return armed;
    }

    /**
     * Which of {@code armed} to use on {@code victim} — the reference game's choice, from its source
     * ({@code WeaponSet::chooseBestWeaponForTarget}), made again every time a target is weighed:
     *
     * <ul>
     *   <li>A lock picks its slot, and nothing else is weighed ({@code isCurWeaponLocked}): the slot, if it may be
     *       fired at the target's class and is not out.
     *   <li>A slot is passed over if the unit may not pick it by itself and nobody ordered this target, or its
     *       order's source may not pick it ({@link WeaponSlot#autoChooseSources}, a unit's own look counting as
     *       the game's); if it is out and does not reload itself; if its weapon may not be fired at the target's
     *       class; or if it would do no damage to it after armour.
     *   <li>A slot preferred against a kind the target has wins outright, and is kept even while it reloads.
     *   <li>Otherwise the ready slot that would do the most damage wins — readiness before damage: one that is
     *       reloading is only a fall-back, the best of those if nothing is ready. Ties go to the lower slot.
     *   <li>Nothing at all: the first slot, if it may be fired at the target — a weapon that deals nothing
     *       still fires, as every such weapon always has.
     * </ul>
     *
     * <p>Range is not weighed, as it is not there: a better weapon out of reach is a reason to close in.
     *
     * @return the slot, or {@code null} where none may be fired at the target at all
     */
    private Armed choose(List<Armed> armed, GameObject victim, OrderSource byOrder) {
        if (armed.isEmpty()) {
            return null;
        }
        var locked = slotOf(armed, lockedSlot);
        if (locked != null) {
            return mayHit(locked.weapon(), victim) && locked.clip().status() != WeaponStatus.OUT ? locked : null;
        }
        Armed ready = null;
        Armed fallBack = null;
        float mostReady = 0f;
        float mostFallBack = 0f;
        // Backwards, and >= below, so that a tie goes to the lower slot.
        for (int at = armed.size() - 1; at >= 0; at--) {
            var one = armed.get(at);
            if (!mayPick(one, byOrder)) {
                continue;
            }
            var status = one.clip().status();
            if (status == WeaponStatus.OUT || !mayHit(one.weapon(), victim)) {
                continue;
            }
            float damage = victim.getBody() == null ? 0f
                    : victim.getBody().estimateDamage(dealt(getOwner(), one.weapon(), one.weapon().damage()),
                            one.weapon().damageType());
            if (damage <= 0f) {
                continue;
            }
            boolean isReady = status == WeaponStatus.READY;
            if (preferredAgainst(one.slot(), victim)) {
                damage = PREFERRED;
                isReady = true; // kept even while it reloads; out of ammo was passed over above
            }
            if (isReady && damage >= mostReady) {
                ready = one;
                mostReady = damage;
            } else if (!isReady && damage >= mostFallBack) {
                fallBack = one;
                mostFallBack = damage;
            }
        }
        if (ready != null) {
            return ready;
        }
        if (fallBack != null) {
            return fallBack;
        }
        var first = armed.getFirst();
        return mayPick(first, byOrder) && mayHit(first.weapon(), victim) && first.clip().status() != WeaponStatus.OUT
                ? first : null;
    }

    /** Whether {@code one} may be picked for an order of {@code source}'s, or, null, for the unit's own look. */
    private static boolean mayPick(Armed one, OrderSource source) {
        return source != null ? one.slot().pickedBy(source)
                : one.slot().autoChoosable() && one.slot().pickedBy(OrderSource.GAME);
    }

    /** The slot at {@code index} of the set in use, or null — for -1, or a slot the set does not have. */
    private static Armed slotOf(List<Armed> armed, int index) {
        for (var one : armed) {
            if (one.index() == index) {
                return one;
            }
        }
        return null;
    }

    /** The set in use changed since it last looked: every lock is let go, as the reference's set swap lets them. */
    private void noticeTheSetInUse() {
        if (sets.isEmpty()) {
            return;
        }
        var said = new ArrayList<List<String>>(sets.size());
        for (var set : sets) {
            said.add(set.conditions());
        }
        int fits = Conditions.bestFit(said, getOwner().getConditions());
        if (fits != setInUse && setInUse >= 0) {
            unlock(Lock.PERMANENTLY);
        }
        setInUse = fits;
    }

    private static boolean preferredAgainst(WeaponSlot slot, GameObject victim) {
        for (var kind : slot.preferredAgainst()) {
            if (victim.isKindOf(kind)) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code weapon} may be fired at {@code victim}'s class — see {@link TargetRule}. */
    private boolean mayHit(Weapon weapon, GameObject victim) {
        var world = getOwner().getWorld();
        if (world != null && victim.getTargetableFrom() > world.getFrame()) {
            return false; // nobody's target yet
        }
        if (victim.isHiddenFrom(getOwner().getPlayerIndex())) {
            return false; // not there, or kept from this side: nothing may be fired at it, forced or not
        }
        if (weapon.targets().isEmpty()) {
            return true;
        }
        var rules = getOwner().getWorld() instanceof uz.dukeengine.rts.RtsSimulation rts
                ? rts.getTargetRules() : List.<TargetRule>of();
        for (var named : TargetRule.classesOf(rules, victim)) {
            if (weapon.targets().contains(named)) {
                return true;
            }
        }
        return false;
    }

    private static boolean noneFiresOnTheMove(List<Armed> armed) {
        for (var one : armed) {
            if (one.weapon().attackOnTheMove()) {
                return false;
            }
        }
        return true;
    }

    private static boolean allOut(List<Armed> armed) {
        for (var one : armed) {
            if (one.clip().status() != WeaponStatus.OUT) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether anything on this unit is holding its fire.
     *
     * <p>Walked in module order, which is fixed when the object is built — the
     * same rule {@link #damageModifiers} follows, and for the same reason.
     */
    private static boolean heldByAModule(GameObject owner) {
        for (var module : owner.getModules()) {
            if (module instanceof WeaponHold hold && hold.holdingFire()) {
                return true;
            }
        }
        return false;
    }

    /** Whether the owner is under way — nothing to say if it cannot move at all. */
    private static boolean isWalking(GameObject owner) {
        var locomotor = owner.getLocomotor();
        return locomotor != null && locomotor.isMoving();
    }

    /**
     * Offer the shot to a {@link ProjectileLauncher} on this unit, if it has one.
     *
     * <p>The first one found, in module order, which is fixed when the object is
     * built — so two peers hand the same shot to the same launcher.
     *
     * @return whether one took it
     */
    private static boolean handOver(GameObject owner, GameObject victim, Shot shot) {
        for (var module : owner.getModules()) {
            if (module instanceof ProjectileLauncher launcher && launcher.launch(owner, victim, shot)) {
                return true;
            }
        }
        return false;
    }

    /**
     * What {@code weapon} deals before the target's armour: its damage, this unit's and its side's bonuses —
     * multiplied in that order, as they always were, so a shot deals the same bits it did.
     */
    private static float dealt(GameObject owner, Weapon weapon, float damage) {
        float dealt = damage * damageModifiers(owner) * bonus(owner, weapon, WeaponBonus.Kind.DAMAGE);
        var shooter = RtsPlayer.of(owner.getWorld(), owner.getPlayerIndex());
        if (shooter != null) {
            dealt *= shooter.getWeaponDamageBonus(); // player-wide upgrade bonus
        }
        return dealt;
    }

    /**
     * Everything attached to this unit that changes how hard it hits, multiplied
     * together.
     *
     * <p>Walked in module order, which is fixed when the object is built, so the
     * product is the same on every machine and in every replay.
     */
    private static float damageModifiers(GameObject owner) {
        float multiplier = 1f;
        for (var module : owner.getModules()) {
            if (module instanceof DamageModifier modifier) {
                multiplier *= modifier.damageMultiplier();
            }
        }
        return multiplier;
    }

    /**
     * The game's weapon bonuses and this weapon's own for what this unit holds, of one kind, added up — see
     * {@link WeaponBonus}.
     */
    private static float bonus(GameObject owner, Weapon weapon, WeaponBonus.Kind kind) {
        return owner.getWorld() instanceof uz.dukeengine.rts.RtsSimulation rts
                ? rts.weaponBonus(owner, kind, weapon.bonuses()) : 1f;
    }

    /** How far a weapon reaches in this unit's hands: its range, and the bonuses for what it holds. */
    private static float range(GameObject owner, Weapon weapon) {
        return weapon.attackRange() * bonus(owner, weapon, WeaponBonus.Kind.RANGE);
    }

    /** Everything that changes how fast this weapon fires in this unit's hands, multiplied together in module order. */
    private static float rateOfFire(GameObject owner, Weapon weapon) {
        float multiplier = bonus(owner, weapon, WeaponBonus.Kind.RATE_OF_FIRE);
        for (var module : owner.getModules()) {
            if (module instanceof RateOfFireModifier modifier) {
                multiplier *= modifier.rateOfFireMultiplier();
            }
        }
        return multiplier;
    }

    /**
     * Area damage round where the shot struck, to whom its weapon's blast hurts ({@link Weapon.Affects}) — not the
     * victim, which took the direct hit: the reference's {@code Weapon::dealDamageInternal}, its damage within the
     * first ring and its second damage beyond it, within the second. Each ring is measured to a thing's bounding
     * sphere ({@code FROM_BOUNDINGSPHERE_3D}): caught where the gap is under the larger radius, the first damage where
     * it is within the first. What the blast kills is the shooter's to be credited with, if it is there.
     */
    private static void splash(uz.dukeengine.core.thing.World world, Shot shot, GameObject shooter,
            GameObject victim, Coord3D where) {
        float reach = Math.max(shot.radius(), shot.secondaryRadius());
        var caught = world.objectsInRange(where, Float.MAX_VALUE, candidate ->
                candidate != victim
                        && gapTo(candidate, where) < reach
                        && !candidate.isContained()
                        && candidate.getBody() != null
                        && !candidate.isEffectivelyDead()
                        && hurts(world, shot, shooter, candidate));
        for (var bystander : caught) {
            float damage = gapTo(bystander, where) <= shot.radius() ? shot.damage() : shot.secondaryDamage();
            if (damage <= 0f) {
                continue;
            }
            bystander.getBody().damage(damage, shot.weapon().damageType(), blow(shot),
                    nearestOf(bystander, where), where);
            if (bystander.isEffectivelyDead() && shooter != null) {
                grantKillExperience(shooter, bystander);
            }
        }
    }

    /**
     * How far {@code point} is from {@code thing}'s bounding sphere — from its centre, less its radius, never below
     * nothing: the reference's {@code distCalcProc_BoundaryAndBoundary_3D} for a point and a thing.
     */
    static float gapTo(GameObject thing, Coord3D point) {
        var shape = thing.getGeometry();
        var at = thing.getPosition();
        float dx = at.x() - point.x();
        float dy = at.y() - point.y();
        float dz = at.z() + shape.sphereCentreHeight() - point.z();
        return Math.max(0f, (float) Math.sqrt(dx * dx + dy * dy + dz * dz) - shape.boundingSphereRadius());
    }

    /**
     * Whether the shot's blast hurts {@code candidate}, by whom its weapon says it hurts; enemies where it says none.
     * Read off what went off, as the reference reads it: {@code SELF} is what went off and its maker, {@code
     * NOT_SIMILAR} its template and its allies.
     */
    private static boolean hurts(uz.dukeengine.core.thing.World world, Shot shot, GameObject shooter,
            GameObject candidate) {
        var affects = shot.weapon().affects();
        if (isSelf(shot, shooter, candidate)) {
            return affects.contains(Weapon.Affects.SELF);
        }
        if (candidate.getTargetableFrom() > world.getFrame() || candidate.hasStatus(ObjectStatus.HIDDEN)) {
            return false; // nobody's target yet, a blast's no more than a gun's; or not there to be caught
        }
        var relationship = candidate.getPlayerIndex() == shot.side() ? Relationship.ALLIES
                : world.getRelationship(shot.side(), candidate.getPlayerIndex());
        if (affects.isEmpty()) {
            return relationship == Relationship.ENEMIES;
        }
        if (affects.contains(Weapon.Affects.NOT_SIMILAR) && relationship == Relationship.ALLIES
                && candidate.getTemplate().name().equals(wentOff(shot, shooter))) {
            return false; // a Terrorist's charge spares the Terrorists beside him, not an enemy's
        }
        if (affects.contains(Weapon.Affects.NOT_AIRBORNE) && candidate.hasStatus(ObjectStatus.AIRBORNE)) {
            return false;
        }
        return affects.contains(switch (relationship) {
            case ALLIES -> Weapon.Affects.ALLIES;
            case ENEMIES -> Weapon.Affects.ENEMIES;
            case NEUTRAL -> Weapon.Affects.NEUTRALS;
        });
    }

    /**
     * What went off and its maker, as the reference's {@code source == victim || source->getProducerID() == victim}:
     * the shooter and the thing that made it — or, a shell having gone off, the shell's launcher, the shooter.
     */
    private static boolean isSelf(Shot shot, GameObject shooter, GameObject candidate) {
        return candidate.getId().equals(shot.shooter())
                || shot.wentOff() == null && shooter != null && candidate.getId().equals(shooter.getProducer());
    }

    /** The template of what went off: the shell's where one carried the shot, else the shooter's; null if gone. */
    private static String wentOff(Shot shot, GameObject shooter) {
        return shot.wentOff() != null ? shot.wentOff() : shooter == null ? null : shooter.getTemplate().name();
    }

    /** Award the killer the victim's experience value, if both track experience. */
    private static void grantKillExperience(GameObject killer, GameObject victim) {
        var killerXp = killer.findModule(ExperienceModule.class);
        var victimXp = victim.findModule(ExperienceModule.class);
        if (killerXp != null && victimXp != null) {
            killerXp.addExperience(victimXp.getExperienceValue());
        }
    }

    /**
     * How far the target is, measured wall to wall. A tank parked against a
     * barracks is at range 0 from it, not half a building away — the same rule
     * SAGE uses, and the reason a short-ranged unit can hit a big structure.
     */
    private static float rangeTo(GameObject owner, GameObject victim) {
        return uz.dukeengine.core.thing.World.reachBetween(owner, victim);
    }

    /** Whether this is one of its frames to look for a target — see {@code RtsSimulation.setTargetScanFrames}. */
    private static boolean scansNow(uz.dukeengine.core.thing.World world, GameObject owner) {
        int every = world instanceof uz.dukeengine.rts.RtsSimulation rts ? rts.getTargetScanFrames() : 1;
        return every <= 1 || Math.floorMod(world.getFrame() + owner.getId().value(), every) == 0;
    }

    /**
     * Pick the nearest living enemy that one of the weapons it may pick by itself can reach and may be fired
     * at, as the new target, if any.
     */
    private void acquireTarget(uz.dukeengine.core.thing.World world, GameObject owner, List<Armed> armed) {
        float reach = 0f;
        for (var one : armed) {
            if (mayLookWith(armed, one) && one.clip().status() != WeaponStatus.OUT) {
                reach = Math.max(reach, range(owner, one.weapon()));
            }
        }
        var enemy = world.findClosestInReach(owner, reach, candidate ->
                candidate != owner
                        && !candidate.isContained()
                        && candidate.getBody() != null
                        && !candidate.isEffectivelyDead()
                        && world.getRelationship(owner.getPlayerIndex(), candidate.getPlayerIndex())
                                == Relationship.ENEMIES
                        && mayPickFor(armed, candidate));
        if (enemy != null) {
            target = enemy.getId();
        }
    }

    /** Whether a weapon it may pick by itself, and has rounds for, may be fired at {@code candidate}. */
    private boolean mayPickFor(List<Armed> armed, GameObject candidate) {
        if (candidate.isDisguisedFrom(getOwner().getPlayerIndex())) {
            return false; // passed off to its side as none of its targets
        }
        for (var one : armed) {
            if (mayLookWith(armed, one) && one.clip().status() != WeaponStatus.OUT
                    && mayHit(one.weapon(), candidate)) {
                return true;
            }
        }
        return false;
    }

    /** Whether its own look may pick {@code one}: the locked slot alone while a lock holds. */
    private boolean mayLookWith(List<Armed> armed, Armed one) {
        var locked = slotOf(armed, lockedSlot);
        return locked != null ? locked == one : mayPick(one, null);
    }
}
