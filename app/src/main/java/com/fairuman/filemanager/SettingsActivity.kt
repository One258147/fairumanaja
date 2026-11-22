package com.fairuman.filemanager

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource

/**
 * 设置页（v1.9.8.1 Compose）：大标题“设置”+ 卡片式分组，四行：
 * 多语言设置（点进单独一页选，右上角 ✓ 确认才切换）、外观（点进单独的外观页，
 * 主题模式 / 动态颜色 / 应用配色都在里面）、开屏动画（开关，默认开）、
 * 关于（进单独的关于页）。实验性功能：点进之前先弹警告框，确认才进，取消不进。
 */
class SettingsActivity : BaseActivity() {

    private lateinit var prefs: SharedPreferences
    private var showWarning by mutableStateOf(false)

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
                FairuCard {
                    FairuRow(
                        iconRes = R.drawable.ic_language,
                        title = stringResource(R.string.language_settings),
                        sub = LanguageActivity.nativeName(LanguageActivity.currentTag(this@SettingsActivity)),
                        onClick = { startActivity(Intent(this@SettingsActivity, LanguageActivity::class.java)) }
                    )
                    FairuRow(
                        iconRes = R.drawable.ic_palette,
                        title = stringResource(R.string.appearance),
                        sub = when (prefs.getInt(MainActivity.PREF_THEME_MODE, 0)) {
                            1 -> stringResource(R.string.theme_light)
                            2 -> stringResource(R.string.theme_dark)
                            else -> stringResource(R.string.theme_system)
                        },
                        onClick = { startActivity(Intent(this@SettingsActivity, AppearanceActivity::class.java)) }
                    )
                    FairuSwitchRow(
                        iconRes = R.drawable.ic_play,
                        title = stringResource(R.string.splash_animation),
                        // v1.9.7.2：View 时代的提示在 Compose 重写时丢了，补回来。
                        sub = stringResource(R.string.splash_animation_hint),
                        checked = splashOn,
                        onToggle = {
                            splashOn = !splashOn
                            prefs.edit().putBoolean(MainActivity.PREF_SPLASH, splashOn).apply()
                        }
                    )
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
                        onClick = { startActivity(Intent(this@SettingsActivity, AboutActivity::class.java)) }
                    )
                    FairuRow(
                        iconRes = R.drawable.ic_flask,
                        title = stringResource(R.string.experimental_features),
                        sub = MainActivity.engineDisplayName(
                            this@SettingsActivity,
                            prefs.getInt(MainActivity.PREF_UI_ENGINE, MainActivity.UI_ENGINE_MD3)
                        ),
                        onClick = { showWarning = true }
                    )
                }
            }
        }
        // 实验性功能警告框：每次点都弹，确认才进，取消不进。
        if (showWarning) {
            AlertDialog(
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
    }
}
