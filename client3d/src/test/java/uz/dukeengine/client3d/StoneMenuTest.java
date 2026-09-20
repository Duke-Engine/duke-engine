package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * What the hand can do to a menu.
 *
 * <p>A real menu, with real fonts and real geometry, driven by real cursor
 * positions — no window is opened, because none of this needs one. And nothing
 * here is told where anything was drawn: the test presses at points until it
 * finds what it is looking for, the way a player does. A test that knew the
 * layout arithmetic would be checking its own copy of it, and would go green on
 * the day the menu drew everything in the wrong place.
 *
 * <p>All four of these went wrong at once when the mouse was first wired up: the
 * open list picked an option some rows off the one under the cursor, the sliders
 * answered the keyboard only, Save and Cancel were one target rather than two,
 * and a knob could be set but not pulled.
 */
class StoneMenuTest {

    private static final float WIDTH = 800f;
    private static final float HEIGHT = 600f;

    private static StoneMenu menu() {
        var assets = new DesktopAssetManager(true);
        var font = assets.loadFont("Interface/Fonts/Default.fnt");
        return new StoneMenu(new StoneCraft(assets, font, MenuStyle.DEFAULTS), font, font,
                new Node("gui"), WIDTH, HEIGHT);
    }

    // ---- sliders ----

    /** Where the one slider on a screen turned out to be. */
    private record Bar(float y, float from, float to) {
        float middle() {
            return (from + to) / 2f;
        }
    }

    private StoneMenu sliderScreen(AtomicInteger value) {
        var menu = menu();
        menu.show("SETTINGS", "", List.of(
                new StoneMenu.Level("Volume", value::get, value::set, 5)), "", "", false);
        return menu;
    }

    /**
     * Feel along the screen for the one place that takes hold of a knob.
     *
     * <p>Taking hold is the mark, rather than the value moving: the label to the
     * left of a bar is part of the same row and a click there nudges — so "the
     * value changed" would find the whole row and call it the bar.
     */
    private static Bar findBar(StoneMenu menu) {
        for (float y = 1f; y < HEIGHT; y += 2f) {
            float from = Float.NaN;
            float to = Float.NaN;
            for (float x = 0f; x < WIDTH; x += 1f) {
                menu.click(new Vector2f(x, y));
                boolean grabbed = menu.isDragging();
                menu.release();
                if (grabbed) {
                    from = Float.isNaN(from) ? x : from;
                    to = x;
                }
            }
            if (!Float.isNaN(from)) {
                return new Bar(y, from, to);
            }
        }
        throw new AssertionError("no slider anywhere on the screen answered a click");
    }

    /** Clicking a bar puts it where the cursor is, which is what everyone tries. */
    @Test
    void aBarIsSetWhereItIsClicked() {
        var value = new AtomicInteger(50);
        var menu = sliderScreen(value);
        var bar = findBar(menu);

        menu.click(new Vector2f(bar.from(), bar.y()));
        menu.release();
        assertEquals(0, value.get(), "the far left of a bar is nothing");

        menu.click(new Vector2f(bar.to(), bar.y()));
        menu.release();
        assertEquals(100, value.get(), "and the far right is all of it");

        menu.click(new Vector2f(bar.middle(), bar.y()));
        menu.release();
        assertEquals(50f, value.get(), 3f, "and the middle is the middle");
    }

    /** And it follows the hand until the hand lets go. */
    @Test
    void aBarIsPulled() {
        var value = new AtomicInteger(50);
        var menu = sliderScreen(value);
        var bar = findBar(menu);

        menu.click(new Vector2f(bar.middle(), bar.y()));
        assertTrue(menu.isDragging(), "pressing on a bar takes hold of it");

        menu.drag(new Vector2f(bar.to(), bar.y()));
        assertEquals(100, value.get());
        menu.drag(new Vector2f(bar.from(), bar.y()));
        assertEquals(0, value.get());

        menu.release();
        menu.drag(new Vector2f(bar.to(), bar.y()));
        assertEquals(0, value.get(), "let go, and the knob stops following");
        assertFalse(menu.isDragging());
    }

    /**
     * The hand is allowed to leave the row while it is pulling.
     *
     * <p>Nobody drags along a nine-pixel line. A slider that let go the moment
     * the cursor drifted off the bar would be a slider that could not be pulled
     * at all.
     */
    @Test
    void aPulledBarKeepsTheHandThatLeftTheRow() {
        var value = new AtomicInteger(50);
        var menu = sliderScreen(value);
        var bar = findBar(menu);

        menu.click(new Vector2f(bar.middle(), bar.y()));
        menu.drag(new Vector2f(bar.to(), bar.y() + 180f));
        assertEquals(100, value.get(), "still the bar's, though the cursor is elsewhere");

        menu.drag(new Vector2f(bar.from() - 400f, bar.y() - 180f));
        assertEquals(0, value.get(), "and off the end it simply sits at nothing");
    }

    /** Beside the bar are the two nudges, and a nudge is not a grip. */
    @Test
    void besideTheBarItStepsInstead() {
        var value = new AtomicInteger(50);
        var menu = sliderScreen(value);
        var bar = findBar(menu);
        value.set(50); // the feeling-about left it wherever it last pressed

        menu.click(new Vector2f(bar.from() - 12f, bar.y()));
        assertEquals(45, value.get(), "left of it is one step down");
        assertFalse(menu.isDragging());

        menu.click(new Vector2f(bar.to() + 12f, bar.y()));
        assertEquals(50, value.get(), "and right of it is one step back up");
        assertFalse(menu.isDragging());
    }

    // ---- the open list ----

    /**
     * A list that drops open picks the line the cursor is actually on.
     *
     * <p>It once picked one several rows below, because the options were counted
     * into the same list as the rows behind them and an index was two things at
     * once. Nothing about that was visible from the code; it was visible
     * immediately from the chair.
     */
    @Test
    void anOpenListPicksWhatTheCursorIsOn() {
        var chosen = new AtomicInteger(0);
        var options = List.of("2560 × 1600", "1920 × 1080", "1600 × 900",
                "1366 × 768", "1280 × 720");
        var taken = new AtomicInteger(-1);
        var menu = menu();
        menu.show("SETTINGS", "", List.of(new StoneMenu.Opens("Size", options,
                chosen::get, to -> {
                    chosen.set(to);
                    taken.set(to);
                })), "", "", false);

        var picked = new TreeMap<Float, Integer>();
        for (float y = 0f; y < HEIGHT; y += 2f) {
            for (float x = 0f; x < WIDTH; x += 20f) {
                taken.set(-1);
                menu.enter(); // the one row is the list, so this opens it
                menu.click(new Vector2f(x, y));
                if (taken.get() >= 0) {
                    picked.put(y, taken.get());
                }
            }
        }

        assertEquals(options.size(), picked.values().stream().distinct().count(),
                "every option in the list should be reachable with the mouse");
        // Screen y counts upward, so the first option sits highest: going down
        // the list the index only ever rises. A wrong offset breaks the run.
        int last = -1;
        for (var line : picked.descendingMap().entrySet()) {
            assertTrue(line.getValue() == last || line.getValue() == last + 1,
                    "at y=" + line.getKey() + " the cursor picked " + line.getValue()
                            + " after " + last + ", so the list is out of order under it");
            last = line.getValue();
        }
        assertEquals(0, picked.lastEntry().getValue(), "the top line is the first option");
        assertEquals(options.size() - 1, picked.firstEntry().getValue(),
                "and the bottom line is the last");
    }

    /** Clicking off an open list is how a person closes one. */
    @Test
    void clickingAwayFromAnOpenListClosesIt() {
        var chosen = new AtomicInteger(0);
        var menu = menu();
        menu.show("SETTINGS", "", List.of(new StoneMenu.Opens("Size",
                List.of("A", "B", "C"), chosen::get, chosen::set)), "", "", false);
        menu.enter();

        assertFalse(menu.click(new Vector2f(4f, 4f)), "the corner is not one of the options");
        assertEquals(0, chosen.get(), "and nothing was chosen by closing it");
    }

    // ---- the two buttons ----

    /**
     * Save and Cancel are two targets with stone between them.
     *
     * <p>They were one row before, which meant a click anywhere along it took
     * whichever the keyboard happened to be on — including Cancel, when what the
     * player pressed was Save.
     */
    @Test
    void saveAndCancelAreTwoSeparateButtons() {
        var saved = new ArrayList<Float>();
        var cancelled = new ArrayList<Float>();
        var missed = new ArrayList<Float>();
        var menu = menu();
        var at = new float[1];
        menu.show("SETTINGS", "", List.of(
                new StoneMenu.Level("Volume", () -> 50, to -> { }, 5),
                new StoneMenu.Buttons("", "Save", () -> saved.add(at[0]),
                        "Cancel", () -> cancelled.add(at[0]), "unsaved changes")),
                "", "", false);

        for (float y = 0f; y < HEIGHT; y += 2f) {
            for (float x = 0f; x < WIDTH; x += 2f) {
                at[0] = x;
                int before = saved.size() + cancelled.size();
                menu.click(new Vector2f(x, y));
                menu.release();
                if (saved.size() + cancelled.size() == before) {
                    missed.add(x);
                }
            }
        }

        assertFalse(saved.isEmpty(), "Save was not clickable anywhere");
        assertFalse(cancelled.isEmpty(), "Cancel was not clickable anywhere");
        float lastSave = saved.stream().max(Float::compare).orElseThrow();
        float firstCancel = cancelled.stream().min(Float::compare).orElseThrow();
        assertTrue(lastSave < firstCancel,
                "Save is the left socket and Cancel the right; they must not overlap");
        assertTrue(missed.stream().anyMatch(x -> x > lastSave && x < firstCancel),
                "the stone between the two buttons should not be a button");
    }

    // ---- what a row says and shows ----

    /**
     * A line under an option saying what taking it means is drawn.
     *
     * <p>It was not: a menu drew a row's {@code label()}, and a question's lines carry theirs in {@code value()},
     * so every blurb was a blank forty-four pixels between two names.
     */
    @Test
    void theLineUnderAnOptionIsRead() {
        var gui = new Node("gui");
        var menu = menu(gui);
        menu.show("DUKE", "How to play", List.of(
                new StoneMenu.Action("Endless", () -> { }),
                new StoneMenu.Words("", "Down until it kills you."),
                new StoneMenu.Action("Back", () -> { })),
                "", "", false);

        assertTrue(writtenIn(gui).contains("Down until it kills you."),
                "the blurb should be on the screen: " + writtenIn(gui));
    }

    /** The lit row's picture, beside the list — and nothing at all where the game ships no such file. */
    @Test
    void theLitRowsPictureIsShownBesideTheList() {
        var gui = new Node("gui");
        var menu = menu(gui);
        menu.show("DUKE", "", List.of(
                new StoneMenu.Action("The First Descent", () -> { }, false, "Interface/Fonts/Default.png"),
                new StoneMenu.Action("Back", () -> { })),
                "", "", false);
        assertTrue(drawn(gui, "picture-plate"), "the first row is the lit one, and it has a picture");

        menu.show("DUKE", "", List.of(
                new StoneMenu.Action("The First Descent", () -> { }, false, "maps/nowhere/preview.png"),
                new StoneMenu.Action("Back", () -> { })),
                "", "", false);
        assertFalse(drawn(gui, "picture-plate"), "a picture the game does not ship leaves the row as it was");
    }

    private static StoneMenu menu(Node gui) {
        var assets = new DesktopAssetManager(true);
        var font = assets.loadFont("Interface/Fonts/Default.fnt");
        return new StoneMenu(new StoneCraft(assets, font, MenuStyle.DEFAULTS), font, font, gui, WIDTH, HEIGHT);
    }

    private static List<String> writtenIn(Node gui) {
        var found = new ArrayList<String>();
        gui.depthFirstTraversal(spatial -> {
            if (spatial instanceof com.jme3.font.BitmapText text) {
                found.add(text.getText());
            }
        });
        return found;
    }

    private static boolean drawn(Node gui, String name) {
        var found = new ArrayList<String>();
        gui.depthFirstTraversal(spatial -> found.add(spatial.getName()));
        return found.contains(name);
    }
}
