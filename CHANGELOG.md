# Changelog

User-facing changes since the last release. Add an entry under **Unreleased**
with each change; when cutting a release, retitle that section with the new version.

## Unreleased

## 0.9 (2026-10-30): first public preview

The first public build of My Bad Drawings, a sketchbook journal built on MonoPaint 0.98.3. Everything below, back
to MonoPaint's own 0.98.3, is what the fork added before this release. Tested on the Supernote Manta only; never
tried on a Nomad.

- **Two-finger double tap for Zen Mode** (the owner, 2026-10-30, as MonoPaint's fullscreen): tap the page twice quickly with two fingers to hide or show the bars. A single two-finger tap still undoes, about a third of a second later (it waits to see whether a second tap is coming); drawing, turning the page or leaving undoes at once first. Two undos in very quick succession now toggle Zen Mode instead, so pause a moment between them.
- **A new app id, with everything brought along** (2026-10-30, for the public release): the app is now
  `ing.ell.mybaddrawings` (it was `app.helpyy.inkwash`, named for a private project), which Android treats as a new
  app. One last update of the old app leaves its brushes, slots and settings, and its private copy of the working
  notebook, in `Document/My Bad Drawings/.handover`; the new app takes them on its first launch with file access and
  says "Brushes and settings brought over". Notebooks were already in `Document/My Bad Drawings` and stay where they
  are. The old app can be uninstalled afterwards.
- **Shapes and the eye swap places** (the owner, 2026-10-30: "because the fun button looks a bit like the shapes button .. let's swap the location of the visibility and shapes buttons"): Shapes now sits beside the timer, and the eye (Show Kinds) beside Play under them.
- **The side bar's icons match** (the owner, 2026-10-30: "the icon buttons seem a bit out of whack with each other"): Menu, the Dropper and the Lasso, Show Kinds and the timer, Shapes and Play, the drop, Refresh and Zen Mode are now drawn at one size (the hourglass, the drop and Zen's circle, which fill their whole box, a step smaller so they look the same), and each pair sits evenly either side of the side bar's middle. The eye had sat off to the left, and the timer and eye were smaller than the rest.
- **The eraser's dots show a light touch** (the owner, 2026-10-30: "make sure the eraser dot preview size is the lightest touch size"): as every brush's, the ring is now what a light touch of the pen erases, not a typical one, so it is smaller.
- **Lemon juice takes minutes** (the owner, 2026-10-30: "make it appear minutes late instead of right away. maybe 5 minutes?"): it goes on as a faint trace as before, then darkens in over about 5 minutes instead of 4 seconds. Its Dries In dial in Brush Lab runs from 10 seconds to 10 minutes ("5:00"). A lemon juice brush you saved keeps its place on the dial, so one that took 4 seconds now takes about 3 minutes. Marks already on a page keep their own time.
- **The eraser's dots go after a second too** (the owner, 2026-10-30: "eraser preview dots should follow the same pattern as the other tools"): erasing with a finger shows its ring for the first second, then only erases.
- **Menu and Zoom sit evenly in their corners** (the owner, 2026-10-30): the Menu icon now sits about as far from the screen's side as from its top, at the corner rather than the middle of the side bar. The zoom percentage has a little more room on its outer side, so it is no longer crowded against the edge.
- **Notebook Settings show when you worked in it** (the owner, 2026-10-30: "include the range of dates since it was started to when it was last edited"): its foot says "Oct 29 – 30, 2026 · 6 pages", from the day it was made to its last save; a notebook only used on its first day still says "Made Oct 29, 2026".
- **Settings come first in the notebook's strip** (the owner, 2026-10-30: "put it to the left of the cover"): its tile is now before the front cover; turning back from the front cover shows it.
- **No ghost of the page over Notebook Settings** (the owner, 2026-10-30: "the notebook settings page sometimes has some ghosting from the previous page"): leaving a page for Notebook Settings, the shelf or Page Settings, your latest marks and the side column's icons could stay on screen over the new one. The whole screen now redraws cleanly as it opens, with one blink.
- **Shapes and Play are back in the side bar** (the owner, 2026-10-30: "move shapes button to below visibility and timer, and move fun button beside it"): a new small row under the eye (Show Kinds) and the hourglass holds **Shapes** and, beside it, **Play** (the confetti). Shapes opens its five shapes and Filled for the brush in use, and its button is marked while you draw shapes. Play opens Magnetic Poetry, Mirrors and Replay Page, and is marked while the magnets or mirrors are on. The main menu no longer has a Play row, and Play's menu no longer has Shapes. On the Manta turned sideways the size and shade picker is a little shorter, as that row takes its spare room.
- **The Daily Prompt is now the Self-Portrait Prompt, a notebook setting** (the owner, 2026-10-30: "maybe rename it as 'self portrait prompt'? ... put it in notebook settings as an optional thing, new for every new page"): a notebook's Settings has a new **Self-Portrait Prompt** row, under Nib Preview, with Off and On (Off in every notebook, old ones too). While it's On, each page you add gets its own card, dealt at random, with Start and Deal Another as before; pages already there are left alone. A card you leave unused still goes when you leave the page, and the page doesn't get another, so a blank page never asks twice. It's gone from Play's menu, and the card's heading says "Self-Portrait Prompt". Your prompts file keeps its name and your edits.
- **Wax crayon finishes faster when you lift the pen** (the owner, 2026-10-29: "this crayon i've been using has a long delay between drawing and the line showing up on screen"): the line you see while drawing was already the final one, but at each lift the crayon did its wax-resist work for the whole stroke again (so washes slide off it) and then redrew the whole stroke's area, about a sixth of a second on the Manta in which the next stroke had to wait. The wax is now worked out while you draw, and the lift redraws nothing it doesn't change: on the desktop the lift's work is about 60% less and the redraw is gone. The page ends exactly as before. Drawing each bit of crayon costs a little more (about a quarter, on the desktop). A crayon still lays nothing at the lightest touch, so a stroke's first millimetres can show late; that is unchanged.
- **No crash on a first start** (found 2026-10-29 while timing the crayon): opening the app with no notebooks yet, straight to the shelf, closed it at once. It now opens to the shelf.
- **Nib preview shows a second, then goes** (the owner, 2026-10-29: the dots were "like a little bug on the screen demanding my attention"): when the pen comes to the page after being away a couple of seconds, the dots show the size and place it would land for one second, then disappear while it keeps hovering. Picking a new tool or size shows them again for a second. Lifting the pen after a stroke doesn't bring them back; taking it away and bringing it back does.
- **Nib preview at a light touch for every brush** (the owner, 2026-10-29: a watercolour that starts small showed its normal size): bristle brushes show how wide they land, before the stroke spreads them, and stipple brushes their spread at a light touch.
- **Smooth edges on big pens and brushes too** (the owner, 2026-10-29, "yes fix that too", after the bumpy-edge fix for chisel washes): at big sizes (about 50 px and up) a pen or brush line, like the Springy brush, had faint regular notches along its edge, stronger when drawn quickly. Its edge is now smooth. Pencils at big sizes are smoother too. At their usual sizes, pens, brush pens, markers and pencils draw exactly as before; charcoal and pastels keep their dusty edges. The web Brush Studio draws the same.
- **Numbered copies go too** (the owner, 2026-10-29: "yes remove the numbered ones too"): once, a saved brush's numbered namesakes ("Wash 2", "Wash 3") are treated as the same name as "Wash", and only the newest of them all stays.
- **Brush Lab saves a new brush with Save as New** (the owner, 2026-10-29: "move to save as new if it's a new brush (instead of staying on save)"): a brush that isn't from your library shows only **Save as New**, in solid ink; a brush opened from your library shows **Save** (update it) and **Save as New** (a copy).
- **One brush a name in your library** (the owner, 2026-10-29: "i notice in the library there are a bunch of duplicated names... can we remove the older versions of those?"): once, when the app starts, where several saved brushes share a name (capitals aside, so "Wet wash" and "wet wash" count as one), only the one saved or used most recently stays; the older ones are removed. The library and its backup file in Document/My Bad Drawings both follow. A brush you keep keeps its own size and grey. From now on nothing adds a second brush of a name: Save as New picks the next suggested name that isn't taken, and a brush brought in from a code file, or one you named yourself, gets a number after its name ("Polite Smear 2"). Recent is not the library and is left as it is.
- **Brush Lab's buttons start at the left** (the owner, 2026-10-29: "in brush lab you can remove the text ABOUT THIS BRUSH and move the menu buttons to the left there"): the "About This Brush" label is gone, and New, Open, Save and Save as New sit at the left of their row.
- **Brush Lab shows the size and shape beside the picker** (the owner, 2026-10-29: "beside the picker show the preview size / shape as in the toolbar"): to the right of the picker box, the same dot as in the side bar: the brush's nib at its size, in the grey you picked. It changes as you pick a size or a grey or change the brush. On the Nomad the picker box is a little shorter to make room.
- **Smooth edges on chisel and flat brushes** (the owner, 2026-10-29: "i'm testing a brush and noticing that it looks like a stamp brush (but it isn't)... i see a repeated pattern along the edge instead of a smooth line"): a wash, paint or dry brush with a Chisel or Flat Nib tip left a row of little beads along its edge, as if stamped. Its edge is now smooth at every size, and the dry brushes with a flat tip lose their sawtooth edges. Big opaque paint brushes get slightly smoother edges too. Print brushes keep their dots and lines, and most other brushes draw exactly as before. Wobble had nothing to do with it.
- **Brush Lab works on the slot you came from** (the owner, 2026-10-29: "the active toolbar isn't important here, since whatever toolbar slot was selected at the beginning should flow through. that will give the brush lab some room"): the row of ten toolbar slots at the top is gone. The lab changes the slot that was selected when you opened it, and Back puts your changes there as before. The space went to the scratch pad and the part cards.
- **Brush Lab's size and grey use the side bar's picker** (the owner, 2026-10-29: "we can use the same horizontal picker from the toolbar in place of the pickers there"): the four size buttons and the grey bar are replaced by the same box of two triangles as in the side bar, turned on its side. Greys run black on the left to white on the right, sizes fine on the left to broad on the right. Tap or drag on either triangle. The Sizes window no longer opens from Brush Lab.
- **Brush Lab has no Basic mode** (the owner, 2026-10-29: "let's just have it advanced, no need for a basic mode"): the Basic | Advanced switch is gone and the lab always shows everything Advanced did: every choice (Hatch, Stipple, Stamp, Spiro and the toys too), locks and dice on every part, and a part's full card when you tap it.
- **Tweak It is gone from Brush Lab** (the owner, 2026-10-29: "let's remove the tweak it section. we can keep that on the online version maybe but here it's too confusing"): the six variations under the scratch pad are gone from the app; the web Brush Lab still has them.
- **New, Open, Save and Save as New in Brush Lab** (the owner, 2026-10-29: "a - once it's edited, maybe suggest a new name?"): the buttons by the brush's name are now **New** (a fresh brush in the same style), **Open** (pick one of your library's brushes, or paste a brush code), **Save** and **Save as New**. Open a library brush, change it and press Save, and that library brush is updated in place, keeping its name (and its own size and grey) unless you renamed it. Save as New always adds a copy and leaves the original alone. A brush that didn't come from the library (the slot's own, a New one, a pasted code) is added on Save. Once you change a brush, "Edited" shows under its name, with a suggested new name that says what the brush now is ("Suggested: Polite Smear"): tap it to use it, or the pencil to type your own (the field starts with the suggestion). Save as New uses the suggestion unless you named it yourself. The slot always keeps what you're editing, saved or not.
- **Open's form closes without a code** (the owner, 2026-10-29: "once you click load, you can't shrink that form without entering something"): it now opens under the buttons, so tapping Open again closes it, and it has a **Cancel** button. Nothing changes either way.
- **No hover dots while the pen touches** (the owner, 2026-10-29: "if there's a way to disable them when the pen is actually touching the screen, i'd like that"): the dots show only while the pen hovers; erasing with the pen no longer shows the eraser's ring. A finger erasing still shows its ring, since the finger hides the page.
- **Deadpan names for new notebooks and brushes** (the owner, 2026-10-29: "mostly deadpan"; "have the brush names hint at what the brush does for sure"; "not too bad, the name of the app is tongue in cheek"): the fantasy names are gone. A new notebook is called something like "Several Opinions About Soup", "Probably Fine", "Doodles for a Goose", "Mistakes Volume Three" or "Minutes of the Pigeon Committee", its day still at the end; now and then (about one in six) it gets a made-up word instead, Edward Lear style: "The Flimwhistle Papers". Its template no longer changes the name ("not necessary"). A new brush's name always says what kind of mark it makes, in Title Case: "Polite Smear", "Regrettable Inky Hake", "Dusty Aunt Charcoal", "Inky Pen Junior", "Smear of Mild Regret", "Wash for Beginners", "Inky Pen Situation", "Budget Dusty Charcoal", or its plain words like "Sloppy Inky Drag"; about one in ten has a made-up word that still says what it does ("Wobblefuzz Pen Deluxe", "Dreamy Shy Washmonger"). Charcoal is called charcoal, never a stick ("don't use the word stick for charcoal"). Notebooks and brushes you already have keep their names.
- **Scribulor is now My Bad Drawings** (the owner, 2026-10-29). The launcher says the new name, and the loading screen is now the owner's own drawing of it. On first launch the app moves its folders for you: Document/Scribulor, EXPORT/Scribulor and Pictures/Scribulor become "My Bad Drawings" folders, and your brushes, prompts, toolbar and stamps files take the new name. Nothing is copied or overwritten; if a file with the same name is already there, both are kept. Supernote Cloud sees the moved EXPORT files as new ones. The app icon is still the old one until the new one is drawn.
- **Zen Mode fills the screen** (the owner, 2026-10-29: "we need to [limit] the min zoom so that no background will show... also need to tweak this for horizontal"): in Zen Mode the page always covers the whole screen, upright or sideways, its top showing, and a pinch can't zoom out past that.
- **Zen Mode's button: a dashed circle while off, a plain circle while on** (the owner, 2026-10-29: "the icon for zen mode activated is a full circle, but it should be what is on the toolbar . the toolbar icon should be a dotted circle icon initially").
- **Show Kinds sits beside the hourglass; the wet drop has its own line under them** (the owner, 2026-10-29: "move the wet indicator to its own line, moving the visibility button there").
- **The hover dots show a brush's lightest mark** (the owner, 2026-10-29: "the dots reflect the normal size, not the light size, which is what i think it should be"): for brushes that grow as you press, like the paints, the dots now ring the mark a light touch makes, not a normal one. The eraser's dots still show a normal touch.
- **A prompt you don't use leaves the page** (the owner, 2026-10-29: "for prompts, if it's closed without doing it, remove it from the page"): if you deal a Daily Prompt card and then leave the page without drawing anything (turn the page, go back to the shelf, or put the app away), the card is taken off that page. It won't pop up again there, won't print in the PDF, and the page counts as blank again. Pressing Start without drawing counts as not doing it. Draw anything at all (a line, a magnet) and the card stays with the page. On a page that already had a drawing, it stays only if you add something after dealing it.
- **The hover dots can be turned off, per notebook** (the owner, 2026-10-29: "for the dot preview.. have it enabled by default but able to be disabled in the notebook settings?"): a notebook's Settings has a new **Nib Preview** row, under See-Through Pages, with Off and On. Turn it Off and the dots that show where the nib will land no longer appear while the pen hovers in that notebook. It's On in every notebook, old ones too. The Dropper's ring and the eraser's ring while erasing still show.
- **Pressing harder works again** (the owner, 2026-10-29: "the eraser used to be pressure sensitive.. is it not any more?", then "pressure for the eraser doesn't seem to affect the size ..."): since the Manta's firmware update (3.31.45), the pen reads a wider range of pressure, and everything past a medium press counted as the same full press. Now pressing harder keeps growing the eraser and the brushes, right up to a hard press. Normal pressure feels the same as before for the brushes. The eraser no longer grows when the pen leans: the way you usually hold the pen had it at full size already at normal pressure. Now a light touch erases a fine line, normal pressure about half its size, and a hard press all of it. Strokes saved for Replay that were drawn firmly before this replay a little thinner.
- **A quieter hover ring** (the owner, 2026-10-29: "at small sizes the 8 dots seems like too many.. you could get away with even 1 at super-fine sizes. can it be relative?"): the dots that show where the nib will land now follow its size: one dot right where it lands for the finest nibs, two or three for medium ones, five at most for big brushes (eight always until now).
- **The picker box sits snug** (the owner, 2026-10-29: "a little more snug .. tighten that up by 50%"): the space above it (from the eraser) and below it (to the Dropper and the Lasso) is half what it was.
- **Side bar tidy-up** (the owner, 2026-10-29): the hourglass is now the same clean style as the other icons ("seems like an old one still"); the wet drop is the same size as the icons around it ("try to keep them all proportional"); the ghost (Refresh) sits at the foot of the side bar, about a centimetre above the Zen Mode circle ("move the ghost button just above the zen mode button, with about 1cm space"); and the shade side of the picker box has no outline, so you see each grey as it is ("remove the stroke outline from the shade side so it's easier to see the right shade").
- **New icons, from Iconoir** (the owner, 2026-10-29: "i like iconoir, but would like you to design replacements for the following..."): the side bar, menus, Brush Lab and page icons are now the clean line icons of Iconoir (iconoir.com), in place of the hand-drawn set. Where Iconoir had nothing that fit, or the owner asked, Scribulor has its own in the same style: Finger Action's pointing finger, the open hand, the ghost (Refresh), the lasso, the broom (Clear page), word tiles for Magnetic Poetry, the stamp, the shapes, the brush, the brush holding paint, the hair dryer, Brush Lab's hand and drying drop, and Peel off masking fluid. The Show Kinds eyes read as steps at a glance: Full a solid pupil, Half the eyelid half closed, Faint a dashed eye, Hidden closed with five lashes.
- **The hair dryer speeds the drying up instead of stopping it** (the owner, 2026-10-29: "looks like the hair dryer just stops the image as it is instead of accellerate the drying processing"): tap the wet drop and a wash ends up just as it would if left to dry, spread out, settled and with its edges, only sooner. The same goes for Mold, floating paint and wet paint. The dryer keeps blowing until it's done. A very big wash that runs downhill may still be stopped where it is after a few seconds.
- **The hair dryer blows its air once** (the owner, 2026-10-29: "it has lines in the icon but the animation adds new lines"): the dryer drawing no longer has air lines of its own, so only the moving puffs show.
- **A longer shade strip and size slider** (the owner, 2026-10-29: "can you stretch the shade selector and size selector a bit to allow for more fine control? maybe 1.5x the height"): the picker box in the side bar is longer, so each grey and each size has more room and is easier to land on. It grows only into room the side bar has to spare ("grow only into spare room"), so nothing else gets smaller: half as long again on the Manta and the Nomad upright, a little less on the Manta sideways, a little on the Nomad sideways.
- **A clean picker box** (the owner, 2026-10-29: "doesn't need to look hand drawn now... match the line size from iconoir", then "the pointy corners, they need to be a little thinner at the end"): the two triangles are now straight-edged outlines, as thin as the icons' lines, with an even gap between them. The greys fill the left triangle; the size triangle is open. Each triangle's point runs right into its corner of the box and narrows to a fine tip, so the size slider's bottom shows how fine the finest sizes are.
- **Picking what the eraser takes picks the eraser too** (the owner, 2026-10-29: "I'd like the eraser to be selected after a menu selection other than clear page"): hold the eraser, tap Pen, Paint, Dry Media or Everything, and the eraser is ready to use. Clear page still just clears.
- **Textures erase apart from pen** (the owner, 2026-10-29: "I used the textures quite a bit in combination with pen and I'd love to have a way to erase one or the other... Or maybe make them paint?"; "my texture I mean the hatching and Ben day etc."): hatching, stipple, Print Shading (Ben-Day, halftone and the rest) and Screentone now count as Paint, whatever they're drawn in. The eraser's Pen takes only the pen lines and leaves the texture; Paint takes the texture (and washes) and leaves the pen. Show Kinds' Paint eye fades them, and with Paint hidden a texture brush says so. They look the same as before, but where a texture crosses a pen line the pen now stays on top. Marks already on your pages keep the kind they were drawn with.
- **Go over a pattern lighter** (the owner, 2026-10-29: "if I put a dark mark with that pattern, I can go over that area again and make it the same pattern, but lighter"): a Print Shading or Screentone stroke over another now replaces it where it goes, so a lighter grey (or lighter pressure) leaves a lighter pattern, and a different pattern swaps it. Pen lines and washes under it stay. Going over at the same grey still lays the same dots. Only patterns drawn from now on are replaced. A light pattern no longer whitens what's under it.
- **Side bar: the wet drop, the hourglass and Show Kinds sit right under the Dropper and the Lasso, the ghost below them after a gap** (the owner, 2026-10-29: "move the wet droplet icon, timer and visibility buttons above the ghost button, and move the ghost button down a bit").
- **"Finger" is now "Finger Action"** in the ≡ menu (the owner, 2026-10-29), as in "Finger Action: Eraser".
- **One Import, with folders, and a single page from a PDF** (the owner, 2026-10-29: "can we merge the pdf and image import flow? and allow user to select a specific page from a pdf if they dont want to import the whole thing? so it'd be just one import thing"; "it's also very hard to find anything with the image import flow.... more like supernote file explorer"): the menu's Import PDF and Import Image are now one **Import** row. It opens a wide picker that works like the Supernote's file explorer: the folder you're in across the top (tap Storage or any folder there to go back up), then its folders and its pictures and PDFs in big rows, with a small picture of each and the day it was saved, folders first, then the newest files. Tap a folder to open it, a file to import it. It opens in the folder you last imported from. A picture goes on the page as before. A PDF shows its first page, with ‹ and › to flip through (hold to jump ten): **New Notebook** makes a notebook of the whole PDF as before, **This Page** puts just the page shown on the page you're drawing on, to move and size like a picture. A one-page PDF still asks, without the arrows.
- **Turned sideways, the page fills the screen's width** (the owner, 2026-10-28: "if it's horizontal i want the top part of the page to show, and the bottom i can move to with my fingers... and vertical it should fit perfectly"; "that % should be the max percent, whatever it is"): rotate to landscape and the page opens as wide as the screen, past 100% if that's what it takes, its top showing; drag with two fingers to see the rest. A pinch can zoom in but never out past the width, so the page always fills the screen sideways. Upright, the whole page still fits. Double-tapping the zoom readout comes back to this view.
- **Template visibility joins Show Kinds** (the owner, 2026-10-28: "add the template visibility there whenever there is one... merge them"): on a page with a template, the Show Kinds popup has a **Template** eye first, beside Pen, Paint and Dry Media, stepping Full, Half, Faint, Hidden like the others. The separate Template Visibility button is gone. The Show Kinds button is always an open eye (the owner, 2026-10-29), and every name in its popup shows in full: each column is as wide as the longest, so Template and Dry Media are no longer cut off.
- **Export a single page** (the owner, 2026-10-28: "each page should have a way to save page in various ways.. pdf png, and possibly layers for photoshop? ie. the diff media layers"): hold a page and its Page Settings now has **Export Page** beside Delete. Pick **PDF**, **PNG** or **Layers**; the page is saved to EXPORT/Scribulor as, say, "Harbour p5.pdf", and a note says where. PDF and PNG look exactly like that page in Export Now's copies. Layers is a Photoshop file (.psd) with the paper, the template's lines and each kind of mark on its own layer (Dry Media, Washes, Pen, Paint, and Magnets on top), so a desktop app can hide or adjust each one.
- **Library, not Advanced** (the owner, 2026-10-28): the long-press menu's button that opens the Tools page now says Library.
- **One Lasso, with its choices over the piece** (the owner, 2026-10-28: "in supernote there is just one lasso tool... when the bounding box appears, above it is a toolbar offering choices"): loop something with the Lasso and a small bar appears just above the box (below it when there's no room above) with **Cut**, **Duplicate** and **Delete**. Duplicate leaves the piece where it is and floats a copy just beside it to drag into place, on this page or, turning the page, another. It steps aside while you move, size or turn the piece and comes back where the piece lands; it goes when the piece is put down, cut or deleted. The separate Trash button is gone. Holding two fingers on the Manta's edge strip still makes the pen delete whatever it loops. With nothing lifted, tap the Lasso to Paste what you last cut.
- **The ghost (Refresh) is back in the side bar, under the Dropper and the Lasso** (the owner, 2026-10-28: "set apart so a finger can press it easily"): alone in its row, the whole row is its button. The top bar gives its room back to the page previews.
- **The hourglass is the size of the other icons** (the owner, 2026-10-28: "shrink the hourglass to match the other icon sizes"): the sand timer beside the wet drop now matches the eyes under it.
- **Your saved brushes tidied, and each brings its own size and grey** (the owner's review, 2026-10-28): the saved brushes were renamed and cleaned up as the owner listed them (41 removed, the rest given plain names such as Classic fineliner and Crosshatch wash). A named brush picked into a slot now brings its own size and grey; brushes without one keep the slot's, as before.
- **A new notebook's name ends with its day, and just the one name** (the owner, 2026-10-28: "include the date, like 26-10-28, at the end"; "just have the one, don't suggest so many"): New Notebook offers one name, such as "Tome of the Damp Wanderer 26-10-28", on the shelf and in the title too; there are no other names under it. A name you type is kept as typed, and notebooks you already have keep their names. A notebook's options (no erasing, one brush) follow the day when there's room.
- **Reset to Default on the first ten brushes** (the owner, 2026-10-28): Verve felt, Ink wash, Watercolour washy, Rob boss and the rest of the default toolbar now reset to their own default settings, not to another Basic tool, and the row only shows once you've changed one.
- **The owner's toolbar is the default** (the owner, 2026-10-28: "I adjusted all of those settings to how I like them so save those as default"): a first launch's ten slots are now Another pencil, Steady line, Verve felt, Fude fountain, Ink wash, Watercolour washy, Rob boss, Filled shape, Ben-Day brush and High lighter, each at the owner's own size and grey, the eraser at 31 px. The old first brushes are still Basic tools. A toolbar holding exactly these ten in another order takes the new order once, each brush keeping its size and grey and the selection staying on its brush; any other toolbar is left as it is. Another pencil now shows the HB pencil drawing.
- **Search the Tools page** (the owner, 2026-10-28: "it's difficult to find things in the tools library"): tap the magnifier beside the tabs and type; the library shows the brushes from Tools, Recent and Saved whose name, style, kind or description has every word. × closes it.
- **Stamp is a tool, and a fuller quick menu** (the owner, 2026-10-28: "just create a tool that is a stamp"): **Stamp** is a Basic tool any slot can hold, in the long-press menu with the owner's drawing and under Pens on the Tools page. A slot holding a Stamp gets a **Stamp ▾** row in its long-press menu: Cut Stamp, Turns, Spacing and Ink Fade for that slot. The long-press menu also gains Cheap ballpoint, Gel pen, Smooth fountain, Permanent marker, Acrylic paint marker, Ms. Sketch (was Mr. Sketch), Ink wash brush and Cover hake (now the owner's saved one), shown by their strokes until they're drawn; Sumi and Comb stay on the Tools page only. Reset to Default is now the menu's top row.
- **Eight slots on the Nomad** (the owner, 2026-10-28: "eight slots for the nomad it's fine. Just cut off the bottom two"): the Nomad's side bar shows slots 1 to 8, either way up, their rows a little taller; the Tools page shows the same eight, two rows of four. Slots 9 and 10 keep their brushes and come back on the Manta. If one of them was selected, the Nomad selects the first slot.
- **A quicker Tools page, grey slots and Tools First** (the owner, 2026-10-28: "loading the tools page is quite slow"; "make the non-selected tools a little grey"; "an advanced 'switch' on the pop-up menu"): the Tools page opens without a blank frame first, its pictures drawn in the background and kept, and drawn ahead while the drawing screen sits idle, so even the first opening shows them at once. The slots you aren't changing are grey; the one you are is black and underlined. A new **Tools First** row at the foot of a slot's long-press menu makes the long press open the Tools page straight away; the Tools First chip beside Toolbar on the Tools page turns it off again.
- **More room in the side bar** (the owner, 2026-10-28): Undo and Redo are gone from the side bar; tap with two fingers to undo and three to redo, as before. Shapes and the confetti button are gone too: **Play** is now a row of the ≡ menu, marked while a toy is on, holding Magnetic Poetry, Mirrors, Shapes, Daily Prompt and Replay Page (Stamp is on the Tools page; Surprise Me stays in Notebook Settings' Tools; Fling is gone). The sand timer sits beside the wet drop, and Show Kinds is now an eye beside Template Visibility, closed while a kind isn't Full. The ghost (Refresh) moved to the top bar's far end, right of Zoom, with a finger-sized target. Export Now and Delete Notebook moved from the menu to the foot of Notebook Settings, where Rename already was. Two rows fewer leave the Manta's rows a little roomier.
- **Show Kinds** (the owner, 2026-10-28: "same method as the template view levels... and even the same icons"; "just per page"): a new row in the menu shows pen, paint or dry media on the page Full, Half, Faint or Hidden, with the same eyes as Template Visibility. Tap Show Kinds for one popup: Pen, Paint and Dry Media side by side, each with its eye underneath; tap an eye to step that kind Full, Half, Faint, Hidden. It stays open while you tap its eyes and closes when you draw or tap Show Kinds again. Each page keeps its own; pages from before show everything. It's only the screen: exports and page pictures always show every kind whole. Picking a brush whose kind isn't Full says so ("Dry media is faint"); drawing with a hidden kind draws nothing and says "Pen is hidden, see Show Kinds", and the eraser leaves a hidden kind alone.
- **A cleaner Tools page** (the owner's mockup, 2026-10-28): the page is in the app's own lettering, with no boxes or grid lines. The scratch pad is shorter, with the tool's name and description written inside it and Clear in its corner; Back to, Load Kit and Cut Stamp sit beside Clear when they apply. Brush Lab is a button in the top bar. The ten slots show their drawings, as the side bar does, over their names. In the library, chips replace the All Styles menu ("instead of dropdown show chips for the categories"): All, Pens, Markers, Brushes, Washes, Paint, Dry Media, Print and Odd Ones. The grid is four across, every drawing trimmed to one length, and under All the tools come in groups by kind. The Basic tools tab now reads Tools. The sizes and the grey bar are no longer on this page: set them on the drawing screen or in Brush Lab. The highlighter's drawing is a little flatter, to match the other pens.
- **New daily prompts, and Start no longer holds the card open** (the owner, 2026-10-27: "here's a replacement set. just these draw yourself as a..."; "they shouldn't lock the page so permanently"): the cards are now 193 self-portraits, three minutes each ("Draw yourself riding a whale."), from the owner's sheet, repeats dropped. A timed card's Start sets the sand timer to its time and closes the card, so the page is free to draw on; "Time's up" at zero, nothing locks. A prompts file already on the tablet keeps its cards: delete it to get these.
- **Ten brush slots** (the owner, 2026-10-27: "we're at 8 now might as well have 10"): the side bar has ten brushes, not eight. Your eight stay where they are; the two new slots under them hold Cheap ballpoint and Permanent marker, which you can change with a long press as any slot. A notebook with its own toolbar of eight keeps its eight and gains the same two. Brushes on Bar goes up to ten. Everything else in the side bar is as before; turned sideways the brush rows are a little shorter so all ten fit (a touch on the Manta, more on the Nomad). On the Tools page the ten sit in two rows of five, so on the Manta upright the Library shows one row fewer.
- **Timed Pages is a guide now, and the lock is a setting** (the owner, 2026-10-27: "i would like to be able to edit after the timer runs out, it's just a guide"; "could the lock be a setting in the notebook? like, timers lock pages?"): at zero a timed page shows the sand timer's small "Time's up" popup, with Next Page and Done, and the page stays editable. The countdown, its pause and its blink are as before. A Diary Page's finished sections stay open too. To lock pages as before, turn on **Lock Pages** in the notebook's settings, under Timed Pages (greyed until a page is timed). It's off in every notebook, old ones too, so a page a timer locked before opens editable; turn Lock Pages on and it's locked again. PDF pages stay locked as always.
- **New daily prompts, and Start no longer holds the card open** (the owner, 2026-10-27: "here's a replacement set. just these draw yourself as a..."; "they shouldn't lock the page so permanently"): the cards are now 193 self-portraits, three minutes each ("A self-portrait riding a whale."), from the owner's sheet, repeats dropped. A timed card's Start sets the sand timer to its time and closes the card, so the page is free to draw on; "Time's up" at zero, nothing locks. A prompts file already on the tablet keeps its cards: delete it to get these.
- **The picker box** (the owner, 2026-10-27: "this new box design.. i'd like to try using it to replace the shade picker and size select"): the shade strip and the size slider are now one box, the owner's own drawing, cut by a white diagonal. The lower-left triangle is the shade: white at the top to black at the foot, its tab on the left. The upper-right triangle is the size: black, broad at the top to a point at the foot, its tab on the right. Press in a triangle and drag up or down; a press on the white line goes to the nearer side. Everything else works as before: the same shades and sizes at the same heights, each tool keeps its own, Undo, the long press for Opacity on the shade. It takes the same room in the side bar. With Left-Handed the box is mirrored, so the shade stays on the outer edge.
- **Sand timer in the side bar** (the owner, 2026-10-27: "just have a sand timer icon, press it to have it open a menu and you can set a timer"): under Play, a sand timer icon. A tap opens a small menu beside it: Off, 1, 3, 5, 10 or 30 min. It counts down right away in the same spot, across pages, and pauses while the app is closed. Tap the countdown to change it or turn it off. At zero a small "Time's up" popup with Done; a touch anywhere closes it. Nothing locks, nothing is saved in the notebook. A page with Timed Pages on shows its own countdown there as before, and the sand timer hides on that page. The icon is a stand-in until the owner draws one.
- **Each kind of mark on its own layer** (the owner, 2026-10-27: the groundwork for hiding or fading a kind): every page
  layer now keeps dry media, washes, pen and opaque paint apart, each made only when that kind first draws there.
  - The eraser's Pen, Paint and Dry Media take exactly their own: where pen crosses pencil, Dry Media takes the pencil
    and leaves the ink as drawn, and Pen the reverse. Everything is as it was.
  - The page stacks them, bottom to top: dry media, washes, pen, opaque paint, whatever order they were drawn in. So a
    pencil or pastel mark drawn over ink now lies under it, a wash over gouache lies under the gouache, and opaque paint
    always covers pen.
  - A stump or finger smudge blends the kind its Medium is (a stump: pencil and charcoal), leaving pen lines crisp; water
    lifts washes.
  - Pages drawn before keep their paint as one layer, under anything new. They open, show and save as before, byte for
    byte. A page with the new layers opens in an older version as it showed, as one layer.
  - Nothing new to set. Showing or fading a kind comes next.
- **The size slider's tab moves cleanly** (the owner, 2026-10-27: "weird ghosting glitch with the size selector indicator ... the shade picker doesn't have this same problem"): it now moves on the screen the way the shade strip's tab does, so no blink a moment after the pen lifts and no half-drawn tab while dragging.
- **Lines round the page at every zoom**: the diagonal lines now fill any room the page leaves, not only under 100%,
  so they no longer vanish when a pinch or pan takes the zoom to 100% or more.
- **The owner's notes page** (2026-10-27):
  - A new drawing of the block eraser in the side bar ("replace eraser drawing").
  - The wet indicator's dry drop is the owner's own dashed teardrop; the wet drops are unchanged.
  - Below 100% the room around the page is filled with fine diagonal lines, as the owner's swatch ("a background for
    when the canvas is < 100%"). Drawn once when the view changes, never moving; never in a saved page or a copy.
  - Play's menu: no Diary Page (still in Page Settings' Template) and no ready brushes (Spiro, Marbling, Mold, Fading
    Ink, Lemon Juice, Crayon Scratch: all still in the tool picker). Magnetic Poetry, Mirrors, Stamp, Surprise Me and
    Fling stay.
- **Opacity on the shade strip** (the owner, 2026-10-27: "add the long press for transparency to the shade picker"): with an opaque paint in use (Gouache, Oils, Acrylic paint marker, Mr. Sketch and the like), a long press on the grey strip opens an Opacity strip beside it, a thin glaze at the top to full cover at the foot. A tap or drag sets that brush's own Opacity: each slot keeps its own, Brush Lab shows the same value, and Undo takes it back. Touching the page or a second long press closes it. With Left-Handed it is mirrored. On washes, markers, ink, pencil and charcoal there is no Opacity (it would only repeat the grey); the long press says "Opaque paint only".
- **The owner's pens in Basic tools** (2026-10-27): seven Brush Studio saves, kept exactly as made, under the owner's names. Fineliner: **Cheap ballpoint** (blots if slow), **Smooth fountain** (thins at speed). Brush pen: **Ink wash brush** (wash, behind). Marker: **Permanent marker** (slow drying), **Acrylic paint marker** (crisp, opaque), **Mr. Sketch** (almost opaque). Pencil: **Another pencil** (soft, toothy). The Brush pen and Marker ones were saved as Watercolor and moved to the owner's groups; they draw the same. Long names show whole on two lines of a card, over no description.
- **Brush Lab Dials rebuilt on today's web engine** (docs/brush-lab/dial-demo.html): a tile for every part, choice and dial, the after-lift ones (Drying Time, Flow While Wet, Takes Ink, Plume, Float, Mold, Fading, Lemon Juice) played over time; built from the lab's source by dial-demo-src/build.cjs, so it no longer falls behind.
- **Stroke previews match the brush drawings** (the owner, 2026-10-27: "the stroke emerges from the left (as if it started off screen) and continue to where the brush images end"). A brush with no drawing shows a stroke that enters at full pressure from the row's left edge, with no tapered start, and ends where the drawings end, with its usual taper. With Left-Handed it is mirrored like the drawings.
- **Menu wording** (the owner, 2026-10-27): Settings reads Notebook Settings; the hand row names the other hand, Left-Handed, or Right-Handed with its hand mirrored once the column is on the right.
- **The notebook's name sits in from the screen's edge** as far as it sits up from the foot, about 28 dp (the owner, 2026-10-27: "like, a tab").
- **Reset to Default in the tool picker** (the owner, 2026-10-27: "if brush has been edited in brush lab from one of the base saved brushes, add a 'reset to default'"): a slot's long press shows Reset to Default over Brush Lab and Advanced when its brush is a Basic tool you changed in Brush Lab. A tap puts the original back, at the slot's own size and grey; Undo takes it back and the edited brush stays in Recent.
- **Tool picker grouped by kind** (the owner, 2026-10-27: "sort by category instead of alphabetical"): pens and nibs, markers, brushes, water and washes, dry media, oils, then the odd ones (Spirograph, Fill in, Dot tone, Lemon juice…). No headers.
- **Notebook settings on one page** (the owner, 2026-10-27: "some text is cut off .. also, instead of a more button, just include everything on that page"):
  - What a row is set to stays on one line, never broken inside a word ("Of / f", "Crackl / e", "Dot Grid · / Faint" before); it takes the room it needs, cut with … only for an imported template's very long name. The settings screens a row opens leave room for Ukulele's wider letters too.
  - No More row: all nine settings are on the page, in three groups with a grey line between them: Template, Random Template, Page Colour, See-Through Pages; No Erasing, Timed Pages, Tools; Cover, Cover Picture. They fit both tablets either way round (rows a little shorter on the Nomad turned sideways).
- **Page bar, tidied** (the owner, 2026-10-27): the previews' right and bottom lines are a little thicker (3 px, were 1); the page-turn carets ‹ › are smaller (18 dp icons, same 32 dp touch area); 8 dp of space between the last preview and the caret next to it. The Notebook button at the bar's end is gone: Settings and Open Notebook in the main menu stay; the back gesture still opens the notebook's pages.
- **Rotate turns the page with the screen** (the owner, 2026-10-27: "on rotate, rotate the canvas. In Supernote notes this zooms in to fit slightly"). After Rotate the drawing reads upright the way the Manta is held, the whole page as large as fits the new screen; before, the page stayed put on the glass and lay sideways. The page itself is never turned: pen, tilt, wet paint's downhill, page previews and PNG copies all follow the page upright. A drawing made sideways in landscape before this now shows sideways there.
- **Bigger Rotate button** (the owner, 2026-10-27: "make it a bit bigger"): half as big again, icon and tap target.
- **Toolbar tweaks** (the owner's list, 2026-10-27):
  - The brush line at the foot shows the brush's name and words only, no stroke preview; a tap on the words still opens Brush Lab.
  - The tray's drawings (the eight slots, the smudge tool and the eraser) sit against the screen's edge; with Left-Handed they're mirrored against the right edge, the in-use bar on the page side.
  - The in-use bar beside a tray row is a little shorter than the row.
  - The grey strip is on the left and the size slider on the right, each tab on its outer side.
  - The size-and-grey dot sits on plain paper, no square round it.
  - The notebook's name in the foot's corner gets the room it needs, as the shelf says it (no date before a title), cut with … only on a narrow screen.
  - Refresh (the ghost) has its own row under the wet indicator, which sits alone and centred; on the Nomad turned sideways, where there's no room for another row, it stays beside the wet indicator.
- **Two fingers on the edge strip = Trash** (the owner, 2026-10-27): hold two fingers on the Manta's left edge strip and the pen deletes what it loops, as Supernote's own gesture does; lift and your last tool is back. A loop mid-stroke finishes first.
- **Refresh button** (the owner, 2026-10-27): a ghost where Finger was, beside the wet indicator; Finger is back in the main menu as its own row. A tap on the ghost gives the screen one full e-ink flash, as the side swipe does.
- **Notebook name** (the owner, 2026-10-27): the open notebook's name, very small, in the foot's corner away from Zen Mode's button (bottom right; bottom left with Left-Handed). It isn't a button, and Zen Mode hides it with the rest.
- **Rotate prompt in the middle** (the owner, 2026-10-27): when tilting the Manta suggests turning the screen, its button shows in the middle of the screen, not a corner.
- **Each tool remembers its own size and grey** (the owner, 2026-10-27: "i think we need per tool memory for size and
  shade"). Switching tools brings back that tool's last size and grey instead of carrying the current ones over; a new
  brush picked into a slot takes the slot's. Kept across a restart. On the first launch every slot starts at the size
  and grey it drew at before.
- **Refresh button** (the owner, 2026-10-27): a ghost beside Menu at the top of the toolbar; a tap gives the screen one full e-ink flash, as the side swipe does.
- **The brush line has more room** (the owner, 2026-10-26): its name and words run to 480 dp before the … cut, so the wider font fits a description.
- **New face: Ukulele, the owner's own hand-drawn letters** (the owner, 2026-10-26: "font seems good.. a little more
  horizontal spacing"). Every word the app shows is in it, with a little more room between letters than the font has
  and a bold drawn for titles and buttons. Signs it lacks (%, &, *) come from the old face. Ukulele font by Elling Lien,
  CC BY 3.0.
- **Spiro rolls round an oval or a bar** (the owner, 2026-10-26: "on oval and rectangle etc would be cool"). Spiro's Mode
  has Oval and Bar beside Line and Ring: the first stroke drags the track corner to corner, as a shape is drawn, then
  circling the pen rolls the wheel round inside it. The bar's corners are rounded so the wheel rolls round them.
- **Wet blooms and Pooling wash in the tool picker** (the owner, 2026-10-26): hold a slot and they're beside Soft wash, the wash brush's picture for now.
- **Ballpoint is steadier and skips less** (the owner, 2026-10-26: "too wobbly and leaves too many gaps"). Its line wavers
  less than half as much, and a skip is rare and short, a hint of a ballpoint rather than a broken line; still thin,
  slightly grainy and darker pressed. A Ballpoint already in a slot keeps the old one: pick Ballpoint again from Tools.
- **Broad nib is now the Fude pen** (the owner, 2026-10-26: its drawing "is actually a fude bent fountain pen.. so
  tilting changes the size of the nib"). A fountain pen whose line is fine held upright and about five times as broad
  laid low, its bent face flattening as it lies; a little wider pressed. A pen without tilt writes a fine fountain pen
  line. A slot already holding Broad nib keeps it as it was; pick Fude pen again for the new one.
- **The size and grey dot is the tool's nib** (the owner, 2026-10-26: "preview the shape of the drawing tool in the
  square preview"). A chisel, broad nib, flat or hake shows its shape at its angle, as it lies at rest, the size its
  longer side; round tools and the eraser keep their circle.
- **The size and grey pickers and the page count are drawn by hand** (the owner, 2026-10-26). The size slider is the
  owner's filled triangle, its tab still beside it; the grey strip's greys fill the owner's drawn bar; the size and grey
  dot sits in the owner's drawn square; and the page bar's "44 / 45" has the owner's slash, the numbers' height.
- **More of the owner's drawings** (2026-10-26): the Shapes button and its rectangle, square and oval, the Trash, Finger,
  Cover picture off and Left-handed icons, and the app icon (a notebook with an eye). Ben-Day brush and Engraving pen have
  their own drawings, in the side bar and the tool picker; other Print tools, no longer sharing Dot tone's, show their
  stroke. The felt marker's and soft charcoal's drawings are the pens' height.
- **The wax crayon's drawing is the pens' height** (the owner, 2026-10-26: "reduce height of crayon to match the other
  pens"). Its drawing is drawn at three quarters of its size, its tip where it was, in the side bar and the tool picker.
- **Page previews on the top bar have a line on their right and bottom only** (the owner, 2026-10-26), a page's edge,
  instead of a box all round. The page drawn on keeps its ink lines and its tab under it.
- **Copies and edits of a tool show its drawing too** (the owner, 2026-10-26: three slots still showed strokes). A slot finds the
  tool its brush came from by its style and seed, which renaming and Brush Lab's dials keep, then by name less a copy's
  number: "Wax crayon 3" shows Wax crayon. A tool not drawn shows its kind's one drawing (Sketch pencil the HB pencil,
  Ben-Day brush Dot tone's); a brush made in Brush Lab keeps its stroke.
- **Shapes moves to the side bar, beside the Trash** (the owner, 2026-10-26: "shapes, that can move to the toolbar
  beside the recycling icon"). A tap opens the same menu of shapes for the slot in use; the button shows its side bar
  while a shape is the tool. It is no longer in the tool picker.
- **The side bar's brushes show the owner's drawings** (the owner, 2026-10-26: "you can add the brush drawings now").
  A slot holding a tool from the tool picker shows that tool's drawing, its tip coming out of the bar's edge, as the
  smudge tool and the eraser do. A brush with no drawing yet (one made in Brush Lab, Mold) keeps its stroke. The size
  and shade show in the dot, as before.
- **Zen Mode flashes clean** (the owner, 2026-10-26): turning it on or off ends with one full flash, as a page change does.
- **Tool drawings traced into vectors** (the owner, 2026-10-26). The tool picker's drawings and the side bar's smudge
  tool and eraser are now crisp outlines at the same size and place; Springy brush keeps its picture for its grey tip.
- **The side bar: undo and redo, the Dropper, the Lasso and the Trash under the size and grey pickers** (the owner,
  2026-10-26), over the wet indicator; the pickers move up under the eraser.
- **Icons drawn by hand** (the owner, 2026-10-26). 73 of the app's icons are now the owner's own drawings, traced
  into vectors: the bars, menus, Brush Lab, mirrors and layers. Icons left blank on the sheets keep their old look.
- **Fix: the side bar's Dropper and smudge tool** (the owner, 2026-10-26). The Dropper now always shows beside the
  Lasso; with the eraser or the smudge tool in use it was hidden, leaving an empty cell. A grey picked then becomes
  the brushes' grey. The smudge tool's row shows only its side bar when selected: the small square of held paint
  beside its tip is gone.
- **Fix: Spiro's guide rings leave no ghosts** (the owner, 2026-10-26: "a lot of ghosting of the circle lines even
  after i am finished drawing the spirograph"). Each size the guide passed through while you dragged out the ring left
  a faint circle on the screen. When the figure is done, the page now flashes once, as after the eraser: two seconds
  after the pen moves away. A ring set up and then dropped (another tool, Undo) flashes the same, shortly after. Never
  while drawing, and never between setting the ring up and drawing it: the guide stays up then.
- **Import Image and Import PDF can be cancelled** (the owner, 2026-10-26: "with import... i noticed there's no way
  to cancel. so you have to select something in order to get out of that screen"). Android's file screen had no
  Cancel, and the Supernote no back button. Both now open the app's own list, with Cancel: the pictures (PNG, JPEG,
  WebP, GIF, BMP) or PDFs in Document, EXPORT, MyStyle, Note, SCREENSHOT, INBOX, Pictures and Download, newest first,
  each its name over its folder, a picture's small thumbnail beside it. Six to a page, with ‹ and › to turn, nothing
  scrolls. A tap imports it as before. With nothing there it says "No pictures found" or "No PDFs found".
- **The side bar, laid out again** (the owner's mockup, 2026-10-26). Top to bottom: the menu alone; the eight
  brushes; then the smudge tool and the eraser, each a row of its own showing the owner's drawing of it (the spudger,
  the block eraser), their long presses as before; undo and redo, the Dropper and the Lasso, and the Trash in one block;
  the size and shade dot, the size triangle and the shade strip; then the wet drop and Finger, Template Visibility and
  Play. A Timed Pages countdown shows under them. The brush line stays along the bottom; its stroke sample is twice
  the size, so the line is a little taller and the page a little shorter (7 dp on the Manta): a page made before
  shows whole at about 99%.
- **The whole page, always, outside zoom** (the owner, 2026-10-26: "when switching to zoom mode or back.. will some
  part of the image be obscured? let's make it fit neatly in both"). A page sits whole and centred in the room the bars
  leave, never under the side bar, the page bar or the brush line: a page made the other way up, or one bigger than
  the screen now, shows a little smaller (90 to 99%) instead of at 100% with an edge hidden. A quick double tap on Zoom
  comes back to that view (it went to 100% before). Zen Mode fits the page again in the whole screen, centred instead
  of in the top corner, and back. Locking or unlocking Zoom keeps the view as it is; pinching is as before, never
  below 100%. A page made here, the usual case, still fills the room at 100% exactly.
- **Fix: the eraser takes a placed picture and what was drawn before it** (the owner, 2026-10-26: "drew a line, then
  placed an image... eraser then could not erase the original line, just the image"). Import Image's Place put the
  picture on a new layer above and drew on it, so the eraser, the eraser's area delete and finger erase reached only
  the picture. Place now lays it on the layer drawn on, as any mark: the eraser takes the picture and the line alike,
  as on paper, and Undo takes the picture away. This reverses the 2026-10-22 design's new layer, at the owner's
  request; the notes "Layers full, on this layer" and "Notebook full, on this layer" are gone with it.
- **The flash comes sooner after a zoom, and at a page change** (the owner, 2026-10-26: "Can flash happen a bit
  sooner after zoom?", "Flash on page change"). After a pinch or a pan the page flashes 0.8 s after the last
  finger lifts, not a second; fingers back on the page before then put it off, so a few quick adjustments still flash
  once. Turning to another page, adding one or opening one ends with the same flash, once the new page is drawn.
- **Fix: Spiro's ring setup no longer freezes the page** (the owner, 2026-10-26: "Spiro extremely sluggish ... It's
  unusable"). Dragging out the ring redrew the dashed guide once for every point the pen sent, so a second's drag left
  the app 20 seconds and more behind the pen (the bench: 17.7 s after the lift, now 0.2 s). The guide is now redrawn
  once each time the page updates, at the pen's latest distance. The figure is drawn as before.
- **Fix: Replay Page no longer lays blobs over the page** (the owner, 2026-10-26: "Replay was cool but there was a
  random blob (a previous drawing) overtop that wasn't from that page"). The replay draws the strokes as they were made,
  and the eraser and the Lasso aren't recorded, so a shape since erased, carved or moved came back where it was first
  drawn, filled in. The replay now shows only what is on the page: wherever the page has nothing, it stays paper.
- **The side bar, rearranged** (the owner's notes, 2026-10-26). The smudge tool and the eraser (the Trash beside it)
  now sit right under the brushes. **Finger** is back on the bar, beside the smudge tool: it shows what one finger does
  (a brush's stroke, the eraser, the smudge tool, or a struck-through hand for Nothing), and a tap opens the choices
  beside it ("finger switch to toolbar. single tap to change"); the menu's Finger row is gone. In a No Erasing notebook
  it still pans. The wet drop moved from the brush line to the bar, beside Play under the Dropper and the Lasso ("move
  wet to near the pickers"), and Template Visibility went up beside the menu to make room.
- **The brush line says what a Basic tool is** (the owner, 2026-10-26: "add description to brush info on bottom of
  screen"). After the name comes the tool's short description, the one its card shows on the Tools page and in the tool
  picker: "Swell brush · presses darker, Brush pen". Your own brushes show their card's words as before.
- **The page flashes clean after a pinch, a pan or the eraser** (the owner, 2026-10-26). The clean that follows them
  now asks the Supernote for its full refresh too, the side swipe's flash, so their ghosts go without a swipe.
- **Shapes are back** (the owner, 2026-10-26: "yes shapes, lets bring them back"). MonoPaint's Shapes tool is in the
  tool picker: hold a slot, tap **Shapes**, and pick **Line**, **Rectangle**, **Square**, **Oval** or **Circle**, or
  **Filled** to fill them (tap it again for outlines). The pen then draws that shape: put it down, drag, lift. A
  rectangle, square or oval goes corner to corner, a circle out from its centre. Each shape is one Undo, in the slot's
  grey, its outline as wide as the slot's size. Tap any tool, or pick a brush, to draw with brushes again. Its picture
  in the picker is an icon until it is drawn. Shapes aren't mirrored or replayed yet.
- **Spiro's ring is set up like a shape** (the owner, 2026-10-26). In Ring, put the pen down where the centre goes and
  drag out: the dashed grey ring follows the pen, growing and shrinking, and nothing is drawn. Lift, and the ring stays
  on the page at that size. Put the pen down again and go round: the figure follows the pen round the centre. Lift, and
  the next stroke starts a new centre. A tap still draws the whole figure at the brush's own size. Undo takes back a ring
  set up and nothing else; another tool, a page turn or any other stroke drops it. The finger, and Brush Lab's test page, draw
  round a ring of the brush's size where they land. Replay Page draws a Spiro made before today at the brush's size.
- **Fix: Play's Spiro lets you set the circle's size** (the owner, 2026-10-25: "it's supposed to start with figuring
  out the size of the circle but it doesnt work"). The Spirograph it puts in the slot drew its loops along the line,
  so there was no ring to size. It is now the toy itself: put the pen down where the centre goes and pull outward; the
  dashed grey ring follows the pen, and when you start going round, that is the ring the figure is drawn in. A tap
  still draws the whole figure at the brush's own size. A Spirograph already in a slot stays as it was: pick Spiro in
  Play again.
- **A small tool picker on a slot's long press** (the owner, 2026-10-25: "change the library to a smaller set of
  tools (the ones with drawings mostly) and add an advanced button which pulls up the library we have now"; "long press
  on any slot to open. single tap selects the tool"). Holding a slot now opens a small menu beside it, as Play's, with
  the 29 Basic tools you drew, each its drawing over its name, the mischief ones in among their kin. Tap one to put it in
  the slot; the one the slot holds is marked. Under them, **Brush Lab** opens Brush Lab on the slot's brush and
  **Advanced** opens the Tools page as before. A tap on a slot still only selects it; a dirty brush's hold still offers
  Clean Brush first, its Tools now the picker.
- **Fix: Replay Page plays** (the owner, 2026-10-25: "i havent been able to get replay to work"). A hand resting on the
  page ended the replay the moment it began, so the page was put back before the screen showed it. Now only the pen or
  a finger landing on the page stops a replay; a resting palm doesn't. The speeds **1×**, **2×** and **4×** open as a
  small menu where the menu was, not beside the eraser (the owner: "why would it appear beside the eraser way far away
  from the menu where you clicked?").
- **The Trash has its own button** (the owner, 2026-10-25: "lets separate the lasso from the trash.. and put the trash
  tool in the toolbar"). The Trash sits beside the eraser: tap it and the loop you draw deletes what it rings; tap it
  again, or pick the Lasso, the Dropper or a tool, to put it down. The Lasso always selects, and its long press is gone.
  The eraser's side bar is now at its left, the Trash's at its right. Its icon is a recycle sign (the owner: "yes use a recycle icon instead"). The size and shade dot has a hairline box round it (the owner: "the box i meant around the preview dot").
- **Turning a page moves the underline alone** (the owner, 2026-10-25: "avoid a refresh of the top bar as well unless
  there is a new page added, just move the underline"). A page turn changes only the page bar's underline and frames,
  the number and an arrow; the previews no longer go blank and fill again, and in a long notebook the previews stay put
  until the page turned to is off the bar. Adding, deleting or moving a page, or another notebook, draws the bar again.
- **No Fill page pop-up on a grey change** (the owner, 2026-10-25). Picking a grey on a blank page no longer shows
  Fill page beside the strip; a page's colour is in Page Settings, Page Colour.

- **One size for every brush** (the owner, 2026-10-25: "stop remembering per tool size and shade and use only the
  current setting"). The size slider now sets one size that every brush draws at, as the grey strip already sets one
  grey. Picking another brush or putting another brush in a slot keeps the size and the grey; Undo and Back to take back
  the brush, not the size. The eraser and the smudge tool keep their own size, as in Procreate ("eraser keeps its own
  size"): the slider sets theirs while they are picked, and the eraser is never finer than 0.3 mm. On the first launch
  the size of the brush in use becomes the one size. A notebook's own toolbar brings its brushes, not their sizes.
- **Lasso: Cut, Copy, Paste and a loop that deletes** (the owner, 2026-10-25: "there should be options for lasso ...
  fast delete ... or cut. and then the usual copy"). With a piece lassoed, a tap on the Lasso offers Cut, Copy and
  Delete, and Paste once something is cut or copied. Copy no longer lays a copy on the page: it keeps the piece to
  paste, as anywhere else. Paste floats the piece where it was cut or copied, on this page or another, to drag into
  place. With the Trash (above) whatever the loop rings is deleted as soon as the loop closes. Each is one Undo.
- **One size for every tool** (the owner, 2026-10-25: "stop remembering per tool size and shade and use only the
  current setting"). The size slider now sets one size that every brush, the smudge tool and the eraser draw at, as the
  grey strip already sets one grey. Picking another tool or putting another brush in a slot keeps the size and the grey;
  Undo and Back to take back the brush, not the size. The eraser is never finer than 0.3 mm. On the first launch the
  size of the tool in use becomes the one size. A notebook's own toolbar brings its brushes, not their sizes.
- **A tool's line shows the moment the pen touches it** (the owner, 2026-10-25: as fast as MonoPaint). A slot, the
  eraser, the smudge tool, the Lasso or the Dropper now shows its line beside it on the pen's or finger's touch, not on
  the lift, about 60 ms sooner. The old line, the size and the grey follow on the lift as before. A touch that slides off
  the tool, or a long press on a slot or the eraser, picks nothing and the line goes again.
- **Picking a tool changes only its line** (the owner, 2026-10-25: "the brush slots.. they dont need to update so
  often, just the line indicator beside", and "most of the things now use the bar to indicate it's active"). Picking a
  slot, the eraser, the smudge tool, the Lasso, the Dropper or Play now updates only the line beside the old and new
  choice, not the slot's stroke or the tool's icon. The Lasso and the Dropper switch in the quick black and white
  update, as a slot does. Coming back from the Tools page or Brush Lab, or after an Undo, only the slots whose brush
  changed are drawn again, and a size or grey picked no longer touches the Dropper.
- **Slow buttons show the tap at once** (the owner, 2026-10-25: "MonoPaint's UI feels particularly fast"). Menu, Play,
  the page arrows and Notebook turn black with a white icon the moment the pen or a finger touches them, in the screen's
  quick black and white update, and go back to white as the menu or the new page shows. The tool slots, the grey strip
  and the size slider are unchanged: their answer is already quick.
- **A clean page after zooming, panning or erasing** (the owner, 2026-10-25: ghosts while zooming that took several
  screen refreshes, and erased marks showing again until a refresh). A second after a pinch or pan ends, and once the
  pen is away after erasing, the page gets one clean refresh with the Supernote's usual short flash. The bars don't
  flash, and nothing refreshes under the pen or while the fingers move.
- **The eraser takes crayon** (the owner, 2026-10-25: "Crayon didn't erase with eraser. Base erase should erase
  everything."). A crayon lays wax as well as its marks, and that wax kept the eraser off the marks under it, at any
  size and more so at large ones. Now the eraser takes the crayon and its wax in one pass, so the paper is clean again
  and a wash over it soaks in. The same goes for Wax resist, which used to stay. Masking fluid still lifts first and
  what is under it goes on the next pass. The eraser kinds Pen, Paint and Dry Media also reach marks under wax now, and
  Dry Media takes a crayon. The eraser still starts on Everything and nothing about the choice changed.
- **Brush Lab's test area and the Tools page's Scratch Pad keep up with the pen** (the owner, 2026-10-25: "it was
  sluggish in brush studio but not in main"). They now gather the pen's samples and draw them together, and space their
  screen updates, as the drawing screen has since its speed work, instead of drawing and updating the screen after
  every one of the pen's 480 moves a second.
- **Snappier picks and a quicker menu** (the owner, 2026-10-25: "MonoPaint's UI feels particularly fast"). Picking a
  slot or the eraser shows on the screen sooner, in the quick black and white update, and the eraser no longer redraws
  the whole side column a second time. With the eraser or the smudge tool the brush line keeps the preview's place blank,
  so the tool's name stays where a brush's name is. The menu shows about three times sooner after a tap on Menu.
- **Quicker taps on the drawing screen** (the owner, 2026-10-25: "MonoPaint's UI feels particularly fast"). Picking a
  slot no longer shows its name beside the column, the brush line under it says the brush. A pick moves the mark and
  changes the brush line at once, in fewer and quicker screen updates. The page bar's arrows, number and previews
  change without the blink, and menus open in one pass and close without redrawing the page and the bars.
- **No more ghosts coming back to the drawing** (the owner, 2026-10-24: "noticed lots of eink ghosts on testing").
  Back on the drawing screen from Brush Lab, the Tools page, the notebook screens or any other screen, the whole screen
  gets one clean refresh with the Supernote's usual short flash, so nothing of the screen before shows through. Brush
  Lab and the Tools page do the same when they open. Drawing itself adds no refreshes.
- **A new install starts on the owner's own toolbar** (the owner, 2026-10-24: "the set of tools i have on the manta now are the ones"). The eight slots are Fineliner, Halftone brush, Dither pencil, Wax crayon, Sketch pencil, Slow ink wash, Flat oils 2 and Weeping Wash of Blooms 2 at the sizes tuned for them, the grey starts at 0.216 and the eraser at 24 px. Installs that already have a toolbar keep theirs.
- **Double tap a slider in Brush Lab to reset it** (the owner, 2026-10-24: "double-tapping a slider setting in brush
  studio, can it go back to its default?"). Any slider, Basic or Advanced, goes back to the value a fresh part of its
  choice starts with. The first tap's jump is taken back, so it is one Undo step, and the note says "Reset Size".
- **The Style menu is a real menu** (the owner, 2026-10-24: "here's the tiny menu"). On the Tools page the styles open
  in a menu over the Library, under the Style button, in two or three columns of rows a pen can hit, in the same words
  as every other menu. The chosen style is bold with a bar beside it. Every style shows with nothing to scroll, and the
  menu opens over the button when there is no room under it. Its chips were drawn too small before, and the menu was
  measured without its padding, so it sat squeezed over the top of the cards.
- **More room for the Library** (the owner, 2026-10-24: "maybe reduce the test area a bit"). The Scratch Pad on the
  Tools page is shorter on the Manta upright, so the Library shows three rows of cards, not two.

- **Toolbar backup.** The eight slots (brush name, full share code, size), the eraser's size, the grey and the selected tool are also written to Document/Scribulor/Scribulor toolbar.txt, one tab-separated line each, whenever they change. It is only a backup, the app never reads it, and a rename carries it along.
- **A + on the page bar instead of a blank page at the end** (the owner, 2026-10-24: "instead of always a one white
  page, add a + icon on the top toolbar"; then "the + should be where the auto new blank page used to be, same idea").
  A notebook's pages are now just the pages you drew or added. The + is a tile of the page previews on the top bar, right
  after the last page and before the back cover (where the blank page was), the size and hairline frame of a page, a plus in
  it; when the strip is scrolled away from the end it stays in the strip's last slot. Tapping it adds a new page at the end of
  the notebook, on the notebook's template and page colour, and turns to it. It is not dragged and has no long press. A
  full notebook says "Notebook full, 250 pages".
  - **Changed.** Opening a notebook takes away the blank page the app kept at its end, but only when it is truly blank,
    has no settings of its own and is not the page you are on. A page with anything on it always stays. Turning past the
    last page goes to the back cover. When a timed page runs out on the last page, Next Page adds a new page.
- **Big print brushes keep up with the pen** (the owner, 2026-10-24: "larger brushes, they seem a little sluggish").
  Measured on the Manta at 60, 120 and 240 px for a pen, pencil, charcoal, marker, watercolour, ink wash, gouache, oils,
  pastel and print brush. Only the print brushes with a dot screen (Ben-Day, Rosette, Halftone, Engraving and the rest
  that are not tones) fell behind under the pen, by up to 1.3 s at 240 px. Their first mark under the pen is now black
  dots in the screen's grey, in the panel's own pattern, and their own dots come in behind the pen and at the lift, as
  before. Ben-Day's wait under the pen at 240 px went from 0.94 s (1.35 s at worst) to under 0.05 s (0.13 s). The
  finished page is the same as before, dot for dot.
  - **Found.** Every other big brush already shows its mark in 5 to 12 ms (35 ms for big charcoal and pastel), then
    its texture. Big charcoal, pastel, pencil and bristle brushes at 120 to 240 px show their texture only at the lift,
    0.1 to 0.6 s after it, because the texture can't be drawn as fast as the pen moves. Wet paint at 240 px waits up to
    0.15 s under the pen.

- **Picking a brush sticks** (the owner, 2026-10-24: "won't switch to another brush, kept jumping to liftith with wrong
  size as well"). After a slot was picked, the side column's marks, the size and grey dot and the brush line's name
  could jump back on screen to the brush in use when the app opened, at its size. The pick itself was right, so the pen
  drew with the new brush, but the screen said otherwise. Android kept its own old drawing of the rows the quick update
  had changed, and the screen took that old drawing back at the next redraw. Each row, tab, dot and name the quick
  update changes is now drawn again by Android too, so both agree.
  - **Changed.** Picking the eraser updates the column as picking a slot does, in one quick update with no blink. The
    size slider is a right triangle, its straight side on the left by its tab. Its tab leaves no trail: a quarter second
    after the lift its strip is cleaned once, gently, with no flash. The page bar's page frames are hairlines, the page
    you are on still marked by its ink frame and the tab under it. A brush's name shows as words alone, the page
    showing through.

- **The size slider no longer blinks** (the owner, 2026-10-24: "size picker is still blinking. make it an outline shape
  instead? why is it redrawing?"). The slider is an ink outline of a triangle, broad at the top, with its tab on its
  left. It is drawn in black and white only, so moving the tab and the lift use the screen's quick black and white
  update, with no grey flash. Picking a size changes the tab and the dot alone: the brush rows and the brush line's
  picture no longer draw again. The lift settles in about 0.14 s, from 0.35 s.
  - **Moved.** The size and grey dot is on top, the size slider on the left under it, the grey strip on the right (the
    owner's sketch). The eraser has a row of its own.
  - **Changed.** The eraser keeps its own size, any size on the slider from 4 px up, whatever the brushes' sizes and
    whichever tool you pick. It snapped to four sizes before, so most of the slider left it as it was. Each brush row
    and the brush line's picture show the brush at its Medium size. The dot shows the size in use.
  - **Tidied.** No lines between the brush rows. Zen Mode's button is an empty circle while off and a filled one while
    on. Every page preview on the page bar has the same frame weight, the page you are on marked by its ink frame and
    its tab. The pen's hover ring is eight small dots on the nib's outline, nothing more. A brush's name shows as words
    alone, with no box.

- **The side column's pickers are fast, with no blink** (the owner, 2026-10-24: "the size thing is still blinking",
  "monopaint's shade selector is TOTALLY fast"). A drag on the grey strip or the size slider moves only its tab, one
  small grey update a move, as MonoPaint's colour bar marker does. On the lift only the tab and the size and grey dot
  change. Picking a slot moves its mark, the size and grey dot and the brush line in two updates, and no longer
  refreshes the whole page a moment later.
  - **Fixed.** A tap or a drag on the grey strip picked nothing (the tab jumped to the pen and stayed), so the dot under
    it kept the old grey. It picks again, and the dot shows the grey in use.
  - **Moved.** The grey strip is on the left, white at the top to black at the foot. The size slider is on the right,
    broad at the top to fine at the foot. The wet indicator is on the brush line, right after the brush's name. The
    Lasso sits beside the Dropper, and Play and Template Visibility share a row.

- **One grey for every brush** (the owner, 2026-10-24: "let's go with the procreate method"). The grey is no longer
  part of a brush. There is one grey in use, and every brush, the stamp, Fill in and Filled Shape paint with it.
  Switching brushes keeps it. Pick it on the side column's grey strip, with the Dropper, or on the grey bar on the
  Tools page and in Brush Lab. Each brush keeps its own size. On the first launch the grey in use is the one the
  selected brush had, so it draws on as before. A notebook's own toolbar and a kit bring their brushes and sizes, not
  greys. Older notebooks, share codes and kits open as before.
  - **Brush pictures show the brush.** Every brush's picture (the side column, the brush line, the Tools page, Brush
    Lab, kits, the library) is drawn at full strength whatever grey is in use. A brush that lays white (White gel pen,
    Crayon Scratch) shows as a light stroke on a dark tile. Every other tile is paper.
  - **Cut.** The four grey squares on the Tools page and in Brush Lab, and the greys in the Sizes window (once Sizes
    and Greys). Brush Lab has the grey bar in their place. Grey Shift's Toolbar greys are still the four greys you
    had. A rub to load paint works on the side column's grey strip only.

- **Refocus: a paper notebook first, a little mischief second** (the owner's keep / tuck / cut marks, 2026-10-24).
  Fewer things on screen; nothing you made is lost: every notebook, brush and setting from before still opens.
  - **Cut.** Copies are no longer made on their own when you leave a notebook or the app: **Export Now** in the menu
    writes the PDF (Documents) and the PNGs (EXPORT) as before ("export only when you need it"). The Finger button in
    the side column. Guides in PDF: the template's lines always print, as on paper. The Hand Hatch tip: brushes that
    used it (Hand hatch, Neat hatch, shared codes) now draw with Hatch. The Tools page's All tab. Lock Settings (Open,
    Locked, Sealed): a locked or sealed notebook opens unlocked, its rules (No Erasing, Brushes, Timed Pages) as they
    were. The menu's Template row (the column's Template Visibility button and the Template screen's chips stay).
    MonoPaint's old settings window and palette editor, which could not be reached.
  - **Moved.**

    | What | Where it is now |
    |---|---|
    | Magnetic Poetry, Mirrors (Copy Strokes, Real Mirror, Kaleidoscope, guides), Stamp | **Play** (the confetti button in the side column), its menu |
    | Spiro, Marbling (Sumi), Mold, Fading Ink, Lemon Juice, Crayon Scratch | Play: each puts that brush in the slot in use |
    | Fling | Play: turns it on or off for the slot in use |
    | Surprise Me, Diary Page (its timed boxes) | Play (Surprise Me is still a choice on the Tools screen too) |
    | Finger choice | The menu's Finger row |
    | Layers | A page's Page Settings (hold the page): Layers row, a chip each to draw on, Add for a new one |
    | Moving a page | Page Settings' Move row (Earlier, Later): holding a page now opens Page Settings at once |
    | See-Through Pages, Random Template (Random From), Tools and Brushes on Bar, Use This Page on the Cover | Notebook settings, **More** |
    | Replay Page | The menu's lower part |
    | New Brush | Brush Lab's Randomize |
    | Style chips | The Tools page's **Style** button (All Styles ▾) |
    | Hatch, Stipple, Filled Shape, Stamp, Spiro, Float, Mold, Fading, Lemon Juice choices | Brush Lab **Advanced** only |
    | A slot's paint dab and its paint gauge | Gone from the column; a dirty slot's long press still offers Clean Brush |

  - **Tidied.** The menu in four groups with a light line between them: the notebook (Settings, Open Notebook, Export
    Now, Rename, Delete, Import PDF), the page (Import Image), Daily Prompt and Replay Page, then Finger and
    Left-Handed. The Tools page's **Tweak in Brush Lab** is a full-width button. Brush Lab's **Anything Goes** is a
    smaller button beside **Randomize**. Holding a page in the page bar opens its Page Settings without waiting for the
    pen to lift.

- **White ink: pens and markers in a light grey cover dark marks** (the owner, 2026-10-24: "white ink. let's allow
  them to use that. so if they select a light shade, it's treated like a light color ink"). A fineliner, brush pen, dip
  pen, ballpoint, gel or fountain pen, or a marker (the Colouring pen, Fill in and Filled shape too), in white now lays
  white over black like a white gel or paint pen, with its own edge and texture. The bar's light grey covers the same
  way and shows its own grey over black. Just past mid grey it eases in, so there is no sudden step. Mid grey, dark grey
  and black draw exactly as before. Washes, watercolour and ink washes still only darken, as real ones can't
  lighten what's under them. A pen line goes on its layer's ink, so it covers that layer's ink and wash and the layers
  below. A marker's fill goes on the wash, under that layer's ink lines, as before. A white line erases and undoes like
  any other line. Vanishing ink, Lemon juice, Mold ink and Sumi stay as they were.
- **Bigger app icon and loading screen book** (the owner's larger drawing). The book fills the icon and is a third of the screen's width on the loading screen, with the name larger under it.
- **Thin lines between the brushes in the side column** (the owner: "if a stroke is white it's impossible to see").
  Each brush now sits in its own cell, so a white pen shows.
- **A size slider in place of the four sizes** (the owner: "I find myself wanting to fine tune the size often").
  Beside the grey strip and built like it: a triangle, fine at the top to broad at the foot, with a small tab on its
  right at the size in use. Drag it, or tap where you want it. Only the tab moves while you drag, so it is quick and
  does not blink (the owner: "the redraw is blinky and slow"). It reaches every size a brush draws, with plenty of room
  for the fine ones. The eraser keeps its four sizes, and its slider snaps to them. Each
  slot keeps its own size, as before, and slots set before keep the size they had.
- **The grey swatches are gone from the side column.** The grey strip is the way to pick a grey there. The Tools page
  and Brush Lab still have the four sizes and four greys, and their long press still opens Sizes and Greys. Picking a
  grey on the strip of a blank page now offers Fill page beside it, as a swatch did.
- **The size and grey in use, as one dot under the slider and the grey strip** (the owner: "put the size selected and
  the current shade below", then "it can be one thing"). The dot is the size the brush draws, up to the room it has,
  in the grey it draws. It changes when you lift the pen.
- **The Dropper** (the owner: "so people can quickly match color"). Beside the wet drop (the owner: "move eyedropper
  to beside wet indicator"). Tap it, then touch the page with the pen: a ring round the pen's tip shows the grey under
  it, with a hole in the middle so you see what it reads (the owner: "the selector is offset"), and lifting sets it as the grey of the brush you were using,
  which draws on at once. It reads the grey you see, every layer included, and never the template's lines. Tap it
  again to put it down without picking.
- **Pulling down the Supernote's tray no longer turns the page.** A finger pulled down from the top edge of the screen
  belongs to the tray. The page bar along the top no longer takes it as a drag through its page pictures, and nothing
  else on the screen acts on it either. A tap on a page picture still goes to that page, and a drag along the bar still
  flips through the pages.
- **No ghost of the Brush Lab after it closes.** Coming back to the drawing screen, or to the Tools page, the whole
  screen is shown again once, so nothing the Brush Lab drew stays behind, even through the Supernote's own refresh.
- **The Lasso works with the eraser selected.** Taking up the Lasso while the eraser was the tool in use left the pen
  erasing instead of drawing a loop. Now the pen draws the loop whichever tool was in use. The pen's eraser end still
  erases.
- **New app icon** (the owner's drawing: a hand-drawn black book with an eye on the cover and a ribbon bookmark). It is the launcher icon and shows on the loading screen above the name.
- **Each notebook's PDF is in Documents** (the owner: "a cool pdf that lives in the documents section and might be
  searchable"). When you leave a notebook, its PDF is written to Document/Inkwash, named for the notebook, so it shows
  in the Supernote's Documents. The grey PNGs stay in EXPORT. Search finds the notebook's title, page numbers, dates,
  the template, Daily Prompt cards and the words of fridge magnets, each on its page where the magnet sits.
  Handwriting is not searched. The table of contents lists the cover, then each day with its pages, and pages are
  labelled Cover, 1, 2 and on. Each page opens at its real size, as it looks on the tablet. Pages with only a few greys
  are stored smaller, with no change to how they look. Leaving a long notebook redraws only the pages you changed, and
  a notebook you didn't change isn't written again.
- **Your handwriting on a notebook's PDF is kept.** The back cover is no longer in the PDF, so page numbers stay put as
  pages are added. If you write on the PDF in Documents and later delete or move a page in the notebook, the PDF you
  wrote on is left as it is and the notebook's PDF continues as a new file, "Title 2". Renaming a notebook renames its
  PDF along with your handwriting. Deleting a notebook leaves its PDF if you wrote on it. A file Inkwash didn't write
  is never replaced.
- **Inkwash is now Scribulor.** The launcher, messages and file names say Scribulor. On its first launch it moves your
  folders over: Document/Inkwash to Document/Scribulor, EXPORT/Inkwash to EXPORT/Scribulor, Pictures likewise, and
  "Inkwash brushes.txt", "Inkwash prompts.txt", the import files and the stamps folder take the new name. Nothing is
  overwritten (a clash keeps both). "*.inkwash.txt" brush files still import. The PNG copies in EXPORT are moved, so
  Supernote's cloud sync sees them as new files under EXPORT/Scribulor. Same app underneath: an update over Inkwash,
  your preferences and drawings kept.
- **A loading screen, and a much quicker start.** The app shows its icon and name on paper the moment it's opened,
  until your page is ready, then the drawing screen in one change. Starting no longer builds the print shadings'
  tables (Stipple, Woodcut, Scribble, Hatching, Ben-Day) before the first page shows (up to 24 s on the Manta in a
  build that is not precompiled); each is made the first time a brush uses it. The toolbar's brushes are also
  readied in the background once the page shows, so the first stroke doesn't wait.

- **Lasso: cut and paste** (the owner: "cut/paste", MonoPaint's Lasso, merged with its other new fixes). The Lasso
  sits in the side column's fourth row. Draw a loop round part of the page and it lifts off as a piece you can move,
  resize (its shape kept) and turn, settling on quarter turns. Wash, ink and masking fluid come along, and the piece
  erases just as it did. Tap off the piece or pick another tool to put it down, or Undo to put it back. With a piece
  up, tap the Lasso for Copy (leave a copy, keep moving another) or Delete. Turn the page and the piece comes with
  you as a copy, so you can paste it on another page or layer. The page you left is unchanged.
- **A finger closes the menus a finger opened** (MonoPaint's fix). A palm never closes one, and a menu the pen opened
  still ignores a resting hand.
- **The menus' fast display connection is reused** (MonoPaint's fix): the colour marker and similar feedback no longer
  open a new connection every frame.

- **Spiro rings you pull out to size** (the owner: "could it be possible to set the size of the circle at the
  beginning somehow? or is it just fixed to the brush size"). In Ring, put the pen down where the centre goes and
  pull outward. A dashed grey ring follows the pen, and when you start going round, that is the ring the figure is
  drawn in. A tap still draws the whole figure at the brush's own size. Line is unchanged.
- **Left-Handed in the menu.** The drawing screen's menu has a **Left-Handed** row, marked while on. A tap moves the
  toolbar to the right side at once, with Zen Mode's button, the brush line, the page bar and the menus mirrored to
  match. Tap it again to go back.
- **Template Visibility on the toolbar** (the owner: "if there is a template, a way to adjust its visibility quickly on
  the toolbar"). A new fourth row of tool buttons has an eye button while the page has a template. A tap steps the
  guides Full, Half, Faint, Hidden and back to Full, the eye showing which. A long press opens the four as a menu. It is
  the same setting as the menu's Template row and the Template screen's chips. The row's other cell is kept for the
  Lasso. On the Nomad in landscape the tool buttons are a little shorter so the row fits.
- **A dirty brush shows the paint it holds.** A toolbar brush that keeps paint (like Dirty brush) shows a small square
  of that paint's grey at the end of its stroke while it holds some. A long press on it then offers **Clean Brush**
  (or **Tools**, as before). The smudge tool's dirty brush icon now fills its head with the grey it holds, not a fixed
  mid grey.
- **New notebooks get fantasy titles** (the owner: "i love the brush naming system and want to add more d&d style
  whimsy to the app"). Instead of "Oct 23 regal-yam notebook", a new notebook is called something like "Tome of the
  Damp Wanderer", "The Ledger of Gloomquill", "Codex of Small Weathers" or "Folio the Unerasing", in the same spirit
  as brush names. The template picks the book: a Lined notebook becomes a Ledger or Journal, Grid a Codex or Atlas,
  Music a Songbook, Storyboard a Saga. The date is no longer in the name on the shelf; the cover still shows it, and
  the notebook's settings still say "Made Oct 23, 2026". Rename offers three more titles. Notebooks you already have
  keep their names. The word lists are in `names/traits.txt` ([tome] and [title]).
- **Slow brushes are faster** (the owner, 2026-10-23: speed up what is still slow while drawing). Measured on the Manta.
  A Bleed marker fill of a big box (3 million pixels) is laid in 3.6 s instead of 6.1, and a whole page settles in 4.2 s
  instead of 8.0. The Blend stump at XL shows its smear 0.6 s after the pen lifts instead of 1.0, and keeps up better at
  L (the pen's slowest waits 22 ms instead of 33). The Water brush lets what it lifted run out sooner: 0.5 s after
  lift instead of 0.9 at M, 1.0 instead of 1.5 at L and 1.3 instead of 2.0 at XL. The Wash comb at L and XL shows its
  finished stroke in 0.4 and 0.6 s instead of 0.8 and 1.3, and the Dirty brush at L and XL in 0.45 and 0.6 s instead of
  0.55 and 0.7. Every stroke and fill looks exactly as before. The work is shared over the tablet's four cores where
  it was done on one (the wet fill's line softening into its wash, Clear Water's run-out, the stump's dab, the comb's
  tines), and pixels a smudge didn't change aren't worked out again.
- **Tilt Shading: a lighter, softer side, for charcoal and pastels too** (the owner: "on an angled pencil the shade is
  usually a little lighter than a straight mark with the pencil. and it's a bit more diffused... for charcoal etc as
  well too"). A pencil laid low now shades at about two thirds of the darkness of an upright line at the same pressure
  (it was often a third or less, and light pressure on smooth paper could vanish), and the shading is more diffused:
  the side rides the paper's peaks and skips its valleys, its edges feather out and faint streaks run along it. The
  charcoal sticks (Messy stick, Compressed, Crisp charcoal, Soft charcoal, Dark stick, Charcoal dust) and the pastels
  (Chisel, Soft, Crumpled, Crisp and Side pastel) now shade the same way: held upright or at the usual 30 to 35 degrees
  they draw their crisp line, and laid lower they lay a broad, lighter, grainier band. The stumps and crayons are
  unchanged. Pencils at 16 px and up draw upright as they did before Tilt Shading (live, at full resolution).

- **Brush Lab: an Anything Goes button** (the owner: "the way to generate anything goes with any parameters randomly is
  missing"). The foot of Brush Lab is two equal black buttons: **Randomize** (dice, a new brush in the same style) and
  **Anything Goes** (shuffle, a new brush in any style with any parameters, even when the brush is one already). Both
  keep what is locked, and Undo takes either back in one step. Start from still has Anything goes.

- **Fix: finger erasing no longer comes undone when the pen comes near.** The Supernote cancels every touch as the pen
  nears the screen, and a cancelled finger stroke was taken back, so what you had just erased came back. Now a finger
  stroke that has gone on for 0.4 s or more is kept, and only a shorter one, like a palm brushing the page, is taken
  back.

- **Filled shapes** (the owner: "draw a filled shape, could we do that possibly?"). Any brush can draw them. In Brush
  Lab, set its Smudge to **Filled Shape**: draw an outline, and when the pen lifts the outline closes, end to start, and
  the shape inside fills with the same paint, grey and paper (ink, wash, crayon, paint and the rest). Loops that cross
  fill every part, and so does a loop drawn round twice. A dot or a short line stays a plain mark. Outline and fill
  undo together in one step. Mirrors fill their copies too. A big shape shows flat grey at once, then its texture. A
  new Basic tool, **Filled shape** (a flat marker wash, "fills outlines"), sits under Marker beside Fill in.
- **Filled shapes are one tone at every grey** (the owner: "it can only be black? otherwise the color is a bit diff for
  the stroke vs the fill"). The fill now goes on the outline's own layer and stops at the outline's grey where the two
  meet, so a grey shape no longer has a darker ring inside its outline. Crayon and charcoal fill as their outline lays,
  with the same paper showing through.
- **Page Settings** (the owner: "long press on a page will bring up a panel to adjust the settings for that page").
  Holding a page's preview on the page bar, in a notebook's strip or in the Pages grid opens a screen of that page's
  settings, laid out like the notebook's settings list: **Template** (its own, or "Notebook's (Lined)". The Template
  screen names the notebook's own template that way, the choice that puts the page back), **Page Colour** (Notebook's
  or one of the six greys, and an older grey stays as it is), **Show Through** (Solid, Half, Faint, Hidden, and Ghost under
  Page Before, only while See-Through Pages is on and the page has no layers), and **No Erasing** and **Timed Pages**
  for this page alone. Each is the notebook's by default, or Off or On for the page (On for a timed page is the
  notebook's time, else 5 min). A locked notebook keeps them as they are. Under them, as plain words: Duplicate, Move
  Up, Move Down, Use on Cover, Replay, Clear and Delete, each doing what it does elsewhere (Duplicate, Replay and Clear
  turn to the page first, and Delete still asks). ‹ Back goes back to the page. It replaces the small options menu a hold
  used to open (and the screen reader's "Page Options" is now "Page Settings"). White rows with thin black lines
  between them, as e-ink shows greys poorly. Notebooks without a page's own rules open and save byte for byte as before.
- **A page's own time is a slider** (the owner: "timed page ... it only has 5 minutes displayed. ideally it should be
  a slider"). In Page Settings, Timed Pages is still Notebook's, Off or On. With On, a slider under it sets that page's
  time, from 30 s to 60 min in the same steps as the notebook's Timed Pages, the time in words beside it ("12 min").
  The page's clock uses it. Until it is moved, the page's time is the notebook's, else 5 min. Notebooks with no page
  time of their own open and save byte for byte as before.
- **Layers start as one icon** (the owner: "layers is confusing... let's just have it as a layers icon to start, then
  when there are layers, show the pages... remove the plus icon"). A page with one layer shows only a layers icon in
  the side bar. Tap it for New Layer Above, New Layer Below or Duplicate. Once the page has layers, their thumbnails show
  instead. Tap one to draw on it, hold one for its menu: New Layer Above, New Layer Below, Duplicate, Merge Down, Delete
  and how it shows (Solid, Half, Faint, Hidden). The + is gone. Back to one layer, the icon comes back.
- **Page Settings keeps only Delete at the foot** (the owner: "you can remove some of the other stuff on the bottom..
  duplicate move up etc."). The others are where they always were: Duplicate in the layers menu, moving a page by
  dragging its preview on the page bar, Use This Page on the Cover in the menu, Clear page by holding the eraser, and
  Replay Page, now in the menu.
- **Side lines instead of underlines on the drawing screen's side bar** (the owner: "it should be a side line, not an
  underline. too hard to differentiate a solid line from a stroke"). The brush slot in use has a thick black line down
  its right side, its row's full height. The tool in use, the layer you draw on, and Magnetic Poetry and Mirrors while
  they're on have the same line. The size in use has a thin line at its left again, as before, and the swatch in use one
  on its outer side. The Tools page and Brush Lab keep their underline.
- **More room between the brushes.** Each brush row is a few pixels taller and its stroke has its full size back.
- **The shade boxes line up with the grey strip.** The strip is as wide as a box, and the boxes under it share its
  edges. Its marker sits to the right of it instead of narrowing it.
- **The eraser is beside the smudge tool.** Under them are the stamp tool and Finger, then **Magnetic Poetry** and
  **Mirrors**, each its own button now. The Fun button is gone. Magnetic Poetry opens and closes the word bin, Mirrors
  opens the mirrors' list, and each shows its side line while it's on.
- **Menu is level with the page previews**, so Menu and the page bar read as one row along the top. A Timed Pages
  countdown sits under undo and redo.
- **The brush line starts a little further from Zen's circle.**
- **Zen's circle is dotted while Zen Mode is off and solid while it's on.**
- **The wet paint drop no longer looks on all the time.** While nothing is wet its outline is dashed. It is half full
  while paint moves and full while paint rests wet.
- **No LAYERS label** over the layers. The + and the layers stay.
- **Smudges show their effect under the pen** (the owner: "dirty brush and blend stump and the rest .. the preview is
  very slow", "takes a second to show the effect"). The Dirty brush, Water brush, Damp tissue and Wash comb at Small
  and Medium, the Stump at every size and the Blend stump up to Large now smear live, right under the pen, on the page
  and the Scratch pad. Before, they showed a light dotted trace and the smear only appeared when the pen lifted. The
  bigger ones still draw quick, but the smear itself now appears under and behind the pen as it is worked out, with
  no dotted trace. Oil paint at Medium and up shows its smear behind the pen. The Blend stump and Wash comb also work
  faster on the Manta (Blend stump Large 44 ms to 7 ms behind the pen, Wash comb Large 168 ms to 46 ms). Finished
  strokes are the same as before. The Water brush still lets what it lifted run out when the pen lifts, and the Blend
  stump's softening of the wash under ink still shows at lift.

- **Thin black lines instead of grey on the Tools page and in Brush Lab** (the owner: "change to very thin line
  separators in tools view from grey. eink grey doesn't quite work the same way. also in brush lab"). The grey bands,
  card names, part cards and chips' backgrounds are white now, and thin black lines frame the groups and part the
  cards. A card's name sits under a thin line, apart from its drawing. The space between the toolbar slots and the
  Library stays.
- **Tweak in Brush Lab is in the middle** (the owner: "in tools move Tweak button to middle"). It sits centred under
  the sizes and greys in This Brush, with Back to on its left and Cut Stamp or Load Kit on its right.
- **Tap another part to switch its choices** (the owner: "if medium picker is open and i tap paper, open the paper
  panel"). In Brush Lab, while a part's choices are open, tapping another part opens that part's choices in one step.
  Tapping the same part again still closes them. The old panel's patch is cleaned first, so it leaves no ghost.
- **Randomize is a black button** (the owner: "make RANDOMIZE a black button, align dice with text"). The dice sit
  right beside the word, centred with it.
- **Template Visibility is on the Template screen** (the owner: "included with template in the notebook settings, not a separate thing"). The settings list no longer has a Template Guides row. The Template screen has **Template Visibility**, a row of Full, Half, Faint and Hidden above the templates (not shown while the template is Blank), and the Template row on the list says "Lined · Faint" when it isn't Full. The file menu's note now says "Template faint". Notebooks open and save as before.
- **The page bar is at the top now** (the owner: "moving the pages bar to the top"). The bar with the Notebook button,
  the page previews, ‹ page / count ›, the lock and Zoom runs along the top of the page, from the side column across.
  Everything on it works as before. The tab under the page you're on now sits at the bar's foot, right above the page.
  Holding a preview opens its options below it, over the page.
- **The brush line is along the bottom, beside Zen** (the owner: "keep the zen in the bottom left ... put the selected
  brush indicator to the right along the bottom"). Zen's circle sits in the bottom corner, centred under the side
  column. The brush line starts just right of it, on one line from the left. Only the brush's mark and words open
  Brush Lab, so a palm resting at the bottom right opens nothing. Zen's circle is as far from the bottom edge as from the side edge, and the brush line sits level with it.
  The page loses only the room the line needs.
- **A tidier side bar** (the owner: "some things are a bit off with it.. layers section is a bit janky"). Everything in
  it lines up on one centre line with the same margin on both sides. The sizes, the grey strip and the four swatches
  form one even grid. The size and swatch you use have a short black line under them. The eraser, smudge or stamp tool
  in use is underlined, like a brush slot. The spaces between groups are even, and there are no black divider lines.
  The smudge tool shows a blending stump for blurring brushes, so the drop is only the wet paint button now.
- **Layers are clearer.** The layers group has a LAYERS label and one + that adds a layer above the one you draw on.
  Every layer has a light grey edge, and the one you draw on has a thick black line under it. To add a layer below,
  tap the underlined one and pick New Layer Below.

- **A calmer look, with fewer black lines** (the design pass, 2026-10-23: "rely on shade difference to show contrast
  mostly"). Boxes, cards and panels are told apart by a light grey step now, not a black frame. Section headings are
  small grey capitals with no rule under them. Only the main button of a screen is black, like Save in Brush Lab.
  The other buttons are plain words. Brush cards show their stroke on white over their name on light grey, and the one
  in use has a short black tab under it.
- **The toolbar slot in use is underlined** (the owner: "the selected toolbar slot is not clear... too subtle", then
  "some brushes are inverted, so in addition to the inverted look of the brush we need to determine another way to
  indicate that it is active... underline?"). A thick black line runs under the slot in use, across its whole width,
  with a little space before the next slot, so it never looks like the next slot's. The brush's stroke shows as it is.
  This is the same on the side bar, the Tools page and in Brush Lab. The tray strokes on the side bar are a little
  shorter to make room for it. On the Tools page the slot no longer blinks.
- **Every menu looks the same, and none should leave a ghost** (the owner: "i am worried they will leave e-ink ghosts
  when they open and close. this happens with the eraser menu"). Menu, Finger, Fun, the eraser's hold, the smudge,
  stamp and layer menus, page options, Sizes and Greys and Brush Lab's part choices are a white panel with a thin black
  edge and 12 px text, with no grey in them. The chosen row is bold with a black bar at its right. The eraser's kinds
  are a short list now. When a menu closes, just the patch it covered is cleaned with a fresh refresh.
- **A notebook's settings are a plain list** (the owner: "replace it with the list"). The library card with its stamps
  is gone. The Settings page shows the notebook's name with Rename under it, then one row for each setting with what
  it is set to: Template, Page Colour, See-Through Pages, Tools, No Erasing, Timed Pages and Cover, plus Template Guides
  and Random From when they apply. Page Colour is six swatches right in its row instead of a slider on the Template
  screen; a notebook with one of the slider's other greys keeps it. See-Through Pages and No Erasing switch Off and On
  in the row (a long press on See-Through Pages still offers Nearby Pages and Faint). The screens the rows open show
  their choices as grey tiles with a black tab under the one in use, and long template names wrap onto two lines.
- **Dialogs** are a light grey box with no outline. Cancel is plain words and the action is a black button.
- **The Tools page and Brush Lab, rebuilt to read as one place** (the owner: "give it a proper hierarchy, and link
  things together"). On the Tools page the slots come first under Active in Toolbar, then This Brush with its sizes,
  greys and grey bar and one black button, Tweak in Brush Lab, then a Scratch Pad about a quarter of the page. The
  Library sits on its own grey band below, with a gap so the two never blend. New Brush is on the Library's row, and
  the page count is under the grid. Brush Lab has the same slots on top, showing the same strokes. About This Brush
  holds the name, the words, the sizes and greys and Save to Library. Adjust sits beside the Scratch Pad, and a big
  Randomize is at the bottom. Brush Lab has no library of its own now. A long press on a saved brush in the Tools
  page's library deletes it. Back in Brush Lab reads "Tools" when it came from there, and goes back there.
- **Back to where you started.** A slot you change on these screens shows a small dot under it, and a "Back to
  Fineliner" button puts back the brush it had when you opened the screen, at its size and grey. Undo takes it back
  too.
- **Nothing unsaved is lost.** When a brush you never saved leaves its slot, it is kept in Recent, the latest 30, and
  a short note says so. A brush made with New Brush now stays in its slot when you leave Brush Lab without saving.
- **Fixed:** Brush Lab's style table showed "Watercolor" and now says "Watercolour". On the Nomad, Brush Lab's slot
  sizes no longer lose their first digit (".1mm"). A notebook setting in use no longer has an extra check
  mark.

- **Every tool shows its nib while the pen hovers** (the owner, 2026-10-23: "possible to do a similar hover dotted
  line preview for all tools?"). Hold the pen just above the page and a dotted ring shows how wide the selected brush
  will draw there with a normal touch. A chisel or flat brush shows its oval, turned to its angle. When pressing harder
  makes a brush much wider, a fainter ring of dots shows the widest it can go. The ring grows and shrinks with zoom.
  It goes away the moment the pen touches the page. On very fine pens it stays a small ring around the tip, so it
  doesn't hide your line.
- **The eraser's ring now matches what it erases.** It used to show the eraser's largest size, which it only reaches
  when you press hard or lean the pen. Now the dashed ring shows what a normal touch erases, and the fainter dotted
  ring shows the largest size.
- **The grey strip is quicker to pick from** (the owner's device notes, 2026-10-23). The grey strip beside the sizes
  now picks like MonoPaint's grey bar. Tap it, or drag along it, and the grey where the pen lifts is the one you get.
  While the pen is down the square beside the strip follows it, so you see which grey you're on before you lift. A
  drag used to count as a rub and pick nothing at all. A rub still loads a brush that takes Paint Load, after picking
  its grey. The strip has lost its black outline, and the square beside it marks the grey in use.
- **Save in Brush Lab says it saved.** After a tap on Save, the button says "Saved to library" for a few seconds, or
  "Already saved" when the brush was in the library already.
- **The Dirty brush says what it smears.** Its words on the Tools page now say that it smears paint but not pen or
  pencil lines, and that it carries that paint into the next stroke.
- **The paint bucket is gone; Fill in and Page Colour do its work** (the owner, 2026-10-23: "maybe this app doesn't
  use a bucket, just a brush with fill in the lines" and "a notebook setting: default page color?"). The bucket's
  button beside the smudge tool is gone, and its cell stays empty. A new Basic tool, **Fill in** (under Marker, beside
  Colouring pen), is a flat wash that stays in the ink lines. Scribble inside a closed shape and it fills the shape to
  its lines. Started on open paper or between letters it is a plain wash where the pen goes, never a refusal and never
  the whole page. A new kit, **Colouring In**, puts Steady line and Fill in on the bar together.
  A notebook's **Page Colour**, a slider over the Template screen's choices on its library card, sets the paper new
  pages start on, White to Black. Pages already drawn on keep their paper, blank ones take the new colour, so a new
  notebook's first page does too. The eraser rubs back to the page's colour, and previews, PNG and PDF copies show it.
  Light paint over a dark page works as it does on a shaded page. Old notebooks open unchanged, on white.
- **Wet strokes blend where they overlap** (the owner, 2026-10-23: "watercolor overlapping wet strokes don't blend"
  and "when they are wet, the liquid combines and the pigments blend in the water"). A watercolour or ink wash stroke
  laid over one that is still wet now joins its water for longer, about three quarters of its drying time instead of
  half. Its fresh water soaks into the older wash around it, so the pigments even out across where the two meet. You
  no longer get a hard dark line along the new stroke's sides, and you don't need the Dirty brush to smooth it. Where
  both strokes lie it is still darker, as more pigment is there. A stroke laid as the wash is nearly dry still blooms,
  and one over a dried wash still glazes with its edges. Wet paint that grows too big for memory no longer dries all
  at once before your next stroke. The strokes painted longest ago dry first, so the one you just painted stays wet.

- **One huge wet stroke no longer runs out of memory** (the 2026-10-22 stability check, #2). A single wet stroke now
  keeps at most about a third of the page wet at once. In a stroke that big, the part you painted longest ago dries
  where it is while the brush carries on, so its water stops spreading there. Painting back over that dried part
  glazes it a little darker, like a second stroke would. If all the wet paint on the page grows too big for memory
  during a stroke, the oldest strokes dry first and the recent ones stay wet. Ordinary strokes look and flow as
  before. A Large Soft wash scribbled over most of the page now takes half the time.

- **More memory to work with.** Inkwash now asks Android for the large memory allowance, 512 MB on the Manta instead
  of 192. Big wet washes, fills and busy pages have more than twice the room before they run short. Wet paint dries
  itself later, and Undo keeps its full 32 MB again.

- **Fill finds its area faster, and a big fill shows at once** (the owner chose the fill-speed plan, 2026-10-23, after
  fills took 4 to 19 seconds on the Manta). A fill on a page with no lines on it, like a held whole-page fill, no longer
  searches for its area. On the Manta that took 0.8 s and now takes 0.06 s. Finding an area shut in by lines shares its
  work over the tablet's four cores, so a big box's area takes 0.5 s instead of 0.75 s. A fill of a big area now shows
  flat in its grey as soon as its area is found, and the real fill, with its texture, takes its place once it's laid.
  That helps most with wet media: a Bleed marker fill of a big box still takes about 6 seconds to settle on the Manta,
  where pens and pastels take about 1. Fills look exactly as before.
- **Less memory sits idle while you draw** (the owner's pick from the 2026-10-22 stability check, §3.4). Going back
  to drawing from the shelf or a notebook's pages now lets go of the shelf's cover sheets (up to 40 MB), so turning to
  a cover can take a moment while its sheet is made again. Undo keeps an eighth of the tablet's memory, 24 MB on the
  Manta instead of 32, so a heavy page keeps a few fewer Undo steps (about 11 big strokes instead of 15). A Clear page
  still stays undoable after more strokes, as before. Undo now also counts a layer's print plane, which it used to
  leave out. Copies, saves, notebook pictures, ghosts and exports now work on one whole page at a time instead of all
  at once right after you leave a notebook. The shelf no longer keeps an old picture of a notebook's cover each time
  the notebook changes. In a desktop test with the Manta's memory, drawing big strokes after the shelf kept 76 MB
  instead of 117.
- **Fill takes much less memory at once.** A fill is now laid, settled and drawn a strip of rows at a time down the
  page, and each strip's working copies are let go as soon as it's drawn. Finding the area also reads the lines a few
  rows at a time. A whole-page fill with a Quick marker (Bleed marker) needed about 145 MB of memory on this computer
  and now needs about 55. Pens, inks and prints need about 20 MB instead of 40 to 70. With the rest of the app's memory
  in use, that marker's page fill ran out every time here and now fills. Fills look exactly as before. Slow, Mold,
  wax and Double Print fills still lay the whole area at once.
- **Fill is lighter on memory, and a whole page needs a held pen** (the owner, 2026-10-22: "the fill bucket.. it
  makes things quite slow sometimes, and undo and redo also gets really slow" and "i also accidentally filled a blank
  page"). A fill over blank paper now keeps half as much (or nearly nothing, for a flat ink) for Undo, so a big fill no
  longer pushes the rest of the page's Undo out, and it needs about a quarter less memory while it's laid. A tap that
  would fill nearly the whole page says "Hold to fill the whole page": hold the pen down half a second and lift to fill
  it. Areas shut in by lines still fill at a tap. If the tablet is short of memory for a big fill, older Undo steps on
  the page go first; if that's still not enough it says "Not enough memory to fill" instead of crashing. Fill uses the
  selected brush's own grey, as before.
- **Light greys go on light over dark marks** (the owner, 2026-10-22: "light colors on opaque materials.. crayon,
  oils.. they should go on light. like white or grey paint", "pencil and charcoal in lighter colors should do the
  same", "also the print ones"). A white or light grey stroke of a crayon, pastel, pencil or charcoal stick now lays
  its grey over darker marks, with its tooth and grain, like a white pencil or white chalk on a dark ground; before,
  these could only darken. A light Print brush (Ben-Day, Halftone, Rosette, Engraving, Dither, Screentone) prints
  crisp white over dark, the opposite of its dots, so a light grey print over black looks like that grey; its black
  dots still add up over other prints as before. Light Wet gouache and oils laid into wet dark paint cover it more
  than they mix (white gouache over wet black now ends near white, not mid grey). Black, any grey over lighter marks,
  and ink pens, markers, watercolour and ink washes behave exactly as before: transparent media only darken.
- **Marbling no longer crashes, and stays light however many rings float** (the owner, 2026-10-22, after crashes with
  the Sumi kit: "is there a way to make it less processor intensive, i think it gets overloaded... if it's too complex
  we can retire the sumi stuff"). A Sumi or Clear water stroke joining the rings could start the print being worked out
  in the background while the stroke changed the rings under it: that crashed the app, and when it didn't, the print
  could leave out the strokes made after it. Now nothing in the background reads the rings while a stroke changes them.
  A bath now holds at most about 50,000 outline points or a box of about a million pixels (a fifth of the page): past
  that it prints when the pen lifts, and the next drop starts a fresh bath on top. A drag through the rings moves them
  every few steps instead of every pen sample. In a desktop test of 600 drops in one spot, the slowest tap went from
  0.2 s to 10 ms, a Sumi drag from 7 ms to 0.1 ms a pen sample, and a held drop from 0.25 s to 11 ms a second (the
  Manta is about 20 times slower).

- **Tool kits: one tap puts a whole way of working on the bar** (the owner, 2026-10-22: "the workflow for sumi and the
  marbling stuff.... i wonder if that could be clarified somehow... maybe sumi tools load several tools in your toolbar
  or something by default", then "yes to kits ! nice idea"). On the Tools page, picking a brush that belongs to a kit
  (Sumi, say) shows **Load Kit** beside This Brush, and the library has a **Kits** tab. A kit's sheet says what it is
  for, lists its steps in order and where they go, and **Load Kit** fills the bar's first slots with them, the first
  selected. Picking a kit's slot on the drawing screen says its step ("2 Drop water") instead of its name. One Undo
  (on the Tools page, or on the drawing screen once back) takes the whole kit back. Three kits to start:
  **Marbling** (1 Drop ink: Sumi, 2 Drop water: Clear water, 3 Comb rings: Comb), **Sketch and Ink** (Sketch pencil,
  Steady line, and the eraser set to Dry Media so it lifts the pencil and leaves the ink) and **Watercolour with
  Masking** (Masking fluid, Soft wash, Damp tissue, and the eraser on Everything to rub the mask off). In a notebook
  that shows fewer brushes on the bar, loading a kit shows enough for it (a locked one says "Bar holds 2 brushes"
  instead); a notebook whose toolbar can't change says why, as it does for a single pick. Kits live in
  `assets/brushes/kits.txt`, easy to edit.
- **Marbling says when it's being used out of order**: Clear water or the Comb with no ink floating notes "No ink
  floating", and drawing with any other brush while rings float notes "Rings printed" (it always printed them).
  Sumi, Clear water and Comb's own words now say the order: sumi first, clear water between its drops, the comb after.

- **Pencils shade when you tilt them** (the owner, 2026-10-22: "all of the pencil presets... let's use the tilt so
  when the pencil is tilted it shades like an angled pencil"). The 2H, HB, B, 2B, 5B and Sketch pencils draw a crisp
  line held upright or at a normal writing angle; lay the pen lower than about 35 degrees from upright and the line
  turns into the side of the lead: up to six times as wide, lighter, soft at its edges and grainy, catching the paper's
  tooth, fully by about 65 degrees. Control's new **Tilt Shading** sets how much, for any brush with a round, chisel,
  flat or filbert line.
- **Charcoal and pastels look, while you draw, as they'll end up** (the owner, 2026-10-22: "charcoal preview is also
  darker than the resulting mark.."). A big charcoal or pastel stroke's quick picture under the pen was one flat dark
  band at any pressure, up to three and a half times as dark as the finished stroke when drawn lightly. It now shows the
  stick's own grain and the paper's tooth, lighter with a light hand and darker pressed, within about a fifth of the
  finished stroke's darkness and mostly on the same specks. The finished stroke is exactly as before.
- **New brushes get D&D-flavoured names that say what they are** (the owner, 2026-10-22: "maybe the name gen can be
  a bit more reflective of what the brush is as well"; "like d&d weapon names perhaps chiseling ink of smudge";
  "generally D&D inspired names ... suffixes like -dor, something the something, -lax"; "yes pref for this structure
  ... because i love the names"). A rolled brush is now named from its most telling parts: about three in ten like a
  weapon ("Weeping Hake of Blooms", "Keen Chisel of Soot"), a third like a hero ("Hakedor the Thick", "Inklax the
  Forking"), the rest a fun word, a describer and its part ("sloppy inky drag", "gruff scratchy nib"), some with a
  mutant word ("dustchisel", "chiselthar"). Words ending in -y or -ish are welcome ("i like the y or ish words"). A
  variation keeps its describer and part, its epithet or its weapon under a new first word. All the words are in
  `names/traits.txt` for editing. Brushes you saved and the Basic tools keep their names; new drawings are still
  adjective-produce.
- **Long brush names wrap to two lines** (the owner: "maybe we extend the cards to accomodate... lol.. because i love
  the names"): on the Tools page and library cards, in Brush Lab's name bar and in the slot name that flashes beside
  the column; only a name past two lines is cut with …. The brush line under the page stays one line.
- **Brush line back over the page bar, reaching the left edge; Zen Mode under it** (the owner, 2026-10-22: "the brush
  line .. that's not what i meant. i meant the same vertical position, just shifted over to the left. and the circle can
  go below it. it can be the same size circle as the menu on top"). The brush line sits between the page and the page
  bar again, now running the screen's whole width under the column's foot; the Zen Mode circle is in the corner below
  it, beside the page bar, at Menu's icon size, in the same spot in Zen Mode. The page is the same size as before. On
  the Nomad in landscape the column's icon rows are 35 dp instead of 40 to make room (icons unchanged).
- **Menu is Phosphor list, no line under it; no lines between the layer add buttons** (the owner, 2026-10-22: "the
  menu on top should be phosphor list and not have a line underneath.. lines dividing layer add buttons: remove").
- **The selected swatch's bar touches the swatch** (the owner, 2026-10-22: "there's a thin line between the swatch
  selected line indicator and the swatch itself, and is the line the same thickness as the line size line? it should
  be"): no paper gap between the bar and the swatch; the bar is the size picker's, 3 dp of ink.
- **Ink wash strokes mix where they overlap** (the owner, 2026-10-22: "less dark where they overlap, especially when
  wet"). Grey wash, Flat wash, Slow ink wash, Line and wash and Wet ink now stay wet until you dry them, like the
  watercolour washes: strokes painted into a wet wash blend into one instead of building a darker band, and they dry
  back softly, feathering a little into the paper. Line and wash and Wet ink keep a crisp pen line under the wash.
  They paint on the wash layer now, so they blend with your other washes. Slow ink wash is a little paler. At large
  sizes their wash stays close to the line (at most a finger's width past it) so the pen keeps up. Brushes you saved
  from the old ones keep drawing as before.
- **Fewer pen lines in the Basic tools' washes** (the owner, 2026-10-22: "set pen line to 0 in existing presets unless
  it's the unique element of the brush"). Grey wash, Flat wash and Slow ink wash (Ink wash) no longer draw a crisp
  pen line through their wash: Pen Line is 0. Line and wash, Wet ink and the wet pens and markers keep theirs, the line
  being what they are; dry pens are unchanged (their mark is the line). Brushes you saved keep drawing as before.
- **The eraser's long-press menu is smaller and lighter** (the owner, 2026-10-22: "long press eraser menu, make it
  smaller and lighter so it doesn't leave ghosts on the screen after"): Everything, Pen, Paint, Dry Media and Clear
  page are plain words in one 32 dp row inside a fine grey outline, the kind in use marked by a small dot and bold,
  no black-filled button. The page shade offer and the side column's short labels take the same light look.
- **The swatches sit on the grid above them** (the owner, 2026-10-22: "align the swatches so they look good with the
  content above (aligned, following same grid)"): the grey strip now stands exactly over the right column of swatches
  and spans the size squares' height, so both columns share their edges and the gaps between columns and rows match.
- **The brush line runs the whole width, Zen Mode at its end** (the owner, 2026-10-22: "for the name on the bottom
  bar.. can the bottom bar overlap and it be all the way to the left? means moving the zen mode there"). The line with
  the brush's preview, name and notes now lies along the screen's foot from edge to edge, under the side column and the
  page bar, with the Zen Mode button at its end on the column's side; the column's foot row for it is gone. In Zen Mode
  the button stays exactly where it was. Its icon is Phosphor's circle while off and circle-dashed while on ("for zen
  mode ... not enabled use phosphor circle and when enabled use circle-dashed"). The page keeps its size.
- **Brushes explain themselves** (the owner, 2026-10-22: "right now they are too curt. explain what makes the brush
  unique, what to expect"). Every brush opens with a sentence about what it is and what stands out ("A stick of vine
  charcoal that leaves dust in its wake."), special tools like Damp tissue and Sumi in their own words, then it says how it behaves in plain, complete sentences ("It stays wet until you tap the
  drop to dry it. Tilt the page and it runs downhill."), in Brush Lab's THIS BRUSH box, on the Tools page and to the
  screen reader. A few card words are clearer ("ribbed" for the Ribbed paper, "smudges only", "grows veins").
- **Wet watercolour strokes mix where they overlap** (the owner, 2026-10-22: "for overlapping wet strokes, the color
  should mix a little bit more, especially early on"). A stroke painted into a wash that is still wet now blends into
  it, most of all while the wash is freshest: the overlap reads as one merged wash instead of a darker band, and no
  thin line shows along the second stroke's edge. How washes dry back (dark edges, grain, blooms, tide lines) is
  unchanged.
- **Drips is no longer a Basic tool** ("i don't love the drips... too heavy handed"). Brushes you saved from it keep
  drawing as before, and the Drips dial is still in Brush Lab.
- **Side column polish** (the owner, 2026-10-22):
  - **The droplet is always there** ("is the droplet invisible?"): Phosphor's drop ("for drop icon use phosphor drop")
    under the swatches, its outline while the page is dry, half full while paint still moves, full while wet paint
    rests. It used to show only while wet, leaving a 44 dp blank band, the **gap under the swatches** ("bit of a big gap
    between the swatches and the tools below"); its row is now 36 dp and always holds the drop.
  - **The blow dryer is MonoPaint's own** ("the hair dryer animation isn't the same as monopaint, can you pull that
    pls"): MonoPaint 0.98.3's dryer icon, frames, shake and air, at the size of MonoPaint's droplet button. A tap blows
    it even on a dry page.
  - **The grey strip marks the grey in use with its side dot only** ("no need to have a line in the gradient picker").
  - **Notes sit on the brush line** ("the system update alerts... got cut off by the canvas... small stuff to the
    right"): messages such as "Inkwash hasn't been tested on this firmware yet." show at the right of the line over
    the page bar, in the brush name's size, for 4 seconds, cut with … past two thirds of the line; the canvas never
    covers them. In Zen Mode and off the drawing screen they are the boxed message as before.
  - **A preview of the brush beside its name** ("put a preview of the brush beside the name ... tapping the name or the
    preview there should open the brush studio"): the brush line starts with a small stroke of the slot in use at its
    size and grey; a tap on it or on the name opens Brush Lab on that brush.
  - **Zen Mode's button stays put** ("it should be in the same spot it is when it's active"; "let's use the waves icon
    instead"): waves at the column's foot; in Zen Mode the filled waves sit in exactly the same spot. Menu is alone in
    its row now. On the Manta in landscape the column's items are a little smaller to make room (icons 52 dp).
- **Fill fills open paper** ("i just used the paint bucket on a blank page and it said no closed area.. it should fill
  the page ideally"): a tap on a blank page fills it whole, and open paper reaching the page's edge fills up to the
  edges, not inside shapes drawn on it. One Undo, masking fluid and mirrors as before. Stay in Lines strokes still
  need a closed shape.

- **Brush previews look like a usual stroke** (the owner, 2026-10-22: "the preview marks are much darker than the typical
  line left at normal pressure ... ie. crayon"). Every brush's picture (Tools page cards, toolbar slots, Brush Lab's
  loops and part cards) is now drawn at an everyday pressure, not pressed hard, so pencils, charcoal, crayon, pastels
  and washes look about as dark as what you'll draw: Messy stick's card went from about two and a half times as dark
  as a usual stroke to the same. **Wax crayon** shows its waxy, broken grain at a size where it reads, and **Crayon
  scratch** scrapes a line out of a waxy crayon ground instead of a flat grey. While drawing a big crayon stroke, the
  quick preview under the pen is grainy wax that follows your pressure, not a flat dark band; the finished stroke is
  exactly as before.
- **The hake lands lying along the stroke** (the owner, 2026-10-22: "the hake brush struggles to find its direction
  and often leaves a mark that doesn't match at the beginning before the stroke really gets going"). A hake that follows
  the stroke now waits the first 2 to 3.5 mm until the way you're going has settled, then lays the start square across
  it from where the pen landed: no fan of crooked heads from a wobbly or hooked landing, no stub at a default angle on
  the Wet hakes (Gouache hake, Broad oils), and a tilted pen's lean is part of the very first mark. A short dab lifted
  before then is still laid, along the way it went. The quick preview waits the same few mm. Hakes fixed at an angle are
  unchanged unless they turn with tilt; other tips are unchanged.
- **Watercolour paints wet into wet.** Soft wash, Wet blooms, Backrun, Granulating, Slow bloom, Pooling wash, Tilt
  blend and Sinking grain now lay their paint into the wet canvas, like Wet gouache: strokes painted while the wash is
  still wet merge into one wash instead of showing as separate marks. As it dries back over the next seconds it darkens
  at its edges, the paper's grain comes up, and a stroke laid into a wash that has gone damp blooms (a frilly dark front,
  paler behind). It stays wet until you tap the droplet or leave the page; a stroke after that glazes over the dried
  wash. Same names, tips and papers as before. Brushes you saved from the old ones keep drawing
  as before.

- **A notebook's fixed tools say so on the Tools page** (the owner, 2026-10-22: "selecting an oil brush in the picker
  screen doesn't stick ... it should prevent you from picking a new brush and explain"). In a notebook whose tools are
  picked each page, or locked, the Tools page says "Notebook picks tools each page" (or "Notebook locked", "Notebook
  sealed") where "Tools" was, greys the library and offers **Notebook Settings**, one tap to the card's Tools screen (Lock
  Settings while locked). On the card, **My Toolbar** now reads "The app's toolbar, free choice", **Each Page** "New
  every page, can't change", and the slider under them is **Brushes on Bar** (how many slots show).

- **Mold grows darker and faster**: its veins now bridge to black marks nearby (before, a black line beside a Mold
  stroke was taken for the stroke itself, so the veins stayed at a fifth of its darkness), and its scouts move twice as
  far each tick. Mold ink's Mold Growth is full and its Drying 5.5 s (was 6.6 s). Beside a black line, its darkest vein
  goes from 20% to 70% of the stroke's darkness, shows in 0.8 to 1.8 s (before: 2.6 s, or never at a small size) and is
  done in 5.5 s.
- **Wet brush: darker, by pressure, and it remembers its ink.** Press lightly for a pale wash, hard for near black from
  a full dip; it still runs dry along the stroke. Each tool's Wet brush keeps its ink when the app is closed and opened
  again (tap a grey to dip it fresh).
- **Wet brushes in the test area** (Tools page, Brush Lab): a Wet brush that has run dry there dips itself again, so the
  test page never goes blank; **Clear** dries the wet paint too, and leaving the test area dries it.

- **Print brushes blend with pressure**: Ben-Day's dots now grow smoothly as you press, instead of jumping between a
  few fixed tints ("it doesn't seem to blend between, it seems to jump between shades"). Halftone and Rosette were
  already smooth. The Tools page and Brush Lab show the pressure-shaded print brushes (Ben-Day, Rosette, Halftone,
  Engraving, Wavy-line, Dither) at an everyday pressure, so the card is about as dark as what you'll draw, not darker.
- **The side column feels like MonoPaint's** (the owner, 2026-10-22: "i want users familiar with the monopaint
  interface to feel like they know what everything does in ours"):
  - **Four grey swatches** two by two under the sizes and the grey strip, MonoPaint's palette squares ("the color
    picker is too hard to use.. let's keep the gradient picker and add a spot for the four swatches below"). Tap a
    swatch to use it; hold one to set them in the Sizes and Greys window; rub one to load a brush.
  - **The grey strip picks any grey** for the tool and no longer changes a swatch. It shows only the grey in use; no
    swatch is marked while you draw with a grey of your own.
  - **The wet droplet** moved under the swatches ("move the wet canvas mark below the size and shade picker"), and a
    tap on it now plays **MonoPaint's blow dryer** for a moment ("a brief hair dryer animation which is cute").
  - **Zen Mode's button is always in the column**, beside Menu ("zen mode button, can you place it on the toolbar
    always"): the praying hands' outline; in Zen Mode the filled hands in the bottom-left corner bring the bars back
    ("icon empty vs full"). The menu no longer has a Zen Mode row.
  - **A brush line** over the page bar names the tool in use and its card's words ("maybe a small line above the
    pages area?"); hidden in Zen Mode. A page made before it opens with its foot under the line (pan to see it).
  - **Picking another slot shows its name** beside it for a moment ("when a new brush is selected in the toolbar, it
    shows its name briefly").
  - **No more ghosts after a popout closes** (the eraser's kinds and the other flyouts beside the column): its area is
    blanked and drawn again ("popout menu for erase often leaves a ghost after erasing").

- **Wet brush is a wet ink brush now** (MonoPaint's wet brush pen, on the wet paint): Brush pen's Wet brush lays ink in
  water that stays wet with the rest of the wet paint. The brush holds its ink between strokes: an inky brush lets it
  out as it goes, so strokes get paler until you **tap a grey to dip** it again; a clean brush soaks up wet ink it
  crosses. It lifts dry paint at once, its ink runs through the water and pools downhill on a tilted tablet, and it
  dries when you tap the droplet or leave the page. Each stroke shows under the pen, final. In Brush Lab: Drying's Wet on
  an Ink or Wash medium.
  - The old Wet brush (line and wash) is gone from the Tools page; a slot or library brush made from it keeps it.

- **Eight new papers** in Brush Lab's Paper (the owner's picks from real paper photos): **Cotton** (cold-press
  watercolour), **Bumpy** (rough watercolour), **Unryu** (long mulberry fibres), **Library** (old paper with stray
  fibres), **Kraft**, **Old Buff**, **Blue Fibre** and **Fine Speck**. With fourteen papers the Paper choices turn in two
  pages (‹ 1/2 › in the panel's head, as the library's). Rolls, the dice and variations never pick them, so every
  brush rolls as before; share codes carry them, and older codes read as before. Photos: Unsplash (credits in
  THIRD_PARTY_NOTICES).

- **Wet paint for Gouache and Oils** (MonoPaint's wet canvas, the owner's choice): Gouache and Oils are new Basic tools
  whose paint stays wet. Each stroke shows under the pen as it lands, final, mixes into the wet paint it crosses, softens
  into it for a few seconds after, and runs downhill when the tablet is tilted (Runny gouache). It stays wet through ink,
  pencil and eraser strokes until you tap the droplet at the foot of the bar, or leave the page. Oils drag what they
  cross and get dirtier as they go; **Loaded oils** runs out (rub a grey to load); **Gouache water** lifts wet or dry
  paint and lets it run. One Undo takes back a stroke and its movement. In Brush Lab it is Drying's **Wet**, with **Stays
  Wet** and Runs Downhill.
  - Wet paint mixes only with other paint: dark stays dark on fresh paper. Dried gouache wets again under a new stroke;
    dried oils don't (new oil covers them).
  - The old Gouache and Oils Basic tools are gone from the Tools page; a slot or library brush made from one keeps it.
    A new install's sixth slot starts with Wet gouache.
  - Pastels and Dry brush stay dry, but over wet paint a pastel sinks into it and a dry brush drags it.
  - Wet paint code is MonoPaint's (`WetWatercolor`, MIT, © mpdairy).

- **Every brush erases unless you say otherwise.** Basic tools, new brushes and rolls all start fully erasable; the
  dice, Randomize, variations and Surprise Me never change it. To make a brush permanent, move its **Erasable** dial in
  Brush Lab (Advanced; it has no die now). Brushes already in the toolbar, Recent and the library that came out less
  erasable are set to erase fully, once, at the update (Wax resist stays wax). A share code pasted later loads as
  written, permanent or not. Notebooks' **No Erasing** is unchanged.
- **Zen Mode** (the file menu): the side column and the page bar go and the page gets the whole screen; the tool in
  use keeps drawing. Tap the praying hands in the bottom-left corner (or Back) to bring the bars back. A new launch
  starts with the bars.
- **Template guides: Full, Half, Faint or Hidden.** The file menu's **Template** row steps through them (its eye shows
  which), or tap the eye beside the Template swatch on the library card. The screen only: the PDF still prints the
  guides as **Guides in PDF** says.
- **The eraser on Everything erases everything**, permanent ink and paint too (the eraser, the pen's eraser end and a
  finger). To rub out a pencil sketch under ink, hold the Eraser and pick **Dry Media**: the ink stays. Masking fluid
  still shields the paint under it, and No Erasing still turns the eraser off.
- **Notebooks hold 250 pages** (were 100), still 200 MB at most; an imported PDF takes up to 124 pages (two
  layers each). A notebook of more than 100 pages doesn't open in older builds.
- **Eight new Basic tools** for brush options no preset showed: **Wax crayon** and **Crayon scratch** (Pastels;
  waxy layers that build up, and a crayon that scratches back to white), **Spirograph** and **Vanishing ink**
  (Fineliner; toy loops along the line, and ink that fades away), **Springy brush** (Brush pen; Control's Springy),
  **Lemon juice** (Dip pen; faint until it darkens in), **Colouring pen** (Marker; a wash that stays inside the ink
  lines) and **Damp tissue** (Watercolour; lifts the wash it passes over and lays nothing, so no dark mark on paper).
- **Import Image** (the file menu): a picture from the tablet goes on the page in greys, fitted and centred. Drag it to
  move it, drag its corner square (or pinch) to size it, then **Place** or **Cancel**. It goes on a new layer above
  the one drawn on, and one Undo takes it away, layer and all; with four layers already, or a full notebook, it goes on
  the layer drawn on. White in the picture stays clear.
- **Import PDF** (the file menu): a PDF becomes a new notebook named after it, a page for each of its pages (the first
  49), each page the PDF's page, locked, under a clear layer to draw on. A box shows how far it is, with Cancel; a
  cancelled import leaves no notebook.
- **Export Now** (the file menu): the notebook's PDF and PNG copies written at once, not only on leaving it, then a
  note says where the PDF is.

- **No Erasing means no eraser, no undo.** With the card's NO ERASING stamp inked (its tap now says "No eraser, no
  undo"), nothing drawn can be taken back: the eraser stays off, Undo and Redo show disabled and a two- or three-finger
  tap says "Undo is off", and Clear Page, Clear Layer, Delete Page and Delete (a layer) are refused for anything with
  marks ("Erasing is off"); a blank page or layer can still go. Drawing, white paint and the smudges that move paint
  (Drag, Smear) stay, as do Merge Down and Merge Old Layers (nothing that shows is lost), adding, moving and setting
  pages and Page Transparency. The undo history is kept but unused while it is on. Notebooks with No Erasing on already
  get no undo too.
- **Guides in PDF hides when there are no guides**: the menu's row shows only when the template, or some page's own, isn't
  Blank.
- **Covers are never timed.** Timed Pages leaves the front and back covers alone: no countdown, no lock, no time-up popup.
- **A Diary Page card sets its own page alone.** Starting one gives the page drawn on the Diary Page template, as a hold
  on it in the strip would, instead of changing the whole notebook's template.
- **Brush strokes on the library card.** Each brush on the card's numbered lines shows its stroke at the line's end,
  as the Tools page's library cards draw it; the names show at once and the strokes fill in together, in one update.
- **Hand Hatch sprays its strokes.** Like Stipple's spray, but short hatch lines: they scatter round the pen, more
  with pressure, a slower stroke, going over again or holding the pen still, one direction at first, then crossed, then
  crossed again, until dark. Line Length, Wobble and Taper Out shape the lines as before.
- **Print Shading and Screentone fill solidly.** A screened brush's mark now prints one even pattern across the whole
  tip, with a clean edge, instead of a darker squiggle down the middle with thinning dots at its sides; strokes over
  strokes at the same grey print the same dots, so scribbling over an area fills it evenly (a darker stroke still
  darkens it). Applies to the Print brushes (Ben-Day, Rosette, Halftone, Mezzotint, Dither, Engraving, Wavy-line) and
  every Print Shading choice; brushes without one are unchanged.
- **Notebook settings fixes.** Turning the tablet on a library card's screen keeps that screen open (a slider's
  stop is kept); a Daily Prompt card whose words end in "times" is no longer skipped; a name typed for a new notebook
  ending in "notebook" keeps that name at its first save; Random From with every template ticked keeps the owner's own
  templates in the draw; the one-time cover and plain-pages passes wait while a notebook opens, and leave a cover design
  chosen since the last save.
- **Brush fixes.** Holding the pen with a Hand Hatch brush no longer skips some of the strokes it should add; Stipple,
  Woodcut, Scribble and Hatching print shading over a wet ink brush draws about three times faster (as fast as
  Halftone); the **Hatched tone** Basic tool now shows crossed lines, as it says; Surprise Me treats Hand hatch as a
  dry brush, as it does Hatch.

- **Layers are back in the side bar, the easy way.** Under the tools: **+** above, a small picture of each layer of the
  page drawn on (the top first, the one drawn on framed), **+** below. One tap adds a layer, a clear page stacked on the
  page, and draws on it; a tap on a picture draws on that layer; a hold opens **Duplicate**, **Merge Down**, **Delete**
  (Undo takes each back in one step) and how it shows (**Solid**, **Half**, **Faint**, **Hidden**, with its eye). Up to
  four layers a page; the other layers show through the one drawn on. On the Nomad turned sideways the layers fold into
  one button that opens them. The Layers panel and the page bar's Layers button are gone.
- **A page of layers is one page everywhere else**: one preview in the page bar, one number in "3 / 24", one tile inside
  the notebook (Delete Page deletes all its layers), one page turn, one page in the PNG and PDF copies (its layers printed
  together, a Hidden one left out), one page on the shelf and the library card. Its guides are its bottom layer's. Older
  versions show each layer as a page.
- **Layers above cover the one being drawn on**, as layers do (white paint too); a page turned to opens on the layer
  last drawn on; a neighbouring page shows through at Page Transparency's own strength, however many layers it has.
- **Always a blank page at the end**: after pages are added, deleted or moved, and once the last one is drawn on.
- **Layer fixes**: a layer added below the bottom one keeps the page's own guides; deleting the bottom layer passes its
  guides, Ghost pin and cover picture to the layer on it (Undo puts them back); Undo of a deleted or merged layer works in
  a full notebook (100 pages); Use This Page on the Cover puts the page's bottom layer on the cover, as inside the
  notebook; Delete Page inside the notebook on the page drawn on goes to the next page's layer last drawn on.

- **A Print style, with twelve Basic tools.** Screentone: **Dot tone**, **Line tone**, **Sand tone**, **Gradient tone**,
  **Hatched tone**. Through Print Shading: **Ben-Day brush**, **Rosette ink**, **Halftone brush**, **Mezzotint** (charcoal),
  **Dither pencil**, **Engraving pen**, **Wavy-line pen**; pressure fills the pattern in, and a lighter grey opens it up.
  Print has its own chip on the Tools page and its own place in Brush Lab's styles; New Brush and Randomize in Print
  always give a printed brush (now and then a screentone).
- **Everyday pens.** Under Fineliner, **Ballpoint** (thin, grainy, darker pressed, now and then skipping), **Gel pen**
  (smooth and bold) and **Fountain pen** (swells pressed, pools where it starts); under Marker, **Highlighter**, a broad
  chisel of light wash that goes behind the lines.

- **Hand Hatch**, a new Tip (Brush Lab, Advanced): hatch strokes laid one by one as a hand lays them, each a little
  askew, bowed and flicked thin at its end, their ends falling where they fall, not a pattern the pen reveals. Light,
  one direction of sparse strokes; pressing harder, going back over a place or holding the pen there adds a second
  direction, then a third, then tighter rows. Its stroke has its own dials: **Line Length**, **Wobble** (neat to loose)
  and **Taper Out** (blunt to a flick), beside Spacing, Line Weight, Line Angle and Lines Follow.
- **Four more Print Shading choices, and Screentone patterns**, whose look changes as they darken: **Stipple** (more dots,
  then they merge), **Woodcut** (broken lines that thicken until only broken white lines are left), **Scribble** (a
  looping scribble, then a second and third over it) and **Hatching** (one direction, then crossed, then crossed again).
- **Ben-Day looks like comic Ben-Day**: a crisp round dot at every point of an even, offset grid, all one size, stepping
  through a printer's tints (10, 20, 30, 50, 70%, then solid) as the grey darkens, instead of scattered dots.
- **Timed sections on the Diary Page.** Did 2:30, Saw 2:30, Heard 0:30, Picture 0:30, one after another (Lynda Barry's
  diary). They start at the first mark on a fresh Diary Page, or a tap on the side bar's countdown, which shows the section
  over its time ("SAW" "1:42"); tap it again to skip ahead. The running box is outlined bold, the others faintly dimmed (on
  screen only, never in the page or its copies). At a section's end its outline flashes once, then "Next: Heard"; after the
  last, "Done". The pen can go anywhere. A section stays open a day after its time, then its box locks ("Section locked").
  The times are set in Inkwash prompts.txt: "Diary Page times | Did 2:30 | Saw 2:30 | Heard 0:30 | Picture 0:30". A Diary
  Page is timed by its sections, not by Timed Pages. The side bar's countdown row is two lines tall.
- **The library card looks like one.** The notebook's name (tap to rename) over the days it was made and last added to
  ("Oct 21, 2026 – Nov 15, 2026"), a lock in the corner for **Lock Settings**, a stamped rule; the template as a swatch
  of its paper; Page Transparency by its eye; the tools as the borrowers' lines, one brush a line, as many filled as the
  bar shows; rubber stamps for No Erasing (tap it), Timed (its 5:00), Created and Pages, inked when on and a faint outline
  when off; the card's foot in a dotted pocket. Tap the swatch, the eye, the tools, Timed or the lock for its screen.
- **The card's screens match it.** Each has its title and a line saying what it does; its choices are cards with what
  each does (templates and covers as pictures); the one in use is filled black with a check, not a thin bar. Timed Pages
  is a big slider with its time in large type; Brushes is a slider under the Tools choices (1 to 8, then All).
- **Renamed:** Pages Show Through is now **Page Transparency**; Lock is **Lock Settings**.
- **Cover Design moved:** hold the front cover in the notebook's strip. **Hold a page** in the strip to give it its own
  template. **Guides in PDF moved** to the menu, beside Settings (ticked while guides are printed). Cover Picture is set
  as before with Use on Cover / No Picture.

- **Timed Pages counts down in the side bar.** With Timed Pages on, the top of the side bar shows the page's time left
  (4:30), changing every 10 seconds, then every second in the last minute. Timed Pages is now a slider from 30 seconds to
  an hour (30 s steps to 5 min, then minutes); the card says "90 s" or "5 min". Older versions see the nearest of their
  old choices (1, 3, 5, 10 or 30 min).
- **Brushes is a slider too** (All, 1 to 8).
- **A page keeps its Daily Prompt card.** The card dealt while on a page shows again when the page is next opened
  (once a session; also when the app reopens on it), with Start for a timed card. A tap closes it.
- **The menu has Open Notebook** (to the shelf), and its button is a circle of three dots. Menu, Undo and Redo are icons
  alone, with no grey behind them.
- **The library card is where a notebook's settings live.** Notebook Options is gone: the shelf's long press and the menu
  now say **Settings** and open the notebook on its card. Tap a row with two choices (Guides in PDF, No Erasing) to switch
  it; tap any other row to open a full screen of its choices, big and marked: Template (previews of every template,
  yours too, and Random; hold Random to pick what it draws from), Pages Show Through, Tools, Brushes, Timed Pages, Lock
  (each saying what it does; Sealed still asks first) and Cover Design (the covers themselves). Tap Surprise Me again for
  new brushes. Locked, the tools and rules show grey on the card. A long value is cut rather than run off the card, and
  the name sits in the middle of its heading.
- **New notebook** asks only its name, then opens its card to set it up. Its name takes the options ("- no erasing - 5
  min") at its first save, once something is drawn.
- **Random templates differ page to page.** No page gets the same template as the page before it (unless Random draws
  from just one).
- **Fun sits beside the stamp tool**, in one row under the eraser, on every screen; the menu's Fun row is gone.
- **A stamp tool.** A new button under the eraser, with a stamp icon: tap it to stamp with its nib. Hold it for its menu:
  a preview of the stamp, **Cut Stamp** (draw round a shape on the page, in marching ants; the ink inside becomes the nib,
  and Undo takes it back; an empty lasso says "Nothing to cut"), and a few choices for **Turns**, **Spacing** and **Ink
  Fade**. It has its own sizes and greys, and its nib is kept like the slots' brushes. Like the smudge tool it stays the
  same in every notebook, and is off while Notebook Options' **Brushes** is set.
- **Templates like Supernote's.** Notebook Options' Template now opens a list: Blank, Lined, Wide Lined, Grid, Dot Grid,
  Isometric, Hex, Music, Cornell, Storyboard, Diary, Diary Page and Comics, drawn sharp at the page's size, plus your own
  Supernote templates from the MyStyle folder (PNG, JPG or WEBP, scaled to the page in the panel's greys; if the file is
  later gone, the page is blank). **Random** gives each new page one of them and the page keeps it; hold Random (or the
  Template chip) to tick which templates it draws from.
- **Brushes, not One Brush.** Notebook Options' **Brushes** row: All, or 1 to 8 slots on the bar (the bar has eight);
  the smudge tool is off while it is set. **Tools** gains **Each Page**: a fresh Surprise Me toolbar on every page, the
  same each time you turn back to it. A notebook that had One Brush opens with that brush as its toolbar and Brushes 1.
- **Pages Show Through** is the new name of Layers Show Through, everywhere.
- **A tidier Notebook Options.** Three small sections, Pages, Tools and Rules, a line per option with its label at the
  left; a row left at its default has a grey label, one you set a bold one. About 100 dp shorter.
- **Daily prompts you can edit.** New cards in the spirit of Lynda Barry's classes (eyes closed, a self-portrait as
  something, four silent panels, Did, Saw, Heard). They live in **Inkwash prompts.txt** in Document/Inkwash, one card a
  line: "3 min | ..." for a timed one, "Diary Page | ..." for the diary boxes, # for notes. Delete the file (or every
  card) to get the originals back.
- **A tidier side bar and page bar.** Undo and Redo sit side by side in one row under the menu. The Brush Lab (flask)
  button is gone from the side bar: open Brush Lab from the Tools page (hold a brush in the tray). Zoom and its lock
  moved to the page bar, beside the page number, the padlock next to the percentage; tap and double tap work as before.
  The Paper button is gone from the page bar: choose **Pages Show Through** in **Notebook Options**. A page's
  see-through steps now show eyes: Solid a filled eye, Half a half-tone eye, Faint a thin eye, Hidden a closed eye, with
  their words beside them in the page's options; the Layers panel's eye shows the page's step too. The size in use is
  marked by a line on its left, and the grey in use by a dot beside the grey strip. Most icons across the app are now
  Phosphor icons.
- **A crisp page inside a notebook.** The big page is drawn from the page itself at the screen's own pixels, so fine
  lines and texture show sharp instead of a stretched, soft preview. The soft one shows for a moment while it is made.
- **A library card inside the front cover.** Right after the front cover in the strip is a card listing every notebook
  option and its value, typewriter style, with the day it was made and its page count stamped at the foot. Values you
  set are bold. Tap a row for its next value (Template, Guides in PDF, Pages Show Through, No Erasing, Timed Pages, Cover
  Design); hold a row to adjust it in Notebook Options (Cover Design opens the cover chooser). Lock is never changed by a
  tap. The card is not a page: it is never counted, exported or moved.
- **Open, not Draw.** Inside a notebook the button says **Open**. Tapping the preview already selected in the strip opens
  that page too.
- **Layers are pages.** The page bar's layers button opens a plain **Layers** panel: one row per page, the last on top.
  Tap a row to draw on that page; drag ≡ to reorder; tap the eye to hide a page from showing through; hold a row for
  **Duplicate Layer** and **Delete Layer**. **New Layer** adds a page right after the one you're on. Steps, the ▸ layer
  lists, ✎ and the Paper chips are gone from it. A page's Solid / Half / Faint / Hidden are still on a long press of its
  preview in the strip, now as words. **Notebook Options** gains **Pages Show Through**: Off, Page Before or Nearby
  Pages, and Faint for a fainter default. Old pages drawn with several in-page layers look as before; hold their row for
  **Merge Old Layers** (hidden ones included, so nothing is lost; Undo takes it back). The screen no longer slows down
  while the panel is open.
- **One grey strip in the side bar.** The four grey swatches beside the sizes are now one thin strip, black at the top
  to white at the foot, with a mark at each of the tool's four greys; the grey in use has a bolder mark. Tap near a mark
  to use that grey. Tap anywhere else on the strip to change the nearest grey to that shade and use it. Hold the strip
  for Sizes and Greys; rub it to load paint, as before. The eraser and the smudge tool hide it, so nothing moves.
- **Glass, and one place for pages and layers.** Paper gains **Glass**: every other page shows through the page being
  drawn on, Half unless set otherwise; Hidden pages stay out. A new **Pages and Layers** button (the layers icon) beside
  Paper opens a panel listing the pages, top of the stack first, the page drawn on opened to its layers. Each row shows its
  picture and four step icons (Solid, Half, Faint, Hidden: grey swatches; hold one to see its name). Tap a page to go to it,
  tap a layer to draw on it, drag ≡ to reorder pages or layers (a layer move is one Undo), hold a layer for Move Layer Up,
  Move Layer Down and Delete Layer (refused under No Erasing when it has marks). **New Layer** adds one above. A layer can be
  Half or Faint too, only on screen: the layer drawn on always shows Solid. Long notebooks show a window of rows with
  "▴ 3 more" / "▾ 18 more"; nothing scrolls. The Paper chips sit at its foot. The side bar's hidden layer column is gone.
  Steps, Paper and the Ghost are icons now; hold one for its word.
- **The eraser rubs a cover's design off.** On a notebook's front or back cover, the Eraser (Everything, the pen's
  eraser end, a finger) now takes the printed pattern too where it passes, down to white paper, to make room to draw.
  The label, the tape and the cover picture stay. Undo puts the pattern back with the marks; Clear Page brings the whole
  design back; a new Cover Design is rubbed off in the same places. Pen, Paint and Dry Media leave the design. It shows
  on the shelf, in the PDF and in the covers' PNGs. Older versions open such a notebook with the design whole.
- **Your notebooks get varied covers too.** Once, at launch, every notebook still on Classic Black Marble takes one of
  the nine designs by chance, as new notebooks do. Long press, Cover Design changes it back.
- **Tracing paper.** A new button beside Notebook sets the notebook's Paper: Plain, or Tracing, where the page before
  shows faintly under the page being drawn on (Half) and the page after fainter still (Faint). Tap it to switch; hold it to
  see both by name. Hold a page's preview and lift to set that page's step (Solid, Half, Faint, Hidden) or, in Tracing, pin
  it as the Ghost in place of the page before. Ghosts are only on screen: never in the page, its PDF or PNG, Replay or
  anything a tool reads. A page that shows through has a grey tab under its preview; a Hidden one is dimmed.
- **Page previews in the page bar.** ‹ and › are gone: the bar now shows tiny pictures of the covers and pages round the
  one being drawn on, next to "3 / 24". Tap one to go there; drag quickly along them to flip through the pages (lift to
  stay); hold one, then drag, to move that page (its pictures, PNG and PDF follow); hold and lift to see its number.
  The page being drawn on updates its picture a moment after the pen lifts.
- **The Pen eraser takes screentone in one pass.** With the eraser set to Pen, Screentone's dots go wherever it
  passes; Everything still scrapes them off in streaks, a little more each pass.
- **Smudge tool: tap picks it, hold for the choices.** A tap selects the smudge tool at once; its chooser of smudge
  brushes now opens on a long press (before, on a second tap), like the Eraser.
- **The page bar's Refresh button is gone**: on the Manta it did nothing visible. Supernote's own swipe refresh stays.
- **Fix: Dry Media takes charcoal's ghost; charcoal erases clean.** Charcoal that had a pen mark over it (later erased)
  left a faint ghost after Everything that no eraser choice could take, Dry Media included. Now Dry Media takes it, and
  Pen takes ink whole where only erasable pencil lies under it. Charcoal and Pastels now start at Erasable 1, like
  pencil, and the Charcoal and Pastels tools erase clean. A charcoal brush you already have keeps its own Erasable (.9:
  its words now say "Part erasable"); pick the tool again, or set Erasable to 1, to erase it clean.
- **An eraser that takes one kind of mark.** Hold the Eraser: a row of buttons beside it, **Everything** (as before), **Pen**, **Paint** and **Dry Media**, the chosen one solid (Clear Page joins the row on a page with marks). Pen takes ink (also Wet ink, Screentone), Paint takes Wash and Opaque, Dry Media takes Graphite, Charcoal and Crayon; the others stay. A chosen kind takes its marks whole, permanent ink too; Everything still never touches permanent paint. Where a pencil lies under ink, either choice leaves the permanent ink. The choice is not kept between launches, and selecting the Eraser says what it takes ("Erasing pen"). Drawings remember the kind of each mark (a byte a pixel only on a layer that holds two kinds); drawings from before count every mark as every kind.
- **A notebook renamed or synced over elsewhere opens as last saved.** If its file changed while it was open (renamed in
  Google Drive, say), Inkwash opens it as it was last saved, or shows the shelf if the file is gone, instead of
  "Couldn't recover the drawing". Only strokes not yet saved are lost; the old recovery is kept aside in private storage.
- **New notebooks get a cover design by chance**, one of the nine, instead of always Classic Black Marble. Long
  press, Cover Design changes it. Notebooks you have keep theirs.
- **A failed recovery no longer locks the shelf.** After "Couldn't recover the drawing", Open shows the shelf and
  its notebooks open again (before, every tap was ignored).
- **Pages never shrink below 100%.** A page bigger than the screen opens and stays at 100% (pan to see the rest);
  pinching out stops at 100% instead of fitting the whole page smaller (the old 94%).
- **Six new Print Shading choices.** After Grain, **Print Shading** (every medium but Resist) adds **Rosette** (four dot screens at the printer's angles, all black, so the dots flower into clusters), **Ben-Day** (comic-book dots that keep their size and crowd closer as the grey darkens), **Crosshatch** (engraved lines, a crossing set joining past mid grey, like etching), **Wavy Lines** (engraved lines on a wave, banknote style), **Mezzotint** (short random dashes, velvety) and **Diamond Dots** (square dots standing on their corners at the default 45°; Angle turns them flat). Like Halftone they are pure black dots fixed to the page and follow the brush's grey; Pattern Size, Angle, Double Print, Dot Swell and Patchy Ink apply (Pattern Size sets the dot or dash size, Angle turns every one). **Screentone** gains three matching patterns after Gradient: **Crosshatch**, **Wavy Lines** and **Mezzotint**. The old choices keep their places, so every saved brush code, preset and drawing reads and draws exactly as before. Randomize now and then lands on one: about one Print Shading roll in six, so Print Shading as a whole turns up as often as before.
- **New notebook: Options is a button, the name says the options, and the sheet stays put.** The name prompt's fourth boxed row (it looked like one more suggested name) is now an **Options** button at the bottom left, opposite Cancel and OK. Choices made there are said in the name: "Oct 20 bashful-cherry diary - no erasing - 5 min" (the template's word, No Erasing, One Brush, Timed Pages' minutes, Locked or Sealed; Tools and Guides in PDF are left out, and the default options leave the name as it was). The field and the three suggestions follow, but a name typed by hand is never replaced. Notebook Options no longer jumps up as the keyboard appears: it opens with the keyboard put away and stays where it is.
- **Notebook Options, tidied; Timed Pages adds 30 Min.** Every row is now a label over its choices, on one grid of four equal columns, so the buttons line up from row to row and nothing is cut off (Timed Pages' six choices take two lines). Two words are shorter on the sheet: **Comics** (was Comic Panels) and **Library** (was Pick from Library). Timed Pages offers **Off, 1, 3, 5, 10 and 30 Min**; notebooks saved before still open.
- **Fix: chosen choices in Notebook Options are readable again.** The chosen Template, Tools, rule and Lock buttons showed black text on black; the dialog's restyle repainted their text ink. Each now shows white on black.
- **Fix: holding a swatch opens Sizes and Greys again, and the page bar loses its line.** A steady finger or pen always jitters a little, and on a grey that jitter added up to a "rub" within half a second, which called the long press off; tiny wobbles no longer count, so holding any size or grey opens the window while a real rub still loads oil paint. The line above the page buttons at the foot of the page is gone.
- **Replay page: speeds, and a longer memory.** Replay page now shows **1×**, **2×** and **4×** beside the tool bar for a few
  seconds; a tap starts the replay at that speed (1× is about five times as fast as drawn, the old speed). A page that
  passes 60,000 samples now forgets its oldest strokes instead of the new ones, so a replay of a very long drawing starts
  part-way.
- **Daily Prompt: Start and a countdown.** A timed card has **Start**: the clock counts down on the card once a second
  and ends on "Time"; Stop ends it early. Untimed cards have no Start. Starting a Diary Page card makes the page's own
  template Diary Page (that page alone, guides only: no ink is touched or lost). The Diary Page boxes are now
  labelled DID, SAW and HEARD, faintly like the rest of the template.
- **Replay page.** The page remembers how it was drawn: every pen stroke with a brush is kept in the drawing's file with
  its brush, size and grey, and **Replay page** in the page menu clears a copy of the page and draws the strokes again, in
  steps (about five times as fast as drawn) so the screen refreshes a chunk at a time. Touch the page to stop; the page
  comes back as it was, never changed. Undo hides a stroke from the replay and Redo brings it back; Clear page starts it
  over. The eraser, smudge, fill, shapes, one-finger drawing and Copy Strokes' copies are not replayed. A page keeps up to
  60,000 samples (a long evening's drawing); after that new strokes draw but are not kept. Older versions open these
  drawings without the replay.
- **Daily Prompt.** A file-menu row, **Daily Prompt**, shows today's card: a short, quick drawing prompt about the day,
  with its length in the heading (30 s, 2 Min, 3 Min or Untimed). The same card all day, a different one each day, 32 of
  them before any repeats. **Deal Another** shows a different card. Two cards are Diary Page cards, for the new
  **Diary Page** template in Notebook Options: three boxes down the left (for Did, Saw and Heard) and a big box for the
  drawing, printed faintly under the ink like the other templates. No countdown yet.
- **Fill works with mirrors.** With Copy Strokes, a Fill tap fills the shape there and the shape at each of its
  reflections, each as if tapped there, and one Undo takes them all back. The tapped shape fills at once and the
  reflections just after. A shape that holds several reflections fills once; a reflection that lands on a line or off
  the page fills nothing. With Real Mirror, a tap on any side fills on the kept side, so it shows on every side, and a
  shape the mirror closes (lines that run up to the mirror) holds the fill. Before, Fill ignored the mirrors, and with
  Real Mirror a tap on the covered side filled nothing that showed.
- **Gradient: the grey changes along a stroke.** A Control option in Brush Lab, after Grey Shift. **Fade** lightens the
  stroke to paper; **To Grey** takes it to an **End Grey**, lighter or darker, one of the panel's 16; **Cycle** blends
  through Grey Shift's set of greys (**Greys**: Toolbar or Any), one after another and round again, like a rainbow
  pencil. **Length** sets how far it takes, in cm of stroke drawn, the same on the Manta and the Nomad (Cycle: from one
  grey to the next). Every stroke starts again at its own grey (Grey Shift's pick when that is on), so it never runs
  out. It works with Paint Load. Ink, Wash, Charcoal, Graphite and Crayon take it; Opaque, Resist, Screentone and Float
  don't, and hide its rows. A Fill lays its grey at once. On the Nomad, with Grey Shift's Pattern or a Spiro, the Control
  card has no room for it. Every other brush code is as it was. The web lab and Brush Studio have the same dials, codes
  and words.
- **Grey Shift: a new grey at every touch.** A Control option in Brush Lab, so any brush can carry it. Each time the pen
  touches down, the brush picks its grey from a set: **Random**, **From a Set** (shuffled, every grey once before any
  repeats, never the same twice running) or **Pattern** (**In Order**, or **Ping-Pong** there and back). **Greys** picks
  where the set comes from: **Toolbar**, the bar's grey swatches as you have them, or **Any** of the panel's 16 greys.
  Tap a swatch in the Control card to take it in or out of the set; each keeps its own set. With Toolbar greys the bar
  marks the grey in use after each stroke. Handwriting comes out in changing greys. Every other brush code is as it
  was. The web lab and Brush Studio have the same dials, codes and words.
- **Stay in Lines: wet paint that keeps inside your ink lines.** A new Smudge choice, **Stay in Lines**, in Brush Lab.
  Draw a shape in ink, then paint inside it with a wash: like a colouring book, the paint stays in the shape where the
  stroke started, however far the brush strays over the line, and runs out through it from the brush. **Flood** sets how
  far: Brush (the brush alone), a few millimetres to about 35 mm past the brush, or Fills (the whole shape). The wash
  runs right up to the line and into the point of a narrow shape, and a small gap in the line (about a third of a
  millimetre) still holds it. Only crisp ink makes walls: a wash or a soft mark never holds it. Started on open paper,
  or in a shape reaching more than about 5 cm from where the stroke starts, it paints as a plain wash. Brushes are never rolled to it; every other
  brush code is as it was. The web lab and Brush Studio have the same choice, dial, codes and words.
- **Crayon: wax on toothy paper.** A new Medium, **Crayon**, in Brush Lab. A light touch catches only the paper's
  grain; pressed hard it goes solid and waxy, with an edge that stays broken. Wax already down lifts the surface, so
  going over it again builds up. A wash laid over it slides off the wax and settles beside it. **Wax** sets how much it
  lays, from chalky to waxy (more build-up, and washes slide off more). **Scratch** turns it into a thin blade that
  scrapes back to white: cover an area in black crayon and scratch through it. The same stroke always catches the same
  grain. Brushes are never rolled to a Crayon; every other brush code is as it was. The web lab and Brush Studio have
  the same choice, dials, codes and words.
- **Fading and Lemon Juice: ink that changes after you draw.** Two new Drying choices in Brush Lab. With **Fading** the
  mark fades away unevenly, its thin parts first, over its **Fades In** time (10 s to 2 minutes, 20 s at first), until it
  is gone from the page and from the saved drawing; a page you turn away from or close keeps fading by the clock, and is
  further along (or gone) when you come back to it. With **Lemon Juice** the mark goes on as a faint trace and darkens in
  unevenly over its **Dries In** time (1 to 10 s, 4 s at first), then stays as an ordinary mark. The page steps them a
  little at a time (lemon juice about once a second, fading every 2 to 5 s), refreshing only where they changed. Undo
  while a mark fades takes the stroke away, and Redo brings it back as faded as it is by then, never fresh; once a fading
  mark is gone nothing brings it back. Ink drawn over a fading mark stays. Randomize gives them rarely (1 in 40 each for
  Anything goes, Lemon Juice 1 in 60 for Ink wash), named fade-… or juice-…; every other roll, brush code and drawing is as
  it was. The web lab and Brush Studio have the same choices, codes and words, and the web lab's test area fades and
  darkens them on a timer.
- **Stamp: a brush whose nib is a piece of your drawing.** A new Tip, **Stamp**, in Brush Lab. Pick it, tap **Cut Stamp** (in Brush Lab's bar, or in This Brush on the Tools page, a long press on the slot), and draw round a shape: the line shows as marching ants, and when you lift, the ink inside it, trimmed, becomes the brush's nib, like a potato stamp. Then it draws like any brush: **Spacing** small lays a continuous trail of the nib, large lays separate prints, and a tap prints once; **Turns** sets whether the nib turns with the line; each print is inked unevenly, darker as you press, and a little lighter print by print until you rest the pen about a second, which inks it again. **Ink Fade** sets how fast it runs dry, from None (every print as dark as the first) to Strong. The medium still applies: an ink stamp, a wash stamp, a halftone stamp. An empty lasso says "Nothing to cut". The nib is kept with your brushes; a brush code shared to another tablet without its nib prints a round stamp. Brushes are never rolled to a Stamp: it needs a cut.
- **Spiro: the spirograph toy as a brush.** A new Tip, **Spiro**, in Brush Lab: a fine line of loops from the toy's wheels. **Mode** **Line** lays the loops along your stroke, a pattern inside the brush's width; **Ring** is the toy itself: where the pen touches down pins the ring, and circling the pen round it turns the wheel and draws the rosette (a shaky hand never turns it back; going clearly the other way does). A tap in Ring draws the whole figure. **Curve** picks the shape: Spirograph (a wheel inside the ring, with the toy's **Ring Teeth**, **Wheel** and **Hole**), Epi (the wheel outside), Rose (**Petals**), Lissajous and Harmonograph (**Ratio** and **Phase**; the harmonograph dies away over **Fade Turns**) and Coil (a spring along the line, its far side lighter); **Loops per cm** sets how tight the loops are along the line. With a Spiro, **Control** adds **Pressure Sets**, **Speed Sets** and **Tilt Sets**: what each of them changes, Size, Loops, Wheel, Hole, Petals or Nothing (at first, pressure sets the size and tilt the hole). A change bends the line rather than making it jump. The medium still applies: an ink spiro, a wash spiro. Brushes are never rolled to a Spiro.
- **Spiro's ring shows while you draw.** In Ring mode the pinned ring appears as a light grey outline round where the pen touched down, drawn once through the screen's fast update (as Cut Stamp's lasso is), so it shows at once, and goes when you lift. It is only on the screen: never in the drawing, the undo history, a PNG or a PDF. Brush Lab's test area and the web lab show it too.

- **Notebook Options: a template, guides in the PDF, and tools of its own.** **Notebook Options**, in a shelf notebook's long press, in the menu inside a notebook, and (as **More Options**, under the suggestions, except on the Nomad turned to landscape, where the keyboard leaves no room) in the new notebook's name prompt, is one small sheet. **Template** puts faint guides under the drawing on every page but the covers: **Diary** (a big panel above, ruled lines below) or **Comic Panels** (a grid of six panels), thin mid-grey rules and hairline panels. They are the page's paper, not marks: the eraser never takes them, Clear Page leaves them, a page with only guides is still blank (it isn't printed at the end, and deleting it doesn't ask), and the page's file holds none, so changing the template changes nothing you drew. The pages' thumbnails show them; the PDF and PNG copies print them unless **Guides in PDF** is **Leave Out**. **Tools**: **My Toolbar** is the app's, as ever; **Surprise Me** gives the notebook eight slots of its own, rolled as a balanced mix (a plain fast pen first to write with; a line tool, a wash or soft brush, a dry or textured tool and two wildcards; at least two fast, two simple and one fancy; about half from your library and half fresh rolls; never two the same), shown as eight previews with a dice to roll them all again, a tap on one rolling just that one; **Pick from Library** opens the Tools page on the notebook's slots. Opening a notebook loads its toolbar, sizes and greys with the brushes; leaving it (the shelf) puts yours back. A notebook with none of these is saved byte for byte as before, and the version before opens one that has them, without them.
- **The Blend stump blends washes too.** Rubbed over a wash it now softens it and pulls its tone a little the way you rub, as it does pencil and charcoal, without adding any grey; where a wash lies under pencil or charcoal, both blend. Permanent ink never smears, a dried wash blends as far as the eraser could lift it, and masking fluid keeps what it covers as it was. One Undo takes back both. Other smudges (the finger, Wash comb, the Water and Dirty brushes) still move only their own layer.
- **Big washes draw faster.** Washes, hakes and opaque paint from about 4 mm wide are now worked out on every other pixel, as they already were from about 8.5 mm: about twice as fast to render, their edges a pixel softer. Pens, pencils, charcoal and every brush under 4 mm draw exactly as before.
- **Big strokes finish sooner.** While you draw a wide wash, hake, oil or big pen line, its real texture now appears a little behind the pen in place of the plain preview, which stays only near the nib; at lift only the last stretch is left to finish, so the page is final sooner. The finished stroke is exactly as before.
- **Notebook covers show at once.** The shelf's covers, the Cover Design choices and a notebook's front and back covers used to take a second or more each to appear the first time. Each design is now a large printed sheet that comes with the app, and each notebook's cover is its own part of it, picked by the notebook's name (and turned or flipped where the design allows), so it appears almost at once and the same name still always gives the same cover. Going back to the shelf no longer redraws covers it already showed. The designs look as they did, though each notebook's cover is a different part of the pattern than before; Swirl's marbling is a little more combed.
- **Eight brush slots, and a long press to change them.** The side bar holds eight brushes now, not six: your six stay exactly as they were, and the two new ones start as Soft charcoal (a broad, smudgeable charcoal, the same stick the Blend stump smudges) and Chisel marker (a flat felt nib). The Edit buttons are gone: hold a brush in the bar to open the Tools page on it, and hold any size or grey to set all four sizes and all four greys in one window, opening on the one you held (in Brush Lab too). Rubbing a grey still loads an oil brush; holding the eraser still offers Clear Page. On the Tools page the eight slots sit in one row on the Manta held upright, and in two rows of four elsewhere.
- **Pages turn from a thin bar under the page.** As in the Supernote's own document reader: ‹, the page number ("3 / 24") and › in the middle of a thin strip along the bottom of the page, and the Notebook button at the far end from the side bar. The page is that much shorter, so nothing is drawn under the bar; a page made before this opens at full size with its bottom edge under the bar.
- **A bigger page inside a notebook.** Its buttons are in one bar at the top: ‹ Notebooks, the notebook's name (tap to rename), Draw, Use on Cover and Delete Page (on the Nomad held upright the last two show their icons). Pages turn from the same thin bar as the drawing screen's, along the bottom, and the page preview takes the room the old rows had.
- **The Brush Lab button's flask sits in the middle of its button**, as do the icons on other icon-only buttons (they sat a little to the left).

- **Mirrors are quick, and their dashed lines can hide.** With Copy Strokes, lifting the pen after a long line no longer stalls while each copy is drawn again: the copies of pens, pencils and washes now appear at once (a kaleidoscope's copies of a long pen line in about a tenth of a second on the Manta, of a big wash in about a third), and every copy shows while drawing with a pen. Each copy is the stroke itself, reflected, so its paper grain is the stroke's mirrored rather than the page's where it lands; where a copy falls on other marks it darkens them as the stroke would. Opaque paint (Gouache), the smudge tool, the eraser and washes that keep drying after the pen lifts still draw each copy again. One Undo still takes the stroke and all its copies back. The Mirrors list has **Show Guides**: off, the dashed lines go and the mirrors work as before; the page remembers it. **Clear Mirrors** is now **Remove Mirrors**: with Copy Strokes it takes the mirrors away and keeps the drawing as it is; with Real Mirror it shows the page as it really is again, and **Keep Reflection** keeps what you see.
- **The smudge tool shows what it smudges, and its smallest stumps blend.** At its two big sizes the Water brush and the Dirty brush showed a thin line under the pen, a tenth as wide as what they then smeared or wet; now they show their whole footprint, as wide as the size dot, as a light dotted wash (the Water brush's water still runs out a little past it when you lift, as water does). The Blend stump at its two smallest sizes did almost nothing you could see; now it blends a fine band as wide as its size dot over pencil and charcoal. From 20 px up every stump, and every other brush, draws exactly as before.
- **Composition book covers, nine designs.** Every notebook's covers now look like a composition book's: a black cloth tape down the spine (on the left of the front, the right of the back), and on the front a white label with slightly rounded corners and a double rule saying COMPOSITION BOOK, with the notebook's name written on the NAME line, a SUBJECT line to write on, and the day the notebook was made on the DATE line. Each notebook picks its own design: **Classic Black Marble** (every notebook's, old ones too, unless another is picked), **Fine Speckle**, **Bold Marble**, **Reverse Marble**, **Swirl** (the earlier marbling, cleaned up), **Crackle**, **Terrazzo**, **Woodgrain** and **Engraved**, each made from the notebook's name. **Cover Design**, in a shelf notebook's long press and in the menu inside a notebook, shows all nine as small covers with that notebook's name, the one in use marked by the black bar; one tap picks it. A drawing on a cover stays as drawn, over the new pattern; the PDF and the cover PNGs follow the design. Covers are made in the background and kept; the shelf shows a plain cover with its tape until each is ready.
- **Oil paint that runs out, and a Filbert.** A new Basic tool under Oils, **Oil paint**: a filbert of opaque oil paint on canvas. Each stroke starts with a small load of paint and lays its grey solidly, then runs out along the stroke, sooner when pressed wider: it lays less of its own grey and more of the paint it drags from the page, and once empty it only smudges, so on clean paper it leaves nothing. To load it, rub the pen back and forth on one of the side bar's grey faces: that grey is picked and the brush filled, more the farther you rub, up to full; a plain tap still just picks the grey. A small gauge at the right of its tray row shows how much paint it holds for the next stroke; it drains as you paint and never drops below the small load every stroke starts with. Paint left over after a stroke carries to the next. In Brush Lab, Drag and Smear have two new dials, **Paint Load** (how far a full brush lays; off by default, as every brush was) and **Starting Load** (the share each stroke starts with), and the Tip has a new choice, **Filbert**: a rounded oval that follows the pen's tilt; with Paint Load on, a first touch is a thin crescent hollowed the way it moves. New Oils brushes sometimes come with a Filbert or Paint Load. Every saved brush and share code draws as before.
- **Real Mirror: draw on any side, and through the mirror.** A stroke on the covered side now draws as if on the kept side, at its reflection, so it shows on both sides; a stroke that crosses a mirror carries on as its reflection, unbroken at the line, its pressure, speed and tilt carrying on too. With a kaleidoscope, every sector draws. One stroke is still one Undo step. "Under the mirror" is gone. The side kept is no longer picked by a tap that took the next stroke: the Mirrors list has **Flip Side** (shown with Real Mirror), which keeps the other side, or with a kaleidoscope the next sector round; one Undo step. Copy Strokes is unchanged.
- **Magnetic poetry: the owner's word list.** The Classic kit is now the owner's merge of Paintmageddon's kit and the earlier one: 258 words, 427 tiles, with body words and anything deleted from either list left out.
- **Fun button, and Mirrors.** The side bar's Words button is now **Fun** (confetti), in the same place: tap it for the fun modes, **Magnetic Poetry** (the fridge magnets' bin, as Words was) and **Mirrors**, each with its icon, the ones on marked by a black bar at the right; the Fun button is filled black while any is on. Where the bar has no room for it, the menu has a **Fun** row. **Mirrors** opens their own list: **Place Mirror**, then draw a roughly straight line and it becomes a mirror, a dashed grey line that is never part of the drawing (it snaps to 15° steps, and through the page's centre or where mirrors cross when it passes near; a line too short says so). Up to four mirrors; a fifth replaces the oldest. **Kaleidoscope** puts three through the centre (six copies), **Clear Mirrors** takes them off, **Mirrors** turns them off and on again, and the list says how many there are ("3 mirrors, 6 copies"). Two styles: **Copy Strokes** draws every stroke again in each reflection, and reflections of reflections, up to 24 copies, every brush, the eraser and the smudge tool too, each copy a real stroke with its own texture and wet paint; the copies show as you draw and are laid properly when you lift, and one Undo takes the stroke and all its copies back. **Real Mirror** works like a mirror stood on the paper: the side you keep (at first the side with more on it; tap a side to keep it) shows as it is and its reflection covers the other side, live as you draw; the pen draws nothing under the mirror ("Under the mirror"), and what was there is only hidden: it comes back when you clear the mirror or go back to Copy Strokes. Several Real Mirrors make a kaleidoscope of what's already on the page. **Keep Reflection** lays what you see into the page as real marks, one Undo step, and takes the mirrors off. The page's PNG and PDF copies and its picture on the shelf never show the guides, and show a Real Mirror's reflection as the page shows it. Mirrors are saved with the page; a page without them saves exactly as before, and a page with them opens in the version before, without them. Magnets aren't mirrored. Undo takes back placing, a kaleidoscope, a style, a side and clearing.
- **A grey bar, black to white, as in MonoPaint.** In the Sizes and Greys window, and in the window a long press on a grey opens, the eight fixed greys are gone: in their place is MonoPaint's grey gradient, solid black at the left to paper white at the right in the tablet's own dots, with a small black marker under the grey in use and that grey shown big beside it, with its name ("Black", "47% grey"). Tap or drag along it: the marker follows the pen at once, and the grey is set where you lift (the long-press window then closes). It holds every grey the screen can show, 65 of them, and black and white are always there at its two ends. Washes (Watercolour, Ink wash and the rest) never had black among their greys: one wash stroke lays a glaze of at most a third of black, so their darkest grey showed as 34% grey. The bar still runs to black for them, and its black end sets a wash's darkest glaze: the big grey and its name show what one stroke lays ("Black glaze, 34% grey"); layers build up to black.
- **The Tools page, in four labelled sections, the slots first in weight.** From the top: **Test Area** (try the selected slot's brush; Clear in its header), **This Brush** (its stroke, name and words), **Toolbar Slots** (the six slots, now the page's biggest cards, each its stroke over its name; **New Brush** in their header) and **Library** (Basic tools, Recent, Saved and All, the types and the pages, as before; fewer cards a page). Each section has a bold header over a black rule. Every preview on the page is now the side bar's short pen stroke instead of the loop (Brush Lab keeps its loops). The selected slot has a thick black outline that fades slowly to grey and back for about six seconds after you pick a slot or open the page, then stays black. **Roll another** is gone. **Back** now says its word beside its arrow, in a heavy black frame at the top left, here and in Brush Lab (where, on the Nomad, Save shows its disk alone to make room). In landscape, Test Area and This Brush are on the left, the slots and the library on the right.
- **Washes reach black.** Watercolour, Ink wash and every other wash used to lay at most a third of black in one stroke, so their darkest grey was a 34% grey. Now the grey you pick is what one stroke lays: half way along the grey bar a wash lays about half black, and at the black end a near-black wash (97% where the brush is full, about 87 to 97% over most of a stroke), which the screen shows black and the grey's name calls "Black". The darker it gets, the more the wash's own look is kept rather than going flat: its darker pooled edges, blooms, backruns and grain still show in a black wash, it still settles as it dries, and on a tilted page its low edge and drips run darker than the wash. Your tools look as they did: a wash slot keeps the grey it drew with (a Watercolour that was on Dark draws its 34% glaze, as before), the smudge tool's washes too, and the Basic tools' cards and samples show washes as they always have; the first grey you pick for a wash slot after this is the new, darker one. Inks, pencils, charcoal, opaque paint and Screentone are unchanged. Every brush code and drawing is as it was. The web lab and Brush Studio draw washes the same way.
- **Magnetic poetry: the owner's word list.** The Classic kit is now the owner's merge of Paintmageddon's kit and the earlier one: 258 words, 427 tiles, with body words and anything deleted from either list left out.
- **Fun button, and Mirrors.** The side bar's Words button is now **Fun** (confetti), in the same place: tap it for the fun modes, **Magnetic Poetry** (the fridge magnets' bin, as Words was) and **Mirrors**, each with its icon, the ones on marked by a black bar at the right; the Fun button is filled black while any is on. Where the bar has no room for it, the menu has a **Fun** row. **Mirrors** opens their own list: **Place Mirror**, then draw a roughly straight line and it becomes a mirror, a dashed grey line that is never part of the drawing (it snaps to 15° steps, and through the page's centre or where mirrors cross when it passes near; a line too short says so). Up to four mirrors; a fifth replaces the oldest. **Kaleidoscope** puts three through the centre (six copies), **Clear Mirrors** takes them off, **Mirrors** turns them off and on again, and the list says how many there are ("3 mirrors, 6 copies"). Two styles: **Copy Strokes** draws every stroke again in each reflection, and reflections of reflections, up to 24 copies, every brush, the eraser and the smudge tool too, each copy a real stroke with its own texture and wet paint; the copies show as you draw and are laid properly when you lift, and one Undo takes the stroke and all its copies back. **Real Mirror** works like a mirror stood on the paper: the side you keep (at first the side with more on it; tap a side to keep it) shows as it is and its reflection covers the other side, live as you draw; the pen draws nothing under the mirror ("Under the mirror"), and what was there is only hidden: it comes back when you clear the mirror or go back to Copy Strokes. Several Real Mirrors make a kaleidoscope of what's already on the page. **Keep Reflection** lays what you see into the page as real marks, one Undo step, and takes the mirrors off. The page's PNG and PDF copies and its picture on the shelf never show the guides, and show a Real Mirror's reflection as the page shows it. Mirrors are saved with the page; a page without them saves exactly as before, and a page with them opens in the version before, without them. Magnets aren't mirrored. Undo takes back placing, a kaleidoscope, a style, a side and clearing.
- **A grey bar, black to white, as in MonoPaint.** In the Sizes and Greys window, and in the window a long press on a grey opens, the eight fixed greys are gone: in their place is MonoPaint's grey gradient, solid black at the left to paper white at the right in the tablet's own dots, with a small black marker under the grey in use and that grey shown big beside it, with its name ("Black", "47% grey"). Tap or drag along it: the marker follows the pen at once, and the grey is set where you lift (the long-press window then closes). It holds every grey the screen can show, 65 of them, and black and white are always there at its two ends. For washes (Watercolour, Ink wash and the rest) the big grey and its name show what one stroke lays ("50% glaze, 49% grey"); since washes reach black, the black end is "Black" for them too.
- **Zoom, with a lock, as in MonoPaint.** Under redo on the side bar, a Zoom button shows a small padlock over the zoom (100%). It starts locked, and then nothing moves, as before. Tap it to unlock: two fingers pinch to zoom in, up to 800%, pinch inward to fit the page again, and drag to pan. Tap it again to lock, and the page stays as you see it. A quick double tap goes back to 100%. The lock is remembered. One finger still does what Finger says, and a quick two- or three-finger tap still undoes or redoes, unlocked too: two fingers only zoom or pan once one of them moves a little (about 3 mm), and then nothing is undone. The pen touching the page or hovering over it stops a pinch, and always draws. A new page, and turning the tablet, start fitted. Zoomed in, every brush, the eraser and the finger draw the same marks on the page as at 100%: sizes are the page's, Print shading's dots grow with the page like a print under a loupe, and a brush that thins when quick (Brush pen, many Anything goes) reads your hand's speed on the glass as at 100%, rather than going fat. The size dots on the bar show the size on the page, as MonoPaint's do. The button is on the bar on the Manta and the Nomad, either way up (on the Nomad in landscape beside the folded page turner, its padlock a little smaller); on a screen too short for it, the menu has a Zoom row that locks or unlocks it.
- **Edit buttons on the side bar, no carets, no numbers.** The tiny ▾ corners on the sizes and greys are gone. Under the four sizes and greys an **Edit** button opens one window for the whole set of the tool in use: its four sizes (tap one, then its slider or a size under it) and its four greys (tap one, then one of the eight greys under them); every change shows at once, and **Done** closes it. A long press on a size or a grey still edits that one alone, here and in Brush Lab (which has no corners either). A selected light grey no longer has a grey frame round it. Tapping a pen in the tray now only picks it, even when it is already the one in use: no window opens. Under the six pens an **Edit** button opens the Tools page, which now shows all six slots at the top: tap a slot, then a brush from the library under them (Basic tools, Recent, Saved, All and the types work as before) to put it there; try it in the test area, Undo takes back any slot's change, and Back puts them all in the bar. A long press on a pen opens the page on that pen's slot. **New Brush** on the Tools page opens Brush Lab on a new brush for the selected slot: once you Save it there, the slot has it and you are back on the Tools page (leave without saving and the slot keeps its brush). The pens have no numbers now, so each stroke runs the row's whole width; Brush Lab's slots and library don't show numbers either. The Finger choices show each slot's pen stroke and its brush's name instead of Slot 1 to 6 (the eraser, the smudge tool and a struck-through hand for Nothing), the one chosen marked by a black bar at its right, and the Finger button shows the slot's stroke small. On the Nomad in landscape the page turner folds into one button to make room (tap it for ‹, › and Notebook), and Words and Zoom are both in the bar there.
- **Two wet smudge brushes: Dirty brush and Water brush.** The smudge tool's chooser has two new Basic tools, each with its own icon on the side bar. The **Dirty brush** smears like a finger and keeps the paint it picks up on the brush, so your next stroke starts with it, even somewhere else on the page; while it holds paint its icon's brush head is grey and the chooser shows **Clean Brush**, which empties it (smearing it out on clean paper cleans it too). The finger set to Smudge keeps its own paint. The **Water brush** lays only clear water: it lifts paint where you work, so a dried wash gets paler there, and lets what it lifted run out softly through the wet it leaves, darkest at the wet's edge. The wet area is a wash again for a few seconds: it settles as it dries, and runs and pools downhill when you tilt the page. Over a wash that is still wet it lifts more and runs further. It lifts only what the eraser could take (permanent ink stays as it is) and stops at masking fluid. While Sumi floats, it nudges the floating rings along instead of printing them. In Brush Lab, Drag and Smear have a new last dial, **Keeps Paint**, and Smudge a new choice, **Clear Water** (Pick Up Paint, Lay Down Paint); Randomize gives Clear Water now and then (1 in 40 for Anything goes, 1 in 50 for Watercolor and Ink wash) and never Keeps Paint. Every brush code and drawing is as it was. The web lab and Brush Studio have the same.
- **Zoom, with a lock, as in MonoPaint.** Under redo on the side bar, a Zoom button shows a small padlock over the zoom (100%). It starts locked, and then nothing moves, as before. Tap it to unlock: two fingers pinch to zoom in, up to 800%, pinch inward to fit the page again, and drag to pan. Tap it again to lock, and the page stays as you see it. A quick double tap goes back to 100%. The lock is remembered. One finger still does what Finger says, and a quick two- or three-finger tap still undoes or redoes, unlocked too: two fingers only zoom or pan once one of them moves a little (about 3 mm), and then nothing is undone. The pen touching the page or hovering over it stops a pinch, and always draws. A new page, and turning the tablet, start fitted. Zoomed in, every brush, the eraser and the finger draw the same marks on the page as at 100%: sizes are the page's, Print shading's dots grow with the page like a print under a loupe, and a brush that thins when quick (Brush pen, many Anything goes) reads your hand's speed on the glass as at 100%, rather than going fat. The size dots on the bar show the size on the page, as MonoPaint's do. On the Nomad in landscape the bar has no room for the button: the menu has a Zoom row that locks or unlocks it.
- **Clearer names in Brush Lab.** Every dial, choice and option now says what it does, in Title Case: a tip's **Size** (was Weight), Bristle's **Paint Load**, Stipple's **Uneven Dots** (was Jitter), Hatch's **Line Angle**, and **Head Follows** / **Lines Follow** Stroke or Page (was follows, fixed); Ink's **Spatter**, **Line Shift** (was Offset), Opaque's **Thick Paint** (was Impasto; its card and "This brush" say "Thick paint"), Charcoal's **Messy Edge** (was Spread) and **Dust When Tilted**, and every medium's **Blend Mode** (Darken, White, Behind, Invert); **Print Shading**'s **Pattern Size** (was Cell), **Double Print** (was Misregister), **Dot Swell** (was Ink gain) and **Patchy Ink** (was Uneven), its options None, Halftone, Engraving, Dither, Grain; Smudge's **Pick Up Paint** and **Lay Down Paint** (Lift now goes gentle to strong); Paper's **Texture** (was Tooth) and **Ribbed** paper (was Laid); Drying's **Flow While Wet** (was Settle), **Runs Downhill**, **Pools Downhill**, **Rock to Blend**, Float's **Drift Downhill** (its rings small to large) and Mold's **Mold Length** and **Mold Growth** (was Reach and Growth, short to long); Control's presets **Press Wide**, **Press Dark**, **Press Both**, **Springy**, **Tilt Shade** and **Tilt Turn** (were Swell, Swell and dark, Brush, Side and Turn). The modules' descriptions say "ink · paint · charcoal · pencil" and "texture", and on the Nomad a narrow tile breaks Screentone as "Screen" / "Tone". Only the words changed: every brush, brush code and drawing is as it was. The web lab and Brush Studio have the same names, and one set of part descriptions.
- **Marbled covers, front and back.** Every notebook now has a black-and-white marbled cover like a composition book's, made from its name (the same name always gives the same marble; renaming makes a new one), with a white label holding the name. The shelf shows each notebook's front cover. Both covers are pages you can draw on: ‹ from the first page opens the front cover (the page number reads **Cover**), and › from the last page, after the blank page at the end, opens the back cover (**Back**, the same marble without the label). The marble is the covers' paper: it shows under what you draw, the eraser rubs back to it and Clear page leaves it; covers have no Fill page. Covers aren't counted as pages and can't be copied or deleted. **Use This Page on the Cover** in the menu (or **Use on cover** inside the notebook) glues a smaller copy of that page onto the front cover under the label, framed in a thin black rule, and it keeps up with that page as you draw on it; what you draw on the cover goes over it. On the front cover, **No Cover Picture** (**No picture** inside the notebook) takes it off. One page at a time is the picture; a page chosen with Set as cover before stays the picture. A cover with nothing drawn on it costs nothing in the notebook's file. The PDF now starts with the front cover and ends with the back cover (in the PNG folder: 00-cover.png and zz-back.png). Notebooks saved by this version still open in the version before, without their covers.
- **A slimmer side bar: pens in a tray, swatches in pairs.** On the drawing screen's side bar each of the six slots and the smudge tool is now a short row, its brush drawn as a short stroke lying across it, light to full pressure, like pens in a tray, as wide as the brush draws (up to what the row holds); its number at its left, a black tab under the one in use. The eraser and the Finger button sit side by side under them. The three sizes and three greys sit in three rows, each size beside a grey, a small black tab under the two in use; a tap picks, the ▾ corner or a long press edits them as before (a broad size's dot no longer says its millimetres there: the size window does). The tools and swatches take less than half their old height, so the bar has room to spare: on the Nomad in landscape, Words, Finger and the whole page turner are back in the bar. Brush Lab is as it was.
- **Four sizes and four greys, and the tools under them.** Every tool now has four sizes and four greys, on the side bar and in Brush Lab. Your three sizes and three greys stay as they were, in their order, and each tool keeps the one it had; the new size sits between the middle and the largest (44 px at first, or between your own two), the new grey between black and the middle grey. The eraser's sizes are S, M, L and XL (its old L is XL now, as broad as before). Under the sizes and greys, four tool buttons sit two by two: the smudge tool and a place for Fill (coming), then the eraser and Finger. The smudge tool's icon says what its brush does: a drop for one that blurs, a smearing finger for one that pushes paint, a blending stump for the rest; the eraser is a block eraser and Finger a hand smearing. What's in use has a black bar down its right side instead of a tab under it. A very light pencil or pen shows a little darker in its row so you can see it; the brush itself is unchanged. On the Nomad the bar is 4 dp wider, and in landscape Words is in the menu again.
- **Naming notebooks, and a blank page ready at the end.** New notebook now asks for a name first: the name it would get is already there and selected, so just start typing to replace it, or tap one of three other names under it to take that one at once (Cancel makes nothing). Long-press a notebook on the shelf for **Rename** and **Delete**; Rename uses the same prompt. Deleting a blank page no longer asks, nor does deleting a notebook with nothing drawn in it. A notebook always has one blank page at its end, ready to draw on: it's added when you open the notebook and when its last page gets its first mark, so › from your last page lands on a fresh one. You can delete it; it comes back the next time you open the notebook. Opening a notebook doesn't change its file, the blank page costs about 9 KB once saved, and the PDF and PNG copies leave out blank pages at the end.
- **Finger button.** What one finger on the page does now has a button on the side bar, beside the eraser: a hand, struck through for Nothing, with a small eraser, the smudge sign or the slot's number for the rest. Tap it for the same choices as the menu's Finger row (Nothing, Eraser, Smudge, Slot 1 to 6), the current one in black; one tap picks. The menu keeps its row, and the two always agree.
- **Clear page.** Press and hold the eraser on the side bar: a **Clear page** button shows beside it for a few seconds. Tap it and everything on the page goes, on every layer, with the masking fluid and the magnets; the page itself stays, with its shade and its place in the notebook. Nothing asks first: one Undo brings it all back. On a page with nothing on it the button doesn't appear.
- **Print shading and Screentone: print looks in pure black and white.** Every medium (but Resist) has a new **Print Shading** dial in Brush Lab: halftone dots, engraving (a line screen), dither or grain. A shaded brush lays its greys as usual, and they reach the page as pure black dots or lines on the paper, fixed to the page, so nothing turns grey. The dots follow the grey the brush would lay: a mid-grey swatch gives about half dots, a lighter one fewer, and with Control's pressure darkness pressing harder makes them bigger (Control says so: "pressure grows dots", and its grid's Dark column reads Dots). Strokes build up as washes do: a second stroke over the first prints darker, and the eraser takes the build-up with the dots (Undo brings both back; a drawing keeps it, and one without any print shading saves exactly as before). Two different prints over each other keep the first's dots and add the second's. With a print chosen, **Pattern Size** and **Angle** set the pattern's size (6 px is about half a millimetre) and angle, and the newsprint dials **Double Print** (a faint second print a few pixels off), **Dot Swell** (dots swell by a ragged pixel) and **Patchy Ink** (specks drop out where the ink ran thin) rough it up. Its menu opens in a panel beside the dial, as a part's choices open beside its module (so do the other dials shown as menus). A new Medium choice, **Screentone**, scribbles an area full of dots, lines, sand or a gradient down the page at its **Darkness**, black on any paper shade, leaving the pen lines under it as they are; the eraser scrapes it off in streaks along its stroke, a little more each pass. A shaded brush doesn't settle or run after you lift. Randomize gives a Print shading now and then (1 in 25 for Anything goes, Marker, Ink wash and Charcoal, 1 in 40 for Fineliner and Pencil, never Watercolor) and Screentone rarely (1 in 40, Anything goes only); every other roll is as before, and every brush code and drawing is as it was. On the Nomad, Brush Lab has room for the Print shading menu only (where it fits). The web lab and Brush Studio have the same.
- **Shaded paper.** On a page with nothing on it yet, tapping a grey on the side bar shows a **Fill page** button beside it for a few seconds: tap it and the whole page becomes that grey, like tinted paper. While the page is still empty, another grey changes it again; the lightest grey's button reads **White page** and gives plain paper back. Undo takes the shade back, one step at a time. Once something is drawn the button no longer appears, and drawing simply ignores it (the pen touching the page puts it away). The shade belongs to the paper: the eraser rubs back to it, the paper's grain still shows in what you draw, ink and washes only darken it (a wash lighter than the paper hardly shows), and gouache, oils and White ink sit on top lighter, so a black page with the White gel pen works as scratchboard. Each page keeps its own shade, in its notebook, its small picture and its PNG and PDF copies. Brush Lab's test page stays white. Earlier versions of Inkwash from 2026-10-16 on open a shaded page on white paper; MonoPaint and older versions can't open it.
- **Drawing stays quick: your work is kept when you pause.** The copy kept in case Inkwash stops was written after every stroke, which took seconds of the tablet's work while you drew the next one. Now it is written once you stop for 2.5 seconds (or every 5 seconds while you draw without stopping), never during a stroke, and quicker. Leaving the app, turning a page and opening a notebook still keep everything at once. If Inkwash crashes, at most the last few seconds of strokes are lost. Turning a page just after drawing is quicker too. Notebooks are saved exactly as before.
- **Notebooks hold 100 pages, however heavy.** A notebook's pages are read from its file when they're shown, and only the pages being drawn on are kept in memory, so a notebook holds 100 pages and up to 200 MB (16 MB before, about 15 heavy wash pages). Your drawings all go into one Loose pages, in the order of the dates in their names; one that can't take its size without losing a mark keeps its own. A notebook past 16 MB opens only in this version on.
- **Notebooks.** Everything lives in notebooks now. The app opens to a **shelf of notebooks**, the last changed first, each with its cover (page 1 unless another is set), its name and "24 pages · Oct 17"; **New notebook** starts one, named at once ("Oct 18 brisk-bean notebook"). Tap a cover to open it: its page, big, with ‹ › (or a finger swipe) to turn, a strip of its pages with + to add one, and **Draw** (or tap the page), **Set as cover** and **Delete page**; tap its name to rename it. Turning is instant: every page's small picture is kept inside the notebook's file, so nothing has to be opened to show it. While drawing, the side bar's foot has the page turner: ‹, the page over the count, › (+ on the last page adds one) and **Notebook**; the layer buttons are hidden for now (the Wash and Ink layers work as before). Back from drawing opens the notebook. The menu has Rename notebook and Delete notebook. Opening a notebook no longer reads every page first, so long ones open quickly.
- **Your drawings become notebooks, once.** At the first launch every one-page drawing goes, oldest first, into a notebook called **Loose pages** (Loose pages 2 and so on past 100 pages or 16 MB, or for drawings of another size). A smaller drawing is padded with blank paper; a bigger one only loses blank paper (the strip under the old bar); one that would lose a mark goes in a Loose pages of its own size. Multi-page files stay notebooks of their own. Only once the notebooks are written and read back are the original files moved, untouched, into **Document/Inkwash/Originals**, with a plain text list of where each one went. It runs once, and if it's interrupted it carries on at the next launch. Brush and import files stay where they are.
- **PDF copies.** Every notebook gets a PDF in EXPORT/Inkwash, one page per page at its real size at 300 dpi, easy to print or send once it's synced, and a grey PNG of each page in EXPORT/Inkwash/<notebook>/. They're written in the background when you leave a notebook or the app, only the pages that changed drawn again, and they follow renames and deletes. The old PNG copies of drawings stay as they are.
- **Fridge magnet words.** A new Words button in the side bar (a magnet) opens a bin of word tiles along the foot of
  the page: up to 48 at a time, More words for the next lot, Done to close it. Drag a word out of the bin onto the page
  with the pen, or tap a word, then tap the page to stick it there. Drag a magnet with the pen to move it (it comes to
  the front), with the bin open or closed: its outline follows the pen and it lands where you lift. Drag it back into
  the bin or off the page to take it off. Fingers don't move magnets. Undo and Redo take back placing, moving and taking
  off, one step each. Magnets lie over the drawing and never in it: with the bin closed the pen draws everywhere but on
  a magnet, and the eraser leaves them alone. They are kept with each page, saved in the drawing, and drawn into its PNG
  copy. Tiles look like Paintmageddon's: the word in Roboto Mono bold about 2.7 mm tall, black on a white tile with a
  fine grey edge and a thin black shadow, each tilted a little. The words are the classic magnetic poetry kit and
  the second kit's half from "liquid" to "young", 479 tiles with their repeats (19 "s", 6 "ing", 9 "the"), shuffled
  each time Inkwash starts; edit them in app/src/main/assets/words/default.txt, a tile a line. Any file in
  Document/Inkwash ending ".words.txt" becomes a pack of its own, shown first: any text, split into words, lower case,
  repeats kept, up to 600 tiles; the file stays as it is. On the Nomad in landscape the side bar has no room for the
  button while the layers are in it, so Words is in the menu there. A drawing with magnets opens in older versions of
  Inkwash from 2026-10-16 on, without its magnets (saving it there drops them).
- **A new brush's name says what stands out about it.** Randomize, a style, Anything goes and Tweak it now name a brush for its most unusual part, then a fruit or vegetable as before: "drip-plum", "rake-fig", "float-pea", "hatch-leek", "stump-fig", "mold-pea". Smudge, drying, gravity and the special tips and mediums come first, then an unusual tip, a strong paper grain, then a medium the style doesn't usually use; a brush with nothing unusual keeps its silly adjective. Tweak it keeps the first word and changes the fruit. The words are in `names/traits.txt`, as easy to edit as the other lists. Saved brushes, Basic tools and brush codes keep their names. The web lab, Brush Studio (its Random tab too) and Brush Scan name brushes the same way.
- **Mold: veins that bridge to nearby marks.** A new Drying choice. A Mold stroke is laid as Slow lays it; then, while
  it is wet (6.6 s at first, up to 20 s with Drying time), faint veins creep out from its edge and the ones that reach
  another mark nearby darken into bridges, slowing as it dries until they set. **Mold Length** is how far they go (at most
  three times the brush's width) and **Mold Growth** how many and how fast. Draw several Mold lines one after another and
  they grow together, feeding each other; any other brush, the eraser, Undo, changing layers or tapping the droplet sets
  them where they are. Undo takes a stroke back with the veins grown since. Masking fluid keeps veins out; the eraser
  takes them as it takes their stroke. New Basic tool under Ink wash: **Mold ink**. Randomize turns an Ink wash,
  Fineliner or Anything goes brush into one now and then; every other roll is as before. The web lab and Brush Studio
  called it Halo and now say Mold too; its codes are unchanged (Brush Studio's 604 codes now all read in the app).
- **Brush Lab: a part's choices open beside it.** Tapping a part opens its choices as a small panel right next to it, instead of in the strip under the test area. A pick puts the choice in and closes it, as do ✕ and a tap outside. In Advanced the part stays open; tap it again for its choices.
- **Randomize rarely makes a permanent brush.** An ink or oil brush from Randomize, a style or Anything goes now erases clean five times in six, and stays permanent one time in six; a wash, gouache, charcoal or pencil keeps its own Erasable. A locked Erasable is kept. Every other part rolls as before. The web lab and Brush Studio roll the same.
- **Open drawing lists the latest first.** Drawings are in the order they were last changed, the newest at the top (folders still come first). Brush libraries already list the newest first.
- **A new drawing's name says what's on it.** At its first save the silly word goes and the styles it was drawn with most take its place: "2026-10-18 ink-wash fig", "2026-10-18 charcoal-hatch ink-wash fig". It happens once; a name you give it is never changed.
- **The drawing screen no longer stays slow after the fast display hiccups.** When the panel refused or failed an
  update (seen after erasing and hovering the eraser's ring), Inkwash fell back to standard drawing, which is slow on
  e-ink, and stayed there until a menu or dialog was opened; its notice only showed then, wrongly as "Autosave
  failed". Now it waits out a busy panel a little longer, reconnects the fast display by itself a second later (up to
  three times in a row), and says so at once.
- **Brushes arrive from Drive more easily.** Any file in Document/Inkwash named "Inkwash import.txt" or ending ".inkwash.txt" is read, and its brush codes added. It is checked when Inkwash opens and each time you come back to the drawing screen. A read file is renamed with "(added)" so it isn't read twice. Brush Studio's Share panel has a new **Download for tablet** button that saves the brush as a ".inkwash.txt" file.
- **A smudge tool, and a choice of what a finger does.** Under the eraser in the side bar a new tile holds a smudge
  brush (the Blend stump at first), with its own three sizes: pick it to blend with the pen. Tap it again for the smudge
  brushes (the Basic tools that smudge, and any you saved) and pick another. In the menu, **Finger** sets what one finger
  does on the page: Nothing, Eraser (as before, the first choice), Smudge, or a slot's brush. Two- and three-finger taps
  still undo and redo; a resting palm still never draws. To make room, the side bar's tiles and sizes are a little
  shorter.
- **Blend stump, Comb and Wash comb.** Smear gets a **Soften** dial, last after Own paint: turned up, the smear works
  like MonoPaint's blending stump, softening marks where it rubs and pulling them a little the way it goes, instead of
  carrying them off along the stroke. Rub back and forth to blend hatching or lines into tone. On a Rake or Split nib
  it combs: each tine pulls the paint into a streak. Three new Basic tools: **Blend stump** (Charcoal), **Comb** (Ink
  wash: clear water with a five-line rake, to comb floating ink) and **Wash comb** (Watercolour: combs a wash into
  streaks). Every older brush and code draws exactly as before.
- **Brush Lab: the brush's loop sits in the "This brush" block, and an ADJUST bar heads the parts.** The loop is at the left of the plain words, drawn at your size and grey. The name and loop stay in the top bar too. On the Nomad, Randomize now sits beside the style button; on the Manta the part samples are a little shorter.
- **Resist: masking fluid and wax.** A new Medium. **Masking fluid** goes on before the paint and shows as diagonal
  hatching on screen (never in the drawing or its exports); nothing takes on it: washes, ink, white ink, paint behind,
  floating ink as it prints, a wash settling or running down a tilted page, a smudge or a blending stump. The eraser lifts it first: one
  pass takes the fluid and leaves whatever is under it, so you rub it off to find clean paper; the next pass erases as
  usual. Clear page peels it all. **Wax** (Resist with Erasable off) stays for good: it leaves a faint sheen, a grey step
  at most, and on rough paper catches only the paper's high points, so a wash over it seeps into the grain; the eraser
  never takes it, nor what is under it. Its one dial is Erasable, from wax to fluid. The grey you pick sets how strongly
  it keeps paint out (Light lets about half through). Two new Basic tools under Watercolour: **Masking fluid** and **Wax
  resist** (on cold press). Randomize turns a Watercolour brush into one now and then; every other roll is as before.
  One Undo takes back a resist, or an eraser stroke with the fluid it lifted. Brush codes with Resist don't open in
  older versions of Inkwash, and a drawing with wax opens in them as masking fluid or not at all; every older code and
  drawing reads and draws exactly as before.
- **Blend: how a brush meets what's already there.** A new dial on every medium, last after Erasable. **Darken** is
  the medium's own way (on Opaque it's **Cover**), as every brush drew before. **White** lays white: a white gel pen
  or chalk over a dark wash, white paint over a drawing; the grey you pick sets how white (Dark is full white). **Behind**
  goes only where the page is still blank, so a wash laid after inking slips under the lines and leaves every mark as
  it was, even as it settles. **Invert** turns what's under it to its opposite: paper to black, black to white. The
  eraser takes each by its medium: white ink over permanent ink stays, over pencil it erases with the pencil; a wash
  behind ink erases as a wash and leaves the ink. On Float it does nothing (greyed). Two new Basic tools: **White gel
  pen** (Fineliner) and **Wash behind** (Watercolour). Randomize picks White, Behind or Invert now and then (Pastels,
  Charcoal, Gouache, Watercolour, Ink wash, Anything goes); every other roll is as before. In Brush Lab on the Nomad
  Blend is a menu button. Older versions of Inkwash open a code with a Blend as the plain medium; every older code
  reads and draws exactly as before.
- **Stipple and Hatch, two new tips.** **Stipple** lays dots scattered along the stroke instead of a solid line:
  press harder and they pack closer, go over it again and more land. Its dials are Dot size, Scatter and Jitter (from
  an even stipple to a sponge print), and **Build-up** (off, slow or fast): rest the pen and dots keep landing under it,
  so a spot darkens the longer you hold, as with a spray. **Hatch** lays crisp parallel lines: press harder and a
  second set crosses them, harder still a third, so greys build from black lines. Its dials are Spacing, Line weight,
  Angle, Lines (fixed on the page, so strokes line up, or following the stroke) and Layers. Both work with every
  medium: an ink stipple, a sponged wash, pencil hatching. Three new Basic tools on the Tools page: **Stipple pen** and
  **Hatch pen** (Fineliner) and **Stipple sponge** (Watercolour). Randomize picks them now and then (a Fineliner,
  Watercolour, Dip pen, Pencil or Anything goes brush); every other roll is as before, and the variations never switch
  to them. Brush codes with a Stipple or Hatch tip don't open in older versions of Inkwash; every older code reads and
  draws exactly as before.

- **Sketch in pencil, ink over it, erase the pencil.** Every brush now has an **Erasable** dial, the last of its Medium
  (Brush Lab's Advanced), from off (permanent) to 1 (erases clean), and starts where the real medium is: ink is
  permanent, pencil erases clean, charcoal leaves a faint ghost, a wash lifts a little (the eraser takes 40% of it) and
  gouache a little (30%); dried oils don't erase. The eraser (the tool, the pen's eraser end, a finger) takes only what
  can be erased: ink drawn over pencil stays exactly as it was drawn when the pencil is erased, however hard you press,
  and a second pass takes nothing more. Smudge moves only what can be erased too: a Stump or a finger blends the pencil
  shading of an inked drawing and leaves the ink lines crisp, and a Lift takes back only the erasable part of a wash.
  Drawings and brushes from before erase everything, as they always did; brushes you made keep erasing fully until you
  change the dial. Every Basic tool now carries its medium's value, and a slot holding a Basic tool (or a first-launch
  brush) exactly as it came takes the new one once, so the Fineliner in slot 1 becomes permanent. A new Basic tool under
  Pencil, **Sketch pencil** (a hard H pencil on smooth paper that "erases clean"), is the pencil to draw under ink. A
  brush that differs from its medium says so: "Erasable.", "Permanent." or "Part erasable." in This brush, and in its
  caption ("Fineliner, erasable"). Drawings with permanent paint are saved in a newer format that older versions of
  Inkwash won't open (they say "Unknown painting format" and leave the drawing untouched; the PNG copy opens anywhere);
  drawings that erase fully are saved exactly as before.
- **Brush Lab's part cards all draw a wave.** The Medium and Drying cards, and the choices that open under every
  part, now draw a wave like the other parts, so a wave always means "one part of the brush" and the loop always means
  "a whole brush". Drying's wave ends pressed firm, so you can see how the paint settles as you lift.
- **The brush's loop beside its name.** In Brush Lab and on the Tools page, the brush's sample loop now sits right
  next to its name at the top, in the slot's grey, so the name and what the brush draws always show together. The
  loop box in the test area's corner is gone: the whole test area is for trying the brush. On the Nomad, Brush Lab's
  Load, Save and Basic | Adv buttons are a little narrower so the name keeps its room.
- **Big strokes show cleanly when you lift the pen.** Big brushes no longer leave a ghosted pattern of rings or teeth
  that only a full refresh cleared, on the drawing screen and in Brush Lab. A big brush's quick preview now uses the
  same screen update as everything else, so it shows about a tenth of a second later under the pen than before.
- **A new typeface: iA Writer Duo, everywhere.** Every screen, the file menu, dialogs and messages now use one calm
  typewriter face in two weights, regular and bold (it replaces Roboto Mono). Labels and headings are bold, the rest
  regular; a dialog's question is bold and its explanation regular. A few Basic tools' one-line descriptions were
  shortened to fit their cards ("thins at speed", "swell, grainy", "runs downhill", "on crumpled").
- **Tool slots without boxes.** On the drawing screen's toolbar and Brush Lab's toolbar row, the slots no longer have
  outlines; the slot in use has a short black bar under it, the same mark the sizes and greys use.
- **The sample loop and the app icon stand upright.** The loop every brush shows (on cards, slot tiles, the toolbar,
  part cards and the test area's corner) is turned a quarter clockwise and flipped, so it no longer looks like a letter
  "a"; the launcher icon is turned the same way. Its tail after it crosses itself is half as long, and the stroke now
  starts light and stays firm to the end. The corner box is upright to fit it, leaving more of the test page.
- **Plainer words everywhere.** Brushes are described the way an art shop labels them: "Line and wash on felt paper.
  Pools when slow. Speed thins." instead of "An ink line with a wash around it from a round tip, on felt paper. It pools
  where you slow down. It settles just behind the nib… Your hand: fast → thinner." Only what is unusual is said (a round
  tip, smooth paper and Quick drying go unsaid), there are no arrows, and one word is used for one thing: Control's
  presets are Swell, Press dark, Side, Turn, Flick…; Multi-line is Rake, Cover is Opaque, Plain paper is Smooth; Pastels
  are pastels, not charcoal; pencil grades keep their capitals ("4B"). Captions no longer repeat the style ("Charcoal,
  charcoal") and Clear water's card says clear water. Watercolour is spelled the Canadian way. The presets Lean brush and
  Lean oils (lean means tilted here, not low-oil paint) are now Tilt brush and Side oils, and Granular is Granulating.
  Open and Save say "drawing" throughout, and the note about untested firmware no longer points to a Settings screen
  Inkwash doesn't have. On the Nomad, Brush Lab's library bar now fits one line, so Basic's test area is a little
  taller. The house style is `docs/TEXT-STYLE.md`.
- **The Tools page is simpler and shows more brushes.** One grid of cards instead of three sections, with four tabs
  over it: **Basic tools** (the ready-made brushes, which were called presets), **Recent** (what you put in a slot
  lately, newest first, Basic tools and rolls too), **Saved** (your own saved brushes) and **All** (both, each once,
  style by style). Under the tabs, tap a type (Fineliner, Watercolor, Anything goes…) to see only those, in whichever
  tab is open; a tab shows only the types it has. The page remembers the tab and type you last chose. More cards fit
  a page (Manta 30, Nomad 20 upright, 16 turned; turn pages with ‹ › beside the tabs). The brush's name is the one
  heading, its description small and grey; the test area runs the full width when the tablet is upright. The bar
  keeps Back, Undo and Brush Lab; **Surprise me** went (the cards are the way to pick a brush you have), and
  **Roll another** sits beside the brush's name. No black bars: white space parts the test area, the tabs and the cards.

- New tip: **Bristles**. A bristle brush's hairs follow each other, split into strands and join again; pressing
  splays them wider, and a lone hair leaves a grey track where a tuft leaves black. It carries its ink a long way,
  then, the drier its paint, runs dry into streaks and broken dry-brush marks at the end of a long stroke. A new Ink
  load setting says how far the ink lasts. Dry brush and Anything goes now sometimes roll a bristle brush (as in the
  web Brush Lab), and the Tools page has two: Bristle brush (Dry brush) and Hog bristle (Oils). Brushes and codes
  that already had a bristle tip, which drew as a round tip until now, draw with their hairs. Like every brush wider than about 3 mm, a bristle brush draws its plain preview under
  the pen and its hairs when you lift.
- **Try a brush on the Tools page** before you go back to drawing: the page (tap the selected slot) now has a test
  area. Tap a Basic tool, a recent brush or one from your library and it becomes the slot's tool, outlined, and the page
  stays so you can draw with it at the slot's size and grey; Roll another does the same. Undo takes each
  pick back, Clear wipes the test area, and Back returns to the drawing with the brush you chose. The tool's name and
  words sit beside the test area (over it when the tablet is turned); its loop shows in the test area's corner. The
  library shows fewer rows a page to make room (turn its pages with ‹ ›).
- **Brush Lab's Advanced mode has no tool library** any more, on the Manta and the Nomad: the test area gets its room
  (Manta about a quarter taller, Nomad half again as tall). The toolbar's six slots and the variations stay; Basic keeps
  the library.
- Drawing again while a Slow wash is still settling starts at once: the wash (the built-in Watercolor and Ink wash in
  Slow, and the Slow Basic tools) now settles off to the side, so a new stroke no longer waits up to a second or so for it
  to catch up, and buttons answer meanwhile. The wash is laid where it is as the new stroke begins, as before. Floating
  ink drifting on a tilted tablet, and a wash running down one, do their work off to the side too.
- **Float** (suminagashi, Japanese ink marbling), a fourth choice under Drying: the paint floats on water before it
  lands. Tap and the ink drops in a ring that pushes the rings already floating outward; hold the pen still and it keeps
  spreading. On the Tools page under Ink wash, **Sumi** and **Clear water** are a ready pair: tap them in turn for
  rings, and drag Clear water (or a rake, a split nib or a hake of it) through them to comb them into feathers and
  hearts. Tilt the tablet and the floating paint drifts downhill. It prints onto the paper when its floating time has
  passed since your last touch, when you tap the droplet, draw with a brush that doesn't float, change layers or leave
  the page; slot, shade and size changes don't print it, so you can switch between Sumi and Clear water freely. While it
  floats, Undo takes back one stroke at a time; after it prints, one Undo takes back the whole print. Rings keep crisp,
  slightly uneven edges with a faint grain of ink. In Brush Lab, Float has its Floating time, Spread (small or wide
  rings) and Drift dials, and Randomize picks it now and then for Ink wash, Watercolor and Anything goes. Older versions
  of the app can't open a Float brush's code.
- Big strokes finish sooner after you lift the pen: a wide wash, oil, ink wash or brush pen drawn with its plain
  preview now shows its real stroke about 0.35 s after the lift instead of 0.65 s. The real stroke is worked out while
  you draw, so lifting only puts it on the page, and the droplet no longer flashes on and off for a moment after each
  of these strokes (it shows only when a stroke takes longer than a second to finish). Slow washes such as the
  built-in Watercolor and Ink wash settle off to the side after the lift, so the screen stays free while they do.
- **Smudge** works: a brush's Smudge part can now Lift (take paint off as it goes, like a damp brush or a tissue;
  opaque gouache picks up some of the dark it covers and greys over it), Drag (push the paint beneath along with its
  own) or Smear (move what's there, like a finger). A smudge moves only the marks on its own layer, so a paint brush
  never drags your pen lines; one Undo takes it back. In Brush Lab, Smudge's choices and its Pick up, Lay down and
  Own paint dials are now live, and Randomize sometimes picks a smudge for Dry Media, Pastels, Gouache and Oils,
  as the web Brush Lab does. On the Tools page, **Stump** under Charcoal is a paper stump: it lays no charcoal of its
  own and only blends what is there. Brushes made in the web Brush Lab with Lift, Drag or Smudge now draw their smudge
  in the app. Small smudges draw live; wider ones draw their plain preview under the pen and the smudge when you lift.
- Strokes appear on the screen sooner: while the pen is down, the Manta now updates the screen with its fastest
  black-and-white refresh, about twice as fast as before, and the stroke is redrawn in its true greys when you lift.
  Every brush wider than about 3 mm now draws its plain preview under the pen (the brush's shape in ink, grey or a light
  dotted wash), so the mark shows right where the pen is instead of filling in behind it; the real stroke replaces it
  when you lift. Slow washes such as the built-in Watercolor and Ink wash, which already showed promptly, draw as before.
- New drawings are named with an adjective and a fruit or vegetable, like
  "2026-10-14 dapper-fig".
- Watercolor, wash and the eraser keep up with fast strokes: drawing quickly
  with a broad brush no longer makes the line lag behind the pen.
- Big brushes keep up with a quick pen: the pen's samples are drawn in batches and the drawing work is shared over
  the tablet's four cores, so a wide wash, oil or brush pen shows a fraction of a second behind the pen instead of
  seconds, and is finished about 0.2 s after you lift.
- Brushes too heavy to draw live at their size (big charcoal, pastel and pencil, big wet oils, wide wet brush pens
  and nibs, split nibs) now draw instantly: while the pen is down you see the brush's shape in plain ink, grey or a
  light dotted wash, and the real stroke replaces it when you lift, usually within a second (the heaviest, such as
  wet charcoal or pencil at the biggest sizes, a second or two). The droplet at the foot of the toolbar shows until it
  is done; drawing again, Undo or tapping the droplet finishes it at once. Undo takes it back in one step. Brushes
  that keep up draw live as before, and Brush Lab's test page follows the same rule.
- The bar's six drawing tools are now open slots: any brush can go in any slot, and each button shows a
  small stroke in its brush. A slot's panel offers the four brushes you used last; saving a brush keeps it,
  with no limit, so the "tap one to replace" step is gone. Your Wild brushes keep their names.
- **Brush Lab**, from a Brush Lab button in any slot's panel: try a brush on a test page, change its settings,
  style and paper texture, pick from six variations, rename it, save it, and share it as a code. Back puts
  the brush in the slot you came from.
- Brushes now have six parts — tip, medium, smudge, paper, drying and control — and Randomize, the style chips, a slot's dice
  and Brush Lab's variations make them: hake, split nib and multi-line tips; ink that loosens into a wash, gouache and
  oils that cover, charcoal and pencil; papers; and control that answers pressure, tilt and speed. New styles: Hake, and
  "Anything goes" (once called Wild). Share codes look like `brave-onion.M…` and work in the web Brush Lab too.
- Brush Lab describes a six-part brush in plain words under "This brush". Simple shows just the styles, Randomize
  and those words; Advanced adds each part's choices and dials, each with its own die and lock.
- A transparent wash whose drying is "Slow", with Settle above 0, keeps settling for its drying time after you lift (up
  to 20 seconds): pigment sinks into the paper's grain. Drawing again lays what's left where it is; Undo takes back the stroke and its settling together.
  Changing layer, page or shade, or leaving the app, finishes the settling at once, so the page is saved settled. A very
  big wash (over about a fifth of the page) settles the moment you lift. Brush previews show a slow-drying brush settled.
- Six-part brushes are stored in a form older versions of Inkwash can't read: if you go back to an older build, they
  disappear from your brush library (and slots holding one go back to a default brush).
- Brush Lab is laid out anew: the test page takes most of the screen, with the settings on the left and, under the
  test page, your six toolbar slots and the library. Tap a slot, then a library brush, to put it there; Undo takes it
  back. Load and Save sit beside the brush's name; Load also shows the share code to copy. Variations show only in
  Advanced.
- Brush Lab's Simple mode shows a brush as cards, one per part (tip, medium, paper, drying, control), each with a small
  sample of what it adds and one slider named by what it does (thin to broad, dry to wet, smooth to rough, still to
  lively, steady to responsive). Tap a card to see every choice for that part drawn with your brush, and tap one to
  use it. Everything fits on the screen of the Manta and the Nomad, without scrolling.
- Brush Lab's Advanced mode opens one part at a time, with all of that part's settings, each with its own lock and
  die, on one screen without scrolling. Control is a grid: tap a cell to say how much pressure, tilt or speed changes
  size, darkness or the nib. "Fast" is a switch that says what it does. Locks only work in Advanced; Simple rolls
  everything and keeps your locks for when you come back.
- Six-part brushes now share three sizes, 4, 16 and 120 pixels at first, instead of sizes tied to each style. A slot
  keeps the size it had until you pick another. When a slot gets a paint after a pen, or a pen after a paint, it takes
  the size closest to the new brush's own. The eraser keeps its own three sizes.
  Brush Lab's test page draws at the slot's own size and shade, and a size picked there is the slot's.
- The three sizes and three greys in a slot's panel can be set: tap the small corner tab (or hold) on a size to pick it
  from thin to big brush, or on a grey to pick one of eight. Every slot uses the same three sizes and greys, and each
  slot remembers the one it uses. They no longer have borders: a short bar under the one in use marks it. Brush Lab's
  test page has the same sizes and greys.
- Each slot's button shows its brush's real mark at its own size and grey, getting firmer to the end like a real
  stroke; a size too big to show is written in millimetres.
- Undo after a slot's brush changed puts back the size the slot had, too.
- Setting a size with the slider applies when you lift the pen from it, so the screen redraws once.
- Brush Lab: "This brush" sits right under Randomize; the shelf has two sections under black bars, "TOOLBAR: Tap to
  replace" with the six slots across, and "TOOL LIBRARY"; card and tile words wrap instead of being cut off; Clear and
  the sizes sit in a row above the test page, so Clear can't vanish.
- Brush Lab's parts are renamed Tip, Medium, Paper, Drying (was Life), Control (was Hand) and Smudge (was Carry);
  Drying's choices are Instant, Quick and Slow. Saved brushes and share codes are unchanged.
- Brush Lab's column is redrawn: Randomize, then one heavy frame with a black "THIS BRUSH" bar, the brush in plain
  words, and the six parts as modules, two to a row. Each module has its pattern strip, a black head with the part's
  name, icon and what it covers, then the current choice in bold, a sample and its slider. In Advanced, opening a part
  shows it across the frame under the six. The settings column is wider and the test page a little narrower.
- Brush Lab's Undo sits right after Save. Save shows a floppy disk, in Brush Lab and a slot's panel, where it is
  filled once the brush is saved.
- Brush Lab blinks less: while you drag a slider only the slider and its value redraw, and when you let go only what
  changed does — that part's module, "This brush" if its words change, the test page's corner sample and the slot's
  tile — all at once. A slide keeps the variations (the dice makes new ones). The size modal's dot and stops follow
  where the slider stops.
- Brush Lab's modules read Tip, Medium, Paper, Drying, Control, Smudge, left to right, top to bottom. "This brush" is
  only as tall as its words, the black heads only as tall as their descriptions, and the samples get the room; the
  THIS BRUSH bar meets the frame's border with no white slivers.
- Smudge's choice "Smudge" reads "Smear" (share codes unchanged).
- A new look across the app, in one plain black-and-white style: one typeface (Roboto Mono, bundled), six greys that
  show cleanly on e-ink, square corners and firm black rules, no rounded or faded buttons. Selected things are filled
  black or outlined heavily; secondary words are dark grey. No text is smaller than one size, anywhere in Basic.
- The toolbar's slots are square tiles like Brush Lab's toolbar row: each shows its brush's loop at the slot's size and
  grey (the size in mm when the loop is drawn smaller), its number in a small black corner, and the selected slot a
  heavy black outline. The eraser is a tile of its own. A thick black rule runs under the bar; the page keeps its size.
- Brush Lab follows its new mockup: Simple is now called **Basic**; the bar reads back, name, Load, Save, Undo and a
  Basic / Advanced switch; Clear, the sizes and the greys sit in a row above the test page, level with the STYLE label,
  so the style table and the test page start at the same height. Each part's header is white with its name, symbol and
  what it holds (on the Manta), over a black rule. The status line at the bottom is gone, and its room goes to the test
  page; a share code that won't load, or one you copied, says so in the Load sheet, and Save reads "Saved" once the
  brush is in the library. On the Nomad, the style table folds into "Style ▾", Control names its strongest response,
  and parts show their name without the list of what they hold.
- Menus and dialogs (open, save, rename, delete) use the same typeface, square boxes and black outlines.
- Every brush is now a six-part brush, drawn by one engine. The built-in tools become six-part brushes named for their
  style: Ink is **Fineliner**, Fude is **Brush pen** (as before, a fine point held upright and nearly five times as
  wide and flat laid low), Wash is **Ink wash** and Watercolor stays **Watercolor**. Your Wild brushes become
  six-part brushes under their own names. Each slot keeps drawing at the size and grey it had; its size and grey marks
  move to the nearest of the three shared ones, and every brush's sizes and greys can now be set. Wet watercolor no
  longer joins the stroke before it. The built-ins are ordinary library brushes now: you can delete them. Pasting an old
  share code (`…I…` or `…P…`) in Brush Lab still works and gives the six-part brush. A new install starts with
  Fineliner, Brush pen, Pencil, Ink wash, Watercolor and Gouache.
- In Brush Lab's Control grid, Tilt → size has a fourth level after FULL: **FULL+**, the old Fude pen's reach, from a
  fine point held upright to nearly five times as wide laid low, the nib flattening. Brush pen uses it.
- Brush Lab only says a "Slow" brush keeps changing after you lift when it does (a transparent wash with Settle above
  0). Ink wash, Watercolor and converted paints, which don't, read "Slow" and "settles as you lift".
- Big brushes keep up with the pen on the Manta: wide washes, hake, oils, gouache, charcoal and big brush pens draw
  as fast as you move, and a "Slow" wash settles sooner after you lift. A brush reaching past 100 px is worked out at
  half resolution and shown at full, so its edges and paper grain are a touch softer. Paints lay their dabs a little
  further apart (a tone or two lighter or darker in places; an Instant or Quick stroke can end a few pixels short of
  the pen), and charcoal and pastel dust falls in other places; pens draw as before. Wide "Quick" washes no longer
  leave flat grey half-moons behind a quick stroke.
- Settling paint gives way to the pen: when drawing lags it waits a moment, and it updates the screen at most about
  eight times a second. A "Slow" wash settles to the same result; a "Quick" stroke's settling behind the nib may show a
  moment later.
- While a "Slow" wash is still settling after you lift, a small droplet shows at the foot of the toolbar (and
  beside Clear in Brush Lab). Tap it to finish the settling now, as drawing again would; it goes away once the paint is
  dry.
- One toolbar, a narrow column down the left edge of the drawing screen; the bar across the top is gone, so the page
  takes the whole height. Top to bottom: the menu, **Brush Lab** (the black flask: opens the lab on the selected slot),
  undo and redo, the six slots and the eraser, the selected tool's three sizes and three greys, the layers, and the
  wet droplet at the foot. Tap a size or grey to use it, or tap its small corner tab (or hold it) to set it; the
  eraser shows its sizes only. Slots no longer drop a panel down. A layer's panel and the menu open beside the column.
- Tap the selected slot again for its **Tools page**: the slot's tool with its description, **Roll another** for a new
  one in its style, and **Brush Lab**; then ready-made Basic tools
  (five for each style; tap a style to see its own), your recently used brushes and your whole library, a page at a
  time. Tap a brush to put it in the slot and go back to drawing; Undo takes it back. Saving a brush is Brush Lab's.
- The toolbar fits the Manta and the Nomad in either turn without scrolling: its buttons share the height, shorter
  when there are more layers. On the Nomad in landscape the layers fold into one **Layers** button showing the current
  layer; tap it for all of them, with the buttons adding a layer above or below.
- Drawings made before this version are as wide as the whole screen: their left edge now sits under the toolbar. New
  drawings fit beside it.

- **Gravity**: tilt the tablet while paint is wet and it moves downhill, as the Gravity Lab showed. A "Slow" wash with
  gravity runs (its colour drains to the low side while the outline stays), pools in a darker, beaded low edge,
  drips (from the low edge, each led by a bead), lets its grain sink, or
  blends as you rock the tablet; it stays wet up to a minute so there is time to tilt it. A "Quick" brush runs, pools
  and sinks just behind the nib. Charcoal can drop dust downhill. Nothing moves once dry, nor on a level page: a
  tablet lying on a desk (under about 5°) counts as level, so nothing creeps. "Instant" brushes and opaque paint never
  move. The tablet's motion sensor is only on while something wet could move.
- Five gravity brushes on the Tools page: **Drips**, **Pooling wash**, **Tilt blend** and **Sinking grain** with
  Watercolor, **Charcoal dust** with Charcoal. Randomize now and then gives a Watercolor brush a little run or pool,
  and charcoal or pastels a little dust; it never makes a random wash drip.
- Brush Lab: Advanced shows the new dials (Runs, Drips, Pool, Sink and Blend under Drying; Dust under Charcoal) and a
  fourth Control row, **Page tilt** (wider drawn downhill, lighter uphill, a nib opening downhill). The test page tilts with the
  tablet, and "This brush" says what tilting does ("drips and runs when tilted"). Older brushes and share codes are
  unchanged.
- Brush Lab, Advanced: the variations sit under their own "Variations" bar, its dice at the right, with more space
  between them and the toolbar below. Their captions are one readable size, never cut off: each names the one or two
  biggest changes, in short words when space is tight ("+nib width"), and every variation's sample is the same height.
  The test page is a little shorter in Advanced to make room.
- Drips look like real paint drips: fewer and broader, spaced along the wash's low edge, darker than the wash,
  running straight down with a round bead at the front that stops where the paint runs out or the tablet is level.
  They are not all alike: where the edge holds more paint a drip runs longer (and a little broader and darker), where
  it holds little one barely starts; the spacing is uneven, now and then with a gap or two drips side by side that
  run together; and once in a while one leans a little.
- Brush Lab on the Nomad (and the Manta): Advanced's small print is bigger and never shrinks or gets cut. The open part
  shows only its dials, in bigger labels that wrap to two lines; its choices are the strip beside it, which already
  showed them with pictures. Line placement's three choices sit under its name.
- The Tools page never cuts the current tool's description: a long name on the Nomad left its first sentence running
  off the frame.
- On the Nomad in landscape, the Layers button no longer goes blank after turning the tablet to portrait and back.
- Saved brushes are kept for good: Save never writes over a saved brush (an edited copy is saved beside it, as
  "name 2"), and the library is also kept in "Inkwash brushes.txt" beside your drawings, so it comes back after a
  reinstall. In Brush Lab, Saved now shows as an outlined button with a check, unlike the solid Save.
- Brushes can come in from outside: put share codes, one per line, in "Inkwash import.txt" beside your drawings
  (Document/Inkwash), and the next time Inkwash opens they join your library; the file is then renamed so each comes in once.
- **Fill.** A paint bucket beside the smudge tool. Tap it, then tap inside a shape drawn in ink: the shape fills with the
  brush in your selected slot, in its grey, on its paper and drying as it dries. A watercolour lays a wash that blooms
  and dries as the brush does, a halftone ink fills with dots, Screentone with its pattern, charcoal with its grain. The
  fill reaches right into sharp points and corners, and runs just under the edge of the lines so no white rim shows; it
  goes under the ink, which stays as you drew it. Small gaps in a line (up to about 0.8 mm) still hold it in; a soft or
  wet line, or a wash, doesn't. Masking fluid keeps it off. Fill stays on, with a bar at its right (the slot it fills
  with keeps its bar too), for one fill after another until you tap another tool; tapping that slot puts the pen back
  in it. A tap on open paper fills nothing and says "No closed area". One Undo takes a fill back, and it erases as the
  brush does. The pen's eraser end still erases. A drag fills where the pen landed.
- **Flick and Fling, two bits of mischief for any brush.** In Brush Lab every Tip has a new last dial, **Flick**: lift
  the pen while it's still moving and the stroke carries on past the lift along the way it was going, curving as it
  curved, thinning to a dry point, further the quicker the lift. Every Medium but Resist and Screentone has **Fling**:
  stop dead after a quick dash and drops fly ahead, nearest first, with a little pool where the pen stopped; it happens
  while the pen is still down (or at a lift right after the stop). Both are drawn by the brush itself, so they print,
  erase and dry as the brush does, and one Undo takes them with the stroke; the same stroke always throws the same
  drops. "This brush" says "Flicks on a quick lift." or "Flings drops on a hard stop." Randomize gives them now and then
  (Flick 1 in 12 for Brush pen, Ink wash and Anything goes, 1 in 20 for Watercolor; Fling 1 in 15 for Dip pen, Brush pen
  and Anything goes). No Basic tool uses them yet. Every brush code and drawing is as it was. The web lab and Brush
  Studio have the same.

## 0.98.3

- With the **wet brush pen**, the Eraser color now erases with the pen's
  footprint, like other tools. To clean the brush, tap white.

## 0.98.2

- In landscape, the dots for the Eraser color, the Eyedropper and Masking fluid
  sit beside their icons, on the same side as the color bar's marker, so
  picking one looks like the color marker moved onto it.
- Turning the tablet to landscape for the first time no longer shrinks the
  painting: an expanded page still opens at 100%.
- The blow dryer shakes and blows air as it dries the paint, with no box
  around it.

## 0.98.1

- Supernote firmware 3.30.45 is now tested on both the Manta and the Nomad, so
  MonoPaint no longer says it's untested at startup, and Settings shows it as
  tested.
- In landscape, the Masking fluid button shows its dot again while it's
  selected.

## 0.98

- **Watercolor mode** is now the droplet in the color bar, for every tool
  instead of an option on each brush. The droplet is solid while it's on and
  dashed while it's off. With transparent paint, brushes paint
  watercolor as before, and shapes and flat fills now lay their whole area as a
  watercolor wash that blends and runs downhill too. Pencils, the airbrush and
  gradient fills paint as usual. The wetness slider beside the droplet now
  wets the canvas all by itself: slide it up for a wet canvas, or all the way
  down to dry it. Turn Watercolor mode on again with the droplet if your brushes
  had it on.
- On a wet canvas, watercolor glazes over the paint beneath it: most of that
  paint stays put and shows through, and some comes loose to bleed and run with
  the new wash, more the higher the wetness slider.
- Tap a color with the **wet brush pen** to dip it in that ink, instead of
  switching back to your brush; the Eyedropper dips it too. It gives the ink
  out as you paint and picks up what it passes over, and each time you lift it
  the color bar's marker slides to the ink it has left; tap a color to dip it
  again. The Eraser color still cleans it, and the marker slides to white.

- While wet paint is still moving, the **Watercolor mode** droplet's outline
  ripples: quickly while lots of paint is blending or running, slower and
  slower as it settles, until the droplet comes to rest. It waits while you draw. The dry brush pen's water settling
  for a moment after you lift it doesn't count.
- The brush in **Watercolor mode** paints a flat glaze while the pen is down,
  and the paint blends and runs only once you lift it, so a stroke goes where
  you put it. Rubbing no longer lifts dried paint under its wash; use the wet
  brush pen for that.
- **Masking fluid**, in the color bar beside the Watercolor mode droplet.
  Tap it and paint with any brush, pencil, fill or shape to lay masking fluid;
  it shows as bold diagonal hatching and isn't part of your painting. Then paint
  right over it: opaque paint, transparent glazes, erasing, and wet paint
  seeping across the canvas all stop at its edge, on every layer. Thin fluid,
  like the faded edge of a soft brush, lets some paint through. Turn on the
  Eraser color while Masking fluid is selected to rub fluid off with your tool.
  To peel it all off, hold the Masking fluid button until a Peel button pops
  out below it, then slide the pen onto Peel and lift (or lift, then tap Peel).
  Peeling can be undone, and Clear page peels the fluid too. Pick any shade
  to go back to painting.

- The Wet canvas water drop is a little taller and skinnier, and the drop and
  Masking fluid buttons take only the room they need, so the shade strip is
  wider.

- Supernote firmware 3.30.45 works with MonoPaint. On the Manta, the marker
  under the color bar follows the pen again instead of trailing behind it like
  a snake, and buttons are now drawn in black and white dots, as on the Nomad,
  so their press feedback is fast too. On the Nomad, closing the Layers panel
  or the Pages box no longer leaves a black box over the painting. Older
  firmware behaves as before.

- Small watercolor splotches keep running a little longer. They still take on
  most of their watercolor look right away, but then slow down instead of
  stopping, creeping for about two seconds, so you have time to tilt the
  tablet and let them run where you want. Bigger washes run at full speed as
  before. Going back over a wash with the watercolor brush glazes over it once it
  has slowed, instead of lifting it: starting the new stroke stops the older
  water, so it can't bleed into the new wash through gaps you missed. Paint you
  just laid is still wet enough to bleed into.

- Big watercolor washes run much faster. Every frame of wet paint now shares
  more of its work across the tablet's cores, so a wash covering the whole page
  moves about twice as fast and looks exactly the same as before. A wash that
  big (about a fifth of the page or more) also takes bigger steps: it dries and
  runs downhill in half as many frames, with a little less fine bleeding. A
  page full of watercolor you tilt now runs and settles in about 20 seconds
  instead of a minute and a half on the Nomad.

- On the Manta, the page button at the top right drops down a column of quick
  page action icons: add a page, **duplicate the page** (a copy goes right after
  it and becomes the current page: on page 5 of 8, the copy becomes page 6 of
  9), **delete the page**, **clear the page** and **clear the current layer**.
  Deleting a page asks first; clearing can be undone. On the Nomad, the Pages
  box shows them too: the page arrows and add page on top, the rest below.
  These replace the Clear button.

- The flat brush keeps up with the pen in transparent watercolor paint, as fast
  as the round and filbert brushes, and lays its watercolor the same as before.

- Tap the canvas with two fingers to undo, or with three fingers to redo, like
  Procreate. Each tap acts as soon as your fingers lift, so tap again to undo
  more. A pinch or pan never counts as a tap. The two-finger double tap that hid
  both bars is gone; swipe the bars away instead, or use Menu → Fullscreen.

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
- The **Blow dryer** is back, inside the Watercolor mode droplet, so it stays
  at hand even with the toolbar hidden. While the droplet ripples, tap it to
  dry all wet paint at once: watercolor, wet canvas paint and wet brush pen ink
  stop running and stay as they are. It turns into a blow dryer that
  blows a little air for a moment, then the droplet comes back. Watercolor mode and
  the wetness slider stay as they were, and the tool you were using stays
  selected.
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
  freely. With the option off,
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
