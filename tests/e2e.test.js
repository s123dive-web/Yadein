// Browser test with mocked Apple Music / Deezer APIs. Run: node tests/e2e.test.js
const { chromium } = require("playwright");
const http = require("http"), fs = require("fs"), path = require("path"), assert = require("assert");
const root = path.join(__dirname, "..");
const types = { ".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".md": "text/markdown" };
const server = http.createServer((req, res) => {
  const p = path.join(root, decodeURIComponent(req.url.split("?")[0]) === "/" ? "index.html" : decodeURIComponent(req.url.split("?")[0]));
  if (!p.startsWith(root) || !fs.existsSync(p)) { res.writeHead(404); return res.end(); }
  res.writeHead(200, { "Content-Type": types[path.extname(p)] || "application/octet-stream" });
  fs.createReadStream(p).pipe(res);
});
const it = (n, genre) => ({ kind: "song", trackId: n, trackName: "Song " + n, artistName: "Artist " + n, collectionName: "Album", releaseDate: "2023-01-01T00:00:00Z", artworkUrl100: "https://example.com/100x100.jpg", previewUrl: "https://audio.example/p" + n + ".m4a", trackViewUrl: "https://music.apple.com/x/" + n, primaryGenreName: genre });

(async () => {
  await new Promise(r => server.listen(0, r));
  const base = "http://127.0.0.1:" + server.address().port + "/";
  const browser = await chromium.launch();
  const page = await browser.newPage();
  const errors = [];
  page.on("pageerror", e => errors.push(e.message));
  let itunesMode = "ok";
  await page.route("https://itunes.apple.com/**", route => {
    const term = new URL(route.request().url()).searchParams.get("term");
    if (itunesMode === "fail") return route.abort();
    const body = itunesMode === "empty" ? { results: [] } : { results: [it(1, "Bollywood"), it(2, "Pop"), it(3, "Regional Indian"), { ...it(4, "Indian Pop"), trackName: "<img src=x onerror=alert(1)>" }] };
    route.fulfill({ status: 200, contentType: "application/json", headers: { "access-control-allow-origin": "*" }, body: JSON.stringify(body) });
  });
  await page.route("https://api.deezer.com/**", route => {
    const u = new URL(route.request().url());
    const data = { data: [{ id: 9, title: "Deezer Song", artist: { name: "DZ Artist" }, album: { title: "DZ Album", cover_medium: "https://example.com/c.jpg" }, preview: "https://audio.example/dz.mp3", link: "https://www.deezer.com/track/9" }] };
    route.fulfill({ status: 200, contentType: "text/javascript", body: u.searchParams.get("callback") + "(" + JSON.stringify(data) + ")" });
  });
  await page.route("https://example.com/**", r => r.fulfill({ status: 200, contentType: "image/png", body: "" }));
  await page.route("https://audio.example/**", r => r.fulfill({ status: 404 }));
  await page.route("https://www.youtube-nocookie.com/**", r => r.fulfill({ status: 200, contentType: "text/html", body: "<p>yt</p>" }));

  await page.goto(base);

  // Search with Indian-only filter: drops the "Pop" result, escapes HTML in titles.
  await page.fill("#q", "Arijit Singh");
  await page.click("#searchForm button");
  await page.waitForSelector("#results .card");
  assert.strictEqual(await page.locator("#results .card").count(), 3);
  assert.match(await page.textContent("#status"), /3 songs from Apple Music/);
  assert.strictEqual(await page.locator("#results img[src=x]").count(), 0, "no HTML injection");
  // Preview sets audio src.
  await page.locator("#results .card").first().locator("button.play").click();
  assert.strictEqual(await page.evaluate(() => document.getElementById("player").src), "https://audio.example/p1.m4a");
  // Untick filter shows all 4.
  await page.uncheck("#indianOnly");
  await page.click("#searchForm button");
  await page.waitForFunction(() => document.querySelectorAll("#results .card").length === 4);
  await page.check("#indianOnly");

  // Quick chip triggers search.
  await page.click("#quickChips >> text=Ajay-Atul");
  await page.waitForFunction(() => document.getElementById("q").value === "Ajay-Atul" && /Apple Music/.test(document.getElementById("status").textContent));

  // Deezer fallback: Apple failing, then Apple empty.
  itunesMode = "fail";
  await page.click("#searchForm button");
  await page.waitForFunction(() => /Deezer/.test(document.getElementById("status").textContent));
  assert.match(await page.textContent("#results .card .title"), /Deezer Song/);
  itunesMode = "empty";
  await page.click("#searchForm button");
  await page.waitForFunction(() => /No Apple Music match/.test(document.getElementById("status").textContent));
  itunesMode = "ok";
  await page.selectOption("#source", "deezer");
  await page.click("#searchForm button");
  await page.waitForFunction(() => /1 songs from Deezer/.test(document.getElementById("status").textContent));
  await page.selectOption("#source", "auto");

  // Save + persistence + copy text.
  await page.locator("#results .card button.save").first().click();
  assert.strictEqual(await page.textContent("#savedCount"), "1");

  // Curated: 20 cards, filters, lazy preview lookup.
  await page.click(".tab[data-tab=curated]");
  assert.strictEqual(await page.locator("#curated .card").count(), 20);
  await page.click("#curatedFilter >> text=Marathi");
  assert.strictEqual(await page.locator("#curated .card:visible").count(), 8);
  await page.click("#curatedFilter >> text=Hindi");
  assert.strictEqual(await page.locator("#curated .card:visible").count(), 12);
  await page.click("#curatedFilter >> text=All");
  const first = page.locator("#curated .card").first();
  assert.match(await first.textContent(), /Kesariya/);
  await first.locator("button.play").click();
  await page.waitForFunction(() => document.getElementById("player").src.includes("audio.example"));
  await first.locator("button.save").click();
  assert.strictEqual(await page.textContent("#savedCount"), "2");

  // Reload: saved list persists.
  await page.reload();
  assert.strictEqual(await page.textContent("#savedCount"), "2");
  await page.click(".tab[data-tab=saved]");
  assert.strictEqual(await page.locator("#saved .card").count(), 2);
  await page.locator("#saved .card button.save").first().click();
  assert.strictEqual(await page.textContent("#savedCount"), "1");

  // YouTube embed parsing.
  const ids = await page.evaluate(() => ["https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=3", "https://youtu.be/dQw4w9WgXcQ", "https://youtube.com/shorts/dQw4w9WgXcQ", "dQw4w9WgXcQ", "https://example.com"].map(Yadein.youtubeId));
  assert.deepStrictEqual(ids, ["dQw4w9WgXcQ", "dQw4w9WgXcQ", "dQw4w9WgXcQ", "dQw4w9WgXcQ", null]);
  await page.click(".tab[data-tab=youtube]");
  await page.fill("#ytUrl", "https://youtu.be/dQw4w9WgXcQ");
  await page.click("#ytForm button");
  assert.match(await page.getAttribute("#ytPlayer iframe", "src"), /youtube-nocookie\.com\/embed\/dQw4w9WgXcQ/);

  // ?q= deep link.
  await page.goto(base + "?q=Sairat");
  await page.waitForSelector("#results .card");

  // Screenshot for review (mobile size).
  await page.setViewportSize({ width: 420, height: 900 });
  if (process.env.SHOT) await page.screenshot({ path: process.env.SHOT, fullPage: false });

  assert.deepStrictEqual(errors, [], "no page errors");
  await browser.close(); server.close();
  console.log("e2e.test.js: OK");
})().catch(e => { console.error(e); process.exit(1); });
