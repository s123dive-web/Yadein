// Verifies the curated list in assets/songs.js matches REELS_MUSIC.md (no regression of the original playlist).
const fs = require("fs"), path = require("path"), vm = require("vm"), assert = require("assert");
const root = path.join(__dirname, "..");
const ctx = { window: {} };
vm.runInNewContext(fs.readFileSync(path.join(root, "assets/songs.js"), "utf8"), ctx);
const curated = ctx.window.YADEIN_CURATED;
const md = fs.readFileSync(path.join(root, "..", "docs", "REELS_MUSIC.md"), "utf8");
const rows = md.split("\n").filter(l => /^\| \d+ \| \*\*/.test(l)).map(l => l.split("|").map(c => c.trim()));
assert.strictEqual(curated.length, 20, "20 curated songs");
assert.strictEqual(rows.length, 20, "20 songs in REELS_MUSIC.md");
rows.forEach((r, i) => {
  const s = curated[i];
  assert.strictEqual(r[2].replace(/\*\*/g, ""), s.title, "title " + i);
  assert.strictEqual(r[3], s.film, "film " + i);
  assert.strictEqual(r[4], s.singers, "singers " + i);
  assert.strictEqual(r[5], s.mood, "mood " + i);
  assert.strictEqual(r[6], s.bestFor, "bestFor " + i);
});
assert.strictEqual(curated.filter(s => s.lang === "Hindi").length, 12);
assert.strictEqual(curated.filter(s => s.lang === "Marathi").length, 8);
assert.ok(fs.readFileSync(path.join(root, "..", "README.md"), "utf8").includes("docs/REELS_MUSIC.md"), "README still links playlist");
console.log("data.test.js: OK");
