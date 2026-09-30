package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.DeathType;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.combat.event.WeaponFired;
import uz.dukeengine.combat.module.Weapon;
import uz.dukeengine.combat.module.WeaponSet;
import uz.dukeengine.combat.module.WeaponSlot;
import uz.dukeengine.combat.module.WeaponUpdate;

/**
 * A weapon's pitch range — the reference's {@code MinTargetPitch} and {@code MaxTargetPitch}: a tank's gun, -15 to 15
 * degrees, is no use against a thing well above it, which another of its weapons may still take.
 */
class PitchRangeTest {

    private static Weapon weapon(String name, float least, float most) {
        return new Weapon(name, 10f, 100f, 5, 5, DamageType.NORMAL, 0f, true, List.of(), 0, 0, true,
                DeathType.NORMAL, List.of(), List.of(), 0f, 0f, false, least, most);
    }

    private record Field(CombatTest.CombatLogic logic, GameObject shooter, GameObject victim, List<String> fired) {

        List<String> run(int frames) {
            for (int frame = 0; frame < frames; frame++) {
                logic.update();
                for (var event : logic.drainEvents()) {
                    if (event instanceof WeaponFired shot) {
                        fired.add(shot.weapon());
                    }
                }
            }
            return fired;
        }
    }

    /** A shooter of no height, and a victim 60 off whose bottom stands {@code above} over it. */
    private static Field field(float above, WeaponSlot... slots) {
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(RtsTemplate.named("Tank").geometry(Geometry.POINT).module(new ActiveBody.Data(100f))
                .module(WeaponUpdate.Data.sets(List.of(new WeaponSet(List.of(), List.of(slots))))).build());
        factory.addTemplate(RtsTemplate.named("Victim").geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(1_000f)).build());
        var logic = new CombatTest.CombatLogic(factory);
        logic.init();
        logic.addWeapons(List.of(weapon("Gun", -15f, 15f), weapon("Rockets", -180f, 180f)));
        var us = logic.getPlayerList().addPlayer("Us");
        var them = logic.getPlayerList().addPlayer("Them");
        us.setRelationshipTo(them, Relationship.ENEMIES);
        them.setRelationshipTo(us, Relationship.ENEMIES);
        var shooter = logic.createObject(factory.findTemplate("Tank"));
        shooter.setPlayerIndex(us.getIndex());
        shooter.setPosition(Coord3D.ZERO);
        var victim = logic.createObject(factory.findTemplate("Victim"));
        victim.setPlayerIndex(them.getIndex());
        victim.setPosition(new Coord3D(60f, 0f, above));
        return new Field(logic, shooter, victim, new ArrayList<>());
    }

    @Test
    void aVictimWellAboveTheRangeIsNotAcquiredNorTakenByAnOrderAndOneJustAboveIsFiredAt() {
        var high = field(30f, new WeaponSlot("Gun"));
        high.run(10);
        var weapon = high.shooter().findModule(WeaponUpdate.class);
        assertNull(weapon.getTarget(), "26.6 degrees up to its bottom: not acquired");
        assertFalse(weapon.attack(high.victim().getId()), "and an order on it refused");
        assertEquals(List.of(), high.fired());

        var low = field(9f, new WeaponSlot("Gun"));
        low.run(1);
        assertEquals(List.of("Gun"), low.fired(), "less than 10 above, pitch is not weighed");
    }

    @Test
    void aSlotOfNoRangeFiresAtWhatTheOtherCannotPointAt() {
        var field = field(30f, new WeaponSlot("Gun"), new WeaponSlot("Rockets"));
        field.run(1);
        assertEquals(List.of("Rockets"), field.fired());
    }
}
