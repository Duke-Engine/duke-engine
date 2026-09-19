package uz.duke.dungeon.world;

import java.util.List;
import uz.duke.dungeon.content.PortraitArt;

/**
 * The hero's panel: every word it says and every picture beside a word, and the face it frames.
 *
 * <p>Words rather than anything the client decides, because the client draws four games and
 * speaks none of their languages.
 *
 * @param depthWord         the word under the depth numeral
 * @param rankSuffix        what turns a level into the words beside his name — added straight onto
 *     the number, so "-daraja" makes "7-daraja" and " lv" would make "7 lv"
 * @param pointsWord        what an unspent level is called, beside the skill heading
 * @param monsterFace       the drawing that stands in the portrait for something that is not his
 * @param heroTitle         what he is, drawn under his name
 * @param chooseModeWord    the first screen: which of the two games is being played
 * @param chooseStageWord   the screen after it, when he chose the frozen kind
 * @param attackWord        what the figures beside the attributes are called: attack and armour on
 *     a hero's card, attack and speed on a creature's; speed is also the row on an attribute's card
 * @param healthWord        what maximum health is called on an attribute's card
 * @param primaryWord       what an attribute's card says under its name when it is his primary
 * @param eachPointWord     the line over what one point of an attribute gives
 * @param speedNowWord      what his speed is called on the card of an attribute that gives speed
 * @param manaWord          what a skill's price is called on its card, and the bar it comes out of
 * @param paintedSkillIcons whether the skill pictures carry their own colours. The panel tints what
 *     it draws, which is how one white drawing serves a skill that is ready, one reloading and one
 *     locked; painted pictures cannot take that, so a game that ships them says so here and the
 *     panel tells the states in brightness instead
 * @param portraitFps       how many times a second the portrait is redrawn — a ceiling, not a
 *     target, and panel-wide: what a portrait costs is a fact about the machine drawing it
 * @param portrait          the portrait every selectable creature gets unless its own block frames
 *     one, or null for none. The camera is written in fractions of whatever it is looking at, so
 *     one framing fits a skeleton and a hero alike
 */
public record Hud(String depthWord, String rankSuffix, String skillsWord, String pointsWord, String masterWord,
        String damageWord, String cooldownWord, String radiusWord, String rangeWord, String boostWord,
        String secondsWord, String raiseKeyWord, String raiseWord, String maxedWord, String noPointsWord,
        String lockedWord, String itemsWord, String heroTitle, String monsterFace, String cmdMoveWord,
        String cmdAttackWord, String cmdStopWord, String cmdGuardWord, String chooseHeroWord,
        String chooseHeroHint, String chooseModeWord, String chooseModeHint, String endlessWord,
        String endlessBlurb, String stagesWord, String stagesBlurb, String chooseStageWord,
        String chooseStageHint, String attackWord, String armourWord, String speedWord, String healthWord,
        String primaryWord, String eachPointWord, String speedNowWord, String manaWord,
        boolean paintedSkillIcons, String cmdMoveIcon, String cmdAttackIcon, String cmdStopIcon,
        String cmdGuardIcon, String attackIcon, String armourIcon, String speedIcon, int portraitFps,
        PortraitArt portrait) {

    /** What a block leaves out. */
    public static final Hud DEFAULTS = new Hud("DEPTH", "-lv", "", "", "", "", "", "", "", "", "", "", "", "",
            "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
            "", "", false, "", "", "", "", "", "", "", 24, null);

    /** The four orders on the buttons beside the map, in the order they are drawn. */
    public List<String> orderWords() {
        return List.of(cmdMoveWord, cmdAttackWord, cmdStopWord, cmdGuardWord);
    }

    /** The four order buttons' pictures, in the order the buttons are drawn. */
    public List<String> orderIcons() {
        return List.of(cmdMoveIcon, cmdAttackIcon, cmdStopIcon, cmdGuardIcon);
    }

    /**
     * The pictures beside the three figures under the bars, in their own order — beside the figures
     * rather than counted off against them, so the game rather than the client says which is which.
     */
    public List<String> statIcons() {
        return List.of(attackIcon, armourIcon, speedIcon);
    }
}
