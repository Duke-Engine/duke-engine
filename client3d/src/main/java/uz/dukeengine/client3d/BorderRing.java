package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;

/**
 * Ground past the map's playable edge — the reference's border, drawn under the black shroud: a ring of cells round the
 * grid whose heights carry its edge's on outward, never explored and so drawn in the unexplored colour, never picked,
 * ordered to or seen across. A picture of the ground only; the simulation's grid is unchanged.
 */
final class BorderRing {

    private final Node node;
    private final Material look;
    private Geometry drawn;

    BorderRing(Node node, AssetManager assets) {
        this.node = node;
        this.look = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        look.getAdditionalRenderState().setFaceCullMode(com.jme3.material.RenderState.FaceCullMode.Off);
    }

    /** {@code cells} of ring round {@code ground}, laid again; none for 0. */
    void lay(DrawnGround ground, int cells) {
        clear();
        if (cells <= 0 || ground.cellSize() <= 0f) {
            return;
        }
        drawn = new Geometry("border", mesh(ground, cells));
        drawn.setMaterial(look);
        node.attachChild(drawn);
    }

    /** The unexplored colour the ring is drawn in now. */
    void colour(ColorRGBA unexplored) {
        look.setColor("Color", unexplored);
    }

    static Mesh mesh(DrawnGround ground, int cells) {
        float size = ground.cellSize();
        int wide = ground.columns() + 2 * cells;
        int deep = ground.rows() + 2 * cells;
        float mapWide = ground.columns() * size;
        float mapDeep = ground.rows() * size;
        int quads = wide * deep - ground.columns() * ground.rows();
        var positions = BufferUtils.createFloatBuffer(quads * 4 * 3);
        var indices = BufferUtils.createIntBuffer(quads * 6);
        int at = 0;
        for (int cy = -cells; cy < ground.rows() + cells; cy++) {
            for (int cx = -cells; cx < ground.columns() + cells; cx++) {
                if (cx >= 0 && cy >= 0 && cx < ground.columns() && cy < ground.rows()) {
                    continue; // the playable grid is the ground's own
                }
                float[][] corners = {{cx, cy}, {cx, cy + 1}, {cx + 1, cy + 1}, {cx + 1, cy}};
                for (var corner : corners) {
                    float x = corner[0] * size;
                    float z = corner[1] * size;
                    // Its edge's height carried straight out: the nearest point of the map.
                    float y = ground.heightAt(Math.clamp(x, 0f, mapWide), Math.clamp(z, 0f, mapDeep));
                    positions.put(x).put(y).put(z);
                }
                indices.put(at).put(at + 1).put(at + 2).put(at).put(at + 2).put(at + 3);
                at += 4;
            }
        }
        var mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, positions.flip());
        mesh.setBuffer(VertexBuffer.Type.Index, 3, indices.flip());
        mesh.updateBound();
        return mesh;
    }

    void clear() {
        if (drawn != null) {
            drawn.removeFromParent();
            drawn = null;
        }
    }
}
