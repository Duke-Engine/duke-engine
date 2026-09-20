package uz.dukeengine.dungeon.content;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.thing.Classified;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.Kind;
import uz.dukeengine.core.thing.Sighted;
import uz.dukeengine.core.thing.Solid;
import uz.dukeengine.core.thing.Titled;

/**
 * A thing that stands about in a room, as one {@code Prop} block writes it: what the engine
 * builds it from, and how often a room draws it — {@code Weight}, against every other prop's.
 */
public record Prop(String name, String displayName, Set<Kind> kindOf, float visionRange, Geometry geometry,
        List<ModuleData> modules, int weight) implements Solid, Sighted, Classified, Titled {

    /** What a block leaves out: drawn as often as anything else that says nothing. */
    static final Prop DEFAULTS = new Prop("", "", Set.of(), 0f, Geometry.POINT, List.of(), 1);

    public Prop {
        displayName = displayName == null ? "" : displayName;
        kindOf = kindOf == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(kindOf));
        geometry = geometry == null ? Geometry.POINT : geometry;
        modules = modules == null ? List.of() : List.copyOf(modules);
    }

    /** How often a room draws it, which is all the generator asks. */
    public DungeonSettings.PropKind kind() {
        return new DungeonSettings.PropKind(name, weight);
    }
}
