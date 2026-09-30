package uz.dukeengine.combat.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.message.CombatOrder;
import uz.dukeengine.combat.message.OrderSource;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.ObjectId;

/** The orders every side gives, on the wire as they always were: a recording or an older peer reads them as ever. */
class CombatOrderCodecTest {

    private static final List<ObjectId> UNITS = List.of(new ObjectId(3), new ObjectId(7));

    @Test
    void eachOrderIsTheLineItAlwaysWasAndReadsBackTheSame() {
        var move = new CombatOrder.MoveTo(1, UNITS, new Coord3D(10.5f, 20.25f, 0f), true);
        var attack = new CombatOrder.AttackObject(1, UNITS, new ObjectId(9));
        var locked = new CombatOrder.AttackObject(2, UNITS, new ObjectId(9), true, OrderSource.GAME, 1);
        var stop = new CombatOrder.StopMoving(1, UNITS);

        assertEquals("MOVE,1,3:7,10.5,20.25,0.0,click", CombatOrderCodec.encode(move));
        assertEquals("ATTACK,1,3:7,9", CombatOrderCodec.encode(attack));
        assertEquals("ATTACK,2,3:7,9,forced,GAME,1", CombatOrderCodec.encode(locked));
        assertEquals("STOP,1,3:7", CombatOrderCodec.encode(stop));
        for (var order : List.of(move, attack, locked, stop)) {
            assertEquals(order, CombatOrderCodec.decode(CombatOrderCodec.encode(order)));
        }
    }

    @Test
    void aLineOfAnotherKindIsLeftToTheSidesOwnCodec() {
        assertNull(CombatOrderCodec.decode("QUEUE,1,5,Tank"));
        assertEquals(List.of(), CombatOrderCodec.parseIds(""));
    }
}
