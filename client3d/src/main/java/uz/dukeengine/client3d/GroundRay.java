package uz.dukeengine.client3d;

import com.jme3.math.Vector3f;

/** Where a ray from the eye meets the ground the player sees: a hill's near face, a bridge's deck, or a level plane. */
final class GroundRay {

    private GroundRay() {
    }

    /**
     * The ray's meeting with an open deck, where it meets one before it meets the ground: a click on a bridge lands on
     * the bridge, at its height, so the order sent is one the simulation reads as on it ({@code PathGrid.floorAt}).
     */
    static Vector3f deckHit(Vector3f near, Vector3f dir, Vector3f ground,
                            java.util.List<uz.dukeengine.core.pathfind.Deck> decks) {
        var best = ground;
        float bestT = ground.subtract(near).dot(dir);
        for (var deck : decks) {
            if (!deck.isOpen()) {
                continue;
            }
            // Its plane, y = a·x + b·z + c in the scene's axes: the map's y is the scene's z.
            float c = deck.heightAt(0f, 0f);
            float a = deck.heightAt(1f, 0f) - c;
            float b = deck.heightAt(0f, 1f) - c;
            float facing = dir.y - a * dir.x - b * dir.z;
            if (Math.abs(facing) < 1e-6f) {
                continue;
            }
            float t = (a * near.x + b * near.z + c - near.y) / facing;
            var hit = near.add(dir.mult(t));
            if (t > 0f && t < bestT && deck.covers(hit.x, hit.z)) {
                best = hit;
                bestT = t;
            }
        }
        return best;
    }

    /**
     * Where the ray under the cursor first meets the ground: the surface the player sees, a hill's near face and not
     * the ground behind it.
     *
     * <p>The ray is walked from where it comes down to the highest ground, {@code step} across the map at a time, to
     * the first point at or under the ground, and the meeting is halved down between that point and the one before.
     * A plane met first and the hit settled onto the ground from there — what was here before — lands behind a hill:
     * the plane at zero lies past a rise the ray has already struck, and on a steep face the settling never settles.
     * Storeys and stairs are ground like any other, so a raised room is met on its floor and not behind it.
     *
     * @param step    how far across the map the ray is walked at a time: half a cell, so no rise is stepped over
     * @param floorAt how high the floor is at a point on the map, a stair's between its two storeys
     */
    static Vector3f groundHit(Vector3f near, Vector3f dir, GroundSpan span, float step,
                              java.util.function.BiFunction<Float, Float, Float> floorAt) {
        if (dir.y > -1e-6f) {
            return meetsAt(near, dir, 0f); // it never comes down
        }
        float from = Math.max(0f, (span.highest() - near.y) / dir.y);
        float to = Math.max(from, (span.lowest() - near.y) / dir.y);
        float across = (float) Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        float stride = across < 1e-6f ? to - from : step / across;
        float above = from;
        for (float t = from; ; t = Math.min(t + stride, to)) {
            var at = near.add(dir.mult(t));
            if (at.y <= floorAt.apply(at.x, at.z)) {
                return onTheGround(near, dir, above, t, floorAt);
            }
            if (t >= to) {
                return new Vector3f(at.x, floorAt.apply(at.x, at.z), at.z); // off the map, under all of it
            }
            above = t;
        }
    }

    /** The meeting between a point of the ray over the ground and a later one at or under it, halved down onto it. */
    private static Vector3f onTheGround(Vector3f near, Vector3f dir, float above, float under,
                                        java.util.function.BiFunction<Float, Float, Float> floorAt) {
        for (int halving = 0; halving < 12; halving++) {
            float middle = (above + under) / 2f;
            var at = near.add(dir.mult(middle));
            if (at.y <= floorAt.apply(at.x, at.z)) {
                under = middle;
            } else {
                above = middle;
            }
        }
        var at = near.add(dir.mult(under));
        return new Vector3f(at.x, floorAt.apply(at.x, at.z), at.z);
    }

    /**
     * Where a ray meets a level plane at {@code height}.
     *
     * <p>A ray above the horizon never meets it, so it is followed a long way
     * instead and the projection clamps the result to the map — which is what the
     * player sees anyway: the view running off the edge of the world.
     */
    private static Vector3f meetsAt(Vector3f near, Vector3f dir, float height) {
        float t = Math.abs(dir.y) < 1e-6f ? -1f : (height - near.y) / dir.y;
        return near.add(dir.mult(t < 0 ? 10_000f : t));
    }
}
