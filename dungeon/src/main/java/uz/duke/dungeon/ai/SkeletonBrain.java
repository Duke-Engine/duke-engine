package uz.duke.dungeon.ai;

import uz.duke.core.module.MoveUpdate;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.World;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.game.script.UnitScript;
import uz.duke.rts.module.WeaponUpdate;

/**
 * Skeletons that come for the hero instead of waiting to be walked into.
 *
 * <p>Standing still, they were furniture with a weapon: the hero could clear the
 * whole dungeon at his own pace, one skeleton at a time, and never be in danger
 * from more than the one he chose. Making them advance changes the shape of the
 * game rather than just its numbers — a room is now a fight that arrives all at
 * once, and walking into the next one carelessly is how a run ends.
 *
 * <p>Two radii keep that from turning into the whole dungeon chasing the hero at
 * once, which would be neither fair nor interesting:
 *
 * <ul>
 *   <li>the sense radius — about a room. Aggro spreads room by room, so the
 *       dungeon is fought in pieces.</li>
 *   <li>the chase radius — how far they follow once roused. Wider, so a fight does
 *       not break off the instant the hero steps back, but finite: skeletons are
 *       slower than the hero, so breaking away and regrouping is a real move he
 *       can make.</li>
 * </ul>
 *
 * <p>Both come from {@link DungeonSettings}, so how alert the dungeon is can be
 * re-tuned without a rebuild.
 *
 * <p>Deterministic like everything on the simulation thread: no randomness at all,
 * and the re-planning frame is staggered by the skeleton's own object id, so a
 * roomful does not all path on the same frame while still doing the same thing on
 * every machine and in every replay.
 */
public final class SkeletonBrain extends UnitScript {

    private final DungeonSettings settings;
    private boolean chasing;

    public SkeletonBrain(DungeonSettings settings) {
        this.settings = settings;
    }

    @Override
    public void onUpdate() {
        // Once roused, a skeleton keeps looking further than it first noticed.
        float reachOut = chasing ? settings.skeletonChaseRadius() : settings.skeletonSenseRadius();
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
        if (World.reachBetween(unit(), hero) <= settings.skeletonAttackRange()) {
            if (move.isMoving()) {
                move.stop(); // close enough to swing
            }
            return;
        }
        advanceOn(move, hero);
    }

    private void advanceOn(MoveUpdate move, GameObject hero) {
        int repath = settings.skeletonRepathFrames();
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
