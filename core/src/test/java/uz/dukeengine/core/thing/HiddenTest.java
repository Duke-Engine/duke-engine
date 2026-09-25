package uz.dukeengine.core.thing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.Concealment;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleFactory;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.player.Relationship;

/** A thing hidden from everyone while it runs, and one kept from some players by a module of its own. */
class HiddenTest {

    /** Hidden from one player while it says so. */
    static final class KeptFrom extends Module implements Concealment {
        int from = -1;

        KeptFrom(GameObject owner) {
            super(owner);
        }

        @Override
        public boolean hiddenFrom(int player) {
            return player == from;
        }
    }

    private GameLogic world;
    private int mine;
    private int theirs;

    @BeforeEach
    void setUp() {
        var factory = new ThingFactory(ModuleFactory.withDefaults());
        factory.addTemplate(ObjectTemplate.named("Parachute").visionRange(100f).module(new ActiveBody.Data(10f))
                .module(new MoveUpdate.Data(30f)).build());
        factory.addTemplate(ObjectTemplate.named("Tank").visionRange(150f).module(new ActiveBody.Data(100f))
                .build());
        world = new GameLogic(factory) {
            @Override
            protected void simulate() {
            }
        };
        world.init();
        mine = world.getPlayerList().addPlayer("Mine").getIndex();
        theirs = world.getPlayerList().addPlayer("Theirs").getIndex();
        world.getPlayerList().getPlayer(mine).setRelationshipTo(world.getPlayerList().getPlayer(theirs),
                Relationship.ENEMIES);
        world.getPlayerList().getPlayer(theirs).setRelationshipTo(world.getPlayerList().getPlayer(mine),
                Relationship.ENEMIES);
    }

    @Test
    void aHiddenThingIsSeenByNobodyItsOwnSideIncludedYetMovesAndIsSeenAgainOnceShown() {
        var chute = world.spawn(world.findTemplate("Parachute"), new Coord3D(100f, 100f, 0f), mine);
        world.spawn(world.findTemplate("Tank"), new Coord3D(120f, 100f, 0f), theirs); // eyes on it
        long shown = world.checksum();
        chute.setStatus(ObjectStatus.HIDDEN);
        chute.getLocomotor().moveTo(new Coord3D(100f, 200f, 0f));

        for (int frame = 0; frame < 30; frame++) {
            world.update();
        }

        assertFalse(world.getVisibleObjects(mine).contains(chute), "not even by its own side");
        assertFalse(world.getVisibleObjects(theirs).contains(chute));
        assertTrue(chute.getPosition().y() > 120f, "its modules run: it moved");
        assertNotEquals(shown, world.checksum(), "hidden is in the checksum");
        assertFalse(world.canSee(mine, new Coord3D(100f, 260f, 0f)), "and it lends its side no sight");

        chute.clearStatus(ObjectStatus.HIDDEN);
        assertTrue(world.getVisibleObjects(mine).contains(chute), "shown: there again");
    }

    @Test
    void aThingKeptFromOnePlayerIsSeenByItsOwnSideAndNotByThem() {
        var hidden = world.spawn(world.findTemplate("Tank"), new Coord3D(100f, 100f, 0f), mine);
        world.spawn(world.findTemplate("Tank"), new Coord3D(120f, 100f, 0f), theirs);
        var stealth = new KeptFrom(hidden);
        hidden.addModule(stealth);

        stealth.from = theirs;
        assertTrue(world.canSee(mine, hidden));
        assertFalse(world.canSee(theirs, hidden), "kept from them, eyes on it or not");

        stealth.from = mine;
        assertTrue(world.canSee(mine, hidden), "never from its own side");

        stealth.from = -1;
        assertTrue(world.canSee(theirs, hidden), "detected: seen again");
    }
}
