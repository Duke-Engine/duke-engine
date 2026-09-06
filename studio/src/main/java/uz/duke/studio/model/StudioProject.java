package uz.duke.studio.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Studio's document: everything a game project contains, serialized as
 * JSON. The engine never sees this — the Studio generates engine INI, Visuals
 * and a scenario from it (the project is the source of truth, engine data is a
 * build artifact).
 */
public final class StudioProject {

    /**
     * A faction — the top of the authoring flow (Rohan/Mordor, USA/China…).
     * Units belong to a faction; players play a faction and can only build and
     * place its units (plus shared ones with no faction).
     */
    public static final class FactionDef {
        public String name = "NewFaction";
        public String displayName = "";
        public String description = "";
        public String colorHex = "#888888";
        /**
         * What a player of this faction begins the match with, placed relative
         * to the map's start position — the RTS "starting base" (command
         * centre, first workers…). This is what makes a faction playable on
         * ANY map.
         */
        public List<StartingUnit> startingUnits = new ArrayList<>();

        public FactionDef() {
        }

        public FactionDef(String name, String displayName, String colorHex) {
            this.name = name;
            this.displayName = displayName;
            this.colorHex = colorHex;
        }
    }

    /** One unit of a faction's starting base, offset from the start position. */
    public static final class StartingUnit {
        public String unit;
        public float dx;
        public float dy;

        public StartingUnit() {
        }

        public StartingUnit(String unit, float dx, float dy) {
            this.unit = unit;
            this.dx = dx;
            this.dy = dy;
        }
    }

    /**
     * A playable map: terrain, up to {@code MAX_STARTS} start positions the
     * players spawn at, and neutral objects (resource piles, critters). Maps
     * carry no armies — factions bring their own starting units.
     */
    public static final class MapDef {
        public static final int MAX_STARTS = 8;

        public String name = "Main";
        public int cellsWide = 70;
        public int cellsHigh = 45;
        public List<String> blockedCells = new ArrayList<>();
        /** Start positions as [x, y] world units, in player order. */
        public List<float[]> startPositions = new ArrayList<>();
        /** Neutral objects on the map ({@link Placement#player} is ignored). */
        public List<Placement> neutrals = new ArrayList<>();

        public MapDef() {
        }

        public MapDef(String name, int cellsWide, int cellsHigh) {
            this.name = name;
            this.cellsWide = cellsWide;
            this.cellsHigh = cellsHigh;
        }
    }

    /** A user-written behaviour script (Java source extending UnitScript). */
    public static final class ScriptDef {
        public String name = "NewScript";
        public String source = "";

        public ScriptDef() {
        }

        public ScriptDef(String name, String source) {
            this.name = name;
            this.source = source;
        }
    }

    /** A unit/structure type being authored. */
    public static final class UnitDef {
        public String name = "NewUnit";
        public String displayName = "";
        /** The faction this unit belongs to; empty = shared by all factions. */
        public String faction = "";
        /** Names of custom scripts attached to this unit. */
        public List<String> scripts = new ArrayList<>();
        public boolean structure;
        public float maxHealth = 100;
        public int buildCost;
        public float buildTimeSeconds = 1;
        public float visionRange = 40;
        // capability type name -> (param key -> value)
        public Map<String, Map<String, String>> capabilities = new LinkedHashMap<>();
        // visuals (all optional)
        public String modelPath = "";
        public float modelScale = 1;
        public float modelYOffset;
        public float modelFacing;
        public String idleAnim = "";
        public String walkAnim = "";
        public String attackAnim = "";
        public String fireSound = "";
        public String dieSound = "";
    }

    /** A participant in the scenario. Team mates are allies; different teams are enemies. */
    public static final class PlayerDef {
        public String name = "Player";
        public String colorHex = "#5AAAFF";
        public int money = 1000;
        public int team = 1;
        /** Which faction this player plays; empty = may use any unit. */
        public String faction = "";

        public PlayerDef() {
        }

        public PlayerDef(String name, String colorHex, int money, int team) {
            this.name = name;
            this.colorHex = colorHex;
            this.money = money;
            this.team = team;
        }

        public PlayerDef(String name, String colorHex, int money, int team, String faction) {
            this(name, colorHex, money, team);
            this.faction = faction;
        }
    }

    /** One unit placed on the map (player is an index into {@link #players}). */
    public static final class Placement {
        public String unitName;
        public int player;
        public float x;
        public float y;

        public Placement() {
        }

        public Placement(String unitName, int player, float x, float y) {
            this.unitName = unitName;
            this.player = player;
            this.x = x;
            this.y = y;
        }
    }

    public String title = "My RTS";
    /** Shown under the title on the game's main menu. */
    public String menuSubtitle = "made with duke-engine";
    public List<MapDef> maps = new ArrayList<>();
    public List<FactionDef> factions = new ArrayList<>();
    public List<UnitDef> units = new ArrayList<>();
    /** Default skirmish setup (colors/money/teams); factions are chosen at play time. */
    public List<PlayerDef> players = new ArrayList<>();
    public List<ScriptDef> scripts = new ArrayList<>();
    public int localPlayer; // index into players

    // ---- legacy single-scenario fields, migrated by ensureIntegrity ----
    public int mapCellsWide;
    public int mapCellsHigh;
    public List<String> blockedCells = new ArrayList<>();
    public List<Placement> placements = new ArrayList<>();

    /** Repair nulls/dangling references after JSON load or edits. */
    public void ensureIntegrity() {
        if (menuSubtitle == null) {
            menuSubtitle = "";
        }
        if (factions == null) {
            factions = new ArrayList<>();
        }
        if (scripts == null) {
            scripts = new ArrayList<>();
        }
        if (maps == null) {
            maps = new ArrayList<>();
        }
        for (var faction : factions) {
            if (faction.startingUnits == null) {
                faction.startingUnits = new ArrayList<>();
            }
            faction.startingUnits.removeIf(s -> s.unit == null || findUnit(s.unit) == null);
        }
        for (var map : maps) {
            if (map.blockedCells == null) {
                map.blockedCells = new ArrayList<>();
            }
            if (map.startPositions == null) {
                map.startPositions = new ArrayList<>();
            }
            if (map.neutrals == null) {
                map.neutrals = new ArrayList<>();
            }
            map.neutrals.removeIf(n -> n.unitName == null || findUnit(n.unitName) == null);
        }
        migrateLegacyScenario();
        if (maps.isEmpty()) {
            maps.add(new MapDef());
        }
        for (var unit : units) {
            if (unit.faction == null || (!unit.faction.isEmpty() && findFaction(unit.faction) == null)) {
                unit.faction = "";
            }
            if (unit.scripts == null) {
                unit.scripts = new ArrayList<>();
            }
            unit.scripts.removeIf(name -> findScript(name) == null);
        }
        for (var player : players) {
            if (player.faction == null || (!player.faction.isEmpty() && findFaction(player.faction) == null)) {
                player.faction = "";
            }
        }
    }

    /**
     * Convert the old single-scenario format (one map + painted armies) into
     * the RTS model: the terrain becomes a map, each player's painted army
     * becomes their faction's starting base (offsets from the army's centre),
     * and the army centres become the map's start positions.
     */
    private void migrateLegacyScenario() {
        if (mapCellsWide <= 0 && placements.isEmpty()) {
            return; // nothing legacy to migrate
        }
        var map = new MapDef("Main", Math.max(10, mapCellsWide), Math.max(10, mapCellsHigh));
        map.blockedCells.addAll(blockedCells);

        for (int playerIndex = 0; playerIndex < players.size(); playerIndex++) {
            var owned = new ArrayList<Placement>();
            for (var placement : placements) {
                if (placement.player == playerIndex && findUnit(placement.unitName) != null) {
                    owned.add(placement);
                }
            }
            if (owned.isEmpty()) {
                continue;
            }
            float centerX = 0;
            float centerY = 0;
            for (var placement : owned) {
                centerX += placement.x;
                centerY += placement.y;
            }
            centerX /= owned.size();
            centerY /= owned.size();
            map.startPositions.add(new float[] {centerX, centerY});

            var faction = findFaction(players.get(playerIndex).faction);
            if (faction != null && faction.startingUnits.isEmpty()) {
                for (var placement : owned) {
                    faction.startingUnits.add(new StartingUnit(placement.unitName,
                            placement.x - centerX, placement.y - centerY));
                }
            }
        }
        maps.add(map);
        mapCellsWide = 0;
        mapCellsHigh = 0;
        blockedCells = new ArrayList<>();
        placements = new ArrayList<>();
    }

    public MapDef findMap(String name) {
        for (var map : maps) {
            if (map.name.equals(name)) {
                return map;
            }
        }
        return null;
    }

    public ScriptDef findScript(String name) {
        for (var script : scripts) {
            if (script.name.equals(name)) {
                return script;
            }
        }
        return null;
    }

    /** The source template a new script starts from. */
    public static String scriptTemplate(String name) {
        return """
                package game.scripts;

                import uz.duke.game.script.UnitScript;

                /**
                 * Custom behaviour for a unit. onUpdate runs 30x per game second on the
                 * simulation thread. Determinism rules: no wall clock, no Math.random(),
                 * no threads, no UI - or multiplayer/replays will desync.
                 */
                public class %s extends UnitScript {

                    @Override
                    public void onStart() {
                        // runs once, when the unit first updates
                    }

                    @Override
                    public void onUpdate() {
                        // example: stand guard - engage the nearest enemy in range
                        if (!isAttacking()) {
                            var enemy = findNearestEnemy(150);
                            if (enemy != null) {
                                attack(enemy);
                            }
                        }
                    }
                }
                """.formatted(name);
    }

    public FactionDef findFaction(String name) {
        for (var faction : factions) {
            if (faction.name.equals(name)) {
                return faction;
            }
        }
        return null;
    }

    /** Units a player of {@code factionName} may use: its own + shared ones. */
    public List<UnitDef> unitsOfFaction(String factionName) {
        var result = new ArrayList<UnitDef>();
        for (var unit : units) {
            if (unit.faction.isEmpty() || unit.faction.equals(factionName)) {
                result.add(unit);
            }
        }
        return result;
    }

    /**
     * A fresh project demonstrating the authoring flow: first factions, then
     * each faction's units, then players who play a faction.
     */
    public static StudioProject starter() {
        var project = new StudioProject();

        project.factions.add(new FactionDef("Rohan", "Rohan", "#5AAAFF"));
        project.factions.add(new FactionDef("Mordor", "Mordor", "#EB5540"));

        var archer = basicWarrior("ElfArcher", "Elf Archer", "Rohan");
        var attack = archer.capabilities.get(CapabilityType.ATTACK.name());
        attack.put("AttackRange", "35"); // archers shoot far
        attack.put("Damage", "8");
        project.units.add(archer);

        var orc = basicWarrior("Orc", "Orc Warrior", "Mordor");
        orc.maxHealth = 120; // orcs are tougher but must close in
        orc.capabilities.get(CapabilityType.ATTACK.name()).put("AttackRange", "6");
        orc.scripts.add("Berserker"); // custom code drives them
        project.units.add(orc);

        project.scripts.add(new ScriptDef("Berserker", """
                package game.scripts;

                import uz.duke.game.script.UnitScript;

                /** Hunts the nearest enemy anywhere on the map, relentlessly. */
                public class Berserker extends UnitScript {

                    @Override
                    public void onUpdate() {
                        var enemy = findNearestEnemy(100000);
                        if (enemy == null) {
                            return;
                        }
                        if (distanceTo(enemy) > 8 && !isMoving()) {
                            moveTo(enemy.getPosition().x(), enemy.getPosition().y());
                        }
                        if (!isAttacking()) {
                            attack(enemy);
                        }
                    }
                }
                """));

        // each faction gets a barracks that trains its warrior (Builds = the menu)
        project.units.add(barracks("RohanBarracks", "Rohan Barracks", "Rohan", "ElfArcher"));
        project.units.add(barracks("MordorPit", "Orc Pit", "Mordor", "Orc"));

        // the starting base every player of the faction begins with, on any map
        var rohan = project.findFaction("Rohan");
        rohan.startingUnits.add(new StartingUnit("RohanBarracks", 0, 0));
        rohan.startingUnits.add(new StartingUnit("ElfArcher", 20, -20));
        rohan.startingUnits.add(new StartingUnit("ElfArcher", 30, -10));
        var mordor = project.findFaction("Mordor");
        mordor.startingUnits.add(new StartingUnit("MordorPit", 0, 0));
        mordor.startingUnits.add(new StartingUnit("Orc", -25, 15));
        mordor.startingUnits.add(new StartingUnit("Orc", -15, 25));

        // one map with two start positions; players pick factions at play time
        var map = new MapDef("Green Plains", 70, 45);
        map.startPositions.add(new float[] {100, 340});
        map.startPositions.add(new float[] {560, 120});
        project.maps.add(map);

        project.players.add(new PlayerDef("Player 1", "#5AAAFF", 1500, 1, "Rohan"));
        project.players.add(new PlayerDef("Player 2", "#EB5540", 1500, 2, "Mordor"));
        return project;
    }

    private static UnitDef barracks(String name, String displayName, String faction, String builds) {
        var structure = new UnitDef();
        structure.name = name;
        structure.displayName = displayName;
        structure.faction = faction;
        structure.structure = true;
        structure.maxHealth = 600;
        structure.visionRange = 35;
        var produce = defaults(CapabilityType.PRODUCE);
        produce.put("Builds", builds);
        structure.capabilities.put(CapabilityType.PRODUCE.name(), produce);
        return structure;
    }

    private static UnitDef basicWarrior(String name, String displayName, String faction) {
        var unit = new UnitDef();
        unit.name = name;
        unit.displayName = displayName;
        unit.faction = faction;
        unit.maxHealth = 80;
        unit.buildCost = 100;
        unit.buildTimeSeconds = 1.5f;
        unit.capabilities.put(CapabilityType.MOVE.name(), defaults(CapabilityType.MOVE));
        unit.capabilities.put(CapabilityType.ATTACK.name(), defaults(CapabilityType.ATTACK));
        return unit;
    }

    public static Map<String, String> defaults(CapabilityType type) {
        var map = new LinkedHashMap<String, String>();
        for (var param : type.getParams()) {
            map.put(param.key(), param.defaultValue());
        }
        return map;
    }

    public UnitDef findUnit(String name) {
        for (var unit : units) {
            if (unit.name.equals(name)) {
                return unit;
            }
        }
        return null;
    }
}
