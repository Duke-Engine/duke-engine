package uz.dukeengine.game.view;

/**
 * How a thing's turrets stand, as its game's {@code Turret} has them — the reference's {@code
 * AIUpdateInterface::getTurretRotAndPitch}: each turned from the thing's facing, the way a thing turns, and its gun
 * pitched up from level, in radians; the second a battleship's.
 */
public record Turrets(float turn, float pitch, float altTurn, float altPitch) {

    /** A thing with no turret, or one standing straight ahead and level. */
    public static final Turrets NONE = new Turrets(0f, 0f, 0f, 0f);

    /** How far turret {@code slot} — 0, or 1 for the second — stands turned. */
    public float turn(int slot) {
        return slot == 0 ? turn : altTurn;
    }

    /** How far turret {@code slot}'s gun stands pitched up. */
    public float pitch(int slot) {
        return slot == 0 ? pitch : altPitch;
    }
}
