package uz.duke.core.thing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import uz.duke.core.ini.FieldParseTable;
import uz.duke.core.ini.Ini;
import uz.duke.core.ini.IniException;

/**
 * Loads template blocks from INI into {@link ThingTemplate}s, ported from SAGE's
 * {@code INI::parseObjectDefinition} and friends.
 *
 * <p>This is what makes the engine data-driven: a thing is described entirely in text —
 * its classification and the modules that give it behaviour — and this turns that into a
 * template the {@link ThingFactory} can stamp out. The engine's own block is
 * {@code Object}:
 * <pre>{@code
 * Object Crusader
 *   DisplayName = Crusader Tank
 *   KindOf = SELECTABLE VEHICLE CAN_ATTACK
 *   Geometry = BOX
 *   GeometryMajorRadius = 8
 *   Body = ActiveBody ModuleTag_01
 *     MaxHealth = 480.0
 *   End
 * End
 * }</pre>
 * A game adds block types of its own with {@link #type}: {@code Monster Brute} becomes the
 * game's {@code Monster} record. Which engine fields a block takes is decided by the
 * capabilities that record implements — a {@link Solid} one takes {@code Geometry}, one
 * that is not refuses it — and module-bearing fields ({@code Body}, {@code Behavior},
 * {@code Update}, {@code Draw}, {@code ClientUpdate}) read a module type and instance tag,
 * then hand the nested sub-block to that module's registered data parser.
 */
public final class ThingTemplateLoader {

    /** Inside a template block, these open a module's sub-block. */
    public static final List<String> MODULE_FIELDS = List.of("Body", "Behavior", "Update", "Draw", "ClientUpdate");

    /** What the engine read from a template block: its name, its modules and the fields of its record's capabilities. */
    public record Parts(String name, List<ThingTemplate.ModuleEntry> modules, String displayName, Set<Kind> kinds,
            float visionRange, Geometry geometry) {
    }

    /** One block type: every field it takes, and how what was read becomes its record. */
    private record Type<P>(FieldParseTable<Draft<P>> fields, Supplier<P> newPart,
            BiFunction<Parts, P, ? extends ThingTemplate> build) {
    }

    /** The shape names INI understands in {@code Geometry = …}. */
    private enum GeometryType {
        SPHERE, CYLINDER, BOX
    }

    private final ThingFactory thingFactory;
    private final Map<String, Type<?>> types = new LinkedHashMap<>();

    public ThingTemplateLoader(ThingFactory thingFactory) {
        this.thingFactory = thingFactory;
        type("Object", ObjectTemplate.class, new FieldParseTable<Void>(), () -> null,
                (parts, none) -> new ObjectTemplate(parts.name(), parts.displayName(), parts.kinds(),
                        parts.visionRange(), parts.geometry(), parts.modules()));
    }

    /**
     * Adds a block type, or replaces one — an RTS gives {@code Object} a build cost. The
     * block takes the engine fields of the capabilities {@code template} implements, its
     * modules, and {@code fields}, which are read into a fresh part from {@code newPart};
     * {@code build} makes the record of both once the block's {@code End} is reached.
     */
    public <P, R extends ThingTemplate> ThingTemplateLoader type(String blockType, Class<R> template,
            FieldParseTable<P> fields, Supplier<P> newPart, BiFunction<Parts, P, R> build) {
        var table = this.<P>engineFields(template).addAll(fields.<Draft<P>>on(Draft::part));
        types.put(blockType, new Type<>(table, newPart, build));
        return this;
    }

    /**
     * {@code game}'s fields and the engine's for {@code template}, the engine's passed over
     * unread: for a game that reads its own fields from a template block before there is a
     * world to read the rest into. Every field of the block is still one somebody reads, so
     * a misspelt one is refused here as it is by the loader.
     */
    public static <T> FieldParseTable<T> passingOverEngineFields(FieldParseTable<T> game, Class<? extends ThingTemplate> template) {
        var table = new FieldParseTable<T>().addAll(game).addAll(FieldParseTable.passingOver(engineFieldNames(template)));
        for (var field : MODULE_FIELDS) {
            table.add(field, (ini, target) -> {
                ini.getRestOfLine();
                ini.skipBlock();
            });
        }
        return table;
    }

    /** The engine fields a block of {@code template} takes, as the capabilities it implements decide. */
    public static List<String> engineFieldNames(Class<? extends ThingTemplate> template) {
        var names = new ArrayList<String>();
        if (Titled.class.isAssignableFrom(template)) {
            names.add("DisplayName");
        }
        if (Classified.class.isAssignableFrom(template)) {
            names.add("KindOf");
        }
        if (Sighted.class.isAssignableFrom(template)) {
            names.add("VisionRange");
        }
        if (Solid.class.isAssignableFrom(template)) {
            names.addAll(List.of("Geometry", "GeometryMajorRadius", "GeometryMinorRadius", "GeometryHeight"));
        }
        return names;
    }

    private <P> FieldParseTable<Draft<P>> engineFields(Class<? extends ThingTemplate> template) {
        var table = new FieldParseTable<Draft<P>>();
        if (Titled.class.isAssignableFrom(template)) {
            table.add("DisplayName", Ini.restOfLine((d, v) -> d.displayName = v));
        }
        if (Classified.class.isAssignableFrom(template)) {
            table.add("KindOf", (ini, d) -> {
                for (var token = ini.getNextTokenOrNull(); token != null; token = ini.getNextTokenOrNull()) {
                    d.kinds.add(Kind.of(token));
                }
            });
        }
        if (Sighted.class.isAssignableFrom(template)) {
            table.add("VisionRange", Ini.real((d, v) -> d.visionRange = v));
        }
        if (Solid.class.isAssignableFrom(template)) {
            table.add("Geometry", ThingTemplateLoader::parseGeometryType)
                    .add("GeometryMajorRadius", Ini.real((d, v) -> d.geometryMajorRadius = v))
                    .add("GeometryMinorRadius", Ini.real((d, v) -> d.geometryMinorRadius = v))
                    .add("GeometryHeight", Ini.real((d, v) -> d.geometryHeight = v));
        }
        for (var field : MODULE_FIELDS) {
            table.add(field, this::parseModule);
        }
        return table;
    }

    /**
     * Reads {@code Geometry = SPHERE|CYLINDER|BOX}. Validated here rather than at
     * build time so a typo is reported with the line that caused it.
     */
    private static void parseGeometryType(Ini ini, Draft<?> draft) {
        var token = ini.getNextToken();
        try {
            draft.geometryType = GeometryType.valueOf(token.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IniException("unknown Geometry '" + token + "' — expected " + Arrays.toString(GeometryType.values()));
        }
    }

    private void parseModule(Ini ini, Draft<?> draft) {
        var moduleType = ini.getNextToken();
        ini.getNextTokenOrNull(); // instance tag (e.g. ModuleTag_01) — referenced later, unused for now
        draft.modules.add(new ThingTemplate.ModuleEntry(moduleType, thingFactory.getModuleFactory().parseData(moduleType, ini)));
    }

    /** Register every block type into a block registry. */
    public void registerBlocks(Map<String, Ini.BlockParser> registry) {
        types.forEach((blockType, type) -> registry.put(blockType, ini -> thingFactory.addTemplate(read(ini, type))));
    }

    /** Parse every template block in {@code iniText} into the factory. */
    public void load(String iniText) {
        var registry = new HashMap<String, Ini.BlockParser>();
        registerBlocks(registry);
        Ini.of(iniText, registry).load();
    }

    private static <P> ThingTemplate read(Ini ini, Type<P> type) {
        var draft = new Draft<>(ini.getNextToken(), type.newPart().get());
        ini.initFromIni(draft, type.fields());
        return type.build().apply(draft.parts(), draft.part);
    }

    /** A block as it is read: the engine's fields here, the game's in {@link #part}. */
    private static final class Draft<P> {
        private final String name;
        private final P part;
        private final List<ThingTemplate.ModuleEntry> modules = new ArrayList<>();
        private final Set<Kind> kinds = new LinkedHashSet<>();
        private String displayName = "";
        private float visionRange;
        // INI spells a shape over several lines, so the pieces are gathered here and
        // assembled once, when the block ends.
        private GeometryType geometryType;
        private float geometryMajorRadius;
        private float geometryMinorRadius;
        private float geometryHeight;

        private Draft(String name, P part) {
            this.name = name;
            this.part = part;
        }

        private P part() {
            return part;
        }

        private Parts parts() {
            return new Parts(name, List.copyOf(modules), displayName, Set.copyOf(kinds), visionRange, geometry());
        }

        private Geometry geometry() {
            if (geometryType == null) {
                return Geometry.POINT;
            }
            return switch (geometryType) {
                case SPHERE -> new Geometry.Sphere(geometryMajorRadius);
                case CYLINDER -> new Geometry.Cylinder(geometryMajorRadius, geometryHeight);
                case BOX -> new Geometry.Box(geometryMajorRadius, geometryMinorRadius, geometryHeight);
            };
        }
    }
}
