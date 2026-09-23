package uz.dukeengine.core.thing;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Which of several things said for some set of conditions fits best: the one rule the engine has for choosing
 * by condition, whether what is chosen is a look (a template's conditional models) or its weapons (a weapon
 * set). One rule so that a game writes its conditions one way everywhere, and a set of words that picks a
 * model picks weapons the same way.
 *
 * <p>A condition is only a word. The engine defines none of them and knows what none of them mean.
 */
public final class Conditions {

    private Conditions() {
    }

    /**
     * The index of the candidate that fits best, or -1 where none fits.
     *
     * <p>A candidate fits when every word of it holds — so one of no words always does, and is what fits when
     * nothing more particular does. Among those that fit, the one with the most words wins: {@code DAMAGED SNOW}
     * beats {@code SNOW} beats nothing said, because the more a candidate says about the moment, the better it
     * describes it. <b>A tie goes to the words themselves</b>, sorted and compared, and only then to the earlier
     * candidate — not to whichever a map happened to hand over first, since two machines must choose alike.
     *
     * @param candidates what each candidate says must hold, in the order they were written
     * @param holding    the words that hold now
     */
    public static int bestFit(List<? extends Collection<String>> candidates, Set<String> holding) {
        int best = -1;
        String bestWords = null;
        int most = -1;
        for (int at = 0; at < candidates.size(); at++) {
            var words = new TreeSet<>(candidates.get(at));
            if (!holding.containsAll(words)) {
                continue;
            }
            var sorted = String.join(" ", words);
            if (best < 0 || words.size() > most || words.size() == most && sorted.compareTo(bestWords) < 0) {
                best = at;
                bestWords = sorted;
                most = words.size();
            }
        }
        return best;
    }
}
