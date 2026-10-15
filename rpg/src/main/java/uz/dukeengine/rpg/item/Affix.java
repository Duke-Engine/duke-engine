package uz.dukeengine.rpg.item;

/**
 * One more figure a thing gives beside its own: a magic sword's +6 % attack speed, a rare ring's +30 health.
 *
 * @param stat  which figure it moves: a {@link Stat} word, an attribute as a hero's block names it, the game's own
 *              word, or {@code SKILL_RANK:Q} for a rank on that key's skill
 * @param value how much, in the figure's own units
 */
public record Affix(String stat, int value) {

    public Affix {
        stat = stat == null ? "" : stat;
    }
}
