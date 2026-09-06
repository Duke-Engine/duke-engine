package uz.duke.rts.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.core.GameLogic;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.ActiveBody;
import uz.duke.core.module.MoveUpdate;
import uz.duke.core.network.CommandPacket;
import uz.duke.core.network.LockstepDriver;
import uz.duke.core.network.LockstepScheduler;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ObjectId;
import uz.duke.core.thing.ThingFactory;
import uz.duke.core.thing.ThingTemplate;
import uz.duke.rts.module.RtsModules;
import uz.duke.rts.RtsSimulation;
import uz.duke.rts.message.GameMessage;

/**
 * Proves the multiplayer core: two peers, each driving their own simulation and
 * exchanging only commands through {@link LockstepDriver}, stay bit-identical
 * frame for frame (verified by {@link GameLogic#checksum()}).
 */
class LockstepDriverTest {

    static final class PeerLogic extends RtsSimulation {
        PeerLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void onRtsCommand(GameMessage command) {
            if (command instanceof GameMessage.MoveTo move) {
                for (var id : move.units()) {
                    var unit = findObject(id);
                    if (unit != null && unit.findModule(MoveUpdate.class) != null) {
                        unit.findModule(MoveUpdate.class).moveTo(move.destination());
                    }
                }
            }
        }

        @Override
        protected void simulate() {
        }
    }

    /** Fans every broadcast packet out to all registered peers. */
    static final class InMemoryTransport {
        private final List<LockstepDriver> peers = new ArrayList<>();

        void register(LockstepDriver driver) {
            peers.add(driver);
        }

        void broadcast(CommandPacket packet) {
            for (var peer : peers) {
                peer.receive(packet);
            }
        }
    }

    private static PeerLogic newPeer() {
        var thingFactory = new ThingFactory(RtsModules.withDefaults());
        var unit = ThingTemplate.named("Unit")
                .module("ActiveBody", new ActiveBody.Data(100f))
                .module("MoveUpdate", new MoveUpdate.Data(30f))
                .build();
        thingFactory.addTemplate(unit);

        var logic = new PeerLogic(thingFactory);
        logic.init();
        logic.getPlayerList().addPlayer("P1");
        logic.getPlayerList().addPlayer("P2");

        var u1 = logic.createObject(unit); // id 1, player 1
        u1.setPlayerIndex(1);
        u1.setPosition(new Coord3D(0f, 0f, 0f));
        var u2 = logic.createObject(unit); // id 2, player 2
        u2.setPlayerIndex(2);
        u2.setPosition(new Coord3D(50f, 0f, 0f));
        return logic;
    }

    @Test
    void twoPeersStayInSyncExchangingOnlyCommands() {
        int delay = 2;
        var transport = new InMemoryTransport();

        var logicA = newPeer();
        var logicB = newPeer();
        var schedulerA = new LockstepScheduler(List.of(1, 2));
        var schedulerB = new LockstepScheduler(List.of(1, 2));
        schedulerA.init();
        schedulerB.init();

        var driverA = new LockstepDriver(logicA, schedulerA, 1, delay, transport::broadcast);
        var driverB = new LockstepDriver(logicB, schedulerB, 2, delay, transport::broadcast);
        transport.register(driverA);
        transport.register(driverB);
        driverA.prime();
        driverB.prime();

        for (int round = 0; round < 40; round++) {
            // Peer A's player issues a move at round 5; peer B's player at round 12.
            if (round == 5) {
                driverA.issueLocal(new GameMessage.MoveTo(1, List.of(new ObjectId(1)), new Coord3D(20f, 0f, 0f)));
            }
            if (round == 12) {
                driverB.issueLocal(new GameMessage.MoveTo(2, List.of(new ObjectId(2)), new Coord3D(10f, 0f, 0f)));
            }

            boolean advancedA = driverA.tick();
            boolean advancedB = driverB.tick();
            assertTrue(advancedA && advancedB, "both peers should advance in lock-step at round " + round);

            assertEquals(logicA.checksum(), logicB.checksum(),
                    "peers desynced at frame " + logicA.getFrame());
        }

        // Sanity: the commands actually took effect (non-vacuous sync).
        GameObject a1 = logicA.findObject(new ObjectId(1));
        GameObject a2 = logicA.findObject(new ObjectId(2));
        assertTrue(a1.getPosition().x() > 0f, "player 1's unit should have advanced");
        assertTrue(a2.getPosition().x() < 50f, "player 2's unit should have advanced");
    }
}
