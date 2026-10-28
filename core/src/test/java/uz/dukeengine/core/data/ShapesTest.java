package uz.dukeengine.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * The shapes the annotation processor wrote say what reflection says, type for type: the same components, in
 * the same order, of the same types, with the same marks and the same defaults; the same permitted records,
 * the same constants, and a factory where reflection finds one.
 *
 * <p>Every test in this module also runs once more with {@code -Dduke.shapes=reflection}, so what is bound
 * from the written shapes is checked against what is bound by reflection over all the module's data tests.
 */
class ShapesTest {

    @Test
    void everyWrittenShapeSaysWhatReflectionSays() {
        var differences = new ArrayList<String>();
        Shapes.generated().forEach((type, written) -> compare(type, written, Shapes.reflected(type), differences));
        assertEquals(List.of(), differences);
    }

    @Test
    void theEnginesTypesAndThisModulesTestRecordsAreWritten() {
        assertNotNull(Shapes.generated().get(Block.class), "a core record");
        assertNotNull(Shapes.generated().get(BinderTest.Generation.class), "a package-private test record");
        assertNotNull(Shapes.generated().get(Value.class), "a sealed interface");
        assertNotNull(Shapes.generated().get(BinderTest.Channel.class), "an enum");
    }

    @Test
    void theBinderReadsTheWrittenShapeWhereThereIsOne() {
        assumeFalse("reflection".equals(System.getProperty("duke.shapes")), "the reflection-only run");
        assertSame(Shapes.generated().get(Block.class), Shapes.of(Block.class));
    }

    @Test
    void aWrittenRecordIsBuiltFromWhatItIsReadAs() {
        var components = Shapes.generated().get(BinderTest.Generation.class).components();
        var values = components.read().apply(components.defaults().get());
        assertEquals(BinderTest.Generation.DEFAULTS, components.build().apply(values));
    }

    private static void compare(Class<?> type, Shape written, Shape reflected, List<String> differences) {
        var name = type.getName();
        same(name + " permitted", written.permitted(), reflected.permitted(), differences);
        same(name + " constants", written.constants(), reflected.constants(), differences);
        same(name + " has a factory", written.factory() != null, reflected.factory() != null, differences);
        if ((written.components() == null) != (reflected.components() == null)) {
            differences.add(name + " is a record to one and not the other");
            return;
        }
        if (written.components() == null) {
            return;
        }
        var a = written.components();
        var b = reflected.components();
        same(name + " names", a.names(), b.names(), differences);
        same(name + " generics", a.generics(), b.generics(), differences);
        same(name + " types", a.types(), b.types(), differences);
        same(name + " marks", described(a.marks()), described(b.marks()), differences);
        same(name + " has defaults", a.defaults() != null, b.defaults() != null, differences);
        if (a.defaults() != null && b.defaults() != null) {
            same(name + " defaults", Arrays.asList(a.read().apply(a.defaults().get())),
                    Arrays.asList(b.read().apply(b.defaults().get())), differences);
        }
    }

    private static void same(String what, Object written, Object reflected, List<String> differences) {
        if (!Objects.equals(written, reflected)) {
            differences.add(what + ": written " + written + ", reflected " + reflected);
        }
    }

    /** An annotation as its type and the value of each of its members, which is all that can be compared. */
    private static List<List<String>> described(List<List<Annotation>> marks) {
        return marks.stream().map(each -> each.stream().map(ShapesTest::described).toList()).toList();
    }

    private static String described(Annotation mark) {
        var text = new StringBuilder(mark.annotationType().getName());
        var members = mark.annotationType().getDeclaredMethods();
        Arrays.sort(members, (x, y) -> x.getName().compareTo(y.getName()));
        for (Method member : members) {
            try {
                text.append(' ').append(member.getName()).append('=')
                        .append(Arrays.deepToString(new Object[] {member.invoke(mark)}));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        return text.toString();
    }

    @Test
    void theComparisonSeesADifference() {
        var differences = new ArrayList<String>();
        compare(Block.class, Shapes.reflected(Block.class), Shapes.reflected(Field.class), differences);
        assertFalse(differences.isEmpty());
    }
}
