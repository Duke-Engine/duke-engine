package uz.dukeengine.client3d;

import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The words the game puts over a thing — {@code GameObject.setCaption}, the reference's {@code
 * Drawable::setCaptionText} — drawn flat on the interface, centred over the point of the world given for each. One
 * text per thing, handed a new string only when it changes, and taken down the frame its words are.
 */
final class Captions {

    /** One thing's words, and the point of the world they are centred over. */
    record Line(int id, String text, Vector3f at) {
    }

    private final BitmapFont font;
    private final Node root = new Node("captions");
    private final Map<Integer, BitmapText> drawn = new HashMap<>();

    Captions(BitmapFont font, Node gui) {
        this.font = font;
        gui.attachChild(root);
    }

    /** Every caption as these lines say: new ones put up, changed ones reworded, any not in them taken down. */
    void update(Camera camera, List<Line> lines) {
        var kept = new java.util.HashSet<Integer>();
        for (var line : lines) {
            var onScreen = camera.getScreenCoordinates(line.at());
            if (onScreen.z > 1f) {
                continue; // behind the camera
            }
            kept.add(line.id());
            var text = drawn.computeIfAbsent(line.id(), _ -> {
                var made = new BitmapText(font);
                root.attachChild(made);
                return made;
            });
            if (!line.text().equals(text.getText())) {
                text.setText(line.text());
            }
            text.setLocalTranslation(onScreen.x - text.getLineWidth() / 2f, onScreen.y + text.getLineHeight(), 1f);
        }
        drawn.entrySet().removeIf(one -> {
            boolean gone = !kept.contains(one.getKey());
            if (gone) {
                one.getValue().removeFromParent();
            }
            return gone;
        });
    }

    /** The text drawn over a thing, or null for none, for a test. */
    BitmapText of(int id) {
        return drawn.get(id);
    }

    /** Take them all down — a new world has nothing captioned in it. */
    void clear() {
        drawn.values().forEach(BitmapText::removeFromParent);
        drawn.clear();
    }
}
