import java.awt.Font;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes app/src/main/res/drawable/splash_name.xml: the app's name (AppName.NAME) in iA Writer Duo Bold at the title
 * size (48 sp), as a vector, for the loading screen (drawable/splash.xml), which shows before any code runs and so can't
 * set type. Run again after a rename (docs/RENAMING.md), from the repository's root:
 * {@code java -Djava.awt.headless=true scripts/java/SplashName.java}.
 */
public final class SplashName {
    public static void main(String[] args) throws Exception {
        String source = new String(Files.readAllBytes(new File("app/src/main/java/io/github/mpdairy/monopaint/AppName.java").toPath()), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("(?m)^\\s*static final String NAME = \"([^\"]+)\";").matcher(source);
        if (!m.find()) throw new IllegalStateException("AppName.java: no NAME line");
        String name = m.group(1);
        // 1000 units an em, the TTF's own; the loading screen name is 48 sp, so a unit is 0.048 dp.
        Font font = Font.createFont(Font.TRUETYPE_FONT, new File("app/src/main/res/font/ia_writer_duo_bold.ttf")).deriveFont(1000f);
        Shape outline = font.createGlyphVector(new FontRenderContext(null, true, true), name).getOutline();
        Rectangle2D box = outline.getBounds2D();
        double x0 = Math.floor(box.getMinX()), y0 = Math.floor(box.getMinY());
        double w = Math.ceil(box.getMaxX()) - x0, h = Math.ceil(box.getMaxY()) - y0;
        StringBuilder path = new StringBuilder();
        double[] c = new double[6];
        for (PathIterator it = outline.getPathIterator(null); !it.isDone(); it.next()) {
            int type = it.currentSegment(c);
            String op = type == PathIterator.SEG_MOVETO ? "M" : type == PathIterator.SEG_LINETO ? "L" : type == PathIterator.SEG_QUADTO ? "Q" : type == PathIterator.SEG_CUBICTO ? "C" : "Z";
            path.append(op);
            int points = type == PathIterator.SEG_QUADTO ? 2 : type == PathIterator.SEG_CUBICTO ? 3 : type == PathIterator.SEG_CLOSE ? 0 : 1;
            for (int i = 0; i < points; i++) path.append(num(c[2 * i] - x0)).append(',').append(num(c[2 * i + 1] - y0)).append(i < points - 1 ? " " : "");
        }
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<!-- \"" + name + "\" (AppName.NAME) in iA Writer Duo Bold at 48 sp, its ink's bounds: the loading screen's name\n"
                + "     (drawable/splash.xml). Made by scripts/java/SplashName.java; made again after a rename. -->\n"
                + "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"" + num(w * .048) + "dp\" android:height=\""
                + num(h * .048) + "dp\" android:viewportWidth=\"" + num(w) + "\" android:viewportHeight=\"" + num(h) + "\">\n"
                + "    <path android:fillColor=\"#000000\" android:pathData=\"" + path + "\"/>\n"
                + "</vector>\n";
        Files.write(new File("app/src/main/res/drawable/splash_name.xml").toPath(), xml.getBytes(StandardCharsets.UTF_8));
        System.out.println("splash_name.xml: \"" + name + "\", " + num(w * .048) + " x " + num(h * .048) + " dp");
    }
    private static String num(double v) {
        String s = String.format(Locale.ROOT, "%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}
