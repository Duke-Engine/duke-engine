package uz.duke.dungeon.ai;

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

    public HeroBrain(DungeonSettings settings) {
        this.settings = settings;
    }

    @Override
    public void onUpdate() {
        var weapon = unit().findModule(WeaponUpdate.class);
        var move = unit().findModule(MoveUpdate.class);
        if (weapon == null || move == null) {
            return;
        }
        forgetOrdersOverriddenByASkill(weapon);
        var current = weapon.isAttacking() ? world().findObject(weapon.getTarget()) : null;
        forgetAPickThatIsNoLongerHis(weapon);
        current = noticeWhatHeHasBeenPointedAt(current, move);
        current = dropWhatHeCannotShoot(weapon, current);

        var ordered = current != null && current.getId().equals(sentAt) ? current : null;
        if (ordered == null) {
            standAndShoot(weapon, move, current);
            return;
        }

        if (World.reachBetween(unit(), ordered) <= settings.closeDistance()
                && canSee(ordered)) {
            move.stop(); // close enough and in sight; standing still is how he fires
            Facing.turnToward(unit(), ordered);
        } else if (!move.isMoving() || frame() % settings.heroRepathFrames() == 0) {
            // Set off at once when he is standing, and correct the aim on the way
            // at intervals — a target that walks is the usual case. A wall between
            // them puts him here too, which is what walking round one looks like.
            moveTo(ordered.getPosition().x(), ordered.getPosition().y());
        }
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
    }

    /** The target a skill pointed his weapon at, which is the skill's and not an order. */
    private ObjectId aimedAtBySkill() {
        var book = unit().findModule(SkillBook.class);
        return book == null ? null : book.getLastAimedAt();
    }

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
