package uz.dukeengine.game;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import uz.dukeengine.core.math.Coord2D;
import uz.dukeengine.core.view.FallingWeather;
import uz.dukeengine.core.view.ViewRays;

/**
 * What the game puts on the window and the window tells the game back, none of it reaching the simulation: credits,
 * a title card, the camera, the alert the view jumps to, the weather and what is burnt or laid into the ground.
 */
final class Presentation {

    private final GameCamera camera = new GameCamera();
    private final AtomicReference<CreditsRoll> credits = new AtomicReference<>();
    private final AtomicReference<TitleCard> card = new AtomicReference<>();
    private final AtomicReference<Coord2D> viewMove = new AtomicReference<>();
    private volatile boolean alertsAreTheGames;
    private volatile Coord2D latestAlert;
    private volatile ViewRays viewRays;
    private volatile FallingWeather weather;
    private final List<DukeGame.GroundMark> groundMarks = new CopyOnWriteArrayList<>();
    private final List<DukeGame.StripPiece> stripPieces = new CopyOnWriteArrayList<>();

    GameCamera camera() {
        return camera;
    }

    void creditsRoll(CreditsRoll roll) {
        credits.set(roll);
    }

    CreditsRoll creditsShowing() {
        return credits.get();
    }

    void creditsGone(CreditsRoll rolled) {
        if (credits.compareAndSet(rolled, null)) {
            rolled.ended().run();
        }
    }

    void titleCard(TitleCard shown) {
        card.set(shown);
    }

    TitleCard titleCardShowing() {
        return card.get();
    }

    void titleCardGone(TitleCard shown) {
        card.compareAndSet(shown, null);
    }

    void moveView(Coord2D place) {
        viewMove.set(place);
    }

    Coord2D takeViewMove() {
        return viewMove.getAndSet(null);
    }

    void setLatestAlert(Coord2D place) {
        latestAlert = place;
        alertsAreTheGames = true;
    }

    boolean alertsAreTheGames() {
        return alertsAreTheGames;
    }

    Coord2D latestAlert() {
        return latestAlert;
    }

    ViewRays viewRays() {
        return viewRays;
    }

    void setViewRays(ViewRays rays) {
        viewRays = rays;
    }

    void weather(FallingWeather weather) {
        this.weather = weather;
    }

    FallingWeather weather() {
        return weather;
    }

    void markGround(DukeGame.GroundMark mark) {
        groundMarks.add(mark);
    }

    List<DukeGame.GroundMark> groundMarks() {
        return Collections.unmodifiableList(groundMarks);
    }

    void layStrips(List<DukeGame.StripPiece> pieces) {
        stripPieces.addAll(pieces);
    }

    List<DukeGame.StripPiece> stripPieces() {
        return Collections.unmodifiableList(stripPieces);
    }
}
