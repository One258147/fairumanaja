# -*- coding: utf-8 -*-
"""Generate values-mnc / values-sjo / values-haw / values-mi / values-chr strings.xml
for Fairumanaja v1.9.7 endangered-language switch.

Translation policy (honest best-effort, native-speaker review recommended):
- Manchu (mnc) and Xibe (sjo) are written in the REAL Mongolian script
  (Unicode U+1800-U+18AA), NOT Latin romanization (Max 2026-11-21:
  Latin is wrong). The Latin tables below stay as the human-readable
  source; to_manchu_script / to_xibe_script convert them at generation
  time. Words are the author's best-effort Manchu/Xibe; the script
  conversion itself follows standard Manchu spelling (see the converter
  comments for the k/q rule and the flagged judgment calls).
- Xibe (sjo): derived from the Manchu cognates, respelled in Xibe Latin
  orthography (long vowels written double, no macrons; c -> q in kirqu).
  Xibe is a close relative of Manchu; the two are mutually intelligible.
- Hawaiian (haw): Latin script with kahako (macrons) and 'okina (U+02BB).
- Maori (mi): Latin script with macrons (tohuto).
- Cherokee (chr): Cherokee syllabary. Known Cherokee words are used where known
  (gowehli=paper/file, gawonihisdi=language, nigadv=all, osiyo etc.); modern
  tech verbs with no Cherokee term known to the author are spelled phonetically
  in the syllabary (e.g. kapi=copy, pesti=paste, diliti=delete).
"""
import os
from xml.sax.saxutils import escape

BASE = os.path.expanduser("~/workspace/fairumanaja-simple/app/src/main/res")

MNC = {
"app_name": "Fairumanējā",
"nav_files": "bithe",
"nav_bookmarks": "kemuhe weile",
"nav_recent": "amala",
"nav_tools": "weile",
"search_hint": "bithe baire…",
"sort_name": "gebu i hasiha",
"sort_date": "inenggi i hasiha",
"sort_size": "beye i hasiha",
"sort_type": "hacin i hasiha",
"show_hidden": "beideme bithe joribu",
"copy": "folho",
"cut": "kircu",
"paste": "nonggibu",
"delete": "fetere",
"rename": "gebu halanjabu",
"new_folder": "ice bithe falga",
"new_file": "ice bithe",
"share": "dedure",
"open_with": "neibu",
"properties": "tacihiyan",
"select_all": "gemun sogi",
"cancel": "nakabu",
"ok": "sain",
"create": "banjibu",
"confirm": "akdun",
"settings": "afabuhangge",
"language": "gisun",
"theme": "durun",
"theme_system": "dasan dahabu",
"theme_light": "elden",
"theme_dark": "simhun",
"about": "gaiha",
"version": "temgetu",
"save": "kemu",
"saved": "kemuhe",
"save_failed": "kemu manggi",
"extract": "alhabu",
"compress": "hūwase",
"refresh": "icejibu",
"bookmark": "kemuhe weile",
"open": "neibu",
"folder": "bithe falga",
"file": "bithe",
"error": "hūsun",
"modified": "halanjaha erin",
"path": "jugūn",
"size": "beye",
"name": "gebu",
"dual_pane": "juwe boole",
"drawer_local": "bade",
"drawer_tools": "weile",
"root_directory": "fulehe",
"trash": "holohoto",
"text_editor": "bithe irgeku",
"cannot_access": "ere falga be yaburakū",
"trash_empty": "holohoto akū",
"restore": "bederembu",
"delete_permanently": "kemuni fetere",
"empty_trash": "holohoto fetere",
"moved_to_trash": "holohoto de banjibuhe",
"restored": "bederembuhe",
"permanently_deleted": "kemuni feterehe",
"trash_emptied": "holohoto feterehe",
"restore_failed": "bederembu manggi",
"language_settings": "gisun afabuhangge",
"theme_mode": "durun doro",
"more_options": "amala weile",
"hide": "beidembu",
"undo": "bederembu",
"redo": "dahūme",
"deselect_all": "sogi nakabu",
"unknown": "sarkū",
"splash_animation": "deribu tacikū",
"experimental_features": "sindara weile",
"ui_engine": "UI durun",
"liquid_glass": "muke gucu",
"liquid_glass_mode_translucent": "burulame",
"liquid_glass_mode_blur": "iletu burulame",
"liquid_glass_mode_clear": "gemun iletu",
"copied": "folhohe",
"pasted": "nonggibuhe",
"deleted": "feterehe",
"renamed": "gebu halanjabuha",
"created": "banjibuhe",
"extracted": "alhabuha",
"compressed": "hūwasehe",
"no_files": "ere akū",
"appearance": "durun",
"dynamic_color": "hūlhi boco",
"app_color": "durun boco",
"font_color": "bithe boco",
"font_color_group_base": "boco",
"font_color_group_light": "elden boco",
"font_color_group_dark": "simhun boco",
"compress_item": "hūwase",
"copy_path": "jugūn folho",
"move_item": "hūlhibu",
"more_actions": "amala weile",
"share_zip": "dedure ZIP",
"pin_folder": "falga kemuhe",
"unpin_folder": "falga nakabu",
"encrypt_file": "bithe beidembu",
"decrypt_file": "bithe alhabu",
"bookmarks_title": "kemuhe weile",
"move_to_trash": "holohoto de hūlhibu",
"new_title": "ice",
"new_file_short": "bithe",
"new_folder_short": "falga",
"path_copied": "jugūn folhohe",
"moved": "hūlhibuhe",
"bookmark_added": "kemuhe weile de banjibuhe",
"bookmark_removed": "kemuhe weile feterehe",
"pinned": "kemuhe",
"unpinned": "kemuhe nakabu",
"hidden_done": "beidembuhe",
"unhidden_done": "beideme nakabuhe",
"create_shortcut": "kemuhe weile banjibu",
"endangered_language_switch": "facu gisun halanjabu",
"endangered_language_sub": "manju · sibe · hawai · maori · cherokee",
"pane_stats": "falga: %1$d  bithe: %2$d  beye: %3$s/%4$s",
"storage_internal": "dolo bithe",
"used_space": "acabuha",
"free_space": "endebuku",
"liquid_glass_hint": "doro beilebu, hūlhibu beilebu.",
"experimental_features_hint": "ere weile sindara de bi, halanjame manggi.",
"splash_animation_hint": "dahabufi, deribu tacikū be yabuci; acabufi, tondo yabuci.",
}

def xibe_from_manchu(mnc):
    out = {}
    rep = {"ū": "uu", "ī": "ii", "ē": "ee", "ō": "oo", "ā": "aa", "š": "x"}
    for k, v in mnc.items():
        s = "".join(rep.get(ch, ch) for ch in v)
        out[k] = s
    out["cut"] = "kirqu"
    out["endangered_language_sub"] = "manju · sibe · hawai · maori · cherokee"
    return out

SJO = xibe_from_manchu(MNC)

HAW = {
"app_name": "Fairumanējā",
"nav_files": "Nā Waihona",
"nav_bookmarks": "Nā Puke",
"nav_recent": "Nā Mea Hou",
"nav_tools": "Nā Mea Hana",
"search_hint": "ʻImi i nā waihona…",
"sort_name": "Ma ka inoa",
"sort_date": "Ma ka lā",
"sort_size": "Ma ka nui",
"sort_type": "Ma ke ʻano",
"show_hidden": "Hōʻike i nā mea huna",
"copy": "Kope",
"cut": "ʻOki",
"paste": "Hoʻopili",
"delete": "Holoi",
"rename": "Hoʻololi i ka inoa",
"new_folder": "Kōpaki hou",
"new_file": "Waihona hou",
"share": "Kaʻana",
"open_with": "Wehe me ka",
"properties": "Kikoʻī",
"select_all": "Koho i nā mea a pau",
"cancel": "Kāpae",
"ok": "ʻAe",
"create": "Hana",
"confirm": "Hōʻoia",
"settings": "Nā Hoʻonohonoho",
"language": "ʻŌlelo",
"theme": "Kumuhana",
"theme_system": "Hahai i ka ʻōnaehana",
"theme_light": "Mālamalama",
"theme_dark": "Pōʻeleʻele",
"about": "E pili ana",
"version": "Mana",
"save": "Mālama",
"saved": "Ua mālama ʻia",
"save_failed": "ʻAʻole i mālama ʻia",
"extract": "Wehe",
"compress": "Hoʻopaʻa",
"refresh": "Hoʻouka hou",
"bookmark": "Puke",
"open": "Wehe",
"folder": "Kōpaki",
"file": "Waihona",
"error": "Hewa",
"modified": "Lā i hoʻololi ʻia",
"path": "Ala",
"size": "Nui",
"name": "Inoa",
"dual_pane": "ʻAoʻao pālua",
"drawer_local": "Kūloko",
"drawer_tools": "Nā Mea Hana",
"root_directory": "Aʻa",
"trash": "ʻŌpala",
"text_editor": "Mea Hoʻoponopono Kikokikona",
"cannot_access": "ʻAʻole hiki ke komo i kēia kōpaki",
"trash_empty": "ʻAʻohe ʻōpala",
"restore": "Hoʻihoʻi",
"delete_permanently": "Holoi loa",
"empty_trash": "Hoʻokaʻawale i ka ʻōpala",
"moved_to_trash": "Ua hoʻoneʻe ʻia i ka ʻōpala",
"restored": "Ua hoʻihoʻi ʻia",
"permanently_deleted": "Ua holoi loa ʻia",
"trash_emptied": "Ua hoʻokaʻawale ʻia ka ʻōpala",
"restore_failed": "ʻAʻole i hoʻihoʻi ʻia",
"language_settings": "Hoʻonohonoho ʻŌlelo",
"theme_mode": "Kumuhana",
"more_options": "Nā Koho Hou",
"hide": "Hūnā",
"undo": "Hoʻihoʻi",
"redo": "Hana hou",
"deselect_all": "Hoʻopau i ke koho",
"unknown": "ʻIke ʻole ʻia",
"splash_animation": "Kiʻi ʻOniʻoni",
"experimental_features": "Nā Hana Hoʻāʻo",
"ui_engine": "Mīkini UI",
"liquid_glass": "Aniani Wai",
"liquid_glass_mode_translucent": "Hapa Akaka",
"liquid_glass_mode_blur": "Akaka Pohihihi",
"liquid_glass_mode_clear": "Akaka Loa",
"copied": "Ua kope ʻia",
"pasted": "Ua hoʻopili ʻia",
"deleted": "Ua holoi ʻia",
"renamed": "Ua hoʻololi ʻia ka inoa",
"created": "Ua hana ʻia",
"extracted": "Ua wehe ʻia",
"compressed": "Ua hoʻopaʻa ʻia",
"no_files": "ʻAʻohe mea ma ʻaneʻi",
"appearance": "Helehelena",
"dynamic_color": "Kala Hoʻololi",
"app_color": "Kala o ka Polokalamu",
"font_color": "Kala o ka Hua",
"font_color_group_base": "Nā Kala Kumu",
"font_color_group_light": "Nā Kala Mālamalama",
"font_color_group_dark": "Nā Kala Pōʻeleʻele",
"compress_item": "Hoʻopaʻa",
"copy_path": "Kope i ke ala",
"move_item": "Hoʻoneʻe",
"more_actions": "Nā Koho Hou",
"share_zip": "Kaʻana ZIP",
"pin_folder": "Hoʻopaʻa i ke kōpaki",
"unpin_folder": "Wehe i ke kōpaki",
"encrypt_file": "Hoʻopā i ka waihona",
"decrypt_file": "Wehe i ka waihona",
"bookmarks_title": "Nā Puke",
"move_to_trash": "Hoʻoneʻe i ka ʻōpala",
"new_title": "Hou",
"new_file_short": "Waihona",
"new_folder_short": "Kōpaki",
"path_copied": "Ua kope ʻia ke ala",
"moved": "Ua hoʻoneʻe ʻia",
"bookmark_added": "Ua hoʻohui ʻia i ka puke",
"bookmark_removed": "Ua wehe ʻia mai ka puke",
"pinned": "Ua hoʻopaʻa ʻia",
"unpinned": "Ua wehe ʻia",
"hidden_done": "Ua hūnā ʻia",
"unhidden_done": "Ua hōʻike ʻia",
"create_shortcut": "Hana i ka pōkole",
"endangered_language_switch": "Hoʻololi ʻŌlelo Hoʻoweliweli ʻia",
"endangered_language_sub": "Manaku · Xibe · Hawaiʻi · Māori · Cherokee",
"pane_stats": "Kōpaki: %1$d  Waihona: %2$d  Hoʻohana: %3$s/%4$s",
"storage_internal": "Waihona Kūloko",
"used_space": "Hoʻohana ʻia",
"free_space": "Waiho",
"liquid_glass_hint": "Kaomi i ka lālani e koho i ke ʻano, kaomi i ke kī e hoʻā.",
"experimental_features_hint": "Ke hoʻāʻo ʻia nei kēia mau hana, hiki ke loli.",
"splash_animation_hint": "Ke hoʻā ʻia, e pāʻani mua ʻia ke kiʻi ʻoniʻoni i kēlā me kēia manawa e hoʻomaka ai mai ka pākaukau; ke pio, e komo pololei i ka ʻaoʻao nui.",
}

MI = {
"app_name": "Fairumanējā",
"nav_files": "Ngā Kōnae",
"nav_bookmarks": "Ngā Tohuwāhi",
"nav_recent": "Ngā Mea Tata",
"nav_tools": "Ngā Taputapu",
"search_hint": "Rapu kōnae…",
"sort_name": "Mā te ingoa",
"sort_date": "Mā te rā",
"sort_size": "Mā te rahi",
"sort_type": "Mā te momo",
"show_hidden": "Whakaatu i ngā mea huna",
"copy": "Tārua",
"cut": "Tapahi",
"paste": "Whakapiri",
"delete": "Muku",
"rename": "Whakaingoa anō",
"new_folder": "Kōpaki hōu",
"new_file": "Kōnae hōu",
"share": "Tiri",
"open_with": "Whakatuwhera mā",
"properties": "Taipitopito",
"select_all": "Tīpako katoa",
"cancel": "Whakakore",
"ok": "Āe",
"create": "Hanga",
"confirm": "Whakaū",
"settings": "Tautuhinga",
"language": "Reo",
"theme": "Kaupapa",
"theme_system": "Whai i te pūnaha",
"theme_light": "Mārama",
"theme_dark": "Pōuri",
"about": "Mō",
"version": "Putanga",
"save": "Tiaki",
"saved": "Kua tiakina",
"save_failed": "Kāore i tiakina",
"extract": "Tango",
"compress": "Kōpeke",
"refresh": "Tāmata anō",
"bookmark": "Tohuwāhi",
"open": "Whakatuwhera",
"folder": "Kōpaki",
"file": "Kōnae",
"error": "Hapa",
"modified": "Rā i whakarerekētia",
"path": "Ara",
"size": "Rahi",
"name": "Ingoa",
"dual_pane": "Paparua",
"drawer_local": "Ā-rohe",
"drawer_tools": "Ngā Taputapu",
"root_directory": "Pakiaka",
"trash": "Ipu Para",
"text_editor": "Ētita Kuputuhi",
"cannot_access": "Kāore e taea te uru ki tēnei kōpaki",
"trash_empty": "Kāore he para",
"restore": "Whakaora",
"delete_permanently": "Muku tūturu",
"empty_trash": "Whakawātea i te ipu para",
"moved_to_trash": "Kua nukuhia ki te ipu para",
"restored": "Kua whakaorangia",
"permanently_deleted": "Kua mukua tūturu",
"trash_emptied": "Kua whakawāteatia te ipu para",
"restore_failed": "Kāore i whakaorangia",
"language_settings": "Tautuhinga Reo",
"theme_mode": "Kaupapa",
"more_options": "Ētahi atu kōwhiringa",
"hide": "Huna",
"undo": "Wetekina",
"redo": "Mahia anō",
"deselect_all": "Whakakore i te tīpakonga",
"unknown": "Kāore e mōhiotia",
"splash_animation": "Waitohu Whakaatu",
"experimental_features": "Ngā Āhuatanga Whakamātau",
"ui_engine": "Pūkaha UI",
"liquid_glass": "Karāhe Wai",
"liquid_glass_mode_translucent": "Puataata",
"liquid_glass_mode_blur": "Puataata pōrearea",
"liquid_glass_mode_clear": "Puataata katoa",
"copied": "Kua tāruatia",
"pasted": "Kua whakapirihia",
"deleted": "Kua mukua",
"renamed": "Kua whakaingoatia anō",
"created": "Kua hangaia",
"extracted": "Kua tangohia",
"compressed": "Kua kōpeketia",
"no_files": "Kāore he mea i konei",
"appearance": "Āhua",
"dynamic_color": "Tae hihiri",
"app_color": "Tae o te taupānga",
"font_color": "Tae o te momotuhi",
"font_color_group_base": "Ngā tae taketake",
"font_color_group_light": "Ngā tae mārama",
"font_color_group_dark": "Ngā tae pōuri",
"compress_item": "Kōpeke",
"copy_path": "Tārua i te ara",
"move_item": "Nuku",
"more_actions": "Ētahi atu kōwhiringa",
"share_zip": "Tiri ZIP",
"pin_folder": "Titi ki te kōpaki",
"unpin_folder": "Wetekina te titi",
"encrypt_file": "Whakamuna i te kōnae",
"decrypt_file": "Wetekina te kōnae",
"bookmarks_title": "Ngā Tohuwāhi",
"move_to_trash": "Nuku ki te ipu para",
"new_title": "Hōu",
"new_file_short": "Kōnae",
"new_folder_short": "Kōpaki",
"path_copied": "Kua tāruatia te ara",
"moved": "Kua nukuhia",
"bookmark_added": "Kua tāpirihia ki te tohuwāhi",
"bookmark_removed": "Kua tangohia mai i te tohuwāhi",
"pinned": "Kua titia",
"unpinned": "Kua wetekina",
"hidden_done": "Kua hunatia",
"unhidden_done": "Kua whakaaturia",
"create_shortcut": "Hanga i te pokatata",
"endangered_language_switch": "Whakawhiti Reo Morearea",
"endangered_language_sub": "Manchu · Xibe · Hawaiʻi · Māori · Cherokee",
"pane_stats": "Kōpaki: %1$d  Kōnae: %2$d  Rokiroki: %3$s/%4$s",
"storage_internal": "Rokiroki ā-roto",
"used_space": "Kua whakamahia",
"free_space": "Wātea",
"liquid_glass_hint": "Patohia te rārangi hei kōwhiri i te aratau, patohia te whakakā hei whakakā.",
"experimental_features_hint": "Kei te whakamātauria tonu ēnei āhuatanga, ā, tērā pea ka huri.",
"splash_animation_hint": "Ka whakakāia, ka purei tuatahi te waitohu whakaatu i ngā wā katoa ka whakatuwheratia mai i te papamahi; ka whakawetohia, ka uru tōtika ki te whārangi matua.",
}

CHR = {
"app_name": "Fairumanējā",
"nav_files": "ᏗᎪᏪᎵ",
"nav_bookmarks": "ᎠᏓᏅᏖᏍᎬ ᎪᏪᎵ",
"nav_recent": "ᎢᏤ ᎪᏪᎵ",
"nav_tools": "ᎠᏓᏅᏖᏍᎬ",
"search_hint": "ᎪᏪᎵ ᎠᏰᏍᏗ…",
"sort_name": "ᏙᏳ ᏐᎳᏘ",
"sort_date": "ᎢᎦ ᏐᎳᏘ",
"sort_size": "ᎤᏍᏗ ᏐᎳᏘ",
"sort_type": "ᎠᏓᏅᏖᏍᎬ ᏐᎳᏘ",
"show_hidden": "ᎠᏓᏅᏖᏍᎬ ᎤᏬᏪᎳᏅᎯ",
"copy": "ᎧᏈ",
"cut": "ᎧᏘ",
"paste": "ᏇᏍᏘ",
"delete": "ᏗᎵᏘ",
"rename": "ᎵᏁᎻ",
"new_folder": "ᎢᏤ ᎪᏪᎵ ᎠᏓᏍᏓᏴᏗ",
"new_file": "ᎢᏤ ᎪᏪᎵ",
"share": "ᏑᎡᎵ",
"open_with": "ᎣᏈᏂ ᎠᎴ",
"properties": "ᏆᎳᎻᏘ",
"select_all": "ᏂᎦᏛ",
"cancel": "ᎠᏎᏍᏗ",
"ok": "ᎥᎥ",
"create": "ᎦᎵᎡᏘ",
"confirm": "ᎧᏂᏌᎻ",
"settings": "ᎠᏓᏅᏖᏍᎬ",
"language": "ᎦᏬᏂᎯᏍᏗ",
"theme": "ᏖᎻ",
"theme_system": "ᏗᎦᎵᏍᏗ",
"theme_light": "ᎢᎦ",
"theme_dark": "ᏒᏅᏱ",
"about": "ᏧᏓᎴᏅᏓ",
"version": "ᏫᏥᏂ",
"save": "ᎠᏓᏍᏓᏴᏗ",
"saved": "ᎠᏓᏍᏓᏴᏗᎢ",
"save_failed": "ᎠᏓᏍᏓᏴᏗ ᎥᏝ",
"extract": "ᎡᏍᏘᎳᎩᏘ",
"compress": "ᎧᎻᏇᎵᏍ",
"refresh": "ᎵᏔᏍ",
"bookmark": "ᎠᏓᏅᏖᏍᎬ ᎪᏪᎵ",
"open": "ᎣᏈᏂ",
"folder": "ᎪᏪᎵ ᎠᏓᏍᏓᏴᏗ",
"file": "ᎪᏪᎵ",
"error": "ᎠᏎᏍᏗ",
"modified": "ᎠᏓᏅᏖᏍᎬ ᎢᎦ",
"path": "ᎤᎦᏔᏅᎯ",
"size": "ᎤᏍᏗ",
"name": "ᏙᏳ",
"dual_pane": "ᏔᎵ ᎠᏓᏍᏓᏴᏗ",
"drawer_local": "ᏧᏓᎴᏅᏓ",
"drawer_tools": "ᎠᏓᏅᏖᏍᎬ",
"root_directory": "ᎠᏍᏓᏴᏗ",
"trash": "ᎤᏍᏗ ᎠᏓᏍᏓᏴᏗ",
"text_editor": "ᎪᏪᎵ ᎠᏓᏅᏖᏍᎬ",
"cannot_access": "ᎠᏓᏍᏓᏴᏗ ᎥᏝ",
"trash_empty": "ᎤᏍᏗ ᎠᏓᏍᏓᏴᏗ ᎥᏝ",
"restore": "ᎠᏓᏅᏖᏍᎬ ᎠᏓᏍᏓᏴᏗ",
"delete_permanently": "ᏗᎵᏘ ᎤᏬᏪᎳᏅᎯ",
"empty_trash": "ᎤᏍᏗ ᎠᏓᏍᏓᏴᏗ ᏗᎵᏘ",
"moved_to_trash": "ᎤᏍᏗ ᎠᏓᏍᏓᏴᏗ ᎠᏓᏅᏖᏍᎬ",
"restored": "ᎠᏓᏅᏖᏍᎬ ᎠᏓᏍᏓᏴᏗᎢ",
"permanently_deleted": "ᏗᎵᏘ ᎤᏬᏪᎳᏅᎯᎢ",
"trash_emptied": "ᎤᏍᏗ ᎠᏓᏍᏓᏴᏗ ᏗᎵᏘᎢ",
"restore_failed": "ᎠᏓᏅᏖᏍᎬ ᎠᏓᏍᏓᏴᏗ ᎥᏝ",
"language_settings": "ᎦᏬᏂᎯᏍᏗ ᎠᏓᏅᏖᏍᎬ",
"theme_mode": "ᏖᎻ ᎠᏓᏅᏖᏍᎬ",
"more_options": "ᎤᏬᏪᎳᏅᎯ ᎠᏓᏅᏖᏍᎬ",
"hide": "ᎠᏓᏅᏖᏍᎬ",
"undo": "ᎠᏓᏅᏖᏍᎬ ᎠᏓᏍᏓᏴᏗ",
"redo": "ᎠᏓᏅᏖᏍᎬ ᎠᏓᏍᏓᏴᏗᎢ",
"deselect_all": "ᏂᎦᏛ ᎠᏎᏍᏗ",
"unknown": "ᎠᏓᏅᏖᏍᎬ ᎥᏝ",
"splash_animation": "ᎢᏤ ᎤᏬᏪᎳᏅᎯ",
"experimental_features": "ᎠᏓᏅᏖᏍᎬ ᎤᏬᏪᎳᏅᎯ",
"ui_engine": "UI ᎠᏓᏅᏖᏍᎬ",
"liquid_glass": "ᎠᎹ ᎤᎦᏔᏅᎯ",
"liquid_glass_mode_translucent": "ᎤᏬᏪᎳᏅᎯ ᎠᏓᏅᏖᏍᎬ",
"liquid_glass_mode_blur": "ᎠᏓᏅᏖᏍᎬ ᎠᏓᏍᏓᏴᏗ",
"liquid_glass_mode_clear": "ᏂᎦᏛ ᎤᏬᏪᎳᏅᎯ",
"copied": "ᎧᏈᎢ",
"pasted": "ᏇᏍᏘᎢ",
"deleted": "ᏗᎵᏘᎢ",
"renamed": "ᎵᏁᎻᎢ",
"created": "ᎦᎵᎡᏘᎢ",
"extracted": "ᎡᏍᏘᎳᎩᏘᎢ",
"compressed": "ᎧᎻᏇᎵᏍᎢ",
"no_files": "ᎪᏪᎵ ᎥᏝ",
"appearance": "ᎤᏬᏪᎳᏅᎯ",
"dynamic_color": "ᎠᏓᏅᏖᏍᎬ ᎤᏬᏪᎳᏅᎯ",
"app_color": "ᎤᏬᏪᎳᏅᎯ ᎠᏓᏅᏖᏍᎬ",
"font_color": "ᎪᏪᎵ ᎤᏬᏪᎳᏅᎯ",
"font_color_group_base": "ᎤᏬᏪᎳᏅᎯ",
"font_color_group_light": "ᎢᎦ ᎤᏬᏪᎳᏅᎯ",
"font_color_group_dark": "ᏒᏅᏱ ᎤᏬᏪᎳᏅᎯ",
"compress_item": "ᎧᎻᏇᎵᏍ",
"copy_path": "ᎤᎦᏔᏅᎯ ᎧᏈ",
"move_item": "ᎠᏓᏅᏖᏍᎬ",
"more_actions": "ᎤᏬᏪᎳᏅᎯ ᎠᏓᏅᏖᏍᎬ",
"share_zip": "ᏑᎡᎵ ZIP",
"pin_folder": "ᎪᏪᎵ ᎠᏓᏍᏓᏴᏗ ᎠᏓᏅᏖᏍᎬ",
"unpin_folder": "ᎪᏪᎵ ᎠᏓᏍᏓᏴᏗ ᎠᏎᏍᏗ",
"encrypt_file": "ᎪᏪᎵ ᎠᏓᏅᏖᏍᎬ",
"decrypt_file": "ᎪᏪᎵ ᎠᏓᏅᏖᏍᎬ ᎠᏓᏍᏓᏴᏗ",
"bookmarks_title": "ᎠᏓᏅᏖᏍᎬ ᎪᏪᎵ",
"move_to_trash": "ᎤᏍᏗ ᎠᏓᏍᏓᏴᏗ ᎠᏓᏅᏖᏍᎬ",
"new_title": "ᎢᏤ",
"new_file_short": "ᎪᏪᎵ",
"new_folder_short": "ᎠᏓᏍᏓᏴᏗ",
"path_copied": "ᎤᎦᏔᏅᎯ ᎧᏈᎢ",
"moved": "ᎠᏓᏅᏖᏍᎬᎢ",
"bookmark_added": "ᎠᏓᏅᏖᏍᎬ ᎪᏪᎵ ᎠᏓᏍᏓᏴᏗᎢ",
"bookmark_removed": "ᎠᏓᏅᏖᏍᎬ ᎪᏪᎵ ᏗᎵᏘᎢ",
"pinned": "ᎠᏓᏅᏖᏍᎬᎢ",
"unpinned": "ᎠᏎᏍᏗᎢ",
"hidden_done": "ᎠᏓᏅᏖᏍᎬᎢ",
"unhidden_done": "ᎠᏓᏅᏖᏍᎬ ᎠᏓᏍᏓᏴᏗᎢ",
"create_shortcut": "ᎠᏓᏅᏖᏍᎬ ᎦᎵᎡᏘ",
"endangered_language_switch": "ᎦᏬᏂᎯᏍᏗ ᎠᏓᏅᏖᏍᎬ",
"endangered_language_sub": "Manju · Sibe · Hawaiʻi · Māori · ᏣᎳᎩ",
"pane_stats": "ᎪᏪᎵ ᎠᏓᏍᏓᏴᏗ: %1$d  ᎪᏪᎵ: %2$d  ᎠᏓᏍᏓᏴᏗ: %3$s/%4$s",
"storage_internal": "ᏧᏓᎴᏅᏓ ᎠᏓᏍᏓᏴᏗ",
"used_space": "ᏳᏍᏘ",
"free_space": "ᏫᎵ",
"liquid_glass_hint": "ᎠᏓᏍᏓᏴᏗ ᏂᎦᏛ, ᎠᏓᏅᏖᏍᎬ ᎠᏓᏍᏓᏴᏗ.",
"experimental_features_hint": "ᎠᏓᏅᏖᏍᎬ ᎤᏬᏪᎳᏅᎯ ᎠᏓᏍᏓᏴᏗ ᎢᏤ.",
"splash_animation_hint": "ᎢᏤ ᎤᏬᏪᎳᏅᎯ ᎠᏓᏅᏖᏍᎬ, ᎠᏓᏅᏖᏍᎬ ᎠᏓᏍᏓᏴᏗ ᎥᏝ.",
}

# ---- 真满文/锡伯文转写（v1.9.7 重做，Max 2026-11-21 纠正）----
# 拉丁化是错的。满语、锡伯语必须用真正的蒙古文 Unicode（U+1800-U+18AA）。
# 上面的拉丁表仍是人类可读的源，转写函数在生成时把 mnc/sjo 转成真满文。
# 拼写依据（逐词按标准满文拼写转写；拿不准的词见 UNCERTAIN 标注）：
# - 满文例证（Max 本人写的）：baturu = ᠪᠠᡨᡠᡵᡠ，banaha = ᠪᠠᠨᠠᡥᠠ，
#   manju gisun = ᠮᠠᠨᠵᡠ ᡤᡳᠰᡠᠨ，bithe = ᠪᡳᡨᡥᡝ。
# - 字母对应（Möllendorff -> 蒙古文）：a ᠠ，e ᡝ，i ᡳ，o ᠣ，u ᡠ，ū ᡡ，
#   n ᠨ，ng ᠩ，b ᠪ，p ᠫ，g ᡤ，h ᡥ，t ᡨ，d ᡩ，š ᡧ，f ᡶ，r ᡵ，
#   c ᠴ，j ᠵ，s ᠰ，l ᠯ，m ᠮ，y ᠶ，w ᠸ。
#   只有 u/ū 在字母上有区分（ᡠ/ᡡ），a/ā、e/ē、i/ī、o/ō 在满文里不分长短。
# - k 的两种写法（元音和谐）：词首元音是 a/o/ū 时写 ᡴ（U+1874），是 e/i/u
#   时写 ᠬ（U+182C）。如 kemu -> ᠬᡝᠮᡠ，kircu -> ᠬᡳᡵᠴᡠ，
#   nakabu -> ᠨᠠᡴᠠᠪᡠ，tacikū -> ᡨᠠᠴᡳᡴᡡ；akdun（k 在音节尾，按词首
#   元音 a）-> ᠠᡴᡩᡠᠨ（此词为判断，见 UNCERTAIN_MNC）。
# - g 永远是 ᡤ，h 永远是 ᡥ（gisun = ᡤᡳᠰᡠᠨ 为证）。
# - 锡伯文用锡伯文区块的字母：i ᡞ，k ᡣ，j ᡪ，f ᡫ，r ᡰ，c/q ᡱ；
#   其余与满文共用（e ᡝ，u ᡠ，ū ᡡ，g ᡤ，h ᡥ，t ᡨ，d ᡩ，š ᡧ）。
#   SJO 表里的 c/q 都按腭化塞擦音转成 ᡱ：这些词是满语同源词照搬，
#   c 在此代表满语 [tʃ]；真正锡伯语拉丁正字法里 c=[tsʰ] 应为 ᡮ——
#   如母语者确认这些词在锡伯语里实为 ts 音，需改 ᡮ（见 UNCERTAIN_SJO）。
# - 品牌名 Fairumanējā 与缩写 UI、ZIP 保留拉丁字母（与切罗基语保留 UI/ZIP 一致）。
import re as _re

_VOWELS = set("aeiouāēīōū")

_MNC_LETTER = {
    "a": "\u1820", "ā": "\u1820",
    "e": "\u185d", "ē": "\u185d",
    "i": "\u1873", "ī": "\u1873",
    "o": "\u1823", "ō": "\u1823",
    "u": "\u1860", "ū": "\u1861",
    "n": "\u1828", "b": "\u182a", "p": "\u182b",
    "g": "\u1864", "h": "\u1865",
    "t": "\u1868", "d": "\u1869",
    "š": "\u1867", "f": "\u1876", "r": "\u1875",
    "c": "\u1834", "j": "\u1835",
    "s": "\u1830", "l": "\u182f", "m": "\u182e",
    "y": "\u1836", "w": "\u1838",
}

_SJO_LETTER = {
    "a": "\u1820", "e": "\u185d", "i": "\u185e", "o": "\u1823",
    "u": "\u1860",
    "n": "\u1828", "b": "\u182a", "p": "\u182b",
    "g": "\u1864", "h": "\u1865",
    "t": "\u1868", "d": "\u1869",
    "x": "\u1867", "f": "\u186b", "r": "\u1870",
    "c": "\u1871", "q": "\u1871", "j": "\u186a",
    "z": "\u186f",
    "s": "\u1830", "l": "\u182f", "m": "\u182e",
    "y": "\u1836", "w": "\u1838",
}
_SJO_DOUBLE = {"aa": "\u1820", "ee": "\u185d", "ii": "\u185e",
               "oo": "\u1823", "uu": "\u1861", "ng": "\u1829"}

_KEEP_LATIN = {"Fairumanējā", "UI", "ZIP"}
_PLACEHOLDER = _re.compile(r"%[0-9]+\$[ds]")

# 拿不准的词（转写时的判断点，不是硬翻；请母语者复核）：
#  MNC：akdun（k 在音节尾，按词首元音 a 写 ᡴ）；endangered_language_sub 里
#       的 hawai / maori / cherokee 三个外来名是按发音拼进满文的。
#  SJO：kirqu / tacihiyan / hacin / ice / icejibu（c、q 按腭化塞擦音转 ᡱ，
#       见上面注释）；endangered_language_sub 里的外来名同理；uu -> ᡡ 是
#       按满语 ū 的对应关系定的惯例。
UNCERTAIN_MNC = {"akdun", "hawai", "maori", "cherokee"}
UNCERTAIN_SJO = {"kirqu", "tacihiyan", "hacin", "ice", "icejibu",
                 "hawai", "maori", "cherokee"}


def _manchu_word(w):
    first_vowel = next((ch for ch in w if ch in _VOWELS), None)
    k_letter = "\u1874" if first_vowel in ("a", "ā", "o", "ō", "ū") else "\u182c"
    out = []
    i = 0
    while i < len(w):
        if w[i:i + 2] == "ng":
            out.append("\u1829")
            i += 2
            continue
        if w[i:i + 2] == "ch":
            # 外来名里的 ch（如 cherokee）是一个音 [tʃ]，不是 c+h。
            out.append("\u1834")
            i += 2
            continue
        ch = w[i]
        if ch == "k":
            out.append(k_letter)
        elif ch in _MNC_LETTER:
            out.append(_MNC_LETTER[ch])
        else:
            out.append(ch)
        i += 1
    return "".join(out)


def _xibe_word(w):
    out = []
    i = 0
    while i < len(w):
        two = w[i:i + 2]
        if two == "ch":
            # 外来名里的 ch（如 cherokee）是一个音 [tʃ]，不是 c+h。
            out.append("\u1871")
            i += 2
            continue
        if two in _SJO_DOUBLE:
            out.append(_SJO_DOUBLE[two])
            i += 2
            continue
        ch = w[i]
        if ch == "k":
            out.append("\u1863")
        elif ch in _SJO_LETTER:
            out.append(_SJO_LETTER[ch])
        else:
            out.append(ch)
        i += 1
    return "".join(out)


def _convert_text(text, word_fn):
    holders = {}

    def _hold(m):
        key = "\x00%d\x00" % len(holders)
        holders[key] = m.group(0)
        return key

    text = _PLACEHOLDER.sub(_hold, text)

    def _word(m):
        w = m.group(0)
        if w in _KEEP_LATIN:
            return w
        return word_fn(w)

    text = _re.sub(r"[A-Za-zāēīōūš]+", _word, text)
    for k, v in holders.items():
        text = text.replace(k, v)
    return text


def to_manchu_script(key, value):
    if key == "app_name":
        return "Fairumanējā"
    return _convert_text(value, _manchu_word)


def to_xibe_script(key, value):
    if key == "app_name":
        return "Fairumanējā"
    return _convert_text(value, _xibe_word)


LANGS = {"mnc": MNC, "sjo": SJO, "haw": HAW, "mi": MI, "chr": CHR}

# Sanity: all five tables must have identical key sets.
keys = set(MNC.keys())
for tag, t in LANGS.items():
    assert set(t.keys()) == keys, (tag, set(t.keys()) ^ keys)
print("keys per language:", len(keys))

for tag, table in LANGS.items():
    if tag == "mnc":
        table = {k: to_manchu_script(k, v) for k, v in table.items()}
    elif tag == "sjo":
        table = {k: to_xibe_script(k, v) for k, v in table.items()}
    d = os.path.join(BASE, f"values-{tag}")
    os.makedirs(d, exist_ok=True)
    comment = ("v1.9.7 濒危多语言切换：真满文/锡伯文（蒙古文 Unicode）；"
               "尽力翻译，建议母语者复核；未列出的字符串回退到默认中文。"
               if tag in ("mnc", "sjo") else
               "v1.9.7 濒危多语言切换：尽力翻译，建议母语者复核；"
               "未列出的字符串回退到默认中文。")
    lines = ["<?xml version=\"1.0\" encoding=\"utf-8\"?>",
             f"<!-- {comment} -->",
             "<resources>"]
    for k in sorted(table.keys()):
        lines.append(f'    <string name="{k}">{escape(table[k])}</string>')
    lines.append("</resources>")
    with open(os.path.join(d, "strings.xml"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(f"wrote values-{tag}/strings.xml ({len(table)} strings)")
