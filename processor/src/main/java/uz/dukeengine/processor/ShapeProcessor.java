package uz.dukeengine.processor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.StandardLocation;

/**
 * Writes the {@code Shape} of every record, sealed type, enum and type with a static {@code of(String)} or
 * {@code valueOf(String)} a compilation declares, so the engine's {@code Binder} can read data files where
 * reflection cannot answer — a browser. One {@code DukeShapes} class a package, each listed under
 * {@code META-INF/services} as a {@code ShapeSource}.
 *
 * <p>No annotation asks for it: every such type gets one, because which records a game binds is decided by its
 * data, not its code. A type the package's class cannot reach — private, or holding something private — is left
 * out, and the binder asks reflection for it as it always did.
 *
 * <p>The option {@code duke.shapes.class} names the class, so a module's tests, which share its packages, write
 * theirs under another name.
 */
@SupportedAnnotationTypes("*")
@SupportedOptions(ShapeProcessor.CLASS_OPTION)
public final class ShapeProcessor extends AbstractProcessor {

    static final String CLASS_OPTION = "duke.shapes.class";

    private static final String DATA = "uz.dukeengine.core.data.";

    private boolean done;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        // Everything is in the first round; later rounds hold only what this wrote.
        if (done || round.processingOver()) {
            return false;
        }
        done = true;
        if (elements().getTypeElement(DATA + "ShapeSource") == null) {
            return false;
        }
        var name = processingEnv.getOptions().getOrDefault(CLASS_OPTION, "DukeShapes");
        var byPackage = new TreeMap<String, List<TypeElement>>();
        for (var root : round.getRootElements()) {
            collect(root, byPackage);
        }
        var sources = new ArrayList<String>();
        byPackage.forEach((pkg, types) -> {
            var methods = new ArrayList<String>();
            var puts = new ArrayList<String>();
            for (var type : types) {
                String shape;
                try {
                    shape = shape(type, pkg);
                } catch (Unreachable _) {
                    continue;
                }
                if (shape != null) {
                    puts.add("        shapes.put(" + name(type) + ".class, s" + methods.size() + "());\n");
                    methods.add("    private static " + DATA + "Shape s" + methods.size() + "() {\n        return "
                            + shape + ";\n    }\n");
                }
            }
            if (!puts.isEmpty()) {
                var qualified = pkg.isEmpty() ? name : pkg + "." + name;
                write(qualified, source(pkg, name, puts, methods), types);
                sources.add(qualified);
            }
        });
        if (!sources.isEmpty()) {
            try (var out = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "",
                    "META-INF/services/" + DATA + "ShapeSource").openWriter()) {
                out.write(String.join("\n", sources) + "\n");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return false;
    }

    private void collect(Element element, Map<String, List<TypeElement>> byPackage) {
        if (!(element instanceof TypeElement type) || element.getKind() == ElementKind.ANNOTATION_TYPE
                || (type.getNestingKind() != NestingKind.TOP_LEVEL && type.getNestingKind() != NestingKind.MEMBER)) {
            return;
        }
        var pkg = elements().getPackageOf(type).getQualifiedName().toString();
        byPackage.computeIfAbsent(pkg, _ -> new ArrayList<>()).add(type);
        for (var inner : type.getEnclosedElements()) {
            collect(inner, byPackage);
        }
    }

    /** {@code new Shape(...)} for this type, {@code null} where it is none of the four kinds. */
    private String shape(TypeElement type, String pkg) {
        var record = type.getKind() == ElementKind.RECORD;
        var sealed = type.getModifiers().contains(Modifier.SEALED);
        var constants = type.getKind() == ElementKind.ENUM;
        var factory = factory(type, pkg);
        if (!record && !sealed && !constants && factory == null) {
            return null;
        }
        reach(type, pkg);
        return "new " + DATA + "Shape(" + (record ? components(type, pkg) : "null") + ", "
                + (sealed ? list(type.getPermittedSubclasses().stream().map(t -> literal(t, pkg)).toList()) : "null")
                + ", " + (factory == null ? "null" : factory) + ", "
                + (constants ? "java.util.List.of(" + name(type) + ".values())" : "null") + ")";
    }

    private String components(TypeElement type, String pkg) {
        var names = new ArrayList<String>();
        var generics = new ArrayList<String>();
        var types = new ArrayList<String>();
        var marks = new ArrayList<String>();
        var casts = new ArrayList<String>();
        var reads = new ArrayList<String>();
        var self = name(type);
        var components = type.getRecordComponents();
        for (int i = 0; i < components.size(); i++) {
            var component = components.get(i);
            var declared = component.asType();
            names.add(quoted(component.getSimpleName().toString()));
            generics.add(ref(declared, pkg));
            types.add(literal(declared, pkg));
            marks.add(list(marks(component, pkg)));
            casts.add("(" + erased(declared, pkg) + ") v[" + i + "]");
            reads.add("((" + self + ") r)." + component.getAccessor().getSimpleName() + "()");
        }
        return "new " + DATA + "Shape.Components(" + list(names) + ", " + list(generics) + ", " + list(types) + ", "
                + list(marks) + ", v -> new " + self + "(" + String.join(", ", casts) + "), r -> new java.lang.Object[] {"
                + String.join(", ", reads) + "}, " + defaults(type, pkg) + ")";
    }

    /** As {@code getDeclaredField("DEFAULTS")} finds it: static, of the record's own type. */
    private String defaults(TypeElement type, String pkg) {
        for (var member : type.getEnclosedElements()) {
            if (member.getKind() == ElementKind.FIELD && member.getSimpleName().contentEquals("DEFAULTS")) {
                if (!member.getModifiers().contains(Modifier.STATIC)
                        || !types().isSameType(types().erasure(member.asType()), types().erasure(type.asType()))) {
                    return "null";
                }
                if (member.getModifiers().contains(Modifier.PRIVATE)) {
                    throw new Unreachable();
                }
                return "() -> " + name(type) + ".DEFAULTS";
            }
        }
        return "null";
    }

    /** As {@code getMethod(name, String.class)} finds it: public and static, in the type or a superclass. */
    private String factory(TypeElement type, String pkg) {
        for (var name : List.of("of", "valueOf")) {
            for (var at = type; at != null; at = superclass(at)) {
                for (var member : at.getEnclosedElements()) {
                    if (member instanceof ExecutableElement method && member.getKind() == ElementKind.METHOD
                            && method.getSimpleName().contentEquals(name)
                            && method.getModifiers().containsAll(Set.of(Modifier.PUBLIC, Modifier.STATIC))
                            && method.getParameters().size() == 1
                            && types().isSameType(method.getParameters().getFirst().asType(),
                                    elements().getTypeElement("java.lang.String").asType())) {
                        if (!types().isAssignable(types().erasure(method.getReturnType()),
                                types().erasure(type.asType()))) {
                            continue;
                        }
                        reach(at, pkg);
                        return "s -> " + name(at) + "." + name + "(s)";
                    }
                }
            }
        }
        return null;
    }

    private TypeElement superclass(TypeElement type) {
        var parent = type.getSuperclass();
        return parent.getKind() == TypeKind.DECLARED ? (TypeElement) types().asElement(parent) : null;
    }

    /** Each runtime annotation on a component, as an instance of it written out in full. */
    private List<String> marks(RecordComponentElement component, String pkg) {
        var marks = new ArrayList<String>();
        for (var mirror : component.getAnnotationMirrors()) {
            var annotation = (TypeElement) mirror.getAnnotationType().asElement();
            var retention = annotation.getAnnotation(Retention.class);
            if (retention == null || retention.value() != RetentionPolicy.RUNTIME) {
                continue;
            }
            reach(annotation, pkg);
            var body = new StringBuilder("new " + name(annotation) + "() {\n"
                    + "            @Override public java.lang.Class<? extends java.lang.annotation.Annotation> "
                    + "annotationType() { return " + name(annotation) + ".class; }\n");
            var values = elements().getElementValuesWithDefaults(mirror);
            for (var member : values.keySet()) {
                body.append("            @Override public ").append(member.getReturnType()).append(' ')
                        .append(member.getSimpleName()).append("() { return ")
                        .append(value(values.get(member), member.getReturnType(), pkg)).append("; }\n");
            }
            marks.add(body.append("        }").toString());
        }
        return marks;
    }

    private String value(AnnotationValue value, TypeMirror type, String pkg) {
        return switch (value.getValue()) {
            case VariableElement constant -> {
                var owner = (TypeElement) constant.getEnclosingElement();
                reach(owner, pkg);
                yield name(owner) + "." + constant.getSimpleName();
            }
            case TypeMirror mirror -> literal(mirror, pkg);
            case List<?> items -> "new " + erased(((ArrayType) type).getComponentType(), pkg) + "[] {"
                    + items.stream().map(item -> value((AnnotationValue) item, ((ArrayType) type).getComponentType(), pkg))
                            .collect(Collectors.joining(", ")) + "}";
            case AnnotationMirror _ -> throw new Unreachable();
            case Object constant -> elements().getConstantExpression(constant);
        };
    }

    /** As reflection's generic type, reduced to what the binder reads: see {@code TypeRef}. */
    private String ref(TypeMirror type, String pkg) {
        var ref = DATA + "TypeRef";
        return switch (type.getKind()) {
            case DECLARED -> {
                var arguments = ((DeclaredType) type).getTypeArguments();
                yield arguments.isEmpty() ? "new " + ref + "(" + literal(type, pkg) + ")"
                        : "new " + ref + "(" + literal(type, pkg) + ", "
                                + list(arguments.stream().map(a -> ref(a, pkg)).toList()) + ")";
            }
            case WILDCARD -> {
                var bound = ((WildcardType) type).getExtendsBound();
                yield bound == null ? ref + ".OBJECT" : "new " + ref + "(" + literal(bound, pkg) + ")";
            }
            case ARRAY -> plain(type) ? "new " + ref + "(" + literal(type, pkg) + ")" : ref + ".OBJECT";
            case TYPEVAR -> ref + ".OBJECT";
            default -> type.getKind().isPrimitive() ? "new " + ref + "(" + literal(type, pkg) + ")" : ref + ".OBJECT";
        };
    }

    /** Whether an array type is a class to reflection, rather than a generic array type. */
    private static boolean plain(TypeMirror type) {
        return switch (type.getKind()) {
            case ARRAY -> plain(((ArrayType) type).getComponentType());
            case DECLARED -> ((DeclaredType) type).getTypeArguments().isEmpty();
            default -> type.getKind().isPrimitive();
        };
    }

    private String literal(TypeMirror type, String pkg) {
        return erased(type, pkg) + ".class";
    }

    /** The erased type as source, every class in it reachable from the package. */
    private String erased(TypeMirror type, String pkg) {
        if (type.getKind().isPrimitive()) {
            return type.getKind().name().toLowerCase(Locale.ROOT);
        }
        return switch (type.getKind()) {
            case ARRAY -> erased(((ArrayType) type).getComponentType(), pkg) + "[]";
            case DECLARED -> {
                var element = (TypeElement) ((DeclaredType) type).asElement();
                reach(element, pkg);
                yield name(element);
            }
            case TYPEVAR, WILDCARD, INTERSECTION -> erased(types().erasure(type), pkg);
            default -> throw new Unreachable();
        };
    }

    /** Throws where the type, or a type it is nested in, cannot be named from a class in {@code pkg}. */
    private void reach(TypeElement type, String pkg) {
        for (Element at = type; at instanceof TypeElement; at = at.getEnclosingElement()) {
            var modifiers = at.getModifiers();
            if (modifiers.contains(Modifier.PRIVATE) || (!modifiers.contains(Modifier.PUBLIC)
                    && !elements().getPackageOf(at).getQualifiedName().contentEquals(pkg))) {
                throw new Unreachable();
            }
        }
    }

    private static String name(TypeElement type) {
        return type.getQualifiedName().toString();
    }

    private static String list(List<String> items) {
        return items.isEmpty() ? "java.util.List.of()" : "java.util.List.of(\n            " + String.join(",\n            ", items) + ")";
    }

    private String quoted(String text) {
        return elements().getConstantExpression(text);
    }

    private static String source(String pkg, String name, List<String> puts, List<String> methods) {
        return (pkg.isEmpty() ? "" : "package " + pkg + ";\n\n")
                + "/** Written by the engine's annotation processor: what the Binder reads of this package's types. */\n"
                + "@javax.annotation.processing.Generated(\"uz.dukeengine.processor.ShapeProcessor\")\n"
                + "@java.lang.SuppressWarnings({\"unchecked\", \"rawtypes\", \"cast\", \"deprecation\", \"removal\"})\n"
                + "public final class " + name + " implements " + DATA + "ShapeSource {\n\n"
                + "    @java.lang.Override\n"
                + "    public java.util.Map<java.lang.Class<?>, " + DATA + "Shape> shapes() {\n"
                + "        var shapes = new java.util.HashMap<java.lang.Class<?>, " + DATA + "Shape>();\n"
                + String.join("", puts)
                + "        return shapes;\n    }\n\n"
                + String.join("\n", methods) + "}\n";
    }

    private void write(String qualified, String source, List<TypeElement> origins) {
        try (var out = processingEnv.getFiler().createSourceFile(qualified, origins.toArray(Element[]::new))
                .openWriter()) {
            out.write(source);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Elements elements() {
        return processingEnv.getElementUtils();
    }

    private Types types() {
        return processingEnv.getTypeUtils();
    }

    /** A type the package's class cannot name; it is left to reflection. */
    private static final class Unreachable extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Unreachable() {
            super(null, null, false, false);
        }
    }
}
