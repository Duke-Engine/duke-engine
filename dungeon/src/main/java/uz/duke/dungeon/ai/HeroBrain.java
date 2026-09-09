package uz.duke.dungeon.ai;

import uz.duke.core.module.MoveUpdate;
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

    public HeroBrain(DungeonSettings settings) {
        this.settings = settings;
    }

    @Override
    public void onUpdate() {
        var weapon = unit().findModule(WeaponUpdate.class);
        if (weapon == null || !weapon.isAttacking()) {
            return; // nothing to chase; auto-acquire handles anything in reach
        }
        var target = world().findObject(weapon.getTarget());
        if (target == null || target.isEffectivelyDead()) {
            return; // the weapon drops dead targets by itself
        }

        var move = unit().findModule(MoveUpdate.class);
        if (move == null) {
            return;
        }
        // Measured the same way the weapon measures it: surface to surface.
        if (World.reachBetween(unit(), target) <= settings.closeDistance()) {
            if (move.isMoving()) {
                move.stop(); // arrived — hold position and let the weapon work
            }
            // Standing still, nothing else would turn him: the locomotor only sets
            // a heading while walking, so he would strike over his shoulder.
            Facing.turnToward(unit(), target);
            return;
        }
        if (!move.isMoving() || frame() % settings.heroRepathFrames() == 0) {
            moveTo(target.getPosition().x(), target.getPosition().y());
        }
    }
}
