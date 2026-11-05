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
        if (settings.ambientOcclusion()) {
            filters.add(new GtaoFilter(settings.occlusionRadius()));
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
        if (settings.bloom()) {
            filters.add(new MipBloomFilter());
        }
        filters.add(new AcesToneMap(settings.exposure(), settings.autoExposure()));
        switch (settings.antiAliasing()) {
            case FXAA -> filters.add(new FXAAFilter());
            case TAA -> filters.add(new SharpenFilter());
            case NONE -> { }
        }
        return filters;
    }
}
