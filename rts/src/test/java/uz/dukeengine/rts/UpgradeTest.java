package uz.dukeengine.rts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.core.module.ModuleFactory;
import uz.dukeengine.rts.player.Upgrade;
import uz.dukeengine.core.thing.ThingFactory;

class UpgradeTest {

    static final class TestLogic extends RtsSimulation {
        TestLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void onRtsCommand(GameMessage command) {
        }

        @Override
        protected void simulate() {
        }
    }

    private TestLogic logic;
    private int usa;

    @BeforeEach
    void setUp() {
        logic = new TestLogic(new ThingFactory(ModuleFactory.withDefaults()));
        logic.init();
        usa = logic.getPlayerList().addPlayer("USA").getIndex();
        logic.getRtsPlayer(usa).deposit(500);
    }

    @Test
    void purchaseChargesAndAppliesEffectOnce() {
        var training = Upgrade.weaponDamage("AdvancedTraining", 200, 1.5f);

        assertTrue(logic.purchaseUpgrade(usa, training));
        var player = logic.getRtsPlayer(usa);
        assertEquals(300, player.getMoney());
        assertTrue(player.hasUpgrade("AdvancedTraining"));
        assertEquals(1.5f, player.getWeaponDamageBonus(), 1e-6f);

        // Buying again is a no-op (already owned), no further charge or effect.
        assertFalse(logic.purchaseUpgrade(usa, training));
        assertEquals(300, player.getMoney());
        assertEquals(1.5f, player.getWeaponDamageBonus(), 1e-6f);
    }

    @Test
    void cannotAffordIsRejected() {
        var expensive = Upgrade.weaponDamage("Superweapon", 9999, 2.0f);
        assertFalse(logic.purchaseUpgrade(usa, expensive));
        assertEquals(500, logic.getRtsPlayer(usa).getMoney());
        assertFalse(logic.getRtsPlayer(usa).hasUpgrade("Superweapon"));
    }

    @Test
    void upgradesStackMultiplicatively() {
        logic.purchaseUpgrade(usa, Upgrade.weaponDamage("A", 100, 1.5f));
        logic.purchaseUpgrade(usa, Upgrade.weaponDamage("B", 100, 2.0f));
        assertEquals(3.0f, logic.getRtsPlayer(usa).getWeaponDamageBonus(), 1e-6f);
    }
}
