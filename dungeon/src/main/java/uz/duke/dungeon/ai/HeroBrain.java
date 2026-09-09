package uz.duke.dungeon.ai;

import uz.duke.core.module.MoveUpdate;
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
 * <p>Movement is issued through {@link UnitScript#moveTo} which drives the
 * locomotor directly, not through the command queue — a {@code MoveTo} command
 * clears the weapon's target, so ordering the hero to walk to his target would
 * cancel the very attack that sent him.
 */
public final class HeroBrain extends UnitScript {

    private final DungeonSettings settings;

    /**
     * Whether the hero was sent at his current target rather than having noticed it
     * in passing.
     *
     * <p>The engine cannot say which it is: a target is a target. But it does not
     * have to, because auto-acquire only ever picks something already within his
     * weapon's range. Anything further away can only have been ordered, and that is
     * enough to tell the two apart — once, when it appears.
     */
    private boolean sentAtIt;

    /** The target {@link #sentAtIt} was decided for, so it is decided only once. */
    private ObjectId decidedFor;

    /** Cached: his weapon's range, read from his own template. See {@link #reachOfHisWeapon}. */
    private float weaponRange = -1f;

    public HeroBrain(DungeonSettings settings) {
        this.settings = settings;
    }

    @Override
    public void onUpdate() {
        var weapon = unit().findModule(WeaponUpdate.class);
        var target = weapon == null || !weapon.isAttacking() ? null
                : world().findObject(weapon.getTarget());
        if (target == null || target.isEffectivelyDead()) {
            forget();
            return; // the weapon drops dead targets by itself
        }
        var move = unit().findModule(MoveUpdate.class);
        if (move == null) {
            return;
        }

        float gap = World.reachBetween(unit(), target);
        if (!target.getId().equals(decidedFor)) {
            // Judged once, when the target appears, and never revisited. Judging it
            // every frame reads the same target differently as the distance closes:
            // something he was walking past ends up beyond his weapon a moment later
            // and turns into something he was sent at, and he goes back for it.
            decidedFor = target.getId();
            sentAtIt = gap > reachOfHisWeapon();
        }

        if (!sentAtIt) {
            // Something that wandered into reach. Where the player sent him outranks
            // it, so he is never stopped or steered for it — the weapon fires in
            // passing regardless. Standing still, he at least turns to face it,
            // because the locomotor only sets a heading while walking.
            if (!move.isMoving()) {
                Facing.turnToward(unit(), target);
            }
            return;
        }

        if (gap <= settings.closeDistance()) {
            if (move.isMoving()) {
                move.stop(); // arrived at what he was sent at
            }
            sentAtIt = false;
            Facing.turnToward(unit(), target);
            return;
        }
        if (!move.isMoving() || frame() % settings.heroRepathFrames() == 0) {
            moveTo(target.getPosition().x(), target.getPosition().y());
        }
    }

    private void forget() {
        sentAtIt = false;
        decidedFor = null;
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
