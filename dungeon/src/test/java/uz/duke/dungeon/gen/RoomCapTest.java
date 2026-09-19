package uz.duke.dungeon.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.dungeon.content.Content;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.content.ShippedBlock;

/**
 * A kind given a cap is never placed more times than that in one room.
 *
 * <p>Proved on the shipped file with the Skeleton Mage made far the most likely thing
 * to fill a room -- so that without its cap the rooms really would fill with them, and
 * the cap is what stops it rather than luck.
 */
class RoomCapTest {

    private static final String MAGE = "SkeletonMage";

    /** The shipped file, with the mage at every depth, a thousand times as likely, and this cap. */
    private static DungeonSettings mageEverywhere(int maxPerRoom) {
        var shipped = ShippedBlock.of(MAGE);
        var mage = shipped.with("MinDepth", 1).with("Weight", 10000).with("MaxPerRoom", maxPerRoom);
        return DungeonSettings.parse(Content.data().replace(shipped.text(), mage.text()));
    }

    /** The most mages any one room of this floor holds. */
    private static int mostInOneRoom(GeneratedDungeon floor) {
        int most = 0;
        for (var room : floor.rooms()) {
            int here = 0;
            for (var monster : floor.monsters()) {
                int x = monster.at().cellX();
                int y = monster.at().cellY();
                if (monster.kind().equals(MAGE) && x >= room.x() && x < room.x() + room.w()
                        && y >= room.y() && y < room.y() + room.h()) {
                    here++;
                }
            }
            most = Math.max(most, here);
        }
        return most;
    }

    @Test
    void noRoomHoldsMoreOfAKindThanItsCap() {
        var capped = mageEverywhere(1);
        var uncapped = mageEverywhere(0);
        int cappedMost = 0;
        int uncappedMost = 0;
        int otherKinds = 0;
        for (long seed = 1; seed <= 12; seed++) {
            var floor = DungeonGenerator.generate(seed, capped, 1);
            cappedMost = Math.max(cappedMost, mostInOneRoom(floor));
            otherKinds += (int) floor.monsters().stream()
                    .filter(monster -> !monster.kind().equals(MAGE)).count();
            uncappedMost = Math.max(uncappedMost,
                    mostInOneRoom(DungeonGenerator.generate(seed, uncapped, 1)));
        }

        assertEquals(1, cappedMost, "a cap of one, and a room held " + cappedMost);
        assertTrue(uncappedMost > 1, "without the cap the same rooms should fill with them,"
                + " which is what makes the cap mean anything -- most was " + uncappedMost);
        assertTrue(otherKinds > 0, "and a capped room is still filled, with the other kinds");
    }
}
