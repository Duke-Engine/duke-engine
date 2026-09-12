package uz.duke.dungeon.ai;

import uz.duke.core.math.Coord3D;
import uz.duke.core.module.MoveUpdate;
import uz.duke.core.player.Relationship;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ObjectId;
import uz.duke.core.thing.World;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.skill.SkillBook;
import uz.duke.game.script.UnitScript;
import uz.duke.rts.module.WeaponUpdate;

/**
 * What the hero does with an order: walk to what he was pointed at, shoot what he
 * can see, and do one thing at a time.
 *
 * <p>The engine deliberately splits the two halves of an attack. A weapon fires
 * when its target is in range and never moves the owner — closing the distance is
 * the locomotor's job. In an RTS that is right: an attack order alone means
 * "shoot it from where you stand". The 3D client sends only {@code AttackObject}
 * on a right-click, so in a dungeon the hero would stand and stare at a skeleton
 * across the room. This supplies the missing half.
 *
 * <p><b>It also chooses what he shoots at.</b> The engine's own acquisition takes
 * the nearest enemy in range and knows nothing about walls, so
 * {@link uz.duke.dungeon.combat.EyesOnly} switches it off and the choosing lands
 * here — where the line of sight can be part of it. That turns out to simplify
 * rather than complicate: because every target is now either one this class named
 * or one the player did, telling an order from something noticed in passing is a
 * fact rather than a guess. It used to be guessed from the distance, with a
 * comment about why the guess had to be made exactly once.
 *
 * <p><b>The player's latest order wins.</b> Ordering an attack while he is walking
 * stops the walk; casting a skill stops the walk and abandons whatever he was
 * sent at. He does one of the things he was told and then stands still, rather
 * than resuming something from a minute ago that the player has long since
 * changed his mind about.
 *
 * <p>Movement is issued through {@link UnitScript#moveTo} which drives the
 * locomotor directly, not through the command queue — a {@code MoveTo} command
 * clears the weapon's target, so ordering the hero to walk to his target would
 * cancel the very attack that sent him.
 */
public final class HeroBrain extends UnitScript {

    private final DungeonSettings settings;

    /**
     * What the player pointed him at, as opposed to what this class picked.
     *
     * <p>Only a note about the weapon's target, never a second copy of it. The
     * order itself lives on the weapon, which is what makes cancelling one free: a
     * {@code MoveTo} takes the weapon's target away, and an order that is no
     * longer the weapon's is no longer an order.
     */
    private ObjectId sentAt;

    /** The frame the order arrived, so a later skill can supersede it. */
    private int orderedAtFrame;

    /** What this class last named, so it is never mistaken for something ordered. */
    private ObjectId picked;

    /** Cached: his weapon's range, read from his own template. See {@link #reachOfHisWeapon}. */
    private float weaponRange = -1f;

    /**
     * Where his last walking order sent him, so the next one is only given if what
     * he is chasing has actually gone somewhere. See {@link Chasing} for why
     * re-ordering him to the place he is already walking to is not free.
     */
    private Coord3D sentAfter;

    /**
     * The spot in front of him that a body is standing in, while one is — so he
     * stands still instead of shoving. Null when he is free to walk.
     *
     * <p>The spot rather than "am I blocked", because he has to keep asking about
     * the <em>same</em> piece of floor even as he turns to face what he is
     * shooting: see {@link WayAhead}.
     */
    private Coord3D waitingOn;

    /**
     * Where a plain walking order was taking him when a body stopped it, so he can
     * carry on rather than simply lose the order. Null unless he is waiting.
     */
    private Coord3D errand;

    /** The standing orders his player has given; see {@link Orders}. */
    private final Orders orders;

    public HeroBrain(DungeonSettings settings) {
        this(settings, new Orders());
    }

    public HeroBrain(DungeonSettings settings, Orders orders) {
        this.settings = settings;
        this.orders = orders == null ? new Orders() : orders;
    }

    @Override
    public void onUpdate() {
        var weapon = unit().findModule(WeaponUpdate.class);
        var move = unit().findModule(MoveUpdate.class);
        if (weapon == null || move == null) {
            return;
        }
        if (standingStill(weapon, move)) {
            return;
        }
        forgetOrdersOverriddenByASkill(weapon);
        var current = weapon.isAttacking() ? world().findObject(weapon.getTarget()) : null;
        forgetAPickThatIsNoLongerHis(weapon);
        current = noticeWhatHeHasBeenPointedAt(current, move);
        current = dropWhatHeCannotShoot(weapon, current);

        var ordered = current != null && current.getId().equals(sentAt) ? current : null;
        if (ordered == null) {
            mindTheWayOnHisErrand(move);
            standAndShoot(weapon, move, current);
            return;
        }

        if (World.reachBetween(unit(), ordered) <= howCloseHeGets()
                && canSee(ordered)) {
            move.stop(); // close enough and in sight; standing still is how he fires
            Facing.turnToward(unit(), ordered);
            waitingOn = null;
        } else {
            advanceOn(move, ordered);
        }
    }

    /**
     * Walk at what he was sent at — unless there is a body in the doorway, in
     * which case stand and let it pass.
     *
     * <p>Standing still is the fix rather than a symptom of giving up. With no
     * room in front of him the locomotor steps aside instead, is pushed back, and
     * tries again thirty times a second, and re-planning less often cannot stop it
     * because the shuffle happens between the orders rather than because of them.
     * See {@link WayAhead}.
     */
    private void advanceOn(MoveUpdate move, GameObject quarry) {
        if (waitingOn != null) {
            if (WayAhead.stillShut(unit(), waitingOn, quarry.getPosition(),
                    settings.wayAheadProbe(), quarry)) {
                return; // the way is shut. Stand, and look again next frame.
            }
            // It opened — or the wait stopped being about anything. Off he goes,
            // whether or not the quarry has moved since: what changed was the
            // road, and Chasing knows only about quarries.
            waitingOn = null;
            sendAfter(quarry);
            return;
        }
        var ahead = WayAhead.noWayPast(unit(), settings.wayAheadProbe(), quarry);
        if (ahead != null) {
            waitingOn = ahead;
            if (move.isMoving()) {
                move.stop();
            }
            return;
        }
        if (Chasing.worthReplanning(sentAfter, quarry.getPosition())
                && (!move.isMoving() || frame() % settings.heroRepathFrames() == 0)) {
            // Off at once when he is standing, and corrected on the way — but
            // only when there is something to correct. See Chasing: ordering him
            // to the place he is already going restarts him, and it was the
            // restarting that kept him shoving at a body he could not pass.
            sendAfter(quarry);
        }
    }

    /**
     * Told to stop: stand, start nothing, and wait to be told otherwise.
     *
     * <p>Asked before anything else, because it outranks everything else. The
     * dropping of what he was already doing happened when the order arrived (see
     * {@code Dungeon}); what is left is refusing to begin anything, which is the
     * part that has to be said every frame — the weapon finds its own targets, and
     * acquires and fires in the same call.
     *
     * <p><b>And it ends the moment the player wants something.</b> A walk under
     * way or a target on his weapon can only have got there since, because both
     * were taken away when he was told to stop, and nothing here puts them back.
     * So either is the player changing his mind, and the standing order goes —
     * otherwise Stop would be a state he could enter and never leave.
     *
     * @return whether he is standing, and everything below should be skipped
     */
    private boolean standingStill(WeaponUpdate weapon, MoveUpdate move) {
        if (!orders.isHolding(unit().getPlayerIndex())) {
            wasStanding = false;
            return false;
        }
        if (!wasStanding) {
            // The frame the order lands: drop the walk and the target. It has to
            // be here rather than only where the command is handled, or setting
            // the order any other way leaves him finishing what he was doing --
            // and the rule below would then read that as the player changing his
            // mind and take the order straight off again.
            wasStanding = true;
            sentAt = null;
            picked = null;
            sentAfter = null;
            forgetTheErrand();
            weapon.holdFire();
            move.stop();
            return true;
        }
        if (move.isMoving() || weapon.isAttacking()) {
            orders.hold(unit().getPlayerIndex(), false);
            wasStanding = false;
            return false;
        }
        weapon.holdFire();
        return true;
    }

    /** Whether he was already standing last frame, so a new order can be told apart. */
    private boolean wasStanding;

    /**
     * The same courtesy on a plain walking order: stop rather than shove, and
     * carry on when the way clears.
     *
     * <p>A walk the player asked for has no quarry to chase, so nothing here
     * re-plans and the locomotor's own patience does end the shuffle — after two
     * seconds. Two seconds of a hero treading the floor is still the fault the
     * player reported, and worse in one way than the monster's: it is <em>his</em>
     * hero, doing it where he is looking.
     *
     * <p>Where he was going is kept because stopping throws it away, and the
     * whole point is that this is a pause and not a cancellation. It is given up
     * the moment anything else is asked of him — a new walk, an attack, a skill —
     * or he would set off again for somewhere the player had long since thought
     * better of.
     */
    private void mindTheWayOnHisErrand(MoveUpdate move) {
        if (errand != null) {
            if (move.isMoving()) {
                forgetTheErrand(); // he has been given something else to do
                return;
            }
            if (WayAhead.stillShut(unit(), waitingOn, errand, settings.wayAheadProbe(), null)) {
                return;
            }
            var goal = errand;
            forgetTheErrand();
            moveTo(goal.x(), goal.y());
            return;
        }
        if (!move.isMoving()) {
            return;
        }
        var ahead = WayAhead.noWayPast(unit(), settings.wayAheadProbe(), null);
        if (ahead != null) {
            errand = move.getGoal();
            waitingOn = ahead;
            move.stop();
        }
    }

    private void forgetTheErrand() {
        errand = null;
        waitingOn = null;
    }

    /** Send him walking at something, remembering where it was when he set off. */
    private void sendAfter(GameObject quarry) {
        sentAfter = quarry.getPosition();
        moveTo(sentAfter.x(), sentAfter.y());
    }

    /**
     * Whether he can see a creature — near enough, nothing between, nothing
     * standing higher.
     *
     * <p>Three questions rather than one, and the one that used to be asked here
     * was only the middle of them. See {@link SightLine#sees}.
     */
    private boolean canSee(GameObject creature) {
        return SightLine.sees(unit(), creature, settings.storeyHeight());
    }

    /**
     * Stop calling a target his own once the weapon has let go of it.
     *
     * <p>Without this the note outlives the target it was about, and the player
     * ordering an attack on the very creature the hero had been shooting a minute
     * ago is read as the hero's own choice — so the order does nothing and he
     * walks on. A {@code MoveTo} clears the weapon, which is exactly when this
     * matters.
     */
    private void forgetAPickThatIsNoLongerHis(WeaponUpdate weapon) {
        if (picked != null && !picked.equals(weapon.getTarget())) {
            picked = null;
        }
    }

    /**
     * A target this class did not name is one the player did.
     *
     * <p>And it is his most recent word, so it stops whatever he was doing: an
     * attack ordered mid-walk means stop and shoot, not finish the errand first.
     * A skill aims his weapon as part of casting, and that is not an order —
     * {@link SkillBook} says which target is its own.
     */
    private GameObject noticeWhatHeHasBeenPointedAt(GameObject current, MoveUpdate move) {
        if (current == null || current.isEffectivelyDead()) {
            return current;
        }
        var id = current.getId();
        if (id.equals(sentAt) || id.equals(picked) || id.equals(aimedAtBySkill())) {
            return current;
        }
        sentAt = id;
        orderedAtFrame = frame();
        sentAfter = null; // a new order is a new chase, however near the old one stood
        forgetTheErrand();
        move.stop();
        return current;
    }

    /**
     * Let go of a target that is dead, or that he cannot see and was never sent
     * at.
     *
     * <p>Something he was <em>sent</em> at is kept out of sight on purpose: he
     * walks until he can see it, which is what being pointed at something round a
     * corner or off in the dark ought to mean. Something he merely picked up is
     * dropped, so the next frame can name something he can actually shoot.
     */
    private GameObject dropWhatHeCannotShoot(WeaponUpdate weapon, GameObject current) {
        if (current == null) {
            return null;
        }
        boolean ordered = current.getId().equals(sentAt);
        if (current.isEffectivelyDead() || (!ordered && !canSee(current))) {
            weapon.holdFire();
            if (ordered) {
                sentAt = null;
            }
            return null;
        }
        return current;
    }

    /**
     * With nothing ordered: keep an eye on what he is already shooting, or find
     * something to shoot.
     *
     * <p>The nearest enemy in reach that he can actually see — which is the
     * engine's own rule with the one thing it does not know added to it. The
     * locomotor only sets a heading while walking, so a hero standing still is
     * turned by hand or he shoots over his shoulder.
     *
     * <p><b>Only while he is standing.</b> Nothing observable is lost by waiting —
     * his bow does not fire on the move either way — and it buys the one thing
     * that cannot otherwise be known: while he is walking, a target on his weapon
     * can only have been put there by the player, so an attack ordered mid-errand
     * is recognisable as an order even when it names the very monster he would
     * have picked himself. Without it, a move order and an attack order on the
     * same creature are indistinguishable, and the second silently does nothing.
     */
    private void standAndShoot(WeaponUpdate weapon, MoveUpdate move, GameObject current) {
        if (current != null) {
            if (!move.isMoving()) {
                Facing.turnToward(unit(), current);
            }
            return;
        }
        if (move.isMoving()) {
            return; // walking somewhere; what he passes is not his business
        }
        var seen = world().findClosestInReach(unit(), reachOfHisWeapon(), candidate ->
                candidate != unit()
                        && !candidate.isContained()
                        && candidate.getBody() != null
                        && !candidate.isEffectivelyDead()
                        && world().getRelationship(unit().getPlayerIndex(),
                                candidate.getPlayerIndex()) == Relationship.ENEMIES
                        && canSee(candidate));
        if (seen != null) {
            picked = seen.getId();
            weapon.attack(seen.getId());
        }
    }

    /**
     * A skill cast after an order replaces it.
     *
     * <p>"Do one thing at a time": dashing away from something he was sent at is
     * the player changing his mind, and coming back afterwards is not what the
     * dash was for.
     *
     * <p>The weapon has to let go of it as well. Forgetting the order on its own
     * leaves the target sitting on the weapon, and the next frame — finding a
     * target nobody here claims — reads it as a brand new order and sends him
     * straight back after it. The exception is a target the skill itself is aiming
     * at, which is the whole point of a drawn shot.
     */
    private void forgetOrdersOverriddenByASkill(WeaponUpdate weapon) {
        var book = unit().findModule(SkillBook.class);
        if (sentAt == null || book == null || book.getLastCastFrame() <= orderedAtFrame) {
            return;
        }
        if (sentAt.equals(weapon.getTarget()) && !sentAt.equals(book.getLastAimedAt())) {
            weapon.holdFire();
        }
        sentAt = null;
        forgetTheErrand();
    }

    /** The target a skill pointed his weapon at, which is the skill's and not an order. */
    private ObjectId aimedAtBySkill() {
        var book = unit().findModule(SkillBook.class);
        return book == null ? null : book.getLastAimedAt();
    }

    /**
     * How close he walks before he stops and lets his weapon work.
     *
     * <p><b>His, not the game's.</b> It was one figure for every hero, and it was
     * the archer's: 48, comfortably inside the 60 his bow reaches. A swordsman
     * reaches 11, so the same number stopped him four body-lengths short of a
     * skeleton and left him standing there swinging at nothing — until it walked
     * the rest of the way and hit him, which looked like a hero who would not
     * fight until he was provoked.
     *
     * <p>Clamped to his own reach as well as read from his block, and the clamp is
     * not belt-and-braces: this is the number a new hero is most likely to be
     * given carelessly, and being wrong about it is invisible — he walks, he
     * stops, and nothing happens. Stopping a little inside the reach rather than
     * on it is the older lesson, written down where the figure lives: park on the
     * edge and one step by either of them puts the target outside again.
     */
    private float howCloseHeGets() {
        var his = settings.heroNamed(unit().getTemplate().getName()).closeDistance();
        float wanted = his > 0f ? his : settings.closeDistance();
        return Math.min(wanted, reachOfHisWeapon() * INSIDE_HIS_REACH);
    }

    /**
     * How much of his reach he closes to, when his own figure is too generous.
     *
     * <p>The archer's own numbers, as a ratio: he stops at 48 of a 60 reach, and
     * what that buys is a margin nothing can step out of by accident.
     */
    private static final float INSIDE_HIS_REACH = 0.8f;

    /**
     * How far his weapon reaches, taken from his own template rather than named
     * here — the two numbers have to agree, and a copy in the game would let them
     * drift apart the moment someone re-tuned the hero.
     */
    private float reachOfHisWeapon() {
        if (weaponRange < 0f) {
            weaponRange = 0f;
            for (var module : unit().getTemplate().getModules()) {
                if (module.data() instanceof WeaponUpdate.Data weapon) {
                    weaponRange = Math.max(weaponRange, weapon.attackRange());
                }
            }
        }
        return weaponRange;
    }
}
