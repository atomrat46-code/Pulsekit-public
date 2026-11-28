package pulsekit;

/**
 * Upscale (Video ready, the Media browser's video player): SogniVideoUpscale on PyJav with the video as
 * its input, its Params open (the size, RelayVSR or FlashVSR...); Return goes back where it started.
 */
final class VideoUp {
    private VideoUp() {}

    static final String PROGRAM = "SogniVideoUpscale.java";
    static final String BUTTON = "Upscale";

    /** The program with `video` (a file PyJav can read) as its input; `back` (with its label) for Return, or null. */
    static void start(MainActivity app, java.io.File video, String label, Runnable back) {
        app.openKitView("py");
        app.programMenus.selectProgram("Java", PROGRAM);
        app.pyJav.pkUseInputPath(video.getAbsolutePath());
        if (back != null) app.pyJav.pkSetReturn(label, back);
        app.pyJav.pkOpenParams();
    }

    /** As above with a copy of the bytes (a ready dialog's clip) in PyJav's input folder; false when it cannot be written. */
    static boolean start(MainActivity app, String name, byte[] data, String label, Runnable back) {
        try {
            java.io.File dir = new java.io.File(app.getCacheDir(), "pyjav-in");
            if (!dir.isDirectory()) dir.mkdirs();
            java.io.File in = new java.io.File(dir, name.replaceAll("[\\\\/:*?\"<>|]", "_"));
            java.io.FileOutputStream out = new java.io.FileOutputStream(in);
            try {
                out.write(data);
            } finally {
                out.close();
            }
            start(app, in, label, back);
            return true;
        } catch (Exception ex) {
            app.setNow("Could not hand " + name + " to " + SogniSections.app(PROGRAM) + ": " + ex.getMessage());
            return false;
        }
    }
}
