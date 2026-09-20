package uz.dukeengine.core.thing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import uz.dukeengine.core.module.ModuleData;

/**
 * The engine's own template, an {@code Object} block: every field the engine reads, and none
 * of a game's. A game whose things have more to say reads blocks of its own records — see
 * {@link ThingTemplateLoader#type}.
 *
 * <p>A block leaves out what a thing does not have: no {@code DisplayName} is its name, no
 * shape is {@link Geometry#POINT}, which collides with nothing.
 */
public record ObjectTemplate(String name, String displayName, Set<Kind> kindOf, float visionRange,
        Geometry geometry, List<ModuleData> modules) implements Solid, Sighted, Classified, Titled {

    public ObjectTemplate {
        displayName = displayName == null ? "" : displayName;
        kindOf = kindOf == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(kindOf));
        geometry = geometry == null ? Geometry.POINT : geometry;
        modules = modules == null ? List.of() : List.copyOf(modules);
    }

    public static Builder named(String name) {
        return new Builder(name);
    }

    /** For a template built in code; a file goes through {@link ThingTemplateLoader}. */
    public static final class Builder {
        private final String name;
        private String displayName = "";
        private final Set<Kind> kinds = new LinkedHashSet<>();
        private final List<ModuleData> modules = new ArrayList<>();
        private float visionRange;
        private Geometry geometry = Geometry.POINT;

        private Builder(String name) {
            this.name = name;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder visionRange(float visionRange) {
            this.visionRange = visionRange;
            return this;
        }

        public Builder geometry(Geometry geometry) {
            this.geometry = geometry;
            return this;
        }

        public Builder kindOf(Kind... kinds) {
            Collections.addAll(this.kinds, kinds);
            return this;
        }

        public Builder addKindOf(Kind kind) {
            kinds.add(kind);
            return this;
        }

        public Builder module(ModuleData data) {
            modules.add(data);
            return this;
        }

        public ObjectTemplate build() {
            return new ObjectTemplate(name, displayName, kinds, visionRange, geometry, modules);
        }
    }
}
