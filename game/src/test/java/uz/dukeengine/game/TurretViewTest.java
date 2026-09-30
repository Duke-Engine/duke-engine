package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.view.Turrets;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.rts.module.Turret;

/** A thing's turrets carried in its view, turned and pitched as its game's {@code Turret} has them. */
class TurretViewTest {

    static final class Battleship extends Module implements Turret {
        Battleship(GameObject owner) {
            super(owner);
        }

        @Override
        public float turretTurn() {
            return 1f;
        }

        @Override
        public float turretPitch() {
            return 0.5f;
        }

        @Override
        public float altTurretTurn() {
            return -2f;
        }

        @Override
        public float altTurretPitch() {
            return 0.25f;
        }
    }

    @Test
    void theViewCarriesEachTurretsTurnAndPitch() {
        var game = DukeGame.create("turrets").loadUnits(DukeGame.STARTER_UNITS).map(20, 20)
                .addUnits(List.of(RtsTemplate.named("Ship").module(new ActiveBody.Data(100f)).build()));
        var me = game.addPlayer("Me", Color.BLUE);
        game.localPlayer(me).spawn("Ship", me, 100f, 100f).spawn("Rifleman", me, 150f, 100f);
        game.runHeadless(1);
        var ship = game.getLogic().getObjects().getFirst();
        ship.addModule(new Battleship(ship));
        game.runHeadless(1);

        var views = game.getSnapshot().units();
        var shipView = views.stream().filter(view -> view.templateName().equals("Ship")).findFirst().orElseThrow();
        assertEquals(new Turrets(1f, 0.5f, -2f, 0.25f), shipView.turrets());
        var riflemanView = views.stream().filter(view -> view.templateName().equals("Rifleman")).findFirst()
                .orElseThrow();
        assertEquals(Turrets.NONE, riflemanView.turrets(), "no turret: straight ahead");
    }
}
