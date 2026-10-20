package uz.dukeengine.combat.module;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import uz.dukeengine.combat.ArmedWorld;
import uz.dukeengine.combat.message.OrderSource;
import uz.dukeengine.combat.module.WeaponUpdate.Lock;
import uz.dukeengine.core.thing.Conditions;
import uz.dukeengine.core.thing.GameObject;

/**
 * A thing's weapons and which of them it uses: the sets and their slots, the set in use by the unit's condition words,
 * each weapon's clip kept across a swap, the lock on one slot, and the choice of a slot for a target. What a
 * {@link WeaponUpdate} arms itself with, as opposed to what it aims at.
 */
final class Loadout {

    private static final Logger LOG = Logger.getLogger(Loadout.class.getName());

    /** Weapon names a slot linked that the world did not have, said once each rather than once a frame. */
    private static final Set<String> MISSING = ConcurrentHashMap.newKeySet();

    /** What a slot preferred against its target counts as dealing: more than any weapon could. */
    private static final float PREFERRED = Float.MAX_VALUE;

    /** The slot the weapon written in place sits in: no preference, chosen as freely by the unit as by an order. */
    private static final WeaponSlot IN_PLACE = new WeaponSlot(null);

    private final GameObject owner;
    private final List<WeaponSet> sets;
    /** The one weapon written in place, when there are no sets — every unit from before sets. */
    private final List<Armed> inPlace;
    /** Every weapon the sets have armed, by name, with its clip: kept across swaps, and ticking through them. */
    private final Map<String, Clip> clips = new LinkedHashMap<>();

    /** The slot of the set in use its weapon is locked to, or -1. */
    private int lockedSlot = -1;
    private Lock lock;
    /** The set in use when it last looked, by its index: a change lets the lock go, as the reference's does. */
    private int setInUse = -1;

    Loadout(GameObject owner, WeaponUpdate.Data data) {
        this.owner = owner;
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

    /** The index of the set whose conditions the unit has, the most of them winning; -1 for none. */
    private int bestSet() {
        var said = new ArrayList<List<String>>(sets.size());
        for (var set : sets) {
            said.add(set.conditions());
        }
        return Conditions.bestFit(said, owner.getConditions());
    }

    /**
     * The weapons of the set in use, slot by slot: the one written in place where there are no sets, else the
     * set whose conditions the unit has, the most of them winning — {@link Conditions#bestFit}, the rule a
     * template's models are chosen by. A slot whose weapon the world does not have is left out, said once.
     */
    List<Armed> armed() {
        if (sets.isEmpty()) {
            return inPlace;
        }
        int fits = bestSet();
        if (fits < 0) {
            return List.of();
        }
        var world = owner.getWorld();
        var slots = sets.get(fits).slots();
        var armed = new ArrayList<Armed>(slots.size());
        for (int index = 0; index < slots.size(); index++) {
            var slot = slots.get(index);
            var weapon = world instanceof ArmedWorld arms ? arms.armoury().findWeapon(slot.weapon()) : null;
            if (weapon == null) {
                if (MISSING.add(String.valueOf(slot.weapon()))) {
                    LOG.warning(() -> owner.getTemplate().name() + "'s weapon set names the weapon '"
                            + slot.weapon() + "', which the game never gave the world — see addWeapons");
                }
                continue;
            }
            armed.add(new Armed(index, slot, weapon, clips.computeIfAbsent(weapon.name(), name -> clipOf(weapon))));
        }
        return armed;
    }

    /** The slot at {@code index} of {@code armed}, or null — for -1, or a slot the set does not have. */
    static Armed slotOf(List<Armed> armed, int index) {
        for (var one : armed) {
            if (one.index() == index) {
                return one;
            }
        }
        return null;
    }

    // ---- clips ----

    /** A frame has passed for every weapon it has, carried or swapped out: reloads run on through a swap. */
    void tick() {
        for (var armed : inPlace) {
            armed.clip().tick();
        }
        for (var clip : clips.values()) {
            clip.tick();
        }
    }

    /** Every clip it has full and every weapon ready at once. */
    void refill() {
        for (var armed : inPlace) {
            armed.clip().refill();
        }
        for (var clip : clips.values()) {
            clip.refill();
        }
    }

    /** Every clip it has filled to at least {@code share} of its rounds — see {@link WeaponUpdate#refill(float)}. */
    void refill(float share) {
        for (var armed : inPlace) {
            armed.clip().refill(share);
        }
        for (var clip : clips.values()) {
            clip.refill(share);
        }
    }

    /** Every slot in use but {@code fromSlot} kept from firing for at least {@code frames} from now. */
    void delaySlots(int fromSlot, int frames) {
        for (var one : armed()) {
            if (one.index() != fromSlot) {
                one.clip().delay(frames);
            }
        }
    }

    /** How long slot {@code slot}'s weapon waits between shots, its least; -1 for a slot not in use. */
    int reloadFramesOf(int slot) {
        var one = slotOf(armed(), slot);
        return one == null ? -1 : one.clip().leastDelay();
    }

    static boolean noneFiresOnTheMove(List<Armed> armed) {
        for (var one : armed) {
            if (one.weapon().attackOnTheMove()) {
                return false;
            }
        }
        return true;
    }

    static boolean allOut(List<Armed> armed) {
        for (var one : armed) {
            if (one.clip().status() != WeaponStatus.OUT) {
                return false;
            }
        }
        return true;
    }

    // ---- the lock ----

    /** See {@link WeaponUpdate#lock}. */
    boolean lock(int slot, Lock lock) {
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
    void unlock(Lock lock) {
        if (this.lock != null && (lock == Lock.PERMANENTLY || this.lock == Lock.TEMPORARILY)) {
            this.lockedSlot = -1;
            this.lock = null;
        }
    }

    int lockedSlot() {
        return lockedSlot;
    }

    /**
     * The set in use changed since it last looked: the lock is let go, as the reference's set swap lets it go — unless
     * the set it changed to shares the lock across sets, which keeps the lock and its slot as they were ({@code
     * WeaponSet::updateWeaponSet}, reading {@code WeaponLockSharedAcrossSets} on the set arrived at).
     */
    void noticeTheSetInUse() {
        if (sets.isEmpty()) {
            return;
        }
        int fits = bestSet();
        boolean shares = fits >= 0 && sets.get(fits).weaponLockSharedAcrossSets();
        if (fits != setInUse && setInUse >= 0 && !shares) {
            unlock(Lock.PERMANENTLY);
        }
        setInUse = fits;
    }

    // ---- what may be fired at what ----

    /** See {@link WeaponUpdate#canFireAt(GameObject, boolean, OrderSource)}. */
    boolean canFireAt(GameObject victim, boolean forced, OrderSource source) {
        if (!forced && victim.isDisguisedFrom(owner.getPlayerIndex())) {
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

    /**
     * Whether {@code weapon} may be fired at {@code victim}'s class — see {@link TargetRule} — and at its pitch
     * ({@link Weapon#withinPitch}).
     */
    boolean mayHit(Weapon weapon, GameObject victim) {
        var world = owner.getWorld();
        if (world != null && victim.getTargetableFrom() > world.getFrame()) {
            return false; // nobody's target yet
        }
        if (victim.isHiddenFrom(owner.getPlayerIndex())) {
            return false; // not there, or kept from this side: nothing may be fired at it, forced or not
        }
        if (world != null && !weapon.withinPitch(owner, victim, world.cellSize())) {
            return false; // above or below what it can be pointed at
        }
        if (weapon.targets().isEmpty()) {
            return true;
        }
        var rules = world instanceof ArmedWorld armed ? armed.armoury().getTargetRules() : List.<TargetRule>of();
        for (var named : TargetRule.classesOf(rules, victim)) {
            if (weapon.targets().contains(named)) {
                return true;
            }
        }
        return false;
    }

    /** The first armed slot whose weapon may hit the ground, as the player picks; null for none. */
    static Armed groundWeapon(List<Armed> armed) {
        for (var one : armed) {
            var targets = one.weapon().targets();
            if (one.slot().pickedBy(OrderSource.PLAYER) && (targets.isEmpty() || targets.contains("GROUND"))) {
                return one;
            }
        }
        return null;
    }

    // ---- which slot ----

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
    Armed choose(List<Armed> armed, GameObject victim, OrderSource byOrder) {
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
                    : victim.getBody().estimateDamage(Hands.dealt(owner, one.weapon(), one.weapon().damage()),
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

    /** The slot of the set in use it would fire at {@code victim} for an order of {@code byOrder}'s; null for none. */
    Armed choose(GameObject victim, OrderSource byOrder) {
        return victim == null ? null : choose(armed(), victim, byOrder);
    }

    /** Whether {@code one} may be picked for an order of {@code source}'s, or, null, for the unit's own look. */
    private static boolean mayPick(Armed one, OrderSource source) {
        return source != null ? one.slot().pickedBy(source)
                : one.slot().autoChoosable() && one.slot().pickedBy(OrderSource.GAME);
    }

    private static boolean preferredAgainst(WeaponSlot slot, GameObject victim) {
        for (var kind : slot.preferredAgainst()) {
            if (victim.isKindOf(kind)) {
                return true;
            }
        }
        return false;
    }

    /** Whether its own look may pick {@code one}: the locked slot alone while a lock holds. */
    boolean mayLookWith(List<Armed> armed, Armed one) {
        var locked = slotOf(armed, lockedSlot);
        return locked != null ? locked == one : mayPick(one, null);
    }
}
