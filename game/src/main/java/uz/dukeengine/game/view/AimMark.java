package uz.dukeengine.game.view;

/**
 * A rectangle of ground an aim's answer marks — the reference's bib laid under what refuses a building's place ({@code
 * W3DTerrainVisual::addFactionBib}): its centre, the way it faces in the simulation's degrees, and how far it reaches
 * ahead of the centre along that facing, behind it, and to either side.
 */
public record AimMark(float x, float y, float facing, float ahead, float behind, float side) {
}
