package bars;

import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import interfaces.IOmoriBar;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Paints every progress bar in the IDE as a dark track with a sprite riding its
 * leading edge.
 *
 * <p>Both modes are the same drawing with different inputs:
 * <ul>
 *   <li><b>determinate</b>   - fill width comes from the model, sprite sits at the edge</li>
 *   <li><b>indeterminate</b> - fill is the whole bar, sprite bounces left to right</li>
 * </ul>
 *
 * <p>Painting always happens on the EDT, so nothing here needs to be volatile.
 * More importantly this class holds <b>no animation state</b> - see {@link #phase()}.
 */
public class OmoriRedHandBar extends BasicProgressBarUI implements IOmoriBar {

    /**
     * Bar height, unscaled px. The platform default is ~4; this must be at least as
     * tall as the sprite (15px), because anything drawn above the bar's top edge falls
     * outside the component's bounds and gets clipped away.
     */
    private static final int BAR_HEIGHT = 18;

    /**
     * Corner radius, unscaled px.
     */
    private static final float ARC = 8f;

    // ------------------------------------------------------------------
    // Tunables. These configure THIS bar. Note they are static: a subclass
    // cannot override a static field (statics are not polymorphic), so for a
    // genuinely different bar override the protected paintFill / paintSprite
    // hooks below instead - that is the real per-bar seam.
    // ------------------------------------------------------------------

    private static final Color OMORI_BLACK = new Color(16, 16, 16);
    private static final Color OMORI_WHITE = new Color(236, 236, 236);
    private static final Color OMORI_RED = new Color(196, 32, 33);

    /** Barber-pole: the two stripe colours and the width of one stripe (unscaled px). */
    private static final Color STRIPE_LIGHT = OMORI_WHITE;
    private static final Color STRIPE_DARK = OMORI_BLACK;
    private static final int STRIPE_WIDTH = 7;

    /**
     * Loaded through this class so the icon resolves via the <em>plugin's</em>
     * classloader - the same isolation issue as the UIManager registration.
     * IconLoader picks up {@code redHand@2x.png} automatically on HiDPI screens;
     * never apply {@link JBUI#scale} to an icon's size on top of that, or you
     * scale twice.
     */
    private static final Icon HAND =
            IconLoader.getIcon("/icons/redHand.png", OmoriRedHandBar.class);

    // ------------------------------------------------------------------
    // Swing entry point
    // ------------------------------------------------------------------

    /**
     * Swing instantiates UI delegates reflectively through this exact static signature.
     * No interface declares it - it is a naming convention {@code UIManager} relies on,
     * which is why nothing in this plugin ever calls {@code new OmoriRedHandBar()}.
     */
    @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "unused"})
    public static ComponentUI createUI(JComponent c) {
        c.setBorder(JBUI.Borders.empty().asUIResource());
        return new OmoriRedHandBar();
    }

    @Override
    public String getName() {
        return "Red Hand";
    }

    @Override
    public Dimension getPreferredSize(JComponent c) {
        return new Dimension(super.getPreferredSize(c).width, JBUI.scale(BAR_HEIGHT));
    }

    // ------------------------------------------------------------------
    // The two slots BasicProgressBarUI asks us to fill
    // ------------------------------------------------------------------

    @Override
    protected void paintIndeterminate(Graphics g, JComponent c) {
        // Keep the sprite fully on screen: its Center travels between half an icon
        // in from each edge, rather than off the ends.
        int half = HAND.getIconWidth() / 2;
        int travel = Math.max(0, c.getWidth() - HAND.getIconWidth());

        float p = phase();                      // 0..1, from the parent's own clock
        float bounce = p < 0.5f ? p * 2f        // 0 -> 1 travelling right
                : 2f - p * 2f;  // 1 -> 0 travelling back

        paintBar(g, c, c.getWidth(), half + Math.round(bounce * travel), p < 0.5f, p);
    }

    @Override
    protected void paintDeterminate(Graphics g, JComponent c) {
        Insets in = progressBar.getInsets();
        int trackWidth = c.getWidth() - in.left - in.right;
        int trackHeight = JBUI.scale(BAR_HEIGHT) - in.top - in.bottom;
        if (trackWidth <= 0 || trackHeight <= 0) return;

        int filled = getAmountFull(in, trackWidth, trackHeight);
        // Determinate has no animation clock, so stripes stay put (phase 0); the fill
        // growing is the motion here.
        paintBar(g, c, filled, filled, true, 0f);
    }

    // ------------------------------------------------------------------
    // One drawing routine, two callers
    // ------------------------------------------------------------------

    /**
     * @param fillWidth     how much of the track is colored in, px
     * @param spriteCenterX where the sprite's Center sits, px from the left
     * @param facingRight   sprite orientation
     * @param phase         0..1 animation position, used to scroll the fill pattern
     */
    private void paintBar(Graphics graphics, JComponent c, int fillWidth, int spriteCenterX,
                          boolean facingRight, float phase) {
        int w = c.getWidth();
        int h = JBUI.scale(BAR_HEIGHT);
        if (w <= 0 || h <= 0) return;

        // Work on a scratch copy: transforms, clips and hints we set are thrown away
        // on dispose(), so an exception mid-paint cannot corrupt the Graphics that the
        // rest of the IDE is still painting with.
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Erase to the parent's colour first, then lay the rounded track on top.
            // Anti-aliasing blends the corners into this, so we need no shape
            // subtraction to fake transparency.
            g.setColor(backgroundOf(c));
            g.fillRect(0, 0, w, c.getHeight());

            // Center vertically - everything below is drawn in "bar space".
            g.translate(0, (c.getHeight() - h) / 2);

            float arc = JBUI.scale(ARC);
            g.setColor(OMORI_BLACK);
            g.fill(new RoundRectangle2D.Float(0, 0, w, h, arc, arc));

            // The filled portion, painted by an overridable hook. Rounded so it
            // matches the track; the hook clips its pattern to exactly this shape.
            RoundRectangle2D fill = new RoundRectangle2D.Float(0, 0, Math.max(fillWidth, arc), h, arc, arc);
            paintFill(g, fill, w, h, phase);

            // Center the sprite on both axes from its real size, so swapping in a
            // different icon never needs the offsets retuned.
            paintSprite(g, c,
                    spriteCenterX - HAND.getIconWidth() / 2,
                    (h - HAND.getIconHeight()) / 2,
                    facingRight);
        } finally {
            g.dispose();
        }
    }

    // ------------------------------------------------------------------
    // Override these two, not the paint methods above
    // ------------------------------------------------------------------

    /**
     * Paints the filled portion of the track. Override for a different bar's look.
     *
     * <p>Default is a scrolling barber-pole: a light base with diagonal dark stripes,
     * offset by {@code phase} so they crawl. Black and white on purpose - the red hand
     * is meant to be the only red on the bar, so it stays visible over either stripe.
     *
     * @param fillShape the exact (rounded) region to paint; clip your pattern to it
     */
    protected void paintFill(Graphics2D g, Shape fillShape, int w, int h, float phase) {
        g.setColor(STRIPE_LIGHT);
        g.fill(fillShape);

        Graphics2D s = (Graphics2D) g.create();
        try {
            s.clip(fillShape);                 // keep stripes inside the rounded fill
            s.setColor(STRIPE_DARK);

            int stripe = JBUI.scale(STRIPE_WIDTH);
            int period = stripe * 2;           // one dark + one light
            float scroll = phase * period;     // advance exactly one period per cycle

            // 45-degree bands: the top edge is shifted right by h relative to the
            // bottom edge, which is what tilts each parallelogram. Start well left of
            // 0 so the slant still covers the top-left corner.
            for (double x = -h - period; x < w + period; x += period) {
                double sx = x + scroll;
                Path2D.Double band = new Path2D.Double();
                band.moveTo(sx, h);
                band.lineTo(sx + stripe, h);
                band.lineTo(sx + stripe + h, 0);
                band.lineTo(sx + h, 0);
                band.closePath();
                s.fill(band);
            }
        } finally {
            s.dispose();
        }
    }

    /**
     * Draws the sprite, mirroring it horizontally when travelling left.
     *
     * <p>The flip is lossless - no resampling - so one source image covers both
     * directions and the pixel art stays crisp.
     *
     * <p>Order matters: {@code scale(-1, 1)} mirrors about x=0, which would throw the
     * icon into negative x and off the left of the bar. Translating to its right edge
     * first means the flip lands it back exactly where it started.
     */
    protected void paintSprite(Graphics2D g, JComponent c, int x, int y, boolean facingRight) {
        if (facingRight) {
            HAND.paintIcon(c, g, x, y);
            return;
        }
        Graphics2D mirrored = (Graphics2D) g.create();
        try {
            mirrored.translate(x + HAND.getIconWidth(), y);
            mirrored.scale(-1, 1);
            HAND.paintIcon(c, mirrored, 0, 0);
        } finally {
            mirrored.dispose();
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Position in the animation cycle, 0..1.
     *
     * <p>This is why the class has no mutable state. {@link BasicProgressBarUI} already
     * runs a repaint timer for indeterminate bars and counts frames; deriving position
     * from that counter makes painting a pure function of (frame, width).
     *
     * <p>Incrementing a field inside paint() instead - as Nyan does - couples sprite
     * speed to however often the OS happens to ask for a redraw, so a resize or an
     * uncovered window makes the sprite jump.
     *
     * <p>Tune speed with the {@code ProgressBar.cycleTime} UIDefaults key.
     */
    private float phase() {
        int frames = Math.max(1, getFrameCount());
        return (getAnimationIndex() % frames) / (float) frames;
    }

    private static Color backgroundOf(JComponent c) {
        Container parent = c.getParent();
        return parent != null ? parent.getBackground() : UIUtil.getPanelBackground();
    }

    /**
     * The indeterminate "box" is normally a small sliding block. We paint the full
     * width ourselves, so report the whole length.
     */
    @Override
    protected int getBoxLength(int availableLength, int otherDimension) {
        return availableLength;
    }

}
