package uz.duke.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.scene.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import uz.duke.core.math.Coord3D;

/**
 * The red circle that flashes round a creature an attack was ordered on.
 *
 * <p>The other half of {@link Chevrons}, and deliberately a different picture. A
 * walking order is answered where the player pointed, so three arrowheads closing
 * on that spot is the right answer: it says <em>there</em>. An attack order is
 * given to <em>somebody</em>, and the question it has to answer is which one —
 * which the same arrowheads, closing on a creature that is about to walk out from
 * under them, answer badly.
 *
 * <p>So: a ring round the creature, going hard on and hard off twice. That is the
 * oldest way of picking one thing out of a crowd of things and still the one the
 * eye finds fastest; a gentle fade in the middle of a fight is a thing nobody
 * sees. How many blinks and how wide the ring are the game's, in
 * {@link OrderMark}.
 *
 * <p>Pooled the same way, for the same reason, and with the same promise: nothing
 * is built while the game runs, and a mark that has finished is hidden rather than
 * detached, so the scene settles at however many were on screen at once.
 */
final class AttackFlash {

    private final AssetManager assets;
    private final Node root;
    private final OrderMark look;
    private final List<GroundRing> pool = new ArrayList<>();

    AttackFlash(AssetManager assets, Node root, OrderMark look) {
        this.assets = assets;
        this.root = root;
        this.look = look == null ? OrderMark.DEFAULT : look;
    }

    /** Flash every attack mark that is still alive, and hide the rest. */
    void show(List<OrderMarkers.Marker> marks, float now, BiFunction<Float, Float, Float> floorAt) {
        int used = 0;
        for (var marker : marks) {
            if (marker.kind() != OrderMarkers.Kind.ATTACK) {
                continue;
            }
            float age = now - marker.bornAt();
            if (age < 0f || look.spent(age)) {
                continue;
            }
            var ring = borrow(used++);
            float lit = look.blinkAt(age);
            if (lit <= 0f) {
                // Off is half the point of a blink, so it really is off rather
                // than dim: the gap is what the eye catches.
                ring.hide();
                continue;
            }
            ring.show(new Coord3D(marker.x(), marker.y(), 0f), look.ringRadius(), look.height(),
                    look.attackColour(), lit, lit * 0.22f, floorAt);
        }
        for (int spare = used; spare < pool.size(); spare++) {
            pool.get(spare).hide();
        }
    }

    /** Hide everything — a new world has no orders outstanding in it. */
    void clear() {
        for (var ring : pool) {
            ring.hide();
        }
    }

    /** How many rings the pool has had to make. Package-private so it can be checked. */
    int madeSoFar() {
        return pool.size();
    }

    private GroundRing borrow(int index) {
        while (pool.size() <= index) {
            pool.add(new GroundRing(assets, root, look.width() * 0.5f, 64, look.brightness()));
        }
        return pool.get(index);
    }
}
