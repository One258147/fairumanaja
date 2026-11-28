#!/usr/bin/env python3
"""Insert 4 video-player option strings into all 48 strings.xml files (after
video_pause). Uncertain languages get a <!-- UNCERTAIN_XX --> comment above
the string, per project convention."""
import os, re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")

# (fill_screen, loop, landscape, duration)
T = {
    "values": ("强制铺满全屏", "循环播放", "横屏模式", "时长"),
    "values-en": ("Fill screen", "Loop", "Landscape", "Duration"),
    "values-en-rUS": ("Fill screen", "Loop", "Landscape", "Duration"),
    "values-zh-rTW": ("強制填滿全螢幕", "循環播放", "橫屏模式", "時長"),
    "values-yue": ("強制填滿成個屏", "循環播放", "橫屏模式", "時長"),
    "values-lzh": ("強制滿屏", "循環播", "橫屏", "時長"),
    "values-ar": ("ملء الشاشة", "تكرار التشغيل", "الوضع الأفقي", "المدة"),
    "values-ar-rEG": ("ملء الشاشة", "تكرار التشغيل", "الوضع الأفقي", "المدة"),
    "values-fr": ("Remplir l'écran", "Lecture en boucle", "Mode paysage", "Durée"),
    "values-de": ("Bildschirm ausfüllen", "Wiederholen", "Querformat", "Dauer"),
    "values-ja": ("画面いっぱいに表示", "ループ再生", "横向き表示", "再生時間"),
    "values-ko": ("화면 가득 채우기", "반복 재생", "가로 모드", "재생 시간"),
    "values-ru": ("На весь экран", "Повтор", "Альбомный режим", "Длительность"),
    "values-es": ("Llenar pantalla", "Repetir", "Modo horizontal", "Duración"),
    "values-pt": ("Preencher a tela", "Repetir", "Modo paisagem", "Duração"),
    "values-it": ("Riempi schermo", "Ripeti", "Orizzontale", "Durata"),
    "values-nl": ("Scherm vullen", "Herhalen", "Liggende stand", "Duur"),
    "values-sv": ("Fyll skärmen", "Upprepa", "Liggende läge", "Längd"),
    "values-nb": ("Fyll skjermen", "Gjenta", "Liggende modus", "Varighet"),
    "values-da": ("Fyld skærmen", "Gentag", "Liggende tilstand", "Varighed"),
    "values-pl": ("Wypełnij ekran", "Zapętl", "Tryb poziomy", "Czas trwania"),
    "values-el": ("Γέμισμα οθόνης", "Επανάληψη", "Οριζόντια λειτουργία", "Διάρκεια"),
    "values-tr": ("Ekranı doldur", "Döngü", "Yatay mod", "Süre"),
    "values-hy": ("Լրացնել էկրանը", "Կրկնել", "Հորիզոնական ռեժիմ", "Տևողություն"),
    "values-hu": ("Képernyő kitöltése", "Ismétlés", "Fekvő mód", "Időtartam"),
    "values-fa": ("پر کردن صفحه", "تکرار", "حالت افقی", "مدت زمان"),
    "values-hi": ("स्क्रीन भरें", "दोहराएँ", "लैंडस्केप मोड", "अवधि"),
    "values-in": ("Isi layar", "Ulangi", "Mode lanskap", "Durasi"),
    "values-ms": ("Isi skrin", "Ulang", "Mod landskap", "Tempoh"),
    "values-fil": ("Punuin ang screen", "I-loop", "Landscape mode", "Tagal"),
    "values-th": ("เติมเต็มหน้าจอ", "เล่นซ้ำ", "โหมดแนวนอน", "ระยะเวลา"),
    "values-ug": ("ئېكراننى تولدۇرۇش", "قايتا ئوينا", "توغرا ھالەت", "ۋاقىت ئۇزۇنلۇقى"),
    "values-bo": ("སྟོན་ཆས་ཁྱབ་ཚད་རྫོགས་པ་", "ཡང་བསྐྱར་འཁྲབ་པ་", "འགྲམ་ཕྱོགས་འཁྲབ་ཚུལ་", "དུས་ཡུན་"),
    "values-mn": ("Дэлгэц дүүргэх", "Давтах", "Хэвтээ горим", "Хугацаа"),
    "values-b+mn+Mong": ("ᠳᠡᠯᠭᠡᠴᠡ ᠳᠦᠭᠦᠷᠭᠡᠬᠦ", "ᠳᠠᠪᠲᠠᠬᠤ", "ᠬᠡᠪᠲᠡᠭᠡ ᠬᠡᠪ", "ᠬᠤᠭᠤᠴᠠᠭ᠎ᠠ"),
    "values-mnc": ("ᡩᡝᡵᡤᡳ ᠪᡝ ᡶᡳᠯᠯᠠᠮᡝ ᠠᠴᠠᠪᡠᠮᠪᡳ", "ᡩᠠᡥᡡᠨ ᡨᠠᠴᡳᡴᡡ", "ᡥᠠᡴᡧᠠᠨ ᠠᡵᠠᠮᠪᡳ", "ᡝᡵᡳᠨ ᡤᠣᠵᡳᠮᡝ"),
    "values-sjo": ("ᡩᡝᡵᡤᡳ ᠪᡝ ᡶᡳᠯᠯᠠᠮᡝ ᠠᠴᠠᠪᡠᠮᠪᡞ", "ᡩᠠᡥᡡᠨ ᡨᠠᠴᡞᡴᡡ", "ᡥᠠᡴᡧᠠᠨ ᠠᡵᠠᠮᠪᡞ", "ᡝᡵᡳᠨ ᡤᠣᠵᡞᠮᡝ"),
    "values-chr": ("ᎠᏓᏅᏙ ᎤᏬᏪᎳᏅᎢ", "ᎠᏓᏅᏓ ᏥᏄᎪᏫᏍᏗ", "ᎠᎦᏔᎲ ᎤᏬᏪᎳᏅᎢ", "ᎠᏴᏫ ᎤᏬᏪᎳᏅᎢ"),
    "values-haw": ("Hoʻopiha i ka pale", "Hoʻokani hou", "ʻAoʻao pae", "Ka lōʻihi"),
    "values-mi": ("Whakakī i te mata", "Purei anō", "Aratau whakapae", "Roa"),
    "values-hmn": ("Sau lub vijtsam", "Rov qab", "Hom kab", "Ntev"),
    "values-b+kk+Arab": ("ەكراندى تولتىرۋ", "قايتا ويناتۋ", "كولدەنەڭ رەجىم", "ۇزاقتىعى"),
    "values-b+kk+Cyrl": ("Экранды толтыру", "Қайта ойнату", "Көлденең режим", "Ұзақтығы"),
    "values-sa": ("पूर्णपटलम्", "पुनः चालय", "तिर्यक् विधा", "अवधिः"),
    "values-la": ("Implere velum", "Repete", "Modus transversus", "Duratio"),
    "values-eo": ("Plenigi ekranon", "Ripeti", "Pejzaĝa reĝimo", "Daŭro"),
    "values-za": ("Daemj biengj", "Lwg youq", "Hengzbingz", "Sizganq"),
    "values-ii": ("ꀋꆏꁮꀋꇬ", "ꀋꇬꁧꀋꇬ", "ꀋꆏꁮꀋꇬ", "ꀋꀋꇬ"),
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

NAMES = ["video_fill_screen", "video_loop", "video_landscape", "video_duration"]
ANCHOR = re.compile(r'^(\s*)<string name="video_pause">.*</string>\s*$')

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
        missing.append(d + " (no video_pause anchor)")
        continue
    if any(f'name="{n}"' in "".join(lines) for n in NAMES):
        missing.append(d + " (already has video option strings)")
        continue
    indent = ANCHOR.match(lines[idx]).group(1)
    tag = UNCERTAIN.get(d)
    block = []
    for name, val in zip(NAMES, T[d]):
        if tag:
            block.append(f"{indent}<!-- {tag}：视频播放器选项新词尽力翻译，需母语者复核 -->\n")
        block.append(f'{indent}<string name="{name}">{esc(val)}</string>\n')
    lines[idx + 1:idx + 1] = block
    open(p, "w", encoding="utf-8").write("".join(lines))
    done.append(d)

print(f"inserted into {len(done)} dirs")
if missing:
    print("PROBLEMS:")
    for m in missing:
        print(" ", m)
