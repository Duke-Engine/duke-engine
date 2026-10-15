package uz.dukeengine.client3d;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.combat.message.CombatOrder;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.thing.ObjectId;

/**
 * How the mouse plays: {@link #CLASSIC}, everything as it always was, or {@link #MODERN} — an action game's, for a
 * single hero: the left button locks on an enemy under it or swings toward the ground, held swinging again; the right
 * button gives the context orders only; a skill's letter casts at once at the pointer.
 *
 * @param modern           whether it plays the modern way
 * @param lockReach        how near the hero an enemy under the pointer must be for the left button to lock on to it
 * @param rmbOnGroundWalks whether the right button on plain ground walks there
 * @param quickCast        whether a skill's letter casts at once where the pointer is, not on the next click
 */
public record MouseScheme(boolean modern, float lockReach, boolean rmbOnGroundWalks, boolean quickCast) {

    public static final MouseScheme CLASSIC = new MouseScheme(false, 0f, true, false);
    public static final MouseScheme MODERN = new MouseScheme(true, 60f, false, true);

    /** The least a held swing's point moves before it is sent again, and the fewest frames between two sends. */
    static final float RESEND_DISTANCE = 2f;
    static final int RESEND_FRAMES = 3;

    /** Whether the right button orders here: a word the game names for it, or a thing, or — if it walks — any ground. */
    boolean rightOrders(boolean namedWord, boolean onAThing) {
        return !modern || namedWord || onAThing || rmbOnGroundWalks;
    }

    /** The left button's swing, held: what it sent last, and when. */
    static final class Swing {
        private boolean held;
        private Coord3D sent;
        private int sentAt;

        /**
         * The left button pressed: a lock on {@code enemy} (-1 for none) where it is within the lock's reach of the
         * hero ({@code gap}), else a swing toward {@code ground}, held.
         */
        List<Command> press(MouseScheme scheme, int player, List<ObjectId> hero, int enemy, float gap, Coord3D ground,
                int frame) {
            var orders = new ArrayList<Command>();
            if (enemy >= 0 && gap <= scheme.lockReach()) {
                orders.add(new CombatOrder.AttackObject(player, hero, new ObjectId(enemy)));
                return orders;
            }
            if (ground != null) {
                held = true;
                sent = ground;
                sentAt = frame;
                orders.add(new CombatOrder.AttackToward(player, hero, ground, true));
            }
            return orders;
        }

        /** Held on: the point sent again once it has moved far enough, and not oftener than every few frames. */
        List<Command> drag(int player, List<ObjectId> hero, Coord3D ground, int frame) {
            if (!held || ground == null || frame - sentAt < RESEND_FRAMES
                    || ground.distance(sent) < RESEND_DISTANCE) {
                return List.of();
            }
            sent = ground;
            sentAt = frame;
            return List.of(new CombatOrder.AttackToward(player, hero, ground, true));
        }

        /** Let go: the held swing ends after the one under way. */
        List<Command> release(int player, List<ObjectId> hero) {
            if (!held) {
                return List.of();
            }
            held = false;
            return List.of(new CombatOrder.AttackToward(player, hero, sent, false));
        }

        boolean isHeld() {
            return held;
        }
    }
}
