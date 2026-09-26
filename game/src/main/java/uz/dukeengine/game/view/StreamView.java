package uz.dukeengine.game.view;

import java.util.List;
import uz.dukeengine.core.math.Coord3D;

/**
 * A stream as its viewer sees it — see {@code World.rideStream}: its name, and the places of the things riding it now,
 * in the order they started, in pieces broken where the game said its aim changed: one ribbon drawn through each piece.
 */
public record StreamView(String name, List<List<Coord3D>> pieces) {

    public StreamView {
        pieces = pieces.stream().map(List::copyOf).toList();
    }
}
