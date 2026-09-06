package uz.duke.core.player;

/**
 * How one player regards another, ported from SAGE's {@code Relationship}.
 *
 * <p>Drives targeting, line-of-fire and most combat decisions: a unit attacks
 * {@link #ENEMIES}, ignores {@link #NEUTRAL}, and protects {@link #ALLIES}.
 */
public enum Relationship {
    ENEMIES,
    NEUTRAL,
    ALLIES
}
