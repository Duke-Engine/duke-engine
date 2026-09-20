package uz.dukeengine.dungeon.gen;

import uz.dukeengine.dungeon.content.DungeonSettings;

/**
 * How big a dungeon to draw, and how much to put in it.
 *
 * <p>These numbers have always been in the settings file, where they describe the
 * floors of the endless descent — kept tight on purpose, because in a roguelike
 * space beyond what the rooms need becomes corridor, and corridor is walked
 * through rather than played.
 *
 * <p>A stage is the other case. It is drawn once, by hand, to be learnt and
 * re-attempted, and there the argument reverses: a floor somebody spends an
 * evening on can afford to be far larger than one he will see once. So the
 * numbers come out of the settings and into a value the caller may replace —
 * {@link #of(DungeonSettings)} is the descent's own, unchanged, and
 * {@link #sized} is what a map drawn once is asked for.
 *
 * <p>Only the three a map drawn once is asked about are open: how wide, how tall,
 * how many rooms. Room size, corridor width, the gap between rooms and how far
 * apart they may drift stay in the file — they are what a dungeon in this game
 * <em>is</em>, and a room fourteen cells across is not a bigger dungeon, it is a
 * different game.
 *
 * @param width      cells across
 * @param height     cells down
 * @param minRooms   fewest rooms to aim for
 * @param maxRooms   most to aim for. The two are a range because the descent
 *                   draws one number out of it per floor; a builder asking for
 *                   forty rooms sets both to forty
 * @param attempts   how many times to try landing a room before settling for the
 *                   ones that fit. Rooms are placed by rejection, so this has to
 *                   grow with how many are wanted and how full the map is getting
 */
public record Layout(int width, int height, int minRooms, int maxRooms, int attempts) {

    /**
     * Roughly how much map one room wants, counting the corridor to reach it and
     * the stone around it.
     *
     * <p>Measured off the shipped floor rather than reasoned about: 50 x 36 cells
     * hold six to fifteen rooms, so the useful range is somewhere near a room per
     * two hundred cells. Only ever a suggestion the builder shows the author —
     * nothing refuses a number because of it.
     */
    private static final int CELLS_PER_ROOM = 200;

    /** The dungeon the settings file describes: the descent's own floors. */
    public static Layout of(DungeonSettings settings) {
        return new Layout(settings.mapWidth(), settings.mapHeight(),
                settings.minRooms(), settings.maxRooms(), settings.placementAttempts());
    }

    /**
     * A dungeon of the author's own size.
     *
     * <p>The attempt count is not asked for and is not the file's either. Placement
     * is rejection sampling: the fuller the map gets the more throws it takes to
     * land the next room, and a count tuned for six rooms on a small map gives up
     * a third of the way through forty on a large one — leaving a floor quietly
     * smaller than the one that was asked for. So it is derived from what is being
     * asked, and generously.
     */
    public static Layout sized(DungeonSettings settings, int width, int height, int rooms) {
        int wanted = Math.max(1, rooms);
        return new Layout(width, height, wanted, wanted,
                Math.max(settings.placementAttempts(), wanted * 500));
    }

    /** About how many rooms a map this size has space for — a hint, never a rule. */
    public static int roomsThatFit(int width, int height) {
        return Math.max(2, width * height / CELLS_PER_ROOM);
    }
}
