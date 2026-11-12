package com.jme3.system.web.teavm;

import java.util.List;
import org.teavm.extension.introspect.IntrospectAccess;
import org.teavm.extension.introspect.IntrospectClass;
import org.teavm.extension.spi.reflection.SimpleReflectionPolicy;

/** What jME reaches by reflection. */
public class JmeReflection extends SimpleReflectionPolicy {
    @Override
    protected void setup() {
        // Asset loaders, locators, caches and processors are made from their class by the no-argument constructor.
        List<IntrospectClass<?>> made = classes("com.jme3.asset.AssetLoader", "com.jme3.asset.AssetLocator",
                "com.jme3.asset.cache.AssetCache", "com.jme3.asset.AssetProcessor");
        selectClasses(c -> made.stream().anyMatch(m -> m.isAssignableFrom(c)))
                .reflectableMethods(m -> m.isConstructor() && m.parameters().isEmpty());
        // A technique is cloned with a new logic made by its (TechniqueDef) constructor.
        List<IntrospectClass<?>> logic = classes("com.jme3.material.logic.TechniqueDefLogic");
        selectClasses(c -> logic.get(0).isAssignableFrom(c))
                .reflectableMethods(m -> m.isConstructor() && m.parameters().size() == 1);
        // A uniform's first value is a fresh one of the value's class (a vector, colour, matrix...).
        selectClasses(c -> c.name().startsWith("com.jme3.math."))
                .reflectableMethods(m -> m.isConstructor() && m.parameters().isEmpty());
        // Cloner copies a Cloneable by its public clone().
        selectClasses(c -> true).reflectableMethods(m -> m.name().equals("clone") && m.parameters().isEmpty()
                && m.access() == IntrospectAccess.PUBLIC);
    }

    private List<IntrospectClass<?>> classes(String... names) {
        return java.util.Arrays.stream(names).<IntrospectClass<?>>map(n -> environment().findClass(n))
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toList());
    }
}
