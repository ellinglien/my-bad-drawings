package io.github.mpdairy.monopaint;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * How a size swatch is set (spec "The toolbar", 1–2; ui.js renderPicker 353–374): the whole set at once, the Sizes
 * window ({@link #set}), over the middle of the screen, made of the size editor ({@link SizeEditor}; the size modal and
 * the grey flyout, each for one face, went on 2026-10-19, when a long press on a face took the Edit buttons' place and
 * opens this window on it; its grey editor went with the one grey, 2026-10-24). All are flat popups turned with the app (see {@link Popups}), square, a menu's
 * panel ({@link Theme#menu}), with no animation, so they work alike on the drawing screen and in Brush Lab. They only
 * show and call: what a change does is the caller's. Their sizes are {@link SwatchSetLayout}'s.
 */
final class SizeModal {
    private SizeModal() { }

    /** What the whole-set modal ({@link #set}) sets: a tool's four sizes. */
    interface Set {
        /** Size {@code i}'s px now; setting it. */
        double size(int i);
        void setSize(int i, double px);
    }

    /**
     * The whole set at once (the owner, 2026-10-19: "a way to customize the set below"), over the middle of the screen:
     * "Sizes" and Done; the four sizes as faces, the one being set tabbed, and under them the size modal's own editor for
     * it ({@link SizeEditor}). A size face chooses which one the editor sets; a stop, or where the slider stops, sets it
     * at once, its face following. It opens on size {@code size} (the one in use). Done, or a pen outside, closes it, and
     * {@code closed} gets what it covered. Its four greys went with the one grey (2026-10-24).
     */
    static PopupWindow set(View root, int turn, Set set, int size, Consumer<RectF> closed) {
        Context context = root.getContext();
        LinearLayout box = frame(context);
        LinearLayout head = LabUi.row(context);
        head.addView(LabUi.heading(context, "Sizes"), new LinearLayout.LayoutParams(0, -2, 1));
        LabButton done = new LabButton(context, 0, "Done", false);
        head.addView(done, LabUi.after(context, -2, 44, 8));
        box.addView(head, new LinearLayout.LayoutParams(-1, -2));
        int count = ToolSwatches.COUNT;
        SizeEditor editor = new SizeEditor(context, SwatchSetLayout.SET_VIEW);
        SwatchFace[] sizes = new SwatchFace[count];
        LinearLayout sizeRow = LabUi.row(context);
        for (int i = 0; i < count; i++) {
            int index = i;
            sizes[i] = SwatchFace.size(context, set.size(i), "Size " + (i + 1), () -> {
                // The size being set until now keeps where its slider was.
                editor.apply.run();
                for (int k = 0; k < count; k++) sizes[k].setPicked(k == index);
                editSize(editor, set, sizes, index);
            });
            sizes[i].setPicked(i == size);
            sizeRow.addView(sizes[i], face(context, i));
        }
        box.addView(sizeRow, below(context, -2, -2, SwatchSetLayout.ROW_GAP));
        box.addView(editor.value, below(context, -1, dp(context, SwatchSetLayout.VALUE), SwatchSetLayout.VALUE_GAP));
        box.addView(editor.view, new LinearLayout.LayoutParams(-1, -2));
        editSize(editor, set, sizes, size);
        return centred(root, turn, box, editor, done, closed);
    }
    /** The whole-set modal's editor set on size {@code i}: what it sets goes to {@code set} and shows on its face. */
    private static void editSize(SizeEditor editor, Set set, SwatchFace[] sizes, int i) {
        editor.edit("Size " + (i + 1), set.size(i), px -> { set.setSize(i, px); sizes[i].setDiameter(px); });
    }
    /** Face {@code i}'s place in a row of four, {@link SwatchSetLayout#FACE_GAP} dp after the one before. */
    private static LinearLayout.LayoutParams face(Context context, int i) {
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(dp(context, SwatchSetLayout.FACE), dp(context, SwatchFace.tall(SwatchSetLayout.FACE)));
        if (i > 0) place.leftMargin = dp(context, SwatchSetLayout.FACE_GAP);
        return place;
    }
    /** {@code box} over the middle of {@code root}, {@link SwatchSetLayout#width} wide; Done closes it, and the size {@code editor} was at still applies (closed mid-slide by a pen outside, too). */
    private static PopupWindow centred(View root, int turn, LinearLayout box, SizeEditor editor, LabButton done, Consumer<RectF> closed) {
        Context context = root.getContext();
        int width = Math.min(dp(context, SwatchSetLayout.WIDTH), root.getWidth() - 2 * dp(context, SwatchSetLayout.MARGIN));
        int height = Math.min(Popups.measuredHeight(box, width, 0), root.getHeight());
        RectF bounds = Popups.rect((root.getWidth() - width) / 2f, (root.getHeight() - height) / 2f, width, height);
        PopupWindow popup = show(root, turn, box, bounds, area -> { editor.apply.run(); closed.accept(area); });
        done.setOnClickListener(v -> popup.dismiss());
        return popup;
    }

    /**
     * The size modal's editor, pointed at one size at a time ({@link #edit}): the true-size dot, 1 : 1, clipped; a
     * slider from thin to big brush ({@link ToolSwatches#position}); Fine and Broad under it; the nine stops; and the
     * size's px and mm ({@link #value}, placed by the modal). While the slider moves only it and the px and mm are
     * drawn again; where it stops, or a tapped stop, shows in the dot and the stops and goes to the size being set, so
     * what the size shows elsewhere is drawn once, not on every move (e-ink).
     */
    private static final class SizeEditor {
        final LinearLayout view;
        final TextView value;
        /** Sends where the slider is, if it moved, to the size being set. */
        final Runnable apply = this::apply;
        private final TrueSize dot;
        private final BandSlider slider;
        private final LabButton[] stops = new LabButton[ToolSwatches.STOPS.length];
        private double now, applied;
        private DoubleConsumer set = px -> { };

        SizeEditor(Context context, int viewHeight) {
            view = LabUi.column(context);
            value = modalWords(context, "");
            // One line of a fixed height, so a new value never lays the modal out again (e-ink).
            value.setSingleLine(true); value.setGravity(Gravity.CENTER_VERTICAL);
            dot = new TrueSize(context);
            dot.setBackgroundColor(Theme.PAPER);
            view.addView(dot, below(context, -1, dp(context, viewHeight), SwatchSetLayout.ROW_GAP));
            slider = new BandSlider(context, 0, 1, 0, null);
            view.addView(slider, below(context, -1, dp(context, SwatchSetLayout.SLIDER), SwatchSetLayout.ROW_GAP));
            LinearLayout scale = LabUi.row(context);
            scale.addView(scaleLabel(context, "Fine"));
            LabUi.spring(scale);
            scale.addView(scaleLabel(context, "Broad"));
            view.addView(scale, new LinearLayout.LayoutParams(-1, -2));
            LabUi.Wrap chips = new LabUi.Wrap(context, dp(context, SwatchSetLayout.STOP_GAP));
            int side = dp(context, SwatchSetLayout.STOP);
            for (int s = 0; s < stops.length; s++) {
                double stop = ToolSwatches.STOPS[s];
                stops[s] = LabButton.chip(context, ToolSwatches.pxText(stop));
                stops[s].setMinHeight(side); stops[s].setMinimumHeight(side); stops[s].setMinWidth(side); stops[s].setMinimumWidth(side);
                stops[s].setOnClickListener(v -> { slider.value = ToolSwatches.position(stop); slider.invalidate(); to(stop); apply(); });
                chips.addView(stops[s]);
            }
            view.addView(chips, below(context, -1, -2, SwatchSetLayout.ROW_GAP));
            slider.listener = new BandSlider.Listener() {
                @Override public void started() { }
                @Override public void moved(double t) { double size = ToolSwatches.sizeAt(t); if (size != now) to(size); }
                @Override public void stopped() { apply(); }
            };
        }
        /** Points the editor at the size called {@code name}, now {@code px}; where it is set goes to {@code set}. */
        void edit(String name, double px, DoubleConsumer set) {
            this.set = set;
            now = px; applied = px;
            slider.value = ToolSwatches.position(px);
            slider.setContentDescription(name + ", fine to broad");
            slider.invalidate();
            to(px);
            show();
        }
        private void to(double size) { now = size; value.setText(ToolSwatches.pxText(now) + " px · " + ToolSwatches.mm(now)); }
        private void show() {
            if (dot.diameter != (float)now) { dot.diameter = (float)now; dot.invalidate(); }
            for (int s = 0; s < stops.length; s++) stops[s].setOn(ToolSwatches.STOPS[s] == now);
        }
        private void apply() { show(); if (now != applied) { applied = now; set.accept(now); } }
    }

    /** A picker's box: a menu's panel ({@link Theme#menu}: paper, a fine ink edge), 15 dp inside it; taps on it stay on it. */
    private static LinearLayout frame(Context context) {
        LinearLayout box = LabUi.column(context);
        box.setBackground(Theme.menu(context));
        int inside = dp(context, Theme.RULE + 12);
        box.setPadding(inside, inside, inside, inside);
        box.setClickable(true);
        return box;
    }
    private static PopupWindow show(View root, int turn, View box, RectF bounds, Consumer<RectF> closed) {
        PopupWindow popup = Popups.create(box, turn, true, Theme.PAPER);
        popup.setOnDismissListener(() -> closed.accept(bounds));
        Popups.show(popup, root, bounds);
        return popup;
    }
    private static TextView scaleLabel(Context context, String text) {
        return modalWords(context, text);
    }
    /**
     * The modal's own words (a value, Fine and Broad) in its face and ink, bold as its heading and chips are (the owner,
     * 2026-10-21: the same font as the rest), at caption size.
     */
    private static TextView modalWords(Context context, String text) {
        TextView label = LabUi.text(context, text, TypeStyle.CAPTION);
        label.setTypeface(Theme.face(700)); label.setTextColor(Theme.INK);
        return label;
    }
    private static LinearLayout.LayoutParams below(Context context, int width, int height, float gap) {
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(width, height);
        place.topMargin = dp(context, gap);
        return place;
    }
    private static int dp(Context context, float value) { return Ui.dp(context, value); }

    /** The size modal's view: the true-size dot, 1 : 1 in screen pixels, centred, clipped inside the view's 2 dp frame. */
    private static final class TrueSize extends View {
        private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        float diameter;
        TrueSize(Context context) { super(context); ink.setColor(Theme.INK); }
        @Override protected void onDraw(Canvas canvas) {
            int frame = Ui.dp(getContext(), Theme.BASE);
            canvas.clipRect(frame, frame, getWidth() - frame, getHeight() - frame);
            canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, Math.max(.8f, diameter / 2), ink);
        }
    }
}
