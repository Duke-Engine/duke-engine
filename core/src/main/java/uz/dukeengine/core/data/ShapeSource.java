package uz.dukeengine.core.data;

import java.util.Map;

/**
 * The shapes one package's types were given at compile time. The engine's annotation processor writes one
 * for each package it compiles and lists it under {@code META-INF/services}; {@link Shapes} finds them all.
 */
public interface ShapeSource {

    Map<Class<?>, Shape> shapes();
}
