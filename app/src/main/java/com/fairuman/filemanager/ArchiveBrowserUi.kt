package com.fairuman.filemanager

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/** 浏览压缩包页（v1.9.8）：标题是压缩包文件名；条目行点文件只弹
 *  属性 / 分享（文本项再加编辑文本）两个/三个按钮，不出现其他功能入口；
 *  点子文件夹进该前缀，返回键回上一级，到根再退出。 */
@Composable
fun ArchiveBrowserActivity.ArchiveBrowserScreen() {
    BackHandler {
        if (!navigateUp()) finish()
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (!navigateUp()) finish() }) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    titleName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (currentPrefix.isNotEmpty()) {
                    Text(
                        currentPrefix.trimEnd('/'),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }
        val children = currentChildren()
        if (children.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_files), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(children, key = { it.name }) { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                if (entry.isDir) {
                                    currentPrefix = entry.name
                                } else {
                                    actionEntry = entry
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painterResource(if (entry.isDir) R.drawable.ic_folder else R.drawable.ic_file),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                entry.name.trimEnd('/').substringAfterLast('/'),
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!entry.isDir) {
                                Text(
                                    if (entry.size >= 0) FileUtils.formatSize(entry.size)
                                    else stringResource(R.string.unknown),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 属性对话框（名称 / 大小 / 日期 / 压缩后大小）。
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

    // 条目操作对话框：只有属性 / 分享；文本项再加编辑文本。
    // 界面上绝不出现第三个（文本项：第四个）功能入口。
    actionEntry?.let { entry ->
        val isTextEntry = !entry.isDir && FileUtils.isText(File(entry.name))
        FairuAlertDialog(
            onDismissRequest = { actionEntry = null },
            title = { Text(entry.name.trimEnd('/').substringAfterLast('/')) },
            text = {
                Column {
                    TextButton(
                        onClick = { actionEntry = null; showPropertiesFor(entry) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.properties))
                    }
                    TextButton(
                        onClick = { actionEntry = null; shareEntry(entry) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.share))
                    }
                    if (isTextEntry) {
                        TextButton(
                            onClick = { actionEntry = null; editEntry(entry) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.edit_text))
                        }
                    }
                }
            },
            confirmButton = { },
            dismissButton = {
                TextButton(onClick = { actionEntry = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
