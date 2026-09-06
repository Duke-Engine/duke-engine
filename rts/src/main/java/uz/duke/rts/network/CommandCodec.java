package uz.duke.rts.network;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import uz.duke.core.math.Coord3D;
import uz.duke.core.message.Command;
import uz.duke.core.network.CommandPacket;
import uz.duke.core.network.PacketCodec;
import uz.duke.core.thing.ObjectId;
import uz.duke.rts.message.GameMessage;

/**
 * The RTS wire format: encodes a {@link CommandPacket} of {@link GameMessage}s
 * to a single line and back.
 *
 * <p>Lock-step ships only commands, so this is the entire serialization surface
 * the network layer needs. The format is deliberately simple and exact: integers
 * and {@link Float#toString} floats (which round-trip to identical bits, keeping
 * the simulation deterministic across peers). The {@link GameMessage} sealed
 * hierarchy is matched exhaustively, so a new command type is a compile error
 * here until it is given a wire form.
 *
 * <p>Field separators: {@code ;} between packet fields, {@code |} between
 * commands, {@code ,} between a command's parts, {@code :} between object ids.
 */
public final class CommandCodec implements PacketCodec {

    /** The shared instance — the codec is stateless. */
    public static final CommandCodec INSTANCE = new CommandCodec();

    private CommandCodec() {
    }

    @Override
    public String encode(CommandPacket packet) {
        var commands = packet.commands().stream()
                .map(CommandCodec::encodeCommand)
                .collect(Collectors.joining("|"));
        return packet.frame() + ";" + packet.playerIndex() + ";" + commands;
    }

    private static String encodeCommand(Command command) {
        if (!(command instanceof GameMessage message)) {
            throw new IllegalArgumentException(
                    "not an RTS command: " + command.getClass().getName());
        }
        return switch (message) {
            case GameMessage.MoveTo m -> "MOVE," + m.playerIndex() + "," + ids(m.units())
                    + "," + Float.toString(m.destination().x())
                    + "," + Float.toString(m.destination().y())
                    + "," + Float.toString(m.destination().z());
            case GameMessage.AttackObject a -> "ATTACK," + a.playerIndex() + "," + ids(a.units())
                    + "," + a.target().value();
            case GameMessage.StopMoving s -> "STOP," + s.playerIndex() + "," + ids(s.units());
            case GameMessage.QueueProduction q -> "QUEUE," + q.playerIndex() + ","
                    + q.factory().value() + "," + q.unitTemplate();
            case GameMessage.SetRallyPoint r -> "RALLY," + r.playerIndex() + ","
                    + r.factory().value()
                    + "," + Float.toString(r.point().x())
                    + "," + Float.toString(r.point().y())
                    + "," + Float.toString(r.point().z());
        };
    }

    @Override
    public CommandPacket decode(String line) {
        var fields = line.split(";", -1);
        int frame = Integer.parseInt(fields[0]);
        int playerIndex = Integer.parseInt(fields[1]);
        var commands = new ArrayList<Command>();
        if (fields.length > 2 && !fields[2].isEmpty()) {
            for (var encoded : fields[2].split("\\|")) {
                commands.add(decodeCommand(encoded));
            }
        }
        return new CommandPacket(frame, playerIndex, commands);
    }

    private static GameMessage decodeCommand(String encoded) {
        var parts = encoded.split(",", -1);
        var kind = parts[0];
        int player = Integer.parseInt(parts[1]);
        return switch (kind) {
            case "MOVE" -> new GameMessage.MoveTo(player, parseIds(parts[2]),
                    new Coord3D(Float.parseFloat(parts[3]), Float.parseFloat(parts[4]), Float.parseFloat(parts[5])));
            case "ATTACK" -> new GameMessage.AttackObject(player, parseIds(parts[2]),
                    new ObjectId(Integer.parseInt(parts[3])));
            case "STOP" -> new GameMessage.StopMoving(player, parseIds(parts[2]));
            case "QUEUE" -> new GameMessage.QueueProduction(player,
                    new ObjectId(Integer.parseInt(parts[2])), parts[3]);
            case "RALLY" -> new GameMessage.SetRallyPoint(player,
                    new ObjectId(Integer.parseInt(parts[2])),
                    new Coord3D(Float.parseFloat(parts[3]), Float.parseFloat(parts[4]), Float.parseFloat(parts[5])));
            default -> throw new IllegalArgumentException("unknown command kind: " + kind);
        };
    }

    private static String ids(List<ObjectId> units) {
        return units.stream().map(id -> Integer.toString(id.value())).collect(Collectors.joining(":"));
    }

    private static List<ObjectId> parseIds(String encoded) {
        if (encoded.isEmpty()) {
            return List.of();
        }
        var ids = new ArrayList<ObjectId>();
        for (var token : encoded.split(":")) {
            ids.add(new ObjectId(Integer.parseInt(token)));
        }
        return ids;
    }
}
