package uz.dukeengine.dungeon.world;

import uz.dukeengine.dungeon.loot.Loot;
import uz.dukeengine.dungeon.loot.LootKind;

/**
 * One thing that can be found on a floor. A new one is a block and no Java.
 *
 * @param name        its id, which nothing but the file and the game read
 * @param displayName what the panel calls it; its name when the block gives none
 * @param attribute   which attribute a {@code Kind = ATTRIBUTE} item gives, by the name a hero's
 *     block uses for it
 */
public record LootItem(String name, String displayName, String icon, LootKind kind, int value, int weight,
        int minDepth, String attribute) {

    /** What a block leaves out. */
    public static final LootItem DEFAULTS = new LootItem("", "", "", LootKind.ATTACK, 0, 10, 1, "");

    public Loot loot() {
        return new Loot(name, displayName == null || displayName.isBlank() ? name : displayName, icon, kind, value,
                weight, minDepth, attribute);
    }
}
