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

    /**
     * A weapon whose range, this much over, is under a pathfinding cell strikes what it touches — the
     * reference's {@code ATTACK_RANGE_FUDGE} in {@code WeaponTemplate::isContactWeapon}.
     */
    private static final float CONTACT_FUDGE = 1.05f;

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
    /** Whether the target was an order's, rather than one it found for itself — which slots it may use. */
    private boolean ordered;

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
     * Order it to engage {@code target} — refused, and whatever it was doing kept, when the target is
     * something none of its weapons may be fired at ({@link #canFireAt}).
     *
     * @return whether it took the order
     */
    public boolean attack(ObjectId target) {
        var world = getOwner().getWorld();
        var victim = world == null || target == null ? null : world.findObject(target);
        if (victim != null && !canFireAt(victim)) {
            return false;
        }
        this.target = target;
        this.ordered = true;
        return true;
    }

    /**
     * Whether any weapon of the set in use may be fired at {@code victim} at all — its class, not its range or
     * its side: one of the classes the world's {@link TargetRule}s give it is among the ones a weapon names. A
     * weapon that names none may be fired at anything.
     */
    public boolean canFireAt(GameObject victim) {
        for (var armed : armed()) {
            if (mayHit(armed.weapon(), victim)) {
                return true;
            }
        }
        return false;
    }

    public void holdFire() {
        this.target = null;
    }

    public boolean isAttacking() {
        return target != null;
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
        var chosen = choose(armed(), victim, ordered);
        return chosen != null && rangeTo(getOwner(), victim) <= chosen.weapon().attackRange();
    }

    public ObjectId getTarget() {
        return target;
    }

    // ---- each frame ----

    @Override
    public void update() {
        if (getOwner().isEffectivelyDead() || getOwner().isContained()
                || getOwner().hasStatus(ObjectStatus.DISABLED) || getOwner().hasStatus(ObjectStatus.SOLD)) {
            return; // dead, inside a transport, disabled or being sold — hold fire
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
            acquireTarget(world, owner, armed);
            if (target == null) {
                return;
            }
            ordered = false;
        }

        var victim = world.findObject(target);
        if (victim == null || victim.isEffectivelyDead() || victim.getBody() == null) {
            target = null;
            return;
        }
        if (world.getRelationship(owner.getPlayerIndex(), victim.getPlayerIndex()) == Relationship.ALLIES) {
            target = null; // never fire on allies
            return;
        }
        var chosen = choose(armed, victim, ordered);
        if (chosen == null) {
            target = null; // it took off, or was never something this could hit
            return;
        }
        if (rangeTo(owner, victim) > chosen.weapon().attackRange()) {
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
        var weapon = chosen.weapon();
        var shot = new Shot(owner.getId(), owner.getPlayerIndex(), weapon, chosen.index(), dealt(owner, weapon));

        // A shot was fired either way — the reload runs and the moment is
        // announced — but whether it lands now is the launcher's to decide.
        boolean inFlight = handOver(owner, victim, shot);
        if (!inFlight) {
            victim.getBody().damage(shot.damage(), weapon.damageType(), blow(shot), middleOf(victim),
                    owner.getPosition()); // scaled by its armour
        }
        chosen.clip().fired(world.random(), rateOfFire(owner));
        world.post(new WeaponFired(world.getFrame(), owner.getId(), victim.getId(),
                owner.getPosition(), victim.getPosition(), weapon.name(), chosen.index(),
                weapon.attackRange() * CONTACT_FUDGE < world.cellSize(), weapon.splashRadius()));

        if (inFlight) {
            return; // nothing has been hit yet; the blast and the kill wait for land()
        }
        struck(world, shot, owner, victim, victim.getPosition(), middleOf(victim), owner.getPosition());
        if (victim.isEffectivelyDead()) {
            target = null;
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
        var hit = victim == null || victim.isEffectivelyDead() || victim.getBody() == null ? null : victim;
        if (hit != null) {
            hit.getBody().damage(shot.damage(), shot.weapon().damageType(), blow(shot), middleOf(hit),
                    from == null ? where : from);
        }
        struck(world, shot, shooter, hit, where, where, from == null ? where : from);
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

    /** The death a shot deals if it kills, and whose it is: its weapon's, and its shooter's. */
    private static Death blow(Shot shot) {
        return new Death(shot.weapon().deathType(), shot.shooter());
    }

    /**
     * Everything that follows the direct hit, one way for a shot that hit at once and one that was carried: the
     * blast, the kill experience, and the moment it landed.
     */
    private static void struck(uz.dukeengine.core.thing.World world, Shot shot, GameObject shooter,
            GameObject victim, Coord3D where, Coord3D shown, Coord3D from) {
        if (shot.weapon().splashRadius() > 0f) {
            splash(world, shot, shooter, victim, where);
        }
        if (victim != null && victim.isEffectivelyDead() && shooter != null) {
            grantKillExperience(shooter, victim);
        }
        world.post(new ShotLanded(world.getFrame(), shot.shooter(),
                victim == null ? null : victim.getId(), shot.weapon().name(), shown, from,
                shot.weapon().splashRadius()));
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
     *   <li>A slot is passed over if the unit may not pick it by itself and nobody ordered this target; if it
     *       is out and does not reload itself; if its weapon may not be fired at the target's class; or if it
     *       would do no damage to it after armour.
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
    private Armed choose(List<Armed> armed, GameObject victim, boolean byOrder) {
        if (armed.isEmpty()) {
            return null;
        }
        float scale = dealtScale(getOwner());
        Armed ready = null;
        Armed fallBack = null;
        float mostReady = 0f;
        float mostFallBack = 0f;
        // Backwards, and >= below, so that a tie goes to the lower slot.
        for (int at = armed.size() - 1; at >= 0; at--) {
            var one = armed.get(at);
            if (!byOrder && !one.slot().autoChoosable()) {
                continue;
            }
            var status = one.clip().status();
            if (status == WeaponStatus.OUT || !mayHit(one.weapon(), victim)) {
                continue;
            }
            float damage = victim.getBody() == null ? 0f
                    : victim.getBody().estimateDamage(one.weapon().damage() * scale, one.weapon().damageType());
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
        return mayHit(first.weapon(), victim) && first.clip().status() != WeaponStatus.OUT ? first : null;
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
    private static float dealt(GameObject owner, Weapon weapon) {
        float dealt = weapon.damage() * damageModifiers(owner);
        var shooter = RtsPlayer.of(owner.getWorld(), owner.getPlayerIndex());
        if (shooter != null) {
            dealt *= shooter.getWeaponDamageBonus(); // player-wide upgrade bonus
        }
        return dealt;
    }

    /** This unit's modifiers times its side's bonus — the same for every weapon it carries. */
    private static float dealtScale(GameObject owner) {
        float scale = damageModifiers(owner);
        var shooter = RtsPlayer.of(owner.getWorld(), owner.getPlayerIndex());
        if (shooter != null) {
            scale *= shooter.getWeaponDamageBonus(); // player-wide upgrade bonus
        }
        return scale;
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

    /** Everything on this unit that changes how fast it fires, multiplied together in module order. */
    private static float rateOfFire(GameObject owner) {
        float multiplier = 1f;
        for (var module : owner.getModules()) {
            if (module instanceof RateOfFireModifier modifier) {
                multiplier *= modifier.rateOfFireMultiplier();
            }
        }
        return multiplier;
    }

    /**
     * Area damage to the enemies of the shot's side round where it struck — neither the victim, which took the
     * direct hit, nor the shooter. What the blast kills is the shooter's to be credited with, if it is there.
     */
    private static void splash(uz.dukeengine.core.thing.World world, Shot shot, GameObject shooter,
            GameObject victim, Coord3D where) {
        var caught = world.objectsInRange(where, shot.weapon().splashRadius(), candidate ->
                candidate != victim
                        && !candidate.getId().equals(shot.shooter())
                        && !candidate.isContained()
                        && candidate.getBody() != null
                        && !candidate.isEffectivelyDead()
                        && world.getRelationship(shot.side(), candidate.getPlayerIndex()) == Relationship.ENEMIES);
        for (var bystander : caught) {
            bystander.getBody().damage(shot.damage(), shot.weapon().damageType(), blow(shot),
                    nearestOf(bystander, where), where);
            if (bystander.isEffectivelyDead() && shooter != null) {
                grantKillExperience(shooter, bystander);
            }
        }
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

    /**
     * Pick the nearest living enemy that one of the weapons it may pick by itself can reach and may be fired
     * at, as the new target, if any.
     */
    private void acquireTarget(uz.dukeengine.core.thing.World world, GameObject owner, List<Armed> armed) {
        float reach = 0f;
        for (var one : armed) {
            if (one.slot().autoChoosable() && one.clip().status() != WeaponStatus.OUT) {
                reach = Math.max(reach, one.weapon().attackRange());
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
        for (var one : armed) {
            if (one.slot().autoChoosable() && one.clip().status() != WeaponStatus.OUT
                    && mayHit(one.weapon(), candidate)) {
                return true;
            }
        }
        return false;
    }
}
