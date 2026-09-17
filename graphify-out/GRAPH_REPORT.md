# Graph Report - duke-engine  (2026-09-17)

## Corpus Check
- 514 files · ~837,361 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 111 file(s) not represented in the graph (top: .bin 31, .gltf 31, .glb 13)

## Summary
- 7503 nodes · 24308 edges · 256 communities (165 shown, 91 thin omitted)
- Extraction: 89% EXTRACTED · 11% INFERRED · 0% AMBIGUOUS · INFERRED: 2728 edges (avg confidence: 0.81)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `c63890cb`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- asserttrue
- DamageType
- Ini
- DungeonSettings
- UnitView
- HeroPanel
- com.jme3.scene.Node
- org.junit.jupiter.api.Test
- map
- .of
- CommandLogic
- com.jme3.math.ColorRGBA
- StudioWindow
- DukeRtsApp
- PathGrid
- .fromText
- .world
- LayeredEffects
- Tileset
- SkillBook
- SkillCastingTest
- .getLogic
- SkillEffects
- GameObject
- DungeonMonsterArtTest
- StageDraft
- StoneCraft
- .newSession
- HitFlash
- DungeonGenerator
- DukeGame
- .read
- list
- .load
- SubsystemInterface
- StudioWindow.java
- Builder
- What You Must Do When Invoked
- Main
- Shell
- Hotkeys
- .newLogic
- Visuals
- LootBag
- RangeRingsTest
- GeneratedDungeon
- DukeIniStructure.kt
- PanelLayoutTest
- DukeIniPsi.kt
- .showSettingsMenu
- LayeredEffectsTest
- .simpleInitApp
- UnitVisual
- .read
- DiscoveryTest
- WeaponUpdate
- StudioProject
- SourceFile
- ProjectileEffects
- .generate
- .showMainMenu
- DungeonEffectLayerTest
- UpdateModule
- .findPath
- DungeonRun
- RtsLogic
- PortraitLook
- HeroPortrait
- SkillEffectsTest
- MultiplayerSession
- SightTest
- ManaTest
- DungeonTilesTest
- ThemeArt
- .installInput
- MageTest
- AudioSink
- HeroBuilder
- InspectorPanel
- DukeIniHighlighting.kt
- UnitScript
- .create
- AttributesTest
- MonsterSkillTest
- .create
- Reading
- Piece
- LockstepGate
- MonsterSummoningTest
- AssetImporter
- .everySizeTheGameMakes
- OrderMarkers
- ProjectileEffectsTest
- MoveTo
- CursorsTest
- org.junit.jupiter.api.BeforeEach
- Chevrons
- HeroPortraitTest
- EffectVisual
- Replay
- Storeys
- .world
- CameraFocus
- HeroLook
- ParticleLayer
- TileLayoutTest
- MonsterHealingTest
- .frame
- StoneMenu
- HeroAttributesTest
- .newStageSession
- Heard
- .heroes
- PacketCodec
- LockstepGateTest
- .simpleUpdate
- FloatingNumbers
- Kind
- LockstepScheduler
- Discovery
- Attributes
- DukeIniLexer
- AttackObject
- ArrowTest
- SkillRanksTest
- DukeIniParser.kt
- MinimapProjection
- HealthWatchTest
- Sounds
- .attributeRules
- .of
- SkillEffect
- ExperienceModule
- com.jme3.material.Material
- StageCanvas
- GameClient
- EffectLook
- NameKeyGenerator
- VisionTest
- ProductionGateTest
- ChevronsTest
- AttributeRules
- GameSounds
- BitmapFontBaker.java
- .asCloseAsHeCanGet
- ThemedLookTest
- SpecialPowerModule
- .write
- HostTransport
- ModuleCompositionTest
- Duke-plugin
- SoundBank
- BannerPanelTest
- Levelling
- FogMap
- SkillRanks
- StaticObstacleTest
- duke-engine — coding rules
- WedgedTest
- graphify reference: extra exports and benchmark
- SelectionBoxTest
- RunMoments
- RtsPlayer
- Sunlight
- .mood
- NetMessage
- HoldGroundTest
- BannerPanel
- DungeonSoundTest
- HeroBalanceTest
- GameExporter
- shrink_particles.py
- .clickedASkillSlot
- PortraitCameraTest
- Doing
- PanelSkinTest
- SoundTouchesNothingTest
- LootTest
- IconSheets
- .themes
- ProductionUpdate
- .messagesTravelOverTcp
- Player
- CollisionTest
- March
- DungeonGeneratorTest
- WatchingTest
- Coord3D
- Theme
- contentfactory
- PropsTest
- ScriptsPanel
- .newScenario
- WeaponHoldTest
- Canvas
- Heard
- ProjectIO.java
- PlayerListTest
- graphify reference: query, path, explain
- DoingTest
- MapDef
- SkillTipTest
- AnimationLibrary
- SolidWorldTest
- Data
- com.jme3.math.Vector3f
- .spread
- GameSettingsTest
- TestEngine
- AlertTest
- ProjectileLauncherTest
- DukeIniFindUsagesProvider
- Coord2D
- graphify reference: add a URL and watch a folder
- MonsterBuilder
- .surplus
- Glyphs
- graphify reference: commit hook and native CLAUDE.md integration
- graphify reference: incremental update and cluster-only
- AttackMoveTest
- DungeonFogTest
- CountingLogic
- DukeIniLanguage.kt
- DungeonFontTest
- ProductionTest
- .skinKey
- MinimapProjectionTest
- GameLogic
- graphify reference: GitHub clone and cross-repo merge
- fbx_to_glb.py
- MovementLogic
- ICoord3D
- .parse
- MonsterLook
- duke-plugin/gradlew
- jblabel
- gradlew
- duke-plugin/build.gradle.kts
- duke-plugin/settings.gradle.kts
- .claude/CLAUDE.md
- extraction-spec.md
- JBPanel
- Project
- ToolWindow
- ToolWindowFactory

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
- `Module boundaries (the most important rule)` --references--> `WeaponFired`  [INFERRED]
  CLAUDE.md → rts/src/main/java/uz/duke/rts/event/WeaponFired.java
- `Module boundaries (the most important rule)` --references--> `DamageModifier`  [INFERRED]
  CLAUDE.md → rts/src/main/java/uz/duke/rts/module/DamageModifier.java
- `Module boundaries (the most important rule)` --references--> `Command`  [INFERRED]
  CLAUDE.md → core/src/main/java/uz/duke/core/message/Command.java
- `Module boundaries (the most important rule)` --references--> `PacketCodec`  [INFERRED]
  CLAUDE.md → core/src/main/java/uz/duke/core/network/PacketCodec.java
- `Module boundaries (the most important rule)` --references--> `ProductionGate`  [INFERRED]
  CLAUDE.md → rts/src/main/java/uz/duke/rts/module/ProductionGate.java

## Import Cycles
- None detected.

## Communities (256 total, 91 thin omitted)

### Community 0 - "asserttrue"
Cohesion: 0.04
Nodes (31): animcomposer, animtrack, assertarrayequals, assertequals, assertfalse, assertinstanceof, assertnotequals, assertnotnull (+23 more)

### Community 1 - "DamageType"
Cohesion: 0.06
Nodes (18): Armor, Builder, BodyModule, DamageType, ARMOR_PIERCING, EXPLOSION, FLAME, NORMAL (+10 more)

### Community 2 - "Ini"
Cohesion: 0.02
Nodes (52): FieldParser, FunctionalInterface, FieldParseTable, BlockParser, Ini, FunctionalInterface, ObjIntConsumer, Data (+44 more)

### Community 3 - "DungeonSettings"
Cohesion: 0.02
Nodes (13): AttributeArt, AttributeBlock, AttributeBuilder, BarStep, BossGuard, CursorLook, DungeonSettings, SkinBuilder (+5 more)

### Community 4 - "UnitView"
Cohesion: 0.03
Nodes (40): ambientlight, analoglistener, animcontrol, audiodata, billboardcontrol, bitset, blendableaction, Module boundaries (the most important rule) (+32 more)

### Community 5 - "HeroPanel"
Cohesion: 0.05
Nodes (9): Block, HeroPanel, Align, OrderReading, Placing, Slot, Stat, StatLine (+1 more)

### Community 6 - "com.jme3.scene.Node"
Cohesion: 0.07
Nodes (33): bifunction, bitmaptext, blendmode, bufferutils, OrderButton, AssetManager, LoadingOverlay, Align (+25 more)

### Community 7 - "org.junit.jupiter.api.Test"
Cohesion: 0.04
Nodes (9): HeroPanelTest, OrderMarkTest, SkillTipTest, Coord3DTest, ThingSystemTest, SkillTest, org.junit.jupiter.api.Test, PlayerBonusTest (+1 more)

### Community 8 - "map"
Cohesion: 0.04
Nodes (45): arraydeque, arrays, assertiterableequals, biconsumer, boundingsphere, bytearrayoutputstream, collections, comparator (+37 more)

### Community 9 - ".of"
Cohesion: 0.12
Nodes (5): FunctionalInterface, PartitionFilter, Override, PartitionManager, PartitionManagerTest

### Community 11 - "com.jme3.math.ColorRGBA"
Cohesion: 0.06
Nodes (11): Shade, Step, UnitBarLook, UnitBarReading, Bar, Lettering, Standing, UnitBars (+3 more)

### Community 12 - "StudioWindow"
Cohesion: 0.12
Nodes (4): ActionListener, javax.swing.JMenuItem, JComboBox, StudioWindow

### Community 13 - "DukeRtsApp"
Cohesion: 0.07
Nodes (10): DukeRtsApp, Dying, Color, Preferences, Size, UnitNode, Cast, com.jme3.anim.AnimComposer (+2 more)

### Community 14 - "PathGrid"
Cohesion: 0.08
Nodes (5): Placement, TileLayout, NoGapsTest, Pathfinder, PathGrid

### Community 15 - ".fromText"
Cohesion: 0.09
Nodes (9): TileSource, ColourWatchingTiles, Override, Spatial, RailedStair, StubTiles, StubTilesWithFace, TerrainSceneTest (+1 more)

### Community 16 - ".world"
Cohesion: 0.10
Nodes (11): Box, Cylinder, Override, Sphere, FlatWorldChecksumTest, Override, TestLogic, ClimbingTest (+3 more)

### Community 17 - "LayeredEffects"
Cohesion: 0.08
Nodes (8): EffectLayer, LayeredEffects, Moment, Place, Playing, Riding, Surroundings, com.jme3.renderer.Camera

### Community 18 - "Tileset"
Cohesion: 0.05
Nodes (4): BoundingBox, Standing, TerrainScene, Tileset

### Community 19 - "SkillBook"
Cohesion: 0.05
Nodes (11): Override, Override, Skill, CastMark, Override, SkillBook, Words, SkillTip (+3 more)

### Community 21 - ".getLogic"
Cohesion: 0.08
Nodes (4): HeroProgress, HeroStatus, Placed, Spawner

### Community 22 - "SkillEffects"
Cohesion: 0.07
Nodes (12): Glow, GroundRing, RangeLook, ColorRGBA, Lane, RangeRings, Color, Vector3f (+4 more)

### Community 23 - "GameObject"
Cohesion: 0.05
Nodes (9): GameObject, World, KeepingDistance, SightLine, Shot, Mending, Summoning, Override (+1 more)

### Community 25 - "StageDraft"
Cohesion: 0.07
Nodes (6): javax.swing.KeyStroke, JRootPane, StageDraft, BuilderWindow, Wanted, WorldBuilderMain

### Community 26 - "StoneCraft"
Cohesion: 0.22
Nodes (3): Align, StoneCraft, Hit

### Community 27 - ".newSession"
Cohesion: 0.10
Nodes (3): HeroChoiceTest, DepthTest, DungeonRunTest

### Community 28 - "HitFlash"
Cohesion: 0.21
Nodes (4): Flash, HitFlash, HitFlashTest, com.jme3.material.MatParamOverride

### Community 29 - "DungeonGenerator"
Cohesion: 0.06
Nodes (6): PropKind, Color, MonsterKind, DeterministicRng, DungeonGenerator, Room

### Community 30 - "DukeGame"
Cohesion: 0.05
Nodes (11): Arena, Field, BuildOption, DukeGame, Logger, ServerSocket, SkirmishAssembler, GamePlayer (+3 more)

### Community 32 - "list"
Cohesion: 0.04
Nodes (39): arraylist, atomicinteger, atomicreference, collectors, color, concurrentlinkedqueue, consumer, Command (+31 more)

### Community 34 - "SubsystemInterface"
Cohesion: 0.09
Nodes (4): GameEngine, Override, SubsystemInterface, SubsystemList

### Community 35 - "StudioWindow.java"
Cohesion: 0.05
Nodes (49): borderfactory, borderlayout, boxlayout, dimension, filenameextensionfilter, GameWindow, gridbagconstraints, gridbaglayout (+41 more)

### Community 37 - "What You Must Do When Invoked"
Cohesion: 0.08
Nodes (24): For /graphify add and --watch, For /graphify query, For the commit hook and native CLAUDE.md integration, For --update and --cluster-only, /graphify, Honesty Rules, Interpreter guard for subcommands, Part A - Structural extraction for code files (+16 more)

### Community 38 - "Main"
Cohesion: 0.06
Nodes (8): Session, Logger, Main, Logger, Listed, Stages, ControlsTest, StageChoiceTest

### Community 39 - "Shell"
Cohesion: 0.11
Nodes (11): Entry, HOST_LAN, JOIN_LAN, PLAY, QUIT, SETTINGS, SKIRMISH, Option (+3 more)

### Community 40 - "Hotkeys"
Cohesion: 0.08
Nodes (11): Aim, GROUND, NOW, OPEN_GROUND, UNIT, UNIT_OR_GROUND, Aimed, Binding (+3 more)

### Community 41 - ".newLogic"
Cohesion: 0.12
Nodes (7): GameSnapshot, GameSnapshotTest, Override, TestLogic, Override, TestLogic, UpgradeTest

### Community 42 - "Visuals"
Cohesion: 0.06
Nodes (9): EdgeScroll, IconLook, MenuStyle, PanelSkin, CastAnim, EffectBudget, HitFlashLook, MomentLook (+1 more)

### Community 43 - "LootBag"
Cohesion: 0.09
Nodes (14): LootBuilder, Loot, LootBag, Override, LootDrop, LootKind, ARMOUR, ATTACK (+6 more)

### Community 44 - "RangeRingsTest"
Cohesion: 0.11
Nodes (10): Shape, AROUND_HIM, AT_A_CREATURE, AT_A_SPOT, DOWN_A_LANE, ON_HIMSELF, SkillRange, RangeRingsTest (+2 more)

### Community 45 - "GeneratedDungeon"
Cohesion: 0.10
Nodes (11): GeneratedDungeon, Link, Monster, Placement, Prop, Override, StageFloors, Stage (+3 more)

### Community 46 - "DukeIniStructure.kt"
Cohesion: 0.07
Nodes (26): Document, DukeIniFoldingBuilder, DukeIniStructureElement, DukeIniStructureViewFactory, TreeBasedStructureViewBuilder, DukeIniStructureViewModel, ASTNode, DumbAware (+18 more)

### Community 47 - "PanelLayoutTest"
Cohesion: 0.12
Nodes (5): BitmapFont, ColorRGBA, PanelLayoutTest, Span, com.jme3.bounding.BoundingBox

### Community 48 - "DukeIniPsi.kt"
Cohesion: 0.09
Nodes (27): astnode, ASTWrapperPsiElement, defaultwordsscanner, PsiElement, DukeIniBadLine, DukeIniBlock, DukeIniDeclarations, DukeIniField (+19 more)

### Community 49 - ".showSettingsMenu"
Cohesion: 0.10
Nodes (10): Screen, LOADING, MENU, PAUSED, PLAYING, SETTINGS, GameSettings, Preferences (+2 more)

### Community 50 - "LayeredEffectsTest"
Cohesion: 0.19
Nodes (3): LayeredEffectsTest, Rig, Type

### Community 52 - "UnitVisual"
Cohesion: 0.08
Nodes (4): AnimationSource, Carried, Color, UnitVisual

### Community 53 - ".read"
Cohesion: 0.16
Nodes (3): UnitBarReadingTest, Screen, UnitBarsTest

### Community 54 - "DiscoveryTest"
Cohesion: 0.11
Nodes (4): appsettings, Duke3D, Fog, DiscoveryTest

### Community 55 - "WeaponUpdate"
Cohesion: 0.05
Nodes (10): Override, Chasing, HeroBrain, Override, Override, MonsterBrain, Orders, WayAhead (+2 more)

### Community 56 - "StudioProject"
Cohesion: 0.07
Nodes (14): placement, playerdef, RohanVsMordor, GameFactory, Result, FactionDef, Placement, PlayerDef (+6 more)

### Community 57 - "SourceFile"
Cohesion: 0.40
Nodes (3): javax.tools.SimpleJavaFileObject, Override, SourceFile

### Community 58 - "ProjectileEffects"
Cohesion: 0.12
Nodes (7): LightPool, Burst, Color, ProjectileEffects, Trail, com.jme3.effect.ParticleEmitter, com.jme3.light.PointLight

### Community 59 - ".generate"
Cohesion: 0.07
Nodes (7): Layout, GeneratedFloors, Override, DungeonShapeTest, LayoutTest, MonsterPlacementTest, RoomCapTest

### Community 61 - "DungeonEffectLayerTest"
Cohesion: 0.06
Nodes (6): EffectLayerArt, MomentArt, Builder, DungeonEffectLayerTest, ProjectileEffectTest, SkillLookTest

### Community 62 - "UpdateModule"
Cohesion: 0.06
Nodes (16): UpdateModule, ObjectStatus, DISABLED, SLOWED, Data, Override, MoverUpdate, Facing (+8 more)

### Community 63 - ".findPath"
Cohesion: 0.13
Nodes (3): LevelsTest, PathfinderTest, PathSmoothingTest

### Community 64 - "DungeonRun"
Cohesion: 0.09
Nodes (6): DungeonRun, State, DEAD, RUNNING, WON, Floors

### Community 65 - "RtsLogic"
Cohesion: 0.08
Nodes (9): RtsClient, Override, RtsGameEngine, DefeatListener, IntervalCallback, FunctionalInterface, Logger, Override (+1 more)

### Community 66 - "PortraitLook"
Cohesion: 0.10
Nodes (13): State, REMEMBERED, UNSEEN, VISIBLE, PortraitLook, State, CALM, DEAD (+5 more)

### Community 67 - "HeroPortrait"
Cohesion: 0.09
Nodes (6): Bodies, HeroPortrait, Clips, com.jme3.renderer.RenderManager, com.jme3.renderer.ViewPort, com.jme3.texture.FrameBuffer

### Community 69 - "MultiplayerSession"
Cohesion: 0.10
Nodes (7): SessionState, DESYNCED, DISCONNECTED, RUNNING, IntConsumer, Override, MultiplayerSession

### Community 73 - "ThemeArt"
Cohesion: 0.10
Nodes (11): Color, Standing, ThemeArt, ThemeMonster, Tone, Chosen, Themes, WhenExhausted (+3 more)

### Community 74 - ".installInput"
Cohesion: 0.12
Nodes (4): Kind, ATTACK, ATTACK_MOVE, MOVE

### Community 75 - "MageTest"
Cohesion: 0.14
Nodes (3): Arena, Data, MageTest

### Community 76 - "AudioSink"
Cohesion: 0.35
Nodes (4): AudioSink, Node, Override, com.jme3.audio.AudioNode

### Community 77 - "HeroBuilder"
Cohesion: 0.28
Nodes (3): AttributeLine, HeroBuilder, Held

### Community 78 - "InspectorPanel"
Cohesion: 0.10
Nodes (18): Container, java.awt.Component, javax.swing.JCheckBox, CapabilityType, ATTACK, AUTO_HEAL, CAPACITY_GATE, EXPERIENCE (+10 more)

### Community 79 - "DukeIniHighlighting.kt"
Cohesion: 0.14
Nodes (19): AnnotationHolder, Annotator, createtextattributeskey, defaultlanguagehighlightercolors, DukeIniAnnotator, DukeIniSyntaxHighlighter, DukeIniSyntaxHighlighterFactory, DumbAware (+11 more)

### Community 80 - "UnitScript"
Cohesion: 0.10
Nodes (6): Override, UnitScript, Broken, Override, ScriptModuleTest, Walker

### Community 81 - ".create"
Cohesion: 0.14
Nodes (9): Job, Kind, ANIMATIONS, MODEL, SOUND, TEXTURE, TILE, Preload (+1 more)

### Community 82 - "AttributesTest"
Cohesion: 0.14
Nodes (7): HeroBase, Found, HeroFigures, AttributesTest, Find, Hero, HeroFiguresGoldenTest

### Community 84 - ".create"
Cohesion: 0.10
Nodes (6): DukeGameTest, EventVisibilityTest, GameCommandTest, Shout, ReplayRoundTripTest, StatusLineTest

### Community 85 - "Reading"
Cohesion: 0.10
Nodes (10): CostReading, ItemReading, ItemSlot, RankReading, Reading, SkillReading, State, COOLING (+2 more)

### Community 86 - "Piece"
Cohesion: 0.25
Nodes (8): Piece, CAP, CORNER, FLOOR, LEDGE, ROCK_FACE, STAIR, WALL

### Community 87 - "LockstepGate"
Cohesion: 0.13
Nodes (5): Desync, Override, LockstepGate, Transport, java.util.function.IntConsumer

### Community 88 - "MonsterSummoningTest"
Cohesion: 0.19
Nodes (4): Circle, Floor, MonsterSummoningTest, Rising

### Community 91 - "OrderMarkers"
Cohesion: 0.22
Nodes (4): OrderMarkers, AttackFlashTest, Scene, OrderMarkersTest

### Community 93 - "MoveTo"
Cohesion: 0.16
Nodes (4): DungeonCombatTest, Fight, DungeonTest, MoveTo

### Community 94 - "CursorsTest"
Cohesion: 0.10
Nodes (8): Cursors, Look, Over, CursorsTest, JmeCursor, com.jme3.cursors.plugins.JmeCursor, com.jme3.input.InputManager, com.jme3.texture.Image

### Community 95 - "org.junit.jupiter.api.BeforeEach"
Cohesion: 0.04
Nodes (21): Override, PathFollowingTest, TestLogic, Override, TestLogic, Override, TestLogic, org.junit.jupiter.api.BeforeEach (+13 more)

### Community 96 - "Chevrons"
Cohesion: 0.16
Nodes (6): AttackFlash, Chevrons, Mark, OrderMark, Step, Marker

### Community 99 - "Replay"
Cohesion: 0.19
Nodes (4): Replay, Override, ReplayMismatch, ReplayTest

### Community 100 - "Storeys"
Cohesion: 0.11
Nodes (4): Corridor, Result, Storeys, StoreysTest

### Community 101 - ".world"
Cohesion: 0.16
Nodes (3): Fight, Data, MonsterKindsTest

### Community 103 - "HeroLook"
Cohesion: 0.11
Nodes (4): PortraitBuilder, HeroLook, PortraitArt, DungeonPortraitTest

### Community 106 - "MonsterHealingTest"
Cohesion: 0.32
Nodes (3): Hurt, MonsterHealingTest, Ward

### Community 107 - ".frame"
Cohesion: 0.19
Nodes (3): NineSlice, Rect, NineSliceTest

### Community 108 - "StoneMenu"
Cohesion: 0.09
Nodes (11): Action, Buttons, Choice, Level, Opens, Row, StoneMenu, Bar (+3 more)

### Community 111 - "Heard"
Cohesion: 0.19
Nodes (3): Heard, Override, SoundsTest

### Community 113 - "PacketCodec"
Cohesion: 0.14
Nodes (4): NetFraming, PacketCodec, Override, ReplayRecorder

### Community 114 - "LockstepGateTest"
Cohesion: 0.23
Nodes (5): Override, LockstepGateTest, Peer, PeerLogic, Switchboard

### Community 115 - ".simpleUpdate"
Cohesion: 0.09
Nodes (3): GamePanel, Override, java.awt.Rectangle

### Community 116 - "FloatingNumbers"
Cohesion: 0.12
Nodes (5): FloatingNumbers, Mark, HitNumbers, FloatingNumbersTest, Screen

### Community 117 - "Kind"
Cohesion: 0.16
Nodes (3): Override, Kind, RtsKinds

### Community 118 - "LockstepScheduler"
Cohesion: 0.19
Nodes (3): Override, LockstepScheduler, LockstepSchedulerTest

### Community 121 - "DukeIniLexer"
Cohesion: 0.15
Nodes (7): DukeIniElementType, DukeIniLexer, DukeIniTypes, IElementType, ifileelementtype, LexerBase, tokentype

### Community 122 - "AttackObject"
Cohesion: 0.12
Nodes (6): GuardOrderTest, Report, StuckDiagnosisTest, AttackObject, CombatTest, CommandCodecTest

### Community 125 - "DukeIniParser.kt"
Cohesion: 0.22
Nodes (9): DukeIniParser, DukeIniParserDefinition, ASTNode, IElementType, Project, ParserDefinition, PsiBuilder, PsiParser (+1 more)

### Community 127 - "HealthWatchTest"
Cohesion: 0.12
Nodes (8): Change, Death, HealthWatch, Blow, Gone, Landing, HealthWatchTest, LandingTest

### Community 128 - "Sounds"
Cohesion: 0.19
Nodes (8): Channel, EFFECTS, MUSIC, UI, VOICE, Sounds, SoundSink, java.util.random.RandomGenerator

### Community 129 - ".attributeRules"
Cohesion: 0.27
Nodes (3): Arena, Fight, HeroProgressTest

### Community 130 - ".of"
Cohesion: 0.24
Nodes (4): KindTest, AsciiRenderer, Override, AsciiRendererTest

### Community 131 - "SkillEffect"
Cohesion: 0.10
Nodes (18): SkillBuilder, Aim, GROUND, OPEN_GROUND, SELF, UNIT, SkillEffect, AREA_AT_SPOT (+10 more)

### Community 132 - "ExperienceModule"
Cohesion: 0.09
Nodes (10): DamageModifier, Data, DataBuilder, ExperienceModule, Override, Rank, DamageModifierTest, Override (+2 more)

### Community 133 - "com.jme3.material.Material"
Cohesion: 0.22
Nodes (3): Texture, KitTiles, com.jme3.material.Material

### Community 134 - "StageCanvas"
Cohesion: 0.08
Nodes (24): basicstroke, defaultcomboboxmodel, flowlayout, graphics2d, java.awt.event.MouseAdapter, java.awt.event.MouseEvent, java.awt.event.MouseWheelEvent, java.awt.Graphics (+16 more)

### Community 135 - "GameClient"
Cohesion: 0.14
Nodes (5): Renderer, Override, RenderingGameClient, GameClient, Override

### Community 136 - "EffectLook"
Cohesion: 0.15
Nodes (5): ArrowLook, EffectBuilder, EffectLook, Color, ProjectileBuilder

### Community 137 - "NameKeyGenerator"
Cohesion: 0.21
Nodes (4): Override, NameKeyGenerator, NameKeyType, NameKeyGeneratorTest

### Community 139 - "ProductionGateTest"
Cohesion: 0.25
Nodes (4): ProductionGate, Override, Permit, ProductionGateTest

### Community 141 - "AttributeRules"
Cohesion: 0.25
Nodes (4): AttributeRules, Data, HeroAttributes, HeroBuild

### Community 143 - "BitmapFontBaker.java"
Cohesion: 0.10
Nodes (14): BasePlatformTestCase, bufferedimage, Baked, BitmapFontBaker, Glyph, Graphics2D, DukeIniTest, file (+6 more)

### Community 145 - "ThemedLookTest"
Cohesion: 0.27
Nodes (3): Override, Stub, ThemedLookTest

### Community 146 - "SpecialPowerModule"
Cohesion: 0.24
Nodes (4): Data, DataBuilder, Override, SpecialPowerModule

### Community 147 - ".write"
Cohesion: 0.10
Nodes (5): StageFile, StageFileTest, Example, ExampleStage, StageDraftTest

### Community 148 - "HostTransport"
Cohesion: 0.15
Nodes (12): bufferedreader, bufferedwriter, concurrenthashmap, copyonwritearraylist, Arrival, HostTransport, Override, Link (+4 more)

### Community 149 - "ModuleCompositionTest"
Cohesion: 0.22
Nodes (4): Override, Legs, ModuleCompositionTest, Ticker

### Community 150 - "Duke-plugin"
Cohesion: 0.12
Nodes (14): Added, Duke-plugin Changelog, Removed, [Unreleased], Build script, Duke-plugin, Features, Overview (+6 more)

### Community 151 - "SoundBank"
Cohesion: 0.17
Nodes (3): Builder, Cue, SoundBank

### Community 152 - "BannerPanelTest"
Cohesion: 0.28
Nodes (3): BannerPanelTest, BitmapText, Screen

### Community 154 - "FogMap"
Cohesion: 0.16
Nodes (5): FogMap, ColorRGBA, FogMapTest, colorspace, com.jme3.texture.Texture2D

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
Cohesion: 0.36
Nodes (3): Moment, RunMoments, RunMomentsTest

### Community 165 - "NetMessage"
Cohesion: 0.22
Nodes (5): Override, LoopbackTransport, NetMessage, Override, SocketTransport

### Community 166 - "HoldGroundTest"
Cohesion: 0.21
Nodes (3): HoldGround, HoldGroundTest, Standoff

### Community 170 - "GameExporter"
Cohesion: 0.18
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

### Community 178 - "IconSheets"
Cohesion: 0.26
Nodes (4): Box, IconSheets, Sheet, java.awt.image.BufferedImage

### Community 180 - "ProductionUpdate"
Cohesion: 0.10
Nodes (5): Override, Data, DataBuilder, Job, ProductionUpdate

### Community 181 - ".messagesTravelOverTcp"
Cohesion: 0.36
Nodes (4): BooleanSupplier, org.junit.jupiter.api.Timeout, Guest, NetworkTransportTest

### Community 182 - "Player"
Cohesion: 0.05
Nodes (13): Override, Player, FunctionalInterface, Override, PlayerFactory, PlayerList, Override, ScriptEngine (+5 more)

### Community 183 - "CollisionTest"
Cohesion: 0.37
Nodes (3): CollisionTest, Override, TestLogic

### Community 187 - "Coord3D"
Cohesion: 0.05
Nodes (11): Override, Coord3D, Path, Footprint, Geometry, AttackMove, Placement, AttackOnTheMoveTest (+3 more)

### Community 192 - ".newScenario"
Cohesion: 0.29
Nodes (3): BattleLogic, DeterminismIntegrationTest, Override

### Community 193 - "WeaponHoldTest"
Cohesion: 0.15
Nodes (6): EyesOnly, Override, WeaponHold, Busy, Override, WeaponHoldTest

### Community 195 - "Heard"
Cohesion: 0.38
Nodes (3): Heard, Override, SelectedCardSoundTest

### Community 196 - "ProjectIO.java"
Cohesion: 0.23
Nodes (6): com.google.gson.Gson, gsonbuilder, ProjectIO, StudioMain, swingutilities, uimanager

### Community 198 - "graphify reference: query, path, explain"
Cohesion: 0.33
Nodes (5): For /graphify explain, For /graphify path, graphify reference: query, path, explain, Step 0 — Constrained query expansion (REQUIRED before traversal), Step 1 — Traversal

### Community 200 - "MapDef"
Cohesion: 0.32
Nodes (3): MapImporter, MapDef, MapPanel

### Community 202 - "AnimationLibrary"
Cohesion: 0.29
Nodes (4): AnimationLibrary, com.jme3.anim.Armature, com.jme3.anim.Joint, Quaternion

### Community 203 - "SolidWorldTest"
Cohesion: 0.36
Nodes (3): Override, SolidWorldTest, TestLogic

### Community 205 - "com.jme3.math.Vector3f"
Cohesion: 0.16
Nodes (8): Override, music(), musicGain(), play(), GroundPickTest, Override, Place, com.jme3.math.Vector3f

### Community 206 - ".spread"
Cohesion: 0.31
Nodes (3): Formation, Spot, FormationTest

### Community 208 - "TestEngine"
Cohesion: 0.25
Nodes (3): GameEngineTest, Override, TestEngine

### Community 210 - "ProjectileLauncherTest"
Cohesion: 0.28
Nodes (4): Override, ProjectileLauncherTest, Quiver, Sharpened

### Community 211 - "DukeIniFindUsagesProvider"
Cohesion: 0.27
Nodes (4): DukeIniFindUsagesProvider, PsiElement, FindUsagesProvider, WordsScanner

### Community 214 - "graphify reference: add a URL and watch a folder"
Cohesion: 0.50
Nodes (3): For /graphify add, For --watch, graphify reference: add a URL and watch a folder

### Community 215 - "MonsterBuilder"
Cohesion: 0.22
Nodes (3): CursorBuilder, MonsterBuilder, ThemeMonsterBuilder

### Community 216 - ".surplus"
Cohesion: 0.24
Nodes (3): Override, Override, PowerGrid

### Community 218 - "graphify reference: commit hook and native CLAUDE.md integration"
Cohesion: 0.50
Nodes (3): For git commit hook, For native CLAUDE.md integration, graphify reference: commit hook and native CLAUDE.md integration

### Community 219 - "graphify reference: incremental update and cluster-only"
Cohesion: 0.50
Nodes (3): For --cluster-only, For --update (incremental re-extraction), graphify reference: incremental update and cluster-only

### Community 222 - "CountingLogic"
Cohesion: 0.43
Nodes (3): CountingLogic, GameLogicTest, Override

### Community 223 - "DukeIniLanguage.kt"
Cohesion: 0.09
Nodes (16): allicons, cachedvalueprovider, cachedvaluesmanager, DukeBundle, DukeIniFile, DukeIniFileType, DukeIniLanguage, dynamicbundle (+8 more)

### Community 225 - "ProductionTest"
Cohesion: 0.31
Nodes (3): Override, ProductionTest, TestLogic

### Community 228 - "GameLogic"
Cohesion: 0.04
Nodes (38): GameLogic, ActiveBody, Override, Locomotor, Builder, DataParser, FunctionalInterface, ModuleFactory (+30 more)

### Community 230 - "fbx_to_glb.py"
Cohesion: 0.40
Nodes (3): bpy, Turns an .fbx of one animation into a .glb the game can read. ./gradlew…, sys

### Community 233 - ".parse"
Cohesion: 0.09
Nodes (6): LayerBuilder, MomentBuilder, PropBuilder, SoundBuilder, ThemeBuilder, ToneBuilder

### Community 236 - "duke-plugin/gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 238 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

## Knowledge Gaps
- **171 isolated node(s):** `Added`, `Removed`, `Overview`, `Features`, `Plugin structure` (+166 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 896 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **91 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `DukeRtsApp` connect `DukeRtsApp` to `asserttrue`, `UnitView`, `com.jme3.material.Material`, `HeroPanel`, `com.jme3.scene.Node`, `map`, `com.jme3.math.ColorRGBA`, `PathGrid`, `GameSounds`, `LayeredEffects`, `Tileset`, `SkillEffects`, `FogMap`, `StoneCraft`, `HitFlash`, `DukeGame`, `list`, `SelectionBoxTest`, `RunMoments`, `BannerPanel`, `Hotkeys`, `Shell`, `Visuals`, `.clickedASkillSlot`, `.showSettingsMenu`, `.simpleInitApp`, `ProjectileEffects`, `Theme`, `.showMainMenu`, `HeroPortrait`, `.installInput`, `AudioSink`, `com.jme3.math.Vector3f`, `OrderMarkers`, `CursorsTest`, `Chevrons`, `.skinKey`, `CameraFocus`, `StoneMenu`, `.simpleUpdate`, `FloatingNumbers`, `Discovery`, `MinimapProjection`, `HealthWatchTest`?**
  _High betweenness centrality (0.122) - this node is a cross-community bridge._
- **Why does `DungeonSettings` connect `DungeonSettings` to `asserttrue`, `.attributeRules`, `Ini`, `SkillEffect`, `StageCanvas`, `EffectLook`, `map`, `SkillBook`, `SkillCastingTest`, `.getLogic`, `.write`, `GameObject`, `DungeonMonsterArtTest`, `StageDraft`, `.newSession`, `DungeonGenerator`, `WedgedTest`, `.read`, `list`, `.load`, `DukeGame`, `StudioWindow.java`, `Main`, `HoldGroundTest`, `DungeonSoundTest`, `Hotkeys`, `HeroBalanceTest`, `LootBag`, `GeneratedDungeon`, `LootTest`, `DiscoveryTest`, `WeaponUpdate`, `DungeonGeneratorTest`, `WatchingTest`, `.generate`, `DungeonEffectLayerTest`, `UpdateModule`, `PropsTest`, `DungeonRun`, `WeaponHoldTest`, `ProjectIO.java`, `SightTest`, `DoingTest`, `ManaTest`, `ThemeArt`, `SkillTipTest`, `MageTest`, `HeroBuilder`, `AlertTest`, `MonsterSkillTest`, `.monsters`, `MonsterBuilder`, `MonsterSummoningTest`, `.everySizeTheGameMakes`, `AttackMoveTest`, `MoveTo`, `DungeonFogTest`, `GameLogic`, `Storeys`, `.world`, `HeroLook`, `.parse`, `MonsterHealingTest`, `HeroAttributesTest`, `.newStageSession`, `.heroes`, `AttackObject`, `ArrowTest`?**
  _High betweenness centrality (0.108) - this node is a cross-community bridge._
- **Why does `Coord3D` connect `Coord3D` to `asserttrue`, `DamageType`, `.attributeRules`, `Ini`, `UnitView`, `.of`, `com.jme3.scene.Node`, `org.junit.jupiter.api.Test`, `map`, `.of`, `CommandLogic`, `VisionTest`, `ExperienceModule`, `DukeRtsApp`, `GameSounds`, `PathGrid`, `.asCloseAsHeCanGet`, `.world`, `.fromText`, `SkillBook`, `SkillCastingTest`, `.getLogic`, `SkillEffects`, `GameObject`, `SpecialPowerModule`, `StaticObstacleTest`, `WedgedTest`, `DukeGame`, `list`, `HoldGroundTest`, `Hotkeys`, `.newLogic`, `RangeRingsTest`, `ProductionUpdate`, `.messagesTravelOverTcp`, `WeaponUpdate`, `CollisionTest`, `March`, `StudioProject`, `UpdateModule`, `.findPath`, `.newScenario`, `WeaponHoldTest`, `SkillEffectsTest`, `SightTest`, `DoingTest`, `.installInput`, `MageTest`, `SolidWorldTest`, `UnitScript`, `ProjectileLauncherTest`, `MonsterSkillTest`, `.create`, `MonsterSummoningTest`, `OrderMarkers`, `AttackMoveTest`, `MoveTo`, `DungeonFogTest`, `org.junit.jupiter.api.BeforeEach`, `Chevrons`, `Replay`, `GameLogic`, `.world`, `HeroAttributesTest`, `LockstepGateTest`, `.simpleUpdate`, `AttackObject`, `ArrowTest`?**
  _High betweenness centrality (0.085) - this node is a cross-community bridge._
- **Are the 129 inferred relationships involving `Coord3D` (e.g. with `.theRingFollowsTheCreatureItWasGivenTo()` and `.whatWasPointedAtIsHandedOn()`) actually correct?**
  _`Coord3D` has 129 INFERRED edges - model-reasoned connections that need verification._
- **What connects `Added`, `Removed`, `Overview` to the rest of the system?**
  _171 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `asserttrue` be split into smaller, more focused modules?**
  _Cohesion score 0.04476255396500795 - nodes in this community are weakly interconnected._
- **Should `DamageType` be split into smaller, more focused modules?**
  _Cohesion score 0.06095791001451379 - nodes in this community are weakly interconnected._