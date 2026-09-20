package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.dungeon.skill.SkillBook;
import uz.dukeengine.rts.module.RtsModuleGroups;

/**
 * Every module the game can be built from is filed under a group, so a tool offering
 * modules by group misses none of them.
 *
 * <p>Found by reading the class files on the classpath rather than from a list, so a
 * module added tomorrow without a group fails here instead of vanishing from the tool.
 * The game sits on top of every layer, so its classpath holds the engine's modules, the
 * RTS library's and its own.
 */
class EveryModuleIsGroupedTest {

    private static final Set<String> SPELLED = Set.of(
            ModuleGroups.MOVEMENT, ModuleGroups.BODY, ModuleGroups.COMBAT, ModuleGroups.EFFECT,
            ModuleGroups.SCRIPT, RtsModuleGroups.ECONOMY, RtsModuleGroups.PROGRESSION);

    @Test
    void everyModuleIsFiledUnderAGroupThatIsSpelledSomewhere() throws Exception {
        var modules = modules();

        // The engine's two, the RTS library's eleven, the script adapter and the game's fourteen.
        assertTrue(modules.size() >= 28, "the scan found too few modules to be a scan: " + modules);
        for (var module : modules) {
            var groups = ModuleGroups.of(module);
            assertFalse(groups.isEmpty(), module.getName() + " is in no group");
            assertTrue(SPELLED.containsAll(groups), module.getName() + " names a group nobody spells: " + groups);
        }
    }

    @Test
    void aModuleInSeveralGroupsNamesEveryOne() {
        assertEquals(List.of(ModuleGroups.COMBAT, ModuleGroups.MOVEMENT, ModuleGroups.EFFECT, ModuleGroups.BODY),
                ModuleGroups.of(SkillBook.class));
    }

    /** Every concrete module class on the classpath, leaving out this module's own test doubles. */
    private static List<Class<? extends Module>> modules() throws IOException, ClassNotFoundException {
        var names = new TreeSet<String>();
        for (var entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            var path = Path.of(entry);
            if (path.endsWith(Path.of("classes", "java", "test"))) {
                continue;
            }
            if (Files.isDirectory(path)) {
                try (var files = Files.walk(path)) {
                    files.forEach(file -> addClass(names, path.relativize(file).toString().replace(File.separatorChar, '/')));
                }
            } else if (entry.endsWith(".jar")) {
                try (var jar = new JarFile(entry)) {
                    jar.stream().map(JarEntry::getName).forEach(name -> addClass(names, name));
                }
            }
        }
        var loader = EveryModuleIsGroupedTest.class.getClassLoader();
        var modules = new ArrayList<Class<? extends Module>>();
        for (var name : names) {
            var type = Class.forName(name, false, loader);
            if (Module.class.isAssignableFrom(type) && !type.isInterface()
                    && !Modifier.isAbstract(type.getModifiers()) && !type.isAnonymousClass()) {
                modules.add(type.asSubclass(Module.class));
            }
        }
        return modules;
    }

    private static void addClass(Set<String> names, String file) {
        if (file.startsWith("uz/dukeengine/") && file.endsWith(".class")) {
            names.add(file.substring(0, file.length() - ".class".length()).replace('/', '.'));
        }
    }
}
