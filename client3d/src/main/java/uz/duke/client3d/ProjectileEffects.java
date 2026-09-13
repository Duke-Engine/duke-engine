package uz.duke.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh;
import com.jme3.effect.shapes.EmitterSphereShape;
import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Sphere;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a thing in flight looks like: what it trails, what it is made of, and what
 * it leaves where it lands.
 *
 * <p><b>Drawing only.</b> Nothing here is asked before anything is decided: the
 * simulation says where a projectile is and when it stopped existing, and this
 * hangs fire on it. Every particle may be as random as it likes — none of it is
 * ever read back.
 *
 * <p>Three things are built once and handed round rather than made per shot, and
 * each for its own reason:
 *
 * <ul>
 *   <li><b>Emitters.</b> A {@link ParticleEmitter} carries a mesh, a material and
 *       a few hundred floats of particle state. Fifty arrows a fight, each making
 *       and dropping one, is a scene that grows and a collector that never rests.
 *   <li><b>Lights.</b> Dynamic lights are the expensive kind. The terrain shader
 *       takes four and the creatures' takes what jME gives it, so there is a
 *       budget and the fifth burning thing flies dark. It still has its trail, so
 *       what is lost is the glow on the floor rather than the shot.
 *   <li><b>The spark.</b> One texture, drawn here rather than shipped: a soft
 *       round dot is a dozen lines of arithmetic and the falloff is worth having
 *       under control.
 * </ul>
 *
 * <p>A projectile too far off to be worth lighting gets neither light nor
 * particles — the client is already refusing to draw anything the fog hides, and
 * this is the same idea a hundred units further out.
 */
final class ProjectileEffects {

    /** The effects this client knows how to draw; see {@link Visuals.EffectVisual}. */
    static final String FLAME_TRAIL = Visuals.EffectVisual.FLAME_TRAIL;
    static final String GLOW_ORB = Visuals.EffectVisual.GLOW_ORB;
    static final String IMPACT_BURST = Visuals.EffectVisual.IMPACT_BURST;
    static final String GLOW_PARTS = Visuals.EffectVisual.GLOW_PARTS;

    /** How wide the generated spark is, in pixels. Small on purpose: it is a blur. */
    private static final int SPARK_PIXELS = 32;

    /** How many emitters one recipe may have alight at once. */
    private final int perRecipe;
    /** How many burst emitters the whole client may have alight at once. */
    private final int bursts;

    private final AssetManager assets;
    private final Node root;
    private final Visuals visuals;
    private final float lightDistance;

    /**
     * The lights, made once and moved about.
     *
     * <p>Free ones are kept in a list of their own rather than recognised by
     * looking at them. A light's own state cannot say whether it is in use:
     * jME reads a radius of zero as <em>infinite</em>, and a black light is still
     * a light the renderer has to consider.
     */
    private final LightPool pool;
    private final Map<Integer, PointLight> lit = new HashMap<>();

    /** Idle emitters, by recipe name, and the ones in the air. */
    private final Map<String, List<ParticleEmitter>> spare = new HashMap<>();
    private final Map<Integer, Trail> flying = new HashMap<>();
    private final List<Burst> burning = new ArrayList<>();

    private Texture spark;

    private record Trail(String recipe, ParticleEmitter emitter) {
    }

    private static final class Burst {
        private final ParticleEmitter emitter;
        private final PointLight light;
        /** What it flashed at, kept so the fade is a fraction of it rather than of itself. */
        private final ColorRGBA flash;
        private final float seconds;
        private float left;

        Burst(ParticleEmitter emitter, PointLight light, ColorRGBA flash, float seconds) {
            this.emitter = emitter;
            this.light = light;
            this.flash = flash;
            this.seconds = Math.max(0.01f, seconds);
            this.left = this.seconds;
        }
    }

    ProjectileEffects(AssetManager assets, Node root, Visuals visuals,
            int maxLights, int perRecipe, int bursts, float lightDistance) {
        this.assets = assets;
        this.root = root;
        this.visuals = visuals;
        this.perRecipe = Math.max(0, perRecipe);
        this.bursts = Math.max(0, bursts);
        this.lightDistance = lightDistance;
        this.pool = new LightPool(root, maxLights);
    }

    /**
     * The lights, for anything else that burns to borrow from.
     *
     * <p>One pool for the whole client: two pools of four are eight lights to the
     * renderer and four to each pool, which is how a budget is quietly doubled.
     */
    LightPool lights() {
        return pool;
    }

    /** How many lights are burning right now, which is what the budget is about. */
    int litCount() {
        return lit.size() + (int) burning.stream().filter(b -> b.light != null).count();
    }

    /** Every light the pool owns, lit or not, for the terrain shader to read. */
    List<PointLight> allLights() {
        return pool.all();
    }

    /**
     * The body of a projectile that has no model, or {@code null} if this one is
     * drawn from a file like everything else.
     *
     * <p>Asked before the client falls back to a coloured capsule, because a
     * fireball drawn as a capsule with a gun barrel on it is worse than no
     * fireball at all.
     */
    Spatial bodyFor(Visuals.UnitVisual visual) {
        var recipe = visuals.effectNamed(visual == null ? null : visual.effect);
        if (recipe != null && recipe.hasLayers()) {
            // Drawn by its layers alone, like everything else about a layered look.
            // An orb as well was a second body -- and on a meteor's mark, a pale
            // disc sitting on the floor in the middle of its own warning. Empty
            // rather than null, or the client would give it a capsule instead.
            return new Node("drawn by its layers");
        }
        if (recipe == null || !recipe.has(GLOW_ORB) || recipe.orbSize <= 0f) {
            return null;
        }
        var orb = new Geometry("orb", new Sphere(8, 12, recipe.orbSize));
        // At the point its own fire burns from, and nowhere else. Left at the
        // node's origin it sat on the FLOOR while the flame flew at bow height,
        // and what the player saw was a yellow disc sliding along the ground
        // under a streak of sparks.
        orb.setLocalTranslation(visual.effectForward, visual.yOffset, 0f);
        var material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", toColour(recipe.colour, 1f));
        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.AlphaAdditive);
        material.getAdditionalRenderState().setDepthWrite(false);
        orb.setMaterial(material);
        orb.setQueueBucket(RenderQueue.Bucket.Transparent);
        return orb;
    }

    /**
     * Light the named pieces of a model from inside — a skeleton's eye sockets,
     * a rune, the coals in a brazier.
     *
     * <p>The one effect here that is not about something in flight, and the
     * cheapest by a distance: one material per piece and no light at all. Which is
     * the point. A torch on every skeleton in a room would be over the light
     * budget before the second one; a pair of burning eyes on every skeleton in
     * the game costs one draw call each and reads across a dark room better than a
     * light would.
     *
     * <p>Unshaded on purpose: a glowing thing is glowing, not lit. Lighting it
     * would make it dimmer in the dark, which is the opposite of the idea.
     */
    void lightThePartsOf(Spatial model, String recipeName) {
        var recipe = visuals.effectNamed(recipeName);
        if (model == null || recipe == null || !recipe.has(GLOW_PARTS) || recipe.parts.isEmpty()) {
            return;
        }
        model.depthFirstTraversal(spatial -> {
            if (!(spatial instanceof Geometry geometry) || !named(geometry, recipe.parts)) {
                return;
            }
            var material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
            material.setColor("Color", toColour(recipe.colour, 1f));
            material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.AlphaAdditive);
            geometry.setMaterial(material);
            geometry.setQueueBucket(RenderQueue.Bucket.Transparent);
            geometry.setShadowMode(RenderQueue.ShadowMode.Off);
        });
    }

    private static boolean named(Geometry geometry, List<String> parts) {
        var name = geometry.getName();
        if (name == null) {
            return false;
        }
        for (var part : parts) {
            if (name.contains(part)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A projectile has appeared: give it a trail and, if there is one to spare, a
     * light.
     *
     * <p>Far-off ones get neither. Not a saving so much as a judgement: a spark
     * two rooms away is a pixel, and the budget it takes is one the arrow in front
     * of the player wanted.
     */
    void appeared(int id, Visuals.UnitVisual visual, Node node, Vector3f at, Vector3f camera) {
        var recipeName = visual == null ? null : visual.effect;
        var recipe = visuals.effectNamed(recipeName);
        if (recipe == null || recipe.hasLayers() || tooFarOff(at, camera)) {
            // A recipe with layers carries its own trail and its own light -- see
            // LayeredEffects -- and drawing this one too would be the same shot in
            // two styles at once.
            return;
        }
        if (recipe.has(FLAME_TRAIL) && recipe.particles > 0) {
            var emitter = borrow(recipeName, recipe);
            if (emitter != null) {
                // On the thing, not under it. The node it hangs from is where the
                // unit stands; the thing itself is lifted off that and, for an
                // arrow, is a dozen units long — so a trail left at the origin
                // comes out of the ground behind the middle of the shaft.
                emitter.setLocalTranslation(visual.effectForward, visual.yOffset, 0f);
                node.attachChild(emitter);
                // No opening puff: a trail is what is left behind, and emitting a
                // full set on the first frame drops the whole of it at the muzzle.
                flying.put(id, new Trail(recipeName, emitter));
            }
        }
        if (recipe.lightPower > 0f) {
            var light = takeLight();
            if (light != null) {
                dress(light, recipe.lightColour, recipe.lightPower, recipe.lightRadius);
                light.setPosition(burningAt(visual, node));
                lit.put(id, light);
            }
        }
    }

    /** It moved: the light follows. The trail rides the node and needs nothing. */
    void moved(int id, Visuals.UnitVisual visual, Node node) {
        var light = lit.get(id);
        if (light != null) {
            light.setPosition(burningAt(visual, node));
        }
    }

    /**
     * Where the fire actually is, in the world.
     *
     * <p>The same point the trail is emitted from, and it has to be: a light at
     * the unit's feet while the flame is at the head of the arrow lights the floor
     * behind what is burning.
     */
    private static Vector3f burningAt(Visuals.UnitVisual visual, Node node) {
        return node.localToWorld(new Vector3f(visual.effectForward, visual.yOffset, 0f), null);
    }

    /**
     * It is gone: take back what it was given.
     *
     * <p>Called whether it landed or merely walked out of the light, because the
     * client cannot always tell and the pool must not care. What it landed on —
     * and whether that is worth a burst — is {@link #landed}.
     */
    void gone(int id) {
        var trail = flying.remove(id);
        if (trail != null) {
            trail.emitter().removeFromParent();
            trail.emitter().killAllParticles();
            spare.computeIfAbsent(trail.recipe(), n -> new ArrayList<>()).add(trail.emitter());
        }
        var light = lit.remove(id);
        if (light != null) {
            douse(light);
        }
    }

    /** It arrived: a burst of sparks and a flash where it struck. */
    void landed(String recipeName, Vector3f at, Vector3f camera) {
        var recipe = visuals.effectNamed(recipeName);
        if (recipe == null || recipe.hasLayers() || !recipe.has(IMPACT_BURST)
                || recipe.burstParticles <= 0
                || tooFarOff(at, camera) || burning.size() >= bursts) {
            return;
        }
        var emitter = burstEmitter(recipe);
        emitter.setLocalTranslation(at.clone());
        root.attachChild(emitter);
        emitter.emitAllParticles();

        var light = recipe.lightPower > 0f ? takeLight() : null;
        var flash = toColour(recipe.lightColour, 1f).mult(recipe.lightPower * 2f);
        if (light != null) {
            light.setColor(flash);
            light.setRadius(Math.max(0.001f, recipe.lightRadius * 1.6f));
            light.setPosition(at.clone());
        }
        burning.add(new Burst(emitter, light, flash, recipe.burstSeconds));
    }

    /** Let the bursts die down. Nothing else here changes with time. */
    void update(float tpf) {
        var done = burning.iterator();
        while (done.hasNext()) {
            var burst = done.next();
            burst.left -= tpf;
            if (burst.light != null) {
                // Fading rather than switching off: a flash that stops reads as a
                // light being unplugged, which is not what fire does. Taken as a
                // fraction of what it flashed at, never of what it is now, or the
                // fade would compound itself a frame at a time.
                burst.light.setColor(burst.flash.mult(Math.max(0f, burst.left / burst.seconds)));
            }
            if (burst.left > 0f) {
                continue;
            }
            burst.emitter.removeFromParent();
            burst.emitter.killAllParticles();
            if (burst.light != null) {
                douse(burst.light);
            }
            done.remove();
        }
    }

    /** Everything goes back in the box — a new world gets a clean one. */
    void clear() {
        for (var id : List.copyOf(flying.keySet())) {
            gone(id);
        }
        for (var burst : List.copyOf(burning)) {
            burst.emitter.removeFromParent();
            burst.emitter.killAllParticles();
            if (burst.light != null) {
                douse(burst.light);
            }
        }
        burning.clear();
        lit.clear();
        pool.clear();
    }

    private boolean tooFarOff(Vector3f at, Vector3f camera) {
        return camera != null && lightDistance > 0f
                && at.distanceSquared(camera) > lightDistance * lightDistance;
    }

    private PointLight takeLight() {
        return pool.take();
    }

    private void dress(PointLight light, java.awt.Color colour, float power, float radius) {
        light.setColor(toColour(colour, power));
        light.setRadius(Math.max(0.001f, radius));
    }

    private void douse(PointLight light) {
        pool.give(light);
    }

    private ParticleEmitter borrow(String recipeName, Visuals.EffectVisual recipe) {
        var pool = spare.computeIfAbsent(recipeName, n -> new ArrayList<>());
        if (!pool.isEmpty()) {
            return pool.remove(pool.size() - 1);
        }
        long alight = flying.values().stream().filter(t -> t.recipe().equals(recipeName)).count();
        return alight >= perRecipe ? null : trailEmitter(recipe);
    }

    private ParticleEmitter trailEmitter(Visuals.EffectVisual recipe) {
        var emitter = emitter("trail", recipe, recipe.particles, recipe.particleSize,
                recipe.particleLife, recipe.spread);
        // Straight out and slowing: sparks left behind rather than thrown forward,
        // because the thing they came off is already moving and they are not.
        emitter.setParticlesPerSec(recipe.particles / Math.max(0.05f, recipe.particleLife));
        return emitter;
    }

    private ParticleEmitter burstEmitter(Visuals.EffectVisual recipe) {
        var emitter = emitter("burst", recipe, recipe.burstParticles, recipe.burstSize,
                recipe.burstSeconds, recipe.spread * 3f + recipe.burstSize * 4f);
        emitter.setParticlesPerSec(0f); // all at once, then nothing
        return emitter;
    }

    private ParticleEmitter emitter(String name, Visuals.EffectVisual recipe, int count,
            float size, float life, float spread) {
        var emitter = new ParticleEmitter(name, ParticleMesh.Type.Triangle, Math.max(1, count));
        var material = new Material(assets, "Common/MatDefs/Misc/Particle.j3md");
        material.setTexture("Texture", spark());
        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.AlphaAdditive);
        emitter.setMaterial(material);
        emitter.setImagesX(1);
        emitter.setImagesY(1);
        emitter.setStartColor(toColour(recipe.colour, 1f));
        emitter.setEndColor(toColour(recipe.fade == null ? recipe.colour : recipe.fade, 0f));
        emitter.setStartSize(size);
        emitter.setEndSize(size * 0.15f);
        emitter.setLowLife(life * 0.6f);
        emitter.setHighLife(life);
        emitter.setGravity(0f, 0f, 0f);
        emitter.setShape(new EmitterSphereShape(Vector3f.ZERO, Math.max(0.01f, size * 0.3f)));
        emitter.getParticleInfluencer().setVelocityVariation(1f);
        emitter.getParticleInfluencer().setInitialVelocity(new Vector3f(0f, spread * 0.25f, 0f));
        emitter.setQueueBucket(RenderQueue.Bucket.Transparent);
        emitter.setShadowMode(RenderQueue.ShadowMode.Off);
        emitter.setEnabled(true);
        return emitter;
    }

    /**
     * A soft round spark, drawn rather than shipped.
     *
     * <p>One texture for every effect in the game: the colour is the emitter's,
     * so what this has to be is a shape — bright in the middle, nothing at the
     * edge, and no seam where it ends. Squaring the falloff is what keeps it from
     * reading as a disc.
     */
    private Texture spark() {
        if (spark != null) {
            return spark;
        }
        var pixels = ByteBuffer.allocateDirect(SPARK_PIXELS * SPARK_PIXELS * 4);
        float middle = (SPARK_PIXELS - 1) / 2f;
        for (int y = 0; y < SPARK_PIXELS; y++) {
            for (int x = 0; x < SPARK_PIXELS; x++) {
                float away = (float) Math.hypot(x - middle, y - middle) / middle;
                float strength = Math.max(0f, 1f - away);
                byte value = (byte) Math.round(255f * strength * strength);
                pixels.put((byte) 255).put((byte) 255).put((byte) 255).put(value);
            }
        }
        pixels.flip();
        spark = new Texture2D(new Image(Image.Format.RGBA8, SPARK_PIXELS, SPARK_PIXELS, pixels,
                com.jme3.texture.image.ColorSpace.sRGB));
        return spark;
    }

    private static ColorRGBA toColour(java.awt.Color colour, float alpha) {
        var awt = colour == null ? java.awt.Color.WHITE : colour;
        return new ColorRGBA(awt.getRed() / 255f, awt.getGreen() / 255f, awt.getBlue() / 255f,
                alpha);
    }
}
