package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;

/**
 * The one sun and the one flat ambient the whole scene is lit by, as the game asked for them — see {@link Sunlight} —
 * the sun's shadows, and the hour the game eases them through.
 *
 * <p>Read once and kept, rather than asked of the visuals at each of the places that want them: they are read while
 * materials are built, which is often, and the answer cannot change while a scene stands.
 *
 * <p>They were three constants until a player said the map looked flat. It did, and this is where: a sun far enough
 * from vertical is the only thing that tells a floor from the top of a wall, since the two are the same tile with the
 * same normal, and how far is a decision about how a game should look rather than a fact about drawing one.
 *
 * <p>Light is drawing. Nothing in the simulation reads it.
 */
final class SceneLight {

    private final Node rootNode;
    private Vector3f sunDirection;
    private ColorRGBA sunColour;
    private ColorRGBA ambientColour;
    /** The sunlight the scene is lit by now, so a world that asks for the same one changes nothing. */
    private Sunlight litBy;
    /** The two scene lights, kept so a new world can relight them rather than add a second sun. */
    private DirectionalLight sun;
    private AmbientLight ambient;
    /** The sun's shadows — see {@link Duke3D#shadows} — and whether they are drawn, and in what colour. */
    private SunShadows sunShadows;
    private boolean shadowsOn = true;
    private ColorRGBA shadowColour = SunShadows.DEFAULT_COLOUR;
    /** The world's sun on its way from one hour to the next — see {@link Duke3D#sunlight}; null before any was asked. */
    private HourOfDay hour;
    /** The ground's sun parameters laid over every material under the root at once. */
    private com.jme3.material.MatParamOverride sunDirectionOverride;
    private com.jme3.material.MatParamOverride ambientShiftOverride;
    private com.jme3.material.MatParamOverride sunShiftOverride;
    /** The scene's lights scaled for the groups of things lit more or less brightly; see {@link LightScales}. */
    private final LightScales scales = new LightScales();

    SceneLight(Sunlight light, Node rootNode) {
        this.rootNode = rootNode;
        this.litBy = light;
        this.sunDirection = light.direction();
        this.sunColour = light.sunColour();
        this.ambientColour = light.ambientColour();
    }

    Vector3f sunDirection() {
        return sunDirection;
    }

    ColorRGBA sunColour() {
        return sunColour;
    }

    ColorRGBA ambientColour() {
        return ambientColour;
    }

    LightScales scales() {
        return scales;
    }

    /** The sun's shadows, or null before the scene is lit. */
    SunShadows shadows() {
        return sunShadows;
    }

    /** The scene lit: the sun, the ambient and the sun's shadows, and the suns the game lights only its things by. */
    void install(AssetManager assetManager, ViewPort viewPort, java.util.List<Visuals.Sun> thingSuns) {
        sun = new DirectionalLight(sunDirection, sunColour);
        ambient = new AmbientLight(ambientColour);
        // Everything drawn takes the sun's shadows; what casts them its look says.
        rootNode.setShadowMode(com.jme3.renderer.queue.RenderQueue.ShadowMode.Receive);
        sunShadows = new SunShadows(assetManager, viewPort, sunDirection);
        sunShadows.on(shadowsOn);
        sunShadows.colour(shadowColour);
        rootNode.addLight(sun);
        rootNode.addLight(ambient);
        var thingLights = new java.util.ArrayList<DirectionalLight>();
        for (var more : thingSuns) {
            var thingLight = new DirectionalLight(more.direction(), more.light()); // the things', not the ground's
            thingLights.add(thingLight);
            rootNode.addLight(thingLight);
        }
        scales.follow(sun, ambient, thingLights);
    }

    /**
     * Take up whatever sun the game says the world now being built is lit by.
     *
     * <p>A map is a place and a time. It used to be read once, when the application was made — before
     * any map was chosen — so every match was lit by one sun, and a night map was drawn at noon. Read
     * instead where the world is built, which is the moment the game knows which world it is: the same
     * moment {@code Visuals.world(...)} is said, for the same reason.
     *
     * <p>Everything lit is built after this — the ground, the tiles, every unit that arrives — and reads
     * the sun as it now stands, so what is relit here is only the two scene lights themselves. A world
     * that asks for the sun it already has changes nothing, which is every game that sets its sun once.
     */
    void adopt(Sunlight light) {
        if (light.equals(litBy)) {
            return;
        }
        litBy = light;
        sunDirection = light.direction();
        sunColour = light.sunColour();
        ambientColour = light.ambientColour();
        if (sun != null) {
            sun.setDirection(sunDirection);
            sun.setColor(sunColour);
        }
        if (sunShadows != null) {
            sunShadows.sun(sunDirection);
        }
        if (ambient != null) {
            ambient.setColor(ambientColour);
        }
        lightTheHour(); // a world built anew keeps the game's hour over its own sun
        scales.refresh();
    }

    void shadows(boolean on) {
        this.shadowsOn = on;
        if (sunShadows != null) {
            sunShadows.on(on);
        }
    }

    void shadowColour(java.awt.Color colour) {
        this.shadowColour = colour == null ? SunShadows.DEFAULT_COLOUR : Materials.colour(colour);
        if (sunShadows != null) {
            sunShadows.colour(shadowColour);
        }
    }

    void sunlight(Sunlight light, float seconds) {
        if (light == null) {
            return;
        }
        if (hour == null) {
            hour = new HourOfDay(new HourOfDay.Sun(sunDirection.clone(), sunColour.clone(), ambientColour.clone()));
        }
        hour.toward(HourOfDay.Sun.of(light), seconds);
        lightTheHour();
    }

    /** The hour's sun easing on, on the frames it moves. */
    void step(float tpf) {
        if (hour != null && hour.step(tpf)) {
            lightTheHour();
        }
    }

    /** The hour's sun on the lights, the shadows and the painted ground, as far as it has eased. */
    private void lightTheHour() {
        if (hour == null) {
            return;
        }
        var now = hour.now();
        if (sun != null) {
            sun.setDirection(now.direction());
            sun.setColor(now.colour());
        }
        if (ambient != null) {
            ambient.setColor(now.ambient());
        }
        scales.refresh();
        if (sunShadows != null) {
            sunShadows.sun(now.direction());
        }
        // The ground's materials were built with the sun of their world: told the hour's share of it, all at once.
        if (sunDirectionOverride == null) {
            sunDirectionOverride = new com.jme3.material.MatParamOverride(com.jme3.shader.VarType.Vector3,
                    "SunDirection", now.direction().clone());
            ambientShiftOverride = new com.jme3.material.MatParamOverride(com.jme3.shader.VarType.Vector4,
                    "AmbientShift", HourOfDay.shift(now.ambient(), ambientColour));
            sunShiftOverride = new com.jme3.material.MatParamOverride(com.jme3.shader.VarType.Vector4,
                    "SunShift", HourOfDay.shift(now.colour(), sunColour));
            rootNode.addMatParamOverride(sunDirectionOverride);
            rootNode.addMatParamOverride(ambientShiftOverride);
            rootNode.addMatParamOverride(sunShiftOverride);
        } else {
            sunDirectionOverride.setValue(now.direction().clone());
            ambientShiftOverride.setValue(HourOfDay.shift(now.ambient(), ambientColour));
            sunShiftOverride.setValue(HourOfDay.shift(now.colour(), sunColour));
        }
    }
}
