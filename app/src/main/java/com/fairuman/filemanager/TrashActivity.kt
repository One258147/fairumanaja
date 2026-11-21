package com.fairuman.filemanager

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

/**
 * 回收站界面（v1.2.0）：列出已删文件（显示原路径），支持恢复、
 * 彻底删除、清空回收站。启动失败同样弹框后 finish（v1.5 的教训）。
 * v1.9.0 重做（图5/6/7/8）：每项后面有 ⋮，点开只有 删除 / 恢复；
 * 恢复弹“是否将文件 X 恢复至原处？”（确定才恢复），删除弹“是否将文件 X
 * 永久删除？”；长按进选择模式（没有分享；顶栏 ✕ / 已选择 N 项+大小 /
 * 删除 / 恢复 / ⋮，⋮ 里只有 全选）；空回收站显示“回收站是空的”+清空回收站按钮。
 */
class TrashActivity : BaseActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var selectionTopBar: LinearLayout
    private lateinit var selTitle: TextView
    private lateinit var selSubtitle: TextView
    private var items: List<TrashHelper.TrashItem> = emptyList()
    // 选中的回收站项（按回收站里的文件名记，刷新列表后实例会变，名字不变）。
    private val selectedNames = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashHandler.install(this)
        // super.onCreate 必须无条件最先调用。
        super.onCreate(savedInstanceState)
        try {
            onCreateInner()
        } catch (e: Throwable) {
            CrashHandler.writeLog(this, e)
            try {
                MaterialAlertDialogBuilder(this)
                    .setTitle("启动失败")
                    .setMessage("启动时出错了，错误已保存到 crash.log。\n\n" + CrashHandler.logPathText(this) + "\n\n错误：" + e.toString())
                    .setPositiveButton(R.string.ok) { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
            } catch (_: Exception) { }
            try { finish() } catch (_: Exception) { }
        }
    }

    private fun onCreateInner() {
        setContentView(R.layout.activity_trash)
        val toolbar = findViewById<MaterialToolbar>(R.id.trashToolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        recycler = findViewById(R.id.trashList)
        recycler.layoutManager = LinearLayoutManager(this)
        emptyView = findViewById(R.id.trashEmptyView)
        findViewById<MaterialButton>(R.id.btnEmptyTrash).setOnClickListener { confirmEmptyTrash() }
        selectionTopBar = findViewById(R.id.trashSelectionTopBar)
        selTitle = findViewById(R.id.trashSelTitle)
        selSubtitle = findViewById(R.id.trashSelSubtitle)
        findViewById<ImageButton>(R.id.btnTrashSelClose).setOnClickListener { clearSelection() }
        findViewById<ImageButton>(R.id.btnTrashSelDelete).setOnClickListener {
            deleteItems(selectedItems())
        }
        findViewById<ImageButton>(R.id.btnTrashSelRestore).setOnClickListener {
            restoreItems(selectedItems())
        }
        findViewById<ImageButton>(R.id.btnTrashSelMore).setOnClickListener { showSelectionMenu(it) }
        refresh()
    }

    private fun selectedItems(): List<TrashHelper.TrashItem> =
        items.filter { selectedNames.contains(it.trashedName) }

    private fun toggleSelect(item: TrashHelper.TrashItem) {
        if (!selectedNames.remove(item.trashedName)) selectedNames.add(item.trashedName)
        refreshAdapter()
        updateSelectionTopBar()
    }

    private fun clearSelection() {
        selectedNames.clear()
        refreshAdapter()
        updateSelectionTopBar()
    }

    private fun refreshAdapter() {
        recycler.adapter = TrashAdapter(items, selectedNames,
            onMenu = { item, v -> showItemMenu(item, v) },
            onToggle = { toggleSelect(it) })
    }

    private fun updateSelectionTopBar() {
        if (selectedNames.isEmpty()) {
            selectionTopBar.visibility = View.GONE
        } else {
            selectionTopBar.visibility = View.VISIBLE
            val sel = selectedItems()
            selTitle.text = getString(R.string.selected_count, sel.size)
            var total = 0L
            sel.forEach { total += if (it.file.isDirectory) FileUtils.dirSize(it.file) else it.file.length() }
            selSubtitle.text = FileUtils.formatSize(total)
        }
    }

    private fun refresh() {
        Thread {
            val list = TrashHelper.listTrash()
            runOnUiThread {
                items = list
                // 刷新后已删的项不再选中（名字对不上了就自动掉出选择态）。
                selectedNames.retainAll(list.map { it.trashedName }.toSet())
                refreshAdapter()
                updateSelectionTopBar()
                emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                recycler.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
            }
        }.start()
    }

    /** 每项后面的 ⋮：只有 删除 / 恢复 两个功能（图5/6/7）。 */
    private fun showItemMenu(item: TrashHelper.TrashItem, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.setForceShowIcon(true)
        popup.menu.add(0, 1, 0, R.string.restore).icon = tintedIcon(R.drawable.ic_undo)
        popup.menu.add(0, 2, 0, R.string.delete_permanently).icon = tintedIcon(R.drawable.ic_drawer_trash)
        popup.setOnMenuItemClickListener { m ->
            when (m.itemId) {
                1 -> restoreItems(listOf(item))
                2 -> deleteItems(listOf(item))
            }
            true
        }
        popup.show()
    }

    /** 选择模式顶栏的 ⋮：只有 全选 一个功能。 */
    private fun showSelectionMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.setForceShowIcon(true)
        popup.menu.add(0, 1, 0, R.string.select_all).icon = tintedIcon(R.drawable.ic_check)
        popup.setOnMenuItemClickListener {
            if (it.itemId == 1) {
                selectedNames.addAll(items.map { item -> item.trashedName })
                refreshAdapter()
                updateSelectionTopBar()
            }
            true
        }
        popup.show()
    }

    /** 恢复：先弹确认框（图6），确定才恢复。 */
    private fun restoreItems(targets: List<TrashHelper.TrashItem>) {
        if (targets.isEmpty()) return
        val msg = if (targets.size == 1) getString(R.string.restore_confirm_msg, targets[0].trashedName)
            else getString(R.string.restore_confirm_multi_msg, targets.size)
        MaterialAlertDialogBuilder(this).setTitle(R.string.restore).setIcon(R.drawable.ic_undo).setMessage(msg)
            .setPositiveButton(R.string.ok) { _, _ ->
                Thread {
                    var ok = true
                    targets.forEach { if (!TrashHelper.restore(it)) ok = false }
                    runOnUiThread {
                        Toast.makeText(this, if (ok) R.string.restored else R.string.restore_failed, Toast.LENGTH_SHORT).show()
                        clearSelection()
                        refresh()
                    }
                }.start()
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    /** 彻底删除：先弹确认框（图7），确定才删。 */
    private fun deleteItems(targets: List<TrashHelper.TrashItem>) {
        if (targets.isEmpty()) return
        val msg = if (targets.size == 1) getString(R.string.delete_permanent_msg, targets[0].trashedName)
            else getString(R.string.delete_permanent_multi_msg, targets.size)
        MaterialAlertDialogBuilder(this).setTitle(R.string.delete).setIcon(R.drawable.ic_drawer_trash).setMessage(msg)
            .setPositiveButton(R.string.delete) { _, _ ->
                Thread {
                    targets.forEach { TrashHelper.deletePermanently(it) }
                    runOnUiThread {
                        Toast.makeText(this, R.string.permanently_deleted, Toast.LENGTH_SHORT).show()
                        clearSelection()
                        refresh()
                    }
                }.start()
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun confirmEmptyTrash() {
        if (items.isEmpty()) {
            Toast.makeText(this, R.string.trash_empty, Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(this).setTitle(R.string.empty_trash).setIcon(R.drawable.ic_drawer_trash).setMessage(R.string.confirm_empty_trash)
            .setPositiveButton(R.string.empty_trash) { _, _ ->
                Thread {
                    TrashHelper.emptyTrash()
                    runOnUiThread {
                        Toast.makeText(this, R.string.trash_emptied, Toast.LENGTH_SHORT).show()
                        refresh()
                    }
                }.start()
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun tintedIcon(resId: Int): android.graphics.drawable.Drawable? {
        val d = AppCompatResources.getDrawable(this, resId)?.mutate() ?: return null
        val color = MaterialColors.getColor(
            this, androidx.appcompat.R.attr.colorControlNormal, android.graphics.Color.GRAY
        )
        DrawableCompat.setTint(d, color)
        return d
    }

    private class TrashAdapter(
        private val items: List<TrashHelper.TrashItem>,
        private val selectedNames: Set<String>,
        private val onMenu: (TrashHelper.TrashItem, View) -> Unit,
        private val onToggle: (TrashHelper.TrashItem) -> Unit
    ) : RecyclerView.Adapter<TrashAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: android.widget.ImageView = v.findViewById(R.id.iconView)
            val name: TextView = v.findViewById(R.id.nameText)
            val info: TextView = v.findViewById(R.id.infoText)
            val moreBtn: ImageButton = v.findViewById(R.id.moreBtn)
            val checkView: ImageView = v.findViewById(R.id.checkView)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val v = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_file, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val ctx = holder.itemView.context
            holder.name.text = item.trashedName
            // 图标：复用 FileAdapter 的色块样式，否则 item_file 的白图标在白底上看不见。
            val bg = android.graphics.drawable.GradientDrawable()
            bg.cornerRadius = 24f * ctx.resources.displayMetrics.density
            bg.setColor(androidx.core.content.ContextCompat.getColor(ctx, FileUtils.tileColorResFor(item.file)))
            holder.icon.background = bg
            holder.icon.setImageResource(FileUtils.iconResFor(item.file))
            holder.icon.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            holder.info.maxLines = 2
            val orig = if (item.originalPath.isBlank()) ""
            else ctx.getString(R.string.original_path) + ": " + item.originalPath
            holder.info.text = listOf(
                orig,
                FileUtils.formatSize(item.file.length()) + "  ·  " + FileUtils.formatDate(item.file.lastModified())
            ).filter { it.isNotBlank() }.joinToString("\n")
            val isSel = selectedNames.contains(item.trashedName)
            val inSelection = selectedNames.isNotEmpty()
            holder.moreBtn.visibility = if (inSelection) View.GONE else View.VISIBLE
            holder.checkView.visibility = if (inSelection) View.VISIBLE else View.GONE
            if (inSelection) setCheck(holder, isSel)
            holder.itemView.setBackgroundColor(
                if (isSel) ContextCompat.getColor(ctx, R.color.m3_primary_container)
                else android.graphics.Color.TRANSPARENT
            )
            holder.moreBtn.setOnClickListener { onMenu(item, it) }
            holder.itemView.setOnClickListener {
                if (inSelection) onToggle(item) else onMenu(item, holder.itemView)
            }
            holder.itemView.setOnLongClickListener { onToggle(item); true }
        }

        /** 多选模式的圆形勾选框（跟 FileAdapter 同款：选中蓝色实心圆 + 白对勾）。 */
        private fun setCheck(vh: VH, isSel: Boolean) {
            val ctx = vh.itemView.context
            val density = ctx.resources.displayMetrics.density
            val bg = android.graphics.drawable.GradientDrawable()
            bg.shape = android.graphics.drawable.GradientDrawable.OVAL
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
    }
}
