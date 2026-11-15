package pulsekit;

import java.util.ArrayList;
import java.util.List;

/**
 * The Sogni programs' Params: small buttons after the Prompt and Loras rows' labels. Copy puts the
 * row's text on the clipboard; Paste puts the clipboard's text in the row (asked first when the row
 * has text); Send puts the row's text into another Sogni program's Params (picked from those that
 * have the row; always asked first), as if typed there and saved. Also Export: the Params' section
 * (the rows Save keeps) written into a file picked, not the prompt library.
 */
public final class ParamsClip {
  private ParamsClip() {}

  public static final String COPY = "⧉", PASTE = "⇩", SEND = "➦";
  public static final String COPY_TIP = "Copy the text", PASTE_TIP = "Paste the clipboard's text (asked first when there is text)",
      SEND_TIP = "Send the text into another Sogni program's Params (asked first)";
  public static final String EXPORT = "Export";
  public static final String SEND_TITLE = "Send to another Sogni program";
  /** The Sogni programs, in the Java menu's order. */
  public static final String[] SOGNI = {"SogniChat.java", "SogniMusic.java", "SogniPadd.java", "SogniPedit.java", "SogniTextVideo.java", "SogniVideo.java"};

  /** Whether a row has the buttons: a Sogni program's prompt or LoRAs. */
  public static boolean has(String program, ProgramParams.Param p) {
    return SogniSections.shown(program) && p != null && p.flag && p.takesValue && (p.token.equals("--prompt") || p.token.equals("--loras"));
  }

  /** The row named in the questions: "prompt" or "LoRAs". */
  public static String what(String token) {
    return "--loras".equals(token) ? "LoRAs" : "prompt";
  }

  public static String replaceAsk(String token) {
    return "The " + what(token) + " has text already. Replace it with the clipboard's text?";
  }

  public static String copied(String token, String text) {
    return text.trim().isEmpty() ? "No " + what(token) + " to copy" : "Copied the " + what(token) + " (" + text.trim().length() + " characters)";
  }

  /** Send's question: the program, and the start of the text. */
  public static String sendAsk(String token, String target, String text) {
    String t = text.trim().replaceAll("\\s+", " ");
    return "Put this " + what(token) + " into " + SogniSections.app(target) + "'s Params? It replaces the " + what(token) + " there.\n\n"
        + (t.length() > 160 ? t.substring(0, 157) + "…" : t);
  }

  public static String sent(String token, String target) {
    return "Sent the " + what(token) + " into " + SogniSections.app(target) + "'s Params";
  }

  /** The Sogni programs other than `program` whose Params have the row (their text from `textOf`, null when not there). */
  public static List<String> targets(String program, String token, java.util.function.Function<String, String> textOf) {
    List<String> out = new ArrayList<String>();
    for (String s : SOGNI) {
      if (s.equals(program) || SogniSections.app(s).equals(SogniSections.app(program))) continue;
      String text = textOf.apply(s);
      if (text == null) continue;
      for (ProgramParams.Param p : ProgramParams.parse(text)) {
        if (p.flag && p.token.equals(token)) {
          out.add(s);
          break;
        }
      }
    }
    return out;
  }

  /** A program's saved Params (`saved`, null when never saved) with the row set to `text`. */
  public static String put(List<ProgramParams.Param> ps, String saved, String token, String text) {
    String[] values = ProgramParams.values(ps, saved, "");
    for (int i = 0; i < ps.size(); i++) if (ps.get(i).flag && ps.get(i).token.equals(token)) values[i] = ProgramParams.oneLine(text);
    return ProgramParams.build(ps, values);
  }

  /** Export's file name: "SogniPedit section - <its prompt's first words>.txt". */
  public static String exportName(String program, java.util.Map<String, String> fields) {
    String prompt = fields.containsKey("--prompt") ? fields.get("--prompt") : "";
    String words = prompt.replaceAll("[^A-Za-z0-9 ]", " ").trim().replaceAll("\\s+", " ");
    String[] w = words.split(" ");
    StringBuilder lead = new StringBuilder();
    for (int i = 0; i < w.length && i < 5 && w[i].length() > 0; i++) lead.append(lead.length() == 0 ? "" : " ").append(w[i]);
    return SogniSections.app(program) + " section" + (lead.length() > 0 ? " - " + lead : "") + ".txt";
  }

  /** Export's question when the program has saved sections: this entry, or all of them. */
  public static final String EXPORT_ASK = "Export the entries in the Params now, or all the sections saved for this program?";
  public static final String EXPORT_THIS = "This entry";

  public static String exportAllLabel(String app, int n) {
    return "All " + app + " sections (" + n + ")";
  }

  /** Export all's file name: "SogniPedit sections.txt". */
  public static String exportAllName(String app) {
    return app + " sections.txt";
  }

  /**
   * Export all's text: "App: SogniPedit", then each section (the newest first), "----" between them: the
   * definitions file format, so Params' Select file reads them back one by one.
   */
  public static String exportAllText(String app, List<PromptVault.SogniSection> sections) {
    StringBuilder sb = new StringBuilder("App: ").append(app).append('\n');
    for (int i = 0; i < sections.size(); i++) {
      if (i > 0) sb.append("----\n");
      String t = sections.get(i).text == null ? "" : sections.get(i).text.trim();
      sb.append(t).append('\n');
    }
    return sb.toString();
  }

  /** Export's text: the section's rows ("Prompt: ...", "Loras: ..."), the definitions file format. */
  public static String exportText(java.util.Map<String, String> fields) {
    return SogniSections.text(fields);
  }
}
