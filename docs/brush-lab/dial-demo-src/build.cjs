// Builds docs/brush-lab/dial-demo.html: Brush Lab Dials, one self-contained page, a tile for every part, choice and dial.
//   node docs/brush-lab/dial-demo-src/build.cjs
// As Brush Studio's build (studio-src/build.cjs): the engine is the Modular Brush Lab's, read from
// prototypes/modular-lab-src as it stands (engine-files.cjs, never copied), with Studio's tile drawing (studio-src/
// render.js), so the page can't fall behind the engine. The paper scans come from the lab's page, the typeface from the
// app's res/font (SIL OFL 1.1). Also writes dial-demo-src/out/node-engine.cjs for check.cjs.
"use strict";
const fs = require("fs"), path = require("path");
const { LAB, engineFiles } = require("../engine-files.cjs");
const HERE = __dirname, REPO = path.join(HERE, "..", "..", ".."), STUDIO = path.join(HERE, "..", "studio-src");
const read = f => fs.readFileSync(f, "utf8");

const files = engineFiles();
const engine = files.map(f => "// ---- modular-lab-src/" + f + "\n" + read(path.join(LAB, f))).join("\n;\n");
const tm = /const TEXTURE_SRC = \{[^\n]*\};/.exec(read(path.join(HERE, "..", "prototypes", "modular-lab.html")));
if (!tm) throw new Error("TEXTURE_SRC not found in modular-lab.html");
const FONT = path.join(REPO, "app", "src", "main", "res", "font");
const face = (file, weight) => "@font-face { font-family: \"iA Writer Duo\"; font-style: normal; font-weight: " + weight + "; font-display: block; src: url(data:font/ttf;base64," +
  fs.readFileSync(path.join(FONT, file)).toString("base64") + ") format(\"truetype\"); }";
const esc = t => t.replace(/[&<>]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;" }[c]));
const studioRender = read(path.join(STUDIO, "render.js")), render = read(path.join(HERE, "render.js")), tiles = read(path.join(HERE, "tiles.js"));

const parts = { FONTS: face("ia_writer_duo_regular.ttf", 400) + "\n" + face("ia_writer_duo_bold.ttf", 700), CSS: read(path.join(HERE, "demo.css")),
  TEXTURES: tm[0], ENGINE: "\"use strict\";\n" + engine, RENDER: "\"use strict\";\n" + studioRender + "\n;\n" + render,
  TILES: "\"use strict\";\n" + tiles, PAGE: "\"use strict\";\n" + read(path.join(HERE, "page.js")),
  OFL: esc(read(path.join(REPO, "app", "src", "main", "assets", "fonts", "OFL.txt"))), ENGINE_FILES: files.join(", ") };
for (const [k, v] of Object.entries(parts)) if (/<\/script/i.test(v) || ((k === "CSS" || k === "FONTS") && /<\/style/i.test(v))) throw new Error("closing tag inside " + k);
let page = read(path.join(HERE, "page.html"));
for (const [k, v] of Object.entries(parts)) {
  const mark = "/*@@" + k + "@@*/";
  if (!page.includes(mark)) throw new Error("no mark " + mark);
  page = page.split(mark).join(v);
}
if (/@@[A-Z_]+@@/.test(page)) throw new Error("unfilled mark " + /@@[A-Z_]+@@/.exec(page)[0]);
const out = path.join(HERE, "..", "dial-demo.html");
fs.writeFileSync(out, page);
fs.mkdirSync(path.join(HERE, "out"), { recursive: true });
fs.writeFileSync(path.join(HERE, "out", "node-engine.cjs"), "\"use strict\";\n" + engine + "\n;\n" + studioRender + "\n;\n" + render + "\n;\n" + tiles +
  "\nmodule.exports = { PARTS, CHOICES, DIALS, dialShown, dialLabel, demoTiles, tileJob, demoJob, withWords, DEMO_W, DEMO_H };\n");
console.log("built " + path.relative(REPO, out) + " " + (page.length / 1024).toFixed(0) + " KB; engine: " + files.join(", "));
