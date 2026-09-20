package uz.duke.dungeon.content;

import uz.duke.core.effect.Effect;
import uz.duke.core.data.Clip;
import uz.duke.core.data.Group;
import uz.duke.core.data.Link;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import uz.duke.core.module.ModuleData;
import uz.duke.core.thing.Classified;
import uz.duke.core.thing.Geometry;
import uz.duke.core.thing.Kind;
import uz.duke.core.thing.Sighted;
import uz.duke.core.thing.Solid;
import uz.duke.core.thing.Titled;
import uz.duke.dungeon.skill.Skill;

/**
 * A monster, as one {@code Monster} block writes it — the whole of it, in the order a file
 * usually does: what the engine builds it from, how it behaves, what it looks like, its face
 * in the panel, and any skill of its own, written inside it.
 *
 * <p>The behaviour is the simulation's and the look is not: {@link #kind()} and {@link #look()}
 * hand each out apart, so nothing the game decides can come to depend on something drawn.
 *
 * @param skillDistance the nearest and the furthest it casts its skill from, surface to surface
 * @param keepDistance  the band it holds around him: nearer and it backs away, further and it
 *                      comes. None, and it closes to {@code closeDistance} like everything else
 * @param maxPerRoom    how many of it one room may hold, or zero for no limit
 * @param skills        what it casts, each a {@code Skill} block in its {@code Skills = [ … ]}
 */
public record Monster(@Group("Identity") String name, String displayName, Set<Kind> kindOf,
        @Group("Body") float visionRange, Geometry geometry,
        @Group("Modules") List<ModuleData> modules,
        @Group("Behaviour") float senseRadius, float chaseRadius, float closeDistance, float alertRadius, int repathFrames,
        int swingFrames, @Group("Spawning") int minDepth, int weight, @Group("Look") int colour, float scale,
        @Group("Behaviour") Band skillDistance, Band keepDistance, @Group("Spawning") int maxPerRoom,
        @Group("Look") String model, String texture, float modelScale, int tint, float facing,
        @Group("Animation") @Link(AnimationSet.class) String animations, @Clip String idle, @Clip String walk,
        @Clip String attack, @Clip String hurt, @Clip String death, @Group("Look") @Link(Effect.class) String effect,
        Held held, @Group("Skills") PortraitArt portrait, List<Skill> skills) implements Solid, Sighted, Classified, Titled {

    /** Two distances, the nearer first: {@code [20, 60]}. */
    public record Band(float nearest, float furthest) {
        public static final Band NONE = new Band(0f, 0f);
    }

    /** What a block leaves out. */
    static final Monster DEFAULTS = new Monster("", "", Set.of(), 0f, Geometry.POINT, List.of(),
            90f, 150f, 4f, 70f, 10, 12, 1, 0, 0xFFFFFF, 1f,
            Band.NONE, Band.NONE, 0,
            null, null, 1f, 0xFFFFFF, 90f, null,
            null, null, null, null, null, null, Held.NOTHING,
            null, List.of());

    public Monster {
        displayName = displayName == null ? "" : displayName;
        kindOf = kindOf == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(kindOf));
        geometry = geometry == null ? Geometry.POINT : geometry;
        modules = modules == null ? List.of() : List.copyOf(modules);
        skillDistance = skillDistance == null ? Band.NONE : skillDistance;
        keepDistance = keepDistance == null ? Band.NONE : keepDistance;
        held = held == null ? Held.NOTHING : held;
        skills = skills == null ? List.of() : List.copyOf(skills);
    }

    /** What it does, which the simulation reads. */
    public MonsterKind kind() {
        return new MonsterKind(name, senseRadius, chaseRadius, closeDistance, alertRadius, repathFrames,
                swingFrames, minDepth, weight, colour, scale, look(), skillKey(), skillDistance.nearest(),
                skillDistance.furthest(), keepDistance.nearest(), keepDistance.furthest(), maxPerRoom);
    }

    /** What it is drawn as, which nothing in the simulation may read. */
    public MonsterLook look() {
        return new MonsterLook(model, texture, modelScale, tint, facing, animations, List.of(), idle, walk, attack,
                hurt, death, held, effect);
    }

    /** The key of the skill it casts: its own, written inside it, or none. */
    public char skillKey() {
        return skills.isEmpty() ? 0 : Character.toUpperCase(skills.getFirst().key());
    }
}
