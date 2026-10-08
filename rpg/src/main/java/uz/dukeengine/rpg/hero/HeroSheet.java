package uz.dukeengine.rpg.hero;

/**
 * What a hero is made of before he levels or finds anything: his template's name, his own plate, his pool and what
 * comes back on its own, and his attributes. The game's own record of a hero answers it.
 */
public interface HeroSheet {

    /** None yet: a hero nobody has said he plays. */
    HeroSheet NONE = new Plain("", 0, 0, 0, 0, HeroAttributes.NONE);

    /** The template his body is spawned from. */
    String name();

    /** Percent taken off what reaches him, of his own. */
    int armourPercent();

    /** His own mana pool, at the first level. */
    int maxMana();

    /** Mana coming back on its own, in tenths of a point a second. */
    int manaRegen();

    /** Health coming back on its own, in tenths of a point a second. */
    int healthRegen();

    /** His own attributes, beside what his levels give. */
    HeroAttributes attributes();

    /** A sheet and nothing else, for a game or a test with no record of its own. */
    record Plain(String name, int armourPercent, int maxMana, int manaRegen, int healthRegen,
            HeroAttributes attributes) implements HeroSheet {
    }
}
