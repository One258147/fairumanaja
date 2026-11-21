package com.fairuman.filemanager

import android.os.Environment
import java.io.File

/**
 * 回收站（v1.2.0）：删除不再直接删文件，而是移入
 * /storage/emulated/0/.FairumanajaTrash，并把原路径记在
 * .trash_meta.txt 里（每行：回收站里的文件名<TAB>原绝对路径），
 * 恢复时按原路径移回去。移入失败由调用方回退到直接删除。
 */
object TrashHelper {

    data class TrashItem(
        val trashedName: String,
        val originalPath: String,
        val file: File
    )

    fun trashDir(): File = File(Environment.getExternalStorageDirectory(), ".FairumanajaTrash")

    private fun metaFile(): File = File(trashDir(), ".trash_meta.txt")

    /** 把文件移入回收站；成功返回 true。 */
    fun moveToTrash(src: File): Boolean {
        return try {
            val dir = trashDir()
            if (!dir.exists() && !dir.mkdirs()) return false
            val dst = uniqueIn(dir, src.name)
            val moved = src.renameTo(dst) || run {
                // 跨文件系统 rename 失败时回退到复制+删除。
                try {
                    FileUtils.copyRecursive(src, dst)
                    FileUtils.deleteRecursive(src)
                    true
                } catch (_: Exception) { false }
            }
            if (!moved) return false
            appendMeta(dst.name, src.absolutePath)
            true
        } catch (_: Exception) { false }
    }

    /** v1.9.0：根目录 / SAF 移入回收站时也记一条元数据（恢复时按原路径找回）。 */
    fun recordMove(trashedName: String, originalPath: String) {
        appendMeta(trashedName, originalPath)
    }

    /** 列出回收站里的文件（按原路径元数据匹配；没有元数据的也列出来）。 */
    fun listTrash(): List<TrashItem> {
        val dir = trashDir()
        if (!dir.exists()) return emptyList()
        val meta = readMeta()
        val out = mutableListOf<TrashItem>()
        dir.listFiles()?.forEach { f ->
            if (f.name == metaFile().name) return@forEach
            val orig = meta[f.name] ?: ""
            out.add(TrashItem(f.name, orig, f))
        }
        return out.sortedByDescending { it.file.lastModified() }
    }

    /** 恢复：移回原路径；原路径的父目录没了就恢复到内部储存根目录；重名加后缀。 */
    fun restore(item: TrashItem): Boolean {
        return try {
            val orig = File(item.originalPath)
            var parent = orig.parentFile
            if (parent == null || !parent.exists()) {
                parent = Environment.getExternalStorageDirectory()
            }
            if (!parent.exists()) parent.mkdirs()
            val dst = uniqueIn(parent, orig.name.ifBlank { item.trashedName })
            val ok = item.file.renameTo(dst) || run {
                try {
                    FileUtils.copyRecursive(item.file, dst)
                    FileUtils.deleteRecursive(item.file)
                    true
                } catch (_: Exception) { false }
            }
            if (ok) removeMeta(item.trashedName)
            ok
        } catch (_: Exception) { false }
    }

    /** 彻底删除回收站里的一项。 */
    fun deletePermanently(item: TrashItem): Boolean {
        return try {
            val ok = FileUtils.deleteRecursive(item.file)
            if (ok) removeMeta(item.trashedName)
            ok
        } catch (_: Exception) { false }
    }

    /** 清空回收站。 */
    fun emptyTrash() {
        try {
            trashDir().listFiles()?.forEach { f ->
                if (f.name != metaFile().name) FileUtils.deleteRecursive(f)
            }
            metaFile().delete()
        } catch (_: Exception) { }
    }

    private fun uniqueIn(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var i = 1
        while (candidate.exists()) {
            candidate = if (ext.isEmpty() || ext == name) File(dir, "${base}_$i")
            else File(dir, "${base}_$i.$ext")
            i++
        }
        return candidate
    }

    private fun readMeta(): Map<String, String> {
        val out = mutableMapOf<String, String>()
        try {
            val f = metaFile()
            if (!f.exists()) return out
            f.readLines().forEach { line ->
                val parts = line.split('\t')
                if (parts.size >= 2) out[parts[0]] = parts[1]
            }
        } catch (_: Exception) { }
        return out
    }

    private fun appendMeta(trashedName: String, originalPath: String) {
        try {
            metaFile().appendText("$trashedName\t$originalPath\n")
        } catch (_: Exception) { }
    }

    private fun removeMeta(trashedName: String) {
        try {
            val f = metaFile()
            if (!f.exists()) return
            val kept = f.readLines().filter { it.substringBefore('\t') != trashedName }
            if (kept.isEmpty()) f.delete() else f.writeText(kept.joinToString("\n") + "\n")
        } catch (_: Exception) { }
    }
}
