package uz.dukeengine.client3d;

import com.jme3.math.ColorRGBA;
import com.jme3.renderer.Renderer;
import com.jme3.renderer.ViewPort;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.view.WorldSnapshot;
import uz.dukeengine.game.DukeGame;

/**
 * What the player has seen of the world, and the dark drawn over the rest: opened round his own things, or as the
 * simulation's sight says, and coloured by the look under the camera or the game's haze.
 *
 * <p>Entirely a matter of what is drawn. It reads the snapshot the client is already given and writes nothing back, so
 * the simulation runs the same whether anyone is looking at it or not — which is the only way fog can be added to a
 * deterministic game without becoming part of it.
 */
final class FogOfWar {

    /** How fast the fog's colour turns to a new look's: most of the way in a second. */
    private static final float FOG_TINT_TURN = 4f;

    private final Visuals visuals;
    /** The dark, as a picture the materials read; null for a game that never asked to be discovered. */
    private final FogMap fogMap;
    private final GroundLooks groundLooks;
    private final Looks looks;
    private final CameraFocus camera;
    private final ViewPort viewPort;
    private final Renderer renderer;
    private final Supplier<DukeGame> game;
    private final Supplier<WorldSnapshot> snapshot;
    /**
     * What the player has seen, when the game asked to be discovered rather than
     * shown. {@code null} for every other game, and then nothing below runs.
     */
    private Discovery discovery;
    /**
     * The discovering template's {@code VisionRange}, resolved once the game is up.
     */
    private float discoveryRadius = -1f;
    /**
     * Whose sight the radius above was read from — see {@link #sync}.
     */
    private String discoveryEyes;
    /** The fog's window as last told to the materials that read it. */
    private int fogWindowTold;
    /** The fog's colour as drawn now, eased toward the colour of the look the camera looks at; null to start afresh. */
    private ColorRGBA fogTintNow;
    /** The colour the haze is eased to, or null for the fog's own; and how quickly. */
    private ColorRGBA hazeWanted;
    private float hazeSeconds;

    FogOfWar(Visuals visuals, FogMap fogMap, GroundLooks groundLooks, Looks looks, CameraFocus camera,
            ViewPort viewPort, Renderer renderer, Supplier<DukeGame> game, Supplier<WorldSnapshot> snapshot) {
        this.visuals = visuals;
        this.fogMap = fogMap;
        this.groundLooks = groundLooks;
        this.looks = looks;
        this.camera = camera;
        this.viewPort = viewPort;
        this.renderer = renderer;
        this.game = game;
        this.snapshot = snapshot;
    }

    /** What the player has seen, or null for a game that is never discovered. */
    Discovery discovery() {
        return discovery;
    }

    /** Whether the player sees a point now: everywhere, in a game that is never discovered. */
    boolean canSee(float x, float y) {
        return discovery == null || discovery.canSee(x, y);
    }

    /**
     * A new world's dark: a floor nobody has walked, kept round the camera on a world whose ground is built round it,
     * its colour starting from whatever the camera looks at.
     */
    void newWorld(PathGrid builtFrom, Object mapRecord) {
        fogTintNow = null;
        if (!visuals.drawsFog()) {
            return;
        }
        // A new floor is a floor nobody has walked: memory belongs to one world,
        // and carrying it over would open rooms in a dungeon nobody has entered.
        if (discovery == null) {
            discovery = new Discovery(builtFrom, visuals.getFog());
        } else {
            discovery.reset(builtFrom);
        }
        if (visuals.fogStartsExploredFor(mapRecord)) {
            discovery.startExplored(); // a world whose lie he knows: remembered, not seen
        }
        // On a world whose ground is built round the camera, its dark is kept round the camera too.
        discovery.window(fogWindowCells(mapRecord));
        discovery.follow(camera.targetX(), camera.targetZ());
        fogMap.resize(builtFrom, discovery);
        groundLooks.fogResized();
        fogWindowTold = fogMap.windowVersion();
        groundLooks.tellEveryFogReaderWhereTheFogHolds();
    }

    /**
     * How many cells a side the dark is kept for round the camera: as far as the ground is built from where the camera
     * looks — the chunks within {@link Visuals#streamGround}, and the ring kept a while past them — each way, and a cell
     * over, since a texel reads the cells round it; 0, the whole map, where the ground is built whole.
     */
    private int fogWindowCells(Object mapRecord) {
        int stream = visuals.streamGroundFor(mapRecord);
        if (stream <= 0) {
            return 0;
        }
        int reach = Math.ceilDiv(stream, TerrainScene.CHUNK_CELLS) + 2;
        return 2 * reach * TerrainScene.CHUNK_CELLS + 2;
    }

    /**
     * Open the map around the player's own units and redraw the dark over what that changes.
     *
     * @return whether the dark was drawn again, for what else is drawn from what he has seen
     */
    boolean sync(float tpf) {
        if (discovery == null) {
            return false;
        }
        var snapshot = this.snapshot.get();
        if (snapshot.revealed()) {
            discovery.openEverything(); // the map revealed to this player, or a watcher's seat
            drawWhatIsOpen(tpf);
            return true;
        }
        if (visuals.isFogBySight()) {
            discovery.fromSight(snapshot.sight()); // the simulation's cells of what this player has seen
            drawWhatIsOpen(tpf);
            return true;
        }
        // Re-read when the eyes change, not only the first time. A game may say
        // late — or differently — whose sight opens the map: the dungeon lets the
        // player choose who he is, and a knight sees a shorter way than an archer.
        // Cached once, the second hero would walk about inside the first one's
        // circle, which looks like the fog being wrong rather than stale.
        var game = this.game.get();
        var eyes = visuals.getDiscoveryTemplate();
        if (discoveryRadius < 0f || !eyes.equals(discoveryEyes)) {
            var template = game.getLogic() == null ? null : game.getLogic().findTemplate(eyes);
            if (template == null) {
                return false; // the game has not finished booting; the map stays black
            }
            discoveryRadius = uz.dukeengine.core.thing.Sighted.of(template);
            discoveryEyes = eyes;
        }
        discovery.blockSight(whatBlocksSight(game, snapshot));
        discovery.reveal(snapshot.units(), game.getLocalPlayerIndex(), discoveryRadius,
                visuals.getDiscoveryTemplate(), visuals.getAlsoSeen());
        drawWhatIsOpen(tpf);
        return true;
    }

    /**
     * The footprints of what stands in this frame's snapshot and hides what is behind it — a shut gate — read from its
     * template, so the fog's line of sight stops at them and a gate opened lets it through the next frame.
     */
    private static List<uz.dukeengine.core.thing.Footprint> whatBlocksSight(DukeGame game, WorldSnapshot snapshot) {
        var logic = game.getLogic();
        var standing = new ArrayList<uz.dukeengine.core.thing.Footprint>();
        for (var view : snapshot.units()) {
            var template = logic == null ? null : logic.getThingFactory().findTemplate(view.templateName());
            if (template instanceof uz.dukeengine.core.thing.Solid solid && solid.blocksSight()) {
                standing.add(new uz.dukeengine.core.thing.Footprint(solid.geometry(),
                        new Coord3D(view.x(), view.y(), 0f), view.orientation()));
            }
        }
        return standing;
    }

    /** What is open, decided: drawn. */
    private void drawWhatIsOpen(float tpf) {
        // How it is drawn eases toward what is open, so the
        // edge sweeps rather than switching. Seconds, not frames.
        discovery.soften(tpf);
        // On a wide world the dark is kept round where the camera looks, and the ground coming into it drawn.
        discovery.follow(camera.targetX(), camera.targetZ());
        // The picture is what the player actually sees the dark as; the terrain is
        // only told what is so far behind it that drawing it is waste.
        fogMap.update(discovery, renderer);
        if (fogMap.windowVersion() != fogWindowTold) {
            fogWindowTold = fogMap.windowVersion();
            groundLooks.tellEveryFogReaderWhereTheFogHolds();
        }
    }

    /**
     * The floor's look's dark taken up. The tint lives in the fog picture's own texels rather than in a material, so a
     * new colour is the next repaint — which the new floor is about to ask for anyway.
     */
    void tint(ColorRGBA tint) {
        if (fogMap == null) {
            return;
        }
        fogMap.tint(tint);
        viewPort.setBackgroundColor(tint);
    }

    void hazeColour(int rgb, float seconds) {
        hazeWanted = rgb < 0 ? null : Materials.rgb(rgb);
        hazeSeconds = Math.max(0f, seconds);
    }

    /**
     * On a map whose cells wear looks of their own, the dark turns to the colour of the look under the point the camera
     * looks at, over about a second — walking from a wood into a cave, the wood's dark gives way to the cave's. The
     * picture is sent again only as the colour moves by a step it can hold. Where the game names a haze, that instead.
     */
    void followTheLookUnderTheCamera(float tpf) {
        if (hazeWanted != null) {
            easeTheHaze(tpf); // the game's haze over the look's
            return;
        }
        if (!looks.byCell() || fogMap == null) {
            return;
        }
        var view = camera.view();
        var wanted = looks.fogTintOf(looks.lookUnder(view.x(), view.z()));
        var eased = fogTintNow == null ? wanted
                : fogTintNow.clone().interpolateLocal(wanted, 1f - (float) Math.exp(-tpf * FOG_TINT_TURN));
        if (fogTintNow == null || packedRgb(eased) != packedRgb(fogTintNow)) {
            fogMap.recolour(eased);
            viewPort.setBackgroundColor(eased);
        }
        fogTintNow = eased;
    }

    /** The haze eased to the game's colour, the fog map and the void behind the world with it. */
    private void easeTheHaze(float tpf) {
        var from = fogTintNow != null ? fogTintNow : viewPort.getBackgroundColor().clone();
        float share = hazeSeconds <= 0f ? 1f : 1f - (float) Math.exp(-tpf / hazeSeconds);
        var eased = from.clone().interpolateLocal(hazeWanted, share);
        if (fogMap != null && packedRgb(eased) != packedRgb(from)) {
            fogMap.recolour(eased);
        }
        viewPort.setBackgroundColor(eased);
        fogTintNow = eased;
    }

    private static int packedRgb(ColorRGBA colour) {
        return Math.round(Math.clamp(colour.r, 0f, 1f) * 255f) << 16
                | Math.round(Math.clamp(colour.g, 0f, 1f) * 255f) << 8
                | Math.round(Math.clamp(colour.b, 0f, 1f) * 255f);
    }
}
