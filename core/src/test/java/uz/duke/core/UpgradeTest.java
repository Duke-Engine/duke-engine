package uz.duke.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.duke.core.module.ModuleFactory;
import uz.duke.core.player.Upgrade;
import uz.duke.core.thing.ThingFactory;

class UpgradeTest {

    static final class TestLogic extends GameLogic {
        TestLogic(ThingFactory thingFactory) {
            super(thingFactory);
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
        logic.getPlayerList().getPlayer(usa).deposit(500);
    }

    @Test
    void purchaseChargesAndAppliesEffectOnce() {
        var training = new Upgrade("AdvancedTraining", 200, 1.5f);

        assertTrue(logic.purchaseUpgrade(usa, training));
        var player = logic.getPlayerList().getPlayer(usa);
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
        var expensive = new Upgrade("Superweapon", 9999, 2.0f);
        assertFalse(logic.purchaseUpgrade(usa, expensive));
        assertEquals(500, logic.getPlayerList().getPlayer(usa).getMoney());
        assertFalse(logic.getPlayerList().getPlayer(usa).hasUpgrade("Superweapon"));
    }

    @Test
    void upgradesStackMultiplicatively() {
        logic.purchaseUpgrade(usa, new Upgrade("A", 100, 1.5f));
        logic.purchaseUpgrade(usa, new Upgrade("B", 100, 2.0f));
        assertEquals(3.0f, logic.getPlayerList().getPlayer(usa).getWeaponDamageBonus(), 1e-6f);
    }
}
