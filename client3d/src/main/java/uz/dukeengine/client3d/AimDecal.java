package uz.dukeengine.client3d;

/**
 * A picture laid on the ground under an armed aim, in place of the ring drawn round it: the reference's {@code
 * RadiusCursorTemplates} ({@code InGameUI.ini}) — a power's own reticle, as wide as twice the aim's radius, following
 * the ground, its opacity throbbing between {@code opacityMin} and {@code opacityMax} once every {@code throbFrames}
 * of the game's frames ({@code OpacityThrobTime}), painted {@code colour} (packed RGB; white leaves it as drawn).
 * Seen only by the player aiming — see {@link Duke3D#aim(uz.dukeengine.game.view.CommandButton, float, String,
 * AimDecal, java.util.function.Consumer)}.
 *
 * @param picture the picture's path, whole from the resource root
 */
public record AimDecal(String picture, float opacityMin, float opacityMax, int throbFrames, int colour) {

    public AimDecal {
        throbFrames = Math.max(1, throbFrames);
    }

    /** How opaque it is in the game's frame {@code frame}: {@code RadiusDecal::update}'s sine, low to high and back. */
    public float opacity(int frame) {
        double turn = 2 * Math.PI * Math.floorMod(frame, throbFrames) / throbFrames;
        return opacityMin + (float) (0.5 * (Math.sin(turn) + 1)) * (opacityMax - opacityMin);
    }
}
