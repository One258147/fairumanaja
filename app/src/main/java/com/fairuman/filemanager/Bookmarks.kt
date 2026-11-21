package com.fairuman.filemanager

import android.content.Context

/**
 * 书签（v1.9.0 新增：九宫格“更多方式”菜单里的“书签”用它）。
 *
 * 存在 SharedPreferences "fairu_bookmarks" 里，键 "items"，值是多行字符串，
 * 每行一个书签，字段用 \t 分隔：kind \t isDir(true/false) \t name \t target。
 * add 时会把 name / target 里的 \t 和 \n 换成空格，避免破坏行格式。
 */
object Bookmarks {

    /** 一条书签。kind 取值：[KIND_FILE] 普通路径、[KIND_ROOT] 根目录路径、[KIND_SAF] SAF。 */
    data class Bookmark(
        val name: String,
        val target: String,
        val isDir: Boolean,
        val kind: String
    )

    /** 普通路径（内部存储 / 外置存储下的文件或文件夹）。 */
    const val KIND_FILE = "file"

    /** 根目录路径（通过 Shizuku 访问的路径）。 */
    const val KIND_ROOT = "root"

    /** SAF：target 存的是文档 URI 字符串。 */
    const val KIND_SAF = "saf"

    private const val PREFS_NAME = "fairu_bookmarks"
    private const val KEY_ITEMS = "items"

    /** 把字段里的 \t 和 \n 换成空格，避免破坏“每行一个书签”的存储格式。 */
    private fun sanitize(value: String): String =
        value.replace('\t', ' ').replace('\n', ' ')

    /** 解析一行存储文本，格式不对返回 null（脏数据直接跳过）。 */
    private fun parseLine(line: String): Bookmark? {
        if (line.isEmpty()) return null
        val parts = line.split('\t', limit = 4)
        if (parts.size < 4) return null
        return Bookmark(
            name = parts[2],
            target = parts[3],
            isDir = parts[1] == "true",
            kind = parts[0]
        )
    }

    private fun serialize(b: Bookmark): String =
        "${b.kind}\t${b.isDir}\t${b.name}\t${b.target}"

    /** 列出全部书签（按添加顺序）。 */
    fun list(context: Context): List<Bookmark> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split('\n').mapNotNull { parseLine(it) }
    }

    /** 保存整张书签表（内部用）。 */
    private fun save(context: Context, items: List<Bookmark>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_ITEMS, items.joinToString("\n") { serialize(it) })
            .apply()
    }

    /**
     * 添加一条书签。同 kind + target 已存在时不重复添加。
     * name / target 里的 \t 和 \n 会被替换成空格。
     */
    fun add(context: Context, name: String, target: String, isDir: Boolean, kind: String) {
        val cleanName = sanitize(name)
        val cleanTarget = sanitize(target)
        val items = list(context).toMutableList()
        if (items.any { it.kind == kind && it.target == cleanTarget }) return
        items.add(Bookmark(name = cleanName, target = cleanTarget, isDir = isDir, kind = kind))
        save(context, items)
    }

    /** 按 kind + target 删除一条书签（不存在时什么都不做）。 */
    fun remove(context: Context, target: String, kind: String) {
        val items = list(context).filterNot { it.kind == kind && it.target == target }
        save(context, items)
    }

    /** 是否已收藏该 kind + target。 */
    fun contains(context: Context, target: String, kind: String): Boolean =
        list(context).any { it.kind == kind && it.target == target }
}
