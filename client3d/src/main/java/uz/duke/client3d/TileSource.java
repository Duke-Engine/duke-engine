package uz.duke.client3d;

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
}
