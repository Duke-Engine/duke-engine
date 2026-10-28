package uz.dukeengine.client3d;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * How the player's controls look and answer, held for {@link Visuals}: camera, pointer, selection, the answers to
 * orders, the bars and marks over things, menus and panels.
 */
final class ControlLooks {

    EdgeScroll edgeScroll = EdgeScroll.NONE;
    CameraFrame cameraFrame = CameraFrame.NONE;
    RightDrag rightDrag = RightDrag.NONE;
    Visuals.DragBoxLook dragBox;
    float dragDistance = SelectionBox.DRAG_THRESHOLD_PIXELS;
    boolean dragSelects = true;
    boolean selectionRings = true;
    Visuals.GhostLook ghostLook;
    OrderMark orderMark = OrderMark.DEFAULTS;
    final Map<String, String> orderAnswers = new HashMap<>();
    final Map<String, Function<AnswerTarget, String>> aimedAnswers = new HashMap<>();
    final Map<String, BiFunction<AnswerTarget, AnswerSelection, String>> selectedAnswers = new HashMap<>();
    Visuals.SelectionFlashLook selectionFlash;
    final Map<String, Visuals.WordMark> wordMarks = new HashMap<>();
    BarColours barColours = BarColours.REFERENCE;
    RallyLook rally = RallyLook.DEFAULT;
    UnitBarLook unitBars = UnitBarLook.NONE;
    RangeLook rangeLook = RangeLook.DEFAULT;
    final Map<Character, SkillRange> skillRanges = new LinkedHashMap<>();
    MenuStyle menuStyle = MenuStyle.DEFAULTS;
    PanelSkin panelSkin = PanelSkin.NONE;
    final Map<String, Cursors.Look> pointers = new LinkedHashMap<>();

    /** The moment that answers a click giving {@code word}: the game's, none (null), or a move's. */
    String orderAnswerFor(String word) {
        return named(word, "move");
    }

    private String named(String word, String plain) {
        return word != null && orderAnswers.containsKey(word) ? orderAnswers.get(word) : plain;
    }

    /** The moment that answers a click giving {@code word} on {@code target} (null for the ground). */
    String orderAnswerFor(String word, AnswerTarget target) {
        return orderAnswerFor(word, target, "move");
    }

    private String orderAnswerFor(String word, AnswerTarget target, String plain) {
        var aimed = word == null ? null : aimedAnswers.get(word);
        var moment = aimed == null ? null : aimed.apply(target);
        return moment != null ? moment : named(word, plain);
    }

    /**
     * The moment that answers a click giving {@code word} on {@code target} (null for the ground) by the selection
     * {@code selection} (null for none): the rule that reads both, else the one that reads the target, else the word's.
     */
    String orderAnswerFor(String word, AnswerTarget target, AnswerSelection selection) {
        return orderAnswerFor(word, target, selection, "move");
    }

    /** The same, {@code plain} answering where the game named nothing for the word. */
    String orderAnswerFor(String word, AnswerTarget target, AnswerSelection selection, String plain) {
        var both = word == null ? null : selectedAnswers.get(word);
        var moment = both == null ? null : both.apply(target, selection);
        return moment != null ? moment : orderAnswerFor(word, target, plain);
    }

    Visuals.WordMark wordMarkFor(String word) {
        return word == null ? Visuals.WordMark.MARK : wordMarks.getOrDefault(word, Visuals.WordMark.MARK);
    }
}
