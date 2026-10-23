#!/usr/bin/env python3
"""The mod's textures, made from vanilla's: python3 tools/make_textures.py [--preview]

Writes into src/main/resources/assets/skullsanctum/textures/:
- block/soul_tank_*: the cauldron in blackstone colours with a soul rune; soul_essence (the souls in the tank, from
  lava_still) and soul_tank_flame (soul fire turned green), both animated like their originals; particle/soul_bubble_*
- block/sacrificial_altar_*: A3's dark rune stone with the bone altar's green dots (glinting when awake, animated) and
  gold brackets in the corners
- block/head_atlas_*: the enchanting table in amethyst cloth with gold, soul-green crystals, blackstone; its book
- particle/soul_flame: the soul fire flame particle turned green
- item/soul: an animated soul wisp; item/guide_book: vanilla's book in the mod's colour with a soul on the cover
- block/portable_atlas: the skeleton skull in the icon's colours with soul-green eyes that shimmer and flicker without pause
With --preview also run/texture-preview/textures.png: every texture at 8x, side by side.
"""
import colorsys
import random
import math
import io
import json
import sys
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'src/main/resources/assets/skullsanctum/textures'
JAR = Path.home() / '.gradle/caches/fabric-loom/26.2/minecraft-client.jar'

DARK, LIGHT = (0x1B, 0x18, 0x20), (0x6E, 0x66, 0x7A)  # blackstone's darkest and lightest
SOUL_GREEN, SOUL_LIGHT, SOUL_DARK = (0x55, 0xE0, 0x6A), (0xD8, 0xFF, 0xD0), (0x1C, 0x6A, 0x2E)  # souls are green, like their flame
GOLD, GOLD_DARK = (0xF2, 0xC2, 0x3B), (0xB0, 0x7A, 0x1C)
BOOK_LEATHER = '#3d3348'
MAIN_RED = 0x65  # make-book-texture.py: the red of the vanilla cover's main brown

jar = zipfile.ZipFile(JAR)
written = []


def vanilla(path):
    return Image.open(io.BytesIO(jar.read(f'assets/minecraft/textures/{path}.png'))).convert('RGBA')


def vanilla_meta(path):
    return jar.read(f'assets/minecraft/textures/{path}.png.mcmeta').decode()


def save(image, path, meta=None):
    file = OUT / f'{path}.png'
    file.parent.mkdir(parents=True, exist_ok=True)
    image.save(file)
    if meta is not None:
        (OUT / f'{path}.png.mcmeta').write_text(meta)
    written.append((path, image))


def lum(r, g, b):
    return (0.299 * r + 0.587 * g + 0.114 * b) / 255


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def recolour(image, low, high, gamma=1.0):
    """Every pixel's brightness, from the image's darkest to its lightest, mapped onto low..high."""
    values = [lum(r, g, b) for r, g, b, a in image.getdata() if a]
    lo, hi = min(values), max(values)
    out = Image.new('RGBA', image.size)
    out.putdata([(*mix(low, high, ((lum(r, g, b) - lo) / max(hi - lo, 1e-6)) ** gamma), a) if a else (0, 0, 0, 0)
                 for r, g, b, a in image.getdata()])
    return out


def hue(image, target, saturation=1.0, value=1.0):
    out = Image.new('RGBA', image.size)
    pixels = []
    for r, g, b, a in image.getdata():
        h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
        r2, g2, b2 = colorsys.hsv_to_rgb(target, min(1, s * saturation), min(1, v * value))
        pixels.append((round(r2 * 255), round(g2 * 255), round(b2 * 255), a))
    out.putdata(pixels)
    return out


def put(image, points, colour):
    for x, y in points:
        image.putpixel((x, y), (*colour, 255))


# ---------------------------------------------------------------- soul tank

side = recolour(vanilla('block/cauldron_side'), DARK, LIGHT)
# a soul rune on the side: an upright diamond with a dot, glowing teal
put(side, [(7, 4), (8, 4), (6, 5), (9, 5), (6, 6), (9, 6), (7, 7), (8, 7)], SOUL_GREEN)
put(side, [(7, 5), (8, 5), (7, 6), (8, 6)], SOUL_DARK)
put(side, [(7, 9), (8, 9)], SOUL_GREEN)
save(side, 'block/soul_tank_side')
save(recolour(vanilla('block/cauldron_top'), DARK, LIGHT), 'block/soul_tank_top')
save(recolour(vanilla('block/cauldron_inner'), DARK, mix(DARK, LIGHT, 0.6)), 'block/soul_tank_inner')
save(recolour(vanilla('block/cauldron_bottom'), DARK, LIGHT), 'block/soul_tank_bottom')
# thick and bubbling: vanilla's lava in the soul's greens, with lava's slow animation
save(recolour(vanilla('block/lava_still'), SOUL_DARK, SOUL_LIGHT, 1.15), 'block/soul_essence',
     vanilla_meta('block/lava_still'))
# the same for shader packs that read LabPBR maps: the souls emit light (specular alpha), brighter where they are bright
essence = Image.open(OUT / 'block/soul_essence.png').convert('RGBA')
specular = Image.new('RGBA', essence.size)
specular.putdata([(40, 10, 0, min(254, 120 + round(lum(r, g, b) * 134))) for r, g, b, a in essence.getdata()])
save(specular, 'block/soul_essence_s', vanilla_meta('block/lava_still'))
# the vial in the Head Atlas shows the same souls in the soul item's colours: its green body, its pale highlights
SOUL_ITEM = [(85, 224, 106), (117, 231, 131), (134, 236, 144), (155, 241, 160), (181, 247, 181), (216, 255, 208)]


def quantize(image, colours, cuts):
    """Every pixel's brightness, from darkest to lightest, mapped onto a palette by these cut points (0 to 1)."""
    values = [lum(r, g, b) for r, g, b, a in image.getdata() if a]
    lo, hi = min(values), max(values)

    def pick(l):
        t = (l - lo) / max(hi - lo, 1e-6)
        for cut, colour in zip(cuts, colours):
            if t < cut:
                return colour
        return colours[-1]

    out = Image.new('RGBA', image.size)
    out.putdata([(*pick(lum(r, g, b)), a) if a else (0, 0, 0, 0) for r, g, b, a in image.getdata()])
    return out


save(quantize(vanilla('block/lava_still'), SOUL_ITEM, [0.36, 0.5, 0.64, 0.78, 0.9]), 'block/soul_vial',
     vanilla_meta('block/lava_still'))
for i in range(5):  # the bubbles popping on it
    save(recolour(vanilla(f'particle/bubble_pop_{i}'), SOUL_DARK, SOUL_LIGHT), f'particle/soul_bubble_{i}')
GREEN = 0.30
save(hue(vanilla('block/soul_fire_0'), GREEN, 1.1, 1.05), 'block/soul_tank_flame', vanilla_meta('block/soul_fire_0'))
save(hue(vanilla('particle/soul_fire_flame'), GREEN, 1.1, 1.05), 'particle/soul_flame')

# ---------------------------------------------------------------- sacrificial altar

polished = vanilla('block/polished_blackstone')
bricks = vanilla('block/polished_blackstone_bricks')
chiseled = vanilla('block/chiseled_polished_blackstone')


# The altar: A3's dark rune stone without its veins, the bone altar's dots on it (soul green, each darkened by up to
# 40 percent; awake, about every second one glints once per cycle) and gold brackets in the top's corners that run
# over the edge and down the side's corners. The side's top row is the stone itself, darker.
GOLD_LIGHT = (0xFF, 0xEC, 0x9A)
SOUL_DEEP = (0x0E, 0x3A, 0x18)
DOT_FRAMES = 16


def altar_dots(seed, count, area):
    rnd = random.Random(seed)
    out = []
    while len(out) < count:
        x, y = rnd.randrange(area[0], area[2]), rnd.randrange(area[1], area[3])
        if (x, y) not in [d[0] for d in out]:
            out.append(((x, y), rnd.random() * 0.4, rnd.randrange(DOT_FRAMES), rnd.random() < 0.6))
    return out


TOP_DOTS = altar_dots(21, 22, (2, 2, 14, 14))
SIDE_DOTS = altar_dots(22, 5, (2, 6, 14, 15))


def dot_colour(shade, flash_frame, flashes, frame, formed):
    if not formed:
        return mix((0x3A, 0x34, 0x40), (0, 0, 0), shade)
    base = mix(SOUL_GREEN, (0, 0, 0), shade)
    if flashes and (frame - flash_frame) % DOT_FRAMES == 0:
        return SOUL_LIGHT
    if flashes and (frame - flash_frame) % DOT_FRAMES == 1:
        return mix(base, SOUL_LIGHT, 0.45)
    return base


def altar_frame(formed, frame):
    top = recolour(polished, (0x12, 0x10, 0x16), (0x48, 0x42, 0x50))
    side = recolour(chiseled, (0x12, 0x10, 0x16), (0x50, 0x4A, 0x58))
    for x in range(16):
        put(side, [(x, 4)], mix(side.getpixel((x, 4))[:3], (0, 0, 0), 0.5))
    for p, shade, flash, flashes in TOP_DOTS:
        put(top, [p], dot_colour(shade, flash, flashes, frame, formed))
    for p, shade, flash, flashes in SIDE_DOTS:
        put(side, [p], dot_colour(shade, flash, flashes, frame, formed))
    for cx, cy, sx, sy in ((0, 0, 1, 1), (15, 0, -1, 1), (0, 15, 1, -1), (15, 15, -1, -1)):
        put(top, [(cx, cy), (cx + sx, cy), (cx + 2 * sx, cy), (cx, cy + sy), (cx, cy + 2 * sy)], GOLD)
        put(top, [(cx, cy)], GOLD_LIGHT)
    put(side, [(0, 4), (1, 4), (2, 4), (13, 4), (14, 4), (15, 4), (0, 5), (15, 5), (0, 6), (15, 6)], GOLD)
    put(side, [(0, 4), (15, 4)], GOLD_LIGHT)
    put(side, [(2, 4), (13, 4), (0, 6), (15, 6)], GOLD_DARK)
    return top, side


def animated(frames):
    sheet = Image.new('RGBA', (16, 16 * len(frames)))
    for i, frame in enumerate(frames):
        sheet.paste(frame, (0, 16 * i))
    return sheet


ANIMATION = json.dumps({'animation': {'frametime': 3}}, indent=2) + '\n'
asleep_top, asleep_side = altar_frame(False, 0)
save(asleep_top, 'block/sacrificial_altar_top')
save(asleep_side, 'block/sacrificial_altar_side')
awake = [altar_frame(True, f) for f in range(DOT_FRAMES)]
save(animated([t for t, _ in awake]), 'block/sacrificial_altar_top_formed', ANIMATION)
save(animated([s for _, s in awake]), 'block/sacrificial_altar_side_formed', ANIMATION)
save(polished, 'block/sacrificial_altar_bottom')

# ---------------------------------------------------------------- Head Atlas: the enchanting table in the sanctum's style
# Amethyst cloth with a gold band and gold filigree where it ends, faceted soul-green crystals in the corners (the
# side's crystal faces textured too), blackstone instead of obsidian.
CLOTH, CLOTH_DARK = (0x9A, 0x5C, 0xD0), (0x3A, 0x20, 0x5A)


def crystal_top(img, x0, y0):
    colours = {'D': SOUL_DEEP, 'L': SOUL_GREEN, 'M': mix(SOUL_DARK, SOUL_GREEN, 0.55), 'K': SOUL_DARK, 'W': SOUL_LIGHT}
    for dy, row in enumerate(['DDDD', 'DWMD', 'DMKD', 'DDDD']):
        for dx, c in enumerate(row):
            put(img, [(x0 + dx, y0 + dy)], colours[c])


def crystal_side(img, x0):
    """The side's crystal faces, rows 4 to 7 (the side shows rows 4 to 16); the right corner mirrored."""
    colours = {'D': SOUL_DEEP, 'L': SOUL_GREEN, 'M': mix(SOUL_DARK, SOUL_GREEN, 0.55), 'K': SOUL_DARK, 'W': SOUL_LIGHT}
    grid, mask = ['DLMD', 'LWMK', 'LMKK', 'DMKD'], ['####', '####', '####', '###.']
    for dy in range(4):
        for dx in range(4):
            mx = dx if x0 == 0 else 3 - dx
            if mask[dy][mx] == '#':
                put(img, [(x0 + dx, 4 + dy)], colours[grid[dy][mx]])


def atlas_texture(name):
    img = vanilla(f'block/enchanting_table_{name}')
    px = img.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = px[x, y]
            if not a:
                continue
            h, s_, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if 0.4 < h < 0.6 and s_ > 0.15:  # the diamonds: repainted below
                px[x, y] = (*SOUL_DARK, a)
            elif (h > 0.9 or h < 0.05) and s_ > 0.4:  # the red cloth
                px[x, y] = (*mix(CLOTH_DARK, CLOTH, min(1, v / 0.75)), a)
            else:  # the obsidian
                px[x, y] = (*mix(DARK, LIGHT, min(1, v / 0.45)), a)
    return img


atlas_top, atlas_side = atlas_texture('top'), atlas_texture('side')
for cx, cy in ((0, 0), (12, 0), (0, 12), (12, 12)):
    crystal_top(atlas_top, cx, cy)
for cx in (0, 12):
    crystal_side(atlas_side, cx)
# the cloth's ends at the top's edges: a gold band, gold hooks reaching into the cloth
ends = [(x, 0) for x in range(4, 12)] + [(x, 15) for x in range(4, 12)] + [(0, y) for y in range(4, 12)] + [(15, y) for y in range(4, 12)]
put(atlas_top, ends, GOLD_DARK)
put(atlas_top, [(6, 1), (9, 1), (6, 14), (9, 14), (1, 6), (1, 9), (14, 6), (14, 9)], GOLD)
put(atlas_top, [(7, 2), (8, 2), (7, 13), (8, 13), (2, 7), (2, 8), (13, 7), (13, 8)], GOLD_LIGHT)
# the cloth hanging down the side: gold on its lowest pixels, a gold drop at the tip
tips = {}
for y in range(4, 9):
    for x in range(4, 12):
        r, g, b, a = atlas_side.getpixel((x, y))
        if colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)[1] > 0.3 and 0.7 < colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)[0] < 0.85:
            tips[x] = max(tips.get(x, 0), y)
for x, y in tips.items():
    put(atlas_side, [(x, y)], GOLD)
put(atlas_side, [(7, max(tips.values()) + 1), (8, max(tips.values()) + 1)], GOLD_LIGHT)
save(atlas_top, 'block/head_atlas_top')
save(atlas_side, 'block/head_atlas_side')
save(recolour(vanilla('block/enchanting_table_bottom'), DARK, LIGHT), 'block/head_atlas_bottom')

# the floating book: vanilla's book in the cloth's amethyst, its gold corners kept
book = vanilla('entity/enchantment/enchanting_table_book')
px = book.load()
for y in range(book.height):
    for x in range(book.width):
        r, g, b, a = px[x, y]
        if not a:
            continue
        h, s_, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
        if 0.11 < h < 0.18 and s_ > 0.6 and v > 0.8:
            px[x, y] = (*GOLD, a)
        elif 0.02 < h < 0.11 and s_ > 0.35:
            px[x, y] = (*mix(CLOTH_DARK, CLOTH, min(1, v / 0.8)), a)
# a wither skeleton skull on both outer covers (x 6-11, the right one mirrored at x 22-27; the spine column stays).
# The book floats on its side, so the skull's top points at the spine column (x 11 and x 22) to stand upright.
WITHER, WITHER_DARK = (0x4A, 0x4A, 0x52), (0x26, 0x26, 0x2C)
BOOK_SKULL = ['.BBB.', 'BBBBD', 'BEBED', 'BBBBD', '.D.D.']  # B bone, D shade (light from the top left), E eye
for dy, row in enumerate(BOOK_SKULL):
    for dx, c in enumerate(row):
        if c in 'BDE':
            colour = {'B': WITHER, 'D': WITHER_DARK, 'E': SOUL_GREEN}[c]
            put(book, [(10 - dy, 6 - dx), (23 + dy, 6 - dx)], colour)
save(book, 'block/head_atlas_book')

# ---------------------------------------------------------------- soul item: an animated wisp
# A round head low on the left with a tail that curls up and away in an S; the tail sways, its tip flickers and the
# core pulses. Frames are stacked vertically (item textures animate like block textures).

SOUL_FRAMES = 16
EDGE, BODY, CORE = SOUL_DARK, SOUL_GREEN, SOUL_LIGHT
HEAD = (6.0, 10.2, 4.0)  # centre x, centre y, radius


def soul_frame(t):
    """Frame t of SOUL_FRAMES: the shape (head plus a tube of discs along an S curve), outline, shading, eyes."""
    phase = 2 * math.pi * t / SOUL_FRAMES
    hx, hy, hr = HEAD
    hy += 0.3 * math.sin(phase)  # the head bobs gently
    discs = [(hx, hy, hr)]
    steps = 80
    for i in range(steps + 1):
        u = i / steps  # 0 at the head, 1 at the tip
        # an S: out to the right, back, and out again at the tip; the upper part sways more
        wave = math.sin(u * math.pi * 1.5 + 0.4) * 2.4 * (0.4 + u)
        sway = math.sin(phase - u * 3.0) * 1.3 * u
        curl = -2.2 * max(0.0, u - 0.75) / 0.25  # the tip rolls back to the left
        cx = hx + 1.2 + 3.0 * u + wave + sway + curl
        cy = (hy - hr * 0.55) - u * (hy - hr * 0.55 - 1.0)
        discs.append((cx, cy, (hr * 0.72) * (1 - u) ** 1.1 + 0.5))
    inside = set()
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5, y + 0.5
            if any((px - cx) ** 2 + (py - cy) ** 2 <= r * r for cx, cy, r in discs):
                inside.add((x, y))
    img = Image.new('RGBA', (16, 16))
    pulse = 0.85 + 0.15 * math.sin(phase)
    lx, ly = hx - 1.0, hy - 1.2  # the light comes from the upper left
    for (x, y) in inside:
        edge = any((x + dx, y + dy) not in inside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            colour = EDGE
        else:
            d = math.hypot(x + 0.5 - lx, y + 0.5 - ly) / (hr * pulse)
            colour = mix(CORE, BODY, min(1.0, max(0.0, (d - 0.25) / 0.8)))
        img.putpixel((x, y), (*colour, 255))
    ey = round(hy - 0.9)
    blink = t % SOUL_FRAMES == 11
    for ex in (round(hx - 1.7), round(hx + 0.9)):
        if blink:
            img.putpixel((ex, ey), (*mix(EDGE, BODY, 0.3), 255))
        else:
            put(img, [(ex, ey), (ex, ey + 1)], EDGE)
    return img


soul_frames = [soul_frame(t) for t in range(SOUL_FRAMES)]
sheet = Image.new('RGBA', (16, 16 * SOUL_FRAMES))
for t, frame in enumerate(soul_frames):
    sheet.paste(frame, (0, 16 * t))
save(sheet, 'item/soul', json.dumps({'animation': {'frametime': 2, 'interpolate': False}}, indent=2) + '\n')

# ---------------------------------------------------------------- portable Head Atlas: the mod icon's skull as a skeleton skull
# The skeleton skull of the 26.2 jar in the icon's greys (bone, shadow, dark, ink), its face redrawn after the
# photographed skull block: 2 × 2 soul-green eyes, a nose, two dark teeth corners, and a crack running from the top of
# the skull down between the eyes (texture round 3, variant 4 "Cracked"). The eyes sparkle without pause (eye round 3,
# proposal 5): every one of their eight pixels twinkles on its own rhythm through the soul item's greens up to the
# glint and back ("starlight", periods of 24 and 36 ticks), and over that the whole of each eye flickers like a flame
# between the soul green and the two next greens, a new shade every 3 ticks from a fixed random table, each eye its
# own. One frame per tick of the 144-tick cycle, equal frames shared; they never go dark. The frames are 64 × 32, the
# layout of the skull model's head (top 8,0; bottom 16,0; right 0,8; front 8,8; left 16,8; back 24,8), as a vertical
# strip; the block atlas animates it for the block entity renderer and the item model alike.
SKULL_BONE, SKULL_SHADOW, SKULL_DARK, SKULL_INK, SKULL_OUTLINE = (106, 106, 116), (74, 74, 82), (48, 48, 56), (18, 16, 22), (14, 11, 18)
SKULL_EYE, SKULL_GLINT = SOUL_GREEN, SOUL_LIGHT
SKULL_RAMP = [SOUL_GREEN, SOUL_ITEM[2], SOUL_ITEM[4], SOUL_LIGHT, SOUL_ITEM[4], SOUL_ITEM[2]]  # a pixel's twinkle
SKULL_FLICKER = [SOUL_GREEN, SOUL_ITEM[1], SOUL_ITEM[2]]  # the whole eye's flame
SKULL_PHASE = [0, 7, 3, 10, 5, 1, 8, 4]
SKULL_PERIOD = [24, 36, 24, 36, 36, 24, 36, 24]
SKULL_CYCLE, SKULL_FLICKER_STEP = 144, 3
SKULL_FACES = {'top': (8, 0), 'bottom': (16, 0), 'right': (0, 8), 'front': (8, 8), 'left': (16, 8), 'back': (24, 8)}
SKULL_PALETTE = {'G': SKULL_BONE, 'g': SKULL_SHADOW, 'd': SKULL_DARK, 'k': SKULL_INK, 'K': SKULL_OUTLINE, 'E': SKULL_EYE}
SKULL_FRONT = ['gGGdGGgg',
               'GGGkgGgg',
               'GGggkgGg',
               'GEEGgEEg',
               'GEEggEEg',
               'gGGddGgg',
               'gkGGGGkg',
               'gdgggggd']
SKULL_TOP_CRACK = {(3, 7): SKULL_DARK, (3, 6): SKULL_INK, (3, 5): SKULL_INK, (2, 4): SKULL_DARK}
SKULL_EYES = [(1, 3), (2, 3), (1, 4), (2, 4), (5, 3), (6, 3), (5, 4), (6, 4)]
_rng = random.Random(7)
SKULL_SHADES = [_rng.random() for _ in range(SKULL_CYCLE // SKULL_FLICKER_STEP)]


def skull_eyes(t):
    """The colours of the eight eye pixels (left eye TL, TR, BL, BR, then the right eye) at tick t of the cycle."""
    k = (t // SKULL_FLICKER_STEP) % len(SKULL_SHADES)
    flame = [SKULL_FLICKER[int(SKULL_SHADES[k] * 3)], SKULL_FLICKER[int(SKULL_SHADES[(k + 17) % len(SKULL_SHADES)] * 3)]]
    colours = []
    for i in range(8):
        pos = ((t + SKULL_PHASE[i] * 2) % SKULL_PERIOD[i]) * len(SKULL_RAMP) // SKULL_PERIOD[i]
        star = SKULL_RAMP[pos]
        colours.append(flame[i // 4] if star == SOUL_GREEN else star)  # a pixel at rest takes the flame's shade
    return tuple(colours)


def skull_grey(value):
    """The skeleton texture's greys (73..227) in the icon's four tones."""
    if value <= 73:
        return SKULL_INK
    if value <= 142:
        return SKULL_DARK
    if value <= 173:
        return SKULL_SHADOW
    return SKULL_BONE


def skull_frame(eyes):
    skeleton = vanilla('entity/skeleton/skeleton')
    frame = Image.new('RGBA', (64, 32))
    for name, (ox, oy) in SKULL_FACES.items():
        for y in range(8):
            for x in range(8):
                r, g, b, a = skeleton.getpixel((ox + x, oy + y))
                if a:
                    put(frame, [(ox + x, oy + y)], skull_grey(r))
    fx, fy = SKULL_FACES['front']
    for y, row in enumerate(SKULL_FRONT):
        for x, c in enumerate(row):
            put(frame, [(fx + x, fy + y)], SKULL_PALETTE[c])
    tx, ty = SKULL_FACES['top']
    for (x, y), colour in SKULL_TOP_CRACK.items():
        put(frame, [(tx + x, ty + y)], colour)
    # the skeleton's bottom is a ring around a hole: a floating skull shows its bottom, so it is closed in the shadow
    # tone, with the spinal opening as a dark 2 x 2 spot at the back
    bx, by = SKULL_FACES['bottom']
    put(frame, [(bx + x, by + y) for x in range(1, 7) for y in range(1, 7)], SKULL_SHADOW)
    put(frame, [(bx + x, by + y) for x in (3, 4) for y in (5, 6)], SKULL_INK)
    for (x, y), colour in zip(SKULL_EYES, eyes):
        put(frame, [(fx + x, fy + y)], colour)
    return frame


skull_unique = []  # the distinct eye states, in order of first appearance: the strip's frames
skull_sequence = []  # (frame, ticks) over the cycle, equal neighbours merged
for t in range(SKULL_CYCLE):
    eyes = skull_eyes(t)
    if eyes not in skull_unique:
        skull_unique.append(eyes)
    index = skull_unique.index(eyes)
    if skull_sequence and skull_sequence[-1][0] == index:
        skull_sequence[-1] = (index, skull_sequence[-1][1] + 1)
    else:
        skull_sequence.append((index, 1))
skull_frames = [skull_frame(eyes) for eyes in skull_unique]
sheet = Image.new('RGBA', (64, 32 * len(skull_frames)))
for t, frame in enumerate(skull_frames):
    sheet.paste(frame, (0, 32 * t))
save(sheet, 'block/portable_atlas', json.dumps({'animation': {'interpolate': False, 'width': 64, 'height': 32,
     'frames': [{'index': index, 'time': ticks} for index, ticks in skull_sequence]}}, indent=2) + '\n')

# ---------------------------------------------------------------- guide book

leather = tuple(int(BOOK_LEATHER.lstrip('#')[i:i + 2], 16) for i in (0, 2, 4))
book = vanilla('item/book')
pixels = []
for r, g, b, a in book.getdata():
    if a and max(r, g, b) - min(r, g, b) >= 16:
        f = r / MAIN_RED
        r, g, b = (min(255, round(c * f)) for c in leather)
    pixels.append((r, g, b, a))
book.putdata(pixels)
# the mark: a soul wisp like the soul item, its tail leaning to the top right with the cover
GUIDE_SOUL = ['.....K.',
              '....KG.',
              '..KKGK.',
              '.KLWGGK',
              'KLWGGGK',
              'KGEGEGK',
              '.KKGGK.',
              '...KK..']
soul_colours = {'K': SOUL_DARK, 'G': SOUL_GREEN, 'L': (0x9A, 0xF5, 0xA0), 'W': SOUL_LIGHT, 'E': (0x0E, 0x3A, 0x18)}
for dy, row in enumerate(GUIDE_SOUL):
    for dx, c in enumerate(row):
        if c in soul_colours:
            put(book, [(5 + dx, 2 + dy)], soul_colours[c])
save(book, 'item/guide_book')

if '--preview' in sys.argv:
    scale, pad = 8, 8
    frames = [img.crop((0, 0, 16, 16)) for _, img in written]
    sheet = Image.new('RGBA', (len(frames) * (16 * scale + pad) + pad, 16 * scale + 2 * pad), (0x30, 0x30, 0x30, 255))
    for i, img in enumerate(frames):
        sheet.alpha_composite(img.resize((16 * scale, 16 * scale), Image.NEAREST), (pad + i * (16 * scale + pad), pad))
    strip = Image.new('RGBA', (SOUL_FRAMES * (16 * scale + pad) + pad, 16 * scale + 2 * pad), (0x30, 0x30, 0x30, 255))
    for t, frame in enumerate(soul_frames):
        strip.alpha_composite(frame.resize((16 * scale, 16 * scale), Image.NEAREST), (pad + t * (16 * scale + pad), pad))
    (ROOT / 'run/texture-preview').mkdir(parents=True, exist_ok=True)
    strip.save(ROOT / 'run/texture-preview/soul-frames.png')
    gif = []
    for frame in soul_frames:
        bg = Image.new('RGBA', (16 * 12, 16 * 12), (0x30, 0x30, 0x30, 255))
        bg.alpha_composite(frame.resize((16 * 12, 16 * 12), Image.NEAREST))
        gif.append(bg.convert('P', palette=Image.ADAPTIVE))
    gif[0].save(ROOT / 'run/texture-preview/soul.gif', save_all=True, append_images=gif[1:], duration=100, loop=0)
    target = ROOT / 'run/texture-preview/textures.png'
    target.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(target)
    print('preview', target)
print('\n'.join(path for path, _ in written))
