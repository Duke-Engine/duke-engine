package uz.duke.worldbuilder;

import java.util.ArrayList;
import java.util.List;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.gen.DungeonGenerator;
import uz.duke.dungeon.gen.GeneratedDungeon;
import uz.duke.dungeon.gen.GeneratedDungeon.Link;
import uz.duke.dungeon.gen.GeneratedDungeon.Monster;
import uz.duke.dungeon.gen.GeneratedDungeon.Placement;
import uz.duke.dungeon.gen.GeneratedDungeon.Prop;
import uz.duke.dungeon.gen.GeneratedDungeon.Room;
import uz.duke.dungeon.stage.Stage;
import uz.duke.dungeon.stage.StageCheck;

/**
 * A stage while it is being built.
 *
 * <p>The division of labour this whole tool rests on: <b>the generator draws the
 * place and the author fills it</b>. Rooms, corridors, storeys and stairs come out
 * of a seed and are never touched here — they carry a connectivity guarantee that
 * a person with a mouse would spend an afternoon breaking. What is editable is
 * what stands in them: which monster is in which corner, where the boss waits,
 * where the player comes in.
 *
 * <p>Which is also why this class exists at all rather than the window editing a
 * {@link Stage} directly. Everything here is arithmetic over cells and lists —
 * there is no window in it — so the rules about what may be placed where are
 * tested headlessly, the way the rest of this game is.
 */
public final class StageDraft {

    private final DungeonSettings settings;

    // The place. Read out of the generator and left alone.
    private final String terrain;
    private final String storeys;
    private final List<Room> rooms;
    private final List<Link> links;
    private final List<Integer> roomStoreys;
    private final int width;
    private final int height;

    // What is in it, and what it is called.
    private String id;
    private String name;
    private String description;
    private int difficulty;
    private int players;
    private long seed;
    private Placement entrance;
    private String bossKind;
    private Placement bossAt;
    private int bossRoom;
    private final List<Monster> monsters = new ArrayList<>();
    private final List<Prop> props = new ArrayList<>();

    private StageDraft(Stage stage, DungeonSettings settings) {
        this.settings = settings;
        var floor = stage.floor();
        this.terrain = floor.asciiMap();
        this.storeys = floor.levelMap();
        this.rooms = List.copyOf(floor.rooms());
        this.links = List.copyOf(floor.links());
        this.roomStoreys = List.copyOf(floor.roomStoreys());
        var rows = terrain.strip().split("\n");
        this.height = rows.length;
        this.width = rows[0].length();

        this.id = stage.id();
        this.name = stage.name();
        this.description = stage.description();
        this.difficulty = stage.difficulty();
        this.players = stage.players();
        this.seed = stage.seed();
        this.entrance = floor.hero();
        this.bossKind = floor.boss() == null ? null : floor.boss().kind();
        this.bossAt = floor.boss() == null ? null : floor.boss().at();
        this.bossRoom = floor.bossRoom();
        this.monsters.addAll(floor.monsters());
        this.props.addAll(floor.props());
    }

    /**
     * A fresh dungeon from a seed, with everything the generator put in it.
     *
     * <p>Starting from the generator's own population rather than from an empty
     * floor, because an empty floor is an afternoon of clicking before anything
     * can be tried. A seed is a first draft: play it, keep the rooms that work,
     * move what does not.
     */
    public static StageDraft generate(long seed, DungeonSettings settings) {
        var floor = DungeonGenerator.generate(seed, settings, 1);
        return new StageDraft(new Stage(idFor(seed), "Stage " + seed, "", 1, 1, seed, floor),
                settings);
    }

    /** A stage opened from a file, to be worked on further. */
    public static StageDraft of(Stage stage, DungeonSettings settings) {
        return new StageDraft(stage, settings);
    }

    private static String idFor(long seed) {
        return "stage" + Long.toUnsignedString(seed, 36);
    }

    // ---- what it becomes ----

    /** The stage as it now stands, ready to be written or checked. */
    public Stage toStage() {
        var boss = bossKind == null ? null : new Monster(bossKind, bossAt);
        var floor = new GeneratedDungeon(terrain, storeys, entrance, List.copyOf(monsters),
                boss, bossRoom, rooms, links, roomStoreys, List.copyOf(props));
        return new Stage(id, name, description, difficulty, players, seed, floor);
    }

    /**
     * What is wrong with it, as the game would say it.
     *
     * <p>The same check the loader runs, and that is the point: an author who is
     * told everything is fine and then cannot start the stage has been told a
     * different truth by two pieces of the same program.
     */
    public List<String> problems() {
        return StageCheck.problems(toStage(), settings);
    }

    // ---- editing ----

    /**
     * Put a monster in a cell, taking out whatever was standing there.
     *
     * <p>Replacing rather than refusing. One cell holds one thing — a solid thing
     * dropped on a skeleton leaves the skeleton unable to move at all — and an
     * editor that silently did nothing when you clicked an occupied cell would
     * teach that rule far more slowly than one that simply obeys.
     */
    public void putMonster(String kind, int cx, int cy) {
        clear(cx, cy);
        monsters.add(new Monster(kind, Placement.atCell(cx, cy)));
    }

    public void putProp(String kind, int cx, int cy) {
        clear(cx, cy);
        props.add(new Prop(kind, Placement.atCell(cx, cy)));
    }

    /**
     * Move the boss, and with it the room the stage is pointed at.
     *
     * <p>One gesture for both because they are one decision: the boss's room is
     * the end of the floor, and a boss standing in a room the stage does not
     * consider his would be an author saying two things at once.
     */
    public void putBoss(String kind, int cx, int cy) {
        clear(cx, cy);
        bossKind = kind;
        bossAt = Placement.atCell(cx, cy);
        int room = roomAt(cx, cy);
        if (room >= 0) {
            bossRoom = room;
        }
    }

    public void putEntrance(int cx, int cy) {
        clear(cx, cy);
        entrance = Placement.atCell(cx, cy);
    }

    /** Take away whatever is in a cell; the boss and the way in are moved, not deleted. */
    public boolean removeAt(int cx, int cy) {
        return clear(cx, cy);
    }

    private boolean clear(int cx, int cy) {
        boolean monsterGone = monsters.removeIf(m -> at(m.at(), cx, cy));
        boolean propGone = props.removeIf(p -> at(p.at(), cx, cy));
        return monsterGone || propGone;
    }

    private static boolean at(Placement placement, int cx, int cy) {
        return placement != null && placement.cellX() == cx && placement.cellY() == cy;
    }

    /** Which room a cell is in, or -1 for a cell out in a corridor. */
    public int roomAt(int cx, int cy) {
        for (int i = 0; i < rooms.size(); i++) {
            var room = rooms.get(i);
            if (cx >= room.x() && cx < room.x() + room.w()
                    && cy >= room.y() && cy < room.y() + room.h()) {
                return i;
            }
        }
        return -1;
    }

    /** What is standing in a cell, or null — so the editor can say what it is about to take. */
    public String whatIsAt(int cx, int cy) {
        for (var monster : monsters) {
            if (at(monster.at(), cx, cy)) {
                return monster.kind();
            }
        }
        for (var prop : props) {
            if (at(prop.at(), cx, cy)) {
                return prop.kind();
            }
        }
        if (at(bossAt, cx, cy)) {
            return bossKind;
        }
        return at(entrance, cx, cy) ? "the way in" : null;
    }

    // ---- what it is called ----

    public void setId(String id) {
        this.id = clean(id).replace(' ', '-');
    }

    public void setName(String name) {
        this.name = clean(name);
    }

    public void setDescription(String description) {
        this.description = clean(description);
    }

    public void setDifficulty(int difficulty) {
        this.difficulty = difficulty;
    }

    public void setPlayers(int players) {
        this.players = players;
    }

    /**
     * Text the file can actually carry.
     *
     * <p>A semicolon begins a comment everywhere in this format, so a description
     * with one in it is a description that would come back cut in half. Taken out
     * here, where a person is typing and can see it happen, rather than thrown at
     * him an hour later when he tries to save.
     */
    private static String clean(String text) {
        return text == null ? "" : text.replace(';', ',').strip();
    }

    // ---- what the canvas draws ----

    public int cellsAcross() {
        return width;
    }

    public int cellsDown() {
        return height;
    }

    public String terrain() {
        return terrain;
    }

    public String storeys() {
        return storeys;
    }

    public List<Room> rooms() {
        return rooms;
    }

    public List<Monster> monsters() {
        return List.copyOf(monsters);
    }

    public List<Prop> props() {
        return List.copyOf(props);
    }

    public Placement entrance() {
        return entrance;
    }

    public Placement bossAt() {
        return bossAt;
    }

    public String bossKind() {
        return bossKind;
    }

    public int bossRoom() {
        return bossRoom;
    }

    public long seed() {
        return seed;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public int difficulty() {
        return difficulty;
    }

    public int players() {
        return players;
    }
}
