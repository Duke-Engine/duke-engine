package com.jme3.system.web.teavm;

import java.util.List;
import org.teavm.extension.introspect.IntrospectAccess;
import org.teavm.extension.introspect.IntrospectClass;
import org.teavm.extension.spi.reflection.SimpleReflectionPolicy;

/** What jME reaches by reflection. */
public class JmeReflection extends SimpleReflectionPolicy {
    @Override
    protected void setup() {
        // Asset loaders, locators, caches and processors, the glTF loader's JSON parser, extras and extension loaders,
        // and a joint's model transform are made from their class by
        // the no-argument constructor.
        List<IntrospectClass<?>> made = classes("com.jme3.asset.AssetLoader", "com.jme3.asset.AssetLocator",
                "com.jme3.asset.cache.AssetCache", "com.jme3.asset.AssetProcessor", "com.jme3.plugins.json.JsonParser",
                "com.jme3.scene.plugins.gltf.ExtrasLoader", "com.jme3.scene.plugins.gltf.ExtensionLoader",
                "com.jme3.anim.util.JointModelTransform");
        selectClasses(c -> made.stream().anyMatch(m -> isA(c, m)))
                .reflectableMethods(m -> m.isConstructor() && m.parameters().isEmpty());
        // A technique is cloned with a new logic made by its (TechniqueDef) constructor.
        List<IntrospectClass<?>> logic = classes("com.jme3.material.logic.TechniqueDefLogic");
        selectClasses(c -> isA(c, logic.get(0)))
                .reflectableMethods(m -> m.isConstructor() && m.parameters().size() == 1);
        // A uniform's first value is a fresh one of the value's class (a vector, colour, matrix...).
        selectClasses(c -> c != null && c.name().startsWith("com.jme3.math."))
                .reflectableMethods(m -> m.isConstructor() && m.parameters().isEmpty());
        // Cloner copies a Cloneable by its public clone().
        selectClasses(c -> c != null).reflectableMethods(m -> m.name().equals("clone") && m.parameters().isEmpty()
                && m.access() == IntrospectAccess.PUBLIC);
    }

    /** {@code c} is a {@code type}: not when TeaVM cannot find c or a supertype of it (null, or a throw), which it reports itself. */
    private static boolean isA(IntrospectClass<?> c, IntrospectClass<?> type) {
        try {
            return type.isAssignableFrom(c);
        } catch (NullPointerException e) {
            return false;
        }
    }

    private List<IntrospectClass<?>> classes(String... names) {
        return java.util.Arrays.stream(names).<IntrospectClass<?>>map(n -> environment().findClass(n))
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toList());
    }
}
