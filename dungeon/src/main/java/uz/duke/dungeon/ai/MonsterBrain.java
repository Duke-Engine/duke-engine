package uz.duke.dungeon.ai;

import uz.duke.core.module.MoveUpdate;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.World;
import uz.duke.dungeon.content.MonsterKind;
import uz.duke.game.script.UnitScript;
import uz.duke.rts.module.WeaponUpdate;

/**
 * The mind every monster in the dungeon shares: notice the hero, close to your
 * fighting distance, hit him, and give up if he gets far enough away.
 *
 * <p>One brain rather than one per kind, because a runner and an archer are not
 * different minds — they are the same mind with different numbers. An archer is
 * the one whose fighting distance is long enough to shoot from; a brute is the
 * one that walks all the way in. Writing a class per monster would make the fifth
 * kind cost a class and the twentieth cost twenty, and none of them would say
 * anything the parameters do not.
 *
 * <p>Two radii keep the dungeon from arriving all at once: the sense radius is
 * about a room, so aggro spreads room by room, and the wider chase radius means a
 * fight does not break off the moment the hero steps back — but is finite, so
 * outrunning something slower than you is a real move.
 *
 * <p>Deterministic: no randomness at all, and the frame a monster re-plans on is
 * staggered by its own object id, so a roomful does not all path on the same
 * frame while still doing the same thing on every machine.
 */
public final class MonsterBrain extends UnitScript {

    private final MonsterKind kind;
    private boolean chasing;

    public MonsterBrain(MonsterKind kind) {
        this.kind = kind;
    }

    @Override
    public void onUpdate() {
        // Once roused, it keeps looking further than it first noticed.
        float reachOut = chasing ? kind.chaseRadius() : kind.senseRadius();
        var hero = findNearestEnemy(reachOut);
        if (hero == null) {
            if (chasing) {
                giveUp();
            }
            return;
        }

        chasing = true;
        attack(hero); // the weapon fires on its own once the hero is in reach

        var move = unit().findModule(MoveUpdate.class);
        if (move == null) {
            return;
        }
        // Measured surface to surface, the way the weapon measures range. This
        // one distance is what separates a thing that closes from a thing that
        // shoots: an archer's is long, a brute's is nearly nothing.
        if (World.reachBetween(unit(), hero) <= kind.closeDistance()) {
            if (move.isMoving()) {
                move.stop(); // close enough to fight from here
            }
            Facing.turnToward(unit(), hero); // look at what it is hitting
            return;
        }
        advanceOn(move, hero);
    }

    private void advanceOn(MoveUpdate move, GameObject hero) {
        int repath = kind.repathFrames();
        int stagger = Math.floorMod(unit().getId().value(), repath);
        if (!move.isMoving() || frame() % repath == stagger) {
            moveTo(hero.getPosition().x(), hero.getPosition().y());
        }
    }

    private void giveUp() {
        chasing = false;
        var move = unit().findModule(MoveUpdate.class);
        if (move != null) {
            move.stop();
        }
        var weapon = unit().findModule(WeaponUpdate.class);
        if (weapon != null) {
            weapon.holdFire(); // drop a target that has walked out of the fight
        }
    }
}
