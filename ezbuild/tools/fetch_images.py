#!/usr/bin/env python3
"""Download product images, named exactly as products.csv expects (<out>/media/p/<sku>.webp).

Setup (once):  pip install requests beautifulsoup4 pillow ddgs
Run:           python fetch_images.py [out_dir]     (default out_dir = ./static)
               python fetch_images.py static --only cpu-01,gpu-03     (just some SKUs)

Order of attempts per product (first good image wins; existing files are skipped):
  1. direct image_url from media_manifest.csv
  2. shop searches: StarTech, Ryans, Techland, Ultratech, UCC, PC Builder BD
  3. Google Images (optional API: set GOOGLE_API_KEY and GOOGLE_CX env vars; else HTML scrape)
  4. DuckDuckGo image search via `ddgs` as the last resort
A result is only accepted if its title/alt text shares enough words with the product
name (so a "RTX 5060" search can't return a 5060 Ti by luck) and the image is >= 200px.
Add --placeholders to fill leftovers with a plain labelled image so the site has no broken pictures.
Failures are listed in missing.txt - put your own <sku>.webp there instead.
"""
import csv, io, os, re, sys, time
from urllib.parse import quote_plus, urljoin
import requests
from bs4 import BeautifulSoup
from PIL import Image

H = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124 Safari/537.36"}
SHOPS = [  # (name, base url, search url template, product-card selectors)
    ("startech",  "https://www.startech.com.bd", "https://www.startech.com.bd/product/search?search={q}", ".p-item"),
    ("ryans",     "https://www.ryans.com",       "https://www.ryans.com/search?q={q}",                    ".card, .product-box, .product-item"),
    ("techland",  "https://www.techlandbd.com",  "https://www.techlandbd.com/search?q={q}",               ".product-card, .product, .card, .p-item"),
    ("ultratech", "https://www.ultratech.com.bd","https://www.ultratech.com.bd/product/search?search={q}","div.product-thumb, .product-layout, .p-item"),
    ("ucc",       "https://www.ucc.com.bd",      "https://www.ucc.com.bd/index.php?route=product/search&search={q}", ".product-thumb, .product-layout"),
    ("pcbstore",  "https://pcbstore.com.bd",     "https://pcbstore.com.bd/search?q={q}",                  ".product-card, .product, .card, .p-item"),
]
OUT = "static"
ONLY = None
args = [a for a in sys.argv[1:] if a != "--placeholders"]
if "--only" in args:
    i = args.index("--only"); ONLY = set(args[i + 1].split(",")); del args[i:i + 2]
if args: OUT = args[0]

def words(s):
    return set(re.findall(r"[a-z0-9]+", s.lower())) - {"the", "gb", "tb", "with", "and", "gaming", "graphics", "card", "in", "mhz"}

def good_match(query, text):
    q, t = words(query), words(text)
    if not q: return True
    nums = {w for w in q if any(c.isdigit() for c in w)}      # model numbers must match
    if nums and not (nums & t): return False
    return len(q & t) / len(q) >= 0.5

def big_version(url):
    return re.sub(r"-(\d+)x(\d+)(w?)(\.\w+)$", r"-800x800\4", url)

def fetch_img(url):
    small = None
    for u in (big_version(url), url):                          # try the larger size first
        try:
            r = requests.get(u, headers=H, timeout=20)
            if r.ok and r.headers.get("content-type", "").startswith("image"):
                im = Image.open(io.BytesIO(r.content))
                if min(im.size) >= 200: return im
                if min(im.size) >= 90: small = small or im      # thumbnail: better than nothing
        except Exception: pass
    return small

def from_shop(shop, query):
    name, base, tpl, sel = shop
    r = requests.get(tpl.format(q=quote_plus(query)), headers=H, timeout=20)
    soup = BeautifulSoup(r.text, "html.parser")
    for card in soup.select(sel)[:8]:
        img = card.find("img")
        if not img: continue
        src = img.get("data-src") or img.get("data-original") or img.get("src")
        text = " ".join(filter(None, [img.get("alt"), img.get("title"), card.get_text(" ", strip=True)[:200]]))
        if src and good_match(query, text):
            im = fetch_img(urljoin(base, src))
            if im: return im
    return None

def variants(q):
    q = re.sub(r"\(.*?\)", " ", q)                           # drop "(2x8)" style bits
    q = re.sub(r"\b(\d+in|FHD|QHD|UHD|NVMe|SATA|RGB|ARGB|Black|V2|CL\d+|\d+Hz|\d+RPM)\b", " ", q, flags=re.I)
    w = q.split()
    out = [" ".join(w), " ".join(w[:4]), " ".join(w[:3])]
    return list(dict.fromkeys(x for x in out if len(x) > 3))

def from_google(query):
    """Official API if GOOGLE_API_KEY + GOOGLE_CX are set (reliable, has titles);
    otherwise scrape Google Images HTML (works only while Google doesn't block it)."""
    key, cx = os.environ.get("GOOGLE_API_KEY"), os.environ.get("GOOGLE_CX")
    if key and cx:
        r = requests.get("https://www.googleapis.com/customsearch/v1", timeout=20, params={
            "key": key, "cx": cx, "q": query + " product", "searchType": "image", "num": 8})
        for it in r.json().get("items", []):
            if good_match(query, it.get("title", "")):
                im = fetch_img(it["link"])
                if im: return im
        return None
    r = requests.get("https://www.google.com/search", headers=H, timeout=20,
                     params={"q": query + " product", "tbm": "isch", "hl": "en"})
    pairs = re.findall(r'\["(https?://[^"]+?\.(?:jpg|jpeg|png|webp))[^"]*",\s*(\d+),\s*(\d+)\]', r.text)
    for url, h, w in pairs[:12]:
        if "gstatic" in url or "google" in url or min(int(h), int(w)) < 300: continue
        im = fetch_img(url.encode().decode("unicode_escape"))
        if im: return im
    return None

def from_web(query):
    from ddgs import DDGS
    for hit in DDGS().images(query + " product", max_results=8):
        if good_match(query, hit.get("title", "")):
            im = fetch_img(hit["image"])
            if im: return im
    return None

products = list(csv.DictReader(open("products.csv", encoding="utf-8")))
manifest = {r["sku"]: r for r in csv.DictReader(open("media_manifest.csv", encoding="utf-8"))}
missing = []
for p in products:
    sku = p["sku"]
    if ONLY and sku not in ONLY: continue
    path = os.path.join(OUT, p["image"])
    if os.path.exists(path): continue
    m = manifest[sku]; im = None; src = ""
    if m.get("image_url"):
        im = fetch_img(m["image_url"]); src = "direct"
    for q in [m["query"]] + variants(m["query"]):
        for shop in SHOPS:
            if im: break
            try: im = from_shop(shop, q); src = shop[0]
            except Exception: pass
            time.sleep(0.4)
        if not im:
            try: im = from_google(q); src = "google"
            except Exception: pass
        if not im:
            try: im = from_web(q); src = "web"
            except Exception: pass
        if im: break
    if im:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        im.convert("RGBA").save(path, "WEBP", quality=85)
        print(f"ok   {sku:8} {src:10} -> {path}")
    else:
        print(f"MISS {sku:8} {m['query']}"); missing.append(sku)
if "--placeholders" in sys.argv:
    from PIL import ImageDraw
    for p in products:
        if p["sku"] in missing:
            im = Image.new("RGB", (500, 500), "white"); d = ImageDraw.Draw(im)
            d.rectangle([20, 20, 480, 480], outline="#0EA5E9", width=4)
            d.text((250, 220), p["category"].upper(), fill="#0EA5E9", anchor="mm")
            d.text((250, 260), p["brand"], fill="#0B0F14", anchor="mm")
            path = os.path.join(OUT, p["image"]); os.makedirs(os.path.dirname(path), exist_ok=True)
            im.save(path, "WEBP"); print("placeholder", p["sku"])
open("missing.txt", "w").write("\n".join(missing))
print(f"\nDone. {len(missing)} missing (see missing.txt).")
