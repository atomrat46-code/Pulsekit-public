package pulsekit;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Recently run PyJav programs and the extra arguments used with them. */
public final class PyJavRecent {
  private static final int MAX = 8;
  private static final int MAX_BYTES = 400000;

  public static final class Item {
    public final String name;
    public final String extra;
    public final String source;
    public final byte[] bytes;

    public Item(String name, String extra, String source, byte[] bytes) {
      this.name = name == null ? "script.py" : name;
      this.extra = extra == null ? "" : extra;
      this.source = source == null ? "" : source;
      this.bytes = bytes;
    }

    public String label() {
      String args = extra == null ? "" : extra.trim();
      String shown = name == null || name.length() == 0 ? "program" : name;
      return args.isEmpty() ? shown : shown + "  " + args;
    }
  }

  private PyJavRecent() {}

  public static List<Item> load(File dir) {
    List<Item> out = new ArrayList<Item>();
    if (dir == null) return out;
    File file = new File(dir, "pyjav-recent.txt");
    if (!file.isFile()) return out;
    try {
      String text = new String(read(file), StandardCharsets.UTF_8);
      // -1 keeps trailing empty fields: an item without bytes ends in an empty line.
      String[] lines = text.split("\n", -1);
      for (int i = 0; i + 3 < lines.length && out.size() < MAX; i += 4) {
        String name = decode(lines[i]);
        if (name.isEmpty()) continue;
        out.add(new Item(name, decode(lines[i + 1]), decode(lines[i + 2]), b64(lines[i + 3])));
      }
    } catch (Exception ignored) {
      return out;
    }
    return out;
  }

  public static void remember(File dir, String name, String extra, String source, byte[] bytes) {
    if (dir == null || name == null || name.trim().isEmpty()) return;
    if (!dir.isDirectory() && !dir.mkdirs()) return;
    byte[] kept = bytes != null && bytes.length > 0 && bytes.length <= MAX_BYTES ? bytes : null;
    Item fresh = new Item(name, extra, source, kept);
    List<Item> list = new ArrayList<Item>();
    list.add(fresh);
    for (Item old : load(dir)) {
      if (list.size() >= MAX) break;
      if (old.name.equals(fresh.name) && old.extra.equals(fresh.extra)) continue;
      list.add(old);
    }
    StringBuilder text = new StringBuilder();
    for (Item item : list) {
      text.append(encode(item.name)).append('\n');
      text.append(encode(item.extra)).append('\n');
      text.append(encode(item.source)).append('\n');
      text.append(item.bytes == null ? "" : b64(item.bytes)).append('\n');
    }
    try {
      File file = new File(dir, "pyjav-recent.txt");
      FileOutputStream out = new FileOutputStream(file);
      try {
        out.write(text.toString().getBytes(StandardCharsets.UTF_8));
      } finally {
        out.close();
      }
    } catch (Exception ignored) {
      /* recent list is optional */
    }
  }

  private static byte[] read(File file) throws Exception {
    FileInputStream in = new FileInputStream(file);
    try {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      byte[] buf = new byte[4096];
      int n;
      while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
      return bos.toByteArray();
    } finally {
      in.close();
    }
  }

  private static String encode(String text) {
    return b64((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
  }

  private static String decode(String text) {
    byte[] data = b64(text == null ? "" : text.trim());
    return data == null ? "" : new String(data, StandardCharsets.UTF_8);
  }

  private static final char[] ALPH = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

  static String b64(byte[] data) {
    if (data == null || data.length == 0) return "";
    StringBuilder out = new StringBuilder(((data.length + 2) / 3) * 4);
    int i = 0;
    while (i < data.length) {
      int b0 = data[i++] & 255;
      int b1 = i < data.length ? data[i++] & 255 : -1;
      int b2 = i < data.length ? data[i++] & 255 : -1;
      out.append(ALPH[b0 >> 2]);
      out.append(ALPH[((b0 & 3) << 4) | (b1 < 0 ? 0 : (b1 >> 4))]);
      out.append(b1 < 0 ? '=' : ALPH[((b1 & 15) << 2) | (b2 < 0 ? 0 : (b2 >> 6))]);
      out.append(b2 < 0 ? '=' : ALPH[b2 & 63]);
    }
    return out.toString();
  }

  static byte[] b64(String text) {
    if (text == null) return null;
    String s = text.trim();
    if (s.isEmpty()) return null;
    int pads = 0;
    if (s.endsWith("==")) pads = 2;
    else if (s.endsWith("=")) pads = 1;
    int len = s.length() * 3 / 4 - pads;
    byte[] out = new byte[Math.max(0, len)];
    int n = 0;
    int acc = 0;
    int bits = 0;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      int v;
      if (c >= 'A' && c <= 'Z') v = c - 'A';
      else if (c >= 'a' && c <= 'z') v = c - 'a' + 26;
      else if (c >= '0' && c <= '9') v = c - '0' + 52;
      else if (c == '+') v = 62;
      else if (c == '/') v = 63;
      else continue;
      acc = (acc << 6) | v;
      bits += 6;
      if (bits >= 8) {
        bits -= 8;
        if (n < out.length) out[n++] = (byte) ((acc >> bits) & 255);
      }
    }
    return out;
  }
}
