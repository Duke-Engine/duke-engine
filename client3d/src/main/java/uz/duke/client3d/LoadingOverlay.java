package uz.duke.client3d;

import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;

/**
 * What the player looks at while the game reads its art: a title, a bar, and the
 * name of the file being read.
 *
 * <p>Same materials and the same bitmap text as the menus, and for the
 * same reason — this appears before anything else has been drawn, so whatever it
 * needs is whatever has to be ready first.
 *
 * <p>The bar is one quad scaled along x rather than a quad rebuilt per frame. A
 * loading screen that allocates on every frame of loading is a joke at its own
 * expense.
 */
final class LoadingOverlay {

    private static final float BAR_WIDTH = 420f;
    private static final float BAR_HEIGHT = 10f;
    private static final ColorRGBA FILL = new ColorRGBA(0.85f, 0.72f, 0.35f, 1f);

    private final Node root = new Node("loading");
    private final BitmapText title;
    private final BitmapText detail;
    private final Geometry bar;
    private final int screenWidth;

    LoadingOverlay(BitmapFont font, com.jme3.asset.AssetManager assets, Node guiNode,
            int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;

        var dim = new Geometry("loading-dim", new Quad(screenWidth, screenHeight));
        dim.setMaterial(flat(assets, new ColorRGBA(0.03f, 0.04f, 0.06f, 1f)));
        dim.setQueueBucket(RenderQueue.Bucket.Gui);
        root.attachChild(dim);

        float barY = screenHeight * 0.42f;
        float barX = (screenWidth - BAR_WIDTH) / 2f;

        var trough = new Geometry("loading-trough", new Quad(BAR_WIDTH, BAR_HEIGHT));
        trough.setMaterial(flat(assets, new ColorRGBA(1f, 1f, 1f, 0.12f)));
        trough.setQueueBucket(RenderQueue.Bucket.Gui);
        trough.setLocalTranslation(barX, barY, 0);
        root.attachChild(trough);

        bar = new Geometry("loading-bar", new Quad(BAR_WIDTH, BAR_HEIGHT));
        bar.setMaterial(flat(assets, FILL));
        bar.setQueueBucket(RenderQueue.Bucket.Gui);
        bar.setLocalTranslation(barX, barY, 1);
        root.attachChild(bar);

        title = new BitmapText(font);
        title.setSize(font.getCharSet().getRenderedSize() * 1.6f);
        title.setColor(new ColorRGBA(0.92f, 0.88f, 0.78f, 1f));
        root.attachChild(title);

        detail = new BitmapText(font);
        detail.setColor(new ColorRGBA(0.62f, 0.60f, 0.56f, 1f));
        detail.setLocalTranslation(barX, barY - 12f, 0);
        root.attachChild(detail);

        title.setText("Loading");
        title.setLocalTranslation((screenWidth - title.getLineWidth()) / 2f,
                barY + BAR_HEIGHT + title.getLineHeight() + 24f, 0);

        guiNode.attachChild(root);
        hide();
    }

    private static Material flat(com.jme3.asset.AssetManager assets, ColorRGBA colour) {
        var material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", colour);
        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        return material;
    }

    /** Put it up, headed by whatever the game calls itself. */
    void show(String heading) {
        title.setText(heading == null || heading.isBlank() ? "Loading" : heading);
        title.setLocalTranslation((screenWidth - title.getLineWidth()) / 2f,
                title.getLocalTranslation().y, 0);
        progress(0f, "");
        root.setCullHint(com.jme3.scene.Spatial.CullHint.Never);
    }

    void hide() {
        root.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
    }

    boolean isShowing() {
        return root.getCullHint() != com.jme3.scene.Spatial.CullHint.Always;
    }

    /**
     * How far along, from nothing to all of it, and what is being read.
     *
     * <p>The bar never reads as empty once there is progress at all: a scale of
     * zero is a quad of no width, which is not "starting" on screen, it is
     * "broken".
     */
    void progress(float done, String what) {
        float fraction = Math.clamp(done, 0f, 1f);
        bar.setLocalScale(Math.max(fraction, 0.004f), 1f, 1f);
        detail.setText(what == null ? "" : what);
    }
}
