package uz.duke.core.module;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import uz.duke.core.GameLogic;
import uz.duke.core.ini.Ini;
import uz.duke.core.math.Coord3D;
import uz.duke.core.message.GameMessage;
import uz.duke.core.player.Relationship;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ObjectId;
import uz.duke.core.thing.ThingFactory;
import uz.duke.core.thing.ThingTemplate;

class ArmorTest {

    private static ActiveBody bodyWithArmor(Armor armor) {
        var owner = new GameObject(new ObjectId(1), ThingTemplate.named("X").build());
        return new ActiveBody(owner, new ActiveBody.Data(100f, armor));
    }

    @Test
    void armorScalesDamageByType() {
        var armor = Armor.builder()
                .set(DamageType.ARMOR_PIERCING, 1.5f) // weakness
                .set(DamageType.FLAME, 0.5f)          // resistance
                .build();

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
        var armor = Armor.builder().set(DamageType.NORMAL, 0.25f).build();
        var body = bodyWithArmor(armor);
        body.damage(40f); // routes to NORMAL
        assertEquals(90f, body.getHealth(), 1e-4f); // 40 * 0.25 = 10
    }

    @Test
    void armorAndDamageTypeParseFromIni() {
        var bodyIni = Ini.of("""
                MaxHealth = 200
                Armor = ARMOR_PIERCING:2.0
                Armor = FLAME:0.5
                End
                """, Ini.registry());
        var bodyData = (ActiveBody.Data) ActiveBody.parseData(bodyIni);
        assertEquals(200f, bodyData.maxHealth(), 1e-6f);
        assertEquals(2.0f, bodyData.armor().getMultiplier(DamageType.ARMOR_PIERCING), 1e-6f);
        assertEquals(0.5f, bodyData.armor().getMultiplier(DamageType.FLAME), 1e-6f);

        var weaponIni = Ini.of("""
                Damage = 10
                AttackRange = 5
                ReloadFrames = 2
                DamageType = FLAME
                End
                """, Ini.registry());
        var weaponData = (WeaponUpdate.Data) WeaponUpdate.parseData(weaponIni);
        assertEquals(DamageType.FLAME, weaponData.damageType());
    }

    @Test
    void weaponDamageTypeMeetsTargetArmorInCombat() {
        var thingFactory = new ThingFactory(ModuleFactory.withDefaults());
        var armoredArmor = Armor.builder().set(DamageType.ARMOR_PIERCING, 1.5f).build();
        var armoredTank = ThingTemplate.named("ArmoredTank")
                .module("ActiveBody", new ActiveBody.Data(100f, armoredArmor))
                .build();
        var apShooter = ThingTemplate.named("APShooter")
                .module("ActiveBody", new ActiveBody.Data(100f))
                .module("WeaponUpdate", new WeaponUpdate.Data(20f, 10f, 2, DamageType.ARMOR_PIERCING))
                .build();
        thingFactory.addTemplate(armoredTank);
        thingFactory.addTemplate(apShooter);

        var logic = new GameLogic(thingFactory) {
            @Override
            protected void onCommand(GameMessage command) {
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
