package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import org.junit.jupiter.api.Test;

/** A thing drawn as nothing: an ambient sound's, a shroud clearer's — no box standing on the ground for it. */
class NoShapeTest {

    @Test
    void aVisualThatSaysItHasNoShapeIsDrawnAsNothingAndNothingPicksIt() {
        var birds = Visuals.create().unit("AmbientBirds", Visuals.UnitVisual::noShape).of("AmbientBirds");
        var tank = Visuals.create().unit("Tank", look -> look.model("models/tank.glb")).of("Tank");

        assertTrue(birds.shapeless, "said so");
        assertFalse(tank.shapeless, "a thing with a model has its shape");
        var body = new Node("no-shape"); // what a thing with no shape is given in the scene
        body.updateGeometricState();
        assertEquals(0, body.getTriangleCount(), "no geometry");
        assertEquals(Float.POSITIVE_INFINITY, Picking.drawnHit(body, new Ray(new Vector3f(0f, 50f, 0f),
                new Vector3f(0f, -1f, 0f))), "and a click on its place meets nothing of it");
    }
}
