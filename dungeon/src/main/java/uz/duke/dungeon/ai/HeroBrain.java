package uz.duke.dungeon.ai;

import uz.duke.core.module.MoveUpdate;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ObjectId;
import uz.duke.core.thing.World;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.game.script.UnitScript;
import uz.duke.rts.module.WeaponUpdate;

/**
 * Makes "click a skeleton" mean what a dungeon player expects: walk over there
 * and kill it.
 *
 * <p>The engine deliberately splits the two halves of that. A weapon fires when
 * its target is in range and never moves the owner — closing the distance is the
 * locomotor's job. In an RTS that is right: you order a move and an attack, and
 * an attack order alone means "shoot it from where you stand". The 3D client
 * sends only {@code AttackObject} on a right-click, so in a dungeon the hero
 * would simply stand still and stare at a skeleton across the room.
 *
 * <p>Rather than change the engine's rule (or the client's input), the game
 * supplies the missing half itself: while the hero has a target he cannot reach,
 * walk to it. That is a gameplay decision — melee heroes close, archers would not
 * — so it belongs here.
 *
 * <p>Auto-attack is untouched. {@code WeaponUpdate} still acquires whatever comes
 * within reach on its own; this only ever adds movement toward a target that
 * already exists, so the two behaviours compose: the hero swings at what is next
 * to him, and goes to what he is pointed at.
 *
 * <p>Not shooting on the move is <em>not</em> here, though it reads like the same
 * kind of decision. It is {@code AttackOnTheMove = No} on his bow, because a
 * weapon acquires its own target and fires inside one call: a script that
 * disarmed it every frame would be undone before the script ran again. That the
 * engine holds the shot is also what keeps this class simple — the weapon keeps
 * its target the whole way across the room, so there is nothing here to remember
 * on its behalf.
 *
 * <p>Movement is issued through {@link UnitScript#moveTo} which drives the
 * locomotor directly, not through the command queue — a {@code MoveTo} command
 * clears the weapon's target, so ordering the hero to walk to his target would
 * cancel the very attack that sent him.
 */
public final class HeroBrain extends UnitScript {

    private final DungeonSettings settings;

    /**
     * What the player pointed him at, as opposed to what he noticed in passing.
     *
     * <p>The engine cannot say which it is: a target is a target. But it does not
     * have to, because auto-acquire only ever picks something already within his
     * weapon's range. Anything further away can only have been ordered, and that is
     * enough to tell the two apart — once, when it appears.
     *
     * <p>This is only a note about the weapon's target, never a second copy of it.
     * The order itself lives on the weapon, which is what makes cancelling one
     * free: a {@code MoveTo} takes the weapon's target away, and an order that is
     * no longer the weapon's is no longer an order.
     */
    private ObjectId sentAt;

    /** The target {@link #sentAt} was decided for, so it is decided only once. */
    private ObjectId decidedFor;

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
        noticeWhatHeHasPickedUp(weapon);

        var ordered = livingOrderedTarget(weapon);
        if (ordered == null) {
            // Nothing he was sent at, but his weapon may have found something of
            // its own. The locomotor only sets a heading while walking, so standing
            // still he would otherwise shoot over his shoulder.
            var acquired = weapon.isAttacking() ? world().findObject(weapon.getTarget()) : null;
            if (acquired != null && !acquired.isEffectivelyDead() && !move.isMoving()) {
                Facing.turnToward(unit(), acquired);
            }
            return;
        }

        if (World.reachBetween(unit(), ordered) <= settings.closeDistance()) {
            move.stop(); // close enough; standing still is also how he gets to fire
            Facing.turnToward(unit(), ordered);
        } else if (!move.isMoving() || frame() % settings.heroRepathFrames() == 0) {
            // Set off at once when he is standing, and correct the aim on the way
            // at intervals — a target that walks is the usual case.
            moveTo(ordered.getPosition().x(), ordered.getPosition().y());
        }
    }

    /**
     * Judge a newly picked-up target once: ordered, or noticed in passing.
     *
     * <p>Auto-acquire can only ever choose something already inside his reach, so
     * anything further away was pointed at. Judged when it appears and never
     * revisited — asking every frame reads the same target differently as the
     * distance closes, and something he walked past turns into something he was
     * sent at, and he goes back for it.
     */
    private void noticeWhatHeHasPickedUp(WeaponUpdate weapon) {
        if (!weapon.isAttacking()) {
            return;
        }
        var target = world().findObject(weapon.getTarget());
        if (target == null || target.isEffectivelyDead()
                || target.getId().equals(decidedFor)) {
            return;
        }
        decidedFor = target.getId();
        if (World.reachBetween(unit(), target) > reachOfHisWeapon()) {
            sentAt = target.getId();
        }
    }

    /**
     * What he was sent at, while it is still his weapon's target and still alive.
     *
     * <p>An attack order and a move order are both "go there", and the second has
     * to win. It does, without anything here noticing: a {@code MoveTo} clears the
     * weapon, and a target the weapon has let go of is not an order any more.
     */
    private GameObject livingOrderedTarget(WeaponUpdate weapon) {
        if (sentAt == null) {
            return null;
        }
        if (!sentAt.equals(weapon.getTarget())) {
            sentAt = null; // sent somewhere else, or it is dead and the weapon dropped it
            return null;
        }
        var ordered = world().findObject(sentAt);
        if (ordered == null || ordered.isEffectivelyDead()) {
            sentAt = null;
            return null;
        }
        return ordered;
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
