package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.message.GameOrder;
import uz.dukeengine.core.Flavour;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.ModuleFactory;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.network.CommandPacket;
import uz.dukeengine.core.network.PacketCodec;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.core.thing.ThingTemplateLoader;
import uz.dukeengine.rts.RtsFlavour;

/**
 * The runtime runs a kind of game it does not know: the one on the classpath where the game names none, the one it
 * names where it does — here a kind that is no RTS, whose world applies no set of its own, a match of it played
 * headless with the game's own commands and word orders heard.
 */
class KindsOfGameTest {

    /** A world of no kind: every command one it does not know. */
    private static final class Plain extends GameLogic {
        Plain() {
            super(new ThingFactory(ModuleFactory.withDefaults()));
        }

        @Override
        protected void onCommand(Command command) {
            onOtherCommand(command);
        }

        @Override
        protected void simulate() {
        }
    }

    /** A kind that is no RTS: a plain world, no records of its own, no wire. */
    private static final class PlainFlavour implements Flavour {
        @Override
        public GameLogic newWorld() {
            return new Plain();
        }

        @Override
        public void templates(ThingTemplateLoader loader) {
        }

        @Override
        public PacketCodec codec() {
            return new PacketCodec() {
                @Override
                public String encode(CommandPacket packet) {
                    throw new UnsupportedOperationException("a plain game is played on one machine");
                }

                @Override
                public CommandPacket decode(String line) {
                    throw new UnsupportedOperationException("a plain game is played on one machine");
                }
            };
        }

        @Override
        public boolean carries(Command command) {
            return false;
        }
    }

    /** A command of the game's own. */
    private record Wave(int playerIndex) implements Command {
    }

    @Test
    void aGameThatNamesNoKindRunsOnTheOneOnTheClasspath() {
        assertInstanceOf(RtsFlavour.class, DukeGame.create("found").flavour());
        assertInstanceOf(RtsFlavour.class, Flavour.found());
    }

    @Test
    void aKindThatIsNoRtsPlaysAMatch() {
        var game = DukeGame.create("plain", new PlainFlavour()).map(20, 20)
                .addUnits(List.of(ThingTemplate.named("Walker").module(new ActiveBody.Data(10f))
                        .module(new MoveUpdate.Data(30f)).build()));
        var me = game.addPlayer("Me", Color.BLUE);
        var waves = new ArrayList<Command>();
        var words = new ArrayList<String>();
        game.localPlayer(me).spawn("Walker", me, 50f, 50f)
                .onCommand(waves::add)
                .onOrder(order -> words.add(order.word()));
        game.runHeadless(1);

        game.postCommand(new Wave(me.getIndex()));
        game.postCommand(new GameOrder(me.getIndex(), "Hail", List.of(), null, null, 0L));
        game.runHeadless(3);

        var walker = game.getSnapshot().units().getFirst();
        assertEquals("Walker", walker.templateName());
        assertFalse(walker.structure(), "a kind that says nothing of structures");
        assertEquals(-1, walker.productionQueue(), "nor of making things");
        assertEquals(0, game.getSnapshot().localPlayerMoney());
        assertEquals(List.of(new Wave(me.getIndex())), waves, "the game's own command, heard once");
        assertEquals(List.of("Hail"), words, "and its word order");
    }

    @Test
    void askingForAnotherKindThanTheGameIsSaysSo() {
        var game = DukeGame.create("plain", new PlainFlavour());
        assertThrows(IllegalStateException.class, () -> game.flavour(RtsFlavour.class));
    }
}
