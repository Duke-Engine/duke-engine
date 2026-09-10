package uz.duke.dungeon.skill;

import java.util.List;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.DamageType;
import uz.duke.core.module.MoveUpdate;
import uz.duke.core.module.UpdateModule;
import uz.duke.core.player.Relationship;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ObjectId;
import uz.duke.core.thing.World;
import uz.duke.dungeon.ai.Facing;
import uz.duke.dungeon.combat.Shot;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.power.PowerBook;
import uz.duke.rts.event.WeaponFired;
import uz.duke.rts.module.DamageModifier;
import uz.duke.rts.module.WeaponHold;
import uz.duke.rts.module.WeaponUpdate;

/**
 * The skills a hero has, what they are doing right now, and how long until he can
 * do them again.
 *
 * <p>Written here rather than built on the engine's {@code SpecialPowerModule},
 * which is a superweapon: one shape (area damage at a map point), values frozen
 * when the unit is built, no notion of a level unlocking anything, and four of
 * them on one unit are not tellable apart. Every one of those is something a
 * dungeon skill needs, so this follows {@code GrowableBody} and {@code HeroProgress}
 * — the game writes its own rule and the engine stays out of it.
 *
 * <p>What it does borrow is {@link DamageModifier}, and this module implements it
 * directly rather than attaching a buff module while {@code EMPOWER} lasts.
 * Attaching and detaching a module mid-frame is exactly what the engine's module
 * list refuses (rightly — it would silently skip whatever came next), and a flag
 * that expires is the same thing without the trap.
 *
 * <p>Determinism: cooldowns are frames counted down, never seconds; the victim of
 * a {@code STRIKE} is the nearest enemy with ties broken by object id; a
 * {@code DASH} walks with {@link StrictMath}. Nothing here asks the clock.
 */
public final class SkillBook extends UpdateModule implements DamageModifier, WeaponHold {

    /** How far apart a dash checks the ground it is crossing. */
    private static final float DASH_STEP = 5f;

    private final List<Skill> skills;
    private final int[] cooldowns;

    /**
     * Casts still in hand before a skill starts recharging.
     *
     * <p>Ordinarily one: cast it and it is spent. A power that grants a charge
     * lets the second press go through and only then starts the clock, which is
     * what "twice over" means to a player and what a second copy of the cooldown
     * would not have been.
     */
    private final int[] chargesLeft;

    /**
     * How many casts each slot holds when full.
     *
     * <p>Remembered rather than asked for every frame, and that is the whole
     * trick: a charge has to be handed over when a <em>power</em> raises the
     * ceiling, and not when the skill simply has one in hand. Without the
     * difference, a second charge refilled itself the frame after it was spent
     * and the skill never came off cooldown at all.
     */
    private final int[] chargeCap;

    /**
     * What the run has picked up along the way.
     *
     * <p>Held rather than copied, and held by reference on purpose: the book
     * outlives this module — a floor gives the hero a fresh body and a fresh
     * {@code SkillBook} — so the powers have to be somewhere that survives him.
     */
    private final PowerBook powers;

    /** Where an arrow comes out and how fast it travels — the archer's, not the skill's. */
    private final DungeonSettings settings;

    /** Frames of {@code EMPOWER} left, and what it is worth while it lasts. */
    private int boostFrames;
    private int boostPercent;

    /**
     * The frame of his last cast, and what it pointed his weapon at.
     *
     * <p>Both are for {@link uz.duke.dungeon.ai.HeroBrain}, which has to tell the
     * player's orders from everything else the hero does. A cast is the player's
     * most recent word and supersedes an attack order; the target a cast aims at
     * is the skill's own and must not be read as one.
     */
    private int lastCastFrame = Integer.MIN_VALUE;
    private ObjectId lastAimedAt;

    // ---- a shot begun and not yet loosed ----

    /**
     * The heavy shot in progress: which skill, at what level, and at whom.
     *
     * <p>A skill that takes time to cast needs somewhere to be while it is being
     * cast, and this is it. Held rather than queued because a hero draws one arrow
     * at a time: casting again is refused by the cooldown, which started the moment
     * he committed.
     */
    private Skill drawing;
    private int drawnAtLevel;
    private ObjectId drawnFor;
    private int loosesIn;

    public SkillBook(GameObject owner, List<Skill> skills, DungeonSettings settings,
            PowerBook powers) {
        super(owner);
        this.skills = List.copyOf(skills);
        this.cooldowns = new int[skills.size()];
        this.chargesLeft = new int[skills.size()];
        this.chargeCap = new int[skills.size()];
        this.settings = settings;
        this.powers = powers;
        for (int slot = 0; slot < skills.size(); slot++) {
            chargeCap[slot] = chargesOf(slot);
            chargesLeft[slot] = chargeCap[slot];
        }
    }

    /** How many times the skill in {@code slot} may be cast before it recharges. */
    private int chargesOf(int slot) {
        return 1 + powers.extraCharges(skills.get(slot).key());
    }

    private static final uz.duke.core.ini.FieldParseTable<Object> NO_FIELDS =
            new uz.duke.core.ini.FieldParseTable<>();

    /**
     * The creature block carries no skill data — only the fact that this creature
     * has skills, which ones being {@code dungeon.ini}'s business. The block still
     * has to be read to its {@code End}, or the rest of the creature is parsed as
     * though it were inside one.
     */
    public static uz.duke.core.module.ModuleData parseData(uz.duke.core.ini.Ini ini) {
        ini.initFromIni(new Object(), NO_FIELDS);
        return null;
    }

    public List<Skill> getSkills() {
        return skills;
    }

    /** Frames until the skill on {@code key} is ready; 0 when it is. */
    public int cooldownOf(char key) {
        int slot = slotOf(key);
        return slot < 0 ? 0 : cooldowns[slot];
    }

    public boolean isReady(char key) {
        int slot = slotOf(key);
        return slot >= 0 && cooldowns[slot] <= 0;
    }

    /** Casts left in hand on {@code key}, which is more than one only with a power. */
    public int chargesOf(char key) {
        int slot = slotOf(key);
        return slot < 0 ? 0 : chargesLeft[slot];
    }

    /** Frames of extra damage left, for anything that wants to draw it. */
    public int getBoostFrames() {
        return boostFrames;
    }

    /** The frame he last committed to a skill, or a long time ago if he never has. */
    public int getLastCastFrame() {
        return lastCastFrame;
    }

    /** What his last cast pointed his weapon at, or {@code null}. */
    public ObjectId getLastAimedAt() {
        return lastAimedAt;
    }

    private int slotOf(char key) {
        for (int slot = 0; slot < skills.size(); slot++) {
            if (skills.get(slot).key() == key) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Cast the skill on {@code key} at a hero of {@code level}. Returns false and
     * does nothing if there is no such skill, it is still recharging, or the level
     * has not unlocked it — an ultimate refuses rather than fires weakly.
     */
    public boolean cast(char key, int level) {
        return cast(key, level, null, null);
    }

    /**
     * Cast at what the player pointed at.
     *
     * <p>An aimed skill that cannot reach what it was aimed at <em>refuses</em>,
     * leaving its cooldown untouched, rather than going off at something else. A
     * player who clicked one skeleton and hit a different one has been given a
     * skill he cannot aim, and the cooldown he wasted is the one he needed.
     *
     * @param at        the creature a {@code UNIT} skill was aimed at, or null for
     *                  the old behaviour of taking whatever is nearest
     * @param towards   where a {@code GROUND} skill was aimed, or null to use the
     *                  caster's current facing
     */
    public boolean cast(char key, int level, ObjectId at, Coord3D towards) {
        int slot = slotOf(key);
        if (slot < 0 || cooldowns[slot] > 0 || chargesLeft[slot] <= 0) {
            return false;
        }
        var skill = skills.get(slot);
        if (!skill.unlockedAt(level)) {
            return false;
        }
        var owner = getOwner();
        var world = owner.getWorld();
        if (world == null || owner.isEffectivelyDead()) {
            return false;
        }
        if (!apply(skill, level, owner, world, at, towards)) {
            return false; // aimed at nothing it could reach; the cooldown is not spent
        }
        spend(slot, skill, level);
        lastCastFrame = world.getFrame();
        // He does one thing at a time. Casting is the player's latest word, so
        // whatever errand he was on ends here rather than resuming underneath it —
        // a dash that lands him somewhere and then walks him back is not a dash.
        var legs = owner.findModule(MoveUpdate.class);
        if (legs != null) {
            legs.stop();
        }
        return true;
    }

    /**
     * Take one cast out of the slot, and start the cooldown only when the last of
     * them has gone.
     *
     * <p>A skill with charges to spare is left ready — that is the whole point of
     * a charge — and the powers that shorten a cooldown are read here rather than
     * kept anywhere, so a card taken between two casts is felt on the second.
     */
    private void spend(int slot, Skill skill, int level) {
        if (--chargesLeft[slot] > 0) {
            return;
        }
        cooldowns[slot] = Math.max(Skill.MIN_COOLDOWN_FRAMES,
                Math.round(skill.cooldownAt(level) * powers.cooldownMultiplier(skill.key())));
    }

    /** @return whether it went off, which an aimed skill may decline */
    private boolean apply(Skill skill, int level, GameObject owner, World world,
            ObjectId at, Coord3D towards) {
        switch (skill.effect()) {
            case STRIKE -> {
                var victim = at == null
                        ? nearestEnemy(owner, world, skill.range())
                        : aimedAt(owner, world, at, skill.range());
                if (at != null && victim == null) {
                    return false;
                }
                if (victim == null) {
                    break; // nothing in reach; he has still spent the cast
                }
                if (skill.windUpFrames() > 0) {
                    beginDrawing(skill, level, owner, victim);
                } else {
                    land(skill, level, owner, victim);
                }
            }
            case AREA_DAMAGE -> {
                float each = damageOf(skill, level);
                for (var victim : enemiesWithin(owner, world, skill.radius())) {
                    victim.getBody().damage(each);
                    stealLife(owner, each);
                }
            }
            case DASH -> {
                if (towards != null) {
                    // Face where he was sent before he goes, so the model and the
                    // travel agree — and so the next thing he does looks that way.
                    Facing.turnToward(owner, towards);
                }
                owner.setPosition(dashEnd(owner, world, reachOf(skill, owner, towards)));
            }
            case EMPOWER -> {
                // Re-casting refreshes rather than stacking: two overlapping copies
                // of the same buff is a question with no obvious answer, and the
                // cooldown already decides how often it can be had.
                boostFrames = skill.durationFrames();
                boostPercent = skill.boostAt(level);
            }
        }
        return true;
    }

    /**
     * Take aim. Nothing is hurt yet — that is the whole point of a wind-up.
     *
     * <p>He turns to the target and his weapon is pointed at it, so the archer on
     * screen is visibly drawing on something rather than standing idle while a
     * monster's health drops for no reason the player can see. Pointing the weapon
     * is not a cheat: he really is aiming at it, and if it is inside his ordinary
     * range he would have been shooting at it anyway.
     */
    private void beginDrawing(Skill skill, int level, GameObject owner, GameObject victim) {
        drawing = skill;
        drawnAtLevel = level;
        drawnFor = victim.getId();
        loosesIn = skill.windUpFrames();
        Facing.turnToward(owner, victim);
        var weapon = owner.findModule(WeaponUpdate.class);
        if (weapon != null) {
            weapon.attack(victim.getId());
            // Pointing his weapon is part of drawing, not an order. Said out loud
            // so his brain does not read it as one and send him chasing.
            lastAimedAt = victim.getId();
        }
    }

    /**
     * The drawn shot goes.
     *
     * <p>Its target may have died while he was drawing, and then the shot is simply
     * lost — he committed when he pressed the key, and the cooldown went with it.
     * That is the cost of a skill that takes time, and it is the reason it hits
     * harder than the one that does not.
     */
    private void looseTheDrawnShot() {
        var skill = drawing;
        var owner = getOwner();
        var world = owner.getWorld();
        drawing = null;
        if (world == null || owner.isEffectivelyDead()) {
            return;
        }
        var victim = world.findObject(drawnFor);
        if (victim == null || victim.isEffectivelyDead() || victim.getBody() == null) {
            return;
        }
        Facing.turnToward(owner, victim);
        land(skill, drawnAtLevel, owner, victim);
    }

    /**
     * Deal a strike's damage — as an arrow if the skill has one, otherwise where
     * the victim stands.
     *
     * <p>The figure is settled here rather than when the key was pressed, the same
     * instant an ordinary shot settles its own: what he is worth is what he is
     * worth as the string leaves his fingers.
     */
    private void land(Skill skill, int level, GameObject owner, GameObject victim) {
        float damage = damageOf(skill, level);
        if (skill.hasProjectile() && Shot.loose(owner, victim, damage, DamageType.NORMAL,
                skill.projectile(), settings.heavyArrowSpeed(), settings.arrowMuzzleOffset())) {
            // The client draws a muzzle flash and plays the shooting sound off this
            // — the same moment the bow announces, for the same reason.
            var world = owner.getWorld();
            world.post(new WeaponFired(world.getFrame(), owner.getId(), victim.getId(),
                    owner.getPosition(), victim.getPosition()));
            return;
        }
        victim.getBody().damage(damage);
        stealLife(owner, damage);
    }

    /**
     * What a skill hits for: its own figure at this level, the ultimate's window
     * if one is open, and whatever the run's cards have added to it.
     */
    private float damageOf(Skill skill, int level) {
        return skill.damageAt(level) * damageMultiplier()
                * powers.skillDamageMultiplier(skill.key());
    }

    /**
     * Give the caster back his share of what he just dealt.
     *
     * <p>Off the damage the skill was worth rather than off the health actually
     * removed, which is the same figure except against something already nearly
     * dead. Taking the smaller of the two would make the last blow of a fight the
     * one that healed least, which is exactly the blow a player is counting on.
     */
    private void stealLife(GameObject owner, float dealt) {
        float share = powers.lifestealFraction();
        if (share > 0f && owner.getBody() != null) {
            owner.getBody().heal(dealt * share);
        }
    }

    /**
     * The creature the player clicked, if it is still something he may hit from
     * where he stands. Everything the un-aimed path checks, asked of one named
     * thing instead of all of them.
     */
    private static GameObject aimedAt(GameObject owner, World world, ObjectId at, float range) {
        var victim = world.findObject(at);
        if (victim == null || victim.getBody() == null || victim.isEffectivelyDead()) {
            return null;
        }
        if (world.getRelationship(owner.getPlayerIndex(), victim.getPlayerIndex())
                != Relationship.ENEMIES) {
            return null;
        }
        return World.reachBetween(owner, victim) <= range ? victim : null;
    }

    /**
     * How far a dash actually carries: the skill's distance, or the spot he was
     * pointed at if that is nearer. Sent two steps away he takes two steps —
     * being flung the full distance past a click is not what the click said.
     */
    private static float reachOf(Skill skill, GameObject owner, Coord3D towards) {
        return towards == null ? skill.distance()
                : Math.min(skill.distance(), owner.getPosition().distance(towards));
    }

    /**
     * The nearest living enemy within {@code range}, measured surface to surface
     * as weapons measure it. Ties go to the lower object id — not for fairness but
     * for reproducibility: two equidistant skeletons must not be chosen by
     * whichever the world happens to list first.
     */
    private static GameObject nearestEnemy(GameObject owner, World world, float range) {
        GameObject best = null;
        float bestReach = Float.MAX_VALUE;
        for (var candidate : enemiesWithin(owner, world, range)) {
            float reach = World.reachBetween(owner, candidate);
            if (reach < bestReach
                    || (reach == bestReach && candidate.getId().value() < best.getId().value())) {
                best = candidate;
                bestReach = reach;
            }
        }
        return best;
    }

    private static List<GameObject> enemiesWithin(GameObject owner, World world, float radius) {
        int player = owner.getPlayerIndex();
        return world.objectsInRange(owner.getPosition(), radius, candidate ->
                candidate.getBody() != null
                        && !candidate.isEffectivelyDead()
                        && world.getRelationship(player, candidate.getPlayerIndex())
                                == Relationship.ENEMIES);
    }

    /**
     * How far forward the caster actually gets.
     *
     * <p>Walked in steps rather than jumped, and the last clear step is where he
     * lands. A dash that simply added the distance would put him inside a wall
     * whenever the room was too small for it — and a unit whose centre is in stone
     * is the bug that took a day to find the last time.
     */
    private static Coord3D dashEnd(GameObject owner, World world, float distance) {
        float facing = owner.getOrientation();
        float dx = (float) StrictMath.cos(facing);
        float dy = (float) StrictMath.sin(facing);
        var from = owner.getPosition();
        var landed = from;
        for (float gone = DASH_STEP; gone <= distance; gone += DASH_STEP) {
            var step = new Coord3D(from.x() + dx * gone, from.y() + dy * gone, from.z());
            if (world.isGroundBlocked(step) || world.findBlocker(owner, step) != null) {
                break; // as far as he gets; the rest of the dash is wall
            }
            landed = step;
        }
        return landed;
    }

    @Override
    public float damageMultiplier() {
        return boostFrames > 0 ? 1f + boostPercent / 100f : 1f;
    }

    /**
     * His bow keeps quiet while he is drawing a heavy shot.
     *
     * <p>Without this he loosed two arrows for one keypress: the skill points his
     * weapon at the target so that he is visibly taking aim, and his weapon —
     * which updates before this module does — took that as an order and fired the
     * ordinary shot on the spot. He has one bow and he is using it.
     *
     * <p>The reload runs on underneath, so his ordinary shooting resumes the frame
     * after the heavy one leaves rather than starting a fresh wait.
     */
    @Override
    public boolean holdingFire() {
        return drawing != null;
    }

    /**
     * A card that grants a charge is felt at once, not at the next recharge.
     *
     * <p>Only the difference is handed over. Setting the slot to full instead
     * would also refill whatever he had already spent, which is a different and
     * much better card than the one on the table.
     */
    private void handOverAnyNewCharge(int slot) {
        int cap = chargesOf(slot);
        if (cap > chargeCap[slot]) {
            chargesLeft[slot] += cap - chargeCap[slot];
        }
        chargeCap[slot] = cap;
    }

    @Override
    public void update() {
        for (int slot = 0; slot < cooldowns.length; slot++) {
            handOverAnyNewCharge(slot);
            if (cooldowns[slot] > 0 && --cooldowns[slot] <= 0) {
                chargesLeft[slot] = chargeCap[slot]; // recharged: all of them back
            }
        }
        if (boostFrames > 0) {
            boostFrames--;
        }
        if (drawing != null && --loosesIn <= 0) {
            looseTheDrawnShot();
        }
    }
}
