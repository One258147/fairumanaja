#!/usr/bin/env python3
"""v1.9.8: 1) pin_folder 改名“顶置”/“Pin to top”（只改值，不改 key）；
2) 新增 play_video / view_image / browse_archive 三条词条（插在 strings.xml，
viewer_format 锚点之后）。48 个语言目录全覆盖；不确定的语言在词条上方加
<!-- UNCERTAIN_XX --> 注释，按项目惯例。
pin_folder 本来就已经是“置顶”意思的 5 种语言（ug/hmn/sa/za/ii）保持原值不变。
"""
import os, re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")

# dir -> pin_folder 新值；None 表示保持原值
PIN = {
    "values": "顶置",
    "values-en": "Pin to top",
    "values-en-rUS": "Pin to top",
    "values-zh-rTW": "置頂",
    "values-yue": "置頂",
    "values-lzh": "置頂",
    "values-ar": "تثبيت في الأعلى",
    "values-ar-rEG": "تثبيت في الأعلى",
    "values-fr": "Épingler en haut",
    "values-de": "Oben anpinnen",
    "values-ja": "上部にピン留め",
    "values-ko": "상단에 고정",
    "values-ru": "Закрепить сверху",
    "values-es": "Fijar arriba",
    "values-pt": "Fixar no topo",
    "values-it": "Fissa in alto",
    "values-nl": "Bovenaan vastzetten",
    "values-sv": "Fäst överst",
    "values-nb": "Fest øverst",
    "values-da": "Fastgør øverst",
    "values-pl": "Przypnij na górę",
    "values-el": "Καρφίτσωμα στην κορυφή",
    "values-tr": "Üste sabitle",
    "values-hy": "Ամրացնել վերևում",
    "values-hu": "Rögzítés felülre",
    "values-fa": "سنجاق کردن در بالا",
    "values-hi": "ऊपर पिन करें",
    "values-in": "Sematkan di atas",
    "values-ms": "Sematkan di atas",
    "values-fil": "I-pin sa itaas",
    "values-th": "ปักหมุดไว้ด้านบน",
    "values-ug": None,
    "values-bo": "ཡིག་སྒམ་སྟེང་དུ་གཏད་",
    "values-mn": "Дээд талд бэхлэх",
    "values-b+mn+Mong": "ᠳᠡᠭᠡᠳᠦ ᠪᠡᠬᠡᠯᠡᠬᠦ",
    "values-mnc": "ᡩᡝᡵᡤᡳ ᡶᠠᠯᡤᠠ ᡥᡝᠮᡠᡥᡝ",
    "values-sjo": "ᡩᡝᡵᡤᡳ ᡫᠠᠯᡤᠠ ᡥᡝᠮᡠᡥᡝ",
    "values-chr": "ᎦᎸᎳᏗ ᎠᏓᏍᏓᏴᏗ",
    "values-haw": "Hoʻopaʻa ma luna",
    "values-mi": "Titi ki runga",
    "values-hmn": None,
    "values-b+kk+Arab": "جوعارىعا بەكىتۋ",
    "values-b+kk+Cyrl": "Жоғарыға бекіту",
    "values-sa": None,
    "values-la": "In summo affige",
    "values-eo": "Alpingli supre",
    "values-za": None,
    "values-ii": None,
}

# (play_video, view_image, browse_archive)
T = {
    "values": ("播放视频", "查看图片", "浏览压缩包"),
    "values-en": ("Play video", "View image", "Browse archive"),
    "values-en-rUS": ("Play video", "View image", "Browse archive"),
    "values-zh-rTW": ("播放影片", "查看圖片", "瀏覽壓縮檔"),
    "values-yue": ("播放影片", "查看圖片", "瀏覽壓縮包"),
    "values-lzh": ("播視頻", "觀圖", "覽壓縮包"),
    "values-ar": ("تشغيل الفيديو", "عرض الصورة", "تصفح الأرشيف"),
    "values-ar-rEG": ("تشغيل الفيديو", "عرض الصورة", "تصفح الملف المضغوط"),
    "values-fr": ("Lire la vidéo", "Voir l'image", "Parcourir l'archive"),
    "values-de": ("Video abspielen", "Bild ansehen", "Archiv durchsuchen"),
    "values-ja": ("動画を再生", "画像を見る", "圧縮ファイルを閲覧"),
    "values-ko": ("동영상 재생", "이미지 보기", "압축 파일 보기"),
    "values-ru": ("Воспроизвести видео", "Просмотреть изображение", "Просмотреть архив"),
    "values-es": ("Reproducir vídeo", "Ver imagen", "Explorar archivo comprimido"),
    "values-pt": ("Reproduzir vídeo", "Ver imagem", "Explorar arquivo compactado"),
    "values-it": ("Riproduci video", "Visualizza immagine", "Sfoglia archivio"),
    "values-nl": ("Video afspelen", "Afbeelding bekijken", "Archief bekijken"),
    "values-sv": ("Spela upp video", "Visa bild", "Bläddra i arkiv"),
    "values-nb": ("Spill av video", "Vis bilde", "Bla gjennom arkiv"),
    "values-da": ("Afspil video", "Vis billede", "Gennemse arkiv"),
    "values-pl": ("Odtwórz wideo", "Wyświetl obraz", "Przeglądaj archiwum"),
    "values-el": ("Αναπαραγωγή βίντεο", "Προβολή εικόνας", "Περιήγηση στο αρχείο"),
    "values-tr": ("Videoyu oynat", "Resmi görüntüle", "Arşivi gözden geçir"),
    "values-hy": ("Տեսանյութ նվագարկել", "Նկարը դիտել", "Արխիվը դիտել"),
    "values-hu": ("Videó lejátszása", "Kép megtekintése", "Archívum böngészése"),
    "values-fa": ("پخش ویدیو", "مشاهده تصویر", "مرور بایگانی"),
    "values-hi": ("वीडियो चलाएँ", "चित्र देखें", "संग्रह देखें"),
    "values-in": ("Putar video", "Lihat gambar", "Jelajahi arsip"),
    "values-ms": ("Main video", "Lihat imej", "Layari arkib"),
    "values-fil": ("I-play ang video", "Tingnan ang larawan", "I-browse ang archive"),
    "values-th": ("เล่นวิดีโอ", "ดูรูปภาพ", "เรียกดูไฟล์บีบอัด"),
    "values-ug": ("سىن ئوينا", "سۈرەت كۆر", "پىرىسلانغان ھۆججەتنى كۆر"),
    "values-bo": ("བརྙན་འཁྲབ་", "པར་ལྟ་", "གཉིས་འགྱུར་ཡིག་ཆ་ལྟ་"),
    "values-mn": ("Видео тоглуулах", "Зураг харах", "Архив үзэх"),
    "values-b+mn+Mong": ("ᠪᠢᠴᠢᠯᠡᠭ ᠲᠣᠭᠯᠠᠬᠤ", "ᠵᠢᠷᠤᠭ ᠦᠵᠡᠬᠦ", "ᠳᠠᠷᠤᠭᠰᠠᠨ ᠪᠢᠴᠢᠭ ᠦᠵᠡᠬᠦ"),
    "values-mnc": ("ᡩᡝᡵᡳᠪᡠ ᡨᠠᠴᡳᡴᡡ", "ᠵᡠᠷᡠ ᡨᡠᠸᠠᠮᠪᡳ", "ᠨᡳᡵᡠᠮᠪᡠ ᡩᠠᠩᠰᡝ ᡨᡠᠸᠠᠮᠪᡳ"),
    "values-sjo": ("ᡩᡝᡵᡞᠪᡠ ᡨᠠᠴᡞᡴᡡ", "ᠵᡠᠷᡠ ᡨᡠᠸᠠᠮᠪᡞ", "ᠨᡳᡵᡠᠮᠪᡠ ᡩᠠᠩᠰᡝ ᡨᡠᠸᠠᠮᠪᡞ"),
    "values-chr": ("ᏗᎦᏘᏯ ᎠᏓᏅᏓ", "ᎠᏓᏅᏓ ᎠᏍᏓᏴᏗ", "ᏗᎦᏘᏯ ᎪᏪᎵ"),
    "values-haw": ("Hoʻokani wikiō", "Nānā kiʻi", "Nānā waihona"),
    "values-mi": ("Purei ataata", "Tiro whakaahua", "Tirotiro pūranga"),
    "values-hmn": ("Tso video", "Saib daim duab", "Tshawb cov ntaub ntawv"),
    "values-b+kk+Arab": ("بەينە ويناتۋ", "سۋرەت كۆرۋ", "قىسىلعان مۇراعاتتى قاراۋ"),
    "values-b+kk+Cyrl": ("Бейнені ойнату", "Суретті көру", "Мұрағатты қарау"),
    "values-sa": ("चलच्चित्रं चालय", "चित्रं पश्य", "सङ्ग्रहं पश्य"),
    "values-la": ("Video reproducere", "Imaginem videre", "Archivum inspicere"),
    "values-eo": ("Ludi videon", "Vidi bildon", "Esplori arĥivon"),
    "values-za": ("Lwg dienzgaenq", "Ndaej ciz", "Ndaej vwnzgen gvaq"),
    "values-ii": ("ꉈꀋꇬ", "ꀋꅉꌦ", "ꀋꅉꇭꄉ"),
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

NAMES = ["play_video", "view_image", "browse_archive"]
ANCHOR = re.compile(r'^(\s*)<string name="viewer_format">.*</string>\s*$')
PIN_RE = re.compile(r'^(?P<ind>\s*)<string name="pin_folder">.*</string>\s*$')

def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace("'", "\\'")

done_pin, done_new, kept_pin, problems = [], [], [], []
for d in sorted(os.listdir(RES)):
    if not d.startswith("values"):
        continue
    if d not in T:
        problems.append(d + " (no translation table)")
        continue
    # --- pin_folder：可能在 strings.xml，也可能在 strings_v190.xml ---
    pin_file = None
    for fn in ("strings.xml", "strings_v190.xml", "strings_v191.xml",
               "strings_appearance.xml"):
        p = os.path.join(RES, d, fn)
        if os.path.isfile(p) and 'name="pin_folder"' in open(p, encoding="utf-8").read():
            pin_file = p
            break
    if pin_file is None:
        problems.append(d + " (pin_folder not found)")
    elif PIN[d] is not None:
        lines = open(pin_file, encoding="utf-8").read().splitlines(keepends=True)
        idx = next(i for i, ln in enumerate(lines) if PIN_RE.match(ln))
        indent = PIN_RE.match(lines[idx]).group("ind")
        tag = UNCERTAIN.get(d)
        block = []
        if tag:
            block.append(f"{indent}<!-- {tag}：顶置新译法，需母语者复核 -->\n")
        block.append(f'{indent}<string name="pin_folder">{esc(PIN[d])}</string>\n')
        lines[idx:idx + 1] = block
        open(pin_file, "w", encoding="utf-8").write("".join(lines))
        done_pin.append(d)
    else:
        kept_pin.append(d)
    # --- 3 条新词条：插进 strings.xml，viewer_format 锚点之后 ---
    p = os.path.join(RES, d, "strings.xml")
    if not os.path.isfile(p):
        problems.append(d + " (no strings.xml)")
        continue
    lines = open(p, encoding="utf-8").read().splitlines(keepends=True)
    if any(f'name="{n}"' in "".join(lines) for n in NAMES):
        problems.append(d + " (already has archive strings)")
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
            block.append(f"{indent}<!-- {tag}：九宫格新词尽力翻译，需母语者复核 -->\n")
        block.append(f'{indent}<string name="{name}">{esc(val)}</string>\n')
    lines[idx + 1:idx + 1] = block
    open(p, "w", encoding="utf-8").write("".join(lines))
    done_new.append(d)

print(f"pin_folder changed: {len(done_pin)} dirs; kept original: {kept_pin}")
print(f"new strings inserted: {len(done_new)} dirs")
if problems:
    print("PROBLEMS:")
    for m in problems:
        print(" ", m)
