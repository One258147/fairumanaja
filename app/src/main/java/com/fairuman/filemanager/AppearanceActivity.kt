package com.fairuman.filemanager

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 外观页（v1.9.8.1 Compose）：大标题“外观”+ 一张卡片，四行：
 * 主题模式（点行弹对话框菜单，三个单选：跟随系统/浅色/深色，
 * 选后点确认才切换，取消不切换；默认跟随系统）、动态颜色（开关，
 * 打开先弹确认框，确认才开；任何版本都能开）、应用配色（点开 52 色网格，
 * 点确认才保存；动态颜色开着时这一行变灰、点不了，副标题提示先关动态颜色）、
 * 字体颜色（52 色按基础/浅/深三组，点确认才保存）。
 */
class AppearanceActivity : BaseActivity() {

    companion object {
        const val PREF_DYNAMIC_COLOR = "dynamic_color"
        const val PREF_SEED_COLOR = "seed_color"
        const val DEFAULT_SEED_COLOR = 0xFF0D47A1.toInt()

        /** 52 个种子色（顺序固定，与任务约定一致）：前 25 不动，后面补到 52。 */
        val SEED_COLORS = listOf(
            0xFF0D47A1.toInt(), 0xFFB3261E.toInt(), 0xFFC2185B.toInt(), 0xFF7B1FA2.toInt(),
            0xFF512DA8.toInt(), 0xFF303F9F.toInt(), 0xFF1976D2.toInt(), 0xFF0288D1.toInt(),
            0xFF0097A7.toInt(), 0xFF00796B.toInt(), 0xFF388E3C.toInt(), 0xFF689F38.toInt(),
            0xFFAFB42B.toInt(), 0xFFF9A825.toInt(), 0xFFFF8F00.toInt(), 0xFFF57C00.toInt(),
            0xFFE64A19.toInt(), 0xFF5D4037.toInt(), 0xFF795548.toInt(), 0xFF546E7A.toInt(),
            0xFF6A1B9A.toInt(), 0xFFAD1457.toInt(), 0xFF00897B.toInt(), 0xFF7CB342.toInt(),
            0xFF33691E.toInt(), 0xFF00BCD4.toInt(), 0xFF009688.toInt(), 0xFFFFFFFF.toInt(),
            0xFF000000.toInt(), 0xFF9E9E9E.toInt(), 0xFFF8BBD0.toInt(), 0xFFE91E63.toInt(),
            0xFF616161.toInt(), 0xFF90CAF9.toInt(), 0xFFA5D6A7.toInt(), 0xFFFFF59D.toInt(),
            0xFFCE93D8.toInt(), 0xFFFF9800.toInt(), 0xFF1A237E.toInt(), 0xFF1B5E20.toInt(),
            0xFF4E342E.toInt(), 0xFFFFC107.toInt(), 0xFFF44336.toInt(), 0xFFEC407A.toInt(),
            0xFF9C27B0.toInt(), 0xFF3F51B5.toInt(), 0xFF03A9F4.toInt(), 0xFFCDDC39.toInt(),
            0xFFFFB300.toInt(), 0xFF8D6E63.toInt(), 0xFF607D8B.toInt(), 0xFF00695C.toInt()
        )
    }

    private lateinit var prefs: SharedPreferences
    private var showThemeDialog by mutableStateOf(false)
    private var pendingThemeMode by mutableStateOf(0)
    private var themeMode by mutableStateOf(0)
    private var dynamicOn by mutableStateOf(false)
    private var showDynamicConfirm by mutableStateOf(false)
    private var showColorDialog by mutableStateOf(false)
    private var showFontDialog by mutableStateOf(false)
    private var seedColor by mutableStateOf(DEFAULT_SEED_COLOR)
    private var fontColor by mutableStateOf(MainActivity.DEFAULT_FONT_COLOR)

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashHandler.install(this)
        super.onCreate(savedInstanceState)
        try {
            prefs = getSharedPreferences("fairu_prefs", MODE_PRIVATE)
            themeMode = prefs.getInt(MainActivity.PREF_THEME_MODE, 0)
            dynamicOn = prefs.getBoolean(PREF_DYNAMIC_COLOR, false)
            seedColor = prefs.getInt(PREF_SEED_COLOR, DEFAULT_SEED_COLOR)
            fontColor = prefs.getInt(MainActivity.PREF_FONT_COLOR, MainActivity.DEFAULT_FONT_COLOR)
            setContent {
                FairuTheme { AppearanceScreen() }
            }
        } catch (e: Throwable) {
            CrashHandler.writeLog(this, e)
            try { finish() } catch (_: Exception) { }
        }
    }

    private fun applyThemeMode(which: Int) {
        themeMode = which
        prefs.edit().putInt(MainActivity.PREF_THEME_MODE, which).apply()
        AppCompatDelegate.setDefaultNightMode(
            when (which) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
        recreate()
    }

    @Composable
    private fun AppearanceScreen() {
        val scrollState = rememberScrollState()
        Column(Modifier.fillMaxSize().background(fairuPageBackground())) {
            FairuPageTopBar(title = stringResource(R.string.appearance), onBack = { finish() })
            Column(Modifier.verticalScroll(scrollState)) {
                FairuCard {
                    // 主题模式：点行弹对话框菜单，选后点确认才切换。
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pendingThemeMode = themeMode; showThemeDialog = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_theme),
                            contentDescription = null,
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.theme_mode), fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Text(
                                when (themeMode) {
                                    1 -> stringResource(R.string.theme_light)
                                    2 -> stringResource(R.string.theme_dark)
                                    else -> stringResource(R.string.theme_system)
                                },
                                fontSize = 13.sp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // v1.9.7.2：标准解释小字。
                            Text(
                                stringResource(R.string.theme_mode_hint),
                                fontSize = 12.sp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            painterResource(R.drawable.ic_arrow_forward),
                            contentDescription = null,
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    // 动态颜色：开关，打开先弹确认框。
                    FairuSwitchRow(
                        iconRes = R.drawable.ic_image,
                        title = stringResource(R.string.dynamic_color),
                        // v1.9.7.2：标准解释小字。
                        sub = stringResource(R.string.dynamic_color_hint),
                        checked = dynamicOn,
                        onToggle = {
                            if (!dynamicOn) {
                                showDynamicConfirm = true
                            } else {
                                dynamicOn = false
                                prefs.edit().putBoolean(PREF_DYNAMIC_COLOR, false).apply()
                                recreate()
                            }
                        }
                    )
                    // 应用配色：动态颜色开着时变灰、点不了。
                    val colorRowEnabled = !dynamicOn
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (colorRowEnabled) Modifier.clickable { showColorDialog = true } else Modifier)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_palette),
                            contentDescription = null,
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.app_color), fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Text(
                                if (dynamicOn) stringResource(R.string.app_color_disabled_hint)
                                else "#%06X".format(0xFFFFFF and seedColor),
                                fontSize = 13.sp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // v1.9.7.2：标准解释小字。
                            Text(
                                stringResource(R.string.app_color_hint),
                                fontSize = 12.sp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (colorRowEnabled) {
                            Icon(
                                painterResource(R.drawable.ic_arrow_forward),
                                contentDescription = null,
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    // 字体颜色。
                    FairuRow(
                        iconRes = R.drawable.ic_font_color,
                        title = stringResource(R.string.font_color),
                        sub = "#%06X".format(0xFFFFFF and fontColor),
                        // v1.9.7.2：标准解释小字。
                        hint = stringResource(R.string.font_color_hint),
                        onClick = { showFontDialog = true }
                    )
                }
            }
        }
        // 动态颜色确认框。
        if (showDynamicConfirm) {
            FairuAlertDialog(
                onDismissRequest = { showDynamicConfirm = false },
                icon = { Icon(painterResource(R.drawable.ic_image), contentDescription = null) },
                title = { Text(stringResource(R.string.dynamic_color_confirm_title)) },
                text = { Text(stringResource(R.string.dynamic_color_confirm_msg)) },
                confirmButton = {
                    TextButton(onClick = {
                        showDynamicConfirm = false
                        dynamicOn = true
                        prefs.edit().putBoolean(PREF_DYNAMIC_COLOR, true).apply()
                        recreate()
                    }) { Text(stringResource(R.string.confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDynamicConfirm = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
        // 主题模式菜单：对话框式，三个单选，选后点确认才切换，取消不切换。
        // 打开时默认选中当前模式；全新安装默认是跟随系统（0）。
        if (showThemeDialog) {
            FairuAlertDialog(
                onDismissRequest = { showThemeDialog = false },
                icon = { Icon(painterResource(R.drawable.ic_theme), contentDescription = null) },
                title = { Text(stringResource(R.string.theme_mode)) },
                text = {
                    Column {
                        listOf(
                            0 to stringResource(R.string.theme_system),
                            1 to stringResource(R.string.theme_light),
                            2 to stringResource(R.string.theme_dark)
                        ).forEach { (mode, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { pendingThemeMode = mode }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = pendingThemeMode == mode, onClick = { pendingThemeMode = mode })
                                Text(label, fontSize = 16.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showThemeDialog = false
                        if (pendingThemeMode != themeMode) applyThemeMode(pendingThemeMode)
                    }) { Text(stringResource(R.string.confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showThemeDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
        if (showColorDialog) ColorPickerDialog(
            title = stringResource(R.string.app_color),
            colors = SEED_COLORS,
            current = seedColor,
            onDismiss = { showColorDialog = false },
            onConfirm = { color ->
                showColorDialog = false
                seedColor = color
                prefs.edit().putInt(PREF_SEED_COLOR, color).apply()
                recreate()
            }
        )
        if (showFontDialog) FontColorDialog(
            current = fontColor,
            onDismiss = { showFontDialog = false },
            onConfirm = { color ->
                showFontDialog = false
                fontColor = color
                prefs.edit().putInt(MainActivity.PREF_FONT_COLOR, color).apply()
                recreate()
            }
        )
    }

    /** 应用配色对话框：5 列网格 52 个色块，当前选中的叠加白色对勾；点确认才保存。 */
    @Composable
    private fun ColorPickerDialog(
        title: String,
        colors: List<Int>,
        current: Int,
        onDismiss: () -> Unit,
        onConfirm: (Int) -> Unit
    ) {
        var selected by androidx.compose.runtime.remember {
            mutableStateOf(colors.indexOf(current).takeIf { it >= 0 } ?: 0)
        }
        FairuAlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(painterResource(R.drawable.ic_palette), contentDescription = null) },
            title = { Text(title) },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(colors) { index, color ->
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(color))
                                .clickable { selected = index }
                                .border(
                                    width = if (index == selected) 2.dp else 0.dp,
                                    color = if (index == selected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (index == selected) {
                                Icon(
                                    painterResource(R.drawable.ic_check),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onConfirm(colors[selected]) }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    /** 字体颜色对话框：52 色按基础/浅/深三组列出；点确认才保存。默认黑色。 */
    @Composable
    private fun FontColorDialog(
        current: Int,
        onDismiss: () -> Unit,
        onConfirm: (Int) -> Unit
    ) {
        var selected by androidx.compose.runtime.remember {
            mutableStateOf(MainActivity.FONT_COLORS.indexOf(current).takeIf { it >= 0 } ?: 0)
        }
        val groups = listOf(
            0 until 20 to stringResource(R.string.font_color_group_base),
            20 until 36 to stringResource(R.string.font_color_group_light),
            36 until 52 to stringResource(R.string.font_color_group_dark)
        )
        FairuAlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(painterResource(R.drawable.ic_font_color), contentDescription = null) },
            title = { Text(stringResource(R.string.font_color)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    groups.forEach { (range, label) ->
                        Text(
                            label,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(5),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(((range.count() + 4) / 5 * 52).dp)
                        ) {
                            itemsIndexed(MainActivity.FONT_COLORS.slice(range)) { i, color ->
                                val index = range.first + i
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color(color))
                                        .clickable { selected = index },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (index == selected) {
                                        Icon(
                                            painterResource(R.drawable.ic_check),
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onConfirm(MainActivity.FONT_COLORS[selected]) }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
