package uz.dukeengine.render;

import com.jme3.post.Filter;
import com.jme3.post.filters.BloomFilter;
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
        if (settings.bloom()) {
            var bloom = new BloomFilter(BloomFilter.GlowMode.Scene);
            bloom.setExposurePower(4f);
            bloom.setBloomIntensity(0.6f);
            bloom.setBlurScale(1.5f);
            filters.add(bloom);
        }
        filters.add(new AcesToneMap(settings.exposure()));
        if (settings.fxaa()) {
            filters.add(new FXAAFilter());
        }
        return filters;
    }
}
