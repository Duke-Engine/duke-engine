package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

/**
 * An order given to a group reaches every unit in it.
 *
 * <p>Worth pinning down now, while the player commands a single hero: a mechanism
 * that quietly only worked for one would not say so until there were companions to
 * lose, and by then the fault would look like a pathfinding problem rather than a
 * missing order.
 */
class FormationTest {

    @Test
    void everyUnitGetsSomewhereOfItsOwn() {
        for (int count : new int[] {1, 2, 3, 5, 9, 12}) {
            var spots = Formation.spread(count, 200f, 150f);

            assertEquals(count, spots.size(), count + " units should get " + count + " places");
            var distinct = new HashSet<>(spots);
            assertEquals(count, distinct.size(),
                    "no two of " + count + " units should be sent to the same spot");
        }
    }

    @Test
    void aLoneUnitIsSentExactlyWhereTheClickWas() {
        var spots = Formation.spread(1, 200f, 150f);

        assertEquals(200f, spots.get(0).x(), 0.001f);
        assertEquals(150f, spots.get(0).y(), 0.001f);
    }

    /** The group gathers on the click rather than trailing off away from it. */
    @Test
    void theGroupIsCentredOnTheOrderedPoint() {
        var spots = Formation.spread(9, 200f, 150f);

        float averageX = 0f;
        float averageY = 0f;
        for (var spot : spots) {
            averageX += spot.x() / spots.size();
            averageY += spot.y() / spots.size();
        }
        assertEquals(200f, averageX, 0.001f, "the group's centre should be the click itself");
        assertEquals(150f, averageY, 0.001f);
    }

    @Test
    void unitsAreSpreadFarEnoughApartToStandSeparately() {
        var spots = Formation.spread(4, 0f, 0f);

        for (int i = 0; i < spots.size(); i++) {
            for (int j = i + 1; j < spots.size(); j++) {
                float dx = spots.get(i).x() - spots.get(j).x();
                float dy = spots.get(i).y() - spots.get(j).y();
                assertTrue(Math.sqrt(dx * dx + dy * dy) >= Formation.SPACING - 0.001f,
                        "neighbours should be at least a spacing apart");
            }
        }
    }

    @Test
    void anEmptySelectionAsksForNothing() {
        assertTrue(Formation.spread(0, 10f, 10f).isEmpty());
    }
}
