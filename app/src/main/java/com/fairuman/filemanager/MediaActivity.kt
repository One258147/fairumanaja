package com.fairuman.filemanager

import android.content.ContentUris
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 媒体页（v1.9.8）：抽屉工具区“图像与视频”共用一个页面，入口用 intent
 * extra 传默认分类。列出手机里所有的图片、视频和音频，来源走 MediaStore
 * （与图像查看器同一套查询；视频查 MediaStore.Video，音频查
 * MediaStore.Audio）。
 * 四个分类：图片（不含 gif）、视频、音频、动图（gif）。
 * 默认按日期倒序、一个日期一组（组头：今天 / 昨天 / YYYY-MM-DD）；
 * 切到按名称/按大小/按类型时平铺不分组（只要排序方式是按日期就分组，
 * 逆向只决定组内和组间顺序）。
 * 点图片进内置图像查看器，点视频进内置视频播放器（都传 content URI；
 * 播放器拿不到同文件夹列表，传空、上下切换置灰），点音频进内置音乐
 * 播放器（传当前分类下按当前排序的全部音频 URI + 下标 + 文件名，
 * 上一个/下一个能切，标题显示文件名）。
 * 没有搜索功能。
 */
class MediaActivity : BaseActivity() {

    companion object {
        const val EXTRA_CATEGORY = "media_category"
        const val CATEGORY_IMAGES = 0
        const val CATEGORY_VIDEOS = 1
        const val CATEGORY_AUDIOS = 2
        const val CATEGORY_GIFS = 3
    }

    data class MediaItem(
        val key: String,
        val uri: Uri,
        val path: String?,
        val name: String,
        val date: Long,
        val size: Long,
        val mime: String,
        val isVideo: Boolean,
        val isGif: Boolean,
        val isAudio: Boolean = false,
    )

    internal var entryCategory by mutableStateOf(CATEGORY_IMAGES)
    internal var category by mutableStateOf(CATEGORY_IMAGES)
    internal var allItems by mutableStateOf<List<MediaItem>>(emptyList())
    internal var loading by mutableStateOf(true)
    internal var sort by mutableStateOf(1)
    internal var reverse by mutableStateOf(true)
    internal var selected by mutableStateOf<Set<String>>(emptySet())
    internal var showMenu by mutableStateOf(false)
    internal var showSortDialog by mutableStateOf(false)
    internal var showDeleteDialog by mutableStateOf(false)

    /** 缩略图缓存（LruCache 不接受 null value，失败的不缓存，只记下来不再反复读）。 */
    private val thumbCache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val thumbFailed = mutableSetOf<String>()

    internal fun loadThumbnail(item: MediaItem): Bitmap? {
        // 音频没有缩略图，直接走音符图标，不查 MediaStore。
        if (item.isAudio) return null
        thumbCache.get(item.key)?.let { return it }
        if (item.key in thumbFailed) return null
        val bmp = try {
            if (Build.VERSION.SDK_INT >= 29) {
                contentResolver.loadThumbnail(item.uri, Size(144, 144), null)
            } else null
        } catch (_: Exception) {
            null
        }
        if (bmp != null) thumbCache.put(item.key, bmp) else thumbFailed.add(item.key)
        return bmp
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sp = getSharedPreferences("fairu_prefs", MODE_PRIVATE)
        sort = sp.getInt("media_sort", 1)
        reverse = sp.getBoolean("media_sort_reverse", true)
        entryCategory = intent.getIntExtra(EXTRA_CATEGORY, CATEGORY_IMAGES)
            .coerceIn(CATEGORY_IMAGES, CATEGORY_GIFS)
        category = entryCategory
        load()
        setContent {
            FairuTheme { MediaScreen() }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从查看器/播放器回来可能删过图：重查一遍，列表不留 stale 行。
        if (!loading) load()
    }

    internal fun load() {
        loading = true
        Thread {
            val list = try {
                queryAll()
            } catch (_: Exception) {
                emptyList()
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                allItems = list
                loading = false
            }
        }.start()
    }

    /** 图片 + 视频两路 MediaStore 查询（与查看器 queryMediaStore 同款）。 */
    private fun queryAll(): List<MediaItem> {
        val out = mutableListOf<MediaItem>()
        // 图片（含 gif，后面按分类再分）。
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.MIME_TYPE,
            )
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dataCol = c.getColumnIndex(MediaStore.Images.Media.DATA)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val sizeCol = c.getColumnIndex(MediaStore.Images.Media.SIZE)
                val mimeCol = c.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    )
                    val name = c.getString(nameCol) ?: uri.lastPathSegment ?: ""
                    val mime = if (mimeCol >= 0) c.getString(mimeCol).orEmpty() else ""
                    val isGif = mime.equals("image/gif", ignoreCase = true) ||
                        name.endsWith(".gif", ignoreCase = true)
                    out.add(
                        MediaItem(
                            key = uri.toString(),
                            uri = uri,
                            path = if (dataCol >= 0) c.getString(dataCol)?.takeIf { it.isNotBlank() } else null,
                            name = name,
                            date = if (dateCol >= 0) c.getLong(dateCol) * 1000L else 0L,
                            size = if (sizeCol >= 0) c.getLong(sizeCol) else -1L,
                            mime = mime,
                            isVideo = false,
                            isGif = isGif,
                        )
                    )
                }
            }
        } catch (_: Exception) { }
        // 视频。
        try {
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.MIME_TYPE,
            )
            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection, null, null,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dataCol = c.getColumnIndex(MediaStore.Video.Media.DATA)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val sizeCol = c.getColumnIndex(MediaStore.Video.Media.SIZE)
                val mimeCol = c.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                    )
                    val name = c.getString(nameCol) ?: uri.lastPathSegment ?: ""
                    val mime = if (mimeCol >= 0) c.getString(mimeCol).orEmpty() else ""
                    out.add(
                        MediaItem(
                            key = uri.toString(),
                            uri = uri,
                            path = if (dataCol >= 0) c.getString(dataCol)?.takeIf { it.isNotBlank() } else null,
                            name = name,
                            date = if (dateCol >= 0) c.getLong(dateCol) * 1000L else 0L,
                            size = if (sizeCol >= 0) c.getLong(sizeCol) else -1L,
                            mime = mime,
                            isVideo = true,
                            isGif = false,
                        )
                    )
                }
            }
        } catch (_: Exception) { }
        // 音频。
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.MIME_TYPE,
            )
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, null, null,
                "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val dataCol = c.getColumnIndex(MediaStore.Audio.Media.DATA)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val sizeCol = c.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val mimeCol = c.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    )
                    val name = c.getString(nameCol) ?: uri.lastPathSegment ?: ""
                    val mime = if (mimeCol >= 0) c.getString(mimeCol).orEmpty() else ""
                    out.add(
                        MediaItem(
                            key = uri.toString(),
                            uri = uri,
                            path = if (dataCol >= 0) c.getString(dataCol)?.takeIf { it.isNotBlank() } else null,
                            name = name,
                            date = if (dateCol >= 0) c.getLong(dateCol) * 1000L else 0L,
                            size = if (sizeCol >= 0) c.getLong(sizeCol) else -1L,
                            mime = mime,
                            isVideo = false,
                            isGif = false,
                            isAudio = true,
                        )
                    )
                }
            }
        } catch (_: Exception) { }
        return out
    }

    /** 当前分类下的条目（已按当前排序排好）。 */
    internal fun currentItems(): List<MediaItem> {
        val filtered = when (category) {
            CATEGORY_IMAGES -> allItems.filter { !it.isVideo && !it.isGif && !it.isAudio }
            CATEGORY_VIDEOS -> allItems.filter { it.isVideo }
            CATEGORY_AUDIOS -> allItems.filter { it.isAudio }
            else -> allItems.filter { !it.isVideo && !it.isAudio && it.isGif }
        }
        val sorted = when (sort) {
            0 -> filtered.sortedBy { it.name.lowercase(Locale.US) }
            2 -> filtered.sortedBy { it.size }
            3 -> filtered.sortedBy { FileUtils.extOfName(it.name).lowercase(Locale.US) }
            else -> filtered.sortedBy { it.date }
        }
        return if (reverse) sorted.reversed() else sorted
    }

    /** 按日期分组（只有排序方式是按日期时才分组）：组头 今天 / 昨天 /
     *  YYYY-MM-DD；组间顺序跟排好的列表一致。 */
    internal fun groupedItems(): List<Pair<String, List<MediaItem>>> {
        val items = currentItems()
        if (sort != 1) return listOf("" to items)
        val groups = linkedMapOf<String, MutableList<MediaItem>>()
        for (item in items) {
            val label = dayLabel(item.date)
            groups.getOrPut(label) { mutableListOf() }.add(item)
        }
        return groups.map { it.key to it.value.toList() }
    }

    internal fun dayLabel(time: Long): String {
        if (time <= 0) return getString(R.string.unknown)
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val day = dayFmt.format(Date(time))
        val today = dayFmt.format(Date())
        val yesterday = dayFmt.format(Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000L))
        return when (day) {
            today -> getString(R.string.media_today)
            yesterday -> getString(R.string.media_yesterday)
            else -> day
        }
    }

    internal fun openItem(item: MediaItem) {
        if (item.isAudio) {
            // 点音频进音乐播放器：传当前分类下按当前排序的全部音频
            // URI + 下标 + 文件名（文件名单独传，播放器标题不显示
            // MediaStore 数字 id——视频那条路的毛病不带过来）。
            val items = currentItems()
            val index = items.indexOfFirst { it.key == item.key }
            startActivity(Intent(this, MusicPlayerActivity::class.java).apply {
                putExtra(MusicPlayerActivity.EXTRA_URI, item.uri.toString())
                putStringArrayListExtra(
                    MusicPlayerActivity.EXTRA_AUDIO_LIST,
                    ArrayList(items.map { it.uri.toString() })
                )
                putExtra(MusicPlayerActivity.EXTRA_AUDIO_INDEX, index)
                putExtra(MusicPlayerActivity.EXTRA_AUDIO_IS_URI, true)
                putStringArrayListExtra(
                    MusicPlayerActivity.EXTRA_AUDIO_NAMES,
                    ArrayList(items.map { it.name })
                )
            })
            return
        }
        if (item.isVideo) {
            startActivity(Intent(this, VideoPlayerActivity::class.java).apply {
                putExtra(VideoPlayerActivity.EXTRA_URI, item.uri.toString())
                putStringArrayListExtra(VideoPlayerActivity.EXTRA_VIDEO_LIST, arrayListOf())
                putExtra(VideoPlayerActivity.EXTRA_VIDEO_INDEX, -1)
                putExtra(VideoPlayerActivity.EXTRA_VIDEO_IS_URI, true)
            })
        } else {
            startActivity(Intent(this, ImageViewerActivity::class.java).apply {
                putExtra(ImageViewerActivity.EXTRA_URI, item.uri.toString())
            })
        }
    }

    internal fun selectAllCurrent() {
        selected = currentItems().map { it.key }.toSet()
    }

    internal fun selectedItems(): List<MediaItem> {
        val keys = selected
        return allItems.filter { it.key in keys }
    }

    internal fun selectedTotalSize(): Long =
        selectedItems().sumOf { if (it.size > 0) it.size else 0L }

    internal fun shareSelected() {
        val targets = selectedItems()
        if (targets.isEmpty()) return
        val intent = Intent(
            if (targets.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
        ).apply {
            type = if (targets.size == 1) {
                targets.first().mime.takeIf { it.isNotBlank() } ?: "*/*"
            } else "*/*"
            if (targets.size == 1) putExtra(Intent.EXTRA_STREAM, targets.first().uri)
            else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(targets.map { it.uri }))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.share)))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
        }
    }

    /** 删除选中项：确认框里勾“移动到回收站”时，有真实路径的走
     *  TrashHelper.moveToTrash（同时尽力删 MediaStore 行，免得列表留
     *  stale 行），没路径的直接删 MediaStore 行；不勾时永久删除。 */
    internal fun deleteSelected(toTrash: Boolean) {
        val targets = selectedItems()
        if (targets.isEmpty()) return
        Thread {
            var failed = 0
            for (item in targets) {
                try {
                    if (toTrash) {
                        if (item.path != null) {
                            TrashHelper.moveToTrash(File(item.path))
                            runCatching { contentResolver.delete(item.uri, null, null) }
                        } else {
                            if (contentResolver.delete(item.uri, null, null) <= 0) failed++
                        }
                    } else {
                        if (item.path != null) {
                            runCatching { File(item.path).delete() }
                            runCatching { contentResolver.delete(item.uri, null, null) }
                        } else {
                            if (contentResolver.delete(item.uri, null, null) <= 0) failed++
                        }
                    }
                } catch (_: Exception) {
                    failed++
                }
            }
            val failedCount = failed
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (failedCount > 0) {
                    Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
                }
                selected = emptySet()
                load()
            }
        }.start()
    }
}
