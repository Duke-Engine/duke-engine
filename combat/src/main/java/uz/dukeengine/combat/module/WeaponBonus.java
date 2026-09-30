package uz.dukeengine.combat.module;

/**
 * One line of a game's weapon bonus table, or of one weapon's own: while a thing holds {@code word}, its weapons'
 * {@code kind} gains what {@code multiplier} adds over 1 — the reference's {@code WeaponBonus} lines
 * ({@code VETERAN RATE_OF_FIRE 120%}), switched on by a rank, a garrison, a battle plan, whatever word the game sets.
 * Every line whose word a thing holds applies, and they add up as the reference's {@code WeaponBonus::appendBonuses}
 * adds them: {@code 1 + Σ(multiplier − 1)}, so a veteran's 110% and uranium shells' 125% make 135%.
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
