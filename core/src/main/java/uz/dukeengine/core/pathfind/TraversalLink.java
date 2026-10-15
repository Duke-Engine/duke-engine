package uz.dukeengine.core.pathfind;

import uz.dukeengine.core.math.Coord3D;

/**
 * A way across what no walk crosses — a gap jumped, a low wall vaulted, a ledge climbed or dropped: from {@code a} to
 * {@code b}, crossed in {@code frames} along an arc {@code arcHeight} high, the walker busy at {@code clip} meanwhile.
 * See {@code GameLogic.setTraversalLinks}.
 *
 * @param kind     the game's word for it — {@code jump}, {@code vault}, {@code climb}, {@code drop}
 * @param bothWays whether it is crossed from {@code b} as well as from {@code a}
 * @param low      whether a dodge across its line crosses it (the game's to read)
 */
public record TraversalLink(Coord3D a, Coord3D b, String kind, int frames, float arcHeight, String clip,
        boolean bothWays, boolean low) {

    public TraversalLink {
        frames = Math.max(1, frames);
    }

    /** The same link, its ends scaled from cells to world units by {@code cell}. */
    public TraversalLink scaled(float cell) {
        return new TraversalLink(new Coord3D(a.x() * cell, a.y() * cell, a.z()),
                new Coord3D(b.x() * cell, b.y() * cell, b.z()), kind, frames, arcHeight, clip, bothWays, low);
    }
}
