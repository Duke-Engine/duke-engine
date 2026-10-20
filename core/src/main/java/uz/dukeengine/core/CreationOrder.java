package uz.dukeengine.core;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.thing.GameObject;

/** Lists of things kept in the order they came into the world ({@link GameObject#getEntered}), searched by halves. */
final class CreationOrder {

    private CreationOrder() {
    }

    /** Where {@code thing} is in {@code things}: found by halves; -1 for nowhere. */
    static int indexOf(List<GameObject> things, GameObject thing) {
        long entered = thing.getEntered();
        int low = 0;
        int high = things.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            long at = things.get(middle).getEntered();
            if (at < entered) {
                low = middle + 1;
            } else if (at > entered) {
                high = middle - 1;
            } else {
                return things.get(middle) == thing ? middle : -1;
            }
        }
        return -1;
    }

    /** Take {@code thing} out of {@code things}; whether it was there. */
    static boolean takeOut(List<GameObject> things, GameObject thing) {
        int at = indexOf(things, thing);
        if (at < 0) {
            return false;
        }
        things.remove(at);
        return true;
    }

    /** Put {@code thing} into {@code things} where its turn puts it. */
    static void putIn(List<GameObject> things, GameObject thing) {
        long entered = thing.getEntered();
        int low = 0;
        int high = things.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (things.get(middle).getEntered() < entered) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        things.add(low, thing);
    }

    /** Two lists each in the order things came into the world, as one in that order, a thing in both once. */
    static List<GameObject> merged(List<GameObject> one, List<GameObject> two) {
        var both = new ArrayList<GameObject>(one.size() + two.size());
        int i = 0;
        int j = 0;
        while (i < one.size() || j < two.size()) {
            if (j == two.size() || i < one.size() && one.get(i).getEntered() < two.get(j).getEntered()) {
                both.add(one.get(i++));
            } else if (i == one.size() || two.get(j).getEntered() < one.get(i).getEntered()) {
                both.add(two.get(j++));
            } else {
                both.add(one.get(i++));
                j++;
            }
        }
        return both;
    }
}
