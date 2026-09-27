#!/usr/bin/env python3
"""Frames raw app screenshots into store images: headline + subline above a device frame.

usage: render.py <platform> <lang> <slide> <screenshot.png> <out.png>
platform: iphone (1284x2778) | ipad (2064x2752) | android (1080x1920)
Renders with headless Chrome; captions come from captions.json next to this file.
"""
import json, os, shutil, subprocess, sys, tempfile, html

HERE = os.path.dirname(os.path.abspath(__file__))
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

# canvas w, h, headline px, subline px, top padding, device width, device top, bezel, radius
LAYOUT = {
    "iphone":  (1284, 2778, 112, 54, 180, 1040, 680, 26, 124),
    "ipad":    (2064, 2752, 132, 64, 190, 1560, 740, 30, 90),
    "android": (1080, 1920,  88, 42, 110,  800, 500, 20, 84),
}

def render(platform, lang, slide, shot, out):
    w, h, h1, sub, pad, dw, dtop, bezel, radius = LAYOUT[platform]
    title, subtitle = json.load(open(os.path.join(HERE, "captions.json")))["text"][lang][slide]
    tmp = tempfile.mkdtemp()
    shutil.copy(shot, os.path.join(tmp, "shot.png"))
    inner = dw - 2 * bezel
    page = f"""<!doctype html><html lang="{lang}"><head><meta charset="utf-8"><style>
html,body{{margin:0;width:{w}px;height:{h}px;overflow:hidden}}
body{{background:linear-gradient(172deg,#F7B866 0%,#EC8A45 42%,#B9552B 100%);
  font-family:ui-rounded,"SF Pro Rounded",system-ui,sans-serif;color:#fff;position:relative}}
.glow{{position:absolute;left:50%;top:{int(h*0.55)}px;width:{int(w*1.1)}px;height:{int(w*1.1)}px;
  transform:translate(-50%,-50%);background:radial-gradient(circle,rgba(255,236,200,.55),rgba(255,236,200,0) 60%)}}
h1{{margin:0;padding:{pad}px {int(w*0.07)}px 0;text-align:center;font-size:{h1}px;line-height:1.08;font-weight:800;
  letter-spacing:-1px;text-wrap:balance;text-shadow:0 4px 18px rgba(120,40,10,.25)}}
p{{margin:{int(sub*0.6)}px {int(w*0.085)}px 0;text-align:center;font-size:{sub}px;line-height:1.25;font-weight:600;
  opacity:.93;text-wrap:balance}}
.dev{{position:absolute;left:50%;top:{dtop}px;transform:translateX(-50%);width:{dw}px;box-sizing:border-box;
  border:{bezel}px solid #1c1a1f;border-radius:{radius}px;box-shadow:0 50px 110px rgba(80,25,5,.45);
  overflow:hidden;background:#1c1a1f}}
.dev img{{display:block;width:{inner}px;border-radius:{radius-bezel}px}}
</style></head><body><div class="glow"></div>
<h1>{html.escape(title)}</h1><p>{html.escape(subtitle)}</p>
<div class="dev"><img src="shot.png"></div></body></html>"""
    open(os.path.join(tmp, "slide.html"), "w").write(page)
    subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars",
                    "--force-device-scale-factor=1", f"--window-size={w},{h}",
                    f"--screenshot={os.path.abspath(out)}", "file://" + os.path.join(tmp, "slide.html")],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    shutil.rmtree(tmp)

if __name__ == "__main__":
    render(*sys.argv[1:6])
