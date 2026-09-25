package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.scene.Node;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import uz.dukeengine.core.math.Coord3D;

/**
 * The pictures laid on the ground under things by the words they hold — {@link Visuals.UnitVisual#groundPicture}, the
 * reference's horde and shadow decals: the one the words best fit laid under the thing and turned with it, fading in as
 * the one it replaces fades out, over its picture of no words — its shadow — which stays.
 */
final class GroundPictures {

    /** One picture laid under a thing, how much of it is seen, and whether it is on its way out. */
    private static final class Laid {
        private final Visuals.UnitVisual.GroundPicture picture;
        private final GroundDecal decal;
        private float opacity;
        private boolean leaving;

        private Laid(Visuals.UnitVisual.GroundPicture picture, GroundDecal decal, float opacity) {
            this.picture = picture;
            this.decal = decal;
            this.opacity = opacity;
        }
    }

    /** What a thing has laid under it, for a look: which picture, how much of it is seen, and whether it is going. */
    record Shown(String picture, float opacity, boolean leaving, GroundDecal decal) {
    }

    private final AssetManager assets;
    private final Node node;
    private final Map<Integer, List<Laid>> laid = new HashMap<>();

    /** How far over its shadow a picture its words chose is laid, so that it is drawn over it. */
    private static final float OVER_THE_SHADOW = 0.02f;

    GroundPictures(AssetManager assets, Node node) {
        this.assets = assets;
        this.node = node;
    }

    /**
     * A thing seen this frame: the picture its words best fit laid where it stands and turned the way it faces, the one
     * before fading out as it fades in. With no words — {@code holding} null — none fits: a dead thing's pictures fade
     * out, as the reference's decal does when its thing dies ({@code Object::onDie}).
     *
     * @param frames how many of the game's frames have passed since the last time: what a fade moves on by
     */
    void see(int id, Visuals.UnitVisual look, Set<String> holding, Coord3D at, float facing, float frames,
            BiFunction<Float, Float, Float> floorAt) {
        var pictures = laid.computeIfAbsent(id, key -> new ArrayList<>());
        int chosen = holding == null ? -1 : look.groundPictureFor(holding);
        var wanted = chosen < 0 ? null : look.groundPictures.get(chosen);
        // Its shadow stays under whatever its words choose, as the reference's shadow decal lies under its state's.
        var shadow = holding == null ? null : look.plainGroundPicture();
        var there = new java.util.HashSet<Visuals.UnitVisual.GroundPicture>();
        for (var one : pictures) {
            if ((one.picture == wanted || one.picture == shadow) && !one.leaving) {
                there.add(one.picture);
            } else {
                one.leaving = true;
            }
        }
        for (var picture : java.util.Arrays.asList(shadow, wanted)) {
            if (picture != null && there.add(picture)) {
                float lift = picture == shadow ? GroundDecal.LIFT : GroundDecal.LIFT + OVER_THE_SHADOW;
                pictures.add(new Laid(picture, new GroundDecal(assets, node, lift),
                        picture.fadeFrames() == 0 ? 1f : 0f));
            }
        }
        for (var each = pictures.iterator(); each.hasNext();) {
            var one = each.next();
            float step = one.picture.fadeFrames() == 0 ? 1f : frames / one.picture.fadeFrames();
            one.opacity = Math.clamp(one.opacity + (one.leaving ? -step : step), 0f, 1f);
            if (one.leaving && one.opacity <= 0f) {
                one.decal.remove();
                each.remove();
                continue;
            }
            one.decal.lay(at, one.picture.width(), one.picture.depth(), facing, one.picture.picture(), 0xFFFFFF,
                    one.opacity, floorAt);
        }
        if (pictures.isEmpty()) {
            laid.remove(id);
        }
    }

    /** A thing gone, or out of sight: what lay under it goes with it. */
    void forget(int id) {
        var pictures = laid.remove(id);
        if (pictures != null) {
            pictures.forEach(one -> one.decal.remove());
        }
    }

    /** Everything gone at once, for a new world. */
    void clear() {
        laid.values().forEach(pictures -> pictures.forEach(one -> one.decal.remove()));
        laid.clear();
    }

    /** What lies under a thing now. */
    List<Shown> under(int id) {
        return laid.getOrDefault(id, List.of()).stream()
                .map(one -> new Shown(one.picture.picture(), one.opacity, one.leaving, one.decal)).toList();
    }
}
