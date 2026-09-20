package uz.dukeengine.client3d;

import com.jme3.scene.Spatial;

/**
 * Where tile models come from.
 *
 * <p>Loading a piece needs an asset manager, and it is not worth pulling one into
 * the class that decides <em>where</em> the tiles go — that class can then be held
 * still without a window, which is where half the mistakes in a modular kit live.
 * The real one is a few lines in the client; a test's is a stub.
 */
interface TileSource {

    /**
     * A piece ready to be placed. Each call gives an instance of its own, since
     * a floor appears several hundred times and each copy stands somewhere else.
     *
     * <p>Always at full brightness. How dark a piece looks is the fog sheet's
     * — see {@link FogMap} — and a kit that dimmed its own pieces as well
     * would be drawing the dark twice, in squares the size of a tile.
     */
    Spatial piece(String assetPath);

    /**
     * The same piece under a colour of its own, multiplied over the kit's.
     *
     * <p>Not brightness, which is the fog's, but <em>identity</em>: the lid over
     * the rock and the floor of a room are the same model and the same picture, so
     * the only thing that can tell them apart is a colour one of them is given.
     * Storeys are the same question one level out.
     *
     * <p>Packed {@code 0xRRGGBB}. White is the plain piece, and an implementation
     * is free to answer with exactly that — which is what the default does, so a
     * source that has never heard of tints goes on working.
     */
    default Spatial piece(String assetPath, int packedRgb) {
        return piece(assetPath);
    }
}
