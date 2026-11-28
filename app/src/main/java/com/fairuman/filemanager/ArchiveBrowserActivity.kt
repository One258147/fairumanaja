package com.fairuman.filemanager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 浏览压缩包（v1.9.8，不解压预览）。
 *
 * 入口与查看器/播放器同款三处：普通文件传路径，SAF 传 URI，根目录传暂存
 * 副本路径 + 真实路径（EXTRA_ORIGINAL_PATH）。
 * 列出条目：文件名、未压缩大小；以 / 结尾的条目是子文件夹，点进去列该
 * 前缀下的内容，返回键回上一级，到根再退出。
 * 点包里的文件条目时只有两个功能：查看属性、分享；文本项再多一个
 * 编辑文本（改完保存写回压缩包，见 writeBackEditedEntry）。其他功能
 * 一律没有：不解压单个文件、不删除、不复制、不改名。
 * 损坏/打不开的压缩包：toast 提示后退出，不崩溃。
 */
class ArchiveBrowserActivity : BaseActivity() {

    companion object {
        const val EXTRA_PATH = "archive_path"
        const val EXTRA_URI = "archive_uri"
        const val EXTRA_ORIGINAL_PATH = "archive_original_path"
    }

    /** 一条 zip 条目（ZipFile 直接读；URI 入口读流时数出来的大小）。 */
    data class ArchiveEntry(
        val name: String,
        val size: Long,
        val compressedSize: Long,
        val time: Long,
        val isDir: Boolean,
    )

    internal var titleName by mutableStateOf("")
    internal var entries by mutableStateOf<List<ArchiveEntry>>(emptyList())
    internal var currentPrefix by mutableStateOf("")
    internal var loading by mutableStateOf(true)
    /** 属性对话框正文（非空即显示）。 */
    internal var propertiesMessage by mutableStateOf<String?>(null)
    /** 点开的那一条（非空即显示操作对话框：属性 / 分享 / 编辑文本）。 */
    internal var actionEntry by mutableStateOf<ArchiveEntry?>(null)

    /** 待写回的编辑：条目 + 解出来的临时文件 + 编辑前的字节。 */
    private var pendingEdit: PendingEdit? = null
    private data class PendingEdit(val entry: ArchiveEntry, val tempFile: File, val before: ByteArray)

    private val editorLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val pending = pendingEdit ?: return@registerForActivityResult
            pendingEdit = null
            // 编辑器返回后才执行写回：临时文件没变就不写；写回失败弹提示，
            // 不崩溃、不丢原包（新包建好之前原文件一个字节都不动）。
            Thread {
                try {
                    val after = runCatching { pending.tempFile.readBytes() }.getOrNull()
                    if (after == null || after.contentEquals(pending.before)) return@Thread
                    val ok = writeBackEditedEntry(pending.entry, pending.tempFile)
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        if (ok) {
                            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
                            loadEntries()
                        } else {
                            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (_: Exception) {
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }.start()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        titleName = archiveDisplayName()
        loadEntries()
        setContent {
            FairuTheme { ArchiveBrowserScreen() }
        }
    }

    /** 标题用压缩包的文件名（SAF 查显示名，根目录用真实路径的文件名）。 */
    private fun archiveDisplayName(): String {
        intent.getStringExtra(EXTRA_ORIGINAL_PATH)?.let { return File(it).name }
        intent.getStringExtra(EXTRA_PATH)?.let { return File(it).name }
        intent.getStringExtra(EXTRA_URI)?.let { uriStr ->
            runCatching {
                val uri = Uri.parse(uriStr)
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) c.getString(idx)?.takeIf { it.isNotBlank() }?.let { return it }
                    }
                }
                uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return "archive.zip"
    }

    /** 当前前缀下的直接子项（文件 + 子文件夹各一行）。注意很多 zip 里
     *  没有显式的目录条目（只有 sub/file.txt，没有 sub/），子文件夹要
     *  从文件路径里合成出来，否则进不去。 */
    internal fun currentChildren(): List<ArchiveEntry> {
        val prefix = currentPrefix
        val out = mutableListOf<ArchiveEntry>()
        val seenDirs = mutableSetOf<String>()
        for (e in entries) {
            if (!e.name.startsWith(prefix)) continue
            val rest = e.name.substring(prefix.length)
            if (rest.isEmpty()) continue
            if (e.isDir) {
                // 目录条目：只有刚好一层时才列（sub/ 在根列，sub/deep/ 不列）。
                if (rest.count { it == '/' } == 1 && seenDirs.add(e.name)) out.add(e)
            } else if (!rest.contains('/')) {
                out.add(e)
            } else {
                // 文件在更深的子文件夹里：把第一层文件夹合成一行。
                val dirName = prefix + rest.substringBefore('/') + "/"
                if (seenDirs.add(dirName)) {
                    out.add(ArchiveEntry(dirName, -1, -1, 0, true))
                }
            }
        }
        return out
    }

    internal fun navigateUp(): Boolean {
        if (currentPrefix.isEmpty()) return false
        val trimmed = currentPrefix.trimEnd('/')
        val parent = trimmed.substringBeforeLast('/', "")
        currentPrefix = if (parent.isEmpty()) "" else "$parent/"
        return true
    }

    private fun loadEntries() {
        loading = true
        Thread {
            val path = intent.getStringExtra(EXTRA_PATH)
            val uriStr = intent.getStringExtra(EXTRA_URI)
            val list: List<ArchiveEntry>? = try {
                when {
                    path != null -> readZipFile(File(path))
                    uriStr != null -> readZipStream(Uri.parse(uriStr))
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                loading = false
                if (list == null) {
                    // 损坏/打不开：toast 后退出，不崩溃。
                    Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
                    finish()
                    return@runOnUiThread
                }
                entries = list
            }
        }.start()
    }

    /** 路径入口：ZipFile 直接读中央目录（大小/压缩后大小/日期都有）。 */
    private fun readZipFile(file: File): List<ArchiveEntry>? {
        ZipFile(file).use { zip ->
            return zip.entries().toList().map { e ->
                ArchiveEntry(e.name, e.size, e.compressedSize, e.time, e.isDirectory)
            }
        }
    }

    /** URI 入口：ZipInputStream 读流；流里拿不到未压缩大小，边读边数。 */
    private fun readZipStream(uri: Uri): List<ArchiveEntry>? {
        val out = mutableListOf<ArchiveEntry>()
        (contentResolver.openInputStream(uri) ?: return null).use { input ->
            ZipInputStream(input).use { zin ->
                var entry = zin.nextEntry
                while (entry != null) {
                    var size = 0L
                    val buf = ByteArray(8192)
                    var n = zin.read(buf)
                    while (n >= 0) {
                        size += n
                        n = zin.read(buf)
                    }
                    out.add(ArchiveEntry(entry.name, size, entry.compressedSize, entry.time, entry.isDirectory))
                    zin.closeEntry()
                    entry = zin.nextEntry
                }
            }
        }
        return out
    }

    // ---------- 属性（名称 / 大小 / 日期 / 压缩后大小） ----------

    internal fun showPropertiesFor(entry: ArchiveEntry) {
        val sizeText = if (entry.size >= 0) FileUtils.formatSize(entry.size)
        else getString(R.string.unknown)
        val dateText = if (entry.time > 0) FileUtils.formatDate(entry.time)
        else getString(R.string.unknown)
        val compressedText = if (entry.compressedSize >= 0) FileUtils.formatSize(entry.compressedSize)
        else getString(R.string.unknown)
        propertiesMessage = "${getString(R.string.name)}: ${entry.name.trimEnd('/')}\n" +
            "${getString(R.string.size)}: $sizeText\n" +
            "${getString(R.string.modified)}: $dateText\n" +
            "${getString(R.string.compressed)}: $compressedText"
    }

    // ---------- 分享：把那一项解到 cacheDir 临时文件再走现有分享流程 ----------

    internal fun shareEntry(entry: ArchiveEntry) {
        if (entry.isDir) return
        Thread {
            val tmp = extractEntry(entry)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (tmp == null) {
                    Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                try {
                    val uri = FairuFileProvider.uriFor(tmp)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = FileUtils.mimeFor(tmp)
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, getString(R.string.share)))
                } catch (_: Exception) {
                    Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    // ---------- 编辑文本：解到临时文件 → 文本编辑器 → 返回后写回包里 ----------

    internal fun editEntry(entry: ArchiveEntry) {
        if (entry.isDir || !FileUtils.isText(File(entry.name))) return
        Thread {
            val tmp = extractEntry(entry, subdir = "archive_edit")
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (tmp == null) {
                    Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                pendingEdit = PendingEdit(entry, tmp, runCatching { tmp.readBytes() }.getOrDefault(ByteArray(0)))
                editorLauncher.launch(Intent(this, TextEditorActivity::class.java).apply {
                    putExtra(TextEditorActivity.EXTRA_PATH, tmp.absolutePath)
                })
            }
        }.start()
    }

    /** 把一条目解到 cacheDir 临时文件；临时文件名只取条目名的最后一段，
     *  防止路径穿越（子文件夹里的同名文件也不会互相覆盖：同一子目录下
     *  用一次性的 archive_share / archive_edit 目录分开）。 */
    private fun extractEntry(entry: ArchiveEntry, subdir: String = "archive_share"): File? {
        val baseName = entry.name.substringAfterLast('/').ifBlank { return null }
        if (baseName == "." || baseName == "..") return null
        return try {
            val dir = File(cacheDir, subdir).apply { mkdirs() }
            val tmp = File(dir, baseName)
            val path = intent.getStringExtra(EXTRA_PATH)
            val uriStr = intent.getStringExtra(EXTRA_URI)
            when {
                path != null -> {
                    ZipFile(File(path)).use { zip ->
                        val ze = zip.getEntry(entry.name) ?: return null
                        zip.getInputStream(ze).use { input ->
                            FileOutputStream(tmp).use { out -> input.copyTo(out) }
                        }
                    }
                }
                uriStr != null -> {
                    var found = false
                    (contentResolver.openInputStream(Uri.parse(uriStr)) ?: return null).use { input ->
                        ZipInputStream(input).use { zin ->
                            var ze = zin.nextEntry
                            while (ze != null) {
                                if (ze.name == entry.name) {
                                    found = true
                                    FileOutputStream(tmp).use { out -> zin.copyTo(out) }
                                    break
                                }
                                zin.closeEntry()
                                ze = zin.nextEntry
                            }
                        }
                    }
                    if (!found) return null
                }
                else -> return null
            }
            tmp
        } catch (_: Exception) {
            null
        }
    }

    /** 写回：重写整个包、替换该条目（新包建好之前原文件一个字节都不动；
     *  建好后再替换原文件 / 写回 SAF 输出流 / 根目录走 Shizuku cp）。 */
    private fun writeBackEditedEntry(entry: ArchiveEntry, editedFile: File): Boolean {
        val path = intent.getStringExtra(EXTRA_PATH)
        val uriStr = intent.getStringExtra(EXTRA_URI)
        val originalPath = intent.getStringExtra(EXTRA_ORIGINAL_PATH)
        return try {
            val newZip = File(cacheDir, "archive_rewrite_${System.currentTimeMillis()}.zip")
            ZipOutputStream(FileOutputStream(newZip)).use { zout ->
                when {
                    path != null -> {
                        ZipFile(File(path)).use { zip ->
                            for (ze in zip.entries()) {
                                // 条目流用完就关（ZipFile 的条目流可以单独关）。
                                copyEntryReplacing(zout, ze, entry, editedFile, zip.getInputStream(ze), closeInput = true)
                            }
                        }
                    }
                    uriStr != null -> {
                        (contentResolver.openInputStream(Uri.parse(uriStr)) ?: return false).use { input ->
                            ZipInputStream(input).use { zin ->
                                var ze = zin.nextEntry
                                while (ze != null) {
                                    // 注意：zin 本身不能关（关了整个流就断了），
                                    // copyEntryReplacing 里只读不关。
                                    copyEntryReplacing(zout, ze, entry, editedFile, zin, closeInput = false)
                                    zin.closeEntry()
                                    ze = zin.nextEntry
                                }
                            }
                        }
                    }
                    else -> return false
                }
            }
            val ok = when {
                originalPath != null -> {
                    // 根目录：先把新包写回暂存副本（App 自己能写），再走
                    // Shizuku cp 写回真实路径；cp 失败原包还在。
                    File(path ?: return false).writeBytes(newZip.readBytes())
                    ShizukuRoot.exec("cp", path, originalPath).exit == 0
                }                uriStr != null -> {
                    contentResolver.openOutputStream(Uri.parse(uriStr), "w")?.use { out ->
                        FileInputStream(newZip).use { it.copyTo(out) }
                    } != null
                }
                path != null -> {
                    newZip.copyTo(File(path), overwrite = true)
                    true
                }
                else -> false
            }
            runCatching { newZip.delete() }
            ok
        } catch (_: Exception) {
            false
        }
    }

    /** 往新包里写一条：是被编辑的那条就写临时文件的新内容，其他原样流复制。
     *  统一写 DEFLATED（原 STORED 条目改方法不改内容）。 */
    private fun copyEntryReplacing(
        zout: ZipOutputStream,
        ze: ZipEntry,
        edited: ArchiveEntry,
        editedFile: File,
        input: java.io.InputStream,
        closeInput: Boolean,
    ) {
        val outEntry = ZipEntry(ze.name)
        if (ze.time > 0) outEntry.time = ze.time
        zout.putNextEntry(outEntry)
        if (ze.name == edited.name && !ze.isDirectory) {
            FileInputStream(editedFile).use { it.copyTo(zout) }
            if (closeInput) runCatching { input.close() }
        } else if (!ze.isDirectory) {
            if (closeInput) input.use { it.copyTo(zout) } else input.copyTo(zout)
        }
        zout.closeEntry()
    }
}
