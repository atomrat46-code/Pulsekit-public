/**
 * JoinVideo: up to six MP4 videos as one, without re-encoding: video a, then video b (or b first
 * with --b_first), then videos c to f when given.
 *
 *   java JoinVideo <video_a.mp4> <video_b.mp4> [--video_c c.mp4] [--video_d d.mp4] [--video_e e.mp4] [--video_f f.mp4]
 *       [output.mp4] [--b_first] [--addtodb]
 *
 * --video_c..f  More videos, after a and b, in order.
 * output.mp4  Default: <video a>-merged.mp4 in the work folder (Downloads on the phone); a name in
 *             use gets (1), (2)...
 * --b_first   Video b first, then video a (videos c to f follow).
 * --addtodb   PyJav also keeps the joined video in the prompt library, as a result file.
 *
 * The two pictures must be in the same format (codec, size and codec setup), as two Sogni clips
 * from the same model are; otherwise it says why. Sound is joined when both have it in the same
 * format; otherwise the sound there is stays where it was, with a note. Plain Java 8, so PyJav can
 * compile it on a phone; the joiner is SogniVideo's (the Mp4Join begin/end lines, kept the same).
 */
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JoinVideo {
  private JoinVideo() {}

  /** The work folder this run writes in (PyJav sets pulsekit.work for each run on the phone). */
  static String work;

  public static void main(String[] args) {
    int code = run(args);
    if (code != 0 && System.getProperty("pulsekit.work") == null) System.exit(code);
  }

  static void usage() {
    System.out.println("Usage: java JoinVideo <video_a.mp4> <video_b.mp4> [--video_c c.mp4] [--video_d d.mp4] [--video_e e.mp4] [--video_f f.mp4] "
        + "[output.mp4] [--b_first] [--addtodb]");
  }

  static int run(String[] args) {
    work = System.getProperty("pulsekit.work");
    List<String> files = new ArrayList<String>();
    // Videos c to f, by letter (a later one may be given without an earlier one).
    String[] more = new String[4];
    boolean bFirst = false;
    boolean addToDb = false;
    for (int i = 0; i < args.length; i++) {
      String a = args[i] == null ? "" : args[i].trim();
      if (a.length() == 0) continue;
      if (a.matches("--video_[c-f]")) {
        if (i + 1 >= args.length || args[i + 1] == null || args[i + 1].trim().length() == 0) {
          System.out.println("Failed: " + a + " needs a video");
          return 2;
        }
        more[a.charAt(8) - 'c'] = args[++i].trim();
      } else if (a.equals("--b_first")) bFirst = true;
      else if (a.equals("--addtodb")) addToDb = true;
      else if (a.equals("-h") || a.equals("--help")) {
        usage();
        return 0;
      } else if (a.startsWith("--")) {
        System.out.println("Failed: unknown option " + a);
        usage();
        return 2;
      } else {
        files.add(a);
      }
    }
    if (files.size() < 2) {
      System.out.println("Failed: give two MP4 videos, video a and video b");
      usage();
      return 2;
    }
    if (files.size() > 3) {
      System.out.println("Failed: too many files: video a, video b and an output name at most");
      return 2;
    }
    File a = new File(files.get(0));
    File b = new File(files.get(1));
    List<File> videos = new ArrayList<File>();
    videos.add(bFirst ? b : a);
    videos.add(bFirst ? a : b);
    for (String m : more) if (m != null) videos.add(new File(m));
    for (File f : videos) {
      if (!f.isFile()) {
        System.out.println("Failed: no such file: " + f.getPath());
        return 2;
      }
    }
    String outName = files.size() > 2 ? files.get(2) : stem(a.getName()) + "-merged.mp4";
    if (!outName.toLowerCase().matches(".+\\.(mp4|m4v|mov)")) outName = outName + ".mp4";
    File out = outFile(outName);
    try {
      String note = Mp4Join.joinAll(videos, out);
      StringBuilder order = new StringBuilder();
      for (int i = 0; i < videos.size(); i++) order.append(i == 0 ? "" : i == videos.size() - 1 ? " and then " : ", then ").append(videos.get(i).getName());
      System.out.println("Joined " + order + ": wrote " + out.getName() + " (" + size(out.length()) + ")");
      if (note.length() > 0) System.out.println("Note: " + note);
      if (addToDb) System.out.println("Add to DB: " + out.getName());
      System.out.println("Succeeded: " + out.getName());
      return 0;
    } catch (IOException ex) {
      out.delete();
      System.out.println("Failed: could not join the videos: " + ex.getMessage());
      return 1;
    }
  }

  /** The output file: in the work folder unless a full path is given, never over an existing file. */
  static File outFile(String name) {
    File f = new File(name);
    if (!f.isAbsolute()) {
      String dir = work != null && work.length() > 0 ? work : System.getProperty("user.dir", ".");
      f = new File(dir, f.getName());
    }
    String path = f.getPath();
    int dot = path.lastIndexOf('.');
    String stem = path.substring(0, dot);
    String ext = path.substring(dot);
    for (int n = 1; f.exists(); n++) f = new File(stem + "(" + n + ")" + ext);
    return f;
  }

  static String stem(String name) {
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;
  }

  static String size(long bytes) {
    if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
    return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
  }

  // --- Mp4Join begin ---
  /**
   * Two MP4 videos as one, without re-encoding (SogniVideo's --join, and JoinVideo). Both
   * must be plain MP4s (H.264 or another codec, not fragmented) whose pictures have the same
   * format (codec settings and size): two Sogni clips from the same model do. The sample tables
   * are rebuilt and the samples copied in order, the first video's then the second's. Sound is
   * joined when both have it in the same format; otherwise the sound there is is kept where it was
   * (a note says so). Java 8 without lambdas, for the phone's compiler.
   */
  static final class Mp4Join {
    private Mp4Join() {}

    /** What went wrong, in words for the log. */
    static final class JoinException extends IOException {
      JoinException(String why) {
        super(why);
      }
    }

    /** One sample: where its bytes are, how big, how long (in its track's timescale). */
    static final class Sample {
      int part;
      long offset;
      int size;
      long duration;
      long ctts;
      boolean sync;
    }

    /** A track of one file. */
    static final class Track {
      String handler;
      long timescale;
      byte[] tkhd;
      byte[] mdhd;
      byte[] hdlr;
      byte[] mediaHeader;
      byte[] dinf;
      byte[] stsd;
      long editMediaTime;
      boolean hasCtts;
      boolean hasStss;
      List<Sample> samples = new ArrayList<Sample>();

      long mediaDuration() {
        long d = 0;
        for (Sample s : samples) d += s.duration;
        return d;
      }
    }

    /** One file: its brand box, movie header and tracks. */
    static final class Movie {
      byte[] ftyp;
      byte[] mvhd;
      long timescale;
      Track video;
      Track audio;
    }

    /** Writes `first` then `second` into `dest`; returns a note (sound left out...) or "". */
    static String join(File first, File second, File dest) throws IOException {
      List<File> both = new ArrayList<File>();
      both.add(first);
      both.add(second);
      return joinAll(both, dest);
    }

    /**
     * Writes the videos one after another into `dest`. The sound is joined over the videos that
     * have it in the first one's format, from the first video with sound up to the first one without
     * (or in another format); returns a note about any part left silent, or "".
     */
    static String joinAll(List<File> files, File dest) throws IOException {
      if (files.size() < 2) throw new JoinException("give two videos or more");
      List<Movie> movies = new ArrayList<Movie>();
      for (int i = 0; i < files.size(); i++) {
        Movie m = read(files.get(i), i);
        if (m.video == null) throw new JoinException(files.get(i).getName() + " has no picture");
        if (!movies.isEmpty() && !sameEntry(movies.get(0).video.stsd, m.video.stsd)) {
          throw new JoinException("the pictures are in different formats (" + describe(movies.get(0).video.stsd) + " in " + files.get(0).getName()
              + " and " + describe(m.video.stsd) + " in " + files.get(i).getName() + "), so they cannot be joined without re-encoding");
        }
        movies.add(m);
      }
      Movie a = movies.get(0);
      Track video = a.video;
      for (int i = 1; i < movies.size(); i++) video = combine(video, movies.get(i).video);
      // The sound: one run of videos with sound in the same format, from the first that has sound.
      int start = -1;
      for (int i = 0; i < movies.size() && start < 0; i++) if (movies.get(i).audio != null) start = i;
      Track audio = null;
      long lead = 0;
      List<String> silent = new ArrayList<String>();
      if (start >= 0) {
        int end = start;
        while (end + 1 < movies.size() && movies.get(end + 1).audio != null && sameEntry(movies.get(start).audio.stsd, movies.get(end + 1).audio.stsd)) end++;
        audio = movies.get(start).audio;
        for (int i = start + 1; i <= end; i++) audio = combine(audio, movies.get(i).audio);
        for (int i = 0; i < start; i++) {
          lead += movies.get(i).video.mediaDuration() * a.timescale / Math.max(1, movies.get(i).video.timescale);
          silent.add(files.get(i).getName());
        }
        for (int i = end + 1; i < movies.size(); i++) silent.add(files.get(i).getName());
      }
      String note = "";
      if (start < 0) note = "none of the videos has sound";
      else if (!silent.isEmpty()) {
        StringBuilder sb = new StringBuilder();
        for (String n : silent) sb.append(sb.length() > 0 ? ", " : "").append(n);
        note = (silent.size() == 1 ? "the part from " : "the parts from ") + sb + " " + (silent.size() == 1 ? "is" : "are")
            + " silent (no sound, or sound in another format, or after one of those)";
      }
      List<Track> tracks = new ArrayList<Track>();
      tracks.add(video);
      if (audio != null) tracks.add(audio);
      long[] leads = new long[] {0, lead};
      write(dest, a, tracks, leads, files.toArray(new File[0]));
      return note;
    }

    /** The second track's samples after the first's, in the first's timescale. */
    static Track combine(Track x, Track y) {
      Track t = new Track();
      t.handler = x.handler;
      t.timescale = x.timescale;
      t.tkhd = x.tkhd;
      t.mdhd = x.mdhd;
      t.hdlr = x.hdlr;
      t.mediaHeader = x.mediaHeader;
      t.dinf = x.dinf;
      t.stsd = x.stsd;
      t.editMediaTime = x.editMediaTime;
      t.hasCtts = x.hasCtts || y.hasCtts;
      t.hasStss = x.hasStss || y.hasStss;
      t.samples.addAll(x.samples);
      // Durations rescaled with the rounding carried along, so the total stays exact.
      long cum = 0;
      long done = 0;
      for (Sample s : y.samples) {
        Sample c = new Sample();
        c.part = s.part;
        c.offset = s.offset;
        c.size = s.size;
        c.sync = s.sync;
        cum += s.duration;
        long end = Math.round(cum * (double) x.timescale / y.timescale);
        c.duration = end - done;
        done = end;
        c.ctts = Math.round(s.ctts * (double) x.timescale / y.timescale);
        t.samples.add(c);
      }
      return t;
    }

    /**
     * Whether samples described by `q` decode with description `p`: the same codec, picture size or
     * sound settings, and codec setup (avcC, hvcC..., or the AAC config in esds). Bitrate notes (btrt,
     * the esds bitrates) and the encoder's name may differ.
     */
    static boolean sameEntry(byte[] p, byte[] q) {
      if (Arrays.equals(p, q)) return true;
      if (p == null || q == null || p.length < 24 || q.length < 24) return false;
      if (!new String(p, 20, 4, StandardCharsets.ISO_8859_1).equals(new String(q, 20, 4, StandardCharsets.ISO_8859_1))) return false;
      boolean video = describe(p).indexOf('x') > 0 && !new String(p, 20, 4, StandardCharsets.ISO_8859_1).equals("mp4a");
      // The fixed part of the entry after its size and type: 78 bytes for a picture, 28 for sound.
      int fixed = video ? 78 : 28;
      int body = 24 + fixed;
      if (p.length < body || q.length < body) return false;
      if (video) {
        for (int i = 48; i < 52; i++) if (p[i] != q[i]) return false;
      } else {
        for (int i = 24; i < body; i++) if (p[i] != q[i]) return false;
      }
      Map<String, byte[]> a = entryBoxes(p, body);
      Map<String, byte[]> b = entryBoxes(q, body);
      if (a == null || b == null) return false;
      String[] setup = {"avcC", "hvcC", "av1C", "vpcC", "dOps", "dac3", "dec3", "alac", "dfLa"};
      for (String k : setup) if (!Arrays.equals(a.get(k), b.get(k))) return false;
      if (a.containsKey("esds") || b.containsKey("esds")) {
        if (!Arrays.equals(esdsConfig(a.get("esds")), esdsConfig(b.get("esds")))) return false;
      }
      return true;
    }

    /** The boxes inside the first sample entry, by type, from `at` to the entry's end; null when damaged. */
    static Map<String, byte[]> entryBoxes(byte[] stsd, int at) {
      Map<String, byte[]> out = new LinkedHashMap<String, byte[]>();
      int end = Math.min(stsd.length, 16 + (int) u32(stsd, 16));
      try {
        for (int[] c : children(stsd, at, end)) out.put(new String(stsd, c[0] + 4, 4, StandardCharsets.ISO_8859_1), Arrays.copyOfRange(stsd, c[0], c[1]));
      } catch (JoinException ex) {
        return null;
      }
      return out;
    }

    /** From an esds box: the codec kind and its decoder setup (DecoderSpecificInfo), without the bitrates. */
    static byte[] esdsConfig(byte[] esds) {
      if (esds == null) return null;
      try {
        int[] pos = {12};
        if (esds[pos[0]++] != 0x03) return esds;
        descriptorLength(esds, pos);
        int flags = esds[pos[0] + 2] & 0xff;
        pos[0] += 3;
        if ((flags & 0x80) != 0) pos[0] += 2;
        if ((flags & 0x40) != 0) pos[0] += 1 + (esds[pos[0]] & 0xff);
        if ((flags & 0x20) != 0) pos[0] += 2;
        if (esds[pos[0]++] != 0x04) return esds;
        descriptorLength(esds, pos);
        byte kind = esds[pos[0]];
        pos[0] += 13;
        if (esds[pos[0]++] != 0x05) return new byte[] {kind};
        int len = descriptorLength(esds, pos);
        byte[] out = new byte[1 + len];
        out[0] = kind;
        System.arraycopy(esds, pos[0], out, 1, len);
        return out;
      } catch (ArrayIndexOutOfBoundsException ex) {
        return esds;
      }
    }

    /** An MPEG-4 descriptor's length: up to four bytes of seven bits each. */
    static int descriptorLength(byte[] d, int[] pos) {
      int len = 0;
      for (int i = 0; i < 4; i++) {
        int b = d[pos[0]++] & 0xff;
        len = (len << 7) | (b & 0x7f);
        if ((b & 0x80) == 0) break;
      }
      return len;
    }

    /** "avc1 768x1152" from a sample description (stsd payload). */
    static String describe(byte[] stsd) {
      // The box header (8), version and entry count (8), then the first entry: size, type, and for
      // a picture its width and height 24 bytes further on.
      if (stsd == null || stsd.length < 24) return "unknown";
      String type = new String(stsd, 20, 4, StandardCharsets.ISO_8859_1);
      if (stsd.length >= 52) {
        int w = ((stsd[48] & 0xff) << 8) | (stsd[49] & 0xff);
        int h = ((stsd[50] & 0xff) << 8) | (stsd[51] & 0xff);
        if (w > 0 && h > 0) return type + " " + w + "x" + h;
      }
      return type;
    }

    // ---- reading

    static Movie read(File f, int part) throws IOException {
      RandomAccessFile in = new RandomAccessFile(f, "r");
      try {
        Movie m = new Movie();
        long pos = 0;
        long len = in.length();
        byte[] moov = null;
        while (pos + 8 <= len) {
          in.seek(pos);
          long size = in.readInt() & 0xffffffffL;
          String type = fourcc(in.readInt());
          long head = 8;
          if (size == 1) {
            size = in.readLong();
            head = 16;
          } else if (size == 0) {
            size = len - pos;
          }
          if (size < head || pos + size > len) throw new JoinException(f.getName() + " is not a complete MP4 file");
          if (type.equals("ftyp")) m.ftyp = readBox(in, pos, size);
          else if (type.equals("moov")) moov = readBox(in, pos, size);
          else if (type.equals("moof")) throw new JoinException(f.getName() + " is a fragmented MP4, which cannot be joined here");
          pos += size;
        }
        if (moov == null) throw new JoinException(f.getName() + " is not an MP4 video");
        for (int[] box : children(moov, 8, moov.length)) {
          String type = new String(moov, box[0] + 4, 4, StandardCharsets.ISO_8859_1);
          if (type.equals("mvhd")) {
            m.mvhd = Arrays.copyOfRange(moov, box[0], box[1]);
            m.timescale = u32(m.mvhd, m.mvhd[8] == 1 ? 8 + 4 + 16 : 8 + 4 + 8);
          } else if (type.equals("trak")) {
            Track t = track(moov, box[0], box[1], part, f.getName());
            if (t == null) continue;
            if ("vide".equals(t.handler) && m.video == null) m.video = t;
            else if ("soun".equals(t.handler) && m.audio == null) m.audio = t;
          }
        }
        if (m.mvhd == null) throw new JoinException(f.getName() + " has no movie header");
        if (m.ftyp == null) throw new JoinException(f.getName() + " is not an MP4 video (no file type)");
        return m;
      } finally {
        in.close();
      }
    }

    static byte[] readBox(RandomAccessFile in, long pos, long size) throws IOException {
      if (size > 64L * 1024 * 1024) throw new JoinException("an MP4 header is too large");
      byte[] b = new byte[(int) size];
      in.seek(pos);
      in.readFully(b);
      return b;
    }

    /** The boxes inside [from, to): {start, end} each. */
    static List<int[]> children(byte[] d, int from, int to) throws JoinException {
      List<int[]> out = new ArrayList<int[]>();
      int pos = from;
      while (pos + 8 <= to) {
        long size = u32(d, pos);
        if (size == 1) size = u64(d, pos + 8);
        else if (size == 0) size = to - pos;
        if (size < 8 || pos + size > to) throw new JoinException("an MP4 header is damaged");
        out.add(new int[] {pos, (int) (pos + size)});
        pos += (int) size;
      }
      return out;
    }

    static int[] child(byte[] d, int from, int to, String type) throws JoinException {
      for (int[] c : children(d, from, to)) if (new String(d, c[0] + 4, 4, StandardCharsets.ISO_8859_1).equals(type)) return c;
      return null;
    }

    /** A track's tables, its samples in order; null for a track that is neither picture nor sound. */
    static Track track(byte[] d, int start, int end, int part, String name) throws JoinException {
      Track t = new Track();
      int[] tkhd = child(d, start + 8, end, "tkhd");
      int[] mdia = child(d, start + 8, end, "mdia");
      if (tkhd == null || mdia == null) return null;
      t.tkhd = Arrays.copyOfRange(d, tkhd[0], tkhd[1]);
      int[] edts = child(d, start + 8, end, "edts");
      if (edts != null) {
        int[] elst = child(d, edts[0] + 8, edts[1], "elst");
        if (elst != null) {
          boolean v1 = d[elst[0] + 8] == 1;
          long count = u32(d, elst[0] + 12);
          // The first edit that shows media: where the track's media starts (skips encoder delay).
          for (int i = 0, at = elst[0] + 16; i < count && at < elst[1]; i++) {
            long mediaTime = v1 ? u64(d, at + 8) : (long) (int) u32(d, at + 4);
            at += v1 ? 20 : 12;
            if (mediaTime >= 0) {
              t.editMediaTime = mediaTime;
              break;
            }
          }
        }
      }
      int[] mdhd = child(d, mdia[0] + 8, mdia[1], "mdhd");
      int[] hdlr = child(d, mdia[0] + 8, mdia[1], "hdlr");
      int[] minf = child(d, mdia[0] + 8, mdia[1], "minf");
      if (mdhd == null || hdlr == null || minf == null) return null;
      t.mdhd = Arrays.copyOfRange(d, mdhd[0], mdhd[1]);
      t.hdlr = Arrays.copyOfRange(d, hdlr[0], hdlr[1]);
      t.handler = new String(d, hdlr[0] + 16, 4, StandardCharsets.ISO_8859_1);
      if (!t.handler.equals("vide") && !t.handler.equals("soun")) return null;
      t.timescale = u32(t.mdhd, t.mdhd[8] == 1 ? 8 + 4 + 16 : 8 + 4 + 8);
      int[] head = child(d, minf[0] + 8, minf[1], t.handler.equals("vide") ? "vmhd" : "smhd");
      int[] dinf = child(d, minf[0] + 8, minf[1], "dinf");
      int[] stbl = child(d, minf[0] + 8, minf[1], "stbl");
      if (head == null || dinf == null || stbl == null) throw new JoinException(name + " has an incomplete track");
      t.mediaHeader = Arrays.copyOfRange(d, head[0], head[1]);
      t.dinf = Arrays.copyOfRange(d, dinf[0], dinf[1]);
      int[] stsd = child(d, stbl[0] + 8, stbl[1], "stsd");
      int[] stts = child(d, stbl[0] + 8, stbl[1], "stts");
      int[] ctts = child(d, stbl[0] + 8, stbl[1], "ctts");
      int[] stss = child(d, stbl[0] + 8, stbl[1], "stss");
      int[] stsc = child(d, stbl[0] + 8, stbl[1], "stsc");
      int[] stsz = child(d, stbl[0] + 8, stbl[1], "stsz");
      int[] stco = child(d, stbl[0] + 8, stbl[1], "stco");
      int[] co64 = child(d, stbl[0] + 8, stbl[1], "co64");
      if (stsd == null || stts == null || stsc == null || stsz == null || (stco == null && co64 == null)) {
        throw new JoinException(name + " has an incomplete sample table");
      }
      t.stsd = Arrays.copyOfRange(d, stsd[0], stsd[1]);
      if (u32(d, stsd[0] + 12) != 1) throw new JoinException(name + " has more than one picture or sound format in a track");
      // Sizes.
      long uniform = u32(d, stsz[0] + 12);
      int n = (int) u32(d, stsz[0] + 16);
      for (int i = 0; i < n; i++) {
        Sample s = new Sample();
        s.part = part;
        s.size = (int) (uniform != 0 ? uniform : u32(d, stsz[0] + 20 + 4 * i));
        s.sync = true;
        t.samples.add(s);
      }
      // Durations.
      int k = 0;
      long entries = u32(d, stts[0] + 12);
      for (int e = 0; e < entries; e++) {
        long count = u32(d, stts[0] + 16 + 8 * e);
        long delta = u32(d, stts[0] + 20 + 8 * e);
        for (long c = 0; c < count && k < n; c++) t.samples.get(k++).duration = delta;
      }
      // Composition offsets.
      if (ctts != null) {
        t.hasCtts = true;
        k = 0;
        entries = u32(d, ctts[0] + 12);
        for (int e = 0; e < entries; e++) {
          long count = u32(d, ctts[0] + 16 + 8 * e);
          long off = (int) u32(d, ctts[0] + 20 + 8 * e);
          for (long c = 0; c < count && k < n; c++) t.samples.get(k++).ctts = off;
        }
      }
      // Key frames (all are, without stss).
      if (stss != null) {
        t.hasStss = true;
        for (Sample s : t.samples) s.sync = false;
        entries = u32(d, stss[0] + 12);
        for (int e = 0; e < entries; e++) {
          long number = u32(d, stss[0] + 16 + 4 * e);
          if (number >= 1 && number <= n) t.samples.get((int) number - 1).sync = true;
        }
      }
      // Where each sample is: chunk offsets, samples per chunk.
      boolean wide = stco == null;
      int[] co = wide ? co64 : stco;
      long chunks = u32(d, co[0] + 12);
      long scEntries = u32(d, stsc[0] + 12);
      k = 0;
      for (int e = 0; e < scEntries; e++) {
        long firstChunk = u32(d, stsc[0] + 16 + 12 * e);
        long perChunk = u32(d, stsc[0] + 20 + 12 * e);
        long desc = u32(d, stsc[0] + 24 + 12 * e);
        if (desc != 1) throw new JoinException(name + " has more than one sample format");
        long lastChunk = e + 1 < scEntries ? u32(d, stsc[0] + 16 + 12 * (e + 1)) - 1 : chunks;
        for (long c = firstChunk; c <= lastChunk; c++) {
          long off = wide ? u64(d, co[0] + 16 + 8 * (int) (c - 1)) : u32(d, co[0] + 16 + 4 * (int) (c - 1));
          for (long i = 0; i < perChunk && k < n; i++) {
            Sample s = t.samples.get(k++);
            s.offset = off;
            off += s.size;
          }
        }
      }
      if (k < n) throw new JoinException(name + "'s sample table is incomplete");
      return t;
    }

    // ---- writing

    /** A run of samples of one track, laid out together in the media data. */
    static final class Chunk {
      Track track;
      int from;
      int to;
      double start;
      long offset;
    }

    static void write(File dest, Movie a, List<Track> tracks, long[] leads, File[] parts) throws IOException {
      // Chunks of about half a second, interleaved by time.
      List<Chunk> chunks = new ArrayList<Chunk>();
      for (int ti = 0; ti < tracks.size(); ti++) {
        Track t = tracks.get(ti);
        double lead = ti == 0 ? 0 : leads[1] / (double) Math.max(1, a.timescale);
        long time = 0;
        int i = 0;
        while (i < t.samples.size()) {
          Chunk c = new Chunk();
          c.track = t;
          c.from = i;
          c.start = lead + time / (double) t.timescale;
          long limit = time + t.timescale / 2;
          int part = t.samples.get(i).part;
          while (i < t.samples.size() && (i == c.from || time < limit) && t.samples.get(i).part == part) {
            time += t.samples.get(i).duration;
            i++;
          }
          c.to = i;
          chunks.add(c);
        }
      }
      Collections.sort(chunks, new java.util.Comparator<Chunk>() {
        public int compare(Chunk x, Chunk y) {
          int c = Double.compare(x.start, y.start);
          return c != 0 ? c : (x.track.handler.equals("vide") ? -1 : 1) - (y.track.handler.equals("vide") ? -1 : 1);
        }
      });
      long mdatSize = 0;
      for (Chunk c : chunks) for (int i = c.from; i < c.to; i++) mdatSize += c.track.samples.get(i).size;
      boolean big = mdatSize > 0xffffffffL - 16;
      // Twice: the movie box's size sets where the media data starts, which sets the chunk offsets.
      byte[] moov = moov(a, tracks, leads, chunks, big);
      long dataStart = a.ftyp.length + moov.length + (big ? 16 : 8);
      long at = dataStart;
      for (Chunk c : chunks) {
        c.offset = at;
        for (int i = c.from; i < c.to; i++) at += c.track.samples.get(i).size;
      }
      moov = moov(a, tracks, leads, chunks, big);
      if (a.ftyp.length + moov.length + (big ? 16 : 8) != dataStart) throw new JoinException("could not lay out the joined file");
      RandomAccessFile[] in = new RandomAccessFile[parts.length];
      DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(new FileOutputStream(dest), 1 << 16));
      boolean done = false;
      try {
        for (int i = 0; i < parts.length; i++) in[i] = new RandomAccessFile(parts[i], "r");
        out.write(a.ftyp);
        out.write(moov);
        if (big) {
          out.writeInt(1);
          out.writeBytes("mdat");
          out.writeLong(mdatSize + 16);
        } else {
          out.writeInt((int) (mdatSize + 8));
          out.writeBytes("mdat");
        }
        byte[] buf = new byte[1 << 16];
        for (Chunk c : chunks) {
          for (int i = c.from; i < c.to; i++) {
            Sample s = c.track.samples.get(i);
            RandomAccessFile src = in[s.part];
            src.seek(s.offset);
            int left = s.size;
            while (left > 0) {
              int n = src.read(buf, 0, Math.min(buf.length, left));
              if (n <= 0) throw new JoinException("a video ends before its last frame");
              out.write(buf, 0, n);
              left -= n;
            }
          }
        }
        out.flush();
        done = true;
      } finally {
        for (RandomAccessFile r : in) if (r != null) r.close();
        out.close();
        if (!done) dest.delete();
      }
    }

    static byte[] moov(Movie a, List<Track> tracks, long[] leads, List<Chunk> chunks, boolean big) throws IOException {
      java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
      long movieDuration = 0;
      List<byte[]> traks = new ArrayList<byte[]>();
      for (int ti = 0; ti < tracks.size(); ti++) {
        Track t = tracks.get(ti);
        long lead = ti == 0 ? 0 : leads[1];
        long media = t.mediaDuration();
        long shown = Math.max(0, media - t.editMediaTime);
        long inMovie = Math.round(shown * (double) a.timescale / t.timescale);
        movieDuration = Math.max(movieDuration, lead + inMovie);
        traks.add(trak(t, ti + 1, lead, inMovie, a.timescale, chunks, big));
      }
      body.write(withDuration(a.mvhd, movieDuration, true, tracks.size() + 1));
      for (byte[] b : traks) body.write(b);
      return box("moov", body.toByteArray());
    }

    static byte[] trak(Track t, int id, long lead, long inMovie, long movieScale, List<Chunk> chunks, boolean big) throws IOException {
      java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
      byte[] tkhd = t.tkhd.clone();
      boolean v1 = tkhd[8] == 1;
      put32(tkhd, v1 ? 8 + 4 + 16 : 8 + 4 + 8, id);
      if (v1) put64(tkhd, 8 + 4 + 16 + 8, lead + inMovie);
      else put32(tkhd, 8 + 4 + 8 + 8, lead + inMovie);
      b.write(tkhd);
      // The edit list: an empty edit first when the track starts later, then the media from where it shows.
      java.io.ByteArrayOutputStream el = new java.io.ByteArrayOutputStream();
      DataOutputStream e = new DataOutputStream(el);
      e.writeInt(0x01000000);
      e.writeInt(lead > 0 ? 2 : 1);
      if (lead > 0) {
        e.writeLong(lead);
        e.writeLong(-1);
        e.writeInt(0x00010000);
      }
      e.writeLong(inMovie);
      e.writeLong(t.editMediaTime);
      e.writeInt(0x00010000);
      b.write(box("edts", box("elst", el.toByteArray())));
      java.io.ByteArrayOutputStream mdia = new java.io.ByteArrayOutputStream();
      mdia.write(withDuration(t.mdhd, t.mediaDuration(), false, 0));
      mdia.write(t.hdlr);
      java.io.ByteArrayOutputStream minf = new java.io.ByteArrayOutputStream();
      minf.write(t.mediaHeader);
      minf.write(t.dinf);
      minf.write(stbl(t, chunks, big));
      mdia.write(box("minf", minf.toByteArray()));
      b.write(box("mdia", mdia.toByteArray()));
      return box("trak", b.toByteArray());
    }

    static byte[] stbl(Track t, List<Chunk> chunks, boolean big) throws IOException {
      java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
      b.write(t.stsd);
      List<Sample> s = t.samples;
      // Durations, run-length.
      java.io.ByteArrayOutputStream x = new java.io.ByteArrayOutputStream();
      DataOutputStream d = new DataOutputStream(x);
      List<long[]> runs = new ArrayList<long[]>();
      for (Sample one : s) {
        if (!runs.isEmpty() && runs.get(runs.size() - 1)[1] == one.duration) runs.get(runs.size() - 1)[0]++;
        else runs.add(new long[] {1, one.duration});
      }
      d.writeInt(0);
      d.writeInt(runs.size());
      for (long[] r : runs) {
        d.writeInt((int) r[0]);
        d.writeInt((int) r[1]);
      }
      b.write(box("stts", x.toByteArray()));
      if (t.hasCtts) {
        x = new java.io.ByteArrayOutputStream();
        d = new DataOutputStream(x);
        runs = new ArrayList<long[]>();
        boolean negative = false;
        for (Sample one : s) {
          if (one.ctts < 0) negative = true;
          if (!runs.isEmpty() && runs.get(runs.size() - 1)[1] == one.ctts) runs.get(runs.size() - 1)[0]++;
          else runs.add(new long[] {1, one.ctts});
        }
        d.writeInt(negative ? 0x01000000 : 0);
        d.writeInt(runs.size());
        for (long[] r : runs) {
          d.writeInt((int) r[0]);
          d.writeInt((int) r[1]);
        }
        b.write(box("ctts", x.toByteArray()));
      }
      if (t.hasStss) {
        x = new java.io.ByteArrayOutputStream();
        d = new DataOutputStream(x);
        List<Integer> keys = new ArrayList<Integer>();
        for (int i = 0; i < s.size(); i++) if (s.get(i).sync) keys.add(Integer.valueOf(i + 1));
        d.writeInt(0);
        d.writeInt(keys.size());
        for (Integer k : keys) d.writeInt(k.intValue());
        b.write(box("stss", x.toByteArray()));
      }
      // Chunks of this track, in file order.
      List<Chunk> mine = new ArrayList<Chunk>();
      for (Chunk c : chunks) if (c.track == t) mine.add(c);
      x = new java.io.ByteArrayOutputStream();
      d = new DataOutputStream(x);
      List<long[]> sc = new ArrayList<long[]>();
      for (int i = 0; i < mine.size(); i++) {
        long per = mine.get(i).to - mine.get(i).from;
        if (sc.isEmpty() || sc.get(sc.size() - 1)[1] != per) sc.add(new long[] {i + 1, per});
      }
      d.writeInt(0);
      d.writeInt(sc.size());
      for (long[] r : sc) {
        d.writeInt((int) r[0]);
        d.writeInt((int) r[1]);
        d.writeInt(1);
      }
      b.write(box("stsc", x.toByteArray()));
      x = new java.io.ByteArrayOutputStream();
      d = new DataOutputStream(x);
      d.writeInt(0);
      d.writeInt(0);
      d.writeInt(s.size());
      for (Sample one : s) d.writeInt(one.size);
      b.write(box("stsz", x.toByteArray()));
      x = new java.io.ByteArrayOutputStream();
      d = new DataOutputStream(x);
      d.writeInt(0);
      d.writeInt(mine.size());
      for (Chunk c : mine) {
        if (big) d.writeLong(c.offset);
        else d.writeInt((int) c.offset);
      }
      b.write(box(big ? "co64" : "stco", x.toByteArray()));
      return box("stbl", b.toByteArray());
    }

    /** A copy of a movie or media header with its duration (and, for the movie, the next track id). */
    static byte[] withDuration(byte[] header, long duration, boolean movie, int nextTrack) {
      byte[] h = header.clone();
      boolean v1 = h[8] == 1;
      int at = v1 ? 8 + 4 + 16 + 4 : 8 + 4 + 8 + 4;
      if (v1) put64(h, at, duration);
      else put32(h, at, Math.min(duration, 0xffffffffL));
      if (movie) put32(h, h.length - 4, nextTrack);
      return h;
    }

    static byte[] box(String type, byte[] payload) {
      byte[] b = new byte[8 + payload.length];
      put32(b, 0, b.length);
      for (int i = 0; i < 4; i++) b[4 + i] = (byte) type.charAt(i);
      System.arraycopy(payload, 0, b, 8, payload.length);
      return b;
    }

    static long u32(byte[] d, int at) {
      return ((d[at] & 0xffL) << 24) | ((d[at + 1] & 0xffL) << 16) | ((d[at + 2] & 0xffL) << 8) | (d[at + 3] & 0xffL);
    }

    static long u64(byte[] d, int at) {
      return (u32(d, at) << 32) | u32(d, at + 4);
    }

    static void put32(byte[] d, int at, long v) {
      d[at] = (byte) (v >>> 24);
      d[at + 1] = (byte) (v >>> 16);
      d[at + 2] = (byte) (v >>> 8);
      d[at + 3] = (byte) v;
    }

    static void put64(byte[] d, int at, long v) {
      put32(d, at, v >>> 32);
      put32(d, at + 4, v);
    }

    static String fourcc(int v) {
      return new String(new byte[] {(byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v}, StandardCharsets.ISO_8859_1);
    }
  }
  // --- Mp4Join end ---
}
