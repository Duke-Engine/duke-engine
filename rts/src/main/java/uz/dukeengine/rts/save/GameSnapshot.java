package uz.dukeengine.rts.save;

import java.util.Arrays;
import java.util.stream.Collectors;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.network.CommandPacket;
import uz.dukeengine.core.network.PacketCodec;
import uz.dukeengine.core.save.ModuleStates;
import uz.dukeengine.core.save.SaveIn;
import uz.dukeengine.core.save.SaveOut;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.rts.RtsSimulation;
import uz.dukeengine.rts.network.CommandCodec;
import uz.dukeengine.rts.player.RtsPlayer;

/**
 * Saves and restores the deterministic world state as portable text, ported in
 * spirit from SAGE's {@code Snapshot}/{@code Xfer} system: a world loaded from it
 * goes on frame for frame as the saved one would have — the same {@link
 * GameLogic#checksum()} at once and on every frame after.
 *
 * <p>One line a record, its word first; the first line is {@code SNAPSHOT} and the
 * {@link #VERSION}. The world's own lines — frame, next id, random numbers, sight,
 * decks, players and how they stand toward each other, the scenery taken away —
 * then each thing's {@code OBJECT} line, then each thing's {@code MODULES} line
 * ({@link ModuleStates}: every module it carries and what it wrote of itself),
 * then the world's own ({@link GameLogic#saveWorld}), the RTS world's ({@link
 * RtsSimulation#saveRts}), the movers' ({@link GameLogic#saveMovers}) and the
 * commands queued for the next frame, in the game's wire ({@link PacketCodec}).
 * Floats are written by {@link Float#toString}, which reads back to the same bits.
 *
 * <p>Taken between frames, on the simulation thread; a module that cannot be
 * written ({@link uz.dukeengine.core.save.UnsavedModuleException}) is refused. Loaded into a
 * world made by the same rules — its templates, its module set and their
 * revivers, its grid and its settings — before the load; what the game keeps
 * outside the engine is the game's to save beside it.
 */
public final class GameSnapshot {

    /**
     * The version of what is written. 2: every thing's modules, the players' stances, the scenery taken away, a thing
     * dead, destroyed, vanished or held, who is awake, the RTS world's own state, the movers' cells and routes and the
     * queued commands. 1, unmarked: the world's lines and the things alone, every module made afresh — refused now,
     * as it cannot go on as it was. Any change to what a line or a module writes raises it.
     */
    public static final int VERSION = 2;

    private GameSnapshot() {
    }

    /** The version a snapshot's text says it is, or 1 for one from before the versions. */
    public static int versionOf(String text) {
        if (!text.startsWith("SNAPSHOT ")) {
            return 1;
        }
        int end = text.indexOf('\n');
        return Integer.parseInt(text.substring("SNAPSHOT ".length(), end < 0 ? text.length() : end).trim());
    }

    /** Serialize the world to text, its queued commands in the RTS wire. */
    public static String save(RtsSimulation logic) {
        return save(logic, CommandCodec.INSTANCE);
    }

    /** Serialize the world to text, its queued commands in {@code codec}'s wire. */
    public static String save(RtsSimulation logic, PacketCodec codec) {
        var sb = new StringBuilder();
        sb.append("SNAPSHOT ").append(VERSION).append('\n');
        sb.append("FRAME ").append(logic.getFrame()).append('\n');
        sb.append("NEXTID ").append(logic.getNextObjectId()).append('\n');
        sb.append("RANDOM ").append(logic.random().state()).append('\n');
        if (!logic.getRevealedTo().isEmpty()) {
            sb.append("REVEALED ").append(logic.getRevealedTo().stream().map(String::valueOf)
                    .collect(Collectors.joining(","))).append('\n');
        }
        if (!logic.getMarkedSeen().isEmpty()) {
            sb.append("SEEN ").append(logic.getMarkedSeen().stream().map(String::valueOf)
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
        for (int i = 0; i < players.getPlayerCount(); i++) {
            var stances = new SaveOut();
            var relationships = players.getPlayer(i).getRelationships();
            stances.writeInt(relationships.size());
            relationships.forEach((other, stance) -> stances.writeInt(other).writeEnum(stance));
            var side = new SaveOut();
            logic.getRtsPlayer(i).save(side);
            sb.append("SIDE ").append(i).append('|').append(stances.text()).append('|').append(side.text())
                    .append('\n');
        }
        if (!logic.getRemovedScenery().isEmpty()) {
            sb.append("SCENERY ").append(logic.getRemovedScenery().stream().map(String::valueOf)
                    .collect(Collectors.joining(","))).append('\n');
        }
        for (int i = 0; i < players.getPlayerCount(); i++) {
            var memory = logic.getSightMemory(i);
            if (memory != null && (memory.seen().length > 0 || memory.inSight().length > 0)) {
                sb.append("SIGHT ").append(i).append('|').append(memory.width()).append('|').append(memory.height())
                        .append('|').append(bitsOf(memory.seen())).append('|').append(Arrays.stream(memory.inSight())
                                .mapToObj(String::valueOf).collect(Collectors.joining(","))).append('\n');
            }
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
                    .append(o.getOwnFogRange()).append('|')
                    .append(o.getRelationOverrides().entrySet().stream()
                            .map(override -> override.getKey() + "=" + override.getValue().name())
                            .collect(Collectors.joining(","))).append('|')
                    .append(o.hasDied()).append('|')
                    .append(o.isDestroyed()).append('|')
                    .append(o.hasVanished()).append('|')
                    .append(o.isContained()).append('|')
                    .append(o.seesOut()).append('|')
                    .append(o.isAnnounced()).append('\n');
        }
        var modules = logic.getThingFactory().getModuleFactory();
        for (var o : logic.getObjects()) {
            sb.append("MODULES ").append(o.getId().value()).append('|').append(ModuleStates.save(o, modules))
                    .append('\n');
        }
        var world = new SaveOut();
        logic.saveWorld(world);
        sb.append("WORLD ").append(world.text()).append('\n');
        var rts = new SaveOut();
        logic.saveRts(rts);
        sb.append("RTS ").append(rts.text()).append('\n');
        var movers = new SaveOut();
        logic.saveMovers(movers);
        sb.append("MOVERS ").append(movers.text()).append('\n');
        var queued = logic.getPendingCommands();
        if (!queued.isEmpty()) {
            sb.append("COMMANDS ").append(codec.encode(new CommandPacket(logic.getFrame(), 0, queued))).append('\n');
        }
        return sb.toString();
    }

    /** Restore a saved world into {@code logic}, its queued commands in the RTS wire. */
    public static void load(String text, RtsSimulation logic) {
        load(text, logic, CommandCodec.INSTANCE);
    }

    /**
     * Restore a saved world into {@code logic}, which must be made by the rules the saved one was — its templates, its
     * module set and revivers, its grid and settings — its queued commands in {@code codec}'s wire. A snapshot of
     * another version is refused before anything is changed.
     */
    public static void load(String text, RtsSimulation logic, PacketCodec codec) {
        int version = versionOf(text);
        if (version != VERSION) {
            throw new IllegalArgumentException("a snapshot of version " + version + " cannot be loaded: this engine"
                    + " reads version " + VERSION + (version == 1 ? ", and one of version 1 holds no module state" : ""));
        }
        logic.reset(); // baseline: empty world, neutral player only
        var modulesToCome = new java.util.LinkedHashSet<Integer>();
        var modules = logic.getThingFactory().getModuleFactory();
        for (var line : text.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            int space = line.indexOf(' ');
            var key = line.substring(0, space);
            var rest = line.substring(space + 1);
            switch (key) {
                case "SNAPSHOT" -> {
                }
                case "SIDE" -> loadSide(rest, logic);
                case "SCENERY" -> logic.setRemovedScenery(Arrays.stream(rest.trim().split(","))
                        .map(Integer::valueOf).toList());
                case "MODULES" -> {
                    int bar = rest.indexOf('|');
                    int id = Integer.parseInt(rest.substring(0, bar));
                    var thing = logic.findObject(new ObjectId(id));
                    if (thing == null || !modulesToCome.remove(id)) {
                        throw new IllegalArgumentException("snapshot has modules for thing " + id
                                + ", which it has no OBJECT line for or gave modules already");
                    }
                    ModuleStates.load(thing, rest.substring(bar + 1), modules);
                }
                case "WORLD" -> {
                    var in = new SaveIn(rest, logic, "WORLD");
                    logic.loadWorld(in);
                    in.expectEnd();
                }
                case "RTS" -> {
                    var in = new SaveIn(rest, logic, "RTS");
                    logic.loadRts(in);
                    in.expectEnd();
                }
                case "MOVERS" -> {
                    var in = new SaveIn(rest, logic, "MOVERS");
                    logic.loadMovers(in); // after the things, decks and scenery: the obstacles it lays are theirs
                    in.expectEnd();
                }
                case "COMMANDS" -> codec.decode(rest).commands().forEach(logic::issueCommand);
                case "OBJECT" -> modulesToCome.add(loadObject(rest, logic));
                case "FRAME" -> logic.setFrame(Integer.parseInt(rest.trim()));
                case "NEXTID" -> logic.setNextObjectId(Integer.parseInt(rest.trim()));
                case "RANDOM" -> logic.random().restore(Long.parseLong(rest.trim()));
                case "REVEALED" -> {
                    for (var player : rest.trim().split(",")) {
                        logic.revealMapTo(Integer.parseInt(player));
                    }
                }
                case "SEEN" -> {
                    for (var player : rest.trim().split(",")) {
                        logic.markMapSeen(Integer.parseInt(player));
                    }
                }
                case "PLAYER" -> loadPlayer(rest, logic);
                case "SIGHT" -> loadSight(rest, logic);
                case "DECK" -> loadDeck(rest, logic);
                default -> throw new IllegalArgumentException("unknown snapshot line: " + key);
            }
        }
        if (!modulesToCome.isEmpty()) {
            throw new IllegalArgumentException("snapshot gives no modules for things " + modulesToCome);
        }
    }

    /** How a player stands toward each other, and its side's bonuses and price changes. */
    private static void loadSide(String rest, RtsSimulation logic) {
        var parts = rest.split("\\|", -1);
        var players = logic.getPlayerList();
        var player = players.getPlayer(Integer.parseInt(parts[0]));
        var stances = new SaveIn(parts[1], logic, "SIDE");
        for (int count = stances.readInt(); count > 0; count--) {
            var other = players.getPlayer(stances.readInt());
            player.setRelationshipTo(other, stances.readEnum(uz.dukeengine.core.player.Relationship.class));
        }
        stances.expectEnd();
        var side = new SaveIn(parts[2], logic, "SIDE");
        ((RtsPlayer) player).load(side);
        side.expectEnd();
    }

    /** A player's cells as he had seen them: kept only where the world keeps cells of the same size. */
    private static void loadSight(String rest, RtsSimulation logic) {
        var parts = rest.split("\\|", -1);
        var cells = logic.getSightCells();
        int width = Integer.parseInt(parts[1]);
        int height = Integer.parseInt(parts[2]);
        if (cells == null || cells.width() != width || cells.height() != height) {
            return;
        }
        var inSight = parts[4].isEmpty() ? new int[0]
                : Arrays.stream(parts[4].split(",")).mapToInt(Integer::parseInt).toArray();
        logic.setSightMemory(Integer.parseInt(parts[0]),
                new uz.dukeengine.core.SightCells.Memory(width, height, bitsFrom(parts[3]), inSight));
    }

    /** A cell's bits, eight bytes a word, as text. */
    private static String bitsOf(long[] words) {
        var bytes = java.nio.ByteBuffer.allocate(words.length * Long.BYTES);
        bytes.asLongBuffer().put(words);
        return java.util.Base64.getEncoder().encodeToString(bytes.array());
    }

    private static long[] bitsFrom(String text) {
        var bytes = java.nio.ByteBuffer.wrap(java.util.Base64.getDecoder().decode(text));
        var words = new long[bytes.remaining() / Long.BYTES];
        bytes.asLongBuffer().get(words);
        return words;
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

    /** A thing back in the world from its line, its template's modules made afresh for its MODULES line; its id. */
    private static int loadObject(String rest, RtsSimulation logic) {
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
        var overrides = parts.length > 17 ? parts[17] : ""; // and one from before overrides has none

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
        if (!overrides.isEmpty()) {
            for (var override : overrides.split(",")) {
                int equals = override.indexOf('=');
                object.setRelationOverride(Integer.parseInt(override.substring(0, equals)),
                        uz.dukeengine.core.player.Relationship.valueOf(override.substring(equals + 1)));
            }
        }
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
        object.setContained(Boolean.parseBoolean(parts[21]));
        object.setSeesOut(Boolean.parseBoolean(parts[22]));
        if (!Boolean.parseBoolean(parts[23])) {
            object.restoredUntold(); // made since its last frame: told so by the next, as in the saved world
        }
        if (Boolean.parseBoolean(parts[18])) {
            object.markDied();
        }
        if (Boolean.parseBoolean(parts[20])) {
            object.vanish();
        } else if (Boolean.parseBoolean(parts[19])) {
            object.markDestroyed();
        }
        return id;
    }
}
