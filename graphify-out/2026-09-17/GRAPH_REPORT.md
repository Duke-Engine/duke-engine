# Graph Report - duke-engine  (2026-09-17)

## Corpus Check
- 508 files · ~833,905 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 110 file(s) not represented in the graph (top: .bin 31, .gltf 31, .glb 13)

## Summary
- 7299 nodes · 23970 edges · 252 communities (171 shown, 81 thin omitted)
- Extraction: 89% EXTRACTED · 11% INFERRED · 0% AMBIGUOUS · INFERRED: 2716 edges (avg confidence: 0.81)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `3d120bf9`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- asserttrue
- FieldParseTable
- Ini
- DungeonSettings
- GameLogic
- HeroPanel
- com.jme3.scene.Node
- org.junit.jupiter.api.Test
- arraylist
- .creature
- Command
- com.jme3.math.ColorRGBA
- StudioWindow
- DukeRtsApp
- PathGrid
- TerrainSceneTest
- .world
- com.jme3.math.Vector3f
- com.jme3.scene.Spatial
- SkillBook
- .getLogic
- .of
- .getBody
- World
- DungeonMonsterArtTest
- StageDraft
- StoneMenu
- .newSession
- HitFlash
- DungeonGenerator
- DukeGame
- KnightTest
- Coord3D
- .parse
- GameEngine
- StudioWindow.java
- Builder
- What You Must Do When Invoked
- .howToPlay
- Shell
- Hotkeys
- RtsSimulation
- Visuals
- LootBag
- RangeRingsTest
- GeneratedDungeon
- WorldEventTest
- PanelLayoutTest
- Builder
- .showSettingsMenu
- LayeredEffectsTest
- SkillTip
- UnitVisual
- .read
- DiscoveryTest
- HeroBrain
- StudioProject
- ScriptCompiler.java
- ProjectileEffects
- .generate
- ArtLoad
- DungeonEffectLayerTest
- .getOwner
- .findPath
- DungeonRun
- RtsLogic
- PortraitMood
- HeroPortrait
- SkillEffectsTest
- MonsterBrain
- SightTest
- ManaTest
- .generate
- ThemeArt
- GameObject
- MageTest
- DukeRtsApp.java
- Main
- InspectorPanel
- FieldParser
- .getId
- Palette.java
- SkillLookTest
- MonsterSkillTest
- .create
- Reading
- Piece
- LockstepGate
- MonsterSummoningTest
- java.nio.file.Path
- .everySizeTheGameMakes
- ChevronsTest
- .create
- MoveTo
- CursorsTest
- .named
- ThingSystemTest
- HeroPortraitTest
- EffectVisual
- Replay
- Storeys
- .world
- CameraFocus
- DungeonPortraitTest
- ParticleLayer
- TileLayoutTest
- MonsterHealingTest
- .frame
- StoneMenuTest
- HeroAttributesTest
- .newStageSession
- Heard
- .of
- PacketCodec
- LockstepGateTest
- GamePanel
- FloatingNumbers
- Kind
- LockstepScheduler
- PortraitLook
- AttributeRules
- GamePlayer
- AttackObject
- ArrowTest
- SkillRanksTest
- .readShippedFile
- .buildTerrain
- FloatingNumbersTest
- Sounds
- Main.java
- .of
- SkillEffect
- ExperienceModuleTest
- StageDraftTest
- MapPanel.java
- .burstAt
- EffectLook
- NameKeyGenerator
- VisionTest
- ProductionGateTest
- Bow
- DepthTest
- UnitView
- BitmapFontBaker
- .asCloseAsHeCanGet
- ContainModule
- SpecialPowerModule
- .write
- HostTransport
- ModuleCompositionTest
- StageCheckTest
- SoundBank
- BannerPanel
- HeroProgress
- .fromText
- GuardOrderTest
- StaticObstacleTest
- duke-engine — coding rules
- WedgedTest
- graphify reference: extra exports and benchmark
- SelectionBoxTest
- RunMoments
- SunlightTest
- .mood
- NetMessage
- HoldGroundTest
- StoreysTest
- DungeonSoundTest
- HeroBalanceTest
- GameExporter
- shrink_particles.py
- .hits
- PortraitCameraTest
- Doing
- PanelSkinTest
- SoundTouchesNothingTest
- PortraitArt
- MapDef
- GeneratedFloors
- ProductionUpdate
- SupplyModule
- Player
- CollisionTest
- March
- DungeonGeneratorTest
- WatchingTest
- AttackOnTheMoveTest
- DamageModifierTest
- MyToolWindowFactory.kt
- PropsTest
- ScriptsPanel
- .newScenario
- WeaponHoldTest
- Canvas
- Heard
- AutoHealUpdate
- PowerModule
- graphify reference: query, path, explain
- DoingTest
- NoGapsTest
- SkillTipTest
- HarvestTest
- SolidWorldTest
- Data
- GroundPickTest
- .spread
- GameSettingsTest
- .statBlockArt
- AlertTest
- ProjectileLauncherTest
- HarvestUpdate
- TestLogic
- Coord2D
- graphify reference: add a URL and watch a folder
- MonsterBuilder
- PowerTest
- Glyphs
- graphify reference: commit hook and native CLAUDE.md integration
- graphify reference: incremental update and cluster-only
- .mageEverywhere
- CountingLogic
- MyMessageBundle.kt
- DungeonFontTest
- ProductionTest
- .skinKey
- Place
- ThingTemplate
- graphify reference: GitHub clone and cross-repo merge
- fbx_to_glb.py
- SoundSink.java
- ICoord3D
- LayerBuilder
- .enemyRifleman
- CapacityGate
- duke-plugin/gradlew
- Data
- gradlew
- duke-plugin/build.gradle.kts
- duke-plugin/settings.gradle.kts
- .claude/CLAUDE.md
- extraction-spec.md

## God Nodes (most connected - your core abstractions)
1. `DungeonSettings` - 502 edges
2. `GameObject` - 421 edges
3. `Coord3D` - 408 edges
4. `DukeGame` - 259 edges
5. `DukeRtsApp` - 219 edges
6. `GameLogic` - 148 edges
7. `HeroPanel` - 147 edges
8. `Visuals` - 134 edges
9. `ObjectId` - 131 edges
10. `PathGrid` - 125 edges

## Surprising Connections (you probably didn't know these)
- `Module boundaries (the most important rule)` --references--> `Command`  [INFERRED]
  CLAUDE.md → core/src/main/java/uz/duke/core/message/Command.java
- `Module boundaries (the most important rule)` --references--> `PacketCodec`  [INFERRED]
  CLAUDE.md → core/src/main/java/uz/duke/core/network/PacketCodec.java
- `Module boundaries (the most important rule)` --references--> `WeaponFired`  [INFERRED]
  CLAUDE.md → rts/src/main/java/uz/duke/rts/event/WeaponFired.java
- `Module boundaries (the most important rule)` --references--> `MoveTo`  [INFERRED]
  CLAUDE.md → rts/src/main/java/uz/duke/rts/message/GameMessage.java
- `Module boundaries (the most important rule)` --references--> `DamageModifier`  [INFERRED]
  CLAUDE.md → rts/src/main/java/uz/duke/rts/module/DamageModifier.java

## Import Cycles
- None detected.

## Communities (252 total, 81 thin omitted)

### Community 0 - "asserttrue"
Cohesion: 0.04
Nodes (31): animcomposer, animtrack, assertarrayequals, assertequals, assertfalse, assertinstanceof, assertnotequals, assertnotnull (+23 more)

### Community 1 - "FieldParseTable"
Cohesion: 0.03
Nodes (44): FieldParseTable, Armor, Builder, DamageType, ARMOR_PIERCING, EXPLOSION, FLAME, NORMAL (+36 more)

### Community 3 - "DungeonSettings"
Cohesion: 0.03
Nodes (9): BarStep, CursorLook, DungeonSettings, MomentBuilder, PropBuilder, SoundBuilder, ThemeBuilder, TileArt (+1 more)

### Community 4 - "GameLogic"
Cohesion: 0.03
Nodes (23): Module boundaries (the most important rule), Override, ObjectDied, WorldEvent, GameLogic, CommandHandler, FunctionalInterface, Override (+15 more)

### Community 5 - "HeroPanel"
Cohesion: 0.05
Nodes (11): Block, HeroPanel, ItemReading, ItemSlot, Align, OrderButton, OrderReading, Placing (+3 more)

### Community 6 - "com.jme3.scene.Node"
Cohesion: 0.05
Nodes (44): bifunction, bitmaptext, blendmode, bufferutils, AttackFlash, Chevrons, Mark, Glow (+36 more)

### Community 7 - "org.junit.jupiter.api.Test"
Cohesion: 0.03
Nodes (11): HeroPanelTest, MinimapProjectionTest, SkillTipTest, Coord3DTest, ThemesTest, DungeonTilesTest, LootTest, SkillTest (+3 more)

### Community 8 - "arraylist"
Cohesion: 0.05
Nodes (28): arraydeque, arraylist, arrays, assertiterableequals, biconsumer, boundingsphere, collections, concurrentlinkedqueue (+20 more)

### Community 9 - ".creature"
Cohesion: 0.10
Nodes (6): Override, FunctionalInterface, PartitionFilter, Override, PartitionManager, PartitionManagerTest

### Community 10 - "Command"
Cohesion: 0.07
Nodes (14): Command, CommandPacket, FrameChecksum, CommandLogic, Override, Override, MovementLogic, Override (+6 more)

### Community 11 - "com.jme3.math.ColorRGBA"
Cohesion: 0.05
Nodes (11): IconLook, Sunlight, Step, UnitBarLook, UnitBarReading, Bar, Lettering, Standing (+3 more)

### Community 12 - "StudioWindow"
Cohesion: 0.11
Nodes (5): ActionListener, javax.swing.JMenuItem, MapPanel, JComboBox, StudioWindow

### Community 13 - "DukeRtsApp"
Cohesion: 0.05
Nodes (5): DukeRtsApp, Dying, Preferences, Size, Vector4f

### Community 14 - "PathGrid"
Cohesion: 0.09
Nodes (4): Placement, TileLayout, Pathfinder, PathGrid

### Community 15 - "TerrainSceneTest"
Cohesion: 0.10
Nodes (7): ColourWatchingTiles, Override, Spatial, RailedStair, StubTiles, StubTilesWithFace, TerrainSceneTest

### Community 16 - ".world"
Cohesion: 0.10
Nodes (11): Box, Cylinder, Override, Sphere, FlatWorldChecksumTest, Override, TestLogic, ClimbingTest (+3 more)

### Community 17 - "com.jme3.math.Vector3f"
Cohesion: 0.11
Nodes (8): EffectLayer, LayeredEffects, Moment, Place, Playing, Riding, com.jme3.math.Vector3f, com.jme3.renderer.Camera

### Community 18 - "com.jme3.scene.Spatial"
Cohesion: 0.05
Nodes (7): BoundingBox, Standing, TerrainScene, Tileset, TileSource, Override, com.jme3.scene.Spatial

### Community 19 - "SkillBook"
Cohesion: 0.06
Nodes (8): Skill, CastMark, Override, SkillBook, Skills, Words, SkillTip, Words

### Community 23 - "World"
Cohesion: 0.10
Nodes (3): World, KeepingDistance, Summoning

### Community 24 - "DungeonMonsterArtTest"
Cohesion: 0.05
Nodes (11): AnimationLibrary, com.jme3.anim.Armature, com.jme3.anim.Joint, Color, MonsterKind, Color, MonsterLook, DungeonMonsterArtTest (+3 more)

### Community 25 - "StageDraft"
Cohesion: 0.06
Nodes (7): javax.swing.KeyStroke, JRootPane, StageDraft, BuilderWindow, Wanted, StageCanvas, WorldBuilderMain

### Community 26 - "StoneMenu"
Cohesion: 0.11
Nodes (6): Align, StoneCraft, Buttons, Hit, StoneMenu, com.jme3.math.Vector2f

### Community 27 - ".newSession"
Cohesion: 0.07
Nodes (6): Session, UpgradeSkill, ControlsTest, HeroChoiceTest, DepthTest, DungeonRunTest

### Community 28 - "HitFlash"
Cohesion: 0.18
Nodes (5): Flash, HitFlash, HitFlashTest, com.jme3.material.MatParamOverride, vartype

### Community 29 - "DungeonGenerator"
Cohesion: 0.08
Nodes (4): PropKind, DeterministicRng, DungeonGenerator, Room

### Community 30 - "DukeGame"
Cohesion: 0.05
Nodes (8): BuildOption, DukeGame, IntConsumer, Logger, ServerSocket, SkirmishAssembler, MultiplayerSyncTest, ThreePlayerSyncTest

### Community 32 - "Coord3D"
Cohesion: 0.07
Nodes (25): atomicinteger, atomicreference, collectors, color, Coord3D, Path, Relationship, ALLIES (+17 more)

### Community 33 - ".parse"
Cohesion: 0.06
Nodes (4): ContentTest, DungeonSettingsTest, AttributesTest, HeroStatusTest

### Community 34 - "GameEngine"
Cohesion: 0.07
Nodes (8): GameClient, Override, GameEngine, Override, SubsystemList, GameEngineTest, Override, TestEngine

### Community 35 - "StudioWindow.java"
Cohesion: 0.06
Nodes (48): borderfactory, borderlayout, boxlayout, dimension, filenameextensionfilter, GameWindow, gridbagconstraints, gridbaglayout (+40 more)

### Community 37 - "What You Must Do When Invoked"
Cohesion: 0.08
Nodes (24): For /graphify add and --watch, For /graphify query, For the commit hook and native CLAUDE.md integration, For --update and --cluster-only, /graphify, Honesty Rules, Interpreter guard for subcommands, Part A - Structural extraction for code files (+16 more)

### Community 38 - ".howToPlay"
Cohesion: 0.09
Nodes (4): Logger, Listed, Stages, StageChoiceTest

### Community 39 - "Shell"
Cohesion: 0.11
Nodes (11): Entry, HOST_LAN, JOIN_LAN, PLAY, QUIT, SETTINGS, SKIRMISH, Option (+3 more)

### Community 40 - "Hotkeys"
Cohesion: 0.08
Nodes (11): Aim, GROUND, NOW, OPEN_GROUND, UNIT, UNIT_OR_GROUND, Aimed, Binding (+3 more)

### Community 41 - "RtsSimulation"
Cohesion: 0.06
Nodes (17): Upgrade, Override, RtsSimulation, GameSnapshot, Override, TestLogic, WeaponFiredTest, ContainTest (+9 more)

### Community 42 - "Visuals"
Cohesion: 0.03
Nodes (19): BitmapFont, Override, EdgeScroll, HitNumbers, MenuStyle, Job, Kind, ANIMATIONS (+11 more)

### Community 43 - "LootBag"
Cohesion: 0.08
Nodes (13): LootBuilder, Loot, LootBag, Override, LootKind, ARMOUR, ATTACK, ATTRIBUTE (+5 more)

### Community 44 - "RangeRingsTest"
Cohesion: 0.11
Nodes (10): Shape, AROUND_HIM, AT_A_CREATURE, AT_A_SPOT, DOWN_A_LANE, ON_HIMSELF, SkillRange, RangeRingsTest (+2 more)

### Community 45 - "GeneratedDungeon"
Cohesion: 0.11
Nodes (12): IniException, GeneratedDungeon, Link, Monster, Placement, Prop, Override, StageFloors (+4 more)

### Community 46 - "WorldEventTest"
Cohesion: 0.24
Nodes (4): Override, TestLogic, WorldEventTest, WreckOnDeath

### Community 47 - "PanelLayoutTest"
Cohesion: 0.12
Nodes (5): BitmapFont, ColorRGBA, PanelLayoutTest, Span, com.jme3.bounding.BoundingBox

### Community 48 - "Builder"
Cohesion: 0.10
Nodes (6): Builder, GeometryType, BOX, CYLINDER, SPHERE, ModuleEntry

### Community 49 - ".showSettingsMenu"
Cohesion: 0.09
Nodes (10): Screen, LOADING, MENU, PAUSED, PLAYING, SETTINGS, GameSettings, Preferences (+2 more)

### Community 50 - "LayeredEffectsTest"
Cohesion: 0.19
Nodes (3): LayeredEffectsTest, Rig, Type

### Community 51 - "SkillTip"
Cohesion: 0.15
Nodes (3): Shade, Align, SkillTip

### Community 52 - "UnitVisual"
Cohesion: 0.06
Nodes (8): Color, Texture, KitTiles, AnimationSource, Carried, Color, UnitVisual, SkinningControl

### Community 53 - ".read"
Cohesion: 0.16
Nodes (3): UnitBarReadingTest, Screen, UnitBarsTest

### Community 54 - "DiscoveryTest"
Cohesion: 0.08
Nodes (4): bitset, Discovery, Fog, DiscoveryTest

### Community 55 - "HeroBrain"
Cohesion: 0.10
Nodes (4): Chasing, HeroBrain, Override, WayAhead

### Community 56 - "StudioProject"
Cohesion: 0.08
Nodes (13): playerdef, RohanVsMordor, GameFactory, FactionDef, Placement, PlayerDef, ScriptDef, StartingUnit (+5 more)

### Community 57 - "ScriptCompiler.java"
Cohesion: 0.11
Nodes (14): bytearrayoutputstream, diagnosticcollector, fileobject, forwardingjavafilemanager, javafileobject, javax.tools.SimpleJavaFileObject, outputstream, standardjavafilemanager (+6 more)

### Community 58 - "ProjectileEffects"
Cohesion: 0.12
Nodes (7): LightPool, Burst, Color, ProjectileEffects, Trail, com.jme3.effect.ParticleEmitter, com.jme3.light.PointLight

### Community 59 - ".generate"
Cohesion: 0.12
Nodes (3): BossGuard, DungeonShapeTest, MonsterPlacementTest

### Community 61 - "DungeonEffectLayerTest"
Cohesion: 0.12
Nodes (3): EffectLayerArt, MomentArt, DungeonEffectLayerTest

### Community 62 - ".getOwner"
Cohesion: 0.06
Nodes (12): ArrowUpdate, Override, Override, Override, Override, Override, Override, Override (+4 more)

### Community 63 - ".findPath"
Cohesion: 0.12
Nodes (3): LevelsTest, PathfinderTest, PathSmoothingTest

### Community 64 - "DungeonRun"
Cohesion: 0.06
Nodes (8): Orders, Arena, DungeonRun, State, DEAD, RUNNING, WON, Floors

### Community 65 - "RtsLogic"
Cohesion: 0.08
Nodes (10): Override, RtsClient, Override, RtsGameEngine, DefeatListener, IntervalCallback, FunctionalInterface, Logger (+2 more)

### Community 66 - "PortraitMood"
Cohesion: 0.18
Nodes (6): State, REMEMBERED, UNSEEN, VISIBLE, Lengths, PortraitMood

### Community 67 - "HeroPortrait"
Cohesion: 0.11
Nodes (8): Bodies, HeroPortrait, Clips, com.jme3.anim.AnimComposer, com.jme3.renderer.RenderManager, com.jme3.renderer.ViewPort, com.jme3.texture.FrameBuffer, com.jme3.texture.Texture2D

### Community 68 - "SkillEffectsTest"
Cohesion: 0.07
Nodes (8): Cast, Color, Vector3f, Ring, RingWanted, SkillEffects, Scene, SkillEffectsTest

### Community 69 - "MonsterBrain"
Cohesion: 0.14
Nodes (4): Override, MonsterBrain, SightLine, Mending

### Community 72 - ".generate"
Cohesion: 0.13
Nodes (3): Layout, Example, ExampleStage

### Community 73 - "ThemeArt"
Cohesion: 0.10
Nodes (11): Color, Standing, ThemeArt, ThemeMonster, Tone, Chosen, Themes, WhenExhausted (+3 more)

### Community 74 - "GameObject"
Cohesion: 0.05
Nodes (5): Override, GameObject, Shot, Override, Override

### Community 75 - "MageTest"
Cohesion: 0.14
Nodes (3): Arena, Data, MageTest

### Community 76 - "DukeRtsApp.java"
Cohesion: 0.08
Nodes (23): ambientlight, analoglistener, animcontrol, audiodata, billboardcontrol, blendableaction, AudioSink, Node (+15 more)

### Community 77 - "Main"
Cohesion: 0.10
Nodes (6): AttributeLine, HeroBuilder, Held, Builder, Logger, Main

### Community 78 - "InspectorPanel"
Cohesion: 0.10
Nodes (18): Container, java.awt.Component, javax.swing.JCheckBox, CapabilityType, ATTACK, AUTO_HEAL, CAPACITY_GATE, EXPERIENCE (+10 more)

### Community 79 - "FieldParser"
Cohesion: 0.14
Nodes (4): FieldParser, FunctionalInterface, ObjIntConsumer, ObjIntConsumer

### Community 80 - ".getId"
Cohesion: 0.06
Nodes (7): Override, UnitScript, Broken, Override, ScriptModuleTest, Walker, SpecialPowerTest

### Community 81 - "Palette.java"
Cohesion: 0.14
Nodes (12): defaultcomboboxmodel, flowlayout, javax.swing.JComboBox, Override, Palette, Tool, toString(), What (+4 more)

### Community 84 - ".create"
Cohesion: 0.11
Nodes (5): DukeGameTest, GameCommandTest, Shout, ReplayRoundTripTest, StatusLineTest

### Community 85 - "Reading"
Cohesion: 0.11
Nodes (9): CostReading, RankReading, Reading, SkillReading, Stat, State, COOLING, LOCKED (+1 more)

### Community 86 - "Piece"
Cohesion: 0.25
Nodes (8): Piece, CAP, CORNER, FLOOR, LEDGE, ROCK_FACE, STAIR, WALL

### Community 87 - "LockstepGate"
Cohesion: 0.06
Nodes (11): Desync, Override, LockstepGate, SessionState, DESYNCED, DISCONNECTED, RUNNING, Transport (+3 more)

### Community 88 - "MonsterSummoningTest"
Cohesion: 0.19
Nodes (4): Circle, Floor, MonsterSummoningTest, Rising

### Community 89 - "java.nio.file.Path"
Cohesion: 0.09
Nodes (19): bufferedimage, com.google.gson.Gson, file, files, gsonbuilder, ioexception, java.nio.file.Path, properties (+11 more)

### Community 91 - "ChevronsTest"
Cohesion: 0.06
Nodes (14): OrderMark, Step, Kind, ATTACK, ATTACK_MOVE, MOVE, Marker, OrderMarkers (+6 more)

### Community 92 - ".create"
Cohesion: 0.10
Nodes (5): PreloadTest, ProjectileEffectsTest, Scene, Stub, ThemedLookTest

### Community 93 - "MoveTo"
Cohesion: 0.16
Nodes (4): DungeonCombatTest, Fight, DungeonTest, MoveTo

### Community 94 - "CursorsTest"
Cohesion: 0.10
Nodes (8): Cursors, Look, Over, CursorsTest, JmeCursor, com.jme3.cursors.plugins.JmeCursor, com.jme3.input.InputManager, com.jme3.texture.Image

### Community 95 - ".named"
Cohesion: 0.13
Nodes (3): Override, StatusUpdateTest, TestLogic

### Community 96 - "ThingSystemTest"
Cohesion: 0.16
Nodes (5): Data, Override, MoverUpdate, TestLogic, ThingSystemTest

### Community 99 - "Replay"
Cohesion: 0.20
Nodes (4): Replay, Override, ReplayMismatch, ReplayTest

### Community 100 - "Storeys"
Cohesion: 0.17
Nodes (3): Corridor, Result, Storeys

### Community 101 - ".world"
Cohesion: 0.12
Nodes (4): Fight, Data, MonsterKindsTest, DungeonFogTest

### Community 106 - "MonsterHealingTest"
Cohesion: 0.32
Nodes (3): Hurt, MonsterHealingTest, Ward

### Community 107 - ".frame"
Cohesion: 0.21
Nodes (3): NineSlice, Rect, NineSliceTest

### Community 108 - "StoneMenuTest"
Cohesion: 0.15
Nodes (9): Action, Choice, Level, Opens, Row, Words, Bar, StoneMenuTest (+1 more)

### Community 109 - "HeroAttributesTest"
Cohesion: 0.13
Nodes (5): Arena, HeroAttributesTest, Played, Fight, HeroProgressTest

### Community 111 - "Heard"
Cohesion: 0.19
Nodes (3): Heard, Override, SoundsTest

### Community 112 - ".of"
Cohesion: 0.14
Nodes (8): BlockParser, FunctionalInterface, IniTest, Side, America, China, GLA, Weapon

### Community 113 - "PacketCodec"
Cohesion: 0.14
Nodes (4): NetFraming, PacketCodec, Override, ReplayRecorder

### Community 114 - "LockstepGateTest"
Cohesion: 0.17
Nodes (7): Override, SessionHalted, Override, LockstepGateTest, Peer, PeerLogic, Switchboard

### Community 115 - "GamePanel"
Cohesion: 0.18
Nodes (5): GamePanel, Override, WorldSnapshot, java.awt.Graphics2D, java.awt.Rectangle

### Community 117 - "Kind"
Cohesion: 0.11
Nodes (6): Renderer, Override, RenderingGameClient, Override, Kind, RtsKinds

### Community 118 - "LockstepScheduler"
Cohesion: 0.18
Nodes (3): Override, LockstepScheduler, LockstepSchedulerTest

### Community 119 - "PortraitLook"
Cohesion: 0.16
Nodes (7): PortraitLook, State, CALM, DEAD, FIGHT, HURT, LEVEL_UP

### Community 120 - "AttributeRules"
Cohesion: 0.07
Nodes (12): Attribute, AttributeRules, Attributes, Override, Data, HeroAttributes, HeroBuild, Found (+4 more)

### Community 121 - "GamePlayer"
Cohesion: 0.11
Nodes (5): Placement, Placed, Spawner, GamePlayer, java.awt.Color

### Community 122 - "AttackObject"
Cohesion: 0.14
Nodes (5): AttackMoveTest, Report, StuckDiagnosisTest, AttackObject, CombatTest

### Community 127 - "FloatingNumbersTest"
Cohesion: 0.15
Nodes (6): Change, Death, HealthWatch, FloatingNumbersTest, Screen, HealthWatchTest

### Community 128 - "Sounds"
Cohesion: 0.16
Nodes (8): Channel, EFFECTS, MUSIC, UI, VOICE, Sounds, SoundSink, java.util.random.RandomGenerator

### Community 129 - "Main.java"
Cohesion: 0.20
Nodes (3): appsettings, Duke3D, HeroLook

### Community 130 - ".of"
Cohesion: 0.16
Nodes (6): KindTest, AsciiRenderer, Override, AsciiRendererTest, Override, TestLogic

### Community 131 - "SkillEffect"
Cohesion: 0.10
Nodes (18): SkillBuilder, Aim, GROUND, OPEN_GROUND, SELF, UNIT, SkillEffect, AREA_AT_SPOT (+10 more)

### Community 132 - "ExperienceModuleTest"
Cohesion: 0.20
Nodes (4): Data, DataBuilder, Rank, ExperienceModuleTest

### Community 134 - "MapPanel.java"
Cohesion: 0.16
Nodes (13): basicstroke, comparator, graphics2d, java.awt.event.MouseAdapter, java.awt.event.MouseEvent, java.awt.event.MouseWheelEvent, java.awt.Graphics, java.awt.Point (+5 more)

### Community 135 - ".burstAt"
Cohesion: 0.19
Nodes (4): Blow, Gone, Landing, LandingTest

### Community 136 - "EffectLook"
Cohesion: 0.11
Nodes (7): ArrowLook, EffectBuilder, EffectLook, Color, ProjectileBuilder, SkinBuilder, SkinLook

### Community 137 - "NameKeyGenerator"
Cohesion: 0.21
Nodes (4): Override, NameKeyGenerator, NameKeyType, NameKeyGeneratorTest

### Community 138 - "VisionTest"
Cohesion: 0.26
Nodes (3): Override, TestLogic, VisionTest

### Community 139 - "ProductionGateTest"
Cohesion: 0.17
Nodes (6): ProductionGate, Override, TestLogic, Override, Permit, ProductionGateTest

### Community 140 - "Bow"
Cohesion: 0.24
Nodes (4): Bow, Data, DataBuilder, Override

### Community 141 - "DepthTest"
Cohesion: 0.30
Nodes (3): DepthTest, Override, TestLogic

### Community 142 - "UnitView"
Cohesion: 0.10
Nodes (4): UnitNode, GameSounds, UnitView, PowerGrid

### Community 143 - "BitmapFontBaker"
Cohesion: 0.25
Nodes (7): Baked, BitmapFontBaker, Glyph, Graphics2D, FontMetrics, java.awt.Font, java.awt.font.FontRenderContext

### Community 145 - "ContainModule"
Cohesion: 0.24
Nodes (3): ContainModule, Data, DataBuilder

### Community 146 - "SpecialPowerModule"
Cohesion: 0.24
Nodes (4): Data, DataBuilder, Override, SpecialPowerModule

### Community 148 - "HostTransport"
Cohesion: 0.15
Nodes (12): bufferedreader, bufferedwriter, concurrenthashmap, copyonwritearraylist, Arrival, HostTransport, Override, Link (+4 more)

### Community 149 - "ModuleCompositionTest"
Cohesion: 0.22
Nodes (4): Override, Legs, ModuleCompositionTest, Ticker

### Community 151 - "SoundBank"
Cohesion: 0.20
Nodes (3): Builder, Cue, SoundBank

### Community 152 - "BannerPanel"
Cohesion: 0.13
Nodes (6): BannerPanel, PanelSkin, Piece, BannerPanelTest, BitmapText, Screen

### Community 153 - "HeroProgress"
Cohesion: 0.14
Nodes (3): HeroProgress, Levelling, LevellingTest

### Community 154 - ".fromText"
Cohesion: 0.15
Nodes (4): FogMap, ColorRGBA, FogMapTest, MapLoaderTest

### Community 156 - "StaticObstacleTest"
Cohesion: 0.31
Nodes (3): Override, StaticObstacleTest, TestLogic

### Community 157 - "duke-engine — coding rules"
Cohesion: 0.22
Nodes (8): Assets, Before claiming "done", Comment discipline, Design principles, duke-engine — coding rules, graphify, Java 25 idioms (non-negotiable), Porting philosophy

### Community 158 - "WedgedTest"
Cohesion: 0.30
Nodes (3): Queue, Watch, WedgedTest

### Community 159 - "graphify reference: extra exports and benchmark"
Cohesion: 0.22
Nodes (8): graphify reference: extra exports and benchmark, Step 6b - Wiki (only if --wiki flag), Step 7 - Neo4j export (only if --neo4j or --neo4j-push flag), Step 7a - FalkorDB export (only if --falkordb or --falkordb-push flag), Step 7b - SVG export (only if --svg flag), Step 7c - GraphML export (only if --graphml flag), Step 7d - MCP server (only if --mcp flag), Step 8 - Token reduction benchmark (only if total_words > 5000)

### Community 160 - "SelectionBoxTest"
Cohesion: 0.24
Nodes (3): Candidate, SelectionBox, SelectionBoxTest

### Community 161 - "RunMoments"
Cohesion: 0.33
Nodes (3): Moment, RunMoments, RunMomentsTest

### Community 165 - "NetMessage"
Cohesion: 0.14
Nodes (10): BooleanSupplier, Override, LoopbackTransport, NetMessage, PeerLeft, Override, SocketTransport, org.junit.jupiter.api.Timeout (+2 more)

### Community 166 - "HoldGroundTest"
Cohesion: 0.21
Nodes (3): HoldGround, HoldGroundTest, Standoff

### Community 170 - "GameExporter"
Cohesion: 0.19
Nodes (4): graphify reference: transcribe video and audio, Step 2.5 - Transcribe video / audio files (only if video files detected), ExportExample, GameExporter

### Community 171 - "shrink_particles.py"
Cohesion: 0.16
Nodes (10): argparse, main(), Bring Kenney's Particle Pack into the game at a size a laptop can afford. The…, shrink(), The two gradients a column of light is drawn from: a white-hot core and a soft…, numpy, os, pathlib (+2 more)

### Community 174 - "Doing"
Cohesion: 0.29
Nodes (6): Doing, FIGHTING, GUARDING, STANDING, WALKING, of()

### Community 176 - "SoundTouchesNothingTest"
Cohesion: 0.29
Nodes (4): Override, Vector3f, Recorder, SoundTouchesNothingTest

### Community 178 - "MapDef"
Cohesion: 0.16
Nodes (7): Box, IconSheets, Sheet, imageio, java.awt.image.BufferedImage, MapImporter, MapDef

### Community 181 - "SupplyModule"
Cohesion: 0.36
Nodes (3): Data, DataBuilder, SupplyModule

### Community 182 - "Player"
Cohesion: 0.08
Nodes (8): Player, FunctionalInterface, Override, PlayerFactory, PlayerList, PlayerListTest, ScoredPlayer, RtsModules

### Community 183 - "CollisionTest"
Cohesion: 0.37
Nodes (3): CollisionTest, Override, TestLogic

### Community 187 - "AttackOnTheMoveTest"
Cohesion: 0.24
Nodes (3): AttackOnTheMoveTest, CombatLogic, Override

### Community 188 - "DamageModifierTest"
Cohesion: 0.37
Nodes (3): DamageModifierTest, Override, Sharpened

### Community 189 - "MyToolWindowFactory.kt"
Cohesion: 0.27
Nodes (8): contentfactory, MyToolWindow, MyToolWindowFactory, jblabel, JBPanel, Project, ToolWindow, ToolWindowFactory

### Community 192 - ".newScenario"
Cohesion: 0.29
Nodes (3): BattleLogic, DeterminismIntegrationTest, Override

### Community 193 - "WeaponHoldTest"
Cohesion: 0.36
Nodes (3): Busy, Override, WeaponHoldTest

### Community 195 - "Heard"
Cohesion: 0.38
Nodes (3): Heard, Override, SelectedCardSoundTest

### Community 196 - "AutoHealUpdate"
Cohesion: 0.38
Nodes (4): AutoHealUpdate, Data, DataBuilder, Override

### Community 197 - "PowerModule"
Cohesion: 0.38
Nodes (3): Data, DataBuilder, PowerModule

### Community 198 - "graphify reference: query, path, explain"
Cohesion: 0.33
Nodes (5): For /graphify explain, For /graphify path, graphify reference: query, path, explain, Step 0 — Constrained query expansion (REQUIRED before traversal), Step 1 — Traversal

### Community 202 - "HarvestTest"
Cohesion: 0.25
Nodes (3): HarvestTest, Override, TestLogic

### Community 203 - "SolidWorldTest"
Cohesion: 0.36
Nodes (3): Override, SolidWorldTest, TestLogic

### Community 206 - ".spread"
Cohesion: 0.31
Nodes (3): Formation, Spot, FormationTest

### Community 210 - "ProjectileLauncherTest"
Cohesion: 0.28
Nodes (4): Override, ProjectileLauncherTest, Quiver, Sharpened

### Community 211 - "HarvestUpdate"
Cohesion: 0.60
Nodes (3): Data, DataBuilder, HarvestUpdate

### Community 214 - "graphify reference: add a URL and watch a folder"
Cohesion: 0.50
Nodes (3): For /graphify add, For --watch, graphify reference: add a URL and watch a folder

### Community 215 - "MonsterBuilder"
Cohesion: 0.22
Nodes (3): CursorBuilder, MonsterBuilder, ThemeMonsterBuilder

### Community 216 - "PowerTest"
Cohesion: 0.29
Nodes (3): Override, PowerTest, TestLogic

### Community 218 - "graphify reference: commit hook and native CLAUDE.md integration"
Cohesion: 0.50
Nodes (3): For git commit hook, For native CLAUDE.md integration, graphify reference: commit hook and native CLAUDE.md integration

### Community 219 - "graphify reference: incremental update and cluster-only"
Cohesion: 0.50
Nodes (3): For --cluster-only, For --update (incremental re-extraction), graphify reference: incremental update and cluster-only

### Community 222 - "CountingLogic"
Cohesion: 0.43
Nodes (3): CountingLogic, GameLogicTest, Override

### Community 223 - "MyMessageBundle.kt"
Cohesion: 0.29
Nodes (4): MyMessageBundle, dynamicbundle, nls, propertykey

### Community 225 - "ProductionTest"
Cohesion: 0.24
Nodes (3): Override, ProductionTest, TestLogic

### Community 227 - "Place"
Cohesion: 0.24
Nodes (3): Surroundings, Override, Place

### Community 228 - "ThingTemplate"
Cohesion: 0.04
Nodes (26): ActiveBody, Data, DataBuilder, Override, Locomotor, Builder, DataParser, FunctionalInterface (+18 more)

### Community 230 - "fbx_to_glb.py"
Cohesion: 0.40
Nodes (3): bpy, Turns an .fbx of one animation into a .glb the game can read. ./gradlew…, sys

### Community 231 - "SoundSink.java"
Cohesion: 0.60
Nodes (4): Override, music(), musicGain(), play()

### Community 236 - "duke-plugin/gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 238 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

## Knowledge Gaps
- **161 isolated node(s):** `graphify`, `Usage`, `What graphify is for`, `Step 0 - GitHub repos and multi-path merge (only if a URL or several paths)`, `Step 1 - Ensure graphify is installed` (+156 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 839 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **81 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `DungeonSettings` connect `DungeonSettings` to `asserttrue`, `FieldParseTable`, `org.junit.jupiter.api.Test`, `arraylist`, `.creature`, `SkillBook`, `.getLogic`, `.of`, `World`, `DungeonMonsterArtTest`, `StageDraft`, `.newSession`, `DungeonGenerator`, `KnightTest`, `Coord3D`, `.parse`, `StudioWindow.java`, `.howToPlay`, `Hotkeys`, `LootBag`, `GeneratedDungeon`, `Builder`, `HeroBrain`, `.generate`, `DungeonEffectLayerTest`, `DungeonRun`, `MonsterBrain`, `SightTest`, `ManaTest`, `.generate`, `ThemeArt`, `MageTest`, `Main`, `FieldParser`, `Palette.java`, `SkillLookTest`, `MonsterSkillTest`, `MonsterSummoningTest`, `java.nio.file.Path`, `.everySizeTheGameMakes`, `MoveTo`, `Storeys`, `.world`, `DungeonPortraitTest`, `MonsterHealingTest`, `HeroAttributesTest`, `.newStageSession`, `GamePlayer`, `AttackObject`, `ArrowTest`, `.readShippedFile`, `Main.java`, `SkillEffect`, `StageDraftTest`, `EffectLook`, `Bow`, `.write`, `StageCheckTest`, `HeroProgress`, `GuardOrderTest`, `WedgedTest`, `HoldGroundTest`, `StoreysTest`, `DungeonSoundTest`, `HeroBalanceTest`, `PortraitArt`, `GeneratedFloors`, `DungeonGeneratorTest`, `WatchingTest`, `PropsTest`, `DoingTest`, `SkillTipTest`, `.statBlockArt`, `AlertTest`, `MonsterBuilder`, `.validate`, `.mageEverywhere`, `ThingTemplate`, `LayerBuilder`?**
  _High betweenness centrality (0.138) - this node is a cross-community bridge._
- **Why does `DukeRtsApp` connect `DukeRtsApp` to `HeroPanel`, `com.jme3.scene.Node`, `.burstAt`, `arraylist`, `com.jme3.math.ColorRGBA`, `UnitView`, `PathGrid`, `com.jme3.math.Vector3f`, `com.jme3.scene.Spatial`, `BannerPanel`, `StoneMenu`, `.fromText`, `HitFlash`, `DukeGame`, `SelectionBoxTest`, `RunMoments`, `.show`, `Shell`, `Hotkeys`, `Visuals`, `.hits`, `.showSettingsMenu`, `UnitVisual`, `DiscoveryTest`, `ProjectileEffects`, `ArtLoad`, `HeroPortrait`, `SkillEffectsTest`, `DukeRtsApp.java`, `GroundPickTest`, `ChevronsTest`, `CursorsTest`, `.skinKey`, `Place`, `CameraFocus`, `GamePanel`, `FloatingNumbers`, `.buildTerrain`, `FloatingNumbersTest`?**
  _High betweenness centrality (0.081) - this node is a cross-community bridge._
- **Why does `Coord3D` connect `Coord3D` to `asserttrue`, `FieldParseTable`, `.of`, `GameLogic`, `com.jme3.scene.Node`, `org.junit.jupiter.api.Test`, `arraylist`, `.creature`, `Command`, `VisionTest`, `DukeRtsApp`, `UnitView`, `PathGrid`, `.asCloseAsHeCanGet`, `.world`, `ContainModule`, `SkillBook`, `.getLogic`, `SpecialPowerModule`, `DepthTest`, `World`, `.fromText`, `GuardOrderTest`, `StaticObstacleTest`, `WedgedTest`, `DukeGame`, `NetMessage`, `HoldGroundTest`, `Hotkeys`, `RtsSimulation`, `RangeRingsTest`, `WorldEventTest`, `ProductionUpdate`, `HeroBrain`, `CollisionTest`, `March`, `StudioProject`, `AttackOnTheMoveTest`, `DamageModifierTest`, `.getOwner`, `.findPath`, `DungeonRun`, `.newScenario`, `WeaponHoldTest`, `SkillEffectsTest`, `MonsterBrain`, `SightTest`, `DoingTest`, `GameObject`, `MageTest`, `DukeRtsApp.java`, `SolidWorldTest`, `.getId`, `ProjectileLauncherTest`, `MonsterSkillTest`, `.create`, `MonsterSummoningTest`, `ChevronsTest`, `MoveTo`, `.named`, `ThingSystemTest`, `Replay`, `ThingTemplate`, `.world`, `HeroAttributesTest`, `LockstepGateTest`, `GamePanel`, `LockstepScheduler`, `GamePlayer`, `AttackObject`, `ArrowTest`?**
  _High betweenness centrality (0.075) - this node is a cross-community bridge._
- **Are the 129 inferred relationships involving `Coord3D` (e.g. with `.theRingFollowsTheCreatureItWasGivenTo()` and `.whatWasPointedAtIsHandedOn()`) actually correct?**
  _`Coord3D` has 129 INFERRED edges - model-reasoned connections that need verification._
- **What connects `graphify`, `Usage`, `What graphify is for` to the rest of the system?**
  _161 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `asserttrue` be split into smaller, more focused modules?**
  _Cohesion score 0.04466661291622995 - nodes in this community are weakly interconnected._
- **Should `FieldParseTable` be split into smaller, more focused modules?**
  _Cohesion score 0.028745163073521283 - nodes in this community are weakly interconnected._