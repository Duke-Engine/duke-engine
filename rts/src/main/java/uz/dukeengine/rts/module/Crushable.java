package uz.dukeengine.rts.module;

import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.thing.GameObject;

/**
 * Marks a thing as something a {@link CrushUpdate} can run over, and how hard it is to: SAGE's {@code
 * CrushableLevel}. A crusher flattens what its level is above — the reference's infantry are 0, run over by
 * anything that crushes at all, its cars 1, flattened by a tank (2) and not by a truck (1).
 *
 * <p>A thing without one is never run over, as a thing is in the reference unless its block says otherwise.
 */
@ModuleGroup(ModuleGroups.BODY)
public final class Crushable extends Module {

    public record Data(int crushableLevel) implements ModuleData {
    }

    private final int level;

    public Crushable(GameObject owner, Data data) {
        super(owner);
        this.level = data.crushableLevel();
    }

    public int getLevel() {
        return level;
    }
}
