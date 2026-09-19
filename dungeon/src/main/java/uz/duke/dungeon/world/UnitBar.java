package uz.duke.dungeon.world;

import java.util.List;

/**
 * How the bar over a creature's head is drawn. Everything here is found by eye, which is why none
 * of it is in the client — see {@code uz.duke.client3d.UnitBarLook}.
 *
 * @param segments the segment table, coarsest last, or empty for a game that draws no bars. Empty
 *     is the meaningful default and the rest are not: without a table there is nothing to divide
 *     a bar into, so the client draws none at all rather than inventing lots of its own
 */
public record UnitBar(List<BarStep> segments, int shortestAt, int longestAt, float shortest, float longest,
        float height, float manaHeight, float gap, float lift, float ring, float ringEdge, float ringGap,
        float arc, int enemy, int friend, int mana, int trough, int tick, int ringFace, int ringRim,
        int bossRim, int lettering, float nameSize, float bossNameSize, float countSize, float levelSize) {

    /** What a block leaves out. */
    public static final UnitBar DEFAULTS = new UnitBar(List.of(), 30, 1400, 80f, 220f, 13f, 6f, 2f, 1.4f, 26f,
            2f, 4f, 3f, 0xA8322B, 0x8FC4AE, 0x3E6FA8, 0x16130F, 0x0A0806, 0x16130F, 0x8FC4AE, 0xE8A33D,
            0xD9CFBA, 11f, 15f, 10f, 12f);

    /**
     * One rung of the segment table, written {@code upTo:worth} — {@code *} for the rung with no
     * ceiling. One item rather than two lists, so a rung cannot be half-written.
     *
     * @param upTo  the greatest health this rung covers, or 0 for the open end
     * @param value how much health one mark is worth here
     */
    public record BarStep(int upTo, int value) {

        public static BarStep of(String written) {
            var halves = written.split(":", 2);
            if (halves.length < 2 || halves[1].isBlank()) {
                throw new IllegalArgumentException(written + " is a rung: the health it goes up to, ':', then what"
                        + " a mark is worth");
            }
            var upTo = halves[0].strip();
            return new BarStep(upTo.equals("*") ? 0 : Integer.parseInt(upTo), Integer.parseInt(halves[1].strip()));
        }
    }
}
