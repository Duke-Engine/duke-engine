package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.game.view.UnitView;

/** A thing kept from some players: see-through and pulsing to its own side, a glow alone to the others. */
class StealthLookTest {

    private static final DesktopAssetManager ASSETS = new DesktopAssetManager(true);
    private static final Visuals VISUALS = Visuals.create().seeThrough("STEALTHED").glow("DETECTED");
    private static final Visuals.UnitVisual SNIPER = Visuals.create().unit("Sniper", look -> look.seeThrough(0.3f))
            .of("Sniper");

    private static UnitView view(boolean allied, String... words) {
        return new UnitView(5, "Sniper", allied ? 1 : 2, 0f, 0f, 0f, 100f, 100f, false, true, false, false, -1, 0f, 0f,
                0f, false, 0, List.of(), List.of(words), 1f, -1, allied);
    }

    private static Node body(Geometry piece) {
        var material = new Material(ASSETS, "Common/MatDefs/Light/Lighting.j3md");
        material.setBoolean("UseMaterialColors", true);
        material.setColor("Diffuse", ColorRGBA.White);
        piece.setMaterial(material);
        var body = new Node("sniper");
        body.attachChild(piece);
        return body;
    }

    @Test
    void thePulseRunsFromTheFaintestToWholeAndBack() {
        assertEquals(0.65f, StealthLook.opacityAt(0.3f, 0f), 1e-5f, "halfway at the start");
        assertEquals(1f, StealthLook.opacityAt(0.3f, FastMath.HALF_PI), 1e-5f, "whole at the top");
        assertEquals(0.3f, StealthLook.opacityAt(0.3f, 3f * FastMath.HALF_PI), 1e-5f, "the faintest at the bottom");
        assertEquals(31, Math.round(FastMath.TWO_PI / StealthLook.PULSE_STEP), "a pulse in about 31 frames");
    }

    @Test
    void aGlowIsWholeWhileTheWordIsHeldAndGoneSoonAfter() {
        float glow = StealthLook.glowAfter(0f, true);
        assertEquals(1f, glow);
        int frames = 0;
        while (glow > 0f) {
            glow = StealthLook.glowAfter(glow, false);
            frames++;
        }
        assertEquals(31, frames, "times 0.8 a drawn frame until under a thousandth");
    }

    @Test
    void itsBlipBlinksOnceASecond() {
        assertEquals(32f / 255f, StealthLook.radarAlpha(0), 1e-5f);
        assertEquals(1f, StealthLook.radarAlpha(29), 1e-5f);
        assertEquals(32f / 255f, StealthLook.radarAlpha(30), 1e-5f, "and down again");
    }

    @Test
    void toItsOwnSideItIsDrawnSeeThroughAndWholeAgainOnceTheWordGoes() {
        var piece = new Geometry("coat", new Box(1f, 2f, 1f));
        var body = body(piece);
        var look = new StealthLook(ASSETS);

        look.see(view(true, "STEALTHED"), body, VISUALS, SNIPER, 1f);
        var faded = (ColorRGBA) piece.getMaterial().getParamValue("Diffuse");
        assertEquals(StealthLook.opacityAt(0.3f, StealthLook.PULSE_STEP), faded.a, 1e-5f, "a frame into its pulse");
        assertEquals(RenderState.BlendMode.Alpha, piece.getMaterial().getAdditionalRenderState().getBlendMode());

        look.see(view(true), body, VISUALS, SNIPER, 1f);
        assertEquals(1f, ((ColorRGBA) piece.getMaterial().getParamValue("Diffuse")).a, "whole again");
        assertEquals(RenderState.BlendMode.Off, piece.getMaterial().getAdditionalRenderState().getBlendMode());
        assertFalse(look.looking(5));
    }

    @Test
    void toTheOthersADetectedThingIsAGlowAloneThatFadesAway() {
        var piece = new Geometry("coat", new Box(1f, 2f, 1f));
        var body = body(piece);
        var own = piece.getMaterial();
        var look = new StealthLook(ASSETS);

        look.see(view(false, "DETECTED"), body, VISUALS, SNIPER, 1f);
        assertNotSame(own, piece.getMaterial(), "its model drawn as the glow instead");
        assertEquals(RenderState.BlendMode.Additive, piece.getMaterial().getAdditionalRenderState().getBlendMode());

        for (int frame = 0; frame < 31; frame++) {
            look.see(view(false), body, VISUALS, SNIPER, 1f);
        }
        assertSame(own, piece.getMaterial(), "gone, and its own look given back");
    }

    @Test
    void toItsOwnSideTheGlowIsALightOverItsSeeThroughLook() {
        var piece = new Geometry("coat", new Box(1f, 2f, 1f));
        var body = body(piece);
        var own = piece.getMaterial();
        var look = new StealthLook(ASSETS);

        look.see(view(true, "STEALTHED", "DETECTED"), body, VISUALS, SNIPER, 1f);

        assertSame(own, piece.getMaterial(), "its own model, see-through");
        assertEquals(1, body.getLocalLightList().size(), "with the glow's light over it");
    }

    @Test
    void aThingThatNeverGlowsDoesNot() {
        var mine = Visuals.create().unit("Mine", look -> look.neverGlows()).of("Mine");
        var piece = new Geometry("mine", new Box(1f, 1f, 1f));
        var body = body(piece);
        var own = piece.getMaterial();

        new StealthLook(ASSETS).see(view(false, "DETECTED"), body, VISUALS, mine, 1f);

        assertSame(own, piece.getMaterial());
        assertTrue(body.getLocalLightList().size() == 0);
    }
}
