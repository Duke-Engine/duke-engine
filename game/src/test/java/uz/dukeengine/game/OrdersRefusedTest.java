package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.message.CombatOrder;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.rts.module.OrderListener;

/** A unit whose module refuses an order is left out of it, neither moved nor told; the others in it go. */
class OrdersRefusedTest {

    /** A gunship's {@code ForbidPlayerCommands}: deaf to every order, and noting any it is told. */
    static final class Deaf extends Module implements OrderListener {
        final List<Command> heard = new ArrayList<>();

        Deaf(GameObject owner) {
            super(owner);
        }

        @Override
        public void onOrder(Command order) {
            heard.add(order);
        }

        @Override
        public boolean refuses(Command order) {
            return true;
        }
    }

    @Test
    void aRefusingUnitStaysWhereItIsAndHearsNothingWhileTheRestOfTheGroupGoes() {
        var game = DukeGame.create("deaf").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS).map(60, 40);
        var usa = game.addPlayer("USA", Color.BLUE);
        game.localPlayer(usa);
        game.spawn("Rifleman", usa, 100f, 100f);
        game.spawn("Rifleman", usa, 100f, 140f);
        game.runHeadless(1);
        var objects = game.getLogic().getObjects();
        var deaf = objects.getFirst();
        var willing = objects.get(1);
        var module = new Deaf(deaf);
        deaf.addModule(module);
        var deafAt = deaf.getPosition();
        var willingAt = willing.getPosition();
        var both = List.of(deaf.getId(), willing.getId());

        game.postCommand(new CombatOrder.MoveTo(usa.getIndex(), both, new Coord3D(400f, 300f, 0f)));
        game.postCommand(new GameMessage.AttackMove(usa.getIndex(), both, new Coord3D(400f, 300f, 0f)));
        game.runHeadless(60);

        assertEquals(deafAt, deaf.getPosition(), "the refusing unit is not moved");
        assertTrue(module.heard.isEmpty(), "nor told the orders it refused");
        assertNotEquals(willingAt, willing.getPosition(), "the other unit named goes");
    }
}
