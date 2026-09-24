package uz.dukeengine.client3d;

import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where each thing's shots come out: its weapon slots' barrels — the bone a shot's effect plays at, the piece
 * its muzzle flash is drawn with, and the bone that kicks back — taken in turn, a barrel a shot. The reference
 * game's {@code W3DModelDraw} barrels ({@code validateWeaponBarrelInfo}, {@code handleWeaponFireFX}, {@code
 * handleClientRecoil}).
 *
 * <p><b>The flash</b> is hidden but on the frames its barrel fires: from the shot until its recoil has kicked
 * all the way back, or the one frame for a barrel with no recoil bone. <b>The recoil</b> slides its bone back
 * along its own x, faster at first — {@code initial}, times {@code damping} each frame, as far as {@code most} —
 * then settles back at {@code settle} a frame. Frames are the game's 30 a second, however fast the screen draws.
 */
final class Barrels {

    /** How a barrel kicks: the reference's {@code W3DModelDrawModuleData} defaults, 2, 3, 0.4 and 0.065. */
    record Recoil(float initial, float most, float damping, float settle) {

        static final Recoil REFERENCE = new Recoil(2f, 3f, 0.4f, 0.065f);
    }

    /** The reference's {@code TINY_RECOIL}: a kick slower than this has stopped. */
    private static final float TINY = 0.01f;

    /** One barrel of a slot: its fire bone, flash piece and recoil bone, any of which may be missing. */
    private static final class Barrel {
        final Spatial fire;
        final Spatial flash;
        final Spatial recoil;
        final Transform rest;
        boolean kicking;
        float shift;
        float rate;
        int flashFrames;

        Barrel(Spatial fire, Spatial flash, Spatial recoil) {
            this.fire = fire;
            this.flash = flash;
            this.recoil = recoil;
            this.rest = recoil == null ? null : recoil.getLocalTransform().clone();
        }
    }

    /** One slot of one model: its barrels, and whose turn it is. */
    private static final class Slot {
        final List<Barrel> barrels;
        int next;

        Slot(List<Barrel> barrels) {
            this.barrels = barrels;
        }
    }

    /** One thing's slots as they were found on its model, and how its barrels kick. */
    private record Dressed(Map<Integer, Slot> slots, Recoil recoil) {
    }

    private final Map<Integer, Dressed> things = new HashMap<>();
    private float owed;

    /**
     * A thing's model is new — first drawn, or swapped for another: find its barrels and hide their flashes, which
     * a model is made with showing.
     */
    void dress(int thing, Spatial model, Visuals.UnitVisual visual) {
        dress(thing, model, visual == null ? Map.of() : visual.weaponBones,
                visual == null ? Recoil.REFERENCE : visual.recoil);
    }

    /**
     * The same, with the slots' bones its words chose — an upgraded Humvee's turret fires from {@code MuzzleUp} —
     * found on the model again whenever the choice changes.
     */
    void dress(int thing, Spatial model, Map<Integer, Visuals.WeaponBones> bones, Recoil recoil) {
        if (model == null || bones.isEmpty()) {
            things.remove(thing);
            return;
        }
        var slots = new HashMap<Integer, Slot>();
        for (var entry : bones.entrySet()) {
            var barrels = barrels(model, entry.getValue());
            for (var barrel : barrels) {
                if (barrel.flash != null) {
                    barrel.flash.setCullHint(Spatial.CullHint.Always);
                }
            }
            slots.put(entry.getKey(), new Slot(barrels));
        }
        things.put(thing, new Dressed(slots, recoil));
    }

    /** The pieces a thing's barrels draw their muzzle flashes with: theirs to show and hide, nobody else's. */
    java.util.Set<Spatial> flashes(int thing) {
        var dressed = things.get(thing);
        if (dressed == null) {
            return java.util.Set.of();
        }
        var flashes = new java.util.HashSet<Spatial>();
        for (var slot : dressed.slots().values()) {
            for (var barrel : slot.barrels) {
                if (barrel.flash != null) {
                    flashes.add(barrel.flash);
                }
            }
        }
        return flashes;
    }

    /**
     * {@code validateWeaponBarrelInfo}: {@code NAME01}, {@code NAME02} … for each of the three, a barrel as long as
     * any of them is found; where none is numbered, the one unnumbered barrel. A barrel with a flash of its own
     * and no fire bone of its own fires from the one before it.
     */
    private static List<Barrel> barrels(Spatial model, Visuals.WeaponBones names) {
        var found = new ArrayList<Barrel>();
        Spatial previousFire = null;
        for (int number = 1; number <= Bones.MOST_NUMBERED; number++) {
            var fire = bone(model, names.fire(), number);
            var flash = bone(model, names.flash(), number);
            var recoil = bone(model, names.recoil(), number);
            if (fire == null && flash == null && recoil == null) {
                break;
            }
            if (fire == null && flash != null) {
                fire = previousFire;
            }
            found.add(new Barrel(fire, flash, recoil));
            previousFire = fire;
        }
        if (found.isEmpty()) {
            var fire = Bones.named(model, names.fire());
            var flash = Bones.named(model, names.flash());
            var recoil = Bones.named(model, names.recoil());
            if (fire != null || flash != null || recoil != null) {
                found.add(new Barrel(fire, flash, recoil));
            }
        }
        return found;
    }

    private static Spatial bone(Spatial model, String name, int number) {
        return name == null ? null : Bones.named(model, Bones.numbered(name, number));
    }

    /**
     * A shot from {@code slot}: the barrel whose turn it is flashes and kicks, and the next shot is the next
     * barrel's. The bone the shot's effect plays at, or null where the slot has none — then the caller plays it
     * where the reference does, at the thing.
     */
    Spatial fire(int thing, int slot) {
        var dressed = things.get(thing);
        var barrels = dressed == null ? null : dressed.slots().get(slot);
        if (barrels == null || barrels.barrels.isEmpty()) {
            return null;
        }
        var barrel = barrels.barrels.get(barrels.next % barrels.barrels.size());
        barrels.next = (barrels.next + 1) % barrels.barrels.size();
        if (barrel.flash != null) {
            barrel.flash.setCullHint(Spatial.CullHint.Inherit);
            barrel.flashFrames = 1;
        }
        if (barrel.recoil != null) {
            barrel.kicking = true;
            barrel.rate = dressed.recoil().initial();
        }
        return barrel.fire;
    }

    /** A thing gone: its barrels are forgotten. */
    void forget(int thing) {
        things.remove(thing);
    }

    /** Every thing forgotten, for a new world. */
    void clear() {
        things.clear();
    }

    /** The client's time passing, a game frame at a time. */
    void update(float seconds) {
        owed += Math.max(0f, seconds);
        int run = 0;
        while (owed >= Particles.FRAME_SECONDS && run < 8) {
            frame();
            owed -= Particles.FRAME_SECONDS;
            run++;
        }
        if (run == 8) {
            owed = 0f;
        }
    }

    /** One of the game's frames: {@code handleClientRecoil}, for every barrel of every thing. */
    void frame() {
        for (var dressed : things.values()) {
            var recoil = dressed.recoil();
            for (var slot : dressed.slots().values()) {
                for (var barrel : slot.barrels) {
                    step(barrel, recoil);
                }
            }
        }
    }

    private static void step(Barrel barrel, Recoil recoil) {
        if (barrel.recoil == null) {
            if (barrel.flash != null && barrel.flashFrames > 0 && --barrel.flashFrames == 0) {
                barrel.flash.setCullHint(Spatial.CullHint.Always); // the one frame it fired on is over
            }
            return;
        }
        if (!barrel.kicking && barrel.shift == 0f) {
            return; // at rest: left as the model, and whatever animates it, have it
        }
        if (barrel.kicking) {
            barrel.shift += barrel.rate;
            barrel.rate *= recoil.damping(); // it slows as it goes back
            if (barrel.shift >= recoil.most()) {
                barrel.shift = recoil.most();
                barrel.kicking = false;
            } else if (Math.abs(barrel.rate) < TINY) {
                barrel.kicking = false;
            }
        } else if (barrel.shift > 0f) {
            barrel.shift = Math.max(0f, barrel.shift - recoil.settle());
        }
        if (barrel.flash != null && !barrel.kicking) {
            barrel.flash.setCullHint(Spatial.CullHint.Always); // shown while it kicks, as the reference's
        }
        // Back along its own x, as the reference's Translate_X(-shift) on the bone.
        var rest = barrel.rest;
        var back = rest.getRotation().mult(new Vector3f(-barrel.shift * rest.getScale().x, 0f, 0f));
        barrel.recoil.setLocalTranslation(rest.getTranslation().add(back));
    }

    /** How far a thing's barrel is kicked back now, for a test. */
    float shift(int thing, int slot, int barrel) {
        var dressed = things.get(thing);
        return dressed == null ? 0f : dressed.slots().get(slot).barrels.get(barrel).shift;
    }
}
