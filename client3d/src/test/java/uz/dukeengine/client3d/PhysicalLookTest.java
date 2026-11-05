package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.post.Filter;
import com.jme3.post.filters.ToneMapFilter;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import java.util.List;
import org.junit.jupiter.api.Test;

class PhysicalLookTest {

    private static final DesktopAssetManager ASSETS = new DesktopAssetManager(true);
    private static final Vector3f TO_SUN = new Vector3f(0f, 1f, 0f);
    private static final ColorRGBA SUN = ColorRGBA.White;
    private static final ColorRGBA AMBIENT = new ColorRGBA(0.5f, 0.5f, 0.5f, 1f);

    private static Material pbr() {
        return new Material(ASSETS, "Common/MatDefs/Light/PBRLighting.j3md");
    }

    @Test
    void theSkyIsBrightestAtTheSunAndDimmestUnderTheGround() {
        var atTheSun = PhysicalLook.skyAt(TO_SUN, TO_SUN, SUN, AMBIENT);
        var horizon = PhysicalLook.skyAt(new Vector3f(1f, 0f, 0f), TO_SUN, SUN, AMBIENT);
        var below = PhysicalLook.skyAt(new Vector3f(0f, -1f, 0f), TO_SUN, SUN, AMBIENT);
        assertTrue(atTheSun.g > horizon.g, "the sun over the horizon");
        assertTrue(horizon.g > below.g, "the horizon over the ground");
        assertEquals(0.25f, below.g, 1e-5f, "under the ground: half the ambient, no sun");
    }

    @Test
    void aFilmModelKeepsItsMapsAndTakesTheLooksColours() {
        var loaded = pbr();
        loaded.setFloat("Metallic", 0.75f);
        var tint = new ColorRGBA(0.5f, 0.25f, 1f, 1f);
        var dressed = Bodies.physicalMaterial(loaded, null, tint);
        assertEquals(0.75f, (Float) dressed.getParamValue("Metallic"), "what the file gave it stays");
        assertEquals(tint, dressed.getParamValue("BaseColor"));
        assertEquals(ColorRGBA.Black, dressed.getParamValue(HitFlash.PARAMETER), "a hit flash has its place to fade to");
        assertTrue(loaded != dressed, "a material per unit: two never share a pose");
    }

    @Test
    void aFlashHasAParameterOnThePhysicalMaterial() {
        assertNotNull(pbr().getMaterialDef().getMaterialParam(HitFlash.PARAMETER));
        assertNotNull(pbr().getMaterialDef().getMaterialParam("SceneAmbient"));
    }

    @Test
    void shadowsMadeLaterAreStillDrawnBeforeTheFinishingFilters() {
        var view = new ViewPort("view", new Camera(16, 16));
        var shadows = new SunShadows(ASSETS, view, new Vector3f(0f, -1f, 0f));
        Filter last = new ToneMapFilter();
        shadows.finishWith(List.of(last));
        var group = shadows.groupFor(0f);
        var order = shadows.post().getFilterList();
        assertSame(group, order.get(0));
        assertSame(last, order.get(1));
    }
}
