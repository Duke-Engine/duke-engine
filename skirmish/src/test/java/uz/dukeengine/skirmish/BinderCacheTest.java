package uz.dukeengine.skirmish;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplateLoader;
import uz.dukeengine.rts.module.RtsModules;
import uz.dukeengine.skirmish.content.Content;
import uz.dukeengine.skirmish.content.Unit;

/**
 * The binder keeps what a record type is once it has worked it out, and binding a great deal of real data
 * twice must still come out the same — every template, every module inside it, equal field for field.
 *
 * <p>What was cached is a lookup — a record's components and its constructor, a type's {@code of(String)} —
 * never an answer about a block, so the second pass is the first pass with the reflection already done.
 * Measured on this file repeated four hundred times: parse and bind went from about 580 ms to about 60.
 */
class BinderCacheTest {

    @Test
    void aLotOfRealDataBindsTheSameTheSecondTimeAsTheFirst() {
        var one = Content.units();
        var text = new StringBuilder();
        for (int copy = 0; copy < 400; copy++) {
            text.append(one.replaceAll("(?m)^(\\s*Name\\s*=\\s*)(\\S+)", "$1$2_" + copy)).append('\n');
        }
        var all = text.toString();

        var first = new ThingTemplateLoader(new ThingFactory(RtsModules.withDefaults()))
                .type(Unit.class).load(all, "many.duke");
        var second = new ThingTemplateLoader(new ThingFactory(RtsModules.withDefaults()))
                .type(Unit.class).load(all, "many.duke");

        assertEquals(first.size(), second.size());
        assertEquals(first, second, "equal records, in the same order");
    }
}
