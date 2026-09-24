package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A model's pieces shown and hidden by the words a thing holds, sticky as the reference's, kept through a swap. */
class PiecesTest {

    /** A Humvee: its turret, its TOW turret, a house-colour stripe and a wheel, as a converter names them. */
    private static Node humvee() {
        var root = new Node("AVHUMMER");
        for (var piece : List.of("AVHUMMER.TURRET", "TurretUp01", "HOUSECOLOR03", "WHEEL", "MuzzleFXUp01")) {
            root.attachChild(new Node(piece));
        }
        return root;
    }

    private static Visuals.UnitVisual look() {
        return Visuals.create().unit("Humvee", look -> look
                .model("models/avhummer.glb")
                .pieces(Set.of(), List.of("TurretUp01", "HouseColor03", "MuzzleFXUp01"), List.of())
                .pieces(Set.of("WEAPONSET_PLAYER_UPGRADE"), List.of("Turret"), List.of("AVHUMMER.TURRETUP01"))
                .fireBone(0, "Muzzle")
                .fireBone(Set.of("WEAPONSET_PLAYER_UPGRADE"), 0, "MuzzleUp")
                .muzzleFlash(Set.of("WEAPONSET_PLAYER_UPGRADE"), 0, "MuzzleFXUp"))
                .of("Humvee");
    }

    /** Whether a piece is drawn: hidden only when culled always, as jME reads a piece's hint up its tree. */
    private static String hint(Node model, String piece) {
        return model.getChild(piece).getCullHint() == Spatial.CullHint.Always ? HIDDEN : SHOWN;
    }

    private static final String HIDDEN = "hidden";
    private static final String SHOWN = "shown";

    @Test
    void aThingGainingAWordHidesOnePieceShowsTheOtherAndKeepsThemThroughASwap() {
        var look = look();
        var pieces = new Pieces();
        var model = humvee();

        pieces.choose(look.pieceStateFor(Set.of()), look.pieceStates);
        pieces.applyTo(model, Set.of());
        assertEquals(SHOWN, hint(model, "AVHUMMER.TURRET"));
        assertEquals(HIDDEN, hint(model, "TurretUp01"), "the plain state hides the upgrade's turret");

        pieces.choose(look.pieceStateFor(Set.of("WEAPONSET_PLAYER_UPGRADE")), look.pieceStates);
        pieces.applyTo(model, Set.of());
        assertEquals(HIDDEN, hint(model, "AVHUMMER.TURRET"), "named bare, found qualified");
        assertEquals(SHOWN, hint(model, "TurretUp01"), "named qualified, found bare");

        var damaged = humvee(); // the damaged file, every piece as it drew it
        pieces.applyTo(damaged, Set.of());
        assertEquals(HIDDEN, hint(damaged, "AVHUMMER.TURRET"), "a damaged Humvee keeps its turret choice");
        assertEquals(SHOWN, hint(damaged, "TurretUp01"));
    }

    @Test
    void aStateChangesOnlyWhatItNamesAndAPieceNoneNamesIsAsTheFileDrewIt() {
        var look = look();
        var pieces = new Pieces();
        var model = humvee();
        model.getChild("WHEEL").setCullHint(Spatial.CullHint.Never);

        pieces.choose(look.pieceStateFor(Set.of()), look.pieceStates);
        pieces.choose(look.pieceStateFor(Set.of("WEAPONSET_PLAYER_UPGRADE")), look.pieceStates);
        pieces.applyTo(model, Set.of());

        assertEquals(HIDDEN, hint(model, "HOUSECOLOR03"), "hidden by the first state, not shown by the second: sticky");
        assertEquals(Spatial.CullHint.Never, model.getChild("WHEEL").getLocalCullHint(), "no state names it");
    }

    @Test
    void aBarrelsFlashIsTheBarrelsAndTheUpgradedTurretFiresFromItsOwnMuzzle() {
        var look = look();
        var model = humvee();
        model.attachChild(new Node("Muzzle01"));
        model.attachChild(new Node("MuzzleUp01"));
        var upgraded = look.weaponBonesFor(Set.of("WEAPONSET_PLAYER_UPGRADE"));
        var barrels = new Barrels();
        barrels.dress(7, model, upgraded, Barrels.Recoil.REFERENCE);
        var pieces = new Pieces();
        pieces.choose(look.pieceStateFor(Set.of()), look.pieceStates);

        assertSame(model.getChild("MuzzleUp01"), barrels.fire(7, 0), "the best-fitting set's fire bone");
        assertEquals(SHOWN, hint(model, "MuzzleFXUp01"), "flashing on the frame it fires");
        pieces.applyTo(model, barrels.flashes(7));
        assertEquals(SHOWN, hint(model, "MuzzleFXUp01"), "no state of the pieces may take it from the barrel");
        assertSame(look.weaponBonesFor(Set.of()), look.weaponBones, "with no word, the plain bones");
    }
}
