package com.fairuman.filemanager

import android.content.SharedPreferences

/**
 * 自定义本地储存：存在 SharedPreferences 的 "custom_storages" 里，一行一条：
 * 名称 \t 路径 \t 是否隐藏(0/1) [\t 系统文档选择器给的 tree URI]，
 * 顺序即抽屉里的顺序。v1.5.0 之前只有前三列，老记录照样读（URI 为空）。
 * URI 用来在删除时释放持久授权（releasePersistableUriPermission）。
 * v1.6.0：路径可能为空——第三方提供方（如 ZeroTermux）或别家 App 的
 * Android/data 目录映射不出真实路径，只存 URI，浏览时走 SAF。
 */
object CustomStorages {
    const val KEY = "custom_storages"

    data class Storage(val name: String, val path: String, val hidden: Boolean, val uri: String = "")

    /** 一条记录的唯一键：有 URI 的按 URI（第三方提供方没有真实路径），
     *  老记录没有 URI 的按路径。path 为空的记录绝不能只按 path 匹配，
     *  否则所有只存 URI 的记录会被当成同一条。 */
    fun key(s: Storage): String = if (s.uri.isNotBlank()) "u:${s.uri}" else "p:${s.path}"

    fun load(prefs: SharedPreferences): MutableList<Storage> {
        val raw = prefs.getString(KEY, "") ?: ""
        val out = mutableListOf<Storage>()
        for (line in raw.split("\n")) {
            if (line.isBlank()) continue
            val parts = line.split("\t")
            if (parts.size < 2) continue
            out.add(Storage(parts[0], parts[1], parts.getOrNull(2) == "1", parts.getOrNull(3) ?: ""))
        }
        return out
    }

    fun save(prefs: SharedPreferences, list: List<Storage>) {
        val raw = list.joinToString("\n") {
            "${it.name}\t${it.path}\t${if (it.hidden) 1 else 0}\t${it.uri}"
        }
        prefs.edit().putString(KEY, raw).apply()
    }
}
