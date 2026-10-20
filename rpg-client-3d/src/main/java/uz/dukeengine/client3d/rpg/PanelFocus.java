package uz.dukeengine.client3d.rpg;

import com.jme3.math.FastMath;
import java.util.HashMap;
import java.util.Map;

/**
 * What the player's hand is on — the skill waiting for a click, the slot under the cursor — and the keys the
 * game moved, shared by the pieces of the bar that light up for them.
 */
final class PanelFocus {

    /** The skill waiting for the player to click something, if any. */
    Character armed;
    /** The slot the mouse is over, if any — it lights to say it can be clicked. */
    Character hovered;
    /** Wall clock, for the armed slot's breathing — presentation only. */
    float clock;

    /** The wall clock as a swell: {@code base} plus {@code swing} of a sine, {@code perSecond} times a second. */
    float breath(float base, float swing, float perSecond) {
        return base + swing * FastMath.sin(clock * FastMath.TWO_PI * perSecond);
    }

    /** The letter each slot is drawn and pressed on, by its skill's key, where the game moved it. */
    private final Map<Character, Character> letters = new HashMap<>();

    /** The letter a slot's skill is pressed on: the one the game gave it, or its own key. */
    char letterOf(char key) {
        return letters.getOrDefault(key, key);
    }

    void readTheLetters(String status) {
        letters.clear();
        if (status == null) {
            return;
        }
        for (var field : status.split("\\|")) {
            if (field.startsWith("letter=") && field.length() >= "letter=K,L".length()) {
                letters.put(field.charAt(7), field.charAt(9));
            }
        }
    }
}
