package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.game.view.CommandButton;

/** A power's reticle laid on the ground under an armed aim, in place of the ring, throbbing as the reference's. */
class GroundDecalTest {

    /** An A-10 strike's: white, half to fully opaque and back each 40 frames. */
    private static final AimDecal STRIKE = new AimDecal("Common/Textures/dot.png", 0.5f, 1f, 40, 0xFFFFFF);

    @Test
    void itThrobsBetweenItsTwoOpacitiesOverItsPeriod() {
        assertEquals(0.75f, STRIKE.opacity(0), 1e-6f, "half way, rising");
        assertEquals(1f, STRIKE.opacity(10), 1e-6f, "a quarter through: the most");
        assertEquals(0.5f, STRIKE.opacity(30), 1e-6f, "three quarters: the least");
        assertEquals(STRIKE.opacity(3), STRIKE.opacity(43), 1e-6f, "round again");
    }

    @Test
    void oneIsLaidAtThePointersGroundPointTwiceItsRadiusAcrossFollowingTheGround() {
        var world = new Node("markers");
        var decal = new GroundDecal(new DesktopAssetManager(true), world);
        decal.show(new Coord3D(100f, 50f, 0f), 100f, STRIKE, 10, (x, z) -> x * 0.1f); // a slope rising east

        assertTrue(decal.showing());
        assertEquals(1, world.getChildren().size(), "one picture");
        assertEquals(100f, decal.node().getLocalTranslation().x, 1e-4f);
        assertEquals(10f, decal.node().getLocalTranslation().y, 1e-4f, "on the ground under the pointer");
        assertEquals(50f, decal.node().getLocalTranslation().z, 1e-4f, "the simulation's y is the client's z");
        assertEquals(200f, decal.across(), 1e-4f, "2 × radius across");
        var bound = (BoundingBox) ((Geometry) decal.node().getChild(0)).getMesh().getBound();
        assertEquals(100f, bound.getXExtent(), 1e-3f);
        assertEquals(100f, bound.getZExtent(), 1e-3f);
        assertEquals(10f, bound.getYExtent(), 1e-3f, "up the slope on one side, down it on the other");
        assertEquals(1f, decal.colour().a, 1e-6f, "as opaque as its throb says at frame 10");

        decal.hide();
        assertFalse(decal.showing(), "disarmed, taken away");
    }

    @Test
    void anAimArmedWithADecalKeepsItUntilItEndsAndOneWithoutDrawsTheRing() {
        var strike = new CommandButton("power:a10", null, "A-10 Strike", "A", true, CommandButton.Aim.GROUND, null);
        var aiming = new Aiming();
        var heard = new ArrayList<AimOutcome>();
        aiming.arm(strike, false, 100f, "target", STRIKE, heard::add);
        assertSame(STRIKE, aiming.decal());

        aiming.giveUp();
        assertNull(aiming.decal(), "disarmed: no picture on the ground");

        aiming.arm(strike, false, 100f, "target", heard::add);
        assertNull(aiming.decal(), "no decal: the ring, as today");
        assertEquals(100f, aiming.radius());
    }
}
