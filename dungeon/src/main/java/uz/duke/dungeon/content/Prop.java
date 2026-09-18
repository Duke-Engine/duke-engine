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
 * Something standing about in a room, as one {@code Prop} block writes it: solid, so it is
 * walked round, with no body, so nothing targets it. It may see — a brazier that lights
 * the room it stands in — which is its vision range.
 */
public record Prop(String name, String displayName, Set<Kind> kinds, float visionRange, Geometry geometry,
        List<ModuleEntry> modules, DungeonSettings.PropKind kind) implements Solid, Sighted, Classified, Titled {
}
