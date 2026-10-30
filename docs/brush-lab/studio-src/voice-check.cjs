// The web lab's voice.js on facts the app gives (VoiceChecks.java): reads lines "facts<TAB>own words" on stdin and
// writes each brush's words (aboutWords) a line, so the app can see the two say every brush the same.
//   node docs/brush-lab/studio-src/voice-check.cjs < facts.tsv
"use strict";
const fs = require("fs"), path = require("path"), vm = require("vm");
const ctx = vm.createContext({});
vm.runInContext(fs.readFileSync(path.join(__dirname, "..", "prototypes", "modular-lab-src", "voice.js"), "utf8") + "\n;globalThis.aboutWords = aboutWords;", ctx);
const out = [];
for (const line of fs.readFileSync(0, "utf8").split("\n")) {
  if (!line) continue;
  const [facts, mine] = line.split("\t");
  out.push(ctx.aboutWords(facts, mine || ""));
}
process.stdout.write(out.join("\n") + "\n");
