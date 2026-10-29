package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.renderer.RenderManager;
import com.jme3.scene.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * One pass over every file the game named, in two halves.
 *
 * <p>The reading half runs on threads of its own, because that is the slow
 * part and doing it here would freeze the very screen that exists to show it
 * happening. jME's asset manager is built for this: a loader off the render
 * thread parses into the shared cache, and nothing touches the scene graph.
 *
 * <p>The second half cannot be moved and does not need to be. Handing a mesh
 * or a texture to the card, and compiling the shader that will draw it, is
 * work only the render thread may do — so it is done here a piece at a time,
 * between frames, while the bar keeps moving. It is the half that matters:
 * a file read but never handed over stalls on first sight exactly as before.
 *
 * <p>Both halves keep what they load, and that is not tidiness. jME's asset
 * cache holds what it has parsed through a <em>weak</em> reference: an asset
 * nobody is holding is collected, and the next request for it reads the file
 * again. Load twenty megabytes, drop it on the floor, and the collector is
 * free to undo the entire exercise before the first monster walks in — with
 * no error, no warning, and a stall in exactly the frame this exists to spare.
 * So one instance of each is kept for the life of the window.
 */
final class ArtLoad implements MatchLoad.Art {

    /** What the art is read into and shown to the card through, and what holds it once it is read. */
    static final class Store {

        private final AssetManager assetManager;
        private final RenderManager renderManager;
        private final Bodies bodies;
        private final Node rootNode;
        private final Node terrainNode;
        private final Consumer<String> sound;
        private final MissingAssets missing;

        /**
         * Where one of each creature stands, culled, for the life of the window.
         *
         * <p>In the scene rather than off to the side of it, because a spatial with no
         * parent has no world transform and jME says so with an assertion. Never
         * drawn — the node is culled outright — and never emptied, because these
         * are the references that hold the parsed models in the asset cache.
         */
        private final Node warmNode = new Node("warm");

        /**
         * Textures and pieces with nothing else holding them. See {@link ArtLoad}.
         */
        private final List<Object> artKeptAlive =
                java.util.Collections.synchronizedList(new ArrayList<>());

        private void keep(Object asset) {
            artKeptAlive.add(asset);
        }

        /**
         * What a load has read, and what it has shown the card, in this window: the next load skips them. The
         * second match's load read and warmed all of Generals' art again, and kept a second copy of every model.
         */
        private final java.util.Set<String> readAlready = java.util.concurrent.ConcurrentHashMap.newKeySet();
        private final java.util.Set<String> warmedAlready = java.util.concurrent.ConcurrentHashMap.newKeySet();

        /**
         * @param sound a sound made ready to play, so the first time it is heard is not the first time it is read
         */
        Store(AssetManager assetManager, RenderManager renderManager, Bodies bodies, Node rootNode,
                Node terrainNode, Consumer<String> sound, MissingAssets missing) {
            this.assetManager = assetManager;
            this.renderManager = renderManager;
            this.bodies = bodies;
            this.rootNode = rootNode;
            this.terrainNode = terrainNode;
            this.sound = sound;
            this.missing = missing;
            warmNode.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
            rootNode.attachChild(warmNode);
        }
    }


    /**
     * How long one frame may spend handing things to the card.
     *
     * <p>A share of a frame rather than a count of pieces. It was one piece a frame, which made the
     * wait a count of frames: 876 models were 876 frames, a quarter of a minute at sixty, however
     * fast the card was. Then half of a sixty-a-second frame, 8 ms, which left the frame idle half
     * its time: Generals' 8389 pieces took 21.6 s. Three quarters of a thirty-a-second frame took
     * 13.0 s, and a load screen's bar moves well enough at thirty.
     */
    private static final long WARM_BUDGET_NANOS = 25_000_000L;

    /** One model to show the card, and the look that says how it is dressed. */
    private record Warm(Visuals.UnitVisual look, String path) {
    }

    private final Visuals visuals;
    private final Store store;
    /** The templates this match can draw, or null to read everything the game registered. */
    private final java.util.Set<String> only;
    private final List<Preload.Job> files;
    /**
     * One entry per model file, not one per creature: six kinds cut from one kit
     * share a file, and showing the card the same mesh six times teaches it
     * nothing it did not know after the first. A look's models for its
     * conditions are here too — a building first drawn wrecked is a stall.
     */
    private final List<Warm> looks;
    /**
     * Every sound, from both places a game may name one.
     *
     * <p>A unit's fire and death sounds are named on the unit; everything else
     * is in the bank. The music is left out on purpose — it is streamed from
     * disc as it plays rather than held, so reading it here would be reading a
     * megabyte to throw it away.
     */
    private final List<String> sounds;
    private final java.util.concurrent.atomic.AtomicInteger read =
            new java.util.concurrent.atomic.AtomicInteger();
    private volatile String reading = "";
    private int warmed;

    ArtLoad(java.util.Set<String> only, Visuals visuals, Store store) {
        this.visuals = visuals;
        this.store = store;
        this.only = only;
        this.files = Preload.plan(visuals, only);
        this.looks = warmList();
        this.sounds = soundList();
        var reader = new Thread(this::readEverything, "duke-art");
        reader.setDaemon(true); // a window closed mid-load must still close
        reader.start();
    }

    private List<Warm> warmList() {
        var byPath = new java.util.LinkedHashMap<String, Warm>();
        for (var look : only == null ? visuals.allLooks() : visuals.looksNamed(only)) {
            if (look.modelPath != null) {
                byPath.putIfAbsent(look.modelPath, new Warm(look, look.modelPath));
            }
            for (var path : look.conditionalModels.values()) {
                byPath.putIfAbsent(path, new Warm(look, path));
            }
        }
        byPath.keySet().removeAll(store.warmedAlready);
        return List.copyOf(byPath.values());
    }

    private List<String> soundList() {
        var everyTemplate = visuals.lookNames();
        return java.util.stream.Stream.concat(
                        files.stream().filter(job -> job.kind() == Preload.Kind.SOUND)
                                .map(Preload.Job::assetPath),
                        visuals.getSounds().all().stream()
                                .filter(cue -> cue.channel() != SoundBank.Channel.MUSIC)
                                .filter(cue -> belongsToThisMatch(cue.name(), everyTemplate))
                                .flatMap(cue -> cue.files().stream()))
                .distinct().toList();
    }

    /**
     * Whether a cue can be heard in this match.
     *
     * <p>A cue made for one template ends with its name — {@code died.Rifleman}, which the client plays
     * when a rifleman dies — and is this match's only if that template is. Everything else, a click, a
     * voice line, {@code died} on its own, is anybody's and stays. Only what is provably another
     * template's is left out, so a game that names its cues some other way loses nothing.
     */
    private boolean belongsToThisMatch(String cue, java.util.Set<String> everyTemplate) {
        if (only == null) {
            return true;
        }
        int dot = cue.lastIndexOf('.');
        var whose = dot < 0 ? null : cue.substring(dot + 1);
        return whose == null || !everyTemplate.contains(whose) || only.contains(whose);
    }

    /**
     * The reading half, on every core but the one that draws the bar.
     *
     * <p>jME's asset manager reads in parallel by design: each thread has its own
     * loader instances and the cache is concurrent. One reader read Generals' 5989
     * files in 31 s; sixteen cores do it in 5. Nothing here touches the world, so
     * the order the files arrive in changes nothing a match plays.
     */
    private void readEverything() {
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        try (var readers = java.util.concurrent.Executors.newFixedThreadPool(cores,
                Thread.ofPlatform().name("duke-art-", 0).daemon().factory())) { // a window closed mid-load must still close
            for (var job : files) {
                readers.execute(() -> readOne(job));
            }
            // A sound's samples read here too: the audio node the render thread makes for it
            // later finds them in the cache, under the very key it asks with.
            for (var sound : sounds) {
                readers.execute(() -> readSound(sound));
            }
        }
        reading = "";
    }

    private void readOne(Preload.Job job) {
        reading = job.assetPath();
        try {
            if (job.kind() == Preload.Kind.SOUND || store.readAlready.contains(job.assetPath())) {
                // A sound's node is the render thread's: it goes into the scene, and the
                // scene is not this thread's to touch. Its samples are read with the others.
                return;
            }
            switch (job.kind()) {
                case MODEL, TILE -> store.keep(store.assetManager.loadModel(job.assetPath()));
                case ANIMATIONS -> store.bodies.readLibrary(job.assetPath());
                case TEXTURE -> store.keep(store.assetManager.loadTexture(job.assetPath()));
                case SOUND -> {
                }
            }
            store.readAlready.add(job.assetPath());
        } catch (RuntimeException | LinkageError _) {
            store.missing.warn(job.assetPath(), job.kind().name().toLowerCase(
                    java.util.Locale.ROOT));
        } finally {
            // In a finally, because a reader that dies holding the count
            // leaves the loading screen up for ever, and a game that will
            // not start is worse than a game missing one texture.
            read.incrementAndGet();
        }
    }

    private void readSound(String path) {
        reading = path;
        try {
            if (store.readAlready.add(path)) {
                // AudioNode(assetManager, path, Buffer)'s own key: not streamed, cached.
                store.keep(store.assetManager.loadAsset(new com.jme3.audio.AudioKey(path, false, true)));
            }
        } catch (RuntimeException | LinkageError _) {
            // Said once, by the render thread, as it makes the node.
        } finally {
            read.incrementAndGet();
        }
    }

    /**
     * How far along, counting both halves as one.
     */
    @Override
    public float done() {
        return (read.get() + warmed) / (float) Math.max(1, total());
    }

    String what() {
        return reading;
    }

    private int total() {
        return reads() + looks.size() + sounds.size() + 1;
    }

    /** The reading half's count: every file, and every sound's samples. */
    private int reads() {
        return files.size() + sounds.size();
    }

    /**
     * Do this frame's share of the work; {@code true} once there is none left.
     */
    @Override
    public boolean step() {
        if (read.get() < reads()) {
            return false; // still reading; the bar is the only thing to do
        }
        int all = looks.size() + sounds.size() + 1;
        // At least one a frame, however slow the one; then as many more as fit in the budget.
        long until = System.nanoTime() + WARM_BUDGET_NANOS;
        do {
            if (warmed >= all) {
                break;
            }
            warmOne();
            warmed++;
        } while (System.nanoTime() < until);
        return warmed >= all;
    }

    private void warmOne() {
        if (warmed < looks.size()) {
            warmLook(looks.get(warmed));
        } else if (warmed < looks.size() + sounds.size()) {
            store.sound.accept(sounds.get(warmed - looks.size()));
        } else {
            // The floor is already standing, and its several hundred tiles are
            // three meshes and one material the card has still never seen.
            store.renderManager.preloadScene(store.terrainNode);
        }
    }

    /**
     * Build one of this creature exactly as the game will, show it to the card,
     * and throw it away.
     *
     * <p>Thrown away and still worth doing, because what is kept is not the
     * model: it is the parsed file in the asset cache, the texture in graphics
     * memory, and the compiled shader for the one material every creature of
     * this kit shares. The next one built is the cheap one.
     */
    private void warmLook(Warm one) {
        var look = one.look();
        reading = one.path();
        try {
            var body = store.assetManager.loadModel(one.path());
            if (look.modelPart != null) {
                body = store.bodies.partOf(body, look.modelPart, one.path());
            }
            store.bodies.dressModel(body, look, one.path());
            // Left in the scene, culled, for as long as the window lasts. A
            // clone is what holds a parsed model in jME's cache; drop it and
            // the file is read again the first time a creature needs it.
            store.warmNode.attachChild(body);
            store.rootNode.updateGeometricState();
            store.renderManager.preloadScene(body);
            store.warmedAlready.add(one.path());
        } catch (RuntimeException _) {
            store.missing.warn(one.path(), "model");
        }
    }
}
