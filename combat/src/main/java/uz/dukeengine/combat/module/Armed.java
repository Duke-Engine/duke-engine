package uz.dukeengine.combat.module;

/** A weapon one slot of the set in use carries, which slot, and its clip: what is weighed when a target is. */
record Armed(int index, WeaponSlot slot, Weapon weapon, Clip clip) {
}
