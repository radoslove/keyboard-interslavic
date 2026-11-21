"""Inline brand/src/decor.svg into the store graphic and render it (Windows, Chrome headless).

    python -X utf8 brand/build_decor.py <out.svg> <out.png>

decor.svg is the ONE source of the decor layer (Glagolitic, runes, Morse: shape, size, colour, opacity).
feature_graphic.svg keeps only the layout; its own decor elements are replaced by the decor groups,
placed at the positions below. Text is rendered without subpixel (LCD) antialiasing.
Refuses to overwrite existing outputs.
"""
import os, re, subprocess, sys, tempfile
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
CHROME = r'C:/Program Files/Google/Chrome/Application/chrome.exe'
# where each decor group sits in the store graphic (x, y of the group origin inside the 1024x500 band)
PLACE = {'decor-glag': (412, 132), 'decor-runy': (414, 336), 'decor-morse': (414, 376.5)}

out_svg, out_png = sys.argv[1], sys.argv[2]
for p in (out_svg, out_png):
    if os.path.exists(p):
        sys.exit(f'{p} exists - pick a new version name')

decor = open(os.path.join(HERE, 'src', 'decor.svg'), encoding='utf-8').read()
fg = open(os.path.join(HERE, 'src', 'feature_graphic.svg'), encoding='utf-8').read()

style = re.search(r'<g id="decor"([^>]*)>', decor).group(1)
groups = []
for gid, (x, y) in PLACE.items():
    m = re.search(rf'<g id="{gid}" transform="[^"]*">(.*?)</g>', decor, re.S)
    groups.append(f'    <g id="{gid}" transform="translate({x} {y})">{m.group(1)}</g>')
block = f'    <!-- decor layer: inlined from brand/src/decor.svg -->\n    <g{style}>\n' + '\n'.join(groups) + '\n    </g>\n'

# drop a decor block inlined by an earlier run, then the graphic's own decor (Segoe UI Historic texts, Morse dots)
fg = re.sub(r'\s*<!-- decor layer: inlined from brand/src/decor.svg -->\s*<g[^>]*>.*?</g>\s*</g>\n', '\n', fg, flags=re.S)
# and drop the graphic's own decor: Segoe UI Historic texts and the Morse dots
fg2 = re.sub(r'\s*<text[^>]*Segoe UI Historic[^>]*>.*?</text>', '', fg)
fg2 = re.sub(r'\s*<rect x="\d+(\.\d+)?" y="376\.5"[^>]*/>', '', fg2)
assert 'Segoe UI Historic' not in fg2 and 'y="376.5"' not in fg2, 'old decor still in the graphic'
fg2 = fg2.replace('  </g>\n</svg>', block + '  </g>\n</svg>')
assert 'id="decor-morse"' in fg2
open(out_svg, 'w', encoding='utf-8', newline='\n').write(fg2)

shot = os.path.join(tempfile.mkdtemp(), 'square.png')
url = 'file:///' + os.path.abspath(out_svg).replace('\\', '/')
subprocess.run([CHROME, '--headless=new', '--disable-gpu', '--hide-scrollbars', '--disable-lcd-text',
                '--force-device-scale-factor=1', '--window-size=1024,1024', f'--screenshot={shot}', url],
               capture_output=True)
im = Image.open(shot).convert('RGB')
assert im.size == (1024, 1024), f'chrome gave {im.size}'
im.crop((0, 262, 1024, 762)).save(out_png)
print('ok', out_svg, out_png)
