package uz.dukeengine.rts.module;

/**
 * A module that turns its thing's turret — the game's, as the reference's {@code TurretAI} turns one toward its
 * target: how far the turret stands turned from its thing's facing, for what sits on it to turn with it (a hold's
 * riders seated on its turret, {@code ContainModule}'s {@code RiderTurret}).
 */
public interface Turret {

    /** Radians its thing's turret stands turned from the thing's facing, the way a thing turns. */
    float turretTurn();
}
