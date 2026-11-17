package uz.dukeengine.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jme3.light.PointLight;
import com.jme3.scene.Node;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocalShadowLightsTest {

    @Test
    void aLightOnAGamesOwnNodeDownTheTreeIsFoundWithTheRootsOwn() {
        var root = new Node("root");
        var layer = new Node("layer");
        var room = new Node("room");
        root.attachChild(layer);
        layer.attachChild(room);
        var top = new PointLight();
        var lamp = new PointLight();
        var deep = new PointLight();
        root.addLight(top);
        layer.addLight(lamp);
        room.addLight(deep);
        assertEquals(List.of(top, lamp, deep), LocalShadowFilter.lightsUnder(root, new ArrayList<>()));
    }
}
