package uz.dukeengine.core.map;

/**
 * What every game's map is: a name. A map is a <em>place</em> — a floor, an island, a street — as against the
 * rules a game is made of, which are its data files; that is why a map is a package of its own (see
 * {@link MapPackage}) rather than a block in the pile.
 *
 * <p>Everything else a map may have is a capability of its own, the way a thing's is — {@link Described},
 * {@link Peopled}, {@link Scaled}, {@link Painted}, {@link Zoned}, {@link Sided}, {@link Furnished}, and the
 * world's own {@code Layered} — and the engine reads only the ones it has a use for. A game's map is a record
 * that implements what its maps have; the cells it is drawn on are a component marked {@code @Grid}, how they
 * rise and fall is one marked {@code @Relief}, what they are painted with one marked {@code @Paint}, and what
 * stands on them are components of its own with an {@code x} and a {@code y}. Nothing here knows what a
 * monster is.
 *
 * <p>The three that answer with more than a number answer with the game's own records: a {@link Zoned} map's
 * areas are whatever record it keeps them in, so long as that record implements {@link MapArea}. So a block
 * keeps the word it was written with and core still gets its question answered — the same bargain
 * {@code ThingTemplate} strikes with {@code Solid} and {@code Sighted}.
 */
public interface MapTemplate {

    /** What the map is called in files and on the command line: the name of its package, and of its own file. */
    String name();
}
