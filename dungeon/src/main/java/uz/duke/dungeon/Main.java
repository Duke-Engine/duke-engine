package uz.duke.dungeon;

import java.util.List;

import uz.duke.client3d.Duke3D;
import uz.duke.client3d.EdgeScroll;
import uz.duke.client3d.Fog;
import uz.duke.client3d.Hotkeys;
import uz.duke.client3d.Shell;
import uz.duke.client3d.Tileset;
import uz.duke.client3d.Visuals;
import uz.duke.core.thing.ObjectId;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.content.HeroLook;
import uz.duke.dungeon.content.ThemeArt;
import uz.duke.dungeon.skill.CastSkill;
import uz.duke.dungeon.stage.Stages;

/**
 * Opens the dungeon in the engine's 3D client.
 *
 * <p>No visuals are bound. Every creature falls back to a coloured primitive,
 * lit and cast into a world the camera looks across rather than straight down —
 * which is the whole difference between a diagram of a game and a game.
 *
 * <p>The front menu is this game's, not the client's. A dungeon has a run to
 * begin and a way out, and nothing else: it is one player against the dungeon,
 * so it does not offer to host a LAN game — which the client used to, on the
 * grounds that the monsters count as a second player.
 *
 * <p>Controls are the engine's own: left-click the hero to select him,
 * right-click the floor to walk or a skeleton to attack it.
 *
 * <p>Each launch draws a different dungeon. The seed is the one thing here the
 * clock touches — and it is outside the simulation, choosing <em>which</em>
 * deterministic dungeon to play rather than reaching into how one is built. From
 * that seed on, generation and the run loop are a pure, reproducible chain.
 */
public final class Main {

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(Main.class.getName());

    private Main() {
    }

    /** One movement, from the file that holds it, under the name the game uses. */
    private static void animation(Visuals.UnitVisual unit, String assetPath, String clipName) {
        if (assetPath != null) {
            unit.animationFrom(assetPath, clipName);
        }
    }

    /**
     * One recipe for what a thing in flight looks like, handed to the client.
     *
     * <p>Nothing here is a decision either. Which effects a recipe uses is a list
     * of names in the settings file, and a name this client does not know is
     * ignored with a warning rather than refused — a burning arrow whose trail is
     * missing is still an arrow that arrives.
     */
    private static void effect(Visuals visuals, DungeonSettings.EffectLook look) {
        visuals.effect(look.name(), recipe -> {
            for (var kind : look.kinds()) {
                recipe.kind(kind);
            }
            for (var part : look.parts()) {
                recipe.part(part);
            }
            recipe.colours(look.awtColour(), look.awtFade())
                    .light(look.awtLight(), look.lightPower(), look.lightRadius())
                    .particles(look.particles(), look.particleSize(), look.particleLife(),
                            look.spread())
                    .orb(look.orbSize())
                    .burst(look.burstParticles(), look.burstSize(), look.burstSeconds())
                    .wave(look.waveFrom(), look.waveTo(), look.waveSeconds(), look.waveEase(),
                            look.waveEdge(), look.waveWash())
                    .mark(look.markRadius(), look.markSeconds())
                    .shake(look.shakeSeconds(), look.shakePower());
        });
    }

    /**
     * One layer, in the client's words: whatever the file said, laid over the
     * client's own defaults.
     *
     * <p>Only what was said, because the defaults are the client's and are written
     * down once, in {@code EffectLayer.Builder}. Package-private so the game's own
     * test can ask what a block in the file turns into on screen.
     */
    static uz.duke.client3d.EffectLayer layerOf(DungeonSettings.EffectLayerArt art,
            String folder) {
        var layer = uz.duke.client3d.EffectLayer.builder();
        art.fields().forEach((field, value) -> {
            switch (field) {
                case "type" -> layer.type(value);
                case "texture" -> layer.texture(value.isBlank() ? "" : folder + value);
                case "additive" -> layer.additive(Boolean.parseBoolean(value));
                case "count" -> layer.count(Integer.parseInt(value));
                case "colourStart" -> layer.colourStart(Integer.parseInt(value));
                case "colourEnd" -> layer.colourEnd(Integer.parseInt(value));
                case "lightColour" -> layer.lightColour(Integer.parseInt(value));
                case "direction" -> layer.direction(value);
                case "at" -> layer.at(value);
                case "measure" -> layer.measure(value);
                case "follows" -> layer.follows(Boolean.parseBoolean(value));
                default -> number(layer, field, Float.parseFloat(value));
            }
        });
        return layer.build();
    }

    private static void number(uz.duke.client3d.EffectLayer.Builder layer, String field,
            float value) {
        switch (field) {
            case "rate" -> layer.rate(value);
            case "delay" -> layer.delay(value);
            case "seconds" -> layer.seconds(value);
            case "lifeMin" -> layer.lifeMin(value);
            case "lifeMax" -> layer.lifeMax(value);
            case "sizeStart" -> layer.sizeStart(value);
            case "sizeEnd" -> layer.sizeEnd(value);
            case "sizeEase" -> layer.sizeEase(value);
            case "sizeJitter" -> layer.sizeJitter(value);
            case "alphaStart" -> layer.alphaStart(value);
            case "alphaEnd" -> layer.alphaEnd(value);
            case "colourEase" -> layer.colourEase(value);
            case "fadeIn" -> layer.fadeIn(value);
            case "fadeOut" -> layer.fadeOut(value);
            case "speedMin" -> layer.speedMin(value);
            case "speedMax" -> layer.speedMax(value);
            case "spread" -> layer.spread(value);
            case "radius" -> layer.radius(value);
            case "height" -> layer.height(value);
            case "gravity" -> layer.gravity(value);
            case "drag" -> layer.drag(value);
            case "stretch" -> layer.stretch(value);
            case "spin" -> layer.spin(value);
            case "turn" -> layer.turn(value);
            case "turnJitter" -> layer.turnJitter(value);
            case "pulseRate" -> layer.pulseRate(value);
            case "pulseDepth" -> layer.pulseDepth(value);
            case "lightPower" -> layer.lightPower(value);
            case "lightRadius" -> layer.lightRadius(value);
            case "fall" -> layer.fall(value);
            case "cover" -> layer.cover(value);
            case "rise" -> layer.rise(value);
            case "riseEase" -> layer.riseEase(value);
            default -> LOG.warning(() -> "an effect layer says " + field
                    + ", which the client does not draw -- ignored");
        }
    }

    /**
     * How long and how far each look's skill goes, handed to the client so a layer
     * that says neither is drawn exactly that long and exactly that wide.
     *
     * <p>From the SKILL, and nowhere else. The knight's guard used to say 4.0 in its
     * effect block and 120 frames in its skill block, and the meteor's warning said
     * 1.5 seconds beside a comment asking whoever changed WindUpFrames to remember
     * to change it too. Now there is one number and the picture follows it.
     *
     * <p>How long: a skill that lasts gives its duration; one that is aimed and then
     * lands gives its wind-up, which is how long the ground is marked; one that
     * slows what it caught gives the slow, which is how long they wear the frost.
     * How far: its radius. And a projectile's effect is given its skill's numbers
     * too -- a meteor's falling mark takes the same wind-up to come down, and a
     * fireball's blast is as wide as the skill that threw it.
     */
    static void measureLooks(uz.duke.client3d.Visuals visuals, DungeonSettings settings) {
        float perSecond = uz.duke.core.GameConstants.LOGICFRAMES_PER_SECOND;
        var carriedBy = new java.util.HashMap<String, String>();
        for (var arrow : settings.projectiles()) {
            carriedBy.put(arrow.name(), arrow.effect());
        }
        for (var skill : settings.skills()) {
            var carried = skill.hasProjectile() ? carriedBy.get(skill.projectile()) : null;
            if (skill.hasLook()) {
                int frames = skill.durationFrames() > 0 ? skill.durationFrames()
                        : skill.windUpFrames() > 0 ? skill.windUpFrames()
                        : skill.slowFrames();
                if (frames > 0) {
                    visuals.effectSeconds(skill.look(), frames / perSecond);
                }
                visuals.effectReach(skill.look(), skill.radius());
            }
            if (carried != null) {
                visuals.effectReach(carried, skill.radius());
                if (skill.effect() == uz.duke.dungeon.skill.SkillEffect.METEOR
                        && skill.windUpFrames() > 0) {
                    visuals.effectSeconds(carried, skill.windUpFrames() / perSecond);
                }
            }
        }
    }

    /** One portrait block, in the words the client keeps them in. */
    private static uz.duke.client3d.PortraitLook portraitLook(
            uz.duke.dungeon.content.PortraitArt art) {
        return new uz.duke.client3d.PortraitLook(
                new uz.duke.client3d.PortraitLook.Camera(art.head(), art.show(),
                        art.yaw(), art.pitch(), art.fov()),
                new uz.duke.client3d.PortraitLook.Clips(art.calm(), art.fight(),
                        art.hurt(), art.dead(), art.levelUp()),
                art.hurtBelowPercent(), art.hurtSpeed());
    }

    /** What this creature has in its hand, if the file gave it anything. */
    private static void carry(Visuals.UnitVisual unit, uz.duke.dungeon.content.Held held) {
        if (held.isCarried()) {
            unit.holds(held.model(), held.bone(), held.scale())
                    .heldTurn(held.pitch(), held.yaw(), held.roll())
                    .heldAt(held.x(), held.y(), held.z());
        }
    }

    /** Everything he carries, in the order the file named it. */
    private static void carry(Visuals.UnitVisual unit,
            java.util.List<uz.duke.dungeon.content.Held> held) {
        for (var one : held) {
            carry(unit, one);
        }
    }

    /**
     * An arrow's look. Two creatures use it — his ordinary shot and the one Q
     * looses — differing only in the numbers, which is why it is one method.
     */
    private static void arrow(Visuals visuals, String template, DungeonSettings.ArrowLook look) {
        visuals.unit(template, unit -> {
            unit.colour(look.awtTint()); // the minimap dot, and the fallback shape
            unit.effect(look.effect()).effectAt(look.effectOffset());
            if (!look.hasModel()) {
                // A fireball has no file anywhere: its effect is its body, and the
                // height is still wanted, because a shot travels at bow height
                // whether or not there is a mesh on it.
                unit.scale(0.28f).yOffset(look.height());
                return;
            }
            unit.modelPart(look.model(), look.part())
                    .scale(look.scale())
                    .facing(look.facing())
                    // Drawing only: the simulation is flat, so height is not a
                    // position but the line the shot is drawn along.
                    .yOffset(look.height())
                    // Not decoration: the tint is what gets it a material this
                    // client can light. Without one it keeps the loader's PBR and
                    // the arrow is a black splinter.
                    .tint(look.awtTint());
        });
    }

    /**
     * The panel's painted edges, turned from the file's words into the client's.
     *
     * <p>Every name in the file has to be one the panel answers to, and the panel
     * is the only thing that knows the list — so this is where the two meet, in
     * the same place and for the same reason the sound channels do below.
     *
     * <p>An unknown name costs that one piece and nothing else. The whole point of
     * a skin is that it is a coat of paint: a misspelt part is drawn the way it
     * was drawn before there was any paint, which is a working panel, and refusing
     * to start the game over a decoration would be the wrong trade.
     */
    private static uz.duke.client3d.PanelSkin panelSkin(DungeonSettings settings) {
        var pieces = new java.util.LinkedHashMap<String, uz.duke.client3d.PanelSkin.Piece>();
        var known = java.util.Set.of(uz.duke.client3d.PanelSkin.MINIMAP,
                uz.duke.client3d.PanelSkin.PORTRAIT, uz.duke.client3d.PanelSkin.SLOT,
                uz.duke.client3d.PanelSkin.GAUGE, uz.duke.client3d.PanelSkin.CHIP,
                uz.duke.client3d.PanelSkin.BUTTON, uz.duke.client3d.PanelSkin.ITEM,
                uz.duke.client3d.PanelSkin.DIVIDER, uz.duke.client3d.PanelSkin.BANNER,
                uz.duke.client3d.PanelSkin.BANNER_WON, uz.duke.client3d.PanelSkin.BANNER_LOST);
        for (var piece : settings.skin()) {
            if (!known.contains(piece.name())) {
                LOG.warning(() -> "DungeonSkin names no part of the panel: " + piece.name()
                        + " — that part is drawn as it was; known parts are " + known);
                continue;
            }
            pieces.put(piece.name(), new uz.duke.client3d.PanelSkin.Piece(
                    piece.texture(), piece.inset(), piece.scale(), piece.awtTint()));
        }
        return new uz.duke.client3d.PanelSkin(pieces);
    }

    /**
     * How a creature's bar is drawn, handed over whole.
     *
     * <p>Package-private rather than private so the game's own test can ask for
     * exactly what the client will be given. The table is the thing worth
     * checking -- see {@code DungeonUnitBarTest} -- and a test that rebuilt it
     * from the same accessors would be checking its own copy.
     */
    static uz.duke.client3d.UnitBarLook unitBars(DungeonSettings settings) {
        var steps = new java.util.ArrayList<uz.duke.client3d.UnitBarLook.Step>();
        for (var rung : settings.unitBarSegments()) {
            steps.add(new uz.duke.client3d.UnitBarLook.Step(rung.upTo(), rung.value()));
        }
        return new uz.duke.client3d.UnitBarLook(steps,
                settings.unitBarShortestAt(), settings.unitBarLongestAt(),
                settings.unitBarShortest(), settings.unitBarLongest(),
                settings.unitBarHeight(), settings.unitBarManaHeight(),
                settings.unitBarGap(), settings.unitBarLift(),
                settings.unitBarRing(), settings.unitBarRingEdge(),
                settings.unitBarRingGap(), settings.unitBarArc(),
                settings.unitBarEnemy(), settings.unitBarFriend(), settings.unitBarMana(),
                settings.unitBarTrough(), settings.unitBarTick(), settings.unitBarRingFace(),
                settings.unitBarRingRim(), settings.unitBarBossRim(),
                settings.unitBarLettering(),
                settings.unitBarNameSize(), settings.unitBarBossNameSize(),
                settings.unitBarCountSize(), settings.unitBarLevelSize());
    }

    /**
     * Every moment the game has a sound for, handed to the client at launch.
     *
     * <p>Nothing here is a decision. Which channel, how loud, how far apart and
     * which files all come out of {@code dungeon.ini}, so a new sound is a block
     * in that file and this method does not change. An unknown channel name is
     * read as an effect rather than refused: a typo should cost the right knob,
     * not the sound.
     */
    private static uz.duke.client3d.SoundBank soundsOf(DungeonSettings settings) {
        var bank = uz.duke.client3d.SoundBank.create();
        bank.voiceGap(settings.voiceGapSeconds());
        for (var cue : settings.sounds()) {
            bank.cue(cue.name(), channelOf(cue.channel()), cue.positional(), cue.gain(),
                    cue.gapSeconds(), cue.files(), cue.label());
        }
        return bank.build();
    }

    private static uz.duke.client3d.SoundBank.Channel channelOf(String named) {
        for (var channel : uz.duke.client3d.SoundBank.Channel.values()) {
            if (channel.name().equalsIgnoreCase(named)) {
                return channel;
            }
        }
        return uz.duke.client3d.SoundBank.Channel.EFFECTS;
    }

    /**
     * Every way a floor can look, handed to the client at launch.
     *
     * <p>One registered look per theme <em>and</em> variation, because a variation
     * changes the kit and the client only ever holds finished looks. Which of them
     * a floor wears is the run's to say, floor by floor, down the status channel
     * — see {@code DungeonRun} and {@code HeroStatus}.
     *
     * <p>Nothing here is a decision. Every number and every path comes out of
     * {@code dungeon.ini}, so a fourth theme is three blocks and a folder of
     * models, and this method does not change.
     */
    private static void themes(Visuals visuals, DungeonSettings settings) {
        for (var theme : settings.themes().all()) {
            for (var variation : theme.tones()) {
                var tone = theme.toneWithPaths(variation);
                visuals.theme(theme.name() + "," + variation.name(), look -> {
                    look.tiles(Tileset.create()
                            .floor(tone.floor())
                            .wall(tone.wall())
                            .corner(tone.corner())
                            .stairs(theme.stairsPath())
                            .tileSize(theme.tileSize())
                            .wallTileSize(theme.wallTileSize())
                            .wallHeight(theme.wallHeight())
                            .wallLift(theme.wallLift())
                            .wallShift(theme.wallShift())
                            .ownMaterials(theme.ownMaterials())
                            .wallFillsRock(theme.standing().fillsRock())
                            .wallClump(theme.standing().clump())
                            .wallSpread(theme.standing().spread())
                            .wallVariety(theme.standing().variety())
                            .rockFace(theme.rockFacePath())
                            .capTint(theme.capTint())
                            .storeyShade(theme.storeyShadePercent() / 100f)
                            .tint(tone.tint()));
                    look.fogTint(theme.fogTint());
                    for (var themed : theme.monsters()) {
                        themedCreature(look, theme.monsterWithPaths(themed), settings);
                    }
                });
            }
        }
    }

    /** One creature drawn the way a theme wants it, described from nothing. */
    private static void themedCreature(Visuals.Theme look, ThemeArt.ThemeMonster themed,
            DungeonSettings settings) {
        var art = themed.look();
        look.unit(themed.template(), unit -> {
            unit.colour(art.awtTint());
            if (!art.hasModel()) {
                return;
            }
            unit.model(art.model())
                    .texture(art.texture())
                    .tint(art.awtTint())
                    .scale(art.modelScale())
                    .facing(art.facing())
                    .idle(art.idle())
                    .walk(art.walk())
                    .attack(art.attack())
                    .hurt(art.hurt())
                    .effect(art.effect())
                    .die(themed.death() != null ? themed.death() : settings.deathClip());
            carry(unit, art.held());
            // Borrowed only when the file says so. A themed creature usually comes
            // with a model of its own, and a model of its own carries its own
            // clips -- copying them onto it from a second copy of the same file
            // rebinds the tracks to the wrong skeleton and the thing collapses.
            // Naming the game's shared library here is for a theme that re-skins a
            // creature with another model from that same kit.
            if (themed.animationsFrom() != null) {
                unit.animationsFrom(themed.animationsFrom());
            }
        });
    }

    public static void main(String[] args) {
        var settings = DungeonSettings.load();
        var session = chosenGame(args, settings);
        // Held rather than passed straight in: the question below reaches back
        // into it, because whose eyes open the map is part of who you chose.
        var visuals = looks(settings);
        // Both held rather than passed straight in, because the question below
        // reaches back into both: whose eyes open the map, what his four keys ask
        // the player to point at, and how far each of them reaches are all part of
        // who you chose, and none of them is known until he is chosen.
        var keys = controls(settings);
        Duke3D.launch(session.game(), visuals, Shell.create()
                .entry(Shell.Entry.PLAY, "Enter the dungeon")
                .entry(Shell.Entry.SETTINGS)
                .entry(Shell.Entry.QUIT)
                // Which turns Play into a path rather than a start -- how, then
                // where, then who. Nothing opens until it is walked. See howToPlay.
                .asking(howToPlay(session, settings, visuals, keys)), keys);
    }

    /**
     * Point the four keys at what THIS hero's skills ask the player for.
     *
     * <p>One hero's four, never the file's twelve. A key belongs to a SLOT rather
     * than to a skill — all three of them cast on Q, W, E and R — so binding every
     * skill in the file would leave whichever hero was read last deciding what Q
     * asks for. That is not a small wrongness: the rogue's Q wants a creature, the
     * knight's wants a patch of floor and the mage's wants a direction.
     *
     * <p><b>Called again every time a hero is chosen</b>, and that is the whole
     * point of it being a method. Settled once at startup it was settled from
     * {@code DefaultHero}, so picking anybody else got you his skills with the
     * default hero's aims — and a skill handed the wrong sort of target does not
     * misfire, it refuses: {@code SkillBook} declines a fireball with no direction
     * and leaves the cooldown unspent, which from a chair is a key that does
     * nothing whatever.
     *
     * <p>Rebinding a letter replaces what it asks for and not whether the game has
     * claimed it, so the client's own controls are untouched by this — the letters
     * were claimed at startup and every hero in the file uses the same four. A
     * hero who wanted a fifth letter would need that letter claimed up front;
     * {@code HeroChoiceTest} holds the four still so the day that changes is a
     * failing test rather than a dead key.
     */
    static void aimsFor(Hotkeys keys, DungeonSettings settings, String hero) {
        for (var skill : settings.skillsFor(hero)) {
            char key = skill.key();
            switch (skill.effect().aim()) {
                case UNIT -> keys.onUnit(key, (game, id) -> game.postCommand(new CastSkill(
                        game.getLocalPlayerIndex(), key, new ObjectId(id), null)));
                case GROUND -> keys.onGround(key, (game, spot) -> game.postCommand(
                        new CastSkill(game.getLocalPlayerIndex(), key, null, spot)));
                case OPEN_GROUND -> keys.onOpenGround(key, (game, spot) -> game.postCommand(
                        new CastSkill(game.getLocalPlayerIndex(), key, null, spot)));
                case SELF -> keys.on(key, game -> game.postCommand(
                        new CastSkill(game.getLocalPlayerIndex(), key)));
            }
        }
    }

    /**
     * And draw HIS four rings, for the same reason and with the same lifetime.
     *
     * <p>A ring is keyed by the letter too, so the rogue's Q and the mage's Q are
     * two quite different circles — one round a creature, one down a lane. Left at
     * the file's hero the mage would have been shown where his fireball could
     * reach in the shape of somebody else's skill.
     */
    static void ringsFor(Visuals visuals, DungeonSettings settings, String hero) {
        for (var skill : settings.skillsFor(hero)) {
            visuals.skillRange(rangeOf(skill, settings.ringSelfRadius()));
        }
    }

    /** The letter the attack order is on; see {@link #orders}. */
    static final char ATTACK_KEY = 'A';

    /**
     * And the fifth ring: how far his ordinary attack reaches.
     *
     * <p>The question a player is actually asking when he reaches for the attack
     * key — "from where?" — and the one number on the bar he has never been shown.
     * A knight reaches eleven and a rogue sixty, which is most of what playing one
     * rather than the other IS, and neither of them was ever drawn.
     *
     * <p><b>Not a fence.</b> Every other ring in the game is the edge of what a
     * skill can do and a click past it is pulled back to the edge; this one is a
     * statement, and the same key sends him to fight his way across the whole
     * floor. The client is told as much — see {@code DukeRtsApp.aimArmedKey}.
     *
     * <p>Read off his own template rather than named here, for the reason
     * {@code HeroBrain.reachOfHisWeapon} gives: two copies of one number drift the
     * first time anybody re-tunes him, and a ring that lies about his reach is
     * worse than no ring.
     */
    static void attackRingFor(Visuals visuals, uz.duke.game.DukeGame game, String hero) {
        float reach = reachOf(game, hero);
        if (reach > 0f) {
            visuals.skillRange(new uz.duke.client3d.SkillRange(ATTACK_KEY,
                    uz.duke.client3d.SkillRange.Shape.AT_A_CREATURE, reach, 0f));
        }
    }

    /** How far that template's weapon reaches, or 0 if it carries none. */
    static float reachOf(uz.duke.game.DukeGame game, String hero) {
        // Null until the game is started, which the hero menu may well be drawn
        // over. No reach is no ring, which is the right answer for a run that has
        // not begun.
        var logic = game.getLogic();
        var template = logic == null ? null : logic.findTemplate(hero);
        if (template == null) {
            return 0f;
        }
        float reach = 0f;
        for (var module : template.getModules()) {
            if (module.data() instanceof uz.duke.rts.module.WeaponUpdate.Data weapon) {
                reach = Math.max(reach, weapon.attackRange());
            }
        }
        return reach;
    }

    /**
     * The endless dungeon, or the stage somebody asked for.
     *
     * <p>A broken stage stops here, loudly, with the list of what is wrong with
     * it. It does not fall back to the endless dungeon: a player who asked for a
     * stage and silently got a random floor instead would have no way of knowing
     * anything had gone wrong, and an author editing one would think his last
     * change had worked.
     */
    private static Dungeon.Session chosenGame(String[] args, DungeonSettings settings) {
        var path = Stages.chosen(args, settings);
        if (path == null) {
            // The seed is the one thing the clock touches, and it is outside the
            // simulation: it chooses WHICH deterministic dungeon to play.
            return Dungeon.newSession(System.nanoTime(), settings);
        }
        return Dungeon.newStageSession(Stages.load(path, settings), settings);
    }

    /**
     * Who the player is asked to be, before anything opens.
     *
     * <p>The roster is the file's — one entry per {@code DungeonHero} block — so a
     * third hero appears on this screen by existing, and nothing here is edited.
     * The words under each name are his own {@code Title}.
     *
     * <p><b>Taking one starts the run, and nothing else does.</b> The world was
     * built before the window opened, with whoever {@code DefaultHero} names,
     * because a client cannot show a menu over a game that does not exist yet.
     * That hero is never seen: the choice lays the first floor again with whoever
     * was picked, which is the same road a death takes and for the same reason —
     * nothing the unchosen hero had was the chosen one's.
     *
     * <p>So a stage does not say which hero plays it, and cannot. The menu is the
     * only thing that decides, which is the whole of the rule.
     */
    static uz.duke.client3d.Shell.Question whoToPlay(Dungeon.Session session,
            DungeonSettings settings, Visuals visuals, Hotkeys keys) {
        var options = new java.util.ArrayList<uz.duke.client3d.Shell.Option>();
        for (var hero : settings.heroes()) {
            var him = hero.name();
            options.add(new uz.duke.client3d.Shell.Option(
                    displayNameOf(session, him), hero.title(),
                    () -> {
                        // The dark opens around HIS eyes. Named rather than
                        // measured, so the radius is his own VisionRange -- and a
                        // knight sees a shorter way than an archer, which is part
                        // of playing him. Left pointing at the file's hero, the
                        // map would never open at all: nothing of that template is
                        // in the dungeon.
                        visuals.discoveredBy(him);
                        // ★ And HIS four keys, and HIS four rings. Everything the
                        // client knows about a skill is keyed by the letter, and
                        // until this line it was all settled at startup from
                        // DefaultHero -- so whoever you picked, Q asked you to
                        // point at whatever the FILE'S hero's Q needed. Choosing
                        // the mage got you the rogue's aims: his Q wants a
                        // creature, and a fireball handed a creature instead of a
                        // direction has nowhere to fly and refuses, which from a
                        // chair is a skill that does nothing at all.
                        aimsFor(keys, settings, him);
                        ringsFor(visuals, settings, him);
                        attackRingFor(visuals, session.game(), him);
                        session.run().startWith(session.game(), him);
                    }));
        }
        return new uz.duke.client3d.Shell.Question(settings.hudChooseHeroWord(),
                settings.hudChooseHeroHint(), options);
    }

    /**
     * The path the player walks before a run begins: how, then where, then who.
     *
     * <p>Two games share this loop. The endless descent draws a floor nobody has
     * seen and asks how far down you get; a stage hands back the same rooms every
     * time and asks you to learn them. Which of the two is the first thing to
     * settle, because everything after it differs — one has a list of stages to
     * pick from and the other has nowhere to go but down.
     *
     * <p><b>Nothing is offered that cannot be played.</b> A build shipping no
     * stages, or only broken ones, has no stage row at all rather than a row
     * leading to an empty column — see {@code Stages.all}, which drops what it
     * cannot read and says so in the log. With no stages the whole question
     * collapses to the hero, which is what the game asked yesterday.
     */
    static uz.duke.client3d.Shell.Question howToPlay(Dungeon.Session session,
            DungeonSettings settings, Visuals visuals, Hotkeys keys) {
        var hero = whoToPlay(session, settings, visuals, keys);
        var stages = uz.duke.dungeon.stage.Stages.all(settings);
        if (stages.isEmpty()) {
            return hero; // nothing to choose between; the only question left is who
        }
        var modes = List.of(
                new uz.duke.client3d.Shell.Option(settings.hudEndlessWord(),
                        settings.hudEndlessBlurb(),
                        () -> session.run().playing(uz.duke.dungeon.run.Floors.generated(
                                System.nanoTime(), settings)),
                        hero),
                new uz.duke.client3d.Shell.Option(settings.hudStagesWord(),
                        settings.hudStagesBlurb(), null,
                        whichStage(session, settings, stages, hero)));
        return new uz.duke.client3d.Shell.Question(settings.hudChooseModeWord(),
                settings.hudChooseModeHint(), modes);
    }

    /**
     * Which frozen dungeon, out of the ones this build can actually play.
     *
     * <p>The rows are the stages' own words — an author names his stage and says
     * one line about it in the file, and that is what a player reads. Nothing here
     * is a list somebody keeps in step: drop a {@code .stage} file in the folder
     * and it is on this screen.
     *
     * <p>Taking one only says which floors to lay. Who lays them is the next
     * question, which is the same one the endless descent asks — a stage does not
     * decide who plays it, and cannot.
     */
    private static uz.duke.client3d.Shell.Question whichStage(Dungeon.Session session,
            DungeonSettings settings, List<uz.duke.dungeon.stage.Stages.Listed> stages,
            uz.duke.client3d.Shell.Question hero) {
        var rows = new java.util.ArrayList<uz.duke.client3d.Shell.Option>();
        for (var listed : stages) {
            var stage = listed.stage();
            rows.add(new uz.duke.client3d.Shell.Option(stage.name(), stage.description(),
                    () -> session.run().playing(
                            uz.duke.dungeon.run.Floors.ofStage(stage)),
                    hero));
        }
        return new uz.duke.client3d.Shell.Question(settings.hudChooseStageWord(),
                settings.hudChooseStageHint(), rows);
    }

    /**
     * What the player calls him, out of his creature block rather than his art.
     *
     * <p>{@code DisplayName} is what the panel writes over his portrait, so it is
     * what the menu should offer — being asked to choose "Knight" and then playing
     * somebody called Garen is two names for one man. Falls back to the template's
     * own name, which is what the panel falls back to.
     */
    private static String displayNameOf(Dungeon.Session session, String template) {
        var found = session.game().getLogic() == null ? null
                : session.game().getLogic().getThingFactory().findTemplate(template);
        if (found == null || found.getDisplayName() == null || found.getDisplayName().isBlank()) {
            return template;
        }
        return found.getDisplayName();
    }

    /**
     * The keys that cast skills, taken from the same file that says what the
     * skills are.
     *
     * <p>Read out of the settings rather than written here, so adding a fifth
     * skill — or a second hero with different keys — binds its key by being in the
     * file. A press does one thing: post the command. Deciding whether the skill
     * is ready, unlocked, or able to reach what it was aimed at is the
     * simulation's, on its own thread, on a frame boundary.
     *
     * <p>Which of the three ways a key is bound follows from what the skill does,
     * because the effect is what knows: a strike is pointed at a creature, a dash
     * at a spot, and the two that go off around the caster are pointed at nothing.
     * The client keeps the press-then-click; this only says what to send once the
     * player has chosen.
     */
    static Hotkeys controls(DungeonSettings settings) {
        var keys = Hotkeys.create();
        // The file's default hero to begin with, and whoever is actually chosen
        // the moment he is -- see aimsFor, and whoToPlay, which calls it again.
        aimsFor(keys, settings, settings.playedHero());
        // Spending a level on a slot. A click on the badge rather than a letter,
        // so it comes through a door of its own -- and it is a COMMAND like every
        // other decision, settled on a frame boundary where the rules live.
        keys.onRaiseSkill((game, key) -> game.postCommand(new uz.duke.dungeon.skill.UpgradeSkill(
                game.getLocalPlayerIndex(), key)));
        orders(keys);
        // Which single creature he has picked out. The panel describes it, and
        // only the simulation can say what it is worth -- see Watching.
        keys.onWatch((game, id) -> game.postCommand(new uz.duke.dungeon.run.Watching(
                game.getLocalPlayerIndex(), id < 0 ? null : new ObjectId(id))));
        return keys;
    }

    /**
     * What one skill's ring should look like, worked out from the skill itself.
     *
     * <p>The effect is what knows. A strike is pointed at a creature within its
     * range whatever its numbers say; a blast dropped on a spot needs both how far
     * it can be thrown and how much it covers; a shot down a lane needs the length
     * and the width of the lane. So nothing here is a second set of numbers to
     * keep in step with the skills — every figure is the skill's own, and a hero
     * added next month gets his rings by having skills.
     *
     * <p>The one number that is not a skill's is the ring for something that only
     * touches the caster: there is no reach to draw, so the look says how wide to
     * draw "just him".
     */
    static uz.duke.client3d.SkillRange rangeOf(
            uz.duke.dungeon.skill.Skill skill, float selfRadius) {
        var shape = switch (skill.effect()) {
            // A mending is pointed at a creature as a strike is -- one of its own.
            case STRIKE, HEAL -> uz.duke.client3d.SkillRange.Shape.AT_A_CREATURE;
            case AREA_AT_SPOT -> uz.duke.client3d.SkillRange.Shape.AT_A_SPOT;
            case SKILLSHOT -> uz.duke.client3d.SkillRange.Shape.DOWN_A_LANE;
            // A blink is pointed at a spot exactly as a dash is. That it does not
            // cross what is between is the simulation's business; what the player
            // has to do with the mouse is the same thing, so the ring is the same.
            case DASH, BLINK -> uz.duke.client3d.SkillRange.Shape.AT_A_SPOT;
            case METEOR -> uz.duke.client3d.SkillRange.Shape.AT_A_SPOT;
            // A summoning calls them up round him, as far out as its Radius.
            case AREA_DAMAGE, SUMMON -> uz.duke.client3d.SkillRange.Shape.AROUND_HIM;
            // Neither of these reaches past him: one sharpens his sword, the
            // other thickens his skin.
            case EMPOWER, GUARD -> uz.duke.client3d.SkillRange.Shape.ON_HIMSELF;
        };
        float reach = switch (skill.effect()) {
            case STRIKE, AREA_AT_SPOT, SKILLSHOT, HEAL -> skill.range();
            case DASH, BLINK -> skill.distance();
            case METEOR -> skill.range();
            case AREA_DAMAGE, SUMMON -> skill.radius();
            case EMPOWER, GUARD -> selfRadius;
        };
        // What it LEAVES where it lands: a blast's radius, a lane's width. A dash
        // leaves a man, and a circle round a man-sized spot is a second ring saying
        // what the pointer already said -- so it draws none.
        // What it LEAVES where it lands: a blast's radius. A dash leaves a man,
        // and a circle round a man-sized spot is a second ring saying what the
        // pointer already said -- so it draws none.
        //
        // ★ Nor does a SKILLSHOT, though it bursts. Where it bursts depends on
        // where it STOPS, which is the first body or the first wall, so a circle
        // drawn at the far end of its reach is a promise the shot does not have
        // to keep. Its Radius is still real and still hurts -- see SkillBook --
        // it simply is not a thing that can be honestly drawn before the cast.
        float area = switch (skill.effect()) {
            case AREA_AT_SPOT, METEOR -> skill.radius();
            default -> 0f;
        };
        return new uz.duke.client3d.SkillRange(skill.key(), shape, reach, area, skill.hitWidth());
    }

    /**
     * The four orders the buttons beside the map give.
     *
     * <p>Three of them are the engine's own and the mouse already gives them; the
     * buttons are there because a right-click never told the player they existed,
     * and because a key is a faster way to say "walk there" than aiming at a piece
     * of floor that might have a skeleton on it.
     *
     * <p>The fourth is this game's — see {@link uz.duke.dungeon.ai.HoldGround} —
     * and it is the one the engine cannot express: stop cancels a walk and says
     * nothing about his bow.
     *
     * <p>Claimed through the same seam the skills are, so the client's rule about
     * one key doing one thing covers all eight of them together.
     */
    private static void orders(Hotkeys keys) {
        // ★ A, S, D — attack, stop, defend, which is where a hand rests and the
        // order every RTS since Warcraft has put them in. Walking keeps the fourth
        // letter and needs it least: pointing at a piece of floor is what the mouse
        // has always done, and the button is there so a player can SEE that the
        // four orders exist rather than because anybody reaches for it.
        keys.onOpenGround('F', (game, spot) -> game.postCommand(
                new uz.duke.rts.message.GameMessage.MoveTo(
                        game.getLocalPlayerIndex(), selected(game), spot)));
        // Attack takes either, because it means two related things and a player
        // mid-fight should not have to decide which before he knows what his click
        // will land on: that creature, or fight your way to that spot.
        keys.onUnitOrGround('A',
                (game, id) -> game.postCommand(
                        new uz.duke.rts.message.GameMessage.AttackObject(
                                game.getLocalPlayerIndex(), selected(game), new ObjectId(id))),
                (game, spot) -> game.postCommand(new uz.duke.dungeon.ai.AttackMove(
                        game.getLocalPlayerIndex(), spot)));
        // Stop is the loudest of the four: drop the walk, drop the target, and
        // start nothing until told otherwise. Two commands because two things are
        // being said -- the engine's own stop, and this game's "and stay stopped".
        keys.on('S', game -> {
            game.postCommand(new uz.duke.rts.message.GameMessage.StopMoving(
                    game.getLocalPlayerIndex(), selected(game)));
            game.postCommand(new uz.duke.dungeon.ai.HoldGround(
                    game.getLocalPlayerIndex(), true));
        });
        // And Guard is the other half of that pair: stand where you are, and fight
        // whatever comes to you. The same two commands Stop sends with the second
        // one inverted, which is the whole difference between the two buttons —
        // Stop says "stand, and start nothing", this says "stand, and start what
        // walks into you". It is also the state he is in for most of a run, which
        // is why it has a button of its own to light.
        //
        // ★ The first of the two is what it was missing, and without it this was
        // not an order at all. It only took a hold OFF: pressing it while he was
        // walking or chasing did nothing whatever, so it was the one button on the
        // bar a player could press all game without once seeing it do anything —
        // while its own word said Himoya.
        keys.on('D', game -> {
            game.postCommand(new uz.duke.rts.message.GameMessage.StopMoving(
                    game.getLocalPlayerIndex(), selected(game)));
            game.postCommand(new uz.duke.dungeon.ai.HoldGround(
                    game.getLocalPlayerIndex(), false));
        });
    }

    /**
     * Whose orders these are: every unit the player owns.
     *
     * <p>A dungeon holds one hero, so "his units" and "the selection" are the same
     * list — and taking it from the world rather than from the client is what lets
     * a key work when nothing has been clicked on, which is the whole point of
     * having the key.
     */
    private static java.util.List<ObjectId> selected(uz.duke.game.DukeGame game) {
        var mine = new java.util.ArrayList<ObjectId>();
        for (var unit : game.getSnapshot().units()) {
            if (unit.playerIndex() == game.getLocalPlayerIndex() && unit.selectable()) {
                mine.add(new ObjectId(unit.id()));
            }
        }
        return mine;
    }

    /**
     * What each kind of monster looks like, taken from the same file that says how
     * it behaves.
     *
     * <p>With no models yet, colour and size are the only things telling one
     * monster from another — and telling a runner from a brute is a decision the
     * player has to make in the second before they reach him. Player colour cannot
     * do it: every monster belongs to the same side, so they would all be one
     * shade of red, on screen and on the minimap alike.
     */
    private static Visuals looks(DungeonSettings settings) {
        var visuals = Visuals.create();
        for (var kind : settings.monsters()) {
            var look = settings.lookOf(kind);
            visuals.unit(kind.name(), unit -> {
                // The colour is set either way: it is what the minimap dot is
                // drawn in, and what the creature falls back to if its model is
                // missing. A shape in the right colour beats nothing on screen.
                unit.colour(kind.awtColour()).scale(kind.scale());
                if (!look.hasModel()) {
                    return;
                }
                unit.model(look.model())
                        .texture(look.texture())
                        .tint(look.awtTint())
                        .scale(look.modelScale())
                        .facing(look.facing())
                        .idle(look.idle())
                        .walk(look.walk())
                        .attack(look.attack())
                        .hurt(look.hurt())
                        .effect(look.effect());
                carry(unit, look.held());
                unit.die(settings.deathClip());
                for (var library : settings.animationLibraries()) {
                    unit.animationsFrom(library);
                }
            });
        }

        // The heroes come from a different kit on a different skeleton, and they
        // carry something — so they are described their own way rather than
        // squeezed into the monsters'. One block each, named after the creature
        // template, so a second hero is a block rather than an edit here.
        for (var hero : settings.heroes()) {
            if (!hero.hasModel()) {
                continue;
            }
            visuals.unit(hero.name(), unit -> {
                unit.model(hero.model())
                        .scale(hero.modelScale())
                        .facing(hero.facing())
                        .idle(hero.idle())
                        .walk(hero.walk())
                        .attack(hero.attack())
                        .hurt(hero.hurt())
                        .die(hero.death());
                if (hero.texture() != null) {
                    unit.texture(hero.texture());
                }
                carry(unit, hero.held());
                // Whatever his own skills say he casts with. Gathered from the
                // skill blocks rather than listed again in his, so the clip is
                // named once -- a name in two files is two names waiting to
                // disagree.
                for (var skill : settings.skillsFor(hero.name())) {
                    unit.alsoAnimation(skill.castAnim());
                }
                for (var library : hero.animations()) {
                    unit.animationsFrom(library);
                }
            });
        }

        // And what everything looks like in the panel's frame, alive. One block
        // for the whole bestiary — the camera is written in fractions of whatever
        // it is looking at, so it frames a skeleton and a hero each by its own
        // measured height — and a block per creature that wants a different one.
        // No art is named anywhere here: a portrait takes the model, the scale and
        // the libraries from the creature's own block, under the same name.
        var everyone = settings.everyPortrait();
        if (everyone != null) {
            visuals.portraits(portraitLook(everyone));
        }
        for (var portrait : settings.portraits()) {
            visuals.portrait(portrait.name(), portraitLook(portrait));
        }
        visuals.portraitFps(settings.portraitFps());
        // What a dead monster leaves lying about. No chest in the kit, so it is
        // a box in torch colour -- which is what a thing worth walking over to
        // has to be, whatever it is eventually modelled as.
        visuals.unit("Chest", unit -> unit.colour(new java.awt.Color(0xE8A33D)).scale(0.5f));

        // Things in flight are units like any other — they are in the world, so
        // the client draws them without being told anything special, and the
        // simulation turns them so they point the way they are flying. What each
        // one burns like is named beside it and declared just below.
        for (var look : settings.effects()) {
            effect(visuals, look);
        }
        // Then the layers each is drawn from, in the file's order -- which is the
        // order they are laid one over another.
        for (var layer : settings.effectLayers()) {
            visuals.effect(layer.effect(),
                    recipe -> recipe.layer(layerOf(layer, settings.particleFolder())));
        }
        for (var look : settings.projectiles()) {
            arrow(visuals, look.name(), look);
        }
        visuals.effectBudget(settings.effectLights(), settings.effectsPerKind(),
                settings.effectBursts(), settings.effectDistance());
        visuals.skillRings(settings.effectRings());
        visuals.particleBudget(settings.effectParticles());
        visuals.shakeScale(settings.shakeScale());
        visuals.hitFlash(new Visuals.HitFlashLook(settings.hitFlashColour(),
                settings.hitFlashSeconds(), settings.hitFlashStrength()));
        visuals.strikeWithin(settings.strikeWithin());
        // The run's own moments -- a level, the boss down, a floor reached -- and the
        // look each one plays on the hero.
        for (var moment : settings.moments()) {
            visuals.moment(moment.name(), moment.effect(), moment.scale());
        }
        measureLooks(visuals, settings);

        // The floor is black until he walks it. Named rather than given a
        // distance: the radius is the hero's own VisionRange from creatures.ini,
        // which is also what the engine's fog uses to decide whether a monster is
        // on screen — so the ground he uncovers and the things he can see are the
        // same number, and re-tuning one cannot leave the other behind.
        // Whoever is being played: a knight sees a shorter way than an archer, and
        // the floor has to open up around the eyes that are actually there.
        visuals.discoveredBy(settings.playedHero());

        // What it all sounds like — see DungeonSound in dungeon.ini. Handed over
        // whole, like the tiles and the themes: the client raises moments by name
        // and this is the only place that knows what a moment sounds like.
        visuals.sounds(soundsOf(settings));

        // The lettering the menus are set in -- carved Roman capitals, baked from
        // the TTF by BitmapFontBaker. Named in the file rather than here for the
        // same reason every other asset is: a path in Java is a path that needs a
        // rebuild to move. See DungeonMenu in dungeon.ini.
        visuals.menuStyle(new uz.duke.client3d.MenuStyle(
                settings.menuTitleFont(), settings.menuRowFont()));

        // What the panel's edges are painted with -- see DungeonSkin in
        // dungeon.ini. The client knows where a socket goes; this says what its
        // rim is made of.
        visuals.panelSkin(panelSkin(settings));
        visuals.unitBars(unitBars(settings));

        // And what the mouse pointer looks like over each thing -- see
        // DungeonCursor in dungeon.ini. The client knows what is under the
        // pointer; this says what to draw there.
        for (var pointer : settings.cursors()) {
            visuals.pointer(pointer.name(), pointer.image(), pointer.hotX(), pointer.hotY(),
                    pointer.tint());
        }

        // What the dark is worth: whether stone stops sight, how dim a room he
        // has left should be, and what colour nothing is. All of it drawing, and
        // all of it in the file — see DungeonFog in dungeon.ini.
        // Shoving the camera with the cursor, on top of the keys — see
        // DungeonCamera in dungeon.ini.
        visuals.edgeScroll(new EdgeScroll(settings.edgeScrollMargin(),
                settings.edgeScrollSpeedPercent()));

        visuals.fog(new Fog(settings.fogLineOfSight(),
                settings.fogUnseenPercent() / 100f, settings.fogRememberedPercent() / 100f,
                settings.fogVisiblePercent() / 100f, settings.fogSoftenCells(),
                settings.fogOpenPerSecond(), settings.fogTextureSize(), settings.fogTint()));

        // What a caster is seen doing, keyed by the recipe the cast is announced
        // under -- see Visuals.castAnim. A skill that names no gesture is cast
        // exactly as it always was, which is with an effect and a still caster.
        for (var hero : settings.heroes()) {
            for (var skill : settings.skillsFor(hero.name())) {
                visuals.castAnim(skill.look(), skill.castAnim(), skill.castSeconds());
            }
        }

        // Whether the panel may colour the skill pictures. It always did, which is
        // how one white drawing served three states; a painted set cannot take it
        // — see IconLook.
        visuals.iconLook(new uz.duke.client3d.IconLook(settings.hudPaintedSkillIcons()));

        // How the block under the experience bar is drawn -- see DungeonStatBlock. The
        // client knows where its three columns go; the file says how big and what colour.
        var statBlockArt = settings.statBlockArt();
        visuals.statLook(new uz.duke.client3d.StatLook(statBlockArt.figureIcon(),
                statBlockArt.primaryIcon(), statBlockArt.attributeIcon(), statBlockArt.iconShare(),
                statBlockArt.rowGap(), statBlockArt.gapUnderBar(), statBlockArt.figureColumn(),
                statBlockArt.primaryColumn(), statBlockArt.figureRows(),
                statBlockArt.attributeRows(), statBlockArt.figureText(),
                statBlockArt.attributeText(), statBlockArt.primaryText(),
                statBlockArt.labelColour(), statBlockArt.valueColour(),
                statBlockArt.primaryColour(), statBlockArt.attributeColour(),
                statBlockArt.gainColour(), statBlockArt.frameColour(), statBlockArt.figureTint(),
                statBlockArt.primaryTint(), statBlockArt.attributeTint()));

        // Where the light comes from — see DungeonSun. The pitch is what decides
        // whether the floor plan reads as a place with heights in it.
        visuals.sunlight(new uz.duke.client3d.Sunlight(settings.sunPitch(), settings.sunYaw(),
                settings.sunStrengthPercent() / 100f, settings.sunAmbientPercent() / 100f,
                settings.sunColour(), settings.sunAmbientTint()));

        // The three arrowheads that answer a click — see DungeonOrderMark.
        visuals.orderMark(new uz.duke.client3d.OrderMark(
                settings.markStartRadius(), settings.markEndRadius(), settings.markSeconds(),
                settings.markSize(), settings.markWidth(), settings.markHeight(),
                settings.markEasePower(), settings.markFadeFrom(), settings.markSpinDegrees(),
                settings.markBrightness(), settings.markRingRadius(), settings.markBlinks(),
                settings.markMoveColour(), settings.markAttackColour()));

        // How far each skill reaches, so the client can draw it before it is spent
        // — see DungeonSkillRing, and SkillRange for what each shape means.
        visuals.rangeLook(new uz.duke.client3d.RangeLook(
                settings.ringBandWidth(), settings.ringFillAlpha(), settings.ringEdgeAlpha(),
                settings.ringHeight(), settings.ringPulseDepth(), settings.ringPulsePerSecond(),
                settings.ringSegments(), settings.ringAllowColour(), settings.ringDenyColour(),
                settings.ringAreaColour(), settings.ringBrightness()));
        // The file's default hero to begin with, and whoever is actually chosen
        // the moment he is -- see ringsFor.
        ringsFor(visuals, settings, settings.playedHero());

        themes(visuals, settings);

        // The floor is a modular kit, laid out by the client from the same grid
        // the pathfinder uses. Named in dungeon.ini rather than here, so swapping
        // the kit — or dropping back to plain blocks — is an edit, not a rebuild.
        var art = settings.tiles();
        if (art.floor() != null) {
            visuals.tiles(Tileset.create()
                    .floor(art.floor())
                    .wall(art.wall())
                    .corner(art.corner())
                    .stairs(art.stairs())
                    .tileSize(art.tileSize())
                    .wallHeight(art.wallHeight())
                    .wallLift(art.wallLift())
                    .wallShift(art.wallShift()));
        }
        return visuals;
    }
}
