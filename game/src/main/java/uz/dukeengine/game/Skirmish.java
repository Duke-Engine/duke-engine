package uz.dukeengine.game;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/** The skirmish catalogue — the maps and factions a player chooses from — and the choice made. */
final class Skirmish {

    private DukeGame.SkirmishAssembler assembler;
    private List<String> mapChoices = List.of();
    private List<String> factionChoices = List.of();
    private String chosenMap;
    private List<String> chosenFactions;
    private Map<String, String> mapPictures = Map.of();
    private boolean placesEverythingAtSetup;

    void catalogue(List<String> maps, List<String> factions, DukeGame.SkirmishAssembler assembler) {
        this.mapChoices = List.copyOf(maps);
        this.factionChoices = List.copyOf(factions);
        this.assembler = assembler;
    }

    List<String> mapChoices() {
        return mapChoices;
    }

    List<String> factionChoices() {
        return factionChoices;
    }

    void pictures(Map<String, String> byMapName) {
        this.mapPictures = byMapName == null ? Map.of() : Map.copyOf(byMapName);
    }

    String picture(String mapName) {
        return mapPictures.get(mapName);
    }

    void choose(String mapName, List<String> playerFactions) {
        this.chosenMap = mapName;
        this.chosenFactions = playerFactions == null ? null : List.copyOf(playerFactions);
    }

    String chosenMap() {
        return chosenMap;
    }

    List<String> chosenFactions() {
        return chosenFactions;
    }

    void placesEverythingAtSetup() {
        this.placesEverythingAtSetup = true;
    }

    /** Whether what stands in the match once it is set up is all it will ever draw. */
    boolean knowsItsArt() {
        return chosenMap != null || placesEverythingAtSetup;
    }

    /** Build the chosen match, if a catalogue gave an assembler. */
    void assemble(DukeGame game) {
        if (assembler != null) {
            assembler.assemble(game, chosenMap, chosenFactions);
        }
    }

    /** The host's choice as a wire token: {@code map|faction1|faction2…}. */
    String encode() {
        if (chosenMap == null && chosenFactions == null) {
            return "";
        }
        var joiner = new StringJoiner("|");
        joiner.add(urlEncode(chosenMap == null ? "" : chosenMap));
        if (chosenFactions != null) {
            for (var faction : chosenFactions) {
                joiner.add(urlEncode(faction));
            }
        }
        return joiner.toString();
    }

    /** Play the host's chosen match. */
    void apply(String spec) {
        if (spec == null || spec.isBlank()) {
            return;
        }
        var parts = spec.split("\\|", -1);
        var map = urlDecode(parts[0]);
        var factions = new ArrayList<String>();
        for (int i = 1; i < parts.length; i++) {
            factions.add(urlDecode(parts[i]));
        }
        choose(map.isEmpty() ? null : map, factions.isEmpty() ? null : factions);
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String urlDecode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
