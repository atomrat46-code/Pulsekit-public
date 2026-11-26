package pulsekit;

import android.app.AlertDialog;

/**
 * Picture ready's and Video ready's Back: to the screen that chained the run which made them. Document ready
 * whose Auto render made the picture (opened again, as it was), or the program whose Chain made the video
 * (or the program whose input folder or Auto render made them). Set when such a batch starts; cleared when a
 * run of its own starts.
 */
final class ReadyBack {
    private ReadyBack() {}

    static final String BACK = "Back";

    /** Where Back goes (a description, for the tests) and what it does; null: no Back. */
    static volatile String to;
    static volatile Runnable back;

    static void set(String where, Runnable r) {
        to = where;
        back = r;
    }

    static void clear() {
        to = null;
        back = null;
    }

    /** The program's page (the Java programs, that program selected). */
    static void toProgram(final MainActivity app, final String program) {
        set(SogniSections.app(program), () -> {
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
