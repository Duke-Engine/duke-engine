package uz.duke.dungeon.content;

import java.util.List;
import java.util.Map;
import uz.duke.core.ini.FieldParseTable;
import uz.duke.core.ini.Ini;
import uz.duke.dungeon.skill.Skill;
import uz.duke.dungeon.skill.SkillEffect;
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
    private int corridorWidth = 2;
    private int maxRoomSpacing = 24;
    private int minSkeletonsPerRoom = 2;
    private int maxSkeletonsPerRoom = 6;

    // ---- behaviour ----

    private float skeletonSenseRadius = 90f;
    private float skeletonChaseRadius = 150f;
    private int skeletonRepathFrames = 10;
    private float closeDistance = 4f;
    private int heroRepathFrames = 10;
    private String arrowTemplate = "Arrow";
    private float arrowSpeed = 260f;
    private float arrowMuzzleOffset = 5f;

    /** How far in front of an archer his arrow appears — the bow, not his chest. */
    public float arrowMuzzleOffset() {
        return arrowMuzzleOffset;
    }

    /** The creature an archer's shot becomes once it is in the air. */
    public String arrowTemplate() {
        return arrowTemplate;
    }

    /** How fast it travels, in world units per second. */
    public float arrowSpeed() {
        return arrowSpeed;
    }

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

    /** Every hero's skills, in file order — the order a HUD lists them in. */
    private final java.util.List<Skill> skills = new java.util.ArrayList<>();

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
        // Entries rather than Map.of: that stops at ten pairs, and the file has
        // more blocks than that now. Nothing else about this changed.
        var ini = Ini.of(iniText, Map.ofEntries(
                Map.entry("DungeonGeneration", reader -> {
                    reader.getNextToken(); // the block's name, which we do not need
                    reader.initFromIni(settings, LAYOUT);
                }),
                Map.entry("DungeonCombat", reader -> {
                    reader.getNextToken();
                    reader.initFromIni(settings, BEHAVIOUR);
                }),
                Map.entry("DungeonRun", reader -> {
                    reader.getNextToken();
                    reader.initFromIni(settings, RUN);
                }),
                Map.entry("DungeonLeveling", reader -> {
                    reader.getNextToken();
                    reader.initFromIni(settings, LEVELLING);
                }),
                // Repeatable: the block's name is the monster's, so the list of
                // kinds is the file's, not a constant somewhere in Java.
                Map.entry("DungeonMonster", reader -> {
                    var kind = new MonsterBuilder(reader.getNextToken());
                    reader.initFromIni(kind, MONSTER);
                    settings.monsters.add(kind.build());
                }),
                Map.entry("DungeonDepth", reader -> {
                    reader.getNextToken();
                    reader.initFromIni(settings, DEPTH);
                }),
                Map.entry("DungeonTiles", reader -> {
                    reader.getNextToken();
                    reader.initFromIni(settings, TILES);
                }),
                Map.entry("DungeonAnimations", reader -> {
                    reader.getNextToken();
                    reader.initFromIni(settings, ANIMATIONS);
                }),
                Map.entry("DungeonHero", reader -> {
                    reader.getNextToken();
                    reader.initFromIni(settings, HERO_LOOK);
                }),
                Map.entry("DungeonArrow", reader -> {
                    reader.getNextToken();
                    reader.initFromIni(settings, ARROW_LOOK);
                }),
                Map.entry("DungeonHud", reader -> {
                    reader.getNextToken();
                    reader.initFromIni(settings, HUD);
                }),
                // Repeatable, and named by whose skill it is: the block header is
                // the hero's template and the key that casts it. A second hero is
                // four more of these and no Java — the roster lives in the file.
                Map.entry("DungeonSkill", (Ini.BlockParser) reader -> {
                    var skill = new SkillBuilder(reader.getNextToken(), reader.getNextToken());
                    reader.initFromIni(skill, SKILL);
                    settings.skills.add(skill.build());
                })));
        ini.load();
        if (!readingShippedFile) {
            settings.fillInMissingMonsters();
            settings.fillInMissingSkills();
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

    /**
     * The same rule for skills, keyed by hero <em>and</em> key rather than by
     * name: naming one hero's Q re-tunes that one skill and leaves the other
     * three, and every other hero, alone.
     */
    private void fillInMissingSkills() {
        var declared = new java.util.ArrayList<>(skills);
        skills.clear();
        for (var shipped : shippedSkills()) {
            var override = declared.stream()
                    .filter(skill -> skill.heroTemplate().equals(shipped.heroTemplate())
                            && skill.key() == shipped.key())
                    .findFirst();
            skills.add(override.orElse(shipped));
            override.ifPresent(declared::remove);
        }
        skills.addAll(declared); // skills — and heroes — this file invented
    }

    private static final List<MonsterKind> SHIPPED_MONSTERS = new java.util.ArrayList<>();
    private static final List<Skill> SHIPPED_SKILLS = new java.util.ArrayList<>();
    private static boolean readingShippedFile;

    /**
     * The monster list from the shipped file, read once and kept.
     *
     * <p>Read by parsing that file the ordinary way — a second, partial parser
     * would be a second thing to keep in step with the first. The flag is what
     * stops that parse from asking itself the same question forever.
     */
    private static List<MonsterKind> shippedMonsters() {
        readShippedFile();
        return SHIPPED_MONSTERS;
    }

    private static List<Skill> shippedSkills() {
        readShippedFile();
        return SHIPPED_SKILLS;
    }

    private static void readShippedFile() {
        if (!SHIPPED_MONSTERS.isEmpty() || readingShippedFile) {
            return;
        }
        readingShippedFile = true;
        try {
            var shipped = parse(Content.read(Content.SETTINGS));
            SHIPPED_MONSTERS.addAll(shipped.monsters);
            SHIPPED_SKILLS.addAll(shipped.skills);
        } finally {
            readingShippedFile = false;
        }
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
        require(corridorWidth >= 1, "a corridor narrower than one cell is a wall");
        require(maxRoomSpacing > maxRoomSize, "rooms could never reach one another");
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
                    .add("CorridorWidth", Ini.integer((s, v) -> s.corridorWidth = v))
                    .add("MaxRoomSpacing", Ini.integer((s, v) -> s.maxRoomSpacing = v))
                    .add("MinSkeletonsPerRoom", Ini.integer((s, v) -> s.minSkeletonsPerRoom = v))
                    .add("MaxSkeletonsPerRoom", Ini.integer((s, v) -> s.maxSkeletonsPerRoom = v));

    private static final FieldParseTable<DungeonSettings> BEHAVIOUR =
            new FieldParseTable<DungeonSettings>()
                    .add("SkeletonSenseRadius", Ini.real((s, v) -> s.skeletonSenseRadius = v))
                    .add("SkeletonChaseRadius", Ini.real((s, v) -> s.skeletonChaseRadius = v))
                    .add("SkeletonRepathFrames", Ini.integer((s, v) -> s.skeletonRepathFrames = v))
                    .add("CloseDistance", Ini.real((s, v) -> s.closeDistance = v))
                    .add("HeroRepathFrames", Ini.integer((s, v) -> s.heroRepathFrames = v))
                    .add("ArrowTemplate", Ini.string((s, v) -> s.arrowTemplate = v))
                    .add("ArrowSpeed", Ini.real((s, v) -> s.arrowSpeed = v))
                    .add("ArrowMuzzleOffset", Ini.real((s, v) -> s.arrowMuzzleOffset = v));

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
        String model;
        String texture;
        float modelScale = 1f;
        int tint = 0xFFFFFF;
        float facing = 90f;
        String idle;
        String walk;
        String attack;

        MonsterBuilder(String name) {
            this.name = name;
        }

        MonsterKind build() {
            return new MonsterKind(name, senseRadius, chaseRadius, closeDistance,
                    repathFrames, minDepth, weight, colour, scale,
                    new MonsterLook(model, texture, modelScale, tint, facing, idle, walk, attack));
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
                    .add("Scale", Ini.real((m, v) -> m.scale = v))
                    // What it is drawn as. Read here rather than in a block of its
                    // own so a new monster stays one block: its behaviour and its
                    // appearance are written together, where they are decided.
                    .add("Model", Ini.string((m, v) -> m.model = v))
                    .add("Texture", Ini.string((m, v) -> m.texture = v))
                    .add("ModelScale", Ini.real((m, v) -> m.modelScale = v))
                    .add("Tint", (ini, m) -> m.tint = Integer.decode(ini.getNextToken()))
                    .add("Facing", Ini.real((m, v) -> m.facing = v))
                    .add("Idle", Ini.string((m, v) -> m.idle = v))
                    .add("Walk", Ini.string((m, v) -> m.walk = v))
                    .add("Attack", Ini.string((m, v) -> m.attack = v));

    /** Accumulates one {@code DungeonSkill <hero> <key>} block. */
    private static final class SkillBuilder {
        private final String heroTemplate;
        private final char key;
        SkillEffect effect = SkillEffect.STRIKE;
        float damage;
        float damagePerLevel;
        float radius;
        float range;
        float distance;
        int boostPercent;
        int boostPerLevel;
        int durationFrames;
        int cooldownFrames = 90;
        int cooldownPerLevel;
        int unlockLevel = 1;
        int windUpFrames;
        String projectile = "";

        SkillBuilder(String heroTemplate, String key) {
            this.heroTemplate = heroTemplate;
            this.key = Character.toUpperCase(key.charAt(0));
        }

        Skill build() {
            return new Skill(heroTemplate, key, effect, damage, damagePerLevel, radius, range,
                    distance, boostPercent, boostPerLevel, durationFrames, cooldownFrames,
                    cooldownPerLevel, unlockLevel, windUpFrames, projectile);
        }
    }

    private static final FieldParseTable<SkillBuilder> SKILL =
            new FieldParseTable<SkillBuilder>()
                    .add("Effect", Ini.enumeration(SkillEffect.class, (s, v) -> s.effect = v))
                    .add("Damage", Ini.real((s, v) -> s.damage = v))
                    .add("DamagePerLevel", Ini.real((s, v) -> s.damagePerLevel = v))
                    .add("Radius", Ini.real((s, v) -> s.radius = v))
                    .add("Range", Ini.real((s, v) -> s.range = v))
                    .add("Distance", Ini.real((s, v) -> s.distance = v))
                    .add("BoostPercent", Ini.integer((s, v) -> s.boostPercent = v))
                    .add("BoostPerLevel", Ini.integer((s, v) -> s.boostPerLevel = v))
                    .add("DurationFrames", Ini.integer((s, v) -> s.durationFrames = v))
                    .add("CooldownFrames", Ini.integer((s, v) -> s.cooldownFrames = v))
                    .add("CooldownPerLevel", Ini.integer((s, v) -> s.cooldownPerLevel = v))
                    .add("UnlockLevel", Ini.integer((s, v) -> s.unlockLevel = v))
                    .add("WindUpFrames", Ini.integer((s, v) -> s.windUpFrames = v))
                    .add("Projectile", Ini.string((s, v) -> s.projectile = v));

    /**
     * The modular kit the floor is drawn from, with each piece's full asset path.
     *
     * <p>Look rather than rule, like a monster's colour and size in the same file:
     * the simulation never reads it. It is here because it is a thing someone
     * retunes — swap the kit, swap the dungeon's whole appearance — and that is
     * what this file is for.
     *
     * @param floor  {@code null} when no kit is named, meaning plain blocks
     */
    public record TileArt(String floor, String wall, String corner, float tileSize) {
    }

    private String tileFolder = "";
    private String tileFloor;
    private String tileWall;
    private String tileCorner;
    private float tileSize = 4f;

    /** The kit to draw the floor with; {@code floor()} is null if the file named none. */
    public TileArt tiles() {
        return new TileArt(path(tileFloor), path(tileWall), path(tileCorner), tileSize);
    }

    private String path(String piece) {
        return piece == null ? null : tileFolder + piece;
    }

    private static final FieldParseTable<DungeonSettings> TILES =
            new FieldParseTable<DungeonSettings>()
                    .add("Folder", Ini.string((s, v) -> s.tileFolder = v))
                    .add("Floor", Ini.string((s, v) -> s.tileFloor = v))
                    .add("Wall", Ini.string((s, v) -> s.tileWall = v))
                    .add("Corner", Ini.string((s, v) -> s.tileCorner = v))
                    .add("TileSize", Ini.real((s, v) -> s.tileSize = v));

    private String animationLibrary;
    private String defaultIdle;
    private String defaultWalk;
    private String defaultAttack;
    private String defaultDeath;

    /**
     * The file every monster's animations are taken from, or {@code null} for
     * none.
     *
     * <p>One library for the whole bestiary, because a creature kit and an
     * animation library meet on a shared skeleton — so what animates one monster
     * animates all of them, and a new monster needs no animation work at all.
     */
    public String animationLibrary() {
        return animationLibrary;
    }

    /** A kind's look with the game's default clip names filled in. */
    /** The clip every monster plays as it falls, or {@code null} for none. */
    public String deathClip() {
        return defaultDeath;
    }

    public MonsterLook lookOf(MonsterKind kind) {
        return kind.look().withDefaults(defaultIdle, defaultWalk, defaultAttack);
    }

    private String heroModel;
    private String heroTexture;
    private float heroModelScale = 1f;
    private float heroFacing = 90f;
    private String heroIdleFrom;
    private String heroWalkFrom;
    private String heroAttackFrom;
    private String heroDeathFrom;

    /**
     * What the hero is drawn as. {@link HeroLook#NONE} when the file names no
     * model, and then he is a coloured shape as he was before there was one.
     */
    public HeroLook hero() {
        return heroModel == null ? HeroLook.NONE
                : new HeroLook(heroModel, heroTexture, heroModelScale, heroFacing,
                        heroIdleFrom, heroWalkFrom, heroAttackFrom, heroDeathFrom);
    }

    private String arrowModel;
    private String arrowPart;
    private float arrowScale = 1f;
    private float arrowFacing = 90f;
    private float arrowHeight;
    private int arrowTint = 0xFFFFFF;

    /**
     * What an arrow is drawn as: one named mesh out of a model file, or nothing,
     * in which case it falls back to a coloured shape.
     *
     * @param part the name <em>inside</em> the file, which need not be a sensible
     *             one — see the block's comment in {@code dungeon.ini}
     */
    public record ArrowLook(String model, String part, float scale, float facing,
            float height, int tint) {
        public boolean hasModel() {
            return model != null && part != null;
        }

        public java.awt.Color awtTint() {
            return new java.awt.Color(tint);
        }
    }

    public ArrowLook arrowLook() {
        return new ArrowLook(arrowModel, arrowPart, arrowScale, arrowFacing,
                arrowHeight, arrowTint);
    }

    private String heavyArrowTemplate = "HeavyArrow";
    private float heavyArrowScale = 16f;
    private float heavyArrowSpeed = 120f;
    private int heavyArrowTint = 0xE8A33D;

    /** The creature a drawn shot becomes — the same shaft, drawn bigger. */
    public String heavyArrowTemplate() {
        return heavyArrowTemplate;
    }

    /**
     * Slower than an ordinary arrow, on purpose. It is the one shot the player
     * chose to spend, so it is the one worth watching cross the room.
     */
    public float heavyArrowSpeed() {
        return heavyArrowSpeed;
    }

    /** The same model and part as an ordinary arrow, bigger and lit differently. */
    public ArrowLook heavyArrowLook() {
        return new ArrowLook(arrowModel, arrowPart, heavyArrowScale, arrowFacing,
                arrowHeight, heavyArrowTint);
    }

    private static final FieldParseTable<DungeonSettings> ARROW_LOOK =
            new FieldParseTable<DungeonSettings>()
                    .add("Model", Ini.string((s, v) -> s.arrowModel = v))
                    .add("Part", Ini.string((s, v) -> s.arrowPart = v))
                    .add("Scale", Ini.real((s, v) -> s.arrowScale = v))
                    .add("Facing", Ini.real((s, v) -> s.arrowFacing = v))
                    .add("Height", Ini.real((s, v) -> s.arrowHeight = v))
                    .add("Tint", (ini, s) -> s.arrowTint = Integer.decode(ini.getNextToken()))
                    .add("HeavyTemplate", Ini.string((s, v) -> s.heavyArrowTemplate = v))
                    .add("HeavyScale", Ini.real((s, v) -> s.heavyArrowScale = v))
                    .add("HeavySpeed", Ini.real((s, v) -> s.heavyArrowSpeed = v))
                    .add("HeavyTint",
                            (ini, s) -> s.heavyArrowTint = Integer.decode(ini.getNextToken()));

    private String hudDepthWord = "DEPTH";
    private String hudRankSuffix = "-lv";

    /** The word under the depth numeral on the hero's panel. */
    public String hudDepthWord() {
        return hudDepthWord;
    }

    /**
     * What turns a level into the words beside his name — added straight onto the
     * number, so "-daraja" makes "7-daraja" and " lv" would make "7 lv".
     */
    public String hudRankSuffix() {
        return hudRankSuffix;
    }

    private static final FieldParseTable<DungeonSettings> HUD =
            new FieldParseTable<DungeonSettings>()
                    .add("DepthWord", Ini.restOfLine((s, v) -> s.hudDepthWord = v))
                    .add("RankSuffix", Ini.restOfLine((s, v) -> s.hudRankSuffix = v));

    private static final FieldParseTable<DungeonSettings> HERO_LOOK =
            new FieldParseTable<DungeonSettings>()
                    .add("Model", Ini.string((s, v) -> s.heroModel = v))
                    .add("Texture", Ini.string((s, v) -> s.heroTexture = v))
                    .add("ModelScale", Ini.real((s, v) -> s.heroModelScale = v))
                    .add("Facing", Ini.real((s, v) -> s.heroFacing = v))
                    // Files rather than clip names: one movement per file is how
                    // animation sites hand their work out, and every such file
                    // carries the same exporter-generated name inside it.
                    .add("IdleFrom", Ini.string((s, v) -> s.heroIdleFrom = v))
                    .add("WalkFrom", Ini.string((s, v) -> s.heroWalkFrom = v))
                    .add("AttackFrom", Ini.string((s, v) -> s.heroAttackFrom = v))
                    .add("DeathFrom", Ini.string((s, v) -> s.heroDeathFrom = v));

    private static final FieldParseTable<DungeonSettings> ANIMATIONS =
            new FieldParseTable<DungeonSettings>()
                    .add("Library", Ini.string((s, v) -> s.animationLibrary = v))
                    .add("Idle", Ini.string((s, v) -> s.defaultIdle = v))
                    .add("Walk", Ini.string((s, v) -> s.defaultWalk = v))
                    .add("Attack", Ini.string((s, v) -> s.defaultAttack = v))
                    .add("Death", Ini.string((s, v) -> s.defaultDeath = v));

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

    /** Corridor width in cells — wide enough for the largest creature to pass. */
    public int corridorWidth() {
        return corridorWidth;
    }

    /** How far a new room may sit from the nearest already placed, in cells. */
    public int maxRoomSpacing() {
        return maxRoomSpacing;
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

    /** Every skill in the file, whoever it belongs to. */
    public java.util.List<Skill> skills() {
        return java.util.List.copyOf(skills);
    }

    /**
     * The skills of one hero, in file order — which is the order a HUD lists
     * them, so writing Q W E R in the file is what puts them in that order on
     * screen. A template with none simply has none.
     */
    public java.util.List<Skill> skillsFor(String heroTemplate) {
        var his = new java.util.ArrayList<Skill>();
        for (var skill : skills) {
            if (skill.heroTemplate().equals(heroTemplate)) {
                his.add(skill);
            }
        }
        return java.util.List.copyOf(his);
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
