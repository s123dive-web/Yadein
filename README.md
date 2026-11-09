# Yadein

🎵 **[Reels Music Playlist — 20 best Hindi & Marathi songs for reels](REELS_MUSIC.md)**

## 🔎 Music explorer: all Hindi & Marathi songs

Open `index.html` in a browser (or the GitHub Pages link once Pages is enabled) to:

- **Search the full catalogue** (any song, singer or film) using the free Apple Music / iTunes Search API, with automatic **Deezer** fallback
- **Play 30-second previews** right on the page (a typical reel length)
- **One-tap singer chips**: Arijit Singh, Pritam, Ajay-Atul, Lavani, Sanju Rathod…
- **Top 20 reel picks** with Hindi/Marathi filter and previews
- **♡ Save** songs to *My list* (saved in your browser) and copy the list
- **Full songs**: links to YouTube / Spotify / JioSaavn, or paste a YouTube link to play it on the page
- Share a search: `index.html?q=Sairat`

No API keys, no install. To use a song in a reel, add it from Instagram's own audio library so the reel isn't muted.

### Publish it (free)
GitHub repo ▸ **Settings ▸ Pages** ▸ Source: *Deploy from a branch* ▸ pick the branch and `/ (root)` ▸ Save.

### Tests
```
node tests/data.test.js   # curated list matches REELS_MUSIC.md
node tests/e2e.test.js    # browser test with mocked music APIs (needs Playwright)
```
