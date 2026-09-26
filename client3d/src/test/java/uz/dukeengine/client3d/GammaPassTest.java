package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The picture drawn through a gamma, as the reference's ramp draws it ({@code DX8Wrapper::Set_Gamma}). */
class GammaPassTest {

    private static float shown(byte[] ramp, float value) {
        return (ramp[Math.round(value * 255f)] & 0xFF) / 255f;
    }

    @Test
    void throughAGammaOfTwoEachChannelShowsAsItsSquareRoot() {
        var ramp = GammaPass.ramp(2f);
        assertEquals(0.5f, shown(ramp, 0.25f), 1f / 255f, "0.25 shows as 0.5");
        assertEquals(1f, shown(ramp, 1f), "1.0 stays 1.0");
        assertEquals(0f, shown(ramp, 0f), "and black black");
    }

    @Test
    void aGammaOfOneDrawsNothingOverThePicture() {
        var pass = new GammaPass(null);
        assertFalse(pass.drawsThrough());
        pass.postFrame(null); // nothing copied, nothing drawn: not even a renderer asked for
        pass.setGamma(2f);
        assertTrue(pass.drawsThrough());
        pass.setGamma(1f);
        assertFalse(pass.drawsThrough(), "kept until set again");
    }

    @Test
    void itsMaterialReadsTheFrameAndTheRamp() {
        var gamma = new com.jme3.material.Material(new com.jme3.asset.DesktopAssetManager(true),
                "MatDefs/duke/Gamma.j3md").getMaterialDef();
        assertTrue(gamma.getMaterialParam("Frame") != null && gamma.getMaterialParam("Ramp") != null);
    }
}
