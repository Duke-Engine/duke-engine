package uz.duke.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.List;

/**
 * What a recipe does to the thing that wears it, as opposed to what it lets out into the air:
 * the body a projectile with no model is drawn as, and the pieces of a creature it lights from
 * inside. Everything let out into the air is {@link LayeredEffects}.
 *
 * <p><b>Drawing only.</b> Nothing here is asked before anything is decided: the simulation says
 * where a thing is, and this hangs a look on it.
 *
 * <p>It also owns the lights. Dynamic lights are the expensive kind — the terrain shader takes
 * four and the creatures' takes what jME gives it — so there is one pool for the whole client,
 * and everything that burns borrows from it.
 */
final class ProjectileEffects {

    private final AssetManager assets;
    private final Visuals visuals;

    /**
     * The lights, made once and moved about.
     *
     * <p>Free ones are kept in a list of their own rather than recognised by
     * looking at them. A light's own state cannot say whether it is in use:
     * jME reads a radius of zero as <em>infinite</em>, and a black light is still
     * a light the renderer has to consider.
     */
    private final LightPool pool;

    ProjectileEffects(AssetManager assets, Node root, Visuals visuals, int maxLights) {
        this.assets = assets;
        this.visuals = visuals;
        this.pool = new LightPool(root, maxLights);
    }

    /**
     * The lights, for anything that burns to borrow from.
     *
     * <p>One pool for the whole client: two pools of four are eight lights to the
     * renderer and four to each pool, which is how a budget is quietly doubled.
     */
    LightPool lights() {
        return pool;
    }

    /** Every light the pool owns, lit or not, for the terrain shader to read. */
    List<PointLight> allLights() {
        return pool.all();
    }

    /**
     * The body of a thing that has no model and is drawn by its layers, or {@code null} if it
     * has none of either.
     *
     * <p>Empty rather than null when its recipe has layers, or the client would give it a
     * coloured capsule as well — and a fireball drawn as a capsule with a gun barrel on it is
     * worse than no fireball at all.
     */
    Spatial bodyFor(Visuals.UnitVisual visual) {
        var recipe = visuals.effectNamed(visual == null ? null : visual.effect);
        return recipe != null && recipe.hasLayers() ? new Node("drawn by its layers") : null;
    }

    /**
     * Light the named pieces of a model from inside — a skeleton's eye sockets,
     * a rune, the coals in a brazier.
     *
     * <p>The cheapest thing a recipe can do by a distance: one material per piece and no light
     * at all. Which is the point. A torch on every skeleton in a room would be over the light
     * budget before the second one; a pair of burning eyes on every skeleton in the game costs
     * one draw call each and reads across a dark room better than a light would.
     *
     * <p>Unshaded on purpose: a glowing thing is glowing, not lit. Lighting it
     * would make it dimmer in the dark, which is the opposite of the idea.
     */
    void lightThePartsOf(Spatial model, String recipeName) {
        var recipe = visuals.effectNamed(recipeName);
        if (model == null || recipe == null || recipe.glowParts.isEmpty()) {
            return;
        }
        model.depthFirstTraversal(spatial -> {
            if (!(spatial instanceof Geometry geometry) || !named(geometry, recipe.glowParts)) {
                return;
            }
            var material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
            material.setColor("Color", toColour(recipe.glowColour));
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

    /** Every light goes back in the box — a new world gets a clean one. */
    void clear() {
        pool.clear();
    }

    private static ColorRGBA toColour(java.awt.Color colour) {
        return new ColorRGBA(colour.getRed() / 255f, colour.getGreen() / 255f, colour.getBlue() / 255f, 1f);
    }
}
