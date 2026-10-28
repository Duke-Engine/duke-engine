package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.content.ParticleSystem;
import uz.dukeengine.core.data.Binder;
import uz.dukeengine.core.data.DukeText;

/**
 * Particle systems, a frame at a time, against the reference game's rules — one rule a test, with the numbers
 * worked out by hand. Every system here is written the way a game writes one, as a block.
 */
class ParticlesTest {

    /** The systems a test's world knows, by name, read from the block text given. */
    private static Map<String, ParticleSystem> library(String text) {
        var binder = new Binder();
        var systems = new LinkedHashMap<String, ParticleSystem>();
        for (var block : DukeText.parse(text, "particles.duke")) {
            var system = binder.bind(block, ParticleSystem.class);
            systems.put(system.name(), system);
        }
        return systems;
    }

    private static Particles world(Map<String, ParticleSystem> systems, long seed) {
        return new Particles(systems::get, seed, Particles.Ground.FLAT, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    private static final Particles.Placement HERE = new Particles.Placement(100f, 200f, 5f, 0f);

    /** The one particle of a system that makes one, once. */
    private static final String ONE = """
              BurstCount = [1]
              BurstDelay = [1000]
              Lifetime = [1000]
            """;

    private static Particle only(Emitter emitter) {
        assertEquals(1, emitter.particles().size());
        return emitter.particles().getFirst();
    }

    @Test
    void aParticlesSizeFollowsItsRateAndThatRatesDamping() {
        var world = world(library("""
                ParticleSystem
                  Name = Grows
                  Blend = ALPHA_TEST
                  Size = [10]
                  SizeRate = [2]
                  SizeRateDamping = [0.5]
                """ + ONE + "End\n"), 1L);
        var emitter = world.startAt("Grows", HERE);
        for (int frames = 1; frames <= 6; frames++) {
            world.update();
            // After n frames: 10 + 2 (1 + 0.5 + ... + 0.5^(n-1)) = 10 + 4 (1 - 0.5^n).
            assertEquals(10f + 4f * (1f - (float) Math.pow(0.5, frames)), only(emitter).size, 1e-5f,
                    "after " + frames + " frame(s)");
        }
    }

    /** Alpha moves at a steady rate from key to key and is set exactly on each: 0.5 on frame 10, 0.25 on 20. */
    @Test
    void alphaIsSetExactlyAtEachKey() {
        var world = world(library("""
                ParticleSystem
                  Name = Fades
                  Blend = ALPHA
                  AlphaKeys = [
                    AlphaKey
                      Min = 1
                      Max = 1
                      Frame = 0
                    End,
                    AlphaKey
                      Min = 0.5
                      Max = 0.5
                      Frame = 10
                    End,
                    AlphaKey
                      Min = 0.25
                      Max = 0.25
                      Frame = 20
                    End
                  ]
                """ + ONE + "End\n"), 1L);
        var emitter = world.startAt("Fades", HERE);
        world.update(); // born, and moved once: age 0
        assertEquals(0.95f, only(emitter).alpha, 1e-6f, "a twentieth of the way down already");
        for (int age = 1; age <= 10; age++) {
            world.update();
        }
        assertEquals(0.5f, only(emitter).alpha, "exactly the key's value on its frame");
        for (int age = 11; age <= 20; age++) {
            world.update();
        }
        assertEquals(0.25f, only(emitter).alpha);
    }

    /**
     * Colour moves at a steady rate from key to key but is never set on one, because the scale is added every
     * frame on top: with no scale it passes through the key's value, with a scale it never does.
     */
    @Test
    void colourReachesAKeysValueOnlyWhenTheScaleIsNothing() {
        var keys = """
                  ColourKeys = [
                    ColourKey
                      Colour = 0x000000
                      Frame = 0
                    End,
                    ColourKey
                      Colour = 0x660000
                      Frame = 5
                    End
                  ]
                """;
        var world = world(library("ParticleSystem\n  Name = Plain\n  Blend = ALPHA_TEST\n" + keys + ONE + "End\n"
                + "ParticleSystem\n  Name = Scaled\n  Blend = ALPHA_TEST\n  ColourScale = [5.1]\n" + keys + ONE
                + "End\n"), 1L);
        var plain = world.startAt("Plain", HERE);
        var scaled = world.startAt("Scaled", HERE);
        float key = 0x66 / 255f;
        for (int age = 0; age < 4; age++) {
            world.update();
        }
        // Ages 0 to 3: four steps of a fifth of the way — the key's value, a frame before its own.
        assertEquals(key * 4 / 5, only(plain).red, 1e-6f);
        world.update();
        assertEquals(key, only(plain).red, 1e-6f, "the key's value, with nothing added to it");
        assertEquals(key + 5 * 0.02f, only(scaled).red, 1e-6f, "and a fiftieth more every frame on top");
        assertEquals(5 * 0.02f, only(scaled).green, 1e-6f, "on every channel");
    }

    @Test
    void aHollowSpheresParticlesAreAllOnItsSurface() {
        var world = world(library("""
                ParticleSystem
                  Name = Shell
                  Blend = ALPHA_TEST
                  VolumeType = SPHERE
                  VolSphereRadius = 7
                  IsHollow = Yes
                  BurstCount = [60]
                  BurstDelay = [1000]
                  Lifetime = [100]
                End
                """), 1L);
        var emitter = world.startAt("Shell", HERE);
        world.update();

        assertEquals(60, emitter.particles().size());
        for (var particle : emitter.particles()) {
            float dx = particle.x - HERE.x();
            float dy = particle.y - HERE.y();
            float dz = particle.z - HERE.z();
            assertEquals(7f, (float) Math.sqrt(dx * dx + dy * dy + dz * dz), 1e-4f);
        }
    }

    /** An additive particle is as bright as its colour: black, and past its last key, it is over. */
    @Test
    void anAdditiveParticleDiesOnceBlack() {
        var world = world(library("""
                ParticleSystem
                  Name = Spark
                  Blend = ADDITIVE
                  ColourKeys = [
                    ColourKey
                      Colour = 0xFFFFFF
                      Frame = 0
                    End,
                    ColourKey
                      Colour = 0x000000
                      Frame = 10
                    End
                  ]
                """ + ONE + "End\n"), 1L);
        var emitter = world.startAt("Spark", HERE);
        for (int age = 0; age <= 9; age++) {
            world.update();
        }
        assertEquals(0f, only(emitter).red, 1e-6f, "black on its ninth frame...");
        assertEquals(1f, only(emitter).alpha, "and its alpha never moved");
        world.update();
        assertTrue(emitter.particles().isEmpty(), "...and over on its tenth, once past its last key");
    }

    @Test
    void everyParticleOfAMasterMakesOneInItsSlave() {
        var world = world(library("""
                ParticleSystem
                  Name = Flash
                  Blend = ALPHA_TEST
                  SlaveSystem = Glow
                  SlavePosOffset = [0, 0, 3]
                  VolumeType = BOX
                  VolBoxHalfSize = [10, 10, 10]
                  BurstCount = [5]
                  BurstDelay = [1000]
                  Lifetime = [100]
                  Size = [2]
                End
                ParticleSystem
                  Name = Glow
                  Blend = ADDITIVE
                  Lifetime = [100]
                  Size = [3]
                End
                """), 1L);
        var master = world.startAt("Flash", HERE);
        world.update();

        var slave = master.slave();
        assertEquals(5, master.particles().size());
        assertEquals(5, slave.particles().size(), "one for one");
        for (int at = 0; at < 5; at++) {
            var own = master.particles().get(at);
            var its = slave.particles().get(at);
            assertEquals(own.x, its.x, 1e-5f);
            assertEquals(own.y, its.y, 1e-5f);
            assertEquals(own.z + 3f, its.z, 1e-5f, "at its place, plus the offset");
            assertEquals(6f, its.size, 1e-5f, "its own size, as a scale of the master's");
        }
        assertEquals(ParticleSystem.Blend.ADDITIVE, slave.data().blend(), "and its own look");
    }

    @Test
    void aSystemRidingAParticleGoesWhenItDies() {
        var world = world(library("""
                ParticleSystem
                  Name = Ember
                  Blend = ALPHA_TEST
                  PerParticleAttachedSystem = Smoke
                  VelocityType = ORTHO
                  VelOrthoX = [2]
                  BurstCount = [1]
                  BurstDelay = [1000]
                  Lifetime = [3]
                End
                ParticleSystem
                  Name = Smoke
                  Blend = ALPHA_TEST
                  BurstCount = [1]
                  Lifetime = [5]
                End
                """), 1L);
        world.startAt("Ember", HERE);
        world.update();
        var smoke = world.emitters().stream().filter(e -> e.data().name().equals("Smoke")).findFirst().orElseThrow();
        assertFalse(smoke.isDestroyed(), "riding its spark");
        world.update();
        world.update(); // the spark's third frame, its last
        assertTrue(smoke.isDestroyed(), "gone with its spark");
        int left = smoke.particles().size();
        world.update();
        assertTrue(smoke.particles().size() <= left, "letting out nothing more");
        for (int frame = 0; frame < 6; frame++) {
            world.update();
        }
        assertFalse(world.emitters().contains(smoke), "and gone itself once its last is");
    }

    /** The same seed makes the same system, particle for particle; another seed does not. */
    @Test
    void theSameSeedMakesTheSameSystem() {
        var systems = library("""
                ParticleSystem
                  Name = Dust
                  Blend = ALPHA
                  VolumeType = CYLINDER
                  VolCylinderRadius = 12
                  VolCylinderLength = 4
                  VelocityType = SPHERICAL
                  VelSpherical = [0.5, 2]
                  Size = [2, 6]
                  SizeRate = [0.1, 0.4]
                  Angle = [0, 6.28]
                  AngularRate = [-0.1, 0.1]
                  AlphaKeys = [
                    AlphaKey
                      Min = 0.5
                      Max = 1
                      Frame = 0
                    End,
                    AlphaKey
                      Min = 0
                      Max = 0.2
                      Frame = 40
                    End
                  ]
                  BurstCount = [2, 5]
                  BurstDelay = [1, 3]
                  Lifetime = [30, 60]
                End
                """);
        assertEquals(look(systems, 7L), look(systems, 7L));
        assertNotEquals(look(systems, 7L), look(systems, 8L));
    }

    private static List<String> look(Map<String, ParticleSystem> systems, long seed) {
        var world = world(systems, seed);
        var emitter = world.startAt("Dust", HERE);
        for (int frame = 0; frame < 20; frame++) {
            world.update();
        }
        var seen = new ArrayList<String>();
        for (var particle : emitter.particles()) {
            seen.add(particle.x + "," + particle.y + "," + particle.z + "," + particle.size + "," + particle.angle
                    + "," + particle.alpha);
        }
        return seen;
    }

    // ---- the system's own clock ----

    /** Nothing at all until its delay has passed; then a burst every delay-plus-one frames, as the reference counts. */
    @Test
    void itWaitsItsDelayThenBurstsOnItsBeat() {
        var world = world(library("""
                ParticleSystem
                  Name = Beat
                  Blend = ALPHA_TEST
                  InitialDelay = [3]
                  BurstCount = [1]
                  BurstDelay = [2]
                  Lifetime = [1000]
                End
                """), 1L);
        var emitter = world.startAt("Beat", HERE);
        var counts = new ArrayList<Integer>();
        for (int frame = 0; frame < 10; frame++) {
            world.update();
            counts.add(emitter.particles().size());
        }
        assertEquals(List.of(0, 0, 0, 1, 1, 1, 2, 2, 2, 3), counts);
    }

    /** Its lifetime over, it lets out nothing more, and lives on until its last particle dies. */
    @Test
    void aFinishedSystemLivesUntilItsLastParticleDies() {
        var world = world(library("""
                ParticleSystem
                  Name = Puff
                  Blend = ALPHA_TEST
                  SystemLifetime = 2
                  BurstCount = [1]
                  BurstDelay = [0]
                  Lifetime = [4]
                End
                """), 1L);
        var emitter = world.startAt("Puff", HERE);
        world.update();
        world.update();
        assertEquals(2, emitter.particles().size());
        world.update();
        assertEquals(2, emitter.particles().size(), "its two frames are over: nothing new");
        for (int frame = 0; frame < 3; frame++) {
            world.update();
        }
        assertFalse(world.emitters().contains(emitter), "and it is gone once they are");
    }

    /** A particle that would be born under the ground is not born. */
    @Test
    void aSystemThatEmitsAboveGroundOnlyMakesNothingUnderIt() {
        var systems = library("""
                ParticleSystem
                  Name = Low
                  Blend = ALPHA_TEST
                  IsEmitAboveGroundOnly = Yes
                  VolumeType = LINE
                  VolLineStart = [0, 0, -10]
                  VolLineEnd = [0, 0, 10]
                  BurstCount = [200]
                  BurstDelay = [1000]
                  Lifetime = [100]
                End
                """);
        var world = new Particles(systems::get, 3L, (_, _) -> 5f, Integer.MAX_VALUE, Integer.MAX_VALUE);
        var emitter = world.startAt("Low", HERE);
        world.update();

        assertTrue(emitter.particles().size() > 50 && emitter.particles().size() < 150,
                "about half of the line is under ground at 5: " + emitter.particles().size());
        for (var particle : emitter.particles()) {
            assertTrue(particle.z >= 5f);
        }
    }

    /** A burst from a system that moved is spread back along the way it came, the first furthest back. */
    @Test
    void aMovingSystemsBurstIsSpreadAlongItsWay() {
        var world = world(library("""
                ParticleSystem
                  Name = Trail
                  Blend = ALPHA_TEST
                  BurstCount = [4]
                  BurstDelay = [0]
                  Lifetime = [100]
                End
                """), 1L);
        float[] at = {0f};
        var emitter = world.start("Trail", () -> new Particles.Placement(at[0], 0f, 0f, 0f));
        world.update();
        at[0] = 40f;
        world.update();

        var second = emitter.particles().subList(4, 8);
        assertEquals(List.of(0f, 10f, 20f, 30f), second.stream().map(p -> p.x).toList(),
                "a quarter of the way further along each");
    }

    /** Past its most, the oldest particles of a lower priority are let go first; the highest is never refused. */
    @Test
    void theBudgetLetsTheOldestOfALowerPriorityGo() {
        var systems = library("""
                ParticleSystem
                  Name = Fluff
                  Priority = 1
                  Blend = ALPHA_TEST
                  BurstCount = [5]
                  BurstDelay = [1000]
                  Lifetime = [1000]
                End
                ParticleSystem
                  Name = Blast
                  Priority = 5
                  Blend = ALPHA_TEST
                  BurstCount = [5]
                  BurstDelay = [1000]
                  Lifetime = [1000]
                End
                ParticleSystem
                  Name = Nuke
                  Priority = 9
                  Blend = ALPHA_TEST
                  BurstCount = [20]
                  BurstDelay = [1000]
                  Lifetime = [1000]
                End
                """);
        var world = new Particles(systems::get, 1L, Particles.Ground.FLAT, 6, 9);
        var fluff = world.startAt("Fluff", HERE);
        world.update();
        var blast = world.startAt("Blast", HERE);
        world.update();

        assertEquals(5, blast.particles().size(), "the blast made all of its own");
        assertTrue(fluff.particles().stream().filter(p -> !p.gone).count() <= 2, "at the fluff's cost");
        var nuke = world.startAt("Nuke", HERE);
        world.update();
        assertEquals(20, nuke.particles().size(), "and the highest is never refused");

        var more = world.startAt("Fluff", HERE);
        world.update();
        assertTrue(more.particles().isEmpty(), "nothing lower is left to let go for fluff");
    }

    /** Turned so its top points back at where its system emits from — instead of by its angular rate. */
    @Test
    void aParticleUpTowardsItsEmitterPointsBackAtIt() {
        var world = world(library("""
                ParticleSystem
                  Name = Petals
                  Blend = ALPHA_TEST
                  IsParticleUpTowardsEmitter = Yes
                  VelocityType = ORTHO
                  VelOrthoX = [1]
                  VelOrthoY = [1]
                  AngularRate = [5]
                """ + ONE + "End\n"), 1L);
        var emitter = world.startAt("Petals", HERE);
        world.update();

        // Out along x = y from the emitter: an eighth of a turn from up the ground's y, plus a half turn.
        assertEquals((float) (Math.PI / 4 + Math.PI), only(emitter).angle, 1e-5f);
    }

    /** OUTWARD from a cylinder: across the ground away from its axis, and "other" up. */
    @Test
    void outwardFromACylinderIsAcrossTheGroundAndOtherUp() {
        var world = world(library("""
                ParticleSystem
                  Name = Ring
                  Blend = ALPHA_TEST
                  VolumeType = CYLINDER
                  VolCylinderRadius = 5
                  IsHollow = Yes
                  VelocityType = OUTWARD
                  VelOutward = [2]
                  VelOutwardOther = [1]
                  BurstCount = [10]
                  BurstDelay = [1000]
                  Lifetime = [100]
                End
                """), 1L);
        var emitter = world.startAt("Ring", new Particles.Placement(0f, 0f, 0f, 0f));
        world.update(); // born on the rim, and moved once by what it was born with
        for (var particle : emitter.particles()) {
            float across = (float) Math.sqrt(particle.x * particle.x + particle.y * particle.y);
            assertEquals(7f, across, 1e-4f, "five out, and two further");
            assertEquals(1f, particle.z, 1e-4f);
        }
    }

    /** Where a particle born still has been blown after ten frames of the wind its lines give. */
    private static Particle blown(String wind) {
        var world = world(library("""
                ParticleSystem
                  Name = Smoke
                  Blend = ALPHA_TEST
                """ + wind + ONE + "End\n"), 1L);
        var emitter = world.startAt("Smoke", new Particles.Placement(0f, 0f, 0f, 0f));
        for (int frame = 0; frame < 10; frame++) {
            world.update();
        }
        return only(emitter);
    }

    /** The wind pushes 2 a frame, times the particle's 0.7 to 1.3, along its angle; no wind, no push. */
    @Test
    void theWindBlowsAParticleAlongItsAngle() {
        var still = blown("");
        assertEquals(0f, still.x, 0f, "no wind");
        assertEquals(0f, still.y, 0f);

        var east = blown("""
                  WindMotion = PING_PONG
                  WindPingPongStartAngle = [0]
                  WindPingPongEndAngle = [0.2]
                """);
        assertTrue(east.x > 10f * 2f * 0.7f * 0.97f && east.x < 10f * 2f * 1.3f, "along x: " + east.x);
        assertTrue(Math.abs(east.y) < east.x * 0.25f, "and hardly across: " + east.y);

        var west = blown("""
                  WindMotion = PING_PONG
                  WindPingPongStartAngle = [3.1416]
                  WindPingPongEndAngle = [3.3416]
                """);
        assertTrue(west.x < -10f * 2f * 0.7f * 0.97f, "a wind the other way blows it the other way: " + west.x);
    }

    /** A circular wind turns 0.15 a frame, as the reference's does, so it swings the particle round. */
    @Test
    void aCircularWindTurnsRound() {
        var round = blown("""
                  WindMotion = CIRCULAR
                  WindPingPongStartAngle = [0]
                  WindPingPongEndAngle = [0]
                """);
        // Ten frames turning from 0.15 to 1.5: the pushes sum along roughly 0.8 radians.
        assertTrue(round.y > 0f && round.x > 0f, "blown up and across as the wind turns: " + round.x + ", " + round.y);
    }

    private static final String FIELD = """
            ParticleSystem
              Name = Field
              Priority = 11
              Blend = ALPHA_TEST
              IsGroundAligned = Yes
              BurstCount = [5]
              BurstDelay = [0]
              Lifetime = [1000]
            End
            """;

    /**
     * Past the field cap a field that already burns makes no more: one over the most, as the reference's {@code >}
     * lets it; a new field still gets its first; and with no cap nothing is held back.
     */
    @Test
    void theFieldCapHoldsGroundAlignedFieldsBack() {
        var capped = world(library(FIELD), 1L);
        capped.fieldCap(11, 6);
        var field = capped.startAt("Field", HERE);
        for (int frame = 0; frame < 5; frame++) {
            capped.update();
        }
        assertEquals(7, field.particles().size(), "held at one over six");
        var another = capped.startAt("Field", HERE);
        for (int frame = 0; frame < 5; frame++) {
            capped.update();
        }
        assertEquals(1, another.particles().size(), "a new field gets its first only");

        var free = world(library(FIELD), 1L);
        var unheld = free.startAt("Field", HERE);
        for (int frame = 0; frame < 5; frame++) {
            free.update();
        }
        assertEquals(25, unheld.particles().size(), "no cap, five a frame");
    }

    /** How many particles a system of one a frame has let out after two seconds drawn at {@code fps}. */
    private static int madeAt(int fps, List<Visuals.ParticleLod> levels) {
        var world = world(library("""
                ParticleSystem
                  Name = Sparks
                  Priority = 5
                  Blend = ALPHA_TEST
                  BurstCount = [1]
                  BurstDelay = [0]
                  Lifetime = [1000]
                End
                """), 1L);
        world.lod(levels);
        var sparks = world.startAt("Sparks", HERE);
        for (int frame = 0; frame < 2 * fps; frame++) {
            world.step(1f / fps);
        }
        return sparks.particles().size();
    }

    /**
     * Below a level's frame rate its mask skips particles under its skip priority, and none under its least is made
     * at all — the reference's dynamic LOD; a level over the rate, or none, makes them all.
     */
    @Test
    void aLowFrameRateMakesFewerParticles() {
        var levels = List.of(new Visuals.ParticleLod(25, 0, 0, 0), new Visuals.ParticleLod(0, 1, 0, 13));
        int fast = madeAt(60, levels);
        int slow = madeAt(10, levels);
        assertEquals(madeAt(60, List.of()), fast, "fast enough: every one");
        assertTrue(fast > 50, "a frame each: " + fast);
        assertEquals(fast / 2f, slow, 2f, "at 10 a second, every other one: " + slow + " of " + fast);

        var none = List.of(new Visuals.ParticleLod(25, 0, 0, 0), new Visuals.ParticleLod(0, 0, 6, 0));
        assertEquals(0, madeAt(10, none), "under the slow level's least priority: none");
    }

    /** A system the game never described draws nothing, and says so once. */
    @Test
    void aSystemNobodyDescribedStartsNothing() {
        var world = world(Map.of(), 1L);
        assertNull(world.startAt("Nothing", HERE));
        assertTrue(world.emitters().isEmpty());
    }
}
