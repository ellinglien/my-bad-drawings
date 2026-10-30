# Paint Media Research: charcoal, gouache, oils for Scribulor

This report covers what the three real media do, how other apps and research papers fake them, and the cheapest believable version for Scribulor: grayscale e-ink, Java on the CPU, about 3 screen updates a second.

## Summary

- **Carry (new, shared).** A small tone buffer that travels with the tool: it picks paint up and lays it back down.
  - Uses: the finger smudge on charcoal, the drag of wet oils, and gouache lifting a little of the paint beneath it.
  - The code already has two parts of it: `PaintLoad` (pickup) and `ToneDabs.soften` (drag).
- **Charcoal.**
  - The paper's height map decides where the stick touches. Pressure and build-up reach into the valleys.
  - A finger smudges. The eraser lifts but leaves a ghost in the valleys.
- **Oils.** A per-layer height plane (1 byte per pixel, in tiles), shaded at lift by one fixed light.
- **Gouache.** Cover = 1, plus a little pickup and an optional dry shift.
- **Leave out:** fluid simulation, pigment optics, 3D bristles, gloss and cracking.

## 1. The real media

### Charcoal
- **Vine or willow:** burnt twigs. It is soft, grey-black and powdery. It sits loosely on the paper, smudges at a touch and erases almost clean ([skizze.eu](https://skizze.eu/en/art-101-an-introduction-to-charcoal-as-a-drawing-medium/), [Virtual Instructor](https://thevirtualinstructor.com/charcoal.html)).
- **Compressed:** charcoal powder bound into sticks. It is darker, and harder to smudge or erase.
- **Tooth:** paper has tiny hills and valleys. A light stroke only touches the hilltops, leaving white specks. Pressure, or more layers, reach into the valleys.
- **Smudging:** a finger or a paper stump pushes the dust sideways and down into the valleys. The tone becomes smoother and softer-edged.
- **Erasing is drawing:** artists tone an area grey, then lift highlights out with a kneaded eraser. The hilltops come clean, but the valleys keep a ghost.
- **The stick's side:** laying it flat gives a broad, light, grainy band. Loose dust falls around the marks.
- **In grayscale:** charcoal is natively grey. Artists exaggerate contrast because its range is limited ([Majumder & Gopi](https://www.cs.unc.edu/Research/ootf/publications/Majumder_NPAR02.pdf)).

### Gouache
- **Opaque and matte:** it is watercolour with more pigment plus a chalky filler ([Bow Seat](https://bowseat.org/resources/inspiration/guide-to-visual-arts-mediums/guide-to-visual-arts-mediums-animation/guide-to-visual-arts-mediums-2d-painting/guide-to-visual-arts-mediums-2d-painting-gouache/)).
- **Light over dark:** white paint covers black.
- **Flat shapes:** it dries in flat, even shapes with crisp edges.
- **Re-wets:** a new wet stroke lifts a little of the old paint, so light over dark starts slightly dirty.
- **Value shift as it dries:** lights dry a bit darker and darks a bit lighter ([Zoungy](https://zoungyart.substack.com/p/gouache-basics)).
- **Dry brush:** thin paint on a dry brush looks chalky.

### Oils
Oil paint is pigment in linseed oil. It is buttery and opaque, and it stays wet for days ([Draw Paint Academy](https://drawpaintacademy.com/9-oil-painting-techniques-for-beginners/), [Art Supplies UK](https://www.artsupplies.co.uk/a/oil-painting-techniques)).
- **Wet into wet:** each stroke drags and mixes the wet paint beneath it. White pulled across wet black turns grey and streaky. This is the most "oil" behaviour.
- **Impasto:** paint thick enough to stand up. It shows ridges, bristle grooves and raised lips, lit on one side and shadowed on the other.
- **Running out:** a loaded brush lays solid paint, then breaks up into scratchy marks.
- **Scumble and glaze:** a scumble is a dry, broken film over the canvas weave. A glaze is a thin, transparent darkening film.
- **Palette knife:** it lays flat slabs with sharp edges and raised lips. It also scrapes paint off.
- **In grayscale:** the drag and the relief read well. Colour mixing doesn't apply, and a glaze is simply darkening.

## 2. How the apps do it

| App | Grain | Smudge / pickup | Impasto | Loading |
|---|---|---|---|---|
| **Procreate** (technique only) | *Texturized* grain is fixed to the canvas; *Moving* grain travels with the brush; *Depth* sets contrast ([handbook](https://help.procreate.com/procreate/handbook/brushes/brush-studio-settings)) | Wet Mix *Pull* drags paint; *Blur* softens it | — | *Charge* thins along the stroke; *Attack* |
| **Krita** | textured tip | **Smearing** copies the previous dab's pixels. **Dulling** fills the dab with the average beneath it. *Smudge length* and *Color rate* ([docs](https://docs.krita.org/Color_Smudge)) | Lightness tips write a height map | Bristle engine: ink depletion, soak ink ([docs](https://docs.krita.org/Bristle)) |
| **Corel Painter** | grain-reactive | Artists' Oils *Blend* and *Wetness*; *Dirty mode* | Depth stored on the canvas under one global light ([lighting](https://product.corel.com/help/Painter/540111162/Corel-Painter-en/Corel-Painter-Impasto-lighting-and-depth.html), [Thick Paint](https://product.corel.com/help/Painter/540111162/Corel-Painter-en/Corel-Painter-Thick-Paint.html)) | *Amount*; *Viscosity* (how fast it runs dry) ([controls](https://product.corel.com/help/Painter/540111162/Corel-Painter-en/Corel-Painter-Artists-Oils-controls.html)); [RealBristle](https://product.corel.com/help/Painter/540230868/Main/EN/Win-Documentation/Corel-Painter-RealBristle-Brushes.html) hairs |
| **ArtRage** (the oils benchmark) | grain-reactive | Wet oils smear unless *Insta-Dry* is on; the knife smears; *Auto Clean* ([oils](https://www.artrage.com/manuals/natural-media-tools/oil-paint-settings/)) | Thickness, with an adjustable light ([v6](https://www.artrage.com/artrage-6/)); method unpublished | *Loading* sets stroke length; *Thinners* |
| **Rebelle** | dry tools for charcoal and pastel ([manual](https://www.escapemotions.com/products/rebelle/documents/Rebelle_Manual_v.7.0.pdf)) | Blend, Smudge, re-wet | Thick or thin paint | per brush |
| **Fresco** live oils | — | Physics engine; blends with wet paint ([help](https://helpx.adobe.com/fresco/using/live-brushes.html)) | Ridges set by pressure | Method unpublished ([Wetbrush](https://history.siggraph.org/?p=210425)?) |
| **Clip Studio** | paper texture | *Blend* or *Running color*; *Color stretch* ([manual](https://help.clip-studio.com/en-us/manual_en/240_brushes/Blending_tools.htm)) | — | *Amount* and *Density* of paint |
| **Infinite Painter** | grain | Paint, **Blend** and Erase modes; a finger can Blend ([docs](https://docs.infinitestudio.art/painter/brushes/editing/)) | — | editor |
| **Expresii** | — | GPU fluid ink, MoXi's successor ([CG Press](https://cgpress.org/archives/expresii_ink_painting_software.html)); listed for contrast only | — | — |

**The pattern:**
- **Pickup** is always three numbers: how much the brush picks up, how long it keeps it, and how much fresh paint it adds.
- **Impasto** is always a separate height channel under one light.
- **Charcoal** is always grain fixed to the canvas, with pressure driving contrast.

## 3. Key papers

- **Sousa & Buchanan.** Pencil ([CGF 2000](https://diglib.eg.org/handle/10.2312/8605)); blenders and erasers ([GI 1999](https://graphicsinterface.org/proceedings/gi1999/gi1999-21)).
  - The paper surface is a height field.
  - A blender carries a buffer. It removes 70%×pressure of the lead from the paper and returns 50%×pressure.
  - Blending "fills the paper's low grains". An eraser only removes.
  - Applies directly to charcoal.
- **Majumder & Gopi** ([NPAR 2002](https://www.cs.unc.edu/Research/ootf/publications/Majumder_NPAR02.pdf)). Charcoal from a contrast curve on the paper texture.
- **Rudolf, Mould & Neufeld**, wax crayon ([2003](https://isgwww.cs.uni-magdeburg.de/~stefans/npr/entry-Rudolf-2003-SWC.html), [2005](https://isgwww.cs.uni-magdeburg.de/~stefans/npr/entry-Rudolf-2005-BDM.html)). Paper is a height field. Deposit follows contact and force. Wax already laid is smeared and redeposited.
- **Baxter et al., dAb** ([2001](https://research.google/pubs/dab-interactive-haptic-painting-with-3d-virtual-brushes/)). Wet and dry layers. The brush both deposits and picks up.
- **IMPaSTo** ([project](https://gamma.cs.unc.edu/IMPASTO/), [summary](https://math.vu.nl/~eliens/demo/local/documents/impasto.htm), [thesis](https://www.billbaxter.com/dissertation/Baxter-dissertation.pdf)).
  - Each cell stores paint volume and pigment.
  - Paint transfers in one direction per cell, from whichever side holds more, at 10% of the difference.
  - Relief is bump-mapped.
  - Too heavy for Scribulor, but its transfer rule is useful.
- **Hertzmann, Fast Paint Texture** ([NPAR 2002](https://www.dgp.toronto.edu/papers/ahertzmann_NPAR2002.pdf)).
  - Each stroke stamps a height texture that **overwrites** the height already there rather than adding to it. Adding let hidden strokes show through.
  - A small offset per stroke marks the stroke boundaries.
  - One fixed light. **This is the model for Scribulor.**
- **Chu et al.** ([NPAR 2010](https://microsoft.com/en-us/research/wp-content/uploads/2010/06/PaintModel_NPAR_2010.pdf)). A 2D **pickup map** under the brush keeps the streaks when smearing. This is Carry.
- **RealBrush** ([2013](https://gfx.cs.princeton.edu/pubs/Lu_2013_RPW/Lu_2013_RPW.pdf)). Separates *shape*, *smear* (wet) and *smudge* (dry brush or finger).
- **For contrast:** **MoXi** ([2005](https://history.siggraph.org/learning/moxi-real-time-ink-dispersion-in-absorbent-paper-by-chu-and-tai-2)) and **Wetbrush** are GPU fluid simulations, suited to ink rather than these media.

## 4. Shared part: Carry

Carry is a buffer the size of one dab that travels with the tool, as in Chu 2010 and Krita's smearing. For each pixel under a dab:

```
under = stroke-base tone (as PaintLoad reads it)
buf  += (under - buf) * pick                       // tool picks up
out   = under + (mix(buf, own, load) - under) * lay
```

The buffer shifts by the dab spacing, so streaks drag along. `load` runs down along the stroke, exactly like `PaintLoad.amount`.

- **Finger on charcoal:** no paint of its own (load 0), with pick 0.7·p and lay 0.5·p. This is Sousa's blender.
- **Oils:** pick 0.4–0.8, scaled by the wetness of the paint beneath. Use IMPaSTo's direction rule: deposit wins where the page is already close to the brush's own tone. This stops paint bouncing back and forth.
- **Gouache:** pick 0.1–0.3.

Cost, per dab:
- Each pixel takes a read, an update and a write: about 1.5–2× a plain dab.
- A 120 px dab is about 11k pixels: roughly 30–60 µs on a Mac and 0.1–0.3 ms on the device. A screen update comes about every 330 ms.
- Nothing extra at lift.

Starting point in the code:
- `PaintLoad` already does the one-average version (Krita's dulling), with a 40 px pickup length.
- `ToneDabs.soften` already drags from behind the tip.
- Merge the two and give the buffer one value per pixel.

## 5. Charcoal

**Traits that matter, most important first:**
1. White grain specks that close up with pressure and with build-up.
2. A finger smudge into soft tone.
3. Lifting highlights with the eraser, leaving a ghost.
4. A fuzzy edge and dust (the prototyped Spread dial).
5. A broad stroke with the stick on its side, using tilt.

**Technique:**
- **Contact against paper height.** With `h` from `PaperGrain` (0–1):
  - `depth = p^0.7·hold' + 0.6·existingDarkness`
  - `coverage *= smoothstep(1-depth-soft, 1-depth+soft, h)`
  - The `existingDarkness` term (one extra read) makes the valleys fill as layers build up.
  - Today's `tooth()` threshold is this rule without that term.
  - Spread's tooth contrast sets `soft`.
- **New dial: HOLD (0–1), vine to compressed.**
  - Low (vine): the darkest value stops near grey 70, the grain shows strongly, it smudges far and erases clean.
  - High (compressed): reaches grey 0–15, fills the valleys sooner, smudges less and leaves a ghost when erased.
- **Finger: Smudge.**
  - Carry with no paint of its own and no tooth test, so it fills the valleys and smooths the grain.
  - Strength × (1 − 0.5·hold).
  - The trail fades as the buffer empties.
- **Ghost erase.** Eraser strength × (1 − 0.35·hold·(1 − h)). Peaks clear and valleys keep a trace, so a half-pressure eraser becomes a highlight tool.
- **Side of the stick.** Add to the prototyped tilt widening: coverage × (1 − 0.4·lean) and depth × (1 − 0.5·lean). The side stroke is broad, light and grainy.

**Modules:**
- Tip: Stick (the nib plus tilt).
- Medium: Dry pigment, with SPREAD (prototyped) and **HOLD** (new).
- Paper: `PaperGrain` plus the build-up term.
- Life: none. Nothing changes after lift.
- Input: **Finger: Smudge** (new choice, the default for Charcoal). The pen's eraser end and the eraser tool still lift.

**Defaults and ranges:**

| Style | Spread | Hold | Pressure | Tooth | Dry |
|---|---|---|---|---|---|
| Willow/vine | .3 (.15–.45) | .2 (0–.35) | .5–.9 | .7–1 | .3–.6 |
| Compressed | .2 (.1–.3) | .75 (.55–.9) | .6–.9 | .5–.8 | .1–.3 |
| Finger smudge | — | — | strength .6 (.3–.9) | — | — |

- Sizes stay at 6/30/120 px.
- Papers: crumpled, cold press, laid.

**Leave out:** particle dust (the speck stamp is enough), fixative (layers already freeze work), burnishing and paper damage.

**Cost:**
- The build-up term adds less than 5% per dab.
- The ghost erase is one multiply per pixel.
- The smudge costs the same as Carry.
- The prototype's messy 120 px stick (351 µs per sample in JS) stays the cost driver.

## 6. Gouache

**Traits that matter, most important first:**
1. Opaque: light covers dark.
2. Flat, crisp-edged shapes.
3. Slight lifting of the paint beneath.
4. Chalky dry brush when it runs thin.
5. A small shift as it dries.

**Technique:**
- **Opacity:** the prototyped COVER dial. At 1 it switches off blooms and feathering, and the edge hardens to 1.2% of the radius.
- **Lifting:** Carry with pick 0.15–0.3. A light stroke over dark starts slightly grey and is clean within about 40 px (`PaintLoad`'s pickup length).
- **Running out:** use `PaintLoad`'s depletion (load 0.5–0.9). Its coverage then passes the existing tooth threshold, so the stroke breaks up and looks chalky.
- **Dry shift (optional):** when `WatercolorSession` marks the stroke dry, `tone += shift·(128−tone)·0.07`. That is at most about one e-ink grey step, done as one pass at the existing drying time.

**Modules:**
- Tip: round, flat or Hake.
- Medium: Body paint, with COVER, **PULL** (Carry's pick) and **LOAD**.
- Life: Dry shift.
- Input: the finger erases (default).

**Defaults:**

| Setting | Default (range) |
|---|---|
| Cover | 1 (.9–1) |
| Pull | .2 (.1–.3) |
| Load | .7 (.5–.9) |
| Soft | 0–.1 |
| Gran | 0–.1 |
| Streaks | 0–.15 |
| Drying | 0–.3 |
| Dry shift | .5 (0–1) |

- Mode: flat.
- Papers: plain, felt, cold press.

**Leave out:** matte versus gloss, a separate re-wetting water tool, cracking.

**Cost:**
- Pickup makes a dab about 1.5× as expensive.
- The dry shift takes under 1 ms on a Mac.
- The lift itself is unchanged.

## 7. Oils

**Traits that matter, most important first:**
1. Wet-into-wet drag.
2. Impasto relief lit from one side.
3. Opaque, buttery cover.
4. Running out into a scumble over the canvas weave.
5. Palette-knife slabs and scraping.

**Technique:**
- **Pull (wet drag):** Carry with pick 0.4–0.8, scaled by the wetness of the paint beneath. Drying at 0.8–1 keeps oils wet for the session. `BlowDryer` dries them, which stops the smear (like ArtRage's Insta-Dry).
- **Height plane (new)**
  - One byte per pixel per paint layer.
  - Stored in the document's 64 px tiles and allocated only where impasto lands. A full Manta layer would be 4.9 MB.
  - Each dab updates `h += (stampH − h)·deposit`, which overwrites the height, as Hertzmann does.
  - `stampH = impasto·(0.6 + 0.4·bristle lane + lip)`:
    - the bristle lanes come from `BristleTexture`;
    - the lip rises at the stroke's sides and where the paint runs out.
  - Carry also carries `h`, so smears drag the ridges.
  - The height tiles join undo.
- **Fixed-light shading at lift**
  - Light comes from the upper left. Over the stroke's box plus 1 px:
    `relief = k·(h[x+1,y+1] − h[x−1,y−1]) − e·max(0, h − blur3(h))`, with k ≈ 100·impasto and e ≈ 90·impasto.
  - **Check the sign.** A slope that rises toward the lower right faces the upper-left light, so it must read lighter. The prototype's `ridge = D[j−rw−1] − D[j+rw+1]` (exp.js) has the opposite sign, which lights the paint from the lower right. Viewers assume light from above, so ridges lit from below can read as grooves. Flip it.
  - The first term is Lambert lighting for a fixed light, reduced to a single difference. The second keeps the prototype's darker piled edge.
  - Keep the relief as an offset stored apart from the base tone, so that shading again after a smear never compounds.
  - A ridge must be larger than about one e-ink step (17 levels) and at least 2 px wide, or the 16 greys hide it.
- **Knife (new Tip)**
  - A flat head with no bristle lanes, a hard edge and a flat plateau height.
  - A lip at the trailing edge.
  - Pull 0.9.
  - With load 0 it scrapes: pick 0.8 and lay 0.2, which lowers `h` and lightens the paint.
- **Scumble and glaze are presets, not dials.**
  - Scumble: low load over the canvas tooth.
  - Glaze: Cover 0.2–0.4 with Pull 0.

**Modules:**
- Tip: Round, Flat, Hake and **Knife**.
- Medium: Body paint, with COVER, PULL, LOAD and IMPASTO.
- Paper: canvas.
- Life: **Open** (wet until dried), plus the height plane and shading at lift.
- Input: tilt turns the flat head.

**Defaults and ranges:**

| Style | Cover | Pull | Load | Impasto | Streaks | Drying |
|---|---|---|---|---|---|---|
| Oils | 1 (.85–1) | .6 (.4–.8) | .6 (.3–.9) | .6 (.4–.9) | .5–.95 | .8–1 |
| Knife | 1 | .9 | .8 | .9 | 0 | .9 |
| Glaze | .3 (.2–.4) | 0 | 1 | 0 | .2 | .9 |
| Scumble | 1 | .2 | .25 | .3 | .8 | .9 |

**Leave out:**
- Pigment optics and fluid flow.
- Simulated bristles (`BristleTexture` lanes are enough).
- Gloss, which flickers on e-ink.
- An adjustable light.
- Cracking.
- A dirty brush carried between strokes: start each stroke clean, like ArtRage's Auto Clean.

**Cost** (the reference point is the Manta watercolor settle, about 61 ms worst case on a Mac):
- **Pull:** about 1.5–2× per dab while drawing.
- **Height writes:** under 10% of a dab.
- **Shading:** two reads plus a 3×3 blur per pixel, about 1–2 ms for a 600×600 box on a Mac.
- **Whole lift:** the prototype's Oils lift at size L went from 11 to 16 ms in JS. Budget about +5 ms on a Mac, roughly 8% of the 61 ms settle. Allow 3–5× that on the device, which is still under one e-ink frame.
- **Memory:** only tiles where impasto lands.

## 8. Order of work

1. **Carry**, then **Finger: Smudge** on charcoal. The biggest win for the least code.
2. **Charcoal:** the HOLD dial, the build-up term and the ghost erase.
3. **Gouache:** COVER plus PULL.
4. **Oils:** the height plane and shading at lift. This replaces the prototype's relief, which is baked into the tones and so cannot survive a smear.
5. **Knife tip**, the Glaze and Scumble presets, and the gouache dry shift.

**Caveats:**
- ArtRage's and Fresco's methods are unpublished. Their entries above come from their manuals.
- The costs are scaled from the prototype benchmarks and the 61 ms settle, so they need checking on the device.

## 9. Pencil and Pastels

Pencil and Pastels reuse charcoal's contact rule and Carry (§4–5), and add one part: **Fill**, a memory of how full the tooth is.

### The real media

**Pencil / graphite**
- **Grades:** clay lightens and hardens the lead. 9H is the hardest and 8B the softest ([Sousa & Buchanan](https://graphicsinterface.org/proceedings/gi1999/gi1999-21), [Eckersley's](https://www.eckersleys.com.au/blogs/learn/what-are-pencil-grades)). H gives light, fine lines, holds its point and barely smudges. B is dark and soft, and smudges.
- **Tooth:** H fills the paper's valleys, so the tone looks smooth. B fills them less, so the grain shows ([Virtual Instructor](https://thevirtualinstructor.com/Artists-pencils-graphite-pencils-explained.html)). Rough paper grates graphite off a soft lead ([Caran d'Ache](https://carandache.com/us/en/how-to-draw-with-graphite-pencils)). Hard graphite laid first plugs the valleys, and soft graphite can't reach them later ([Sibley](https://sibleyfineart.com/_blog/tag/mellotex/)). Heavy pressure with H also leaves grooves that stay white.
- **Burnishing:** pressing and reworking flattens the tooth and polishes the graphite. The tone turns smooth and shiny-dark, and the paper takes little more ([Vitruvian Studio](https://vitruvianstudio.com/blog/3-ways-to-reduce-graphite-shine-in-your-drawings/)).
- **Side vs point:** the point gives lines and hatching. The side, or a chisel face, gives broad soft-edged tone that merges ([Sibley](https://sibleyfineart.com/draw-with-mike-videos/ds001-chisel-point.htm)).
- **Smudging:** a stump or finger pushes graphite into the valleys, so the tone becomes smoother and deeper (Sousa 1999).

**Pastels**
- **Soft:** mostly pigment with little binder. It is dusty, the most opaque, the quickest to cover and the easiest to blend and layer ([Jackson's](https://www.jacksonsart.com/colour/pastel)).
- **Hard:** more binder. It gives crisp lines and less dust, so it goes down first. Hard over soft rubs the soft off ([Fine Art Tutorials](https://finearttutorials.com/guide/soft-pastel-for-beginners/)).
- **Tooth saturation:** "the tooth of the paper can quickly fill up". After that, new pastel slides and turns muddy. Heavy first layers fill it fastest (same source). Light over dark works only while there is tooth left: artists spray fixative over full dark areas so light lines can bite again ([Unison](https://www.unisoncolour.com/blog/fixative-or-no-fixative-by-elena-degenhardt/)).
- **Finger blending:** fuses strokes into soft tone and pushes pigment into the tooth.
- **Oil pastel:** pigment in oil and wax. It never dries. Light over dark "won't completely cover… but will lighten" ([Fine Art Tutorials](https://finearttutorials.com/guide/oil-pastel-techniques/)). Layers saturate fast and turn dirty, so colours go side by side and the seam is blended ([Dessindigo](https://dessindigo.com/en/blog/advice/soft-oil-pastels-differences)). More pressure smears and blends. **Sgraffito** scratches the top layer to show the one beneath.

### How apps and papers do it

| Source | Pencil / pastel idea |
|---|---|
| **Sousa & Buchanan** ([GI 1999](https://graphicsinterface.org/proceedings/gi1999/gi1999-21), [CGF 2000](https://diglib.eg.org/handle/10.2312/8605)) | Paper is a height field. Each pixel stores the lead it holds, and its tone is graphite ÷ **TotalLead** (about 1000 units, the most a spot can hold). That cap is tooth saturation. Clay and wax are optically neutral, so a hard lead fills without darkening. Strokes can "destroy" grains (burnishing). Blender pick 0.7 and lay 0.5. |
| **Procreate** (technique only) | Texturized grain is fixed to the canvas, so strokes re-hit the same specks; tilt sets size and opacity ([handbook](https://help.procreate.com/procreate/handbook/brushes/brush-studio-settings)). |
| **Krita** | Texture *Height* mode, our contact level, can cover fully in one stroke; *Subtract* is grainier ([docs](https://docs.krita.org/en/reference_manual/brushes/brush_settings/texture.html)). |
| **Rebelle** | Pencil and Pastel tools share Paint, Paint & Blend (light pressure blends, hard pressure paints) and Blend modes. *Tip Tilt* widens the mark for side shading ([manual](https://www.escapemotions.com/products/rebelle/documents/Rebelle_Manual_v.7.0.pdf)). |
| **Fresco** | Dry Media pixel brushes: pencil, graphite, soft and hard pastel, with barrel roll on the pastels ([help](https://helpx.adobe.com/fresco/using/pixel-brushes.html)). |
| **Paper by WeTransfer** | Stylus draws, finger smudges ([Made Mistakes](https://mademistakes.com/mastering-paper/pencil-first-look/)): our Finger: Smudge. |
| **Rudolf et al.** (wax crayon, §3) | The model for oil pastel: deposit by contact and force, and wax already laid smears and is laid again. |

### Shared part: Fill (tooth saturation)

- **Plane:** 1 byte per pixel per layer in 64 px tiles, allocated only where Pencil or Pastels draw. Same tile store and undo as the oils height plane; a full Manta layer would be 4.9 MB.
- **Per dab, after the contact test:**
  ```
  room  = 1 - fill
  dep   = cov * room^0.7                // a full tooth takes little more
  fill += dep * rate                    // rate: H and soft pastel high, B low
  h'    = h + fill*(1-h)                // valleys rise as they fill
  ```
- **Contact:** charcoal's rule with `h'` in place of `h`. As fill rises, strokes reach the valleys and the grain closes. That is burnishing on pencil and a "full" sheet on pastel.
- **Blocking for free:** H first sets fill high but leaves the tone light, so B laid later can't darken the valleys. Pressed H lines become white lines.
- **Cover gate (pastels):** light-over-dark strength is `cover * room^0.5`. A full patch barely lightens.
- **Finger:** Carry, as Sousa's blender, plus `fill += 0.2*p` (it pushes pigment in).
- **Eraser:** it lifts tone, and `fill *= (1 - 0.5*strength)`: the tooth comes back part way. This is the only "fixative".
- **Gap today:** `WashCoverage` keeps the stronger level, so a scribble doesn't build within one stroke, only across strokes. Open choice.

### Pencil

**Traits that matter, most important first:**
1. Grade: how dark it gets against how grainy it looks.
2. Grain specks that close with pressure and passes, ending in a burnish.
3. Side shading with tilt.
4. Finger smudge (B more than H).
5. Hard-under-soft blocking.

**Dial:** **GRADE**, stored in charcoal's HOLD slot. Label it 6H…8B, with HB at 0.4.
- Darkest grey `= lerp(185, 40, g)`. Graphite never reaches pure black, so 8B ends near grey 40.
- Fill rate `= lerp(.35, .10, g)`.
- Contact depth `= p^0.7·(.9 - .35g) + .6·fill`.
- Grain edge `soft = lerp(.12, .04, g)`.
- Smudge strength `× (.3 + .7g)`.
- **Burnish:** where fill > .8, push the tone toward the grade's darkest. Shine can't show on e-ink, so leave the gloss out.
- **Side:** the charcoal side rule (coverage × (1 − 0.4·lean), depth × (1 − 0.5·lean)) plus up to 3× the width.

| Preset | Grade | Darkest grey | Pressure | Tooth | Dry |
|---|---|---|---|---|---|
| 2H | .2 (.1–.3) | ~155 | .2–.4 | .4–.7 | 0–.15 |
| HB | .4 (.3–.5) | ~125 | .2–.5 | .5–.8 | .1–.2 |
| 4B | .7 (.6–.8) | ~85 | .3–.6 | .6–.9 | .1–.3 |
| 8B | .95 (.85–1) | ~45 | .3–.6 | .7–1 | .2–.3 |

Keep today's Pencil ranges, add GRADE, and make Finger: Smudge the default finger (strength .4, .2–.6).

### Pastels

**Traits that matter, most important first:**
1. Grainy broken marks over the tooth, with broad side strokes.
2. Light over dark while the tooth lasts.
3. The tooth fills, then the sheet is "full".
4. Finger blending into soft tone.
5. Soft against hard: dusty and opaque against crisp.

**Dials:**
- **HOLD**: charcoal's meaning, how firmly the binder holds. Low is soft pastel, high is hard pastel.
- **COVER**: gouache's dial, gated by room.
- SPREAD (dust), and today's Gran as the tooth strength.

| Style | Hold | Cover | Fill rate | Spread | Pull | Gran |
|---|---|---|---|---|---|---|
| Soft pastel | .2 (0–.35) | .85 (.7–.95) | .3 (.2–.45) | .3 (.2–.45) | 0 | .6–1 |
| Hard pastel | .7 (.55–.85) | .6 (.45–.75) | .15 (.1–.2) | .1 (0–.2) | 0 | .5–.8 |
| Oil pastel (maybe) | .5 | .5 (.35–.65) | .5 (.4–.6) | 0 | .3·p (.2–.45) | .4–.7 |
| Finger blend | — | — | +.2·p | — | strength .7 (.4–.9) | — |

**Oil pastel:**
- Carry with a pressure-scaled pick (Rudolf). Its Fill saturates fast, so once the patch is full a stroke mostly smears.
- **Sgraffito needs no new code:** an eraser of size S on the top layer shows the layer beneath.
- Leave out impasto; a small ridge won't read on 16 greys.
- `BrushStyle` warns never to reorder, and Wild sits last in the paint list. Adding Oil pastel before Wild shifts Wild's share-code index. Append it after Wild, or migrate the codes.

### WYSIWYG notes

- Everything happens in the dab under the nib: contact, Fill, cover and Carry. There is no settle, no dust falling and no fixative.
- **Pastels is a paint style today, so it goes through `WashStroke`'s lift settle.** A dry stick must not change at lift. Route Pastels through the dry-stick (charcoal) renderer, and keep its place in the paint list for share codes.
- That renderer must allow light over dark for Pastels: `WildInkStroke` is "never lighter than the ink beneath".
- Oil-pastel smear stays inside Carry's live trail behind the nib, the Fast-wet window.

### Cost

- **Fill:** one read and one write per pixel, about 10–15% per dab. The `room^0.7` and `^0.5` powers are a 256-entry table.
- **Pastel cover:** two multiplies.
- **Finger and oil-pastel smear:** Carry's cost, about 1.5–2× a plain dab.
- **Lift:** nothing added. Pastels leaving the wash settle makes their lift cheaper.
- **Memory:** Fill tiles only where these styles touch.
- **Order:** add Fill after Carry and charcoal's contact rule (§8, steps 1–2). Then Pencil's GRADE, then Pastels on the dry path with COVER, then Oil pastel.
