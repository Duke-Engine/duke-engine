package uz.dukeengine.rts.module;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;

/**
 * Applies timed status effects to its owner, ported in spirit from the way SAGE
 * objects carry expiring {@code ObjectStatus} flags (e.g. from a stun weapon).
 *
 * <p>{@link #apply} sets a status on the owner for a number of frames; each frame
 * the timers count down and expired statuses are cleared. Other modules
 * ({@link MoveUpdate}, {@link WeaponUpdate}) read the owner's flags to change
 * behaviour. Timers live in an {@link EnumMap} so iteration order is the enum's
 * declaration order — deterministic.
 */
@ModuleGroup(ModuleGroups.EFFECT)
public final class StatusUpdate extends UpdateModule {

    /** No configuration. */
    public record Data() implements ModuleData {
    }

    private final Map<ObjectStatus, Integer> timers = new EnumMap<>(ObjectStatus.class);

    public StatusUpdate(GameObject owner, Data data) {
        super(owner);
    }

    /** Apply {@code status} to the owner for {@code frames} logic frames. */
    public void apply(ObjectStatus status, int frames) {
        if (frames <= 0) {
            return;
        }
        timers.put(status, frames);
        getOwner().setStatus(status);
    }

    @Override
    public void update() {
        if (timers.isEmpty()) {
            return;
        }
        var expired = new ArrayList<ObjectStatus>();
        for (var entry : timers.entrySet()) {
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                expired.add(entry.getKey());
            } else {
                entry.setValue(remaining);
            }
        }
        for (var status : expired) {
            timers.remove(status);
            getOwner().clearStatus(status);
        }
    }
}
