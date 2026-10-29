package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import uz.dukeengine.game.Colour;

/** The plain materials and colours the client's own drawing is made of. */
final class Materials {

    private Materials() {
    }

    /** One flat colour, unlit. */
    static Material unshaded(AssetManager assets, ColorRGBA colour) {
        var material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", colour);
        return material;
    }

    /** One picture, unlit. */
    static Material picture(AssetManager assets, com.jme3.texture.Texture picture) {
        var material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setTexture("ColorMap", picture);
        return material;
    }

    /** One colour, lit by the scene's lights. */
    static Material lit(AssetManager assets, ColorRGBA colour) {
        var material = new Material(assets, "Common/MatDefs/Light/Lighting.j3md");
        material.setBoolean("UseMaterialColors", true);
        material.setColor("Diffuse", colour);
        material.setColor("Ambient", colour.mult(0.7f));
        return material;
    }

    /** A colour the game named, opaque. */
    static ColorRGBA colour(Colour colour) {
        return new ColorRGBA(colour.red() / 255f, colour.green() / 255f, colour.blue() / 255f, 1f);
    }

    /** A colour packed as {@code 0xRRGGBB}, opaque; whatever is above the low 24 bits is ignored. */
    static ColorRGBA rgb(int packed) {
        return new ColorRGBA((packed >> 16 & 0xFF) / 255f, (packed >> 8 & 0xFF) / 255f, (packed & 0xFF) / 255f, 1f);
    }
}
