package uz.dukeengine.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.event.WeaponWoundUp;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.core.event.ObjectHurt;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.ThingTemplate;

/** A weapon written in place winds up as long as it says, and is told as it begins, its blow landing at the end (E69). */
class WindUpSwingTest {

    @Test
    void aNineFrameWindUpIsToldNineFramesBeforeItsBlow() {
        var world = new CombatWorld();
        world.init();
        var sword = new WeaponUpdate.Data(10f, 30f, 60, DamageType.NORMAL, 0f, true, List.of(), 0, 0, 0, true,
                "Sword", List.of(), null, 0, 9);
        world.getThingFactory().addTemplate(ThingTemplate.named("Knight").module(new ActiveBody.Data(100f))
                .module(sword).build());
        world.getThingFactory().addTemplate(ThingTemplate.named("Skeleton").module(new ActiveBody.Data(100f)).build());
        var players = world.getPlayerList();
        var red = players.addPlayer("Red");
        var blue = players.addPlayer("Blue");
        red.setRelationshipTo(blue, Relationship.ENEMIES);
        blue.setRelationshipTo(red, Relationship.ENEMIES);
        world.spawn(world.findTemplate("Knight"), Coord3D.ZERO, red.getIndex());
        world.spawn(world.findTemplate("Skeleton"), new Coord3D(10f, 0f, 0f), blue.getIndex());

        var woundUp = new ArrayList<Integer>();
        var hurt = new ArrayList<Integer>();
        for (int frame = 0; frame < 20; frame++) {
            world.update();
            for (var event : world.drainEvents()) {
                if (event instanceof WeaponWoundUp wound) {
                    woundUp.add(wound.frame());
                    assertEquals(9, wound.frames());
                } else if (event instanceof ObjectHurt blow) {
                    hurt.add(blow.frame());
                }
            }
        }
        assertEquals(1, woundUp.size(), "one swing begun");
        assertEquals(List.of(woundUp.getFirst() + 9), hurt, "its blow nine frames on");
    }
}
