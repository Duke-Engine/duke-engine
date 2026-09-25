package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.light.AmbientLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import uz.dukeengine.game.view.UnitView;

/**
 * How a thing kept from some players looks to those it is not kept from — {@link Visuals#seeThrough} and {@link
 * Visuals#glow}, the reference's {@code StealthUpdate} look and its heat vision. None of it reaches the simulation, which
 * alone decides who sees a thing at all.
 *
 * <p><b>See-through:</b> holding the game's word, a thing is drawn to its own side and its allies at an opacity pulsing
 * from its template's faintest to whole and back ({@link #opacityAt}), and blinks on their radar ({@link #radarAlpha}).
 *
 * <p><b>Glow:</b> holding the second word, it is drawn to everyone else as a glow instead of its model — an additive
 * pass of the model alone in the reference's heat-vision colour — and to its own side and allies as a light of that
 * colour over its see-through look. Whole every frame it holds the word, fading ×0.8 each drawn frame once it does not,
 * gone under 0.001 ({@link #glowAfter}).
 */
final class StealthLook {

    /** {@code StealthUpdate}'s pulse: its phase moves on 0.2 a logic frame, a pulse in about 31 frames. */
    static final float PULSE_STEP = 0.2f;

    /** The heat-vision pass's emissive colour, {@code W3DScene}'s {@code m_heatVisionOnlyPass}. */
    private static final ColorRGBA HEAT = new ColorRGBA(0.5f, 0.2f, 0f, 1f);

    /** What a glow is multiplied by each drawn frame without the word — {@code Drawable::draw} — and where it ends. */
    private static final float GLOW_FADE = 0.8f;
    private static final float GLOW_GONE = 0.001f;

    /** How seen it is at {@code phase}: {@code min + (1 − min)(0.5 + 0.5 sin φ)}, as {@code StealthUpdate::update}. */
    static float opacityAt(float faintest, float phase) {
        return faintest + (1f - faintest) * (0.5f + 0.5f * FastMath.sin(phase));
    }

    /** A glow a drawn frame on: whole while the word is held, and otherwise fading until it is gone. */
    static float glowAfter(float strength, boolean held) {
        if (held) {
            return 1f;
        }
        float next = strength * GLOW_FADE;
        return next < GLOW_GONE ? 0f : next;
    }

    /**
     * Its blip's alpha on its own side's radar at logic frame {@code frame}: up from 32 of 255 to whole once a second —
     * the ramp {@code W3DRadar} means (its first half-second wraps an unsigned byte there, which is no look anyone chose).
     */
    static float radarAlpha(int frame) {
        return (32f + Math.floorMod(frame, 30) / 29f * 223f) / 255f;
    }

    private static final class Look {
        private float phase;
        private float glow;
        /** What each piece was drawn with before this took it over, to be given back. */
        private final Map<Geometry, Material> own = new LinkedHashMap<>();
        private final Map<Geometry, RenderQueue.Bucket> buckets = new LinkedHashMap<>();
        private Material heat;
        private AmbientLight light;
        private Spatial on;
    }

    private final AssetManager assets;
    private final Map<Integer, Look> looks = new HashMap<>();

    StealthLook(AssetManager assets) {
        this.assets = assets;
    }

    /**
     * A thing seen this frame.
     *
     * @param frames how many of the game's frames have passed since the last time: what its pulse moves on by
     */
    void see(UnitView view, Spatial body, Visuals visuals, Visuals.UnitVisual look, float frames) {
        if (body == null) {
            return;
        }
        var words = view.conditions();
        boolean seeThrough = visuals.getSeeThroughWord() != null && words.contains(visuals.getSeeThroughWord())
                && view.allied();
        boolean held = look.glows && visuals.getGlowWord() != null && words.contains(visuals.getGlowWord());
        var state = looks.get(view.id());
        if (state == null) {
            if (!seeThrough && !held) {
                return;
            }
            state = new Look();
            looks.put(view.id(), state);
        }
        state.glow = glowAfter(state.glow, held);
        if (seeThrough) {
            state.phase += PULSE_STEP * frames;
        }
        boolean glowing = state.glow > 0f;
        boolean asGlow = glowing && !view.allied();
        dress(state, body, asGlow);
        if (asGlow) {
            state.heat.setColor("Color", HEAT.mult(state.glow));
        } else {
            fade(body, seeThrough ? opacityAt(look.seeThroughFaintest, state.phase) : 1f);
        }
        light(state, body, glowing && view.allied() ? HEAT.mult(state.glow) : null);
        if (!seeThrough && !glowing) {
            forget(view.id());
        }
    }

    /** Its pieces drawn as the glow, or as they were: swapped only when that changes. */
    private void dress(Look state, Spatial body, boolean asGlow) {
        boolean dressed = !state.own.isEmpty();
        if (asGlow == dressed) {
            return;
        }
        if (asGlow) {
            if (state.heat == null) {
                state.heat = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
                var render = state.heat.getAdditionalRenderState();
                render.setBlendMode(RenderState.BlendMode.Additive);
                render.setDepthWrite(false);
            }
            body.depthFirstTraversal(spatial -> {
                if (spatial instanceof Geometry geometry && geometry.getMaterial() != null) {
                    state.own.put(geometry, geometry.getMaterial());
                    state.buckets.put(geometry, geometry.getLocalQueueBucket());
                    geometry.setMaterial(state.heat); // no first pass and no shadow: the glow alone
                    geometry.setQueueBucket(RenderQueue.Bucket.Transparent);
                }
            });
        } else {
            undress(state);
        }
    }

    private static void undress(Look state) {
        state.own.forEach(Geometry::setMaterial);
        state.buckets.forEach(Geometry::setQueueBucket);
        state.own.clear();
        state.buckets.clear();
    }

    /** A light of the glow's colour over the thing alone, or none. */
    private static void light(Look state, Spatial body, ColorRGBA colour) {
        if (colour == null) {
            if (state.light != null && state.on != null) {
                state.on.removeLight(state.light);
            }
            state.light = null;
            state.on = null;
            return;
        }
        if (state.light == null) {
            state.light = new AmbientLight();
            body.addLight(state.light);
            state.on = body;
        }
        state.light.setColor(colour);
    }

    /** Every piece drawn {@code opacity} seen; whole again, drawn as it was. */
    static void fade(Spatial body, float opacity) {
        body.depthFirstTraversal(spatial -> {
            if (!(spatial instanceof Geometry geometry) || geometry.getMaterial() == null) {
                return;
            }
            var material = geometry.getMaterial();
            String channel = material.getMaterialDef().getMaterialParam("Diffuse") != null ? "Diffuse"
                    : material.getMaterialDef().getMaterialParam("Color") != null ? "Color" : null;
            if (channel == null) {
                return;
            }
            boolean faded = Boolean.TRUE.equals(geometry.getUserData("stealth.faded"));
            if (opacity >= 1f && !faded) {
                return; // never faded, left exactly as it was
            }
            var colour = material.getParamValue(channel) instanceof ColorRGBA was ? was.clone() : ColorRGBA.White.clone();
            colour.a = opacity;
            material.setColor(channel, colour);
            if (opacity < 1f) {
                if (!faded) {
                    geometry.setUserData("stealth.faded", true);
                    geometry.setUserData("stealth.blend", material.getAdditionalRenderState().getBlendMode().name());
                    geometry.setUserData("stealth.bucket", geometry.getLocalQueueBucket().name());
                }
                material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
                geometry.setQueueBucket(RenderQueue.Bucket.Transparent);
            } else {
                material.getAdditionalRenderState().setBlendMode(
                        RenderState.BlendMode.valueOf(geometry.getUserData("stealth.blend")));
                geometry.setQueueBucket(RenderQueue.Bucket.valueOf(geometry.getUserData("stealth.bucket")));
                geometry.setUserData("stealth.faded", false);
            }
        });
    }

    /** Whether a thing is drawn see-through or as a glow now, for a test. */
    boolean looking(int id) {
        return looks.containsKey(id);
    }

    /** A thing gone, or drawn from scratch: what this laid over it is taken off. */
    void forget(int id) {
        var state = looks.remove(id);
        if (state == null) {
            return;
        }
        undress(state);
        if (state.light != null && state.on != null) {
            state.on.removeLight(state.light);
        }
    }

    /** Everything taken off, for a new world. */
    void clear() {
        for (var id : java.util.List.copyOf(looks.keySet())) {
            forget(id);
        }
    }
}
