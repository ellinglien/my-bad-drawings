# Brush Lab v2: what the mockup implies for the app

Mockup: `lab-v2-mockup.html`, built by `build.js` from `src/`. Run `#selftest` for a 47-step click-through. Sizes are rough net lines.

## Design choices

- **The lab works on the picked slot.** Tap a slot, then tap a library card or a variation: it goes straight into that slot, and Undo takes it back. There is no separate try step (Elling, review 1). A card's number badges show which slots hold it. The library order stays fixed (built-ins, then saved brushes newest first), so pages don't reshuffle after a pick. A STYLE ▾ filter shows counts.
- **No scrolling in Advanced.** Every setting is shown; none are hidden by style. Inactive ones (nib width on a round nib, the wet-only settings on flat paint) are greyed out, not removed. Three things make it fit:
  - the settings column runs the full height, and the library sits under the test area;
  - the style chips fold into a STYLE ▾ button;
  - rows share the height at 30–40 dp.
- **Fit was checked** on Manta P/L and Nomad P/L. The tightest is Nomad landscape (30 dp rows). Nomad portrait folds the variations into 2×3.
- **Simple** has no locks: they live in Advanced only, and Simple's Randomize, styles and variations ignore them. It shows the styles as a 2-column grid of 44 dp buttons, plus Randomize, the variations, the test area, the slots and the library.
- **Explainer and guide.** Tap a setting's name and the variations strip becomes an off/half/full card. GUIDE in the bar opens paged rows for every setting, drawn with the current brush. Both are static. "Animate" is web only.
- **Share/Load code** moves behind a CODE button in the bar.

- **Picked slot or card: never inverted.** The slot row, toolbar slots, the eraser, library cards and comparison buttons get a 3–4 dp frame plus a 6 dp black tab on the side facing the page. The sample stays as it will look on the page. Inverted fills are only for text buttons and chips.
- **Filed idea:** an "inverted mode" (white on black) for the whole app, which Elling thinks is fun for later.

## Lab (app/src/main/java/io/github/mpdairy/monopaint/)

- **BrushLabActivity** (~+180): variation tap = assign to the picked slot; new layout: column full height, shelf under test area, strip that swaps variations/explainer, Simple/Advanced, GUIDE/CODE in the bar, Nomad-compact rules (no title, icon Undo, 2×3 variations). Mode remembered in preferences.
- **LabSettings** (~+150): groups (Shape/Ink/Paper; Paint/Wet paint/Paper; Pen input), compact rows with height from the column, choice chips inline, STYLE ▾ flyout, names as explainer buttons, pen-input rows.
- **LabLibrary** (~+90): slot row (six previews, picked one framed) above the paged cards, "→ slot N" label, slot badges on cards, fixed order, style filter flyout.
- **New `SettingGuide`** (~180): explainer card plus paged guide page; variants per setting (off/half/full or each choice); upright pen except Tilt.
- **New `SettingWords`** (~40): one line per setting (from `DESCRIBE` in `src/app.js`).
- **BrushLab** (~+40): lab state is slots + picked slot (undo restores both); `assign(slot, entry)`; filter state.
- **LabUi** (~+20): segmented control, flyout box, small chip style.

## Pen input

- **WildBrush** (~+40): `pressureTo` (size, darker, both), `tilt` (amount, 0 = none) and `tiltTo` (width, nib angle). Speed stays the signed dial, where 0 means none. Old brushes keep their behaviour: size, no tilt.
- **BrushCode** (~+30): optional suffix `.x` + pressureTo + tiltTo + tilt (two base-36 digits) + paint speed (two digits). It is only written when something isn't the default, so old codes are unchanged and read the same.
- **WildInkStroke** (~+30):
  - darker: coverage × (1 − .8·amount·(1 − pressure));
  - tilt → width ×(1 + 1.6·amount·lean);
  - tilt → nib angle leans toward the tilt's direction.
  - The Nomad's `landscapeTilt` already exists.
- **WashStroke / WashLook** (~+25):
  - pressure → darker;
  - tilt → width;
  - speed → thinner/thicker. This is new for paint, using `PenSpeed` as ink does.
- **BrushStyle / BrushVariations** (~+10): Randomize keeps where pressure/tilt go (how you hold the pen) and rolls amounts as now. Locks cover pen rows.

## Drawing toolbar (InkWashBar, ~+160 / −60)

- **Three shared size slots** replace S/M/L (Elling: "size slots, like the swatch slots").
  - They sit beside the shade swatches and are styled the same: 48 dp, with a ▾ tab.
  - Each face shows a true-size dot. Past 80% of the button the dot is capped and its size in mm is written under it (option C's rule).
  - Tap a size slot: the current tool takes that size.
  - The ▾ tab (or a long-press) opens a picker:
    - a true-size dot at 1:1, clipped by its window when it is bigger;
    - a log slider from 1 to 360 px, labelled thin ↔ big brush;
    - stops at 2, 4, 8, 16, 32, 64, 120, 240 and 360;
    - px and mm shown.
  - Changing a size slot also sets the current tool. Other tools keep their own last size in px, so changing a slot never alters them. This is the same rule as shades.
  - The eraser uses the size slots too.
  - Defaults: 3, 16 and 120 px.
  - Sizes become one px value per tool, shared by ink and paint, instead of `InkWashSizes.Preset` per kind (~+60 there, migrating S/M/L to px). There is a new `SizePicker` (~120) and a `SizeSlot` view (~50).
- **Slot panel** is now just Roll and Brush Lab… (−S/M/L, −shades, −Save, −recent brushes; ~−90). The eraser has no panel.
  - **Recommend keeping it** as a two-row panel, opened by tapping the picked slot again, as today. A long-press does the same.
  - Removing it entirely would hide Roll and the way into the lab behind a long-press. That is slow with the pen on e-ink and hard to discover.
  - `ToolSlots.recent()` can go (~−20).
- **Three shared swatches** sit after the eraser.
  - Tap one: the current slot's shade becomes that grey.
  - Long-press, or tap its corner tab, to open an 8-grey flyout. Picking a grey sets the swatch and the current slot.
  - Each slot keeps its own grey, so a swatch change never alters other slots. The swatch matching the slot's grey is framed.
  - The default greys are 100%, 53% and 30%.
  - This needs `InkWashShades` to change from 3 named shades to a grey value per slot (~+40, with migration from DARK/LIGHT/LIGHTER). There is also a new `ShadePicker` (~70).
- **Bar layout: recommend LEFT** (viewer: LEFT BAR / TOP BAR). Checked with 6 slots, the eraser, 3 sizes and 3 shades.
  - **Left:** as MonoPaint, page actions (menu, undo, redo, layers) run along the top in one 56 dp row. The slots and eraser run down the left edge in a column 108 dp wide, then sizes and shades side by side (two columns, three rows of 48).
    - It is the same on every screen, with 96×64 slots everywhere.
    - Tightest is Nomad landscape: 6×64 + 48 + 12 + 144 = 588 of 690 dp.
    - Panels and pickers open to the right.
    - Page area against the top bar: Manta P 920×1309 vs 1024×1237; Nomad L 890×690 vs 998×621.
    - Mirror it to the right edge when MonoPaint's left-hand setting is on.
  - **Top:** two rows, with page actions, sizes and shades above and the slots and eraser below.
    - Portrait: 96×64 slots on the Manta and the Nomad.
    - Manta landscape fits one row: 96×64 slots with sizes and shades.
    - Nomad landscape can't fit one row (the slots would be under 56), so it takes two rows too, losing 128 dp of a 749 dp height.
  - Why left: the slots never shrink, there is one layout, the hand covers the right edge rather than the tools, and on the Nomad in landscape it keeps more page.
  - `PaintActivity.showInkWashBar` puts the bar in a left column again (MonoPaint's `toolScroll` slot is there already). `InkWashBar` gets a vertical orientation (~+80). `WIDE_BAR_DP` goes.
- **Lab:** the test area's S/M/L become the same three size slots and the same picker. The corner sample is drawn at the test size, capped at a quarter of its box.
- **Slot preview** (WildSample ~+40, InkWashBar.showBrush):
  - It follows the icon's loop (`WildSample.LOOP`), at the slot's size and shade.
  - **Recommended: option C.** The line is drawn at its true width while it is at most ¼ of the button's height. Past that, it is drawn at ¼ with a true-size corner dot or arc.
  - Compared in the mockup against A (clipped true width, where a big paint becomes a grey block) and B (always a dot).
  - The cache key adds size and shade.
- **Pressure ramp for the preview:** p = .1 + .85·smoothstep(t/.7). It is light at the start and near full for the last ~30%.
  - **Recommend the lab's loop (WildSample.loop) uses it too.** Then a brush's card and its slot button look alike, and the cards show pressure response, which matters now that pressure has a target.
  - The cost is that every cached sample redraws once. There are no golden checks on samples.

## Plain description and the Wildcard name

- **"This brush"** appears in Simple mode, under the style grid. It is 2–4 short sentences generated from the settings, for someone who doesn't know the setting names.
  - Example: "A wet watercolor on cold-press paper. Darkens at the edges and blooms in light and dark patches. Pressing harder makes it wider. Dries in about 1 second."
  - The order is the order a person notices things:
    1. what it is and what paper it's on (style noun, crisp/grainy/dry, nib and angle, wet/even, soft or crisp edge);
    2. its shape (thick and thin with the nib, tapers);
    3. the marks it leaves (at most the two strongest dials past their threshold);
    4. how it answers the pen (pressure target, speed, tilt);
    5. for wet paint, how long it dries.
  - Only dials past the BrushCaptions thresholds are mentioned: splatter .3, dry .3, tooth .4, wobble .2–.25, pool .4, bloom .35, hollow .4, feather .4, streaks .4, gran .5, backrun .3. Pressure must be over .5, speed over .35 and tilt over .3.
- **In the app: a new `BrushDescription`** (~120, pure, checked on the desktop beside BrushCaptions).
  - It has one `static String describe(Brush)` that builds a list of sentences in that order and keeps the first four.
  - Its phrases are constants next to BrushCaptions' trait words, so the card captions and the sentences say the same things.
  - Built-ins describe their approximation, the same as cards do.
  - `LabSettings` shows it in Simple mode as a text view under the styles (~+15), re-rendered on every change.
  - The JS version is `plainWords` in `src/app.js`.
- **Wild is shown as "Wildcard"** (a placeholder while Elling chooses a name).
  - In the mockup, one constant `WILD_LABEL` (`src/app.js`) feeds the style chips, the STYLE ▾ button, the library filter, card captions and status lines.
  - In the app it is one string in `BrushStyle` (e.g. `WILD_LABEL`) used only for display.
  - The style's place in the list, which share codes use, is unchanged.

## Open questions

- Swatch greys are page greys, so a black swatch makes black watercolour. Are paint defaults of 30% right (today's wash Dark is about 34%)?
- Left bar: should the tab of the picked slot stay visible while its panel is open? It is covered now, but the frame still shows.
- Locks set in Advanced are kept, not cleared, while Simple ignores them. Should switching to Simple clear them?
