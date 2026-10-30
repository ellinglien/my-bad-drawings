package io.github.mpdairy.monopaint;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The painting screen. It owns the document and current selection, builds the layout,
 * turns it for app-only rotation, and coordinates the panels.
 *
 * <p>Layout, from the outside in: {@link PanelPreviewLayout} (optional smaller-panel
 * simulation) → {@link QuarterTurnLayout} turning the whole UI → {@link #root}, holding
 * the header/color bar ({@link #palette}) and the {@link #body} with the {@link Toolbar}
 * and the {@link DrawingPad} canvas. Android itself always stays in portrait.
 */
public final class PaintActivity extends Activity implements ControlHost {
    // Document
    DocumentStore store;
    DrawingBook book;
    /** Saved path of the open drawing, or empty if never saved. */
    String drawingName = "";
    /** Names the open notebook's file, and says it in the foot's corner ({@link InkWashBar#bookName}). */
    private void named(String path) { drawingName = path; if (inkWashBar != null) inkWashBar.bookName(DrawingFiles.name(path)); }
    String saveError;
    boolean resumed, loading = true, destroyed, saving;
    /** The old app's brushes and settings were taken in at this launch (HandOver): one note once the page is up. */
    private boolean handedOver;

    // Settings and the current selection
    SharedPreferences preferences;
    PaintPreferences prefs;
    /** The tablet itself; {@link #layoutDevice} is the one the controls are laid out for. */
    Device device;
    ToolLibrary library;
    final PaintState paint = new PaintState();
    /** The Inkwash tools; replaces MonoPaint's tool library for drawing. */
    InkWash inkWash;
    /** Replay page ({@link PageReplay}). */
    final PageReplay replay = new PageReplay(this);
    /** Tracing paper's ghosts under the page drawn on. */
    final Ghosts ghosts = new Ghosts(this);
    InkWashBar inkWashBar;
    /** The Mirrors button's list ({@link MirrorsMenu}). */
    final MirrorsMenu mirrors = new MirrorsMenu(this);
    /** The shelf of notebooks and the inside of one, over the drawing screen (Inkwash's notebooks). */
    NotebookScreens notebooks;
    boolean navigationLocked = true;
    /** The eyedropper is active; {@link #pickedShade} once it has sampled the canvas. */
    boolean pickingShade, pickedShade;
    /** Shade to restore if the eyedropper is cancelled. */
    int pickOriginalShade;

    // App-only rotation: Android and the e-ink driver always remain in portrait.
    int appRotation = Surface.ROTATION_0;
    boolean landscape, toolboxRight;
    /** Degrees the header strip is turned; 90 in landscape. */
    int toolbarTurn;
    /** The untransformed event, whose pen tilt axes Android would otherwise rotate. */
    MotionEvent physicalPenEvent;

    // Layout
    LinearLayout root, body, palette, headerControls, leftHeader, rightHeader, menuControls;
    /** Inkwash's page area beside the column: the pad, and the page bar under it ({@link InkWashBar#turner}). */
    private LinearLayout pageArea;
    /** Along the foot: Zen Mode's corner under the column ({@link #zenCorner}, its button floats over it), the brush line and the notebook's name in the far corner. */
    private LinearLayout footRow;
    private View zenCorner;
    QuarterTurnLayout paletteFrame, orientationFrame;
    PanelPreviewLayout previewFrame;
    DrawingPad pad;
    FullscreenController fullscreen;
    EdgeBars edgeBars;
    Toolbar toolbar;
    RotationPrompt rotationPrompt;
    CanvasGravity canvasGravity;
    ShadePicker shadePicker;
    WetnessBar wetnessBar;
    /** The droplet and wetness slider, kept upright together. */
    private QuarterTurnLayout wetControls;
    WetDroplet wetDroplet;
    ToolButton maskButton, watercolorButton, opaqueButton, transparentButton, eyedropperButton, eraseButton;
    ImageButton menuButton, previousPage, nextPage, pageMenuButton;
    PageActionButton rotateButton, pagesButton;
    TextView pageNumber;

    // Panels. At most one popup is open at a time.
    PopupWindow toolPicker, filePopup, layersPopup;
    AlertDialog pageOverview, pageConfirmation;
    QuarterTurnLayout pagePanel;
    private TextView popupPageNumber;
    private PageActionButton popupPrevious, popupNext;
    private final Rect pagePanelBounds = new Rect();
    private boolean pagePanelByFinger;
    TextView gradientHint;
    private final Runnable gradientHintTask = this::showGradientHint;
    /** Swallows the rest of a touch that dismissed a panel. */
    private boolean dismissingTouch;
    /** Where a finger landed in the screen's top band (raw px), until it moves ({@link StripGesture#edgePull}); NaN for none. */
    private float edgeX = Float.NaN, edgeY;
    boolean pageActionPending;
    final SelectionFeedback selectionFeedback = new SelectionFeedback();
    /** Rubbing in the color bar or palette loads a brush with limited paint. */
    final PaintRub paintRub = new PaintRub(this::loadPaint);
    final SelectionFeedback layerFeedback = new SelectionFeedback();

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(Ui.largerText(base));
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        // Textures and name lists are read from the APK's assets (see Assets).
        Assets.install(getApplicationContext().getAssets()::open);
        Theme.fonts(this);
        // Notebook covers' labels in the app's face.
        NotebookCovers.lettering = new CoverLettering();
        forgetOldCovers();
        device = Device.current(this);
        preferences = getSharedPreferences(PaintPreferences.FILE, MODE_PRIVATE);
        // From the old application id, once, before anything reads the preferences (HandOver).
        handedOver = DrawingStorage.takeHandOver(this, preferences);
        prefs = new PaintPreferences(preferences);
        DrawingStorage.sharedAllowed(this); // after a rename, carries the former name's folders over (AppName) first
        inkWash = new InkWash(preferences);
        inkWash.hiddenKinds = () -> pad == null || pad.document == null ? 0 : EraseKind.hidden(pad.document.kindsShown());
        DirectEink.useAndroidDrawing(prefs.androidDrawing());
        DirectEink.useFastBinaryPlane(device.fastBinaryPlane());
        // Draw once (the wet paint spec's experiment): launched with --ez drawOnce true or false, kept until changed.
        if (getIntent() != null && getIntent().hasExtra(PaintPreferences.DRAW_ONCE)) prefs.setDrawOnce(getIntent().getBooleanExtra(PaintPreferences.DRAW_ONCE, false));
        QuickStroke.drawOnce = prefs.drawOnce();
        noticeUntestedFirmware();
        // Inkwash: one finger draws with the finger tool (FingerTool), two or three tap to undo or redo; with Zoom unlocked
        // two also pinch and pan once they move (PinchPan). Locked at first, the choice kept.
        navigationLocked = prefs.navigationLocked();
        prefs.loadPaint(paint);
        library = prefs.toolLibrary(() -> message("Could not load presets. A recovery copy has been kept."));
        if (library.activeId().isEmpty() && library.current().isBrush()) library.edit(library.current().asBrush());
        paint.eraseMode &= library.current().supportsEraseMode();
        // Start in private storage; loadDrawing() moves the library to shared storage when allowed.
        // The owner's own Supernote templates (NotebookTemplate): Supernote keeps them in MyStyle.
        NotebookTemplate.folder = new java.io.File(android.os.Environment.getExternalStorageDirectory(), "MyStyle");
        NotebookTemplate.images = (file, w, h) -> {
            android.graphics.Bitmap read = android.graphics.BitmapFactory.decodeFile(file.getPath());
            if (read == null) return null;
            android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(read, w, h, true);
            int[] pixels = new int[w * h];
            scaled.getPixels(pixels, 0, w, 0, 0, w, h);
            if (scaled != read) scaled.recycle();
            read.recycle();
            return pixels;
        };
        store = new DocumentStore(DrawingStorage.appPrivate(this), DrawingStorage.recovery(this));

        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.PAPER);
        buildHeader();
        paletteFrame = new QuarterTurnLayout(this); paletteFrame.addView(palette);
        root.addView(paletteFrame);
        body = new LinearLayout(this);
        toolbar = new Toolbar(this);
        body.addView(toolbar.toolScroll, new LinearLayout.LayoutParams(dp(64), -1));
        toolbar.rebuildTools();
        inkWashBar = new InkWashBar(this);
        fullscreen = new FullscreenController(this);
        edgeBars = new EdgeBars(this);
        pad = new DrawingPad(this);
        pageArea = new LinearLayout(this); pageArea.setOrientation(LinearLayout.VERTICAL);
        pageArea.addView(pad, new LinearLayout.LayoutParams(-1, 0, 1));
        body.addView(pageArea, new LinearLayout.LayoutParams(0, -1, 1));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        orientationFrame = new QuarterTurnLayout(this);
        // The notebook screens lie over the drawing screen and turn with it.
        notebooks = new NotebookScreens(this);
        FrameLayout stage = new FrameLayout(this);
        stage.addView(root);
        // Zen Mode's button, in the foot corner under the column in both states (under the notebook screens).
        zenButton = zenButton();
        stage.addView(zenButton, new FrameLayout.LayoutParams(0, 0, Gravity.TOP | Gravity.START));
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> { if (r - l != or - ol || b - t != ob - ot) zenButton.post(this::placeZen); });
        stage.addView(notebooks.view);
        orientationFrame.addView(stage);
        orientationFrame.afterLayout = () -> { pad.disconnectDisplay(); pad.updateViewport(); pad.post(pad::connectDisplay); };
        previewFrame = new PanelPreviewLayout(this);
        previewFrame.addView(orientationFrame);
        previewFrame.setRotationHint(rotateButton, dp(72));
        previewFrame.afterLayout = () -> pad.post(pad::connectDisplay);
        previewFrame.setSimulated(simulating() ? device.simulates : null);
        setContentView(previewFrame);
        // Behind the loading screen (the window's background, drawable/splash) until the first page is ready: no blank
        // screen in between on the panel (reveal).
        previewFrame.setVisibility(View.INVISIBLE);
        applyToolboxSide(); updatePages();
        rotationPrompt = new RotationPrompt(this, rotateButton);
        canvasGravity = new CanvasGravity(this);
        pad.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (l != ol || t != ot || r != or || b != ob) { pad.disconnectDisplay(); pad.post(pad::connectDisplay); }
        });
        if (DrawingStorage.sharedAllowed(this)) loadDrawing();
        else root.post(this::askForSharedStorage);
    }

    /** Moves the library to shared storage when allowed, then restores the working drawing. */
    private void loadDrawing() {
        if (destroyed) return;
        // Shared storage just allowed and the old app's handover waiting: start again, so it is taken before anything
        // reads the preferences, and nothing this launch holds is saved over it.
        if (DrawingStorage.handOverWaiting(this, preferences)) { startAgain(); return; }
        if (DrawingStorage.sharedAllowed(this)) store.relocate(DrawingStorage.shared(), (unused, error) -> {
            if (error != null) runOnUiThread(() -> message("Could not move drawings to "
                    + DrawingStorage.SHARED_NAME + ": " + error.getMessage()));
        });
        store.recover((recovered, error) -> runOnUiThread(() -> {
            if (destroyed) return;
            loading = false;
            if (error != null) migrateThen(null, () -> {
                // Keep the failed recovery on disk until the user explicitly chooses a new notebook or opens one.
                loading = true;
                showDialog(new AlertDialog.Builder(this).setTitle("Couldn't recover the drawing")
                        .setMessage(error.getMessage())
                        .setPositiveButton("Open", (d, w) -> { loading = false; notebooks.showShelf(); })
                        .setNegativeButton("New", (d, w) -> { startNewDrawing(); notebooks.hide(); })
                        .setCancelable(false));
            });
            else migrateThen(recovered, () -> {
                // The working notebook as it was, open on its last page (the owner, 2026-10-21); the shelf only with none.
                named(recovered == null || recovered.path.isEmpty() ? newName() : recovered.path);
                replaceBook(recovered == null ? null : recovered.book);
                scheduleAutosave();
                if (recovered == null || recovered.book == null) notebooks.showShelf();
                randomCoversOnce();
                plainPagesOnce();
            });
            reveal();
            if (handedOver) { handedOver = false; message("Brushes and settings brought over"); }
        }));
    }
    /** A new process for the drawing screen, this one gone without its pause: nothing it holds is saved. */
    private void startAgain() {
        startActivity(new Intent(this, PaintActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        Runtime.getRuntime().exit(0);
    }

    /**
     * Ends the loading screen (the window's background, drawable/splash, AppTheme.Loading): the drawing screen as it now
     * stands takes its place in one frame, so the panel changes once. Then the slots' brushes are resolved off the main
     * thread ({@link #warmSlots}).
     */
    private void reveal() {
        if (previewFrame.getVisibility() == View.VISIBLE) return;
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Theme.PAPER));
        previewFrame.setVisibility(View.VISIBLE);
        pad.post(pad::connectDisplay);
        warmSlots();
    }
    /**
     * The ten slots' brushes resolved once in the background at their sizes and greys, so the first stroke or fill
     * doesn't wait for what a brush first loads. The result is dropped: each stroke resolves its own, the same.
     */
    private void warmSlots() {
        java.util.List<Runnable> work = new java.util.ArrayList<>();
        for (int i = 0; i < ToolSlots.COUNT; i++) {
            Brush brush = inkWash.brush(i);
            ToolSwatches.Look look = inkWash.look(i);
            if (brush != null) work.add(() -> ResolvedBrush.of(brush.parts, look.px, look.darkness));
        }
        Thread warm = new Thread(() -> {
            for (Runnable r : work) try { r.run(); } catch (RuntimeException | OutOfMemoryError ignored) { }
        }, "warm slots");
        warm.setPriority(Thread.MIN_PRIORITY);
        warm.start();
    }

    /**
     * Once (the owner, 2026-10-20: "give the existing ones random designs"): every notebook still on the default design takes
     * one of the nine by chance, as new ones do. shortcut: a one-time pass for notebooks made before random covers; drop it
     * once no such library is left.
     */
    private void randomCoversOnce() {
        if (preferences.getBoolean("randomCovers", false)) return;
        store.notebooks((list, error) -> runOnUiThread(() -> {
            if (destroyed || error != null) return;
            // A notebook being opened is rewritten after its read: its open book would save over the change. Tried again later.
            if (loading || saving) { pad.postDelayed(this::randomCoversOnce, 2000); return; }
            CoverDesign[] designs = CoverDesign.values();
            java.util.Random random = new java.util.Random();
            for (DocumentStore.Notebook n : list) {
                if (n.unreadable || n.design != CoverDesign.DEFAULT) continue;
                CoverDesign design = designs[random.nextInt(designs.length)];
                // The open one as it is now: a design chosen since its last save stays.
                if (n.path.equals(drawingName) && book != null) { if (book.design() == CoverDesign.DEFAULT) setCoverDesign(design); }
                else { store.design(n.path, design, (unused, failed) -> { if (failed != null) Log.e(ProbeActivity.TAG, "Random cover failed", failed); }); }
            }
            preferences.edit().putBoolean("randomCovers", true).apply();
            // Queued after the writes: the shelf shows them.
            store.library((library, none) -> runOnUiThread(() -> { if (!destroyed && notebooks.onShelf()) notebooks.showShelf(); }));
        }));
    }
    /**
     * Once (the owner, 2026-10-21: "set all pages to normal for now"): every notebook made before layers-are-pages goes back
     * to Off, every page Solid. shortcut: a one-time pass that rewrites each notebook once; drop it once no such library is left.
     */
    private void plainPagesOnce() {
        if (preferences.getBoolean("plainPages", false)) return;
        store.notebooks((list, error) -> runOnUiThread(() -> {
            if (destroyed || error != null) return;
            if (loading || saving) { pad.postDelayed(this::plainPagesOnce, 2000); return; }
            for (DocumentStore.Notebook n : list) {
                if (n.unreadable) continue;
                if (n.path.equals(drawingName) && book != null) setTracing(book.tracing().plainView());
                else store.plainTracing(n.path, (unused, failed) -> { if (failed != null) Log.e(ProbeActivity.TAG, "Plain pages failed", failed); });
            }
            preferences.edit().putBoolean("plainPages", true).apply();
        }));
    }
    /** Builds the header strip: menu, undo/redo, the color bar and the page controls. */
    private void buildHeader() {
        palette = new LinearLayout(this);
        leftHeader = new LinearLayout(this); rightHeader = new LinearLayout(this);
        rightHeader.setGravity(Gravity.END);
        // Size each control group to its contents; the shade strip gets all spare space.
        palette.addView(leftHeader, new LinearLayout.LayoutParams(-2, dp(48)));
        LinearLayout colors = new LinearLayout(this);
        palette.addView(colors, new LinearLayout.LayoutParams(0, dp(48), 1));
        palette.addView(rightHeader, new LinearLayout.LayoutParams(-2, dp(48)));

        menuButton = new PageActionButton(this, this, true);
        menuButton.setImageResource(R.drawable.ic_ph_list); menuButton.setContentDescription("File menu");
        menuButton.setOnClickListener(v -> {
            if (busy()) return;
            pad.finishStroke(); setPickingShade(false);
            fileMenu(menuButton);
        });
        menuControls = new LinearLayout(this);
        menuControls.addView(menuButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        headerUndo = headerAction("Undo", R.drawable.ic_ph_undo, this::undo);
        headerRedo = headerAction("Redo", R.drawable.ic_ph_redo, this::redo);
        leftHeader.addView(menuControls);

        rotateButton = pageAction("Rotate", R.drawable.ic_ph_arrow_clockwise, () -> rotationPrompt.accept(), false);
        // Over the page, so a menu's panel: paper, a fine ink edge, square.
        rotateButton.setBackground(Theme.menu(this)); rotateButton.setStateListAnimator(null);
        // 1.5 times a header button (the owner, 2026-10-27: "make it a bit bigger"): 72 dp, its icon 36 dp.
        rotateButton.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER); rotateButton.setPadding(dp(18), dp(18), dp(18), dp(18));
        rotateButton.setVisibility(View.INVISIBLE);

        headerControls = new LinearLayout(this);
        previousPage = headerPageButton("Previous page", R.drawable.ic_ph_caret_left, () -> turnPage(-1));
        pageNumber = new PageLabel(this, this); pageNumber.setOnClickListener(v -> showPageOverview());
        Theme.style(pageNumber, TypeStyle.BODY); pageNumber.setTypeface(Theme.face(700));
        pageNumber.setGravity(Gravity.CENTER); headerControls.addView(pageNumber, new LinearLayout.LayoutParams(dp(76), dp(48)));
        nextPage = headerPageButton("Next page", R.drawable.ic_ph_caret_right, () -> turnPage(1));
        pageMenuButton = headerPageButton("Page actions", R.drawable.ic_ph_file, this::showPageMenu);
        pagesButton = new PageActionButton(this, this, true); pagesButton.setImageResource(R.drawable.ic_ph_files);
        pagesButton.setContentDescription("Pages");
        pagesButton.setOnClickListener(v -> { if (!busy()) showPagePanel(); });

        maskButton = colorBarButton(colors, "Masking fluid", R.drawable.ic_masking_fluid, 32, this::toggleMaskingFluid);
        maskButton.setOnTouchListener(new MaskPeelTouch(this, maskButton));
        maskButton.markerBelow = true;
        // The droplet and its wetness slider make one square, turned upright in landscape
        // like an icon, so the slider always fills from the bottom.
        LinearLayout wetPair = new LinearLayout(this);
        wetControls = new QuarterTurnLayout(this); wetControls.addView(wetPair);
        colors.addView(wetControls, new LinearLayout.LayoutParams(dp(48), dp(48)));
        watercolorButton = colorBarButton(wetPair, "Watercolour mode", 0, 32, this::tapDroplet);
        watercolorButton.headerIcon = false;
        // No icon resource: the droplet is solid while Watercolor mode is on, and dashed while it is off.
        watercolorButton.iconOnly(new DropletIcon(false), new DropletIcon(true));
        watercolorButton.setContentDescription("Watercolour mode. Transparent brushes, shapes and flat fills paint watercolour. While wet paint is moving, tap to dry it.");
        watercolorButton.iconHalf = 18;
        wetDroplet = new WetDroplet(this, watercolorButton);
        wetnessBar = new WetnessBar(this);
        wetPair.addView(wetnessBar, new LinearLayout.LayoutParams(dp(16), dp(48)));
        eyedropperButton = colorBarButton(colors, "Pick color from canvas", R.drawable.ic_ph_eyedropper, () -> {
            setPickingShade(!pickingShade);
            if (pickingShade) pad.dryWet();
        });
        eyedropperButton.markerBelow = true;
        eyedropperButton.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) hideGradientHint();
            return false;
        });
        shadePicker = new ShadePicker(this);
        colors.addView(shadePicker, new LinearLayout.LayoutParams(0, dp(48), 1));
        eraseButton = colorBarButton(colors, "Erase with current tool", R.drawable.ic_ph_eraser, this::toggleEraseMode);
        eraseButton.markerBelow = true;
        transparentButton = colorBarButton(colors, "Transparent paint", R.drawable.ic_ph_circle_dashed, () -> setTransparentPaint(true));
        opaqueButton = colorBarButton(colors, "Opaque paint", R.drawable.ic_ph_circle, () -> setTransparentPaint(false));
        refreshPaintModes(); setMaskingFluid(paint.maskingFluid);
        rightHeader.addView(headerControls);
    }

    int dp(float value) { return Ui.dp(this, value); }
    int appTurn() { return appRotation == Surface.ROTATION_270 ? -90 : appRotation*90; }

    // ControlHost
    /** The shelf's taps: with no notebook open (a recovery that failed), only a load or save in progress holds them. */
    boolean shelfBusy() { return book == null ? loading || saving : busy(); }
    @Override public boolean busy() { return replay.running() || loading || saving || pad == null || pad.document == null || pad.fill != null || pad.gradientCommit; }
    @Override public boolean pageActionPending() { return pageActionPending; }
    @Override public int toolbarTurn() { return toolbarTurn; }
    @Override public boolean colorMarkersOnTop() { return landscape && !toolboxRight; }
    @Override public SelectionFeedback selectionFeedback() { return selectionFeedback; }

    // Controls

    /**
     * An icon control. Pressing it first ends the current pen gesture and leaves the
     * eyedropper, except that color controls may act on a pending gradient.
     */
    ToolButton iconButton(String title, int icon, Runnable action) {
        ToolButton button = new ToolButton(this, this);
        button.setTag(title); button.setContentDescription(title);
        button.setPadding(dp(10), 0, dp(8), 0);
        button.iconOnly(icon);
        button.setOnClickListener(v -> {
            if (busy()) return;
            boolean colorControl = button == eyedropperButton || button == eraseButton;
            if (button != eyedropperButton) setPickingShade(false);
            if (!colorControl || !pad.hasGradient()) pad.finishStroke();
            action.run();
        });
        return button;
    }
    private ToolButton headerButton(LinearLayout parent, String title, int icon, Runnable action) {
        return headerButton(parent, title, icon, 48, action);
    }
    /**
     * A header control {@code width} dp along the strip. A narrow icon takes a narrow button,
     * leaving the room to the shade strip; it still turns upright in place in landscape.
     */
    private ToolButton headerButton(LinearLayout parent, String title, int icon, int width, Runnable action) {
        ToolButton button = iconButton(title, icon, action);
        button.headerIcon = true; button.setBackgroundColor(Theme.PAPER);
        parent.addView(button, new LinearLayout.LayoutParams(dp(width), dp(48)));
        return button;
    }
    private ToolButton headerAction(String title, int icon, Runnable action) { return headerButton(menuControls, title, icon, action); }
    /** A color bar toggle that shows its state with a dot. */
    private ToolButton colorBarButton(LinearLayout parent, String title, int icon, Runnable action) {
        return colorBarButton(parent, title, icon, 48, action);
    }
    private ToolButton colorBarButton(LinearLayout parent, String title, int icon, int width, Runnable action) {
        ToolButton button = headerButton(parent, title, icon, width, action);
        button.alwaysDot = true;
        return button;
    }
    /** A button whose action runs after its pressed outline reaches the panel. */
    private PageActionButton pageAction(String name, int icon, Runnable action, boolean header) {
        PageActionButton button = new PageActionButton(this, this, header);
        button.setImageResource(icon); button.setContentDescription(name);
        button.setOnClickListener(v -> {
            if (busy() || pageActionPending) return;
            button.press.set(true); pageActionPending = true;
            // Submit the press before page work or the full orientation change.
            button.post(() -> {
                try {
                    // End the press before page counters, disabled arrows or a
                    // new panel request an Android frame. Retaining the box for
                    // 200 ms lets those redraws replay (and dim) its fast pixels.
                    button.press.set(false);
                    if (resumed && !destroyed && button.isAttachedToWindow()) { setPickingShade(false); action.run(); }
                } finally { pageActionPending = false; }
            });
        });
        return button;
    }
    private ImageButton headerPageButton(String name, int icon, Runnable action) {
        PageActionButton button = pageAction(name, icon, action, true);
        headerControls.addView(button, new LinearLayout.LayoutParams(dp(40), dp(48))); return button;
    }
    /** A drag shadow that matches the app's turned UI. */
    View.DragShadowBuilder turnedShadow(View view) {
        return new View.DragShadowBuilder(view) {
            @Override public void onDrawShadow(Canvas canvas) {
                canvas.save(); canvas.rotate(appTurn(), view.getWidth()/2f, view.getHeight()/2f);
                super.onDrawShadow(canvas); canvas.restore();
            }
        };
    }

    // Pages

    void updatePages() { updatePages(false); }
    /** As {@link #updatePages()}; {@code turned}: another page turned to, the pages as they were (the page bar moves its marks alone). */
    private void updatePages(boolean turned) {
        if (pageNumber == null) return;
        if (book != null && book.current() != null) nameBook(false);
        // A stack of layers is one page: its number, and the count of them (PageStacks).
        PageStacks stacks = book == null ? null : book.stacks();
        int count = book == null ? 1 : stacks.count(), index = book == null ? 0 : stacks.of(book.index());
        // A cover's pattern made ahead, from the first and the last page (and on a cover), so turning to one needn't wait.
        if (book != null && (index <= 0 || index == count - 1)) CoverPattern.prepare(book.design(), book.name(), book.width, book.height, null);
        // Text and enabled states use Android's next frame. Artwork is already
        // submitted by showPage; updating the counter must not add an e-ink flash.
        updatePageRow(pageNumber, previousPage, nextPage, index, count);
        if (pagesButton != null) pagesButton.setContentDescription("Pages. Page " + (index+1) + " of " + count);
        if (popupPageNumber != null) updatePageRow(popupPageNumber, popupPrevious, popupNext, index, count);
        if (inkWashBar != null) { inkWashBar.pagesChanged(index, count, turned); inkWashBar.turner.timer(pageLocked(), false); }
        if (notebooks != null) notebooks.pagesChanged();
        timedLook();
        // The pages changed or another is drawn on: the ghosts follow, if what they are of changed.
        ghosts.changed();
    }
    private void updatePageRow(TextView label, View previous, View next, int index, int count) {
        boolean cover = DrawingBook.isCover(index);
        label.setText(index == DrawingBook.FRONT ? NotebookCovers.FRONT_WORD : index == DrawingBook.BACK ? NotebookCovers.BACK_WORD : (index+1) + " / " + count);
        label.setContentDescription((cover ? label.getText() : "Page " + (index+1) + " of " + count) + ". Show all pages");
        Ui.setDimmed(previous, book != null && index != DrawingBook.FRONT, .3f);
        Ui.setDimmed(next, book != null && index != DrawingBook.BACK, .3f);
    }
    /**
     * The open notebook's name for its covers' pattern and label ({@link DrawingBook#name}); with {@code show}, the cover
     * drawn on shows again if its paper changed (a rename).
     */
    private void nameBook(boolean show) {
        if (book == null) return;
        String name = DrawingFiles.name(drawingName);
        boolean renamed = !name.equals(book.name());
        if (book.name(name) && show && pad != null && pad.document == book.current()) pad.showPage(book.current());
        if (renamed && !name.isEmpty()) CoverPattern.prepare(book.design(), name, book.width, book.height, null);
    }
    /**
     * The cache folder's covers from before: "marbles" (before cover designs) and "covers" (before covers were windows of
     * their designs' sheets, CoverTexture, faster made than read back): never read again, so they go.
     */
    private void forgetOldCovers() {
        for (String folder : new String[]{"marbles", "covers"}) {
            File old = new File(getCacheDir(), folder);
            if (!old.isDirectory()) continue;
            new Thread(() -> {
                File[] files = old.listFiles();
                if (files != null) for (File f : files) f.delete();
                old.delete();
            }, "old covers").start();
        }
    }
    /**
     * The open notebook's covers in {@code design}, a change to save: the shelf or the notebook view shows them at once,
     * and a cover being drawn on takes its new paper once its pattern is made, in the background.
     */
    void setCoverDesign(CoverDesign design) {
        if (book == null || busy() || design == book.design()) return;
        DrawingBook changed = book;
        book.setDesign(design);
        recovery();
        if (notebooks.onShelf()) notebooks.showShelf(); else notebooks.pagesChanged();
        CoverPattern.prepare(design, book.name(), book.width, book.height, () -> runOnUiThread(() -> {
            if (destroyed || book != changed || busy()) return;
            if (book.refreshCovers() && pad.document == book.current()) pad.showPage(book.current());
        }));
    }
    // Notebook options (docs/archive/superpowers/specs/2026-10-19-notebook-options-design.md)

    /**
     * The open notebook's tools made {@code tools} from its library card ({@link CardSelector}): My Toolbar; Surprise Me or
     * Each Page, rolled (switching between them keeps the roll; Surprise Me again rolls anew); Library, then the Tools page
     * on the notebook's slots, which start as the toolbar it has, else the app's.
     */
    void chooseTools(NotebookOptions.Tools tools) {
        if (book == null || busy()) return;
        captureToolbar();
        NotebookOptions o = book.options();
        ToolSlots slots = inkWash.slots;
        boolean rolled = tools == NotebookOptions.Tools.SURPRISE || tools == NotebookOptions.Tools.EACH_PAGE;
        boolean wasRolled = o.tools == NotebookOptions.Tools.SURPRISE || o.tools == NotebookOptions.Tools.EACH_PAGE;
        if (tools == NotebookOptions.Tools.MINE) setNotebookOptions(o.withTools(tools, null));
        else if (rolled && wasRolled && tools != o.tools) setNotebookOptions(o.withTools(tools, o.toolbar));
        else if (rolled) {
            List<Brush> library = new ArrayList<>();
            for (BrushLibrary.Entry e : slots.library.entries()) library.add(e.brush);
            setNotebookOptions(o.withTools(tools, SurpriseTools.toolbar(SurpriseTools.roll(library, slots.swatches, new java.util.Random())).lines()));
        } else {
            ToolSlots.Toolbar own = ToolSlots.of(o);
            setNotebookOptions(o.withTools(tools, (own != null ? own : slots.appWide()).lines()));
            notebooks.hide(); openTools(Math.min(inkWash.selected, ToolSlots.COUNT - 1));
        }
    }
    /**
     * Page {@code page}'s own template made {@code template} (a hold on it in the notebook's strip, {@link CardSelector}), a
     * change to save: the page shows its guides at once if it is the one drawn on, and the copies are drawn again.
     */
    void setPageTemplate(int page, NotebookTemplate template) {
        if (book == null || busy()) return;
        NotebookTemplate was = book.template(page);
        pad.finishStroke();
        book.setPageTemplate(page, template);
        if (book.template(page).equals(was)) return;
        // Every layer of the stack shows its bottom page's guides.
        if (!DrawingBook.isCover(book.index()) && book.stacks().of(page) == book.stacks().of(book.index()) && pad.document == book.current()) pad.showPage(book.current());
        recoveryNow();
        if (notebooks.shown()) notebooks.pagesChanged();
        // Its sections (a Diary Page's) come or go.
        timedLook();
    }
    /**
     * Page {@code page}'s own rules made {@code rules} (Page Settings: No Erasing and Timed Pages, each the notebook's, or
     * Off or On for the page alone), a change to save; fixed while the notebook is locked. The bar and the clock follow.
     */
    void setPageRules(int page, int rules) {
        if (book == null || busy() || rules == book.pageRules(page)) return;
        boolean erasing = book.rules(page).noErasing;
        book.setPageRules(page, rules);
        rulesChanged();
        recoveryNow();
        // What No Erasing means, said as it is set on.
        if (!erasing && book.rules(page).noErasing) note(NotebookRules.NO_ERASING_SAID);
        if (notebooks.shown()) notebooks.pagesChanged();
    }
    /** Page {@code page}'s own time made {@code seconds} (Page Settings' slider, used while its Timed Pages is On), a change to save; fixed while locked. */
    void setPageSeconds(int page, int seconds) {
        if (book == null || busy() || seconds == book.pageSeconds(page)) return;
        book.setPageSeconds(page, seconds);
        rulesChanged();
        recoveryNow();
        if (notebooks.shown()) notebooks.pagesChanged();
    }
    /** The side column's sand timer ({@link SandTimer}): the app's, running across pages; whether it has the countdown's row now. */
    private final SandTimer sand = new SandTimer();
    private boolean sandRow;
    /** The sand timer's pick: {@code seconds} from now, or off (0). */
    void setSandTimer(int seconds) {
        sand.set(seconds, android.os.SystemClock.uptimeMillis());
        timedLook();
    }
    /** Page Settings' Page Colour: page {@code place}'s (a stack's) paper made {@code tone} on its bottom page, turned to, as Fill page does: one Undo step. */
    void shadeStack(int place, int tone) {
        if (book == null || busy() || place < 0 || place >= book.stacks().count()) return;
        int bottom = book.stacks().first(place);
        drawOn(bottom);
        if (book.index() != bottom || pad.document.paperTone() == tone) return;
        history(() -> pad.document.shadePaper(tone));
        if (notebooks.shown()) notebooks.pagesChanged();
    }
    /**
     * The open notebook's options made {@code options}, a change to save: its pages show the template's guides at once, its
     * toolbar is loaded (or the app's put back), and its copies are drawn again.
     */
    void setNotebookOptions(NotebookOptions options) {
        if (book == null || busy() || options.equals(book.options())) return;
        NotebookOptions was = book.options();
        pad.finishStroke();
        book.setOptions(options);
        syncToolbar();
        // The guides are the page's paper: it shows again with them, or without; a blank page drawn on takes a new Page Colour.
        if ((!options.template.equals(was.template) || options.guidesShown != was.guidesShown || options.paper != was.paper) && pad.document == book.current()) pad.showPage(book.current());
        recoveryNow();
        if (notebooks.shown()) notebooks.pagesChanged();
    }
    /**
     * The toolbar the screen calls for: the open notebook's own while it is open and not on the shelf, else the app's
     * ({@link ToolSlots#enter}, {@link ToolSlots#leave}); the notebook's last changes kept before the app's is put back.
     * Told whenever the notebook or the screen changes ({@link NotebookScreens}, {@link #replaceBook}).
     */
    void syncToolbar() {
        if (inkWash == null) return;
        ToolSlots slots = inkWash.slots;
        ToolSlots.Toolbar own = book != null && notebooks != null && !notebooks.onShelf() ? slots.forPage(book.options(), book.stacks().of(book.index())) : null;
        if (own == null) captureToolbar();
        // Each Page's, or a locked notebook's own toolbar: rolls and picks change nothing.
        slots.frozen = own != null && book.options().toolbarFrozen();
        if (own != null ? slots.enter(own) : slots.leave()) refreshToolbar();
        rulesChanged();
    }

    // Rules and Lock (docs/archive/superpowers/specs/2026-10-19-notebook-options-design.md)

    /** The options in force on the drawing screen: the open notebook's, else the defaults (the shelf, a drawing that isn't a notebook). */
    /**
     * No Erasing: a finger whose choice is the eraser pans the page instead (with Zoom unlocked, as two fingers do), and
     * Finger says and shows Pan (the owner, 2026-10-21).
     */
    boolean fingerPans() { return inkWash != null && inkWash.finger() == FingerTool.Choice.ERASER && NotebookRules.keeps(rules(), true); }
    NotebookOptions rules() { return rules(book == null ? 0 : book.index()); }
    /** The rules in force on page {@code page}: the notebook's with the page's own over them ({@link NotebookRules#forPage}), else the defaults. */
    NotebookOptions rules(int page) { return book != null && notebooks != null && !notebooks.onShelf() ? book.rules(page) : NotebookOptions.DEFAULT; }
    /** Whether the page shown is locked (its time ran out under Lock Pages, or a PDF's page): no marks, no Undo, no Redo. */
    boolean pageLocked() { return pad != null && pad.document != null && pad.document.locked(); }
    /** Whether {@code tool} (a slot, the eraser or the smudge tool) may draw here now; if not, the note why. Locked page and No Erasing and Brushes alike. */
    boolean allowStroke(int tool) {
        if (pageLocked()) { note(NotebookRules.PAGE_LOCKED); return false; }
        if (NotebookRules.refuses(rules(), tool, inkWash.brush(tool))) { note(NotebookRules.ERASING_OFF); return false; }
        // Show Kinds: nothing is drawn, or erased, unseen.
        String hidden = hiddenNote(tool);
        if (hidden != null) { note(hidden); return false; }
        // Marbling's order (ToolKits): clear water or a comb with nothing floating moves nothing; another brush prints the rings.
        Brush drawn = inkWash.brush(tool);
        FloatWater floating = inkWash.session.floating();
        boolean afloat = floating != null && pad.document != null && floating.valid(pad.document);
        String floatNote = ToolKits.floatNote(drawn == null ? null : drawn.parts, drawn == null ? null : inkWash.session.onWater(pad.document, drawn.parts), afloat);
        if (floatNote != null && (drawn != null || tool == ToolSlots.ERASER)) note(floatNote);
        // A fresh page of timed sections starts them at its first mark.
        ToneDocument sectioned = sectionedPage();
        if (sectioned != null && !sectioned.sections().started() && sectioned.inkless()
                && sectioned.setSections(TimedSections.start(sectionTimes, System.currentTimeMillis()))) recovery();
        timed.strokeBegins(android.os.SystemClock.uptimeMillis());
        timedLook();
        return true;
    }
    /**
     * Show Kinds: why tool {@code tool} may not draw on the page drawn on, the kind it lays hidden ({@link EraseKind#hidden}),
     * or the eraser's every kind hidden: "Pen is hidden, see Show Kinds"; null when it may.
     */
    String hiddenNote(int tool) {
        int shown = shownKinds(), hidden = EraseKind.hidden(shown);
        return EraseKind.refusal(shown, tool != InkWash.ERASER ? inkWash.kindOf(tool) : hidden != 0 && inkWash.erasing() == 0 ? hidden : 0);
    }
    /** Show Kinds' steps on the page drawn on ({@link ToneDocument#kindsShown}); 0, all Full, with none. */
    int shownKinds() { return pad == null || pad.document == null ? 0 : pad.document.kindsShown(); }
    /** Show Kinds: tool {@code tool} taken up, the kind it lays not Full says so ("Pen is hidden", "Dry media is faint"). */
    void sayKind(int tool) {
        if (tool == InkWash.ERASER) return;
        int choice = EraseKind.choiceOf(inkWash.kindOf(tool)), step = EraseKind.step(shownKinds(), choice);
        if (choice > 0 && step != NotebookOptions.FULL) message(EraseKind.said(choice, step));
    }
    /** {@link #message}, but not again within a few seconds (a finger drag asks at every touch). */
    void note(String text) {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastNote < 3000) return;
        lastNote = now; message(text);
    }
    private long lastNote = -10000;
    /** The header's Undo and Redo, disabled under No Erasing. */
    private ToolButton headerUndo, headerRedo;
    /** The page's clock, the open notebook's Timed Pages ({@link TimedPages}). */
    final TimedPages timed = new TimedPages();
    private final Runnable timedTick = this::timedLook;
    /** The rules changed (a notebook opened, left or its options changed): the bar and the clock follow. */
    private void rulesChanged() {
        if (inkWashBar != null) inkWashBar.rulesChanged(rules());
        // No Erasing: Undo and Redo show disabled (the header's, where it is shown, as the bar's).
        boolean undoOff = NotebookRules.undoOff(rules());
        for (ToolButton b : new ToolButton[]{headerUndo, headerRedo}) if (b != null) { b.setEnabled(!undoOff); b.setAlpha(undoOff ? .3f : 1f); }
        timedLook();
    }
    /** Counting stops and the time so far is given to the page: before it is left, packed or saved, or the app paused. */
    void pauseTimer() {
        // A wash still settling is laid first: the page's clock is a change to it, which would end the settling.
        if (pad != null && timed.running()) pad.settleDrying();
        timed.pause(android.os.SystemClock.uptimeMillis());
    }
    /** The time so far goes to the page for a save, unless a wash is settling (the next save has it). */
    private void commitTimer() { if (!inkWash.session.wet()) timed.commit(android.os.SystemClock.uptimeMillis()); }
    /**
     * Looks at the clock and the bar's page number: counting only while the page shows (the app resumed, no notebook screen
     * over it); the number blinks in the last seconds and at zero Time's up shows (the page locks under Lock Pages, else stays
     * editable), then the next look is set.
     */
    void timedLook() {
        if (pad == null) return;
        pad.removeCallbacks(timedTick);
        if (destroyed) return;
        long now = android.os.SystemClock.uptimeMillis();
        NotebookOptions options = rules();
        ToneDocument page = book != null && pad.document != null ? pad.document : null;
        boolean showing = page != null && resumed && !loading && notebooks != null && !notebooks.shown();
        // A page of timed sections (the Diary Page) is timed by them: Timed Pages leaves it alone.
        ToneDocument sectioned = sectionedPage();
        // A cover is never timed (the owner, 2026-10-21): no clock, no countdown, no lock.
        boolean cover = book != null && DrawingBook.isCover(book.index());
        timed.configure(NotebookRules.timedSeconds(options, book != null ? book.index() : 0, sectioned != null), now);
        timed.sync(page, showing, now);
        // At zero a wash still settling is laid first (the clock is a change to the page, which would end its settling).
        if (timed.running() && timed.remaining(now) <= 0 && !pad.drawingNow()) pad.settleDrying();
        TimedPages.State state = timed.tick(now, pad.drawingNow());
        if (state == TimedPages.State.TIME_UP) {
            // The page is as it was at zero; its clock, run out, is kept in its record. It stays editable (the owner, 2026-10-27:
            // "it's just a guide"), unless the notebook's Lock Pages is on ({@link ToneDocument#locked}). The sand timer's popup,
            // with Next Page (the owner, 2026-10-21): on the last page, a new page after it, as the page bar's + adds.
            recovery();
            if (inkWashBar != null) inkWashBar.pageChanged();
            if (inkWashBar != null) inkWashBar.timeUp(() -> {
                if (busy() || book == null) return;
                PageStacks s = book.stacks();
                if (s.after(s.of(book.index())) == DrawingBook.BACK) newPage(); else turnPage(1);
            });
        }
        long next = timed.nextLook(now);
        // The sand timer counts while the app is resumed, whatever the page; at zero, once the pen is up, its popup, the
        // page as it was.
        if (resumed) sand.resume(now); else sand.pause(now);
        boolean sandWaits = sand.on() && sand.remaining(now) <= 0 && pad.stroking();
        if (!sandWaits && sand.ended(now) && inkWashBar != null) inkWashBar.timeUp(null);
        if (inkWashBar != null) {
            // The blink and the countdown are small updates of their own squares, and wait for the pen to be up (as the wet droplet's does).
            boolean hidden = timed.numberHidden(now), stroking = pad.stroking();
            inkWashBar.turner.timer(page != null && page.locked(), stroking ? inkWashBar.turner.numberHidden() : hidden);
            if (stroking && hidden != inkWashBar.turner.numberHidden()) next = 150;
            // A page out of time shows the row as an untimed page does; a locked one its 0:00.
            String countdown = options.seconds > 0 && page != null && !cover && (timed.remaining(now) > 0 || page.locked()) ? NotebookRules.countdown(timed.remaining(now)) : null;
            if (sectioned != null) {
                long[] look = {next};
                countdown = sectionLook(sectioned, showing, pad.drawingNow(), look);
                next = look[0];
            } else if (page != null) { page.fence(null); pad.showSections(null, -1); }
            // The row with no Timed Pages countdown is the sand timer's: its countdown while it runs, else its icon (never on
            // a cover or a locked page, where there is no drawing to time).
            sandRow = countdown == null;
            boolean icon = false;
            if (sandRow && sand.on()) countdown = NotebookRules.countdown(sand.remaining(now));
            else icon = sandRow && page != null && !cover && !page.locked();
            if (!inkWashBar.countdown(countdown, icon, stroking)) next = 150;
        }
        long sandNext = sandWaits ? 150 : sand.nextLook(now);
        if (sandNext >= 0 && (next < 0 || sandNext < next)) next = sandNext;
        if (next >= 0) pad.postDelayed(timedTick, next);
        if (showing) promptLook(page);
    }
    // Timed Sections (TimedSections): the Diary Page's boxes timed in order.

    /** The page shown when its template's boxes are timed ({@link NotebookTemplate#timed}), else null; its times read for it ({@link #sectionTimes}). */
    private ToneDocument sectionedPage() {
        if (book == null || pad == null || pad.document == null || pad.document != book.current() || notebooks == null || notebooks.onShelf()) return null;
        NotebookTemplate template = book.template(book.index());
        if (!template.timed()) return null;
        if (!template.equals(sectionTemplate) || sectionTimesFor != pad.document) {
            // Read once a page shown: the owner edits the times in the prompts file.
            sectionTemplate = template; sectionTimesFor = pad.document;
            sectionTimes = DailyPrompt.sectionTimes(DrawingStorage.sharedAllowed(this) ? DrawingStorage.shared() : null, template);
        }
        return pad.document;
    }
    /** The template whose times {@link #sectionTimes} are, and the times, s. */
    private NotebookTemplate sectionTemplate;
    private ToneDocument sectionTimesFor;
    private int[] sectionTimes;
    /** The page whose sections the screen last showed, and its running section then ({@link TimedSections#active}). */
    private ToneDocument sectionPage;
    private int sectionShown;
    /**
     * Looks at {@code page}'s sections: under Lock Pages a section ended a day ago is fenced off ({@link ToneDocument#fence}), the running one
     * outlined and the rest dimmed, and a section that has just ended flashes, then its note ("Next: Heard", "Done"); all
     * waiting for the pen to lift. The countdown's words; {@code next[0]} the next look, ms.
     */
    private String sectionLook(ToneDocument page, boolean showing, boolean stroking, long[] next) {
        long wall = System.currentTimeMillis();
        TimedSections s = page.sections();
        String[] names = sectionTemplate.sections;
        int[][] rects = sectionTemplate.sectionRects(page.width, page.height);
        java.util.List<int[]> locked = new ArrayList<>();
        boolean locks = rules().lockPages;
        for (int i = 0; i < rects.length; i++) if (locks && s.locked(i, wall)) locked.add(rects[i]);
        page.fence(locked.toArray(new int[0][]));
        int active = s.active(wall), n = s.count();
        if (stroking) next[0] = 150;
        else {
            if (page != sectionPage) { sectionPage = page; sectionShown = active; }
            else if (active != sectionShown) {
                int was = sectionShown;
                sectionShown = active;
                String note = active < n ? TimedSections.NEXT + names[active] : TimedSections.DONE;
                if (was >= 0 && was < rects.length && showing) pad.flashSection(rects[was], () -> message(note));
            }
            pad.showSections(rects, s.started() && active < n ? active : -1);
            long change = s.nextChange(wall);
            // Over a day's wait the next look is in ten minutes (the monotonic clock stops while the device sleeps).
            next[0] = change < 0 ? -1 : Math.min(change, 600_000);
        }
        if (!s.started()) return TimedSections.countdown(names[0], sectionTimes[0] * 1000L);
        if (active >= n) return TimedSections.countdown(null, 0);
        long left = s.remaining(wall);
        if (!stroking) next[0] = Math.max(50, Math.min(next[0], NotebookRules.nextCountdown(left)));
        return TimedSections.countdown(names[active], left);
    }
    /**
     * A tap on the side bar's countdown or its sand timer: on a page of sections they start, or the running one ends and the
     * next starts; where the row is the sand timer's, its choices ({@link InkWashBar#timerMenu}); a Timed Pages countdown's, nothing.
     */
    void countdownTapped() {
        if (busy()) return;
        ToneDocument page = sectionedPage();
        if (page == null) {
            if (sandRow && inkWashBar != null) inkWashBar.timerMenu(sand.seconds());
            return;
        }
        if (pageLocked()) return;
        TimedSections s = page.sections(), next = s.started() ? s.skip(System.currentTimeMillis()) : TimedSections.start(sectionTimes, System.currentTimeMillis());
        if (page.setSections(next)) recovery();
        timedLook();
    }
    /** The pages whose prompt has shown this session (the notebook and the page), so each shows once. */
    private final java.util.Set<String> promptsShown = new java.util.HashSet<>();
    /** The page shown has a Self-Portrait Prompt card: it shows, once a session (on opening the app too, which reopens the last page). */
    private void promptLook(ToneDocument page) {
        if (page.prompt() == null || busy() || pad.drawingNow() || !promptsShown.add(drawingName + "\n" + book.index())) return;
        // Found with nothing on the page but the card (the app closed without a pause): it goes, unshown.
        if (page.dropUnusedPrompt()) { recovery(); return; }
        DailyPrompt.Card card = DailyPrompt.of(page.prompt());
        if (card != null) promptCard(card, DailyPrompt.heading(card), null);
    }
    /**
     * The page drawn on is being left (another page, the shelf, the app paused): its Self-Portrait Prompt card goes if nothing was
     * done with it ({@link ToneDocument#dropUnusedPrompt}), before the book keeps the page. True when it went (a change to save).
     */
    private boolean leavePrompt() {
        if (book == null || pad.document == null || pad.document != book.current()) return false;
        pad.finishStroke(); // a stroke still down is drawing
        return pad.document.dropUnusedPrompt();
    }
    /** The slots as the open notebook's own toolbar, if it has one and it is loaded: a change to save. */
    void captureToolbar() {
        if (book == null || inkWash == null || !inkWash.slots.inNotebook() || ToolSlots.of(book.options()) == null) return;
        book.setOptions(book.options().withToolbar(inkWash.slots.toolbar().lines()));
    }
    /** Every slot's button shows its slot as it is now. */
    private void refreshToolbar() {
        if (inkWashBar == null) return;
        for (int i = 0; i < ToolSlots.COUNT; i++) inkWashBar.showBrush(i);
    }
    /**
     * Page {@code page} as the front cover's picture, or none (-1): from the menu (Use This Page on the Cover, No Cover
     * Picture) or inside the notebook (Use on Cover, No Picture). The front cover, if it is drawn on, shows its paper again.
     */
    void setCoverPicture(int page) {
        if (book == null || busy() || page != -1 && !book.valid(page)) return;
        book.setCover(page);
        if (book.refreshCovers() && pad.document == book.current()) pad.showPage(book.current());
        recovery();
        notebooks.pagesChanged();
    }
    /** The menu's cover picture rows: on a page, Use This Page on the Cover; on the front cover, or on the page that is the picture, No Cover Picture. */
    private void replaceBook(DrawingBook replacement) {
        if (inkWash != null) inkWash.drawn.clear();
        layerUndos.clear();
        // The notebook left keeps its toolbar as it stands; the one opened loads its own (or the app's comes back).
        captureToolbar();
        pad.finishStroke(); pauseTimer();
        if (replacement == null) { pad.replace(null); syncToolbar(); return; }
        // An old notebook's blank page at its end goes (BlankPages.opened): the page bar's + adds one now.
        try { BlankPages.opened(replacement); }
        catch (java.io.IOException | RuntimeException error) { Log.w(ProbeActivity.TAG, "The blank page at the end stays", error); }
        pad.finishStroke(); book = replacement; pad.showPage(book.current()); updatePages(); pad.post(fullscreen::pageChanged);
        syncToolbar();
        // A blank page's bytes made ahead off the main thread, so its blank pages are told by them.
        BlankPages.prepare(book.width, book.height, book.options().paper);
    }
    private void changePage(int index) {
        if (busy() || !book.valid(index)) return;
        nameBook(false);
        // Settled while the page is still the book's current one, so the page kept is the settled one.
        pad.finishStroke(); pad.dryWet(); pad.settleDrying(); pauseTimer();
        leavePrompt();
        notebooks.leavingPage();
        try { book.select(index); pad.showPage(book.current()); pad.pageTurned(); updatePages(true); syncToolbar(); recoveryNow(); fullscreen.pageChanged(); }
        catch (java.io.IOException error) { message("Could not open page: " + error.getMessage()); }
    }
    private void addPage() { addPage(() -> book.addPage()); }
    /** The page bar's + tile: a new page at the end, turned to ({@link DrawingBook#insertPage}); a full notebook says so. */
    void newPage() {
        if (book == null) return;
        if (book.count() >= DrawingBook.MAX_PAGES) { note(DrawingBook.PAGES_FULL); return; }
        addPage(() -> book.insertPage());
    }
    /**
     * A page added by {@code add} and turned to; with the notebook's Self-Portrait Prompt on ({@link NotebookOptions#selfPortrait}),
     * dealt a card of its own ({@link #dailyPrompt}). A page only gets one as it is added: one left unused goes as any
     * card does ({@link #leavePrompt}) and is not dealt again on coming back, so a blank page never asks twice.
     */
    private void addPage(BookEdit add) {
        int pages = book.count();
        pageEdit("add", add);
        if (book.count() <= pages || !book.options().selfPortrait) return;
        // Read each time: the owner edits the cards' file beside the notebooks.
        List<DailyPrompt.Card> cards = DailyPrompt.load(DrawingStorage.sharedAllowed(this) ? DrawingStorage.shared() : null);
        dailyPrompt(cards, new java.util.Random().nextInt(cards.size()));
    }
    /** Inserts a copy of the current page after it and turns to the copy. */
    private void duplicatePage() { pageEdit("duplicate", () -> book.duplicatePage()); }
    private void deletePage() { if (erasingOff(BlankPages.deleteAsks(book, book.index()))) return; pageEdit("delete", () -> book.deletePage()); }
    void clearPage() { if (erasingOff()) return; layerChange(() -> pad.document.clearAllLayers()); }
    private void clearLayer() { if (erasingOff()) return; layerChange(() -> pad.document.clear()); }
    /** No Erasing is on here: the note is given, and whatever erases is not done. */
    boolean erasingOff() { return erasingOff(true); }
    /** As {@link #erasingOff()}, for a page or layer that is {@code marked} (a blank one may still go: {@link NotebookRules#keeps}). */
    boolean erasingOff(boolean marked) { return erasingOff(rules(), marked); }
    /** As {@link #erasingOff(boolean)}, under {@code rules} (another page's). */
    private boolean erasingOff(NotebookOptions rules, boolean marked) {
        if (!NotebookRules.keeps(rules, marked)) return false;
        note(NotebookRules.ERASING_OFF);
        return true;
    }
    private interface BookEdit { void run() throws java.io.IOException; }
    /** Changes the pages with {@code edit}, which selects the page to show, and shows it. */
    private void pageEdit(String verb, BookEdit edit) {
        if (busy()) return;
        pad.finishStroke(); pad.dryWet(); pad.settleDrying(); pauseTimer();
        leavePrompt();
        notebooks.leavingPage();
        try { edit.run(); pad.showPage(book.current()); pad.pageTurned(); updatePages(); syncToolbar(); recoveryNow(); fullscreen.pageChanged(); }
        catch (java.io.IOException error) { message("Could not " + verb + " page: " + error.getMessage()); }
    }
    /** The compact layout's Pages box: the page row with Add page, above the other page actions. */
    private void showPagePanel() {
        if (!beginPagePanel()) return;
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_HORIZONTAL);
        popupPrevious = pageAction("Previous page", R.drawable.ic_ph_caret_left, () -> turnPage(-1), false);
        popupNext = pageAction("Next page", R.drawable.ic_ph_caret_right, () -> turnPage(1), false);
        popupPageNumber = Theme.style(new TextView(this), TypeStyle.BODY);
        popupPageNumber.setOnClickListener(v -> showPageOverview());
        popupPageNumber.setTypeface(Theme.face(700)); popupPageNumber.setGravity(Gravity.CENTER);
        row.addView(popupPrevious, new LinearLayout.LayoutParams(dp(48), dp(48)));
        row.addView(popupPageNumber, new LinearLayout.LayoutParams(dp(76), dp(48)));
        row.addView(popupNext, new LinearLayout.LayoutParams(dp(48), dp(48)));
        addPageItem(row);
        updatePageRow(popupPageNumber, popupPrevious, popupNext, book.index(), book.count());
        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.CENTER_HORIZONTAL); addPageChanges(actions);
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.addView(row, new LinearLayout.LayoutParams(-1, dp(48)));
        box.addView(actions, new LinearLayout.LayoutParams(-1, dp(48)));
        showPagePanel(pagesButton, box, 5*dp(48), 2*dp(48));
    }
    /**
     * The page actions in a column below the header's page button; compact layouts show them in
     * the Pages box instead.
     */
    private void showPageMenu() {
        if (compactLayout()) { showPagePanel(); return; }
        if (!beginPagePanel()) return;
        LinearLayout column = new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL);
        addPageItem(column); addPageChanges(column);
        showPagePanel(pageMenuButton, column, dp(48), 6*dp(48));
    }
    private void addPageItem(LinearLayout menu) {
        pageMenuItem(menu, "Add page", R.drawable.ic_ph_file_plus, this::addPage, book.count() < DrawingBook.MAX_PAGES);
    }
    /** Adds the actions that change the current page; deleting it, which can't be undone, asks first unless it is blank. */
    private void addPageChanges(LinearLayout menu) {
        // A cover is neither copied nor deleted.
        boolean page = !DrawingBook.isCover(book.index());
        // No Erasing keeps a page with marks (as Clear page).
        boolean kept = page && NotebookRules.keeps(rules(), BlankPages.deleteAsks(book, book.index()));
        pageMenuItem(menu, "Duplicate page", R.drawable.ic_ph_files, this::duplicatePage, page && book.count() < DrawingBook.MAX_PAGES);
        pageMenuItem(menu, "Delete page", R.drawable.ic_ph_file_x, () -> {
            if (DrawingBook.isCover(book.index()) || erasingOff(BlankPages.deleteAsks(book, book.index()))) return;
            if (!BlankPages.deleteAsks(book, book.index())) { deletePage(); return; }
            confirmPageAction("Delete page " + (book.index()+1) + "?", "You can't undo this.", "Delete", this::deletePage);
        }, page && book.count() > 1 && !kept);
        pageMenuItem(menu, "Replay page", R.drawable.ic_ph_play, () -> replay.choose(compactLayout() ? pagesButton : pageMenuButton), replay.available());
        pageMenuItem(menu, "Clear page", R.drawable.ic_ph_broom, this::clearPage, !rules().noErasing);
        pageMenuItem(menu, "Clear current layer", R.drawable.ic_ph_eraser, this::clearLayer, !rules().noErasing);
    }
    private View pageMenuItem(LinearLayout menu, String name, int icon, Runnable action, boolean enabled) {
        // Like the page row, act while the panel is open so page artwork reaches the panel at once.
        View item = pageAction(name, icon, () -> { action.run(); closePagePanel(); }, false);
        Ui.setDimmed(item, enabled, .3f);
        menu.addView(item, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return item;
    }
    private void confirmPageAction(String title, String text, String verb, Runnable action) {
        closePagePanel();
        AlertDialog dialog = showDialog(new AlertDialog.Builder(this).setTitle(title).setMessage(text)
                .setPositiveButton(verb, (unused, which) -> { if (!busy()) action.run(); })
                .setNegativeButton("Cancel", null));
        pageConfirmation = dialog;
        dialog.setOnDismissListener(unused -> { if (pageConfirmation == dialog) pageConfirmation = null; });
    }
    /** Closes an open page panel, or prepares the canvas for a new one; false when none should open. */
    private boolean beginPagePanel() {
        if (pagePanel != null) { closePagePanel(); return false; }
        if (busy()) return false;
        dismissPanels(); hideGradientHint();
        pad.suspend();
        return true;
    }
    /**
     * Shows {@code content} ({@code width} × {@code height} pixels, without its border) in an
     * outlined panel beside {@code button}, drawn through the same fast path as toolbar selection.
     */
    private void showPagePanel(View button, View content, int width, int height) {
        content.setPadding(dp(4), dp(4), dp(4), dp(4));
        content.setBackground(Theme.menu(this));
        RectF anchor = Popups.bounds(root, button);
        // Sum rounded child pixels: at density 300, dp(56) is one pixel shorter
        // than dp(48)+2*dp(4), which clips buttons and disables direct feedback.
        width += 2*dp(4); height += 2*dp(4);
        float left = landscape ? (toolboxRight ? anchor.left-width : anchor.right) : anchor.right-width;
        float top = landscape ? anchor.bottom-height : anchor.bottom;
        showPanelAt(content, Popups.rect(Popups.clamp(left, 0, root.getWidth()-width), Popups.clamp(top, 0, root.getHeight()-height), width, height));
    }
    /**
     * Shows {@code content} in a panel over the canvas at {@code bounds} ({@link #root}'s coordinates), drawn through the
     * same fast path as toolbar selection; a touch outside closes it ({@link #closePagePanel}).
     */
    void showPanelAt(View content, RectF bounds) {
        QuarterTurnLayout panel = new QuarterTurnLayout(this); panel.setTurn(appTurn()); panel.addView(content);
        // The panel is a child of the preview frame, not a popup window.
        PanelCoordinates.fromView(root).mapRect(bounds);
        Matrix inverse = new Matrix(); PanelCoordinates.fromView(previewFrame).invert(inverse);
        inverse.mapRect(bounds); bounds.roundOut(pagePanelBounds);
        pagePanel = panel; pagePanelByFinger = Ui.fingerPress();
        // Lay out the real touch targets synchronously, then submit only their
        // bounded pixels through the same fast path as toolbar selection.
        selectionFeedback.update(previewFrame, new Rect(pagePanelBounds), () -> previewFrame.showPanel(panel, pagePanelBounds));
    }
    /** {@code change} to the open panel, shown through the fast path. */
    void updatePanel(Runnable change) { if (pagePanel != null) selectionFeedback.update(previewFrame, new Rect(pagePanelBounds), change); else change.run(); }
    void closePagePanel() { closePagePanel(true); }
    /** Closes an open page panel; not {@code shown}: without showing what was under it, as the page turn that follows shows the page. */
    void closePagePanel(boolean shown) {
        if (pagePanel == null) return;
        Rect bounds = new Rect(pagePanelBounds); pagePanel = null;
        if (shown) selectionFeedback.update(previewFrame, bounds, previewFrame::hidePanel); else previewFrame.hidePanel();
        pagePanelBounds.setEmpty(); popupPageNumber = null; popupPrevious = null; popupNext = null;
        pad.post(pad::connectDisplay);
        // Inkwash: what it covered gets the clean pass, as a menu's does (no ghost; the owner, 2026-10-23).
        if (shown) { RectF covered = new RectF(bounds); PanelCoordinates.fromView(previewFrame).mapRect(covered); pad.cleanArea(covered); }
    }
    /** The thumbnail grid of every page. */
    private void showPageOverview() {
        if (busy() || pageOverview != null) return;
        dismissPanels(); hideGradientHint();
        pad.suspend();
        DrawingBook source = book;
        PageOverview grid = new PageOverview(this, source.snapshot());
        // Bound the scrolling content in app coordinates, including rotated Nomad mode.
        grid.setLayoutParams(new FrameLayout.LayoutParams(-1, Math.max(dp(120), Math.min(dp(540), root.getHeight()-dp(180)))));
        FrameLayout content = new FrameLayout(this); content.addView(grid);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Pages").setView(content)
                .setNegativeButton("Close", null).create();
        pageOverview = dialog;
        grid.setOnItemClickListener((parent, view, index, id) -> {
            dialog.dismiss();
            if (book == source && index != book.index()) changePage(index);
        });
        // A hold on a page: its Page Settings.
        grid.setOnItemLongClickListener((parent, view, index, id) -> {
            dialog.dismiss();
            if (book == source && index < book.count()) notebooks.showPageSettings(book.stacks().of(index), true);
            return true;
        });
        dialog.setOnDismissListener(unused -> {
            grid.close(); if (pageOverview == dialog) pageOverview = null; pad.post(pad::connectDisplay);
        });
        dialog.show(); compactDialog(dialog, 600); grid.setSelection(source.index());
    }

    // Orientation and layout

    /** Lays the screen out for the current rotation and drawing hand. */
    void applyToolboxSide() {
        edgeBars.reset();
        closePagePanel();
        dismiss(filePopup); dismiss(layersPopup);
        boolean right = prefs.toolboxRight();
        toolboxRight = right;
        if (pad != null) { pad.finishStroke(); pad.disconnectDisplay(); }
        // Read the side column from menu/undo at the top to page controls at the bottom.
        toolbarTurn = landscape ? 90 : 0;
        shadePicker.setContentDescription(!landscape
                ? "Gray gradient. Tap or drag from black on the left to white on the right."
                : "Gray gradient. Tap or drag from black at the bottom to white at the top.");
        // Follow the shade endpoints in portrait and landscape.
        LinearLayout colors = (LinearLayout)shadePicker.getParent();
        colors.removeView(eraseButton); colors.removeView(eyedropperButton);
        colors.addView(landscape ? eraseButton : eyedropperButton, colors.indexOfChild(shadePicker));
        colors.addView(landscape ? eyedropperButton : eraseButton, colors.indexOfChild(shadePicker) + 1);
        paletteFrame.setTurn(toolbarTurn); wetControls.setTurn(-toolbarTurn);
        root.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.removeView(paletteFrame);
        root.addView(paletteFrame, landscape && right ? root.getChildCount() : 0,
                new LinearLayout.LayoutParams(landscape ? dp(48) : -1, landscape ? -1 : dp(48)));
        body.setLayoutParams(new LinearLayout.LayoutParams(landscape ? 0 : -1, landscape ? -1 : 0, 1));
        body.setOrientation(landscape ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        body.removeView(toolbar.toolScroll); body.removeView(toolbar.landscapeTools);
        toolbar.orient(landscape);
        // Both drawing hands keep tools above the canvas in either landscape direction.
        if (landscape) body.addView(toolbar.landscapeTools, 0, new LinearLayout.LayoutParams(-1, dp(64)));
        else body.addView(toolbar.toolScroll, right ? body.getChildCount() : 0, new LinearLayout.LayoutParams(dp(64), -1));
        arrangeHeader(right);
        toolbar.rebuildTools();
        fullscreen.applyVisibility();
        Ui.invalidateTree(palette);
        pad.updateViewport(); pad.invalidate(); pad.post(pad::connectDisplay);
        showInkWashBar();
    }
    /**
     * Inkwash shows only its own toolbar beside the page. MonoPaint's header, color bar and side
     * toolbar are still built, so code that refers to them keeps working, but never shown.
     */
    private void showInkWashBar() {
        paletteFrame.setVisibility(View.GONE);
        toolbar.toolScroll.setVisibility(View.GONE); toolbar.landscapeTools.setVisibility(View.GONE);
        // Inkwash's arrangement in either turn: one column down the drawing hand's side (MonoPaint's tool column's place
        // in the body row) and beside it the page bar over the pad (the owner, 2026-10-23: "moving the pages bar to the
        // top"); along the foot Zen Mode's corner under the column and the brush line from just past it, the page shorter
        // by the bar and the line (DrawLayout.pageBar, BRUSH_LINE).
        root.setOrientation(LinearLayout.VERTICAL);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));
        pageArea.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1));
        pad.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));
        DrawLayout layout = inkWashBar.layout();
        Ui.detach(inkWashBar.view);
        body.addView(inkWashBar.view, toolboxRight ? body.getChildCount() : 0, new LinearLayout.LayoutParams(dp(layout.side), -1));
        inkWashBar.cornered(toolboxRight);
        Ui.detach(inkWashBar.turner.view);
        pageArea.addView(inkWashBar.turner.view, 0, new LinearLayout.LayoutParams(-1, dp(layout.pageBar())));
        if (footRow == null) {
            footRow = new LinearLayout(this); footRow.setOrientation(LinearLayout.HORIZONTAL); zenCorner = new View(this);
            // The name's most room follows the screen as laid out (a turn): what the brush line leaves past its least.
            footRow.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> { if (r - l != or - ol) footRow.post(this::fitBookName); });
        }
        Ui.detach(footRow); footRow.removeAllViews(); Ui.detach(inkWashBar.brushLine);
        footRow.addView(zenCorner, new LinearLayout.LayoutParams(dp(layout.lineStart()), -1));
        LinearLayout.LayoutParams linePlace = new LinearLayout.LayoutParams(0, dp(DrawLayout.BRUSH_LINE), 1);
        linePlace.gravity = Gravity.TOP;  // the row's top is the line's top; the room under it centres the line on Zen Mode's icon
        footRow.addView(inkWashBar.brushLine, toolboxRight ? 0 : 1, linePlace);
        // The notebook's name in the foot's far corner, beside the line (never over it), away from Zen Mode's button, as
        // wide as its words up to what the line leaves (DrawLayout.bookName).
        Ui.detach(inkWashBar.bookName);
        fitBookName();
        LinearLayout.LayoutParams namePlace = new LinearLayout.LayoutParams(-2, dp(DrawLayout.BRUSH_LINE));
        namePlace.gravity = Gravity.TOP;
        footRow.addView(inkWashBar.bookName, toolboxRight ? 0 : footRow.getChildCount(), namePlace);
        inkWashBar.bookName.setGravity(Gravity.CENTER_VERTICAL | (toolboxRight ? Gravity.LEFT : Gravity.RIGHT));
        // As far in from the screen's side as its words sit up from the screen's foot, about 28 dp (the owner, 2026-10-27:
        // "put a bit of right padding, like, a tab", "equal distance as from the bottom of the screen").
        inkWashBar.bookName.setPadding(toolboxRight ? dp(28) : 0, 0, toolboxRight ? 0 : dp(28), 0);
        inkWashBar.bookName(DrawingFiles.name(drawingName));
        root.addView(footRow, new LinearLayout.LayoutParams(-1, dp(layout.footHeight())));
        inkWashBar.arrange(toolboxRight);
        // Zen Mode: the column, the brush line and the page bar gone, the pad the whole screen (the page fitted to it again).
        inkWashBar.view.setVisibility(zen ? View.GONE : View.VISIBLE);
        inkWashBar.turner.view.setVisibility(zen ? View.GONE : View.VISIBLE);
        footRow.setVisibility(zen ? View.GONE : View.VISIBLE);
        inkWashBar.brushLine.setVisibility(zen ? View.GONE : View.VISIBLE);
        showZen();
    }

    /** The notebook's name at most what the brush line leaves past its least on the screen as it is ({@link DrawLayout#bookName}). */
    private void fitBookName() {
        int most = Math.max(0, dp(inkWashBar.layout().bookName()));
        if (inkWashBar.bookName.getMaxWidth() != most) inkWashBar.bookName.setMaxWidth(most);
    }

    // Zen Mode (DrawLayout.ZEN)

    /** Whether Zen Mode is on: no column, no brush line, no page bar. Not kept: a new launch has the bars. */
    boolean zen;
    private ToolButton zenButton;
    /** Zen Mode's one button, both ways, no background: the dashed circle while off, the circle while on. */
    private ToolButton zenButton() {
        ToolButton button = iconButton(DrawLayout.ZEN, R.drawable.ic_ph_circle_dashed, () -> setZen(!zen));
        button.iconHalf = DrawLayout.ZEN_ICON / 2;
        // Menu's icon size and no background, as Menu (the owner, 2026-10-22: "it can be the same size circle as the menu on top").
        button.setBackground(null);
        return button;
    }
    /** The button's icon and words for the state, and its place. */
    private void showZen() {
        // The dashed circle while off, the circle while on (the owner, 2026-10-29: "the icon for zen mode activated is a full
        // circle, but it should be what is on the toolbar . the toolbar icon should be a dotted circle icon initially";
        // circle and filled circle from 2026-10-24, dashed and circle before).
        zenButton.iconOnly(zen ? R.drawable.ic_ph_circle : R.drawable.ic_ph_circle_dashed);
        String said = zen ? DrawLayout.ZEN_LEAVE : DrawLayout.ZEN;
        zenButton.setTag(said); zenButton.setContentDescription(said);
        placeZen();
    }
    /** Puts the button in the foot corner under the column as it is laid out now ({@link DrawLayout#zenCell}), in Zen Mode too. */
    private void placeZen() {
        if (inkWashBar == null) return;
        View stage = (View) zenButton.getParent();
        DrawLayout at = inkWashBar.layout();
        int width = stage.getWidth() > 0 ? stage.getWidth() : root.getWidth() > 0 ? root.getWidth() : dp(at.width);
        int height = stage.getHeight() > 0 ? stage.getHeight() : root.getHeight() > 0 ? root.getHeight() : dp(at.height);
        // Menu's width (its tile: half the column) and its icon square with the column, its icon centred.
        int cellWidth = dp(at.half()), cellHeight = dp(at.zenHeight());
        int[] cell = DrawLayout.zenCell(width, height, dp(at.side), cellWidth, cellHeight, toolboxRight);
        FrameLayout.LayoutParams place = new FrameLayout.LayoutParams(cellWidth, cellHeight, Gravity.TOP | Gravity.START);
        place.setMargins(cell[0], cell[1], 0, 0);
        zenButton.setLayoutParams(place);
    }
    /** Turns Zen Mode on or off: the screen laid out again, the stroke in progress finished first. */
    void setZen(boolean on) {
        if (zen == on || inkWashBar == null) return;
        zen = on;
        inkWashBar.closePanel(); inkWashBar.hideLabel();
        applyToolboxSide();
        // The bars come or go: one flash clears what they leave (the owner, 2026-10-26).
        if (pad != null) pad.flashSoon(on ? "Zen Mode on" : "Zen Mode off");
    }
    /** Zen Mode's button drawn again through Android (after a full e-ink refresh of the page, which may have drawn over it). */
    void invalidateZen() { if (zenButton != null) zenButton.invalidate(); }
    /** Keeps menu/undo/redo in the outer corner and page controls at the other end. */
    private void arrangeHeader(boolean right) {
        Ui.detach(menuControls);
        boolean menuAtEnd = !landscape && right;
        // Keep Undo/Redo beside the outer-corner menu for either drawing hand.
        if ((menuControls.indexOfChild(menuButton) == 0) == menuAtEnd) {
            ArrayList<View> controls = new ArrayList<>();
            for (int i = menuControls.getChildCount()-1; i >= 0; i--) controls.add(menuControls.getChildAt(i));
            menuControls.removeAllViews();
            for (View control : controls) menuControls.addView(control);
        }
        Ui.detach(headerControls); Ui.detach(pagesButton);
        LinearLayout menuSide = menuAtEnd ? rightHeader : leftHeader;
        LinearLayout pageSide = menuAtEnd ? leftHeader : rightHeader;
        menuSide.addView(menuControls, new LinearLayout.LayoutParams(-2, dp(48)));
        // A narrower header replaces the page row with a Pages button.
        if (compactLayout()) pageSide.addView(pagesButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        else pageSide.addView(headerControls);
    }
    /** Turns the app to a quarter turn from the tablet's natural portrait. */
    void requestQuarter(int quarter) {
        if (busy()) return;
        pad.suspend();
        dismiss(toolPicker);
        toolbar.presetDrag.reset(); rotationPrompt.hide();
        inkWashBar.closePanel(); pad.words.close();
        appRotation = (4-quarter)%4;
        landscape = appRotation == Surface.ROTATION_90 || appRotation == Surface.ROTATION_270;
        orientationFrame.setTurn(appTurn());
        applyToolboxSide();
    }
    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        rotationPrompt.hide();
        dismiss(toolPicker); inkWashBar.closePanel();
        applyToolboxSide();
    }
    boolean canSimulate() { return device.simulates != null; }
    /** Previewing a smaller tablet's panel and controls (Settings' simulation mode). */
    boolean simulating() { return canSimulate() && prefs.simulateSmallerPanel(); }
    /**
     * The tablet the controls are laid out for: the simulated one while simulating. Input
     * and display quirks always come from {@link #device}, the hardware actually in use.
     */
    Device layoutDevice() { return simulating() ? device.simulates : device; }
    boolean compactLayout() { return layoutDevice().compactControls; }
    void setSimulating(boolean enabled) {
        if (busy()) return;
        enabled = enabled && canSimulate();
        dismissPanels(); hideGradientHint();
        pad.suspend(); pad.viewport.reset();
        prefs.setSimulateSmallerPanel(enabled);
        previewFrame.setSimulated(enabled ? device.simulates : null);
        applyToolboxSide();
        saveToolState(); recovery();
    }

    /**
     * Fast e-ink is a guess on firmware the device checks have not run on with this tablet.
     * Say so once per build, so a misbehaving screen has an obvious way out.
     */
    private void noticeUntestedFirmware() {
        if (!Device.supernote() || device.firmwareTested() || prefs.androidDrawing()
                || Device.firmware().equals(prefs.untestedFirmwareNoticed())) return;
        prefs.setUntestedFirmwareNoticed(Device.firmware());
        message(AppName.NAME + " hasn't been tested on this firmware yet.");
    }
    /** Switches between fast e-ink and Android drawing (Settings' Screen drawing). */
    void setAndroidDrawing(boolean enabled) {
        pad.suspend();
        prefs.setAndroidDrawing(enabled);
        DirectEink.useAndroidDrawing(enabled);
        inkWashBar.partialGrey();
        root.invalidate(); pad.connectDisplay();
    }

    void dispatchCancelledTouch(MotionEvent event) { super.dispatchTouchEvent(event); }

    /** The pen hovering anywhere over the window, the bar too, or leaving it: see {@link DrawingPad#penHover}. */
    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (pad != null) pad.penHover(event);
        return super.dispatchGenericMotionEvent(event);
    }

    final EdgeHold edgeHold = new EdgeHold();
    void edgeHoldAct(EdgeHold.Act act) { if (inkWashBar != null) inkWashBar.edgeTrash(act); }
    /** The edge strip's keys ({@link EdgeHold}): ours alone, so nothing else acts on them; a hold starts on the drawing screen only. */
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code != EdgeHold.KEY_ONE && code != EdgeHold.KEY_TWO) return super.dispatchKeyEvent(event);
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        if (down && (busy() || popupOpen() || notebookShown())) return true;
        edgeHoldAct(edgeHold.key(code, down, event.getRepeatCount() > 0, pad != null && pad.stroking()));
        return true;
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        Ui.notePress(event);
        boolean down = event.getActionMasked() == MotionEvent.ACTION_DOWN;
        if (down && rotateButton != null && rotateButton.getVisibility() == View.VISIBLE && Ui.outside(rotateButton, event))
            rotationPrompt.dismiss();
        if (dismissingTouch) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL)
                dismissingTouch = false;
            return true;
        }
        // A finger pulled down from the screen's top edge opens the Supernote's tray: the touch is the system's, never the
        // page bar's (a riffle would turn the page) or anything else's (the owner, 2026-10-24: "swiping down for the top
        // menu tray in supernote often changes the page"). A tap there, or a riffle along the bar, is the app's.
        if (down) edgeX = event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER && event.getRawY() < dp(StripGesture.SYSTEM_EDGE_DP) ? event.getRawX() : Float.NaN;
        if (down) edgeY = event.getRawY();
        else if (!Float.isNaN(edgeX)) {
            int pull = event.getActionMasked() != MotionEvent.ACTION_MOVE ? -1
                    : StripGesture.edgePull(event.getRawX() - edgeX, event.getRawY() - edgeY, dp(StripGesture.MOVE_DP));
            if (pull != 0) edgeX = Float.NaN;
            if (pull > 0) {
                MotionEvent cancel = MotionEvent.obtain(event); cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel); cancel.recycle();
                dismissingTouch = true; return true;
            }
        }
        // A touch outside the page row or palette editor only closes it, or is a resting hand to ignore.
        if (down && pagePanel != null && Ui.outside(pagePanel, event)) {
            if (Ui.closesPanel(pagePanelByFinger, event)) closePagePanel();
            dismissingTouch = true; return true;
        }
        // The pen landing anywhere in the window closes the tool panel; drawing or the tapped control goes ahead.
        // With a palm or finger already resting, the pen arrives as a further pointer.
        int action = event.getActionMasked(), landing = event.getActionIndex();
        if (down && inkWashBar != null) inkWashBar.touchDown();
        if ((down || action == MotionEvent.ACTION_POINTER_DOWN) && inkWashBar != null
                && event.getToolType(landing) != MotionEvent.TOOL_TYPE_FINGER) inkWashBar.penDown(event, landing);
        // Android treats ORIENTATION as radians when it transforms a View's events.
        // Retain the firmware's original signed-degree tilt axes before that happens.
        MotionEvent previous = physicalPenEvent; physicalPenEvent = event;
        try { return super.dispatchTouchEvent(event); }
        finally { physicalPenEvent = previous; }
    }

    // Panels

    private static void dismiss(PopupWindow popup) { if (popup != null) popup.dismiss(); }
    /** Closes every panel and popup and leaves the eyedropper. */
    void dismissPanels() {
        closePagePanel(); setPickingShade(false);
        dismiss(filePopup); dismiss(layersPopup); dismiss(toolPicker);
        if (inkWashBar != null) { inkWashBar.closePanel(); inkWashBar.hideLabel(); }
        if (pad != null) pad.words.close();
    }
    /** Whether a menu, list or panel popup, or the bar's label, is open over the window. */
    boolean popupOpen() {
        return filePopup != null || layersPopup != null || toolPicker != null
                || (inkWashBar != null && (inkWashBar.panelOpen() || inkWashBar.labelShown()));
    }
    /** Whether a panel or prompt covers the canvas, so direct presentation must pause. */
    boolean canvasCovered() {
        return rotateButton.getVisibility() == View.VISIBLE || pagePanel != null || pageOverview != null || notebookShown();
    }
    /** Shows a popup beside a toolbar control, fitted inside the canvas area. */
    void showBesideTool(PopupWindow popup, View content, View anchor, int preferredWidthDp) {
        RectF area = Popups.bounds(root, pad); area.inset(dp(8), dp(8));
        int width = Math.min(dp(preferredWidthDp), Math.round(area.width()));
        int height = Math.min(Popups.measuredHeight(content, width, Math.round(area.height())), Math.round(area.height()));
        Popups.show(popup, root, Popups.besideAnchor(Popups.bounds(root, anchor), area, width, height, landscape, dp(8)));
    }
    /** Opens settings for the selected tool beside its toolbar button. */
    PopupWindow showToolSettings() {
        closePagePanel(); dismiss(toolPicker);
        View anchor = toolbar.selectionButtons.get(toolbar.selectedKey());
        ToolSettingsPanel panel = new ToolSettingsPanel(this, anchor == null ? toolbar.toolRail : anchor);
        panel.show(); return panel.popup;
    }
    void showLayers(ToolButton anchor) {
        closePagePanel();
        if (busy()) return;
        if (layersPopup != null) { layersPopup.dismiss(); return; }
        pad.finishStroke(); pad.dryWet();
        new LayersPanel(this, anchor);
    }

    /**
     * The guides shown at {@code step} ({@link NotebookOptions#GUIDE_STEPS}), from the Template screen or Show Kinds'
     * popup's Template eye ({@link DrawLayout#TEMPLATE}), one state with the Template screen's chips; said in a note.
     */
    void showGuides(int step) {
        if (book == null) return;
        NotebookOptions next = book.options().withGuidesShown(step);
        setNotebookOptions(next);
        if (book.options().equals(next)) message(NotebookOptions.guidesNote(step));
    }
    /**
     * The menu (the owner, 2026-10-24: "tidy up the order of it, it's chaotic"): two groups, most used first, a grey-400
     * line between them. The notebook: Settings, Open Notebook, Import (a picture onto the page, or a PDF as a new notebook
     * or one of its pages onto the page; Import PDF and the page's group, Import Image, until 2026-10-29). No Play (a row
     * here from 2026-10-28; the column's confetti button again since 2026-10-30, {@link #playMenu}). The app:
     * Finger (what one finger does; the menu's row alone since the column's button was cut), Left-Handed. Gone at the
     * refocus: Template (Template Visibility is the column's and the Template screen's), Guides in PDF (the guides always
     * print), Use This Page on the Cover (Settings' More). Gone 2026-10-28: Export Now, Rename and Delete Notebook (Notebook
     * Settings' foot), Show Kinds (the column's), the Daily Prompt and Replay Page (Play's).
     */
    void fileMenu(View anchor) {
        List<String> names = new ArrayList<>();
        List<Integer> icons = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        // The notebook.
        names.add(DrawLayout.NOTEBOOK_SETTINGS); icons.add(R.drawable.ic_ph_list); actions.add(this::showSettings);
        names.add(NotebookLayout.OPEN_NOTEBOOK); icons.add(R.drawable.ic_ph_folder); actions.add(() -> { if (leaveDrawing()) leaveNotebook(); });
        names.add(PictureImport.IMPORT); icons.add(R.drawable.ic_ph_file); actions.add(this::importFile);
        // The app.
        divider(names, icons, actions);
        // Finger, what one finger does now: its choices beside the menu.
        names.add(inkWashBar.fingerRow()); icons.add(R.drawable.ic_finger); actions.add(() -> fingerMenu(anchor));
        // The drawing hand (MonoPaint's setting), the row naming the other hand: the column on the right, or back.
        names.add(toolboxRight ? DrawLayout.RIGHT_HANDED : DrawLayout.LEFT_HANDED); icons.add(toolboxRight ? R.drawable.ic_ph_hand_pointing_mirrored : R.drawable.ic_ph_hand_pointing); actions.add(() -> { prefs.setToolboxRight(!prefs.toolboxRight()); applyToolboxSide(); });
        Drawable[] icon = new Drawable[icons.size()];
        for (int i = 0; i < icon.length; i++) icon[i] = icons.get(i) == 0 ? null : getDrawable(icons.get(i));
        menu(anchor, "file menu", names.toArray(new String[0]), icon, actions.toArray(new Runnable[0]), prefs.largeSettingsText() ? 256 : 240, new boolean[names.size()], null);
    }
    /** Kind {@code choice} ({@link EraseKind#LABELS}) shown at {@code step} on the page drawn on, a change to save: it shows again once; said in a note ("Pen half"). */
    void showKind(int choice, int step) {
        if (busy() || pad.stroking() || pad.document == null) return;
        if (pad.document.setKindsShown(EraseKind.withStep(pad.document.kindsShown(), choice, step))) { pad.showSteps(layerSteps(pad.document), false); recovery(); }
        message(EraseKind.stepNote(choice, step));
    }
    /** A grey-400 line between a menu's groups: a null name ({@link #menu}). */
    private static void divider(List<String> names, List<Integer> icons, List<Runnable> actions) { names.add(null); icons.add(0); actions.add(null); }
    /**
     * Play's menu (the refocus, 2026-10-24: the toys in one place, "a paper notebook that feels like real media, useful
     * first, then a little mischief"; {@link DrawLayout#PLAY}), from the menu's Play row (2026-10-28): Magnetic Poetry (the
     * magnets' bin), Mirrors (their list), each marked while on; then Replay Page (tucked low: the owner, 2026-10-24) on a
     * page it can replay. Beside the column's Play (2026-10-30; the menu's Play row from 2026-10-28). No Shapes (the
     * column's, beside Play, 2026-10-30), Self-Portrait Prompt (Notebook Settings', 2026-10-30), Stamp (the Tools page's),
     * Surprise Me (Notebook Settings' Tools) or Fling (the owner, 2026-10-28); no Diary Page and no ready brushes (the owner, 2026-10-27).
     */
    void playMenu(View anchor) {
        List<String> names = new ArrayList<>();
        List<Integer> icons = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        List<Boolean> on = new ArrayList<>();
        names.add(DrawLayout.POETRY); icons.add(R.drawable.ic_ph_magnet); actions.add(() -> pad.words.toggle()); on.add(pad.words.open());
        names.add(DrawLayout.MIRRORS); icons.add(R.drawable.ic_ph_flip_horizontal); actions.add(() -> mirrors.mirrors(anchor)); on.add(mirrors.on());
        if (replay.available()) { divider(names, icons, actions); on.add(false); names.add(PageReplay.REPLAY_PAGE); icons.add(R.drawable.ic_ph_play); actions.add(() -> replay.choose(anchor)); on.add(false); }
        Drawable[] icon = new Drawable[icons.size()];
        boolean[] marked = new boolean[on.size()];
        for (int i = 0; i < icon.length; i++) { icon[i] = icons.get(i) == 0 ? null : getDrawable(icons.get(i)); marked[i] = on.get(i); }
        menu(anchor, "play menu", names.toArray(new String[0]), icon, actions.toArray(new Runnable[0]), prefs.largeSettingsText() ? 256 : 240, marked, null);
    }
    /**
     * The tool picker's pick: {@code brush} put in slot {@code slot}, which is selected, an Undo step; the
     * brush it replaces kept in Recent when it would be lost ({@link ToolSlots#keep}).
     */
    private void pickBrush(int slot, Brush brush) {
        if (toolbarFixed()) return;
        Brush was = inkWash.slots.brush(slot);
        if (was != null && !was.sameAs(brush)) inkWash.slots.keep(was);
        boolean used = inkWash.slots.use(slot, brush);
        inkWashBar.selectTool(slot);
        if (!used) return;
        recovery();
        inkWashBar.brushChanged(slot, brush.name());
    }
    /** Whether the slots can't change now (Each Page's toolbar): said, never silently. */
    boolean toolbarFixed() {
        String fixed = NotebookRules.toolsFixed(rules());
        if (fixed != null) note(fixed);
        return fixed != null;
    }
    /**
     * The Self-Portrait Prompt (a new page's, {@link #addPage(BookEdit)}): card {@code index} of {@code cards}, its length in the heading. Start (timed cards) sets the sand timer to its
     * length and closes the card; a Diary Page card first makes the page drawn on's own template Diary Page (guides are
     * paper, so no ink is touched and there is nothing to undo). Deal Another shows a different card.
     */
    void dailyPrompt(List<DailyPrompt.Card> cards, int index) {
        DailyPrompt.Card card = cards.get(index);
        // The page drawn on is given the card: it shows again when the page is next opened (once a session).
        String line = DailyPrompt.line(card);
        if (book != null && book.index() >= 0 && pad.document == book.current() && line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= DailyPrompt.MOST_BYTES
                && pad.document.setPrompt(line)) { promptsShown.add(drawingName + "\n" + book.index()); recovery(); }
        promptCard(card, DailyPrompt.heading(card), () -> dailyPrompt(cards, DailyPrompt.another(index, cards.size(), new java.util.Random())));
    }
    /** A card's window: its heading and words, Deal Another when {@code deal} is given, Close, and Start for a timed card. */
    private void promptCard(DailyPrompt.Card card, String heading, Runnable deal) {
        int seconds = card.seconds;
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(heading).setMessage(card.text).setNegativeButton("Close", null);
        if (deal != null) b.setNeutralButton(DailyPrompt.DEAL, (d, w) -> deal.run());
        if (seconds > 0) b.setPositiveButton(DailyPrompt.START, null);
        AlertDialog dialog = showDialog(b);
        if (seconds == 0) return;
        // Start hands the time to the sand timer and closes the card, so the page is free to draw on: its countdown in the
        // column, "Time's up" at zero, never a lock (the owner, 2026-10-27: "they shouldn't lock the page so permanently").
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            // The page drawn on alone takes the card's template, as a hold on it in the strip would (never the notebook's).
            if (card.diary && book != null) setPageTemplate(book.index(), DailyPrompt.template(card));
            setSandTimer(seconds);
            dialog.dismiss();
        });
    }
    /**
     * What one finger on the page does ({@link FingerTool}): Nothing, Eraser, Smudge or a slot, kept; the one chosen
     * marked by the bar's black bar at its right. Each shows what it is (the owner, 2026-10-19: "include brush previews
     * for brushes"): a slot its tray stroke and its brush's name (the slots have no numbers), Eraser and Smudge their
     * icons, Nothing the hand struck through ({@link InkWashBar#fingerIcon}). Beside the menu, from its Finger row (the owner,
     * 2026-10-27: back in the menu; the column's button from 2026-10-26: "finger switch to toolbar. single tap to change").
     */
    void fingerMenu(View anchor) {
        FingerTool.Choice[] choices = FingerTool.Choice.values();
        String[] names = new String[choices.length];
        Drawable[] icons = new Drawable[choices.length];
        Runnable[] actions = new Runnable[choices.length];
        for (int i = 0; i < choices.length; i++) {
            FingerTool.Choice choice = choices[i];
            names[i] = inkWashBar.fingerName(choice);
            icons[i] = inkWashBar.fingerIcon(choice, dp(64), dp(28));
            actions[i] = () -> inkWash.finger(choice);
        }
        menu(anchor, "finger menu", names, icons, actions, prefs.largeSettingsText() ? 296 : 280, inkWash.finger().ordinal());
    }
    /**
     * A list of labeled rows {@code widthDp} wide dropping down from {@code anchor}, Inkwash's one menu ({@link
     * Theme#menu}, {@link Theme#menuRow}); tapping one closes the list and runs its action. Closing it cleans what it
     * covered on the panel (see {@link DrawingPad#uncovered}).
     */
    void menu(View anchor, String name, String[] names, int[] icons, Runnable[] actions, int widthDp) {
        Drawable[] drawn = new Drawable[icons.length];
        for (int i = 0; i < icons.length; i++) drawn[i] = icons[i] == 0 ? null : getDrawable(icons[i]);
        menu(anchor, name, names, drawn, actions, widthDp, -1);
    }
    /**
     * As above, each row's icon {@code icons[i]} (null: none; one without bounds at its own size), and row {@code
     * chosen} marked as the one chosen (-1: none) by a black bar down its right side, as the bar marks the tool in use.
     */
    void menu(View anchor, String name, String[] names, Drawable[] icons, Runnable[] actions, int widthDp, int chosen) {
        boolean[] marked = new boolean[names.length];
        if (chosen >= 0 && chosen < names.length) marked[chosen] = true;
        menu(anchor, name, names, icons, actions, widthDp, marked, null);
    }
    /**
     * As above, every row {@code marked} marked by the bar's right-edge bar (the mirrors on, {@link MirrorsMenu}), and
     * {@code note} (null: none) under the rows in captions, which a tap passes by; a null name is a grey-400 line between groups.
     */
    void menu(View anchor, String name, String[] names, Drawable[] icons, Runnable[] actions, int widthDp, boolean[] marked, String note) {
        if (preparing) { built(anchor, name, names, icons, widthDp, marked, note); return; }
        closePagePanel();
        dismiss(filePopup); dismiss(layersPopup); inkWashBar.closePanel();
        Menu menu = built(anchor, name, names, icons, widthDp, marked, note);
        menu.actions = actions;
        show(menu);
    }
    /** {@code menu} on the panel, as the one popup open over the page. */
    private void show(Menu menu) {
        // Inkwash: one grey pass as it opens, and only what it covered cleaned as it closes (2026-10-25: the page's whole
        // refresh and the bars drawn again after every menu until then).
        InkWashBar.partialGrey(menu.scroll);
        filePopup = menu.popup;
        // Inkwash: on the panel at once, drawn before (the firmware shows a new window about 350 ms after Android draws it).
        if (menu.pixels != null) selectionFeedback.presentAhead(menu.pixels, menu.covered);
        Popups.show(menu.popup, root, menu.bounds);
    }
    /**
     * Inkwash: a menu as built, placed and drawn: the file menu's is kept for its next opening while nothing it shows or
     * where it goes has changed, and made ahead when the drawing screen has its focus ({@link #prepareFileMenu}), so a tap
     * on Menu shows it at once (its rows built, measured and drawn took 40 to 70 ms of the tap, 2026-10-25).
     */
    private static final class Menu {
        String key; PopupWindow popup; View scroll; RectF bounds, covered; Bitmap pixels; Runnable[] actions;
    }
    private Menu fileMenuBuilt;
    /** While true, {@link #menu} builds the menu and keeps it rather than show it ({@link #prepareFileMenu}). */
    private boolean preparing;
    /** The file menu built ahead, at the drawing screen's next idle moment with its focus. */
    void prepareFileMenu() {
        if (destroyed || pad == null || inkWashBar == null || inkWashBar.menu.getWidth() == 0 || !hasWindowFocus() || busy()) return;
        preparing = true;
        try { fileMenu(inkWashBar.menu); } finally { preparing = false; }
    }
    private Menu built(View anchor, String name, String[] names, Drawable[] icons, int widthDp, boolean[] marked, String note) {
        RectF anchorBounds = Popups.bounds(root, anchor);
        String key = name + "|" + java.util.Arrays.toString(names) + java.util.Arrays.toString(marked) + note + widthDp + anchorBounds
                + root.getWidth() + "x" + root.getHeight() + toolboxRight + appTurn() + Ui.fingerPress() + prefs.largeSettingsText();
        // The file menu's icons are fixed by its rows' names, so its key holds them; other menus (Play's strokes) are built each time.
        boolean keep = "file menu".equals(name);
        if (keep && fileMenuBuilt != null && fileMenuBuilt.key.equals(key)) return fileMenuBuilt;
        Menu built = new Menu();
        built.key = key;
        LinearLayout rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this); scroll.addView(rows);
        scroll.setBackground(Theme.menu(this));
        int edge = dp(Theme.FINE);
        scroll.setPadding(edge, edge, edge, edge);
        PopupWindow menu = Popups.create(scroll, appTurn());
        for (int i = 0; i < names.length; i++) {
            // A null name: a grey-400 line between groups (the refocus, 2026-10-24), not a row.
            if (names[i] == null) {
                View line = new View(this); line.setBackgroundColor(Theme.GREY_400);
                LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(-1, dp(Theme.FINE));
                place.setMargins(dp(16), dp(4), dp(16), dp(4));
                rows.addView(line, place);
                continue;
            }
            int index = i;
            TextView row = Theme.menuRow(new TextView(this), i < marked.length && marked[i]); row.setText(names[i]);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(12), dp(16), dp(12)); row.setMinHeight(dp(DrawLayout.MENU_ROW));
            Drawable icon = icons[i];
            if (icon != null && icon.getBounds().isEmpty()) icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
            // Left, not start: a relative drawable is placed only once the row is attached, so the menu's height, measured
            // before, left the icon out, and a label that wraps beside it (Use This Page on the Cover) was cut off at the foot.
            row.setCompoundDrawables(icon, null, null, null); row.setCompoundDrawablePadding(dp(12));
            row.setFocusable(true);
            row.setOnClickListener(v -> {
                menu.dismiss();
                if (busy()) return;
                pad.finishStroke(); built.actions[index].run();
            });
            rows.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        if (note != null) {
            TextView caption = Theme.style(new TextView(this), TypeStyle.MENU);
            caption.setText(note);
            caption.setGravity(Gravity.CENTER_VERTICAL); caption.setPadding(dp(16), dp(8), dp(16), dp(8));
            caption.setMinHeight(dp(DrawLayout.MENU_NOTE));
            rows.addView(caption, new LinearLayout.LayoutParams(-1, -2));
        }
        placed(built, menu, scroll, anchorBounds, widthDp);
        if (keep) {
            if (fileMenuBuilt != null && fileMenuBuilt.pixels != null && !fileMenuBuilt.popup.isShowing()) fileMenuBuilt.pixels.recycle();
            fileMenuBuilt = built;
        }
        return built;
    }
    /**
     * {@code built} as {@code menu}, its content {@code scroll}, {@code widthDp} wide: placed beside its anchor, toward
     * the page, in the user's orientation (Inkwash: the side toolbar), level with it, moved up where it would run off the
     * foot (Play, low in the column), so it never scrolls; what it covers cleaned as it closes; drawn ahead.
     */
    private void placed(Menu built, PopupWindow menu, View scroll, RectF anchorBounds, int widthDp) {
        int width = Math.min(dp(widthDp), root.getWidth());
        int height = Math.min(Popups.measuredHeight(scroll, width, 0), Math.max(1, root.getHeight()));
        float left = Popups.clamp(toolboxRight ? anchorBounds.left-width : anchorBounds.right, 0, root.getWidth()-width);
        float top = Popups.clamp(anchorBounds.top, 0, root.getHeight()-height);
        RectF bounds = Popups.rect(left, top, width, height), covered = new RectF(bounds);
        PanelCoordinates.fromView(root).mapRect(covered);
        menu.setOnDismissListener(() -> { if (filePopup == menu) filePopup = null; pad.cleanArea(covered); });
        built.popup = menu; built.scroll = scroll; built.bounds = bounds; built.covered = covered;
        if (appTurn() == 0) {
            // As Android will lay it out in the popup, to put on the panel before the window shows (SelectionFeedback.presentAhead).
            scroll.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            scroll.layout(0, 0, width, height);
            built.pixels = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            built.pixels.eraseColor(android.graphics.Color.WHITE);
            scroll.draw(new android.graphics.Canvas(built.pixels));
        }
    }
    /**
     * The tool picker on slot {@code slot}, beside {@code anchor}, its row ({@link DrawLayout#PICKER}; the owner,
     * 2026-10-25: "long press on any slot to open. single tap selects the tool"): the Basic tools the owner drew, each its
     * drawing over its name, the one the slot holds marked by the side bar; a tap puts it in the slot
     * ({@link #pickBrush}). Under them Brush Lab on its brush
     * ("brush studio could be opened directly from the tiny popup") and Advanced, the Tools page on it ("an advanced
     * button which pulls up the library we have now"), and over them, for a Stamp, its menu ({@link DrawLayout#STAMP_MENU});
     * at its top Reset to Default when the slot's brush is a Basic tool edited ({@link DrawLayout#edited}); at its foot the switch Tools First ({@link DrawLayout#TOOLS_FIRST}), with which on
     * the Tools page opens on the slot instead of the picker.
     */
    void toolPicker(int slot, View anchor) {
        if (prefs.toolsFirst()) { openTools(slot); return; }
        closePagePanel();
        dismiss(filePopup); dismiss(layersPopup); inkWashBar.closePanel();
        pad.finishStroke();
        Brush held = inkWash.brush(slot);
        int columns = DrawLayout.pickerColumns(Math.round(root.getHeight() / getResources().getDisplayMetrics().density));
        int cellWidth = DrawLayout.pickerCellWidth(), room = dp(DrawLayout.PICKER_PAD);
        LinearLayout rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this); scroll.addView(rows);
        scroll.setBackground(Theme.menu(this));
        int edge = dp(Theme.FINE);
        scroll.setPadding(edge, edge, edge, edge);
        PopupWindow popup = Popups.create(scroll, appTurn());
        Menu built = new Menu();
        ToolPresets.Preset original = DrawLayout.edited(held);
        if (original != null) {
            // Reset to Default, at the top (the owner, 2026-10-28): the Basic tool back in the slot, an Undo step; the slot keeps its own size and grey (ToolSwatches).
            TextView reset = Theme.menuRow(new TextView(this), false);
            reset.setText(DrawLayout.RESET); reset.setGravity(Gravity.CENTER);
            reset.setMinHeight(dp(DrawLayout.MENU_ROW));
            reset.setOnClickListener(v -> { popup.dismiss(); if (!busy()) pickBrush(slot, original.brush()); });
            rows.addView(reset, new LinearLayout.LayoutParams(-1, -2));
        }
        LinearLayout line = null;
        int shown = 0;
        for (String[] tool : DrawLayout.PICKER) {
            ToolPresets.Preset preset = ToolKits.preset(tool[0]);
            // The drawing by its file's name, so the table stays one list the owner can change (no resource shrinking).
            int drawing = tool[1] == null ? 0 : getResources().getIdentifier(tool[1], "drawable", getPackageName());
            if (preset == null || tool[1] != null && drawing == 0) continue;
            if (shown++ % columns == 0) { line = LabUi.row(this); rows.addView(line, new LinearLayout.LayoutParams(-1, -2)); }
            LinearLayout cell = LabUi.column(this);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(room, room, room + dp(DrawLayout.PICKER_MARK), room);
            View picture;
            if (drawing != 0) {
                android.widget.ImageView drawn = new android.widget.ImageView(this);
                drawn.setImageResource(drawing);
                drawn.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                picture = drawn;
            } else {
                // Not drawn yet: its stroke across the cell, as the Tools page shows it (ToolCard), drawn in the background.
                if (toolPictures == null) toolPictures = new SampleQueue();
                ToolCard stroke = new ToolCard(this, toolPictures);
                stroke.setArt(cellWidth - 2 * DrawLayout.PICKER_PAD - DrawLayout.PICKER_MARK, DrawLayout.PICKER_TIP);
                Brush brush = preset.brush();
                stroke.show(brush.identity() + " tray", brush, LabLibrary.traySampler(brush, null), "");
                picture = stroke;
            }
            cell.addView(picture, new LinearLayout.LayoutParams(-1, dp(DrawLayout.PICKER_TIP)));
            TextView name = Theme.style(new TextView(this), TypeStyle.CAPTION);
            name.setText(preset.name); name.setSingleLine(true); name.setGravity(Gravity.CENTER_HORIZONTAL);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams namePlace = new LinearLayout.LayoutParams(-1, -2);
            namePlace.topMargin = room;
            cell.addView(name, namePlace);
            boolean on = !(pad.shapesOn && inkWash.selected == slot) && held != null && held.sameAs(preset.brush());
            if (on) { cell.setBackground(Theme.marked(this)); name.setTypeface(Theme.face(700)); }
            cell.setContentDescription(preset.name);
            cell.setOnClickListener(v -> { popup.dismiss(); if (!busy()) pickBrush(slot, preset.brush()); });
            line.addView(cell, new LinearLayout.LayoutParams(dp(cellWidth), -2));
        }
        if (held != null && held.parts.stamps()) {
            // A Stamp's own menu beside the slot (InkWashBar.stampMenu): Cut Stamp, Turns, Spacing, Ink Fade.
            TextView stamp = Theme.menuRow(new TextView(this), false);
            stamp.setText(DrawLayout.STAMP_MENU); stamp.setGravity(Gravity.CENTER);
            stamp.setMinHeight(dp(DrawLayout.MENU_ROW));
            stamp.setOnClickListener(v -> { popup.dismiss(); if (!busy()) inkWashBar.stampMenu(slot); });
            rows.addView(stamp, new LinearLayout.LayoutParams(-1, -2));
        }
        // The last row: Brush Lab, Advanced.
        String[] words = {DrawLayout.BRUSH_LAB, DrawLayout.ADVANCED};
        Runnable[] acts = {() -> openBrushLab(slot), () -> openTools(slot)};
        LinearLayout last = LabUi.row(this);
        for (int i = 0; i < words.length; i++) {
            Runnable act = acts[i];
            TextView button = Theme.menuRow(new TextView(this), false);
            button.setText(words[i]); button.setGravity(Gravity.CENTER);
            button.setMinHeight(dp(DrawLayout.MENU_ROW));
            button.setOnClickListener(v -> { popup.dismiss(); if (!busy()) act.run(); });
            last.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
        }
        rows.addView(last, new LinearLayout.LayoutParams(-1, -2));
        // Tools First, a switch: marked while on, as Shapes' Filled; on, the next long press opens the Tools page instead.
        TextView first = Theme.menuRow(new TextView(this), false);
        first.setText(DrawLayout.TOOLS_FIRST); first.setGravity(Gravity.CENTER);
        first.setMinHeight(dp(DrawLayout.MENU_ROW));
        first.setContentDescription(DrawLayout.TOOLS_FIRST + ", off");
        first.setOnClickListener(v -> {
            boolean on = !prefs.toolsFirst();
            prefs.setToolsFirst(on);
            Theme.menuRow(first, on);
            first.setContentDescription(DrawLayout.TOOLS_FIRST + (on ? ", on" : ", off"));
        });
        rows.addView(first, new LinearLayout.LayoutParams(-1, -2));
        placed(built, popup, scroll, Popups.bounds(root, anchor), columns * cellWidth + 2 * Math.round(Theme.FINE));
        show(built);
    }
    /**
     * Shapes' menu beside its button, {@code anchor} ({@link DrawLayout#SHAPES}), for slot {@code slot}: the
     * five shapes, then Filled, the shape in use and Filled marked; a tap takes Shapes up on the slot with that shape, or with Filled turned on or off.
     */
    void shapesMenu(int slot, View anchor) {
        ToolSettings.Shape[] kinds = ToolSettings.Shape.values();
        int filled = kinds.length;
        String[] names = new String[filled + 1];
        Drawable[] icons = new Drawable[names.length];
        Runnable[] actions = new Runnable[names.length];
        boolean[] marked = new boolean[names.length];
        boolean on = pad.shapesOn && inkWash.selected == slot;
        for (int i = 0; i < filled; i++) {
            ToolSettings.Shape kind = kinds[i];
            names[i] = kind.label; icons[i] = getDrawable(ToolIcons.shape(kind));
            actions[i] = () -> takeUpShapes(slot, anchor, pad.shapes.shape(kind));
            marked[i] = on && pad.shapes.shape == kind;
        }
        names[filled] = DrawLayout.FILLED; icons[filled] = getDrawable(R.drawable.ic_ph_circle_fill);
        actions[filled] = () -> takeUpShapes(slot, anchor, pad.shapes.filled(!pad.shapes.filled));
        marked[filled] = pad.shapes.filled;
        menu(anchor, "shapes menu", names, icons, actions, prefs.largeSettingsText() ? 216 : 200, marked, null);
    }
    /** Shapes taken up on slot {@code slot}, drawing {@code shapes}, said beside its {@code anchor}. */
    private void takeUpShapes(int slot, View anchor, ToolSettings shapes) {
        inkWashBar.selectTool(slot);
        pad.shapes = shapes; pad.shapesOn = true;
        inkWashBar.lassoChanged();
        inkWashBar.say(DrawLayout.shapeWords(shapes), InkWashBar.LABEL_MS, anchor);
    }
    // Document edits

    /** Applies a layer edit as one undoable change and presents it. */
    void layerChange(Runnable action) {
        if (busy()) return;
        if (pageLocked()) { note(NotebookRules.PAGE_LOCKED); return; }
        // A Drying wash still settling is laid where it is first, on the layers it was drawn on.
        pad.finishStroke(); pad.dryWet(); pad.settleDrying();
        try { action.run(); inkWash.slots.pageChanged(); pad.renderDirty(); pad.present(); recovery(); }
        catch (IllegalStateException | IllegalArgumentException error) { message(error.getMessage()); }
    }
    /**
     * One undoable layer change from the bar or the Stack panel, which dries wet paint first; whether it changed
     * anything. The page was drawn again whole: a clean refresh at the next pause, and its preview follows.
     */
    boolean layerEdit(java.util.function.BooleanSupplier action) {
        boolean[] done = {false};
        layerChange(() -> { if (done[0] = action.getAsBoolean()) inkWash.session.dry("layers changed"); });
        pad.refreshAfterLargeChange("layer change");
        if (done[0]) inkWashBar.pageChanged();
        return done[0];
    }
    /**
     * Undo, from the bar or a two-finger tap: takes back a slot's roll or pick made since the page last changed,
     * naming the brush it goes back to, or else undoes the page.
     */
    void undo() {
        // A floating piece (the Lasso) is dropped instead, putting back what it lifted.
        if (pad.cancelSelection()) return;
        // Scribulor: so is a Spiro's ring set up, the page untouched.
        if (pad.dropRing()) return;
        if (pageLocked()) { note(NotebookRules.PAGE_LOCKED); return; }
        if (undoOff()) return;
        // A layer deleted or merged down, nothing done since: back as it was, in one step.
        if (!layerUndos.isEmpty() && layerUndos.peek().ready(book)) { LayerEdits.Undo back = layerUndos.pop(); pageEdit("undo", () -> back.apply(book)); return; }
        int slot = inkWash.slots.undo();
        // While paint floats (Float), Undo takes back its strokes one by one.
        if (slot < 0) { history(() -> inkWash.session.undo(pad.document)); return; }
        // A tool kit's step puts back every slot it filled, in one Undo.
        for (int other : inkWash.slots.undone()) if (other != slot) inkWashBar.showBrush(other);
        inkWashBar.brushChanged(slot, inkWash.slots.brush(slot).name());
    }
    /** Redo, from the bar or a three-finger tap. */
    void redo() {
        if (pageLocked()) { note(NotebookRules.PAGE_LOCKED); return; }
        if (!undoOff()) history(pad.document::redo);
    }
    /** No Erasing is on here: Undo and Redo give the note and do nothing (the history is kept, unused while it is on). */
    private boolean undoOff() {
        if (!NotebookRules.undoOff(rules())) return false;
        note(NotebookRules.UNDO_OFF);
        return true;
    }
    interface HistoryStep { boolean apply(); }
    void history(HistoryStep step) {
        // Locked means locked: no Undo, no Redo, no magnet moved.
        if (pageLocked()) { note(NotebookRules.PAGE_LOCKED); return; }
        inkWashBar.closePanel();
        pad.finishStroke(); pad.dryWet();
        int width=pad.document.width, height=pad.document.height;
        if (step.apply()) {
            inkWash.slots.pageChanged();
            if(width!=pad.document.width || height!=pad.document.height) { pad.viewport.reset(); pad.canvasResized(); }
            else { pad.renderDirty(); pad.present(); pad.refreshAfterLargeChange("undo or redo"); }
            recovery(); inkWashBar.pageChanged();
        }
    }

    // Color and paint modes

    /** Changes the color bar's state through the fast display path. */
    void updateColorBar(Runnable change) { selectionFeedback.update(shadePicker, shadePicker.markerArea(), change); }
    boolean shadeMarkerVisible() { return !(((paint.eraseMode || paint.maskingFluid) && !pickingShade) || (pickingShade && !pickedShade)); }

    void selectShade(int value) {
        if (busy()) return;
        setPickingShade(false);
        if (!pad.hasGradient()) pad.finishStroke();
        setEraseMode(false); setMaskingFluid(false);
        // A brush pen holding no ink lays no color: choosing one goes back to the brush last in use.
        if (library.current().tool.water && !library.current().holdsInk()) toolbar.returnToBrush();
        if (paint.gray != value) updateColorBar(() -> paint.gray = value);
        loadInk(value);
        if (pad.hasGradient()) pad.previewGradient(value);
    }
    /**
     * Any shade chosen, the same one too, resets a watercolor brush's ink to it, after the brush
     * has laid the paint it is still showing; with the wet brush pen, it dips the pen in that ink.
     */
    void loadInk(int value) {
        pad.layQueuedPaint();
        paint.brushLoad.tint(value);
        if (holdsInk()) paint.brushInk.load(value);
    }
    /**
     * With the wet brush pen, the color bar's marker slides to the ink it holds now, after a stroke;
     * with opaque paint it stays, since the next stroke starts dipped in the selected ink again.
     */
    void showBrushInk() {
        if (!holdsInk() || !paint.transparentPaint || paint.eraseMode || paint.maskingFluid || pickingShade) return;
        int shade = 255 - Math.round(paint.brushInk.fullness() * 255), from = paint.gray;
        if (shade == from) return;
        // The marker still shows the old shade until it slides.
        paint.gray = shade; shadePicker.slideMarker(from);
    }
    /** Loads a brush with limited paint by as far as the pen rubbed in the color bar or palette. */
    void loadPaint(float travel) {
        ToolSettings current = library.current();
        if (busy()) return;
        if (!current.limitsPaint()) return;
        library.edit(current.loadedBy(travel)); saveToolState(); toolbar.refreshToolSelection();
    }
    /** The pen or key left the shade strip: commit or cancel what it previewed. */
    void endShadeGesture(boolean committed) {
        if (committed) pad.applyGradient(); else pad.cancelGradient();
    }
    private void toggleEraseMode() {
        if (busy() || !library.current().supportsEraseMode() || erasingOff()) return;
        // A pending gradient fades to transparent instead of waiting for a color.
        boolean gradient = pad.hasGradient();
        if (!gradient) pad.finishStroke();
        setPickingShade(false); pad.dryWet();
        setEraseMode(gradient || !paint.eraseMode);
        if (gradient) { pad.previewGradient(ToneDocument.ERASE); pad.applyGradient(); }
        saveToolState();
    }
    void setEraseMode(boolean value) {
        if (paint.eraseMode != value) updateColorBar(() -> paint.eraseMode = value);
        refreshEraseControl();
    }
    void refreshEraseControl() {
        if (eraseButton == null) return;
        boolean supported = library.current().supportsEraseMode();
        if (!supported && paint.eraseMode) { setEraseMode(false); return; }
        eraseButton.setEnabled(supported);
        eraseButton.setAlpha(supported ? 1f : .3f);
        eraseButton.setContentDescription(supported ? "Erase with current tool" : "This tool does not use a color");
        eraseButton.mark(paint.eraseMode && !pickingShade);
    }
    /** The wet brush pen holds the ink it is dipped in, or soaks up, between strokes; white cleans it. */
    private boolean holdsInk() { return library.current().holdsInk(); }
    void setPickingShade(boolean picking) {
        if (pickingShade == picking) return;
        if (picking) { hideGradientHint(); pickOriginalShade = paint.gray; pickedShade = false; }
        updateColorBar(() -> pickingShade = picking);
        refreshEraseControl();
        if (eyedropperButton != null) {
            eyedropperButton.mark(picking);
            eyedropperButton.setContentDescription(picking ? "Cancel picking color" : "Pick color from canvas");
        }
    }
    // Masking fluid

    /** Masking fluid is chosen like a color; the eraser then rubs it off. */
    private void toggleMaskingFluid() {
        if (busy()) return;
        pad.dryWet();
        // The brush pens lay no color: masking fluid goes on with the brush last in use.
        if (!paint.maskingFluid && library.current().tool.water) toolbar.returnToBrush();
        setMaskingFluid(!paint.maskingFluid); saveToolState();
    }
    void setMaskingFluid(boolean value) {
        if (paint.maskingFluid != value) updateColorBar(() -> paint.maskingFluid = value);
        if (maskButton == null) return;
        maskButton.mark(value);
        maskButton.setContentDescription(value ? "Masking fluid. Erase to rub it off; hold to peel it all off."
                : "Masking fluid. Hold to peel it all off.");
    }
    /** Opens the Peel button below the masking fluid; null when no panel opened. */
    View showPeel() {
        if (!beginPagePanel()) return null;
        LinearLayout column = new LinearLayout(this);
        View peel = pageMenuItem(column, "Peel off masking fluid", R.drawable.ic_peel, this::peelMask, pad.document.hasMask());
        showPagePanel(maskButton, column, dp(48), dp(48));
        return peel;
    }
    private void peelMask() { if (erasingOff()) return; layerChange(() -> pad.document.peelMask()); }

    void refreshPaintModes() {
        watercolorButton.mark(paint.watercolor);
        transparentButton.mark(paint.transparentPaint);
        opaqueButton.mark(!paint.transparentPaint);
    }
    /** Transparent paint from brushes, shapes and flat fills becomes watercolor, or stops being it. */
    private void setWatercolor(boolean value) {
        if (busy()) return;
        pad.finishStroke(); paint.watercolor = value;
        refreshPaintModes(); saveToolState();
    }
    /** While wet paint is moving, the droplet dries it; otherwise it toggles watercolor mode. */
    private void tapDroplet() {
        if (wetDroplet.moving()) blowDry();
        else setWatercolor(!paint.watercolor);
    }
    /**
     * The blow dryer: wet paint stops moving and stays as it is, and the droplet blows a little
     * air. The modes stay as they are, so on a wet canvas the next stroke wets the paint it reaches again.
     */
    void blowDry() {
        if (busy()) return;
        setPickingShade(false);
        pad.finishStroke(); pad.dryWet();
        wetDroplet.rest(); BlowDryer.blow(this, watercolorButton);
    }
    private void setTransparentPaint(boolean value) {
        if (busy()) return;
        pad.finishStroke(); paint.transparentPaint = value;
        refreshPaintModes(); saveToolState();
    }
    void toggleNavigationLock() {
        // Discard any fingers already down; unlocking requires a fresh gesture.
        pad.touchBlocked = true; pad.endNavigation();
        // Inkwash: the page bar's Zoom button shows it (MonoPaint's toolbar is hidden); the view stays as it is.
        inkWashBar.zoom.lockChanged(() -> navigationLocked = !navigationLocked);
        prefs.setNavigationLocked(navigationLocked);
    }
    /** Saves the current color, modes and tools. */
    void saveToolState() {
        // The Nomad's side refresh can reuse Android's last surface without an
        // app redraw. Commit the final marker once rapid color taps have settled.
        shadePicker.finishUpdate();
        try { prefs.savePaint(paint, library); }
        catch (java.io.IOException error) { message("Could not save tool settings: " + error.getMessage()); }
    }

    // Gradient hint

    /** Prompts for a gradient's second color if none is chosen soon. */
    void scheduleGradientHint() { hideGradientHint(); shadePicker.postDelayed(gradientHintTask, 3000); }
    private void showGradientHint() {
        if (pad == null || !pad.gradientWaiting || pickingShade || !resumed || !hasWindowFocus()) return;
        TextView hint = Theme.style(new TextView(this), TypeStyle.BODY); hint.setText("Select a second color");
        hint.setBackgroundColor(Theme.PAPER);
        hint.setPadding(dp(8), dp(4), dp(8), dp(4));
        hint.measure(View.MeasureSpec.makeMeasureSpec(Math.max(1, root.getWidth()-dp(16)), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        RectF anchor = Popups.bounds(root, shadePicker);
        int width = hint.getMeasuredWidth(), height = hint.getMeasuredHeight();
        float x = landscape ? (toolboxRight ? anchor.left-width-dp(4) : anchor.right+dp(4)) : anchor.centerX()-width/2f;
        int left = Math.round(Popups.clamp(x, 0, root.getWidth()-width));
        int top = Math.round(Popups.clamp(anchor.bottom+dp(4), 0, root.getHeight()-height));
        hint.layout(left, top, left+width, top+height);
        gradientHint = hint; root.getOverlay().add(hint);
    }
    void hideGradientHint() {
        if (shadePicker != null) shadePicker.removeCallbacks(gradientHintTask);
        if (gradientHint != null) { root.getOverlay().remove(gradientHint); gradientHint = null; }
    }

    // Dialogs

    void compactDialog(AlertDialog dialog) { compactDialog(dialog, 440); }
    void compactDialog(AlertDialog dialog, int width) {
        orientDialog(dialog, width);
        SettingsForm.style(dialog.getWindow().getDecorView(), prefs.largeSettingsText());
        for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
            Button b = dialog.getButton(which); if (b != null) { b.setAllCaps(false); b.setTypeface(Theme.face(700)); }
        }
        Theme.dialog(dialog);
    }
    AlertDialog showDialog(AlertDialog.Builder builder) {
        AlertDialog dialog = builder.create(); dialog.show(); compactDialog(dialog); return dialog;
    }
    void orientDialog(AlertDialog dialog) { orientDialog(dialog, 440); }
    /** Turns a dialog's content with the app; Android places it in portrait. */
    void orientDialog(AlertDialog dialog, int desiredWidth) {
        android.view.Window window = dialog.getWindow();
        if (window == null) return;
        Ui.notePresses(window);
        boolean openedByFinger = Ui.fingerPress();
        window.getDecorView().setOnTouchListener((panel, event) -> Ui.restingOutside(panel, event, openedByFinger));
        Theme.dialog(dialog);
        int width = Math.min(dp(desiredWidth), root.getWidth()-dp(32));
        boolean compact = compactLayout();
        if (appRotation == Surface.ROTATION_0 && !compact) { window.setLayout(width, -2); return; }
        ViewGroup content = window.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() != 1 || content.getChildAt(0) instanceof QuarterTurnLayout) return;
        View panel = content.getChildAt(0); content.removeView(panel);
        QuarterTurnLayout frame = new QuarterTurnLayout(this); frame.setTurn(appTurn()); frame.addView(panel);
        if (compact) frame.setMaximumSize(Math.max(1, orientationFrame.getWidth()-dp(32)), Math.max(1, orientationFrame.getHeight()-dp(32)));
        content.addView(frame, new FrameLayout.LayoutParams(-1, -1));
        window.setLayout(landscape ? -2 : width, landscape ? width : -2);
    }
    /**
     * A short status message: on the drawing screen at the brush line's right ({@link InkWashBar#note}); elsewhere in the
     * app's face on a square paper box with an ink outline, a toast with its own view, since the system draws a plain
     * text toast in its own font.
     */
    void message(String text) {
        // On the drawing screen, at the brush line's right, where the canvas never covers it (the owner, 2026-10-22);
        // a toast on other screens, under a dialog's, and in Zen Mode.
        boolean drawing = inkWashBar != null && !zen && (notebooks == null || !notebooks.shown())
                && (inkWashBar.brushLine.getWidth() <= 0 || hasWindowFocus());
        if (drawing && inkWashBar.note(text)) return;
        Toast toast = new Toast(this);
        toast.setView(messageView(text)); toast.setDuration(Toast.LENGTH_LONG);
        toast.show();
    }
    /** {@link #message}'s words as it shows them. */
    TextView messageView(String text) {
        TextView words = Theme.style(new TextView(this), TypeStyle.BODY);
        words.setText(text); words.setBackground(Theme.box(this));
        words.setPadding(dp(16), dp(12), dp(16), dp(12));
        return words;
    }

    // Files

    /**
     * The page changed (a stroke, an edit, Undo): the whole book is saved to the recovery file once drawing pauses
     * ({@link RecoverySchedule}: 2.5 s after the last change, 5 s at most while changes keep coming, never while a stroke
     * is down), and to the painting's own file within a minute.
     */
    void recovery() {
        if (loading || pad.document == null) return;
        recoveryIn(recoverySchedule.changed(android.os.SystemClock.uptimeMillis()));
        scheduleAutosave();
    }
    private final RecoverySchedule recoverySchedule = new RecoverySchedule();
    private final Runnable recoveryDue = () -> {
        if (destroyed) return;
        long wait = recoverySchedule.ready(android.os.SystemClock.uptimeMillis(), busy() || pad.drawingNow(), inkWash != null && inkWash.session.wet());
        if (wait == 0) recoveryNow(); else recoveryIn(wait);
    };
    private void recoveryIn(long wait) { pad.removeCallbacks(recoveryDue); if (wait >= 0) pad.postDelayed(recoveryDue, wait); }
    /** A change still waiting for its recovery save is saved now: the notebook view or the shelf is shown. */
    private void recoverySaved() { if (recoverySchedule.waiting()) recoveryNow(); }
    /** Queues the whole book's recovery save now (a pause, a page turned, a notebook opened, a save, a rename). */
    void recoveryNow() {
        pad.removeCallbacks(recoveryDue); recoverySchedule.saved();
        if (loading || pad.document == null) return;
        commitTimer();
        // The covers follow the notebook's name (a rename, a notebook opened); its own toolbar as it stands.
        nameBook(true);
        captureToolbar();
        DrawingBook saving = book;
        DocumentStore.Snapshot snapshot = new DocumentStore.Snapshot(book, drawingName);
        store.recoverLater(snapshot, (unused, error) -> {
            if (error != null) {
                Log.e(ProbeActivity.TAG, "Drawing recovery save failed", error);
                runOnUiThread(() -> saveError = error.getMessage());
                return;
            }
            // The page as this save packed it: turning the page, or the next save, needn't pack it again.
            PackedPage packed = snapshot.book.activePacked();
            if (packed != null) runOnUiThread(() -> { if (book == saving) saving.offerPacked(snapshot.book.activeKey, packed); });
        });
        scheduleAutosave();
    }
    /**
     * How long a named painting's own file may lag its recovery file while drawing. Supernote
     * Cloud syncs the library, and rewriting a file there every few seconds broke the sync.
     */
    private static final long AUTOSAVE_MS = 60_000;
    private final Runnable autosave = this::autosave;
    private boolean autosaveScheduled;
    private void scheduleAutosave() {
        if (autosaveScheduled || drawingName.isEmpty() || book == null || !book.unsaved()) return;
        autosaveScheduled = true; pad.postDelayed(autosave, AUTOSAVE_MS);
    }
    private void autosaveNow() { pad.removeCallbacks(autosave); autosave(); }
    /** Writes a named painting to its own file without interrupting drawing. */
    private void autosave() {
        autosaveScheduled = false;
        if (destroyed || drawingName.isEmpty() || book == null || !book.unsaved()) return;
        if (busy() || pad.stroking()) { autosaveScheduled = true; pad.postDelayed(autosave, 1000); return; }
        commitTimer();
        describeName();
        captureToolbar();
        DrawingBook savedBook = book; String path = drawingName;
        DocumentStore.Snapshot snapshot = withPicture(new DocumentStore.Snapshot(book));
        store.save(path, snapshot, (unused, error) -> runOnUiThread(() -> {
            if (destroyed) return;
            if (error != null) {
                Log.e(ProbeActivity.TAG, "Drawing autosave failed", error);
                saveError = error.getMessage(); showSaveError(); return;
            }
            if (path.equals(drawingName)) saved(savedBook, snapshot, path);
        }));
    }
    private void showSaveError() {
        if (saveError != null) { message("Autosave failed: " + saveError); saveError = null; }
    }
    private static final int SHARED_STORAGE = 2;
    /** Asked on every launch until allowed, before the working drawing loads. */
    private void askForSharedStorage() {
        store.drawingCount((count, error) -> runOnUiThread(() -> askForSharedStorage(count == null ? 0 : count)));
    }
    private void askForSharedStorage(int drawings) {
        if (destroyed) return;
        reveal();
        String risk = drawings == 0 ? "Drawings inside the app are lost if it's uninstalled."
                : (drawings == 1 ? "1 drawing is" : drawings + " drawings are") + " inside the app, lost if it's uninstalled.";
        showDialog(new AlertDialog.Builder(this).setTitle("Keep drawings in Files?")
                .setMessage(risk + " Allow file access to keep them in " + DrawingStorage.SHARED_NAME + ".")
                .setPositiveButton("Allow", (d, w) -> {
                    if (!DrawingStorage.request(this, SHARED_STORAGE)) { message("This tablet cannot grant file access"); loadDrawing(); }
                })
                .setNegativeButton("Not now", (d, w) -> loadDrawing())
                .setCancelable(false));
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == SHARED_STORAGE) loadDrawing();
    }
    private void startNewDrawing() { String name = newName(); startNewDrawing(name); givenName = name; }
    /** The open notebook's name as the app gave it, before its first save says its options in it ({@link NotebookNames#named}); null when typed or picked. */
    private String givenName;
    /** A new notebook named {@code path}, its one blank page drawn on. */
    private void startNewDrawing(String path) { loading = false; replaceBook(null); named(path); recoveryNow(); }
    /** A fresh title (its date kept unseen, {@link DrawingNames#notebook}) no drawing or PNG copy has yet. */
    private String newName() {
        return DrawingNames.notebook(java.time.LocalDate.now(), new java.util.Random(),
                name -> drawingExists(name) || AutoExport.hasCopies(name));
    }
    /**
     * Before a new drawing's first save, its date-and-words name takes words for what's on it, once: the brushes drawn
     * with most ({@link DrawingNames#described}). Nothing is on disk under the old name yet but perhaps its PNG copy,
     * which follows. A drawing renamed, opened or saved as is never renamed.
     */
    private void describeName() {
        if (loading || book == null || inkWash == null || !book.unsaved() || drawingExists(drawingName)) return;
        String folder = DrawingFiles.parent(drawingName);
        java.util.function.Predicate<String> taken = n -> { String path = DrawingFiles.child(folder, n); return drawingExists(path) || AutoExport.file(path).exists(); };
        String name = DrawingNames.described(DrawingFiles.name(drawingName), inkWash.drawn.top(), taken);
        // A new notebook's generated name says its options as set on its card ("- no erasing - 5 min").
        if (name == null) { name = NotebookNames.named(DrawingFiles.name(drawingName), drawingName.equals(givenName), book.options()); if (name.equals(DrawingFiles.name(drawingName)) || taken.test(name)) return; }
        String from = drawingName;
        named(DrawingFiles.child(folder, name));
        AutoExport.rename(from, drawingName);
        recoveryNow();
    }
    /** Whether a drawing is already saved in the library at {@code path} (folder and name, no extension). */
    private boolean drawingExists(String path) {
        return DrawingFiles.exists(new File(DrawingStorage.library(this), path + DrawingFiles.EXTENSION));
    }
    /**
     * The notebook or the app left: its toolbar kept, a new notebook named by what's on it. No copies are written: the
     * PDF and PNGs are Export Now's alone (the owner, 2026-10-24: "export only when you need it").
     */
    private void leaving() {
        if (loading || book == null || pad.document == null) return;
        pad.finishStroke(); // a stroke still down is not in the page yet
        captureToolbar();
        describeName();
    }
    /**
     * Runs {@code then}, which replaces the open drawing, once its changes are saved: a named
     * painting saves to its own file, and an unnamed one, kept only in the recovery file, asks
     * for a name or to drop it.
     */
    private void settleChanges(String replacing, Runnable then) {
        if (pad != null) pad.settleDrying();
        leaving(); // names a new drawing by what's on it first
        if (loading || book == null || !book.unsaved()) { then.run(); return; }
        if (!drawingName.isEmpty()) { saveDrawingTo(drawingName, true, then); return; }
        showDialog(new AlertDialog.Builder(this).setTitle("Save this drawing?")
                .setMessage("It has no name yet.")
                .setPositiveButton("Save as…", (d, w) -> saveDrawingAs(then))
                .setNeutralButton("Don't save", (d, w) -> then.run())
                .setNegativeButton("Cancel", null));
    }
    private void saveDrawingAs() { saveDrawingAs(() -> { }); }
    private void saveDrawingAs(Runnable saved) {
        showSaveError();
        new DrawingBrowser(this, store, true, drawingName, (path, replace) -> saveDrawingTo(path, replace, saved), () -> { });
    }
    private void saveDrawingTo(String path, boolean replace, Runnable saved) {
        if (busy()) return;
        pad.finishStroke(); pad.dryWet();
        captureToolbar();
        DrawingBook savedBook = book;
        saving = true;
        DocumentStore.Snapshot snapshot = withPicture(new DocumentStore.Snapshot(book));
        store.save(path, snapshot, replace, (unused, error) -> runOnUiThread(() -> {
            saving = false;
            if (destroyed) return;
            if (error != null) { message("Save failed: " + error.getMessage()); return; }
            saved(savedBook, snapshot, path);
            message("Saved");
            if (book == savedBook) saved.run();
        }));
    }
    /** Records that {@code snapshot} of {@code savedBook} reached {@code path}, which becomes its file if it is still open. */
    private void saved(DrawingBook savedBook, DocumentStore.Snapshot snapshot, String path) {
        // The picture of the page drawn on, made as it was saved, and the pages as the file holds them now: leaving the
        // page needn't pack it again, and the recovery save below names them in the file instead of packing them.
        byte[] picture = snapshot.book.picture(snapshot.book.index);
        if (picture != null) savedBook.offerPicture(snapshot.book.activeKey, picture);
        savedBook.savedPages(snapshot.book);
        // A failed save must never change the destination of subsequent saves.
        if (book == savedBook) { savedBook.markSaved(snapshot.book); named(path); recoveryNow(); }
    }
    private static final int PICK_FOLDER = 1;
    private static final int BRUSH_LAB = 3, TOOLS = 4, PICK_DOCUMENT = 5;
    /**
     * Opens the Tools page on slot {@code slot} (the tool picker's Advanced, {@link #toolPicker}), turned as
     * the drawing is; it answers in {@link #toolsReturned}.
     */
    void openTools(int slot) { openTools(slot, null); }
    /** As {@link #openTools(int)}; {@code start}: the slots' start the Tools page had before Brush Lab ({@link ToolsActivity#START}). */
    private void openTools(int slot, String[] start) {
        inkWashBar.closePanel();
        pad.finishStroke();
        // A toolbar that can't change (Each Page, a locked notebook's own): the page says so instead of taking picks.
        NotebookOptions fixed = inkWash.slots.frozen ? rules() : null;
        CardSheet.Screen settings = NotebookRules.toolsSettings(fixed);
        startActivityForResult(new Intent(this, ToolsActivity.class).putExtra(ToolsActivity.SLOT, slot).putExtra(ToolsActivity.TURN, appTurn())
                .putExtra(ToolsActivity.FIXED, NotebookRules.toolsFixed(fixed)).putExtra(ToolsActivity.SETTINGS, settings == null ? null : settings.name())
                .putExtra(ToolsActivity.BAR, rules().slotsShown()).putExtra(ToolsActivity.START, start).putExtra(ToolsActivity.OPENED, android.os.SystemClock.uptimeMillis()), TOOLS);
        overridePendingTransition(0, 0);
    }
    /**
     * Back from the Tools page: each slot it changed takes its brush (an Undo step each, its size set there already, as
     * Brush Lab's), and the slot selected there is selected, named under the bar if its brush changed; then Brush Lab on
     * it, if that is what was tapped, or on a new brush for it (New Brush), whose Back comes to the Tools page again
     * ({@link #brushLabReturned}), the slots' start carried through.
     */
    private void toolsReturned(Intent data) {
        int slot = data.getIntExtra(ToolsActivity.SLOT, -1);
        String[] written = data.getStringArrayExtra(ToolsActivity.BRUSHES);
        if (slot < 0 || slot >= ToolSlots.COUNT || written == null || written.length != ToolSlots.COUNT) return;
        boolean lab = data.getBooleanExtra(ToolsActivity.LAB, false);
        String settings = data.getStringExtra(ToolsActivity.SETTINGS);
        if (settings != null) { inkWashBar.selectTool(slot); showCardScreen(CardSheet.Screen.valueOf(settings)); return; }
        // A tool kit (ToolKits): the bar shows all its slots (Brushes on Bar raised), and its eraser step sets the eraser's kind.
        int bar = data.getIntExtra(ToolsActivity.BAR, -1), eraser = data.getIntExtra(ToolsActivity.ERASER, -1);
        if (bar > 0 && book != null && rules().brushes > 0 && bar > rules().brushes) setNotebookOptions(book.options().withBrushes(bar));
        if (eraser >= 0 && eraser < EraseKind.KINDS.length) { inkWash.eraseKinds = EraseKind.KINDS[eraser]; inkWashBar.eraserChanged(); }
        boolean changed = slotsReturned(written, slot, data.getBooleanExtra(ToolsActivity.TOGETHER, false));
        if (lab) { openBrushLab(slot, data.getStringArrayExtra(ToolsActivity.START)); return; }
        if (data.getBooleanExtra(ToolsActivity.CUT, false)) { cutStamp(slot); return; }
        if (!changed) return;
        // Its step in a kit ("1 Drop ink"), else its name.
        String name = inkWashBar.said(slot);
        // Once the drawing is back on screen: the label is a popup over it.
        pad.post(() -> inkWashBar.brushChanged(slot, name));
    }
    /**
     * Each of the ten slots whose brush came back ({@code written}, as {@link Brush#write}; null: unchanged) takes it,
     * an Undo step each, or, {@code together}, every change one Undo step (a tool kit's, {@link ToolSlots#useAll}). Every
     * button shows its slot as it is now, and slot {@code slot} is selected. Whether {@code slot}'s brush changed.
     */
    private boolean slotsReturned(String[] written, int slot, boolean together) {
        boolean changed = false;
        boolean refused = false;
        Brush[] brushes = new Brush[ToolSlots.COUNT];
        for (int i = 0; i < ToolSlots.COUNT; i++) brushes[i] = Brush.read(written[i]);
        boolean[] usedAll = together ? inkWash.slots.useAll(brushes) : null;
        for (int i = 0; i < ToolSlots.COUNT; i++) {
            Brush brush = brushes[i];
            boolean used = brush != null && (together ? usedAll[i] : inkWash.slots.use(i, brush));
            if (brush != null && !used && inkWash.slots.frozen && !brush.identity().equals(inkWash.slots.brush(i).identity())) refused = true;
            if (used && i == slot) changed = true;
            else inkWashBar.showBrush(i);
        }
        // Never silently: a fixed toolbar (Brush Lab's change to it) says why it kept its brushes.
        String fixed = refused ? NotebookRules.toolsFixed(rules()) : null;
        if (fixed != null) note(fixed);
        inkWashBar.selectTool(slot);
        // A notebook with a toolbar of its own keeps the change (captureToolbar, at the next pause or save).
        recovery();
        return changed;
    }
    /**
     * Cut Stamp (the Tools page's, for a slot holding a Stamp, and the stamp tool's menu, {@link InkWash#STAMP}): the
     * pen's next stroke on the page is a lasso ({@link DrawingPad.Placing#CUT}), shown in marching ants; the ink inside it
     * becomes tool {@code slot}'s nib ({@link #stampCut}). Said beside the tool once the drawing is back on screen.
     */
    void cutStamp(int slot) {
        pad.cutting = slot;
        pad.placing = DrawingPad.Placing.CUT;
        pad.post(() -> inkWashBar.sayAt(slot, StampNib.LASSO, StampNib.LASSO_MS));
    }
    /**
     * Cut Stamp's lasso lifted: the ink inside it, trimmed, is the slot's (or the stamp tool's) Stamp's nib from now on (kept in the nib store,
     * an Undo step as any change of a slot's brush), or "Nothing to cut".
     */
    void stampCut(MirrorShow.Place lasso) {
        pad.placing = DrawingPad.Placing.NONE;
        int slot = pad.cutting;
        ToneDocument page = pad.document;
        Brush brush = inkWash.slots.brush(slot);
        StampNib nib = page == null || brush == null || !brush.parts.stamps() ? null
                : StampNib.cut(page::compositeTone, page.paperTone(), lasso.xs(), lasso.ys(), lasso.count(), page.width, page.height);
        if (nib == null) { inkWashBar.sayAt(slot, StampNib.NOTHING, StampNib.NOTHING_MS); return; }
        if (inkWash.slots.use(slot, Brush.of(brush.parts.withNib(StampNib.keep(nib))))) recovery();
        inkWashBar.brushChanged(slot, StampNib.DONE);
    }
    /** Opens Brush Lab on slot {@code slot}'s brush; the ten slots' brushes come back in {@link #brushLabReturned}. */
    void openBrushLab(int slot) { openBrushLab(slot, null); }
    /**
     * As {@link #openBrushLab(int)}; {@code start}: opened from the Tools page, its slots' start, and its Back goes to the Tools page again ({@link
     * BrushLabActivity#START}).
     */
    private void openBrushLab(int slot, String[] start) {
        inkWashBar.closePanel();
        pad.finishStroke();
        startActivityForResult(new Intent(this, BrushLabActivity.class).putExtra(BrushLabActivity.SLOT, slot)
                .putExtra(BrushLabActivity.START, start), BRUSH_LAB);
        overridePendingTransition(0, 0);
    }
    /**
     * Back from Brush Lab: each slot whose brush the lab changed takes it (each one Undo takes back), and the lab's
     * selected slot is selected, named under the bar if its brush changed. Slots the lab didn't change keep their
     * brushes; every button shows its slot's size and grey, which the lab may have set. Opened from the Tools page, the
     * Tools page opens again on the slot, with the start it had (its Back reads "Tools").
     */
    private void brushLabReturned(Intent data) {
        int slot = data.getIntExtra(BrushLabActivity.SLOT, -1);
        String[] written = data.getStringArrayExtra(BrushLabActivity.BRUSHES);
        if (slot < 0 || slot >= ToolSlots.COUNT || written == null || written.length != ToolSlots.COUNT) return;
        boolean changed = slotsReturned(written, slot, false);
        String[] start = data.getStringArrayExtra(BrushLabActivity.START);
        if (start != null) { openTools(slot, start); return; }
        if (!changed) return;
        String name = inkWash.slots.brush(slot).name();
        // Once the drawing is back on screen: the label is a popup over it.
        pad.post(() -> inkWashBar.brushChanged(slot, name));
    }
    private Consumer<Uri> folderPicked;
    /** Opens Android's folder picker at Supernote's EXPORT folder; keeps access to the chosen folder. */
    void pickFolder(Consumer<Uri> picked) {
        folderPicked = picked;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                .putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                        DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:EXPORT"));
        try { startActivityForResult(intent, PICK_FOLDER); }
        catch (android.content.ActivityNotFoundException error) { folderPicked = null; message("No folder picker on this tablet"); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == SHARED_STORAGE) { loadDrawing(); return; }
        if (request == BRUSH_LAB) { if (result == RESULT_OK && data != null) brushLabReturned(data); return; }
        if (request == TOOLS) { if (result == RESULT_OK && data != null) toolsReturned(data); return; }
        if (request == PICK_DOCUMENT) {
            Consumer<Uri> picked = documentPicked; documentPicked = null;
            if (picked != null && result == RESULT_OK && data != null && data.getData() != null) picked.accept(data.getData());
            return;
        }
        if (request != PICK_FOLDER) return;
        Consumer<Uri> picked = folderPicked; folderPicked = null;
        if (picked == null || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri tree = data.getData();
        getContentResolver().takePersistableUriPermission(tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        picked.accept(tree);
    }
    // Import and Export Now (the owner, 2026-10-22: docs/archive/superpowers/specs/2026-10-22-import-design.md; Import
    // Image and Import PDF one Import since 2026-10-29)

    private Consumer<Uri> documentPicked;
    /** Android's document picker for one picture or PDF; {@code picked} hears it, if one is. */
    private void pickDocument(Consumer<Uri> picked) {
        documentPicked = picked;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "application/pdf"});
        try { startActivityForResult(intent, PICK_DOCUMENT); }
        catch (android.content.ActivityNotFoundException error) { documentPicked = null; message("No file picker on this tablet"); }
    }
    /**
     * Import (the owner, 2026-10-29: "can we merge the pdf and image import flow? ... so it'd be just one import thing"):
     * one picker for pictures and PDFs, the app's own, with Cancel ({@link ImportPicker}), or Android's where the app may
     * not list files. A picture picked is placed ({@link #importPicture}); a PDF asks how ({@link ImportPicker#pdf}): a
     * new notebook of its pages ({@link #importPdf}), or the page shown, drawn as large as fits the page, placed as a
     * picture is.
     */
    private void importFile() {
        if (book == null || busy()) return;
        Consumer<Uri> picked = uri -> {
            if (!PictureImport.pdf(getContentResolver().getType(uri), uri.getPath())) importPicture((w, h) -> PicturePlacer.read(getContentResolver(), uri, w, h));
            else ImportPicker.pdf(this, uri, () -> importPdf(uri), page -> importPicture((w, h) -> PdfImport.page(this, uri, page, w, h).picture));
        };
        if (DrawingStorage.sharedAllowed(this)) new ImportPicker(this, picked);
        else pickDocument(picked);
    }
    /** A picture read no larger than fits a {@code width × height} page, off the main thread. */
    private interface PictureRead { android.graphics.Bitmap read(int width, int height) throws Exception; }
    /**
     * A picture {@code read}, then placed on the layer drawn on ({@link PicturePlacer}, {@link PictureImport#place}), as
     * one Undo step. A page locked takes none.
     */
    private void importPicture(PictureRead read) {
        if (book == null || busy()) return;
        if (pageLocked()) { note(NotebookRules.PAGE_LOCKED); return; }
        int width = book.width, height = book.height;
        new Thread(() -> {
            android.graphics.Bitmap picture;
            try { picture = read.read(width, height); }
            catch (Exception | OutOfMemoryError error) { Log.w(ProbeActivity.TAG, "Import failed", error); runOnUiThread(() -> message(PictureImport.UNREADABLE)); return; }
            runOnUiThread(() -> placePicture(picture));
        }, "import picture").start();
    }
    /** {@code picture} shown over the page drawn on, to move and size, then Place or Cancel. */
    private void placePicture(android.graphics.Bitmap picture) {
        if (destroyed || book == null || busy() || pageLocked()) { picture.recycle(); return; }
        dismissPanels(); pad.words.close(); pad.finishStroke();
        pad.placer.begin(picture, this::lay);
    }
    /** Place: the picture laid where it lies, on the layer drawn on, as any mark. */
    private void lay(android.graphics.Bitmap picture, PictureImport.Frame frame) {
        Rect area = new Rect(frame.rect()[0], frame.rect()[1], frame.rect()[2], frame.rect()[3]);
        if (book == null || !area.intersect(0, 0, book.width, book.height)) return;
        int[] greys = PictureImport.greys(PicturePlacer.pixels(picture, frame, area));
        layerChange(() -> PictureImport.place(book, area.left, area.top, area.width(), area.height(), greys));
        pad.refreshAfterLargeChange("picture placed");
        if (inkWashBar != null) inkWashBar.pageChanged();
    }
    /** The PDF importing (one at a time), and its progress. */
    private PdfImport pdfImport;
    /**
     * A PDF picked, New Notebook: made a new notebook named after it ({@link PdfImport}), the open one saved first; while
     * it works a box says how far it is, with Cancel. Done, the new notebook opens; a PDF cut short says so.
     */
    private void importPdf(Uri uri) {
        if (book == null || busy() || pdfImport != null) return;
        int width = book.width, height = book.height;
        PdfImport work = pdfImport = new PdfImport();
        AlertDialog box = showDialog(new AlertDialog.Builder(this).setTitle(PictureImport.IMPORT).setMessage(PictureImport.importing(0, 0)).setCancelable(false)
                .setNegativeButton(PictureImport.CANCEL, (d, w) -> work.cancel()));
        CoverDesign[] designs = CoverDesign.values();
        work.run(this, uri, width, height, designs[new java.util.Random().nextInt(designs.length)], java.time.LocalDate.now().toEpochDay(), store,
                (done, of) -> runOnUiThread(() -> { if (!work.cancelled()) box.setMessage(PictureImport.importing(Math.min(done + 1, of), of)); }),
                (made, error) -> runOnUiThread(() -> {
                    if (pdfImport == work) pdfImport = null;
                    if (box.isShowing()) box.dismiss();
                    if (destroyed) return;
                    if (error != null) { message(PictureImport.NO_PAGES.equals(error.getMessage()) ? PictureImport.NO_PAGES : PictureImport.UNREADABLE); return; }
                    if (made == null) return;
                    if (made.cut) message(PictureImport.firstPages(made.pages));
                    openNotebook(made.path);
                }));
    }
    /**
     * Export Now, the one way copies are made (the owner, 2026-10-24: "export only when you need it"): the notebook's PDF
     * in Documents/<app folder> and its PNGs in EXPORT/<app folder>, wet paint set first; done, a note says where the PDF is.
     */
    void exportNow() {
        if (book == null || busy() || loading || pad.document == null) return;
        leaving();
        pad.dryWet();
        if (!AutoExport.notebook(this, drawingName, book.snapshot(), () -> message(EXPORT_FAILED), () -> message(EXPORTED))) message(EXPORT_FAILED);
    }
    /** Export Now's notes: where the copies are (docs/TEXT-STYLE.md: the result, in a few words). */
    static final String EXPORTED = "PDF in " + DrawingStorage.SHARED_NAME, EXPORT_FAILED = "Export failed";
    /**
     * Export Page (Page Settings, the owner, 2026-10-28): page {@code place}'s (a stack's) {@code choice} copy, a PDF, PNG
     * or Layers ({@link PageExport}), wet paint set first, as Export Now; done, a note says where it is: "p5.psd in EXPORT/Scribulor".
     */
    void exportPage(int place, String choice) {
        if (book == null || busy() || loading || pad.document == null) return;
        leaving();
        pad.dryWet();
        String done = PageExport.page(place, choice) + " in " + PngExport.SUPERNOTE_FOLDER;
        if (!AutoExport.page(this, drawingName, book.snapshot(), place, choice, () -> message(EXPORT_FAILED), () -> message(done))) message(EXPORT_FAILED);
    }
    private void exportPng() {
        pad.dryWet();
        PngExport.show(this, book.snapshot(), drawingName);
    }
    private void openDrawing() {
        showSaveError();
        new DrawingBrowser(this, store, false, drawingName, (path, replace) -> {
            boolean recovering = loading;
            loading = true;
            store.openBook(path, (document, error) -> runOnUiThread(() -> {
                if (destroyed) return;
                loading = false;
                if (error != null) {
                    message("Open failed: " + error.getMessage());
                    if (recovering) { loading = true; openRecoveryChoice(); }
                    else pad.post(pad::connectDisplay);
                    return;
                }
                replaceBook(document); named(path); recoveryNow();
            }));
        }, () -> { if (!destroyed && loading) openRecoveryChoice(); });
    }
    private void openRecoveryChoice() {
        showDialog(new AlertDialog.Builder(this).setTitle("Start a new drawing?")
                .setMessage("The recovery file couldn't be read.")
                .setPositiveButton("New", (d, w) -> startNewDrawing())
                .setNegativeButton("Open", (d, w) -> openDrawing()).setCancelable(false));
    }
    /**
     * Renames the notebook at {@code from} through the name prompt ({@link NamePrompt}: its name selected, other names
     * under it). The open one's saves made after this point must go to the new name: autosave reads {@code drawingName},
     * so it switches at once, before the rename runs on the store's queue. Saves already queued run first under the old
     * name, which the rename then moves. Another notebook (a long press on the shelf) has no saves to follow.
     */
    void renameNotebook(String from) {
        if (from.isEmpty()) return;
        String folder = DrawingFiles.parent(from);
        NotebookNames names = NotebookNames.renaming(DrawingFiles.name(from), java.time.LocalDate.now());
        // Checked here because the store would otherwise overwrite; a case-only change (this notebook's own file) goes through.
        NamePrompt.show(this, NotebookNames.RENAME, names, name -> drawingExists(DrawingFiles.child(folder, name)), name -> {
            String to = DrawingFiles.child(folder, name);
            if (to.equals(from) || busy()) return;
            boolean open = from.equals(drawingName);
            if (open) { pad.removeCallbacks(autosave); autosaveScheduled = false; named(to); }
            store.rename(from, name, (renamed, error) -> runOnUiThread(() -> {
                if (destroyed) return;
                if (error != null) {
                    // Back to the old name, unless the user has moved on to another drawing.
                    if (open && drawingName.equals(to)) { named(from); recoveryNow(); }
                    message("Rename failed: " + error.getMessage()); return;
                }
                AutoExport.rename(from, renamed);
                notebooks.renamed();
            }));
            if (open) recoveryNow();
        });
    }
    /**
     * Deletes the notebook at {@code path}, after asking unless nothing is drawn in it. The open one is replaced before
     * its files are deleted, so no later save can write it again; saves already queued run first and the delete removes
     * what they wrote.
     */
    void deleteNotebook(String path) {
        if (path.isEmpty()) return;
        boolean open = path.equals(drawingName) && book != null;
        Runnable delete = () -> {
            if (busy()) return;
            if (open && path.equals(drawingName)) {
                pad.removeCallbacks(autosave); autosaveScheduled = false;
                startNewDrawing();
            }
            store.delete(path, (unused, error) -> runOnUiThread(() -> {
                if (destroyed) return;
                if (error != null) { message("Delete failed: " + error.getMessage()); return; }
                AutoExport.delete(path);
            }));
            // Read after the delete on the store's queue: the shelf no longer has it.
            notebooks.showShelf();
        };
        // Another notebook than the open one isn't read until it is opened: it is taken to have drawings.
        if (open && BlankPages.empty(book)) { delete.run(); return; }
        showDialog(new AlertDialog.Builder(this).setTitle("Delete this notebook?")
                .setMessage("\"" + DrawingNames.shown(DrawingFiles.name(path), java.time.LocalDate.now()) + "\" and its PDF and PNG copies will be deleted. This can't be undone.")
                .setPositiveButton("Delete", (d, w) -> delete.run())
                .setNegativeButton("Cancel", null));
    }

    // Notebooks (docs/archive/superpowers/specs/2026-10-18-notebooks-design.md)

    /** Whether the shelf or a notebook covers the drawing screen. */
    boolean notebookShown() { return notebooks != null && notebooks.shown(); }
    /** The open notebook, at the page drawn on. */
    void showNotebook() { if (leaveDrawing()) notebooks.showPages(book.index()); }
    /** The menu's Settings: the open notebook's library card. */
    void showSettings() { if (leaveDrawing()) notebooks.showCard(); }
    /** The library card with its screen {@code screen} open (the Tools page's Notebook Settings). */
    private void showCardScreen(CardSheet.Screen screen) { if (leaveDrawing()) notebooks.showCard(screen); }
    /** The page drawn on left for the notebook screen: its stroke finished, its clock stopped, its picture made; false when there is no notebook or it's busy. */
    private boolean leaveDrawing() {
        if (book == null || busy()) return false;
        pad.finishStroke(); pauseTimer(); recoverySaved(); notebooks.leavingPage();
        return true;
    }
    /**
     * The bar's ‹ and ›, a swipe, Time's up's Next Page: the page before or after, in the notebook's order (the front
     * cover, the pages, the back cover), a stack of layers one page ({@link PageStacks}): turned to on the layer drawn on last ({@link DrawingBook#pageOf}), else its top.
     */
    void turnPage(int by) {
        if (book == null || busy()) return;
        PageStacks s = book.stacks();
        int here = s.of(book.index()), to = by < 0 ? s.before(here) : s.after(here);
        if (to != DrawingBook.NONE) changePage(book.pageOf(to));
    }
    /**
     * The page bar's previews: page {@code from} (a stack of layers, whole) moved to place {@code to} (docs/archive/superpowers/
     * specs/2026-10-20-tracing-paper-design.md, "Reorder"), the page drawn on staying the page drawn on. Free under the
     * lock and the rules (the spec's "Rules, the lock and timed pages"): the pages' order is not a tool or a rule.
     */
    void moveStack(int from, int to) {
        if (book == null || busy() || pad.stroking()) return;
        int n = book.stacks().count();
        if (from < 0 || to < 0 || from >= n || to >= n || from == to) return;
        book.moveStack(from, to);
        updatePages(); recoveryNow();
    }
    /**
     * Tracing paper made {@code tracing} (docs/archive/superpowers/specs/2026-10-20-tracing-paper-design.md): the Paper, a page's
     * step or the ghost; a book edit, free under the lock and the rules (a view of the pages, not a tool or a rule). The
     * ghosts follow once the taps settle.
     */
    void setTracing(TracingPaper tracing) {
        if (book == null || busy() || pad.stroking() || tracing.equals(book.tracing())) return;
        book.setTracing(tracing);
        if (inkWashBar != null) inkWashBar.turner.tracingChanged();
        pad.showSteps(layerSteps(pad.document), false);
        recoveryNow(); ghosts.changed();
    }
    /** Tracing paper's layer steps for {@code page} on the screen: the page drawn on's, else none. */
    int layerSteps(ToneDocument page) { return (book == null ? 0 : book.layerSteps(page)) | (page == null ? 0 : page.kindsShown() << ToneDocument.KIND_STEPS); }
    /** Drawing on page {@code page} (a layer), turned to through the fast page turn. */
    void drawOn(int page) { if (book != null && page != book.index()) changePage(page); }
    /** Drawing on page {@code place} (a stack, on the layer drawn on last, else its top; or a cover): the strip, the notebook screen's Open. */
    void drawOnStack(int place) {
        if (book == null) return;
        PageStacks s = book.stacks();
        if (DrawingBook.isCover(place) || place >= 0 && place < s.count()) drawOn(book.pageOf(place));
    }

    // Layers: a layer is a page stacked on the one below (the spec's "Stage 3c"); added and picked in Page Settings since the refocus (2026-10-24).

    /** Layer changes Undo takes back in one step ({@link LayerEdits}), the last first; a few at most. */
    private final java.util.ArrayDeque<LayerEdits.Undo> layerUndos = new java.util.ArrayDeque<>();
    private void undoable(LayerEdits.Undo undo) {
        if (undo == null) return;
        layerUndos.push(undo);
        while (layerUndos.size() > 8) layerUndos.removeLast();
    }
    /** A new layer: a clear page stacked onto the page drawn on, {@code above} it or below it, drawn on. */
    void newLayer(boolean above) {
        if (book == null || DrawingBook.isCover(book.index())) return;
        pageEdit("add", () -> book.addLayer(above));
    }
    /** Merge Old Layers: the page drawn on's in-page layers made one, hidden ones too (one undo step); their steps go with them. */
    void mergeLayers() {
        if (book != null && layerEdit(() -> InkWashLayers.merge(pad.document))) setTracing(book.tracing().withLayers(book.index(), new int[]{-1}));
    }
    /** The strip's +: a page after the last, shown in the notebook. */
    void addNotebookPage() { addPage(); notebooks.showAdded(); }
    /**
     * Delete Page, inside a notebook: page {@code place} (a stack, every layer of it), at once when it is blank, else after
     * asking; a page not drawn on is never decoded for it (only to tell whether it is blank, when its picture is white).
     */
    void deleteStack(int place) {
        if (book == null || DrawingBook.isCover(place)) return;
        PageStacks s = book.stacks();
        if (s.count() <= 1 || place < 0 || place >= s.count()) return;
        int from = s.first(place), to = s.last(place);
        boolean asks = false;
        for (int p = from; p <= to; p++) asks |= BlankPages.deleteAsks(book, p);
        Runnable delete = () -> pageEdit("delete", () -> {
            // The page drawn on goes too: the page after it is drawn on first (the one before, for the last).
            int at = book.index();
            if (at >= from && at <= to) book.select(book.pageOf(to + 1 < book.count() ? place + 1 : place - 1));
            for (int p = to; p >= from; p--) book.deletePage(p);
        });
        // No Erasing (the page's own, or the notebook's) keeps a page with marks (as Clear page).
        if (erasingOff(rules(from), asks)) return;
        if (asks) notebooks.confirmDelete(place, delete); else delete.run();
    }
    /** ‹ Notebooks: the notebook is left, and the shelf shows. */
    void leaveNotebook() {
        if (busy()) return;
        captureToolbar();
        if (leavePrompt()) recovery();
        recoverySaved();
        notebooks.leavingPage();
        leaving();
        notebooks.showShelf();
    }
    /** A shelf tile: the open notebook, or another opened once the open one is saved. */
    void openNotebook(String path) { openNotebook(path, false); }
    /** As {@link #openNotebook(String)}, on its library card when {@code card} (a shelf tile's Settings). */
    void openNotebook(String path, boolean card) {
        if (path.equals(drawingName) && book != null) {
            if (card) notebooks.showCard(); else notebooks.showPages(book.index());
            return;
        }
        settleChanges("opening another notebook", () -> {
            loading = true;
            store.openBook(path, (opened, error) -> {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    loading = false;
                    if (error != null) { message("Couldn't open it: " + error.getMessage()); return; }
                    replaceBook(opened); named(path); recoveryNow();
                    if (card) notebooks.showCard(); else notebooks.showPages(opened.index());
                });
            });
        });
    }
    /**
     * New notebook, on the shelf: its name asked first (the one it would get, all selected, and others), then its library
     * card, to set it up; its options are said in its name at its first save ({@link #describeName}), once something is drawn.
     */
    void newNotebook() {
        java.time.LocalDate today = java.time.LocalDate.now();
        NotebookNames names = NotebookNames.creating(today, new java.util.Random(), name -> drawingExists(name) || AutoExport.hasCopies(name));
        NamePrompt.show(this, NotebookNames.NEW, names, this::drawingExists, name -> {
            if (shelfBusy()) return;
            settleChanges("starting a new notebook", () -> {
                startNewDrawing(name); givenName = name.equals(names.base) ? name : null; notebooks.showCard();
                // Each new notebook a design of its own, by chance (the owner liked them all); Cover Design changes it.
                CoverDesign[] designs = CoverDesign.values(); setCoverDesign(designs[new java.util.Random().nextInt(designs.length)]);
            });
        });
    }
    /** The name over a notebook's page: Rename. */
    void renameNotebook() { renameNotebook(drawingName); }
    /** A long press on a shelf tile: Rename, Settings (the notebook opened on its library card) and Delete for that notebook. */
    void notebookMenu(View anchor, DocumentStore.Notebook notebook) {
        String path = notebook.path;
        menu(anchor, "notebook menu", new String[]{"Rename", LibraryCard.SETTINGS, "Delete"},
                new int[]{R.drawable.ic_ph_pencil_simple, R.drawable.ic_ph_list, R.drawable.ic_ph_trash},
                new Runnable[]{() -> renameNotebook(path), () -> openNotebook(path, true), () -> deleteNotebook(path)},
                prefs.largeSettingsText() ? 288 : 264);
    }
    /** A named save's snapshot, with the picture of the page drawn on made just before it is written (on the IO thread). */
    private DocumentStore.Snapshot withPicture(DocumentStore.Snapshot snapshot) {
        DrawingBook.Snapshot book = snapshot.book;
        if (book.picture(book.index) == null)
            snapshot.prepare = () -> book.setPicture(book.index, PagePicture.of(book.activePage, WordsMode.exportTiles()).encode());
        return snapshot;
    }
    /**
     * At launch, once: the library's old drawings into notebooks ({@link NotebookMigration}), the shelf saying so, then
     * {@code then}. The working drawing's unsaved changes go in with it; it is then no longer open.
     */
    private void migrateThen(RecoveryCodec.Recovered recovered, Runnable then) {
        if (preferences.getBoolean(NotebookScreens.MIGRATED, false)) { then.run(); return; }
        loading = true;
        notebooks.migrating(0, 0); notebooks.showShelf();
        NotebookMigration.Working working = recovered != null && recovered.book.unsaved() ? new NotebookMigration.Working(recovered.path, recovered.book) : null;
        store.migrate(working, (done, total) -> runOnUiThread(() -> notebooks.migrating(done, total)), (result, error) -> runOnUiThread(() -> {
            if (destroyed) return;
            loading = false;
            notebooks.migrated();
            if (error != null) {
                // Tried again at the next launch; every original is where it was or in Originals, whole.
                Log.e(ProbeActivity.TAG, "Notebooks migration failed", error);
                message("Couldn't make notebooks: " + error.getMessage());
                then.run(); return;
            }
            preferences.edit().putBoolean(NotebookScreens.MIGRATED, true).apply();
            // The working drawing was gathered (with its unsaved changes, or saved and moved to Originals): not reopened.
            boolean gathered = result.working || result.ran && recovered != null && (recovered.path.isEmpty() || !drawingExists(recovered.path));
            if (!gathered) { then.run(); return; }
            // Its drawing is in a notebook now: the recovery goes, and a new notebook is open.
            store.forgetRecovery((unused, forgotten) -> runOnUiThread(() -> {
                if (destroyed) return;
                named(newName()); replaceBook(null);
                notebooks.showShelf();
            }));
        }));
    }

    // Lifecycle

    @Override protected void onResume() {
        super.onResume(); resumed = true;
        DrawingStorage.leaveHandOver(this);
        // The tilt watch for the Rotate prompt (dropped by mistake with MonoPaint's controls, 53b444a).
        rotationPrompt.enable();
        if (inkWash != null) inkWash.importBrushes();
        if (pad != null) { pad.returned(); pad.updateViewport(); pad.post(pad::connectDisplay); pad.resumeDrying(); }
        if (notebooks != null && notebooks.onShelf() && !loading) notebooks.showShelf();
        timedLook();
    }
    @Override protected void onPause() {
        replay.stop();
        if (edgeBars != null) edgeBars.reset();
        if (fullscreen != null) fullscreen.dismiss();
        if (pageOverview != null) pageOverview.dismiss();
        dismissPanels();
        resumed = false;
        rotationPrompt.disable();
        rotationPrompt.hide();
        toolbar.presetDrag.reset();
        if (pad != null) { pad.suspend(); pad.pauseDrying(); pad.removeCallbacks(warmTools); }
        // The pictures not yet drawn ahead are let go: the Tools page, opening now, draws what it needs itself.
        if (toolPictures != null) { toolPictures.close(); toolPictures = null; }
        pauseTimer();
        sand.pause(android.os.SystemClock.uptimeMillis());
        if (!loading && book != null) { leavePrompt(); saveToolState(); recoveryNow(); autosaveNow(); leaving(); }
        DrawingStorage.leaveHandOver(this);
        super.onPause();
    }
    /** How long the drawing screen is left to settle before the Tools page's pictures are drawn ahead, ms; and again while the pen is down. */
    static final long WARM_TOOLS_MS = 3000;
    /** The thread the Tools page's pictures are drawn ahead on (low priority), made the first time. */
    private SampleQueue toolPictures;
    /**
     * Draws the Tools page's first pictures ahead ({@link ToolsActivity#warm}), so even its first opening shows them at once
     * (the owner, 2026-10-28: slow "just on the first load"): once the screen has settled, never while the pen is down or
     * anything else is going on; those kept already cost nothing.
     */
    private final Runnable warmTools = () -> {
        if (destroyed || pad == null || !resumed || !hasWindowFocus()) return;
        if (busy() || pad.stroking()) { pad.postDelayed(this.warmTools, WARM_TOOLS_MS); return; }
        if (toolPictures == null) toolPictures = new SampleQueue();
        ToolsActivity.warm(this, toolPictures, inkWash.slots, InkWash.store(preferences), inkWash.selected,
                Math.round(root.getWidth() / getResources().getDisplayMetrics().density), Math.round(root.getHeight() / getResources().getDisplayMetrics().density));
    };
    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (pad == null) return;
        if (focus) {
            pad.post(pad::connectDisplay); showSaveError();
            android.os.Looper.myQueue().addIdleHandler(() -> { prepareFileMenu(); return false; });
            pad.removeCallbacks(warmTools); pad.postDelayed(warmTools, WARM_TOOLS_MS);
        }
        else { rotationPrompt.dismiss(); pad.finishStroke(); pad.disconnectDisplay(); selectionFeedback.closeSession(); }
    }
    @Override protected void onDestroy() {
        destroyed = true;
        if (rotationPrompt != null) rotationPrompt.orientationSensor.disable();
        selectionFeedback.close();
        layerFeedback.close();
        if (pad != null) pad.close();
        if (inkWashBar != null) inkWashBar.close();
        if (notebooks != null) notebooks.close();
        book = null;
        super.onDestroy();
    }
    @Override public void onBackPressed() {
        if (notebooks != null && notebooks.shown()) { if (!notebooks.back()) super.onBackPressed(); return; }
        if (inkWashBar != null && inkWashBar.panelOpen()) { inkWashBar.closePanel(); return; }
        if (pagePanel != null) { closePagePanel(); return; }
        if (fullscreen != null && fullscreen.mode != 0) { fullscreen.setMode(0); return; }
        if (zen) { setZen(false); return; }
        if (pad != null && (pad.hasGradient() || pad.fillGesture)) { pad.finishStroke(); return; }
        // From drawing, back to its notebook.
        if (!busy()) showNotebook();
    }
}
