package uz.dukeengine.skirmish.content;

import java.util.List;
import java.util.Set;
import uz.dukeengine.core.content.AnimationSet;
import uz.dukeengine.core.data.Clip;
import uz.dukeengine.core.data.Group;
import uz.dukeengine.core.data.Link;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.thing.Classified;
import uz.dukeengine.core.thing.Drawn;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.Kind;
import uz.dukeengine.core.thing.Sighted;
import uz.dukeengine.core.thing.Solid;
import uz.dukeengine.core.thing.Titled;
import uz.dukeengine.rts.Buildable;

/**
 * Anything a side owns and can lose: a worker, a soldier, a barracks.
 *
 * <p>Written from nothing rather than copied from the dungeon's {@code Monster}, on purpose. Two games that were
 * written independently and turn out to need the same fields have shown what a unit template <em>is</em>; two games
 * where one was copied from the other have shown nothing. What this record ended up needing, and what it did not,
 * is the evidence for the {@code Unit} the engine should offer — see
 * {@code docs/plan/2026-09-20-vocabulary-and-scripts.md}.
 *
 * <p>What it deliberately has <b>not</b> got, though the dungeon's creature record has: senses and chase bands (a
 * unit here is ordered, not roused), a portrait, a held weapon, skills, a spawn depth, a per-room count. What it has
 * that the dungeon's has not: a price and a build time, because here a unit is <em>bought</em>.
 *
 * @param buildCost what it costs its side; 0 for something that cannot be bought
 * @param buildTime seconds on the production line
 * @param model     its look, as the client loads it — a whole path from the resource root
 */
public record Unit(@Group("Identity") String name, String displayName, Set<Kind> kindOf,
        @Group("Body") float visionRange, Geometry geometry,
        @Group("Modules") List<ModuleData> modules,
        @Group("Cost") int buildCost, float buildTime,
        @Group("Look") String model, float modelScale, int tint, float facing,
        @Group("Animation") @Link(AnimationSet.class) String animations,
        @Clip String idle, @Clip String walk, @Clip String attack, @Clip String death)
        implements Solid, Sighted, Classified, Titled, Buildable, Drawn {

    /** What a block leaves out. */
    static final Unit DEFAULTS = new Unit("", "", Set.of(), 0f, Geometry.POINT, List.of(),
            0, 0f, null, 1f, 0xFFFFFF, 0f, null, null, null, null, null);

    public Unit {
        displayName = displayName == null ? "" : displayName;
        kindOf = kindOf == null ? Set.of() : Set.copyOf(kindOf);
        geometry = geometry == null ? Geometry.POINT : geometry;
        modules = modules == null ? List.of() : List.copyOf(modules);
        modelScale = modelScale <= 0f ? 1f : modelScale;
    }
}
