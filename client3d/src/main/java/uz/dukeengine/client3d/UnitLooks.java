package uz.dukeengine.client3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** The looks of the game's templates and of its themes, held for {@link Visuals}, with the words that tell them apart. */
final class UnitLooks {

    // Linked, so the order a game declares its units in is the order anything
    // walking them sees -- which is what a loading bar advances through.
    final Map<String, Visuals.UnitVisual> units = new LinkedHashMap<>();
    final Visuals.UnitVisual defaults = new Visuals.UnitVisual();
    final Map<String, Visuals.Theme> themes = new LinkedHashMap<>();
    Tileset tileset;
    String houseColour;
    Set<String> worldConditions = Set.of();
    String seeThroughWord;
    String glowWord;
    String ownGlowWord;

    /** The look named {@code templateName} in {@code into}, made if it is new, then configured. */
    static void configure(Map<String, Visuals.UnitVisual> into, String templateName,
            Consumer<Visuals.UnitVisual> config) {
        config.accept(into.computeIfAbsent(templateName, _ -> new Visuals.UnitVisual()));
    }

    Visuals.UnitVisual of(String templateName) {
        return units.getOrDefault(templateName, defaults);
    }

    List<Visuals.UnitVisual> all() {
        var all = new ArrayList<>(units.values());
        for (var theme : themes.values()) {
            all.addAll(theme.units.values());
        }
        return all;
    }

    List<Visuals.UnitVisual> named(Set<String> templateNames) {
        var named = new ArrayList<Visuals.UnitVisual>();
        collect(units, templateNames, named);
        for (var theme : themes.values()) {
            collect(theme.units, templateNames, named);
        }
        return named;
    }

    private static void collect(Map<String, Visuals.UnitVisual> from, Set<String> templateNames,
            List<Visuals.UnitVisual> into) {
        from.forEach((name, look) -> {
            if (templateNames.contains(name)) {
                into.add(look);
            }
        });
    }

    Set<String> names() {
        var names = new LinkedHashSet<>(units.keySet());
        for (var theme : themes.values()) {
            names.addAll(theme.units.keySet());
        }
        return names;
    }

    List<Tileset> kits() {
        var all = new ArrayList<Tileset>();
        if (tileset != null) {
            all.add(tileset);
        }
        for (var theme : themes.values()) {
            if (theme.tileset != null) {
                all.add(theme.tileset);
            }
        }
        return all;
    }
}
