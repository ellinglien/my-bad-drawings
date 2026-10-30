// Builds ../match-lab.html: page.html with the scripts inlined.
//  - The engine is the Modular Brush Lab's, read unchanged from ../prototypes/modular-lab-src with Brush Studio's file
//    list (../engine-files.cjs), and its paper scans from ../prototypes/modular-lab.html.
//  - The app's drawn tools and their codes, read from the app's source (DrawLayout.PICKER, ToolPresets.ROWS).
//  - The core (engine + imaging + analysis + fit + paper import) goes in one <script id="core">: the page runs it, and
//    the fit's Web Worker is made from the same text, so both draw with the same code.
// Also writes a Node bundle of the core (no textures, no UI) for the checks: node build.js [node-bundle path].
const fs = require("fs"), path = require("path");
const here = f => fs.readFileSync(path.join(__dirname, f), "utf8");
// The engine as Brush Studio builds it (../engine-files.cjs): the lab's files, the new modules (newmods.js) among them.
const { LAB, engineFiles } = require("../engine-files.cjs");
const engine = engineFiles().map(f => fs.readFileSync(path.join(LAB, f), "utf8"));
const mine = ["imaging.js", "analyze.js", "fit.js", "paper.js", "samples.js"].filter(f => fs.existsSync(path.join(__dirname, f))).map(here);
// The app's drawn tools (DrawLayout.PICKER, in its order) with their share codes (ToolPresets.ROWS), read from the app's
// source at build time, so the fit compares the photo with the tools as they stand.
const APP = path.join(__dirname, "..", "..", "..", "app", "src", "main", "java", "io", "github", "mpdairy", "monopaint");
const presets = fs.readFileSync(path.join(APP, "ToolPresets.java"), "utf8"), layout = fs.readFileSync(path.join(APP, "DrawLayout.java"), "utf8");
const rows = presets.slice(presets.indexOf("ROWS = {"), presets.indexOf("RETIRED = {"));
const codeOf = new Map([...rows.matchAll(/\{"[^"]+", "([^"]+)", "([^"]+)", "[^"]*", "\d+"\}/g)].map(m => [m[1], m[2]]));
const picker = [...layout.slice(layout.indexOf("PICKER = {")).split("};")[0].matchAll(/\{"([^"]+)", "tip_/g)].map(m => m[1]);
if (picker.length < 10 || picker.some(n => !codeOf.has(n))) throw new Error("DrawLayout.PICKER or ToolPresets.ROWS didn't read: " + picker.filter(n => !codeOf.has(n)));
const drawn = "const DRAWN_TOOLS = " + JSON.stringify(picker.map(n => [n, codeOf.get(n)])) + ";";
const core = "\"use strict\";\n" + [...engine, drawn, ...mine].join("\n;\n");
const exportsList = "rng, clamp, lerp, modSurface, blankPage, layStroke, Stroke, showPage, resolve, encodeM, readAnyCode, normBrush, freshPart, cloneBrush, CHOICES, DIALS, PARTS, TEXTURES, PAPER_TONE, Water, plainWords, choiceWords, freshName, newSeed, MSTYLES, STYLE_NAMES, rangeOf, dialDefault, switchChoice, brushWords, choiceWords, DRAWN_TOOLS"
  + (mine.length ? ", " + ["grayOf", "flatten", "otsu", "components", "edt", "gauss", "resample", "blur", "median", "quantile", "mean", "std"].join(", ") : "")
  + (fs.existsSync(path.join(__dirname, "analyze.js")) ? ", preparePhoto, analyzeStroke, measureMark, endShape, STEADY, roughExcess" : "")
  + (fs.existsSync(path.join(__dirname, "fit.js")) ? ", fitStroke, fitNow, renderFit, scoreFit, brushFromFit, cameraBlur, candidates, freeDials, FIT_TIPS, CATEGORIES" : "")
  + (fs.existsSync(path.join(__dirname, "paper.js")) ? ", makePaper, paperStats" : "")
  + ", makeSample, sampleBrush, SAMPLE_NAMES, SAMPLE_ENGINE, SAMPLE_DRAWN";
const out = process.argv[2] || path.join(__dirname, "node-bundle.js");
fs.writeFileSync(out, core + "\nmodule.exports = { " + exportsList + " };\n");
if (fs.existsSync(path.join(__dirname, "page.html"))) {
  const modular = fs.readFileSync(path.join(__dirname, "..", "prototypes", "modular-lab.html"), "utf8");
  const m = /const TEXTURE_SRC = \{[^\n]*?\};/.exec(modular);
  if (!m) throw new Error("no TEXTURE_SRC in modular-lab.html");
  const ui = here("ui.js");
  for (const [n, js] of [["core", core], ["textures", m[0]], ["ui", ui]]) if (/<\/script/i.test(js)) throw new Error("script close tag inside " + n);
  const page = here("page.html")
    .replace("/*@@CORE@@*/", () => core)
    .replace("/*@@TEXTURES@@*/", () => m[0])
    .replace("/*@@UI@@*/", () => "\"use strict\";\n" + ui);
  fs.writeFileSync(path.join(__dirname, "..", "match-lab.html"), page);
  console.log("built match-lab.html", (page.length / 1024).toFixed(0) + " KB");
}
console.log("node bundle", out);
