import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SogniChat: a question to Sogni's hosted chat model (Sogni Intelligence), answered in text.
 *
 * Plain text chat: Sogni's creative tools are off, so a reply never starts a paid image or music
 * job; the chat itself spends a little Sogni credit (Spark). The key is found as SogniMusic finds
 * it: SOGNI_API_KEY, --key_file, the key file in File > Drum Midi Settings, or
 * ~/.config/sogni/credentials.
 *
 * --prompt is the question. --file adds a file after it, and can be given more than once:
 *   - text (a CompareHits or DrumMidi log, a lyric sheet, notes); a Pulsekit .prompt sheet gives
 *     its prompt text;
 *   - MIDI, turned into text the model can read: tempo, time signature, then each track bar by bar
 *     with its notes (drum names on channel 10);
 *   - a PNG or JPEG picture, sent as an image for a model that sees pictures. The saved
 *     conversation names the picture, and --continue does not send it again. --system says how to
 * answer ("You are a drum teacher. Answer briefly."). The reply is printed and saved as a .txt
 * (sogni-chat-<first words>.txt, or the name given), which lands in Downloads on the phone.
 *
 * --models lists the chat models Sogni offers; --model picks one.
 *
 * The saved file is the whole conversation (model, system text, each question and reply), so
 * --continue <that file> goes on from it: the earlier turns are sent again with the new --prompt,
 * and the longer conversation is saved as a new file (sogni-chat-<words>-2.txt, -3...). Its model
 * and system text carry on unless --model or --system are given. A file holding only a reply
 * (from SogniChat before this) continues as that one reply.
 */
public final class SogniChat {
  public static void main(String[] args) throws Exception {
    int code = run(args);
    // Inside Pulsekit (PyJav on the phone runs programs in the app's own process, and sets
    // pulsekit.work) System.exit would close the app, so only a separate run exits with the code.
    if (code != 0 && System.getProperty("pulsekit.work") == null) System.exit(code);
  }

  /** Printed first, so a run's log shows which SogniChat ran. */
  static final String VERSION = "SogniChat 2026-10-06";

  /** The first line of a saved conversation, and the lines that start each turn in it. */
  static final String HEAD = "SogniChat conversation";
  static final String YOU = "=== You ===";
  static final String SOGNI = "=== Sogni ===";

  /** The most of a --file that is sent: chat models read a limited amount of text. */
  static final int FILE_MAX = 60000;

  /** The largest picture sent (7 MB; a data: URI is a third bigger). */
  static final int IMAGE_MAX = 7 * 1024 * 1024;

  /** The program; returns its exit code (0 ok, 1 failed, 2 bad arguments). */
  static int run(String[] typed) throws Exception {
    System.out.println(VERSION);
    String[] args = tidy(typed);
    String out = null;
    String prompt = null;
    String system = null;
    List<String> files = new ArrayList<String>();
    String earlier = null;
    String model = null;
    String keyFile = null;
    String apiBase = null;
    double maxTokens = 0;
    boolean thinking = false;
    boolean models = false;
    for (int i = 0; i < args.length; i++) {
      String a = args[i];
      if (a.equals("--prompt") && i + 1 < args.length) prompt = args[++i];
      else if (a.equals("--system") && i + 1 < args.length) system = args[++i];
      else if (a.equals("--file") && i + 1 < args.length) {
        String f = args[++i].trim();
        if (f.length() > 0) files.add(f);
      }
      else if (a.equals("--continue") && i + 1 < args.length) earlier = args[++i];
      else if (a.equals("--model") && i + 1 < args.length) model = args[++i].trim();
      else if (a.equals("--max_tokens") && i + 1 < args.length) maxTokens = number(a, args[++i]);
      else if (a.equals("--key_file") && i + 1 < args.length) keyFile = args[++i];
      else if (a.equals("--api_base") && i + 1 < args.length) apiBase = args[++i];
      else if (a.equals("--thinking")) thinking = true;
      else if (a.equals("--models")) models = true;
      else if (a.equals("-h") || a.equals("--help")) {
        usage();
        return 0;
      } else if (!a.startsWith("--") && out == null) out = a;
      else {
        System.out.println("Unknown argument: " + a);
        usage();
        return 2;
      }
    }
    if (Double.isNaN(maxTokens)) return 2;
    if (maxTokens < 0 || maxTokens > 32000) {
      System.out.println("Failed: --max_tokens is 1 to 32000 (leave it out for Sogni's default)");
      return 2;
    }
    List<Attachment> attached = new ArrayList<Attachment>();
    for (String f : files) {
      Attachment a = attachment(new File(f));
      if (a == null) return 2;
      attached.add(a);
    }
    Conversation before = null;
    if (earlier != null && earlier.trim().length() > 0) {
      before = conversation(new File(earlier.trim()));
      if (before == null) return 2;
    }
    boolean asked = (prompt != null && prompt.trim().length() > 0) || !attached.isEmpty();
    if (!asked && !models) {
      System.out.println(before != null
          ? "Failed: give --prompt with the next question to continue " + new File(earlier.trim()).getName()
          : "Failed: give --prompt (the question), --file (a text, MIDI or picture file to send), or both, "
              + "for example --prompt \"Suggest a fill for a rock groove at 120 BPM\"");
      usage();
      return 2;
    }
    // A continued conversation keeps its model and system text unless new ones are given.
    if (before != null && (model == null || model.length() == 0)) model = before.model;
    if (before != null && (system == null || system.trim().length() == 0)) system = before.system;
    String key = SogniApi.findKey(keyFile);
    if (key == null) {
      System.out.println("Failed: no Sogni API key. Choose a key file in File > Drum Midi Settings (Sogni API key file), give --key_file "
          + "with a text file holding SOGNI_API_KEY=<your key>, or set SOGNI_API_KEY. Get the key at https://dashboard.sogni.ai (account menu).");
      return 1;
    }
    SogniApi api = new SogniApi(apiBase, key);
    // A long answer takes a while to write; the reply comes in one piece.
    api.timeoutMs = 180000;
    try {
      if (models) {
        List<String> ids = api.chatModels();
        System.out.println("Chat models (" + ids.size() + "):");
        for (String id : ids) System.out.println("  " + id + (id.equals(SogniApi.CHAT_MODEL) ? "  (default)" : ""));
        if (!asked) {
          System.out.println("Succeeded: listed " + ids.size() + " models");
          return 0;
        }
      }
      String question = question(prompt, attached);
      List<String[]> turns = new ArrayList<String[]>();
      if (before != null) {
        turns.addAll(before.turns);
        System.out.println("Continuing " + new File(earlier.trim()).getName() + ": " + exchanges(before.turns) + " earlier "
            + (exchanges(before.turns) == 1 ? "exchange" : "exchanges"));
      }
      List<String> sent = new ArrayList<String>();
      sent.add("user");
      sent.add(question);
      for (Attachment a : attached) if (a.image != null) sent.add(a.image);
      turns.add(sent.toArray(new String[0]));
      String chosen = model == null || model.length() == 0 ? SogniApi.CHAT_MODEL : model;
      System.out.println("Model " + chosen + (thinking ? ", thinking" : ""));
      if (system != null && system.trim().length() > 0) System.out.println("System: " + system.trim());
      StringBuilder shown = new StringBuilder("Prompt: ").append(prompt == null ? "" : prompt.trim());
      for (Attachment a : attached) shown.append(shown.length() > 8 ? " " : "").append("[+ ").append(a.what).append(']');
      System.out.println(shown);
      Object payload = api.chat(SogniApi.chatInput(chosen, system, turns, (int) maxTokens, thinking));
      String reply = SogniApi.chatReply(payload);
      if (reply == null || reply.length() == 0) {
        System.out.println("Failed: Sogni sent no reply text" + (thinking ? " (with --thinking the answer can run out of tokens: raise --max_tokens)" : ""));
        return 1;
      }
      // The saved conversation keeps the question's text; a picture is named in it, not stored.
      turns.set(turns.size() - 1, new String[] {"user", question});
      System.out.println();
      System.out.println(reply);
      System.out.println();
      String usage = SogniApi.chatUsage(payload);
      if (usage != null) System.out.println("Tokens: " + usage);
      turns.add(new String[] {"assistant", reply});
      String name = out != null ? out : before != null ? continuedName(new File(earlier.trim()).getName(), exchanges(turns)) : replyName(prompt, files.isEmpty() ? null : files.get(0));
      File saved = save(name, transcript(chosen, system, turns));
      if (saved == null) {
        System.out.println("Could not save the reply (it is in the log above)");
        System.out.println("Succeeded");
      } else {
        System.out.println("Wrote " + saved.getName());
        System.out.println("Succeeded: " + saved.getName());
      }
    } catch (SogniApi.ApiException ex) {
      boolean picture = false;
      for (Attachment a : attached) if (a.image != null) picture = true;
      System.out.println("Failed: " + ex.getMessage() + (picture && ex.status == 400
          ? " (a picture needs a model that sees pictures: try --model deepseek-v4-flash-vision-exp-dspark-1m)" : ""));
      return 1;
    } catch (IOException ex) {
      System.out.println("Failed: " + ex.getMessage());
      return 1;
    }
    return 0;
  }

  static void usage() {
    System.out.println("Usage: java SogniChat [output.txt] [--prompt text] [--file notes.txt|song.mid|picture.jpg] [--continue chat.txt] [--system text] [--model id] "
        + "[--max_tokens N] [--thinking] [--models] [--key_file credentials.txt]");
  }

  /** A --file as it is sent: text (a text file, or a MIDI file read out), or a picture. */
  static final class Attachment {
    String name;
    /** The text sent after the prompt, or null for a picture. */
    String text;
    /** A picture as a data: URI, or null. */
    String image;
    /** For the log: "notes.txt, 34 characters". */
    String what;
  }

  /** What is sent as text: the prompt, then each file under its name (a picture is named; it goes with the message). */
  static String question(String prompt, List<Attachment> attached) {
    StringBuilder sb = new StringBuilder(prompt == null ? "" : prompt.trim());
    for (Attachment a : attached) {
      if (sb.length() > 0) sb.append("\n\n");
      if (a.image != null) sb.append("Picture ").append(a.name).append(" is attached.");
      else sb.append("File ").append(a.name).append(":\n").append(a.text);
    }
    return sb.toString();
  }

  /** A --file read by its contents: MIDI (MThd), a PNG or JPEG picture, or text. Null after saying why. */
  static Attachment attachment(File f) {
    byte[] data = readBytes(f, "--file");
    if (data == null) return null;
    Attachment a = new Attachment();
    a.name = f.getName();
    if (data.length >= 4 && data[0] == 'M' && data[1] == 'T' && data[2] == 'h' && data[3] == 'd') {
      a.text = midiText(f.getName(), data);
      if (a.text == null) return null;
      if (a.text.length() > FILE_MAX) {
        int cut = a.text.lastIndexOf('\n', FILE_MAX);
        a.text = a.text.substring(0, cut > 0 ? cut : FILE_MAX) + "\n(the rest of the song is left out: too long to send)";
        System.out.println("Note: " + f.getName() + " is long; its first part is sent");
      }
      a.what = f.getName() + " as text, " + a.text.length() + " characters";
      return a;
    }
    String mime = pictureType(data);
    if (mime != null) {
      if (data.length > IMAGE_MAX) {
        System.out.println("Failed: " + f.getName() + " is " + (data.length / (1024 * 1024)) + " MB; send a picture of up to "
            + (IMAGE_MAX / (1024 * 1024)) + " MB (a smaller size or a JPEG)");
        return null;
      }
      a.image = "data:" + mime + ";base64," + base64(data);
      a.what = "picture " + f.getName() + ", " + (data.length / 1024) + " KB";
      return a;
    }
    a.text = fileText(f);
    if (a.text == null) return null;
    a.what = f.getName() + ", " + a.text.length() + " characters";
    return a;
  }

  /** "image/png" or "image/jpeg" from the file's first bytes, else null. */
  static String pictureType(byte[] d) {
    if (d.length >= 8 && (d[0] & 0xff) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return "image/png";
    if (d.length >= 3 && (d[0] & 0xff) == 0xff && (d[1] & 0xff) == 0xd8 && (d[2] & 0xff) == 0xff) return "image/jpeg";
    return null;
  }

  /** Base64 without java.util.Base64 (Android before API 26 has none). */
  static String base64(byte[] d) {
    String abc = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    StringBuilder sb = new StringBuilder((d.length + 2) / 3 * 4);
    for (int i = 0; i < d.length; i += 3) {
      int b0 = d[i] & 0xff;
      int b1 = i + 1 < d.length ? d[i + 1] & 0xff : 0;
      int b2 = i + 2 < d.length ? d[i + 2] & 0xff : 0;
      int v = (b0 << 16) | (b1 << 8) | b2;
      sb.append(abc.charAt((v >> 18) & 63)).append(abc.charAt((v >> 12) & 63));
      sb.append(i + 1 < d.length ? abc.charAt((v >> 6) & 63) : '=');
      sb.append(i + 2 < d.length ? abc.charAt(v & 63) : '=');
    }
    return sb.toString();
  }

  /**
   * A text file's contents, cut to FILE_MAX characters; a Pulsekit .prompt sheet (PKPROMPT1) gives
   * the prompt after its "---" line. Null, after saying why, when it cannot be read.
   */
  static String fileText(File f) {
    String text = readText(f, "--file");
    if (text == null) return null;
    if (text.startsWith("PKPROMPT1")) {
      int at = text.indexOf("\n---\n");
      if (at >= 0) text = text.substring(at + 5);
    }
    text = text.trim();
    if (text.length() == 0) {
      System.out.println("Failed: " + f.getName() + " is empty");
      return null;
    }
    if (text.length() > FILE_MAX) {
      System.out.println("Note: " + f.getName() + " is long; its first " + FILE_MAX + " characters are sent");
      text = text.substring(0, FILE_MAX);
    }
    return text;
  }

  /** A text file's contents, or null after saying why (`flag` names the switch it came from). */
  static String readText(File f, String flag) {
    byte[] data = readBytes(f, flag);
    if (data == null) return null;
    String text = new String(data, StandardCharsets.UTF_8);
    if (text.indexOf('\0') >= 0) {
      System.out.println("Failed: " + f.getName() + " is not a text file"
          + ("--file".equals(flag) ? ", a MIDI file or a PNG or JPEG picture" : " (pick a sogni-chat .txt)"));
      return null;
    }
    return text.replace("\r\n", "\n");
  }

  /** A file's bytes, or null after saying why (`flag` names the switch it came from). */
  static byte[] readBytes(File f, String flag) {
    if (!f.isFile()) {
      System.out.println("Failed: " + flag + " " + f.getName() + " was not found");
      return null;
    }
    try {
      InputStream in = new FileInputStream(f);
      try {
        return SogniApi.readAll(in);
      } finally {
        in.close();
      }
    } catch (IOException ex) {
      System.out.println("Failed: could not read " + f.getName() + ": " + ex.getMessage());
      return null;
    }
  }

  // ---- MIDI as text ----

  /** General MIDI drum names (channel 10), by note number. */
  static final String[] DRUMS = {
    "Acoustic kick", "Kick", "Side stick", "Snare", "Clap", "Electric snare", "Low floor tom", "Closed hat",
    "High floor tom", "Pedal hat", "Low tom", "Open hat", "Low-mid tom", "High-mid tom", "Crash", "High tom",
    "Ride", "China", "Ride bell", "Tambourine", "Splash", "Cowbell", "Crash 2", "Vibraslap", "Ride 2",
    "High bongo", "Low bongo", "Muted high conga", "Open high conga", "Low conga", "High timbale",
    "Low timbale", "High agogo", "Low agogo", "Cabasa", "Maracas", "Short whistle", "Long whistle",
    "Short guiro", "Long guiro", "Claves", "High wood block", "Low wood block", "Muted cuica", "Open cuica",
    "Muted triangle", "Open triangle"
  };
  static final String[] NOTES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

  /**
   * A MIDI file as text a chat model can read: the tempo and time signature, then each track (and
   * channel) bar by bar, "Bar 3 | 1: Kick, Closed hat | 1.5: Closed hat | 2: Snare". Beats count
   * quarter notes from 1; a pitched note shows its length in beats, C4 (0.5). Null after saying why.
   */
  static String midiText(String name, byte[] b) {
    try {
      int division = u16(b, 12);
      if ((division & 0x8000) != 0 || division == 0) {
        System.out.println("Failed: " + name + " counts time in SMPTE frames, which SogniChat does not read");
        return null;
      }
      int format = u16(b, 8);
      int tracks = u16(b, 10);
      // {track, channel, tick, key, velocity, length in ticks (-1 until its note-off)}
      List<long[]> notes = new ArrayList<long[]>();
      Map<Integer, String> names = new LinkedHashMap<Integer, String>();
      double bpm = 0;
      int num = 0;
      int den = 4;
      int tempos = 0;
      int sigs = 0;
      long last = 0;
      int pos = 8 + (int) u32(b, 4);
      for (int t = 0; t < tracks && pos + 8 <= b.length; t++) {
        int len = (int) u32(b, pos + 4);
        boolean track = b[pos] == 'M' && b[pos + 1] == 'T' && b[pos + 2] == 'r' && b[pos + 3] == 'k';
        int end = Math.min(b.length, pos + 8 + len);
        int p = pos + 8;
        pos = pos + 8 + len;
        if (!track) continue;
        long tick = 0;
        int status = 0;
        Map<Integer, long[]> open = new LinkedHashMap<Integer, long[]>();
        while (p < end) {
          long[] delta = varLen(b, p);
          tick += delta[0];
          p = (int) delta[1];
          int c = b[p] & 0xff;
          if (c == 0xff) {
            int type = b[p + 1] & 0xff;
            long[] l = varLen(b, p + 2);
            int at = (int) l[1];
            int n = (int) l[0];
            if (type == 0x51 && n >= 3) {
              double next = 60000000.0 / (((b[at] & 0xff) << 16) | ((b[at + 1] & 0xff) << 8) | (b[at + 2] & 0xff));
              if (bpm == 0) bpm = next;
              else if (Math.abs(next - bpm) > 0.01) tempos++;
            } else if (type == 0x58 && n >= 2) {
              if (num == 0) {
                num = b[at] & 0xff;
                den = 1 << (b[at + 1] & 0xff);
              } else {
                sigs++;
              }
            } else if (type == 0x03 && n > 0 && !names.containsKey(Integer.valueOf(t))) {
              names.put(Integer.valueOf(t), new String(b, at, n, StandardCharsets.UTF_8).trim());
            }
            p = at + n;
          } else if (c == 0xf0 || c == 0xf7) {
            long[] l = varLen(b, p + 1);
            p = (int) (l[1] + l[0]);
          } else {
            if (c >= 0x80) {
              status = c;
              p++;
            }
            int kind = status & 0xf0;
            int ch = status & 0x0f;
            if (kind == 0xc0 || kind == 0xd0) {
              p += 1;
              continue;
            }
            int key = b[p] & 0x7f;
            int vel = b[p + 1] & 0x7f;
            p += 2;
            Integer slot = Integer.valueOf(ch * 128 + key);
            if (kind == 0x90 && vel > 0) {
              long[] note = {t, ch, tick, key, vel, -1};
              notes.add(note);
              open.put(slot, note);
              last = Math.max(last, tick);
            } else if (kind == 0x80 || kind == 0x90) {
              long[] note = open.remove(slot);
              if (note != null) note[5] = tick - note[2];
            }
          }
        }
      }
      if (num == 0) num = 4;
      if (bpm == 0) bpm = 120;
      double beatsPerBar = num * 4.0 / den;
      long barTicks = Math.max(1, Math.round(division * beatsPerBar));
      StringBuilder sb = new StringBuilder();
      sb.append("MIDI ").append(name).append(": format ").append(format).append(", ").append(tracks).append(tracks == 1 ? " track" : " tracks")
          .append(", ").append(notes.size()).append(" notes\n");
      sb.append("Tempo ").append(beats(bpm)).append(" BPM").append(tempos > 0 ? " (then " + tempos + " tempo changes)" : "")
          .append(", time signature ").append(num).append('/').append(den).append(sigs > 0 ? " (then " + sigs + " changes)" : "")
          .append(", ").append(last / barTicks + 1).append(" bars. Beats count quarter notes from 1.\n");
      if (notes.isEmpty()) return sb.append("(no notes)\n").toString();
      // One section per track and channel, in the order they first play.
      List<String> order = new ArrayList<String>();
      Map<String, List<long[]>> sections = new LinkedHashMap<String, List<long[]>>();
      for (long[] n : notes) {
        String k = n[0] + ":" + n[1];
        if (!sections.containsKey(k)) {
          sections.put(k, new ArrayList<long[]>());
          order.add(k);
        }
        sections.get(k).add(n);
      }
      for (String k : order) {
        List<long[]> list = sections.get(k);
        int t = (int) list.get(0)[0];
        int ch = (int) list.get(0)[1];
        boolean drums = ch == 9;
        String trackName = names.get(Integer.valueOf(t));
        sb.append("\nTrack ").append(t + 1).append(trackName != null && trackName.length() > 0 ? " \"" + trackName + "\"" : "")
            .append(", channel ").append(ch + 1).append(drums ? " (drums)" : "").append(": ").append(list.size()).append(" notes\n");
        java.util.Collections.sort(list, new java.util.Comparator<long[]>() {
          public int compare(long[] x, long[] y) {
            return x[2] != y[2] ? (x[2] < y[2] ? -1 : 1) : (int) (x[3] - y[3]);
          }
        });
        long bar = -1;
        long at = -1;
        StringBuilder line = null;
        for (long[] n : list) {
          long nb = n[2] / barTicks;
          if (nb != bar) {
            if (line != null) sb.append(line).append('\n');
            bar = nb;
            at = -1;
            line = new StringBuilder("Bar ").append(nb + 1);
          }
          if (n[2] != at) {
            at = n[2];
            line.append(" | ").append(beats(1 + (n[2] - nb * barTicks) / (double) division)).append(": ");
          } else {
            line.append(", ");
          }
          int key = (int) n[3];
          if (drums) line.append(key >= 35 && key - 35 < DRUMS.length ? DRUMS[key - 35] : "Drum " + key);
          else line.append(NOTES[key % 12]).append(key / 12 - 1).append(n[5] > 0 ? " (" + beats(n[5] / (double) division) + ")" : "");
        }
        if (line != null) sb.append(line).append('\n');
      }
      return sb.toString();
    } catch (RuntimeException ex) {
      System.out.println("Failed: " + name + " could not be read as MIDI (" + ex + ")");
      return null;
    }
  }

  /** 1, 1.5, 2.25: a beat position or length, to two decimals. */
  static String beats(double v) {
    String t = String.format(java.util.Locale.ROOT, "%.2f", v);
    while (t.endsWith("0")) t = t.substring(0, t.length() - 1);
    if (t.endsWith(".")) t = t.substring(0, t.length() - 1);
    return t;
  }

  static int u16(byte[] b, int at) {
    return ((b[at] & 0xff) << 8) | (b[at + 1] & 0xff);
  }

  static long u32(byte[] b, int at) {
    return ((long) (b[at] & 0xff) << 24) | ((b[at + 1] & 0xff) << 16) | ((b[at + 2] & 0xff) << 8) | (b[at + 3] & 0xff);
  }

  /** A MIDI variable-length number at `at`: {value, index after it}. */
  static long[] varLen(byte[] b, int at) {
    long v = 0;
    int p = at;
    for (int i = 0; i < 4; i++) {
      int c = b[p++] & 0xff;
      v = (v << 7) | (c & 0x7f);
      if ((c & 0x80) == 0) break;
    }
    return new long[] {v, p};
  }

  /** A saved conversation: its model and system text (null when it had none) and its turns as {role, text}. */
  static final class Conversation {
    String model;
    String system;
    final List<String[]> turns = new ArrayList<String[]>();
  }

  /**
   * The conversation saved in `f` (transcript writes it). A file without the SogniChat heading is
   * an earlier reply on its own, and continues as that reply. Null, after saying why, if unreadable.
   */
  static Conversation conversation(File f) {
    String text = readText(f, "--continue");
    if (text == null) return null;
    Conversation c = new Conversation();
    if (!text.startsWith(HEAD)) {
      if (text.trim().length() == 0) {
        System.out.println("Failed: " + f.getName() + " is empty");
        return null;
      }
      c.turns.add(new String[] {"assistant", text.trim()});
      return c;
    }
    String role = null;
    StringBuilder body = new StringBuilder();
    for (String line : text.split("\n", -1)) {
      if (line.equals(YOU) || line.equals(SOGNI)) {
        if (role != null) c.turns.add(new String[] {role, body.toString().trim()});
        role = line.equals(YOU) ? "user" : "assistant";
        body.setLength(0);
      } else if (role != null) {
        body.append(line).append('\n');
      } else if (line.startsWith("Model: ")) {
        c.model = line.substring(7).trim();
      } else if (line.startsWith("System: ")) {
        c.system = line.substring(8).trim();
      }
    }
    if (role != null) c.turns.add(new String[] {role, body.toString().trim()});
    if (c.turns.isEmpty()) {
      System.out.println("Failed: " + f.getName() + " holds no questions or replies to continue");
      return null;
    }
    return c;
  }

  /** The conversation as it is saved: a heading, the model and system text, then each turn under its marker. */
  static String transcript(String model, String system, List<String[]> turns) {
    StringBuilder sb = new StringBuilder(HEAD).append('\n');
    sb.append("Model: ").append(model).append('\n');
    if (system != null && system.trim().length() > 0) sb.append("System: ").append(system.trim().replaceAll("\\s*\n\\s*", " ")).append('\n');
    for (String[] t : turns) sb.append('\n').append("user".equals(t[0]) ? YOU : SOGNI).append('\n').append(t[1].trim()).append('\n');
    return sb.toString();
  }

  /** Questions answered: the replies in `turns`. */
  static int exchanges(List<String[]> turns) {
    int n = 0;
    for (String[] t : turns) if ("assistant".equals(t[0])) n++;
    return n;
  }

  /** sogni-chat-suggest-a-fill-3.txt for the third exchange of sogni-chat-suggest-a-fill(1).txt or -2.txt. */
  static String continuedName(String from, int exchanges) {
    String stem = from.replaceAll("\\.[^.]*$", "");
    stem = stem.replaceAll("\\s*\\(\\d+\\)$", "").replaceAll("-\\d+$", "");
    if (stem.length() == 0) stem = "sogni-chat";
    return stem + "-" + exchanges + ".txt";
  }

  /** sogni-chat-suggest-a-fill-for.txt: the first words of the prompt (or the file's name). */
  static String replyName(String prompt, String file) {
    String from = prompt != null && prompt.trim().length() > 0 ? prompt : (file == null ? "" : new File(file.trim()).getName().replaceAll("\\.[^.]*$", ""));
    StringBuilder sb = new StringBuilder();
    int words = 0;
    for (String w : from.toLowerCase().split("[^a-z0-9]+")) {
      if (w.length() == 0) continue;
      if (sb.length() > 0) sb.append('-');
      sb.append(w);
      if (++words == 4 || sb.length() > 30) break;
    }
    return "sogni-chat" + (sb.length() > 0 ? "-" + sb : "") + ".txt";
  }

  /** Writes the reply beside the program's other files, never over an existing file. Null if it could not. */
  static File save(String name, String reply) {
    File file = inWork(name);
    String path = file.getPath();
    int dot = path.lastIndexOf('.');
    String stem = dot > path.lastIndexOf(File.separatorChar) ? path.substring(0, dot) : path;
    String ext = dot > path.lastIndexOf(File.separatorChar) ? path.substring(dot) : "";
    for (int n = 1; file.exists(); n++) file = new File(stem + "(" + n + ")" + ext);
    try {
      FileOutputStream fos = new FileOutputStream(file);
      try {
        fos.write((reply + "\n").getBytes(StandardCharsets.UTF_8));
      } finally {
        fos.close();
      }
      return file;
    } catch (IOException ex) {
      return null;
    }
  }

  /**
   * A file in the work folder. On the phone PyJav runs programs inside the app, where a bare name
   * would land in the process's own folder ("/", read-only), so names are placed under PyJav's
   * work folder (pulsekit.work, else user.dir); an absolute path stays as given.
   */
  static File inWork(String name) {
    File f = new File(name);
    if (f.isAbsolute()) return f;
    // Android ignores setting user.dir (it stays "/", read-only), so PyJav's own pulsekit.work comes first.
    String work = System.getProperty("pulsekit.work");
    if (work == null || work.length() == 0) work = System.getProperty("user.dir", ".");
    return new File(work, name);
  }

  /**
   * Arguments as typed by hand: "--prompt hello" given as one argument becomes the switch and its
   * value, and a switch given twice in a row (--prompt --prompt hello) counts once.
   */
  static String[] tidy(String[] args) {
    List<String> out = new ArrayList<String>();
    for (String a : args) {
      String t = a == null ? "" : a.trim();
      int sp = t.startsWith("--") ? t.indexOf(' ') : -1;
      List<String> parts = new ArrayList<String>();
      if (sp > 0) {
        parts.add(t.substring(0, sp));
        parts.add(t.substring(sp + 1).trim());
      } else {
        parts.add(a);
      }
      for (String p : parts) {
        if (p.startsWith("--") && !out.isEmpty() && p.equals(out.get(out.size() - 1))) continue;
        out.add(p);
      }
    }
    return out.toArray(new String[0]);
  }

  /** A number for `flag`, or NaN after saying what is wrong. */
  static double number(String flag, String value) {
    try {
      return Double.parseDouble(value.trim());
    } catch (NumberFormatException ex) {
      System.out.println("Failed: " + flag + " needs a number, not \"" + value + "\"");
      return Double.NaN;
    }
  }

  /** shared/src/pulsekit/SogniApi.java, copied here (programs see only the Java runtime); android/build.sh checks they match. */
  static final class SogniApi {
    // --- SogniApi begin ---
    public static final String BASE = "https://api.sogni.ai";
    public static final String APP_SOURCE = "pulsekit";
    public static final String[] DONE = {"completed", "partial_failure", "failed", "cancelled", "waiting_for_user"};

    /** Progress lines (status changes, waits). */
    public interface Log {
      void line(String s);
    }

    /** A request Sogni refused, with its HTTP status and, for 429, the seconds to wait. */
    public static final class ApiException extends IOException {
      public final int status;
      public final int retryAfter;

      public ApiException(String message, int status, int retryAfter) {
        super(message);
        this.status = status;
        this.retryAfter = retryAfter;
      }
    }

    public final String base;
    public final String apiKey;
    public int timeoutMs = 60000;

    public SogniApi(String base, String apiKey) {
      String b = base == null || base.length() == 0 ? BASE : base;
      while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
      this.base = b;
      this.apiKey = apiKey;
    }

    /** The key file set in Pulsekit's settings (File > Drum Midi Settings), or null. Set by ApiKeys. */
    public static String keyFileSetting;

    /**
     * The API key: SOGNI_API_KEY in the environment, else SOGNI_API_KEY=... (or the key alone) in
     * keyFile when given, in the settings' key file, or in ~/.config/sogni/credentials. Null when
     * none is found.
     */
    public static String findKey(String keyFile) {
      String env = System.getenv("SOGNI_API_KEY");
      if (env != null && env.trim().length() > 0) return env.trim();
      List<File> files = new ArrayList<File>();
      if (keyFile != null && keyFile.length() > 0) files.add(new File(keyFile));
      if (keyFileSetting != null && keyFileSetting.length() > 0) files.add(new File(keyFileSetting));
      String home = System.getProperty("user.home");
      if (home != null) files.add(new File(home, ".config/sogni/credentials"));
      for (File f : files) {
        String k = keyIn(f);
        if (k != null) return k;
      }
      return null;
    }

    /** The key in a file: a SOGNI_API_KEY=... line, or a line holding only the key. Null if none. */
    public static String keyIn(File f) {
      if (f == null || !f.isFile()) return null;
      try {
        return keyInText(new String(readAll(new java.io.FileInputStream(f)), StandardCharsets.UTF_8));
      } catch (IOException ex) {
        return null;
      }
    }

    public static String keyInText(String text) {
      if (text == null) return null;
      for (String line : text.split("\r?\n")) {
        String t = line.trim();
        if (t.startsWith("export ")) t = t.substring(7).trim();
        if (t.startsWith("SOGNI_API_KEY=")) {
          String v = t.substring(14).trim();
          if (v.length() > 1 && (v.startsWith("\"") && v.endsWith("\"") || v.startsWith("'") && v.endsWith("'"))) v = v.substring(1, v.length() - 1);
          if (v.length() > 0) return v;
        }
        // A file holding only the key.
        if (t.length() >= 16 && t.indexOf('=') < 0 && t.indexOf(' ') < 0 && !t.startsWith("#")) return t;
      }
      return null;
    }

    /**
     * A one-step generate_music workflow. Zero or empty values are left to Sogni's defaults
     * (30 s, 120 BPM, C major, 4/4, turbo).
     */
    public static String musicInput(String title, String prompt, double duration, double bpm, String keyscale, int timesig, String model, String lyrics) {
      Map<String, Object> args = new LinkedHashMap<String, Object>();
      args.put("prompt", prompt);
      if (duration > 0) args.put("duration", Double.valueOf(duration));
      if (bpm > 0) args.put("bpm", Double.valueOf(bpm));
      if (keyscale != null && keyscale.length() > 0) args.put("keyscale", keyscale);
      if (timesig > 0) args.put("timesig", Integer.valueOf(timesig));
      if (model != null && model.length() > 0) args.put("model", model);
      if (lyrics != null && lyrics.length() > 0) args.put("lyrics", lyrics);
      args.put("numberOfVariations", Integer.valueOf(1));
      Map<String, Object> step = new LinkedHashMap<String, Object>();
      step.put("id", "music");
      step.put("toolName", "generate_music");
      step.put("arguments", args);
      List<Object> steps = new ArrayList<Object>();
      steps.add(step);
      Map<String, Object> input = new LinkedHashMap<String, Object>();
      if (title != null && title.length() > 0) input.put("title", title);
      input.put("steps", steps);
      return toJson(input);
    }

    /** Starts a workflow from input JSON ({"steps": [...]}) and returns its id. */
    public String start(String inputJson, boolean confirmCost, double maxCost) throws IOException {
      StringBuilder body = new StringBuilder();
      body.append("{\"input\":").append(inputJson);
      body.append(",\"token_type\":\"spark\",\"app_source\":").append(quote(APP_SOURCE));
      if (confirmCost) body.append(",\"confirm_cost\":true");
      if (maxCost > 0) body.append(",\"max_estimated_capacity_units\":").append(number(maxCost));
      body.append('}');
      Map<String, Object> wf = workflowOf(this.request("POST", "/v1/creative-agent/workflows", body.toString()));
      String id = str(wf.get("workflowId"));
      if (id == null) id = str(wf.get("id"));
      if (id == null) throw new IOException("Sogni did not return a workflow id");
      return id;
    }

    /** The workflow record. */
    public Map<String, Object> workflow(String id) throws IOException {
      return workflowOf(this.request("GET", "/v1/creative-agent/workflows/" + enc(id), null));
    }

    /** The workflow's event list (what happened, step by step), as Sogni returns it. */
    public Object events(String id) throws IOException {
      return this.request("GET", "/v1/creative-agent/workflows/" + enc(id) + "/events", null);
    }

    // ---- Chat (Sogni Intelligence, OpenAI-style /v1/chat/completions) ----

    /** The hosted chat model Sogni's own tools use by default. */
    public static final String CHAT_MODEL = "qwen3.6-35b-a3b-gguf-iq4xs";

    /**
     * A plain text chat request: no Sogni tools, so the reply is text only. `turns` are
     * {role, text} pairs ("user" or "assistant") after the optional system text; a user turn can add
     * pictures after its text, as data: URIs (PNG or JPEG), which a vision model sees. maxTokens 0
     * leaves Sogni's default. `thinking` lets the model reason before it answers (slower, more tokens).
     */
    public static String chatInput(String model, String system, List<String[]> turns, int maxTokens, boolean thinking) {
      List<Object> messages = new ArrayList<Object>();
      if (system != null && system.trim().length() > 0) messages.add(message("system", system));
      for (String[] t : turns) {
        if (t.length <= 2) {
          messages.add(message(t[0], t[1]));
          continue;
        }
        // Text and pictures in one message, as OpenAI-style vision input.
        List<Object> parts = new ArrayList<Object>();
        Map<String, Object> text = new LinkedHashMap<String, Object>();
        text.put("type", "text");
        text.put("text", t[1]);
        parts.add(text);
        for (int i = 2; i < t.length; i++) {
          Map<String, Object> url = new LinkedHashMap<String, Object>();
          url.put("url", t[i]);
          Map<String, Object> image = new LinkedHashMap<String, Object>();
          image.put("type", "image_url");
          image.put("image_url", url);
          parts.add(image);
        }
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("role", t[0]);
        m.put("content", parts);
        messages.add(m);
      }
      Map<String, Object> body = new LinkedHashMap<String, Object>();
      body.put("model", model == null || model.length() == 0 ? CHAT_MODEL : model);
      body.put("messages", messages);
      if (maxTokens > 0) body.put("max_tokens", Integer.valueOf(maxTokens));
      body.put("token_type", "spark");
      body.put("app_source", APP_SOURCE);
      body.put("sogni_tools", Boolean.FALSE);
      body.put("sogni_tool_execution", Boolean.FALSE);
      Map<String, Object> kwargs = new LinkedHashMap<String, Object>();
      kwargs.put("enable_thinking", Boolean.valueOf(thinking));
      body.put("chat_template_kwargs", kwargs);
      return toJson(body);
    }

    static Map<String, Object> message(String role, String text) {
      Map<String, Object> m = new LinkedHashMap<String, Object>();
      m.put("role", role);
      m.put("content", text);
      return m;
    }

    /** Sends a chat request (chatInput) and returns Sogni's reply as it came. */
    public Object chat(String inputJson) throws IOException {
      return this.request("POST", "/v1/chat/completions", inputJson);
    }

    /** The reply's text (choices[0].message.content, also inside "data"), without a <think> part; null if none. */
    @SuppressWarnings("unchecked")
    public static String chatReply(Object payload) {
      if (!(payload instanceof Map)) return null;
      Map<String, Object> p = (Map<String, Object>) payload;
      Object data = p.get("data");
      if (!(p.get("choices") instanceof List) && data instanceof Map) p = (Map<String, Object>) data;
      Object choices = p.get("choices");
      if (!(choices instanceof List) || ((List<Object>) choices).isEmpty()) return null;
      Object first = ((List<Object>) choices).get(0);
      if (!(first instanceof Map)) return null;
      Object m = ((Map<String, Object>) first).get("message");
      if (!(m instanceof Map)) m = ((Map<String, Object>) first).get("delta");
      if (!(m instanceof Map)) return null;
      String text = str(((Map<String, Object>) m).get("content"));
      if (text == null) return null;
      int close = text.lastIndexOf("</think>");
      if (close >= 0) text = text.substring(close + 8);
      return text.trim();
    }

    /** "120 in, 340 out" from the reply's token counts, or null. */
    @SuppressWarnings("unchecked")
    public static String chatUsage(Object payload) {
      if (!(payload instanceof Map)) return null;
      Object u = ((Map<String, Object>) payload).get("usage");
      if (!(u instanceof Map) && ((Map<String, Object>) payload).get("data") instanceof Map) u = ((Map<String, Object>) ((Map<String, Object>) payload).get("data")).get("usage");
      if (!(u instanceof Map)) return null;
      Object inN = ((Map<String, Object>) u).get("prompt_tokens");
      Object outN = ((Map<String, Object>) u).get("completion_tokens");
      String in = inN instanceof Number ? number(((Number) inN).doubleValue()) : str(inN);
      String out = outN instanceof Number ? number(((Number) outN).doubleValue()) : str(outN);
      if (in == null && out == null) return null;
      return (in == null ? "?" : in) + " in, " + (out == null ? "?" : out) + " out";
    }

    /** The chat model ids Sogni offers (/v1/models). */
    @SuppressWarnings("unchecked")
    public List<String> chatModels() throws IOException {
      Object payload = this.request("GET", "/v1/models", null);
      List<String> out = new ArrayList<String>();
      Object list = payload instanceof Map ? ((Map<String, Object>) payload).get("data") : payload;
      if (list instanceof Map) list = ((Map<String, Object>) list).get("data");
      if (list instanceof List) {
        for (Object o : (List<Object>) list) {
          String id = o instanceof Map ? str(((Map<String, Object>) o).get("id")) : str(o);
          if (id != null) out.add(id);
        }
      }
      return out;
    }

    /**
     * Follows the workflow until it stops (completed, failed, cancelled, or waiting for the user)
     * and returns its record. Reads the event stream, one long request; if that breaks, looks again
     * every 20 seconds, waiting longer when Sogni asks (429).
     */
    public Map<String, Object> waitFor(String id, long timeoutMs, Log log) throws IOException {
      long end = System.currentTimeMillis() + timeoutMs;
      String last = null;
      try {
        last = this.stream(id, end, log);
      } catch (IOException ex) {
        if (log != null) log.line("Event stream stopped (" + ex.getMessage() + "); checking every 20 s");
      }
      while (true) {
        Map<String, Object> wf;
        try {
          wf = this.workflow(id);
        } catch (ApiException ex) {
          if (ex.status != 429) throw ex;
          int wait = Math.max(ex.retryAfter, 20);
          if (log != null) log.line("Sogni asks to wait " + wait + " s");
          sleep(wait * 1000L);
          continue;
        }
        String status = str(wf.get("status"));
        if (status != null && !status.equals(last) && log != null) log.line("Status: " + status);
        last = status;
        if (isDone(status)) return wf;
        if (System.currentTimeMillis() > end) throw new IOException("Timed out; the workflow " + id + " is still " + status + " on Sogni");
        sleep(20000L);
      }
    }

    /** Reads the workflow's event stream until a final status or the stream ends; returns the last status seen. */
    String stream(String id, long end, Log log) throws IOException {
      HttpURLConnection c = this.open("GET", "/v1/creative-agent/workflows/" + enc(id) + "/events/stream");
      c.setRequestProperty("Accept", "text/event-stream");
      c.setReadTimeout((int) Math.max(1000L, Math.min(Integer.MAX_VALUE, end - System.currentTimeMillis())));
      int code = c.getResponseCode();
      if (code / 100 != 2) throw failure(c, code);
      String last = null;
      java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
      try {
        StringBuilder data = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
          if (line.startsWith("data:")) {
            data.append(line.substring(5).trim());
            continue;
          }
          if (line.length() > 0) continue;
          if (data.length() == 0) continue;
          String status = null;
          try {
            Object ev = parseJson(data.toString());
            if (ev instanceof Map) {
              Map<?, ?> m = (Map<?, ?>) ev;
              status = str(m.get("status"));
              if (status == null && m.get("data") instanceof Map) status = str(((Map<?, ?>) m.get("data")).get("status"));
              if (status == null && m.get("workflow") instanceof Map) status = str(((Map<?, ?>) m.get("workflow")).get("status"));
            }
          } catch (RuntimeException ignored) {
            // Not JSON: a keep-alive or a note.
          }
          data.setLength(0);
          if (status != null && !status.equals(last)) {
            if (log != null) log.line("Status: " + status);
            last = status;
          }
          if (isDone(status)) break;
        }
      } finally {
        in.close();
        c.disconnect();
      }
      return last;
    }

    public static boolean isDone(String status) {
      if (status == null) return false;
      for (String d : DONE) if (d.equals(status)) return true;
      return false;
    }

    /** Audio results anywhere in a workflow record: objects with a URL that is audio by type or extension. */
    public static List<Map<String, Object>> audioArtifacts(Object record) {
      List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
      collectAudio(record, out);
      return out;
    }

    @SuppressWarnings("unchecked")
    static void collectAudio(Object o, List<Map<String, Object>> out) {
      if (o instanceof Map) {
        Map<String, Object> m = (Map<String, Object>) o;
        String url = str(m.get("url"));
        if (url != null && url.startsWith("http") && isAudio(m, url)) {
          for (Map<String, Object> seen : out) if (url.equals(seen.get("url"))) return;
          out.add(m);
          return;
        }
        for (Object v : m.values()) collectAudio(v, out);
      } else if (o instanceof List) {
        for (Object v : (List<Object>) o) collectAudio(v, out);
      }
    }

    static boolean isAudio(Map<String, Object> m, String url) {
      for (String k : new String[] {"mediaType", "mimeType", "type", "contentType"}) {
        String v = str(m.get(k));
        if (v != null && v.toLowerCase().startsWith("audio")) return true;
      }
      return extension(url, null) != null;
    }

    /** ".mp3", ".wav", ".flac", ".m4a", ".ogg" or ".aac" from the URL path or a MIME type; fallback when neither says. */
    public static String extension(String url, String fallback) {
      String path = url == null ? "" : url.toLowerCase();
      int q = path.indexOf('?');
      if (q >= 0) path = path.substring(0, q);
      for (String e : new String[] {".mp3", ".wav", ".flac", ".m4a", ".ogg", ".aac"}) if (path.endsWith(e)) return e;
      if (path.startsWith("audio/")) {
        if (path.contains("mpeg") || path.contains("mp3")) return ".mp3";
        if (path.contains("wav")) return ".wav";
        if (path.contains("flac")) return ".flac";
        if (path.contains("mp4") || path.contains("m4a")) return ".m4a";
        if (path.contains("ogg")) return ".ogg";
      }
      return fallback;
    }

    /** Downloads a result URL (signed; no key is sent to hosts other than the API). */
    public byte[] download(String url) throws IOException {
      HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
      c.setConnectTimeout(this.timeoutMs);
      c.setReadTimeout(this.timeoutMs);
      if (url.startsWith(this.base + "/")) this.authorize(c);
      int code = c.getResponseCode();
      if (code / 100 != 2) throw failure(c, code);
      try {
        return readAll(c.getInputStream());
      } finally {
        c.disconnect();
      }
    }

    /** The error a stopped workflow reports, or its waiting reason. */
    public static String problem(Map<String, Object> wf) {
      String status = str(wf.get("status"));
      if ("waiting_for_user".equals(status)) {
        String why = str(wf.get("waitingReason"));
        if (Boolean.TRUE.equals(wf.get("awaitingCostApproval")) || "cost_approval_required".equals(why)) {
          return "Sogni wants the cost approved. Run again with --confirm_cost.";
        }
        return "Sogni is waiting for input: " + (why == null ? "unknown reason" : why);
      }
      List<String> why = new ArrayList<String>();
      reasons(wf, why);
      if (!why.isEmpty()) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < why.size() && i < 3; i++) sb.append(i == 0 ? "" : "; ").append(why.get(i));
        return sb.toString();
      }
      return status == null ? "no status" : "workflow " + status;
    }

    /**
     * Error text anywhere in a record or event list: values of error, lastError, failureReason,
     * errorMessage and reason, or the message beside them. The workflow's own "workflow failed"
     * status says nothing, so the failed step's words are what count.
     */
    @SuppressWarnings("unchecked")
    public static void reasons(Object o, List<String> out) {
      if (o instanceof Map) {
        Map<String, Object> m = (Map<String, Object>) o;
        for (Map.Entry<String, Object> e : m.entrySet()) {
          String k = e.getKey().toLowerCase();
          Object v = e.getValue();
          boolean errorKey = k.equals("error") || k.equals("lasterror") || k.equals("failurereason") || k.equals("errormessage")
              || k.equals("reason") || k.equals("failure");
          if (errorKey && v instanceof String) addReason(out, (String) v);
          else if (errorKey && v instanceof Map) {
            Map<String, Object> em = (Map<String, Object>) v;
            String msg = str(em.get("message"));
            String code = str(em.get("code"));
            if (msg == null) msg = str(em.get("errorMessage"));
            if (msg != null) addReason(out, code != null && !msg.contains(code) ? msg + " (" + code + ")" : msg);
          }
        }
        String status = str(m.get("status"));
        if (status != null && (status.contains("fail") || status.contains("error")) && m.get("message") instanceof String) addReason(out, (String) m.get("message"));
        for (Object v : m.values()) if (v instanceof Map || v instanceof List) reasons(v, out);
      } else if (o instanceof List) {
        for (Object v : (List<Object>) o) reasons(v, out);
      }
    }

    static void addReason(List<String> out, String s) {
      String t = s == null ? "" : s.trim();
      if (t.length() == 0 || t.equalsIgnoreCase("workflow failed") || out.contains(t)) return;
      out.add(t);
    }

    // ---- HTTP ----

    Object request(String method, String path, String body) throws IOException {
      HttpURLConnection c = this.open(method, path);
      if (body != null) {
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        OutputStream out = c.getOutputStream();
        try {
          out.write(body.getBytes(StandardCharsets.UTF_8));
        } finally {
          out.close();
        }
      }
      int code = c.getResponseCode();
      if (code / 100 != 2) throw failure(c, code);
      try {
        String text = new String(readAll(c.getInputStream()), StandardCharsets.UTF_8);
        return text.trim().length() == 0 ? new LinkedHashMap<String, Object>() : parseJson(text);
      } finally {
        c.disconnect();
      }
    }

    HttpURLConnection open(String method, String path) throws IOException {
      HttpURLConnection c = (HttpURLConnection) new URL(this.base + path).openConnection();
      c.setRequestMethod(method);
      c.setConnectTimeout(this.timeoutMs);
      c.setReadTimeout(this.timeoutMs);
      c.setRequestProperty("Accept", "application/json");
      this.authorize(c);
      return c;
    }

    void authorize(HttpURLConnection c) {
      c.setRequestProperty("Authorization", "Bearer " + this.apiKey);
      c.setRequestProperty("api-key", this.apiKey);
    }

    static ApiException failure(HttpURLConnection c, int code) {
      String text = "";
      try {
        InputStream err = c.getErrorStream();
        if (err != null) text = new String(readAll(err), StandardCharsets.UTF_8);
      } catch (IOException ignored) {
        // The status alone still says what went wrong.
      }
      String message = null;
      try {
        Object p = parseJson(text);
        if (p instanceof Map) {
          message = str(((Map<?, ?>) p).get("message"));
          Object e = ((Map<?, ?>) p).get("error");
          if (message == null && e instanceof Map) message = str(((Map<?, ?>) e).get("message"));
        }
      } catch (RuntimeException ignored) {
        if (text.trim().length() > 0 && text.length() < 300) message = text.trim();
      }
      int retry = 0;
      try {
        String ra = c.getHeaderField("Retry-After");
        if (ra != null) retry = Integer.parseInt(ra.trim());
      } catch (NumberFormatException ignored) {
        retry = 60;
      }
      String what = code == 401 ? "Sogni refused the API key (401)" : code == 429 ? "Sogni rate limit (429)" : "Sogni API error " + code;
      c.disconnect();
      return new ApiException(message == null ? what : what + ": " + message, code, retry);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> workflowOf(Object payload) throws IOException {
      if (!(payload instanceof Map)) throw new IOException("Unexpected reply from Sogni");
      Map<String, Object> p = (Map<String, Object>) payload;
      Object data = p.get("data");
      if (data instanceof Map && ((Map<String, Object>) data).get("workflow") instanceof Map) return (Map<String, Object>) ((Map<String, Object>) data).get("workflow");
      if (p.get("workflow") instanceof Map) return (Map<String, Object>) p.get("workflow");
      if (data instanceof Map) return (Map<String, Object>) data;
      return p;
    }

    static String enc(String s) {
      try {
        return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
      } catch (java.io.UnsupportedEncodingException e) {
        return s;
      }
    }

    static byte[] readAll(InputStream in) throws IOException {
      try {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
      } finally {
        in.close();
      }
    }

    static void sleep(long ms) throws IOException {
      try {
        Thread.sleep(ms);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted");
      }
    }

    static String str(Object o) {
      return o == null ? null : String.valueOf(o);
    }

    // ---- JSON: just what these calls need ----

    public static String quote(String s) {
      StringBuilder sb = new StringBuilder("\"");
      for (int i = 0; i < s.length(); i++) {
        char ch = s.charAt(i);
        if (ch == '"' || ch == '\\') sb.append('\\').append(ch);
        else if (ch == '\n') sb.append("\\n");
        else if (ch == '\r') sb.append("\\r");
        else if (ch == '\t') sb.append("\\t");
        else if (ch < 0x20) sb.append(String.format("\\u%04x", Integer.valueOf(ch)));
        else sb.append(ch);
      }
      return sb.append('"').toString();
    }

    static String number(double d) {
      return d == Math.rint(d) && Math.abs(d) < 1e15 ? String.valueOf((long) d) : String.valueOf(d);
    }

    @SuppressWarnings("unchecked")
    public static String toJson(Object o) {
      if (o == null) return "null";
      if (o instanceof String) return quote((String) o);
      if (o instanceof Double || o instanceof Float) return number(((Number) o).doubleValue());
      if (o instanceof Number || o instanceof Boolean) return String.valueOf(o);
      StringBuilder sb = new StringBuilder();
      if (o instanceof Map) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) {
          if (!first) sb.append(',');
          first = false;
          sb.append(quote(e.getKey())).append(':').append(toJson(e.getValue()));
        }
        return sb.append('}').toString();
      }
      if (o instanceof List) {
        sb.append('[');
        boolean first = true;
        for (Object v : (List<Object>) o) {
          if (!first) sb.append(',');
          first = false;
          sb.append(toJson(v));
        }
        return sb.append(']').toString();
      }
      return quote(String.valueOf(o));
    }

    /** Maps, lists, strings, doubles, booleans and nulls. Throws IllegalArgumentException on bad JSON. */
    public static Object parseJson(String text) {
      int[] at = {0};
      Object v = value(text, at);
      skip(text, at);
      if (at[0] != text.length()) throw new IllegalArgumentException("Trailing text in JSON at " + at[0]);
      return v;
    }

    static Object value(String s, int[] at) {
      skip(s, at);
      if (at[0] >= s.length()) throw new IllegalArgumentException("JSON ended early");
      char ch = s.charAt(at[0]);
      if (ch == '{') {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        at[0]++;
        skip(s, at);
        if (s.charAt(at[0]) == '}') {
          at[0]++;
          return m;
        }
        while (true) {
          skip(s, at);
          String k = string(s, at);
          skip(s, at);
          expect(s, at, ':');
          m.put(k, value(s, at));
          skip(s, at);
          if (s.charAt(at[0]) == ',') {
            at[0]++;
            continue;
          }
          expect(s, at, '}');
          return m;
        }
      }
      if (ch == '[') {
        List<Object> l = new ArrayList<Object>();
        at[0]++;
        skip(s, at);
        if (s.charAt(at[0]) == ']') {
          at[0]++;
          return l;
        }
        while (true) {
          l.add(value(s, at));
          skip(s, at);
          if (s.charAt(at[0]) == ',') {
            at[0]++;
            continue;
          }
          expect(s, at, ']');
          return l;
        }
      }
      if (ch == '"') return string(s, at);
      if (s.startsWith("true", at[0])) {
        at[0] += 4;
        return Boolean.TRUE;
      }
      if (s.startsWith("false", at[0])) {
        at[0] += 5;
        return Boolean.FALSE;
      }
      if (s.startsWith("null", at[0])) {
        at[0] += 4;
        return null;
      }
      int start = at[0];
      while (at[0] < s.length() && "+-0123456789.eE".indexOf(s.charAt(at[0])) >= 0) at[0]++;
      if (start == at[0]) throw new IllegalArgumentException("Bad JSON at " + start);
      return Double.valueOf(s.substring(start, at[0]));
    }

    static String string(String s, int[] at) {
      expect(s, at, '"');
      StringBuilder sb = new StringBuilder();
      while (at[0] < s.length()) {
        char ch = s.charAt(at[0]++);
        if (ch == '"') return sb.toString();
        if (ch != '\\') {
          sb.append(ch);
          continue;
        }
        char e = s.charAt(at[0]++);
        if (e == 'n') sb.append('\n');
        else if (e == 't') sb.append('\t');
        else if (e == 'r') sb.append('\r');
        else if (e == 'b') sb.append('\b');
        else if (e == 'f') sb.append('\f');
        else if (e == 'u') {
          sb.append((char) Integer.parseInt(s.substring(at[0], at[0] + 4), 16));
          at[0] += 4;
        } else sb.append(e);
      }
      throw new IllegalArgumentException("Unterminated JSON string");
    }

    static void skip(String s, int[] at) {
      while (at[0] < s.length() && Character.isWhitespace(s.charAt(at[0]))) at[0]++;
    }

    static void expect(String s, int[] at, char ch) {
      if (at[0] >= s.length() || s.charAt(at[0]) != ch) throw new IllegalArgumentException("Expected " + ch + " in JSON at " + at[0]);
      at[0]++;
    }
    // --- SogniApi end ---
  }
}
