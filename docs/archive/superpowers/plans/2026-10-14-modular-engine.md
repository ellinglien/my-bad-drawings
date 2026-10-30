# Modular Engine (Stage 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Six-part brushes (Tip, Medium, Carry, Paper, Life, Hand) with the web lab's `M` share codes draw live in the app; Randomize, the style chips, the slot dice and variations make them; the current lab screen edits them; every existing brush draws exactly as it does now.

**Architecture:** A second, parallel engine for six-part brushes, ported from the Modular Brush Lab prototype; the old engine (built-ins and `I`/`P` Wild brushes) is not touched. The model (`BrushPart`, `PartDial`, `PartChoice`, `ModularBrush`, `ModularStyle`, `ModularCode`, `ModularVariations`, `ModularWords`) and the engine (`ResolvedBrush`, the stampers, `BodySettle`, `ModularCompose`, `ModularStroke`, `DryingWater`) are pure Java checked on the desktop. `Brush` gains a third kind of value; `InkWash.stroke` sends six-part brushes to `ModularStroke.start`; `BrushLab`, `LabSettings` and `ToolSlots` make and edit them. Drying's after-lift settling ticks in `DrawingPad` and the lab's test pad through `DryingTicker`.

**Tech Stack:** Java 8 Android app (no Kotlin, no XML layouts), desktop checks run by `bash scripts/test_inkwash.sh` (plain `main` methods with a `check(boolean, String)` helper, no JUnit).

**Source of truth:** `docs/archive/superpowers/specs/2026-10-14-modular-engine-design.md`. **Reference implementation:** the prototype's sources, extracted in Task 1 to `docs/brush-lab/prototypes/modular-lab-src/` (`parts.js`, `engine.js`, `stroke.js`, `port.js`, `oldmodel.js`, `checks.js`, `ui.js`). Every "port X (file a–b)" below means those files and line numbers. Port them; don't redesign them. Keep each function's arithmetic, constants and order of random draws; change only what the "App differences" notes say.

**Conventions (from CLAUDE.md, AGENTS.md and earlier plans):**
- Read `PHILOSOPHY.md` and the spec first. Pure model classes never import `android.*`; Android classes only show and call them.
- Run checks with Android Studio's JDK:
  `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" PATH="/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin:$PATH"`
- Desktop checks: `bash scripts/test_inkwash.sh` (29 PASS lines before Task 1) and `bash scripts/test_document.sh` (64 before Task 1; it runs `test_inkwash.sh` at its end, so it grows with it). Every new pure source goes into `sources=(...)` and every new check class into `checks=(...)` of `scripts/test_inkwash.sh` in the task that adds it. A check prints exactly one `PASS:` line. Count PASS lines with `bash scripts/test_inkwash.sh | grep -c '^PASS'`.
- **Never re-record `WashGoldenChecks` or `LegacyGoldenChecks` checksums.** `LegacyGoldenChecks` is recorded once, in Task 1, before anything else changes.
- Every existing check keeps passing after every task. A task that changes behaviour on purpose rewrites exactly the assertions it names.
- Build check: `./gradlew :app:assembleRelease` (same JAVA_HOME; if Gradle says "SDK location not found", prefix `ANDROID_HOME=$HOME/Android/Sdk`). Do **not** install on the device or run `adb`.
- AGENTS.md: every user-facing change gets a plain-language line under **Unreleased** in `CHANGELOG.md`, in the same commit (Tasks 13 and 15 give the lines).
- Never `git add -A` or `git commit -a`; add files by name. Leave the untracked `docs/monopaint-ideas-draft.md` alone.
- Commit messages end with:
  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01E7BSCi595U3kgRpEiXR6YV
  ```
- Java 8 only in `app/src/main` (no `var`, `List.of`, `String.repeat`, switch expressions). Format numbers with `Locale.ROOT`.
- All paths below are under `app/src/main/java/io/github/mpdairy/monopaint/` (main) or `app/src/test/java/io/github/mpdairy/monopaint/` (checks) unless given in full.

**Porting rules (every engine task):**
- The prototype's `rng(seed)` is `WildBrush.Random` (mulberry32, the same generator). `rng(x >>> 0)` → `new WildBrush.Random(x & 0xffffffffL)`; `Math.imul(a, b)` → `a * b` on ints; `v >>> 0` as a number → `v & 0xffffffffL`; `x | 0` → `(int)x` (both truncate toward zero); `Math.round` → `Math.round` (both round .5 up).
- The prototype's `Float32Array`s are float arrays: compute in double, store float, so stored values match the prototype's to the bit.
- A rectangle is `int[]{left, top, right, bottom}`, inclusive, as the prototype's; empty when `right < left || bottom < top` (`StrokeMath.empty`), and the prototype's `[w, h, 0, 0]` / `[w, h, -1, -1]` starts become `StrokeMath.emptyRect(width, height)`.
- Page buffers (`surf.L`, `surf.W`, `surf.D`, `surf.rawL`, `surf.rawW`, `surf.wetD`, `surf.relief`, `surf.lineSave`, `surf.bodySave`, `surf.scratch`) are `FloatTiles`. The prototype's global `WET` / `WET_D` (the Fast-wet distance map) becomes an explicit nullable `FloatTiles wetD` parameter.
- Noise: `surf.fine[i]`, `surf.mid[i]` → `paper.fine(x, y)`, `paper.mid(x, y)`; `surf.low[i]`, `surf.blob[i]` → `paper.low(x, y)`, `paper.blob(x, y)` (`StrokePaper`); the prototype indexes with `i = y * w + x`, so recover x and y. Where it reads at a scaled index (`li` in `settleBody`, `(x / big) | 0` in `stampLine`), read at the scaled coordinates.
- `scaleAware` is always true; `b.sizes` is always undefined (false). Drop both.

**Decisions this plan makes where the spec left room:**
1. `ModularStyle`'s name table lands with the part model (Task 2), so `ModularBrush` can hold a style before rolls exist (Task 3).
2. Fixture brushes are the prototype's own codes (generated from `modular-lab-src` by Node during planning); `ModularCodeChecks.FIXTURES` holds them and later checks decode them, so every check draws the same brushes the prototype drew.
3. Until Task 11, Life Fast-wet draws as None; until Task 15, Drying settles at lift only. No user path makes a six-part brush before Task 13.
4. `ModularStroke` has a package-private counter `retapers` (how often the Fast-wet line window re-tapered) for the 50 ms check.
5. Built-in Ink and Fude can't run on the desktop (`InkStroke` uses Android's `PressureStroke`), so `LegacyGoldenChecks` covers Wash, Watercolor and the five Wild codes, through the same constructors `InkWash.stroke` uses.

---

## File structure

| File | Task | Responsibility |
|---|---|---|
| `docs/brush-lab/prototypes/modular-lab-src/*.js` (new) | 1 | The prototype's sources with their own line numbers, extracted from the committed page. |
| `LegacyGoldenChecks.java` (new check) | 1 | Old brushes draw byte-identically. |
| `BrushPart.java`, `PartDial.java`, `PartChoice.java`, `ModularFeatures.java`, `ModularBrush.java` (new) | 2 | The part model. |
| `ModularStyle.java` (new; names in 2, rolls in 3), `ModularLocks.java` (new) | 2–3 | Styles, rolls, locks. |
| `ModularCode.java` (new), `BrushCode.java` (modify: `written` package-visible) | 4 | M share codes. |
| `ModularVariations.java`, `ModularWords.java` (new) | 5 | Variations; words, captions, "This brush". |
| `ResolvedBrush.java` (new) | 6 | Parts → stamper parameters. |
| `FloatTiles.java`, `StrokePoints.java`, `StrokeMath.java`, `StrokePaper.java`, `StickGrain.java` (new), `BrushDirection.java` (modify: `azimuth`) | 7 | Engine foundations. |
| `LineStamper.java`, `Placement.java` (new) | 8 | The line and placed lines. |
| `BodyStamper.java`, `HakeRibbon.java`, `BodySettle.java` (new) | 9 | The body, the hake, the settle and the blend. |
| `ModularCompose.java`, `ModularStroke.java` (new), `ToneDocument.java` (modify: `coverTones`) | 10 | The stroke on the page: None and Drying. |
| `ModularStroke.java` (modify) | 11 | Fast-wet windows; the timing check. |
| `ModularSizes.java` (new); `Brush`, `InkWash`, `WildSample`, `InkWashBar`, `BrushCaptions`, `BrushStyle`, `ModularStroke` (modify) | 12 | Six-part brushes in the app. |
| `ToolSlots`, `BrushLab`, `BrushLabActivity` (modify) | 13 | Randomize, styles, dice and variations make six-part brushes. |
| `LabSettings`, `BrushLabActivity` (modify) | 14 | The lab's column for six-part brushes. |
| `DryingWater.java` (new), `DryingTicker.java` (new, Android); `WatercolorSession`, `ModularStroke`, `InkWash`, `DrawingPad`, `BrushTestPad`, `BrushLabActivity` (modify) | 15 | Drying keeps settling after lift. |
| spec (modify) | 16 | As-built notes. |

## Tasks

1. Prototype sources and the golden guard for old brushes
2. The part model
3. Styles, rolls and locks
4. M share codes
5. Variations and words
6. Resolve
7. Engine foundations
8. Line stampers
9. Body stampers, the hake and the settle
10. The stroke on the page: compose, None and Drying
11. Fast-wet, and the timing check
12. Six-part brushes in the app
13. Randomize, styles, dice and variations make six-part brushes
14. The lab's column for six-part brushes
15. Drying keeps settling after lift
16. Full verification and as-built notes

---

### Task 1: Prototype sources and the golden guard for old brushes

**Files:**
- Create: `docs/brush-lab/prototypes/modular-lab-src/{port,oldmodel,engine,stroke,parts,checks,ui}.js`
- Create: `app/src/test/java/io/github/mpdairy/monopaint/LegacyGoldenChecks.java`
- Modify: `scripts/test_inkwash.sh`

- [ ] **Step 1: Extract the prototype's sources from the committed page**

The page concatenates them unchanged (`build.js` joins them with `\n;\n`):

```bash
P=docs/brush-lab/prototypes; S=$P/modular-lab-src; mkdir -p $S
sed -n 270,657p $P/modular-lab.html > $S/port.js
sed -n 660,994p $P/modular-lab.html > $S/oldmodel.js
sed -n 997,1771p $P/modular-lab.html > $S/engine.js
sed -n 1774,2481p $P/modular-lab.html > $S/stroke.js
sed -n 2484,3204p $P/modular-lab.html > $S/parts.js
sed -n 3207,3278p $P/modular-lab.html > $S/checks.js
sed -n 3281,4115p $P/modular-lab.html > $S/ui.js
wc -l $S/*.js
sed -n 174p $S/parts.js; sed -n 113p $S/engine.js; sed -n 463p $S/stroke.js; sed -n 3p $S/port.js
```

Expected: 72 checks.js, 775 engine.js, 335 oldmodel.js, 721 parts.js, 388 port.js, 708 stroke.js, 835 ui.js; then the lines `function resolve(B, size, shade) {`, `function stampLine(surf, b, pts, size, final, from, clip, meta, win) {`, `class Stroke {`, `function rng(seed) {`. (`oldmodel.js` has the name lists inlined where the source had `@@ADJECTIVES@@`; nothing else differs.)

- [ ] **Step 2: Write the golden check**

`LegacyGoldenChecks.java`:

```java
package io.github.mpdairy.monopaint;

import java.util.zip.CRC32;

/**
 * Old brushes must draw exactly as they did before the modular engine: the five share codes BrushCodeChecks pins,
 * at every size and two shades, the Wash and Watercolor built-ins, and their loop samples, against checksums
 * recorded before Stage 1 changed anything (docs/archive/superpowers/specs/2026-10-14-modular-engine-design.md). Never
 * re-record them. Ink and Fude need Android (PressureStroke), so they aren't drawn here; Stage 1 doesn't touch them.
 */
public final class LegacyGoldenChecks {
    static final String[] CODES = {"dapper-fig.I610gavij3f7q16o0000006m00.e1", "bobson-dugnutt.I610zkyum2fbzsge00ej5k00e7.1",
            "x.I613zkyum2fbzsge00ej5k00e7.1", "mellow-plum.P600xjf400c4jzxh00aqhu.hd", "bouncy-moose.P610hh46000000000000wp.25"};
    static final String EXPECTED = "RECORD-ME";

    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        StringBuilder sums = new StringBuilder();
        for (String code : CODES) {
            Brush brush = Brush.of(BrushCode.decode(code));
            for (InkWashSizes.Preset preset : InkWashSizes.Preset.values()) sums.append(draw(brush, preset, InkWashShades.Shade.DARK)).append(' ');
            sums.append(draw(brush, InkWashSizes.Preset.MEDIUM, InkWashShades.Shade.LIGHT)).append(' ').append(sample(brush)).append(' ');
        }
        for (BuiltinBrush builtin : new BuiltinBrush[]{BuiltinBrush.WASH, BuiltinBrush.WATERCOLOR}) {
            Brush brush = Brush.of(builtin);
            sums.append(draw(brush, InkWashSizes.Preset.MEDIUM, InkWashShades.Shade.DARK)).append(' ').append(sample(brush)).append(' ');
        }
        String got = sums.toString().trim();
        if (args.length > 0) { System.out.println(got); return; }
        check(got.equals(EXPECTED), "Old brushes draw exactly as before Stage 1: " + got + " vs " + EXPECTED);
        System.out.println("PASS: old brushes (five I/P codes at every size and two shades, Wash and Watercolor) and their samples give byte-identical pages");
    }

    /** InkWash.stroke's dispatch for these brushes (InkWash itself needs Android), on the blank pair's ink or wash layer. */
    static DrawingStroke start(ToneDocument doc, Brush brush, InkWashSizes.Preset preset, InkWashShades.Shade shade) {
        WatercolorSession session = new WatercolorSession();
        if (brush.inks()) { doc.selectLayer(InkWashLayers.INK); return new WildInkStroke(doc, brush.wild, preset, InkWashShades.ink(shade)); }
        doc.selectLayer(InkWashLayers.WASH);
        if (brush.builtin == BuiltinBrush.WASH) return WashStroke.flat(doc, preset, InkWashShades.wash(shade), session, () -> 0);
        WashLook look = brush.wild == null ? WashLook.WATERCOLOR : WashLook.of(brush.wild);
        return WashStroke.watercolor(doc, preset, InkWashShades.watercolor(shade), look, session, () -> 0);
    }
    /** One scripted stroke: a wave, pressure rising and falling, the pen leaning and turning, 5 ms a sample. */
    static String draw(Brush brush, InkWashSizes.Preset preset, InkWashShades.Shade shade) {
        ToneDocument doc = InkWashLayers.blank(480, 360);
        DrawingStroke stroke = start(doc, brush, preset, shade);
        for (int i = 0; i <= 120; i++) {
            double t = i / 120.0;
            stroke.sample((float)(40 + 400 * t), (float)(180 + 100 * Math.sin(2 * Math.PI * 1.25 * t)),
                    ToolSettings.pressure((float)(.15 + .75 * Math.pow(Math.sin(Math.PI * t), .7))), 10 * (i % 7) - 30, 15, i * 5L);
        }
        stroke.finish();
        return crc(doc);
    }
    static String sample(Brush brush) {
        InkWashSizes.Preset preset = WildSample.preset(brush.inks(), 96);
        int[] pixels = WildSample.pixels(brush, InkWashShades.Shade.DARK, preset, WildSample.Shape.LOOP, 120, 96,
                page -> start(page, brush, preset, InkWashShades.Shade.DARK));
        CRC32 crc = new CRC32();
        for (int p : pixels) { crc.update(p); crc.update(p >>> 8); crc.update(p >>> 16); }
        return Long.toHexString(crc.getValue());
    }
    static String crc(ToneDocument doc) {
        CRC32 crc = new CRC32();
        for (ToneDocument.Layer layer : doc.layerSnapshot().layers) { crc.update(layer.tones); crc.update(layer.alpha); }
        return Long.toHexString(crc.getValue());
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
```

- [ ] **Step 3: Add it to the script and record**

In `scripts/test_inkwash.sh` append `LegacyGoldenChecks` to the end of `checks=(...)` (all its sources are already in `sources=(...)`). Then record:

```bash
classes=$(mktemp -d); m=app/src/main/java/io/github/mpdairy/monopaint; t=app/src/test/java/io/github/mpdairy/monopaint
javac -d $classes $(sed -n 's/^sources=(\(.*\))/\1/p' scripts/test_inkwash.sh | tr ' ' '\n' | sed "s|^|$m/|;s|$|.java|") $t/LegacyGoldenChecks.java
java -cp $classes io.github.mpdairy.monopaint.LegacyGoldenChecks record
```

Expected: one line of 29 hex checksums (25 for the codes, 4 for the built-ins). Paste it as `EXPECTED`. Run the same `java` line without `record` twice: `PASS: old brushes …` both times (it is deterministic).

- [ ] **Step 4: Run all checks**

Run: `bash scripts/test_inkwash.sh | grep -c '^PASS'` → `30`; `bash scripts/test_document.sh | grep -c '^PASS'` → `65`.

- [ ] **Step 5: Commit**

```bash
git add docs/brush-lab/prototypes/modular-lab-src scripts/test_inkwash.sh app/src/test/java/io/github/mpdairy/monopaint/LegacyGoldenChecks.java
git commit -m "Modular engine: prototype sources and a golden guard for old brushes"
```
(with the two attribution lines).

---

### Task 2: The part model

**Files:**
- Create: `BrushPart.java`, `PartDial.java`, `PartChoice.java`, `ModularFeatures.java`, `ModularBrush.java`, `ModularStyle.java` (names only)
- Create: `PartModelChecks.java`
- Modify: `scripts/test_inkwash.sh`

**Port:** parts.js 6–168 and 386–390 (`PARTS`, `ROLE`, `GRADES`, `DIALS`, `HAND_CELLS`, `HAND_DIALS`, `CHOICES`, `choiceOf`, `rangeOf`, `dialDefault`, `fmtDial`, `freshPart`, `KEEPS`, `switchChoice`, `normPart`, `normBrush`, `handCell`, `matchHand`). The spec's "Data model" lists every table in code order.

- [ ] **Step 1: Write the check**

`PartModelChecks.java`:

```java
package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PartModelChecks {
    public static void main(String[] args) {
        tables(); fresh(); normalize(); swaps(); hand(); shown(); styles();
        System.out.println("PASS: six parts with the prototype's choices and dials in code order; fresh parts take their choice's defaults, normalizing clamps to the choice's range, swaps keep what means the same, hands are named for the preset they match, deferred choices aren't offered");
    }
    static String keys(BrushPart part) { List<String> k = new ArrayList<>(); for (PartChoice c : part.choices()) k.add(c.key); return String.join(",", k); }
    static String dials(PartChoice c) { List<String> k = new ArrayList<>(); for (PartDial d : c.dials) k.add(d.key); return String.join(" ", k); }
    static void tables() {
        check(Arrays.toString(BrushPart.values()).equals("[TIP, MEDIUM, CARRY, PAPER, LIFE, HAND]"), "Six parts in code order");
        check(keys(BrushPart.TIP).equals("round,chisel,flat,hake,split,multi,bristles"), "Tip choices: " + keys(BrushPart.TIP));
        check(keys(BrushPart.MEDIUM).equals("ink,wash,cover,charcoal,graphite"), "Medium choices");
        check(keys(BrushPart.CARRY).equals("off,lift,drag,smudge"), "Carry choices");
        check(keys(BrushPart.PAPER).equals("plain,cold press,laid,felt,crumpled,canvas"), "Paper choices");
        check(keys(BrushPart.LIFE).equals("none,wet,dry"), "Life choices");
        check(keys(BrushPart.HAND).equals("steady,wider,darker,both,brush,side,nib,quick,custom"), "Hand choices");
        check(dials(PartChoice.ROUND).equals("weight taperIn taperOut dry wobble soft"), "Round's dials");
        check(dials(PartChoice.CHISEL).equals("aspect angle weight taperIn taperOut dry wobble soft") && dials(PartChoice.FLAT_NIB).equals(dials(PartChoice.CHISEL)), "Chisel's and flat nib's dials");
        check(dials(PartChoice.HAKE).equals("weight headTurn angle dry soft"), "Hake's dials");
        check(dials(PartChoice.SPLIT_NIB).equals("gap aspect angle weight taperIn taperOut dry"), "Split nib's dials");
        check(dials(PartChoice.MULTI_LINE).equals("count reach weight taperIn taperOut dry wobble"), "Multi-line's dials");
        check(dials(PartChoice.BRISTLES).equals("hairs splay cohesion dryness weight"), "Bristles' dials");
        check(dials(PartChoice.INK).equals("wet line pool splatter bleed bloom lines offset chaos"), "Ink's dials");
        check(dials(PartChoice.WASH).equals("wet pool bleed bloom backrun line"), "Wash's dials");
        check(dials(PartChoice.COVER).equals("cover impasto") && dials(PartChoice.CHARCOAL).equals("spread hold") && dials(PartChoice.GRAPHITE).equals("grade spread"), "Cover's, charcoal's, graphite's dials");
        check(dials(PartChoice.OFF).isEmpty() && dials(PartChoice.LIFT).equals("pick lay") && dials(PartChoice.SMUDGE).equals("pick lay load"), "Carry's dials");
        check(dials(PartChoice.NONE).isEmpty() && dials(PartChoice.FAST_WET).equals("window") && dials(PartChoice.DRYING).equals("drying settle takes plume"), "Life's dials");
        for (PartChoice c : BrushPart.HAND.choices()) check(dials(c).equals("ps pd pw ts td tw ss sd sw gain dir"), "Every hand has the nine cells, gain and dir");
        for (BrushPart part : BrushPart.values()) for (int i = 0; i < part.choices().size(); i++)
            check(part.choices().get(i).index() == i && PartChoice.of(part, i) == part.choices().get(i) && part.choices().get(i).part == part, "Choice indexes: " + part + " " + i);
        check(PartChoice.of(BrushPart.TIP, 7) == null && PartChoice.of(BrushPart.TIP, -1) == null, "No choice past the list");
        for (int i = 0; i < 6; i++) check(BrushPart.PAPER.choices().get(i).texture() == BrushTexture.values()[i], "A paper is its texture");
        check(Arrays.equals(PartChoice.CHISEL.range(PartDial.ASPECT), new double[]{.3, .55}) && Arrays.equals(PartChoice.FLAT_NIB.range(PartDial.ASPECT), new double[]{.14, .28})
                && Arrays.equals(PartChoice.ROUND.range(PartDial.WEIGHT), new double[]{.5, 1.5}), "Ranges: a choice's own, else the dial's");
    }
    static void fresh() {
        ModularBrush.Part hake = new ModularBrush.Part(PartChoice.HAKE);
        check(hake.get(PartDial.ANGLE) == 75 && hake.get(PartDial.DRY) == .35 && hake.get(PartDial.SOFT) == .2 && hake.get(PartDial.WEIGHT) == 1 && hake.get(PartDial.HEAD_TURN) == 0, "A fresh hake");
        check(!hake.has(PartDial.TAPER_IN) && hake.get(PartDial.TAPER_IN) == 0 && hake.get(PartDial.TAPER_IN, -1) == -1, "An absent dial reads 0, or the fallback asked for");
        check(new ModularBrush.Part(PartChoice.CHISEL).get(PartDial.ASPECT) == .42 && new ModularBrush.Part(PartChoice.FLAT_NIB).get(PartDial.ASPECT) == .2
                && new ModularBrush.Part(PartChoice.WASH).get(PartDial.WET) == .85 && new ModularBrush.Part(PartChoice.WASH).get(PartDial.LINE) == 0
                && new ModularBrush.Part(PartChoice.PLAIN).get(PartDial.TOOTH) == 0 && new ModularBrush.Part(PartChoice.LAID).get(PartDial.TOOTH) == .5
                && new ModularBrush.Part(PartChoice.GRAPHITE).get(PartDial.SPREAD) == 0 && new ModularBrush.Part(PartChoice.DRAG).get(PartDial.LOAD) == .85, "Choice defaults");
        ModularBrush.Part nib = new ModularBrush.Part(PartChoice.TILT_NIB);
        check(nib.get(PartDial.PS) == .4 && nib.get(PartDial.TW) == .9 && nib.get(PartDial.PW) == .4 && nib.get(PartDial.TS) == 0 && nib.get(PartDial.GAIN) == 1 && nib.get(PartDial.DIR) == 0, "A fresh hand preset sets its cells");
        ModularBrush b = new ModularBrush("x", 5, ModularStyle.named("Fineliner"));
        for (BrushPart part : BrushPart.values()) check(b.choice(part) == part.choices().get(0), "A new brush holds each part's first choice");
        check(b.seed == 5 && b.name.equals("x") && b.style.name.equals("Fineliner"), "and its name, seed and style");
    }
    static void normalize() {
        ModularBrush.Part chisel = new ModularBrush.Part(PartChoice.CHISEL);
        chisel.set(PartDial.ASPECT, .2); chisel.set(PartDial.ANGLE, 99); chisel.set(PartDial.TAPER_IN, -1); chisel.set(PartDial.SOFT, Double.NaN);
        chisel.normalize();
        check(chisel.get(PartDial.ASPECT) == .3 && chisel.get(PartDial.ANGLE) == 70 && chisel.get(PartDial.TAPER_IN) == 0 && chisel.get(PartDial.SOFT) == .3, "Normalizing clamps to the choice's range and fills what isn't a number");
        ModularBrush.Part multi = new ModularBrush.Part(PartChoice.MULTI_LINE); multi.set(PartDial.COUNT, 3.6); multi.normalize();
        check(multi.get(PartDial.COUNT) == 4, "An integer dial rounds");
        ModularBrush.Part hand = new ModularBrush.Part(PartChoice.CUSTOM); hand.set(PartDial.DIR, 1.7); hand.normalize();
        check(hand.get(PartDial.DIR) == 1, "A choice dial rounds into its options");
    }
    static void swaps() {
        ModularBrush.Part chisel = new ModularBrush.Part(PartChoice.CHISEL);
        chisel.set(PartDial.TAPER_IN, .5); chisel.set(PartDial.ANGLE, 30); chisel.set(PartDial.ASPECT, .5);
        ModularBrush.Part split = ModularBrush.switchTo(chisel, PartChoice.SPLIT_NIB);
        check(split.choice == PartChoice.SPLIT_NIB && split.get(PartDial.TAPER_IN) == .5 && split.get(PartDial.ANGLE) == 30 && split.get(PartDial.ASPECT) == .5 && split.get(PartDial.GAP) == .55, "A swap keeps the tip's shared dials and an aspect inside the new range");
        check(ModularBrush.switchTo(chisel, PartChoice.FLAT_NIB).get(PartDial.ASPECT) == .2, "An aspect outside the new range starts at its default");
        check(ModularBrush.switchTo(new ModularBrush.Part(PartChoice.PLAIN), PartChoice.COLD_PRESS).get(PartDial.TOOTH) == .5, "Plain paper's tooth isn't kept");
        ModularBrush.Part cold = new ModularBrush.Part(PartChoice.COLD_PRESS); cold.set(PartDial.TOOTH, .8);
        check(ModularBrush.switchTo(cold, PartChoice.LAID).get(PartDial.TOOTH) == .8, "Another paper's tooth is");
        ModularBrush.Part ink = new ModularBrush.Part(PartChoice.INK); ink.set(PartDial.POOL, .4); ink.set(PartDial.WET, .6);
        ModularBrush.Part wash = ModularBrush.switchTo(ink, PartChoice.WASH);
        check(wash.get(PartDial.POOL) == .4 && wash.get(PartDial.WET) == .85, "A medium keeps pooling, not wet");
        ModularBrush.Part brush = new ModularBrush.Part(PartChoice.BRUSH_HAND); brush.set(PartDial.GAIN, 1.3);
        ModularBrush.Part custom = ModularBrush.switchTo(brush, PartChoice.CUSTOM);
        check(custom.get(PartDial.PS) == .9 && custom.get(PartDial.SS) == .4 && custom.get(PartDial.GAIN) == 1.3, "Custom keeps every cell");
        ModularBrush.Part wider = ModularBrush.switchTo(brush, PartChoice.PRESS_WIDER);
        check(wider.get(PartDial.PS) == .8 && wider.get(PartDial.PD) == 0 && wider.get(PartDial.GAIN) == 1.3, "A preset takes its own cells, keeping the response");
        ModularBrush.Part dry = new ModularBrush.Part(PartChoice.DRYING); dry.set(PartDial.DRYING, .9);
        check(ModularBrush.switchTo(dry, PartChoice.FAST_WET).get(PartDial.WINDOW) == 6, "Life keeps only drying time");
    }
    static void hand() {
        ModularBrush b = new ModularBrush("x", 1, ModularStyle.named("Anything goes"));
        b.set(BrushPart.HAND, new ModularBrush.Part(PartChoice.CUSTOM));
        b.part(BrushPart.HAND).set(PartDial.PS, .8); b.part(BrushPart.HAND).set(PartDial.GAIN, 1.5); b.part(BrushPart.HAND).set(PartDial.TD, .2);
        check(b.handCell(PartDial.PS) == 1 && Math.abs(b.handCell(PartDial.TD) - .3) < 1e-12, "A cell in use is its amount times the response, at most 1");
        check(ModularBrush.matchHand(new ModularBrush.Part(PartChoice.TILT_NIB)) == PartChoice.TILT_NIB, "A preset matches itself");
        ModularBrush.Part c = new ModularBrush.Part(PartChoice.CUSTOM); c.set(PartDial.PS, .4); c.set(PartDial.TW, .9); c.set(PartDial.PW, .4);
        check(ModularBrush.matchHand(c) == PartChoice.TILT_NIB, "Cells that match a preset are named for it");
        c.set(PartDial.TW, .8);
        check(ModularBrush.matchHand(c) == PartChoice.CUSTOM, "otherwise Custom");
        check(ModularBrush.matchHand(new ModularBrush.Part(PartChoice.CUSTOM)) == PartChoice.STEADY, "Every cell off is Steady");
    }
    static void shown() {
        check(PartDial.GRADE.shown(.4).equals("HB") && PartDial.GRADE.shown(0).equals("6H") && PartDial.GRADE.shown(1).equals("8B"), "Grades");
        check(PartDial.WINDOW.shown(6).equals("6.0 mm") && PartDial.DRYING.shown(.35).equals("3.1 s") && PartDial.WEIGHT.shown(1).equals("×1.00")
                && PartDial.ANGLE.shown(40.4).equals("40°") && PartDial.TAPER_IN.shown(0).equals("off") && PartDial.LINES.shown(2).equals("squiggle")
                && PartDial.COUNT.shown(3).equals("3") && PartDial.GAIN.shown(1).equals("×1.00") && PartDial.SOFT.shown(.3).equals("0.30")
                && PartDial.DIR.shown(1).equals("thicker"), "Dials show as the prototype's fmtDial");
        check(PartDial.wetMs(.35) == 3108 && PartDial.wetMs(0) == 750 && PartDial.wetMs(1) == 20000, "Drying time spans 0.75 to 20 s");
        check(PartChoice.WASH.labelOf(PartDial.POOL).equals("Hollow edge") && PartChoice.INK.labelOf(PartDial.POOL).equals("Pooling") && PartChoice.HAKE.labelOf(PartDial.DRY).equals("Streaks"), "Labels a choice renames");
    }
    static void styles() {
        String[] names = {"Fineliner", "Brush pen", "Dip pen", "Marker", "Pencil", "Charcoal", "Watercolor", "Ink wash", "Gouache", "Oils", "Pastels", "Dry brush", "Hake", "Anything goes"};
        check(ModularStyle.all().size() == names.length, "Fourteen styles");
        for (int i = 0; i < names.length; i++) check(ModularStyle.at(i).name.equals(names[i]) && ModularStyle.at(i).index == i && ModularStyle.named(names[i]) == ModularStyle.at(i), "Style " + i);
        check(ModularStyle.named("Wild") == ModularStyle.at(13) && ModularStyle.named("Wildcard") == ModularStyle.at(13) && ModularStyle.at(13).wild && !ModularStyle.at(0).wild && ModularStyle.named("Nope") == null, "Wild is Anything goes");
        check(!ModularFeatures.offered(PartChoice.BRISTLES) && !ModularFeatures.offered(PartChoice.LIFT) && !ModularFeatures.offered(PartChoice.DRAG) && !ModularFeatures.offered(PartChoice.SMUDGE)
                && ModularFeatures.offered(PartChoice.HAKE) && ModularFeatures.offered(PartChoice.OFF) && ModularFeatures.offered(PartChoice.DRYING), "Stage 1 offers no deferred choice");
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
```

- [ ] **Step 2: Run it to see it fail**

Add `BrushPart PartDial PartChoice ModularFeatures ModularStyle ModularBrush` to the end of `sources=(...)` and `PartModelChecks` to the end of `checks=(...)`; run `bash scripts/test_inkwash.sh`. Expected: compile errors (the classes don't exist).

- [ ] **Step 3: Write `BrushPart`**

```java
/** The six parts of a modular brush, in share-code order (parts.js 6–14). Never reorder. Pure. */
enum BrushPart {
    TIP("Tip", "The shape that touches the paper."), MEDIUM("Medium", "What it lays down."),
    CARRY("Carry", "What it picks up and drags along."), PAPER("Paper", "The grain under it."),
    LIFE("Life", "When the mark is final."), HAND("Hand", "How you hold it: pressure, tilt and speed.");
    final String label, blurb;
    /** This part's choices, in code order. */
    List<PartChoice> choices() { ... }   // PartChoice.values() filtered by part, cached in a static array per part
    /** The dials a swap keeps (parts.js 142): tip weight, taperIn, taperOut, dry, wobble, soft, angle; medium pool, bleed, bloom, splatter; paper tooth; life drying; hand gain, dir. */
    Set<PartDial> keeps() { ... }
}
```

- [ ] **Step 4: Write `PartDial`**

One constant per dial, in this order (the part decides nothing about codes; the choices' lists do): `WEIGHT, ASPECT, ANGLE, TAPER_IN, TAPER_OUT, DRY, WOBBLE, SOFT, HEAD_TURN, GAP, COUNT, REACH, HAIRS, COHESION, SPLAY, DRYNESS` (tip), `WET, LINE, POOL, SPLATTER, BLEED, BLOOM, BACKRUN, LINES, OFFSET, CHAOS, COVER, IMPASTO, SPREAD, HOLD, GRADE` (medium), `PICK, LAY, LOAD` (carry), `TOOTH` (paper), `WINDOW, DRYING, SETTLE, TAKES, PLUME` (life), `PS, PD, PW, TS, TD, TW, SS, SD, SW, DIR, GAIN` (hand). Fields: `final BrushPart part; final String key, label; final double min, max, def; final String[] choices (null for a slider); final boolean integer;` keys, labels, ranges, defaults and options exactly as parts.js 19–75 (the spec's "Dials" list). Also:

```java
static final PartDial[] HAND_CELLS = {PS, PD, PW, TS, TD, TW, SS, SD, SW};
boolean choice() { return choices != null; }
/** parts.js fmtDial (127–133) with each dial's fmt: WEIGHT and GAIN "×" + %.2f; ANGLE round + "°"; COUNT and HAIRS round; WINDOW %.1f + " mm"; DRYING wetMs/1000 %.1f + " s"; GRADE grade(); a choice its option; min 0 and v < .005 "off"; else %.2f. */
String shown(double v)
/** parts.js 15–16: GRADES[round(g < .4 ? g / .4 * 7 : 7 + (g - .4) / .6 * 8)], GRADES = 6H 5H 4H 3H 2H H F HB B 2B 3B 4B 5B 6B 7B 8B. */
static String grade(double g)
/** stroke.js 82: round(750 + 19250 · d²) ms. */
static long wetMs(double d)
```

- [ ] **Step 5: Write `PartChoice`**

Constants in code order: `ROUND, CHISEL, FLAT_NIB, HAKE, SPLIT_NIB, MULTI_LINE, BRISTLES, INK, WASH, COVER, CHARCOAL, GRAPHITE, OFF, LIFT, DRAG, SMUDGE, PLAIN, COLD_PRESS, LAID, FELT, CRUMPLED, CANVAS, NONE, FAST_WET, DRYING, STEADY, PRESS_WIDER, PRESS_DARKER, PRESS_BOTH, BRUSH_HAND, TILT_SIDE, TILT_NIB, QUICK_THIN, CUSTOM`, with `key` the prototype's (`"round"`, `"flat"`, `"split"`, `"multi"`, `"cold press"`, `"wet"` for Fast-wet, `"dry"` for Drying, `"wider"`, `"darker"`, `"both"`, `"brush"`, `"side"`, `"nib"`, `"quick"`, …), `label` the prototype's ("Flat nib", "Split nib", "Multi-line", "Cold press", "Fast-wet", "Press → wider", "Brush hand", "Tilt → nib", "Quick → thin", …; paper labels capitalised as parts.js 102), `blurb`, `dials` (parts.js 81–117; every hand choice has `HAND_DIALS` = the nine cells, `GAIN`, `DIR`), `macro` and `words` (for Stage 2; store them now), range overrides (`CHISEL`, `SPLIT_NIB` aspect .3–.55; `FLAT_NIB` .14–.28), default overrides (`CHISEL`, `SPLIT_NIB` aspect .42; `FLAT_NIB` .2; `HAKE` angle 75, dry .35, soft .2; `WASH` wet .85, line 0, pool .3, bloom .2; `GRAPHITE` spread 0; `LIFT` pick .2, lay .3; `DRAG` .6, .7, .85; `SMUDGE` .7, .55, .3; `PLAIN` tooth 0, other papers .5), label overrides (`HAKE` dry "Streaks"; `WASH` pool "Hollow edge", line "Pen line", wet "Wetness"; `CHARCOAL` hold "Hold (vine↔compressed)"; `GRAPHITE` spread "Smudge"), and hand preset cells (parts.js 109–117; `CUSTOM` has none). Methods:

```java
int index()                          // position within part.choices()
static PartChoice of(BrushPart part, int index)   // null outside the list
static PartChoice of(BrushPart part, String key)  // null if none
double[] range(PartDial d)           // the override, else {d.min, d.max}
double defaultOf(PartDial d)         // the override, else d.def
String labelOf(PartDial d)           // the override, else d.label
boolean has(PartDial d)
boolean preset()                     // a hand choice other than CUSTOM
double cell(PartDial cell)           // a preset's cell, 0 when it doesn't set it
BrushTexture texture()               // a paper's BrushTexture.values()[index()]; null for other parts
```

Enum constructors can't take all this neatly: give the constructor `(BrushPart part, String key, String label, PartDial macro, String low, String high, String blurb, PartDial... dials)` and fill overrides and cells in a `static {}` block (EnumMaps per choice).

- [ ] **Step 6: Write `ModularFeatures`**

```java
/** What Stage 1 draws. A deferred choice decodes and is kept, but rolls, dice, swaps and variations never pick it (spec, decision 4). */
final class ModularFeatures {
    static final boolean BRISTLES = false, CARRY = false, PLUMES = false, FILL_PLANE = false;
    static boolean offered(PartChoice c) { return !(c == PartChoice.BRISTLES && !BRISTLES) && !(c.part == BrushPart.CARRY && c != PartChoice.OFF && !CARRY); }
    private ModularFeatures() { }
}
```

- [ ] **Step 7: Write `ModularStyle` (names only for now)**

```java
/** The 14 styles (parts.js 280–322), in share-code order: never reorder. Rolls arrive in Task 3. Pure. */
final class ModularStyle {
    static final String ANYTHING_GOES = "Anything goes";
    final String name; final int index; final boolean wild;
    static List<ModularStyle> all()
    static ModularStyle at(int index)           // null outside 0–13
    static ModularStyle named(String name)      // "Wild" and "Wildcard" → Anything goes; null for unknown names
}
```

- [ ] **Step 8: Write `ModularBrush`**

Port `freshPart`, `switchChoice`, `normPart`, `normBrush`, `handCell`, `matchHand` (parts.js 134–168, 386–390):

```java
/** A six-part brush: a name, a 32-bit seed (read unsigned), a style, and per part one choice and its dials. Treat a Brush's as a value; change a copy. Pure. */
final class ModularBrush {
    /** One part: a choice and its dials' values, in the choice's dial order. */
    static final class Part {
        final PartChoice choice;
        private final double[] values;
        /** Every dial at the choice's default; a hand preset sets its cells (parts.js 135–140). Not clamped, as freshPart: a fresh hake's angle is 75, past the dial's 70, until normalized or written. */
        Part(PartChoice choice)
        boolean has(PartDial d)
        /** The value; 0 for a dial the choice doesn't have (parts.js val). */
        double get(PartDial d)
        /** The value; {@code absent} for a dial the choice doesn't have (the prototype's `?? fallback`). */
        double get(PartDial d, double absent)
        /** Sets a dial the choice has; IllegalArgumentException otherwise. */
        void set(PartDial d, double v)
        /** parts.js normPart: NaN or infinite → default; choice dials rounded into their options; others clamped to range(d); integers rounded. Returns this. */
        Part normalize()
        Part copy()
        /** The same choice and every value equal. */
        boolean sameAs(Part other)
    }
    String name;
    final long seed;
    ModularStyle style;
    private final Part[] parts = new Part[BrushPart.values().length];

    /** Every part its first choice, fresh. */
    ModularBrush(String name, long seed, ModularStyle style)   // seed & 0xffffffffL
    Part part(BrushPart p)
    PartChoice choice(BrushPart p)
    /** Puts {@code part} in its own part's place (part.choice.part). */
    void set(BrushPart p, Part part)                            // IllegalArgumentException if p != part.choice.part
    /** parts.js handCell: min(1, cell × gain). */
    double handCell(PartDial cell)
    ModularBrush normalize()
    ModularBrush copy()
    ModularBrush copy(long seed)
    /** parts.js switchChoice (143–149): {@code to}'s fresh part, keeping old's values for to.part.keeps() (paper: not from plain); a tip's aspect when inside to's range; every value for Custom hand. */
    static Part switchTo(Part old, PartChoice to)
    /** parts.js matchHand: the first preset whose nine cells are all within .005 of {@code hand}'s, else CUSTOM. */
    static PartChoice matchHand(Part hand)
}
```

- [ ] **Step 9: Run the checks**

Run: `bash scripts/test_inkwash.sh | grep -c '^PASS'` → `31`, including `PASS: six parts with the prototype's choices…`; `bash scripts/test_document.sh | grep -c '^PASS'` → `66`.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/io/github/mpdairy/monopaint/{BrushPart,PartDial,PartChoice,ModularFeatures,ModularStyle,ModularBrush}.java app/src/test/java/io/github/mpdairy/monopaint/PartModelChecks.java scripts/test_inkwash.sh
git commit -m "Modular engine: the six-part brush model"
```

---

### Task 3: Styles, rolls and locks

**Files:**
- Modify: `ModularStyle.java`
- Create: `ModularLocks.java`, `ModularStyleChecks.java`
- Modify: `scripts/test_inkwash.sh`

**Port:** parts.js 276–415 (`MSTYLES`, `styleSize`, `pickWeighted`, `rollSpec`, `styleBand`, `WILD_PARTS`, `ROUGH`, `rollPart`, `rollDial`, `rollHand`, `rollStyle`, `rollOnePart`, `rollOneDial`).

**App differences:**
- Randomness is a `java.util.Random` parameter (each `rnd()` is `random.nextDouble()`); names from `BrushNames.fresh(random)`; seeds `random.nextInt() & 0xffffffffL`.
- `pickWeighted` skips choices `ModularFeatures.offered` refuses; if nothing is left, the part's first offered choice.
- `rollDial` returns 0 for `TAKES` while `!ModularFeatures.PLUMES`.
- Anything goes' `size` is the prototype's 16 here; `ModularSizes` (Task 12) applies the app's own sizes for it.

- [ ] **Step 1: Write the check**

`ModularStyleChecks.java`:

```java
package io.github.mpdairy.monopaint;

import java.util.EnumSet;
import java.util.Random;

public final class ModularStyleChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        sizes(); bands(); withinStyles(); charcoal(); hake(); anythingGoes(); nothingDeferred(); locks(); oneAtATime();
        System.out.println("PASS: the 14 styles roll six-part brushes inside their weights and ranges (Charcoal's spread in its bands, Hake's head mostly following), Anything goes reaches every offered choice with tip roughness capped, nothing deferred is rolled, locks keep parts and dials, a part's or a dial's own dice changes only it");
    }
    static ModularStyle s(String name) { return ModularStyle.named(name); }
    static void sizes() {
        check(s("Fineliner").size(PartChoice.INK) == 4 && s("Ink wash").size(PartChoice.INK) == 8 && s("Ink wash").size(PartChoice.WASH) == 90
                && s("Ink wash").size(PartChoice.COVER) == 8 && s("Hake").size(PartChoice.WASH) == 180 && s("Anything goes").size(PartChoice.WASH) == 16, "Sizes as styleSize");
    }
    static void bands() {
        check(java.util.Arrays.equals(s("Fineliner").band(PartChoice.ROUND, PartDial.TAPER_IN), new double[]{0, .15}), "A sometimes-on dial's band runs from 0");
        check(java.util.Arrays.equals(s("Brush pen").band(PartChoice.ROUND, PartDial.TAPER_IN), new double[]{.3, .8}), "An always-on dial's band is its range");
        check(java.util.Arrays.equals(s("Charcoal").band(PartChoice.CHARCOAL, PartDial.SPREAD), new double[]{0, .9}), "Bands span every band");
        check(java.util.Arrays.equals(s("Marker").band(PartChoice.CHISEL, PartDial.ASPECT), new double[]{.35, .55}) && s("Fineliner").band(PartChoice.ROUND, PartDial.SOFT) == null
                && s("Hake").band(PartChoice.HAKE, PartDial.HEAD_TURN) == null && s("Anything goes").band(PartChoice.ROUND, PartDial.WEIGHT) == null, "No band without a spec, for choice dials, or in Anything goes");
    }
    static void withinStyles() {
        for (ModularStyle style : ModularStyle.all()) {
            if (style.wild) continue;
            Random random = new Random(1000 + style.index);
            for (int n = 0; n < 300; n++) {
                ModularBrush b = style.roll(random);
                check(b.style == style && !b.name.isEmpty() && b.seed >= 0 && b.seed <= 0xffffffffL, style.name + " rolls a named brush in itself");
                for (BrushPart part : BrushPart.values()) {
                    ModularBrush.Part p = b.part(part);
                    if (part != BrushPart.HAND) check(style.weight(p.choice) > 0 && ModularFeatures.offered(p.choice), style.name + " rolls only its own offered " + part + " choices: " + p.choice);
                    for (PartDial d : p.choice.dials) {
                        double v = p.get(d), r[] = p.choice.range(d);
                        check(v >= r[0] - 1e-12 && v <= r[1] + 1e-12, style.name + " " + d + " inside its range: " + v);
                        ModularStyle.Spec spec = style.spec(d);
                        if (d.choice() || part == BrushPart.HAND) continue;
                        if (spec == null) check(v == Math.max(r[0], Math.min(r[1], p.choice.defaultOf(d))), style.name + " leaves " + d + " at its default (inside its range)");
                        else if (spec.kind == ModularStyle.Spec.Kind.ALWAYS) check(v >= Math.max(spec.lo, r[0]) - 1e-9 && v <= Math.min(spec.hi, r[1]) + 1e-9, style.name + " " + d + " inside its spec: " + v);
                        else if (spec.kind == ModularStyle.Spec.Kind.MAYBE) check(v == 0 || v >= Math.max(spec.lo, r[0]) - 1e-9 && v <= Math.min(spec.hi, r[1]) + 1e-9, style.name + " " + d + " off or inside its spec: " + v);
                    }
                }
                ModularBrush.Part hand = b.part(BrushPart.HAND);
                check(hand.choice == ModularBrush.matchHand(hand) && hand.get(PartDial.GAIN) == 1, style.name + "'s hand is named for its cells, at full response");
            }
        }
    }
    static void charcoal() {
        Random random = new Random(21);
        int mid = 0, crisp = 0, messy = 0, n = 3000;
        for (int i = 0; i < n; i++) {
            double v = s("Charcoal").roll(random).part(BrushPart.MEDIUM).get(PartDial.SPREAD);
            if (v >= .15 && v <= .45) mid++; else if (v <= .1) crisp++; else if (v >= .6 && v <= .9) messy++;
        }
        check(mid > .64 * n && mid < .76 * n && crisp > .11 * n && crisp < .19 * n && messy > .11 * n && messy < .19 * n, "Charcoal's spread rolls 70% .15–.45, 15% crisp, 15% messy: " + mid + " " + crisp + " " + messy);
    }
    static void hake() {
        Random random = new Random(22);
        int fixed = 0, wash = 0, n = 2000;
        for (int i = 0; i < n; i++) {
            ModularBrush b = s("Hake").roll(random);
            check(b.choice(BrushPart.TIP) == PartChoice.HAKE, "Hake rolls a hake");
            if (b.part(BrushPart.TIP).get(PartDial.HEAD_TURN) == 1) fixed++;
            if (b.choice(BrushPart.MEDIUM) == PartChoice.WASH) wash++;
        }
        check(fixed > .18 * n && fixed < .32 * n && wash > .62 * n && wash < .78 * n, "Hake's head is fixed one roll in four, its medium wash seven in ten: " + fixed + " " + wash);
    }
    static void anythingGoes() {
        Random random = new Random(7);
        EnumSet<PartChoice> seen = EnumSet.noneOf(PartChoice.class);
        for (int i = 0; i < 4000; i++) {
            ModularBrush b = s("Anything goes").roll(random);
            for (BrushPart part : BrushPart.values()) seen.add(b.choice(part));
            ModularBrush.Part tip = b.part(BrushPart.TIP);
            check(tip.get(PartDial.DRY) + tip.get(PartDial.WOBBLE) <= 1.1 + 1e-9, "Anything goes caps a tip's roughness");
        }
        for (PartChoice c : PartChoice.values()) if (c.part != BrushPart.HAND && ModularFeatures.offered(c)) check(seen.contains(c), "Anything goes reaches " + c);
        check(seen.contains(PartChoice.CUSTOM) && seen.contains(PartChoice.STEADY), "and Custom and Steady hands");
    }
    static void nothingDeferred() {
        for (ModularStyle style : ModularStyle.all()) {
            Random random = new Random(300 + style.index);
            for (int i = 0; i < 500; i++) {
                ModularBrush b = style.roll(random);
                check(ModularFeatures.offered(b.choice(BrushPart.TIP)) && b.choice(BrushPart.CARRY) == PartChoice.OFF && b.part(BrushPart.LIFE).get(PartDial.TAKES) == 0, style.name + " rolls nothing Stage 1 can't draw");
            }
        }
    }
    static void locks() {
        Random random = new Random(3);
        ModularStyle pencil = s("Pencil");
        ModularBrush old = pencil.roll(random);
        ModularLocks paper = new ModularLocks(); paper.parts.add(BrushPart.PAPER);
        for (int i = 0; i < 20; i++) check(pencil.roll(random, old, paper).part(BrushPart.PAPER).sameAs(old.part(BrushPart.PAPER)), "A locked part is kept whole");
        ModularLocks grade = new ModularLocks(); grade.dials.add(PartDial.GRADE);
        for (int i = 0; i < 20; i++) check(pencil.roll(random, old, grade).part(BrushPart.MEDIUM).get(PartDial.GRADE) == old.part(BrushPart.MEDIUM).get(PartDial.GRADE), "A locked dial is kept when its choice rolls again");
    }
    static void oneAtATime() {
        Random random = new Random(4);
        ModularStyle dip = s("Dip pen");
        ModularBrush b = dip.roll(random);
        ModularBrush c = dip.rollOnePart(b, BrushPart.TIP, new ModularLocks(), random);
        for (BrushPart part : BrushPart.values()) if (part != BrushPart.TIP) check(c.part(part).sameAs(b.part(part)), "A part's dice changes only that part");
        check(c.seed == b.seed && c.name.equals(b.name) && c != b, "and keeps the name and seed, on a copy");
        ModularBrush d = dip.rollOneDial(b, PartDial.ANGLE, random);
        double angle = d.part(BrushPart.TIP).get(PartDial.ANGLE);
        check(angle >= 30 && angle <= 60, "A dial's dice stays in the style's band: " + angle);
        for (BrushPart part : BrushPart.values()) for (PartDial dial : b.part(part).choice.dials)
            if (dial != PartDial.ANGLE) check(d.part(part).get(dial) == b.part(part).get(dial), "and changes only that dial: " + dial);
        ModularBrush e = dip.rollOneDial(b, PartDial.TS, random);
        double ts = e.part(BrushPart.HAND).get(PartDial.TS);
        check((ts == 0 || ts >= .25 && ts <= 1) && e.choice(BrushPart.HAND) == ModularBrush.matchHand(e.part(BrushPart.HAND)), "A hand cell's dice is off or .25–1, and the hand is renamed");
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
```

- [ ] **Step 2: Run it to see it fail**

Add `ModularLocks` to `sources=(...)` after `ModularStyle`, and `ModularStyleChecks` to `checks=(...)`. Run `bash scripts/test_inkwash.sh`. Expected: compile errors.

- [ ] **Step 3: Write `ModularLocks`**

```java
/** What Randomize, dice and variations leave alone: whole parts, and dials (kept while their part's choice stays). Pure. */
final class ModularLocks {
    final EnumSet<BrushPart> parts = EnumSet.noneOf(BrushPart.class);
    final EnumSet<PartDial> dials = EnumSet.noneOf(PartDial.class);
}
```

- [ ] **Step 4: Add the styles and rolls to `ModularStyle`**

- The table: every style's `size` (a number, or Ink wash's {ink 8, wash 90}: `size(medium)` returns the medium's or the first), part weights (`LinkedHashMap<PartChoice, Double>` per part, insertion order as parts.js lists them), dial specs (`EnumMap<PartDial, Spec>`) exactly as parts.js 281–320, and Anything goes' `wild = true` with `WILD_PARTS` (parts.js 343–344) as its weights.
- `Spec`: `enum Kind { ALWAYS, MAYBE, BANDS, OPTIONS }`, `final Kind kind; final double p, lo, hi; final double[][] bands; final int[] options;` and `double roll(Random random)` = `rollSpec` (parts.js 327–332; `opts` picks `options[(int)(random.nextDouble() * options.length)]`).
- `double weight(PartChoice c)`: the style's weight for c (0 if it doesn't list it; HAND: 0).
- `Spec spec(PartDial d)`.
- `double[] band(PartChoice c, PartDial d)` = `styleBand` (parts.js 334–341).
- `ModularBrush roll(Random random)` = `roll(random, null, null)`.
- `ModularBrush roll(Random random, ModularBrush old, ModularLocks locks)` = `rollStyle` (parts.js 392–401): name `BrushNames.fresh(random)` (drawn first), then seed, then each part in order: a locked part copied from old; otherwise `rollPart`, then locked dials copied when old holds the same choice; then `normalize()`.
- `ModularBrush.Part rollPart(BrushPart part, ModularBrush.Part old, Set<PartDial> locked, Random random)` = `rollPart` (parts.js 346–361), with `rollHand` (375–385) for HAND and the ROUGH cap (tip dry and wobble; paper tooth) in Anything goes.
- `double rollDial(PartChoice c, PartDial d, Random random)` = `rollDial` (parts.js 362–374), the Anything goes rules in the same order, `TAKES` → 0 while `!ModularFeatures.PLUMES`.
- `ModularBrush rollOnePart(ModularBrush b, BrushPart part, ModularLocks locks, Random random)` and `ModularBrush rollOneDial(ModularBrush b, PartDial d, Random random)` = parts.js 403–415, on a copy.
- `pickWeighted` (parts.js 326) walks the weights in order: total of offered weights, `r = random.nextDouble() * total`, subtract until `r <= 0`.

- [ ] **Step 5: Run the checks**

Run: `bash scripts/test_inkwash.sh | grep -c '^PASS'` → `32`; `bash scripts/test_document.sh | grep -c '^PASS'` → `67`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/io/github/mpdairy/monopaint/{ModularStyle,ModularLocks}.java app/src/test/java/io/github/mpdairy/monopaint/ModularStyleChecks.java scripts/test_inkwash.sh
git commit -m "Modular engine: the 14 styles, rolls and locks"
```

---

### Task 4: M share codes

**Files:**
- Create: `ModularCode.java`, `ModularCodeChecks.java`
- Modify: `BrushCode.java` (`written` package-visible), `ModularBrush.java` (`key()`), `scripts/test_inkwash.sh`

**Port:** parts.js 544–597 (`M_STEPS`, `two36`, `writePart`, `readPart`, `codeSlug`, `encodeM`, `brushKey`, the M branch of `readAnyCode`). The format and the reading rules are in the spec's "Share codes".

- [ ] **Step 1: Write the check**

`ModularCodeChecks.java` (its `FIXTURES` are the prototype's own codes; later checks decode them):

```java
package io.github.mpdairy.monopaint;

import java.util.Locale;
import java.util.Random;

public final class ModularCodeChecks {
    /** Codes the prototype wrote (modular-lab-src, encodeM). The app must read them and write them back unchanged. */
    static final String BRAVE_ONION = "brave-onion.M24p6okati000ee00_0atzz9000000020000_0_277_1e0_6ee00ee0000we000000nz0.1u5hvr4";
    static final String[] FIXTURES = {
            "wet-wash.M60i000000000ee_1wei000ee7700_0_1at_2i0ll0js_1ss0000000000000000nz0.2r",
            "thick-oil.M925aeei00000p600at_2zzll_0_5i0_1rz_5ee0000ssi000000000nz0.7",
            "soft-coal.M51vleei000000000at_3clat_0_1ss_0_8p60000ssi000000000nz0.3",
            "wide-hake.Mc3i01zzcl77_1eeat00770000_0_000_0_8770000000000000000nz0.5",
            "rake.Md5nzssi000000000_0llp6000000000at77_0_000_0_4weatg7000000ee0000nz0.b",
            "hb-pencil.M40i000000000at_4i000_0_2p6_0_8atll00000000000000nz0.2",
            BRAVE_ONION};
    /** A fixture brush by its name, decoded afresh. */
    static ModularBrush fixture(String name) {
        for (String code : FIXTURES) if (code.startsWith(name + ".")) return ModularCode.decode(code);
        throw new IllegalArgumentException(name);
    }

    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        golden(); handBuilt(); fixtures(); shortCodes(); bad(); roundTrip(); identity();
        System.out.println("PASS: M share codes are the prototype's byte for byte (style, each part's choice and dials, seed), read back within half a step, read short codes as defaults and any case; bad codes and I/P codes read as nothing; a brush's key is its parts and seed");
    }
    static void golden() {
        String[][] fresh = {
                {"TIP", "HAKE", "test-tip.Md3i00zzcl77_000zz0000000000000_0_000_0_0000000000000000000nz0.9ix"},
                {"TIP", "SPLIT_NIB", "test-tip.Md4jsokeei0000000_000zz0000000000000_0_000_0_0000000000000000000nz0.9ix"},
                {"TIP", "MULTI_LINE", "test-tip.Md5c0lli000000000_000zz0000000000000_0_000_0_0000000000000000000nz0.9ix"},
                {"TIP", "BRISTLES", "test-tip.Md6rog7js5ei0_000zz0000000000000_0_000_0_0000000000000000000nz0.9ix"},
                {"MEDIUM", "WASH", "test-medium.Md0i000000000at_1ulat00770000_0_000_0_0000000000000000000nz0.9ix"},
                {"MEDIUM", "COVER", "test-medium.Md0i000000000at_2zz00_0_000_0_0000000000000000000nz0.9ix"},
                {"MEDIUM", "CHARCOAL", "test-medium.Md0i000000000at_3atee_0_000_0_0000000000000000000nz0.9ix"},
                {"MEDIUM", "GRAPHITE", "test-medium.Md0i000000000at_4ee00_0_000_0_0000000000000000000nz0.9ix"},
                {"CARRY", "DRAG", "test-carry.Md0i000000000at_000zz0000000000000_2llp6ul_000_0_0000000000000000000nz0.9ix"},
                {"PAPER", "CANVAS", "test-paper.Md0i000000000at_000zz0000000000000_0_5i0_0_0000000000000000000nz0.9ix"},
                {"LIFE", "FAST_WET", "test-life.Md0i000000000at_000zz0000000000000_0_000_1jz_0000000000000000000nz0.9ix"},
                {"LIFE", "DRYING", "test-life.Md0i000000000at_000zz0000000000000_0_000_2clcl0js_0000000000000000000nz0.9ix"},
                {"HAND", "BRUSH_HAND", "test-hand.Md0i000000000at_000zz0000000000000_0_000_0_4weatg7000000ee0000nz0.9ix"},
                {"HAND", "QUICK_THIN", "test-hand.Md0i000000000at_000zz0000000000000_0_000_0_7at0000000000p6i0eenz0.9ix"},
                {"HAND", "CUSTOM", "test-hand.Md0i000000000at_000zz0000000000000_0_000_0_8000000000000000000nz0.9ix"}};
        for (String[] f : fresh) {
            ModularBrush b = new ModularBrush("test-" + f[0].toLowerCase(Locale.ROOT), 12345, ModularStyle.named("Anything goes"));
            b.set(BrushPart.valueOf(f[0]), new ModularBrush.Part(PartChoice.valueOf(f[1])));
            check(ModularCode.encode(b).equals(f[2]), "A fresh " + f[1] + " writes as the prototype's: " + ModularCode.encode(b));
        }
    }
    static void handBuilt() {
        ModularBrush b = new ModularBrush("Brave Onion", 4_000_000_000L, ModularStyle.named("Dip pen"));
        ModularBrush.Part tip = new ModularBrush.Part(PartChoice.SPLIT_NIB); tip.set(PartDial.GAP, .7); tip.set(PartDial.ANGLE, 35); tip.set(PartDial.TAPER_OUT, .4);
        ModularBrush.Part medium = new ModularBrush.Part(PartChoice.INK); medium.set(PartDial.WET, .3); medium.set(PartDial.LINES, 2); medium.set(PartDial.POOL, .25);
        ModularBrush.Part paper = new ModularBrush.Part(PartChoice.LAID); paper.set(PartDial.TOOTH, .2);
        ModularBrush.Part life = new ModularBrush.Part(PartChoice.FAST_WET); life.set(PartDial.WINDOW, 4.5);
        b.set(BrushPart.TIP, tip); b.set(BrushPart.MEDIUM, medium); b.set(BrushPart.PAPER, paper); b.set(BrushPart.LIFE, life);
        b.set(BrushPart.HAND, new ModularBrush.Part(PartChoice.TILT_NIB));
        check(ModularCode.encode(b.normalize()).equals(BRAVE_ONION), "A brush built by hand writes the prototype's code: " + ModularCode.encode(b));
    }
    static void fixtures() {
        for (String code : FIXTURES) {
            ModularBrush b = ModularCode.decode(code);
            check(b != null && ModularCode.encode(b).equals(code), "The prototype's code reads and writes back unchanged: " + code);
        }
        ModularBrush onion = fixture("brave-onion");
        check(onion.name.equals("brave-onion") && onion.seed == 4_000_000_000L && onion.style.name.equals("Dip pen") && onion.choice(BrushPart.TIP) == PartChoice.SPLIT_NIB
                && onion.part(BrushPart.MEDIUM).get(PartDial.LINES) == 2 && Math.abs(onion.part(BrushPart.LIFE).get(PartDial.WINDOW) - 4.5) < 9 / 1295.0 / 2 + 1e-9, "brave-onion's parts");
        ModularBrush wash = fixture("wet-wash");
        check(wash.style.name.equals("Watercolor") && wash.choice(BrushPart.MEDIUM) == PartChoice.WASH && wash.choice(BrushPart.PAPER) == PartChoice.COLD_PRESS
                && wash.choice(BrushPart.LIFE) == PartChoice.DRYING && wash.part(BrushPart.LIFE).get(PartDial.SETTLE) == 777 / 1295.0 && wash.choice(BrushPart.HAND) == PartChoice.PRESS_WIDER, "wet-wash's parts");
        check(fixture("wide-hake").part(BrushPart.TIP).get(PartDial.HEAD_TURN) == 1 && fixture("rake").part(BrushPart.TIP).get(PartDial.COUNT) == 4
                && fixture("thick-oil").choice(BrushPart.MEDIUM) == PartChoice.COVER && fixture("soft-coal").choice(BrushPart.HAND) == PartChoice.CUSTOM, "The others' parts");
    }
    static void shortCodes() {
        String full = "a.Md0i000000000at_000zz0000000000000_0_000_0_0000000000000000000nz0.1";
        check(ModularCode.encode(ModularCode.decode("a.Md0_0_0_0_0_0.1")).equals(full), "A code that stops early reads the rest as defaults");
        check(ModularCode.encode(ModularCode.decode("A.MD0_0_0_0_0_0.1")).equals(full), "A code in capitals reads");
        check(ModularCode.encode(ModularCode.decode("a.Md0_0_0_0_0_0_5.1")).equals(full), "Segments past the sixth are ignored");
        check(ModularCode.encode(ModularCode.decode("a.Md0_0_0_0_0_6.1")).equals("a.Md0i000000000at_000zz0000000000000_0_000_0_6ee00ee0000we000000nz0.1"), "A hand preset without dials takes its cells");
        check(ModularCode.decode("  " + BRAVE_ONION + "\n") != null, "Spaces around a pasted code don't matter");
    }
    static void bad() {
        String[] codes = {null, "", "hello", "a.Me0_0_0_0_0_0.1", "a.Md7_0_0_0_0_0.1", "a.Md3i02_0_0_0_0_0.1", "a.Md0_0_0_0_0_0.zzzzzzz", "a.Md0_0_0_0_0.1",
                "x.M0_0_0.1", "a.M9z0000.1", "a.Md0_0_0_0_0_0", "a b.Md0_0_0_0_0_0.1", "dapper-fig.I610gavij3f7q16o0000006m00.e1"};
        for (String code : codes) check(ModularCode.decode(code) == null, "A bad code, or an I/P code, reads as no M brush: " + code);
        check(BrushCode.decode("dapper-fig.I610gavij3f7q16o0000006m00.e1") != null, "I/P codes still read as before");
    }
    static void roundTrip() {
        Random random = new Random(9);
        for (int i = 0; i < 2000; i++) {
            ModularBrush b = ModularStyle.at(i % 14).roll(random);
            String code = ModularCode.encode(b);
            ModularBrush back = ModularCode.decode(code);
            check(back != null && ModularCode.encode(back).equals(code) && back.seed == b.seed && back.style == b.style && back.name.equals(BrushCode.slug(b.name)), "A code reads back: " + code);
            for (BrushPart part : BrushPart.values()) {
                check(back.choice(part) == b.choice(part), "Choices read back: " + code);
                for (PartDial d : b.part(part).choice.dials) {
                    double within = d.choice() || d.integer ? 0 : (d.max - d.min) / 1295 / 2 + 1e-9;
                    check(Math.abs(back.part(part).get(d) - b.part(part).get(d)) <= within, d + " reads back within half a step: " + code);
                }
            }
        }
    }
    static void identity() {
        ModularBrush a = fixture("rake"), b = a.copy();
        b.name = "other"; b.style = ModularStyle.named("Oils");
        check(a.key().equals(b.key()) && a.key().equals(ModularCode.settings(a)) && !a.key().contains("rake"), "A brush's key is its parts and seed, not its name or style");
        check(!a.key().equals(a.copy(5).key()), "Another seed is another brush");
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
```

- [ ] **Step 2: Run it to see it fail**

Add `ModularCode` to `sources=(...)` after `ModularLocks`, `ModularCodeChecks` to `checks=(...)`. Expected: compile errors.

- [ ] **Step 3: Write `ModularCode` and `ModularBrush.key()`**

```java
/** Six-part share codes, the prototype's encodeM and its M reading (parts.js 544–597), byte for byte. Pure. */
final class ModularCode {
    static final int STEPS = 1295;
    private static final Pattern CODE = Pattern.compile("([a-z]+(?:-[a-z]+)*)\\.M([0-9a-z])([0-9a-z_]+)\\.([0-9a-z]{1,7})");
    static String encode(ModularBrush b)      // BrushCode.slug(name) + ".M" + style index base 36 + parts joined "_" + "." + seed base 36
    static String settings(ModularBrush b)    // brushKey: the parts joined "_" + "." + seed base 36
    static ModularBrush decode(String code)   // null unless an M code that reads; then normalized
}
```
- `writePart`: `Integer.toString(choice.index(), 36)`; a choice dial `Integer.toString((int)v, 36)`; another dial `round((clamp(v, d.min, d.max) - d.min) / (d.max - d.min) * 1295)` as two base-36 digits, a leading `0` under 36. Use the **dial's** range, not the choice's.
- `readPart` (parts.js 562–577): first character → choice index (null past the list); then each dial of the choice: a choice dial reads one digit if any remain (null past its options); another reads two if two remain (`Integer.parseInt(…, 36)`, null outside 0–1295) as `min + step / 1295 * (max - min)`; stop when the segment runs out (the rest stay at defaults); ignore characters left over.
- `decode`: `BrushCode.written(code.trim())` (make `written` package-visible in `BrushCode`; no other change there), match `CODE`, style `Character.digit(c, 36)` must be < 14, seed `Long.parseLong(…, 36)` ≤ 0xffffffff, split segments on `_` with `split("_", -1)`, fewer than six → null, read the first six, `new ModularBrush(name, seed, style)` with each part set, `normalize()`.
- `ModularBrush.key()` returns `ModularCode.settings(this)`.

- [ ] **Step 4: Run the checks**

Run: `bash scripts/test_inkwash.sh | grep -c '^PASS'` → `33` (BrushCodeChecks unchanged and passing); `bash scripts/test_document.sh | grep -c '^PASS'` → `68`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/mpdairy/monopaint/{ModularCode,ModularBrush,BrushCode}.java app/src/test/java/io/github/mpdairy/monopaint/ModularCodeChecks.java scripts/test_inkwash.sh
git commit -m "Modular engine: M share codes, as the web lab writes them"
```

---

### Task 5: Variations and words

**Files:**
- Create: `ModularVariations.java`, `ModularWords.java`, `ModularVariationsChecks.java`
- Modify: `scripts/test_inkwash.sh`

**Port:** parts.js 417–537 (`makeVars`, `handWords`, `choiceWords`, `captionLines`, `PAPER_WORDS`, `cap1`, `article`, `plainWords`).

**App differences:**
- `makeVars`' swap picks among `CHOICES[part]` minus the current one, minus Custom hand, minus choices `ModularFeatures.offered` refuses. Kin names: `BrushNames.kin(b.name, random)`; seeds `random.nextInt() & 0xffffffffL`. Its draws stay in the prototype's order: `swapAt` (`random.nextDouble() < .85 ? (int)(random.nextDouble() * 6) : -1`), then per card: seed, name, the swap (part, then choice), the nudge count, each nudge (pick, sign, amount).
- A nudge's caption is `"more " / "less "` + the choice's label for the dial, lowercased, with any ` (…)` removed (parts.js 456).
- `captionLines` returns the items, not joined lines: `caption(b)` = [the style's name, then the bits of parts.js 493–498], for `BrushCaptions.twoLines` to fit (Task 12).
- `plainWords`: leave out the carry clause unless `ModularFeatures.CARRY`, the plume clause unless `ModularFeatures.PLUMES`, and "splits into strands that rejoin" unless `ModularFeatures.BRISTLES`, so the words say what Stage 1 draws. Sort marks with a stable sort (`Collections.sort`), as the prototype's.
- Numbers as JS's `toFixed`: `String.format(Locale.ROOT, "%.1f" / "%.0f", v)`.

- [ ] **Step 1: Write the check**

`ModularVariationsChecks.java`:

```java
package io.github.mpdairy.monopaint;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public final class ModularVariationsChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        words(); choices(); variations(); locks();
        System.out.println("PASS: six variations nudge two or three dials and at most one swaps a part to an offered choice, labelled (\"Tip → Split nib\"), leaving locks alone; the words, captions and \"This brush\" are the prototype's");
    }
    static ModularBrush f(String name) { return ModularCodeChecks.fixture(name); }
    static void words() {
        String[][] expected = {
                {"brave-onion", "An ink line with a wash around it from a split nib, on laid paper. It parts into two rails as you press. It settles just behind the nib and doesn't change after you lift. Your hand: tilt → turns nib, press → wider."},
                {"wet-wash", "A wet, transparent wash from a round tip, on cold-press paper. It darkens at its edges and blooms in light and dark patches. It keeps changing for about 6 s after you lift. Your hand: press → wider."},
                {"thick-oil", "A thick opaque paint from a flat nib, on canvas. It breaks into dry-brush gaps and stands up in ridges lit from the upper left. It settles just behind the nib and doesn't change after you lift. Your hand: tilt → wider, tilt → lighter."},
                {"soft-coal", "A soft charcoal from a chisel, on cold-press paper. It catches the paper's grain and has a slight fuzz and white specks. It is final the moment the nib passes. Your hand: tilt → wider, press → wider."},
                {"wide-hake", "A flat, even wash from a wide hake, on smooth paper. It streaks as the hairs run dry. It is final the moment the nib passes. Your hand: press → wider."},
                {"rake", "An ink wash with a line through it from a 4-line rake, on smooth paper. It lets the line wander off the wash. It is final the moment the nib passes. Your hand: press → wider, press → lines part."},
                {"hb-pencil", "A pencil, B from a round tip, on laid paper. It catches the paper's grain. It is final the moment the nib passes. Your hand: press → darker, press → wider."}};
        for (String[] e : expected) check(ModularWords.plainWords(f(e[0])).equals(e[1]), "\"This brush\" as the prototype says it: " + ModularWords.plainWords(f(e[0])));
    }
    static void choices() {
        ModularBrush onion = f("brave-onion");
        check(ModularWords.choiceWords(onion, BrushPart.MEDIUM).equals("Ink, wet") && ModularWords.choiceWords(onion, BrushPart.LIFE).equals("Fast-wet · 5 mm")
                && ModularWords.choiceWords(onion, BrushPart.TIP).equals("Split nib") && ModularWords.choiceWords(onion, BrushPart.HAND).equals("tilt→turns nib, press→wider"), "Choice words");
        check(ModularWords.choiceWords(f("wet-wash"), BrushPart.LIFE).equals("Drying · 5.6 s") && ModularWords.choiceWords(f("hb-pencil"), BrushPart.MEDIUM).equals("Graphite B")
                && ModularWords.choiceWords(f("rake"), BrushPart.TIP).equals("4 lines") && ModularWords.choiceWords(f("thick-oil"), BrushPart.MEDIUM).equals("Cover, impasto")
                && ModularWords.choiceWords(f("soft-coal"), BrushPart.MEDIUM).equals("Charcoal"), "and more");
        check(ModularWords.caption(onion).equals(Arrays.asList("Dip pen", "split nib", "ink, wet", "laid")) && ModularWords.caption(f("soft-coal")).equals(Arrays.asList("Charcoal", "chisel", "charcoal", "cold press", "fast")), "Caption items: " + ModularWords.caption(onion));
    }
    static void variations() {
        Random random = new Random(11);
        ModularBrush b = f("brave-onion");
        int swaps = 0;
        for (int round = 0; round < 300; round++) {
            List<ModularVariations.Made> made = ModularVariations.make(b, new ModularLocks(), random);
            check(made.size() == 6, "Six variations");
            int swapped = 0;
            for (ModularVariations.Made m : made) {
                int differ = 0; BrushPart which = null;
                for (BrushPart part : BrushPart.values()) if (m.brush.choice(part) != b.choice(part)) { differ++; which = part; }
                check(differ <= 1, "A variation swaps at most one part");
                if (differ == 1) {
                    swapped++;
                    check(which != BrushPart.HAND && ModularFeatures.offered(m.brush.choice(which)) && (which.label + " → " + m.brush.choice(which).label).equals(m.swap) && m.caption.get(0).equals(m.swap), "A swap is labelled, never the hand, never deferred: " + m.swap);
                } else check(m.swap == null, "No label without a swap");
                int nudges = m.caption.size() - (m.swap == null ? 0 : 1);
                check(nudges <= 3, "Two or three nudges");
                for (String item : m.caption) if (!item.equals(m.swap)) check(item.startsWith("more ") || item.startsWith("less "), "A nudge says which way: " + item);
                check(m.brush.name.endsWith("-onion") && m.brush.style == b.style && m.brush != b, "A kin name, the same style, a new brush");
            }
            check(swapped <= 1, "At most one of the six swaps a part");
            swaps += swapped;
        }
        check(swaps > 230 && swaps < 280, "About 85% of rounds swap: " + swaps);
        check(b.key().equals(ModularCodeChecks.fixture("brave-onion").key()), "The brush varied is unchanged");
    }
    static void locks() {
        Random random = new Random(12);
        ModularBrush b = f("brave-onion");
        ModularLocks locks = new ModularLocks(); locks.parts.add(BrushPart.TIP); locks.dials.add(PartDial.WET);
        for (int round = 0; round < 100; round++) for (ModularVariations.Made m : ModularVariations.make(b, locks, random)) {
            check(m.brush.part(BrushPart.TIP).sameAs(b.part(BrushPart.TIP)), "A locked part never varies");
            if (m.brush.choice(BrushPart.MEDIUM) == b.choice(BrushPart.MEDIUM)) check(m.brush.part(BrushPart.MEDIUM).get(PartDial.WET) == b.part(BrushPart.MEDIUM).get(PartDial.WET), "nor a locked dial");
        }
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
```

- [ ] **Step 2: Run it to see it fail**

Add `ModularVariations ModularWords` to `sources=(...)`, `ModularVariationsChecks` to `checks=(...)`. Expected: compile errors.

- [ ] **Step 3: Write `ModularWords`**

```java
/** A modular brush in words (parts.js 463–537): choice words, the hand's summary, caption items and "This brush". Pure. */
final class ModularWords {
    static String handWords(ModularBrush b, int n)               // parts.js 466–472
    static String choiceWords(ModularBrush b, BrushPart part)    // parts.js 473–490
    static List<String> caption(ModularBrush b)                  // [style name, bits…] (parts.js 492–501, unjoined)
    static String plainWords(ModularBrush b)                     // parts.js 507–537, with the Stage 1 omissions above
}
```

- [ ] **Step 4: Write `ModularVariations`**

```java
/** Six "your brush, but…" variations (parts.js 417–461). Pure. */
final class ModularVariations {
    static final int COUNT = 6;
    static final class Made {
        final ModularBrush brush;
        /** The swap's label first if there is one, then each nudge ("more nib width"). */
        final List<String> caption;
        /** "Tip → Split nib", or null. */
        final String swap;
    }
    static List<Made> make(ModularBrush b, ModularLocks locks, Random random)
}
```
The nudge pool, bands, `rel` and sign rules, `.04` snap to 0 and `matchHand` after a hand nudge are parts.js 422–457 unchanged; each variation is `normalize()`d.

- [ ] **Step 5: Run the checks**

Run: `bash scripts/test_inkwash.sh | grep -c '^PASS'` → `34`; `bash scripts/test_document.sh | grep -c '^PASS'` → `69`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/io/github/mpdairy/monopaint/{ModularVariations,ModularWords}.java app/src/test/java/io/github/mpdairy/monopaint/ModularVariationsChecks.java scripts/test_inkwash.sh
git commit -m "Modular engine: variations, captions and the \"This brush\" words"
```

---

### Task 6: Resolve

**Files:**
- Create: `ResolvedBrush.java`, `ResolveChecks.java`
- Modify: `scripts/test_inkwash.sh`

**Port:** parts.js 170–274 (`resolve`, `partsA`). Doubles throughout. The rng for Offset's skips (parts.js 265) is `new WildBrush.Random(seed ^ 0x1a6)`.

**App differences (spec, "Resolve"):**
- Bristles: `lineTip = NIB`, `bodyTip = BODY`, the `bristle` input (`bb.bristle`) is the tip's `DRYNESS`; no swarm fields.
- No `carry` (Stage 1 draws Carry as Off): the own-paint `load` in `inkK` and `washK` (parts.js 241) is always 1; `takes` is always none.
- `gray = 255 * (1 - darkness)`.

- [ ] **Step 1: Write the check**

`ResolveChecks.java` (numbers from the prototype's `resolve` on the same codes, at 16 px and 360 px, shade 1):

```java
package io.github.mpdairy.monopaint;

public final class ResolveChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        onion(); wash(); oil(); coal(); hake(); rake(); pencil(); fallbacks();
        System.out.println("PASS: parts resolve to the prototype's line, body and settle for split nib, wash, cover with impasto, charcoal, hake, multi-line and graphite brushes, at 16 and 360 px; bristles draw as a round tip and carry as off");
    }
    static ResolvedBrush r(String name, double size) { return ResolvedBrush.of(ModularCodeChecks.fixture(name), size, 1); }
    static void onion() {
        ResolvedBrush r = r("brave-onion", 16);
        near(r.inkSize, 16.00617760617761, "inkSize"); near(r.washSize, 128.04942084942087, "washSize"); near(r.bodySize, 52.45081734590586, "bodySize");
        near(r.inkK, 1, "inkK"); near(r.washK, .34, "washK"); near(r.windowLen, 62.03552123552123, "windowLen"); near(r.placeSize, 52.45081734590586, "placeSize");
        check(r.lineTip == ResolvedBrush.LineTip.NIB && r.linePlaced && r.bodyTip == ResolvedBrush.BodyTip.BODY && r.line && r.body != null && r.lineReach == 96
                && r.life == ResolvedBrush.Life.WET && r.rule == ResolvedBrush.Rule.INK && r.paper == BrushTexture.LAID, "brave-onion's stampers");
        ResolvedBrush.Pen p = r.pen;
        near(p.aspect, .4198764478764479, "aspect"); near(p.angle, 35.01930501930502, "angle"); near(p.taperOut, .4, "taperOut"); near(p.pressure, .3998455598455599, "pressure");
        near(p.tooth, .13992277992277993, "tooth"); near(p.pool, .175038535501856, "pool"); near(p.spread, .6996138996138996, "spread"); near(p.breathe, .3998455598455599, "breathe");
        near(p.wetGap, .5406949806949807, "wetGap"); near(p.tiltAngle, .9000384609651019, "tiltAngle");
        check(p.lines == ResolvedBrush.Lines.SPLIT && p.pWidth == 0 && !p.fast && p.speed == 0 && !p.thicker, "a split nib breathes inside its placement");
        near(ResolvedBrush.of(ModularCodeChecks.fixture("brave-onion"), 16, .5).inkK, .5, "inkK follows the shade");
        near(ResolvedBrush.of(ModularCodeChecks.fixture("brave-onion"), 16, .53).gray, 255 * .47, "gray is the app's paper times the shade's lightness");
    }
    static void wash() {
        ResolvedBrush r = r("wet-wash", 16);
        check(!r.line && r.body != null && r.body.wet && r.life == ResolvedBrush.Life.DRY && r.cover == 0, "A wet wash is a body, drying");
        near(r.bodySize, 16.00617760617761, "bodySize"); near(r.inkSize, 1.600617760617761, "inkSize"); near(r.windowLen, 80.0030888030888, "windowLen"); near(r.settle, 777 / 1295.0, "settle");
        near(r.body.pressure, .7996911196911198, "body pressure"); near(r.body.soft, .4, "soft"); near(r.body.gran, .30038610038610036, "body gran");
        near(r.look.hollow, .5003861003861004, "hollow"); near(r.look.bloom, .3521853281853282, "bloom"); near(r.look.backrun, .1760926640926641, "backrun"); near(r.look.gran, .30038610038610036, "gran");
        ResolvedBrush big = r("wet-wash", 360);
        near(big.washSize, 360.1389961389962, "washSize at 360"); near(big.windowLen, 252.0694980694981, "windowLen at 360");
    }
    static void oil() {
        ResolvedBrush r = r("thick-oil", 16);
        check(r.cover == 1 && r.body != null && !r.body.wet && r.life == ResolvedBrush.Life.WET, "Cover is opaque flat paint");
        near(r.body.aspect, .3202471042471043, "a flat nib's paint is an ellipse"); near(r.body.impasto, .6, "impasto"); near(r.body.tilt, .7996911196911198, "tilt");
        near(r.body.tDark, .5001929011195422, "tDark"); near(r.body.streaks, .6996138996138996, "streaks"); near(r.windowLen, 103.98455598455597, "windowLen");
        near(r.look.cover, 1, "look cover"); near(r.look.impasto, .6, "look impasto"); near(r.look.gran, .5003861003861004, "look gran");
    }
    static void coal() {
        ResolvedBrush r = r("soft-coal", 16);
        check(r.rule == ResolvedBrush.Rule.CHARCOAL && r.line && r.body == null && r.pen.fast && r.life == ResolvedBrush.Life.NONE, "Charcoal is a line, final as laid");
        near(r.inkK, .8041081081081081, "inkK"); near(r.pen.fuzz, .3498069498069498, "fuzz"); near(r.pen.tooth, .8, "tooth"); near(r.pen.tiltSize, .7996911196911198, "tiltSize");
        ResolvedBrush big = r("soft-coal", 360);
        near(big.inkSize, 240, "Charcoal caps at 240 px"); near(big.washSize, 360, "washSize"); near(big.bodySize, 528, "bodySize"); near(big.windowLen, 192, "windowLen");
    }
    static void hake() {
        ResolvedBrush r = r("wide-hake", 16);
        check(r.lineTip == ResolvedBrush.LineTip.HAKE && !r.line && r.body != null && r.body.flatHead && r.body.headFixed && !r.body.wet && r.body.fast, "A fixed hake laying flat wash");
        near(r.body.streaks, .3498069498069498, "streaks"); near(r.body.pressure, .19992277992277996, "pressure");
    }
    static void rake() {
        ResolvedBrush r = r("rake", 16);
        check(r.pen.lines == ResolvedBrush.Lines.SPREAD && r.pen.count == 4 && r.linePlaced && r.lineReach == 375, "A 4-line rake, placed");
        near(r.pen.skipEnd, 11.536572220824544, "Offset's skips come from the seed"); near(r.body.skipStart, 8.251397428451753, "body skipStart");
        near(r.parts.lineWet, 1.5717683397683397, "lineWet"); near(r.parts.bodyAmt, .7755472592592593, "bodyAmt"); near(r.parts.diffuse, .96, "diffuse");
        check(r.parts.diffR == 10 && r.parts.placed && r.parts.stampLine, "diffR");
        near(r.inkK, .805349806949807, "inkK"); near(r.placeSize, 121.64694980694982, "placeSize"); near(r.body.amount, .7755472592592593, "amount");
        near(r.body.soft, .5464, "soft"); near(r.body.pWidth, .45001923048255094, "pWidth"); near(r.look.feather, .288, "feather");
        near(r.pen.speed, -.3998455598455599, "speed"); near(r.pen.pDark, .3002701211967621, "pDark");
        ResolvedBrush big = r("rake", 360);
        check(big.lineReach == 5966, "lineReach at 360: " + big.lineReach); near(big.placeSize, 2737.056370656371, "placeSize at 360"); near(big.bodySize, 580.8581700188381, "bodySize at 360");
    }
    static void pencil() {
        ResolvedBrush r = r("hb-pencil", 16);
        check(r.rule == ResolvedBrush.Rule.GRAPHITE && r.body == null, "Graphite is a line");
        near(r.inkK, .5590430766901355, "inkK"); near(r.pen.pDark, .5997683397683398, "pDark"); near(r.pen.tooth, .6996138996138996, "tooth");
    }
    static void fallbacks() {
        ModularBrush bristles = ModularCode.decode("test-tip.Md6rog7js5ei0_000zz0000000000000_0_000_0_0000000000000000000nz0.9ix");
        ResolvedBrush r = ResolvedBrush.of(bristles, 16, 1);
        check(r.lineTip == ResolvedBrush.LineTip.NIB && r.bodyTip == ResolvedBrush.BodyTip.BODY, "Bristles draw as a round tip in Stage 1");
        near(r.pen.dry, bristles.part(BrushPart.TIP).get(PartDial.DRYNESS), "with its dryness as dry brush");
        ModularBrush drag = ModularCode.decode("test-carry.Md0i000000000at_000zz0000000000000_2llp6ul_000_0_0000000000000000000nz0.9ix");
        ModularBrush off = drag.copy(); off.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.OFF));
        near(ResolvedBrush.of(drag, 16, 1).inkK, ResolvedBrush.of(off, 16, 1).inkK, "Carry draws as off: its own paint is all of it");
    }
    static void near(double got, double want, String what) { check(Math.abs(got - want) <= 1e-9 * Math.max(1, Math.abs(want)), what + ": " + got + " vs " + want); }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
```

- [ ] **Step 2: Run it to see it fail**

Add `ResolvedBrush` to `sources=(...)`, `ResolveChecks` to `checks=(...)`. Expected: compile errors.

- [ ] **Step 3: Write `ResolvedBrush`**

```java
/** Parts → what the stampers draw: the prototype's resolve and partsA (parts.js 170–274), with Stage 1's fallbacks. Pure. */
final class ResolvedBrush {
    enum Rule { INK, CHARCOAL, GRAPHITE }
    enum Life { NONE, WET, DRY }
    enum Lines { CENTRE, SPREAD, OUTLINE, SQUIGGLE, SPLIT }
    enum LineTip { NIB, HAKE }
    enum BodyTip { BODY, PLACED }
    /** partsA's blend of a line and a body. */
    static final class Blend { double w, inkShare, lineWet, bodyAmt, lineSize, bodySize, diffuse; int diffR; boolean stampLine, placed; }
    /** The line's inputs (partsA's pen plus the hand). Mutable: bodyPen is a changed copy. */
    static final class Pen {
        long seed; double aspect, angle, taperIn, taperOut, pressure, speed, dry, tooth, wobble, pool, splatter;
        Lines lines; int count; double spread, breathe, wetGap, offset, chaos, skipStart, skipEnd;
        boolean fast, thicker; double tiltSize, tiltAngle, tDark, pDark, sDark, pWidth, sAngle, fuzz;
        Pen copy()
    }
    /** The body's inputs (partsA's body plus the hand, cover and head); also the hake as a line (lineHake). */
    static final class Body {
        long seed; boolean wet; double pressure, soft, streaks, aspect, angle, taperIn, taperOut, amount, skipStart, skipEnd;
        boolean fast; double tiltSize, tDark, pDark, sDark, pWidth, sAngle, cover, impasto, gran;
        boolean flatHead, headFixed; double tilt, speed, softK;
        Body copy()
    }
    /** The settle's inputs. */
    static final class Look { double feather, hollow, bloom, backrun, gran, cover, impasto; }

    final Blend parts; final Pen pen; final Body body; final Body placeBody; final Look look;
    final Rule rule; final BrushTexture paper; final Life life;
    final double inkSize, washSize, bodySize, inkK, washK, cover, gray, shade, hold, spread, grade, windowLen, drying, settle, lineTooth, placeSize;
    final boolean line, lineInk, linePlaced, lineStateful, bodyStateful;
    final int lineReach;
    final LineTip lineTip; final BodyTip bodyTip;
    final Body lineHake;              // the hake as a line, or null
    final double bodyLineSize, bodyPlaceSize; final Pen bodyPen; final Body bodyGeom;   // the placed body, or 0 / null
    static ResolvedBrush of(ModularBrush b, double size, double darkness)
}
```
`body` is null when `parts.bodyAmt <= 0` (parts.js 217). `paper` is the paper choice's `texture()`. `lineStateful` is `lineTip == HAKE`; `bodyStateful` is the tip being a hake (Stage 1: no swarm). Map `mode` → `wet`, `head === "flat"` → `flatHead`, `headTurn === "fixed"` → `headFixed`; `lineHake.softK = lerp(.012, .6, soft)`.

- [ ] **Step 4: Run the checks**

Run: `bash scripts/test_inkwash.sh | grep -c '^PASS'` → `35`; `bash scripts/test_document.sh | grep -c '^PASS'` → `70`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/mpdairy/monopaint/ResolvedBrush.java app/src/test/java/io/github/mpdairy/monopaint/ResolveChecks.java scripts/test_inkwash.sh
git commit -m "Modular engine: resolve parts into the line, the body and the settle"
```

---

### Task 7: Engine foundations

**Files:**
- Create: `FloatTiles.java`, `StrokePoints.java`, `StrokeMath.java`, `StrokePaper.java`, `StickGrain.java`, `StrokeBasicsChecks.java`
- Modify: `BrushDirection.java` (add `azimuth`), `scripts/test_inkwash.sh`

**Port:** engine.js 8–18 and 38–106 (`smooth`, `smoothstep`, `mix`, `LINE_WET_K`, `union`, `grow`, `tiltOf`, `turnToward`, `addPt`, `inkBig`, `paintBig`, `taperLens`, `handDark`, `tileNoise`, `stickGrain`, `grainAt`), engine.js 479–490 (`breath`, `hash01`, `noise1`), port.js 213–232 (`blur`), 277–286 (`addPoint`), stroke.js 462 (`firstAt`).

- [ ] **Step 1: Write the check**

`StrokeBasicsChecks.java` (reference numbers from the prototype's functions in Node):

```java
package io.github.mpdairy.monopaint;

import java.util.Random;

public final class StrokeBasicsChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        blur(); hashes(); grain(); tilt(); hand(); tiles(); points(); paper();
        System.out.println("PASS: the engine's blur, hashes, noise, stick grain and tilt give the prototype's numbers; float tiles keep the strongest value and clear; samples skip under 0.6 px, smooth speed and direction and keep the peak pressure");
    }
    static void blur() {
        float[] a = {0, 1, 0, 0, 2, 0, 0, 0, 3, 1, 1, 1};
        float[] want = {0.6666666865348816f, 0.4444444477558136f, 0.2222222238779068f, 0f, 1.3333333730697632f, 0.8888888955116272f,
                0.4444444477558136f, 0.3333333432674408f, 2f, 1.3333332538604736f, 0.6666666865348816f, 0.6666666865348816f};
        check(java.util.Arrays.equals(StrokeMath.blur(a, 4, 3, 1), want), "The box blur is the prototype's, to the bit");
        Random random = new Random(1); int w = 50, h = 40, r = 3; float[] src = new float[w * h];
        for (int i = 0; i < src.length; i++) src[i] = random.nextFloat();
        float[] got = StrokeMath.blur(src, w, h, r);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            double row = 0;
            for (int dy = -r; dy <= r; dy++) { double col = 0; for (int dx = -r; dx <= r; dx++) col += src[clamp(y + dy, h) * w + clamp(x + dx, w)]; row += col / (2 * r + 1); }
            check(Math.abs(got[y * w + x] - row / (2 * r + 1)) < 1e-5, "and a box blur, edges clamped");
        }
    }
    static int clamp(int v, int n) { return v < 0 ? 0 : v >= n ? n - 1 : v; }
    static void hashes() {
        check(StrokeMath.hash01(12345, 7) == 0.4938501762226224 && StrokeMath.hash01(0xdeadbeef, -3) == 0.5925729863811284, "hash01 as the prototype's");
        check(StrokeMath.noise1(99, 3.7) == 0.6352493548375787 && StrokeMath.noise1((int)4_000_000_000L, .25) == 0.3719984561874298, "noise1 as the prototype's");
        WildBrush.Random rng = new WildBrush.Random(99);
        check(rng.next() == 0.2604658124037087 && rng.next() == 0.8048227655235678, "WildBrush.Random is the prototype's rng");
    }
    static void grain() {
        StickGrain g = StickGrain.of(3);
        check(g.fine(0, 0) == 0.2557336688041687f && g.mid(488, 1) == 0.6832239031791687f && g.low(511, 511) == 0.0683593824505806f, "The stick grain is the prototype's");
        check(g.fine(5, 7) == g.fine(517, 519) && g.fine(5, 7) == g.fine(-507, 7) && StickGrain.of(3) == g, "It tiles every 512 px and is kept");
    }
    static void tilt() {
        check(Math.abs(BrushDirection.azimuth(30, 25) - 0.6793994308245329) < 1e-12 && Math.abs(BrushDirection.lean(30, 25) - 0.5263449511455924) < 1e-6, "Tilt's direction and lean as the prototype's tiltOf");
        check(Double.isNaN(BrushDirection.azimuth(2, 2)) && BrushDirection.lean(2, 2) == 0 && Double.isNaN(BrushDirection.azimuth(Float.NaN, 0)) && Double.isNaN(BrushDirection.azimuth(95, 0)), "No direction near upright or without a reading");
        check(Math.abs(StrokeMath.turnToward(0, Math.PI / 2, .5) - Math.PI / 4) < 1e-12 && Math.abs(StrokeMath.turnToward(0, Math.PI, 1)) < 1e-12, "turnToward treats a head as symmetric");
    }
    static void hand() {
        check(StrokeMath.handDark(0, 0, 0, .3, 2, .5) == 1, "The hand's darkness is exactly 1 with every cell off");
        check(Math.abs(StrokeMath.handDark(.5, 0, 0, .2, 0, 0) - .68) < 1e-12 && Math.abs(StrokeMath.handDark(0, 1, 0, 1, 2.5, 0) - .45) < 1e-12 && Math.abs(StrokeMath.handDark(0, 0, 1, 1, 0, 1) - .55) < 1e-12, "pressure, speed and tilt lighten it");
        check(StrokeMath.inkBig(8) == 1 && StrokeMath.inkBig(32) == 2 && StrokeMath.paintBig(220) == 1 && Math.abs(StrokeMath.paintBig(880) - 2) < 1e-12, "Size scaling starts past 16 px lines and 220 px paints");
    }
    static void tiles() {
        FloatTiles t = new FloatTiles(300, 200, 0);
        check(t.get(10, 10) == 0 && t.tile(0, 0, false) == null, "Empty until written");
        check(t.raise(10, 10, .5f) && !t.raise(10, 10, .4f) && t.get(10, 10) == .5f, "raise keeps the stronger");
        t.set(299, 199, 1); check(t.get(299, 199) == 1, "set");
        t.clear(new int[]{0, 0, 299, 199}); check(t.get(10, 10) == 0 && t.get(299, 199) == 0, "clear");
        FloatTiles m = new FloatTiles(300, 200, -1); check(m.get(5, 5) == -1 && m.raise(5, 5, 0) && m.get(5, 5) == 0, "Another fill");
    }
    static void points() {
        StrokePoints p = new StrokePoints();
        check(p.add(0, 0, .5f, 0, Float.NaN, Float.NaN) && !p.add(.3f, .3f, .5f, 4, 0, 0), "A sample under 0.6 px from the last is skipped");
        check(p.add(10, 0, .7f, 10, 30, 25) && p.count == 2 && p.d[1] == 10 && Math.abs(p.v[1] - .25) < 1e-6 && p.pk[1] == .7f && p.ux[0] == 1 && p.ux[1] == 1, "distance, speed a quarter at a time, peak, direction");
        p.add(10, 10, .2f, 20, 0, 0);
        double k = 1 - Math.exp(-10 / 12.0), sx = 1 + (0 - 1) * k, sy = k, m = Math.hypot(sx, sy);
        check(Math.abs(p.v[2] - (.25 * .75 + .25)) < 1e-6 && p.pk[2] == .7f && Math.abs(p.ux[2] - sx / m) < 1e-6 && Math.abs(p.uy[2] - sy / m) < 1e-6 && p.tx[1] == 30 && p.ty[1] == 25, "direction smoothed over 12 px; tilt kept");
        check(p.firstAt(15) == 2 && p.firstAt(0) == 1 && Math.abs(p.total() - 20) < 1e-6, "firstAt and total");
        StrokePoints u = new StrokePoints(); u.add(0, 0, .5f, PenSpeed.UNTIMED, 0, 0); u.add(10, 0, .5f, PenSpeed.UNTIMED, 0, 0);
        check(u.v[1] == 0, "Without times the speed stays 0");
    }
    static void paper() {
        StrokePaper plain = new StrokePaper(BrushTexture.PLAIN), laid = new StrokePaper(BrushTexture.LAID);
        PaperGrain g = PaperGrain.PLAIN;
        float want = Math.max(0, Math.min(1, .5f + g.spread * (g.fine(3, 4) / 255f - .5f)));
        check(Math.abs(plain.fine(3, 4) - want) < 1e-6 && plain.low(9, 9) == WatercolorSettle.Paper.low(9, 9) / 255f && plain.blob(9, 9) == WatercolorSettle.Paper.blob(9, 9) / 255f, "The paper's noise is the app's, widened as WildInkStroke widens it");
        int differ = 0; for (int x = 0; x < 64; x++) if (laid.fine(x, 7) != plain.fine(x, 7)) differ++;
        check(differ > 32, "and follows the paper's texture");
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
```

- [ ] **Step 2: Run it to see it fail**

Add `FloatTiles StrokePoints StrokeMath StrokePaper StickGrain` to `sources=(...)`, `StrokeBasicsChecks` to `checks=(...)`. Expected: compile errors.

- [ ] **Step 3: Write the classes**

```java
/** A page-sized float buffer in 64-pixel tiles, made on first write (the prototype's page buffers, without 20 MB each). Pure. */
final class FloatTiles {
    static final int SHIFT = 6, SIDE = 1 << SHIFT;
    final int width, height; final float fill;
    FloatTiles(int width, int height, float fill)
    float get(int x, int y)                        // fill where no tile; x, y on the page
    void set(int x, int y, float v)
    boolean raise(int x, int y, float v)           // keeps the stronger; true if v was stronger
    /** The tile at tile column tx, row ty (SIDE × SIDE, row by row), made filled when create, else null if absent. */
    float[] tile(int tx, int ty, boolean create)
    void clear(int[] rect)                         // back to fill inside rect; tiles wholly inside are dropped
    void clearAll()
}
```
Inner loops fetch a tile once per run of pixels inside it (`x >> SHIFT`, `y >> SHIFT`, index `((y & 63) << 6) | (x & 63)`), not per pixel.

```java
/** A stroke's samples, as the prototype's addPt over addPoint (engine.js 51–66, port.js 277–286). Pure. */
final class StrokePoints {
    int count;
    float[] x, y, p, d, v, tx, ty, pk, ux, uy; long[] t;   // grown by doubling
    boolean add(float x, float y, float p, long time, float tiltX, float tiltY)
    float total()                                           // d[count - 1], 0 when empty
    int firstAt(float d)                                    // stroke.js 462
}
```
Speed: `v = v₀ · .75 + seg / max(1, t − t₀) · .25`, the instant term 0 when either time is `PenSpeed.UNTIMED` (as `WildInkStroke.add`). Direction: the first segment sets both points' (ux, uy); later ones smooth with `k = 1 − e^(−seg/12)` and normalise.

```java
/** The engine's small arithmetic (engine.js 8–80, 479–490; port.js 213–232). Pure. */
final class StrokeMath {
    static final double LINE_WET_K = 1.4;
    static double clamp(double v) / clamp(double v, double lo, double hi); static double lerp(double a, double b, double t);
    static double mix(double a, double b, double t)       // exact at its ends
    static double smooth(double t); static double smoothstep(double e0, double e1, double x)
    static float[] blur(float[] src, int w, int h, int r)  // two passes; accumulate in double, store float
    static double hash01(int seed, int i); static double noise1(int seed, double x)
    static double turnToward(double a, double target, double k)
    static double handDark(double pDark, double sDark, double tDark, double pr, double v, double lean)
    static double inkBig(double size); static double paintBig(double size)
    static double[] taperLens(double taperIn, double taperOut, double size)
    static double breath(double breathe, double pr)
    static int[] emptyRect(int width, int height); static boolean empty(int[] r); static int[] union(int[] a, int[] b); static int[] grow(int[] r, int[] s)
}
```
`handDark(pDark, sDark, tDark, pr, v, lean)` = `(1 − .8·pDark·(1 − pr))(1 − .55·sDark·clamp(v/2.5))(1 − .45·tDark·lean)`, each factor only when its cell is on, so exactly 1 when all are off.

In `BrushDirection` add (keep everything else):
```java
/** The lean's direction in radians, as the prototype's tiltOf().dir: atan2(tan tiltY, tan tiltX); NaN without a dependable lean (under 5°, or no reading). */
static double azimuth(float tiltX, float tiltY)
```
Tilt in the engine is `BrushDirection.lean` (the prototype's `lean`) and `azimuth` (its `dir`); `tiltOf` returning null is `lean == 0` with `azimuth` NaN.

```java
/** The noise a stroke reads (spec "Stampers and buffers"). Pure. */
final class StrokePaper {
    StrokePaper(BrushTexture texture)
    float fine(int x, int y)   // grain.fine / 255, widened about .5 by grain.spread, clamped 0–1 (as WildInkStroke.tooth widens it)
    float mid(int x, int y)    // the same for mid
    float low(int x, int y)    // WatercolorSettle.Paper.low / 255
    float blob(int x, int y)   // WatercolorSettle.Paper.blob / 255
}
```

```java
/** Charcoal's own grain (engine.js 82–106): three tileable 512-px value noises from the brush's seed, the last six kept. Pure. */
final class StickGrain {
    static StickGrain of(long seed)                     // rng((seed ^ 0xc4a2c0a1) >>> 0): fine (cell 1.6), mid (7), low (36), in that order
    float fine(int x, int y); float mid(int x, int y); float low(int x, int y)   // index ((y & 511) << 9) | (x & 511)
}
```

- [ ] **Step 4: Run the checks**

Run: `bash scripts/test_inkwash.sh | grep -c '^PASS'` → `36`; `bash scripts/test_document.sh | grep -c '^PASS'` → `71` (`BrushDirection` is in both scripts).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/mpdairy/monopaint/{FloatTiles,StrokePoints,StrokeMath,StrokePaper,StickGrain,BrushDirection}.java app/src/test/java/io/github/mpdairy/monopaint/StrokeBasicsChecks.java scripts/test_inkwash.sh
git commit -m "Modular engine: float tiles, samples, tilt, noise and stick grain"
```

---

### Task 8: Line stampers

**Files:**
- Create: `LineStamper.java`, `Placement.java`, `LineStamperChecks.java`
- Modify: `scripts/test_inkwash.sh`

**Port:** engine.js 108–251 (`stampLine`, `splatterLive`, `splatterSeg`) with port.js 135–155 (`splatter`, the final splatter under 16 px); engine.js 475–602 (`stampPlaced`) with port.js 171–174 (`paintWidth`).

```java
/** The line (engine.js 113–251). Pure. */
final class LineStamper {
    /** What a stroke's stamps remember: the widest reach, the body's last dab distance, the hake's state. */
    static final class Meta { int widest; double lastD = Double.NEGATIVE_INFINITY; HakeRibbon.State hake; }
    /** Lay only dabs with dMin <= d < dMax; with a tail, taper them toward the end over tailLen by tailAmt. */
    static final class Window { double dMin, dMax = Double.POSITIVE_INFINITY, tailLen, tailAmt; boolean tail; }
    /** stampLine: into {@code into}, from segment {@code from}; last = the stroke is lifting; clip null for the page. Returns the touched rectangle. */
    static int[] stamp(FloatTiles into, StrokePaper paper, ResolvedBrush.Pen b, StrokePoints pts, double size, boolean last, int from, int[] clip, Meta meta, Window win)
    /** splatterLive (engine.js 229–231). */
    static void splatterLive(FloatTiles into, ResolvedBrush.Pen b, StrokePoints pts, double size, int from, int[] rect)
}
/** Lines placed inside the body (engine.js 491–602): split nib, multi-line, outline, squiggle, Offset, Chaos. Pure. */
final class Placement {
    /** stampPlaced. wetD: the Fast-wet distance map or null; wetGap: the body buffer the split's wet gap goes to, or null. */
    static int[] stamp(FloatTiles into, FloatTiles wetD, FloatTiles wetGap, StrokePaper paper, ResolvedBrush.Pen b, ResolvedBrush.Body body, StrokePoints pts, double size, double bodySize, boolean last, int from, LineStamper.Window win)
    /** port.js paintWidth: size × (tip + (1 − tip) · p^1.2), tip = lerp(.6, .06, pressure). */
    static double paintWidth(ResolvedBrush.Body body, double size, double p)
}
```

**Subtle points (keep them exactly):**
- Tilt (engine.js 152–161): `lean = BrushDirection.lean(tx, ty)` at the dab's interpolated tilt; `wd *= 1 + 1.2·tiltSize·lean`; when `tiltAngle > 0` and the azimuth is a number, `ang = turnToward(ang, azimuth + π/2, tiltAngle)`; `flat = 1 − .45·tiltAngle·lean`. Only computed when `tiltSize + tiltAngle + tDark > 0`.
- Speed turns the nib (163): `ang = turnToward(ang, atan2(dirY, dirX) + π/2, sAngle·clamp(v/1.5))`.
- Pressure → nib width (168): round tips (`aspect >= 1`) squash, `bb = max(.6, bb(1 − .45·pWidth·pr))`; shaped ones open, `bb = max(.6, min(a, bb(1 + 1.2·pWidth(pr − .4))))`.
- The grid (127, 170): `gridSp = inkBig > 1 ? max(.4, min(.08·size, 2·max(.6, size/2·aspect)/√(size/2))) : 0`; a dab is skipped when its distance shares a grid cell with the previous step's, except the stroke's first dab and, when lifting, its last.
- Fast's taper out (139–143), the window tail (145), wobble read at `((int)(x / big), (int)(y / big))` clamped to the page (146–147), pooling's big-brush form (148–151).
- Charcoal (171–219): `sw`, `press`, the grain-shaped edge with `StickGrain.of(seed)`, the dry-brush test against the stick's fine noise, and dust specks from `new WildBrush.Random((int)seed ^ (int)Math.round(d * 4) * (int)2654435761L)`, at most 24 a dab.
- Final splatter (222–226): under 16 px the port's `splatter` with `new WildBrush.Random((int)seed ^ count * (int)2654435761L ^ (int)Math.round(total * 16.0))` (as `WildInkStroke.splatter`); bigger, `splatterSeg` per segment from one such generator; while drawing in None (`fast`), `splatterLive` per new segment from `new WildBrush.Random((int)seed ^ k * (int)2654435761L)`.
- `stampPlaced`'s generators: bristles `seed ^ 0x9e37` (48 draws), loads/widths/phases `seed ^ 0x11e5` (5 × 4 draws, then sq0, sq1); the cross-noise `cn(j, c, x) = noise1(seed + j·1013 + c·7919, x)` on ints; Offset's drift `noise1(seed ^ 0x0ff5e7, …)`; the squiggle's `noise1(seed ^ 0x5a1, …)`. The phase noise reads `mid` at `((int)((cx + phase) % w), (int)((cy + phase) % h))` clamped, with w and h the page's.
- The split's gap (584–589) writes `wetGap.raise(px, py, cov·gp·wetGap·.8)` when given.
- Everything keeps the strongest coverage per pixel (`into.raise`); `if (buf[i] >= 1) continue` and `if (buf[i] >= ink) continue` are speed-ups that change nothing: keep them.

- [ ] **Step 1: Write the check**

`LineStamperChecks.java`:

```java
package io.github.mpdairy.monopaint;

public final class LineStamperChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        width(); tapers(); tiltWidens(); tiltTurns(); pressureDarkens(); grid(); windows(); charcoal(); splatter(); split(); multi(); outline();
        System.out.println("PASS: the line is as wide as its size, tapers in, widens and turns with tilt when the hand says so, lightens with light pressure, lays no gaps at 360 px, stays inside its window; charcoal is deterministic and fuzzy; flicks splatter; a split nib parts, a 3-line rake lays three lines and an outline two");
    }
    static final StrokePaper PAPER = new StrokePaper(BrushTexture.PLAIN);
    /** A plain round pen: a fresh Fineliner-like brush, every rough dial and hand cell off. */
    static ResolvedBrush.Pen pen(double size) {
        ModularBrush b = new ModularBrush("p", 7, ModularStyle.named("Fineliner"));
        b.set(BrushPart.LIFE, new ModularBrush.Part(PartChoice.DRYING));
        return ResolvedBrush.of(b, size, 1).pen;
    }
    /** A horizontal line at y from x0 to x1, 2 px and 5 ms a sample, at pressure travel p, tilted (tx, ty). */
    static StrokePoints line(float y, float x0, float x1, float p, float tx, float ty) {
        StrokePoints pts = new StrokePoints();
        for (float x = x0; x <= x1; x += 2) pts.add(x, y, p, (long)((x - x0) * 2.5), tx, ty);
        return pts;
    }
    static FloatTiles stamp(ResolvedBrush.Pen b, StrokePoints pts, double size) {
        FloatTiles t = new FloatTiles(1400, 900, 0);
        LineStamper.stamp(t, PAPER, b, pts, size, true, 1, null, new LineStamper.Meta(), null);
        return t;
    }
    static int rows(FloatTiles t, int x, float min) { int n = 0; for (int y = 0; y < 900; y++) if (t.get(x, y) >= min) n++; return n; }
    static void width() {
        FloatTiles t = stamp(pen(16), line(100, 40, 360, .5f, Float.NaN, Float.NaN), 16);
        check(Math.abs(rows(t, 200, .5f) - 16) <= 1 && t.get(200, 100) == 1 && t.get(200, 120) == 0, "A 16 px line is 16 px wide: " + rows(t, 200, .5f));
    }
    static void tapers() {
        ResolvedBrush.Pen b = pen(16); b.taperIn = 1;
        FloatTiles t = stamp(b, line(100, 40, 360, .5f, Float.NaN, Float.NaN), 16);
        check(rows(t, 44, .5f) < rows(t, 300, .5f) - 6, "Taper in starts it narrow");
    }
    static void tiltWidens() {
        ResolvedBrush.Pen b = pen(16); b.tiltSize = 1;
        int upright = rows(stamp(b, line(100, 40, 360, .5f, 0, 0), 16), 200, .5f), leaning = rows(stamp(b, line(100, 40, 360, .5f, 40, 0), 16), 200, .5f);
        check(upright <= 17 && leaning >= 25, "Tilt → size widens a leaning line (about 1 + 1.2 × lean): " + upright + " → " + leaning);
        check(rows(stamp(pen(16), line(100, 40, 360, .5f, 40, 0), 16), 200, .5f) <= 17, "and only when the hand says so");
    }
    static void tiltTurns() {
        ModularBrush m = new ModularBrush("c", 7, ModularStyle.named("Marker"));
        m.set(BrushPart.TIP, new ModularBrush.Part(PartChoice.CHISEL)); m.part(BrushPart.TIP).set(PartDial.ASPECT, .3); m.part(BrushPart.TIP).set(PartDial.ANGLE, 20);
        ResolvedBrush.Pen b = ResolvedBrush.of(m, 16, 1).pen;
        int still = rows(stamp(b, line(100, 40, 360, .5f, 40, 0), 16), 200, .5f);
        b.tiltAngle = 1;
        int turned = rows(stamp(b, line(100, 40, 360, .5f, 40, 0), 16), 200, .5f);
        check(turned > still + 3, "Tilt → angle turns a chisel's broad side across a horizontal stroke: " + still + " → " + turned);
    }
    static void pressureDarkens() {
        ResolvedBrush.Pen b = pen(16); b.pDark = 1;
        FloatTiles light = stamp(b, line(100, 40, 360, .1f, Float.NaN, Float.NaN), 16);
        check(light.get(200, 100) <= .29f && light.get(200, 100) > .25f, "Pressure → dark: a light touch lays 1 − .8(1 − p): " + light.get(200, 100));
    }
    static void grid() {
        FloatTiles t = stamp(pen(360), line(450, 40, 1360, .5f, Float.NaN, Float.NaN), 360);
        for (int x = 200; x <= 1200; x++) check(t.get(x, 450) == 1, "A 360 px line's grid dabs leave no gap at x " + x);
    }
    static void windows() {
        FloatTiles t = new FloatTiles(1400, 900, 0);
        LineStamper.Window w = new LineStamper.Window(); w.dMin = 100; w.dMax = 200;
        LineStamper.stamp(t, PAPER, pen(8), line(100, 40, 360, .5f, Float.NaN, Float.NaN), 8, false, 1, null, new LineStamper.Meta(), w);
        check(t.get(100, 100) == 0 && t.get(190, 100) == 1 && t.get(300, 100) == 0, "A window lays only its own stretch");
    }
    static void charcoal() {
        ResolvedBrush.Pen b = ResolvedBrush.of(ModularCodeChecks.fixture("soft-coal"), 30, 1).pen;
        FloatTiles one = stamp(b, line(100, 40, 360, .6f, Float.NaN, Float.NaN), 30), two = stamp(b, line(100, 40, 360, .6f, Float.NaN, Float.NaN), 30);
        int partial = 0;
        for (int y = 60; y < 140; y++) for (int x = 100; x < 300; x++) { check(one.get(x, y) == two.get(x, y), "Charcoal is the same every time"); float c = one.get(x, y); if (c > .05f && c < .95f) partial++; }
        check(partial > 400, "and fuzzy at its edges: " + partial);
    }
    static void splatter() {
        ResolvedBrush.Pen b = pen(8); b.splatter = 1;
        StrokePoints fast = new StrokePoints();
        for (int i = 0; i <= 60; i++) fast.add(40 + 6 * i, 100, .5f, i, Float.NaN, Float.NaN);
        FloatTiles t = stamp(b, fast, 8);
        int outside = 0;
        for (int y = 0; y < 900; y++) for (int x = 0; x < 1400; x++) if (t.get(x, y) > 0 && (Math.abs(y - 100) > 8 || x > 410)) outside++;
        check(outside > 0, "Fast flicks throw drops off the line");
        b.splatter = 0;
        FloatTiles none = stamp(b, fast, 8); int outsideNone = 0;
        for (int y = 0; y < 900; y++) for (int x = 0; x < 1400; x++) if (none.get(x, y) > 0 && (Math.abs(y - 100) > 8 || x > 410)) outsideNone++;
        check(outsideNone == 0, "and none without splatter");
    }
    static ResolvedBrush placed(ResolvedBrush.Lines lines, int count) {
        ModularBrush m = ModularCodeChecks.fixture("brave-onion");
        ResolvedBrush r = ResolvedBrush.of(m, 16, 1);
        r.pen.lines = lines; r.pen.count = count; r.pen.chaos = 0; r.pen.offset = 0; r.pen.wobble = 0; r.pen.spread = .8; r.pen.breathe = .4; r.pen.dry = 0;
        return r;
    }
    static FloatTiles place(ResolvedBrush r) {
        FloatTiles t = new FloatTiles(1400, 900, 0);
        // A body 120 px wide, so a 3-line rake's lines (about 13 px each) have room to stand apart.
        Placement.stamp(t, null, null, PAPER, r.pen, r.placeBody, line(200, 40, 600, .9f, Float.NaN, Float.NaN), r.inkSize, 120, true, 1, null);
        return t;
    }
    static int runs(FloatTiles t, int x) { int n = 0; boolean in = false; for (int y = 0; y < 900; y++) { boolean on = t.get(x, y) > .5f; if (on && !in) n++; in = on; } return n; }
    static void split() {
        FloatTiles t = place(placed(ResolvedBrush.Lines.SPLIT, 3));   // the gap: |across| under .72·g − .14 of the half-width is bare
        check(t.get(300, 200) < .5f && runs(t, 300) == 2, "A pressed split nib parts into two rails: " + runs(t, 300));
    }
    static void multi() { check(runs(place(placed(ResolvedBrush.Lines.SPREAD, 3)), 300) == 3, "A 3-line rake lays three lines"); }
    static void outline() { check(runs(place(placed(ResolvedBrush.Lines.OUTLINE, 3)), 300) == 2, "An outline lays two"); }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
```

- [ ] **Step 2: Run it to see it fail**

Add `LineStamper Placement HakeRibbon` to `sources=(...)` (`LineStamper.Meta` names `HakeRibbon.State`: create `HakeRibbon.java` now holding only `static final class State { }`; Task 9 fills it), `LineStamperChecks` to `checks=(...)`. Expected: compile errors.

- [ ] **Step 3: Port the stampers** as the signatures and subtle points above say.

- [ ] **Step 4: Run the checks**

Run: `bash scripts/test_inkwash.sh | grep -c '^PASS'` → `37`; `bash scripts/test_document.sh | grep -c '^PASS'` → `72`. The pixel counts in the messages are estimates from the prototype's formulas. If one is off by a pixel or two at an antialiased edge while the port matches the source line for line, widen that one tolerance and say why in the commit message; never change the port to fit a check.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/mpdairy/monopaint/{LineStamper,Placement,HakeRibbon}.java app/src/test/java/io/github/mpdairy/monopaint/LineStamperChecks.java scripts/test_inkwash.sh
git commit -m "Modular engine: the line, with tilt, charcoal, splatter and placed lines"
```

---

### Task 9: Body stampers, the hake and the settle

**Files:**
- Create: `BodyStamper.java`, `BodySettle.java`, `BodyStamperChecks.java`
- Modify: `HakeRibbon.java`, `scripts/test_inkwash.sh`

**Port:** engine.js 253–333 (`paintDiameter`, `stampBody`), 335–473 (`stampHake`, `laneShare`, `runOut`, `hakeDens`, `hakePut`, `hakeHead`, `hakeRibbon`), 718–775 (`settleBody`), stroke.js 1–74 (`settleBlend`).

```java
/** The body of paint (engine.js 257–333). Pure. */
final class BodyStamper {
    /** paintDiameter; lean 0 without tilt. */
    static double diameter(ResolvedBrush.Body b, double size, double pr, double lean)
    /** stampBody; a flat head goes to HakeRibbon. wetD: the Fast-wet distance map or null (writes the dab's d where it lays). */
    static int[] stamp(FloatTiles into, FloatTiles wetD, StrokePaper paper, ResolvedBrush.Body b, StrokePoints pts, double size, int from, LineStamper.Meta meta, boolean last)
}
/** The hake's swept ribbon (engine.js 335–473). Pure. */
final class HakeRibbon {
    static final class State { double next; Head prev; double th = Double.NaN, travel = Double.NaN; int k = 1; }
    static final class Head { double x, y, th, w, q, d, pr, c, s; }
    static int[] stamp(FloatTiles into, FloatTiles wetD, StrokePaper paper, ResolvedBrush.Body b, StrokePoints pts, double size, LineStamper.Meta meta, double softK)
}
/** The settle and the line-and-wash blend (engine.js 723–775, stroke.js 6–74). Pure. */
final class BodySettle {
    static final int BAND = 256;
    interface Prep { void apply(float[] p, int x0, int y0, int w, int h); }
    /** settleBody: reads src over rect (padded), writes dst (0–1.6) and, with impasto, relief. Returns the region covered. */
    static int[] settle(FloatTiles src, FloatTiles dst, FloatTiles relief, StrokePaper paper, ResolvedBrush.Look look, int[] rect, double size, Prep prep, int extraPad)
    /** The same, in bands of {@code band} rows (package-private, for the check). */
    static int[] settle(FloatTiles src, FloatTiles dst, FloatTiles relief, StrokePaper paper, ResolvedBrush.Look look, int[] rect, double size, Prep prep, int extraPad, int band)
    /** settleBlend: the line loosens in wet wash; writes L in place and the body into D. Returns the region covered. */
    static int[] blend(FloatTiles l, FloatTiles w, FloatTiles d, FloatTiles lineSave, FloatTiles bodySave, FloatTiles relief, StrokePaper paper, ResolvedBrush r, int[] rect)
}
```

**Subtle points:**
- Hake: `H.th === null` → `Double.isNaN(state.th)`; `H.travel !== undefined` → `!Double.isNaN(state.travel)`; the pivot on tight turns (engine.js 362–366), the slew limit `±1°` per px of step (367–368), the end-crossing fix (372–379), the in-between ribbons, at most 24 (381–392), the next head at `max(1.5, min(Q, 2 + .03W))` (396). `runOut` and `laneShare` exactly. `hakePut` writes `wetD` with the ribbon's or head's d.
- `stampBody`'s ellipse for shaped tips (303), tilt's ellipse (302), the big-brush gap rule (273–288, through `meta.lastD`), `liveGran` (324), skips for Offset (284).
- `settleBody`: `wet = 1 − clamp(cover)` fades feather, hollow, bloom and backruns; `K = paintBig(size)`; blooms and backruns read `low`/`blob` at `((int)(Y / K²), (int)(X / K²))` clamped to the page when `K > 1`; impasto writes `relief = impasto·(100·ridge − 90·edge)` with `ridge = D[j + rw + 1] − D[j − rw − 1]` (light from the upper left).
- **Bands:** the prototype settles the padded rectangle in one piece. Settle it in bands of at most `band` output rows, each band reading `over` extra rows above and below (clamped to the padded rectangle), `over = 2·(fr + rimR + 2·K + diffR + rT) + 8` where `fr`, `rimR = round((3 + 5·hollow)K)`, the impasto blur `max(2, round(3K))`, and the blend's `diffR` and `rT` are the blurs this settle runs; write only the band's own rows. Box blurs clamp at the array's edges, so with that overlap every written row equals the one-piece result. Impasto's first and last rows (relative to the whole rectangle) stay unwritten, as in the prototype.
- `blend`'s prep clears `lineSave` and `bodySave` at its end (stroke.js 70); with bands, do that clear once after all bands, in `blend`, not in the prep.

- [ ] **Step 1: Write the check**

`BodyStamperChecks.java`:

```java
package io.github.mpdairy.monopaint;

public final class BodyStamperChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        diameter(); disc(); hakeWidth(); hakeTurns(); settleIdentity(); bands(); hollow(); impasto(); cover(); blend();
        System.out.println("PASS: paint dabs are as wide as their diameter; the hake sweeps a ribbon its head's width, fixed or following, and pivots round a U-turn without gaps; an empty look settles to itself, banded or not; a hollow edge darkens the rim, impasto lights the top edge, cover keeps paint as laid; a line loosens into the wash it crosses");
    }
    static final StrokePaper PAPER = new StrokePaper(BrushTexture.PLAIN);
    static StrokePoints line(float y, float x0, float x1, float p) {
        StrokePoints pts = new StrokePoints();
        for (float x = x0; x <= x1; x += 2) pts.add(x, y, p, (long)((x - x0) * 2.5), Float.NaN, Float.NaN);
        return pts;
    }
    static int rows(FloatTiles t, int x, float min) { int n = 0; for (int y = 0; y < t.height; y++) if (t.get(x, y) >= min) n++; return n; }
    static ResolvedBrush wash() { return ResolvedBrush.of(ModularCodeChecks.fixture("wet-wash"), 60, 1); }
    static void diameter() {
        ResolvedBrush.Body b = wash().body;
        check(Math.abs(BodyStamper.diameter(b, 100, 1, 0) - 100) < 1e-9, "A full press reaches the size");
        ResolvedBrush.Body hake = ResolvedBrush.of(ModularCodeChecks.fixture("wide-hake"), 100, 1).body;
        check(Math.abs(BodyStamper.diameter(hake, 100, 0, 0) - 80) < 1e-9, "A flat head is 80% of the size at the lightest touch");
    }
    static void disc() {
        FloatTiles t = new FloatTiles(800, 400, 0);
        BodyStamper.stamp(t, null, PAPER, wash().body, line(200, 40, 700, 1), 60, 1, new LineStamper.Meta(), true);
        check(Math.abs(rows(t, 300, .001f) - 60) <= 3, "A wash at full press is 60 px wide, its soft edge included: " + rows(t, 300, .001f));
    }
    static int hakeRows(boolean fixed, double angle) {
        ResolvedBrush.Body b = ResolvedBrush.of(ModularCodeChecks.fixture("wide-hake"), 120, 1).body;
        b.headFixed = fixed; b.angle = angle;
        FloatTiles t = new FloatTiles(900, 500, 0);
        HakeRibbon.stamp(t, null, PAPER, b, line(250, 40, 800, .5f), 120, new LineStamper.Meta(), .2);
        return rows(t, 400, .2f);
    }
    static void hakeWidth() {
        int follow = hakeRows(false, 70), fixed = hakeRows(true, 20);
        check(follow > 95 && follow < 115, "A following head lies across the stroke: its width, about 106 px at half press: " + follow);
        check(fixed > 28 && fixed < 50 && follow > 2 * fixed, "A head fixed at 20° sweeps only about 2W·sin 20° plus its thickness: " + fixed);
    }
    static void hakeTurns() {
        ResolvedBrush.Body b = ResolvedBrush.of(ModularCodeChecks.fixture("wide-hake"), 120, 1).body;
        b.headFixed = false;
        StrokePoints u = new StrokePoints(); long t = 0;
        for (int x = 100; x <= 400; x += 2) u.add(x, 150, .5f, t += 5, Float.NaN, Float.NaN);
        for (int i = 1; i <= 90; i++) { double a = -Math.PI / 2 + Math.PI * i / 90; u.add((float)(400 + 40 * Math.cos(a)), (float)(190 + 40 * Math.sin(a)), .5f, t += 5, Float.NaN, Float.NaN); }
        for (int x = 398; x >= 100; x -= 2) u.add(x, 230, .5f, t += 5, Float.NaN, Float.NaN);
        FloatTiles tiles = new FloatTiles(700, 400, 0);
        HakeRibbon.stamp(tiles, null, PAPER, b, u, 120, new LineStamper.Meta(), .2);
        int covered = 0, n = 0;
        for (int i = 0; i < u.count; i += 3, n++) if (tiles.get((int)u.x[i], (int)u.y[i]) > .3f) covered++;
        check(covered >= .95 * n, "The hake pivots round a tight U-turn without leaving its path bare: " + covered + " of " + n);
    }
    static FloatTiles blob(int w, int h) {
        FloatTiles t = new FloatTiles(w, h, 0);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) { double d = Math.hypot(x - w / 2.0, y - h / 2.0); if (d < 40) t.set(x, y, 1); else if ((x * 7 + y * 13) % 11 == 0 && x > 20 && y > 20 && x < w - 20 && y < h - 20) t.set(x, y, .5f); }
        return t;
    }
    static void settleIdentity() {
        FloatTiles src = blob(200, 200), dst = new FloatTiles(200, 200, 0);
        BodySettle.settle(src, dst, null, PAPER, new ResolvedBrush.Look(), new int[]{20, 20, 179, 179}, 60, null, 0);
        for (int y = 0; y < 200; y++) for (int x = 0; x < 200; x++) check(dst.get(x, y) == src.get(x, y), "An empty look settles paint to itself");
    }
    static void bands() {
        FloatTiles src = blob(300, 700);
        ResolvedBrush.Look look = new ResolvedBrush.Look(); look.feather = .5; look.hollow = .5; look.bloom = .3; look.backrun = .3; look.gran = .2; look.impasto = .5;
        FloatTiles a = new FloatTiles(300, 700, 0), b = new FloatTiles(300, 700, 0), ra = new FloatTiles(300, 700, 0), rb = new FloatTiles(300, 700, 0);
        int[] rect = {30, 30, 269, 669};
        BodySettle.settle(src, a, ra, PAPER, look, rect, 300, null, 0, 64);
        BodySettle.settle(src, b, rb, PAPER, look, rect, 300, null, 0, 10_000);
        for (int y = 0; y < 700; y++) for (int x = 0; x < 300; x++) check(a.get(x, y) == b.get(x, y) && ra.get(x, y) == rb.get(x, y), "Settling in bands gives the one-piece result at " + x + "," + y);
    }
    static void hollow() {
        FloatTiles src = new FloatTiles(200, 200, 0), dst = new FloatTiles(200, 200, 0);
        for (int y = 0; y < 200; y++) for (int x = 0; x < 200; x++) if (Math.hypot(x - 100, y - 100) < 40) src.set(x, y, 1);
        ResolvedBrush.Look look = new ResolvedBrush.Look(); look.hollow = 1;
        BodySettle.settle(src, dst, null, PAPER, look, new int[]{55, 55, 145, 145}, 60, null, 0);
        check(dst.get(100, 100) < dst.get(137, 100), "A hollow edge pools pigment at the rim: " + dst.get(100, 100) + " < " + dst.get(137, 100));
    }
    static void impasto() {
        FloatTiles src = new FloatTiles(200, 200, 0), dst = new FloatTiles(200, 200, 0), relief = new FloatTiles(200, 200, 0);
        for (int y = 80; y < 120; y++) for (int x = 40; x < 160; x++) src.set(x, y, 1);
        ResolvedBrush.Look look = new ResolvedBrush.Look(); look.impasto = 1; look.cover = 1;
        BodySettle.settle(src, dst, relief, PAPER, look, new int[]{40, 80, 159, 119}, 60, null, 0);
        check(relief.get(100, 80) > 0 && relief.get(100, 119) < 0, "Impasto is lit from the upper left: the top edge lighter, the bottom darker");
    }
    static void cover() {
        FloatTiles src = blob(200, 200), dst = new FloatTiles(200, 200, 0);
        ResolvedBrush.Look look = new ResolvedBrush.Look(); look.cover = 1; look.feather = 1; look.hollow = 1; look.bloom = 1; look.backrun = 1;
        BodySettle.settle(src, dst, null, PAPER, look, new int[]{20, 20, 179, 179}, 60, null, 0);
        for (int y = 0; y < 200; y++) for (int x = 0; x < 200; x++) check(dst.get(x, y) == src.get(x, y), "Opaque paint keeps none of the wet effects");
    }
    static void blend() {
        ResolvedBrush r = ResolvedBrush.of(ModularCodeChecks.fixture("rake"), 16, 1);
        FloatTiles l = new FloatTiles(400, 300, 0), w = new FloatTiles(400, 300, 0), d = new FloatTiles(400, 300, 0), ls = new FloatTiles(400, 300, 0), bs = new FloatTiles(400, 300, 0);
        for (int y = 100; y < 200; y++) for (int x = 100; x < 300; x++) w.set(x, y, .8f);
        for (int y = 148; y < 152; y++) for (int x = 60; x < 340; x++) l.set(x, y, 1);
        double before = 0, after = 0;
        for (int y = 140; y < 160; y++) for (int x = 120; x < 280; x++) before += l.get(x, y);
        BodySettle.blend(l, w, d, ls, bs, null, PAPER, r, new int[]{60, 100, 339, 199});
        for (int y = 140; y < 160; y++) for (int x = 120; x < 280; x++) after += l.get(x, y);
        check(d.get(200, 150) > 0 && d.get(200, 120) > 0, "The blend settles the body into D");
        check(ls.get(200, 150) == 0 && bs.get(200, 150) == 0, "and clears what it saved");
        check(after != before, "A line crossing wet wash loosens: " + before + " → " + after);
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
```

- [ ] **Step 2: Run it to see it fail**

Add `BodyStamper BodySettle` to `sources=(...)`, `BodyStamperChecks` to `checks=(...)`. Expected: compile errors.

- [ ] **Step 3: Port** as above.

- [ ] **Step 4: Run the checks**

Run: `bash scripts/test_inkwash.sh | grep -c '^PASS'` → `38`; `bash scripts/test_document.sh | grep -c '^PASS'` → `73`. The same rule as Task 8 applies to the width estimates.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/mpdairy/monopaint/{BodyStamper,HakeRibbon,BodySettle}.java app/src/test/java/io/github/mpdairy/monopaint/BodyStamperChecks.java scripts/test_inkwash.sh
git commit -m "Modular engine: paint bodies, the hake ribbon and the settle"
```

---

### Task 10: The stroke on the page: compose, None and Drying

**Files:**
- Create: `ModularCompose.java`, `ModularStroke.java`, `ModularStrokeChecks.java`
- Modify: `ToneDocument.java` (add `coverTones`; `test_document.sh` already compiles it, and its `DocumentChecks`, `MaskChecks` and `LayerChecks` must keep passing), `scripts/test_inkwash.sh`

**Port:** stroke.js 391–441 (`composeStroke`), 455–701 (`Stroke`: constructor, `stampL`, `stampW`, `mergeLineWet`, `boxOf`, `add`, `end`) without Carry, water and the Fill plane. Fast-wet (`wetLine`, `wetBody`) is Task 11: until then `Life.WET` takes the None path.

```java
/** A stroke's tones over the tones the gesture found (stroke.js 396–441), written to the page. Pure. */
final class ModularCompose {
    enum Phase { PREVIEW, FINAL }
    /** Composes rect: reads strokeBaseTones, writes setTones (and coverTones where cover lays paint); a pixel with nothing whose tone differs from its base is restored. */
    static void compose(ToneDocument document, ResolvedBrush r, StrokePaper paper, FloatTiles l, FloatTiles body, FloatTiles relief, int[] rect, Phase phase)
}
/** A six-part brush's stroke (stroke.js Stroke). Pure. */
final class ModularStroke implements DrawingStroke {
    /** {@code brush} at {@code size} page pixels and {@code darkness} 0–1 on the document's active layer; {@code session} receives a Drying wash's settling (Task 15), or null for none (samples). */
    ModularStroke(ToneDocument document, ModularBrush brush, double size, double darkness, WatercolorSession session)
    @Override public void sample(float x, float y, float pressure, float tiltX, float tiltY)          // UNTIMED
    @Override public void sample(float x, float y, float pressure, float tiltX, float tiltY, long time)
    @Override public boolean finish()
}
```

**Compose (per pixel, rect clipped to the page, row by row with `int[]` rows reused, never allocated per sample):**
- `under` = `document.strokeBaseTones` for the row; `c = l.get`, `d = body.get` (body = W in PREVIEW and in None, D otherwise).
- Nothing (`c <= 0 && d <= 0`): `-1` unless `document.tone(x, y) != under`, then the run is restored with `document.restoreSpan(x0, x1, y)`.
- Body, PREVIEW: `t = under − (under − min(200, under·.82))·shade`; with cover `lerp(t, lerp(under, gray, min(1, d)), cover)`. FINAL: `dd = min(1.6, d)`, `glaze = under·(1 − min(1, dd·washK))` (the `Math.fround` is the float cast), with cover `lerp(glaze, lerp(under, gray, min(1, dd/bodyK)), cover)`, `bodyK = lerp(1, .4, clamp(impasto))`; relief added when FINAL and impasto > 0, clamped 0–255.
- Line: INK with `lineTooth > 0`: `n = fine·.65 + mid·.35`, `a = clamp((c − (.5 + tooth·.9·(n − .5)))·4 + .5)`. CHARCOAL (stroke.js 426–429) with `StickGrain.of(r.pen.seed)` and `ex = 1 − under/255`. GRAPHITE (430–434) with `fl = 0` (no Fill plane). Then `tone = tone·(1 − (float)(a·inkK))`.
- Writes: the rounded tone; where cover > 0 and d > 0 the coverage `round(255·cover·min(1, dd/bodyK))` (PREVIEW: `min(1, d)`) goes in a second row and the row is written with `coverTones`, otherwise `setTones`.

**`ToneDocument.coverTones(int x, int y, int w, int h, int[] grays, int[] coverages)`:** as `setTones`, but each pixel's coverage is `max(alpha, 255 − gray, coverages[i])` and its raw tone `rawTone(gray, coverage)`; a negative gray leaves the pixel; masking fluid: per pixel through `setPixel(x, y, rawTone(gray, coverage), coverage)` (which shields). Javadoc: "Opaque paint: covers what lies beneath on this layer and the layers below by at least {@code coverages}."

**The stroke:**
- Constructor: `r = ResolvedBrush.of(brush, size, darkness)`, `paper = new StrokePaper(r.paper)`, `pts`, buffers `L`, `W`, `D` (and lazily `relief`, `lineSave`, `bodySave`) as `FloatTiles(document.width, document.height, 0)`, `mL`, `mW` (`LineStamper.Meta`), `rect` = empty; then `document.begin()` (last, as the other strokes).
- `sample`: non-finite x, y or pressure → return; `pts.add(x, y, ToolSettings.travel(pressure), time, tiltX, tiltY)`; nothing new or fewer than 2 points → return. Then stroke.js 633–651 for NONE and DRY (and WET until Task 11): stamp the line (`stampL`: HAKE → `HakeRibbon.stamp` with `r.lineHake`, size `r.inkSize`, `mL`, `r.lineHake.softK`; placed → `Placement.stamp` with `wetGap = r.body != null ? W : null`, then `mL.widest = max(mL.widest, r.lineReach)`; else `LineStamper.stamp`), stamp the body (`stampW`: PLACED → `Placement.stamp` with `r.bodyPen`, `r.bodyGeom`, `r.bodyLineSize`, `r.bodyPlaceSize`; else `BodyStamper.stamp` with `r.body`, `r.bodySize`, `mW`), in None with both a line and a body `mergeLineWet` (stroke.js 496–505, into W), then `ModularCompose.compose(…, life == DRY ? PREVIEW : FINAL)` over the new rectangle, growing `rect`. `done = pts.count`.
- `finish()`: a single point becomes a dot (stroke.js 659); in DRY (stroke.js 664–681): clear L and re-stamp it with `last = true` (unless `lineStateful`), clear W and re-stamp (unless `bodyStateful`), settle (`BodySettle.blend` when there is a line, else `BodySettle.settle` into D), then compose FINAL over everything touched. Keep the held-back share for Task 15: `f = cover > 0 ? 0 : .45·settle`; D is multiplied by `1 − f` before composing (stroke.js 671–677) — store the pre-multiplied darkness block (`min(1, d·washK)`) in a field `pendingWet` for Task 15, unused until then. Then drop the buffers and `return document.finish()`.
- `holdsWetPaint()` stays false.

- [ ] **Step 1: Write the check**

`ModularStrokeChecks.java`:

```java
package io.github.mpdairy.monopaint;

import java.util.Arrays;

public final class ModularStrokeChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        draws(); oneUndo(); noneIsFinal(); dryingSettlesAtLift(); neverLighter(); coverCovers(); coverHidesBelow(); deterministic(); masked(); fallbacks(); coverTones();
        System.out.println("PASS: a six-part stroke draws on its layer as one undo step; None is final as laid, Drying previews then settles at lift; ink never lightens what's beneath, cover lays light over dark and hides the layers below; masking fluid shields it; Bristles and Carry draw as their fallbacks");
    }
    static ToneDocument page(int layer) { ToneDocument d = InkWashLayers.blank(480, 360); d.selectLayer(layer); return d; }
    static ModularBrush fixture(String name) { return ModularCodeChecks.fixture(name); }
    static ModularBrush life(ModularBrush b, PartChoice life) { b.set(BrushPart.LIFE, new ModularBrush.Part(life)); return b; }
    static ModularStroke wave(ToneDocument d, ModularBrush b, double size, double darkness) {
        ModularStroke s = new ModularStroke(d, b, size, darkness, new WatercolorSession());
        for (int i = 0; i <= 120; i++) {
            double t = i / 120.0;
            s.sample((float)(40 + 400 * t), (float)(180 + 60 * Math.sin(2 * Math.PI * t)), ToolSettings.pressure((float)(.2 + .7 * Math.sin(Math.PI * t))), 0, 0, i * 5L);
        }
        return s;
    }
    static byte[][] layers(ToneDocument d) { ToneDocument.Snapshot s = d.layerSnapshot(); byte[][] out = new byte[4][]; for (int i = 0; i < 2; i++) { out[2 * i] = s.layers.get(i).tones.clone(); out[2 * i + 1] = s.layers.get(i).alpha.clone(); } return out; }
    static boolean same(byte[][] a, byte[][] b) { for (int i = 0; i < a.length; i++) if (!Arrays.equals(a[i], b[i])) return false; return true; }
    static void draws() {
        ToneDocument d = page(InkWashLayers.INK);
        wave(d, life(fixture("brave-onion"), PartChoice.NONE), 16, 1).finish();
        int dark = 0; for (int y = 0; y < 360; y++) for (int x = 0; x < 480; x++) if (d.compositeTone(x, y) < 128) dark++;
        check(dark > 2000 && d.compositeTone(240, 20) == 255, "A six-part line draws along its path, paper elsewhere: " + dark);
        check(!d.layerDarkness(InkWashLayers.WASH, 0, 0, 480, 360, new int[480 * 360]), "on its own layer only");
    }
    static void oneUndo() {
        ToneDocument d = page(InkWashLayers.WASH);
        wave(d, fixture("wet-wash"), 60, 1).finish();
        check(d.compositeTone(240, 180) < 255, "A wash draws");
        d.undo();
        for (int y = 0; y < 360; y += 7) for (int x = 0; x < 480; x += 7) check(d.compositeTone(x, y) == 255, "and one Undo takes it all back");
    }
    static void noneIsFinal() {
        for (String name : new String[]{"soft-coal", "wide-hake", "rake", "hb-pencil"}) {
            ToneDocument d = page(name.equals("wide-hake") ? InkWashLayers.WASH : InkWashLayers.INK);
            ModularStroke s = wave(d, life(fixture(name), PartChoice.NONE), 24, 1);
            byte[][] before = layers(d);
            s.finish();
            check(same(before, layers(d)), "Life None: nothing changes at lift (" + name + ")");
        }
    }
    static void dryingSettlesAtLift() {
        ToneDocument d = page(InkWashLayers.WASH);
        ModularStroke s = wave(d, fixture("wet-wash"), 60, 1);
        int preview = d.compositeTone(240, 180);
        check(preview == WashStroke.preview(255, 1), "Drying previews as watercolor does: " + preview);
        s.finish();
        check(d.compositeTone(240, 180) != preview, "and settles at lift");
        ModularBrush tapered = life(fixture("brave-onion"), PartChoice.DRYING);
        tapered.part(BrushPart.TIP).set(PartDial.TAPER_OUT, 1);
        ToneDocument e = page(InkWashLayers.INK);
        ModularStroke t = wave(e, tapered, 16, 1);
        byte[][] before = layers(e);
        t.finish();
        check(!same(before, layers(e)), "A Drying line's taper out is laid at lift");
    }
    static void neverLighter() {
        ToneDocument d = page(InkWashLayers.INK);
        d.begin(); for (int y = 150; y < 210; y++) for (int x = 100; x < 400; x++) d.setTone(x, y, 100); d.finish();
        int[] before = new int[480 * 360]; for (int i = 0; i < before.length; i++) before[i] = d.compositeTone(i % 480, i / 480);
        wave(d, life(fixture("brave-onion"), PartChoice.NONE), 16, .31).finish();
        for (int i = 0; i < before.length; i++) check(d.compositeTone(i % 480, i / 480) <= before[i], "Ink never lightens what it crosses");
    }
    static ModularBrush gouache() {
        ModularBrush b = new ModularBrush("g", 3, ModularStyle.named("Gouache"));
        b.set(BrushPart.MEDIUM, new ModularBrush.Part(PartChoice.COVER));
        return life(b, PartChoice.NONE);
    }
    static void coverCovers() {
        ToneDocument d = page(InkWashLayers.WASH);
        d.begin(); for (int y = 100; y < 260; y++) for (int x = 100; x < 400; x++) d.setTone(x, y, 40); d.finish();
        wave(d, gouache(), 60, .31).finish();
        int tone = d.compositeTone(240, 180), gray = (int)Math.round(255 * .69);
        check(Math.abs(tone - gray) <= 3 && d.opacity(240, 180) == 255, "Light gouache covers dark on its layer: " + tone);
    }
    static void coverHidesBelow() {
        ToneDocument d = InkWashLayers.blank(480, 360);
        d.selectLayer(InkWashLayers.WASH); d.begin(); for (int y = 100; y < 260; y++) for (int x = 100; x < 400; x++) d.setTone(x, y, 40); d.finish();
        d.selectLayer(InkWashLayers.INK);
        wave(d, gouache(), 60, .31).finish();
        check(Math.abs(d.compositeTone(240, 180) - Math.round(255 * .69)) <= 3, "and hides the layers below");
    }
    static void deterministic() {
        String[] sums = new String[2];
        for (int i = 0; i < 2; i++) { ToneDocument d = page(InkWashLayers.INK); wave(d, fixture("rake"), 16, 1).finish(); sums[i] = LegacyGoldenChecks.crc(d); }
        check(sums[0].equals(sums[1]), "The same brush and samples draw the same pixels");
    }
    static void masked() {
        ToneDocument d = page(InkWashLayers.INK);
        d.paintMaskingFluid(true); d.begin(); for (int y = 0; y < 360; y++) for (int x = 200; x < 280; x++) d.paintTone(x, y, 0, 255); d.finish(); d.paintMaskingFluid(false);
        wave(d, life(fixture("brave-onion"), PartChoice.NONE), 16, 1).finish();
        d.peelMask();
        for (int y = 0; y < 360; y++) for (int x = 200; x < 280; x++) check(d.compositeTone(x, y) == 255, "Masking fluid shields a six-part stroke");
    }
    static void fallbacks() {
        ModularBrush bristles = life(ModularCode.decode("test-tip.Md6rog7js5ei0_000zz0000000000000_0_000_0_0000000000000000000nz0.9ix"), PartChoice.NONE);
        ToneDocument d = page(InkWashLayers.INK); wave(d, bristles, 16, 1).finish();
        int marked = 0; for (int y = 0; y < 360; y++) for (int x = 0; x < 480; x++) if (d.compositeTone(x, y) < 200) marked++;
        check(marked > 1000, "Bristles draw (as a round tip): " + marked);
        ModularBrush drag = life(ModularCode.decode("test-carry.Md0i000000000at_000zz0000000000000_2llp6ul_000_0_0000000000000000000nz0.9ix"), PartChoice.NONE), off = drag.copy();
        off.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.OFF));
        ToneDocument a = page(InkWashLayers.INK), b = page(InkWashLayers.INK);
        wave(a, drag, 16, 1).finish(); wave(b, off, 16, 1).finish();
        check(LegacyGoldenChecks.crc(a).equals(LegacyGoldenChecks.crc(b)), "Carry draws as off");
    }
    static void coverTones() {
        ToneDocument d = page(InkWashLayers.WASH);
        d.begin(); d.coverTones(10, 10, 2, 1, new int[]{200, -1}, new int[]{255, 255}); d.finish();
        check(d.compositeTone(10, 10) == 200 && d.opacity(10, 10) == 255 && d.compositeTone(11, 10) == 255 && d.opacity(11, 10) == 0, "coverTones lays opaque tone; a negative gray leaves its pixel");
        d.begin(); d.coverTones(10, 10, 1, 1, new int[]{220}, new int[]{40}); d.finish();
        check(d.compositeTone(10, 10) == 220 && d.opacity(10, 10) == 255, "and never lowers the coverage already there");
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
```

(`d.opacity(x, y)` reads the active layer, which is the one each check painted.)

- [ ] **Step 2: Run it to see it fail**

Add `ModularCompose ModularStroke` to `sources=(...)`, `ModularStrokeChecks` to `checks=(...)`. Expected: compile errors.

- [ ] **Step 3: Add `coverTones` to `ToneDocument`, write `ModularCompose` and `ModularStroke`** as above.

- [ ] **Step 4: Run all checks**

Run: `bash scripts/test_inkwash.sh | grep -c '^PASS'` → `39`; `bash scripts/test_document.sh | grep -c '^PASS'` → `74` (DocumentChecks, MaskChecks, LayerChecks unchanged and passing; WashGoldenChecks and LegacyGoldenChecks unchanged and passing).

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleRelease` → `BUILD SUCCESSFUL` (nothing calls the new classes yet; this proves they compile for Android, Java 8).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/io/github/mpdairy/monopaint/{ModularCompose,ModularStroke,ToneDocument}.java app/src/test/java/io/github/mpdairy/monopaint/ModularStrokeChecks.java scripts/test_inkwash.sh
git commit -m "Modular engine: six-part strokes on the page, final as laid or settled at lift"
```

---

### Task 11: Fast-wet, and the timing check

**Files:**
- Modify: `ModularStroke.java`, `scripts/test_inkwash.sh`
- Create: `FastWetChecks.java`, `ModularBenchChecks.java`

**Port:** stroke.js 461–462 (`WET_FRAME_MS = 330`, `firstAt`), 496–505 (`mergeLineWet` into rawW with the distance map), 506–533 (`wetLine`), 534–539 (`boxOf`), 540–594 (`wetBody`), and the WET branch of `add` (635–637).

**App differences:**
- Times are the samples' (`pts.t`), as the prototype's. Without times (`PenSpeed.UNTIMED`), `wetLine` re-tapers every sample (the 50 ms test counts as passed) and `wetBody` re-settles only by distance (`total − lastSettleD >= .3·windowLen`).
- `rawL`, `rawW`, `wetD` (fill −1) and `relief` are `FloatTiles`, made on first use. `wetBody`'s local arrays (`P`, `Wt`, the blurs) are sized to its window box, which is small (at most the window plus the brush's reach).
- Count each full re-taper (the branch past the 50 ms test, stroke.js 521) in a package-private `int retapers`.
- In Fast-wet, `finish()` only makes a tap a dot and calls `document.finish()`: nothing else changes at lift.

- [ ] **Step 1: Write the checks**

`FastWetChecks.java`:

```java
package io.github.mpdairy.monopaint;

public final class FastWetChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        nothingAtLift(); finalBehind(); tailTapersWhileDrawing(); hollowBehind(); untimed(); paced();
        System.out.println("PASS: Fast-wet changes nothing at lift; what lies behind its window is final; the tail tapers and the wash develops while the pen is down; strokes without times still taper; the tail re-tapers at most every 50 ms");
    }
    static ModularBrush wet(String name) { return ModularStrokeChecks.life(ModularCodeChecks.fixture(name), PartChoice.FAST_WET); }
    static ToneDocument page(ModularBrush b) {
        PartChoice m = b.choice(BrushPart.MEDIUM);
        return ModularStrokeChecks.page(m == PartChoice.WASH || m == PartChoice.COVER ? InkWashLayers.WASH : InkWashLayers.INK);
    }
    static void nothingAtLift() {
        for (String name : new String[]{"brave-onion", "wet-wash", "thick-oil", "rake", "wide-hake"}) {
            ModularBrush b = wet(name);
            ToneDocument d = page(b);
            ModularStroke s = ModularStrokeChecks.wave(d, b, name.equals("brave-onion") || name.equals("rake") ? 16 : 60, 1);
            byte[][] before = ModularStrokeChecks.layers(d);
            s.finish();
            check(ModularStrokeChecks.same(before, ModularStrokeChecks.layers(d)), "Fast-wet: nothing changes at lift (" + name + ")");
        }
    }
    static void finalBehind() {
        ModularBrush b = wet("wet-wash");
        ToneDocument d = InkWashLayers.blank(1400, 400); d.selectLayer(InkWashLayers.WASH);
        ModularStroke s = new ModularStroke(d, b, 60, 1, new WatercolorSession());
        int[] early = null;
        for (int i = 0; i <= 600; i++) {
            s.sample(40 + 2 * i, 200, ToolSettings.pressure(.7f), 0, 0, i * 5L);
            if (i == 300) { early = new int[120 * 200]; for (int y = 100; y < 300; y++) for (int x = 40; x < 160; x++) early[(y - 100) * 120 + x - 40] = d.compositeTone(x, y); }
        }
        s.finish();
        for (int y = 100; y < 300; y++) for (int x = 40; x < 160; x++) check(d.compositeTone(x, y) == early[(y - 100) * 120 + x - 40], "What lies far behind the window never changes again");
    }
    static void tailTapersWhileDrawing() {
        ModularBrush b = wet("brave-onion");
        b.set(BrushPart.TIP, new ModularBrush.Part(PartChoice.ROUND)); b.part(BrushPart.TIP).set(PartDial.TAPER_OUT, 1);
        b.set(BrushPart.MEDIUM, new ModularBrush.Part(PartChoice.INK));
        ToneDocument d = ModularStrokeChecks.page(InkWashLayers.INK);
        ModularStroke s = new ModularStroke(d, b, 16, 1, new WatercolorSession());
        for (int i = 0; i <= 150; i++) { float p = i < 120 ? .7f : .7f * (150 - i) / 30f; s.sample(40 + 2.5f * i, 180, ToolSettings.pressure(p), 0, 0, i * 8L); }
        int middle = rows(d, 150), end = rows(d, 410);
        check(end < middle - 4, "The tail tapers while the pen is still down: " + middle + " → " + end);
        s.finish();
        check(rows(d, 410) == end, "and stays so at lift");
    }
    static int rows(ToneDocument d, int x) { int n = 0; for (int y = 0; y < d.height; y++) if (d.compositeTone(x, y) < 128) n++; return n; }
    static void hollowBehind() {
        ModularBrush b = wet("wet-wash");
        b.part(BrushPart.MEDIUM).set(PartDial.POOL, 1);
        ToneDocument d = InkWashLayers.blank(1000, 400); d.selectLayer(InkWashLayers.WASH);
        ModularStroke s = new ModularStroke(d, b, 80, 1, new WatercolorSession());
        for (int i = 0; i <= 400; i++) s.sample(40 + 2 * i, 200, ToolSettings.pressure(1), 0, 0, i * 10L);
        s.finish();
        int y0 = 200, rim = 255, middle = d.compositeTone(200, y0);
        for (int y = 160; y < 200; y++) rim = Math.min(rim, d.compositeTone(200, y));
        check(rim < middle, "Behind the nib the wash develops its hollow edge while drawing: rim " + rim + " vs middle " + middle);
    }
    static void untimed() {
        ModularBrush b = wet("brave-onion");
        b.set(BrushPart.TIP, new ModularBrush.Part(PartChoice.ROUND)); b.part(BrushPart.TIP).set(PartDial.TAPER_OUT, 1);
        b.set(BrushPart.MEDIUM, new ModularBrush.Part(PartChoice.INK));
        ToneDocument d = ModularStrokeChecks.page(InkWashLayers.INK);
        ModularStroke s = new ModularStroke(d, b, 16, 1, new WatercolorSession());
        for (int i = 0; i <= 150; i++) { float p = i < 120 ? .7f : .7f * (150 - i) / 30f; s.sample(40 + 2.5f * i, 180, ToolSettings.pressure(p), 0, 0); }
        s.finish();
        check(rows(d, 410) < rows(d, 150) - 4, "A stroke without times still tapers");
    }
    static void paced() {
        ModularBrush b = wet("brave-onion");
        b.set(BrushPart.TIP, new ModularBrush.Part(PartChoice.ROUND)); b.part(BrushPart.TIP).set(PartDial.TAPER_OUT, 1);
        ToneDocument d = ModularStrokeChecks.page(InkWashLayers.INK);
        ModularStroke s = new ModularStroke(d, b, 16, 1, new WatercolorSession());
        for (int i = 0; i <= 300; i++) s.sample(40 + i, 180, ToolSettings.pressure(.6f), 0, 0, i);
        check(s.retapers >= 2 && s.retapers <= 300 / 50 + 1, "The tail re-tapers at most every 50 ms of pen time: " + s.retapers);
        s.finish();
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
```

`ModularBenchChecks.java`:

```java
package io.github.mpdairy.monopaint;

import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

/**
 * Desktop timing of every style's brush, Fast-wet, on a 1400 × 900 wave with tilt, three samples an event, after a
 * warm-up stroke (spec "Live drawing on e-ink"). Budgets are about twice the prototype's browser numbers; the Manta is
 * 3–5× slower than this Mac. Any argument prints the table. If a budget fails on a busy machine, run it again once.
 */
public final class ModularBenchChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        StringBuilder table = new StringBuilder();
        for (ModularStyle style : ModularStyle.all()) {
            ModularBrush b = style.roll(new Random(100 + style.index));
            ModularBrush.Part life = new ModularBrush.Part(PartChoice.FAST_WET); b.set(BrushPart.LIFE, life);
            for (double size : new double[]{16, 360}) {
                run(b, size);
                double[] t = run(b, size);
                table.append(String.format(Locale.ROOT, "%-13s %-28s %4.0f px  mean %6.3f ms  p95 %6.3f ms  lift %5.1f ms%n", style.name,
                        ModularWords.choiceWords(b, BrushPart.TIP) + " · " + ModularWords.choiceWords(b, BrushPart.MEDIUM), size, t[0], t[1], t[2]));
                check(size <= 16 ? t[0] <= .3 && t[1] <= 1.5 : t[0] <= 3 && t[1] <= 15, style.name + " at " + size + " px is within budget:\n" + table);
                check(t[2] <= 30, style.name + " lifts within 30 ms:\n" + table);
            }
        }
        ModularBrush wc = ModularStyle.named("Watercolor").roll(new Random(5));
        ModularBrush.Part drying = new ModularBrush.Part(PartChoice.DRYING); drying.set(PartDial.SETTLE, .5); wc.set(BrushPart.LIFE, drying);
        for (double size : new double[]{16, 360}) { run(wc, size); double[] t = run(wc, size); check(t[2] <= 60, "A Drying Watercolor lifts within 60 ms at " + size + " px: " + t[2]); }
        if (args.length > 0) System.out.print(table);
        System.out.println("PASS: every style draws a 1400 × 900 wave within the desktop budgets (16 px: mean ≤ 0.3 ms, p95 ≤ 1.5 ms a sample; 360 px: mean ≤ 3 ms, p95 ≤ 15 ms; lift ≤ 30 ms, a Drying lift ≤ 60 ms)");
    }
    /** {mean ms a sample, p95 ms a sample, lift ms}. */
    static double[] run(ModularBrush b, double size) {
        PartChoice m = b.choice(BrushPart.MEDIUM);
        ToneDocument d = InkWashLayers.blank(1400, 900);
        d.selectLayer(m == PartChoice.WASH || m == PartChoice.COVER ? InkWashLayers.WASH : InkWashLayers.INK);
        ModularStroke s = new ModularStroke(d, b, size, 1, new WatercolorSession());
        int n = (int)Math.ceil(1320 / 3.0);
        double[] per = new double[(n + 3) / 3]; int events = 0; long time = 0;
        for (int i = 0; i <= n; i += 3) {
            long start = System.nanoTime();
            for (int j = i; j < Math.min(n + 1, i + 3); j++) {
                double u = (double)j / n, env = Math.sin(Math.PI * u);
                time += Math.round(2 + 4 * (1 - env));
                s.sample((float)(40 + 1320 * u), (float)(450 + (size > 100 ? 220 : 300) * Math.sin(2.5 * Math.PI * u)), ToolSettings.pressure((float)(.15 + .75 * Math.pow(env, .7))),
                        (float)(30 * Math.cos(3 * u)), 25, time);
            }
            if (events < per.length) per[events++] = (System.nanoTime() - start) / 3e6;
        }
        long lift = System.nanoTime();
        s.finish();
        double liftMs = (System.nanoTime() - lift) / 1e6;
        double[] sorted = Arrays.copyOf(per, events); Arrays.sort(sorted);
        double sum = 0; for (double v : sorted) sum += v;
        return new double[]{sum / events, sorted[(int)Math.floor(events * .95)], liftMs};
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
```

- [ ] **Step 2: Run them to see them fail**

Add `FastWetChecks ModularBenchChecks` to `checks=(...)`. Run `bash scripts/test_inkwash.sh`. Expected: `FastWetChecks` fails at "Fast-wet: nothing changes at lift" or "tapers while the pen is still down" (Fast-wet is still None).

- [ ] **Step 3: Port the windows** as above.

- [ ] **Step 4: Run the checks and print the table**

Run: `bash scripts/test_inkwash.sh | grep -c '^PASS'` → `41`; `bash scripts/test_document.sh | grep -c '^PASS'` → `76`. Then print the timings and keep them for Task 16:

```bash
classes=$(mktemp -d); m=app/src/main/java/io/github/mpdairy/monopaint; t=app/src/test/java/io/github/mpdairy/monopaint
javac -d $classes $(sed -n 's/^sources=(\(.*\))/\1/p' scripts/test_inkwash.sh | tr ' ' '\n' | sed "s|^|$m/|;s|$|.java|") $t/ModularCodeChecks.java $t/ModularBenchChecks.java
java -cp $classes io.github.mpdairy.monopaint.ModularBenchChecks table
```

If a budget fails, profile before loosening anything: the usual cause is per-pixel `FloatTiles` lookups (fetch the tile once per run) or allocations per sample (reuse rows). Budgets change only with the controller's agreement.

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleRelease` → `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/io/github/mpdairy/monopaint/ModularStroke.java app/src/test/java/io/github/mpdairy/monopaint/{FastWetChecks,ModularBenchChecks}.java scripts/test_inkwash.sh
git commit -m "Modular engine: Fast-wet's live windows, and a desktop timing check per style"
```
Put the timing table in the commit message body.

---

### Task 12: Six-part brushes in the app

**Files:**
- Create: `ModularSizes.java`, `ModularBrushChecks.java`
- Modify: `Brush.java`, `InkWash.java`, `ModularStroke.java` (static `start`), `WildSample.java`, `InkWashBar.java`, `BrushCaptions.java`, `BrushStyle.java`, `scripts/test_inkwash.sh`

- [ ] **Step 1: Write the check**

`ModularBrushChecks.java`:

```java
package io.github.mpdairy.monopaint;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public final class ModularBrushChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        value(); storage(); labels(); sizes(); swatches(); strokes(); samples(); slots();
        System.out.println("PASS: a six-part brush is a Brush: stored as its M code, read back, keyed by its settings, drawn on the ink layer for ink, charcoal and graphite and the wash layer for wash and cover, sized from its style, sampled to fit its tile; Wild shows as Anything goes");
    }
    static Brush f(String name) { return Brush.of(ModularCodeChecks.fixture(name)); }
    static void value() {
        check(f("brave-onion").inks() && f("soft-coal").inks() && f("hb-pencil").inks() && !f("wet-wash").inks() && !f("thick-oil").inks(), "Ink, charcoal and graphite are pens; wash and cover paints");
        check(!f("wet-wash").wet() && f("brave-onion").name().equals("brave-onion") && f("brave-onion").builtin == null && f("brave-onion").wild == null, "A six-part brush is neither watercolor nor Wild");
    }
    static void storage() {
        Brush b = f("rake");
        check(b.write().equals("rake\t" + ModularCode.encode(b.parts)), "Stored as name and M code");
        Brush back = Brush.read(b.write());
        check(back != null && back.parts != null && back.sameAs(b), "and read back as the same brush");
        Brush renamed = Brush.of(b.parts.copy()); renamed.parts.name = "Other Name";
        check(!renamed.sameAs(b) && Brush.read(renamed.write()).name().equals("Other Name"), "A name is part of identity and survives storage");
        Brush old = Brush.read("x\tdapper-fig.I610gavij3f7q16o0000006m00.e1");
        check(old != null && old.wild != null && old.parts == null, "Old codes read as the old brushes they are");
    }
    static void labels() {
        check(BrushStyle.wild(WildBrush.Kind.INK).label().equals("Anything goes") && BrushStyle.named(WildBrush.Kind.INK, "Pencil").label().equals("Pencil"), "Wild is called Anything goes");
        check(Brush.of(WildBrush.roll(WildBrush.Kind.INK, 1)).styleLabel().equals("Anything goes") && f("brave-onion").styleLabel().equals("Dip pen") && Brush.of(BuiltinBrush.FUDE).styleLabel().equals("Brush pen"), "Style labels");
        check(f("rake").modularStyle().wild && Brush.of(BuiltinBrush.FUDE).modularStyle() == ModularStyle.named("Brush pen") && Brush.of(WildBrush.roll(WildBrush.Kind.PAINT, 3)).modularStyle().wild, "Every brush has a modular style for its dice");
        check(BrushCaptions.describe(f("brave-onion")).equals(BrushCaptions.twoLines(ModularWords.caption(ModularCodeChecks.fixture("brave-onion")))), "Six-part captions");
    }
    static void sizes() {
        ModularBrush onion = ModularCodeChecks.fixture("brave-onion");
        check(ModularSizes.size(onion, InkWashSizes.Preset.MEDIUM) == 6 && Math.abs(ModularSizes.size(onion, InkWashSizes.Preset.SMALL) - 2.4) < 1e-9 && ModularSizes.size(onion, InkWashSizes.Preset.LARGE) == 12, "Dip pen: 2.4, 6 and 12 px");
        check(ModularSizes.size(ModularCodeChecks.fixture("rake"), InkWashSizes.Preset.MEDIUM) == 8 && Math.abs(ModularSizes.size(ModularCodeChecks.fixture("hb-pencil"), InkWashSizes.Preset.SMALL) - 1.6) < 1e-9, "Anything goes ink is 8 px at M; Pencil's S is 1.6 px");
        ModularBrush wash = ModularCodeChecks.fixture("rake").copy(); wash.set(BrushPart.MEDIUM, new ModularBrush.Part(PartChoice.WASH));
        check(ModularSizes.size(wash, InkWashSizes.Preset.MEDIUM) == 120 && ModularSizes.size(ModularCodeChecks.fixture("wide-hake"), InkWashSizes.Preset.LARGE) == 360, "Anything goes paint is 120 px at M; Hake's L is 360");
        check(ModularSizes.darkness(InkWashShades.Shade.DARK) == 1 && Math.abs(ModularSizes.darkness(InkWashShades.Shade.LIGHT) - (1 - 120 / 255.0)) < 1e-12 && Math.abs(ModularSizes.darkness(InkWashShades.Shade.LIGHTER) - (1 - 175 / 255.0)) < 1e-12, "Shades are the ink greys' darkness");
        check(Math.abs(ModularSizes.sampleSize(onion, 96) - 96 / (13 + 14 * onion.part(BrushPart.MEDIUM).get(PartDial.WET))) < 1e-4 && Math.abs(ModularSizes.sampleSize(ModularCodeChecks.fixture("wide-hake"), 96) - 96 * .24) < 1e-4, "Samples fit their tile");
    }
    static void swatches() {
        check(ModularSizes.swatch(ModularCodeChecks.fixture("brave-onion"), InkWashShades.Shade.LIGHT) == InkWashShades.INK_GRAY && ModularSizes.swatch(ModularCodeChecks.fixture("wet-wash"), InkWashShades.Shade.DARK) == Math.round(255 * (1 - .34))
                && ModularSizes.swatch(ModularCodeChecks.fixture("thick-oil"), InkWashShades.Shade.DARK) == 0, "Swatches: ink's grey, a wash's glaze, cover's own grey");
    }
    static void strokes() {
        for (String name : new String[]{"brave-onion", "wet-wash"}) {
            ToneDocument d = InkWashLayers.blank(480, 360);
            DrawingStroke s = ModularStroke.start(d, ModularCodeChecks.fixture(name), InkWashSizes.Preset.MEDIUM, InkWashShades.Shade.DARK, new WatercolorSession());
            check(d.activeLayer() == (name.equals("wet-wash") ? InkWashLayers.WASH : InkWashLayers.INK), "start picks the layer by medium: " + name);
            for (int i = 0; i <= 60; i++) s.sample(60 + 6 * i, 180, ToolSettings.pressure(.6f), 0, 0, i * 6L);
            check(s.finish(), "and draws: " + name);
        }
    }
    static void samples() {
        for (String code : ModularCodeChecks.FIXTURES) {
            ModularBrush b = ModularCode.decode(code);
            int[] px = WildSample.modular(b, InkWashShades.Shade.DARK, WildSample.Shape.LOOP, 120, 96);
            int dark = 0; for (int p : px) if ((p & 0xff) < 200) dark++;
            check(px.length == 120 * 96 && dark > 30, "A six-part sample shows marks: " + code);
            check(WildSample.modular(b, InkWashShades.Shade.DARK, WildSample.Shape.LOOP, 120, 96) == px, "and is kept");
            check(WildSample.key(Brush.of(b), InkWashShades.Shade.DARK).equals("parts " + b.key() + " " + InkWashShades.Shade.DARK), "keyed by its settings and shade");
        }
    }
    static void slots() {
        Map<String, String> store = new HashMap<>();
        BrushLibrary.Store s = new BrushLibrary.Store() { public String get(String k) { return store.get(k); } public void put(String k, String v) { if (v == null) store.remove(k); else store.put(k, v); } };
        ToolSlots slots = new ToolSlots(s, new Random(1));
        Brush b = f("thick-oil");
        check(slots.use(3, b) && slots.brush(3).sameAs(b) && new ToolSlots(s, new Random(2)).brush(3).sameAs(b), "A six-part brush lives in a slot across launches");
        check(slots.library.save(b) == BrushLibrary.Saved.ADDED && slots.library.contains(b), "and in the library");
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
```

- [ ] **Step 2: Run it to see it fail**

Add `ModularSizes` to `sources=(...)`, `ModularBrushChecks` to `checks=(...)`. Expected: compile errors.

- [ ] **Step 3: `ModularSizes`**

```java
/** Sizes, shades and sample sizes for six-part brushes (spec "Sizes and shades"). Pure. */
final class ModularSizes {
    static boolean inks(ModularBrush b)                                   // medium ink, charcoal or graphite
    static double size(ModularBrush b, InkWashSizes.Preset preset)        // M = style.size(medium), Anything goes 8 (inks) or 120; S .4 M, L 2 M; 1.5–360
    static double darkness(InkWashShades.Shade shade)                     // 1, 1 − INK_GRAY/255, 1 − INK_LIGHT_GRAY/255
    static int swatch(ModularBrush b, InkWashShades.Shade shade)          // inks: InkWashShades.ink; wash round(255(1 − .34d)); cover round(255(1 − d))
    static float sampleSize(ModularBrush b, float ref)                    // ui.js 498–509, role "loop"
    static float samplePad(float size, float ref)                         // size < ref/7 ? 2 size + 6 : .55 size + 4, at least 4
}
```

- [ ] **Step 4: `Brush`**

Add `final ModularBrush parts;` (null unless six-part), `static Brush of(ModularBrush parts)`, and:
- `kind()`: parts → `ModularSizes.inks(parts) ? INK : PAINT`.
- `name()`, `identity()` (`parts.name + "\t" + parts.key()`), `write()` (`clean(name) + "\t" + ModularCode.encode(parts)`), `sameAs` unchanged.
- `read`: after the tab, `ModularCode.decode` first, then `BrushCode.decode` as today.
- `wet()`: false for parts.
- `settings()` and `style()`: throw `IllegalStateException("A six-part brush has no Wild settings")` for parts (nothing calls them for one after Task 13; the check never does).
- `String styleLabel()`: parts → `parts.style.name`; Wild → `wild.style.label()`; built-in → `builtin.style`.
- `ModularStyle modularStyle()`: parts → its style; Wild → `ModularStyle.named(wild.style.name)` (Wild → Anything goes); built-in → `ModularStyle.named(builtin.style)`.

`BrushStyle.label()`: `wild() ? ModularStyle.ANYTHING_GOES : name`. `BrushCaptions.describe`: parts → `twoLines(ModularWords.caption(brush.parts))`; Wild brushes' first trait becomes `b.style.label()`.

- [ ] **Step 5: `ModularStroke.start`, `InkWash`, `WildSample`, `InkWashBar`**

```java
/** {@code brush} at {@code preset} in {@code shade} on the selected pair: ink, charcoal and graphite on its ink layer, wash and cover on its wash layer. */
static ModularStroke start(ToneDocument document, ModularBrush brush, InkWashSizes.Preset preset, InkWashShades.Shade shade, WatercolorSession session) {
    int pair = InkWashLayers.current(document);
    document.selectLayer(ModularSizes.inks(brush) ? InkWashLayers.inkOf(document, pair) : InkWashLayers.washOf(document, pair));
    return new ModularStroke(document, brush, ModularSizes.size(brush, preset), ModularSizes.darkness(shade), session);
}
```
- `InkWash.stroke(document, brush, size, shade, session, clock)`: after the eraser line, `if (brush.parts != null) return ModularStroke.start(document, brush.parts, size, shade, session);`. Nothing else in that method changes (review: the built-in and Wild branches are byte-identical to before).
- `InkWash.swatch`: `if (brush.parts != null) return ModularSizes.swatch(brush.parts, shade);` first.
- `InkWash.diameter(Brush brush, InkWashSizes.Preset preset)` (new, static): parts → `(float)ModularSizes.size(brush.parts, preset)`; otherwise `brush.inks() ? inkSize(preset) : typical(preset)`. `InkWashBar.fillToolPanel`'s dots use it (the eraser keeps `typical`).
- `InkWash.sample(brush, shade, preset, shape, width, height)`: `if (brush.parts != null) return WildSample.modular(brush.parts, shade, shape, width, height);` first.
- `WildSample.key`: parts → `"parts " + parts.key() + " " + shade`.
- `WildSample.modular(ModularBrush b, InkWashShades.Shade shade, Shape shape, int width, int height)`: `ref = shape == LOOP ? min(height, width·.62) : height`, `size = ModularSizes.sampleSize(b, ref)`, the points `loop(width, height, samplePad(size, ref), 0, 0)` or `swoosh(width, height, inks ? SWOOSH_PAD_INK : SWOOSH_PAD_PAINT)`; kept under `key + " " + shape + " " + width + "x" + height` in `kept`; drawn by `page -> { select the ink or wash layer of pair 0; return new ModularStroke(page, b, size, ModularSizes.darkness(shade), null); }` (no session: a sample settles at lift only) through the existing `draw`. Refactor `pixels(String key, …)` so both paths share the cache and `draw` (no copied cache code).

- [ ] **Step 6: Run all checks**

Run: `bash scripts/test_inkwash.sh | grep -c '^PASS'` → `42`, including `LegacyGoldenChecks` and `WashGoldenChecks` unchanged; `bash scripts/test_document.sh | grep -c '^PASS'` → `77`.

- [ ] **Step 7: Build**

Run: `./gradlew :app:assembleRelease` → `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/io/github/mpdairy/monopaint/{ModularSizes,Brush,InkWash,ModularStroke,WildSample,InkWashBar,BrushCaptions,BrushStyle}.java app/src/test/java/io/github/mpdairy/monopaint/ModularBrushChecks.java scripts/test_inkwash.sh
git commit -m "Modular engine: six-part brushes as slot and library brushes, drawn, sized and sampled"
```

---

### Task 13: Randomize, styles, dice and variations make six-part brushes

**Files:**
- Modify: `ToolSlots.java`, `BrushLab.java`, `BrushLabActivity.java` (help text), `LabSettings.java` (only so a six-part brush doesn't crash it), `CHANGELOG.md`
- Modify (checks): `ToolSlotsChecks.java` (lines 53–58), `BrushLabChecks.java` (`opens()`'s status, `randomize()`)
- Create: `ModularLabChecks.java`
- Modify: `scripts/test_inkwash.sh`

- [ ] **Step 1: `ToolSlots.roll`**

```java
/** The dice: a new six-part brush in the slot brush's style (a Wild brush's is Anything goes), for this slot only. */
Brush roll(int slot) {
    Brush rolled = Brush.of(brush(slot).modularStyle().roll(dice));
    change(slot, rolled);
    return rolled;
}
```
In `ToolSlotsChecks`, replace lines 53–58 (the dice's expectations change on purpose) with:
```java
        Brush rolled = slots.roll(2);
        check(rolled.parts != null && rolled.parts.style.wild && slots.brush(2).sameAs(rolled), "The dice puts a new six-part brush in the slot, Anything goes for a Wild brush");
        Brush fineliner = slots.roll(0);
        check(fineliner.parts != null && fineliner.parts.style == ModularStyle.named("Fineliner"), "A built-in's dice rolls in its style");
        check(slots.roll(0).parts.style.name.equals("Fineliner"), "and keeps rolling in it");
```

- [ ] **Step 2: `BrushLab` holds either kind**

Fields: `private ModularBrush parts;` (the lab's own six-part brush, or null) and `final ModularLocks partLocks = new ModularLocks();`. Keep `locks` (`BrushSetting`) for old brushes.

```java
ModularBrush parts()                                   // the six-part brush, or null for an old brush or a built-in
/** A new six-part brush in {@code style}, keeping locked parts and dials of a six-part brush. */
void pickStyle(ModularStyle style)                     // replaces pickStyle(BrushStyle); no-op if parts != null and style == parts.style; status "A new <style> brush: <name>. Roll for another."
void randomize()                                       // in brush.modularStyle(); status "Randomized: <name>, a <style> brush[, keeping <locked>]." or, in Anything goes, "Randomized: <name>, anything goes[, keeping …]."; <locked> = locked parts' and dials' labels, lowercased, comma-joined
void choose(BrushPart part, PartChoice choice)         // ModularBrush.switchTo; status "Tip: Split nib."
void slide(PartDial dial, double value)                // sets the dial; a hand cell makes the hand Custom while sliding
void endSlide(PartDial dial)                           // normalize; a hand gets matchHand's name; fresh(); status "<label> set to <shown>."
void roll(PartDial dial)                               // style.rollOneDial; status "<label> rolled to <shown>."
void rollPart(BrushPart part)                          // style.rollOnePart; status "<Part> rolled: <choice words>."
void toggleLock(PartDial dial); void toggleLock(BrushPart part)
```
- `show(Brush b)`: `brush = b; parts = b.parts; settings = parts != null ? null : b.wild != null ? b.wild : b.settings();`.
- `copyOf`: parts → `Brush.of(b.parts.copy())`.
- `edit()` for a six-part brush: `remember()` and return nothing (`editParts()`), since there's no built-in to copy.
- `code()`: `parts != null ? ModularCode.encode(parts) : BrushCode.encode(settings)`.
- `load(code)`: `ModularCode.decode` first, then `BrushCode.decode`; the failure text "That code didn't read. A share code looks like brave-onion.M… or dapper-fig.I…, copied whole."
- `rename`: a six-part brush renames `parts.name`.
- `fresh()`: parts → each `ModularVariations.Made` becomes `new BrushVariations.Variation(Brush.of(m.brush), lines(m))`, where `lines(m)` is `[m.swap]` plus the first line of `BrushCaptions.twoLines(the nudges)` when there is a swap (a swap longer than `BrushCaptions.LINE` drops its part: "→ Cold press"), else `BrushCaptions.twoLines(m.caption)`; old brushes as today.
- `opened()`: `brush.name() + " · " + brush.styleLabel() + …`.
- Old-brush methods (`slide(BrushSetting…)`, `choose(BrushSetting…)`, `roll(BrushSetting)`, `flipSpeed`, `toggleLock(BrushSetting)`) stay for old brushes and built-ins.

In `BrushLabActivity`, change only what no longer compiles (its help text mentions "Wild": make it "Anything goes"); the column is Task 14.

- [ ] **Step 3: Update `BrushLabChecks`**

- `opens()`: the status is now `"wobbly-goose · Anything goes"`.
- Replace `randomize()` whole (it tested the old style dice, which Stage 1 replaces):
```java
    static void randomize() {
        BrushLab lab = new BrushLab(slots(), 2, new Random(5));
        lab.pickStyle(ModularStyle.named("Pencil"));
        check(lab.parts() != null && lab.parts().style.name.equals("Pencil") && lab.parts().choice(BrushPart.MEDIUM) == PartChoice.GRAPHITE && lab.status.startsWith("A new Pencil brush: "), "Picking a style rolls a six-part brush in it: " + lab.status);
        lab.toggleLock(PartDial.GRADE);
        double grade = lab.parts().part(BrushPart.MEDIUM).get(PartDial.GRADE);
        for (int i = 0; i < 20; i++) {
            lab.randomize();
            check(lab.parts().part(BrushPart.MEDIUM).get(PartDial.GRADE) == grade && lab.parts().style.name.equals("Pencil"), "Randomize stays in the style and keeps what's locked");
        }
        check(lab.status.startsWith("Randomized: ") && lab.status.endsWith(", a Pencil brush, keeping grade."), "and says so: " + lab.status);
        for (BrushVariations.Variation v : lab.variations) if (v.brush.parts.choice(BrushPart.MEDIUM) == PartChoice.GRAPHITE) check(v.brush.parts.part(BrushPart.MEDIUM).get(PartDial.GRADE) == grade, "and so do variations");
        lab.toggleLock(PartDial.GRADE);
        check(lab.partLocks.dials.isEmpty(), "Unlocking");
        lab.pickStyle(ModularStyle.named("Anything goes"));
        check(lab.parts().style.wild, "Anything goes");
    }
```
Any other `BrushLabChecks` assertion that called `pickStyle(BrushStyle…)` gets the same treatment; nothing else in it should change.

- [ ] **Step 4: Write `ModularLabChecks`**

```java
package io.github.mpdairy.monopaint;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public final class ModularLabChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        fromOld(); parts(); locks(); variations(); codes(); undo();
        System.out.println("PASS: the lab rolls six-part brushes from any brush, swaps choices, slides, rolls and locks dials and parts, shows six-part variations, copies and loads M codes and still loads I/P codes as old brushes; Undo takes each change back");
    }
    static BrushLab lab(int slot, long seed) {
        Map<String, String> store = new HashMap<>();
        BrushLibrary.Store s = new BrushLibrary.Store() { public String get(String k) { return store.get(k); } public void put(String k, String v) { if (v == null) store.remove(k); else store.put(k, v); } };
        return new BrushLab(new ToolSlots(s, new Random(1)), slot, new Random(seed));
    }
    static void fromOld() {
        BrushLab lab = lab(2, 1);
        check(lab.parts() == null && lab.brush().wild != null, "An old brush opens as itself");
        lab.randomize();
        check(lab.parts() != null && lab.parts().style.wild && lab.canUndo(), "Randomize turns it into a six-part Anything goes brush");
        BrushLab fude = lab(1, 2);
        fude.randomize();
        check(fude.parts() != null && fude.parts().style == ModularStyle.named("Brush pen"), "A built-in randomizes in its style");
    }
    static void parts() {
        BrushLab lab = lab(2, 3);
        lab.pickStyle(ModularStyle.named("Dip pen"));
        lab.choose(BrushPart.TIP, PartChoice.HAKE);
        check(lab.parts().choice(BrushPart.TIP) == PartChoice.HAKE && lab.status.equals("Tip: Hake."), "A choice chip swaps the part: " + lab.status);
        lab.startSlide(); lab.slide(PartDial.ANGLE, 50); lab.endSlide(PartDial.ANGLE);
        check(lab.parts().part(BrushPart.TIP).get(PartDial.ANGLE) == 50 && lab.status.equals("Nib angle set to 50°."), "A slider sets its dial: " + lab.status);
        lab.startSlide(); lab.slide(PartDial.TS, .7); lab.endSlide(PartDial.TS);
        check(lab.parts().part(BrushPart.HAND).get(PartDial.TS) == .7 && lab.parts().choice(BrushPart.HAND) == ModularBrush.matchHand(lab.parts().part(BrushPart.HAND)), "A hand cell slides; the hand is named for its cells");
        lab.roll(PartDial.ANGLE);
        check(lab.status.startsWith("Nib angle rolled to "), "A dial's own dice: " + lab.status);
        PartChoice paper = lab.parts().choice(BrushPart.PAPER);
        lab.rollPart(BrushPart.MEDIUM);
        check(lab.parts().choice(BrushPart.PAPER) == paper, "A part's dice leaves the others");
    }
    static void locks() {
        BrushLab lab = lab(2, 4);
        lab.pickStyle(ModularStyle.named("Watercolor"));
        lab.toggleLock(BrushPart.PAPER);
        ModularBrush.Part paper = lab.parts().part(BrushPart.PAPER).copy();
        for (int i = 0; i < 10; i++) { lab.randomize(); check(lab.parts().part(BrushPart.PAPER).sameAs(paper), "A locked part survives Randomize"); }
        for (BrushVariations.Variation v : lab.variations) check(v.brush.parts.part(BrushPart.PAPER).sameAs(paper), "and variations");
    }
    static void variations() {
        BrushLab lab = lab(2, 5);
        lab.pickStyle(ModularStyle.named("Ink wash"));
        check(lab.variations.size() == 6, "Six variations");
        int swaps = 0;
        for (BrushVariations.Variation v : lab.variations) {
            check(v.brush.parts != null && v.caption.size() <= 2, "A six-part variation, its caption at most two lines");
            // A swap's line comes first; nudges start "more " or "less " (a hand cell's label has an arrow too: "more pressure → size").
            if (!v.caption.isEmpty() && !v.caption.get(0).startsWith("more ") && !v.caption.get(0).startsWith("less ")) swaps++;
        }
        check(swaps <= 1, "At most one swaps a part");
        BrushVariations.Variation v = lab.variations.get(0);
        lab.pickVariation(0);
        check(lab.brush().sameAs(v.brush) && lab.parts() != v.brush.parts, "Picking one switches to a copy of it");
    }
    static void codes() {
        BrushLab lab = lab(2, 6);
        lab.pickStyle(ModularStyle.named("Hake"));
        String code = lab.code();
        check(code.contains(".Mc"), "A six-part brush's code is an M code: " + code);
        BrushLab other = lab(2, 7);
        check(other.load(code) && other.parts() != null && ModularCode.encode(other.parts()).equals(code), "and loads");
        check(other.load("dapper-fig.I610gavij3f7q16o0000006m00.e1") && other.parts() == null && other.brush().wild != null, "An I code loads as the old brush it is");
        check(!other.load("brave-onion.Mz0_0_0_0_0_0.1") && other.status.startsWith("That code didn't read."), "A bad code says so");
        check(other.load(ModularCodeChecks.BRAVE_ONION) && other.brush().name().equals("brave-onion"), "The web lab's codes load");
        check(other.rename("Brave Onion II") && other.parts().name.equals("Brave Onion II"), "Rename");
    }
    static void undo() {
        BrushLab lab = lab(2, 8);
        lab.pickStyle(ModularStyle.named("Marker"));
        String before = lab.code();
        lab.choose(BrushPart.TIP, PartChoice.SPLIT_NIB);
        lab.undo();
        check(lab.code().equals(before), "Undo takes a swap back");
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
```
(`lab(2, …)` opens slot 2, a Wild pen on first launch; slot 1 is Fude.)

- [ ] **Step 5: CHANGELOG**

Under **Unreleased** add:
```
- Brushes now have six parts — tip, medium, carry, paper, life and hand — and Randomize, the style chips, a slot's dice
  and Brush Lab's variations make them: hake, split nib and multi-line tips; ink that loosens into a wash, gouache and
  oils that cover, charcoal and pencil; papers; and a hand that answers pressure, tilt and speed. New styles: Hake, and
  "Anything goes" (once called Wild). Share codes look like `brave-onion.M…` and work in the web Brush Lab too. Your
  existing brushes draw exactly as before.
```

- [ ] **Step 6: Run all checks**

Add `ModularLabChecks` to `checks=(...)`. Run: `bash scripts/test_inkwash.sh | grep -c '^PASS'` → `43`; `bash scripts/test_document.sh | grep -c '^PASS'` → `78`.

- [ ] **Step 7: Build**

Before building, make `LabSettings` compile and not crash with the new `BrushLab`: its style chips become the 14 `ModularStyle`s (names, Anything goes last), on for `lab.brush().modularStyle()` when `lab.parts() != null`, a tap calling `lab.pickStyle(style)`; Randomize's description uses `lab.brush().styleLabel()`; and when `lab.parts() != null` it renders only the chips and Randomize (Task 14 adds the parts). Old brushes keep their rows.

Run: `./gradlew :app:assembleRelease` → `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/io/github/mpdairy/monopaint/{ToolSlots,BrushLab,BrushLabActivity,LabSettings}.java app/src/test/java/io/github/mpdairy/monopaint/{ToolSlotsChecks,BrushLabChecks,ModularLabChecks}.java scripts/test_inkwash.sh CHANGELOG.md
git commit -m "Randomize, styles, the slot dice and variations make six-part brushes"
```

---

### Task 14: The lab's column for six-part brushes

**Files:**
- Modify: `LabSettings.java`, `BrushLabActivity.java`

No new pure logic (it's all in `BrushLab`, checked in Task 13); this task is Android views. Read `LabSettings` and `LabUi` first and reuse their row, label, chip and slider code: one generic row for a `PartDial`, sharing today's `row(BrushSetting…)` layout through a small shared method rather than copying it.

- [ ] **Step 1: The column**

`LabSettings.render()`:
- Style chips: the 14 `ModularStyle`s (names; `Anything goes` last), on for `lab.brush().modularStyle()` when the brush is six-part, none on for an old brush; a tap → `lab.pickStyle(style)`.
- Randomize as today; its description "Randomize: a new <style> brush; locked parts and dials stay".
- Old brushes and built-ins (`lab.parts() == null`): today's rows, unchanged.
- Six-part brushes:
  1. **This brush**: a `TextView` of `ModularWords.plainWords(lab.parts())`, 13 sp, normal weight, padded like the style block, under a rule.
  2. Per part (`BrushPart.values()`): a header row — the part's lock (`lab.toggleLock(part)`; locked shows `ic_ph_lock` and greys the row), the label in the column's label style, then a wrap of choice chips: each `ModularFeatures.offered` choice of the part, plus the current one if it isn't offered (so a decoded Bristles or Drag shows), on for the current; a tap → `lab.choose(part, choice)`; a die at the end → `lab.rollPart(part)`.
  3. Under it, the current choice's dials as rows: lock (`lab.toggleLock(dial)`), label (`choice.labelOf(dial)`), then for a choice dial (Head, Line placement, Fast is) its option chips, else a `BandSlider(min, max, value, band)` with `range = choice.range(dial)` and `band = lab.parts().style.band(choice, dial)`, the value `dial.shown(value)`, and the die (`lab.roll(dial)`). Slider events call `lab.startSlide()`, `lab.slide(dial, v)` (updating the value text), `lab.endSlide(dial)` then `changed`. Life hides `TAKES` and `PLUME` (deferred).
  4. Hand: its header's chips are the presets (Custom shown only when current), then the nine cells, Response and Fast is as rows.
- A part lock or a dial lock redraws only the column, as today.

- [ ] **Step 2: `BrushLabActivity`**

- Help text: "Style picks a family (Fineliner, Brush pen, Dip pen, Marker, Pencil, Charcoal, Watercolor, Ink wash, Gouache, Oils, Pastels, Dry brush, Hake) or Anything goes." and "A brush is six parts: tip, medium, carry, paper, life and hand. Each part has its choices and dials, each with its own die and lock."
- `showCorner()`: a six-part brush's corner is `InkWash.sample(brush, DARK, size, LOOP, CORNER_WIDTH, CORNER_HEIGHT)` (its size is fitted to the tile, so the preset doesn't matter); old brushes as today.

- [ ] **Step 3: Build and check**

Run: `./gradlew :app:assembleRelease` → `BUILD SUCCESSFUL`; `bash scripts/test_inkwash.sh | grep -c '^PASS'` → `43`.

Leave for the controller's device check (don't run adb): on the Manta, open Brush Lab from a slot, tap Randomize a few times; pick Hake, Charcoal, Watercolor, Oils; swap the tip to Split nib; move Tilt → size; read "This brush"; copy the code and paste it back; open an old Wild brush and see its old rows.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/io/github/mpdairy/monopaint/{LabSettings,BrushLabActivity}.java
git commit -m "Brush Lab: a column of parts for six-part brushes, with \"This brush\""
```

---

### Task 15: Drying keeps settling after lift

**Files:**
- Create: `DryingWater.java`, `DryingWaterChecks.java`, `DryingTicker.java` (Android)
- Modify: `WatercolorSession.java`, `ModularStroke.java`, `InkWash.java`, `DrawingPad.java`, `BrushTestPad.java`, `BrushLabActivity.java`, `scripts/test_inkwash.sh`, `CHANGELOG.md`

**Port:** stroke.js 76–238: `TPS`, `wetTicksOf`, `hashPts`, `waterTile`, `sampleTile`, `Water`'s constructor (only `psiA`, `psiB`, `psiC`, `height`), `curl`, `addWet` (156–200), `stepPatch` (201–238), and `step`'s patch rule (384: a patch steps on its even ages and on its last). Leave out parcels, sources, stains, plumes, trails and `touchLine`.

**App differences:**
- One patch, on the layer the Drying stroke drew; per-patch arrays: `height` over the patch rectangle plus 1 px (from the stroke's `StrokePaper` fine and mid, blurred with radius 1), `depth` over the patch (from its mask, stroke.js 167–175). `curl` reads them at patch coordinates (clamped).
- The water tiles' generator is `new WildBrush.Random((int)brushSeed ^ 0xf10e)`; the patch's `rr` is `new WildBrush.Random((int)(penSeed ^ hashPts) ^ 0x5e771e)` (stroke.js 686).
- `mul` becomes a tone write: the patch keeps each pixel's tone at lift (`liftTone`, read with `document.tone` after the stroke composed) and `shown₀`; a step writes `round(liftTone · (1 − shown) / (1 − shown₀))` for the pixels whose `shown` rose, through `setTones`, in one gesture: `document.begin()`, the writes, `document.finishContinuation()` (one undo step with the stroke).
- `finishNow`: the last step at once (`q = 1`, no flow: stroke.js 204–205 with `last = true`), in one gesture.

```java
/** A Drying wash's suspended pigment settling into the paper's valleys after lift (stroke.js 76–238, patches only). Pure. */
final class DryingWater {
    /** null when settle is 0 or the stroke is opaque: nothing to settle. darkness: the held-back body, min(1, d · washK), over x0, y0, w, h. */
    static DryingWater lay(ToneDocument document, StrokePaper paper, int x0, int y0, int w, int h, float[] darkness, double drying, double settle, long brushSeed, int strokeSeed)
    /** Still the page it was laid on: the same revision and active layer. */
    boolean valid(ToneDocument document)
    /** One tick (100 ms); false when it has finished. */
    boolean step(ToneDocument document)
    /** Lays everything still suspended where it is, at once. */
    void finishNow(ToneDocument document)
}
```

`WatercolorSession` (keep everything it does today):
```java
private DryingWater drying;
/** After a Drying stroke's lift: its wash keeps settling. */
void holdDrying(DryingWater water) { drying = water; }
boolean settling() { return drying != null; }
/** One tick of the settling wash; false when there is none left. A page changed by anything else drops it where it is. */
boolean tickDrying(ToneDocument document)
/** Before any stroke: what is still settling is laid where it is. */
void settleNow(ToneDocument document)
```
`dry(reason)` also sets `drying = null`.

`ModularStroke.finish()` (DRY): when `session != null` and `document.finish()` returned true, `session.holdDrying(DryingWater.lay(document, paper, …, pendingWet, r.drying, r.cover > 0 ? 0 : r.settle, brushSeed, (int)(r.pen.seed ^ hashPts)))` (a null water is fine). The water reads `document.revision()` after the stroke's finish. `InkWash.stroke(document, brush, …)` (static) calls `session.settleNow(document)` first, before choosing a layer; the instance `stroke(document, eraserEnd)` calls it before its `session.dry(…)`.

`DryingTicker` (Android):
```java
/** Ten ticks a second while a Drying wash settles, between strokes (spec "Drying after lift"). */
final class DryingTicker {
    static final long TICK_MS = 100;
    interface Host { ToneDocument page(); boolean busy(); void show(); void settled(); }
    DryingTicker(View view, WatercolorSession session, Host host)
    void start()      // after a stroke: schedules a tick if session.settling()
    void stop()
}
```
A tick: busy (a stroke down, the screen paused or loading) → try again in `TICK_MS`; else `more = session.tickDrying(host.page())`, `host.show()`, then `more ? postDelayed(tick, TICK_MS) : host.settled()`.
- `DrawingPad`: a `DryingTicker` with `page()` = `document`, `busy()` = `stroke != null || !app.resumed || app.loading`, `show()` = `renderDirty(); flush(true);`, `settled()` = `unrefreshed = true; app.recovery(); removeCallbacks(penAwayRefresh); postDelayed(penAwayRefresh, PEN_AWAY_MS);`. Start it at the end of the stroke-finishing method (after `scheduleWet()`); stop it in `close()`.
- `BrushTestPad`: takes the lab's `WatercolorSession` in its constructor (`BrushLabActivity` passes its `session`), the same ticker with `show()` = `renderDirty(); flush();`, `settled()` = schedule its refresh as after a stroke; start after `finishStroke`, stop where it gives the panel back.

- [ ] **Step 1: Write the check**

`DryingWaterChecks.java`:

```java
package io.github.mpdairy.monopaint;

public final class DryingWaterChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        settles(); onlyDarkens(); ends(); settleNow(); dropped(); oneUndo(); opaque();
        System.out.println("PASS: a Drying wash with Settle keeps settling after lift, only darkening, for its drying time; a new stroke lays what's left where it is; anything else changing the page stops it; the stroke and its settling are one undo step; opaque paint doesn't settle");
    }
    static ModularBrush brush(double settle, double drying) {
        ModularBrush b = ModularCodeChecks.fixture("wet-wash");
        b.part(BrushPart.LIFE).set(PartDial.SETTLE, settle); b.part(BrushPart.LIFE).set(PartDial.DRYING, drying);
        return b;
    }
    static ToneDocument drawn(WatercolorSession session, ModularBrush b) {
        ToneDocument d = InkWashLayers.blank(480, 360);
        d.selectLayer(InkWashLayers.WASH);
        ModularStroke s = new ModularStroke(d, b, 80, 1, session);
        for (int i = 0; i <= 120; i++) { double t = i / 120.0; s.sample((float)(40 + 400 * t), (float)(180 + 60 * Math.sin(2 * Math.PI * t)), ToolSettings.pressure(.8f), 0, 0, i * 5L); }
        s.finish();
        return d;
    }
    static int[] tones(ToneDocument d) { int[] t = new int[d.width * d.height]; for (int i = 0; i < t.length; i++) t[i] = d.compositeTone(i % d.width, i / d.width); return t; }
    static void settles() {
        WatercolorSession session = new WatercolorSession();
        ToneDocument d = drawn(session, brush(.8, .2));
        check(session.settling(), "A Drying wash with Settle keeps settling after lift");
        int[] before = tones(d);
        int ticks = 0; while (session.tickDrying(d) && ticks < 1000) ticks++;
        int changed = 0; int[] after = tones(d); for (int i = 0; i < after.length; i++) if (after[i] != before[i]) changed++;
        check(changed > 100, "Settling changes the wash: " + changed + " pixels over " + ticks + " ticks");
    }
    static void onlyDarkens() {
        WatercolorSession session = new WatercolorSession();
        ToneDocument d = drawn(session, brush(.8, .2));
        int[] prev = tones(d);
        while (session.tickDrying(d)) { int[] now = tones(d); for (int i = 0; i < now.length; i++) check(now[i] <= prev[i], "Settling only darkens"); prev = now; }
    }
    static void ends() {
        WatercolorSession session = new WatercolorSession();
        ToneDocument d = drawn(session, brush(.8, 0));
        int ticks = 0; while (session.tickDrying(d)) ticks++;
        check(ticks <= PartDial.wetMs(0) / 100 + 1 && !session.settling(), "It ends with its drying time: " + ticks + " ticks");
    }
    static void settleNow() {
        WatercolorSession session = new WatercolorSession();
        ToneDocument d = drawn(session, brush(.8, 1));
        int[] before = tones(d);
        session.settleNow(d);
        int darker = 0; int[] after = tones(d);
        for (int i = 0; i < after.length; i++) { check(after[i] <= before[i], "Laying it now only darkens"); if (after[i] < before[i]) darker++; }
        check(darker > 100 && !session.settling(), "A new stroke lays what's still suspended where it is");
    }
    static void dropped() {
        WatercolorSession session = new WatercolorSession();
        ToneDocument d = drawn(session, brush(.8, 1));
        d.begin(); d.setTone(5, 5, 0); d.finish();
        int[] before = tones(d);
        check(!session.tickDrying(d) && !session.settling(), "Anything else changing the page stops it");
        int[] after = tones(d); for (int i = 0; i < after.length; i++) check(after[i] == before[i], "without writing");
    }
    static void oneUndo() {
        WatercolorSession session = new WatercolorSession();
        ToneDocument d = drawn(session, brush(.8, .2));
        while (session.tickDrying(d)) { }
        d.undo();
        for (int y = 0; y < d.height; y += 5) for (int x = 0; x < d.width; x += 5) check(d.compositeTone(x, y) == 255, "One Undo takes back the stroke and its settling");
    }
    static void opaque() {
        WatercolorSession session = new WatercolorSession();
        ModularBrush b = brush(.8, .2); b.set(BrushPart.MEDIUM, new ModularBrush.Part(PartChoice.COVER));
        drawn(session, b);
        check(!session.settling(), "Opaque paint doesn't settle");
        WatercolorSession none = new WatercolorSession();
        drawn(none, brush(0, .5));
        check(!none.settling(), "nor a wash with Settle 0");
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
```
(`drawn` builds its own stroke rather than using `ModularStrokeChecks.wave`, which makes a session of its own.)

- [ ] **Step 2: Run it to see it fail**

Add `DryingWater` to `sources=(...)` (before `WatercolorSession`), `DryingWaterChecks` to `checks=(...)`. Expected: compile errors.

- [ ] **Step 3: Port and wire** as above.

- [ ] **Step 4: CHANGELOG**

Under **Unreleased** add:
```
- A brush whose life is "Drying" keeps settling for its drying time after you lift (up to 20 seconds): pigment sinks
  into the paper's grain. Drawing again lays what's left where it is; Undo takes back the stroke and its settling together.
```

- [ ] **Step 5: Run all checks and build**

Run: `bash scripts/test_inkwash.sh | grep -c '^PASS'` → `44`; `bash scripts/test_document.sh | grep -c '^PASS'` → `79`; `./gradlew :app:assembleRelease` → `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/io/github/mpdairy/monopaint/{DryingWater,DryingTicker,WatercolorSession,ModularStroke,InkWash,DrawingPad,BrushTestPad,BrushLabActivity}.java app/src/test/java/io/github/mpdairy/monopaint/DryingWaterChecks.java scripts/test_inkwash.sh CHANGELOG.md
git commit -m "Drying brushes keep settling after lift"
```

---

### Task 16: Full verification and as-built notes

**Files:**
- Modify: `docs/archive/superpowers/specs/2026-10-14-modular-engine-design.md` (an "As built" section at the end)

- [ ] **Step 1: Everything passes**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" PATH="/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin:$PATH"
bash scripts/test_inkwash.sh | grep -c '^PASS'     # 44
bash scripts/test_document.sh | grep -c '^PASS'    # 79
./gradlew :app:assembleRelease                     # BUILD SUCCESSFUL
git diff 3e47f54 -- app/src/test/java/io/github/mpdairy/monopaint/WashGoldenChecks.java   # empty
git log --format=%H -n 1 -- app/src/test/java/io/github/mpdairy/monopaint/LegacyGoldenChecks.java   # only Task 1's commit
```
(`3e47f54` is the commit this plan was written on; `LegacyGoldenChecks`' `EXPECTED` must not have changed since Task 1.)

- [ ] **Step 2: Review the diff against CLAUDE.md's rules**

`git diff 3e47f54 --stat`, then read every changed main file for: duplicated logic (the sample cache, the lab's rows, tilt reading: one path each), helpers duplicating existing ones (`WildBrush.Random`, `BrushCode.slug`/`written`, `BrushNames`, `BrushCaptions.twoLines`, `WashStroke.preview`'s formula, `BrushDirection`), old-engine code touched (only the one dispatch line in `InkWash.stroke` and the additive changes the tasks name), and any `android.*` import in a pure class.

- [ ] **Step 3: As-built notes**

Append to the spec an "As built (2026-10-…)" section: the desktop timing table from Task 11 (rerun `ModularBenchChecks table`), any port detail that had to differ from the prototype and why, check counts, and the device checks left for Elling (Brush pen and Ink wash at Large, Hake at Large, a Drying Watercolor, tilt on Pencil and Charcoal, an old Wild brush unchanged).

- [ ] **Step 4: Commit**

```bash
git add docs/archive/superpowers/specs/2026-10-14-modular-engine-design.md
git commit -m "docs: modular engine Stage 1 as built"
```
