package uz.duke.core.thing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import uz.duke.core.data.Binder;
import uz.duke.core.data.DataException;
import uz.duke.core.data.DukeText;
import uz.duke.core.module.ModuleData;

/**
 * Reads template blocks into {@link ThingTemplate}s, ported from SAGE's
 * {@code INI::parseObjectDefinition} and friends.
 *
 * <p>This is what makes the engine data-driven: a thing is described entirely in text —
 * its classification and the modules that give it behaviour — and this turns that into a
 * template the {@link ThingFactory} can stamp out. The engine's own block is {@code Object}:
 * <pre>{@code
 * Object
 *   Name = Crusader
 *   DisplayName = Crusader Tank
 *   KindOf = [SELECTABLE, VEHICLE, CAN_ATTACK]
 *   Box
 *     MajorRadius = 8
 *     MinorRadius = 5
 *     Height = 6
 *   End
 *   ActiveBody
 *     MaxHealth = 480
 *   End
 * End
 * }</pre>
 * A block is the record its type names, read by {@link Binder}: each field is a component of
 * that record, and each module it holds is one the factory builds, named by its class. A game
 * adds block types of its own with {@link #type} — a {@code Monster} block is its
 * {@code Monster} record — and which fields a block takes is simply what that record has.
 */
public final class ThingTemplateLoader {

    private record Type(String word, Class<? extends ThingTemplate> record) {
    }

    private final ThingFactory factory;
    private final Map<String, Type> types = new LinkedHashMap<>();

    public ThingTemplateLoader(ThingFactory factory) {
        this.factory = factory;
        type("Object", ObjectTemplate.class);
    }

    /** Blocks called {@code word} are {@code record}s; a later registration of a word replaces the earlier. */
    public ThingTemplateLoader type(String word, Class<? extends ThingTemplate> record) {
        types.put(word.toLowerCase(Locale.ROOT), new Type(word, record));
        return this;
    }

    /** Blocks named after {@code record}'s class are {@code record}s: {@code Monster} for {@code Monster}. */
    public ThingTemplateLoader type(Class<? extends ThingTemplate> record) {
        return type(record.getSimpleName(), record);
    }

    /** Every template {@code text} holds, each added to the factory; {@code source} is how errors name the text. */
    public List<ThingTemplate> load(String text, String source) {
        var binder = new Binder().vocabulary(ModuleData.class, factory.getModuleFactory().vocabulary());
        var loaded = new ArrayList<ThingTemplate>();
        for (var block : DukeText.parse(text, source)) {
            var type = types.get(block.word().toLowerCase(Locale.ROOT));
            if (type == null) {
                throw new DataException(block.at(block.line()), "no template is called '" + block.word()
                        + "'; these are: " + types.values().stream().map(Type::word).toList());
            }
            var template = binder.bind(block, type.record());
            if (template.name() == null || template.name().isBlank()) {
                throw new DataException(block.at(block.line()), "'" + block.word() + "' has no Name");
            }
            factory.addTemplate(template);
            loaded.add(template);
        }
        return loaded;
    }
}
