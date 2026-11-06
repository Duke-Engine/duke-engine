package uz.dukeengine.combat.module;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import uz.dukeengine.combat.event.WeaponFired;
import uz.dukeengine.combat.event.WeaponWoundUp;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.World;

/**
 * Taking a shot: closing the last checks on a target (reach, wind-up, aim), the shot itself — handed to a launcher,
 * struck at once, or loosed along a line — the clip and the moment told. What a {@link WeaponUpdate} does once it has
 * decided to fire; where the shot comes down is {@link Landing}'s.
 */
final class Firing {

    private final GameObject owner;
    private final WeaponUpdate self;
    private final Loadout loadout;
    private final Engagement engagement;

    /** The frame each slot of a set, by its index, last fired: for what a client draws, nothing the game decides. */
    private final Map<Integer, Integer> firedOn = new HashMap<>();
    /** The slot of the set in use winding up, or -1; the frame its shot comes; and at what. */
    private int windingUp = -1;
    private int windUpEnds;
    private ObjectId windUpAt;
    /** What it last fired at: a wind-up before the first shot at a target is not wound up at this one. */
    private ObjectId lastFiredAt;

    Firing(GameObject owner, WeaponUpdate self, Loadout loadout, Engagement engagement) {
        this.owner = owner;
        this.self = self;
        this.loadout = loadout;
        this.engagement = engagement;
    }

    /** The slot winding up, or -1. */
    int windingUp() {
        return windingUp;
    }

    /** Whether slot {@code index} fired in the game's frame {@code frame}. */
    boolean firedOn(int index, int frame) {
        return firedOn.getOrDefault(index, -1) == frame;
    }

    /**
     * A new frame begins: a frame that does not go on winding a shot up drops it, as the reference's attack state does.
     *
     * @return the slot that was winding up, or -1
     */
    int beginFrame() {
        int was = windingUp;
        windingUp = -1;
        return was;
    }

    void stopWinding() {
        windingUp = -1;
    }

    /** Its wind-up and what it last fired at; not {@code firedOn}, read only for the frame just run. */
    void save(uz.dukeengine.core.save.SaveOut out) {
        out.writeInt(windingUp).writeInt(windUpEnds).writeId(windUpAt).writeId(lastFiredAt);
    }

    void load(uz.dukeengine.core.save.SaveIn in) {
        windingUp = in.readInt();
        windUpEnds = in.readInt();
        windUpAt = in.readId();
        lastFiredAt = in.readId();
    }

    /**
     * Fire on {@code victim} with {@code chosen}, if it is near enough, may be fired from here, is ready, is aimed and
     * has wound up — else leave it for a later frame, or, where it can never fire from where it stands, let it go.
     */
    void engage(World world, Armed chosen, GameObject victim, boolean walking, int wasWindingUp) {
        float gap = Hands.rangeTo(owner, victim);
        boolean leeching = engagement.leeching();
        if (!leeching && gap > Hands.range(owner, chosen.weapon())) {
            return; // out of range — wait for movement to close in
        }
        if (!leeching && Hands.insideLeast(owner, chosen.weapon(), gap)) {
            if (Hands.cannotBackAway(owner)) {
                self.holdFire(); // too near, and nowhere it can go to fire from
            }
            return; // too near — wait for movement to back off
        }
        if (walking && !chosen.weapon().attackOnTheMove()) {
            return; // this one is stood still for
        }
        if (chosen.clip().status() != WeaponStatus.READY) {
            return; // between shots, or refilling its clip
        }
        boolean windsOn = wasWindingUp == chosen.index() && victim.getId().equals(windUpAt);
        if (!windsOn && !aimed(chosen, victim)) {
            return; // a turret, or the thing, still turning to it — see WeaponAim
        }
        if (windsOn ? world.getFrame() < windUpEnds : windUp(world, chosen, victim)) {
            windingUp = chosen.index();
            return; // winding up; the shot comes as it ends
        }
        fire(world, victim, chosen);
    }

    /**
     * Start {@code chosen}'s wind-up at {@code victim}, if it winds up before this shot — the reference's {@code
     * Weapon::preFireWeapon} and {@code getPreAttackDelay}: before every shot, before the first at a target it did not
     * last fire at, or before the first of a full clip.
     *
     * @return whether it started one
     */
    private boolean windUp(World world, Armed chosen, GameObject victim) {
        var weapon = chosen.weapon();
        if (weapon.preAttackFrames() <= 0) {
            return false;
        }
        boolean winds = switch (weapon.preAttackType()) {
            case PER_SHOT -> true;
            case PER_ATTACK -> !victim.getId().equals(lastFiredAt);
            case PER_CLIP -> weapon.clipSize() <= 0 || chosen.clip().rounds() >= weapon.clipSize();
        };
        if (!winds) {
            return false;
        }
        windUpAt = victim.getId();
        windUpEnds = world.getFrame() + weapon.preAttackFrames();
        engagement.keepReach(weapon.leechRange());
        // Told so the picture starts the swing now, its blow meeting the shot at the wind-up's end.
        world.post(new WeaponWoundUp(world.getFrame(), owner.getId(), victim.getId(),
                weapon.name(), chosen.index(), weapon.preAttackFrames()));
        return true;
    }

    /** Whether every {@link WeaponAim} on the thing says {@code chosen} is aimed at {@code victim}, in module order. */
    private boolean aimed(Armed chosen, GameObject victim) {
        for (var module : owner.getModules()) {
            if (module instanceof WeaponAim aim && !aim.aimed(chosen.index(), chosen.weapon(), victim)) {
                return false;
            }
        }
        return true;
    }

    private void fire(World world, GameObject victim, Armed chosen) {
        firedOn.put(chosen.index(), world.getFrame());
        var weapon = chosen.weapon();
        lastFiredAt = victim.getId();
        engagement.keepReach(weapon.leechRange());
        var shot = Hands.shotOf(world, owner, chosen);
        for (var listener : Landing.shotListeners(owner)) {
            listener.onFired(shot, victim);
        }

        // A shot was fired either way — the reload runs and the moment is
        // announced — but whether it lands now is the launcher's to decide.
        boolean inFlight = launched(launcher -> launcher.launch(owner, victim, shot));
        boolean thrownOff = Landing.offsetOf(victim) != null;
        if (!inFlight && !thrownOff) {
            // scaled by its armour
            Landing.deal(owner, shot, victim, shot.damage(), Landing.middleOf(victim), owner.getPosition());
        }
        if (spent(world, chosen, shot) && chosen.index() == loadout.lockedSlot()) {
            loadout.unlock(WeaponUpdate.Lock.TEMPORARILY); // its clip is empty: a lock until then is over
        }
        told(world, chosen, victim.getId(), owner.getPosition(), Landing.aimPoint(victim),
                weapon.isContact(world.cellSize()), shot);
        if (engagement.lastShotAt(victim.getId())) {
            self.holdFire(); // the most it was ordered to fire at it are fired: the attack is over
        }

        if (inFlight) {
            return; // nothing has been hit yet; the blast and the kill wait for land()
        }
        if (thrownOff) {
            // it missed: only its blast, round where it was aimed, may hurt the thing
            var point = Landing.aimPoint(victim);
            Landing.struck(world, shot, owner, null, point, point, owner.getPosition());
            return;
        }
        Landing.struck(world, shot, owner, victim, victim.getPosition(), Landing.middleOf(victim), owner.getPosition());
        if (victim.isEffectivelyDead()) {
            self.holdFire();
        }
    }

    /** One frame of a ground attack: close to the place, then fire on it from the range of the weapon that may. */
    void onTheGround(World world, List<Armed> armed, boolean walking) {
        var chosen = Loadout.groundWeapon(armed);
        if (chosen == null) {
            engagement.giveUpGround();
            return;
        }
        var place = engagement.groundTarget();
        var here = owner.getPosition();
        float dx = place.x() - here.x();
        float dy = place.y() - here.y();
        float gap = (float) Math.sqrt(dx * dx + dy * dy);
        var legs = owner.getLocomotor();
        if (gap > Hands.range(owner, chosen.weapon())) {
            if (legs != null && !legs.isMoving()) {
                legs.moveTo(place); // ponytail: walked toward the place, stopped once in range
            }
            return;
        }
        if (legs != null && legs.isMoving()) {
            legs.stop();
        }
        if (walking && !chosen.weapon().attackOnTheMove() || chosen.clip().status() != WeaponStatus.READY) {
            return;
        }
        owner.setOrientation((float) StrictMath.atan2(dy, dx)); // ponytail: turned at once; no wind-up, no aim
        firedOn.put(chosen.index(), world.getFrame());
        var weapon = chosen.weapon();
        var shot = Hands.shotOf(world, owner, chosen);
        boolean inFlight = launched(launcher -> launcher.launchAt(owner, place, shot));
        spent(world, chosen, shot);
        told(world, chosen, null, here, place, weapon.isContact(world.cellSize()), shot);
        if (!inFlight) {
            Landing.struck(world, shot, owner, null, place, place, here);
        }
    }

    /**
     * A swing toward its point: turned to it, wound up as the weapon winds, then loosed along the line by a launcher
     * that carries to a place, or else struck at what its attack toward the point names — or at nothing.
     */
    void toward(World world, Armed chosen, boolean walking, Coord3D toward) {
        var here = owner.getPosition();
        float dx = toward.x() - here.x();
        float dy = toward.y() - here.y();
        if (dx != 0f || dy != 0f) {
            owner.setOrientation((float) StrictMath.atan2(dy, dx));
        }
        var weapon = chosen.weapon();
        if (walking && !weapon.attackOnTheMove() || chosen.clip().status() != WeaponStatus.READY) {
            return;
        }
        if (weapon.preAttackFrames() > 0) {
            if (engagement.pointWindEnds() < 0) {
                engagement.windPointUntil(world.getFrame() + weapon.preAttackFrames());
                world.post(new WeaponWoundUp(world.getFrame(), owner.getId(), null,
                        weapon.name(), chosen.index(), weapon.preAttackFrames()));
            }
            if (world.getFrame() < engagement.pointWindEnds()) {
                windingUp = chosen.index();
                return;
            }
            engagement.windPointUntil(-1);
        }
        var attack = engagement.pointAttack();
        float reach = Hands.range(owner, weapon);
        double facing = StrictMath.atan2(dy, dx);
        var along = new Coord3D(here.x() + (float) StrictMath.cos(facing) * reach,
                here.y() + (float) StrictMath.sin(facing) * reach, here.z());
        var shot = Hands.shotOf(world, owner, chosen);
        if (launched(launcher -> launcher.launchAt(owner, along, shot))) {
            firedOn.put(chosen.index(), world.getFrame());
            spent(world, chosen, shot);
            told(world, chosen, null, here, along, false, shot);
        } else {
            var victim = attack.struck(self, reach, (float) facing);
            if (victim != null) {
                fire(world, victim, chosen); // struck as a targeted blow is
            } else {
                firedOn.put(chosen.index(), world.getFrame());
                spent(world, chosen, shot);
                told(world, chosen, null, here, along, weapon.isContact(world.cellSize()), shot);
            }
        }
        // A blow that killed lets its target go; the attack toward the point is not over for that.
        engagement.swung(attack);
    }

    /**
     * A swing the game timed itself: one shot, its roll drawn once, worth {@code share} of the weapon's, struck now at
     * each of {@code victims} that is still there to hit, with no wind-up, no reach check and no reload begun -- so a
     * game's own combo is not held to the weapon's timing. A launcher may still take it (the sword's seal), and the
     * moment is told once, at the first victim or along the facing.
     *
     * @return how many it struck
     */
    int blow(World world, Armed chosen, List<GameObject> victims, float share) {
        var shot = Hands.shotOf(world, owner, chosen).scaled(share);
        firedOn.put(chosen.index(), world.getFrame());
        GameObject first = null;
        int struck = 0;
        for (var victim : victims) {
            if (victim.isEffectivelyDead() || victim.getBody() == null) {
                continue;
            }
            first = first == null ? victim : first;
            struck++;
            for (var listener : Landing.shotListeners(owner)) {
                listener.onFired(shot, victim);
            }
            if (launched(launcher -> launcher.launch(owner, victim, shot))) {
                continue; // in flight: it lands when its launcher says
            }
            Landing.deal(owner, shot, victim, shot.damage(), Landing.middleOf(victim), owner.getPosition());
            Landing.struck(world, shot, owner, victim, victim.getPosition(), Landing.middleOf(victim),
                    owner.getPosition());
        }
        var weapon = chosen.weapon();
        var here = owner.getPosition();
        var along = new Coord3D(here.x() + (float) StrictMath.cos(owner.getOrientation()),
                here.y() + (float) StrictMath.sin(owner.getOrientation()), here.z());
        told(world, chosen, first == null ? null : first.getId(), owner.getPosition(),
                first == null ? along : Landing.aimPoint(first), weapon.isContact(world.cellSize()), shot);
        return struck;
    }

    /**
     * Offer the shot to the {@link ProjectileLauncher}s on this unit, the first found, in module order, which is fixed
     * when the object is built — so two peers hand the same shot to the same launcher.
     *
     * @return whether one took it
     */
    private boolean launched(Predicate<ProjectileLauncher> takes) {
        for (var module : owner.getModules()) {
            if (module instanceof ProjectileLauncher launcher && takes.test(launcher)) {
                return true;
            }
        }
        return false;
    }

    /** A shot taken: the clip counts it, the reload runs and its listeners are told; whether that emptied the clip. */
    private boolean spent(World world, Armed chosen, Shot shot) {
        boolean emptied = chosen.clip().fired(world.random(), Hands.rateOfFire(owner, chosen.weapon()));
        for (var listener : Landing.shotListeners(owner)) {
            listener.onSpent(shot, chosen.clip().waitFrames(), emptied);
        }
        return emptied;
    }

    /** The moment of a shot, told. */
    private void told(World world, Armed chosen, ObjectId at, Coord3D from, Coord3D to, boolean contact, Shot shot) {
        world.post(new WeaponFired(world.getFrame(), owner.getId(), at, from, to, chosen.weapon().name(),
                chosen.index(), contact, shot.radius()).ranked(Hands.rankOf(owner)));
    }
}
