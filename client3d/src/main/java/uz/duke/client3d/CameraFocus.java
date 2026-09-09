package uz.duke.client3d;

import java.util.List;
import uz.duke.game.view.UnitView;

/**
 * Where the camera is looking and how far back it stands.
 *
 * <p>An RTS camera is the player's, not the game's: it goes where he pans it and
 * stays there. But there are two moments when leaving it alone is wrong, because
 * the world has just changed underneath it — the first frame of a game, and the
 * first frame of a new run after dying. Both times the player's units are
 * somewhere he has never looked, and a camera obediently holding its old position
 * is showing him nothing.
 *
 * <p>So this is a one-shot request, not a follow: something asks for the camera to
 * be put on the player's units, and the next snapshot that actually contains one
 * satisfies the request and clears it. From then on the camera is his again. That
 * matters — a camera that kept following would take panning away, and looking
 * around while your hero stands still is most of how an RTS is played.
 *
 * <p>Held apart from the jME application because it is the part with answers that
 * can be wrong, and none of it needs a window: plain floats and a list of units.
 */
final class CameraFocus {

    /**
     * How far back the camera starts.
     *
     * <p>The camera sits about this far from the point it looks at, so it shows
     * roughly this much ground front to back and half again across. A dungeon room
     * is 50–90 units, which puts the hero's room on screen with its surroundings
     * around it — close enough to see what is happening, wide enough to see what is
     * coming.
     */
    static final float START_DISTANCE = 140f;

    private static final float NEAREST = 40f;
    private static final float FURTHEST = 400f;

    private float targetX;
    private float targetZ;
    private float distance = START_DISTANCE;
    private boolean wantsOwnUnit;

    /** Look here — used before there is a world, and by the minimap. */
    void lookAt(float worldX, float worldY) {
        this.targetX = worldX;
        this.targetZ = worldY;
    }

    /**
     * Ask to be put on the player's units as soon as there are any.
     *
     * <p>Deferred rather than done now because the caller — a game starting, a new
     * dungeon being laid out — knows the world has changed before the simulation
     * has produced a snapshot showing it.
     */
    void requestOwnUnit() {
        wantsOwnUnit = true;
    }

    boolean isAwaitingOwnUnit() {
        return wantsOwnUnit;
    }

    /**
     * Satisfy a pending request if this snapshot has one of the player's units.
     * Does nothing at all when none is pending — the camera is the player's.
     *
     * @return whether the camera was moved
     */
    boolean focusOnOwnUnit(List<UnitView> units, int localPlayer) {
        if (!wantsOwnUnit) {
            return false;
        }
        for (var unit : units) {
            if (unit.playerIndex() == localPlayer) {
                lookAt(unit.x(), unit.y());
                wantsOwnUnit = false;
                return true;
            }
        }
        return false; // nothing of his on screen yet; keep asking
    }

    /** Pan by a world-space offset (the WASD keys). */
    void panBy(float dx, float dz) {
        targetX += dx;
        targetZ += dz;
    }

    /** Zoom by a factor, kept between the nearest and furthest useful distances. */
    void zoomBy(float factor) {
        distance = Math.clamp(distance * factor, NEAREST, FURTHEST);
    }

    /** How fast panning should feel — further back, faster, so it takes the same time. */
    float panSpeed() {
        return distance * 0.9f;
    }

    float targetX() {
        return targetX;
    }

    float targetZ() {
        return targetZ;
    }

    float distance() {
        return distance;
    }
}
