package uz.dukeengine.core.map;

import java.util.List;

/** A map cut into named pieces with shapes of their own, water among them: see {@link MapArea}. */
public interface Zoned extends MapTemplate {

    /** Its areas, in the order the file wrote them — so anything decided from them is decided the same way twice. */
    List<? extends MapArea> areas();
}
