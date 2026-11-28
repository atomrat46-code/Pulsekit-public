package pulsekit;

import android.app.AlertDialog;

/**
 * Picture ready's and Video ready's Back: to the screen that chained the run which made them. Document ready
 * whose Auto render made the picture (opened again, as it was), or the program whose Chain made the video
 * (or the program whose input folder or Auto render made them), or Document ready whose Send as prompt
 * opened the program. Set when such a batch starts or Send is used; cleared when another program's own run starts.
 */
final class ReadyBack {
    private ReadyBack() {}

    static final String BACK = "Back";

    /** Where Back goes (a description, for the tests), what it does (null: no Back), and the program whose runs have it. */
    static volatile String to;
    static volatile Runnable back;
    static volatile String forProgram;

    static void set(String program, String where, Runnable r) {
        forProgram = program;
        to = where;
        back = r;
    }

    static void clear() {
        forProgram = null;
        to = null;
        back = null;
    }

    /** `forProgram`'s runs: Back to `program`'s page (the Java programs, that program selected). */
    static void toProgram(final MainActivity app, String forProgram, final String program) {
        set(forProgram, SogniSections.app(program), () -> {
            app.openKitView("py");
            app.programMenus.selectProgram("Java", program);
        });
    }

    /** Back on a ready screen (its neutral button) when a chain made what it shows. */
    static void addTo(AlertDialog.Builder b) {
        final Runnable r = back;
        if (r != null) b.setNeutralButton(BACK, (d, w) -> r.run());
    }
}
