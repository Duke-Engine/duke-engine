package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import org.junit.jupiter.api.Test;

/** A building drawn rising out of the ground as it is built: sunk by what is left times its model's height. */
class RisingTest {

    /** A barracks 12 tall, its base on the ground it stands on. */
    private static Node barracks() {
        var body = new Node("barracks");
        var walls = new Geometry("walls", new Box(9f, 6f, 9f));
        walls.setLocalTranslation(0f, 6f, 0f);
        body.attachChild(walls);
        return body;
    }

    @Test
    void atNothingBuiltItsTopIsAtTheGroundAtHalfItIsHalfOutAndWholeItStandsWhereItIs() {
        var body = barracks();
        float top = UnitPlacement.topOf(body);
        assertEquals(12f, top, 1e-4f, "measured off the model");

        assertEquals(12f, UnitPlacement.sunk(0f, top), 1e-4f, "sunk its whole height: its top at the ground");
        assertEquals(6f, UnitPlacement.sunk(0.5f, top), 1e-4f, "half out");
        assertEquals(0f, UnitPlacement.sunk(1f, top), 1e-4f, "whole, where it stands");
        assertEquals(18f, UnitPlacement.sunk(-0.5f, top), 1e-4f, "sold: below the ground altogether");

        var placed = new Node("unit");
        placed.setLocalTranslation(0f, 30f, 0f); // on a hill
        placed.attachChild(body);
        placed.updateGeometricState();
        assertEquals(12f, UnitPlacement.topOf(body), 1e-4f, "the same measured hung from its node on a hill");
    }

    @Test
    void aLookThatSaysNothingIsDrawnWholeThroughout() {
        var plain = Visuals.create().unit("Barracks", l -> l.model("models/barracks.glb")).of("Barracks");
        assertFalse(plain.risesAsBuilt, "no switch, no rising");
        var rising = Visuals.create().unit("Barracks", l -> l.model("models/barracks.glb").risesAsBuilt())
                .of("Barracks");
        assertTrue(rising.risesAsBuilt);
    }
}
