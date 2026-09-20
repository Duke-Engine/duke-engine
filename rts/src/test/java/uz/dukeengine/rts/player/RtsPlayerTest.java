package uz.dukeengine.rts.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.player.PlayerList;

class RtsPlayerTest {

    @Test
    void moneyDepositAndWithdraw() {
        var usa = new RtsPlayer(1, "USA");
        usa.deposit(1000);
        assertTrue(usa.withdraw(600));
        assertEquals(400, usa.getMoney());
        assertFalse(usa.withdraw(500)); // not enough
        assertEquals(400, usa.getMoney());
    }

    @Test
    void negativeAmountsAreIgnored() {
        var usa = new RtsPlayer(1, "USA");
        usa.deposit(-50);
        assertEquals(0, usa.getMoney());
        assertFalse(usa.withdraw(-50));
        assertEquals(0, usa.getMoney());
    }

    @Test
    void upgradesAccumulateAndMultiplyDamage() {
        var usa = new RtsPlayer(1, "USA");
        assertFalse(usa.hasUpgrade("Training"));
        usa.addUpgrade("Training");
        usa.multiplyWeaponDamageBonus(1.5f);
        usa.addUpgrade("Armor");
        usa.multiplyWeaponDamageBonus(2f);

        assertTrue(usa.hasUpgrade("Training"));
        assertEquals(java.util.Set.of("Training", "Armor"), usa.getUpgrades());
        assertEquals(3f, usa.getWeaponDamageBonus(), 1e-6f);
    }

    @Test
    void ofReturnsNullForARosterOfPlainPlayers() {
        // A world that is not running an RTS roster has no RtsPlayer to hand back.
        var plainRoster = new PlayerList();
        plainRoster.init();
        assertNull(RtsPlayer.of(null, 0));
        assertFalse(plainRoster.getNeutralPlayer() instanceof RtsPlayer);
    }
}
