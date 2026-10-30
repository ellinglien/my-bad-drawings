// node papercheck.cjs <node-bundle.js> <paper photo.png> <out dir>
// Imports a paper photo in Node, prints its numbers next to the app's papers', writes the tile and a 2 × 2 repeat.
const fs = require("fs"), path = require("path");
const K = require("./testkit.cjs");
const [bundle, file, out] = [path.resolve(process.argv[2]), path.resolve(process.argv[3]), path.resolve(process.argv[4] || ".")];
const E = K.engine(bundle);
for (const [k, t] of Object.entries(E.TEXTURES)) { const s = E.paperStats(Uint8Array.from(t, v => Math.round(v * 255))); console.log(("app " + k).padEnd(16), `mean ${s.mean.toFixed(1)} spread ${s.spread.toFixed(1)} grain ${s.grain.toFixed(2)} px seam ${s.seam.toFixed(2)}`); }
const g = K.readPNGGray(file), t0 = Date.now(), r = E.makePaper(g), ms = Date.now() - t0, s = r.stats;
console.log("imported".padEnd(16), `mean ${s.mean.toFixed(1)} spread ${s.spread.toFixed(1)} grain ${s.grain.toFixed(2)} px seam ${s.seam.toFixed(2)}  (photo grain ${r.photoL.toFixed(2)} px, ×${r.k.toFixed(3)}, region ${r.region.s} px, ${ms} ms)`);
const base = path.join(out, path.basename(file, ".png"));
K.writePNG(base + ".tile.png", 256, 256, r.bytes, 1);
fs.writeFileSync(base + ".bin", Buffer.from(r.bytes));
const N = 256, rep = new Uint8Array(4 * N * N);
for (let y = 0; y < 2 * N; y++) for (let x = 0; x < 2 * N; x++) rep[y * 2 * N + x] = r.bytes[(y % N) * N + (x % N)];
K.writePNG(base + ".tile2x2.png", 2 * N, 2 * N, rep, 1);
