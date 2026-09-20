package uz.dukeengine.client3d;

import com.jme3.math.Vector3f;
import java.util.List;
import uz.dukeengine.game.view.UnitView;

/**
 * Whether a thing that has just left the world came down, or only left the light.
 *
 * <p>Asked of what has no life of its own -- an arrow, a fireball, the mark a meteor
 * falls on. The core removes such a thing without posting that it died:
 * {@code ObjectDied} is for what had a body to lose, and a burst drawn off it was a
 * burst never drawn. So the moment a shot lands is the moment it is gone, and the
 * only question is whether gone meant "arrived" or "flew out of sight".
 *
 * <p>The player's own things are never hidden from him, so for those gone is
 * arrived. Anybody else's arrived only if the spot it was last seen at is in sight.
 */
final class Landing {

    /** How far a mark may drift and still be one; a shot moves further every frame. */
    private static final float STILL = 0.5f;

    /** How long a mark lies before it lands; a shot is never still for this long. */
    private static final float SETTLED = 0.5f;

    private Landing() {
    }

    /**
     * A shot that has just ended, as the client last drew it.
     *
     * @param his whether it was the watching player's own
     */
    record Gone(int id, String effect, boolean his, float bornX, float bornZ, float bornAt,
            float lastX, float lastZ, float goneAt) {

        /** Whether it lay where it was put for a while -- the mark a meteor falls on. */
        boolean lay() {
            return Math.hypot(lastX - bornX, lastZ - bornZ) <= STILL
                    && goneAt - bornAt >= SETTLED;
        }
    }

    /** A blow that landed this frame, where the one it landed on is. */
    record Blow(float x, float z, boolean onHis) {
    }

    /**
     * Where a shot that has just ended bursts, or {@code null} for nowhere.
     *
     * <p>A mark that lay where it was put lands there: a meteor comes down whether or
     * not anybody is under it, and the simulation centres its blast on the mark.
     *
     * <p>A shot that flew bursts only if it struck somebody. The simulation deals a
     * shot's blast round the body it struck and nothing at all when it meets a wall
     * or runs out of flight, so the only honest evidence of a strike is a blow on the
     * other side, this frame, near where the shot was last drawn -- and the burst goes
     * on that body, which is where the blast is centred. The nearest: everyone else
     * the blast caught took the same blow, further off.
     *
     * @param within how near where the shot was last drawn the body it struck may be
     */
    static Vector3f burstAt(Gone shot, List<Blow> blows, float within) {
        if (shot.lay()) {
            return new Vector3f(shot.lastX(), 0f, shot.lastZ());
        }
        Blow struck = null;
        float nearest = within * within;
        for (var blow : blows) {
            if (blow.onHis() == shot.his()) {
                continue; // his shot hurts the other side, and theirs hurts his
            }
            float dx = blow.x() - shot.lastX();
            float dz = blow.z() - shot.lastZ();
            if (dx * dx + dz * dz <= nearest) {
                nearest = dx * dx + dz * dz;
                struck = blow;
            }
        }
        return struck == null ? null : new Vector3f(struck.x(), 0f, struck.z());
    }

    /**
     * @param view         what it was when it was last seen
     * @param localPlayer  whose screen this is
     * @param lastSpotSeen whether the spot it was last drawn at is in sight now
     */
    static boolean arrived(UnitView view, int localPlayer, boolean lastSpotSeen) {
        if (view == null || view.maxHealth() > 0f) {
            return false; // a creature leaves by dying or by walking off, never by landing
        }
        return view.playerIndex() == localPlayer || lastSpotSeen;
    }
}
