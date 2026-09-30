package uz.dukeengine.core.view;

/**
 * Where the camera is, as the runtime's {@code GameCamera} keeps it: the point on the ground it looks at, in world
 * units; which way it faces, {@code angle}, radians about the vertical, 0 being the client's own unturned view, which
 * faces toward smaller y; how steeply it looks down, {@code pitch}, radians above the ground; and how far back it
 * stands from the point, {@code zoom}, in world units. Pitch and zoom are NaN where the client keeps its own.
 */
public record CameraView(float x, float y, float angle, float pitch, float zoom) {
}
