# Monoline lettering in the style of the Fčil wordmark (docs/brand): stroke 24, round caps and
# joins, baseline y=200, x-height y=100, ascender/cap y=50, descender y=265. Each glyph: (paths,
# dots, width) with x from 0; glyphs are placed GAP apart (the wordmark's i and l stems are 45 apart).
import sys
GAP = 36
G = {
 'a': (["M100 150A50 50 0 1 0 100 150.01", "M100 100V200"], [], 100),
 'd': (["M100 150A50 50 0 1 0 100 150.01", "M100 50V200"], [], 100),
 'o': (["M100 150A50 50 0 1 0 100 150.01"], [], 100),
 'p': (["M0 100V265", "M100 150A50 50 0 1 0 100 150.01"], [], 100),
 'e': (["M2 150H98A48 48 0 1 0 84 184"], [], 98),
 'g': (["M100 150A50 50 0 1 0 100 150.01", "M100 100V228A40 40 0 0 1 60 268H32"], [], 100),
 'i': (["M0 100V200"], [(0, 62)], 0),
 'í': (["M0 100V200"], [], 0),
 'n': (["M0 200V100", "M0 145A45 45 0 0 1 90 145V200"], [], 90),
 'r': (["M0 200V100", "M0 150A50 50 0 0 1 50 100H62"], [], 62),
 's': (["M74 112C60 96 8 96 8 125C8 152 78 142 78 172C78 205 20 206 4 186"], [], 78),
 't': (["M22 58V176A24 24 0 0 0 46 200H54", "M0 112H50"], [], 54),
 'v': (["M0 100L42 200L84 100"], [], 84),
 'y': (["M0 100L44 200", "M88 100L34 265"], [], 88),
 'k': (["M0 50V200", "M70 100L4 158", "M34 132L76 200"], [], 76),
 'Z': (["M0 50H100L0 200H100"], [], 100),
 'S': (["M96 72C80 46 6 44 6 90C6 130 100 118 100 160C100 210 18 214 0 182"], [], 100),
 'R': (["M0 200V50H48A38 38 0 0 1 48 126H0", "M48 126L96 200"], [], 96),
 'N': (["M0 200V50L104 200V50"], [], 104),
}
ACCENT = {'á': ('a', 50), 'í': ('í', 0)}  # acute over the glyph's centre x

def word(text, blue, red):
    x, out, red_out = 0.0, [], []
    for ch in text:
        base, cx = (ACCENT[ch] if ch in ACCENT else (ch, None))
        paths, dots, w = G[base]
        out += [f'<path transform="translate({x:.1f} 0)" d="{p}"/>' for p in paths]
        out += [f'<circle cx="{x + dx:.1f}" cy="{dy}" r="14" fill="{blue}" stroke="none"/>' for dx, dy in dots]
        if cx is not None:
            red_out.append(f'<path d="M{x + cx - 10:.1f} 62L{x + cx + 12:.1f} 40"/>')
        x += w + GAP
    width = x - GAP
    return width, out, red_out

def svg(text, blue='#1A4FA3', red='#E3242B', height=None):
    w, paths, reds = word(text, blue, red)
    vb = f'-20 20 {w + 40:.0f} 270'
    hattr = f' height="{height}"' if height else ''
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{vb}"{hattr}>'
            f'<g fill="none" stroke="{blue}" stroke-width="24" stroke-linecap="round" stroke-linejoin="round">{"".join(paths)}</g>'
            f'<g fill="none" stroke="{red}" stroke-width="22" stroke-linecap="round">{"".join(reds)}</g></svg>')

if __name__ == '__main__':
    LOGO = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="-10 20 310 195" height="72"><g fill="none" stroke="#1A4FA3" stroke-width="24" stroke-linecap="round" stroke-linejoin="round"><path d="M30 200V95A45 45 0 0 1 75 50H85"/><path d="M8 112H66"/><path d="M185.4 114.6A50 50 0 1 0 185.4 185.4"/><path d="M230 100V200"/><path d="M275 50V200"/></g><circle cx="230" cy="62" r="14" fill="#1A4FA3"/><path d="M128 38L150 60L172 38" fill="none" stroke="#E3242B" stroke-width="22" stroke-linecap="round" stroke-linejoin="round"/></svg>'
    h = 72 * 270 / 195
    rows = ''.join(f'<div style="display:flex;align-items:flex-start;gap:24px;margin:8px 0">{LOGO}{svg(t, height=h)}</div>' for t in ['Zastávky', 'Radar', 'Nastavení', 'Stops', 'Settings'])
    open(sys.argv[1], 'w').write(f'<!doctype html><meta charset=utf-8><body style="margin:16px;background:#F3F4F7">{rows}</body>')
