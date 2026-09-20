package uz.dukeengine.client3d;

import com.jme3.material.MatParamOverride;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Spatial;
import com.jme3.shader.VarType;
import java.util.HashMap;
import java.util.Map;

/**
 * A creature that is hit goes white for an instant.
 *
 * <p>The number that comes off it says how much; this says WHICH, and says it
 * where the player is already looking. In a crowd of six skeletons round a nova,
 * the ones whose health moved are the ones that flashed.
 *
 * <p><b>An override rather than a material.</b> A creature's material is built
 * once and worn by every piece of it, and swapping it out for a blow would be a new
 * material per blow. jME can instead lay a value over one parameter for everything
 * under a node and take it off again, so a flash costs one colour a frame while it
 * shows and nothing once it has gone. The parameter is the lit material's Ambient:
 * the part of its colour the lights have no say in, so raising it towards white
 * whitens the creature however dark the room is.
 *
 * <p>Drawing only. Nothing here is read by the game, and a flash that never came
 * would change nothing but how a fight reads.
 */
final class HitFlash {

    /** What a flash is laid over: the Ambient of Lighting.j3md, which every creature wears. */
    static final String PARAMETER = "Ambient";

    private static final class Flash {
        private final Spatial root;
        private final ColorRGBA own;
        private final MatParamOverride override;
        private float age;

        private Flash(Spatial root, ColorRGBA own, MatParamOverride override) {
            this.root = root;
            this.own = own;
            this.override = override;
        }
    }

    private final Map<Integer, Flash> flashing = new HashMap<>();
    private final ColorRGBA colour;
    private final float seconds;
    private final float strength;

    HitFlash(Visuals.HitFlashLook look) {
        var chosen = look == null ? Visuals.HitFlashLook.NONE : look;
        this.colour = new ColorRGBA(((chosen.colour() >> 16) & 0xFF) / 255f,
                ((chosen.colour() >> 8) & 0xFF) / 255f, (chosen.colour() & 0xFF) / 255f, 1f);
        this.seconds = chosen.seconds();
        this.strength = Math.clamp(chosen.strength(), 0f, 1f);
    }

    /**
     * It was hit: it flashes, or its flash starts again if it already was.
     *
     * @param own the Ambient its material already has, which is where the flash
     *            fades back to -- a flash that ended on plain white or plain black
     *            would leave a creature a different colour from its friends
     */
    void struck(int unitId, Spatial root, ColorRGBA own) {
        if (root == null || seconds <= 0f || strength <= 0f) {
            return;
        }
        var flash = flashing.get(unitId);
        if (flash != null && flash.root == root) {
            // Again, from the top, rather than a second one over the first: two
            // blows in a breath are one creature being hit hard, not one twice as
            // white.
            flash.age = 0f;
            flash.override.setValue(colourAt(flash.own, 0f));
            return;
        }
        forget(unitId);
        var base = own == null ? ColorRGBA.White.clone() : own.clone();
        var override = new MatParamOverride(VarType.Vector4, PARAMETER, colourAt(base, 0f));
        root.addMatParamOverride(override);
        flashing.put(unitId, new Flash(root, base, override));
    }

    /** Fade every flash on, and take off the ones that are over. */
    void update(float tpf) {
        var going = flashing.values().iterator();
        while (going.hasNext()) {
            var flash = going.next();
            flash.age += tpf;
            if (flash.age >= seconds) {
                flash.root.removeMatParamOverride(flash.override);
                going.remove();
            } else {
                flash.override.setValue(colourAt(flash.own, flash.age / seconds));
            }
        }
    }

    /**
     * Its own colour drawn towards the flash's: all the way at once, and back along
     * a square -- a struck thing is brightest the instant it is struck.
     */
    ColorRGBA colourAt(ColorRGBA own, float through) {
        float left = 1f - Math.clamp(through, 0f, 1f);
        return new ColorRGBA().interpolateLocal(own, colour, strength * left * left);
    }

    /** It is gone, or about to be drawn again from scratch: no flash left over it. */
    void forget(int unitId) {
        var flash = flashing.remove(unitId);
        if (flash != null) {
            flash.root.removeMatParamOverride(flash.override);
        }
    }

    /** Every flash off at once: a world being rebuilt has nobody in it being hit. */
    void clear() {
        for (var flash : flashing.values()) {
            flash.root.removeMatParamOverride(flash.override);
        }
        flashing.clear();
    }

    int flashingCount() {
        return flashing.size();
    }
}
