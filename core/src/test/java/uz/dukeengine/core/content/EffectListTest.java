package uz.dukeengine.core.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.data.Binder;
import uz.dukeengine.core.data.DukeText;

/** An effect list written as a game writes one: every kind of entry, and what each leaves out. */
class EffectListTest {

    @Test
    void everyKindOfEntryIsRead() {
        var list = new Binder().bind(DukeText.parse("""
                EffectList
                  Name = FX_TankDie
                  Entries = [
                    ParticleSystem
                      Name = TankExplosion
                      Count = 2
                      Offset = [0, 0, 5]
                      Radius = [5, 10]
                      InitialDelay = [3, 6]
                      OrientToObject = Yes
                    End,
                    Sound
                      Name = TankDie
                    End,
                    LightPulse
                      Colour = 0xFF8000
                      RadiusShare = 1.5
                      RiseFrames = 2
                      FallFrames = 20
                    End,
                    Shake
                      Strength = SEVERE
                    End,
                    Scorch
                      Pictures = [textures/scorch_1.png, textures/scorch_2.png]
                      Radius = 12
                    End,
                    Tracer
                      Speed = 20
                      Probability = 0.3
                    End,
                    AtBone
                      Effect = FX_Smoke
                      Bone = Wheel
                    End
                  ]
                End
                """, "fx.duke").getFirst(), EffectList.class);

        assertEquals("FX_TankDie", list.name());
        assertEquals(7, list.entries().size());
        var system = assertInstanceOf(EffectList.ParticleSystem.class, list.entries().get(0));
        assertEquals(2, system.count());
        assertEquals(List.of(0f, 0f, 5f), system.offset());
        assertTrue(system.orientToObject());
        assertEquals(0xFF8000, assertInstanceOf(EffectList.LightPulse.class, list.entries().get(2)).colour());
        assertEquals(EffectList.Shake.Strength.SEVERE,
                assertInstanceOf(EffectList.Shake.class, list.entries().get(3)).strength());
        var tracer = assertInstanceOf(EffectList.Tracer.class, list.entries().get(5));
        assertEquals(10f, tracer.length(), 0f, "left out: the reference's 10");
        assertEquals(1f, tracer.decayAt(), 0f);
        assertTrue(assertInstanceOf(EffectList.AtBone.class, list.entries().get(6)).orientToBone(),
                "turned with its bone unless it says not");
    }

    @Test
    void aParticleSystemLeftAloneIsOneCopyWithItsOwnDelay() {
        var list = new Binder().bind(DukeText.parse("""
                EffectList
                  Name = Puff
                  Entries = [
                    ParticleSystem
                      Name = Smoke
                    End,
                    Shake
                    End
                  ]
                End
                """, "fx.duke").getFirst(), EffectList.class);

        var system = (EffectList.ParticleSystem) list.entries().getFirst();
        assertEquals(1, system.count());
        assertTrue(system.initialDelay().isEmpty(), "the system's own");
        assertEquals(EffectList.Shake.Strength.NORMAL, ((EffectList.Shake) list.entries().get(1)).strength());
    }
}
