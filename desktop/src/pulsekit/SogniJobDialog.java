package pulsekit;

/** A made file's Sogni job id (MediaDir.SOGNI_JOB) and the workflow Pulsekit kept with it, with Copy. */
final class SogniJobDialog {
    private SogniJobDialog() {}

    static void show(java.awt.Component parent, String job) {
        Object[] options = {"Copy", "Close"};
        int got = javax.swing.JOptionPane.showOptionDialog(parent, SogniHistory.jobText(job), MediaDir.SOGNI_JOB,
            javax.swing.JOptionPane.DEFAULT_OPTION, javax.swing.JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
        if (got != 0) return;
        try {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(job), null);
        } catch (Exception ignored) {
            // no clipboard (headless)
        }
    }
}
