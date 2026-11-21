package com.fairuman.filemanager

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.concurrent.Executors

/**
 * RecyclerView adapter for one pane. Position 0 is the ".." parent row
 * (MT-style) when the pane is not at a root. Long-press selects.
 * v1.8.0：每行末尾的 ⋮ 菜单按钮加回来了（Max 要求 v1.7.0 去掉的那颗），
 * 点它弹出该项的操作菜单；多选模式下 ⋮ 藏起来，改显示圆形勾选框
 * （选中为蓝色实心圆 + 白对勾，照 Max 发的参考图）。
 * 根目录（Shizuku）解析不上的条目：大小/日期未知，副标题显示“未知”
 * （dateOverride 为 0 就代表未知，别显示成 1970 年）。
 */
class FileAdapter(
    private val onClick: (File) -> Unit,
    private val onLongClick: (File) -> Boolean,
    private val onParentClick: () -> Unit,
    private val onMoreClick: (File, View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_PARENT = 0
        private const val TYPE_FILE = 1
    }

    var files: List<File> = emptyList()
        private set
    var selected: MutableSet<File> = mutableSetOf()
    var showParent: Boolean = false

    // Shizuku 模式用的覆盖值：File 对象本身读不到根目录的属性（没权限），
    // 目录/大小/日期都由 ls -la 的解析结果提供，key 是绝对路径。
    var isDirOverride: Map<String, Boolean> = emptyMap()
    var sizeOverride: Map<String, Long> = emptyMap()
    var dateOverride: Map<String, Long> = emptyMap()
    // Shizuku 模式下文件夹行显示“文件夹”而不是“x 项”（ls 不给子项数）。
    var dirShowsFolderLabel: Boolean = false
    // v1.9.0 Pin folder：已置顶文件夹的 key（绝对路径 / SAF 合成路径），
    // 置顶的行末尾显示图钉标记。
    var pinnedKeys: Set<String> = emptySet()

    // v1.9.4：文件夹递归总大小的缓存 + 后台计算（MP 样式副标题“日期 + 大小”）。
    // 大小在后台线程算，算好再刷新对应行，绝不卡滚动；换目录时清缓存，
    // 避免改动过的文件夹还显示旧总和。
    private val dirSizeCache = mutableMapOf<String, Long>()
    private val dirSizePending = mutableSetOf<String>()
    private val dirSizeExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun update(newFiles: List<File>, showParentRow: Boolean) {
        files = newFiles
        showParent = showParentRow
        dirSizeCache.clear()
        dirSizePending.clear()
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = files.size + if (showParent) 1 else 0
    override fun getItemViewType(position: Int): Int =
        if (showParent && position == 0) TYPE_PARENT else TYPE_FILE

    private fun fileAt(position: Int): File = files[position - if (showParent) 1 else 0]

    class FileVH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.iconView)
        val name: TextView = v.findViewById(R.id.nameText)
        val info: TextView = v.findViewById(R.id.infoText)
        val moreBtn: ImageButton = v.findViewById(R.id.moreBtn)
        val checkView: ImageView = v.findViewById(R.id.checkView)
        val pinView: ImageView = v.findViewById(R.id.pinView)
        val root: View = v
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FileVH(v)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val vh = holder as FileVH
        val ctx = vh.itemView.context
        if (getItemViewType(position) == TYPE_PARENT) {
            bindName(vh, "..")
            vh.info.text = ""
            setTile(vh, R.drawable.ic_folder, R.color.tile_parent)
            vh.moreBtn.visibility = View.GONE
            vh.checkView.visibility = View.GONE
            vh.pinView.visibility = View.GONE
            vh.root.setOnClickListener { onParentClick() }
            vh.root.setOnLongClickListener { false }
            vh.root.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            return
        }
        val f = fileAt(position)
        // v1.9.1：名字/日期固定字号（13sp/12sp），autoSize 已删，不再复位。
        bindName(vh, f.name)
        val isDir = isDirOverride[f.absolutePath] ?: f.isDirectory
        // v1.9.4：副标题改成“日期 + 大小”（MP 样式，如 26-09-13 10:04 1.61MB）。
        // 文件夹显示递归总和（后台算 + 缓存），文件显示自身大小；
        // 根目录（Shizuku）和 SAF 算不出大小，只显示日期。
        // v1.8.0：日期为 0 表示未知（Shizuku 解析不上的条目），显示“未知”。
        val dateMs = dateOverride[f.absolutePath] ?: f.lastModified()
        val date = if (dateMs == 0L) ctx.getString(R.string.unknown)
            else FileUtils.formatDateShort(dateMs)
        if (dateMs == 0L) {
            bindInfo(vh, ctx.getString(R.string.unknown))
        } else {
            val sizeText: String? = when {
                dirShowsFolderLabel ->
                    if (!isDir) sizeOverride[f.absolutePath]?.let { FileUtils.formatSizeMp(it) } else null
                f.absolutePath.startsWith("/saf/") -> null
                isDir -> dirSizeText(f)
                else -> if (f.exists()) FileUtils.formatSizeMp(f.length()) else null
            }
            bindInfo(vh, if (sizeText != null) "$date $sizeText" else date)
        }
        val iconRes = if (isDir) R.drawable.ic_folder else FileUtils.iconResFor(f)
        val tileRes = if (isDir) R.color.tile_folder else FileUtils.tileColorResFor(f)
        setTile(vh, iconRes, tileRes)
        val isSel = selected.contains(f)
        vh.root.setBackgroundColor(
            if (isSel) ContextCompat.getColor(ctx, R.color.m3_primary_container)
            else android.graphics.Color.TRANSPARENT
        )
        // 多选模式：⋮ 藏起来，显示圆形勾选框；平时反过来。
        val inSelection = selected.isNotEmpty()
        vh.moreBtn.visibility = if (inSelection) View.GONE else View.VISIBLE
        vh.checkView.visibility = if (inSelection) View.VISIBLE else View.GONE
        if (inSelection) setCheck(vh, isSel)
        // v1.9.0：置顶的文件夹行末尾显示图钉标记。
        vh.pinView.visibility = if (isDir && pinnedKeys.contains(f.absolutePath)) View.VISIBLE else View.GONE
        vh.moreBtn.setOnClickListener { onMoreClick(f, it) }
        vh.root.setOnClickListener { onClick(f) }
        vh.root.setOnLongClickListener { onLongClick(f) }
    }

    /** 名字绑定（v1.9.4）：固定 13sp（MP 样式小字号），只设字号和文本。
     *  autoSize 已彻底去掉——它在 RecyclerView 复用时会把上一行长名的
     *  缩小字号带到短名行上；ellipsize 在布局里已关掉，长名换行显示
     *  （最多五行，Max 22:02：一个字都不许截）。 */
    private fun bindName(vh: FileVH, text: String) {
        vh.name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        vh.name.text = text
    }

    /** 文件夹递归总大小（MP 样式副标题用）：缓存命中直接返回；没算过就
     *  在后台线程算，算好后只刷新这一行。算不出（无权读）返回 null，
     *  副标题只显示日期。 */
    private fun dirSizeText(f: File): String? {
        val path = f.absolutePath
        dirSizeCache[path]?.let { return FileUtils.formatSizeMp(it) }
        if (dirSizePending.add(path)) {
            dirSizeExecutor.execute {
                val size = FileUtils.dirSizeOrNull(f)
                mainHandler.post {
                    dirSizePending.remove(path)
                    if (size != null) {
                        dirSizeCache[path] = size
                        val idx = files.indexOfFirst { it.absolutePath == path }
                        if (idx >= 0) notifyItemChanged(idx + if (showParent) 1 else 0)
                    }
                }
            }
        }
        return null
    }

    /** 日期绑定（v1.9.1）：固定 12sp，只设字号和文本，不再动 autoSize。 */
    private fun bindInfo(vh: FileVH, text: String) {
        vh.info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        vh.info.text = text
    }

    /** 多选模式的圆形勾选框：选中是蓝色实心圆 + 白对勾，未选中是灰色圆环。 */
    private fun setCheck(vh: FileVH, isSel: Boolean) {
        val ctx = vh.itemView.context
        val density = ctx.resources.displayMetrics.density
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        if (isSel) {
            bg.setColor(ContextCompat.getColor(ctx, R.color.m3_primary))
            vh.checkView.background = bg
            vh.checkView.setImageResource(R.drawable.ic_check)
            vh.checkView.imageTintList = ColorStateList.valueOf(android.graphics.Color.WHITE)
            vh.checkView.setPadding(
                (5 * density).toInt(), (5 * density).toInt(),
                (5 * density).toInt(), (5 * density).toInt()
            )
        } else {
            bg.setColor(android.graphics.Color.TRANSPARENT)
            bg.setStroke((2 * density).toInt(), ContextCompat.getColor(ctx, R.color.tile_generic))
            vh.checkView.background = bg
            vh.checkView.setImageDrawable(null)
        }
    }

    private fun setTile(vh: FileVH, iconRes: Int, colorRes: Int) {
        val ctx = vh.itemView.context
        val bg = GradientDrawable()
        // v1.9.4：MP 样式小圆角方（圆角 6dp），不再是 24dp 的大圆。
        bg.cornerRadius = 6f * ctx.resources.displayMetrics.density
        bg.setColor(ContextCompat.getColor(ctx, colorRes))
        vh.icon.background = bg
        vh.icon.setImageResource(iconRes)
        vh.icon.imageTintList = ColorStateList.valueOf(android.graphics.Color.WHITE)
    }
}
