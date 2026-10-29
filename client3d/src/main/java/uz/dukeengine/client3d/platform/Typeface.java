package uz.dukeengine.client3d.platform;

/** One font, before a size is chosen. */
public interface Typeface {

    /** The font at this size, stretched by {@code scaleX}; with or without fractional metrics. */
    Face at(float size, boolean bold, float scaleX, boolean fractionalMetrics);
}
