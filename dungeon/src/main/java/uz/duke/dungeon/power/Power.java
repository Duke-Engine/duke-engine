package uz.duke.dungeon.power;

/**
 * One power the hero may be offered when he levels, as the data file describes it.
 *
 * <p>Data and nothing else — no game object, no simulation — for the same reason
 * {@link uz.duke.dungeon.level.Levelling} and {@link uz.duke.dungeon.skill.Skill}
 * are: what a run's fourth level can offer is a question worth being able to ask
 * without starting a dungeon.
 *
 * <p>{@code name} and {@code description} are finished words. The client draws
 * the card and writes none of them, exactly as it draws the hero's panel and
 * writes none of that — which is what lets one dungeon speak Uzbek while
 * {@code client3d} serves three other games.
 *
 * @param id           what a chosen power is named by on the wire; the block's header
 * @param name         the card's title, in the game's own language
 * @param description  the card's line under it, likewise
 * @param icon         which of the client's drawings goes on the card
 * @param effect       what it actually does
 * @param skillKey     the skill it is about, or {@link PowerEffect#EVERY_SKILL} for
 *                     all of them; meaningless for a power about the hero
 * @param value        percent for the three percentages, casts for {@code EXTRA_CHARGE}
 * @param weight       how often it comes up against the others; zero is never
 * @param maxStacks    how many times it may be taken, after which it stops being offered
 * @param minLevel     the level below which it is not offered at all
 */
public record Power(
        String id,
        String name,
        String description,
        String icon,
        PowerEffect effect,
        char skillKey,
        int value,
        int weight,
        int maxStacks,
        int minLevel) {

    /** Whether this power says anything about the skill on {@code key}. */
    public boolean appliesTo(char key) {
        return effect.isAboutASkill()
                && (skillKey == PowerEffect.EVERY_SKILL || skillKey == key);
    }
}
