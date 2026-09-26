package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.rts.module.ContainModule;
import uz.dukeengine.rts.module.OrderListener;

/** A game's own order reaching a hold: an evacuate and an exit told as every other order is, after it is applied. */
class HoldOrdersToldTest {

    /** A game's module that hears its unit's orders, and what its hold held as it heard each. */
    static final class Ear extends Module implements OrderListener {
        final List<GameMessage> heard = new ArrayList<>();
        final List<Integer> holding = new ArrayList<>();
        final List<Integer> frames = new ArrayList<>();

        Ear(GameObject owner) {
            super(owner);
        }

        @Override
        public void onOrder(GameMessage order) {
            heard.add(order);
            var hold = getOwner().findModule(ContainModule.class);
            holding.add(hold == null ? -1 : hold.getPassengers().size());
            frames.add(getOwner().getWorld().getFrame());
        }
    }

    private record Field(DukeGame game, GamePlayer me, GameObject truck, GameObject soldier) {
    }

    private static Field field() {
        var game = DukeGame.create("holds").loadUnits(DukeGame.STARTER_UNITS).map(30, 30)
                .addUnits(List.of(RtsTemplate.named("Truck").module(new ActiveBody.Data(200f))
                        .module(new MoveUpdate.Data(30f)).module(new ContainModule.Data(4)).build()));
        var me = game.addPlayer("Me", Color.BLUE);
        game.localPlayer(me).spawn("Truck", me, 150f, 150f).spawn("Rifleman", me, 150f, 150f);
        game.runHeadless(1);
        var objects = game.getLogic().getObjects();
        var truck = objects.get(0);
        var soldier = objects.get(1);
        truck.findModule(ContainModule.class).load(soldier);
        return new Field(game, me, truck, soldier);
    }

    @Test
    void anEvacuateIsToldOnceTheFrameItIsGivenAfterTheHoldHasLetItsPassengersOut() {
        var field = field();
        var ear = new Ear(field.truck());
        field.truck().addModule(ear);
        field.game().postCommand(new GameMessage.Evacuate(field.me().getIndex(), field.truck().getId()));
        int frame = field.game().getSnapshot().frame();
        field.game().runHeadless(3);

        assertEquals(1, ear.heard.size(), "once");
        assertEquals(GameMessage.Evacuate.class, ear.heard.getFirst().getClass());
        assertEquals(List.of(0), ear.holding, "told after the engine let the soldier out");
        assertEquals(frame, (int) ear.frames.getFirst(), 1, "the frame it was given");
    }

    @Test
    void anExitIsToldToThePassengerItNames() {
        var field = field();
        var ear = new Ear(field.soldier());
        field.soldier().addModule(ear);
        field.game().postCommand(new GameMessage.ExitContainer(field.me().getIndex(), field.soldier().getId()));
        field.game().runHeadless(3);

        assertEquals(1, ear.heard.size(), "once, to the passenger named");
        assertEquals(GameMessage.ExitContainer.class, ear.heard.getFirst().getClass());
    }
}
