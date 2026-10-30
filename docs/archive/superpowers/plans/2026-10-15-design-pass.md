# Design pass: Daily Puzzle Style across Inkwash (plan)

The owner's design system ("Daily Puzzle Style", tokens.json + README, written for
Supernote templates in 300 ppi pixels) applied to the whole app, and Brush Lab made
to match the approved mockup (LabDaily: Manta, Basic mode; module heads as B5e
"light header"). A visual pass: structure and behaviour stay; colours, type,
strokes, spacing, alignment and minimum sizes change. No new full redraws.

## Tokens → code

Both tablets are ~300 ppi, 1.875 px a dp. Everything lives in `Theme` (Inkwash-owned;
changed from `Ui` once the upstream rule in `docs/INTEGRATION.md` landed, so MonoPaint's
`Ui` stays untouched) and the pure `TypeStyle`; `LabUi`, `LabButton` and the drawing
screen read them. No other colour, weight or stroke literal stays in Inkwash's UI code.

| Token | Template px | App | Constant |
|---|---|---|---|
| paper | #ffffff | background | `Theme.PAPER` |
| grey-200 | #dddddd | faintest tint (unused so far) | `Theme.GREY_200` |
| grey-400 / line | #bbbbbb | dividers between cards and slots, disabled | `Theme.GREY_400` |
| grey-600 | #888888 | mid fill (Medium's strip, a slider's band) | `Theme.GREY_600` |
| grey-800 | #555555 | secondary text, end words, captions | `Theme.GREY_800` |
| ink | #000000 | text, outlines, selected fill | `Theme.INK` |
| on-ink | #ffffff | text and icons on ink | `Theme.ON_INK` |
| stroke-fine | 2 | 1 dp, the least (ink only) | `Theme.FINE` |
| stroke-base | 3 | 2 dp: buttons, borders, rules, #bbb lines | `Theme.BASE` |
| header rule | 3 (mockup) | 3 dp under a bar or a module head | `Theme.RULE` |
| stroke-heavy | 6 | 4 dp: THIS BRUSH's frame, the selected slot | `Theme.HEAVY` |
| caption | 24 / 500 | 13 sp, the least anywhere | `TypeStyle.CAPTION` |
| label | 26 / 600, caps, .06 em | 14 sp | `TypeStyle.LABEL` |
| body | 30 / 400 | 15 sp (16 for a menu row) | `TypeStyle.BODY` |
| heading | 38 / 600 | 20 sp | `TypeStyle.HEADING` |
| title | 60 / 700 | 32 sp | `TypeStyle.TITLE` |
| radius | none | square corners; no pills but the bar's label | — |

- **Typeface:** Roboto Mono, the variable `RobotoMono[wght].ttf` (googlefonts/RobotoMono,
  SIL OFL 1.1; the license goes in `assets/fonts/OFL.txt` and THIRD_PARTY_NOTICES),
  bundled in `assets/fonts/`, its weights 400, 500, 600, 700 made once with
  `Typeface.Builder.setFontVariationSettings` (API 26+; minSdk is 27): `Theme.mono(weight)`.
  No italics: the library caption's italic lines become regular.
- **Text scale:** the app keeps its 1.2× font scale (`Ui.largerText`), so 13 sp draws
  15.6 dp. `LabText` models Roboto Mono: 0.6 em a character (unchanged), a line
  1.32 em (2701/2048, was Droid Sans Mono's 1.17).
- **Selected** = ink fill, on-ink text (chips, the mode switch, a style), except the
  owner's two outlines: the selected slot (a 4 dp inset ink outline) and the picked
  module (its 4 dp frame).
- No shadows, gradients or transparency: a disabled control draws in grey-400 instead
  of fading (in Inkwash's own views; MonoPaint's `Ui.setDimmed` stays).

## Where each screen's styling lives

| Screen | Files |
|---|---|
| Tokens, typeface, type styles | `Theme`, `TypeStyle` |
| Lab builders (text, bar, box, rows) | `LabUi`, `LabButton` |
| Lab bar, test area, Load sheet | `BrushLabActivity` |
| Lab column: style grid, Randomize, THIS BRUSH frame, modules, open card, rows | `LabSettings`, `PartCard`, `HandGrid`, `BandSlider`, `SquareSwitch` |
| Lab shelf: TOOLBAR row, TOOL LIBRARY, variations, swap strip | `LabLibrary`, `LoopCard`, `SwapStrip` |
| Slot tile (one helper, both screens) | new `SlotTile`: the loop at the slot's size and grey, number badge, #bbb dividers in an ink border, selected = 4 dp inset outline; `InkWash.tile` draws its loop |
| Budgets | `LabLayout`, `LabText`; checked by `LabLayoutChecks` |
| Drawing bar, tool and layer panels, the label | `InkWashBar`, `ToolButton` |
| Size modal, grey flyout, swatches | `SizeModal`, `SwatchFace` |
| File menu, dialogs, Open/Save sheet | `Theme.restyle`/`dialog`, called from `PaintActivity.menu`, `compactDialog`, `orientDialog`, `DrawingBrowser` |

MonoPaint's header, colour bar and side toolbar (`Toolbar`, `ShadePicker`, the page
panel) are built but never shown in Inkwash (`showInkWashBar`); they stay as MonoPaint
has them.

## Lab decisions (mockup)

- Bar: back, name + pencil, LOAD (outlined), SAVE (ink, floppy), UNDO (outlined), the
  BASIC / ADVANCED square segmented switch at the right; a 3 dp rule under it.
  "Simple" reads "Basic" wherever the user sees it.
- No status line: its 28 dp go to the test area. Each `BrushLab.status` use decided:
  the model keeps writing it (the checks read it); the screen shows only the ones that
  matter, inline: a code that won't read (the Load sheet's note), Copy ("Copied" on
  the sheet), Save ("Saved", its floppy filled, while the brush is in the library).
  Everything else is already visible where it happened.
- No vertical rule between the column and the right side: a 16 dp gap. The STYLE label
  row and the Clear / sizes / greys row share one height and gap, so the style table's
  top lines up with the test area's.
- Modules: 2 dp ink border, the pattern strip, a white head (NAME caps bold, the glyph
  at the right as tall as the name, the description across under them in #555), a
  3 dp ink rule, the choice centred bold, the sample, the slider with #555 end words.
- THIS BRUSH: 4 dp frame, full-width ink bar. TOOLBAR: TAP TO REPLACE and TOOL
  LIBRARY: ink header bars over grids in a 2 dp ink border with #bbb lines.
- Nomad Basic: Style ▾ in the label row, smaller samples, before any text under 13 sp.

## Order of work (commits)

1. This plan.
2. Tokens and font: `Ui` tokens, Roboto Mono bundled, `LabUi`/`LabButton` on them,
   `LabText` metrics.
3. Lab: bar, test area head, column, modules, shelf, `SlotTile`; status line gone;
   Simple → Basic.
4. Drawing screen: bar (slot tiles shared with the lab, a 3 dp rule inside its 56 dp),
   panels, the label, size modal, grey flyout.
5. Menus, dialogs and the Open/Save sheet.
6. Budgets and checks: `LabLayout` and `LabLayoutChecks` at the new sizes; CHANGELOG;
   the lab spec's As-built.
