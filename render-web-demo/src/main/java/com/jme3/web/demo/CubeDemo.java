package com.jme3.web.demo;

import com.jme3.app.SimpleApplication;
import com.jme3.app.state.AppState;
import com.jme3.font.BitmapFont;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;
import com.jme3.system.web.WebSystem;

/** A lit cube that turns: Space stops and starts it, a click changes its colour. */
public class CubeDemo extends SimpleApplication {
    private static final ColorRGBA[] COLOURS = {ColorRGBA.Orange, ColorRGBA.Cyan, ColorRGBA.Magenta};
    private Geometry box;
    private Material material;
    private boolean turning = true;
    private int colour;

    public CubeDemo() {
        super(new AppState[0]); // the stats and fly-camera states want a bitmap font, which has no loader here yet
    }

    public static void main(String[] args) {
        WebSystem.install();
        CubeDemo app = new CubeDemo();
        app.setAssetManager(WebSystem.assetManager());
        app.setSettings(WebSystem.settings());
        app.setShowSettings(false);
        app.start();
    }

    @Override
    protected BitmapFont loadGuiFont() {
        return null;
    }

    @Override
    public void simpleInitApp() {
        viewPort.setBackgroundColor(new ColorRGBA(0.1f, 0.12f, 0.16f, 1));
        cam.setLocation(new Vector3f(0, 1.5f, 6));
        cam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
        box = new Geometry("box", new Box(1, 1, 1));
        material = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        material.setBoolean("UseMaterialColors", true);
        material.setColor("Ambient", ColorRGBA.White);
        material.setColor("Diffuse", COLOURS[0]);
        box.setMaterial(material);
        rootNode.attachChild(box);
        rootNode.addLight(new DirectionalLight(new Vector3f(-1, -2, -3).normalizeLocal()));
        rootNode.addLight(new AmbientLight(ColorRGBA.White.mult(0.2f)));

        inputManager.addMapping("turn", new KeyTrigger(KeyInput.KEY_SPACE));
        inputManager.addMapping("colour", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addListener((ActionListener) (name, pressed, tpf) -> {
            if (!pressed) {
                return;
            }
            if (name.equals("turn")) {
                turning = !turning;
            } else {
                colour = (colour + 1) % COLOURS.length;
                material.setColor("Diffuse", COLOURS[colour]);
            }
            System.out.println(name + ": turning " + turning + ", colour " + colour);
        }, "turn", "colour");
    }

    @Override
    public void simpleUpdate(float tpf) {
        if (turning) {
            box.rotate(0.4f * tpf, tpf, 0);
        }
    }
}
