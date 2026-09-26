package uz.dukeengine.client3d;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;

/**
 * The drag box, outlined in the width and colour the game names — the reference's two pixels of {@code 0x9933FF33}
 * ({@code W3DInGameUI::drawSelectionRegion}) — or the client's own single pixel of pale green. Each side a band of the
 * width laid along the rectangle's edge, a quad, since a core-profile window draws no line wider than a pixel.
 */
final class DragBox {

    /** The client's own: one pixel, pale green. */
    static final Visuals.DragBoxLook PLAIN = new Visuals.DragBoxLook(1f, 0xE680FF80);

    private final Geometry outline;

    DragBox(Visuals.DragBoxLook look, Material unshaded) {
        var drawn = look == null ? PLAIN : look;
        var mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, new float[16 * 3]);
        var indices = new short[4 * 6];
        for (int side = 0; side < 4; side++) {
            int corner = side * 4;
            System.arraycopy(new short[] {(short) corner, (short) (corner + 1), (short) (corner + 2), (short) corner,
                (short) (corner + 2), (short) (corner + 3)}, 0, indices, side * 6, 6);
        }
        mesh.setBuffer(VertexBuffer.Type.Index, 3, indices);
        mesh.setDynamic();
        mesh.updateBound();
        outline = new Geometry("drag-box", mesh);
        int argb = drawn.colour();
        unshaded.setColor("Color", new ColorRGBA(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f, ((argb >>> 24) & 0xFF) / 255f));
        if (look != null) {
            unshaded.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        }
        outline.setMaterial(unshaded);
        outline.setUserData("width", drawn.width());
        hide();
    }

    Geometry geometry() {
        return outline;
    }

    /** Outlined from one corner of the drag to the other, in the window's pixels. */
    void lay(float fromX, float fromY, float toX, float toY) {
        float width = outline.getUserData("width");
        var mesh = outline.getMesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, outline(fromX, fromY, toX, toY, width));
        mesh.updateBound();
        outline.setCullHint(Spatial.CullHint.Never);
    }

    void hide() {
        outline.setCullHint(Spatial.CullHint.Always);
    }

    /** The four bands of a rectangle's outline, {@code width} wide and centred on its edges: sixteen corners. */
    static float[] outline(float fromX, float fromY, float toX, float toY, float width) {
        float left = Math.min(fromX, toX);
        float right = Math.max(fromX, toX);
        float bottom = Math.min(fromY, toY);
        float top = Math.max(fromY, toY);
        float half = width / 2f;
        float[][] bands = {
            {left - half, bottom - half, right + half, bottom + half},
            {left - half, top - half, right + half, top + half},
            {left - half, bottom + half, left + half, top - half},
            {right - half, bottom + half, right + half, top - half},
        };
        var corners = new float[16 * 3];
        for (int side = 0; side < 4; side++) {
            var band = bands[side];
            float[] quad = {band[0], band[1], 0f, band[2], band[1], 0f, band[2], band[3], 0f, band[0], band[3], 0f};
            System.arraycopy(quad, 0, corners, side * 12, 12);
        }
        return corners;
    }
}
