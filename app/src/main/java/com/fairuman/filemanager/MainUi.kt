package com.fairuman.filemanager

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------- 主界面（v1.9.8.1：Compose；View 的 activity_main 布局已删） ----------

@Composable
fun MainActivity.MainScreen() {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    // 抽屉开关与 Compose 的 DrawerState 双向同步（返回键关抽屉也走这里）。
    LaunchedEffect(drawerOpen) {
        if (drawerOpen) drawerState.open() else drawerState.close()
    }
    LaunchedEffect(drawerState.isOpen) {
        if (!drawerState.isOpen && drawerOpen) drawerOpen = false
    }
    // Snackbar：逻辑层调 showSnackbar 发消息，这里消费掉。
    val msg = snackbarMessage
    LaunchedEffect(msg) {
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            snackbarMessage = null
        }
    }
    // v1.9.7.5：液态玻璃已删，面板用不透明底色，不再模糊。

    ModalNavigationDrawer(
        drawerState = drawerState,
        // 注意：抽屉容器的颜色由 ModalDrawerSheet 提供，默认是不透明的
        // surface；不改的话内层背景设成透明也没用（全透明模式下抽屉还是白的）。
        drawerContent = {
            ModalDrawerSheet(
                // Miuix：抽屉底是浅灰，里面的分组是白色卡片。
                drawerContainerColor = if (isMiuixEngine()) MaterialTheme.colorScheme.background
                else MaterialTheme.colorScheme.surface,
                // v1.9.8.1：抽屉里没写颜色的文字跟字体颜色走。默认
                // contentColor 按容器底色算（浅底就是黑），抽屉标题
                // Fairumanējā 就还是黑色；直接指到主题 onSurface。
                drawerContentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(300.dp)
                ) {
                    FairuDrawer()
                }
            }
        },
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)
    ) {
        Scaffold(
            // v1.9.8.1：主界面里没写颜色的文字（行名、“..”）跟字体颜色走。
            // Scaffold 默认 contentColor 按容器底色算（浅底就是黑），
            // 行名就还是黑色；直接指到主题 onSurface（已是字体颜色）。
            contentColor = MaterialTheme.colorScheme.onSurface,
            topBar = { FairuTopBar() },
            bottomBar = { FairuBottomBar() },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding)
            ) {
                PaneColumn(left, Modifier.weight(1f))
                if (dualPane) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    PaneColumn(right, Modifier.weight(1f))
                }
            }
        }
    }

    // ---------- 对话框（状态驱动） ----------
    // v1.9.7.2：下拉菜单不再放根部——DropdownMenu 的弹窗按父布局锚定，
    // 放根部会落到左下角（Max 手机截图）。各菜单已移到触发按钮的 Box 里。
    deleteDialog?.let { DeleteDialog(it) }
    renameDialog?.let { RenameDialog(it) }
    if (showNewDialog) NewDialog()
    if (showJumpDialog) JumpDialog()
    if (showCreateHtmlDialog) CreateHtmlDialog()
    moreActionsDialog?.let { MoreActionsGridDialog(it) }
    shortcutDialog?.let { ShortcutDialog(it) }
    propertiesDialog?.let { PropertiesDialog(it) }
    if (showSortDialog) SortDialog()
    passwordDialog?.let { PasswordDialog(it) }
    storageRenameDialog?.let { StorageRenameDialog(it) }
    storageDeleteDialog?.let { StorageDeleteDialog(it) }
    if (showSortStoragesDialog) SortStoragesDialog()
}

// ---------- 顶栏（平时：标题+副标题+搜索+⋮；多选：✕ + 已选 N 项 + 分享/删除/⋮） ----------

@Composable
fun MainActivity.FairuTopBar() {
    val pane = activePane()
    val inSelection = pane.selected.isNotEmpty()
    val miuix = isMiuixEngine()
    // Miuix：浅灰底 + 深色字（参考图）；MD3：主色底 + 白字。
    val bg = if (miuix) MaterialTheme.colorScheme.background
    else MaterialTheme.colorScheme.primary
    val fg = if (miuix) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onPrimary
    val barModifier = Modifier
        .fillMaxWidth()
        .background(bg)
    if (inSelection) {
        Row(
            modifier = barModifier.padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { clearSelection(pane) }) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.cancel), tint = fg)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.selected_count, pane.selected.size),
                    color = fg,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    FileUtils.formatSize(selectionTotalSize(pane)),
                    color = fg.copy(alpha = 0.8f),
                    fontSize = 13.sp
                )
            }
            IconButton(onClick = { shareFiles(pane, pane.selected.toList()) }) {
                Icon(painterResource(R.drawable.ic_share), contentDescription = stringResource(R.string.share), tint = fg)
            }
            IconButton(onClick = { confirmDelete(pane.selected.toList()) }) {
                Icon(painterResource(R.drawable.ic_drawer_trash), contentDescription = stringResource(R.string.delete), tint = fg)
            }
            // v1.9.7.2：菜单包在触发按钮的 Box 里锚定，否则落到左下角。
            Box {
                IconButton(onClick = { showSelectionMenu = true }) {
                    Icon(painterResource(R.drawable.ic_more_vert), contentDescription = null, tint = fg)
                }
                if (showSelectionMenu) SelectionMenuDialog()
            }
        }
        return
    }
    if (showSearch) {
        Row(
            modifier = barModifier.padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                showSearch = false
                onSearchQueryChanged(pane, "")
            }) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null, tint = fg)
            }
            TextField(
                value = pane.searchQuery,
                // v1.9.7.8：敲键只改查询、不扫盘（扫描在后台线程 + 250ms 防抖，
                // 见 MainActivity.onSearchQueryChanged；之前这里同步调 loadPane，
                // 主线程递归扫整棵树，每敲一个字母就冻住一阵、输入法还丢字）。
                onValueChange = { onSearchQueryChanged(pane, it) },
                placeholder = { Text(stringResource(R.string.search_hint), color = fg.copy(alpha = 0.7f)) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = fg,
                    unfocusedTextColor = fg,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = fg
                ),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                onSearchQueryChanged(pane, "")
            }) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = null, tint = fg)
            }
        }
        return
    }
    Row(
        modifier = barModifier.padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { drawerOpen = true }) {
            Icon(painterResource(R.drawable.ic_menu), contentDescription = null, tint = fg)
        }
        // v1.9.8：点顶栏这行当前路径弹出“跳转”对话框。
        Column(Modifier.weight(1f).clickable { showJumpDialog = true }) {
            Text(
                // Miuix：大号粗黑标题，显示当前文件夹名（完整路径在下面的路径条里）。
                if (miuix) paneDisplayName(pane) else paneTitle(pane),
                color = fg,
                fontSize = if (miuix) 24.sp else 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                paneSubtitle(pane),
                color = fg.copy(alpha = 0.8f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { showSearch = true }) {
            Icon(painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.search_hint), tint = fg)
        }
        // v1.9.7.2：菜单包在触发按钮的 Box 里锚定，否则落到左下角。
        Box {
            IconButton(onClick = { showTopMenu = true }) {
                Icon(painterResource(R.drawable.ic_more_vert), contentDescription = null, tint = fg)
            }
            if (showTopMenu) TopMenuDialog()
        }
    }
}

/** 顶栏标题：SAF 用面包屑，否则当前目录绝对路径。 */
@Composable
fun MainActivity.paneTitle(pane: MainActivity.Pane): String {
    return if (pane.safMode) safBreadcrumb(pane) else pane.currentDir.absolutePath
}

/** Miuix 大标题：当前文件夹名（根目录显示“/”，内部储存显示“内部储存”，SAF 显示栈顶名）。 */
@Composable
fun MainActivity.paneDisplayName(pane: MainActivity.Pane): String {
    if (pane.safMode) {
        val last = pane.safStack.lastOrNull()?.name
        if (!last.isNullOrEmpty()) return last
        return safBreadcrumb(pane)
    }
    val dir = pane.currentDir
    if (dir.absolutePath == Environment.getExternalStorageDirectory().absolutePath) {
        return stringResource(R.string.storage_internal)
    }
    val name = dir.name
    return if (name.isEmpty()) dir.absolutePath else name
}

/** 顶栏副标题：文件夹数 / 文件数 + 已用 / 总容量。 */
@Composable
fun MainActivity.paneSubtitle(pane: MainActivity.Pane): String {
    val dirs = if (pane.shizukuMode) pane.shizukuEntries.count { it.isDir } else pane.files.count { it.isDirectory }
    val count = if (pane.shizukuMode) pane.shizukuEntries.count { !it.isDir } else pane.files.count { it.isFile }
    val root = Environment.getExternalStorageDirectory()
    val (total, free) = FileUtils.storageInfo(root)
    return stringResource(
        R.string.pane_stats, dirs, count,
        FileUtils.formatSize(total - free), FileUtils.formatSize(total)
    )
}

// ---------- 底栏（← → + ⇄ ↑；v1.9.0：+ 在中间） ----------

@Composable
fun MainActivity.FairuBottomBar() {
    val bg = MaterialTheme.colorScheme.surface
    // Miuix：图标用主色（跟随应用配色）；MD3：用 onSurface。
    val fg = if (isMiuixEngine()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { goBack() }) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null, tint = fg)
        }
        IconButton(onClick = { goForward() }) {
            Icon(painterResource(R.drawable.ic_arrow_forward), contentDescription = null, tint = fg)
        }
        IconButton(onClick = { showNewDialog = true }) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.new_title), tint = fg)
        }
        if (dualPane) {
            IconButton(onClick = { swapPanes() }) {
                Icon(painterResource(R.drawable.ic_swap), contentDescription = null, tint = fg)
            }
        }
        IconButton(onClick = { navigateUp(activePane()) }) {
            Icon(painterResource(R.drawable.ic_arrow_up), contentDescription = null, tint = fg)
        }
    }
}

// ---------- 单个窗格：路径条 + 文件列表 ----------

@Composable
fun MainActivity.PaneColumn(pane: MainActivity.Pane, modifier: Modifier = Modifier) {
    val miuix = isMiuixEngine()
    Column(modifier = modifier.fillMaxHeight().clickable { setActive(pane) }) {
        if (miuix) {
            // Miuix：路径条是灰色小字标签（参考图的“常规”分区标签），
            // 激活窗格用主色粗字。
            // v1.9.8：点路径条弹出“跳转”对话框（先激活本窗格）。
            Text(
                paneTitle(pane),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { setActive(pane); showJumpDialog = true }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                fontSize = 13.sp,
                fontWeight = if (pane.isActive) FontWeight.Bold else FontWeight.Normal,
                color = if (pane.isActive) MaterialTheme.colorScheme.primary
                else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            val pathBg = if (pane.isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
            Text(
                paneTitle(pane),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(pathBg)
                    // v1.9.8：点路径条弹出“跳转”对话框（先激活本窗格）。
                    .clickable { setActive(pane); showJumpDialog = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // Miuix：列表装在白色大圆角卡片里；MD3：保持原样。
        // 卡片边距 6dp + 行内边距 6dp：双窗格窄栏里副标题“日期 时间 大小”
        // 才能放得下（8dp+8dp 时实测副标题被截掉末尾的大小，层级 dump
        // 只报完整字符串、看不出截断，只能看截图像素确认）。
        val listModifier = if (miuix) Modifier
            .weight(1f)
            .padding(horizontal = 6.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
        else Modifier.weight(1f)
        Box(listModifier) {
            LazyColumn(Modifier.fillMaxSize()) {
                if (pane.showParent) {
                    item(key = "..") {
                        ParentRow(pane)
                    }
                }
                // v1.9.8.1：item 的 key 带上文件夹缓存大小。大小是异步到
                // 的，到了之后 key 一变，LazyColumn 把旧 item 槽整个丢掉
                // 重建——只重建行内容（行内 key()）不够：槽的旧绘制残留，
                // 回收站 79B 冷启动 45 秒、数分钟后、滚出滚回像素仍无大小，
                // 只有进选择模式/切单窗格才出现。文件不进缓存，key 不变。
                items(pane.files, key = { f ->
                    f.absolutePath + "|" + (pane.dirSizeCache[f.absolutePath]?.toString() ?: "")
                }) { f ->
                    PaneFileRow(pane, f)
                }
            }
            if (pane.showEmpty) {
                Text(
                    pane.emptyText,
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** “..”父目录行（v1.9.7.7 紧凑版：图标 24dp、上下内边距 4dp、名字 12sp）。 */
@Composable
fun MainActivity.ParentRow(pane: MainActivity.Pane) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { setActive(pane); navigateUp(pane) }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(if (isMiuixEngine()) 6.dp else 5.dp))
                .background(colorResource(R.color.tile_parent)),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(R.drawable.ic_folder), contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text("..", fontSize = if (isMiuixEngine()) 16.sp else 12.sp, lineHeight = if (isMiuixEngine()) 19.sp else 14.sp, fontWeight = if (isMiuixEngine()) FontWeight.Bold else FontWeight.Normal)
    }
}

/** 文件/文件夹行（v1.9.4 MP 样式；v1.9.7.7 照 MT 密度改紧凑：图标 24dp、
 * 上下内边距 MD3 4dp / Miuix 5dp、名字 MD3 12sp 行高 14sp、副标题 11sp 行高
 * 13sp、名字和副标题之间无多余行距；名字仍最多 5 行完整显示、副标题仍
 * 是“日期 + 大小”；⋮ 按钮缩到 32dp，否则 IconButton 的 48dp 最小触摸
 * 尺寸会把行高撑回去）。 */
@Composable
fun MainActivity.PaneFileRow(pane: MainActivity.Pane, f: File) {
    val isDir = pane.isDirOverride[f.absolutePath] ?: f.isDirectory
    val dateMs = pane.dateOverride[f.absolutePath] ?: f.lastModified()
    val date = if (dateMs == 0L) stringResource(R.string.unknown) else FileUtils.formatDateShort(dateMs)
    // 文件夹递归大小：后台算 + 缓存（v1.9.4 设计；先只显示日期，算好再刷新本行）。
    // v1.9.8.1：key 加 pane.loadSeq——loadPane 清缓存后同一路径复用旧组合时
    // LaunchedEffect 也会重跑，否则大小永久丢失（右窗格即此 bug）。
    if (isDir && !pane.dirShowsFolderLabel && !f.absolutePath.startsWith("/saf/")) {
        LaunchedEffect(f.absolutePath, pane.loadSeq) {
            if (!pane.dirSizeCache.containsKey(f.absolutePath)) {
                val size = withContext(Dispatchers.IO) { FileUtils.dirSizeOrNull(f) }
                // v1.9.8.1：失败也记 -1L 哨兵——否则缓存永无此项，副标题
                // 永远等不到，日期都出不来；sizeText 只认 >=0 的值。
                pane.dirSizeCache[f.absolutePath] = size ?: -1L
            }
        }
    }
    val sizeText: String? = when {
        dateMs == 0L -> null
        pane.dirShowsFolderLabel -> if (!isDir) pane.sizeOverride[f.absolutePath]?.let { FileUtils.formatSizeMp(it) } else null
        f.absolutePath.startsWith("/saf/") -> null
        isDir -> pane.dirSizeCache[f.absolutePath]?.takeIf { it >= 0L }?.let { FileUtils.formatSizeMp(it) }
        else -> if (f.exists()) FileUtils.formatSizeMp(f.length()) else null
    }
    val info = if (dateMs == 0L) stringResource(R.string.unknown) else date
    val isSel = pane.selected.contains(f)
    val inSelection = pane.selected.isNotEmpty()
    val iconRes = if (isDir) R.drawable.ic_folder else FileUtils.iconResFor(f)
    val tileRes = if (isDir) R.color.tile_folder else FileUtils.tileColorResFor(f)
    val isPinned = isDir && pane.pinnedKeys.contains(f.absolutePath)
    val miuix = isMiuixEngine()
    val subColor = if (miuix) MiuixTheme.colorScheme.onSurfaceVariantSummary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .combinedClickable(
                onClick = { setActive(pane); onFileClick(pane, f) },
                onLongClick = { setActive(pane); toggleSelect(pane, f) }
            )
            // v1.9.8.1：行内边距再收窄一点，给副标题留宽（Miuix 4dp→2dp、
            // MD3 12dp→8dp）；副标题本身另有 AutoFitSubtitle 兜底。
            // v1.9.7.7：照 MT 密度再收窄（MD3 8dp→4dp、Miuix 10dp→5dp）。
            .padding(horizontal = if (miuix) 2.dp else 8.dp, vertical = if (miuix) 5.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(if (miuix) 6.dp else 5.dp))
                .background(colorResource(tileRes)),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(iconRes), contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(if (miuix) 8.dp else 8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    f.name,
                    fontSize = if (miuix) 16.sp else 12.sp,
                    lineHeight = if (miuix) 19.sp else 14.sp,
                    fontWeight = if (miuix) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 5,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isPinned) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        painterResource(R.drawable.ic_pin),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            // v1.9.8.1：副标题等大小算好再画（文件夹先只画名字，大小一到
            // 再出整行“日期 时间 大小”；文件大小同步取、SAF/标签模式本来
            // 就只有日期、dateMs==0 只有“未知”，这些直接画，不等）。
            // 注意：之前“拆双 Text/行内 key/整行 key 三轮无效”不是重组
            // 残留，是宽度问题——见 AutoFitSubtitle 的注释。
            val subtitleReady = when {
                dateMs == 0L -> true
                pane.dirShowsFolderLabel -> true
                f.absolutePath.startsWith("/saf/") -> true
                isDir -> pane.dirSizeCache.containsKey(f.absolutePath)
                else -> true
            }
            if (subtitleReady) {
                val fullInfo = if (sizeText != null) "$info $sizeText" else info
                AutoFitSubtitle(fullInfo, subColor)
            }
        }
        if (inSelection) {
            // 多选模式：圆形勾选框（选中蓝底白勾，未选中灰环）。
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .border(
                        width = 2.dp,
                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSel) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        } else {
            // v1.9.7.2：菜单包在触发按钮的 Box 里锚定，否则落到左下角。
            // 只画属于这一行的菜单（pane 是同一对象、file 按路径相等）。
            // v1.9.7.7：不用 IconButton（48dp 最小触摸尺寸会把行高撑回
            // 去），改 32dp 圆形可点 Box，图标 18dp，菜单行为不变。
            Box {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { setActive(pane); fileMenu = FileMenuRequest(pane, f) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(painterResource(R.drawable.ic_more_vert), contentDescription = null, modifier = Modifier.size(18.dp))
                }
                fileMenu?.takeIf { it.pane === pane && it.file == f }?.let { FileMenuDialog(it) }
            }
        }
    }
}

/** 副标题单行自适应字号：双窗格窄栏里“日期 时间 大小”经常超出栏宽。
 * 实测真因不是重组/残留，是宽度——栏宽约 269px，完整串“26-11-22 00:46
 * 79B”约 285px、“200.69KB”约 367px，超出时大小 Text 整段不画（不是
 * 截断、也不是半个字）；“0B”约 267px 刚好放下所以能画；单窗格/选择模
 * 式栏宽够就正常。0B 能画、79B 画不出、单窗格能画、选择模式能画，
 * 五条证据都对得上。所以字号从 12sp 起按 0.5sp 下调直到放得下
 * （最低 7.5sp），保证大小一定可见；字号只在需要时变小。
 * v1.9.7.7：起始字号 12sp→11sp、行高固定 13sp，名字行（行高 14sp）和
 * 副标题之间不留多余空隙。 */
@Composable
fun AutoFitSubtitle(text: String, color: Color) {
    var fontSize by remember(text) { mutableStateOf(11.sp) }
    Text(
        text,
        fontSize = fontSize,
        lineHeight = 13.sp,
        color = color,
        maxLines = 1,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && fontSize.value > 7.5f) {
                fontSize = (fontSize.value - 0.5f).sp
            }
        }
    )
}

/** 分组卡片：Miuix 时用真 miuix 白色大圆角卡片；MD3 / M3 Expressive 时不包卡片（原样）。 */
@Composable
fun MiuixGroupCard(content: @Composable () -> Unit) {
    if (isMiuixEngine()) {
        FairuCard { content() }
    } else {
        Column { content() }
    }
}

// ---------- 抽屉（MT 式：根目录 / 内部储存 / 自定义储存 / 书签 / 工具组） ----------

@Composable
fun MainActivity.FairuDrawer() {
    val act = this
    val miuix = isMiuixEngine()
    // Miuix：抽屉底是浅灰，分组是白色卡片。
    val contentBg = if (miuix) MaterialTheme.colorScheme.background
    else MaterialTheme.colorScheme.surface
    Column(
        Modifier
            .fillMaxHeight()
            .background(contentBg)
            .padding(vertical = 8.dp)
    ) {
        // 抽屉头：应用名 + 版本。
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                stringResource(R.string.app_name),
                fontSize = if (miuix) 24.sp else 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                drawerVersionText(),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // 根目录 / 内部储存：Miuix 时装进一张白色大圆角卡片。
        MiuixGroupCard {
        // 根目录行（带用量条）+ 右上角 ⋮。
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DrawerRow(
                iconRes = R.drawable.ic_drawer_root,
                label = stringResource(R.string.root_directory),
                sub = drawerRootInfo,
                progress = drawerRootPct,
                modifier = Modifier.weight(1f),
                onClick = { drawerOpen = false; openRootViaShizuku() }
            )
            // v1.9.7.2：菜单包在触发按钮的 Box 里锚定，否则落到左下角
            //（Max 手机上“添加本地存储”卡片掉到左下角就是这个菜单）。
            Box {
                IconButton(onClick = { showDrawerOverflowMenu = true }) {
                    Icon(painterResource(R.drawable.ic_more_vert), contentDescription = null)
                }
                if (showDrawerOverflowMenu) DrawerOverflowMenuDialog()
            }
        }
        // 内部储存行（带用量条）。
        DrawerRow(
            iconRes = R.drawable.ic_drawer_storage,
            label = stringResource(R.string.storage_internal),
            sub = drawerInternalInfo,
            progress = drawerInternalPct,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            onClick = {
                drawerOpen = false
                navigateDrawerTo(Environment.getExternalStorageDirectory())
            }
        )
        }
        // 自定义储存。
        if (customStorageRows.isNotEmpty()) {
            FairuSectionLabel(stringResource(R.string.drawer_local))
            MiuixGroupCard {
            customStorageRows.forEach { row ->
                // v1.9.7.2：长按菜单包在行外面的 Box 里锚定，否则落到左下角。
                // 按储存 key 匹配（刷新后对象可能重建，引用相等不可靠）。
                Box(Modifier.fillMaxWidth()) {
                    DrawerRow(
                        iconRes = R.drawable.ic_drawer_storage,
                        label = row.storage.name,
                        sub = row.displayPath,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        onClick = {
                            drawerOpen = false
                            navigateDrawerToStorage(row.storage)
                        },
                        onLongClick = { storageMenu = StorageMenuRequest(row.storage) }
                    )
                    storageMenu?.takeIf { CustomStorages.key(it.storage) == CustomStorages.key(row.storage) }?.let { StorageMenuDialog(it) }
                }
            }
            }
        }
        // 书签。
        if (bookmarkRows.isNotEmpty()) {
            FairuSectionLabel(stringResource(R.string.bookmarks_title))
            MiuixGroupCard {
            bookmarkRows.forEach { b ->
                // v1.9.7.2：长按菜单包在行外面的 Box 里锚定，否则落到左下角。
                Box(Modifier.fillMaxWidth()) {
                    DrawerRow(
                        iconRes = R.drawable.ic_bookmark,
                        label = b.name,
                        sub = b.target,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        onClick = {
                            drawerOpen = false
                            openBookmark(b)
                        },
                        onLongClick = { bookmarkMenu = BookmarkMenuRequest(b) }
                    )
                    bookmarkMenu?.takeIf { it.bookmark == b }?.let { BookmarkMenuDialog(it) }
                }
            }
            }
        }
        // 工具组。
        FairuSectionLabel(stringResource(R.string.drawer_tools))
        MiuixGroupCard {
        DrawerRow(
            iconRes = R.drawable.ic_drawer_trash,
            label = stringResource(R.string.trash),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            onClick = {
                drawerOpen = false
                startActivity(android.content.Intent(act, TrashActivity::class.java))
            }
        )
        DrawerRow(
            iconRes = R.drawable.ic_text,
            label = stringResource(R.string.text_editor),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            onClick = {
                drawerOpen = false
                startActivity(android.content.Intent(act, TextEditorActivity::class.java))
            }
        )
        DrawerRow(
            iconRes = R.drawable.ic_image,
            label = stringResource(R.string.media_images_videos),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            onClick = {
                drawerOpen = false
                startActivity(android.content.Intent(act, MediaActivity::class.java).apply {
                    putExtra(MediaActivity.EXTRA_CATEGORY, MediaActivity.CATEGORY_IMAGES)
                })
            }
        )
        DrawerRow(
            iconRes = R.drawable.ic_drawer_settings,
            label = stringResource(R.string.settings),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            onClick = {
                drawerOpen = false
                startActivity(android.content.Intent(act, SettingsActivity::class.java))
            }
        )
        }
    }
}

@Composable
fun DrawerRow(
    iconRes: Int,
    label: String,
    sub: String? = null,
    progress: Int? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val miuixRow = isMiuixEngine()
            Text(
                label,
                fontSize = if (miuixRow) 16.sp else 15.sp,
                fontWeight = if (miuixRow) FontWeight.Bold else FontWeight.Normal
            )
            if (sub != null && sub.isNotEmpty()) {
                Text(
                    sub,
                    fontSize = if (miuixRow) 13.sp else 12.sp,
                    color = if (miuixRow) MiuixTheme.colorScheme.onSurfaceVariantSummary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (progress != null) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                )
            }
        }
    }
}
