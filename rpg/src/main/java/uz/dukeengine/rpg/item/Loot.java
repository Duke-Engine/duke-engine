package uz.dukeengine.rpg.item;

/**
 * One thing that can be found, as the data file describes it, at the level it has been joined to.
 *
 * <p>Data and nothing else: a new item is a block and no Java at all.
 *
 * <p>{@code name} is finished words — the client writes none of its own, here as
 * everywhere else — and for the same reason it may not carry the two characters
 * the status line separates its fields with.
 *
 * <p><b>Three alike are one of the next level.</b> A thing is found at the first level; three of the same thing at
 * the same level in one bag become one of the next, worth the three of them together — its figure and its extra
 * — and at the second level the extra begins. See {@link #joined}. All but one that unlocks, which never joins: see
 * {@link #joins}.
 *
 * @param id         what the block is headed by; never seen by the player
 * @param name       what it is called when he picks it up and when the pointer rests on
 *                   it, in the game's own language
 * @param icon       the picture his bag draws it with, a path from the resource root
 * @param kind       which figure it moves: one of {@link Stat}'s, or a word of the game's own
 * @param value      percent for {@code ATTACK} and {@code ARMOUR}, flat health or mana
 *                   for {@code HEALTH} and {@code MANA}, whole points for
 *                   {@code ATTRIBUTE} — at this level
 * @param weight     how often it is the one that drops; zero is never
 * @param minTier    the tier below which it is not found at all, which is what
 *                   makes going further worth the monsters — see {@link LootTable}
 * @param attribute  which attribute an {@code ATTRIBUTE} item gives, as a hero's block
 *                   names it; empty for every other kind
 * @param extra      what it gives beside its figure from the second level on: a word as {@code kind} is, or empty
 * @param extraValue how much of it, at this level: nothing at the first
 * @param extraStep  how much of it the second level brings
 * @param level      how many times it has been joined, and one: found at 1
 * @param use        what a left click on it in the bag does
 * @param liesAs     the template it lies on the floor as; blank for the chest everything else lies in
 */
public record Loot(String id, String name, String icon, String kind, int value, int weight, int minTier,
        String attribute, String extra, int extraValue, int extraStep, int level, ItemUse use, String liesAs) {

    public Loot {
        attribute = attribute == null ? "" : attribute;
        kind = kind == null ? "" : kind;
        extra = extra == null ? "" : extra;
        level = Math.max(1, level);
        use = use == null ? ItemUse.NONE : use;
        liesAs = liesAs == null ? "" : liesAs;
    }

    /** An item that gives no attribute, which is every kind but {@code ATTRIBUTE}. */
    public Loot(String id, String name, String icon, String kind, int value, int weight,
            int minTier) {
        this(id, name, icon, kind, value, weight, minTier, "");
    }

    /** An item found as it is, with nothing beside its figure. */
    public Loot(String id, String name, String icon, String kind, int value, int weight,
            int minTier, String attribute) {
        this(id, name, icon, kind, value, weight, minTier, attribute, "", 0, 0, 1, ItemUse.NONE, "");
    }

    /** Whether it is the same thing as {@code other} at the same level: what joins with it. */
    public boolean sameAs(Loot other) {
        return other != null && id.equals(other.id) && level == other.level;
    }

    /** Whether alike ones join into one of the next level: everything but a key, which is one key however many. */
    public boolean joins() {
        return use != ItemUse.UNLOCK;
    }

    /** What it lies on the floor as: its own template, or {@code chest} for a thing that names none. */
    public String liesAs(String chest) {
        return liesAs.isBlank() ? chest : liesAs;
    }

    /**
     * {@code count} of this joined into one of the next level: worth all of them together, figure and extra — and
     * at the second level its extra begins.
     */
    public Loot joined(int count) {
        return new Loot(id, name, icon, kind, value * count, weight, minTier, attribute, extra,
                extraValue * count + (level == 1 ? extraStep : 0), extraStep, level + 1, use, liesAs);
    }

    /** The same thing, worth {@code value} instead. */
    public Loot worth(int value) {
        return new Loot(id, name, icon, kind, value, weight, minTier, attribute, extra, extraValue, extraStep, level,
                use, liesAs);
    }
}
