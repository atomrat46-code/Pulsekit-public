package pulsekit;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.concurrent.Callable;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

/**
 * Long DB work run with its progress (WorkProgress) in a window while it runs, as on Android: the
 * step being done, a bar, the percent done and left, and about how long to go.
 */
final class WorkProgressDialog {
    private WorkProgressDialog() {}

    /** The work run on a thread of its own while the window shows; what it gave, or what it threw. */
    static <T> T run(Component parent, String title, int steps, final Callable<T> work) throws Exception {
        final WorkProgress progress = new WorkProgress(steps);
        final Object[] got = new Object[1];
        final Exception[] failed = new Exception[1];
        Runnable job = () -> {
            WorkProgress.NOW.set(progress);
            try {
                got[0] = work.call();
            } catch (Exception ex) {
                failed[0] = ex;
            } catch (Throwable ex) {
                failed[0] = new Exception(ex.toString(), ex);
            } finally {
                WorkProgress.NOW.remove();
            }
        };
        if (!SwingUtilities.isEventDispatchThread() || java.awt.GraphicsEnvironment.isHeadless()) {
            job.run();
        } else {
            final Thread t = new Thread(job, "pulsekit-db-work");
            java.awt.Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
            if (parent instanceof java.awt.Window) owner = (java.awt.Window) parent;
            final JDialog d = new JDialog(owner, title, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
            d.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            final JLabel step = new JLabel("Starting…");
            step.setName("work-step");
            final JProgressBar bar = new JProgressBar(0, 1000);
            bar.setName("work-bar");
            bar.setStringPainted(true);
            final JLabel percent = new JLabel("0% done");
            percent.setName("work-percent");
            JPanel col = new JPanel(new BorderLayout(0, 8));
            col.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
            col.add(step, BorderLayout.NORTH);
            col.add(bar, BorderLayout.CENTER);
            col.add(percent, BorderLayout.SOUTH);
            d.setContentPane(col);
            d.setSize(460, 140);
            d.setLocationRelativeTo(parent);
            final javax.swing.Timer timer = new javax.swing.Timer(250, null);
            timer.addActionListener(e -> {
                step.setText(progress.stepText());
                int at = (int) Math.round(progress.fraction() * 1000);
                bar.setValue(at);
                bar.setString(at / 10 + "%");
                percent.setText(progress.percentText());
                if (!t.isAlive()) {
                    timer.stop();
                    d.dispose();
                }
            });
            t.start();
            timer.start();
            d.setVisible(true);
            timer.stop();
            d.dispose();
            t.join();
        }
        if (failed[0] != null) throw failed[0];
        @SuppressWarnings("unchecked")
        T out = (T) got[0];
        return out;
    }
}
