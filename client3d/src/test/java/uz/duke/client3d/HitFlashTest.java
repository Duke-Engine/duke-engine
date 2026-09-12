package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.MatParamOverride;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;
import org.junit.jupiter.api.Test;

/**
 * A creature that is hit flashes, the flash goes, and nothing is left over it.
 *
 * <p>What cannot be checked here is whether the flash is the right amount of
 * white. What can be is everything a flash must not do: miss the parameter the
 * creature's material actually has, stack, or stay.
 */
class HitFlashTest {

    private static final ColorRGBA OWN = new ColorRGBA(0.55f, 0.5f, 0.45f, 1f);

    private static HitFlash flash(float seconds, float strength) {
        return new HitFlash(new Visuals.HitFlashLook(0xFFFFFF, seconds, strength));
    }

    private static ColorRGBA shown(Node root) {
        assertEquals(1, root.getLocalMatParamOverrides().size(), "one flash over it");
        return (ColorRGBA) root.getLocalMatParamOverrides().get(0).getValue();
    }

    /**
     * The flash is laid over a parameter a creature's material really has.
     *
     * <p>jME passes over an override whose name or type the material does not
     * have, silently -- so a flash aimed at the wrong parameter is a flash that
     * never shows and never complains.
     */
    @Test
    void theFlashIsLaidOverSomethingACreatureWears() {
        var material = new Material(new DesktopAssetManager(true),
                "Common/MatDefs/Light/Lighting.j3md");
        var parameter = material.getMaterialDef().getMaterialParam(HitFlash.PARAMETER);
        assertNotNull(parameter, "Lighting.j3md has no " + HitFlash.PARAMETER);

        var root = new Node("skeleton");
        flash(0.2f, 1f).struck(3, root, OWN);

        MatParamOverride laid = root.getLocalMatParamOverrides().get(0);
        assertEquals(parameter.getVarType(), laid.getVarType(), "and of the same kind");
    }

    @Test
    void aCreatureThatIsHitGoesWhiteAndComesBack() {
        var flash = flash(0.2f, 1f);
        var root = new Node("skeleton");

        flash.struck(3, root, OWN);
        assertEquals(1f, shown(root).r, 0.001f, "white the instant it is hit");

        flash.update(0.1f);
        float halfway = shown(root).g;
        assertTrue(halfway < 1f && halfway > OWN.g, "on its way back: " + halfway);

        flash.update(0.11f);
        assertTrue(root.getLocalMatParamOverrides().isEmpty(), "and nothing over it once it is over");
        assertEquals(0, flash.flashingCount());
    }

    @Test
    void aSecondBlowStartsTheFlashAgainRatherThanStackingOne() {
        var flash = flash(0.2f, 1f);
        var root = new Node("skeleton");

        flash.struck(3, root, OWN);
        flash.update(0.15f);
        flash.struck(3, root, OWN);

        assertEquals(1f, shown(root).r, 0.001f, "from the top");
        flash.update(0.15f);
        assertEquals(1, flash.flashingCount(), "still the second blow's");
    }

    @Test
    void aFileThatSaysNoFlashFlashesNothing() {
        var root = new Node("skeleton");

        flash(0.2f, 0f).struck(3, root, OWN);
        flash(0f, 1f).struck(3, root, OWN);

        assertTrue(root.getLocalMatParamOverrides().isEmpty());
    }

    @Test
    void clearingTakesEveryFlashOff() {
        var flash = flash(0.5f, 1f);
        var one = new Node("one");
        var two = new Node("two");
        flash.struck(1, one, OWN);
        flash.struck(2, two, OWN);

        flash.clear();

        assertTrue(one.getLocalMatParamOverrides().isEmpty());
        assertTrue(two.getLocalMatParamOverrides().isEmpty());
        assertEquals(0, flash.flashingCount());
    }
}
