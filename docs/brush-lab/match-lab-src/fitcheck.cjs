// node fitcheck.cjs <node-bundle.js> <tests dir> [name filter] [budget ms]
// Runs Match on every test photo in Node and prints the fit's numbers; writes <name>.fit.png (photo | fit) beside them.
const fs = require("fs"), path = require("path");
const K = require("./testkit.cjs");
const [bundle, dir, only, budget] = [path.resolve(process.argv[2]), path.resolve(process.argv[3]), process.argv[4] || "", +(process.argv[5] || 5000)];
const E = K.engine(bundle);
const truth = JSON.parse(fs.readFileSync(path.join(dir, "truth.json"), "utf8"));
const rows = [];
for (const name of Object.keys(truth)) {
  if (truth[name].kind === "paper" || (only && !name.includes(only))) continue;
  const g = K.readPNGGray(path.join(dir, name + ".png"));
  const t0 = Date.now(), photo = E.preparePhoto(g), job = E.analyzeStroke(photo, photo.crop), ta = Date.now() - t0;
  const r = E.fitNow(job, { budgetMs: budget, filter: process.env.ONLY ? st => st.medium === process.env.ONLY || st.tip === process.env.ONLY : undefined }), sc = r.score, F = job.F, G = sc.F;
  const st = r.struct, tr = truth[name];
  // The truth, drawn the same way: what the score is for the brush that really made it (engine photos only).
  let oracle = null;
  if (tr.code) { const B = E.readAnyCode(tr.code).b; oracle = E.scoreFit(job, E.renderFit(job, B, tr.size * tr.photoScale * job.scale, tr.shade), true); }
  rows.push({ name, ms: ta + r.ms, evals: r.evals, iou: sc.iou, width: sc.width, tone: sc.tone, spec: sc.spec, roughP: F.rough, roughF: G.rough, loss: r.loss,
    got: `${st.tip}${st.count ? st.count : ""}/${st.wetInk ? "wet ink" : st.medium}/${st.life}/${st.paper}`, want: tr.struct ? `${tr.struct.tip}/${tr.struct.medium}/${tr.struct.life}/${tr.struct.paper}` : tr.about,
    size: r.size, shade: r.shade, code: r.code });
  // photo | fit, side by side, at the work frame's scale.
  const w = job.w, h = job.h, px = new Uint8Array(w * 2 * h);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { px[y * 2 * w + x] = 255 * (1 - K.clamp(job.D[y * w + x])); px[y * 2 * w + w + x] = 255 * (1 - K.clamp(r.img[y * w + x])); }
  K.writePNG(path.join(dir, name + ".fit.png"), 2 * w, h, px, 1);
  const p = rows[rows.length - 1];
  console.log(`${name.padEnd(18)} ${p.got.padEnd(28)} want ${p.want}`);
  console.log(`   IoU ${p.iou.toFixed(3)}  width err ${(p.width * 100).toFixed(1)}%  tone EMD ${p.tone.toFixed(3)}  spec ${p.spec.toFixed(2)}  rough ${p.roughP.toFixed(2)}/${p.roughF.toFixed(2)}  loss ${p.loss.toFixed(2)}  size ${p.size} shade ${p.shade.toFixed(2)}  ${p.evals} evals ${p.ms} ms`);
  const ew = e => e.square > .65 ? "square" : e.square > .2 ? "round" : "tapered";
  console.log(`   ends photo ${job.ends.map(ew).join("/")} (${job.ends.map(e => e.square.toFixed(2)).join("/")}), fit ${sc.ends.map(ew).join("/")} (${sc.ends.map(e => e.square.toFixed(2)).join("/")}); shade ${r.shade.toFixed(2)}; paper texture ${(job.paper.texture * 100).toFixed(1)} %, ink ×${(1 / job.paper.b).toFixed(2)}, halo ${job.paper.haloReal}, halo band photo ${job.F.haloFrac.toFixed(3)} fit ${sc.F.haloFrac.toFixed(3)}`);
  console.log("   tried: " + r.tried.map(t => t.words + " " + t.loss.toFixed(1)).join(" | "));
  if (oracle) console.log(`   truth: IoU ${oracle.iou.toFixed(3)}  width err ${(oracle.width * 100).toFixed(1)}%  loss ${oracle.total.toFixed(2)} at size ${(tr.size * tr.photoScale * job.scale).toFixed(1)}`);
  console.log("   " + p.code + "   " + Object.entries(sc.parts).filter(([, v]) => v > .5).map(([k, v]) => k + " " + v.toFixed(1)).join(", "));
}
fs.writeFileSync(path.join(dir, "fitcheck.json"), JSON.stringify(rows, null, 1));
