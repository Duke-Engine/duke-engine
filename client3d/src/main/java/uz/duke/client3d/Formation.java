package uz.duke.client3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Where each of a group of units should stand when they are all sent to one spot.
 *
 * <p>A move order names a point, but a group cannot all occupy it. Sent to the
 * same coordinate they arrive, collide, shove each other aside and mill about,
 * which reads as the order having half worked. Spreading them over a small grid
 * around the click gives every unit somewhere of its own to stand.
 *
 * <p>Pure arithmetic, so the property that matters — <em>every</em> selected unit
 * gets a destination, and no two get the same one — can be checked directly. That
 * property is why this is worth having as a class rather than a loop inside the
 * click handler: today the player commands one hero, and a mechanism that quietly
 * only worked for one would not say so until there were companions to lose.
 */
final class Formation {

    /** World units between neighbours — enough that bodies do not overlap. */
    static final float SPACING = 5f;

    record Spot(float x, float y) {
    }

    private Formation() {
    }

    /**
     * One standing place per unit, in a rough square centred on the ordered point.
     *
     * <p>Both axes centre on the <em>last</em> index rather than the count — rows
     * minus one, columns minus one. Centring on the count instead shifts the whole
     * group half a space off the click, which for a single unit meant clicking a
     * spot and watching your one hero walk to somewhere slightly else.
     */
    static List<Spot> spread(int count, float centreX, float centreY) {
        var spots = new ArrayList<Spot>(count);
        if (count <= 0) {
            return spots;
        }
        int columns = (int) Math.ceil(Math.sqrt(count));
        int rows = (int) Math.ceil((float) count / columns);
        for (int i = 0; i < count; i++) {
            float offsetX = (i % columns - (columns - 1) / 2f) * SPACING;
            float offsetY = (i / columns - (rows - 1) / 2f) * SPACING;
            spots.add(new Spot(centreX + offsetX, centreY + offsetY));
        }
        return spots;
    }
}
