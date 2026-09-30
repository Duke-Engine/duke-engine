package uz.dukeengine.combat;

/**
 * A side whose every weapon deals more, or less — the reference's player-wide weapon bonus, which an RTS's upgrades
 * raise ({@code RtsPlayer}). A weapon multiplies what it deals by it after its own thing's modifiers and bonuses; a
 * player that is not one deals as its things do. A game with one hero gives the hero a
 * {@link uz.dukeengine.combat.module.DamageModifier} of his own instead.
 */
public interface ArmedSide {

    /** What the side's weapons' damage is multiplied by; 1 for none. */
    float getWeaponDamageBonus();
}
