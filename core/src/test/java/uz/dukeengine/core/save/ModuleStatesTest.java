package uz.dukeengine.core.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleFactory;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

/** A thing's modules written into a save and put back from it: every one named, none left out without a word. */
class ModuleStatesTest {

    /** Counts its frames: state a fresh one would not have. */
    static class Counter extends UpdateModule implements Saveable {
        record Data(int step) implements ModuleData {
        }

        private final int step;
        private int count;

        Counter(GameObject owner, Data data) {
            super(owner);
            this.step = data.step();
        }

        @Override
        public void update() {
            count += step;
        }

        @Override
        public void save(SaveOut out) {
            out.writeInt(count);
        }

        @Override
        public void load(SaveIn in) {
            count = in.readInt();
        }
    }

    /** A counter that keeps a field of its own its inherited save does not write. */
    static final class CounterWithMore extends Counter {
        record Data() implements ModuleData {
        }

        private int more;

        CounterWithMore(GameObject owner, Data data) {
            super(owner, new Counter.Data(1));
        }

        @Override
        public void update() {
            super.update();
            more++;
        }
    }

    /** Holds nothing, and says so. */
    @Stateless
    static final class Idle extends Module {
        record Data() implements ModuleData {
        }

        Idle(GameObject owner, Data data) {
            super(owner);
        }
    }

    /** Holds a number and says nothing of it: what a save must refuse. */
    static final class Forgetful extends UpdateModule {
        record Data() implements ModuleData {
        }

        private int frames;

        Forgetful(GameObject owner, Data data) {
            super(owner);
        }

        @Override
        public void update() {
            frames++;
        }
    }

    /** An order put on a thing while the match runs: made again by its reviver. */
    static final class Errand extends UpdateModule implements Saveable {
        private final Coord3D to;
        private final ObjectId with;
        private int walked;

        Errand(GameObject owner, Coord3D to, ObjectId with) {
            super(owner);
            this.to = to;
            this.with = with;
        }

        @Override
        public void update() {
            walked++;
        }

        @Override
        public void save(SaveOut out) {
            out.writePlace(to).writeId(with).writeInt(walked);
        }

        @Override
        public void load(SaveIn in) {
            throw new AssertionError("made by its reviver");
        }

        static Errand revive(GameObject owner, SaveIn in) {
            var errand = new Errand(owner, in.readPlace(), in.readId());
            errand.walked = in.readInt();
            return errand;
        }
    }

    private static final class World extends GameLogic {
        World(ThingFactory factory) {
            super(factory);
        }

        @Override
        protected void simulate() {
        }
    }

    private static final ThingTemplate TANK = ThingTemplate.named("Tank").module(new Idle.Data())
            .module(new Counter.Data(2)).module(new Idle.Data()).module(new Counter.Data(5)).build();

    private static World world(ModuleData... more) {
        var modules = new ModuleFactory().register(Counter.Data.class, Counter::new)
                .register(Idle.Data.class, Idle::new).register(Forgetful.Data.class, Forgetful::new)
                .register(CounterWithMore.Data.class, CounterWithMore::new)
                .revives(Errand.class, Errand::revive);
        var factory = new ThingFactory(modules);
        factory.addTemplate(TANK);
        var template = ThingTemplate.named("Other").module(new Idle.Data());
        for (var data : more) {
            template.module(data);
        }
        factory.addTemplate(template.build());
        var world = new World(factory);
        world.init();
        return world;
    }

    private static String saved(GameObject thing) {
        return ModuleStates.save(thing, ((GameLogic) thing.getWorld()).getThingFactory().getModuleFactory());
    }

    private static void load(World world, GameObject into, String line) {
        ModuleStates.load(into, line, world.getThingFactory().getModuleFactory());
    }

    @Test
    void eachModuleIsWrittenByItsSlotAndBroughtBackWithWhatItHeld() {
        var played = world();
        var tank = played.spawn(TANK, new Coord3D(10f, 10f, 0f), 0);
        for (int i = 0; i < 3; i++) {
            played.update();
        }
        var line = saved(tank);
        assertEquals("0 " + Idle.class.getName() + "|1 " + Counter.class.getName()
                + " 6|2 " + Idle.class.getName() + "|3 " + Counter.class.getName() + " 15", line);

        var loaded = world();
        var again = loaded.restoreObject(TANK, tank.getId());
        load(loaded, again, line);
        assertEquals(line, saved(again));
        assertSame(again.templateModules().get(1), again.getModules().get(1), "the template's own, not a new one");
    }

    @Test
    void aModuleAddedWhileTheMatchRanIsMadeAgainByItsReviverAndOneTakenOffStaysOff() {
        var played = world();
        var tank = played.spawn(TANK, new Coord3D(10f, 10f, 0f), 0);
        var other = played.spawn(played.getThingFactory().findTemplate("Other"), Coord3D.ZERO, 0);
        tank.removeModule(tank.getModules().get(1));
        tank.addModule(new Errand(tank, new Coord3D(1f, 2f, 3f), other.getId()));
        played.update();
        var line = saved(tank);
        assertTrue(line.endsWith("|+ " + Errand.class.getName() + " 1.0,2.0,3.0 " + other.getId().value() + " 1"), line);

        var loaded = world();
        var again = loaded.restoreObject(TANK, tank.getId());
        load(loaded, again, line);
        assertEquals(List.of(Idle.class, Idle.class, Counter.class, Errand.class),
                again.getModules().stream().map(Object::getClass).toList());
        assertEquals(line, saved(again));
    }

    @Test
    void aModuleNeitherSaveableNorStatelessIsRefusedByNameAtTheSave() {
        var world = world(new Forgetful.Data());
        var thing = world.spawn(world.getThingFactory().findTemplate("Other"), Coord3D.ZERO, 0);
        var refused = assertThrows(UnsavedModuleException.class, () -> saved(thing));
        assertEquals(Forgetful.class.getName(), refused.module());
        assertTrue(refused.getMessage().contains("Other"), refused.getMessage());
    }

    @Test
    void aSubclassKeepingFieldsItsInheritedSaveDoesNotWriteIsRefused() {
        var world = world(new CounterWithMore.Data());
        var thing = world.spawn(world.getThingFactory().findTemplate("Other"), Coord3D.ZERO, 0);
        var refused = assertThrows(UnsavedModuleException.class, () -> saved(thing));
        assertEquals(CounterWithMore.class.getName(), refused.module());
    }

    @Test
    void aModuleAddedWithNoReviverIsRefusedAtTheSave() {
        var world = world();
        var tank = world.spawn(TANK, Coord3D.ZERO, 0);
        tank.addModule(new Counter(tank, new Counter.Data(1)));
        var refused = assertThrows(UnsavedModuleException.class, () -> saved(tank));
        assertTrue(refused.getMessage().contains("reviver"), refused.getMessage());
    }

    @Test
    void aSaveReadDifferentlyFromHowItWasWrittenIsRefused() {
        var world = world();
        var tank = world.restoreObject(TANK, new ObjectId(1));
        var counter = Counter.class.getName();
        var extra = assertThrows(UnsavedModuleException.class, () -> load(world, tank, "1 " + counter + " 6 7"));
        assertTrue(extra.getMessage().contains("read 1 of its 2 values"), extra.getMessage());
        var moved = assertThrows(UnsavedModuleException.class, () -> load(world, tank, "2 " + counter + " 6"));
        assertTrue(moved.getMessage().contains("no longer makes there"), moved.getMessage());
        var unknown = assertThrows(UnsavedModuleException.class, () -> load(world, tank, "+ " + counter + " 6"));
        assertTrue(unknown.getMessage().contains("no reviver"), unknown.getMessage());
        assertThrows(UnsavedModuleException.class, () -> load(world, tank, "1 " + counter + " six"));
    }

    @Test
    void theFactoryNamesEveryModuleItBuildsThatASaveWouldRefuse() {
        var factory = world().getThingFactory().getModuleFactory();
        assertEquals(List.of("Forgetful"), factory.unsaved(),
                "CounterWithMore inherits a save, which the save itself checks against its fields");
    }

    @Test
    void everyKindOfValueReadsBackAsItWasWritten() {
        var out = new SaveOut().writeInt(-7).writeLong(1L << 40).writeFloat(-0f).writeFloat(Float.NaN)
                .writeFloat(0.1f).writeDouble(1e-300).writeBoolean(true).writeBoolean(false)
                .writeString("a b|c%d\ne\r").writeString("").writeString(null).writeEnum(Thread.State.BLOCKED)
                .writeEnum(null).writeId(new ObjectId(9)).writeId(null).writePlace(new Coord3D(1.5f, -2f, 3e-8f))
                .writePlace(null).writeStrings(List.of("x", "y z"));
        assertTrue(out.text().indexOf('|') < 0 && out.text().indexOf('\n') < 0, out.text());
        var in = new SaveIn(out.text(), null, "test");
        assertEquals(-7, in.readInt());
        assertEquals(1L << 40, in.readLong());
        assertEquals(Float.floatToIntBits(-0f), Float.floatToIntBits(in.readFloat()));
        assertTrue(Float.isNaN(in.readFloat()));
        assertEquals(0.1f, in.readFloat());
        assertEquals(1e-300, in.readDouble());
        assertTrue(in.readBoolean());
        assertEquals(false, in.readBoolean());
        assertEquals("a b|c%d\ne\r", in.readString());
        assertEquals("", in.readString());
        assertNull(in.readString());
        assertEquals(Thread.State.BLOCKED, in.readEnum(Thread.State.class));
        assertNull(in.readEnum(Thread.State.class));
        assertEquals(new ObjectId(9), in.readId());
        assertNull(in.readId());
        assertEquals(new Coord3D(1.5f, -2f, 3e-8f), in.readPlace());
        assertNull(in.readPlace());
        assertEquals(List.of("x", "y z"), in.readStrings());
        in.expectEnd();
    }
}
