package uz.dukeengine.rts.module;

/**
 * One line of a game's weapon bonus table: while a thing holds {@code word}, its weapons' {@code kind} is multiplied
 * by {@code multiplier} — the reference's {@code WeaponBonus} lines ({@code VETERAN RATE_OF_FIRE 120%}), switched on
 * by a rank, a garrison, a battle plan, whatever word the game sets. Every line whose word a thing holds applies,
 * multiplied together in the order of their words, sorted.
 */
public record WeaponBonus(String word, Kind kind, float multiplier) {

    /** What a bonus multiplies. */
    public enum Kind {
        /** What a shot deals. */
        DAMAGE,
        /** How far a weapon reaches. */
        RANGE,
        /** How often it fires: every wait, between shots and to fill its clip, divided by it. */
        RATE_OF_FIRE,
        /** How wide its blast is. */
        RADIUS
    }
}
