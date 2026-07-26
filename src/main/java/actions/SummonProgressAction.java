package actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import org.jetbrains.annotations.NotNull;

/**
 * A throwaway tester: runs a fake background task purely so the progress bar has
 * something to show. Not part of the plugin's real function - it exists to give a
 * fast, on-demand feedback loop instead of waiting for the IDE to index.
 *
 * <p>It deliberately runs <b>both</b> bar modes back to back:
 * <ol>
 *   <li><b>indeterminate</b> first - watch the hand pace left and right;</li>
 *   <li><b>determinate</b> after - watch it ride the growing fill from 0 to 100%.</li>
 * </ol>
 * Indexing only ever exercises the first, so this is the only way to see the second.
 */
public class SummonProgressAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {

        // Task.Backgroundable hands OUR code a ProgressIndicator on a background thread.
        // We never touch a JProgressBar - the platform creates one in the status bar and
        // syncs it from the indicator. Our painter is what draws that JProgressBar.
        Task.Backgroundable task =
                new Task.Backgroundable(e.getProject(), "Omori: summoning the red hand", true) {

                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        // --- Phase 1: indeterminate (the pacing hand) ---
                        indicator.setIndeterminate(true);
                        indicator.setText("The hand paces…");
                        long until = System.currentTimeMillis() + 6000;
                        while (System.currentTimeMillis() < until) {
                            indicator.checkCanceled();   // throws if the user hits Cancel
                            sleep(50);
                        }

                        // --- Phase 2: determinate (the advancing hand) ---
                        indicator.setIndeterminate(false);
                        indicator.setText("The hand advances…");
                        for (int pct = 0; pct <= 100; pct++) {
                            indicator.checkCanceled();
                            indicator.setFraction(pct / 100.0);   // 0..1, drives the fill width
                            indicator.setText2(pct + "%");
                            sleep(60);
                        }
                    }
                };

        ProgressManager.getInstance().run(task);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();   // restore the flag, let the task unwind
        }
    }
}
