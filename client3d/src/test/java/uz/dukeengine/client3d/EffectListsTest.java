package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.content.EffectList;
import uz.dukeengine.core.content.ParticleSystem;
import uz.dukeengine.core.data.Binder;
import uz.dukeengine.core.data.DukeText;
import uz.dukeengine.core.event.ObjectDied;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.DeathType;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.rts.event.ShotLanded;
import uz.dukeengine.rts.event.WeaponFired;

/**
 * Effect lists, and where the world's moments play them: every entry at once, at the thing's place turned the
 * way it faces, at a gun's barrel, where a shot struck, where a thing died — as the reference places them.
 */
class EffectListsTest {

    private static final WorldMoments.Floor FLAT = (x, z) -> 0f;

    /** Blocks of the kinds a game writes, read as it reads them. */
    private static <T extends Record> Map<String, T> read(String text, Class<T> type) {
        var binder = new Binder();
        var found = new LinkedHashMap<String, T>();
        for (var block : DukeText.parse(text, "test.duke")) {
            var record = binder.bind(block, type);
            found.put(type == EffectList.class ? ((EffectList) record).name() : ((ParticleSystem) record).name(),
                    record);
        }
        return found;
    }

    private static final Map<String, ParticleSystem> SYSTEMS = read("""
            ParticleSystem
              Name = Flash
              BurstCount = [1]
              BurstDelay = [1000]
              Lifetime = [100]
            End
            ParticleSystem
              Name = Smoke
              InitialDelay = [30]
              BurstCount = [1]
              BurstDelay = [1000]
              Lifetime = [100]
            End
            """, ParticleSystem.class);

    /** What a list played besides its particle systems. */
    private static final class Shown implements EffectLists.Show {
        final List<String> sounds = new ArrayList<>();
        final List<Vector3f> tracers = new ArrayList<>();

        @Override
        public void sound(String cue, Vector3f at) {
            sounds.add(cue);
        }

        @Override
        public void light(Vector3f at, int colour, float radius, int riseFrames, int fallFrames) {
        }

        @Override
        public void shake(Vector3f at, EffectList.Shake.Strength strength) {
        }

        @Override
        public void scorch(Vector3f at, String picture, float radius) {
        }

        @Override
        public void tracer(Vector3f from, Vector3f to, EffectList.Tracer tracer) {
            tracers.add(to);
        }

        final List<String> thrown = new ArrayList<>();
        final List<Thrown> flights = new ArrayList<>();
        final List<EffectList.Debris> pieces = new ArrayList<>();
        final List<EffectList.ClipSet> clips = new ArrayList<>();
        /** Where what it draws is hung, or null to draw nothing. */
        Node drawsUnder;

        @Override
        public com.jme3.scene.Spatial debris(EffectList.Debris debris, EffectList.ClipSet clips, Quaternion turn,
                Thrown flight, com.jme3.scene.Spatial forWhom) {
            thrown.add(debris.model() + "#" + debris.piece());
            flights.add(flight);
            pieces.add(debris);
            this.clips.add(clips);
            if (drawsUnder == null) {
                return null;
            }
            var piece = new Node("piece");
            piece.setLocalTranslation(flight.at());
            drawsUnder.attachChild(piece);
            return piece;
        }
    }

    private record Scene(Particles systems, EffectLists lists, Shown shown) {
    }

    private static Scene scene(String lists) {
        var systems = new Particles(SYSTEMS::get, 7L, Particles.Ground.FLAT, Integer.MAX_VALUE, Integer.MAX_VALUE);
        var named = read(lists, EffectList.class);
        var shown = new Shown();
        return new Scene(systems, new EffectLists(named::get, systems, shown, 11L), shown);
    }

    /** A place in the systems' frame, for comparing with where an emitter stands. */
    private static void assertAt(float x, float y, float z, Emitter emitter) {
        var at = emitter.where();
        assertEquals(x, at[0], 1e-3f, "x");
        assertEquals(y, at[1], 1e-3f, "y");
        assertEquals(z, at[2], 1e-3f, "z");
    }

    /** A thing's turn as the client draws one: facing {@code orientation} in the simulation's frame. */
    private static Quaternion facing(float orientation) {
        return new Quaternion().fromAngleAxis(-orientation, Vector3f.UNIT_Y);
    }

    @Test
    void twoSystemsWithOffsetsStartAtTheThingTurnedTheWayItFaces() {
        var scene = scene("""
                EffectList
                  Name = TankDies
                  Entries = [
                    ParticleSystem
                      Name = Flash
                      Offset = [10, 0, 5]
                    End,
                    ParticleSystem
                      Name = Smoke
                      Offset = [0, 4, 0]
                    End,
                    Sound
                      Name = TankDie
                    End
                  ]
                End
                """);
        // The client's (100, 0, 200) is the systems' (100, -200, 0); facing the simulation's +y, a quarter turn.
        scene.lists().play("TankDies", new EffectLists.Cue(new Vector3f(100f, 0f, 200f),
                facing((float) Math.PI / 2f), null, null, 0f));

        var started = scene.systems().emitters();
        assertEquals(2, started.size(), "both at once");
        assertAt(100f, -210f, 5f, started.get(0)); // ten ahead of it — the simulation's +y, the systems' -y
        assertAt(104f, -200f, 0f, started.get(1)); // four to its left
        assertEquals(List.of("TankDie"), scene.shown().sounds, "and its sound with them");
    }

    @Test
    void threeCopiesOnARingOfTenAreEachTenFromTheMiddle() {
        var scene = scene("""
                EffectList
                  Name = Sparks
                  Entries = [
                    ParticleSystem
                      Name = Flash
                      Count = 3
                      Radius = [10]
                    End
                  ]
                End
                """);
        scene.lists().play("Sparks", EffectLists.Cue.at(new Vector3f(50f, 0f, 50f)));

        var started = scene.systems().emitters();
        assertEquals(3, started.size());
        for (var emitter : started) {
            var at = emitter.where();
            assertEquals(10f, (float) Math.hypot(at[0] - 50f, at[1] + 50f), 1e-3f);
            assertEquals(0f, at[2], 1e-3f);
        }
    }

    @Test
    void aListsInitialDelayReplacesTheSystemsOwn() {
        var scene = scene("""
                EffectList
                  Name = Late
                  Entries = [
                    ParticleSystem
                      Name = Smoke
                      InitialDelay = [5]
                    End,
                    ParticleSystem
                      Name = Smoke
                    End
                  ]
                End
                """);
        scene.lists().play("Late", EffectLists.Cue.at(new Vector3f()));

        assertEquals(5, scene.systems().emitters().get(0).delayLeft(), "the list's own delay");
        assertEquals(30, scene.systems().emitters().get(1).delayLeft(), "the system's, where the list says none");
    }

    @Test
    void anAttachedSystemFollowsItsThingAndEndsWithIt() {
        var scene = scene("""
                EffectList
                  Name = Burning
                  Entries = [
                    ParticleSystem
                      Name = Flash
                      AttachToObject = Yes
                    End
                  ]
                End
                """);
        var world = new Node("world");
        var truck = new Node("truck");
        world.attachChild(truck);
        world.updateGeometricState();
        scene.lists().play("Burning", new EffectLists.Cue(new Vector3f(), null, truck, null, 0f));
        var burning = scene.systems().emitters().getFirst();

        truck.setLocalTranslation(30f, 0f, 40f);
        world.updateGeometricState();
        scene.systems().update();
        assertAt(30f, -40f, 0f, burning);

        truck.removeFromParent();
        scene.systems().update();
        assertTrue(burning.isDestroyed(), "gone with the truck");
    }

    /**
     * An effect the simulation played on a thing is cued on it, and what attaches to a thing follows it; one at a
     * point stands there turned its way; a name the game has no list by plays nothing.
     */
    @Test
    void anEffectTheSimulationPlayedOnAThingFollowsIt() {
        var scene = scene("""
                EffectList
                  Name = Burning
                  Entries = [
                    ParticleSystem
                      Name = Flash
                      AttachToObject = Yes
                    End
                  ]
                End
                """);
        var world = new Node("world");
        var truck = new Node("truck");
        truck.setLocalTranslation(10f, 0f, 20f);
        world.attachChild(truck);
        world.updateGeometricState();
        var onTruck = new uz.dukeengine.core.event.EffectPlayed(5, "Burning", new Coord3D(10f, 20f, 0f), 0f,
                new ObjectId(3));

        var cue = WorldMoments.played(onTruck, truck, FLAT);
        assertSame(truck, cue.thing());
        assertTrue(scene.lists().play("Burning", cue));
        var burning = scene.systems().emitters().getFirst();
        truck.setLocalTranslation(30f, 0f, 40f);
        world.updateGeometricState();
        scene.systems().update();
        assertAt(30f, -40f, 0f, burning);

        var atAPoint = WorldMoments.played(new uz.dukeengine.core.event.EffectPlayed(5, "Burning",
                new Coord3D(70f, 80f, 30f), 1f, null), null, FLAT);
        assertEquals(new Vector3f(70f, 30f, 80f), atAPoint.at());
        assertEquals(facing(1f), atAPoint.turn());
        assertNull(atAPoint.thing());
        assertFalse(scene.lists().play("NoSuchList", atAPoint), "no list by that name: nothing played, nothing broken");
    }

    /** A list's debris: its copies thrown from where it plays, each up and out within its ranges. */
    @Test
    void aListThrowsItsDebrisFromWhereItPlays() {
        var scene = scene("""
                EffectList
                  Name = TurretBlownOff
                  Entries = [
                    Debris
                      Model = models/vehicles/tank.glb
                      Piece = Turret
                      Count = 3
                      Up = [8, 12]
                      Out = [2, 4]
                      Lifetime = [60, 90]
                    End
                  ]
                End
                """);

        scene.lists().play("TurretBlownOff", EffectLists.Cue.at(new Vector3f(50f, 0f, 70f)));

        assertEquals(List.of("models/vehicles/tank.glb#Turret", "models/vehicles/tank.glb#Turret",
                "models/vehicles/tank.glb#Turret"), scene.shown().thrown);
        for (var flight : scene.shown().flights) {
            var start = flight.at().clone();
            assertEquals(new Vector3f(50f, 0f, 70f), start, "from where it played");
            flight.frame(FLAT);
            var first = flight.at().subtract(start);
            float up = first.y + 1f; // what gravity took off it in the first frame
            float out = (float) Math.hypot(first.x, first.z);
            assertTrue(up >= 8f && up <= 12f, "up within its range: " + up);
            assertTrue(out >= 2f - 1e-4f && out <= 4f + 1e-4f, "and out: " + out);
        }
    }

    /** A thrown gunner: heard where it strikes, trailing smoke, flailing then landing, and in its side's colour. */
    @Test
    void aPieceNamesWhatItPlaysAfterTheThrow() {
        var scene = scene("""
                EffectList
                  Name = GunnerThrown
                  Entries = [
                    Debris
                      Model = models/infantry/gunner.glb
                      Count = 4
                      Up = [4]
                      Friction = 0.15
                      LifeFromRest = true
                      Lifetime = [60]
                      BounceSound = BodyThud
                      ParticleSystem = Flash
                      ClipSets = [
                        ClipSet
                          Flying = Flail_A
                          Landed = Land_A
                        End,
                        ClipSet
                          Flying = Flail_B
                          Landed = Land_B
                        End
                      ]
                      LandedEffect = BodyLanded
                      HouseColoured = true
                    End
                  ]
                End
                """);
        scene.shown().drawsUnder = new Node("scene");

        scene.lists().play("GunnerThrown", EffectLists.Cue.at(new Vector3f(5f, 0f, 6f)));

        var piece = scene.shown().pieces.getFirst();
        assertEquals(0.15f, piece.friction());
        assertTrue(piece.lifeFromRest());
        assertEquals("BodyThud", piece.bounceSound());
        assertEquals("BodyLanded", piece.landedEffect());
        assertTrue(piece.houseColoured());
        var sets = List.of(new EffectList.ClipSet("Flail_A", "Land_A"), new EffectList.ClipSet("Flail_B", "Land_B"));
        assertEquals(sets, piece.clipSets());
        assertEquals(4, scene.shown().clips.size());
        assertTrue(sets.containsAll(scene.shown().clips), "each copy one of its sets: " + scene.shown().clips);
        assertEquals(4, scene.systems().emitters().size(), "a trail riding each copy");
        assertAt(5f, -6f, 0f, scene.systems().emitters().getFirst());
    }

    /** A tank whose gun has two barrels, a muzzle each: the reference's MUZZLE01 and MUZZLE02, and flashes. */
    private static Node tank() {
        var model = new Node("tank");
        var first = new Node("MUZZLE01");
        first.setLocalTranslation(12f, 3f, 1f);
        var second = new Node("MUZZLE02");
        second.setLocalTranslation(12f, 3f, -1f);
        var flash = new Node("MuzzleFX01");
        model.attachChild(first);
        model.attachChild(second);
        first.attachChild(flash);
        return model;
    }

    @Test
    void aShotStartsAtItsBarrelAndTheNextAtTheNextBarrel() {
        var scene = scene("""
                EffectList
                  Name = TankGunFire
                  Entries = [
                    ParticleSystem
                      Name = Flash
                    End
                  ]
                End
                """);
        var world = new Node("world");
        var model = tank();
        model.setLocalTranslation(100f, 0f, 200f);
        world.attachChild(model);
        world.updateGeometricState();
        var visual = Visuals.create().unit("Tank", look -> look.fireBone(0, "Muzzle")).of("Tank");
        var barrels = new Barrels();
        barrels.dress(7, model, visual);
        var shot = new WeaponFired(3, new ObjectId(7), new ObjectId(9), new Coord3D(100f, 200f, 0f),
                new Coord3D(100f, 300f, 0f), "TankGun", 0, false, 0f);

        for (int shots = 0; shots < 2; shots++) {
            var bone = barrels.fire(7, 0);
            scene.lists().play("TankGunFire", WorldMoments.fired(shot, bone, model, FLAT));
        }

        var started = scene.systems().emitters();
        assertAt(112f, -201f, 3f, started.get(0)); // MUZZLE01: the client's (112, 3, 201)
        assertAt(112f, -199f, 3f, started.get(1)); // and the second shot from MUZZLE02
    }

    @Test
    void theFlashIsHiddenButOnTheFrameItsBarrelFires() {
        var model = tank();
        var flash = model.getChild("MuzzleFX01");
        var visual = Visuals.create().unit("Tank", look -> look.fireBone(0, "Muzzle").muzzleFlash(0, "MuzzleFX"))
                .of("Tank");
        var barrels = new Barrels();

        barrels.dress(7, model, visual);
        assertSame(Spatial.CullHint.Always, flash.getLocalCullHint(), "hidden before a shot");

        barrels.fire(7, 0);
        assertSame(Spatial.CullHint.Inherit, flash.getLocalCullHint(), "shown on the frame it fires");

        barrels.frame();
        assertSame(Spatial.CullHint.Always, flash.getLocalCullHint(), "and hidden after");
    }

    @Test
    void aBarrelKicksBackAndSettles() {
        var model = tank();
        var barrel = new Node("Barrel01");
        barrel.setLocalTranslation(5f, 2f, 0f);
        model.attachChild(barrel);
        var visual = Visuals.create().unit("Tank", look -> look.recoilBone(0, "Barrel")).of("Tank");
        var barrels = new Barrels();
        barrels.dress(7, model, visual);

        barrels.fire(7, 0);
        barrels.frame();
        assertEquals(2f, barrels.shift(7, 0, 0), 1e-5f, "the first kick, 2");
        barrels.frame();
        barrels.frame();
        assertEquals(3f, barrels.shift(7, 0, 0), 1e-5f, "as far as it goes, 3");
        assertEquals(2f, barrel.getLocalTranslation().x, 1e-5f, "back along its own x");
        for (int frame = 0; frame < 50; frame++) {
            barrels.frame();
        }
        assertEquals(0f, barrels.shift(7, 0, 0), 1e-5f, "and home again");
        assertEquals(5f, barrel.getLocalTranslation().x, 1e-5f);
    }

    @Test
    void aShotLandsAtTheMiddleOfWhatItHit() {
        // The simulation says where: the middle of what was hit, 3 up a thing 6 tall standing at (40, 60).
        var landed = new ShotLanded(4, new ObjectId(1), new ObjectId(2), "TankGun", new Coord3D(40f, 60f, 3f),
                new Coord3D(0f, 60f, 0f), 0f);

        var cue = WorldMoments.landed(landed, null, FLAT);

        assertEquals(new Vector3f(40f, 3f, 60f), cue.at());
        // Turned along the way the shot came: from the ground at x 0 to 3 up at x 40, tipped up with it.
        var forward = cue.turn().mult(Vector3f.UNIT_X);
        var way = new Vector3f(40f, 3f, 0f).normalizeLocal();
        assertEquals(way.x, forward.x, 1e-4f);
        assertEquals(way.y, forward.y, 1e-4f);
        assertEquals(0f, forward.z, 1e-4f);
    }

    @Test
    void aThingThatDiesThirtyUpIsMournedThirtyUp() {
        var death = new ObjectDied(9, new ObjectId(5), "Comanche", 1, new Coord3D(70f, 80f, 30f),
                DeathType.NORMAL, null, 0f);

        var cue = WorldMoments.died(death, null, FLAT);

        assertEquals(30f, cue.at().y, 1e-5f, "in the air, where it was");
        assertEquals(new Vector3f(70f, 30f, 80f), cue.at());
        var grounded = WorldMoments.died(new ObjectDied(9, new ObjectId(6), "Tank", 1, new Coord3D(70f, 80f, 0f),
                DeathType.NORMAL, null, 0f), null, (x, z) -> 12f);
        assertEquals(12f, grounded.at().y, 1e-5f, "and never under the floor");
    }

    @Test
    void aTracerNeedsSomewhereToGo() {
        var scene = scene("""
                EffectList
                  Name = Tracers
                  Entries = [
                    Tracer
                      Speed = 20
                    End
                  ]
                End
                """);
        scene.lists().play("Tracers", EffectLists.Cue.at(new Vector3f()));
        assertTrue(scene.shown().tracers.isEmpty(), "no other end, no streak");

        var target = new Vector3f(0f, 0f, 90f);
        scene.lists().play("Tracers", new EffectLists.Cue(new Vector3f(), null, null, target, 0f));
        assertEquals(List.of(target), scene.shown().tracers);
    }

    @Test
    void aNameThatIsNoListIsNotPlayed() {
        var scene = scene("""
                EffectList
                  Name = Something
                End
                """);
        assertFalse(scene.lists().play("Nothing", EffectLists.Cue.at(new Vector3f())));
        assertTrue(scene.systems().emitters().isEmpty());
    }
}
