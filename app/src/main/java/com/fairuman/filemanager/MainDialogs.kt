package com.fairuman.filemanager

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

// ---------- 对话框请求（v1.9.8.1：对话框由状态驱动，置非空即显示） ----------

data class DeleteRequest(val pane: MainActivity.Pane, val targets: List<File>, val stats: MainActivity.DeleteStats)
data class FileMenuRequest(val pane: MainActivity.Pane, val file: File)
data class RenameRequest(
    val pane: MainActivity.Pane,
    val file: File,
    val kind: Int, // 0 普通，1 根目录，2 SAF
    val shizukuEntry: ShizukuRoot.Entry? = null,
    val safEntry: SafEntry? = null
)
data class MoreActionsRequest(val pane: MainActivity.Pane, val file: File)
data class ShortcutRequest(val pane: MainActivity.Pane, val file: File)
data class PropertiesRequest(val message: String)
data class BookmarkMenuRequest(val bookmark: Bookmarks.Bookmark)
data class PasswordRequest(val pane: MainActivity.Pane, val file: File, val encrypt: Boolean)
data class StorageMenuRequest(val storage: CustomStorages.Storage)
data class StorageRenameRequest(val storage: CustomStorages.Storage)
data class StorageDeleteRequest(val storage: CustomStorages.Storage)
data class CustomStorageRow(val storage: CustomStorages.Storage, val displayPath: String)

// ---------- 删除对话框（v1.9.0 流程：标题 + 是否移入回收站 + 大小/文件数/文件夹数 + 复选框） ----------

@Composable
fun MainActivity.DeleteDialog(request: DeleteRequest) {
    var toTrash by remember { mutableStateOf(true) }
    val pane = request.pane
    val targets = request.targets
    val stats = request.stats
    val msg = if (targets.size == 1) stringResource(R.string.delete_to_trash_msg, targets[0].name)
        else stringResource(R.string.delete_to_trash_multi_msg, targets.size)
    val hasDir = targets.any { f ->
        when {
            pane.safMode -> pane.safEntries.firstOrNull { it.keyPath == f.absolutePath }?.isDir == true
            pane.shizukuMode -> pane.shizukuEntries.firstOrNull { it.path == f.absolutePath }?.isDir == true
            else -> f.isDirectory
        }
    }
    val info = buildString {
        append("${stringResource(R.string.size)}  ${FileUtils.formatSize(stats.size)}")
        if (hasDir && stats.countsKnown) {
            append("\n${stringResource(R.string.file_count)}  ${stats.files}")
            append("\n${stringResource(R.string.folder_count)}  ${stats.dirs}")
        }
    }
    FairuAlertDialog(
        onDismissRequest = { deleteDialog = null },
        icon = { Icon(painterResource(R.drawable.ic_drawer_trash), contentDescription = null) },
        title = { Text(stringResource(R.string.delete)) },
        text = {
            Column {
                Text(msg, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                Text(info, fontSize = 15.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = toTrash, onCheckedChange = { toTrash = it })
                    Text(stringResource(R.string.move_to_trash))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                deleteDialog = null
                doDelete(pane, targets, toTrash)
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = { deleteDialog = null }) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// ---------- 重命名对话框（普通 / 根目录 / SAF 三态） ----------

@Composable
fun MainActivity.RenameDialog(request: RenameRequest) {
    var name by remember { mutableStateOf(request.file.name) }
    FairuAlertDialog(
        onDismissRequest = { renameDialog = null },
        title = { Text(stringResource(R.string.rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val n = name.trim()
                if (n.isNotEmpty() && n != request.file.name) {
                    renameDialog = null
                    doRename(request, n)
                } else {
                    renameDialog = null
                }
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = { renameDialog = null }) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// ---------- 新建对话框（v1.9.5：取消 / 文件 / 文件夹，粘贴已删） ----------

@Composable
fun MainActivity.NewDialog() {
    val pane = activePane()
    var name by remember { mutableStateOf("") }
    val miuix = isMiuixEngine()
    androidx.compose.ui.window.Dialog(onDismissRequest = { showNewDialog = false }) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(if (miuix) 20.dp else 28.dp))
                .background(if (miuix) Color.White else MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.new_title),
                    fontSize = 20.sp,
                    fontWeight = if (miuix) FontWeight.Bold else FontWeight.Normal
                )
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(stringResource(R.string.file_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { showNewDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
                Row {
                    TextButton(onClick = {
                        val n = name.trim()
                        if (!checkNewName(pane, n)) return@TextButton
                        showNewDialog = false
                        createFile(pane, n)
                    }) {
                        Icon(painterResource(R.drawable.ic_file), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.new_file_short))
                    }
                    TextButton(onClick = {
                        val n = name.trim()
                        if (!checkNewName(pane, n)) return@TextButton
                        showNewDialog = false
                        createFolder(pane, n)
                    }) {
                        Icon(painterResource(R.drawable.ic_folder), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.new_folder_short))
                    }
                }
            }
        }
    }
}

// ---------- 每行 ⋮ 菜单（v1.9.0：文件 10 项 / 文件夹 9 项，压缩不在 ⋮ 菜单里） ----------

@Composable
fun MainActivity.FileMenuDialog(request: FileMenuRequest) {
    val pane = request.pane
    val f = request.file
    val isDir = isDirOf(pane, f)
    val isText = when {
        pane.safMode -> pane.safEntries.firstOrNull { it.keyPath == f.absolutePath }?.let { FileUtils.isText(File(it.name)) } ?: false
        pane.shizukuMode -> FileUtils.isText(f)
        else -> FileUtils.isText(f)
    }
    FairuDropdownMenu(
        expanded = true,
        onDismissRequest = { fileMenu = null }
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.open)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_folder), contentDescription = null) },
            onClick = { fileMenu = null; onFileClick(pane, f) }
        )
        if (!isDir) DropdownMenuItem(
            text = { Text(stringResource(R.string.share)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_share), contentDescription = null) },
            onClick = { fileMenu = null; shareFiles(pane, listOf(f)) }
        )
        if (isText) DropdownMenuItem(
            text = { Text(stringResource(R.string.edit_text)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_text), contentDescription = null) },
            onClick = {
                fileMenu = null
                openTextEditor(pane, f)
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.copy)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_copy), contentDescription = null) },
            onClick = { fileMenu = null; copyItemToClipboard(pane, f, cut = false) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.cut)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_cut), contentDescription = null) },
            onClick = { fileMenu = null; copyItemToClipboard(pane, f, cut = true) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.delete)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_drawer_trash), contentDescription = null) },
            onClick = { fileMenu = null; confirmDelete(listOf(f)) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.rename)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_edit), contentDescription = null) },
            onClick = { fileMenu = null; renameItem(pane, f) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.properties)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_info), contentDescription = null) },
            onClick = { fileMenu = null; showPropertiesFor(pane, f) }
        )
        if (!isDir) DropdownMenuItem(
            text = { Text(stringResource(R.string.open_with)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_arrow_forward), contentDescription = null) },
            onClick = { fileMenu = null; openWithFor(pane, f) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.copy_path)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_copy), contentDescription = null) },
            onClick = { fileMenu = null; copyItemPath(pane, f) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.move_item)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_move), contentDescription = null) },
            enabled = otherPaneTargetValid(),
            onClick = { fileMenu = null; transferToOtherPane(pane, f, move = true) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.more_actions)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_more_vert), contentDescription = null) },
            onClick = { fileMenu = null; showMoreActionsDialog(pane, f) }
        )
    }
}

// ---------- 多选 ⋮ 菜单（v1.8.0：复制 / 剪切 / 粘贴 / 重命名 / 属性 / 全选 / 取消选择） ----------

@Composable
fun MainActivity.SelectionMenuDialog() {
    val pane = activePane()
    val single = pane.selected.size == 1
    FairuDropdownMenu(
        expanded = showSelectionMenu,
        onDismissRequest = { showSelectionMenu = false }
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.copy)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_copy), contentDescription = null) },
            onClick = { showSelectionMenu = false; copySelection() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.cut)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_cut), contentDescription = null) },
            onClick = { showSelectionMenu = false; cutSelection() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.paste)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_paste), contentDescription = null) },
            onClick = { showSelectionMenu = false; pasteInto(pane) }
        )
        if (single) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rename)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_edit), contentDescription = null) },
                onClick = {
                    showSelectionMenu = false
                    val f = pane.selected.firstOrNull() ?: return@DropdownMenuItem
                    renameItem(pane, f)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.properties)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_info), contentDescription = null) },
                onClick = {
                    showSelectionMenu = false
                    val f = pane.selected.firstOrNull() ?: return@DropdownMenuItem
                    showPropertiesFor(pane, f)
                }
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.select_all)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_check), contentDescription = null) },
            onClick = { showSelectionMenu = false; pane.selected.addAll(pane.files) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.deselect_all)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_close), contentDescription = null) },
            onClick = { showSelectionMenu = false; clearSelection(pane) }
        )
    }
}

// ---------- 更多方式九宫格（v1.9.0 最终版：只放 ⋮ 菜单之外的额外功能，共 9 个） ----------

@Composable
fun MainActivity.MoreActionsGridDialog(request: MoreActionsRequest) {
    val pane = request.pane
    val f = request.file
    val isDir = isDirOf(pane, f)
    val name = f.name
    val isZip = FileUtils.isZip(File(name))
    val isEnc = name.endsWith(".enc", ignoreCase = true)
    val pinned = PinnedFolders.isPinned(this, f.absolutePath)
    val bookmarked = Bookmarks.contains(this, bookmarkTarget(pane, f), bookmarkKind(pane))
    val hidden = name.startsWith(".")
    data class GridAction(val labelRes: Int, val iconRes: Int, val enabled: Boolean, val run: () -> Unit)
    val buttons = listOf(
        GridAction(R.string.compress_item, R.drawable.ic_archive, true) { compressItem(pane, f) },
        GridAction(R.string.share_zip, R.drawable.ic_share, true) { shareAsZip(pane, f) },
        GridAction(if (pinned) R.string.unpin_folder else R.string.pin_folder, R.drawable.ic_pin, isDir) { pinToggle(pane, f) },
        GridAction(if (hidden) R.string.unhide else R.string.hide, R.drawable.ic_hide, true) { hideToggle(pane, f) },
        GridAction(R.string.bookmarks_title, R.drawable.ic_bookmark, true) { bookmarkToggle(pane, f) },
        GridAction(R.string.encrypt_file, R.drawable.ic_shield, !isDir && !isEnc) { encryptItem(pane, f) },
        GridAction(R.string.decrypt_file, R.drawable.ic_shield, !isDir && isEnc) { decryptItem(pane, f) },
        GridAction(R.string.extract, R.drawable.ic_archive, !isDir && isZip) { unzipItem(pane, f) },
        GridAction(R.string.create_shortcut, R.drawable.ic_shortcut, true) { showCreateShortcutDialog(pane, f) }
    )
    FairuAlertDialog(
        onDismissRequest = { moreActionsDialog = null },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        moreActionsDialog = null
                        renameItem(pane, f)
                    }) {
                        Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.rename))
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(buttons) { b ->
                        // Miuix：白色按钮 + 浅灰边框 + 主色图标文字（跟应用配色走）；
                        // MD3：原来的 secondaryContainer 底。
                        val miuix = isMiuixEngine()
                        val bg = if (miuix) {
                            if (b.enabled) Color.White else Color(0xFFF5F5F7)
                        } else if (b.enabled) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                        val fg = if (miuix) {
                            if (b.enabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        } else if (b.enabled) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(bg)
                                .then(
                                    if (miuix && b.enabled) Modifier.border(
                                        1.dp, Color(0xFFE5E5EA), RoundedCornerShape(12.dp)
                                    ) else Modifier
                                )
                                .clickable(enabled = b.enabled) {
                                    moreActionsDialog = null
                                    b.run()
                                }
                                .padding(vertical = 14.dp, horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(painterResource(b.iconRes), contentDescription = null, tint = fg, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.height(4.dp))
                                Text(stringResource(b.labelRes), color = fg, fontSize = 13.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { },
        dismissButton = { }
    )
}

// ---------- 创建快捷方式对话框（v1.9.1：图标 + 名称 + 类型 + 执行操作，取消/确定） ----------

@Composable
fun MainActivity.ShortcutDialog(request: ShortcutRequest) {
    val pane = request.pane
    val f = request.file
    val isDir = isDirOf(pane, f)
    val name = f.name
    var label by remember { mutableStateOf(name) }
    var typeIcon by remember { mutableStateOf(false) }
    var selectedAction by remember { mutableStateOf(MainActivity.SHORTCUT_LOCATE) }
    var typeExpanded by remember { mutableStateOf(false) }
    var actionExpanded by remember { mutableStateOf(false) }
    val actions = listOf(
        R.string.shortcut_action_locate to MainActivity.SHORTCUT_LOCATE,
        R.string.shortcut_action_locate_click to MainActivity.SHORTCUT_LOCATE_OPEN,
        R.string.shortcut_action_open_editor to MainActivity.SHORTCUT_OPEN_EDITOR,
        R.string.shortcut_action_preview_html to MainActivity.SHORTCUT_PREVIEW_HTML
    )
    val iconRes = if (isDir) R.drawable.ic_folder else FileUtils.iconResFor(f)
    val tileRes = if (isDir) R.color.tile_folder else FileUtils.tileColorResFor(f)
    FairuAlertDialog(
        onDismissRequest = { shortcutDialog = null },
        title = { Text(stringResource(R.string.create_shortcut)) },
        text = {
            Column {
                Text(stringResource(R.string.shortcut_icon), fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colorResource(tileRes)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(iconRes),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.shortcut_name), fontSize = 16.sp)
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.shortcut_type), fontSize = 16.sp)
                Box {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { typeExpanded = true }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(if (typeIcon) R.string.shortcut_type_icon else R.string.shortcut_type_desktop),
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text("▼", fontSize = 14.sp)
                    }
                    FairuDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.shortcut_type_desktop)) },
                            onClick = { typeIcon = false; typeExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.shortcut_type_icon)) },
                            onClick = { typeIcon = true; typeExpanded = false }
                        )
                    }
                }
                Text(
                    stringResource(if (typeIcon) R.string.shortcut_type_icon_desc else R.string.shortcut_type_desktop_desc),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.shortcut_action), fontSize = 16.sp)
                Box {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { actionExpanded = true }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(actions.first { it.second == selectedAction }.first),
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text("▼", fontSize = 14.sp)
                    }
                    FairuDropdownMenu(expanded = actionExpanded, onDismissRequest = { actionExpanded = false }) {
                        actions.forEach { (res, code) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(res)) },
                                onClick = { selectedAction = code; actionExpanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val l = label.trim()
                if (l.isEmpty()) {
                    android.widget.Toast.makeText(this@ShortcutDialog, R.string.invalid_name, android.widget.Toast.LENGTH_SHORT).show()
                    return@TextButton
                }
                shortcutDialog = null
                pinShortcut(f.absolutePath, l, isDir, typeIcon, selectedAction)
            }) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = { shortcutDialog = null }) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// ---------- 属性对话框 ----------

@Composable
fun MainActivity.PropertiesDialog(request: PropertiesRequest) {
    FairuAlertDialog(
        onDismissRequest = { propertiesDialog = null },
        title = { Text(stringResource(R.string.properties)) },
        text = { Text(request.message) },
        confirmButton = {
            TextButton(onClick = { propertiesDialog = null }) { Text(stringResource(R.string.ok)) }
        }
    )
}

// ---------- 排序对话框（v1.9.0：选一个再点确认才排序） ----------

@Composable
fun MainActivity.SortDialog() {
    var selected by remember { mutableStateOf(prefs.getInt("sort", 0)) }
    val options = listOf(
        stringResource(R.string.sort_name),
        stringResource(R.string.sort_date),
        stringResource(R.string.sort_size)
    )
    FairuAlertDialog(
        onDismissRequest = { showSortDialog = false },
        icon = { Icon(painterResource(R.drawable.ic_sort), contentDescription = null) },
        title = { Text(stringResource(R.string.sort)) },
        text = {
            Column {
                options.forEachIndexed { i, label ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { selected = i }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == i, onClick = { selected = i })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                showSortDialog = false
                prefs.edit().putInt("sort", selected).apply()
                load()
            }) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = { showSortDialog = false }) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// ---------- 顶部 ⋮ 菜单（v1.9.0：双窗格 / 排序 / 显示隐藏文件 / 刷新） ----------

@Composable
fun MainActivity.TopMenuDialog() {
    val showHidden = prefs.getBoolean("show_hidden", false)
    FairuDropdownMenu(
        expanded = showTopMenu,
        onDismissRequest = { showTopMenu = false }
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.dual_pane)) },
            leadingIcon = {
                Icon(
                    painterResource(if (dualPane) R.drawable.ic_check else R.drawable.ic_swap),
                    contentDescription = null
                )
            },
            onClick = {
                showTopMenu = false
                dualPane = !dualPane
                prefs.edit().putBoolean("dual_pane", dualPane).apply()
                applyDualPane()
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.sort)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_sort), contentDescription = null) },
            onClick = { showTopMenu = false; showSortDialog = true }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.show_hidden)) },
            leadingIcon = {
                Icon(
                    painterResource(if (showHidden) R.drawable.ic_check else R.drawable.ic_eye),
                    contentDescription = null
                )
            },
            onClick = {
                showTopMenu = false
                prefs.edit().putBoolean("show_hidden", !showHidden).apply()
                load()
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.refresh)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_redo), contentDescription = null) },
            onClick = { showTopMenu = false; load() }
        )
    }
}

// ---------- 抽屉右上角 ⋮ 菜单（v1.5.0：只剩 添加本地储存） ----------

@Composable
fun MainActivity.DrawerOverflowMenuDialog() {
    FairuDropdownMenu(
        expanded = showDrawerOverflowMenu,
        onDismissRequest = { showDrawerOverflowMenu = false }
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.add_local_storage)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_drawer_storage), contentDescription = null) },
            onClick = { showDrawerOverflowMenu = false; openStoragePicker() }
        )
    }
}

// ---------- 书签长按菜单（只删记录） ----------

@Composable
fun MainActivity.BookmarkMenuDialog(request: BookmarkMenuRequest) {
    val act = this
    FairuDropdownMenu(
        expanded = true,
        onDismissRequest = { bookmarkMenu = null }
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.delete)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_drawer_trash), contentDescription = null) },
            onClick = {
                bookmarkMenu = null
                Bookmarks.remove(act, request.bookmark.target, request.bookmark.kind)
                refreshBookmarks()
                android.widget.Toast.makeText(act, R.string.bookmark_removed, android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }
}

// ---------- 加密 / 解密密码框 ----------

@Composable
fun MainActivity.PasswordDialog(request: PasswordRequest) {
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    FairuAlertDialog(
        onDismissRequest = { passwordDialog = null },
        icon = { Icon(painterResource(R.drawable.ic_shield), contentDescription = null) },
        title = { Text(stringResource(if (request.encrypt) R.string.encrypt_file else R.string.decrypt_file)) },
        text = {
            Column {
                OutlinedTextField(
                    value = p1,
                    onValueChange = { p1 = it },
                    placeholder = { Text(stringResource(R.string.password_hint)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                if (request.encrypt) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = p2,
                        onValueChange = { p2 = it },
                        placeholder = { Text(stringResource(R.string.password_confirm_hint)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (request.encrypt) {
                    when {
                        p1.isEmpty() -> android.widget.Toast.makeText(this, R.string.password_hint, android.widget.Toast.LENGTH_SHORT).show()
                        p1 != p2 -> android.widget.Toast.makeText(this, R.string.password_mismatch, android.widget.Toast.LENGTH_SHORT).show()
                        else -> {
                            passwordDialog = null
                            Thread { doEncrypt(request.pane, request.file, p1) }.start()
                        }
                    }
                } else {
                    if (p1.isEmpty()) {
                        android.widget.Toast.makeText(this, R.string.password_hint, android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        passwordDialog = null
                        Thread { doDecrypt(request.pane, request.file, p1) }.start()
                    }
                }
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = { passwordDialog = null }) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// ---------- 自定义储存长按菜单（重命名 / 删除 / 隐藏 / 排序） ----------

@Composable
fun MainActivity.StorageMenuDialog(request: StorageMenuRequest) {
    val act = this
    val s = request.storage
    FairuDropdownMenu(
        expanded = true,
        onDismissRequest = { storageMenu = null }
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.rename)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_edit), contentDescription = null) },
            onClick = { storageMenu = null; storageRenameDialog = StorageRenameRequest(s) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.delete)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_drawer_trash), contentDescription = null) },
            onClick = { storageMenu = null; storageDeleteDialog = StorageDeleteRequest(s) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.hide)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_hide), contentDescription = null) },
            onClick = {
                storageMenu = null
                val list = CustomStorages.load(prefs)
                val i = list.indexOfFirst { CustomStorages.key(it) == CustomStorages.key(s) }
                if (i >= 0) {
                    list[i] = list[i].copy(hidden = true)
                    CustomStorages.save(prefs, list)
                    refreshCustomStorages()
                }
                android.widget.Toast.makeText(act, R.string.saved, android.widget.Toast.LENGTH_SHORT).show()
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.sort)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_sort), contentDescription = null) },
            onClick = {
                storageMenu = null
                val list = CustomStorages.load(prefs)
                if (list.isEmpty()) {
                    android.widget.Toast.makeText(act, R.string.no_files, android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    showSortStoragesDialog = true
                }
            }
        )
    }
}

// ---------- 自定义储存重命名 ----------

@Composable
fun MainActivity.StorageRenameDialog(request: StorageRenameRequest) {
    val act = this
    var name by remember { mutableStateOf(request.storage.name) }
    FairuAlertDialog(
        onDismissRequest = { storageRenameDialog = null },
        title = { Text(stringResource(R.string.rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val n = name.trim().replace("\t", " ").replace("\n", " ")
                if (n.isNotEmpty() && n != request.storage.name) {
                    val list = CustomStorages.load(prefs)
                    val i = list.indexOfFirst { CustomStorages.key(it) == CustomStorages.key(request.storage) }
                    if (i >= 0) {
                        list[i] = list[i].copy(name = n)
                        CustomStorages.save(prefs, list)
                        refreshCustomStorages()
                    }
                    android.widget.Toast.makeText(act, R.string.renamed, android.widget.Toast.LENGTH_SHORT).show()
                }
                storageRenameDialog = null
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = { storageRenameDialog = null }) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// ---------- 自定义储存删除确认（只删记录，不删文件夹本身；释放持久授权） ----------

@Composable
fun MainActivity.StorageDeleteDialog(request: StorageDeleteRequest) {
    val s = request.storage
    FairuAlertDialog(
        onDismissRequest = { storageDeleteDialog = null },
        title = { Text(stringResource(R.string.confirm_delete)) },
        text = { Text(s.name + "\n" + s.path) },
        confirmButton = {
            TextButton(onClick = {
                storageDeleteDialog = null
                deleteCustomStorage(s)
            }) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = { storageDeleteDialog = null }) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// ---------- 自定义储存排序（上下移按钮 + 取消隐藏） ----------

@Composable
fun MainActivity.SortStoragesDialog() {
    val act = this
    val list = remember { CustomStorages.load(prefs).toMutableList() }
    var version by remember { mutableStateOf(0) }
    @Suppress("UNUSED_VARIABLE")
    val v = version
    FairuAlertDialog(
        onDismissRequest = { showSortStoragesDialog = false },
        title = { Text(stringResource(R.string.sort)) },
        text = {
            Column {
                list.forEachIndexed { index, s ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            s.name + if (s.hidden) "（" + stringResource(R.string.hide) + "）" else "",
                            modifier = Modifier.weight(1f)
                        )
                        if (s.hidden) {
                            TextButton(onClick = {
                                list[index] = list[index].copy(hidden = false)
                                CustomStorages.save(prefs, list)
                                refreshCustomStorages()
                                android.widget.Toast.makeText(act, R.string.saved, android.widget.Toast.LENGTH_SHORT).show()
                                version++
                            }) { Text(stringResource(R.string.unhide)) }
                        }
                        IconButton(
                            enabled = index > 0,
                            onClick = {
                                if (index > 0) {
                                    val tmp = list[index - 1]
                                    list[index - 1] = list[index]
                                    list[index] = tmp
                                    CustomStorages.save(prefs, list)
                                    refreshCustomStorages()
                                    version++
                                }
                            }
                        ) {
                            Icon(painterResource(R.drawable.ic_arrow_up), contentDescription = stringResource(R.string.move_up))
                        }
                        IconButton(
                            enabled = index < list.size - 1,
                            onClick = {
                                if (index < list.size - 1) {
                                    val tmp = list[index + 1]
                                    list[index + 1] = list[index]
                                    list[index] = tmp
                                    CustomStorages.save(prefs, list)
                                    refreshCustomStorages()
                                    version++
                                }
                            }
                        ) {
                            Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = stringResource(R.string.move_down))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { showSortStoragesDialog = false }) { Text(stringResource(R.string.ok)) }
        }
    )
}
