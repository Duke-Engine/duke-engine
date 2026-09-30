package uz.dukeengine.core.view;

/**
 * One drawable unit as the renderer sees it — an immutable copy of the fields
 * presentation needs, safe to read from the Swing thread while the simulation
 * runs on its own thread.
 *
 * @param z         its own height, the simulation's: a jet's cruising height, a shell at the top of its arc
 * @param pitch     its nose up, in radians
 * @param roll      banked to its right, in radians
 * @param ownHeight whether it is drawn at {@code z} even under the ground; otherwise at {@code z} or on the ground,
 *                  whichever is higher
 * @param passengers the ids of what rides inside it, in the order they got in; empty for anything that carries none
 * @param conditions the words it holds, sorted — a rank, an upgrade's weapon set, whatever the game set on it — which
 *                   a client chooses its model, its pieces and its barrels by
 * @param built      how far it is built: 0 to 1 for a site going up, falling below 0 for a building being sold, as
 *                   the reference's construction percent does; 1 for anything else
 * @param ridesOn    the id of what it rides on top of, or of the hold that shows it — drawn where it stands, clicked
 *                   as that — or -1
 * @param allied     whether it is the viewer's own or an ally's — everything, for a machine watching — for a look that
 *                   shows its own side what it shows no one else
 * @param span       the line it is drawn along — a bridge's two ends — or null for a thing drawn at its place
 * @param mobile     whether it can move: a click on open ground moves it, and never makes it a rally point
 * @param drawnAs    the template it is drawn as — its model and clips, chosen by its own words — or null for its own
 * @param wears      the player whose colours, house and radar, it is drawn in for this viewer: its own, or the one it
 *                   is disguised as to a viewer not on its side
 * @param opacity    how opaque it is drawn, 0 to 1, as the game drives it through a change of look
 * @param hostile    whether the viewer takes its owner for an enemy — neither its own, an ally's nor a neutral's — for
 *                   a look that hides from its enemies what it shows everyone else
 * @param speed      how fast it moved over the last frame, world units a second, as the simulation has it: what a
 *                   walk's clip is paced to
 * @param remembered whether it is drawn as its viewer last saw it, its ground fogged
 * @param turrets    how its turrets stand turned and pitched, as its game's {@code Turret} has them
 * @param lift       how far above where it would be drawn it is drawn, as the game sets it
 * @param yaw        how far it is drawn turned about its up beside its facing, in radians, as the game sets it
 * @param corners    how far its wheels stand raised at each corner, as the game sets them
 */
public record UnitView(
        int id,
        String templateName,
        int playerIndex,
        float x,
        float y,
        float orientation,
        float health,
        float maxHealth,
        boolean structure,
        boolean selectable,
        boolean moving,
        boolean attacking,
        int productionQueue,
        float z,
        float pitch,
        float roll,
        boolean ownHeight,
        int statuses,
        java.util.List<Integer> passengers,
        java.util.List<String> conditions,
        float built,
        int ridesOn,
        boolean allied,
        uz.dukeengine.core.thing.Span span,
        boolean mobile,
        String drawnAs,
        int wears,
        float opacity,
        boolean hostile,
        float speed,
        boolean remembered,
        Turrets turrets,
        float lift,
        float yaw,
        uz.dukeengine.core.thing.Corners corners) {
    public UnitView {
        passengers = passengers == null ? java.util.List.of() : java.util.List.copyOf(passengers);
        conditions = conditions == null ? java.util.List.of() : java.util.List.copyOf(conditions);
        turrets = turrets == null ? Turrets.NONE : turrets;
        corners = corners == null ? uz.dukeengine.core.thing.Corners.LEVEL : corners;
    }

    /** A view drawn where and as it stands, nothing lifted, turned or sprung: every view from before one could be. */
    public UnitView(int id, String templateName, int playerIndex, float x, float y, float orientation, float health,
            float maxHealth, boolean structure, boolean selectable, boolean moving, boolean attacking,
            int productionQueue, float z, float pitch, float roll, boolean ownHeight, int statuses,
            java.util.List<Integer> passengers, java.util.List<String> conditions, float built, int ridesOn,
            boolean allied, uz.dukeengine.core.thing.Span span, boolean mobile, String drawnAs, int wears,
            float opacity, boolean hostile, float speed, boolean remembered, Turrets turrets) {
        this(id, templateName, playerIndex, x, y, orientation, health, maxHealth, structure, selectable, moving,
                attacking, productionQueue, z, pitch, roll, ownHeight, statuses, passengers, conditions, built, ridesOn,
                allied, span, mobile, drawnAs, wears, opacity, hostile, speed, remembered, turrets, 0f, 0f,
                uz.dukeengine.core.thing.Corners.LEVEL);
    }

    /** A view whose turrets stand straight ahead: every view from before a turret was drawn turned. */
    public UnitView(int id, String templateName, int playerIndex, float x, float y, float orientation, float health,
            float maxHealth, boolean structure, boolean selectable, boolean moving, boolean attacking,
            int productionQueue, float z, float pitch, float roll, boolean ownHeight, int statuses,
            java.util.List<Integer> passengers, java.util.List<String> conditions, float built, int ridesOn,
            boolean allied, uz.dukeengine.core.thing.Span span, boolean mobile, String drawnAs, int wears,
            float opacity, boolean hostile, float speed, boolean remembered) {
        this(id, templateName, playerIndex, x, y, orientation, health, maxHealth, structure, selectable, moving,
                attacking, productionQueue, z, pitch, roll, ownHeight, statuses, passengers, conditions, built, ridesOn,
                allied, span, mobile, drawnAs, wears, opacity, hostile, speed, remembered, Turrets.NONE);
    }

    /** A view of a thing in sight: every view from before one could be remembered. */
    public UnitView(int id, String templateName, int playerIndex, float x, float y, float orientation, float health,
            float maxHealth, boolean structure, boolean selectable, boolean moving, boolean attacking,
            int productionQueue, float z, float pitch, float roll, boolean ownHeight, int statuses,
            java.util.List<Integer> passengers, java.util.List<String> conditions, float built, int ridesOn,
            boolean allied, uz.dukeengine.core.thing.Span span, boolean mobile, String drawnAs, int wears,
            float opacity, boolean hostile, float speed) {
        this(id, templateName, playerIndex, x, y, orientation, health, maxHealth, structure, selectable, moving,
                attacking, productionQueue, z, pitch, roll, ownHeight, statuses, passengers, conditions, built, ridesOn,
                allied, span, mobile, drawnAs, wears, opacity, hostile, speed, false);
    }

    /**
     * This view as its viewer remembers it, its ground fogged — the reference's ghost of a still thing ({@code
     * W3DGhostObject}): drawn as it was, not picked, and showing no bar.
     */
    public UnitView asRemembered() {
        return new UnitView(id, templateName, playerIndex, x, y, orientation, health, maxHealth, structure, false,
                false, false, productionQueue, z, pitch, roll, ownHeight, statuses, passengers, conditions, built,
                ridesOn, allied, span, mobile, drawnAs, wears, opacity, hostile, 0f, true, turrets, lift, yaw, corners);
    }

    /** A view that says nothing of its speed: every view from before a walk was paced to it. */
    public UnitView(int id, String templateName, int playerIndex, float x, float y, float orientation, float health,
            float maxHealth, boolean structure, boolean selectable, boolean moving, boolean attacking,
            int productionQueue, float z, float pitch, float roll, boolean ownHeight, int statuses,
            java.util.List<Integer> passengers, java.util.List<String> conditions, float built, int ridesOn,
            boolean allied, uz.dukeengine.core.thing.Span span, boolean mobile, String drawnAs, int wears,
            float opacity, boolean hostile) {
        this(id, templateName, playerIndex, x, y, orientation, health, maxHealth, structure, selectable, moving,
                attacking, productionQueue, z, pitch, roll, ownHeight, statuses, passengers, conditions, built, ridesOn,
                allied, span, mobile, drawnAs, wears, opacity, hostile, 0f);
    }

    /** A view that takes whatever is not on the viewer's side for an enemy: every view from before one could ask. */
    public UnitView(int id, String templateName, int playerIndex, float x, float y, float orientation, float health,
            float maxHealth, boolean structure, boolean selectable, boolean moving, boolean attacking,
            int productionQueue, float z, float pitch, float roll, boolean ownHeight, int statuses,
            java.util.List<Integer> passengers, java.util.List<String> conditions, float built, int ridesOn,
            boolean allied, uz.dukeengine.core.thing.Span span, boolean mobile, String drawnAs, int wears,
            float opacity) {
        this(id, templateName, playerIndex, x, y, orientation, health, maxHealth, structure, selectable, moving,
                attacking, productionQueue, z, pitch, roll, ownHeight, statuses, passengers, conditions, built, ridesOn,
                allied, span, mobile, drawnAs, wears, opacity, !allied);
    }

    /** A view of a thing drawn as itself, in its own colours, whole: every view from before one could be disguised. */
    public UnitView(int id, String templateName, int playerIndex, float x, float y, float orientation, float health,
            float maxHealth, boolean structure, boolean selectable, boolean moving, boolean attacking,
            int productionQueue, float z, float pitch, float roll, boolean ownHeight, int statuses,
            java.util.List<Integer> passengers, java.util.List<String> conditions, float built, int ridesOn,
            boolean allied, uz.dukeengine.core.thing.Span span, boolean mobile) {
        this(id, templateName, playerIndex, x, y, orientation, health, maxHealth, structure, selectable, moving,
                attacking, productionQueue, z, pitch, roll, ownHeight, statuses, passengers, conditions, built, ridesOn,
                allied, span, mobile, null, playerIndex, 1f);
    }

    /** The template whose look it is drawn with: the one it is drawn as, or its own. */
    public String looksAs() {
        return drawnAs != null ? drawnAs : templateName;
    }

    /** A view that does not say whether it can move: every thing but a structure can, as views before it had it. */
    public UnitView(int id, String templateName, int playerIndex, float x, float y, float orientation, float health,
            float maxHealth, boolean structure, boolean selectable, boolean moving, boolean attacking,
            int productionQueue, float z, float pitch, float roll, boolean ownHeight, int statuses,
            java.util.List<Integer> passengers, java.util.List<String> conditions, float built, int ridesOn,
            boolean allied, uz.dukeengine.core.thing.Span span) {
        this(id, templateName, playerIndex, x, y, orientation, health, maxHealth, structure, selectable, moving,
                attacking, productionQueue, z, pitch, roll, ownHeight, statuses, passengers, conditions, built, ridesOn,
                allied, span, !structure);
    }

    /** A view of a thing drawn at its place: every view from before one could be drawn along a line. */
    public UnitView(int id, String templateName, int playerIndex, float x, float y, float orientation, float health,
            float maxHealth, boolean structure, boolean selectable, boolean moving, boolean attacking,
            int productionQueue, float z, float pitch, float roll, boolean ownHeight, int statuses,
            java.util.List<Integer> passengers, java.util.List<String> conditions, float built, int ridesOn,
            boolean allied) {
        this(id, templateName, playerIndex, x, y, orientation, health, maxHealth, structure, selectable, moving,
                attacking, productionQueue, z, pitch, roll, ownHeight, statuses, passengers, conditions, built, ridesOn,
                allied, null);
    }

    /** A view that says nothing of whose side it is on: every view from before a look could ask. */
    public UnitView(int id, String templateName, int playerIndex, float x, float y, float orientation, float health,
            float maxHealth, boolean structure, boolean selectable, boolean moving, boolean attacking,
            int productionQueue, float z, float pitch, float roll, boolean ownHeight, int statuses,
            java.util.List<Integer> passengers, java.util.List<String> conditions, float built, int ridesOn) {
        this(id, templateName, playerIndex, x, y, orientation, health, maxHealth, structure, selectable, moving,
                attacking, productionQueue, z, pitch, roll, ownHeight, statuses, passengers, conditions, built, ridesOn,
                false);
    }

    /** A view of a thing that rides on nothing: every view from before a thing could ride on top of another. */
    public UnitView(int id, String templateName, int playerIndex, float x, float y, float orientation, float health,
            float maxHealth, boolean structure, boolean selectable, boolean moving, boolean attacking,
            int productionQueue, float z, float pitch, float roll, boolean ownHeight, int statuses,
            java.util.List<Integer> passengers, java.util.List<String> conditions, float built) {
        this(id, templateName, playerIndex, x, y, orientation, health, maxHealth, structure, selectable, moving,
                attacking, productionQueue, z, pitch, roll, ownHeight, statuses, passengers, conditions, built, -1);
    }

    /** A view that says nothing of being built: every view from before a site was drawn rising. */
    public UnitView(int id, String templateName, int playerIndex, float x, float y, float orientation, float health,
            float maxHealth, boolean structure, boolean selectable, boolean moving, boolean attacking,
            int productionQueue, float z, float pitch, float roll, boolean ownHeight, int statuses,
            java.util.List<Integer> passengers, java.util.List<String> conditions) {
        this(id, templateName, playerIndex, x, y, orientation, health, maxHealth, structure, selectable, moving,
                attacking, productionQueue, z, pitch, roll, ownHeight, statuses, passengers, conditions, 1f);
    }

    /** A view that says nothing of the words it holds: every view from before they were carried. */
    public UnitView(int id, String templateName, int playerIndex, float x, float y, float orientation, float health,
            float maxHealth, boolean structure, boolean selectable, boolean moving, boolean attacking,
            int productionQueue, float z, float pitch, float roll, boolean ownHeight, int statuses,
            java.util.List<Integer> passengers) {
        this(id, templateName, playerIndex, x, y, orientation, health, maxHealth, structure, selectable, moving,
                attacking, productionQueue, z, pitch, roll, ownHeight, statuses, passengers, java.util.List.of());
    }

    /** A view that says nothing of passengers: every view from before they were carried. */
    public UnitView(int id, String templateName, int playerIndex, float x, float y, float orientation, float health,
            float maxHealth, boolean structure, boolean selectable, boolean moving, boolean attacking,
            int productionQueue, float z, float pitch, float roll, boolean ownHeight, int statuses) {
        this(id, templateName, playerIndex, x, y, orientation, health, maxHealth, structure, selectable, moving,
                attacking, productionQueue, z, pitch, roll, ownHeight, statuses, java.util.List.of());
    }

    /** A unit on the ground, level: every view from before a thing could be drawn at its own height. */
    public UnitView(int id, String templateName, int playerIndex, float x, float y, float orientation, float health,
            float maxHealth, boolean structure, boolean selectable, boolean moving, boolean attacking,
            int productionQueue) {
        this(id, templateName, playerIndex, x, y, orientation, health, maxHealth, structure, selectable, moving,
                attacking, productionQueue, 0f, 0f, 0f, false, 0);
    }

    /** A view that says nothing of statuses: every view from before they were carried. */
    public UnitView(int id, String templateName, int playerIndex, float x, float y, float orientation, float health,
            float maxHealth, boolean structure, boolean selectable, boolean moving, boolean attacking,
            int productionQueue, float z, float pitch, float roll, boolean ownHeight) {
        this(id, templateName, playerIndex, x, y, orientation, health, maxHealth, structure, selectable, moving,
                attacking, productionQueue, z, pitch, roll, ownHeight, 0);
    }

    /** Whether the thing carries this status — held, sold, in the air, going up. */
    public boolean has(uz.dukeengine.core.thing.ObjectStatus status) {
        return (statuses & (1 << status.ordinal())) != 0;
    }

    /** Whether this unit is a production structure (has a build queue). */
    public boolean producer() {
        return productionQueue >= 0;
    }

    public boolean isDamaged() {
        return maxHealth > 0 && health < maxHealth;
    }

    public float healthFraction() {
        return maxHealth <= 0 ? 1f : health / maxHealth;
    }
}
