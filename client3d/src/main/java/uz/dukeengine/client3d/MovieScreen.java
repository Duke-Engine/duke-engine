package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;

/**
 * Where a movie is shown: one picture, stretched over the window or into its rectangle, above the world and the HUD
 * and just under the game's canvas. Each new picture goes into the one texture, which is made again only when a
 * picture is a different size.
 */
final class MovieScreen {

    /** Just under the canvas, so the game can draw over a movie. */
    private static final float Z = CanvasDrawing.Z - 1f;

    private final Geometry sheet = new Geometry("movie", new Quad(1f, 1f));
    private final Material material;
    private Texture2D picture;

    MovieScreen(AssetManager assets, Node gui) {
        material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Off);
        material.getAdditionalRenderState().setDepthTest(false);
        material.getAdditionalRenderState().setDepthWrite(false);
        sheet.setMaterial(material);
        sheet.setCullHint(Spatial.CullHint.Always);
        gui.attachChild(sheet);
    }

    /** Put this picture up. */
    void show(MovieFrames.Frame frame) {
        var image = picture == null ? null : picture.getImage();
        if (image != null && image.getWidth() == frame.width() && image.getHeight() == frame.height()) {
            image.setData(0, frame.pixels());
            image.setUpdateNeeded();
            return;
        }
        picture = new Texture2D(new Image(Image.Format.RGB8, frame.width(), frame.height(), frame.pixels(),
                ColorSpace.sRGB));
        picture.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        picture.setMagFilter(Texture.MagFilter.Bilinear);
        picture.setWrap(Texture.WrapMode.EdgeClamp);
        material.setTexture("ColorMap", picture);
    }

    /** Stretch it over the window, or into the movie's rectangle, and show it once it has a picture. */
    void place(Movie movie, int screenWidth, int screenHeight) {
        float width = movie.wholeWindow() ? screenWidth : movie.width();
        float height = movie.wholeWindow() ? screenHeight : movie.height();
        float left = movie.wholeWindow() ? 0f : movie.x();
        float top = movie.wholeWindow() ? 0f : movie.y();
        // The movie's rectangle counts down from the top; the GUI counts up from the bottom.
        sheet.setLocalScale(width, height, 1f);
        sheet.setLocalTranslation(left, screenHeight - top - height, Z);
        sheet.setCullHint(picture == null ? Spatial.CullHint.Always : Spatial.CullHint.Never);
    }

    /** Take it down. */
    void hide() {
        sheet.setCullHint(Spatial.CullHint.Always);
        picture = null;
    }
}
