// Builds docs/brush-lab/studio.html: Brush Studio, one self-contained page.
//   node docs/brush-lab/studio-src/build.cjs
// The engine is the Modular Brush Lab's, read from ../prototypes/modular-lab-src as it stands (never copied): its core
// files in the lab's build order, then any other engine file there (newmods.js and the like), so additions to the lab
// show up here when rebuilt. ui.js is the lab's page and is left out. The paper scans come from the lab's page, the
// typeface from the app's res/font (SIL OFL 1.1). Also writes studio-src/out/node-engine.cjs for the Node checks.
"use strict";
const fs = require("fs"), path = require("path");
const { LAB, engineFiles } = require("../engine-files.cjs");
const HERE = __dirname, REPO = path.join(HERE, "..", "..", "..");
const read = f => fs.readFileSync(f, "utf8");

// ---------- the engine: the lab's core, in its build order, then whatever else the lab adds (engine-files.cjs) ----------
const files = engineFiles();
let engine = files.map(f => "// ---- modular-lab-src/" + f + "\n" + read(path.join(LAB, f))).join("\n;\n");
// An older oldmodel.js took the app's name lists at build time.
const NAMES = path.join(REPO, "app", "src", "main", "assets", "names");
for (const [k, f] of [["ADJECTIVES", "adjectives"], ["PRODUCE", "produce"]])
  if (engine.includes("\"@@" + k + "@@\"")) engine = engine.replace("\"@@" + k + "@@\"", JSON.stringify(read(path.join(NAMES, f + ".txt"))));

// ---------- the paper scans, as the lab's page carries them ----------
const labPage = read(path.join(HERE, "..", "prototypes", "modular-lab.html"));
const tm = /const TEXTURE_SRC = \{[^\n]*\};/.exec(labPage);
if (!tm) throw new Error("TEXTURE_SRC not found in modular-lab.html");
const textures = tm[0];

// ---------- the typeface: iA Writer Duo 400 and 700, embedded ----------
const FONT = path.join(REPO, "app", "src", "main", "res", "font");
const face = (file, weight) => "@font-face { font-family: \"iA Writer Duo\"; font-style: normal; font-weight: " + weight + "; font-display: block; src: url(data:font/ttf;base64," +
  fs.readFileSync(path.join(FONT, file)).toString("base64") + ") format(\"truetype\"); }";
const fonts = face("ia_writer_duo_regular.ttf", 400) + "\n" + face("ia_writer_duo_bold.ttf", 700);
const ofl = read(path.join(REPO, "app", "src", "main", "assets", "fonts", "OFL.txt"));
const esc = t => t.replace(/[&<>]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;" }[c]));

// ---------- the page ----------
const studio = ["qr.js", "render.js", "studio.js"].map(f => read(path.join(HERE, f)));
// Brush export (export.js) with its Procreate template, the archive of a real brush (procreate-template.json, MIT).
const tpl = JSON.parse(read(path.join(HERE, "procreate-template.json")));
const exporter = "const PROCREATE_TEMPLATE = " + JSON.stringify({ source: tpl.source, license: tpl.license, archive: tpl.archive }) + ";\n" + read(path.join(HERE, "export.js"));
const parts = { FONTS: fonts, CSS: read(path.join(HERE, "studio.css")), TEXTURES: textures, ENGINE: "\"use strict\";\n" + engine,
  RENDER: "\"use strict\";\n" + studio[1], QR: "\"use strict\";\n" + studio[0], EXPORT: "\"use strict\";\n" + exporter, STUDIO: "\"use strict\";\n" + studio[2], OFL: esc(ofl),
  ENGINE_FILES: files.join(", "),
  // Last: its mark is inside studio.js. The owner's favourite brushes, one code a line.
  FAVOURITES: JSON.stringify(fs.existsSync(path.join(HERE, "..", "favourites.txt")) ? read(path.join(HERE, "..", "favourites.txt")).split("\n").map(l => l.trim()).filter(l => l && !l.startsWith("#")) : []) };
for (const [k, v] of Object.entries(parts)) if (/<\/script/i.test(v) || ((k === "CSS" || k === "FONTS") && /<\/style/i.test(v))) throw new Error("closing tag inside " + k);
let page = read(path.join(HERE, "page.html"));
for (const [k, v] of Object.entries(parts)) {
  const mark = "/*@@" + k + "@@*/";
  if (!page.includes(mark)) throw new Error("no mark " + mark);
  page = page.split(mark).join(k === "FAVOURITES" ? v + "||" : v);
}
if (/@@[A-Z_]+@@/.test(page)) throw new Error("unfilled mark " + /@@[A-Z_]+@@/.exec(page)[0]);
const out = path.join(HERE, "..", "studio.html");
fs.writeFileSync(out, page);
// The engine and the tile renderer for Node (no DOM): the checks load this.
fs.mkdirSync(path.join(HERE, "out"), { recursive: true });
fs.writeFileSync(path.join(HERE, "out", "node-engine.cjs"), "\"use strict\";\n" + engine + "\n;\n" + studio[1] + "\n;\n" + studio[0] + "\n;\n" + exporter +
  "\nmodule.exports = { TEXTURES, PROCREATE_TEMPLATE, writeBplist, procreateSet, procreateValues, exportGrain, SHAPE_PX, GRAIN_PX, switchChoice, studioJob, encodeM, readAnyCode, rollStyle, normBrush, cloneBrush, PARTS, CHOICES, DIALS, selfTest, qrMatrix, builtins, WILDCARD, STYLE_NAMES, plainWords, brushWords, sayWords, VOICE_MISSED };\n");
console.log("built " + path.relative(REPO, out) + " " + (page.length / 1024).toFixed(0) + " KB; engine: " + files.join(", "));
