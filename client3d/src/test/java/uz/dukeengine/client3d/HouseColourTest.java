package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import org.junit.jupiter.api.Test;

/**
 * Which meshes of a model take their owner's colour.
 *
 * <p>The colour itself is a multiply on a material, which needs an asset manager to make; what is checked
 * here is the part that decides <em>which</em> meshes — the one a mistake in would paint a whole tank red,
 * or none of it.
 */
class HouseColourTest {

    private static Geometry mesh(String name) {
        return new Geometry(name, new Box(1f, 1f, 1f));
    }

    @Test
    void aMeshNamedForItsOwnerIsOneOfTheParts() {
        var body = new Node("tank");
        var hull = mesh("HULL");
        var stripe = mesh("HOUSECOLOR01");
        var turret = mesh("HouseColor02_turret");
        body.attachChild(hull);
        body.attachChild(stripe);
        body.attachChild(turret);

        assertTrue(DukeRtsApp.isHouseColoured(stripe, body, "HOUSECOLOR"));
        assertTrue(DukeRtsApp.isHouseColoured(turret, body, "HOUSECOLOR"), "letter case says nothing");
        assertFalse(DukeRtsApp.isHouseColoured(hull, body, "HOUSECOLOR"), "the rest of it is left alone");
    }

    /** A loader may keep the artist's name on the node and give the geometry under it another. */
    @Test
    void theNameMayBeOnWhatTheMeshHangsUnder() {
        var body = new Node("barracks");
        var marked = new Node("HOUSECOLOR03");
        var inside = mesh("mesh_0");
        marked.attachChild(inside);
        body.attachChild(marked);

        assertTrue(DukeRtsApp.isHouseColoured(inside, body, "HOUSECOLOR"));
    }

    /** Only inside the body: whatever the body hangs under is not part of the model. */
    @Test
    void nothingAboveTheBodyCounts() {
        var world = new Node("HOUSECOLOR_by_accident");
        var body = new Node("tank");
        var hull = mesh("HULL");
        body.attachChild(hull);
        world.attachChild(body);

        assertFalse(DukeRtsApp.isHouseColoured(hull, body, "HOUSECOLOR"));
    }

    /** The prefix is the game's; the engine names none, and a game that gives none paints nothing. */
    @Test
    void theEngineNamesNoPrefixOfItsOwn() {
        assertNull(Visuals.create().getHouseColour());
        assertNull(Visuals.create().houseColour("  ").getHouseColour(), "a blank one is none");
        assertEquals("TEAM_", Visuals.create().houseColour("TEAM_").getHouseColour());
    }

    /** Handed to another side, a thing is painted in the new owner's colour, not the old one's times the new. */
    @Test
    void paintedAgainForANewOwnerItIsTheirColourNotTheLastOwnersTimesTheirs() {
        var assets = new com.jme3.asset.DesktopAssetManager(true);
        var stripe = mesh("HOUSECOLOR01");
        var material = new com.jme3.material.Material(assets, "Common/MatDefs/Light/Lighting.j3md");
        material.setBoolean("UseMaterialColors", true);
        material.setColor("Diffuse", new com.jme3.math.ColorRGBA(0.5f, 0.5f, 0.5f, 1f));
        stripe.setMaterial(material);
        var body = new Node("tank");
        body.attachChild(stripe);

        DukeRtsApp.paintOwner(body, "HOUSECOLOR", com.jme3.math.ColorRGBA.Red);
        DukeRtsApp.paintOwner(body, "HOUSECOLOR", com.jme3.math.ColorRGBA.Blue);
        assertEquals(new com.jme3.math.ColorRGBA(0f, 0f, 0.5f, 1f), material.getParamValue("Diffuse"),
                "grey times blue: the red is gone");
    }
}
