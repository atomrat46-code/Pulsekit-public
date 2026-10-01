package pulsekit;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Shared song, style, MIDI and .sng logic for desktop and Android. */
public final class Engine {
  public static final int MAX_PLUGINS = 12;

  public static final class PlugFill {
    public String id;
    public String name;
    public int[][] cells;
  }

  public static final class Plugin {
    public String id = "plugin";
    public String name = "Plugin";
    public boolean enabled = true;
    public String rawJson = "{}";
    public final List<Style> styles = new ArrayList<>();
    public final List<PlugFill> fills = new ArrayList<>();
    public final List<String> ops = new ArrayList<>();
    public String scriptName;
    public String script;

    public String summary() {
      return styles.size() + " styles · " + fills.size() + " fills · " + ops.size() + " processors";
    }
  }

  public static Plugin examplePlugin() {
    Plugin p = new Plugin();
    p.id = "pulsekit.example.dub";
    p.name = "Dub Reggae Pack";
    p.styles.add(dubStyle());
    PlugFill f = new PlugFill();
    f.id = "dub-drop";
    f.name = "Dub drop";
    f.cells = dubFill();
    p.fills.add(f);
    p.ops.add("ghostHats");
    p.rawJson = "{\"format\":\"pulsekit-plugin\",\"v\":1,\"id\":\"pulsekit.example.dub\",\"name\":\"Dub Reggae Pack\""
        + ",\"styles\":[{\"id\":\"dub\",\"name\":\"Dub\",\"bpm\":76,\"pattern\":" + cellsJson(styleCells(dubStyle())) + "}]"
        + ",\"fills\":[{\"id\":\"dub-drop\",\"name\":\"Dub drop\",\"pattern\":" + cellsJson(dubFill()) + "}]"
        + ",\"processors\":[{\"id\":\"ghost-hats\",\"name\":\"Ghost hats\",\"ops\":[{\"op\":\"ghostHats\"}]}]}";
    return p;
  }

  public static Plugin decodePlugin(byte[] data) throws Exception {
    Map<String, byte[]> files;
    try {
      files = unzip(data);
    } catch (Exception ex) {
      files = new LinkedHashMap<>();
    }
    byte[] jsonBytes = files.get("plugin.json");
    if (jsonBytes == null) jsonBytes = data;
    String json = new String(jsonBytes, StandardCharsets.UTF_8);
    if (!json.contains("pulsekit-plugin")) throw new IllegalArgumentException("Could not read that plugin");
    Plugin p = new Plugin();
    p.rawJson = json;
    String id = jsonStr(json, "\"id\"");
    String name = jsonStr(json, "\"name\"");
    if (id != null && !id.isEmpty()) p.id = id;
    if (name != null && !name.isEmpty()) p.name = name;
    for (String obj : jsonObjects(json, "styles")) {
      String sid = jsonStr(obj, "\"id\"");
      String sname = jsonStr(obj, "\"name\"");
      int bpm = clamp(jsonInt(obj, "\"bpm\"", 120), 40, 240);
      int[][] cells = emptyCells();
      patternFromJson(obj, cells);
      if (sid == null || sid.isEmpty()) sid = "style";
      p.styles.add(new Style(sid, sname != null ? sname : sid, bpm, rowsFromCells(cells)));
    }
    for (String obj : jsonObjects(json, "fills")) {
      PlugFill f = new PlugFill();
      f.id = jsonStr(obj, "\"id\"");
      f.name = jsonStr(obj, "\"name\"");
      if (f.id == null) f.id = "fill";
      if (f.name == null) f.name = f.id;
      f.cells = emptyCells();
      patternFromJson(obj, f.cells);
      p.fills.add(f);
    }
    int at = 0;
    while (true) {
      int opAt = json.indexOf("\"op\"", at);
      if (opAt < 0) break;
      String op = jsonStr(json.substring(opAt), "\"op\"");
      if (op != null && !p.ops.contains(op)) p.ops.add(op);
      at = opAt + 4;
    }
    for (String k : files.keySet()) {
      if (k.startsWith("scripts/") && k.endsWith(".py")) {
        p.scriptName = k.substring(k.lastIndexOf('/') + 1);
        p.script = new String(files.get(k), StandardCharsets.UTF_8);
        break;
      }
    }
    if (p.styles.isEmpty()) {
      int[][] cells = emptyCells();
      patternFromJson(json, cells);
      int bpm = clamp(jsonInt(json, "\"bpm\"", 120), 40, 240);
      p.styles.add(new Style("plug", p.name, bpm, rowsFromCells(cells)));
    }
    return p;
  }

  public static void applyOps(int[][] cells, List<String> ops, java.util.Random rng, int steps) {
    if (ops == null || ops.isEmpty()) return;
    int n = clampSteps(steps);
    int kick = track("kick"), snare = track("snare"), chh = track("chh");
    for (String op : ops) {
      if ("ghostHats".equals(op)) ghostHats(cells, rng, n);
      else if ("dropHats".equals(op)) {
        for (int s = 0; s < n; s++) {
          if (s % 4 != 0 && cells[chh][s] > 0 && rng.nextDouble() < 0.45) cells[chh][s] = 0;
        }
      } else if ("extraKick".equals(op)) {
        for (int s = 0; s < n; s++) {
          if (s % 8 == 6 && cells[kick][s] <= 0 && rng.nextDouble() < 0.7) cells[kick][s] = 100;
        }
      } else if ("flamSnare".equals(op)) {
        for (int s = 1; s < n; s++) {
          if (cells[snare][s] >= 100 && cells[snare][s - 1] <= 0) cells[snare][s - 1] = 64;
        }
      } else if ("halveHats".equals(op)) {
        for (int s = 0; s < n; s++) if (s % 2 == 1) cells[chh][s] = 0;
      } else if ("boostKicks".equals(op)) {
        for (int s = 0; s < n; s++) {
          if (cells[kick][s] > 0) cells[kick][s] = Math.min(127, cells[kick][s] + 12);
        }
      }
    }
  }

  public static List<String> jsonObjects(String json, String arrayKey) {
    List<String> out = new ArrayList<>();
    int at = json.indexOf("\"" + arrayKey + "\"");
    if (at < 0) return out;
    int br = json.indexOf('[', at);
    if (br < 0) return out;
    int depth = 0;
    int start = -1;
    boolean inStr = false;
    boolean esc = false;
    for (int i = br + 1; i < json.length(); i++) {
      char c = json.charAt(i);
      if (inStr) {
        if (esc) esc = false;
        else if (c == '\\') esc = true;
        else if (c == '"') inStr = false;
        continue;
      }
      if (c == '"') {
        inStr = true;
        continue;
      }
      if (c == '{') {
        if (depth == 0) start = i;
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0 && start >= 0) {
          out.add(json.substring(start, i + 1));
          start = -1;
        }
      } else if (c == ']' && depth == 0) break;
    }
    return out;
  }

  public static String jsonStr(String json, String key) {
    int at = json.indexOf(key);
    if (at < 0) return null;
    int c = json.indexOf(':', at);
    if (c < 0) return null;
    int q = json.indexOf('"', c + 1);
    if (q < 0) return null;
    StringBuilder sb = new StringBuilder();
    for (int i = q + 1; i < json.length(); i++) {
      char ch = json.charAt(i);
      if (ch == '\\' && i + 1 < json.length()) {
        sb.append(json.charAt(i + 1));
        i++;
      } else if (ch == '"') break;
      else sb.append(ch);
    }
    return sb.toString();
  }

  public static int jsonInt(String json, String key, int fallback) {
    int at = json.indexOf(key);
    if (at < 0) return fallback;
    int c = json.indexOf(':', at);
    if (c < 0) return fallback;
    int i = c + 1;
    while (i < json.length() && (json.charAt(i) == ' ' || json.charAt(i) == '\n')) i++;
    int j = i;
    while (j < json.length() && "-0123456789".indexOf(json.charAt(j)) >= 0) j++;
    try {
      return Integer.parseInt(json.substring(i, j));
    } catch (Exception ex) {
      return fallback;
    }
  }

  public static float jsonFloat(String json, String key, float fallback) {
    int at = json.indexOf(key);
    if (at < 0) return fallback;
    int c = json.indexOf(':', at);
    if (c < 0) return fallback;
    int i = c + 1;
    while (i < json.length() && (json.charAt(i) == ' ' || json.charAt(i) == '\n')) i++;
    int j = i;
    while (j < json.length() && "-0123456789.eE".indexOf(json.charAt(j)) >= 0) j++;
    try {
      return Float.parseFloat(json.substring(i, j));
    } catch (Exception ex) {
      return fallback;
    }
  }

  public static void patternFromJson(String json, int[][] dest) {
    int at = json.indexOf("\"pattern\"");
    if (at < 0) return;
    for (int t = 0; t < TRACK_ID.length; t++) {
      String k = "\"" + TRACK_ID[t] + "\"";
      int p = json.indexOf(k, at);
      if (p < 0) continue;
      int br = json.indexOf('[', p);
      int cl = json.indexOf(']', br);
      if (br < 0 || cl < 0) continue;
      String[] nums = json.substring(br + 1, cl).split(",");
      for (int s = 0; s < MAX_STEPS && s < nums.length; s++) {
        try {
          dest[t][s] = Integer.parseInt(nums[s].trim());
        } catch (Exception ignored) { /* */ }
      }
    }
  }
  public static final int STEPS = 16;
  public static final int MAX_STEPS = 32;
  public static final int MIN_STEPS = 4;
  public static final int MIN_BPM = 40;
  public static final int MAX_BPM = 240;
  public static final int TPQ = 480;
  public static final int DRUM_CH = 9;
  public static final String[] TRACK_ID = {
    "kick", "dkick", "snare", "clap", "rim", "chh", "ohh", "crash", "ride", "ltom", "mtom", "htom"
  };
  public static final String[] TRACK_SHORT = {
    "KIK", "DBL", "SNR", "CLP", "RIM", "CHH", "OHH", "CSH", "RID", "FLO", "MID", "RAK"
  };
  public static final String[] TRACK_LABEL = {
    "Kick", "Double kick", "Snare", "Clap", "Rim", "Closed hat", "Open hat", "Crash", "Ride", "Floor tom", "Mid tom", "Rack tom"
  };
  public static final String[] FILL_ID = { "toms", "roll", "crash", "break", "rest" };
  public static final String[] FILL_LABEL = { "Toms", "Snare roll", "Crash", "Break", "Silent" };
  public static final int[] NOTES = {36, 35, 38, 39, 37, 42, 46, 49, 51, 41, 47, 50};

  public static final class Style {
    public final String id;
    public final String label;
    public final int bpm;
    public final String[] rows;

    Style(String id, String label, int bpm, String[] rows) {
      this.id = id;
      this.label = label;
      this.bpm = bpm;
      this.rows = rows;
    }
  }

  public static final class Part {
    public String kind = "groove";
    public String name = "Part";
    public int repeats = 4;
    public int bpm = 120;
    public int steps = STEPS;
    public int tsNum = 4;
    public int tsDen = 4;
    public int[][] cells = emptyCells();
    public int[][] lens = emptyCells();
  }

  public static final class ImportedSong {
    public String id;
    public String name;
    public final List<Part> parts = new ArrayList<>();
  }

  public static Map<String, Style> styles() {
    Map<String, Style> m = new LinkedHashMap<>();
    add(m, "house", "House", 124, "X---X---X---X---", "----------------", "----x-------x---", "----x-------x---", "----------------",
        "x-x-x-x-x-x-x-x-", "--x---x---x---x-", "x---------------", "----o-------o---", "----------------",
        "----------------", "--------------o-");
    add(m, "techno", "Techno", 132, "X---X---X---X---", "----------------", "----------------", "----x-------x---", "------x-------x-",
        "xxxxxxxxxxxxxxxx", "----------------", "x---------------", "x-x-x-x-x-x-x-x-", "------------x---",
        "----------------", "----------------");
    add(m, "hiphop", "Hip-Hop", 92, "X------x--x-----", "----------------", "----X-------X---", "----------------", "----------o-----",
        "x-x-x-x-x-x-x-x-", "------x-------x-", "x---------------", "----------------", "--------------o-",
        "----------------", "----------o-----");
    add(m, "trap", "Trap", 140, "X-----x------x-x", "----------------", "----X-------X---", "------------o---", "----------------",
        "x-xxx-x-x-xxx-x-", "------x-------x-", "x---------------", "--------o-------", "----------------",
        "--------------x-", "----------------");
    add(m, "rock", "Rock", 118, "X-------X-X-----", "----------------", "----X-------X---", "----------------", "----------------",
        "x-x-x-x-x-x-x-x-", "----------------", "x-------x-------", "x-x-x-x-x-x-x-x-", "--------------x-",
        "-------------x--", "------------x---");
    add(m, "hardrock", "Hard Rock", 128, "X---X---X---X-x-", "----------------", "----X-------X---", "----------------", "----------------",
        "x-x-x-x-x-x-x-x-", "------x-------x-", "x-------x-------", "x-x-x-x-x-x-x-x-", "--------------x-",
        "------------x---", "----------x-----");
    add(m, "metal", "Metal", 156, "X-X-X-X-X-X-X-X-", "----------------", "----X-------X---", "----------------", "----------------",
        "x-x-x-x-x-x-x-x-", "--------------x-", "x---------------", "x-x-x-x-x-x-x-x-", "--------------X-",
        "-------------x--", "------------x---");
    add(m, "progmetal", "Prog Metal", 148, "X--X--X-X--X--X-", "-XX-XX-X-XX-XX-X", "----X-------X---", "----------------", "----------------",
        "x-x-x-x-x-x-x-x-", "--------------x-", "x---------------", "----------------", "--------------X-",
        "------------x---", "----------x-----");
    add(m, "rockballad", "Rock Ballad", 72, "X-------X-------", "----------------", "----X-------X---", "----------------", "----------o-----",
        "x---x---x---x---", "------x-------x-", "x---------------", "----x-------x---", "------------o---",
        "----------------", "--------------o-");
    add(m, "metalballad", "Metal Ballad", 76, "X-------X-------", "----------------", "--------X-------", "----------------", "----------------",
        "x-x-x-x-x-x-x-x-", "--------------x-", "x---------------", "x-x-x-x-x-x-x-x-", "--------------X-",
        "-------------x--", "------------x---");
    add(m, "funk", "Funk", 108, "X--x--x-----x---", "----------------", "----X--o----X---", "----------------", "--o-------o-----",
        "x-x-x-x-x-x-x-x-", "------x-------x-", "x---------------", "------o-------o-", "----------------",
        "----------o-----", "------------o---");
    add(m, "breakbeat", "Breaks", 136, "X--x--x-----x---", "----------------", "----X--x-X--x---", "----------------", "----------------",
        "x-x-x-x-x-x-x-x-", "----------x-----", "x---------------", "--------x-------", "--------------o-",
        "------------x---", "----------x-----");
    add(m, "dnb", "DnB", 172, "X-----x---x-----", "----------------", "----X-------X---", "----------------", "----------------",
        "xxxxxxxxxxxxxxxx", "--x---x---x---x-", "x---------------", "x---x---x---x---", "----------x-----",
        "----------------", "--------------x-");
    add(m, "latin", "Latin", 100, "X------xX-------", "----------------", "----o-------o---", "------x---------", "--x--x----x--x--",
        "x-x-x-x-x-x-x-x-", "----------------", "x---------------", "x-x-x-x-x-x-x-x-", "x--x----x--x----",
        "----x-------x---", "--------------x-");
    add(m, "folk", "Folk", 98, "X---------------", "----------------", "----x-------x---", "----------------", "----x-------x---",
        "x---x---x---x---", "----------------", "----------------", "----------------", "----------------",
        "----------------", "----------------");
    add(m, "pop", "Pop", 110, "X-------X-------", "----------------", "----X-------X---", "----X-------X---", "----------------",
        "x-x-x-x-x-x-x-x-", "----------------", "x-------x-------", "----o-------o---", "------------o---",
        "----------------", "--------------x-");
    add(m, "popballad", "Pop Ballad", 70, "X-----------X---", "----------------", "----X-------X---", "----x-------x---", "----------------",
        "--x---x---x---x-", "------x---------", "x---------------", "--------o-------", "------------o---",
        "----------------", "--------------o-");
    add(m, "boombap", "Boom Bap", 88, "X------x--x-----", "----------------", "----X-------X---", "----------------", "----------o-----",
        "x---x---x---x---", "------x-------x-", "x---------------", "----------------", "--------------x-",
        "----------o-----", "----------------");
    add(m, "ukg", "UKG", 132, "X-----x-X-------", "----------------", "----X-------X---", "----x-------x---", "------o---------",
        "--x---x---x---x-", "x-------x-------", "x---------------", "--o-------o-----", "----------------",
        "----------------", "------------x---");
    return m;
  }

  private static void add(Map<String, Style> m, String id, String label, int bpm, String... rows) {
    m.put(id, new Style(id, label, bpm, rows));
  }

  public static int styleSwing(String id) {
    if (id != null && id.isEmpty()) return 0;  // no style: a file set taken from a file plays as written
    if ("techno".equals(id) || "hardrock".equals(id) || "metalballad".equals(id)) return 6;
    if ("hiphop".equals(id)) return 22;
    if ("trap".equals(id) || "ukg".equals(id)) return 18;
    if ("rock".equals(id) || "dnb".equals(id) || "popballad".equals(id)) return 8;
    if ("progmetal".equals(id)) return 2;
    if ("metal".equals(id)) return 4;
    if ("rockballad".equals(id) || "pop".equals(id)) return 10;
    if ("folk".equals(id)) return 6;
    if ("funk".equals(id)) return 28;
    if ("breakbeat".equals(id)) return 16;
    if ("latin".equals(id)) return 20;
    if ("boombap".equals(id)) return 24;
    return 12;
  }

  public static int styleDensity(String id) {
    if ("dnb".equals(id) || "progmetal".equals(id)) return 8;
    if ("techno".equals(id) || "metal".equals(id) || "breakbeat".equals(id)) return 7;
    if ("trap".equals(id) || "hardrock".equals(id) || "funk".equals(id) || "latin".equals(id) || "ukg".equals(id)) return 6;
    if ("rockballad".equals(id) || "popballad".equals(id)) return 3;
    if ("hiphop".equals(id) || "boombap".equals(id) || "metalballad".equals(id)) return 4;
    return 5;
  }

  public static int styleHuman(String id) {
    if (id != null && id.isEmpty()) return 0;
    if ("boombap".equals(id)) return 30;
    if ("hiphop".equals(id)) return 28;
    if ("rockballad".equals(id) || "popballad".equals(id)) return 24;
    if ("trap".equals(id) || "funk".equals(id)) return 22;
    if ("breakbeat".equals(id)) return 20;
    if ("house".equals(id) || "metalballad".equals(id) || "latin".equals(id)) return 18;
    if ("rock".equals(id) || "pop".equals(id) || "ukg".equals(id)) return 16;
    if ("hardrock".equals(id)) return 14;
    if ("dnb".equals(id)) return 12;
    if ("progmetal".equals(id)) return 8;
    if ("techno".equals(id) || "metal".equals(id)) return 10;
    return 18;
  }

  /** Patterns of one file set. Names need only be unique there: its song finds parts by name. */
  public static List<Learned> learnedFrom(List<Learned> list, String source) {
    List<Learned> out = new ArrayList<Learned>();
    if (list != null) for (Learned x : list) if (source != null && source.equals(sourceOf(x))) out.add(x);
    return out;
  }

  public static List<LearnedFill> fillsFrom(List<LearnedFill> list, String source) {
    List<LearnedFill> out = new ArrayList<LearnedFill>();
    if (list != null) for (LearnedFill x : list) if (source != null && source.equals(sourceOf(x))) out.add(x);
    return out;
  }

  public static String uniqueFillName(String base, List<LearnedFill> list) {
    String stem = base == null || base.isEmpty() ? "Var" : base.trim();
    if (stem.length() > 22) stem = stem.substring(0, 22);
    boolean used = false;
    for (LearnedFill x : list) if (stem.equals(x.name)) used = true;
    if (!used) return stem;
    for (int i = 2; i < 99; i++) {
      String n = stem + " " + i;
      if (n.length() > 28) n = n.substring(0, 28);
      boolean hit = false;
      for (LearnedFill x : list) if (n.equals(x.name)) { hit = true; break; }
      if (!hit) return n;
    }
    return stem;
  }

  public static String uniqueLearnedName(String base, List<Learned> list) {
    String stem = base == null || base.isEmpty() ? "Copy" : base.trim();
    if (stem.length() > 22) stem = stem.substring(0, 22);
    boolean used = false;
    for (Learned x : list) if (stem.equals(x.name)) used = true;
    if (!used) return stem;
    for (int i = 2; i < 99; i++) {
      String n = stem + " " + i;
      if (n.length() > 28) n = n.substring(0, 28);
      boolean hit = false;
      for (Learned x : list) if (n.equals(x.name)) { hit = true; break; }
      if (!hit) return n;
    }
    return stem;
  }

  public static String uniqueImportedName(String base, List<ImportedSong> list) {
    String stem = base == null || base.isEmpty() ? "Import" : base.trim();
    if (stem.length() > 22) stem = stem.substring(0, 22);
    boolean used = false;
    for (ImportedSong x : list) if (stem.equals(x.name)) used = true;
    if (!used) return stem;
    for (int i = 2; i < 99; i++) {
      String n = stem + " " + i;
      if (n.length() > 28) n = n.substring(0, 28);
      boolean hit = false;
      for (ImportedSong x : list) if (n.equals(x.name)) { hit = true; break; }
      if (!hit) return n;
    }
    return stem;
  }

  public static int[][] emptyCells() {
    return new int[TRACK_ID.length][MAX_STEPS];
  }

  public static void zeroCells(int[][] cells) {
    if (cells == null) return;
    for (int t = 0; t < cells.length; t++) {
      if (cells[t] == null) continue;
      for (int s = 0; s < cells[t].length; s++) cells[t][s] = 0;
    }
  }

  public static int[][] rowsToCells(String[] rows) {
    int[][] cells = emptyCells();
    for (int t = 0; t < TRACK_ID.length; t++) {
      String row = t < rows.length ? rows[t] : "----------------";
      for (int i = 0; i < STEPS; i++) {
        char c = i < row.length() ? row.charAt(i) : '-';
        cells[t][i] = c == 'X' ? 127 : c == 'x' ? 100 : c == 'o' ? 64 : 0;
      }
    }
    return cells;
  }

  public static int[][] copyCells(int[][] src) {
    int[][] d = emptyCells();
    if (src == null) return d;
    for (int t = 0; t < TRACK_ID.length; t++) {
      int n = Math.min(MAX_STEPS, src[t].length);
      System.arraycopy(src[t], 0, d[t], 0, n);
    }
    return d;
  }

  public static int usedSteps(int[][] cells) {
    if (cells == null || cells.length == 0) return STEPS;
    for (int s = MAX_STEPS - 1; s >= STEPS; s--) {
      for (int t = 0; t < TRACK_ID.length; t++) {
        if (s < cells[t].length && cells[t][s] > 0) return MAX_STEPS;
      }
    }
    return STEPS;
  }

  public static int patternLenFromCells(int[][] cells, int tsNum, int tsDen) {
    int bar = barSteps(tsNum, tsDen);
    if (cells == null) return bar;
    int two = bar * 2;
    if (two > MAX_STEPS) return bar;
    for (int s = bar; s < two; s++) {
      for (int t = 0; t < TRACK_ID.length; t++) {
        if (s < cells[t].length && cells[t][s] > 0) return two;
      }
    }
    return bar;
  }

  public static void tileSteps(int[][] cells, int from, int to) {
    for (int t = 0; t < TRACK_ID.length; t++) {
      for (int s = from; s < to && s < cells[t].length; s++) {
        cells[t][s] = cells[t][s % from];
      }
    }
  }

  public static int[][] tomsFill() {
    return rowsToCells(new String[] {
      "----------------", "----------------", "------------o---", "----------------", "----------------",
      "------------x-x-", "--------------x-", "--------------X-", "----------------",
      "--------xxxx----", "----xxxx--------", "xxxx------------",
    });
  }

  public static int[][] fillCells(String id) {
    if (id == null) return tomsFill();
    if (id.startsWith("l:") || id.startsWith("p:")) return tomsFill();
    if ("roll".equals(id)) {
      return rowsToCells(new String[] {
        "X-------X------X", "----------------", "----X---xxxxxxxx", "----------------", "----------------",
        "x-x-x-x---------", "----------------", "---------------X", "----------------",
        "----------------", "----------------", "----------------",
      });
    }
    if ("crash".equals(id)) {
      return rowsToCells(new String[] {
        "X-------X------X", "----------------", "----X-------X---", "----------------", "----------------",
        "----------------", "--------------x-", "x-------x------X", "x-x-x-x-x-x-----",
        "----------------", "----------------", "----------------",
      });
    }
    if ("break".equals(id)) {
      return rowsToCells(new String[] {
        "X---------------", "----------------", "----X-----------", "----------------", "----------------",
        "x-x-x-x---------", "----------------", "---------------X", "----------------",
        "----------------", "----------------", "----------------",
      });
    }
    if ("rest".equals(id)) return emptyCells();
    return tomsFill();
  }

  public static int track(String id) {
    for (int i = 0; i < TRACK_ID.length; i++) if (TRACK_ID[i].equals(id)) return i;
    return -1;
  }

  static int[][] groove16(int[][] src) {
    int[][] out = emptyCells();
    if (src == null) return out;
    int len = src[0] != null ? src[0].length : STEPS;
    int start = len >= MAX_STEPS ? len - STEPS : 0;
    for (int t = 0; t < TRACK_ID.length; t++) {
      if (src[t] == null) continue;
      int n = src[t].length;
      if (n <= 0) continue;
      for (int i = 0; i < STEPS; i++) {
        int idx = n >= MAX_STEPS ? start + i : i % n;
        if (idx >= 0 && idx < n) out[t][i] = src[t][idx];
      }
    }
    return out;
  }

  static boolean evenGrid(int[][] p) {
    int even = 0, odd = 0;
    int kick = track("kick"), snare = track("snare"), chh = track("chh"), ride = track("ride");
    for (int i = 0; i < STEPS; i++) {
      int e = at(p, kick, i) + at(p, snare, i) + at(p, chh, i) + at(p, ride, i);
      if ((i & 1) == 0) even += e;
      else odd += e;
    }
    return odd * 2 < even;
  }

  static int at(int[][] p, int t, int i) {
    if (t < 0 || p == null || t >= p.length || p[t] == null || i < 0 || i >= p[t].length) return 0;
    return p[t][i];
  }

  static void set(int[][] p, int t, int i, int v) {
    if (t < 0 || p == null || t >= p.length || p[t] == null || i < 0 || i >= p[t].length) return;
    p[t][i] = v;
  }

  static boolean pulse(int[][] p, int i) {
    return at(p, track("kick"), i) >= 80 || at(p, track("snare"), i) >= 90 || at(p, track("clap"), i) >= 90;
  }

  static void clearToms(int[][] p, int from) {
    int h = track("htom"), m = track("mtom"), l = track("ltom");
    for (int i = from; i < STEPS; i++) {
      set(p, h, i, 0);
      set(p, m, i, 0);
      set(p, l, i, 0);
    }
  }

  static void thinHats(int[][] p, int from) {
    int chh = track("chh"), ohh = track("ohh"), ride = track("ride");
    for (int i = from; i < STEPS; i++) {
      if (i >= 12) {
        set(p, chh, i, 0);
        set(p, ride, i, 0);
        if (i < 15) set(p, ohh, i, 0);
      } else if (at(p, chh, i) > 0) {
        set(p, chh, i, Math.min(at(p, chh, i), 70));
      }
    }
  }

  static void putTom(int[][] p, int i, String id, int v) {
    if (i < 0 || i > 15) return;
    if (pulse(p, i) && i != 15) return;
    set(p, track(id), i, v);
  }

  static void overlayToms(int[][] p, String style) {
    clearToms(p, 8);
    boolean trap = "trap".equals(style);
    boolean sparse = "rockballad".equals(style) || "metalballad".equals(style) || "popballad".equals(style) || "pop".equals(style);
    boolean dense = "metal".equals(style) || "progmetal".equals(style) || "dnb".equals(style);
    thinHats(p, trap ? 13 : 8);
    if (trap) {
      putTom(p, 13, "mtom", 100);
      putTom(p, 14, "ltom", 110);
      putTom(p, 15, "ltom", 118);
    } else if (sparse) {
      putTom(p, 11, "htom", 96);
      putTom(p, 13, "mtom", 104);
      putTom(p, 14, "ltom", 110);
      putTom(p, 15, "ltom", 118);
    } else if (dense) {
      putTom(p, 8, "htom", 92);
      putTom(p, 9, "htom", 100);
      putTom(p, 10, "mtom", 96);
      putTom(p, 11, "mtom", 108);
      putTom(p, 12, "ltom", 100);
      putTom(p, 13, "ltom", 108);
      putTom(p, 14, "ltom", 114);
      putTom(p, 15, "ltom", 120);
    } else if (evenGrid(p)) {
      putTom(p, 10, "htom", 100);
      putTom(p, 12, "mtom", 104);
      putTom(p, 13, "mtom", 96);
      putTom(p, 14, "ltom", 112);
      putTom(p, 15, "ltom", 118);
    } else {
      putTom(p, 9, "htom", 92);
      putTom(p, 10, "htom", 100);
      putTom(p, 11, "mtom", 96);
      putTom(p, 12, "mtom", 108);
      putTom(p, 13, "ltom", 100);
      putTom(p, 14, "ltom", 112);
      putTom(p, 15, "ltom", 118);
    }
    int crash = track("crash");
    set(p, crash, 15, Math.max(at(p, crash, 15), 118));
  }

  static void overlayRoll(int[][] p) {
    thinHats(p, 8);
    clearToms(p, 8);
    boolean even = evenGrid(p);
    int snare = track("snare");
    for (int i = 8; i < STEPS; i++) {
      int grow = 52 + (i - 8) * 9;
      if (even && (i & 1) == 1) {
        if (at(p, snare, i) < 1) set(p, snare, i, 46);
      } else {
        set(p, snare, i, Math.max(at(p, snare, i), Math.min(127, grow)));
      }
    }
    int crash = track("crash");
    set(p, crash, 15, Math.max(at(p, crash, 15), 118));
  }

  static void overlayCrash(int[][] p) {
    int kick = track("kick"), crash = track("crash"), ohh = track("ohh"), chh = track("chh"), ride = track("ride");
    if (at(p, kick, 8) >= 80) set(p, crash, 8, Math.max(at(p, crash, 8), 100));
    set(p, ohh, 14, Math.max(at(p, ohh, 14), 88));
    set(p, chh, 14, 0);
    set(p, chh, 15, 0);
    set(p, ride, 14, 0);
    set(p, ride, 15, 0);
    set(p, crash, 15, Math.max(at(p, crash, 15), 120));
  }

  static void overlayBreak(int[][] p) {
    int kick = track("kick"), snare = track("snare"), clap = track("clap");
    int rim = track("rim"), chh = track("chh"), ohh = track("ohh"), ride = track("ride"), crash = track("crash");
    for (int i = 8; i < STEPS; i++) {
      boolean keepKick = i % 4 == 0 && at(p, kick, i) >= 80;
      boolean keepSnare = i == 12 && at(p, snare, i) >= 80;
      boolean keepClap = i == 12 && at(p, clap, i) >= 80;
      if (!keepKick) set(p, kick, i, 0);
      if (!keepSnare) set(p, snare, i, 0);
      if (!keepClap) set(p, clap, i, 0);
      set(p, rim, i, 0);
      set(p, chh, i, 0);
      set(p, ohh, i, 0);
      set(p, ride, i, 0);
      set(p, track("htom"), i, 0);
      set(p, track("mtom"), i, 0);
      set(p, track("ltom"), i, 0);
      if (i != 15) set(p, crash, i, 0);
    }
    set(p, crash, 15, Math.max(at(p, crash, 15), 118));
  }

  /** Groove-locked fill: keep kick/snare pulse, layer toms/roll on the last half-bar. */
  public static int[][] buildFill(String fillId, int[][] groove) {
    if ("rest".equals(fillId)) return emptyCells();
    int[][] out = groove16(groove);
    if ("roll".equals(fillId)) overlayRoll(out);
    else if ("crash".equals(fillId)) overlayCrash(out);
    else if ("break".equals(fillId)) overlayBreak(out);
    else overlayToms(out, null);
    return out;
  }

  public static int[][] buildFill(String fillId, int[][] groove, String style) {
    if ("rest".equals(fillId)) return emptyCells();
    int[][] out = groove16(groove);
    if ("roll".equals(fillId)) overlayRoll(out);
    else if ("crash".equals(fillId)) overlayCrash(out);
    else if ("break".equals(fillId)) overlayBreak(out);
    else overlayToms(out, style);
    return out;
  }

  public static String fillLabel(String id) {
    for (int i = 0; i < FILL_ID.length; i++) {
      if (FILL_ID[i].equals(id)) return FILL_LABEL[i];
    }
    return "Fill";
  }

  public static Part groove(String name, int bpm, int[][] cells, int repeats) {
    return groove(name, bpm, cells, repeats, null);
  }

  public static Part groove(String name, int bpm, int[][] cells, int repeats, int[][] gates) {
    Part p = new Part();
    p.kind = "groove";
    p.name = name;
    p.bpm = clampBpm(bpm);
    p.repeats = clamp(repeats, 1, 32);
    p.cells = copyCells(cells);
    p.lens = copyCells(gates);
    p.steps = clampSteps(usedSteps(cells));
    return p;
  }

  public static Part fill(String name, int bpm, int repeats) {
    return fill(name, bpm, fillCells("toms"), repeats, null);
  }

  public static Part fill(String name, int bpm, int[][] cells, int repeats) {
    return fill(name, bpm, cells, repeats, null);
  }

  public static Part fill(String name, int bpm, int[][] cells, int repeats, int[][] gates) {
    Part p = new Part();
    p.kind = "fill";
    p.name = name;
    p.bpm = clampBpm(bpm);
    p.repeats = clamp(repeats, 1, 32);
    p.cells = cells != null ? copyCells(cells) : fillCells("toms");
    p.lens = copyCells(gates);
    p.steps = clampSteps(usedSteps(p.cells));
    return p;
  }

  public static Part rest(int bpm, int repeats) {
    Part p = new Part();
    p.kind = "rest";
    p.name = "Silent";
    p.bpm = clampBpm(bpm);
    p.repeats = clamp(repeats, 1, 32);
    p.cells = emptyCells();
    return p;
  }

  public static int partStepCount(Part p) {
    if (p == null) return STEPS;
    return clampSteps(p.steps <= 0 ? STEPS : p.steps);
  }

  public static float songDurationSec(List<Part> parts) {
    float n = 0;
    if (parts == null) return 0;
    for (Part p : parts) {
      n += Math.max(1, p.repeats) * partStepCount(p) * (60f / Math.max(MIN_BPM, p.bpm) / 4f);
    }
    return n;
  }

  public static int repeatsForDuration(float durationSec, int bpm, int steps) {
    float one = Math.max(1, steps) * (60f / Math.max(MIN_BPM, bpm) / 4f);
    if (!(durationSec > 0) || !(one > 0)) return 1;
    int n = Math.round(durationSec / one);
    return n < 1 ? 1 : n;
  }

  public static List<Part> fitSongToDuration(List<Part> parts, float durationSec) {
    if (parts == null || parts.isEmpty() || !(durationSec > 0)) return parts;
    float err = Math.abs(songDurationSec(parts) - durationSec);
    for (;;) {
      if (err < 0.25f) break;
      if (songDurationSec(parts) >= durationSec) break;
      int i = songFlexIndex(parts);
      Part p = parts.get(i);
      if (p.repeats < 32) {
        p.repeats++;
        float next = Math.abs(songDurationSec(parts) - durationSec);
        if (next > err) { p.repeats--; break; }
        err = next;
        continue;
      }
      if (parts.size() >= MAX_SONG) break;
      Part extra = copyPart(p);
      extra.repeats = 1;
      parts.add(extra);
      float next = Math.abs(songDurationSec(parts) - durationSec);
      if (next > err) { parts.remove(parts.size() - 1); break; }
      err = next;
    }
    for (;;) {
      if (err < 0.25f) break;
      if (songDurationSec(parts) <= durationSec) break;
      int i = songFlexIndex(parts);
      Part p = parts.get(i);
      if (p.repeats > 1) {
        p.repeats--;
        float next = Math.abs(songDurationSec(parts) - durationSec);
        if (next > err) { p.repeats++; break; }
        err = next;
        continue;
      }
      break;
    }
    return parts;
  }

  private static int songFlexIndex(List<Part> parts) {
    for (int i = parts.size() - 1; i >= 0; i--) {
      String k = parts.get(i).kind == null ? "" : parts.get(i).kind.toLowerCase();
      if ("groove".equals(k) || "rest".equals(k)) return i;
    }
    return parts.size() - 1;
  }

  private static Part copyPart(Part src) {
    Part p = new Part();
    if (src == null) return p;
    p.kind = src.kind;
    p.name = src.name;
    p.repeats = src.repeats;
    p.bpm = src.bpm;
    p.steps = src.steps;
    p.tsNum = src.tsNum;
    p.tsDen = src.tsDen;
    p.cells = copyCells(src.cells);
    p.lens = copyCells(src.lens);
    return p;
  }

  public static int songGlobalStep(List<Part> parts, int index, int loop, int step) {
    int n = 0;
    if (parts == null) return 0;
    for (int i = 0; i < parts.size(); i++) {
      Part p = parts.get(i);
      int st = partStepCount(p);
      int times = Math.max(1, p.repeats);
      if (i < index) {
        n += times * st;
        continue;
      }
      if (i == index) {
        int lp = Math.max(0, Math.min(times - 1, loop));
        int s = Math.max(0, Math.min(st - 1, step < 0 ? 0 : step));
        n += lp * st + s;
      }
      break;
    }
    return n;
  }

  public static float songElapsedSec(List<Part> parts, int globalStep) {
    if (parts == null || parts.isEmpty()) return 0;
    int remain = Math.max(0, globalStep);
    float n = 0;
    for (Part p : parts) {
      int st = partStepCount(p);
      int partSteps = Math.max(1, p.repeats) * st;
      float stepSec = 60f / Math.max(MIN_BPM, p.bpm) / 4f;
      if (remain >= partSteps) {
        n += partSteps * stepSec;
        remain -= partSteps;
      } else {
        n += remain * stepSec;
        break;
      }
    }
    return n;
  }

  public static String fmtSongTime(float sec) {
    int s = Math.max(0, Math.round(sec));
    int m = s / 60;
    int r = s % 60;
    return m + ":" + (r < 10 ? "0" : "") + r;
  }

  public static String songNowLine(List<Part> parts, boolean playing, int partIndex, int globalStep) {
    if (parts == null || parts.isEmpty()) return "Empty song";
    String total = fmtSongTime(songDurationSec(parts));
    if (!playing || partIndex < 0 || partIndex >= parts.size()) {
      return parts.size() + " parts · " + total;
    }
    Part p = parts.get(partIndex);
    String name = p.name == null || p.name.isEmpty() ? "Part" : p.name;
    return name + " · " + fmtSongTime(songElapsedSec(parts, globalStep)) + " / " + total;
  }

  public static int clampBpm(int n) {
    return clamp(n, MIN_BPM, MAX_BPM);
  }

  public static int clampTsNum(int n) {
    return clamp(n, 1, 16);
  }

  public static int clampTsDen(int n) {
    if (n == 2 || n == 8 || n == 16) return n;
    return 4;
  }

  public static int stepsPerBeat(int den) {
    return Math.max(1, 16 / clampTsDen(den));
  }

  /** Milliseconds between the two strokes of a Double-kick pad tap. */
  public static int doubleKickDelayMs(int bpm, int tsDen) {
    double step = 60.0 / clampBpm(bpm) / 4.0;
    double beat = step * stepsPerBeat(tsDen);
    double gap = Math.max(step, beat / 4.0);
    return Math.max(20, (int) Math.round(gap * 1000.0));
  }

  /** Double kick shares the kick sample. */
  public static int soundTrack(int t) {
    return t == track("dkick") ? track("kick") : t;
  }

  public static int clampNoteLength(int n) {
    if (n < 1) return 1;
    return Math.max(1, Math.min(16, n));
  }

  public static int noteLengthAt(int[] row, int step) {
    if (row == null || step < 0 || step >= row.length) return 1;
    int n = row[step];
    return n <= 1 ? 1 : clampNoteLength(n);
  }

  public static String noteLengthLabel(int steps) {
    int n = clampNoteLength(steps);
    if (n <= 1) return "16th";
    if (n == 2) return "8th";
    if (n == 4) return "Quarter";
    if (n == 8) return "Half";
    if (n == 16) return "Whole";
    return n + " steps";
  }

  public static boolean lengthCoveredAt(int[] gates, int[] pattern, int at) {
    if (pattern == null || at <= 0) return false;
    int start = Math.max(0, at - 16);
    for (int j = at - 1; j >= start; j--) {
      if (j >= pattern.length || pattern[j] <= 0) continue;
      if (j + noteLengthAt(gates, j) > at) return true;
    }
    return false;
  }

  public static int barSteps(int num, int den) {
    int steps = (int) Math.round(clampTsNum(num) * (16.0 / clampTsDen(den)));
    return clamp(steps <= 0 ? STEPS : steps, MIN_STEPS, MAX_STEPS);
  }

  public static boolean isDoubled(int steps, int num, int den) {
    int bar = barSteps(num, den);
    return steps == bar * 2 && bar * 2 <= MAX_STEPS;
  }

  public static int patternSteps(int num, int den, boolean doubled) {
    int bar = barSteps(num, den);
    if (doubled && bar * 2 <= MAX_STEPS) return bar * 2;
    return bar;
  }

  public static int clampSteps(int n) {
    return clamp(n, MIN_STEPS, MAX_STEPS);
  }

  public static final int[] LEN_STEPS = {1, 2, 4, 8, 16};
  public static final String[] LEN_LABEL = {"16th", "8th", "Quarter", "Half", "Whole"};

  public static int clampLen(int n) {
    if (n < 1) return 1;
    return clamp(n, 1, 16);
  }

  public static int gateTicks(int len) {
    int n = clampLen(len);
    if (n <= 1) return 80;
    return Math.max(80, n * (TPQ / 4) - 12);
  }

  public static int lenAt(int[][] lens, int t, int s) {
    if (lens == null || t < 0 || t >= lens.length || lens[t] == null) return 1;
    if (s < 0 || s >= lens[t].length) return 1;
    int n = lens[t][s];
    return n <= 1 ? 1 : clampLen(n);
  }

  public static String lenMark(int len) {
    int n = clampLen(len);
    if (n <= 1) return "";
    if (n == 2) return "8n";
    if (n == 4) return "4n";
    if (n == 8) return "2n";
    if (n == 16) return "1n";
    return Integer.toString(n);
  }

  public static int accentVel(int vel, boolean acc) {
    if (vel <= 0) return 0;
    if (acc) return Math.min(127, Math.max(vel, 110));
    return Math.max(1, vel * 58 / 100);
  }

  public static int lenHue(int len) {
    int n = clampLen(len);
    if (n >= 16) return 0xE06B6B;
    if (n >= 8) return 0xA78BFA;
    if (n >= 4) return 0xD9A441;
    if (n >= 2) return 0x3DB8C8;
    return 0x9AAB9C;
  }

  public static int lenColor(int len, int vel) {
    int rgb = lenHue(len);
    int r = (rgb >> 16) & 0xff;
    int g = (rgb >> 8) & 0xff;
    int b = rgb & 0xff;
    int bgR = 0x1B, bgG = 0x1D, bgB = 0x1F;
    float a = vel >= 90 ? 1f : vel > 0 ? 0.52f : 0.34f;
    r = Math.round(bgR + (r - bgR) * a);
    g = Math.round(bgG + (g - bgG) * a);
    b = Math.round(bgB + (b - bgB) * a);
    return 0xFF000000 | (r << 16) | (g << 8) | b;
  }

  public static int coverLen(int[][] lens, int[][] cells, int t, int s) {
    if (cells == null || t < 0 || t >= cells.length || cells[t] == null) return 1;
    int start = Math.max(0, s - 16);
    for (int j = s - 1; j >= start; j--) {
      if (cells[t][j] > 0 && j + lenAt(lens, t, j) > s) return lenAt(lens, t, j);
    }
    return 1;
  }

  public static int fitSteps(int steps, int num, int den) {
    int tsNum = clampTsNum(num);
    int tsDen = clampTsDen(den);
    return patternSteps(tsNum, tsDen, isDoubled(clampSteps(steps), tsNum, tsDen));
  }

  public static int midiTsDenExp(int den) {
    int d = clampTsDen(den);
    if (d == 2) return 1;
    if (d == 8) return 3;
    if (d == 16) return 4;
    return 2;
  }

  public static int denFromMidiExp(int exp) {
    if (exp == 1) return 2;
    if (exp == 3) return 8;
    if (exp == 4) return 16;
    return 4;
  }

  public static void defaultAccents(boolean[] acc, int steps, int beat) {
    int b = Math.max(1, beat);
    int n = clampSteps(steps);
    for (int i = 0; i < acc.length; i++) acc[i] = i < n && (i % b == 0);
  }

  public static int clamp(int n, int lo, int hi) {
    return Math.max(lo, Math.min(hi, n));
  }

  public static String sngFilename(List<Part> parts) {
    String stem = parts.isEmpty() ? "song" : parts.get(0).name;
    return stem.replaceAll("[/\\\\?%*:|\"<>]", " ").trim() + ".sng";
  }

  public static byte[] encodeSng(List<Part> parts, String name) {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"format\":\"pulsekit-sng\",\"v\":1,\"name\":").append(quote(name));
    sb.append(",\"parts\":").append(partsJson(parts)).append('}');
    String json = "PKSNG1\n" + sb;
    return json.getBytes(StandardCharsets.UTF_8);
  }

  public static String partsJson(List<Part> parts) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < parts.size(); i++) {
      if (i > 0) sb.append(',');
      Part p = parts.get(i);
      sb.append("{\"kind\":").append(quote(p.kind));
      sb.append(",\"name\":").append(quote(p.name));
      sb.append(",\"repeats\":").append(p.repeats);
      sb.append(",\"bpm\":").append(p.bpm);
      sb.append(",\"steps\":").append(p.steps);
      sb.append(",\"tsNum\":").append(p.tsNum);
      sb.append(",\"tsDen\":").append(p.tsDen);
      sb.append(",\"pattern\":{");
      for (int t = 0; t < TRACK_ID.length; t++) {
        if (t > 0) sb.append(',');
        sb.append(quote(TRACK_ID[t])).append(":[");
        int n = clampSteps(p.steps);
        for (int s = 0; s < n; s++) {
          if (s > 0) sb.append(',');
          sb.append(p.cells[t][s]);
        }
        sb.append(']');
      }
      sb.append("},\"accents\":[");
      int beat = stepsPerBeat(p.tsDen);
      int accN = clampSteps(p.steps);
      for (int s = 0; s < accN; s++) {
        if (s > 0) sb.append(',');
        sb.append(s % beat == 0 ? "true" : "false");
      }
      sb.append("],\"lengths\":{");
      for (int t = 0; t < TRACK_ID.length; t++) {
        if (t > 0) sb.append(',');
        sb.append(quote(TRACK_ID[t])).append(":[");
        int n = clampSteps(p.steps);
        for (int s = 0; s < n; s++) {
          if (s > 0) sb.append(',');
          int g = p.lens != null && s < p.lens[t].length ? p.lens[t][s] : 0;
          sb.append(g > 1 ? clampNoteLength(g) : 0);
        }
        sb.append(']');
      }
      sb.append("}}");
    }
    return sb.append(']').toString();
  }

  public static String importedSongsJson(List<ImportedSong> list) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < list.size(); i++) {
      if (i > 0) sb.append(',');
      ImportedSong s = list.get(i);
      sb.append("{\"id\":").append(quote(s.id));
      sb.append(",\"name\":").append(quote(s.name));
      sb.append(",\"parts\":").append(partsJson(s.parts)).append('}');
    }
    return sb.append(']').toString();
  }

  public static List<ImportedSong> decodeImportedSongs(String json) {
    List<ImportedSong> out = new ArrayList<>();
    for (String obj : jsonObjects(json, "importedSongs")) {
      ImportedSong s = new ImportedSong();
      s.id = jsonStr(obj, "\"id\"");
      s.name = jsonStr(obj, "\"name\"");
      if (s.id == null || s.id.isEmpty()) s.id = newLearnedId();
      if (s.name == null || s.name.isEmpty()) s.name = "Import";
      s.parts.addAll(decodeSng(obj.getBytes(StandardCharsets.UTF_8)));
      if (!s.parts.isEmpty()) out.add(s);
      if (out.size() >= MAX_IMPORTED_SONGS) break;
    }
    return out;
  }

  public static List<Part> decodeSng(byte[] bytes) {
    String text = new String(bytes, StandardCharsets.UTF_8);
    if (text.startsWith("PKSNG1")) {
      int nl = text.indexOf('\n');
      text = nl >= 0 ? text.substring(nl + 1) : text.substring(6);
    }
    List<Part> out = new ArrayList<>();
    int idx = 0;
    while (true) {
      int k = text.indexOf("\"kind\"", idx);
      if (k < 0) break;
      Part p = new Part();
      p.kind = extractString(text, text.indexOf(':', k) + 1);
      int n = text.indexOf("\"name\"", k);
      p.name = n > 0 ? extractString(text, text.indexOf(':', n) + 1) : "Part";
      p.repeats = extractInt(text, "\"repeats\"", k, 1);
      p.bpm = clampBpm(extractInt(text, "\"bpm\"", k, 120));
      p.tsNum = clampTsNum(extractInt(text, "\"tsNum\"", k, 4));
      p.tsDen = clampTsDen(extractInt(text, "\"tsDen\"", k, 4));
      p.steps = fitSteps(extractInt(text, "\"steps\"", k, STEPS), p.tsNum, p.tsDen);
      p.cells = emptyCells();
      p.lens = emptyCells();
      for (int t = 0; t < TRACK_ID.length; t++) {
        String key = "\"" + TRACK_ID[t] + "\"";
        int at = text.indexOf(key, k);
        int nextPart = text.indexOf("\"kind\"", k + 6);
        if (at < 0 || (nextPart > 0 && at > nextPart)) continue;
        int br = text.indexOf('[', at);
        int cl = text.indexOf(']', br);
        if (br < 0 || cl < 0) continue;
        String[] nums = text.substring(br + 1, cl).split(",");
        for (int s = 0; s < MAX_STEPS && s < nums.length; s++) {
          try {
            p.cells[t][s] = Integer.parseInt(nums[s].trim());
          } catch (NumberFormatException ignored) {
            /* */
          }
        }
      }
      int lenAt = text.indexOf("\"lengths\"", k);
      int nextKind = text.indexOf("\"kind\"", k + 6);
      if (lenAt > 0 && (nextKind < 0 || lenAt < nextKind)) {
        for (int t = 0; t < TRACK_ID.length; t++) {
          String key = "\"" + TRACK_ID[t] + "\"";
          int at = text.indexOf(key, lenAt);
          if (at < 0 || (nextKind > 0 && at > nextKind)) continue;
          int br = text.indexOf('[', at);
          int cl = text.indexOf(']', br);
          if (br < 0 || cl < 0) continue;
          String[] nums = text.substring(br + 1, cl).split(",");
          for (int s = 0; s < MAX_STEPS && s < nums.length; s++) {
            try {
              int g = Integer.parseInt(nums[s].trim());
              p.lens[t][s] = g > 1 ? clampNoteLength(g) : 0;
            } catch (NumberFormatException ignored) {
              /* */
            }
          }
        }
      }
      out.add(p);
      idx = k + 6;
      if (out.size() >= 24) break;
    }
    return out;
  }

  private static String extractString(String text, int from) {
    int q = text.indexOf('"', from);
    if (q < 0) return "";
    int q2 = text.indexOf('"', q + 1);
    if (q2 < 0) return "";
    return text.substring(q + 1, q2);
  }

  private static int extractInt(String text, String key, int from, int fallback) {
    int at = text.indexOf(key, from);
    if (at < 0) return fallback;
    int c = text.indexOf(':', at);
    int i = c + 1;
    while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\n')) i++;
    int j = i;
    while (j < text.length() && (Character.isDigit(text.charAt(j)))) j++;
    try {
      return Integer.parseInt(text.substring(i, j));
    } catch (Exception e) {
      return fallback;
    }
  }

  public static String quote(String s) {
    if (s == null) s = "";
    return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  public static byte[] encodeMidi(int[][] cells, int bpm) {
    return encodeMidi(cells, bpm, usedSteps(cells));
  }

  public static byte[] encodeMidi(int[][] cells, int bpm, int steps) {
    return encodeMidi(cells, bpm, steps, 4, 4);
  }

  public static byte[] encodeMidi(int[][] cells, int bpm, int steps, int tsNum, int tsDen) {
    return encodeMidi(cells, null, bpm, steps, tsNum, tsDen);
  }

  public static byte[] encodeMidi(int[][] cells, int[][] lens, int bpm, int steps, int tsNum, int tsDen) {
    int n = clampSteps(steps);
    List<int[]> ev = new ArrayList<>();
    int us = (int) Math.round(60_000_000.0 / Math.max(MIN_BPM, Math.min(MAX_BPM, bpm)));
    ev.add(new int[] {0, 0xff, 0x51, 0x03, (us >> 16) & 0xff, (us >> 8) & 0xff, us & 0xff});
    int nn = clampTsNum(tsNum);
    ev.add(new int[] {0, 0xff, 0x58, 0x04, nn, midiTsDenExp(tsDen), 0x18, 0x08});
    int step = TPQ / 4;
    for (int t = 0; t < TRACK_ID.length; t++) {
      for (int s = 0; s < n; s++) {
        int vel = s < cells[t].length ? cells[t][s] : 0;
        if (vel <= 0) continue;
        int tick = s * step;
        if (s % 2 == 1) tick += (int) (step * 0.12);
        ev.add(new int[] {tick, 0x90 | DRUM_CH, NOTES[t], vel});
        ev.add(new int[] {tick + gateTicks(lenAt(lens, t, s)), 0x80 | DRUM_CH, NOTES[t], 0});
      }
    }
    return packMidi(ev, n * step);
  }

  public static byte[] encodeSongMidi(List<Part> parts) {
    List<int[]> ev = new ArrayList<>();
    int tick = 0;
    int step = TPQ / 4;
    for (Part p : parts) {
      int us = (int) Math.round(60_000_000.0 / Math.max(MIN_BPM, Math.min(MAX_BPM, p.bpm)));
      ev.add(new int[] {tick, 0xff, 0x51, 0x03, (us >> 16) & 0xff, (us >> 8) & 0xff, us & 0xff});
      ev.add(new int[] {tick, 0xff, 0x58, 0x04, clampTsNum(p.tsNum), midiTsDenExp(p.tsDen), 0x18, 0x08});
      int times = Math.max(1, p.repeats);
      int n = clampSteps(p.steps);
      for (int r = 0; r < times; r++) {
        for (int t = 0; t < TRACK_ID.length; t++) {
          for (int s = 0; s < n; s++) {
            int vel = s < p.cells[t].length ? p.cells[t][s] : 0;
            if (vel <= 0) continue;
            int at = tick + s * step;
            ev.add(new int[] {at, 0x90 | DRUM_CH, NOTES[t], vel});
            ev.add(new int[] {at + gateTicks(lenAt(p.lens, t, s)), 0x80 | DRUM_CH, NOTES[t], 0});
          }
        }
        tick += n * step;
      }
    }
    return packMidi(ev, tick);
  }

  private static byte[] packMidi(List<int[]> ev, int endTick) {
    ev.sort((a, b) -> Integer.compare(a[0], b[0]));
    ArrayList<Byte> body = new ArrayList<>();
    int last = 0;
    for (int[] e : ev) {
      for (int b : vlq(e[0] - last)) body.add((byte) b);
      for (int i = 1; i < e.length; i++) body.add((byte) e[i]);
      last = e[0];
    }
    for (int b : vlq(Math.max(0, endTick - last))) body.add((byte) b);
    body.add((byte) 0xff);
    body.add((byte) 0x2f);
    body.add((byte) 0x00);
    int len = body.size();
    byte[] out = new byte[14 + 8 + len];
    out[0] = 'M'; out[1] = 'T'; out[2] = 'h'; out[3] = 'd';
    out[7] = 6;
    out[9] = 0;
    out[11] = 1;
    out[12] = (byte) ((TPQ >> 8) & 0xff);
    out[13] = (byte) (TPQ & 0xff);
    out[14] = 'M'; out[15] = 'T'; out[16] = 'r'; out[17] = 'k';
    out[18] = (byte) ((len >> 24) & 0xff);
    out[19] = (byte) ((len >> 16) & 0xff);
    out[20] = (byte) ((len >> 8) & 0xff);
    out[21] = (byte) (len & 0xff);
    for (int i = 0; i < len; i++) out[22 + i] = body.get(i);
    return out;
  }

  private static int[] vlq(int value) {
    int v = Math.max(0, value);
    int[] tmp = new int[4];
    int n = 0;
    tmp[n++] = v & 0x7f;
    v >>= 7;
    while (v > 0) {
      tmp[n++] = (v & 0x7f) | 0x80;
      v >>= 7;
    }
    int[] out = new int[n];
    for (int i = 0; i < n; i++) out[i] = tmp[n - 1 - i];
    return out;
  }

  public static int parseMidiBpm(byte[] data, int fallback) {
    if (data.length < 14) return fallback;
    int i = 14;
    while (i + 8 < data.length) {
      if (data[i] != 'M' || data[i + 1] != 'T') break;
      int len = ((data[i + 4] & 0xff) << 24) | ((data[i + 5] & 0xff) << 16)
          | ((data[i + 6] & 0xff) << 8) | (data[i + 7] & 0xff);
      int p = i + 8;
      int end = Math.min(data.length, p + Math.max(0, len));
      while (p + 3 < end) {
        int[] v = readVlq(data, p);
        p = v[1];
        if (p >= end) break;
        int st = data[p] & 0xff;
        if (st == 0xff && p + 2 < end) {
          int type = data[p + 1] & 0xff;
          int l = data[p + 2] & 0xff;
          if (type == 0x51 && l >= 3 && p + 5 < end) {
            int us = ((data[p + 3] & 0xff) << 16) | ((data[p + 4] & 0xff) << 8) | (data[p + 5] & 0xff);
            if (us > 0) return clamp((int) Math.round(60_000_000.0 / us), MIN_BPM, MAX_BPM);
          }
          p += 3 + l;
          continue;
        }
        if (st == 0xf0 || st == 0xf7) {
          int[] sl = readVlq(data, p + 1);
          p = sl[1] + sl[0];
          continue;
        }
        int cmd = st & 0xf0;
        if (cmd == 0xc0 || cmd == 0xd0) p += 2;
        else p += 3;
      }
      i = end;
    }
    return fallback;
  }

  public static int[][] parseMidi(byte[] data) {
    int[][] cells = emptyCells();
    MidiHitList parsed = collectMidiHits(data);
    if (parsed.hits.isEmpty()) return cells;
    int stepTicks = Math.max(1, parsed.tpq / 4);
    int last = 0;
    for (int[] h : parsed.hits) {
      int s = (int) Math.round(h[0] / (double) stepTicks);
      if (s > last) last = s;
    }
    int bar = barSteps(parsed.tsNum, parsed.tsDen);
    boolean longForm = last >= bar + Math.max(4, bar / 4);
    int lastBarStart = longForm ? (last / bar) * bar : 0;
    for (int[] h : parsed.hits) {
      int s = (int) Math.round(h[0] / (double) stepTicks);
      int dest;
      if (s < 0) continue;
      if (s < bar) dest = s;
      else if (longForm && s >= lastBarStart && s < lastBarStart + bar) dest = bar + (s - lastBarStart);
      else continue;
      if (dest < 0 || dest >= MAX_STEPS) continue;
      int t = trackForNote(h[1]);
      if (t < 0) continue;
      if (h[2] > cells[t][dest]) cells[t][dest] = h[2] > 127 ? 127 : h[2];
    }
    return cells;
  }

  public static final class MidiBars {
    public int bpm = 120;
    public int tsNum = 4;
    public int tsDen = 4;
    public final List<int[][]> bars = new ArrayList<>();
  }

  public static final class MidiSeg {
    public String kind = "pattern";
    public int[][] groove;
    public int[][] fill;
    public int grooveRepeats = 1;
  }

  private static final class MidiHitList {
    int tpq = 480;
    int tsNum = 4;
    int tsDen = 4;
    final ArrayList<int[]> hits = new ArrayList<>();
  }

  private static MidiHitList collectMidiHits(byte[] data) {
    MidiHitList out = new MidiHitList();
    if (data == null || data.length < 14) return out;
    String head = new String(data, 0, 4, StandardCharsets.ISO_8859_1);
    if (!head.equals("MThd")) return out;
    int tpq = ((data[12] & 0xff) << 8) | (data[13] & 0xff);
    if ((tpq & 0x8000) != 0 || tpq <= 0) tpq = 480;
    out.tpq = tpq;
    int i = 8 + (((data[4] & 0xff) << 24) | ((data[5] & 0xff) << 16) | ((data[6] & 0xff) << 8) | (data[7] & 0xff));
    if (i < 14) i = 14;
    while (i + 8 < data.length && out.hits.size() < 8000) {
      if (data[i] != 'M' || data[i + 1] != 'T' || data[i + 2] != 'r' || data[i + 3] != 'k') break;
      int len = ((data[i + 4] & 0xff) << 24) | ((data[i + 5] & 0xff) << 16)
          | ((data[i + 6] & 0xff) << 8) | (data[i + 7] & 0xff);
      if (len < 0) break;
      int p = i + 8;
      int end = Math.min(data.length, p + len);
      int tick = 0;
      int running = 0;
      while (p < end && out.hits.size() < 8000) {
        int[] v = readVlq(data, p);
        tick += v[0];
        p = v[1];
        if (p >= end) break;
        int st = data[p] & 0xff;
        if (st < 0x80) {
          if (running == 0) {
            p++;
            continue;
          }
          st = running;
        } else {
          p++;
          if (st < 0xf0) running = st;
        }
        if (st == 0xff) {
          if (p >= end) break;
          int type = data[p++] & 0xff;
          int[] ml = readVlq(data, p);
          int lenMeta = ml[0];
          int dataAt = ml[1];
          if (type == 0x58 && lenMeta >= 2 && dataAt + 1 < end) {
            out.tsNum = clampTsNum(data[dataAt] & 0xff);
            out.tsDen = denFromMidiExp(data[dataAt + 1] & 0xff);
          }
          p = dataAt + lenMeta;
          continue;
        }
        if (st == 0xf0 || st == 0xf7) {
          int[] sl = readVlq(data, p);
          p = sl[1] + sl[0];
          continue;
        }
        int cmd = st & 0xf0;
        int ch = st & 0x0f;
        if (cmd == 0x90 || cmd == 0x80) {
          if (p + 1 >= end) break;
          int note = data[p++] & 0xff;
          int vel = data[p++] & 0xff;
          if (ch == 9 && cmd == 0x90 && vel > 0) {
            out.hits.add(new int[] { tick, note, vel });
          }
        } else if (cmd == 0xc0 || cmd == 0xd0) {
          p++;
        } else {
          p += 2;
        }
      }
      i = end;
    }
    return out;
  }

  public static MidiBars parseMidiBars(byte[] data) {
    MidiHitList parsed = collectMidiHits(data);
    if (parsed.hits.isEmpty()) return null;
    int stepTicks = Math.max(1, parsed.tpq / 4);
    int last = 0;
    for (int[] h : parsed.hits) {
      int s = (int) Math.round(h[0] / (double) stepTicks);
      if (s > last) last = s;
    }
    int barLen = barSteps(parsed.tsNum, parsed.tsDen);
    int barCount = Math.min(MAX_MIDI_BARS, Math.max(1, last / barLen + 1));
    ArrayList<int[][]> bars = new ArrayList<>();
    for (int b = 0; b < barCount; b++) bars.add(emptyCells());
    for (int[] h : parsed.hits) {
      int s = (int) Math.round(h[0] / (double) stepTicks);
      if (s < 0) continue;
      int bar = s / barLen;
      if (bar < 0 || bar >= barCount) continue;
      int t = trackForNote(h[1]);
      if (t < 0) continue;
      int dest = s % barLen;
      int[][] cells = bars.get(bar);
      if (h[2] > cells[t][dest]) cells[t][dest] = h[2] > 127 ? 127 : h[2];
    }
    while (bars.size() > 1 && hitCount(bars.get(bars.size() - 1)) < 1) bars.remove(bars.size() - 1);
    boolean any = false;
    for (int[][] b : bars) if (hitCount(b) > 0) { any = true; break; }
    if (!any) return null;
    MidiBars out = new MidiBars();
    out.bpm = parseMidiBpm(data, 120);
    out.tsNum = parsed.tsNum;
    out.tsDen = parsed.tsDen;
    out.bars.addAll(bars);
    return out;
  }

  private static int runLength(List<int[][]> bars, int start) {
    String sig = patternSignature(bars.get(start));
    int n = 1;
    while (start + n < bars.size() && patternSignature(bars.get(start + n)).equals(sig)) n++;
    return n;
  }

  public static boolean looksLikeFillBar(int[][] bar, int[][] groove, int nextRun, int barFreq, int grooveRun) {
    if (patternSignature(bar).equals(patternSignature(groove))) return false;
    if (nextRun >= 2) return false;
    if (hitCount(bar) == 0) return true;
    if (barFreq >= 3) return false;
    boolean fillLike = shouldLearnFill(bar);
    if (barFreq == 1 && fillLike) return true;
    if (barFreq <= 2 && grooveRun >= 2 && fillLike) return true;
    return false;
  }

  /** Steps where one bar has a hit and the other does not. Velocity is ignored. */
  public static int hitDiff(int[][] a, int[][] b) {
    int n = 0;
    for (int t = 0; t < TRACK_ID.length; t++) {
      int len = Math.max(a[t].length, b[t].length);
      for (int s = 0; s < len; s++) {
        boolean x = s < a[t].length && a[t][s] > 0;
        boolean y = s < b[t].length && b[t][s] > 0;
        if (x != y) n++;
      }
    }
    return n;
  }

  /**
   * Detected drums vary by a hit or two from bar to bar. Each bar becomes the most
   * common bar within maxDiff hits of it, so a song is a few patterns, not one per bar.
   * Bars with fewer than 4 hits are left alone, so a sparse bar never turns into another.
   */
  public static List<int[][]> mergeNearBars(List<int[][]> bars, int maxDiff) {
    if (bars == null || maxDiff <= 0) return bars;
    LinkedHashMap<String, int[][]> first = new LinkedHashMap<>();
    final Map<String, Integer> count = new HashMap<>();
    for (int[][] b : bars) {
      String sig = patternSignature(b);
      if (!first.containsKey(sig)) first.put(sig, b);
      Integer n = count.get(sig);
      count.put(sig, n == null ? 1 : n + 1);
    }
    List<String> order = new ArrayList<>(first.keySet());
    Collections.sort(order, (x, y) -> count.get(y) - count.get(x));  // stable: ties keep first appearance
    List<String> reps = new ArrayList<>();
    Map<String, String> to = new HashMap<>();
    for (String sig : order) {
      int[][] cells = first.get(sig);
      String best = null;
      int bestDiff = maxDiff + 1;
      if (hitCount(cells) >= 4) {
        for (String r : reps) {
          int[][] rc = first.get(r);
          if (hitCount(rc) < 4) continue;
          int d = hitDiff(cells, rc);
          if (d < bestDiff) {
            bestDiff = d;
            best = r;
          }
        }
      }
      if (best == null) {
        reps.add(sig);
        to.put(sig, sig);
      } else {
        to.put(sig, best);
      }
    }
    List<int[][]> out = new ArrayList<>(bars.size());
    for (int[][] b : bars) out.add(first.get(to.get(patternSignature(b))));
    return out;
  }

  public static List<MidiSeg> segmentMidiBars(List<int[][]> bars) {
    List<MidiSeg> out = new ArrayList<>();
    if (bars == null || bars.isEmpty()) return out;
    bars = mergeNearBars(bars, MidiImportSettings.mergeLimit());
    Map<String, Integer> freq = new LinkedHashMap<>();
    for (int[][] b : bars) {
      String s = patternSignature(b);
      Integer n = freq.get(s);
      freq.put(s, n == null ? 1 : n + 1);
    }
    int i = 0;
    while (i < bars.size()) {
      int[][] groove = bars.get(i);
      if (hitCount(groove) < 1 && !MidiImportSettings.keepSilent) {
        i++;
        continue;
      }
      if (hitCount(groove) < 1) {
        // Silent bars stay in the song, so what follows keeps its place.
        int rest = 1;
        while (i + rest < bars.size() && hitCount(bars.get(i + rest)) < 1) rest++;
        MidiSeg seg = new MidiSeg();
        seg.kind = "pattern";
        seg.groove = groove;
        seg.grooveRepeats = rest;
        out.add(seg);
        i += rest;
        continue;
      }
      String gsig = patternSignature(groove);
      int run = runLength(bars, i);
      int nextIdx = i + run;
      if (nextIdx < bars.size() && MidiImportSettings.oneOffFills) {
        int[][] fill = bars.get(nextIdx);
        int nrun = runLength(bars, nextIdx);
        Integer nf = freq.get(patternSignature(fill));
        int nfreq = nf == null ? 0 : nf;
        boolean phraseEnd = (nextIdx + 1) % 4 == 0 || (nextIdx + 1) % 8 == 0 || nextIdx == bars.size() - 1;
        boolean fillish = looksLikeFillBar(fill, groove, nrun, nfreq, run)
            || (nrun == 1 && phraseEnd && !patternSignature(fill).equals(gsig) && nfreq == 1);
        if (fillish) {
          MidiSeg seg = new MidiSeg();
          seg.kind = "fillern";
          seg.groove = groove;
          seg.fill = fill;
          seg.grooveRepeats = run;
          out.add(seg);
          i = nextIdx + 1;
          continue;
        }
      }
      MidiSeg seg = new MidiSeg();
      seg.kind = "pattern";
      seg.groove = groove;
      seg.grooveRepeats = run;
      out.add(seg);
      i = nextIdx;
    }
    return out;
  }

  /** GM drum note → Pulsekit track, or -1. */
  public static int trackForNote(int note) {
    if (note == 36) return track("kick");
    if (note == 35) return track("dkick");
    if (note == 38 || note == 40) return track("snare");
    if (note == 39) return track("clap");
    if (note == 37) return track("rim");
    if (note == 42 || note == 44) return track("chh");
    if (note == 46) return track("ohh");
    if (note == 49 || note == 57) return track("crash");
    if (note == 51 || note == 59) return track("ride");
    if (note == 41 || note == 43) return track("ltom");
    if (note == 45 || note == 47) return track("mtom");
    if (note == 48 || note == 50) return track("htom");
    for (int t = 0; t < NOTES.length; t++) if (NOTES[t] == note) return t;
    return -1;
  }

  private static int[] readVlq(byte[] data, int p) {
    int n = 0;
    int i = 0;
    while (p < data.length && i < 4) {
      int b = data[p++] & 0xff;
      n = (n << 7) | (b & 0x7f);
      i++;
      if ((b & 0x80) == 0) break;
    }
    return new int[] { n, p };
  }

  public static Style dubStyle() {
    return new Style(
        "dub",
        "Dub",
        76,
        new String[] {
          "X-------X-------",
          "----------------",
          "--------X-------",
          "----------------",
          "----x-------x---",
          "x---x---x---x---",
          "------------x---",
          "----------------",
          "----------------",
          "--------------x-",
          "----------------",
          "----------------"
        });
  }

  public static int[][] dubFill() {
    return rowsToCells(new String[] {
      "X-----------X---",
      "----------------",
      "----x---x-x-X---",
      "----------------",
      "----------------",
      "----------------",
      "----------------",
      "--------------X-",
      "----------------",
      "------x-x-------",
      "----x-----x-----",
      "--x-------------"
    });
  }

  public static int[][] styleCells(Style st) {
    return rowsToCells(st.rows);
  }

  public static final int MAX_LEARNED = 256;
  public static final int MAX_VARIATED = 8;
  public static final int MAX_SONG = 256;
  public static final int MAX_IMPORTED_SONGS = 8;
  public static final int MAX_MIDI_BARS = 256;
  public static final double[] TRACK_WEIGHT = {4, 3, 4, 1.2, 0.8, 2, 1, 0.8, 1.2, 0.7, 0.7, 0.7};

  public static final class Learned {
    public String id;
    public String name;
    public int bpm;
    public String closest;
    public int[][] cells;
    public int swing = -1;
    public int density = -1;
    public int human = -1;
    public String source;
    public int tsNum = 4;
    public int tsDen = 4;
    public int steps = STEPS;
  }

  public static final class LearnedFill {
    public String id;
    public String name;
    public String kind;
    public int[][] cells;
    public String source;
  }

  public static final class DrumSet {
    public String id = "original";
    public String name = "Original";
    public boolean original = true;
    public final short[][] samples = new short[TRACK_ID.length][];
    public final boolean[] matchOrig = new boolean[TRACK_ID.length];

    public static DrumSet originalSet() {
      DrumSet s = new DrumSet();
      s.id = "original";
      s.name = "Original";
      s.original = true;
      return s;
    }

    public static DrumSet isolated(String id, String name) {
      DrumSet s = new DrumSet();
      s.id = id == null || id.isEmpty() ? "s" + Integer.toHexString((int) (Math.random() * 1_000_000_000)) : id;
      s.name = name == null || name.isEmpty() ? "Isolated" : name;
      s.original = false;
      return s;
    }

    public int foundCount() {
      int n = 0;
      for (int t = 0; t < TRACK_ID.length; t++) {
        if (!matchOrig[t] && samples[t] != null) n++;
      }
      return n;
    }
  }

  public static String uniqueSetName(String name, List<DrumSet> sets) {
    String base = name == null || name.trim().isEmpty() ? "Isolated" : name.trim();
    if (base.length() > 28) base = base.substring(0, 28);
    boolean hit = false;
    for (DrumSet s : sets) if (base.equals(s.name)) hit = true;
    if (!hit) return base;
    for (int n = 2; n < 40; n++) {
      String next = (base + " " + n);
      if (next.length() > 28) next = next.substring(0, 28);
      boolean used = false;
      for (DrumSet s : sets) if (next.equals(s.name)) used = true;
      if (!used) return next;
    }
    return base;
  }

  public static int[] hits16(int[] row) {
    int[] out = new int[STEPS];
    if (row == null || row.length == 0) return out;
    for (int i = 0; i < STEPS; i++) out[i] = row[i % row.length] > 0 ? 1 : 0;
    return out;
  }

  public static String patternSignature(int[][] cells) {
    StringBuilder sb = new StringBuilder();
    for (int t = 0; t < TRACK_ID.length; t++) {
      if (t > 0) sb.append('.');
      int[] h = hits16(cells[t]);
      for (int v : h) sb.append(v);
    }
    return sb.toString();
  }

  public static int hitCount(int[][] cells) {
    int n = 0;
    for (int t = 0; t < TRACK_ID.length; t++) {
      for (int s = 0; s < cells[t].length; s++) if (cells[t][s] > 0) n++;
    }
    return n;
  }

  public static int[][] lastBar(int[][] cells) {
    return lastBar(cells, STEPS);
  }

  public static int[][] lastBar(int[][] cells, int barLen) {
    int[][] out = emptyCells();
    if (cells == null || cells.length == 0 || cells[0] == null) return out;
    int bar = clampSteps(barLen);
    int n = usedSteps(cells);
    int start = n >= bar * 2 - 4 ? Math.max(0, n - bar) : 0;
    for (int t = 0; t < TRACK_ID.length; t++) {
      if (cells[t] == null || cells[t].length == 0) continue;
      for (int i = 0; i < bar; i++) {
        int idx = start + i;
        out[t][i] = idx < cells[t].length ? cells[t][idx] : cells[t][i % Math.max(cells[t].length, 1)];
      }
    }
    return out;
  }

  public static int[][] firstBar(int[][] cells) {
    return firstBar(cells, STEPS);
  }

  public static int[][] firstBar(int[][] cells, int barLen) {
    int[][] out = emptyCells();
    if (cells == null) return out;
    int bar = clampSteps(barLen);
    for (int t = 0; t < TRACK_ID.length; t++) {
      if (cells[t] == null) continue;
      for (int i = 0; i < bar; i++) out[t][i] = i < cells[t].length ? cells[t][i] : 0;
    }
    return out;
  }

  public static int hitsIn(int[][] cells, int[] tracks, int from, int to) {
    int n = 0;
    for (int t : tracks) {
      for (int i = from; i < to && i < cells[t].length; i++) if (cells[t][i] > 0) n++;
    }
    return n;
  }

  public static String classifyFill(int[][] cells) {
    int[][] p = lastBar(cells);
    int first = hitsIn(p, range(0, TRACK_ID.length), 0, 8);
    int last = hitsIn(p, range(0, TRACK_ID.length), 8, 16);
    int toms = hitsIn(p, new int[] {8, 9, 10}, 8, 16);
    int snare = hitsIn(p, new int[] {1}, 8, 16);
    int hatsLast = hitsIn(p, new int[] {4, 5, 7}, 8, 16);
    int hatsFirst = hitsIn(p, new int[] {4, 5, 7}, 0, 8);
    boolean crashEnd = p[6][15] > 0 || p[6][14] > 0;
    if (hitCount(p) == 0) return "rest";
    if (last <= Math.max(3, first * 0.45)) return "break";
    if (toms >= 3) return "toms";
    if (snare >= 4) return "roll";
    if (crashEnd && hatsLast < hatsFirst * 0.75) return "crash";
    if (last > first * 1.1) {
      if (toms >= 2) return "toms";
      if (snare >= 3) return "roll";
      return "crash";
    }
    return crashEnd ? "crash" : "toms";
  }

  private static int[] range(int a, int b) {
    int[] o = new int[b - a];
    for (int i = 0; i < o.length; i++) o[i] = a + i;
    return o;
  }

  public static boolean shouldLearnFill(int[][] cells) {
    int[][] p = lastBar(cells);
    int last = hitsIn(p, range(0, TRACK_ID.length), 8, 16);
    int toms = hitsIn(p, new int[] {8, 9, 10}, 8, 16);
    int snare = hitsIn(p, new int[] {1}, 8, 16);
    String kind = classifyFill(p);
    if ("rest".equals(kind)) return hitCount(p) == 0;
    if ("break".equals(kind)) return last >= 1 || p[6][15] > 0 || p[0][15] > 0;
    return last >= 2 || toms >= 2 || snare >= 3;
  }

  public static boolean shouldImportFill(int[][] cells) {
    int[][] bar = lastBar(cells);
    if (hitCount(bar) < 2) return shouldLearnFill(cells);
    if (usedSteps(cells) >= 24) {
      String a = patternSignature(firstBar(cells));
      String b = patternSignature(bar);
      if (!a.equals(b)) return true;
    }
    return shouldLearnFill(cells);
  }

  public static boolean importedFillern(int[][] cells) {
    if (usedSteps(cells) < 24) return false;
    String a = patternSignature(firstBar(cells));
    String b = patternSignature(lastBar(cells));
    if (a.equals(b)) return false;
    return shouldImportFill(cells) || hitCount(lastBar(cells)) >= 2;
  }

  public static String matchStyle(int[][] cells) {
    String best = null;
    double bestScore = -1;
    Map<String, Style> all = styles();
    for (Style st : all.values()) {
      int[][] preset = rowsToCells(st.rows);
      double same = 0;
      double total = 0;
      for (int t = 0; t < TRACK_ID.length; t++) {
        double w = t < TRACK_WEIGHT.length ? TRACK_WEIGHT[t] : 1;
        int[] a = hits16(cells[t]);
        int[] b = hits16(preset[t]);
        for (int i = 0; i < STEPS; i++) {
          total += w;
          if (a[i] == b[i]) same += w;
        }
      }
      double score = total == 0 ? 0 : same / total;
      if (score > bestScore) {
        bestScore = score;
        best = st.id;
      }
    }
    return best;
  }

  public static String styleNameFromFile(String filename) {
    if (filename == null || filename.isEmpty()) return "Import";
    String stem = filename;
    int q = stem.indexOf('?');
    if (q >= 0) stem = stem.substring(0, q);
    try {
      stem = java.net.URLDecoder.decode(stem, "UTF-8");
    } catch (Exception ignored) { /* keep raw */ }
    int slash = Math.max(Math.max(stem.lastIndexOf('/'), stem.lastIndexOf('\\')), stem.lastIndexOf(':'));
    if (slash >= 0) stem = stem.substring(slash + 1);
    stem = stem.replaceAll("(?i)\\.(mid|midi|jar|zip|wav|wave|mp3|sng|fset|prj|pkp)$", "");
    stem = stem.replaceAll("[_-]+", " ").replaceAll("\\s+", " ").trim();
    if (stem.isEmpty() || stem.equalsIgnoreCase("document") || stem.equalsIgnoreCase("raw")) stem = "Import";
    return stem.length() > 28 ? stem.substring(0, 28) : stem;
  }

  public static String fillNameFromFile(String filename, String kind) {
    String stem = stemNameFromMidi(filename);
    String tag = "break".equals(kind) ? "break" : "rest".equals(kind) ? "silent" : "fill";
    String lower = stem.toLowerCase();
    String base = lower.matches(".*\\b(fill|break|silent)\\b.*") ? stem : stem + " " + tag;
    return base.length() > 28 ? base.substring(0, 28) : base;
  }

  public static String midiRoleFromFile(String filename) {
    if (filename == null) return null;
    String stem = styleNameFromFile(filename).toLowerCase();
    stem = stem.replaceAll("\\s+\\d+\\s*bpm\\b", "").replaceAll("\\s+variated\\b", "").replaceAll("\\s+", " ").trim();
    if (stem.endsWith("fillern")) return "fillern";
    if (stem.endsWith("pattern")) return "pattern";
    if (stem.endsWith("fill") || stem.endsWith("break") || stem.endsWith("silent")) return "fill";
    return null;
  }

  public static String stemNameFromMidi(String filename) {
    String stem = styleNameFromFile(filename);
    stem = stem.replaceAll("(?i)\\s+\\d+\\s*bpm\\b", "");
    stem = stem.replaceAll("(?i)\\s*\\bvariated\\b", "");
    stem = stem.replaceAll("(?i)\\s*\\b(fillern|pattern|fill|break|silent)\\s*$", "");
    stem = stem.replaceAll("\\s+", " ").trim();
    if (stem.isEmpty()) stem = "Import";
    return stem.length() > 28 ? stem.substring(0, 28) : stem;
  }

  public static String midiFileName(String style, int bpm, String role, String fillLabel) {
    String s = style == null || style.isEmpty() ? "pulsekit" : style.toLowerCase().trim();
    if ("fillern".equals(role)) return s + " " + bpm + " bpm fillern.mid";
    if ("fill".equals(role)) {
      String f = fillLabel == null || fillLabel.isEmpty() ? "fill" : fillLabel.toLowerCase().trim();
      if (!f.matches(".*\\b(fill|break|silent)\\b.*")) f = f + " fill";
      return s + " " + f + ".mid";
    }
    return s + " " + bpm + " bpm pattern.mid";
  }

  public static int[][] cellsForMidiRole(int[][] groove, int[][] fill, String role) {
    if ("fill".equals(role)) return lastBar(fill != null ? fill : groove);
    if ("fillern".equals(role)) {
      int[][] out = emptyCells();
      int[][] g = firstBar(groove);
      int[][] f = lastBar(fill != null ? fill : groove);
      for (int t = 0; t < TRACK_ID.length; t++) {
        for (int i = 0; i < STEPS; i++) {
          out[t][i] = g[t][i];
          out[t][STEPS + i] = f[t][i];
        }
      }
      return out;
    }
    return copyCells(groove);
  }

  public static String importSource(String filename) {
    String s = stemNameFromMidi(filename);
    if (s == null) return null;
    s = s.trim();
    return s.isEmpty() ? null : (s.length() > 40 ? s.substring(0, 40) : s);
  }

  public static String uniqueImportSource(String base, List<Learned> learned, List<LearnedFill> fills) {
    String stem = base == null ? "" : base.trim();
    if (stem.isEmpty()) stem = "Import";
    if (stem.length() > 40) stem = stem.substring(0, 40);
    java.util.HashSet<String> used = new java.util.HashSet<String>();
    if (learned != null) {
      for (Learned x : learned) {
        String s = sourceOf(x);
        if (!s.isEmpty()) used.add(s.toLowerCase());
      }
    }
    if (fills != null) {
      for (LearnedFill x : fills) {
        String s = sourceOf(x);
        if (!s.isEmpty()) used.add(s.toLowerCase());
      }
    }
    if (!used.contains(stem.toLowerCase())) return stem;
    String root = stem;
    int start = 2;
    int sp = stem.lastIndexOf(' ');
    if (sp > 0) {
      String tail = stem.substring(sp + 1);
      boolean digits = tail.length() > 0;
      for (int i = 0; i < tail.length(); i++) {
        char c = tail.charAt(i);
        if (c < '0' || c > '9') { digits = false; break; }
      }
      if (digits) {
        String cut = stem.substring(0, sp).trim();
        if (!cut.isEmpty()) {
          root = cut;
          try { start = Integer.parseInt(tail) + 1; } catch (Exception ignored) { start = 2; }
        }
      }
    }
    for (int i = start; i < start + 99; i++) {
      String n = root.length() > 36 ? root.substring(0, 36) : root;
      n = n + " " + i;
      if (n.length() > 40) n = n.substring(0, 40);
      if (!used.contains(n.toLowerCase())) return n;
    }
    return stem;
  }

  public static String sourceOf(Learned item) {
    return item == null || item.source == null || item.source.isEmpty() ? "" : item.source;
  }

  public static String sourceOf(LearnedFill item) {
    return item == null || item.source == null || item.source.isEmpty() ? "" : item.source;
  }

  private static long lastIdMillis;
  private static int idSeq;

  /**
   * "c" + time in base 36 + a sequence number. An import creates several patterns
   * in the same millisecond; the old random last digit let two of them share an id
   * (about 1 in 36), and the second then replaced the first.
   */
  public static synchronized String newLearnedId() {
    long now = System.currentTimeMillis();
    if (now <= lastIdMillis) {
      now = lastIdMillis;
      idSeq++;
    } else {
      lastIdMillis = now;
      idSeq = 0;
    }
    return "c" + Long.toString(now, 36) + Integer.toString(idSeq, 36);
  }

  public static String learnedJson(List<Learned> list) {
    StringBuilder sb = new StringBuilder();
    sb.append('[');
    for (int i = 0; i < list.size(); i++) {
      if (i > 0) sb.append(',');
      Learned item = list.get(i);
      sb.append("{\"id\":").append(quote(item.id));
      sb.append(",\"name\":").append(quote(item.name));
      sb.append(",\"bpm\":").append(item.bpm);
      sb.append(",\"tsNum\":").append(item.tsNum);
      sb.append(",\"tsDen\":").append(item.tsDen);
      sb.append(",\"steps\":").append(item.steps);
      sb.append(",\"closest\":").append(quote(item.closest == null ? "" : item.closest));
      sb.append(",\"swing\":").append(item.swing);
      sb.append(",\"density\":").append(item.density);
      sb.append(",\"human\":").append(item.human);
      if (item.source != null && !item.source.isEmpty()) sb.append(",\"source\":").append(quote(item.source));
      sb.append(",\"pattern\":").append(cellsJson(item.cells == null ? emptyCells() : item.cells)).append('}');
    }
    return sb.append(']').toString();
  }

  public static String learnedFillsJson(List<LearnedFill> list) {
    StringBuilder sb = new StringBuilder();
    sb.append('[');
    for (int i = 0; i < list.size(); i++) {
      if (i > 0) sb.append(',');
      LearnedFill item = list.get(i);
      sb.append("{\"id\":").append(quote(item.id));
      sb.append(",\"name\":").append(quote(item.name));
      sb.append(",\"kind\":").append(quote(item.kind == null ? "toms" : item.kind));
      if (item.source != null && !item.source.isEmpty()) sb.append(",\"source\":").append(quote(item.source));
      sb.append(",\"pattern\":").append(cellsJson(item.cells == null ? emptyCells() : item.cells)).append('}');
    }
    return sb.append(']').toString();
  }

  public static List<Learned> parseLearnedJson(String json) {
    return parseLearnedList(json, "learned");
  }

  public static List<Learned> parseVariatedPatternsJson(String json) {
    return parseLearnedList(json, "variatedPatterns");
  }

  public static List<Learned> parseLearnedList(String json, String key) {
    List<Learned> out = new ArrayList<>();
    if (json == null || json.isEmpty()) return out;
    String src = json.trim();
    if (src.startsWith("[")) src = "{\"" + key + "\":" + src + "}";
    for (String obj : jsonObjects(src, key)) {
      Learned item = new Learned();
      item.id = jsonStr(obj, "\"id\"");
      item.name = jsonStr(obj, "\"name\"");
      if (item.id == null || item.name == null) continue;
      item.bpm = jsonInt(obj, "\"bpm\"", 120);
      item.closest = jsonStr(obj, "\"closest\"");
      if (item.closest != null && item.closest.isEmpty()) item.closest = null;
      item.swing = jsonInt(obj, "\"swing\"", -1);
      item.density = jsonInt(obj, "\"density\"", -1);
      item.human = jsonInt(obj, "\"human\"", -1);
      item.source = jsonStr(obj, "\"source\"");
      if (item.source != null && item.source.isEmpty()) item.source = null;
      item.cells = emptyCells();
      patternFromJson(obj, item.cells);
      out.add(item);
    }
    return out;
  }

  public static List<LearnedFill> parseLearnedFillsJson(String json) {
    return parseFillList(json, "learnedFills");
  }

  public static List<LearnedFill> parseVariatedFillsJson(String json) {
    return parseFillList(json, "variatedFills");
  }

  public static List<LearnedFill> parseFillList(String json, String key) {
    List<LearnedFill> out = new ArrayList<>();
    if (json == null || json.isEmpty()) return out;
    String src = json.trim();
    if (src.startsWith("[")) src = "{\"" + key + "\":" + src + "}";
    for (String obj : jsonObjects(src, key)) {
      LearnedFill item = new LearnedFill();
      item.id = jsonStr(obj, "\"id\"");
      item.name = jsonStr(obj, "\"name\"");
      if (item.id == null || item.name == null) continue;
      item.kind = jsonStr(obj, "\"kind\"");
      if (item.kind == null) item.kind = "toms";
      item.source = jsonStr(obj, "\"source\"");
      if (item.source != null && item.source.isEmpty()) item.source = null;
      item.cells = emptyCells();
      patternFromJson(obj, item.cells);
      out.add(item);
    }
    return out;
  }

  public static String[] rowsFromCells(int[][] cells) {
    String[] rows = new String[TRACK_ID.length];
    for (int t = 0; t < TRACK_ID.length; t++) {
      char[] cs = new char[STEPS];
      for (int s = 0; s < STEPS; s++) {
        int v = cells[t][s];
        cs[s] = v >= 120 ? 'X' : v >= 90 ? 'x' : v > 0 ? 'o' : '-';
      }
      rows[t] = new String(cs);
    }
    return rows;
  }

  public static void nudgePattern(int[][] cells, java.util.Random rng, int density, int steps) {
    if (cells == null || rng == null) return;
    double chance = 0.04 + Math.max(1, Math.min(10, density)) * 0.02;
    int n = Math.max(1, Math.min(steps, cells[0] != null ? cells[0].length : STEPS));
    for (int t = 0; t < TRACK_ID.length; t++) {
      if (cells[t] == null) continue;
      for (int s = 0; s < n && s < cells[t].length; s++) {
        if (rng.nextDouble() >= chance) continue;
        if (cells[t][s] > 0 && rng.nextBoolean()) cells[t][s] = 0;
        else if (cells[t][s] == 0) cells[t][s] = rng.nextBoolean() ? 64 : 100;
      }
    }
  }

  public static void stampFillLastBar(int[][] cells, int[][] fill, int steps) {
    stampFillLastBar(cells, fill, steps, STEPS);
  }

  public static void stampFillLastBar(int[][] cells, int[][] fill, int steps, int barLen) {
    if (cells == null || fill == null) return;
    int span = Math.min(clampSteps(barLen), steps);
    int start = Math.max(0, steps - span);
    for (int t = 0; t < TRACK_ID.length; t++) {
      if (cells[t] == null) continue;
      for (int i = 0; i < span; i++) {
        int src = fill[t] != null && i < fill[t].length ? fill[t][i] : 0;
        if (start + i < cells[t].length) cells[t][start + i] = src;
      }
    }
  }

  public static String randomFillId(java.util.Random rng) {
    String[] ids = { "toms", "roll", "crash", "break" };
    return ids[rng.nextInt(ids.length)];
  }

  public static void ghostHats(int[][] cells, java.util.Random rng) {
    ghostHats(cells, rng, usedSteps(cells));
  }

  public static void ghostHats(int[][] cells, java.util.Random rng, int steps) {
    int chh = track("chh");
    int n = clampSteps(steps);
    for (int s = 0; s < n; s++) {
      if (s % 2 == 1 && cells[chh][s] <= 0 && rng.nextDouble() < 0.55) cells[chh][s] = 64;
    }
  }

  public static byte[] encodePkp(String id, String name, int bpm, String styleId, int[][] cells, int[][] fill, String pyName, String py)
      throws Exception {
    LinkedHashMap<String, byte[]> files = new LinkedHashMap<>();
    String scriptFile = "scripts/0-script.py";
    files.put(scriptFile, (py == null ? "" : py).getBytes(StandardCharsets.UTF_8));
    String json = "{\"format\":\"pulsekit-plugin\",\"v\":1,\"id\":" + quote(id)
        + ",\"name\":" + quote(name)
        + ",\"version\":\"1.0\",\"author\":\"Pulsekit\""
        + ",\"styles\":[{\"id\":" + quote(styleId) + ",\"name\":" + quote(name)
        + ",\"bpm\":" + bpm + ",\"pattern\":" + cellsJson(cells) + "}]"
        + ",\"fills\":[{\"id\":\"pack-fill\",\"name\":\"Pack fill\",\"kind\":\"toms\",\"pattern\":"
        + cellsJson(fill != null ? fill : emptyCells()) + "}]"
        + ",\"processors\":[{\"id\":\"ghost-hats\",\"name\":\"Ghost hats\",\"ops\":[{\"op\":\"ghostHats\"}]}]"
        + ",\"scripts\":[{\"name\":" + quote(pyName == null || pyName.isEmpty() ? "script.py" : pyName)
        + ",\"file\":" + quote(scriptFile) + "}]}";
    files.put("plugin.json", json.getBytes(StandardCharsets.UTF_8));
    return zipStored(files);
  }

  public static byte[] encodeKitJar(byte[] midi, int[][] cells, int bpm, String style) throws Exception {
    LinkedHashMap<String, byte[]> files = new LinkedHashMap<>();
    String mf = "Manifest-Version: 1.0\r\nCreated-By: Pulsekit\r\nName: beat.mid\r\nMIDI-File: beat.mid\r\n\r\n";
    files.put("META-INF/MANIFEST.MF", mf.getBytes(StandardCharsets.UTF_8));
    files.put("beat.mid", midi);
    String json = "{\"pattern\":" + cellsJson(cells) + ",\"bpm\":" + bpm + ",\"style\":" + quote(style)
        + ",\"steps\":" + usedSteps(cells) + "}";
    files.put("pattern.json", json.getBytes(StandardCharsets.UTF_8));
    return zipStored(files);
  }

  public static String cellsJson(int[][] cells) {
    return cellsJson(cells, usedSteps(cells));
  }

  public static String cellsJson(int[][] cells, int steps) {
    int n = clampSteps(steps);
    StringBuilder sb = new StringBuilder();
    sb.append('{');
    for (int t = 0; t < TRACK_ID.length; t++) {
      if (t > 0) sb.append(',');
      sb.append(quote(TRACK_ID[t])).append(":[");
      for (int s = 0; s < n; s++) {
        if (s > 0) sb.append(',');
        sb.append(s < cells[t].length ? cells[t][s] : 0);
      }
      sb.append(']');
    }
    sb.append('}');
    return sb.toString();
  }

  public static String boolJson(boolean[] a) {
    StringBuilder sb = new StringBuilder();
    sb.append('[');
    for (int i = 0; i < a.length; i++) {
      if (i > 0) sb.append(',');
      sb.append(a[i] ? "true" : "false");
    }
    sb.append(']');
    return sb.toString();
  }

  public static byte[] zipStored(Map<String, byte[]> files) throws Exception {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    ZipOutputStream zos = new ZipOutputStream(bos);
    zos.setMethod(ZipOutputStream.STORED);
    CRC32 crc = new CRC32();
    for (Map.Entry<String, byte[]> e : files.entrySet()) {
      byte[] data = e.getValue() != null ? e.getValue() : new byte[0];
      ZipEntry ze = new ZipEntry(e.getKey());
      crc.reset();
      crc.update(data);
      ze.setMethod(ZipEntry.STORED);
      ze.setSize(data.length);
      ze.setCompressedSize(data.length);
      ze.setCrc(crc.getValue());
      zos.putNextEntry(ze);
      zos.write(data);
      zos.closeEntry();
    }
    zos.close();
    return bos.toByteArray();
  }

  public static Map<String, byte[]> unzip(byte[] data) throws Exception {
    LinkedHashMap<String, byte[]> out = new LinkedHashMap<>();
    ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(data));
    ZipEntry e;
    byte[] buf = new byte[4096];
    while ((e = zis.getNextEntry()) != null) {
      if (e.isDirectory()) continue;
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      int n;
      while ((n = zis.read(buf)) > 0) bos.write(buf, 0, n);
      out.put(e.getName(), bos.toByteArray());
      zis.closeEntry();
    }
    zis.close();
    return out;
  }

  public static final class FileSetPart {
    public String name = "Part";
    public String kind = "groove";
    public int bpm = 120;
    public int tsNum = 4;
    public int tsDen = 4;
    public String styleLabel = "";
    public int bars = 1;
    public float startSec;
    public float endSec;
    public int hits;
    public int swing;
    public float density;
  }

  public static final class FileSet {
    public String name = "Import";
    public String origin = "";
    public float durationSec;
    public byte[] sourceWav;
    public byte[] combinedWav;
    public String combinedName = "";
    public byte[] sourceMidi;
    public String sourceMidiName = "";
    public final List<Learned> patterns = new ArrayList<Learned>();
    public final List<LearnedFill> fills = new ArrayList<LearnedFill>();
    public final List<String[]> fillerns = new ArrayList<String[]>();
    public final List<FileSetPart> parts = new ArrayList<FileSetPart>();
  }

  /** Source name → midi | analyze | isolate | compose. Survives in fset-info.json. */
  public static final LinkedHashMap<String, String> fileSetOrigins = new LinkedHashMap<String, String>();

  public static final class FileSetAudio {
    public byte[] sourceWav;
    public byte[] combinedWav;
    public byte[] drumWav;
    public byte[] bedWav;
    public byte[] sourceMidi;
    public String sourceMidiName = "";
    public String combinedName = "";
  }

  /** Source name → original guitar WAV and optional combined mix. */
  public static final LinkedHashMap<String, FileSetAudio> fileSetAudio = new LinkedHashMap<String, FileSetAudio>();

  public static String combinedAudioName(String name) {
    String stem = name == null ? "" : name.trim();
    if (stem.toLowerCase().startsWith("combined_")) stem = stem.substring("combined_".length());
    if (stem.toLowerCase().endsWith(".wav")) stem = stem.substring(0, stem.length() - 4);
    String n = stem.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    if (n.length() > 40) n = n.substring(0, 40);
    if (n.isEmpty()) n = "track";
    return "combined_" + n + ".wav";
  }

  public static ImportedSong fileSetSongMade(String label, List<ImportedSong> songs) {
    if (label == null || songs == null) return null;
    String stem = label.trim();
    if (stem.length() > 22) stem = stem.substring(0, 22);
    if (stem.isEmpty()) return null;
    ImportedSong numbered = null;
    for (ImportedSong s : songs) {
      if (s == null || s.name == null) continue;
      if (stem.equals(s.name)) return s;
      if (s.name.startsWith(stem + " ")) {
        String rest = s.name.substring(stem.length() + 1);
        boolean digits = rest.length() > 0;
        for (int i = 0; i < rest.length(); i++) {
          char c = rest.charAt(i);
          if (c < '0' || c > '9') digits = false;
        }
        if (digits) numbered = s;
      }
    }
    return numbered;
  }

  public static void rememberFileSetMidi(String source, byte[] midi, String name) {
    if (source == null || source.isEmpty() || midi == null || midi.length < 14) return;
    FileSetAudio cur = fileSetAudio.get(source);
    if (cur == null) {
      cur = new FileSetAudio();
      fileSetAudio.put(source, cur);
    }
    cur.sourceMidi = midi;
    String file = name == null ? "" : name.trim();
    int slash = Math.max(file.lastIndexOf('/'), file.lastIndexOf('\\'));
    if (slash >= 0) file = file.substring(slash + 1);
    if (file.length() == 0 || !file.toLowerCase().endsWith(".mid") && !file.toLowerCase().endsWith(".midi")) file = "song.mid";
    cur.sourceMidiName = file;
  }

  public static String fileSetMidiName(String source) {
    FileSetAudio au = fileSetAudioOf(source);
    if (au == null || au.sourceMidi == null || au.sourceMidi.length < 14) return "";
    if (au.sourceMidiName != null && au.sourceMidiName.length() > 0) return au.sourceMidiName;
    return "song.mid";
  }

  /** Label from the file-set row, matched to the source that actually holds the MIDI. */
  public static String fileSetMidiKeyForLabel(String label) {
    String raw = label == null ? "" : label.trim();
    if (fileSetMidiName(raw).length() > 0) return raw;
    String stripped = raw;
    if (stripped.startsWith("EP ")) stripped = stripped.substring(3);
    else if (stripped.startsWith("A ") || stripped.startsWith("M ") || stripped.startsWith("I ") || stripped.startsWith("C ")) stripped = stripped.substring(2);
    if (!stripped.equals(raw) && fileSetMidiName(stripped).length() > 0) return stripped;
    String only = null;
    int n = 0;
    for (Map.Entry<String, FileSetAudio> e : fileSetAudio.entrySet()) {
      FileSetAudio au = e.getValue();
      if (au == null || au.sourceMidi == null || au.sourceMidi.length < 14) continue;
      String key = e.getKey() == null ? "" : e.getKey();
      String shown = key.isEmpty() ? "Other" : key;
      String marked = fileSetMarked(shown, fileSetOriginOf(key));
      if (raw.equals(shown) || raw.equals(marked) || raw.equals(key) || stripped.equals(shown) || stripped.equals(key) || stripped.equals(marked)) return key;
      only = key;
      n++;
    }
    return n == 1 && only != null ? only : "";
  }

  private static byte[] stagedMidi;
  private static String stagedMidiName = "";

  /** DrumMidi's MIDI, attached to the next file set that is built from it. */
  public static void stageSourceMidi(byte[] midi, String name) {
    stagedMidi = midi;
    stagedMidiName = name == null ? "" : name;
  }

  public static void attachStagedMidi(FileSet set) {
    if (set == null || stagedMidi == null || stagedMidi.length < 14) return;
    set.sourceMidi = stagedMidi;
    String file = stagedMidiName == null ? "" : stagedMidiName;
    int slash = Math.max(file.lastIndexOf('/'), file.lastIndexOf('\\'));
    if (slash >= 0) file = file.substring(slash + 1);
    if (file.length() == 0) file = "song.mid";
    set.sourceMidiName = file;
    rememberFileSetMidi(set.name, stagedMidi, file);
  }

  public static void rememberFileSetAudio(String source, byte[] sourceWav, byte[] combinedWav, String combinedName) {
    if (source == null || source.isEmpty()) return;
    FileSetAudio cur = fileSetAudio.get(source);
    if (cur == null) {
      cur = new FileSetAudio();
      fileSetAudio.put(source, cur);
    }
    if (sourceWav != null && sourceWav.length > 0) cur.sourceWav = sourceWav;
    if (combinedWav != null && combinedWav.length > 0) {
      cur.combinedWav = combinedWav;
      if (combinedName != null && !combinedName.isEmpty()) cur.combinedName = combinedName;
    }
  }

  public static FileSetAudio fileSetAudioOf(String source) {
    if (source == null) return null;
    return fileSetAudio.get(source);
  }

  private static boolean audioPresent(FileSetAudio au) {
    if (au == null) return false;
    return (au.sourceWav != null && au.sourceWav.length > 0) || (au.combinedWav != null && au.combinedWav.length > 0);
  }

  /** Audio remembered for the set name, or for a pattern/fill source if those differ. */
  private static FileSetAudio audioFor(FileSet set) {
    if (set == null) return null;
    FileSetAudio au = fileSetAudioOf(set.name);
    if (audioPresent(au)) return au;
    if (set.patterns != null) {
      for (Learned p : set.patterns) {
        FileSetAudio alt = fileSetAudioOf(sourceOf(p));
        if (audioPresent(alt)) return alt;
      }
    }
    if (set.fills != null) {
      for (LearnedFill f : set.fills) {
        FileSetAudio alt = fileSetAudioOf(sourceOf(f));
        if (audioPresent(alt)) return alt;
      }
    }
    return au;
  }

  public static void forgetFileSetAudio(String source) {
    if (source != null) fileSetAudio.remove(source);
  }

  public static void loadFileSetAudioDir(File dir) {
    if (dir == null || !dir.isDirectory()) return;
    File[] kids = dir.listFiles();
    if (kids == null) return;
    for (File kid : kids) {
      if (kid == null || !kid.isDirectory()) continue;
      try {
        File metaF = new File(kid, "meta.txt");
        if (!metaF.isFile()) continue;
        String meta = new String(Files.readAllBytes(metaF.toPath()), StandardCharsets.UTF_8);
        int nl = meta.indexOf('\n');
        String source = (nl < 0 ? meta : meta.substring(0, nl)).trim();
        String combinedName = nl < 0 ? "" : meta.substring(nl + 1).trim();
        byte[] sourceWav = null;
        byte[] combined = null;
        File sw = new File(kid, "source.wav");
        File cw = new File(kid, "combined.wav");
        if (sw.isFile()) sourceWav = Files.readAllBytes(sw.toPath());
        if (cw.isFile()) combined = Files.readAllBytes(cw.toPath());
        rememberFileSetAudio(source, sourceWav, combined, combinedName.isEmpty() ? null : combinedName);
        File mid = new File(kid, "source.mid");
        if (mid.isFile()) rememberFileSetMidi(source, Files.readAllBytes(mid.toPath()), "source.mid");
        FileSetAudio loaded = fileSetAudioOf(source);
        File dw = new File(kid, "drum.wav");
        File bw = new File(kid, "bed.wav");
        if (loaded != null && dw.isFile() && bw.isFile()) {
          loaded.drumWav = Files.readAllBytes(dw.toPath());
          loaded.bedWav = Files.readAllBytes(bw.toPath());
        }
      } catch (Exception ignored) { /* optional */ }
    }
  }

  public static void storeFileSetAudioDir(File dir) {
    if (dir == null) return;
    try {
      if (!dir.exists() && !dir.mkdirs()) return;
      File[] old = dir.listFiles();
      if (old != null) for (File f : old) deleteTree(f);
      int i = 0;
      for (Map.Entry<String, FileSetAudio> e : fileSetAudio.entrySet()) {
        FileSetAudio au = e.getValue();
        if (au == null) continue;
        boolean hasSource = au.sourceWav != null && au.sourceWav.length > 0;
        boolean hasMix = au.combinedWav != null && au.combinedWav.length > 0;
        boolean hasMidi = au.sourceMidi != null && au.sourceMidi.length >= 14;
        if (!hasSource && !hasMix && !hasMidi) continue;
        File kid = new File(dir, "a" + (i++));
        if (!kid.mkdirs() && !kid.isDirectory()) continue;
        String name = au.combinedName == null ? "" : au.combinedName.replace("\n", " ");
        String meta = e.getKey().replace("\n", " ") + "\n" + name + "\n";
        Files.write(new File(kid, "meta.txt").toPath(), meta.getBytes(StandardCharsets.UTF_8));
        if (hasSource) Files.write(new File(kid, "source.wav").toPath(), au.sourceWav);
        if (hasMix) Files.write(new File(kid, "combined.wav").toPath(), au.combinedWav);
        if (au.drumWav != null && au.drumWav.length > 44) Files.write(new File(kid, "drum.wav").toPath(), au.drumWav);
        if (au.bedWav != null && au.bedWav.length > 44) Files.write(new File(kid, "bed.wav").toPath(), au.bedWav);
        if (hasMidi) Files.write(new File(kid, "source.mid").toPath(), au.sourceMidi);
      }
    } catch (Exception ignored) { /* optional */ }
  }

  private static void deleteTree(File f) {
    if (f == null || !f.exists()) return;
    if (f.isDirectory()) {
      File[] kids = f.listFiles();
      if (kids != null) for (File k : kids) deleteTree(k);
    }
    f.delete();
  }

  public static boolean isFileSetOrigin(String s) {
    return "midi".equals(s) || "analyze".equals(s) || "isolate".equals(s) || "compose".equals(s) || "program".equals(s);
  }

  /** Isolation, Analyze, and Compose keep the source file. No style database. */
  /**
   * True when a file set was taken as written (MIDI or program output, never restyled):
   * no part has a style label and no pattern has a style. Change style labels it.
   */
  static boolean unstyledFileSet(List<FileSetPart> parts, FileSet set) {
    boolean any = false;
    if (parts != null) {
      for (FileSetPart p : parts) {
        if (p == null) continue;
        if (p.styleLabel != null && !p.styleLabel.trim().isEmpty()) return false;
        any = true;
      }
    }
    for (Learned p : set.patterns) {
      if (p == null) continue;
      if (p.closest != null && !p.closest.isEmpty()) return false;
      any = true;
    }
    return any;
  }

  public static boolean fileSetStyleOn(String origin) {
    return !("analyze".equals(origin) || "isolate".equals(origin) || "compose".equals(origin));
  }

  public static String fileSetOriginMark(String origin) {
    if ("midi".equals(origin)) return "M";
    if ("analyze".equals(origin)) return "A";
    if ("isolate".equals(origin)) return "I";
    if ("compose".equals(origin)) return "C";
    if ("program".equals(origin)) return "EP";
    return "";
  }

  public static String fileSetOriginTitle(String origin) {
    if ("midi".equals(origin)) return "MIDI";
    if ("analyze".equals(origin)) return "Analyzed";
    if ("isolate".equals(origin)) return "Isolated";
    if ("compose".equals(origin)) return "Composed";
    if ("program".equals(origin)) return "Executed program";
    return "";
  }

  public static String fileSetMarked(String label, String origin) {
    String mark = fileSetOriginMark(origin);
    if (label == null) label = "";
    if (mark.isEmpty() || label.startsWith(mark + " ")) return label;
    return mark + " " + label;
  }

  public static void rememberFileSetOrigin(String source, String origin) {
    if (source == null || !isFileSetOrigin(origin)) return;
    fileSetOrigins.put(source, origin);
  }

  public static String fileSetOriginOf(String source) {
    if (source == null) return "";
    String o = fileSetOrigins.get(source);
    return isFileSetOrigin(o) ? o : "";
  }

  public static void forgetFileSetOrigin(String source) {
    if (source != null) fileSetOrigins.remove(source);
  }

  public static boolean isFillId(String s) {
    if (s == null) return false;
    for (String id : FILL_ID) if (id.equals(s)) return true;
    return false;
  }

  public static String fileSlug(String s, String fallback) {
    if (s == null) s = "";
    String n = s.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    if (n.length() > 28) n = n.substring(0, 28);
    return n.isEmpty() ? fallback : n;
  }

  public static String fsetFilename(String name) {
    String stem = name == null ? "" : name.replaceAll("[/\\\\?%*:|\"<>]", " ").replaceAll("\\s+", " ").trim();
    if (stem.length() > 40) stem = stem.substring(0, 40);
    if (stem.isEmpty()) stem = "set";
    return stem + ".fset";
  }

  /** Drop this file set's patterns and fills, then install the rebuilt ones. Returns the first pattern id. */
  public static String replaceFileSetLearned(
      String source,
      FileSet set,
      List<Learned> learned,
      List<LearnedFill> fills,
      Map<String, String> pairs) {
    String want = source == null ? "" : source;
    java.util.HashSet<String> dropP = new java.util.HashSet<String>();
    java.util.HashSet<String> dropF = new java.util.HashSet<String>();
    if (learned != null) for (Learned x : learned) if (want.equals(sourceOf(x))) dropP.add(x.id);
    if (fills != null) for (LearnedFill x : fills) if (want.equals(sourceOf(x))) dropF.add(x.id);
    if (learned != null) learned.removeIf(x -> dropP.contains(x.id));
    if (fills != null) fills.removeIf(x -> dropF.contains(x.id));
    if (pairs != null) {
      java.util.Iterator<Map.Entry<String, String>> it = pairs.entrySet().iterator();
      while (it.hasNext()) {
        Map.Entry<String, String> e = it.next();
        String k = e.getKey();
        String v = e.getValue();
        String pk = k != null && k.startsWith("l:") ? k.substring(2) : "";
        String fk = v != null && v.startsWith("l:") ? v.substring(2) : "";
        if (dropP.contains(pk) || dropF.contains(fk)) it.remove();
      }
    }
    if (set == null) return null;
    java.util.LinkedHashMap<String, String> ids = new java.util.LinkedHashMap<String, String>();
    String first = null;
    if (learned != null) {
      for (int i = set.patterns.size() - 1; i >= 0; i--) {
        Learned p = set.patterns.get(i);
        Learned item = new Learned();
        item.id = newLearnedId();
        item.name = p.name;
        item.bpm = p.bpm;
        item.closest = p.closest == null || p.closest.isEmpty() ? "" : p.closest;
        item.cells = copyCells(p.cells);
        item.source = want;
        item.tsNum = p.tsNum;
        item.tsDen = p.tsDen;
        item.swing = p.swing >= 0 ? p.swing : (item.closest.isEmpty() ? 0 : -1);
        item.human = item.closest.isEmpty() ? 0 : p.human;
        learned.add(0, item);
        ids.put(p.name, item.id);
        first = item.id;
      }
    }
    java.util.ArrayList<LearnedFill> imported = new java.util.ArrayList<LearnedFill>();
    if (fills != null) {
      for (int i = set.fills.size() - 1; i >= 0; i--) {
        LearnedFill f = set.fills.get(i);
        LearnedFill item = new LearnedFill();
        item.id = newLearnedId();
        item.name = f.name == null || f.name.isEmpty() ? "fill" : f.name;
        item.kind = f.kind == null ? "toms" : f.kind;
        item.cells = copyCells(f.cells);
        item.source = want;
        fills.add(0, item);
        imported.add(item);
      }
    }
    if (pairs != null) {
      for (String[] row : set.fillerns) {
        if (row == null || row.length < 2) continue;
        String pid = ids.get(row[0]);
        if (pid == null) continue;
        pairs.put("l:" + pid, resolveFsetFillKey(row[1], imported));
      }
    }
    return first;
  }

  public static FileSet collectFset(String source, String name, List<Learned> learned, List<LearnedFill> fills, Map<String, String> pairs) {
    String want = source == null ? "" : source;
    FileSet set = new FileSet();
    set.name = name == null || name.isEmpty() ? (want.isEmpty() ? "Other" : want) : name;
    if (learned != null) {
      for (Learned x : learned) if (want.equals(sourceOf(x))) set.patterns.add(x);
    }
    if (fills != null) {
      for (LearnedFill x : fills) if (want.equals(sourceOf(x))) set.fills.add(x);
    }
    java.util.LinkedHashSet<String> extra = new java.util.LinkedHashSet<String>();
    if (pairs != null) {
      for (Learned p : set.patterns) {
        String fk = pairs.get("l:" + p.id);
        if (fk == null || fk.isEmpty()) continue;
        String fillName = null;
        if (isFillId(fk)) fillName = fk;
        else if (fk.startsWith("l:")) {
          String id = fk.substring(2);
          if (fills != null) {
            for (LearnedFill f : fills) {
              if (id.equals(f.id)) {
                fillName = f.name;
                if (!want.equals(sourceOf(f))) extra.add(f.id);
                break;
              }
            }
          }
        }
        if (fillName != null) set.fillerns.add(new String[] { p.name, fillName });
      }
    }
    if (fills != null) {
      for (LearnedFill f : fills) if (extra.contains(f.id)) set.fills.add(f);
    }
    if (set.patterns.isEmpty() && set.fills.isEmpty()) return null;
    return set;
  }

  private static String uniqueFsetFile(String name, java.util.Set<String> used) {
    String n = name;
    int i = 2;
    int dot = name.lastIndexOf('.');
    String stem = dot > 0 ? name.substring(0, dot) : name;
    String ext = dot > 0 ? name.substring(dot) : "";
    while (used.contains(n)) n = stem + "-" + (i++) + ext;
    used.add(n);
    return n;
  }

  public static byte[] encodeFset(FileSet set) throws Exception {
    if (set != null) {
      if (!isFileSetOrigin(set.origin)) {
        String remembered = fileSetOriginOf(set.name);
        if (isFileSetOrigin(remembered)) set.origin = remembered;
      }
      unifyFileSetParts(set.parts, set);
    }
    LinkedHashMap<String, byte[]> files = new LinkedHashMap<String, byte[]>();
    java.util.LinkedHashSet<String> used = new java.util.LinkedHashSet<String>();
    StringBuilder json = new StringBuilder();
    json.append("{\"format\":\"pulsekit-fset\",\"v\":1,\"name\":").append(quote(set.name));
    if (isFileSetOrigin(set.origin)) json.append(",\"origin\":").append(quote(set.origin));
    float dur = set.durationSec > 0 ? set.durationSec : partsSpanSec(set.parts);
    if (dur > 0) {
      set.durationSec = round1(dur);
      json.append(",\"durationSec\":").append(set.durationSec);
    }
    json.append(",\"patterns\":[");
    for (int i = 0; i < set.patterns.size(); i++) {
      Learned p = set.patterns.get(i);
      String file = uniqueFsetFile("patterns/" + fileSlug(p.name, "pattern-" + i) + ".mid", used);
      files.put(file, encodeMidi(p.cells == null ? emptyCells() : p.cells, p.bpm, clampSteps(usedSteps(p.cells)), p.tsNum, p.tsDen));
      if (i > 0) json.append(',');
      json.append("{\"name\":").append(quote(p.name));
      json.append(",\"bpm\":").append(p.bpm);
      json.append(",\"tsNum\":").append(p.tsNum);
      json.append(",\"tsDen\":").append(p.tsDen);
      if (p.closest != null && !p.closest.isEmpty()) json.append(",\"closest\":").append(quote(p.closest));
      json.append(",\"file\":").append(quote(file));
      json.append(",\"pattern\":").append(cellsJson(p.cells == null ? emptyCells() : p.cells, STEPS)).append('}');
    }
    json.append("],\"fills\":[");
    used.clear();
    for (int i = 0; i < set.fills.size(); i++) {
      LearnedFill f = set.fills.get(i);
      String file = uniqueFsetFile("fills/" + fileSlug(f.name, "fill-" + i) + ".mid", used);
      files.put(file, encodeMidi(lastBar(f.cells), 120, STEPS));
      if (i > 0) json.append(',');
      json.append("{\"name\":").append(quote(f.name));
      if (f.kind != null) json.append(",\"kind\":").append(quote(f.kind));
      json.append(",\"file\":").append(quote(file));
      json.append(",\"pattern\":").append(cellsJson(f.cells == null ? emptyCells() : f.cells, STEPS)).append('}');
    }
    json.append("],\"fillerns\":[");
    used.clear();
    for (int i = 0; i < set.fillerns.size(); i++) {
      String[] row = set.fillerns.get(i);
      if (i > 0) json.append(',');
      json.append("{\"pattern\":").append(quote(row[0])).append(",\"fill\":").append(quote(row[1]));
      Learned groove = null;
      for (Learned p : set.patterns) if (p.name.equals(row[0])) { groove = p; break; }
      int[][] fillCells = null;
      if (isFillId(row[1])) {
        fillCells = buildFill(row[1], groove != null ? groove.cells : emptyCells(), groove != null ? groove.closest : "house");
      } else {
        for (LearnedFill f : set.fills) if (f.name.equals(row[1])) { fillCells = f.cells; break; }
      }
      if (groove != null && fillCells != null) {
        String file = uniqueFsetFile("fillerns/" + fileSlug(row[0], "fillern-" + i) + ".mid", used);
        int[][] both = cellsForMidiRole(groove.cells, fillCells, "fillern");
        files.put(file, encodeMidi(both, groove.bpm, MAX_STEPS));
        json.append(",\"file\":").append(quote(file));
      }
      json.append('}');
    }
    json.append("],\"parts\":").append(fileSetPartsJson(set.parts));
    FileSetAudio au = audioFor(set);
    if (set.sourceWav != null && set.sourceWav.length > 0) {
      if (au == null) {
        rememberFileSetAudio(set.name, set.sourceWav, set.combinedWav, set.combinedName);
        au = fileSetAudioOf(set.name);
      } else if (au.sourceWav == null) au.sourceWav = set.sourceWav;
    }
    if (set.combinedWav != null && set.combinedWav.length > 0) {
      rememberFileSetAudio(set.name, null, set.combinedWav, set.combinedName);
      au = fileSetAudioOf(set.name);
    }
    if (au != null && au.sourceWav != null && au.sourceWav.length > 0) {
      files.put("source.wav", au.sourceWav);
      json.append(",\"sourceAudio\":\"source.wav\"");
    }
    if (au != null && au.combinedWav != null && au.combinedWav.length > 0) {
      String combinedName = combinedAudioName(au.combinedName != null && !au.combinedName.isEmpty() ? au.combinedName : set.name);
      files.put(combinedName, au.combinedWav);
      json.append(",\"combinedAudio\":").append(quote(combinedName));
    }
    byte[] midi = set.sourceMidi;
    String midiName = set.sourceMidiName;
    if ((midi == null || midi.length < 14) && au != null && au.sourceMidi != null && au.sourceMidi.length >= 14) {
      midi = au.sourceMidi;
      midiName = au.sourceMidiName;
    }
    if (midi != null && midi.length >= 14) {
      String file = midiName == null || midiName.length() == 0 ? "source.mid" : midiName;
      int slash = Math.max(file.lastIndexOf('/'), file.lastIndexOf('\\'));
      if (slash >= 0) file = file.substring(slash + 1);
      if (!file.toLowerCase().endsWith(".mid") && !file.toLowerCase().endsWith(".midi")) file = file + ".mid";
      files.put(file, midi);
      json.append(",\"sourceMidi\":").append(quote(file));
    }
    json.append("}");
    LinkedHashMap<String, byte[]> ordered = new LinkedHashMap<String, byte[]>();
    ordered.put("set.json", json.toString().getBytes(StandardCharsets.UTF_8));
    ordered.putAll(files);
    return zipStored(ordered);
  }

  public static FileSet decodeFset(byte[] data) throws Exception {
    Map<String, byte[]> files = unzip(data);
    byte[] jsonBytes = files.get("set.json");
    if (jsonBytes == null) throw new IllegalArgumentException("Could not read that .fset");
    String json = new String(jsonBytes, StandardCharsets.UTF_8);
    if (!json.contains("pulsekit-fset")) throw new IllegalArgumentException("Could not read that .fset");
    FileSet set = new FileSet();
    String name = jsonStr(json, "\"name\"");
    if (name != null && !name.isEmpty()) set.name = name;
    String origin = jsonStr(json, "\"origin\"");
    if (isFileSetOrigin(origin)) set.origin = origin;
    for (String obj : jsonObjects(json, "patterns")) {
      Learned item = new Learned();
      item.name = jsonStr(obj, "\"name\"");
      if (item.name == null) continue;
      item.bpm = clamp(jsonInt(obj, "\"bpm\"", 120), 40, 240);
      item.tsNum = jsonInt(obj, "\"tsNum\"", 4);
      item.tsDen = jsonInt(obj, "\"tsDen\"", 4);
      item.closest = jsonStr(obj, "\"closest\"");
      item.cells = emptyCells();
      patternFromJson(obj, item.cells);
      if (hitCount(item.cells) < 1) {
        String file = jsonStr(obj, "\"file\"");
        byte[] midi = file == null ? null : files.get(file);
        if (midi != null) item.cells = parseMidi(midi);
      }
      if (hitCount(item.cells) < 1) continue;
      set.patterns.add(item);
    }
    for (String obj : jsonObjects(json, "fills")) {
      LearnedFill item = new LearnedFill();
      item.name = jsonStr(obj, "\"name\"");
      if (item.name == null) continue;
      item.kind = jsonStr(obj, "\"kind\"");
      if (item.kind == null) item.kind = "toms";
      item.cells = emptyCells();
      patternFromJson(obj, item.cells);
      if (hitCount(item.cells) < 1) {
        String file = jsonStr(obj, "\"file\"");
        byte[] midi = file == null ? null : files.get(file);
        if (midi != null) item.cells = lastBar(parseMidi(midi));
      }
      if (hitCount(item.cells) < 1) continue;
      set.fills.add(item);
    }
    for (String obj : jsonObjects(json, "fillerns")) {
      String p = jsonStr(obj, "\"pattern\"");
      String f = jsonStr(obj, "\"fill\"");
      if (p != null && f != null) set.fillerns.add(new String[] { p, f });
    }
    set.parts.addAll(parseFileSetParts(json));
    set.durationSec = jsonFloat(json, "\"durationSec\"", 0);
    if (!(set.durationSec > 0)) set.durationSec = partsSpanSec(set.parts);
    byte[] sourceWav = files.get("source.wav");
    String combinedPath = jsonStr(json, "\"combinedAudio\"");
    byte[] combinedWav = combinedPath == null ? null : files.get(combinedPath);
    if (combinedWav == null) {
      for (Map.Entry<String, byte[]> e : files.entrySet()) {
        String base = e.getKey();
        int slash = base.lastIndexOf('/');
        if (slash >= 0) base = base.substring(slash + 1);
        if (base.startsWith("combined_") && base.toLowerCase().endsWith(".wav")) {
          combinedPath = base;
          combinedWav = e.getValue();
          break;
        }
      }
    }
    set.sourceWav = sourceWav;
    set.combinedWav = combinedWav;
    set.combinedName = combinedPath == null ? "" : combinedPath;
    String midiKey = jsonStr(json, "\"sourceMidi\"");
    byte[] sourceMidi = midiKey == null ? null : files.get(midiKey);
    if (sourceMidi == null) sourceMidi = files.get("source.mid");
    set.sourceMidi = sourceMidi;
    set.sourceMidiName = midiKey == null || midiKey.length() == 0 ? (sourceMidi == null ? "" : "source.mid") : midiKey;
    if (sourceMidi != null && sourceMidi.length >= 14) rememberFileSetMidi(set.name, sourceMidi, set.sourceMidiName);
    if ((sourceWav != null && sourceWav.length > 0) || (combinedWav != null && combinedWav.length > 0)) {
      rememberFileSetAudio(set.name, sourceWav, combinedWav, set.combinedName);
    }
    unifyFileSetParts(set.parts, set);
    if (set.patterns.isEmpty() && set.fills.isEmpty()) throw new IllegalArgumentException("Could not read that .fset");
    return set;
  }

  public static boolean isFset(byte[] data) {
    if (data == null || data.length < 4) return false;
    if (data[0] != 'P' || data[1] != 'K') return false;
    try {
      Map<String, byte[]> files = unzip(data);
      byte[] json = files.get("set.json");
      if (json == null) return false;
      return new String(json, StandardCharsets.UTF_8).contains("pulsekit-fset");
    } catch (Exception ex) {
      return false;
    }
  }

  public static String resolveFsetFillKey(String fillName, List<LearnedFill> imported) {
    if (fillName == null) return "toms";
    if (imported != null) {
      for (LearnedFill f : imported) if (fillName.equals(f.name)) return "l:" + f.id;
    }
    if (isFillId(fillName)) return fillName;
    String low = fillName.toLowerCase();
    if (imported != null) {
      for (LearnedFill f : imported) if (f.name != null && f.name.toLowerCase().equals(low)) return "l:" + f.id;
    }
    for (int i = 0; i < FILL_ID.length; i++) {
      if (FILL_ID[i].equals(low) || FILL_LABEL[i].toLowerCase().equals(low)) return FILL_ID[i];
    }
    return "toms";
  }

  public static String partJson(FileSetPart p) {
    if (p == null) p = new FileSetPart();
    StringBuilder json = new StringBuilder();
    json.append("{\"name\":").append(quote(p.name == null ? "Part" : p.name));
    json.append(",\"kind\":").append(quote(p.kind == null ? "groove" : p.kind));
    json.append(",\"bpm\":").append(p.bpm);
    json.append(",\"tsNum\":").append(p.tsNum);
    json.append(",\"tsDen\":").append(p.tsDen);
    json.append(",\"styleLabel\":").append(quote(p.styleLabel == null ? "" : p.styleLabel));
    json.append(",\"bars\":").append(p.bars);
    json.append(",\"startSec\":").append(p.startSec);
    json.append(",\"endSec\":").append(p.endSec);
    json.append(",\"hits\":").append(p.hits);
    json.append(",\"swing\":").append(p.swing);
    json.append(",\"density\":").append(p.density);
    json.append('}');
    return json.toString();
  }

  public static String fileSetPartsJson(List<FileSetPart> parts) {
    StringBuilder json = new StringBuilder();
    json.append('[');
    if (parts != null) {
      for (int i = 0; i < parts.size(); i++) {
        if (i > 0) json.append(',');
        json.append(partJson(parts.get(i)));
      }
    }
    json.append(']');
    return json.toString();
  }

  public static FileSetPart parseFileSetPart(String obj) {
    if (obj == null) return null;
    String name = jsonStr(obj, "\"name\"");
    if (name == null || name.isEmpty()) return null;
    FileSetPart p = new FileSetPart();
    p.name = name;
    String kind = jsonStr(obj, "\"kind\"");
    if (kind != null && !kind.isEmpty()) p.kind = kind;
    p.bpm = clamp(jsonInt(obj, "\"bpm\"", 120), 40, 240);
    p.tsNum = jsonInt(obj, "\"tsNum\"", 4);
    p.tsDen = jsonInt(obj, "\"tsDen\"", 4);
    String lab = jsonStr(obj, "\"styleLabel\"");
    p.styleLabel = lab == null ? "" : lab;
    p.bars = Math.max(1, jsonInt(obj, "\"bars\"", 1));
    p.startSec = jsonFloat(obj, "\"startSec\"", 0);
    p.endSec = jsonFloat(obj, "\"endSec\"", 0);
    p.hits = Math.max(0, jsonInt(obj, "\"hits\"", 0));
    p.swing = Math.max(0, jsonInt(obj, "\"swing\"", 0));
    p.density = jsonFloat(obj, "\"density\"", 0);
    if (p.density < 0) p.density = 0;
    if (p.density > 1) p.density = 1;
    return p;
  }

  public static List<FileSetPart> parseFileSetParts(String json) {
    List<FileSetPart> out = new ArrayList<FileSetPart>();
    if (json == null) return out;
    for (String obj : jsonObjects(json, "parts")) {
      FileSetPart p = parseFileSetPart(obj);
      if (p != null) out.add(p);
    }
    return out;
  }

  public static float round1(float n) {
    return Math.round(n * 10f) / 10f;
  }

  public static float partsSpanSec(List<FileSetPart> parts) {
    float m = 0;
    if (parts != null) {
      for (FileSetPart p : parts) {
        if (p != null && p.endSec > m) m = p.endSec;
      }
    }
    return m;
  }

  public static void fitFileSetParts(List<FileSetPart> parts, float durationSec) {
    if (parts == null || parts.isEmpty() || !(durationSec > 0)) return;
    float span = partsSpanSec(parts);
    if (span <= 0) {
      int n = parts.size();
      for (int i = 0; i < n; i++) {
        FileSetPart p = parts.get(i);
        p.startSec = round1((i / (float) n) * durationSec);
        p.endSec = round1(((i + 1f) / n) * durationSec);
      }
    } else {
      float s = durationSec / span;
      for (FileSetPart p : parts) {
        p.startSec = Math.max(0, round1(p.startSec * s));
        p.endSec = Math.max(0, round1(p.endSec * s));
      }
    }
    parts.get(0).startSec = 0;
    parts.get(parts.size() - 1).endSec = round1(durationSec);
  }

  public static String fmtClock(float sec) {
    int s = Math.max(0, Math.round(sec));
    return (s / 60) + ":" + String.format("%02d", Integer.valueOf(s % 60));
  }

  public static String fileSetLengthLine(List<FileSetPart> parts, float durationSec) {
    float span = partsSpanSec(parts);
    float file = durationSec > 0 ? durationSec : span;
    String a = fmtClock(span);
    String b = fmtClock(file);
    if (span <= 0 && file <= 0) return "0:00";
    if (a.equals(b)) return b;
    return a + " parts \u00b7 " + b + " file";
  }

  public static int unifySongBpm(List<FileSetPart> parts, FileSet set, int fallback) {
    int fb = clampBpm(fallback);
    java.util.LinkedHashMap<Integer, Double> w = new java.util.LinkedHashMap<Integer, Double>();
    if (parts != null) {
      for (FileSetPart p : parts) {
        int b = clampBpm(p.bpm);
        double wt = Math.max(1, p.bars);
        String kind = p.kind == null ? "" : p.kind.toLowerCase();
        if ("fill".equals(kind) || "break".equals(kind) || "rest".equals(kind)) wt *= 0.12;
        else if ("chorus".equals(kind)) wt *= 1.2;
        Double cur = w.get(Integer.valueOf(b));
        w.put(Integer.valueOf(b), Double.valueOf((cur == null ? 0 : cur.doubleValue()) + wt));
      }
    }
    if (set != null) {
      for (Learned p : set.patterns) {
        int b = clampBpm(p.bpm);
        Double cur = w.get(Integer.valueOf(b));
        w.put(Integer.valueOf(b), Double.valueOf((cur == null ? 0 : cur.doubleValue()) + 1));
      }
    }
    if (w.isEmpty()) return fb;
    int best = fb;
    double bestW = -1;
    for (java.util.Map.Entry<Integer, Double> e : w.entrySet()) {
      int b = e.getKey().intValue();
      double wt = e.getValue().doubleValue();
      if (wt > bestW || (wt == bestW && (b == fb || Math.abs(b - fb) < Math.abs(best - fb)))) {
        bestW = wt;
        best = b;
      }
    }
    return best;
  }

  public static void unifyFileSetParts(List<FileSetPart> parts, FileSet set) {
    if (set != null && (!fileSetStyleOn(set.origin) || unstyledFileSet(parts, set))) {
      int fbBpm = 120;
      if (parts != null && !parts.isEmpty()) fbBpm = parts.get(0).bpm;
      else if (!set.patterns.isEmpty()) fbBpm = set.patterns.get(0).bpm;
      int bpm = unifySongBpm(parts, set, fbBpm);
      if (parts != null) {
        for (FileSetPart p : parts) {
          if (p == null) continue;
          p.styleLabel = "";
          p.swing = 0;
          p.bpm = bpm;
        }
      }
      for (Learned p : set.patterns) {
        if (p == null) continue;
        p.closest = "";
        p.bpm = bpm;
        p.swing = 0;
        p.human = 0;
      }
      float dur = set.durationSec > 0 ? set.durationSec : partsSpanSec(parts);
      if (dur > 0) {
        set.durationSec = "compose".equals(set.origin) ? dur : round1(dur);
        fitFileSetParts(parts, set.durationSec);
      }
      return;
    }
    java.util.ArrayList<String> kits = new java.util.ArrayList<String>();
    java.util.ArrayList<Double> weights = new java.util.ArrayList<Double>();
    if (parts != null) {
      for (FileSetPart p : parts) {
        String kit = StyleDb.fromLabel(p.styleLabel);
        if (kit == null) kit = "pop";
        double w = Math.max(1, p.bars);
        String kind = p.kind == null ? "" : p.kind.toLowerCase();
        if ("fill".equals(kind) || "break".equals(kind) || "rest".equals(kind)) w *= 0.12;
        else if ("chorus".equals(kind)) w *= 1.2;
        kits.add(kit);
        weights.add(Double.valueOf(w));
      }
    }
    if (set != null) {
      for (Learned p : set.patterns) {
        if (p.closest != null && !p.closest.isEmpty()) {
          kits.add(p.closest);
          weights.add(Double.valueOf(1));
        }
      }
    }
    String fallback = "pop";
    if (set != null && !set.patterns.isEmpty() && set.patterns.get(0).closest != null) fallback = set.patterns.get(0).closest;
    String kit = StyleDb.unify(kits, weights, fallback);
    Style st = styles().get(kit);
    String label = st != null && st.label != null && !st.label.isEmpty() ? st.label : kit;
    if (parts != null && !parts.isEmpty()) {
      boolean same = true;
      java.util.HashMap<String, Integer> counts = new java.util.HashMap<String, Integer>();
      for (FileSetPart p : parts) {
        String lab = p.styleLabel == null ? "" : p.styleLabel.trim();
        if (lab.isEmpty() || !kit.equals(StyleDb.fromLabel(lab))) {
          same = false;
          break;
        }
        Integer n = counts.get(lab);
        counts.put(lab, Integer.valueOf((n == null ? 0 : n.intValue()) + 1));
      }
      if (same && !counts.isEmpty()) {
        int best = 0;
        for (java.util.Map.Entry<String, Integer> e : counts.entrySet()) {
          if (e.getValue().intValue() > best) {
            best = e.getValue().intValue();
            label = e.getKey();
          }
        }
      }
    }
    int swing = StyleDb.swingOf(kit);
    if (parts != null && !parts.isEmpty()) {
      boolean sameSwing = true;
      int s0 = parts.get(0).swing;
      for (FileSetPart p : parts) {
        if (p.swing != s0) sameSwing = false;
      }
      if (sameSwing) swing = s0;
    }
    int fbBpm = 120;
    if (parts != null && !parts.isEmpty()) fbBpm = parts.get(0).bpm;
    else if (set != null && !set.patterns.isEmpty()) fbBpm = set.patterns.get(0).bpm;
    int bpm = unifySongBpm(parts, set, fbBpm);
    if (parts != null) {
      for (FileSetPart p : parts) {
        p.styleLabel = label;
        p.swing = swing;
        p.bpm = bpm;
      }
    }
    if (set != null) {
      for (Learned p : set.patterns) {
        p.closest = kit;
        p.bpm = bpm;
      }
      boolean compose = "compose".equals(set.origin);
      float dur = set.durationSec > 0 ? set.durationSec : partsSpanSec(parts);
      if (dur > 0) {
        set.durationSec = compose ? dur : round1(dur);
        fitFileSetParts(parts, set.durationSec);
      }
    } else if (parts != null) {
      float dur = partsSpanSec(parts);
      if (dur > 0) fitFileSetParts(parts, dur);
    }
  }

  public static List<FileSetPart> partsForDisplay(FileSet set) {
    if (set == null) return new ArrayList<FileSetPart>();
    if (set.parts != null && !set.parts.isEmpty()) {
      unifyFileSetParts(set.parts, set);
      return set.parts;
    }
    List<FileSetPart> out = new ArrayList<FileSetPart>();
    for (int i = 0; i < set.patterns.size(); i++) {
      Learned p = set.patterns.get(i);
      FileSetPart x = new FileSetPart();
      x.name = p.name;
      x.kind = "groove";
      x.bpm = p.bpm;
      x.tsNum = p.tsNum;
      x.tsDen = p.tsDen;
      if (p.closest != null) {
        Style st = styles().get(p.closest);
        x.styleLabel = st != null ? st.label : p.closest;
      }
      x.bars = 1;
      x.startSec = i;
      x.endSec = i + 1;
      x.hits = hitCount(p.cells);
      x.density = Math.min(1f, x.hits / 32f);
      out.add(x);
    }
    unifyFileSetParts(out, set);
    return out;
  }

  private static boolean nameEq(String a, String b) {
    if (a == null || b == null) return false;
    return a.trim().equalsIgnoreCase(b.trim());
  }

  private static Learned findPattern(FileSet set, String name) {
    if (set == null || name == null) return null;
    for (Learned p : set.patterns) if (nameEq(p.name, name)) return p;
    return null;
  }

  private static LearnedFill findFill(FileSet set, String name) {
    if (set == null || name == null) return null;
    for (LearnedFill f : set.fills) if (nameEq(f.name, name)) return f;
    return null;
  }

  private static String fillernFill(FileSet set, String patternName) {
    if (set == null || patternName == null) return null;
    for (String[] row : set.fillerns) {
      if (row != null && row.length >= 2 && nameEq(row[0], patternName)) return row[1];
    }
    return null;
  }

  private static int[][] fillCellsNamed(FileSet set, String name) {
    LearnedFill fill = findFill(set, name);
    if (fill != null && fill.cells != null) return lastBar(fill.cells);
    Learned pat = findPattern(set, name);
    if (pat != null && pat.cells != null) return lastBar(pat.cells);
    return null;
  }

  private static Part stampTs(Part p, int tsNum, int tsDen) {
    p.tsNum = clampTsNum(tsNum);
    p.tsDen = clampTsDen(tsDen);
    int bar = barSteps(p.tsNum, p.tsDen);
    int used = usedSteps(p.cells);
    p.steps = clampSteps(Math.max(bar, used));
    return p;
  }

  /** Arrangement for Song → Imported from a file set's parts, patterns, and Fillerns. */
  public static List<Part> songFromFileSet(FileSet set) {
    return songFromFileSet(set, null);
  }

  /** Same arrangement. When `sectionSecs` is set, one entry per file-set part: drum time before it is fit onto the file. */
  public static List<Part> songFromFileSet(FileSet set, java.util.List<Float> sectionSecs) {
    List<Part> out = new ArrayList<Part>();
    if (set == null) return out;
    List<FileSetPart> timeline = partsForDisplay(set);
    int songBpm = 120;
    if (!timeline.isEmpty() && timeline.get(0).bpm > 0) songBpm = timeline.get(0).bpm;
    boolean useWall = !reconstructedParts(timeline);
    boolean asWritten = !timeline.isEmpty() && unstyledFileSet(timeline, set);
    float sixteenth = 60f / Math.max(MIN_BPM, songBpm) / 4f;
    if (sectionSecs != null) {
      sectionSecs.clear();
      for (int i = 0; i < timeline.size(); i++) sectionSecs.add(Float.valueOf(0f));
    }
    for (int i = 0; i < timeline.size(); i++) {
      if (out.size() >= MAX_SONG) break;
      float before = songDurationSec(out);
      FileSetPart info = timeline.get(i);
      String nextKind = i + 1 < timeline.size() && timeline.get(i + 1).kind != null
          ? timeline.get(i + 1).kind.toLowerCase() : "";
      String kind = info.kind == null ? "groove" : info.kind.toLowerCase();
      int tsNum = info.tsNum > 0 ? info.tsNum : 4;
      int tsDen = info.tsDen > 0 ? info.tsDen : 4;
      int bpm = songBpm;
      int bar = barSteps(tsNum, tsDen);

      if ("fill".equals(kind)) {
        String prev = out.isEmpty() ? null : out.get(out.size() - 1).name;
        String pair = fillernFill(set, prev);
        int[][] cells = fillCellsNamed(set, info.name);
        if (cells == null) cells = fillCellsNamed(set, info.name + " fill");
        if (cells == null) cells = fillCellsNamed(set, pair);
        if (cells == null && !set.fills.isEmpty()) cells = lastBar(set.fills.get(0).cells);
        if (cells == null) cells = emptyCells();
        LearnedFill named = findFill(set, info.name);
        if (named == null) named = findFill(set, info.name + " fill");
        if (named == null) named = findFill(set, pair);
        int n = useWall ? sizedRepeats(info, bpm, bar, true) : Math.min(Math.max(1, info.bars > 0 ? info.bars : 1), 8);
        Part proto = fill(named != null ? named.name : (info.name == null ? "fill" : info.name), bpm, cells, 1);
        stampTs(proto, tsNum, tsDen);
        addSongChunks(out, proto, n);
        noteSection(sectionSecs, i, songDurationSec(out) - before);
        continue;
      }

      if (("break".equals(kind) || "rest".equals(kind)) && info.hits < 2) {
        Part proto = rest(bpm, 1);
        proto.name = info.name == null || info.name.isEmpty() ? "Silent" : info.name;
        stampTs(proto, tsNum, tsDen);
        addSongChunks(out, proto, sizedRepeats(info, bpm, bar, useWall));
        noteSection(sectionSecs, i, songDurationSec(out) - before);
        continue;
      }

      Learned pat = findPattern(set, info.name);
      if (pat == null && i < set.patterns.size()) pat = set.patterns.get(i);
      if (pat == null && !set.patterns.isEmpty()) pat = set.patterns.get(0);
      if (pat == null) {
        noteSection(sectionSecs, i, 0f);
        continue;
      }
      int gTsNum = pat.tsNum > 0 ? pat.tsNum : tsNum;
      int gTsDen = pat.tsDen > 0 ? pat.tsDen : tsDen;
      int steps = clampSteps(Math.max(barSteps(gTsNum, gTsDen), usedSteps(pat.cells)));
      // A set kept as written plays its parts in order: a pattern's Fillern is not added where the file had none.
      String fname = !asWritten && !"fill".equals(nextKind) ? fillernFill(set, pat.name) : null;
      int[][] fp = fname != null ? fillCellsNamed(set, fname) : null;
      boolean addFillern = fp != null && hitCount(fp) >= 1;
      float grooveDur = Math.max(0, info.endSec - info.startSec);
      if (useWall && addFillern) grooveDur = Math.max(0, grooveDur - bar * sixteenth);
      int grooveRepeats;
      if (!useWall) grooveRepeats = Math.max(1, info.bars > 0 ? info.bars : 1);
      else if (grooveDur >= 0.45f * steps * sixteenth) grooveRepeats = repeatsForDuration(grooveDur, bpm, steps);
      else grooveRepeats = 0;
      if (grooveRepeats > 0) {
        Part proto = groove(pat.name, bpm, pat.cells, 1);
        stampTs(proto, gTsNum, gTsDen);
        addSongChunks(out, proto, grooveRepeats);
      }
      if (addFillern) {
        LearnedFill named = findFill(set, fname);
        Part fill = fill(named != null ? named.name : (fname == null ? "fill" : fname), bpm, fp, 1);
        out.add(stampTs(fill, gTsNum, gTsDen));
      }
      noteSection(sectionSecs, i, songDurationSec(out) - before);
    }
    if (useWall) {
      float target = set.durationSec > 0 ? set.durationSec : partsSpanSec(timeline);
      if (target >= 3) fitSongToDuration(out, target);
    }
    if (sectionSecs != null && !sectionSecs.isEmpty()) {
      float sum = 0f;
      for (int i = 0; i < sectionSecs.size(); i++) sum += sectionSecs.get(i).floatValue();
      float delta = songDurationSec(out) - sum;
      if (Math.abs(delta) > 0.0001f) {
        int idx = sectionSecs.size() - 1;
        for (int k = timeline.size() - 1; k >= 0; k--) {
          String kind = timeline.get(k).kind == null ? "" : timeline.get(k).kind.toLowerCase();
          if (!"fill".equals(kind)) { idx = k; break; }
        }
        if (idx >= 0 && idx < sectionSecs.size()) {
          sectionSecs.set(idx, Float.valueOf(Math.max(0f, sectionSecs.get(idx).floatValue() + delta)));
        }
      }
    }
    return out;
  }

  private static void noteSection(java.util.List<Float> sectionSecs, int index, float dur) {
    if (sectionSecs == null || index < 0 || index >= sectionSecs.size()) return;
    sectionSecs.set(index, Float.valueOf(sectionSecs.get(index).floatValue() + Math.max(0f, dur)));
  }

  private static boolean reconstructedParts(List<FileSetPart> parts) {
    if (parts == null || parts.isEmpty()) return true;
    for (int i = 0; i < parts.size(); i++) {
      FileSetPart p = parts.get(i);
      if (p.bars != 1 || Math.abs(p.startSec - i) > 0.05f || Math.abs(p.endSec - (i + 1)) > 0.05f) return false;
    }
    return true;
  }

  private static int sizedRepeats(FileSetPart info, int bpm, int steps, boolean useWall) {
    if (!useWall) return Math.max(1, info.bars > 0 ? info.bars : 1);
    float wall = Math.max(0, info.endSec - info.startSec);
    if (!(wall > 0)) return Math.max(1, info.bars > 0 ? info.bars : 1);
    return repeatsForDuration(wall, bpm, steps);
  }

  private static void addSongChunks(List<Part> out, Part proto, int repeats) {
    int left = Math.max(1, repeats);
    while (left > 0 && out.size() < MAX_SONG) {
      int n = Math.min(32, left);
      Part p = copyPart(proto);
      p.repeats = n;
      out.add(p);
      left -= n;
    }
  }

  public static String encodeFsetInfo(Map<String, List<FileSetPart>> map) {
    return encodeFsetInfo(map, fileSetOrigins);
  }

  public static String encodeFsetInfo(Map<String, List<FileSetPart>> map, Map<String, String> origins) {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"format\":\"pulsekit-fset-info\",\"items\":[");
    int i = 0;
    if (map != null) {
      for (Map.Entry<String, List<FileSetPart>> e : map.entrySet()) {
        if (i++ > 0) sb.append(',');
        sb.append("{\"source\":").append(quote(e.getKey() == null ? "" : e.getKey()));
        sb.append(",\"parts\":").append(fileSetPartsJson(e.getValue())).append('}');
      }
    }
    sb.append("],\"origins\":[");
    int j = 0;
    if (origins != null) {
      for (Map.Entry<String, String> e : origins.entrySet()) {
        if (!isFileSetOrigin(e.getValue())) continue;
        if (j++ > 0) sb.append(',');
        sb.append("{\"source\":").append(quote(e.getKey() == null ? "" : e.getKey()));
        sb.append(",\"origin\":").append(quote(e.getValue())).append('}');
      }
    }
    sb.append("]}");
    return sb.toString();
  }

  public static LinkedHashMap<String, List<FileSetPart>> decodeFsetInfo(String json) {
    LinkedHashMap<String, List<FileSetPart>> out = new LinkedHashMap<String, List<FileSetPart>>();
    if (json == null) return out;
    for (String obj : jsonObjects(json, "items")) {
      String src = jsonStr(obj, "\"source\"");
      if (src == null) continue;
      List<FileSetPart> parts = new ArrayList<FileSetPart>();
      for (String p : jsonObjects(obj, "parts")) {
        FileSetPart part = parseFileSetPart(p);
        if (part != null) parts.add(part);
      }
      out.put(src, parts);
    }
    return out;
  }

  public static LinkedHashMap<String, String> decodeFsetOrigins(String json) {
    LinkedHashMap<String, String> out = new LinkedHashMap<String, String>();
    if (json == null) return out;
    for (String obj : jsonObjects(json, "origins")) {
      String src = jsonStr(obj, "\"source\"");
      String origin = jsonStr(obj, "\"origin\"");
      if (src != null && isFileSetOrigin(origin)) out.put(src, origin);
    }
    return out;
  }

  public static boolean isPrj(byte[] data) {
    if (data == null || data.length < 4) return false;
    if (data[0] != 'P' || data[1] != 'K') return false;
    try {
      Map<String, byte[]> files = unzip(data);
      byte[] json = files.get("project.json");
      if (json == null) return false;
      String s = new String(json, StandardCharsets.UTF_8);
      return s.contains("pulsekit-prj");
    } catch (Exception ex) {
      return false;
    }
  }

  public static boolean isPlugin(byte[] data) {
    if (data == null || data.length < 4) return false;
    if (data[0] == '{') {
      String s = new String(data, 0, Math.min(data.length, 120), StandardCharsets.UTF_8);
      return s.contains("pulsekit-plugin");
    }
    if (data[0] != 'P' || data[1] != 'K') return false;
    try {
      Map<String, byte[]> files = unzip(data);
      byte[] json = files.get("plugin.json");
      if (json == null) return false;
      return new String(json, StandardCharsets.UTF_8).contains("pulsekit-plugin");
    } catch (Exception ex) {
      return false;
    }
  }

  private Engine() {}
}
