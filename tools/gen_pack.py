"""Generate the EssenceOfDoom resource pack.

Textures are drawn procedurally, matching the plugin's item ids:
  * essence_dark / essence_sin / essence_blood / essence_doom  - swirling vials
  * stone_<id>                                                 - 16 awakening stones
  * ab_<abilityId>                                             - ability tokens

Every icon is hand-plotted pixel by pixel so the pack needs no source art.
"""
import json
import os
import time
import zipfile

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK = os.path.join(ROOT, "resourcepack")
DIST = os.path.join(ROOT, "dist")
NS = "essence"
PACK_FORMAT = 75
S = 16


def save_png(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(obj, fh, indent=1)
        fh.write("\n")


def hexrgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def blend(a, b, t):
    return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3))


# --------------------------------------------------------------- item plumbing
def flat_item(name, texture):
    write_json(os.path.join(PACK, f"assets/{NS}/models/item/{name}.json"),
               {"parent": "minecraft:item/generated",
                "textures": {"layer0": f"{NS}:item/{texture}"}})
    write_json(os.path.join(PACK, f"assets/{NS}/items/{name}.json"),
               {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})


# ------------------------------------------------------------------- essences
def essence_icon(colour, glow, style):
    """A potion-shaped vial with swirling contents."""
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    px = img.load()
    base = hexrgb(colour)
    hi = glow if isinstance(glow, tuple) else hexrgb(glow)

    # cork
    for x in range(6, 10):
        px[x, 1] = (92, 64, 40, 255)
        px[x, 2] = (120, 84, 52, 255)
    # neck
    for y in range(3, 6):
        for x in range(6, 10):
            px[x, y] = (200, 206, 214, 200)
    # glass body
    for y in range(6, 15):
        for x in range(3, 13):
            edge = x in (3, 12) or y == 14
            px[x, y] = (210, 216, 224, 170) if edge else (150, 158, 170, 120)

    # liquid
    for y in range(8, 14):
        for x in range(4, 12):
            t = (y - 8) / 6.0
            c = blend(shade(base, 1.35), shade(base, 0.6), t)
            px[x, y] = (*c, 235)

    # style flourish
    if style == "swirl":
        for i in range(6):
            x = 5 + (i % 3) * 2
            y = 9 + (i // 3) * 2
            px[x, y] = (*hi, 255)
    elif style == "spike":
        for i in range(4):
            px[5 + i, 13 - i] = (*hi, 255)
            px[10 - i, 13 - i] = (*hi, 255)
        px[7, 9] = px[8, 9] = (*hi, 255)
    elif style == "drop":
        for y in range(9, 13):
            w = 1 + (y - 9) // 2
            for x in range(8 - w, 8 + w):
                px[x, y] = (*hi, 255)
    elif style == "void":
        for y in range(8, 14):
            for x in range(4, 12):
                d = abs(x - 8) + abs(y - 11)
                if d % 3 == 0:
                    px[x, y] = (*hi, 255)
        px[7, 10] = px[8, 10] = (10, 4, 20, 255)
    # glass highlight
    px[4, 7] = (255, 255, 255, 210)
    px[4, 8] = (255, 255, 255, 150)
    return img


# ------------------------------------------------------------ awakening stones
def stone_icon(colour, rarity, seed):
    """A sharply faceted crystal: flat planes, hard edges, offset highlight."""
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    px = img.load()
    base = hexrgb(colour)
    light = shade(base, 1.75)
    mid = shade(base, 1.05)
    dark = shade(base, 0.5)
    deep = shade(base, 0.28)

    # A hexagonal crystal silhouette (like an amethyst shard seen head-on).
    # rows: (y, half-width) - widest at the girdle, tapering to a point.
    profile = [
        (2, 1),   # apex
        (3, 2),
        (4, 3),
        (5, 4),
        (6, 5),
        (7, 5),
        (8, 5),
        (9, 4),
        (10, 4),
        (11, 3),
        (12, 2),
        (13, 1),
    ]
    cx = 7.5

    for y, hw in profile:
        for dx in range(-hw, hw + 1):
            x = int(cx + dx)
            if not (0 <= x < S):
                continue
            # Hard facet planes: left third catches light, right third falls
            # into shadow, the centre is the body. Crisp boundaries, no blur.
            frac = (dx + hw) / (2 * hw) if hw else 0.5
            if frac < 0.33:
                c = light
            elif frac < 0.62:
                c = mid
            elif frac < 0.86:
                c = dark
            else:
                c = deep
            # a hard girdle line across the widest rows
            if y in (7, 8) and frac >= 0.62:
                c = deep
            px[x, y] = (c[0], c[1], c[2], 255)

    # crisp top facet (the table of the gem) - a flat plane, not a dot
    for x in range(6, 10):
        px[x, 3] = (light[0], light[1], light[2], 255)
    for x in range(6, 10):
        px[x, 4] = (mid[0], mid[1], mid[2], 255)

    # offset specular highlight (top-left, as if lit from above-left)
    px[6, 4] = (255, 255, 255, 245)
    px[6, 5] = (250, 250, 255, 200)
    px[7, 4] = (245, 245, 252, 160)

    # dark outline for readability against any background
    outline = [(2, 1), (3, 2), (4, 3), (5, 4), (6, 5), (7, 5), (8, 5),
               (9, 4), (10, 4), (11, 3), (12, 2), (13, 1)]
    for y, hw in outline:
        for dx in (-hw, hw):
            x = int(cx + dx)
            if 0 <= x < S:
                px[x, y] = (16, 10, 24, 255)

    # rarity marker: a row of chips along the base
    chips = {"uncommon": 1, "rare": 2, "epic": 3, "legendary": 5}.get(rarity, 1)
    start = 8 - chips // 2
    for i in range(chips):
        x = start + i
        if 0 <= x < S:
            px[x, 14] = (235, 220, 255, 255) if rarity == "legendary" else (210, 200, 235, 230)

    # seed accent: a couple of facet glints so no two stones are identical
    for i in range(3):
        gx = 4 + (seed * 3 + i * 4) % 9
        gy = 5 + (seed * 5 + i * 2) % 6
        if 0 <= gx < S and 0 <= gy < S and px[gx, gy][3] > 0:
            px[gx, gy] = (255, 255, 255, 200)
    return img


# ----------------------------------------------------------- ability tokens
def ability_icon(kind, colour, seed):
    """Distinct silhouettes per ability kind, tinted by essence."""
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    px = img.load()
    base = hexrgb(colour)
    light = shade(base, 1.7)
    dark = shade(base, 0.4)

    def put(x, y, c):
        if 0 <= x < S and 0 <= y < S:
            if len(c) == 4:
                px[x, y] = c
            else:
                px[x, y] = (c[0], c[1], c[2], 255)

    if kind == "active":
        # a bolt / spell shard
        for i in range(11):
            put(4 + i, 12 - i, light if i % 2 else base)
        for i in range(5):
            put(3 + i, 8 - i // 2, dark)
        put(8, 8, (255, 255, 255, 230))
    elif kind == "aura":
        # concentric rings
        for y in range(S):
            for x in range(S):
                d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
                if 6.2 <= d <= 7.0 or 3.4 <= d <= 4.0:
                    px[x, y] = (*base, 235)
                elif d <= 1.6:
                    px[x, y] = (*light, 255)
        put(7, 7, (255, 255, 255, 255))
    elif kind == "familiar":
        # a small winged shape
        for i in range(6):
            put(2 + i, 5 + i, dark)
            put(13 - i, 5 + i, dark)
        for y in range(6, 12):
            for x in range(6, 10):
                px[x, y] = (*base, 255)
        put(6, 6, (*light, 255))
        put(9, 6, (*light, 255))
        put(7, 8, (255, 255, 255, 240))
        put(8, 8, (255, 255, 255, 240))
    elif kind == "passive":
        # a shield
        for y in range(3, 13):
            w = 4 if y < 10 else 3 - (y - 10)
            for x in range(7 - w, 8 + w):
                px[x, y] = (*base, 255)
        for y in range(4, 12):
            for x in range(7, 9):
                px[x, y] = (*light, 255)
        put(7, 7, (255, 255, 255, 255))
    else:  # utility
        # an eye
        for y in range(S):
            for x in range(S):
                d = ((x - 7.5) ** 2 + ((y - 7.5) * 1.6) ** 2) ** 0.5
                if 4.6 <= d <= 5.6:
                    px[x, y] = (*dark, 255)
                elif d <= 4.4:
                    px[x, y] = (*base, 255)
        for y in range(6, 10):
            for x in range(6, 10):
                px[x, y] = (*light, 255)
        for (x, y) in [(7, 7), (8, 7), (7, 8), (8, 8)]:
            px[x, y] = (12, 8, 18, 255)

    # seed-based accent so two same-kind icons are never identical
    for i in range(6):
        ax = (seed * 7 + i * 5) % S
        ay = (seed * 11 + i * 3) % S
        put(ax, ay, light)
        put(ax, (ay + 1) % S, dark)
    return img


# ----------------------------------------------------------------- pack icon
def pack_icon():
    img = Image.new("RGBA", (128, 128), (14, 8, 24, 255))
    d = ImageDraw.Draw(img)
    for y in range(128):
        for x in range(128):
            if (x // 8 + y // 8) % 2 == 0:
                r, g, b, a = img.getpixel((x, y))
                img.putpixel((x, y), (r + 8, g + 6, b + 14, a))
    # three essences converging into one
    d.ellipse([16, 20, 56, 60], fill=(74, 58, 107, 255))
    d.ellipse([72, 20, 112, 60], fill=(160, 27, 27, 255))
    d.ellipse([44, 62, 84, 102], fill=(138, 31, 61, 255))
    d.ellipse([52, 44, 76, 68], fill=(123, 47, 247, 255))
    d.ellipse([60, 52, 68, 60], fill=(255, 255, 255, 255))
    return img


# ----------------------------------------------------------------------- main
def main():
    data = json.load(open(os.path.join(ROOT, "essences.json"), encoding="utf-8"))
    essences = data["essences"]
    abilities = data["abilities"]
    stones = data["stones"]

    write_json(os.path.join(PACK, "pack.mcmeta"),
               {"pack": {"pack_format": PACK_FORMAT,
                         "description": "EssenceOfDoom - essences, awakening stones & abilities"}})

    lang = {}
    count = 0

    # ---- essences
    styles = {"dark": "swirl", "sin": "spike", "blood": "drop", "doom": "void"}
    for eid, e in essences.items():
        name = f"essence_{eid}"
        img = essence_icon(e["colour"], shade(hexrgb(e["colour"]), 1.9), styles.get(eid, "swirl"))
        save_png(img, os.path.join(PACK, f"assets/{NS}/textures/item/{name}.png"))
        flat_item(name, name)
        lang[f"item.{NS}.{name}"] = e["name"]
        count += 1

    # ---- stones
    for i, s in enumerate(stones):
        name = f"stone_{s['id']}"
        img = stone_icon(s["colour"], s["rarity"], i + 1)
        save_png(img, os.path.join(PACK, f"assets/{NS}/textures/item/{name}.png"))
        flat_item(name, name)
        lang[f"item.{NS}.{name}"] = s["name"]
        count += 1

    # ---- ability tokens, grouped by the stone that teaches them
    for i, (aid, a) in enumerate(sorted(abilities.items())):
        name = f"ab_{aid}"
        # colour by the essence the ability reads as
        colour = "#6b5aa8"
        if "blood" in aid or aid in ("crimson_vitality", "sanguine_armour", "act_siphon"):
            colour = "#a01b1b"
        elif "doom" in aid or "ruin" in aid or "mark" in aid or "execute" in aid:
            colour = "#7b2ff7"
        elif ("sin" in aid or "gluttony" in aid or "iron_hide" in aid
              or "brutal" in aid or "quake" in aid):
            colour = "#8a1f3d"
        # per-ability tint so two same-kind, same-essence icons never collide
        r, g, b = hexrgb(colour)
        hsh = sum(ord(ch) * (idx + 1) for idx, ch in enumerate(aid))
        r = max(0, min(255, r + (hsh % 41) - 20))
        g = max(0, min(255, g + ((hsh // 41) % 41) - 20))
        b = max(0, min(255, b + ((hsh // 1681) % 41) - 20))
        colour = f"#{r:02x}{g:02x}{b:02x}"
        img = ability_icon(a["kind"], colour, hsh)
        save_png(img, os.path.join(PACK, f"assets/{NS}/textures/item/{name}.png"))
        flat_item(name, name)
        lang[f"item.{NS}.{name}"] = a["name"]
        count += 1

    write_json(os.path.join(PACK, f"assets/{NS}/lang/en_us.json"), lang)
    save_png(pack_icon(), os.path.join(PACK, "pack.png"))

    # ---- zip it (write to a fresh name so a held handle cannot block us)
    os.makedirs(DIST, exist_ok=True)
    stamp = time.strftime("%H%M%S")
    out = os.path.join(DIST, f"EssenceOfDoom-ResourcePack.zip")
    tmp = os.path.join(DIST, f"EssenceOfDoom-ResourcePack.{stamp}.zip")
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zf:
        for base, _, files in os.walk(PACK):
            for fn in files:
                full = os.path.join(base, fn)
                zf.write(full, os.path.relpath(full, PACK))
    try:
        if os.path.exists(out):
            os.remove(out)
        os.replace(tmp, out)
    except OSError:
        out = tmp      # keep the timestamped one if the old file is locked

    print(f"textures written: {count}")
    print(f"  essences: {len(essences)}")
    print(f"  stones:   {len(stones)}")
    print(f"  tokens:   {len(abilities)}")
    print("wrote", out, os.path.getsize(out), "bytes")


if __name__ == "__main__":
    main()
