package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An LV2 bundle (a folder: manifest.ttl, the plugins' .ttl files and libraries) read as the host
 * needs it: each plugin's URI, name, library and ports (kind, direction, symbol, name, default,
 * range), and whether it takes MIDI. Its Turtle (.ttl) is read here (Turtle) instead of with lilv:
 * no C libraries for it in Pulsekit.
 */
public final class Lv2Bundle {
  public static final int AUDIO = 0, CONTROL = 1, ATOM = 2, CV = 3, OTHER = 4;

  static final String RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
  static final String LV2 = "http://lv2plug.in/ns/lv2core#";
  static final String ATOM_NS = "http://lv2plug.in/ns/ext/atom#";
  static final String MIDI_EVENT = "http://lv2plug.in/ns/ext/midi#MidiEvent";
  static final String EVENT_PORT = "http://lv2plug.in/ns/ext/event#EventPort";
  static final String DOAP_NAME = "http://usefulinc.com/ns/doap#name";
  static final String RDFS_SEE_ALSO = "http://www.w3.org/2000/01/rdf-schema#seeAlso";

  /** A port of a plugin. */
  public static final class Port {
    public int index;
    public int kind = OTHER;
    public boolean output;
    public boolean midi;
    public String symbol = "";
    public String name = "";
    public float value;
    public float min = Float.NaN;
    public float max = Float.NaN;
    public boolean optional;
  }

  /** A plugin of the bundle. */
  public static final class Plugin {
    public String uri = "";
    public String name = "";
    public File binary;
    public File bundle;
    public final List<Port> ports = new ArrayList<Port>();
    public final List<String> required = new ArrayList<String>();
    public boolean instrument;

    /** It takes MIDI (an atom input that supports MIDI events). */
    public boolean takesMidi() {
      for (Port p : this.ports) if (p.kind == ATOM && !p.output && p.midi) return true;
      return false;
    }

    public int audioIns() {
      int n = 0;
      for (Port p : this.ports) if (p.kind == AUDIO && !p.output) n++;
      return n;
    }

    public int audioOuts() {
      int n = 0;
      for (Port p : this.ports) if (p.kind == AUDIO && p.output) n++;
      return n;
    }

    /** "instrument" (MIDI in), "effect" (audio in) or "other". */
    public String role() {
      return this.takesMidi() ? "instrument" : this.audioIns() > 0 ? "effect" : "other";
    }

    /** The host's features the plugin cannot do without that Pulsekit's host does not have. */
    public List<String> missingFeatures() {
      List<String> out = new ArrayList<String>();
      for (String f : this.required) if (!HOST_FEATURES.contains(f)) out.add(f.substring(f.lastIndexOf('/') + 1));
      return out;
    }

    /** Ports sorted by index (the arrays lv2_render.c takes). */
    public int[] kinds() {
      int[] k = new int[this.ports.size()];
      for (Port p : this.ports) if (p.index < k.length) k[p.index] = p.kind;
      return k;
    }

    public int[] outs() {
      int[] o = new int[this.ports.size()];
      for (Port p : this.ports) if (p.index < o.length) o[p.index] = p.output ? 1 : 0;
      return o;
    }

    public float[] values() {
      float[] v = new float[this.ports.size()];
      for (Port p : this.ports) if (p.index < v.length) v[p.index] = p.value;
      return v;
    }
  }

  /** The features lv2_render.c gives a plugin. */
  static final java.util.Set<String> HOST_FEATURES = new java.util.HashSet<String>(java.util.Arrays.asList(
      "http://lv2plug.in/ns/ext/urid#map", "http://lv2plug.in/ns/ext/urid#unmap", "http://lv2plug.in/ns/ext/log#log",
      "http://lv2plug.in/ns/ext/options#options", "http://lv2plug.in/ns/ext/buf-size#boundedBlockLength",
      "http://lv2plug.in/ns/ext/buf-size#powerOf2BlockLength", LV2 + "isLive", LV2 + "inPlaceBroken", LV2 + "hardRTCapable"));

  /** The plugins of a bundle folder: its manifest.ttl and the files it names (rdfs:seeAlso). */
  public static List<Plugin> read(File bundle) throws Exception {
    Turtle t = new Turtle();
    File manifest = new File(bundle, "manifest.ttl");
    if (!manifest.isFile()) throw new IllegalArgumentException(bundle.getName() + " has no manifest.ttl (not an LV2 bundle)");
    t.parse(new String(Files.readAllBytes(manifest.toPath()), StandardCharsets.UTF_8), manifest.toURI().toString());
    java.util.Set<String> read = new java.util.HashSet<String>();
    read.add(manifest.toURI().toString());
    for (String[] tr : new ArrayList<String[]>(t.triples)) {
      if (!tr[1].equals(RDFS_SEE_ALSO)) continue;
      String uri = tr[2];
      if (read.contains(uri) || !uri.startsWith("file:")) continue;
      read.add(uri);
      File f = new File(new java.net.URI(uri));
      if (f.isFile()) t.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8), f.toURI().toString());
    }
    List<Plugin> out = new ArrayList<Plugin>();
    for (String s : t.subjects(RDF + "type", LV2 + "Plugin")) {
      Plugin p = new Plugin();
      p.uri = s;
      p.bundle = bundle;
      p.name = t.one(s, DOAP_NAME, s.substring(s.lastIndexOf('/') + 1));
      String bin = t.one(s, LV2 + "binary", null);
      if (bin != null && bin.startsWith("file:")) p.binary = new File(new java.net.URI(bin));
      p.required.addAll(t.all(s, LV2 + "requiredFeature"));
      List<String> types = t.all(s, RDF + "type");
      p.instrument = types.contains(LV2 + "InstrumentPlugin");
      for (String node : t.all(s, LV2 + "port")) {
        Port port = new Port();
        List<String> pt = t.all(node, RDF + "type");
        port.output = pt.contains(LV2 + "OutputPort");
        port.kind = pt.contains(LV2 + "AudioPort") ? AUDIO : pt.contains(LV2 + "ControlPort") ? CONTROL
            : pt.contains(ATOM_NS + "AtomPort") || pt.contains(EVENT_PORT) ? ATOM : pt.contains(LV2 + "CVPort") ? CV : OTHER;
        port.index = (int) number(t.one(node, LV2 + "index", "-1"), -1);
        port.symbol = t.one(node, LV2 + "symbol", "");
        port.name = t.one(node, LV2 + "name", port.symbol);
        port.min = (float) number(t.one(node, LV2 + "minimum", null), Float.NaN);
        port.max = (float) number(t.one(node, LV2 + "maximum", null), Float.NaN);
        double def = number(t.one(node, LV2 + "default", null), Double.NaN);
        port.value = (float) (!Double.isNaN(def) ? def : !Float.isNaN(port.min) ? port.min : 0);
        port.midi = t.all(node, ATOM_NS + "supports").contains(MIDI_EVENT) || pt.contains(EVENT_PORT);
        port.optional = t.all(node, LV2 + "portProperty").contains(LV2 + "connectionOptional");
        p.ports.add(port);
      }
      p.ports.sort((a, b) -> Integer.compare(a.index, b.index));
      out.add(p);
    }
    out.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
    return out;
  }

  static double number(String s, double otherwise) {
    if (s == null) return otherwise;
    try {
      return Double.parseDouble(s.trim());
    } catch (NumberFormatException ex) {
      return otherwise;
    }
  }

  /**
   * Turtle (the W3C RDF text format LV2 uses) read into triples: prefixes and base, IRIs (relative
   * ones against the file), prefixed names, "a", literals (quoted, long-quoted, numbers, booleans;
   * language tags and datatypes dropped), blank nodes ([...] and _:x), collections ((...) as rdf
   * lists), ";" and "," lists, comments. Literal values are kept as their text.
   */
  static final class Turtle {
    final List<String[]> triples = new ArrayList<String[]>();
    private final Map<String, List<String[]>> bySubject = new HashMap<String, List<String[]>>();
    private int blank;

    private String text;
    private int at;
    private String base;
    private final Map<String, String> prefixes = new LinkedHashMap<String, String>();

    void parse(String text, String fileUri) {
      this.text = text;
      this.at = 0;
      this.base = fileUri;
      this.prefixes.clear();
      while (true) {
        this.skip();
        if (this.at >= this.text.length()) break;
        if (this.text.startsWith("@prefix", this.at) || this.startsWord("PREFIX")) {
          boolean at = this.text.charAt(this.at) == '@';
          this.at += 7;
          this.skip();
          int colon = this.text.indexOf(':', this.at);
          String name = this.text.substring(this.at, colon).trim();
          this.at = colon + 1;
          this.skip();
          this.prefixes.put(name, this.iri());
          if (at) this.expect('.');
        } else if (this.text.startsWith("@base", this.at) || this.startsWord("BASE")) {
          boolean at = this.text.charAt(this.at) == '@';
          this.at += at ? 5 : 4;
          this.skip();
          this.base = this.iri();
          if (at) this.expect('.');
        } else {
          String subject = this.subject();
          this.skip();
          if (this.peek() != '.') this.predicates(subject);
          this.expect('.');
        }
      }
    }

    private boolean startsWord(String w) {
      return this.text.regionMatches(true, this.at, w, 0, w.length()) && this.at + w.length() < this.text.length()
          && Character.isWhitespace(this.text.charAt(this.at + w.length()));
    }

    private char peek() {
      return this.at < this.text.length() ? this.text.charAt(this.at) : 0;
    }

    private void expect(char c) {
      this.skip();
      if (this.peek() != c) throw new IllegalArgumentException("Turtle: expected '" + c + "' at " + this.where());
      this.at++;
    }

    private String where() {
      int line = 1;
      for (int i = 0; i < this.at && i < this.text.length(); i++) if (this.text.charAt(i) == '\n') line++;
      return "line " + line;
    }

    private void skip() {
      while (this.at < this.text.length()) {
        char c = this.text.charAt(this.at);
        if (Character.isWhitespace(c)) this.at++;
        else if (c == '#') {
          while (this.at < this.text.length() && this.text.charAt(this.at) != '\n') this.at++;
        } else break;
      }
    }

    private String subject() {
      this.skip();
      char c = this.peek();
      if (c == '[') {
        this.at++;
        String node = "_:b" + (++this.blank);
        this.skip();
        if (this.peek() != ']') this.predicates(node);
        this.expect(']');
        return node;
      }
      if (c == '(') return this.collection();
      return this.resource();
    }

    private void predicates(String subject) {
      while (true) {
        this.skip();
        String predicate = this.peek() == 'a' && this.at + 1 < this.text.length() && Character.isWhitespace(this.text.charAt(this.at + 1))
            ? this.a() : this.resource();
        while (true) {
          String object = this.object();
          this.add(subject, predicate, object);
          this.skip();
          if (this.peek() == ',') {
            this.at++;
            continue;
          }
          break;
        }
        this.skip();
        if (this.peek() == ';') {
          while (this.peek() == ';') {
            this.at++;
            this.skip();
          }
          char c = this.peek();
          if (c == '.' || c == ']' || c == 0) return;
          continue;
        }
        return;
      }
    }

    private String a() {
      this.at++;
      return RDF + "type";
    }

    private String object() {
      this.skip();
      char c = this.peek();
      if (c == '[' || c == '(') return this.subject();
      if (c == '"' || c == '\'') return this.literal();
      if (c == '+' || c == '-' || c == '.' || Character.isDigit(c)) return this.number();
      if (this.text.startsWith("true", this.at) && !this.nameChar(this.at + 4)) {
        this.at += 4;
        return "true";
      }
      if (this.text.startsWith("false", this.at) && !this.nameChar(this.at + 5)) {
        this.at += 5;
        return "false";
      }
      return this.resource();
    }

    private boolean nameChar(int i) {
      if (i >= this.text.length()) return false;
      char c = this.text.charAt(i);
      return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == ':';
    }

    private String collection() {
      this.expect('(');
      List<String> items = new ArrayList<String>();
      while (true) {
        this.skip();
        if (this.peek() == ')') {
          this.at++;
          break;
        }
        items.add(this.object());
      }
      if (items.isEmpty()) return RDF + "nil";
      String head = "_:b" + (++this.blank), node = head;
      for (int i = 0; i < items.size(); i++) {
        this.add(node, RDF + "first", items.get(i));
        String next = i + 1 < items.size() ? "_:b" + (++this.blank) : RDF + "nil";
        this.add(node, RDF + "rest", next);
        node = next;
      }
      return head;
    }

    private String number() {
      int start = this.at;
      while (this.at < this.text.length()) {
        char c = this.text.charAt(this.at);
        // A '.' ends the statement unless digits follow.
        if (c == '.' && !(this.at + 1 < this.text.length() && Character.isDigit(this.text.charAt(this.at + 1)))) break;
        if (Character.isDigit(c) || c == '+' || c == '-' || c == '.' || c == 'e' || c == 'E') this.at++;
        else break;
      }
      return this.text.substring(start, this.at);
    }

    private String literal() {
      char q = this.peek();
      boolean longQuote = this.text.startsWith("" + q + q + q, this.at);
      this.at += longQuote ? 3 : 1;
      StringBuilder sb = new StringBuilder();
      while (this.at < this.text.length()) {
        char c = this.text.charAt(this.at);
        if (longQuote ? this.text.startsWith("" + q + q + q, this.at) : c == q) {
          this.at += longQuote ? 3 : 1;
          break;
        }
        if (c == '\\' && this.at + 1 < this.text.length()) {
          char e = this.text.charAt(this.at + 1);
          this.at += 2;
          if (e == 'n') sb.append('\n');
          else if (e == 't') sb.append('\t');
          else if (e == 'r') sb.append('\r');
          else if (e == 'u' || e == 'U') {
            int n = e == 'u' ? 4 : 8;
            sb.appendCodePoint(Integer.parseInt(this.text.substring(this.at, this.at + n), 16));
            this.at += n;
          } else sb.append(e);
          continue;
        }
        sb.append(c);
        this.at++;
      }
      // A language tag or a datatype: dropped (the text is what is used).
      if (this.peek() == '@') {
        this.at++;
        while (this.nameChar(this.at)) this.at++;
      } else if (this.text.startsWith("^^", this.at)) {
        this.at += 2;
        this.resource();
      }
      return sb.toString();
    }

    private String resource() {
      this.skip();
      char c = this.peek();
      if (c == '<') return this.iri();
      if (this.text.startsWith("_:", this.at)) {
        int start = this.at;
        this.at += 2;
        while (this.nameChar(this.at) && this.text.charAt(this.at) != ':') this.at++;
        return this.text.substring(start, this.at) + "@" + this.base;
      }
      int start = this.at;
      while (this.at < this.text.length()) {
        char ch = this.text.charAt(this.at);
        if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '-' || ch == ':' || ch == '%'
            || (ch == '.' && this.at + 1 < this.text.length() && this.nameChar(this.at + 1))) this.at++;
        else break;
      }
      String name = this.text.substring(start, this.at);
      int colon = name.indexOf(':');
      if (colon < 0) throw new IllegalArgumentException("Turtle: not a name at " + this.where() + ": " + name);
      String ns = this.prefixes.get(name.substring(0, colon));
      if (ns == null) throw new IllegalArgumentException("Turtle: unknown prefix " + name.substring(0, colon) + " at " + this.where());
      return ns + name.substring(colon + 1);
    }

    private String iri() {
      this.skip();
      this.expect('<');
      int end = this.text.indexOf('>', this.at);
      String iri = this.text.substring(this.at, end);
      this.at = end + 1;
      // Absolute (a scheme first): as it is; else against the file's address.
      if (iri.matches("[A-Za-z][A-Za-z0-9+.-]*:.*")) return iri;
      try {
        return new java.net.URI(this.base).resolve(new java.net.URI(null, null, iri, null)).toString();
      } catch (Exception ex) {
        return iri;
      }
    }

    private void add(String s, String p, String o) {
      String[] t = {s, p, o};
      this.triples.add(t);
      List<String[]> l = this.bySubject.get(s);
      if (l == null) this.bySubject.put(s, l = new ArrayList<String[]>());
      l.add(t);
    }

    List<String> all(String s, String p) {
      List<String> out = new ArrayList<String>();
      List<String[]> l = this.bySubject.get(s);
      if (l != null) for (String[] t : l) if (t[1].equals(p)) out.add(t[2]);
      return out;
    }

    String one(String s, String p, String otherwise) {
      List<String> a = this.all(s, p);
      return a.isEmpty() ? otherwise : a.get(0);
    }

    List<String> subjects(String p, String o) {
      List<String> out = new ArrayList<String>();
      for (String[] t : this.triples) if (t[1].equals(p) && t[2].equals(o) && !out.contains(t[0])) out.add(t[0]);
      return out;
    }
  }
}
