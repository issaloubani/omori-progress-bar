package listener;

import bars.OmoriRedHandBar;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.wm.IdeFrame;
import org.jetbrains.annotations.NotNull;

import javax.swing.UIManager;

/**
 * Keeps our {@link OmoriRedHandBar} installed as the IDE's progress bar painter.
 *
 * <p>Two topics, one job:
 * <ul>
 *   <li>{@code ApplicationActivationListener} - fires when the IDE window first gains
 *       focus, i.e. shortly after startup. This is what <b>bootstraps</b> the
 *       registration; without it nothing would install the painter at all.</li>
 *   <li>{@code LafManagerListener} - fires on theme change. A theme switch rebuilds
 *       {@code UIManager}'s table wholesale, discarding our entry, so we re-apply.</li>
 * </ul>
 */
public class OmoriProgressListener implements LafManagerListener, ApplicationActivationListener {

    /** Swing's lookup key for "who paints JProgressBar". */
    private static final String PROGRESS_BAR_UI_KEY = "ProgressBarUI";

    @Override
    public void lookAndFeelChanged(@NotNull LafManager lafManager) {
        renderOmoriBar();
    }

    @Override
    public void applicationActivated(@NotNull IdeFrame ideFrame) {
        renderOmoriBar();
    }

    void renderOmoriBar() {
        String className = OmoriRedHandBar.class.getName();

        // 1. Point the "ProgressBarUI" key at our class, by name. This is the
        //    instruction - without it nothing asks for our painter.
        UIManager.put(PROGRESS_BAR_UI_KEY, className);

        // 2. Pre-resolve that name to the actual Class. Swing would otherwise try to
        //    load it through the platform's classloader, which cannot see inside a
        //    plugin - so this line is what stops a ClassNotFoundException the moment
        //    the first progress bar appears.
        UIManager.getDefaults().put(className, OmoriRedHandBar.class);
    }
}
