package uz.duke.studio.model;

import java.awt.Color;
import java.util.Map;
import uz.duke.client3d.Visuals;
import uz.duke.game.DukeGame;
import uz.duke.game.GamePlayer;
import uz.duke.game.script.ScriptModule;
import uz.duke.game.script.UnitScript;

/**
 * Turns a {@link StudioProject} into runnable engine artifacts: the INI the
 * engine loads, the {@link Visuals} bindings, and a fully configured
 * {@link DukeGame} — the same generation the Play button and the exporter use,
 * so "what you play is what you ship".
 */
public final class GameFactory {

    private GameFactory() {
    }

    /** Generate the engine INI for every unit in the project. */
    public static String toIni(StudioProject project) {
        var ini = new StringBuilder();
        for (var unit : project.units) {
            appendUnit(ini, unit);
        }
        return ini.toString();
    }

    private static void appendUnit(StringBuilder ini, StudioProject.UnitDef unit) {
        ini.append("Object ").append(unit.name).append('\n');
        if (!unit.displayName.isBlank()) {
            ini.append("  DisplayName = ").append(unit.displayName).append('\n');
        }
        ini.append("  KindOf = ").append(kindOf(unit)).append('\n');
        if (unit.buildCost > 0) {
            ini.append("  BuildCost = ").append(unit.buildCost).append('\n');
            ini.append("  BuildTime = ").append(unit.buildTimeSeconds).append('\n');
        }
        ini.append("  VisionRange = ").append(unit.visionRange).append('\n');
        ini.append("  Body = ActiveBody Tag\n");
        ini.append("    MaxHealth = ").append(unit.maxHealth).append('\n');
        ini.append("  End\n");

        appendCapability(ini, unit, CapabilityType.MOVE, "Update = MoveUpdate");
        appendCapability(ini, unit, CapabilityType.ATTACK, "Update = WeaponUpdate");
        appendCapability(ini, unit, CapabilityType.PRODUCE, "Update = ProductionUpdate");
        appendCapability(ini, unit, CapabilityType.POWER, "Update = PowerModule");
        appendCapability(ini, unit, CapabilityType.CAPACITY_GATE, "Behavior = CapacityGate");
        appendCapability(ini, unit, CapabilityType.EXPERIENCE, "Behavior = ExperienceModule");
        appendCapability(ini, unit, CapabilityType.AUTO_HEAL, "Update = AutoHealUpdate");
        appendCapability(ini, unit, CapabilityType.SUPPLY, "Behavior = SupplyModule");
        appendCapability(ini, unit, CapabilityType.HARVEST, "Update = HarvestUpdate");
        for (var script : unit.scripts) {
            ini.append("  Update = ").append(ScriptModule.TAG_PREFIX).append(script).append(" Tag\n");
            ini.append("  End\n");
        }
        ini.append("End\n");
    }

    private static void appendCapability(StringBuilder ini, StudioProject.UnitDef unit,
            CapabilityType type, String moduleHeader) {
        var params = unit.capabilities.get(type.name());
        if (params == null) {
            return;
        }
        ini.append("  ").append(moduleHeader).append(" Tag\n");
        for (var entry : params.entrySet()) {
            var value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (value.isEmpty() || isDefaultZero(entry.getKey(), value)) {
                continue;
            }
            ini.append("    ").append(entry.getKey()).append(" = ").append(value).append('\n');
        }
        ini.append("  End\n");
    }

    /** Omit zero-valued optional numerics so the INI stays clean. */
    private static boolean isDefaultZero(String key, String value) {
        return (key.equals("SplashRadius") || key.equals("TurnRate")
                || key.equals("Produces") || key.equals("Consumes"))
                && value.equals("0");
    }

    private static String kindOf(StudioProject.UnitDef unit) {
        var kinds = new StringBuilder(unit.structure ? "STRUCTURE" : "INFANTRY");
        kinds.append(" SELECTABLE");
        if (unit.capabilities.containsKey(CapabilityType.ATTACK.name())) {
            kinds.append(" CAN_ATTACK");
        }
        if (unit.capabilities.containsKey(CapabilityType.POWER.name())) {
            kinds.append(" POWERED");
        }
        return kinds.toString();
    }

    /** Build the {@link Visuals} bindings from the project's asset fields. */
    public static Visuals toVisuals(StudioProject project) {
        var visuals = Visuals.create();
        for (var unit : project.units) {
            if (unit.modelPath.isBlank() && unit.fireSound.isBlank() && unit.dieSound.isBlank()) {
                continue;
            }
            visuals.unit(unit.name, v -> {
                if (!unit.modelPath.isBlank()) {
                    v.model(unit.modelPath).scale(unit.modelScale)
                            .yOffset(unit.modelYOffset).facing(unit.modelFacing);
                    if (!unit.idleAnim.isBlank()) {
                        v.idle(unit.idleAnim);
                    }
                    if (!unit.walkAnim.isBlank()) {
                        v.walk(unit.walkAnim);
                    }
                    if (!unit.attackAnim.isBlank()) {
                        v.attack(unit.attackAnim);
                    }
                }
                if (!unit.fireSound.isBlank()) {
                    v.fireSound(unit.fireSound);
                }
                if (!unit.dieSound.isBlank()) {
                    v.dieSound(unit.dieSound);
                }
            });
        }
        return visuals;
    }

    /** Assemble a game with no custom scripts (they compile to nothing here). */
    public static DukeGame toGame(StudioProject project) {
        return toGame(project, Map.of());
    }

    /**
     * Assemble a ready-to-run {@link DukeGame} the real-RTS way: the project
     * provides maps, factions (with their starting bases) and units; the actual
     * match — which map, who plays which faction — is chosen at play time
     * through the game's skirmish menus (or dictated by the multiplayer host).
     */
    public static DukeGame toGame(StudioProject project, Map<String, Class<? extends UnitScript>> scripts) {
        project.ensureIntegrity();
        var defaultMap = project.maps.get(0);
        var game = DukeGame.create(project.title)
                .subtitle(project.menuSubtitle)
                .loadUnits(toIni(project))
                .map(defaultMap.cellsWide, defaultMap.cellsHigh);

        game.customModules(factory -> {
            for (var entry : scripts.entrySet()) {
                ScriptModule.registerScript(factory, entry.getKey(), () -> {
                    try {
                        return entry.getValue().getDeclaredConstructor().newInstance();
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(
                                "script '" + entry.getKey() + "' needs a public no-arg constructor", e);
                    }
                });
            }
        });

        var gamePlayers = new GamePlayer[project.players.size()];
        for (int i = 0; i < project.players.size(); i++) {
            var def = project.players.get(i);
            gamePlayers[i] = game.addPlayer(def.name, Color.decode(def.colorHex));
            game.money(gamePlayers[i], def.money);
        }
        for (int a = 0; a < gamePlayers.length; a++) {
            for (int b = a + 1; b < gamePlayers.length; b++) {
                if (project.players.get(a).team == project.players.get(b).team) {
                    game.allies(gamePlayers[a], gamePlayers[b]);
                } else {
                    game.enemies(gamePlayers[a], gamePlayers[b]);
                }
            }
        }
        if (project.localPlayer >= 0 && project.localPlayer < gamePlayers.length) {
            game.localPlayer(gamePlayers[project.localPlayer]);
        }

        var mapNames = project.maps.stream().map(m -> m.name).toList();
        var factionNames = project.factions.stream().map(f -> f.name).toList();
        game.skirmish(mapNames, factionNames,
                (g, mapName, chosenFactions) -> assembleMatch(project, g, gamePlayers, mapName, chosenFactions));
        return game;
    }

    /** Build the chosen match: terrain, neutral objects, and each player's base. */
    private static void assembleMatch(StudioProject project, DukeGame game, GamePlayer[] gamePlayers,
            String mapName, java.util.List<String> chosenFactions) {
        var map = project.findMap(mapName);
        if (map == null) {
            map = project.maps.get(0);
        }

        var grid = new uz.duke.core.pathfind.PathGrid(map.cellsWide, map.cellsHigh);
        for (var cell : map.blockedCells) {
            var parts = cell.split(",");
            if (parts.length == 2) {
                try {
                    grid.setBlocked(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), true);
                } catch (NumberFormatException ignored) {
                    // malformed cell entry — skip
                }
            }
        }
        game.applyMapTerrain(grid);

        for (var neutral : map.neutrals) {
            game.spawnNeutral(neutral.unitName, neutral.x, neutral.y);
        }

        for (int i = 0; i < gamePlayers.length; i++) {
            var faction = resolveFaction(project, chosenFactions, i);
            if (faction == null) {
                continue;
            }
            float[] start = i < map.startPositions.size()
                    ? map.startPositions.get(i)
                    : fallbackStart(map, i);
            for (var startingUnit : faction.startingUnits) {
                game.spawn(startingUnit.unit, gamePlayers[i],
                        start[0] + startingUnit.dx, start[1] + startingUnit.dy);
            }
        }
    }

    private static StudioProject.FactionDef resolveFaction(StudioProject project,
            java.util.List<String> chosenFactions, int playerIndex) {
        if (chosenFactions != null && playerIndex < chosenFactions.size()) {
            var chosen = project.findFaction(chosenFactions.get(playerIndex));
            if (chosen != null) {
                return chosen;
            }
        }
        var authored = project.findFaction(project.players.get(playerIndex).faction);
        if (authored != null) {
            return authored;
        }
        return project.factions.isEmpty() ? null : project.factions.get(0);
    }

    /** A deterministic corner start when the map defines too few positions. */
    private static float[] fallbackStart(StudioProject.MapDef map, int playerIndex) {
        float worldW = map.cellsWide * 10f;
        float worldH = map.cellsHigh * 10f;
        float margin = 60f;
        return switch (playerIndex % 4) {
            case 0 -> new float[] {margin, worldH - margin};
            case 1 -> new float[] {worldW - margin, margin};
            case 2 -> new float[] {margin, margin};
            default -> new float[] {worldW - margin, worldH - margin};
        };
    }
}
