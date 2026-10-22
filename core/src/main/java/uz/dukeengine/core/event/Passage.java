package uz.dukeengine.core.event;

/** How a thing went into a hold or came out of it — see {@link ObjectEntered} and {@link ObjectLeft}. */
public enum Passage {
    /** Loaded in. */
    BOARDED,
    /** Let out by an order, a sale or the game: stepped out beside its holder. */
    EJECTED,
    /** Let out by its holder's death — the reference's falling from a plane. */
    FELL,
    /** Let out by an evacuate order. */
    EVACUATED
}
