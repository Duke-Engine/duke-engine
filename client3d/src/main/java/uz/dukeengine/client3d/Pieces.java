package uz.dukeengine.client3d;

import com.jme3.scene.Spatial;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * One thing's model pieces as its states have left them: the reference's {@code HideSubObject} and {@code
 * ShowSubObject} per condition state ({@code W3DModelDraw}). A state changes only the pieces it names — hidden and
 * shown are sticky, not reset per state — so a Humvee's first state hiding its upgrade turret and a later one showing
 * it leave the house colour the first hid still hidden. A piece no state named keeps the file's own visibility.
 *
 * <p>Held for the thing rather than its model, and laid on again when the model is swapped for a damaged one, so a
 * damaged Humvee keeps its turret choice.
 */
final class Pieces {

    private static final int NONE_YET = Integer.MIN_VALUE;

    /** Each named piece, by its bare name in capitals, shown or hidden. */
    private final Map<String, Boolean> shown = new LinkedHashMap<>();
    private int state = NONE_YET;

    /**
     * The state its words now best fit, as an index into the look's states, or -1 for none: where it is not the
     * last one chosen, its hides and shows are laid over what the states before it left.
     *
     * @return whether anything changed, and the model wants them laid on again
     */
    boolean choose(int index, List<Visuals.PieceState> states) {
        if (index == state) {
            return false;
        }
        state = index;
        if (index < 0) {
            return false;
        }
        var chosen = states.get(index);
        chosen.hide().forEach(piece -> shown.put(bare(piece), false));
        chosen.show().forEach(piece -> shown.put(bare(piece), true));
        return true;
    }

    /**
     * Every named piece of {@code model} hidden or shown as its states left it, but those in {@code leaveAlone}: a
     * barrel's muzzle flash, which its barrel shows on the frames it fires and hides the rest of the time.
     */
    void applyTo(Spatial model, Set<Spatial> leaveAlone) {
        if (model == null || shown.isEmpty()) {
            return;
        }
        model.depthFirstTraversal(piece -> {
            if (piece.getName() == null || leaveAlone.contains(piece)) {
                return;
            }
            var visible = shown.get(bare(piece.getName()));
            if (visible != null) {
                piece.setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
            }
        });
    }

    /**
     * A piece's name without the container it may be qualified by, in capitals: {@code TURRETUP01} for {@code
     * AVHUMMER.TurretUp01}, so a model and a state may each write it either way — ignoring case, as the reference
     * compares its pieces'.
     */
    static String bare(String name) {
        return name.substring(name.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
    }
}
