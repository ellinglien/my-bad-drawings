package io.github.mpdairy.monopaint;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * The word bin along the foot of the page ({@link WordsMode}, {@link WordBinLayout}): a 3 dp ink rule; WORDS, the pack
 * and page ("Classic 3/14"), More words and Done; under them a page of tiles. The pen carries a tile out onto the page
 * ({@link Host#carry}); a tap on a tile picks it (ink-filled) for the pen's next landing, a tap on it again puts it
 * back; More words shows the next page, through every pack (imported packs first) and round again. Nothing scrolls.
 */
@SuppressLint("ViewConstructor")
final class WordBin extends LinearLayout {
    interface Host {
        /** The pick changed: {@code word}, or null for none. */
        void picked(String word);
        void done();
        /** Shows a change of the bin's pixels fast ({@link SelectionFeedback}). */
        void update(View view, Rect area, Runnable change);
        /**
         * The pen carrying {@code word} out of the bin at panel point {@code panelX, panelY}: it starts
         * ({@link MotionEvent#ACTION_DOWN}), moves, lifts or is cancelled ({@link WordsMode#carry}).
         */
        void carry(String word, int action, float panelX, float panelY);
    }
    /** How far the pen moves from where it landed on a tile, px, before it carries the tile rather than tapping it (2 mm). */
    static final float CARRY = 2 * MagnetDrag.STILL;
    private final WordBinLayout layout;
    private final MagnetTiles tiles;
    private final Host host;
    /** Each pack's pages, and the pack's name beside them. */
    private final List<List<String>> pages = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private final List<Integer> numbers = new ArrayList<>(), counts = new ArrayList<>();
    private int page, pick = -1;
    private final TextView caption;
    private final Tiles area;

    WordBin(Context context, WordBinLayout layout, List<WordPacks.Pack> packs, MagnetTiles tiles, Host host) {
        super(context);
        this.layout = layout; this.tiles = tiles; this.host = host;
        setOrientation(VERTICAL);
        setBackgroundColor(Theme.PAPER);
        for (WordPacks.Pack pack : packs) {
            List<List<String>> own = layout.pages(pack.tiles);
            for (int i = 0; i < own.size(); i++) { pages.add(own.get(i)); names.add(pack.name); numbers.add(i + 1); counts.add(own.size()); }
        }
        View rule = new View(context); rule.setBackgroundColor(Theme.INK);
        addView(rule, new LayoutParams(-1, layout.rule));
        LinearLayout header = LabUi.row(context);
        header.setPadding(layout.pad, layout.pad, layout.pad, 0);
        TextView label = LabUi.heading(context, "Words");
        header.addView(label);
        caption = LabUi.text(context, "", TypeStyle.CAPTION);
        caption.setTextColor(Theme.GREY_800); caption.setSingleLine(true); caption.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        LayoutParams captionPlace = new LayoutParams(0, -2, 1);
        captionPlace.leftMargin = Ui.dp(context, WordBinLayout.BUTTON_GAP);
        header.addView(caption, captionPlace);
        LabButton more = new LabButton(context, 0, "More words", false);
        more.setOnClickListener(v -> more());
        LabButton done = new LabButton(context, 0, "Done", true);
        done.setOnClickListener(v -> host.done());
        for (LabButton b : new LabButton[]{more, done}) {
            LayoutParams place = new LayoutParams(-2, layout.header);
            place.leftMargin = Ui.dp(context, WordBinLayout.BUTTON_GAP);
            header.addView(b, place);
        }
        more.setEnabled(pages.size() > 1);
        addView(header, new LayoutParams(-1, layout.pad + layout.header));
        area = new Tiles(context);
        addView(area, new LayoutParams(-1, layout.height - layout.rule - layout.pad - layout.header));
        showCaption();
    }
    /** The word picked, or null. */
    String picked() { return pick < 0 ? null : pages.get(page).get(pick); }
    /** Puts the picked word back (it was placed). */
    void unpick() { if (pick >= 0) choose(-1); }
    private void more() {
        if (pages.size() < 2) return;
        host.update(area, new Rect(0, 0, area.getWidth(), area.getHeight()), () -> {
            page = (page + 1) % pages.size(); pick = -1; area.invalidate();
        });
        host.picked(null);
        showCaption();
    }
    private void showCaption() { caption.setText(names.get(page) + " " + numbers.get(page) + "/" + counts.get(page)); }
    private void choose(int i) {
        int[][] at = layout.places(pages.get(page));
        Rect changed = new Rect();
        for (int k : new int[]{pick, i}) if (k >= 0) changed.union(at[k][0], at[k][1] - top(), at[k][2], at[k][3] - top());
        host.update(area, changed, () -> { pick = i; area.invalidate(); });
        host.picked(picked());
    }
    /** Where the tiles' area starts in the bin's own places ({@link WordBinLayout#places}). */
    private int top() { return layout.rule + layout.pad + layout.header; }

    /** The page of tiles; the pen carries one out, taps pick. */
    private final class Tiles extends View {
        /** The tile the pen landed on (-1: none, or not the pen), the word it carries (null: none) and where it landed. */
        private int held = -1;
        private String carried;
        private float downX, downY;
        private final Matrix toPanel = new Matrix();
        Tiles(Context context) { super(context); setContentDescription("Words"); }
        @Override protected void onDraw(Canvas canvas) {
            canvas.drawColor(Theme.PAPER);
            List<String> words = pages.get(page);
            int[][] at = layout.places(words);
            for (int i = 0; i < words.size(); i++) canvas.drawBitmap(tiles.picture(words.get(i), 0, 0, i == pick), at[i][0] - Magnets.EDGE, at[i][1] - top() - Magnets.EDGE, null);
        }
        @SuppressLint("ClickableViewAccessibility")
        @Override public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                held = DrawingPad.isPen(event, 0) ? tileAt(event.getX(), event.getY()) : -1;
                carried = null; downX = event.getX(); downY = event.getY();
                toPanel.set(PanelCoordinates.fromView(this));
                return true;
            }
            if (carried == null && held >= 0 && action == MotionEvent.ACTION_MOVE && Math.hypot(event.getX() - downX, event.getY() - downY) >= CARRY) {
                carried = pages.get(page).get(held);
                if (held == pick) choose(-1);
                carry(MotionEvent.ACTION_DOWN, event);
            }
            if (carried != null) {
                if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) carry(action, event);
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) { carried = null; held = -1; }
                return true;
            }
            if (action != MotionEvent.ACTION_UP) return true;
            int i = tileAt(event.getX(), event.getY());
            if (i >= 0) choose(i == pick ? -1 : i);
            return true;
        }
        private void carry(int action, MotionEvent event) {
            float[] p = {event.getX(), event.getY()};
            toPanel.mapPoints(p);
            host.carry(carried, action, p[0], p[1]);
        }
        /** The tile at this view's point {@code x, y}; -1 for none. */
        private int tileAt(float x, float y) {
            int[][] at = layout.places(pages.get(page));
            y += top();
            for (int i = 0; i < at.length; i++) if (x >= at[i][0] && x < at[i][2] && y >= at[i][1] && y < at[i][3]) return i;
            return -1;
        }
    }
}
