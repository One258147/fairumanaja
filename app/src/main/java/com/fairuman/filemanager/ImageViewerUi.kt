package com.fairuman.filemanager

import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Image
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ---------- 图像查看器界面（v1.9.8；样式走 FairuTheme，不自创样式） ----------
// 顶栏：返回 + 文件名 + 副标题“日期 宽x高” + 右上角背景切换 / 属性 / 排序
// （三个都是图标按钮，不要 ⋮；菜单用 FairuAlertDialog 圆圈单选 + 确认才切换，
// 与排序对话框 / 主题模式对话框同款流程）。
// 底栏：左下角方框（选择模式变蓝底对勾 + 已选数量）、中间“序号/总数”
// （总数就是手机全部图片数）、右下角删除 / 分享 / 其他打开方式
// （不要图1 里的铅笔，那是 MT 自带的，Max 没要）。
// 横向翻页 = 左右滑动一次一张；纵向排序 = 全部图片上下排成一列滚动。

/** 背景切换的三档颜色（默认黑色，记入 fairu_prefs，下次打开沿用）。 */
private fun viewerBgColor(mode: Int): Color = when (mode) {
    ImageViewerActivity.BG_WHITE -> Color.White
    ImageViewerActivity.BG_GRAY -> Color(0xFF808080)
    else -> Color.Black
}

/** 背景上图片加载失败 / 占位文字的颜色（黑底白字、白底黑字、灰底白字）。 */
private fun viewerBgTextColor(mode: Int): Color =
    if (mode == ImageViewerActivity.BG_WHITE) Color.Black else Color.White

@Composable
fun ImageViewerActivity.ImageViewerScreen() {
    BackHandler { finishWithResult() }
    val item = currentItem()
    val bg = viewerBgColor(bgMode)
    val inSelection = selected.isNotEmpty()
    // 顶栏 / 底栏跟应用配色走（Miuix 浅灰底深字，MD3 主色底白字，
    // 与 FairuPageTopBar 同款分支）；图片区用选中的背景色。
    val barBg = if (isMiuixEngine()) MiuixTheme.colorScheme.background
    else MaterialTheme.colorScheme.primary
    val barFg = if (isMiuixEngine()) MiuixTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onPrimary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isMiuixEngine()) MiuixTheme.colorScheme.background
            else MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        // ---------- 顶栏 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(barBg)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { finishWithResult() }) {
                Icon(
                    painterResource(R.drawable.ic_arrow_back),
                    contentDescription = null,
                    tint = barFg
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = item?.name ?: "",
                    color = barFg,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // 副标题“日期 宽x高”（图1：2026/9/9 256x256；这里日期用
                // 应用自己的 FileUtils.formatDate 格式，见汇报）。
                val sub = if (item != null) {
                    val dateText = if (item.date > 0) FileUtils.formatDate(item.date)
                    else stringResource(R.string.unknown)
                    val dimText = if (item.width > 0 && item.height > 0)
                        "${item.width}x${item.height}"
                    else stringResource(R.string.unknown)
                    "$dateText $dimText"
                } else ""
                Text(
                    text = sub,
                    color = barFg.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // 右上角从左到右：背景切换、属性、排序（都有图标，不要 ⋮）。
            IconButton(onClick = { showBgDialog = true }) {
                Icon(
                    painterResource(R.drawable.ic_palette),
                    contentDescription = null,
                    tint = fairuTopBarIconTint()
                )
            }
            IconButton(onClick = { item?.let { showPropertiesFor(it) } }) {
                Icon(
                    painterResource(R.drawable.ic_info),
                    contentDescription = null,
                    tint = fairuTopBarIconTint()
                )
            }
            IconButton(onClick = { showModeDialog = true }) {
                Icon(
                    painterResource(R.drawable.ic_sort),
                    contentDescription = null,
                    tint = fairuTopBarIconTint()
                )
            }
        }

        // ---------- 图片区 ----------
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(bg)
        ) {
            if (loading) {
                // 正在查 MediaStore：只显示背景色，不闪退、不弹空列表。
            } else if (images.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.error),
                        color = viewerBgTextColor(bgMode),
                        fontSize = 16.sp
                    )
                }
            } else if (viewMode == ImageViewerActivity.MODE_VERTICAL) {
                VerticalImageList(bgMode)
            } else {
                HorizontalImagePager(bgMode)
            }
        }

        // ---------- 底栏 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isMiuixEngine()) MiuixTheme.colorScheme.background
                    else MaterialTheme.colorScheme.surface
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左下角方框：正常态空方框；点一下进选择模式（当前图打勾，
            // 方框变蓝底对勾，旁边显示已选数量）；再点取消当前图的选择；
            // 选到 0 张自动回正常态（selected 为空即正常态）。
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(
                    enabled = item != null,
                    onClick = { toggleSelectCurrent() }
                )
            ) {
                val checked = item != null && isSelected(item)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .then(
                            if (checked) Modifier.background(MaterialTheme.colorScheme.primary)
                            else Modifier.border(
                                2.dp,
                                MaterialTheme.colorScheme.onSurfaceVariant,
                                RoundedCornerShape(4.dp)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (checked) {
                        Icon(
                            painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (inSelection) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${selected.size}",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            // 中间“当前序号/总数”（总数就是手机全部图片数）。
            Text(
                text = if (images.isEmpty()) "0/0" else "${currentIndex + 1}/${images.size}",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            // 右下角：删除、分享、其他打开方式（正常模式对当前图，
            // 选择模式对全部已选图片；不要铅笔）。
            val targets = actionTargets()
            IconButton(
                enabled = targets.isNotEmpty(),
                onClick = { requestDelete(targets) }
            ) {
                Icon(
                    painterResource(R.drawable.ic_drawer_trash),
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(
                enabled = targets.isNotEmpty(),
                onClick = { shareTargets(targets) }
            ) {
                Icon(
                    painterResource(R.drawable.ic_share),
                    contentDescription = stringResource(R.string.share),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(
                enabled = item != null,
                onClick = { item?.let { openWithTarget(it) } }
            ) {
                // “打开方式”行菜单里用的就是 ic_arrow_forward，这里沿用它。
                Icon(
                    painterResource(R.drawable.ic_arrow_forward),
                    contentDescription = stringResource(R.string.open_with),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    // ---------- 对话框（状态驱动，非空即显示，与主列表同款） ----------
    if (showBgDialog) BgDialog()
    if (showModeDialog) ViewModeDialog()
    propertiesMessage?.let { msg ->
        FairuAlertDialog(
            onDismissRequest = { propertiesMessage = null },
            title = { Text(stringResource(R.string.properties)) },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { propertiesMessage = null }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
    deleteRequest?.let { targets ->
        DeleteConfirmDialog(targets)
    }
}

/** 横向翻页：左右滑动一次一张（HorizontalPager，foundation 自带）。 */
@Composable
private fun ImageViewerActivity.HorizontalImagePager(bgMode: Int) {
    val pagerState = rememberPagerState(
        initialPage = currentIndex,
        pageCount = { images.size }
    )
    // 翻页 → 当前序号跟着变（底栏计数、顶栏标题都读 currentIndex）。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (page != currentIndex) currentIndex = page
        }
    }
    // 删图 / 切纵向回来后下标变了：把 pager 滑回当前页。
    LaunchedEffect(currentIndex, images.size) {
        if (images.isNotEmpty() && pagerState.currentPage != currentIndex) {
            pagerState.scrollToPage(currentIndex)
        }
    }
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        val item = images.getOrNull(page) ?: return@HorizontalPager
        ViewerImageBox(
            item = item,
            bgMode = bgMode,
            maxDim = ImageViewerActivity.MAX_DECODE_DIM,
            fillMaxSize = true,
            onTap = {
                // 选择模式下点图片切换该图的选中；正常模式点图片什么也不做。
                if (selected.isNotEmpty()) toggleSelect(item)
            }
        )
    }
}

/** 纵向排序：全部图片上下排成一列滚动（像图2）。 */
@Composable
private fun ImageViewerActivity.VerticalImageList(bgMode: Int) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex)
    // 滚动 → 当前序号取最上面可见的那张（底栏计数跟着走）。
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { idx ->
            if (idx != currentIndex) currentIndex = idx
        }
    }
    // 从横向切过来：滚到当前那张。
    LaunchedEffect(currentIndex) {
        // 只在模式切换那一刻滚一次（currentIndex 是滚动自己改的，
        // scrollToItem 在同一帧里是空操作，不会抖）。
        if (images.isNotEmpty() && listState.firstVisibleItemIndex != currentIndex) {
            listState.scrollToItem(currentIndex)
        }
    }
    val screenWidth = LocalConfiguration.current.screenWidthDp
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        items(images, key = { it.key }) { item ->
            ViewerImageBox(
                item = item,
                bgMode = bgMode,
                // 纵向按屏宽采样解码：省内存，上下滑不卡。
                maxDim = (screenWidth * 2).coerceAtLeast(720),
                fillMaxSize = false,
                onTap = {
                    if (selected.isNotEmpty()) toggleSelect(item)
                }
            )
        }
    }
}

/** 一张图的显示：异步采样解码；GIF 是动画 drawable，用 ImageView 播。 */
@Composable
private fun ImageViewerActivity.ViewerImageBox(
    item: ImageViewerActivity.ViewerImage,
    bgMode: Int,
    maxDim: Int,
    fillMaxSize: Boolean,
    onTap: () -> Unit,
) {
    val loaded by produceState<ImageViewerActivity.LoadedImage?>(
        initialValue = null,
        key1 = item
    ) {
        value = decodeImage(item, maxDim)
    }
    // 选中态：图片上盖一层半透明主色 + 右上角蓝底对勾（图3 主列表同款打勾）。
    val checked = isSelected(item)
    Box(
        modifier = Modifier
            .then(if (fillMaxSize) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
            .then(
                if (!fillMaxSize && item.width > 0 && item.height > 0)
                    Modifier.aspectRatio(item.width.toFloat() / item.height.toFloat())
                else Modifier
            )
            .clickable { onTap() },
        contentAlignment = Alignment.Center
    ) {
        when (val img = loaded) {
            is ImageViewerActivity.LoadedImage.Static -> {
                Image(
                    bitmap = img.bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    // 横向整页填满；纵向按图本身宽高比排，未知尺寸时按原图宽排。
                    modifier = if (fillMaxSize) Modifier.fillMaxSize()
                    else Modifier.fillMaxWidth()
                )
            }
            is ImageViewerActivity.LoadedImage.Animated -> {
                val drawable = img.drawable
                AndroidView(
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            adjustViewBounds = true
                            scaleType = ImageView.ScaleType.FIT_CENTER
                        }
                    },
                    update = { view ->
                        view.setImageDrawable(drawable)
                        (drawable as? AnimatedImageDrawable)?.start()
                    },
                    modifier = if (fillMaxSize) Modifier.fillMaxSize()
                    else Modifier.fillMaxWidth()
                )
                DisposableEffect(drawable) {
                    onDispose { (drawable as? AnimatedImageDrawable)?.stop() }
                }
            }
            ImageViewerActivity.LoadedImage.Failed -> {
                Text(
                    stringResource(R.string.error),
                    color = viewerBgTextColor(bgMode),
                    fontSize = 16.sp
                )
            }
            null -> {
                // 还在解码：只显示背景色，不放占位图。
            }
        }
        if (checked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** 背景切换菜单：黑色 / 白色 / 灰色（圆圈单选，默认黑色，点确认才切换）。 */
@Composable
private fun ImageViewerActivity.BgDialog() {
    var pending by remember { mutableStateOf(bgMode) }
    val options = listOf(
        stringResource(R.string.viewer_bg_black),
        stringResource(R.string.viewer_bg_white),
        stringResource(R.string.viewer_bg_gray)
    )
    FairuAlertDialog(
        onDismissRequest = { showBgDialog = false },
        icon = { Icon(painterResource(R.drawable.ic_palette), contentDescription = null) },
        title = { Text(stringResource(R.string.viewer_background)) },
        text = {
            Column {
                options.forEachIndexed { i, label ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pending = i }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = pending == i, onClick = { pending = i })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                showBgDialog = false
                saveBgMode(pending)
            }) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = { showBgDialog = false }) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/** 排序菜单：横向翻页 / 纵向排序（圆圈单选，默认横向翻页，点确认才切换）。 */
@Composable
private fun ImageViewerActivity.ViewModeDialog() {
    var pending by remember { mutableStateOf(viewMode) }
    val options = listOf(
        stringResource(R.string.viewer_view_horizontal),
        stringResource(R.string.viewer_view_vertical)
    )
    FairuAlertDialog(
        onDismissRequest = { showModeDialog = false },
        icon = { Icon(painterResource(R.drawable.ic_sort), contentDescription = null) },
        title = { Text(stringResource(R.string.sort)) },
        text = {
            Column {
                options.forEachIndexed { i, label ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pending = i }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = pending == i, onClick = { pending = i })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                showModeDialog = false
                saveViewMode(pending)
            }) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = { showModeDialog = false }) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/** 删除确认框：与主列表删除对话框同款（默认进回收站，可取消勾选永久删除）。 */
@Composable
private fun ImageViewerActivity.DeleteConfirmDialog(targets: List<ImageViewerActivity.ViewerImage>) {
    var toTrash by remember { mutableStateOf(true) }
    val totalSize = targets.filter { it.size >= 0 }.sumOf { it.size }
    val msg = if (targets.size == 1) stringResource(R.string.delete_to_trash_msg, targets[0].name)
    else stringResource(R.string.delete_to_trash_multi_msg, targets.size)
    FairuAlertDialog(
        onDismissRequest = { deleteRequest = null },
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
                deleteRequest = null
                doDelete(targets, toTrash)
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = { deleteRequest = null }) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
