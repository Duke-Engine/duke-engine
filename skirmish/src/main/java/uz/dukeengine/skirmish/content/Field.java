package uz.dukeengine.skirmish.content;

import uz.dukeengine.core.thing.WorldTemplate;

/**
 * The world a skirmish is fought in: one open field, and nothing else to say about it.
 *
 * <p>A record with a single component looks like a record that need not exist. It is here because it is the
 * answer to a question the engine asks every game — {@code WorldTemplate} — and the interesting part is what it
 * does <em>not</em> implement: {@code Layered}. The dungeon's world is built in storeys and every map is laid at
 * its height; a field is flat, so the whole idea is absent rather than set to zero.
 */
public record Field(String name) implements WorldTemplate {

    static final Field DEFAULTS = new Field("Field");

    public Field {
        name = name == null || name.isBlank() ? "Field" : name;
    }
}
