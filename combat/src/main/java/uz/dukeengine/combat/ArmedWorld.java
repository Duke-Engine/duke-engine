package uz.dukeengine.combat;

/**
 * A world whose things fight: the {@link Armoury} its weapons are drawn from and its rules read by. An RTS's
 * simulation is one, and so is any other game's that arms its things.
 */
public interface ArmedWorld {

    /** The world's arms, the one object its every weapon reads. */
    Armoury armoury();
}
