package com.fairuman.filemanager

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * 回收站界面（v1.9.8.1 Compose）：列出已删文件（显示原路径），支持恢复、
 * 彻底删除、清空回收站。
 * v1.9.0 重做（图5/6/7/8）：每项后面有 ⋮，点开只有 删除 / 恢复；
 * 恢复弹“是否将文件 X 恢复至原处？”（确定才恢复），删除弹“是否将文件 X
 * 永久删除？”；长按进选择模式（没有分享；顶栏 ✕ / 已选择 N 项+大小 /
 * 删除 / 恢复 / ⋮，⋮ 里只有 全选）；空回收站显示“回收站是空的”+清空回收站按钮。
 */
class TrashActivity : BaseActivity() {

    private var items by mutableStateOf<List<TrashHelper.TrashItem>>(emptyList())
    private var selectedNames by mutableStateOf<Set<String>>(emptySet())
    private var itemMenu by mutableStateOf<TrashHelper.TrashItem?>(null)
    private var showSelMenu by mutableStateOf(false)
    private var restoreTargets by mutableStateOf<List<TrashHelper.TrashItem>?>(null)
    private var deleteTargets by mutableStateOf<List<TrashHelper.TrashItem>?>(null)
    private var showEmptyConfirm by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashHandler.install(this)
        super.onCreate(savedInstanceState)
        try {
            setContent {
                FairuTheme { TrashScreen() }
            }
            refresh()
        } catch (e: Throwable) {
            CrashHandler.writeLog(this, e)
            try { finish() } catch (_: Exception) { }
        }
    }

    private fun selectedItems(): List<TrashHelper.TrashItem> =
        items.filter { selectedNames.contains(it.trashedName) }

    private fun toggleSelect(item: TrashHelper.TrashItem) {
        selectedNames = if (selectedNames.contains(item.trashedName)) {
            selectedNames - item.trashedName
        } else {
            selectedNames + item.trashedName
        }
    }

    private fun clearSelection() {
        selectedNames = emptySet()
    }

    private fun refresh() {
        Thread {
            val list = TrashHelper.listTrash()
            runOnUiThread {
                items = list
                selectedNames = selectedNames.intersect(list.map { it.trashedName }.toSet())
            }
        }.start()
    }

    private fun doRestore(targets: List<TrashHelper.TrashItem>) {
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

    private fun doDelete(targets: List<TrashHelper.TrashItem>) {
        Thread {
            targets.forEach { TrashHelper.deletePermanently(it) }
            runOnUiThread {
                Toast.makeText(this, R.string.permanently_deleted, Toast.LENGTH_SHORT).show()
                clearSelection()
                refresh()
            }
        }.start()
    }

    @Composable
    private fun TrashScreen() {
        val inSelection = selectedNames.isNotEmpty()
        Column(Modifier.fillMaxSize().background(fairuPageBackground())) {
            if (inSelection) {
                // 选择模式顶栏：✕ / 已选择 N 项 + 大小 / 删除 / 恢复 / ⋮（只有全选）。
                val sel = selectedItems()
                var total = 0L
                sel.forEach { total += if (it.file.isDirectory) FileUtils.dirSize(it.file) else it.file.length() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isMiuixEngine()) top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.background
                            else androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                        )
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { clearSelection() }) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.cancel))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.selected_count, sel.size), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(FileUtils.formatSize(total), fontSize = 13.sp)
                    }
                    IconButton(onClick = { deleteTargets = sel }) {
                        Icon(painterResource(R.drawable.ic_drawer_trash), contentDescription = stringResource(R.string.delete_permanently))
                    }
                    IconButton(onClick = { restoreTargets = sel }) {
                        Icon(painterResource(R.drawable.ic_undo), contentDescription = stringResource(R.string.restore))
                    }
                    Box {
                        IconButton(onClick = { showSelMenu = true }) {
                            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = null)
                        }
                        FairuDropdownMenu(expanded = showSelMenu, onDismissRequest = { showSelMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.select_all)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_check), contentDescription = null) },
                                onClick = {
                                    showSelMenu = false
                                    selectedNames = items.map { it.trashedName }.toSet()
                                }
                            )
                        }
                    }
                }
            } else {
                FairuPageTopBar(title = stringResource(R.string.trash), onBack = { finish() })
            }
            if (items.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(R.string.trash_empty), fontSize = 16.sp)
                    Spacer(Modifier.weight(1f))
                    androidx.compose.material3.Button(onClick = {
                        if (items.isEmpty()) {
                            Toast.makeText(this@TrashActivity, R.string.trash_empty, Toast.LENGTH_SHORT).show()
                        } else {
                            showEmptyConfirm = true
                        }
                    }) {
                        Text(stringResource(R.string.empty_trash))
                    }
                }
            } else {
                // Miuix：列表装进一张白色大圆角卡片；MD3 原样。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (isMiuixEngine()) Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                                .background(androidx.compose.ui.graphics.Color.White)
                            else Modifier
                        )
                ) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(items, key = { it.trashedName }) { item ->
                            TrashRow(item, inSelection)
                        }
                    }
                }
            }
        }
        restoreTargets?.let { targets ->
            FairuAlertDialog(
                onDismissRequest = { restoreTargets = null },
                icon = { Icon(painterResource(R.drawable.ic_undo), contentDescription = null) },
                title = { Text(stringResource(R.string.restore)) },
                text = {
                    Text(
                        if (targets.size == 1) getString(R.string.restore_confirm_msg, targets[0].trashedName)
                        else getString(R.string.restore_confirm_multi_msg, targets.size)
                    )
                },
                confirmButton = {
                    TextButton(onClick = { restoreTargets = null; doRestore(targets) }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { restoreTargets = null }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
        deleteTargets?.let { targets ->
            FairuAlertDialog(
                onDismissRequest = { deleteTargets = null },
                icon = { Icon(painterResource(R.drawable.ic_drawer_trash), contentDescription = null) },
                title = { Text(stringResource(R.string.delete)) },
                text = {
                    Text(
                        if (targets.size == 1) getString(R.string.delete_permanent_msg, targets[0].trashedName)
                        else getString(R.string.delete_permanent_multi_msg, targets.size)
                    )
                },
                confirmButton = {
                    TextButton(onClick = { deleteTargets = null; doDelete(targets) }) { Text(stringResource(R.string.delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTargets = null }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
        if (showEmptyConfirm) {
            FairuAlertDialog(
                onDismissRequest = { showEmptyConfirm = false },
                icon = { Icon(painterResource(R.drawable.ic_drawer_trash), contentDescription = null) },
                title = { Text(stringResource(R.string.empty_trash)) },
                text = { Text(stringResource(R.string.confirm_empty_trash)) },
                confirmButton = {
                    TextButton(onClick = {
                        showEmptyConfirm = false
                        Thread {
                            TrashHelper.emptyTrash()
                            runOnUiThread {
                                Toast.makeText(this@TrashActivity, R.string.trash_emptied, Toast.LENGTH_SHORT).show()
                                refresh()
                            }
                        }.start()
                    }) { Text(stringResource(R.string.empty_trash)) }
                },
                dismissButton = {
                    TextButton(onClick = { showEmptyConfirm = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
    }

    @Composable
    private fun TrashRow(item: TrashHelper.TrashItem, inSelection: Boolean) {
        val isSel = selectedNames.contains(item.trashedName)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isSel) androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                    else Color.Transparent
                )
                .combinedClickable(
                    onClick = {
                        if (inSelection) toggleSelect(item) else itemMenu = item
                    },
                    onLongClick = { toggleSelect(item) }
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                    .background(colorResource(FileUtils.tileColorResFor(item.file))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(FileUtils.iconResFor(item.file)),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    item.trashedName,
                    fontSize = if (isMiuixEngine()) 16.sp else 13.sp,
                    fontWeight = if (isMiuixEngine()) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 5
                )
                val orig = if (item.originalPath.isBlank()) ""
                else getString(R.string.original_path) + ": " + item.originalPath
                val info = listOf(
                    orig,
                    FileUtils.formatSize(item.file.length()) + "  ·  " + FileUtils.formatDate(item.file.lastModified())
                ).filter { it.isNotBlank() }.joinToString("\n")
                Text(
                    info,
                    fontSize = 12.sp,
                    color = if (isMiuixEngine()) top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceVariantSummary
                    else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            if (inSelection) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSel) androidx.compose.material3.MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        )
                        .border(
                            width = if (isSel) 0.dp else 2.dp,
                            color = if (isSel) Color.Transparent else colorResource(R.color.tile_generic),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSel) {
                        Icon(
                            painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                // v1.9.7.2：菜单包在触发按钮的 Box 里锚定，否则落到左下角。
                // 每项后面的 ⋮：只有 删除 / 恢复。
                Box {
                    IconButton(onClick = { itemMenu = item }) {
                        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = null)
                    }
                    if (itemMenu == item) {
                        FairuDropdownMenu(
                            expanded = true,
                            onDismissRequest = { itemMenu = null }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.restore)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_undo), contentDescription = null) },
                                onClick = { itemMenu = null; restoreTargets = listOf(item) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_permanently)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_drawer_trash), contentDescription = null) },
                                onClick = { itemMenu = null; deleteTargets = listOf(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}
