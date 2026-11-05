package uz.dukeengine.render;

import com.jme3.post.Filter;
import com.jme3.post.filters.FXAAFilter;
import java.util.ArrayList;
import java.util.List;

/** The filters over a finished HDR picture, in the order they must run. */
public final class PictureChain {

    private PictureChain() {
    }

    public static List<Filter> filters(PictureSettings settings) {
        var filters = new ArrayList<Filter>();
        // First, so that every effect after it sees a picture with the water in it.
        if (settings.water()) {
            filters.add(new WaterFilter());
        }
        if (settings.ambientOcclusion()) {
            filters.add(new GtaoFilter(settings.occlusionRadius()));
        }
        // After the occlusion, whose darkening the bounce is not to be darkened by; before the reflections, which then reflect it.
        if (settings.screenSpaceGi()) {
            filters.add(new SsgiFilter());
        }
        // Before the fog, which a reflection is seen through as the surface is.
        if (settings.reflections()) {
            filters.add(new SsrFilter());
        }
        if (settings.fogDensity() > 0f) {
            filters.add(new HeightFogFilter(settings));
        }
        // After every effect that draws its grain and before the bloom and the curve, which would hold a bright pixel's flicker.
        if (settings.antiAliasing() == AntiAliasing.TAA) {
            filters.add(new TaaFilter());
        }
        // On the settled picture, before the bloom, so that a blurred highlight spills as a blurred one. Motion after focus: a streak of a sharp picture.
        if (settings.lens().depthOfField()) {
            filters.add(new DepthOfFieldFilter(settings.lens()));
        }
        if (settings.lens().motionBlur()) {
            filters.add(new MotionBlurFilter(settings.lens()));
        }
        if (settings.bloom()) {
            filters.add(new MipBloomFilter());
        }
        filters.add(new AcesToneMap(settings.exposure(), settings.autoExposure()));
        // On what the screen shows, ahead of the filters that smooth and sharpen it.
        if (settings.lut() != null || settings.vignette() > 0f) {
            filters.add(new GradeFilter(settings.lut(), settings.vignette()));
        }
        switch (settings.antiAliasing()) {
            case FXAA -> filters.add(new FXAAFilter());
            case TAA -> filters.add(new SharpenFilter());
            case NONE -> { }
        }
        return filters;
    }
}
