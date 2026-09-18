package uz.duke.dungeon.content;

import java.util.List;
import uz.duke.core.thing.Sighted;
import uz.duke.core.thing.Titled;

/**
 * A thing in flight, as one {@code Projectile} block writes it. Not solid — nothing can
 * shoot it and it passes through the world — so its block takes no shape. It may see: a
 * flare shot into the dark lights what it flies over, which is its vision range.
 */
public record Projectile(String name, String displayName, float visionRange, List<ModuleEntry> modules,
        DungeonSettings.ArrowLook look) implements Sighted, Titled {
}
