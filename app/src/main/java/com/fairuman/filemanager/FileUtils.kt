package com.fairuman.filemanager

import android.os.StatFs
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object FileUtils {
    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = bytes.toDouble()
        var i = -1
        while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
        return String.format(Locale.US, "%.1f %s", v, units[i])
    }

    /** v1.9.4：MP-Manager 样式的大小格式——0 显示“0B”，其余两位小数、
     *  数字与单位之间不加空格（如 37.00B、118.67KB、1.61MB、428.50MB）。 */
    fun formatSizeMp(bytes: Long): String {
        if (bytes < 1024) return "${bytes}B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = bytes.toDouble()
        var i = -1
        while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
        return String.format(Locale.US, "%.2f%s", v, units[i])
    }

    fun formatDate(time: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(time))

    /** 列表副标题用的短日期（MT 样式：26-09-13 10:04；v1.9.0 从 MM-dd 改成
     *  yy-MM-dd，Max 11:15 补的图3样式就是带年份的两位年格式）。 */
    fun formatDateShort(time: Long): String =
        SimpleDateFormat("yy-MM-dd HH:mm", Locale.getDefault()).format(Date(time))

    fun ext(file: File): String = file.name.substringAfterLast('.', "").lowercase(Locale.US)

    /** 文件名取扩展名（小写；无点或点在首位视作无扩展名）。 */
    fun extOfName(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0) "" else name.substring(dot + 1).lowercase(Locale.US)
    }

    /**
     * v1.9.8.1：三路共用的列表排序（普通 File / SAF / Shizuku 同一套规则）。
     * sort：0 按名称 / 1 按日期 / 2 按大小 / 3 按类型。
     * 文件夹永远在前、文件在后；reverse 只倒各自组内顺序（按类型逆向时
     * 无扩展名文件始终排最后，不会被倒到前面）。
     * typeFoldersOnly（“仅用于文件夹”，v1.9.8.1 最终语义）：勾上后不管
     * 当前是哪种排序方式，排序只对文件夹生效，文件不参与排序、保持传入
     * 的原顺序排在后面；文件夹按当前排序方式排（逆向选中时文件夹倒序）。
     * 按类型时文件夹按名称排（文件夹没有扩展名可分）。不勾时文件夹和
     * 文件都正常排序。注意：按日期正序是文件夹日期倒序、文件日期倒序
     * （组内各排各的，不再像以前那样文件夹和文件混在一起按日期排）；
     * 按大小时文件夹按名称排（文件夹没有可比大小），文件按大小排。
     */
    fun <T> sortNamed(
        list: List<T>, sort: Int, reverse: Boolean, typeFoldersOnly: Boolean,
        isDir: (T) -> Boolean, name: (T) -> String,
        size: (T) -> Long, modified: (T) -> Long
    ): List<T> {
        fun lower(t: T) = name(t).lowercase(Locale.US)
        val dirCmp: Comparator<T> = when (sort) {
            1 -> if (reverse) compareBy { modified(it) } else compareByDescending { modified(it) }
            else -> if (reverse) compareByDescending { lower(it) } else compareBy { lower(it) }
        }
        val fileCmp: Comparator<T> = when (sort) {
            1 -> if (reverse) compareBy { modified(it) } else compareByDescending { modified(it) }
            2 -> if (reverse) compareBy { size(it) } else compareByDescending { size(it) }
            3 -> Comparator { a, b ->
                val ea = extOfName(name(a))
                val eb = extOfName(name(b))
                val c = when {
                    ea.isEmpty() && eb.isEmpty() -> 0
                    ea.isEmpty() -> 1
                    eb.isEmpty() -> -1
                    else -> if (reverse) eb.compareTo(ea) else ea.compareTo(eb)
                }
                if (c != 0) c else if (reverse) lower(b).compareTo(lower(a)) else lower(a).compareTo(lower(b))
            }
            else -> if (reverse) compareByDescending { lower(it) } else compareBy { lower(it) }
        }
        val dirs = list.filter(isDir).sortedWith(dirCmp)
        // 勾上“仅用于文件夹”：文件不参与排序，保持传入的原顺序（目录原顺序）；
        // 只对文件夹子列表应用当前排序比较器。
        val files = if (typeFoldersOnly) list.filter { !isDir(it) }
            else list.filter { !isDir(it) }.sortedWith(fileCmp)
        return dirs + files
    }

    fun isText(f: File): Boolean {
        val e = ext(f)
        return e in setOf("txt","md","xml","json","java","kt","kts","py","js","ts","html","htm","css","csv","log","ini","properties","gradle","sh","c","cpp","h","yaml","yml",
            // v1.9.8：九宫格“编辑文本”补常见文本格式。
            "cfg","conf","toml","sql","env","text","srt","vtt","bat","ps1","rb","go","rs","php","lua","dart")
    }
    /** v1.9.8：HTML 文件（.html/.htm）：主列表点了直接进内置文本编辑器
     *  看代码，不弹浏览器 / 打开方式；“创建HTML”建好也直接进编辑器。 */
    fun isHtml(f: File): Boolean = ext(f) in setOf("html", "htm")
    fun isImage(f: File): Boolean = ext(f) in setOf("jpg","jpeg","png","webp","gif","bmp","heic","heif")
    /** v1.9.8：内置视频播放器的入口判断（点这些后缀进播放器）。
     *  ExoPlayer 能播 mp4/mkv/webm/mov/3gp/m4v/ts/m2ts/mpg/mpeg，
     *  avi/flv/wmv 两种引擎都不支持——进了播放器也会弹提示退出，不崩溃。 */
    fun isVideo(f: File): Boolean = ext(f) in setOf("mp4","mkv","avi","mov","webm","flv","wmv","3gp","m4v","ts","m2ts","mpg","mpeg")
    /** v1.9.8：内置音乐播放器的入口判断（点这些后缀进播放器）。
     *  ExoPlayer 能解 mp3/wav/flac/ogg/m4a/aac/opus/amr/mid，别的
     *  引擎解不了的进了播放器也会弹提示退出，不崩溃。 */
    fun isAudio(f: File): Boolean = ext(f) in setOf("mp3","wav","flac","ogg","m4a","aac","opus","amr","mid","midi")
    fun isZip(f: File): Boolean = ext(f) == "zip"
    fun isApk(f: File): Boolean = ext(f) == "apk"
    fun isArchive(f: File): Boolean = ext(f) in setOf("zip","tar","gz")

    /** Material 3 icon drawable + tile color per file type (MT-style dark folder tile). */
    fun iconResFor(f: File): Int = when {
        f.isDirectory -> R.drawable.ic_folder
        isImage(f) -> R.drawable.ic_image
        isApk(f) -> R.drawable.ic_apk
        isZip(f) || isArchive(f) -> R.drawable.ic_archive
        isAudio(f) -> R.drawable.ic_audio
        isVideo(f) -> R.drawable.ic_video
        ext(f) == "pdf" -> R.drawable.ic_text
        isText(f) -> R.drawable.ic_text
        else -> R.drawable.ic_file
    }

    fun tileColorResFor(f: File): Int = when {
        f.isDirectory -> R.color.tile_folder
        isImage(f) -> R.color.tile_image
        isApk(f) -> R.color.tile_apk
        isZip(f) || isArchive(f) -> R.color.tile_archive
        isAudio(f) -> R.color.tile_audio
        isVideo(f) -> R.color.tile_video
        ext(f) == "pdf" -> R.color.tile_pdf
        isText(f) -> R.color.tile_text
        else -> R.color.tile_generic
    }

    fun iconFor(f: File): String = when {
        f.isDirectory -> "📁"
        isImage(f) -> "🖼️"
        isApk(f) -> "📦"
        isZip(f) || isArchive(f) -> "🗜️"
        isAudio(f) -> "🎵"
        isVideo(f) -> "🎬"
        ext(f) == "pdf" -> "📕"
        isText(f) -> "📄"
        else -> "📄"
    }

    fun copyFile(src: File, dst: File) {
        dst.parentFile?.mkdirs()
        src.inputStream().use { input -> dst.outputStream().use { output -> input.copyTo(output) } }
    }

    fun copyRecursive(src: File, dst: File) {
        if (src.isDirectory) {
            dst.mkdirs()
            src.listFiles()?.forEach { copyRecursive(it, File(dst, it.name)) }
        } else copyFile(src, dst)
    }

    fun deleteRecursive(f: File): Boolean {
        if (f.isDirectory) f.listFiles()?.forEach { deleteRecursive(it) }
        return f.delete()
    }

    fun dirSize(f: File): Long {
        if (f.isFile) return f.length()
        var total = 0L
        f.listFiles()?.forEach { total += dirSize(it) }
        return total
    }

    /** v1.9.4：递归算文件夹总大小，算不出返回 null（listFiles() 为 null
     *  表示无权读取，如根目录/SAF 之外的不可读目录；子目录读不出也算 null，
     *  宁可只显示日期，也别显示一个错的总和）。 */
    // v1.9.8.1：尽力而为——读不到的子目录（如真机上别家应用的
    // Android/data）跳过、只算能读到的，而不是让整棵树都算不出
    // 大小（以前一个子目录 listFiles() 为 null 就整树返回 null，
    // 文件夹永远不显示大小）。
    fun dirSizeOrNull(f: File): Long? {
        if (f.isFile) return f.length()
        val children = f.listFiles() ?: return 0L
        var total = 0L
        for (child in children) {
            total += dirSizeOrNull(child) ?: 0L
        }
        return total
    }

    fun zipFiles(files: List<File>, outZip: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outZip))).use { zos ->
            files.forEach { addToZip(it, it.name, zos) }
        }
    }

    private fun addToZip(file: File, name: String, zos: ZipOutputStream) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { addToZip(it, "$name/${it.name}", zos) }
        } else {
            zos.putNextEntry(ZipEntry(name))
            file.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
        }
    }

    fun unzip(zipFile: File, destDir: File): Int {
        var count = 0
        destDir.mkdirs()
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val out = File(destDir, entry.name)
                // Zip-slip protection
                if (!out.canonicalPath.startsWith(destDir.canonicalPath)) { entry = zis.nextEntry; continue }
                if (entry.isDirectory) out.mkdirs()
                else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zis.copyTo(it) }
                    count++
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return count
    }

    fun storageInfo(path: File): Pair<Long, Long> {
        return try {
            val sf = StatFs(path.absolutePath)
            val total = sf.blockCountLong * sf.blockSizeLong
            val free = sf.availableBlocksLong * sf.blockSizeLong
            Pair(total, free)
        } catch (e: Exception) { Pair(0L, 0L) }
    }

    fun mimeFor(f: File): String = when (ext(f)) {
        "jpg","jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "heic" -> "image/heic"
        "heif" -> "image/heif"
        "pdf" -> "application/pdf"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/x-wav"
        "flac" -> "audio/flac"
        "ogg" -> "audio/ogg"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "opus" -> "audio/opus"
        "amr" -> "audio/amr"
        "mid", "midi" -> "audio/midi"
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "webm" -> "video/webm"
        "flv" -> "video/x-flv"
        "wmv" -> "video/x-ms-wmv"
        "3gp" -> "video/3gpp"
        "m4v" -> "video/x-m4v"
        "ts", "m2ts" -> "video/mp2t"
        "mpg", "mpeg" -> "video/mpeg"
        "apk" -> "application/vnd.android.package-archive"
        "zip" -> "application/zip"
        "txt","md" -> "text/plain"
        "html" -> "text/html"
        "json" -> "application/json"
        else -> "*/*"
    }
}
