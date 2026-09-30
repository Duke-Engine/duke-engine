package uz.dukeengine.core.view;

import uz.dukeengine.core.math.Coord3D;

/**
 * What the player's view covers, as the client last drew it: where the eye is, and the direction of the ray through
 * each corner of the world's part of the window, each of length one — in world units, x and y on the map and z up.
 * Cast onto a level, the four corners outline what is on the screen: a trapezium that grows with the zoom and turns
 * with the camera, as the reference's radar outlines its view ({@code W3DRadar::reconstructViewBox}).
 */
public record ViewRays(Coord3D eye, Coord3D topLeft, Coord3D topRight, Coord3D bottomRight, Coord3D bottomLeft) {
}
