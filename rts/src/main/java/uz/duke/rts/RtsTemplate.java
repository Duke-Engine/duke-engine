package uz.duke.rts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import uz.duke.core.GameConstants;
import uz.duke.core.module.ModuleData;
import uz.duke.core.thing.Classified;
import uz.duke.core.thing.Geometry;
import uz.duke.core.thing.Kind;
import uz.duke.core.thing.Sighted;
import uz.duke.core.thing.Solid;
import uz.duke.core.thing.ThingTemplateLoader;
import uz.duke.core.thing.Titled;

/**
 * An RTS's {@code Object}: the engine's fields, and what it costs to build. Every thing
 * in an RTS may come out of a factory, so the library gives the {@code Object} block
 * itself the two fields — {@code BuildCost} and {@code BuildTime} — see {@link #register}.
 */
public record RtsTemplate(String name, String displayName, Set<Kind> kindOf, float visionRange, Geometry geometry,
        List<ModuleData> modules, int buildCost, float buildTime)
        implements Solid, Sighted, Classified, Titled, Buildable {

    public RtsTemplate {
        displayName = displayName == null ? "" : displayName;
        kindOf = kindOf == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(kindOf));
        geometry = geometry == null ? Geometry.POINT : geometry;
        modules = modules == null ? List.of() : List.copyOf(modules);
    }

    /** Makes {@code Object} blocks RTS templates, with a build cost and a build time. */
    public static ThingTemplateLoader register(ThingTemplateLoader loader) {
        return loader.type("Object", RtsTemplate.class);
    }

    public static Builder named(String name) {
        return new Builder(name);
    }

    /** For a template built in code; a file goes through {@link #register}. */
    public static final class Builder {
        private final String name;
        private String displayName = "";
        private final Set<Kind> kinds = new LinkedHashSet<>();
        private final List<ModuleData> modules = new ArrayList<>();
        private float visionRange;
        private Geometry geometry = Geometry.POINT;
        private int buildCost;
        private float buildTime;

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

        public Builder buildCost(int buildCost) {
            this.buildCost = buildCost;
            return this;
        }

        /** In frames, as a test counts them; a file writes seconds. */
        public Builder buildTimeFrames(int buildTimeFrames) {
            this.buildTime = buildTimeFrames / (float) GameConstants.LOGICFRAMES_PER_SECOND;
            return this;
        }

        public RtsTemplate build() {
            return new RtsTemplate(name, displayName, kinds, visionRange, geometry, modules, buildCost, buildTime);
        }
    }
}
