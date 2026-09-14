package uz.duke.dungeon.content;

import java.util.List;
import java.util.Map;
import uz.duke.core.ini.FieldParseTable;
import uz.duke.core.ini.Ini;
import uz.duke.dungeon.loot.Loot;
import uz.duke.dungeon.loot.LootKind;
import uz.duke.dungeon.skill.Skill;
import uz.duke.dungeon.skill.SkillEffect;
import uz.duke.dungeon.level.Attribute;
import uz.duke.dungeon.level.AttributeRules;
import uz.duke.dungeon.level.Attributes;
import uz.duke.dungeon.level.HeroAttributes;
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

    // ---- height ----
    //
    // "Storey" rather than level or floor: a level is what the hero has, and a
    // floor is a whole dungeon at a depth. This is how high a room stands.

    private int maxStorey = 2;
    private int storeyChangePercent = 45;
    private float storeyHeight = 6f;
    private int stairLength = 1;
    private int entranceStorey = 0;
    private int bossStorey = 2;

    // ---- behaviour ----

    private float skeletonSenseRadius = 90f;
    private float skeletonChaseRadius = 150f;
    private int skeletonRepathFrames = 10;
    private float closeDistance = 4f;
    private int heroRepathFrames = 10;
    private float wayAheadProbe = 5f;
    private float retreatTurnDegrees = 30f;
    private int retreatTurns = 3;
    private float summonTurnDegrees = 45f;
    private int summonTurns = 4;
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
    private int descendDelayFrames = 75;
    private int victoryFrames = 150;
    private String diedWord = "You died";
    private String wonWord = "You won";

    /**
     * What the banner says between floors, with the floor's number put in for
     * {@code %d}.
     *
     * <p>A word rather than a string built in Java, which is what it was: the only
     * thing the player reads that was written in English in a source file while
     * every other word he sees came out of this file.
     */
    private String nextDepthWord = "Depth %d";

    // ---- leveling ----

    private int maxLevel = 10;

    /**
     * How far the ordinary skills may drift apart, in ranks.
     *
     * <p>What stops one of them being taken four times running while the other
     * two sit at nothing. See {@code SkillRanks}.
     */
    private int skillSpread = 2;

    /** How far the ordinary skills may drift apart. See {@code SkillRanks}. */
    public int skillSpread() {
        return skillSpread;
    }
    private int xpBase = 30;
    private int xpStep = 15;
    private int armourPercentPerLevel = 5;
    private int minDamageTakenPercent = 40;
    private int manaPerKill;

    // ---- what a point of each attribute is worth, in hundredths ----

    private int healthPerStrength;
    private int speedPerAgility;
    private int manaPerIntelligence;
    private int damagePerPrimary;

    /** Mana given back for a kill; 0 is off, which is the shipped setting. */
    public int manaPerKill() {
        return manaPerKill;
    }
    private int levelUpBannerFrames = 60;

    // ---- monsters and depth ----

    /** In file order, which is the order a seed picks through them. */
    private final java.util.List<MonsterKind> monsters = new java.util.ArrayList<>();

    /** Every hero's skills, in file order — the order a HUD lists them in. */
    private final java.util.List<Skill> skills = new java.util.ArrayList<>();

    /** Everything that can be found on a floor, in file order. */
    private final java.util.List<Loot> loot = new java.util.ArrayList<>();

    private String lootTemplate = "";
    private int lootDropPercent = 20;
    private int lootBossDropPercent = 100;
    private float lootPickupRange = 14f;
    private int lootValuePercentPerDepth = 20;
    private int lootNoteFrames = 90;

    private int monsterHealthPercentPerDepth = 25;
    private int monsterDamagePercentPerDepth = 15;
    private int monsterCountPercentPerDepth = 20;
    private int bossHealthPercentPerDepth = 40;
    private int bossDamagePercentPerDepth = 25;
    private int experiencePercentPerDepth = 30;

    /**
     * The kind placed in the furthest room when the file names no others. Named,
     * not flagged, so it is findable.
     */
    public static final String BOSS = "Boss";

    /**
     * One boss per depth, in order, from {@code DungeonDepth Descent}.
     *
     * <p>Empty means the descent has no bottom: one boss, the same one every
     * floor, going down for ever — which is what this game did before it had an
     * ending.
     */
    private final java.util.List<String> bosses = new java.util.ArrayList<>();

    /** Who stands in the boss's room with it, in file order: see {@link #bossGuardsAt}. */
    private final java.util.List<BossGuard> bossGuards = new java.util.ArrayList<>();

    /** How many cells out from the boss its guard stands. */
    private int bossGuardRing = 2;

    /**
     * Which kind waits in the furthest room at this depth.
     *
     * <p>Clamped rather than wrapped, because past the last boss there is no floor
     * to be on: {@link #finalDepth()} is where the descent stops.
     */
    public String bossKindAt(int depth) {
        return bosses.isEmpty() ? BOSS
                : bosses.get(Math.clamp(depth, 1, bosses.size()) - 1);
    }

    /** The boss of this depth, whole. */
    public MonsterKind bossAt(int depth) {
        return monster(bossKindAt(depth));
    }

    /** One kind the boss is guarded by, and how many of it. */
    public record BossGuard(String kind, int count) {
    }

    /**
     * Who stands with the boss at this depth: each guard the file names whose kind is
     * deep enough to have appeared at all -- so a shallow boss still waits alone.
     */
    public java.util.List<BossGuard> bossGuardsAt(int depth) {
        var here = new java.util.ArrayList<BossGuard>();
        for (var guard : bossGuards) {
            var kind = monster(guard.kind());
            if (kind != null && kind.minDepth() <= depth) {
                here.add(guard);
            }
        }
        return here;
    }

    /** How many cells out from the boss its guard stands; see {@code DungeonGenerator}. */
    public int bossGuardRing() {
        return bossGuardRing;
    }

    /**
     * The depth the last boss stands on, or {@code 0} for a descent with no
     * bottom.
     *
     * <p>Derived from the list rather than given a number of its own, so there is
     * no second figure to keep in step with it: the bosses <em>are</em> the floors.
     */
    public int finalDepth() {
        return bosses.size();
    }

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
                // Which stage this build opens on, if it opens on one at all. A
                // blank file is the endless dungeon, which is what the game is
                // when nobody has said otherwise.
                Map.entry("DungeonStage", reader -> {
                    reader.getNextToken();
                    reader.initFromIni(settings, STAGE);
                }),
                Map.entry("DungeonLeveling", reader -> {
                    reader.getNextToken();
                    reader.initFromIni(settings, LEVELLING);
                }),
                // What a point of strength, agility and intelligence is worth to
                // every hero. Which of them each hero has, and how fast they grow,
                // is in his own DungeonHero block.
                Map.entry("DungeonAttributes", reader -> {
                    reader.getNextToken();
                    reader.initFromIni(settings, ATTRIBUTES);
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
                // Repeatable and named after the creature template he is, exactly
                // as DungeonSkill already is: his four skill blocks are headed
                // with the same word. A second hero is a block here, four there,
                // and no Java.
                Map.entry("DungeonHero", (Ini.BlockParser) reader -> {
                    var hero = new HeroBuilder(reader.getNextToken());
                    reader.initFromIni(hero, HERO_LOOK);
                    settings.heroes.add(hero);
                }),
                // And what he looks like in the panel's frame, alive. Named after
                // the same template, and separate from the block above because it
                // is asking a different question: that one is what he is made of,
                // this one is where the little camera stands and what he does in
                // front of it.
                Map.entry("DungeonPortrait", (Ini.BlockParser) reader -> {
                    var portrait = new PortraitBuilder(reader.getNextToken());
                    reader.initFromIni(portrait, PORTRAIT);
                    settings.portraits.add(portrait);
                }),
                // And the one every selectable creature gets, which is what makes
                // the whole bestiary a block rather than a block each: the camera
                // is written in fractions of whatever it is looking at, and a
                // creature's own idle and death are already bound on it.
                Map.entry("DungeonPortraits", reader -> {
                    reader.getNextToken();
                    settings.portraitsDeclared = true;
                    reader.initFromIni(settings.everyPortrait, PORTRAIT);
                }),
                Map.entry("DungeonArrow", reader -> {
                    reader.getNextToken();
                    reader.initFromIni(settings, ARROW_LOOK);
                }),
                // How each thing in flight is drawn, and what it burns like. Named
                // and repeatable, like the monsters and the themes, and for the
                // same reason: a fourth projectile is a block here and no Java.
                Map.entry("DungeonProjectile", (Ini.BlockParser) reader -> {
                    var projectile = new ProjectileBuilder(reader.getNextToken());
                    reader.initFromIni(projectile, PROJECTILE);
                    settings.projectiles.add(projectile);
                }),
                Map.entry("DungeonEffect", (Ini.BlockParser) reader -> {
                    var effect = new EffectBuilder(reader.getNextToken());
                    reader.initFromIni(effect, EFFECT);
                    settings.effects.add(effect);
                }),
                // What the hero panel's edges are painted with. Named after the
                // part of the panel it paints, and every one of them optional:
                // a part nobody names keeps the carved look it always had.
                // What the mouse pointer looks like in one situation. Named by
                // the situation, because the client owns those and the game owns
                // the pictures -- see Cursors.
                Map.entry("DungeonCursor", (Ini.BlockParser) reader -> {
                    var pointer = new CursorBuilder(reader.getNextToken());
                    reader.initFromIni(pointer, CURSOR);
                    settings.cursors.add(pointer);
                }),
                Map.entry("DungeonSkin", (Ini.BlockParser) reader -> {
                    var piece = new SkinBuilder(reader.getNextToken());
                    reader.initFromIni(piece, SKIN);
                    settings.skin.add(piece);
                }),
                // A layer of an effect, named by the effect and then by itself, as a
                // tone is named by its theme. In file order, which is draw order.
                Map.entry("DungeonEffectLayer", (Ini.BlockParser) reader -> {
                    var layer = new LayerBuilder(reader.getNextToken(), reader.getNextToken());
                    reader.initFromIni(layer, LAYER);
                    settings.effectLayers.add(layer);
                }),
                // What one of the run's own moments plays on the hero: a level, the
                // boss down, a floor reached.
                Map.entry("DungeonMoment", (Ini.BlockParser) reader -> {
                    var moment = new MomentBuilder(reader.getNextToken());
                    reader.initFromIni(moment, MOMENT);
                    settings.moments.add(moment);
                }),
                Map.entry("DungeonEffects", reader -> {
                    reader.getNextToken();
                    reader.initFromIni(settings, EFFECT_BUDGET);
                }),
                Map.entry("DungeonHud", reader -> {
                    reader.getNextToken();
                    reader.initFromIni(settings, HUD);
                }),
                Map.entry("DungeonUnitBar", reader -> {
                    reader.getNextToken();
                    reader.initFromIni(settings, UNIT_BAR);
                }),
                Map.entry("DungeonMenu", reader -> {
                    reader.getNextToken();
                    reader.initFromIni(settings, MENU);
                }),
                // How a floor looks, and which floor looks like what. Repeatable
                // and named, the same way monsters and skills are: a fourth theme
                // is three more blocks here and a folder of models.
                Map.entry("DungeonTheme", (Ini.BlockParser) reader -> {
                    var theme = new ThemeBuilder(reader.getNextToken());
                    reader.initFromIni(theme, THEME);
                    settings.themes.add(theme);
                }),
                Map.entry("DungeonTone", (Ini.BlockParser) reader -> {
                    var tone = new ToneBuilder(reader.getNextToken(), reader.getNextToken());
                    reader.initFromIni(tone, TONE);
                    settings.tones.add(tone);
                }),
                Map.entry("DungeonThemeMonster", (Ini.BlockParser) reader -> {
                    var themed = new ThemeMonsterBuilder(
                            reader.getNextToken(), reader.getNextToken());
                    reader.initFromIni(themed, THEME_MONSTER);
                    settings.themeMonsters.add(themed);
                }),
                Map.entry("DungeonThemes", reader -> {
                    reader.getNextToken();
                    reader.initFromIni(settings, THEME_ORDER);
                }),
                // What stands about in the rooms. Named and repeatable like the
                // monsters, and for the same reason: a fourth kind of thing to
                // walk round is a block here and a template in props.ini.
                Map.entry("DungeonProp", (Ini.BlockParser) reader -> {
                    var prop = new PropBuilder(reader.getNextToken());
                    reader.initFromIni(prop, PROP);
                    settings.props.add(prop);
                }),
                Map.entry("DungeonProps", reader -> {
                    reader.getNextToken();
                    reader.initFromIni(settings, PROPS);
                }),
                // What the game sounds like. One block per moment, and the client
                // asks for moments by name -- it has never heard of a bow.
                Map.entry("DungeonSound", (Ini.BlockParser) reader -> {
                    var cue = new SoundBuilder(reader.getNextToken());
                    reader.initFromIni(cue, SOUND);
                    settings.sounds.add(cue);
                }),
                Map.entry("DungeonSounds", reader -> {
                    reader.getNextToken();
                    reader.initFromIni(settings, SOUNDS);
                }),
                Map.entry("DungeonFog", reader -> {
                    reader.getNextToken();
                    reader.initFromIni(settings, FOG);
                }),
                Map.entry("DungeonSun", reader -> {
                    reader.getNextToken();
                    reader.initFromIni(settings, SUN);
                }),
                Map.entry("DungeonCamera", reader -> {
                    reader.getNextToken();
                    reader.initFromIni(settings, CAMERA);
                }),
                Map.entry("DungeonOrderMark", reader -> {
                    reader.getNextToken();
                    reader.initFromIni(settings, ORDER_MARK);
                }),
                Map.entry("DungeonSkillRing", reader -> {
                    reader.getNextToken();
                    reader.initFromIni(settings, SKILL_RING);
                }),
                Map.entry("DungeonLoot", reader -> {
                    reader.getNextToken();
                    reader.initFromIni(settings, LOOT_RULES);
                }),
                // Repeatable, headed by the item's id: a new thing to find is a
                // block here and no Java.
                Map.entry("DungeonLootItem", (Ini.BlockParser) reader -> {
                    var item = new LootBuilder(reader.getNextToken());
                    reader.initFromIni(item, LOOT);
                    settings.loot.add(item.build());
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
            settings.fillInMissingLoot();
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

    /** The same rule again, keyed by the item's id. */
    private void fillInMissingLoot() {
        var declared = new java.util.ArrayList<>(loot);
        loot.clear();
        for (var shipped : shippedLoot()) {
            var override = declared.stream()
                    .filter(item -> item.id().equals(shipped.id()))
                    .findFirst();
            loot.add(override.orElse(shipped));
            override.ifPresent(declared::remove);
        }
        loot.addAll(declared); // items this file invented
    }

    private static final List<MonsterKind> SHIPPED_MONSTERS = new java.util.ArrayList<>();
    private static final List<Skill> SHIPPED_SKILLS = new java.util.ArrayList<>();
    private static final List<Loot> SHIPPED_LOOT = new java.util.ArrayList<>();
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

    private static List<Loot> shippedLoot() {
        readShippedFile();
        return SHIPPED_LOOT;
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
            SHIPPED_LOOT.addAll(shipped.loot);
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
        require(retreatTurns >= 0, "RetreatTurns cannot be negative");
        require(retreatTurnDegrees > 0f && retreatTurnDegrees * retreatTurns <= 180f,
                "RetreatTurnDegrees times RetreatTurns has to stay within a half turn");
        require(summonTurns >= 0, "SummonTurns cannot be negative");
        require(summonTurnDegrees > 0f && summonTurnDegrees * summonTurns <= 180f,
                "SummonTurnDegrees times SummonTurns has to stay within a half turn");
        for (var kind : monsters) {
            var name = "DungeonMonster " + kind.name();
            if (kind.hasSkill()) {
                var skill = skillsFor(kind.name()).stream()
                        .filter(one -> one.key() == kind.skillKey()).findFirst().orElse(null);
                require(skill != null, name + " casts " + kind.skillKey() + ", and no DungeonSkill "
                        + kind.name() + " " + kind.skillKey() + " says what that is");
                // A mending is cast on its own side, so how far off HE is means nothing to it.
                require(skill.effect() == SkillEffect.HEAL
                                || kind.skillNearest() >= 0f && kind.skillFurthest() > kind.skillNearest(),
                        name + " has to cast across some distance: SkillDistance nearest furthest");
            }
            require(kind.keepFurthest() == 0f
                            || kind.keepNearest() >= 0f && kind.keepFurthest() > kind.keepNearest(),
                    name + "'s KeepDistance has to be a band, nearest then furthest");
            require(kind.maxPerRoom() >= 0, name + "'s MaxPerRoom cannot be negative");
        }
        for (var guard : bossGuards) {
            require(monster(guard.kind()) != null,
                    "BossGuards names " + guard.kind() + ", and no DungeonMonster describes it");
            require(guard.count() >= 1, "BossGuards has to put at least one " + guard.kind() + " there");
        }
        require(bossGuardRing >= 1, "BossGuardRing has to stand the guard off the boss's own cell");
        require(corridorWidth >= 1, "a corridor narrower than one cell is a wall");
        require(maxRoomSpacing > maxRoomSize, "rooms could never reach one another");
        require(minPropsPerRoom >= 0, "a room cannot hold fewer than no things");
        require(maxPropsPerRoom >= minPropsPerRoom,
                "MaxPerRoom must not be below MinPerRoom");
        require(maxStorey >= 0, "MaxStorey cannot be negative");
        require(maxStorey <= 9, "a storey is one character in the level map, so 9 is the ceiling");
        require(storeyChangePercent >= 0 && storeyChangePercent <= 100,
                "StoreyChangePercent is a percentage");
        require(storeyHeight >= 0f, "StoreyHeight cannot be negative");
        // A stair narrower than the corridor it sits in is a bottleneck, and a
        // bottleneck is where the biggest creature wedges — see the corridor
        // width above, which is a correctness setting for the same reason.
        require(stairLength >= 1, "a stair of no cells is a cliff");
        // Not checked against MaxStorey: turning height off with MaxStorey = 0
        // should not then demand two more fields be edited to match. Both are
        // read back through the ceiling — see entranceStorey() and bossStorey().
        require(entranceStorey >= 0, "EntranceStorey cannot be negative");
        require(bossStorey >= 0, "BossStorey cannot be negative");
        require(respawnDelayFrames >= 0, "the death pause cannot be negative");
        require(descendDelayFrames >= 0, "the pause before descending cannot be negative");
        require(maxLevel >= Levelling.FIRST_LEVEL, "MaxLevel cannot be below the first level");
        require(xpBase > 0, "XpBase must be positive or no level is ever reached");
        require(xpStep >= 0, "XpStep cannot make later levels cheaper");
        require(armourPercentPerLevel >= 0, "a level cannot take armour away");
        require(healthPerStrength >= 0 && speedPerAgility >= 0 && manaPerIntelligence >= 0
                        && damagePerPrimary >= 0,
                "DungeonAttributes: a point of an attribute cannot take something away");
        for (var hero : heroes) {
            var name = "DungeonHero " + hero.name;
            require(hero.strength >= 0 && hero.agility >= 0 && hero.intelligence >= 0
                            && hero.strengthPerLevel >= 0 && hero.agilityPerLevel >= 0
                            && hero.intelligencePerLevel >= 0,
                    name + ": an attribute cannot be negative, and a level cannot take one away");
            boolean namesAny = hero.strength != 0 || hero.agility != 0 || hero.intelligence != 0
                    || hero.strengthPerLevel != 0 || hero.agilityPerLevel != 0
                    || hero.intelligencePerLevel != 0;
            require(hero.primary != null || !namesAny,
                    name + " has attributes and no Primary: say which of STR, AGI and INT he hits with");
            require(hero.manaRegen >= 0 && hero.healthRegen >= 0,
                    name + ": what comes back on its own cannot be negative");
        }
        // Each is a field on the status line, which splits on these two.
        require(sayable(hudStrengthWord) && sayable(hudAgilityWord)
                        && sayable(hudIntelligenceWord) && sayable(hudHealthWord),
                "the attribute words and HealthWord may not contain ',' or '|'");
        require(hudPrimaryWord.indexOf('|') < 0 && hudEachPointWord.indexOf('|') < 0,
                "PrimaryWord and EachPointWord may not contain '|'");
        require(minDamageTakenPercent > 0 && minDamageTakenPercent <= 100,
                "the damage floor must leave some way to lose");
        require(levelUpBannerFrames >= 0, "the level-up message cannot last negative frames");
        require(edgeScrollMargin >= 0, "the screen's edge cannot be a negative width");
        require(edgeScrollSpeedPercent >= 0, "a camera cannot be shoved backwards");
        require(fogUnseenPercent >= 0 && fogUnseenPercent <= 100,
                "UnseenPercent is a share of a lit room");
        require(fogRememberedPercent >= 0 && fogRememberedPercent <= 100,
                "RememberedPercent is a share of a lit room");
        require(fogVisiblePercent >= 0 && fogVisiblePercent <= 100,
                "VisiblePercent is a share of a lit room");
        require(fogUnseenPercent <= fogRememberedPercent
                        && fogRememberedPercent <= fogVisiblePercent,
                "the three shades have to darken in that order, or the map reads backwards");
        require(fogSoftenCells >= 0, "the fog cannot be smeared over negative cells");
        require(fogOpenPerSecond > 0, "fog that never opens is a black screen");
        require(fogTextureSize >= 8 && fogTextureSize <= 2048,
                "TextureSize is the fog sheet's own resolution, between 8 and 2048");
        require(lootDropPercent >= 0 && lootDropPercent <= 100,
                "DropPercent is a chance, not a count");
        require(lootBossDropPercent >= 0 && lootBossDropPercent <= 100,
                "BossDropPercent is a chance, not a count");
        require(lootPickupRange > 0, "something he can never reach is not loot");
        require(lootNoteFrames >= 0, "the pickup message cannot last negative frames");
        for (var item : loot) {
            require(sayable(item.name()),
                    "an item's Name may not contain ',' or '|': " + item.id());
        }
        for (var moment : moments) {
            require(!moment.effect.isBlank(), "DungeonMoment " + moment.name + " plays no Effect");
            require(moment.scale > 0f, "DungeonMoment " + moment.name + " has to be drawn at some size");
        }
        require(sayable(hudIconFolder), "IconFolder may not contain ',' or '|'");
        for (var skill : skills) {
            // The panel is told which picture to draw down the status line, and
            // the line is split on those two characters.
            require(sayable(skill.icon()),
                    "a skill's Icon may not contain ',' or '|': " + skill.key());
            if (skill.effect() == SkillEffect.HEAL) {
                var name = "DungeonSkill " + skill.heroTemplate() + " " + skill.key();
                require(skill.heal() > 0f && skill.range() > 0f, name + " mends nobody: it needs a Heal and a Range");
                require(skill.healBelowPercent() > 0 && skill.healBelowPercent() <= 100,
                        name + "'s HealBelowPercent is a share of health, from 1 to 100");
                require(skill.hasProjectile(), name + " has no light to call down: name it in Projectile");
            }
            if (skill.effect() == SkillEffect.SUMMON) {
                var name = "DungeonSkill " + skill.heroTemplate() + " " + skill.key();
                require(!skill.summons().isBlank() && skill.summonCount() >= 1 && skill.maxSummoned() >= 1,
                        name + " calls up nothing: it needs Summons, a SummonCount and a MaxSummoned");
                require(skill.radius() > 0f && skill.durationFrames() > 0,
                        name + " needs a Radius to call them up at and DurationFrames for them to last");
                require(skill.summonExperiencePercent() >= 0 && skill.summonExperiencePercent() <= 100,
                        name + "'s SummonExperiencePercent is a share, from 0 to 100");
                require(skill.hasProjectile(), name + " has no rift to open: name it in Projectile");
            }
        }
    }

    private static boolean sayable(String words) {
        return words != null && words.indexOf(',') < 0 && words.indexOf('|') < 0;
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
                    .add("MaxSkeletonsPerRoom", Ini.integer((s, v) -> s.maxSkeletonsPerRoom = v))
                    .add("MaxStorey", Ini.integer((s, v) -> s.maxStorey = v))
                    .add("StoreyChangePercent", Ini.integer((s, v) -> s.storeyChangePercent = v))
                    .add("StoreyHeight", Ini.real((s, v) -> s.storeyHeight = v))
                    .add("StairLength", Ini.integer((s, v) -> s.stairLength = v))
                    .add("EntranceStorey", Ini.integer((s, v) -> s.entranceStorey = v))
                    .add("BossStorey", Ini.integer((s, v) -> s.bossStorey = v));

    // ---- what the game sounds like ----

    private final java.util.List<SoundBuilder> sounds = new java.util.ArrayList<>();
    private String soundFolder = "";
    private float voiceGapSeconds = 1.5f;

    private static final class SoundBuilder {
        private final String name;
        String channel = "Effects";
        boolean positional = true;
        float gain = 1f;
        float gap;
        String label;
        final java.util.List<String> files = new java.util.ArrayList<>();

        SoundBuilder(String name) {
            this.name = name;
        }
    }

    private static final FieldParseTable<SoundBuilder> SOUND =
            new FieldParseTable<SoundBuilder>()
                    .add("Channel", Ini.string((s, v) -> s.channel = v))
                    .add("Positional", Ini.bool((s, v) -> s.positional = v))
                    .add("Gain", Ini.real((s, v) -> s.gain = v))
                    .add("GapSeconds", Ini.real((s, v) -> s.gap = v))
                    // Repeated on purpose: each File line adds one more way this
                    // moment can sound, and a moment heard a hundred times a run
                    // wants several.
                    .add("File", Ini.string((s, v) -> s.files.add(v)))
                    // Only the music is ever named on screen, and only because
                    // the player picks from it -- so a label is optional and the
                    // rest of the cues never write one.
                    .add("Label", Ini.restOfLine((s, v) -> s.label = v));

    private static final FieldParseTable<DungeonSettings> SOUNDS =
            new FieldParseTable<DungeonSettings>()
                    .add("Folder", Ini.string((s, v) -> s.soundFolder = v))
                    .add("VoiceGapSeconds", Ini.real((s, v) -> s.voiceGapSeconds = v));

    /** Every moment the game has a sound for, each file path made whole. */
    public java.util.List<SoundArt> sounds() {
        return sounds.stream()
                .map(cue -> new SoundArt(cue.name, cue.channel, cue.positional, cue.gain,
                        cue.gap, cue.files, cue.label).withFolder(soundFolder))
                .toList();
    }

    /**
     * The least time between two of the hero's lines.
     *
     * <p>Here rather than on each cue because what is being prevented is two
     * voices at once, and it is no better when they are saying different things.
     */
    public float voiceGapSeconds() {
        return voiceGapSeconds;
    }

    private static final FieldParseTable<DungeonSettings> BEHAVIOUR =
            new FieldParseTable<DungeonSettings>()
                    .add("SkeletonSenseRadius", Ini.real((s, v) -> s.skeletonSenseRadius = v))
                    .add("SkeletonChaseRadius", Ini.real((s, v) -> s.skeletonChaseRadius = v))
                    .add("SkeletonRepathFrames", Ini.integer((s, v) -> s.skeletonRepathFrames = v))
                    .add("CloseDistance", Ini.real((s, v) -> s.closeDistance = v))
                    .add("HeroRepathFrames", Ini.integer((s, v) -> s.heroRepathFrames = v))
                    .add("WayAheadProbe", Ini.real((s, v) -> s.wayAheadProbe = v))
                    .add("RetreatTurnDegrees", Ini.real((s, v) -> s.retreatTurnDegrees = v))
                    .add("RetreatTurns", Ini.integer((s, v) -> s.retreatTurns = v))
                    .add("SummonTurnDegrees", Ini.real((s, v) -> s.summonTurnDegrees = v))
                    .add("SummonTurns", Ini.integer((s, v) -> s.summonTurns = v))
                    .add("ArrowTemplate", Ini.string((s, v) -> s.arrowTemplate = v))
                    .add("ArrowSpeed", Ini.real((s, v) -> s.arrowSpeed = v))
                    .add("ArrowMuzzleOffset", Ini.real((s, v) -> s.arrowMuzzleOffset = v));

    /** Accumulates one {@code DungeonMonster} block. */
    // ---- themes ----

    private final java.util.List<ThemeBuilder> themes = new java.util.ArrayList<>();
    private final java.util.List<ToneBuilder> tones = new java.util.ArrayList<>();
    private final java.util.List<ThemeMonsterBuilder> themeMonsters = new java.util.ArrayList<>();
    private final java.util.List<String> themeOrder = new java.util.ArrayList<>();
    private Themes.WhenExhausted whenExhausted = Themes.WhenExhausted.REPEAT;

    /** One kind of thing that stands about in a room, and how often it is drawn. */
    public record PropKind(String template, int weight) {
    }

    private static final class PropBuilder {
        private final String template;
        int weight = 1;

        PropBuilder(String template) {
            this.template = template;
        }
    }

    private final java.util.List<PropBuilder> props = new java.util.ArrayList<>();
    private int minPropsPerRoom;
    private int maxPropsPerRoom = 3;

    private static final FieldParseTable<PropBuilder> PROP =
            new FieldParseTable<PropBuilder>()
                    .add("Weight", Ini.integer((p, v) -> p.weight = v));

    private static final FieldParseTable<DungeonSettings> PROPS =
            new FieldParseTable<DungeonSettings>()
                    .add("MinPerRoom", Ini.integer((s, v) -> s.minPropsPerRoom = v))
                    .add("MaxPerRoom", Ini.integer((s, v) -> s.maxPropsPerRoom = v));

    /** What may be scattered through the rooms, in file order. */
    public java.util.List<PropKind> propKinds() {
        var kinds = new java.util.ArrayList<PropKind>(props.size());
        for (var prop : props) {
            kinds.add(new PropKind(prop.template, prop.weight));
        }
        return java.util.List.copyOf(kinds);
    }

    public int minPropsPerRoom() {
        return minPropsPerRoom;
    }

    public int maxPropsPerRoom() {
        return maxPropsPerRoom;
    }

    private static final class ThemeBuilder {
        private final String name;
        String folder = "";
        float tileSize = 4f;
        float wallTileSize;
        float wallHeight = 4f;
        float wallLift;
        float wallShift;
        boolean ownMaterials;
        boolean wallFillsRock;
        int wallClump = 1;
        float wallSpread;
        float wallVariety;
        String propFolder = "";
        String stairs;
        String rockFace;
        int capTint = 0xFFFFFF;
        int storeyShadePercent = 100;
        int fogTint;

        ThemeBuilder(String name) {
            this.name = name;
        }
    }

    private static final class ToneBuilder {
        private final String theme;
        private final String name;
        String floor;
        String wall;
        String corner;
        int tint = 0xFFFFFF;

        ToneBuilder(String theme, String name) {
            this.theme = theme;
            this.name = name;
        }
    }

    private static final class ThemeMonsterBuilder {
        private final String theme;
        private final String template;
        final MonsterBuilder art = new MonsterBuilder("themed");
        String animationsFrom;
        String death;

        ThemeMonsterBuilder(String theme, String template) {
            this.theme = theme;
            this.template = template;
        }
    }

    private static final FieldParseTable<ThemeBuilder> THEME =
            new FieldParseTable<ThemeBuilder>()
                    .add("Folder", Ini.string((t, v) -> t.folder = v))
                    .add("TileSize", Ini.real((t, v) -> t.tileSize = v))
                    .add("WallTileSize", Ini.real((t, v) -> t.wallTileSize = v))
                    .add("WallHeight", Ini.real((t, v) -> t.wallHeight = v))
                    .add("WallLift", Ini.real((t, v) -> t.wallLift = v))
                    .add("WallShift", Ini.real((t, v) -> t.wallShift = v))
                    .add("OwnMaterials", Ini.bool((t, v) -> t.ownMaterials = v))
                    .add("WallFillsRock", Ini.bool((t, v) -> t.wallFillsRock = v))
                    .add("WallClump", Ini.integer((t, v) -> t.wallClump = v))
                    .add("WallSpread", Ini.real((t, v) -> t.wallSpread = v))
                    .add("WallVariety", Ini.real((t, v) -> t.wallVariety = v))
                    .add("PropFolder", Ini.string((t, v) -> t.propFolder = v))
                    .add("Stairs", Ini.string((t, v) -> t.stairs = v))
                    .add("RockFace", Ini.string((t, v) -> t.rockFace = v))
                    .add("CapTint", (ini, t) -> t.capTint = Integer.decode(ini.getNextToken()))
                    .add("StoreyShadePercent",
                            Ini.integer((t, v) -> t.storeyShadePercent = v))
                    .add("FogTint", (ini, t) -> t.fogTint = Integer.decode(ini.getNextToken()));

    private static final FieldParseTable<ToneBuilder> TONE =
            new FieldParseTable<ToneBuilder>()
                    .add("Floor", Ini.string((t, v) -> t.floor = v))
                    .add("Wall", Ini.string((t, v) -> t.wall = v))
                    .add("Corner", Ini.string((t, v) -> t.corner = v))
                    .add("Tint", (ini, t) -> t.tint = Integer.decode(ini.getNextToken()));

    /** A themed creature is described exactly as any other, plus where its clips live. */
    private static final FieldParseTable<ThemeMonsterBuilder> THEME_MONSTER =
            new FieldParseTable<ThemeMonsterBuilder>()
                    .add("Model", Ini.string((t, v) -> t.art.model = v))
                    .add("Texture", Ini.string((t, v) -> t.art.texture = v))
                    .add("ModelScale", Ini.real((t, v) -> t.art.modelScale = v))
                    .add("Tint", (ini, t) -> t.art.tint = Integer.decode(ini.getNextToken()))
                    .add("Facing", Ini.real((t, v) -> t.art.facing = v))
                    .add("Idle", Ini.string((t, v) -> t.art.idle = v))
                    .add("Walk", Ini.string((t, v) -> t.art.walk = v))
                    .add("Attack", Ini.string((t, v) -> t.art.attack = v))
                    .add("Hurt", Ini.string((t, v) -> t.art.hurt = v))
                    .add("Effect", Ini.string((t, v) -> t.art.effect = v))
                    .add("Holds", Ini.string((t, v) -> t.art.holds = v))
                    .add("HeldIn", Ini.string((t, v) -> t.art.heldIn = v))
                    .add("HeldScale", Ini.real((t, v) -> t.art.heldScale = v))
                    .add("AnimationsFrom", Ini.string((t, v) -> t.animationsFrom = v))
                    .add("Death", Ini.string((t, v) -> t.death = v));

    private static final FieldParseTable<DungeonSettings> THEME_ORDER =
            new FieldParseTable<DungeonSettings>()
                    // Repeatable in one line: the order is a list, and a list of
                    // names reads better across a line than down a column.
                    .add("Order", (ini, s) -> {
                        for (var name : ini.getRestOfLine().trim().split("\\s+")) {
                            if (!name.isBlank()) {
                                s.themeOrder.add(name);
                            }
                        }
                    })
                    .add("WhenExhausted", Ini.enumeration(Themes.WhenExhausted.class,
                            (s, v) -> s.whenExhausted = v));

    /**
     * The themes the file described, assembled — each with its own variations and
     * whatever creatures it redraws.
     *
     * <p>Built here rather than kept as it was parsed because a theme's parts
     * arrive in three separate blocks, and nothing outside this class should have
     * to put them back together.
     */
    public Themes themes() {
        var built = new java.util.ArrayList<ThemeArt>();
        for (var theme : themes) {
            var itsTones = new java.util.ArrayList<ThemeArt.Tone>();
            for (var tone : tones) {
                if (tone.theme.equals(theme.name)) {
                    itsTones.add(new ThemeArt.Tone(tone.name, tone.floor, tone.wall,
                            tone.corner, tone.tint));
                }
            }
            var itsMonsters = new java.util.ArrayList<ThemeArt.ThemeMonster>();
            for (var themed : themeMonsters) {
                if (themed.theme.equals(theme.name)) {
                    itsMonsters.add(new ThemeArt.ThemeMonster(themed.template,
                            themed.art.look(), themed.animationsFrom, themed.death));
                }
            }
            built.add(new ThemeArt(theme.name, theme.folder, theme.tileSize,
                    theme.wallTileSize, theme.wallHeight, theme.wallLift, theme.wallShift,
                    theme.ownMaterials, theme.propFolder, theme.stairs, theme.rockFace,
                    theme.capTint, theme.storeyShadePercent, theme.fogTint,
                    new ThemeArt.Standing(theme.wallFillsRock, theme.wallClump,
                            theme.wallSpread, theme.wallVariety),
                    itsTones, itsMonsters));
        }
        return new Themes(themeOrder, whenExhausted, built);
    }

    private static final class MonsterBuilder {
        private final String name;
        float senseRadius = 90f;
        float chaseRadius = 150f;
        float closeDistance = 4f;
        float alertRadius = 70f;
        int repathFrames = 10;
        int swingFrames = 12;
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
        String hurt;
        String effect;
        String holds;
        String heldIn;
        float heldScale = 1f;
        float heldPitch;
        float heldYaw;
        float heldRoll;
        char skillKey;
        float skillNearest;
        float skillFurthest;
        float keepNearest;
        float keepFurthest;
        int maxPerRoom;

        MonsterBuilder(String name) {
            this.name = name;
        }

        MonsterKind build() {
            return new MonsterKind(name, senseRadius, chaseRadius, closeDistance, alertRadius,
                    repathFrames, swingFrames, minDepth, weight, colour, scale, look(), skillKey,
                    skillNearest, skillFurthest, keepNearest, keepFurthest, maxPerRoom);
        }

        /** Just the art of it, which is all a theme overriding a creature needs. */
        MonsterLook look() {
            return new MonsterLook(model, texture, modelScale, tint, facing, idle, walk, attack,
                    hurt, new Held(holds, heldIn, heldScale, heldPitch, heldYaw, heldRoll,
                            0f, 0f, 0f),
                    effect);
        }
    }

    private static final FieldParseTable<MonsterBuilder> MONSTER =
            new FieldParseTable<MonsterBuilder>()
                    .add("SenseRadius", Ini.real((m, v) -> m.senseRadius = v))
                    .add("ChaseRadius", Ini.real((m, v) -> m.chaseRadius = v))
                    .add("CloseDistance", Ini.real((m, v) -> m.closeDistance = v))
                    .add("AlertRadius", Ini.real((m, v) -> m.alertRadius = v))
                    .add("RepathFrames", Ini.integer((m, v) -> m.repathFrames = v))
                    .add("SwingFrames", Ini.integer((m, v) -> m.swingFrames = v))
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
                    .add("Attack", Ini.string((m, v) -> m.attack = v))
                    .add("Hurt", Ini.string((m, v) -> m.hurt = v))
                    .add("Effect", Ini.string((m, v) -> m.effect = v))
                    .add("Holds", Ini.string((m, v) -> m.holds = v))
                    .add("HeldIn", Ini.string((m, v) -> m.heldIn = v))
                    .add("HeldScale", Ini.real((m, v) -> m.heldScale = v))
                    .add("HeldPitch", Ini.real((m, v) -> m.heldPitch = v))
                    .add("HeldYaw", Ini.real((m, v) -> m.heldYaw = v))
                    .add("HeldRoll", Ini.real((m, v) -> m.heldRoll = v))
                    // Which of its own skills it decides to cast, by key. The skill is a
                    // DungeonSkill block headed by this creature's name, as a hero's are.
                    .add("Skill", Ini.string((m, v) -> m.skillKey = v.isEmpty() ? 0
                            : Character.toUpperCase(v.charAt(0))))
                    // The nearest and the furthest it casts from, surface to surface.
                    .add("SkillDistance", (ini, m) -> {
                        m.skillNearest = Ini.scanReal(ini.getNextToken());
                        m.skillFurthest = Ini.scanReal(ini.getNextToken());
                    })
                    // The band it holds: nearer and it backs away, further and it comes.
                    // Unsaid, it closes to CloseDistance like everything else.
                    .add("KeepDistance", (ini, m) -> {
                        m.keepNearest = Ini.scanReal(ini.getNextToken());
                        m.keepFurthest = Ini.scanReal(ini.getNextToken());
                    })
                    // How many of it one room may hold; zero for no limit.
                    .add("MaxPerRoom", Ini.integer((m, v) -> m.maxPerRoom = v));

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
        float hitWidth;
        int boostPercent;
        int boostPerLevel;
        int durationFrames;
        int tickFrames;
        int slowFrames;
        int cooldownFrames = 90;
        int cooldownPerLevel;
        int maxRank = 4;
        int levelPerRank;
        int windUpFrames;
        String projectile = "";
        String icon = "";
        String look = "";
        String name = "";
        String blurb = "";
        String castAnim = "";
        float castSeconds;
        int manaCost;
        int manaCostPerLevel;
        float projectileSpeed;
        float heal;
        int healBelowPercent;
        String summons = "";
        int summonCount;
        int maxSummoned;
        int summonExperiencePercent;

        SkillBuilder(String heroTemplate, String key) {
            this.heroTemplate = heroTemplate;
            this.key = Character.toUpperCase(key.charAt(0));
        }

        Skill build() {
            return new Skill(heroTemplate, key, effect, damage, damagePerLevel, radius, range,
                    distance, hitWidth, boostPercent, boostPerLevel, durationFrames, tickFrames,
                    slowFrames, cooldownFrames, cooldownPerLevel, maxRank, levelPerRank,
                    windUpFrames, manaCost, manaCostPerLevel,
                    projectile, icon, look, castAnim, castSeconds, name, blurb, projectileSpeed,
                    heal, healBelowPercent, summons, summonCount, maxSummoned,
                    summonExperiencePercent);
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
                    // How wide the thing a SKILLSHOT throws is. Drawing only --
                    // see the note on Skill.hitWidth.
                    .add("HitWidth", Ini.real((s, v) -> s.hitWidth = v))
                    .add("BoostPercent", Ini.integer((s, v) -> s.boostPercent = v))
                    .add("BoostPerLevel", Ini.integer((s, v) -> s.boostPerLevel = v))
                    .add("DurationFrames", Ini.integer((s, v) -> s.durationFrames = v))
                    // How often a lasting AREA_DAMAGE lands. Zero lands it once,
                    // which is every skill written before there was a whirlwind.
                    .add("TickFrames", Ini.integer((s, v) -> s.tickFrames = v))
                    // How long an area blast leaves whoever it caught dragging his
                    // feet. Zero is a blast that only hurts, which is what every
                    // area skill written before there was a frost nova says.
                    .add("SlowFrames", Ini.integer((s, v) -> s.slowFrames = v))
                    .add("CooldownFrames", Ini.integer((s, v) -> s.cooldownFrames = v))
                    .add("CooldownPerLevel", Ini.integer((s, v) -> s.cooldownPerLevel = v))
                    // How many points may go into it -- four for an ordinary
                    // skill, three for an ultimate.
                    .add("MaxRank", Ini.integer((s, v) -> s.maxRank = v))
                    // And the hero level its Nth rank waits for, as a multiple:
                    // 4 is "first at 4, second at 8, third at 12". Naming it at
                    // all is what makes a skill an ultimate.
                    .add("LevelPerRank", Ini.integer((s, v) -> s.levelPerRank = v))
                    .add("WindUpFrames", Ini.integer((s, v) -> s.windUpFrames = v))
                    .add("Projectile", Ini.string((s, v) -> s.projectile = v))
                    .add("Icon", Ini.string((s, v) -> s.icon = v))
                    // What it LOOKS like going off: the name of a DungeonEffect
                    // block. Named rather than described here for the reason every
                    // other look in this file is -- two skills may want the same
                    // ring, and a fifth skill should be a fifth block.
                    .add("Look", Ini.string((s, v) -> s.look = v))
                    // What the caster DOES, as against what the spell does. A
                    // clip out of one of his libraries, played once where he
                    // stands -- and the hero is given it automatically, because
                    // naming it here and again in his own block would be one
                    // name in two places and eventually two names.
                    // What it costs to cast, and what a rank does to that. The
                    // second is the DamagePerLevel pattern and may be negative:
                    // a skill can be made cheaper by learning it as easily as
                    // dearer by strengthening it.
                    .add("ManaCost", Ini.integer((s, v) -> s.manaCost = v))
                    .add("ManaCostPerLevel", Ini.integer((s, v) -> s.manaCostPerLevel = v))
                    .add("CastAnim", Ini.string((s, v) -> s.castAnim = v))
                    // How long it should take. The clip's own length by default
                    // (0), or stretched to this -- a gesture that ends when the
                    // spell lands reads as having caused it, and one that runs
                    // on past reads as somebody waving after the fact.
                    .add("CastSeconds", Ini.real((s, v) -> s.castSeconds = v))
                    // What the player is told it is called, and what it does. The
                    // NUMBERS are never here -- they are worked out from the rank,
                    // and a sentence with a figure in it goes stale the moment
                    // anything above is retuned.
                    .add("Name", Ini.restOfLine((s, v) -> s.name = v))
                    .add("Blurb", Ini.restOfLine((s, v) -> s.blurb = v))
                    // How fast what it throws travels, in units a second. Unsaid, it is
                    // the drawn arrow's HeavySpeed, which every hero's shot flies at.
                    .add("ProjectileSpeed", Ini.real((s, v) -> s.projectileSpeed = v))
                    // What a HEAL gives back, and how hurt somebody has to be, as a share
                    // of his own health, before it is spent on him at all.
                    .add("Heal", Ini.real((s, v) -> s.heal = v))
                    .add("HealBelowPercent", Ini.integer((s, v) -> s.healBelowPercent = v))
                    // What a SUMMON calls up, how many a cast and at most, and what killing
                    // one is worth as a share of its own kind.
                    .add("Summons", Ini.string((s, v) -> s.summons = v))
                    .add("SummonCount", Ini.integer((s, v) -> s.summonCount = v))
                    .add("MaxSummoned", Ini.integer((s, v) -> s.maxSummoned = v))
                    .add("SummonExperiencePercent",
                            Ini.integer((s, v) -> s.summonExperiencePercent = v));

    /** Accumulates one {@code DungeonLootItem <id>} block. */
    private static final class LootBuilder {
        private final String id;
        String name;
        String icon = "";
        LootKind kind = LootKind.ATTACK;
        int value;
        int weight = 10;
        int minDepth = 1;

        LootBuilder(String id) {
            this.id = id;
            this.name = id;
        }

        Loot build() {
            return new Loot(id, name, icon, kind, value, weight, minDepth);
        }
    }

    private static final FieldParseTable<LootBuilder> LOOT =
            new FieldParseTable<LootBuilder>()
                    .add("Name", Ini.restOfLine((l, v) -> l.name = v))
                    .add("Icon", Ini.string((l, v) -> l.icon = v))
                    .add("Kind", Ini.enumeration(LootKind.class, (l, v) -> l.kind = v))
                    .add("Value", Ini.integer((l, v) -> l.value = v))
                    .add("Weight", Ini.integer((l, v) -> l.weight = v))
                    .add("MinDepth", Ini.integer((l, v) -> l.minDepth = v));

    private static final FieldParseTable<DungeonSettings> LOOT_RULES =
            new FieldParseTable<DungeonSettings>()
                    .add("Template", Ini.string((s, v) -> s.lootTemplate = v))
                    .add("DropPercent", Ini.integer((s, v) -> s.lootDropPercent = v))
                    .add("BossDropPercent", Ini.integer((s, v) -> s.lootBossDropPercent = v))
                    .add("PickupRange", Ini.real((s, v) -> s.lootPickupRange = v))
                    .add("ValuePercentPerDepth",
                            Ini.integer((s, v) -> s.lootValuePercentPerDepth = v))
                    .add("NoteFrames", Ini.integer((s, v) -> s.lootNoteFrames = v));

    /** Everything that can be found on a floor, in file order. */
    public java.util.List<Loot> loot() {
        return java.util.List.copyOf(loot);
    }

    /** The creature a dropped item becomes; empty means nothing is ever dropped. */
    public String lootTemplate() {
        return lootTemplate;
    }

    public int lootDropPercent() {
        return lootDropPercent;
    }

    public int lootBossDropPercent() {
        return lootBossDropPercent;
    }

    /** How close he has to walk before it is his. */
    public float lootPickupRange() {
        return lootPickupRange;
    }

    public int lootValuePercentPerDepth() {
        return lootValuePercentPerDepth;
    }

    /** How long the panel says what he just found, in logic frames. */
    public int lootNoteFrames() {
        return lootNoteFrames;
    }

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
    public record TileArt(String floor, String wall, String corner, String stairs,
            float tileSize, float wallHeight, float wallLift, float wallShift) {
    }

    private String tileFolder = "";
    private String tileFloor;
    private String tileWall;
    private String tileCorner;
    private String tileStairs;
    private float tileSize = 4f;
    private float tileWallHeight = 4f;
    private float tileWallLift;
    private float tileWallShift;

    /** The kit to draw the floor with; {@code floor()} is null if the file named none. */
    public TileArt tiles() {
        return new TileArt(path(tileFloor), path(tileWall), path(tileCorner),
                path(tileStairs), tileSize, tileWallHeight, tileWallLift, tileWallShift);
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
                    .add("Stairs", Ini.string((s, v) -> s.tileStairs = v))
                    .add("TileSize", Ini.real((s, v) -> s.tileSize = v))
                    .add("WallHeight", Ini.real((s, v) -> s.tileWallHeight = v))
                    .add("WallLift", Ini.real((s, v) -> s.tileWallLift = v))
                    .add("WallShift", Ini.real((s, v) -> s.tileWallShift = v));

    private final java.util.List<String> animationLibraries = new java.util.ArrayList<>();
    private String defaultIdle;
    private String defaultWalk;
    private String defaultAttack;
    private String defaultHurt;
    private String defaultDeath;

    /**
     * The files every monster's animations are taken from.
     *
     * <p>The same libraries for the whole bestiary, because a creature kit and an
     * animation library meet on a shared skeleton — so what animates one monster
     * animates all of them, and a new monster needs no animation work at all.
     *
     * <p>Several rather than one, because a kit sorts its clips by what the
     * movement is for: standing and dying in one file, walking in another, a swing
     * in a third. It was one file when the bestiary came from a library that
     * bundled everything together.
     */
    public java.util.List<String> animationLibraries() {
        return java.util.List.copyOf(animationLibraries);
    }

    /** The clip every monster plays as it falls, or {@code null} for none. */
    public String deathClip() {
        return defaultDeath;
    }

    /** The swing a creature gets when its own block names none. */
    public String defaultAttack() {
        return defaultAttack;
    }

    /** And how it stands when it is doing nothing in particular. */
    public String defaultIdle() {
        return defaultIdle;
    }

    /** A kind's look with the game's default clip names filled in. */
    public MonsterLook lookOf(MonsterKind kind) {
        return kind.look().withDefaults(defaultIdle, defaultWalk, defaultAttack, defaultHurt);
    }

    private final java.util.List<HeroBuilder> heroes = new java.util.ArrayList<>();
    private final java.util.List<PortraitBuilder> portraits = new java.util.ArrayList<>();

    /**
     * What each hero the file describes is drawn as, in the order it names them.
     *
     * <p>A list because the roster is the file's. He used to be a set of fields on
     * this class — one hero, and a second block would have silently overwritten
     * the first — which was the one place the data layer could not keep the
     * promise it keeps about monsters, skills and loot.
     *
     * <p>Empty when the file names none, and then whoever is playing is a coloured
     * shape, as he was before there was a model.
     */
    public java.util.List<HeroLook> heroes() {
        return heroes.stream().map(HeroBuilder::build).toList();
    }

    private String playedHero = "Rogue";

    /**
     * Which of them is being played.
     *
     * <p>The one thing about a roster that cannot be worked out from the roster.
     * It was the literal word {@code Hero} in six places in Java, so a second hero
     * could be described in full and never walk into a dungeon.
     *
     * <p>A settings line rather than a screen because a choosing screen is a
     * different piece of work; this is what makes the second hero playable enough
     * to be balanced.
     */
    public String playedHero() {
        return playedHero;
    }

    private String stageFile = "";

    /**
     * The stage this build opens on, or blank for the endless dungeon.
     *
     * <p>A path rather than a switch, because there is nothing to switch between:
     * the game is the descent unless somebody has frozen a floor and named the
     * file. Beaten by {@code --stage=} on the command line, which is what an
     * author uses while he is building one.
     */
    public String stageFile() {
        return stageFile;
    }

    /** That hero's block, or {@link HeroLook#NONE} if the file describes no such one. */
    public HeroLook heroNamed(String templateName) {
        for (var hero : heroes) {
            if (hero.name.equals(templateName)) {
                return hero.build();
            }
        }
        return HeroLook.NONE;
    }

    /** The one being played, which is what the run spawns and the panel describes. */
    public HeroLook playedHeroLook() {
        return heroNamed(playedHero);
    }

    /** Every live portrait the file describes, by the creature it is the face of. */
    public java.util.List<PortraitArt> portraits() {
        return portraits.stream().map(PortraitBuilder::build).toList();
    }

    private final PortraitBuilder everyPortrait = new PortraitBuilder("");
    private boolean portraitsDeclared;

    /**
     * The portrait every selectable creature gets, or {@code null} if the file
     * asked for none.
     *
     * <p>One block for the whole bestiary. Nothing about a portrait is
     * per-creature except where the camera stands, and that is written as
     * fractions of whatever it is looking at — so this frames a skeleton, a hero
     * and whatever is added next, each by its own measured height.
     */
    public PortraitArt everyPortrait() {
        return portraitsDeclared ? everyPortrait.build() : null;
    }

    /**
     * How many times a second the portrait is redrawn. A ceiling, not a target —
     * see {@code uz.duke.client3d.PortraitMood}.
     */
    private int portraitFps = 24;

    public int portraitFps() {
        return portraitFps;
    }

    /**
     * One hero, as his block spells him.
     *
     * <p>Everything about his art except the two things that are only true in a
     * portrait, which are in {@link PortraitBuilder} beside it.
     */
    private static final class HeroBuilder {
        private final String name;
        String title = "";
        float closeDistance;
        int maxMana;
        int manaRegen;
        int healthRegen;
        int armourPercent;
        /** His primary, and his three with what a level adds to each — all in tenths. */
        Attribute primary;
        int strength;
        int agility;
        int intelligence;
        int strengthPerLevel;
        int agilityPerLevel;
        int intelligencePerLevel;
        String model;
        String texture;
        float modelScale = 1f;
        float facing = 90f;
        final java.util.List<String> animations = new java.util.ArrayList<>();
        String idle;
        String walk;
        String attack;
        String hurt;
        String death;
        /**
         * Everything he carries, in the order the file names it.
         *
         * <p>{@code Holds} starts a new one and every {@code Held*} line after it
         * describes that one, the way a paragraph describes the heading above it.
         * It reads the way the old single-item block read, which is the point: a
         * hero who carries one thing is written exactly as he always was.
         */
        final java.util.List<Held> carried = new java.util.ArrayList<>();

        /** Start a new thing carried; the Held* lines below it fill it in. */
        void holds(String model) {
            carried.add(new Held(model, null, 1f, 0f, 0f, 0f, 0f, 0f, 0f));
        }

        /**
         * Rewrite the one being described.
         *
         * <p>A {@code Held*} line with no {@code Holds} above it would otherwise
         * be a silent no-op; it gets an empty one to fill in instead, which
         * {@link Held#isCarried} then drops for having no model. The file is
         * wrong either way and this is the way that cannot corrupt the item
         * before it.
         */
        void describe(java.util.function.UnaryOperator<Held> change) {
            if (carried.isEmpty()) {
                holds(null);
            }
            carried.set(carried.size() - 1, change.apply(carried.get(carried.size() - 1)));
        }

        HeroBuilder(String name) {
            this.name = name;
        }

        HeroLook build() {
            var attributes = primary == null ? HeroAttributes.NONE
                    : new HeroAttributes(primary,
                            new Attributes(strength, agility, intelligence),
                            new Attributes(strengthPerLevel, agilityPerLevel, intelligencePerLevel));
            return new HeroLook(name, title, closeDistance, armourPercent, maxMana, manaRegen,
                    healthRegen, attributes,
                    model, texture,
                    modelScale, facing,
                    animations, idle, walk, attack, hurt, death,
                    java.util.List.copyOf(carried));
        }
    }

    /**
     * One creature's live portrait.
     *
     * <p>The numbers here are the file's; these are what a block that leaves a
     * line out falls back to, the same way a monster falls back to facing 90.
     */
    private static final class PortraitBuilder {
        private final String name;
        float head = 0.74f;
        float show = 0.64f;
        float yaw = 22f;
        float pitch = -4f;
        float fov = 34f;
        String calm;
        String fight;
        String hurt;
        String dead;
        String levelUp;
        float hurtBelowPercent = 30f;
        float hurtSpeed = 1.5f;

        PortraitBuilder(String name) {
            this.name = name;
        }

        PortraitArt build() {
            return new PortraitArt(name, head, show, yaw, pitch, fov,
                    calm, fight, hurt, dead, levelUp, hurtBelowPercent, hurtSpeed);
        }
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
    public record ArrowLook(String name, String model, String part, float scale, float facing,
            float height, int tint, String effect, float effectOffset) {
        /**
         * A model is enough, and none at all is allowed: a fireball is drawn by its
         * effect and has no file anywhere. {@code part} is for a projectile that is
         * one mesh inside a larger file — which is how it had to be found while the
         * only arrow the game owned was the one on the hero's string, and is not
         * how a kit that ships an arrow hands it over.
         */
        public boolean hasModel() {
            return model != null;
        }

        public java.awt.Color awtTint() {
            return new java.awt.Color(tint);
        }
    }

    /**
     * How each projectile is drawn, by the name of the template it is.
     *
     * <p>Named and repeatable, which it was not: there were two shots in the game
     * and one block described both, the second under a {@code Heavy} prefix. A
     * dungeon with a crossbow in it and a mage throwing fire has four, and a
     * prefix each is not a scheme.
     */
    private final java.util.List<ProjectileBuilder> projectiles = new java.util.ArrayList<>();

    private static final class ProjectileBuilder {
        private final String name;
        String model;
        String part;
        float scale = 1f;
        float facing = 90f;
        float height;
        int tint = 0xFFFFFF;
        String effect;
        float effectOffset;

        ProjectileBuilder(String name) {
            this.name = name;
        }

        ArrowLook look() {
            return new ArrowLook(name, model, part, scale, facing, height, tint, effect,
                    effectOffset);
        }
    }

    /** Every projectile the file describes, in the order it describes them. */
    public java.util.List<ArrowLook> projectiles() {
        return projectiles.stream().map(ProjectileBuilder::look).toList();
    }

    /** How one is drawn, or a plain shape when the file describes no such thing. */
    public ArrowLook projectile(String template) {
        for (var projectile : projectiles) {
            if (projectile.name.equals(template)) {
                return projectile.look();
            }
        }
        return new ArrowLook(template, null, null, 1f, 90f, 0f, 0xFFFFFF, null, 0f);
    }

    private String heavyArrowTemplate = "HeavyArrow";
    private float heavyArrowSpeed = 120f;

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

    private static final FieldParseTable<DungeonSettings> ARROW_LOOK =
            new FieldParseTable<DungeonSettings>()
                    .add("HeavyTemplate", Ini.string((s, v) -> s.heavyArrowTemplate = v))
                    .add("HeavySpeed", Ini.real((s, v) -> s.heavyArrowSpeed = v));

    /**
     * What a thing in flight looks like, by name.
     *
     * <p>Shared rather than written on each projectile: an arrow and the drawn
     * shot the hero looses are the same fire at two sizes. The client owns the
     * <em>kinds</em> — a trail, a glowing body, a burst where it lands — and every
     * number in them is here, so a new burning thing is a block of settings and
     * not a class.
     */
    public record EffectLook(String name, java.util.List<String> kinds,
            java.util.List<String> parts, int colour, int fade,
            int lightColour, float lightPower, float lightRadius,
            int particles, float particleSize, float particleLife, float spread,
            float orbSize, int burstParticles, float burstSize, float burstSeconds,
            float waveFrom, float waveTo, float waveSeconds, float waveEase,
            float waveEdge, float waveWash, float markRadius, float markSeconds,
            float shakeSeconds, float shakePower) {

        public EffectLook {
            kinds = java.util.List.copyOf(kinds);
            parts = java.util.List.copyOf(parts);
        }

        public java.awt.Color awtColour() {
            return new java.awt.Color(colour);
        }

        public java.awt.Color awtFade() {
            return new java.awt.Color(fade);
        }

        public java.awt.Color awtLight() {
            return new java.awt.Color(lightColour);
        }
    }

    private final java.util.List<EffectBuilder> effects = new java.util.ArrayList<>();

    private static final class EffectBuilder {
        private final String name;
        final java.util.List<String> kinds = new java.util.ArrayList<>();
        final java.util.List<String> parts = new java.util.ArrayList<>();
        int colour = 0xFFFFFF;
        int fade = 0x000000;
        int lightColour = 0xFFFFFF;
        float lightPower;
        float lightRadius;
        int particles;
        float particleSize = 1f;
        float particleLife = 0.4f;
        float spread;
        float orbSize;
        int burstParticles;
        float burstSize = 1f;
        float burstSeconds = 0.3f;
        float waveFrom;
        float waveTo;
        float waveSeconds = 0.45f;
        float waveEase = 2.4f;
        float waveEdge = 1f;
        float waveWash = 0.25f;
        float markRadius;
        float markSeconds;
        float shakeSeconds;
        float shakePower;

        EffectBuilder(String name) {
            this.name = name;
        }

        EffectLook look() {
            return new EffectLook(name, kinds, parts, colour, fade, lightColour, lightPower,
                    lightRadius, particles, particleSize, particleLife, spread, orbSize,
                    burstParticles, burstSize, burstSeconds, waveFrom, waveTo, waveSeconds,
                    waveEase, waveEdge, waveWash, markRadius, markSeconds, shakeSeconds,
                    shakePower);
        }
    }

    /** Every effect the file describes, in the order it describes them. */
    public java.util.List<EffectLook> effects() {
        return effects.stream().map(EffectBuilder::look).toList();
    }

    /**
     * One painted edge of the hero panel: which part, which picture, and how.
     *
     * <p>The client's own {@code PanelSkin} is what this becomes — see
     * {@code Main} — so the name is one of the names it answers to and nothing
     * here decides where a frame goes, only what it is made of.
     *
     * @param name  the part of the panel: Minimap, Portrait, Slot, Gauge, Chip,
     *              Divider
     * @param inset how many pixels of the picture are corner, measured off the
     *              file. Wrong and the corner is stretched or the edge is not
     * @param scale how many panel pixels one picture pixel becomes — the same
     *              file laid on lightly for a socket and heavily for a bar
     */
    public record SkinLook(String name, String texture, float inset, float scale, int tint) {

        public java.awt.Color awtTint() {
            return new java.awt.Color(tint);
        }
    }

    private final java.util.List<SkinBuilder> skin = new java.util.ArrayList<>();

    private static final class SkinBuilder {
        private final String name;
        String texture = "";
        float inset;
        float scale = 1f;
        int tint = 0xFFFFFF;

        SkinBuilder(String name) {
            this.name = name;
        }

        SkinLook look(String folder) {
            return new SkinLook(name, folder + texture, inset, scale, tint);
        }
    }

    /**
     * Every painted edge the file describes, with {@code SkinFolder} already on
     * the front of each path — the same joining {@link #hudIcon} does, and for the
     * same reason: a folder written once rather than on every line.
     */
    public java.util.List<SkinLook> skin() {
        return skin.stream().map(piece -> piece.look(hudSkinFolder)).toList();
    }

    /**
     * One mouse pointer: which situation, which picture, and where its tip is.
     *
     * @param hotX how far from the left of the picture the tip is, in pixels
     * @param hotY how far from the TOP of it -- read the way anyone reads a file
     */
    public record CursorLook(String name, String image, int hotX, int hotY, int tint) {
    }

    private final java.util.List<CursorBuilder> cursors = new java.util.ArrayList<>();

    private static final class CursorBuilder {
        private final String name;
        String image = "";
        int hotX;
        int hotY;
        int tint = 0xFFFFFF;

        CursorBuilder(String name) {
            this.name = name;
        }

        CursorLook look(String folder) {
            return new CursorLook(name, folder + image, hotX, hotY, tint);
        }
    }

    /** Every pointer the file describes, with {@code CursorFolder} on the front. */
    public java.util.List<CursorLook> cursors() {
        return cursors.stream().map(pointer -> pointer.look(hudCursorFolder)).toList();
    }

    private static final FieldParseTable<CursorBuilder> CURSOR =
            new FieldParseTable<CursorBuilder>()
                    .add("Image", Ini.string((c, v) -> c.image = v))
                    .add("HotX", Ini.integer((c, v) -> c.hotX = v))
                    .add("HotY", Ini.integer((c, v) -> c.hotY = v))
                    .add("Tint", (ini, c) -> c.tint = Integer.decode(ini.getNextToken()));

    private static final FieldParseTable<SkinBuilder> SKIN =
            new FieldParseTable<SkinBuilder>()
                    .add("Texture", Ini.string((s, v) -> s.texture = v))
                    .add("Inset", Ini.real((s, v) -> s.inset = v))
                    .add("Scale", Ini.real((s, v) -> s.scale = v))
                    .add("Tint", (ini, s) -> s.tint = Integer.decode(ini.getNextToken()));

    private static final FieldParseTable<EffectBuilder> EFFECT =
            new FieldParseTable<EffectBuilder>()
                    // Repeatable: one thing can trail, glow and burst at once.
                    .add("Kind", Ini.string((e, v) -> e.kinds.add(v)))
                    .add("Colour", (ini, e) -> e.colour = Integer.decode(ini.getNextToken()))
                    .add("FadeColour", (ini, e) -> e.fade = Integer.decode(ini.getNextToken()))
                    .add("LightColour",
                            (ini, e) -> e.lightColour = Integer.decode(ini.getNextToken()))
                    .add("LightPower", Ini.real((e, v) -> e.lightPower = v))
                    .add("LightRadius", Ini.real((e, v) -> e.lightRadius = v))
                    .add("Particles", Ini.integer((e, v) -> e.particles = v))
                    .add("ParticleSize", Ini.real((e, v) -> e.particleSize = v))
                    .add("ParticleLife", Ini.real((e, v) -> e.particleLife = v))
                    .add("Spread", Ini.real((e, v) -> e.spread = v))
                    .add("OrbSize", Ini.real((e, v) -> e.orbSize = v))
                    // Repeatable: "the eyes and the jaw" is two lines.
                    .add("Part", Ini.string((e, v) -> e.parts.add(v)))
                    .add("BurstParticles", Ini.integer((e, v) -> e.burstParticles = v))
                    .add("BurstSize", Ini.real((e, v) -> e.burstSize = v))
                    .add("BurstSeconds", Ini.real((e, v) -> e.burstSeconds = v))
                    // SHOCKWAVE: the ring that opens across the floor. From and To
                    // are where it starts and stops; To of zero and it never runs.
                    // A skill that says nothing about its own width gets these, and
                    // one that has a Radius of its own overrides them at the cast.
                    .add("WaveFrom", Ini.real((e, v) -> e.waveFrom = v))
                    .add("WaveTo", Ini.real((e, v) -> e.waveTo = v))
                    .add("WaveSeconds", Ini.real((e, v) -> e.waveSeconds = v))
                    // The whole of the feel, in one number. 1 opens at a constant
                    // speed and reads as a circle being resized; above 1 it leaps
                    // and then slows, which is what an impact does. 2 to 3 is the
                    // useful range and 2.4 is what a block that says nothing gets.
                    .add("WaveEase", Ini.real((e, v) -> e.waveEase = v))
                    .add("WaveEdge", Ini.real((e, v) -> e.waveEdge = v))
                    .add("WaveWash", Ini.real((e, v) -> e.waveWash = v))
                    // GROUND_MARK: the disc that STAYS. MarkSeconds of zero and
                    // there is none; MarkRadius of zero takes the skill's own.
                    .add("MarkRadius", Ini.real((e, v) -> e.markRadius = v))
                    .add("MarkSeconds", Ini.real((e, v) -> e.markSeconds = v))
                    // And the knock. Small numbers: a shake is felt rather than
                    // seen, and one that can be SEEN is one a player turns off.
                    .add("ShakeSeconds", Ini.real((e, v) -> e.shakeSeconds = v))
                    .add("ShakePower", Ini.real((e, v) -> e.shakePower = v));

    // ---- the layers an effect is drawn from ----

    /**
     * One layer of an effect, as the file wrote it — only the fields it said.
     *
     * <p>The fields and nothing else, because the defaults belong to the client: a
     * layer that does not mention its drag gets whatever the client gives a layer
     * that does not mention its drag, and there is one place that says what that
     * is instead of two that can disagree. Keyed by the client's own names, and
     * already checked to be the kind of value each one is, so a typo is caught
     * when the file is read rather than when the effect is drawn.
     *
     * @param effect the effect this is a layer of — a {@code DungeonEffect} name
     * @param name   what this layer is called, for whoever reads the file
     * @param fields what it said, by the client's name for each
     */
    public record EffectLayerArt(String effect, String name, java.util.Map<String, String> fields) {

        public EffectLayerArt {
            fields = java.util.Map.copyOf(fields);
        }
    }

    private static final class LayerBuilder {
        private final String effect;
        private final String name;
        private final java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();

        LayerBuilder(String effect, String name) {
            this.effect = effect;
            this.name = name;
        }

        void put(String key, String value) {
            fields.put(key, value);
        }

        void two(String first, String second, String low, String high) {
            put(first, String.valueOf(Float.parseFloat(low)));
            put(second, String.valueOf(Float.parseFloat(high)));
        }

        void twoColours(String first, String second, String start, String end) {
            put(first, String.valueOf(Integer.decode(start)));
            put(second, String.valueOf(Integer.decode(end)));
        }
    }

    private final java.util.List<LayerBuilder> effectLayers = new java.util.ArrayList<>();

    /**
     * Every layer the file describes, in the order it describes them — which is
     * also the order they are drawn in, one over another.
     */
    public java.util.List<EffectLayerArt> effectLayers() {
        return effectLayers.stream()
                .map(layer -> new EffectLayerArt(layer.effect, layer.name, layer.fields))
                .toList();
    }

    /**
     * What one of the run's own moments looks like: a level gained, the boss down,
     * the hero arriving on a floor. The client notices the moment; this says which
     * recipe it plays on him.
     *
     * @param name   which moment, in the client's word for it
     * @param effect the recipe it plays, a {@code DungeonEffect} drawn in layers
     * @param scale  how much bigger than the recipe is written it is drawn; 1 as written
     */
    public record MomentArt(String name, String effect, float scale) {
    }

    private static final class MomentBuilder {
        private final String name;
        String effect = "";
        float scale = 1f;

        MomentBuilder(String name) {
            this.name = name;
        }
    }

    private final java.util.List<MomentBuilder> moments = new java.util.ArrayList<>();

    /** Every moment the file gives a look, in the order it gives them. */
    public java.util.List<MomentArt> moments() {
        return moments.stream()
                .map(moment -> new MomentArt(moment.name, moment.effect, moment.scale))
                .toList();
    }

    private static final FieldParseTable<MomentBuilder> MOMENT =
            new FieldParseTable<MomentBuilder>()
                    .add("Effect", Ini.string((m, v) -> m.effect = v))
                    .add("Scale", Ini.real((m, v) -> m.scale = v));

    private String particleFolder = "";

    /** Where a layer's texture is found, joined onto the front of its name. */
    public String particleFolder() {
        return particleFolder;
    }

    private int effectParticles;

    /**
     * How many particles may burn at once across every effect. A ceiling: past it
     * a layer is drawn thinner, and past that it is not drawn.
     */
    public int effectParticles() {
        return effectParticles;
    }

    private float shakeScale = 1f;

    /** Every knock of the camera against what its effect asked for; 0 is none. */
    public float shakeScale() {
        return shakeScale;
    }

    private int hitFlashColour = 0xFFFFFF;
    private float hitFlashSeconds;
    private float hitFlashStrength;

    /** What a creature that is hit flashes towards. */
    public int hitFlashColour() {
        return hitFlashColour;
    }

    /** How long the whole flash is; 0 is none. */
    public float hitFlashSeconds() {
        return hitFlashSeconds;
    }

    /** How far towards its colour, 0 to 1; 0 is none. */
    public float hitFlashStrength() {
        return hitFlashStrength;
    }

    private float strikeWithin = 30f;

    /** How near a shot's end a blow must land, that frame, for the shot to have struck. */
    public float strikeWithin() {
        return strikeWithin;
    }

    private static final FieldParseTable<LayerBuilder> LAYER =
            new FieldParseTable<LayerBuilder>()
                    .add("Type", Ini.string((l, v) -> l.put("type", v.toUpperCase(java.util.Locale.ROOT))))
                    // A file name inside ParticleFolder, as SkinFolder and CursorFolder do.
                    .add("Texture", Ini.string((l, v) -> l.put("texture", v)))
                    // Additive for light -- fire, magic, sparks -- and Alpha for stuff: smoke
                    // and dust drawn additively brighten the floor they are meant to hide.
                    .add("Blend", Ini.string((l, v) -> l.put("additive",
                            String.valueOf(!"Alpha".equalsIgnoreCase(v)))))
                    // And how much of the floor it hides, for fire that has to read on
                    // pale ground: 0 is Additive's, 1 is Alpha's.
                    .add("Cover", Ini.real((l, v) -> l.put("cover", String.valueOf(v))))
                    .add("Count", Ini.integer((l, v) -> l.put("count", String.valueOf(v))))
                    .add("Rate", Ini.real((l, v) -> l.put("rate", String.valueOf(v))))
                    .add("Delay", Ini.real((l, v) -> l.put("delay", String.valueOf(v))))
                    .add("Seconds", Ini.real((l, v) -> l.put("seconds", String.valueOf(v))))
                    .add("SizeEase", Ini.real((l, v) -> l.put("sizeEase", String.valueOf(v))))
                    .add("SizeJitter", Ini.real((l, v) -> l.put("sizeJitter", String.valueOf(v))))
                    .add("ColourEase", Ini.real((l, v) -> l.put("colourEase", String.valueOf(v))))
                    .add("FadeIn", Ini.real((l, v) -> l.put("fadeIn", String.valueOf(v))))
                    .add("FadeOut", Ini.real((l, v) -> l.put("fadeOut", String.valueOf(v))))
                    .add("Spread", Ini.real((l, v) -> l.put("spread", String.valueOf(v))))
                    .add("Radius", Ini.real((l, v) -> l.put("radius", String.valueOf(v))))
                    .add("Height", Ini.real((l, v) -> l.put("height", String.valueOf(v))))
                    .add("Gravity", Ini.real((l, v) -> l.put("gravity", String.valueOf(v))))
                    .add("Drag", Ini.real((l, v) -> l.put("drag", String.valueOf(v))))
                    .add("Stretch", Ini.real((l, v) -> l.put("stretch", String.valueOf(v))))
                    .add("Spin", Ini.real((l, v) -> l.put("spin", String.valueOf(v))))
                    .add("Turn", Ini.real((l, v) -> l.put("turn", String.valueOf(v))))
                    .add("TurnJitter", Ini.real((l, v) -> l.put("turnJitter", String.valueOf(v))))
                    .add("PulseRate", Ini.real((l, v) -> l.put("pulseRate", String.valueOf(v))))
                    .add("PulseDepth", Ini.real((l, v) -> l.put("pulseDepth", String.valueOf(v))))
                    .add("LightPower", Ini.real((l, v) -> l.put("lightPower", String.valueOf(v))))
                    .add("LightRadius", Ini.real((l, v) -> l.put("lightRadius", String.valueOf(v))))
                    .add("Fall", Ini.real((l, v) -> l.put("fall", String.valueOf(v))))
                    // A PILLAR's end that moves: how much of its life it takes to cross
                    // the whole height, and along what curve.
                    .add("Rise", Ini.real((l, v) -> l.put("rise", String.valueOf(v))))
                    .add("RiseEase", Ini.real((l, v) -> l.put("riseEase", String.valueOf(v))))
                    // Whether a layer on somebody goes where he goes. An AURA always does.
                    .add("Follows", Ini.bool((l, v) -> l.put("follows", String.valueOf(v))))
                    // Two numbers, where a thing has a start and an end or a least and a most.
                    .add("Life", (ini, l) -> l.two("lifeMin", "lifeMax", ini.getNextToken(), ini.getNextToken()))
                    .add("Size", (ini, l) -> l.two("sizeStart", "sizeEnd", ini.getNextToken(), ini.getNextToken()))
                    .add("Alpha", (ini, l) -> l.two("alphaStart", "alphaEnd", ini.getNextToken(), ini.getNextToken()))
                    .add("Speed", (ini, l) -> l.two("speedMin", "speedMax", ini.getNextToken(), ini.getNextToken()))
                    .add("Colour", (ini, l) -> l.twoColours("colourStart", "colourEnd",
                            ini.getNextToken(), ini.getNextToken()))
                    .add("LightColour", (ini, l) -> l.put("lightColour",
                            String.valueOf(Integer.decode(ini.getNextToken()))))
                    .add("Direction", Ini.string((l, v) -> l.put("direction", v.toUpperCase(java.util.Locale.ROOT))))
                    .add("At", Ini.string((l, v) -> l.put("at", v.toUpperCase(java.util.Locale.ROOT))))
                    // UNITS, or REACH for a shape as wide as the skill's own radius.
                    .add("Measure", Ini.string((l, v) -> l.put("measure", v.toUpperCase(java.util.Locale.ROOT))));

    // ---- what the client may spend on all of it ----

    private int effectLights = 4;
    private int effectsPerKind = 8;
    private int effectBursts = 8;
    private int effectRings = 6;
    private float effectDistance;

    /**
     * The ceilings, not the targets. A fight is not one arrow: fifty in the air,
     * each with a hundred sparks and a light of its own, is five thousand
     * particles and fifty dynamic lights — and dynamic lights are the expensive
     * kind. Past a ceiling a shot flies plainer, never differently.
     */
    public int effectLights() {
        return effectLights;
    }

    public int effectsPerKind() {
        return effectsPerKind;
    }

    /**
     * How many skill rings may be open across the floor at once.
     *
     * <p>Its own ceiling rather than a share of the bursts', because it is a
     * different resource: a burst is particles and a light, a ring is a vertex
     * buffer and two materials. Past it a skill keeps its fire, its light and its
     * damage and loses a decoration, which is the cheapest thing in the room.
     */
    public int effectRings() {
        return effectRings;
    }

    public int effectBursts() {
        return effectBursts;
    }

    /** How far from the camera a thing is still worth the trouble; 0 for no limit. */
    public float effectDistance() {
        return effectDistance;
    }

    private static final FieldParseTable<DungeonSettings> EFFECT_BUDGET =
            new FieldParseTable<DungeonSettings>()
                    .add("MaxLights", Ini.integer((s, v) -> s.effectLights = v))
                    .add("MaxRings", Ini.integer((s, v) -> s.effectRings = v))
                    .add("MaxPerEffect", Ini.integer((s, v) -> s.effectsPerKind = v))
                    .add("MaxBursts", Ini.integer((s, v) -> s.effectBursts = v))
                    .add("MaxDistance", Ini.real((s, v) -> s.effectDistance = v))
                    // Every particle burning at once, across every effect: a ceiling
                    // rather than a target, and what keeps a fight from warming a laptop.
                    .add("MaxParticles", Ini.integer((s, v) -> s.effectParticles = v))
                    .add("ParticleFolder", Ini.string((s, v) -> s.particleFolder = v))
                    // How it feels rather than what it costs: every knock of the camera
                    // at once, and the flash a creature gives when it is hit.
                    .add("ShakeScale", Ini.real((s, v) -> s.shakeScale = v))
                    .add("HitFlashColour", (ini, s) -> s.hitFlashColour = Integer.decode(ini.getNextToken()))
                    .add("HitFlashSeconds", Ini.real((s, v) -> s.hitFlashSeconds = v))
                    .add("HitFlashStrength", Ini.real((s, v) -> s.hitFlashStrength = v))
                    .add("StrikeWithin", Ini.real((s, v) -> s.strikeWithin = v));

    private static final FieldParseTable<ProjectileBuilder> PROJECTILE =
            new FieldParseTable<ProjectileBuilder>()
                    .add("Model", Ini.string((p, v) -> p.model = v))
                    .add("Part", Ini.string((p, v) -> p.part = v))
                    .add("Scale", Ini.real((p, v) -> p.scale = v))
                    .add("Facing", Ini.real((p, v) -> p.facing = v))
                    .add("Height", Ini.real((p, v) -> p.height = v))
                    .add("Tint", (ini, p) -> p.tint = Integer.decode(ini.getNextToken()))
                    .add("Effect", Ini.string((p, v) -> p.effect = v))
                    .add("EffectOffset", Ini.real((p, v) -> p.effectOffset = v));

    // ---- the camera ----

    private float edgeScrollMargin;
    private int edgeScrollSpeedPercent = 100;

    /** How close to the edge the cursor has to be to shove the camera; 0 is off. */
    public float edgeScrollMargin() {
        return edgeScrollMargin;
    }

    /** How fast it shoves, as a percentage of what the keys move the camera at. */
    public int edgeScrollSpeedPercent() {
        return edgeScrollSpeedPercent;
    }

    private static final FieldParseTable<DungeonSettings> CAMERA =
            new FieldParseTable<DungeonSettings>()
                    .add("EdgeMargin", Ini.real((s, v) -> s.edgeScrollMargin = v))
                    .add("EdgeSpeedPercent",
                            Ini.integer((s, v) -> s.edgeScrollSpeedPercent = v));

    // ---- the flash that answers a click ----

    private float markStartRadius = 7f;
    private float markEndRadius = 1f;
    private float markSeconds = 0.4f;
    private float markSize = 3.5f;
    private float markWidth = 3f;
    private float markHeight = 0.25f;
    private float markEasePower = 3f;
    private float markFadeFrom = 0.6f;
    private float markSpinDegrees = 22f;
    private float markBrightness = 1.6f;
    private float markRingRadius = 7f;
    private int markBlinks = 2;
    private int markMoveColour = 0x3CFF6E;
    private int markAttackColour = 0xFF4436;

    /** How far out the arrowheads start, in world units. */
    public float markStartRadius() {
        return markStartRadius;
    }

    /** How near the middle they have closed to when they go out. */
    public float markEndRadius() {
        return markEndRadius;
    }

    /** The whole flight, in seconds — an acknowledgement, not an animation. */
    public float markSeconds() {
        return markSeconds;
    }

    /** Each arrowhead from its point to its back edge. */
    public float markSize() {
        return markSize;
    }

    /** How wide across the back edge. */
    public float markWidth() {
        return markWidth;
    }

    /** How far above the floor it lies. */
    public float markHeight() {
        return markHeight;
    }

    /** How strongly it slows as it arrives; 1 is a constant speed. */
    public float markEasePower() {
        return markEasePower;
    }

    /** The share of its life it travels at full strength before going out. */
    public float markFadeFrom() {
        return markFadeFrom;
    }

    /** How far the set turns over the flight. */
    public float markSpinDegrees() {
        return markSpinDegrees;
    }

    /** What its colour is multiplied by — over 1, because it is drawn additively. */
    public float markBrightness() {
        return markBrightness;
    }

    /** How wide the ring round an attacked creature is drawn. */
    public float markRingRadius() {
        return markRingRadius;
    }

    /** How many times it goes out and comes back. */
    public int markBlinks() {
        return markBlinks;
    }

    /** "Go there". */
    public int markMoveColour() {
        return markMoveColour;
    }

    /** "Kill that". */
    public int markAttackColour() {
        return markAttackColour;
    }

    // ---- the ring a skill draws while it is being aimed ----

    private float ringBandWidth = 1.6f;
    private float ringFillAlpha = 0.10f;
    private float ringEdgeAlpha = 0.85f;
    private float ringHeight = 0.3f;
    private float ringPulseDepth = 0.25f;
    private float ringPulsePerSecond = 1.4f;
    private int ringSegments = 96;
    private int ringAllowColour = 0x53E0FF;
    private int ringDenyColour = 0xFF4436;
    private int ringAreaColour = 0xFFB347;
    private float ringBrightness = 1.5f;

    /**
     * How wide the ring hugging the hero is when a skill only affects him.
     *
     * <p>A number of its own because a self-buff has no reach to draw: the ring
     * has to be some size, and the size that says "only me" is his own width and
     * a little more.
     */
    private float ringSelfRadius = 5f;

    /** How thick the ring is drawn. */
    public float ringBandWidth() {
        return ringBandWidth;
    }

    /** How strongly the inside of a ring is washed in. */
    public float ringFillAlpha() {
        return ringFillAlpha;
    }

    /** How strongly the ring itself is drawn. */
    public float ringEdgeAlpha() {
        return ringEdgeAlpha;
    }

    /** How far above the floor it lies. */
    public float ringHeight() {
        return ringHeight;
    }

    /** How much the ring breathes. */
    public float ringPulseDepth() {
        return ringPulseDepth;
    }

    /** How often it breathes. */
    public float ringPulsePerSecond() {
        return ringPulsePerSecond;
    }

    /** How many straight pieces the circle is really made of. */
    public int ringSegments() {
        return ringSegments;
    }

    /** The colour of a cast that will go through. */
    public int ringAllowColour() {
        return ringAllowColour;
    }

    /** The colour of one that will not. */
    public int ringDenyColour() {
        return ringDenyColour;
    }

    /** The colour of the blast itself. */
    public int ringAreaColour() {
        return ringAreaColour;
    }

    /** What every ring colour is multiplied by; over 1, because it is added. */
    public float ringBrightness() {
        return ringBrightness;
    }

    /** How wide the ring is for a skill that only touches the caster. */
    public float ringSelfRadius() {
        return ringSelfRadius;
    }

    private static final FieldParseTable<DungeonSettings> SKILL_RING =
            new FieldParseTable<DungeonSettings>()
                    .add("BandWidth", Ini.real((s, v) -> s.ringBandWidth = v))
                    .add("FillAlpha", Ini.real((s, v) -> s.ringFillAlpha = v))
                    .add("EdgeAlpha", Ini.real((s, v) -> s.ringEdgeAlpha = v))
                    .add("Height", Ini.real((s, v) -> s.ringHeight = v))
                    .add("PulseDepth", Ini.real((s, v) -> s.ringPulseDepth = v))
                    .add("PulsePerSecond", Ini.real((s, v) -> s.ringPulsePerSecond = v))
                    .add("Segments", Ini.integer((s, v) -> s.ringSegments = v))
                    .add("SelfRadius", Ini.real((s, v) -> s.ringSelfRadius = v))
                    .add("Brightness", Ini.real((s, v) -> s.ringBrightness = v))
                    .add("AllowColour",
                            (ini, s) -> s.ringAllowColour = Integer.decode(ini.getNextToken()))
                    .add("DenyColour",
                            (ini, s) -> s.ringDenyColour = Integer.decode(ini.getNextToken()))
                    .add("AreaColour",
                            (ini, s) -> s.ringAreaColour = Integer.decode(ini.getNextToken()));

    private static final FieldParseTable<DungeonSettings> ORDER_MARK =
            new FieldParseTable<DungeonSettings>()
                    .add("StartRadius", Ini.real((s, v) -> s.markStartRadius = v))
                    .add("EndRadius", Ini.real((s, v) -> s.markEndRadius = v))
                    .add("Seconds", Ini.real((s, v) -> s.markSeconds = v))
                    .add("Size", Ini.real((s, v) -> s.markSize = v))
                    .add("Width", Ini.real((s, v) -> s.markWidth = v))
                    .add("Height", Ini.real((s, v) -> s.markHeight = v))
                    .add("EasePower", Ini.real((s, v) -> s.markEasePower = v))
                    .add("FadeFrom", Ini.real((s, v) -> s.markFadeFrom = v))
                    .add("SpinDegrees", Ini.real((s, v) -> s.markSpinDegrees = v))
                    .add("Brightness", Ini.real((s, v) -> s.markBrightness = v))
                    .add("RingRadius", Ini.real((s, v) -> s.markRingRadius = v))
                    .add("Blinks", Ini.integer((s, v) -> s.markBlinks = v))
                    .add("MoveColour",
                            (ini, s) -> s.markMoveColour = Integer.decode(ini.getNextToken()))
                    .add("AttackColour",
                            (ini, s) -> s.markAttackColour = Integer.decode(ini.getNextToken()));

    // ---- the dark ----

    private boolean fogLineOfSight = true;
    private int fogUnseenPercent;
    private int fogRememberedPercent = 34;
    private int fogVisiblePercent = 100;
    private int fogSoftenCells = 2;
    private int fogOpenPerSecond = 7;
    private int fogTextureSize = 256;
    private int fogTint = 0x000000;

    /**
     * How the dark behaves and what colour it is.
     *
     * <p>Look rather than rule, like the tile kit and a monster's colour: the
     * simulation never reads it. What the player can see does not change what is
     * there — which is what makes fog something the client may have an opinion
     * about at all.
     */
    public boolean fogLineOfSight() {
        return fogLineOfSight;
    }

    /** How brightly ground nobody has walked is drawn; 0 is a black floor. */
    public int fogUnseenPercent() {
        return fogUnseenPercent;
    }

    /** How brightly a room he has left is drawn, as a percentage of a lit one. */
    public int fogRememberedPercent() {
        return fogRememberedPercent;
    }

    /** How brightly a room in sight is drawn; 100 is the scene's own light. */
    public int fogVisiblePercent() {
        return fogVisiblePercent;
    }

    /** How many cells the edge of the light is smeared over. */
    public int fogSoftenCells() {
        return fogSoftenCells;
    }

    /** How fast the dark gives way, as a share of the remaining gap per second. */
    public int fogOpenPerSecond() {
        return fogOpenPerSecond;
    }

    /** How many texels across the fog sheet is drawn — nothing to do with cells. */
    public int fogTextureSize() {
        return fogTextureSize;
    }

    /** What unlit stone fades toward — the colour of the dark itself. */
    public int fogTint() {
        return fogTint;
    }

    private static final FieldParseTable<DungeonSettings> FOG =
            new FieldParseTable<DungeonSettings>()
                    .add("LineOfSight", Ini.bool((s, v) -> s.fogLineOfSight = v))
                    .add("UnseenPercent", Ini.integer((s, v) -> s.fogUnseenPercent = v))
                    .add("RememberedPercent",
                            Ini.integer((s, v) -> s.fogRememberedPercent = v))
                    .add("VisiblePercent", Ini.integer((s, v) -> s.fogVisiblePercent = v))
                    .add("SoftenCells", Ini.integer((s, v) -> s.fogSoftenCells = v))
                    .add("OpenPerSecond", Ini.integer((s, v) -> s.fogOpenPerSecond = v))
                    .add("TextureSize", Ini.integer((s, v) -> s.fogTextureSize = v))
                    .add("Tint", (ini, s) -> s.fogTint = Integer.decode(ini.getNextToken()));

    // ---- the light ----

    private int sunPitch = 57;
    private int sunYaw = 219;
    private int sunStrengthPercent = 100;
    private int sunAmbientPercent = 50;
    private int sunColour = 0xFFF7E6;
    private int sunAmbientTint = 0xE6E6FF;

    /**
     * How far above the horizon the sun stands, in degrees.
     *
     * <p>The number that decides whether the map has any depth in it. A floor and
     * the lid over a wall are the same tile facing the same way, so the only thing
     * that can shade one differently from the other is light arriving at an angle
     * — and at 90 there is no angle, so there is no difference, and a player cannot
     * see where he is allowed to walk.
     *
     * <p>Look and nothing else, like the fog: the simulation never reads it.
     */
    public int sunPitch() {
        return sunPitch;
    }

    /** Which way round the compass it comes from, deciding which face is the lit one. */
    public int sunYaw() {
        return sunYaw;
    }

    /** How bright the sun is, as a percentage of its own colour. */
    public int sunStrengthPercent() {
        return sunStrengthPercent;
    }

    /**
     * How much light a face the sun never reaches still gets, as a percentage.
     *
     * <p>The sun's opposite, and it wants reading with it. Raising this is what
     * stops an unlit wall being a black shape, and raising it too far is what
     * flattens the picture again — at 100 every face is lit the same whatever it
     * faces, which is the very thing the pitch is there to prevent.
     */
    public int sunAmbientPercent() {
        return sunAmbientPercent;
    }

    /** What colour the sunlight is, packed {@code 0xRRGGBB}. */
    public int sunColour() {
        return sunColour;
    }

    /** What colour the shadowed side is, packed {@code 0xRRGGBB}. */
    public int sunAmbientTint() {
        return sunAmbientTint;
    }

    private static final FieldParseTable<DungeonSettings> SUN =
            new FieldParseTable<DungeonSettings>()
                    .add("Pitch", Ini.integer((s, v) -> s.sunPitch = v))
                    .add("Yaw", Ini.integer((s, v) -> s.sunYaw = v))
                    .add("StrengthPercent", Ini.integer((s, v) -> s.sunStrengthPercent = v))
                    .add("AmbientPercent", Ini.integer((s, v) -> s.sunAmbientPercent = v))
                    .add("Colour", (ini, s) -> s.sunColour = Integer.decode(ini.getNextToken()))
                    .add("AmbientTint",
                            (ini, s) -> s.sunAmbientTint = Integer.decode(ini.getNextToken()));

    // ---- the bar over a creature's head ----

    /**
     * One rung of the bar's segment table, exactly as the file writes it.
     *
     * <p>Its own little record rather than a pair of parallel lists, because two
     * lists that have to be the same length are two lists that one day are not,
     * and the file gives no hint which of them the missing entry belonged to.
     *
     * @param upTo  the greatest health this rung covers, or 0 for the open end
     * @param value how much health one mark is worth here
     */
    public record BarStep(int upTo, int value) {
    }

    private final java.util.List<BarStep> unitBarSegments = new java.util.ArrayList<>();

    private int unitBarShortestAt = 30;
    private int unitBarLongestAt = 1400;
    private float unitBarShortest = 80f;
    private float unitBarLongest = 220f;
    private float unitBarHeight = 13f;
    private float unitBarManaHeight = 6f;
    private float unitBarGap = 2f;
    private float unitBarLift = 1.4f;
    private float unitBarRing = 26f;
    private float unitBarRingEdge = 2f;
    private float unitBarRingGap = 4f;
    private float unitBarArc = 3f;
    private int unitBarEnemy = 0xA8322B;
    private int unitBarFriend = 0x8FC4AE;
    private int unitBarMana = 0x3E6FA8;
    private int unitBarTrough = 0x16130F;
    private int unitBarTick = 0x0A0806;
    private int unitBarRingFace = 0x16130F;
    private int unitBarRingRim = 0x8FC4AE;
    private int unitBarBossRim = 0xE8A33D;
    private int unitBarLettering = 0xD9CFBA;
    private float unitBarNameSize = 11f;
    private float unitBarBossNameSize = 15f;
    private float unitBarCountSize = 10f;
    private float unitBarLevelSize = 12f;

    /**
     * The segment table, coarsest last, or empty for a game that draws no bars.
     *
     * <p>Empty is the meaningful default and the scalars above are not: without a
     * table there is nothing to divide a bar into, so the client draws none at
     * all rather than inventing lots of its own. See {@code UnitBarLook.NONE}.
     */
    public java.util.List<BarStep> unitBarSegments() {
        return java.util.List.copyOf(unitBarSegments);
    }

    public int unitBarShortestAt() {
        return unitBarShortestAt;
    }

    public int unitBarLongestAt() {
        return unitBarLongestAt;
    }

    public float unitBarShortest() {
        return unitBarShortest;
    }

    public float unitBarLongest() {
        return unitBarLongest;
    }

    public float unitBarHeight() {
        return unitBarHeight;
    }

    public float unitBarManaHeight() {
        return unitBarManaHeight;
    }

    public float unitBarGap() {
        return unitBarGap;
    }

    public float unitBarLift() {
        return unitBarLift;
    }

    public float unitBarRing() {
        return unitBarRing;
    }

    public float unitBarRingEdge() {
        return unitBarRingEdge;
    }

    public float unitBarRingGap() {
        return unitBarRingGap;
    }

    public float unitBarArc() {
        return unitBarArc;
    }

    public int unitBarEnemy() {
        return unitBarEnemy;
    }

    public int unitBarFriend() {
        return unitBarFriend;
    }

    public int unitBarMana() {
        return unitBarMana;
    }

    public int unitBarTrough() {
        return unitBarTrough;
    }

    public int unitBarTick() {
        return unitBarTick;
    }

    public int unitBarRingFace() {
        return unitBarRingFace;
    }

    public int unitBarRingRim() {
        return unitBarRingRim;
    }

    public int unitBarBossRim() {
        return unitBarBossRim;
    }


    public int unitBarLettering() {
        return unitBarLettering;
    }

    public float unitBarNameSize() {
        return unitBarNameSize;
    }

    public float unitBarBossNameSize() {
        return unitBarBossNameSize;
    }

    public float unitBarCountSize() {
        return unitBarCountSize;
    }

    public float unitBarLevelSize() {
        return unitBarLevelSize;
    }

    /**
     * How a creature's bar is drawn. Everything here is found by eye, which is
     * why none of it is in the client -- see {@code uz.duke.client3d.UnitBarLook}.
     */
    private static final FieldParseTable<DungeonSettings> UNIT_BAR =
            new FieldParseTable<DungeonSettings>()
                    // "<up to>:<worth>", and "*" for the rung with no ceiling.
                    // One field rather than two, so a rung cannot be half-written.
                    .add("Segments", (ini, s) -> {
                        for (var rung : ini.getRestOfLine().trim().split("\s+")) {
                            var halves = rung.split(":", 2);
                            if (halves.length < 2 || halves[1].isBlank()) {
                                continue;
                            }
                            int upTo = "*".equals(halves[0]) ? 0 : Integer.parseInt(halves[0]);
                            s.unitBarSegments.add(
                                    new BarStep(upTo, Integer.parseInt(halves[1])));
                        }
                    })
                    .add("ShortestAt", Ini.integer((s, v) -> s.unitBarShortestAt = v))
                    .add("LongestAt", Ini.integer((s, v) -> s.unitBarLongestAt = v))
                    .add("Shortest", Ini.real((s, v) -> s.unitBarShortest = v))
                    .add("Longest", Ini.real((s, v) -> s.unitBarLongest = v))
                    .add("Height", Ini.real((s, v) -> s.unitBarHeight = v))
                    .add("ManaHeight", Ini.real((s, v) -> s.unitBarManaHeight = v))
                    .add("Gap", Ini.real((s, v) -> s.unitBarGap = v))
                    .add("Lift", Ini.real((s, v) -> s.unitBarLift = v))
                    .add("Ring", Ini.real((s, v) -> s.unitBarRing = v))
                    .add("RingEdge", Ini.real((s, v) -> s.unitBarRingEdge = v))
                    .add("RingGap", Ini.real((s, v) -> s.unitBarRingGap = v))
                    .add("Arc", Ini.real((s, v) -> s.unitBarArc = v))
                    .add("Enemy",
                            (ini, s) -> s.unitBarEnemy = Integer.decode(ini.getNextToken()))
                    .add("Friend",
                            (ini, s) -> s.unitBarFriend = Integer.decode(ini.getNextToken()))
                    .add("Mana",
                            (ini, s) -> s.unitBarMana = Integer.decode(ini.getNextToken()))
                    .add("Trough",
                            (ini, s) -> s.unitBarTrough = Integer.decode(ini.getNextToken()))
                    .add("Tick",
                            (ini, s) -> s.unitBarTick = Integer.decode(ini.getNextToken()))
                    .add("RingFace",
                            (ini, s) -> s.unitBarRingFace = Integer.decode(ini.getNextToken()))
                    .add("RingRim",
                            (ini, s) -> s.unitBarRingRim = Integer.decode(ini.getNextToken()))
                    .add("BossRim",
                            (ini, s) -> s.unitBarBossRim = Integer.decode(ini.getNextToken()))
                    .add("Lettering",
                            (ini, s) -> s.unitBarLettering = Integer.decode(ini.getNextToken()))
                    .add("NameSize", Ini.real((s, v) -> s.unitBarNameSize = v))
                    .add("BossNameSize", Ini.real((s, v) -> s.unitBarBossNameSize = v))
                    .add("CountSize", Ini.real((s, v) -> s.unitBarCountSize = v))
                    .add("LevelSize", Ini.real((s, v) -> s.unitBarLevelSize = v));

    private String hudDepthWord = "DEPTH";
    private String hudRankSuffix = "-lv";
    private String hudChooseHeroWord = "";
    private String hudChooseHeroHint = "";
    private String hudChooseModeWord = "";
    private String hudChooseModeHint = "";
    private String hudChooseStageWord = "";
    private String hudChooseStageHint = "";
    private String hudEndlessWord = "";
    private String hudEndlessBlurb = "";
    private String hudStagesWord = "";
    private String hudStagesBlurb = "";
    private String hudAttackWord = "";
    private String hudArmourWord = "";
    private String hudSpeedWord = "";
    private String hudStrengthWord = "";
    private String hudAgilityWord = "";
    private String hudIntelligenceWord = "";
    private String hudHealthWord = "";
    private String hudPrimaryWord = "";
    private String hudEachPointWord = "";
    private String hudStrengthStatIcon = "";
    private String hudAgilityStatIcon = "";
    private String hudIntelligenceStatIcon = "";

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

    /** The heading over the roster the player is asked to choose from. */
    public String hudChooseHeroWord() {
        return hudChooseHeroWord;
    }

    /** And the line along the foot of it, saying how to answer. */
    public String hudChooseHeroHint() {
        return hudChooseHeroHint;
    }

    /** The screen before that one: which of the two games is being played. */
    public String hudChooseModeWord() {
        return hudChooseModeWord;
    }

    public String hudChooseModeHint() {
        return hudChooseModeHint;
    }

    /** And the one after it, when he chose the frozen kind. */
    public String hudChooseStageWord() {
        return hudChooseStageWord;
    }

    public String hudChooseStageHint() {
        return hudChooseStageHint;
    }

    /** What the two games are called, and one line each about them. */
    public String hudEndlessWord() {
        return hudEndlessWord;
    }

    public String hudEndlessBlurb() {
        return hudEndlessBlurb;
    }

    public String hudStagesWord() {
        return hudStagesWord;
    }

    public String hudStagesBlurb() {
        return hudStagesBlurb;
    }

    /** The three figures under the bars, in the order the panel writes them. */
    public String hudAttackWord() {
        return hudAttackWord;
    }

    public String hudArmourWord() {
        return hudArmourWord;
    }

    public String hudSpeedWord() {
        return hudSpeedWord;
    }

    /** What an attribute is called on the panel. */
    public String hudAttributeWord(Attribute attribute) {
        return switch (attribute) {
            case STRENGTH -> hudStrengthWord;
            case AGILITY -> hudAgilityWord;
            case INTELLIGENCE -> hudIntelligenceWord;
        };
    }

    /** The picture beside an attribute, with its folder in front of it. */
    public String hudAttributeIcon(Attribute attribute) {
        return inStats(switch (attribute) {
            case STRENGTH -> hudStrengthStatIcon;
            case AGILITY -> hudAgilityStatIcon;
            case INTELLIGENCE -> hudIntelligenceStatIcon;
        });
    }

    /** What maximum health is called on an attribute's card. */
    public String hudHealthWord() {
        return hudHealthWord;
    }

    /** What an attribute's card says under its name when it is his primary. */
    public String hudPrimaryWord() {
        return hudPrimaryWord;
    }

    /** The line over what one point of an attribute gives. */
    public String hudEachPointWord() {
        return hudEachPointWord;
    }

    private String hudMonsterFace = "";
    private String hudSkillsWord = "";

    /**
     * What an unspent level is called, beside the skill heading.
     *
     * <p>A word rather than a number's label, for the same reason every other word
     * on the panel is here: the client serves three other games and has no
     * business knowing which language this one speaks.
     */
    private String hudPointsWord = "";
    /**
     * The tooltip's words: what each row of figures is called, and what the
     * footer says in each of its three states. All of them here for the same
     * reason every other word on the panel is -- the client has three other
     * games to serve and no business knowing which language this one speaks.
     */
    private String hudDamageWord = "";
    private String hudCooldownWord = "";
    private String hudRadiusWord = "";
    private String hudRangeWord = "";
    private String hudBoostWord = "";
    private String hudRaiseWord = "";
    private String hudRaiseKeyWord = "";
    private String hudMaxedWord = "";
    private String hudNoPointsWord = "";
    private String hudSecondsWord = "";

    /** What a skill with nothing left to buy is called, and one nobody has bought. */
    private String hudMasterWord = "";
    private String hudLockedWord = "";
    private String hudItemsWord = "";
    private String hudHeroTitle = "";
    private String hudMoveWord = "";
    private String hudAttackOrderWord = "";
    private String hudStopWord = "";
    private String hudGuardWord = "";
    private String hudIconFolder = "";
    private String hudSkinFolder = "";
    private String hudCursorFolder = "";

    /** The drawing that stands in the portrait for something that is not his. */
    public String hudMonsterFace() {
        return hudMonsterFace;
    }

    /** The heading over the skill row. */
    public String hudSkillsWord() {
        return hudSkillsWord;
    }

    public String hudPointsWord() {
        return hudPointsWord;
    }

    public String hudDamageWord() {
        return hudDamageWord;
    }

    public String hudCooldownWord() {
        return hudCooldownWord;
    }

    public String hudRadiusWord() {
        return hudRadiusWord;
    }

    public String hudRangeWord() {
        return hudRangeWord;
    }

    public String hudBoostWord() {
        return hudBoostWord;
    }

    public String hudRaiseWord() {
        return hudRaiseWord;
    }

    public String hudRaiseKeyWord() {
        return hudRaiseKeyWord;
    }

    public String hudMaxedWord() {
        return hudMaxedWord;
    }

    public String hudNoPointsWord() {
        return hudNoPointsWord;
    }

    public String hudSecondsWord() {
        return hudSecondsWord;
    }

    public String hudMasterWord() {
        return hudMasterWord;
    }

    public String hudLockedWord() {
        return hudLockedWord;
    }

    /** The heading over his bag. */
    public String hudItemsWord() {
        return hudItemsWord;
    }

    /** What he is, drawn under his name. */
    public String hudHeroTitle() {
        return hudHeroTitle;
    }

    /** The four orders on the buttons beside the map, in the order they are drawn. */
    public java.util.List<String> hudOrderWords() {
        return java.util.List.of(hudMoveWord, hudAttackOrderWord, hudStopWord, hudGuardWord);
    }

    /**
     * Where a skill's {@code Icon} is to be found, joined onto the front of it —
     * the same arrangement the tile kit uses, and for the same reason: a folder
     * written once beats a folder written on every line that names a file.
     */
    public String hudIcon(String icon) {
        return icon == null || icon.isBlank() ? "" : hudIconFolder + icon;
    }

    private String hudManaWord = "";

    /** What a skill's price is called on its card, and the bar it comes out of. */
    public String hudManaWord() {
        return hudManaWord;
    }

    private String hudCommandIconFolder = "";
    private String hudStatIconFolder = "";
    private boolean hudPaintedSkillIcons;
    private String hudMoveIcon = "";
    private String hudAttackOrderIcon = "";
    private String hudStopIcon = "";
    private String hudGuardIcon = "";
    private String hudAttackStatIcon = "";
    private String hudArmourStatIcon = "";
    private String hudSpeedStatIcon = "";

    /**
     * Whether the skill pictures carry their own colours.
     *
     * <p>The panel tints what it draws, which is how one white drawing serves a
     * skill that is ready, one reloading and one locked. Painted pictures cannot
     * take that — a blue frost burst multiplied by the torch colour is a gold one
     * — so a game that ships them says so here and the panel tells the states in
     * brightness instead.
     */
    public boolean hudPaintedSkillIcons() {
        return hudPaintedSkillIcons;
    }

    /** The four order buttons' pictures, in the order the buttons are drawn. */
    public java.util.List<String> hudOrderIcons() {
        return java.util.List.of(inCommands(hudMoveIcon), inCommands(hudAttackOrderIcon),
                inCommands(hudStopIcon), inCommands(hudGuardIcon));
    }

    /**
     * The pictures beside the four figures under the bars, in their own order.
     *
     * <p>Beside the figures rather than counted off against them: the panel used
     * to choose by position out of a list it held itself, which made the client
     * the one deciding that a game's third figure is a lightning bolt.
     */
    public java.util.List<String> hudStatIcons() {
        return java.util.List.of(inStats(hudAttackStatIcon), inStats(hudArmourStatIcon),
                inStats(hudSpeedStatIcon));
    }

    private String inCommands(String icon) {
        return icon == null || icon.isBlank() ? "" : hudCommandIconFolder + icon;
    }

    private String inStats(String icon) {
        return icon == null || icon.isBlank() ? "" : hudStatIconFolder + icon;
    }

    private static final FieldParseTable<DungeonSettings> HUD =
            new FieldParseTable<DungeonSettings>()
                    .add("DepthWord", Ini.restOfLine((s, v) -> s.hudDepthWord = v))
                    .add("RankSuffix", Ini.restOfLine((s, v) -> s.hudRankSuffix = v))
                    // The screen that asks who is being played. Words rather than
                    // pictures, like every other word on the bar: the client draws
                    // four games and speaks none of their languages.
                    .add("ChooseHeroWord", Ini.restOfLine((s, v) -> s.hudChooseHeroWord = v))
                    .add("ChooseHeroHint", Ini.restOfLine((s, v) -> s.hudChooseHeroHint = v))
                    // The screen before it: which of the two games. And the one
                    // after, when he chose the frozen kind.
                    .add("ChooseModeWord", Ini.restOfLine((s, v) -> s.hudChooseModeWord = v))
                    .add("ChooseModeHint", Ini.restOfLine((s, v) -> s.hudChooseModeHint = v))
                    .add("ChooseStageWord", Ini.restOfLine((s, v) -> s.hudChooseStageWord = v))
                    .add("ChooseStageHint", Ini.restOfLine((s, v) -> s.hudChooseStageHint = v))
                    .add("EndlessWord", Ini.restOfLine((s, v) -> s.hudEndlessWord = v))
                    .add("EndlessBlurb", Ini.restOfLine((s, v) -> s.hudEndlessBlurb = v))
                    .add("StagesWord", Ini.restOfLine((s, v) -> s.hudStagesWord = v))
                    .add("StagesBlurb", Ini.restOfLine((s, v) -> s.hudStagesBlurb = v))
                    .add("AttackWord", Ini.restOfLine((s, v) -> s.hudAttackWord = v))
                    .add("ArmourWord", Ini.restOfLine((s, v) -> s.hudArmourWord = v))
                    .add("SpeedWord", Ini.restOfLine((s, v) -> s.hudSpeedWord = v))
                    .add("StrengthWord", Ini.restOfLine((s, v) -> s.hudStrengthWord = v))
                    .add("AgilityWord", Ini.restOfLine((s, v) -> s.hudAgilityWord = v))
                    .add("IntelligenceWord",
                            Ini.restOfLine((s, v) -> s.hudIntelligenceWord = v))
                    .add("HealthWord", Ini.restOfLine((s, v) -> s.hudHealthWord = v))
                    .add("PrimaryWord", Ini.restOfLine((s, v) -> s.hudPrimaryWord = v))
                    .add("EachPointWord", Ini.restOfLine((s, v) -> s.hudEachPointWord = v))
                    .add("MonsterFace", Ini.string((s, v) -> s.hudMonsterFace = v))
                    .add("SkillsWord", Ini.restOfLine((s, v) -> s.hudSkillsWord = v))
                    .add("PointsWord", Ini.restOfLine((s, v) -> s.hudPointsWord = v))
                    .add("DamageWord", Ini.restOfLine((s, v) -> s.hudDamageWord = v))
                    .add("CooldownWord", Ini.restOfLine((s, v) -> s.hudCooldownWord = v))
                    .add("RadiusWord", Ini.restOfLine((s, v) -> s.hudRadiusWord = v))
                    .add("RangeWord", Ini.restOfLine((s, v) -> s.hudRangeWord = v))
                    .add("BoostWord", Ini.restOfLine((s, v) -> s.hudBoostWord = v))
                    .add("RaiseWord", Ini.restOfLine((s, v) -> s.hudRaiseWord = v))
                    .add("RaiseKeyWord", Ini.restOfLine((s, v) -> s.hudRaiseKeyWord = v))
                    .add("MaxedWord", Ini.restOfLine((s, v) -> s.hudMaxedWord = v))
                    .add("NoPointsWord", Ini.restOfLine((s, v) -> s.hudNoPointsWord = v))
                    .add("SecondsWord", Ini.restOfLine((s, v) -> s.hudSecondsWord = v))
                    .add("MasterWord", Ini.restOfLine((s, v) -> s.hudMasterWord = v))
                    .add("LockedWord", Ini.restOfLine((s, v) -> s.hudLockedWord = v))
                    .add("ItemsWord", Ini.restOfLine((s, v) -> s.hudItemsWord = v))
                    .add("HeroTitle", Ini.restOfLine((s, v) -> s.hudHeroTitle = v))
                    .add("CmdMoveWord", Ini.restOfLine((s, v) -> s.hudMoveWord = v))
                    .add("CmdAttackWord", Ini.restOfLine((s, v) -> s.hudAttackOrderWord = v))
                    .add("CmdStopWord", Ini.restOfLine((s, v) -> s.hudStopWord = v))
                    .add("CmdGuardWord", Ini.restOfLine((s, v) -> s.hudGuardWord = v))
                    .add("ManaWord", Ini.restOfLine((s, v) -> s.hudManaWord = v))
                    .add("IconFolder", Ini.string((s, v) -> s.hudIconFolder = v))
                    .add("CommandIconFolder",
                            Ini.string((s, v) -> s.hudCommandIconFolder = v))
                    .add("StatIconFolder", Ini.string((s, v) -> s.hudStatIconFolder = v))
                    .add("PaintedSkillIcons",
                            Ini.bool((s, v) -> s.hudPaintedSkillIcons = v))
                    .add("CmdMoveIcon", Ini.string((s, v) -> s.hudMoveIcon = v))
                    .add("CmdAttackIcon", Ini.string((s, v) -> s.hudAttackOrderIcon = v))
                    .add("CmdStopIcon", Ini.string((s, v) -> s.hudStopIcon = v))
                    .add("CmdGuardIcon", Ini.string((s, v) -> s.hudGuardIcon = v))
                    .add("AttackIcon", Ini.string((s, v) -> s.hudAttackStatIcon = v))
                    .add("ArmourIcon", Ini.string((s, v) -> s.hudArmourStatIcon = v))
                    .add("SpeedIcon", Ini.string((s, v) -> s.hudSpeedStatIcon = v))
                    .add("StrengthIcon", Ini.string((s, v) -> s.hudStrengthStatIcon = v))
                    .add("AgilityIcon", Ini.string((s, v) -> s.hudAgilityStatIcon = v))
                    .add("IntelligenceIcon",
                            Ini.string((s, v) -> s.hudIntelligenceStatIcon = v))
                    .add("SkinFolder", Ini.string((s, v) -> s.hudSkinFolder = v))
                    .add("CursorFolder", Ini.string((s, v) -> s.hudCursorFolder = v))
                    // Panel-wide rather than per-hero: what a portrait costs is a
                    // fact about the machine drawing it, not about whose face is in
                    // it. See DungeonPortrait for the faces themselves.
                    .add("PortraitFps", Ini.integer((s, v) -> s.portraitFps = v));

    // ---- the lettering the menus are set in ----

    private String menuTitleFont = "";
    private String menuRowFont = "";

    /**
     * The font a menu title is drawn in, or {@code null} when the file names
     * none and the engine's own lettering should be used.
     */
    public String menuTitleFont() {
        return menuTitleFont.isBlank() ? null : menuTitleFont;
    }

    /** The font a menu row is drawn in, or {@code null} for the engine's own. */
    public String menuRowFont() {
        return menuRowFont.isBlank() ? null : menuRowFont;
    }

    private static final FieldParseTable<DungeonSettings> MENU =
            new FieldParseTable<DungeonSettings>()
                    .add("TitleFont", Ini.string((s, v) -> s.menuTitleFont = v))
                    .add("RowFont", Ini.string((s, v) -> s.menuRowFont = v));

    private static final FieldParseTable<HeroBuilder> HERO_LOOK =
            new FieldParseTable<HeroBuilder>()
                    // What the panel calls him under his name. His, not the
                    // panel's: one line in DungeonHud was the archer's title on
                    // every hero who came after him.
                    .add("Title", Ini.restOfLine((s, v) -> s.title = v))
                    // What he shrugs off before earning a level. Here rather than
                    // in his creature block because a hero's armour is rewritten
                    // from his level whenever it changes, so a template's own
                    // figure would not survive the first one.
                    // How close he walks before letting his weapon work. His, because
                    // it has to be inside HIS reach: one figure for everybody was
                    // the archer's, and a swordsman stopped four bodies short.
                    .add("CloseDistance", Ini.real((s, v) -> s.closeDistance = v))
                    // What he casts out of, before any level is earned. Two
                    // numbers rather than one because a pool and a trickle are
                    // different things to play against: see HeroLook.
                    .add("MaxMana", Ini.integer((s, v) -> s.maxMana = v))
                    .add("ManaRegen", Ini.integer((s, v) -> s.manaRegen = v))
                    .add("HealthRegen", Ini.integer((s, v) -> s.healthRegen = v))
                    .add("ArmourPercent", Ini.integer((s, v) -> s.armourPercent = v))
                    // His three and which one he hits with. Written as decimals and
                    // read into exact tenths, never through a float: a level of 1.8
                    // strength fifteen times over has to be 27 on every machine.
                    .add("Primary", Ini.string((s, v) -> s.primary = Attribute.named(v)))
                    .add("Strength", (ini, s) -> s.strength = exactly(ini, "Strength", 1))
                    .add("Agility", (ini, s) -> s.agility = exactly(ini, "Agility", 1))
                    .add("Intelligence",
                            (ini, s) -> s.intelligence = exactly(ini, "Intelligence", 1))
                    .add("StrPerLevel",
                            (ini, s) -> s.strengthPerLevel = exactly(ini, "StrPerLevel", 1))
                    .add("AgiPerLevel",
                            (ini, s) -> s.agilityPerLevel = exactly(ini, "AgiPerLevel", 1))
                    .add("IntPerLevel",
                            (ini, s) -> s.intelligencePerLevel = exactly(ini, "IntPerLevel", 1))
                    .add("Model", Ini.string((s, v) -> s.model = v))
                    .add("Texture", Ini.string((s, v) -> s.texture = v))
                    .add("ModelScale", Ini.real((s, v) -> s.modelScale = v))
                    .add("Facing", Ini.real((s, v) -> s.facing = v))
                    // Repeatable: a kit sorts its clips by what the movement is
                    // for, so standing and dying come out of one file and a bow
                    // out of another, and he needs all of them.
                    .add("AnimationsFrom", Ini.string((s, v) -> s.animations.add(v)))
                    .add("Idle", Ini.string((s, v) -> s.idle = v))
                    .add("Walk", Ini.string((s, v) -> s.walk = v))
                    .add("Attack", Ini.string((s, v) -> s.attack = v))
                    .add("Hurt", Ini.string((s, v) -> s.hurt = v))
                    .add("Death", Ini.string((s, v) -> s.death = v))
                    // ★ Repeatable, like AnimationsFrom above and for the same
                    // reason: a hero is rarely one thing in one hand. Holds names
                    // a thing and every Held* line under it describes THAT thing,
                    // so a knight is a sword paragraph and a shield paragraph.
                    .add("Holds", Ini.string((s, v) -> s.holds(v)))
                    .add("HeldIn", Ini.string((s, v) -> s.describe(h -> new Held(h.model(), v,
                            h.scale(), h.pitch(), h.yaw(), h.roll(), h.x(), h.y(), h.z()))))
                    .add("HeldScale", Ini.real((s, v) -> s.describe(h -> new Held(h.model(),
                            h.bone(), v, h.pitch(), h.yaw(), h.roll(), h.x(), h.y(), h.z()))))
                    .add("HeldPitch", Ini.real((s, v) -> s.describe(h -> new Held(h.model(),
                            h.bone(), h.scale(), v, h.yaw(), h.roll(), h.x(), h.y(), h.z()))))
                    .add("HeldYaw", Ini.real((s, v) -> s.describe(h -> new Held(h.model(),
                            h.bone(), h.scale(), h.pitch(), v, h.roll(), h.x(), h.y(), h.z()))))
                    .add("HeldRoll", Ini.real((s, v) -> s.describe(h -> new Held(h.model(),
                            h.bone(), h.scale(), h.pitch(), h.yaw(), v, h.x(), h.y(), h.z()))))
                    // Three numbers on one line, because a place is one fact.
                    .add("HeldAt", (ini, s) -> {
                        float x = ini.scanReal(ini.getNextToken());
                        float y = ini.scanReal(ini.getNextToken());
                        float z = ini.scanReal(ini.getNextToken());
                        s.describe(h -> new Held(h.model(), h.bone(), h.scale(), h.pitch(),
                                h.yaw(), h.roll(), x, y, z));
                    });

    /**
     * The live portrait's block.
     *
     * <p>No model, no scale and no library: those are in his own block above,
     * under the same name, and a portrait that named its own art could drift out
     * of step with the creature it is the face of.
     */
    private static final FieldParseTable<PortraitBuilder> PORTRAIT =
            new FieldParseTable<PortraitBuilder>()
                    .add("Head", Ini.real((s, v) -> s.head = v))
                    .add("Show", Ini.real((s, v) -> s.show = v))
                    .add("Yaw", Ini.real((s, v) -> s.yaw = v))
                    .add("Pitch", Ini.real((s, v) -> s.pitch = v))
                    .add("Fov", Ini.real((s, v) -> s.fov = v))
                    .add("Calm", Ini.string((s, v) -> s.calm = v))
                    .add("Fight", Ini.string((s, v) -> s.fight = v))
                    .add("Hurt", Ini.string((s, v) -> s.hurt = v))
                    .add("Dead", Ini.string((s, v) -> s.dead = v))
                    .add("LevelUp", Ini.string((s, v) -> s.levelUp = v))
                    .add("HurtBelowPercent", Ini.real((s, v) -> s.hurtBelowPercent = v))
                    .add("HurtSpeed", Ini.real((s, v) -> s.hurtSpeed = v));

    private static final FieldParseTable<DungeonSettings> ANIMATIONS =
            new FieldParseTable<DungeonSettings>()
                    .add("Library", Ini.string((s, v) -> s.animationLibraries.add(v)))
                    .add("Idle", Ini.string((s, v) -> s.defaultIdle = v))
                    .add("Walk", Ini.string((s, v) -> s.defaultWalk = v))
                    .add("Attack", Ini.string((s, v) -> s.defaultAttack = v))
                    .add("Hurt", Ini.string((s, v) -> s.defaultHurt = v))
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
                    // One per floor, in order, and the list is also how many floors
                    // there are: kill the last of them and the run is won.
                    .add("Bosses", (ini, s) -> {
                        for (var name : ini.getRestOfLine().trim().split("\s+")) {
                            if (!name.isBlank()) {
                                s.bosses.add(name);
                            }
                        }
                    })
                    // Who stands with the boss: a kind and how many, and again. Said once
                    // more it replaces the list rather than adding to it, so a file can
                    // send the guard away by saying nothing.
                    .add("BossGuards", (ini, s) -> {
                        s.bossGuards.clear();
                        var line = ini.getRestOfLine();
                        if (line == null || line.isBlank()) {
                            return;
                        }
                        var words = line.trim().split("\s+");
                        require(words.length % 2 == 0, "BossGuards is pairs: a kind, then how many of it");
                        for (int at = 0; at < words.length; at += 2) {
                            s.bossGuards.add(new BossGuard(words[at], Integer.parseInt(words[at + 1])));
                        }
                    })
                    .add("BossGuardRing", Ini.integer((s, v) -> s.bossGuardRing = v))
                    .add("ExperiencePercentPerDepth",
                            Ini.integer((s, v) -> s.experiencePercentPerDepth = v));

    private static final FieldParseTable<DungeonSettings> RUN =
            new FieldParseTable<DungeonSettings>()
                    .add("RespawnDelayFrames", Ini.integer((s, v) -> s.respawnDelayFrames = v))
                    .add("DescendDelayFrames", Ini.integer((s, v) -> s.descendDelayFrames = v))
                    .add("VictoryFrames", Ini.integer((s, v) -> s.victoryFrames = v))
                    .add("DiedWord", Ini.restOfLine((s, v) -> s.diedWord = v))
                    .add("WonWord", Ini.restOfLine((s, v) -> s.wonWord = v))
                    .add("NextDepthWord", Ini.restOfLine((s, v) -> s.nextDepthWord = v))
                    // Which hero walks into the dungeon. Named rather than chosen,
                    // until there is a screen to choose on.
                    .add("DefaultHero", Ini.string((s, v) -> s.playedHero = v));

    private static final FieldParseTable<DungeonSettings> STAGE =
            new FieldParseTable<DungeonSettings>()
                    // restOfLine rather than a token: a path may have a space in it,
                    // and a stage found at half its own name is a stage not found.
                    .add("File", Ini.restOfLine((s, v) -> s.stageFile = v));

    private static final FieldParseTable<DungeonSettings> LEVELLING =
            new FieldParseTable<DungeonSettings>()
                    .add("MaxLevel", Ini.integer((s, v) -> s.maxLevel = v))
                    .add("SkillSpread", Ini.integer((s, v) -> s.skillSpread = v))
                    .add("XpBase", Ini.integer((s, v) -> s.xpBase = v))
                    .add("XpStep", Ini.integer((s, v) -> s.xpStep = v))
                    .add("ArmourPercentPerLevel", Ini.integer((s, v) -> s.armourPercentPerLevel = v))
                    .add("MinDamageTakenPercent", Ini.integer((s, v) -> s.minDamageTakenPercent = v))
                    .add("ManaPerKill", Ini.integer((s, v) -> s.manaPerKill = v))
                    .add("LevelUpBannerFrames", Ini.integer((s, v) -> s.levelUpBannerFrames = v));

    private static final FieldParseTable<DungeonSettings> ATTRIBUTES =
            new FieldParseTable<DungeonSettings>()
                    .add("HealthPerStrength",
                            (ini, s) -> s.healthPerStrength = exactly(ini, "HealthPerStrength", 2))
                    .add("SpeedPerAgility",
                            (ini, s) -> s.speedPerAgility = exactly(ini, "SpeedPerAgility", 2))
                    .add("ManaPerIntelligence",
                            (ini, s) -> s.manaPerIntelligence = exactly(ini, "ManaPerIntelligence", 2))
                    .add("DamagePerPrimary",
                            (ini, s) -> s.damagePerPrimary = exactly(ini, "DamagePerPrimary", 2));

    /**
     * The next number in the file as a whole count of tenths or hundredths — exactly, or
     * not at all.
     *
     * <p>Read as a decimal rather than through a float: 0.15 has no float, and the
     * nearest one times a thousand is 149.99999. A figure with more places than its field
     * keeps is refused rather than rounded, so what the file says is what the game does.
     */
    private static int exactly(Ini ini, String field, int places) {
        var token = ini.getNextToken();
        try {
            return new java.math.BigDecimal(token.trim()).movePointRight(places).intValueExact();
        } catch (NumberFormatException | ArithmeticException wrong) {
            throw new IllegalArgumentException("dungeon.ini: " + field + " = " + token
                    + " is not a number with at most " + places + " decimal place"
                    + (places == 1 ? "" : "s"));
        }
    }

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

    /** The highest a room may stand. Zero is a dungeon on one level, as before. */
    public int maxStorey() {
        return maxStorey;
    }

    /** How often a corridor changes storey rather than running level, as a percentage. */
    public int storeyChangePercent() {
        return storeyChangePercent;
    }

    /** How far apart two storeys stand, in world units. */
    public float storeyHeight() {
        return storeyHeight;
    }

    /** How many cells of a corridor a stair takes up. */
    public int stairLength() {
        return stairLength;
    }

    /** Which storey the hero starts on, never above the dungeon's own ceiling. */
    public int entranceStorey() {
        return Math.min(entranceStorey, maxStorey);
    }

    /** Which storey the boss waits on — the top of the dungeon, by default. */
    public int bossStorey() {
        return Math.min(bossStorey, maxStorey);
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

    /** How far ahead a walking thing looks for a body; see {@code WayAhead}. */
    public float wayAheadProbe() {
        return wayAheadProbe;
    }

    /**
     * How much further aside each try turns when a monster that keeps its distance
     * backs away and straight back is stone.
     */
    public float retreatTurnDegrees() {
        return retreatTurnDegrees;
    }

    /** And how many tries it makes each side of straight back before it is cornered. */
    public int retreatTurns() {
        return retreatTurns;
    }

    /** How much further aside each try at a spot for what a monster calls up; see {@code Summoning}. */
    public float summonTurnDegrees() {
        return summonTurnDegrees;
    }

    /** And how many tries each way before it has run out of spots. */
    public int summonTurns() {
        return summonTurns;
    }

    public int heroRepathFrames() {
        return heroRepathFrames;
    }

    // ---- run loop ----

    public int respawnDelayFrames() {
        return respawnDelayFrames;
    }

    /**
     * How long the word stays up after the last boss falls.
     *
     * <p>Longer than a death's, and that is the whole of the difference in how
     * the two are handled: a death is an interruption and a win is an ending, and
     * an ending wants to be looked at.
     */
    public int victoryFrames() {
        return victoryFrames;
    }

    /** What the banner says when the run is lost, and when it is finished. */
    /** What the banner says as he goes down to {@code depth}. */
    public String nextDepthWord(int depth) {
        return nextDepthWord.replace("%d", Integer.toString(depth));
    }

    public String diedWord() {
        return diedWord;
    }

    public String wonWord() {
        return wonWord;
    }

    /**
     * How long the finished floor stays open after the boss falls.
     *
     * <p>Long enough to walk to what it left behind — a floor that closed in the
     * same frame took the boss's own drop away with it.
     */
    public int descendDelayFrames() {
        return descendDelayFrames;
    }

    // ---- leveling ----

    /** The progression rules, as one value the leveling code can be handed. */
    public Levelling levelling() {
        return new Levelling(maxLevel, xpBase, xpStep, armourPercentPerLevel,
                minDamageTakenPercent);
    }

    /** What a point of each attribute is worth, to every hero alike. */
    public AttributeRules attributeRules() {
        return new AttributeRules(healthPerStrength, speedPerAgility, manaPerIntelligence,
                damagePerPrimary);
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
