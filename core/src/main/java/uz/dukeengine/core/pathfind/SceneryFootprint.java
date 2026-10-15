package uz.dukeengine.core.pathfind;

/**
 * Where a piece of scenery stands in the way: a circle on the ground, in world units — see {@code
 * GameLogic.setSceneryFootprints}.
 */
public record SceneryFootprint(float x, float y, float radius, int index) {

    /** A piece its map does not number: told apart by where it stands in the list it was set with. */
    public SceneryFootprint(float x, float y, float radius) {
        this(x, y, radius, -1);
    }
}
