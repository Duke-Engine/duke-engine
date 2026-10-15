package uz.dukeengine.core.event;

/**
 * A piece of the map's scenery taken away — a tree felled — or put back, grown again: {@code index} its place in the
 * map's scenery, for the picture to stop or start drawing it. See {@code GameLogic.removeScenery}.
 */
public record SceneryChanged(int frame, int index, boolean removed) implements WorldEvent {
}
