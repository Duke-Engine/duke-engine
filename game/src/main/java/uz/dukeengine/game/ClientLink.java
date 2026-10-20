package uz.dukeengine.game;

import java.util.function.Predicate;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.view.MomentWords;
import uz.dukeengine.core.view.ViewParts;
import uz.dukeengine.core.view.WorldSnapshot;

/**
 * The client that copies the world into snapshots, and how the game has told it to look: what it remembers, which
 * words it adds, what it says on the banner and the status line. A setting made before the client exists is applied
 * when it is made; one made after reaches it at once.
 */
final class ClientLink {

    private MatchClient client;
    private volatile MomentWords momentWords;
    private volatile Predicate<GameObject> remembers;
    private volatile int keepFrames;
    private volatile int keepDeadFrames;
    private boolean hearsThroughFog;

    /** The client, or null before the match is set up. */
    MatchClient client() {
        return client;
    }

    MatchClient create(GameLogic logic, ViewParts parts) {
        client = new MatchClient(logic, parts);
        client.setMomentWords(momentWords);
        client.remember(remembers);
        client.keepOutOfSight(keepFrames, keepDeadFrames);
        client.hearThroughFog(hearsThroughFog);
        return client;
    }

    void momentWords(MomentWords words) {
        this.momentWords = words;
        if (client != null) {
            client.setMomentWords(words);
        }
    }

    void remember(Predicate<GameObject> which) {
        this.remembers = which;
        if (client != null) {
            client.remember(which);
        }
    }

    void keepOutOfSight(int frames, int deadFrames) {
        this.keepFrames = frames;
        this.keepDeadFrames = deadFrames;
        if (client != null) {
            client.keepOutOfSight(frames, deadFrames);
        }
    }

    void hearThroughFog(boolean hears) {
        this.hearsThroughFog = hears;
        if (client != null) {
            client.hearThroughFog(hears);
        }
    }

    void viewer(int playerIndex) {
        if (client != null) {
            client.setViewerPlayer(playerIndex);
        }
    }

    void setBanner(String banner) {
        if (client != null) {
            client.setBanner(banner);
        }
    }

    void setStatus(String status) {
        if (client != null) {
            client.setStatus(status);
        }
    }

    WorldSnapshot snapshot() {
        return client == null ? WorldSnapshot.EMPTY : client.getSnapshot();
    }
}
