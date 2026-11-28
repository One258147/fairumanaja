package com.fairuman.filemanager

import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

/**
 * 内置视频播放器（v1.9.8）。
 *
 * 入口：主列表点任意视频文件（mp4/mkv/avi/mov/webm/flv/wmv/3gp/m4v/ts/m2ts
 * 等，见 FileUtils.isVideo）进这里；只播点的这一个视频，不翻页。
 * 行 ⋮ 菜单里的“打开方式”不动（见 MainActivity.onFileClick 的集成点）。
 *
 * 播放引擎：Media3 ExoPlayer 1.4.1（Google Maven 拉的 AAR，见
 * tools/resolve-media3.py）。系统 MediaPlayer 播不了 mkv/webm/flv，
 * ExoPlayer 覆盖面更广；avi/wmv 两种引擎都不支持，照规矩弹提示、不崩溃。
 *
 * 打开自动播放；双击视频区暂停/继续播（切时短暂显示状态图标）；
 * 单击切换控制栏显隐；调速 0.5x–2x 六档，圆圈单选、确认才生效
 * （与查看器背景/排序对话框同款流程），倍速不持久化。
 * 切后台/返回键：onPause 里停播并释放播放器，不留后台声音；
 * 回来时（onResume）按离开时的进度重建继续播。
 * 播放失败（解码不了、文件没了）：弹提示后退出页面，不崩溃。
 */
class VideoPlayerActivity : BaseActivity() {

    companion object {
        /** 点开的视频真实路径（普通文件入口）。 */
        const val EXTRA_PATH = "video_path"
        /** SAF 入口：文档的 content URI 字符串。 */
        const val EXTRA_URI = "video_uri"
        /** 根目录入口：被 Shizuku 暂存后的可读副本路径放 EXTRA_PATH，
         *  真实路径放这里（本页不删文件，留着与查看器入口对齐）。 */
        const val EXTRA_ORIGINAL_PATH = "video_original_path"
        /** 上一个/下一个：同文件夹视频列表（路径或 URI 字符串）+ 当前下标。
         *  空列表 = 拿不到同文件夹列表（根目录），按钮置灰。 */
        const val EXTRA_VIDEO_LIST = "video_list"
        const val EXTRA_VIDEO_INDEX = "video_index"
        const val EXTRA_VIDEO_IS_URI = "video_is_uri"

        /** 调速六档（倍速不持久化，每次打开默认 1x）。 */
        val SPEEDS = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

        /** 播放器选项（fairu_prefs，默认全关；照 viewer_bg/viewer_mode 的风格）。 */
        const val PREF_PLAYER_FILL = "player_fill"
        const val PREF_PLAYER_LOOP = "player_loop"
        const val PREF_PLAYER_LANDSCAPE = "player_landscape"
    }

    /** ExoPlayer 实例；onPause 释放后置 null，onResume 按进度重建。 */
    internal var player by mutableStateOf<ExoPlayer?>(null)
        private set

    internal var fileName by mutableStateOf("")
    internal var fileDate by mutableStateOf(0L)
    internal var isPlaying by mutableStateOf(false)
    internal var positionMs by mutableStateOf(0L)
    internal var durationMs by mutableStateOf(0L)
    /** 当前倍速（默认 1x，不持久化）。 */
    internal var speed by mutableStateOf(1f)
    /** 控制栏（顶栏+底栏）是否显示；单击视频区切换。 */
    internal var controlsVisible by mutableStateOf(true)
    internal var showSpeedDialog by mutableStateOf(false)
    /** 双击后短暂显示的状态图标："play" / "pause"，null = 不显示。 */
    internal var flashIcon by mutableStateOf<String?>(null)
    /** 强制铺满全屏 / 循环播放 / 横屏模式（fairu_prefs，默认全关）。 */
    internal var fillScreen by mutableStateOf(false)
    internal var loopPlay by mutableStateOf(false)
    internal var landscapeMode by mutableStateOf(false)
    /** ⋮ 菜单是否打开。 */
    internal var showOptionsMenu by mutableStateOf(false)
    /** 属性对话框文案，null = 不显示（照查看器 propertiesMessage 的做法）。 */
    internal var propertiesMessage by mutableStateOf<String?>(null)

    /** 当前播的视频（切上一个/下一个时会变；分享/属性/播放源都读它，不读 intent）。 */
    internal var currentPath by mutableStateOf<String?>(null)
    internal var currentUri by mutableStateOf<String?>(null)
    /** 同文件夹视频列表 + 当前下标（MainActivity 按主列表当前排序传来；
     *  空列表 = 拿不到，上一个/下一个置灰）。 */
    internal var videoList by mutableStateOf<List<String>>(emptyList())
    internal var videoIndex by mutableStateOf(-1)
    internal var videoIsUri by mutableStateOf(false)

    /** 离开时（onPause）的进度，重建播放器时接回去。 */
    private var savedPositionMs = 0L
    /** 播放失败是否已处理过（onPlayerError 可能连着回调多次）。 */
    private var errorHandled = false

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@VideoPlayerActivity.isPlaying = isPlaying
        }

        override fun onPlaybackStateChanged(state: Int) {
            val p = player ?: return
            // 时长在准备好后才有（之前是 TIME_UNSET）。
            val d = p.duration
            durationMs = if (d > 0) d else 0L
            if (state == Player.STATE_ENDED) {
                isPlaying = false
                positionMs = durationMs
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            // 解码不了 / 文件没了：弹提示后退出，不崩溃。
            if (errorHandled) return
            errorHandled = true
            runOnUiThread {
                Toast.makeText(
                    this@VideoPlayerActivity,
                    R.string.video_cannot_play, Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("fairu_prefs", MODE_PRIVATE)
        fillScreen = prefs.getBoolean(PREF_PLAYER_FILL, false)
        loopPlay = prefs.getBoolean(PREF_PLAYER_LOOP, false)
        landscapeMode = prefs.getBoolean(PREF_PLAYER_LANDSCAPE, false)
        applyOrientation()
        currentPath = intent.getStringExtra(EXTRA_PATH)
        currentUri = intent.getStringExtra(EXTRA_URI)
        videoList = intent.getStringArrayListExtra(EXTRA_VIDEO_LIST).orEmpty()
        videoIndex = intent.getIntExtra(EXTRA_VIDEO_INDEX, -1)
        videoIsUri = intent.getBooleanExtra(EXTRA_VIDEO_IS_URI, false)
        val path = currentPath
        if (path != null) {
            val f = File(path)
            fileName = f.name
            fileDate = if (f.exists()) f.lastModified() else 0L
        } else {
            // SAF 入口：名字从 URI 最后一段取（拿不到就空着，只显示顶栏返回键）。
            fileName = currentUri
                ?.let { Uri.parse(it).lastPathSegment }.orEmpty()
        }
        createPlayer()
        setContent {
            FairuTheme { VideoPlayerScreen() }
        }
    }

    override fun onResume() {
        super.onResume()
        // 切后台时释放了播放器：回来按离开时的进度重建继续播。
        if (player == null && !errorHandled) createPlayer()
    }

    override fun onPause() {
        super.onPause()
        // 切后台 / 返回键：停播并释放播放器，不留后台声音。
        releasePlayer()
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }

    /** 建播放器并自动播放（从 savedPositionMs 继续）。 */
    private fun createPlayer() {
        val uri = videoUri() ?: run {
            Toast.makeText(this, R.string.video_cannot_play, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        errorHandled = false
        val dataSourceFactory = DefaultDataSource.Factory(this)
        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
            )
            .build()
        exo.addListener(playerListener)
        exo.setMediaItem(MediaItem.fromUri(uri))
        exo.setPlaybackSpeed(speed)
        // 循环：开 = 播完自动从头再播；铺满：开 = 裁剪填满，关 = 按比例完整显示。
        exo.repeatMode = if (loopPlay) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        exo.videoScalingMode = if (fillScreen) C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        else C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        exo.prepare()
        if (savedPositionMs > 0) exo.seekTo(savedPositionMs)
        exo.playWhenReady = true
        player = exo
    }

    private fun releasePlayer() {
        val exo = player ?: return
        player = null
        // 记下进度（播完的记 0，下次打开从头播）。
        savedPositionMs = if (exo.playbackState == Player.STATE_ENDED) 0L
        else exo.currentPosition.coerceAtLeast(0L)
        positionMs = savedPositionMs
        isPlaying = false
        exo.removeListener(playerListener)
        exo.release()
    }

    /** 播放源 URI：普通文件/根目录暂存副本用 file://，SAF 用 content://
     *  （读当前视频，不读 intent，切上一个/下一个后才对）。 */
    private fun videoUri(): Uri? {
        val path = currentPath
        if (path != null) {
            val f = File(path)
            if (!f.exists() || f.isDirectory) return null
            return Uri.fromFile(f)
        }
        val uriStr = currentUri ?: return null
        return runCatching { Uri.parse(uriStr) }.getOrNull()
    }

    /** 有没有上一个/下一个（到头/到尾、或拿不到列表时对应按钮置灰）。 */
    internal val hasPrev: Boolean get() = videoList.isNotEmpty() && videoIndex > 0
    internal val hasNext: Boolean get() = videoList.isNotEmpty() && videoIndex in 0 until videoList.size - 1

    /** 切到列表里的第 index 个视频（上一个/下一个）：停掉当前播放器，
     *  换源从头自动播；播完不停在新视频（不自动跳下一个）。 */
    internal fun switchToVideo(index: Int) {
        if (index !in videoList.indices || index == videoIndex) return
        releasePlayer()
        savedPositionMs = 0L
        positionMs = 0L
        durationMs = 0L
        videoIndex = index
        val target = videoList[index]
        if (videoIsUri) {
            currentPath = null
            currentUri = target
            fileName = runCatching { Uri.parse(target).lastPathSegment }.getOrNull().orEmpty()
            fileDate = 0L
        } else {
            currentPath = target
            currentUri = null
            val f = File(target)
            fileName = f.name
            fileDate = if (f.exists()) f.lastModified() else 0L
        }
        createPlayer()
    }

    // ---------- UI 调用的操作 ----------

    internal fun togglePlayPause() {
        val exo = player ?: return
        if (exo.playbackState == Player.STATE_ENDED) {
            // 播完了再点播放：从头播。
            exo.seekTo(0)
            positionMs = 0L
            exo.play()
            flashIcon = "play"
            return
        }
        if (exo.isPlaying) {
            exo.pause()
            positionMs = exo.currentPosition
            flashIcon = "pause"
        } else {
            exo.play()
            flashIcon = "play"
        }
    }

    internal fun seekTo(ms: Long) {
        val exo = player ?: return
        exo.seekTo(ms.coerceIn(0L, durationMs.coerceAtLeast(0L)))
        positionMs = exo.currentPosition
    }

    internal fun applySpeed(rate: Float) {
        speed = rate
        player?.setPlaybackSpeed(rate)
    }

    // ---------- 播放器选项（⋮ 菜单三行开关，点开关立即生效并保存） ----------

    private fun savePlayerPrefs() {
        getSharedPreferences("fairu_prefs", MODE_PRIVATE).edit()
            .putBoolean(PREF_PLAYER_FILL, fillScreen)
            .putBoolean(PREF_PLAYER_LOOP, loopPlay)
            .putBoolean(PREF_PLAYER_LANDSCAPE, landscapeMode)
            .apply()
    }

    /** 横屏模式：开 = 锁定横屏，关 = 恢复跟随系统。 */
    private fun applyOrientation() {
        requestedOrientation = if (landscapeMode) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    internal fun toggleFill() {
        fillScreen = !fillScreen
        savePlayerPrefs()
        player?.videoScalingMode = if (fillScreen) C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        else C.VIDEO_SCALING_MODE_SCALE_TO_FIT
    }

    internal fun toggleLoop() {
        loopPlay = !loopPlay
        savePlayerPrefs()
        val exo = player ?: return
        exo.repeatMode = if (loopPlay) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        // 播完了才开循环：从头播。
        if (loopPlay && exo.playbackState == Player.STATE_ENDED) {
            exo.seekTo(0)
            exo.play()
        }
    }

    internal fun toggleLandscape() {
        landscapeMode = !landscapeMode
        savePlayerPrefs()
        applyOrientation()
    }

    // ---------- 分享 / 属性（照查看器 shareTargets / showPropertiesFor 的做法） ----------

    internal fun shareVideo() {
        Thread {
            val uri = runCatching {
                val path = currentPath
                if (path != null) FairuFileProvider.uriFor(File(path))
                else currentUri?.let { Uri.parse(it) }
            }.getOrNull()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (uri == null) {
                    Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                val mime = currentPath
                    ?.let { runCatching { FileUtils.mimeFor(File(it)) }.getOrNull() }
                    ?: "video/*"
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
                } catch (_: Exception) {
                    Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    /** 属性：名称、路径、大小、日期、时长、分辨率、格式（取不到显示未知）。 */
    internal fun showProperties() {
        Thread {
            var durMs = durationMs
            var width = 0
            var height = 0
            runCatching {
                val mmr = MediaMetadataRetriever()
                val path = currentPath
                if (path != null) mmr.setDataSource(path)
                else currentUri
                    ?.let { mmr.setDataSource(this, Uri.parse(it)) }
                durMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: durMs
                width = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull() ?: 0
                height = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull() ?: 0
                mmr.release()
            }
            val location = currentPath
                ?: currentUri.orEmpty()
            val sizeText = currentPath?.let { File(it) }
                ?.takeIf { it.exists() }?.let { FileUtils.formatSize(it.length()) }
                ?: getString(R.string.unknown)
            val dateText = if (fileDate > 0) FileUtils.formatDate(fileDate)
            else getString(R.string.unknown)
            val durText = if (durMs > 0) formatTime(durMs) else getString(R.string.unknown)
            val dimText = if (width > 0 && height > 0) "${width}x${height}"
            else getString(R.string.unknown)
            val formatText = FileUtils.extOfName(fileName).uppercase()
                .ifBlank { getString(R.string.unknown) }
            val msg = "${getString(R.string.name)}: $fileName\n" +
                "${getString(R.string.path)}: $location\n" +
                "${getString(R.string.size)}: $sizeText\n" +
                "${getString(R.string.modified)}: $dateText\n" +
                "${getString(R.string.video_duration)}: $durText\n" +
                "${getString(R.string.viewer_dimensions)}: $dimText\n" +
                "${getString(R.string.viewer_format)}: $formatText"
            runOnUiThread { propertiesMessage = msg }
        }.start()
    }

    /** mm:ss / h:mm:ss（总时长可能上小时，照这个格式）。 */
    internal fun formatTime(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0L)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s)
        else "%02d:%02d".format(m, s)
    }
}
