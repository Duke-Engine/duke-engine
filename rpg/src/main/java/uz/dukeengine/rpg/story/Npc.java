package uz.dukeengine.rpg.story;

import java.util.List;

/**
 * A person: the creature they are, what they say when talked to, how near a hero comes before they speak, and how far
 * they stroll round home — or, a traveller, the places they walk between.
 *
 * @param talk   the conversation they greet with, or blank for none
 * @param radius how near, in world units, a hero walks before they talk of themselves
 * @param wander how far from home, in world units, they stroll when nobody is near; 0 stands still
 * @param role   what they are, under their name — a trader, a commander — or blank
 * @param route  the zones of the map a traveller walks between, in turn, round and round; empty for one who keeps home
 */
public record Npc(String name, String talk, float radius, float wander, String role, List<String> route) {

    public static final Npc DEFAULTS = new Npc("", "", 30f, 0f, "", List.of());

    public Npc {
        route = route == null ? List.of() : List.copyOf(route);
    }

    /** A person who stands still. */
    public Npc(String name, String talk, float radius) {
        this(name, talk, radius, 0f, "", List.of());
    }
}
