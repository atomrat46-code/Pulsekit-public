package pulsekit;

import android.app.AlertDialog;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static pulsekit.MainActivity.*;

/**
 * The Java, Python and Code menus on the PyJav page. They list the bundled
 * Programs/Java, Programs/Python and Programs/Code folders (APK assets).
 *
 * Java and Python pick the program Run executes; the editor is left alone.
 * Code opens a file in the editor for editing and never changes what Run executes.
 */
final class ProgramMenus {
    static final String[] KINDS = {"Java", "Python", "Code"};

    final MainActivity app;
    final TextView[] buttons = new TextView[KINDS.length];

    /** The listed program Run executes, while it is still the current program. */
    String runName;
    String runSource;
    String runKind;

    ProgramMenus(MainActivity app) {
        this.app = app;
    }

    /** A row with the three menu buttons. */
    LinearLayout buildRow() {
        LinearLayout row = app.row();
        row.setTag("program-menus");
        for (int i = 0; i < KINDS.length; i++) {
            final String kind = KINDS[i];
            TextView b = app.action(kind + " ▾", ELEV, FG, v -> this.showMenu(kind));
            b.setSingleLine(true);
            b.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, app.dp(40), 1.0f);
            lp.setMargins(i == 0 ? 0 : app.dp(3), 0, i == KINDS.length - 1 ? 0 : app.dp(3), 0);
            row.addView(b, lp);
            this.buttons[i] = b;
        }
        return row;
    }

    /** File names in Programs/KIND, sorted. */
    String[] list(String kind) {
        try {
            String[] names = app.getAssets().list("Programs/" + kind);
            if (names == null) return new String[0];
            List<String> files = new ArrayList<>();
            for (String n : names) {
                if (n.indexOf('.') > 0) files.add(n);
            }
            String[] out = files.toArray(new String[0]);
            Arrays.sort(out, String.CASE_INSENSITIVE_ORDER);
            return out;
        } catch (Exception e) {
            return new String[0];
        }
    }

    byte[] read(String kind, String name) throws Exception {
        InputStream in = app.getAssets().open("Programs/" + kind + "/" + name);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    void showMenu(final String kind) {
        final String[] names = this.list(kind);
        if (names.length == 0) {
            app.setNow("No " + kind + " programs in Programs/" + kind);
            return;
        }
        new AlertDialog.Builder(app)
            .setTitle(kind)
            .setItems(names, (dialog, which) -> this.pick(kind, names[which]))
            .setNegativeButton("Cancel", null)
            .show();
    }

    void pick(String kind, String name) {
        if ("Code".equals(kind)) {
            this.openCode(name);
        } else {
            this.selectProgram(kind, name);
        }
    }

    /** Java or Python: this file becomes the program Run executes. */
    void selectProgram(String kind, String name) {
        try {
            byte[] data = this.read(kind, name);
            String src = new String(data, StandardCharsets.UTF_8);
            app.pyName = name;
            app.pkPyBytes = null;
            app.pyJav.pkPyInputPath = null;
            this.runName = name;
            this.runSource = src;
            this.runKind = kind;
            app.pyJav.pkShowPromptModes();
            app.pyJav.pkApplyHint(PyJavHints.status(name, src, data));
            this.paint();
            app.setNow("Run · " + name);
        } catch (Exception e) {
            app.setNow("Could not open " + name);
        }
    }

    /** Code: show the file in the editor for editing. What Run executes does not change. */
    void openCode(String name) {
        try {
            String text = new String(this.read("Code", name), StandardCharsets.UTF_8);
            if (app.pyEditor != null) {
                app.pyEditor.setText(text);
                app.pyEditor.setSelection(0);
                app.pyEditor.scrollTo(0, 0);
            }
            this.buttons[2].setText("Code · " + name);
            app.setNow("Editing · " + name);
        } catch (Exception e) {
            app.setNow("Could not open " + name);
        }
    }

    /** Source Run should execute: the listed program while it is still current, else null. */
    String sourceToRun() {
        return this.current() ? this.runSource : null;
    }

    /** True while the listed program is still the current program (nothing else was opened since). */
    boolean current() {
        return this.runName != null && this.runName.equals(app.pyName) && app.pkPyBytes == null;
    }

    /** Button labels: the selected Java/Python program, or the plain menu name. */
    void paint() {
        if (this.runName != null && !this.current()) {
            this.runName = null;
            this.runSource = null;
            this.runKind = null;
        }
        for (int i = 0; i < 2; i++) {
            if (this.buttons[i] == null) continue;
            boolean on = KINDS[i].equals(this.runKind);
            this.buttons[i].setText(on ? KINDS[i] + " · " + this.runName : KINDS[i] + " ▾");
            this.buttons[i].setBackground(app.round(on ? HIT : ELEV, 8));
            this.buttons[i].setTextColor(on ? BG : FG);
        }
    }
}
