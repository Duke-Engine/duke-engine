package uz.duke.studio.examples;

import java.nio.file.Path;
import java.util.Map;
import uz.duke.studio.io.ProjectIO;
import uz.duke.studio.model.CapabilityType;
import uz.duke.studio.model.StudioProject;
import uz.duke.studio.model.StudioProject.Placement;
import uz.duke.studio.model.StudioProject.PlayerDef;
import uz.duke.studio.model.StudioProject.ScriptDef;
import uz.duke.studio.model.StudioProject.UnitDef;

/**
 * The dogfooding game: a complete Rohan vs Mordor skirmish authored purely
 * through the Studio's own project model — factions, full unit rosters, a map
 * with mountain chokepoints, a scripted Mordor AI that trains waves and throws
 * them at you, and win/lose by annihilation.
 *
 * <p>You are Rohan (bottom-left). Hold the passes, train from the barracks
 * (select it, press numbers), and destroy the Orc Pit. {@code main} writes
 * {@code examples/RohanVsMordor.duke} — open it in Duke Studio and press Play.
 */
public final class RohanVsMordor {

    private RohanVsMordor() {
    }

    public static void main(String[] args) throws Exception {
        var target = Path.of(args.length > 0 ? args[0] : "../examples/RohanVsMordor.duke").normalize();
        ProjectIO.save(build(), target);
        System.out.println("wrote " + target.toAbsolutePath());
    }

    public static StudioProject build() {
        var project = new StudioProject();
        project.title = "Rohan vs Mordor";
        project.menuSubtitle = "Hold the passes of the White Mountains";
        project.mapCellsWide = 90;  // 900 world units
        project.mapCellsHigh = 60;  // 600 world units
        project.localPlayer = 0;

        project.factions.add(new StudioProject.FactionDef("Rohan", "Kingdom of Rohan", "#5AAAFF"));
        project.factions.add(new StudioProject.FactionDef("Mordor", "Hosts of Mordor", "#C03A2B"));

        rohanRoster(project);
        mordorRoster(project);
        scripts(project);
        mountains(project);

        project.players.add(new PlayerDef("Theoden", "#5AAAFF", 1400, 1, "Rohan"));
        project.players.add(new PlayerDef("Sauron", "#C03A2B", 3500, 2, "Mordor"));

        placeArmies(project);
        return project;
    }

    // ---- rosters ----

    private static void rohanRoster(StudioProject project) {
        var archer = warrior("ElfArcher", "Elf Archer", "Rohan", 70, 120, 1.5f);
        attack(archer, 9, 38, 12, 0);
        move(archer, 13, 0);
        experience(archer, 30, "60 180 360");
        project.units.add(archer);

        var rider = warrior("Rider", "Rider of Rohan", "Rohan", 150, 250, 3f);
        attack(rider, 20, 8, 20, 0);
        move(rider, 26, 160);
        experience(rider, 60, "120 300 600");
        project.units.add(rider);

        var legolas = warrior("Legolas", "Legolas", "Rohan", 220, 800, 8f);
        attack(legolas, 26, 46, 8, 0);
        move(legolas, 16, 0);
        experience(legolas, 200, "150 400 800");
        legolas.capabilities.put(CapabilityType.AUTO_HEAL.name(),
                params(CapabilityType.AUTO_HEAL, "HealPerSecond", "3"));
        project.units.add(legolas);

        var barracks = structure("RohanBarracks", "Golden Hall Barracks", "Rohan", 900);
        produce(barracks, "ElfArcher Rider Legolas");
        project.units.add(barracks);

        var tower = structure("GuardTower", "Guard Tower", "Rohan", 500);
        attack(tower, 14, 48, 10, 0);
        project.units.add(tower);
    }

    private static void mordorRoster(StudioProject project) {
        var orc = warrior("Orc", "Orc Warrior", "Mordor", 130, 90, 1.2f);
        attack(orc, 13, 6, 15, 0);
        move(orc, 12, 0);
        experience(orc, 25, "75 200 400");
        orc.scripts.add("Berserker");
        project.units.add(orc);

        var archer = warrior("MordorArcher", "Orc Archer", "Mordor", 60, 110, 1.5f);
        attack(archer, 8, 32, 12, 0);
        move(archer, 12, 0);
        experience(archer, 25, "75 200 400");
        archer.scripts.add("Berserker");
        project.units.add(archer);

        var troll = warrior("Troll", "Cave Troll", "Mordor", 450, 500, 6f);
        attack(troll, 45, 8, 45, 7); // club smash: slow, splashing
        move(troll, 9, 90);
        experience(troll, 150, "200 500 1000");
        troll.scripts.add("Berserker");
        project.units.add(troll);

        var pit = structure("OrcPit", "Orc Pit", "Mordor", 900);
        produce(pit, "Orc MordorArcher Troll");
        pit.scripts.add("WarlordAI"); // the enemy commander lives here
        project.units.add(pit);
    }

    // ---- scripts (the Mordor AI) ----

    private static void scripts(StudioProject project) {
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
                        if (distanceTo(enemy) > 10 && !isMoving()) {
                            moveTo(enemy.getPosition().x(), enemy.getPosition().y());
                        }
                        if (!isAttacking()) {
                            attack(enemy);
                        }
                    }
                }
                """));

        project.scripts.add(new ScriptDef("WarlordAI", """
                package game.scripts;

                import uz.duke.game.script.UnitScript;

                /** Mordor's war machine: trains waves and hurls them at Rohan. */
                public class WarlordAI extends UnitScript {

                    private static final String[] WAVE = {"Orc", "Orc", "MordorArcher", "Orc", "Troll"};
                    private int next;

                    @Override
                    public void onStart() {
                        setRallyPoint(170, 460); // the heart of Rohan's base
                    }

                    @Override
                    public void onUpdate() {
                        if (frame() % 30 != 0) {
                            return; // think once per game second
                        }
                        if (productionQueue() >= 2) {
                            return; // keep the forges busy but the queue short
                        }
                        if (trainUnit(WAVE[next % WAVE.length])) {
                            next++;
                        }
                    }
                }
                """));
    }

    // ---- the map: a mountain wall with two passes ----

    private static void mountains(StudioProject project) {
        for (int cx = 42; cx <= 46; cx++) {
            for (int cy = 0; cy < 60; cy++) {
                boolean northPass = cy >= 12 && cy <= 17;
                boolean southPass = cy >= 42 && cy <= 47;
                if (!northPass && !southPass) {
                    project.blockedCells.add(cx + "," + cy);
                }
            }
        }
    }

    // ---- starting armies ----

    private static void placeArmies(StudioProject project) {
        // Rohan (you), bottom-left, guarding the south pass
        add(project, "RohanBarracks", 0, 140, 470);
        add(project, "GuardTower", 0, 300, 430);
        add(project, "GuardTower", 0, 300, 500);
        add(project, "Legolas", 0, 250, 460);
        add(project, "ElfArcher", 0, 230, 430);
        add(project, "ElfArcher", 0, 230, 450);
        add(project, "ElfArcher", 0, 230, 480);
        add(project, "ElfArcher", 0, 230, 500);
        add(project, "Rider", 0, 200, 440);
        add(project, "Rider", 0, 200, 490);

        // Mordor (AI), top-right
        add(project, "OrcPit", 1, 760, 110);
        add(project, "Troll", 1, 700, 140);
        add(project, "Orc", 1, 720, 90);
        add(project, "Orc", 1, 730, 120);
        add(project, "Orc", 1, 740, 150);
        add(project, "MordorArcher", 1, 750, 80);
        add(project, "MordorArcher", 1, 760, 170);
    }

    // ---- small authoring helpers ----

    private static UnitDef warrior(String name, String display, String faction,
            float hp, int cost, float buildSeconds) {
        var unit = new UnitDef();
        unit.name = name;
        unit.displayName = display;
        unit.faction = faction;
        unit.maxHealth = hp;
        unit.buildCost = cost;
        unit.buildTimeSeconds = buildSeconds;
        unit.visionRange = 45;
        return unit;
    }

    private static UnitDef structure(String name, String display, String faction, float hp) {
        var unit = warrior(name, display, faction, hp, 0, 1);
        unit.structure = true;
        unit.visionRange = 50;
        return unit;
    }

    private static void attack(UnitDef unit, float damage, float range, int reload, float splash) {
        var params = StudioProject.defaults(CapabilityType.ATTACK);
        params.put("Damage", String.valueOf(damage));
        params.put("AttackRange", String.valueOf(range));
        params.put("ReloadFrames", String.valueOf(reload));
        params.put("SplashRadius", String.valueOf(splash));
        unit.capabilities.put(CapabilityType.ATTACK.name(), params);
    }

    private static void move(UnitDef unit, float speed, float turnRate) {
        var params = StudioProject.defaults(CapabilityType.MOVE);
        params.put("Speed", String.valueOf(speed));
        params.put("TurnRate", String.valueOf(turnRate));
        unit.capabilities.put(CapabilityType.MOVE.name(), params);
    }

    private static void experience(UnitDef unit, int worth, String thresholds) {
        var params = StudioProject.defaults(CapabilityType.EXPERIENCE);
        params.put("ExperienceValue", String.valueOf(worth));
        params.put("ExperienceRequired", thresholds);
        unit.capabilities.put(CapabilityType.EXPERIENCE.name(), params);
    }

    private static void produce(UnitDef unit, String builds) {
        unit.capabilities.put(CapabilityType.PRODUCE.name(),
                params(CapabilityType.PRODUCE, "Builds", builds));
    }

    private static Map<String, String> params(CapabilityType type, String key, String value) {
        var params = StudioProject.defaults(type);
        params.put(key, value);
        return params;
    }

    private static void add(StudioProject project, String unit, int player, float x, float y) {
        project.placements.add(new Placement(unit, player, x, y));
    }
}
