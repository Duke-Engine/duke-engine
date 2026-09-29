package uz.dukeengine.core.pathfind;

/**
 * Where a piece of scenery stands in the way: a circle on the ground, in world units — see {@code
 * GameLogic.setSceneryFootprints}.
 */
public record SceneryFootprint(float x, float y, float radius) {
}
