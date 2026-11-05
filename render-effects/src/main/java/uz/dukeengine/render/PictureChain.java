package uz.dukeengine.render;

import com.jme3.math.Vector3f;
import com.jme3.post.Filter;
import com.jme3.post.filters.BloomFilter;
import com.jme3.post.filters.FXAAFilter;
import com.jme3.post.filters.ToneMapFilter;
import com.jme3.post.ssao.SSAOFilter;
import java.util.ArrayList;
import java.util.List;

/** The filters over a finished HDR picture, in the order they must run. */
public final class PictureChain {

    private PictureChain() {
    }

    /** shortcut: one SSAO radius for every game's world size, make it a setting when a game needs another. */
    public static List<Filter> filters(PictureSettings settings) {
        var filters = new ArrayList<Filter>();
        if (settings.ambientOcclusion()) {
            filters.add(new SSAOFilter(1.6f, 1.4f, 0.6f, 0.1f));
        }
        if (settings.bloom()) {
            var bloom = new BloomFilter(BloomFilter.GlowMode.Scene);
            bloom.setExposurePower(4f);
            bloom.setBloomIntensity(0.6f);
            bloom.setBlurScale(1.5f);
            filters.add(bloom);
        }
        var toneMap = new ToneMapFilter(new Vector3f(4f, 4f, 4f));
        toneMap.setExposure(settings.exposure());
        filters.add(toneMap);
        if (settings.fxaa()) {
            filters.add(new FXAAFilter());
        }
        return filters;
    }
}
