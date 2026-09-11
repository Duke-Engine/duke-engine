package uz.duke.client3d;

import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.ArrayList;
import java.util.List;

/**
 * The numbers that come off a creature when it is hurt or healed.
 *
 * <p>What they are for is in {@link HitNumbers}; this is the part that knows about
 * jME. Two jobs: keep a number alive for its short life, and put it where the
 * creature it belongs to is <em>on screen</em>, which changes every frame as the
 * camera moves and as the creature walks.
 *
 * <p>Drawn flat on the interface rather than standing in the world, which is what
 * the genre does and is right for the same reason a health bar is: a number is
 * read, and a read thing should not get smaller because the thing it is about
 * walked away, nor lie down when the camera tips. So it is projected — the
 * creature's place in the world becomes a place on the screen, and from there the
 * number simply drifts up.
 *
 * <p>Pooled, like everything else on this layer: a busy fight throws a dozen of
 * these a second and a fresh {@code BitmapText} for each would be a fresh mesh
 * for each. A spent one is emptied and lent out again; the scene settles at the
 * busiest moment it has seen.
 */
final class FloatingNumbers {

    private final BitmapFont font;
    private final Node root;
    private final HitNumbers look;
    private final List<Mark> pool = new ArrayList<>();

    /** How many have been thrown, which is only ever used to alternate the lean. */
    private int thrown;

    /** One number in flight: where in the world it belongs, and when it began. */
    private static final class Mark {
        private final BitmapText text;
        private float bornAt = Float.NaN;
        private float worldX;
        private float worldY;
        private float height;
        private int colour;
        private int side;

        private Mark(BitmapText text) {
            this.text = text;
        }

        private boolean spent(HitNumbers look, float now) {
            return Float.isNaN(bornAt) || look.spent(now - bornAt);
        }
    }

    FloatingNumbers(BitmapFont font, Node gui, HitNumbers look) {
        this.font = font;
        this.look = look == null ? HitNumbers.DEFAULT : look;
        this.root = new Node("hit-numbers");
        gui.attachChild(root);
    }

    /**
     * Throw a number off a creature.
     *
     * @param height how high above the floor it starts, in world units — over the
     *               creature's head rather than at its feet
     */
    void add(HealthWatch.Change change, float now, float height) {
        var mark = borrow();
        mark.bornAt = now;
        mark.worldX = change.x();
        mark.worldY = change.y();
        mark.height = height;
        mark.colour = look.colourOf(change.healed(), change.his());
        // Alternating, so two landing in the same instant lean apart instead of
        // being drawn one exactly on top of the other and reading as one number.
        mark.side = (thrown++ % 2 == 0) ? 1 : -1;
        int whole = Math.max(1, Math.round(change.amount()));
        mark.text.setText(change.healed() ? "+" + whole : Integer.toString(whole));
        mark.text.setCullHint(Spatial.CullHint.Inherit);
    }

    /**
     * Move every number that is still up, and put away the ones that are not.
     *
     * <p>Projected every frame rather than placed once. The creature walks, the
     * camera pans, and a number that stayed where the screen used to be would
     * belong to a patch of floor rather than to anybody.
     */
    void update(float now, Camera camera, java.util.function.BiFunction<Float, Float, Float> floorAt) {
        for (var mark : pool) {
            if (mark.spent(look, now)) {
                put(mark);
                continue;
            }
            var step = look.at(now - mark.bornAt, mark.side);
            float floor = floorAt == null ? 0f : floorAt.apply(mark.worldX, mark.worldY);
            var onScreen = camera.getScreenCoordinates(
                    new Vector3f(mark.worldX, floor + mark.height, mark.worldY));
            if (onScreen.z > 1f) {
                mark.text.setCullHint(Spatial.CullHint.Always); // behind the camera
                continue;
            }
            mark.text.setCullHint(Spatial.CullHint.Inherit);
            mark.text.setColor(Glow.colour(mark.colour, look.brightness(), step.alpha()));
            mark.text.setLocalTranslation(
                    onScreen.x - mark.text.getLineWidth() / 2f + step.aside(),
                    onScreen.y + step.up(), 0f);
        }
    }

    /** Take them all down — a new world has nobody in it who was just hit. */
    void clear() {
        for (var mark : pool) {
            put(mark);
        }
    }

    /** How many numbers the pool has had to make. Package-private so it can be checked. */
    int madeSoFar() {
        return pool.size();
    }

    /** How many are on screen right now. */
    int showing() {
        int up = 0;
        for (var mark : pool) {
            if (!Float.isNaN(mark.bornAt)) {
                up++;
            }
        }
        return up;
    }

    Node node() {
        return root;
    }

    private void put(Mark mark) {
        mark.bornAt = Float.NaN;
        mark.text.setCullHint(Spatial.CullHint.Always);
    }

    private Mark borrow() {
        for (var mark : pool) {
            if (Float.isNaN(mark.bornAt)) {
                return mark;
            }
        }
        var text = new BitmapText(font);
        text.setSize(font.getCharSet().getRenderedSize() * look.textScale());
        text.setCullHint(Spatial.CullHint.Always);
        root.attachChild(text);
        var mark = new Mark(text);
        pool.add(mark);
        return mark;
    }
}
