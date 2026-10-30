# Scribbler Style

My Bad Drawings' look is the owner's **Scribbler Style** (once called "Daily Puzzle Style", then "Inkwash Design Style"; the design system's README
and tokens are the owner's artifact, https://claude.ai/artifact/6ccnsCHTZGBT8VqYy7uZFT). In the app every token lives in `Theme` and `TypeStyle`; nothing else names a
colour, a stroke or a typeface. The words follow `docs/TEXT-STYLE.md`.

- **Shade before line** (the owner, 2026-10-23: "the solid black strokes, let's remove them where possible and rely on
  shade difference to show contrast mostly"): boxes, cards, tiles, chips and panels are told apart by a step in grey
  (grey-200 on paper, paper on grey-200), not by a black outline. Ink is for text, icons and the one thing on or
  chosen. A rule that still has to be there is grey-400. The design pass (2026-10-23, `docs/archive/design-pass-2026-10-23.md`)
  moved the shared pieces over; the rules they follow are under **Components** below.
- **Six greys and nothing else:** paper, grey-200, grey-400, grey-600, grey-800, ink (white on ink). They sit on the
  e-ink panel's 16-step grid, so they never dither.
- **Square corners, strokes 2 / 3 / 4 dp** (base, rule, heavy; a 1 dp fine line in ink only). No shadows, gradients
  or rounded boxes. The one gradient is the **grey bar** (the owner, 2026-10-19: "a gradient displayed like in
  monopaint"), where a grey swatch is set: MonoPaint's color bar strip (`ShadeStrip`), black at the left to white at
  the right in the panel's own dots, in a 1 dp ink frame, a solid black and a solid white end,
  the grey in use marked by MonoPaint's 6 dp black marker under it and shown big beside it.
- **What's on is ink-filled or marked by a tab or a bar:** a chosen tab or chip is ink-filled; every tile and card in
  use has a 5 dp ink tab under it; Brush Lab's picker box marks the size and grey with its tabs, as the side column's. The toolbar slot in use on the
  Tools page has a thick ink underline, 5 dp, across its full width, its mark drawn as it is (the
  owner, 2026-10-23: "some brushes are inverted, so in addition to the inverted look of the brush we need to determine
  another way to indicate that it is active... underline?"), 1 dp under the stroke and 3 dp clear of the card's foot
  (`DrawLayout.MARK`). On the drawing screen's side column every "in use" or "on" mark is a **side bar** instead (the
  owner, 2026-10-23: "it should be a side line, not an underline. too hard to differentiate a solid line from a
  stroke"): a 5 dp ink bar (`DrawLayout.SIDE_BAR`) 2 dp clear (`BAR_GAP`) of what it marks. The slot in use has it at
  its row's right, the row's full height, as the bar down the right side before 2026-10-23. The tool in use (the
  smudge tool, the eraser), Shapes while taken up (in place of the slot's) and Play while a toy is on (the magnets' bin
  open, the page's mirrors on) have it on the column's edge side of their cell, its icon's height, so it never lies between two icons. The Dropper while taken up has it at its cell's right, as Mirrors. The size
  in use is the slider's tab, so it has no bar (the size and the swatch in use had a 3 dp one until 2026-10-24). The
  grey strip's grey in use has a 3 dp tab right of the strip. Template Visibility has no mark (a state, not a tool: its eye says the step). The finger's choices and a menu's chosen rows have the menu's bar at the row's right.
- **Shapes and Play back in the column** (the owner, 2026-10-30: "move shapes button to below visibility and timer,
  and move fun button beside it"; where the bullets below differ, this is current). Later that day Shapes and Show Kinds
  swapped ("the fun button looks a bit like the shapes button .. let's swap the location of the visibility and shapes
  buttons"): Shapes beside the sand timer, Show Kinds beside Play, over the wet indicator. Until then a small row of their own straight
  under Show Kinds and the sand timer, over the wet indicator (`DrawLayout.PLAY_ROWS` 3): **Shapes** (Phosphor shapes)
  in the left cell, its tap the menu of its five shapes and Filled beside it, for the slot in use (the first when the
  eraser, the smudge tool or the stamp tool is), its side bar while taken up, in place of the slot's (a tap on the slot,
  or any tool, puts it down); **Play** (Phosphor confetti) in the right cell, its tap its menu beside it, its side bar
  while a toy is on (`DrawLayout.playOn`: the magnets' bin open, the page's mirrors on). Play's menu: **Magnetic
  Poetry**, **Mirrors**, each marked while on; a grey-400 line and **Replay Page** on a page it can replay. Neither is
  in the main menu now (Settings, Open Notebook, Import; Finger, Left-Handed). Refresh keeps its place over Zen Mode
  (`ghostMargin`). The row is 44 dp on the Manta: upright the white space at the foot is 131 dp (175 before); turned,
  the picker box has no spare to grow into and stays at its four rows (1.35x before); the Nomad turned fits at fit
  0.11, its tray rows 28 dp (30). Nothing folds.
- **More room** (the owner, 2026-10-28; where layout v3 below differs, this is current). Undo and redo left the column
  (two- and three-finger taps undo and redo, `MultiFingerTap`; the edge strip's slide too), and so did Shapes and Play
  (Play is the main menu's row, Shapes in it); later that day the Trash (the Lasso's bar over the piece deletes,
  below). Top to bottom past the picker box: the Dropper and the Lasso; **Refresh** alone in the row under them, the
  ghost, one full e-ink flash, the whole row its target for a finger, 52 dp tall and the column's inside wide on the
  Manta (the owner, 2026-10-28: "move the refresh ghost to a spot on the toolbar, set apart so a finger can press it
  easily"; on the page bar that morning); the wet indicator and the sand timer side by side, the sand timer's icon the
  eyes' size ("shrink the hourglass to match the other icon sizes");
  **Show Kinds** (always the open eye, 2026-10-29; closed while a kind wasn't Full until then; a tap its popup, from the
  main menu's row; Template Visibility beside it until it joined the popup, 2026-10-28); the countdown's row under them while a time runs. Two rows fewer on the Manta and the Nomad upright,
  one turned (the Nomad turned had Refresh beside the wet indicator). The Manta's icon rows are 52 dp and small rows 44
  (44 and 36 until then), so they breathe; the rest stays white space at the foot (`DrawLayout.ROOMY`). On the Nomad
  the rows grow back toward their preferred heights, the tray's turned 26 dp (24 until then). No item in the column was
  marked for Shapes until 2026-10-30: the slot it drew with kept its side bar.
- **Layout v3** (the owner's mockup, 2026-10-26; where the column's order below differs, this is current). Top to
  bottom: Menu alone; the ten slots (eight until 2026-10-27); a gap, then the smudge tool and the eraser, a slot's row each, the owner's
  drawings of them (the spudger and the block eraser, `res/drawable/tool_smudge.xml` and `tool_eraser.xml`, traced, the
  Manta's own pixels, never enlarged, centred in the stroke's place, the side bar at the row's right as a slot's, nothing
  else: no dab of held paint, 2026-10-26); the dot, the size slider and the grey strip; the icon block in 44 dp rows
  (40 on the Nomad, 30 the least): undo and redo, the Dropper (always shown, whatever the tool) and the Lasso, the Trash
  alone in the left cell (under the pickers since layout v4, the owner, 2026-10-26); the small rows: the wet indicator, Refresh (its own row under it, beside it where the column is short), Template Visibility and Play; Finger is the main menu's row (the owner, 2026-10-27), not in the column; the countdown's row under
  them: a Timed Pages page's countdown as before, otherwise the sand timer (2026-10-27, `SandTimer`, its icon
  `ic_sand_timer`, a placeholder for the owner to draw, a small row's cell), or its countdown while it runs, in the
  countdown's words and update. A tap on either opens a light column beside the row as the eraser's long press does:
  Off, 1 min, 3 min, 5 min, 10 min, 30 min (Timed Pages' steps), the one set marked. A plain drawing timer: it starts
  at the pick, runs across pages, pauses while the app is paused, is never saved, never touches a page's clock or lock;
  at zero a light popup beside the row, "Time's up" and Done, closed by Done or any touch (`InkWashBar.timeUp`). A
  Timed Pages page at zero shows the same popup with Next Page between them (on the last page, a new page), and stays
  editable unless the notebook's Lock Pages is on (2026-10-27, the owner: "it's just a guide"; "could the lock be a
  setting in the notebook?"); its countdown row then shows as an untimed page's. No icon on a cover or a
  locked page (a running countdown still shows there). Five gaps part the groups. The brush line stays along the foot, its preview 80 × 32 dp, the line as tall.
- **The side column** (the column pass, 2026-10-23, the owner: "some things are a bit off with it.. layers section is a
  bit janky.. inconsistencies"). One inner margin each side (4 dp on the Manta, 2 on the Nomad, `DrawLayout.edge`) and
  one centre axis. A row of one (Menu) sits on the axis. A row of two uses two equal
  cells meeting on it (undo and redo, the grey strip and the size slider, the Dropper and the Lasso, the tool buttons).
  The far margin takes what rounding leaves, so a row's whole cells fit the inside exactly: a control clipped by a
  pixel can't use the fast display. Menu comes first,
  its row as tall as centres its icon on the page bar's previews, so Menu and the page bar read as one top row (the
  owner, 2026-10-23: "make the menu icon in the upper right in line with the page previews", `DrawLayout.menuRow`). A
  Timed Pages countdown is under undo and redo. The tray rows are 34 dp on the Manta (32 on the Nomad; turned, 32 on the
  Manta and 24 on the Nomad, below), 4 dp over and
  under each stroke (the owner, 2026-10-23: "a tad more vertical spacing between the brushes"), no line between rows
  (the owner, 2026-10-24: "remove lines between preview strokes", a 1 px ink hairline from earlier that day until then). Each row
  shows its brush at the brush's Medium size and full strength, whatever size and grey the slot draws at
  (`ToolSwatches.picture`), as the brush line's preview does: the dot says the size, so picking a size draws no row
  again. The slot count is one number (`ToolSlots.COUNT`): ten since 2026-10-27 (the owner: "we're at 8 now might as
  well have 10"), the two new slots, under the eight, starting on Cheap ballpoint and Permanent marker, two of the
  owner's Brush Studio pens (`ToolSlots.FIRST`); a toolbar kept with eight (the app's, or a notebook's own,
  `ToolSlots.Toolbar.parse`) keeps them in place and gains the two. Brushes on Bar goes to ten. Ten fit every screen
  either way up with nothing else dropped (`DrawLayoutChecks.tenSlots`): the tray's rows give way evenly, the rest as
  with eight. Tray rows with eight, then ten: Manta upright 34, 34; Manta turned 34, 32 (the icons, faces and small rows
  a dp shorter); Nomad upright 32, 32; Nomad turned 30, 24 (the least a tray row has, 30 until then; the icons, faces
  and small rows at their least there, as with eight; the smudge tool's and the eraser's drawings scale down to fit,
  never up). Under the tray, the
  **readout** on top, the **picker box** under it (the owner's drawing, 2026-10-27: "i'd like to try using it to
  replace the shade picker and size select"; `SwatchBar`, `res/drawable/shape_box_shade.xml` and `shape_box_size.xml`,
  the drawing `docs/icons/picker-box-drawing.png` cut into its two triangles, traced with potrace until 2026-10-29,
  then drawn clean, the owner: "doesn't need to look hand drawn now... match the line size from iconoir": straight
  edges, in dp at the Manta's upright size; then, of a solid and an outlined version, the outlined, "b 164 looks
  good", with "the pointy corners ... a little thinner at the end ... have the triangle tip hit the furthest corner.
  they need to be thin to demonstrate the finer point sizes": each triangle a 1.5 dp ink outline (Iconoir's stroke;
  `shape_box_size` the slider's; the strip's outline went later that day, "remove the stroke outline from the shade side
  so it's easier to see the right shade", so its greys run to its edges), built as the triangle less one 1.5 dp
  in, so each sharp corner is a mitred sliver about 10 dp long closing to a point, never a cut-off end; the strip's
  white tip in the box's top-left corner and the slider's fine tip in its foot-right one, the long sides parallel with
  an even 1.5 dp gap, the other ends cut by the box's top and foot; a 1.5 dp round corner at each right angle): a tall box cut by
  a white diagonal from its top left to its foot right, its lower-left triangle the **grey strip** and its upper-right
  one the **size slider** (side by side until then, the strip on the left: the owner, 2026-10-27, "move the size
  selector to the right side; shade to the left"; the slider left from the owner's sketch, 2026-10-24, "DO IT THIS
  WAY", before that), and the **Dropper** and the
  **Lasso** under that (below), in the room the four sizes and the four swatches had. The strip runs white at the top to black at the foot and the
  slider broad at the top to fine at the foot (the owner, 2026-10-24: "flip the triangle for the size so it goes down",
  "same with the shade picker"). Brush Lab holds the same box (`PickerBox`) turned a quarter, lying across, so its
  greys run black at the left as MonoPaint's colour bar. The box is as wide as the
  strip and the slider were side by side, two squares and the gap (56 dp on the Manta, 50 on the Nomad), and half as
  long again as the four sizes' squares were as far as the column has room to spare (the owner, 2026-10-29: "stretch
  the shade selector and size selector a bit to allow for more fine control? maybe 1.5x the height", then "grow only
  into spare room"; `DrawLayout.BOX_ROWS`, `DrawLayout.Item.BOX`): four rows of faces fitted with the rest, then the
  spare room, before the gaps take theirs, so no row gives way for it: 180 dp on the Manta upright (120 before), 164
  turned, 162 on the Nomad upright (108), 127 turned (the gaps there 2 dp, 5 before). Both run the box's length, so a
  step is finer: each of the 65 greys about 4 to 4.5 px of the strip on the Manta and the Nomad upright (2.6 to 2.9
  before), and a dp of the slider 3 to 3.5 % of size (5). The strip's triangle is the strip's grey in the panel's dots, white at its point at the
  top to black along the foot, inside its ink outline; the slider's an ink outline on paper, broad at the top to a point at the foot. Each keeps its tab on the
  box's outer side, the strip's left of it, the slider's right of it, at the same heights as before: the same ranges,
  the same per-tool memory, Undo, the strip's long press (Opacity) and rub, the slider's readout. The two are views
  laid over each other, the whole box each (`GreyStrip.boxed`, `SizeSlider`); a pen goes, for its whole gesture, to the
  triangle it lands in, on the white gap to the nearer one, in a tab's room to that tab's (`DrawLayout.boxShade`, the
  straight diagonal), and to the slider while the strip is hidden (the eraser, the smudge tool). Both tabs move as the
  strip's always has (mode 7, the tab's strip alone); the triangles never draw again. With the column on the right
  (Left-Handed) the box is mirrored, the strip's triangle lower right and its tab on the column's outer side, as the
  Opacity strip already was. The Opacity strip keeps the old standing bar, a cell wide. The readout (the owner, 2026-10-24: "put the size selected and the current shade below",
  then "merge the current size and shade, it can be one thing", `SwatchReadout`) is one dot on the column's axis in the
  swatches' two rows: the size in use at its true size up to the room it has, filled with the grey in use in the panel's
  dots, a fine ink edge round a light grey or a dot too small for its dots, paper inside that edge for the eraser and the
  smudge tool, on plain paper, no frame round it (the owner, 2026-10-27: "remove square frame from toolbar"). It draws only when the size or the grey changes, on a lift. It has no anti-aliasing, its dots and edge
  ink and paper, so its change goes through the panel's binary update with the tabs, no grey flash. The Dropper and the Lasso share the row under it, a cell each (the owner: "lasso can move beside
  picker"). The **wet indicator** sits alone on its row under the icon block (the owner, 2026-10-26: "move wet to near the
  pickers"; on the brush line after the name from 2026-10-24), Refresh's row under it, then Template Visibility and
  Play side by side. The brush line's words keep a fixed room (`DrawLayout.LINE_NAME`, 310 dp, cut with … past it), now a Basic
  tool's description after the name. Straight under the slots the smudge tool, then the eraser, a row each. **Finger** (what one finger
  does, its tap the finger's choices) is the main menu's row (a column button only from 2026-10-26 to 2026-10-27). Groups are set
  apart by space, no rule: menu and undo, the slots with the smudge tool and the eraser, the size and greys with the
  Dropper, the Lasso, Play and the drop. The gaps between them are all one height,
  and grow evenly where the column has room (up to four times their least, `DrawLayout.GAP_MOST`). A blurring smudge
  brush shows MonoPaint's blending stump, so the drop means the wet indicator alone, its outline dashed while the page is
  dry, half full while paint moves, full while paint rests wet (the owner, 2026-10-23: "the wet icon seems like it's
  always on now": Phosphor's solid outline read as on). Its tap dries the page as it would dry left alone, only sooner: a wash runs on to its end
  (spread, settled, its edges formed), with no wait between its steps, and shows once, MonoPaint's blow dryer blowing
  meanwhile (the owner, 2026-10-29: "looks like the hair dryer just stops the image as it is instead of accellerate the
  drying processing"; until then the tap stopped it where it was). The tool buttons (the refocus, 2026-10-24): the eraser on its
  own row (the owner, 2026-10-24: "place eraser button on its own", beside the smudge tool from 2026-10-23), the smudge
  tool, then **Play** (below) and **Template Visibility** (below), three rows. Picking the eraser or the smudge tool is
  like picking a slot: the old and new marks, the slider's tab, the dot, and the grey strip hidden or
  shown, in one update of the column, with no Android redraw of it (the owner, 2026-10-24: "switching to eraser, brush
  selector blinked"). The stamp tool, Finger, Magnetic Poetry, Mirrors and the layers left the column then:
  the toys are Play's, Finger the menu's row, the layers Page Settings'. Nothing folds now: the Nomad in landscape fits
  at the least heights, icons 40 dp.
- **Play from 2026-10-28 to 2026-10-30** (the owner: "remove fling and surprise me", "remove stamp because that is in the tools
  section"; the column's button again since 2026-10-30, above): a row of the main menu, Phosphor confetti, marked (the menu's bar) while a toy is on: the magnets' bin
  open, the page's mirrors on, or Shapes taken up (`DrawLayout.playOn`). Its menu beside Menu: **Magnetic Poetry**,
  **Mirrors**, **Shapes** (its five shapes and Filled, as the column's button gave them, for the slot in use), each
  marked while on; a grey-400 line; **Daily Prompt** and **Replay Page** (from the main menu's own rows). No Stamp (the
  Tools page's), Surprise Me (Notebook Settings' Tools still has it) or Fling (Brush Lab's dial). The stamp tool's own
  menu (`InkWashBar.stamp`) has no way in for now. Where the bullet below differs, this is current.
- **Play** (the refocus, 2026-10-24: "a paper notebook that feels like real media, useful first, then a little
  mischief"; Phosphor confetti, the owner's pick; `DrawLayout.PLAY`, `PaintActivity.playMenu`): one button for the toys,
  its tap a menu beside it (`Theme.menu`, as every menu), grey-400 lines between its groups: **Magnetic Poetry** (the
  magnets' bin), **Mirrors** (their list), **Stamp** (the stamp tool taken up; in use, its menu beside Play: its card,
  Cut Stamp, Turns, Spacing, Ink Fade); **Surprise Me** (the notebook's toolbar rolled; again, rolled anew), shown
  in a notebook; and **Fling**, turned on or off on the slot in use's brush (its Medium's Fling dial at .5, or 0). No
  Diary Page and no ready brushes since the owner, 2026-10-27: the page's template is in Page Settings, the Basic
  tools in the tool picker. Each row
  is marked (the menu's bar) while on. Play's side bar while a toy is on, as a tool's.
- **Self-Portrait Prompt** (the owner, 2026-10-30: "('daily prompt', maybe rename it as 'self portrait prompt'? since
  it's not always daily unless it's baked into the notebook?) actually in that case put it in notebook settings as an
  optional thing, new for every new page"; the Daily Prompt, Play's row, until then): a notebook setting
  (`NotebookOptions.selfPortrait`, Off by default; `SELF` in `BookOptions` and `TSRQ` in the recovery, each written only
  when on). While it is on, every page added to the notebook (the page bar's + tile, Time's up's Next Page, Add page)
  is dealt a card at random as it is turned to (`PaintActivity.addPage`), its heading "Self-Portrait Prompt, 3 Min",
  Start and Deal Another as before; pages there already are untouched, and there is no today's card. A page is dealt
  one only as it is added: a card left unused goes when the page is left (below) and is not dealt again on coming back,
  so a page left blank never asks twice. The cards' file keeps its name and its lines (`DailyPrompt.FILE`).
- **A prompt card on a page**: dealing a card gives the page drawn on its line at once, and the page shows it again
  on opening (once a session). A card not acted on goes (the owner, 2026-10-29: "for prompts, if it's closed without
  doing it, remove it from the page"): when the page is left (another page or layer, the shelf, the app paused or
  closed) with nothing drawn since the card was given, the page loses its card, a change saved like any other and not
  an Undo step, and is a blank page again everywhere blank pages count. Nothing drawn means nothing on the page but the
  card, or, on a page drawn on before, no Undo step made since (or all of them undone), which is known while the page
  stays open; Start alone is not drawing. A page found on opening with nothing but its card (the app closed without a
  pause) loses it then, unshown (`ToneDocument.dropUnusedPrompt`, `PaintActivity.leavePrompt`). A Diary Page card's
  template stays on the page.
- **The tool picker** (the owner, 2026-10-25: "change the library to a smaller set of tools (the ones with drawings
  mostly) and add an advanced button which pulls up the library we have now. like, have it be a tiny modal like i
  suggested for the confetti menu"; "long press on any slot to open. single tap selects the tool"; `DrawLayout.PICKER`,
  `PaintActivity.toolPicker`): a slot's long press opens a small menu beside it (`Theme.menu`, placed as Play's, on
  the other side for the left hand), a grid of the Basic tools the owner drew ("just whatever i drew"), each its
  drawing (`res/drawable/tip_*.xml`, traced vectors sized in the PNGs' px; Springy brush still `res/drawable-nodpi/tip_springy_brush.png` for its grey tip; tip to the right) over its name in
  captions; pens, markers, brushes, then dry media, the mischief among its kin ("mischief in with the other drawn
  tools"). Three columns, more where three would run off the foot (four on the Nomad turned). The one the slot holds
  has the menu's bar at its right and a bold name. A tap puts it in the slot and selects
  the slot. Under them **Brush Lab** (on the slot's brush; "brush studio could be opened directly from the tiny
  popup") and **Advanced** (the Tools page on the slot, as before); at its foot the switch **Tools First** (2026-10-28,
  marked while on): on, the long press opens the Tools page straight away (turned off there). A tap on a slot still only selects it.
- **The size slider** (the owner, 2026-10-24: "change the four size options to a quick size slider. I find myself
  wanting to fine tune the size often", `SizeSlider`, `DrawLayout.FACE_ROWS`). A slider against PHILOSOPHY rule 2, by
  the owner's choice (see PHILOSOPHY.md). The picker box's upper-right triangle since 2026-10-27 (above); the left
  cell beside the grey strip, as long as it, until then (the owner's sketch, 2026-10-24). Its track was a 1 dp ink outline of a right triangle, the square's width at the top (broad) to a point
  at the foot (fine), its straight side down the left by the tab and its right angle at the top (the owner, 2026-10-24:
  "size picker is still blinking. make it an outline shape instead?", then "triangle shape should be right triangle".
  A grey-600 right triangle, then an outline pointing down from the middle, until then). The size in use is the strip's
  ink tab, 3 by 10 dp, left of the triangle along its straight side, on the column's outer side. The triangle never draws again. A drag moves
  the tab alone, exactly as the grey strip's tab moves: the tab's strip of the cell, 6 dp wide
  (`DrawLayout.sliderMarkRoom`), goes to the panel in one small grey update (mode 7) a move, nothing else drawn, neither
  by the app nor by Android, and no clean after the lift. From 2026-10-24 to 27 its moves went through the panel's
  binary update (mode 5) with a two-pass redrive of the tab's strip a quarter second after the lift; the owner saw it
  blink and the tab sometimes half drawn ("weird ghosting glitch ... the shade picker doesn't have this same problem"),
  so it is the strip's path again (see INTEGRATION.md, "Size tab as the shade tab"). The lift changes the
  dot alone, one binary update: the slot's row and the brush line's preview show the brush, not its size, so they are
  not drawn again. The size shows in the readout over it once the pen lifts (a readout drawn during the drag would
  queue a second panel update behind the tab's). It spans every size the engine draws, 1.5 to 360 px, the same for every brush, on a log scale
  (`ToolSwatches.position`, the old size modal's), so the sizes under a millimetre take a third of it and a dp of drag
  is under 5 % of size anywhere (under 4 % on the Manta and the Nomad upright since the box grew, 2026-10-29; 6 %
  before), half pixels under 10 px, whole ones above. The eraser has a size of its own, kept
  apart from the brushes' and kept as tools are switched, from 4 px (twice its tip) up (the owner, 2026-10-24: "eraser
  size stays same size for eraser". It snapped to its four, S to XL, until then, so most of the slider left it as it
  was): the slider sets it where the pen lifts, its broadest at a hard press (`ToolSwatches.useEraserSize`). A pen landing on its side of the box's diagonal takes it (the whole cell until 2026-10-27). The lift sets the size for the slot (or the tool) in
  use, so a tap jumps there. No long press: the Sizes window is the Tools page's (the four
  shared sizes stay there as presets, and a slot set on the slider shows none of them marked).
- **No name on a pick, and the hover ring.** Picking a slot (or the stamp from Play) shows nothing beside the column:
  its mark moves and the brush line under it says the brush (the owner, 2026-10-25, to make the screen as snappy as
  MonoPaint's; from 2026-10-22 its name showed beside it a moment, an extra window on the panel each tap). The label
  beside the column stays for what has no other place: a slot's brush changed (back from the Tools page, Undo), a grey
  the Dropper picked, the mirrors' notes, Cut Stamp's words. It is the menu's words, bold, with no box round them and
  nothing behind them, the page showing through ("for brush name tooltip, don't use a box with a stroke, just the
  text", the owner, 2026-10-24). The pen hovering over the page marks the nib it would
  lay with a few ink dots, 2 px square, on the nib's outline (eight until 2026-10-29; now one a 24 px of the ring, a lone dot where the finest lands, five at most; off with Nib Preview), and nothing else ("make hover indicators more subtle.
  right now they can disrupt the image", a dashed ring of black and white dashes and a second dotted ring for the firmest
  nib until then, `DrawingPad.OUTLINE_TICKS`). Each dot is its own small patch, put back as the pen moves and when it
  goes. A notebook's **Nib Preview** (Notebook Settings, Off and On, on by default and in every older notebook; the
  owner, 2026-10-29: "for the dot preview.. have it enabled by default but able to be disabled in the notebook
  settings?") turns these dots off in it; the Dropper's ring and the eraser's ring while erasing stay. A drawing that
  isn't a notebook shows them.
  Said "Size: 4.2 mm", and a screen reader's scroll steps it.
- **One grey for everything** (the owner, 2026-10-24: "let's go with the procreate method", `ToolSwatches.CURRENT`).
  As in Procreate, the grey is not part of a brush: there is one current grey, and every brush and tool that paints
  uses it (the slots, the stamp, Fill in and Filled Shape, a light pen's white ink). Switching brushes keeps it. The
  grey strip (the side column's and Brush Lab's picker box), the Dropper and the grey bar on the Tools page set it. The size stays each slot's own.
  The smudge tool and the eraser have no grey (the smudge carries paint). Grey Shift's grey is the stroke's alone and
  leaves the current grey as it is, and Grey Shift's and Gradient's Toolbar set are still the four grey swatches. A kit
  brings brushes only and keeps the grey. The greys each slot kept before, and a notebook's own toolbar's, still read
  (an older build reads them as ever) but nothing paints at them: the first launch takes the selected slot's grey (the
  stamp's, or the first slot's while the eraser or the smudge tool is selected) as the current grey, so the brush in
  use draws on as it did. Share codes never held a grey.
- **A named brush brings its own size and grey** (the owner, 2026-10-28, reviewing their saved brushes: "consider the
  ideal size and shade for each tool... since we have to set that now"; `ToolSwatches.LOOK`). Each tool keeps its own
  size and grey (2026-10-27), but a library brush may carry a px and a darkness, kept under its share code (its name's
  slug and settings) apart from the library's lines; put in a slot (`ToolSlots.use`: the picker, the Tools page, a kit),
  the slot takes them (`ToolSwatches.bring`). A brush without one keeps the slot's, as before; a white brush carries a
  px alone. Undo takes back the brush, not the size. The owner's table set them once, with the saved brushes' new names
  and removals (`LibraryCleanup`, matched by name and code exactly; a library read back from its backup after a
  reinstall gets its sizes and greys again).
- **One brush a name in the library** (the owner, 2026-10-29: "in the library there are a bunch of duplicated names...
  can we remove the older versions of those?"). Names match with case and spaces round them aside
  (`BrushLibrary.sameName`). Once per install (`LibraryCleanup.sameNames`, key `brush_library_names`, after the
  owner's table), of brushes under one name only the newest stays: the latest stamp (saved or used), a tie the later
  line (`BrushLibrary.keepNewestNames`); the dropped ones' own sizes and greys stay unused in the store, the kept ones'
  untouched, and the backup file follows. Since then no save adds a second: `BrushLibrary.save` numbers a taken name
  (`freeName`, "Polite Smear 2": an imported code, a name given), and Brush Lab's suggestion passes by the library's
  names for the generator's next (`BrushNames.name(parts, taken)`), so Save as New takes a new name before a number.
  The duplicates came from saves before numbering (2026-10-15), names matched exactly (case apart: "Wet wash" beside
  "wet wash") and imports (`BrushImport`), which never numbered. Numbered siblings ("Pooling brush 2") are names of
  their own and stay. Recent (`ToolSlots.recent`) is not the library and may hold a name the
  library has; searching the Tools page lists a brush once by name and settings, not by name.
- **A brush's picture is the brush, not the grey** (the one grey, 2026-10-24): every preview (the column's rows, the
  brush line, the Tools page's slots, cards and This Brush, Brush Lab's loop and Open sheet, kits, the library)
  draws the stroke at full darkness whatever grey is in use, black for ink (`WildSample.slotTray`, `WildSample.PREVIEW`).
  A brush whose stroke is light (Blend's White, as the White gel pen, and Crayon Scratch) is a light stroke on a dark ground
  (`WildSample.darkGround`). Every other tile stays paper (the owner: grey and dark tiles ghost on e-ink).
- **The Dropper** (the owner, 2026-10-24: "add a dropper tool so people can quickly match color", Phosphor Bold
  `eyedropper`, `DrawLayout.DROPPER`), the left cell under the readout, the Lasso beside it (the owner, 2026-10-24:
  "lasso can move beside picker", beside the wet indicator before, under the grey strip before that). A button, not a hold on the strip, because a hold there would hide a mode
  inside the strip and a slow tap would pick a grey. A tap takes it up: its side bar at its cell's left, the slot's
  given up meanwhile, as the Lasso's. The pen hovering or pressing on the page shows a ring round its tip, 28 dp across
  with a 10 dp hole the page shows through, the band filled with the grey the page shows under the tip, ink rings edged
  in white either side (the owner, 2026-10-24: "dropper is confusing.. the selector is offset", a disc 40 dp over the pen
  until then). It is the hover ring's patch, put back as it moves and when the pen goes. The grey is read: every layer of the stack with what shows through, the paper's shade,
  never the template's guides (`ToneDocument.shownTone`). The lift sets that grey for the tool in use, as a tap on the
  strip would (a wash at the grey it lays, its darkest its black's), says it beside the strip, and puts the Dropper
  down, so the same brush draws on in the picked grey. A tap on it again, or any tool, puts it down with nothing picked.
  Hidden with the strip (the eraser, the smudge tool).
- **The Lasso** (MonoPaint's Lasso, merged 2026-10-23, the owner: cut and paste): the right cell beside the Dropper, Phosphor
  Bold `lasso`, a tool, so it has the side bar on the column's edge side while in use and the slot gives its bar up
  meanwhile. A tap takes it up, and the pen's strokes are then loops in Cut Stamp's marching ants. What the layer drawn on
  holds inside the loop, wash and ink with the masking fluid over them, floats as a piece in MonoPaint's frame: a 2 dp
  ink outline edged in white, a dot at each corner to size it (its shape kept) and a knob on a stem to turn it,
  settling on quarter turns. The pen on the piece moves it, off it puts it down. Choosing another tool puts it down.
  Undo puts it back. With a piece floating, a tap on the Lasso opens its menu (`Theme.menu`, as every menu): **Cut**
  (kept to paste, gone from the page), **Copy** (kept to paste, the piece floating on) and **Delete**, then **Paste**
  once something is kept (the owner, 2026-10-25: "there should be options for lasso ... fast delete ... or cut. and
  then the usual copy"). With nothing floating and something kept, the tap opens Paste alone. Paste puts any floating
  piece down, then floats a copy of the kept one where it was cut or copied, on this page or another of the same size,
  to drag into place. Each is one Undo step; the kept piece lasts until the next Cut or Copy (not saved). Turning the
  page carries a floating piece along as a copy: the page left stays as it was, the next page or layer takes it where
  it is put down.
  **The Lasso's bar** (the owner, 2026-10-28: "in supernote there is just one lasso tool... select something and then
  when the bounding box appears, above it is a toolbar offering choices for what to do with it. we could merge the
  lasso trash and the lasso"; where the menu above differs, this is current): with a piece floating, Cut, Duplicate and
  Delete in a light row (Duplicate in Copy's place, later that day: "it makes sense to have duplicate instead of copy";
  the piece goes down where it lies and a copy floats 40 page px down and right, carried to another page as any piece is) (`InkWashBar.lassoBar`, the label's popup, `Theme.menu`: paper, a fine ink edge, no grey,
  partial grey, what it covered alone cleaned as it closes), 8 dp over the frame and its knob, under them with no
  room over them, centred on the piece and kept on the page. The pen on the piece hides it, and it shows again where
  the piece lands; it goes with the piece (put down, cut, deleted, the Lasso put away). A pen tap on it lands (it is
  kept on the pen's down as Show Kinds' popup is). A tap on the Lasso no longer opens Cut, Copy and Delete; with
  nothing floating and something kept, it opens Paste alone. A locked page refuses Cut and Delete with its note, as
  before. The Trash's button (2026-10-25 to 2026-10-28) is gone: the edge strip's two-finger hold (`EdgeHold`,
  `InkWashBar.edgeTrash`) is the loop that deletes what it rings (`DrawingPad.lassoDeletes`), the Lasso marked while
  held.
- **Template Visibility** (the owner, 2026-10-23: "if there is a template, a way to adjust its visibility quickly on the
  toolbar", `InkWashBar.template`, `DrawLayout.TEMPLATE`): the second row's right cell, shown only while the page drawn
  on has guides (else its cell is empty). Its icon is the step's eye, the menu's Template row's (Phosphor eye fill for
  Full, duotone for Half, light for Faint, eye-closed for Hidden, `PageTurner.STEP_ICONS`; Iconoir's since 2026-10-29, above). A tap steps Full, Half,
  Faint, Hidden, then Full again, with a note ("Template faint"). A long press opens the four as a menu
  beside it, the step in use marked. **Since 2026-10-28 (the owner: "add the template visibility there whenever there is
  one... merge them") it is the Template eye in Show Kinds' popup, first, only while the page has guides; the button and
  its long-press menu are gone.** One state with the Template screen's chips (the menu's Template row was cut, 2026-10-24)
  (`NotebookOptions.guidesShown`, set through `PaintActivity.showGuides`). No side bar: it is a state, not a tool.
- **The layers** left the column at the refocus (2026-10-24, the owner: "layers are not important i'm finding"): a page
  that has layers keeps them and shows them as one page; **Page Settings' Layers row** (below) draws on one of them, adds
  one on top, or merges the old in-page layers. The layer menu (New Layer Below, Duplicate, Merge Down, Delete, a
  layer's step) went with the column's group; Undo still takes a layer change back.
- **Each kind of mark keeps to its own layer** (the owner, 2026-10-27; spec `docs/superpowers/specs/2026-10-27-kind-layers-design.md`):
  inside every page layer the engine keeps dry media, washes, pen and opaque paint apart, stacked in that order whatever
  order they were drawn, so the eraser's Pen, Paint and Dry Media take exactly their kind. A smudge with no paint of its own
  blends its Medium's kind (a stump: dry media). Old pages' paint stays as one layer under all of it.
- **Textures are Paint** (the owner, 2026-10-29: "I'd love to have a way to erase one or the other"): a Stipple, Hatch or
  Hand Hatch tip, Print Shading and Screentone lay the Paint kind whatever their Medium (`ResolvedBrush.medium`), on the
  wash plane under pen, so Pen erases only pen lines, Paint takes textures and washes, and Show Kinds' Paint step fades
  them. Alone on the paper they look exactly as before; where they cross pen, pen now lies on top. Marks drawn before keep
  the kind they were drawn with. Stamp and Spiro tips keep their Medium's kind. The eraser's live plane (Screentone's
  scrape) is the ink layer's Paint plane when it takes paint.
- **A print over prints replaces them** (the owner, 2026-10-29: "go over that area again and make it the same pattern,
  but lighter"): a Print Shading or Screentone stroke of its own Blend, not opaque (`ResolvedBrush.replaces`), sets its
  plane's print tone to its own under its footprint and clears the plane between its dots, so a lighter one over a darker
  one leaves the lighter pattern; pen (its own plane, over) and washes (under) stay. Its soft edge keeps that much of what
  was there (`ModularCompose.edge`, the tip's tone read from a pixel's four neighbours), so strokes of one tone side by
  side print the same dots, no seam; such a stroke composes every pixel it touches (no skip of unchanged ones) and is kept
  as a smear (`StrokeTones.smeared`). These prints no longer lay light over dark: the clear shows what is under them.
- **Show Kinds** (the owner, 2026-10-28: "same method as the template view levels... and even the same icons", "maybe in
  the notepad menu", then "per notebook is not necessary, just per page"): the column's eye beside Template Visibility
  (the main menu's row until later that day; its eye closed while a
  kind wasn't Full on the page drawn on until 2026-10-29: "use the open eye icon instead of closed")) opens one popup (later that day: "just one popup where you
  can adjust the visibility of each type at the same time. use the eyes as the button below the names"): Pen, Paint
  (washes and opaque paint) and Dry Media side by side, Template before them while the page has guides, each with its step's eye under its name
  (`PageTurner.STEP_ICONS`); a tap on an eye steps that kind Full, Half, Faint, Hidden, as Template Visibility's tap
  (`InkWashBar.kindsPopup`); it stays open while its eyes are tapped, until the pen goes down
  elsewhere. Kept per page, in its file
  (`ToneDocument.kindsShown`, a `KSHN` section; old pages all Full), not in Undo; another page shows its own. The screen
  alone: each kind plane composites at its step's strength, the guides' (`ToneDocument.visibleLayers`, the screen's layer
  steps from bit `KIND_STEPS`); the page's composite, its copies and pictures, and the base's old paint stay whole. A
  step change shows the page again once. A brush whose kind (its Medium's, `ResolvedBrush.medium`) isn't Full says so
  when taken up ("Dry media is faint"); a stroke of a Hidden kind is refused, "Pen is hidden, see Show Kinds"
  (`PaintActivity.allowStroke`); the eraser never takes a Hidden kind (`EraseKind.takes`). Clear page still clears all.
- **Nothing is numbered or cornered** (the owner, 2026-10-19): slots have no number badges anywhere (the bar's tray,
  the Tools page's Toolbar); a slot is its brush. Faces have no ▾ corner and the bar has no
  Edit buttons (the owner, 2026-10-19: "instead of edit buttons, we use long press as a way to customize"): a long
  press on a tray row opens the tool picker on its slot (its Advanced the Tools page), one on the eraser Clear Page. On the Tools page
  one on a size opens the Sizes window on that face (all four sizes at once, its greys gone with the one grey,
  2026-10-24). A rub on the column's grey strip (oil paint) is movement, which calls the long press off.
- **The page bar** (the owner, 2026-10-19: "use the supernote style and put it on a thin bottom bar, same style as the
  document reader"; `PageTurner`, `DrawLayout.pageBar`): a thin strip the page's width, 40 dp tall (36 on the Nomad),
  with no rule and nothing else framing it. On the drawing screen it runs along the top of the page, from just past the
  side column (the owner, 2026-10-23: "moving the pages bar to the top"). The page starts under it, 1 dp of paper
  between them. On the drawing screen (the tracing-paper spec, 2026-10-20, "The bottom strip"), from the end away from the column
  (clear of the drawing hand; since 2026-10-28, where this differs): Zoom, the number, the previews (`barParts`;
  Refresh at the far end that morning, back in the column since). Until then: the Notebook button, the Paper button (Phosphor Bold `file` for Plain, `copy` for
  Tracing, `stack-simple` for Glass; a hold shows the Papers as a column of chips, icon and word, the one in force
  ink-filled), the page and its count in one line in the LABEL style ("3 / 24"; on a
  cover "Cover" or "Back") between ‹ and ›, then a strip of tiny previews of the covers and pages round the one drawn on, 22 × 30 dp
  with 4 dp between (19 × 26 and 3 on the Nomad), each in a 1 px grey-400 hairline. Between the last page and the back cover is the **+** tile (the owner, 2026-10-24: "the + should be where the auto new blank
  page used to be, same idea"): a preview's size with the same 1 px hairline frame, Phosphor `plus` centred in it, a tile of
  the strip like the pages (`StripGesture.tileOf`: front cover, pages, +, back cover). When the window round the page drawn on
  does not reach it, it is pinned in the window's last slot (taking that tile's place), so a page can always be added. A tap adds a new page at the notebook's end, on the notebook's template and Page Colour,
  and turns to it. Not draggable, no long press or riffle; screen-reader label "New page". No Undo (adding a page never had one,
  and Delete takes a blank page away without asking). Past 250 pages it says "Notebook full, 250 pages". A notebook's pages
  are the pages drawn or added: no blank page waits at its end (one did until 2026-10-24, and an old notebook's goes when it is
  opened, only when it is a page alone, blank on the Page Colour, with nothing of its own and not the page drawn on, not as a
  change, `BlankPages.opened`). › from the last page turns to the back cover, and Timed Pages' Next Page there adds a page as
  + does. The previews' frames: the page drawn on in a 1 px ink one
  (every frame one weight, the owner, 2026-10-24: "use same stroke size for page outlines on top", then "strokes around
  pages on top toolbar should be hairline". 3 dp and 2 dp, then 2 dp all, until then)
  with a 4 dp ink tab under it (the 40 dp bar holds no more with 4 dp clear of the screen's top edge, the tab along the
  bar's foot next to the page), a page showing through
  it (tracing paper's ghost) a 3 dp grey-600 tab, a Hidden page's preview at 35 %; nothing moves but in steps. A hold on a page opens its Page Settings (below) as soon
  as the hold is seen, the pen still down (the owner, 2026-10-24: "have the page menu load right away, don't wait for
  pen lift"); so a hold no longer lifts a page to drag, and Page Settings' Move row moves it. The page is that much shorter, so the bar never lies over the drawing. Inside a notebook the same bar has ‹ and › either side of the number (icons
  black on paper, greyed grey-400 where they can't turn), without the Notebook button, under the strip of pages; there the screen has one bar at the
  top: ‹ Notebooks, the name (two lines at most, a tap renames it), Draw (ink-filled), Use on Cover and Delete Page,
  the last two their icons alone (`ic_cover_picture`, Phosphor's trash) on the Nomad in portrait.
- **Zen by gesture** (the owner, 2026-10-30, as MonoPaint's fullscreen): a two-finger double tap on the page turns Zen
  Mode on or off, the second tap lifting within 350 ms of the first (`DrawingPad.ZEN_TAP_MS`). A lone two-finger tap
  still undoes, after that wait; the pen landing, a finger's stroke, a page turn or leaving the page does a waiting
  Undo at once, first. Two undos tapped faster than that are Zen instead (the owner chose this over a two-finger hold).
- **The brush line and Zen** (the owner, 2026-10-23: "keep the zen in the bottom left (align a bit better pls) and you
  can put the selected brush indicator to the right along the bottom"). Zen's button (Phosphor circle, unfilled, while
  off, circle fill while on, the owner, 2026-10-24: "change dotted circle for zen: circle unfilled and filled for zen
  mode", circle-dashed while off from 2026-10-23. Menu's size, `DrawLayout.zenCell`) sits in the foot corner under the side column, centred on the column's
  axis. It is in the same spot with Zen on. Its cell is as tall as the column is wide, so its icon is as far from the screen's foot as from the side edge. The column runs from the top of the screen down to it. The brush line
  (`InkWashBar.brushLine`, `DrawLayout.BRUSH_LINE` 32 dp, as tall as its 80 × 32 dp preview, 150 × 60 px on the Manta; 18 dp with a 40 × 14 preview until layout v3, 2026-10-26) runs along the screen's foot from 14 dp past Zen's cell (`LINE_GAP`, the owner, 2026-10-23: "move the active tool info over a bit, so it's separate from the zen circle"), vertically centred on Zen's icon. It
  shows the tool in use's mark, then its name and words, left-aligned on one line, and the app's notes at its far end.
  The canvas ends above the line's words (`DrawLayout.footHeight`). Only the mark and the
  words take a tap (Brush Lab on the slot). The empty line after them doesn't, so a palm resting at the far corner opens
  nothing (the owner: "i am worried it will be triggered by a palm accidentally.. best to shift it to the left"). With the
  column on the right, all of it is mirrored.
- **Left-Handed** (2026-10-23): a row of the drawing screen's menu, marked while on (the menu's bar at its right). It puts
  the column on the page's right at once, and with it Zen Mode's corner, the brush line (reading from Zen outward), the
  page bar (the Notebook button at its far end from the column) and every menu and popup beside the column, toward the
  page. MonoPaint's setting (`PaintPreferences.toolboxRight`), its old Settings window not reachable in My Bad Drawings. The page gets everything between the page bar and the brush line.
- **Import** (2026-10-29; Import PDF and Import Image until then, the owner: "so it'd be just one import thing"): one row
  of the menu's first group, Phosphor `file`. Its picker is a dialog as wide as the screen (the owner: "maybe bigger
  modal ... more like supernote file explorer"): the folder's crumbs under the title (Storage, the Supernote's folders,
  then each folder down; the one shown bold, the rest regular and tappable), then paper rows 76 dp tall, as many as
  the screen holds, each a 60 dp thumbnail (a picture, a PDF's first page, Phosphor `folder` for a folder) before the
  name and a file's day; folders first, A to Z, then the files newest first; ‹ n / m › under them (a hold: ten
  pages). It opens in the last folder a file was picked from (Document at first). A PDF picked shows its page (280 dp),
  ‹ n / m ›, and New Notebook, This Page, Cancel; This Page places that page as a picture.
- **Icons are Iconoir's** (the owner, 2026-10-29: "i like iconoir"; https://iconoir.com, MIT, v7.10.1): 24 on 24, a 1.5
  stroke, round caps and joins, black on paper, filled only where the meaning is a filled state (the full drop and eye,
  Zen's circle). Each file names its source in a comment: an Iconoir glyph, or "Drawn for My Bad Drawings in Iconoir's style"
  where Iconoir has none that fits or the owner asked for one ("design replacements for the following: finger, part hand,
  part life (dna), peel masking fluid, clear page, circle dashed (make it dashed), Half, Full (Show Kinds), Faint,
  Refresh (ghost), Lasso, Line Segment, Magnetic Poetry (...words on tiles), Rectangle, Shapes, Stamp, Shape oval";
  "oh also brush", "and clean brush"). Show Kinds' steps read as one row: Full a solid pupil, Half the lid half closed over it (the owner: "eyelid half closed"),
  Faint the eye dashed, Hidden closed with five short lashes; the wet drop dashed (dry), half full (moving), full (wet). The hair dryer has
  no air of its own (`BlowDryer` draws it right of its nozzle). The owner's hand-drawn set (2026-10-26) went before
  this, Phosphor Bold before that; a Phosphor glyph named elsewhere in this doc is now its Iconoir counterpart, and the
  few icons outside that set (the tools' own, the sand timer, layers' stack) keep their look. Until 2026-10-29:
  Phosphor's Bold weight (24 on 256). The bar's eraser (a block eraser), Finger's menu row (a hand
  smearing, Phosphor's hand-pointing turned) and the smudge tool's finger are drawn after Photoshop's Eraser and
  Smudge tools in that weight (`ic_eraser_block`, `ic_ph_hand_smudge`); the smudge tool's
  blending stump (`ic_blending_stump`) is the owner's own drawing, its source unknown, given a thin outline to hold on
  e-ink. Fill's paint bucket was retired 2026-10-23 (the Fill in brush and Page Colour do its work); its cell beside the
  smudge tool is empty. The smudge tool's icon follows its brush: the stump for one that blurs (MonoPaint's blending stump, Smear at
  Soften .5 and up; the drop until the column pass, 2026-10-23, now the wet indicator's alone), the finger for one that pushes paint (Smear under that, Drag), a brush beside a water drop for
  clear water (`ic_water_brush`), a brush with a dab of paint for one that keeps its paint (`ic_dirty_brush`; its head
  filled with the grey of the paint it holds while it holds some, that paint's darkness snapped to the six greys, never
  paper, `DrawLayout.heldGrey`, `ic_dirty_brush_paint` under it, the mid grey #888 until 2026-10-23), the stump for the rest.
- **A brush that runs out shows its paint in a gauge** (the oil paint spec, Smudge's Paint Load; not on the drawing
  screen's tray since the refocus, 2026-10-24, which put oils that run out in Brush Lab's back room): a tray row whose brush
  runs out of paint has a small upright gauge at its stroke's right end, 6 dp wide and the stroke's height, a 1 dp ink
  outline with 2 dp of paper at its left, inked from the bottom by the share of a full load its next stroke starts with
  (`SlotTile.gauge`). It fills when a grey is rubbed, drains as strokes spend it, and never drops below the Starting
  Load. On the selected row's ink fill it keeps its paper behind it.
- **A dirty brush shows the paint it holds** (2026-10-23; the slots' dab left the column at the refocus, 2026-10-24: Clean Brush is behind a dirty slot's long press alone, and
  the smudge tool's head still shows its paint, its chooser Clean Brush; its drawn row shows no dab since 2026-10-26, the owner: "looks like a square at the end, shouldn't be there", its words say "holds paint"): a tray row whose brush keeps paint (Keeps Paint) and holds
  some has a small square dab at its stroke's right end (left of a gauge), 8 dp, a 1 dp ink edge, filled with that
  paint's grey (`SlotTile.dab`, `DrawLayout.heldGrey`). Its long press then opens a light column beside it, **Clean
  Brush** and **Tools** (the tool picker, as the long press opens otherwise), so a stray long press still gets there. Clean
  Brush empties the pen's paint and the finger's, and the dab goes. The smudge tool keeps Clean Brush in its chooser.
- **A faint tray stroke is shown a little darker:** samples are the page's dots, so a stroke is faint when it is thin
  and sparse (a light pencil in the lightest grey): its densest patch under 45 % and all its ink under 2.5 % of the
  row. Such a stroke is drawn again darker until its densest patch is about 35 %, the same brush, size and texture
  (`WildSample.legibleTray`). A pale wash covers enough of its row to read and is shown as drawn: darkened, a Lighter
  wash would look Dark. The brush and the grey in use are untouched, and the grey strip shows the real grey.
- **Nothing scrolls, text never under 12 sp, nothing clipped:** text wraps instead (Brush Lab and the Tools page
  prove it on the desktop, `LabLayoutChecks`, `ToolsPageChecks`, `DrawLayoutChecks`).

## Components

The design pass's component rules (the owner's decisions, 2026-10-23). The full table is in
`docs/archive/design-audit-2026-10-23.md`, "Component rules". Each is drawn by one shared piece, named here.

- **Groups are a grey step, not a frame.** A grey-200 band with paper blocks on it, never more than two steps deep. A
  sheet, a field on paper, a mode switch and a note are `Theme.box` (grey-200, no line). A card grid is `LabUi.grid`: a
  grey-200 band, 2 dp of it between the cards.
- **One section header.** LABEL in grey-800, no rule and no ink bar (`LabUi.header`, `LabUi.bar` with `barText`).
- **Buttons.** One primary per screen or block, ink-filled with on-ink words (`LabButton` made `solid`: Save, a
  dialog's act). One exception: Brush Lab's Randomize is ink-filled too, besides Save (the owner
, 2026-10-23: "make RANDOMIZE a
  black button, align dice with text"); Anything Goes beside it is a smaller plain-words button (the owner, 2026-10-24). Every other button is plain ink words or an icon, no frame and no fill, and grey-400 while disabled.
  Chips are plain words too, ink-filled while chosen.
- **Tiles and cards** (`LoopCard`): the drawing on paper, the name and words under it on grey-200, no frame. The one in
  use has the 5 dp ink tab at its foot. On the Tools page and in Brush Lab the words are on paper
  under a 1 dp ink line instead (see below). A part card in Brush Lab (`PartCard`) is a paper block in a 1 dp ink edge,
  no pattern strip, the picked one tabbed the same way.
- **One mark per chosen thing.** The toolbar slot in use has a 5 dp ink underline across it, clear of the next row,
  wherever the ten slots show: the side bar's tray, the Tools page (`SlotTile`, `ToolCard`; the
  owner: "some brushes are inverted... underline?"; a black fill before that, "too subtle" before the fill), except the
  drawing screen's side column, where it is the side bar (see above). Other tiles and part cards have the tab, and so do a notebook
  settings screen's choices (`CardArt.choice`: grey tiles, no frame, no fill, no ✓). Tabs and chips are ink-filled. The
  side bar's slot, tool, mode and layer in use have the side bar, the size its slider's thumb. A menu's rows have the bar at their right.
- **Menus and popups over the page** (the owner's option A: "i am worried they will leave e-ink ghosts when they open
  and close. this happens with the eraser menu"): one look for all of them, `Theme.menu` and `Theme.menuRow`. A paper
  panel with a 1 dp ink edge and no grey anywhere in it but the grey-400 lines between a menu's groups (Menu's and
  Play's, the refocus, 2026-10-24). Rows of text in `TypeStyle.MENU` (12 sp, one size for every
  menu, the owner: "12px font for menus is better"). The chosen row is bold with a 4 dp ink bar at its right. This covers Menu and its lists (Finger, Mirrors, Template Visibility's hold, a notebook's hold), Play's, the eraser's kinds, a dirty slot's Clean Brush, the smudge chooser, the stamp and layer menus,
  page options, Sizes (the Tools page's), Brush Lab's part choices and dial menus. A card in a menu keeps its words on paper.
- **A closed menu leaves no ghost.** When a menu or popup closes, its rectangle alone is blanked and shown again in a
  forced grey update, once Android has drawn what it covered (`DrawingPad.uncovered` and `cleanArea` on the drawing
  screen, `SelectionFeedback.cleanAfterClose` elsewhere). The page's full refresh at the next pause still follows a
  menu, as before.
- **Dialogs** (`Theme.dialog`): a grey-200 box with no outline. Cancel is plain words, the act ink-filled. Fields and
  rows in a dialog are paper on it.
- **Dividers** only where no grey step can do it: grey-400, never ink. The side column has none, its groups
  set apart by space. The eraser's small badge has a grey-400 edge; the menus' groups have a grey-400 line between them.
- **A notebook's settings are a plain list** (the owner, 2026-10-23: "replace it with the list"; `LibraryCard`, built by
  `NotebookScreens.fillSettings`), where the library card was, the strip's Settings tile: the bar's ‹ Notebooks (no Lock: cut at the refocus, 2026-10-24); the name in TITLE (HEADING or BODY where the room is short) with Rename under it; one grey-200 row a
  setting, at most 720 dp wide, its key in LABEL grey-800 at the left, its value at the right (BODY 700, one line, as
  wide as it needs, cut with … only past the row's room: two autosized lines broke words, "Of / f", until 2026-10-27),
  a grey-800 caret where it opens a screen. Three groups, a grey-400 line between them (no **More** since 2026-10-27,
  the owner: "instead of a more button, just include everything on that page"). The page: Template (a thumbnail of its
  page in a preview's grey-400 frame; "Lined · Faint" when its visibility isn't Full), Random Template (Off or what
  Random draws from, its screen Random From; the Template screen offers Random only to a Random notebook), Page Colour
  (six swatches, the six greys, a 2 dp grey-400 edge on the two lightest, the ink tab under the one chosen; an older
  notebook's slider grey keeps its exact grey and no swatch is chosen), See-Through Pages (Off and On chips), Nib Preview
  (Off and On chips: the hover ring's dots, on by default and in every older notebook, 2026-10-29), Self-Portrait Prompt
  (Off and On chips: a card dealt each new page, off by default and in every older notebook, 2026-10-30). The
  rules: No Erasing (Off and On chips), Timed Pages, Lock Pages (Off and On chips, greyed while no page is timed: a page
  run out, or a Diary Page's section a day after it ended, locked; off by default and in every older notebook), Tools (its brushes as small tray strokes; Brushes on Bar on its
  screen). The cover: Cover, Cover Picture (Use on Cover for the page drawn on, No Picture). After one more grey-400
  line, the notebook's own actions (the owner, 2026-10-28; the drawing screen's menu's rows until then), plain-words
  buttons a row's height: **Export Now**, then **Delete Notebook** last, each as it was there (Delete asks first, a blank
  notebook goes at once); Rename stays the name's button (`LibraryCard.settingsLayout` counts the row and the line).
  Rows are 56 dp, down to 40 where the room is short; all eleven fit both tablets in both turns, and the list scrolls
  only where they couldn't. The settings screens measure words in Ukulele's own width (`LabText.UKULELE`). The foot is one CAPTION line in grey-800. A row's
  screen (`CardSheet`): ‹ Settings, its title in TITLE and a CAPTION line under it, no rule; its choices grey tiles
  with the ink tab; a name in two lines, a long file name broken rather than cut. The Template screen also has
  **Template Visibility**, a row of four chips (Full, Half, Faint, Hidden, the one chosen ink-filled) over the grid, hidden while Blank.
- **A page's settings are the same list for one page** (the owner, 2026-10-23: "long press on a page will bring up a
  panel to adjust the settings for that page (similar to notebook settings, with constraints, etc.)". `LibraryCard.pageRows`,
  built by `NotebookScreens.fillPageSettings` from the list's own row pieces.) A hold on a page's preview on the page bar
  (and its screen-reader action), in a notebook's strip or in the Pages grid opens it, a whole screen over the notebook
  screen. **‹ Back** goes to drawing, or to the notebook's pages. "Page 3" is in TITLE with the notebook's name under it
  in BODY grey-800. Then a row a setting, as the notebook list's, but **paper rows with 1 dp ink lines between them** (the
  owner: e-ink greys render poorly, so new screens prefer paper and 1 dp ink lines to grey fills). Template has its
  thumbnail, its own or "Notebook's (Lined)", and ›. The Template screen names the notebook's own template so. Page
  Colour has a Notebook's chip and the six swatches, the tab under the page's grey unless it is the notebook's. It is set
  as Fill page sets it, one Undo step on its bottom page, turned to. Show Through has Solid, Half, Faint and Hidden as
  chips, and the Ghost icon chip under Page Before on a page not drawn on. It shows only while See-Through Pages is on and
  the page has no layers. No Erasing and Timed Pages have three chips, "Notebook's (Off)", Off and On.
  **Layers** (2026-10-24): a chip a layer, "1" the bottom, the one drawn on chosen (a tap draws on it, the drawing
  screen back), **Add** (a new layer on top, drawn on), **Merge Old** on a page with the old in-page layers. **Move**:
  Earlier and Later (the page bar's hold no longer lifts a page to drag). Timed Pages On has a row
  under it (the owner, 2026-10-23: "it only has 5 minutes displayed. ideally it should be a slider"): Brush Lab's slider
  (`BandSlider`, a 2 dp ink track and a square ink thumb, no band) over the notebook Timed Pages screen's stops, 30 s to
  60 min, the time in words at its right in BODY bold. It is set as the slider is let go. Until it is moved, the page's
  time is the notebook's, else 5 min. Chips are ink-filled when chosen. At the foot one plain-words button, Delete,
  greyed on a notebook's last page, asking first as Delete Page does (the owner, 2026-10-23: "you can remove some of the
  other stuff on the bottom.. duplicate move up etc."). The drawing screen's menu has no Delete Page, so it stays here.
  Before it **Export Page** (the owner, 2026-10-28: "each page should have a way to save page in various ways.. pdf
  png, and possibly layers for photoshop? ie. the diff media layers"), as Notebook Settings' foot has Export Now: a tap
  opens the usual menu beside it, **PDF**, **PNG**, **Layers** (Phosphor `file-pdf`, `image`, `stack`), and the copy is
  made on Export Now's worker, wet paint set first, into EXPORT/<app folder> beside the notebooks' PNG folders, named
  "<notebook> p5.pdf" / ".png" / ".psd"; the note says "p5.psd in EXPORT/My Bad Drawings" (`PageExport`, `AutoExport.page`).
  PDF and PNG are that page's own from the notebook's copies, byte for byte the PNG. **Layers** is a Photoshop file
  (`PsdWriter`): Grayscale, 8 bits, 300 dpi, PackBits; bottom first **Paper**, **Template** (the guides where they show),
  then each layer of the page kind by kind as the engine stacks them (**Old Paint**, the base of old pages and paint of
  no one kind, **Dry Media**, **Washes**, **Pen**, **Paint** for opaque paint; "Layer 2 Pen" where a page has several),
  **Magnets** on top; empty ones left out; every layer Normal, as the page composites (no wash multiplies). Each layer
  in the PNG's smooth greys at the coverage that shows on the paper as the PNG does, so the layers come within a few
  greys of it where marks overlap (11 at most on the checks' page, 0.04 on average); the file's merged image is the
  PNG's own. Left out: the masking fluid, Show Kinds' steps and a Hidden layer, as the copies leave them out. No
  settings, no file picker.
  The rest: the screen reader's Move Page Up and Down, Use on Cover inside the notebook (and Settings' Cover Picture), Replay
  Page in the menu (on a page it can replay), Clear page in the eraser's hold. A page's own rules and
  time are kept with the page (`NotebookRules.forPage`, `BookOptions`' `PRUL` and `PTIM`), and the eraser, Undo, Clear
  and the clock ask for the page drawn on's.
- **Kept as they are:** the grey bar (`ShadeStrip`), the page strip's previews and their frames, the paint gauge, magnets.

## The Tools page and Brush Lab

The design pass (2026-10-23, `docs/archive/design-pass-2026-10-23.md`): both screens have the same top, so they read as one
place: picking a brush on the Tools page, changing it in Brush Lab.

- **Thin ink lines, not grey fills** (the owner, 2026-10-23: "change to very thin line separators in tools view from
  grey. eink grey doesn't quite work the same way. also in brush lab"). On these two screens grey fills are replaced by
  1 dp ink lines, the menus' edge weight (`Theme.FINE`), because e-ink greys render poorly. The bands are paper in a
  1 dp ink frame (`Theme.menu`). A 1 dp line runs across a band between its groups
  (`LabUi.line`). Card grids (`LabUi.grid`: the Open sheet's library, a part's choices) show 1 dp
  ink lines between and round their cards. A card's words are on paper under a 1 dp line that parts them from its
  drawing (`LoopCard.ruled`). Part cards, the scratch pad, the rename field, the Open sheet
  and its fields and the kit sheet have a 1 dp ink edge. The open card's dial rows are ruled in 1 dp ink. Ink fills for what's
  chosen stay (tabs, chips, the primary button). This overrides "shade before line" on these two screens only.
  The Tools page left the bands and lines for the owner's mockup (2026-10-28, below): Brush Lab keeps them.

- **The Tools page** (`ToolsActivity`, `ToolsLayout`, `ToolsPage`, `ToolCard`; the owner's mockup, 2026-10-28, "Tools page
  v2"): all in Ukulele, on paper with no bands, frames or grid lines (the thin-lines rule above is Brush Lab's now). The
  bar: ‹ Back, "Tools" (bold), Undo and **Brush Lab** in a rounded 2 dp box (Tweak in Brush Lab, ink-filled, until then).
  The **scratch pad**, a rounded 2 dp box, at least 220 dp tall on the Manta (120 on the Nomad), the rest of the page
  after the Library's rows: the tool's name (bold) and its one line (" · even and crisp") in its foot; along its top a
  change's note ("Kept Fineliner in Recent") and small grey words at the right, **Back to** the slot's first brush,
  **Load Kit** or **Cut Stamp** while they apply, **Clear** and the drop. This Brush went (its name and line moved into
  the pad; the sizes and the grey bar are Brush Lab's and the drawing screen's). The small grey label **Toolbar** over
  the ten slots, two rows of five: each its drawing as the side bar shows it over its name on one line, the selected
  one bold and underlined (5 dp), a slot changed on this visit a dot after its name. The **Library**: the tabs as words
  (Tools, Recent, Saved, Kits; the chosen one bold over a 3 dp line), the pages ("‹ 1 / 2 ›") at their right; under
  them the **chips** (the owner: "instead of dropdown show chips for the categories"; the Style menu, 2026-10-24 to
  2026-10-28): All, Pens (Fineliner, Dip pen), Markers, Brushes (Brush pen, Dry brush, Hake), Washes (Watercolour, Ink
  wash), Paint (Gouache, Oils), Dry Media (Pencil, Charcoal, Pastels), Print and Odd Ones (Anything goes), rounded, the
  chosen one filled, only those the tab has (none under Kits), a row on the Manta upright, wrapping where the width runs
  out (`ToolsPage.Chip`; kept as the style was, a kept style's name opening on its chip). The grid: four across (three
  on the Nomad turned), each card its drawing over its name, both at its left; the card holding the slot's tool
  underlined. Every drawing is cut at its handle end (`DrawLayout.TIP_TRIM`) and scaled to one width in an art box of
  one height (`ToolsLayout.art`: the owner, "the tool images need to be limited to a certain length only to be uniform
  with one another"); a brush with no drawing (Brush Lab's) shows its stroke at that size. Under All the Tools tab goes
  chip by chip, each group from a new row, 8 dp further apart; Recent and Saved keep their order. A long press on a
  saved card asks in the chips' place to delete it. In landscape the scratch pad and the slots are a column at the
  left, the Library beside it.
  **Search** (the owner, 2026-10-28: "it's difficult to find things in the tools library"): a magnifier at the tabs'
  right, left of the pages, puts a one-line field and × in the chips' place, the keyboard up (`adjustPan`: the page
  keeps its size, no new layout). The grid shows Tools, Recent and Saved together, each brush once, no tab chosen, no
  groups, from the first page; every typed word must be somewhere in the card's name, its style's name or label, its
  chip or its line, any case, any order (`ToolsPage.matches`), else "Nothing found". The grid follows once per pause
  in the typing (250 ms), redrawn only when its cards change. × or a tab closes and clears it; it isn't kept. The
  Nomad's tabs are 10 dp apart (14 before) so the magnifier fits its row turned.
  The slots not selected are drawn in solid grey-600, picture and name (the owner, 2026-10-28: "make the non-selected
  tools a little grey"); the selected one stays black, bold and underlined. At the Toolbar label's right the chip
  **Tools First** (`DrawLayout.TOOLS_FIRST`, `PaintPreferences.toolsFirst`, app-wide, off at first), filled while on:
  then a slot's long press opens this page on the slot, not the picker (whose foot row turns it on).
  Opening fast (the owner: slow "just on the first load"): the page is laid out before its first frame (a pre-draw
  listener, no blank frame first); every art box's picture, drawing or stroke, is drawn off the main thread as an
  ALPHA_8 mask and kept for the app's life (`ToolCard`, an `LruCache` of 4 MB, by drawing or brush and size), the grey
  slots the same mask in grey; the drawing screen draws the page's first pictures ahead 3 s after it settles, never
  while the pen is down (`ToolsActivity.warm`). Logcat (tag MonoPaint): "Tools page shown in N ms".
- **Brush Lab** (`BrushLabActivity`, `LabLayout`, `LabSettings`): a double tap on any slider (within half a second, `BandSlider`) resets it to its choice's default, one Undo step, noted "Reset Size"; the bar (‹ Tools when the Tools page opened it, and it
  goes back there; "Brush Lab"; Undo at the right). The lab works on the slot that was selected when it opened, from
  the drawing screen or the Tools page, and shows no other (the owner, 2026-10-29: "the active toolbar isn't important
  here, since whatever toolbar slot was selected at the beginning should flow through. that will give the brush lab
  some room"; the **Active in Toolbar** band of ten slots went, its room to the modules, the open card and the scratch
  pad). Then one band: **About This Brush** (its header: the brush's actions in one ordered list,
  `BrushLabActivity.ACTIONS`: **New**, **Open**, **Save**, the one ink button, **Save as New**, Back to, Cut Stamp, from
  the row's left, no label on either screen (the owner, 2026-10-29: "you can remove the text ABOUT THIS BRUSH and move
  the menu buttons to the left there"; the Manta's said About This Brush until then). Its block: the loop, the name with its
  pencil, under it **Edited** once the brush changed since it was opened or saved and a suggested name to tap, the
  words, and at the right the drawing screen's **picker box** (`PickerBox`, the same component as the side column's)
  turned a quarter so it lies across where the four sizes and the grey bar were (the owner, 2026-10-29: "we can use the
  same horizontal picker from the toolbar in place of the pickers there"): the greys black at the left to white at the
  right with their tab above, the sizes fine at the left to broad at the right with their tab under, 28 dp squares (24
  on the Nomad), 344 dp long (194; 264 until its readout), and at its right the side column's **readout**
  (`SwatchReadout`, the same component, `SwatchReadout.show(look, brush, greys)` for both), a square as thick as the
  box: the nib at rest at the size in use, in the grey (the owner, 2026-10-29: "beside the picker show the preview size
  / shape as in the toolbar"; the side column's sits over its box, so turned with it, it sits at the right), set with
  the box's tabs when the size, the grey or the brush changes; no Sizes window here any more), **Adjust** (the six part modules with their locks
  and dice, their end words kept; Start from ▾ in its header) beside **Scratch Pad**, a 1 dp line over them, and
  **Randomize** across the foot, big, ink-filled, its dice beside its word and centred with it as one (the owner,
  2026-10-23: "make RANDOMIZE a black button, align dice with text"; the lab's second ink button), **Anything Goes**
  beside it a smaller plain-words button with its shuffle (the owner, 2026-10-24). One mode, what Advanced was (the
  owner, 2026-10-29: "let's just have it advanced, no need for a basic mode"): every choice rolls may pick, the back
  room's too (Hatch, Stipple, Filled Shape, Stamp, Spiro, Float, Mold, Fading, Lemon Juice), locks always apply, a
  tapped module opens its card. No Tweak It (the owner, 2026-10-29: "let's remove the tweak it section. we can keep
  that on the online version maybe but here it's too confusing"; the web lab keeps it).
  **New, Open, Save** (the owner's option A, 2026-10-29: "once it's edited, maybe suggest a new name?"): **New** rolls a
  new brush in the brush's style that no library entry holds. **Open** (on while open) unfolds a sheet under the
  header: a pasted code (Open, and **Cancel**, which closes it with nothing changed; Open again or Back closes it too,
  the owner: "once you click load, you can't shrink that form without entering something"), this brush's code to
  copy, and the **Library**, two rows of its brushes a page (‹ 1/3 ›; six across, four on the Nomad), the one Save
  writes over tabbed; a tap opens one. **Save** writes over the library entry the brush was opened from, in its place,
  its own size and grey moved to the new code, keeping its name unless one was given (typed or the suggestion taken);
  a brush not opened from the library (the slot's own, New's, a pasted code) is added. **Save as New** always adds a
  copy, under the suggested name unless one was given, the entry left alone. The slot always holds what is being
  edited; saving only concerns the library. The suggestion is `BrushNames.name` of the brush as it now is (deadpan,
  Title Case, its part's word in it), offered only once it's edited and its name isn't that already; the rename field
  opens on it. While a part's choices are open, a tap on another part's module
  switches them to that part's in one step (the owner, 2026-10-23: "if medium picker is open and i tap paper, open the
  paper panel"): the open panel closes, its patch is cleaned (`SelectionFeedback.cleanAfterClose`), and the new one
  opens once the clean has run, so no ghost stays. Its own module, or anywhere else, closes them.
- **Back to where you started** (the owner: "an easy way"): each screen remembers what every slot held when it opened
  (`ToolSlots.Start`; Brush Lab opened from the Tools page keeps the Tools page's). A slot holding something else has a
  small grey-800 dot under it on the Tools page (`LoopCard.setChanged`), and a plain-words **↺ Back to
  Fineliner** (Phosphor's arrow-counter-clockwise) puts the first brush back, its size and grey too; one Undo step.
- **Nothing unsaved is lost** when a slot's brush is replaced (a pick, a kit, Randomize, Start from, Load, Back to): a
  brush not in the library and not a Basic tool is kept first in **Recent** (`ToolSlots.keep`; the latest 30, each
  once), said for a moment ("Kept Fineliner in Recent"): beside This Brush's header on the Tools page, under the picker
  box in Brush Lab. A brush Randomize made stays in its slot on Back, saved or not.

## Typography

- **One family: Ukulele** (since 2026-10-26, the owner's: "font seems good.. a little more horizontal spacing"), the
  owner's own hand-drawn unicase face (Ukulele font by Elling Lien, CC BY 3.0), in two weights, **400 and 700**
  (`res/font/ukulele.ttf`, `ukulele_bold.ttf`, made from the dafont file by `scripts/ukulele_font.py`: 1.12× its size,
  0.07 em between letters, a drawn bold with the regular's advances). It is the app theme's font
  (`res/font/ukulele_family.xml`, `res/values/styles.xml`), so the system's and MonoPaint's menus, dialogs and
  messages draw in it too; My Bad Drawings' own text gets it from `Theme.face(weight)` and `Theme.style(view, TypeStyle)`.
  A weight up to 500 draws 400, from 600 up 700.
- **What it lacks:** Ukulele has only a–z, A–Z, 0–9 and a little punctuation; the script adds : ; , · and … from its
  own period and apostrophe, and anything else (% & * = ×, the arrows) comes from **iA Writer Duo** behind it
  (`res/font/ia_writer_duo*.ttf`, the static `iAWriterDuoS` files from github.com/iaolo/iA-Fonts, SIL OFL 1.1,
  unmodified), then the system's (▾, ✕).
- **Widths:** layouts are still counted in Duo's duospace (`LabText`, the lengths in docs/TEXT-STYLE.md). Ukulele runs
  a little wider on average and up to about a tenth wider on a name; a box sized to exactly its text (the tool
  picker's cells, `DrawLayout.PICKER_SPACING`) allows for it.
- **Hierarchy by size, weight and case, never by more weights:** menu 12 sp 400 (every menu's rows, the chosen one
  700); caption 13 sp 400 (descriptions, end words, card
  captions); body 15 sp 400; label 14 sp 700, caps, tracked .06 em (buttons, bars, module names); heading 20 sp 700,
  sentence case; title 32 sp 700. A dialog's title is 700 and its message 400. Grey-800 marks secondary text.
- **Measuring model** (`LabText`, the desktop's stand-in for the device's paint; from the TTFs' hmtx and hhea, 1000
  units an em, the same in both weights): a character is **0.6 em** wide; the wide set (`LabText.WIDE_SET`: m w M W
  % @ © Æ æ Œ œ — … and a few Greek and Cyrillic letters) **0.9 em**; a sign the face lacks **1 em** (a fallback
  face's); a zero-width space nothing. A line is **1.3 em** (ascent 1025 + descent 275; no font padding). The app
  draws text at 1.2× its sp (`Ui.largerText`), so 13 sp is 15.6 dp. Budgets that used "characters" now measure the
  words: a Tools page card's line (`ToolsLayout.lineRoom`), Brush Lab's value column (its widest value, "9.8 mm").

**The one exception: fridge magnets** (the owner's, 2026-10-19). A magnet is a thing on the page, not the app's
chrome, so it keeps Paintmageddon's look: its word in **Roboto Mono Bold** (`res/font/roboto_mono_bold.ttf`, SIL OFL
1.1, `Theme.magnetFace`, used by `MagnetTiles` alone), ink on a paper tile with 2 px corners, a fine grey-600 edge and
a solid 2 px ink offset shadow. Everything else, the Words bin's header and buttons too, stays iA Writer Duo.

The web pages (`docs/brush-lab/*.html`) keep IBM Plex Mono / Roboto Mono from Google Fonts: iA Writer Duo isn't on
Google Fonts, and the published pages may load stylesheets from Google Fonts only.

## Loop and wave

Two sample shapes tell a viewer what a thing is before they read anything (the owner's rule, 2026-10-16):

- **The loop is a whole brush.** Anything drawn as the sample loop (the turned scribble loop, its tail cut short after the
  crossing; `WildSample.LOOP`) is a brush you can pick or already have: the web lab's variations, the loop beside a brush's name in
  Brush Lab, sweep steps you can take, Brush Studio's Explore grid.
- **The exceptions: the drawing screen's bar and the Tools page** (the owner's "B Pen tray", 2026-10-19). The bar's ten
  slots and the smudge tool are short rows, each its brush as a pen lying in a tray: a short gentle stroke across the
  row, light to firm, at the width the tool draws up to what the row holds (`WildSample.TRAY`). It is still a whole
  brush; the rows are too short for a loop. The Tools page draws every preview so too (the owner: "lets use the toolbar
  stroke look for all of the previews"): its slots and This Brush at the one size, as the bar's rows, its
  library cards at the size that fits the card (`LabLibrary.traySampler`), all at full darkness whatever the grey in use. Brush Lab's Open
  sheet draws its library the same way; it keeps its loop for About This Brush.
- **The wave is one part or setting.** Anything drawn as a wave is a piece you adjust, not a brush you pick: Brush Lab's
  part cards and their choices, Brush Studio's part cards, and the dial demo's tiles.

A brush's name always sits beside its loop, so the name and what it draws are seen together.

## This brush

Wherever a brush is being changed, one block says which brush it is, and it is always in view (owner, 2026-10-16):

- **The block "This brush":** on the left a big live loop of the brush (at full darkness, whatever the grey in use: the loop is a whole brush);
  on the right its name (tap to rename) and its plain words. The brush's own actions sit with it: in Brush Lab, New,
  Open (a library brush or a share code), Save, Save as New, Back to and Cut Stamp in its header; Undo in the bar. The name and loop appear here,
  once and prominently, not again in a top bar.
- **Adjust** heads the parts (Tip, Medium, Paper, Drying, Control, Smudge, Erasable), each shown with waves.
- **Finding a brush is kept apart from changing this one.** Places that offer brushes (Community library, Random,
  the Tools page's tabs, the library) show loops you can try (the Tools page: tray strokes); trying one makes it "This brush".
- In Brush Lab (app): About This Brush across the band's top, then Adjust under it. In Brush Studio (web): a persistent sidebar holds
  the block and, under it, the ways to change or look at this brush: Adjust, Close-up (Fine-tune until 2026-10-30: it changes the test stroke, not the brush), Mix; the top tabs are the ways to find one:
  Community library, Random.
