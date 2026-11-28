package com.fairuman.filemanager

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 媒体页（v1.9.8）：顶栏 + 四个分类标签（选中项下面一条应用配色
 *  下划线，参考已安装应用页）+ 按日期分组的列表；长按进选择模式
 *  （参考图 3：×、已选择 N 项 / 总大小、分享、删除、三个点）。 */
@Composable
fun MediaActivity.MediaScreen() {
    val inSelection = selected.isNotEmpty()
    BackHandler {
        if (inSelection) selected = emptySet() else finish()
    }
    Column(modifier = Modifier.fillMaxSize()) {
        // ---------- 顶栏 ----------
        if (inSelection) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selected = emptySet() }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.cancel))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.selected_count, selected.size),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        FileUtils.formatSize(selectedTotalSize()),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { shareSelected() }) {
                    Icon(painterResource(R.drawable.ic_share), contentDescription = stringResource(R.string.share))
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(painterResource(R.drawable.ic_drawer_trash), contentDescription = stringResource(R.string.delete))
                }
                // v1.9.8：菜单包在触发按钮的 Box 里锚定，否则落到左下角（跟主列表 v1.9.7.2 同款修法）。
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = null)
                    }
                    MediaOverflowMenu()
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { finish() }) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                }
                Text(
                    stringResource(R.string.media_images_videos),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // v1.9.8：菜单包在触发按钮的 Box 里锚定，否则落到左下角（跟主列表 v1.9.7.2 同款修法）。
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = null)
                    }
                    MediaOverflowMenu()
                }
            }
        }

        // ---------- 分类标签 ----------
        Row(modifier = Modifier.fillMaxWidth()) {
            MediaTab(
                label = stringResource(R.string.media_images),
                selected = category == MediaActivity.CATEGORY_IMAGES,
                onClick = { category = MediaActivity.CATEGORY_IMAGES },
                modifier = Modifier.weight(1f)
            )
            MediaTab(
                label = stringResource(R.string.media_videos),
                selected = category == MediaActivity.CATEGORY_VIDEOS,
                onClick = { category = MediaActivity.CATEGORY_VIDEOS },
                modifier = Modifier.weight(1f)
            )
            MediaTab(
                label = stringResource(R.string.media_audios),
                selected = category == MediaActivity.CATEGORY_AUDIOS,
                onClick = { category = MediaActivity.CATEGORY_AUDIOS },
                modifier = Modifier.weight(1f)
            )
            MediaTab(
                label = stringResource(R.string.media_gifs),
                selected = category == MediaActivity.CATEGORY_GIFS,
                onClick = { category = MediaActivity.CATEGORY_GIFS },
                modifier = Modifier.weight(1f)
            )
        }

        // ---------- 内容 ----------
        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val groups = groupedItems()
            val flat = groups.flatMap { it.second }
            if (flat.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_files), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    for ((label, items) in groups) {
                        if (label.isNotEmpty()) {
                            item(key = "header_$label") {
                                Text(
                                    label,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                                )
                            }
                        }
                        items(items, key = { it.key }) { item ->
                            MediaRow(
                                item = item,
                                inSelection = inSelection,
                                checked = item.key in selected,
                                onClick = {
                                    if (inSelection) {
                                        selected = if (item.key in selected) selected - item.key else selected + item.key
                                    } else {
                                        openItem(item)
                                    }
                                },
                                onLongClick = {
                                    if (!inSelection) selected = setOf(item.key)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSortDialog) MediaSortDialog()
    if (showDeleteDialog) MediaDeleteDialog()
}

/** 右上角 ⋮ 菜单：正常态和选择模式共用一个。内容不变（正常态：排序 / 全选；
 *  选择模式：只有全选）。必须放在触发按钮的 Box 里，放在页面末尾会落到左下角。 */
@Composable
private fun MediaActivity.MediaOverflowMenu() {
    FairuDropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false }
    ) {
        if (selected.isEmpty()) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sort)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_sort), contentDescription = null) },
                onClick = { showMenu = false; showSortDialog = true }
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.select_all)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_check), contentDescription = null) },
            onClick = { showMenu = false; selectAllCurrent() }
        )
    }
}

/** 分类标签：四个并排居中文字，选中项下面一条应用配色下划线。 */
@Composable
private fun MediaTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        Box(
            modifier = Modifier.fillMaxWidth().height(3.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface
                )
        )
    }
}

/** 一行：圆角缩略图（失败显示类型图标）+ 文件名 + “大小 • 时间”
 *  （时间格式照主列表）；选择模式时右边是圆圈勾选。 */
@Composable
private fun MediaActivity.MediaRow(
    item: MediaActivity.MediaItem,
    inSelection: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(
                if (checked) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MediaThumb(item)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.name,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${if (item.size >= 0) FileUtils.formatSize(item.size) else stringResource(R.string.unknown)} • " +
                    if (item.date > 0) FileUtils.formatDate(item.date) else stringResource(R.string.unknown),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (inSelection) {
            Spacer(Modifier.width(8.dp))
            SelectCircle(checked = checked)
        }
    }
}

@Composable
private fun MediaActivity.MediaThumb(item: MediaActivity.MediaItem) {
    val bmp by produceState<Bitmap?>(initialValue = null, item.key) {
        value = withContext(Dispatchers.IO) { loadThumbnail(item) }
    }
    Box(
        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            androidx.compose.foundation.Image(
                bitmap = bmp!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                painterResource(
                    when {
                        item.isVideo -> R.drawable.ic_video
                        item.isAudio -> R.drawable.ic_audio
                        else -> R.drawable.ic_image
                    }
                ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/** 选择模式的圆圈勾选（参考图 3 右边的圆圈：选中是主色实心圆 + 白勾）。 */
@Composable
private fun SelectCircle(checked: Boolean) {
    Box(
        modifier = Modifier.size(24.dp)
            .border(
                2.dp,
                if (checked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                CircleShape
            )
            .background(
                if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** 排序对话框：复用主界面 SortDialog 的结构（5 个圆圈单选，两列布局，
 *  点确认才生效），去掉“仅用于文件夹”方框那一行。 */
@Composable
private fun MediaActivity.MediaSortDialog() {
    var selectedSort by remember { mutableStateOf(sort) }
    var reverseSel by remember { mutableStateOf(reverse) }
    val options = listOf(
        stringResource(R.string.sort_name),
        stringResource(R.string.sort_date),
        stringResource(R.string.sort_size),
        stringResource(R.string.sort_by_type),
        stringResource(R.string.sort_reverse)
    )
    val selected = if (reverseSel) 4 else selectedSort
    FairuAlertDialog(
        onDismissRequest = { showSortDialog = false },
        icon = { Icon(painterResource(R.drawable.ic_sort), contentDescription = null) },
        title = { Text(stringResource(R.string.sort)) },
        text = {
            Column {
                Row(Modifier.fillMaxWidth()) {
                    MediaSortRadioCell(
                        label = options[0],
                        selected = selected == 0,
                        onClick = { selectedSort = 0; reverseSel = false },
                        modifier = Modifier.weight(1f)
                    )
                    MediaSortRadioCell(
                        label = options[1],
                        selected = selected == 1,
                        onClick = { selectedSort = 1; reverseSel = false },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(Modifier.fillMaxWidth()) {
                    MediaSortRadioCell(
                        label = options[2],
                        selected = selected == 2,
                        onClick = { selectedSort = 2; reverseSel = false },
                        modifier = Modifier.weight(1f)
                    )
                    MediaSortRadioCell(
                        label = options[3],
                        selected = selected == 3,
                        onClick = { selectedSort = 3; reverseSel = false },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(Modifier.fillMaxWidth()) {
                    MediaSortRadioCell(
                        label = options[4],
                        selected = selected == 4,
                        onClick = { reverseSel = true },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                showSortDialog = false
                sort = selectedSort
                reverse = reverseSel
                getSharedPreferences("fairu_prefs", android.content.Context.MODE_PRIVATE).edit()
                    .putInt("media_sort", selectedSort)
                    .putBoolean("media_sort_reverse", reverseSel)
                    .apply()
            }) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = { showSortDialog = false }) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun MediaSortRadioCell(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

/** 删除确认框：样式跟主列表一致（标题删除 + 是否移入回收站 +
 *  大小 + “移动到回收站”复选框默认勾选）。 */
@Composable
private fun MediaActivity.MediaDeleteDialog() {
    var toTrash by remember { mutableStateOf(true) }
    val targets = selectedItems()
    val msg = if (targets.size == 1) stringResource(R.string.delete_to_trash_msg, targets[0].name)
    else stringResource(R.string.delete_to_trash_multi_msg, targets.size)
    val totalSize = targets.sumOf { if (it.size > 0) it.size else 0L }
    FairuAlertDialog(
        onDismissRequest = { showDeleteDialog = false },
        icon = { Icon(painterResource(R.drawable.ic_drawer_trash), contentDescription = null) },
        title = { Text(stringResource(R.string.delete)) },
        text = {
            Column {
                Text(msg, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "${stringResource(R.string.size)}  ${FileUtils.formatSize(totalSize)}",
                    fontSize = 15.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = toTrash, onCheckedChange = { toTrash = it })
                    Text(stringResource(R.string.move_to_trash))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                showDeleteDialog = false
                deleteSelected(toTrash)
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
        }
    )
}
