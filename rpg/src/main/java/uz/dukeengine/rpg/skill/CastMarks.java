package uz.dukeengine.rpg.skill;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.rpg.skill.SkillBook.CastMark;

/**
 * Where the last cast should be DRAWN, and what it should be drawn as.
 *
 * <p>The simulation deciding what a thing looks like would be the wrong way
 * round, and this does not: it decides only WHERE, which nothing but the
 * simulation knows, and hands over the name of a block in the art file for
 * the rest. The client is free to draw nothing at all.
 *
 * <p>A list, because one cast is not always one place. A blink is two -- the
 * spot he left and the spot he arrived at -- and half a blink is a teleport
 * with a bug.
 *
 * <p>It is left standing rather than cleared each frame. What keeps a ring
 * from being drawn twice is the FRAME beside it, which the client compares
 * against the last one it drew; clearing this would mean a cast landing in a
 * frame the client happened to miss simply never being seen.
 */
final class CastMarks {

    private final GameObject owner;
    private final List<CastMark> marks = new ArrayList<>();

    /**
     * The frame the marks were put there, which is NOT the book's last cast frame.
     *
     * <p>Two numbers that look like one and are not, and they were one until a
     * whirlwind proved it. The last cast frame is when the PLAYER last spoke,
     * and whatever chooses for the hero reads it to decide whether a cast
     * has superseded a move order. A whirlwind lands eight times off a single
     * press, and moving that number on at each landing would have cancelled the
     * order he gave -- so he would have stopped walking, mid-ultimate, without
     * anybody touching the mouse.
     *
     * <p>This one is only ever read by the client, which compares it against the
     * last one it drew so that one landing is drawn once.
     */
    private int frame = Integer.MIN_VALUE;

    CastMarks(GameObject owner) {
        this.owner = owner;
    }

    List<CastMark> marks() {
        return List.copyOf(marks);
    }

    int frame() {
        return frame;
    }

    /**
     * Where this cast wants drawing.
     *
     * <p>Worked out from the effect rather than written in the file, because the
     * effect is what knows: a blast round him is drawn round him whatever its
     * numbers say, and there is no useful sense in which one hero's nova is
     * centred somewhere else. The same argument the client's aiming ring makes for
     * the rings the player aims with -- and it means a hero added next month gets
     * his effects by naming a block, with nothing here to keep in step.
     */
    void rememberTheCast(Skill skill, Coord3D stood, Coord3D towards) {
        marks.clear();
        if (!skill.hasLook()) {
            return;
        }
        switch (skill.effect()) {
            // Round him, as wide as it actually reached -- the blast, or the pull.
            case AREA_DAMAGE, PULL -> markOn(skill, owner, skill.radius());
            // On him and no wider than the block says: these are about HIM, and a
            // ring the size of a room would claim they were about the room. His
            // rather than the floor's, for the same reason.
            case EMPOWER, GUARD, STRIKE, SKILLSHOT, WAVE -> markOn(skill, owner, 0f);
            // Where he put it -- for a meteor, the warning circle, as wide as the blast
            // that is coming. Where a skillshot LANDS is its own burst and is drawn by
            // whatever it ran into, which is the point of a lane.
            case AREA_AT_SPOT, METEOR -> mark(skill, towards == null ? owner.getPosition()
                    : Blows.withinReach(owner, towards, skill.range()), skill.radius());
            // Both ends. For a dash it is the two feet of the run; for a blink it
            // is the whole of what the skill looks like.
            case DASH, BLINK, SKEWER -> {
                // The spot he left is a place and stays one -- it is the dust he
                // kicked up, and dust does not follow the man.
                mark(skill, stood, 0f);
                markOn(skill, owner, 0f);
            }
        }
    }

    /** A mark on a patch of floor, which stays there whatever happens next. */
    private void mark(Skill skill, Coord3D at, float radius) {
        remember(new CastMark(skill.look(), at.x(), at.y(), radius, ObjectId.INVALID));
    }

    /** A mark on a creature, which is his and goes where he goes. */
    void markOn(Skill skill, GameObject on, float radius) {
        var at = on.getPosition();
        remember(new CastMark(skill.look(), at.x(), at.y(), radius, on.getId()));
    }

    /** The marks there were, replaced by {@code mark} alone: a lasting blast landing again. */
    void replaceWith(CastMark mark) {
        marks.clear();
        remember(mark);
    }

    private void remember(CastMark mark) {
        marks.add(mark);
        var world = owner.getWorld();
        frame = world == null ? frame : world.getFrame();
    }
}
