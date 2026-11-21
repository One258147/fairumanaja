package com.fairuman.filemanager

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.os.LocaleListCompat
import android.widget.ProgressBar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.example.liquidglass.BlurMethod
import com.example.liquidglass.ChromaticAberrationEffect
import com.example.liquidglass.GlassMaterial
import com.example.liquidglass.LiquidGlassDialogBuilder
import com.example.liquidglass.LiquidGlassView
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.crypto.BadPaddingException

/**
 * Fairumanējā 全新简单版 1.8.0：双窗格（MT 式）+ 抽屉菜单 + 回收站 + 文本编辑器
 * + Material Design 3 + 18 种语言 + Shizuku 根目录浏览 + 自定义本地储存。
 * v1.8.0（Max 22:07 的 4 点要求）：① 每行末尾的 ⋮ 加回来了，点它弹出该项
 * 的操作菜单（带图标）；② 长按逻辑重改成参考图的样子——长按进多选模式，
 * 顶栏变成“已选择 N 项”+ 已选总大小，右边分享/删除/⋮，每行右边圆形勾选框
 * （选中蓝色对勾），点行切换选中，⋮ 菜单里复制/剪切/粘贴/重命名/属性/
 * 全选/取消选择，✕ 或返回键退出多选，底部的选择操作条删了，不再两套并存；
 * ③ 根目录解析加固：toybox 把 stat 不到的条目印成 `??????????` 的行以前
 * 直接丢掉（Android 16 上这就是“部分读取根目录失败”的根因），现在照样
 * 收下来列出（大小/日期未知就标“未知”），解析不上的行打 Log，子目录读
 * 不出来时清空列表并显示明确错误，不再留着旧列表误导人；实测还发现
 * toybox 对 stat 失败的条目只往 stderr 写 "Permission denied"，stdout
 * 里根本没有这一行——这种名字也从 stderr 里捞回来列出（模拟器 / 下
 * 9 个就是这么找回来的：init.rc、init、metadata、adb_keys……）；
 * 还有一个：toybox 对软链接到目录的参数（如 /sdcard）只印链接本身
 * 那一行不列内容，传给 ls 的路径后加一个斜杠才列出目标内容；④ 根目录不止
 * 浏览了——复制/剪切/粘贴/删除/重命名/新建文件夹/属性都走 Shizuku shell
 * 命令（cp/mv/rm/mkdir/du），失败弹明确提示，绝不静默失败、不闪退。
 * v1.7.1 修 1 处：SAF 删单个文件静默失败——v1.7.0 的删除线程没有超时也没有
 * 日志，提供方调用卡住时回调一次没跑到，选择条一直挂着、文件还在、没有任何
 * 提示。根因是 safDeleteDocument 拿 listSafChildren 返回非空当文件夹，而
 * downloads 提供方对文件 docId 也会返回子项（msf:86 这种文件能“列出” 2 个子项，
 * 其中一个还是 msd:82），于是 msd:82→msd:84→msf:86→msd:82 无限循环；删空文件
 * 夹能成功正是因为无子项不递归。现在删除/重命名都走带 30 秒超时的
 * runSafOpTimeout，超时或失败弹“删除失败，请重试”、清选择态、刷新列表；删除
 * 路径每一步都打 Log（重列几个、找到目标没有、deleteDocument 调没调、返回值
 * 多少），是不是文件夹只认 MIME_TYPE，递归深度上限 32。logcat 可 grep Fairumanaja。
 * v1.7.0：SAF 模式的储存不再仅能浏览——新建文件夹、重命名、删除、复制/
 * 剪切/粘贴、属性、文本编辑都补齐了，用法和普通文件夹一样（入口、菜单、
 * 底部按钮全是同一套）；复制/剪切/粘贴用 ContentResolver 流式走，
 * File 项和 SAF 项可以混装在剪贴板里跨窗格粘贴；SAF 删除直接删，不经过
 * 回收站（回收站只认 File 路径）；根目录（Shizuku）仍仅浏览，不动。
 * v1.6.0 修 1 处：添加本地储存时，第三方提供方（如 ZeroTermux）或别家 App
 * 的 Android/data 目录映射不出真实路径，v1.5.0 直接拒掉；现在任何 tree URI
 * 都收下来（持久授权拿得到就行），浏览时映射路径读不了就改走 SAF
 * （DocumentsContract 列目录，点文件用 ACTION_VIEW + content URI 打开）。
 * v1.5.0 修 4 处：① 根目录在手机上是空的——旧解析器只认 GNU 版 ls -la，
 * Android 自带的 toybox 用 ISO 日期，一行都被丢掉；现在两种格式都认，
 * 软链接批量判目录，失败弹真实错误绝不静默显示空。② 抽屉 ⋮ 菜单只剩
 * 添加本地储存一项。③ 添加本地储存改走安卓原生文档选择器
 * （ACTION_OPEN_DOCUMENT_TREE），选好直接添加，不再手填名称和路径。
 * ④ 删除自定义储存时释放持久 URI 授权。
 * 只要浏览、复制/粘贴、删除、重命名、建文件夹、搜索、排序。
 * 沿用 v1.5 修通的那套打包做法：主题已关 windowActionBar（否则
 * setSupportActionBar 会抛 IllegalStateException），super.onCreate
 * 无条件最先调用，启动失败弹框后立刻 finish，onResume 先判 isInitialized。
 * 语言切换用 AppCompatDelegate.setApplicationLocales（现代接口），不用已废弃的
 * resources.updateConfiguration（那是旧版 v1.2 闪退的头号嫌疑）。
 * 动态配色已在 1.4.0 去掉（设置里不再有这一项，DynamicColors 相关代码全清）。
 * v1.9.0：① 剪切图标重画（两片刀片张开的剪刀）；② ⋮ 菜单加 压缩/复制路径/
 * 移动/更多方式（移动仅双窗格有效，移到另一窗格当前文件夹，不可用时置灰）；
 * 更多方式是图3式的九宫格对话框（最终 8 个额外功能：压缩/分享 ZIP/
 * Pin folder/隐藏/书签/文件加密/文件解密/解压；⋮ 菜单已有的功能不进
 * 九宫格；命令助手整个功能已删）；③ 设置里主题模式改成外观页（主题模式折叠菜单/
 * 动态颜色确认开关/25 种应用配色，种子色用 M3 HCT TonalSpot 方案）；
 * ④ 删除改成图4式对话框（大小+文件数/文件夹数+“移动到回收站”复选框默认勾选，
 * 根目录/SAF 删除也走回收站）；⑤ 回收站重做（每项 ⋮ 只有删除/恢复，恢复和
 * 永久删除都有确认框，长按进无分享的选择模式）；⑥ 底部 + 改成图9式新建对话框
 * （输入框+取消/粘贴/文件/文件夹，粘贴按钮在“文件”按钮左面）；⑦ SAF 粘贴到
 * 根目录的暂存改道（不再用 App 私有缓存，shell 读不到）。
 */
/** SAF 浏览里的一级目录：文档 ID + 显示名。 */
data class SafDir(val docId: String, val name: String)

/** SAF 浏览里的一条：显示名、文档 URI、是否文件夹、大小、修改时间、合成路径。
 *  合成路径（/saf/…/名字）只在内存里用，给 FileAdapter 的覆盖表当 key，
 *  真实位置永远是 uri。 */
data class SafEntry(val name: String, val uri: Uri, val isDir: Boolean, val size: Long, val modified: Long, val keyPath: String)

class MainActivity : BaseActivity() {

    companion object {
        /** SAF 删除/重命名各步的日志 tag（v1.7.1：logcat 可 grep Fairumanaja 跟踪）。 */
        const val TAG = "Fairumanaja"
        const val REQ_PERMS = 1001
        const val PREF_LOCALE = "locale_tag"
        const val PREF_THEME_MODE = "theme_mode" // 0 跟随系统 / 1 浅色 / 2 深色
        // v1.9.0 外观：动态颜色开关、应用配色种子色（Int，0xAARRGGBB）。
        const val PREF_DYNAMIC_COLOR = "dynamic_color"
        // v1.9.4 开屏动画开关（默认开）：关了就直接进主界面，不播动画。
        const val PREF_SPLASH = "splash_animation"
        // v1.9.6 UI 引擎：0 Material Design 3（默认）/ 1 M3 Expressive / 2 Miuix。
        const val PREF_UI_ENGINE = "ui_engine"
        const val UI_ENGINE_MD3 = 0
        const val UI_ENGINE_EXPRESSIVE = 1
        const val UI_ENGINE_MIUIX = 2
        // v1.9.6 液态玻璃：面板半透明（默认关）；背景模糊只在 API 31+ 生效。
        const val PREF_LIQUID_GLASS = "liquid_glass"
        // v1.9.6 液态玻璃跟随主题：玻璃的色调从当前主题主色取（种子色、动态颜色、
        // 引擎主色都会反映到 colorPrimary 上），低透明度叠加。强度 0.25
        // （库文档：0.2–0.4 接近 iOS 彩色玻璃）。透明模糊模式色调淡一些（0.10），
        // 让模糊透出来。
        const val GLASS_TINT_ALPHA = 64 // 25%
        const val GLASS_TINT_ALPHA_BLUR = 26 // 10%
        // v1.9.6 液态玻璃模式：0 半透明（默认）/ 1 透明模糊 / 2 全透明。
        const val PREF_LIQUID_GLASS_MODE = "liquid_glass_mode"
        const val GLASS_MODE_TRANSLUCENT = 0
        const val GLASS_MODE_BLUR = 1
        const val GLASS_MODE_CLEAR = 2

        fun glassModeDisplayName(ctx: android.content.Context, mode: Int): String = when (mode) {
            GLASS_MODE_BLUR -> ctx.getString(R.string.liquid_glass_mode_blur)
            GLASS_MODE_CLEAR -> ctx.getString(R.string.liquid_glass_mode_clear)
            else -> ctx.getString(R.string.liquid_glass_mode_translucent)
        }

        /** 当前主题主色（不带 alpha 的 RGB）。 */
        fun themePrimaryColor(ctx: android.content.Context): Int {
            return try {
                val c = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorPrimary, Color.BLUE)
                Color.rgb(Color.red(c), Color.green(c), Color.blue(c))
            } catch (_: Exception) { Color.BLUE }
        }

        /** 液态玻璃的染色：主题主色 + 指定透明度（库的 glassTint 直接吃 ARGB）。 */
        fun glassTintColor(ctx: android.content.Context, alpha: Int = GLASS_TINT_ALPHA): Int {
            val p = themePrimaryColor(ctx)
            return Color.argb(alpha, Color.red(p), Color.green(p), Color.blue(p))
        }

        /** 把带 alpha 的染色盖到一个底色上，返回同 alpha 的染色底色
         *  （抽屉 fallback / 卡片用——只改色相，不改透明度）。 */
        fun tintSurfaceColor(base: Int, tintArgb: Int): Int {
            val a = Color.alpha(tintArgb) / 255f
            return Color.argb(Color.alpha(base),
                (Color.red(tintArgb) * a + Color.red(base) * (1f - a)).toInt(),
                (Color.green(tintArgb) * a + Color.green(base) * (1f - a)).toInt(),
                (Color.blue(tintArgb) * a + Color.blue(base) * (1f - a)).toInt())
        }
        // v1.9.1 桌面快捷方式 Intent：action + extras（目标绝对路径、动作、是否文件夹）。
        const val ACTION_SHORTCUT = "com.fairuman.filemanager.ACTION_SHORTCUT"
        const val EXTRA_SHORTCUT_PATH = "shortcut_path"
        const val EXTRA_SHORTCUT_ACTION = "shortcut_action"
        const val EXTRA_SHORTCUT_IS_DIR = "shortcut_is_dir"
        // 执行操作：0 定位所在位置，1 定位所在位置并点击，2 用文本编辑器打开，3 预览 HTML 文件。
        const val SHORTCUT_LOCATE = 0
        const val SHORTCUT_LOCATE_OPEN = 1
        const val SHORTCUT_OPEN_EDITOR = 2
        const val SHORTCUT_PREVIEW_HTML = 3
        const val PREF_SEED_COLOR = "seed_color"
        val DEFAULT_SEED = -15906911 // 0xFF0D47A1 深蓝色

        /** 字体颜色偏好（v1.9.6）：默认黑色。只改 colorOnSurface。 */
        const val PREF_FONT_COLOR = "font_color"
        val DEFAULT_FONT_COLOR = 0xFF000000.toInt()

        /** 52 个字体颜色（顺序固定）：基础 20 / 浅 16 / 深 16。 */
        val FONT_COLORS = intArrayOf(
            0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFF44336.toInt(), 0xFFE91E63.toInt(),
            0xFF9C27B0.toInt(), 0xFF673AB7.toInt(), 0xFF3F51B5.toInt(), 0xFF2196F3.toInt(),
            0xFF03A9F4.toInt(), 0xFF00BCD4.toInt(), 0xFF009688.toInt(), 0xFF4CAF50.toInt(),
            0xFF8BC34A.toInt(), 0xFFCDDC39.toInt(), 0xFFFFEB3B.toInt(), 0xFFFFC107.toInt(),
            0xFFFF9800.toInt(), 0xFFFF5722.toInt(), 0xFF795548.toInt(), 0xFF9E9E9E.toInt(),
            0xFFFFCDD2.toInt(), 0xFFF8BBD0.toInt(), 0xFFE1BEE7.toInt(), 0xFFD1C4E9.toInt(),
            0xFFC5CAE9.toInt(), 0xFFBBDEFB.toInt(), 0xFFB3E5FC.toInt(), 0xFFB2EBF2.toInt(),
            0xFFB2DFDB.toInt(), 0xFFC8E6C9.toInt(), 0xFFDCEDC8.toInt(), 0xFFF0F4C3.toInt(),
            0xFFFFF9C4.toInt(), 0xFFFFECB3.toInt(), 0xFFFFE0B2.toInt(), 0xFFE0E0E0.toInt(),
            0xFFB71C1C.toInt(), 0xFF880E4F.toInt(), 0xFF4A148C.toInt(), 0xFF311B92.toInt(),
            0xFF1A237E.toInt(), 0xFF0D47A1.toInt(), 0xFF01579B.toInt(), 0xFF006064.toInt(),
            0xFF004D40.toInt(), 0xFF1B5E20.toInt(), 0xFF33691E.toInt(), 0xFFF57F17.toInt(),
            0xFFFF6F00.toInt(), 0xFFBF360C.toInt(), 0xFF3E2723.toInt(), 0xFF212121.toInt()
        )
        // 25 种应用配色（顺序与 res/values[-night]/themes_seed.xml 的
        // ThemeOverlay.Fairu.Seed01…Seed25 一一对应；Seed19 是 #795548，
        // 纯灰 #616161 用 HCT 算出的配色与 #0097A7 完全相同，所以换掉了）。
        val SEED_COLORS = intArrayOf(
            -15906911, -5036514, -4056997, -8708190, -11457112,
            -13615201, -15108398, -16611119, -16738393, -16746133,
            -13070788, -9920712, -5262293, -415707, -28928,
            -689152, -1684967, -10665929, -8825528, -11243910,
            -9823334, -5434281, -16742021, -8604862, -13407970,
            -16728876, -16738680, -1, -16777216, -6381922,
            -476208, -1499549, -10395295, -7288071, -5908825,
            -2659, -3238952, -26624, -15064194, -14983648,
            -11652050, -16121, -769226, -1294214, -6543440,
            -12627531, -16537100, -3285959, -19712, -7508381,
            -10453621, -16750244
        )
        // applyPrefs 最近一次应用的值；onResume 拿它跟保存的设置比，不一致就重建
        // （外观页改了动态颜色/种子色后，回来不用重启 App 也能生效）。
        var lastAppliedSeed = DEFAULT_SEED
        var lastAppliedDynamic = false
        var lastAppliedFontColor = DEFAULT_FONT_COLOR

        // v1.9.2：壁纸取色的缓存（每进程算一次，壁纸中途换了要重启 App 才更新）。
        private var cachedWallpaperSeed: Int? = null
        private var wallpaperSeedComputed = false

        /** Android 12 以下动态颜色的兜底：读当前壁纸，下采样后按饱和度加权
         *  取主色，再找 25 个种子色里最接近的一个。取不到壁纸返回 null。 */
        fun wallpaperSeed(ctx: android.content.Context): Int? {
            if (wallpaperSeedComputed) return cachedWallpaperSeed
            wallpaperSeedComputed = true
            try {
                val wm = android.app.WallpaperManager.getInstance(ctx)
                val drawable = wm.drawable ?: return null
                val src = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: return null
                if (src.isRecycled) return null
                val small = android.graphics.Bitmap.createScaledBitmap(src, 32, 32, true)
                val buckets = HashMap<Int, IntArray>()
                for (y in 0 until 32) {
                    for (x in 0 until 32) {
                        val c = small.getPixel(x, y)
                        val r = android.graphics.Color.red(c)
                        val g = android.graphics.Color.green(c)
                        val b = android.graphics.Color.blue(c)
                        if (maxOf(r, g, b) - minOf(r, g, b) < 24) continue // 灰色像素不参与主色统计
                        val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
                        val a = buckets.getOrPut(key) { IntArray(4) }
                        a[0]++; a[1] += r; a[2] += g; a[3] += b
                    }
                }
                if (buckets.isEmpty()) return null
                var bestKey = -1
                var bestScore = -1.0
                for ((key, a) in buckets) {
                    val n = a[0]
                    val r = a[1] / n; val g = a[2] / n; val b = a[3] / n
                    val mx = maxOf(r, g, b); val mn = minOf(r, g, b)
                    val sat = if (mx == 0) 0.0 else (mx - mn).toDouble() / mx
                    val score = n * (0.25 + sat)
                    if (score > bestScore) { bestScore = score; bestKey = key }
                }
                val a = buckets[bestKey]!!
                val n = a[0]
                val dom = android.graphics.Color.rgb(a[1] / n, a[2] / n, a[3] / n)
                var best: Int? = null
                var bestDist = Int.MAX_VALUE
                for (s in SEED_COLORS) {
                    val dr = android.graphics.Color.red(s) - android.graphics.Color.red(dom)
                    val dg = android.graphics.Color.green(s) - android.graphics.Color.green(dom)
                    val db = android.graphics.Color.blue(s) - android.graphics.Color.blue(dom)
                    val d = dr * dr * 3 + dg * dg * 6 + db * db
                    if (d < bestDist) { bestDist = d; best = s }
                }
                cachedWallpaperSeed = best
                return best
            } catch (_: Exception) {
                return null
            }
        }

        /** 种子色 → 主题覆盖层 style（res/values[-night]/themes_seed.xml）。 */
        fun seedStyleRes(seed: Int): Int {
            val idx = SEED_COLORS.indexOf(seed)
            if (idx < 0) return 0
            return when (idx) {
                0 -> R.style.ThemeOverlay_Fairu_Seed01
                1 -> R.style.ThemeOverlay_Fairu_Seed02
                2 -> R.style.ThemeOverlay_Fairu_Seed03
                3 -> R.style.ThemeOverlay_Fairu_Seed04
                4 -> R.style.ThemeOverlay_Fairu_Seed05
                5 -> R.style.ThemeOverlay_Fairu_Seed06
                6 -> R.style.ThemeOverlay_Fairu_Seed07
                7 -> R.style.ThemeOverlay_Fairu_Seed08
                8 -> R.style.ThemeOverlay_Fairu_Seed09
                9 -> R.style.ThemeOverlay_Fairu_Seed10
                10 -> R.style.ThemeOverlay_Fairu_Seed11
                11 -> R.style.ThemeOverlay_Fairu_Seed12
                12 -> R.style.ThemeOverlay_Fairu_Seed13
                13 -> R.style.ThemeOverlay_Fairu_Seed14
                14 -> R.style.ThemeOverlay_Fairu_Seed15
                15 -> R.style.ThemeOverlay_Fairu_Seed16
                16 -> R.style.ThemeOverlay_Fairu_Seed17
                17 -> R.style.ThemeOverlay_Fairu_Seed18
                18 -> R.style.ThemeOverlay_Fairu_Seed19
                19 -> R.style.ThemeOverlay_Fairu_Seed20
                20 -> R.style.ThemeOverlay_Fairu_Seed21
                21 -> R.style.ThemeOverlay_Fairu_Seed22
                22 -> R.style.ThemeOverlay_Fairu_Seed23
                23 -> R.style.ThemeOverlay_Fairu_Seed24
                24 -> R.style.ThemeOverlay_Fairu_Seed25
                25 -> R.style.ThemeOverlay_Fairu_Seed26
                26 -> R.style.ThemeOverlay_Fairu_Seed27
                27 -> R.style.ThemeOverlay_Fairu_Seed28
                28 -> R.style.ThemeOverlay_Fairu_Seed29
                29 -> R.style.ThemeOverlay_Fairu_Seed30
                30 -> R.style.ThemeOverlay_Fairu_Seed31
                31 -> R.style.ThemeOverlay_Fairu_Seed32
                32 -> R.style.ThemeOverlay_Fairu_Seed33
                33 -> R.style.ThemeOverlay_Fairu_Seed34
                34 -> R.style.ThemeOverlay_Fairu_Seed35
                35 -> R.style.ThemeOverlay_Fairu_Seed36
                36 -> R.style.ThemeOverlay_Fairu_Seed37
                37 -> R.style.ThemeOverlay_Fairu_Seed38
                38 -> R.style.ThemeOverlay_Fairu_Seed39
                39 -> R.style.ThemeOverlay_Fairu_Seed40
                40 -> R.style.ThemeOverlay_Fairu_Seed41
                41 -> R.style.ThemeOverlay_Fairu_Seed42
                42 -> R.style.ThemeOverlay_Fairu_Seed43
                43 -> R.style.ThemeOverlay_Fairu_Seed44
                44 -> R.style.ThemeOverlay_Fairu_Seed45
                45 -> R.style.ThemeOverlay_Fairu_Seed46
                46 -> R.style.ThemeOverlay_Fairu_Seed47
                47 -> R.style.ThemeOverlay_Fairu_Seed48
                48 -> R.style.ThemeOverlay_Fairu_Seed49
                49 -> R.style.ThemeOverlay_Fairu_Seed50
                50 -> R.style.ThemeOverlay_Fairu_Seed51
                51 -> R.style.ThemeOverlay_Fairu_Seed52
                else -> R.style.ThemeOverlay_Fairu_Seed52
            }
        }


        /** 字体颜色 → 主题覆盖层 style（res/values/themes_font.xml）。
         *  每个颜色只改 colorOnSurface；找不到返回黑色 Font01。 */
        fun fontStyleRes(fontColor: Int): Int {
            val idx = FONT_COLORS.indexOf(fontColor)
            if (idx < 0) return R.style.ThemeOverlay_Fairu_Font01
            return when (idx) {
                0 -> R.style.ThemeOverlay_Fairu_Font01
                1 -> R.style.ThemeOverlay_Fairu_Font02
                2 -> R.style.ThemeOverlay_Fairu_Font03
                3 -> R.style.ThemeOverlay_Fairu_Font04
                4 -> R.style.ThemeOverlay_Fairu_Font05
                5 -> R.style.ThemeOverlay_Fairu_Font06
                6 -> R.style.ThemeOverlay_Fairu_Font07
                7 -> R.style.ThemeOverlay_Fairu_Font08
                8 -> R.style.ThemeOverlay_Fairu_Font09
                9 -> R.style.ThemeOverlay_Fairu_Font10
                10 -> R.style.ThemeOverlay_Fairu_Font11
                11 -> R.style.ThemeOverlay_Fairu_Font12
                12 -> R.style.ThemeOverlay_Fairu_Font13
                13 -> R.style.ThemeOverlay_Fairu_Font14
                14 -> R.style.ThemeOverlay_Fairu_Font15
                15 -> R.style.ThemeOverlay_Fairu_Font16
                16 -> R.style.ThemeOverlay_Fairu_Font17
                17 -> R.style.ThemeOverlay_Fairu_Font18
                18 -> R.style.ThemeOverlay_Fairu_Font19
                19 -> R.style.ThemeOverlay_Fairu_Font20
                20 -> R.style.ThemeOverlay_Fairu_Font21
                21 -> R.style.ThemeOverlay_Fairu_Font22
                22 -> R.style.ThemeOverlay_Fairu_Font23
                23 -> R.style.ThemeOverlay_Fairu_Font24
                24 -> R.style.ThemeOverlay_Fairu_Font25
                25 -> R.style.ThemeOverlay_Fairu_Font26
                26 -> R.style.ThemeOverlay_Fairu_Font27
                27 -> R.style.ThemeOverlay_Fairu_Font28
                28 -> R.style.ThemeOverlay_Fairu_Font29
                29 -> R.style.ThemeOverlay_Fairu_Font30
                30 -> R.style.ThemeOverlay_Fairu_Font31
                31 -> R.style.ThemeOverlay_Fairu_Font32
                32 -> R.style.ThemeOverlay_Fairu_Font33
                33 -> R.style.ThemeOverlay_Fairu_Font34
                34 -> R.style.ThemeOverlay_Fairu_Font35
                35 -> R.style.ThemeOverlay_Fairu_Font36
                36 -> R.style.ThemeOverlay_Fairu_Font37
                37 -> R.style.ThemeOverlay_Fairu_Font38
                38 -> R.style.ThemeOverlay_Fairu_Font39
                39 -> R.style.ThemeOverlay_Fairu_Font40
                40 -> R.style.ThemeOverlay_Fairu_Font41
                41 -> R.style.ThemeOverlay_Fairu_Font42
                42 -> R.style.ThemeOverlay_Fairu_Font43
                43 -> R.style.ThemeOverlay_Fairu_Font44
                44 -> R.style.ThemeOverlay_Fairu_Font45
                45 -> R.style.ThemeOverlay_Fairu_Font46
                46 -> R.style.ThemeOverlay_Fairu_Font47
                47 -> R.style.ThemeOverlay_Fairu_Font48
                48 -> R.style.ThemeOverlay_Fairu_Font49
                49 -> R.style.ThemeOverlay_Fairu_Font50
                50 -> R.style.ThemeOverlay_Fairu_Font51
                51 -> R.style.ThemeOverlay_Fairu_Font52
                else -> R.style.ThemeOverlay_Fairu_Font01
            }
        }

        /** UI 引擎 → 主题覆盖层 style（res/values[-night]/themes_engine.xml）。
         *  MD3（默认）返回 0，不应用任何覆盖层，现有行为一个字不改。 */
        fun engineStyleRes(engine: Int): Int = when (engine) {
            UI_ENGINE_EXPRESSIVE -> R.style.ThemeOverlay_Fairu_Expressive
            UI_ENGINE_MIUIX -> R.style.ThemeOverlay_Fairu_Miuix
            else -> 0
        }

        /** UI 引擎的显示名（设置页副标题、实验性功能页副标题共用）。 */
        fun engineDisplayName(ctx: android.content.Context, engine: Int): String = when (engine) {
            UI_ENGINE_EXPRESSIVE -> "M3 Expressive"
            UI_ENGINE_MIUIX -> "Miuix"
            else -> "Material Design 3"
        }

        /** 液态玻璃覆盖层（res/values[-night]/themes_engine.xml）。
         *  HAZE 是 Jetpack Compose 专用库，本项目是 View 体系 + 命令行直编、
         *  没有 Compose 编译器插件，接不进来；这里用系统原生办法做同样效果：
         *  半透明背景 + API 31+ 窗口背景模糊（API 29 只有半透明、无模糊）。 */
        fun liquidGlassStyleRes(): Int = R.style.ThemeOverlay_Fairu_LiquidGlass

        /** 所有 Activity 启动前统一应用：语言 + 主题模式 + 动态颜色/种子色
         *  （要在 super.onCreate 之前调）。动态颜色开着时优先于种子色；
         *  种子色用 getTheme().applyStyle 合并覆盖层（浅/深各一套，按夜间模式自动取）。 */
        fun applyPrefs(ctx: android.content.Context) {
            try {
                val sp = ctx.getSharedPreferences("fairu_prefs", MODE_PRIVATE)
                val tag = sp.getString(PREF_LOCALE, null)
                if (!tag.isNullOrBlank()) {
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.create(Locale.forLanguageTag(tag))
                    )
                }
                val mode = when (sp.getInt(PREF_THEME_MODE, 0)) {
                    1 -> AppCompatDelegate.MODE_NIGHT_NO
                    2 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                AppCompatDelegate.setDefaultNightMode(mode)
                val dynamic = sp.getBoolean(PREF_DYNAMIC_COLOR, false)
                val seed = sp.getInt(PREF_SEED_COLOR, DEFAULT_SEED)
                lastAppliedDynamic = dynamic
                lastAppliedSeed = seed
                val act = ctx as? Activity ?: return
                if (dynamic) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        try { DynamicColors.applyToActivityIfAvailable(act) } catch (_: Exception) { }
                    } else {
                        // v1.9.2：Android 12 以下没有系统动态取色——从当前壁纸提主色，
                        // 用最接近的种子色覆盖层兜底（版本限制已取消，任何版本都能开）。
                        val wSeed = wallpaperSeed(ctx) ?: seed
                        val style = seedStyleRes(wSeed)
                        if (style != 0) {
                            try { act.theme.applyStyle(style, true) } catch (_: Exception) { }
                        }
                    }
                } else {
                    val style = seedStyleRes(seed)
                    if (style != 0) {
                        try { act.theme.applyStyle(style, true) } catch (_: Exception) { }
                    }
                }
                // v1.9.6 UI 引擎：最后应用，Expressive / Miuix 的配色与形状
                // 盖掉种子色（引擎换的是整个 UI）；MD3 不应用，保持原样。
                val engine = sp.getInt(PREF_UI_ENGINE, UI_ENGINE_MD3)
                val engineStyle = engineStyleRes(engine)
                if (engineStyle != 0) {
                    try { act.theme.applyStyle(engineStyle, true) } catch (_: Exception) { }
                }
                // v1.9.6 液态玻璃：再盖一层半透明（只改颜色，不改引擎的形状）。
                if (sp.getBoolean(PREF_LIQUID_GLASS, false)) {
                    try { act.theme.applyStyle(liquidGlassStyleRes(), true) } catch (_: Exception) { }
                }
                // v1.9.6 字体颜色：最后应用，只改 colorOnSurface，盖掉种子色/
                // 引擎的同名角色（字体颜色是用户点的，要赢）。
                val fontColor = sp.getInt(PREF_FONT_COLOR, DEFAULT_FONT_COLOR)
                lastAppliedFontColor = fontColor
                val fontStyle = fontStyleRes(fontColor)
                try { act.theme.applyStyle(fontStyle, true) } catch (_: Exception) { }
                // v1.9.7 真满文/锡伯文：语言是 mnc/sjo 时，再盖一层字体覆盖层，
                // 全页字体换成随包的 Noto Sans Mongolian（见 themes_font.xml）。
                // 盖在字体颜色之后，字体颜色只改 colorOnSurface，两者不冲突。
                val localeTag = sp.getString(PREF_LOCALE, null)
                if (localeTag == "mnc" || localeTag == "sjo") {
                    try { act.theme.applyStyle(R.style.ThemeOverlay_Fairu_Mongolian, true) } catch (_: Exception) { }
                }
            } catch (_: Exception) { }
        }

        /** SAF 写操作的后台执行器：守护线程，提供方调用卡住时不拖住进程。 */
        val safExecutor = Executors.newCachedThreadPool { r -> Thread(r, "saf-op").apply { isDaemon = true } }
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var topBar: MaterialToolbar
    // v1.8.0 多选模式顶栏（照 Max 的参考图）：平时隐藏，选中后顶替掉 topBar。
    private lateinit var selectionTopBar: LinearLayout
    private lateinit var selTitle: TextView
    private lateinit var selSubtitle: TextView
    private lateinit var paneLeftRoot: View
    private lateinit var paneRightRoot: View
    private lateinit var paneDivider: View
    private lateinit var btnSwap: MaterialButton

    private lateinit var left: Pane
    private lateinit var right: Pane
    private var active = 0 // 0 = 左窗格，1 = 右窗格
    private var dualPane = true

    /** 剪贴板里的一项：普通 File，或 SAF 文档（URI + 名字 + 是否文件夹），
     *  或根目录（Shizuku）里的路径（isRoot=true，file 是真实路径但 App
     *  自己读不了，操作要走 Shizuku shell）。
     *  三者可以混装，跨窗格粘贴都走得通：File→SAF、SAF→File、SAF→SAF
     *  都用 ContentResolver 流式复制，剪切是复制成功后删源。 */
    // 剪贴板：一项可能是 File，也可能是 SAF 文档（safUri）。SAF 项多记它所在的
    // treeUri + 父目录 docId，剪切删源时能按名重取新鲜 URI（docId 会过期，见 safDeleteByName）。
    data class ClipEntry(val file: File?, val safUri: Uri?, val name: String, val isDir: Boolean,
                         val safTreeUri: Uri? = null, val safParentDocId: String? = null,
                         val isRoot: Boolean = false) {
        companion object {
            fun ofFile(f: File) = ClipEntry(f, null, f.name, f.isDirectory)
            fun ofRoot(f: File) = ClipEntry(f, null, f.name, f.isDirectory, isRoot = true)
            fun ofSaf(e: SafEntry, treeUri: Uri? = null, parentDocId: String? = null) =
                ClipEntry(null, e.uri, e.name, e.isDir, treeUri, parentDocId)
        }
    }

    private var clipboard: List<ClipEntry> = emptyList()
    private var clipboardCut = false

    // v1.9.6 液态玻璃（QWEA0/Liquid-Glass-Android v2.0.11）：库的原生 .so 只有
    // arm64-v8a/armeabi-v7a；x86 设备（比如 32 位 x86 模拟器）上 loadLibrary
    // 会失败。在这里先探一次：成功走库默认（SMART + 色差 AUTO，API 33+ 再
    // 走 AGSL 透镜管线）；失败走纯 Kotlin 管线（BOX_BLUR + 色差 KOTLIN），
    // 绝不碰任何 native 方法——否则库的 SMART 默认会在第一帧抛
    // ExceptionInInitializerError 闪退（loadLibrary 的 UnsatisfiedLinkError
    // 是 Error，库里的 catch (e: Exception) 接不住）。
    val NATIVE_GLASS_OK: Boolean by lazy {
        try { System.loadLibrary("nativegauss"); true } catch (t: Throwable) { false }
    }

    /** v1.8.0：根目录文本文件用内置编辑器打开时，先经 Shizuku cp 到缓存里
     *  暂存，编辑器改的是暂存文件；编辑器退出后暂存文件变了就 cp 回原路径。
     *  （编辑器只认普通 File 路径，根目录文件 App 自己写不进去。） */
    private data class RootEdit(val targetPath: String, val staged: File, val lastModified: Long, val length: Long)
    private var pendingRootEdit: RootEdit? = null
    private val rootEditorLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val pending = pendingRootEdit ?: return@registerForActivityResult
        pendingRootEdit = null
        Thread {
            val staged = pending.staged
            val changed = staged.exists() &&
                (staged.lastModified() != pending.lastModified || staged.length() != pending.length)
            if (changed) {
                val r = ShizukuRoot.exec("cp", staged.absolutePath, pending.targetPath)
                Log.d(TAG, "root edit write-back ${pending.targetPath}: exit=${r.exit} ${r.err.trim().take(120)}")
                runOnUiThread {
                    if (r.exit == 0) Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
                    else Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show()
                }
            }
            // 暂存文件是 shell 建的、放在 shell 的目录里，App 自己删不掉，走 shell rm。
            try { ShizukuRoot.exec("rm", "-rf", staged.absolutePath) } catch (_: Exception) { }
        }.start()
    }

    inner class Pane(val index: Int) {
        var currentDir: File = Environment.getExternalStorageDirectory()
        val backStack = mutableListOf<File>()
        val forwardStack = mutableListOf<File>()
        var files: List<File> = emptyList()
        val selected = mutableSetOf<File>()
        var searchQuery = ""
        // Shizuku 模式：当前窗格在浏览根目录（File 读不到，用 ls -la 的解析结果）。
        var shizukuMode = false
        var shizukuEntries: List<ShizukuRoot.Entry> = emptyList()
        // v1.8.0：根目录子目录读失败时记下错误原因，空列表里直接显示给用户看，
        // 不再留着旧列表（旧列表配新路径会误导人），也不再只显示“这里是空的”。
        var shizukuError: String? = null
        // SAF 模式：当前窗格在浏览一个 File 读不到的已添加储存（比如别家 App
        // 的目录，由 DocumentsProvider 暴露），用 DocumentsContract 列目录。
        var safMode = false
        var safStorage: CustomStorages.Storage? = null
        // SAF 浏览栈：一串（文档 ID，显示名），栈顶是当前目录。
        var safStack = mutableListOf<SafDir>()
        // 后退 / 前进栈：每次存一份 safStack 的快照。
        var safBackStack = mutableListOf<List<SafDir>>()
        var safForwardStack = mutableListOf<List<SafDir>>()
        var safEntries: List<SafEntry> = emptyList()
        // 合成路径（/saf/…）→ 真实文档 URI，点行时用它进目录 / 打开文件。
        var safUriByPath = mapOf<String, Uri>()
        lateinit var root: View
        lateinit var pathView: TextView
        lateinit var recycler: RecyclerView
        lateinit var emptyView: TextView
        lateinit var adapter: FileAdapter
    }

    private fun panes(): List<Pane> = listOf(left, right)
    private fun activePane(): Pane = if (active == 0) left else right
    private fun otherPane(): Pane = if (active == 0) right else left

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashHandler.install(this)
        // 语言/主题/配色由 BaseActivity.onCreate 在 super.onCreate 之前统一应用。
        // super.onCreate 必须无条件最先调用（v1.2 的教训：包在 try 里会跳过它）。
        super.onCreate(savedInstanceState)
        try {
            onCreateInner(savedInstanceState)
        } catch (e: Throwable) {
            CrashHandler.writeLog(this, e)
            try {
                MaterialAlertDialogBuilder(this)
                    .setTitle("启动失败")
                    .setMessage("启动时出错了，错误已保存到 crash.log。\n\n" + CrashHandler.logPathText(this) + "\n\n错误：" + e.toString())
                    .setPositiveButton(R.string.ok) { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
            } catch (_: Exception) { }
            try { finish() } catch (_: Exception) { }
        }
    }

    /** 已并入 companion.applyPrefs（语言 + 主题模式统一入口）。 */
    private fun applySavedLocale() = applyPrefs(this)

    private fun onCreateInner(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("fairu_prefs", MODE_PRIVATE)
        dualPane = prefs.getBoolean("dual_pane", true)

        topBar = findViewById(R.id.topAppBar)
        setSupportActionBar(topBar)
        // 注意：不要再调 topBar.setOnMenuItemClickListener——它会盖掉
        // ToolbarActionBar 转发到 onOptionsItemSelected 的监听，导致双分发；
        // 菜单统一走下面的 onCreateOptionsMenu + onOptionsItemSelected。

        // 左上角抽屉菜单（MT 式）：汉堡按钮滑出抽屉。
        drawerLayout = findViewById(R.id.drawerLayout)
        topBar.setNavigationIcon(R.drawable.ic_menu)
        topBar.setNavigationOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        refreshDrawerTexts()
        setupLiquidGlass()
        findViewById<View>(R.id.drawerRootRow).setOnClickListener {
            openRootViaShizuku()
        }
        findViewById<ImageButton>(R.id.drawerOverflow).setOnClickListener {
            showDrawerOverflowMenu(it)
        }
        refreshCustomStorages()
        refreshBookmarks()
        findViewById<View>(R.id.drawerInternalRow).setOnClickListener {
            navigateDrawerTo(Environment.getExternalStorageDirectory())
        }
        findViewById<View>(R.id.drawerTrashRow).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, TrashActivity::class.java))
        }
        findViewById<View>(R.id.drawerEditorRow).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, TextEditorActivity::class.java))
        }
        findViewById<View>(R.id.drawerSettingsRow).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // v1.8.0 多选模式顶栏（照 Max 的参考图）：✕ 退出多选，分享 / 删除 /
        // ⋮ 走多选菜单。底部的选择操作条删了，不再两套并存。
        selectionTopBar = findViewById(R.id.selectionTopBar)
        selTitle = findViewById(R.id.selTitle)
        selSubtitle = findViewById(R.id.selSubtitle)
        findViewById<ImageButton>(R.id.btnSelClose).setOnClickListener { clearSelection(activePane()) }
        findViewById<ImageButton>(R.id.btnSelShare).setOnClickListener { shareFiles(activePane(), activePane().selected.toList()) }
        findViewById<ImageButton>(R.id.btnSelDelete).setOnClickListener { confirmDelete(activePane().selected.toList()) }
        findViewById<ImageButton>(R.id.btnSelMore).setOnClickListener { showSelectionMenu(it) }
        paneLeftRoot = findViewById(R.id.paneLeft)
        paneRightRoot = findViewById(R.id.paneRight)
        paneDivider = findViewById(R.id.paneDivider)

        left = Pane(0).also { setupPane(it, paneLeftRoot) }
        right = Pane(1).also { setupPane(it, paneRightRoot) }

        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { goBack() }
        findViewById<MaterialButton>(R.id.btnForward).setOnClickListener { goForward() }
        findViewById<MaterialButton>(R.id.btnNew).setOnClickListener { showNewDialog() }
        btnSwap = findViewById(R.id.btnSwap)
        btnSwap.setOnClickListener { swapPanes() }
        findViewById<MaterialButton>(R.id.btnUp).setOnClickListener { navigateUp(activePane()) }

        // btnSwap 要先初始化，applyDualPane 里才会用到它。
        applyDualPane()

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::drawerLayout.isInitialized && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    return
                }
                val p = activePane()
                if (p.selected.isNotEmpty()) { clearSelection(p); return }
                if (goBack()) return
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })

        // setupSearch() 不在这里调：菜单还没 inflate，topBar.menu 是空的；
        // 它在 onCreateOptionsMenu 里 inflate 之后调。
        checkPermissions()
        load()
        // v1.9.1：桌面快捷方式点进来时按动作定位/打开。
        handleShortcutIntent(intent)
    }

    private fun setupPane(pane: Pane, root: View) {
        pane.root = root
        pane.pathView = root.findViewById(R.id.panePath)
        pane.recycler = root.findViewById(R.id.recyclerView)
        pane.emptyView = root.findViewById(R.id.emptyView)
        pane.recycler.layoutManager = LinearLayoutManager(this)
        pane.adapter = FileAdapter(
            onClick = { f -> setActive(pane); onFileClick(pane, f) },
            onLongClick = { f -> setActive(pane); toggleSelect(pane, f); true },
            onParentClick = { setActive(pane); navigateUp(pane) },
            // v1.8.0：每行末尾的 ⋮，点它弹出该项的操作菜单。
            onMoreClick = { f, v -> setActive(pane); showFileMenu(pane, f, v) }
        )
        pane.adapter.selected = pane.selected
        pane.recycler.adapter = pane.adapter
        // 点窗格任意处即激活该窗格。
        root.setOnClickListener { setActive(pane) }
    }

    private fun setActive(pane: Pane) {
        if (active != pane.index) {
            active = pane.index
            updateTopBar()
            updateSelectionTopBar()
            highlightActivePane()
        }
    }

    private fun highlightActivePane() {
        if (!::left.isInitialized || !::right.isInitialized) return
        val activeBg = getColor(R.color.m3_primary_container)
        val idleBg = getColor(R.color.m3_surface_container_high)
        left.pathView.setBackgroundColor(if (active == 0) activeBg else idleBg)
        right.pathView.setBackgroundColor(if (active == 1) activeBg else idleBg)
    }

    private fun applyDualPane() {
        if (!::left.isInitialized || !::right.isInitialized) return
        if (::btnSwap.isInitialized) {
            btnSwap.visibility = if (dualPane) View.VISIBLE else View.GONE
        }
        if (dualPane) {
            paneRightRoot.visibility = View.VISIBLE
            paneDivider.visibility = View.VISIBLE
        } else {
            paneRightRoot.visibility = View.GONE
            paneDivider.visibility = View.GONE
            active = 0
        }
        topBar.menu.findItem(R.id.menu_dual_pane)?.isChecked = dualPane
        load()
    }

    // ---------- 抽屉菜单 ----------

    /** 点抽屉里的目录项：切换当前活动窗格的目录并关抽屉；进不去就友好提示，绝不闪退。
     *  从 Shizuku 模式切回普通目录时先退出 Shizuku 模式。 */
    private fun navigateDrawerTo(dir: File) {
        drawerLayout.closeDrawer(GravityCompat.START)
        if (dir.listFiles() == null) {
            Toast.makeText(this, R.string.cannot_access, Toast.LENGTH_SHORT).show()
            return
        }
        exitSafMode(activePane())
        exitShizukuMode(activePane())
        navigateTo(activePane(), dir)
    }

    /** 点抽屉里一个已添加的储存：映射路径能直接读就走原来的 File 方式；
     *  映射不到、或 File 读不了（比如别家 App 的目录），就走 SAF 方式。
     *  注意 File.listFiles() 返回 null（读不了）和空数组（空文件夹）是两回事，
     *  只有 null 才进 SAF。 */
    private fun navigateDrawerToStorage(s: CustomStorages.Storage) {
        drawerLayout.closeDrawer(GravityCompat.START)
        if (s.path.isNotBlank()) {
            val f = File(s.path)
            if (f.listFiles() != null) {
                exitSafMode(activePane())
                exitShizukuMode(activePane())
                navigateTo(activePane(), f)
                return
            }
        }
        if (s.uri.isNotBlank()) {
            enterSafRoot(s)
            return
        }
        Toast.makeText(this, R.string.cannot_access, Toast.LENGTH_SHORT).show()
    }

    /** 抽屉里两组储存用量条：已用 / 可用 / 百分比（StatFs）。 */
    private fun refreshDrawerInfo() {
        try {
            refreshOneDrawerStorage(File("/"), R.id.drawerRootBar, R.id.drawerRootInfo)
            refreshOneDrawerStorage(
                Environment.getExternalStorageDirectory(),
                R.id.drawerInternalBar, R.id.drawerInternalInfo
            )
        } catch (_: Exception) { }
    }

    private fun refreshOneDrawerStorage(dir: File, barId: Int, infoId: Int) {
        val bar = findViewById<ProgressBar>(barId) ?: return
        val info = findViewById<TextView>(infoId) ?: return
        val (total, free) = FileUtils.storageInfo(dir)
        if (total <= 0) {
            bar.progress = 0
            info.text = getString(R.string.cannot_access)
            return
        }
        val used = total - free
        val pct = ((used * 100) / total).toInt()
        bar.progress = pct
        info.text = getString(R.string.used_space) + " " + FileUtils.formatSize(used) +
            "  ·  " + getString(R.string.free_space) + " " + FileUtils.formatSize(free) +
            "  ·  $pct%"
    }

    // ---------- 浏览 / 搜索 / 排序 ----------

    private fun load() {
        if (!::left.isInitialized || !::right.isInitialized) return
        loadPane(left)
        if (dualPane) loadPane(right)
        updateTopBar()
        updateSelectionTopBar()
        highlightActivePane()
        refreshDrawerInfo()
    }

    private fun loadPane(pane: Pane) {
        if (!::left.isInitialized) return
        // v1.9.0：置顶的文件夹排最前面（Pin folder），行末尾显示图钉标记。
        pane.adapter.pinnedKeys = PinnedFolders.pinnedKeys(this)
        if (pane.safMode) {
            // SAF 模式：File 读不到这目录，用 DocumentsContract 读到的条目拼
            // File 列表（合成路径只当 key）；目录/大小/日期由 adapter 的
            // override 提供，跟 Shizuku 模式同一套做法。
            var entries = pane.safEntries
            if (pane.searchQuery.isNotBlank()) {
                val q = pane.searchQuery.lowercase()
                entries = entries.filter { it.name.lowercase().contains(q) }
            }
            pane.files = applyPins(entries.map { File(it.keyPath) })
            pane.pathView.text = safBreadcrumb(pane)
            pane.adapter.isDirOverride = entries.associate { it.keyPath to it.isDir }
            pane.adapter.sizeOverride = entries.associate { it.keyPath to it.size }
            pane.adapter.dateOverride = entries.associate { it.keyPath to it.modified }
            pane.adapter.dirShowsFolderLabel = true
            pane.adapter.update(pane.files, pane.safStack.size > 1)
            pane.emptyView.text = getString(R.string.no_files)
            pane.emptyView.visibility = if (pane.files.isEmpty() && pane.safStack.size <= 1) View.VISIBLE else View.GONE
            return
        }
        if (pane.shizukuMode) {
            // Shizuku 模式：用上次 ls -la 读到的条目拼 File 列表；
            // 目录/大小/日期由 adapter 的 override 提供（File 本身读不到）。
            var entries = pane.shizukuEntries
            if (pane.searchQuery.isNotBlank()) {
                val q = pane.searchQuery.lowercase()
                entries = entries.filter { it.name.lowercase().contains(q) }
            }
            pane.files = applyPins(entries.map { File(it.path) })
            pane.pathView.text = pane.currentDir.absolutePath
            pane.adapter.isDirOverride = entries.associate { it.path to it.isDir }
            pane.adapter.sizeOverride = entries.associate { it.path to it.size }
            pane.adapter.dateOverride = entries.associate { it.path to it.modified }
            pane.adapter.dirShowsFolderLabel = true
            pane.adapter.update(pane.files, pane.currentDir.parentFile != null)
            // v1.8.0：读失败时空列表里直接显示错误原因（refreshShizukuList
            // 失败时把 shizukuError 填上），不再只显示“这里是空的”。
            pane.emptyView.text = pane.shizukuError ?: getString(R.string.no_files)
            pane.emptyView.visibility = if (pane.files.isEmpty()) View.VISIBLE else View.GONE
            return
        }
        pane.adapter.isDirOverride = emptyMap()
        pane.adapter.sizeOverride = emptyMap()
        pane.adapter.dateOverride = emptyMap()
        pane.adapter.dirShowsFolderLabel = false
        val showHidden = prefs.getBoolean("show_hidden", false)
        val sort = prefs.getInt("sort", 0)
        var list: List<File> = pane.currentDir.listFiles()?.toList() ?: emptyList()
        if (!showHidden) list = list.filter { !it.name.startsWith(".") }
        if (pane.searchQuery.isNotBlank()) {
            val q = pane.searchQuery.lowercase()
            list = searchRecursive(pane.currentDir, q)
        }
        list = when (sort) {
            1 -> list.sortedByDescending { it.lastModified() }
            2 -> list.sortedByDescending { if (it.isDirectory) 0L else it.length() }
            else -> list.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        }
        list = applyPins(list)
        pane.files = list
        pane.pathView.text = pane.currentDir.absolutePath
        val showParent = pane.currentDir.parentFile != null
        pane.adapter.update(list, showParent)
        pane.emptyView.text = getString(R.string.no_files)
        pane.emptyView.visibility = if (list.isEmpty() && !showParent) View.VISIBLE else View.GONE
    }

    /** v1.9.0 Pin folder：已置顶的文件夹排到列表最前面（其余顺序不变）。 */
    private fun applyPins(list: List<File>): List<File> {
        val pins = PinnedFolders.pinnedKeys(this)
        if (pins.isEmpty()) return list
        val (pinned, rest) = list.partition { pins.contains(it.absolutePath) }
        return pinned + rest
    }

    private fun searchRecursive(dir: File, q: String): List<File> {
        val out = mutableListOf<File>()
        val children = dir.listFiles() ?: return out
        for (c in children) {
            if (c.name.lowercase().contains(q)) out.add(c)
            if (c.isDirectory && !c.name.startsWith(".")) {
                if (out.size < 500) out.addAll(searchRecursive(c, q))
            }
        }
        return out
    }

    private fun updateTopBar() {
        if (!::left.isInitialized) return
        val p = activePane()
        if (p.safMode) {
            // SAF 模式：标题是面包屑（储存名/子目录），计数用 SAF 条目。
            topBar.title = safBreadcrumb(p)
            val dirs = p.safEntries.count { it.isDir }
            val count = p.safEntries.count { !it.isDir }
            val root = Environment.getExternalStorageDirectory()
            val (total, free) = FileUtils.storageInfo(root)
            topBar.subtitle = getString(
                R.string.pane_stats, dirs, count,
                FileUtils.formatSize(total - free), FileUtils.formatSize(total)
            )
            topBar.menu.findItem(R.id.menu_show_hidden)?.isChecked = prefs.getBoolean("show_hidden", false)
            return
        }
        topBar.title = p.currentDir.absolutePath
        // Shizuku 模式下 File.isDirectory 读不到，用 ls 的解析结果计数。
        val dirs = if (p.shizukuMode) p.shizukuEntries.count { it.isDir } else p.files.count { it.isDirectory }
        val count = if (p.shizukuMode) p.shizukuEntries.count { !it.isDir } else p.files.count { it.isFile }
        val root = Environment.getExternalStorageDirectory()
        val (total, free) = FileUtils.storageInfo(root)
        topBar.subtitle = getString(
            R.string.pane_stats, dirs, count,
            FileUtils.formatSize(total - free), FileUtils.formatSize(total)
        )
        topBar.menu.findItem(R.id.menu_show_hidden)?.isChecked = prefs.getBoolean("show_hidden", false)
    }

    private fun navigateTo(pane: Pane, dir: File) {
        if (dir == pane.currentDir && !pane.shizukuMode) return
        if (pane.safMode) return // SAF 模式里进目录走 enterSafDir，不走这儿。
        if (pane.shizukuMode) {
            // Shizuku 模式内继续用 Shizuku 列目录（File.listFiles 读不到）。
            enterShizukuDir(pane, dir.absolutePath)
            return
        }
        // 列不出文件的目录（无权限 / 不存在，如非 root 手机上的根目录）不进去，只提示。
        if (dir.listFiles() == null) {
            Toast.makeText(this, R.string.cannot_access, Toast.LENGTH_SHORT).show()
            return
        }
        pane.backStack.add(pane.currentDir)
        pane.forwardStack.clear()
        pane.currentDir = dir
        pane.searchQuery = ""
        loadPane(pane)
        updateTopBar()
    }

    private fun navigateUp(pane: Pane) {
        if (pane.safMode) {
            // SAF 模式：沿浏览栈往上走一层（同时压入后退栈，< 能回来）。
            if (pane.safStack.size <= 1) return
            pane.safBackStack.add(pane.safStack.toList())
            pane.safForwardStack.clear()
            pane.safStack.removeAt(pane.safStack.lastIndex)
            pane.currentDir = File(safKeyPath(pane))
            pane.searchQuery = ""
            refreshSafList(pane)
            updateTopBar()
            return
        }
        if (pane.shizukuMode) {
            // Shizuku 模式下 File.exists() 对受限目录返回 false，不能用它判断，
            // 直接按路径字符串往上走。
            val parent = pane.currentDir.parent ?: return
            navigateTo(pane, File(parent))
            return
        }
        val parent = pane.currentDir.parentFile
        if (parent != null && parent.exists()) navigateTo(pane, parent)
    }

    private fun goBack(): Boolean {
        val p = activePane()
        if (p.safMode) {
            if (p.safBackStack.isEmpty()) return false
            p.safForwardStack.add(p.safStack.toList())
            p.safStack = p.safBackStack.removeAt(p.safBackStack.lastIndex).toMutableList()
            p.currentDir = File(safKeyPath(p))
            p.searchQuery = ""
            refreshSafList(p)
            updateTopBar()
            return true
        }
        if (p.backStack.isEmpty()) return false
        p.forwardStack.add(p.currentDir)
        p.currentDir = p.backStack.removeAt(p.backStack.lastIndex)
        p.searchQuery = ""
        if (p.shizukuMode) refreshShizukuList(p) else loadPane(p)
        updateTopBar()
        return true
    }

    private fun goForward(): Boolean {
        val p = activePane()
        if (p.safMode) {
            if (p.safForwardStack.isEmpty()) return false
            p.safBackStack.add(p.safStack.toList())
            p.safStack = p.safForwardStack.removeAt(p.safForwardStack.lastIndex).toMutableList()
            p.currentDir = File(safKeyPath(p))
            p.searchQuery = ""
            refreshSafList(p)
            updateTopBar()
            return true
        }
        if (p.forwardStack.isEmpty()) return false
        p.backStack.add(p.currentDir)
        p.currentDir = p.forwardStack.removeAt(p.forwardStack.lastIndex)
        p.searchQuery = ""
        if (p.shizukuMode) refreshShizukuList(p) else loadPane(p)
        updateTopBar()
        return true
    }

    private fun swapPanes() {
        if (!dualPane) return
        val dirL = left.currentDir
        val dirR = right.currentDir
        left.currentDir = dirR
        right.currentDir = dirL
        // SAF 浏览状态跟着窗格一起换（只换 currentDir 的话 SAF 窗格会串位）。
        val safModeL = left.safMode; val safModeR = right.safMode
        left.safMode = safModeR; right.safMode = safModeL
        val safStorageL = left.safStorage; val safStorageR = right.safStorage
        left.safStorage = safStorageR; right.safStorage = safStorageL
        val safStackL = left.safStack; val safStackR = right.safStack
        left.safStack = safStackR; right.safStack = safStackL
        val safBackL = left.safBackStack; val safBackR = right.safBackStack
        left.safBackStack = safBackR; right.safBackStack = safBackL
        val safFwdL = left.safForwardStack; val safFwdR = right.safForwardStack
        left.safForwardStack = safFwdR; right.safForwardStack = safFwdL
        val safEntriesL = left.safEntries; val safEntriesR = right.safEntries
        left.safEntries = safEntriesR; right.safEntries = safEntriesL
        val safUriL = left.safUriByPath; val safUriR = right.safUriByPath
        left.safUriByPath = safUriR; right.safUriByPath = safUriL
        left.backStack.clear(); left.forwardStack.clear()
        right.backStack.clear(); right.forwardStack.clear()
        load()
    }

    private fun onFileClick(pane: Pane, f: File) {
        if (pane.selected.isNotEmpty()) { toggleSelect(pane, f); return }
        if (pane.safMode) {
            // SAF 模式：目录进得去；文本文件进内置编辑器（读流进来、保存写流回去），
            // 别的用 ACTION_VIEW + content URI 打开。
            val entry = pane.safEntries.firstOrNull { it.keyPath == f.absolutePath } ?: return
            if (entry.isDir) { enterSafDir(pane, entry); return }
            if (FileUtils.isText(File(entry.name))) {
                startActivity(Intent(this, TextEditorActivity::class.java).apply {
                    putExtra(TextEditorActivity.EXTRA_URI, entry.uri.toString())
                })
                return
            }
            openSafFile(entry)
            return
        }
        if (pane.shizukuMode) {
            // v1.8.0：根目录不止浏览了——目录进得去；文件先经 Shizuku cp
            // 到缓存里暂存再打开（文本进内置编辑器，改完写回原路径；
            // 别的用 ACTION_VIEW 打开暂存副本）。
            val entry = pane.shizukuEntries.firstOrNull { it.path == f.absolutePath }
            if (entry?.isDir == true) { navigateTo(pane, f); return }
            if (entry != null) { openRootFile(pane, entry); return }
            Toast.makeText(this, R.string.cannot_access, Toast.LENGTH_SHORT).show()
            return
        }
        if (f.isDirectory) { navigateTo(pane, f); return }
        if (FileUtils.isText(f)) {
            // 文本文件用内置编辑器打开。
            startActivity(Intent(this, TextEditorActivity::class.java).apply {
                putExtra(TextEditorActivity.EXTRA_PATH, f.absolutePath)
            })
            return
        }
        openWith(f)
    }

    private fun toggleSelect(pane: Pane, f: File) {
        // v1.8.0：根目录（Shizuku）也能多选了（以前直接弹“仅支持浏览”）。
        // SAF 窗格也能多选：选中态按合成路径（File(keyPath)）记，操作时再按
        // 合成路径找回 SafEntry。
        if (pane.selected.contains(f)) pane.selected.remove(f) else pane.selected.add(f)
        pane.adapter.notifyDataSetChanged()
        updateSelectionTopBar()
    }

    private fun clearSelection(pane: Pane) {
        pane.selected.clear()
        pane.adapter.notifyDataSetChanged()
        updateSelectionTopBar()
    }

    /** v1.8.0 多选模式顶栏（照 Max 的参考图）：有选中项时顶替掉工具栏，
     *  显示“已选择 N 项”+ 已选总大小；没选中项时恢复正常工具栏。
     *  底部的选择操作条删了，这套顶栏是唯一的选中操作入口。 */
    private fun updateSelectionTopBar() {
        if (!::left.isInitialized) return
        val p = activePane()
        val sel = p.selected
        if (sel.isEmpty()) {
            selectionTopBar.visibility = View.GONE
            topBar.visibility = View.VISIBLE
            updateTopBar()
        } else {
            topBar.visibility = View.GONE
            selectionTopBar.visibility = View.VISIBLE
            selTitle.text = getString(R.string.selected_count, sel.size)
            selSubtitle.text = FileUtils.formatSize(selectionTotalSize(p))
        }
    }

    /** 已选中的总大小：普通文件按实际大小（文件夹递归算）；SAF 按列表里的
     *  大小；根目录按 ls 的大小（解析不上的条目是 0，算不进去）。 */
    private fun selectionTotalSize(pane: Pane): Long {
        var total = 0L
        pane.selected.forEach { f ->
            total += when {
                pane.shizukuMode ->
                    pane.shizukuEntries.firstOrNull { it.path == f.absolutePath }?.size ?: 0L
                pane.safMode ->
                    pane.safEntries.firstOrNull { it.keyPath == f.absolutePath }?.size ?: 0L
                f.isDirectory -> FileUtils.dirSize(f)
                else -> f.length()
            }
        }
        return total
    }

    // ---------- 复制 / 剪切 / 粘贴 / 删除 / 重命名 / 新建 ----------

    /** 把一个窗格当前选中的项装进剪贴板：SAF 窗格按合成路径找回 SafEntry；
     *  根目录窗格记成 isRoot 项（真实路径，操作时走 Shizuku shell）。 */
    private fun selectionToClipboard(pane: Pane): List<ClipEntry> {
        if (pane.safMode) {
            val treeUri = pane.safStorage?.let { try { Uri.parse(it.uri) } catch (_: Exception) { null } }
            val parentDocId = pane.safStack.lastOrNull()?.docId
            return pane.selected.mapNotNull { f ->
                pane.safEntries.firstOrNull { it.keyPath == f.absolutePath }
                    ?.let { ClipEntry.ofSaf(it, treeUri, parentDocId) }
            }
        }
        if (pane.shizukuMode) return pane.selected.map { ClipEntry.ofRoot(it) }
        return pane.selected.map { ClipEntry.ofFile(it) }
    }

    private fun copySelection() {
        val p = activePane()
        clipboard = selectionToClipboard(p); clipboardCut = false
        clearSelection(p); snackbar(R.string.copied)
    }

    private fun cutSelection() {
        val p = activePane()
        clipboard = selectionToClipboard(p); clipboardCut = true
        clearSelection(p); snackbar(R.string.copied)
    }

    /** 粘贴到指定窗格（底部粘贴按钮用活动窗格，菜单“粘贴到另一窗格”用另一窗格）：
     *  目标是 SAF 目录就走 SAF 复制，根目录走 Shizuku shell，普通目录走原来的
     *  File 复制；剪贴板里 File 项、SAF 项和根目录项可以混装。失败的项留在
     *  剪贴板里，不丢。 */
    private fun pasteInto(targetPane: Pane) {
        if (clipboard.isEmpty()) return
        if (targetPane.shizukuMode) { pasteIntoRoot(targetPane); return }
        if (targetPane.safMode) { pasteIntoSaf(targetPane); return }
        val targetDir = targetPane.currentDir
        runBg {
            val failed = mutableListOf<ClipEntry>()
            val moved = mutableListOf<ClipEntry>()
            val stagedTemps = mutableListOf<File>()
            clipboard.forEach { src ->
                try {
                    val ok = when {
                        src.file != null -> {
                            // 根目录来的项：App 自己读不了，先经 Shizuku cp 到缓存里暂存。
                            val realSrc = if (src.isRoot) stageRootFile(src.file.absolutePath) else src.file
                            if (src.isRoot && realSrc != null) stagedTemps.add(realSrc)
                            if (realSrc == null) false else {
                                val dst = File(targetDir, src.name)
                                if (dst.canonicalPath == realSrc.canonicalPath) true // 粘回原处：不用动
                                else if (clipboardCut) {
                                    if (!src.isRoot && realSrc.renameTo(dst)) {
                                        // 同分区 renameTo 最快；跨分区会失败，下面兜底。
                                    } else {
                                        FileUtils.copyRecursive(realSrc, dst)
                                        if (!src.isRoot) FileUtils.deleteRecursive(realSrc)
                                    }
                                    true
                                } else { FileUtils.copyRecursive(realSrc, dst); true }
                            }
                        }
                        src.safUri != null -> copySafToFile(src, targetDir)
                        else -> false
                    }
                    if (ok) moved.add(src) else failed.add(src)
                } catch (_: Exception) { failed.add(src) }
            }
            if (clipboardCut) {
                // 剪切：只删复制成功的源。SAF 源按名重取新鲜 URI 再删；
                // 根目录源走 Shizuku rm；普通 File 源上面已处理。
                moved.forEach { src ->
                    if (src.safUri != null) { try { safDeleteClipEntry(src) } catch (_: Exception) { } }
                    else if (src.isRoot && src.file != null) {
                        try { ShizukuRoot.exec("rm", "-rf", src.file.absolutePath) } catch (_: Exception) { }
                    }
                }
            }
            // 暂存的根目录副本用完就清掉，别留在暂存区占地方。
            // 暂存文件是 shell 建的，App 自己删不掉，走 shell rm。
            stagedTemps.forEach {
                try { FileUtils.deleteRecursive(it) } catch (_: Exception) { }
                if (it.exists()) try { ShizukuRoot.exec("rm", "-rf", it.absolutePath) } catch (_: Exception) { }
            }
            runOnUiThread {
                clipboard = if (failed.isEmpty()) emptyList() else failed
                if (failed.isEmpty()) snackbar(R.string.pasted) else snackbar(R.string.error)
                panes().forEach { if (it.safMode) refreshSafList(it) }
            }
        }
    }

    /** 粘贴到一个根目录：来源可能是普通 File、SAF 文档或别的根目录路径，
     *  全部走 Shizuku shell（cp -r 复制，mv 剪切）；重名自动加 (1) 后缀，
     *  不覆盖；失败的项留在剪贴板里，不丢。 */
    private fun pasteIntoRoot(targetPane: Pane) {
        val targetDir = targetPane.currentDir.absolutePath
        Thread {
            val failed = mutableListOf<ClipEntry>()
            val moved = mutableListOf<ClipEntry>()
            clipboard.forEach { src ->
                try {
                    val ok = when {
                        src.file != null -> {
                            // 普通 File 和根目录路径都能直接给 shell 用；SAF 没有 file。
                            // 根目录源本身就是真实路径；普通 File 也是真实路径。
                            val dst = uniqueRootName(targetDir, src.name)
                            if (clipboardCut) shizukuMove(src.file.absolutePath, dst)
                            else shizukuCopy(src.file.absolutePath, dst)
                        }
                        src.safUri != null -> {
                            // v1.9.0：SAF 源先流式写到外置储存暂存区，再经 Shizuku cp
                            // 进根目录。旧代码暂存进 App 私有缓存（cacheDir/root_stage），
                            // shell 用户读不到，cp 直接失败——v1.8.1 stageRootFile
                            // 同款问题。
                            val stagedFile = stageSafToSdcard(src)
                            if (stagedFile == null) false else {
                                val dst = uniqueRootName(targetDir, src.name)
                                val okCp = shizukuCopy(stagedFile.absolutePath, dst)
                                try { FileUtils.deleteRecursive(stagedFile) } catch (_: Exception) { }
                                if (clipboardCut && okCp) {
                                    try { safDeleteClipEntry(src) } catch (_: Exception) { }
                                }
                                okCp
                            }
                        }
                        else -> false
                    }
                    if (ok) moved.add(src) else failed.add(src)
                } catch (e: Exception) { Log.w(TAG, "pasteIntoRoot failed src=${src.name}: $e"); failed.add(src) }
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                clipboard = if (failed.isEmpty()) emptyList() else failed
                if (failed.isEmpty()) snackbar(R.string.pasted) else snackbar(R.string.error)
                refreshShizukuList(targetPane)
                load()
            }
        }.start()
    }

    /** Shizuku cp -r：复制文件或文件夹（-r），返回是否成功。 */
    private fun shizukuCopy(srcPath: String, dstPath: String): Boolean {
        val r = ShizukuRoot.exec("cp", "-r", srcPath, dstPath)
        Log.d(TAG, "shizuku cp -r $srcPath -> $dstPath: exit=${r.exit} ${r.err.trim().take(120)}")
        return r.exit == 0
    }

    /** Shizuku mv：剪切（移动）。mv 跨文件系统也能自己复制 + 删源；
     *  mv 失败时用 cp -r + rm -rf 兜底。 */
    private fun shizukuMove(srcPath: String, dstPath: String): Boolean {
        val r = ShizukuRoot.exec("mv", srcPath, dstPath)
        Log.d(TAG, "shizuku mv $srcPath -> $dstPath: exit=${r.exit} ${r.err.trim().take(120)}")
        if (r.exit == 0) return true
        if (!shizukuCopy(srcPath, dstPath)) return false
        val del = ShizukuRoot.exec("rm", "-rf", srcPath)
        return del.exit == 0
    }

    /** 根目录里粘贴时的重名处理：目标已存在就加 " (1)"、" (2)"……不覆盖。 */
    private fun uniqueRootName(dir: String, name: String): String {
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var cand = "$dir/$name"
        var i = 1
        while (true) {
            // v1.8.1：`test` 只是 shell 内建命令，Android 上没有
            // /system/bin/test 这个二进制文件，直接 newProcess("test", …)
            // 永远起不来、exit=-1，重名检查等于没做，粘贴会静默覆盖
            // 同名文件。必须包一层 sh -c 让 shell 自己跑 test。
            val r = ShizukuRoot.execScript("test -e " + ShizukuRoot.shellQuote(cand))
            if (r.exit != 0) return cand
            cand = "$dir/$base ($i)$ext"
            i++
            if (i > 100) return cand
        }
    }

    /** 把根目录里的一个文件/文件夹经 Shizuku cp 到缓存里暂存（App 自己
     *  读不了根目录，只能让 shell 替它拷一份出来）。同步调用，调用方
     *  自己放后台线程。返回暂存的 File，失败返回 null。
     *  v1.8.1：暂存位置从 App 私有 cacheDir 改到 /data/local/tmp/fairu_stage——
     *  旧位置 App 私有目录是 0700，Shizuku 的 shell 用户根本写不进去，
     *  `cp` 直接 Permission denied，根目录文件点开就“出错了”，编辑器
     *  一次都没打开过。/data/local/tmp 是 shell 可写的公共暂存区；
     *  拷完再 `chmod -R a+rwX`，否则暂存文件是 shell 建的、App 读不了
     *  也写不了，编辑器一样打不开、保存不了。 */
    private fun stageRootFile(rootPath: String): File? {
        return try {
            val stageDir = File("/data/local/tmp/fairu_stage")
            ShizukuRoot.exec("mkdir", "-p", stageDir.absolutePath)
            val name = rootPath.substringAfterLast('/', "staged").ifBlank { "staged" }
            val staged = File(stageDir, name)
            if (staged.exists()) ShizukuRoot.exec("rm", "-rf", staged.absolutePath)
            val r = ShizukuRoot.exec("cp", "-r", rootPath, staged.absolutePath)
            Log.d(TAG, "stageRootFile $rootPath -> ${staged.absolutePath}: exit=${r.exit} ${r.err.trim().take(120)}")
            if (r.exit != 0) return null
            ShizukuRoot.exec("chmod", "-R", "a+rwX", staged.absolutePath)
            if (staged.exists() && staged.canRead()) staged else {
                Log.w(TAG, "stageRootFile staged not readable by app: ${staged.absolutePath}")
                null
            }
        } catch (e: Exception) { Log.w(TAG, "stageRootFile failed $rootPath: $e"); null }
    }

    /** 粘贴到一个 SAF 目录：来源可能是 File 也可能是 SAF 文档，都走流式复制；
     *  文件夹递归建；重名自动加 (1) 后缀，不覆盖；剪切是复制成功后删源。 */
    private fun pasteIntoSaf(targetPane: Pane) {
        val treeUri = targetPane.safStorage?.let { try { Uri.parse(it.uri) } catch (_: Exception) { null } } ?: return
        val parentDocId = targetPane.safStack.lastOrNull()?.docId ?: return
        val parentUri = try { DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId) } catch (_: Exception) { return }
        Thread {
            val failed = mutableListOf<ClipEntry>()
            val moved = mutableListOf<ClipEntry>()
            val stagedTemps = mutableListOf<File>()
            clipboard.forEach { src ->
                try {
                    val ok = when {
                        src.file != null -> {
                            // 根目录来的项先经 Shizuku cp 到缓存里暂存再流式写进去。
                            val realSrc = if (src.isRoot) stageRootFile(src.file.absolutePath) else src.file
                            if (src.isRoot && realSrc != null) stagedTemps.add(realSrc)
                            if (realSrc == null) false else copyFileToSaf(treeUri, parentUri, realSrc)
                        }
                        src.safUri != null -> copySafToSaf(treeUri, parentUri, src)
                        else -> false
                    }
                    if (ok) moved.add(src) else failed.add(src)
                } catch (_: Exception) { failed.add(src) }
            }
            if (clipboardCut) {
                moved.forEach { src ->
                    try {
                        if (src.safUri != null) safDeleteClipEntry(src)
                        else if (src.isRoot && src.file != null) ShizukuRoot.exec("rm", "-rf", src.file.absolutePath)
                        else if (src.file != null) FileUtils.deleteRecursive(src.file)
                    } catch (_: Exception) { }
                }
            }
            stagedTemps.forEach { try { FileUtils.deleteRecursive(it) } catch (_: Exception) { } }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                clipboard = if (failed.isEmpty()) emptyList() else failed
                if (failed.isEmpty()) snackbar(R.string.pasted) else snackbar(R.string.error)
                refreshSafList(targetPane)
                panes().forEach { if (it !== targetPane && it.safMode) refreshSafList(it) }
            }
        }.start()
    }

    /** v1.9.0 删除流程重做（图4）：所有删除入口（行菜单、多选栏、更多方式）
     *  都弹这个对话框——标题“删除”，正文“是否将文件 X 移动到回收站？”（多选
     *  是“是否将 N 个项目移动到回收站？”），下面列大小，文件夹还列文件数和
     *  文件夹数（递归统计），一个“移动到回收站”复选框默认勾选。勾选+确定→
     *  移入回收站；不勾选+确定→永久删除。根目录和 SAF 的删除也走这个对话框：
     *  移不进去就明确提示、不删文件。 */
    private data class DeleteStats(val size: Long, val files: Int, val dirs: Int, val countsKnown: Boolean)

    private fun confirmDelete(targets: List<File>) {
        if (targets.isEmpty()) return
        val pane = activePane()
        Thread {
            val stats = computeDeleteStats(pane, targets)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                showDeleteDialog(pane, targets, stats)
            }
        }.start()
    }

    /** 删除对话框要显示的统计：总大小；文件夹递归算文件数/文件夹数。
     *  根目录用 du + find 现算，算不出来就只显示大小（countsKnown=false）。 */
    private fun computeDeleteStats(pane: Pane, targets: List<File>): DeleteStats {
        var size = 0L
        var files = 0
        var dirs = 0
        var hasDir = false
        var known = true
        targets.forEach { f ->
            when {
                pane.safMode -> {
                    val e = pane.safEntries.firstOrNull { it.keyPath == f.absolutePath } ?: return@forEach
                    if (e.isDir) {
                        hasDir = true
                        size += safDirSize(e.uri)
                        val c = safDirCounts(e.uri)
                        files += c.first; dirs += c.second
                    } else { size += e.size; files += 1 }
                }
                pane.shizukuMode -> {
                    val e = pane.shizukuEntries.firstOrNull { it.path == f.absolutePath } ?: return@forEach
                    if (e.isDir) {
                        hasDir = true
                        val du = ShizukuRoot.exec("du", "-sb", e.path, timeoutMs = 15000L).let { r ->
                            if (r.exit == 0) r.out.trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull() else null
                        }
                        if (du != null) size += du else size += e.size
                        val fc = ShizukuRoot.execScript("find " + ShizukuRoot.shellQuote(e.path) + " -type f | wc -l").out.trim().toIntOrNull()
                        val dc = ShizukuRoot.execScript("find " + ShizukuRoot.shellQuote(e.path) + " -type d | wc -l").out.trim().toIntOrNull()
                        if (fc != null && dc != null) { files += fc; dirs += dc - 1 } else known = false
                    } else { size += e.size; files += 1 }
                }
                f.isDirectory -> {
                    hasDir = true
                    size += FileUtils.dirSize(f)
                    val c = dirCounts(f)
                    files += c.first; dirs += c.second
                }
                else -> { size += f.length(); files += 1 }
            }
        }
        return DeleteStats(size, files, dirs, known || !hasDir)
    }

    /** 普通文件夹的递归计数（文件数，文件夹数——不含自身）。 */
    private fun dirCounts(dir: File): Pair<Int, Int> {
        var files = 0
        var dirs = 0
        dir.listFiles()?.forEach {
            if (it.isDirectory) { dirs += 1; val c = dirCounts(it); files += c.first; dirs += c.second }
            else files += 1
        }
        return files to dirs
    }

    /** SAF 文件夹的递归计数（文件数，文件夹数——不含自身）。 */
    private fun safDirCounts(uri: Uri): Pair<Int, Int> {
        var files = 0
        var dirs = 0
        try {
            val docId = DocumentsContract.getDocumentId(uri)
            val treeUri = safTreeUriOf(uri) ?: return files to dirs
            val children = listSafChildren(treeUri, docId) ?: return files to dirs
            for (child in children) {
                if (child.isDir) { dirs += 1; val c = safDirCounts(child.uri); files += c.first; dirs += c.second }
                else files += 1
            }
        } catch (_: Exception) { }
        return files to dirs
    }

    // ---------- v1.9.6：液态玻璃（QWEA0/Liquid-Glass-Android v2.0.11） ----------
    /** 给一块 LiquidGlassView 上统一配置（三种模式真的不一样）：
     *  半透明 = 真实模糊 + 主题色调 25%；透明模糊 = 真实模糊 + 主题色调
     *  10%（淡一些，让模糊透出来）；全透明 = 不模糊、不染色、不描边，
     *  就是块透明玻璃。
     *  动态背景（enableDynamicBackground）故意不开：它只是在每次绘制后
     *  再调一次 invalidate，让玻璃每帧重绘——我们背后的内容是静态文件列
     *  表，根本不需要逐帧刷。开着它在 CPU 经典管线（模拟器）上会让主线
     *  程常年占三到六成，uiautomator dump/截图都变慢甚至超时（2026-11-20
     *  08:05–08:15 逐页扫因此卡死在 goto_main 的反复重启里）。关掉后库
     *  仍会在背景真的变化、树滚动、玻璃移动、贴图生成完时重绘（经典管线
     *  有 8×8 背景签名检测 + backdropScrollListener，贴图生成完调
     *  invalidate；透镜管线同理），肉眼效果一样，主线程回到空闲。
     *  原生库不可用（x86）时强制纯 Kotlin 管线，绝不碰 native 方法；
     *  arm 上保持库默认（SMART + 色差 AUTO，API 33+ 走 AGSL 透镜管线）。 */
    private fun configureLiquidGlass(v: LiquidGlassView) {
        v.enableDynamicBackground = false
        v.material = GlassMaterial.REGULAR
        when (prefs.getInt(PREF_LIQUID_GLASS_MODE, GLASS_MODE_TRANSLUCENT)) {
            GLASS_MODE_CLEAR -> {
                v.enableBackdropBlur = false
                v.enableChromaticAberration = false
                v.enableChromaticDispersion = false
                v.enableEdgeHighlight = false
                v.enableAdaptiveTint = false
                v.glassTint = Color.TRANSPARENT
                v.saturation = 100f
            }
            GLASS_MODE_BLUR -> {
                v.enableBackdropBlur = true
                v.enableChromaticAberration = true
                v.enableChromaticDispersion = false
                v.enableEdgeHighlight = true
                v.enableAdaptiveTint = false
                v.glassTint = glassTintColor(v.context, GLASS_TINT_ALPHA_BLUR)
                if (!NATIVE_GLASS_OK) {
                    v.blurMethod = BlurMethod.BOX_BLUR
                    v.chromaticAberrationMode = ChromaticAberrationEffect.PerformanceMode.KOTLIN
                }
            }
            else -> {
                // 半透明（默认）：模糊 + 主题色调 25%。
                v.enableBackdropBlur = true
                v.enableChromaticAberration = true
                v.enableChromaticDispersion = false
                v.enableEdgeHighlight = true
                v.enableAdaptiveTint = false
                v.glassTint = glassTintColor(v.context)
                if (!NATIVE_GLASS_OK) {
                    v.blurMethod = BlurMethod.BOX_BLUR
                    v.chromaticAberrationMode = ChromaticAberrationEffect.PerformanceMode.KOTLIN
                }
            }
        }
    }

    /** 玻璃关着时的惰性配置：不模糊、不描边、不染色、不追背景——就是个透明
     *  容器，主界面看起来和原来一模一样。
     *  注意：只关 enableBackdropBlur 不够——renderGlassEffectSync 里模糊关
     *  时 cachedBlurred 直接引用原背景，而色差默认是开的；位移贴图在后台
     *  生成完（attach 后几秒）就会进色差分支，AUTO 模式对大图走 C++，
     *  x86 上 loadLibrary 失败直接闪退（2026-11-20 05:51 实测）。所以关玻
     *  璃必须同时关色差和色散；x86 上再把模糊方法也钉成纯 Kotlin 的 BOX_BLUR。 */
    private fun configureLiquidGlassOff(v: LiquidGlassView) {
        v.enableDynamicBackground = false
        v.enableBackdropBlur = false
        v.enableChromaticAberration = false
        v.enableChromaticDispersion = false
        v.enableEdgeHighlight = false
        v.enableAdaptiveTint = false
        v.glassTint = Color.TRANSPARENT
        v.saturation = 100f
        if (!NATIVE_GLASS_OK) {
            v.blurMethod = BlurMethod.BOX_BLUR
            v.chromaticAberrationMode = ChromaticAberrationEffect.PerformanceMode.KOTLIN
        }
    }

    /** 主界面四块玻璃（工具栏/选择栏/底栏/抽屉）：开着时配玻璃 + 把被包面板
     *  的实色背景改透明（玻璃本身做底）；关着时配惰性 + 恢复实色背景。
     *  抽屉的背景是后面的主内容（backdropSource 指到 mainRoot）。
     *  注意 API<33：库给每个玻璃在 attach/size 变化时生成 3 张整尺寸位移
     *  贴图，抽屉那张（300dp×全高）约 13MB；本模拟器堆上限钉死 16MB
     *  （largeHeap 也救不了），生成到一半就 OOM 闪退（2026-11-20 07:22、
     *  07:39 两次实测；关着玻璃也会生成，因为生成跟开关无关）。
     *  所以 API<33 时在 onCreate 里就把抽屉内容从玻璃里拆出来（此时还没
     *  第一次 layout，贴图不会生成），抽屉退回半透明底（overlay 的
     *  colorSurfaceContainerLowest），不糊但不断。API 33+ 走透镜管线，
     *  根本不生成贴图，抽屉玻璃完整保留（Max 的手机是 API 36，不受影响）。 */
    private fun setupLiquidGlass() {
        val on = prefs.getBoolean(PREF_LIQUID_GLASS, false)
        try {
            val topGlass = findViewById<LiquidGlassView>(R.id.glassTopBar)
            val selGlass = findViewById<LiquidGlassView>(R.id.glassSelectionBar)
            val bottomGlass = findViewById<LiquidGlassView>(R.id.glassBottomBar)
            var drawerGlass: LiquidGlassView? = findViewById(R.id.glassDrawer)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && drawerGlass != null) {
                val drawerView = findViewById<ViewGroup>(R.id.drawerView)
                val content = drawerGlass.getChildAt(0)
                if (content != null) {
                    drawerGlass.removeView(content)
                    drawerView.removeView(drawerGlass)
                    drawerView.addView(content)
                }
                drawerGlass = null
            }
            val glasses = listOfNotNull(topGlass, selGlass, bottomGlass, drawerGlass)
            if (on) {
                glasses.forEach { configureLiquidGlass(it) }
                drawerGlass?.backdropSource = findViewById(R.id.mainRoot)
                findViewById<View>(R.id.topAppBar).background = ColorDrawable(Color.TRANSPARENT)
                // 玻璃开着时顶栏变浅：标题/副标题/导航图标原先是 colorOnPrimary
                // （白字，配实色主色底才对），现在衬浅色玻璃底会发白难读（红绿
                // 主界面图里都有）。改成 colorOnSurface——浅色主题是深字、
                // 深色主题是浅字，两边都对。玻璃关着不走这行，XML 的
                // ?attr/fairuToolbarTitleColor 原样生效。
                val topBarDark = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
                val topBar = findViewById<MaterialToolbar>(R.id.topAppBar)
                topBar.setTitleTextColor(topBarDark)
                topBar.setSubtitleTextColor(topBarDark)
                // 注意：不能用 navigationIcon.mutate() + setTint 再重新赋值——
                // MaterialToolbar.setNavigationIcon 会重新套 XML 里
                // navigationIconTint（白），等于把白字套回去。用现成的
                // setNavigationIconTint（Material 1.13 有）直接改。
                topBar.setNavigationIconTint(topBarDark)
                findViewById<View>(R.id.selectionTopBar).background = ColorDrawable(Color.TRANSPARENT)
                findViewById<View>(R.id.bottomBar).background = ColorDrawable(Color.TRANSPARENT)
                if (drawerGlass != null) {
                    findViewById<View>(R.id.drawerView).background = ColorDrawable(Color.TRANSPARENT)
                    findViewById<View>(R.id.drawerHeader).background = ColorDrawable(Color.TRANSPARENT)
                } else {
                    // 抽屉没玻璃（API<33）：给不透明底。以前这里用带透明度的
                    // colorSurfaceContainerLowest（#B3FFFFFF，70% 白），抽屉
                    // 文字和后面透出来的文件列表文字重叠（红绿抽屉图里都有：
                    // 标题压路径、菜单项压文件夹名、safetest 重影）——这是挡
                    // 交付的 bug。底色仍带种子色调（色调跟着种子变），只把
                    // 透明度钉成不透明：底色改用不透明的 colorSurface 混色调。
                    // 三种模式仍可分：半透明 = 25% 色调不透明底；透明模糊 =
                    // 10% 色调不透明底（更淡）；全透明 = 全透明（本来的意思，
                    // 文字重叠是该模式固有的）。
                    val mode = prefs.getInt(PREF_LIQUID_GLASS_MODE, GLASS_MODE_TRANSLUCENT)
                    val surface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.WHITE)
                    val drawerBg = when (mode) {
                        GLASS_MODE_CLEAR -> Color.TRANSPARENT
                        GLASS_MODE_BLUR -> tintSurfaceColor(surface, glassTintColor(this, GLASS_TINT_ALPHA_BLUR))
                        else -> tintSurfaceColor(surface, glassTintColor(this))
                    }
                    findViewById<View>(R.id.drawerView).background = ColorDrawable(drawerBg)
                    findViewById<View>(R.id.drawerHeader).background = ColorDrawable(drawerBg)
                }
            } else {
                glasses.forEach { configureLiquidGlassOff(it) }
            }
        } catch (_: Exception) { }
    }

    /** 对话框 builder：液态玻璃开着时用库的 LiquidGlassDialogBuilder（整块
     *  面板包进玻璃、背景从 Activity 后面采样），关着时走原来的
     *  MaterialAlertDialogBuilder。只换新建/删除/更多方式/排序这 4 个对话框
     *  （之前 BlurView 版也是这 4 个）。 */
    private fun glassDialogBuilder(): MaterialAlertDialogBuilder {
        return if (prefs.getBoolean(PREF_LIQUID_GLASS, false)) {
            LiquidGlassDialogBuilder(this, glassSetup = { configureLiquidGlass(this) })
        } else {
            MaterialAlertDialogBuilder(this)
        }
    }

    private fun showDeleteDialog(pane: Pane, targets: List<File>, stats: DeleteStats) {
        val density = resources.displayMetrics.density
        val pad = (20 * density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, 0, pad, 0)
        }
        val msg = if (targets.size == 1) getString(R.string.delete_to_trash_msg, targets[0].name)
            else getString(R.string.delete_to_trash_multi_msg, targets.size)
        val msgTv = TextView(this).apply { text = msg; textSize = 16f }
        container.addView(msgTv)
        val info = StringBuilder()
        info.append("${getString(R.string.size)}  ${FileUtils.formatSize(stats.size)}")
        val hasDir = targets.any { f ->
            when {
                pane.safMode -> pane.safEntries.firstOrNull { it.keyPath == f.absolutePath }?.isDir == true
                pane.shizukuMode -> pane.shizukuEntries.firstOrNull { it.path == f.absolutePath }?.isDir == true
                else -> f.isDirectory
            }
        }
        if (hasDir && stats.countsKnown) {
            info.append("\n${getString(R.string.file_count)}  ${stats.files}")
            info.append("\n${getString(R.string.folder_count)}  ${stats.dirs}")
        }
        val infoTv = TextView(this).apply {
            text = info.toString()
            textSize = 15f
            setPadding(0, (12 * density).toInt(), 0, (4 * density).toInt())
        }
        container.addView(infoTv)
        val checkBox = CheckBox(this).apply {
            text = getString(R.string.move_to_trash)
            isChecked = true
        }
        container.addView(checkBox)
        val deleteDlg = glassDialogBuilder().setTitle(R.string.delete).setIcon(R.drawable.ic_drawer_trash).setView(container)
            .setPositiveButton(R.string.ok) { _, _ -> doDelete(pane, targets, checkBox.isChecked) }
            .setNegativeButton(R.string.cancel, null).create()
        deleteDlg.show()
    }

    /** 删除的执行：勾选进回收站（移不进去就明确提示、不删文件），
     *  不勾选永久删除。 */
    private fun doDelete(pane: Pane, targets: List<File>, toTrash: Boolean) {
        if (toTrash) {
            Thread {
                var failed = 0
                targets.forEach { f ->
                    val ok = when {
                        pane.safMode -> moveSafToTrash(pane, f)
                        pane.shizukuMode -> moveRootToTrash(f)
                        else -> TrashHelper.moveToTrash(f)
                    }
                    if (!ok) failed++
                }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    clearSelection(pane)
                    if (failed == 0) snackbar(R.string.moved_to_trash) else snackbar(R.string.trash_move_failed)
                    refreshPane(pane)
                }
            }.start()
            return
        }
        // 永久删除：不勾选复选框就是这个分支。
        if (pane.safMode) {
            val treeUri = pane.safStorage?.let { try { Uri.parse(it.uri) } catch (_: Exception) { null } }
            val parentDocId = pane.safStack.lastOrNull()?.docId
            runSafOpTimeout(30000L, block = {
                var ok = true
                targets.forEach { f ->
                    val deleted = if (treeUri != null && parentDocId != null) {
                        safDeleteByName(treeUri, parentDocId, f.name) ||
                            pane.safEntries.firstOrNull { it.keyPath == f.absolutePath }
                                ?.let { safDeleteDocument(it.uri, it.isDir) } ?: false
                    } else {
                        pane.safEntries.firstOrNull { it.keyPath == f.absolutePath }
                            ?.let { safDeleteDocument(it.uri, it.isDir) } ?: false
                    }
                    if (!deleted) ok = false
                }
                ok
            }, onDone = { ok ->
                clearSelection(pane)
                if (ok) snackbar(R.string.deleted) else snackbar(R.string.delete_failed)
                refreshSafList(pane)
            })
            return
        }
        if (pane.shizukuMode) {
            Thread {
                var ok = true
                targets.forEach { p ->
                    if (p.absolutePath == "/") { ok = false; return@forEach }
                    val r = ShizukuRoot.exec("rm", "-rf", p.absolutePath)
                    if (r.exit != 0) ok = false
                }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    clearSelection(pane)
                    if (ok) snackbar(R.string.deleted) else snackbar(R.string.delete_failed)
                    refreshShizukuList(pane)
                }
            }.start()
            return
        }
        runBg {
            targets.forEach { FileUtils.deleteRecursive(it) }
            runOnUiThread { clearSelection(pane); snackbar(R.string.deleted) }
        }
    }

    /** 根目录项移入回收站：Shizuku mv 到回收站目录，记一条元数据（恢复用）。
     *  "/" 本身不让动；mv 失败返回 false，文件不动。 */
    private fun moveRootToTrash(f: File): Boolean {
        val src = f.absolutePath
        if (src == "/") return false
        return try {
            val trashDir = TrashHelper.trashDir()
            if (!trashDir.exists()) trashDir.mkdirs()
            val dstName = uniqueRootName(trashDir.absolutePath, f.name).substringAfterLast('/')
            val dst = "${trashDir.absolutePath}/$dstName"
            val r = ShizukuRoot.exec("mv", src, dst)
            Log.d(TAG, "root move to trash $src -> $dst: exit=${r.exit} ${r.err.trim().take(120)}")
            if (r.exit != 0) return false
            TrashHelper.recordMove(dstName, src)
            true
        } catch (e: Exception) { Log.w(TAG, "moveRootToTrash failed: $e"); false }
    }

    /** SAF 项移入回收站：先流式复制进回收站目录（普通 File 目录），成功再删原文件；
     *  复制失败或删源失败都返回 false，原文件不动（删源失败时回收站里会多一份，
     *  用户重试即可，数据不丢）。 */
    private fun moveSafToTrash(pane: Pane, f: File): Boolean {
        return try {
            val entry = pane.safEntries.firstOrNull { it.keyPath == f.absolutePath } ?: return false
            val treeUri = pane.safStorage?.let { try { Uri.parse(it.uri) } catch (_: Exception) { null } }
            val trashDir = TrashHelper.trashDir()
            if (!trashDir.exists()) trashDir.mkdirs()
            val unique = uniqueFileName(trashDir, entry.name)
            val copied = copySafToFile(ClipEntry.ofSaf(entry, treeUri, pane.safStack.lastOrNull()?.docId).copy(name = unique.name), trashDir)
            if (!copied) return false
            val deleted = safDeleteClipEntry(ClipEntry.ofSaf(entry, treeUri, pane.safStack.lastOrNull()?.docId))
            if (!deleted) return false
            TrashHelper.recordMove(unique.name, safBreadcrumb(pane) + "/" + entry.name)
            true
        } catch (e: Exception) { Log.w(TAG, "moveSafToTrash failed: $e"); false }
    }

    // v1.7.2：重命名后清选择态 + 刷新列表，不然选中的旧名会留在选择条里，
    // 下次多选删除时按旧名重列找不到就报 false。
    private fun showRenameDialog(pane: Pane, f: File) {
        val input = EditText(this); input.setText(f.name)
        MaterialAlertDialogBuilder(this).setTitle(R.string.rename).setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty() && n != f.name) runBg {
                    f.renameTo(File(f.parentFile, n))
                    runOnUiThread { clearSelection(pane); loadPane(pane); snackbar(R.string.renamed) }
                }
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    /** 根目录项重命名：Shizuku mv。失败（没权限 / 重名）弹“出错了”，
     *  成功清选择态、刷新列表。 */
    private fun showShizukuRenameDialog(pane: Pane, entry: ShizukuRoot.Entry) {
        val input = EditText(this); input.setText(entry.name)
        MaterialAlertDialogBuilder(this).setTitle(R.string.rename).setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty() && n != entry.name) {
                    val parent = entry.path.substringBeforeLast('/', "")
                    val dst = if (parent.isEmpty()) "/$n" else "$parent/$n"
                    Thread {
                        val r = ShizukuRoot.exec("mv", entry.path, dst)
                        Log.d(TAG, "root rename ${entry.path} -> $dst: exit=${r.exit} ${r.err.trim().take(120)}")
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            if (r.exit == 0) { clearSelection(pane); snackbar(R.string.renamed); refreshShizukuList(pane) }
                            else snackbar(R.string.error)
                        }
                    }.start()
                }
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    /** SAF 项重命名：DocumentsContract.renameDocument，成功刷新列表。 */
    private fun showSafRenameDialog(pane: Pane, entry: SafEntry) {
        val input = EditText(this); input.setText(entry.name)
        MaterialAlertDialogBuilder(this).setTitle(R.string.rename).setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty() && n != entry.name) {
                    // v1.7.1：重命名和删除走同一类提供方调用，也加超时 + 日志，
                    // 免得卡住时同样静默挂着（v1.7.0 的毛病）。
                    val treeUri = pane.safStorage?.let { try { Uri.parse(it.uri) } catch (_: Exception) { null } }
                    val parentDocId = pane.safStack.lastOrNull()?.docId
                    Log.d(TAG, "SAF rename ${entry.name} -> $n (parentDocId=$parentDocId)")
                    runSafOpTimeout(30000L, block = {
                        // 先按名重取新鲜 URI 再改名（docId 会过期，跟删除同一个毛病），
                        // 重列失败就用缓存 URI 试一次。
                        val freshUri = if (treeUri != null && parentDocId != null) {
                            try { listSafChildren(treeUri, parentDocId)?.firstOrNull { it.name == entry.name }?.uri } catch (e: Exception) { Log.w(TAG, "SAF rename re-list threw: $e"); null }
                        } else null
                        Log.d(TAG, "SAF rename: freshUri=${freshUri ?: "null, using cached entry.uri"}")
                        safRenameDocument(freshUri ?: entry.uri, n)
                    }, onDone = { ok ->
                        // v1.7.2：重命名成功后先清选择态再刷新，不然选中的旧名会留在
                        // 选择条里，下次多选删除时按旧名重列找不到就报 false。
                        if (ok) { clearSelection(pane); snackbar(R.string.renamed); refreshSafList(pane) }
                        else snackbar(R.string.error)
                    })
                }
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    /** v1.9.0 底部 + 重做（图9）：新建对话框——标题“新建”，上面一个名字输入框，
     *  下面一排按钮：取消在左，右边依次是 文件 / 文件夹。
     *  v1.9.5：粘贴按钮整个拿掉（Max 09:47 手机截图：双窗格下第四个按钮
     *  “文件夹”只剩“文件”二字、“夹”被截掉，碍事的粘贴不要了）。文件建空文件，
     *  文件夹建文件夹。 */
    private fun showNewDialog() {
        val p = activePane()
        val density = resources.displayMetrics.density
        // v1.9.4：容器内边距 20dp → 12dp，给按钮多留横向空间。
        val pad = (12 * density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, 0, pad, 0)
        }
        val input = EditText(this).apply { hint = getString(R.string.file_name_hint) }
        container.addView(input)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (16 * density).toInt(), 0, 0)
        }
        lateinit var dialog: androidx.appcompat.app.AlertDialog
        fun textButton(res: Int): android.widget.Button =
            // MaterialButton 在 1.13 里没有四参构造；用 Button 的四参构造套
            // Material3 TextButton 样式（透明底 + 主色文字 + 涟漪）。
            android.widget.Button(
                this, null, 0,
                com.google.android.material.R.style.Widget_Material3_Button_TextButton_Dialog
            ).apply {
                text = getString(res)
                // v1.9.4：TextButton 默认最小宽 88dp + 大内边距，按钮挤不下
                // 才把“文件夹”截掉；最小宽清零、横向内边距缩到 8dp，三个按钮
                // 图标+文字都能完整显示。
                minimumWidth = 0
                setPadding((8 * density).toInt(), 0, (8 * density).toInt(), 0)
            }
        val btnCancel = textButton(R.string.cancel).apply { setOnClickListener { dialog.dismiss() } }
        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        val btnFile = textButton(R.string.new_file_short).apply {
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_file, 0, 0, 0)
            compoundDrawableTintList = ColorStateList.valueOf(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary))
            setOnClickListener {
                val name = input.text.toString().trim()
                if (!checkNewName(p, name)) return@setOnClickListener
                dialog.dismiss()
                createFile(p, name)
            }
        }
        val btnFolder = textButton(R.string.new_folder_short).apply {
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_folder, 0, 0, 0)
            compoundDrawableTintList = ColorStateList.valueOf(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary))
            setOnClickListener {
                val name = input.text.toString().trim()
                if (!checkNewName(p, name)) return@setOnClickListener
                dialog.dismiss()
                createFolder(p, name)
            }
        }
        row.addView(btnCancel)
        row.addView(spacer)
        row.addView(btnFile)
        row.addView(btnFolder)
        container.addView(row)
        dialog = glassDialogBuilder().setTitle(R.string.new_title).setIcon(R.drawable.ic_add).setView(container).create()
        dialog.show()
        // v1.9.4：对话框默认宽度太窄是第四个按钮被截的另一半原因；把窗口
        // 放宽到屏宽的 92%，取消/文件/文件夹三个按钮图标+文字完整显示。
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    /** 新建的名字校验：非空、不含 "/"、不重名。 */
    private fun checkNewName(pane: Pane, name: String): Boolean {
        if (name.isBlank() || name.contains("/")) {
            Toast.makeText(this, R.string.invalid_name, Toast.LENGTH_SHORT).show()
            return false
        }
        val exists = when {
            pane.safMode -> pane.safEntries.any { it.name == name }
            pane.shizukuMode -> pane.shizukuEntries.any { it.name == name }
            else -> File(pane.currentDir, name).exists()
        }
        if (exists) {
            Toast.makeText(this, R.string.name_exists, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    /** 在当前目录新建空文件（普通 / 根目录 / SAF 三态）。 */
    private fun createFile(pane: Pane, name: String) {
        when {
            pane.shizukuMode -> {
                val dir = pane.currentDir.absolutePath
                val path = if (dir == "/") "/$name" else "$dir/$name"
                Thread {
                    val r = ShizukuRoot.exec("touch", path)
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        if (r.exit == 0) { snackbar(R.string.created); refreshShizukuList(pane) }
                        else snackbar(R.string.error)
                    }
                }.start()
            }
            pane.safMode -> {
                val treeUri = pane.safStorage?.let { try { Uri.parse(it.uri) } catch (_: Exception) { null } }
                val parentDocId = pane.safStack.lastOrNull()?.docId
                if (treeUri == null || parentDocId == null) return
                Thread {
                    val ok = safCreateFile(treeUri, parentDocId, name, safMimeFor(name)) != null
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        if (ok) { snackbar(R.string.created); refreshSafList(pane) }
                        else snackbar(R.string.error)
                    }
                }.start()
            }
            else -> runBg {
                try { File(pane.currentDir, name).createNewFile() } catch (_: Exception) { }
                runOnUiThread { snackbar(R.string.created) }
            }
        }
    }

    /** 在当前目录新建文件夹（普通 / 根目录 / SAF 三态；原来 showNewFolderDialog 的逻辑）。 */
    private fun createFolder(pane: Pane, name: String) {
        if (pane.shizukuMode) {
            // v1.8.0：根目录里新建文件夹走 Shizuku mkdir；没权限或已存在
            // 就失败，弹“出错了”，绝不静默。
            val dir = pane.currentDir.absolutePath
            val path = if (dir == "/") "/$name" else "$dir/$name"
            Thread {
                val r = ShizukuRoot.exec("mkdir", path)
                Log.d(TAG, "root mkdir $path: exit=${r.exit} ${r.err.trim().take(120)}")
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (r.exit == 0) { snackbar(R.string.created); refreshShizukuList(pane) }
                    else snackbar(R.string.error)
                }
            }.start()
            return
        }
        if (pane.safMode) {
            // SAF 目录里新建文件夹：DocumentsContract.createDocument。
            val treeUri = pane.safStorage?.let { try { Uri.parse(it.uri) } catch (_: Exception) { null } }
            val parentDocId = pane.safStack.lastOrNull()?.docId
            if (treeUri == null || parentDocId == null) return
            Thread {
                val ok = safCreateDir(treeUri, parentDocId, name) != null
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (ok) { snackbar(R.string.created); refreshSafList(pane) }
                    else snackbar(R.string.error)
                }
            }.start()
            return
        }
        runBg {
            File(pane.currentDir, name).mkdirs()
            runOnUiThread { snackbar(R.string.created) }
        }
    }

    /** 每行末尾 ⋮ 弹出的操作菜单（v1.8.0 加回来的那颗 ⋮）。
     *  文件：打开 / 分享 / 编辑文本（文本文件）/ 复制 / 剪切 / 删除 /
     *  重命名 / 属性 / 打开方式；文件夹：打开 / 复制 / 剪切 / 删除 /
     *  重命名 / 属性。菜单项都带图标（浅色下也看得见），全部能用。
     *  新建文件夹、粘贴不在这儿，在顶部工具栏 / 底部按钮里。 */
    private fun showFileMenu(pane: Pane, f: File, anchor: View) {
        if (pane.safMode) {
            // SAF 项的菜单，和普通文件夹同一套，全部能用（不是摆设）。
            val entry = pane.safEntries.firstOrNull { it.keyPath == f.absolutePath } ?: return
            val popup = PopupMenu(this, anchor)
            popup.setForceShowIcon(true)
            popup.menu.add(0, 1, 0, R.string.open).icon = tintedIcon(R.drawable.ic_folder)
            if (!entry.isDir) popup.menu.add(0, 9, 0, R.string.share).icon = tintedIcon(R.drawable.ic_share)
            if (FileUtils.isText(File(entry.name))) popup.menu.add(0, 8, 0, R.string.edit_text).icon = tintedIcon(R.drawable.ic_text)
            popup.menu.add(0, 3, 0, R.string.copy).icon = tintedIcon(R.drawable.ic_copy)
            popup.menu.add(0, 4, 0, R.string.cut).icon = tintedIcon(R.drawable.ic_cut)
            popup.menu.add(0, 5, 0, R.string.delete).icon = tintedIcon(R.drawable.ic_drawer_trash)
            popup.menu.add(0, 2, 0, R.string.rename).icon = tintedIcon(R.drawable.ic_edit)
            popup.menu.add(0, 6, 0, R.string.properties).icon = tintedIcon(R.drawable.ic_info)
            if (!entry.isDir) popup.menu.add(0, 7, 0, R.string.open_with).icon = tintedIcon(R.drawable.ic_arrow_forward)
            popup.menu.add(0, 11, 0, R.string.copy_path).icon = tintedIcon(R.drawable.ic_copy)
            val moveItemSaf = popup.menu.add(0, 12, 0, R.string.move_item)
            moveItemSaf.icon = tintedIcon(R.drawable.ic_move)
            moveItemSaf.isEnabled = otherPaneTargetValid()
            popup.menu.add(0, 13, 0, R.string.more_actions).icon = tintedIcon(R.drawable.ic_more_vert)
            // 剪贴板里多记 treeUri + 父 docId，剪切删源时能按名重取新鲜 URI。
            val safTreeUri = pane.safStorage?.let { try { Uri.parse(it.uri) } catch (_: Exception) { null } }
            val safParentDocId = pane.safStack.lastOrNull()?.docId
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> onFileClick(pane, f)
                    2 -> showSafRenameDialog(pane, entry)
                    3 -> { clipboard = listOf(ClipEntry.ofSaf(entry, safTreeUri, safParentDocId)); clipboardCut = false; snackbar(R.string.copied) }
                    4 -> { clipboard = listOf(ClipEntry.ofSaf(entry, safTreeUri, safParentDocId)); clipboardCut = true; snackbar(R.string.copied) }
                    5 -> confirmDelete(listOf(f))
                    6 -> showSafProperties(pane, entry)
                    7 -> openSafFile(entry)
                    8 -> startActivity(Intent(this, TextEditorActivity::class.java).apply {
                        putExtra(TextEditorActivity.EXTRA_URI, entry.uri.toString())
                    })
                    9 -> shareFiles(pane, listOf(f))
                    10 -> compressItem(pane, f)
                    11 -> copyItemPath(pane, f)
                    12 -> transferToOtherPane(pane, f, move = true)
                    13 -> showMoreActionsDialog(pane, f)
                }
                true
            }
            popup.show()
            return
        }
        if (pane.shizukuMode) {
            // v1.8.0：根目录项的菜单——打开 / 分享（仅文件）/ 复制 / 剪切 /
            // 删除 / 重命名 / 属性，全部走 Shizuku shell。
            val entry = pane.shizukuEntries.firstOrNull { it.path == f.absolutePath } ?: return
            val popup = PopupMenu(this, anchor)
            popup.setForceShowIcon(true)
            popup.menu.add(0, 1, 0, R.string.open).icon = tintedIcon(R.drawable.ic_folder)
            if (!entry.isDir) popup.menu.add(0, 9, 0, R.string.share).icon = tintedIcon(R.drawable.ic_share)
            popup.menu.add(0, 3, 0, R.string.copy).icon = tintedIcon(R.drawable.ic_copy)
            popup.menu.add(0, 4, 0, R.string.cut).icon = tintedIcon(R.drawable.ic_cut)
            popup.menu.add(0, 5, 0, R.string.delete).icon = tintedIcon(R.drawable.ic_drawer_trash)
            popup.menu.add(0, 2, 0, R.string.rename).icon = tintedIcon(R.drawable.ic_edit)
            popup.menu.add(0, 6, 0, R.string.properties).icon = tintedIcon(R.drawable.ic_info)
            popup.menu.add(0, 11, 0, R.string.copy_path).icon = tintedIcon(R.drawable.ic_copy)
            val moveItemRoot = popup.menu.add(0, 12, 0, R.string.move_item)
            moveItemRoot.icon = tintedIcon(R.drawable.ic_move)
            moveItemRoot.isEnabled = otherPaneTargetValid()
            popup.menu.add(0, 13, 0, R.string.more_actions).icon = tintedIcon(R.drawable.ic_more_vert)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> onFileClick(pane, f)
                    2 -> showShizukuRenameDialog(pane, entry)
                    3 -> { clipboard = listOf(ClipEntry.ofRoot(f)); clipboardCut = false; snackbar(R.string.copied) }
                    4 -> { clipboard = listOf(ClipEntry.ofRoot(f)); clipboardCut = true; snackbar(R.string.copied) }
                    5 -> confirmDelete(listOf(f))
                    6 -> showShizukuProperties(pane, entry)
                    9 -> shareFiles(pane, listOf(f))
                    10 -> compressItem(pane, f)
                    11 -> copyItemPath(pane, f)
                    12 -> transferToOtherPane(pane, f, move = true)
                    13 -> showMoreActionsDialog(pane, f)
                }
                true
            }
            popup.show()
            return
        }
        val isDir = f.isDirectory
        val popup = PopupMenu(this, anchor)
        popup.setForceShowIcon(true)
        popup.menu.add(0, 1, 0, R.string.open).icon = tintedIcon(R.drawable.ic_folder)
        if (!isDir) popup.menu.add(0, 9, 0, R.string.share).icon = tintedIcon(R.drawable.ic_share)
        if (FileUtils.isText(f)) popup.menu.add(0, 8, 0, R.string.edit_text).icon = tintedIcon(R.drawable.ic_text)
        popup.menu.add(0, 3, 0, R.string.copy).icon = tintedIcon(R.drawable.ic_copy)
        popup.menu.add(0, 4, 0, R.string.cut).icon = tintedIcon(R.drawable.ic_cut)
        popup.menu.add(0, 5, 0, R.string.delete).icon = tintedIcon(R.drawable.ic_drawer_trash)
        popup.menu.add(0, 2, 0, R.string.rename).icon = tintedIcon(R.drawable.ic_edit)
        popup.menu.add(0, 6, 0, R.string.properties).icon = tintedIcon(R.drawable.ic_info)
        if (!isDir) popup.menu.add(0, 7, 0, R.string.open_with).icon = tintedIcon(R.drawable.ic_arrow_forward)
        popup.menu.add(0, 11, 0, R.string.copy_path).icon = tintedIcon(R.drawable.ic_copy)
        val moveItem = popup.menu.add(0, 12, 0, R.string.move_item)
        moveItem.icon = tintedIcon(R.drawable.ic_move)
        moveItem.isEnabled = otherPaneTargetValid()
        popup.menu.add(0, 13, 0, R.string.more_actions).icon = tintedIcon(R.drawable.ic_more_vert)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> onFileClick(pane, f)
                2 -> showRenameDialog(pane, f)
                3 -> { clipboard = listOf(ClipEntry.ofFile(f)); clipboardCut = false; snackbar(R.string.copied) }
                4 -> { clipboard = listOf(ClipEntry.ofFile(f)); clipboardCut = true; snackbar(R.string.copied) }
                5 -> confirmDelete(listOf(f))
                6 -> showProperties(f)
                7 -> openWith(f)
                8 -> startActivity(Intent(this, TextEditorActivity::class.java).apply {
                    putExtra(TextEditorActivity.EXTRA_PATH, f.absolutePath)
                })
                9 -> shareFiles(pane, listOf(f))
                10 -> compressItem(pane, f)
                11 -> copyItemPath(pane, f)
                12 -> transferToOtherPane(pane, f, move = true)
                13 -> showMoreActionsDialog(pane, f)
            }
            true
        }
        popup.show()
    }

    /** v1.8.0 多选模式顶栏右边的 ⋮ 菜单（照 Max 的参考图）：复制 / 剪切 /
     *  粘贴 / 重命名 / 属性 / 全选 / 取消选择。重命名和属性一次只对一项，
     *  选中多项时不显示这两项。 */
    private fun showSelectionMenu(anchor: View) {
        val pane = activePane()
        val single = pane.selected.size == 1
        val popup = PopupMenu(this, anchor)
        popup.setForceShowIcon(true)
        popup.menu.add(0, 1, 0, R.string.copy).icon = tintedIcon(R.drawable.ic_copy)
        popup.menu.add(0, 2, 0, R.string.cut).icon = tintedIcon(R.drawable.ic_cut)
        popup.menu.add(0, 3, 0, R.string.paste).icon = tintedIcon(R.drawable.ic_paste)
        if (single) {
            popup.menu.add(0, 4, 0, R.string.rename).icon = tintedIcon(R.drawable.ic_edit)
            popup.menu.add(0, 5, 0, R.string.properties).icon = tintedIcon(R.drawable.ic_info)
        }
        popup.menu.add(0, 6, 0, R.string.select_all).icon = tintedIcon(R.drawable.ic_check)
        popup.menu.add(0, 7, 0, R.string.deselect_all).icon = tintedIcon(R.drawable.ic_close)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> copySelection()
                2 -> cutSelection()
                3 -> pasteInto(pane)
                4 -> {
                    val f = pane.selected.firstOrNull() ?: return@setOnMenuItemClickListener true
                    when {
                        pane.safMode -> pane.safEntries.firstOrNull { it.keyPath == f.absolutePath }
                            ?.let { showSafRenameDialog(pane, it) }
                        pane.shizukuMode -> pane.shizukuEntries.firstOrNull { it.path == f.absolutePath }
                            ?.let { showShizukuRenameDialog(pane, it) }
                        else -> showRenameDialog(pane, f)
                    }
                }
                5 -> {
                    val f = pane.selected.firstOrNull() ?: return@setOnMenuItemClickListener true
                    when {
                        pane.safMode -> pane.safEntries.firstOrNull { it.keyPath == f.absolutePath }
                            ?.let { showSafProperties(pane, it) }
                        pane.shizukuMode -> pane.shizukuEntries.firstOrNull { it.path == f.absolutePath }
                            ?.let { showShizukuProperties(pane, it) }
                        else -> showProperties(f)
                    }
                }
                6 -> {
                    pane.selected.addAll(pane.files)
                    pane.adapter.notifyDataSetChanged()
                    updateSelectionTopBar()
                }
                7 -> clearSelection(pane)
            }
            true
        }
        popup.show()
    }

    // ---------- v1.9.0：跨窗格移动 / 复制、压缩、分享 ZIP、书签、Pin ----------

    /** 按窗格当前模式刷新（普通 loadPane / SAF 刷新列表 / 根目录刷新列表）。 */
    private fun refreshPane(pane: Pane) {
        when {
            pane.safMode -> refreshSafList(pane)
            pane.shizukuMode -> refreshShizukuList(pane)
            else -> loadPane(pane)
        }
    }

    /** 外置储存上的暂存区（shell 能读）。App 私有缓存是 0700，Shizuku 的
     *  shell 用户读不到（v1.8.1 stageRootFile 同款问题），所以凡是 App 建出来
     *  要交给 shell 用的文件（SAF→根目录暂存、根目录加密/解压的中转）都放这儿，
     *  不放 cacheDir。 */
    private fun sdcardStageDir(): File = File(Environment.getExternalStorageDirectory(), ".fairu_stage")

    /** SAF 项暂存到外置储存暂存区（v1.9.0 修 SAF→根目录粘贴：旧代码暂存进
     *  App 私有缓存再让 shell cp，shell 读不到就失败）。 */
    private fun stageSafToSdcard(src: ClipEntry): File? {
        val stageDir = sdcardStageDir()
        return try {
            FileUtils.deleteRecursive(stageDir)
            stageDir.mkdirs()
            if (copySafToFile(src, stageDir)) File(stageDir, src.name) else null
        } catch (_: Exception) { null }
    }

    /** 普通目录里重名时自动改名：name (1).ext、name (2).ext……不覆盖。 */
    private fun uniqueFileName(dir: File, name: String): File {
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var cand = File(dir, name)
        var i = 1
        while (cand.exists()) {
            cand = File(dir, "$base ($i)$ext")
            i++
            if (i > 100) break
        }
        return cand
    }

    /** “移动 / 复制 ->”是否可用：仅双窗格有效，且另一窗格打开着一个文件夹。
     *  单窗格、或另一窗格在根目录 "/"（路径为空或是根目录）时不可用，
     *  菜单项置灰（像 Max 图3里的 移动 那样）。 */
    private fun otherPaneTargetValid(): Boolean {
        if (!dualPane) return false
        val o = otherPane()
        return when {
            o.safMode -> o.safStack.isNotEmpty()
            o.shizukuMode -> o.currentDir.absolutePath != "/"
            else -> o.currentDir.absolutePath.isNotBlank() && o.currentDir.absolutePath != "/"
        }
    }

    /** 把一项移到另一窗格当前文件夹（移动）或复制过去（复制 ->）。
     *  同一个文件夹内移动是空操作，直接提示，不做任何事。 */
    private fun transferToOtherPane(pane: Pane, f: File, move: Boolean) {
        if (!otherPaneTargetValid()) {
            Toast.makeText(this, R.string.other_pane_no_folder, Toast.LENGTH_SHORT).show()
            return
        }
        val target = otherPane()
        if (move) {
            val sameDir = when {
                pane.safMode && target.safMode ->
                    pane.safStorage?.uri == target.safStorage?.uri &&
                        pane.safStack.lastOrNull()?.docId == target.safStack.lastOrNull()?.docId
                pane.shizukuMode && target.shizukuMode ->
                    pane.currentDir.absolutePath == target.currentDir.absolutePath
                !pane.safMode && !pane.shizukuMode && !target.safMode && !target.shizukuMode ->
                    pane.currentDir.absolutePath == target.currentDir.absolutePath
                else -> false
            }
            if (sameDir) {
                Toast.makeText(this, R.string.already_here, Toast.LENGTH_SHORT).show()
                return
            }
        }
        Thread {
            val ok = doTransfer(pane, f, target, move)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (ok) {
                    snackbar(if (move) R.string.moved else R.string.copied)
                } else {
                    snackbar(if (move) R.string.move_failed else R.string.error)
                }
                refreshPane(pane)
                refreshPane(target)
            }
        }.start()
    }

    /** 跨窗格传输的具体执行（后台线程）：源 × 目标共 3×3 种组合。
     *  普通↔普通走 File；涉及根目录走 Shizuku shell；涉及 SAF 走流式复制；
     *  剪切（move）是复制成功后删源。 */
    private fun doTransfer(srcPane: Pane, f: File, dstPane: Pane, move: Boolean): Boolean {
        return try {
            when {
                srcPane.safMode -> {
                    val entry = srcPane.safEntries.firstOrNull { it.keyPath == f.absolutePath } ?: return false
                    val srcTree = srcPane.safStorage?.let { try { Uri.parse(it.uri) } catch (_: Exception) { null } }
                    val srcClip = ClipEntry.ofSaf(entry, srcTree, srcPane.safStack.lastOrNull()?.docId)
                    when {
                        dstPane.safMode -> {
                            val dstTree = dstPane.safStorage?.let { try { Uri.parse(it.uri) } catch (_: Exception) { null } } ?: return false
                            val dstParent = dstPane.safStack.lastOrNull()?.docId ?: return false
                            val dstParentUri = try { DocumentsContract.buildDocumentUriUsingTree(dstTree, dstParent) } catch (_: Exception) { return false }
                            if (move) {
                                // 同一个提供方树内优先走 moveDocument（不断链）；
                                // 跨树或提供方不支持就复制 + 删源。
                                val sameTree = srcPane.safStorage?.uri == dstPane.safStorage?.uri
                                val moved = if (sameTree) {
                                    safMoveDocument(entry, srcPane.safStack.lastOrNull()?.docId ?: return false, dstTree, dstParent)
                                } else false
                                if (moved) true else {
                                    val ok = copySafToSaf(dstTree, dstParentUri, srcClip)
                                    if (ok) safDeleteClipEntry(srcClip) else false
                                }
                            } else copySafToSaf(dstTree, dstParentUri, srcClip)
                        }
                        dstPane.shizukuMode -> {
                            val staged = stageSafToSdcard(srcClip) ?: return false
                            val dst = uniqueRootName(dstPane.currentDir.absolutePath, srcClip.name)
                            val ok = shizukuCopy(staged.absolutePath, dst)
                            try { FileUtils.deleteRecursive(staged) } catch (_: Exception) { }
                            if (ok && move) safDeleteClipEntry(srcClip) else ok
                        }
                        else -> {
                            val dstDir = dstPane.currentDir
                            val unique = uniqueFileName(dstDir, srcClip.name)
                            val ok = copySafToFile(srcClip.copy(name = unique.name), dstDir)
                            if (ok && move) safDeleteClipEntry(srcClip) else ok
                        }
                    }
                }
                srcPane.shizukuMode -> {
                    val srcPath = f.absolutePath
                    when {
                        dstPane.safMode -> {
                            val staged = stageRootFile(srcPath) ?: return false
                            val dstTree = dstPane.safStorage?.let { try { Uri.parse(it.uri) } catch (_: Exception) { null } } ?: return false
                            val dstParent = dstPane.safStack.lastOrNull()?.docId ?: return false
                            val dstParentUri = try { DocumentsContract.buildDocumentUriUsingTree(dstTree, dstParent) } catch (_: Exception) { return false }
                            val ok = copyFileToSaf(dstTree, dstParentUri, staged)
                            try { FileUtils.deleteRecursive(staged) } catch (_: Exception) { }
                            if (staged.exists()) try { ShizukuRoot.exec("rm", "-rf", staged.absolutePath) } catch (_: Exception) { }
                            if (ok && move) ShizukuRoot.exec("rm", "-rf", srcPath).exit == 0 else ok
                        }
                        dstPane.shizukuMode -> {
                            val dst = uniqueRootName(dstPane.currentDir.absolutePath, f.name)
                            if (move) shizukuMove(srcPath, dst) else shizukuCopy(srcPath, dst)
                        }
                        else -> {
                            val dstDir = dstPane.currentDir
                            val dstFile = uniqueFileName(dstDir, f.name)
                            if (move) {
                                // shell mv 写不进目标时（如目标在受限位置），暂存再拷。
                                shizukuMove(srcPath, dstFile.absolutePath) || run {
                                    val staged = stageRootFile(srcPath) ?: return false
                                    val ok = try { FileUtils.copyRecursive(staged, dstFile); true } catch (_: Exception) { false }
                                    try { FileUtils.deleteRecursive(staged) } catch (_: Exception) { }
                                    if (staged.exists()) try { ShizukuRoot.exec("rm", "-rf", staged.absolutePath) } catch (_: Exception) { }
                                    if (ok) ShizukuRoot.exec("rm", "-rf", srcPath).exit == 0 else false
                                }
                            } else {
                                shizukuCopy(srcPath, dstFile.absolutePath) || run {
                                    val staged = stageRootFile(srcPath) ?: return false
                                    val ok = try { FileUtils.copyRecursive(staged, dstFile); true } catch (_: Exception) { false }
                                    try { FileUtils.deleteRecursive(staged) } catch (_: Exception) { }
                                    if (staged.exists()) try { ShizukuRoot.exec("rm", "-rf", staged.absolutePath) } catch (_: Exception) { }
                                    ok
                                }
                            }
                        }
                    }
                }
                else -> {
                    when {
                        dstPane.safMode -> {
                            val dstTree = dstPane.safStorage?.let { try { Uri.parse(it.uri) } catch (_: Exception) { null } } ?: return false
                            val dstParent = dstPane.safStack.lastOrNull()?.docId ?: return false
                            val dstParentUri = try { DocumentsContract.buildDocumentUriUsingTree(dstTree, dstParent) } catch (_: Exception) { return false }
                            val ok = copyFileToSaf(dstTree, dstParentUri, f)
                            if (ok && move) FileUtils.deleteRecursive(f) else ok
                        }
                        dstPane.shizukuMode -> {
                            val dst = uniqueRootName(dstPane.currentDir.absolutePath, f.name)
                            if (move) shizukuMove(f.absolutePath, dst) else shizukuCopy(f.absolutePath, dst)
                        }
                        else -> {
                            val dstDir = dstPane.currentDir
                            val dstFile = if (f.parentFile?.absolutePath == dstDir.absolutePath) uniqueFileName(dstDir, f.name)
                                else File(dstDir, f.name)
                            if (move) {
                                if (f.renameTo(dstFile)) true else {
                                    try { FileUtils.copyRecursive(f, dstFile); FileUtils.deleteRecursive(f); true } catch (_: Exception) { false }
                                }
                            } else {
                                try { FileUtils.copyRecursive(f, dstFile); true } catch (_: Exception) { false }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) { Log.w(TAG, "doTransfer failed: $e"); false }
    }

    /** SAF 文档移动（API 24+ 的 DocumentsContract.moveDocument）：同一个
     *  提供方树内改名即移动。个别提供方不支持会抛异常，调用方回落到复制+删源。 */
    private fun safMoveDocument(entry: SafEntry, srcParentDocId: String, dstTreeUri: Uri, dstParentDocId: String): Boolean {
        return try {
            val srcTree = safTreeUriOf(entry.uri) ?: return false
            val srcParentUri = DocumentsContract.buildDocumentUriUsingTree(srcTree, srcParentDocId)
            val dstParentUri = DocumentsContract.buildDocumentUriUsingTree(dstTreeUri, dstParentDocId)
            DocumentsContract.moveDocument(contentResolver, entry.uri, srcParentUri, dstParentUri) != null
        } catch (e: Exception) { Log.w(TAG, "safMoveDocument failed (provider may not support it): $e"); false }
    }

    /** 复制路径：把该项的绝对路径（SAF 是面包屑路径）放进系统剪贴板。 */
    private fun copyItemPath(pane: Pane, f: File) {
        val path = if (pane.safMode) safBreadcrumb(pane) + "/" + f.name else f.absolutePath
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("path", path))
            Toast.makeText(this, R.string.path_copied, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) { Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show() }
    }

    /** 压缩：把该项打成 name.zip 放当前目录（重名加 (1)），文件和文件夹都能压。 */
    private fun compressItem(pane: Pane, f: File) {
        Thread {
            var ok = false
            try {
                ok = when {
                    pane.safMode -> {
                        val entry = pane.safEntries.firstOrNull { it.keyPath == f.absolutePath }
                        val treeUri = pane.safStorage?.let { try { Uri.parse(it.uri) } catch (_: Exception) { null } }
                        val parentDocId = pane.safStack.lastOrNull()?.docId
                        if (entry == null || treeUri == null || parentDocId == null) false else {
                            val parentUri = try { DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId) } catch (_: Exception) { null } ?: return@Thread
                            val zipName = safUniqueName(treeUri, parentDocId, entry.name + ".zip", isDir = false)
                            val docUri = try { DocumentsContract.createDocument(contentResolver, parentUri, "application/zip", zipName) } catch (_: Exception) { null }
                            if (docUri == null) {
                                runOnUiThread { snackbar(R.string.zip_failed) }
                                return@Thread
                            }
                            try {
                                contentResolver.openOutputStream(docUri, "w")?.use { out ->
                                    java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(out)).use { zos ->
                                        addSafToZip(treeUri, entry, entry.name, zos)
                                    }
                                }
                                true
                            } catch (e: Exception) { Log.w(TAG, "saf zip failed: $e"); false }
                        }
                    }
                    pane.shizukuMode -> {
                        val staged = stageRootFile(f.absolutePath)
                        if (staged == null) {
                            runOnUiThread { snackbar(R.string.zip_failed) }
                            return@Thread
                        }
                        // zip 中转文件放外置储存暂存区：shell 要读它拷进根目录，
                        // App 私有缓存 shell 读不到（同款问题见 stageSafToSdcard）。
                        val stageDir = sdcardStageDir().apply { mkdirs() }
                        val tmpZip = File(stageDir, "tmp_${System.currentTimeMillis()}.zip")
                        var okZip = false
                        try {
                            FileUtils.zipFiles(listOf(staged), tmpZip)
                            okZip = true
                        } catch (e: Exception) { Log.w(TAG, "root zip failed: $e") }
                        var done = false
                        if (okZip) {
                            val dst = uniqueRootName(pane.currentDir.absolutePath, f.name + ".zip")
                            done = shizukuCopy(tmpZip.absolutePath, dst)
                        }
                        try { tmpZip.delete() } catch (_: Exception) { }
                        try { FileUtils.deleteRecursive(staged) } catch (_: Exception) { }
                        if (staged.exists()) try { ShizukuRoot.exec("rm", "-rf", staged.absolutePath) } catch (_: Exception) { }
                        done
                    }
                    else -> {
                        val out = uniqueFileName(pane.currentDir, f.name + ".zip")
                        try { FileUtils.zipFiles(listOf(f), out); true } catch (e: Exception) { Log.w(TAG, "zip failed: $e"); false }
                    }
                }
            } catch (e: Exception) { Log.w(TAG, "compress failed: $e") }
            val resultOk = ok
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (resultOk) { snackbar(R.string.compressed); refreshPane(pane) }
                else snackbar(R.string.zip_failed)
            }
        }.start()
    }

    /** SAF 项写进 ZipOutputStream（文件夹递归；条目名用相对路径）。 */
    private fun addSafToZip(treeUri: Uri, entry: SafEntry, entryName: String, zos: java.util.zip.ZipOutputStream) {
        if (entry.isDir) {
            val docId = try { DocumentsContract.getDocumentId(entry.uri) } catch (_: Exception) { return }
            val children = listSafChildren(treeUri, docId) ?: return
            children.forEach { addSafToZip(treeUri, it, "$entryName/${it.name}", zos) }
        } else {
            zos.putNextEntry(java.util.zip.ZipEntry(entryName))
            try { contentResolver.openInputStream(entry.uri)?.use { it.copyTo(zos) } } catch (_: Exception) { }
            zos.closeEntry()
        }
    }

    /** 分享 ZIP：先把该项打成 zip 放 App 缓存，再调系统分享（文件夹也能分享）。 */
    private fun shareAsZip(pane: Pane, f: File) {
        Thread {
            val zipFile = File(cacheDir, "share_${System.currentTimeMillis()}.zip")
            var ok = false
            try {
                ok = when {
                    pane.safMode -> {
                        val entry = pane.safEntries.firstOrNull { it.keyPath == f.absolutePath }
                        val treeUri = pane.safStorage?.let { try { Uri.parse(it.uri) } catch (_: Exception) { null } }
                        if (entry == null || treeUri == null) false else try {
                            java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(zipFile.outputStream())).use { zos ->
                                addSafToZip(treeUri, entry, entry.name, zos)
                            }
                            true
                        } catch (e: Exception) { Log.w(TAG, "share zip saf failed: $e"); false }
                    }
                    pane.shizukuMode -> {
                        val staged = stageRootFile(f.absolutePath)
                        if (staged == null) {
                            runOnUiThread { Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show() }
                            return@Thread
                        }
                        val okZip = try { FileUtils.zipFiles(listOf(staged), zipFile); true } catch (e: Exception) { Log.w(TAG, "share zip root failed: $e"); false }
                        try { FileUtils.deleteRecursive(staged) } catch (_: Exception) { }
                        if (staged.exists()) try { ShizukuRoot.exec("rm", "-rf", staged.absolutePath) } catch (_: Exception) { }
                        okZip
                    }
                    else -> try { FileUtils.zipFiles(listOf(f), zipFile); true } catch (e: Exception) { Log.w(TAG, "share zip failed: $e"); false }
                }
            } catch (e: Exception) { Log.w(TAG, "share zip failed: $e") }
            val resultOk = ok
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (!resultOk || !zipFile.exists()) {
                    Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                try {
                    val uri = FairuFileProvider.uriFor(zipFile)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, getString(R.string.share_zip)))
                } catch (_: Exception) { Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    // ---------- v1.9.0：更多方式对话框 ----------

    private data class MoreAction(val labelRes: Int, val iconRes: Int, val enabled: Boolean, val run: () -> Unit)

    /** 图3式的更多方式对话框：标题是文件名 + 右上角铅笔（重命名），下面是
     *  两列网格按钮（带图标，不可用的置灰）。16 个按钮全部真能用。 */
    private fun showMoreActionsDialog(pane: Pane, f: File) {
        val isDir: Boolean
        val name: String
        when {
            pane.safMode -> {
                val e = pane.safEntries.firstOrNull { it.keyPath == f.absolutePath } ?: return
                isDir = e.isDir; name = e.name
            }
            pane.shizukuMode -> {
                val e = pane.shizukuEntries.firstOrNull { it.path == f.absolutePath } ?: return
                isDir = e.isDir; name = e.name
            }
            else -> { isDir = f.isDirectory; name = f.name }
        }
        val isZip = FileUtils.isZip(File(name))
        val isEnc = name.endsWith(".enc", ignoreCase = true)
        val pinned = PinnedFolders.isPinned(this, f.absolutePath)
        val bookmarked = Bookmarks.contains(this, bookmarkTarget(pane, f), bookmarkKind(pane))
        val hidden = name.startsWith(".")
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()
        lateinit var dialog: androidx.appcompat.app.AlertDialog
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        // 标题行：文件名 + 右上角铅笔（点开是重命名）。
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleTv = TextView(this).apply {
            text = name
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val pencil = ImageButton(this).apply {
            setImageResource(R.drawable.ic_edit)
            background = selectableBackground()
            contentDescription = getString(R.string.rename)
            setOnClickListener { dialog.dismiss(); renameItem(pane, f) }
        }
        titleRow.addView(titleTv)
        titleRow.addView(pencil)
        container.addView(titleRow)
        // v1.9.0（Max 15:26 纠正，最终版）：更多方式只放 ⋮ 菜单之外的额外功能。
        // v1.9.1（Max 16:41）：再加 创建快捷方式，共 9 个。
        val buttons = listOf(
            MoreAction(R.string.compress_item, R.drawable.ic_archive, true) { compressItem(pane, f) },
            MoreAction(R.string.share_zip, R.drawable.ic_share, true) { shareAsZip(pane, f) },
            MoreAction(if (pinned) R.string.unpin_folder else R.string.pin_folder, R.drawable.ic_pin, isDir) { pinToggle(pane, f) },
            MoreAction(if (hidden) R.string.unhide else R.string.hide, R.drawable.ic_hide, true) { hideToggle(pane, f) },
            MoreAction(R.string.bookmarks_title, R.drawable.ic_bookmark, true) { bookmarkToggle(pane, f) },
            MoreAction(R.string.encrypt_file, R.drawable.ic_shield, !isDir && !isEnc) { encryptItem(pane, f) },
            MoreAction(R.string.decrypt_file, R.drawable.ic_shield, !isDir && isEnc) { decryptItem(pane, f) },
            MoreAction(R.string.extract, R.drawable.ic_archive, !isDir && isZip) { unzipItem(pane, f) },
            MoreAction(R.string.create_shortcut, R.drawable.ic_shortcut, true) { showCreateShortcutDialog(pane, f) }
        )
        for (i in buttons.indices step 2) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (j in 0..1) {
                val idx = i + j
                if (idx >= buttons.size) break
                val b = buttons[idx]
                val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonTonalStyle).apply {
                    text = getString(b.labelRes)
                    icon = AppCompatResources.getDrawable(this@MainActivity, b.iconRes)
                    iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                    isEnabled = b.enabled
                    alpha = if (b.enabled) 1f else 0.45f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        val m = (4 * density).toInt()
                        setMargins(m, m, m, m)
                    }
                    setOnClickListener { dialog.dismiss(); b.run() }
                }
                row.addView(btn)
            }
            container.addView(row)
        }
        val scroll = ScrollView(this).apply { addView(container) }
        dialog = glassDialogBuilder().setView(scroll).create()
        dialog.show()
    }

    // ---------- v1.9.1：创建快捷方式（照 MT 图4–图6） ----------

    /** 创建快捷方式对话框：图标 + 名称输入框 + 类型下拉 + 执行操作下拉，
     *  底部只有取消/确定（不要管理按钮；执行操作里不要执行脚本文件）。 */
    private fun showCreateShortcutDialog(pane: Pane, f: File) {
        val isDir: Boolean
        val name: String
        when {
            pane.safMode -> {
                val e = pane.safEntries.firstOrNull { it.keyPath == f.absolutePath } ?: return
                isDir = e.isDir; name = e.name
            }
            pane.shizukuMode -> {
                val e = pane.shizukuEntries.firstOrNull { it.path == f.absolutePath } ?: return
                isDir = e.isDir; name = e.name
            }
            else -> { isDir = f.isDirectory; name = f.name }
        }
        val density = resources.displayMetrics.density
        val pad = (20 * density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, 0, pad, 0)
        }
        fun label(res: Int): TextView = TextView(this).apply {
            text = getString(res)
            textSize = 16f
            setPadding(0, (12 * density).toInt(), 0, (4 * density).toInt())
        }
        // 图标行：文件类型图标放在圆角色块里（和列表行同款）。
        container.addView(label(R.string.shortcut_icon))
        val iconRes = if (isDir) R.drawable.ic_folder else FileUtils.iconResFor(f)
        val tileRes = if (isDir) R.color.tile_folder else FileUtils.tileColorResFor(f)
        val iconView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((56 * density).toInt(), (56 * density).toInt())
            val bg = GradientDrawable()
            bg.cornerRadius = 14f * density
            bg.setColor(ContextCompat.getColor(this@MainActivity, tileRes))
            background = bg
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            val ip = (12 * density).toInt()
            setPadding(ip, ip, ip, ip)
        }
        container.addView(iconView)
        // 名称输入框：默认填当前名字，全选方便改。
        container.addView(label(R.string.shortcut_name))
        val nameInput = EditText(this).apply { setText(name); selectAll() }
        container.addView(nameInput)
        // 类型下拉：桌面快捷方式 / 图标快捷方式（点开 PopupMenu）。
        container.addView(label(R.string.shortcut_type))
        var typeIcon = false // false=桌面快捷方式，true=图标快捷方式
        val typeField = TextView(this).apply {
            text = getString(R.string.shortcut_type_desktop)
            textSize = 16f
            setPadding(0, (4 * density).toInt(), 0, 0)
        }
        val typeSub = TextView(this).apply {
            text = getString(R.string.shortcut_type_desktop_desc)
            textSize = 13f
            setTextColor(Color.GRAY)
            setPadding(0, (2 * density).toInt(), 0, 0)
        }
        val typeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setOnClickListener {
                val pm = PopupMenu(this@MainActivity, typeField)
                pm.menu.add(0, 0, 0, R.string.shortcut_type_desktop)
                pm.menu.add(0, 1, 1, R.string.shortcut_type_icon)
                pm.setOnMenuItemClickListener { item ->
                    typeIcon = item.itemId == 1
                    typeField.text = getString(if (typeIcon) R.string.shortcut_type_icon else R.string.shortcut_type_desktop)
                    typeSub.text = getString(if (typeIcon) R.string.shortcut_type_icon_desc else R.string.shortcut_type_desktop_desc)
                    true
                }
                pm.show()
            }
            addView(typeField, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(this@MainActivity).apply { text = "▼"; textSize = 14f })
        }
        container.addView(typeRow)
        container.addView(typeSub)
        // 执行操作下拉：只留 4 项（不要执行脚本文件）。
        container.addView(label(R.string.shortcut_action))
        val actions = listOf(
            R.string.shortcut_action_locate to SHORTCUT_LOCATE,
            R.string.shortcut_action_locate_click to SHORTCUT_LOCATE_OPEN,
            R.string.shortcut_action_open_editor to SHORTCUT_OPEN_EDITOR,
            R.string.shortcut_action_preview_html to SHORTCUT_PREVIEW_HTML
        )
        var selectedAction = SHORTCUT_LOCATE
        val actionField = TextView(this).apply {
            text = getString(R.string.shortcut_action_locate)
            textSize = 16f
            setPadding(0, (4 * density).toInt(), 0, 0)
        }
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setOnClickListener {
                val pm = PopupMenu(this@MainActivity, actionField)
                actions.forEachIndexed { i, (res, _) -> pm.menu.add(0, i, i, res) }
                pm.setOnMenuItemClickListener { item ->
                    val (res, code) = actions[item.itemId]
                    selectedAction = code
                    actionField.text = getString(res)
                    true
                }
                pm.show()
            }
            addView(actionField, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(this@MainActivity).apply { text = "▼"; textSize = 14f })
        }
        container.addView(actionRow)
        // 底部只有取消/确定（自定义按钮，名字为空时不关框）。
        val dlg = MaterialAlertDialogBuilder(this).setTitle(R.string.create_shortcut).setView(container).create()
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, (16 * density).toInt(), 0, 0)
        }
        fun textButton(res: Int, onClick: () -> Unit): android.widget.Button =
            // 和 showNewDialog 同款：MaterialButton 在 1.13 里没有四参构造，
            // 用 Button 的四参构造套 Material3 TextButton 样式。
            android.widget.Button(
                this, null, 0,
                com.google.android.material.R.style.Widget_Material3_Button_TextButton_Dialog
            ).apply {
                text = getString(res)
                setOnClickListener { onClick() }
            }
        btnRow.addView(textButton(R.string.cancel) { dlg.dismiss() })
        btnRow.addView(textButton(R.string.confirm) {
            val label = nameInput.text.toString().trim()
            if (label.isEmpty()) {
                Toast.makeText(this, R.string.invalid_name, Toast.LENGTH_SHORT).show()
                return@textButton
            }
            dlg.dismiss()
            pinShortcut(f.absolutePath, label, isDir, typeIcon, selectedAction)
        })
        container.addView(btnRow)
        dlg.show()
    }

    /** 生成桌面快捷方式（ShortcutManager.requestPinShortcut）。 */
    private fun pinShortcut(path: String, label: String, isDir: Boolean, useTypeIcon: Boolean, action: Int) {
        try {
            val sm = getSystemService(ShortcutManager::class.java)
            if (sm == null || !sm.isRequestPinShortcutSupported) {
                Toast.makeText(this, R.string.shortcut_not_supported, Toast.LENGTH_SHORT).show()
                return
            }
            val intent = Intent(this, MainActivity::class.java).apply {
                this.action = ACTION_SHORTCUT
                putExtra(EXTRA_SHORTCUT_PATH, path)
                putExtra(EXTRA_SHORTCUT_ACTION, action)
                putExtra(EXTRA_SHORTCUT_IS_DIR, isDir)
            }
            val icon = if (!useTypeIcon) {
                Icon.createWithResource(this, R.drawable.ic_app_icon)
            } else {
                Icon.createWithBitmap(shortcutTypeIconBitmap(File(path), isDir))
            }
            val info = ShortcutInfo.Builder(this, "fairu_" + System.currentTimeMillis())
                .setShortLabel(label)
                .setIcon(icon)
                .setIntent(intent)
                .build()
            sm.requestPinShortcut(info, null)
            Toast.makeText(this, R.string.shortcut_created, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.shortcut_not_supported, Toast.LENGTH_SHORT).show()
        }
    }

    /** 图标快捷方式用的位图：圆角色块底 + 白色文件类型图标（和列表行同款）。 */
    private fun shortcutTypeIconBitmap(f: File, isDir: Boolean): Bitmap {
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val tileRes = if (isDir) R.color.tile_folder else FileUtils.tileColorResFor(f)
        val bg = GradientDrawable()
        bg.cornerRadius = size * 0.22f
        bg.setColor(ContextCompat.getColor(this, tileRes))
        bg.setBounds(0, 0, size, size)
        bg.draw(canvas)
        val iconRes = if (isDir) R.drawable.ic_folder else FileUtils.iconResFor(f)
        AppCompatResources.getDrawable(this, iconRes)?.let { d ->
            val dr = DrawableCompat.wrap(d)
            DrawableCompat.setTint(dr, Color.WHITE)
            val pad = size / 4
            dr.setBounds(pad, pad, size - pad, size - pad)
            dr.draw(canvas)
        }
        return bmp
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::left.isInitialized) handleShortcutIntent(intent)
    }

    /** 处理桌面快捷方式点进来的 Intent：进到文件所在目录，再按动作执行。
     *  进不了目录或文件没了就弹明确提示，不闪退。 */
    private fun handleShortcutIntent(intent: Intent) {
        if (intent.action != ACTION_SHORTCUT) return
        val path = intent.getStringExtra(EXTRA_SHORTCUT_PATH) ?: return
        val action = intent.getIntExtra(EXTRA_SHORTCUT_ACTION, SHORTCUT_LOCATE)
        val isDir = intent.getBooleanExtra(EXTRA_SHORTCUT_IS_DIR, false)
        val target = File(path)
        val p = activePane()
        exitSafMode(p)
        exitShizukuMode(p)
        if (isDir) {
            if (!target.isDirectory) {
                Toast.makeText(this, R.string.shortcut_target_missing, Toast.LENGTH_SHORT).show()
                return
            }
            if (action == SHORTCUT_LOCATE) {
                val parent = target.parentFile
                if (parent == null || !parent.isDirectory) {
                    Toast.makeText(this, R.string.shortcut_target_missing, Toast.LENGTH_SHORT).show()
                    return
                }
                navigateTo(p, parent)
            } else {
                navigateTo(p, target)
            }
            return
        }
        if (!target.isFile) {
            Toast.makeText(this, R.string.shortcut_target_missing, Toast.LENGTH_SHORT).show()
            return
        }
        val parent = target.parentFile
        if (parent == null || !parent.isDirectory) {
            Toast.makeText(this, R.string.shortcut_target_missing, Toast.LENGTH_SHORT).show()
            return
        }
        navigateTo(p, parent)
        when (action) {
            SHORTCUT_LOCATE_OPEN -> onFileClick(p, target)
            SHORTCUT_OPEN_EDITOR -> {
                if (FileUtils.isText(target)) {
                    startActivity(Intent(this, TextEditorActivity::class.java).apply {
                        putExtra(TextEditorActivity.EXTRA_PATH, target.absolutePath)
                    })
                } else {
                    Toast.makeText(this, R.string.shortcut_not_text, Toast.LENGTH_SHORT).show()
                    openWith(target)
                }
            }
            SHORTCUT_PREVIEW_HTML -> {
                if (FileUtils.ext(target) == "html" || FileUtils.ext(target) == "htm") {
                    try {
                        val uri = FairuFileProvider.uriFor(target)
                        val view = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "text/html")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(view, getString(R.string.open_with)))
                    } catch (_: Exception) {
                        Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    openWith(target)
                }
            }
            else -> { /* 定位所在位置：已进到父目录 */ }
        }
    }

    private fun renameItem(pane: Pane, f: File) {
        when {
            pane.safMode -> pane.safEntries.firstOrNull { it.keyPath == f.absolutePath }?.let { showSafRenameDialog(pane, it) }
            pane.shizukuMode -> pane.shizukuEntries.firstOrNull { it.path == f.absolutePath }?.let { showShizukuRenameDialog(pane, it) }
            else -> showRenameDialog(pane, f)
        }
    }

    private fun isDirOf(pane: Pane, f: File): Boolean = when {
        pane.safMode -> pane.safEntries.firstOrNull { it.keyPath == f.absolutePath }?.isDir ?: false
        pane.shizukuMode -> pane.shizukuEntries.firstOrNull { it.path == f.absolutePath }?.isDir ?: false
        else -> f.isDirectory
    }

    /** 隐藏 / 取消隐藏：改名加 "." 前缀（已隐藏的去掉 "."）。 */
    private fun hideToggle(pane: Pane, f: File) {
        val name = f.name
        val newName = if (name.startsWith(".")) name.removePrefix(".") else ".$name"
        if (newName.isBlank() || newName == ".") {
            Toast.makeText(this, R.string.invalid_name, Toast.LENGTH_SHORT).show()
            return
        }
        val wasHidden = name.startsWith(".")
        when {
            pane.safMode -> {
                val entry = pane.safEntries.firstOrNull { it.keyPath == f.absolutePath } ?: return
                runSafOpTimeout(30000L, block = { safRenameDocument(entry.uri, newName) }, onDone = { ok ->
                    if (ok) {
                        snackbar(if (wasHidden) R.string.unhidden_done else R.string.hidden_done)
                        refreshSafList(pane)
                    } else snackbar(R.string.error)
                })
            }
            pane.shizukuMode -> {
                val parent = f.parent ?: "/"
                val dst = if (parent == "/") "/$newName" else "$parent/$newName"
                Thread {
                    val r = ShizukuRoot.exec("mv", f.absolutePath, dst)
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        if (r.exit == 0) {
                            snackbar(if (wasHidden) R.string.unhidden_done else R.string.hidden_done)
                            refreshShizukuList(pane)
                        } else snackbar(R.string.error)
                    }
                }.start()
            }
            else -> {
                val ok = f.renameTo(File(f.parentFile, newName))
                if (ok) {
                    snackbar(if (wasHidden) R.string.unhidden_done else R.string.hidden_done)
                    loadPane(pane)
                } else snackbar(R.string.error)
            }
        }
    }

    /** Pin folder：置顶到列表最前面（带图钉标记），再点取消置顶。仅文件夹。 */
    private fun pinToggle(pane: Pane, f: File) {
        val nowPinned = PinnedFolders.toggle(this, f.absolutePath)
        Toast.makeText(this, if (nowPinned) R.string.pinned else R.string.unpinned, Toast.LENGTH_SHORT).show()
        loadPane(pane)
    }

    // ---------- v1.9.0：文件加密 / 解密、解压、书签 ----------

    /** 文件加密（仅文件）：弹密码框（输两遍防手误），AES 加密生成 .enc 文件，
     *  成功后删原文件。密码错只影响解密，加密本身不校验密码。 */
    private fun encryptItem(pane: Pane, f: File) {
        val pass = EditText(this).apply {
            hint = getString(R.string.password_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val pass2 = EditText(this).apply {
            hint = getString(R.string.password_confirm_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
            addView(pass)
            addView(pass2)
        }
        MaterialAlertDialogBuilder(this).setTitle(R.string.encrypt_file).setIcon(R.drawable.ic_shield).setView(container)
            .setPositiveButton(R.string.ok) { _, _ ->
                val p1 = pass.text.toString()
                val p2 = pass2.text.toString()
                when {
                    p1.isEmpty() -> Toast.makeText(this, R.string.password_hint, Toast.LENGTH_SHORT).show()
                    p1 != p2 -> Toast.makeText(this, R.string.password_mismatch, Toast.LENGTH_SHORT).show()
                    else -> Thread { doEncrypt(pane, f, p1) }.start()
                }
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun doEncrypt(pane: Pane, f: File, password: String) {
        var ok = false
        try {
            when {
                pane.shizukuMode -> {
                    val staged = stageRootFile(f.absolutePath)
                    if (staged != null) {
                        // 加密要在输入文件旁边新建 .enc：/data/local/tmp/fairu_stage
                        // 是 shell 建的目录，App 在里面建不了新文件；先拷到外置
                        // 暂存区（App 可写、shell 可读）再加密。
                        val workDir = File(sdcardStageDir(), "enc_${System.currentTimeMillis()}").apply { mkdirs() }
                        val work = File(workDir, f.name)
                        try {
                            FileUtils.copyRecursive(staged, work)
                            val enc = CryptoUtils.encrypt(work, password)
                            val dst = uniqueRootName(f.parent ?: "/", enc.name)
                            ok = shizukuCopy(enc.absolutePath, dst)
                            if (ok) ShizukuRoot.exec("rm", "-rf", f.absolutePath)
                        } catch (e: Exception) { Log.w(TAG, "root encrypt failed: $e") }
                        try { FileUtils.deleteRecursive(workDir) } catch (_: Exception) { }
                        try { FileUtils.deleteRecursive(staged) } catch (_: Exception) { }
                        if (staged.exists()) try { ShizukuRoot.exec("rm", "-rf", staged.absolutePath) } catch (_: Exception) { }
                    }
                }
                pane.safMode -> {
                    val entry = pane.safEntries.firstOrNull { it.keyPath == f.absolutePath }
                    val treeUri = pane.safStorage?.let { try { Uri.parse(it.uri) } catch (_: Exception) { null } }
                    val parentDocId = pane.safStack.lastOrNull()?.docId
                    if (entry != null && treeUri != null && parentDocId != null) {
                        val tmpDir = File(cacheDir, "enc_stage").apply { FileUtils.deleteRecursive(this); mkdirs() }
                        try {
                            val copied = copySafToFile(ClipEntry.ofSaf(entry, treeUri, parentDocId), tmpDir)
                            val staged = File(tmpDir, entry.name)
                            if (copied && staged.exists()) {
                                val enc = CryptoUtils.encrypt(staged, password)
                                val docUri = safCreateFile(treeUri, parentDocId, enc.name, "application/octet-stream")
                                if (docUri != null) {
                                    contentResolver.openOutputStream(docUri, "w")?.use { out ->
                                        enc.inputStream().use { it.copyTo(out) }
                                    }
                                    ok = true
                                    safDeleteClipEntry(ClipEntry.ofSaf(entry, treeUri, parentDocId))
                                }
                                try { enc.delete() } catch (_: Exception) { }
                            }
                        } catch (e: Exception) { Log.w(TAG, "saf encrypt failed: $e") }
                        try { FileUtils.deleteRecursive(tmpDir) } catch (_: Exception) { }
                    }
                }
                else -> {
                    try { CryptoUtils.encrypt(f, password); ok = true }
                    catch (e: Exception) { Log.w(TAG, "encrypt failed: $e") }
                }
            }
        } catch (e: Exception) { Log.w(TAG, "encrypt failed: $e") }
        val resultOk = ok
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            if (resultOk) { snackbar(R.string.encrypted); refreshPane(pane) }
            else snackbar(R.string.encrypt_failed)
        }
    }

    /** 文件解密（仅 .enc 文件）：弹密码框，解密回原名，成功后删 .enc；
     *  密码错提示且不动文件。 */
    private fun decryptItem(pane: Pane, f: File) {
        val pass = EditText(this).apply {
            hint = getString(R.string.password_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        MaterialAlertDialogBuilder(this).setTitle(R.string.decrypt_file).setIcon(R.drawable.ic_shield).setView(pass)
            .setPositiveButton(R.string.ok) { _, _ ->
                val p = pass.text.toString()
                if (p.isEmpty()) {
                    Toast.makeText(this, R.string.password_hint, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                Thread { doDecrypt(pane, f, p) }.start()
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun doDecrypt(pane: Pane, f: File, password: String) {
        var ok = false
        var wrongPassword = false
        try {
            when {
                pane.shizukuMode -> {
                    val staged = stageRootFile(f.absolutePath)
                    if (staged != null) {
                        val workDir = File(sdcardStageDir(), "dec_${System.currentTimeMillis()}").apply { mkdirs() }
                        val work = File(workDir, f.name)
                        try {
                            FileUtils.copyRecursive(staged, work)
                            val out = try {
                                CryptoUtils.decrypt(work, password)
                            } catch (e: BadPaddingException) {
                                wrongPassword = true
                                null
                            }
                            if (out != null) {
                                val dst = uniqueRootName(f.parent ?: "/", out.name)
                                ok = shizukuCopy(out.absolutePath, dst)
                                if (ok) ShizukuRoot.exec("rm", "-rf", f.absolutePath)
                            }
                        } catch (e: Exception) { Log.w(TAG, "root decrypt failed: $e") }
                        try { FileUtils.deleteRecursive(workDir) } catch (_: Exception) { }
                        try { FileUtils.deleteRecursive(staged) } catch (_: Exception) { }
                        if (staged.exists()) try { ShizukuRoot.exec("rm", "-rf", staged.absolutePath) } catch (_: Exception) { }
                    }
                }
                pane.safMode -> {
                    val entry = pane.safEntries.firstOrNull { it.keyPath == f.absolutePath }
                    val treeUri = pane.safStorage?.let { try { Uri.parse(it.uri) } catch (_: Exception) { null } }
                    val parentDocId = pane.safStack.lastOrNull()?.docId
                    if (entry != null && treeUri != null && parentDocId != null) {
                        val tmpDir = File(cacheDir, "dec_stage").apply { FileUtils.deleteRecursive(this); mkdirs() }
                        try {
                            val copied = copySafToFile(ClipEntry.ofSaf(entry, treeUri, parentDocId), tmpDir)
                            val staged = File(tmpDir, entry.name)
                            if (copied && staged.exists()) {
                                val out = try {
                                    CryptoUtils.decrypt(staged, password)
                                } catch (e: BadPaddingException) {
                                    wrongPassword = true
                                    null
                                }
                                if (out != null) {
                                    val docUri = safCreateFile(treeUri, parentDocId, out.name, safMimeFor(out.name))
                                    if (docUri != null) {
                                        contentResolver.openOutputStream(docUri, "w")?.use { o ->
                                            out.inputStream().use { it.copyTo(o) }
                                        }
                                        ok = true
                                        safDeleteClipEntry(ClipEntry.ofSaf(entry, treeUri, parentDocId))
                                    }
                                    try { out.delete() } catch (_: Exception) { }
                                }
                            }
                        } catch (e: Exception) { Log.w(TAG, "saf decrypt failed: $e") }
                        try { FileUtils.deleteRecursive(tmpDir) } catch (_: Exception) { }
                    }
                }
                else -> {
                    try {
                        CryptoUtils.decrypt(f, password)
                        ok = true
                    } catch (e: BadPaddingException) {
                        wrongPassword = true
                    } catch (e: Exception) { Log.w(TAG, "decrypt failed: $e") }
                }
            }
        } catch (e: Exception) { Log.w(TAG, "decrypt failed: $e") }
        val resultOk = ok
        val resultWrong = wrongPassword
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            when {
                resultOk -> { snackbar(R.string.decrypted); refreshPane(pane) }
                resultWrong -> Toast.makeText(this, R.string.wrong_password, Toast.LENGTH_SHORT).show()
                else -> snackbar(R.string.decrypt_failed)
            }
        }
    }

    /** 解压（仅 .zip）：解到同名文件夹里（重名加 (1)）。 */
    private fun unzipItem(pane: Pane, f: File) {
        Thread {
            var ok = false
            try {
                when {
                    pane.safMode -> {
                        val entry = pane.safEntries.firstOrNull { it.keyPath == f.absolutePath }
                        val treeUri = pane.safStorage?.let { try { Uri.parse(it.uri) } catch (_: Exception) { null } }
                        val parentDocId = pane.safStack.lastOrNull()?.docId
                        if (entry != null && treeUri != null && parentDocId != null) {
                            val dirName = f.name.removeSuffix(".zip")
                            val dirId = safCreateDir(treeUri, parentDocId, dirName)
                            if (dirId != null) {
                                ok = true
                                val rootId: String = dirId
                                try {
                                    contentResolver.openInputStream(entry.uri)?.use { input ->
                                        java.util.zip.ZipInputStream(java.io.BufferedInputStream(input)).use { zis ->
                                            var e = zis.nextEntry
                                            while (e != null) {
                                                // 只接受文件夹内的相对路径，防 zip-slip。
                                                val clean = e.name.trimStart('/').replace("..", "")
                                                if (clean.isNotBlank() && !e.isDirectory) {
                                                    val parts = clean.split('/').filter { it.isNotEmpty() }
                                                    var curId = rootId
                                                    for (k in 0 until parts.size - 1) {
                                                        val nextId = safCreateDir(treeUri, curId, parts[k]) ?: break
                                                        curId = nextId
                                                    }
                                                    val fileName = parts.last()
                                                    val docUri = safCreateFile(treeUri, curId, fileName, safMimeFor(fileName))
                                                    if (docUri != null) {
                                                        contentResolver.openOutputStream(docUri, "w")?.use { out -> zis.copyTo(out) }
                                                    }
                                                }
                                                zis.closeEntry()
                                                e = zis.nextEntry
                                            }
                                        }
                                    }
                                } catch (e: Exception) { Log.w(TAG, "saf unzip failed: $e"); ok = false }
                            }
                        }
                    }
                    pane.shizukuMode -> {
                        val staged = stageRootFile(f.absolutePath)
                        if (staged != null) {
                            val destDir = uniqueRootName(f.parent ?: "/", f.name.removeSuffix(".zip"))
                            ShizukuRoot.exec("mkdir", "-p", destDir)
                            // 解压中转放外置储存暂存区（App 可写、shell 可读），
                            // 再经 Shizuku cp 回根目录。
                            val tmpDir = File(sdcardStageDir(), "unzip_${System.currentTimeMillis()}").apply { mkdirs() }
                            try {
                                FileUtils.unzip(staged, tmpDir)
                                tmpDir.listFiles()?.forEach { child ->
                                    shizukuCopy(child.absolutePath, "$destDir/${child.name}")
                                }
                                ok = true
                            } catch (e: Exception) { Log.w(TAG, "root unzip failed: $e") }
                            try { FileUtils.deleteRecursive(tmpDir) } catch (_: Exception) { }
                            try { FileUtils.deleteRecursive(staged) } catch (_: Exception) { }
                            if (staged.exists()) try { ShizukuRoot.exec("rm", "-rf", staged.absolutePath) } catch (_: Exception) { }
                        }
                    }
                    else -> {
                        val destDir = uniqueFileName(pane.currentDir, f.name.removeSuffix(".zip"))
                        try { FileUtils.unzip(f, destDir); ok = true } catch (e: Exception) { Log.w(TAG, "unzip failed: $e") }
                    }
                }
            } catch (e: Exception) { Log.w(TAG, "unzip failed: $e") }
            val resultOk = ok
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (resultOk) { snackbar(R.string.extracted); refreshPane(pane) }
                else snackbar(R.string.error)
            }
        }.start()
    }

    // ---------- v1.9.0：书签（抽屉分组） ----------

    private fun bookmarkKind(pane: Pane): String = when {
        pane.safMode -> "saf"
        pane.shizukuMode -> "root"
        else -> "file"
    }

    private fun bookmarkTarget(pane: Pane, f: File): String = when {
        pane.safMode -> pane.safEntries.firstOrNull { it.keyPath == f.absolutePath }?.uri?.toString() ?: f.absolutePath
        else -> f.absolutePath
    }

    /** 书签开关：没加过就加到抽屉书签分组，加过就取消。 */
    private fun bookmarkToggle(pane: Pane, f: File) {
        val target = bookmarkTarget(pane, f)
        val kind = bookmarkKind(pane)
        if (Bookmarks.contains(this, target, kind)) {
            Bookmarks.remove(this, target, kind)
            Toast.makeText(this, R.string.bookmark_removed, Toast.LENGTH_SHORT).show()
        } else {
            Bookmarks.add(this, f.name, target, isDirOf(pane, f), kind)
            Toast.makeText(this, R.string.bookmark_added, Toast.LENGTH_SHORT).show()
        }
        refreshBookmarks()
    }

    /** 抽屉里的书签分组（没有书签时整个分组隐藏）。 */
    private fun refreshBookmarks() {
        if (!::drawerLayout.isInitialized || !::prefs.isInitialized) return
        val container = findViewById<LinearLayout>(R.id.drawerBookmarksContainer) ?: return
        val title = findViewById<TextView>(R.id.drawerBookmarksTitle)
        val list = Bookmarks.list(this)
        title?.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        container.removeAllViews()
        for (b in list) {
            val row = layoutInflater.inflate(R.layout.item_drawer_storage, container, false)
            row.findViewById<TextView>(R.id.storageName).text = b.name
            row.findViewById<TextView>(R.id.storagePath).text = b.target
            // item_drawer_storage 的图标没有 id：第一个子 View 就是它，换成书签图标。
            try {
                val icon = (row as? android.view.ViewGroup)?.getChildAt(0) as? ImageView
                icon?.setImageResource(R.drawable.ic_bookmark)
            } catch (_: Exception) { }
            row.setOnClickListener {
                drawerLayout.closeDrawer(GravityCompat.START)
                openBookmark(b)
            }
            row.setOnLongClickListener { showBookmarkMenu(it, b); true }
            container.addView(row)
        }
    }

    /** 长按书签：删掉这条书签（只删记录，不动文件本身）。 */
    private fun showBookmarkMenu(anchor: View, bookmark: Bookmarks.Bookmark) {
        val popup = PopupMenu(this, anchor)
        popup.setForceShowIcon(true)
        popup.menu.add(0, 1, 0, R.string.delete).icon = tintedIcon(R.drawable.ic_drawer_trash)
        popup.setOnMenuItemClickListener {
            if (it.itemId == 1) {
                Bookmarks.remove(this, bookmark.target, bookmark.kind)
                refreshBookmarks()
                Toast.makeText(this, R.string.bookmark_removed, Toast.LENGTH_SHORT).show()
            }
            true
        }
        popup.show()
    }

    /** 点书签跳到对应位置：文件夹进目录，文件打开。 */
    private fun openBookmark(b: Bookmarks.Bookmark) {
        when (b.kind) {
            "root" -> {
                if (!ShizukuRoot.isRunning() || !ShizukuRoot.isAuthorized()) {
                    openRootViaShizuku()
                    return
                }
                val p = activePane()
                exitSafMode(p)
                p.shizukuMode = true
                p.backStack.clear()
                p.forwardStack.clear()
                p.selected.clear()
                p.searchQuery = ""
                if (b.isDir) {
                    p.currentDir = File(b.target)
                    refreshShizukuList(p)
                } else {
                    val parent = File(b.target).parent ?: "/"
                    p.currentDir = File(parent)
                    ShizukuRoot.listDir(parent) { entries, _ ->
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            if (entries != null) {
                                p.shizukuEntries = entries
                                loadPane(p)
                                updateTopBar()
                                entries.firstOrNull { it.path == b.target }?.let { openRootFile(p, it) }
                            } else refreshShizukuList(p)
                        }
                    }
                }
            }
            "saf" -> {
                val uri = try { Uri.parse(b.target) } catch (_: Exception) { null } ?: return
                val treeUri = safTreeUriOf(uri) ?: return
                if (b.isDir) {
                    val docId = try { DocumentsContract.getDocumentId(uri) } catch (_: Exception) { return }
                    val p = activePane()
                    exitShizukuMode(p)
                    p.safMode = true
                    p.safStorage = CustomStorages.Storage(b.name, "", false, treeUri.toString())
                    p.safStack = mutableListOf(SafDir(docId, b.name))
                    p.safBackStack.clear()
                    p.safForwardStack.clear()
                    p.selected.clear()
                    p.searchQuery = ""
                    p.currentDir = File(safRootKey(treeUri))
                    refreshSafList(p)
                } else {
                    if (FileUtils.isText(File(b.name))) {
                        startActivity(Intent(this, TextEditorActivity::class.java).apply {
                            putExtra(TextEditorActivity.EXTRA_URI, uri.toString())
                        })
                    } else {
                        openSafFile(SafEntry(b.name, uri, false, 0L, 0L, b.target))
                    }
                }
            }
            else -> {
                val f = File(b.target)
                val p = activePane()
                if (b.isDir) {
                    navigateDrawerTo(f)
                } else {
                    val parent = f.parentFile
                    if (parent == null || !parent.isDirectory) {
                        Toast.makeText(this, R.string.cannot_access, Toast.LENGTH_SHORT).show()
                        return
                    }
                    exitSafMode(p)
                    exitShizukuMode(p)
                    navigateTo(p, parent)
                    onFileClick(p, f)
                }
            }
        }
    }

    /** 分享选中的文件（多选顶栏的分享按钮和每行 ⋮ 菜单的分享都走这儿）：
     *  普通文件走 FileProvider，SAF 文档直接用 content URI，根目录文件
     *  先经 Shizuku cp 到缓存里暂存再分享；文件夹不能分享（跳过，选中的
     *  全是文件夹就弹“出错了”）。 */
    private fun shareFiles(pane: Pane, targets: List<File>) {
        if (targets.isEmpty()) return
        Thread {
            val uris = mutableListOf<Uri>()
            var skipped = false
            targets.forEach { f ->
                when {
                    pane.shizukuMode -> {
                        val entry = pane.shizukuEntries.firstOrNull { it.path == f.absolutePath }
                        if (entry == null || entry.isDir) { skipped = true; return@forEach }
                        val staged = stageRootFile(entry.path)
                        if (staged == null) skipped = true
                        else try { uris.add(FairuFileProvider.uriFor(staged)) } catch (_: Exception) { skipped = true }
                    }
                    pane.safMode -> {
                        val e = pane.safEntries.firstOrNull { it.keyPath == f.absolutePath }
                        if (e == null || e.isDir) skipped = true else uris.add(e.uri)
                    }
                    else -> {
                        if (f.isDirectory) { skipped = true; return@forEach }
                        try { uris.add(FairuFileProvider.uriFor(f)) } catch (_: Exception) { skipped = true }
                    }
                }
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (uris.isEmpty()) {
                    Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                if (skipped) Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
                val intent = Intent(
                    if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
                ).apply {
                    type = "*/*"
                    if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris[0])
                    else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    startActivity(Intent.createChooser(intent, getString(R.string.share)))
                } catch (_: Exception) {
                    Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    /** SAF 项属性：名字、大小（文件夹递归算）、修改时间、位置（储存面包屑）。 */
    private fun showSafProperties(pane: Pane, entry: SafEntry) {
        val location = safBreadcrumb(pane) + "/" + entry.name
        if (entry.isDir) {
            Thread {
                val size = safDirSize(entry.uri)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    showSafPropertiesDialog(entry, location, size)
                }
            }.start()
        } else {
            showSafPropertiesDialog(entry, location, entry.size)
        }
    }

    private fun showSafPropertiesDialog(entry: SafEntry, location: String, size: Long) {
        val msg = "${getString(R.string.name)}: ${entry.name}\n${getString(R.string.path)}: $location\n" +
            "${getString(R.string.size)}: ${FileUtils.formatSize(size)}\n${getString(R.string.modified)}: ${FileUtils.formatDate(entry.modified)}\n" +
            "${getString(R.string.file)}: ${if (entry.isDir) getString(R.string.folder) else getString(R.string.file)}"
        MaterialAlertDialogBuilder(this).setTitle(R.string.properties).setMessage(msg)
            .setPositiveButton(R.string.ok, null).show()
    }

    private fun showProperties(f: File) {
        val size = if (f.isDirectory) FileUtils.dirSize(f) else f.length()
        val msg = "${getString(R.string.name)}: ${f.name}\n${getString(R.string.path)}: ${f.absolutePath}\n" +
            "${getString(R.string.size)}: ${FileUtils.formatSize(size)}\n${getString(R.string.modified)}: ${FileUtils.formatDate(f.lastModified())}\n" +
            "${getString(R.string.file)}: ${if (f.isDirectory) getString(R.string.folder) else getString(R.string.file)}"
        MaterialAlertDialogBuilder(this).setTitle(R.string.properties).setMessage(msg)
            .setPositiveButton(R.string.ok, null).show()
    }

    /** v1.8.0 根目录项属性：名字、路径、大小、修改时间、类型。文件夹大小
     *  用 du -sb 现算（读不了的目录算不出来就显示“未知”）；解析不上的
     *  条目（大小/日期未知）也显示“未知”，不要编一个 1970 年出来。 */
    private fun showShizukuProperties(pane: Pane, entry: ShizukuRoot.Entry) {
        if (entry.isDir) {
            Thread {
                val size = ShizukuRoot.exec("du", "-sb", entry.path, timeoutMs = 15000L).let { r ->
                    if (r.exit == 0) r.out.trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull() else null
                }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    showShizukuPropertiesDialog(entry, size)
                }
            }.start()
        } else {
            showShizukuPropertiesDialog(entry, entry.size)
        }
    }

    private fun showShizukuPropertiesDialog(entry: ShizukuRoot.Entry, size: Long?) {
        val unknown = entry.modified == 0L && size == null
        val sizeText = when {
            size != null -> FileUtils.formatSize(size)
            unknown -> getString(R.string.unknown)
            else -> FileUtils.formatSize(entry.size)
        }
        val modText = if (entry.modified == 0L) getString(R.string.unknown)
            else FileUtils.formatDate(entry.modified)
        val msg = "${getString(R.string.name)}: ${entry.name}\n${getString(R.string.path)}: ${entry.path}\n" +
            "${getString(R.string.size)}: $sizeText\n${getString(R.string.modified)}: $modText\n" +
            "${getString(R.string.file)}: ${if (entry.isDir) getString(R.string.folder) else getString(R.string.file)}"
        MaterialAlertDialogBuilder(this).setTitle(R.string.properties).setMessage(msg)
            .setPositiveButton(R.string.ok, null).show()
    }

    private fun openWith(f: File) {
        try {
            val uri = FairuFileProvider.uriFor(f)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, FileUtils.mimeFor(f)); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.open_with)))
        } catch (e: Exception) { Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show() }
    }

    // ---------- Shizuku 根目录 ----------

    /** 点抽屉里的根目录：先确认 Shizuku 可用且已授权，再进根目录浏览。 */
    private fun openRootViaShizuku() {
        drawerLayout.closeDrawer(GravityCompat.START)
        if (!ShizukuRoot.isRunning()) {
            // 没装 Shizuku 或没启动：弹提示，可去下载，绝不闪退。
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.shizuku_needed_title)
                .setMessage(R.string.shizuku_needed_msg)
                .setPositiveButton(R.string.shizuku_download) { _, _ ->
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/")))
                    } catch (_: Exception) {
                        Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }
        if (!ShizukuRoot.isAuthorized()) {
            Toast.makeText(this, R.string.shizuku_request_msg, Toast.LENGTH_SHORT).show()
            ShizukuRoot.requestPermission { granted ->
                if (granted) enterShizukuRoot()
                else Toast.makeText(this, R.string.shizuku_denied, Toast.LENGTH_SHORT).show()
            }
            return
        }
        enterShizukuRoot()
    }

    private fun enterShizukuRoot() {
        val p = activePane()
        p.shizukuMode = true
        p.backStack.clear()
        p.forwardStack.clear()
        p.currentDir = File("/")
        p.searchQuery = ""
        p.selected.clear()
        refreshShizukuList(p)
    }

    /** 在 Shizuku 模式内进一个子目录（会把当前目录压入后退栈）。 */
    private fun enterShizukuDir(pane: Pane, path: String) {
        if (path == pane.currentDir.absolutePath) return
        pane.backStack.add(pane.currentDir)
        pane.forwardStack.clear()
        pane.currentDir = File(path)
        pane.searchQuery = ""
        refreshShizukuList(pane)
    }

    /** 用 Shizuku 的 ls 刷新当前窗格的根目录列表；失败弹真实错误，不闪退，
     *  也绝不静默显示空。
     *  v1.8.0：失败时把旧列表清空、把错误原因记到 pane.shizukuError 里，
     *  空列表上直接显示错误原因——以前失败时不刷新列表，窗格里还留着
     *  上个目录的旧条目，路径栏却是新目录，会误导人。 */
    private fun refreshShizukuList(pane: Pane) {
        ShizukuRoot.listDir(pane.currentDir.absolutePath) { entries, error ->
            if (isFinishing || isDestroyed) return@listDir
            if (!::left.isInitialized || !::right.isInitialized) return@listDir
            if (entries == null) {
                val detail = if (error == "parse") getString(R.string.shizuku_failed)
                    else if (!error.isNullOrBlank()) getString(R.string.shizuku_failed_detail, error)
                    else getString(R.string.shizuku_failed)
                pane.shizukuEntries = emptyList()
                pane.shizukuError = detail
                loadPane(pane)
                updateTopBar()
                Toast.makeText(this, detail, Toast.LENGTH_LONG).show()
                return@listDir
            }
            pane.shizukuEntries = entries
            pane.shizukuError = null
            loadPane(pane)
            updateTopBar()
        }
    }

    /** 退出 Shizuku 模式（切回普通目录前调）。 */
    private fun exitShizukuMode(pane: Pane) {
        if (!pane.shizukuMode) return
        pane.shizukuMode = false
        pane.shizukuEntries = emptyList()
        pane.shizukuError = null
        pane.backStack.clear()
        pane.forwardStack.clear()
        pane.selected.clear()
    }

    /** v1.8.0：根目录里点一个文件——App 自己读不了，先经 Shizuku cp 到
     *  缓存里暂存再打开。文本文件进内置编辑器（改完退出时写回原路径，
     *  见 rootEditorLauncher）；别的用 ACTION_VIEW 打开暂存副本。
     *  暂存失败（没权限读）弹“出错了”，不闪退。 */
    private fun openRootFile(pane: Pane, entry: ShizukuRoot.Entry) {
        Thread {
            val staged = stageRootFile(entry.path)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (staged == null) {
                    Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                if (FileUtils.isText(File(entry.name))) {
                    pendingRootEdit = RootEdit(entry.path, staged, staged.lastModified(), staged.length())
                    rootEditorLauncher.launch(Intent(this, TextEditorActivity::class.java).apply {
                        putExtra(TextEditorActivity.EXTRA_PATH, staged.absolutePath)
                    })
                    return@runOnUiThread
                }
                try {
                    val uri = FairuFileProvider.uriFor(staged)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, FileUtils.mimeFor(staged))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, getString(R.string.open_with)))
                } catch (_: Exception) {
                    Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    // ---------- SAF 浏览（File 读不到的已添加储存） ----------

    /** SAF 合成路径的根：/saf/<编码后的 tree URI>。只在内存里用，给
     *  FileAdapter 的覆盖表当 key，真实位置永远是 tree URI。 */
    private fun safRootKey(treeUri: Uri): String = "/saf/" + Uri.encode(treeUri.toString())

    /** 当前 SAF 目录的合成路径：根 key + 一路的显示名。 */
    private fun safKeyPath(pane: Pane): String {
        val storage = pane.safStorage ?: return "/saf"
        val root = safRootKey(Uri.parse(storage.uri))
        val names = pane.safStack.drop(1).map { it.name }
        return if (names.isEmpty()) root else root + "/" + names.joinToString("/")
    }

    /** SAF 面包屑：储存名/子目录/子目录，显示在标题栏和窗格路径行。 */
    private fun safBreadcrumb(pane: Pane): String =
        pane.safStack.joinToString("/") { it.name }

    /** 进一个 SAF 储存的根目录（抽屉里点它时调）。 */
    private fun enterSafRoot(storage: CustomStorages.Storage) {
        val treeUri = try { Uri.parse(storage.uri) } catch (_: Exception) { null }
        val treeDocId = try {
            if (treeUri == null) null else DocumentsContract.getTreeDocumentId(treeUri)
        } catch (_: Exception) { null }
        if (treeUri == null || treeDocId == null) {
            Toast.makeText(this, R.string.cannot_access, Toast.LENGTH_SHORT).show()
            return
        }
        val p = activePane()
        exitSafMode(p)
        exitShizukuMode(p)
        p.safMode = true
        p.safStorage = storage
        p.safStack = mutableListOf(SafDir(treeDocId, storage.name))
        p.safBackStack.clear()
        p.safForwardStack.clear()
        p.currentDir = File(safRootKey(treeUri))
        p.searchQuery = ""
        p.selected.clear()
        refreshSafList(p)
    }

    /** 退出 SAF 模式（切回普通目录 / 另一个储存前调）。 */
    private fun exitSafMode(pane: Pane) {
        if (!pane.safMode) return
        pane.safMode = false
        pane.safStorage = null
        pane.safStack.clear()
        pane.safBackStack.clear()
        pane.safForwardStack.clear()
        pane.safEntries = emptyList()
        pane.safUriByPath = emptyMap()
        pane.backStack.clear()
        pane.forwardStack.clear()
        pane.selected.clear()
    }

    /** 在 SAF 模式内进一个子目录（会把当前目录压入后退栈）。 */
    private fun enterSafDir(pane: Pane, entry: SafEntry) {
        val docId = try { DocumentsContract.getDocumentId(entry.uri) } catch (_: Exception) { null } ?: return
        pane.safBackStack.add(pane.safStack.toList())
        pane.safForwardStack.clear()
        pane.safStack.add(SafDir(docId, entry.name))
        pane.currentDir = File(entry.keyPath)
        pane.searchQuery = ""
        refreshSafList(pane)
    }

    /** 用 DocumentsContract 列出当前 SAF 目录；后台查，主线程刷新。
     *  失败弹真实错误，不闪退，也绝不静默显示空。 */
    private fun refreshSafList(pane: Pane) {
        val storage = pane.safStorage ?: return
        val treeUri = try { Uri.parse(storage.uri) } catch (_: Exception) { return }
        val dir = pane.safStack.lastOrNull() ?: return
        Thread {
            val raw = listSafChildren(treeUri, dir.docId)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (!::left.isInitialized || !::right.isInitialized) return@runOnUiThread
                // 用户可能已经切走了：只刷新仍在 SAF 模式且仍在同一目录的窗格。
                if (!pane.safMode || pane.safStorage != storage || pane.safStack.lastOrNull() != dir) return@runOnUiThread
                if (raw == null) {
                    Toast.makeText(this, R.string.cannot_access, Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                val base = safKeyPath(pane)
                pane.safEntries = raw.map { SafEntry(it.name, it.uri, it.isDir, it.size, it.modified, "$base/${it.name}") }
                pane.safUriByPath = pane.safEntries.associate { it.keyPath to it.uri }
                loadPane(pane)
                updateTopBar()
            }
        }.start()
    }

    /** 列一个 SAF 目录的子项：名字、是否文件夹、大小、修改时间。
     *  返回 null = 提供方出错 / 没权限（跟“空文件夹”区分开，空文件夹返回空列表）。
     *  直接用 DocumentsContract，不引 documentfile 库。 */
    private fun listSafChildren(treeUri: Uri, dirDocId: String): List<SafEntry>? {
        Log.d(TAG, "listSafChildren: dirDocId=$dirDocId")
        val childrenUri = try {
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirDocId)
        } catch (e: Exception) { Log.w(TAG, "listSafChildren build uri failed dirDocId=$dirDocId: $e"); return null }
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        val out = mutableListOf<SafEntry>()
        try {
            val cursor = contentResolver.query(childrenUri, projection, null, null, null)
                ?: run { Log.w(TAG, "listSafChildren: query returned null, dirDocId=$dirDocId"); return null }
            cursor.use { c ->
                val iId = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val iName = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val iMime = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val iSize = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val iMod = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (c.moveToNext()) {
                    val docId = c.getString(iId) ?: continue
                    val name = c.getString(iName) ?: continue
                    val mime = c.getString(iMime) ?: ""
                    val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    val size = if (iSize >= 0) c.getLong(iSize) else 0L
                    val mod = if (iMod >= 0) c.getLong(iMod) else 0L
                    val docUri = try {
                        DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    } catch (_: Exception) { continue }
                    out.add(SafEntry(name, docUri, isDir, size, mod, ""))
                }
            }
        } catch (e: Exception) { Log.w(TAG, "listSafChildren failed dirDocId=$dirDocId: $e"); return null }
        out.sortWith(compareBy({ !it.isDir }, { it.name.lowercase(Locale.US) }))
        Log.d(TAG, "listSafChildren: dirDocId=$dirDocId -> ${out.size} item(s)")
        return out
    }

    /** SAF 模式里点一个文件：用 ACTION_VIEW + content URI 打开（带读授权）。 */
    private fun openSafFile(entry: SafEntry) {
        try {
            val mime = try { contentResolver.getType(entry.uri) } catch (_: Exception) { null }
                ?: FileUtils.mimeFor(File(entry.name))
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(entry.uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.open_with)))
        } catch (e: Exception) { Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show() }
    }

    // ---------- SAF 写操作（v1.7.0：SAF 储存不再仅浏览） ----------
    // 项目里没有 androidx.documentfile 的 AAR（只引了 appcompat/material/
    // recyclerview/drawerlayout），所以直接调框架的 DocumentsContract——
    // DocumentFile 本身就是它的薄封装，行为完全一样，不多引依赖。

    /** 从一个 SAF 文档 URI 反推它所属的 tree URI（列子目录、建文档要用到）。 */
    private fun safTreeUriOf(docUri: Uri): Uri? {
        return try {
            val treeDocId = DocumentsContract.getTreeDocumentId(docUri)
            DocumentsContract.buildTreeDocumentUri(docUri.authority, treeDocId)
        } catch (_: Exception) { null }
    }

    /** SAF 目标目录里重名时自动改名：name (1).ext、name (2).ext……不覆盖。 */
    private fun safUniqueName(treeUri: Uri, parentDocId: String, name: String, isDir: Boolean): String {
        val existing = listSafChildren(treeUri, parentDocId)?.map { it.name }?.toSet() ?: return name
        if (!existing.contains(name)) return name
        val dot = if (isDir) -1 else name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (true) {
            val cand = "$base ($i)$ext"
            if (!existing.contains(cand)) return cand
            i++
        }
    }

    /** SAF 里建一个文件：返回新文档 URI，失败返回 null。 */
    private fun safCreateFile(treeUri: Uri, parentDocId: String, name: String, mime: String): Uri? {
        val parentUri = try { DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId) } catch (_: Exception) { return null }
        val unique = safUniqueName(treeUri, parentDocId, name, isDir = false)
        return try {
            DocumentsContract.createDocument(contentResolver, parentUri, mime, unique)
        } catch (_: Exception) { null }
    }

    /** SAF 里建一个文件夹：返回新目录的文档 ID，失败返回 null。 */
    private fun safCreateDir(treeUri: Uri, parentDocId: String, name: String): String? {
        val parentUri = try { DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId) } catch (_: Exception) { return null }
        val unique = safUniqueName(treeUri, parentDocId, name, isDir = true)
        val uri = try {
            DocumentsContract.createDocument(contentResolver, parentUri, DocumentsContract.Document.MIME_TYPE_DIR, unique)
        } catch (_: Exception) { null } ?: return null
        return try { DocumentsContract.getDocumentId(uri) } catch (_: Exception) { null }
    }

    /** SAF 建文档用的 MIME：按扩展名猜，猜不到用 application/octet-stream
     *  （createDocument 不接受通配 MIME，个别提供方会直接拒掉）。 */
    private fun safMimeFor(name: String): String {
        val m = FileUtils.mimeFor(File(name))
        return if (m == "*/*") "application/octet-stream" else m
    }

    /** File → SAF 目录：文件建文档写流；文件夹递归建目录。 */
    private fun copyFileToSaf(treeUri: Uri, parentUri: Uri, src: File): Boolean {
        val parentDocId = try { DocumentsContract.getDocumentId(parentUri) } catch (_: Exception) { return false }
        return if (src.isDirectory) {
            val dirId = safCreateDir(treeUri, parentDocId, src.name) ?: return false
            val dirUri = try { DocumentsContract.buildDocumentUriUsingTree(treeUri, dirId) } catch (_: Exception) { return false }
            src.listFiles()?.all { copyFileToSaf(treeUri, dirUri, it) } ?: false
        } else {
            val docUri = safCreateFile(treeUri, parentDocId, src.name, safMimeFor(src.name)) ?: return false
            try {
                src.inputStream().use { input ->
                    contentResolver.openOutputStream(docUri, "w")?.use { output -> input.copyTo(output) }
                        ?: return false
                }
                true
            } catch (_: Exception) { false }
        }
    }

    /** SAF 文档 → 普通 File 目录：文件读流写文件；文件夹递归建目录。 */
    private fun copySafToFile(src: ClipEntry, targetDir: File): Boolean {
        val uri = src.safUri ?: return false
        if (!src.isDir) {
            val dst = File(targetDir, src.name)
            return try {
                contentResolver.openInputStream(uri)?.use { input ->
                    dst.outputStream().use { output -> input.copyTo(output) }
                } ?: return false
                true
            } catch (_: Exception) { false }
        }
        val dir = File(targetDir, src.name)
        if (!dir.mkdirs() && !dir.isDirectory) return false
        val docId = try { DocumentsContract.getDocumentId(uri) } catch (_: Exception) { return false }
        val treeUri = safTreeUriOf(uri) ?: return false
        val children = listSafChildren(treeUri, docId) ?: return false
        return children.all { child ->
            copySafToFile(ClipEntry(null, child.uri, child.name, child.isDir), dir)
        }
    }

    /** SAF 文档 → SAF 目录：文件建文档写流；文件夹递归建目录。 */
    private fun copySafToSaf(treeUri: Uri, parentUri: Uri, src: ClipEntry): Boolean {
        val uri = src.safUri ?: return false
        val parentDocId = try { DocumentsContract.getDocumentId(parentUri) } catch (_: Exception) { return false }
        return if (src.isDir) {
            val dirId = safCreateDir(treeUri, parentDocId, src.name) ?: return false
            val docId = try { DocumentsContract.getDocumentId(uri) } catch (_: Exception) { return false }
            val srcTreeUri = safTreeUriOf(uri) ?: return false
            val children = listSafChildren(srcTreeUri, docId) ?: return false
            val dirUri = try { DocumentsContract.buildDocumentUriUsingTree(treeUri, dirId) } catch (_: Exception) { return false }
            children.all { copySafToSaf(treeUri, dirUri, ClipEntry(null, it.uri, it.name, it.isDir)) }
        } else {
            val docUri = safCreateFile(treeUri, parentDocId, src.name, safMimeFor(src.name)) ?: return false
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    contentResolver.openOutputStream(docUri, "w")?.use { output -> input.copyTo(output) }
                        ?: return false
                } ?: return false
                true
            } catch (_: Exception) { false }
        }
    }

    /** 查一个 SAF 文档是不是文件夹：直接问它自己的 MIME_TYPE。绝不能拿
     *  listSafChildren 返回非空当文件夹（见 safDeleteDocument 的注释）。
     *  查不到就按文件处理（直接删，不递归）。 */
    private fun safIsDir(uri: Uri): Boolean {
        return try {
            contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { c ->
                c.moveToFirst() && c.getString(0) == DocumentsContract.Document.MIME_TYPE_DIR
            } ?: false
        } catch (e: Exception) { Log.w(TAG, "safIsDir failed uri=$uri: $e"); false }
    }

    /** 删一个 SAF 文档：文件夹先递归删子项再删自己（个别提供方不接受删非空目录）。
     *  v1.7.1：每一步都打 Log（删没调 deleteDocument、返回值多少，logcat 可查）。
     *  v1.7.1 修死循环：是不是文件夹只认 MIME_TYPE（isDir 参数，没传就现查），
     *  绝不能拿 listSafChildren 返回非空当文件夹——downloads 提供方对文件 docId
     *  也会返回子项（文件 msf:86 能“列出” 2 个子项，其中一个还是 msd:82，于是
     *  msd:82→msd:84→msf:86→msd:82 无限循环，线程永远回不来，v1.7.0 的静默失败
     *  就是这么来的）。depth 上限 32 是最后一道保险。 */
    private fun safDeleteDocument(uri: Uri, isDir: Boolean? = null, depth: Int = 0): Boolean {
        Log.d(TAG, "safDeleteDocument: uri=$uri isDir=$isDir depth=$depth")
        if (depth > 32) { Log.w(TAG, "safDeleteDocument: depth limit hit, aborting uri=$uri"); return false }
        return try {
            val dir = isDir ?: safIsDir(uri)
            if (dir) {
                val docId = DocumentsContract.getDocumentId(uri)
                val treeUri = safTreeUriOf(uri)
                if (treeUri != null) {
                    val children = listSafChildren(treeUri, docId)
                    Log.d(TAG, "safDeleteDocument: children of $docId = ${children?.size ?: "null (query failed)"}")
                    if (children != null) {
                        // 能列出子项就是文件夹：先递归删子项（isDir 直接用列表里的，不再逐个查）。
                        for (child in children) { if (!safDeleteDocument(child.uri, child.isDir, depth + 1)) return false }
                    }
                }
            }
            Log.d(TAG, "safDeleteDocument: calling deleteDocument uri=$uri")
            val ok = DocumentsContract.deleteDocument(contentResolver, uri)
            Log.d(TAG, "safDeleteDocument: deleteDocument uri=$uri -> $ok")
            ok
        } catch (e: Exception) { Log.w(TAG, "safDeleteDocument failed uri=$uri: $e"); false }
    }

    /** v1.7.0 修复：按父目录 + 名字重列一遍、拿新鲜的 URI 再删。downloads 提供方
     *  的数字 docId 会变（比如粘贴后立即列出的 msf:89，实际行已经是 msf:91），
     *  拿着缓存的旧 URI 调 deleteDocument 会报 "No item at …" 删不掉。
     *  重列失败返回 false，调用方再用缓存 URI 兜底试一次。 */
    private fun safDeleteByName(treeUri: Uri, parentDocId: String, name: String): Boolean {
        Log.d(TAG, "safDeleteByName: parentDocId=$parentDocId name=$name (re-listing for fresh URI)")
        val children = try {
            listSafChildren(treeUri, parentDocId)
        } catch (e: Exception) { Log.w(TAG, "safDeleteByName re-list threw: $e"); null }
        Log.d(TAG, "safDeleteByName: re-listed ${children?.size ?: "null"} item(s), target found=${children?.any { it.name == name }}")
        val fresh = children?.firstOrNull { it.name == name } ?: return false
        return safDeleteDocument(fresh.uri, fresh.isDir)
    }

    /** 删一条剪贴板里的 SAF 源：优先按名重取新鲜 URI（同上），失败再用缓存 URI 试一次。 */
    private fun safDeleteClipEntry(src: ClipEntry): Boolean {
        val uri = src.safUri ?: return false
        val treeUri = src.safTreeUri
        val parentDocId = src.safParentDocId
        if (treeUri != null && parentDocId != null) {
            if (safDeleteByName(treeUri, parentDocId, src.name)) return true
        }
        return safDeleteDocument(uri, src.isDir)
    }

    /** SAF 项重命名：返回是否成功（个别提供方不支持会抛异常）。v1.7.1：加 Log。 */
    private fun safRenameDocument(uri: Uri, newName: String): Boolean {
        Log.d(TAG, "safRenameDocument: uri=$uri newName=$newName")
        return try {
            val ok = DocumentsContract.renameDocument(contentResolver, uri, newName) != null
            Log.d(TAG, "safRenameDocument -> $ok")
            ok
        } catch (e: Exception) { Log.w(TAG, "safRenameDocument failed: $e"); false }
    }

    /** SAF 文件夹的递归大小（后台线程里算）；文件直接取列表里的 size。 */
    private fun safDirSize(uri: Uri): Long {
        var total = 0L
        try {
            val docId = DocumentsContract.getDocumentId(uri)
            val treeUri = safTreeUriOf(uri) ?: return 0L
            val children = listSafChildren(treeUri, docId) ?: return 0L
            for (child in children) {
                total += if (child.isDir) safDirSize(child.uri) else child.size
            }
        } catch (_: Exception) { }
        return total
    }

    // ---------- 抽屉右上角 ⋮ 菜单 / 自定义本地储存 ----------

    /** 抽屉头右上角的 ⋮ 菜单：v1.5.0 起只剩“添加本地储存”一项（Max 的要求：
     * 别的都不要，主题在设置→主题模式里改，设置本来就在抽屉工具组里）。 */
    private fun showDrawerOverflowMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.setForceShowIcon(true)
        popup.menu.add(0, 3, 0, R.string.add_local_storage).icon = tintedIcon(R.drawable.ic_drawer_storage)
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == 3) openStoragePicker()
            true
        }
        popup.show()
    }

    /** 系统文档选择器（ACTION_OPEN_DOCUMENT_TREE）：点“添加本地储存”直接跳
     *  到安卓原生文件管理器，选好文件夹就添加，名字就是文件夹本身的名字。 */
    private val pickStorageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onStorageTreePicked(uri)
    }

    private fun openStoragePicker() {
        try {
            pickStorageLauncher.launch(null)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
        }
    }

    /** 用户在系统选择器里选好文件夹后的处理：v1.6.0 起任何 tree URI 都收——
     *  第三方提供方（如 ZeroTermux）或别家 App 的 Android/data 目录映射不出
     *  真实路径，以前直接拒掉；现在只要持久授权拿得到就存下来，浏览时走 SAF。
     *  能映射出真实路径的照样存路径，浏览时优先走 File（快）。 */
    private fun onStorageTreePicked(uri: Uri) {
        val uriStr = uri.toString()
        // 持久授权：先读写，个别只读提供方退而求其次只读；都拿不到就加不了。
        val persisted = try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            true
        } catch (_: Exception) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                true
            } catch (_: Exception) { false }
        }
        if (!persisted) {
            Toast.makeText(this, R.string.storage_not_supported, Toast.LENGTH_SHORT).show()
            return
        }
        val docId = try { DocumentsContract.getTreeDocumentId(uri) } catch (_: Exception) { null }
        // 名字：优先问提供方要显示名（第三方提供方的 docId 不一定含名字）。
        val displayName = if (docId != null) {
            try {
                val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, docId)
                var n: String? = null
                contentResolver.query(
                    docUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null
                )?.use { c -> if (c.moveToFirst()) n = c.getString(0) }
                n
            } catch (_: Exception) { null }
        } else null
        val name = displayName?.takeIf { it.isNotBlank() }
            ?: docId?.let { storageNameFromDocId(it) }
            ?: uri.lastPathSegment?.takeIf { it.isNotBlank() }
            ?: getString(R.string.storage_internal)
        val mappedPath = if (docId != null) treeDocIdToPath(uri.authority, docId) else null
        val path = if (mappedPath != null && File(mappedPath).isDirectory) mappedPath else ""
        val list = CustomStorages.load(prefs)
        val existing = list.indexOfFirst {
            (it.uri.isNotBlank() && it.uri == uriStr) || (path.isNotBlank() && it.path == path)
        }
        if (existing >= 0) {
            if (list[existing].hidden) {
                // 之前藏起来的又选了一遍：取消隐藏，不重复加。
                list[existing] = list[existing].copy(hidden = false)
                CustomStorages.save(prefs, list)
                refreshCustomStorages()
                Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.storage_already_added, Toast.LENGTH_SHORT).show()
            }
            return
        }
        list.add(CustomStorages.Storage(name, path, false, uriStr))
        CustomStorages.save(prefs, list)
        refreshCustomStorages()
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
    }

    /** tree 文档 ID 映射成真实路径；映射不出来的返回 null。
     *  外部储存：`primary:Download` / `BA73-022B:xxx`；
     *  下载提供方：`downloads`（即内置 Download 目录）、`raw:/绝对路径`。 */
    private fun treeDocIdToPath(authority: String?, docId: String): String? {
        if (authority == "com.android.providers.downloads.documents") {
            if (docId == "downloads") {
                return File(Environment.getExternalStorageDirectory(), "Download").absolutePath
            }
            if (docId.startsWith("raw:")) {
                val p = docId.removePrefix("raw:")
                if (p.startsWith("/") && !p.contains("..")) return p
            }
            return null
        }
        if (authority != "com.android.externalstorage.documents") return null
        val parts = docId.split(":", limit = 2)
        if (parts.size != 2) return null
        val volume = parts[0]
        val rel = parts[1]
        if (rel.contains("..")) return null
        val base = if (volume == "primary") {
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            if (!Regex("^[A-Za-z0-9-]+$").matches(volume)) return null
            "/storage/$volume"
        }
        return if (rel.isEmpty()) base else "$base/$rel"
    }

    /** 文件夹本身的名字：取文档 ID 相对路径的最后一段；选的是储存根目录时
     *  用储存名（内部储存 / 卷标）；下载提供方根目录叫 Download。 */
    private fun storageNameFromDocId(docId: String): String {
        if (docId == "downloads") return "Download"
        val rel = docId.substringAfter(":", "")
        val last = rel.split("/").lastOrNull()?.takeIf { it.isNotEmpty() }
        if (last != null) return last
        val volume = docId.substringBefore(":")
        return if (volume == "primary") getString(R.string.storage_internal) else volume
    }

    /** 弹出菜单的图标：AppCompat 不会自动给菜单图标上色，白底白字会看不见，
     *  所以这里手动按 colorControlNormal（随主题变）着色。 */
    private fun tintedIcon(resId: Int): Drawable? {
        val d = AppCompatResources.getDrawable(this, resId)?.mutate() ?: return null
        val color = MaterialColors.getColor(
            this, androidx.appcompat.R.attr.colorControlNormal, Color.GRAY
        )
        DrawableCompat.setTint(d, color)
        return d
    }

    /** 无边框的可点击背景（selectableItemBackgroundBorderless），供代码里建的按钮用。 */
    private fun selectableBackground(): Drawable? {
        val tv = android.util.TypedValue()
        if (!theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)) return null
        return try { AppCompatResources.getDrawable(this, tv.resourceId) } catch (_: Exception) { null }
    }

    /** 抽屉里自定义储存的副标题：有真实路径就显示路径；只存了 URI 的
     *  （第三方提供方 / 别家 App 目录）显示文档 ID 的最后一段。 */
    /** 抽屉里自定义储存的副标题：有真实路径就显示路径；SAF 储存没有路径，
     *  以前取 tree docId 尾段，downloads 提供方的 docId 是 msd:82 这种，
     *  尾段只剩 "82" 很难看。现在先问提供方要树文档的显示名（比如 Download），
     *  问不到才回落到 docId 尾段——纯数字的尾段不要，改显示提供方 authority。 */
    private fun storageDisplayPath(s: CustomStorages.Storage): String {
        if (s.path.isNotBlank()) return s.path
        if (s.uri.isBlank()) return ""
        return try {
            val u = Uri.parse(s.uri)
            val docId = DocumentsContract.getTreeDocumentId(u)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(u, docId)
            val displayName = try {
                contentResolver.query(docUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            } catch (_: Exception) { null }
            if (!displayName.isNullOrBlank()) return displayName
            val tail = docId.substringAfterLast(':').substringAfterLast('/')
            if (tail.isNotBlank() && !tail.all { it.isDigit() }) tail else (u.authority ?: "")
        } catch (_: Exception) { "" }
    }

    /** 抽屉本地组里的自定义储存行（未隐藏的才显示）。 */
    private fun refreshCustomStorages() {
        if (!::drawerLayout.isInitialized || !::prefs.isInitialized) return
        val container = findViewById<LinearLayout>(R.id.drawerCustomContainer) ?: return
        container.removeAllViews()
        for (s in CustomStorages.load(prefs)) {
            if (s.hidden) continue
            val row = layoutInflater.inflate(R.layout.item_drawer_storage, container, false)
            row.findViewById<TextView>(R.id.storageName).text = s.name
            row.findViewById<TextView>(R.id.storagePath).text = storageDisplayPath(s)
            row.setOnClickListener {
                drawerLayout.closeDrawer(GravityCompat.START)
                navigateDrawerToStorage(s)
            }
            row.setOnLongClickListener {
                showCustomStorageMenu(it, s)
                true
            }
            container.addView(row)
        }
    }

    /** 长按自定义储存弹出的菜单（照 MT 那个面板）：重命名 / 删除 / 隐藏 / 排序。 */
    private fun showCustomStorageMenu(anchor: View, storage: CustomStorages.Storage) {
        val popup = PopupMenu(this, anchor)
        popup.setForceShowIcon(true)
        popup.menu.add(0, 1, 0, R.string.rename).icon = tintedIcon(R.drawable.ic_edit)
        popup.menu.add(0, 2, 0, R.string.delete).icon = tintedIcon(R.drawable.ic_drawer_trash)
        popup.menu.add(0, 3, 0, R.string.hide).icon = tintedIcon(R.drawable.ic_hide)
        popup.menu.add(0, 4, 0, R.string.sort).icon = tintedIcon(R.drawable.ic_sort)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> showRenameStorageDialog(storage)
                2 -> confirmDeleteStorage(storage)
                3 -> {
                    val list = CustomStorages.load(prefs)
                    val i = list.indexOfFirst { CustomStorages.key(it) == CustomStorages.key(storage) }
                    if (i >= 0) {
                        list[i] = list[i].copy(hidden = true)
                        CustomStorages.save(prefs, list)
                        refreshCustomStorages()
                    }
                    Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
                }
                4 -> showSortStoragesDialog()
            }
            true
        }
        popup.show()
    }

    private fun showRenameStorageDialog(storage: CustomStorages.Storage) {
        val input = EditText(this)
        input.setText(storage.name)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rename)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                val n = input.text.toString().trim().replace("\t", " ").replace("\n", " ")
                if (n.isNotEmpty() && n != storage.name) {
                    val list = CustomStorages.load(prefs)
                    val i = list.indexOfFirst { CustomStorages.key(it) == CustomStorages.key(storage) }
                    if (i >= 0) {
                        list[i] = list[i].copy(name = n)
                        CustomStorages.save(prefs, list)
                        refreshCustomStorages()
                    }
                    Toast.makeText(this, R.string.renamed, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** 删除自定义储存：只删记录，不删文件夹本身；通过选择器加的还要释放
     *  持久 URI 授权（v1.5.0：不释放的话系统里一直挂着授权记录）。 */
    private fun confirmDeleteStorage(storage: CustomStorages.Storage) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.confirm_delete)
            .setMessage(storage.name + "\n" + storage.path)
            .setPositiveButton(R.string.delete) { _, _ ->
                if (storage.uri.isNotBlank()) {
                    try {
                        contentResolver.releasePersistableUriPermission(
                            Uri.parse(storage.uri),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    } catch (_: Exception) { }
                }
                val list = CustomStorages.load(prefs)
                list.removeAll { CustomStorages.key(it) == CustomStorages.key(storage) }
                CustomStorages.save(prefs, list)
                refreshCustomStorages()
                Toast.makeText(this, R.string.deleted, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** 排序：弹出列表，每行上下移按钮，顺序即抽屉里的顺序（只排自定义储存）。 */
    private fun showSortStoragesDialog() {
        val list = CustomStorages.load(prefs)
        if (list.isEmpty()) {
            Toast.makeText(this, R.string.no_files, Toast.LENGTH_SHORT).show()
            return
        }
        val density = resources.displayMetrics.density
        val pad = (12 * density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun render() {
            container.removeAllViews()
            list.forEachIndexed { index, s ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                val nameTv = TextView(this).apply {
                    text = s.name + if (s.hidden) "（" + getString(R.string.hide) + "）" else ""
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val upBtn = ImageButton(this).apply {
                    setImageResource(R.drawable.ic_arrow_up)
                    contentDescription = getString(R.string.move_up)
                    background = selectableBackground()
                    isEnabled = index > 0
                    alpha = if (index > 0) 1f else 0.3f
                    setOnClickListener {
                        if (index > 0) {
                            val tmp = list[index - 1]
                            list[index - 1] = list[index]
                            list[index] = tmp
                            CustomStorages.save(prefs, list)
                            refreshCustomStorages()
                            render()
                        }
                    }
                }
                val downBtn = ImageButton(this).apply {
                    setImageResource(R.drawable.ic_arrow_down)
                    contentDescription = getString(R.string.move_down)
                    background = selectableBackground()
                    isEnabled = index < list.size - 1
                    alpha = if (index < list.size - 1) 1f else 0.3f
                    setOnClickListener {
                        if (index < list.size - 1) {
                            val tmp = list[index + 1]
                            list[index + 1] = list[index]
                            list[index] = tmp
                            CustomStorages.save(prefs, list)
                            refreshCustomStorages()
                            render()
                        }
                    }
                }
                row.addView(nameTv)
                if (s.hidden) {
                    // 已隐藏的项在这里取消隐藏（v1.5.0：手填对话框删了，找回入口搬到这儿）。
                    val unhideBtn = android.widget.Button(this).apply {
                        text = getString(R.string.unhide)
                        setOnClickListener {
                            list[index] = list[index].copy(hidden = false)
                            CustomStorages.save(prefs, list)
                            refreshCustomStorages()
                            Toast.makeText(this@MainActivity, R.string.saved, Toast.LENGTH_SHORT).show()
                            render()
                        }
                    }
                    row.addView(unhideBtn)
                }
                row.addView(upBtn)
                row.addView(downBtn)
                container.addView(row)
            }
        }
        render()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sort)
            .setView(container)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    // ---------- 菜单 / 搜索 / 权限 ----------

    /** v1.7.0 修复：顶部工具栏菜单一直打不开，就是缺了这个——以前只在布局里写了
     *  app:menu，但 setSupportActionBar 接管工具栏后，菜单必须由 Activity 的
     *  onCreateOptionsMenu 来 inflate，否则搜索图标和 ⋮ 根本不会出现。 */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.simple_menu, menu)
        // v1.9.6 玻璃开着时顶栏变浅：搜索图标是白色矢量图（为实色主色底
        // 设计），衬浅色玻璃底会发白难读；标题/副标题/导航图标已在
        // setupLiquidGlass 里改深色，这里把搜索图标也改深（colorOnSurface，
        // 浅色主题深字、深色主题浅字）。菜单每次 inflate 都重走这里，
        // 玻璃开关变化重建 Activity 时也会重走，不会漏。
        if (::prefs.isInitialized && prefs.getBoolean(PREF_LIQUID_GLASS, false)) {
            val dark = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
            menu.findItem(R.id.menu_search)?.icon?.mutate()?.let { DrawableCompat.setTint(it, dark) }
        }
        // v1.9.0：溢出菜单默认不显示图标，强制显示（Max 要求每项带图标）。
        try {
            (menu as? androidx.appcompat.view.menu.MenuBuilder)?.setOptionalIconsVisible(true)
        } catch (_: Exception) { }
        menu.findItem(R.id.menu_dual_pane)?.isChecked = dualPane
        menu.findItem(R.id.menu_show_hidden)?.isChecked =
            if (::prefs.isInitialized) prefs.getBoolean("show_hidden", false) else false
        setupSearch()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return onMenu(item)
    }

    private fun setupSearch() {
        val searchItem = topBar.menu.findItem(R.id.menu_search) ?: return
        val searchView = searchItem.actionView as? SearchView ?: return
        searchView.queryHint = getString(R.string.search_hint)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?): Boolean {
                activePane().searchQuery = q ?: ""; loadPane(activePane()); return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                activePane().searchQuery = newText ?: ""; loadPane(activePane()); return true
            }
        })
        searchView.setOnCloseListener { activePane().searchQuery = ""; loadPane(activePane()); false }
    }

    private fun onMenu(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_sort -> { showSortDialog(); return true }
            R.id.menu_show_hidden -> {
                val v = !prefs.getBoolean("show_hidden", false)
                prefs.edit().putBoolean("show_hidden", v).apply(); item.isChecked = v
            }
            R.id.menu_dual_pane -> {
                dualPane = !dualPane
                prefs.edit().putBoolean("dual_pane", dualPane).apply()
                item.isChecked = dualPane
                applyDualPane()
                return true
            }
            R.id.menu_refresh -> { load(); return true }
            else -> return false
        }
        load()
        return true
    }

    /** v1.9.0 排序对话框：列出 按名称 / 按日期 / 按大小 三个选项，
     *  选一个再点确认才排序（取消不改）。 */
    private fun showSortDialog() {
        var selected = prefs.getInt("sort", 0)
        val options = arrayOf(
            getString(R.string.sort_name),
            getString(R.string.sort_date),
            getString(R.string.sort_size)
        )
        val sortDlg = glassDialogBuilder().setTitle(R.string.sort).setIcon(R.drawable.ic_sort)
            .setSingleChoiceItems(options, selected) { _, which -> selected = which }
            .setPositiveButton(R.string.confirm) { _, _ ->
                prefs.edit().putInt("sort", selected).apply()
                load()
            }
            .setNegativeButton(R.string.cancel, null).create()
        sortDlg.show()
    }

    private fun showAbout() {
        startActivity(Intent(this, AboutActivity::class.java))
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.permission_needed)
                    .setMessage(R.string.permission_needed)
                    .setPositiveButton(R.string.grant_access) { _, _ ->
                        try {
                            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
                        } catch (e: Exception) {
                            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        } else {
            val perms = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (perms.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
                requestPermissions(perms, REQ_PERMS)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        load()
    }

    /**
     * v1.4.1：抽屉里的文字（标题、分组名、各行名称、版本号）原来只在 onCreate 设一次，
     * 切语言后 MainActivity 在后台没重建，回来抽屉还是旧语言，要重启 App 才变。
     * 现在每次 onResume 都按当前语言重填一遍；版本号改从 PackageManager 读，
     * 升级不用再改代码里的硬编码。
     */
    private fun refreshDrawerTexts() {
        if (!::drawerLayout.isInitialized) return
        try {
            val versionText = try {
                val pi = packageManager.getPackageInfo(packageName, 0)
                val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode else pi.versionCode.toLong()
                getString(R.string.version) + " " + pi.versionName + " ($code)"
            } catch (_: Exception) {
                getString(R.string.version) + " 1.9.0 (14)"
            }
            findViewById<TextView>(R.id.drawerVersion).text = versionText
            findViewById<TextView>(R.id.drawerLocalTitle).text = getString(R.string.drawer_local)
            findViewById<TextView>(R.id.drawerBookmarksTitle)?.text = getString(R.string.bookmarks_title)
            findViewById<TextView>(R.id.drawerToolsTitle).text = getString(R.string.drawer_tools)
            findViewById<TextView>(R.id.drawerRootLabel).text = getString(R.string.root_directory)
            findViewById<TextView>(R.id.drawerInternalLabel).text = getString(R.string.storage_internal)
            findViewById<TextView>(R.id.drawerTrashLabel).text = getString(R.string.trash)
            findViewById<TextView>(R.id.drawerEditorLabel).text = getString(R.string.text_editor)
            findViewById<TextView>(R.id.drawerSettingsLabel).text = getString(R.string.settings)
        } catch (_: Exception) { }
    }

    override fun onResume() {
        super.onResume()
        if (!::left.isInitialized || !::right.isInitialized) return
        // 从设置页回来时语言可能变了：跟保存的设置不一致就重建，让新语言生效。
        try {
            val tag = prefs.getString(PREF_LOCALE, null)
            val current = Locale.getDefault().toLanguageTag()
            if (!tag.isNullOrBlank() && !current.equals(tag, ignoreCase = true)
                && !current.startsWith(tag, ignoreCase = true)) {
                recreate()
                return
            }
            // 主题也可能在设置里改了：当前夜间模式跟保存的不一致就重建。
            val mode = prefs.getInt(PREF_THEME_MODE, 0)
            val night = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            val wantNight = when (mode) {
                1 -> false
                2 -> true
                else -> night == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
            val isNight = night == android.content.res.Configuration.UI_MODE_NIGHT_YES
            if (mode != 0 && wantNight != isNight) {
                recreate()
                return
            }
            // v1.9.2：配色变化由 BaseActivity.onResume 统一处理（按本页面创建时
            // 应用的值比，不再用静态 lastApplied*——它会被外观页自己的
            // recreate 更新掉，导致这里永远比不出变化）。
        } catch (_: Exception) { }
        refreshDrawerTexts()
        refreshCustomStorages()
        refreshBookmarks()
        load()
    }

    private fun runBg(block: () -> Unit) {
        Thread {
            try { block() } catch (e: Exception) { e.printStackTrace() }
            runOnUiThread { load() }
        }.start()
    }

    /** v1.7.1：SAF 写操作带超时跑。提供方调用可能卡住不出（binder 调用不响应
     *  interrupt，v1.7.0 就是这么静默失败的：线程卡住，回调一次没跑到，选择条
     *  一直挂着）。超时后直接走 onDone(false)：报错、清选择态、刷新列表。
     *  onDone 一定在主线程跑一次，且只跑一次。 */
    private fun runSafOpTimeout(timeoutMs: Long = 15000L, block: () -> Boolean, onDone: (Boolean) -> Unit) {
        val future = safExecutor.submit(block)
        Thread {
            val ok = try {
                future.get(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (e: TimeoutException) {
                Log.w(TAG, "SAF op timeout after ${timeoutMs}ms (worker may still be stuck in provider call), cancelling")
                future.cancel(true)
                false
            } catch (e: Exception) {
                Log.w(TAG, "SAF op failed: $e")
                false
            }
            runOnUiThread { if (!isFinishing && !isDestroyed) onDone(ok) }
        }.start()
    }

    private fun snackbar(res: Int) {
        Snackbar.make(findViewById(R.id.mainRoot), res, Snackbar.LENGTH_SHORT).show()
    }
}
