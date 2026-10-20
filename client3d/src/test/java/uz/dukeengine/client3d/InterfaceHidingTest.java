package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The engine's interface put away while the game says so, the game's own canvas kept, all given back after (E104). */
class InterfaceHidingTest {

    @Test
    void hiddenItDrawsNoPanelButTheGamesCanvasAndShownItIsAsItWas() {
        var gui = new Node("gui");
        var panel = new Node("panel");
        var label = new Node("label");
        label.setCullHint(Spatial.CullHint.Always); // a label of its own, shown only when it has something to say
        var canvas = new Node("the game's canvas");
        gui.attachChild(panel);
        gui.attachChild(label);
        gui.attachChild(canvas);
        var hiding = new InterfaceHiding();

        hiding.hide(Set.of(canvas), gui);
        assertEquals(Spatial.CullHint.Always, panel.getLocalCullHint(), "no panel");
        assertEquals(Spatial.CullHint.Inherit, canvas.getLocalCullHint(), "the game's letterbox and box still drawn");

        hiding.showAgain();
        assertEquals(Spatial.CullHint.Inherit, panel.getLocalCullHint(), "the panel as before");
        assertEquals(Spatial.CullHint.Always, label.getLocalCullHint(), "and the label as it was, hidden by itself");
    }
}
