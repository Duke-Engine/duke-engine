package uz.duke.dungeon.content;

import java.util.List;
import java.util.Map;
import uz.duke.core.ini.FieldParseTable;
import uz.duke.core.ini.Ini;
import uz.duke.core.thing.ThingTemplateLoader;
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
 * Every tuning number that is not a unit stat: the {@code World} block of {@code dungeon.ini}, one
 * section per concern, and the dungeon's part of the unit, skill, effect and sound blocks in the
 * files it lists.
 *
 * <p>Unit stats belong in the unit blocks, where the engine's template
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

    private String worldName = "Dungeon";

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
    // The World block's LevelHeight, which the engine lays every map at.
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

    // ---- the attributes, in the file's order ----

    private final java.util.List<AttributeBlock> attributes = new java.util.ArrayList<>();
    /** What a point of a hero's primary adds to his blow, in hundredths. */
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
     * One boss per depth, in order, from {@code Depth = Descent}.
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
        return parse(Content.world(), Content.data());
    }

    /**
     * Settings from the World block's text, with everything else as shipped — the seam a test
     * uses to prove that changing the file changes the game, without a rebuild.
     */
    public static DungeonSettings parse(String worldText) {
        return parse(worldText, "");
    }

    /**
     * Settings from the World block's text and the text of {@code .duke} files: a test's own
     * monsters, heroes, projectiles, props, effects and sounds, each overriding the shipped one of
     * its name — see {@link #fillInMissingMonsters} — and leaving every other alone.
     */
    public static DungeonSettings parse(String worldText, String data) {
        var settings = new DungeonSettings();
        Ini.of(worldText, Map.of(
                // The world: one block, and every setting of it a section inside, headed
                // `Generation = Layout` and closed by an End of its own. See WORLD.
                "World", reader -> {
                    settings.worldName = reader.getNextToken();
                    reader.initFromIni(settings, WORLD);
                })).load();
        settings.read(Content.records(data, "data"));
        if (!readingShippedFile) {
            settings.fillInMissingMonsters();
            settings.fillInMissingSkills();
            settings.fillInMissingLoot();
            settings.fillInMissingAttributes();
            // A block that re-tunes a monster and says nothing of its skill keeps the shipped
            // one, as every other skill is kept -- and so keeps casting it.
            settings.monsters.replaceAll(kind -> kind.hasSkill() ? kind : settings.skillsFor(kind.name())
                    .stream().findFirst().map(skill -> kind.casting(skill.key())).orElse(kind));
        }
        settings.validate();
        return settings;
    }

    /**
     * Everything the data files hold besides templates' engine parts, each by what it is. A unit's
     * skills are its own, written inside it, and its framing is its own face.
     */
    private void read(List<Record> records) {
        for (var record : records) {
            switch (record) {
                case Monster monster -> {
                    monsters.add(monster.kind());
                    own(monster.name(), monster.portrait(), monster.skills());
                }
                case Hero hero -> {
                    heroes.add(hero);
                    own(hero.name(), hero.portrait(), hero.skills());
                }
                case Projectile projectile -> projectiles.add(projectile);
                case Prop prop -> props.add(prop);
                case Effect effect -> effects.add(effect);
                case Sound sound -> sounds.add(sound);
                case HeavyShot shot -> heavyShot = shot;
                default -> {
                    // an Object, which is a world's to build and has nothing of the settings'
                }
            }
        }
    }

    private void own(String unit, PortraitArt portrait, List<Skill> itsSkills) {
        if (portrait != null) {
            portraits.add(portrait.named(unit));
        }
        for (var skill : itsSkills) {
            skills.add(skill.ownedBy(unit));
        }
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

    /**
     * The same rule again, keyed by the attribute's name.
     *
     * <p>Order matters more here than anywhere: it is the order a hero's attributes are
     * held in and the order the panel draws them, so an attribute a file re-tunes keeps
     * its shipped place.
     */
    private void fillInMissingAttributes() {
        var declared = new java.util.ArrayList<>(attributes);
        attributes.clear();
        for (var shipped : shippedAttributes()) {
            var override = declared.stream()
                    .filter(attribute -> attribute.rule().name().equals(shipped.rule().name()))
                    .findFirst();
            attributes.add(override.orElse(shipped));
            override.ifPresent(declared::remove);
        }
        attributes.addAll(declared); // attributes this file invented
    }

    private static final List<MonsterKind> SHIPPED_MONSTERS = new java.util.ArrayList<>();
    private static final List<Skill> SHIPPED_SKILLS = new java.util.ArrayList<>();
    private static final List<Loot> SHIPPED_LOOT = new java.util.ArrayList<>();
    private static final List<AttributeBlock> SHIPPED_ATTRIBUTES = new java.util.ArrayList<>();
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

    private static List<AttributeBlock> shippedAttributes() {
        readShippedFile();
        return SHIPPED_ATTRIBUTES;
    }

    private static void readShippedFile() {
        if (!SHIPPED_MONSTERS.isEmpty() || readingShippedFile) {
            return;
        }
        readingShippedFile = true;
        try {
            var shipped = parse(Content.world(), Content.data());
            SHIPPED_MONSTERS.addAll(shipped.monsters);
            SHIPPED_SKILLS.addAll(shipped.skills);
            SHIPPED_LOOT.addAll(shipped.loot);
            SHIPPED_ATTRIBUTES.addAll(shipped.attributes);
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
            var name = "Monster " + kind.name();
            if (kind.hasSkill()) {
                var skill = skillsFor(kind.name()).stream()
                        .filter(one -> one.key() == kind.skillKey()).findFirst().orElse(null);
                require(skill != null, name + " casts " + kind.skillKey() + ", and no Skill inside it"
                        + " says what that is");
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
                    "BossGuards names " + guard.kind() + ", and no Monster block describes it");
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
        require(storeyHeight >= 0f, "LevelHeight cannot be negative");
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
        validateAttributes();
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
            require(!moment.effect.isBlank(), "Moment " + moment.name + " plays no Effect");
            require(moment.scale > 0f, "Moment " + moment.name + " has to be drawn at some size");
        }
        for (var skill : skills) {
            // The panel is told which picture to draw down the status line, and
            // the line is split on those two characters.
            require(sayable(skill.icon()),
                    "a skill's Icon may not contain ',' or '|': " + skill.key());
            switch (skill.effect()){
                case HEAL -> {
                    var name = skill.heroTemplate() + "'s Skill " + skill.key();
                    require(skill.heal() > 0f && skill.range() > 0f, name + " mends nobody: it needs a Heal and a Range");
                    require(skill.healBelowPercent() > 0 && skill.healBelowPercent() <= 100,
                            name + "'s HealBelowPercent is a share of health, from 1 to 100");
                    require(skill.hasProjectile(), name + " has no light to call down: name it in Projectile");
                }
                case SUMMON -> {
                    var name = skill.heroTemplate() + "'s Skill " + skill.key();
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
    }

    /**
     * The attributes, and everything that names one: a hero's lines and his primary, and
     * an item that gives one.
     *
     * <p>Names are checked here rather than where they are read, because a hero's block
     * may come before the attribute it names, and a partial file's attributes are only
     * all there once the shipped ones have been filled in.
     */
    private void validateAttributes() {
        require(damagePerPrimary >= 0,
                "Attributes: a point of a primary cannot take something away");
        var rules = attributeRules();
        for (int i = 0; i < attributes.size(); i++) {
            var block = attributes.get(i);
            var name = "Attribute " + block.rule().name();
            require(block.rule().healthPerPoint() >= 0 && block.rule().speedPerPoint() >= 0
                            && block.rule().manaPerPoint() >= 0,
                    name + ": a point of it cannot take something away");
            // Both are fields on the status line, which splits on these two.
            require(sayable(block.word()) && sayable(block.icon()),
                    name + ": its Word and Icon may not contain ',' or '|'");
            for (int j = 0; j < i; j++) {
                var earlier = attributes.get(j).rule();
                require(!earlier.isNamed(block.rule().name())
                                && !earlier.isNamed(block.rule().shortName()),
                        name + " is called what Attribute " + earlier.name()
                                + " already is, and a hero could not say which he means");
            }
        }
        for (var hero : heroes) {
            var name = "Hero " + hero.name();
            var named = new java.util.HashSet<Integer>();
            for (var attribute : hero.attributes().entrySet()) {
                int at = rules.indexOf(attribute.getKey());
                require(at >= 0, name + " has " + attribute.getKey()
                        + ", and no Attribute is called that");
                require(named.add(at), name + " names " + attribute.getKey() + " twice");
                require(attribute.getValue().base().value() >= 0 && attribute.getValue().perLevel().value() >= 0,
                        name + ": an attribute cannot be negative, and a level cannot take one away");
            }
            require(hero.primary() == null || rules.indexOf(hero.primary()) >= 0,
                    name + "'s Primary is " + hero.primary()
                            + ", and no Attribute is called that");
            require(hero.primary() != null || hero.attributes().isEmpty(),
                    name + " has attributes and no Primary: say which of them he hits with");
            require(hero.manaRegen() >= 0 && hero.healthRegen() >= 0,
                    name + ": what comes back on its own cannot be negative");
        }
        for (var item : loot) {
            var name = "LootItem " + item.id();
            if (item.kind() == LootKind.ATTRIBUTE) {
                require(rules.indexOf(item.attribute()) >= 0, name + " gives "
                        + item.attribute() + ", and no Attribute is called that");
            } else {
                require(item.attribute().isEmpty(),
                        name + ": Attribute only means something on an item of Kind = ATTRIBUTE");
            }
        }
        var block = statBlock;
        require(block.figureIcon > 0f && block.primaryIcon > 0f && block.attributeIcon > 0f,
                "StatBlock: a socket has to have a size");
        require(block.iconShare > 0f && block.iconShare <= 1f,
                "StatBlock: IconShare is a share of a socket");
        require(block.figureRows >= 1 && block.attributeRows >= 1,
                "StatBlock: the block keeps room for at least one row of each");
        require(block.rowGap >= 0f && block.gapUnderBar >= 0f,
                "StatBlock: a gap cannot be negative");
        // Each is a field on the status line, which splits on these two.
        require(sayable(hudHealthWord) && sayable(hudSpeedNowWord),
                "HealthWord and SpeedNowWord may not contain ',' or '|'");
        require(hudPrimaryWord.indexOf('|') < 0 && hudEachPointWord.indexOf('|') < 0,
                "PrimaryWord and EachPointWord may not contain '|'");
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
                    .add("StairLength", Ini.integer((s, v) -> s.stairLength = v))
                    .add("EntranceStorey", Ini.integer((s, v) -> s.entranceStorey = v))
                    .add("BossStorey", Ini.integer((s, v) -> s.bossStorey = v));

    // ---- what the data files hold, each block a record ----

    private final java.util.List<Hero> heroes = new java.util.ArrayList<>();
    private final java.util.List<PortraitArt> portraits = new java.util.ArrayList<>();
    private final java.util.List<Projectile> projectiles = new java.util.ArrayList<>();
    private final java.util.List<Prop> props = new java.util.ArrayList<>();
    private final java.util.List<Effect> effects = new java.util.ArrayList<>();
    private final java.util.List<Sound> sounds = new java.util.ArrayList<>();
    private HeavyShot heavyShot = HeavyShot.DEFAULTS;

    /**
     * What each hero the files describe is drawn as, in the order they name them.
     *
     * <p>A list because the roster is the files'. Empty when they name none, and then whoever
     * is playing is a coloured shape, as he was before there was a model.
     */
    public java.util.List<HeroLook> heroes() {
        var rules = attributeRules();
        return heroes.stream().map(hero -> hero.look(rules)).toList();
    }

    /** That hero's block, or {@link HeroLook#NONE} if no file describes such a one. */
    public HeroLook heroNamed(String templateName) {
        for (var hero : heroes) {
            if (hero.name().equals(templateName)) {
                return hero.look(attributeRules());
            }
        }
        return HeroLook.NONE;
    }

    /** Every live portrait a unit frames for itself, by the creature it is the face of. */
    public java.util.List<PortraitArt> portraits() {
        return java.util.List.copyOf(portraits);
    }

    /** Every projectile the files describe, in the order they describe them. */
    public java.util.List<ArrowLook> projectiles() {
        return projectiles.stream().map(Projectile::look).toList();
    }

    /** How one is drawn, or a plain shape when no file describes such a thing. */
    public ArrowLook projectile(String template) {
        for (var projectile : projectiles) {
            if (projectile.name().equals(template)) {
                return projectile.look();
            }
        }
        return new ArrowLook(template, null, null, 1f, 90f, 0f, 0xFFFFFF, null, 0f);
    }

    /** The creature a drawn shot becomes — the same shaft, drawn bigger. */
    public String heavyArrowTemplate() {
        return heavyShot.template();
    }

    /**
     * Slower than an ordinary arrow, on purpose. It is the one shot the player
     * chose to spend, so it is the one worth watching cross the room.
     */
    public float heavyArrowSpeed() {
        return heavyShot.speed();
    }

    /**
     * One prop by its template's name. One the files do not describe is one they never
     * scatter, which is what a weight of nothing says.
     */
    public PropKind prop(String template) {
        for (var prop : props) {
            if (prop.name().equals(template)) {
                return prop.kind();
            }
        }
        return new PropKind(template, 0);
    }

    /** What may be scattered through the rooms, in file order. */
    public java.util.List<PropKind> propKinds() {
        return props.stream().map(Prop::kind).toList();
    }

    /** Every effect the files describe, in the order they describe them. */
    public java.util.List<Effect> effects() {
        return java.util.List.copyOf(effects);
    }

    /**
     * Every layer an effect is drawn from, in the order written — which is also the order they
     * are drawn in, one over another.
     */
    public java.util.List<EffectLayerArt> effectLayers() {
        return effects.stream()
                .flatMap(effect -> effect.layers().stream()
                        .map(layer -> new EffectLayerArt(effect.name(), layer.name(), layer.fields())))
                .toList();
    }

    /** Every moment the game has a sound for. */
    public java.util.List<Sound> sounds() {
        return java.util.List.copyOf(sounds);
    }

    // ---- what the game sounds like ----

    private float voiceGapSeconds = 1.5f;

    private static final FieldParseTable<DungeonSettings> SOUNDS =
            new FieldParseTable<DungeonSettings>()
                    .add("VoiceGapSeconds", Ini.real((s, v) -> s.voiceGapSeconds = v));

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

    /** Accumulates one {@code Monster} block. */
    // ---- themes ----

    private final java.util.List<ThemeBuilder> themes = new java.util.ArrayList<>();
    private final java.util.List<ToneBuilder> tones = new java.util.ArrayList<>();
    private final java.util.List<ThemeMonsterBuilder> themeMonsters = new java.util.ArrayList<>();
    private final java.util.List<String> themeOrder = new java.util.ArrayList<>();
    private Themes.WhenExhausted whenExhausted = Themes.WhenExhausted.REPEAT;

    /** One kind of thing that stands about in a room, and how often it is drawn. */
    public record PropKind(String template, int weight) {
    }

    private int minPropsPerRoom;
    private int maxPropsPerRoom = 3;

    private static final FieldParseTable<DungeonSettings> PROPS =
            new FieldParseTable<DungeonSettings>()
                    .add("MinPerRoom", Ini.integer((s, v) -> s.minPropsPerRoom = v))
                    .add("MaxPerRoom", Ini.integer((s, v) -> s.maxPropsPerRoom = v));

    public int minPropsPerRoom() {
        return minPropsPerRoom;
    }

    public int maxPropsPerRoom() {
        return maxPropsPerRoom;
    }

    private static final class ThemeBuilder {
        private final String name;
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
        final LookBuilder art = new LookBuilder();
        String animationsFrom;
        String death;

        ThemeMonsterBuilder(String theme, String template) {
            this.theme = theme;
            this.template = template;
        }
    }

    /** What a themed creature is drawn as instead, which is a monster's look and nothing else. */
    private static final class LookBuilder {
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

        MonsterLook look() {
            return new MonsterLook(model, texture, modelScale, tint, facing, idle, walk, attack, hurt,
                    new Held(holds, heldIn, heldScale, 0f, 0f, 0f, 0f, 0f, 0f), effect);
        }
    }

    private static final FieldParseTable<ThemeBuilder> THEME =
            new FieldParseTable<ThemeBuilder>()
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
            built.add(new ThemeArt(theme.name, theme.tileSize,
                    theme.wallTileSize, theme.wallHeight, theme.wallLift, theme.wallShift,
                    theme.ownMaterials, theme.stairs, theme.rockFace,
                    theme.capTint, theme.storeyShadePercent, theme.fogTint,
                    new ThemeArt.Standing(theme.wallFillsRock, theme.wallClump,
                            theme.wallSpread, theme.wallVariety),
                    itsTones, itsMonsters));
        }
        return new Themes(themeOrder, whenExhausted, built);
    }

    /** Accumulates one {@code LootItem = <id>} section of the World block. */
    private static final class LootBuilder {
        private final String id;
        String name;
        String icon = "";
        LootKind kind = LootKind.ATTACK;
        int value;
        int weight = 10;
        int minDepth = 1;
        String attribute = "";

        LootBuilder(String id) {
            this.id = id;
            this.name = id;
        }

        Loot build() {
            return new Loot(id, name, icon, kind, value, weight, minDepth, attribute);
        }
    }

    private static final FieldParseTable<LootBuilder> LOOT =
            new FieldParseTable<LootBuilder>()
                    .add("Name", Ini.restOfLine((l, v) -> l.name = v))
                    .add("Icon", Ini.string((l, v) -> l.icon = v))
                    .add("Kind", Ini.enumeration(LootKind.class, (l, v) -> l.kind = v))
                    .add("Value", Ini.integer((l, v) -> l.value = v))
                    .add("Weight", Ini.integer((l, v) -> l.weight = v))
                    .add("MinDepth", Ini.integer((l, v) -> l.minDepth = v))
                    // Which attribute a Kind = ATTRIBUTE item gives, by the name a
                    // hero's block uses for it.
                    .add("Attribute", Ini.string((l, v) -> l.attribute = v));

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
        return new TileArt(tileFloor, tileWall, tileCorner,
                tileStairs, tileSize, tileWallHeight, tileWallLift, tileWallShift);
    }

    private static final FieldParseTable<DungeonSettings> TILES =
            new FieldParseTable<DungeonSettings>()
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

    /** The one being played, which is what the run spawns and the panel describes. */
    public HeroLook playedHeroLook() {
        return heroNamed(playedHero);
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


    /**
     * What an arrow is drawn as: one named mesh out of a model file, or nothing,
     * in which case it falls back to a coloured shape.
     *
     * @param part the name <em>inside</em> the file, which need not be a sensible
     *             one — see the Projectile blocks in {@code data/projectiles/}
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

        SkinLook look() {
            return new SkinLook(name, texture, inset, scale, tint);
        }
    }

    /** Every painted edge the file describes. */
    public java.util.List<SkinLook> skin() {
        return skin.stream().map(SkinBuilder::look).toList();
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

        CursorLook look() {
            return new CursorLook(name, image, hotX, hotY, tint);
        }
    }

    /** Every pointer the file describes. */
    public java.util.List<CursorLook> cursors() {
        return cursors.stream().map(CursorBuilder::look).toList();
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
     * @param effect the effect this is a layer of — an {@code Effect} name
     * @param name   what this layer is called, for whoever reads the file
     * @param fields what it said, by the client's name for each
     */
    public record EffectLayerArt(String effect, String name, java.util.Map<String, String> fields) {

        public EffectLayerArt {
            fields = java.util.Map.copyOf(fields);
        }
    }

    /**
     * What one of the run's own moments looks like: a level gained, the boss down,
     * the hero arriving on a floor. The client notices the moment; this says which
     * recipe it plays on him.
     *
     * @param name   which moment, in the client's word for it
     * @param effect the recipe it plays, an {@code Effect} drawn in layers
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
                    // How it feels rather than what it costs: every knock of the camera
                    // at once, and the flash a creature gives when it is hit.
                    .add("ShakeScale", Ini.real((s, v) -> s.shakeScale = v))
                    .add("HitFlashColour", (ini, s) -> s.hitFlashColour = Integer.decode(ini.getNextToken()))
                    .add("HitFlashSeconds", Ini.real((s, v) -> s.hitFlashSeconds = v))
                    .add("HitFlashStrength", Ini.real((s, v) -> s.hitFlashStrength = v))
                    .add("StrikeWithin", Ini.real((s, v) -> s.strikeWithin = v));

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
    private String hudHealthWord = "";
    private String hudPrimaryWord = "";
    private String hudEachPointWord = "";
    private String hudSpeedNowWord = "";

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

    /**
     * What the figures beside the attributes are called: attack and armour on a hero's
     * card, attack and speed on a creature's. Speed is also the row on an attribute's card.
     */
    public String hudAttackWord() {
        return hudAttackWord;
    }

    public String hudArmourWord() {
        return hudArmourWord;
    }

    public String hudSpeedWord() {
        return hudSpeedWord;
    }

    /**
     * How each attribute is shown — its word and its picture — in the order the
     * file lists them, which is the order a hero's attributes are held in.
     */
    public java.util.List<AttributeArt> attributeArt() {
        return attributes.stream()
                .map(block -> new AttributeArt(block.rule().name(), block.rule().shortName(),
                        block.word(), block.icon()))
                .toList();
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

    /**
     * What his speed is called on the card of an attribute that gives speed.
     *
     * <p>A hero's speed is not a figure beside his attributes: it is what one of them
     * became, so it is read where that one is explained.
     */
    public String hudSpeedNowWord() {
        return hudSpeedNowWord;
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

    private String hudManaWord = "";

    /** What a skill's price is called on its card, and the bar it comes out of. */
    public String hudManaWord() {
        return hudManaWord;
    }

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
        return java.util.List.of(hudMoveIcon, hudAttackOrderIcon, hudStopIcon, hudGuardIcon);
    }

    /**
     * The pictures beside the four figures under the bars, in their own order.
     *
     * <p>Beside the figures rather than counted off against them: the panel used
     * to choose by position out of a list it held itself, which made the client
     * the one deciding that a game's third figure is a lightning bolt.
     */
    public java.util.List<String> hudStatIcons() {
        return java.util.List.of(hudAttackStatIcon, hudArmourStatIcon, hudSpeedStatIcon);
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
                    .add("HealthWord", Ini.restOfLine((s, v) -> s.hudHealthWord = v))
                    .add("PrimaryWord", Ini.restOfLine((s, v) -> s.hudPrimaryWord = v))
                    .add("EachPointWord", Ini.restOfLine((s, v) -> s.hudEachPointWord = v))
                    .add("SpeedNowWord", Ini.restOfLine((s, v) -> s.hudSpeedNowWord = v))
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
                    .add("PaintedSkillIcons",
                            Ini.bool((s, v) -> s.hudPaintedSkillIcons = v))
                    .add("CmdMoveIcon", Ini.string((s, v) -> s.hudMoveIcon = v))
                    .add("CmdAttackIcon", Ini.string((s, v) -> s.hudAttackOrderIcon = v))
                    .add("CmdStopIcon", Ini.string((s, v) -> s.hudStopIcon = v))
                    .add("CmdGuardIcon", Ini.string((s, v) -> s.hudGuardIcon = v))
                    .add("AttackIcon", Ini.string((s, v) -> s.hudAttackStatIcon = v))
                    .add("ArmourIcon", Ini.string((s, v) -> s.hudArmourStatIcon = v))
                    .add("SpeedIcon", Ini.string((s, v) -> s.hudSpeedStatIcon = v))
                    // Panel-wide rather than per-hero: what a portrait costs is a
                    // fact about the machine drawing it, not about whose face is in
                    // it. See Portraits, and the Portrait... lines of a unit block, for the faces.
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
                    .add("DamagePerPrimary",
                            (ini, s) -> s.damagePerPrimary = exactly(ini, "DamagePerPrimary", 2));

    /** Accumulates one {@code Attribute = <name>} section of the World block. */
    private static final class AttributeBuilder {
        private final String name;
        String shortName;
        String word = "";
        String icon = "";
        int healthPerPoint;
        int speedPerPoint;
        int manaPerPoint;

        AttributeBuilder(String name) {
            this.name = name;
            this.shortName = name;
        }

        AttributeBlock build() {
            return new AttributeBlock(
                    new Attribute(name, shortName, healthPerPoint, speedPerPoint, manaPerPoint),
                    word.isBlank() ? name : word, icon);
        }
    }

    /** One attribute as the file describes it: what a point is worth, and how it is shown. */
    private record AttributeBlock(Attribute rule, String word, String icon) {
    }

    private static final FieldParseTable<AttributeBuilder> ATTRIBUTE =
            new FieldParseTable<AttributeBuilder>()
                    // How a hero's block names it. The block's own name does as well.
                    .add("Short", Ini.string((a, v) -> a.shortName = v))
                    // What the panel calls it, and the picture beside it.
                    .add("Word", Ini.restOfLine((a, v) -> a.word = v))
                    .add("Icon", Ini.string((a, v) -> a.icon = v))
                    // What a point of it adds, in exact hundredths. A figure the block
                    // leaves out, it adds none of.
                    .add("HealthPerPoint",
                            (ini, a) -> a.healthPerPoint = exactly(ini, "HealthPerPoint", 2))
                    .add("SpeedPerPoint",
                            (ini, a) -> a.speedPerPoint = exactly(ini, "SpeedPerPoint", 2))
                    .add("ManaPerPoint",
                            (ini, a) -> a.manaPerPoint = exactly(ini, "ManaPerPoint", 2));

    // ---- the block under the experience bar ----

    private final StatBlockBuilder statBlock = new StatBlockBuilder();

    /** How the block of figures and attributes under the hero's experience bar is drawn. */
    public StatBlockArt statBlockArt() {
        return statBlock.build();
    }

    /** Accumulates the {@code StatBlock} section; a file that leaves a line out gets these. */
    private static final class StatBlockBuilder {
        float figureIcon = 30f;
        float primaryIcon = 44f;
        float attributeIcon = 24f;
        float iconShare = 0.8f;
        float rowGap = 2f;
        float gapUnderBar = 6f;
        float figureColumn = 150f;
        float primaryColumn = 74f;
        int figureRows = 2;
        int attributeRows = 3;
        float figureText = 12f;
        float attributeText = 11f;
        float primaryText = 14f;
        int labelColour = 0x8B8171;
        int valueColour = 0xD9CFBA;
        int primaryColour = 0xF0D48A;
        int attributeColour = 0xC9A24B;
        int gainColour = 0x7FBF6A;
        int frameColour = 0xF0D48A;
        int figureTint = 0xC9A24B;
        int primaryTint = 0xFFFFFF;
        int attributeTint = 0xC9A24B;

        StatBlockArt build() {
            return new StatBlockArt(figureIcon, primaryIcon, attributeIcon, iconShare, rowGap,
                    gapUnderBar, figureColumn, primaryColumn, figureRows, attributeRows,
                    figureText, attributeText, primaryText, labelColour, valueColour,
                    primaryColour, attributeColour, gainColour, frameColour, figureTint,
                    primaryTint, attributeTint);
        }
    }

    /** A colour written the way every other colour in the file is: {@code 0xF0D48A}. */
    private static <T> uz.duke.core.ini.FieldParser<T> colour(
            java.util.function.ObjIntConsumer<T> setter) {
        return (ini, instance) -> setter.accept(instance, Integer.decode(ini.getNextToken()));
    }

    private static final FieldParseTable<StatBlockBuilder> STAT_BLOCK =
            new FieldParseTable<StatBlockBuilder>()
                    .add("FigureIcon", Ini.real((b, v) -> b.figureIcon = v))
                    .add("PrimaryIcon", Ini.real((b, v) -> b.primaryIcon = v))
                    .add("AttributeIcon", Ini.real((b, v) -> b.attributeIcon = v))
                    .add("IconShare", Ini.real((b, v) -> b.iconShare = v))
                    .add("RowGap", Ini.real((b, v) -> b.rowGap = v))
                    .add("GapUnderBar", Ini.real((b, v) -> b.gapUnderBar = v))
                    .add("FigureColumn", Ini.real((b, v) -> b.figureColumn = v))
                    .add("PrimaryColumn", Ini.real((b, v) -> b.primaryColumn = v))
                    .add("FigureRows", Ini.integer((b, v) -> b.figureRows = v))
                    .add("AttributeRows", Ini.integer((b, v) -> b.attributeRows = v))
                    .add("FigureText", Ini.real((b, v) -> b.figureText = v))
                    .add("AttributeText", Ini.real((b, v) -> b.attributeText = v))
                    .add("PrimaryText", Ini.real((b, v) -> b.primaryText = v))
                    .add("LabelColour", colour((b, v) -> b.labelColour = v))
                    .add("ValueColour", colour((b, v) -> b.valueColour = v))
                    .add("PrimaryColour", colour((b, v) -> b.primaryColour = v))
                    .add("AttributeColour", colour((b, v) -> b.attributeColour = v))
                    .add("GainColour", colour((b, v) -> b.gainColour = v))
                    .add("FrameColour", colour((b, v) -> b.frameColour = v))
                    .add("FigureTint", colour((b, v) -> b.figureTint = v))
                    .add("PrimaryTint", colour((b, v) -> b.primaryTint = v))
                    .add("AttributeTint", colour((b, v) -> b.attributeTint = v));

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

    /** The World block as the engine reads it: its name, and how tall a storey stands. */
    public DungeonWorld world() {
        return new DungeonWorld(worldName, storeyHeight);
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

    /**
     * Every attribute the file describes, in its order, and what a point of each is
     * worth — to every hero alike.
     */
    public AttributeRules attributeRules() {
        return new AttributeRules(attributes.stream().map(AttributeBlock::rule).toList(),
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



    // The World block: how tall a storey stands, which the engine reads, and every section
    // of the dungeon's own. A section the world has one of reads into the settings and its
    // name only labels it; a repeatable one is named by what it describes. Declared after
    // every table a section is read with.
    private static final FieldParseTable<DungeonSettings> WORLD =
            new FieldParseTable<DungeonSettings>()
                    .add("LevelHeight", Ini.real((s, v) -> s.storeyHeight = v))
                    .add("Generation", Ini.section(LAYOUT))
                    .add("Combat", Ini.section(BEHAVIOUR))
                    .add("Run", Ini.section(RUN))
                    // Which stage this build opens on, if it opens on one at all. A
                    // blank section is the endless dungeon, which is what the game is
                    // when nobody has said otherwise.
                    .add("Stage", Ini.section(STAGE))
                    .add("Leveling", Ini.section(LEVELLING))
                    // What a point of a hero's primary adds to his blow. What a point of
                    // each attribute is worth otherwise is that attribute's own section,
                    // and which of them each hero has is his Hero block.
                    .add("Attributes", Ini.section(ATTRIBUTES))
                    // Repeatable, headed by the attribute's name: the list of attributes
                    // is the file's, in its order, so another one is a section here, a
                    // line in each hero and a picture -- and no Java.
                    .add("Attribute", (reader, s) -> {
                        var attribute = new AttributeBuilder(reader.getNextToken());
                        reader.initFromIni(attribute, ATTRIBUTE);
                        s.attributes.add(attribute.build());
                    })
                    // How the block of figures and attributes under the hero's experience
                    // bar is drawn. Sizes and colours only: what is in it is the line's.
                    .add("StatBlock", Ini.section(STAT_BLOCK.<DungeonSettings>on(s -> s.statBlock)))
                    .add("Depth", Ini.section(DEPTH))
                    .add("Tiles", Ini.section(TILES))
                    .add("Animations", Ini.section(ANIMATIONS))
                    // And the face every selectable creature gets, which is what makes
                    // the whole bestiary a section rather than a block each: the camera
                    // is written in fractions of whatever it is looking at, and a
                    // creature's own idle and death are already bound on it.
                    .add("Portraits", (reader, s) -> {
                        reader.getNextToken();
                        s.portraitsDeclared = true;
                        reader.initFromIni(s.everyPortrait, PORTRAIT);
                    })
                    // What the mouse pointer looks like in one situation. Named by
                    // the situation, because the client owns those and the game owns
                    // the pictures -- see Cursors.
                    .add("Cursor", (reader, s) -> {
                        var pointer = new CursorBuilder(reader.getNextToken());
                        reader.initFromIni(pointer, CURSOR);
                        s.cursors.add(pointer);
                    })
                    // What the hero panel's edges are painted with. Named after the
                    // part of the panel it paints, and every one of them optional:
                    // a part nobody names keeps the carved look it always had.
                    .add("Skin", (reader, s) -> {
                        var piece = new SkinBuilder(reader.getNextToken());
                        reader.initFromIni(piece, SKIN);
                        s.skin.add(piece);
                    })
                    // What one of the run's own moments plays on the hero: a level, the
                    // boss down, a floor reached.
                    .add("Moment", (reader, s) -> {
                        var moment = new MomentBuilder(reader.getNextToken());
                        reader.initFromIni(moment, MOMENT);
                        s.moments.add(moment);
                    })
                    .add("Effects", Ini.section(EFFECT_BUDGET))
                    .add("Hud", Ini.section(HUD))
                    .add("UnitBar", Ini.section(UNIT_BAR))
                    .add("Menu", Ini.section(MENU))
                    // How a floor looks, and which floor looks like what. Repeatable
                    // and named, the same way monsters and skills are: a fourth theme
                    // is three more sections here and a folder of models.
                    .add("Theme", (reader, s) -> {
                        var theme = new ThemeBuilder(reader.getNextToken());
                        reader.initFromIni(theme, THEME);
                        s.themes.add(theme);
                    })
                    .add("Tone", (reader, s) -> {
                        var tone = new ToneBuilder(reader.getNextToken(), reader.getNextToken());
                        reader.initFromIni(tone, TONE);
                        s.tones.add(tone);
                    })
                    .add("ThemeMonster", (reader, s) -> {
                        var themed = new ThemeMonsterBuilder(reader.getNextToken(), reader.getNextToken());
                        reader.initFromIni(themed, THEME_MONSTER);
                        s.themeMonsters.add(themed);
                    })
                    .add("Themes", Ini.section(THEME_ORDER))
                    .add("Props", Ini.section(PROPS))
                    .add("Sounds", Ini.section(SOUNDS))
                    .add("Fog", Ini.section(FOG))
                    .add("Sun", Ini.section(SUN))
                    .add("Camera", Ini.section(CAMERA))
                    .add("OrderMark", Ini.section(ORDER_MARK))
                    .add("SkillRing", Ini.section(SKILL_RING))
                    .add("Loot", Ini.section(LOOT_RULES))
                    // Repeatable, headed by the item's id: a new thing to find is a
                    // section here and no Java.
                    .add("LootItem", (reader, s) -> {
                        var item = new LootBuilder(reader.getNextToken());
                        reader.initFromIni(item, LOOT);
                        s.loot.add(item.build());
                    });
}
