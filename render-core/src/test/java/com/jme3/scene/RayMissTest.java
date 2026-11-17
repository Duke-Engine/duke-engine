package com.jme3.scene;

import com.jme3.collision.CollisionResults;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.scene.shape.Box;
import org.junit.Assert;
import org.junit.Test;

/** A ray that passes a geometry by leaves its mesh without a collision tree, and one aimed at it still hits. */
public class RayMissTest {

    private static final class Counted extends Box {
        int trees;

        Counted() {
            super(0.5f, 0.5f, 0.5f);
        }

        @Override
        public void createCollisionData() {
            trees++;
            super.createCollisionData();
        }
    }

    @Test
    public void aRayPastTheBoundBuildsNoTreeAndOneAtItHits() {
        var mesh = new Counted();
        var box = new Geometry("box", mesh);
        box.setLocalTranslation(10f, 0f, 0f);
        box.updateGeometricState();

        var hits = new CollisionResults();
        box.collideWith(new Ray(Vector3f.ZERO, Vector3f.UNIT_Z), hits);
        Assert.assertEquals(0, hits.size());
        Assert.assertEquals(0, mesh.trees);

        box.collideWith(new Ray(Vector3f.ZERO, Vector3f.UNIT_X), hits);
        Assert.assertTrue(hits.size() > 0);
        Assert.assertEquals(1, mesh.trees);
    }
}
