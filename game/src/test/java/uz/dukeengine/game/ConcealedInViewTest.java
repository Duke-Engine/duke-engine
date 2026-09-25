package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.module.Concealment;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.rts.event.WeaponFired;

/** What each player is shown of a thing kept from some of them, or hidden from all. */
class ConcealedInViewTest {

    /** Hidden from every side but its own. */
    static final class Stealth extends Module implements Concealment {
        Stealth(GameObject owner) {
            super(owner);
        }

        @Override
        public boolean hiddenFrom(int player) {
            return player != getOwner().getPlayerIndex();
        }
    }

    /** A hidden American rifleman firing at a Chinese one beside him, shown to whoever {@code local} is. */
    private static DukeGame match(int local, boolean watch) {
        var game = DukeGame.create("stealth").loadUnits(DukeGame.STARTER_UNITS).map(70, 45);
        var usa = game.addPlayer("USA", Color.BLUE);
        var china = game.addPlayer("China", Color.RED);
        game.enemies(usa, china).localPlayer(local == 1 ? usa : china);
        game.spawn("Rifleman", usa, 50f, 50f);
        game.spawn("Rifleman", china, 70f, 50f);
        game.runHeadless(1);
        hider(game).addModule(new Stealth(hider(game)));
        if (watch) {
            game.watch();
        }
        game.runHeadless(20);
        return game;
    }

    private static GameObject hider(DukeGame game) {
        return game.getLogic().getObjects().stream().filter(o -> o.getPlayerIndex() == 1).findFirst().orElseThrow();
    }

    private static boolean shows(DukeGame game, GameObject thing) {
        return game.getSnapshot().units().stream().anyMatch(view -> view.id() == thing.getId().value());
    }

    private static boolean firedIn(DukeGame game, GameObject thing) {
        var fired = new java.util.ArrayList<Boolean>();
        for (int frame = 0; frame < 30; frame++) {
            game.runHeadless(1);
            fired.add(game.getSnapshot().events().stream()
                    .anyMatch(event -> event instanceof WeaponFired shot && shot.shooter().equals(thing.getId())));
        }
        return fired.contains(true);
    }

    @Test
    void itsOwnSideSeesItAndItsShots() {
        var game = match(1, false);
        assertTrue(shows(game, hider(game)));
        assertTrue(firedIn(game, hider(game)), "its side hears its rifle");
    }

    @Test
    void theSideItIsKeptFromSeesNeitherItNorItsShots() {
        var game = match(2, false);
        assertFalse(shows(game, hider(game)), "not drawn, so not clicked, boxed, hovered or on the radar");
        assertFalse(firedIn(game, hider(game)), "and its rifle's moment is not played for them");
    }

    @Test
    void aWatcherSeesItThroughoutButNothingHiddenFromEveryone() {
        var game = match(2, true);
        var hider = hider(game);
        assertTrue(shows(game, hider), "a watcher sees everything");

        hider.setStatus(ObjectStatus.HIDDEN);
        game.runHeadless(1);
        assertFalse(shows(game, hider), "but a thing not there is there for nobody");
    }
}
