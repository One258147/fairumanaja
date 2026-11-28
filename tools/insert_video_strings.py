#!/usr/bin/env python3
"""Insert 4 video-player strings into all 48 strings.xml files (after
viewer_format). Uncertain languages get a <!-- UNCERTAIN_XX --> comment
above the string, per project convention."""
import os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")

# (speed, cannot_play, play, pause)
T = {
    "values": ("调速", "无法播放该视频", "播放", "暂停"),
    "values-en": ("Speed", "Cannot play this video", "Play", "Pause"),
    "values-en-rUS": ("Speed", "Cannot play this video", "Play", "Pause"),
    "values-zh-rTW": ("調速", "無法播放此影片", "播放", "暫停"),
    "values-yue": ("調速", "播唔到呢條片", "播放", "暫停"),
    "values-lzh": ("调速", "不能播放此视频", "播放", "暂止"),
    "values-ar": ("سرعة التشغيل", "تعذّر تشغيل هذا الفيديو", "تشغيل", "إيقاف مؤقت"),
    "values-ar-rEG": ("سرعة التشغيل", "لا يمكن تشغيل هذا الفيديو", "تشغيل", "إيقاف مؤقت"),
    "values-fr": ("Vitesse de lecture", "Impossible de lire cette vidéo", "Lecture", "Pause"),
    "values-de": ("Geschwindigkeit", "Dieses Video kann nicht abgespielt werden", "Abspielen", "Pause"),
    "values-ja": ("再生速度", "この動画を再生できません", "再生", "一時停止"),
    "values-ko": ("재생 속도", "이 동영상을 재생할 수 없습니다", "재생", "일시 정지"),
    "values-ru": ("Скорость", "Не удаётся воспроизвести это видео", "Воспроизвести", "Пауза"),
    "values-es": ("Velocidad", "No se puede reproducir este vídeo", "Reproducir", "Pausa"),
    "values-pt": ("Velocidade", "Não é possível reproduzir este vídeo", "Reproduzir", "Pausar"),
    "values-it": ("Velocità", "Impossibile riprodurre questo video", "Riproduci", "Pausa"),
    "values-nl": ("Snelheid", "Deze video kan niet worden afgespeeld", "Afspelen", "Pauzeren"),
    "values-sv": ("Hastighet", "Det går inte att spela upp videon", "Spela upp", "Pausa"),
    "values-nb": ("Hastighet", "Kan ikke spille av denne videoen", "Spill av", "Pause"),
    "values-da": ("Hastighed", "Denne video kan ikke afspilles", "Afspil", "Pause"),
    "values-pl": ("Prędkość", "Nie można odtworzyć tego filmu", "Odtwórz", "Wstrzymaj"),
    "values-el": ("Ταχύτητα", "Δεν είναι δυνατή η αναπαραγωγή αυτού του βίντεο", "Αναπαραγωγή", "Παύση"),
    "values-tr": ("Hız", "Bu video oynatılamıyor", "Oynat", "Duraklat"),
    "values-hy": ("Արագություն", "Այս տեսանյութը հնարավոր չէ նվագարկել", "Նվագարկել", "Դադար"),
    "values-hu": ("Sebesség", "Ezt a videót nem lehet lejátszani", "Lejátszás", "Szünet"),
    "values-fa": ("سرعت پخش", "پخش این ویدیو ممکن نیست", "پخش", "توقف موقت"),
    "values-hi": ("गति", "यह वीडियो नहीं चलाया जा सकता", "चलाएँ", "रोकें"),
    "values-in": ("Kecepatan", "Video ini tidak dapat diputar", "Putar", "Jeda"),
    "values-ms": ("Kelajuan", "Video ini tidak dapat dimainkan", "Main", "Jeda"),
    "values-fil": ("Bilis", "Hindi ma-play ang video na ito", "I-play", "I-pause"),
    "values-th": ("ความเร็ว", "ไม่สามารถเล่นวิดีโอนี้ได้", "เล่น", "หยุดชั่วคราว"),
    "values-ug": ("تېزلىك", "بۇ سىننى ئوينىغىلى بولمايدۇ", "ئوينا", "ۋاقىتلىق توختا"),
    "values-bo": ("མགྱོགས་ཚད་", "བརྙན་འདི་འཁྲབ་མི་ཐུབ་", "འཁྲབ་", "བར་མཚམས་བཞག་"),
    "values-mn": ("Хурд", "Энэ видеог тоглуулах боломжгүй", "Тоглуулах", "Түр зогсоох"),
    "values-b+mn+Mong": ("ᠬᠤᠷᠳᠤ", "ᠡᠨᠡ ᠪᠢᠴᠢᠯᠡᠭ ᠲᠣᠭᠯᠠᠵᠤ ᠪᠣᠯᠤᠬᠤ ᠦᠭᠡᠢ", "ᠲᠣᠭᠯᠠᠬᠤ", "ᠲᠦᠷ ᠵᠣᠭᠰᠣᠬᠤ"),
    "values-mnc": ("ᡥᡡᡩᡠᠨ ᡥᠠᠰᡳᡥᠠ", "ᡝᡵᡝ ᡩᡝᡵᡳᠪᡠ ᡨᠠᠴᡳᡴᡡ ᠣᠵᠣᡵᠠᡴᡡ", "ᡨᠠᠴᡳᡴᡡ", "ᠨᠠᡴᠠᠮᠪᡳ"),
    "values-sjo": ("ᡥᡡᡩᡠᠨ ᡥᠠᠰᡞᡥᠠ", "ᡝᡵᡝ ᡩᡝᡵᡞᠪᡠ ᡨᠠᠴᡞᡴᡡ ᠣᠵᠣᡵᠠᡴᡡ", "ᡨᠠᠴᡞᡴᡡ", "ᠨᠠᡴᠠᠮᠪᡞ"),
    "values-chr": ("ᎠᎦᏔᎲᎢ", "ᎠᏴᏍᏗ ᎥᏝ ᏱᎠᏓᏅᏓ", "ᎠᏓᏅᏓ", "ᎠᏍᏓᏴᏗ"),
    "values-haw": ("Māmā", "ʻAʻole hiki ke hoʻokani i kēia wikiō", "Hoʻokani", "Hoʻomaha"),
    "values-mi": ("Tere", "Kāore e taea te purei i tēnei ataata", "Purei", "Whakatā"),
    "values-hmn": ("Ceev", "Tsis tuaj yeem tso video no", "Tso", "Ncua"),
    "values-b+kk+Arab": ("جىلدامدىق", "بۇل بەينەنى ويناتۋ مۇمكىن ەمەس", "ويناتۋ", "توقتاتۋ"),
    "values-b+kk+Cyrl": ("Жылдамдық", "Бұл бейнені ойнату мүмкін емес", "Ойнату", "Кідірту"),
    "values-sa": ("वेगः", "अस्य चलच्चित्रस्य प्रदर्शनं न शक्यम्", "चालय", "विरम"),
    "values-la": ("Velocitas", "Hoc video reproduci non potest", "Reproducere", "Pausa"),
    "values-eo": ("Rapido", "Ne eblas ludi ĉi tiun videon", "Ludi", "Paŭzi"),
    "values-za": ("Sokduh", "Mbwnj ndaej lwg dienzgaenq", "Lwg", "Doengq"),
    "values-ii": ("ꃅꇬ", "ꉈꀋꌠꃅꀋꃅ", "ꀋꇬ", "ꀋꄸ"),
}

# language dir -> UNCERTAIN tag (only for the uncertain ones; all 4 strings
# in that language get the comment)
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

NAMES = ["video_speed", "video_cannot_play", "video_play", "video_pause"]
ANCHOR = re.compile(r'^(\s*)<string name="viewer_format">.*</string>\s*$')

def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace("'", "\\'")

done, missing = [], []
for d in sorted(os.listdir(RES)):
    if not d.startswith("values"):
        continue
    p = os.path.join(RES, d, "strings.xml")
    if not os.path.isfile(p):
        continue
    if d not in T:
        missing.append(d + " (no translation table)")
        continue
    lines = open(p, encoding="utf-8").read().splitlines(keepends=True)
    idx = next((i for i, ln in enumerate(lines) if ANCHOR.match(ln)), None)
    if idx is None:
        missing.append(d + " (no viewer_format anchor)")
        continue
    if any(f'name="{n}"' in "".join(lines) for n in NAMES):
        missing.append(d + " (already has video strings)")
        continue
    indent = ANCHOR.match(lines[idx]).group(1)
    tag = UNCERTAIN.get(d)
    block = []
    for name, val in zip(NAMES, T[d]):
        if tag:
            block.append(f"{indent}<!-- {tag}：视频播放器新词尽力翻译，需母语者复核 -->\n")
        block.append(f'{indent}<string name="{name}">{esc(val)}</string>\n')
    lines[idx + 1:idx + 1] = block
    open(p, "w", encoding="utf-8").write("".join(lines))
    done.append(d)

print(f"inserted into {len(done)} dirs")
if missing:
    print("PROBLEMS:")
    for m in missing:
        print(" ", m)
