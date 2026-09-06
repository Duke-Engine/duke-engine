package uz.duke.client3d;

import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import java.util.ArrayList;
import java.util.List;

/**
 * A minimal in-game menu for the 3D client: a dimmed backdrop, a title, and a
 * column of clickable text buttons — no external GUI library, just bitmap text
 * and screen-space hit testing. Drives the main menu and the pause menu.
 */
final class MenuOverlay {

    /** One clickable entry. */
    record Item(String label, Runnable action) {
    }

    private static final ColorRGBA IDLE = new ColorRGBA(0.85f, 0.85f, 0.85f, 1f);
    private static final ColorRGBA HOVER = new ColorRGBA(1f, 0.9f, 0.3f, 1f);

    private final Node root = new Node("menu");
    private final BitmapFont font;
    private final int screenWidth;
    private final int screenHeight;
    private final List<BitmapText> buttons = new ArrayList<>();
    private final List<Item> items = new ArrayList<>();

    MenuOverlay(BitmapFont font, com.jme3.asset.AssetManager assets, Node guiNode,
            int screenWidth, int screenHeight) {
        this.font = font;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;

        var dim = new Geometry("menu-dim", new Quad(screenWidth, screenHeight));
        var material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", new ColorRGBA(0f, 0f, 0f, 0.65f));
        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        dim.setMaterial(material);
        dim.setQueueBucket(RenderQueue.Bucket.Gui);
        root.attachChild(dim);

        guiNode.attachChild(root);
        hide();
    }

    /** Replace the menu's contents and show it. */
    void show(String title, String subtitle, List<Item> newItems) {
        // rebuild: menus are tiny, clarity beats caching
        for (var button : buttons) {
            button.removeFromParent();
        }
        buttons.clear();
        items.clear();
        root.detachChildNamed("menu-title");
        root.detachChildNamed("menu-subtitle");

        var titleText = new BitmapText(font);
        titleText.setName("menu-title");
        titleText.setSize(font.getCharSet().getRenderedSize() * 3.5f);
        titleText.setText(title);
        titleText.setColor(new ColorRGBA(1f, 0.9f, 0.35f, 1f));
        titleText.setLocalTranslation((screenWidth - titleText.getLineWidth()) / 2f,
                screenHeight * 0.78f, 1);
        root.attachChild(titleText);

        if (subtitle != null && !subtitle.isBlank()) {
            var subtitleText = new BitmapText(font);
            subtitleText.setName("menu-subtitle");
            subtitleText.setText(subtitle);
            subtitleText.setColor(new ColorRGBA(0.8f, 0.8f, 0.8f, 1f));
            subtitleText.setLocalTranslation((screenWidth - subtitleText.getLineWidth()) / 2f,
                    screenHeight * 0.78f - titleText.getLineHeight(), 1);
            root.attachChild(subtitleText);
        }

        float y = screenHeight * 0.52f;
        for (var item : newItems) {
            var button = new BitmapText(font);
            button.setSize(font.getCharSet().getRenderedSize() * 1.8f);
            button.setText(item.label());
            button.setColor(IDLE);
            button.setLocalTranslation((screenWidth - button.getLineWidth()) / 2f, y, 1);
            root.attachChild(button);
            buttons.add(button);
            items.add(item);
            y -= button.getLineHeight() * 1.6f;
        }
        root.setCullHint(com.jme3.scene.Spatial.CullHint.Never);
    }

    void hide() {
        root.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
    }

    /** Remove this overlay from the scene (used when the screen is resized). */
    void destroy() {
        root.removeFromParent();
    }

    boolean isVisible() {
        return root.getCullHint() != com.jme3.scene.Spatial.CullHint.Always;
    }

    /** Highlight the button under the cursor. Call each frame while visible. */
    void updateHover(Vector2f cursor) {
        for (int i = 0; i < buttons.size(); i++) {
            buttons.get(i).setColor(hit(buttons.get(i), cursor) ? HOVER : IDLE);
        }
    }

    /** Run the clicked button's action. Returns true if the click hit a button. */
    boolean click(Vector2f cursor) {
        if (!isVisible()) {
            return false;
        }
        for (int i = 0; i < buttons.size(); i++) {
            if (hit(buttons.get(i), cursor)) {
                items.get(i).action().run();
                return true;
            }
        }
        return true; // a visible menu swallows every click
    }

    private static boolean hit(BitmapText button, Vector2f cursor) {
        var pos = button.getLocalTranslation();
        float pad = 8f;
        return cursor.x >= pos.x - pad && cursor.x <= pos.x + button.getLineWidth() + pad
                && cursor.y <= pos.y + pad && cursor.y >= pos.y - button.getLineHeight() - pad;
    }
}
