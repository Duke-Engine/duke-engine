package uz.dukeengine.client3d;

import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapCharacter;
import com.jme3.font.BitmapCharacterSet;
import com.jme3.font.BitmapFont;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.plugins.AWTLoader;
import java.awt.Font;
import java.awt.FontFormatException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * A face the game named for the hero's bar, baked at the pixel sizes its lines are drawn at: one pixel of the face to
 * one of the screen however large the bar is drawn, where a bitmap font drawn through the bar's scale is resampled.
 * Each size is baked once ({@link BitmapFontBaker}) and kept.
 */
final class Lettering {

    private static final Logger LOG = Logger.getLogger(Lettering.class.getName());
    private static final Pattern FIELD = Pattern.compile("(\\w+)=(\"[^\"]*\"|\\S+)");
    /** The size a face's kerning is measured at, once, and scaled from to every size it is baked at. */
    private static final float KERNING_MEASURED_AT = 64f;
    /** Each face named, loaded once for the asset manager that loads it: a bar rebuilt at a resize bakes nothing new. */
    private static final Map<AssetManager, Map<String, java.util.Optional<Lettering>>> KEPT =
            new java.util.WeakHashMap<>();

    private final AssetManager assets;
    private final Font face;
    private final Map<Integer, BitmapFont> bySize = new HashMap<>();
    private BitmapFontBaker.Kerning kerning;

    Lettering(AssetManager assets, Font face) {
        this.assets = assets;
        this.face = face;
    }

    /** The face in the font file at {@code path}, from the resource root; null for none named, or one that will not load. */
    static Lettering of(AssetManager assets, String path) {
        if (path == null || path.isBlank() || assets == null) {
            return null;
        }
        synchronized (KEPT) {
            return KEPT.computeIfAbsent(assets, manager -> new HashMap<>())
                    .computeIfAbsent(path, named -> java.util.Optional.ofNullable(load(assets, named)))
                    .orElse(null);
        }
    }

    private static Lettering load(AssetManager assets, String path) {
        try {
            var file = assets.locateAsset(new AssetKey<>(path));
            if (file == null) {
                LOG.warning(() -> "the hero bar's lettering names a font file that is not there: " + path);
                return null;
            }
            try (var stream = file.openStream()) {
                return new Lettering(assets, Font.createFont(Font.TRUETYPE_FONT, stream));
            }
        } catch (IOException | FontFormatException notAFace) {
            LOG.warning(() -> "the hero bar's lettering will not load from " + path + ": " + notAFace);
            return null;
        }
    }

    /** The face baked so that a line of it stands {@code pixels} tall, its rendered size; null where it cannot be. */
    BitmapFont at(int pixels) {
        return bySize.computeIfAbsent(Math.max(1, pixels), this::bake);
    }

    private BitmapFont bake(int pixels) {
        try {
            if (kerning == null) {
                kerning = BitmapFontBaker.Kerning.of(face.deriveFont(KERNING_MEASURED_AT),
                        BitmapFontBaker.DEFAULT_CHARACTERS);
            }
            return fontOf(BitmapFontBaker.bake(sized(pixels), "lettering", BitmapFontBaker.DEFAULT_CHARACTERS,
                    kerning));
        } catch (IOException cannot) {
            LOG.warning(() -> "the hero bar's lettering could not be baked at " + pixels + " pixels: " + cannot);
            return null;
        }
    }

    /** The face at the point size whose line, ascent and descent, comes to {@code pixels}: a few tries close it. */
    private Font sized(int pixels) {
        float points = pixels;
        var sized = face.deriveFont(points);
        for (int tries = 0; tries < 6; tries++) {
            int rendered = BitmapFontBaker.renderedSize(sized);
            if (rendered == pixels || rendered <= 0) {
                break;
            }
            points *= (float) pixels / rendered;
            sized = face.deriveFont(points);
        }
        return sized;
    }

    /** The baked pair as the bitmap font jME draws with, read as its own loader reads an AngelCode file. */
    private BitmapFont fontOf(BitmapFontBaker.Baked baked) throws IOException {
        var set = new BitmapCharacterSet();
        for (var line : baked.fnt().split("\n")) {
            var fields = new HashMap<String, String>();
            var field = FIELD.matcher(line);
            while (field.find()) {
                fields.put(field.group(1), field.group(2));
            }
            if (line.startsWith("info ")) {
                set.setRenderedSize(number(fields, "size"));
            } else if (line.startsWith("common ")) {
                set.setLineHeight(number(fields, "lineHeight"));
                set.setBase(number(fields, "base"));
                set.setWidth(number(fields, "scaleW"));
                set.setHeight(number(fields, "scaleH"));
            } else if (line.startsWith("char ")) {
                var glyph = new BitmapCharacter();
                glyph.setX(number(fields, "x"));
                glyph.setY(number(fields, "y"));
                glyph.setWidth(number(fields, "width"));
                glyph.setHeight(number(fields, "height"));
                glyph.setXOffset(number(fields, "xoffset"));
                glyph.setYOffset(number(fields, "yoffset"));
                glyph.setXAdvance(number(fields, "xadvance"));
                glyph.setPage(0);
                set.addCharacter(number(fields, "id"), glyph);
            } else if (line.startsWith("kerning ")) {
                var first = set.getCharacter(number(fields, "first"));
                if (first != null) {
                    first.addKerning(number(fields, "second"), number(fields, "amount"));
                }
            }
        }
        var picture = javax.imageio.ImageIO.read(new ByteArrayInputStream(baked.png()));
        var texture = new Texture2D(new AWTLoader().load(picture, true));
        // Drawn one texel to a pixel, where the nearest texel is the texel itself wherever the line starts.
        texture.setMagFilter(Texture.MagFilter.Nearest);
        texture.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        var page = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        page.setTexture("ColorMap", texture);
        page.setBoolean("VertexColor", true);
        page.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        var font = new BitmapFont();
        font.setCharSet(set);
        font.setPages(new Material[] {page});
        return font;
    }

    private static int number(Map<String, String> fields, String key) {
        var value = fields.get(key);
        return value == null ? 0 : Integer.parseInt(value);
    }
}
