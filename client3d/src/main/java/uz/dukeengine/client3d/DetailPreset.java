package uz.dukeengine.client3d;

/**
 * The switches of a detail preset the client carries out ({@code StaticGameLOD}: {@code UseShadowDecals}, {@code
 * UseEmissiveNightMaterials}, {@code ShowSoftWaterEdge}, {@code UseBuildupScaffolds}, {@code UseTreeSway}), set with
 * {@link Duke3D#preset} before the game starts or while it runs: what a switch turns off is taken away from what is
 * already drawn, not only from what is drawn next. Drawing only. {@link #ALL} is how everything was always drawn.
 *
 * @param shadowDecals            the ground pictures a look gives as its shadow ({@code UnitVisual.shadowDecal})
 * @param emissiveNightMaterials  the models a look picks for the words of the night ({@code Visuals.nightWords})
 * @param softWaterEdge           water as clear at the shore as the ground under it is shallow ({@code WaterLook})
 * @param buildupScaffolds        the layers a look marks as its scaffold ({@code UnitVisual.scaffold})
 * @param treeSway                the lean of things that sway in the wind ({@code UnitVisual.sway})
 */
public record DetailPreset(boolean shadowDecals, boolean emissiveNightMaterials, boolean softWaterEdge,
        boolean buildupScaffolds, boolean treeSway) {

    public static final DetailPreset ALL = new DetailPreset(true, true, true, true, true);
}
