package uz.dukeengine.client3d;

import com.jme3.renderer.ViewPort;
import com.jme3.scene.Spatial;
import java.util.List;

/**
 * The engine's own interface put away while the game says so — a film's look through an intro (E104): the interface's
 * view drawing only what is kept (the game's own canvas) instead of the whole interface, and the order marks in the
 * world culled. No piece of the interface has its own showing touched, so a menu hidden or a bar shown meanwhile is as
 * it was left when the interface comes back (E105).
 */
final class InterfaceHiding {

    private boolean hiding;

    /**
     * Put the interface away, or back, as {@code hide} says: {@code view} drawing {@code kept} alone in place of {@code
     * interfaceRoot}, and {@code marks} culled, while it is away.
     */
    void apply(boolean hide, ViewPort view, Spatial interfaceRoot, List<Spatial> kept, Spatial marks) {
        if (hide == hiding) {
            return;
        }
        hiding = hide;
        if (hide) {
            view.detachScene(interfaceRoot);
            kept.forEach(view::attachScene);
        } else {
            kept.forEach(view::detachScene);
            view.attachScene(interfaceRoot);
        }
        if (marks != null) {
            marks.setCullHint(hide ? Spatial.CullHint.Always : Spatial.CullHint.Inherit);
        }
    }

    boolean isHiding() {
        return hiding;
    }
}
