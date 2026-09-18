package uz.duke.dungeon.content;

import java.util.List;
import java.util.Set;
import uz.duke.core.thing.Classified;
import uz.duke.core.thing.Geometry;
import uz.duke.core.thing.Kind;
import uz.duke.core.thing.Sighted;
import uz.duke.core.thing.Solid;
import uz.duke.core.thing.Titled;

/**
 * A monster, as one {@code Monster} block writes it: what the engine builds it from, and
 * what the dungeon makes of it.
 *
 * <p>The dungeon's part is {@link #kind}, whole, rather than spread over this record:
 * the generator draws kinds before any world exists, and only a world can read a
 * monster's modules, so the kind is read with the settings and joined to the rest here.
 */
public record Monster(String name, String displayName, Set<Kind> kinds, float visionRange, Geometry geometry,
        List<ModuleEntry> modules, MonsterKind kind) implements Solid, Sighted, Classified, Titled {
}
