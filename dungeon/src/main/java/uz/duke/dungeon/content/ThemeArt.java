package uz.duke.dungeon.content;

import java.util.List;

/**
 * One way a floor can look: the kit it is built from, the colour of its dark, and
 * whatever creatures walk it differently.
 *
 * <p>Look and nothing else. Which theme a floor wears changes no room, no monster
 * and no number the simulation reads — a stone dungeon and a sci-fi corridor at
 * the same depth are the same fight in different clothes. That is what lets the
 * choice be made from the seed without touching the world's own randomness, and
 * what lets a test prove the checksum is untouched.
 *
 * <p>Adding one is a block in {@code dungeon.ini} and a folder of models. There is
 * no Java to write, which is the point: {@code DungeonTheme} says what the kit is,
 * {@code DungeonTone} says how it varies, {@code DungeonThemeMonster} says what
 * lives there, and {@code DungeonThemes} says which depth wears which.
 *
 * @param folder        prefixed to every asset the theme names
 * @param tileSize      what one floor tile was modelled at, in the model's units
 * @param wallTileSize  what one wall was modelled at, when that is not the same —
 *     kits are not all authored on one module, and one of these lays two-unit
 *     floors inside four-unit walls
 * @param wallHeight    how tall a wall stands, which is where the roof over the
 *     rock is laid
 * @param wallLift      how far up a wall must move to stand on the floor rather
 *     than half sunk into it — kits disagree about where a wall's origin is
 * @param wallShift     how far back it must move for its face to land on the
 *     boundary and its body in the stone
 * @param ownMaterials  whether the models carry their own colours. A kit drawn on
 *     one atlas wants one shared skin; a kit that ships no texture and says what
 *     colour each of its parts is wants what it came with
 * @param propFolder    where this theme's own props and re-skinned creatures
 *     live. Apart from the tiles because the two are different kinds of thing
 *     and a folder each is what makes a tree worth having; empty falls back to
 *     the tile folder, which is what a kit with everything in one place wants
 * @param stairs        the flight of steps between two storeys, or null for a kit
 *     that ships none — then the client builds them out of blocks
 * @param fogTint       what the dark is coloured here, packed {@code 0xRRGGBB} —
 *     bluish under ice, red under lava, black in plain stone
 */
public record ThemeArt(
        String name,
        String folder,
        float tileSize,
        float wallTileSize,
        float wallHeight,
        float wallLift,
        float wallShift,
        boolean ownMaterials,
        String propFolder,
        String stairs,
        int fogTint,
        List<Tone> tones,
        List<ThemeMonster> monsters) {

    public ThemeArt {
        // A kit whose walls are on the same module as its floors says so by not
        // saying anything, and then this record has to give the answer rather than
        // the zero it was handed — or every reader has to know to ask twice.
        wallTileSize = wallTileSize > 0f ? wallTileSize : tileSize;
        tones = List.copyOf(tones);
        monsters = List.copyOf(monsters);
    }

    /**
     * A small variation within a theme: a different floor pattern, a different
     * wall, a warmer or colder cast over both.
     *
     * <p>Which one a floor gets is drawn from the seed, so two runs down the same
     * seed see the same room and the same stone — and a player who plays the same
     * theme twice in one run does not see the same floor twice.
     */
    public record Tone(String name, String floor, String wall, String corner, int tint) {

        public java.awt.Color awtTint() {
            return new java.awt.Color(tint);
        }
    }

    /**
     * A creature drawn differently while this theme lasts.
     *
     * <p>The kind is untouched — the same health, the same reach, the same brain.
     * Only the model changes, which is how a skeleton becomes a robot when the
     * floor becomes a spaceship without a single number moving.
     *
     * @param animationsFrom  a file to take clips from, when the creature's own
     *     model does not carry them. Null falls back to the game's library.
     * @param death  what it plays when it falls, when its own kit names that clip
     *     something else. Null falls back to the game's.
     */
    public record ThemeMonster(String template, MonsterLook look, String animationsFrom,
            String death) {
    }

    /** The steps between storeys, with the folder in front of them. */
    public String stairsPath() {
        return path(stairs);
    }

    /** The theme with every asset path made whole. */
    public Tone toneWithPaths(Tone tone) {
        return new Tone(tone.name(), path(tone.floor()), path(tone.wall()),
                path(tone.corner()), tone.tint());
    }

    private String path(String piece) {
        return piece == null ? null : folder + piece;
    }

    /** The look of a themed creature, with its model path made whole. */
    public ThemeMonster monsterWithPaths(ThemeMonster themed) {
        var look = themed.look();
        return new ThemeMonster(themed.template(),
                new MonsterLook(inProps(look.model()), inProps(look.texture()), look.modelScale(),
                        look.tint(), look.facing(), look.idle(), look.walk(), look.attack(),
                        look.hurt()),
                inProps(themed.animationsFrom()), themed.death());
    }

    /**
     * A path under the theme's props, which is where everything that is not a
     * tile lives — the pillars and statues, and any creature this theme draws
     * differently.
     *
     * <p>Falls back to the tile folder when a theme names no second one, so a kit
     * that keeps everything in one place says nothing and gets what it had.
     */
    private String inProps(String piece) {
        if (piece == null) {
            return null;
        }
        return (propFolder == null || propFolder.isBlank() ? folder : propFolder) + piece;
    }

    public java.awt.Color awtFogTint() {
        return new java.awt.Color(fogTint);
    }
}
