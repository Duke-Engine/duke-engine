package uz.duke.dungeon.ai;

import uz.duke.core.math.Coord3D;
import uz.duke.core.module.MoveUpdate;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.World;
import uz.duke.dungeon.combat.Swing;
import uz.duke.dungeon.content.DungeonSettings;
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
    private final DungeonSettings settings;
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

    /**
     * Where the last order sent it, so the next one is only given if the hero has
     * actually gone somewhere. See {@link Chasing} for why re-ordering a mover to
     * the place it is already heading is not free.
     */
    private Coord3D sentAfter;

    /**
     * The spot in front of it that a body is standing in, while one is — so it
     * stands still instead of shoving. Null when it is free to walk.
     *
     * <p>The spot rather than "am I blocked", because it has to keep asking about
     * the <em>same</em> piece of floor: see {@link WayAhead}.
     */
    private Coord3D waitingOn;

    public MonsterBrain(MonsterKind kind, DungeonSettings settings) {
        this.kind = kind;
        this.settings = settings;
    }

    @Override
    public void onUpdate() {
        noticeAnyWound();
        // Once roused, it keeps looking further than it first noticed — and once
        // hurt, it stops looking and simply comes.
        float reachOut = wounded ? THE_WHOLE_FLOOR
                : chasing ? kind.chaseRadius() : kind.senseRadius();
        var hero = findNearestEnemy(reachOut);
        if (hero == null && !chasing && somethingNearbyHasStarted()) {
            // Roused by a neighbour rather than by its own eyes, and then it looks
            // as far as it would once already in a fight. This is what makes a
            // room a room: the one at the door engages, the ones at the back hear
            // it, and the fight is with all of them instead of with a queue.
            hero = findNearestEnemy(kind.chaseRadius());
        }
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
        if (waitingOn != null) {
            if (WayAhead.stillShut(unit(), waitingOn, hero.getPosition(),
                    settings.wayAheadProbe(), hero)) {
                return; // the way is shut. Stand, and look again next frame.
            }
            // It opened — or the wait stopped being about anything. Go, whether or
            // not he has moved since: what changed was the road, not the quarry,
            // and Chasing knows only about quarries.
            waitingOn = null;
            sendAfter(hero);
            return;
        }
        var ahead = WayAhead.noWayPast(unit(), settings.wayAheadProbe(), hero);
        if (ahead != null) {
            // A body in front of it and no way round: stand rather than shove, and
            // remember the spot rather than the heading. See WayAhead.
            waitingOn = ahead;
            if (move.isMoving()) {
                move.stop();
            }
            return;
        }
        if (!Chasing.worthReplanning(sentAfter, hero.getPosition())) {
            return; // already on its way to where he is; see Chasing
        }
        if (!move.isMoving()) {
            sendAfter(hero);
            return;
        }
        int repath = kind.repathFrames();
        int stagger = Math.floorMod(unit().getId().value(), repath);
        if (frame() % repath == stagger) {
            sendAfter(hero);
        }
    }

    private void sendAfter(GameObject hero) {
        sentAfter = hero.getPosition();
        moveTo(sentAfter.x(), sentAfter.y());
    }

    /**
     * Whether one of its own is already fighting, near enough and in plain sight.
     *
     * <p>"Fighting" is read off the weapon rather than out of another brain: a
     * creature that has named a target is a creature in a fight, and that is
     * public where a brain's own state is not. It also means the shout needs no
     * mechanism — no event, no flag passed around, nothing to keep in step. Every
     * monster simply looks.
     *
     * <p><b>In plain sight</b> is what makes it a room rather than a radius. Stone
     * between them and the shout does not carry, so a fight in one room does not
     * empty the next one through the wall — which is the same rule the archer's
     * eyes follow, for the same reason.
     */
    private boolean somethingNearbyHasStarted() {
        if (kind.alertRadius() <= 0f || world() == null) {
            return false;
        }
        return world().findClosest(unit().getPosition(), kind.alertRadius(), other ->
                other != unit()
                        && other.getPlayerIndex() == unit().getPlayerIndex()
                        && !other.isEffectivelyDead()
                        && isFighting(other)
                        && SightLine.clear(unit(), other)) != null;
    }

    private static boolean isFighting(GameObject creature) {
        var weapon = creature.findModule(WeaponUpdate.class);
        return weapon != null && weapon.isAttacking();
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
        sentAfter = null; // the next fight is a new chase, not the tail of this one
        waitingOn = null;
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
