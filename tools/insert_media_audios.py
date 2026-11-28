#!/usr/bin/env python3
"""v1.9.8 媒体页：新增 media_audios 词条（音频分类，排在视频后面；
分类顺序：图片 / 视频 / 音频 / 动图）。插进各语言 strings.xml，
media_videos 锚点之后。48 个语言目录全覆盖；不确定的语言在词条上方加
<!-- UNCERTAIN_XX --> 注释，按项目惯例。
基线：322 -> 323。"""
import os, re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")

T = {
    "values": "音频",
    "values-en": "Audio",
    "values-en-rUS": "Audio",
    "values-zh-rTW": "音訊",
    "values-yue": "音頻",
    "values-lzh": "音頻",
    "values-ar": "الصوتيات",
    "values-ar-rEG": "الصوتيات",
    "values-fr": "Audio",
    "values-de": "Audio",
    "values-ja": "音声",
    "values-ko": "오디오",
    "values-ru": "Аудио",
    "values-es": "Audio",
    "values-pt": "Áudio",
    "values-it": "Audio",
    "values-nl": "Audio",
    "values-sv": "Ljud",
    "values-nb": "Lyd",
    "values-da": "Lyd",
    "values-pl": "Audio",
    "values-el": "Ήχος",
    "values-tr": "Ses",
    "values-hy": "Ձայն",
    "values-hu": "Hang",
    "values-fa": "صدا",
    "values-hi": "ऑडियो",
    "values-in": "Audio",
    "values-ms": "Audio",
    "values-fil": "Audio",
    "values-th": "เสียง",
    "values-ug": "ئاۋاز",
    "values-bo": "སྒྲ་",
    "values-mn": "Аудио",
    "values-b+mn+Mong": "ᠳᠠᠭᠤ",
    "values-mnc": "ᡝᡤᡝᠨ",
    "values-sjo": "ᡝᡤᡝᠨ",
    "values-chr": "ᎠᏓᏅᏓ",
    "values-haw": "Nā leo",
    "values-mi": "Ngā oro",
    "values-hmn": "Suab",
    "values-b+kk+Arab": "اۋديو",
    "values-b+kk+Cyrl": "Аудио",
    "values-sa": "ध्वनिः",
    "values-la": "Sonus",
    "values-eo": "Aŭdio",
    "values-za": "Yinyoz",
    "values-ii": "ꉈꀋ",
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

NAME = "media_audios"
ANCHOR = re.compile(r'^(\s*)<string name="media_videos">.*</string>\s*$')

def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace("'", "\\'")

done, problems = [], []
for d in sorted(os.listdir(RES)):
    if not d.startswith("values"):
        continue
    if d == "values-night":
        continue
    if d not in T:
        problems.append(d + " (no translation table)")
        continue
    p = os.path.join(RES, d, "strings.xml")
    if not os.path.isfile(p):
        problems.append(d + " (no strings.xml)")
        continue
    lines = open(p, encoding="utf-8").read().splitlines(keepends=True)
    if any(f'name="{NAME}"' in "".join(lines) for _ in [0]):
        problems.append(d + " (already has media_audios)")
        continue
    idx = next((i for i, ln in enumerate(lines) if ANCHOR.match(ln)), None)
    if idx is None:
        problems.append(d + " (no media_videos anchor)")
        continue
    indent = ANCHOR.match(lines[idx]).group(1)
    tag = UNCERTAIN.get(d)
    block = []
    if tag:
        block.append(f"{indent}<!-- {tag}：媒体页音频分类新词尽力翻译，需母语者复核 -->\n")
    block.append(f'{indent}<string name="{NAME}">{esc(T[d])}</string>\n')
    lines[idx + 1:idx + 1] = block
    open(p, "w", encoding="utf-8").write("".join(lines))
    done.append(d)

print(f"inserted into {len(done)} dirs")
if problems:
    print("PROBLEMS:")
    for m in problems:
        print(" ", m)
