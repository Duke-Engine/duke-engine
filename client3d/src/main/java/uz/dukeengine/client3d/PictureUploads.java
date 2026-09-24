package uz.dukeengine.client3d;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Which of the game's pictures the card has to be handed again: one it has never had, or one changed since it was
 * last handed over. Held weakly, so a picture the game lets go of is forgotten here too.
 */
final class PictureUploads {

    private final Map<Picture, Integer> handedOver = new WeakHashMap<>();

    /** Whether {@code picture} must be handed to the card now; from here on it counts as handed over. */
    boolean needsUpload(Picture picture) {
        var last = handedOver.put(picture, picture.version());
        return last == null || last != picture.version();
    }
}
