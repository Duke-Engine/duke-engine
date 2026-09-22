package uz.dukeengine.core.map;

import java.util.List;

/** A map that comes with things already standing on it: see {@link MapThing}. */
public interface Furnished extends MapTemplate {

    /**
     * What stands on it when it is laid, in the order the file wrote them.
     *
     * <p>The order is the contract. Whatever a game makes of these it makes in this order — object ids are
     * handed out as things are created, and two machines that walk the same list differently are two machines
     * that disagree about which tank is which before either has drawn a frame.
     */
    List<? extends MapThing> things();
}
