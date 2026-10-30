// Codes for java-codes.sh: "write" prints Studio codes (every style rolled, and rolls over whole ranges as Anything goes
// makes them); "compare a b" checks the app wrote each one back the same.
"use strict";
const fs = require("fs"), E = require("./out/node-engine.cjs");
if (process.argv[2] === "write") {
  const keep = Math.random, out = [];
  let s = 20261016; Math.random = () => ((s = Math.imul(s ^ (s >>> 15), 0x2c1b3c6d) + 0x6d2b79f5 >>> 0) / 4294967296);
  for (let i = 0; i < 300; i++) out.push(E.encodeM(E.rollStyle(E.STYLE_NAMES[i % E.STYLE_NAMES.length])));
  for (let i = 0; i < 300; i++) {
    const B = E.rollStyle(E.WILDCARD);
    for (const part of E.PARTS) {
      const list = E.CHOICES[part].filter(c => c.k !== "custom"), c = list[(Math.random() * list.length) | 0];
      const P = { k: c.k, d: {} };
      // The hand's hidden dials (the app's Tilt Range and page tilt, which the lab doesn't draw) stay as set.
      for (const key of c.dials) { const sp = E.DIALS[part][key]; if (sp.hide && part === "hand") continue; P.d[key] = sp.choices ? (Math.random() * sp.choices.length) | 0 : sp.min + (sp.max - sp.min) * Math.random(); }
      B[part] = P;
    }
    out.push(E.encodeM(E.normBrush(B)));
  }
  for (const b of E.builtins()) out.push(E.encodeM(b));
  // The oil paint spec's: the app's Oil paint Basic tool (Filbert, Paint Load), and it as a Smear starting empty.
  out.push("oil-paint.M99vleei0000090005e_2zza300_2llp6zz00js_5at_1jz_1ss0000000000000000nz0.20l");
  { const B = E.readAnyCode("oil-paint.M99vleei0000090005e_2zza300_2llp6zz00js_5at_1jz_1ss0000000000000000nz0.20l").b; B.carry = { k: "smudge", d: { pload: .3, pmin: 0 } }; out.push(E.encodeM(E.normBrush(B))); }
  // A Stamp with its nib's id after the six, and one without.
  out.push("potato.M0ai0b60qe_000zz0000000000000_0_000_0_0000000000000000000nz0_12lvawr.h", "potato.M0ai0b60qe_000zz0000000000000_0_000_0_0000000000000000000nz0.h");
  // A Crayon at its defaults, and one with Scratch On, little wax, a chisel on cold press.
  out.push("wax-crayon.Ma0i000000000at_7ll0_0_000_1jz_0000000000000000000nz0.oju", "wax-crayon.Ma1okeei000000000at_7771_0_1i0_1jz_0000000000000000000nz0.oju");
  // A Spiro in a ring (a rose, speed setting its petals), and along the line (a coil, speed its loops, pressure nothing).
  out.push("rosette.M0bi012gj08huug01i08r_000zz0000000000000_0_000_0_0000000000000000000nz0000000004.h", "coil.M0bi005gj08huug01i08r_000zz0000000000000_0_000_0_0000000000000000000nz0000000051.h");
  // Grey Shift: a Pattern over Any ping-ponging, part of its set left out, and Random over part of the toolbar.
  { const B = E.readAnyCode("rosette.M0bi012gj08huug01i08r_000zz0000000000000_0_000_0_0000000000000000000nz0000000004.h").b;
    B.hand.d = Object.assign(B.hand.d, { shift: 3, sorder: 1, sgreys: 1, sany: 0x81, sany2: 0xc0 }); out.push(E.encodeM(E.normBrush(B)));
    B.tip = { k: "round", d: {} }; B.hand.d = Object.assign(B.hand.d, { shift: 1, sorder: 0, sgreys: 0, sbar: 5, sany: 1, sany2: 0 }); out.push(E.encodeM(E.normBrush(B)));
    // Gradient: a To Grey to 60% over 7 cm with Grey Shift on, a Cycle over Any with Grey Shift off, a Fade at its Length.
    B.hand.d = Object.assign(B.hand.d, { grad: 2, gto: 9, glen: 7 }); out.push(E.encodeM(E.normBrush(B)));
    B.hand.d = Object.assign(B.hand.d, { shift: 0, sgreys: 1, sany: 0x81, grad: 3, gto: 5, glen: 3 }); out.push(E.encodeM(E.normBrush(B)));
    B.medium = { k: "wash", d: {} }; B.hand.d = Object.assign(B.hand.d, { sgreys: 0, sany: 1, grad: 1, glen: 10 }); out.push(E.encodeM(E.normBrush(B))); }
  // Stay in Lines: a watercolour at Flood's default, and one that fills (Smudge choice 5).
  for (const flood of [.35, 1]) { const B = E.readAnyCode("wax-crayon.Ma0i000000000at_7ll0_0_000_1jz_0000000000000000000nz0.oju").b; B.medium = { k: "wash", d: {} }; B.carry = { k: "stayin", d: { flood } }; out.push(E.encodeM(E.normBrush(B))); }
  // Filled Shape (Smudge choice 6): the app's Filled shape Basic tool, and a crayon that fills.
  out.push("filled-shape.M30wd00000000at_1atat00000000_6_000_0_0000000000000000000nz0.2fi");
  { const B = E.readAnyCode("wax-crayon.Ma0i000000000at_7ll0_0_000_1jz_0000000000000000000nz0.oju").b; B.carry = { k: "shape", d: {} }; out.push(E.encodeM(E.normBrush(B))); }
  // Tilt Shading: the 2H pencil preset (the hand's last dial), and it half shaded.
  out.push("h-pencil.M40i000000000at_48n00_0_2s8_1jz_87nge00000000000000nz000000000530000005000c099zz.51j8ud");
  { const B = E.readAnyCode("h-pencil.M40i000000000at_48n00_0_2s8_1jz_87nge00000000000000nz000000000530000005000c099zz.51j8ud").b; B.hand.d.tshade = .5; out.push(E.encodeM(E.normBrush(B))); }
  Math.random = keep;
  process.stdout.write(out.join("\n") + "\n");
} else {
  // A code the app writes back differently is explained when the app doesn't have the lab's newer dials and choices
  // yet (Erasable, Blend, Stipple, Hatch, Resist, Halo): it reads them as their defaults, or not at all. Anything else
  // is a failure.
  const a = fs.readFileSync(process.argv[3], "utf8").trim().split("\n"), b = fs.readFileSync(process.argv[4], "utf8").trim().split("\n");
  const NEW = { tip: ["stipple", "hatch"], medium: ["resist"], life: ["halo"] };
  const usesNew = B => Object.entries(NEW).some(([p, ks]) => ks.includes(B[p].k));
  const plain = B => { const c = E.cloneBrush(B); if ("erase" in c.medium.d) c.medium.d.erase = 1; if ("blend" in c.medium.d) c.medium.d.blend = 0; return E.encodeM(E.normBrush(c)); };
  let same = 0, labOnly = 0, newOnly = 0; const bad = [];
  a.forEach((c, i) => {
    if (c === b[i]) { same++; return; }
    const mine = E.readAnyCode(c).b;
    if (b[i] === "null") { if (usesNew(mine)) newOnly++; else bad.push(c + "\n  app: null"); return; }
    const app = E.readAnyCode(b[i]);
    if (app && plain(app.b) === plain(mine)) labOnly++; else bad.push(c + "\n  app: " + b[i]);
  });
  console.log(`${a.length} codes: ${same} read and written back the same by the app; ${labOnly} the same but for Erasable or Blend, ` +
    `${newOnly} with a choice the app doesn't have yet; ${bad.length} wrong` + (bad.length ? "\n" + bad.slice(0, 5).join("\n") : ""));
  process.exit(bad.length ? 1 : 0);
}
