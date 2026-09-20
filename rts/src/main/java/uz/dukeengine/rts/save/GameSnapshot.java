package uz.dukeengine.rts.save;

import java.util.Arrays;
import java.util.stream.Collectors;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.rts.RtsSimulation;
import uz.dukeengine.rts.player.RtsPlayer;

/**
 * Saves and restores the deterministic world state as portable text, ported in
 * spirit from SAGE's {@code Snapshot}/{@code Xfer} system.
 *
 * <p>Captures the strategic state — frame counter, players (money, weapon-bonus,
 * upgrades) and objects (template, owner, transform, health, status) — enough
 * that a restored world has the same {@link GameLogic#checksum()} as the saved
 * one. Float values use {@link Float#toString}, which round-trips to identical
 * bits.
 *
 * <p>Limitation: per-module in-flight state (a unit's current move goal, a
 * weapon's reload counter, a factory's build queue) is <em>not</em> yet
 * serialized — modules are rebuilt fresh on load. The checksum-relevant world is
 * faithfully restored; resuming in-progress orders is the next step (a per-module
 * {@code Saveable} hook).
 */
public final class GameSnapshot {

    private GameSnapshot() {
    }

    /** Serialize the world to text. */
    public static String save(RtsSimulation logic) {
        var sb = new StringBuilder();
        sb.append("FRAME ").append(logic.getFrame()).append('\n');
        sb.append("NEXTID ").append(logic.getNextObjectId()).append('\n');

        var players = logic.getPlayerList();
        for (int i = 0; i < players.getPlayerCount(); i++) {
            var p = logic.getRtsPlayer(i);
            var upgrades = p.getUpgrades().stream().sorted().collect(Collectors.joining(","));
            sb.append("PLAYER ").append(i).append('|').append(p.getName()).append('|')
                    .append(p.getMoney()).append('|')
                    .append(Float.toString(p.getWeaponDamageBonus())).append('|')
                    .append(upgrades).append('\n');
        }

        for (var o : logic.getObjects()) {
            var statuses = Arrays.stream(ObjectStatus.values())
                    .filter(o::hasStatus).map(Enum::name).collect(Collectors.joining(","));
            var health = o.getBody() == null ? "" : Float.toString(o.getBody().getHealth());
            var pos = o.getPosition();
            sb.append("OBJECT ").append(o.getId().value()).append('|')
                    .append(o.getTemplate().name()).append('|')
                    .append(o.getPlayerIndex()).append('|')
                    .append(Float.toString(pos.x())).append('|')
                    .append(Float.toString(pos.y())).append('|')
                    .append(Float.toString(pos.z())).append('|')
                    .append(Float.toString(o.getOrientation())).append('|')
                    .append(health).append('|')
                    .append(statuses).append('\n');
        }
        return sb.toString();
    }

    /** Restore a saved world into {@code logic}, which must have the templates loaded. */
    public static void load(String text, RtsSimulation logic) {
        logic.reset(); // baseline: empty world, neutral player only
        for (var line : text.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            int space = line.indexOf(' ');
            var key = line.substring(0, space);
            var rest = line.substring(space + 1);
            switch (key) {
                case "FRAME" -> logic.setFrame(Integer.parseInt(rest.trim()));
                case "NEXTID" -> logic.setNextObjectId(Integer.parseInt(rest.trim()));
                case "PLAYER" -> loadPlayer(rest, logic);
                case "OBJECT" -> loadObject(rest, logic);
                default -> throw new IllegalArgumentException("unknown snapshot line: " + key);
            }
        }
    }

    private static void loadPlayer(String rest, RtsSimulation logic) {
        var parts = rest.split("\\|", -1);
        int index = Integer.parseInt(parts[0]);
        var name = parts[1];
        int money = Integer.parseInt(parts[2]);
        float bonus = Float.parseFloat(parts[3]);
        var upgrades = parts[4];

        var players = logic.getPlayerList();
        if (index != 0) {
            players.addPlayer(name); // index 0 (neutral) already exists after reset
        }
        var player = (RtsPlayer) players.getPlayer(index);
        player.deposit(money);
        player.multiplyWeaponDamageBonus(bonus); // players start at 1.0 after reset
        if (!upgrades.isEmpty()) {
            for (var upgrade : upgrades.split(",")) {
                player.addUpgrade(upgrade);
            }
        }
    }

    private static void loadObject(String rest, RtsSimulation logic) {
        var parts = rest.split("\\|", -1);
        int id = Integer.parseInt(parts[0]);
        var templateName = parts[1];
        int player = Integer.parseInt(parts[2]);
        float x = Float.parseFloat(parts[3]);
        float y = Float.parseFloat(parts[4]);
        float z = Float.parseFloat(parts[5]);
        float orientation = Float.parseFloat(parts[6]);
        var health = parts[7];
        var statuses = parts[8];

        var template = logic.getThingFactory().findTemplate(templateName);
        if (template == null) {
            throw new IllegalArgumentException("snapshot references unknown template: " + templateName);
        }
        var object = logic.restoreObject(template, new ObjectId(id));
        object.setPlayerIndex(player);
        object.setPosition(new Coord3D(x, y, z));
        object.setOrientation(orientation);
        if (!health.isEmpty() && object.getBody() != null) {
            object.getBody().setHealth(Float.parseFloat(health));
        }
        if (!statuses.isEmpty()) {
            for (var status : statuses.split(",")) {
                object.setStatus(ObjectStatus.valueOf(status));
            }
        }
    }
}
