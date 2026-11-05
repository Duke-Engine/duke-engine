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
        if (settings.fogDensity() > 0f) {
            filters.add(new HeightFogFilter(settings));
        }
        if (settings.bloom()) {
            filters.add(new MipBloomFilter());
        }
        filters.add(new AcesToneMap(settings.exposure(), settings.autoExposure()));
        if (settings.fxaa()) {
            filters.add(new FXAAFilter());
        }
        return filters;
    }
}
