package pulsekit;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

/**
 * Browse prompts: the prompt library's main categories as tabs, the chosen one's subcategories
 * under it, and the prompts in the category picked; tapping a prompt hands its text (its final
 * version) to the field that asked (Params' Prompt, the Prompts page's Prompt). Opens on the
 * category picked last. Favourite prompts (Save prompt's "Add to favourites", the Prompts page's
 * star) are listed on top, then favourite categories (★ Favourite on the one shown) as a row.
 */
final class PromptBrowser {
    interface Picked {
        void picked(String title, String text);
    }

    /** The category shown last (0: the first main category). */
    static long lastCategory;
    /** The browser shown last, for the tests. */
    static AlertDialog last;

    private PromptBrowser() {}

    static void show(final Activity activity, final Picked picked) {
        final PromptVault vault;
        try {
            vault = PromptVault.open(activity.getFilesDir());
        } catch (Exception ex) {
            android.widget.Toast.makeText(activity, "Could not open the prompt library", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        final LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 12);
        body.setPadding(pad, pad / 2, pad, pad / 2);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(body);
        final AlertDialog[] dialog = new AlertDialog[1];
        dialog[0] = new AlertDialog.Builder(activity)
            .setTitle("Browse prompts")
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .create();
        last = dialog[0];
        fill(activity, vault, body, dialog, picked);
        dialog[0].show();
    }

    private static void fill(final Activity activity, final PromptVault vault, final LinearLayout body, final AlertDialog[] dialog, final Picked picked) {
        body.removeAllViews();
        List<PromptVault.Category> mains = vault.mains();
        if (mains.isEmpty()) {
            body.addView(text(activity, "No categories yet", 14, "#8A8B86"));
            return;
        }
        long chosen = vault.category(lastCategory) != null ? lastCategory : mains.get(0).id;
        long mainId = vault.mainOf(chosen);
        // Favourite prompts (versions): a tap hands on that version's text; a long press removes it.
        VersionFavourites.load(activity.getFilesDir());
        List<PromptVault.Version> favPrompts = VersionFavourites.list(vault);
        if (!favPrompts.isEmpty()) {
            body.addView(text(activity, "\u2605 Favourite prompts", 12, "#8A8B86"));
            for (final PromptVault.Version v : favPrompts) {
                final String label = VersionFavourites.label(vault, v);
                final String words = VersionFavourites.words(v);
                final PromptVault.Prompt p = vault.prompt(v.promptId);
                TextView item = text(activity, label, 14, "#E8C35A");
                item.setTag("prompt-browser-favprompt:" + label);
                item.setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 10), dp(activity, 8));
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                bg.setCornerRadius(dp(activity, 8));
                bg.setColor(Color.parseColor("#1B1D1F"));
                item.setBackground(bg);
                item.setOnClickListener(x -> {
                    if (words.length() == 0) {
                        android.widget.Toast.makeText(activity, label + " has no prompt text", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (dialog[0] != null) dialog[0].dismiss();
                    picked.picked(p == null ? label : p.title, words);
                });
                item.setOnLongClickListener(x -> {
                    new AlertDialog.Builder(activity).setTitle(label).setMessage("Remove from favourites?")
                        .setPositiveButton("Remove", (d, w) -> {
                            VersionFavourites.toggle(v.id);
                            fill(activity, vault, body, dialog, picked);
                        })
                        .setNegativeButton("Cancel", null).show();
                    return true;
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.bottomMargin = dp(activity, 6);
                body.addView(item, lp);
            }
        }
        CategoryFavourites.load(activity.getFilesDir());
        List<PromptVault.Category> favs = CategoryFavourites.list(vault);
        if (!favs.isEmpty()) {
            body.addView(text(activity, "\u2605 Favourites", 12, "#8A8B86"));
            HorizontalScrollView favScroll = new HorizontalScrollView(activity);
            favScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout favRow = new LinearLayout(activity);
            favRow.setOrientation(LinearLayout.HORIZONTAL);
            for (PromptVault.Category c : favs) {
                TextView chip = chip(activity, vault, c, c.id == chosen, "prompt-browser-fav:", body, dialog, picked);
                chip.setText(CategoryFavourites.label(vault, c));
                favRow.addView(chip, chipLp(activity));
            }
            favScroll.addView(favRow);
            body.addView(favScroll);
        }
        // Main categories as tabs; the one shown is lit.
        HorizontalScrollView tabs = new HorizontalScrollView(activity);
        tabs.setHorizontalScrollBarEnabled(false);
        LinearLayout tabRow = new LinearLayout(activity);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        for (PromptVault.Category c : mains) tabRow.addView(chip(activity, vault, c, c.id == mainId, "prompt-browser-tab:", body, dialog, picked), chipLp(activity));
        tabs.addView(tabRow);
        body.addView(tabs);
        PromptVault.Category main = vault.category(mainId);
        List<PromptVault.Category> subs = vault.children(mainId);
        if (!subs.isEmpty()) {
            body.addView(text(activity, "Subcategories of " + main.name, 12, "#8A8B86"));
            HorizontalScrollView subScroll = new HorizontalScrollView(activity);
            subScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout subRow = new LinearLayout(activity);
            subRow.setOrientation(LinearLayout.HORIZONTAL);
            for (PromptVault.Category c : subs) subRow.addView(chip(activity, vault, c, c.id == chosen, "prompt-browser-sub:", body, dialog, picked), chipLp(activity));
            subScroll.addView(subRow);
            body.addView(subScroll);
        }
        PromptVault.Category shown = vault.category(chosen);
        TextView heading = text(activity, "PROMPTS IN " + (shown == null ? "" : shown.name.toUpperCase(java.util.Locale.ROOT)), 12, "#8A8B86");
        heading.setPadding(0, dp(activity, 10), 0, dp(activity, 4));
        body.addView(heading);
        if (shown != null) {
            final long id = shown.id;
            boolean on = CategoryFavourites.is(id);
            TextView star = text(activity, on ? "\u2605 Favourite" : "\u2606 Add to favourites", 14, on ? "#E8C35A" : "#ECEBE6");
            star.setTag("prompt-browser-star");
            star.setPadding(0, dp(activity, 2), 0, dp(activity, 8));
            star.setOnClickListener(x -> {
                CategoryFavourites.toggle(id);
                fill(activity, vault, body, dialog, picked);
            });
            body.addView(star);
        }
        List<PromptVault.Prompt> prompts = vault.prompts(chosen);
        if (prompts.isEmpty()) body.addView(text(activity, "No prompts here", 14, "#8A8B86"));
        for (final PromptVault.Prompt p : prompts) {
            final PromptVault.Version v = vault.finalVersion(p.id);
            final String words = v == null ? "" : PromptRun.withoutDescription(v.description, v.body == null ? "" : v.body).trim();
            String first = words.replace('\n', ' ');
            if (first.length() > 70) first = first.substring(0, 70) + "…";
            TextView item = text(activity, (p.title == null ? "Prompt" : p.title) + (first.length() > 0 ? "\n" + first : ""), 14, "#ECEBE6");
            item.setTag("prompt-browser-item:" + p.title);
            item.setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 10), dp(activity, 8));
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(dp(activity, 8));
            bg.setColor(Color.parseColor("#1B1D1F"));
            item.setBackground(bg);
            item.setOnClickListener(x -> {
                if (words.length() == 0) {
                    android.widget.Toast.makeText(activity, p.title + " has no prompt text", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                if (dialog[0] != null) dialog[0].dismiss();
                picked.picked(p.title, words);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(activity, 6);
            body.addView(item, lp);
        }
    }

    private static TextView chip(final Activity activity, final PromptVault vault, final PromptVault.Category c, boolean on, String tag,
            final LinearLayout body, final AlertDialog[] dialog, final Picked picked) {
        TextView chip = text(activity, c.name, 14, on ? "#0A0B0C" : "#ECEBE6");
        chip.setTag(tag + c.name);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(activity, 12), 0, dp(activity, 12), 0);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(dp(activity, 8));
        bg.setColor(Color.parseColor(on ? "#ECEBE6" : "#1B1D1F"));
        chip.setBackground(bg);
        chip.setOnClickListener(x -> {
            lastCategory = c.id;
            fill(activity, vault, body, dialog, picked);
        });
        return chip;
    }

    private static LinearLayout.LayoutParams chipLp(Activity activity) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(activity, 36));
        lp.rightMargin = dp(activity, 8);
        lp.bottomMargin = dp(activity, 6);
        return lp;
    }

    private static TextView text(Activity activity, String s, int size, String color) {
        TextView t = new TextView(activity);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(Color.parseColor(color));
        return t;
    }

    private static int dp(Activity activity, int v) {
        return Math.round(v * activity.getResources().getDisplayMetrics().density);
    }
}
