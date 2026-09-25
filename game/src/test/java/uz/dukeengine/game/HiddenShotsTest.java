package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.Concealment;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.DeathType;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Kind;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.rts.event.WeaponFired;
import uz.dukeengine.rts.module.Weapon;
import uz.dukeengine.rts.module.WeaponSet;
import uz.dukeengine.rts.module.WeaponSlot;
import uz.dukeengine.rts.module.WeaponUpdate;

/**
 * A hidden thing's shot shown where the game says: a mine always shows its blast, as does a weapon that says so (the
 * reference's PlayFXWhenStealthed); and, where the game keeps them to the owner, a hidden shooter's other shots are
 * kept from its allies too.
 */
class HiddenShotsTest {

    private static final Kind MINE = Kind.of("MINE");

    /** Hidden from its side's enemies, as a stealthed thing undetected is. */
    static final class Stealth extends Module implements Concealment {
        Stealth(GameObject owner) {
            super(owner);
        }

        @Override
        public boolean hiddenFrom(int player) {
            var world = getOwner().getWorld();
            return world.getRelationship(getOwner().getPlayerIndex(), player) == Relationship.ENEMIES;
        }
    }

    private static Weapon weapon(String name, boolean shownWhenHidden) {
        return new Weapon(name, 5f, 30f, 10, 10, DamageType.NORMAL, 0f, true, List.of(), 0, 0, true,
                DeathType.NORMAL, List.of(), List.of(), 0f, 0f, shownWhenHidden);
    }

    private static RtsTemplate shooter(String name, String weapon, Kind... kinds) {
        return RtsTemplate.named(name).kindOf(kinds).visionRange(50f).module(new ActiveBody.Data(100f))
                .module(WeaponUpdate.Data.sets(List.of(new WeaponSet(List.of(), List.of(new WeaponSlot(weapon))))))
                .build();
    }

    /** Whether a hidden {@code template} of the first side, firing at the enemy's rifleman beside it, is heard. */
    private static boolean heard(String template, int viewer, boolean ownerOnly) {
        var game = DukeGame.create("hidden").loadUnits(DukeGame.STARTER_UNITS).map(40, 40)
                .addWeapons(List.of(weapon("MineBlast", true), weapon("TrapGun", false)))
                .addUnits(List.of(shooter("Demo", "MineBlast"), shooter("Trap", "TrapGun"),
                        shooter("Mine", "TrapGun", MINE)));
        var usa = game.addPlayer("USA", Color.BLUE);
        var uk = game.addPlayer("UK", Color.GREEN);
        var china = game.addPlayer("China", Color.RED);
        game.enemies(usa, china).enemies(uk, china).allies(usa, uk)
                .localPlayer(switch (viewer) {
                    case 0 -> usa;
                    case 1 -> uk;
                    default -> china;
                });
        game.spawn(template, usa, 100f, 100f);
        game.spawn("Rifleman", china, 120f, 100f);
        game.runHeadless(1);
        game.getLogic().setShownWhenHidden(List.of(MINE));
        game.getLogic().setHiddenShotsToOwnerOnly(ownerOnly);
        var hider = game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals(template))
                .findFirst().orElseThrow();
        hider.addModule(new Stealth(hider));
        boolean heard = false;
        for (int frame = 0; frame < 40; frame++) {
            game.runHeadless(1);
            heard |= game.getSnapshot().events().stream()
                    .anyMatch(event -> event instanceof WeaponFired shot && shot.shooter().equals(hider.getId()));
        }
        return heard;
    }

    @Test
    void aMarkedShotOfAHiddenShooterIsShownToTheEnemyWhoSeesThePlaceAndAnUnmarkedOneIsNot() {
        assertTrue(heard("Demo", 2, false), "a demo trap's detonation, marked, is shown to the enemy beside it");
        assertTrue(heard("Mine", 2, false), "and a mine's, whatever its weapon");
        assertFalse(heard("Trap", 2, false), "an unmarked one is not");
    }

    @Test
    void keptToTheOwnerAnAllyIsNotShownTheUnmarkedShotAndTheOwnerIs() {
        assertTrue(heard("Trap", 1, false), "by default an ally, whom it is not hidden from, is shown it");
        assertFalse(heard("Trap", 1, true), "kept to the owner, the ally is not");
        assertTrue(heard("Trap", 0, true), "and the owner is");
        assertTrue(heard("Demo", 1, true), "a marked one is shown to the ally all the same");
    }
}
