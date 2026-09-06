package uz.duke.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.duke.core.GameLogic;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.ActiveBody;
import uz.duke.core.module.ModuleFactory;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.KindOf;
import uz.duke.core.thing.ThingFactory;
import uz.duke.core.thing.ThingTemplate;

class AsciiRendererTest {

    static final class TestLogic extends GameLogic {
        TestLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void simulate() {
        }
    }

    private TestLogic logic;
    private ThingFactory thingFactory;
    private AsciiRenderer renderer;
    private int usa;
    private int china;

    @BeforeEach
    void setUp() {
        thingFactory = new ThingFactory(ModuleFactory.withDefaults());
        logic = new TestLogic(thingFactory);
        logic.init();
        usa = logic.getPlayerList().addPlayer("USA").getIndex();
        china = logic.getPlayerList().addPlayer("China").getIndex();
        // 10x10 grid over a 100x100 world -> each cell is 10 units.
        renderer = new AsciiRenderer(10, 10, 100f, 100f);
    }

    private ThingTemplate template(String name, float vision, KindOf... kinds) {
        var t = ThingTemplate.named(name).module("ActiveBody", new ActiveBody.Data(100f)).visionRange(vision);
        for (var k : kinds) {
            t.addKindOf(k);
        }
        return t.build();
    }

    private GameObject spawn(ThingTemplate template, int player, float x, float y) {
        var o = logic.createObject(template);
        o.setPlayerIndex(player);
        o.setPosition(new Coord3D(x, y, 0f));
        return o;
    }

    private static char cell(String frame, int row, int col) {
        return frame.split("\n")[row].charAt(col);
    }

    @Test
    void ownUnitRendersAsUppercaseGlyphAtMappedCell() {
        var tank = template("Tank", 0f, KindOf.VEHICLE);
        thingFactory.addTemplate(tank);
        spawn(tank, usa, 25f, 25f); // -> col 2, row 2

        var frame = renderer.render(logic, usa);
        assertEquals('V', cell(frame, 2, 2));
    }

    @Test
    void enemyHiddenByFogIsNotDrawn() {
        var tank = template("Tank", 0f, KindOf.VEHICLE);
        thingFactory.addTemplate(tank);
        spawn(tank, china, 95f, 95f); // enemy, no friendly vision -> hidden

        var frame = renderer.render(logic, usa);
        assertEquals('.', cell(frame, 9, 9));
    }

    @Test
    void enemyInVisionRendersAsLowercase() {
        var watchtower = template("Tower", 200f, KindOf.STRUCTURE);
        var tank = template("Tank", 0f, KindOf.VEHICLE);
        thingFactory.addTemplate(watchtower);
        thingFactory.addTemplate(tank);

        spawn(watchtower, usa, 5f, 5f);   // sees far (vision 200)
        spawn(tank, china, 95f, 95f);     // enemy, now visible

        var frame = renderer.render(logic, usa);
        assertEquals('B', cell(frame, 0, 0));  // own structure, uppercase
        assertEquals('v', cell(frame, 9, 9));  // enemy vehicle, lowercase
    }

    @Test
    void renderingClientStoresLatestFrame() {
        var tank = template("Tank", 0f, KindOf.VEHICLE);
        thingFactory.addTemplate(tank);
        spawn(tank, usa, 50f, 50f);

        var client = new RenderingGameClient(logic, renderer, usa);
        assertTrue(client.getLastFrame().isEmpty());
        client.update(); // GameClient.update() calls render()
        var frame = client.getLastFrame();
        assertFalse(frame.isEmpty());
        assertTrue(frame.contains("V"));
        assertEquals(1, client.getFrame());
    }
}
