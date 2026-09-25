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
 * <p>Captures the strategic state — frame counter, where the simulation's random
 * numbers stand, players (money, weapon-bonus, upgrades, granted words, whether a computer
 * plays them, what they earned and spent) and objects (template,
 * owner, transform, health and most health, status, condition words) — enough
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
        sb.append("RANDOM ").append(logic.random().state()).append('\n');
        if (!logic.getRevealedTo().isEmpty()) {
            sb.append("REVEALED ").append(logic.getRevealedTo().stream().map(String::valueOf)
                    .collect(Collectors.joining(","))).append('\n');
        }
        if (logic.getPathGrid() != null) {
            for (var deck : logic.getPathGrid().decks()) {
                sb.append("DECK ").append(deck.floor());
                for (int i = 0; i < 4; i++) {
                    var corner = deck.corner(i);
                    sb.append('|').append(Float.toString(corner.x())).append(',')
                            .append(Float.toString(corner.y())).append(',')
                            .append(Float.toString(corner.z()));
                }
                sb.append('|').append(deck.isOpen()).append('\n');
            }
        }

        var players = logic.getPlayerList();
        for (int i = 0; i < players.getPlayerCount(); i++) {
            var p = logic.getRtsPlayer(i);
            var upgrades = p.getUpgrades().stream().sorted().collect(Collectors.joining(","));
            sb.append("PLAYER ").append(i).append('|').append(p.getName()).append('|')
                    .append(p.getMoney()).append('|')
                    .append(Float.toString(p.getWeaponDamageBonus())).append('|')
                    .append(upgrades).append('|')
                    .append(String.join(",", p.getGranted())).append('|')
                    .append(p.isComputer()).append('|')
                    .append(p.getEarned()).append('|')
                    .append(p.getSpent()).append('\n');
        }

        for (var o : logic.getObjects()) {
            var statuses = Arrays.stream(ObjectStatus.values())
                    .filter(o::hasStatus).map(Enum::name).collect(Collectors.joining(","));
            var health = o.getBody() == null ? "" : Float.toString(o.getBody().getHealth());
            var most = o.getBody() == null ? "" : Float.toString(o.getBody().getMaxHealth());
            var pos = o.getPosition();
            sb.append("OBJECT ").append(o.getId().value()).append('|')
                    .append(o.getTemplate().name()).append('|')
                    .append(o.getPlayerIndex()).append('|')
                    .append(Float.toString(pos.x())).append('|')
                    .append(Float.toString(pos.y())).append('|')
                    .append(Float.toString(pos.z())).append('|')
                    .append(Float.toString(o.getOrientation())).append('|')
                    .append(health).append('|')
                    .append(statuses).append('|')
                    .append(String.join(",", o.getConditions())).append('|')
                    .append(most).append('|')
                    .append(o.getOwnVisionRange()).append('|')
                    .append(o.getTargetableFrom()).append('|')
                    .append(o.getFloor()).append('|')
                    .append(spanOf(o.getSpan())).append('|')
                    .append(o.getProducer() == null ? "" : Integer.toString(o.getProducer().value())).append('|')
                    .append(o.getOwnFogRange()).append('\n');
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
                case "RANDOM" -> logic.random().restore(Long.parseLong(rest.trim()));
                case "REVEALED" -> {
                    for (var player : rest.trim().split(",")) {
                        logic.revealMapTo(Integer.parseInt(player));
                    }
                }
                case "PLAYER" -> loadPlayer(rest, logic);
                case "DECK" -> loadDeck(rest, logic);
                case "OBJECT" -> loadObject(rest, logic);
                default -> throw new IllegalArgumentException("unknown snapshot line: " + key);
            }
        }
    }

    /** A thing's line as its two ends, six numbers; nothing for none. */
    private static String spanOf(uz.dukeengine.core.thing.Span span) {
        if (span == null) {
            return "";
        }
        return java.util.stream.Stream.of(span.from().x(), span.from().y(), span.from().z(), span.to().x(),
                span.to().y(), span.to().z()).map(value -> Float.toString(value)).collect(Collectors.joining(","));
    }

    private static uz.dukeengine.core.thing.Span spanFrom(String text) {
        var at = text.split(",");
        return new uz.dukeengine.core.thing.Span(
                new Coord3D(Float.parseFloat(at[0]), Float.parseFloat(at[1]), Float.parseFloat(at[2])),
                new Coord3D(Float.parseFloat(at[3]), Float.parseFloat(at[4]), Float.parseFloat(at[5])));
    }

    /** A deck the map or the game laid before the load is only opened or closed; one laid at run time is laid again. */
    private static void loadDeck(String rest, RtsSimulation logic) {
        var grid = logic.getPathGrid();
        if (grid == null) {
            return;
        }
        var parts = rest.trim().split("\\|");
        int floor = Integer.parseInt(parts[0]);
        if (grid.deck(floor) == null) {
            var corners = new Coord3D[4];
            for (int i = 0; i < 4; i++) {
                var xyz = parts[i + 1].split(",");
                corners[i] = new Coord3D(Float.parseFloat(xyz[0]), Float.parseFloat(xyz[1]), Float.parseFloat(xyz[2]));
            }
            grid.addDeck(corners[0], corners[1], corners[2], corners[3]);
        }
        grid.setDeckOpen(floor, Boolean.parseBoolean(parts[5]));
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
        // A save from before the books has neither total.
        player.restoreBooks(money, parts.length > 7 ? Long.parseLong(parts[7]) : 0L,
                parts.length > 8 ? Long.parseLong(parts[8]) : 0L);
        player.multiplyWeaponDamageBonus(bonus); // players start at 1.0 after reset
        if (!upgrades.isEmpty()) {
            for (var upgrade : upgrades.split(",")) {
                player.addUpgrade(upgrade);
            }
        }
        // A save from before granted words and computer sides has neither.
        if (parts.length > 5 && !parts[5].isEmpty()) {
            for (var word : parts[5].split(",")) {
                player.grant(word);
            }
        }
        player.setComputer(parts.length > 6 && Boolean.parseBoolean(parts[6]));
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
        var conditions = parts.length > 9 ? parts[9] : ""; // a save from before conditions has none
        var most = parts.length > 10 ? parts[10] : ""; // and one from before most health, the template's
        // and one from before a thing's own sight and protection, its template's sight and none
        float sight = parts.length > 11 && !parts[11].isEmpty() ? Float.parseFloat(parts[11]) : -1f;
        int targetableFrom = parts.length > 12 && !parts[12].isEmpty() ? Integer.parseInt(parts[12]) : 0;
        int floor = parts.length > 13 && !parts[13].isEmpty() ? Integer.parseInt(parts[13]) : 0;
        var span = parts.length > 14 && !parts[14].isEmpty() ? spanFrom(parts[14]) : null;
        var producer = parts.length > 15 && !parts[15].isEmpty() ? new ObjectId(Integer.parseInt(parts[15])) : null;
        float fog = parts.length > 16 && !parts[16].isEmpty() ? Float.parseFloat(parts[16]) : -1f;

        var template = logic.getThingFactory().findTemplate(templateName);
        if (template == null) {
            throw new IllegalArgumentException("snapshot references unknown template: " + templateName);
        }
        var object = logic.restoreObject(template, new ObjectId(id));
        object.setPlayerIndex(player);
        object.setPosition(new Coord3D(x, y, z));
        object.setOrientation(orientation);
        object.setVisionRange(sight);
        object.setTargetableFrom(targetableFrom);
        object.setFloor(floor);
        object.setSpan(span);
        object.setProducer(producer);
        object.setFogRange(fog);
        // The most first: health is held to it, and an upgraded Crusader's 680 would be cut to its template's 480.
        if (!most.isEmpty() && object.getBody() != null
                && Float.parseFloat(most) != object.getBody().getMaxHealth()) {
            object.getBody().setMaxHealth(Float.parseFloat(most),
                    uz.dukeengine.core.module.BodyModule.MaxHealthChange.KEEP_HEALTH);
        }
        if (!health.isEmpty() && object.getBody() != null) {
            object.getBody().setHealth(Float.parseFloat(health));
        }
        if (!statuses.isEmpty()) {
            for (var status : statuses.split(",")) {
                object.setStatus(ObjectStatus.valueOf(status));
            }
        }
        if (!conditions.isEmpty()) {
            for (var word : conditions.split(",")) {
                object.setCondition(word);
            }
        }
    }
}
