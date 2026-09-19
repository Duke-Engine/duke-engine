package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.scene.Node;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What a recipe does to the thing wearing it: the body a modelless thing is given, and the pieces
 * of a creature it lights from inside.
 *
 * <p>No window is opened. Materials and nodes are ordinary scene objects until something renders
 * them, which is what lets this be a build failure instead of a slow afternoon.
 */
class ProjectileEffectsTest {

    private static ProjectileEffects effects(Visuals visuals) {
        return new ProjectileEffects(new DesktopAssetManager(true), new Node("root"), visuals, 4);
    }

    /**
     * A projectile the game describes no effect for is given no body, and neither is one whose
     * recipe draws nothing: null is what sends the client off to build its plain shape.
     */
    @Test
    void aShotWithNothingToDrawIsGivenNoBody() {
        var visuals = Visuals.create()
                .effect("Knock", recipe -> recipe.shake(0.2f, 1f))
                .unit("Shot", unit -> unit.effect("NoSuchEffect"))
                .unit("Thump", unit -> unit.effect("Knock"));

        assertNull(effects(visuals).bodyFor(visuals.of("Shot")));
        assertNull(effects(visuals).bodyFor(visuals.of("Thump")));
        assertNull(effects(visuals).bodyFor(null));
    }

    /**
     * A look drawn in layers is given an empty body: its layers are all of it.
     *
     * <p>Not null -- null sends the client off to build a capsule, and a fireball drawn as a
     * capsule with a gun barrel on it is worse than no fireball at all.
     */
    @Test
    void aLookDrawnInLayersIsGivenAnEmptyBody() {
        var visuals = Visuals.create()
                .effect("Orb", recipe -> recipe.layer(EffectLayer.builder().build()))
                .unit("Ball", unit -> unit.effect("Orb"));

        var body = effects(visuals).bodyFor(visuals.of("Ball"));

        assertNotNull(body, "null would be a capsule");
        assertEquals(0, body instanceof Node node ? node.getQuantity() : -1, "and nothing is drawn in it");
    }

    /**
     * Lighting a model's eyes lights its eyes, and nothing else it is made of.
     *
     * <p>A word matched against mesh names. Matching too much would put a skeleton's whole
     * ribcage on fire — which is a thing somebody might want, and is not what "Parts = [Eyes]"
     * says.
     */
    @Test
    void aGlowLightsThosePartsOnly() {
        var visuals = Visuals.create().effect("Embers", recipe -> recipe.glow(List.of("Eyes"), java.awt.Color.ORANGE));
        var assets = new DesktopAssetManager(true);

        var body = new com.jme3.scene.Geometry("Skeleton_Warrior_Body", new com.jme3.scene.shape.Box(1f, 1f, 1f));
        var eyes = new com.jme3.scene.Geometry("Skeleton_Warrior_Eyes", new com.jme3.scene.shape.Box(1f, 1f, 1f));
        var plain = new com.jme3.material.Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        body.setMaterial(plain);
        eyes.setMaterial(plain);
        var model = new Node("skeleton");
        model.attachChild(body);
        model.attachChild(eyes);

        effects(visuals).lightThePartsOf(model, "Embers");

        assertTrue(eyes.getMaterial() != plain, "the eyes kept the material they came with");
        assertEquals(new com.jme3.math.ColorRGBA(1f, 200f / 255f, 0f, 1f), eyes.getMaterial().getParam("Color").getValue(),
                "and are not the colour the recipe asked for");
        assertTrue(body.getMaterial() == plain, "the whole skeleton caught fire");
    }

    /** A recipe with no glow leaves the model it is worn on exactly as it was. */
    @Test
    void noGlowTouchesNothing() {
        var visuals = Visuals.create().effect("Plain", recipe -> recipe.shake(0.1f, 1f));
        var assets = new DesktopAssetManager(true);
        var eyes = new com.jme3.scene.Geometry("Skeleton_Warrior_Eyes", new com.jme3.scene.shape.Box(1f, 1f, 1f));
        var plain = new com.jme3.material.Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        eyes.setMaterial(plain);
        var model = new Node("skeleton");
        model.attachChild(eyes);

        effects(visuals).lightThePartsOf(model, "Plain");
        effects(visuals).lightThePartsOf(model, "NoSuchEffect");

        assertTrue(eyes.getMaterial() == plain);
    }
}
