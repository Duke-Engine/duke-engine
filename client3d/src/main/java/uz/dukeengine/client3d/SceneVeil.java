package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;
import uz.dukeengine.game.GameCamera;

/**
 * What a scene puts over the world and under the interface — see {@link GameCamera.Screen}: a fade of one colour over
 * the whole window and black bars sliding in at the top and bottom.
 */
final class SceneVeil {

    /** Under every other picture on the interface, over the world. */
    private static final float DEPTH = -1f;

    private final Node node = new Node("scene veil");
    private final Geometry fade;
    private final Geometry top;
    private final Geometry bottom;

    SceneVeil(AssetManager assets) {
        fade = quad(assets, "fade");
        top = quad(assets, "letterbox top");
        bottom = quad(assets, "letterbox bottom");
        top.getMaterial().setColor("Color", ColorRGBA.Black);
        bottom.getMaterial().setColor("Color", ColorRGBA.Black);
        node.setCullHint(Spatial.CullHint.Always);
    }

    Node node() {
        return node;
    }

    void show(GameCamera.Screen screen, int wide, int tall) {
        boolean any = screen.fade() > 0f || screen.letterbox() > 0f;
        node.setCullHint(any ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        if (!any) {
            return;
        }
        int rgb = screen.colour();
        fade.getMaterial().setColor("Color", new ColorRGBA(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f,
                (rgb & 0xFF) / 255f, screen.fade()));
        fade.setLocalScale(wide, tall, 1f);
        fade.setLocalTranslation(0f, 0f, DEPTH);
        fade.setCullHint(screen.fade() > 0f ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        float bar = tall * GameCamera.Screen.LETTERBOX_SHARE * screen.letterbox();
        top.setLocalScale(wide, bar, 1f);
        top.setLocalTranslation(0f, tall - bar, DEPTH + 0.01f);
        bottom.setLocalScale(wide, bar, 1f);
        bottom.setLocalTranslation(0f, 0f, DEPTH + 0.01f);
    }

    private Geometry quad(AssetManager assets, String name) {
        var geometry = new Geometry(name, new Quad(1f, 1f));
        var material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        geometry.setMaterial(material);
        geometry.setQueueBucket(RenderQueue.Bucket.Gui);
        node.attachChild(geometry);
        return geometry;
    }
}
