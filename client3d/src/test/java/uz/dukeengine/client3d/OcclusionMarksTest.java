package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A marked thing carries its owner's colour for the silhouette, and gives it up when it is no longer marked. */
class OcclusionMarksTest {

    @Test
    void aMarkedThingWearsItsOwnersColourUntilLetGo() {
        var marks = new OcclusionMarks(new DesktopAssetManager(true)); // the material's definition reads
        var tank = new Node("tank");
        marks.mark(Map.of(tank, ColorRGBA.Red));

        assertEquals(1, tank.getLocalMatParamOverrides().size());
        assertEquals(new ColorRGBA(1f, 0f, 0f, OcclusionMarks.ALPHA), tank.getLocalMatParamOverrides().getFirst()
                .getValue());
        marks.mark(Map.of());
        assertTrue(tank.getLocalMatParamOverrides().isEmpty());
    }
}
