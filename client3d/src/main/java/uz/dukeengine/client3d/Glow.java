package uz.dukeengine.client3d;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.asset.AssetManager;

/**
 * Flat marks on the floor that have to be seen in a dark room.
 *
 * <p>Everything the client draws on the ground to answer the player — the flash
 * under a click, the ring round a skill — is the same kind of thing and wants the
 * same treatment, so it is written once here. Three decisions, each of which is
 * wrong in an obvious way if taken differently:
 *
 * <ul>
 *   <li><b>Added rather than blended.</b> A mark that is mixed with the floor
 *       takes the floor's darkness with it, and a dungeon floor is nearly black:
 *       the thing meant to be read at a glance is the dimmest thing on screen.
 *       Added, it carries its own light and reads through torchlight and fog
 *       alike — and it still goes out cleanly, because the alpha it is multiplied
 *       by is its own.
 *   <li><b>Brighter than its colour.</b> Adding a colour at its own strength over
 *       a lit floor washes it out, so the number that comes out of the file is
 *       multiplied past 1 on the way in. That is what makes the colour still look
 *       like a colour once it is competing with a torch.
 *   <li><b>No depth writing, but depth testing.</b> A translucent thing has no
 *       business hiding what is drawn after it; and a ring on the floor of the
 *       next room has every business being hidden by the wall in front of it.
 * </ul>
 */
final class Glow {

    private Glow() {
    }

    /** A material for something flat, glowing and on the floor. */
    static Material material(AssetManager assets) {
        var material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", ColorRGBA.White);
        var state = material.getAdditionalRenderState();
        state.setBlendMode(RenderState.BlendMode.AlphaAdditive);
        state.setDepthWrite(false);
        // Which way a flat shape was wound is not worth caring about.
        state.setFaceCullMode(RenderState.FaceCullMode.Off);
        return material;
    }

    /** Put a geometry where the translucent things are drawn. */
    static Geometry inTheGlow(Geometry geometry, Material material) {
        geometry.setMaterial(material);
        geometry.setQueueBucket(RenderQueue.Bucket.Transparent);
        return geometry;
    }

    /** A packed RGB from the game's file, turned into something worth adding. */
    static ColorRGBA colour(int packed, float brightness, float alpha) {
        float scale = brightness / 255f;
        return new ColorRGBA(
                ((packed >> 16) & 0xFF) * scale,
                ((packed >> 8) & 0xFF) * scale,
                (packed & 0xFF) * scale,
                alpha);
    }
}
