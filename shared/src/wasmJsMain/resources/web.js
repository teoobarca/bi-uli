// What the Kotlin/Wasm app asks of the browser beyond drawing (shared/src/wasmJsMain): the splash, what to do when
// drawing stops, and the back gesture. Taken over from FITagoras, where it runs on people's iPhones.
"use strict";
window.uliWeb = (() => {
  // ---- the splash ----
  // index.html's #splash stays until the app has drawn its first frame (Main.kt calls ready()). Its bar counts the bytes
  // the app's files have brought, out of the total scripts/web.sh writes into the meta "uli-bytes".
  const TEXT = {
    first: "Prvýkrát sa sťahuje celá appka, asi 5 MB. Potom sa otvára hneď, aj bez internetu.",
    stalled: "Sťahovanie stojí. Skontroluj pripojenie na internet.",
    offline: "Appku sa nepodarilo stiahnuť. Skontroluj pripojenie na internet a skús to znova.",
    old: "Tento prehliadač appku nespustí. Treba iOS 18.2 alebo novší, prípadne aktuálny Chrome či Firefox.",
    broken: "Appku sa nepodarilo spustiť. Skús to znova; ak to nepomôže, založ issue na github.com/teoobarca/bi-uli.",
  };
  const total = () => Number((document.querySelector('meta[name="uli-bytes"]') || {}).content) || 0;
  let splashOn = true, got = 0, lastByte = Date.now(), failedOnce = false;
  const started = Date.now();
  const el = (sel) => document.querySelector("#splash" + (sel ? " " + sel : ""));
  function say(text, cls) {
    const s = el(); if (!s) return;
    if (cls) s.classList.add(cls);
    el("p").textContent = text;
  }
  function fail(kind) {
    if (!splashOn || failedOnce) return;
    failedOnce = true;
    say(TEXT[kind], "failed");
  }
  function failure(reason) {
    const m = String(reason && (reason.message || reason.name) || reason || "");
    if (typeof WebAssembly !== "object" || /Wasm GC|CompileError|exception handling/i.test(m) || (reason && reason.name === "CompileError")) fail("old");
    else if (!navigator.onLine || /Failed to fetch|NetworkError|Load failed|network connection|offline/i.test(m)) fail("offline");
    else fail("broken");
  }
  const origFetch = window.fetch.bind(window);
  window.fetch = function (...args) {
    const p = origFetch(...args);
    if (!splashOn) return p;
    // a copy of each answer is read alongside only to count its bytes; the app gets the answer itself untouched
    return p.then((r) => {
      if (splashOn && r.ok && r.body) {
        const rd = r.clone().body.getReader();
        const pump = () => rd.read().then(({ done, value }) => {
          if (done || !splashOn) { if (!done) rd.cancel().catch(() => {}); return; }
          got += value.byteLength; lastByte = Date.now(); pump();
        }).catch(() => {});
        pump();
      }
      return r;
    });
  };
  const tick = setInterval(() => {
    if (!el() || !splashOn) return;
    const t = total();
    if (t) el(".bar i").style.width = Math.min(100, Math.round(100 * got / t)) + "%";
    if (failedOnce) return;
    const now = Date.now();
    if (now - lastByte > 15000 && now - started > 15000 && !document.hidden && !(t && got >= t)) { say(TEXT.stalled, "slow"); return; }
    el().classList.remove("slow");
    say(now - started > 2500 && !(navigator.serviceWorker && navigator.serviceWorker.controller) ? TEXT.first : "Načítava sa…");
  }, 250);
  addEventListener("error", (e) => { if (splashOn) failure(e.error || e.message); else crashed(); });
  addEventListener("unhandledrejection", (e) => { if (splashOn) failure(e.reason); else crashed(); });
  addEventListener("offline", () => { if (splashOn && got < total()) fail("offline"); });
  addEventListener("DOMContentLoaded", () => {
    el("button").addEventListener("click", () => location.reload());
    if (typeof WebAssembly !== "object") fail("old");
  });
  function ready() {
    if (!splashOn) return;
    splashOn = false; clearInterval(tick); window.fetch = origFetch;
    const s = el(); if (!s) return;
    s.classList.add("gone");
    setTimeout(() => s.remove(), 250);
    watchCanvas();
  }

  // ---- when drawing stops ----
  // Safari may take the WebGL context of a page left in the background and the app doesn't get it back: the page
  // reloads instead (once it is looked at) and comes back to the same screen with the saved progress.
  function reloadWhenSeen() {
    if (!document.hidden) { location.reload(); return; }
    document.addEventListener("visibilitychange", () => { if (!document.hidden) location.reload(); });
  }
  function watchCanvas() {
    const find = (n) => { for (const c of n.children || []) { if (c.tagName === "CANVAS") return c; const f = (c.shadowRoot && find(c.shadowRoot)) || find(c); if (f) return f; } return null; };
    const canvas = find(document.getElementById("app"));
    if (canvas) canvas.addEventListener("webglcontextlost", reloadWhenSeen);
  }
  let barShown = false;
  function bar(text) {
    if (barShown) return;
    barShown = true;
    const b = document.createElement("div");
    b.id = "bar";
    b.setAttribute("role", "alert");
    b.innerHTML = '<span></span><button type="button">Načítať znova</button><button type="button" class="x" aria-label="Zavrieť">✕</button>';
    b.querySelector("span").textContent = text;
    b.querySelector("button").addEventListener("click", () => location.reload());
    b.querySelector(".x").addEventListener("click", () => { b.remove(); barShown = false; });
    document.body.appendChild(b);
  }
  const crashed = () => bar("Niečo sa pokazilo.");
  const saveFailed = () => bar("Postup sa nedá uložiť. Nie si v súkromnom okne?");

  // ---- the back gesture ----
  // The browser's back (the iOS edge swipe, the back button) is the app's back: one history entry above the page is
  // kept armed and re-armed after each use.
  function onBack(cb) {
    if (!history.state || !history.state.uli) {
      history.replaceState({ uliBase: true }, "");
      history.pushState({ uli: true }, "");
    }
    addEventListener("popstate", () => { history.pushState({ uli: true }, ""); cb(); });
  }

  // Two tabs share one progress and each writes its whole copy: once another tab has saved, this one reloads when it
  // comes forward, so it continues from the newer progress.
  let stale = false;
  addEventListener("storage", (e) => { if (e.key === "uli.progress" || e.key === "uli.session") stale = true; });
  document.addEventListener("visibilitychange", () => { if (!document.hidden && stale) location.reload(); });

  const persist = async () => { try { if (navigator.storage && navigator.storage.persist) await navigator.storage.persist(); } catch (e) { /* not granted */ } };
  // an iPhone or iPad in the browser, not yet opened from the home screen
  const ios = () => /iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);
  const standalone = () => window.matchMedia("(display-mode: standalone)").matches || navigator.standalone === true;

  // offline after the first visit (sw.js, written by scripts/web.sh)
  if ("serviceWorker" in navigator && (location.protocol === "https:" || location.hostname === "127.0.0.1"))
    addEventListener("load", () => navigator.serviceWorker.register("sw.js").catch(() => {}));

  return { ready, failed: failure, saveFailed, onBack, persist, needsHomeScreen: () => ios() && !standalone() };
})();
