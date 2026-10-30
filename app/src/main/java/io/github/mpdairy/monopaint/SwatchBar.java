package io.github.mpdairy.monopaint;

import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

/**
 * The selected tool's size and grey, in the drawing screen's side toolbar ({@link InkWashBar}), always there, so nothing
 * drops down: on top the readout, one dot of the size and the grey in use ({@link SwatchReadout}; the owner, 2026-10-24:
 * "it can be one thing", under the pickers until the owner's sketch the same day, "DO IT THIS WAY"); under it the picker
 * box, the owner's drawing (2026-10-27: "i'd like to try using it to replace the shade picker and size select"), a box
 * two cells wide cut by a diagonal from its top left to its foot right: its lower-left triangle the grey strip, white at
 * the top to black at the foot, its tab left of the box ({@link GreyStrip}, the owner, 2026-10-21), its upper-right one
 * the size slider, broad at the top to fine at the foot, its tab right of the box ({@link SizeSlider}; the owner,
 * 2026-10-24: "change the four size options to a quick size slider. I find myself wanting to fine tune the size
 * often"), {@link DrawLayout#BOX_ROWS} rows of faces long where the column has room; mirrored with the column on the right. The two views lie over
 * each other, the whole box each ({@link PickerBox}, Brush Lab's too); a pen goes to the one whose triangle it lands in ({@link DrawLayout#boxShade}). Side
 * by side until then, the strip on the left (the owner, 2026-10-27: "move the size selector to the right side").
 * The Dropper ({@link DrawLayout#DROPPER}; "grey swatches can go ... add a dropper tool so people can quickly match
 * color") sits in the icon block beside the Lasso, always shown (the owner, 2026-10-26: hidden with the strip, its cell looked empty) ({@link InkWashBar}; the
 * owner, 2026-10-24: "lasso can move beside picker", the wet indicator gone to the brush line). Until 2026-10-24 the four size faces sat
 * where the slider is and the four grey swatches two by two under them; the Tools page keeps its four
 * sizes as the shared presets ({@link SwatchFaces}), none marked while the size is one of the slider's own.
 * <p>
 * The slider sets, on the lift, the picked tool's own size (one for every brush from 2026-10-25 to 27,
 * {@link ToolSwatches#useSize}); while the pen is on it only its tab moves (no preview on the page since the
 * owner's "the redraw is blinky and slow", 2026-10-24). A tap or a drag on the strip picks the grey there as the
 * picked tool's own ({@link ToolSwatches#useGrey}; one for every brush from 2026-10-24 to 27); a rub on it loads the brush (the oil
 * paint spec). The strip's long press opens Opacity for opaque paint (2026-10-27, {@link InkWashBar}); the slider has
 * none: the Sizes window is the Tools page's. The eraser and the smudge tool show their size
 * only; the strip and the grey's square are hidden, keeping their place, so nothing moves. Sizes are {@link DrawLayout}'s. A
 * change is put on the panel over each one's own patch only (the strip's tab, the slider's tab, the dot; {@link
 * SelectionFeedback}), with no Android frame, and only when what they show changed (e-ink).
 */
final class SwatchBar {
    final LinearLayout view;
    /** The Dropper, placed beside the Lasso, always shown (hidden with the strip until 2026-10-26); its tap is the bar's ({@link InkWashBar}). */
    final ToolButton dropper;
    private final PaintActivity app;
    private final SizeSlider slider;
    private final GreyStrip strip;
    /** The picker box, the strip and the slider over each other in it, each the whole box. */
    private final PickerBox box;
    private final SwatchReadout readout;
    /** What the slider and the strip show, to put them on the panel again only when it changes. */
    private String shown;
    /** The tool shown. */
    private int tool;

    /** {@code hold}: the strip's long press (Opacity, {@link InkWashBar}). */
    SwatchBar(PaintActivity app, SwatchFaces.Host host, ToolButton dropper, Runnable hold) {
        this.app = app;
        this.dropper = dropper;
        view = LabUi.column(app);
        view.setGravity(Gravity.CENTER_HORIZONTAL);
        ToolSwatches swatches = app.inkWash.slots.swatches;
        // The dot on top, the picker box under it (the owner's drawing, 2026-10-27: "i'd like to try using it to replace the
        // shade picker and size select"): the strip its lower-left triangle, the slider over it its upper-right one, each tab
        // on its outer side (side by side, the strip on the left, from 2026-10-24 to 27).
        box = new PickerBox(app, app.selectionFeedback, new SizeSlider.Listener() {
            @Override public boolean ready() { return host.ready(); }
            @Override public void set(double t) {
                swatches.useSize(InkWash.key(tool), ToolSwatches.sizeAt(t));
                host.changed(true);
                view.announceForAccessibility(DrawLayout.sizeSaid(app.inkWash.look(tool).px));
            }
        }, darkness -> {
            if (!host.ready()) return;
            swatches.useGrey(InkWash.key(tool), darkness);
            host.changed(true);
            host.greyPicked(-1);
            Brush brush = app.inkWash.brush(tool);
            if (brush != null) view.announceForAccessibility(ToolSwatches.greyWords(darkness, ModularSizes.swatch(brush.parts, darkness)));
        }, hold,
                // A rub loads the brush with the grey in use (the oil paint spec).
                travel -> { if (host.ready()) host.rubbed(travel); });
        slider = box.slider; strip = box.strip;
        readout = new SwatchReadout(app);
        view.addView(readout);
        view.addView(box);
    }

    /**
     * Lays them out at {@code layout}'s sizes: the box two cells wide ({@link DrawLayout#half}), as the strip's and the
     * slider's were side by side, {@link DrawLayout#box} tall, the readout {@link
     * DrawLayout#READOUT_ROWS} over it; mirrored with the column on the right ({@code right}), the strip's triangle and
     * tab on the column's outer side. The Dropper is placed by the bar.
     */
    void apply(DrawLayout layout, boolean right) {
        int cell = dp(layout.half()), tall = dp(layout.face);
        box.setLayoutParams(new LinearLayout.LayoutParams(2 * cell, dp(layout.box)));
        readout.setLayoutParams(new LinearLayout.LayoutParams(2 * cell, tall * DrawLayout.READOUT_ROWS));
        if (strip.mirrored == right) return;
        strip.mirrored = slider.mirrored = right;
        strip.invalidate(); slider.invalidate();
    }

    /**
     * Shows tool {@code tool}'s size and grey (a slot; the eraser or the smudge tool: its size only) as it draws them;
     * put on the panel over them, and only when they changed.
     */
    void show(int tool) {
        Brush brush = app.inkWash.brush(tool);
        ToolSwatches.Look look = app.inkWash.look(tool);
        double inUse = brush == null ? Double.NaN : look.darkness;
        String now = InkWash.key(tool) + " " + (brush == null ? "" : brush.identity()) + " " + ToolSwatches.position(look.px) + " " + inUse;
        if (now.equals(shown)) return;
        boolean first = shown == null;
        shown = now;
        this.tool = tool;
        boolean hasGreys = DrawLayout.greys(tool);
        Runnable dot = () -> readout.show(look, brush, hasGreys);
        // The eraser or the smudge tool to or from a slot: the strip drawn as paper or shown, in the same update, with no
        // Android frame (a visibility change drew the whole column again, 2026-10-25).
        int access = hasGreys ? View.IMPORTANT_FOR_ACCESSIBILITY_AUTO : View.IMPORTANT_FOR_ACCESSIBILITY_NO;
        Runnable change = () -> { strip.hidden = !hasGreys; strip.setImportantForAccessibility(access); box.show(look, brush, hasGreys); dot.run(); };
        if (first || view.getWidth() <= 0) {
            change.run();
            for (View each : new View[]{strip, slider, readout}) each.invalidate();
            return;
        }
        // Through the fast display, as MonoPaint's color bar marker, one update over what changed (the tabs, the dot, so its
        // dots show the grey in use: the owner, 2026-10-24, "on the manta the dot color doesn't reflect the shade
        // selected"), and no Android frame now. The tabs and the dot are ink and paper, so it is the panel's binary
        // update, no grey flash (the owner, 2026-10-24: "size picker is still blinking"). The strip shown or hidden too,
        // gathered into a tool tap's one update of the column (the owner, 2026-10-24: "switching to eraser, brush selector
        // blinked": it was laid out and drawn again through Android, the whole column with it).
        app.selectionFeedback.updateBinary(view, new Rect(0, 0, view.getWidth(), view.getHeight()), change);
    }
    /** Where the strip's page shade offer goes beside. */
    View strip() { return strip; }

    private int dp(float value) { return Ui.dp(app, value); }
}
