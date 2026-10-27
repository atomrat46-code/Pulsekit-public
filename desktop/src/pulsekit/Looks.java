package pulsekit;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.UIManager;

/**
 * The desktop's look: FlatLaf's dark theme in Pulsekit's own colors (the pages paint the same dark
 * palette, UiKit's), or Swing's classic look when General settings' Modern look is unticked (or
 * FlatLaf cannot start). Chosen once, before the first window.
 */
final class Looks {
    private Looks() {}

    /** Whether FlatLaf is in use (for the tests too). */
    static boolean modern;
    /** Why FlatLaf did not start ("" when it did, or when the classic look was chosen). */
    static String failure = "";

    /** The look in use, for General settings: "Modern (FlatLaf Dark)", or the classic one and why. */
    static String inUse() {
        if (modern) return "In use: modern (FlatLaf Dark)";
        return "In use: classic (" + UIManager.getLookAndFeel().getName() + ")" + (failure.length() > 0 ? ": FlatLaf could not start: " + failure : "");
    }

    /** "228" from the version file the build puts in the jar, or "" when there is none (run from the classes). */
    static String version() {
        try (java.io.InputStream in = Looks.class.getResourceAsStream("version.txt")) {
            if (in == null) return "";
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[64];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8").trim();
        } catch (Exception ex) {
            return "";
        }
    }

    static void apply() {
        AppSettings.load(PromptDb.dir());
        modern = false;
        failure = "";
        if (AppSettings.modernLook()) {
            try {
                com.formdev.flatlaf.FlatLaf.setGlobalExtraDefaults(defaults());
                modern = com.formdev.flatlaf.FlatDarkLaf.setup();
                if (!modern) failure = "it said no";
            } catch (Throwable ex) {
                // the classic look below; General settings say why
                failure = ex.getClass().getSimpleName() + (ex.getMessage() == null ? "" : " " + ex.getMessage());
            }
        }
        System.out.println("Pulsekit " + version() + " on Java " + System.getProperty("java.version") + ": " + (modern ? "modern look (FlatLaf)" : "classic look") + (failure.length() > 0 ? " (FlatLaf: " + failure + ")" : ""));
        if (modern) return;
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
            // Swing's default
        }
    }

    /** FlatLaf's colors and shapes as Pulsekit's: UiKit's surfaces, text and the gold accent. */
    static Map<String, String> defaults() {
        Map<String, String> d = new LinkedHashMap<String, String>();
        d.put("@background", "#131416");
        d.put("@foreground", "#ECEBE6");
        d.put("@disabledForeground", "#5C5D59");
        d.put("@accentColor", "#D9A441");
        d.put("@selectionBackground", "#3A3424");
        d.put("@selectionForeground", "#ECEBE6");
        d.put("@componentBackground", "#1B1D1F");
        d.put("@buttonBackground", "#1B1D1F");
        d.put("Component.borderColor", "#2A2B2C");
        d.put("Component.focusColor", "#D9A441");
        d.put("Component.arc", "8");
        d.put("Button.arc", "8");
        d.put("TextComponent.arc", "6");
        d.put("CheckBox.arc", "4");
        d.put("ScrollBar.showButtons", "false");
        d.put("ScrollBar.width", "10");
        d.put("ScrollBar.thumbArc", "999");
        d.put("ScrollBar.thumbInsets", "2,2,2,2");
        d.put("ScrollBar.track", "#0A0B0C");
        d.put("ScrollBar.thumb", "#2A2B2C");
        d.put("TabbedPane.selectedBackground", "#1B1D1F");
        d.put("TabbedPane.underlineColor", "#D9A441");
        d.put("PopupMenu.borderColor", "#2A2B2C");
        d.put("TitlePane.background", "#0A0B0C");
        d.put("TitlePane.unifiedBackground", "true");
        return d;
    }
}
