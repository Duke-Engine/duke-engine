package uz.duke.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.duke.rts.RtsSimulation;
import uz.duke.core.GameLogic;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.ActiveBody;
import uz.duke.core.player.Relationship;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ThingFactory;
import uz.duke.core.thing.ThingTemplate;
import uz.duke.rts.message.GameMessage;

class CombatTest {

    /** Routes AttackObject commands to each unit's weapon. */
    static final class CombatLogic extends RtsSimulation {
        CombatLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void onRtsCommand(GameMessage command) {
            if (command instanceof GameMessage.AttackObject attack) {
                for (var id : attack.units()) {
                    var unit = findObject(id);
                    if (unit != null && unit.findModule(WeaponUpdate.class) != null) {
                        unit.findModule(WeaponUpdate.class).attack(attack.target());
                    }
                }
            }
        }

        @Override
        protected void simulate() {
        }
    }

    private CombatLogic logic;
    private ThingTemplate soldier;
    private int usa;
    private int china;

    @BeforeEach
    void setUp() {
        var thingFactory = new ThingFactory(RtsModules.withDefaults());
        soldier = ThingTemplate.named("Soldier")
                .module("ActiveBody", new ActiveBody.Data(100f))
                .module("WeaponUpdate", new WeaponUpdate.Data(25f, 10f, 2))
                .module("ExperienceModule", new ExperienceModule.Data(100, 100, 300, 600))
                .build();
        thingFactory.addTemplate(soldier);

        logic = new CombatLogic(thingFactory);
        logic.init();

        usa = logic.getPlayerList().addPlayer("USA").getIndex();
        china = logic.getPlayerList().addPlayer("China").getIndex();
        var usaPlayer = logic.getPlayerList().getPlayer(usa);
        var chinaPlayer = logic.getPlayerList().getPlayer(china);
        usaPlayer.setRelationshipTo(chinaPlayer, Relationship.ENEMIES);
        chinaPlayer.setRelationshipTo(usaPlayer, Relationship.ENEMIES);
    }

    private GameObject spawn(int player, Coord3D pos) {
        var unit = logic.createObject(soldier);
        unit.setPlayerIndex(player);
        unit.setPosition(pos);
        return unit;
    }

    @Test
    void enemyInRangeTakesDamagePerReloadCycle() {
        var attacker = spawn(usa, Coord3D.ZERO);
        var victim = spawn(china, new Coord3D(5f, 0f, 0f));

        logic.issueCommand(new GameMessage.AttackObject(usa, List.of(attacker.getId()), victim.getId()));

        logic.update(); // first shot fires immediately
        assertEquals(75f, victim.getBody().getHealth(), 1e-4f);

        logic.update(); // reloading, no shot
        assertEquals(75f, victim.getBody().getHealth(), 1e-4f);

        logic.update(); // reloaded, second shot
        assertEquals(50f, victim.getBody().getHealth(), 1e-4f);
    }

    @Test
    void sustainedFireKillsTheTarget() {
        var attacker = spawn(usa, Coord3D.ZERO);
        var victim = spawn(china, new Coord3D(5f, 0f, 0f));
        logic.issueCommand(new GameMessage.AttackObject(usa, List.of(attacker.getId()), victim.getId()));

        for (int i = 0; i < 20; i++) {
            logic.update();
        }
        assertTrue(victim.isEffectivelyDead());
        assertFalse(attacker.findModule(WeaponUpdate.class).isAttacking()); // target cleared on death
    }

    @Test
    void targetOutOfRangeTakesNoDamage() {
        var attacker = spawn(usa, Coord3D.ZERO);
        var victim = spawn(china, new Coord3D(100f, 0f, 0f)); // beyond range 10
        logic.issueCommand(new GameMessage.AttackObject(usa, List.of(attacker.getId()), victim.getId()));

        for (int i = 0; i < 5; i++) {
            logic.update();
        }
        assertEquals(100f, victim.getBody().getHealth(), 1e-4f);
        assertTrue(attacker.findModule(WeaponUpdate.class).isAttacking()); // still trying
    }

    @Test
    void killingAnEnemyGrantsExperienceAndRanksUp() {
        var attacker = spawn(usa, Coord3D.ZERO);
        var victim = spawn(china, new Coord3D(5f, 0f, 0f));
        logic.issueCommand(new GameMessage.AttackObject(usa, List.of(attacker.getId()), victim.getId()));

        for (int i = 0; i < 20; i++) {
            logic.update();
        }

        var xp = attacker.findModule(ExperienceModule.class);
        assertTrue(victim.isEffectivelyDead());
        assertEquals(100, xp.getExperience()); // victim was worth 100
        assertEquals(VeterancyLevel.VETERAN, xp.getLevel());
    }

    @Test
    void idleUnitAutoAcquiresNearbyEnemy() {
        // No command issued — the unit should defend itself.
        var defender = spawn(usa, Coord3D.ZERO);
        var intruder = spawn(china, new Coord3D(5f, 0f, 0f));

        logic.update();

        assertTrue(intruder.getBody().getHealth() < 100f, "auto-acquired enemy should take fire");
        assertTrue(defender.findModule(WeaponUpdate.class).isAttacking());
    }

    @Test
    void playerUpgradeIncreasesWeaponDamage() {
        logic.purchaseUpgrade(usa, new uz.duke.rts.player.Upgrade("Training", 0, 1.5f));
        var attacker = spawn(usa, Coord3D.ZERO);
        var victim = spawn(china, new Coord3D(5f, 0f, 0f));
        logic.issueCommand(new GameMessage.AttackObject(usa, List.of(attacker.getId()), victim.getId()));

        logic.update(); // first shot: 25 base * 1.5 upgrade = 37.5
        assertEquals(62.5f, victim.getBody().getHealth(), 1e-4f);
    }

    @Test
    void alliesAreNeverFiredUpon() {
        var attacker = spawn(usa, Coord3D.ZERO);
        var friend = spawn(usa, new Coord3D(5f, 0f, 0f)); // same player => allied
        logic.issueCommand(new GameMessage.AttackObject(usa, List.of(attacker.getId()), friend.getId()));

        logic.update();
        assertEquals(100f, friend.getBody().getHealth(), 1e-4f);
        assertFalse(attacker.findModule(WeaponUpdate.class).isAttacking()); // target dropped
    }
}
