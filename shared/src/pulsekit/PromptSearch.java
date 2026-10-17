package pulsekit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The prompt browser's search: prompts whose name, category (and the categories above it), tags,
 * description or text hold every word typed. "#night" looks in the tags only. Matches in the name
 * come first, then by name.
 */
public final class PromptSearch {
  private PromptSearch() {}

  /** What was searched for last (the browser opens with it). */
  public static String query = "";

  public static List<PromptVault.Prompt> find(PromptVault vault, String typed) {
    List<PromptVault.Prompt> out = new ArrayList<PromptVault.Prompt>();
    String q = typed == null ? "" : typed.trim().toLowerCase(Locale.ROOT);
    if (vault == null || q.length() == 0) return out;
    String[] words = q.split("\\s+");
    final List<PromptVault.Prompt> named = new ArrayList<PromptVault.Prompt>();
    for (PromptVault.Prompt p : vault.allPrompts()) {
      String title = p.title == null ? "" : p.title.toLowerCase(Locale.ROOT);
      String tags = ";" + (p.tags == null ? "" : p.tags.toLowerCase(Locale.ROOT)) + ";";
      String place = where(vault, p).toLowerCase(Locale.ROOT);
      PromptVault.Version v = vault.finalVersion(p.id);
      String text = v == null ? "" : ((v.description == null ? "" : v.description) + "\n" + (v.body == null ? "" : v.body)).toLowerCase(Locale.ROOT);
      boolean all = true;
      boolean inTitle = false;
      for (String w : words) {
        if (w.startsWith("#") && w.length() > 1) {
          String t = w.substring(1);
          boolean tagged = false;
          for (String one : tags.split(";")) tagged |= one.equals(t) || one.startsWith(t);
          all &= tagged;
        } else {
          boolean hit = title.contains(w) || tags.contains(w) || place.contains(w) || text.contains(w);
          inTitle |= title.contains(w);
          all &= hit;
        }
      }
      if (!all) continue;
      if (inTitle) named.add(p);
      else out.add(p);
    }
    java.util.Comparator<PromptVault.Prompt> byName = new java.util.Comparator<PromptVault.Prompt>() {
      @Override
      public int compare(PromptVault.Prompt a, PromptVault.Prompt b) {
        return String.valueOf(a.title).compareToIgnoreCase(String.valueOf(b.title));
      }
    };
    java.util.Collections.sort(named, byName);
    java.util.Collections.sort(out, byName);
    named.addAll(out);
    return named;
  }

  /** The prompt's place: "Video / Clips". */
  public static String where(PromptVault vault, PromptVault.Prompt p) {
    StringBuilder sb = new StringBuilder();
    PromptVault.Category c = vault.category(p.categoryId);
    for (int depth = 0; c != null && depth < 10; depth++) {
      sb.insert(0, sb.length() == 0 ? c.name : c.name + " / ");
      c = c.parentId == 0 ? null : vault.category(c.parentId);
    }
    return sb.toString();
  }

  /** "#beach #night", or "". */
  public static String tagLine(PromptVault vault, PromptVault.Prompt p) {
    StringBuilder sb = new StringBuilder();
    for (String t : vault.tags(p.id)) sb.append(sb.length() == 0 ? "#" : " #").append(t);
    return sb.toString();
  }

  /** The text a prompt hands on (its final version, without the description). */
  public static String words(PromptVault vault, PromptVault.Prompt p) {
    PromptVault.Version v = vault.finalVersion(p.id);
    return v == null ? "" : PromptRun.withoutDescription(v.description, v.body == null ? "" : v.body).trim();
  }
}
