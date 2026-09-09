package uz.duke.dungeon.content;

import java.util.List;
import java.util.Map;
import uz.duke.core.ini.FieldParseTable;
import uz.duke.core.ini.Ini;
import uz.duke.dungeon.level.Levelling;

/**
 * Every tuning number that is not a unit stat, read from {@code dungeon.ini}.
 *
 * <p>Unit stats belong in {@code creatures.ini}, where the engine's template
 * loader reads them. What is left is the shape of a dungeon and the decisions its
 * creatures make — neither of which is a property of any one unit, so the engine
 * has nowhere to put them and no opinion about them. They were constants in Java;
 * now they are data, and tuning the game is editing a file.
 *
 * <p>Parsed with the engine's own {@link Ini} reader and {@link FieldParseTable},
 * the same machinery that reads unit definitions, so the file looks and behaves
 * like every other duke-engine data file — blocks ending in {@code End}, {@code ;}
 * for comments.
 *
 * <p>Settings are read once and passed to whatever needs them, rather than reached
 * for through a global. That keeps the simulation's inputs explicit, which is what
 * a deterministic world needs.
 */
public final class DungeonSettings {

    // ---- layout ----

    private int mapWidth = 50;
    private int mapHeight = 36;
    private int minRooms = 5;
    private int maxRooms = 8;
    private int minRoomSize = 5;
    private int maxRoomSize = 9;
    private int roomGap = 1;
    private int placementAttempts = 600;
    private int minSkeletonsPerRoom = 2;
    private int maxSkeletonsPerRoom = 6;

    // ---- behaviour ----

    private float skeletonSenseRadius = 90f;
    private float skeletonChaseRadius = 150f;
    private int skeletonRepathFrames = 10;
    private float closeDistance = 4f;
    private int heroRepathFrames = 10;

    // ---- run loop ----

    private int respawnDelayFrames = 60;

    // ---- leveling ----

    private int maxLevel = 10;
    private int xpBase = 30;
    private int xpStep = 15;
    private int healthPerLevel = 20;
    private int damagePercentPerLevel = 12;
    private int armourPercentPerLevel = 5;
    private int minDamageTakenPercent = 40;
    private int levelUpBannerFrames = 60;

    // ---- monsters and depth ----

    /** In file order, which is the order a seed picks through them. */
    private final java.util.List<MonsterKind> monsters = new java.util.ArrayList<>();

    private int monsterHealthPercentPerDepth = 25;
    private int monsterDamagePercentPerDepth = 15;
    private int monsterCountPercentPerDepth = 20;
    private int bossHealthPercentPerDepth = 40;
    private int bossDamagePercentPerDepth = 25;
    private int experiencePercentPerDepth = 30;

    /** The kind placed in the furthest room. Named, not flagged, so it is findable. */
    public static final String BOSS = "Boss";

    private DungeonSettings() {
    }

    /** The settings shipped with the game. */
    public static DungeonSettings load() {
        return parse(Content.read(Content.SETTINGS));
    }

    /**
     * Settings from INI text — the seam a test uses to prove that changing the
     * file changes the game, without a rebuild.
     */
    public static DungeonSettings parse(String iniText) {
        var settings = new DungeonSettings();
        var ini = Ini.of(iniText, Map.of(
                "DungeonGeneration", reader -> {
                    reader.getNextToken(); // the block's name, which we do not need
                    reader.initFromIni(settings, LAYOUT);
                },
                "DungeonCombat", reader -> {
                    reader.getNextToken();
                    reader.initFromIni(settings, BEHAVIOUR);
                },
                "DungeonRun", reader -> {
                    reader.getNextToken();
                    reader.initFromIni(settings, RUN);
                },
                "DungeonLeveling", reader -> {
                    reader.getNextToken();
                    reader.initFromIni(settings, LEVELLING);
                },
                // Repeatable: the block's name is the monster's, so the list of
                // kinds is the file's, not a constant somewhere in Java.
                "DungeonMonster", reader -> {
                    var kind = new MonsterBuilder(reader.getNextToken());
                    reader.initFromIni(kind, MONSTER);
                    settings.monsters.add(kind.build());
                },
                "DungeonDepth", reader -> {
                    reader.getNextToken();
                    reader.initFromIni(settings, DEPTH);
                }));
        ini.load();
        if (!readingShippedFile) {
            settings.fillInMissingMonsters();
        }
        settings.validate();
        return settings;
    }

    /**
     * Keep the monsters this file did not mention, in the order the shipped file
     * lists them.
     *
     * <p>A monster block overrides the kind of that name and leaves the rest alone,
     * which is how every other setting in this file already behaves: naming the map
     * width does not delete the room count. The alternative — a declared list
     * replacing the whole roster — reads the same in the file and fails a long way
     * from the edit, when a creature definition asks for a behaviour nobody
     * registered because its kind quietly stopped existing.
     *
     * <p>Order is the shipped order, since that is the order a seed draws through:
     * overriding a kind must not silently change which monster a seed picks.
     */
    private void fillInMissingMonsters() {
        var declared = new java.util.ArrayList<>(monsters);
        monsters.clear();
        for (var shipped : shippedMonsters()) {
            var override = declared.stream()
                    .filter(kind -> kind.name().equals(shipped.name()))
                    .findFirst();
            monsters.add(override.orElse(shipped));
            override.ifPresent(declared::remove);
        }
        monsters.addAll(declared); // kinds this file invented
    }

    private static final List<MonsterKind> SHIPPED_MONSTERS = new java.util.ArrayList<>();
    private static boolean readingShippedFile;

    /**
     * The monster list from the shipped file, read once and kept.
     *
     * <p>Read by parsing that file the ordinary way — a second, partial parser
     * would be a second thing to keep in step with the first. The flag is what
     * stops that parse from asking itself the same question forever.
     */
    private static List<MonsterKind> shippedMonsters() {
        if (SHIPPED_MONSTERS.isEmpty() && !readingShippedFile) {
            readingShippedFile = true;
            try {
                SHIPPED_MONSTERS.addAll(parse(Content.read(Content.SETTINGS)).monsters);
            } finally {
                readingShippedFile = false;
            }
        }
        return SHIPPED_MONSTERS;
    }

    /**
     * Catch a file that would produce a broken dungeon at load time, where the
     * message can name the field, rather than as a strange failure much later.
     */
    private void validate() {
        require(mapWidth > 0 && mapHeight > 0, "map size must be positive");
        require(minRooms >= 1, "a dungeon needs at least one room");
        require(maxRooms >= minRooms, "MaxRooms must not be below MinRooms");
        require(minRoomSize >= 3, "a room smaller than 3 cells has no interior");
        require(maxRoomSize >= minRoomSize, "MaxRoomSize must not be below MinRoomSize");
        require(maxRoomSize + 3 <= Math.min(mapWidth, mapHeight),
                "rooms must fit on the map with a border");
        require(minSkeletonsPerRoom >= 0, "a room cannot hold fewer than no skeletons");
        require(maxSkeletonsPerRoom >= minSkeletonsPerRoom,
                "MaxSkeletonsPerRoom must not be below MinSkeletonsPerRoom");
        require(skeletonChaseRadius >= skeletonSenseRadius,
                "a skeleton should not give up closer than it first notices");
        require(skeletonRepathFrames >= 1 && heroRepathFrames >= 1,
                "re-planning every zero frames is not a plan");
        require(closeDistance >= 0, "CloseDistance cannot be negative");
        require(respawnDelayFrames >= 0, "the death pause cannot be negative");
        require(maxLevel >= Levelling.FIRST_LEVEL, "MaxLevel cannot be below the first level");
        require(xpBase > 0, "XpBase must be positive or no level is ever reached");
        require(xpStep >= 0, "XpStep cannot make later levels cheaper");
        require(healthPerLevel >= 0 && damagePercentPerLevel >= 0 && armourPercentPerLevel >= 0,
                "a level cannot take something away");
        require(minDamageTakenPercent > 0 && minDamageTakenPercent <= 100,
                "the damage floor must leave some way to lose");
        require(levelUpBannerFrames >= 0, "the level-up message cannot last negative frames");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException("dungeon.ini: " + message);
        }
    }

    private static final FieldParseTable<DungeonSettings> LAYOUT =
            new FieldParseTable<DungeonSettings>()
                    .add("MapWidth", Ini.integer((s, v) -> s.mapWidth = v))
                    .add("MapHeight", Ini.integer((s, v) -> s.mapHeight = v))
                    .add("MinRooms", Ini.integer((s, v) -> s.minRooms = v))
                    .add("MaxRooms", Ini.integer((s, v) -> s.maxRooms = v))
                    .add("MinRoomSize", Ini.integer((s, v) -> s.minRoomSize = v))
                    .add("MaxRoomSize", Ini.integer((s, v) -> s.maxRoomSize = v))
                    .add("RoomGap", Ini.integer((s, v) -> s.roomGap = v))
                    .add("PlacementAttempts", Ini.integer((s, v) -> s.placementAttempts = v))
                    .add("MinSkeletonsPerRoom", Ini.integer((s, v) -> s.minSkeletonsPerRoom = v))
                    .add("MaxSkeletonsPerRoom", Ini.integer((s, v) -> s.maxSkeletonsPerRoom = v));

    private static final FieldParseTable<DungeonSettings> BEHAVIOUR =
            new FieldParseTable<DungeonSettings>()
                    .add("SkeletonSenseRadius", Ini.real((s, v) -> s.skeletonSenseRadius = v))
                    .add("SkeletonChaseRadius", Ini.real((s, v) -> s.skeletonChaseRadius = v))
                    .add("SkeletonRepathFrames", Ini.integer((s, v) -> s.skeletonRepathFrames = v))
                    .add("CloseDistance", Ini.real((s, v) -> s.closeDistance = v))
                    .add("HeroRepathFrames", Ini.integer((s, v) -> s.heroRepathFrames = v));

    /** Accumulates one {@code DungeonMonster} block. */
    private static final class MonsterBuilder {
        private final String name;
        float senseRadius = 90f;
        float chaseRadius = 150f;
        float closeDistance = 4f;
        int repathFrames = 10;
        int minDepth = 1;
        int weight;
        int colour = 0xFFFFFF;
        float scale = 1f;

        MonsterBuilder(String name) {
            this.name = name;
        }

        MonsterKind build() {
            return new MonsterKind(name, senseRadius, chaseRadius, closeDistance,
                    repathFrames, minDepth, weight, colour, scale);
        }
    }

    private static final FieldParseTable<MonsterBuilder> MONSTER =
            new FieldParseTable<MonsterBuilder>()
                    .add("SenseRadius", Ini.real((m, v) -> m.senseRadius = v))
                    .add("ChaseRadius", Ini.real((m, v) -> m.chaseRadius = v))
                    .add("CloseDistance", Ini.real((m, v) -> m.closeDistance = v))
                    .add("RepathFrames", Ini.integer((m, v) -> m.repathFrames = v))
                    .add("MinDepth", Ini.integer((m, v) -> m.minDepth = v))
                    .add("Weight", Ini.integer((m, v) -> m.weight = v))
                    // Decoded rather than scanned so a file can write 0xRRGGBB,
                    // which is how anyone actually writes a colour.
                    .add("Colour", (ini, m) -> m.colour = Integer.decode(ini.getNextToken()))
                    .add("Scale", Ini.real((m, v) -> m.scale = v));

    private static final FieldParseTable<DungeonSettings> DEPTH =
            new FieldParseTable<DungeonSettings>()
                    .add("MonsterHealthPercentPerDepth",
                            Ini.integer((s, v) -> s.monsterHealthPercentPerDepth = v))
                    .add("MonsterDamagePercentPerDepth",
                            Ini.integer((s, v) -> s.monsterDamagePercentPerDepth = v))
                    .add("MonsterCountPercentPerDepth",
                            Ini.integer((s, v) -> s.monsterCountPercentPerDepth = v))
                    .add("BossHealthPercentPerDepth",
                            Ini.integer((s, v) -> s.bossHealthPercentPerDepth = v))
                    .add("BossDamagePercentPerDepth",
                            Ini.integer((s, v) -> s.bossDamagePercentPerDepth = v))
                    .add("ExperiencePercentPerDepth",
                            Ini.integer((s, v) -> s.experiencePercentPerDepth = v));

    private static final FieldParseTable<DungeonSettings> RUN =
            new FieldParseTable<DungeonSettings>()
                    .add("RespawnDelayFrames", Ini.integer((s, v) -> s.respawnDelayFrames = v));

    private static final FieldParseTable<DungeonSettings> LEVELLING =
            new FieldParseTable<DungeonSettings>()
                    .add("MaxLevel", Ini.integer((s, v) -> s.maxLevel = v))
                    .add("XpBase", Ini.integer((s, v) -> s.xpBase = v))
                    .add("XpStep", Ini.integer((s, v) -> s.xpStep = v))
                    .add("HealthPerLevel", Ini.integer((s, v) -> s.healthPerLevel = v))
                    .add("DamagePercentPerLevel", Ini.integer((s, v) -> s.damagePercentPerLevel = v))
                    .add("ArmourPercentPerLevel", Ini.integer((s, v) -> s.armourPercentPerLevel = v))
                    .add("MinDamageTakenPercent", Ini.integer((s, v) -> s.minDamageTakenPercent = v))
                    .add("LevelUpBannerFrames", Ini.integer((s, v) -> s.levelUpBannerFrames = v));

    // ---- layout ----

    public int mapWidth() {
        return mapWidth;
    }

    public int mapHeight() {
        return mapHeight;
    }

    public int minRooms() {
        return minRooms;
    }

    public int maxRooms() {
        return maxRooms;
    }

    public int minRoomSize() {
        return minRoomSize;
    }

    public int maxRoomSize() {
        return maxRoomSize;
    }

    public int roomGap() {
        return roomGap;
    }

    public int placementAttempts() {
        return placementAttempts;
    }

    public int minSkeletonsPerRoom() {
        return minSkeletonsPerRoom;
    }

    public int maxSkeletonsPerRoom() {
        return maxSkeletonsPerRoom;
    }

    // ---- behaviour ----

    public float skeletonSenseRadius() {
        return skeletonSenseRadius;
    }

    public float skeletonChaseRadius() {
        return skeletonChaseRadius;
    }

    public int skeletonRepathFrames() {
        return skeletonRepathFrames;
    }

    /**
     * How close a fighter walks before stopping to let its weapon work. Shorter
     * than any weapon's reach on purpose — see {@code dungeon.ini}.
     */
    public float closeDistance() {
        return closeDistance;
    }

    public int heroRepathFrames() {
        return heroRepathFrames;
    }

    // ---- run loop ----

    public int respawnDelayFrames() {
        return respawnDelayFrames;
    }

    // ---- leveling ----

    /** The progression rules, as one value the leveling code can be handed. */
    public Levelling levelling() {
        return new Levelling(maxLevel, xpBase, xpStep, healthPerLevel,
                damagePercentPerLevel, armourPercentPerLevel, minDamageTakenPercent);
    }

    /** How long "Level 2!" stays on screen, in logic frames. */
    public int levelUpBannerFrames() {
        return levelUpBannerFrames;
    }

    // ---- monsters and depth ----

    /** Every kind the file describes, in file order. */
    public java.util.List<MonsterKind> monsters() {
        return java.util.List.copyOf(monsters);
    }

    /** One kind by name, or {@code null} if the file never described it. */
    public MonsterKind monster(String name) {
        for (var kind : monsters) {
            if (kind.name().equals(name)) {
                return kind;
            }
        }
        return null;
    }

    /**
     * The kinds that can fill a room at this depth: deep enough to have appeared,
     * and carrying a weight, which is how the file says "placed deliberately, not
     * scattered" — the boss has none.
     */
    public java.util.List<MonsterKind> roomFillersAt(int depth) {
        var available = new java.util.ArrayList<MonsterKind>();
        for (var kind : monsters) {
            if (kind.weight() > 0 && kind.minDepth() <= depth) {
                available.add(kind);
            }
        }
        return available;
    }

    public MonsterKind boss() {
        return monster(BOSS);
    }

    /** What a monster's health, damage or numbers are multiplied by at this depth. */
    public float monsterHealthAt(int depth) {
        return scaled(monsterHealthPercentPerDepth, depth);
    }

    public float monsterDamageAt(int depth) {
        return scaled(monsterDamagePercentPerDepth, depth);
    }

    public float monsterCountAt(int depth) {
        return scaled(monsterCountPercentPerDepth, depth);
    }

    public float bossHealthAt(int depth) {
        return scaled(bossHealthPercentPerDepth, depth);
    }

    public float bossDamageAt(int depth) {
        return scaled(bossDamagePercentPerDepth, depth);
    }

    public float experienceAt(int depth) {
        return scaled(experiencePercentPerDepth, depth);
    }

    /**
     * Linear growth from the first depth: {@code 1 + (depth - 1) * percent / 100}.
     *
     * <p>Computed from the depth in one step rather than compounded, so the tenth
     * floor is the same whether you arrived by playing or by asking.
     */
    private static float scaled(int percentPerDepth, int depth) {
        return 1f + Math.max(0, depth - 1) * percentPerDepth / 100f;
    }
}
