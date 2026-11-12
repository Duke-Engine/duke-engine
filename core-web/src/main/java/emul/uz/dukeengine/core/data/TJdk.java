package emul.uz.dukeengine.core.data;

import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;

/** {@code Jdk} in a browser: one thread to spin on, no classpath to list, no jar manifest. */
public final class TJdk {

    private TJdk() {
    }

    /** A sleep, not a spin: the one thread spinning would starve the page it shares with the frame being waited for. */
    public static void spinWait() {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** None: a caller falls back to the one resource it can open by name. */
    public static Enumeration<URL> resources(ClassLoader loader, String path) {
        return Collections.emptyEnumeration();
    }

    public static String implementationVersion(Class<?> type) {
        return null;
    }
}
