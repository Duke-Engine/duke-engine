package uz.duke.core.thing;

import java.util.HashMap;
import java.util.Map;
import uz.duke.core.SubsystemInterface;
import uz.duke.core.module.ModuleFactory;

/**
 * The registry of {@link ThingTemplate}s and the maker of {@link GameObject}s,
 * ported from SAGE's {@code ThingFactory}.
 *
 * <p>Templates are loaded once (from INI, or built in code) and looked up by
 * name. {@link #newObject} stamps out a live object from a template, building
 * each of its modules via the {@link ModuleFactory}. The caller (the simulation)
 * supplies the {@link ObjectId} so identity allocation stays in one place.
 */
public final class ThingFactory extends SubsystemInterface {

    private final Map<String, ThingTemplate> templates = new HashMap<>();
    private final ModuleFactory moduleFactory;

    public ThingFactory(ModuleFactory moduleFactory) {
        this.moduleFactory = moduleFactory;
    }

    public ModuleFactory getModuleFactory() {
        return moduleFactory;
    }

    @Override
    public void init() {
        moduleFactory.init();
    }

    @Override
    public void reset() {
        // Templates are persistent game data loaded once from INI; resetting
        // for a new game clears the world's objects, not the template catalogue.
        // Use clearTemplates() to force a full reload.
        moduleFactory.reset();
    }

    /** Drop every loaded template — only needed when reloading INI data. */
    public void clearTemplates() {
        templates.clear();
    }

    @Override
    public void update() {
    }

    public void addTemplate(ThingTemplate template) {
        templates.put(template.getName(), template);
    }

    /** The template with this name, or {@code null} if none is registered. */
    public ThingTemplate findTemplate(String name) {
        return templates.get(name);
    }

    /** Build a new object from a template, attaching all its modules. */
    public GameObject newObject(ThingTemplate template, ObjectId id) {
        var object = new GameObject(id, template);
        for (var entry : template.getModules()) {
            object.addModule(moduleFactory.newModule(entry.tag(), object, entry.data()));
        }
        return object;
    }
}
