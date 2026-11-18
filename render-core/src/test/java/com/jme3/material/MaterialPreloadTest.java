package com.jme3.material;

import com.jme3.renderer.RenderManager;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;
import com.jme3.system.NullRenderer;
import com.jme3.system.TestUtil;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/** Preloading puts every material's own textures on the card, not only the first one on a shared shader. */
public class MaterialPreloadTest {

    private final List<Texture> set = new ArrayList<>();
    private final RenderManager renders = TestUtil.createRenderManager(new NullRenderer() {
        @Override
        public void setTexture(int unit, Texture texture) {
            set.add(texture);
        }
    });

    @Test
    public void secondMaterialOnTheSameShaderStillSetsItsTexture() {
        var assets = TestUtil.createAssetManager();
        var first = new Texture2D(4, 4, Image.Format.RGBA8);
        var second = new Texture2D(4, 4, Image.Format.RGBA8);
        for (var texture : new Texture[] {first, second}) {
            var material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
            material.setTexture("ColorMap", texture);
            var geometry = new Geometry("box", new Box(1f, 1f, 1f));
            geometry.setMaterial(material);
            material.preload(renders, geometry);
        }
        Assert.assertTrue(set.contains(first));
        Assert.assertTrue("the second material's texture was skipped", set.contains(second));
    }
}
