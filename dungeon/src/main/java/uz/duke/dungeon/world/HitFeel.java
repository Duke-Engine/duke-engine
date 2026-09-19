package uz.duke.dungeon.world;

/**
 * How a blow feels rather than what it costs: every knock of the camera at once, and the flash a
 * creature gives when it is hit.
 *
 * @param shakeScale       every knock of the camera against what its effect asked for; 0 is none
 * @param hitFlashColour   what a creature that is hit flashes towards
 * @param hitFlashSeconds  how long the whole flash is; 0 is none
 * @param hitFlashStrength how far towards its colour, 0 to 1; 0 is none
 * @param strikeWithin     how near a shot's end a blow must land, that frame, for the shot to have
 *     struck
 */
public record HitFeel(float shakeScale, int hitFlashColour, float hitFlashSeconds, float hitFlashStrength,
        float strikeWithin) {

    /** What a block leaves out. */
    public static final HitFeel DEFAULTS = new HitFeel(1f, 0xFFFFFF, 0f, 0f, 30f);
}
