package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;

import java.util.List;
import java.util.function.Supplier;

import uz.dukeengine.core.view.WorldSnapshot;
import uz.dukeengine.game.DukeGame;

/**
 * The game's own drawing over everything else in the window — its {@link Painter}'s canvas, the title card and the
 * credits its story puts up — and the movie it plays.
 */
final class GameCanvas {

    private final AssetManager assetManager;
    private final Camera cam;
    private final HudScale hud;
    /** The game's own drawing — see {@link Painter}; null for a game that draws nothing of its own. */
    private final Painter painter;
    private final List<HudPart> parts;
    /** Where the world's points, things' bars and the bottom panel are on the screen, for the game's drawing. */
    private final CanvasFrame.World world;
    private final Supplier<DukeGame> game;
    private final Supplier<WorldSnapshot> snapshot;
    /** Every noise the game makes, for a movie's sound. */
    private final Supplier<GameSounds> noises;
    private final MissingAssets missing;
    private final CanvasText canvasText;
    private final CanvasDrawing canvasDrawing;
    private final MovieScreen movieScreen;
    /** The movie playing — see {@link Duke3D#playMovie}. */
    private MoviePlayer movie;
    /** The size the painter was last told the screen is. */
    private int paintedFor;
    /** The credits rolling, as the client draws them — see {@link DukeGame#creditsRoll}; null for none. */
    private CreditsRollShow credits;

    /** The title card up, as the client draws it — see {@link DukeGame#titleCard}; null for none. */
    private TitleCardShow titleCard;

    GameCanvas(AssetManager assetManager, Node guiNode, Camera cam, HudScale hud, Painter painter, List<HudPart> parts,
            CanvasFrame.World world, Supplier<DukeGame> game, Supplier<WorldSnapshot> snapshot,
            Supplier<GameSounds> noises, MissingAssets missing) {
        this.assetManager = assetManager;
        this.cam = cam;
        this.hud = hud;
        this.painter = painter;
        this.parts = parts;
        this.world = world;
        this.game = game;
        this.snapshot = snapshot;
        this.noises = noises;
        this.missing = missing;
        this.canvasText = new CanvasText(this::openAsset);
        this.canvasDrawing = new CanvasDrawing(assetManager, canvasText, this::portraitOf);
        guiNode.attachChild(hud.draw(canvasDrawing.node()));
        this.movieScreen = new MovieScreen(assetManager, guiNode);
    }

    /** What the canvas is drawn in, put away with the rest of the interface where the game says. */
    Node node() {
        return canvasDrawing.node();
    }

    /** Whether a title card or the credits take the input, before the game's canvas or any control. */
    boolean takesInput() {
        return titleCard != null && titleCard.takesInput() || credits != null;
    }

    /** Esc or a click on the credits: the rest of the roll is skipped. */
    void skip() {
        if (credits != null) {
            credits.skip();
        }
    }

    /** This frame's movie picture, and the game's drawing over everything. */
    void frame(float tpf) {
        runTheMovie(tpf);
        // Last, over everything, whatever the screen: the game's front end, its load screen, its HUD in play.
        paintTheCanvas(tpf);
    }

    /** A file the game named, by its whole path — from the classpath or a folder the game registered — or null. */
    private java.io.InputStream openAsset(String path) {
        try {
            var found = assetManager.locateAsset(new com.jme3.asset.AssetKey<>(path));
            return found == null ? null : found.openStream();
        } catch (RuntimeException _) {
            return null;
        }
    }

    /** Play a movie — see {@link Duke3D#playMovie}; one playing is stopped first. */
    void playMovie(Movie wanted, Runnable ended) {
        stopMovie();
        var frames = new MovieFrames(() -> openAsset(wanted.frames()), MovieFrames.AHEAD);
        var sound = wanted.sound() == null ? SoundSink.Playing.NONE : noises.get().sounds().movieSound(wanted.sound());
        movie = new MoviePlayer(wanted, frames, sound, ended == null ? () -> { } : ended);
    }

    /** Stop the movie playing, and its sound, now. */
    void stopMovie() {
        if (movie != null) {
            movie.stop();
            movie = null;
            movieScreen.hide();
        }
    }

    /** The movie's next picture, if one is due, over whatever is on the screen. */
    private void runTheMovie(float tpf) {
        if (movie == null) {
            return;
        }
        var picture = movie.update(tpf);
        if (picture != null) {
            movieScreen.show(picture);
        }
        if (movie.isOver()) {
            movie = null;
            movieScreen.hide();
            return;
        }
        movieScreen.place(movie.movie(), cam.getWidth(), cam.getHeight());
    }

    /** The live portrait of {@code unitId} a part keeps for the canvas, or null — see {@link Canvas#drawPortrait}. */
    private com.jme3.texture.Texture portraitOf(int unitId) {
        for (var part : parts) {
            var texture = part.portraitOf(unitId);
            if (texture != null) {
                return texture;
            }
        }
        return null;
    }

    /** The game's drawing for this frame, over whatever else is on the screen. */
    private void paintTheCanvas(float tpf) {
        var game = this.game.get();
        var wanted = game.titleCardShowing();
        if (wanted == null) {
            titleCard = null;
        } else if (titleCard == null || titleCard.card() != wanted) {
            titleCard = new TitleCardShow(wanted);
        }
        var rolling = game.creditsShowing();
        if (rolling == null) {
            credits = null;
        } else if (credits == null || credits.roll() != rolling) {
            credits = new CreditsRollShow(rolling);
        }
        if (painter == null && titleCard == null && credits == null) {
            return;
        }
        int width = hud.of(cam.getWidth());
        int height = hud.of(cam.getHeight());
        if (painter != null && paintedFor != width * 100_000 + height) {
            paintedFor = width * 100_000 + height;
            painter.resized(width, height);
        }
        var frame = new CanvasFrame(width, height, canvasText, canvasDrawing::sizeOf, world);
        try {
            if (painter != null) {
                painter.paint(frame);
            }
        } catch (RuntimeException e) {
            missing.warn(String.valueOf(e), "canvas painter failure");
        }
        // Over everything the game painted: the card is the story's, and says so above its own screens too.
        if (titleCard != null && !titleCard.paint(frame, tpf)) {
            game.titleCardGone(titleCard.card());
            titleCard = null;
        }
        if (credits != null && !credits.paint(frame, tpf)) {
            game.creditsGone(credits.roll());
            credits = null;
        }
        var snapshot = this.snapshot.get();
        if (snapshot != null) {
            var asked = frame.portraitsAsked();
            for (var part : parts) {
                part.portraits(asked, snapshot, tpf);
            }
        }
        canvasDrawing.show(frame.triangles(), height);
    }
}
