package uz.duke.core.thing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import uz.duke.core.module.ModuleData;

/**
 * The immutable definition of a kind of thing, ported from SAGE's
 * {@code ThingTemplate}.
 *
 * <p>This is the "blueprint" loaded from an INI {@code Object} block: a name,
 * classification flags, and the list of modules every instance should be built
 * with. {@link ThingFactory} stamps out {@link GameObject}s from it. Templates
 * are shared and never mutated after construction, so a single template safely
 * backs thousands of objects.
 */
public final class ThingTemplate {

    /** One module to attach to each instance: its factory tag plus its data. */
    public record ModuleEntry(String tag, ModuleData data) {
    }

    private final String name;
    private final String displayName;
    private final Set<Kind> kinds;
    private final List<ModuleEntry> modules;
    private final int buildCost;
    private final int buildTimeFrames;
    private final float visionRange;

    private ThingTemplate(Builder b) {
        this.name = b.name;
        this.displayName = b.displayName;
        this.kinds = Set.copyOf(b.kinds);
        this.modules = List.copyOf(b.modules);
        this.buildCost = b.buildCost;
        this.buildTimeFrames = b.buildTimeFrames;
        this.visionRange = b.visionRange;
    }

    public static Builder named(String name) {
        return new Builder(name);
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isKindOf(Kind kind) {
        return kinds.contains(kind);
    }

    /** This template's classification flags. Unmodifiable. */
    public Set<Kind> getKinds() {
        return kinds;
    }

    public List<ModuleEntry> getModules() {
        return modules;
    }

    /** Resource cost to produce one of these. */
    public int getBuildCost() {
        return buildCost;
    }

    /** Logic frames it takes to produce one of these. */
    public int getBuildTimeFrames() {
        return buildTimeFrames;
    }

    /** How far this object can see, in world units (0 = no vision). */
    public float getVisionRange() {
        return visionRange;
    }

    /** Mutable builder; the resulting {@link ThingTemplate} is immutable. */
    public static final class Builder {
        private final String name;
        private String displayName = "";
        private final Set<Kind> kinds = new LinkedHashSet<>();
        private final List<ModuleEntry> modules = new ArrayList<>();
        private int buildCost;
        private int buildTimeFrames;
        private float visionRange;

        private Builder(String name) {
            this.name = name;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
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

        public Builder visionRange(float visionRange) {
            this.visionRange = visionRange;
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

        public ThingTemplate build() {
            return new ThingTemplate(this);
        }
    }
}
