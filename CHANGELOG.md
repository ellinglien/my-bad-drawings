# Changelog

User-facing changes since the last release. Add an entry under **Unreleased**
with each change; when cutting a release, turn that section into
`dist/RELEASE_NOTES.md` and retitle it with the new version.

## Unreleased (since 0.97)

- Paintings save themselves. Once a painting has a name, MonoPaint keeps its
  file up to date as you work: within a minute of drawing, whenever you leave
  the app, and before you open or start another painting, so switching
  paintings no longer asks "Save changes?". The **Save painting** button is
  gone. A **New painting** is kept inside the app until you give it a name with
  **Save painting as…**, and starting or opening another painting first asks
  whether to name it. On a named painting, Save painting as… makes a copy under
  a new name.

- Transparent paint keeps up with the pen: a transparent brush now lays its
  paint as fast as an opaque one, so big strokes no longer lag behind.
- The **Blow dryer** is back, right below the Brush pen in the toolbar. Tap it
  to dry all wet paint at once: watercolor, wet canvas paint and wet brush pen
  ink stop running and stay as they are. Wet canvas stays on, so your next
  stroke still blends into the paint it touches. It blows a little air for a moment, and the tool you were using
  stays selected. You can hide or move it in Settings like other toolbar items.
- Watercolor keeps up with the pen too: a big watercolor stroke shows right
  away as its color glazed over the page, then turns into watercolor (lifting,
  soaking and flowing) a moment later, catching up as soon as the tablet has
  time, mostly once you lift the pen. The finished painting is the same as
  before.

- The **Wet brush pen** is back, as a choice on the Brush pen: open the Brush
  pen's settings and pick Dry or Wet, and the toolbar button keeps your
  choice (Dry at first). The wet one's water stays wet and lets ink run
  through it like ink in water, faster where the water is fresher, and it
  runs smoothly instead of in square chunks when you move fast.
- Wet brush pen ink settles much faster: a big stroke now looks finished a
  few seconds after you lift the pen instead of around 20, and the ink keeps
  running while you're still drawing. Wet paint uses all of the tablet's
  processor cores.
- The Wet brush pen now holds ink between strokes. A clean brush soaks up ink
  it rubs over, lightening that spot a little, since dried paint holds more
  ink than shows: touch a black line and the brush fills with gray while the
  line stays dark. It soaks up less as it fills, until it
  only smears. Put it down somewhere else and the ink it collected comes out
  over lighter paint. With the Wet brush pen selected, the eraser in the color
  bar cleans the brush; its dot shows for a moment when you tap it.
- The **Wet canvas uses gravity** setting is now **Watercolor uses gravity**,
  and it's on by default.
- With **Watercolor uses gravity** on, Wet brush pen ink now runs downhill
  through its water and pools at the low edge of the wet area. Tilt the
  tablet a little for a soft gradient, more to pool it hard.
- New brush option, **Watercolor mode when transparent**: with transparent
  paint the brush paints like the Wet brush pen, on a dry or wet canvas. It lifts the
  paint it touches, soaks up ink and carries it between strokes, and its ink
  runs and pools with gravity. The selected color is the lightest shade the
  brush lays; it never runs out of it, and darker colors soak up less. White
  makes it work just like the Wet brush pen. Tap a color to reset the ink the
  brush has picked up. On a dry canvas, dried paint stays put under a wash:
  each brush dab over it soaks it a little, so a couple of passes over line
  art leave it alone, but keep scrubbing a spot and the brush lifts it. It never
  drains into the wash by itself. Paint that's still wet mixes
  freely, and on a wet canvas everything lifts at once. With the option off,
  transparent paint works as before.
- The brush's **Oil paint** option is now **Oil paint mode when opaque**: it
  only applies to opaque paint, so transparent paint never runs out.
- Choosing a color while a brush pen is selected switches back to the brush
  you were using before, built-in or favorite, in that color.

## 0.97

- Layer names show again in the Layers panel on the Nomad.
- On the Nomad, tapping a button in the Layers panel no longer leaves it stuck
  on screen after the panel closes, even through a screen refresh. The whole
  panel now disappears at once.
- Autosave no longer breaks when Supernote Cloud sync is on. The autosave copy
  of the painting you're working on now stays inside MonoPaint instead of in
  `Document/MonoPaint`, where syncing it left hundreds of `_recovery` copies and
  eventually made autosave fail. MonoPaint removes those leftovers from the
  folder the first time it opens. Paintings you save are still kept in
  `Document/MonoPaint`.
- Settings has a new **Screen drawing** choice. **Fast e-ink** is the normal
  mode. **Standard Android** is slower but avoids MonoPaint's direct screen
  updates, for firmware (such as a beta) that shows black boxes or other screen
  glitches. Settings also shows your firmware build and whether MonoPaint has
  been tested on it, and MonoPaint tells you once when it starts on firmware
  it hasn't been tested on.
- The Settings window is wider, and choices with long names (such as
  "Standard Android") are no longer cut off.
- Exporting one page of a painting with several pages now names the file for
  its page, such as `statues004.png`, instead of `statues.png`.
- Export PNG has a **Dithered, as on screen** option. It saves the same black
  and white dots MonoPaint shows on the tablet instead of smooth grays. Dots
  only look right at one size, so you also pick the size: the page's own, as
  large as fits a Manta or Nomad screen (for example, exporting a Manta
  painting as the Nomad would show it), or a custom width or height that keeps
  the page's proportions. The app remembers these choices for later exports.
- A page you expanded to fill the screen now opens at 100%, where you painted
  it, instead of shrunk (to about 91%) to fit beside the toolbars.
- MonoPaint no longer keeps the tablet awake. The screen now sleeps and locks
  after the tablet's normal idle time, so leaving MonoPaint open won't drain
  the battery.

## 0.96

- The color selector is faster on the Nomad: only the small marker below the
  shade strip moves, leaving the strip's dots untouched.
- Rapid color taps leave fewer stray marker copies on the Nomad. The marker
  follows each tap immediately, with a cleanup a quarter-second after you pause
  instead of extra refreshes after every tap.
- Paintings are now kept in `Document/MonoPaint`, where the Files app shows them
  and they survive uninstalling or reinstalling MonoPaint. Each launch asks for
  file access until you allow it, saying how many paintings are still stored
  inside the app; then they and the painting you're working on move there
  automatically. After a reinstall, paintings already in that folder open again.
  On tablets other than Supernotes, paintings go to the standard
  `Documents/MonoPaint` instead.
- Export PNG now saves to `EXPORT/MonoPaint` on a Supernote, where the Files
  app shows it, once MonoPaint has file access. Other tablets still use
  `Pictures/MonoPaint`, and a folder chosen with Change folder… still wins.
- The menu no longer has a Canvas size item. Each page still offers Keep
  canvas size or Expand canvas the first time its bars are hidden.
- Paintings are now `.mpaint` files. Existing `.tsm` paintings are renamed
  automatically the first time this version starts.
- New painting and Open painting now ask whether to save unsaved changes first:
  Save, Don't save, or Cancel. If nothing changed since your last save, they go
  straight ahead.
- On the Nomad, shape previews and the gradient direction line now follow the
  pen without leaving a trail of copies or catching up after you stop.
- The app now calls your artwork paintings: New painting, Open painting, Save
  painting as…, and so on.

## 0.95

- MonoPaint now supports drawing on the Supernote Nomad, with a compact Pages
  menu that leaves more room for the color bar.
- Hide the bars for more drawing room. Swipe one finger from the canvas off a
  bar's edge of the screen to hide or show just that bar, starting from any
  layout. The artwork stays in place. Swipes that stop inside the canvas do
  nothing, and Supernote's own gestures still work. On the Manta, upward swipes
  now work reliably even though its touch screen loses the finger just before
  the top edge.
- A two-finger double tap hides both bars when both are showing, or brings both
  back when neither is showing. With one bar showing, it undoes the last bar
  change. The three-finger gesture is gone, and Fullscreen in the menu offers
  all four bar layouts.
- The first time you hide the bars on a page, illustrated buttons let you keep
  the canvas size or expand it with blank margins that fill the screen, without
  stretching the artwork. Each page has its own choice; revisit it in Canvas
  size. Expansion can be undone, and showing the bars again never crops a page.
  Existing drawings still open normally.
- Quickly double-tap the Zoom tool to return to 100%, with the artwork back
  where the page began; an expanded page reaches under the bars. A single tap
  still locks or unlocks zoom and pan, and a double tap keeps your lock setting.
- Export PNG has a **Change folder…** button. It opens at Supernote's EXPORT
  folder, which the tablet's file manager shows (Pictures/MonoPaint doesn't).
  The app remembers the folder you choose for later exports.
- Nomad fixes: tilt-sensitive brushes like Flat follow the pen's tilt; color
  bar markers follow the pen smoothly without leaving a trail and stay on the
  chosen shade after a screen refresh; the wetness slider, swatches, buttons
  and page controls use fast pressed feedback with softer-edged icons.
- Page-navigation buttons clear before the page changes, avoiding repeated
  flashes while the page counter and arrows update.

## 0.91

See the [0.91 release notes](https://github.com/mpdairy/monopaint/releases/tag/v0.91).

## 0.9

See the [0.9 release notes](https://github.com/mpdairy/monopaint/releases/tag/v0.9).
