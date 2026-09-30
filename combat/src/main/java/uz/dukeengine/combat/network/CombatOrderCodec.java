package uz.dukeengine.combat.network;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import uz.dukeengine.combat.message.CombatOrder;
import uz.dukeengine.combat.message.GameOrder;
import uz.dukeengine.combat.message.OrderSource;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.thing.ObjectId;

/**
 * The wire lines of the orders every side gives ({@link CombatOrder}, and the game's own words, {@link GameOrder}): one
 * line an order, in the form a side's own codec joins into a packet — {@code MOVE}, {@code ATTACK}, {@code STOP} and
 * {@code ORDER}, the lines they always had, so a recording and a peer of an older build read them as ever.
 *
 * <p>Integers and {@link Float#toString} floats, which round-trip to identical bits; {@code ,} between an order's
 * parts and {@code :} between object ids, as a side's codec keeps {@code ;} and {@code |} for its packet.
 */
public final class CombatOrderCodec {

    private CombatOrderCodec() {
    }

    /** The line of {@code order}. */
    public static String encode(CombatOrder order) {
        return switch (order) {
            case CombatOrder.MoveTo m -> "MOVE," + m.playerIndex() + "," + ids(m.units())
                    + "," + Float.toString(m.destination().x())
                    + "," + Float.toString(m.destination().y())
                    + "," + Float.toString(m.destination().z())
                    + (m.click() ? ",click" : "");
            case CombatOrder.AttackObject a -> "ATTACK," + a.playerIndex() + "," + ids(a.units())
                    + "," + a.target().value()
                    + (a.source() == OrderSource.PLAYER && a.slot() < 0 ? (a.forced() ? ",forced" : "")
                            : "," + (a.forced() ? "forced" : "") + "," + a.source().name() + "," + a.slot());
            case CombatOrder.StopMoving s -> "STOP," + s.playerIndex() + "," + ids(s.units());
        };
    }

    /** The line of {@code order}; its word written URL-encoded, so no separator in it breaks the line. */
    public static String encode(GameOrder order) {
        return "ORDER," + order.playerIndex() + ","
                + java.net.URLEncoder.encode(order.word(), java.nio.charset.StandardCharsets.UTF_8)
                + "," + ids(order.units())
                + "," + (order.place() == null ? "" : Float.toString(order.place().x()))
                + "," + (order.place() == null ? "" : Float.toString(order.place().y()))
                + "," + (order.place() == null ? "" : Float.toString(order.place().z()))
                + "," + (order.target() == null ? "" : Integer.toString(order.target().value()))
                + "," + order.number();
    }

    /**
     * The order {@code line} is — a {@link CombatOrder} or a {@link GameOrder} — or null for a line of any other kind,
     * a side's own, for its codec to read.
     */
    public static Command decode(String line) {
        var parts = line.split(",", -1);
        return switch (parts[0]) {
            case "MOVE" -> new CombatOrder.MoveTo(Integer.parseInt(parts[1]), parseIds(parts[2]),
                    new Coord3D(Float.parseFloat(parts[3]), Float.parseFloat(parts[4]), Float.parseFloat(parts[5])),
                    parts.length > 6 && "click".equals(parts[6]));
            case "ATTACK" -> new CombatOrder.AttackObject(Integer.parseInt(parts[1]), parseIds(parts[2]),
                    new ObjectId(Integer.parseInt(parts[3])), parts.length > 4 && "forced".equals(parts[4]),
                    parts.length > 5 ? OrderSource.valueOf(parts[5]) : OrderSource.PLAYER,
                    parts.length > 6 ? Integer.parseInt(parts[6]) : -1);
            case "STOP" -> new CombatOrder.StopMoving(Integer.parseInt(parts[1]), parseIds(parts[2]));
            case "ORDER" -> new GameOrder(Integer.parseInt(parts[1]),
                    java.net.URLDecoder.decode(parts[2], java.nio.charset.StandardCharsets.UTF_8),
                    parseIds(parts[3]),
                    parts[4].isEmpty() ? null : new Coord3D(Float.parseFloat(parts[4]), Float.parseFloat(parts[5]),
                            Float.parseFloat(parts[6])),
                    parts[7].isEmpty() ? null : new ObjectId(Integer.parseInt(parts[7])),
                    Long.parseLong(parts[8]));
            default -> null;
        };
    }

    /** Object ids as a line writes them, {@code :} between each. */
    public static String ids(List<ObjectId> units) {
        return units.stream().map(id -> Integer.toString(id.value())).collect(Collectors.joining(":"));
    }

    /** Object ids as {@link #ids} wrote them. */
    public static List<ObjectId> parseIds(String encoded) {
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
