package com.fairuman.filemanager

import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 内置图像查看器（v1.9.8）。
 *
 * 入口：主列表点任意图片文件（jpg/jpeg/png/gif/webp/bmp/heic/heif）进这里；
 * 其他文件不变，行 ⋮ 菜单里的“打开方式”也不动（见 MainActivity.onFileClick
 * 的集成点，写在汇报里，不在本文件里改）。
 *
 * 图片来源：MediaStore 全部图片，按修改时间倒序；从点的那张开始。
 * 点的图片不在 MediaStore 里时，只显示这一张（单图兜底，见 loadImages）。
 * 没装 Coil/Glide（工程没引这个依赖），解码只用 BitmapFactory /
 * ImageDecoder（API 28+），大图采样解码防 OOM；GIF 用 ImageDecoder
 * 播动画（API 26/27 上 GIF 只能显示第一帧静态图，见汇报）。
 *
 * 删除 / 分享 / 打开方式 / 属性：不另写一套——
 * 普通文件走 TrashHelper（进回收站，与主列表同一实现）/
 * FairuFileProvider（与 MainActivity.shareFiles / openWith 同一套 intent）、
 * 属性文案与 MainActivity.showProperties 同格式再加尺寸、格式两行。
 * SAF / 根目录来源的图片删不掉（本页没有 Pane 的 SAF/根目录操作入口），
 * 走 setResult 把删除请求带回 MainActivity，让它现成的 confirmDelete 接手。
 */
class ImageViewerActivity : BaseActivity() {

    companion object {
        /** 点开的那张图的真实路径（普通文件入口）。 */
        const val EXTRA_PATH = "image_path"
        /** SAF 入口：文档的 content URI 字符串（无真实路径时用它）。 */
        const val EXTRA_URI = "image_uri"
        /** 根目录入口：被 Shizuku 暂存后的可读副本路径放 EXTRA_PATH，
         *  真实路径放这里——删除时按真实路径带回主列表代删。 */
        const val EXTRA_ORIGINAL_PATH = "image_original_path"
        /** 返回：选中的图片真实路径（ArrayList<String>，只含当前文件夹能
         *  勾回去的那些；没有真实路径的 MediaStore 项带不回去，见汇报）。 */
        const val EXTRA_SELECTED_PATHS = "selected_paths"
        /** 返回：本页内已删掉的普通文件路径（主列表回来后刷新用）。 */
        const val EXTRA_DELETED_PATHS = "deleted_paths"
        /** 返回：请主列表代删的路径/URI 字符串（SAF 或根目录来源）。 */
        const val EXTRA_REQUEST_DELETE_PATHS = "request_delete_paths"

        // fairu_prefs 里的查看器偏好（与主列表偏好同一个文件）。
        const val PREF_VIEWER_BG = "viewer_bg"
        const val PREF_VIEWER_MODE = "viewer_mode"
        const val BG_BLACK = 0
        const val BG_WHITE = 1
        const val BG_GRAY = 2
        const val MODE_HORIZONTAL = 0
        const val MODE_VERTICAL = 1

        /** 采样解码的目标边长上限（横向翻页）；纵向列表按屏宽再算。 */
        const val MAX_DECODE_DIM = 2048
    }

    /** 一张图（MediaStore 行，或点开的那张文件的单图兜底）。 */
    data class ViewerImage(
        /** 唯一键：真实路径，没有路径时用 content URI 字符串。 */
        val key: String,
        /** 真实文件路径；MediaStore 在部分系统上不给路径时为 null。 */
        val path: String?,
        /** 内容 URI：MediaStore 的，或 SAF 入口传进来的。 */
        val uri: Uri,        val name: String,
        /** 修改时间（毫秒）；未知为 0。 */
        val date: Long,
        /** 字节数；未知为 -1。 */
        val size: Long,
        /** 像素宽 / 高；未知为 0。 */
        val width: Int,
        val height: Int,
        val mime: String,
        /** 0 普通文件 / 1 仅 MediaStore（无路径）/ 2 SAF 或根目录入口。 */
        val source: Int,
        /** 删除委托标识：根目录入口时是真实路径（EXTRA_ORIGINAL_PATH），
         *  其他情况为空（用 key）。 */
        val deleteKey: String? = null,
    ) {
        val isFile: Boolean get() = path != null
        /** 带回主列表代删时用的标识（真实路径或 SAF URI 字符串）。 */
        val deleteTarget: String get() = deleteKey ?: key
    }

    /** 解码结果：静态位图 / 动画 drawable（GIF）/ 解不出来。 */
    sealed interface LoadedImage {
        data class Static(val bitmap: Bitmap) : LoadedImage
        data class Animated(val drawable: Drawable) : LoadedImage
        object Failed : LoadedImage
    }

    // ---------- 状态（UI 直接读，改动即重组） ----------

    internal var images by mutableStateOf<List<ViewerImage>>(emptyList())
    internal var currentIndex by mutableStateOf(0)
    internal var loading by mutableStateOf(true)
    internal var bgMode by mutableStateOf(BG_BLACK)
    internal var viewMode by mutableStateOf(MODE_HORIZONTAL)
    /** 已选图片的 key 集合；空 = 正常态，非空 = 选择模式。 */
    internal var selected by mutableStateOf<Set<String>>(emptySet())
    internal var showBgDialog by mutableStateOf(false)
    internal var showModeDialog by mutableStateOf(false)
    /** 属性对话框正文（非空即显示）；与 MainDialogs.PropertiesDialog 同形。 */
    internal var propertiesMessage by mutableStateOf<String?>(null)
    /** 删除确认框的目标（非空即显示）；与主列表删除对话框同形。 */
    internal var deleteRequest by mutableStateOf<List<ViewerImage>?>(null)

    /** 本页内已删的普通文件路径（返回时带回主列表刷新用）。 */
    private val deletedPaths = mutableListOf<String>()
    /** 请主列表代删的路径/URI（SAF 或根目录来源）。 */
    private val requestDeletePaths = mutableListOf<String>()
    /** 待 MediaStore 系统删除确认框回来的那批图（API 30+）。 */
    private var pendingMediaDelete: List<ViewerImage> = emptyList()

    /** 解码缓存：只留最近几张采样图，纵向滚动时不反复解码。 */
    private val bitmapCache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val deleteMediaLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                pendingMediaDelete.forEach { removeFromList(it) }
            } else {
                Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
            }
            pendingMediaDelete = emptyList()
        }

    // ---------- 生命周期 ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sp = getSharedPreferences("fairu_prefs", MODE_PRIVATE)
        bgMode = sp.getInt(PREF_VIEWER_BG, BG_BLACK)
        viewMode = sp.getInt(PREF_VIEWER_MODE, MODE_HORIZONTAL)
        loadImages()
        setContent {
            FairuTheme { ImageViewerScreen() }
        }
    }

    // ---------- 图片来源：MediaStore + 单图兜底 ----------

    private fun hasImagePermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33)
            android.Manifest.permission.READ_MEDIA_IMAGES
        else android.Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
    }

    private fun loadImages() {
        Thread {
            val startPath = intent.getStringExtra(EXTRA_PATH)
            val startUriStr = intent.getStringExtra(EXTRA_URI)
            val originalPath = intent.getStringExtra(EXTRA_ORIGINAL_PATH)
            val startUri = startUriStr?.let { runCatching { Uri.parse(it) }.getOrNull() }
            var list = if (hasImagePermission()) queryMediaStore() else emptyList()
            var startIndex = 0
            if (startPath != null) {
                startIndex = list.indexOfFirst { it.path == startPath }
                if (startIndex < 0) {
                    // 点的图片不在 MediaStore 里：只显示这一张。
                    // 有 EXTRA_ORIGINAL_PATH 就是根目录入口：看的是暂存副本，
                    // 删的时候按真实路径带回主列表（source=2 走委托分支）。
                    singleImageFromFile(File(startPath), deleteKey = originalPath)?.let {
                        list = listOf(it)
                        startIndex = 0
                    } ?: run {
                        // 文件已经不存在（点开后被删了）：没法看，直接退回。
                        runOnUiThread {
                            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        return@Thread
                    }
                }
            } else if (startUri != null) {
                startIndex = list.indexOfFirst { it.uri == startUri }
                if (startIndex < 0) {
                    singleImageFromUri(startUri)?.let {
                        list = listOf(it)
                        startIndex = 0
                    }
                }
            }
            val finalList = list
            val finalIndex = startIndex.coerceIn(0, (finalList.size - 1).coerceAtLeast(0))
            runOnUiThread {
                images = finalList
                currentIndex = finalIndex
                loading = false
            }
        }.start()
    }

    /** MediaStore 全部图片，按修改时间倒序（总数就是手机全部图片数）。 */
    private fun queryMediaStore(): List<ViewerImage> {
        val out = mutableListOf<ViewerImage>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
        )
        try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dataCol = c.getColumnIndex(MediaStore.Images.Media.DATA)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val wCol = c.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val hCol = c.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                val sizeCol = c.getColumnIndex(MediaStore.Images.Media.SIZE)
                val mimeCol = c.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    )
                    val path = if (dataCol >= 0) c.getString(dataCol)?.takeIf { it.isNotBlank() } else null
                    val name = c.getString(nameCol) ?: uri.lastPathSegment ?: ""
                    var w = if (wCol >= 0) c.getInt(wCol) else 0
                    var h = if (hCol >= 0) c.getInt(hCol) else 0
                    // MediaStore 有时没填宽高（本模拟器上就是 NULL）：现解码文件头补上，
                    // 只读尺寸不解全图，副标题和属性里就不再显示 Unknown。
                    if ((w <= 0 || h <= 0) && path != null) {
                        try {
                            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(path, opts)
                            if (opts.outWidth > 0) w = opts.outWidth
                            if (opts.outHeight > 0) h = opts.outHeight
                        } catch (_: Exception) { }
                    }
                    out.add(
                        ViewerImage(
                            key = path ?: uri.toString(),
                            path = path,
                            uri = uri,
                            name = name,
                            date = if (dateCol >= 0) c.getLong(dateCol) * 1000L else 0L,
                            size = if (sizeCol >= 0) c.getLong(sizeCol) else -1L,
                            width = w,
                            height = h,
                            mime = if (mimeCol >= 0) c.getString(mimeCol).orEmpty() else "",
                            source = if (path != null) 0 else 1,
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // 查不到就当作没有：下面单图兜底还能看点的那张，不闪退。
        }
        return out
    }

    /** 点的图片不在 MediaStore 里：只凭这个文件建一张（尺寸现解码）。
     *  deleteKey 非空 = 根目录入口（看暂存副本，删按真实路径委托）。 */
    private fun singleImageFromFile(f: File, deleteKey: String? = null): ViewerImage? {
        if (!f.exists() || f.isDirectory) return null
        var w = 0
        var h = 0
        try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, opts)
            w = opts.outWidth
            h = opts.outHeight
        } catch (_: Exception) { }
        return ViewerImage(
            key = f.absolutePath,
            path = f.absolutePath,
            uri = runCatching { FairuFileProvider.uriFor(f) }.getOrNull()
                ?: Uri.fromFile(f),
            name = f.name,
            date = f.lastModified(),
            size = f.length(),
            width = w,
            height = h,
            mime = FileUtils.mimeFor(f),
            source = if (deleteKey != null) 2 else 0,
            deleteKey = deleteKey,
        )
    }

    /** SAF 入口：只凭这个 URI 建一张（名字/大小从 OpenableColumns 读）。 */
    private fun singleImageFromUri(uri: Uri): ViewerImage? {
        var name = uri.lastPathSegment ?: ""
        var size = -1L
        try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val nameCol = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeCol = c.getColumnIndex(OpenableColumns.SIZE)
                    if (nameCol >= 0) c.getString(nameCol)?.let { name = it }
                    if (sizeCol >= 0) size = c.getLong(sizeCol)
                }
            }
        } catch (_: Exception) { }
        var w = 0
        var h = 0
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, opts)
                w = opts.outWidth
                h = opts.outHeight
            }
        } catch (_: Exception) { }
        return ViewerImage(
            key = uri.toString(),
            path = null,
            uri = uri,
            name = name,
            date = 0L,
            size = size,
            width = w,
            height = h,
            mime = contentResolver.getType(uri).orEmpty(),
            source = 2,
        )
    }

    // ---------- 解码：大图采样防 OOM；GIF 用 ImageDecoder 播动画 ----------

    /** 在 IO 线程解码一张图（UI 侧用 produceState 调）。maxDim 是目标边长上限。 */
    internal suspend fun decodeImage(item: ViewerImage, maxDim: Int): LoadedImage =
        withContext(Dispatchers.IO) {
            val cacheKey = "${item.key}@$maxDim"
            bitmapCache.get(cacheKey)?.let { return@withContext LoadedImage.Static(it) }
            try {
                val ext = FileUtils.extOfName(item.name)
                if ((ext == "gif" || ext == "heic" || ext == "heif") &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ) {
                    // GIF / HEIC / HEIF：ImageDecoder 解；GIF 出来是动画 drawable。
                    val src = if (item.path != null) ImageDecoder.createSource(File(item.path))
                    else ImageDecoder.createSource(contentResolver, item.uri)
                    val drawable = ImageDecoder.decodeDrawable(src)
                    if (drawable is AnimatedImageDrawable) return@withContext LoadedImage.Animated(drawable)
                    if (drawable is BitmapDrawable) {
                        bitmapCache.put(cacheKey, drawable.bitmap)
                        return@withContext LoadedImage.Static(drawable.bitmap)
                    }
                    return@withContext LoadedImage.Failed
                }
                // 静态图：先读尺寸再采样解码，边长压到 maxDim 以内。
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                decodeBounds(item, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext LoadedImage.Failed
                var sample = 1
                while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bitmap = decodeBitmap(item, opts) ?: return@withContext LoadedImage.Failed
                bitmapCache.put(cacheKey, bitmap)
                LoadedImage.Static(bitmap)
            } catch (_: Exception) {
                // 解不出来的格式弹提示、不崩溃（UI 侧显示“出错了”）。
                LoadedImage.Failed
            }
        }

    private fun decodeBounds(item: ViewerImage, opts: BitmapFactory.Options) {
        if (item.path != null) BitmapFactory.decodeFile(item.path, opts)
        else contentResolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun decodeBitmap(item: ViewerImage, opts: BitmapFactory.Options): Bitmap? {
        if (item.path != null) return BitmapFactory.decodeFile(item.path, opts)
        return contentResolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    // ---------- 选择模式 ----------

    internal fun currentItem(): ViewerImage? = images.getOrNull(currentIndex)

    internal fun isSelected(item: ViewerImage): Boolean = selected.contains(item.key)

    internal fun toggleSelect(item: ViewerImage) {
        val next = selected.toMutableSet()
        if (next.contains(item.key)) next.remove(item.key) else next.add(item.key)
        selected = next
    }

    internal fun toggleSelectCurrent() {
        currentItem()?.let { toggleSelect(it) }
    }

    /** 选择模式下的目标；正常模式就是当前这一张。 */
    internal fun actionTargets(): List<ViewerImage> {
        if (selected.isEmpty()) return currentItem()?.let { listOf(it) } ?: emptyList()
        return images.filter { selected.contains(it.key) }
    }

    /** 选中的真实路径（返回给主列表勾选；没有路径的项带不回去）。 */
    internal fun selectedPaths(): List<String> =
        images.filter { selected.contains(it.key) }.mapNotNull { it.path }

    // ---------- 偏好：背景 / 排序方式（确认才保存，与排序对话框同款流程） ----------

    internal fun saveBgMode(mode: Int) {
        bgMode = mode
        getSharedPreferences("fairu_prefs", MODE_PRIVATE).edit()
            .putInt(PREF_VIEWER_BG, mode).apply()
    }

    internal fun saveViewMode(mode: Int) {
        viewMode = mode
        getSharedPreferences("fairu_prefs", MODE_PRIVATE).edit()
            .putInt(PREF_VIEWER_MODE, mode).apply()
    }

    // ---------- 属性（与 MainActivity.showProperties 同格式 + 尺寸、格式） ----------

    internal fun showPropertiesFor(item: ViewerImage) {
        val sizeText = if (item.size >= 0) FileUtils.formatSize(item.size)
        else getString(R.string.unknown)
        val dateText = if (item.date > 0) FileUtils.formatDate(item.date)
        else getString(R.string.unknown)
        val dimText = if (item.width > 0 && item.height > 0) "${item.width}x${item.height}"
        else getString(R.string.unknown)
        val formatText = FileUtils.extOfName(item.name).uppercase().ifBlank { getString(R.string.unknown) }
        val location = item.path ?: item.uri.toString()
        propertiesMessage = "${getString(R.string.name)}: ${item.name}\n" +
            "${getString(R.string.path)}: $location\n" +
            "${getString(R.string.size)}: $sizeText\n" +
            "${getString(R.string.modified)}: $dateText\n" +
            "${getString(R.string.viewer_dimensions)}: $dimText\n" +
            "${getString(R.string.viewer_format)}: $formatText"
    }

    // ---------- 分享 / 打开方式（与 MainActivity.shareFiles / openWith 同一套 intent） ----------

    internal fun shareTargets(targets: List<ViewerImage>) {
        if (targets.isEmpty()) return
        Thread {
            val uris = targets.mapNotNull { item ->
                if (item.path != null) {
                    runCatching { FairuFileProvider.uriFor(File(item.path)) }.getOrNull()
                } else item.uri
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (uris.isEmpty()) {
                    Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                val mime = if (uris.size == 1) {
                    targets.firstOrNull()?.mime?.takeIf { it.isNotBlank() } ?: "*/*"
                } else "*/*"
                val intent = Intent(
                    if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
                ).apply {
                    type = mime
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

    internal fun openWithTarget(item: ViewerImage) {
        try {
            val uri = if (item.path != null) FairuFileProvider.uriFor(File(item.path)) else item.uri
            val mime = item.mime.ifBlank { "*/*" }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.open_with)))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- 删除（普通文件本页确认+进回收站；SAF/根目录来源带回主列表代删） ----------

    /** 删除按钮入口：按来源分流（见汇报）。 */
    internal fun requestDelete(targets: List<ViewerImage>) {
        if (targets.isEmpty()) return
        val safTargets = targets.filter { it.source == 2 }
        val normalTargets = targets.filter { it.source != 2 }
        if (safTargets.isNotEmpty()) {
            // SAF / 根目录来源：本页没有 Pane 的 SAF/根目录删除入口，
            // 把标识带回主列表，让它现成的 confirmDelete 接手。
            safTargets.forEach { requestDeletePaths.add(it.deleteTarget) }
            if (normalTargets.isEmpty()) {
                finishWithResult()
                return
            }
        }
        // 普通文件（含仅 MediaStore 有 content URI 的）：本页弹确认框，
        // 与主列表删除对话框同款（默认进回收站，可取消勾选永久删除）。
        deleteRequest = normalTargets
    }

    /** 删除确认框点“确定”后执行（toTrash=true 进回收站，false 永久删除）。 */
    internal fun doDelete(targets: List<ViewerImage>, toTrash: Boolean) {
        val mediaOnly = targets.filter { it.path == null }
        val fileTargets = targets.filter { it.path != null }
        // 仅 MediaStore（无路径）的：API 30+ 走系统删除确认框，
        // 30 以下直接 ContentResolver.delete（不需要回收站流程）。
        if (mediaOnly.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pendingMediaDelete = mediaOnly
                try {
                    val pending = MediaStore.createDeleteRequest(
                        contentResolver, mediaOnly.map { it.uri }
                    )
                    deleteMediaLauncher.launch(
                        IntentSenderRequest.Builder(pending.intentSender).build()
                    )
                } catch (_: Exception) {
                    Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
                    pendingMediaDelete = emptyList()
                }
            } else {
                Thread {
                    var ok = true
                    mediaOnly.forEach {
                        try {
                            if (contentResolver.delete(it.uri, null, null) <= 0) ok = false
                        } catch (_: Exception) { ok = false }
                    }
                    val success = ok
                    runOnUiThread {
                        if (success) mediaOnly.forEach { removeFromList(it) }
                        else Toast.makeText(this, R.string.delete_failed, Toast.LENGTH_SHORT).show()
                    }
                }.start()
            }
        }
        if (fileTargets.isEmpty()) return
        // 普通文件：与 MainActivity.doDelete 普通分支同一实现。
        Thread {
            var failed = 0
            fileTargets.forEach { item ->
                val f = File(item.path!!)
                val ok = if (toTrash) TrashHelper.moveToTrash(f)
                else FileUtils.deleteRecursive(f)
                if (ok) deletedPaths.add(item.path!!) else failed++
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                fileTargets.forEach { removeFromList(it) }
                if (failed == 0) {
                    Toast.makeText(
                        this,
                        if (toTrash) R.string.moved_to_trash else R.string.deleted,
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(this, R.string.delete_failed, Toast.LENGTH_SHORT).show()
                }
                if (images.isEmpty()) finishWithResult()
            }
        }.start()
    }

    /** 从列表里去掉一张（删完后下标自动跟进；选中的也去掉）。 */
    private fun removeFromList(item: ViewerImage) {
        val idx = images.indexOfFirst { it.key == item.key }
        if (idx < 0) return
        images = images.filterNot { it.key == item.key }
        selected = selected - item.key
        if (currentIndex > idx) currentIndex -= 1
        if (currentIndex >= images.size) currentIndex = (images.size - 1).coerceAtLeast(0)
    }

    // ---------- 退出：把选择带回主列表 ----------

    internal fun finishWithResult() {
        val data = Intent().apply {
            putStringArrayListExtra(EXTRA_SELECTED_PATHS, ArrayList(selectedPaths()))
            putStringArrayListExtra(EXTRA_DELETED_PATHS, ArrayList(deletedPaths))
            putStringArrayListExtra(EXTRA_REQUEST_DELETE_PATHS, ArrayList(requestDeletePaths))
        }
        setResult(RESULT_OK, data)
        finish()
    }
}
