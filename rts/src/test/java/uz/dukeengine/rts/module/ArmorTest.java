package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.rts.RtsSimulation;
import uz.dukeengine.core.GameLogic;
import java.util.Map;
import uz.dukeengine.core.data.Binder;
import uz.dukeengine.core.data.DukeText;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.rts.message.GameMessage;

class ArmorTest {

    private static ActiveBody bodyWithArmor(Map<DamageType, Float> armor) {
        var owner = new GameObject(new ObjectId(1), ThingTemplate.named("X").build());
        return new ActiveBody(owner, new ActiveBody.Data(100f, armor));
    }

    @Test
    void armorScalesDamageByType() {
        var armor = Map.of(
                DamageType.ARMOR_PIERCING, 1.5f, // weakness
                DamageType.FLAME, 0.5f);          // resistance

        var pierced = bodyWithArmor(armor);
        pierced.damage(40f, DamageType.ARMOR_PIERCING);
        assertEquals(40f, pierced.getHealth(), 1e-4f); // 40 * 1.5 = 60 damage

        var burned = bodyWithArmor(armor);
        burned.damage(40f, DamageType.FLAME);
        assertEquals(80f, burned.getHealth(), 1e-4f); // 40 * 0.5 = 20 damage

        var plain = bodyWithArmor(armor);
        plain.damage(40f, DamageType.NORMAL);
        assertEquals(60f, plain.getHealth(), 1e-4f); // unlisted type -> 1.0
    }

    @Test
    void untypedDamageIsNormalType() {
        var armor = Map.of(DamageType.NORMAL, 0.25f);
        var body = bodyWithArmor(armor);
        body.damage(40f); // routes to NORMAL
        assertEquals(90f, body.getHealth(), 1e-4f); // 40 * 0.25 = 10
    }

    @Test
    void armorAndDamageTypeAreReadFromTheirBlocks() {
        var binder = new Binder();
        var body = binder.bind(DukeText.parse("""
                ActiveBody
                  MaxHealth = 200
                  Armor = [ARMOR_PIERCING = 2.0, FLAME = 0.5]
                End
                """, "body.duke").getFirst(), ActiveBody.Data.class);
        assertEquals(200f, body.maxHealth(), 1e-6f);
        assertEquals(Map.of(DamageType.ARMOR_PIERCING, 2.0f, DamageType.FLAME, 0.5f), body.armor());

        var weapon = binder.bind(DukeText.parse("""
                WeaponUpdate
                  Damage = 10
                  AttackRange = 5
                  ReloadFrames = 2
                  DamageType = FLAME
                End
                """, "weapon.duke").getFirst(), WeaponUpdate.Data.class);
        assertEquals(DamageType.FLAME, weapon.damageType());
        assertTrue(weapon.attackOnTheMove(), "what the block leaves out is the default");
    }

    /**
     * A game's own kind of damage, fired by a weapon and answered by an armour, with the engine never
     * having heard the word. This is the case the closed enum could not express, and the one an RTS's
     * whole armour table is made of: infantry take rifles in full and shrug off a tank shell, so the two
     * kinds have to be two words or a rifle shreds tanks.
     */
    @Test
    void aGamesOwnDamageTypeIsFiredAndAnsweredWithoutTheEngineKnowingTheWord() {
        var smallArms = DamageType.of("SMALL_ARMS");
        var binder = new Binder();
        var infantry = binder.bind(DukeText.parse("""
                ActiveBody
                  MaxHealth = 100
                  Armor = [SMALL_ARMS = 1.0, ARMOR_PIERCING = 0.1]
                End
                """, "infantry.duke").getFirst(), ActiveBody.Data.class);
        var rifle = binder.bind(DukeText.parse("""
                WeaponUpdate
                  Damage = 20
                  AttackRange = 5
                  ReloadFrames = 2
                  DamageType = SMALL_ARMS
                End
                """, "rifle.duke").getFirst(), WeaponUpdate.Data.class);

        assertEquals(smallArms, rifle.damageType(), "read out of the block, interned, never registered");

        var body = new ActiveBody(new GameObject(new ObjectId(1), ThingTemplate.named("Rifleman").build()),
                infantry);
        body.damage(rifle.damage(), rifle.damageType());
        assertEquals(80f, body.getHealth(), 1e-4f, "a rifle takes a man in full");

        body.damage(rifle.damage(), DamageType.ARMOR_PIERCING);
        assertEquals(78f, body.getHealth(), 1e-4f, "and a tank shell barely scratches him");
    }

    @Test
    void weaponDamageTypeMeetsTargetArmorInCombat() {
        var thingFactory = new ThingFactory(RtsModules.withDefaults());
        var armoredTank = ThingTemplate.named("ArmoredTank")
                .module(new ActiveBody.Data(100f, Map.of(DamageType.ARMOR_PIERCING, 1.5f)))
                .build();
        var apShooter = ThingTemplate.named("APShooter")
                .module(new ActiveBody.Data(100f))
                .module(new WeaponUpdate.Data(20f, 10f, 2, DamageType.ARMOR_PIERCING))
                .build();
        thingFactory.addTemplate(armoredTank);
        thingFactory.addTemplate(apShooter);

        var logic = new RtsSimulation(thingFactory) {
            @Override
            protected void onRtsCommand(GameMessage command) {
                if (command instanceof GameMessage.AttackObject a) {
                    for (var id : a.units()) {
                        findObject(id).findModule(WeaponUpdate.class).attack(a.target());
                    }
                }
            }

            @Override
            protected void simulate() {
            }
        };
        logic.init();
        int me = logic.getPlayerList().addPlayer("Me").getIndex();
        int foe = logic.getPlayerList().addPlayer("Foe").getIndex();
        logic.getPlayerList().getPlayer(me).setRelationshipTo(
                logic.getPlayerList().getPlayer(foe), Relationship.ENEMIES);

        var shooter = logic.createObject(apShooter);
        shooter.setPlayerIndex(me);
        shooter.setPosition(Coord3D.ZERO);
        var tank = logic.createObject(armoredTank);
        tank.setPlayerIndex(foe);
        tank.setPosition(new Coord3D(5f, 0f, 0f));

        logic.issueCommand(new GameMessage.AttackObject(me, java.util.List.of(shooter.getId()), tank.getId()));
        logic.update(); // 20 AP damage * 1.5 armor weakness = 30
        assertEquals(70f, tank.getBody().getHealth(), 1e-4f);
    }
}
