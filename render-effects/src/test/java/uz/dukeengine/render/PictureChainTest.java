package uz.dukeengine.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.post.filters.BloomFilter;
import com.jme3.post.ssao.SSAOFilter;
import org.junit.jupiter.api.Test;

/** That the occlusion in the chain is the one that reads the scene buffers, and that its radius is a real one. */
class PictureChainTest {

    @Test
    void ambientOcclusionIsOneGtaoAndNoSsao() {
        var filters = PictureChain.filters(PictureSettings.FULL);
        assertEquals(1, filters.stream().filter(GtaoFilter.class::isInstance).count());
        assertTrue(filters.stream().noneMatch(SSAOFilter.class::isInstance));
    }

    @Test
    void ambientOcclusionOffLeavesNoOcclusionFilter() {
        var off = new PictureSettings(false, true, AntiAliasing.TAA, 0.7f, false, true, true, 4, 0.75f, false, 0f, 0.06f, true, true);
        assertTrue(PictureChain.filters(off).stream().noneMatch(GtaoFilter.class::isInstance));
    }

    @Test
    void bloomIsOneMipBloomAheadOfTheToneCurveAndNoForkBloom() {
        var filters = PictureChain.filters(PictureSettings.FULL);
        assertEquals(1, filters.stream().filter(MipBloomFilter.class::isInstance).count());
        assertTrue(filters.stream().noneMatch(BloomFilter.class::isInstance));
        assertTrue(filters.indexOf(filters.stream().filter(MipBloomFilter.class::isInstance).findFirst().get())
                < filters.indexOf(filters.stream().filter(AcesToneMap.class::isInstance).findFirst().get()));
    }

    @Test
    void theEyeIsOffInFullAndOnWhenAsked() {
        assertFalse(PictureSettings.FULL.autoExposure());
        assertTrue(PictureSettings.FULL.withAutoExposure(true).autoExposure());
        assertEquals(PictureSettings.FULL, PictureSettings.FULL.withAutoExposure(true).withAutoExposure(false));
    }

    @Test
    void fogIsOffInFullAndOneFilterBeforeTheBloomWhenAsked() {
        assertEquals(0f, PictureSettings.FULL.fogDensity());
        assertTrue(PictureChain.filters(PictureSettings.FULL).stream().noneMatch(HeightFogFilter.class::isInstance));
        var filters = PictureChain.filters(PictureSettings.FULL.withFog(0.01f, 0.05f));
        assertEquals(1, filters.stream().filter(HeightFogFilter.class::isInstance).count());
        assertTrue(filters.indexOf(filters.stream().filter(GtaoFilter.class::isInstance).findFirst().get())
                < filters.indexOf(filters.stream().filter(HeightFogFilter.class::isInstance).findFirst().get()));
        assertTrue(filters.indexOf(filters.stream().filter(HeightFogFilter.class::isInstance).findFirst().get())
                < filters.indexOf(filters.stream().filter(MipBloomFilter.class::isInstance).findFirst().get()));
        assertThrows(IllegalArgumentException.class, () -> PictureSettings.FULL.withFog(-1f, 0.05f));
        assertThrows(IllegalArgumentException.class, () -> PictureSettings.FULL.withFog(0.01f, -0.05f));
    }

    @Test
    void reflectionsAreOneFilterBetweenTheOcclusionAndTheFogAndGoWhenAskedTo() {
        var filters = PictureChain.filters(PictureSettings.FULL.withFog(0.01f, 0.05f).withReflections(true));
        assertEquals(1, filters.stream().filter(SsrFilter.class::isInstance).count());
        int ssr = filters.indexOf(filters.stream().filter(SsrFilter.class::isInstance).findFirst().get());
        assertTrue(filters.indexOf(filters.stream().filter(GtaoFilter.class::isInstance).findFirst().get()) < ssr);
        assertTrue(ssr < filters.indexOf(filters.stream().filter(HeightFogFilter.class::isInstance).findFirst().get()));
        assertTrue(PictureChain.filters(PictureSettings.FULL.withReflections(false)).stream().noneMatch(SsrFilter.class::isInstance));
    }

    @Test
    void edgesAreSmoothedByTheOneFilterTheModeNamesAndTaaSitsBetweenTheEffectsAndTheBloom() {
        var taa = PictureChain.filters(PictureSettings.FULL.withAntiAliasing(AntiAliasing.TAA).withFog(0.01f, 0.05f));
        assertEquals(1, taa.stream().filter(TaaFilter.class::isInstance).count());
        assertEquals(1, taa.stream().filter(SharpenFilter.class::isInstance).count());
        assertTrue(taa.stream().noneMatch(com.jme3.post.filters.FXAAFilter.class::isInstance));
        int at = taa.indexOf(taa.stream().filter(TaaFilter.class::isInstance).findFirst().get());
        assertTrue(taa.indexOf(taa.stream().filter(HeightFogFilter.class::isInstance).findFirst().get()) < at);
        assertTrue(at < taa.indexOf(taa.stream().filter(MipBloomFilter.class::isInstance).findFirst().get()));
        assertTrue(taa.indexOf(taa.stream().filter(AcesToneMap.class::isInstance).findFirst().get()) < taa.indexOf(taa.stream().filter(SharpenFilter.class::isInstance).findFirst().get()));
        var fxaa = PictureChain.filters(PictureSettings.FULL.withAntiAliasing(AntiAliasing.FXAA));
        assertEquals(1, fxaa.stream().filter(com.jme3.post.filters.FXAAFilter.class::isInstance).count());
        assertTrue(fxaa.stream().noneMatch(TaaFilter.class::isInstance) && fxaa.stream().noneMatch(SharpenFilter.class::isInstance));
        var none = PictureChain.filters(PictureSettings.FULL.withAntiAliasing(AntiAliasing.NONE));
        assertTrue(none.stream().noneMatch(f -> f instanceof TaaFilter || f instanceof SharpenFilter || f instanceof com.jme3.post.filters.FXAAFilter));
    }

    @Test
    void radiusMustBeAboveZero() {
        assertEquals(2.5f, PictureSettings.FULL.withOcclusionRadius(2.5f).occlusionRadius());
        assertThrows(IllegalArgumentException.class, () -> PictureSettings.FULL.withOcclusionRadius(0f));
        assertThrows(IllegalArgumentException.class, () -> PictureSettings.FULL.withOcclusionRadius(Float.NaN));
    }
}
