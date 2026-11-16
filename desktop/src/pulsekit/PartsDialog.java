package pulsekit;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * vst_render's Parts (--parts kick=2,snare=1,...): which plugin row plays which drums when the plugins
 * play one run together. Configure… opens this: a list of the plugins chosen (rows 1 to 4), and for the
 * one picked the parts it plays (kick, snare, toms, hats, cymbals); a part ticked for one plugin is taken
 * from the others. The parts no plugin has: plugin 1's.
 */
final class PartsDialog {
    private PartsDialog() {}

    static final String[] PARTS = {"kick", "snare", "toms", "hats", "cymbals"};
    static final String[] PART_LABELS = {"Kick", "Snare", "Toms", "Hats (else with the cymbals)", "Cymbals"};

    /** "kick=2,snare=1": {part: row}; what it cannot read is left out. */
    static Map<String, Integer> parse(String text) {
        Map<String, Integer> out = new LinkedHashMap<String, Integer>();
        for (String pair : (text == null ? "" : text).split("[,;\\s]+")) {
            String[] kv = pair.trim().toLowerCase(java.util.Locale.ROOT).split("=");
            if (kv.length != 2) continue;
            try {
                int row = Integer.parseInt(kv[1].trim());
                if (row >= 1 && row <= 4 && java.util.Arrays.asList(PARTS).contains(kv[0].trim())) out.put(kv[0].trim(), row);
            } catch (NumberFormatException ignored) {
                // left out
            }
        }
        return out;
    }

    /** The parts as --parts' text, in PARTS' order. */
    static String build(Map<String, Integer> parts) {
        StringBuilder b = new StringBuilder();
        for (String p : PARTS) if (parts.containsKey(p)) b.append(b.length() == 0 ? "" : ",").append(p).append('=').append(parts.get(p));
        return b.toString();
    }

    /** What the row says: "Kick: plugin 2 · Cymbals: plugin 3 · the rest: plugin 1", or that none is set. */
    static String summary(String text, String[] plugins) {
        Map<String, Integer> parts = parse(text);
        if (parts.isEmpty()) return "Not set: Configure… picks which plugin plays which drums";
        StringBuilder b = new StringBuilder();
        for (int r = 1; r <= 4; r++) {
            StringBuilder mine = new StringBuilder();
            for (String p : PARTS) if (parts.get(p) != null && parts.get(p) == r) mine.append(mine.length() == 0 ? "" : ", ").append(p);
            if (mine.length() > 0) b.append(b.length() == 0 ? "" : " · ").append(name(plugins, r)).append(": ").append(mine);
        }
        return b.append(" · the rest: ").append(name(plugins, 1)).toString();
    }

    static String name(String[] plugins, int row) {
        String p = plugins != null && row - 1 < plugins.length ? plugins[row - 1] : null;
        if (p == null || p.trim().isEmpty()) return "plugin " + row;
        return row + " " + new java.io.File(p.trim()).getName().replaceAll("(?i)\\.(vst3|dll|clap|lv2|so|vst)$", "");
    }

    /** The window's content: the plugin list and its parts. `result()` gives the text when OK is pressed. */
    static final class Panel extends JPanel {
        final Map<String, Integer> parts;
        final JComboBox<String> plugin = new JComboBox<String>();
        final JCheckBox[] boxes = new JCheckBox[PARTS.length];
        final int[] rows;
        final JLabel said = new JLabel(" ");
        final String[] plugins;
        boolean quiet;

        /** `plugins`: rows 1 to 4's plugin files (null or empty: no plugin there). */
        Panel(String text, String[] plugins) {
            super(new BorderLayout(0, 8));
            this.plugins = plugins;
            this.parts = parse(text);
            int n = 0;
            int[] tmp = new int[4];
            for (int r = 1; r <= 4; r++) {
                if (plugins == null || r - 1 >= plugins.length || plugins[r - 1] == null || plugins[r - 1].trim().isEmpty()) continue;
                tmp[n++] = r;
                this.plugin.addItem(name(plugins, r));
            }
            this.rows = java.util.Arrays.copyOf(tmp, n);
            this.plugin.setName("parts-plugin");
            JPanel top = new JPanel(new BorderLayout(6, 0));
            top.add(new JLabel("Plugin:"), BorderLayout.WEST);
            top.add(this.plugin, BorderLayout.CENTER);
            this.add(top, BorderLayout.NORTH);
            JPanel list = new JPanel(new GridLayout(0, 1, 0, 2));
            list.setBorder(BorderFactory.createTitledBorder("Plays"));
            for (int i = 0; i < PARTS.length; i++) {
                final int at = i;
                this.boxes[i] = new JCheckBox(PART_LABELS[i]);
                this.boxes[i].setName("parts-part:" + PARTS[i]);
                this.boxes[i].addActionListener(e -> this.tick(at));
                list.add(this.boxes[i]);
            }
            this.add(list, BorderLayout.CENTER);
            this.said.setName("parts-summary");
            this.add(this.said, BorderLayout.SOUTH);
            this.plugin.addActionListener(e -> this.refresh());
            if (n == 0) {
                for (JCheckBox b : this.boxes) b.setEnabled(false);
                this.said.setText("Choose the plugins first (rows 1 to 4)");
            } else {
                this.refresh();
            }
        }

        int row() {
            int i = this.plugin.getSelectedIndex();
            return i < 0 || i >= this.rows.length ? 1 : this.rows[i];
        }

        /** The plugin picked's parts ticked. */
        void refresh() {
            this.quiet = true;
            int r = this.row();
            for (int i = 0; i < PARTS.length; i++) {
                Integer has = this.parts.get(PARTS[i]);
                this.boxes[i].setSelected(has != null && has == r);
            }
            this.quiet = false;
            this.said.setText("<html>" + summary(build(this.parts), this.plugins) + "</html>");
        }

        /** A part ticked: this plugin's (taken from another); unticked: no plugin's (plugin 1 plays it). */
        void tick(int i) {
            if (this.quiet) return;
            if (this.boxes[i].isSelected()) this.parts.put(PARTS[i], this.row());
            else this.parts.remove(PARTS[i]);
            this.refresh();
        }

        String result() {
            return build(this.parts);
        }
    }
}
