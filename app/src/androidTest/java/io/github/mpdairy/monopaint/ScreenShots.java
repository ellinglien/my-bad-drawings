package io.github.mpdairy.monopaint;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Pictures of Brush Lab, the Tools page and the drawing screen's side toolbar at the Manta's size and at the Nomad's
 * (its 1404 × 1872 panel, through {@link PanelPreviewLayout}, the drawing screen's own simulation, put round the lab's
 * and the Tools page's content on this tablet): every state a screen has that changes its layout; and ({@link #app})
 * every other screen, menu and dialog of the drawing app. Runs in the bench build or the isolated checks build
 * ({@code -PisolatedChecks -PfastChecks}), each with its own library and settings, never the owner's app.
 * {@code -e screens true}, {@code -e only lab|tools|draw|menus|app|font} (font: {@link #FONT}'s Manta screens alone); the PNGs land in that build's external files,
 * {@code screens/}.
 */
final class ScreenShots {
    private final Instrumentation test;
    private final File out;
    private final StringBuilder report;
    /** Whether the screen is asleep, so the drawing screen is laid out here ({@link #laidOut}). */
    private boolean offScreen;
    /** The font mockup's few screens (-e only font, 2026-10-26), or null for every step. */
    private static final String[] FONT = {"draw-manta", "draw-manta-brush-name", "menu-manta-file", "menu-manta-tool-picker",
            "shelf-manta", "dialog-manta-rename", "dialog-manta-import-pdf"};
    private java.util.Set<String> picked;

    private ScreenShots(Instrumentation test, StringBuilder report) {
        this.test = test; this.report = report;
        out = new File(test.getTargetContext().getExternalFilesDir(null), "screens");
        out.mkdirs();
        for (File f : out.listFiles()) f.delete();
    }

    static void run(Instrumentation test, StringBuilder report, String only) throws Exception {
        ScreenShots s = new ScreenShots(test, report);
        // Android draws every screen, never the pen service's direct path: a picture shows it all, and a test page never
        // waits on a pen service another app holds (Brush Lab and the Tools page stalled past the 15 s focus limit).
        DirectEink.useAndroidDrawing(true);
        // PrintScreen's tables, made once on first use, take longer than that limit; made here, off the main thread.
        Class.forName(PrintScreen.class.getName(), true, PrintScreen.class.getClassLoader());
        if ("font".equals(only)) { s.picked = new java.util.HashSet<>(java.util.Arrays.asList(FONT)); s.app(); return; }
        for (boolean nomad : new boolean[]{true, false}) {
            String size = nomad ? "nomad" : "manta";
            if (only == null || only.contains("lab")) s.lab(size, nomad);
            if (only == null || only.contains("tools")) s.tools(size, nomad);
        }
        if (only == null || only.contains("draw")) s.drawing();
        if (only == null || only.contains("menus")) s.menus();
        if (only == null || only.contains("app")) s.app();
    }

    // Brush Lab

    private void lab(String size, boolean nomad) throws Exception {
        // As the Tools page opens it (its Back reads "Tools"): a start that doesn't read is the slots as they are.
        Activity lab = launch(new Intent(test.getTargetContext(), BrushLabActivity.class).putExtra(BrushLabActivity.SLOT, 1).putExtra(BrushLabActivity.START, new String[0]), nomad);
        try {
            {
                String mode = size;
                settle(2500);
                shot(lab, "lab-" + mode);
                for (BrushPart part : BrushPart.SHOWN) {
                    main(() -> call(lab, "tapped", new Class<?>[]{BrushPart.class}, part));
                    settle(2500);
                    shot(lab, "lab-" + mode + "-" + part.label.toLowerCase(java.util.Locale.ROOT));
                    main(() -> { call(lab, "tapped", new Class<?>[]{BrushPart.class}, (Object) null); call(lab, "closeSwap", new Class<?>[0]); });
                    settle(300);
                }
                {
                    main(() -> call(lab, "showSheet", new Class<?>[]{boolean.class}, true));
                    settle(800);
                    shot(lab, "lab-" + mode + "-open");
                    main(() -> call(lab, "showSheet", new Class<?>[]{boolean.class}, false));
                    settle(300);
                }
                Object settings = get(lab, "settings");
                if (get(settings, "styleButton") != null) {
                    main(() -> call(settings, "showFlyout", new Class<?>[]{boolean.class}, true));
                    settle(800);
                    shot(lab, "lab-" + mode + "-styles");
                    main(() -> call(settings, "showFlyout", new Class<?>[]{boolean.class}, false));
                    settle(300);
                }
                {
                    // Randomize: the slot changed (its dot, Back to the slot's brush), then Undo.
                    Object state = get(lab, "lab");
                    main(() -> { call(state, "randomize", new Class<?>[0]); call(lab, "render", new Class<?>[0]); });
                    settle(2500);
                    shot(lab, "lab-" + mode + "-changed");
                    main(() -> { call(state, "undo", new Class<?>[0]); call(lab, "render", new Class<?>[0]); });
                    settle(300);
                    // Renaming: the name a field, with Done.
                    main(() -> { field(lab.getClass(), "renaming").setBoolean(lab, true); call(lab, "render", new Class<?>[0]); });
                    settle(800);
                    shot(lab, "lab-" + mode + "-rename");
                    main(() -> { field(lab.getClass(), "renaming").setBoolean(lab, false); call(lab, "hideKeyboard", new Class<?>[]{View.class}, lab.getWindow().getDecorView()); call(lab, "render", new Class<?>[0]); });
                    settle(300);
                }
            }
        } finally {

            main(lab::finish);
            settle(500);
        }
    }

    // The Tools page

    /**
     * The Tools page at the Manta's size and the Nomad's, portrait and landscape, on each tab; then a Basic tool tapped
     * (it stays, underlined), a stroke drawn on the scratch pad with it, and Undo after it. Laid out and drawn off the screen
     * at the screen's size in dp ({@link #page}), so the panel may sleep and the Nomad needs no simulation.
     */
    private void tools(String size, boolean nomad) throws Exception {
        for (int turn : new int[]{0, 90}) {
            String name = "tools-" + size + (turn == 0 ? "-portrait" : "-landscape");
            int shortSide = nomad ? 749 : 1024, longSide = nomad ? 998 : 1365;
            int w = turn == 0 ? shortSide : longSide, h = turn == 0 ? longSide : shortSide;
            Activity tools = launch(new Intent(test.getTargetContext(), ToolsActivity.class).putExtra(ToolsActivity.SLOT, 1), false);
            try {
                Object page = get(tools, "page");
                // The Tools tab under All, as a first visit (the page remembers the last tab and chip); each other tab;
                // a tab under a chip it has nothing of (the empty grid).
                Object[][] views = {{ToolsPage.Tab.BASIC, ToolsPage.Chip.ALL, ""}, {ToolsPage.Tab.RECENT, ToolsPage.Chip.ALL, "-recent"}, {ToolsPage.Tab.SAVED, ToolsPage.Chip.ALL, "-saved"},
                        {ToolsPage.Tab.BASIC, ToolsPage.Chip.WASHES, "-basic-washes"}, {ToolsPage.Tab.BASIC, ToolsPage.Chip.DRY_MEDIA, "-basic-dry-media"},
                        {ToolsPage.Tab.SAVED, ToolsPage.Chip.PRINT, "-saved-print"}, {ToolsPage.Tab.KITS, ToolsPage.Chip.ALL, "-kits"},
                        {ToolsPage.Tab.BASIC, ToolsPage.Chip.ALL, null}};
                for (Object[] v : views) {
                    main(() -> { call(page, "tab", new Class<?>[]{ToolsPage.Tab.class}, v[0]); call(page, "filter", new Class<?>[]{ToolsPage.Chip.class}, v[1]); });
                    if (v[2] != null) page(tools, name + v[2], w, h);
                }
                // A kit's sheet over the page.
                if (!ToolKits.all().isEmpty()) {
                    main(() -> call(tools, "openKit", new Class<?>[]{ToolKits.Kit.class}, ToolKits.all().get(0)));
                    page(tools, name + "-kit-sheet", w, h);
                    main(() -> call(tools, "closeKit", new Class<?>[0]));
                }
                main(() -> {
                    java.util.List<?> cards = (java.util.List<?>) call(page, "cards", new Class<?>[0]);
                    call(tools, "pick", new Class<?>[]{Brush.class}, get(cards.get(Math.min(2, cards.size() - 1)), "brush"));
                });
                settle(1500);
                View pad = (View) get(get(tools, "test"), "pad");
                stroke(pad);
                page(tools, name + "-tried", w, h);
                Object undo = get(tools, "undo");
                main(() -> ((View) undo).performClick());
                page(tools, name + "-undone", w, h);
            } finally {
                main(tools::finish);
                settle(500);
            }
        }
    }
    /**
     * {@code activity}'s page ({@code root}) laid out {@code wDp × hDp} and drawn into a picture, off the screen: built
     * at that size the first time (its own layout listener does that), then given time for its samples, laid out again and drawn as its views draw.
     */
    private void page(Activity activity, String name, int wDp, int hDp) throws Exception {
        View root = (View) get(activity, "root");
        float density = activity.getResources().getDisplayMetrics().density;
        int w = Math.round(wDp * density), h = Math.round(hDp * density);
        Work lay = () -> {
            root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, w, h);
        };
        // A layout at a new size posts a build; then the views it made are laid out, and their samples drawn.
        main(() -> { lay.run(); call(activity, "render", new Class<?>[0]); });
        settle(1000);
        main(lay);
        settle(5000);
        main(() -> {
            lay.run();
            Bitmap picture = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            root.draw(new android.graphics.Canvas(picture));
            try (FileOutputStream file = new FileOutputStream(new File(out, name + ".png"))) { picture.compress(Bitmap.CompressFormat.PNG, 100, file); }
            report.append(name).append(".png\n");
        });
    }
    /** A pen's wave across {@code pad}, as the stylus would send it: down, moves, up. */
    private void stroke(View pad) throws Exception {
        main(() -> {
            int w = pad.getWidth(), h = pad.getHeight();
            long t = SystemClock.uptimeMillis();
            int n = 60;
            for (int i = 0; i <= n + 1; i++) {
                int action = i == 0 ? android.view.MotionEvent.ACTION_DOWN : i > n ? android.view.MotionEvent.ACTION_UP : android.view.MotionEvent.ACTION_MOVE;
                float f = Math.min(i, n) / (float) n;
                android.view.MotionEvent.PointerProperties[] props = {new android.view.MotionEvent.PointerProperties()};
                props[0].id = 0; props[0].toolType = android.view.MotionEvent.TOOL_TYPE_STYLUS;
                android.view.MotionEvent.PointerCoords[] coords = {new android.view.MotionEvent.PointerCoords()};
                coords[0].x = w * (.08f + .6f * f);
                coords[0].y = h * (.55f + .25f * (float) Math.sin(f * Math.PI * 2));
                coords[0].pressure = .3f + .5f * (float) Math.sin(f * Math.PI);
                android.view.MotionEvent event = android.view.MotionEvent.obtain(t, t + i * 8L, action, 1, props, coords, 0, 0, 1, 1, 0, 0,
                        android.view.InputDevice.SOURCE_STYLUS, 0);
                pad.dispatchTouchEvent(event);
                event.recycle();
            }
        });
    }
    /** The window as its views draw it, cropped to the Nomad's panel when simulating it. */
    private void drawnShot(Activity activity, String name, boolean nomad) throws Exception {
        Bitmap[] bitmap = {null};
        main(() -> {
            View view = activity.getWindow().getDecorView();
            bitmap[0] = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            view.draw(new android.graphics.Canvas(bitmap[0]));
        });
        Bitmap screen = bitmap[0], kept = screen;
        if (nomad && screen.getWidth() >= Device.NOMAD.panelWidth && screen.getHeight() >= Device.NOMAD.panelHeight)
            kept = Bitmap.createBitmap(screen, (screen.getWidth() - Device.NOMAD.panelWidth) / 2, (screen.getHeight() - Device.NOMAD.panelHeight) / 2, Device.NOMAD.panelWidth, Device.NOMAD.panelHeight);
        try (FileOutputStream file = new FileOutputStream(new File(out, name + ".png"))) {
            kept.compress(Bitmap.CompressFormat.PNG, 100, file);
        } finally {
            if (kept != screen) kept.recycle();
            screen.recycle();
        }
        report.append("drawn ").append(name).append('\n');
    }

    // The drawing screen

    private void drawing() throws Exception {
        PaintActivity app = (PaintActivity) test.startActivitySync(new Intent(test.getTargetContext(), PaintActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        long until = SystemClock.uptimeMillis() + 20000;
        while ((Boolean) get(app, "loading") && SystemClock.uptimeMillis() < until) SystemClock.sleep(200);
        try {
            drawingScreen(app);
            for (String layers : new String[]{"1layer", "4layers"}) {
                if (layers.equals("4layers")) {
                    for (int i = 0; i < 3; i++) main(() -> app.newLayer(true));
                    settle(1000);
                }
                for (boolean nomad : new boolean[]{true, false}) {
                    main(() -> call(app, "setSimulating", new Class<?>[]{boolean.class}, nomad));
                    settle(1500);
                    String size = nomad ? "nomad" : "manta";
                    for (int quarter : new int[]{0, 1}) {
                        main(() -> call(app, "requestQuarter", new Class<?>[]{int.class}, quarter));
                        settle(2000);
                        laidOut(app);
                        drawn(app, "draw-" + size + "-" + (quarter == 0 ? "portrait" : "landscape") + "-" + layers, nomad);
                    }
                    main(() -> call(app, "requestQuarter", new Class<?>[]{int.class}, 0));
                    settle(1000);
                }
            }
            main(() -> call(app, "setSimulating", new Class<?>[]{boolean.class}, false));
        } finally {
            main(app::finish);
            settle(500);
        }
    }

    // The file menu, dialogs and a message

    /**
     * The drawing screen's file menu, a confirmation (Delete), the drawing browser (Open) and a status message, at the
     * Manta's size and the Nomad's: MonoPaint's own menus and dialogs as Inkwash shows them, each drawn off the screen
     * on its own (a window of its own: {@link #window}), then dismissed, so nothing is changed.
     */
    private void menus() throws Exception {
        PaintActivity app = (PaintActivity) test.startActivitySync(new Intent(test.getTargetContext(), PaintActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        long until = SystemClock.uptimeMillis() + 20000;
        while ((Boolean) get(app, "loading") && SystemClock.uptimeMillis() < until) SystemClock.sleep(200);
        try {
            drawingScreen(app);
            for (boolean nomad : new boolean[]{false, true}) {
                String size = nomad ? "nomad" : "manta";
                main(() -> call(app, "setSimulating", new Class<?>[]{boolean.class}, nomad));
                settle(1500);
                laidOut(app);
                View bar = (View) get(get(app, "inkWashBar"), "view");
                main(() -> app.fileMenu(((ViewGroup) bar).getChildAt(0)));
                settle(1500);
                android.widget.PopupWindow menu = (android.widget.PopupWindow) get(app, "filePopup");
                main(() -> drawn(menu.getContentView(), "menu-" + size + "-file", menu.getWidth(), menu.getHeight()));
                main(menu::dismiss);
                settle(800);
                for (String dialog : new String[]{"openDrawing"}) {
                    int before = windows().size();
                    main(() -> call(app, dialog, new Class<?>[0]));
                    settle(2500);
                    java.util.List<View> roots = windows();
                    if (roots.size() <= before) { report.append("no window for ").append(dialog).append('\n'); continue; }
                    View window = roots.get(roots.size() - 1);
                    main(() -> drawn(window, "dialog-" + size + "-" + dialog.replace("Drawing", ""), 0, 0));
                    // Back, to the dialog's own window (it cancels): a key the screen may not pass on while asleep.
                    main(() -> {
                        window.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK));
                        window.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK));
                    });
                    settle(800);
                }
                main(() -> drawn(app.messageView("Saved"), "toast-" + size, 0, 0));
            }
            main(() -> call(app, "setSimulating", new Class<?>[]{boolean.class}, false));
        } finally {
            main(app::finish);
            settle(500);
        }
    }
    /**
     * The drawing screen, laid out: it starts on the shelf, so a notebook is opened (the open one, else the one with fewest
     * pages past one) and its page drawn on, anything it shows over it (the Daily Prompt) closed.
     */
    private void drawingScreen(PaintActivity app) throws Exception {
        laidOut(app);
        settle(3000);
        if (app.book == null) {
            DocumentStore.Notebook pick = null;
            for (Object o : (java.util.List<?>) get(app.notebooks, "shelf")) {
                DocumentStore.Notebook n = (DocumentStore.Notebook) o;
                if (n.pages > 1 && (pick == null || n.pages < pick.pages)) pick = n;
            }
            if (pick != null) {
                String path = pick.path;
                main(() -> app.openNotebook(path));
                long until = SystemClock.uptimeMillis() + 20000;
                while ((app.book == null || (Boolean) get(app, "loading")) && SystemClock.uptimeMillis() < until) SystemClock.sleep(200);
            }
        }
        main(app.notebooks::hide);
        settle(2000);
        closeAll(app);
    }
    /** The app's windows' root views, oldest first (WindowManagerGlobal's, as Espresso finds them). */
    @SuppressWarnings("unchecked")
    private static java.util.List<View> windows() throws Exception {
        Class<?> global = Class.forName("android.view.WindowManagerGlobal");
        Object instance = global.getMethod("getInstance").invoke(null);
        Field views = global.getDeclaredField("mViews");
        views.setAccessible(true);
        return new java.util.ArrayList<>((java.util.List<View>) views.get(instance));
    }
    /**
     * {@code view} drawn into a picture on its own, laid out first if the screen never did (asleep): {@code w × h}
     * pixels, or 0 for as wide as its window asks (else 440 dp) and as tall as it needs.
     */
    private void drawn(View view, String name, int w, int h) throws Exception {
        android.util.DisplayMetrics m = view.getResources().getDisplayMetrics();
        if (w <= 0) {
            ViewGroup.LayoutParams p = view.getLayoutParams();
            w = p != null && p.width > 0 ? p.width : view.getWidth() > 0 ? view.getWidth() : Math.round(440 * m.density);
        }
        int width = w;
        if (h <= 0) {
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(m.heightPixels, View.MeasureSpec.AT_MOST));
            h = view.getMeasuredHeight();
            width = view.getMeasuredWidth();
        }
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, h);
        Bitmap picture = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888);
        picture.eraseColor(android.graphics.Color.WHITE);
        view.draw(new android.graphics.Canvas(picture));
        try (FileOutputStream file = new FileOutputStream(new File(out, name + ".png"))) { picture.compress(Bitmap.CompressFormat.PNG, 100, file); }
        finally { picture.recycle(); }
        report.append("drawn ").append(name).append('\n');
    }
    /** The drawing screen laid out at the screen's size if the screen never did (asleep): its layout builds the bar. */
    private void laidOut(Activity app) throws Exception {
        View decor = app.getWindow().getDecorView();
        offScreen |= decor.getWidth() == 0;
        if (!offScreen) return;
        android.graphics.Point real = new android.graphics.Point();
        app.getWindowManager().getDefaultDisplay().getRealSize(real);
        int w = real.x, h = real.y;
        Work lay = () -> {
            decor.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            decor.layout(0, 0, w, h);
        };
        main(lay); settle(1500); main(lay); settle(1500); main(lay);
    }

    // Every other screen and popup of the drawing app (the design audit, 2026-10-23)

    /**
     * The drawing screen and everything that opens over it, at the Manta's size and the Nomad's: Zen, the menus a tap or a
     * hold opens (Menu, Finger, Fun, Mirrors, the eraser's, the smudge tool's, the stamp's, the size slider held, the Dropper beside the wet indicator, a layer's, a
     * page's options), the words bin, the rotate prompt, a note, the dialogs (Time is up, Delete page, the Daily Prompt,
     * New Notebook, Rename, Import PDF's box, Pages, Settings), the shelf and its notebook menu, a notebook's pages, its
     * library card and each card screen. Each is opened as a tap or a hold would, drawn as its windows draw ({@link #composite}),
     * then closed; one that fails is reported and passed by. The checks build's own notebook only.
     */
    private void app() throws Exception {
        PaintActivity app = (PaintActivity) test.startActivitySync(new Intent(test.getTargetContext(), PaintActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        long until = SystemClock.uptimeMillis() + 20000;
        while ((Boolean) get(app, "loading") && SystemClock.uptimeMillis() < until) SystemClock.sleep(200);
        try {
            drawingScreen(app);
            for (boolean nomad : picked != null ? new boolean[]{false} : new boolean[]{false, true}) {
                main(() -> call(app, "setSimulating", new Class<?>[]{boolean.class}, nomad));
                settle(2000);
                laidOut(app);
                String s = nomad ? "nomad" : "manta";
                InkWashBar bar = app.inkWashBar;
                View[] tools = (View[]) get(bar, "tools");
                step(app, nomad, "draw-" + s, () -> { }, null);
                step(app, nomad, "draw-" + s + "-zen", () -> app.setZen(true), () -> app.setZen(false));
                step(app, nomad, "draw-" + s + "-note", () -> app.message("Saved"), null);
                step(app, nomad, "menu-" + s + "-file", () -> app.fileMenu((View) get(bar, "menu")), null);
                step(app, nomad, "menu-" + s + "-finger", () -> ((View) get(bar, "finger")).performClick(), null);
                step(app, nomad, "menu-" + s + "-tool-picker", () -> app.toolPicker(0, tools[0]), null);
                step(app, nomad, "tool-" + s + "-poetry", () -> app.pad.words.toggle(), () -> app.pad.words.close());
                step(app, nomad, "menu-" + s + "-mirrors", () -> app.mirrors.mirrors(bar.menu), null);
                step(app, nomad, "menu-" + s + "-eraser", () -> tools[InkWash.ERASER].performLongClick(), null);
                step(app, nomad, "menu-" + s + "-smudge", () -> tools[InkWash.SMUDGE].performLongClick(), null);
                step(app, nomad, "menu-" + s + "-stamp", () -> tools[InkWash.STAMP].performLongClick(), null);
                // A Lined page at Half, then Show Kinds' popup with its Template eye; Left-Handed, the column on the right. Each put back after.
                NotebookOptions[] guides = new NotebookOptions[1];
                step(app, nomad, "draw-" + s + "-template", () -> { guides[0] = app.book.options(); app.setNotebookOptions(guides[0].withTemplate(NotebookTemplate.LINED).withGuidesShown(NotebookOptions.HALF)); }, null);
                step(app, nomad, "menu-" + s + "-template", () -> bar.kinds.performClick(), () -> { bar.hideLabel(); app.setNotebookOptions(guides[0]); });
                boolean hand = app.prefs.toolboxRight();
                step(app, nomad, "draw-" + s + "-left-handed", () -> { app.prefs.setToolboxRight(!hand); app.applyToolboxSide(); }, () -> { app.prefs.setToolboxRight(hand); app.applyToolboxSide(); });
                // The size slider (2026-10-24) on the first slot: the pen held on it two fifths down, its tab there (no preview on
                // the page since the owner's "the redraw is blinky and slow"); called off after, nothing set.
                View slider = (View) get(bar.swatches, "slider");
                step(app, nomad, "draw-" + s + "-size-slider", () -> {
                    call(bar, "choose", new Class<?>[]{int.class}, 0);
                    long down = SystemClock.uptimeMillis();
                    for (int action : new int[]{android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE}) {
                        android.view.MotionEvent e = android.view.MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, slider.getWidth() / 2f, slider.getHeight() * .4f, 0);
                        slider.dispatchTouchEvent(e); e.recycle();
                    }
                }, () -> {
                    android.view.MotionEvent e = android.view.MotionEvent.obtain(0, SystemClock.uptimeMillis(), android.view.MotionEvent.ACTION_CANCEL, 0, 0, 0);
                    slider.dispatchTouchEvent(e); e.recycle();
                });
                // A slot picked: its name beside it, the words alone (the owner, 2026-10-24: "don't use a box with a stroke");
                // the eraser picked: on its own row, its own size on the slider, the strip hidden (2026-10-24).
                step(app, nomad, "draw-" + s + "-brush-name", () -> call(bar, "choose", new Class<?>[]{int.class}, 1), () -> call(bar, "choose", new Class<?>[]{int.class}, 0));
                step(app, nomad, "draw-" + s + "-eraser", () -> call(bar, "choose", new Class<?>[]{int.class}, InkWash.ERASER), () -> call(bar, "choose", new Class<?>[]{int.class}, 0));
                // The Dropper taken up: its side bar, the slot's given up. Then a pick: the slot lays the picked grey, still selected, the Dropper down.
                step(app, nomad, "draw-" + s + "-dropper", () -> bar.swatches.dropper.performClick(), () -> {
                    int selected = app.inkWash.selected;
                    double kept = app.inkWash.look(selected).darkness;
                    app.pad.dropper(false);
                    bar.dropped(60);
                    int laid = ModularSizes.swatch(app.inkWash.brush(selected).parts, app.inkWash.look(selected).darkness);
                    if (app.inkWash.selected != selected || app.pad.dropperOn || laid != 60) report.append("FAILED dropper: tool ").append(app.inkWash.selected).append(" lays ").append(laid).append('\n');
                    app.inkWash.slots.swatches.useGrey(InkWash.key(selected), kept);
                    call(bar, "showBrush", new Class<?>[]{int.class}, selected);
                });
                step(app, nomad, "menu-" + s + "-play", () -> app.playMenu(bar.play), null);
                // Page Settings: a hold on a page's preview (the screen-reader action's path), a screen over the drawing screen.
                step(app, nomad, "page-" + s + "-settings", () -> call(get(bar.turner, "strip"), "options", new Class<?>[]{int.class}, app.book.stacks().of(Math.max(0, app.book.index()))), app.notebooks::hide);
                step(app, nomad, "draw-" + s + "-words-bin", () -> app.pad.words.toggle(), () -> app.pad.words.close());
                step(app, nomad, "draw-" + s + "-rotate-prompt", () -> call(app.rotationPrompt, "suggest", new Class<?>[]{int.class}, 90), () -> app.rotationPrompt.hide());
                step(app, nomad, "draw-" + s + "-time-up", () -> bar.timeUp(() -> { }), bar::hideLabel);
                step(app, nomad, "dialog-" + s + "-delete-page", () -> app.notebooks.confirmDelete(0, () -> { }), null);
                step(app, nomad, "dialog-" + s + "-daily-prompt", () -> {
                    java.util.List<DailyPrompt.Card> cards = DailyPrompt.load(DrawingStorage.sharedAllowed(app) ? DrawingStorage.shared() : null);
                    if (!cards.isEmpty()) app.dailyPrompt(cards, 0);
                }, null);
                step(app, nomad, "dialog-" + s + "-new-notebook", app::newNotebook, null);
                step(app, nomad, "dialog-" + s + "-rename", app::renameNotebook, null);
                step(app, nomad, "dialog-" + s + "-import-pdf", () -> app.showDialog(new android.app.AlertDialog.Builder(app).setTitle(PictureImport.IMPORT)
                        .setMessage(PictureImport.importing(3, 12)).setNegativeButton(PictureImport.CANCEL, null)), null);
                step(app, nomad, "dialog-" + s + "-pages", () -> call(app, "showPageOverview", new Class<?>[0]), null);
                step(app, nomad, "shelf-" + s, app.notebooks::showShelf, app.notebooks::hide);
                step(app, nomad, "shelf-" + s + "-notebook-menu", () -> {
                    main(app.notebooks::showShelf);
                    settle(2500);
                    main(() -> { View tile = longClickable(app.notebooks.view); if (tile != null) tile.performLongClick(); });
                }, app.notebooks::hide);
                step(app, nomad, "notebook-" + s + "-pages", () -> app.notebooks.showPages(app.book.index()), app.notebooks::hide);
                // The settings list (LibraryCard), then with all nine rows: Random (Random From), an older
                // notebook's off-swatch grey (no swatch chosen), No Erasing on and locked; its options put back after.
                step(app, nomad, "notebook-" + s + "-settings", app.notebooks::showCard, app.notebooks::hide);
                NotebookOptions[] was = new NotebookOptions[1];
                step(app, nomad, "notebook-" + s + "-settings-all", () -> {
                    was[0] = app.book.options();
                    app.setNotebookOptions(was[0].withTemplate(NotebookTemplate.random(null)).withPaper(179).withNoErasing(true));
                    app.notebooks.showCard();
                }, () -> { app.setNotebookOptions(was[0]); app.notebooks.hide(); });
                for (CardSheet.Screen screen : CardSheet.Screen.values())
                    step(app, nomad, "card-" + s + "-" + screen.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'),
                            () -> app.notebooks.showCard(screen), () -> { app.notebooks.back(); app.notebooks.hide(); });
            }
            main(() -> call(app, "setSimulating", new Class<?>[]{boolean.class}, false));
        } finally {
            main(app::finish);
            settle(500);
        }
    }
    /**
     * One screen: {@code open} run on the main thread (it may wait itself, off it: a step that settles), the screen given time to draw, its
     * windows drawn ({@link #composite}), then {@code close} and every window over the drawing screen closed ({@link #closeAll}).
     */
    private void step(PaintActivity app, boolean nomad, String name, Work open, Work close) {
        if (picked != null && !picked.contains(name)) return;
        try {
            if (open != null) { boolean waits = name.contains("notebook-menu"); if (waits) open.run(); else main(open); }
            settle(name.startsWith("shelf") || name.startsWith("notebook") || name.startsWith("card") ? 4000 : 2000);
            composite(app, name, nomad);
        } catch (Throwable e) {
            report.append("FAILED step ").append(name).append(": ").append(e.getCause() != null ? e.getCause() : e).append('\n');
        }
        try {
            if (close != null) main(close);
            closeAll(app);
        } catch (Throwable e) {
            report.append("FAILED closing ").append(name).append(": ").append(e).append('\n');
        }
    }
    /** The first view under {@code root} with a long press of its own (the shelf's first notebook), or null. */
    private static View longClickable(View root) {
        if (root.isLongClickable() && root.hasOnLongClickListeners()) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            View found = longClickable(((ViewGroup) root).getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }
    /**
     * The app's windows as they draw: the activity's, then each popup and dialog over it at its place on the screen (the
     * e-ink's own presentation, which a screenshot may miss, aside). For the Nomad, the simulated panel's frame cut out.
     */
    private void composite(PaintActivity app, String name, boolean nomad) throws Exception {
        main(() -> {
            View decor = app.getWindow().getDecorView();
            Bitmap picture = Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(picture);
            decor.draw(canvas);
            int[] at = new int[2];
            for (View window : windows()) {
                if (window == decor || !window.isShown() || window.getWidth() == 0) continue;
                window.getLocationOnScreen(at);
                canvas.save(); canvas.translate(at[0], at[1]); window.draw(canvas); canvas.restore();
            }
            Bitmap kept = picture;
            if (nomad) {
                View frame = (View) get(app, "orientationFrame");
                frame.getLocationOnScreen(at);
                if (frame.getWidth() > 0 && at[0] + frame.getWidth() <= picture.getWidth() && at[1] + frame.getHeight() <= picture.getHeight())
                    kept = Bitmap.createBitmap(picture, at[0], at[1], frame.getWidth(), frame.getHeight());
            }
            try (FileOutputStream file = new FileOutputStream(new File(out, name + ".png"))) { kept.compress(Bitmap.CompressFormat.PNG, 100, file); }
            finally { if (kept != picture) kept.recycle(); picture.recycle(); }
        });
        report.append("composite ").append(name).append('\n');
    }
    /** Every window over the drawing screen closed: Back to each dialog and popup, the app's panels, then any popup left taken away. */
    private void closeAll(PaintActivity app) throws Exception {
        View decor = app.getWindow().getDecorView();
        for (int round = 0; round < 3; round++) {
            java.util.List<View> roots = windows();
            boolean any = false;
            for (int i = roots.size() - 1; i >= 0; i--) {
                View window = roots.get(i);
                if (window == decor || !window.isAttachedToWindow()) continue;
                any = true;
                main(() -> {
                    window.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK));
                    window.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK));
                });
                settle(300);
            }
            main(app::dismissPanels);
            settle(300);
            if (!any) break;
        }
        main(() -> {
            for (View window : windows())
                if (window != decor && window.isAttachedToWindow() && window.getClass().getName().contains("PopupDecorView"))
                    app.getWindowManager().removeViewImmediate(window);
        });
        settle(500);
    }

    // Helpers

    /** Starts {@code intent}; for the Nomad, its content goes in a Nomad-sized {@link PanelPreviewLayout}. */
    private Activity launch(Intent intent, boolean nomad) throws Exception {
        Activity activity = test.startActivitySync(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        if (nomad) main(() -> {
            ViewGroup content = activity.findViewById(android.R.id.content);
            View child = content.getChildAt(0);
            content.removeView(child);
            PanelPreviewLayout preview = new PanelPreviewLayout(activity);
            preview.addView(child);
            preview.setSimulated(Device.NOMAD);
            content.addView(preview, new ViewGroup.LayoutParams(-1, -1));
        });
        settle(1500);
        return activity;
    }
    private void shot(Activity activity, String name) throws Exception {
        ViewGroup content = activity.findViewById(android.R.id.content);
        shot(activity, name, content.getChildAt(0) instanceof PanelPreviewLayout);
    }
    /** The screen, cropped to the Nomad's panel when simulating it. */
    private void shot(Activity activity, String name, boolean nomad) throws Exception {
        Bitmap screen = test.getUiAutomation().takeScreenshot();
        Bitmap kept = screen;
        if (nomad && screen.getWidth() >= Device.NOMAD.panelWidth && screen.getHeight() >= Device.NOMAD.panelHeight) {
            boolean wide = screen.getWidth() > screen.getHeight();
            int w = wide ? Device.NOMAD.panelHeight : Device.NOMAD.panelWidth, h = wide ? Device.NOMAD.panelWidth : Device.NOMAD.panelHeight;
            kept = Bitmap.createBitmap(screen, (screen.getWidth() - w) / 2, (screen.getHeight() - h) / 2, w, h);
        }
        try (FileOutputStream file = new FileOutputStream(new File(out, name + ".png"))) {
            kept.compress(Bitmap.CompressFormat.PNG, 100, file);
        } finally {
            if (kept != screen) kept.recycle();
            screen.recycle();
        }
        report.append("shot ").append(name).append('\n');
    }
    /**
     * The drawing screen as its views draw it (its panel is presented straight to the e-ink, which a screenshot may
     * miss): the simulated panel's frame, or the whole window.
     */
    private void drawn(PaintActivity app, String name, boolean nomad) throws Exception {
        Bitmap[] bitmap = {null};
        main(() -> {
            View view = nomad ? (View) get(app, "orientationFrame") : app.getWindow().getDecorView();
            bitmap[0] = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            view.draw(new android.graphics.Canvas(bitmap[0]));
        });
        try (FileOutputStream file = new FileOutputStream(new File(out, name + ".png"))) {
            bitmap[0].compress(Bitmap.CompressFormat.PNG, 100, file);
        } finally { bitmap[0].recycle(); }
        report.append("drawn ").append(name).append('\n');
    }
    private void settle(long ms) { test.waitForIdleSync(); SystemClock.sleep(ms); test.waitForIdleSync(); }

    private interface Work { void run() throws Exception; }
    private void main(Work work) throws Exception {
        Throwable[] error = {null};
        test.runOnMainSync(() -> { try { work.run(); } catch (Throwable e) { error[0] = e; } });
        if (error[0] != null) throw new Exception(error[0]);
    }
    private static Field field(Class<?> type, String name) throws Exception {
        for (Class<?> c = type; c != null; c = c.getSuperclass())
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; } catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(name);
    }
    private static Object get(Object owner, String name) throws Exception { return field(owner.getClass(), name).get(owner); }
    private static Object call(Object owner, String name, Class<?>[] types, Object... args) throws Exception {
        for (Class<?> c = owner.getClass(); c != null; c = c.getSuperclass())
            try { Method m = c.getDeclaredMethod(name, types); m.setAccessible(true); return m.invoke(owner, args); } catch (NoSuchMethodException ignored) { }
        throw new NoSuchMethodException(name);
    }
}
