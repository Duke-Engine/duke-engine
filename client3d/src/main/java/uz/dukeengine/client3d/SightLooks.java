package uz.dukeengine.client3d;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import uz.dukeengine.core.view.UnitView;

/** What is shown of what has not been seen, held for {@link Visuals}: fog, discovery, haze, the minimap's dark. */
final class SightLooks {

    String discoveryTemplate;
    boolean fogBySight;
    Fog fog = Fog.DEFAULT;
    Predicate<Object> startsExplored = _ -> false;
    float wideSightUnits;
    float wideRemembered;
    Predicate<Object> wideEyes = _ -> false;
    Supplier<List<Visuals.Seen>> alsoSeen = List::of;
    float minimapUnseenShows;
    int minimapSpan;
    boolean occlusionMarks;
    Predicate<UnitView> occlusionMarked = _ -> false;
    float hazeFrom;
    float hazeTo;
    Predicate<Object> hazesMap = _ -> true;

    boolean drawsFog() {
        return discoveryTemplate != null || fogBySight;
    }

    boolean hazes() {
        return hazeTo > 0f;
    }

    List<Visuals.Seen> alsoSeenNow() {
        var places = alsoSeen.get();
        return places == null ? List.of() : places;
    }
}
