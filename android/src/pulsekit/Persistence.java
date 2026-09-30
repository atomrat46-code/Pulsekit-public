package pulsekit;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static pulsekit.MainActivity.*;

/** Saving and restoring learned patterns, Fillerns, kit and session state. */
final class Persistence {
    final MainActivity app;

    Persistence(MainActivity app) {
        this.app = app;
    }

    File learnedFile() {
        return new File(app.getFilesDir(), "learned.json");
    }

    File kitFile() {
        return new File(app.getFilesDir(), "kit.sf2");
    }

    void persistLearnedBase() {
        try {
            String string = "{\"learned\":" + Engine.learnedJson(app.learned) + ",\"learnedFills\":" + Engine.learnedFillsJson(app.learnedFills) + ",\"variatedFills\":" + Engine.learnedFillsJson(app.variatedFills) + ",\"variatedPatterns\":" + Engine.learnedJson(app.variatedPatterns) + ",\"fillernPairs\":" + this.fillernPairJson() + ",\"importedSongs\":" + Engine.importedSongsJson(app.importedSongs) + "}";
            FileOutputStream fileOutputStream = new FileOutputStream(this.learnedFile());
            fileOutputStream.write(string.getBytes(StandardCharsets.UTF_8));
            fileOutputStream.close();
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    String fillernPairJson() {
        StringBuilder stringBuilder = new StringBuilder("[");
        int n = 0;
        for (Map.Entry<String, String> entry : app.fillernPairs.entrySet()) {
            if (n++ > 0) {
                stringBuilder.append(',');
            }
            stringBuilder.append(Engine.quote(entry.getKey() + "=" + entry.getValue()));
        }
        stringBuilder.append(']');
        return stringBuilder.toString();
    }

    void loadFillernPairs(String string) {
        app.fillernPairs.clear();
        int n = string.indexOf("\"fillernPairs\"");
        if (n < 0) {
            return;
        }
        int n2 = string.indexOf(91, n);
        int n3 = string.indexOf(93, n2);
        if (n2 < 0 || n3 < 0) {
            return;
        }
        String string2 = string.substring(n2 + 1, n3).trim();
        if (string2.isEmpty()) {
            return;
        }
        Matcher matcher = Pattern.compile("\"((?:\\\\.|[^\"])*)\"").matcher(string2);
        while (matcher.find()) {
            String string3 = matcher.group(1).replace("\\\"", "\"");
            int n4 = string3.indexOf(61);
            if (n4 <= 0) continue;
            app.fillernPairs.put(string3.substring(0, n4), string3.substring(n4 + 1));
        }
    }

    void persistSf2(byte[] byArray) {
        if (byArray == null || byArray.length < 16) {
            return;
        }
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(this.kitFile());
            fileOutputStream.write(byArray);
            fileOutputStream.close();
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    void restoreSession() {
        int n;
        FileInputStream fileInputStream;
        byte[] byArray;
        File file;
        try {
            file = this.learnedFile();
            if (file.isFile() && file.length() > 8L) {
                byArray = new byte[(int)file.length()];
                fileInputStream = new FileInputStream(file);
                n = fileInputStream.read(byArray);
                fileInputStream.close();
                if (n > 0) {
                    app.learned.clear();
                    app.learned.addAll(Engine.parseLearnedJson(new String(byArray, 0, n, StandardCharsets.UTF_8)));
                    for (Engine.Learned object : app.learned) {
                        app.styles.put(object.id, new Engine.Style(object.id, object.name, object.bpm, Engine.rowsFromCells(object.cells)));
                        app.importLibrary.addLearnedChip(object);
                    }
                    app.learnedFills.clear();
                    app.learnedFills.addAll(Engine.parseLearnedFillsJson(new String(byArray, 0, n, StandardCharsets.UTF_8)));
                    for (Engine.LearnedFill learnedFill : app.learnedFills) {
                        app.importLibrary.addLearnedFillChip(learnedFill);
                    }
                    app.variatedFills.clear();
                    app.variatedFills.addAll(Engine.parseVariatedFillsJson(new String(byArray, 0, n, StandardCharsets.UTF_8)));
                    for (Engine.LearnedFill learnedFill : app.variatedFills) {
                        app.styleLibrary.addVariatedFillChip(learnedFill);
                    }
                    app.variatedPatterns.clear();
                    app.variatedPatterns.addAll(Engine.parseVariatedPatternsJson(new String(byArray, 0, n, StandardCharsets.UTF_8)));
                    for (Engine.Learned learned : app.variatedPatterns) {
                        app.styles.put(learned.id, new Engine.Style(learned.id, learned.name, learned.bpm, Engine.rowsFromCells(learned.cells)));
                        app.styleLibrary.addVariatedPatternChip(learned);
                    }
                    this.loadFillernPairs(new String(byArray, 0, n, StandardCharsets.UTF_8));
                    List<Engine.ImportedSong> list = Engine.decodeImportedSongs(new String(byArray, 0, n, StandardCharsets.UTF_8));
                    if (!list.isEmpty()) {
                        app.importedSongs.clear();
                        app.importedSongs.addAll((Collection<Engine.ImportedSong>)list);
                        app.importedSongId = ((Engine.ImportedSong)list.get((int)0)).id;
                    }
                }
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        try {
            file = this.kitFile();
            if (file.isFile() && file.length() > 64L) {
                byArray = new byte[(int)file.length()];
                fileInputStream = new FileInputStream(file);
                n = fileInputStream.read(byArray);
                fileInputStream.close();
                if (n > 0) {
                    app.playback.applySf2(AudioIo.parseSf2(n == byArray.length ? byArray : Arrays.copyOf(byArray, n)));
                }
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    String jsonStr(String string, String string2) {
        int n = string.indexOf(string2);
        if (n < 0) {
            return null;
        }
        int n2 = string.indexOf(58, n);
        if (n2 < 0) {
            return null;
        }
        int n3 = string.indexOf(34, n2 + 1);
        if (n3 < 0) {
            return null;
        }
        int n4 = string.indexOf(34, n3 + 1);
        if (n4 < 0) {
            return null;
        }
        return string.substring(n3 + 1, n4);
    }

    int jsonInt(String string, String string2, int n) {
        int n2;
        int n3;
        int n4 = string.indexOf(string2);
        if (n4 < 0) {
            return n;
        }
        int n5 = string.indexOf(58, n4);
        if (n5 < 0) {
            return n;
        }
        for (n3 = n5 + 1; n3 < string.length() && string.charAt(n3) == ' '; ++n3) {
        }
        for (n2 = n3; n2 < string.length() && "-0123456789".indexOf(string.charAt(n2)) >= 0; ++n2) {
        }
        try {
            return Integer.parseInt(string.substring(n3, n2));
        }
        catch (Exception exception) {
            return n;
        }
    }

    double jsonDouble(String string, String string2, double d) {
        int n;
        int n2;
        int n3 = string.indexOf(string2);
        if (n3 < 0) {
            return d;
        }
        int n4 = string.indexOf(58, n3);
        if (n4 < 0) {
            return d;
        }
        for (n2 = n4 + 1; n2 < string.length() && string.charAt(n2) == ' '; ++n2) {
        }
        for (n = n2; n < string.length() && "-0123456789.eE".indexOf(string.charAt(n)) >= 0; ++n) {
        }
        try {
            return Double.parseDouble(string.substring(n2, n));
        }
        catch (Exception exception) {
            return d;
        }
    }

    void copyPattern(String string, String string2, int[][] nArray) {
        int n = string.indexOf(string2);
        if (n < 0) {
            return;
        }
        for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
            String string3 = "\"" + Engine.TRACK_ID[i] + "\"";
            int n2 = string.indexOf(string3, n);
            if (n2 < 0) continue;
            int n3 = string.indexOf(91, n2);
            int n4 = string.indexOf(93, n3);
            if (n3 < 0 || n4 < 0) continue;
            String[] stringArray = string.substring(n3 + 1, n4).split(",");
            for (int j = 0; j < 32 && j < stringArray.length; ++j) {
                try {
                    nArray[i][j] = Integer.parseInt(stringArray[j].trim());
                    continue;
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
        }
    }

    void persistLearned() {
        this.persistLearnedBase();
        app.fileSets.persistFsetInfo();
    }
}
