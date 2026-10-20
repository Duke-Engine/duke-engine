package uz.dukeengine.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Function;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.Beam;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.RidingEffect;
import uz.dukeengine.core.thing.Stream;

/**
 * What the simulation shows and never reads: the beams it owns, the effects riding its things and the streams its
 * things ride — out of the checksum and the save, since nothing decided reads them.
 */
final class Spectacle {

    private final Function<ObjectId, GameObject> things;
    /** The beams the simulation owns, by number. */
    private final TreeMap<Integer, Beam> beams = new TreeMap<>();
    private int nextBeam = 1;
    private final TreeMap<Integer, RidingEffect> ridingEffects = new TreeMap<>();
    private int nextEffect = 1;
    /** The streams things ride, by name: what rides each, {@code Stream.GAP} where it breaks. */
    private final TreeMap<String, List<ObjectId>> streams = new TreeMap<>();

    Spectacle(Function<ObjectId, GameObject> things) {
        this.things = things;
    }

    /** No beams and no effects, numbered from the first again; the streams ride on. */
    void clear() {
        beams.clear();
        ridingEffects.clear();
        nextEffect = 1;
        nextBeam = 1;
    }

    int beam(String look, Coord3D from, Coord3D to, float width) {
        int id = nextBeam++;
        beams.put(id, new Beam(id, look, from, to, width));
        return id;
    }

    void moveBeam(int beam, Coord3D from, Coord3D to, float width) {
        beams.computeIfPresent(beam, (id, was) -> new Beam(id, was.look(), from, to, width));
    }

    void endBeam(int beam) {
        beams.remove(beam);
    }

    List<Beam> beams() {
        return List.copyOf(beams.values());
    }

    int effect(String name, GameObject thing, String bone, Coord3D offset) {
        int id = nextEffect++;
        ridingEffects.put(id, new RidingEffect(id, name, thing.getId(), bone, offset));
        return id;
    }

    void endEffect(int effect) {
        ridingEffects.remove(effect);
    }

    List<RidingEffect> effects() {
        return List.copyOf(ridingEffects.values());
    }

    /** Things gone from the world end the effects riding them. */
    void left(List<GameObject> leaving) {
        if (ridingEffects.isEmpty()) {
            return;
        }
        var gone = new HashSet<ObjectId>();
        leaving.forEach(object -> gone.add(object.getId()));
        ridingEffects.values().removeIf(effect -> gone.contains(effect.thing()));
    }

    void rideStream(String stream, GameObject thing, int most) {
        var riders = streams.computeIfAbsent(stream, name -> new ArrayList<>());
        riders.removeIf(id -> id != Stream.GAP && ended(id)); // what ended, dropped
        riders.add(thing.getId());
        long riding = riders.stream().filter(id -> id != Stream.GAP).count();
        while (riding > Math.max(1, most)) {
            if (riders.removeFirst() != Stream.GAP) {
                riding--;
            }
        }
        while (riders.getFirst() == Stream.GAP) {
            riders.removeFirst();
        }
    }

    void breakStream(String stream) {
        var riders = streams.get(stream);
        if (riders != null && !riders.isEmpty() && riders.getLast() != Stream.GAP) {
            riders.add(Stream.GAP);
        }
    }

    private boolean ended(ObjectId id) {
        var thing = things.apply(id);
        return thing == null || thing.isDestroyed();
    }

    List<Stream> streams() {
        var all = new ArrayList<Stream>();
        streams.forEach((name, riders) -> all.add(new Stream(name, riders)));
        return all;
    }
}
