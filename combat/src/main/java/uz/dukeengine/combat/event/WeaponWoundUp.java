package uz.dukeengine.combat.event;

import uz.dukeengine.core.event.WorldEvent;
import uz.dukeengine.core.thing.ObjectId;

/**
 * A weapon has begun its wind-up at a target — the reference's {@code PREATTACK} — and its shot comes {@code frames}
 * frames on: for the picture to start the swing now, so the blow meets the shot. A weapon that winds up is told here
 * and not again on its {@link WeaponFired}, which still says where the shot went.
 */
public record WeaponWoundUp(int frame, ObjectId shooter, ObjectId target, String weapon, int slot, int frames)
        implements WorldEvent {
}
