package uz.dukeengine.core.data;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

/**
 * JDK calls a browser's class library (TeaVM) lacks, behind one door its build replaces: there a spin is a no-op,
 * a loader enumerates nothing and a jar has no version.
 */
public final class Jdk {

    private Jdk() {
    }

    /** As {@code Thread.onSpinWait}. */
    public static void spinWait() {
        Thread.onSpinWait();
    }

    /** As {@code ClassLoader.getResources}. */
    public static Enumeration<URL> resources(ClassLoader loader, String path) throws IOException {
        return loader.getResources(path);
    }

    /** As {@code Package.getImplementationVersion} of {@code type}'s package; null where there is none. */
    public static String implementationVersion(Class<?> type) {
        var in = type.getPackage();
        return in == null ? null : in.getImplementationVersion();
    }
}
