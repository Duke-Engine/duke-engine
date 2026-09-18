package uz.duke.client3d;

import com.jme3.math.ColorRGBA;
import java.util.List;

/**
 * What the bar over a creature's head is made of.
 *
 * <p>A health bar is the most-read thing on the screen and the least looked at:
 * a player takes it in at the edge of his attention while he is aiming somewhere
 * else. So the whole of the design is about being readable without being read —
 * how long it is, how it is divided, and what colour — and none of those is a
 * number that can be reasoned out. They are found by eye, which is why they are
 * all in the game's data file. See {@code UnitBar}.
 *
 * <h2>The two rules, and why they are two</h2>
 *
 * <p><b>Length says how much.</b> A boss's bar is long and a rat's is short, so
 * that the player is told "this one is dangerous" before he has read anything at
 * all. Logarithmic between two anchors rather than proportional, because the
 * range this game actually produces is 30 health to about 1400 — forty-six times
 * — and a proportional bar spends nine tenths of that range in its first
 * quarter-inch. The eye compares two bars as a ratio; the curve is the one that
 * matches it.
 *
 * <p><b>Ticks say how much <em>each</em>.</b> Dark marks across the fill, one per
 * {@link Step#value} of health, from a table shared by every creature in the
 * game. That is the whole point of the table: a player who has learnt that a
 * mark is fifty health on one monster can read the next one without being told,
 * which he could not do if every creature scaled its own marks to its own size.
 *
 * <p><b>They were nearly one rule.</b> Drawing the bar as {@code ticks × a fixed
 * spacing} makes the two agree by construction and is a paragraph shorter — and
 * it is wrong, because a stepped table is not monotonic. Ninety health at ten a
 * mark is nine marks; a hundred and seventy at twenty-five a mark is six. The
 * weaker monster would have had the longer bar. Two rules, then, and the ticks
 * are allowed to sit at different spacings on different bars.
 *
 * @param steps        the segment table, in order, each rung covering health up
 *                     to its own ceiling — see {@link Step}. Chosen so the count
 *                     of marks stays in the band where a bar can be counted at a
 *                     glance and still divided finely enough to mean anything
 * @param shortestAt   the health at which a bar is drawn at its shortest, and
 *                     below which it stops shrinking. A floor rather than a
 *                     scale: something has to be visible
 * @param longestAt    the health at which it reaches its longest. Past it every
 *                     bar is the same length, which is the honest answer — at
 *                     that size the number is what is being read
 * @param shortest     how many pixels long the shortest bar is
 * @param longest      and the longest
 * @param height       how tall the bar is. The design's thirteen: tall enough to
 *                     hold a number inside it, which is what took the reading off
 *                     the top of the creature's head and put it where the bar is
 * @param manaHeight   how tall the mana bar under it is, or 0 for a game with no
 *                     mana at all. Thinner on purpose — it is the second thing
 *                     to look at, and a bar of equal weight would argue with the
 *                     first
 * @param gap          the clear space between the two
 * @param lift         how far above the creature's own height the bar floats, in
 *                     world units, before any of this is projected
 * @param ring         how wide across the medallion at the near end is
 * @param ringEdge     how thick its black keyline is
 * @param ringGap      the clear space between the medallion and the bar
 * @param arc          how thick the experience ring around the medallion is
 * @param enemy        the fill on somebody else's creature
 * @param friend       the fill on his own, and on anything fighting for him
 * @param mana         the fill on the mana bar
 * @param trough       what is behind an unfilled bar
 * @param tick         the marks across the fill
 * @param ringFace     what fills the medallion behind its number
 * @param ringRim      the rim of an ordinary creature's medallion
 * @param bossRim      and of a boss's, which is the one thing on the bar that is
 *                     meant to be noticed rather than read. The experience ring
 *                     is drawn in whichever of the two this creature wears, at
 *                     full strength where it has filled and at a fraction where
 *                     it has not — one colour and two strengths, because the
 *                     ring says two things at once and a second colour would
 *                     have them arguing over which the eye answers first
 * @param lettering    the numbers inside the bars and the level in the medallion
 * @param nameSize     how big a creature's name is under its bar
 * @param bossNameSize and a boss's, which is larger for the same reason its rim
 *                     is a different colour
 * @param countSize    how big the reading inside a bar is
 * @param levelSize    how big the level in the medallion is
 */
public record UnitBarLook(
        List<Step> steps,
        int shortestAt,
        int longestAt,
        float shortest,
        float longest,
        float height,
        float manaHeight,
        float gap,
        float lift,
        float ring,
        float ringEdge,
        float ringGap,
        float arc,
        int enemy,
        int friend,
        int mana,
        int trough,
        int tick,
        int ringFace,
        int ringRim,
        int bossRim,
        int lettering,
        float nameSize,
        float bossNameSize,
        float countSize,
        float levelSize) {

    /**
     * One rung of the segment table: health up to {@code upTo} is marked off in
     * lots of {@code value}.
     *
     * <p>The last rung in the list is the open end and its ceiling is ignored, so
     * that a creature larger than anything the table anticipated still gets marks
     * rather than none.
     *
     * @param upTo  the greatest health this rung covers
     * @param value how much health one mark is worth here
     */
    public record Step(int upTo, int value) {
    }

    public UnitBarLook {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    /**
     * No game said anything: nothing is drawn over anybody.
     *
     * <p>An empty table rather than a guessed one. Three other games launch this
     * client and none of them has a dungeon's idea of what a creature is worth;
     * a bar with invented marks on it would be the client claiming to know.
     */
    public static final UnitBarLook NONE = new UnitBarLook(List.of(), 1, 2, 0f, 0f,
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0f, 0f, 0f, 0f);

    /** Whether there is anything here to draw at all. */
    public boolean draws() {
        return !steps.isEmpty() && longest > 0f && height > 0f;
    }

    /** Whether this game has a mana bar to put under the health one. */
    public boolean hasMana() {
        return manaHeight > 0f;
    }

    /** How much health one mark is worth on a creature of this size. */
    public int valueFor(float maxHealth) {
        if (steps.isEmpty()) {
            return 0;
        }
        for (var step : steps) {
            if (maxHealth <= step.upTo()) {
                return Math.max(1, step.value());
            }
        }
        return Math.max(1, steps.get(steps.size() - 1).value());
    }

    /**
     * How many marks a creature of this size wears.
     *
     * <p>Rounded down and never less than one. Down, because a mark that is only
     * partly earned is a mark the player would count: nineteen and a sliver reads
     * as twenty, and then the last one empties in a fraction of what the others
     * took.
     */
    public int segmentsFor(float maxHealth) {
        int value = valueFor(maxHealth);
        return value <= 0 ? 0 : Math.max(1, (int) (maxHealth / value));
    }

    /**
     * How long this creature's bar is, in the interface's own pixels.
     *
     * <p>See the class note for why the curve is logarithmic. Below
     * {@link #shortestAt} and above {@link #longestAt} it is flat, so the two
     * anchors are also the answer to "what about a creature nobody planned for".
     */
    public float widthFor(float maxHealth) {
        if (longestAt <= shortestAt || shortestAt <= 0) {
            return longest; // nothing to interpolate between: one length for all
        }
        if (maxHealth <= shortestAt) {
            return shortest;
        }
        if (maxHealth >= longestAt) {
            return longest;
        }
        double span = Math.log(longestAt) - Math.log(shortestAt);
        double along = (Math.log(maxHealth) - Math.log(shortestAt)) / span;
        return shortest + (longest - shortest) * (float) along;
    }

    /**
     * How far apart the marks fall on that bar, in pixels.
     *
     * <p>Worked out rather than given, since the two rules above settle it
     * between them. It is what a repeating texture is scaled by, which is the
     * difference between twenty geometries per creature and one.
     */
    public float tickSpacingFor(float maxHealth) {
        int marks = segmentsFor(maxHealth);
        return marks <= 0 ? 0f : widthFor(maxHealth) / marks;
    }

    public ColorRGBA fill(boolean his) {
        return colour(his ? friend : enemy);
    }

    public ColorRGBA rim(boolean boss) {
        return colour(boss ? bossRim : ringRim);
    }

    public float nameSize(boolean boss) {
        return boss ? bossNameSize : nameSize;
    }

    public ColorRGBA manaColour() {
        return colour(mana);
    }

    public ColorRGBA troughColour() {
        return colour(trough);
    }

    public ColorRGBA tickColour() {
        return colour(tick);
    }

    public ColorRGBA faceColour() {
        return colour(ringFace);
    }

    public ColorRGBA letteringColour() {
        return colour(lettering);
    }

    /** A packed 0xRRGGBB, opaque, the way every other look in this client reads one. */
    static ColorRGBA colour(int packed) {
        return new ColorRGBA(((packed >> 16) & 0xFF) / 255f, ((packed >> 8) & 0xFF) / 255f,
                (packed & 0xFF) / 255f, 1f);
    }
}
