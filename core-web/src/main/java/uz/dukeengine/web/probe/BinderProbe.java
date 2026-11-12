package uz.dukeengine.web.probe;

import java.util.List;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import uz.dukeengine.core.thing.ThingTemplateLoader;
import uz.dukeengine.rts.RtsFlavour;
import uz.dukeengine.skirmish.content.Unit;

/**
 * Binds the skirmish's unit files as the skirmish does (the RTS flavour's templates and its {@code Unit}) and writes out what came of them, one template
 * a line. The page runs it in a browser; {@code BinderProbeTest} runs the same on the JVM, so the two can be
 * compared line by line.
 */
public final class BinderProbe {
    static final List<String> UNITS = List.of("archer", "barracks", "depot", "ore", "soldier", "worker");

    private BinderProbe() {
    }

    /** Every template the texts hold, in order, as its record prints. */
    public static String describe(List<String> names, List<String> texts) {
        var flavour = new RtsFlavour();
        var loader = new ThingTemplateLoader(flavour.newWorld().getThingFactory());
        flavour.templates(loader);
        loader.type(Unit.class);
        var out = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            for (var template : loader.load(texts.get(i), names.get(i))) {
                out.append(template).append('\n');
            }
        }
        return out.toString();
    }

    public static void main(String[] args) {
        var texts = UNITS.stream().map(name -> text("units/" + name + ".duke")).toList();
        long start = System.currentTimeMillis();
        String result;
        try {
            result = describe(UNITS.stream().map(name -> name + ".duke").toList(), texts);
        } catch (RuntimeException e) {
            result = "FAILED: " + e;
            e.printStackTrace();
        }
        System.out.println("bound in " + (System.currentTimeMillis() - start) + " ms");
        System.out.print(result);
        show(result);
    }

    @JSFunctor
    interface Got extends JSObject {
        void got(String text);
    }

    @JSBody(params = {"path", "got"}, script = "fetch(path).then(function(r) { return r.ok ? r.text() : null; })"
            + ".then(function(t) { got(t); }, function() { got(null); });")
    private static native void fetch(String path, Got got);

    @Async
    private static native String text(String path);

    private static void text(String path, AsyncCallback<String> callback) {
        fetch(path, callback::complete);
    }

    @JSBody(params = "text", script = "var p = document.getElementById('out'); p.textContent = text;"
            + " p.dataset.done = 'yes';")
    private static native void show(String text);
}
