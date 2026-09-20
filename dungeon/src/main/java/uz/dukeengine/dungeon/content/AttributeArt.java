package uz.dukeengine.dungeon.content;

/**
 * How one attribute is shown: what the panel calls it and the picture beside it.
 *
 * @param name      the block's name — Strength
 * @param shortName how a hero's block names it — STR
 * @param word      what the panel calls it, in the game's own language
 * @param icon      its picture, with the panel's stat folder in front of it; empty for
 *                  none
 */
public record AttributeArt(String name, String shortName, String word, String icon) {
}
