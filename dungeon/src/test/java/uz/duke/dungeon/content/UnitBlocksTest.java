package uz.duke.dungeon.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.core.thing.Solid;
import uz.duke.dungeon.Dungeon;

/**
 * A unit is one block — {@code Monster}, {@code Hero}, {@code Projectile}, {@code Prop} —
 * and a world builds it as the record its type names, with the dungeon's part of the same
 * block joined to it.
 */
class UnitBlocksTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    @Test
    void aWorldBuildsEachUnitAsTheRecordItsBlockNames() {
        var game = Dungeon.world(".....\n.....\n.....\n", SETTINGS).game();
        game.runHeadless(1);
        var factory = game.getLogic().getThingFactory();

        var brute = assertInstanceOf(Monster.class, factory.findTemplate("Brute"));
        assertEquals(SETTINGS.monster("Brute"), brute.kind(), "the dungeon's part is the settings' own kind");
        assertFalse(brute.modules().isEmpty(), "and the engine's part has its modules");

        var rogue = assertInstanceOf(Hero.class, factory.findTemplate("Rogue"));
        assertEquals("Rogue", rogue.look().name());

        // An arrow has no body to hit and sees nothing, but it could: a flare would.
        var arrow = assertInstanceOf(Projectile.class, factory.findTemplate("Arrow"));
        assertEquals(uz.duke.core.thing.Geometry.POINT, Solid.of(arrow), "an arrow takes up no room");
        assertEquals(0f, arrow.visionRange());

        assertInstanceOf(Prop.class, factory.findTemplate("Pillar"));
    }

    /** A monster may frame a face of its own, as a hero does. */
    @Test
    void aMonsterBlockMayFrameItsOwnPortrait() {
        var settings = DungeonSettings.parse("""
                Monster Warden
                  PortraitYaw = -12
                End
                """);

        var face = settings.portraits().stream().filter(art -> art.name().equals("Warden")).findFirst();
        assertTrue(face.isPresent(), "the Warden's block framed no portrait");
        assertEquals(-12f, face.get().yaw(), 0.001f);
    }
}
