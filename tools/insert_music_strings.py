#!/usr/bin/env python3
"""Insert 2 music-player strings into all 48 strings.xml files (after
video_cannot_play). Uncertain languages get a <!-- UNCERTAIN_XX --> comment
above the string, per project convention.
play_music is new (grid button #14); music_cannot_play is new because the
video one literally says 视频 and cannot be reused for audio."""
import os, re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")

# (play_music, music_cannot_play)
T = {
    "values": ("播放音乐", "无法播放该音频"),
    "values-en": ("Play music", "Cannot play this audio"),
    "values-en-rUS": ("Play music", "Cannot play this audio"),
    "values-zh-rTW": ("播放音樂", "無法播放此音訊"),
    "values-yue": ("播放音樂", "播唔到呢段音頻"),
    "values-lzh": ("播放音樂", "此音頻不能播放"),
    "values-ar": ("تشغيل الموسيقى", "تعذّر تشغيل هذا الصوت"),
    "values-ar-rEG": ("تشغيل الموسيقى", "لا يمكن تشغيل هذا الصوت"),
    "values-fr": ("Lire la musique", "Impossible de lire cet audio"),
    "values-de": ("Musik abspielen", "Diese Audiodatei kann nicht abgespielt werden"),
    "values-ja": ("音楽を再生", "この音声を再生できません"),
    "values-ko": ("음악 재생", "이 오디오를 재생할 수 없습니다"),
    "values-ru": ("Воспроизвести музыку", "Не удаётся воспроизвести это аудио"),
    "values-es": ("Reproducir música", "No se puede reproducir este audio"),
    "values-pt": ("Reproduzir música", "Não é possível reproduzir este áudio"),
    "values-it": ("Riproduci musica", "Impossibile riprodurre questo audio"),
    "values-nl": ("Muziek afspelen", "Deze audio kan niet worden afgespeeld"),
    "values-sv": ("Spela musik", "Det går inte att spela upp ljudet"),
    "values-nb": ("Spill musikk", "Kan ikke spille av denne lyden"),
    "values-da": ("Afspil musik", "Denne lyd kan ikke afspilles"),
    "values-pl": ("Odtwórz muzykę", "Nie można odtworzyć tego dźwięku"),
    "values-el": ("Αναπαραγωγή μουσικής", "Δεν είναι δυνατή η αναπαραγωγή αυτού του ήχου"),
    "values-tr": ("Müzik çal", "Bu ses çalınamıyor"),
    "values-hy": ("Երաժշտություն նվագարկել", "Այս ձայնը հնարավոր չէ նվագարկել"),
    "values-hu": ("Zene lejátszása", "Ezt a hangot nem lehet lejátszani"),
    "values-fa": ("پخش موسیقی", "پخش این صدا ممکن نیست"),
    "values-hi": ("संगीत चलाएँ", "यह ऑडियो नहीं चलाया जा सकता"),
    "values-in": ("Putar musik", "Audio ini tidak dapat diputar"),
    "values-ms": ("Main muzik", "Audio ini tidak dapat dimainkan"),
    "values-fil": ("I-play ang musika", "Hindi ma-play ang audio na ito"),
    "values-th": ("เล่นเพลง", "ไม่สามารถเล่นเสียงนี้ได้"),
    "values-ug": ("مۇزىكا ئوينا", "بۇ ئاۋازنى ئوينىغىلى بولمايدۇ"),
    "values-bo": ("རོལ་དབྱངས་འཁྲབ་", "སྒྲ་འདི་འཁྲབ་མི་ཐུབ་"),
    "values-mn": ("Хөгжим тоглуулах", "Энэ аудиог тоглуулах боломжгүй"),
    "values-b+mn+Mong": ("ᠬᠥᠭᠵᠢᠮ ᠲᠣᠭᠯᠠᠬᠤ", "ᠡᠨᠡ ᠳᠠᠭᠤ ᠲᠣᠭᠯᠠᠵᠤ ᠪᠣᠯᠤᠬᠤ ᠦᠭᠡᠢ"),
    "values-mnc": ("ᡝᡤᡝᠨ ᡨᠠᠴᡳᡴᡡ", "ᡝᡵᡝ ᠠᡵᠠ ᡨᠠᠴᡳᡴᡡ ᠣᠵᠣᡵᠠᡴᡡ"),
    "values-sjo": ("ᡝᡤᡝᠨ ᡨᠠᠴᡞᡴᡡ", "ᡝᡵᡝ ᠠᡵᠠ ᡨᠠᠴᡞᡴᡡ ᠣᠵᠣᡵᠠᡴᡡ"),
    "values-chr": ("ᎠᏓᏅᏓ ᏗᏓᏅᏓ", "ᎠᏴᏍᏗ ᎥᏝ ᏱᎠᏓᏅᏓ"),
    "values-haw": ("Hoʻokani mele", "ʻAʻole hiki ke hoʻokani i kēia leo"),
    "values-mi": ("Purei waiata", "Kāore e taea te purei i tēnei oro"),
    "values-hmn": ("Tso suab paj nruag", "Tsis tuaj yeem tso lub suab no"),
    "values-b+kk+Arab": ("مۋزىكا ويناتۋ", "بۇل اۋديونى ويناتۋ مۇمكىن ەمەس"),
    "values-b+kk+Cyrl": ("Музыка ойнату", "Бұл аудионы ойнату мүмкін емес"),
    "values-sa": ("सङ्गीतं चालय", "अस्य ध्वनेः प्रदर्शनं न शक्यम्"),
    "values-la": ("Musicam reproducere", "Hic sonus reproduci non potest"),
    "values-eo": ("Ludi muzikon", "Ne eblas ludi ĉi tiun sonon"),
    "values-za": ("Lwg yinyoz", "Mbwnj ndaej lwg yinyoz"),
    "values-ii": ("ꀋꄣ", "ꉈꀋꌠꀋꃅꀋꃅ"),
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

NAMES = ["play_music", "music_cannot_play"]
ANCHOR = re.compile(r'^(\s*)<string name="video_cannot_play">.*</string>\s*$')

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
    if any(f'name="{n}"' in "".join(lines) for n in NAMES):
        problems.append(d + " (already has music strings)")
        continue
    idx = next((i for i, ln in enumerate(lines) if ANCHOR.match(ln)), None)
    if idx is None:
        problems.append(d + " (no video_cannot_play anchor)")
        continue
    indent = ANCHOR.match(lines[idx]).group(1)
    tag = UNCERTAIN.get(d)
    block = []
    for name, val in zip(NAMES, T[d]):
        if tag:
            block.append(f"{indent}<!-- {tag}：音乐播放器新词尽力翻译，需母语者复核 -->\n")
        block.append(f'{indent}<string name="{name}">{esc(val)}</string>\n')
    lines[idx + 1:idx + 1] = block
    open(p, "w", encoding="utf-8").write("".join(lines))
    done.append(d)

print(f"inserted into {len(done)} dirs")
if problems:
    print("PROBLEMS:")
    for m in problems:
        print(" ", m)
