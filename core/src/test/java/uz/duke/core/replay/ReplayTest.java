package uz.duke.core.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.core.GameLogic;
import uz.duke.core.TestCommand;
import uz.duke.core.math.Coord3D;
import uz.duke.core.message.Command;
import uz.duke.core.module.ActiveBody;
import uz.duke.core.module.ModuleFactory;
import uz.duke.core.module.MoveUpdate;
import uz.duke.core.network.CommandPacket;
import uz.duke.core.network.PacketCodec;
import uz.duke.core.thing.ObjectId;
import uz.duke.core.thing.ThingFactory;
import uz.duke.core.thing.ThingTemplate;

/**
 * A recording is the starting conditions plus the commands — nothing about the
 * world itself. Playing it back recomputes the world, which is only possible if
 * the simulation is deterministic, and is therefore also how that claim is tested.
 */
class ReplayTest {

    private static final ThingTemplate RUNNER = ThingTemplate.named("Runner")
            .module(new ActiveBody.Data(100f))
            .module(new MoveUpdate.Data(15f))
            .build();

    /** A world with a mind of its own: it issues commands as well as obeying them. */
    static final class ScriptedLogic extends GameLogic {
        ScriptedLogic() {
            super(newFactory());
        }

        private static ThingFactory newFactory() {
            var factory = new ThingFactory(ModuleFactory.withDefaults());
            factory.addTemplate(RUNNER);
            return factory;
        }

        @Override
        protected void onCommand(Command command) {
            if (command instanceof TestCommand.Move move) {
                for (var id : move.units()) {
                    var unit = findObject(id);
                    if (unit != null) {
                        unit.findModule(MoveUpdate.class).moveTo(move.destination());
                    }
                }
            }
        }

        @Override
        protected void simulate() {
            // A scripted "AI": every 40 frames it orders unit 2 somewhere. On
            // playback it will do this again, which is exactly the double-issue
            // the replay has to be immune to.
            if (getFrame() % 40 == 39) {
                issueCommand(new TestCommand.Move(2, List.of(new ObjectId(2)),
                        new Coord3D(20f + getFrame(), 90f, 0f)));
            }
        }
    }

    /** The command wire format of a game with only {@link TestCommand}s. */
    static final class TestCodec implements PacketCodec {
        @Override
        public String encode(CommandPacket packet) {
            var out = new StringBuilder()
                    .append(packet.frame()).append(';').append(packet.playerIndex());
            for (var command : packet.commands()) {
                var move = (TestCommand.Move) command;
                out.append(';').append(move.playerIndex())
                        .append(',').append(move.units().get(0).value())
                        .append(',').append(Float.toString(move.destination().x()))
                        .append(',').append(Float.toString(move.destination().y()));
            }
            return out.toString();
        }

        @Override
        public CommandPacket decode(String line) {
            var parts = line.split(";");
            var commands = new ArrayList<Command>();
            for (int i = 2; i < parts.length; i++) {
                var f = parts[i].split(",");
                commands.add(new TestCommand.Move(Integer.parseInt(f[0]),
                        List.of(new ObjectId(Integer.parseInt(f[1]))),
                        new Coord3D(Float.parseFloat(f[2]), Float.parseFloat(f[3]), 0f)));
            }
            return new CommandPacket(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), commands);
        }
    }

    private static final TestCodec CODEC = new TestCodec();

    private static ScriptedLogic newWorld() {
        var logic = new ScriptedLogic();
        logic.init();
        logic.spawn(RUNNER, new Coord3D(10f, 10f, 0f), 1);
        logic.spawn(RUNNER, new Coord3D(50f, 10f, 0f), 2);
        return logic;
    }

    /** Play a game the same way twice, so the recording and the replay line up. */
    private static void playFor(GameLogic logic, int frames, Replay replay) {
        for (int frame = 0; frame < frames; frame++) {
            if (replay != null) {
                replay.beforeStep(logic);
            } else if (frame % 25 == 0) {
                logic.issueCommand(new TestCommand.Move(1, List.of(new ObjectId(1)),
                        new Coord3D(100f + frame, 30f, 0f)));
            }
            logic.update();
        }
    }

    private static String record(int frames) {
        var logic = newWorld();
        var recorder = new ReplayRecorder(CODEC);
        logic.setFrameLog(recorder);
        playFor(logic, frames, null);
        return recorder.toText();
    }

    @Test
    void aRecordedGamePlaysBackToTheSameWorld() {
        var text = record(200);

        var replay = Replay.parse(text, CODEC);
        var logic = newWorld();
        playFor(logic, 200, replay);

        assertNull(replay.getMismatch(),
                () -> "the simulation is not deterministic: " + replay.getMismatch());
        assertTrue(replay.getLastFrame() >= 180, "the recording should cover the whole game");

        // And the same world, not merely the same checkpoints.
        var original = newWorld();
        playFor(original, 200, null);
        assertEquals(original.checksum(), logic.checksum());
    }

    @Test
    void aRecordingIsJustCommandsAndCheckpoints() {
        var text = record(60);
        var lines = text.strip().split("\n");

        assertEquals(ReplayRecorder.HEADER, lines[0]);
        assertTrue(text.contains("\nK "), "checkpoints are written");
        assertTrue(text.contains("\nC "), "so are the frames that had input");
        assertTrue(lines.length < 60,
                "a recording holds input, not a frame-by-frame copy of the world; got "
                        + lines.length + " lines for 60 frames");
    }

    @Test
    void aWorldThatDivergesIsCaughtAtTheFrameItDivergedOn() {
        var text = record(120);
        // Forge the checkpoint at frame 60: the "recording" now claims a world the
        // replay will not produce, which is what a determinism bug looks like.
        var forged = new ArrayList<String>();
        for (var line : text.strip().split("\n")) {
            forged.add(line.startsWith("K 0 60 ") ? "K 0 60 999999" : line);
        }
        var replay = Replay.parse(String.join("\n", forged), CODEC);

        playFor(newWorld(), 120, replay);

        var mismatch = replay.getMismatch();
        assertNotNull(mismatch, "a forged checkpoint must not pass");
        assertEquals(60, mismatch.frame());
        assertEquals(999999L, mismatch.recorded());
    }

    @Test
    void theSimulationsOwnCommandsAreNotAppliedTwice() {
        // The scripted order at frame 39 is both regenerated by the replayed world
        // and present in the recording; only one of them may take effect.
        var text = record(120);
        var replay = Replay.parse(text, CODEC);
        var logic = newWorld();
        playFor(logic, 120, replay);

        assertNull(replay.getMismatch(),
                "a world that issues its own commands must still replay exactly");
    }

    @Test
    void somethingThatIsNotARecordingIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Replay.parse("hello\n", CODEC));
        assertThrows(IllegalArgumentException.class, () -> Replay.parse("", CODEC));
    }
}
