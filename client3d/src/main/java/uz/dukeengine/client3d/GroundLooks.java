package uz.dukeengine.client3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** How the ground is lit, painted and dressed, held for {@link Visuals}: light, water, paint, marks, strips, streams. */
final class GroundLooks {

    Sunlight sunlight = Sunlight.DEFAULT;
    Visuals.GroundLight light;
    final List<Visuals.GroundShade> shades = new ArrayList<>();
    Visuals.WaterLook water;
    int borderCells;
    List<String> skybox = List.of();
    final Map<String, Visuals.TrackLook> trackMarks = new HashMap<>();
    final List<Visuals.WaveLook> shoreWaves = new ArrayList<>();
    List<Visuals.Sun> thingSuns = List.of();
    final Map<String, Visuals.SurfaceLook> surfaces = new LinkedHashMap<>();
    Visuals.PaintBlend paintBlend;
    Visuals.Cliffs paintCliffs;
    boolean paintInQuarters;
    int streamGround;
    Predicate<Object> streamsMap = _ -> true;
    java.util.function.UnaryOperator<String> instancedScenery;
    Predicate<String> scenerySunCasts = _ -> false;
    float sunShadowsReach;
    final Map<String, Visuals.Strip> strips = new HashMap<>();
    final Map<String, Visuals.StreamLook> streamLooks = new LinkedHashMap<>();

    Visuals.SurfaceLook surface(String paletteValue) {
        return paletteValue == null ? Visuals.SurfaceLook.PLAIN
                : surfaces.getOrDefault(paletteValue, Visuals.SurfaceLook.PLAIN);
    }

    int streamGroundFor(Object map) {
        return streamGround > 0 && streamsMap.test(map) ? streamGround : 0;
    }
}
