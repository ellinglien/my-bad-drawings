# Modular Brush Ideas: how other modular systems stay understandable

For Scribulor's Brush Lab. The focus is the **UI**: what you see and touch, how a module is swapped, how its dials are revealed, how the result is previewed, and how beginners and experts share one screen. Engine internals come up only where they shape the screen.

**Short answer.** The systems people learn fastest share a **fixed path that already works** (semi-modular synth, camera, pen), **one module in focus at a time** with few controls, and a **live result beside the controls**. Free graphs (patch cables, node editors) are powerful but are where people get lost. **Recommendation: Concept A, the "Semi-modular pen".** Its five fixed parts read top to bottom, from hand to page. Tap one part to swap it, and open one part's dials only in Advanced.

---

## 1. Twenty-three systems, as the user meets them

The "Connects" column uses these terms:
- **chain:** a fixed or ordered line;
- **graph:** free cables or nodes;
- **slots:** fixed named places;
- **macro:** one control that drives many.

| System | Connects | What you touch; how a module is swapped | Why it's learnable | What goes wrong |
|---|---|---|---|---|
| **Eurorack / VCV Rack** | graph | Panels of jacks and knobs. Every control is visible. You add a module from a browser and drag cables between jacks. | You can hear every change at once. Players adopt a layout habit of control at top-left and sound out at bottom-right ([ModularGrid](https://modulargrid.net/e/forum/posts/view/6479)). | An empty rack makes no sound. Beginners are told to copy a known patch first ([VCV community](https://community.vcvrack.com/t/patches-for-beginners/547)). Cables pile up into spaghetti. |
| **Semi-modular synths** (Mother-32, 0-Coast) | chain + override | A fixed panel. The jacks are pre-wired ("normalled"), and a cable overrides one link. | "No patching required" ([Moog via XLR8R](https://xlr8r.com/?p=52249)). The 0-Coast "operate[s] with or without the use of patch cables" ([manual](https://www.polynominal.com/sample-packs/make-noise-0-COAST/0-coast_manual.pdf)). It makes sound the moment you play it. | Normals are invisible: plugging one jack can silently mute something else ([ModularGrid](https://modulargrid.net/e/forum/posts/index/3629)). |
| **littleBits** | chain | Magnetic bits snap in a line, colour-coded by role. | The magnets "enforce polarity, so you can't put things in the wrong way" ([we-make-money-not-art](https://we-make-money-not-art.com/?p=6239)). A circuit works the moment it snaps. | Expression is limited. The coding is colour only, so it needs translating for grey. |
| **OP-1 / Pocket Operators** | slots | Four colour-coded knobs, matched to the same colours on screen ([TE guide](https://teenage.engineering/guides/op-1/original/layout)). Pocket Operators have only two knobs. | "Always four controls" per page. Designer: when you turn a knob "you should hear the difference" ([Pamono](https://www.pamono.fr/stories/dream-machine)). | A knob's meaning changes with the page. |
| **Elektron parameter locks** | per-step override | Hold a step and turn a knob. The locked value shows inverted and the step flashes ([Elektronauts](https://www.elektronauts.com/t/trigless-locks-why-how-to-use-them/41953)). | A lock is one gesture, and it is local. | You can't see *which* settings are locked without paging through them ([Elektronauts](https://www.elektronauts.com/t/see-what-trigs-are-parameter-locked/231976)). |
| **Ableton Racks** | macro | Eight macro knobs on the front. Each maps to many settings, each with its own min/max ([Sound On Sound](https://www.soundonsound.com/techniques/using-racks-ableton-live)). | One knob makes one musical move. Shared racks open to their macros. | The macro hides what changes underneath. |
| **Guitar pedalboard** | chain | A row of boxes, each with two to four knobs and a bypass footswitch. | Each pedal does one thing. Bypass gives an instant A/B. There is a well-known conventional order ([Guitar Player](https://www.guitarplayer.com/gear/guide-to-guitar-pedal-order)). | The order changes the sound ([MusicRadar](https://www.musicradar.com/how-to/guitar-pedal-order-how-to-organise-your-pedalboard-signal-chain)), and beginners agonise over it. |
| **Line 6 Helix** | chain on screen | Select a block in the chain, turn to change its model, press for the model list. Its knobs edit that block ([cheat sheet](https://im.static-thomann.de/pics/atg/atgdata/document/misc/helix_cheat_sheet_english_rev_a.pdf)). | Select-then-swap: one block in focus. | Users call the flow view "engineer-brain-centric" ([The Gear Forum](https://thegearforum.com/threads/line-6-helix-stadium.9058/page-314)). |
| **MicroFreak mod matrix** | grid | A 5×7 grid of sources (envelope, LFO, press…) against targets. Pick a cell and set an amount ([Sound On Sound](https://www.soundonsound.com/reviews/arturia-microfreak?page=2)). | There are no cables. The whole routing is one small table. | Its three assignable columns hide their targets. |
| **Node editors** (Blender, Nuke, TouchDesigner) | graph | Boxes and wires on an infinite canvas. A **group** folds nodes into one box and exposes chosen inputs ([Blender manual](https://docs.blender.org/manual/en/4.1/compositing/types/groups.html)). | Groups hide complexity. Expert-made groups give beginners a few knobs. | Blank-canvas paralysis and spaghetti. Only experts build from scratch. |
| **Photoshop Layer Style** | fixed list | A checklist of effects on the left. Tick to enable, click the name to see its settings on the right. Since CS6 the list order **is** the order they are applied ([Tuts+](https://design.tutsplus.com/articles/improvements-to-layer-style-feature-in-photoshop-cs6--psd-16973)). | Fixed order and plain names. Every effect is visible but off by default. | You can't reorder, and users asked for it for years ([Adobe community](https://community.adobe.com/t5/photoshop-ecosystem-ideas/p-multiple-and-reorder-sort-effects-styles/idc-p/12268422)). |
| **Procreate Brush Studio** | slots | An attribute list (Stroke path, Taper, Shape, Grain, Wet mix…), that attribute's settings, and a **Drawing Pad** that redraws your test strokes "in real time" ([handbook](https://help.procreate.com/procreate/handbook/brushes/brush-studio)). | Sections follow the anatomy of a stroke. The preview never leaves the screen. | There are 12–14 sections of sliders, so it is power-user territory. |
| **Krita / MyPaint** | slots + curves | Pick an engine from a list, which starts a fresh preset ([Krita docs](https://docs.krita.org/Loading_and_Saving_Brushes)). Each option has sensors (pressure, tilt, speed…) with curves ([Krita sensors](https://docs.krita.org/ca/_sources/reference_manual/brushes/brush_settings/tablet_sensors.rst.txt)). There is a scratchpad. | Deep and exact. | Swapping the engine throws away your settings. Several sensors on one option give "weird results" ([KDE bug 497367](https://bugs.kde.org/show_bug.cgi?id=497367)). Curves are opaque. |
| **iOS Shortcuts** | vertical chain | Action cards stacked top to bottom. Each output flows to the next automatically ([Apple](https://support.apple.com/guide/shortcuts/use-variables-apdd02c2780c/7.0/ios/17.0)). | Reading order **is** the flow, so no wires are needed. | Long shortcuts scroll out of sight. |
| **Scratch** | shape-keyed blocks | Blocks whose shapes only fit where they are valid ([K-State](https://textbooks.cs.ksu.edu/intro-scratch/02-blocks/02-block-shapes/)). | **Shape** prevents most syntax errors, with no colour needed ([arXiv](https://arxiv.org/pdf/2102.07440)). | Semantic mistakes remain. |
| **Noita wands** | slot chain | A wand has N slots, and spells cast left to right ([Noita wiki](https://noita.wiki.gg/wiki/Wand)). | It is visible and ordered, and you edit in a safe room. | Emergent combinations are opaque, so players need the wiki. |
| **Zelda TotK Fuse/Ultrahand** | free attach | Grab and stick. The parts look like real objects, so you can guess what they do ([Zelda Dungeon / GMTK](https://www.zeldadungeon.net/informative-new-video-analyzes-how-nintendo-designed-ultrahand-for-tears-of-the-kingdom/)). | Staff tuned the combinations "so results wouldn't disappoint" ([Nintendo, Ask the Developer](https://www.nintendo.com/sg/interview/totk/04.html)). A visible "glue" shows what is attached. | Free 3D building is fiddly with a controller. |
| **Spore Creature Creator** | snap to body | Seven part categories. Parts snap only to valid spots, and more columns unlock as you go ([SporeWiki](https://spore.fandom.com/wiki/Creature_Creator)). | Reviewers placed it "right down the middle" of simple and advanced ([Giant Bomb](https://giantbomb.com/user-reviews/id/10515/as-much-fun-as-your-creativity)). | The large catalogue needs sorting ([Newsweek](https://www.newsweek.com/evolution-slow-process-94209)). |
| **Minecraft redstone** | free graph | Dust wires in the world. | Physical and visible. | Hidden rules ("quasi-connectivity") break machines that "feel like they ought to work" ([Minecraft feedback](https://feedback.minecraft.net/hc/en-us/community/posts/360012390191/comments/360009590132)). |
| **Cameras** | slots | Body, lens, film, filter. The mount decides what fits ([PictureCorrect](https://www.picturecorrect.com/understanding-lens-mount-compatibility/)). Fujifilm "recipes" are a handful of named settings shared as text ([Fuji X Weekly](https://fujixweekly.com/2022/03/31/more-fujifilm-film-simulation-recipes/)). | Each slot is a physical object with a name. A recipe is shareable. | Mount incompatibility. |
| **Fountain pen** | slots | Nib, ink, paper. A wet nib on absorbent paper feathers ([Pure Pens](https://www.purepens.co.uk/blogs/news/fountain-pen-paper-guide)). | Everyone already knows the model, and the interactions are physical. | — |
| **Cooking / coffee** | elements | Salt, fat, acid, heat as a "compass" rather than a recipe ([NPR](https://www.npr.org/sections/thesalt/2017/05/30/529699099/-salt-fat-acid-heat-a-guide-to-the-elements-of-cooking)). The coffee compass steers by outcome: sour, weak, bitter, strong ([Bean Scene](https://www.beanscenemag.com.au/pete-licata-and-veneziano-release-coffee-brewing-compasses)). | A few named elements, and dials named by what you **perceive**. | — |
| **LEGO / Unix pipes** | one connector | Studs fit everything since 1958 ([LEGO](https://www.lego.com/pt-pt/aboutus/news/2025/october/houses-built-to-last-70-years-of-the-lego-system-in-play)). Pipes pass text between small programs that "do one thing well" ([Wikipedia](https://en.wikipedia.org/wiki/Unix_philosophy)). | One universal interface. | — |

**Synplant**, rejected before, fits the evidence: its radial garden ([MusicRadar](https://www.musicradar.com/reviews/sonic-charge-synplant-2-review)) has no physical counterpart, and Synplant 2 added a conventional "DNA editor" beside it ([CDM](https://cdm.link/synplant-2-hands-on/)).

---

## 2. Twelve patterns, mapped to the Brush Lab

1. **Normalled defaults ("always a mark").** *Semi-modular, littleBits, cameras.* Every module always holds something: Life = None and Carry = Off are real choices, not empty sockets. Any combination draws, so there is never a broken or blank state.
2. **A fixed order that reads as the flow.** *Pedalboard, Photoshop styles, Shortcuts, Noita.* The modules sit in one order: Hand → Tip → Medium → Carry → Paper → Life. That is the order things happen when you draw. There is no reordering, because the physics has one order.
3. **No free graph.** *Lesson from the failures of Eurorack, node editors and redstone.* No cables, no canvas and no hidden normals. What is shown is what is connected.
4. **Select, then swap.** *Helix, Procreate, Photoshop.* Tap a module, and its choices replace the strip under the test area. One module is in focus; the rest stay put.
5. **Choices drawn with *your* brush.** *Procreate Drawing Pad, the Lab's variations.* Each choice card is the current brush with only that module changed. This is WYSIWYG and static, so it works on e-ink.
6. **Few controls per module, with one macro on top.** *OP-1 (four), Pocket Operator (two), Ableton macros.* In Simple, each module has one dial named by its result, as the coffee compass does: Tip *thin↔broad*, Medium *dry↔wet* (the existing Wet dial), Paper *smooth↔rough*, Life *still↔lively*, Hand *steady↔responsive*.
7. **Role coding without colour.** *littleBits colours become Scratch shapes.* Each role gets a fixed position, a fill pattern on its tab (solid, hatched, grey, lined, dotted, open) and a glyph. Position does most of the work, and the pattern links a module's card, its choices and its dial page.
8. **Constraints instead of errors.** *littleBits magnets, Scratch shapes, Spore snapping, camera mounts.* A choice that would do nothing is greyed, not hidden, as v2 already does with nib width on a round nib. Better still, each choice adapts so it always does *something*, as TotK tuned its combinations.
9. **Locks at module level, clearly shown.** *Elektron's weak spot.* A padlock on a module card keeps that whole part through Randomize, for example "keep my paper, roll the rest". Per-dial locks stay inside the module, and a locked module card shows inverted.
10. **Presets as recipes.** *Fuji recipes, Salt Fat Acid Heat, Racks.* A brush is the five choices in words, such as "Split nib · Wash, wet · Cold press · Drying · Press→wider". The plain description and share codes already fit this.
11. **Input as a small matrix, not curves.** *MicroFreak, against Krita's stacked sensors.* Pressure, tilt and speed form the rows; size, darkness and width/angle form the columns. Each cell is off, some or full. One source per target avoids Krita's "weird results".
12. **Bypass to learn.** *Pedal footswitch, Photoshop checkbox.* Holding a module card draws the test loop with that module at its neutral choice, so you see what it adds. This is a static before/after, with no animation.

---

## 3. Three UI concepts

All three keep the test area, the six variations, the slots and library, Undo/Save, and v2's rule that a picked item gets a frame and tab, never an inverted fill.

Sizes are portrait dp: Manta 1024×1365 and Nomad 749×998. Wireframes for every state are in the HTML page.

### A. Semi-modular pen (a fixed column of parts)

Analogies: a semi-modular synth (a pre-wired path that works untouched), a fountain pen or camera (named physical parts), Helix (select, then swap), and Photoshop Layer Style (a fixed list with settings beside it). littleBits/Scratch contribute the keyed joints between cards.

```
NOMAD 749 dp                         ┌─[←] BRISK QUINCE  [SIMPLE|adv] [↶][SAVE]┐
┌─ strip 260 ─┐┌──── test area 489 ──┤
│▓ HAND       ││                     │
│  press→wider││   draw here         │
│▨ TIP     ~~ ││                     │
│  Split nib  ││                     │
│░ MEDIUM  ~~ │├─ VARIATIONS ────────┤
│  Wash · wet ││ [~][~][~]           │
│≡ CARRY  off ││ [~][~][~]           │
│∴ PAPER   ~~ │├─ SLOTS 1-6 ─────────┤
│  Cold press ││ [1][2][3][4][5][6]  │
│○ LIFE    ~~ │├─ LIBRARY ───────────┤
│  Drying     ││ [ ][ ][ ][ ]  ‹ ›   │
│ STYLE ▾     ││                     │
│[ RANDOMIZE ]││                     │
│ This brush: ││                     │
│ A wet wash… ││                     │
└─────────────┘└─────────────────────┘
```

- **Main screen.** Five module cards plus a slim Carry card form a column, in physical order. Each card shows the role (pattern tab and glyph), the current choice in words, and a small sample of that module's contribution.
- **Swapping a module.** Tap a card. The area under the test area (variations, slots and library) becomes "TIP: pick one". It is a 3×3 grid of the choices, each drawn with the current brush and only the tip changed. Tap one to apply it (Undo reverts), or tap ✕ to go back. The picked card gets the frame and tab.
- **Opening a module's dials.** In Advanced, the tapped card expands in place: the other cards shrink to 48 dp one-line rows, and the open card shows its choice chips and every dial, each with a lock and dice. Only one card is open at a time, so it never scrolls.
- **Simple vs Advanced.**
  - **Simple:** each card has its choice plus one macro slider. There are no locks. STYLE ▾, Randomize and the plain description sit under the column.
  - **Advanced:** the same column, with module locks on the cards and full dials when a card opens.
- **Randomize, locks, variations.** Randomize rolls every unlocked module; a module lock keeps a whole part. Variations nudge within each module and can swap at most one module's choice, so the six cards read as "your brush, but…".
- **Strengths.**
  - It is the most conventional: a list beside a canvas, like Photoshop or Procreate.
  - The physical order teaches the model without instruction.
  - Swaps preview in place.
  - It reuses the v2 "strip that swaps" mechanism.
- **Risks.**
  - On the Nomad a 260 dp column is tight for Advanced sliders, so it widens to 300 dp when a card is open (test area 449 dp).
  - Six cards at 105–130 dp fit 998 dp only with the description kept short.

### B. Pedalboard (a chain of boxes you can bypass)

Analogies: guitar pedalboard, Helix, and Ableton macros.

```
NOMAD 749 dp
┌─[←] BRISK QUINCE [SIMPLE|adv] [LIB] [↶][SAVE]─┐
│                 test area                   │
│                                             │
├─HAND─┬─TIP──┬─MEDIUM┬─CARRY┬─PAPER┬─LIFE───┤
│ ▓    │ ▨    │ ░     │ ≡    │ ∴    │ ○      │
│press │Split │Wash   │off   │Cold  │Drying  │
│ ━━   │ ━━   │ ━━    │ ━━   │ ━━   │ ━━     │
│      │      │       │ (●)  │ (●)  │ (●)    │ ← bypass
├──────┴──────┴───────┴──────┴──────┴────────┤
│ STYLE ▾          [ RANDOMIZE ]              │
│ [~][~][~][~][~][~]  variations              │
│ [1][2][3][4][5][6]  slots                   │
└─────────────────────────────────────────────┘
```

- **Main screen.** The test area is full width. Under it is a board of six boxes of 124 dp, with cables drawn between them. Each box has a name, its model, one macro bar and, on the optional effects (Carry, Paper texture, Life), a bypass footswitch.
- **Swapping a module.** Tap a box's name, and a model list in the Helix style covers the test area. It is one row per choice, with a sample and a line of description.
- **Opening a module's dials.** Tap the box body, and an edit panel covers the board and variations. It shows the box drawn large with all its dials, locks and dice.
- **Simple vs Advanced.**
  - **Simple:** boxes show one bar each.
  - **Advanced:** boxes show lock pads and two bars, and the edit panel opens.
  - The library moves behind LIB in the bar.
- **Randomize, locks and variations** work as in A. Bypass becomes a fast way to learn: a footswitch shows the brush with and without the effect.
- **Strengths.** Bypass is a very clear learning tool; the test area is wide; Elling knows pedalboards.
- **Risks.**
  - Six boxes fill 749 dp exactly, so there is no room for a seventh (a Knife or Finger module later).
  - A chain of boxes suggests you can reorder them, which you can't.
  - Tip and Medium can't really be bypassed, which breaks the metaphor.
  - Knobs are a weak control with a pen on e-ink.
  - The library becomes a second screen.

### C. Recipe card (the brush as a list of named ingredients)

Analogies: Fuji film recipes, Salt Fat Acid Heat, iOS Shortcuts, and fountain pen + ink + paper.

```
NOMAD 749 dp
┌─[←] BRISK QUINCE [SIMPLE|adv] [↶][SAVE]──────┐
│                 test area                   │
├─ RECIPE ──────────────── [SURPRISE ME] ─────┤
│ ▓ HAND   press → wider, tilt → angle    ▾  │
│ ▨ TIP    Split nib                       ▾  │
│ ░ MEDIUM Wash, quite wet                 ▾  │
│ ≡ CARRY  picks up a little               ▾  │
│ ∴ PAPER  Cold press, light tooth         ▾  │
│ ○ LIFE   Dries in 4 s, blooms            ▾  │
│ "A wet wash with a split nib on…"           │
├─────────────────────────────────────────────┤
│ [~][~][~][~][~][~]  other recipes           │
│ [1][2][3][4][5][6]  slots                   │
└─────────────────────────────────────────────┘
```

- **Main screen.** The test area sits on top and the recipe card below it. Each line reads as a sentence. The plain-language description closes the card.
- **Swapping a module.** Tap a line, and a full-width picker opens below it. Each row is a choice name, a one-line description and a sample drawn with your brush.
- **Opening a module's dials.** In Advanced, ▾ expands the line into its dials (an accordion, one open at a time), and the lines below fold to their names.
- **Simple vs Advanced.**
  - **Simple:** lines and pickers only.
  - **Advanced:** a pin on each line (a module lock) and the dials.
- **Randomize, locks, variations.**
  - "Surprise me" rerolls the unpinned lines.
  - Variations are "other recipes": six cards whose captions say which line changed.
- **Strengths.** Words first and very learnable; ideal for sharing ("post your recipe"); fits any width; the most conventional, since it is a settings form.
- **Risks.**
  - It is the least like a playground, and is close to today's Advanced list in sentences.
  - Samples are smaller.
  - The test area loses height to the card on the Nomad (about 360 dp in Simple, 260 dp in Advanced).

---

## 4. Recommendation: A, the Semi-modular pen, with two borrowings

**Why A.**
- **Conventional.** A list beside a canvas, like Photoshop's Layer Style, Procreate's Brush Studio and today's lab. Only the five words are new.
- **Physical.** A pen is already modular (hand, tip, ink, paper, time), and the column reads in that order, so it teaches itself.
- **Always a mark.** Every slot is normalled and there are no cables, so spaghetti, blank racks and hidden normals can't happen.
- **A playground, not weird.** Tap a part to see every alternative drawn with your brush; lock a part and roll the rest. That is "plug ideas together" without a patching surface, reusing the Randomize, locks and variations Elling liked.
- **It fits** the Nomad without scrolling, and v2's "strip that swaps" carries the picker.

**Borrow from B: bypass.** Hold a module card and the corner sample shows the brush with that module neutral. This gives the pedal's A/B lesson without the pedalboard's false promise of reordering.

**Borrow from C: words.**
- Each card states its choice in plain words.
- The library caption reads as the recipe.
- The "This brush" description is the five lines joined.

**Open questions for Elling.**
- Should Carry be its own slim card, as drawn, or a dial inside Medium?
- Should Input sit at the top as "Hand", or at the bottom as "How you hold it"?
- Should a variation be allowed to swap a module's *choice*, or only nudge its dials?
