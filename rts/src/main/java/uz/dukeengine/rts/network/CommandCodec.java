package uz.dukeengine.rts.network;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.network.CommandPacket;
import uz.dukeengine.core.network.PacketCodec;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.combat.message.CombatOrder;
import uz.dukeengine.combat.network.CombatOrderCodec;

/**
 * The RTS wire format: encodes a {@link CommandPacket} of {@link GameMessage}s,
 * and the orders every side gives ({@link CombatOrder}, whose lines are combat's),
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
 * A game's own order carries a word the engine never reads, so that word is
 * percent-encoded and may hold any of them.
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
        return switch (command) {
            case CombatOrder order -> CombatOrderCodec.encode(order);
            case GameMessage message -> encodeRts(message);
            default -> throw new IllegalArgumentException("not an RTS command: " + command.getClass().getName());
        };
    }

    private static String encodeRts(GameMessage message) {
        return switch (message) {
            case GameMessage.QueueProduction q -> "QUEUE," + q.playerIndex() + ","
                    + q.factory().value() + "," + q.unitTemplate();
            case GameMessage.SetRallyPoint r -> "RALLY," + r.playerIndex() + ","
                    + r.factory().value()
                    + "," + Float.toString(r.point().x())
                    + "," + Float.toString(r.point().y())
                    + "," + Float.toString(r.point().z());
            case GameMessage.Construct c -> "BUILD," + c.playerIndex() + "," + c.builder().value()
                    + "," + c.template()
                    + "," + Float.toString(c.place().x())
                    + "," + Float.toString(c.place().y())
                    + "," + Float.toString(c.place().z())
                    + "," + Float.toString(c.facing());
            case GameMessage.CancelConstruction c -> "UNBUILD," + c.playerIndex() + "," + c.site().value();
            case GameMessage.QueueResearch r -> "RESEARCH," + r.playerIndex() + "," + r.factory().value()
                    + "," + r.upgrade();
            case GameMessage.CancelProduction c -> "UNQUEUE," + c.playerIndex() + "," + c.factory().value()
                    + "," + c.index();
            case GameMessage.Sell s -> "SELL," + s.playerIndex() + "," + s.building().value();
            case GameMessage.AttackMove a -> "AMOVE," + a.playerIndex() + "," + CombatOrderCodec.ids(a.units())
                    + "," + Float.toString(a.destination().x())
                    + "," + Float.toString(a.destination().y())
                    + "," + Float.toString(a.destination().z());
            case GameMessage.Guard g -> "GUARD," + g.playerIndex() + "," + CombatOrderCodec.ids(g.units())
                    + "," + (g.place() == null ? "" : Float.toString(g.place().x()))
                    + "," + (g.place() == null ? "" : Float.toString(g.place().y()))
                    + "," + (g.place() == null ? "" : Float.toString(g.place().z()))
                    + "," + (g.target() == null ? "" : Integer.toString(g.target().value()))
                    + "," + g.mode().name();
            case GameMessage.Evacuate e -> "EVAC," + e.playerIndex() + "," + e.container().value();
            case GameMessage.ExitContainer e -> "EXIT," + e.playerIndex() + "," + e.passenger().value();
            case GameMessage.ResumeConstruction r -> "RESUME," + r.playerIndex() + "," + r.builder().value() + ","
                    + r.site().value();
            case GameMessage.GameOrder o -> "ORDER," + o.playerIndex() + ","
                    + java.net.URLEncoder.encode(o.word(), java.nio.charset.StandardCharsets.UTF_8)
                    + "," + CombatOrderCodec.ids(o.units())
                    + "," + (o.place() == null ? "" : Float.toString(o.place().x()))
                    + "," + (o.place() == null ? "" : Float.toString(o.place().y()))
                    + "," + (o.place() == null ? "" : Float.toString(o.place().z()))
                    + "," + (o.target() == null ? "" : Integer.toString(o.target().value()))
                    + "," + o.number();
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

    private static Command decodeCommand(String encoded) {
        var order = CombatOrderCodec.decode(encoded);
        if (order != null) {
            return order;
        }
        var parts = encoded.split(",", -1);
        var kind = parts[0];
        int player = Integer.parseInt(parts[1]);
        return switch (kind) {
            case "QUEUE" -> new GameMessage.QueueProduction(player,
                    new ObjectId(Integer.parseInt(parts[2])), parts[3]);
            case "RALLY" -> new GameMessage.SetRallyPoint(player,
                    new ObjectId(Integer.parseInt(parts[2])),
                    new Coord3D(Float.parseFloat(parts[3]), Float.parseFloat(parts[4]), Float.parseFloat(parts[5])));
            case "BUILD" -> new GameMessage.Construct(player,
                    new ObjectId(Integer.parseInt(parts[2])), parts[3],
                    new Coord3D(Float.parseFloat(parts[4]), Float.parseFloat(parts[5]), Float.parseFloat(parts[6])),
                    Float.parseFloat(parts[7]));
            case "UNBUILD" -> new GameMessage.CancelConstruction(player, new ObjectId(Integer.parseInt(parts[2])));
            case "RESEARCH" -> new GameMessage.QueueResearch(player, new ObjectId(Integer.parseInt(parts[2])),
                    parts[3]);
            case "UNQUEUE" -> new GameMessage.CancelProduction(player, new ObjectId(Integer.parseInt(parts[2])),
                    Integer.parseInt(parts[3]));
            case "SELL" -> new GameMessage.Sell(player, new ObjectId(Integer.parseInt(parts[2])));
            case "AMOVE" -> new GameMessage.AttackMove(player, CombatOrderCodec.parseIds(parts[2]),
                    new Coord3D(Float.parseFloat(parts[3]), Float.parseFloat(parts[4]), Float.parseFloat(parts[5])));
            case "GUARD" -> new GameMessage.Guard(player, CombatOrderCodec.parseIds(parts[2]),
                    parts[3].isEmpty() ? null : new Coord3D(Float.parseFloat(parts[3]), Float.parseFloat(parts[4]),
                            Float.parseFloat(parts[5])),
                    parts[6].isEmpty() ? null : new ObjectId(Integer.parseInt(parts[6])),
                    GameMessage.Guard.Mode.valueOf(parts[7]));
            case "EVAC" -> new GameMessage.Evacuate(player, new ObjectId(Integer.parseInt(parts[2])));
            case "EXIT" -> new GameMessage.ExitContainer(player, new ObjectId(Integer.parseInt(parts[2])));
            case "RESUME" -> new GameMessage.ResumeConstruction(player, new ObjectId(Integer.parseInt(parts[2])),
                    new ObjectId(Integer.parseInt(parts[3])));
            case "ORDER" -> new GameMessage.GameOrder(player,
                    java.net.URLDecoder.decode(parts[2], java.nio.charset.StandardCharsets.UTF_8),
                    CombatOrderCodec.parseIds(parts[3]),
                    parts[4].isEmpty() ? null : new Coord3D(Float.parseFloat(parts[4]), Float.parseFloat(parts[5]),
                            Float.parseFloat(parts[6])),
                    parts[7].isEmpty() ? null : new ObjectId(Integer.parseInt(parts[7])),
                    Long.parseLong(parts[8]));
            default -> throw new IllegalArgumentException("unknown command kind: " + kind);
        };
    }
}
