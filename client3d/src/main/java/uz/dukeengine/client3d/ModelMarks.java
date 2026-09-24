package uz.dukeengine.client3d;

import com.jme3.anim.AnimComposer;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Function;
import uz.dukeengine.core.GameConstants;

/**
 * Orders answered with the game's own model — see {@link OrderMark#model}: the reference's move hints
 * ({@code InGameUI::createMoveHint}, {@code W3DInGameUI::drawMoveHints}). A mark is laid on the ground where the order
 * went, its clip played once from its first frame, and taken away when its frames are up. A new order from the same
 * selection moves that selection's mark there and starts it again, rather than laying a second one.
 */
final class ModelMarks {

    /** The reference's {@code MAX_MOVE_HINTS}: past this many, the oldest mark is taken away. */
    static final int MOST = 256;

    private record Mark(Spatial model, float bornAt) {
    }

    private final Node parent;
    private final Function<String, Spatial> load;
    /** By the selection that was ordered, oldest first. */
    private final LinkedHashMap<List<Integer>, Mark> marks = new LinkedHashMap<>();

    ModelMarks(Node parent, Function<String, Spatial> load) {
        this.parent = parent;
        this.load = load;
    }

    /** An order from {@code selection} to the point {@code x}, {@code y}, whose ground stands at {@code ground}. */
    void add(List<Integer> selection, float x, float y, float ground, OrderMark look, float now) {
        var key = List.copyOf(new TreeSet<>(selection)); // one selection, however it was chosen
        var old = marks.remove(key);
        var model = old != null ? old.model() : load.apply(look.model());
        if (model == null) {
            return;
        }
        if (old == null) {
            while (marks.size() >= MOST) {
                var oldest = marks.keySet().iterator().next();
                marks.remove(oldest).model().removeFromParent();
            }
            parent.attachChild(model);
        }
        model.setLocalTranslation(x, ground, y);
        var composer = AnimationLibrary.findControl(model, AnimComposer.class);
        if (composer != null && look.clip() != null && composer.getAnimClip(look.clip()) != null) {
            composer.setCurrentAction(look.clip(), AnimComposer.DEFAULT_LAYER, false).setSpeed(1);
            composer.setTime(AnimComposer.DEFAULT_LAYER, 0);
        }
        marks.put(key, new Mark(model, now));
    }

    /** The marks whose frames are up, taken away. */
    void update(float now, OrderMark look) {
        var each = marks.values().iterator();
        while (each.hasNext()) {
            var mark = each.next();
            // In frames, with a thousandth of one to spare: a float clock read at the 40th frame reads a hair short.
            if ((now - mark.bornAt()) * GameConstants.LOGICFRAMES_PER_SECOND >= look.frames() - 0.001f) {
                mark.model().removeFromParent();
                each.remove();
            }
        }
    }

    int count() {
        return marks.size();
    }

    /** The mark {@code selection}'s last order left, or null — for a test. */
    Spatial markOf(List<Integer> selection) {
        var mark = marks.get(List.copyOf(new TreeSet<>(selection)));
        return mark == null ? null : mark.model();
    }

    /** Every mark taken away — a new world has no orders outstanding in it. */
    void clear() {
        marks.values().forEach(mark -> mark.model().removeFromParent());
        marks.clear();
    }
}
