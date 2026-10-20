package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The engine's interface put away while the game says so, the game's own canvas still drawn (E104), and what hid or
 * showed itself meanwhile left as it was (E105).
 */
class InterfaceHidingTest {

    @Test
    void hiddenOnlyTheGamesCanvasIsDrawnAndAMenuHiddenMeanwhileStaysHidden() {
        var gui = new Node("gui");
        var panel = new Node("panel");
        var menu = new Node("menu");
        var canvas = new Node("the game's canvas");
        gui.attachChild(panel);
        gui.attachChild(menu);
        gui.attachChild(canvas);
        var marks = new Node("order marks");
        var view = new ViewPort("gui", new Camera(10, 10));
        view.attachScene(gui);
        var hiding = new InterfaceHiding();

        hiding.apply(true, view, gui, List.of(canvas), marks);
        assertEquals(List.of(canvas), view.getScenes(), "no panel: the game's letterbox and box alone");
        assertEquals(Spatial.CullHint.Always, marks.getLocalCullHint());

        menu.setCullHint(Spatial.CullHint.Always); // the match adopted: the menu put away while the interface is
        hiding.apply(false, view, gui, List.of(canvas), marks);
        assertEquals(List.of(gui), view.getScenes(), "the panel as before");
        assertEquals(Spatial.CullHint.Always, menu.getLocalCullHint(), "and the menu as it was left: hidden");
        assertEquals(Spatial.CullHint.Inherit, panel.getLocalCullHint(), "no piece's own showing touched");
        assertEquals(Spatial.CullHint.Inherit, marks.getLocalCullHint());
    }
}
