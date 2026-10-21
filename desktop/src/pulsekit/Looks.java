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

    static void apply() {
        AppSettings.load(PromptDb.dir());
        modern = false;
        if (AppSettings.modernLook()) {
            try {
                com.formdev.flatlaf.FlatLaf.setGlobalExtraDefaults(defaults());
                modern = com.formdev.flatlaf.FlatDarkLaf.setup();
            } catch (Throwable ignored) {
                // the classic look below
            }
        }
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
