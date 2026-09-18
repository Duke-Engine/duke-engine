package uz.duke.rts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import uz.duke.core.GameConstants;
import uz.duke.core.ini.FieldParseTable;
import uz.duke.core.ini.Ini;
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
 * itself the two fields — see {@link #register}.
 */
public record RtsTemplate(String name, String displayName, Set<Kind> kinds, float visionRange, Geometry geometry,
        List<ModuleEntry> modules, int buildCost, int buildTimeFrames)
        implements Solid, Sighted, Classified, Titled, Buildable {

    public RtsTemplate {
        kinds = Set.copyOf(kinds);
        modules = List.copyOf(modules);
    }

    /** What an {@code Object} block adds in an RTS. */
    private static final class Cost {
        private int buildCost;
        private int buildTimeFrames;
    }

    private static final FieldParseTable<Cost> COST = new FieldParseTable<Cost>()
            .add("BuildCost", Ini.integer((c, v) -> c.buildCost = v))
            // Seconds in the file, frames in the template, as SAGE writes it.
            .add("BuildTime", (ini, c) -> c.buildTimeFrames =
                    Math.round(Ini.scanReal(ini.getNextToken()) * GameConstants.LOGICFRAMES_PER_SECOND));

    /** Makes {@code Object} blocks RTS templates, with a build cost and a build time. */
    public static ThingTemplateLoader register(ThingTemplateLoader loader) {
        return loader.type("Object", RtsTemplate.class, COST, Cost::new,
                (parts, cost) -> new RtsTemplate(parts.name(), parts.displayName(), parts.kinds(), parts.visionRange(),
                        parts.geometry(), parts.modules(), cost.buildCost, cost.buildTimeFrames));
    }

    public static Builder named(String name) {
        return new Builder(name);
    }

    /** For a template built in code; INI goes through {@link #register}. */
    public static final class Builder {
        private final String name;
        private String displayName = "";
        private final Set<Kind> kinds = new LinkedHashSet<>();
        private final List<ModuleEntry> modules = new ArrayList<>();
        private float visionRange;
        private Geometry geometry = Geometry.POINT;
        private int buildCost;
        private int buildTimeFrames;

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

        public Builder buildCost(int buildCost) {
            this.buildCost = buildCost;
            return this;
        }

        public Builder buildTimeFrames(int buildTimeFrames) {
            this.buildTimeFrames = buildTimeFrames;
            return this;
        }

        public RtsTemplate build() {
            return new RtsTemplate(name, displayName, kinds, visionRange, geometry, modules, buildCost, buildTimeFrames);
        }
    }
}
