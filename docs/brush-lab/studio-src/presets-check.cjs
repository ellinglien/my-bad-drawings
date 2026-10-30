// Every Basic tool's code (ToolPresets.ROWS) through the web engine: read it, write it again, compare.
//   node docs/brush-lab/studio-src/build.cjs && node docs/brush-lab/studio-src/presets-check.cjs [app.txt]
// A code must come back as written, but a Hand Hatch tip (cut from the app, Tip choice 12), which the app itself reads
// as Hatch and writes so (ModularCode.asHatch). With app.txt (the app's StudioCodeEcho over `presets-check.cjs list`,
// one line a code) each must come back exactly as the app writes it. Exits 1 on any that doesn't.
"use strict";
const fs = require("fs"), path = require("path"), E = require("./out/node-engine.cjs");
const java = path.join(__dirname, "..", "..", "..", "app", "src", "main", "java", "io", "github", "mpdairy", "monopaint", "ToolPresets.java");
const src = fs.readFileSync(java, "utf8"), rows = src.slice(src.indexOf("ROWS = {"), src.indexOf("};", src.indexOf("ROWS = {")));
const codes = [...rows.matchAll(/^\s*\{"[^"]*", "[^"]*", "([^"]+)"/gm)].map(m => m[1]);
if (codes.length < 100) throw new Error("only " + codes.length + " codes found in ToolPresets.ROWS");
if (process.argv[2] === "list") { process.stdout.write(codes.join("\n") + "\n"); process.exit(0); }
const app = process.argv[2] ? fs.readFileSync(process.argv[2], "utf8").trim().split("\n") : null;
if (app && app.length !== codes.length) throw new Error(app.length + " lines in " + process.argv[2] + ", " + codes.length + " codes");
let read = 0, same = 0, hatch = 0; const bad = [];
codes.forEach((c, i) => {
  const r = E.readAnyCode(c), back = r && E.encodeM(r.b);
  if (r) read++;
  if (back === c) same++;
  const cut = /\.M[0-9a-z]c/.test(c) && r && r.b.tip.k === "hatch";
  if (cut && back !== c) hatch++;
  if (app ? back !== app[i] : back !== c && !cut) bad.push(c + "\n   web: " + back + (app ? "\n   app: " + app[i] : ""));
});
console.log(`${codes.length} Basic tools: ${read} read, ${same} written back the same, ${hatch} Hand Hatch written as Hatch` +
  (app ? ", " + (codes.length - bad.length) + " as the app writes them" : "") + (bad.length ? "; " + bad.length + " wrong\n" + bad.join("\n") : ""));
process.exit(bad.length ? 1 : 0);
