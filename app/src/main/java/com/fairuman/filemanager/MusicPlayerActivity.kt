package com.fairuman.filemanager

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

/**
 * 内置音乐播放器（v1.9.8）。
 *
 * 入口：主列表点任意音频文件（mp3/wav/flac/ogg/m4a/aac/opus/amr/mid，
 * 见 FileUtils.isAudio）进这里；九宫格“播放音乐”也能进（音频和视频
 * 都能点——点视频只放视频里的声音，不挂视频面、不显示画面）。
 *
 * 播放引擎：Media3 ExoPlayer 1.4.1（与视频播放器同款）。
 *
 * 打开自动播放；双击中央区暂停/继续播（切时短暂显示状态图标）；
 * 单击切换控制栏显隐；调速 0.5x–2x 六档，圆圈单选、确认才生效
 * （与视频播放器同款流程），倍速不持久化。
 * 切后台/返回键：onPause 里停播并释放播放器，不留后台声音；
 * 回来时（onResume）按离开时的进度重建继续播。
 * 播完停在当前这首，不自动跳下一首（循环开时从头再播）。
 * 播放失败（解码不了、文件没了）：弹提示后退出页面，不崩溃。
 */
class MusicPlayerActivity : BaseActivity() {

    companion object {
        /** 点开的音频真实路径（普通文件入口）。 */
        const val EXTRA_PATH = "music_path"
        /** SAF 入口：文档的 content URI 字符串。 */
        const val EXTRA_URI = "music_uri"
        /** 根目录入口：被 Shizuku 暂存后的可读副本路径放 EXTRA_PATH，
         *  真实路径放这里（本页不删文件，留着与查看器/视频播放器入口对齐）。 */
        const val EXTRA_ORIGINAL_PATH = "music_original_path"
        /** 上一个/下一个：同文件夹音频列表（路径或 URI 字符串）+ 当前下标。
         *  空列表 = 拿不到同文件夹列表（根目录），按钮置灰；
         *  九宫格“播放音乐”点视频文件进来时也不在音频列表里（下标 -1），
         *  两个按钮同样置灰——只放这一段视频的声音。 */
        const val EXTRA_AUDIO_LIST = "audio_list"
        const val EXTRA_AUDIO_INDEX = "audio_index"
        const val EXTRA_AUDIO_IS_URI = "audio_is_uri"
        /** 与 EXTRA_AUDIO_LIST 一一对应的文件名（媒体页 URI 入口用：
         *  content URI 的 lastPathSegment 是 MediaStore 数字 id，
         *  不能当标题；没传就不管，走原来的 lastPathSegment）。 */
        const val EXTRA_AUDIO_NAMES = "audio_names"

        /** 调速六档（照视频播放器，倍速不持久化，每次打开默认 1x）。 */
        val SPEEDS = VideoPlayerActivity.SPEEDS

        /** 循环播放（fairu_prefs，默认关；与视频播放器的 player_loop
         *  分开记，互不干扰）。 */
        const val PREF_MUSIC_LOOP = "music_loop"
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
    /** 控制栏（顶栏+底栏）是否显示；单击中央区切换。 */
    internal var controlsVisible by mutableStateOf(true)
    internal var showSpeedDialog by mutableStateOf(false)
    /** 双击后短暂显示的状态图标："play" / "pause"，null = 不显示。 */
    internal var flashIcon by mutableStateOf<String?>(null)
    /** 循环播放（fairu_prefs，默认关）。 */
    internal var loopPlay by mutableStateOf(false)
    /** ⋮ 菜单是否打开。 */
    internal var showOptionsMenu by mutableStateOf(false)
    /** 属性对话框文案，null = 不显示（照查看器 propertiesMessage 的做法）。 */
    internal var propertiesMessage by mutableStateOf<String?>(null)

    /** 当前播的音频（切上一个/下一个时会变；分享/属性/播放源都读它，不读 intent）。 */
    internal var currentPath by mutableStateOf<String?>(null)
    internal var currentUri by mutableStateOf<String?>(null)
    /** 同文件夹音频列表 + 当前下标（MainActivity 按主列表当前排序传来；
     *  空列表 = 拿不到，上一个/下一个置灰）。 */
    internal var audioList by mutableStateOf<List<String>>(emptyList())
    internal var audioIndex by mutableStateOf(-1)
    internal var audioIsUri by mutableStateOf(false)
    /** 与 audioList 一一对应的文件名（媒体页 URI 入口传来；切歌时
     *  标题从这里取，不用 URI 的 lastPathSegment）。 */
    internal var audioNames by mutableStateOf<List<String>>(emptyList())

    /** 离开时（onPause）的进度，重建播放器时接回去。 */
    private var savedPositionMs = 0L
    /** 播放失败是否已处理过（onPlayerError 可能连着回调多次）。 */
    private var errorHandled = false

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@MusicPlayerActivity.isPlaying = isPlaying
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
                    this@MusicPlayerActivity,
                    R.string.music_cannot_play, Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("fairu_prefs", MODE_PRIVATE)
        loopPlay = prefs.getBoolean(PREF_MUSIC_LOOP, false)
        currentPath = intent.getStringExtra(EXTRA_PATH)
        currentUri = intent.getStringExtra(EXTRA_URI)
        audioList = intent.getStringArrayListExtra(EXTRA_AUDIO_LIST).orEmpty()
        audioIndex = intent.getIntExtra(EXTRA_AUDIO_INDEX, -1)
        audioIsUri = intent.getBooleanExtra(EXTRA_AUDIO_IS_URI, false)
        audioNames = intent.getStringArrayListExtra(EXTRA_AUDIO_NAMES).orEmpty()
        val path = currentPath
        if (path != null) {
            val f = File(path)
            fileName = f.name
            fileDate = if (f.exists()) f.lastModified() else 0L
        } else {
            // SAF / 媒体页 URI 入口：名字优先用传来的文件名列表，
            // 没传才退回 URI 最后一段（MediaStore 数字 id）。
            fileName = audioNames.getOrNull(audioIndex)?.takeIf { it.isNotBlank() }
                ?: currentUri
                    ?.let { Uri.parse(it).lastPathSegment }.orEmpty()
        }
        createPlayer()
        setContent {
            FairuTheme { MusicPlayerScreen() }
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

    /** 建播放器并自动播放（从 savedPositionMs 继续）。
     *  注意：不挂视频面——点视频文件进来时也只出声、不显示画面。 */
    private fun createPlayer() {
        val uri = audioUri() ?: run {
            Toast.makeText(this, R.string.music_cannot_play, Toast.LENGTH_SHORT).show()
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
        // 循环：开 = 播完自动从头再播；关 = 播完停在当前这首。
        exo.repeatMode = if (loopPlay) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
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
     *  （读当前音频，不读 intent，切上一个/下一个后才对）。 */
    private fun audioUri(): Uri? {
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
    internal val hasPrev: Boolean get() = audioList.isNotEmpty() && audioIndex > 0
    internal val hasNext: Boolean get() = audioList.isNotEmpty() && audioIndex in 0 until audioList.size - 1

    /** 切到列表里的第 index 首音频（上一个/下一个）：停掉当前播放器，
     *  换源从头自动播；播完不停在新音频（不自动跳下一首）。 */
    internal fun switchToAudio(index: Int) {
        if (index !in audioList.indices || index == audioIndex) return
        releasePlayer()
        savedPositionMs = 0L
        positionMs = 0L
        durationMs = 0L
        audioIndex = index
        val target = audioList[index]
        if (audioIsUri) {
            currentPath = null
            currentUri = target
            fileName = audioNames.getOrNull(index)?.takeIf { it.isNotBlank() }
                ?: runCatching { Uri.parse(target).lastPathSegment }.getOrNull().orEmpty()
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

    // ---------- 播放器选项（⋮ 菜单：循环播放开关 + 调速） ----------

    private fun saveMusicPrefs() {
        getSharedPreferences("fairu_prefs", MODE_PRIVATE).edit()
            .putBoolean(PREF_MUSIC_LOOP, loopPlay)
            .apply()
    }

    internal fun toggleLoop() {
        loopPlay = !loopPlay
        saveMusicPrefs()
        val exo = player ?: return
        exo.repeatMode = if (loopPlay) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        // 播完了才开循环：从头播。
        if (loopPlay && exo.playbackState == Player.STATE_ENDED) {
            exo.seekTo(0)
            exo.play()
        }
    }

    // ---------- 分享 / 属性（照视频播放器 shareVideo / showProperties 的做法） ----------

    internal fun shareMusic() {
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
                    ?: "audio/*"
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

    /** 属性：名称、路径、大小、日期、时长、格式（取不到显示未知）。 */
    internal fun showProperties() {
        Thread {
            var durMs = durationMs
            runCatching {
                val mmr = MediaMetadataRetriever()
                val path = currentPath
                if (path != null) mmr.setDataSource(path)
                else currentUri
                    ?.let { mmr.setDataSource(this, Uri.parse(it)) }
                durMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: durMs
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
            val formatText = FileUtils.extOfName(fileName).uppercase()
                .ifBlank { getString(R.string.unknown) }
            val msg = "${getString(R.string.name)}: $fileName\n" +
                "${getString(R.string.path)}: $location\n" +
                "${getString(R.string.size)}: $sizeText\n" +
                "${getString(R.string.modified)}: $dateText\n" +
                "${getString(R.string.video_duration)}: $durText\n" +
                "${getString(R.string.viewer_format)}: $formatText"
            runOnUiThread { propertiesMessage = msg }
        }.start()
    }

    /** mm:ss / h:mm:ss（总时长可能上小时，照视频播放器这个格式）。 */
    internal fun formatTime(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0L)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s)
        else "%02d:%02d".format(m, s)
    }
}
