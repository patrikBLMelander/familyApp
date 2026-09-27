#!/usr/bin/env python3
"""Play feature graphic (1024x500) per language: icon, name and tagline beside three pets.

usage: feature.py <lang> <out.png>
"""
import json, os, shutil, subprocess, sys, tempfile, html

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
RES = os.path.join(REPO, "android/KidQuest/app/src/main")
ASSETS = {
    "icon.png": os.path.join(RES, "ic_launcher-playstore.png"),
    "dragon.png": os.path.join(RES, "res/drawable/dragon_stage5.png"),
    "cat.png": os.path.join(RES, "res/drawable/cat_stage5.png"),
    "unicorn.png": os.path.join(RES, "res/drawable/unicorn_stage5.png"),
}

def render(lang, out):
    tagline = json.load(open(os.path.join(HERE, "captions.json")))["text"][lang]["feature"]
    tmp = tempfile.mkdtemp()
    for name, src in ASSETS.items():
        shutil.copy(src, os.path.join(tmp, name))
    page = f"""<!doctype html><html lang="{lang}"><head><meta charset="utf-8"><style>
html,body{{margin:0;width:1024px;height:500px;overflow:hidden}}
body{{background:linear-gradient(120deg,#F7B866 0%,#EC8A45 50%,#B9552B 100%);
  font-family:ui-rounded,"SF Pro Rounded",system-ui,sans-serif;color:#fff;position:relative}}
.glow{{position:absolute;right:-60px;top:250px;width:760px;height:760px;transform:translateY(-50%);
  background:radial-gradient(circle,rgba(255,240,210,.75),rgba(255,240,210,0) 62%)}}
.text{{position:absolute;left:64px;top:0;bottom:0;width:470px;display:flex;flex-direction:column;justify-content:center}}
.icon{{width:112px;height:112px;border-radius:26px;box-shadow:0 10px 26px rgba(80,25,5,.35)}}
h1{{margin:26px 0 0;font-size:78px;line-height:1;font-weight:800;letter-spacing:-1px;
  text-shadow:0 3px 14px rgba(120,40,10,.25)}}
p{{margin:16px 0 0;font-size:34px;line-height:1.2;font-weight:700;text-wrap:balance;opacity:.95}}
.pet{{position:absolute;filter:drop-shadow(0 14px 18px rgba(80,25,5,.35))}}
.dragon{{width:400px;right:120px;top:115px}}
.cat{{right:430px;top:222px}}
.cat img{{height:245px;display:block;clip-path:inset(7% 0 0 0)}}
.unicorn{{height:255px;right:36px;top:205px}}
</style></head><body><div class="glow"></div>
<div class="text"><img class="icon" src="icon.png"><h1>KidQuest</h1><p>{html.escape(tagline)}</p></div>
<div class="pet cat"><img src="cat.png"></div><img class="pet unicorn" src="unicorn.png"><img class="pet dragon" src="dragon.png">
</body></html>"""
    open(os.path.join(tmp, "f.html"), "w").write(page)
    subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars",
                    "--force-device-scale-factor=1", "--window-size=1024,500",
                    f"--screenshot={os.path.abspath(out)}", "file://" + os.path.join(tmp, "f.html")],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    shutil.rmtree(tmp)

if __name__ == "__main__":
    render(sys.argv[1], sys.argv[2])
