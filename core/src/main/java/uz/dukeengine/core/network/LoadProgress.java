package uz.dukeengine.core.network;

/**
 * How far a peer has got loading the match, 0 to 100 — said while the match loads, so every machine's load screen can
 * show everybody's, as the reference's machines broadcast their percentages.
 *
 * <p>Control plane, and nothing the simulation hears: a figure for a screen, carried on the connection the match will
 * be played over because that connection already reaches everyone.
 */
public record LoadProgress(int playerIndex, int percent) implements NetMessage {
}
