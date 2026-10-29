package uz.dukeengine.rts.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.module.AutoHealUpdate;
import uz.dukeengine.core.Sleep;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.Kind;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.rts.RtsLogic;
import uz.dukeengine.rts.RtsTemplate;

/** What a world keeps beside its things — who sleeps, its sides' stances, bonuses and prices, its caps — saved. */
class WorldStateResumeTest {

    private static RtsLogic rules() {
        var logic = new RtsLogic();
        logic.getThingFactory().addTemplate(RtsTemplate.named("Hero").geometry(new Geometry.Cylinder(3f, 3f))
                .module(new ActiveBody.Data(100f)).build());
        logic.getThingFactory().addTemplate(RtsTemplate.named("Soldier").kindOf(Kind.of("INFANTRY"))
                .geometry(new Geometry.Cylinder(3f, 3f)).module(new ActiveBody.Data(100f))
                .module(new AutoHealUpdate.Data(6f)).buildCost(300).build());
        logic.init();
        logic.setSleep(new Sleep(thing -> thing.getTemplate().name().equals("Hero"), 2, 30));
        return logic;
    }

    private static void setup(RtsLogic logic) {
        var red = logic.getPlayerList().addPlayer("Red");
        var blue = logic.getPlayerList().addPlayer("Blue");
        red.setRelationshipTo(blue, Relationship.ENEMIES);
        blue.setRelationshipTo(red, Relationship.ALLIES);
        logic.spawn(logic.findTemplate("Hero"), new Coord3D(10f, 10f, 0f), red.getIndex());
        logic.spawn(logic.findTemplate("Soldier"), new Coord3D(20f, 10f, 0f), red.getIndex()).getBody().damage(50f);
        logic.spawn(logic.findTemplate("Soldier"), new Coord3D(900f, 900f, 0f), red.getIndex()).getBody().damage(50f);
        var side = logic.getRtsPlayer(red.getIndex());
        side.addPriceChange(Kind.of("INFANTRY"), -0.25f);
        side.addPriceChange(Kind.of("INFANTRY"), -0.25f);
        side.setBonus("Armour", 1.5f);
        logic.setCap("Superweapon", 1);
    }

    @Test
    void aSleeperStaysAsleepUntilTheRulesOwnFrameAsInTheSavedWorld() {
        SaveLoadCheck.resumes(WorldStateResumeTest::rules, WorldStateResumeTest::setup, (logic, frame) -> {
            if (frame == 12) {
                logic.findObject(new ObjectId(1)).setPosition(new Coord3D(890f, 900f, 0f));
            }
        }, 15, 60);
    }

    @Test
    void aSidesStancesBonusesAndPricesAndTheCapsComeBack() {
        var played = rules();
        setup(played);
        played.update();
        var loaded = rules();
        var saved = GameSnapshot.save(played);
        GameSnapshot.load(saved, loaded);

        assertEquals(Relationship.ENEMIES, loaded.getPlayerList().getPlayer(1).getRelationshipTo(
                loaded.getPlayerList().getPlayer(2)));
        assertEquals(Relationship.ALLIES, loaded.getPlayerList().getPlayer(2).getRelationshipTo(
                loaded.getPlayerList().getPlayer(1)));
        assertEquals(saved, GameSnapshot.save(loaded), "the caps and the rest saved again the same");
        var red = loaded.getRtsPlayer(1);
        assertEquals(225, red.priceOf(loaded.findTemplate("Soldier")), "300 a quarter off, given twice, counted once");
        red.removePriceChange(Kind.of("INFANTRY"), -0.25f);
        assertEquals(225, red.priceOf(loaded.findTemplate("Soldier")), "given twice, it holds once taken away once");
        red.removePriceChange(Kind.of("INFANTRY"), -0.25f);
        assertEquals(300, red.priceOf(loaded.findTemplate("Soldier")), "and is gone the second time");
        assertEquals(1.5f, red.getBonus("Armour"));
        assertTrue(loaded.isAsleep(loaded.findObject(new ObjectId(3))), "the far soldier sleeps");
        assertFalse(loaded.isAsleep(loaded.findObject(new ObjectId(2))), "the near one is awake");
    }
}
