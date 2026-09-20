package uz.dukeengine.rts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.thing.Classified;
import uz.dukeengine.core.content.AnimationSet;
import uz.dukeengine.core.content.Effect;
import uz.dukeengine.core.data.Clip;
import uz.dukeengine.core.data.Link;
import uz.dukeengine.core.thing.Drawn;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.Kind;
import uz.dukeengine.core.thing.Sighted;
import uz.dukeengine.core.thing.Solid;
import uz.dukeengine.core.thing.ThingTemplateLoader;
import uz.dukeengine.core.thing.Titled;

/**
 * An RTS's {@code Object}: the engine's fields, and what it costs to build. Every thing
 * in an RTS may come out of a factory, so the library gives the {@code Object} block
 * itself the two fields — {@code BuildCost} and {@code BuildTime} — see {@link #register}.
 */
public record RtsTemplate(String name, String displayName, Set<Kind> kindOf, float visionRange, Geometry geometry,
        List<ModuleData> modules, int buildCost, float buildTime,
        String model, float modelScale, int tint, float facing,
        @Link(AnimationSet.class) String animations,
        @Clip String idle, @Clip String walk, @Clip String attack, @Clip String death,
        @Link(Effect.class) String effect)
        implements Solid, Sighted, Classified, Titled, Buildable, Drawn {

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
            return new RtsTemplate(name, displayName, kinds, visionRange, geometry, modules, buildCost, buildTime,
                    null, 1f, 0xFFFFFF, 0f, null, null, null, null, null, null);
        }
    }
}
