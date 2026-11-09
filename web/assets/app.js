(function () {
  "use strict";

  var CURATED = window.YADEIN_CURATED || [];
  var QUICK = window.YADEIN_QUICK || {};
  var STORE_KEY = "yadein.saved.v1";
  var INDIAN_GENRE = /bollywood|indian|hindi|marathi|regional|devotional|ghazal|sufi|punjabi|lavani|filmi|desi|bhojpuri|gujarati/i;

  var $ = function (id) { return document.getElementById(id); };
  var player = $("player");
  var playingCard = null;

  // ---------- helpers ----------
  function el(tag, attrs, children) {
    var n = document.createElement(tag);
    if (attrs) Object.keys(attrs).forEach(function (k) {
      if (k === "class") n.className = attrs[k];
      else if (k === "text") n.textContent = attrs[k];
      else if (k.indexOf("on") === 0) n.addEventListener(k.slice(2), attrs[k]);
      else n.setAttribute(k, attrs[k]);
    });
    (children || []).forEach(function (c) { if (c) n.appendChild(typeof c === "string" ? document.createTextNode(c) : c); });
    return n;
  }
  function enc(s) { return encodeURIComponent(s); }
  function firstArtist(a) { return String(a || "").split(/,|&| and /)[0].trim(); }
  function fullSongLinks(title, artist) {
    var q = (title + " " + firstArtist(artist)).trim();
    return [
      ["YouTube", "https://www.youtube.com/results?search_query=" + enc(q)],
      ["Spotify", "https://open.spotify.com/search/" + enc(q)],
      ["JioSaavn", "https://www.jiosaavn.com/search/" + enc(q)]
    ];
  }

  // ---------- data sources ----------
  function fromItunes(r) {
    return {
      id: "it:" + r.trackId, source: "Apple Music",
      title: r.trackName, artist: r.artistName, album: r.collectionName || "",
      year: r.releaseDate ? String(r.releaseDate).slice(0, 4) : "",
      art: r.artworkUrl100 ? r.artworkUrl100.replace("100x100", "300x300") : "",
      preview: r.previewUrl || "", link: r.trackViewUrl || "", genre: r.primaryGenreName || ""
    };
  }
  function fromDeezer(r) {
    return {
      id: "dz:" + r.id, source: "Deezer",
      title: r.title, artist: r.artist ? r.artist.name : "", album: r.album ? r.album.title : "",
      year: "", art: r.album ? (r.album.cover_medium || "") : "",
      preview: r.preview || "", link: r.link || "", genre: ""
    };
  }

  function searchItunes(term, limit) {
    var url = "https://itunes.apple.com/search?media=music&entity=song&country=in&limit=" + (limit || 100) + "&term=" + enc(term);
    return fetch(url).then(function (res) {
      if (!res.ok) throw new Error("Apple Music returned " + res.status);
      return res.json();
    }).then(function (j) { return (j.results || []).filter(function (r) { return r.kind === "song" || r.trackName; }).map(fromItunes); });
  }

  var jsonpSeq = 0;
  function searchDeezer(term, limit) {
    return new Promise(function (resolve, reject) {
      var cb = "yadeinDz" + (++jsonpSeq) + "_" + Date.now();
      var s = document.createElement("script");
      var timer = setTimeout(function () { cleanup(); reject(new Error("Deezer timed out")); }, 12000);
      function cleanup() { clearTimeout(timer); delete window[cb]; if (s.parentNode) s.parentNode.removeChild(s); }
      window[cb] = function (j) {
        cleanup();
        if (j && j.error) return reject(new Error("Deezer: " + (j.error.message || "error")));
        resolve(((j && j.data) || []).map(fromDeezer));
      };
      s.onerror = function () { cleanup(); reject(new Error("Deezer unreachable")); };
      s.src = "https://api.deezer.com/search?output=jsonp&limit=" + (limit || 100) + "&callback=" + cb + "&q=" + enc(term);
      document.head.appendChild(s);
    });
  }

  // source: auto | itunes | deezer. Resolves {tracks, source, note}
  function search(term, source, opts) {
    opts = opts || {};
    var indianOnly = !!opts.indianOnly;
    function filt(list) {
      if (!indianOnly) return list;
      return list.filter(function (t) { return !t.genre || INDIAN_GENRE.test(t.genre); });
    }
    if (source === "deezer") return searchDeezer(term, opts.limit).then(function (t) { return { tracks: t, source: "Deezer" }; });
    var it = searchItunes(term, opts.limit).then(function (t) { return { tracks: filt(t), source: "Apple Music" }; });
    if (source === "itunes") return it;
    return it.then(function (r) {
      if (r.tracks.length) return r;
      return searchDeezer(term, opts.limit).then(function (t) { return { tracks: t, source: "Deezer", note: "No Apple Music match, showing Deezer." }; });
    }, function () {
      return searchDeezer(term, opts.limit).then(function (t) { return { tracks: t, source: "Deezer", note: "Apple Music unavailable, showing Deezer." }; });
    });
  }

  // ---------- saved list ----------
  function loadSaved() {
    try { var v = JSON.parse(localStorage.getItem(STORE_KEY) || "[]"); return Array.isArray(v) ? v : []; }
    catch (e) { return []; }
  }
  var saved = loadSaved();
  function persist() {
    try { localStorage.setItem(STORE_KEY, JSON.stringify(saved)); } catch (e) { /* storage disabled */ }
    $("savedCount").textContent = saved.length;
  }
  function isSaved(id) { return saved.some(function (t) { return t.id === id; }); }
  function toggleSave(track) {
    if (isSaved(track.id)) saved = saved.filter(function (t) { return t.id !== track.id; });
    else saved.push(track);
    persist();
    renderSaved();
    document.querySelectorAll('button.save[data-id="' + cssEsc(track.id) + '"]').forEach(function (b) { setSaveBtn(b, track.id); });
  }
  function setSaveBtn(b, id) { var on = isSaved(id); b.classList.toggle("on", on); b.textContent = on ? "♥ Saved" : "♡ Save"; }
  function cssEsc(s) { return window.CSS && CSS.escape ? CSS.escape(s) : String(s).replace(/"/g, '\\"'); }

  // ---------- playback ----------
  function stopPlaying() {
    player.pause();
    if (playingCard) {
      playingCard.classList.remove("playing");
      var b = playingCard.querySelector("button.play"); if (b) b.textContent = "▶ Preview";
    }
    playingCard = null;
  }
  function playPreview(card, url) {
    if (playingCard === card && !player.paused) { stopPlaying(); return; }
    stopPlaying();
    player.src = url;
    playingCard = card;
    card.classList.add("playing");
    var b = card.querySelector("button.play"); if (b) b.textContent = "⏸ Pause";
    var p = player.play();
    if (p && p.catch) p.catch(function () { /* autoplay blocked or bad url */ });
  }
  player.addEventListener("ended", stopPlaying);

  // ---------- rendering ----------
  function trackCard(t) {
    var card = el("div", { class: "card", "data-id": t.id });
    var art = t.art ? el("img", { src: t.art, alt: "", loading: "lazy" }) : el("div", { class: "art", text: "🎵" });
    var actions = el("div", { class: "actions" });
    if (t.preview) actions.appendChild(el("button", { class: "play", type: "button", text: "▶ Preview", onclick: function () { playPreview(card, t.preview); } }));
    var sb = el("button", { class: "save", type: "button", "data-id": t.id, onclick: function () { toggleSave(t); } });
    setSaveBtn(sb, t.id);
    actions.appendChild(sb);
    if (t.link) actions.appendChild(el("a", { href: t.link, target: "_blank", rel: "noopener", text: t.source }));
    fullSongLinks(t.title, t.artist).forEach(function (l) { actions.appendChild(el("a", { href: l[1], target: "_blank", rel: "noopener", text: l[0] })); });
    card.appendChild(art);
    card.appendChild(el("div", { class: "meta" }, [
      el("div", { class: "title", text: t.title, title: t.title }),
      el("div", { class: "artist", text: t.artist }),
      el("div", { class: "album", text: [t.album, t.year].filter(Boolean).join(" · ") }),
      actions
    ]));
    return card;
  }

  function runSearch(term) {
    term = (term || "").trim();
    if (!term) return;
    $("q").value = term;
    var status = $("status"), results = $("results");
    status.textContent = "Searching “" + term + "”…";
    results.innerHTML = "";
    var mySeq = ++runSearch.seq;
    return search(term, $("source").value, { indianOnly: $("indianOnly").checked }).then(function (r) {
      if (mySeq !== runSearch.seq) return;
      if (!r.tracks.length) { status.textContent = "No songs found for “" + term + "”. Try another spelling, or untick “Indian music only”."; return; }
      status.textContent = r.tracks.length + " songs from " + r.source + (r.note ? ". " + r.note : "");
      var frag = document.createDocumentFragment();
      r.tracks.forEach(function (t) { frag.appendChild(trackCard(t)); });
      results.appendChild(frag);
    }).catch(function (e) {
      if (mySeq !== runSearch.seq) return;
      status.textContent = "Couldn't reach the music services (" + e.message + "). Check your connection and try again.";
    });
  }
  runSearch.seq = 0;

  function renderQuick() {
    var box = $("quickChips");
    Object.keys(QUICK).forEach(function (lang) {
      var row = el("div", { class: "chips" }, [el("span", { class: "label", text: lang + ":" })]);
      box.appendChild(row);
      QUICK[lang].forEach(function (term) {
        row.appendChild(el("button", { class: "chip", type: "button", text: term, onclick: function () { runSearch(term); } }));
      });
    });
  }

  function curatedCard(s, idx) {
    var id = "cur:" + idx;
    var track = { id: id, source: "Top 20", title: s.title, artist: s.singers, album: s.film, year: "", art: "", preview: "", link: "", genre: s.lang };
    var card = el("div", { class: "card", "data-id": id, "data-lang": s.lang });
    var actions = el("div", { class: "actions" });
    var playBtn = el("button", { class: "play", type: "button", text: "▶ Preview" });
    playBtn.addEventListener("click", function () {
      if (track.preview) return playPreview(card, track.preview);
      playBtn.textContent = "…";
      search(s.title + " " + firstArtist(s.singers), "auto", { limit: 5 }).then(function (r) {
        var hit = r.tracks.filter(function (t) { return t.preview; })[0];
        if (!hit) { playBtn.textContent = "No preview"; playBtn.disabled = true; return; }
        track.preview = hit.preview;
        if (hit.art) { var img = el("img", { src: hit.art, alt: "", loading: "lazy" }); card.replaceChild(img, card.firstChild); track.art = hit.art; }
        playBtn.textContent = "▶ Preview";
        playPreview(card, track.preview);
      }).catch(function () { playBtn.textContent = "▶ Retry"; });
    });
    actions.appendChild(playBtn);
    var sb = el("button", { class: "save", type: "button", "data-id": id, onclick: function () { toggleSave(track); } });
    setSaveBtn(sb, id);
    actions.appendChild(sb);
    fullSongLinks(s.title, s.singers).forEach(function (l) { actions.appendChild(el("a", { href: l[1], target: "_blank", rel: "noopener", text: l[0] })); });
    card.appendChild(el("div", { class: "art", text: s.lang === "Marathi" ? "🚩" : "🎬" }));
    card.appendChild(el("div", { class: "meta" }, [
      el("div", { class: "title", text: (idx + 1) + ". " + s.title, title: s.title }),
      el("div", { class: "artist", text: s.singers }),
      el("div", { class: "album", text: s.film }),
      el("span", { class: "tag", text: s.lang + " · " + s.mood }),
      el("div", { class: "best", text: "Best for: " + s.bestFor }),
      actions
    ]));
    return card;
  }

  function renderCurated() {
    var box = $("curated");
    CURATED.forEach(function (s, i) { box.appendChild(curatedCard(s, i)); });
    $("curatedFilter").addEventListener("click", function (e) {
      var b = e.target.closest("button[data-lang]"); if (!b) return;
      document.querySelectorAll("#curatedFilter .chip").forEach(function (c) { c.classList.toggle("active", c === b); });
      var lang = b.getAttribute("data-lang");
      box.querySelectorAll(".card").forEach(function (c) { c.style.display = (lang === "All" || c.getAttribute("data-lang") === lang) ? "" : "none"; });
    });
  }

  function renderSaved() {
    var box = $("saved");
    box.innerHTML = "";
    $("savedEmpty").style.display = saved.length ? "none" : "";
    saved.forEach(function (t) { box.appendChild(trackCard(t)); });
  }

  // ---------- YouTube ----------
  function youtubeId(input) {
    var s = String(input || "").trim();
    if (/^[\w-]{11}$/.test(s)) return s;
    var m = s.match(/(?:youtu\.be\/|youtube(?:-nocookie)?\.com\/(?:watch\?(?:.*&)?v=|embed\/|shorts\/|live\/|v\/))([\w-]{11})/);
    return m ? m[1] : null;
  }

  function initYoutube() {
    $("ytForm").addEventListener("submit", function (e) {
      e.preventDefault();
      var id = youtubeId($("ytUrl").value);
      var box = $("ytPlayer");
      box.innerHTML = "";
      if (!id) { $("ytStatus").textContent = "That doesn't look like a YouTube link."; return; }
      $("ytStatus").textContent = "";
      stopPlaying();
      box.appendChild(el("iframe", {
        src: "https://www.youtube-nocookie.com/embed/" + id + "?autoplay=1&rel=0",
        title: "YouTube player", allow: "autoplay; encrypted-media; picture-in-picture", allowfullscreen: ""
      }));
    });
  }

  // ---------- tabs & init ----------
  function initTabs() {
    document.querySelectorAll(".tab").forEach(function (t) {
      t.addEventListener("click", function () {
        var name = t.getAttribute("data-tab");
        document.querySelectorAll(".tab").forEach(function (x) { x.classList.toggle("active", x === t); });
        document.querySelectorAll(".panel").forEach(function (p) { p.classList.toggle("active", p.id === "tab-" + name); });
      });
    });
  }

  function init() {
    initTabs();
    renderQuick();
    renderCurated();
    renderSaved();
    persist();
    initYoutube();
    $("searchForm").addEventListener("submit", function (e) { e.preventDefault(); runSearch($("q").value); });
    $("copySaved").addEventListener("click", function () {
      var text = saved.map(function (t, i) { return (i + 1) + ". " + t.title + " — " + t.artist; }).join("\n");
      var done = function () { $("copySaved").textContent = "✅ Copied"; setTimeout(function () { $("copySaved").textContent = "📋 Copy list"; }, 1500); };
      if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(text).then(done, function () { window.prompt("Copy your list:", text); });
      else window.prompt("Copy your list:", text);
    });
    $("clearSaved").addEventListener("click", function () {
      if (!saved.length || !window.confirm("Remove all saved songs?")) return;
      saved = []; persist(); renderSaved();
      document.querySelectorAll("button.save").forEach(function (b) { setSaveBtn(b, b.getAttribute("data-id")); });
    });
    var initial = new URLSearchParams(location.search).get("q");
    if (initial) runSearch(initial);
  }

  window.Yadein = { youtubeId: youtubeId, search: search, runSearch: runSearch };
  init();
})();
