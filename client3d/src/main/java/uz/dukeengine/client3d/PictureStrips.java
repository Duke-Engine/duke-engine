package uz.dukeengine.client3d;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.event.StripPlayed;

/**
 * Picture strips played at points of the world — see {@link StripPlayed} — drawn as the reference's world animations
 * are ({@code InGameUI::updateAndDrawWorldAnimations}): centred on the point's place on screen at each picture's own
 * size, the point rising as the game's frames go, the last second fading to nothing, and drawn only while the point is
 * in the player's clear view. Stepped by the game's frames, so a paused game holds a strip where it is.
 */
final class PictureStrips {

    /** How long before its end a strip fades: the reference's {@code FRAMES_BEFORE_EXPIRE_TO_FADE}, a second. */
    static final int FADE_FRAMES = GameConstants.LOGICFRAMES_PER_SECOND;

    private record Playing(StripPlayed played, Visuals.Strip strip, int since, Geometry quad) {

        int frames() {
            return Math.round(played.seconds() * GameConstants.LOGICFRAMES_PER_SECOND);
        }
    }

    private final Node root = new Node("picture-strips");
    private final Function<String, Texture> pictures;
    private final Function<String, Visuals.Strip> strips;
    private final Function<Texture, Material> material;
    private final List<Playing> playing = new ArrayList<>();

    /**
     * @param pictures a picture by its whole path, or null where it will not load
     * @param strips   the game's strip of a name, or null for none
     * @param material an unshaded, alpha-blended material showing a picture
     */
    PictureStrips(Node gui, Function<String, Texture> pictures, Function<String, Visuals.Strip> strips,
            Function<Texture, Material> material) {
        this.pictures = pictures;
        this.strips = strips;
        this.material = material;
        gui.attachChild(root);
    }

    /** A strip played in the game's frame {@code frame}, drawn from the next; a name it has none of draws nothing. */
    void add(StripPlayed played, int frame) {
        var strip = played.strip() == null ? null : strips.apply(played.strip());
        if (strip == null || strip.frames().isEmpty() || played.seconds() <= 0f) {
            return;
        }
        var quad = new Geometry("strip", new Quad(1f, 1f));
        quad.setQueueBucket(RenderQueue.Bucket.Gui);
        quad.setCullHint(Spatial.CullHint.Always);
        root.attachChild(quad);
        playing.add(new Playing(played, strip, frame, quad));
    }

    /** How high a strip stands {@code age} frames after it was played: its point, and its rise so far. */
    static float heightAt(StripPlayed played, int age) {
        float frames = played.seconds() * GameConstants.LOGICFRAMES_PER_SECOND;
        return played.where().z() + played.rise() * Math.clamp(age / frames, 0f, 1f);
    }

    /** Its alpha {@code age} frames after it was played, of {@code frames}: whole, then the last second fading out. */
    static float alphaAt(int age, int frames) {
        int left = frames - age;
        return left >= FADE_FRAMES ? 1f : Math.max(0f, left / (float) FADE_FRAMES);
    }

    /**
     * Every strip as it stands in the game's frame {@code frame}: risen, faded, on its picture of the moment, hidden
     * where {@code seen} says its point is not in clear view or it is behind the camera, and gone once its time is up.
     */
    void update(int frame, Camera camera, BiPredicate<Float, Float> seen) {
        var each = playing.iterator();
        while (each.hasNext()) {
            var one = each.next();
            int age = frame - one.since() - 1; // drawn from the frame after it was played
            if (age >= one.frames()) {
                one.quad().removeFromParent();
                each.remove();
                continue;
            }
            var where = one.played().where();
            float height = heightAt(one.played(), age);
            var onScreen = camera.getScreenCoordinates(new Vector3f(where.x(), height, where.y()));
            var picture = age < 0 ? null
                    : pictures.apply(one.strip().frameAt(age * GameConstants.SECONDS_PER_LOGICFRAME));
            boolean shown = picture != null && onScreen.z <= 1f && seen.test(where.x(), where.y());
            one.quad().setCullHint(shown ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
            if (!shown) {
                continue;
            }
            var look = one.quad().getMaterial() != null
                    && one.quad().getMaterial().getTextureParam("ColorMap") != null
                    && one.quad().getMaterial().getTextureParam("ColorMap").getTextureValue() == picture
                    ? one.quad().getMaterial() : material.apply(picture);
            look.setColor("Color", new ColorRGBA(1f, 1f, 1f, alphaAt(age, one.frames())));
            look.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            one.quad().setMaterial(look);
            float wide = picture.getImage().getWidth();
            float tall = picture.getImage().getHeight();
            one.quad().setLocalScale(wide, tall, 1f);
            one.quad().setLocalTranslation(onScreen.x - wide / 2f, onScreen.y - tall / 2f, 0f);
        }
    }

    /** How many are up, for a test. */
    int count() {
        return playing.size();
    }

    /** The one played {@code index}-th of those up, as drawn, for a test. */
    Geometry quad(int index) {
        return playing.get(index).quad();
    }

    /** Take them all down — a new world has nothing playing in it. */
    void clear() {
        playing.forEach(one -> one.quad().removeFromParent());
        playing.clear();
    }
}
