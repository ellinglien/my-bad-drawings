# My Bad Drawings icon

The launcher icon is the owner's hand-drawn notebook with an eye on its cover
(icons 6 sheet, 2026-10-26), black on white, traced with potrace. It replaced
the tome with an eye from the owner's logo file, page 3
(described below as it was made), which replaced the Phosphor scribble loop;
the app's sample loop (`WildSample.loop`) is unchanged.

- [`mipmap-anydpi-v26/ic_launcher.xml`](../app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml):
  the adaptive icon every supported Android uses (minSdk 27), a white
  background and the vector foreground
  [`drawable/ic_launcher_foreground.xml`](../app/src/main/res/drawable/ic_launcher_foreground.xml).
  The drawing's longer side (its height) is 62 dp, centred in the 108 dp layer,
  inside the 66 dp safe zone. The Supernote launcher shows square icons; a
  round-mask launcher would clip the book's corners.
- [`mipmap-nodpi/ic_launcher.png`](../app/src/main/res/mipmap-nodpi/ic_launcher.png):
  the same drawing filling a 512 px picture (the sheet's pixels, scaled), the
  fallback and the README's image.
- [`branding/logo.svg`](branding/logo.svg): page 3 as plain SVG.

How the tome's vector was made (2026-10-24, replaced): `pdftocairo -svg -f 3 -l 3` on the logo file (not in the repo) gives absolute
M/L/C/Z paths, y down. The white background rect and clip were dropped; the
five shapes are kept unchanged and in order (black book, white page block,
white ribbon highlights, white eye, black pupil). The vector drawable uses the
SVG's units as its viewport (1 dp = 2.175 units) with one group translate to
centre it; white shapes are separate white fills drawn after the black. The PNG
was rendered from that vector with headless Chrome. Change them together.
At 48 px the eye reads as a white almond with a dot.

The loading screen (`drawable/splash.xml`) shows the same foreground (the notebook
about 305 dp tall) with the name at 48 sp under it;
`scripts/java/SplashName.java` makes `splash_name.xml` at that size.
