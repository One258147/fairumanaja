package com.fairuman.filemanager

import android.content.Context

/**
 * 置顶文件夹（v1.9.0 新增：九宫格“更多方式”菜单里的“Pin folder”用它）。
 *
 * 存在 SharedPreferences "fairu_pins" 里，键 "keys"，值是 StringSet，
 * 存文件夹的唯一键：普通 / 根目录文件夹用绝对路径，SAF 文件夹用合成路径 keyPath。
 * 置顶的文件夹在文件列表里排在前面（排序逻辑由调用方实现）。
 */
object PinnedFolders {

    private const val PREFS_NAME = "fairu_pins"
    private const val KEY_KEYS = "keys"

    /** 取出当前全部置顶键（返回一份拷贝，改它不会影响存盘数据）。 */
    private fun load(context: Context): MutableSet<String> {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_KEYS, null)
        return stored?.toMutableSet() ?: mutableSetOf()
    }

    /** 存盘（内部用）。 */
    private fun save(context: Context, keys: Set<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY_KEYS, keys)
            .apply()
    }

    /** [key] 对应的文件夹是否已置顶。 */
    fun isPinned(context: Context, key: String): Boolean =
        load(context).contains(key)

    /**
     * 切换 [key] 的置顶状态：已置顶就取消，未置顶就置顶。
     * 返回 toggle 后的新状态：true = 已置顶，false = 已取消置顶。
     */
    fun toggle(context: Context, key: String): Boolean {
        val keys = load(context)
        val nowPinned = if (keys.contains(key)) {
            keys.remove(key)
            false
        } else {
            keys.add(key)
            true
        }
        save(context, keys)
        return nowPinned
    }

    /** 全部已置顶文件夹的键（返回一份拷贝）。 */
    fun pinnedKeys(context: Context): Set<String> = load(context)
}
