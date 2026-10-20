package uz.dukeengine.client3d.action;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.action.ActionOrders;
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
public record MouseScheme(boolean modern, float lockReach, boolean rmbOnGroundWalks, boolean quickCast, boolean action,
        float meleeReach, float softLockCone) {

    public static final MouseScheme CLASSIC = new MouseScheme(false, 0f, true, false);
    public static final MouseScheme MODERN = new MouseScheme(true, 60f, false, true);
    /**
     * The action scheme's: the left button strikes along the camera's forward — a hero reaching {@code meleeReach} or
     * less swings at the point his reach ahead, one reaching further takes the nearest enemy within a cone of
     * {@code softLockCone} radians round the forward, or shoots straight ahead at his full reach.
     */
    public static final MouseScheme ACTION = new MouseScheme(true, 60f, false, true, true, 20f,
            (float) Math.toRadians(30));

    /** A scheme that strikes by the pointer, as every one did before one could strike ahead. */
    public MouseScheme(boolean modern, float lockReach, boolean rmbOnGroundWalks, boolean quickCast) {
        this(modern, lockReach, rmbOnGroundWalks, quickCast, false, 20f, (float) Math.toRadians(30));
    }

    /** An enemy the soft lock may take: its id and where it stands. */
    record Candidate(int id, float x, float y) {
    }

    /**
     * The enemy the soft lock takes: the nearest within {@code reach} of ({@code x}, {@code y}) whose middle lies inside
     * the cone round {@code forward} (radians, the world's), ties to the smaller angle off it; -1 for none.
     */
    int softLock(java.util.List<Candidate> enemies, float x, float y, float forward, float reach) {
        int best = -1;
        float bestGap = Float.MAX_VALUE;
        float bestOff = Float.MAX_VALUE;
        for (var enemy : enemies) {
            float dx = enemy.x() - x;
            float dy = enemy.y() - y;
            float gap = (float) Math.sqrt(dx * dx + dy * dy);
            float off = Math.abs((float) Math.atan2(Math.sin(Math.atan2(dy, dx) - forward),
                    Math.cos(Math.atan2(dy, dx) - forward)));
            if (gap > reach || off > softLockCone / 2f) {
                continue;
            }
            if (gap < bestGap || gap == bestGap && off < bestOff) {
                best = enemy.id();
                bestGap = gap;
                bestOff = off;
            }
        }
        return best;
    }

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
                orders.add(ActionOrders.toward(player, hero, ground, true));
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
            return List.of(ActionOrders.toward(player, hero, ground, true));
        }

        /** Let go: the held swing ends after the one under way. */
        List<Command> release(int player, List<ObjectId> hero) {
            if (!held) {
                return List.of();
            }
            held = false;
            return List.of(ActionOrders.toward(player, hero, sent, false));
        }

        boolean isHeld() {
            return held;
        }

        /** The forward last sent, quantized to 256ths of a turn; and the enemy the soft lock holds, or -1. */
        private int sentAngle = -1;
        private int locked = -1;

        /**
         * The left button pressed the action way: a strike along {@code forward} (radians) for a hero at ({@code x},
         * {@code y}) reaching {@code reach} — see {@link MouseScheme#ACTION}.
         */
        List<Command> pressAhead(MouseScheme scheme, int player, List<ObjectId> hero, float x, float y, float reach,
                float forward, List<Candidate> enemies, int frame) {
            if (reach > scheme.meleeReach()) {
                int enemy = scheme.softLock(enemies, x, y, forward, reach);
                if (enemy >= 0) {
                    locked = enemy;
                    held = true;
                    sent = null;
                    return List.of(new CombatOrder.AttackObject(player, hero, new ObjectId(enemy)));
                }
            }
            locked = -1;
            held = true;
            sentAt = frame;
            sentAngle = quantized(forward);
            sent = ahead(x, y, forward, reach);
            return List.of(ActionOrders.toward(player, hero, sent, true));
        }

        /**
         * Held on the action way: a soft lock's enemy gone, a new pick; a swing ahead sent again as the forward turns,
         * at most every few frames.
         */
        List<Command> dragAhead(MouseScheme scheme, int player, List<ObjectId> hero, float x, float y, float reach,
                float forward, List<Candidate> enemies, int frame) {
            if (!held) {
                return List.of();
            }
            if (locked >= 0) {
                int still = locked;
                if (enemies.stream().anyMatch(enemy -> enemy.id() == still)) {
                    return List.of();
                }
                return pressAhead(scheme, player, hero, x, y, reach, forward, enemies, frame); // it fell: the next
            }
            int angle = quantized(forward);
            if (angle == sentAngle || frame - sentAt < RESEND_FRAMES) {
                return List.of();
            }
            sentAngle = angle;
            sentAt = frame;
            sent = ahead(x, y, forward, reach);
            return List.of(ActionOrders.toward(player, hero, sent, true));
        }

        /** Let go the action way: a swing ahead ends after the one under way; a lock goes on until its enemy falls. */
        List<Command> releaseAhead(int player, List<ObjectId> hero) {
            boolean swinging = held && locked < 0 && sent != null;
            held = false;
            locked = -1;
            return swinging ? List.of(ActionOrders.toward(player, hero, sent, false)) : List.of();
        }

        private static int quantized(float radians) {
            return (int) Math.round(radians / (2.0 * Math.PI) * 256.0) & 0xFF;
        }

        private static Coord3D ahead(float x, float y, float forward, float reach) {
            return new Coord3D(x + (float) Math.cos(forward) * reach, y + (float) Math.sin(forward) * reach, 0f);
        }
    }
}
