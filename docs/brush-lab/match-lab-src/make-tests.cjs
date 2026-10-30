// node make-tests.cjs <node-bundle.js> <out dir>
// Writes the sample photos (samples.js: engine strokes with their brushes, drawn marks) as PNGs, and truth.json.
// The paper photo for the import check is made by make-paper-photo.py (it starts from a JPEG scan).
const fs = require("fs"), path = require("path");
const K = require("./testkit.cjs");
const [bundle, out] = [path.resolve(process.argv[2]), path.resolve(process.argv[3] || ".")];
fs.mkdirSync(out, { recursive: true });
const E = K.engine(bundle), truth = {};
for (const name of E.SAMPLE_NAMES) {
  const s = E.makeSample(name);
  K.writePNG(path.join(out, name + ".png"), s.w, s.h, s.px);
  truth[name] = s.truth;
  console.log(name, s.w + "x" + s.h);
}
fs.writeFileSync(path.join(out, "truth.json"), JSON.stringify(truth, null, 1));
