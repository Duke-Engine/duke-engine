package uz.dukeengine.client3d;

/** What a button's aim came to: its press sent with a place or a thing, or given up without one. */
public enum AimOutcome {
    /** The click came, where it fitted: the press was sent. */
    USED,
    /** A right click, Escape, or another aim armed in its place: nothing was sent. */
    GIVEN_UP
}
