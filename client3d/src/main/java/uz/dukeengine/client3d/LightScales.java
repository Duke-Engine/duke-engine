package uz.dukeengine.client3d;

import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The scene's suns lit brighter or dimmer for some of its things — {@link Visuals.UnitVisual#lightGroup}, the
 * reference's {@code InfantryLight*Scale} ({@code RTS3DScene::updateFixedLightEnvironments}): the global lights' diffuse
 * and ambient times a scale, each channel held to 1, so infantry is easier to see at evening and night.
 *
 * <p>A light in jME reaches every spatial under the node it hangs on, so a group's scale is a second set of the
 * scene's lights hung on each thing of the group, each as bright as the difference between the scaled light and the
 * scene's own: the thing is then lit by exactly the scaled one while the rest of the scene is lit as before. They are
 * shared by the whole group and follow the scene's lights as the hour moves them ({@link #refresh}).
 *
 * <p>Light is drawing. Nothing in the simulation reads it.
 */
final class LightScales {

    /** The extra lights worn by one group's things: the sun's, the ambient's and each thing sun's. */
    static final class Extra {
        private final DirectionalLight sun = new DirectionalLight();
        private final AmbientLight ambient = new AmbientLight();
        private final List<DirectionalLight> more = new ArrayList<>();
    }

    private final Map<String, Float> times = new HashMap<>();
    private final Map<String, Extra> extras = new HashMap<>();
    private DirectionalLight sun;
    private AmbientLight ambient;
    private List<DirectionalLight> more = List.of();

    /** The scene's own lights, which every group's are made from. */
    void follow(DirectionalLight sun, AmbientLight ambient, List<DirectionalLight> more) {
        this.sun = sun;
        this.ambient = ambient;
        this.more = List.copyOf(more);
        refresh();
    }

    /** Every thing of {@code group} lit {@code scale} times as brightly; 1 is as the scene is lit. */
    void times(String group, float scale) {
        times.put(group, Math.max(0f, scale));
        refresh();
    }

    float times(String group) {
        return times.getOrDefault(group, 1f);
    }

    /** The lights worn by things of {@code group} now, or null while it is lit as the scene is. */
    Extra of(String group) {
        return group == null || times(group) == 1f ? null : extras.computeIfAbsent(group, _ -> new Extra());
    }

    /** The extra lights brought up to the scene's lights as they stand and the scales as they are. */
    void refresh() {
        if (sun == null) {
            return;
        }
        for (var entry : extras.entrySet()) {
            var extra = entry.getValue();
            float scale = times(entry.getKey());
            extra.sun.setDirection(sun.getDirection());
            extra.sun.setColor(difference(sun.getColor(), scale));
            extra.ambient.setColor(difference(ambient.getColor(), scale));
            while (extra.more.size() < more.size()) {
                extra.more.add(new DirectionalLight());
            }
            for (int i = 0; i < more.size(); i++) {
                extra.more.get(i).setDirection(more.get(i).getDirection());
                extra.more.get(i).setColor(difference(more.get(i).getColor(), scale));
            }
        }
    }

    /** What a thing of a group wears now, put on {@code node} in place of {@code was}; returns what it now wears. */
    Extra dress(Node node, Extra was, String group) {
        var now = of(group);
        if (now == was) {
            return was;
        }
        if (was != null) {
            node.removeLight(was.sun);
            node.removeLight(was.ambient);
            was.more.forEach(node::removeLight);
        }
        if (now != null) {
            refresh();
            node.addLight(now.sun);
            node.addLight(now.ambient);
            now.more.forEach(node::addLight);
        }
        return now;
    }

    /** A light {@code scale} times as bright, each channel held to 1, less the light itself: what to add to it. */
    static ColorRGBA difference(ColorRGBA light, float scale) {
        return new ColorRGBA(Math.min(1f, light.r * scale) - light.r, Math.min(1f, light.g * scale) - light.g,
                Math.min(1f, light.b * scale) - light.b, 1f);
    }
}
