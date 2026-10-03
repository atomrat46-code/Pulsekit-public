package pulsekit;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Extra arguments a PyJav program looks like it accepts. */
public final class PyJavHints {
  private PyJavHints() {}

  /** File name for saving a program's output, such as "CompareHits_test_results.txt". */
  public static String resultsFileName(String program) {
    String n = program == null ? "" : program.trim();
    int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
    if (slash >= 0) n = n.substring(slash + 1);
    int dot = n.lastIndexOf('.');
    if (dot > 0) n = n.substring(0, dot);
    n = n.replaceAll("[^A-Za-z0-9._-]+", "_");
    if (n.length() == 0) n = "PyJav";
    return n + "_test_results.txt";
  }

  public static String status(String name, String source, byte[] bytes) {
    String found = suggest(name, source, bytes);
    String label = name == null || name.length() == 0 ? "program" : name;
    if (found.length() == 0) {
      return "Loaded " + label + ". Run uses this file.\nNo extra args found in this file.";
    }
    if (found.startsWith("Usage:")) {
      return "Loaded " + label + ". Run uses this file.\n" + found;
    }
    return "Loaded " + label + ". Run uses this file.\nExtra args: " + found;
  }

  public static String suggest(String name, String source, byte[] bytes) {
    StringBuilder blob = new StringBuilder();
    if (source != null && source.length() > 0) blob.append(source).append('\n');
    if (bytes != null && bytes.length > 0) appendBytes(blob, bytes, name);
    return format(blob.toString());
  }

  /** The program's text: its source, or the readable strings in a .class or .jar. */
  public static String programText(String name, String source, byte[] bytes) {
    StringBuilder blob = new StringBuilder();
    if (source != null && source.length() > 0) blob.append(source).append('\n');
    if (bytes != null && bytes.length > 0) appendBytes(blob, bytes, name);
    return blob.toString();
  }

  private static void appendBytes(StringBuilder blob, byte[] bytes, String name) {
    String low = name == null ? "" : name.toLowerCase();
    boolean jar = low.endsWith(".jar") || (bytes.length > 3 && bytes[0] == 'P' && bytes[1] == 'K');
    if (!jar) {
      pullAscii(blob, bytes);
      return;
    }
    int budget = 0;
    try {
      ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(bytes));
      try {
        ZipEntry entry;
        while ((entry = zin.getNextEntry()) != null && budget < 900000) {
          String entryName = entry.getName();
          if (entryName.endsWith(".class") || entryName.endsWith(".java") || entryName.endsWith(".py")) {
            byte[] chunk = readCap(zin, 250000);
            budget += chunk.length;
            pullAscii(blob, chunk);
          }
          zin.closeEntry();
        }
      } finally {
        zin.close();
      }
    } catch (Exception ignored) {
      pullAscii(blob, bytes);
    }
  }

  private static byte[] readCap(ZipInputStream in, int cap) throws Exception {
    byte[] buf = new byte[4096];
    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
    int n;
    int left = cap;
    while (left > 0 && (n = in.read(buf, 0, Math.min(buf.length, left))) > 0) {
      bos.write(buf, 0, n);
      left -= n;
    }
    return bos.toByteArray();
  }

  private static void pullAscii(StringBuilder out, byte[] data) {
    if (data == null) return;
    int start = -1;
    for (int i = 0; i <= data.length; i++) {
      int b = i < data.length ? data[i] & 255 : 0;
      boolean ok = b >= 32 && b < 127;
      if (ok) {
        if (start < 0) start = i;
      } else if (start >= 0) {
        int len = i - start;
        if (len >= 4 && len <= 180) {
          out.append(new String(data, start, len, StandardCharsets.US_ASCII)).append('\n');
        }
        start = -1;
      }
    }
  }

  static String format(String blob) {
    LinkedHashSet<String> flags = new LinkedHashSet<String>();
    for (int i = 0; i + 3 < blob.length() && flags.size() < 18; i++) {
      if (blob.charAt(i) != '-' || blob.charAt(i + 1) != '-' || !isLower(blob.charAt(i + 2))) continue;
      int j = i + 2;
      while (j < blob.length() && isName(blob.charAt(j))) j++;
      String flag = blob.substring(i, j);
      if (flag.length() < 3 || flag.length() > 32 || flag.substring(2).indexOf("--") >= 0) {
        i = j;
        continue;
      }
      flags.add(flag);
      i = j;
    }
    int u = blob.indexOf("Usage:");
    String usageLine = u >= 0 ? ProgramParams.usageLine(blob, u + 6) : null;
    StringBuilder sb = new StringBuilder();
    for (String flag : flags) {
      // With a Usage line, only its switches: a program's messages may name other programs' switches.
      if (usageLine != null && !java.util.regex.Pattern.compile("(^|[\\s\\[|])" + java.util.regex.Pattern.quote(flag) + "([\\s\\]|=]|$)").matcher(usageLine).find()) continue;
      if (sb.length() > 0) sb.append(' ');
      sb.append(flag);
    }
    if (u >= 0) {
      // Java strings split over lines are joined, and the source's closing quote is left out.
      String usage = "Usage: " + usageLine;
      if (usage.length() > 220) usage = usage.substring(0, 220);
      if (usage.length() > 7) {
        if (sb.length() > 0) sb.append('\n');
        sb.append(usage);
      }
    }
    return sb.toString();
  }

  private static boolean isLower(char c) {
    return c >= 'a' && c <= 'z';
  }

  private static boolean isName(char c) {
    return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_';
  }

  /** First <input.wav>-style placeholder, or null. Output names are skipped. */
  public static String firstInput(String hint) {
    if (hint == null) return null;
    int i = 0;
    while (i < hint.length()) {
      int a = hint.indexOf('<', i);
      if (a < 0) return null;
      int b = hint.indexOf('>', a + 1);
      if (b < 0) return null;
      String name = hint.substring(a + 1, b).trim();
      if (isInputName(name)) return hint.substring(a, b + 1);
      i = b + 1;
    }
    return null;
  }

  /** Put the chosen file where the input placeholder was. */
  public static String withInput(String extra, String hint, String token, String path) {
    String quoted = path != null && path.indexOf(' ') >= 0 ? "\"" + path + "\"" : path;
    String base = extra == null ? "" : extra.trim();
    if (base.length() == 0) base = placeholders(hint);
    if (token != null && token.length() > 0 && base.indexOf(token) >= 0) {
      return base.replace(token, quoted == null ? "" : quoted);
    }
    if (quoted == null || quoted.length() == 0) return base;
    return base.length() == 0 ? quoted : quoted + " " + base;
  }

  private static String placeholders(String hint) {
    StringBuilder sb = new StringBuilder();
    if (hint == null) return "";
    int i = 0;
    while (i < hint.length()) {
      int a = hint.indexOf('<', i);
      if (a < 0) break;
      int b = hint.indexOf('>', a + 1);
      if (b < 0) break;
      String name = hint.substring(a + 1, b).trim();
      if (name.indexOf(' ') < 0 && name.indexOf('.') >= 0 && name.indexOf('<') < 0) {
        String token = hint.substring(a, b + 1);
        if (sb.indexOf(token) < 0) {
          if (sb.length() > 0) sb.append(' ');
          sb.append(token);
        }
      }
      i = b + 1;
    }
    return sb.toString();
  }

  /** Extra-args line with the input and a real output path filled in. */
  public static String fillArgs(String extra, String hint, String inputToken, String inputPath, String outDir) {
    String line = extra == null ? "" : extra.trim();
    if (line.length() == 0) line = placeholders(hint);
    line = line.replaceAll("(?i)--\\s+output\\b", "--output");
    String blob = (hint == null ? "" : hint) + "\n" + line;
    String out = outputFile(inputPath, blob, outDir);
    if (inputPath != null && inputPath.length() > 0) {
      String quoted = quote(inputPath);
      if (inputToken != null && inputToken.length() > 0 && line.indexOf(inputToken) >= 0) {
        line = line.replace(inputToken, quoted);
      }
      StringBuilder swapped = new StringBuilder();
      int i = 0;
      while (i < line.length()) {
        int a = line.indexOf('<', i);
        if (a < 0) {
          swapped.append(line.substring(i));
          break;
        }
        swapped.append(line.substring(i, a));
        int b = line.indexOf('>', a + 1);
        if (b < 0) {
          swapped.append(line.substring(a));
          break;
        }
        String name = line.substring(a + 1, b);
        if (isInputName(name)) swapped.append(quoted);
        else swapped.append(line.substring(a, b + 1));
        i = b + 1;
      }
      line = swapped.toString();
      if (line.indexOf(inputPath) < 0) line = line.length() == 0 ? quoted : quoted + " " + line;
    }
    if (out.length() > 0) line = putOutput(line, out);
    return joinArgs(dedupeArgs(JavaRun.split(line), hint));
  }

  /** Path passed for --output, -output, -- output, or <output.mid>. Empty if the program has none. */
  public static String outputFile(String inputPath, String blob, String outDir) {
    if (!wantsOutput(blob)) return "";
    String ext = outputExt(blob);
    if (ext == null) ext = ".mid";
    String base = "output";
    String place = firstOutputName(blob);
    if (place != null) {
      int dot = place.lastIndexOf('.');
      if (dot > 0) base = place.substring(0, dot);
    }
    String dir = outDir == null ? "" : outDir;
    if (inputPath != null && inputPath.length() > 0) {
      int slash = Math.max(inputPath.lastIndexOf('/'), inputPath.lastIndexOf('\\'));
      String name = slash >= 0 ? inputPath.substring(slash + 1) : inputPath;
      int dot = name.lastIndexOf('.');
      if (dot > 0) base = name.substring(0, dot);
      if (slash > 0) dir = inputPath.substring(0, slash);
    }
    String file = safeName(base) + ext;
    if (dir.length() == 0) return file;
    if (dir.endsWith("/") || dir.endsWith("\\")) return dir + file;
    return dir + "/" + file;
  }

  public static String outputNotice(String extra, String hint, String inputPath, String outDir) {
    String filled = fillArgs(extra, hint, firstInput(hint), inputPath, outDir);
    String out = outputFile(inputPath, (hint == null ? "" : hint) + "\n" + (extra == null ? "" : extra), outDir);
    StringBuilder sb = new StringBuilder();
    if (inputPath != null && inputPath.length() > 0) sb.append("Selected input: ").append(inputPath).append('\n');
    if (out.length() > 0) sb.append("Output file: ").append(out).append('\n');
    sb.append("Extra args: ").append(filled);
    return sb.toString();
  }

  public static String outputPath(String inputPath, String hint) {
    return outputFile(inputPath, hint, "");
  }

  public static String pairArgs(String inputPath, String outputPath) {
    String dir = parentOf(outputPath != null && outputPath.length() > 0 ? outputPath : inputPath);
    return fillArgs("", "", null, inputPath, dir);
  }

  public static String selectionText(String inputPath, String outputPath) {
    String dir = parentOf(outputPath != null && outputPath.length() > 0 ? outputPath : inputPath);
    return outputNotice("", "", inputPath, dir);
  }

  private static String parentOf(String path) {
    if (path == null) return "";
    int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
    return slash > 0 ? path.substring(0, slash) : "";
  }

  private static String quote(String path) {
    if (path == null) return "";
    return path.indexOf(' ') >= 0 ? "\"" + path + "\"" : path;
  }

  private static boolean wantsOutput(String text) {
    if (text == null || text.length() == 0) return false;
    String low = text.toLowerCase();
    if (low.indexOf("<output") >= 0) return true;
    if (low.indexOf("-- output") >= 0 || low.indexOf("--output") >= 0 || low.indexOf("-output") >= 0) return true;
    if (low.indexOf("--out") >= 0 || low.indexOf("-out") >= 0) return true;
    return outputExt(text) != null;
  }

  private static String outputExt(String hint) {
    if (hint == null) return null;
    String any = null;
    java.util.Set<String> notOutput = new java.util.HashSet<String>();
    java.util.List<String> lastNames = new java.util.ArrayList<String>();
    int i = 0;
    while (i < hint.length()) {
      int a = hint.indexOf('<', i);
      if (a < 0) break;
      int b = hint.indexOf('>', a + 1);
      if (b < 0) break;
      String name = hint.substring(a + 1, b).trim();
      int dot = name.lastIndexOf('.');
      if (dot > 0 && !isInputName(name)) {
        if (name.toLowerCase().indexOf("output") >= 0) return name.substring(dot);
        // Without an "output" name, only the last file in the usage is taken as the output,
        // and not when an optional [file.ext] follows it (CompareHits <drums.mid> [song.mid]).
        int lineEnd = hint.indexOf('\n', b);
        String rest = hint.substring(b + 1, lineEnd < 0 ? hint.length() : lineEnd);
        if (rest.matches("(?s).*[<\\[][^>\\]\\s-][^>\\]]*\\.[A-Za-z0-9]+\\s*[>\\]].*")) notOutput.add(name);
        else lastNames.add(name);
      }
      i = b + 1;
    }
    for (String name : lastNames) {
      if (!notOutput.contains(name) && any == null) any = name.substring(name.lastIndexOf('.'));
    }
    return any;
  }

  private static String firstOutputName(String text) {
    if (text == null) return null;
    int i = 0;
    while (i < text.length()) {
      int a = text.indexOf('<', i);
      if (a < 0) return null;
      int b = text.indexOf('>', a + 1);
      if (b < 0) return null;
      String name = text.substring(a + 1, b).trim();
      if (name.toLowerCase().indexOf("output") >= 0) return name;
      i = b + 1;
    }
    return null;
  }

  private static String putOutput(String line, String outPath) {
    String replaced = replaceOutputTokens(line, outPath);
    java.util.List<String> toks = JavaRun.split(replaced);
    java.util.List<String> out = new java.util.ArrayList<String>();
    boolean saw = false;
    for (int i = 0; i < toks.size(); i++) {
      String t = toks.get(i);
      if (isOutputFlag(t)) {
        String low = t.toLowerCase();
        String flag = low.startsWith("--")
            ? (low.indexOf("output") >= 0 ? "--output" : "--out")
            : (low.indexOf("output") >= 0 ? "-output" : "-out");
        out.add(flag);
        String next = i + 1 < toks.size() ? toks.get(i + 1) : null;
        if (next != null && isAbsolutePath(next)) {
          out.add(next);
          i++;
        } else {
          out.add(outPath);
          if (next != null && !isInputish(next)) i++;
        }
        saw = true;
        continue;
      }
      out.add(t);
      if (outPath.equals(t) || sameFile(t, outPath)) saw = true;
      else if (isStaleOutput(t)) {
        out.set(out.size() - 1, outPath);
        saw = true;
      }
    }
    if (!saw) {
      int dot = outPath.lastIndexOf('.');
      String ext = dot >= 0 ? outPath.substring(dot).toLowerCase() : "";
      boolean already = ext.length() > 1 && hasExt(out, ext);
      if (!already) out.add(outPath);
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < out.size(); i++) {
      if (i > 0) sb.append(' ');
      sb.append(quote(out.get(i)));
    }
    return sb.toString();
  }

  private static boolean isStaleOutput(String token) {
    if (token == null) return false;
    int slash = Math.max(token.lastIndexOf('/'), token.lastIndexOf('\\'));
    String name = (slash >= 0 ? token.substring(slash + 1) : token).toLowerCase();
    return name.equals("output.mid") || name.equals("output.midi") || name.equals("output");
  }

  private static boolean sameFile(String a, String b) {
    if (a == null || b == null) return false;
    int as = Math.max(a.lastIndexOf('/'), a.lastIndexOf('\\'));
    int bs = Math.max(b.lastIndexOf('/'), b.lastIndexOf('\\'));
    String an = as >= 0 ? a.substring(as + 1) : a;
    String bn = bs >= 0 ? b.substring(bs + 1) : b;
    return an.equals(bn);
  }

  private static boolean hasExt(java.util.List<String> args, String ext) {
    for (int i = 0; i < args.size(); i++) {
      String t = args.get(i);
      if (t.startsWith("-")) continue;
      if (t.toLowerCase().endsWith(ext)) return true;
    }
    return false;
  }

  private static String replaceOutputTokens(String line, String outPath) {
    StringBuilder sb = new StringBuilder();
    int i = 0;
    while (i < line.length()) {
      int a = line.indexOf('<', i);
      if (a < 0) {
        sb.append(line.substring(i));
        break;
      }
      sb.append(line.substring(i, a));
      int b = line.indexOf('>', a + 1);
      if (b < 0) {
        sb.append(line.substring(a));
        break;
      }
      String name = line.substring(a + 1, b).trim();
      if (!isInputName(name) && (name.toLowerCase().indexOf("output") >= 0 || name.indexOf('.') > 0)) {
        sb.append(quote(outPath));
      } else {
        sb.append(line.substring(a, b + 1));
      }
      i = b + 1;
    }
    return sb.toString();
  }

  private static boolean isOutputFlag(String token) {
    if (token == null) return false;
    String low = token.toLowerCase();
    return "--output".equals(low) || "--out".equals(low) || "-output".equals(low) || "-out".equals(low);
  }

  private static boolean isAbsolutePath(String token) {
    if (token == null || token.length() == 0) return false;
    return token.charAt(0) == '/' || (token.length() > 2 && token.charAt(1) == ':');
  }

  private static boolean isInputish(String token) {
    if (token == null) return false;
    String low = token.toLowerCase();
    if (low.indexOf("input") >= 0) return true;
    return low.endsWith(".wav") || low.endsWith(".mp3") || low.endsWith(".aiff") || low.endsWith(".flac");
  }

  private static String safeName(String base) {
    if (base == null || base.length() == 0) return "output";
    StringBuilder sb = new StringBuilder();
    boolean space = false;
    for (int i = 0; i < base.length(); i++) {
      char c = base.charAt(i);
      if (c == '\\' || c == '/' || c == ':' || c == '"' || c == '?' || c == '*' || c == '<' || c == '>' || c == '|' || c == '\u0000') c = ' ';
      if (c == ' ') {
        if (sb.length() > 0 && !space) {
          sb.append(' ');
          space = true;
        }
        continue;
      }
      space = false;
      sb.append(c);
    }
    while (sb.length() > 0 && sb.charAt(sb.length() - 1) == ' ') sb.setLength(sb.length() - 1);
    return sb.length() == 0 ? "output" : sb.toString();
  }

  /** Arguments passed to the program. Output flags and <output.mid> become a real path. */
  public static java.util.List<String> programArgs(String extra, String token, String path) {
    return programArgs(extra, token, path, "", "");
  }

  public static java.util.List<String> programArgs(String extra, String token, String path, String hint, String outDir) {
    String line = fillArgs(extra, hint, token, path, outDir);
    StringBuilder plain = new StringBuilder();
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c != '<' && c != '>') plain.append(c);
    }
    return dedupeArgs(JavaRun.split(plain.toString()), hint);
  }

  /**
   * As many audio paths as the program's usage names (one for DrumMidi; CutWav takes an input and
   * an output .wav), so an old input left in the arguments is dropped. The same path twice is
   * always dropped.
   */
  private static java.util.List<String> dedupeArgs(java.util.List<String> raw, String hint) {
    int maxAudio = 0;
    for (ProgramParams.Param p : ProgramParams.parse(hint)) {
      if (!p.flag && p.ext != null && !"mid".equals(p.ext)) maxAudio++;
    }
    if (maxAudio < 1) maxAudio = 1;
    java.util.List<String> out = new java.util.ArrayList<String>();
    int audio = 0;
    boolean mid = false;
    for (int i = 0; i < raw.size(); i++) {
      String t = raw.get(i);
      String low = t.toLowerCase();
      if (low.endsWith(".wav") || low.endsWith(".mp3") || low.endsWith(".aiff") || low.endsWith(".flac")) {
        if (out.contains(t) || audio >= maxAudio) continue;
        audio++;
      } else if (low.endsWith(".mid") || low.endsWith(".midi")) {
        // A program may take several MIDI files (CompareHits); only the same one twice is dropped.
        if (mid && out.contains(t)) continue;
        mid = true;
      }
      out.add(t);
    }
    return out;
  }

  private static String joinArgs(java.util.List<String> args) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < args.size(); i++) {
      if (i > 0) sb.append(' ');
      sb.append(quote(args.get(i)));
    }
    return sb.toString();
  }

  /** Empty when the chosen file matches the placeholder. Otherwise a warning. */
  public static String inputWarning(String token, String filename) {
    return inputWarning(token, filename, null);
  }

  public static String inputWarning(String token, String filename, byte[] head) {
    return "";
  }

  /** Extensions in <input.wav> or <input.wav|mp3>. A bar means either format is acceptable. */
  static java.util.List<String> acceptedExts(String token) {
    java.util.List<String> out = new java.util.ArrayList<String>();
    if (token == null) return out;
    String text = token.trim();
    java.util.List<String> held = new java.util.ArrayList<String>();
    int i = 0;
    while (i < text.length()) {
      int a = text.indexOf('<', i);
      if (a < 0) break;
      int b = text.indexOf('>', a + 1);
      if (b < 0) break;
      String name = text.substring(a + 1, b).trim();
      if (isInputName(name)) held.add(name);
      i = b + 1;
    }
    java.util.List<String> parts = held.isEmpty() ? java.util.Collections.singletonList(stripToken(text)) : held;
    for (int p = 0; p < parts.size(); p++) {
      String[] alts = parts.get(p).split("[|¦∣｜]", -1);
      for (int n = 0; n < alts.length; n++) {
        String piece = alts[n].trim();
        if (piece.length() == 0) continue;
        String ext = extOf(piece);
        if (ext.length() == 0) ext = bareExt(piece);
        if (ext.length() == 0) continue;
        String[] bits = ext.split("[|¦∣｜]", -1);
        for (int k = 0; k < bits.length; k++) {
          String fam = family(bits[k]);
          if (fam.length() > 0 && !out.contains(fam)) out.add(fam);
        }
      }
    }
    return out;
  }

  private static String stripToken(String token) {
    String t = token == null ? "" : token.trim();
    if (t.startsWith("<") && t.endsWith(">") && t.length() > 2) t = t.substring(1, t.length() - 1);
    return t;
  }

  private static String bareExt(String piece) {
    String p = piece.startsWith(".") ? piece.substring(1) : piece;
    if (piece.indexOf('.') >= 0 && !piece.startsWith(".")) return "";
    if (p.length() < 2 || p.length() > 5) return "";
    for (int i = 0; i < p.length(); i++) {
      char c = p.charAt(i);
      boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
      if (!ok) return "";
    }
    return p.toLowerCase();
  }

  private static boolean matchesAny(java.util.List<String> wants, String got) {
    String fam = family(got);
    for (int i = 0; i < wants.size(); i++) {
      if (family(wants.get(i)).equals(fam)) return true;
    }
    return false;
  }

  static String joinExts(java.util.List<String> exts) {
    if (exts.isEmpty()) return "";
    if (exts.size() == 1) return exts.get(0);
    if (exts.size() == 2) return exts.get(0) + " or " + exts.get(1);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < exts.size(); i++) {
      if (i > 0) sb.append(i == exts.size() - 1 ? ", or " : ", ");
      sb.append(exts.get(i));
    }
    return sb.toString();
  }

  private static String extOf(String name) {
    if (name == null) return "";
    String t = name.trim();
    if (t.startsWith("<") && t.endsWith(">") && t.length() > 2) t = t.substring(1, t.length() - 1);
    int slash = Math.max(t.lastIndexOf('/'), t.lastIndexOf('\\'));
    int dot = t.lastIndexOf('.');
    if (dot < 0 || dot < slash || dot == t.length() - 1) return "";
    return t.substring(dot + 1).toLowerCase();
  }

  private static String family(String ext) {
    if (ext == null) return "";
    String e = ext.toLowerCase();
    if ("wave".equals(e)) return "wav";
    if ("aif".equals(e)) return "aiff";
    if ("midi".equals(e)) return "mid";
    if ("jpeg".equals(e)) return "jpg";
    return e;
  }

  private static String sniff(byte[] head) {
    if (head == null || head.length < 4) return "";
    if (head.length >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
        && head[8] == 'W' && head[9] == 'A' && head[10] == 'V' && head[11] == 'E') return "wav";
    if (head.length >= 12 && head[0] == 'F' && head[1] == 'O' && head[2] == 'R' && head[3] == 'M') return "aiff";
    if (head[0] == 'M' && head[1] == 'T' && head[2] == 'h' && head[3] == 'd') return "mid";
    if (head[0] == 'f' && head[1] == 'L' && head[2] == 'a' && head[3] == 'C') return "flac";
    if (head[0] == 'O' && head[1] == 'g' && head[2] == 'g' && head[3] == 'S') return "ogg";
    if (head[0] == 'I' && head[1] == 'D' && head[2] == '3') return "mp3";
    if ((head[0] & 0xFF) == 0xFF && ((head[1] & 0xE0) == 0xE0)) return "mp3";
    if (head[0] == 'P' && head[1] == 'K') return "zip";
    if (head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F') return "pdf";
    return "";
  }

  private static boolean isInputName(String name) {
    if (name == null || name.length() == 0 || name.indexOf(' ') >= 0) return false;
    String low = name.toLowerCase();
    if (low.indexOf("output") >= 0) return false;
    if (low.startsWith("input")) return true;
    if (low.endsWith(".wav") || low.endsWith(".wave") || low.endsWith(".mp3")
        || low.endsWith(".aiff") || low.endsWith(".aif") || low.endsWith(".flac")
        || low.endsWith(".ogg") || low.endsWith(".raw") || low.endsWith(".pcm")) return true;
    java.util.List<String> exts = acceptedExts(low);
    for (int i = 0; i < exts.size(); i++) {
      String e = exts.get(i);
      if ("wav".equals(e) || "mp3".equals(e) || "aiff".equals(e) || "flac".equals(e)
          || "ogg".equals(e) || "raw".equals(e) || "pcm".equals(e)) return true;
    }
    return false;
  }
}
