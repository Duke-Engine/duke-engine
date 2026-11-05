package uz.dukeengine.render;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.jme3.light.DirectionalLight;
import com.jme3.light.LightProbe;
import com.jme3.scene.Node;
import com.jme3.texture.TextureCubeMap;
import java.util.List;
import org.junit.jupiter.api.Test;

/** That the probe the reflections take back is the one PBRLighting has lit with: ready, with its cube, on a scene root. */
class SsrFilterTest {

    private static LightProbe probe(boolean ready, boolean withCube) {
        var probe = new LightProbe();
        probe.setReady(ready);
        if (withCube) {
            probe.setPrefilteredMap(new TextureCubeMap(4, 4, com.jme3.texture.Image.Format.RGB16F));
        }
        return probe;
    }

    @Test
    void theProbeIsTheFirstReadyOneWithACube() {
        var root = new Node("root");
        var other = new Node("other");
        var notReady = probe(false, true);
        var noCube = probe(true, false);
        var good = probe(true, true);
        root.addLight(new DirectionalLight());
        root.addLight(notReady);
        root.addLight(noCube);
        other.addLight(good);
        assertNull(SsrFilter.probeIn(List.of(root)));
        assertSame(good, SsrFilter.probeIn(List.of(root, other)));
    }
}
