"""Draws the launcher icon (a striped lighthouse throwing its beam across a night sea) and docs/logo.png.

python tools/icon.py
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
SKY_TOP = (6, 14, 26)
SKY_LOW = (21, 48, 77)
SEA = (11, 30, 50)
TOWER = (233, 223, 200)
STRIPE = (192, 83, 60)
IRON = (21, 27, 36)
LAMP = (244, 185, 78)
HOT = (255, 227, 163)
S = 4  # supersampling


def gradient(w: int, h: int, top, low) -> Image.Image:
    im = Image.new("RGBA", (w, h))
    d = ImageDraw.Draw(im)
    for y in range(h):
        t = y / max(1, h - 1)
        d.line([(0, y), (w, y)], fill=tuple(int(top[i] + (low[i] - top[i]) * t) for i in range(3)) + (255,))
    return im


def foreground(size: int, background: bool) -> Image.Image:
    """The icon on a size x size canvas in 108 dp units; the safe zone is the middle 72."""
    W = size * S
    u = W / 108
    if background:
        im = gradient(W, W, SKY_TOP, SKY_LOW)
        ImageDraw.Draw(im).rectangle((0, 74 * u, W, W), fill=SEA + (255,))
    else:
        im = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    # The beams, soft-edged, to both sides of the lamp.
    # Blurred as a mask over one solid colour: blurring RGBA would drag dark fringes in from the transparent edge.
    mask = Image.new("L", (W, W), 0)
    g = ImageDraw.Draw(mask)
    lamp = (54 * u, 38 * u)
    g.polygon([(lamp[0], lamp[1] - 1.5 * u), (14 * u, 30 * u), (14 * u, 46 * u), (lamp[0], lamp[1] + 1.5 * u)], fill=190)
    g.polygon([(lamp[0], lamp[1] - 1.5 * u), (94 * u, 31 * u), (94 * u, 45 * u), (lamp[0], lamp[1] + 1.5 * u)], fill=225)
    g.ellipse((lamp[0] - 11 * u, lamp[1] - 11 * u, lamp[0] + 11 * u, lamp[1] + 11 * u), fill=210)
    mask = mask.filter(ImageFilter.GaussianBlur(2.2 * u))
    im.alpha_composite(Image.merge("RGBA", (*Image.new("RGB", (W, W), (255, 214, 130)).split(), mask)))
    d = ImageDraw.Draw(im)
    # Rock and tower.
    d.polygon([(30 * u, 80 * u), (40 * u, 74 * u), (54 * u, 72.5 * u), (66 * u, 74 * u), (78 * u, 80 * u)], fill=(26, 34, 48, 255))
    base, top = 74 * u, 45 * u
    wb, wt = 15 * u, 9 * u
    tower = Image.new("L", (W, W), 0)
    ImageDraw.Draw(tower).polygon([(54 * u - wb / 2, base), (54 * u - wt / 2, top), (54 * u + wt / 2, top), (54 * u + wb / 2, base)], fill=255)
    paint = Image.new("RGBA", (W, W), TOWER + (255,))
    pd = ImageDraw.Draw(paint)
    band = (base - top) / 4.5
    for i in (1, 3):
        y0 = top + band * (i - 0.5)
        pd.rectangle((0, y0, W, y0 + band), fill=STRIPE + (255,))
    pd.rectangle((0, 0, 54 * u - 1.5 * u, W), fill=None)
    shade = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    ImageDraw.Draw(shade).rectangle((0, 0, 52 * u, W), fill=(0, 8, 20, 90))
    paint.alpha_composite(shade)
    im.paste(paint, (0, 0), tower)
    d = ImageDraw.Draw(im)
    # Gallery, lamp room, cap.
    d.rectangle((54 * u - 7 * u, top - 2 * u, 54 * u + 7 * u, top), fill=IRON + (255,))
    d.rectangle((54 * u - 4 * u, 33.5 * u, 54 * u + 4 * u, top - 2 * u), fill=HOT + (255,))
    for x in (54 * u - 1.4 * u, 54 * u + 1.4 * u):
        d.line([(x, 33.5 * u), (x, top - 2 * u)], fill=IRON + (255,), width=int(0.8 * u))
    d.pieslice((54 * u - 5.5 * u, 27.5 * u, 54 * u + 5.5 * u, 39.5 * u), 180, 360, fill=IRON + (255,))
    d.line([(54 * u, 27 * u), (54 * u, 24.5 * u)], fill=IRON + (255,), width=int(1 * u))
    if background:
        # Moonlit swell on the water.
        for y, x0, x1 in [(83, 22, 40), (88, 60, 86), (93, 30, 52), (98, 64, 80)]:
            d.line([(x0 * u, y * u), (x1 * u, y * u)], fill=(150, 175, 200, 90), width=int(1.2 * u))
    return im.resize((size, size), Image.LANCZOS)


def main():
    sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for dpi, px in sizes.items():
        out = RES / f"mipmap-{dpi}"
        out.mkdir(parents=True, exist_ok=True)
        fg = 108 * px // 48
        foreground(fg, False).save(out / "ic_launcher_foreground.png")
        full = foreground(fg, True).crop(((fg - px) // 2 - px // 6, (fg - px) // 2 - px // 6, (fg + px) // 2 + px // 6, (fg + px) // 2 + px // 6)).resize((px, px), Image.LANCZOS)
        full.save(out / "ic_launcher.png")
        mask = Image.new("L", full.size, 0)
        ImageDraw.Draw(mask).ellipse((0, 0, px - 1, px - 1), fill=255)
        rnd = Image.new("RGBA", full.size, (0, 0, 0, 0))
        rnd.paste(full, (0, 0), mask)
        rnd.save(out / "ic_launcher_round.png")
    logo = foreground(432, True).crop((54, 54, 378, 378)).resize((256, 256), Image.LANCZOS)
    mask = Image.new("L", logo.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, 255, 255), radius=56, fill=255)
    out = Image.new("RGBA", logo.size, (0, 0, 0, 0))
    out.paste(logo, (0, 0), mask)
    (ROOT / "docs").mkdir(exist_ok=True)
    out.save(ROOT / "docs/logo.png")


if __name__ == "__main__":
    main()
