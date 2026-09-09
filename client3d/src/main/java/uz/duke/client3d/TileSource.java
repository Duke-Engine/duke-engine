package uz.duke.client3d;

import com.jme3.scene.Spatial;

/**
 * Where tile models come from, and how a tile is made to look remembered.
 *
 * <p>Both jobs need an asset manager and a shader, and neither is worth pulling
 * into the class that decides <em>where</em> the tiles go — that class can then be
 * held still without a window, which is where half the mistakes in a modular kit
 * live. The real one is a few lines in the client; a test's is a stub.
 */
interface TileSource {

    /**
     * A piece ready to be placed. Each call gives an instance of its own, since
     * a floor appears several hundred times and each copy stands somewhere else.
     */
    Spatial piece(String assetPath);

    /**
     * Draw a piece at a brightness: 1 for ground in sight, 0 for black, and
     * anything between for the fog's soft edge and for ground the player has
     * walked and left.
     *
     * <p>Kept here rather than in the scene because "dimmer" is a property of
     * whatever material the kit turned out to use, and the scene should not have
     * to know. A source is free to round to as few steps as it likes -- what it
     * must not do is treat this as a flag.
     */
    void shade(Spatial piece, float light);
}
