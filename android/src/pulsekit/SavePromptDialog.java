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
 * prompt's name and description, Tag 1–3 (from the tag list, or Add new tag), and the version it
 * is saved as (the prompt's versions there + 1) with today's date. Cancel saves nothing.
 */
final class SavePromptDialog {
    interface Done {
        /** The place and names chosen; null when cancelled. */
        void done(PromptSave.Choice choice);
    }

    /** The dialog shown last, and the tag list or New tag dialog shown last, for the tests. */
    static AlertDialog last;
    static AlertDialog lastTagAsk;

    private SavePromptDialog() {}

    /** The downloaded file at the top of Save prompt (nothing when its type is left out in General settings). */
    static void preview(final MainActivity app, LinearLayout col, final JavaRun.FileOut media) {
        if (!AppSettings.showsFile(media.name)) return;
        int kind = MediaDir.kind(media.name);
        int px = app.dp(120);
        if (kind == MediaDir.PICTURE || kind == MediaDir.VIDEO) {
            android.graphics.Bitmap b = null;
            try {
                b = kind == MediaDir.PICTURE ? PictureOffer.decode(media.bytes, px * 2) : PromptSheet.refThumb(app, media.name, media.bytes, px * 2);
            } catch (Throwable ignored) {
                b = null;
            }
            if (b == null) return;
            android.widget.ImageView image = new android.widget.ImageView(app);
            image.setImageBitmap(b);
            image.setAdjustViewBounds(true);
            image.setMaxHeight(px);
            image.setTag("save-prompt-thumb");
            col.addView(image, new LinearLayout.LayoutParams(-2, px));
            return;
        }
        if (kind != MediaDir.SOUND || MediaDir.isMidi(media.name)) return;
        // A sound: Play, Pause and Stop.
        final java.io.File file = new java.io.File(app.getCacheDir(), "save-prompt-" + media.name.replaceAll("[^A-Za-z0-9._-]", "_"));
        try {
            java.nio.file.Files.write(file.toPath(), media.bytes);
        } catch (Exception ex) {
            return;
        }
        LinearLayout row = app.row();
        final TextView play = app.pill("Play", true, null);
        final TextView stop = app.pill("Stop", false, null);
        play.setTag("save-prompt-play");
        stop.setTag("save-prompt-stop");
        play.setOnClickListener(v -> {
            try {
                if (player == null) {
                    player = new android.media.MediaPlayer();
                    SoundOut.prefer(player);
                    player.setDataSource(file.getAbsolutePath());
                    player.setOnCompletionListener(mp -> {
                        stopPlayer();
                        play.setText("Play");
                    });
                    player.prepare();
                }
                if (player.isPlaying()) {
                    player.pause();
                    play.setText("Play");
                } else {
                    player.start();
                    play.setText("Pause");
                }
            } catch (Exception ex) {
                stopPlayer();
                app.setNow("Could not play " + media.name);
            }
        });
        stop.setOnClickListener(v -> {
            stopPlayer();
            play.setText("Play");
        });
        row.addView(app.text(media.name + "  ", 13, false));
        row.addView(play);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(-2, -2);
        gap.leftMargin = app.dp(6);
        row.addView(stop, gap);
        col.addView(row);
    }

    static void stopPlayer() {
        if (player == null) return;
        try {
            player.release();
        } catch (Exception ignored) {
            // released
        }
        player = null;
    }

    /** The subcategory or group list shown last (for the tests). */
    static AlertDialog lastBrowse;

    static void show(final MainActivity app, final PromptVault vault, PromptRun.Sheet sheet, final Done done) {
        show(app, vault, sheet, null, done);
    }

    /** The player of the downloaded sound shown last (stopped as the dialog closes). */
    static android.media.MediaPlayer player;

    /**
     * As above, with the file the run made shown small at the top: a picture, a video's first
     * frame, or Play / Pause / Stop for a sound (as General settings' Show images, videos, sounds allow).
     */
    static void show(final MainActivity app, final PromptVault vault, PromptRun.Sheet sheet, JavaRun.FileOut media, final Done done) {
        final PromptSave.Choice first = PromptSave.suggest(vault, sheet);
        LinearLayout col = new LinearLayout(app);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = app.dp(14);
        col.setPadding(pad, pad / 2, pad, pad / 2);
        if (media != null) preview(app, col, media);
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
        col.addView(label(app, "Subcategory (optional; \"Clips / Night\" for one inside another)", 12, "#8A8B86"));
        col.addView(withBrowse(app, sub, "save-prompt-sub-browse", () -> {
            if (vault == null) return;
            long mainId = PromptSave.find(vault, main.getText().toString(), "");
            // Subcategories nest: each as its place under the main one ("Clips / Night").
            final List<String> kids = PromptSave.subPaths(vault, mainId);
            // Then a new one inside the one typed, or beside it (made on OK).
            CharSequence[] names = new CharSequence[kids.size() + 3];
            names[0] = "(none)";
            for (int i = 0; i < kids.size(); i++) names[i + 1] = kids.get(i);
            names[kids.size() + 1] = PromptSave.ADD_LOWER;
            names[kids.size() + 2] = PromptSave.ADD_SAME;
            lastBrowse = new AlertDialog.Builder(app).setTitle("Subcategory of " + main.getText().toString().trim()).setItems(names, (d, which) -> {
                if (which <= kids.size()) {
                    sub.setText(which == 0 ? "" : kids.get(which - 1));
                    return;
                }
                final boolean lower = which == kids.size() + 1;
                final String now = sub.getText().toString();
                final EditText typed = field(app, "", "save-prompt-new-sub");
                typed.setHint(lower ? (now.trim().length() == 0 ? "inside " + main.getText().toString().trim() : "inside " + now.trim()) : "beside " + now.trim());
                lastBrowse = new AlertDialog.Builder(app).setTitle(lower ? PromptSave.ADD_LOWER : PromptSave.ADD_SAME).setView(typed)
                    .setPositiveButton("OK", (d2, w2) -> sub.setText(lower ? PromptSave.lowerLevel(now, typed.getText().toString())
                        : PromptSave.sameLevel(now, typed.getText().toString())))
                    .setNegativeButton("Cancel", null).show();
            }).setNegativeButton("Cancel", null).show();
        }));
        col.addView(label(app, "Name", 12, "#8A8B86"));
        col.addView(name);
        col.addView(label(app, "Description (optional)", 12, "#8A8B86"));
        col.addView(description);
        // Tag 1–3: each from the tag list (the prompts' and the Media browser's), or Add new tag.
        final List<String> tagList = PromptSave.tagChoices(vault, app.getFilesDir());
        final String[] tags = new String[3];
        for (int i = 0; i < 3; i++) {
            final int n = i;
            tags[n] = i < first.tags.size() ? first.tags.get(i) : "";
            col.addView(label(app, "Tag " + (n + 1), 12, "#8A8B86"));
            LinearLayout row = new LinearLayout(app);
            row.setOrientation(LinearLayout.HORIZONTAL);
            final Button choose = new Button(app);
            choose.setAllCaps(false);
            choose.setText(tags[n].length() == 0 ? "(none)" : tags[n]);
            choose.setTag("save-prompt-tag" + (n + 1));
            choose.setOnClickListener(v -> {
                final CharSequence[] names = new CharSequence[tagList.size() + 1];
                names[0] = "(none)";
                for (int k = 0; k < tagList.size(); k++) names[k + 1] = tagList.get(k);
                lastTagAsk = new AlertDialog.Builder(app).setTitle("Tag " + (n + 1)).setItems(names, (d, which) -> {
                    tags[n] = which == 0 ? "" : tagList.get(which - 1);
                    choose.setText(names[which]);
                }).setNegativeButton("Cancel", null).show();
            });
            Button add = new Button(app);
            add.setAllCaps(false);
            add.setText("Add new tag");
            add.setTag("save-prompt-tag-new" + (n + 1));
            add.setOnClickListener(v -> {
                final EditText typed = new EditText(app);
                typed.setSingleLine(true);
                typed.setTag("save-prompt-new-tag-field");
                lastTagAsk = new AlertDialog.Builder(app).setTitle("New tag").setView(typed).setPositiveButton("Add new tag", (d, w) -> {
                    String t = PromptSave.newTag(app.getFilesDir(), typed.getText().toString());
                    if (t.length() == 0) return;
                    boolean have = false;
                    for (String k : tagList) have |= k.equalsIgnoreCase(t);
                    if (!have) {
                        tagList.add(t);
                        java.util.Collections.sort(tagList, String.CASE_INSENSITIVE_ORDER);
                    }
                    tags[n] = t;
                    choose.setText(t);
                }).setNegativeButton("Cancel", null).show();
            });
            row.addView(choose, new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(add);
            col.addView(row);
        }
        col.addView(version);
        // Group: the run's result file and the prompt file go in it ((none), a group, New group…).
        FileGroups.load(app.getFilesDir());
        final String[] group = {""};
        col.addView(label(app, "Group (optional)", 12, "#8A8B86"));
        final TextView groupPick = app.outline("(none) \u25be", false, null);
        groupPick.setTag("save-prompt-group");
        groupPick.setOnClickListener(v -> {
            final List<String> groups = FileGroups.names();
            CharSequence[] rows = new CharSequence[groups.size() + 2];
            rows[0] = "(none)";
            for (int i = 0; i < groups.size(); i++) rows[i + 1] = groups.get(i);
            rows[groups.size() + 1] = FileGroups.NEW;
            lastBrowse = new AlertDialog.Builder(app).setTitle("Group").setItems(rows, (d, which) -> {
                if (which <= groups.size()) {
                    group[0] = which == 0 ? "" : groups.get(which - 1);
                    groupPick.setText((which == 0 ? "(none)" : group[0]) + " \u25be");
                    return;
                }
                final EditText typed = field(app, "", "save-prompt-new-group");
                lastBrowse = new AlertDialog.Builder(app).setTitle(FileGroups.NEW).setView(typed).setPositiveButton("OK", (d2, w2) -> {
                    String g = FileGroups.clean(typed.getText().toString());
                    if (g.isEmpty()) return;
                    FileGroups.make(g);
                    group[0] = g;
                    groupPick.setText(g + " \u25be");
                }).setNegativeButton("Cancel", null).show();
            }).setNegativeButton("Cancel", null).show();
        });
        col.addView(groupPick);
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
        dialog.setOnDismissListener(d -> stopPlayer());
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
            c.group = group[0];
            for (String t : tags) if (t.length() > 0) c.tags.add(t);
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
