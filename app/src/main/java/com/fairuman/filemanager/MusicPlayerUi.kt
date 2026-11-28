package com.fairuman.filemanager

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ---------- 内置音乐播放器界面（v1.9.8；照 VideoPlayerUi 那套，样式走 FairuTheme） ----------
// 打开自动播放；顶栏：返回 + 文件名 + 副标题“时长 · 格式”；
// 中央区：大音乐图标（没有画面——点视频文件进来也只出声）；
// 单击切换控制栏显隐，双击暂停/继续播（切时中间短暂显示播放/暂停大图标）；
// 底栏两行：第一行进度条（左当前时间、中间圆点滑块、右总时长），
// 第二行控制行（上一个、快退10秒、播放/暂停居中加大、快进10秒、下一个；
// 上一个/下一个切同文件夹音频，到头/到尾置灰，拿不到列表也置灰）；
// ⋮ 菜单：循环播放（开关）+ 调速（弹六档对话框）。
// 调速：圆圈单选对话框，0.5x–2x 六档，默认 1x，点确认才生效
// （与视频播放器同款流程），倍速不持久化。

@Composable
fun MusicPlayerActivity.MusicPlayerScreen() {
    BackHandler { finish() }
    val barBg = if (isMiuixEngine()) MiuixTheme.colorScheme.background
    else MaterialTheme.colorScheme.primary
    val barFg = if (isMiuixEngine()) MiuixTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onPrimary

    // 播放中每 250ms 刷新一次进度（暂停时不刷，拖进度条时也不刷）。
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            val p = player
            if (p != null) {
                positionMs = p.currentPosition.coerceAtLeast(0L)
                val d = p.duration
                durationMs = if (d > 0) d else 0L
            }
            delay(250)
        }
    }
    // 双击后闪一下状态图标，700ms 后消失。
    val flash = flashIcon
    if (flash != null) {
        LaunchedEffect(flash) {
            delay(700)
            flashIcon = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ---------- 顶栏（控制栏显隐跟着它） ----------
        if (controlsVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(barBg)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { finish() }) {
                    Icon(
                        painterResource(R.drawable.ic_arrow_back),
                        contentDescription = null,
                        tint = barFg
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = fileName,
                        color = barFg,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // 副标题“时长 · 格式”（时长在准备好后才有，没有就只显示格式）。
                    val durText = if (durationMs > 0) formatTime(durationMs) else ""
                    val formatText = FileUtils.extOfName(fileName).uppercase()
                    val sub = listOf(durText, formatText).filter { it.isNotBlank() }
                        .joinToString(" · ")
                    if (sub.isNotBlank()) {
                        Text(
                            text = sub,
                            color = barFg.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // 右上角从左到右：分享、属性、⋮（都有图标）。
                IconButton(onClick = { shareMusic() }) {
                    Icon(
                        painterResource(R.drawable.ic_share),
                        contentDescription = stringResource(R.string.share),
                        tint = barFg
                    )
                }
                IconButton(onClick = { showProperties() }) {
                    Icon(
                        painterResource(R.drawable.ic_info),
                        contentDescription = stringResource(R.string.properties),
                        tint = barFg
                    )
                }
                Box {
                    IconButton(onClick = { showOptionsMenu = true }) {
                        Icon(
                            painterResource(R.drawable.ic_more_vert),
                            contentDescription = null,
                            tint = barFg
                        )
                    }
                    if (showOptionsMenu) {
                        FairuDropdownMenu(
                            expanded = true,
                            onDismissRequest = { showOptionsMenu = false }
                        ) {
                            MusicOptionRow(
                                label = stringResource(R.string.video_loop),
                                checked = loopPlay,
                                onToggle = { toggleLoop() }
                            )
                            // 调速（点后关菜单、弹六档调速对话框）。
                            MusicSpeedRow(
                                label = stringResource(R.string.video_speed),
                                onClick = {
                                    showOptionsMenu = false
                                    showSpeedDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }

        // ---------- 中央区：大音乐图标（没有视频面） ----------
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { togglePlayPause() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(R.drawable.ic_audio),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(120.dp)
            )
            // 双击后短暂显示的大图标（暂停变 ‖、继续变 ▶）。
            if (flash != null) {
                Icon(
                    painterResource(
                        if (flash == "pause") R.drawable.ic_pause else R.drawable.ic_play
                    ),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(72.dp)
                )
            }
        }

        // ---------- 底栏（控制栏显隐跟着它）：两行 ----------
        if (controlsVisible) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(barBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                // 第一行：进度条——左当前时间、中间滑块（圆点）、右总时长。
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 当前时间。
                    Text(
                        text = formatTime(positionMs),
                        color = barFg,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    // 进度条（拖动时按住的那一点显示，不跟播放进度打架）。
                    var dragPos by remember { mutableStateOf<Float?>(null) }
                    val shownPos = dragPos ?: positionMs.toFloat()
                    Slider(
                        value = if (durationMs > 0) (shownPos / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                        onValueChange = { frac ->
                            dragPos = frac * durationMs.toFloat()
                        },
                        onValueChangeFinished = {
                            dragPos?.let { seekTo(it.toLong()) }
                            dragPos = null
                        },
                        // 底栏背景就是 primary 色，默认 Slider 的已播轨道/滑块也是 primary，
                        // 会跟背景融成一片看不见；改成前景色（白）+ 半透明未播轨道。
                        colors = SliderDefaults.colors(
                            thumbColor = barFg,
                            activeTrackColor = barFg,
                            inactiveTrackColor = barFg.copy(alpha = 0.4f)
                        ),
                        // 圆点滑块：默认滑块在模拟器上会画成竖直白条，改成明确的圆点。
                        thumb = { _ ->
                            Box(
                                Modifier
                                    .size(20.dp)
                                    .background(barFg, CircleShape)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    // 总时长。
                    Text(
                        text = formatTime(durationMs),
                        color = barFg,
                        fontSize = 13.sp
                    )
                }
                // 第二行：控制行——上一个、快退10秒、播放/暂停（居中、比别的图标大）、
                // 快进10秒、下一个（到头/到尾、或拿不到列表时对应按钮置灰）。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 上一个（同文件夹上一首音频，到第一首时置灰）。
                    IconButton(
                        onClick = { switchToAudio(audioIndex - 1) },
                        enabled = hasPrev
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_skip_previous),
                            contentDescription = null,
                            tint = if (hasPrev) barFg else barFg.copy(alpha = 0.35f)
                        )
                    }
                    // 快退 10 秒。
                    IconButton(onClick = { seekTo(positionMs - 10_000) }) {
                        Icon(
                            painterResource(R.drawable.ic_fast_rewind),
                            contentDescription = null,
                            tint = barFg
                        )
                    }
                    // 播放 / 暂停（居中、比别的图标大）。
                    IconButton(
                        onClick = { togglePlayPause() },
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            painterResource(
                                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                            ),
                            contentDescription = stringResource(
                                if (isPlaying) R.string.video_pause else R.string.video_play
                            ),
                            tint = barFg,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    // 快进 10 秒。
                    IconButton(onClick = { seekTo(positionMs + 10_000) }) {
                        Icon(
                            painterResource(R.drawable.ic_fast_forward),
                            contentDescription = null,
                            tint = barFg
                        )
                    }
                    // 下一个（同文件夹下一首音频，到最后一首时置灰）。
                    IconButton(
                        onClick = { switchToAudio(audioIndex + 1) },
                        enabled = hasNext
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_skip_next),
                            contentDescription = null,
                            tint = if (hasNext) barFg else barFg.copy(alpha = 0.35f)
                        )
                    }
                }
            }
        }
    }

    if (showSpeedDialog) MusicSpeedDialog()

    // 属性对话框（照查看器 propertiesMessage 的做法）。
    propertiesMessage?.let { msg ->
        FairuAlertDialog(
            onDismissRequest = { propertiesMessage = null },
            title = { Text(stringResource(R.string.properties)) },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { propertiesMessage = null }) {
                    Text(stringResource(R.string.confirm))
                }
            }
        )
    }
}

/** ⋮ 菜单里的一行：左边文字、右边开关，点开关立即生效（菜单不关）。 */
@Composable
private fun MusicOptionRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontSize = 15.sp
        )
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

/** ⋮ 菜单第二行：调速（点后关菜单、弹六档调速对话框）。 */
@Composable
private fun MusicSpeedRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontSize = 15.sp
        )
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = LocalContentColor.current
        )
    }
}

/** 倍速显示：“1x”“0.5x”这种（0.75x 不丢小数）。 */
private fun musicSpeedLabel(rate: Float): String {
    val s = if (rate == rate.toInt().toFloat()) rate.toInt().toString()
    else rate.toString()
    return "${s}x"
}

/** 调速菜单：0.5x–2x 六档（圆圈单选，默认 1x，点确认才生效）。 */
@Composable
private fun MusicPlayerActivity.MusicSpeedDialog() {
    var pending by remember { mutableStateOf(speed) }
    val options = MusicPlayerActivity.SPEEDS
    FairuAlertDialog(
        onDismissRequest = { showSpeedDialog = false },
        title = { Text(stringResource(R.string.video_speed)) },
        text = {
            Column {
                options.forEach { rate ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pending = rate }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = pending == rate,
                            onClick = { pending = rate }
                        )
                        Text(musicSpeedLabel(rate))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                showSpeedDialog = false
                applySpeed(pending)
            }) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = { showSpeedDialog = false }) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
