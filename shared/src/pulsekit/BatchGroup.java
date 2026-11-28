package pulsekit;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Params' Batch group (the Sogni picture and video programs, DownloadMove.BATCH_GROUP): a batch's files
 * (Run sections, an input folder's runs, SogniChat's Auto render) put in that group by themselves, and %g in
 * the output name is it. The group's % codes are filled in once, at the batch's first run (%n: the first number
 * no group has yet); a single run does not use it.
 */
public final class BatchGroup {
  private BatchGroup() {}

  /** The group's name with its % codes filled in (the groups kept in `dir`); "" when none is given. */
  public static String resolve(File dir, String pattern) {
    String p = FileGroups.clean(pattern);
    if (p.isEmpty()) return "";
    FileGroups.load(dir);
    final java.util.Set<String> have = new java.util.HashSet<String>();
    for (String g : FileGroups.names()) have.add(g.toLowerCase(Locale.ROOT));
    String name = p.indexOf('%') < 0 ? p : DownloadMove.expand(p, java.util.Calendar.getInstance(), n -> have.contains(n.toLowerCase(Locale.ROOT)));
    return FileGroups.clean(name.replaceAll("[/\\\\]", "-"));
  }

  /** The run's arguments with %g (and %g%) in its output name filled in with the group. */
  public static List<String> withGroup(List<ProgramParams.Param> ps, List<String> argv, String group) {
    if (argv == null || group == null || group.isEmpty()) return argv;
    String out = ChatFolder.outputName(ps, argv);
    if (out == null || out.indexOf("%g") < 0) return argv;
    List<String> made = new ArrayList<String>(argv);
    int at = made.indexOf(out);
    if (at >= 0) made.set(at, DownloadMove.fillChoices(out, TextReady.choices(group)));
    return made;
  }

  /** The run's files ({name, size}) put in the group (made when missing); how many went in. */
  public static int add(File dir, String group, List<Object[]> files) {
    if (group == null || group.isEmpty() || files == null) return 0;
    FileGroups.load(dir);
    int n = 0;
    for (Object[] f : files) {
      String name = (String) f[0];
      long size = ((Number) f[1]).longValue();
      if (name == null || !name.toLowerCase(Locale.ROOT).matches(".+\\.(png|jpe?g|webp|gif|mp4|m4v|webm|mov|mp3|wav|flac)")) continue;
      FileGroups.set(group, MarkFile.groupKeys(null, name, size)[0], true);
      n++;
    }
    return n;
  }

  /** The log's line: "Batch group Moods: 2 files put in it". */
  public static String said(String group, int n) {
    return "Batch group " + group + ": " + n + " file" + (n == 1 ? "" : "s") + " put in it";
  }
}
