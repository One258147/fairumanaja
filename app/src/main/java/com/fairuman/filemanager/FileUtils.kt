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

    fun isText(f: File): Boolean {
        val e = ext(f)
        return e in setOf("txt","md","xml","json","java","kt","kts","py","js","ts","html","css","csv","log","ini","properties","gradle","sh","c","cpp","h","yaml","yml")
    }
    fun isImage(f: File): Boolean = ext(f) in setOf("jpg","jpeg","png","webp","gif","bmp")
    fun isZip(f: File): Boolean = ext(f) == "zip"
    fun isApk(f: File): Boolean = ext(f) == "apk"
    fun isArchive(f: File): Boolean = ext(f) in setOf("zip","tar","gz")

    /** Material 3 icon drawable + tile color per file type (MT-style dark folder tile). */
    fun iconResFor(f: File): Int = when {
        f.isDirectory -> R.drawable.ic_folder
        isImage(f) -> R.drawable.ic_image
        isApk(f) -> R.drawable.ic_apk
        isZip(f) || isArchive(f) -> R.drawable.ic_archive
        ext(f) in setOf("mp3","wav","flac","ogg","m4a") -> R.drawable.ic_audio
        ext(f) in setOf("mp4","mkv","avi","mov","webm") -> R.drawable.ic_video
        ext(f) == "pdf" -> R.drawable.ic_text
        isText(f) -> R.drawable.ic_text
        else -> R.drawable.ic_file
    }

    fun tileColorResFor(f: File): Int = when {
        f.isDirectory -> R.color.tile_folder
        isImage(f) -> R.color.tile_image
        isApk(f) -> R.color.tile_apk
        isZip(f) || isArchive(f) -> R.color.tile_archive
        ext(f) in setOf("mp3","wav","flac","ogg","m4a") -> R.color.tile_audio
        ext(f) in setOf("mp4","mkv","avi","mov","webm") -> R.color.tile_video
        ext(f) == "pdf" -> R.color.tile_pdf
        isText(f) -> R.color.tile_text
        else -> R.color.tile_generic
    }

    fun iconFor(f: File): String = when {
        f.isDirectory -> "📁"
        isImage(f) -> "🖼️"
        isApk(f) -> "📦"
        isZip(f) || isArchive(f) -> "🗜️"
        ext(f) in setOf("mp3","wav","flac","ogg","m4a") -> "🎵"
        ext(f) in setOf("mp4","mkv","avi","mov","webm") -> "🎬"
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
        "pdf" -> "application/pdf"
        "mp3" -> "audio/mpeg"
        "mp4" -> "video/mp4"
        "apk" -> "application/vnd.android.package-archive"
        "zip" -> "application/zip"
        "txt","md" -> "text/plain"
        "html" -> "text/html"
        "json" -> "application/json"
        else -> "*/*"
    }
}
