package uz.duke.dungeon;

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
import uz.duke.dungeon.power.ChoosePower;
import uz.duke.dungeon.skill.CastSkill;

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
                    .burst(look.burstParticles(), look.burstSize(), look.burstSeconds());
        });
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
                    .heldTurn(held.pitch(), held.yaw(), held.roll());
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
        Duke3D.launch(Dungeon.create(System.nanoTime(), settings), looks(settings), Shell.create()
                .entry(Shell.Entry.PLAY, "Enter the dungeon")
                .entry(Shell.Entry.SETTINGS)
                .entry(Shell.Entry.QUIT), controls(settings));
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
        // The level-up cards. The client draws them and reports which was taken;
        // which power that is, and what it is worth, is settled in the simulation
        // when the command comes round — the same road a keypress travels.
        keys.onChoose((game, index) -> game.postCommand(new ChoosePower(
                game.getLocalPlayerIndex(), index, offeredId(game))));
        // The played hero's four, not the file's eight. Both heroes cast on Q, W,
        // E and R — a key belongs to a slot rather than to a skill — so binding
        // every skill in the file would have whichever hero was read last deciding
        // what Q asks the player to point at. Which is not a small wrongness: the
        // archer's Q wants a creature and the knight's wants a patch of floor.
        for (var skill : settings.skillsFor(settings.playedHero())) {
            char key = skill.key();
            switch (skill.effect().aim()) {
                case UNIT -> keys.onUnit(key, (game, id) -> game.postCommand(new CastSkill(
                        game.getLocalPlayerIndex(), key, new ObjectId(id), null)));
                case OPEN_GROUND -> keys.onOpenGround(key, (game, spot) -> game.postCommand(
                        new CastSkill(game.getLocalPlayerIndex(), key, null, spot)));
                case SELF -> keys.on(key, game -> game.postCommand(
                        new CastSkill(game.getLocalPlayerIndex(), key)));
            }
        }
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
    private static uz.duke.client3d.SkillRange rangeOf(
            uz.duke.dungeon.skill.Skill skill, float selfRadius) {
        var shape = switch (skill.effect()) {
            case STRIKE -> uz.duke.client3d.SkillRange.Shape.AT_A_CREATURE;
            case AREA_AT_SPOT -> uz.duke.client3d.SkillRange.Shape.AT_A_SPOT;
            case SKILLSHOT -> uz.duke.client3d.SkillRange.Shape.DOWN_A_LANE;
            case DASH -> uz.duke.client3d.SkillRange.Shape.AT_A_SPOT;
            case AREA_DAMAGE -> uz.duke.client3d.SkillRange.Shape.AROUND_HIM;
            // Neither of these reaches past him: one sharpens his sword, the
            // other thickens his skin.
            case EMPOWER, GUARD -> uz.duke.client3d.SkillRange.Shape.ON_HIMSELF;
        };
        float reach = switch (skill.effect()) {
            case STRIKE, AREA_AT_SPOT, SKILLSHOT -> skill.range();
            case DASH -> skill.distance();
            case AREA_DAMAGE -> skill.radius();
            case EMPOWER, GUARD -> selfRadius;
        };
        // What it LEAVES where it lands: a blast's radius, a lane's width. A dash
        // leaves a man, and a circle round a man-sized spot is a second ring saying
        // what the pointer already said -- so it draws none.
        float area = switch (skill.effect()) {
            case AREA_AT_SPOT, SKILLSHOT -> skill.radius();
            default -> 0f;
        };
        return new uz.duke.client3d.SkillRange(skill.key(), shape, reach, area);
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
        keys.onOpenGround('A', (game, spot) -> game.postCommand(
                new uz.duke.rts.message.GameMessage.MoveTo(
                        game.getLocalPlayerIndex(), selected(game), spot)));
        keys.onUnit('S', (game, id) -> game.postCommand(
                new uz.duke.rts.message.GameMessage.AttackObject(
                        game.getLocalPlayerIndex(), selected(game), new ObjectId(id))));
        // Stop is the loudest of the four: drop the walk, drop the target, and
        // start nothing until told otherwise. Two commands because two things are
        // being said -- the engine's own stop, and this game's "and stay stopped".
        keys.on('D', game -> {
            game.postCommand(new uz.duke.rts.message.GameMessage.StopMoving(
                    game.getLocalPlayerIndex(), selected(game)));
            game.postCommand(new uz.duke.dungeon.ai.HoldGround(
                    game.getLocalPlayerIndex(), true));
        });
        // And Guard is how he is let go again: stand where you are, but pick a
        // fight with anything that comes near. It is also the state he is in for
        // most of a run, which is why it has a button of its own to light.
        keys.on('F', game -> game.postCommand(
                new uz.duke.dungeon.ai.HoldGround(game.getLocalPlayerIndex(), false)));
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
     * Which offer the player is answering, read back out of the line the game
     * itself wrote.
     *
     * <p>The client knows the number — it is drawing the screen — but handing it
     * back through the callback would have made a general seam carry one game's
     * field. Reading it here keeps the client's side of the bargain to "the
     * player took the second card", which is all it can honestly claim to know.
     */
    private static int offeredId(uz.duke.game.DukeGame game) {
        var status = game.getSnapshot().status();
        int at = status.indexOf("|offer=");
        if (at < 0) {
            return -1;
        }
        var field = status.substring(at + "|offer=".length());
        int comma = field.indexOf(',');
        try {
            return Integer.parseInt(comma < 0 ? field : field.substring(0, comma));
        } catch (NumberFormatException broken) {
            return -1;
        }
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
        for (var look : settings.projectiles()) {
            arrow(visuals, look.name(), look);
        }
        visuals.effectBudget(settings.effectLights(), settings.effectsPerKind(),
                settings.effectBursts(), settings.effectDistance());

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
        // The played hero's, for the same reason his keys are — see controls. A
        // ring is keyed by the letter, and the archer's Q and the knight's Q are
        // two very different circles.
        for (var skill : settings.skillsFor(settings.playedHero())) {
            visuals.skillRange(rangeOf(skill, settings.ringSelfRadius()));
        }

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
