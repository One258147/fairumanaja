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
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.os.LocaleListCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

        /** 当前主题主色（不带 alpha 的 RGB）。 */
        fun themePrimaryColor(ctx: android.content.Context): Int {
            return try {
                val c = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorPrimary, Color.BLUE)
                Color.rgb(Color.red(c), Color.green(c), Color.blue(c))
            } catch (_: Exception) { Color.BLUE }
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
            -16728876, -16738680, -6750312, -32944, -6381922,
            -476208, -1499549, -10395295, -7288071, -5908825,
            -2659, -3238952, -26624, -15064194, -14983648,
            -11652050, -16121, -769226, -1294214, -6543440,
            -12627531, -16537100, -3285959, -19712, -7508381,
            -10453621, -16750244
        )
        // v1.9.8.1：lastApplied* 已删——偏好变化由 BaseActivity 的实例字段
        // 在 onResume 里比（偏好对偏好），不再用静态字段。
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





        /** UI 引擎的显示名（设置页副标题、实验性功能页副标题共用）。 */
        fun engineDisplayName(ctx: android.content.Context, engine: Int): String = when (engine) {
            UI_ENGINE_EXPRESSIVE -> "M3 Expressive"
            UI_ENGINE_MIUIX -> "Miuix"
            else -> "Material Design 3"
        }

        /** 所有 Activity 启动前统一应用：语言 + 主题模式 + 动态颜色/种子色
         *  （要在 super.onCreate 之前调）。动态颜色开着时优先于种子色；
         *  种子色用 getTheme().applyStyle 合并覆盖层（浅/深各一套，按夜间模式自动取）。 */
        /** v1.9.8.1：所有 Activity 启动前统一应用语言 + 夜间模式（AppCompat 级，
         *  Compose 管不着）。配色/引擎/玻璃/字体颜色改由 Compose 的 FairuTheme
         *  直接从偏好算，不再走主题覆盖层（themes_seed/engine/font 已删）。 */
        fun applyPrefs(ctx: android.content.Context) {
            try {
                val sp = ctx.getSharedPreferences("fairu_prefs", MODE_PRIVATE)
                val tag = sp.getString(PREF_LOCALE, null)
                if (tag.isNullOrBlank() || tag == LanguageActivity.TAG_FOLLOW_SYSTEM) {
                    // v1.9.8：没保存语言 = 跟随系统，必须清掉 AppCompat 存的旧覆盖，
                    // 否则以前选过的具体语言会一直生效。
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                } else {
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
            } catch (_: Exception) { }
        }

        /** SAF 写操作的后台执行器：守护线程，提供方调用卡住时不拖住进程。 */
        val safExecutor = Executors.newCachedThreadPool { r -> Thread(r, "saf-op").apply { isDaemon = true } }
    }

    internal lateinit var prefs: SharedPreferences
    internal lateinit var left: Pane
    internal lateinit var right: Pane
    internal var active by mutableStateOf(0) // 0 = 左窗格，1 = 右窗格
    internal var dualPane by mutableStateOf(true)

    // ---------- Compose 对话框 / 菜单状态（v1.9.8.1） ----------
    // 对话框由状态驱动：置非空即显示，点取消/确认后置回 null。状态定义见 MainDialogs.kt。
    var deleteDialog by mutableStateOf<DeleteRequest?>(null)
    var renameDialog by mutableStateOf<RenameRequest?>(null)
    var showNewDialog by mutableStateOf(false)
    // v1.9.8：路径跳转对话框（点顶栏路径 / 窗格路径条弹出）与创建 HTML 对话框。
    var showJumpDialog by mutableStateOf(false)
    var showCreateHtmlDialog by mutableStateOf(false)

    var fileMenu by mutableStateOf<FileMenuRequest?>(null)
    var showSelectionMenu by mutableStateOf(false)

    var moreActionsDialog by mutableStateOf<MoreActionsRequest?>(null)
    var shortcutDialog by mutableStateOf<ShortcutRequest?>(null)
    var propertiesDialog by mutableStateOf<PropertiesRequest?>(null)
    var showSortDialog by mutableStateOf(false)

    var showTopMenu by mutableStateOf(false)

    var showDrawerOverflowMenu by mutableStateOf(false)

    var bookmarkMenu by mutableStateOf<BookmarkMenuRequest?>(null)
    var passwordDialog by mutableStateOf<PasswordRequest?>(null)
    var showSearch by mutableStateOf(false)

    var drawerOpen by mutableStateOf(false)
    // 抽屉自定义储存 / 书签行（带显示副标题，后台算好再填；Compose 直接读）。
    var customStorageRows by mutableStateOf<List<CustomStorageRow>>(emptyList())
    var bookmarkRows by mutableStateOf<List<Bookmarks.Bookmark>>(emptyList())
    // 自定义储存的长按菜单 / 重命名 / 删除确认 / 排序对话框。
    var storageMenu by mutableStateOf<StorageMenuRequest?>(null)
    var storageRenameDialog by mutableStateOf<StorageRenameRequest?>(null)
    var storageDeleteDialog by mutableStateOf<StorageDeleteRequest?>(null)
    var showSortStoragesDialog by mutableStateOf(false)

    // 抽屉里储存用量（StatFs，后台算好再填；Compose 直接读状态）。
    var drawerRootInfo by mutableStateOf("")

    var drawerRootPct by mutableStateOf(0)

    var drawerInternalInfo by mutableStateOf("")

    var drawerInternalPct by mutableStateOf(0)

    var customStoragesVersion by mutableStateOf(0)

    var bookmarksVersion by mutableStateOf(0)

    // Snackbar（Compose Scaffold 的 SnackbarHostState，由 MainUi 持有，这里只发消息）。
    var snackbarMessage by mutableStateOf<String?>(null)

    fun showSnackbar(msg: String) { snackbarMessage = msg }

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

    /** v1.9.8：内置图像查看器从哪个窗格点开的（返回时选择要勾回这个窗格；
     *  进程重建丢了就退回 activePane()）。 */
    private var pendingViewerPane: Pane? = null

    /** v1.9.8：内置图像查看器返回后的处理（照 rootEditorLauncher 的样子）。 */
    private val imageViewerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) { pendingViewerPane = null; return@registerForActivityResult }
        val data = result.data
        val pane = pendingViewerPane ?: activePane()
        pendingViewerPane = null
        if (data == null) return@registerForActivityResult
        // 1. 请主列表代删的（SAF / 根目录来源）：按标识找回条目再调现成的 confirmDelete。
        val requestDelete = data.getStringArrayListExtra(ImageViewerActivity.EXTRA_REQUEST_DELETE_PATHS).orEmpty()
        if (requestDelete.isNotEmpty()) {
            val targets = requestDelete.mapNotNull { key ->
                when {
                    // SAF：查看器返回的是文档 URI 字符串，按 URI 找回 SafEntry，用合成路径。
                    pane.safMode -> pane.safEntries.firstOrNull { it.uri.toString() == key }
                        ?.let { File(it.keyPath) }
                    // 根目录：返回的是真实路径。
                    pane.shizukuMode -> File(key).takeIf { f ->
                        pane.shizukuEntries.any { it.path == f.absolutePath }
                    }
                    else -> null
                }
            }
            if (targets.isNotEmpty()) confirmDelete(targets)
        }
        // 2. 本页已删的普通文件：主列表刷新掉。
        if (data.getStringArrayListExtra(ImageViewerActivity.EXTRA_DELETED_PATHS).orEmpty().isNotEmpty()) {
            refreshPane(pane)
        }
        // 3. 带选择退出：把其中在当前文件夹里的勾进主列表选中态，进选择模式；
        // 跨文件夹的、没有真实路径的带不回去（见汇报）。
        val selectedPaths = data.getStringArrayListExtra(ImageViewerActivity.EXTRA_SELECTED_PATHS).orEmpty()
        if (selectedPaths.isNotEmpty()) {
            val dir = pane.currentDir
            selectedPaths.forEach { path ->
                val f = File(path)
                if (f.parentFile?.absolutePath == dir.absolutePath) pane.selected.add(f)
            }
            updateSelectionTopBar()
        }
    }

    /** v1.9.8：点图片进内置查看器（普通文件传路径，SAF 传 URI，
     *  根目录传暂存副本路径 + 真实路径）。 */
    private fun openImageViewer(pane: Pane, path: String? = null, uri: String? = null, originalPath: String? = null) {
        pendingViewerPane = pane
        imageViewerLauncher.launch(Intent(this, ImageViewerActivity::class.java).apply {
            path?.let { putExtra(ImageViewerActivity.EXTRA_PATH, it) }
            uri?.let { putExtra(ImageViewerActivity.EXTRA_URI, it) }
            originalPath?.let { putExtra(ImageViewerActivity.EXTRA_ORIGINAL_PATH, it) }
        })
    }

    /** v1.9.8：点视频进内置播放器（入口与查看器同款三处；播放器不返回
     *  结果，直接 startActivity，不用 launcher）。同时传同文件夹视频列表
     *  + 当前下标（按主列表当前排序），供底栏上一个/下一个切换；
     *  SAF 传 URI 列表，根目录拿不到列表（传空，按钮置灰）。 */
    private fun openVideoPlayer(pane: Pane, path: String? = null, uri: String? = null, originalPath: String? = null) {
        startActivity(Intent(this, VideoPlayerActivity::class.java).apply {
            path?.let { putExtra(VideoPlayerActivity.EXTRA_PATH, it) }
            uri?.let { putExtra(VideoPlayerActivity.EXTRA_URI, it) }
            originalPath?.let { putExtra(VideoPlayerActivity.EXTRA_ORIGINAL_PATH, it) }
            val (list, index, isUri) = videoPlaylist(pane, path, uri)
            putStringArrayListExtra(VideoPlayerActivity.EXTRA_VIDEO_LIST, ArrayList(list))
            putExtra(VideoPlayerActivity.EXTRA_VIDEO_INDEX, index)
            putExtra(VideoPlayerActivity.EXTRA_VIDEO_IS_URI, isUri)
        })
    }

    /** v1.9.8：九宫格里点“浏览压缩包”进浏览页（入口与查看器/播放器同款
     *  三处；不解压，只列目录）。 */
    private fun openArchiveBrowser(pane: Pane, path: String? = null, uri: String? = null, originalPath: String? = null) {
        startActivity(Intent(this, ArchiveBrowserActivity::class.java).apply {
            path?.let { putExtra(ArchiveBrowserActivity.EXTRA_PATH, it) }
            uri?.let { putExtra(ArchiveBrowserActivity.EXTRA_URI, it) }
            originalPath?.let { putExtra(ArchiveBrowserActivity.EXTRA_ORIGINAL_PATH, it) }
        })
    }

    /** v1.9.8：点音频进内置音乐播放器（入口与视频播放器同款三处；
     *  直接 startActivity，不用 launcher）。同时传同文件夹音频列表
     *  + 当前下标（按主列表当前排序），供底栏上一个/下一个切换；
     *  SAF 传 URI 列表，根目录拿不到列表（传空，按钮置灰）。 */
    private fun openMusicPlayer(pane: Pane, path: String? = null, uri: String? = null, originalPath: String? = null) {
        startActivity(Intent(this, MusicPlayerActivity::class.java).apply {
            path?.let { putExtra(MusicPlayerActivity.EXTRA_PATH, it) }
            uri?.let { putExtra(MusicPlayerActivity.EXTRA_URI, it) }
            originalPath?.let { putExtra(MusicPlayerActivity.EXTRA_ORIGINAL_PATH, it) }
            val (list, index, isUri) = audioPlaylist(pane, path, uri)
            putStringArrayListExtra(MusicPlayerActivity.EXTRA_AUDIO_LIST, ArrayList(list))
            putExtra(MusicPlayerActivity.EXTRA_AUDIO_INDEX, index)
            putExtra(MusicPlayerActivity.EXTRA_AUDIO_IS_URI, isUri)
        })
    }

    /** v1.9.8：九宫格里播放视频 / 查看图片 / 浏览压缩包的三路传参
     *  （与 onFileClick 同款：普通文件传路径，SAF 传 URI，根目录先
     *  stageRootFile 暂存再传暂存路径 + 真实路径）。 */
    internal fun openGridVideo(pane: Pane, f: File) = openGridTarget(pane, f, GridTarget.VIDEO)
    internal fun openGridImage(pane: Pane, f: File) = openGridTarget(pane, f, GridTarget.IMAGE)
    internal fun openGridArchive(pane: Pane, f: File) = openGridTarget(pane, f, GridTarget.ARCHIVE)
    internal fun openGridText(pane: Pane, f: File) = openGridTarget(pane, f, GridTarget.TEXT)
    /** v1.9.8：九宫格里点“播放音乐”——音频和视频都能点（点视频只放
     *  视频里的声音，不显示画面，见 MusicPlayerActivity）。 */
    internal fun openGridMusic(pane: Pane, f: File) = openGridTarget(pane, f, GridTarget.MUSIC)

    private enum class GridTarget { VIDEO, IMAGE, ARCHIVE, TEXT, MUSIC }

    private fun openGridTarget(pane: Pane, f: File, target: GridTarget) {
        val open: (path: String?, uri: String?, originalPath: String?) -> Unit = { path, uri, originalPath ->
            when (target) {
                GridTarget.VIDEO -> openVideoPlayer(pane, path = path, uri = uri, originalPath = originalPath)
                GridTarget.IMAGE -> openImageViewer(pane, path = path, uri = uri, originalPath = originalPath)
                GridTarget.ARCHIVE -> openArchiveBrowser(pane, path = path, uri = uri, originalPath = originalPath)
                GridTarget.MUSIC -> openMusicPlayer(pane, path = path, uri = uri, originalPath = originalPath)
                // 文本：照 onFileClick 的文本分支（普通文件 EXTRA_PATH，
                // SAF 传 EXTRA_URI；根目录不在这里走，见下面的 shizukuMode 分支）。
                GridTarget.TEXT -> startActivity(Intent(this, TextEditorActivity::class.java).apply {
                    path?.let { putExtra(TextEditorActivity.EXTRA_PATH, it) }
                    uri?.let { putExtra(TextEditorActivity.EXTRA_URI, it) }
                })
            }
        }
        when {
            pane.safMode -> {
                val entry = pane.safEntries.firstOrNull { it.keyPath == f.absolutePath }
                if (entry == null) {
                    Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
                    return
                }
                open(null, entry.uri.toString(), null)
            }
            pane.shizukuMode -> {
                val entry = pane.shizukuEntries.firstOrNull { it.path == f.absolutePath }
                if (entry == null) {
                    Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
                    return
                }
                Thread {
                    val staged = stageRootFile(entry.path)
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        if (staged == null) {
                            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
                            return@runOnUiThread
                        }
                        // 文本：照 openRootFile 的暂存+写回路子（pendingRootEdit +
                        // rootEditorLauncher，退出时 shell cp 写回真实路径）。
                        if (target == GridTarget.TEXT) {
                            pendingRootEdit = RootEdit(entry.path, staged, staged.lastModified(), staged.length())
                            rootEditorLauncher.launch(Intent(this, TextEditorActivity::class.java).apply {
                                putExtra(TextEditorActivity.EXTRA_PATH, staged.absolutePath)
                            })
                            return@runOnUiThread
                        }
                        open(staged.absolutePath, null, entry.path)
                    }
                }.start()
            }
            else -> open(f.absolutePath, null, null)
        }
    }

    /** 同文件夹视频播放列表（按主列表当前排序）：普通文件是路径列表，
     *  SAF 是 URI 列表，根目录返回空列表（上一个/下一个置灰）。 */
    private fun videoPlaylist(pane: Pane, path: String?, uri: String?): Triple<List<String>, Int, Boolean> {
        if (uri != null) {
            val videos = pane.safEntries.filter { !it.isDir && FileUtils.isVideo(File(it.name)) }
            val list = videos.map { it.uri.toString() }
            return Triple(list, videos.indexOfFirst { it.uri.toString() == uri }, true)
        }
        if (path != null) {
            // 根目录（Shizuku 暂存副本）不在 pane.files 里：拿不到列表。
            if (pane.shizukuMode) return Triple(emptyList(), -1, false)
            val videos = pane.files.filter { !it.isDirectory && FileUtils.isVideo(it) }
            val list = videos.map { it.absolutePath }
            return Triple(list, videos.indexOfFirst { it.absolutePath == path }, false)
        }
        return Triple(emptyList(), -1, false)
    }

    /** 同文件夹音频播放列表（按主列表当前排序）：普通文件是路径列表，
     *  SAF 是 URI 列表，根目录返回空列表（上一个/下一个置灰）。
     *  九宫格“播放音乐”点视频文件进来时：当前文件不在音频列表里，
     *  下标 -1，两个按钮都置灰（只放这一段视频的声音）。 */
    private fun audioPlaylist(pane: Pane, path: String?, uri: String?): Triple<List<String>, Int, Boolean> {
        if (uri != null) {
            val audios = pane.safEntries.filter { !it.isDir && FileUtils.isAudio(File(it.name)) }
            val list = audios.map { it.uri.toString() }
            return Triple(list, audios.indexOfFirst { it.uri.toString() == uri }, true)
        }
        if (path != null) {
            // 根目录（Shizuku 暂存副本）不在 pane.files 里：拿不到列表。
            if (pane.shizukuMode) return Triple(emptyList(), -1, false)
            val audios = pane.files.filter { !it.isDirectory && FileUtils.isAudio(it) }
            val list = audios.map { it.absolutePath }
            return Triple(list, audios.indexOfFirst { it.absolutePath == path }, false)
        }
        return Triple(emptyList(), -1, false)
    }

    inner class Pane(val index: Int) {
        var currentDir: File by mutableStateOf(Environment.getExternalStorageDirectory())
        val backStack = mutableStateListOf<File>()
        val forwardStack = mutableStateListOf<File>()
        // Compose 状态：列表/选中/搜索一变，LazyColumn 自动重组。
        var files: List<File> by mutableStateOf(emptyList())
        val selected = mutableStateSetOf<File>()
        var searchQuery: String by mutableStateOf("")
        // Shizuku 模式：当前窗格在浏览根目录（File 读不到，用 ls -la 的解析结果）。
        var shizukuMode: Boolean by mutableStateOf(false)
        var shizukuEntries: List<ShizukuRoot.Entry> by mutableStateOf(emptyList())
        // v1.8.0：根目录子目录读失败时记下错误原因，空列表里直接显示给用户看，
        // 不再留着旧列表（旧列表配新路径会误导人），也不再只显示“这里是空的”。
        var shizukuError: String? by mutableStateOf(null)
        // SAF 模式：当前窗格在浏览一个 File 读不到的已添加储存（比如别家 App
        // 的目录，由 DocumentsProvider 暴露），用 DocumentsContract 列目录。
        var safMode: Boolean by mutableStateOf(false)
        var safStorage: CustomStorages.Storage? by mutableStateOf(null)
        // SAF 浏览栈：一串（文档 ID，显示名），栈顶是当前目录。
        var safStack = mutableStateListOf<SafDir>()
        // 后退 / 前进栈：每次存一份 safStack 的快照。
        var safBackStack = mutableStateListOf<List<SafDir>>()
        var safForwardStack = mutableStateListOf<List<SafDir>>()
        var safEntries: List<SafEntry> by mutableStateOf(emptyList())
        // 合成路径（/saf/…）→ 真实文档 URI，点行时用它进目录 / 打开文件。
        var safUriByPath: Map<String, Uri> by mutableStateOf(emptyMap())
        // 行显示覆盖（Shizuku/SAF 模式：File 本身读不到属性，用解析结果提供）。
        var isDirOverride: Map<String, Boolean> by mutableStateOf(emptyMap())
        var sizeOverride: Map<String, Long> by mutableStateOf(emptyMap())
        var dateOverride: Map<String, Long> by mutableStateOf(emptyMap())
        var dirShowsFolderLabel: Boolean by mutableStateOf(false)
        var pinnedKeys: Set<String> by mutableStateOf(emptySet())
        // 是否显示“..”父目录行；空目录提示文字。
        var showParent: Boolean by mutableStateOf(false)
        var emptyText: String by mutableStateOf("")
        var showEmpty: Boolean by mutableStateOf(false)
        // 文件夹递归大小缓存（MP 样式副标题用，后台算 + 缓存，见 FileAdapter 旧实现）。
        val dirSizeCache = mutableStateMapOf<String, Long>()
        // v1.9.8.1：加载代次——loadPane 每次清缓存时 +1，行里的 LaunchedEffect
        // 以它为 key，同一路径复用旧组合时也能重新算大小（否则清缓存后大小永久丢失）。
        var loadSeq by mutableIntStateOf(0)
        // 当前窗格是否激活（顶栏/路径条高亮用）。
        var isActive: Boolean by mutableStateOf(false)
    }

    private fun panes(): List<Pane> = listOf(left, right)
    internal fun activePane(): Pane = if (active == 0) left else right
    internal fun otherPane(): Pane = if (active == 0) right else left

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
        prefs = getSharedPreferences("fairu_prefs", MODE_PRIVATE)
        dualPane = prefs.getBoolean("dual_pane", true)

        left = Pane(0)
        right = Pane(1)
        left.isActive = true

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerOpen) { drawerOpen = false; return }
                val p = activePane()
                if (p.selected.isNotEmpty()) { clearSelection(p); return }
                if (goBack()) return
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })

        setContent {
            FairuTheme { MainScreen() }
        }

        checkPermissions()
        load()
        // v1.9.1：桌面快捷方式点进来时按动作定位/打开。
        handleShortcutIntent(intent)
    }

    internal fun setActive(pane: Pane) {
        if (active != pane.index) {
            active = pane.index
            left.isActive = active == 0
            right.isActive = active == 1
        }
    }

    internal fun applyDualPane() {
        if (!::left.isInitialized || !::right.isInitialized) return
        if (!dualPane) active = 0
        left.isActive = active == 0
        right.isActive = active == 1
        load()
    }


    // ---------- 抽屉菜单 ----------

    /** 点抽屉里的目录项：切换当前活动窗格的目录并关抽屉；进不去就友好提示，绝不闪退。
     *  从 Shizuku 模式切回普通目录时先退出 Shizuku 模式。 */
    internal fun navigateDrawerTo(dir: File) {
        drawerOpen = false
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
    internal fun navigateDrawerToStorage(s: CustomStorages.Storage) {
        drawerOpen = false
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
    internal fun refreshDrawerInfo() {
        try {
            val (rootTotal, rootFree) = FileUtils.storageInfo(File("/"))
            if (rootTotal > 0) {
                val used = rootTotal - rootFree
                val pct = ((used * 100) / rootTotal).toInt()
                drawerRootPct = pct
                drawerRootInfo = getString(R.string.used_space) + " " + FileUtils.formatSize(used) +
                    "  ·  " + getString(R.string.free_space) + " " + FileUtils.formatSize(rootFree) +
                    "  ·  $pct%"
            } else {
                drawerRootPct = 0
                drawerRootInfo = getString(R.string.cannot_access)
            }
            val (intTotal, intFree) = FileUtils.storageInfo(Environment.getExternalStorageDirectory())
            if (intTotal > 0) {
                val used = intTotal - intFree
                val pct = ((used * 100) / intTotal).toInt()
                drawerInternalPct = pct
                drawerInternalInfo = getString(R.string.used_space) + " " + FileUtils.formatSize(used) +
                    "  ·  " + getString(R.string.free_space) + " " + FileUtils.formatSize(intFree) +
                    "  ·  $pct%"
            } else {
                drawerInternalPct = 0
                drawerInternalInfo = getString(R.string.cannot_access)
            }
        } catch (_: Exception) { }
    }

    /** 抽屉头版本号（从 PackageManager 读，升级不用改代码）。 */
    internal fun drawerVersionText(): String {
        return try {
            val pi = packageManager.getPackageInfo(packageName, 0)
            val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) pi.longVersionCode else pi.versionCode.toLong()
            getString(R.string.version) + " " + pi.versionName + " ($code)"
        } catch (_: Exception) {
            getString(R.string.version) + " 1.9.7.1 (22)"
        }
    }


    // ---------- 浏览 / 搜索 / 排序 ----------

    internal fun load() {
        if (!::left.isInitialized || !::right.isInitialized) return
        loadPane(left)
        if (dualPane) loadPane(right)
        refreshDrawerInfo()
    }

    internal fun loadPane(pane: Pane) {
        if (!::left.isInitialized) return
        // v1.9.0：置顶的文件夹排最前面（Pin folder），行末尾显示图钉标记。
        pane.pinnedKeys = PinnedFolders.pinnedKeys(this)
        if (pane.safMode) {
            // SAF 模式：File 读不到这目录，用 DocumentsContract 读到的条目拼
            // File 列表（合成路径只当 key）；目录/大小/日期由 override 提供，
            // 跟 Shizuku 模式同一套做法。Compose 行直接读这些状态。
            var entries = pane.safEntries
            if (pane.searchQuery.isNotBlank()) {
                val q = pane.searchQuery.lowercase()
                entries = entries.filter { it.name.lowercase().contains(q) }
            }
            pane.files = applyPins(entries.map { File(it.keyPath) })
            pane.isDirOverride = entries.associate { it.keyPath to it.isDir }
            pane.sizeOverride = entries.associate { it.keyPath to it.size }
            pane.dateOverride = entries.associate { it.keyPath to it.modified }
            pane.dirShowsFolderLabel = true
            pane.showParent = pane.safStack.size > 1
            pane.dirSizeCache.clear()
            pane.loadSeq++
            pane.emptyText = getString(R.string.no_files)
            pane.showEmpty = pane.files.isEmpty() && pane.safStack.size <= 1
            return
        }
        if (pane.shizukuMode) {
            // Shizuku 模式：用上次 ls -la 读到的条目拼 File 列表；
            // 目录/大小/日期由 override 提供（File 本身读不到）。
            var entries = pane.shizukuEntries
            if (pane.searchQuery.isNotBlank()) {
                val q = pane.searchQuery.lowercase()
                entries = entries.filter { it.name.lowercase().contains(q) }
            }
            pane.files = applyPins(entries.map { File(it.path) })
            pane.isDirOverride = entries.associate { it.path to it.isDir }
            pane.sizeOverride = entries.associate { it.path to it.size }
            pane.dateOverride = entries.associate { it.path to it.modified }
            pane.dirShowsFolderLabel = true
            pane.showParent = pane.currentDir.parentFile != null
            pane.dirSizeCache.clear()
            pane.loadSeq++
            // v1.8.0：读失败时空列表里直接显示错误原因（refreshShizukuList
            // 失败时把 shizukuError 填上），不再只显示“这里是空的”。
            pane.emptyText = pane.shizukuError ?: getString(R.string.no_files)
            pane.showEmpty = pane.files.isEmpty()
            return
        }
        pane.isDirOverride = emptyMap()
        pane.sizeOverride = emptyMap()
        pane.dateOverride = emptyMap()
        pane.dirShowsFolderLabel = false
        val showHidden = prefs.getBoolean("show_hidden", false)
        val sort = prefs.getInt("sort", 0)
        val reverse = prefs.getBoolean("sort_reverse", false)
        val typeFoldersOnly = prefs.getBoolean("sort_type_folders_only", false)
        var list: List<File> = pane.currentDir.listFiles()?.toList() ?: emptyList()
        if (!showHidden) list = list.filter { !it.name.startsWith(".") }
        if (pane.searchQuery.isNotBlank()) {
            val q = pane.searchQuery.lowercase()
            list = searchRecursive(pane.currentDir, q)
        }
        list = sortFileList(list, sort, reverse, typeFoldersOnly)
        list = applyPins(list)
        pane.files = list
        pane.showParent = pane.currentDir.parentFile != null
        pane.dirSizeCache.clear()
            pane.loadSeq++
        pane.emptyText = getString(R.string.no_files)
        pane.showEmpty = list.isEmpty() && !pane.showParent
    }

    /** v1.9.0 Pin folder：已置顶的文件夹排到列表最前面（其余顺序不变）。 */
    private fun applyPins(list: List<File>): List<File> {
        val pins = PinnedFolders.pinnedKeys(this)
        if (pins.isEmpty()) return list
        val (pinned, rest) = list.partition { pins.contains(it.absolutePath) }
        return pinned + rest
    }

    private fun searchRecursive(dir: File, q: String, isCancelled: () -> Boolean = { false }): List<File> {
        val out = mutableListOf<File>()
        val children = dir.listFiles() ?: return out
        for (c in children) {
            if (isCancelled()) return out
            if (c.name.lowercase().contains(q)) out.add(c)
            if (c.isDirectory && !c.name.startsWith(".")) {
                if (out.size < 500) out.addAll(searchRecursive(c, q, isCancelled))
            }
        }
        return out
    }

    // v1.9.7.8：搜索防抖 + 后台线程。之前每次敲键都在 onValueChange 里
    // 同步调 loadPane，主线程上递归扫整棵树（Max 手机上 /storage/emulated/0
    // 又大又深），每敲一个字母就冻住一阵；主线程一卡，输入法连接就丢，
    // 查询被清空、列表跳回完整目录、大小缓存也被清掉、副标题跟着闪。
    // 现在敲键只改 searchQuery（立即返回、不扫盘），250ms 后在后台线程
    // 扫，算完只有查询没变才上屏；旧查询的线程算完直接丢弃。
    @Volatile
    private var searchToken = 0

    internal fun onSearchQueryChanged(pane: Pane, q: String) {
        pane.searchQuery = q
        val token = ++searchToken
        val dir = pane.currentDir
        if (q.isBlank()) { loadPane(pane); return }
        // SAF / Shizuku：只是在内存里过滤已有条目，不扫盘，走原来同步路。
        if (pane.safMode || pane.shizukuMode) { loadPane(pane); return }
        Thread {
            try { Thread.sleep(250) } catch (_: InterruptedException) { }
            if (token != searchToken) return@Thread
            val results = searchRecursive(dir, q.lowercase()) { token != searchToken }
            if (token != searchToken) return@Thread
            val sort = prefs.getInt("sort", 0)
            val reverse = prefs.getBoolean("sort_reverse", false)
            val typeFoldersOnly = prefs.getBoolean("sort_type_folders_only", false)
            val pinned = applyPins(sortFileList(results, sort, reverse, typeFoldersOnly))
            runOnUiThread {
                if (token != searchToken) return@runOnUiThread
                if (pane.searchQuery != q) return@runOnUiThread
                if (isFinishing || isDestroyed) return@runOnUiThread
                pane.files = pinned
                pane.showParent = dir.parentFile != null
                // 注意：不像 loadPane 那样清 dirSizeCache——已算好的大小
                // 留着，副标题不闪；只 +loadSeq 让缺大小的行去算。
                pane.loadSeq++
                pane.emptyText = getString(R.string.no_files)
                pane.showEmpty = pinned.isEmpty() && !pane.showParent
            }
        }.start()
    }

    /** 列表排序（loadPane 和后台搜索共用；规则见 FileUtils.sortNamed）。 */
    private fun sortFileList(list: List<File>, sort: Int, reverse: Boolean, typeFoldersOnly: Boolean): List<File> {
        return FileUtils.sortNamed(list, sort, reverse, typeFoldersOnly,
            { it.isDirectory }, { it.name }, { it.length() }, { it.lastModified() })
    }

    // v1.9.8.1：顶栏是 Compose，标题/副标题/隐藏开关直接读窗格状态，
    // 不再由这里推 View。保留空函数体是让旧调用点照常编译。
    private fun updateTopBar() { }

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

    internal fun navigateUp(pane: Pane) {
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

    internal fun goBack(): Boolean {
        val p = activePane()
        if (p.safMode) {
            if (p.safBackStack.isEmpty()) return false
            p.safForwardStack.add(p.safStack.toList())
            p.safStack = p.safBackStack.removeAt(p.safBackStack.lastIndex).toMutableStateList()
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

    internal fun goForward(): Boolean {
        val p = activePane()
        if (p.safMode) {
            if (p.safForwardStack.isEmpty()) return false
            p.safBackStack.add(p.safStack.toList())
            p.safStack = p.safForwardStack.removeAt(p.safForwardStack.lastIndex).toMutableStateList()
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

    internal fun swapPanes() {
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

    internal fun onFileClick(pane: Pane, f: File) {
        if (pane.selected.isNotEmpty()) { toggleSelect(pane, f); return }
        if (pane.safMode) {
            // SAF 模式：目录进得去；文本文件进内置编辑器（读流进来、保存写流回去），
            // 别的用 ACTION_VIEW + content URI 打开。
            val entry = pane.safEntries.firstOrNull { it.keyPath == f.absolutePath } ?: return
            if (entry.isDir) { enterSafDir(pane, entry); return }
            // v1.9.8：.html/.htm 点了直接进内置文本编辑器看代码，不弹浏览器/打开方式。
            if (FileUtils.isHtml(File(entry.name))) { openTextEditor(pane, f); return }
            if (FileUtils.isText(File(entry.name))) {
                startActivity(Intent(this, TextEditorActivity::class.java).apply {
                    putExtra(TextEditorActivity.EXTRA_URI, entry.uri.toString())
                })
                return
            }
            if (FileUtils.isImage(File(entry.name))) {
                // v1.9.8：SAF 里的图片进内置查看器（传文档 URI）。
                openImageViewer(pane, uri = entry.uri.toString())
                return
            }
            if (FileUtils.isVideo(File(entry.name))) {
                // v1.9.8：SAF 里的视频进内置播放器（传文档 URI）。
                openVideoPlayer(pane, uri = entry.uri.toString())
                return
            }
            if (FileUtils.isAudio(File(entry.name))) {
                // v1.9.8：SAF 里的音频进内置音乐播放器（传文档 URI）。
                openMusicPlayer(pane, uri = entry.uri.toString())
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
        // v1.9.8：.html/.htm 点了直接进内置文本编辑器看代码，不弹浏览器/打开方式。
        if (FileUtils.isHtml(f)) { openTextEditor(pane, f); return }
        if (FileUtils.isText(f)) {
            // 文本文件用内置编辑器打开。
            startActivity(Intent(this, TextEditorActivity::class.java).apply {
                putExtra(TextEditorActivity.EXTRA_PATH, f.absolutePath)
            })
            return
        }
        if (FileUtils.isImage(f)) {
            // v1.9.8：图片进内置查看器（不走 openWith）。
            openImageViewer(pane, path = f.absolutePath)
            return
        }
        if (FileUtils.isVideo(f)) {
            // v1.9.8：视频进内置播放器（不走 openWith）。
            openVideoPlayer(pane, path = f.absolutePath)
            return
        }
        if (FileUtils.isAudio(f)) {
            // v1.9.8：音频进内置音乐播放器（不走 openWith）。
            openMusicPlayer(pane, path = f.absolutePath)
            return
        }
        openWith(f)
    }

    internal fun toggleSelect(pane: Pane, f: File) {
        // v1.8.0：根目录（Shizuku）也能多选了（以前直接弹“仅支持浏览”）。
        // SAF 窗格也能多选：选中态按合成路径（File(keyPath)）记，操作时再按
        // 合成路径找回 SafEntry。
        if (pane.selected.contains(f)) pane.selected.remove(f) else pane.selected.add(f)
        updateSelectionTopBar()
    }

    internal fun clearSelection(pane: Pane) {
        pane.selected.clear()
        updateSelectionTopBar()
    }

    /** v1.8.0 多选模式顶栏（照 Max 的参考图）：有选中项时顶替掉工具栏，
     *  显示“已选择 N 项”+ 已选总大小；没选中项时恢复正常工具栏。
     *  底部的选择操作条删了，这套顶栏是唯一的选中操作入口。 */
    // v1.9.8.1：多选顶栏是 Compose，直接读 selected 状态；同上留空函数体。
    private fun updateSelectionTopBar() { }

    /** 已选中的总大小：普通文件按实际大小（文件夹递归算）；SAF 按列表里的
     *  大小；根目录按 ls 的大小（解析不上的条目是 0，算不进去）。 */
    internal fun selectionTotalSize(pane: Pane): Long {
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

    internal fun copySelection() {
        val p = activePane()
        clipboard = selectionToClipboard(p); clipboardCut = false
        clearSelection(p); snackbar(R.string.copied)
    }

    internal fun cutSelection() {
        val p = activePane()
        clipboard = selectionToClipboard(p); clipboardCut = true
        clearSelection(p); snackbar(R.string.copied)
    }

    /** 粘贴到指定窗格（底部粘贴按钮用活动窗格，菜单“粘贴到另一窗格”用另一窗格）：
     *  目标是 SAF 目录就走 SAF 复制，根目录走 Shizuku shell，普通目录走原来的
     *  File 复制；剪贴板里 File 项、SAF 项和根目录项可以混装。失败的项留在
     *  剪贴板里，不丢。 */
    internal fun pasteInto(targetPane: Pane) {
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
    data class DeleteStats(val size: Long, val files: Int, val dirs: Int, val countsKnown: Boolean)

    internal fun confirmDelete(targets: List<File>) {
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

    internal fun showDeleteDialog(pane: Pane, targets: List<File>, stats: DeleteStats) {
        deleteDialog = DeleteRequest(pane, targets, stats)
    }

    /** 删除的执行：勾选进回收站（移不进去就明确提示、不删文件），
     *  不勾选永久删除。 */
    internal fun doDelete(pane: Pane, targets: List<File>, toTrash: Boolean) {
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
            // v1.9.8.1：永久删除也要重载列表，否则删掉的文件还留在列表里
            // （搜索结果里删完也一样）；进回收站/SAF/根目录三路原来就有刷新。
            runOnUiThread { clearSelection(pane); snackbar(R.string.deleted); refreshPane(pane) }
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
    internal fun showRenameDialog(pane: Pane, f: File) {
        renameDialog = RenameRequest(pane, f, 0)
    }

    /** 根目录项重命名：Shizuku mv。失败（没权限 / 重名）弹“出错了”，
     *  成功清选择态、刷新列表。 */
    internal fun showShizukuRenameDialog(pane: Pane, entry: ShizukuRoot.Entry) {
        renameDialog = RenameRequest(pane, File(entry.path), 1, shizukuEntry = entry)
    }

    /** SAF 项重命名：DocumentsContract.renameDocument，成功刷新列表。 */
    internal fun showSafRenameDialog(pane: Pane, entry: SafEntry) {
        renameDialog = RenameRequest(pane, File(entry.keyPath), 2, safEntry = entry)
    }

    /** 重命名执行（Compose 重命名对话框确认后调；三态各走各的原逻辑）。 */
    internal fun doRename(request: RenameRequest, newName: String) {
        val pane = request.pane
        when (request.kind) {
            1 -> {
                val entry = request.shizukuEntry ?: return
                val parent = entry.path.substringBeforeLast('/', "")
                val dst = if (parent.isEmpty()) "/$newName" else "$parent/$newName"
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
            2 -> {
                val entry = request.safEntry ?: return
                val treeUri = pane.safStorage?.let { try { Uri.parse(it.uri) } catch (_: Exception) { null } }
                val parentDocId = pane.safStack.lastOrNull()?.docId
                Log.d(TAG, "SAF rename ${entry.name} -> $newName (parentDocId=$parentDocId)")
                runSafOpTimeout(30000L, block = {
                    val freshUri = if (treeUri != null && parentDocId != null) {
                        try { listSafChildren(treeUri, parentDocId)?.firstOrNull { it.name == entry.name }?.uri } catch (e: Exception) { Log.w(TAG, "SAF rename re-list threw: $e"); null }
                    } else null
                    safRenameDocument(freshUri ?: entry.uri, newName)
                }, onDone = { ok ->
                    if (ok) { clearSelection(pane); snackbar(R.string.renamed); refreshSafList(pane) }
                    else snackbar(R.string.error)
                })
            }
            else -> {
                val f = request.file
                runBg {
                    f.renameTo(File(f.parentFile, newName))
                    runOnUiThread { clearSelection(pane); loadPane(pane); snackbar(R.string.renamed) }
                }
            }
        }
    }


    /** 新建的名字校验：非空、不含 "/"、不重名。 */
    internal fun checkNewName(pane: Pane, name: String): Boolean {
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

    /** v1.9.8 路径跳转：当前窗格跳到输入的路径。
     *  自己能列出来的普通目录直接进（走 loadPane）；自己列不出来时，
     *  Shizuku 可用就用 `test -d` 验一下，是文件夹就进 Shizuku 模式；
     *  路径不存在 / 不是文件夹 / Shizuku 不可用都只弹提示，不跳转、不崩溃。
     *  相对路径按当前目录算。SAF 窗格跳到普通路径时先退出 SAF 模式。 */
    internal fun jumpToPath(pane: Pane, rawPath: String) {
        val path = rawPath.trim()
        if (path.isEmpty()) {
            Toast.makeText(this, R.string.jump_invalid_path, Toast.LENGTH_SHORT).show()
            return
        }
        val dir = if (path.startsWith("/")) File(path) else File(pane.currentDir, path)
        val absPath = dir.absolutePath
        if (absPath == pane.currentDir.absolutePath) return // 已经在这儿了
        if (dir.isDirectory && dir.listFiles() != null) {
            exitSafMode(pane)
            exitShizukuMode(pane)
            pane.backStack.add(pane.currentDir)
            pane.forwardStack.clear()
            pane.currentDir = dir
            pane.searchQuery = ""
            pane.selected.clear()
            loadPane(pane)
            return
        }
        if (ShizukuRoot.isRunning() && ShizukuRoot.isAuthorized()) {
            Thread {
                val isDir = try {
                    ShizukuRoot.exec("test", "-d", absPath, timeoutMs = 10000L).exit == 0
                } catch (_: Throwable) { false }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (!isDir) {
                        Toast.makeText(this, R.string.jump_invalid_path, Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }
                    exitSafMode(pane)
                    pane.backStack.add(pane.currentDir)
                    pane.forwardStack.clear()
                    pane.shizukuMode = true
                    pane.currentDir = File(absPath)
                    pane.searchQuery = ""
                    pane.selected.clear()
                    refreshShizukuList(pane)
                }
            }.start()
            return
        }
        Toast.makeText(this, R.string.jump_invalid_path, Toast.LENGTH_SHORT).show()
    }

    /** v1.9.8 创建 HTML：在当前窗格文件夹里新建一个 .html 文件。
     *  名字已带 .html 不再重复加；空名 / 含 "/" / 已存在都弹提示、不创建
     *  （复用 checkNewName）。建好直接自动打开内置文本编辑器（新建的空文件，
     *  进编辑器就能写）；列表刷新照常走。
     *  创建本身按窗格模式分三路（普通 / 根目录 / SAF），不复用 createFile：
     *  建好那一刻就要开编辑器——普通路文件已在磁盘上、SAF 路建文档直接拿到
     *  URI、根目录路用刚建的路径暂存打开，都不用等列表刷新回来再找条目。 */
    internal fun createHtmlFile(pane: Pane, rawName: String) {
        var name = rawName.trim()
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.invalid_name, Toast.LENGTH_SHORT).show()
            return
        }
        if (!name.endsWith(".html", ignoreCase = true)) name += ".html"
        if (!checkNewName(pane, name)) return
        when {
            pane.shizukuMode -> {
                val dir = pane.currentDir.absolutePath
                val path = if (dir == "/") "/$name" else "$dir/$name"
                Thread {
                    val r = ShizukuRoot.exec("touch", path)
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        if (r.exit == 0) {
                            snackbar(R.string.created)
                            refreshShizukuList(pane)
                            // 根目录文件 App 自己读不了，走暂存打开编辑器。
                            openRootFile(pane, ShizukuRoot.Entry(name, path, false, 0L, System.currentTimeMillis()))
                        } else snackbar(R.string.error)
                    }
                }.start()
            }
            pane.safMode -> {
                val treeUri = pane.safStorage?.let { try { Uri.parse(it.uri) } catch (_: Exception) { null } }
                val parentDocId = pane.safStack.lastOrNull()?.docId
                if (treeUri == null || parentDocId == null) return
                Thread {
                    val uri = safCreateFile(treeUri, parentDocId, name, safMimeFor(name))
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        if (uri != null) {
                            snackbar(R.string.created)
                            refreshSafList(pane)
                            // SAF 文档按 URI 进编辑器，保存写回原处。
                            startActivity(Intent(this, TextEditorActivity::class.java).apply {
                                putExtra(TextEditorActivity.EXTRA_URI, uri.toString())
                            })
                        } else snackbar(R.string.error)
                    }
                }.start()
            }
            else -> runBg {
                val ok = try { File(pane.currentDir, name).createNewFile() } catch (_: Exception) { false }
                runOnUiThread {
                    if (ok) {
                        snackbar(R.string.created)
                        openTextEditor(pane, File(pane.currentDir, name))
                    } else snackbar(R.string.error)
                }
            }
        }
    }

    /** 在当前目录新建空文件（普通 / 根目录 / SAF 三态）。 */
    internal fun createFile(pane: Pane, name: String) {        when {
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
    internal fun createFolder(pane: Pane, name: String) {
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
    internal fun showFileMenu(pane: Pane, f: File) {
        fileMenu = FileMenuRequest(pane, f)
    }

    /** 行 ⋮ 菜单里“复制/剪切”：按窗格模式把该项放进剪贴板（普通 / 根目录 / SAF）。 */
    internal fun copyItemToClipboard(pane: Pane, f: File, cut: Boolean) {
        when {
            pane.safMode -> {
                val entry = pane.safEntries.firstOrNull { it.keyPath == f.absolutePath } ?: return
                val treeUri = pane.safStorage?.let { try { Uri.parse(it.uri) } catch (_: Exception) { null } }
                val parentDocId = pane.safStack.lastOrNull()?.docId
                clipboard = listOf(ClipEntry.ofSaf(entry, treeUri, parentDocId))
            }
            pane.shizukuMode -> clipboard = listOf(ClipEntry.ofRoot(f))
            else -> clipboard = listOf(ClipEntry.ofFile(f))
        }
        clipboardCut = cut
        snackbar(R.string.copied)
    }

    /** 行 ⋮ 菜单里“编辑文本”：普通文件按路径进编辑器，SAF 按 URI 进。 */
    internal fun openTextEditor(pane: Pane, f: File) {
        if (pane.safMode) {
            val entry = pane.safEntries.firstOrNull { it.keyPath == f.absolutePath } ?: return
            startActivity(Intent(this, TextEditorActivity::class.java).apply {
                putExtra(TextEditorActivity.EXTRA_URI, entry.uri.toString())
            })
        } else {
            startActivity(Intent(this, TextEditorActivity::class.java).apply {
                putExtra(TextEditorActivity.EXTRA_PATH, f.absolutePath)
            })
        }
    }

    /** 行 ⋮ 菜单里“属性”：按窗格模式分发到对应的属性对话框。 */
    internal fun showPropertiesFor(pane: Pane, f: File) {
        when {
            pane.safMode -> pane.safEntries.firstOrNull { it.keyPath == f.absolutePath }?.let { showSafProperties(pane, it) }
            pane.shizukuMode -> pane.shizukuEntries.firstOrNull { it.path == f.absolutePath }?.let { showShizukuProperties(pane, it) }
            else -> showProperties(f)
        }
    }

    /** 行 ⋮ 菜单里“打开方式”：普通文件走 FileProvider，SAF 走 content URI。 */
    internal fun openWithFor(pane: Pane, f: File) {
        if (pane.safMode) {
            val entry = pane.safEntries.firstOrNull { it.keyPath == f.absolutePath } ?: return
            openSafFile(entry)
        } else {
            openWith(f)
        }
    }


    // ---------- v1.9.0：跨窗格移动 / 复制、压缩、分享 ZIP、书签、Pin ----------

    /** 按窗格当前模式刷新（普通 loadPane / SAF 刷新列表 / 根目录刷新列表）。 */
    internal fun refreshPane(pane: Pane) {
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
    internal fun otherPaneTargetValid(): Boolean {
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
    internal fun transferToOtherPane(pane: Pane, f: File, move: Boolean) {
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
    internal fun copyItemPath(pane: Pane, f: File) {
        val path = if (pane.safMode) safBreadcrumb(pane) + "/" + f.name else f.absolutePath
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("path", path))
            Toast.makeText(this, R.string.path_copied, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) { Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show() }
    }

    /** 压缩：把该项打成 name.zip 放当前目录（重名加 (1)），文件和文件夹都能压。 */
    internal fun compressItem(pane: Pane, f: File) {
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
    internal fun shareAsZip(pane: Pane, f: File) {
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
    internal fun showMoreActionsDialog(pane: Pane, f: File) {
        moreActionsDialog = MoreActionsRequest(pane, f)
    }

    // ---------- v1.9.1：创建快捷方式（照 MT 图4–图6） ----------

    /** 创建快捷方式对话框：图标 + 名称输入框 + 类型下拉 + 执行操作下拉，
     *  底部只有取消/确定（不要管理按钮；执行操作里不要执行脚本文件）。 */
    internal fun showCreateShortcutDialog(pane: Pane, f: File) {
        shortcutDialog = ShortcutRequest(pane, f)
    }

    /** 生成桌面快捷方式（ShortcutManager.requestPinShortcut）。 */
    internal fun pinShortcut(path: String, label: String, isDir: Boolean, useTypeIcon: Boolean, action: Int) {
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
    internal fun handleShortcutIntent(intent: Intent) {
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

    internal fun renameItem(pane: Pane, f: File) {
        when {
            pane.safMode -> pane.safEntries.firstOrNull { it.keyPath == f.absolutePath }?.let { showSafRenameDialog(pane, it) }
            pane.shizukuMode -> pane.shizukuEntries.firstOrNull { it.path == f.absolutePath }?.let { showShizukuRenameDialog(pane, it) }
            else -> showRenameDialog(pane, f)
        }
    }

    internal fun isDirOf(pane: Pane, f: File): Boolean = when {
        pane.safMode -> pane.safEntries.firstOrNull { it.keyPath == f.absolutePath }?.isDir ?: false
        pane.shizukuMode -> pane.shizukuEntries.firstOrNull { it.path == f.absolutePath }?.isDir ?: false
        else -> f.isDirectory
    }

    /** 隐藏 / 取消隐藏：改名加 "." 前缀（已隐藏的去掉 "."）。 */
    internal fun hideToggle(pane: Pane, f: File) {
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
    internal fun pinToggle(pane: Pane, f: File) {
        val nowPinned = PinnedFolders.toggle(this, f.absolutePath)
        Toast.makeText(this, if (nowPinned) R.string.pinned else R.string.unpinned, Toast.LENGTH_SHORT).show()
        loadPane(pane)
    }

    // ---------- v1.9.0：文件加密 / 解密、解压、书签 ----------

    /** 文件加密（仅文件）：弹密码框（输两遍防手误），AES 加密生成 .enc 文件，
     *  成功后删原文件。密码错只影响解密，加密本身不校验密码。 */
    internal fun encryptItem(pane: Pane, f: File) {
        passwordDialog = PasswordRequest(pane, f, encrypt = true)
    }

    internal fun doEncrypt(pane: Pane, f: File, password: String) {
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
    internal fun decryptItem(pane: Pane, f: File) {
        passwordDialog = PasswordRequest(pane, f, encrypt = false)
    }

    internal fun doDecrypt(pane: Pane, f: File, password: String) {
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
    internal fun unzipItem(pane: Pane, f: File) {
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

    internal fun bookmarkKind(pane: Pane): String = when {
        pane.safMode -> "saf"
        pane.shizukuMode -> "root"
        else -> "file"
    }

    internal fun bookmarkTarget(pane: Pane, f: File): String = when {
        pane.safMode -> pane.safEntries.firstOrNull { it.keyPath == f.absolutePath }?.uri?.toString() ?: f.absolutePath
        else -> f.absolutePath
    }

    /** 书签开关：没加过就加到抽屉书签分组，加过就取消。 */
    internal fun bookmarkToggle(pane: Pane, f: File) {
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
    internal fun refreshBookmarks() {
        if (!::prefs.isInitialized) return
        bookmarkRows = Bookmarks.list(this)
        bookmarksVersion++
    }

    /** 长按书签：删掉这条书签（只删记录，不动文件本身）。 */
    internal fun showBookmarkMenu(bookmark: Bookmarks.Bookmark) {
        bookmarkMenu = BookmarkMenuRequest(bookmark)
    }

    /** 点书签跳到对应位置：文件夹进目录，文件打开。 */
    internal fun openBookmark(b: Bookmarks.Bookmark) {
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
                    p.safStack = mutableStateListOf(SafDir(docId, b.name))
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
    internal fun shareFiles(pane: Pane, targets: List<File>) {
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
    internal fun showSafProperties(pane: Pane, entry: SafEntry) {
        val location = safBreadcrumb(pane) + "/" + entry.name
        if (entry.isDir) {
            Thread {
                val size = safDirSize(entry.uri)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    propertiesDialog = PropertiesRequest(safPropertiesMessage(entry, location, size))
                }
            }.start()
        } else {
            propertiesDialog = PropertiesRequest(safPropertiesMessage(entry, location, entry.size))
        }
    }

    private fun safPropertiesMessage(entry: SafEntry, location: String, size: Long): String {
        return "${getString(R.string.name)}: ${entry.name}\n${getString(R.string.path)}: $location\n" +
            "${getString(R.string.size)}: ${FileUtils.formatSize(size)}\n${getString(R.string.modified)}: ${FileUtils.formatDate(entry.modified)}\n" +
            "${getString(R.string.file)}: ${if (entry.isDir) getString(R.string.folder) else getString(R.string.file)}"
    }


    internal fun showProperties(f: File) {
        val size = if (f.isDirectory) FileUtils.dirSize(f) else f.length()
        val msg = "${getString(R.string.name)}: ${f.name}\n${getString(R.string.path)}: ${f.absolutePath}\n" +
            "${getString(R.string.size)}: ${FileUtils.formatSize(size)}\n${getString(R.string.modified)}: ${FileUtils.formatDate(f.lastModified())}\n" +
            "${getString(R.string.file)}: ${if (f.isDirectory) getString(R.string.folder) else getString(R.string.file)}"
        propertiesDialog = PropertiesRequest(msg)
    }

    /** v1.8.0 根目录项属性：名字、路径、大小、修改时间、类型。文件夹大小
     *  用 du -sb 现算（读不了的目录算不出来就显示“未知”）；解析不上的
     *  条目（大小/日期未知）也显示“未知”，不要编一个 1970 年出来。 */
    internal fun showShizukuProperties(pane: Pane, entry: ShizukuRoot.Entry) {
        if (entry.isDir) {
            Thread {
                val size = ShizukuRoot.exec("du", "-sb", entry.path, timeoutMs = 15000L).let { r ->
                    if (r.exit == 0) r.out.trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull() else null
                }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    propertiesDialog = PropertiesRequest(shizukuPropertiesMessage(entry, size))
                }
            }.start()
        } else {
            propertiesDialog = PropertiesRequest(shizukuPropertiesMessage(entry, entry.size))
        }
    }

    private fun shizukuPropertiesMessage(entry: ShizukuRoot.Entry, size: Long?): String {
        val unknown = entry.modified == 0L && size == null
        val sizeText = when {
            size != null -> FileUtils.formatSize(size)
            unknown -> getString(R.string.unknown)
            else -> FileUtils.formatSize(entry.size)
        }
        val modText = if (entry.modified == 0L) getString(R.string.unknown)
            else FileUtils.formatDate(entry.modified)
        return "${getString(R.string.name)}: ${entry.name}\n${getString(R.string.path)}: ${entry.path}\n" +
            "${getString(R.string.size)}: $sizeText\n${getString(R.string.modified)}: $modText\n" +
            "${getString(R.string.file)}: ${if (entry.isDir) getString(R.string.folder) else getString(R.string.file)}"
    }


    internal fun openWith(f: File) {
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
    internal fun openRootViaShizuku() {
        drawerOpen = false
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
    internal fun refreshShizukuList(pane: Pane) {
        ShizukuRoot.listDir(
            pane.currentDir.absolutePath,
            prefs.getInt("sort", 0),
            prefs.getBoolean("sort_reverse", false),
            prefs.getBoolean("sort_type_folders_only", false)
        ) { entries, error ->
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
    internal fun exitShizukuMode(pane: Pane) {
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
                // v1.9.8：文本（含 .html/.htm）进内置编辑器看代码，不走浏览器。
                if (FileUtils.isText(File(entry.name))) {
                    pendingRootEdit = RootEdit(entry.path, staged, staged.lastModified(), staged.length())
                    rootEditorLauncher.launch(Intent(this, TextEditorActivity::class.java).apply {
                        putExtra(TextEditorActivity.EXTRA_PATH, staged.absolutePath)
                    })
                    return@runOnUiThread
                }
                if (FileUtils.isImage(File(entry.name))) {
                    // v1.9.8：根目录里的图片进内置查看器（看暂存副本，
                    // 删的时候按真实路径带回主列表代删）。
                    openImageViewer(pane, path = staged.absolutePath, originalPath = entry.path)
                    return@runOnUiThread
                }
                if (FileUtils.isVideo(File(entry.name))) {
                    // v1.9.8：根目录里的视频进内置播放器（看暂存副本）。
                    openVideoPlayer(pane, path = staged.absolutePath, originalPath = entry.path)
                    return@runOnUiThread
                }
                if (FileUtils.isAudio(File(entry.name))) {
                    // v1.9.8：根目录里的音频进内置音乐播放器（看暂存副本）。
                    openMusicPlayer(pane, path = staged.absolutePath, originalPath = entry.path)
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
    internal fun safBreadcrumb(pane: Pane): String =
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
        p.safStack = mutableStateListOf(SafDir(treeDocId, storage.name))
        p.safBackStack.clear()
        p.safForwardStack.clear()
        p.currentDir = File(safRootKey(treeUri))
        p.searchQuery = ""
        p.selected.clear()
        refreshSafList(p)
    }

    /** 退出 SAF 模式（切回普通目录 / 另一个储存前调）。 */
    internal fun exitSafMode(pane: Pane) {
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
    internal fun refreshSafList(pane: Pane) {
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
        // v1.9.8.1：SAF 也走同一套排序（读偏好；其他调用点只查名字/找
        // 某一项，顺序无所谓，排好序返回没有副作用）。
        val sorted = FileUtils.sortNamed(out,
            prefs.getInt("sort", 0),
            prefs.getBoolean("sort_reverse", false),
            prefs.getBoolean("sort_type_folders_only", false),
            { it.isDir }, { it.name }, { it.size }, { it.modified })
        Log.d(TAG, "listSafChildren: dirDocId=$dirDocId -> ${sorted.size} item(s)")
        return sorted
    }

    /** SAF 模式里点一个文件：用 ACTION_VIEW + content URI 打开（带读授权）。 */
    internal fun openSafFile(entry: SafEntry) {
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
    internal fun showDrawerOverflowMenu() {
        showDrawerOverflowMenu = true
    }

    /** 系统文档选择器（ACTION_OPEN_DOCUMENT_TREE）：点“添加本地储存”直接跳
     *  到安卓原生文件管理器，选好文件夹就添加，名字就是文件夹本身的名字。 */
    private val pickStorageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onStorageTreePicked(uri)
    }

    internal fun openStoragePicker() {
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
    internal fun storageDisplayPath(s: CustomStorages.Storage): String {
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
    internal fun refreshCustomStorages() {
        if (!::prefs.isInitialized) return
        customStorageRows = CustomStorages.load(prefs)
            .filter { !it.hidden }
            .map { CustomStorageRow(it, storageDisplayPath(it)) }
        customStoragesVersion++
    }

    /** 长按自定义储存弹出的菜单（照 MT 那个面板）：重命名 / 删除 / 隐藏 / 排序。 */
    internal fun showCustomStorageMenu(storage: CustomStorages.Storage) {
        storageMenu = StorageMenuRequest(storage)
    }

    internal fun showRenameStorageDialog(storage: CustomStorages.Storage) {
        storageRenameDialog = StorageRenameRequest(storage)
    }

    /** 删除自定义储存：只删记录，不删文件夹本身；通过选择器加的还要释放
     *  持久 URI 授权（v1.5.0：不释放的话系统里一直挂着授权记录）。 */
    internal fun confirmDeleteStorage(storage: CustomStorages.Storage) {
        storageDeleteDialog = StorageDeleteRequest(storage)
    }

    /** 自定义储存删除执行：只删记录，不删文件夹本身；释放持久 URI 授权。 */
    internal fun deleteCustomStorage(storage: CustomStorages.Storage) {
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
        android.widget.Toast.makeText(this, R.string.deleted, android.widget.Toast.LENGTH_SHORT).show()
    }

    /** 排序：弹出列表，每行上下移按钮，顺序即抽屉里的顺序（只排自定义储存）。 */

    // ---------- 菜单 / 搜索 / 权限 ----------






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
    internal fun refreshDrawerTexts() {
        // v1.9.8.1：抽屉是 Compose，文字直接读字符串资源 + PackageManager，
        // 切语言后重建即更新；这里留空函数体是让 onResume 的调用点照常编译。
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
        // v1.9.8：一键重置可能在设置页改了双窗格偏好，回来时同步一下，否则
        // 主界面还停留在旧的单/双窗格（其他偏好由 BaseActivity.onResume 比出重建）。
        try {
            val dp = prefs.getBoolean("dual_pane", true)
            if (dp != dualPane) {
                dualPane = dp
                applyDualPane()
            }
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

    internal fun snackbar(res: Int) {
        showSnackbar(getString(res))
    }
}
