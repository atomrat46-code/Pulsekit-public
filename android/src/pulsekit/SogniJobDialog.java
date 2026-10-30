package pulsekit;

import android.app.AlertDialog;

/** A made file's Sogni job id (MediaDir.SOGNI_JOB) and the workflow Pulsekit kept with it, with Copy. */
final class SogniJobDialog {
    private SogniJobDialog() {}

    /** The dialog shown last, for the tests. */
    static AlertDialog last;

    static void show(final MainActivity app, final String job) {
        last = new AlertDialog.Builder(app).setTitle(MediaDir.SOGNI_JOB)
            .setMessage(SogniHistory.jobText(job))
            .setPositiveButton("Copy", (d, w) -> {
                android.content.ClipboardManager clips = (android.content.ClipboardManager) app.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                if (clips != null) clips.setPrimaryClip(android.content.ClipData.newPlainText("Sogni job", job));
                app.setNow("Sogni job id copied: " + job);
            })
            .setNegativeButton("Close", null)
            .show();
    }

    /** The job id a file carries (a path, or a document address: its first 2 MB), when Sogni tools are shown. */
    static String of(MainActivity app, String id, String name) {
        if (id == null) return null;
        java.io.File f = new java.io.File(id);
        if (f.isFile()) return MediaDir.sogniJob(f, name);
        int k = MediaDir.kind(name);
        if (!AppSettings.showSogni() || (k != MediaDir.PICTURE && k != MediaDir.VIDEO && k != MediaDir.SOUND)) return null;
        try (java.io.InputStream in = app.getContentResolver().openInputStream(android.net.Uri.parse(id))) {
            if (in == null) return null;
            byte[] b = new byte[2 * 1024 * 1024];
            int got = 0;
            for (int n; got < b.length && (n = in.read(b, got, b.length - got)) > 0; ) got += n;
            return SogniApi.jobIn(java.util.Arrays.copyOf(b, got));
        } catch (Exception ex) {
            return null;
        }
    }
}
