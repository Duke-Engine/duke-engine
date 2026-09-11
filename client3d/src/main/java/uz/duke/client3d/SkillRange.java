package uz.duke.client3d;

import uz.duke.core.math.Coord3D;

/**
 * How far one skill reaches, and what the player is choosing when he aims it.
 *
 * <p>The client keeps the press-then-click and has never heard of skills — see
 * {@link Hotkeys} — which was fine while all it had to do was forward the click.
 * It is not fine for drawing: "how far does this go", "can I hit that from here",
 * "which way will it fly" are questions about a <em>number</em>, and the number
 * belongs to the game. So the game names one of these per key, out of the same
 * data its skills are already written in, and the client draws it.
 *
 * <p>What is <em>not</em> here is what it looks like — see {@link RangeLook}.
 * These two are split because they change for different reasons: the reach of a
 * skill is a balance decision made per skill, and how a reach is drawn is a
 * decision made once for the whole game.
 *
 * @param key   the key that arms it
 * @param shape what the player is choosing, which decides what is drawn
 * @param reach how far it goes: to a target, to a spot, or down a lane. For a
 *              skill that goes off around the caster, how far around him
 * @param area  how wide the thing it leaves is — the blast at the spot, or the
 *              width of the lane. Unused by the shapes that have no area
 */
public record SkillRange(char key, Shape shape, float reach, float area) {

    /**
     * The five things a player can be asked to aim at, and the five drawings.
     *
     * <p>Named for what the player does rather than for the effect behind it: two
     * quite different skills that both ask "point at a creature within reach" want
     * the same picture, and a hero added next month should get the right drawing
     * by saying what kind of aiming his skill needs.
     */
    public enum Shape {

        /**
         * Point at a creature inside the ring. One ring, centred on him: the whole
         * question is "am I close enough".
         */
        AT_A_CREATURE,

        /**
         * Point at a spot inside the ring, and something lands there. Two rings —
         * how far he can throw it, and how much it covers where it falls, that one
         * following the cursor. Warcraft's, and still the clearest picture anyone
         * has drawn of "here, this big".
         */
        AT_A_SPOT,

        /**
         * Point a direction and let it fly. A lane out of him toward the cursor,
         * as long as the shot really travels and as wide as it really is: what it
         * hits is whatever is standing in that lane, so the lane is the honest
         * drawing of the decision.
         */
        DOWN_A_LANE,

        /** It goes off around him. One ring, and nothing to point at. */
        AROUND_HIM,

        /**
         * It only affects him. A ring drawn tight against him, which is the
         * picture of "this one is for me": the same language as the others, saying
         * the reach is himself.
         */
        ON_HIMSELF
    }

    /** Whether the player has to point at something before this can be cast. */
    public boolean needsAiming() {
        return shape == Shape.AT_A_CREATURE || shape == Shape.AT_A_SPOT
                || shape == Shape.DOWN_A_LANE;
    }

    /**
     * Whether it is drawn while the key is <em>held</em> rather than while it waits
     * for a click.
     *
     * <p>The two that need nothing pointed at used to go off the instant the key
     * went down, which gave the player no way to ask "how far does this actually
     * reach" without spending it. Holding the key shows him; letting go casts. It
     * costs nothing — a tap is still a cast — and it is the only way a skill with
     * no target can ever be looked at before it is used.
     */
    public boolean castOnRelease() {
        return !needsAiming();
    }

    /**
     * Where the skill would really go, given where he is and where the player is
     * pointing.
     *
     * <p>Pulled back to the edge of the ring rather than refused. Pointing past
     * the edge means "as far that way as you can", which is what the player
     * intends every time, and it is what makes the ring a guide rather than a
     * fence — Warcraft does the same and nobody has ever had to be told.
     *
     * <p>The height is the spot's, not his: a skill thrown from an upper floor
     * lands on the floor it was aimed at.
     */
    public Coord3D within(Coord3D from, Coord3D wanted) {
        if (from == null || wanted == null) {
            return wanted;
        }
        float dx = wanted.x() - from.x();
        float dy = wanted.y() - from.y();
        float away = (float) Math.hypot(dx, dy);
        if (away <= reach || away <= 0.0001f) {
            return wanted;
        }
        float share = reach / away;
        return new Coord3D(from.x() + dx * share, from.y() + dy * share, wanted.z());
    }

    /** Whether the pointer is inside the ring at all. */
    public boolean inReach(Coord3D from, Coord3D wanted) {
        if (from == null || wanted == null) {
            return false;
        }
        return Math.hypot(wanted.x() - from.x(), wanted.y() - from.y()) <= reach;
    }
}
