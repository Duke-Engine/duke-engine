package uz.dukeengine.client3d;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.game.view.UnitView;

/**
 * The client's own controls, on the keys the game gave them ({@link KeyMap}): what a key does to the selection,
 * the camera, the control groups and the bookmarks. Held apart from the window because this is the part with
 * answers that can be wrong, and none of it needs one.
 *
 * <p>All of it is the local player's and none of it the simulation's: a group, a bookmark and what is selected
 * are this machine's, and the one thing that leaves — an order to stop or to scatter — goes as a command, the
 * road every order travels.
 */
final class Controls {

    /** How far a unit scatters: four times its own size, as {@code AIGroup::groupScatter} sends it. */
    private static final float SCATTER_SIZES = 4f;

    /** What the client is drawing, and what a control may do to it. */
    interface Scene {

        /** The units this frame, as the snapshot has them. */
        List<UnitView> units();

        int localPlayer();

        /** What is selected, changed in place. */
        Set<Integer> selection();

        /** Whether a unit is on the screen now. */
        boolean onScreen(UnitView unit);

        /** Whether a unit's template has this kind. */
        boolean hasKind(UnitView unit, String kind);

        /** How wide a unit is: the radius round it on the ground. */
        float sizeOf(UnitView unit);

        CameraFocus camera();

        /** Tell these units to stop. */
        void stop(List<Integer> units);

        /** Send each of these units to its own place. */
        void move(Map<Integer, Coord3D> destinations);
    }

    private final KeyMap map;
    private final Map<Integer, List<Integer>> groups = new HashMap<>();
    private final Map<Integer, CameraFocus.View> bookmarks = new HashMap<>();
    private final Set<KeyMap.Control> held = EnumSet.noneOf(KeyMap.Control.class);
    private float[] latestAlert;
    private boolean gamesAlerts;

    private int lastGroup = -1;
    private float lastGroupAt = Float.NEGATIVE_INFINITY;
    private float lastSameTypeAt = Float.NEGATIVE_INFINITY;
    private int cycled = -1;

    Controls(KeyMap map) {
        this.map = map;
    }

    KeyMap map() {
        return map;
    }

    /**
     * What a key did: whether it was one of these at all, and the control it was, if it was one — null for a
     * control group's key or a bookmark's, which are done here and hand nothing back.
     */
    record Press(boolean taken, KeyMap.Control control) {

        static final Press NOTHING = new Press(false, null);
    }

    /**
     * A key went down, with these modifiers held. Controls about the selection, the camera, the groups and the
     * bookmarks are done here; the rest — the menu, the command bar, chat, a picture, pause, fullscreen — are
     * handed back for the client to do.
     */
    Press press(KeyMap.Key key, float now, Scene scene) {
        if (group(key, now, scene) || bookmark(key, scene)) {
            return new Press(true, null);
        }
        var control = controlOn(key);
        if (control == null) {
            return Press.NOTHING;
        }
        if (control.isHeld()) {
            held.add(control);
            return new Press(true, control);
        }
        switch (control) {
            case STOP -> scene.stop(own(scene));
            case SCATTER -> scatter(scene);
            case SELECT_ALL -> selectAll(scene, null);
            case SELECT_ALL_OF_KIND -> {
                if (map.kindToSelect() != null) {
                    selectAll(scene, map.kindToSelect());
                }
            }
            case SELECT_SAME_TYPE -> sameType(scene, now);
            case NEXT_UNIT -> cycle(scene, 1);
            case PREVIOUS_UNIT -> cycle(scene, -1);
            case CENTRE_ON_BASE -> centreOnBase(scene);
            case LATEST_ALERT -> {
                if (latestAlert != null) {
                    scene.camera().lookAt(latestAlert[0], latestAlert[1]);
                }
            }
            case RESET_VIEW -> scene.camera().resetView();
            default -> {
                // the client's own: the menu, the bar, chat, a picture, pause and fullscreen
            }
        }
        return new Press(true, control);
    }

    /** A key came up: whatever held control it was on is let go. */
    void release(int code) {
        held.removeIf(control -> map.keysOf(control).stream().anyMatch(key -> key.code() == code));
    }

    /** Whether a held control is held now. */
    boolean isHeld(KeyMap.Control control) {
        return held.contains(control);
    }

    /** Every held control let go at once — the window lost the keyboard, say. */
    void releaseAll() {
        held.clear();
    }

    /**
     * The control on this key: one whose key it is exactly, modifiers and all. A held control is found by its
     * key alone, so a pan held with Shift down still pans.
     */
    KeyMap.Control controlOn(KeyMap.Key key) {
        KeyMap.Control loose = null;
        for (var control : KeyMap.Control.values()) {
            for (var on : map.keysOf(control)) {
                if (on.equals(key)) {
                    return control;
                }
                if (loose == null && control.isHeld() && on.code() == key.code()) {
                    loose = control;
                }
            }
        }
        return loose;
    }

    /**
     * Something worth looking at happened here — a thing of his hurt: {@link KeyMap.Control#LATEST_ALERT} goes to the
     * latest, unless the game keeps its alerts itself ({@link #gamesAlert}).
     */
    void alert(float x, float z) {
        if (!gamesAlerts) {
            latestAlert = new float[] {x, z};
        }
    }

    /** The game's own latest alert, or null for none yet: the key looks there, and his hurt things move it no more. */
    void gamesAlert(uz.dukeengine.core.math.Coord2D place) {
        gamesAlerts = true;
        latestAlert = place == null ? null : new float[] {place.x(), place.y()};
    }

    /** A unit is dead, and gone from every group it was in. */
    void died(int unit) {
        for (var members : groups.values()) {
            members.remove(Integer.valueOf(unit));
        }
    }

    // ---- control groups ----

    private boolean group(KeyMap.Key key, float now, Scene scene) {
        for (var entry : map.groups().entrySet()) {
            int number = entry.getKey();
            var keys = entry.getValue();
            if (key.equals(keys.create())) {
                groups.put(number, new ArrayList<>(own(scene)));
                return true;
            }
            if (key.equals(keys.select()) || key.equals(keys.add())) {
                // SelectionXlat: the same group again soon after puts the camera on it, and selects nothing new.
                boolean again = number == lastGroup && now - lastGroupAt < map.doublePressSeconds();
                lastGroup = number;
                lastGroupAt = now;
                if (again) {
                    centreOn(scene, number);
                } else {
                    if (key.equals(keys.select())) {
                        scene.selection().clear();
                    }
                    scene.selection().addAll(living(scene, number));
                }
                return true;
            }
            if (key.equals(keys.centre())) {
                centreOn(scene, number);
                return true;
            }
        }
        return false;
    }

    /** A group's members that are still on the map, in the order they were grouped. */
    private List<Integer> living(Scene scene, int number) {
        var members = groups.getOrDefault(number, List.of());
        var here = new LinkedHashSet<Integer>();
        for (var unit : scene.units()) {
            here.add(unit.id());
        }
        return members.stream().filter(here::contains).toList();
    }

    /** The camera on a group: on the last of it, as the reference puts it. */
    private void centreOn(Scene scene, int number) {
        var members = living(scene, number);
        if (members.isEmpty()) {
            return;
        }
        var last = find(scene, members.getLast());
        if (last != null) {
            scene.camera().lookAt(last.x(), last.y());
        }
    }

    List<Integer> group(int number) {
        return List.copyOf(groups.getOrDefault(number, List.of()));
    }

    // ---- bookmarks ----

    private boolean bookmark(KeyMap.Key key, Scene scene) {
        for (var entry : map.bookmarks().entrySet()) {
            if (key.equals(entry.getValue().save())) {
                bookmarks.put(entry.getKey(), scene.camera().view());
                return true;
            }
            if (key.equals(entry.getValue().recall())) {
                var view = bookmarks.get(entry.getKey());
                if (view != null) {
                    scene.camera().restore(view);
                }
                return true;
            }
        }
        return false;
    }

    // ---- selecting ----

    /**
     * {@code MSG_META_SELECT_ALL}: every one of his units — not a building, and not a kind the game leaves out;
     * with a kind, only those of it.
     */
    private void selectAll(Scene scene, String kind) {
        var picked = new ArrayList<Integer>();
        for (var unit : scene.units()) {
            if (unit.playerIndex() != scene.localPlayer() || unit.structure() || !unit.selectable()) {
                continue;
            }
            if (kind != null && !scene.hasKind(unit, kind)) {
                continue;
            }
            if (map.leftOutOfSelectAll().stream().anyMatch(out -> scene.hasKind(unit, out))) {
                continue;
            }
            picked.add(unit.id());
        }
        scene.selection().clear();
        scene.selection().addAll(picked);
    }

    /** Every one of his units of a type selected, on the screen — and, pressed again soon after, anywhere. */
    private void sameType(Scene scene, float now) {
        boolean everywhere = now - lastSameTypeAt < map.doublePressSeconds();
        lastSameTypeAt = now;
        var types = new LinkedHashSet<String>();
        for (var unit : scene.units()) {
            if (scene.selection().contains(unit.id()) && unit.playerIndex() == scene.localPlayer()) {
                types.add(unit.templateName());
            }
        }
        if (types.isEmpty()) {
            return;
        }
        var picked = new ArrayList<Integer>();
        for (var unit : scene.units()) {
            if (unit.playerIndex() == scene.localPlayer() && types.contains(unit.templateName())
                    && (everywhere || scene.onScreen(unit))) {
                picked.add(unit.id());
            }
        }
        scene.selection().clear();
        scene.selection().addAll(picked);
    }

    /** The next of his units, or the one before, selected and looked at. */
    private void cycle(Scene scene, int step) {
        var his = scene.units().stream()
                .filter(unit -> unit.playerIndex() == scene.localPlayer() && !unit.structure() && unit.selectable())
                .toList();
        if (his.isEmpty()) {
            return;
        }
        int current = -1;
        for (int index = 0; index < his.size(); index++) {
            if (his.get(index).id() == cycled) {
                current = index;
                break;
            }
        }
        int at = current < 0 ? (step > 0 ? 0 : his.size() - 1) : Math.floorMod(current + step, his.size());
        var unit = his.get(at);
        cycled = unit.id();
        scene.selection().clear();
        scene.selection().add(unit.id());
        scene.camera().lookAt(unit.x(), unit.y());
    }

    private void centreOnBase(Scene scene) {
        for (var unit : scene.units()) {
            if (unit.playerIndex() == scene.localPlayer()
                    && (map.baseKind() == null || scene.hasKind(unit, map.baseKind()))) {
                scene.camera().lookAt(unit.x(), unit.y());
                return;
            }
        }
    }

    // ---- orders ----

    /**
     * {@code AIGroup::groupScatter}: each away from the middle of them all, by four times its own size — the
     * middle nudged aside a hair per unit, so one standing on it still has somewhere to go.
     */
    private void scatter(Scene scene) {
        var units = scene.units().stream()
                .filter(unit -> scene.selection().contains(unit.id())
                        && unit.playerIndex() == scene.localPlayer() && !unit.structure())
                .toList();
        if (units.isEmpty()) {
            return;
        }
        float middleX = 0f;
        float middleY = 0f;
        for (var unit : units) {
            middleX += unit.x();
            middleY += unit.y();
        }
        middleX /= units.size();
        middleY /= units.size();
        var destinations = new LinkedHashMap<Integer, Coord3D>();
        for (var unit : units) {
            middleX -= 0.01f;
            float dx = unit.x() - middleX;
            float dy = unit.y() - middleY;
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length == 0f) {
                continue;
            }
            float reach = SCATTER_SIZES * scene.sizeOf(unit);
            destinations.put(unit.id(), new Coord3D(unit.x() + dx / length * reach, unit.y() + dy / length * reach,
                    0f));
        }
        scene.move(destinations);
    }

    /** What is selected that is his: what an order may go to. */
    private static List<Integer> own(Scene scene) {
        var his = new ArrayList<Integer>();
        for (var unit : scene.units()) {
            if (scene.selection().contains(unit.id()) && unit.playerIndex() == scene.localPlayer()) {
                his.add(unit.id());
            }
        }
        return his;
    }

    private static UnitView find(Scene scene, int id) {
        for (var unit : scene.units()) {
            if (unit.id() == id) {
                return unit;
            }
        }
        return null;
    }
}
