package uz.dukeengine.client3d;

/**
 * A movie playing: which picture is due, by the sound's own clock while it plays and by the window's frames while it
 * does not, and when it is over. The picture for time {@code t} is number {@code floor(t × rate)}; the movie is over
 * when that number passes its last picture — or, holding its last frame, when it is stopped.
 *
 * <p>The game is told once, on the window's thread, when the last picture's time is up — whether the movie then goes
 * or stays on it. Stopping it ends it and its sound at once, and tells nothing: the game that stopped it knows.
 */
final class MoviePlayer {

    private final Movie movie;
    private final MovieFrames frames;
    private final SoundSink.Playing sound;
    private final Runnable ended;
    private float clock;
    private boolean toldEnd;
    private boolean over;
    private MovieFrames.Frame shown;

    MoviePlayer(Movie movie, MovieFrames frames, SoundSink.Playing sound, Runnable ended) {
        this.movie = movie;
        this.frames = frames;
        this.sound = sound;
        this.ended = ended;
    }

    /** Time passing; the picture to put up now, or null when the one up stays. */
    MovieFrames.Frame update(float seconds) {
        if (over) {
            return null;
        }
        float heard = sound.seconds();
        // The sound keeps the time while it plays; the pictures never run back to meet it.
        clock = Float.isNaN(heard) ? clock + seconds : Math.max(clock, heard);
        int due = (int) Math.floor(clock * movie.framesPerSecond());
        var next = frames.take(due);
        if (next != null) {
            shown = next;
        }
        if (!toldEnd && frames.allRead() && due >= frames.count()) {
            toldEnd = true;
            if (!movie.holdLastFrame()) {
                finish();
            }
            ended.run();
        }
        return next;
    }

    /** End it and its sound, now. */
    void stop() {
        if (!over) {
            finish();
        }
    }

    private void finish() {
        over = true;
        sound.stop();
        frames.close();
    }

    /** Whether it is over: gone from the screen, its sound stopped. */
    boolean isOver() {
        return over;
    }

    /** The picture up now, or null before the first. */
    MovieFrames.Frame shown() {
        return shown;
    }

    Movie movie() {
        return movie;
    }
}
