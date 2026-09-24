package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.texture.image.ImageRaster;
import com.jme3.util.BufferUtils;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Puts a {@link CanvasFrame} on the screen: its triangles in runs of the same picture and blend, a mesh a run, each
 * run a hair nearer than the one before so the GUI's sort keeps them in the order they were drawn — and all of them
 * above everything else in the GUI, so the game's drawing is over the client's HUD.
 *
 * <p>Pictures are loaded once and kept, each with filtering of its own (bilinear, no mipmaps, clamped at its edges,
 * as a screen's pictures want) so that setting it takes nothing from anything else drawing the same file. A
 * picture drawn in grey is a grey copy of it, made the first time and kept.
 */
final class CanvasDrawing {

    private static final Logger LOG = Logger.getLogger(CanvasDrawing.class.getName());

    /** Where the canvas starts in the GUI's depth: above every panel the client draws. */
    static final float Z = 1000f;
    /** How much nearer each run is than the last. */
    private static final float STEP = 0.001f;

    private final AssetManager assets;
    private final CanvasText text;
    private final Node node = new Node("canvas");
    private final Map<String, Texture> pictures = new HashMap<>();
    private final Map<String, int[]> sizes = new HashMap<>();
    private final Map<String, Texture> grays = new HashMap<>();
    private final Set<String> missing = new HashSet<>();
    private final List<Texture2D> pages = new ArrayList<>();
    private final List<Integer> pageVersions = new ArrayList<>();
    private final Map<String, Material> materials = new HashMap<>();
    private final List<Run> runs = new ArrayList<>();

    /** One mesh the canvas keeps and fills again each frame. */
    private static final class Run {
        final Mesh mesh = new Mesh();
        final Geometry geometry;
        int capacity;
        FloatBuffer positions;
        FloatBuffer coordinates;
        FloatBuffer colours;

        Run(int index) {
            geometry = new Geometry("canvas-" + index, mesh);
        }
    }

    CanvasDrawing(AssetManager assets, CanvasText text) {
        this.assets = assets;
        this.text = text;
    }

    Node node() {
        return node;
    }

    /** A picture's size in pixels, loading it the first time; null for one that will not load. */
    int[] sizeOf(String path) {
        return picture(path) == null ? null : sizes.get(path);
    }

    /** Draw this frame, replacing the last. */
    void show(List<CanvasFrame.Triangle> triangles, int screenHeight) {
        uploadPages();
        int used = 0;
        for (int start = 0; start < triangles.size(); ) {
            var first = triangles.get(start);
            int end = start + 1;
            while (end < triangles.size() && triangles.get(end).source().equals(first.source())
                    && triangles.get(end).blend() == first.blend()) {
                end++;
            }
            var material = material(first.source(), first.blend());
            if (material != null) {
                fill(run(used), triangles.subList(start, end), screenHeight, material, used);
                used++;
            }
            start = end;
        }
        for (int unused = used; unused < runs.size(); unused++) {
            runs.get(unused).geometry.setCullHint(Spatial.CullHint.Always);
        }
    }

    private Run run(int index) {
        while (runs.size() <= index) {
            var run = new Run(runs.size());
            node.attachChild(run.geometry);
            runs.add(run);
        }
        return runs.get(index);
    }

    private static void fill(Run run, List<CanvasFrame.Triangle> triangles, int screenHeight, Material material,
            int index) {
        int vertices = triangles.size() * 3;
        if (vertices > run.capacity) {
            run.capacity = Math.max(vertices, run.capacity * 2);
            run.positions = BufferUtils.createFloatBuffer(run.capacity * 3);
            run.coordinates = BufferUtils.createFloatBuffer(run.capacity * 2);
            run.colours = BufferUtils.createFloatBuffer(run.capacity * 4);
        }
        run.positions.clear();
        run.coordinates.clear();
        run.colours.clear();
        for (var triangle : triangles) {
            for (int corner = 0; corner < 3; corner++) {
                // The canvas counts down from the top; the GUI counts up from the bottom, and so do pictures.
                run.positions.put(triangle.x()[corner]).put(screenHeight - triangle.y()[corner]).put(0f);
                run.coordinates.put(triangle.u()[corner]).put(1f - triangle.v()[corner]);
                int argb = triangle.argb()[corner];
                run.colours.put(((argb >>> 16) & 0xFF) / 255f).put(((argb >>> 8) & 0xFF) / 255f)
                        .put((argb & 0xFF) / 255f).put(((argb >>> 24) & 0xFF) / 255f);
            }
        }
        run.positions.flip();
        run.coordinates.flip();
        run.colours.flip();
        run.mesh.setBuffer(VertexBuffer.Type.Position, 3, run.positions);
        run.mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, run.coordinates);
        run.mesh.setBuffer(VertexBuffer.Type.Color, 4, run.colours);
        run.mesh.updateCounts();
        run.mesh.updateBound();
        run.geometry.setMaterial(material);
        run.geometry.setLocalTranslation(0f, 0f, Z + index * STEP);
        run.geometry.setCullHint(Spatial.CullHint.Never);
    }

    private Material material(CanvasFrame.Source source, Canvas.Blend blend) {
        var key = source + "|" + blend;
        var known = materials.get(key);
        if (known != null) {
            return known;
        }
        Texture texture = switch (source) {
            case CanvasFrame.Plain plain -> null;
            case CanvasFrame.Picture picture -> picture.gray() ? gray(picture.path()) : picture(picture.path());
            case CanvasFrame.Glyphs glyphs -> glyphs.page() < pages.size() ? pages.get(glyphs.page()) : null;
        };
        if (texture == null && !(source instanceof CanvasFrame.Plain)) {
            return null;
        }
        var material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setBoolean("VertexColor", true);
        if (texture != null) {
            material.setTexture("ColorMap", texture);
        }
        var state = material.getAdditionalRenderState();
        state.setBlendMode(switch (blend) {
            case ALPHA, GRAYSCALE -> RenderState.BlendMode.Alpha;
            case ADDITIVE -> RenderState.BlendMode.Additive;
            case SOLID -> RenderState.BlendMode.Off;
        });
        state.setDepthTest(false);
        state.setDepthWrite(false);
        state.setFaceCullMode(RenderState.FaceCullMode.Off);
        materials.put(key, material);
        return material;
    }

    private Texture picture(String path) {
        var known = pictures.get(path);
        if (known != null || missing.contains(path)) {
            return known;
        }
        try {
            var loaded = assets.loadTexture(path).clone();
            loaded.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
            loaded.setMagFilter(Texture.MagFilter.Bilinear);
            loaded.setWrap(Texture.WrapMode.EdgeClamp);
            pictures.put(path, loaded);
            sizes.put(path, new int[]{loaded.getImage().getWidth(), loaded.getImage().getHeight()});
            return loaded;
        } catch (RuntimeException e) {
            missing.add(path);
            LOG.warning("the canvas has no picture " + path + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * The picture in grey, blended by its alpha: its luminance by the weights of Rec. 601 (0.299, 0.587, 0.114), made
     * once. A picture whose pixels cannot be read here — a compressed one — is drawn in its colours, and said once.
     */
    private Texture gray(String path) {
        var known = grays.get(path);
        if (known != null) {
            return known;
        }
        var colour = picture(path);
        if (colour == null) {
            return null;
        }
        var image = colour.getImage();
        int width = image.getWidth();
        int height = image.getHeight();
        Texture made;
        try {
            var raster = ImageRaster.create(image);
            var data = BufferUtils.createByteBuffer(width * height * 4);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    var pixel = raster.getPixel(x, y);
                    byte luma = (byte) Math.clamp(Math.round((0.299f * pixel.r + 0.587f * pixel.g
                            + 0.114f * pixel.b) * 255f), 0, 255);
                    data.put(luma).put(luma).put(luma).put((byte) Math.clamp(Math.round(pixel.a * 255f), 0, 255));
                }
            }
            data.flip();
            made = texture(new Image(Image.Format.RGBA8, width, height, data, ColorSpace.sRGB));
        } catch (RuntimeException e) {
            LOG.warning("the canvas cannot grey " + path + " (" + e.getMessage() + "); drawing it in colour");
            made = colour;
        }
        grays.put(path, made);
        return made;
    }

    /** Every page of glyphs, handed to the card again whenever glyphs were added to it. */
    private void uploadPages() {
        var drawn = text.pages();
        for (int index = 0; index < drawn.size(); index++) {
            var page = drawn.get(index);
            if (index < pages.size() && pageVersions.get(index) == page.version) {
                continue;
            }
            var data = pixels(page);
            if (index < pages.size()) {
                var image = pages.get(index).getImage();
                image.setData(0, data);
                image.setUpdateNeeded();
                pageVersions.set(index, page.version);
            } else {
                pages.add(texture(new Image(Image.Format.RGBA8, CanvasText.PAGE, CanvasText.PAGE, data,
                        ColorSpace.sRGB)));
                pageVersions.add(page.version);
            }
        }
    }

    /** A page's pixels, bottom row first as the card counts them. */
    private static ByteBuffer pixels(CanvasText.Page page) {
        int[] argb = ((DataBufferInt) page.image.getRaster().getDataBuffer()).getData();
        var data = BufferUtils.createByteBuffer(CanvasText.PAGE * CanvasText.PAGE * 4);
        for (int y = CanvasText.PAGE - 1; y >= 0; y--) {
            for (int x = 0; x < CanvasText.PAGE; x++) {
                int pixel = argb[y * CanvasText.PAGE + x];
                data.put((byte) (pixel >>> 16)).put((byte) (pixel >>> 8)).put((byte) pixel).put((byte) (pixel >>> 24));
            }
        }
        return data.flip();
    }

    private static Texture2D texture(Image image) {
        var texture = new Texture2D(image);
        texture.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        texture.setMagFilter(Texture.MagFilter.Bilinear);
        texture.setWrap(Texture.WrapMode.EdgeClamp);
        return texture;
    }
}
