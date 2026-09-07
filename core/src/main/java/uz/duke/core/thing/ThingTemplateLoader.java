package uz.duke.core.thing;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import uz.duke.core.GameConstants;
import uz.duke.core.ini.FieldParseTable;
import uz.duke.core.ini.Ini;
import uz.duke.core.ini.IniException;

/**
 * Loads {@code Object} definitions from INI into {@link ThingTemplate}s, ported
 * from SAGE's {@code INI::parseObjectDefinition} and friends.
 *
 * <p>This is what makes the engine data-driven: a unit is described entirely in
 * text — its classification and the modules that give it behaviour — and this
 * loader turns that into a template the {@link ThingFactory} can stamp out. A
 * definition looks like:
 * <pre>{@code
 * Object Crusader
 *   DisplayName = Crusader Tank
 *   KindOf = SELECTABLE VEHICLE CAN_ATTACK
 *   Geometry = BOX
 *   GeometryMajorRadius = 8
 *   GeometryMinorRadius = 5
 *   GeometryHeight = 6
 *   Body = ActiveBody ModuleTag_01
 *     MaxHealth = 480.0
 *   End
 * End
 * }</pre>
 * Module-bearing fields ({@code Body}, {@code Behavior}, {@code Update},
 * {@code Draw}, {@code ClientUpdate}) read a module <em>type</em> and instance
 * tag, then hand the nested sub-block to that module's registered data parser.
 */
public final class ThingTemplateLoader {

    private static final String[] MODULE_FIELDS = {"Body", "Behavior", "Update", "Draw", "ClientUpdate"};

    private final ThingFactory thingFactory;
    private final FieldParseTable<ThingTemplate.Builder> objectTable;

    public ThingTemplateLoader(ThingFactory thingFactory) {
        this.thingFactory = thingFactory;
        this.objectTable = buildObjectTable();
    }

    private FieldParseTable<ThingTemplate.Builder> buildObjectTable() {
        var table = new FieldParseTable<ThingTemplate.Builder>()
                .add("DisplayName", Ini.restOfLine(ThingTemplate.Builder::displayName))
                .add("KindOf", this::parseKindOf)
                .add("BuildCost", Ini.integer((b, v) -> b.buildCost(v)))
                .add("BuildTime", (ini, b) -> b.buildTimeFrames(
                        Math.round(Ini.scanReal(ini.getNextToken()) * GameConstants.LOGICFRAMES_PER_SECOND)))
                .add("VisionRange", Ini.real((b, v) -> b.visionRange(v)))
                .add("Geometry", this::parseGeometryType)
                .add("GeometryMajorRadius", Ini.real((b, v) -> b.geometryMajorRadius = v))
                .add("GeometryMinorRadius", Ini.real((b, v) -> b.geometryMinorRadius = v))
                .add("GeometryHeight", Ini.real((b, v) -> b.geometryHeight = v));
        for (var field : MODULE_FIELDS) {
            table.add(field, this::parseModule);
        }
        return table;
    }

    private void parseKindOf(Ini ini, ThingTemplate.Builder builder) {
        for (var token = ini.getNextTokenOrNull(); token != null; token = ini.getNextTokenOrNull()) {
            builder.addKindOf(Kind.of(token));
        }
    }

    /**
     * Reads {@code Geometry = SPHERE|CYLINDER|BOX}. Validated here rather than at
     * build time so a typo is reported with the line that caused it.
     */
    private void parseGeometryType(Ini ini, ThingTemplate.Builder builder) {
        var token = ini.getNextToken();
        try {
            builder.geometryType = ThingTemplate.GeometryType.valueOf(token.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IniException("unknown Geometry '" + token + "' — expected "
                    + Arrays.toString(ThingTemplate.GeometryType.values()));
        }
    }

    private void parseModule(Ini ini, ThingTemplate.Builder builder) {
        var moduleType = ini.getNextToken();
        ini.getNextTokenOrNull(); // instance tag (e.g. ModuleTag_01) — referenced later, unused for now
        var data = thingFactory.getModuleFactory().parseData(moduleType, ini);
        builder.module(moduleType, data);
    }

    private void parseObject(Ini ini) {
        var name = ini.getNextToken();
        var builder = ThingTemplate.named(name);
        ini.initFromIni(builder, objectTable);
        thingFactory.addTemplate(builder.build());
    }

    /** Register the {@code Object} block parser into a block registry. */
    public void registerBlocks(Map<String, Ini.BlockParser> registry) {
        registry.put("Object", this::parseObject);
    }

    /** Parse every {@code Object} block in {@code iniText} into the factory. */
    public void load(String iniText) {
        var registry = new HashMap<String, Ini.BlockParser>();
        registerBlocks(registry);
        Ini.of(iniText, registry).load();
    }
}
