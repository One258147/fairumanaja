package com.fairuman.filemanager

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource

/**
 * 设置页（v1.9.7.8 改版：两组分组——语言与外观 / 应用；分组小标签主色、
 * 白色大圆角卡片、行首图标放浅色圆圈里、卡片内行间细分隔线；只改样式布局，
 * 5 行一个不少、顺序不变、每行的标题/副标题/行为都不变）。
 * 多语言设置（点进单独一页选，右上角 ✓ 确认才切换）、外观（点进外观页）、
 * 开屏动画（开关，默认开）、关于（进单独的关于页）、实验性功能
 * （点进之前先弹警告框，确认才进，取消不进）。
 */
/** 卡片内行与行之间的细分隔线（v1.9.7.8 参考图样式；左端对齐文字起始处）。 */
@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

class SettingsActivity : BaseActivity() {

    private lateinit var prefs: SharedPreferences
    private var showWarning by mutableStateOf(false)
    private var showResetDialog by mutableStateOf(false)

    /** v1.9.8：一键恢复默认设置。只动 fairu_prefs 里的 10 项偏好，不删任何数据
     * （书签、置顶文件夹、自定义储存、回收站、文本编辑器偏好一律不动）。 */
    private fun resetToDefaults() {
        prefs.edit()
            .remove(MainActivity.PREF_LOCALE)
            .putInt(MainActivity.PREF_THEME_MODE, 0)
            .putBoolean(MainActivity.PREF_DYNAMIC_COLOR, false)
            .putInt(MainActivity.PREF_SEED_COLOR, MainActivity.DEFAULT_SEED)
            .putInt(MainActivity.PREF_FONT_COLOR, MainActivity.DEFAULT_FONT_COLOR)
            .putInt(MainActivity.PREF_UI_ENGINE, MainActivity.UI_ENGINE_MD3)
            .putBoolean(MainActivity.PREF_SPLASH, true)
            .putInt("sort", 0)
            .putBoolean("sort_reverse", false)
            .putBoolean("sort_type_folders_only", false)
            .putBoolean("dual_pane", true)
            .putBoolean("show_hidden", false)
            .apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        MainActivity.applyPrefs(this)
        recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashHandler.install(this)
        super.onCreate(savedInstanceState)
        try {
            prefs = getSharedPreferences("fairu_prefs", MODE_PRIVATE)
            setContent {
                FairuTheme { SettingsScreen() }
            }
        } catch (e: Throwable) {
            CrashHandler.writeLog(this, e)
            try { finish() } catch (_: Exception) { }
        }
    }

    @Composable
    private fun SettingsScreen() {
        val scrollState = rememberScrollState()
        var splashOn by androidx.compose.runtime.remember {
            mutableStateOf(prefs.getBoolean(MainActivity.PREF_SPLASH, true))
        }
        Column(Modifier.fillMaxSize().background(fairuPageBackground())) {
            FairuPageTopBar(title = stringResource(R.string.settings), onBack = { finish() })
            Column(Modifier.verticalScroll(scrollState)) {
                // v1.9.7.8：两组分组（参考图样式）。分组名只中英写死，其他语言见 strings.xml。
                FairuSectionLabel(
                    stringResource(R.string.settings_group_language_appearance),
                    color = MaterialTheme.colorScheme.primary
                )
                FairuCard {
                    FairuRow(
                        iconRes = R.drawable.ic_language,
                        title = stringResource(R.string.language_settings),
                        // v1.9.8：跟随系统时副标题显示“跟随系统”，不显示具体语言名。
                        sub = run {
                            val tag = LanguageActivity.currentTag(this@SettingsActivity)
                            if (tag == LanguageActivity.TAG_FOLLOW_SYSTEM) stringResource(R.string.follow_system_language)
                            else LanguageActivity.nativeName(tag)
                        },
                        iconCircle = true,
                        onClick = { startActivity(Intent(this@SettingsActivity, LanguageActivity::class.java)) }
                    )
                    SettingsDivider()
                    FairuRow(
                        iconRes = R.drawable.ic_palette,
                        title = stringResource(R.string.appearance),
                        sub = when (prefs.getInt(MainActivity.PREF_THEME_MODE, 0)) {
                            1 -> stringResource(R.string.theme_light)
                            2 -> stringResource(R.string.theme_dark)
                            else -> stringResource(R.string.theme_system)
                        },
                        iconCircle = true,
                        onClick = { startActivity(Intent(this@SettingsActivity, AppearanceActivity::class.java)) }
                    )
                }
                FairuSectionLabel(
                    stringResource(R.string.settings_group_app),
                    color = MaterialTheme.colorScheme.primary
                )
                FairuCard {
                    FairuSwitchRow(
                        iconRes = R.drawable.ic_play,
                        title = stringResource(R.string.splash_animation),
                        // v1.9.7.2：View 时代的提示在 Compose 重写时丢了，补回来。
                        sub = stringResource(R.string.splash_animation_hint),
                        checked = splashOn,
                        iconCircle = true,
                        onToggle = {
                            splashOn = !splashOn
                            prefs.edit().putBoolean(MainActivity.PREF_SPLASH, splashOn).apply()
                        }
                    )
                    SettingsDivider()
                    FairuRow(
                        iconRes = R.drawable.ic_info,
                        title = stringResource(R.string.about),
                        sub = run {
                            val ver = try {
                                packageManager.getPackageInfo(packageName, 0).versionName
                            } catch (_: Exception) {
                                "1.9.7.1"
                            }
                            stringResource(R.string.version) + " " + ver
                        },
                        iconCircle = true,
                        onClick = { startActivity(Intent(this@SettingsActivity, AboutActivity::class.java)) }
                    )
                    SettingsDivider()
                    FairuRow(
                        iconRes = R.drawable.ic_flask,
                        title = stringResource(R.string.experimental_features),
                        sub = MainActivity.engineDisplayName(
                            this@SettingsActivity,
                            prefs.getInt(MainActivity.PREF_UI_ENGINE, MainActivity.UI_ENGINE_MD3)
                        ),
                        iconCircle = true,
                        onClick = { showWarning = true }
                    )
                }
                // v1.9.8：一键恢复默认设置（卡片外、页面底部，长椭圆形按钮）。
                Button(
                    onClick = { showResetDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        painterResource(R.drawable.ic_undo),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.reset_defaults),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        // 实验性功能警告框：每次点都弹，确认才进，取消不进。
        if (showWarning) {
            FairuAlertDialog(
                onDismissRequest = { showWarning = false },
                icon = { Icon(painterResource(R.drawable.ic_flask), contentDescription = null) },
                title = { Text(stringResource(R.string.experimental_warning_title)) },
                text = { Text(stringResource(R.string.experimental_warning_msg)) },
                confirmButton = {
                    TextButton(onClick = {
                        showWarning = false
                        startActivity(Intent(this@SettingsActivity, ExperimentalActivity::class.java))
                    }) { Text(stringResource(R.string.confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showWarning = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
        // v1.9.8：一键恢复默认设置确认框，确认才重置，取消不做任何事。
        if (showResetDialog) {
            FairuAlertDialog(
                onDismissRequest = { showResetDialog = false },
                icon = { Icon(painterResource(R.drawable.ic_undo), contentDescription = null) },
                title = { Text(stringResource(R.string.reset_defaults)) },
                text = { Text(stringResource(R.string.reset_defaults_msg)) },
                confirmButton = {
                    TextButton(onClick = {
                        showResetDialog = false
                        resetToDefaults()
                    }) { Text(stringResource(R.string.confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
    }
}
