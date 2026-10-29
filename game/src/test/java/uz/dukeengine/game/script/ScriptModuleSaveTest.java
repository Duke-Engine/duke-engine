package uz.dukeengine.game.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleFactory;
import uz.dukeengine.core.save.ModuleStates;
import uz.dukeengine.core.save.SaveIn;
import uz.dukeengine.core.save.SaveOut;
import uz.dukeengine.core.save.Saveable;
import uz.dukeengine.core.save.Stateless;
import uz.dukeengine.core.save.UnsavedModuleException;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

/** A unit's script saved as a module is: its own fields by its own save, nothing for one that holds none, else refused. */
class ScriptModuleSaveTest {

    /** Counts its frames, and writes them. */
    public static final class Counting extends UnitScript implements Saveable {
        public record Data() implements ModuleData {
        }

        int frames;

        @Override
        public void onUpdate() {
            frames++;
        }

        @Override
        public void save(SaveOut out) {
            out.writeInt(frames);
        }

        @Override
        public void load(SaveIn in) {
            frames = in.readInt();
        }
    }

    /** Holds nothing. */
    @Stateless
    public static final class Idle extends UnitScript {
        public record Data() implements ModuleData {
        }

        @Override
        public void onUpdate() {
        }
    }

    /** Counts its frames and says nothing of them. */
    public static final class Forgetful extends UnitScript {
        public record Data() implements ModuleData {
        }

        int frames;

        @Override
        public void onUpdate() {
            frames++;
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

    private static World world() {
        var modules = new ModuleFactory();
        ScriptModule.registerScript(modules, Counting.Data.class, Counting::new);
        ScriptModule.registerScript(modules, Idle.Data.class, Idle::new);
        ScriptModule.registerScript(modules, Forgetful.Data.class, Forgetful::new);
        var factory = new ThingFactory(modules);
        factory.addTemplate(ThingTemplate.named("Scripted").module(new Counting.Data()).module(new Idle.Data()).build());
        factory.addTemplate(ThingTemplate.named("Forgetful").module(new Forgetful.Data()).build());
        var world = new World(factory);
        world.init();
        return world;
    }

    @Test
    void aScriptThatWritesItsFieldsIsBroughtBackWithThem() {
        var played = world();
        var thing = played.spawn(played.findTemplate("Scripted"), Coord3D.ZERO, 0);
        for (int i = 0; i < 4; i++) {
            played.update();
        }
        var factory = played.getThingFactory().getModuleFactory();
        var line = ModuleStates.save(thing, factory);

        var loaded = world();
        var again = loaded.restoreObject(loaded.findTemplate("Scripted"), thing.getId());
        ModuleStates.load(again, line, factory);
        assertEquals(line, ModuleStates.save(again, factory));
        assertEquals(List.of("Forgetful"), factory.unsaved(), "the script's own class is what is asked");
    }

    @Test
    void aScriptNeitherSaveableNorStatelessIsRefusedByItsName() {
        var world = world();
        var thing = world.spawn(world.findTemplate("Forgetful"), Coord3D.ZERO, 0);
        var refused = assertThrows(UnsavedModuleException.class,
                () -> ModuleStates.save(thing, world.getThingFactory().getModuleFactory()));
        assertEquals(Forgetful.class.getName(), refused.module());
    }
}
