package uz.dukeengine.client3d;

/**
 * The player's camera as the game frames it — the reference's {@code W3DView} and its {@code GameData} camera lines:
 * how steeply it looks down, how wide it sees, how near and far it may be brought and where it starts, how far a wheel
 * notch takes it, how a middle-button drag turns it and how fast the view pans. Held by the player, the camera keeps
 * all of it; a {@code GameCamera} pitch or zoom the game leaves at NaN keeps the player's. Every number left NaN keeps
 * the client's own.
 *
 * @param pitch        how steeply it looks down, in degrees above the ground; the client's own is 55
 * @param fieldOfView  how wide it sees across the whole window, in degrees, its height following the shape of the
 *                     world's part of the window (the reference's {@code Set_Aspect_Ratio}, which keeps the horizontal
 *                     half-width); the client's own keeps 45 degrees up and down
 * @param nearest      how near the wheel and the keys may bring it to the point it looks at, along its line of sight;
 *                     the client's own is 40
 * @param furthest     how far back they may take it; the client's own is 400
 * @param start        how far back it stands when a match starts and after a reset; the client's own is 140
 * @param wheelStep    how far one notch of the wheel moves it along its line of sight; the client's own multiplies the
 *                     distance by 0.92 or 1.09
 * @param turnPerPixel how far a middle-button drag turns the view, in radians for each pixel it moves across — the
 *                     reference's 0.01 — a middle click putting it back as a reset does; 0 or NaN for neither
 * @param panAcross    how fast the pan keys and the window's edges move the view across the screen, in world units a
 *                     second whatever the zoom, before the player's own speed ({@link Duke3D#scrollSpeed}); the
 *                     client's own is 0.9 of its distance a second
 * @param panAlong     and up and down it, along the ground
 * @param turnSpeed    how fast the held turn keys turn the view, in radians a second — the reference's {@code
 *                     KeyboardCameraRotateSpeed}, 0.1 a frame, 3 a second; the client's own is a quarter turn a second
 * @param zoomSpeed    how fast the held zoom keys move the eye along its line of sight, in world units a second — the
 *                     reference's 10 higher or lower a frame, 492.8 a second along a line 37.5 degrees down; the
 *                     client's own halves or doubles its distance each second
 * @param zoomEase     the share of the way to where the wheel and the zoom keys set it that the eye closes each
 *                     thirtieth of a second — the reference's {@code CameraAdjustSpeed}, 0.3; the client's own lands
 *                     there at once
 */
public record CameraFrame(float pitch, float fieldOfView, float nearest, float furthest, float start,
        float wheelStep, float turnPerPixel, float panAcross, float panAlong, float turnSpeed, float zoomSpeed,
        float zoomEase) {

    /** Nothing framed: the client's own camera, as it always was. */
    public static final CameraFrame NONE = new CameraFrame(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN,
            Float.NaN, Float.NaN, Float.NaN, Float.NaN);

    /** A frame that leaves the keys' turn and zoom and the eye's easing the client's own. */
    public CameraFrame(float pitch, float fieldOfView, float nearest, float furthest, float start, float wheelStep,
            float turnPerPixel, float panAcross, float panAlong) {
        this(pitch, fieldOfView, nearest, furthest, start, wheelStep, turnPerPixel, panAcross, panAlong, Float.NaN,
                Float.NaN, Float.NaN);
    }
}
