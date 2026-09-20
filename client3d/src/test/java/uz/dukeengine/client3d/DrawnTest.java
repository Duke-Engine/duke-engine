package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.content.AnimationSet;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.thing.Drawn;

/**
 * The seam that both games were missing: a template says what it looks like, and the client reads it.
 *
 * <p>Before this, every game handed its own record's look fields to {@link Visuals} one at a time — the same
 * twenty lines in the dungeon and in the skirmish. What is checked here is that a template needs only the
 * components it has, and that a clip it leaves out falls back to the set it links.
 */
class DrawnTest {

    /** A game's own template, with only some of the look: no tint, no facing, no walk. */
    private record Sparse(String name, List<ModuleData> modules, String model, String animations, String idle)
            implements Drawn {
    }

    /** And one that says everything. */
    private record Full(String name, List<ModuleData> modules, String model, float modelScale, int tint,
            float facing, String animations, String idle, String walk, String attack, String death)
            implements Drawn {
    }

    @Test
    void aTemplateNeedsOnlyTheComponentsItHas() {
        Drawn sparse = new Sparse("Worker", List.of(), "models/units/worker.glb", "Humanoid", "Idle");

        assertEquals(1f, sparse.modelScale(), 0.001f, "no ModelScale means the file's own size");
        assertEquals(0xFFFFFF, sparse.tint(), "no Tint means untinted");
        assertEquals(0f, sparse.facing(), 0.001f);
        assertNull(sparse.walk(), "a clip it does not name is not one it has");
        assertEquals("Idle", sparse.idle());
    }

    @Test
    void aTemplateWithNoModelIsLeftToItsGeometry() {
        Drawn nothing = new Sparse("Pile", List.of(), null, null, null);
        assertEquals(false, nothing.hasModel());
        assertEquals(true, new Sparse("Ore", List.of(), "models/ground/ore.gltf", null, null).hasModel());
    }

    /** What the client ends up being told, out of the block and the set it links. */
    @Test
    void theClientIsToldWhatTheBlockSaysAndTheSetFillsTheRest() {
        var set = new AnimationSet("Humanoid", List.of("animations/humanoid.glb"),
                "Idle_Loop", "Walk_Loop", "Swing", "Flinch", "Death");
        Drawn full = new Full("Soldier", List.of(), "models/units/soldier.glb", 4f, 0x00FF00, 90f,
                "Humanoid", null, "March", null, null);

        var visuals = Visuals.create().draw(full, set);

        // A name nobody bound gets the shared defaults; a bound one gets its own.
        assertNotSame(visuals.of("Nobody"), visuals.of("Soldier"), "the client should have been told about it");

        var nothing = Visuals.create().draw(new Sparse("Pile", List.of(), null, null, null), null);
        assertSame(nothing.of("Nobody"), nothing.of("Pile"),
                "and told nothing about a template with no model: it is left to its Geometry");
    }
}
