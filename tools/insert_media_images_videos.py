#!/usr/bin/env python3
"""v1.9.8 抽屉合并：新增 media_images_videos 一条词条（插进各语言
strings.xml，media_yesterday 之后）。48 个语言目录全覆盖；不确定的语言在
词条上方加 <!-- UNCERTAIN_XX --> 注释，按项目惯例。
译文由已验收的 media_images / media_videos 译文用各语言的“和”拼成；
media_images、media_videos 旧词条留着（分类标签还在用）。
基线 319 -> 320。"""
import os, re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")

T = {
    "values": "图像与视频",
    "values-en": "Images and Videos",
    "values-en-rUS": "Images and Videos",
    "values-zh-rTW": "圖像與影片",
    "values-yue": "圖像同影片",
    "values-lzh": "圖像與視頻",
    "values-ar": "الصور والفيديوهات",
    "values-ar-rEG": "الصور والفيديوهات",
    "values-fr": "Images et vidéos",
    "values-de": "Bilder und Videos",
    "values-ja": "画像と動画",
    "values-ko": "이미지와 동영상",
    "values-ru": "Изображения и видео",
    "values-es": "Imágenes y vídeos",
    "values-pt": "Imagens e vídeos",
    "values-it": "Immagini e video",
    "values-nl": "Afbeeldingen en video's",
    "values-sv": "Bilder och videor",
    "values-nb": "Bilder og videoer",
    "values-da": "Billeder og videoer",
    "values-pl": "Obrazy i filmy",
    "values-el": "Εικόνες και βίντεο",
    "values-tr": "Görseller ve videolar",
    "values-hy": "Պատկերներ և տեսանյութեր",
    "values-hu": "Képek és videók",
    "values-fa": "تصاویر و ویدیوها",
    "values-hi": "चित्र और वीडियो",
    "values-in": "Gambar dan Video",
    "values-ms": "Imej dan Video",
    "values-fil": "Mga larawan at mga video",
    "values-th": "รูปภาพและวิดีโอ",
    "values-ug": "سۈرەتلەر ۋە سىنلار",
    "values-bo": "པར་དང་བརྙན་",
    "values-mn": "Зураг болон видео",
    "values-b+mn+Mong": "ᠵᠢᠷᠤᠭ ᠪᠣᠯᠤᠨ ᠪᠢᠴᠢᠯᠡᠭ",
    "values-mnc": "ᠵᡠᠷᡠ ᡝᠮᡤᡳ ᡩᡝᡵᡳᠪᡠ",
    "values-sjo": "ᠵᡠᠷᡠ ᡝᠮᡤᡞ ᡩᡝᡵᡞᠪᡠ",
    "values-chr": "ᎠᏓᏅᏓ ᎠᎴ ᏗᎦᏘᏯ",
    "values-haw": "Nā kiʻi a me nā wikiō",
    "values-mi": "Ngā whakaahua me ngā ataata",
    "values-hmn": "Cov duab thiab cov video",
    "values-b+kk+Arab": "سۋرەتتەر جەنە بەينەلەر",
    "values-b+kk+Cyrl": "Суреттер және бейнелер",
    "values-sa": "चित्राणि च चलच्चित्राणि",
    "values-la": "Imagines et Video",
    "values-eo": "Bildoj kaj videoj",
    "values-za": "Ciz caeuq Dienzgaenq",
    "values-ii": "ꀋꅉꌠꀋꃅ",
}

UNCERTAIN = {
    "values-mnc": "UNCERTAIN_MNC",
    "values-sjo": "UNCERTAIN_SJO",
    "values-b+mn+Mong": "UNCERTAIN_MONG",
    "values-chr": "UNCERTAIN_CHR",
    "values-haw": "UNCERTAIN_HAW",
    "values-mi": "UNCERTAIN_MI",
    "values-hmn": "UNCERTAIN_HMN",
    "values-b+kk+Arab": "UNCERTAIN_KK",
    "values-b+kk+Cyrl": "UNCERTAIN_KK",
    "values-sa": "UNCERTAIN_SA",
    "values-bo": "UNCERTAIN_BO",
    "values-za": "UNCERTAIN_ZA",
    "values-ii": "UNCERTAIN_II",
    "values-lzh": "UNCERTAIN_LZH",
}

NAME = "media_images_videos"
ANCHOR = re.compile(r'^(\s*)<string name="media_yesterday">.*</string>\s*$')

def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace("'", "\\'")

done, problems = [], []
for d in sorted(os.listdir(RES)):
    if not d.startswith("values"):
        continue
    if d not in T:
        problems.append(d + " (no translation table)")
        continue
    p = os.path.join(RES, d, "strings.xml")
    if not os.path.isfile(p):
        problems.append(d + " (no strings.xml)")
        continue
    lines = open(p, encoding="utf-8").read().splitlines(keepends=True)
    if any(f'name="{NAME}"' in ln for ln in lines):
        problems.append(d + " (already has it)")
        continue
    idx = next((i for i, ln in enumerate(lines) if ANCHOR.match(ln)), None)
    if idx is None:
        problems.append(d + " (no media_yesterday anchor)")
        continue
    indent = ANCHOR.match(lines[idx]).group(1)
    tag = UNCERTAIN.get(d)
    block = []
    if tag:
        block.append(f"{indent}<!-- {tag}：抽屉合并新词尽力翻译，需母语者复核 -->\n")
    block.append(f'{indent}<string name="{NAME}">{esc(T[d])}</string>\n')
    lines[idx + 1:idx + 1] = block
    open(p, "w", encoding="utf-8").write("".join(lines))
    done.append(d)

print(f"inserted into {len(done)} dirs")
if problems:
    print("PROBLEMS:")
    for m in problems:
        print(" ", m)
