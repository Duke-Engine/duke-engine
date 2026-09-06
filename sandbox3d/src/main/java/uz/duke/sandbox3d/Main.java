package uz.duke.sandbox3d;

import java.awt.Color;
import uz.duke.client3d.Duke3D;
import uz.duke.client3d.Visuals;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.ProductionUpdate;
import uz.duke.game.DukeGame;

/**
 * The full Unity-style pitch in one file: a 3D RTS with an animated model,
 * primitive stand-ins for the rest, camera, selection and orders — in well
 * under 60 lines of game code.
 */
public final class Main {

    public static void main(String[] args) {
        var game = DukeGame.create("Duke RTS 3D — Skirmish")
                .loadUnits(DukeGame.STARTER_UNITS)
                .map(70, 45);

        var you = game.addPlayer("USA", new Color(90, 170, 255));
        var foe = game.addPlayer("China", new Color(235, 85, 60));
        game.enemies(you, foe).localPlayer(you).money(you, 1500).money(foe, 5000);

        game.spawn("PowerPlant", you, 60, 330)
                .spawn("Barracks", you, 100, 360)
                .spawn("Rifleman", you, 130, 330)
                .spawn("Rifleman", you, 140, 340)
                .spawn("Tank", you, 120, 310);

        game.spawn("PowerPlant", foe, 620, 90)
                .spawn("Barracks", foe, 580, 60)
                .spawn("Tank", foe, 550, 100);

        // Enemy "AI": rally on your base and keep training riflemen.
        game.onStart(g -> {
            var barracks = foeBarracks(g);
            if (barracks != null) {
                barracks.setRallyPoint(new Coord3D(150f, 330f, 0f));
            }
        });
        game.everySeconds(6, g -> {
            var barracks = foeBarracks(g);
            if (barracks != null && barracks.getQueueSize() < 2) {
                barracks.queue(g.getLogic().getThingFactory().findTemplate("Rifleman"));
            }
        });
        game.onPlayerDefeated((g, p) ->
                System.out.println(p.getName() + (p == you ? " — defeat..." : " — victory!")));

        // Unity-style asset binding: the rifleman gets a real animated model
        // (Oto, from jME's test assets); everything else uses clean primitives
        // until you drop in your own glTF files.
        var visuals = Visuals.create()
                .unit("Rifleman", u -> u.model("Models/Oto/Oto.mesh.xml")
                        .scale(0.35f).yOffset(1.8f).facing(90)
                        .idle("stand").walk("Walk").attack("push"));

        Duke3D.launch(game, visuals);
    }

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
