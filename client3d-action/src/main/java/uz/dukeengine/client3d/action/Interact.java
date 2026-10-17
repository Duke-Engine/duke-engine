package uz.dukeengine.client3d.action;

import java.util.function.BiFunction;
import uz.dukeengine.combat.message.GameOrder;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.game.DukeGame;

/**
 * The nearest thing worth an order in front of the hero — see {@link ActionControls#interact(char, float, float,
 * BiFunction)}: looked for on the simulation thread with each snapshot, as the pointer's word is, and given on a key.
 */
final class Interact {

    /** What the key would give an order to now: who (-1 for a piece of the map's scenery), the word, and where. */
    record Target(int unit, String word, float x, float y) {
    }

    private final char key;
    private final float reach;
    private final float cone;
    private final BiFunction<String, Character, String> promptOf;
    private volatile Target target;

    Interact(char key, float reach, float cone, BiFunction<String, Character, String> promptOf) {
        this.key = key;
        this.reach = reach;
        this.cone = cone;
        this.promptOf = promptOf;
    }

    char key() {
        return key;
    }

    BiFunction<String, Character, String> promptOf() {
        return promptOf;
    }

    Target target() {
        return target;
    }

    /** Give it what a click on it gives — a piece of scenery, what a click on the ground under it gives. */
    void give(DukeGame game) {
        var now = target;
        if (now == null || game.camera().isDriven()) {
            return;
        }
        var units = new java.util.ArrayList<ObjectId>();
        for (var id : game.getOrderedTo()) {
            units.add(new ObjectId(id));
        }
        game.postCommand(new GameOrder(game.getLocalPlayerIndex(), now.word(), units, new Coord3D(now.x(), now.y(), 0f),
                now.unit() < 0 ? null : new ObjectId(now.unit()), 0));
    }

    /**
     * The nearest thing a context rule names a word for, or piece of scenery the ground rule does, within reach of the
     * first of his things selected and inside the cone round his facing — the camera's forward while he walks — ties to
     * the smaller angle; none while he is stunned or held. On the simulation thread.
     */
    void look(DukeGame game) {
        target = find(game);
    }

    private Target find(DukeGame game) {
        var world = game.getLogic();
        var selected = game.orderedOwn();
        if (world == null || reach <= 0f || selected.isEmpty()) {
            return null;
        }
        var hero = selected.getFirst();
        if (hero.hasStatus(ObjectStatus.DISABLED) || hero.hasStatus(ObjectStatus.HELD)) {
            return null;
        }
        var legs = hero.getLocomotor();
        float yaw = game.camera().current().angle();
        float facing = legs != null && legs.isMoving()
                ? (float) StrictMath.atan2(-StrictMath.cos(yaw), -StrictMath.sin(yaw)) : hero.getOrientation();
        var here = hero.getPosition();
        Target best = null;
        float bestGap = Float.MAX_VALUE;
        float bestOff = Float.MAX_VALUE;
        for (var thing : world.objectsInRange(here, reach, other -> other != hero)) {
            var at = thing.getPosition();
            float gap = gap(here, at.x(), at.y());
            float off = off(here, at.x(), at.y(), facing);
            if (off > cone / 2f || gap > bestGap || gap == bestGap && off >= bestOff) {
                continue;
            }
            var word = game.contextWordOn(thing);
            if (word != null) {
                best = new Target(thing.getId().value(), word, at.x(), at.y());
                bestGap = gap;
                bestOff = off;
            }
        }
        // The map's own scenery too — a forest's trees are pieces, not things — by the ground rule under each (E96).
        for (var piece : world.sceneryWithin(here, reach)) {
            float gap = gap(here, piece.x(), piece.y());
            float off = off(here, piece.x(), piece.y(), facing);
            if (off > cone / 2f || gap > bestGap || gap == bestGap && off >= bestOff) {
                continue;
            }
            var word = game.groundWordAt(new Coord3D(piece.x(), piece.y(), 0f));
            if (word != null) {
                best = new Target(-1, word, piece.x(), piece.y());
                bestGap = gap;
                bestOff = off;
            }
        }
        return best;
    }

    private static float gap(Coord3D here, float x, float y) {
        float dx = x - here.x();
        float dy = y - here.y();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static float off(Coord3D here, float x, float y, float facing) {
        double turn = StrictMath.atan2(y - here.y(), x - here.x()) - facing;
        return (float) Math.abs(StrictMath.atan2(StrictMath.sin(turn), StrictMath.cos(turn)));
    }
}
