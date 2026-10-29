package uz.dukeengine.rts.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.message.CombatOrder;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.save.UnsavedModuleException;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.rts.RtsLogic;
import uz.dukeengine.rts.RtsTemplate;

/** The snapshot as a whole: its version, what it refuses, and what it carries beside the things' own state. */
class SnapshotFormatTest {

    /** A game's module that keeps a number and says nothing of it. */
    static final class Forgetful extends Module {
        record Data() implements ModuleData {
        }

        int kept;

        Forgetful(GameObject owner, Data data) {
            super(owner);
        }
    }

    private static RtsLogic rules() {
        var logic = new RtsLogic();
        logic.getThingFactory().getModuleFactory().register(Forgetful.Data.class, Forgetful::new);
        logic.getThingFactory().addTemplate(RtsTemplate.named("Tank").geometry(new Geometry.Cylinder(3f, 3f))
                .module(new ActiveBody.Data(100f)).module(new MoveUpdate.Data(40f)).build());
        logic.getThingFactory().addTemplate(RtsTemplate.named("Odd").module(new ActiveBody.Data(10f))
                .module(new Forgetful.Data()).build());
        logic.init();
        logic.setPathGrid(new PathGrid(40, 40));
        return logic;
    }

    private static RtsLogic played() {
        var logic = rules();
        int red = logic.getPlayerList().addPlayer("Red").getIndex();
        logic.spawn(logic.findTemplate("Tank"), new Coord3D(40f, 40f, 0f), red);
        logic.spawn(logic.findTemplate("Tank"), new Coord3D(80f, 40f, 0f), red);
        for (int i = 0; i < 5; i++) {
            logic.update();
        }
        return logic;
    }

    @Test
    void theTextSaysItsVersionAndAnotherVersionIsRefusedBeforeAnythingChanges() {
        var text = GameSnapshot.save(played());
        assertTrue(text.startsWith("SNAPSHOT " + GameSnapshot.VERSION + "\n"), text);
        assertEquals(GameSnapshot.VERSION, GameSnapshot.versionOf(text));

        var loaded = played();
        var before = GameSnapshot.save(loaded);
        var old = assertThrows(IllegalArgumentException.class,
                () -> GameSnapshot.load(text.substring(text.indexOf('\n') + 1), loaded));
        assertTrue(old.getMessage().contains("version 1"), old.getMessage());
        assertThrows(IllegalArgumentException.class, () -> GameSnapshot.load(text.replaceFirst("SNAPSHOT " + GameSnapshot.VERSION,
                "SNAPSHOT " + (GameSnapshot.VERSION + 1)), loaded));
        assertEquals(before, GameSnapshot.save(loaded), "refused before anything was changed");
    }

    @Test
    void aGamesModuleThatCannotBeWrittenRefusesTheWholeSaveByName() {
        var logic = played();
        logic.spawn(logic.findTemplate("Odd"), Coord3D.ZERO, 0);
        logic.update();
        var refused = assertThrows(UnsavedModuleException.class, () -> GameSnapshot.save(logic));
        assertEquals(Forgetful.class.getName(), refused.module());
        assertEquals(List.of("Forgetful"), logic.getThingFactory().getModuleFactory().unsaved());
    }

    @Test
    void aThingMadeSinceTheLastFrameIsToldItIsMadeByTheNextAsInTheSavedWorld() {
        var played = played();
        var made = played.spawn(played.findTemplate("Tank"), new Coord3D(120f, 120f, 0f), 1);
        var loaded = rules();
        GameSnapshot.load(GameSnapshot.save(played), loaded);
        assertTrue(!loaded.findObject(made.getId()).isAnnounced());
        played.update();
        loaded.update();
        assertTrue(loaded.findObject(made.getId()).isAnnounced());
        assertEquals(GameSnapshot.save(played), GameSnapshot.save(loaded));
    }

    @Test
    void ordersQueuedForTheNextFrameAreCarriedAndAppliedThere() {
        var played = played();
        var tanks = played.getObjects().stream().map(GameObject::getId).toList();
        played.issueCommand(new CombatOrder.MoveTo(1, tanks, new Coord3D(250f, 300f, 0f), true));
        var text = GameSnapshot.save(played);
        assertTrue(text.contains("\nCOMMANDS "), text);
        var loaded = rules();
        GameSnapshot.load(text, loaded);
        for (int i = 0; i < 200; i++) {
            played.update();
            loaded.update();
            assertEquals(played.checksum(), loaded.checksum(), "frame " + played.getFrame());
        }
        assertEquals(GameSnapshot.save(played), GameSnapshot.save(loaded));
    }

    @Test
    void aThingMarkedToGoGoesOnTheFrameAfterTheLoadAsInTheSavedWorld() {
        var played = played();
        var doomed = played.getObjects().getFirst();
        doomed.markDestroyed();
        var loaded = rules();
        GameSnapshot.load(GameSnapshot.save(played), loaded);
        assertTrue(loaded.findObject(doomed.getId()).isDestroyed());
        played.update();
        loaded.update();
        assertNull(loaded.findObject(doomed.getId()));
        assertEquals(played.checksum(), loaded.checksum());
        assertEquals(GameSnapshot.save(played), GameSnapshot.save(loaded));
    }

    /** A game's own ledger outside the world: a count of what it has heard and who heard it last. */
    static final class Ledger implements uz.dukeengine.core.save.Saveable {
        int heard;
        GameObject last;

        @Override
        public void save(uz.dukeengine.core.save.SaveOut out) {
            out.writeInt(heard).writeThing(last);
        }

        @Override
        public void load(uz.dukeengine.core.save.SaveIn in) {
            heard = in.readInt();
            last = in.readThing();
        }
    }

    @Test
    void theGamesOwnPartsAreWrittenLastAndReadBackOnceTheThingsAreInTheWorld() {
        var playedLedger = new Ledger();
        var played = played();
        played.saveAlso("ledger", playedLedger);
        playedLedger.heard = 7;
        playedLedger.last = played.getObjects().get(1);
        var text = GameSnapshot.save(played);
        assertTrue(text.endsWith("GAME ledger|7 " + playedLedger.last.getId().value() + "\n"), text);

        var loadedLedger = new Ledger();
        var loaded = rules();
        loaded.saveAlso("ledger", loadedLedger);
        loadedLedger.heard = 99; // a reset keeps the part: it is read back over, not made again
        GameSnapshot.load(text, loaded);
        assertEquals(7, loadedLedger.heard);
        assertEquals(playedLedger.last.getId(), loadedLedger.last.getId(), "found once every thing is back");
        assertEquals(text, GameSnapshot.save(loaded));

        var unkept = assertThrows(IllegalArgumentException.class, () -> GameSnapshot.load(text, rules()));
        assertTrue(unkept.getMessage().contains("ledger"), unkept.getMessage());
        var more = rules();
        more.saveAlso("ledger", new Ledger());
        more.saveAlso("other", new Ledger());
        var unread = assertThrows(IllegalArgumentException.class, () -> GameSnapshot.load(text, more));
        assertTrue(unread.getMessage().contains("other"), unread.getMessage());
        assertThrows(IllegalArgumentException.class, () -> more.saveAlso("other", new Ledger()), "given once");
        assertThrows(IllegalArgumentException.class, () -> more.saveAlso("two words", new Ledger()));
    }

    /** A module that puts a word and a status on its thing as it is made. */
    @uz.dukeengine.core.save.Stateless
    static final class Packed extends Module {
        record Data() implements ModuleData {
        }

        Packed(GameObject owner, Data data) {
            super(owner);
            owner.setCondition("PACKED");
            owner.setStatus(uz.dukeengine.core.thing.ObjectStatus.UNSELECTABLE);
        }
    }

    @Test
    void aThingIsLoadedWithExactlyTheWordsAndStatusesItWasSavedWith() {
        java.util.function.Supplier<RtsLogic> rules = () -> {
            var logic = new RtsLogic();
            logic.getThingFactory().getModuleFactory().register(Packed.Data.class, Packed::new);
            logic.getThingFactory().addTemplate(RtsTemplate.named("Drone").module(new ActiveBody.Data(10f))
                    .module(new Packed.Data()).build());
            logic.init();
            return logic;
        };
        var played = rules.get();
        var drone = played.spawn(played.findTemplate("Drone"), Coord3D.ZERO, 0);
        drone.clearCondition("PACKED");
        drone.setCondition("MENDING");
        drone.clearStatus(uz.dukeengine.core.thing.ObjectStatus.UNSELECTABLE);
        var text = GameSnapshot.save(played);
        var loaded = rules.get();
        GameSnapshot.load(text, loaded);
        var again = loaded.findObject(drone.getId());
        assertEquals(java.util.Set.of("MENDING"), again.getConditions());
        assertTrue(!again.hasStatus(uz.dukeengine.core.thing.ObjectStatus.UNSELECTABLE));
        assertEquals(text, GameSnapshot.save(loaded));
    }
}
