package uz.duke.client3d;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The floor's half of the status line, read.
 *
 * <p>The other half is the card under the player's thumb and is read by
 * {@link HeroPanel}; this is what goes over everybody's head out in the world.
 * Both come down the same string, because the status channel is one string —
 * a line the engine carries and never reads, which is the seam a game is given
 * for whatever it counts that the engine has no name for.
 *
 * <p><b>Four facts, and none of them is per creature.</b> The snapshot already
 * says where everything is, whose it is and what is left of it; what it cannot
 * say is a level, a pool of mana, a printed name, or which of them is the boss.
 * Written per creature that would be fifty entries rebuilt thirty times a
 * second. It is not:
 *
 * <ul>
 * <li>the level is the <b>depth</b>, one number for the whole floor — which is
 *     what a monster is scaled by and what a stage's difficulty is defined as,
 *     so it is the level rather than a stand-in for one
 * <li>the boss is one id
 * <li>a name belongs to the <b>template</b>, so it is a dictionary of a dozen
 * <li>and mana belongs to whoever has a skill book, which here is the hero
 * </ul>
 *
 * <p><b>A field that will not parse is dropped in silence</b>, which is the
 * opposite of what the panel does with the same line — and deliberately. The
 * panel refuses a line it does not fully understand because a half-read card is
 * a card that lies about the hero. These are marks over the world: a name that
 * did not arrive is a name not drawn, and a bar with no name on it is still a
 * bar.
 *
 * @param depth            the floor, and so every monster's level. 0 when the
 *                         game said nothing
 * @param bossId           the creature whose medallion is lit differently, or 0
 * @param heroId           whose medallion carries an experience ring, or 0. Only
 *                         his: he is the only thing in the game that earns any,
 *                         and an empty ring on a skeleton would be a promise
 * @param heroLevel        what his medallion says, where a monster's says the
 *                         depth
 * @param mana             what is in his pool
 * @param maxMana          and what it holds, or 0 for a hero who casts free
 * @param experience       how far into his level he is
 * @param experienceNeeded and how far the level goes
 * @param names            what to print under a creature, by template name.
 *                         Missing means the template's own name is as good as
 *                         anything the game could have added
 */
record UnitBarReading(
        int depth,
        int bossId,
        int heroId,
        int heroLevel,
        int mana,
        int maxMana,
        int experience,
        int experienceNeeded,
        Map<String, String> names) {

    /** A game that says none of this: every bar is drawn plain, or not at all. */
    static final UnitBarReading NOTHING =
            new UnitBarReading(0, 0, 0, 0, 0, 0, 0, 0, Map.of());

    UnitBarReading {
        names = names == null ? Map.of() : Map.copyOf(names);
    }

    /** Whether this creature is the one the floor is named after. */
    boolean isBoss(int unitId) {
        return bossId != 0 && unitId == bossId;
    }

    /** Whether this is the creature whose ring fills. */
    boolean isHero(int unitId) {
        return heroId != 0 && unitId == heroId;
    }

    /** What the medallion says on this creature: his own level, or the floor's. */
    int levelOn(int unitId) {
        return isHero(unitId) ? heroLevel : depth;
    }

    /** How far round the ring has gone, or 0 for anybody who earns nothing. */
    float experienceOn(int unitId) {
        if (!isHero(unitId) || experienceNeeded <= 0) {
            return 0f;
        }
        return Math.clamp(experience / (float) experienceNeeded, 0f, 1f);
    }

    /** What to print under this creature, or its template name if nothing better. */
    String nameOf(String templateName) {
        var given = names.get(templateName);
        return given == null ? templateName : given;
    }

    /**
     * Read the floor's fields out of a status line.
     *
     * <p>Walks the whole line rather than stopping at the first thing it knows,
     * since the two halves are interleaved in whatever order the game wrote
     * them and neither end should have to care.
     */
    static UnitBarReading read(String status) {
        if (status == null || status.isEmpty()) {
            return NOTHING;
        }
        int depth = 0;
        int boss = 0;
        var hero = new int[] {0, 0, 0, 0, 0, 0};
        var names = new LinkedHashMap<String, String>();
        for (var field : status.split("[|]")) {
            int split = field.indexOf('=');
            if (split < 0) {
                continue;
            }
            var value = field.substring(split + 1);
            switch (field.substring(0, split)) {
                case "deep" -> depth = whole(value, depth);
                case "boss" -> boss = whole(value, boss);
                case "hero" -> hero = six(value, hero);
                case "who" -> {
                    var halves = value.split(",", 2);
                    if (halves.length == 2 && !halves[0].isBlank()) {
                        names.put(halves[0], halves[1]);
                    }
                }
                default -> { } // the panel's, or another game's
            }
        }
        return new UnitBarReading(depth, boss, hero[0], hero[1], hero[2], hero[3],
                hero[4], hero[5], names);
    }

    private static int whole(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }

    /** {@code id,level,mana,maxMana,xp,xpNeeded} — all of it or none of it. */
    private static int[] six(String value, int[] fallback) {
        var parts = value.split(",");
        if (parts.length < 6) {
            return fallback;
        }
        var read = new int[6];
        for (int at = 0; at < 6; at++) {
            try {
                read[at] = Integer.parseInt(parts[at].trim());
            } catch (NumberFormatException notANumber) {
                return fallback; // a partly-read hero is worse than no hero
            }
        }
        return read;
    }
}
