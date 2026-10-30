#!/usr/bin/env python3
"""res/font/ukulele.ttf and ukulele_bold.ttf, the app's face (2026-10-26), from the owner's Ukulele (dafont; Ukulele
font by Elling Lien, CC BY 3.0), worked over for the app's UI.

  python3 scripts/ukulele_font.py path/to/ukulele.ttf   (needs fonttools)

- 1.12x larger (its unicase letters stand 0.5 em, iA Writer Duo's capitals 0.7 em), with 0.07 em more room between
  letters (the owner: "a little more horizontal spacing", then "a little more"). A name runs up to about a tenth
  wider than LabText's duospace count of it; the tool picker's cells allow for it (DrawLayout.PICKER_SPACING).
- Ascent and descent cut to its letters (they have no descenders), so a label sits centred in its box like Duo's.
- Hinting and device tables dropped (they no longer match the new size).
- Missing : ; , · ... drawn from its own period and apostrophe; the rest it lacks (% & * = etc.) fall back per glyph
  to iA Writer Duo (Theme.face).
- Bold (700): each outline drawn four times, BOLD units apart across and up, so its strokes are about 40% thicker
  (Android's own fake bold barely showed on its thin strokes); its advances are the regular's, as Duo's were.
"""
import sys
from fontTools.ttLib import TTFont
from fontTools.ttLib.tables._g_l_y_f import Glyph, GlyphComponent, GlyphCoordinates
from fontTools.ttLib.tables.ttProgram import Program

SCALE, TRACKING, ASCENT, DESCENT, BOLD = 1.12, .07, 1500, 450, 45
src = sys.argv[1]
fonts = "app/src/main/res/font/"


def build(out, bold):
    f = TTFont(src)
    glyf, hmtx, order = f["glyf"], f["hmtx"], f.getGlyphOrder()
    for tag in ("hdmx", "VDMX", "LTSH", "fpgm", "prep", "cvt ", "gasp"):
        if tag in f: del f[tag]
    track = round(TRACKING * f["head"].unitsPerEm / SCALE)
    for name in order:
        g = glyf[name]
        if g.numberOfContours > 0:
            g.program = Program(); g.program.fromBytecode(b"")
            if bold:
                n, coords = len(g.coordinates), list(g.coordinates)
                coords = [(x - BOLD // 2, y) for x, y in coords]
                for dx, dy in ((BOLD, 0), (0, BOLD), (BOLD, BOLD)): coords += [(x + dx, y + dy) for x, y in coords[:n]]
                g.endPtsOfContours = [e + k * n for k in range(4) for e in g.endPtsOfContours]
                g.flags = g.flags * 4; g.numberOfContours *= 4
                g.coordinates = GlyphCoordinates(coords)
            g.coordinates.translate((track // 2, 0))
        advance, lsb = hmtx.metrics[name]
        hmtx.metrics[name] = (advance + track, lsb)
    f["maxp"].maxSizeOfInstructions = 0

    def composite(name, code, advance, parts):
        g = Glyph(); g.numberOfContours = -1; g.components = []
        for base, x, y in parts:
            c = GlyphComponent(); c.glyphName, c.x, c.y, c.flags = base, x, y, 0x4  # ROUND_XY_TO_GRID
            g.components.append(c)
        glyf[name] = g  # also adds it to the glyph order
        hmtx.metrics[name] = (advance + track, 0)
        for table in f["cmap"].tables:
            if table.isUnicode(): table.cmap[code] = name

    period, quote = "period", "quotesingle"
    composite("comma", ord(","), 269, [(quote, 40, -900)])
    composite("colon", ord(":"), 269, [(period, 0, 0), (period, 0, 560)])
    composite("semicolon", ord(";"), 269, [(quote, 40, -900), (period, 0, 560)])
    composite("periodcentered", 0xB7, 269, [(period, 0, 420)])
    composite("ellipsis", 0x2026, 760, [(period, 0, 0), (period, 240, 0), (period, 480, 0)])
    f.setGlyphOrder(glyf.glyphOrder)
    f["post"].formatType = 3.0
    for name in glyf.glyphOrder:  # left side bearings to the moved outlines
        g = glyf[name]; g.recalcBounds(glyf)
        hmtx.metrics[name] = (hmtx.metrics[name][0], getattr(g, "xMin", 0))

    f["head"].unitsPerEm = round(f["head"].unitsPerEm / SCALE)
    hhea, os2 = f["hhea"], f["OS/2"]
    hhea.ascent, hhea.descent, hhea.lineGap = ASCENT, -DESCENT, 0
    os2.sTypoAscender, os2.sTypoDescender, os2.sTypoLineGap = ASCENT, -DESCENT, 0
    os2.usWinAscent, os2.usWinDescent = ASCENT, DESCENT
    if bold: os2.usWeightClass = 700
    f.save(out)
    assert all(ord(c) in TTFont(out).getBestCmap() for c in ":;,·…"), "composites missing"
    print(out)


build(fonts + "ukulele.ttf", False)
build(fonts + "ukulele_bold.ttf", True)
