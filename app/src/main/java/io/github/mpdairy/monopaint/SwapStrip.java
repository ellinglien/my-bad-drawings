package io.github.mpdairy.monopaint;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Brush Lab's swap panel (ui.js swapList/renderSwap, 425–453): a part's choices, each a tile drawn with the lab's brush
 * with only that part changed ({@link BrushLab#swaps}), as that part's wave ({@link WildSample#part}: choosing is
 * adjusting a part, not picking a brush), paper tiles parted by thin ink lines, the current one's ink tab under it, in a
 * menu's panel ({@link Theme#menu}: it opens over the page). A small modal beside the part's module (the owner's
 * sketch, 2026-10-17: the module on the left, its choices next to it; {@link BrushLabActivity} shows it in a popup), as
 * wide as the test area and as tall as its tiles, which are sized as the old strip's under the test page
 * ({@link LabLayout#swapCell}). A tap puts that choice in (an Undo step) and closes it, as do Done and a tap outside it.
 * No hint text. Only what changed is drawn again. More tiles than {@link LabLayout#SWAP_MOST} turn in pages, as the
 * library's ({@link LabPages}: the head's ‹ 1/2 ›), each page's grid the same size; it opens at the current choice's.
 */
@SuppressLint("ViewConstructor")
final class SwapStrip extends LinearLayout {
    private final BrushLab lab;
    private final SampleQueue samples;
    private final LabLayout layout;
    /** Before a change to the lab (ends a test stroke), and closing it (a pick, ✕), which shows the lab as it now stands. */
    private final Runnable finish, close;
    private BrushPart part;
    /** The tiles' choices, to lay them out again only when those change; the tiles; the brush their samples show (a copy). */
    private String shown;
    private final List<LoopCard> tiles = new ArrayList<>();
    private ModularBrush samplesShown;
    /** The page shown, and the head as shown (its part and page), to draw it again only when that changes. */
    private int page;
    private String headShown;

    SwapStrip(Context context, BrushLab lab, SampleQueue samples, LabLayout layout, Runnable finish, Runnable close) {
        super(context);
        this.lab = lab; this.samples = samples; this.layout = layout; this.finish = finish; this.close = close;
        setOrientation(VERTICAL);
        setBackground(Theme.menu(context));
        int rule = dp(Theme.BASE);
        setPadding(rule, 0, rule, rule);
        // Taps on it stay on it.
        setClickable(true);
    }

    /**
     * Shows {@code part}'s choices for the lab's brush. The tiles are laid out again only when the part or its choices
     * change; otherwise each shows its caption and frame in place, and its sample anew only for a new brush or another
     * choice of the part ({@link BrushLab#redraw}): a slide draws none of them again (e-ink).
     */
    void render(BrushPart part) {
        List<BrushLab.Swap> all = lab.swaps(part);
        int perPage = LabLayout.swapPerPage(all.size()), pages = LabPages.pages(all.size(), perPage);
        if (part != this.part) { page = 0; for (int i = 0; i < all.size(); i++) if (all.get(i).now) page = LabPages.of(i, perPage); }
        page = LabPages.clamp(page, all.size(), perPage);
        List<BrushLab.Swap> swaps = all.subList(page * perPage, Math.min(all.size(), (page + 1) * perPage));
        List<List<String>> captions = new ArrayList<>();
        StringBuilder now = new StringBuilder(part.name()).append(' ').append(page);
        for (BrushLab.Swap swap : swaps) {
            captions.add(caption(swap, part));
            now.append(' ').append(swap.choice);
        }
        boolean samples = part != this.part || !now.toString().equals(shown);
        this.part = part;
        String head = part.name() + " " + page + "/" + pages;
        if (!head.equals(headShown)) { headShown = head; renderHead(pages); shown = null; }
        if (!now.toString().equals(shown)) {
            shown = now.toString();
            while (getChildCount() > 1) removeViewAt(1);
            renderTiles(captions, perPage);
        }
        ModularBrush b = lab.parts();
        BrushLab.Redraw redraw = BrushLab.redraw(samplesShown, b);
        samples |= redraw.all || samplesShown != null && samplesShown.choice(part) != b.choice(part);
        if (samples) samplesShown = b.copy();
        for (int i = 0; i < swaps.size(); i++) show(tiles.get(i), swaps.get(i), captions.get(i), samples);
    }

    /** Its header, the one header style (no ink bar): the glyph, the part's name, the page buttons when it has {@code pages} (the library's), and Done as plain words. */
    private void renderHead(int pages) {
        removeAllViews();
        LinearLayout head = LabUi.bar(getContext());
        ImageView glyph = new ImageView(getContext());
        glyph.setImageResource(PartCard.glyph(part));
        glyph.setColorFilter(Theme.INK);
        head.addView(glyph, new LayoutParams(dp(20), dp(20)));
        TextView title = LabUi.barText(getContext(), part.label);
        LayoutParams titlePlace = new LayoutParams(0, -2, 1); titlePlace.leftMargin = dp(8);
        head.addView(title, titlePlace);
        if (pages > 1) {
            BrushPart shownPart = part;
            LabButton previous = LabButton.headIcon(getContext(), R.drawable.ic_ph_caret_left, "Previous page");
            previous.setEnabled(page > 0);
            previous.setOnClickListener(v -> { page--; render(shownPart); });
            head.addView(previous, LabUi.after(getContext(), dp(layout.pageButton), 28, 8));
            TextView count = LabUi.barText(getContext(), (page + 1) + "/" + pages);
            count.setPadding(dp(4), 0, dp(4), 0);
            head.addView(count);
            LabButton next = LabButton.headIcon(getContext(), R.drawable.ic_ph_caret_right, "Next page");
            next.setEnabled(page < pages - 1);
            next.setOnClickListener(v -> { page++; render(shownPart); });
            head.addView(next, LabUi.after(getContext(), dp(layout.pageButton), 28, 0));
        }
        LabButton done = new LabButton(getContext(), 0, "Done", false);
        done.setMinHeight(0); done.setMinimumHeight(0);
        done.setOnClickListener(v -> close.run());
        head.addView(done, new LayoutParams(-2, dp(28)));
        addView(head, new LayoutParams(-1, dp(LabLayout.SECTION_BAR)));
    }

    /**
     * The tiles, {@link LabLayout#swapColumns} across so each is about 1.35 : 1, {@link Theme#FINE} dp ink lines between
     * them and over them (no grey in a menu, and e-ink greys render poorly); never
     * taller than wide (Life's three would be, on the Manta); a page laid out for {@code slots} tiles, so every page is
     * the same size. The panel ends under them.
     */
    private void renderTiles(List<List<String>> captions, int slots) {
        tiles.clear();
        int n = captions.size(), columns = layout.swapColumns(slots), rows = (slots + columns - 1) / columns, height = dp(layout.swapCell(slots)[1]);
        LinearLayout grid = LabUi.column(getContext());
        for (int r = 0; r < rows; r++) {
            LinearLayout row = LabUi.row(getContext());
            for (int c = 0; c < columns; c++) {
                int index = r * columns + c;
                View cell;
                if (index < n) {
                    LoopCard tile = new LoopCard(getContext(), samples, captions.get(index), null);
                    tile.inkCaption = true;
                    tile.ruled = true;
                    tile.setLeast(layout.swapLeast());
                    tile.setInset(layout.swapInset());
                    tiles.add(tile); cell = tile;
                }
                else { cell = new View(getContext()); cell.setBackgroundColor(Theme.PAPER); }
                LayoutParams place = new LayoutParams(0, -1, 1);
                // A line between tiles, as the library's grid.
                if (c < columns - 1) place.rightMargin = dp(Theme.FINE);
                row.addView(cell, place);
            }
            LayoutParams rowPlace = new LayoutParams(-1, height);
            if (r < rows - 1) rowPlace.bottomMargin = dp(Theme.FINE);
            grid.addView(row, rowPlace);
        }
        grid.setBackgroundColor(Theme.INK);
        grid.setPadding(0, dp(Theme.FINE), 0, 0);
        addView(grid, new LayoutParams(-1, -2));
    }
    /** A tile's caption: the choice, then its note ({@link ModularWords#swapNote}) when it has one. */
    private static List<String> caption(BrushLab.Swap swap, BrushPart part) {
        List<String> caption = new ArrayList<>();
        caption.add(ModularWords.tileLabel(swap.choice));
        String note = ModularWords.swapNote(swap.brush.parts, part);
        if (!note.isEmpty()) caption.add(note);
        return caption;
    }
    /** {@code swap} on {@code tile}: its caption, its sample if {@code sample} (the swap's brush is a copy of its own), its tab, and its tap. */
    private void show(LoopCard tile, BrushLab.Swap swap, List<String> caption, boolean sample) {
        BrushPart part = this.part;
        ModularBrush b = swap.brush.parts;
        tile.setCaption(caption);
        tile.setContentDescription(part.label + ": " + String.join(", ", caption));
        if (sample) tile.setSample(part + " " + b.key(), () -> (w, h) -> WildSample.part(b, part, w, h));
        tile.setNow(swap.now);
        tile.setOnClickListener(v -> {
            if (swap.now) return;
            finish.run();
            lab.choose(part, swap.choice);
            close.run();
        });
    }
    private int dp(float value) { return Ui.dp(getContext(), value); }
}
