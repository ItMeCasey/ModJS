"""Draws the ModJS icon as 32x32 pixel art and writes it at several sizes.

Run from the repository root: python branding/source/make_icon.py
Writes branding/icon/icon_<size>.png, branding/icon/icon.svg and modjs_logo.png (for the jar).
"""
import math
import random
from pathlib import Path

from PIL import Image

N = 32
OUT = Path(__file__).resolve().parent.parent / "icon"

BG = (24, 22, 38)
BG_EDGE = (46, 42, 70)
BG_TOP = (58, 54, 88)
GOLD = (242, 179, 61)
GOLD_HI = (255, 226, 140)
GOLD_LO = (178, 116, 32)
GOLD_DK = (112, 70, 20)
INK = (14, 12, 24)
CODE = [(122, 168, 255), (126, 217, 87), (255, 255, 255)]  # blue, green, white code tokens
BRACE = (122, 168, 255)
BRACE_HI = (190, 214, 255)

random.seed(11)
px = [[None] * N for _ in range(N)]


def put(x, y, c):
    if 0 <= x < N and 0 <= y < N:
        px[y][x] = c


# Background tile with stepped rounded corners, same as the SMWTI tile.
R = 4
for y in range(N):
    for x in range(N):
        cx = min(max(x, R - 0.5), N - R - 0.5)
        cy = min(max(y, R - 0.5), N - R - 0.5)
        if math.hypot(x - cx, y - cy) <= R:
            px[y][x] = BG
for y in range(N):
    for x in range(N):
        if px[y][x] is None:
            continue
        edge = any(
            not (0 <= x + dx < N and 0 <= y + dy < N) or px[y + dy][x + dx] is None
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
        )
        if edge:
            px[y][x] = BG_TOP if y < N // 2 else BG_EDGE

# Soft glow behind the block.
for y in range(N):
    for x in range(N):
        if px[y][x] == BG and math.hypot(x - 16, y - 15) < 9.5 and (x + y) % 2 == 0:
            px[y][x] = (34, 30, 48)


def in_poly(x, y, pts):
    inside = False
    for i in range(len(pts)):
        x1, y1 = pts[i]
        x2, y2 = pts[(i + 1) % len(pts)]
        if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
            inside = not inside
    return inside


# Isometric block, 16 wide, centered. Faces carry lines of "code".
CX0, CY0 = 8.0, 6.0
T, Rr, C, L = (8, 0), (16, 4), (8, 8), (0, 4)
BL, BR, B = (0, 12), (16, 12), (8, 16)


def off(pts):
    return [(a + CX0, b + CY0) for a, b in pts]


TOP, LEFT, RIGHT = off([T, Rr, C, L]), off([L, C, B, BL]), off([C, Rr, BR, B])
blk = {}
# Left face is a dark screen with a gold frame and lines of code; top is light gold, right is shaded gold.
SCREEN = (30, 27, 54)
LINES = {2: (1, 3, 0), 4: (2, 4, 1), 6: (2, 2, 2), 8: (1, 5, 0)}
for y in range(N):
    for x in range(N):
        fx, fy = x + 0.5, y + 0.5
        if in_poly(fx, fy, TOP):
            blk[(x, y)] = GOLD_HI if (x + y) % 6 else GOLD
        elif in_poly(fx, fy, LEFT):
            depth = int(fy - (CY0 + 4 + (fx - CX0) * 0.5))
            col = int(fx - CX0)
            c = SCREEN
            if depth == 0 or col == 0:
                c = GOLD
            elif depth in LINES:
                s0, ln, tok = LINES[depth]
                if s0 <= col < s0 + ln:
                    c = CODE[tok]
            blk[(x, y)] = c
        elif in_poly(fx, fy, RIGHT):
            depth = int(fy - (CY0 + 8 - (fx - CX0 - 8) * 0.5))
            col = int(fx - CX0 - 8)
            c = GOLD_LO
            if col == 7:
                c = GOLD_DK
            blk[(x, y)] = c

# Outline.
for (x, y) in list(blk):
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
        if (x + dx, y + dy) not in blk and px[y + dy][x + dx] is not None and px[y + dy][x + dx] not in (INK,):
            if (x + dx, y + dy) not in blk:
                px[y + dy][x + dx] = INK
for (x, y), c in blk.items():
    px[y][x] = c
# Bright top-left edge of the block.
for i in range(8):
    put(int(CX0 + 7 - i), int(CY0 + i * 0.5), GOLD_HI)

# Curly braces on both sides.
left = ["..##", ".#..", ".#..", ".#..", "#...", ".#..", ".#..", ".#..", "..##"]
right = ["##..", "..#.", "..#.", "..#.", "...#", "..#.", "..#.", "..#.", "##.."]
for j, row in enumerate(left):
    for i, ch in enumerate(row):
        if ch == "#":
            put(2 + i, 10 + j, BRACE_HI if j < 4 else BRACE)
for j, row in enumerate(right):
    for i, ch in enumerate(row):
        if ch == "#":
            put(26 + i, 10 + j, BRACE_HI if j < 4 else BRACE)

# Cursor bar under the block.
for x in range(13, 19):
    put(x, 26, GOLD)
    put(x, 27, GOLD_LO)

img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
for y in range(N):
    for x in range(N):
        if px[y][x] is not None:
            img.putpixel((x, y), px[y][x] + (255,))

OUT.mkdir(parents=True, exist_ok=True)
for size in (32, 64, 128, 256, 512, 1024):
    img.resize((size, size), Image.NEAREST).save(OUT / f"icon_{size}.png", optimize=True)
img.resize((512, 512), Image.NEAREST).save(OUT / "modjs_logo.png", optimize=True)

rects = []
for y in range(N):
    for x in range(N):
        c = px[y][x]
        if c is not None:
            rects.append(f'<rect x="{x}" y="{y}" width="1.02" height="1.02" fill="#{c[0]:02x}{c[1]:02x}{c[2]:02x}"/>')
(OUT / "icon.svg").write_text(
    f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {N} {N}" shape-rendering="crispEdges">'
    + "".join(rects) + "</svg>\n",
    encoding="utf-8",
)
print("wrote", OUT)
