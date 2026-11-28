#!/usr/bin/env python3
"""v1.9.8 媒体页：新增 media_images / media_videos / media_gifs /
media_today / media_yesterday 5 条词条（插进各语言 strings.xml，
viewer_format 锚点之后）。48 个语言目录全覆盖；不确定的语言在词条上方加
<!-- UNCERTAIN_XX --> 注释，按项目惯例。
注意：edit_text 在 48 种语言里本来就存在（不是新词条），所以上一轮的
基线是 311 → 314，不是 315；本轮 314 → 319。"""
import os, re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")

# (media_images, media_videos, media_gifs, media_today, media_yesterday)
T = {
    "values": ("图像", "视频", "动图", "今天", "昨天"),
    "values-en": ("Images", "Videos", "GIFs", "Today", "Yesterday"),
    "values-en-rUS": ("Images", "Videos", "GIFs", "Today", "Yesterday"),
    "values-zh-rTW": ("圖像", "影片", "動圖", "今天", "昨天"),
    "values-yue": ("圖像", "影片", "動圖", "今日", "尋日"),
    "values-lzh": ("圖像", "視頻", "動圖", "今日", "昨日"),
    "values-ar": ("الصور", "الفيديوهات", "صور متحركة", "اليوم", "أمس"),
    "values-ar-rEG": ("الصور", "الفيديوهات", "صور متحركة", "النهاردة", "امبارح"),
    "values-fr": ("Images", "Vidéos", "GIF", "Aujourd'hui", "Hier"),
    "values-de": ("Bilder", "Videos", "GIFs", "Heute", "Gestern"),
    "values-ja": ("画像", "動画", "GIF", "今日", "昨日"),
    "values-ko": ("이미지", "동영상", "GIF", "오늘", "어제"),
    "values-ru": ("Изображения", "Видео", "GIF", "Сегодня", "Вчера"),
    "values-es": ("Imágenes", "Vídeos", "GIF", "Hoy", "Ayer"),
    "values-pt": ("Imagens", "Vídeos", "GIF", "Hoje", "Ontem"),
    "values-it": ("Immagini", "Video", "GIF", "Oggi", "Ieri"),
    "values-nl": ("Afbeeldingen", "Video's", "GIF's", "Vandaag", "Gisteren"),
    "values-sv": ("Bilder", "Videor", "GIF-bilder", "I dag", "I går"),
    "values-nb": ("Bilder", "Videoer", "GIF-er", "I dag", "I går"),
    "values-da": ("Billeder", "Videoer", "GIF'er", "I dag", "I går"),
    "values-pl": ("Obrazy", "Filmy", "GIF-y", "Dzisiaj", "Wczoraj"),
    "values-el": ("Εικόνες", "Βίντεο", "GIF", "Σήμερα", "Χθες"),
    "values-tr": ("Görseller", "Videolar", "GIF'ler", "Bugün", "Dün"),
    "values-hy": ("Պատկերներ", "Տեսանյութեր", "GIF-եր", "Այսօր", "Երեկ"),
    "values-hu": ("Képek", "Videók", "GIF-ek", "Ma", "Tegnap"),
    "values-fa": ("تصاویر", "ویدیوها", "گیف‌ها", "امروز", "دیروز"),
    "values-hi": ("चित्र", "वीडियो", "GIF", "आज", "कल"),
    "values-in": ("Gambar", "Video", "GIF", "Hari ini", "Kemarin"),
    "values-ms": ("Imej", "Video", "GIF", "Hari ini", "Semalam"),
    "values-fil": ("Mga larawan", "Mga video", "GIF", "Ngayon", "Kahapon"),
    "values-th": ("รูปภาพ", "วิดีโอ", "GIF", "วันนี้", "เมื่อวาน"),
    "values-ug": ("سۈرەتلەر", "سىنلار", "ھەرىكەتچان سۈرەت", "بۈگۈن", "تۈنۈگۈن"),
    "values-bo": ("པར་", "བརྙན་", "འགུལ་པར་", "དེ་རིང་", "ཁ་སང་"),
    "values-mn": ("Зураг", "Видео", "GIF", "Өнөөдөр", "Өчигдөр"),
    "values-b+mn+Mong": ("ᠵᠢᠷᠤᠭ", "ᠪᠢᠴᠢᠯᠡᠭ", "GIF", "ᠡᠨᠡ ᠡᠳᠦᠷ", "ᠦᠴᠦᠭᠡᠳᠦᠷ"),
    "values-mnc": ("ᠵᡠᠷᡠ", "ᡩᡝᡵᡳᠪᡠ", "GIF", "ᡝᡵᡝ ᠠᠨᡳᠶᠠ", "ᠰᡳᡴᠰᡝ ᠠᠨᡳᠶᠠ"),
    "values-sjo": ("ᠵᡠᠷᡠ", "ᡩᡝᡵᡞᠪᡠ", "GIF", "ᡝᡵᡝ ᠠᠨᡞᠶᠠ", "ᠰᡞᡴᠰᡝ ᠠᠨᡞᠶᠠ"),
    "values-chr": ("ᎠᏓᏅᏓ", "ᏗᎦᏘᏯ", "GIF", "ᎪᎯ ᎢᎦ", "ᏒᏅᏔᏂ"),
    "values-haw": ("Nā kiʻi", "Nā wikiō", "GIF", "I kēia lā", "Inehinei"),
    "values-mi": ("Ngā whakaahua", "Ngā ataata", "GIF", "I tēnei rā", "Inanahi"),
    "values-hmn": ("Cov duab", "Cov video", "GIF", "Hnub no", "Nag"),
    "values-b+kk+Arab": ("سۋرەتتەر", "بەينەلەر", "GIF", "بۇگٸن", "كەشە"),
    "values-b+kk+Cyrl": ("Суреттер", "Бейнелер", "GIF", "Бүгін", "Кеше"),
    "values-sa": ("चित्राणि", "चलच्चित्राणि", "GIF", "अद्य", "श्वः"),
    "values-la": ("Imagines", "Video", "GIF", "Hodie", "Heri"),
    "values-eo": ("Bildoj", "Videoj", "GIF-oj", "Hodiaŭ", "Hieraŭ"),
    "values-za": ("Ciz", "Dienzgaenq", "GIF", "Ngoenzneix", "Ngoenzmoq"),
    "values-ii": ("ꀋꅉ", "ꀋꃅ", "GIF", "ꋍꆏ", "ꀉꎭ"),
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

NAMES = ["media_images", "media_videos", "media_gifs", "media_today", "media_yesterday"]
ANCHOR = re.compile(r'^(\s*)<string name="viewer_format">.*</string>\s*$')

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
    if any(f'name="{n}"' in "".join(lines) for n in NAMES):
        problems.append(d + " (already has media strings)")
        continue
    idx = next((i for i, ln in enumerate(lines) if ANCHOR.match(ln)), None)
    if idx is None:
        problems.append(d + " (no viewer_format anchor)")
        continue
    indent = ANCHOR.match(lines[idx]).group(1)
    tag = UNCERTAIN.get(d)
    block = []
    for name, val in zip(NAMES, T[d]):
        if tag:
            block.append(f"{indent}<!-- {tag}：媒体页新词尽力翻译，需母语者复核 -->\n")
        block.append(f'{indent}<string name="{name}">{esc(val)}</string>\n')
    lines[idx + 1:idx + 1] = block
    open(p, "w", encoding="utf-8").write("".join(lines))
    done.append(d)

print(f"inserted into {len(done)} dirs")
if problems:
    print("PROBLEMS:")
    for m in problems:
        print(" ", m)
