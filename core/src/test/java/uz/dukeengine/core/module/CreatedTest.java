package uz.dukeengine.core.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ObjectTemplate;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

/** A module told its thing is made: once, knowing its owner, place and facing, before the thing's first update. */
class CreatedTest {

    /** Born a veteran: the word put on at creation, and what it saw then. */
    static final class BornVeteran extends Module {
        record Data() implements ModuleData {
        }

        final List<String> heard = new ArrayList<>();

        BornVeteran(GameObject owner) {
            super(owner);
        }

        @Override
        public void onCreated() {
            var owner = getOwner();
            owner.setCondition("VETERAN");
            heard.add(owner.getPlayerIndex() + " " + owner.getPosition().x() + " " + owner.getOrientation());
        }
    }

    /** Looks at the word on every update, from the first. */
    static final class Reader extends UpdateModule {
        record Data() implements ModuleData {
        }

        final List<Boolean> veteran = new ArrayList<>();

        Reader(GameObject owner) {
            super(owner);
        }

        @Override
        public void update() {
            veteran.add(getOwner().hasCondition("VETERAN"));
        }
    }

    /** Makes a veteran from inside its own update, the frame it is told to. */
    static final class Maker extends UpdateModule {
        record Data() implements ModuleData {
        }

        static ThingTemplate kind;
        static GameObject made;

        Maker(GameObject owner) {
            super(owner);
        }

        @Override
        public void update() {
            if (made == null) {
                made = getOwner().getWorld().spawn(kind, new Coord3D(5f, 5f, 0f), 2);
            }
        }
    }

    private GameLogic world;
    private ThingTemplate veteran;

    @BeforeEach
    void setUp() {
        var modules = ModuleFactory.withDefaults()
                .register(BornVeteran.Data.class, (owner, data) -> new BornVeteran(owner))
                .register(Reader.Data.class, (owner, data) -> new Reader(owner))
                .register(Maker.Data.class, (owner, data) -> new Maker(owner));
        var factory = new ThingFactory(modules);
        veteran = ObjectTemplate.named("Ranger").module(new BornVeteran.Data()).module(new Reader.Data()).build();
        factory.addTemplate(veteran);
        factory.addTemplate(ObjectTemplate.named("Barracks").module(new Maker.Data()).build());
        world = new GameLogic(factory) {
            @Override
            protected void simulate() {
            }
        };
        world.init();
        Maker.kind = veteran;
        Maker.made = null;
    }

    @Test
    void itIsToldOnceAfterItsOwnerPlaceAndFacingAreSetAndBeforeItsFirstUpdate() {
        var ranger = world.spawn(veteran, new Coord3D(40f, 0f, 0f), 1);
        ranger.setOrientation(1.5f); // set by whatever made it, after the spawn

        world.update();
        world.update();

        var born = ranger.findModule(BornVeteran.class);
        assertEquals(List.of("1 40.0 1.5"), born.heard, "once, with its owner, place and facing");
        assertEquals(List.of(true, true), ranger.findModule(Reader.class).veteran, "a veteran from its first update");
    }

    @Test
    void oneMadeMidFrameIsToldByTheEndOfThatFrame() {
        world.spawn(world.getThingFactory().findTemplate("Barracks"), new Coord3D(0f, 0f, 0f), 2);

        world.update(); // the barracks makes a ranger from its update

        assertTrue(Maker.made.hasCondition("VETERAN"), "the frame it was made, before anything draws it");
        assertEquals(List.of(), Maker.made.findModule(Reader.class).veteran, "and it has not updated yet");
    }

    @Test
    void oneBroughtBackFromASaveIsNotToldAgain() {
        var ranger = world.restoreObject(veteran, new ObjectId(7));

        world.update();

        assertEquals(List.of(), ranger.findModule(BornVeteran.class).heard, "made long ago; its words came back with it");
    }
}
