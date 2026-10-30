// ===================================================================================================================
// A brush in friendly words (the app's BrushVoice, the owner's 2026-10-22 brief: "explain what makes the brush unique,
// what to expect", "complete sentences", "human sounding", "don't use semicolons"). plainWords keeps the facts, short and
// checked; brushWords says them as a person would, in complete sentences, what matters most first, at most four. The
// rows of VOICE_RULES are BrushVoice.RULES', row for row (VoiceChecks.java checks they stay the same). This brush and
// Brush Studio show brushWords.
// ===================================================================================================================
// Rank (lower first), kind (S a whole sentence, P what "It" does, two joined), the fact as a pattern, the words, a tag.
const VOICE_RULES = [
  ["1", "S", "Builds up where it lingers", "Dots pile up where you linger.", ""],
  ["1", "S", "Stamps a shape cut from the page", "It stamps a shape you cut from the page.", ""],
  ["1", "S", "Fades as it prints", "Each print comes out a little fainter.", ""],
  ["1", "S", "Builds up, resists washes", "The wax builds up in layers and pushes washes away.", ""],
  ["1", "S", "Builds up", "The wax builds up in layers.", ""],
  ["1", "S", "Scrapes back to white", "It scrapes back through to white paper.", ""],
  ["1", "S", "Loops along the line", "It draws toy loops along your line.", ""],
  ["1", "S", "Loops round a ring pulled out from the start", "It loops around a ring you pull out from where you start.", ""],
  ["1", "S", "Crosses where gone over or held", "Go back over a spot, or hold still, and the hatching crosses.", ""],
  ["1", "S", "Greys shift each stroke at random", "Each stroke picks a new grey at random.", ""],
  ["1", "S", "Greys shift each stroke, shuffled", "Each stroke takes the next grey from a shuffled set.", ""],
  ["1", "S", "Greys shift each stroke, there and back", "Each stroke steps to the next grey, then back again.", ""],
  ["1", "S", "Greys shift each stroke, in order", "Each stroke steps to the next grey in order.", ""],
  ["1", "S", "Fades to paper over (.+) cm", "It fades out to bare paper over $1 cm.", ""],
  ["1", "S", "Shifts to (.+) over (.+) cm", "It shifts to $1 over $2 cm.", ""],
  ["1", "S", "Cycles through its greys every (.+) cm", "It cycles through its greys every $1 cm.", ""],
  ["1", "S", "Flicks on a quick lift, flings drops on a hard stop", "Lift quickly and it flicks. Stop hard and it flings drops.", ""],
  ["1", "S", "Flicks on a quick lift", "Lift quickly and it flicks.", ""],
  ["1", "S", "Flings drops on a hard stop", "Stop hard and it flings drops.", ""],
  ["1", "S", "Nothing takes on it", "Nothing painted on top will stick to it.", ""],
  ["1", "S", "Nothing takes on it until rubbed off", "Nothing sticks to it until you rub it off with the eraser.", ""],
  ["1", "S", "Scrapes off in streaks", "The eraser scrapes it off in streaks.", ""],
  ["1", "S", "Goes behind marks", "It slips behind the marks already there.", ""],
  ["1", "S", "Inverts what's under it", "It flips the greys under it, dark to light.", ""],
  ["1", "S", "Taps drop clear rings, dragging combs them", "Tap to drop clear rings, then drag to comb them.", ""],
  ["1", "S", "Taps drop clear rings, dragging combs them a little", "Tap to drop clear rings, then drag to comb them a little.", ""],
  ["1", "S", "Floats (.+) s", "It floats on the water for $s.", ""],
  ["1", "S", "Taps drop rings, dragging combs them", "Tap to drop rings of ink, then drag to comb them.", ""],
  ["1", "S", "Taps drop rings", "Tap to drop rings of ink.", ""],
  ["1", "S", "Veins bridge nearby marks for (.+) s", "Mold veins grow out to bridge nearby marks for $s.", ""],
  ["1", "S", "Fades away in (.+) s", "It fades away after $s.", ""],
  ["1", "S", "Darkens in as it dries", "It goes on faint and darkens as it dries.", ""],
  ["1", "S", "Holds ink between strokes", "It holds its ink between strokes.", ""],
  ["1", "S", "Tap a grey to dip", "Tap a grey to dip it in fresh ink.", ""],
  ["1", "S", "Runs out of paint, then only smudges", "It runs out of paint as you go, then only smudges.", ""],
  ["1", "S", "Rub a grey to load", "Rub a grey to load it up again.", ""],
  ["1", "S", "Stays inside ink lines", "It stays inside your ink lines.", ""],
  ["1", "S", "Floods inside ink lines", "It floods up to your ink lines.", ""],
  ["1", "S", "Fills the shape it starts in", "It fills the whole shape it starts in.", ""],
  ["1", "S", "Fills the shape it outlines", "When you lift, it closes the outline and fills it in.", ""],
  ["2", "S", "Stays wet until dried", "It stays wet until you tap the drop to dry it.", ""],
  ["2", "S", "Merges while wet, blooms when damp", "Wet strokes mix together and bloom as they dry.", "bloom"],
  ["2", "S", "Merges while wet", "Wet strokes mix together.", ""],
  ["2", "S", "Wet for (.+) s", "It stays wet for $s.", ""],
  ["2", "S", "(runs|drips|pools|granulates|blends when rocked)( and (runs|drips|pools|granulates|blends when rocked))? when tilted", "@tilt", "tilt"],
  ["2", "S", "Lifts paint, lets it run", "It lifts paint and lets it run.", ""],
  ["2", "S", "Lifts paint", "It lifts paint off the page.", ""],
  ["2", "S", "Drags wet paint", "It drags wet paint along with it.", ""],
  ["2", "S", "Smears", "It smears whatever it passes over.", ""],
  ["2", "S", "Softens", "It softens marks without adding any paint.", ""],
  ["2", "S", "Combs", "It combs through wet paint.", ""],
  ["2", "S", "Keeps paint for the next stroke", "It keeps some paint for the next stroke.", ""],
  ["4", "P", "tapers to a point", "tapers to a fine point", ""],
  ["4", "P", "dry-brush texture", "has a scratchy, dry-brush texture", ""],
  ["4", "P", "dry-brush streaks", "leaves streaky brush marks", ""],
  ["4", "P", "slight wobble", "wobbles a little", ""],
  ["4", "P", "dark edges", "dries with dark edges", ""],
  ["4", "P", "pools when slow", "pools where you slow down", ""],
  ["4", "P", "spatters on flicks", "spatters when you flick", ""],
  ["4", "P", "blooms", "blooms as it dries", "bloom"],
  ["4", "P", "backruns", "leaves pale backruns", ""],
  ["4", "P", "bleeds", "bleeds softly into the paper", ""],
  ["4", "P", "raised ridges", "builds up raised ridges", ""],
  ["4", "P", "dusty broken edge", "has a dusty, broken edge", ""],
  ["4", "P", "soft speckled edge", "has a soft, speckled edge", ""],
  ["4", "P", "line shifted", "lands a little off the line", ""],
  ["4", "P", "broken line", "breaks up here and there", ""],
  ["4", "P", "opens into two lines", "opens into two lines", ""],
  ["4", "P", "splits into strands", "splits into strands", ""],
  ["4", "P", "runs dry quickly", "runs dry quickly", ""],
  ["4", "P", "runs dry on long strokes", "runs dry on long strokes", ""],
  ["4", "P", "picks up the texture", "catches the paper's grain", ""],
  ["4", "S", "sheds dust when tilted", "Tilt the page and dust falls off it.", ""],
  ["4", "P", "doubled", "prints a little doubled", ""],
  ["4", "P", "dots swell", "prints fat, swollen dots", ""],
  ["4", "P", "patchy ink", "prints with patchy ink", ""],
  ["5", "S", "Dries at once", "It dries as soon as you lift the pen.", ""],
  ["5", "S", "Permanent", "The eraser can't touch it.", ""],
  ["5", "S", "Part erasable", "The eraser only lightens it.", ""],
  ["5", "S", "Erasable", "", ""],
];
const VOICE_PATTERNS = VOICE_RULES.map(r => new RegExp("^" + r[2] + "$"));
const VOICE_MOST_SENTENCES = 4, VOICE_MOST = 230, VOICE_HAND = 3, VOICE_OPENER_MOST = 70, VOICE_TWO_SENTENCES = 150, VOICE_ABOUT_MOST = 260, VOICE_STEADY = "It stays the same however hard you press or how fast you go.";
const voiceLower1 = s => s ? s.charAt(0).toLowerCase() + s.slice(1) : s, voiceCap1 = s => s ? s.charAt(0).toUpperCase() + s.slice(1) : s;
const voiceSentences = t => t.trim().split(/(?<=\.)\s+/).filter(Boolean);
const voiceStrip = s => s.endsWith(".") ? s.slice(0, -1) : s;
// "about 6 seconds", "1 second": a time the facts give in seconds, to the nearest whole one.
function voiceSeconds(s) { const n = Math.max(1, Math.round(parseFloat(s))); return (n < 10 ? "about " : "") + n + (n === 1 ? " second" : " seconds"); }
const VOICE_TILT = { runs: "runs", drips: "drips", pools: "pools", granulates: "settles into grain", "blends when rocked": "blends as you rock it" };
function voiceTilt(first, second) {
  const last = second || first, downhill = last === "runs" || last === "drips" || last === "pools";
  return "Tilt the page and it " + VOICE_TILT[first] + (second ? " and " + VOICE_TILT[second] : "") + (downhill ? " downhill" : "") + ".";
}
function voiceRule(fact) {
  const f = voiceLower1(fact);
  for (let i = 0; i < VOICE_RULES.length; i++) {
    const m = VOICE_PATTERNS[i].exec(f) || VOICE_PATTERNS[i].exec(fact);
    if (!m) continue;
    let words = VOICE_RULES[i][3];
    if (words === "@tilt") words = voiceTilt(m[1], m[3]);
    else if (words.includes("$s")) words = words.replace("$s", voiceSeconds(m[1]));
    else for (let g = m.length - 1; g >= 1; g--) words = words.split("$" + g).join(m[g] == null ? "" : m[g]);
    return [VOICE_RULES[i][0], words, VOICE_RULES[i][1], VOICE_RULES[i][4]];
  }
  return null;
}
const VOICE_MISSED = new Set();
const VOICE_NOUN = /^(.+?)(?:, (chisel tip|flat nib|hake|split nib|\d+-line rake|bristle brush|filbert|round tip))?,?(?: on (.+))?$/;
// What the facts' noun says it is, friendlier, with "a" or "an" where it takes one (BrushVoice.nounPhrase).
function voiceNounPhrase(noun) {
  let n = noun.replace(/ stipple$/, " stippling").replace(/ spiro$/, " spirograph").replace(/^(Vine|Compressed) charcoal$/, "stick of $1 charcoal");
  const grade = /^(.*?)(\S+) graphite$/.exec(n);
  if (grade) n = grade[1] + (/^\d*[bhfBHF]+$/.test(grade[2]) ? grade[2].toUpperCase() : grade[2]) + " pencil";
  const first = n.split(" ")[0];
  if (!(first === "Ben-Day" || first === first.toUpperCase())) n = voiceLower1(n);
  const counted = /(line|wash|stamp|spirograph|scratch|pencil|pastel|crayon)$|^stick of/.test(n) && !n.endsWith(" and wash");
  const an = "aeiou8".includes(n.charAt(0)) || /^[FHLMNRSX][A-Z0-9 ]/.test(n);
  return (!counted ? "" : an ? "an " : "a ") + n.replace("stick of Vine", "stick of vine").replace("stick of Compressed", "stick of compressed");
}
// The facts' first sentence as a sentence: "It's an ink line with a chisel tip, on felt paper."
function voiceNoun(fact) {
  const m = VOICE_NOUN.exec(fact);
  if (!m) { VOICE_MISSED.add(fact); return "It's " + voiceLower1(fact) + "."; }
  const tip = m[2] ? " with a " + m[2] : "", on = m[3] ? (tip ? "," : "") + " on " + m[3] : "";
  return "It's " + voiceNounPhrase(m[1]) + tip + on + ".";
}
// What stands out, for the opener, most telling first (BrushVoice.OPENERS, row for row): the fact, after "that", after a comma.
const VOICE_OPENERS = [
  ["Stamps a shape cut from the page", "stamps a shape you cut from the page", "stamping a shape you cut from the page"],
  ["Loops along the line", "spins out toy loops as you draw", "spinning out toy loops as you draw"],
  ["Loops round a ring pulled out from the start", "spins loops around a ring you pull out", "spinning loops around a ring you pull out"],
  ["Crosses where gone over or held", "crosshatches wherever you go back", "crosshatching wherever you go back"],
  ["Builds up, resists washes", "lays down waxy layers", "laying down waxy layers"],
  ["Builds up", "lays down waxy layers", "laying down waxy layers"],
  ["Scrapes back to white", "scratches back to white", "scratching back to white"],
  ["Builds up where it lingers", "piles up dots where you linger", "piling up dots where you linger"],
  ["Nothing takes on it(?: until rubbed off)?", "keeps washes off the paper", "keeping washes off the paper"],
  ["Goes behind marks", "slides under your lines", "sliding under your lines"],
  ["Inverts what's under it", "flips the greys under it", "flipping the greys under it"],
  ["Taps drop clear rings, dragging combs them(?: a little)?", "pushes floating ink into rings", "pushing floating ink into rings"],
  ["Floats (.+) s", "floats in rings", "floating in rings"],
  ["Veins bridge nearby marks for (.+) s", "grows little mold veins", "growing little mold veins"],
  ["Fades away in (.+) s", "slowly fades away", "slowly fading away"],
  ["Darkens in as it dries", "shows up as it dries", "showing up as it dries"],
  ["Holds ink between strokes", "keeps its ink from stroke to stroke", "keeping its ink from stroke to stroke"],
  ["Runs out of paint, then only smudges", "runs out of paint as you go", "running out of paint as you go"],
  ["Stays inside ink lines", "stays inside your lines", "staying inside your lines"],
  ["Floods inside ink lines", "floods up to your lines", "flooding up to your lines"],
  ["Fills the shape it starts in", "fills whole shapes", "filling whole shapes"],
  ["Fills the shape it outlines", "fills in the shapes you draw", "filling in the shapes you draw"],
  ["Flicks on a quick lift, flings drops on a hard stop", "flicks and flings when you're quick", "flicking and flinging when you're quick"],
  ["Flicks on a quick lift", "flicks when you lift fast", "flicking when you lift fast"],
  ["Flings drops on a hard stop", "flings drops when you stop hard", "flinging drops when you stop hard"],
  ["Greys shift each stroke.*", "changes grey every stroke", "changing grey every stroke"],
  ["(?:Fades to paper|Shifts to .+|Cycles through its greys) (?:over|every) .+ cm", "changes grey along the stroke", "changing grey along the stroke"],
  ["Stays wet until dried", "stays wet until you dry it", "staying wet until you dry it"],
  ["Wet for (.+) s", "stays wet for a while", "staying wet for a while"],
  ["sheds dust when tilted", "leaves dust in its wake", "leaving dust in its wake"],
  ["blooms", "blooms as it dries", "blooming as it dries"],
  ["backruns", "leaves pale backruns", "leaving pale backruns"],
  ["bleeds", "bleeds into the paper", "bleeding into the paper"],
  ["splits into strands", "splits into hairy strands", "splitting into hairy strands"],
  ["runs dry (?:quickly|on long strokes)", "runs dry into streaks", "running dry into streaks"],
  ["opens into two lines", "opens into two lines", "opening into two lines"],
  ["(?:runs|drips|pools)(?: and \\w+)? when tilted", "runs when you tilt the page", "running when you tilt the page"],
  ["(?:granulates|blends when rocked)(?: and .+)? when tilted", "moves when you tilt the page", "moving when you tilt the page"],
  ["Merges while wet(?:, blooms when damp)?", "mixes while it's wet", "mixing while it's wet"],
  ["Lifts paint, lets it run", "lifts paint and lets it run", "lifting paint and letting it run"],
  ["Lifts paint", "lifts paint off the page", "lifting paint off the page"],
  ["Drags wet paint", "drags wet paint along", "dragging wet paint along"],
  ["Softens", "softens what's there", "softening what's there"],
  ["Combs", "combs through wet paint", "combing through wet paint"],
  ["Smears", "smears what's there", "smearing what's there"],
  ["Keeps paint for the next stroke", "never quite comes clean", "never quite coming clean"],
  ["dots swell|doubled|patchy ink", "prints like old newsprint", "printing like old newsprint"],
  ["spatters on flicks", "spatters when flicked", "spattering when flicked"],
  ["pools when slow", "pools when you slow down", "pooling when you slow down"],
  ["raised ridges", "builds up raised ridges", "building up raised ridges"],
  ["dusty broken edge", "has a dusty, broken edge", "showing a dusty, broken edge"],
  ["tapers to a point", "tapers to a fine point", "tapering to a fine point"],
  ["dry-brush streaks", "leaves streaky marks", "leaving streaky marks"],
  ["dry-brush texture", "skips and scratches", "skipping and scratching"],
  ["picks up the texture", "catches the paper's grain", "catching the paper's grain"],
  ["soft speckled edge", "has a soft, speckled edge", "showing a soft, speckled edge"],
  ["dark edges", "dries with dark edges", "drying with dark edges"],
  ["slight wobble", "wobbles a little", "wobbling a little"],
  ["line shifted|broken line", "breaks up here and there", "breaking up here and there"],
];
const VOICE_OPENER_PATTERNS = VOICE_OPENERS.map(r => new RegExp("^(?:" + r[0] + ")$"));
const VOICE_HAND_START = /^(Pressure|Tilt|Speed|Downhill|Uphill)\b/;
// FNV-1a over the facts' chars: a brush's own pick of the opener's shape (BrushVoice.hash).
function voiceHash(s) { let h = 0x811c9dc5; for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); } return h >>> 0; }
const voiceOpens = u => VOICE_OPENER_PATTERNS.findIndex(p => p.test(voiceLower1(u)) || p.test(u));
function voiceUnits(sentences) {
  const out = [];
  for (let i = 1; i < sentences.length; i++) {
    const s = voiceStrip(sentences[i]);
    if (VOICE_HAND_START.test(s)) continue;
    if (s.includes(", ") && voiceOpens(s) >= 0) { out.push(s); continue; }
    out.push(...s.split(", "));
  }
  return out;
}
function voiceShaped(shape, what, rows) {
  if (!rows.length) return shape === 0 ? voiceCap1(what) + ", plain and simple." : shape === 1 ? "Here's " + what + ", nothing fancy."
    : shape === 2 ? "This is " + what + ", no surprises." : "Meet " + what + ".";
  const rel = VOICE_OPENERS[rows[0]][1] + (rows.length > 1 ? " and " + VOICE_OPENERS[rows[1]][1] : "");
  const ing = VOICE_OPENERS[rows[0]][2] + (rows.length > 1 ? " and " + VOICE_OPENERS[rows[1]][2] : "");
  return shape === 0 ? voiceCap1(what) + " that " + rel + "." : shape === 1 ? "Here's " + what + " that " + rel + "."
    : shape === 2 ? "This is " + what + ", " + ing + "." : "Meet " + what + ", " + ing + ".";
}
// The opener: what it is and the one or two things that stand out most, in one of four shapes (BrushVoice.opener).
function voiceOpener(facts, used, most = VOICE_OPENER_MOST) {
  const sentences = voiceSentences(facts);
  if (!sentences.length) return "";
  const m = VOICE_NOUN.exec(voiceStrip(sentences[0]));
  const noun = m ? voiceNounPhrase(m[1]) : voiceLower1(voiceStrip(sentences[0]));
  const tip = m && m[2] ? " with a " + m[2] : "", on = m && m[3] ? " on " + m[3] : "";
  const units = voiceUnits(sentences), picked = [], rows = [];
  for (let i = 0; i < VOICE_OPENERS.length && rows.length < 2; i++)
    for (const u of units)
      if (!picked.includes(u) && (VOICE_OPENER_PATTERNS[i].test(voiceLower1(u)) || VOICE_OPENER_PATTERNS[i].test(u))) { picked.push(u); rows.push(i); break; }
  const shape = voiceHash(facts) % 4;
  const tries = [["2", on, tip], ["1", on, tip], ["1", "", tip], ["1", "", ""], ["0", on, tip], ["0", "", tip], ["0", "", ""]];
  for (let lead = 0; lead < 2; lead++)
    for (const t of tries) {
      const n = Math.min(+t[0], rows.length);
      if (n === 0 && rows.length && t[0] === "0" && lead === 0) continue;
      const s = voiceShaped(lead === 0 ? shape : 0, noun + t[2] + t[1], rows.slice(0, n));
      if (s.length <= most) { used.push(...picked.slice(0, n)); return s; }
    }
  return voiceCap1(noun) + ".";
}
// The facts without what the opener said (BrushVoice.without).
function voiceWithout(facts, used) {
  for (const u of used.slice()) if (voiceLower1(u) === "blooms") used.push("blooms when damp");
  const sentences = voiceSentences(facts); let out = "";
  for (let i = 0; i < sentences.length; i++) {
    let s = voiceStrip(sentences[i]);
    if (i > 0 && !VOICE_HAND_START.test(s)) {
      if (used.includes(s)) continue;
      const keep = s.split(", ").filter(p => !used.includes(p));
      if (!keep.length) continue;
      s = voiceCap1(keep.join(", "));
    }
    out += (out ? " " : "") + s + ".";
  }
  return out;
}
const VOICE_CAUSE = { pressure: "Pressing harder", tilt: "Tilting the pen", speed: "Moving faster", downhill: "Tilting the page", uphill: "Tilting the page" };
const VOICE_MAKES = {}, VOICE_SETS = { size: "the loops' size", loops: "how many loops there are", wheel: "the wheel", hole: "the hole in the middle", petals: "the petals", nothing: "nothing" };
for (const [one, two, made] of [["widens", "widen", "it wider"], ["darkens", "darken", "it darker"], ["lightens", "lighten", "it lighter"],
  ["thins", "thin", "it thinner"], ["thickens", "thicken", "it thicker"], ["broadens", "broaden", "it broader"],
  ["grows dots", "grow dots", "the dots bigger"], ["shrinks dots", "shrink dots", "the dots smaller"],
  ["widens nib", "widen nib", "the nib wider"], ["opens nib", "open nib", "the nib open"],
  ["opens tines", "open tines", "the tines spread"], ["spreads lines", "spread lines", "the lines spread"], ["turns nib", "turn nib", "the nib turn"]]) { VOICE_MAKES[one] = made; VOICE_MAKES[two] = made; }
function voiceMakes(effect, cause) {
  let made = VOICE_MAKES[effect];
  if (made == null) { VOICE_MISSED.add(effect); made = "it " + effect; }
  return made + (cause === "downhill" ? " downhill" : cause === "uphill" ? " uphill" : "");
}
function voiceSets(what) { let s = VOICE_SETS[what]; if (s == null) { VOICE_MISSED.add(what); s = "the " + what; } return s; }
function voiceClause(f) {
  const two = /^(\w+) and (\w+) (.+)$/.exec(f);
  if (two && VOICE_CAUSE[two[1]] && VOICE_CAUSE[two[2]]) {
    let both = voiceLower1(VOICE_CAUSE[two[1]]) + " and " + voiceLower1(VOICE_CAUSE[two[2]]);
    if (two[1] === "downhill" || two[2] === "downhill" || VOICE_CAUSE[two[1]] === VOICE_CAUSE[two[2]]) both = voiceLower1(VOICE_CAUSE[two[1]]);
    const what = two[3];
    if (what.startsWith("set ")) return both + " both change " + voiceSets(what.slice(4));
    return both + " both make " + voiceMakes(what, two[1]);
  }
  const space = f.indexOf(" "), cause = f.slice(0, space), what = f.slice(space + 1);
  const who = VOICE_CAUSE[cause] ? voiceLower1(VOICE_CAUSE[cause]) : cause;
  if (what.startsWith("sets ")) return who + " changes " + voiceSets(what.slice(5));
  const effects = what.split(" and ");
  let made = voiceMakes(effects[0], cause);
  if (effects.length > 1) {
    const second = voiceMakes(effects[1], cause);
    made = made.startsWith("it ") && second.startsWith("it ") ? made + " and " + second.slice(3)
      : made.replace(/^it /, "the line ") + " and " + second.replace(/^it /, "the line ");
  }
  return who + " makes " + made;
}
// Control's facts ("Pressure widens, speed thins") as a sentence: "Pressing harder makes it wider, and moving faster makes it thinner."
function voiceHand(fact) {
  const f = voiceLower1(fact), pair = f.split(", ");
  if (pair.length === 2) return voiceCap1(voiceClause(pair[0])) + ", and " + voiceClause(pair[1]) + ".";
  return voiceCap1(voiceClause(f)) + ".";
}
// The facts (plainWords) in friendly sentences, what matters most first; noun: start with what it is.
function sayWords(facts, noun = true) {
  const sentences = voiceSentences(facts), all = [];
  if (noun && sentences.length) all.push({ rank: 0, order: 0, words: voiceNoun(voiceStrip(sentences[0])), tag: "" });
  for (let i = 1; i < sentences.length; i++) {
    const s = voiceStrip(sentences[i]);
    if (VOICE_HAND_START.test(s)) { all.push({ rank: VOICE_HAND, order: all.length, words: voiceHand(s), tag: "" }); continue; }
    const whole = s.includes(", ") ? voiceRule(s) : null;
    if (whole) { if (whole[1]) all.push({ rank: +whole[0], order: all.length, words: whole[1], tag: whole[3] }); continue; }
    const does = [], doTags = []; let rank = 9;
    for (const part of s.split(", ")) {
      let r = voiceRule(part);
      if (!r) { VOICE_MISSED.add(part); r = ["4", "It " + voiceLower1(part) + ".", "S", ""]; }
      if (!r[1]) continue;
      if (r[2] === "P") { does.push(r[1]); doTags.push(r[3]); rank = Math.min(rank, +r[0]); }
      else all.push({ rank: +r[0], order: all.length, words: r[1], tag: r[3] });
    }
    if (does.length) all.push({ rank, order: all.length, words: null, tag: "", does, doTags });
  }
  // Nothing past what it is: Control is steady, the drying Quick, nothing unusual. Say so.
  if (all.length === (noun && sentences.length ? 1 : 0)) all.push({ rank: VOICE_HAND, order: all.length, words: VOICE_STEADY, tag: "" });
  const order = all.slice().sort((a, b) => a.rank !== b.rank ? a.rank - b.rank : a.order - b.order), tags = new Set();
  let out = "", count = 0;
  for (const s of order) {
    if (s.tag && tags.has(s.tag)) continue;
    if (s.tag) tags.add(s.tag);
    let words = s.words;
    if (s.does) {
      const left = s.does.filter((d, k) => { const g = s.doTags[k]; if (!g) return true; if (tags.has(g)) return false; tags.add(g); return true; });
      if (!left.length) continue;
      words = "It " + left.join(" and ") + ".";
    }
    const n = voiceSentences(words).length;
    if (count > 0 && (count + n > VOICE_MOST_SENTENCES || out.length + 1 + words.length > VOICE_MOST)) continue;
    out += (out ? " " : "") + words; count += n;
  }
  return out;
}
// The brush in words (BrushVoice.about): mine (a special tool's own sentences) or the opener, then how it behaves,
// leaving out what the opener said, while it fits.
function aboutWords(facts, mine = "") {
  const used = [];
  let lead = mine || voiceOpener(facts, used), behaves = sayWords(voiceWithout(facts, used), false);
  // The opener and the first of the rest within Brush Lab's two first sentences (150): a shorter opener where they aren't.
  const first = voiceSentences(behaves);
  if (!mine && first.length && lead.length + 1 + first[0].length > VOICE_TWO_SENTENCES) {
    used.length = 0;
    lead = voiceOpener(facts, used, VOICE_TWO_SENTENCES - 1 - first[0].length);
    behaves = sayWords(voiceWithout(facts, used), false);
  }
  let out = lead;
  for (const s of voiceSentences(behaves)) { if (out.length + 1 + s.length > VOICE_ABOUT_MOST) break; out += " " + s; }
  return out;
}
// This brush, as the app's Brush Lab and Tools page say it.
function brushWords(B) { return aboutWords(plainWords(B)); }
