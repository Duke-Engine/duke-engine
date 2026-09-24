package uz.dukeengine.client3d;

import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import uz.dukeengine.core.event.TextFloated;

/**
 * Texts floated up from points of the world — see {@link TextFloated} — drawn as the reference's floating text is
 * ({@code InGameUI}): from the point's place on screen, centred, over a black drop shadow of the same alpha, rising
 * {@code rise} pixels a frame of the game, keeping its colour for {@code hold} frames and then losing {@code int(k ×
 * fade)} of its alpha on the k-th frame after, and drawn only while its point is in the player's clear view. Stepped by
 * the game's frames, so a paused game holds a text where it is.
 */
final class FloatingTexts {

    /**
     * How a floated text moves and fades — the reference's, measured from its code: a pixel a frame, ten frames at its
     * colour, then a tenth of the frames since times one of its alpha lost each frame, so an alpha of 230 is gone 82
     * frames after it appeared.
     */
    record Look(float rise, int hold, float fade) {

        static final Look REFERENCE = new Look(1f, 10, 0.1f);

        /** How far up it has risen {@code age} frames after it appeared, in pixels. */
        float riseAt(int age) {
            return rise * Math.max(0, age);
        }

        /** Its alpha {@code age} frames after it appeared, from {@code alpha}: 0 once gone. */
        int alphaAt(int age, int alpha) {
            int lost = 0;
            for (int k = 1; k <= age - hold; k++) {
                lost += (int) (k * fade + 1e-6f); // a tenth times ten is one, not a hair under it
            }
            return Math.max(0, alpha - lost);
        }
    }

    private record Floating(TextFloated text, int since, BitmapText face, BitmapText shadow) {
    }

    private final BitmapFont font;
    private final Node root = new Node("floating-texts");
    private final Look look;
    private final List<Floating> floating = new ArrayList<>();

    FloatingTexts(BitmapFont font, Node gui, Look look) {
        this.font = font;
        this.look = look == null ? Look.REFERENCE : look;
        gui.attachChild(root);
    }

    /** A text floated in the game's frame {@code frame}, drawn from the next. */
    void add(TextFloated text, int frame) {
        var face = new BitmapText(font);
        face.setText(text.text());
        var shadow = new BitmapText(font);
        shadow.setText(text.text());
        root.attachChild(shadow);
        root.attachChild(face);
        floating.add(new Floating(text, frame, face, shadow));
    }

    /**
     * Every text as it stands in the game's frame {@code frame}: risen, faded, hidden where {@code seen} says its
     * point is not in clear view or it is behind the camera, and gone once faded out.
     */
    void update(int frame, Camera camera, BiPredicate<Float, Float> seen) {
        var each = floating.iterator();
        while (each.hasNext()) {
            var one = each.next();
            int age = frame - one.since() - 1; // drawn from the frame after it was floated
            int alpha = look.alphaAt(age, one.text().argb() >>> 24);
            if (alpha <= 0) {
                one.face().removeFromParent();
                one.shadow().removeFromParent();
                each.remove();
                continue;
            }
            var where = one.text().where();
            var onScreen = camera.getScreenCoordinates(new Vector3f(where.x(), where.z(), where.y()));
            boolean shown = age >= 0 && onScreen.z <= 1f && seen.test(where.x(), where.y());
            one.face().setCullHint(shown ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
            one.shadow().setCullHint(shown ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
            if (!shown) {
                continue;
            }
            float x = onScreen.x - one.face().getLineWidth() / 2f;
            float y = onScreen.y + one.face().getLineHeight() / 2f + look.riseAt(age);
            int argb = one.text().argb();
            one.face().setColor(new ColorRGBA(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f,
                    (argb & 0xFF) / 255f, alpha / 255f));
            one.face().setLocalTranslation(x, y, 1f);
            one.shadow().setColor(new ColorRGBA(0f, 0f, 0f, alpha / 255f));
            one.shadow().setLocalTranslation(x + 1f, y - 1f, 0f);
        }
    }

    /** How many are up, for a test. */
    int count() {
        return floating.size();
    }

    /** The one floated {@code index}-th of those up, as drawn, for a test. */
    BitmapText face(int index) {
        return floating.get(index).face();
    }

    /** Take them all down — a new world has nothing floating in it. */
    void clear() {
        floating.forEach(one -> {
            one.face().removeFromParent();
            one.shadow().removeFromParent();
        });
        floating.clear();
    }
}
