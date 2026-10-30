# Code structure

All app code is in `app/src/main/java/io/github/mpdairy/monopaint` (MonoPaint's package, kept; see
[INTEGRATION.md](INTEGRATION.md) for which classes are upstream's and which are My Bad Drawings').

## The painting screen

`PaintActivity` owns the open book, the current selection and the layout, and
coordinates the panels. Android and the e-ink driver always stay in portrait; the
app turns its own view tree (`QuarterTurnLayout`), so popups are placed in the
app's upright coordinates and mapped back to the portrait window (`Popups`).

| Class | Role |
| --- | --- |
| `DrawingPad` | The canvas: rendering, direct e-ink presentation, pen and finger gestures, pinch/pan, wet paint |
| `InkWashBar` | The one side column: Menu, the tray of ten slots, the smudge tool and the eraser, the size and grey box, the icon block, the wet indicator, Template Visibility and Play |
| `DrawLayout` | The screen's measures in dp: the column, the page bar, the brush line, the menus' and the tool picker's sizes |
| `PageTurner` | The page bar along the page's top: Notebook, Zoom, the page count and previews |
| `InkWash`, `ToolSlots` | The selected tool, each tool's size and grey, what one finger does (`FingerTool`), and the strokes they start |
| `SwatchBar`, `PickerBox`, `GreyStrip`, `SizeSlider` | The column's size and grey controls (the picker box is Brush Lab's too, turned on its side) |
| `WetIndicator` | The droplet while paint is wet; a tap lays it |
| `NotebookScreens` | The shelf of notebooks and the inside of one |
| `ToolsActivity`, `BrushLabActivity` | The Tools page (the brush library) and Brush Lab, where brushes are made |
| `ToolButton`, `PressOutline` | Icon controls and their immediate pressed outline |
| `SelectionFeedback` | Sends small control changes straight to the panel |
| `Theme` | Every colour, stroke, type style and typeface the screens use (see [DESIGN.md](DESIGN.md)) |
| `PaintPreferences` | Typed access to saved settings |

MonoPaint's own controls (`Toolbar`, `ToolSettingsPanel`, `LayersPanel`, `WetnessBar` and the rest) are still built
but hidden; My Bad Drawings' column replaces them, and a page's layers are in Page Settings (`NotebookScreens`).

## Tablets

`Device` is the only class that identifies hardware. Code asks it about a named
fact, such as `landscapeTilt` or `compactControls`, and asks `DirectEink` about
the e-ink driver's buffer. It never checks for a model. `PanelPreviewLayout`
previews a smaller tablet's panel (the Manta's Nomad simulation). See
[DEVICES.md](DEVICES.md) for the profiles and how to add a tablet.

## Tools

Every slot and the smudge tool hold a `Brush`: a six-part brush (`ModularBrush`: a name, a seed, a style
and one `PartChoice` per `BrushPart`, with its dials), stored and shared as an `M` share code. `ResolvedBrush` turns
it into what is drawn, and `InkWash` starts its stroke (`QuickStroke`, or `EraseBothStroke` for the eraser). A stroke
receives pen samples in page coordinates and edits the `ToneDocument`; it commits one undo step. The brush's medium,
not the slot, decides which layer it draws on.

To add a tool:

1. Make the brush in Brush Lab and copy its share code.
2. Add a row to `ToolPresets.ROWS` (style, name, share code, a one-line description, seed). It shows on the Tools
   page as a Basic tool.
3. To offer it in the tool picker (a slot's long press), add it with its drawing to `DrawLayout.PICKER`, the drawing
   a `res/drawable/tip_*.xml` named in that row.
4. A new kind of behaviour, not a new brush, is a new `PartChoice` with its dials, drawn in `ResolvedBrush`;
   `ModularFeatures` decides whether rolls may pick it.

Fill, Shapes and Lasso are drag-and-release gestures rather than strokes. `DrawingPad` runs
them itself. Live previews draw straight into the display raster without touching the
document: `ShapePreview` and `SelectionPreview` composite the layers natively (sharing
`cpp/layers.h` with page composition), and `CanvasGuide` draws guide lines over the artwork,
such as the gradient's direction or a selection's frame, and restores what was beneath.

A Lasso loop lifts a `FloatingSelection` from the active layer. The piece moves, scales and turns over the
page; `DrawingPad.placeSelection` lays it as one undo step when the pen lands elsewhere or
any other action calls `finishStroke`, and Undo drops it instead (`cancelSelection`). **Import**
(`PaintActivity.importFile`, `ImportPicker`, `PictureImport`) shows a picture, or one page of a PDF, over the page with `PicturePlacer`, to move and
size until Place or Cancel; Place lays its greys on the layer drawn on as one undo step.

## Documents

`ToneDocument` holds one page's layers and undo history. `DrawingBook` holds the
pages and tracks whether it has unsaved changes, and `DocumentStore` saves books
and the autosave/recovery file in the background (`BookCodec`, `DocumentCodec`,
`RecoveryCodec`). `DrawingStorage` chooses the library folder: shared
`Document/<folder>` on a Supernote (the folder its Files app shows) or
`Documents/<folder>` elsewhere, where `<folder>` is `BuildConfig.LIBRARY_FOLDER` (the app's name, from `AppName`),
once the app has file access, so drawings outlive the app,
otherwise its private files. At launch `DocumentStore.relocate` moves drawings
left in private storage into the shared library before the drawing loads. The
recovery file always stays private: Supernote Cloud syncs `Document/`, and syncing
a file rewritten every few seconds left conflict copies until autosave failed.
