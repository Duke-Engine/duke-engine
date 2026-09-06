package uz.duke.sandbox;

import java.awt.Color;
import uz.duke.core.module.ProductionUpdate;
import uz.duke.game.DukeGame;

/**
 * A complete playable RTS in under 40 lines — the Unity-style promise of
 * duke-engine. You (blue) hold a base against China (red); their barracks keeps
 * training riflemen that march on your base. Select with the left mouse button,
 * order with the right.
 */
public final class Main {

    public static void main(String[] args) {
        var game = DukeGame.create("Duke RTS — Skirmish")
                .loadUnits(DukeGame.STARTER_UNITS)
                .map(70, 45); // 700×450 world units

        var you = game.addPlayer("USA", new Color(90, 170, 255));
        var foe = game.addPlayer("China", new Color(235, 85, 60));
        game.enemies(you, foe).localPlayer(you).money(you, 1500).money(foe, 5000);

        // Your base (bottom-left)
        game.spawn("PowerPlant", you, 60, 330)
                .spawn("Barracks", you, 100, 360)
                .spawn("Rifleman", you, 130, 330)
                .spawn("Rifleman", you, 140, 340)
                .spawn("Tank", you, 120, 310);

        // Enemy base (top-right)
        game.spawn("PowerPlant", foe, 620, 90)
                .spawn("Barracks", foe, 580, 60)
                .spawn("Tank", foe, 550, 100);

        // Enemy "AI": rally at your base, keep training riflemen.
        game.onStart(g -> {
            var barracks = foeBarracks(g);
            if (barracks != null) {
                barracks.setRallyPoint(new uz.duke.core.math.Coord3D(150f, 330f, 0f));
            }
        });
        game.everySeconds(6, g -> {
            var barracks = foeBarracks(g);
            if (barracks != null && barracks.getQueueSize() < 2) {
                barracks.queue(g.getLogic().getThingFactory().findTemplate("Rifleman"));
            }
        });

        game.onPlayerDefeated((g, p) ->
                System.out.println(p.getName() + " is annihilated — " +
                        (p == you ? "defeat..." : "victory!")));

        game.start();
    }

    /** The enemy's (only) production building, or null once destroyed. */
    private static ProductionUpdate foeBarracks(DukeGame g) {
        for (var object : g.getLogic().getObjects()) {
            if (object.getPlayerIndex() == 2) {
                var production = object.findModule(ProductionUpdate.class);
                if (production != null) {
                    return production;
                }
            }
        }
        return null;
    }
}
