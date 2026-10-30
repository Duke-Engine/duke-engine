package uz.dukeengine.client3d.touch;

/**
 * A floating thumb stick's maths: a touch sets its centre, a drag gives a direction and a strength, a lift stops it.
 * Pixels, y up the screen as jME gives them. It knows no window, so a test can feed it by hand.
 */
final class VirtualStick {

    /** No direction: the stick idle or inside its dead zone — the {@code ActionOrders.NONE} it is sent as. */
    static final int NONE = -1;

    /** How far the knob can be pulled from the centre, pixels. */
    static final float RADIUS = 120f;

    /** The share of the radius inside which the stick says nothing. */
    static final float DEAD_ZONE = 0.2f;

    /** The radius now: {@link #RADIUS} times the screen's density. */
    private float radius = RADIUS;
    private boolean active;
    private float cx;
    private float cy;
    private float dx;
    private float dy;

    /** Size the stick for a screen {@code uiScale} times as dense as the design. */
    void scale(float uiScale) {
        radius = RADIUS * uiScale;
    }

    float radius() {
        return radius;
    }

    void down(float x, float y) {
        active = true;
        cx = x;
        cy = y;
        dx = 0f;
        dy = 0f;
    }

    void move(float x, float y) {
        if (active) {
            dx = x - cx;
            dy = y - cy;
        }
    }

    void up() {
        active = false;
        dx = 0f;
        dy = 0f;
    }

    boolean active() {
        return active;
    }

    float centreX() {
        return cx;
    }

    float centreY() {
        return cy;
    }

    /** The knob's place, pulled back to the radius. */
    float knobX() {
        return cx + dx * Math.min(1f, radius / Math.max(distance(), 1e-6f));
    }

    float knobY() {
        return cy + dy * Math.min(1f, radius / Math.max(distance(), 1e-6f));
    }

    /** 0 inside the dead zone, then growing to 1 at the radius. */
    float strength() {
        float share = Math.min(1f, distance() / radius);
        return share <= DEAD_ZONE ? 0f : (share - DEAD_ZONE) / (1f - DEAD_ZONE);
    }

    /**
     * The direction on the ground, the camera turned {@code yaw}: in 256ths of a turn, 0 along +x and counter-clockwise
     * from above, as the walking keys give it ({@code DirectionKeys.angleOf}) — up the screen walks away from the eye.
     * {@link #NONE} while idle or inside the dead zone.
     */
    int angle(float yaw) {
        if (strength() == 0f) {
            return NONE;
        }
        float across = dx / distance();
        float downScreen = -dy / distance();
        float cos = (float) Math.cos(yaw);
        float sin = (float) Math.sin(yaw);
        float x = across * cos + downScreen * sin;
        float y = downScreen * cos - across * sin;
        return (int) Math.round(Math.atan2(y, x) / (2.0 * Math.PI) * 256.0) & 0xFF;
    }

    private float distance() {
        return (float) Math.hypot(dx, dy);
    }
}
