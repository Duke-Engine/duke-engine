package uz.dukeengine.client3d;

import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.function.BiFunction;

/**
 * The mark a placement drag puts on the ground, as the reference's {@code W3DInGameUI::drawPlaceAngle} does with its
 * {@code Locater01} and {@code Locater02}: from the press until it is let go, the anchor model stands where the press
 * went down while the cursor has not been dragged away from it, and once it has, the arrow does, turned along the drag
 * as the thing being placed is. Models are the game's ({@link Visuals.LocaterLook}); one it names none of is not drawn.
 */
final class PlacementLocater {

    private final Node node = new Node("placement locater");
    private final Spatial anchor;
    private final Spatial arrow;

    /** {@code anchor} and {@code arrow}, either null where the game named no model or it would not load. */
    PlacementLocater(Node parent, Spatial anchor, Spatial arrow) {
        this.anchor = anchor;
        this.arrow = arrow;
        if (anchor != null) {
            node.attachChild(anchor);
        }
        if (arrow != null) {
            node.attachChild(arrow);
        }
        parent.attachChild(node);
        hide();
    }

    /** Laid for the drag as it stands: where its press went down, turned the way the thing facing now is. */
    void show(PlacementDrag drag, BiFunction<Float, Float, Float> floorAt) {
        var at = drag.pressed() ? drag.where() : null;
        if (at == null) {
            hide();
            return;
        }
        set(anchor, !drag.dragging());
        set(arrow, drag.dragging());
        node.setLocalTranslation(at.x(), floorAt.apply(at.x(), at.y()), at.y());
        node.setLocalRotation(PlacementDrag.turnedTo(drag.facing()));
    }

    /** Which of the two lies on the ground now — {@code anchor} or {@code arrow} — or null for neither. */
    String showing() {
        return shown(anchor) ? "anchor" : shown(arrow) ? "arrow" : null;
    }

    void remove() {
        node.removeFromParent();
    }

    private void hide() {
        set(anchor, false);
        set(arrow, false);
    }

    private static void set(Spatial model, boolean on) {
        if (model != null) {
            model.setCullHint(on ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        }
    }

    private static boolean shown(Spatial model) {
        return model != null && model.getCullHint() != Spatial.CullHint.Always;
    }
}
