package uz.duke.core.thing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import uz.duke.core.module.ModuleData;

/**
 * The engine's own template, an INI {@code Object} block: every field the engine reads,
 * and none of a game's. A game whose things have more to say registers a block type of
 * its own with {@link ThingTemplateLoader#type}.
 */
public record ObjectTemplate(String name, String displayName, Set<Kind> kinds, float visionRange,
        Geometry geometry, List<ModuleEntry> modules) implements Solid, Sighted, Classified, Titled {

    public ObjectTemplate {
        kinds = Set.copyOf(kinds);
        modules = List.copyOf(modules);
    }

    public static Builder named(String name) {
        return new Builder(name);
    }

    /** For a template built in code; INI goes through {@link ThingTemplateLoader}. */
    public static final class Builder {
        private final String name;
        private String displayName = "";
        private final Set<Kind> kinds = new LinkedHashSet<>();
        private final List<ModuleEntry> modules = new ArrayList<>();
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

        public Builder module(String tag, ModuleData data) {
            modules.add(new ModuleEntry(tag, data));
            return this;
        }

        public ObjectTemplate build() {
            return new ObjectTemplate(name, displayName, kinds, visionRange, geometry, modules);
        }
    }
}
