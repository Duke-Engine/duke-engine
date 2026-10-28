package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.light.Light;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Infantry and vehicles under one sun lit by scales of their own, as the reference lights them. */
class LightScalesTest {

    private final Node scene = new Node("scene");
    private final DirectionalLight sun = new DirectionalLight(new Vector3f(0f, -1f, 0f), new ColorRGBA(0.5f, 0.4f, 0.3f, 1f));
    private final AmbientLight ambient = new AmbientLight(new ColorRGBA(0.2f, 0.2f, 0.2f, 1f));
    private final DirectionalLight thingSun = new DirectionalLight(new Vector3f(1f, -1f, 0f), new ColorRGBA(0.1f, 0.1f, 0.1f, 1f));
    private final LightScales scales = new LightScales();

    LightScalesTest() {
        scene.addLight(sun);
        scene.addLight(ambient);
        scene.addLight(thingSun);
        scales.follow(sun, ambient, List.of(thingSun));
    }

    /** What a thing is lit by: the colours of every directional and ambient light on it and above it, summed. */
    private static ColorRGBA litBy(Spatial thing) {
        var total = new ColorRGBA(0f, 0f, 0f, 1f);
        for (Spatial at = thing; at != null; at = at.getParent()) {
            for (Light light : at.getLocalLightList()) {
                total.addLocal(light.getColor());
            }
        }
        return total;
    }

    private Node thing(String name, String group, LightScales.Extra[] wearing) {
        var node = new Node(name);
        scene.attachChild(node);
        wearing[0] = scales.dress(node, wearing[0], group);
        return node;
    }

    @Test
    void infantryAndATankUnderOneSunAreLitByDifferentScales() {
        scales.times("infantry", 2f);
        var soldier = thing("soldier", "infantry", new LightScales.Extra[1]);
        var tank = thing("tank", "vehicle", new LightScales.Extra[1]);
        var plain = thing("prop", null, new LightScales.Extra[1]);

        float base = 0.5f + 0.2f + 0.1f; // the sun's red, the ambient's and the thing sun's
        assertEquals(base, litBy(tank).r, 1e-5f, "a group nobody scaled is lit as the scene is");
        assertEquals(base, litBy(plain).r, 1e-5f);
        assertEquals(1f + 0.4f + 0.2f, litBy(soldier).r, 1e-5f, "the sun's red doubled to 1, the ambient's to 0.4, "
                + "the thing sun's to 0.2");
        assertEquals(0.8f + 0.4f + 0.2f, litBy(soldier).g, 1e-5f, "the sun's green doubled to 0.8");
    }

    @Test
    void aScaledChannelIsHeldToOneAsTheReferenceCapsIt() {
        scales.times("infantry", 4f);
        var soldier = thing("soldier", "infantry", new LightScales.Extra[1]);

        assertEquals(1f + 0.8f + 0.4f, litBy(soldier).r, 1e-5f, "the sun's 0.5 held to 1, not 2");
    }

    @Test
    void aScaleBelowOneDimsAndTheHoursSunMovesItWithTheScene() {
        scales.times("infantry", 0.5f);
        var soldier = thing("soldier", "infantry", new LightScales.Extra[1]);
        assertEquals(0.25f + 0.1f + 0.05f, litBy(soldier).r, 1e-5f);

        sun.setColor(new ColorRGBA(0.2f, 0.2f, 0.2f, 1f)); // the hour moves on, the scene's sun with it
        scales.refresh();
        assertEquals(0.1f + 0.1f + 0.05f, litBy(soldier).r, 1e-5f, "half of what the scene now gives");
    }

    @Test
    void aGroupBackAtOneTakesItsExtraLightsOffAndIsLitAsTheSceneIs() {
        scales.times("infantry", 2f);
        var wearing = new LightScales.Extra[1];
        var soldier = thing("soldier", "infantry", wearing);
        assertSame(wearing[0], scales.of("infantry"));

        scales.times("infantry", 1f);
        wearing[0] = scales.dress(soldier, wearing[0], "infantry");

        assertNull(wearing[0]);
        assertEquals(0, soldier.getLocalLightList().size());
        assertEquals(0.8f, litBy(soldier).r, 1e-5f);
    }

    @Test
    void thingsOfAGroupShareTheirLightsAndAThingChangingGroupsSwapsThem() {
        scales.times("infantry", 2f);
        scales.times("vehicle", 3f);
        var wearing = new LightScales.Extra[1];
        var one = thing("one", "infantry", wearing);
        var other = thing("other", "infantry", new LightScales.Extra[1]);
        assertEquals(litBy(one).r, litBy(other).r, 1e-6f);

        wearing[0] = scales.dress(one, wearing[0], "vehicle");
        assertEquals(1f + 0.6f + 0.3f, litBy(one).r, 1e-5f, "now lit as a vehicle, not as both");
        assertEquals(3, one.getLocalLightList().size(), "its three extra lights and nothing of the infantry's");
    }
}
