package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.game.view.EffectView;

/** An effect riding a thing until the simulation ends it: numbered, shown where the thing is, ended, gone with it. */
class RidingEffectTest {

    private static final String UNITS = """
            Object
              Name = Barracks
              KindOf = [STRUCTURE]
              VisionRange = 150
              Geometry = Box
                MajorRadius = 15
                MinorRadius = 15
                Height = 20
              End
              Modules = [
                ActiveBody
                  MaxHealth = 500
                End
              ]
            End
            """;

    private static DukeGame game() {
        var game = DukeGame.create("Smoke").loadUnits(UNITS).map(80, 80);
        var me = game.addPlayer("Me", Color.CYAN);
        game.localPlayer(me).spawn("Barracks", me, 100f, 100f);
        game.runHeadless(1);
        return game;
    }

    private static GameObject barracks(DukeGame game) {
        return game.getLogic().getObjects().getFirst();
    }

    @Test
    void aHeadlessGameGivesANumberShowsItAndEndsItAndTheSumsAreUntouched() {
        var game = game();
        var smoke = new AtomicInteger();
        var before = new AtomicLong();
        var after = new AtomicLong();
        game.runOnSimThread(() -> {
            before.set(game.getLogic().checksum());
            smoke.set(game.getLogic().effect("SmallLightSmokeColumn", barracks(game), "SMOKE01", null));
            after.set(game.getLogic().checksum());
        });
        game.runHeadless(1);

        assertTrue(smoke.get() > 0, "a number");
        assertEquals(before.get(), after.get(), "drawing only: out of the checksum");
        assertEquals(new EffectView(smoke.get(), "SmallLightSmokeColumn", barracks(game).getId().value(), "SMOKE01",
                null), game.getSnapshot().effects().getFirst());

        game.runOnSimThread(() -> game.getLogic().endEffect(smoke.get()));
        game.runHeadless(1);
        assertTrue(game.getSnapshot().effects().isEmpty(), "ended, gone");
    }

    @Test
    void oneRidingAThingThatIsTakenAwayEndsWithIt() {
        var game = game();
        game.runOnSimThread(() -> game.getLogic().effect("SmallLightSmokeColumn", barracks(game), null, null));
        game.runHeadless(1);
        assertEquals(1, game.getLogic().getRidingEffects().size());

        game.runOnSimThread(() -> barracks(game).markDestroyed());
        game.runHeadless(2);
        assertTrue(game.getLogic().getRidingEffects().isEmpty(), "gone with it");
        assertTrue(game.getSnapshot().effects().isEmpty());
    }
}
