package pulsekit;

import android.content.Context;
import java.io.File;
import java.util.List;

/**
 * After a run that saved a prompt sheet (SogniMusic and SogniVideo --saveprompt), the sheet goes
 * into the prompt library as a new version of the prompt with its name, in its category: the prompt,
 * the model and type, the reference files it names (read from the run's own inputs: SogniVideo's
 * first and last frame pictures), the result file the run made and the result text. Opening the
 * sheet later loads the pictures back as its reference files. A file over the library's 16 MB is
 * kept by name only. The programs write only the .prompt file; the library is the app's.
 */
final class PromptKeep {
    private static final long MAX_BYTES = 16L * 1024 * 1024;

    private PromptKeep() {}

    /** Stores each sheet the run saved; returns a line for the log, or "" when it saved none. */
    static String keep(Context ctx, JavaRun.Result result, List<String> argv) {
        if (ctx == null || result == null || result.files == null) return "";
        StringBuilder note = new StringBuilder();
        for (JavaRun.FileOut f : result.files) {
            if (f.name == null || !f.name.toLowerCase().endsWith(".prompt") || f.bytes == null) continue;
            PromptRun.Sheet sheet = PromptRun.parse(new String(f.bytes, java.nio.charset.StandardCharsets.UTF_8));
            if (sheet == null || sheet.name.trim().length() == 0) continue;
            try {
                note.append(note.length() > 0 ? "\n" : "").append(store(PromptVault.open(ctx), sheet, result, argv));
            } catch (Exception ex) {
                note.append(note.length() > 0 ? "\n" : "").append("Prompt library: could not store ").append(f.name)
                    .append(ex.getMessage() == null ? "" : " (" + ex.getMessage() + ")");
            }
        }
        return note.toString();
    }

    static String store(PromptVault vault, PromptRun.Sheet sheet, JavaRun.Result result, List<String> argv) throws Exception {
        String catName = sheet.category.trim().length() == 0 ? "General" : sheet.category.trim();
        long catId = 0;
        for (PromptVault.Category c : vault.categories()) {
            if (c.name != null && c.name.trim().equalsIgnoreCase(catName)) {
                catId = c.id;
                break;
            }
        }
        if (catId == 0) catId = vault.addCategory(catName);
        long promptId = 0;
        for (PromptVault.Prompt p : vault.prompts(catId)) {
            if (p.title != null && p.title.trim().equalsIgnoreCase(sheet.name.trim())) promptId = p.id;
        }
        boolean added = promptId == 0;
        if (added) promptId = vault.addPrompt(catId, sheet.name.trim());
        StringBuilder byName = new StringBuilder();
        byte[] ref1 = input(sheet.ref1, argv, byName);
        byte[] ref2 = input(sheet.ref2, argv, byName);
        byte[] made = null;
        if (sheet.result.length() > 0) {
            for (JavaRun.FileOut f : result.files) if (sheet.result.equals(f.name)) made = f.bytes;
            if (made != null && made.length > MAX_BYTES) {
                byName.append(byName.length() > 0 ? ", " : "").append(sheet.result);
                made = null;
            }
        }
        vault.addVersion(promptId, sheet.name.trim(), "", sheet.body, sheet.model, sheet.ref1, ref1, sheet.ref2, ref2,
            sheet.result, made, sheet.type, sheet.resultText);
        StringBuilder sb = new StringBuilder("Prompt library: ").append(sheet.name.trim()).append(" (").append(catName).append(added ? ", new" : ", new version").append(')');
        if (ref1 != null) sb.append(", reference ").append(sheet.ref1);
        if (ref2 != null) sb.append(", ").append(sheet.ref2);
        if (made != null) sb.append(", result ").append(sheet.result);
        if (byName.length() > 0) sb.append(" (by name only, over 16 MB or not found: ").append(byName).append(')');
        return sb.toString();
    }

    /**
     * A MidiDrumGen run's MIDI into the prompt library (Drum Midi Settings: Save MidiDrumGen output
     * file into DB): a new version of the prompt "MidiDrumGen" in Music, the arguments it ran with as
     * its text, the MIDI as result file and the run's "Wrote ..." line as result text. Returns a line
     * for the log, or "" when the run made no MIDI or saved it with a prompt sheet already.
     */
    static String keepMidiDrumGen(Context ctx, JavaRun.Result result, List<String> argv) {
        if (ctx == null || result == null || result.files == null || result.code != 0) return "";
        JavaRun.FileOut midi = null;
        for (JavaRun.FileOut f : result.files) {
            String low = f.name == null ? "" : f.name.toLowerCase();
            if ((low.endsWith(".mid") || low.endsWith(".midi")) && f.bytes != null && f.bytes.length >= 4 && f.bytes[0] == 'M' && f.bytes[1] == 'T') midi = f;
        }
        if (midi == null) return "";
        // A run with --saveprompt keeps its MIDI with its sheet (keep): it is not stored a second time.
        for (JavaRun.FileOut f : result.files) {
            if (f.name == null || !f.name.toLowerCase().endsWith(".prompt") || f.bytes == null) continue;
            PromptRun.Sheet sheet = PromptRun.parse(new String(f.bytes, java.nio.charset.StandardCharsets.UTF_8));
            if (sheet != null && midi.name.equals(sheet.result)) return "";
        }
        StringBuilder args = new StringBuilder("MidiDrumGen");
        if (argv != null) {
            for (String a : argv) {
                // Paths by their names; arguments with spaces in quotes, as typed.
                String shown = a != null && a.indexOf('/') >= 0 ? new File(a).getName() : a == null ? "" : a;
                args.append(' ').append(shown.indexOf(' ') >= 0 ? "\"" + shown + "\"" : shown);
            }
        }
        String wrote = "";
        for (String line : (result.log == null ? "" : result.log).split("\n")) if (line.trim().startsWith("Wrote ")) wrote = line.trim();
        try {
            PromptVault vault = PromptVault.open(ctx);
            long catId = 0;
            for (PromptVault.Category c : vault.categories()) if (c.name != null && c.name.trim().equalsIgnoreCase("Music")) catId = c.id;
            if (catId == 0) catId = vault.addCategory("Music");
            long promptId = 0;
            for (PromptVault.Prompt p : vault.prompts(catId)) if ("MidiDrumGen".equalsIgnoreCase(p.title == null ? "" : p.title.trim())) promptId = p.id;
            if (promptId == 0) promptId = vault.addPrompt(catId, "MidiDrumGen");
            vault.addVersion(promptId, "MidiDrumGen", "", args.toString(), "", "", null, "", null, midi.name, midi.bytes, "", wrote);
            return "Prompt library: MidiDrumGen (Music), result " + midi.name;
        } catch (Exception ex) {
            return "Prompt library: could not store " + midi.name + (ex.getMessage() == null ? "" : " (" + ex.getMessage() + ")");
        }
    }

    /** The bytes of the run's input called `name` (a --image picture), or null after noting why. */
    private static byte[] input(String name, List<String> argv, StringBuilder byName) {
        if (name == null || name.trim().length() == 0) return null;
        if (argv != null) {
            for (String a : argv) {
                File f = new File(a == null ? "" : a);
                if (!f.isFile() || !f.getName().equals(name.trim())) continue;
                if (f.length() > MAX_BYTES) break;
                try {
                    // No java.nio.file before Android 8.
                    java.io.FileInputStream in = new java.io.FileInputStream(f);
                    try {
                        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                        byte[] buf = new byte[65536];
                        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                        return out.toByteArray();
                    } finally {
                        in.close();
                    }
                } catch (Exception ex) {
                    break;
                }
            }
        }
        byName.append(byName.length() > 0 ? ", " : "").append(name.trim());
        return null;
    }
}
