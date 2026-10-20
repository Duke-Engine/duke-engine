package uz.dukeengine.rpg.skill;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.World;
import uz.dukeengine.rpg.skill.SkillBook.CastMark;

/** What goes off over an area: an {@code AREA_DAMAGE} round him, lasting or not, and an {@code AREA_AT_SPOT}. */
final class Blasts {

    private final GameObject owner;
    private final Blows blows;
    private final CastMarks marks;

    /**
     * An {@code AREA_DAMAGE} that has not finished happening.
     *
     * <p>What it is worth is worked out when it is cast rather than when it lands,
     * so a whirlwind is not sharpened halfway through by the level he reaches
     * during it — and so that it goes on being his even if something about him
     * changes while it turns.
     */
    private int lastingFrames;
    private int lastingEvery;
    private int lastingNext;
    private float lastingDamage;
    private float lastingRadius;

    /** What it is drawn as, so each landing is drawn and not only the first. */
    private String lastingLook = "";

    Blasts(GameObject owner, Blows blows, CastMarks marks) {
        this.owner = owner;
        this.blows = blows;
        this.marks = marks;
    }

    boolean around(Skill skill, int level, World world) {
        float each = blows.damageOf(skill, level);
        blows.strikeAll(blows.enemiesWithin(world, skill.radius()), each);
        // A blast that also chills, if the file asks for one. A third
        // effect would have been the obvious move and the wrong one: this
        // IS the area skill, with one more thing true of it, and the
        // knight's whirlwind is untouched by having said nothing about it.
        blows.chill(world, owner.getPosition(), skill.radius(), skill.slowFrames());
        if (skill.lasts()) {
            // It has landed once already; the rest is the file's business.
            // Re-casting refreshes rather than stacking, as EMPOWER does.
            lastingFrames = skill.durationFrames();
            lastingEvery = skill.tickFrames();
            lastingNext = skill.tickFrames();
            lastingDamage = each;
            lastingRadius = skill.radius();
            lastingLook = skill.look();
        }
        return true;
    }

    boolean atSpot(Skill skill, int level, World world, Coord3D towards) {
        if (towards == null) {
            return false; // it has to be put somewhere; nowhere is not a spot
        }
        // Pulled back to the edge of his reach rather than refused. The
        // client draws the ring and clamps the click to it, so a click
        // outside is a player asking for "as far that way as I can" --
        // and the two have to agree or the picture is a lie.
        var spot = Blows.withinReach(owner, towards, skill.range());
        float each = blows.damageOf(skill, level);
        blows.strikeAll(blows.enemiesWithin(world, spot, skill.radius()), each);
        // And leaves whoever it caught dragging his feet, if the file asks: the
        // mage's frost nova, dropped where he points rather than round himself.
        blows.chill(world, spot, skill.radius(), skill.slowFrames());
        Blows.fired(owner, spot);
        return true;
    }

    /**
     * A lasting {@code AREA_DAMAGE}, landing again.
     *
     * <p>It follows him, which is what makes it a whirlwind rather than a fire on
     * the floor: it is measured from wherever he is standing each time it lands,
     * so walking into a second group carries it with him and walking out of a
     * fight ends it for them.
     */
    void turnTheWhirlwind() {
        if (lastingFrames <= 0) {
            return;
        }
        lastingFrames--;
        if (--lastingNext > 0) {
            return;
        }
        lastingNext = lastingEvery;
        if (owner == null || owner.getWorld() == null) {
            return;
        }
        blows.strikeAll(blows.enemiesWithin(owner.getWorld(), lastingRadius), lastingDamage);
        // And drawn each time it lands, not only the first. A whirlwind that
        // opened one ring and then turned in silence for four seconds is a skill
        // the player has to count frames to know is still going.
        //
        // The frame is moved on with it, since that is what the client compares
        // against to know it has not drawn this one already.
        if (lastingLook != null && !lastingLook.isBlank()) {
            marks.replaceWith(new CastMark(lastingLook, owner.getPosition().x(),
                    owner.getPosition().y(), lastingRadius, owner.getId()));
        }
    }
}
