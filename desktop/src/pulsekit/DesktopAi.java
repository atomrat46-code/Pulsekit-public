package pulsekit;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Desktop Grok, Sogni, and Claude. An API key or an installed app counts as present. */
public final class DesktopAi {
  public static final class Choice {
    public final String name;
    public final String kind;
    public final String target;

    public Choice(String name, String kind, String target) {
      this.name = name;
      this.kind = kind;
      this.target = target;
    }
  }

  public static final class Found {
    public boolean grok;
    public boolean sogni;
    public boolean claude;
    public final List<Choice> choices = new ArrayList<Choice>();
  }

  private DesktopAi() {}

  public static Found present() {
    Found found = new Found();
    String key = System.getenv("XAI_API_KEY");
    if (key != null && key.trim().length() > 0) {
      found.choices.add(new Choice("Grok", "api", "grok"));
    } else {
      String grok = installed("Grok", new String[] {"Grok"}, new String[] {"grok"}, new String[][] {
        {"LOCALAPPDATA", "Programs/Grok/Grok.exe"}
      });
      if (grok != null) found.choices.add(parse(grok));
    }
    String sogni = installed("Sogni Chat", new String[] {"Sogni Chat"}, new String[] {"sogni-chat"}, new String[][] {
      {"LOCALAPPDATA", "Programs/Sogni Chat/Sogni Chat.exe"}
    });
    if (sogni != null) found.choices.add(parse(sogni));
    else found.choices.add(new Choice("Sogni Chat", "url", "https://chat.sogni.ai"));
    String claude = installed("Claude", new String[] {"Claude"}, new String[] {"claude"}, new String[][] {
      {"LOCALAPPDATA", "AnthropicClaude/claude.exe"},
      {"LOCALAPPDATA", "Programs/Claude/Claude.exe"}
    });
    if (claude != null) found.choices.add(parse(claude));
    for (int i = 0; i < found.choices.size(); i++) {
      String name = found.choices.get(i).name;
      if ("Grok".equals(name)) found.grok = true;
      if (name != null && name.startsWith("Sogni")) found.sogni = true;
      if ("Claude".equals(name)) found.claude = true;
    }
    return found;
  }

  /** Opens the app, or returns null when Grok should be called through the API. */
  public static String launch(Choice choice, String prompt) {
    if (choice == null) return "AI prompt. PyJav cannot run this. Grok, Sogni, and Claude are not present.";
    if ("api".equals(choice.kind)) return null;
    copy(prompt);
    try {
      if ("mac".equals(choice.kind)) {
        new ProcessBuilder("open", "-a", choice.target).start();
      } else if ("gtk".equals(choice.kind)) {
        new ProcessBuilder("gtk-launch", choice.target).start();
      } else if ("url".equals(choice.kind)) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) new ProcessBuilder("open", choice.target).start();
        else if (os.contains("win")) new ProcessBuilder("cmd", "/c", "start", "", choice.target).start();
        else new ProcessBuilder("xdg-open", choice.target).start();
      } else {
        new ProcessBuilder(choice.target).start();
      }
      return "Opened " + choice.name + ". The prompt is on the clipboard.";
    } catch (Exception ex) {
      return choice.name + " is installed, but PyJav could not open it.";
    }
  }

  private static Choice parse(String packed) {
    int a = packed.indexOf('\n');
    int b = packed.indexOf('\n', a + 1);
    return new Choice(packed.substring(0, a), packed.substring(a + 1, b), packed.substring(b + 1));
  }

  private static String installed(String name, String[] macApps, String[] commands, String[][] winRel) {
    String os = System.getProperty("os.name", "").toLowerCase();
    if (os.contains("mac")) {
      String home = System.getProperty("user.home", "");
      for (int i = 0; i < macApps.length; i++) {
        if (new File("/Applications/" + macApps[i] + ".app").isDirectory()) return name + "\nmac\n" + macApps[i];
        if (home.length() > 0 && new File(home + "/Applications/" + macApps[i] + ".app").isDirectory()) return name + "\nmac\n" + macApps[i];
      }
    }
    if (os.contains("win")) {
      for (int i = 0; i < winRel.length; i++) {
        String root = System.getenv(winRel[i][0]);
        if (root == null || root.length() == 0) continue;
        File file = new File(root, winRel[i][1].replace('/', File.separatorChar));
        if (file.isFile()) return name + "\npath\n" + file.getAbsolutePath();
      }
    }
    String cmd = command(commands);
    if (cmd != null) return name + "\ncommand\n" + cmd;
    if (!os.contains("mac") && !os.contains("win")) {
      String desktop = linuxDesktop(name);
      if (desktop != null) return name + "\ngtk\n" + desktop.substring(desktop.indexOf('\n') + 1);
    }
    return null;
  }

  private static String command(String[] names) {
    String path = System.getenv("PATH");
    if (path == null) return null;
    boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
    String[] dirs = path.split(File.pathSeparator);
    for (int i = 0; i < dirs.length; i++) {
      for (int n = 0; n < names.length; n++) {
        File plain = new File(dirs[i], names[n]);
        if (plain.isFile()) return plain.getAbsolutePath();
        if (win) {
          File exe = new File(dirs[i], names[n] + ".exe");
          if (exe.isFile()) return exe.getAbsolutePath();
          File cmd = new File(dirs[i], names[n] + ".cmd");
          if (cmd.isFile()) return cmd.getAbsolutePath();
        }
      }
    }
    return null;
  }

  private static String linuxDesktop(String token) {
    String home = System.getProperty("user.home", "");
    File[] dirs = new File[] {
      new File(home, ".local/share/applications"),
      new File("/usr/share/applications"),
      new File("/usr/local/share/applications")
    };
    String want = token.toLowerCase();
    for (int d = 0; d < dirs.length; d++) {
      File[] files = dirs[d].listFiles();
      if (files == null) continue;
      for (int i = 0; i < files.length; i++) {
        String name = files[i].getName().toLowerCase();
        if (!name.endsWith(".desktop")) continue;
        if (name.contains(want)) {
          String id = files[i].getName();
          if (id.toLowerCase().endsWith(".desktop")) id = id.substring(0, id.length() - 8);
          return "gtk-launch\n" + id;
        }
      }
    }
    return null;
  }

  private static void copy(String prompt) {
    try {
      StringSelection sel = new StringSelection(prompt == null ? "" : prompt);
      Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
    } catch (Exception ignored) {}
  }
}
