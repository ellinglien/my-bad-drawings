package io.github.mpdairy.monopaint;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.view.Display;

/**
 * The tablet MonoPaint is running on, and the only class that identifies hardware. Other code
 * asks about a named fact here (or, for the e-ink driver's buffer, {@link DirectEink}), never
 * about a model. Supporting another tablet means adding a profile below; see docs/DEVICES.md.
 */
final class Device {
    /** The smaller Supernote. */
    static final Device NOMAD = new Device("Nomad", 1404, 1872, true, true, 6, null, 1, 0,
            "Chauvet.E103.2606141001.2389_release", "Chauvet.E103.2610091001.2545_release");
    /** The larger Supernote, which can also preview the Nomad's layout at physical size. */
    static final Device MANTA = new Device("Manta", 1920, 2560, false, false, 24, NOMAD, -1, 0,
            "Chauvet.E103.2606141001.2389_release", "Chauvet.E103.2610091001.2545_release");
    /** Any other tablet, or a Supernote panel no profile matches. */
    static final Device OTHER = new Device("Android", 0, 0, false, false, 24, null, -1, -1);
    static final Device[] SUPERNOTES = {MANTA, NOMAD};

    final String name;
    /** The physical panel in natural portrait, in pixels; 0 when unknown. */
    final int panelWidth, panelHeight;
    /**
     * The digitizer reports tilt in the panel's landscape scan frame (the Nomad's firmware
     * hwrota=270), while positions arrive already turned to portrait. Confirmed by hand on a
     * Nomad on 2026-10-06 with the Pencil and flat brush in all four app rotations.
     */
    final boolean landscapeTilt;
    /** Narrow controls: the header shows a Pages button in place of the page row. */
    final boolean compactControls;
    /** How close to the screen edge a finger must reach to count as an edge swipe, tuned by hand per digitizer. */
    final int edgeSwipeSlopDp;
    /** A smaller tablet whose layout this one can preview at physical size, or null. */
    final Device simulates;
    /**
     * The driver plane for fast binary control pixels (mode 9/flags 1) on firmware before and
     * from {@link #PEN_MAIN_PLANE_SINCE}, or -1 when controls stay on the gray path (mode 7).
     */
    private final int olderFastBinaryPlane, newerFastBinaryPlane;
    /**
     * The first build whose libeinkutils.so sends the firmware's own pen requests (mode 9) to
     * the main plane rather than the plane after it. Its gray frames also queue behind the pen
     * on the Manta, as they always did on the Nomad. Found on 3.30.45 (2026-10-12).
     */
    private static final long PEN_MAIN_PLANE_SINCE = 2610091001L;
    /**
     * Firmware builds ({@link #firmware()}) the device checks passed on with this tablet. The
     * firmware drives each model's panel differently, so a build tested on one model says
     * nothing about another. Empty for tablets MonoPaint has no profile for.
     */
    private final String[] testedFirmware;

    private Device(String name, int panelWidth, int panelHeight, boolean landscapeTilt,
                   boolean compactControls, int edgeSwipeSlopDp, Device simulates,
                   int olderFastBinaryPlane, int newerFastBinaryPlane, String... testedFirmware) {
        this.name = name; this.panelWidth = panelWidth; this.panelHeight = panelHeight;
        this.landscapeTilt = landscapeTilt; this.compactControls = compactControls;
        this.edgeSwipeSlopDp = edgeSwipeSlopDp; this.simulates = simulates;
        this.olderFastBinaryPlane = olderFastBinaryPlane; this.newerFastBinaryPlane = newerFastBinaryPlane;
        this.testedFirmware = testedFirmware;
    }

    /** The installed firmware build, e.g. "Chauvet.E103.2606141001.2389_release". */
    static String firmware() { return Build.DISPLAY; }
    /** Fast e-ink is normally used on any firmware; untested means it is a guess the user can override. */
    boolean firmwareTested() { return java.util.Arrays.asList(testedFirmware).contains(firmware()); }

    /**
     * The driver mode for a pen stroke's exact black/white pixels on the main plane, faster than the gray mode 7, on the
     * installed firmware, or -1 for none known. The Manta before {@link #PEN_MAIN_PLANE_SINCE}: mode 5 (flags 0) runs
     * the panel 8 frames, about 145 ms, where modes 7 and 9 run 18-19, about 270 ms (counted from the e-ink
     * controller's frame interrupts, 2026-10-15, Inkwash's StrokePanel). Unmeasured elsewhere.
     */
    int fastStrokeMode() {
        return this == MANTA && buildStamp(firmware()) > 0 && buildStamp(firmware()) < PEN_MAIN_PLANE_SINCE ? 5 : -1;
    }
    /** The driver plane fast binary controls use on the installed firmware, or -1 for none. */
    int fastBinaryPlane() {
        return buildStamp(firmware()) >= PEN_MAIN_PLANE_SINCE ? newerFastBinaryPlane : olderFastBinaryPlane;
    }
    /** The yyMMddHHmm stamp in a build like "Chauvet.E103.2606141001.2389_release"; 0 when absent. */
    static long buildStamp(String firmware) {
        java.util.regex.Matcher stamp = java.util.regex.Pattern.compile("\\.(\\d{10})\\.").matcher(firmware == null ? "" : firmware);
        return stamp.find() ? Long.parseLong(stamp.group(1)) : 0;
    }

    /** A Supernote of any model. MonoPaint also installs and runs on other Android tablets. */
    static boolean supernote() { return "Supernote".equalsIgnoreCase(Build.MANUFACTURER); }

    private static Device current;
    /** This tablet's profile. */
    static synchronized Device current(Context context) {
        if (current != null) return current;
        current = OTHER;
        if (!supernote()) return current;
        // The Manta also reports "Supernote Nomad" as its model. Match the physical panel,
        // independent of app rotation, window size, density, or the Nomad simulation.
        Display.Mode mode = context.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY).getMode();
        int width = Math.min(mode.getPhysicalWidth(), mode.getPhysicalHeight());
        int height = Math.max(mode.getPhysicalWidth(), mode.getPhysicalHeight());
        for (Device device : SUPERNOTES) if (device.panelWidth == width && device.panelHeight == height) current = device;
        return current;
    }
}
