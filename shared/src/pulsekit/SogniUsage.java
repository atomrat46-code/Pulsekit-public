package pulsekit;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Sogni account's fair-use standing (GET /v1/subscriptions/usage, its data.fairUse: percentages, reset
 * times, fastRenderingPaused), read before a batch of a Sogni program starts when Params' Usage warning is
 * set: over it, the batch asks before going on. Sogni's docs name the part but not its fields, so every
 * percentage-like number in it is shown by its name.
 */
public final class SogniUsage {
  private SogniUsage() {}

  /** Params' Usage warning (the Sogni programs): PyJav takes it off; a batch asks first when a percentage is at it or over. */
  public static final String WARN_PARAM = "--pk-usage-warn";
  public static final String WARN_LABEL = "Usage warning at (%): before a batch (Run sections, an input folder, Auto render) Sogni's fair-use usage is read; at this or over, the batch asks first";
  public static final String WARN_HINT = "e.g. 80; empty: not read";

  /** The usage reply (the API key as the programs find it). */
  public static Object fetch() throws IOException {
    String key = SogniApi.findKey(null);
    if (key == null) throw new IOException("no Sogni API key (File > Drum Midi Settings)");
    return new SogniApi(null, key).request("GET", "/v1/subscriptions/usage", null);
  }

  /** The fair-use part's percentage-like numbers (keys with pct or percent in them), by their path in it. */
  @SuppressWarnings("unchecked")
  public static Map<String, Double> percents(Object payload) {
    Map<String, Double> out = new LinkedHashMap<String, Double>();
    Object fair = part(payload, "fairUse");
    collect(fair, "", out);
    return out;
  }

  /** Whether Sogni says fast rendering is paused (fairUse.fastRenderingPaused). */
  public static boolean paused(Object payload) {
    Object fair = part(payload, "fairUse");
    return fair instanceof Map && Boolean.TRUE.equals(((Map<?, ?>) fair).get("fastRenderingPaused"));
  }

  @SuppressWarnings("unchecked")
  static Object part(Object payload, String name) {
    if (!(payload instanceof Map)) return null;
    Map<String, Object> m = (Map<String, Object>) payload;
    if (m.get("data") instanceof Map) m = (Map<String, Object>) m.get("data");
    return m.get(name);
  }

  @SuppressWarnings("unchecked")
  static void collect(Object o, String path, Map<String, Double> out) {
    if (o instanceof Map) {
      for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) {
        String p = path.isEmpty() ? e.getKey() : path + "." + e.getKey();
        String k = e.getKey().toLowerCase(Locale.ROOT);
        if (e.getValue() instanceof Number && (k.contains("pct") || k.contains("percent"))) out.put(p, Double.valueOf(((Number) e.getValue()).doubleValue()));
        else collect(e.getValue(), p, out);
      }
    } else if (o instanceof List) {
      int i = 0;
      for (Object x : (List<Object>) o) collect(x, path + "[" + (i++) + "]", out);
    }
  }

  /** The highest of them, or -1. */
  public static double highest(Map<String, Double> pcts) {
    double max = -1;
    for (Double d : pcts.values()) if (d != null && d.doubleValue() > max) max = d.doubleValue();
    return max;
  }

  /** The log's line: "Sogni usage: dailyPct 34%, monthlyPct 12%" (or what Sogni gave). */
  public static String line(Object payload) {
    Map<String, Double> p = percents(payload);
    StringBuilder sb = new StringBuilder("Sogni usage: ");
    if (p.isEmpty()) sb.append(part(payload, "fairUse") == null ? "no fair-use figures (no Unlimited plan, or Sogni gave none)" : "no percentages in Sogni's reply");
    int i = 0;
    for (Map.Entry<String, Double> e : p.entrySet()) sb.append(i++ == 0 ? "" : ", ").append(e.getKey()).append(' ').append(number(e.getValue().doubleValue())).append('%');
    if (paused(payload)) sb.append("; fast rendering paused");
    return sb.toString();
  }

  /** The running line's short form: "Sogni 85.5%" (the highest percentage), "Sogni: no figures", "Sogni: paused". */
  public static String brief(Object payload) {
    double max = highest(percents(payload));
    String s = max < 0 ? "Sogni usage: no figures" : "Sogni usage " + number(max) + "%";
    return paused(payload) ? s + ", paused" : s;
  }

  static String number(double d) {
    return d == Math.rint(d) ? String.valueOf((long) d) : String.format(Locale.ROOT, "%.1f", d);
  }

  /** Params' Usage warning in saved Params ("--pk-usage-warn 80"), or 0 when not set. */
  public static int threshold(String saved) {
    if (saved == null) return 0;
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:^|\\s)" + java.util.regex.Pattern.quote(WARN_PARAM) + "\\s+\"?(\\d+)").matcher(saved);
    return m.find() ? Integer.parseInt(m.group(1)) : 0;
  }

  /** The batch's question when usage is at the warning or over (null when it is under, or not read). */
  public static String ask(Object payload, int threshold) {
    if (threshold <= 0) return null;
    double max = highest(percents(payload));
    if (max < threshold && !paused(payload)) return null;
    return line(payload) + "\n\nThat is at or over your warning (" + threshold + "%)" + (paused(payload) ? ", and fast rendering is paused" : "") + ". Run the batch anyway?";
  }
}
