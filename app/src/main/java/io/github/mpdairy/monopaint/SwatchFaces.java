package io.github.mpdairy.monopaint;

import android.content.Context;
import android.graphics.RectF;
import android.view.View;
import android.widget.PopupWindow;

/**
 * A tool's four size faces, bound to the shared {@link ToolSwatches}: a tap picks one; a long press on a brush's opens
 * the Sizes window ({@link SizeModal#set}) on that face, so all four sizes are set from one long press (the owner,
 * 2026-10-19: "instead of edit buttons, we use long press as a way to customize"). The same on the Tools page and over
 * Brush Lab's test page until its picker box, 2026-10-29 (the drawing screen's bar had them until 2026-10-24, when its size slider took their place,
 * {@link SwatchBar}): each places the faces and says, as its {@link Host}, what a change shows. A tap sets the size
 * of the tool {@code key} names (its own since 2026-10-27; the eraser's own S, M, L and XL gone 2026-10-25). The four
 * grey faces went 2026-10-24: the grey bar beside the sizes sets the tool's grey.
 */
final class SwatchFaces {
    interface Host {
        /** Before any change: ends the pen's stroke; false when nothing may change now. */
        boolean ready();
        /** A swatch changed: {@code picked}, a face tapped; else set in the Sizes window. */
        void changed(boolean picked);
        /** The drawing screen's grey strip was tapped (-1: no face), after {@link #changed}. */
        default void greyPicked(int i) {}
        /**
         * The grey strip was rubbed {@code travel} screen px (the oil paint spec), after {@link #changed}: whether it
         * loaded the brush in use ({@link LoadedPaint#rub}). One that didn't was a tap ({@link #greyPicked}).
         */
        default boolean rubbed(double travel) { return false; }
        /** The Sizes window opened ({@code area} null), or closed, having covered {@code area} (the root's app coordinates). */
        void covered(RectF area);
    }

    final SwatchFace[] sizes;
    private final ToolSwatches swatches;
    private final View root;
    private int turn;
    private final Host host;
    /** The key of the tool whose size the faces show and set ({@link ToolSlots#key}). */
    private final java.util.function.Supplier<String> key;
    private PopupWindow picker;

    /** Faces for {@code swatches}; the Sizes window opens over {@code root}, turned {@code turn}. */
    SwatchFaces(Context context, ToolSwatches swatches, View root, int turn, java.util.function.Supplier<String> key, Host host) {
        this.swatches = swatches; this.root = root; this.turn = turn; this.key = key; this.host = host;
        sizes = new SwatchFace[InkWashSizes.Preset.values().length];
        for (int i = 0; i < sizes.length; i++) {
            int index = i;
            sizes[i] = SwatchFace.size(context, 0, "Size " + InkWashSizes.Preset.values()[i].label, () -> pick(index));
        }
    }

    /** The pickers open turned {@code turn} from now on (the app turned). */
    void turn(int turn) { this.turn = turn; }
    /**
     * The faces show the four sizes and the current one (none when it is one of its own, the drawing screen's slider's,
     * {@link ToolSwatches#sizeInUse}); a long press sets them.
     */
    void show() {
        SwatchFace.show(sizes, swatches, swatches.sizeInUse(key.get()));
        for (int i = 0; i < sizes.length; i++) { int index = i; sizes[i].editable(() -> editAll(index)); }
    }
    /** The Sizes window ({@link SizeModal#set}): the four sizes, opening on size {@code size} (a long press's face); each change applies at once. */
    void editAll(int size) {
        if (!host.ready()) return;
        dismiss();
        picker = SizeModal.set(root, turn, new SizeModal.Set() {
            @Override public double size(int i) { return swatches.size(i); }
            @Override public void setSize(int i, double px) { swatches.setSize(key.get(), i, px); host.changed(false); }
        }, size, this::closed);
        host.covered(null);
    }
    /** Whether the Sizes window is open. */
    boolean open() { return picker != null; }
    /** Closes the Sizes window, if open. */
    void dismiss() {
        PopupWindow open = picker;
        picker = null;
        if (open != null) open.dismiss();
    }

    private void pick(int i) {
        if (!host.ready()) return;
        swatches.chooseSize(key.get(), i);
        host.changed(true);
    }
    private void closed(RectF area) {
        picker = null;
        host.covered(area);
    }
}
