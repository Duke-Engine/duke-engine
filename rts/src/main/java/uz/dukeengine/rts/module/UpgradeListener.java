package uz.dukeengine.rts.module;

/**
 * A module told that an upgrade reached its thing: the one building or unit that researched an upgrade of its
 * own, every thing of a side that finished one of the side's, and a thing made afterwards by that side as it is
 * made. The reference's upgrade modules — a weapon set swapped, armour changed, a locomotor fitted — are the
 * game's answer to this; the word the upgrade leaves ({@code GameObject.hasCondition}) is the other.
 */
public interface UpgradeListener {

    void onUpgrade(String name);
}
