package pulsekit;

import android.app.Activity;
import android.app.AlertDialog;

/**
 * A favourite prompt's menu (a long press, or its Menu button): open it (Browse prompts: use its
 * text), copy its prompt text, change its prompt's title, move it up or down the list, or remove it
 * from the favourites.
 */
final class FavouriteMenu {
    /** The menu shown last, for the tests. */
    static AlertDialog last;

    private FavouriteMenu() {}

    static void show(final Activity activity, final PromptVault vault, final PromptVault.Version v, String openLabel, final Runnable open, final Runnable changed) {
        final String label = VersionFavourites.label(vault, v);
        final String words = VersionFavourites.words(v);
        String[] items = {openLabel, "Copy prompt text", "Change title", "Move up", "Move down", "Remove from favourites"};
        last = new AlertDialog.Builder(activity).setTitle("★ " + label).setItems(items, (d, which) -> {
            if (which == 0) {
                open.run();
            } else if (which == 1) {
                android.content.ClipboardManager clips = (android.content.ClipboardManager) activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                if (clips != null) clips.setPrimaryClip(android.content.ClipData.newPlainText("prompt", words));
                toast(activity, words.length() == 0 ? label + " has no prompt text" : "Copied the prompt of " + label);
            } else if (which == 2) {
                changeTitle(activity, vault, v, changed);
            } else if (which == 3 || which == 4) {
                if (!VersionFavourites.move(v.id, which == 3 ? -1 : 1)) toast(activity, which == 3 ? "Already the first" : "Already the last");
                changed.run();
            } else {
                VersionFavourites.toggle(v.id);
                toast(activity, "Removed " + label + " from favourites");
                changed.run();
            }
        }).setNegativeButton("Cancel", null).show();
    }

    /** The dialog Change title shows last, for the tests. */
    static AlertDialog lastTitle;

    /** Renames the favourite's prompt (all its versions share the title). */
    private static void changeTitle(final Activity activity, final PromptVault vault, final PromptVault.Version v, final Runnable changed) {
        PromptVault.Prompt p = vault.prompt(v.promptId);
        final android.widget.EditText field = new android.widget.EditText(activity);
        field.setSingleLine(true);
        field.setTag("favourite-title");
        field.setText(p == null || p.title == null ? "" : p.title);
        field.setSelectAllOnFocus(true);
        lastTitle = new AlertDialog.Builder(activity).setTitle("Change title").setView(field).setPositiveButton("Save", (d, w) -> {
            try {
                vault.renamePrompt(v.promptId, field.getText().toString());
                toast(activity, "Renamed · " + VersionFavourites.label(vault, v));
                changed.run();
            } catch (Exception ex) {
                toast(activity, ex.getMessage() != null ? ex.getMessage() : ex.toString());
            }
        }).setNegativeButton("Cancel", null).show();
    }

    private static void toast(Activity activity, String text) {
        android.widget.Toast.makeText(activity, text, android.widget.Toast.LENGTH_SHORT).show();
    }
}
