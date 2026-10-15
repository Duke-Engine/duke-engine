package uz.dukeengine.client3d.rpg;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import uz.dukeengine.client3d.Canvas;

/** A canvas that writes down the pictures and the words drawn on it, with bars where a test puts them. */
final class Drawn implements Canvas {

    record Picture(String path, float x0, float y0, float x1, float y1) {
        int middleX() {
            return Math.round((x0 + x1) / 2f);
        }

        int middleY() {
            return Math.round((y0 + y1) / 2f);
        }
    }

    final List<Picture> pictures = new ArrayList<>();
    final List<String> text = new ArrayList<>();
    /** Each line drawn, by its words, and the colour it was drawn in. */
    final java.util.Map<String, Integer> colours = new java.util.HashMap<>();
    /** Where each thing's bar stands, by its id: nowhere, unless a test says. */
    IntFunction<Box> bars = id -> null;
    /** Where the client's panel stands: nowhere, unless a test says. */
    Box panel;

    void clear() {
        pictures.clear();
        text.clear();
    }

    Picture picture(String path) {
        return pictures.stream().filter(it -> it.path().equals(path)).findFirst().orElse(null);
    }

    @Override
    public int width() {
        return 1600;
    }

    @Override
    public int height() {
        return 900;
    }

    @Override
    public void drawImage(Image image, float x0, float y0, float x1, float y1, int argb, Blend blend) {
        pictures.add(new Picture(image.path(), x0, y0, x1, y1));
    }

    @Override
    public void fillRect(float x, float y, float w, float h, int argb) {
    }

    @Override
    public void openRect(float x, float y, float w, float h, float lineWidth, int argb) {
    }

    @Override
    public void fillTriangle(float x0, float y0, float x1, float y1, float x2, float y2, int argb) {
    }

    @Override
    public void line(float x0, float y0, float x1, float y1, float width, int argb) {
    }

    @Override
    public void line(float x0, float y0, float x1, float y1, float width, int argb, int argbEnd) {
    }

    @Override
    public void clip(float x0, float y0, float x1, float y1) {
    }

    @Override
    public void noClip() {
    }

    @Override
    public void drawText(Font font, String line, float x, float y, int argb) {
        text.add(line);
        colours.put(line, argb);
    }

    @Override
    public Measure measure(Font font, String line) {
        return new Measure(line.length() * 8, font.pixelHeight() + 4);
    }

    @Override
    public Box barOf(int id) {
        return bars.apply(id);
    }

    @Override
    public Box bottomPanel() {
        return panel;
    }
}
