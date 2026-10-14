package pulsekit;

import android.app.AlertDialog;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

/**
 * Save prompt, after a run whose prompt sheet (--saveprompt) changed: the main category and the
 * subcategory (typed, browsed, or one of the places used recently; a new name is made on OK), the
 * prompt's name and description, and the version it is saved as (the prompt's versions there + 1)
 * with today's date. Cancel saves nothing.
 */
final class SavePromptDialog {
    interface Done {
        /** The place and names chosen; null when cancelled. */
        void done(PromptSave.Choice choice);
    }

    /** The dialog shown last, for the tests. */
    static AlertDialog last;

    private SavePromptDialog() {}

    static void show(final MainActivity app, final PromptVault vault, PromptRun.Sheet sheet, final Done done) {
        final PromptSave.Choice first = PromptSave.suggest(vault, sheet);
        LinearLayout col = new LinearLayout(app);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = app.dp(14);
        col.setPadding(pad, pad / 2, pad, pad / 2);
        final EditText main = field(app, first.main, "save-prompt-main");
        final EditText sub = field(app, first.sub, "save-prompt-sub");
        final EditText name = field(app, first.name, "save-prompt-name");
        final EditText description = field(app, first.description, "save-prompt-description");
        description.setSingleLine(false);
        description.setMinLines(2);
        final TextView version = label(app, "", 13, "#ECEBE6");
        version.setTag("save-prompt-version");
        final Runnable paintVersion = () -> {
            int v = vault == null ? 1 : PromptSave.nextVersion(vault, main.getText().toString(), sub.getText().toString(), name.getText().toString());
            version.setText("Saved as v" + v + " · " + PromptSave.today());
        };
        List<String[]> recent = PromptSave.recent();
        if (!recent.isEmpty()) {
            col.addView(label(app, "Recent", 12, "#8A8B86"));
            HorizontalScrollView scroll = new HorizontalScrollView(app);
            LinearLayout row = new LinearLayout(app);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (final String[] place : recent) {
                String text = PromptSave.placeLabel(place[0], place[1]);
                TextView pill = app.pill(text, false, v -> {
                    main.setText(place[0]);
                    sub.setText(place[1]);
                });
                pill.setTag("save-prompt-recent:" + text);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                lp.rightMargin = app.dp(6);
                row.addView(pill, lp);
            }
            scroll.addView(row);
            col.addView(scroll);
        }
        col.addView(label(app, "Main category", 12, "#8A8B86"));
        col.addView(withBrowse(app, main, "save-prompt-main-browse", () -> {
            if (vault == null) return;
            final List<PromptVault.Category> mains = vault.mains();
            if (mains.isEmpty()) {
                app.setNow("No categories yet: type a name, it is made on OK");
                return;
            }
            CharSequence[] names = new CharSequence[mains.size()];
            for (int i = 0; i < mains.size(); i++) names[i] = mains.get(i).name;
            new AlertDialog.Builder(app).setTitle("Main category").setItems(names, (d, which) -> {
                main.setText(mains.get(which).name);
                sub.setText("");
            }).setNegativeButton("Cancel", null).show();
        }));
        col.addView(label(app, "Subcategory (optional)", 12, "#8A8B86"));
        col.addView(withBrowse(app, sub, "save-prompt-sub-browse", () -> {
            if (vault == null) return;
            long mainId = PromptSave.find(vault, main.getText().toString(), "");
            final List<PromptVault.Category> kids = mainId == 0 ? new java.util.ArrayList<PromptVault.Category>() : vault.children(mainId);
            if (kids.isEmpty()) {
                app.setNow("No subcategories in " + main.getText().toString().trim() + ": type a name, it is made on OK");
                return;
            }
            CharSequence[] names = new CharSequence[kids.size() + 1];
            names[0] = "(none)";
            for (int i = 0; i < kids.size(); i++) names[i + 1] = kids.get(i).name;
            new AlertDialog.Builder(app).setTitle("Subcategory of " + main.getText().toString().trim()).setItems(names, (d, which) -> {
                sub.setText(which == 0 ? "" : kids.get(which - 1).name);
            }).setNegativeButton("Cancel", null).show();
        }));
        col.addView(label(app, "Name", 12, "#8A8B86"));
        col.addView(name);
        col.addView(label(app, "Description (optional)", 12, "#8A8B86"));
        col.addView(description);
        col.addView(version);
        // Add to favourites: the version stored goes into the favourite prompts (Browse prompts, Prompts page).
        final android.widget.CheckBox favourite = new android.widget.CheckBox(app);
        favourite.setText("Add to favourites");
        favourite.setTag("save-prompt-favourite");
        col.addView(favourite);
        TextWatcher watch = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                paintVersion.run();
            }
        };
        main.addTextChangedListener(watch);
        sub.addTextChangedListener(watch);
        name.addTextChangedListener(watch);
        paintVersion.run();
        ScrollView scroll = new ScrollView(app);
        scroll.addView(col);
        final boolean[] answered = new boolean[1];
        final AlertDialog dialog = new AlertDialog.Builder(app)
            .setTitle("Save prompt")
            .setView(scroll)
            .setPositiveButton("OK", null)
            .setNegativeButton("Cancel", (d, w) -> {
                answered[0] = true;
                done.done(null);
            })
            .setOnCancelListener(d -> {
                if (answered[0]) return;
                answered[0] = true;
                done.done(null);
            })
            .create();
        last = dialog;
        dialog.show();
        // OK checks the names first; the dialog stays open until they are given.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (main.getText().toString().trim().length() == 0) {
                app.setNow("Name the main category");
                return;
            }
            if (name.getText().toString().trim().length() == 0) {
                app.setNow("Name the prompt");
                return;
            }
            PromptSave.Choice c = new PromptSave.Choice();
            c.main = main.getText().toString().trim();
            c.sub = sub.getText().toString().trim();
            c.name = name.getText().toString().trim();
            c.description = description.getText().toString().trim();
            c.favourite = favourite.isChecked();
            answered[0] = true;
            dialog.dismiss();
            done.done(c);
        });
    }

    private static LinearLayout withBrowse(MainActivity app, EditText field, String tag, final Runnable browse) {
        LinearLayout row = new LinearLayout(app);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(field, new LinearLayout.LayoutParams(0, -2, 1f));
        Button b = new Button(app);
        b.setText("Browse");
        b.setTag(tag);
        b.setOnClickListener(v -> browse.run());
        row.addView(b);
        return row;
    }

    private static EditText field(MainActivity app, String text, String tag) {
        EditText e = new EditText(app);
        e.setSingleLine(true);
        e.setText(text == null ? "" : text);
        e.setTag(tag);
        return e;
    }

    private static TextView label(MainActivity app, String s, int size, String color) {
        TextView t = new TextView(app);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(Color.parseColor(color));
        t.setPadding(0, app.dp(6), 0, app.dp(2));
        return t;
    }
}
