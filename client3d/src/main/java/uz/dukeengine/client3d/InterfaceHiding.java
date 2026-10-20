package uz.dukeengine.client3d;

import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The engine's own interface put away while the game says so — a film's look through an intro (E104): every child of
 * the nodes it is handed culled for the frame but those kept (the game's own canvas), and each given back the next
 * frame as it was, so what sets its own showing — a label shown and hidden each frame — goes on doing so.
 */
final class InterfaceHiding {

    private final Map<Spatial, Spatial.CullHint> hidden = new IdentityHashMap<>();

    /** Everything put away last frame, as it was. */
    void showAgain() {
        hidden.forEach(Spatial::setCullHint);
        hidden.clear();
    }

    /** Every child of {@code nodes} but those {@code kept}, culled for this frame. */
    void hide(Set<Spatial> kept, Node... nodes) {
        for (var node : nodes) {
            if (node == null) {
                continue;
            }
            for (var child : node.getChildren()) {
                if (kept.contains(child) || hidden.containsKey(child)) {
                    continue;
                }
                hidden.put(child, child.getLocalCullHint());
                child.setCullHint(Spatial.CullHint.Always);
            }
        }
    }
}
