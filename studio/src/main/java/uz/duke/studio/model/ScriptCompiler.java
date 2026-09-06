package uz.duke.studio.model;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import uz.duke.game.script.UnitScript;

/**
 * Compiles the project's {@code UnitScript} sources in memory with the JDK
 * compiler ({@code javax.tools}) — the Studio's "script assembly". No files
 * touch disk; compiled classes are loaded straight into the running JVM for
 * the Play button. Exported games skip all this: they get the plain
 * {@code .java} sources and Gradle compiles them normally.
 */
public final class ScriptCompiler {

    /** The outcome of a compile: loaded script classes, or human-readable errors. */
    public record Result(Map<String, Class<? extends UnitScript>> scripts, String errors) {
        public boolean ok() {
            return errors.isEmpty();
        }
    }

    private ScriptCompiler() {
    }

    /** Compile every script in the project. Never throws on user code errors. */
    public static Result compile(List<StudioProject.ScriptDef> scripts) {
        if (scripts.isEmpty()) {
            return new Result(Map.of(), "");
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return new Result(Map.of(), "No system Java compiler available — run the Studio on a JDK.");
        }

        var sources = new ArrayList<JavaFileObject>();
        for (var script : scripts) {
            sources.add(new SourceFile("game.scripts." + script.name, script.source));
        }

        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var classBytes = new HashMap<String, ByteArrayOutputStream>();
        var standard = compiler.getStandardFileManager(diagnostics, Locale.ROOT, null);
        var inMemory = new ForwardingJavaFileManager<StandardJavaFileManager>(standard) {
            @Override
            public JavaFileObject getJavaFileForOutput(Location location, String className,
                    JavaFileObject.Kind kind, FileObject sibling) {
                var buffer = new ByteArrayOutputStream();
                classBytes.put(className, buffer);
                return new SimpleJavaFileObject(URI.create("mem:///" + className + ".class"), kind) {
                    @Override
                    public OutputStream openOutputStream() {
                        return buffer;
                    }
                };
            }
        };

        var options = List.of("-classpath", System.getProperty("java.class.path"));
        boolean ok = compiler.getTask(null, inMemory, diagnostics, options, null, sources).call();
        if (!ok) {
            var errors = new StringBuilder();
            for (var diagnostic : diagnostics.getDiagnostics()) {
                errors.append(diagnostic.getKind()).append(": ")
                        .append(diagnostic.getSource() != null
                                ? diagnostic.getSource().getName().replace("mem:///", "") + ":" : "")
                        .append(diagnostic.getLineNumber()).append("  ")
                        .append(diagnostic.getMessage(Locale.ROOT)).append('\n');
            }
            return new Result(Map.of(), errors.toString());
        }

        var loader = new ClassLoader(ScriptCompiler.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                var bytes = classBytes.get(name);
                if (bytes == null) {
                    throw new ClassNotFoundException(name);
                }
                var data = bytes.toByteArray();
                return defineClass(name, data, 0, data.length);
            }
        };

        var loaded = new HashMap<String, Class<? extends UnitScript>>();
        for (var script : scripts) {
            try {
                var cls = loader.loadClass("game.scripts." + script.name);
                if (!UnitScript.class.isAssignableFrom(cls)) {
                    return new Result(Map.of(), "Script '" + script.name
                            + "' must extend UnitScript (public class " + script.name + " extends UnitScript).");
                }
                loaded.put(script.name, cls.asSubclass(UnitScript.class));
            } catch (ClassNotFoundException e) {
                return new Result(Map.of(), "Script '" + script.name
                        + "': class game.scripts." + script.name
                        + " not found — the public class name must match the script name.");
            }
        }
        return new Result(loaded, "");
    }

    private static final class SourceFile extends SimpleJavaFileObject {
        private final String source;

        SourceFile(String className, String source) {
            super(URI.create("mem:///" + className.replace('.', '/') + ".java"), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }
}
