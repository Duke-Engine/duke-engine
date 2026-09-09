package uz.duke.dungeon.ai;

import uz.duke.core.module.MoveUpdate;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.World;
import uz.duke.dungeon.combat.Swing;
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

    /**
     * Far enough to find anyone on the floor. Used by something that has been
     * hurt: whoever did it is coming to answer for it wherever they are standing.
     */
    private static final float THE_WHOLE_FLOOR = 100_000f;

    private final MonsterKind kind;
    private boolean chasing;

    /**
     * Whether it has been hit, and the health it had when last asked.
     *
     * <p>A monster that is being shot at from beyond its own hearing would
     * otherwise stand there being killed — which is not a monster, it is a
     * target. There is no need to be told who fired: the dungeon holds one hero,
     * so losing health means he did it, and he is what it goes after.
     *
     * <p>Health going <em>up</em> is not a hit, which matters because one of these
     * heals itself.
     */
    private boolean wounded;
    private float healthWhenLastLooked = -1f;

    public MonsterBrain(MonsterKind kind) {
        this.kind = kind;
    }

    @Override
    public void onUpdate() {
        noticeAnyWound();
        // Once roused, it keeps looking further than it first noticed — and once
        // hurt, it stops looking and simply comes.
        float reachOut = wounded ? THE_WHOLE_FLOOR
                : chasing ? kind.chaseRadius() : kind.senseRadius();
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
        if (World.reachBetween(unit(), hero) <= kind.closeDistance() || midBlow()) {
            if (move.isMoving()) {
                move.stop(); // close enough to fight from here, or still swinging
            }
            Facing.turnToward(unit(), hero); // look at what it is hitting
            return;
        }
        advanceOn(move, hero);
    }

    /**
     * Whether the blow it last landed is still in progress.
     *
     * <p>A blow is not free. Without this a monster that reached the hero and
     * raised its arm went straight back to walking the instant he stepped away --
     * so it followed him around the room with its arm up and never landed
     * anything, and stepping away cost him nothing either, because the step cost
     * the monster nothing. What it buys the player is the window: get out of reach
     * and the monster is still finishing the blow you left.
     *
     * <p>Tied to a blow it actually struck, not to having come close. Paying the
     * recovery for merely brushing past would mean anything faster than it could
     * walk away for free, losing it ground every time it caught up and never
     * hitting anything.
     *
     * <p>How long a swing is belongs to the kind -- a brute's is slow and a
     * runner's is not -- so it is in dungeon.ini. The animation follows without
     * being told: a monster standing still with a target is exactly the state the
     * client draws as attacking.
     */
    private boolean midBlow() {
        var swing = unit().findModule(Swing.class);
        return swing != null && swing.stillSwinging(frame(), kind.swingFrames());
    }

    private void advanceOn(MoveUpdate move, GameObject hero) {
        int repath = kind.repathFrames();
        int stagger = Math.floorMod(unit().getId().value(), repath);
        if (!move.isMoving() || frame() % repath == stagger) {
            moveTo(hero.getPosition().x(), hero.getPosition().y());
        }
    }

    /** Compare what health it has with what it had, and remember being hit. */
    private void noticeAnyWound() {
        var body = unit().getBody();
        if (body == null) {
            return;
        }
        float now = body.getHealth();
        if (healthWhenLastLooked >= 0f && now < healthWhenLastLooked) {
            wounded = true;
        }
        healthWhenLastLooked = now;
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
