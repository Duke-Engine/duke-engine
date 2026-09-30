package uz.dukeengine.core.module;

import java.util.Collection;

/**
 * Module data that names the other templates its thing may bring into the world — a factory's build list — so the art a
 * match can draw is planned from what stands in it and what that can make, before the first of it is made.
 */
public interface Brings {

    /** The names of the templates it may bring in. */
    Collection<String> brings();
}
