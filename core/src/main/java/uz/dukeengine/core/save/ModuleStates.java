package uz.dukeengine.core.save;

import java.util.ArrayList;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleFactory;
import uz.dukeengine.core.thing.GameObject;

/**
 * A thing's modules as a save holds them: one line of entries parted by {@code |}, each module in the order the thing
 * carries them — where it stood among its template's ({@link GameObject#templateSlotOf}) or {@code +} for one added
 * while the match ran, its class's full name, and what it wrote ({@link SaveOut}). Every module is named, the stateless
 * ones too, so a load puts back exactly the modules the saved thing had, in its order.
 */
public final class ModuleStates {

    private ModuleStates() {
    }

    /**
     * For a Saveable module's class, the first class from it up to the one declaring its save that adds instance fields
     * of its own, which that save cannot know to write; null where none does. A subclass of a saving module that keeps
     * a field must write it.
     */
    private static final ClassValue<Class<?>> UNWRITTEN = new ClassValue<>() {
        @Override
        protected Class<?> computeValue(Class<?> type) {
            if (!Saveable.class.isAssignableFrom(type)) {
                return null;
            }
            for (var c = type; c != null; c = c.getSuperclass()) {
                if (declaresSave(c)) {
                    return null;
                }
                for (var field : c.getDeclaredFields()) {
                    if (!java.lang.reflect.Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                        return c;
                    }
                }
            }
            return null;
        }
    };

    private static boolean declaresSave(Class<?> type) {
        try {
            type.getDeclaredMethod("save", SaveOut.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * {@code thing}'s modules as one line, refused ({@link UnsavedModuleException}) for a module that is neither
     * {@link Saveable} nor declared {@link Stateless}, and for one added while the match ran that {@code factory} has no
     * reviver for.
     */
    public static String save(GameObject thing, ModuleFactory factory) {
        var line = new StringBuilder();
        for (var module : thing.getModules()) {
            var type = module.getClass();
            int slot = thing.templateSlotOf(module);
            if (!(module instanceof Saveable) && !type.isAnnotationPresent(Stateless.class)) {
                throw new UnsavedModuleException(type.getName(), type.getName() + " on " + named(thing)
                        + " is neither Saveable nor declared @Stateless: a save would lose what it holds");
            }
            var unwritten = UNWRITTEN.get(type);
            if (unwritten != null) {
                throw new UnsavedModuleException(type.getName(), type.getName() + " on " + named(thing) + " is Saveable"
                        + " by a save it inherits, but " + unwritten.getName() + " holds fields of its own that save"
                        + " does not write: it declares save and load of its own, or the fields go");
            }
            if (slot < 0 && factory.reviverFor(type.getName()) == null) {
                throw new UnsavedModuleException(type.getName(), type.getName() + " on " + named(thing)
                        + " was added while the match ran and has no reviver (ModuleFactory.revives): a load could not"
                        + " make it again");
            }
            if (!line.isEmpty()) {
                line.append('|');
            }
            line.append(slot < 0 ? "+" : Integer.toString(slot)).append(' ').append(type.getName());
            if (module instanceof Saveable saveable) {
                var out = new SaveOut();
                saveable.save(out);
                if (!out.text().isEmpty()) {
                    line.append(' ').append(out.text());
                }
            }
        }
        return line.toString();
    }

    /**
     * Put back on {@code thing}, which its template has just made afresh, the modules {@code line} names, each handed
     * what it wrote: its template's own by slot, the rest made by their revivers. Every thing of the save must be in the
     * world already. A module the template no longer has where the save says, a class with no reviver, or a value read
     * differently from how it was written is refused by name.
     */
    @SuppressWarnings("unchecked")
    public static void load(GameObject thing, String line, ModuleFactory factory) {
        var fresh = thing.templateModules();
        var restored = new ArrayList<Module>();
        for (var entry : line.isEmpty() ? new String[0] : line.split("\\|", -1)) {
            int nameStart = entry.indexOf(' ') + 1;
            int nameEnd = entry.indexOf(' ', nameStart);
            var slot = entry.substring(0, nameStart - 1);
            var type = nameEnd < 0 ? entry.substring(nameStart) : entry.substring(nameStart, nameEnd);
            var in = new SaveIn(nameEnd < 0 ? "" : entry.substring(nameEnd + 1), thing.getWorld(), type);
            Module module;
            if (slot.equals("+")) {
                var reviver = (Saveable.Reviver<Module>) factory.reviverFor(type);
                if (reviver == null) {
                    throw new UnsavedModuleException(type, type + " on " + named(thing)
                            + " has no reviver (ModuleFactory.revives) to make it again");
                }
                module = reviver.revive(thing, in);
            } else {
                int at = Integer.parseInt(slot);
                if (at >= fresh.size() || !fresh.get(at).getClass().getName().equals(type)) {
                    throw new UnsavedModuleException(type, named(thing) + " was saved with " + type + " as its module "
                            + at + ", which its template no longer makes there");
                }
                module = fresh.get(at);
                if (module instanceof Saveable saveable) {
                    saveable.load(in);
                }
            }
            in.expectEnd();
            restored.add(module);
        }
        thing.restoreModules(restored);
    }

    private static String named(GameObject thing) {
        return "thing " + thing.getId().value() + " (" + thing.getTemplate().name() + ")";
    }
}
