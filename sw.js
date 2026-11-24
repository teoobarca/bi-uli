// Written by scripts/web.sh: this build's files from the cache, a new build takes over once the app is closed.
const CACHE = "uli-b5f8855cd866";
const FILES = ["658820f72b4617efb380.wasm", "apple-touch-icon.png", "bf770c33323736b665ff.wasm", "google_sans_flex.ttf", "icon-192.png", "icon-512.png", "index.html", "jetbrains_mono.ttf", "manifest.webmanifest", "questions.json", "uli.js", "uli.js.LICENSE.txt", "web.js"];
self.addEventListener("install", (e) => e.waitUntil(caches.open(CACHE).then((c) => c.addAll(["./", ...FILES].map((f) => new Request(f, { cache: "no-cache" }))))));
self.addEventListener("activate", (e) => e.waitUntil(caches.keys()
  .then((keys) => Promise.all(keys.filter((k) => k.startsWith("uli-") && k !== CACHE).map((k) => caches.delete(k))))
  .then(() => self.clients.claim())));
self.addEventListener("fetch", (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== "GET" || url.origin !== location.origin || !url.pathname.startsWith(new URL("./", self.registration.scope).pathname)) return;
  const key = e.request.mode === "navigate" ? "./" : e.request;
  e.respondWith(caches.open(CACHE).then((c) => c.match(key)).then((hit) => hit || fetch(e.request)));
});
