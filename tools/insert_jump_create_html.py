#!/usr/bin/env python3
"""v1.9.8 路径跳转 + 创建HTML：新增 3 条词条，插进各语言 strings.xml，
media_audios 锚点之后。48 个语言目录全覆盖；不确定的语言在词条上方加
<!-- UNCERTAIN_XX --> 注释，按项目惯例。
基线：323 -> 326。
新词：
  jump_title        跳转对话框标题
  create_html       顶部 ⋮ 菜单“创建HTML” / 创建 HTML 对话框标题
  jump_invalid_path 跳转目标不存在或不是文件夹时的提示
"""
import os, re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")

# 每个语言三条：(jump_title, create_html, jump_invalid_path)
T = {
    "values": ("跳转", "创建HTML", "路径不存在或不是文件夹"),
    "values-en": ("Jump", "Create HTML", "Path does not exist or is not a folder"),
    "values-en-rUS": ("Jump", "Create HTML", "Path does not exist or is not a folder"),
    "values-zh-rTW": ("跳轉", "建立 HTML", "路徑不存在或不是資料夾"),
    "values-yue": ("跳轉", "建立 HTML", "路徑唔存在或者唔係資料夾"),
    "values-lzh": ("跳轉", "創建 HTML", "路徑不存在或非資料夾"),
    "values-ar": ("انتقال", "إنشاء HTML", "المسار غير موجود أو ليس مجلدًا"),
    "values-ar-rEG": ("انتقال", "إنشاء HTML", "المسار مش موجود أو مش مجلد"),
    "values-fr": ("Aller à", "Créer un fichier HTML", "Le chemin n'existe pas ou n'est pas un dossier"),
    "values-de": ("Springen zu", "HTML erstellen", "Pfad existiert nicht oder ist kein Ordner"),
    "values-ja": ("ジャンプ", "HTMLを作成", "パスが存在しないか、フォルダではありません"),
    "values-ko": ("이동", "HTML 만들기", "경로가 존재하지 않거나 폴더가 아닙니다"),
    "values-ru": ("Перейти", "Создать HTML", "Путь не существует или не является папкой"),
    "values-es": ("Ir a", "Crear HTML", "La ruta no existe o no es una carpeta"),
    "values-pt": ("Ir para", "Criar HTML", "O caminho não existe ou não é uma pasta"),
    "values-it": ("Vai a", "Crea HTML", "Il percorso non esiste o non è una cartella"),
    "values-nl": ("Ga naar", "HTML maken", "Pad bestaat niet of is geen map"),
    "values-sv": ("Gå till", "Skapa HTML", "Sökvägen finns inte eller är inte en mapp"),
    "values-nb": ("Gå til", "Opprett HTML", "Banen finnes ikke eller er ikke en mappe"),
    "values-da": ("Gå til", "Opret HTML", "Stien findes ikke eller er ikke en mappe"),
    "values-pl": ("Przejdź do", "Utwórz HTML", "Ścieżka nie istnieje lub nie jest folderem"),
    "values-el": ("Μετάβαση", "Δημιουργία HTML", "Η διαδρομή δεν υπάρχει ή δεν είναι φάκελος"),
    "values-tr": ("Git", "HTML oluştur", "Yol mevcut değil veya klasör değil"),
    "values-hy": ("Անցնել", "Ստեղծել HTML", "Ուղին չկա կամ թղթապանակ չէ"),
    "values-hu": ("Ugrás", "HTML létrehozása", "Az útvonal nem létezik vagy nem mappa"),
    "values-fa": ("پرش", "ایجاد HTML", "مسیر وجود ندارد یا پوشه نیست"),
    "values-hi": ("जाएं", "HTML बनाएं", "पथ मौजूद नहीं है या फ़ोल्डर नहीं है"),
    "values-in": ("Lompat", "Buat HTML", "Jalur tidak ada atau bukan folder"),
    "values-ms": ("Lompat", "Cipta HTML", "Laluan tidak wujud atau bukan folder"),
    "values-fil": ("Pumunta", "Gumawa ng HTML", "Ang path ay hindi umiiral o hindi isang folder"),
    "values-th": ("ไปที่", "สร้าง HTML", "เส้นทางไม่มีอยู่หรือไม่ใช่โฟลเดอร์"),
    "values-ug": ("ئۆتۈش", "HTML قۇرۇش", "يول مەۋجۇت ئەمەس ياكى قالتا ئەمەس"),
    "values-bo": ("འགྲོ་", "HTML བཟོ་", "ལམ་འདི་མེད་པའམ་སྣོད་མིན་པ་རེད་"),
    "values-mn": ("Очих", "HTML үүсгэх", "Зам байхгүй эсвэл хавтас биш байна"),
    "values-b+mn+Mong": ("ᠣᠴᠢᠬᠤ", "HTML ᠪᠦᠲᠦᠭᠡᠬᠦ", "ᠵᠠᠮ ᠪᠠᠶᠢᠬᠤ ᠦᠭᠡᠢ ᠪᠤᠶᠤ ᠬᠠᠪᠲᠠᠰᠤ ᠪᠢᠰᠢ"),
    "values-mnc": ("ᡤᡝᠨᡝᠮᠪᡳ", "HTML ᠪᠠᠨᠵᡳᠪᡠᠮᠪᡳ", "ᠵᡠᡤᡡᠨ ᠠᡴᡡ ᠪᠠ ᡶᠣᠯᡩᡝᡵ ᠸᠠᡴᠠ"),
    "values-sjo": ("ᡤᡝᠨᡝᠮᠪᡳ", "HTML ᠪᠠᠨᠵᡳᠪᡠᠮᠪᡳ", "ᠵᡠᡤᡡᠨ ᠠᡴᡡ ᠪᠠ ᡶᠣᠯᡩᡝᡵ ᠸᠠᡴᠠ"),
    "values-chr": ("ᎤᎦᏔᏅ", "HTML ᎠᏕᎳᏗᏍᏗ", "ᎠᏍᏆᏂᎪᏗ ᎥᏝ ᏱᎩ ᎠᏓᏅᏓ ᏱᎩ"),
    "values-haw": ("Lele", "Hana i HTML", "ʻAʻole e loaʻa ana ke ala a ʻaʻole ia he waihona"),
    "values-mi": ("Haere ki", "Hangaia HTML", "Kāore te ara i te noho, kāore rānei he kōpaki"),
    "values-hmn": ("Mus", "Tsim HTML", "Txoj kev tsis muaj los sis tsis yog lub nplaub tshev"),
    "values-b+kk+Arab": ("ءوتۋ", "HTML جاساۋ", "جول جوق نەمەسە قالتا ەمەس"),
    "values-b+kk+Cyrl": ("Өту", "HTML жасау", "Жол жоқ немесе қалта емес"),
    "values-sa": ("गच्छतु", "HTML रचयतु", "पन्था नास्ति वा सञ्चिका नास्ति"),
    "values-la": ("I", "HTML crea", "Via non exstat aut non est directorium"),
    "values-eo": ("Salti al", "Krei HTML", "La vojo ne ekzistas aŭ ne estas dosierujo"),
    "values-za": ("Bae", "Sang HTML", "Lwg roen mbouj youq roxnaeuz mbouj dwg faiqloeg"),
    "values-ii": ("ꂷ", "HTML ꀋꉙ", "ꌠꃀ ꀋꊿꀋꉆꁱꂷ"),
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

NAMES = ("jump_title", "create_html", "jump_invalid_path")
ANCHOR = re.compile(r'^(\s*)<string name="media_audios">.*</string>\s*$')

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
    text = open(p, encoding="utf-8").read()
    if any(f'name="{n}"' in text for n in NAMES):
        problems.append(d + " (already has one of the new strings)")
        continue
    lines = text.splitlines(keepends=True)
    idx = next((i for i, ln in enumerate(lines) if ANCHOR.match(ln)), None)
    if idx is None:
        problems.append(d + " (no media_audios anchor)")
        continue
    indent = ANCHOR.match(lines[idx]).group(1)
    tag = UNCERTAIN.get(d)
    block = []
    if tag:
        block.append(f"{indent}<!-- {tag}：跳转 / 创建HTML 新词尽力翻译，需母语者复核 -->\n")
    for n, v in zip(NAMES, T[d]):
        block.append(f'{indent}<string name="{n}">{esc(v)}</string>\n')
    lines[idx + 1:idx + 1] = block
    open(p, "w", encoding="utf-8").write("".join(lines))
    done.append(d)

print(f"inserted into {len(done)} dirs")
if problems:
    print("PROBLEMS:")
    for m in problems:
        print(" ", m)
