package uz.dukeengine.render;

/**
 * Four steps of picture against cost, for a game's settings menu: each a {@link PictureSettings} a game may still change
 * one thing of. What saves time without changing the look (instancing, levels of detail, occlusion culling, clustered
 * lights) is on at every step. What each costs on the bench is in the render plan's notes (step 4.4).
 */
public enum Quality {

    /** For weak and old GPUs and phones: no screen-space effects, FXAA, one narrow filter for the sun's shadows. */
    LOW,
    /** Ambient occlusion and contact shadows, two local lights with shadows, still FXAA. */
    MEDIUM,
    /** Temporal anti-aliasing, soft sun shadows, reflections and the shafts, four local shadows. */
    HIGH,
    /** Everything: the bounce of light (screen-space GI) and eight local shadows. */
    EPIC;

    /** The settings of this step. */
    public PictureSettings settings() {
        var full = PictureSettings.FULL;
        return switch (this) {
            case LOW -> new PictureSettings(false, true, AntiAliasing.FXAA, full.exposure(), full.atmosphere(), false, false, 0,
                    full.occlusionRadius(), full.autoExposure(), full.fogDensity(), full.fogFalloff(), false, false, full.lens(), full.lut(),
                    full.vignette(), true, false, full.water(), true, true, true);
            case MEDIUM -> new PictureSettings(true, true, AntiAliasing.FXAA, full.exposure(), full.atmosphere(), false, true, 2,
                    full.occlusionRadius(), full.autoExposure(), full.fogDensity(), full.fogFalloff(), false, false, full.lens(), full.lut(),
                    full.vignette(), true, false, full.water(), true, true, true);
            case HIGH -> full.withScreenSpaceGi(false);
            case EPIC -> full.withLocalShadows(8);
        };
    }
}
