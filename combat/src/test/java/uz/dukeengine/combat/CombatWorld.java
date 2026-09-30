package uz.dukeengine.combat;

import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.player.PlayerList;
import uz.dukeengine.core.thing.ThingFactory;

/**
 * A world of things that fight and nothing more: core's logic holding an armoury, the combat modules its words — no
 * RTS anywhere on the classpath of the tests that use it.
 */
final class CombatWorld extends GameLogic implements ArmedWorld {

    private final Armoury armoury = new Armoury();

    CombatWorld() {
        super(new ThingFactory(CombatModules.withDefaults()));
    }

    CombatWorld(PlayerList.PlayerFactory players) {
        super(new ThingFactory(CombatModules.withDefaults()), new PlayerList(players));
    }

    @Override
    public Armoury armoury() {
        return armoury;
    }

    @Override
    protected void simulate() {
    }
}
