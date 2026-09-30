package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.view.UnitView;

/**
 * On a map whose cells wear looks of their own, a still thing is drawn as the look of the cell it stands on draws it —
 * a wood's pillar a bare tree, a cave's a stalagmite, whatever the floor's look is — and a creature, which walks from
 * one into the other, as the floor's look draws it.
 */
class StillThingLookTest {

    private final Visuals visuals = Visuals.create()
            .unit("Pillar", pillar -> pillar.model("models/props/pillar.glb"))
            .theme("wood", wood -> wood.unit("Pillar", tree -> tree.model("models/props/bare_tree.glb")))
            .theme("cave", cave -> cave.unit("Pillar", column -> column.model("models/props/stalagmite.glb")))
            .theme("crypt", crypt -> { });
    private final Visuals.Theme wood = visuals.getTheme("wood");
    private final Visuals.Theme cave = visuals.getTheme("cave");

    /** A view from before one said whether it moves takes a structure for a still thing. */
    private static UnitView pillar(boolean still) {
        return new UnitView(7, "Pillar", 0, 55f, 55f, 0f, 100f, 100f, still, true, false, false, -1);
    }

    @Test
    void aStillThingIsDrawnAsTheLookOfItsCellDrawsIt() {
        assertEquals("models/props/stalagmite.glb", DukeRtsApp.lookOf(visuals, wood, cave, pillar(true)).modelPath);
    }

    @Test
    void aLookThatDoesNotDrawItLeavesItAsTheGameDrewItAndNotAsTheFloorsLook() {
        var crypt = visuals.getTheme("crypt");

        assertEquals("models/props/pillar.glb", DukeRtsApp.lookOf(visuals, wood, crypt, pillar(true)).modelPath);
    }

    @Test
    void onACellOfTheMapsOwnLookAndForAnythingThatMovesTheFloorsLookDrawsIt() {
        assertEquals("models/props/bare_tree.glb", DukeRtsApp.lookOf(visuals, wood, null, pillar(true)).modelPath);
        assertEquals("models/props/bare_tree.glb", DukeRtsApp.lookOf(visuals, wood, cave, pillar(false)).modelPath);
        assertEquals("models/props/pillar.glb", DukeRtsApp.lookOf(visuals, null, null, pillar(true)).modelPath);
    }
}
