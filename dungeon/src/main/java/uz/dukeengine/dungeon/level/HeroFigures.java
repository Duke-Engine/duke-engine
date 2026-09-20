package uz.dukeengine.dungeon.level;

/**
 * What one hero comes to: his creature block, his attributes at his level, and what he
 * has found.
 *
 * <p>The one place the arithmetic is done. It has two readers — the simulation that
 * applies it to his body and weapon, and the panel that prints it — and both call this,
 * so the figure under the bar is the figure taking the blows.
 *
 * @param attributes  everything he has, found things included, in tenths
 * @param bonusHealth maximum health on top of his creature block: strength and whatever
 *                    he found, in whole points
 * @param maxHealth   the whole of it
 * @param speed       movement speed, his block's and his agility's
 * @param attack      what one blow of his weapon is worth
 * @param maxMana     the pool he casts out of
 */
public record HeroFigures(Attributes attributes, int bonusHealth, float maxHealth, float speed,
        float attack, int maxMana) {

    /**
     * What he has picked up, as far as his figures are concerned.
     *
     * @param attributes    attributes items give, in tenths
     * @param health        flat maximum health
     * @param mana          flat maximum mana
     * @param attackPercent percent added to his attack
     */
    public record Found(Attributes attributes, int health, int mana, int attackPercent) {

        public static final Found NOTHING = new Found(Attributes.NONE, 0, 0, 0);
    }

    public static HeroFigures of(HeroBase base, int baseMana, HeroAttributes hero,
            AttributeRules rules, int level, Found found) {
        var attributes = hero.atLevel(level).plus(found.attributes());
        int bonusHealth = Math.addExact(rules.health(attributes), found.health());
        float speed = base.speed() + rules.speed(attributes);
        // The percentage is of the whole blow, attributes included, so a blade found on
        // the first floor is worth the same share of his swing on the fourth. Left out
        // entirely when there is none, so the blow he was built with is exactly his
        // block's and his primary's rather than that sum put through a hundred and back.
        float blow = base.damage() + rules.attack(attributes, hero.primary());
        float attack = found.attackPercent() == 0 ? blow
                : blow * (100 + found.attackPercent()) / 100f;
        int maxMana = Math.addExact(Math.addExact(baseMana, rules.mana(attributes)), found.mana());
        return new HeroFigures(attributes, bonusHealth, base.maxHealth() + bonusHealth, speed,
                attack, maxMana);
    }
}
