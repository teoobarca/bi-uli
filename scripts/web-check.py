# The web app's check before it goes out: serves build/web/ and walks it in WebKit as an iPhone, in Chromium as an
# Android phone and in a computer's window. Exit code 0 only when everything holds.
#
#   scripts/web.sh && uvx --with playwright==1.64.0 python scripts/web-check.py [--shots DIR] [--app URL]
#
# --app URL walks a deployed copy instead. Playwright 1.64.0 uses browsers in ~/Library/Caches/ms-playwright
# (otherwise once: `playwright install webkit chromium`).
import asyncio
import functools
import http.server
import json
import os
import sys
import threading

from playwright.async_api import async_playwright

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WEB = os.path.join(ROOT, "build", "web")
QUESTIONS = json.load(open(os.path.join(ROOT, "content", "questions.json"), encoding="utf-8"))
arg = lambda name: sys.argv[sys.argv.index(name) + 1] if name in sys.argv else None
SHOTS, APP = arg("--shots"), arg("--app")
# messages of Kotlin/Wasm and Skia themselves
KNOWN = ("Accessing `memory` via `wasmExports`", "WEBGL_debug_renderer_info", "WEBGL_polygon_mode", "GPU stall", "unreadable, backed up to")
problems = []


def check(ok, what):
    print(("  ok    " if ok else "  CHYBA ") + what, flush=True)
    if not ok:
        problems.append(what)


class Handler(http.server.SimpleHTTPRequestHandler):
    extensions_map = {**http.server.SimpleHTTPRequestHandler.extensions_map, ".wasm": "application/wasm"}

    def log_message(self, *a):
        pass


class Notched(Handler):
    """The same files as on a phone with a notch: browsers here have no safe areas, so the page gets fixed ones."""

    insets = {"top": 47, "right": 0, "bottom": 34, "left": 0}

    def do_GET(self):
        if self.path.split("?")[0] not in ("/", "/index.html"):
            return super().do_GET()
        html = open(os.path.join(WEB, "index.html"), encoding="utf-8").read()
        for side, px in self.insets.items():
            html = html.replace(f"env(safe-area-inset-{side})", f"{px}px")
        body = html.encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


class NotchedLandscape(Notched):
    insets = {"top": 0, "right": 59, "bottom": 21, "left": 59}


def serve(handler):
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), functools.partial(handler, directory=WEB))
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return f"http://127.0.0.1:{server.server_port}/"


async def opened(page):
    await page.wait_for_selector("#splash", state="detached", timeout=60000)
    await page.wait_for_timeout(700)


async def find(page, name, wait=6000):
    """The button the app exposes to accessibility (Compose draws on a canvas), or None."""
    el = page.get_by_role("button", name=name).first
    try:
        await el.wait_for(state="attached", timeout=wait)
        return el
    except Exception:
        return None


async def stored(page, key):
    t = await page.evaluate("k => localStorage.getItem(k)", key)
    return json.loads(t) if t else None


def plain(s):
    return "".join(c for c in s if c.isprintable() and not c.isspace())


BUTTONS = """() => { const out = []; const walk = (n) => { for (const e of n.querySelectorAll('*')) { if (e.shadowRoot) walk(e.shadowRoot);
  if (e.getAttribute('role') === 'button') { const r = e.getBoundingClientRect(); out.push([e.getAttribute('aria-label') || e.textContent, r.x, r.y, r.width, r.height]); } } };
  walk(document); return out; }"""


async def buttons(page):
    """Every button and its box, once nothing moves any more (two equal readings)."""
    last = None
    for _ in range(20):
        now = await page.evaluate(BUTTONS)
        if now == last:
            return now
        last = now
        await page.wait_for_timeout(350)
    return last


async def tap(page, name):
    if await find(page, name) is None:
        check(False, f"tlačidlo {name!r}")
        return
    hit = next((b for b in await buttons(page) if plain(name) in plain(b[0]) and b[4] > 0), None)
    if hit is None:
        check(False, f"tlačidlo {name!r} vidno")
        return
    await page.mouse.click(hit[1] + hit[3] / 2, hit[2] + hit[4] / 2)
    await page.wait_for_timeout(900)


async def option(page, text):
    """Taps the option with [text] (its button reads "B", then the text), scrolled into view by "Ďalšie možnosti"."""
    want = plain(text)
    for _ in range(5):
        now = await buttons(page)
        more = next((b for b in now if plain(b[0]) == plain("Ďalšie možnosti") and b[4] > 0), None)
        for label, x, y, w, h in now:
            if plain(label) in (want, *(c + want for c in "ABCDEFGH")) and h > 30 and (more is None or y + h / 2 < more[2]):
                await page.mouse.click(x + w / 2, y + h / 2)
                await page.wait_for_timeout(900)
                return True
        await page.wait_for_timeout(800)
        if more and more in await buttons(page):  # really there, not just while the card slides in
            await page.mouse.click(more[1] + more[3] / 2, more[2] + more[4] / 2)
        await page.wait_for_timeout(900)
    if SHOTS:
        await page.screenshot(path=os.path.join(SHOTS, "nenajdene.png"))
    print(now)
    return False


async def answer(page, right):
    """Answers the open question by its saved state: the option order is in the session, the key in the bank."""
    q = (await stored(page, "uli.session"))["quiz"]
    question = QUESTIONS[q["round"]["queue"][q["round"]["index"]] - 1]
    good = [i for i, o in enumerate(question["options"]) if o["correct"]]
    picks = good if right else [i for i, o in enumerate(question["options"]) if not o["correct"]][:1] + good[1:]
    for i in sorted(picks, key=q["order"].index):  # top to bottom, the list only scrolls down here
        text = question["options"][i]["text"]
        if not await option(page, text):
            check(False, f"možnosť {text!r} otázky {question['id']}")
            raise SystemExit(1)
    if question["kind"] == "multi":
        await tap(page, "Skontrolovať")


async def walk(browser, name, options, base):
    print(name, flush=True)
    errors, outside = [], []
    ctx = await browser.new_context(**options)
    page = await ctx.new_page()
    page.on("console", lambda m: errors.append(m.text) if m.type in ("error", "warning") and not m.text.startswith(KNOWN) and not any(k in m.text for k in KNOWN) else None)
    page.on("pageerror", lambda e: errors.append(str(e)))
    page.on("request", lambda r: outside.append(r.url) if not r.url.startswith((base, "blob:", "data:")) else None)
    await page.goto(base)
    await opened(page)
    # taps land where the app draws only when the canvas covers exactly the box Compose measures
    fits = await page.evaluate("""() => { const app = document.getElementById('app'), r = app.getBoundingClientRect();
      const find = (n) => { for (const e of n.querySelectorAll('*')) { if (e.tagName === 'CANVAS') return e; const c = e.shadowRoot && find(e.shadowRoot); if (c) return c; } };
      const c = find(app).getBoundingClientRect();
      return [c.x - r.x, c.y - r.y, c.width - app.clientWidth, c.height - app.clientHeight].every((d) => Math.abs(d) < 1) && r.bottom <= innerHeight; }""")
    check(fits, "plátno presne vypĺňa appku")
    if "naležato" in name:  # too short to walk without scrolling back up; the button at the bottom edge is enough
        await tap(page, "Začať")
        check(((await stored(page, "uli.session")) or {}).get("quiz") is not None, "ťuk na spodné tlačidlo otvorí kolo")
        await ctx.close()
        return
    hint = "iPhone" in name
    check((await find(page, "Skryť", 1500) is not None) == hint, "rada o ploche je len na iPhone")
    if hint:
        await tap(page, "Skryť")
        check("Skryť" not in [b[0] for b in await buttons(page)], "rada sa dá skryť")
    await tap(page, "Začať")
    await answer(page, True)
    p = await stored(page, "uli.progress")
    check(bool(p) and len(p["cards"]) == 1, "správna odpoveď je uložená")
    await tap(page, "Ďalej")
    await answer(page, False)
    if SHOTS:
        await page.screenshot(path=os.path.join(SHOTS, f"{name}-chyba.png"))
    p = await stored(page, "uli.progress")
    check(len(p["cards"]) == 2 and sum(c["timesWrong"] for c in p["cards"].values()) == 1, "zlá odpoveď je uložená")
    await page.reload()
    await opened(page)
    check(await find(page, "Ďalej") is not None, "obnovenie stránky sa vráti do kola")
    await tap(page, "Ďalej")
    await page.evaluate("history.back()")
    await page.wait_for_timeout(1200)
    check(await find(page, "Pokračovať") is not None, "späť z kola ide na Domov a kolo ostane otvorené")
    await tap(page, "Pokračovať")
    for _ in range(30):
        s = await stored(page, "uli.session")
        if not s or not s.get("quiz"):
            break
        await answer(page, True)
        await tap(page, "Ďalej" if await find(page, "Ďalej", 1500) else "Dokončiť")
    check(await find(page, "Ďalšie kolo") is not None, "kolo sa dohralo do konca")
    if SHOTS:
        await page.wait_for_timeout(1500)
        await page.screenshot(path=os.path.join(SHOTS, f"{name}-koniec.png"))
    await page.evaluate("history.back()")
    await page.wait_for_timeout(1200)
    await tap(page, "Skúška nanečisto")
    check((await stored(page, "uli.session") or {}).get("exam") is not None, "skúška nanečisto sa spustí")
    await page.reload()
    await opened(page)
    check((await stored(page, "uli.session") or {}).get("exam") is not None and await find(page, "Ukončiť skúšku") is not None, "obnovenie sa vráti do skúšky")
    await page.evaluate("history.back()")
    await page.wait_for_timeout(1200)
    check(await find(page, "Skúška nanečisto") is not None, "späť z nezačatej skúšky ide na Domov")
    # offline: only the service worker's cache
    await page.evaluate("navigator.serviceWorker.ready.then(() => 1)")
    await page.reload()
    await opened(page)
    if "Chromium" in name:
        await ctx.set_offline(True)
        await page.reload()
        await opened(page)
        check(await find(page, "Ďalšie kolo") is not None, "offline sa otvorí")
        await ctx.set_offline(False)
    # damaged progress is kept aside and said, not overwritten
    await page.evaluate("localStorage.setItem('uli.progress', '{zle')")
    await page.reload()
    await opened(page)
    check(await find(page, "Skúsiť znova") is not None and await page.evaluate("localStorage.getItem('uli.progress.corrupt')") == "{zle", "poškodený postup sa odloží a appka to povie")
    check(not errors, f"konzola bez chýb {errors[:3]}")
    check(not outside, f"nič z cudzieho servera {outside[:3]}")
    await ctx.close()


async def main():
    base = APP or serve(Handler)
    notched, landscape = (APP, APP) if APP else (serve(Notched), serve(NotchedLandscape))
    if SHOTS:
        os.makedirs(SHOTS, exist_ok=True)
    async with async_playwright() as p:
        wk, cr = await p.webkit.launch(), await p.chromium.launch()
        await walk(wk, "iPhone WebKit", p.devices["iPhone 15"], notched)
        await walk(wk, "iPhone WebKit naležato", p.devices["iPhone 15 landscape"], landscape)
        await walk(wk, "iPhone SE WebKit tmavá", {**p.devices["iPhone SE"], "color_scheme": "dark"}, base)
        await walk(cr, "Pixel Chromium", p.devices["Pixel 7"], base)
        await walk(cr, "počítač Chromium", {"viewport": {"width": 1440, "height": 900}}, base)
        await wk.close()
        await cr.close()
    print(f"\n{len(problems)} chýb" if problems else "\nvšetko v poriadku")
    sys.exit(1 if problems else 0)


asyncio.run(main())
