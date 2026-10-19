package uz.dukeengine.rpg.story;

/**
 * Somebody a conversation's lines may be said by: the name the lines use, what the box calls them, and the picture
 * framed beside their words — none draws the first letter of their name. {@link #HERO} needs no block: it is whoever
 * the player plays.
 */
public record Speaker(String name, String displayName, String picture) {

    /** The speaker every file knows: the hero the player plays. */
    public static final String HERO = "Hero";

    public static final Speaker DEFAULTS = new Speaker("", "", "");
}
