package uz.dukeengine.rpg.story;

/** What a story's log and script look things up in, by name: the game's own files, however it keeps them. */
public interface Lore {

    /** The quest of that name, or null. */
    Quest quest(String name);

    /** The conversation of that name, or null. */
    Conversation conversation(String name);

    /** The person of that name, or null. */
    Npc npc(String name);
}
