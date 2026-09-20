package uz.duke.client3d;

/**
 * Where on the screen a block of the hero's bar stands when it stands on its own.
 *
 * <p>An edge or a corner of the window, which is what a HUD is hung from: a thing pinned to the middle of the
 * screen moves when the window is resized and a thing pinned to a corner does not.
 */
public enum PanelAnchor {
    TOP_LEFT,
    TOP,
    TOP_RIGHT,
    LEFT,
    MIDDLE,
    RIGHT,
    BOTTOM_LEFT,
    BOTTOM,
    BOTTOM_RIGHT
}
