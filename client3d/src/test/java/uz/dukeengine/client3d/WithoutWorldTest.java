package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.view.UnitView;

/** A thing drawn without the night's word wears its day look at night, and its neighbours their night one. */
class WithoutWorldTest {

    private static final String DAY = "models/house.glb";
    private static final String NIGHT = "models/house_n.glb";

    private static final Visuals VISUALS = Visuals.create().world("NIGHT").unit("House", look -> look
            .model(DAY).model(Set.of("NIGHT"), NIGHT));

    private static UnitView house(Set<String> without) {
        return new UnitView(1, "House", 0, 0f, 0f, 0f, 100f, 100f, true, true, false, false, 0, 0f, 0f, 0f, false, 0,
                List.of(), List.of(), 0f, 0, true, null, false, null, 0, 1f, false, 0f, false, null, 0f, 0f, null, 1f,
                0, null, without);
    }

    @Test
    void aThingWithoutTheNightIsDrawnInItsDayLookAtNightAndTheRestInTheirNightOne() {
        var look = VISUALS.of("House");
        var world = VISUALS.getWorldConditions();
        var marked = house(Set.of("NIGHT"));

        assertEquals(NIGHT, look.modelFor(1f, world, house(Set.of()).conditions()), "the others, as ever");
        assertEquals(DAY, look.modelFor(1f, UnitScene.without(world, marked.withoutWorld()), marked.conditions()),
                "this one, day at night");
        assertEquals(Set.of("NIGHT"), world, "the world itself is not changed");
    }

    @Test
    void aViewMadeBeforeTheGameCouldSayItHoldsNothingBack() {
        var old = new UnitView(1, "House", 0, 0f, 0f, 0f, 100f, 100f, true, true, false, false, 0, 0f, 0f, 0f, false, 0,
                List.of(), List.of());

        assertEquals(Set.of(), old.withoutWorld());
        assertEquals(Set.of(), house(null).withoutWorld());
        assertEquals(Set.of("NIGHT"), house(Set.of("NIGHT")).asRemembered().withoutWorld(), "kept as remembered, too");
    }
}
