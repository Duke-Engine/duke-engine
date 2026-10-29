package uz.dukeengine.client3d.platform;

import com.jme3.app.state.ScreenshotAppState;
import com.jme3.system.JmeContext;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Everything the client needs of the machine it runs on and cannot ask of jME alone: fonts, picture files, saved
 * settings, a text box, the window and what is held down in it. One aggregate, so there is one lookup and one clear
 * failure, and a platform supplies all of it or none. Found on the classpath as a kind of game is (a
 * {@code META-INF/services} line naming it).
 */
public interface ClientPlatform {

    /**
     * The one platform on the classpath, found on first use and kept. An error where there is none, or more than one.
     */
    static ClientPlatform get() {
        return Found.PLATFORM;
    }

    /** The glyphs of the machine's fonts and of font files. */
    GlyphRasterizer glyphs();

    /** Reads picture files. */
    ImageDecoder images();

    /** Where the player's settings are kept between runs. */
    SettingsStore settings();

    /** Asks the player for a line of text. */
    TextPrompt prompt();

    /** The window of the context, asked each time since it exists only once the application has started. */
    Window window(Supplier<JmeContext> context);

    /** What is held down in the window of the context. */
    HeldInput held(Supplier<JmeContext> context);

    /**
     * The state that saves a picture of the screen on a key, to {@code folder} ({@code null} for jME's default) as
     * {@code pattern}, telling {@code saved} the name of each file once it is written.
     */
    ScreenshotAppState screenshots(String folder, String pattern, Consumer<String> saved);

    /** {@code pattern}, where it names each number apart — {@code sshot%03d.bmp} — or an error for one that does not. */
    static String checkedScreenshotName(String pattern) {
        if (pattern == null || String.format(Locale.ROOT, pattern, 1).equals(String.format(Locale.ROOT, pattern, 2))) {
            throw new IllegalArgumentException("a screenshot's name needs its number, as sshot%03d.bmp: " + pattern);
        }
        return pattern;
    }

    /** The lookup, held apart so that it runs once and only when someone asks. */
    final class Found {
        private static final ClientPlatform PLATFORM = load();

        private Found() {
        }

        private static ClientPlatform load() {
            var found = ServiceLoader.load(ClientPlatform.class, ClientPlatform.class.getClassLoader()).stream()
                    .toList();
            if (found.size() != 1) {
                throw new IllegalStateException(found.isEmpty()
                        ? "no platform on the classpath: add uz.duke-engine:client3d-desktop (or the platform's "
                                + "module) to the runtime classpath"
                        : "more than one platform on the classpath, " + found.stream().map(p -> p.type().getName())
                                .toList() + ": keep the one this game runs on");
            }
            return found.getFirst().get();
        }
    }
}
