package com.fairuman.filemanager

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * 实验性功能页（v1.9.8.1 Compose，v1.9.7.5 改）：大标题“实验性功能”+ 一张卡片，两行：
 * UI引擎（点行弹菜单：Material Design 3 / M3 Expressive / Miuix，选别的先弹确认框，
 * 确认才切换；跟主题设置页的主题风格是同一份 PREF_UI_ENGINE，两边同步）、
 * 濒危多语言切换（点弹 5 选 1 对话框，选好点确认才切换）。
 * 液态玻璃已删（v1.9.7.5）。
 * 卡片底部只留第二行提示“这里的功能还在试验中，可能会有变化。”
 * （第一行“点行选模式，点开关开启。”已删，Max：明显多余）。
 * 濒危多语言行副标题：当前是 5 种之一时显示该语种原语名，否则显示
 * “目前仅支持5种语言，后续可能会添加”。
 */
class ExperimentalActivity : BaseActivity() {

    private lateinit var prefs: SharedPreferences

    companion object {
        /** v1.9.7 濒危多语言：只做 Max 点名的这 5 个，不加别的。 */
        val ENDANGERED_LANGS = listOf(
            Triple("mnc", "ᠮᠠᠨᠵᡠ ᡤᡳᠰᡠᠨ", "满语 · Manchu"),
            Triple("sjo", "ᠰᡞᠪᡝ ᡤᡞᠰᡠᠨ", "锡伯语 · Xibe"),
            Triple("haw", "ʻŌlelo Hawaiʻi", "夏威夷语 · Hawaiian"),
            Triple("mi", "Te Reo Māori", "毛利语 · Māori"),
            Triple("chr", "ᏣᎳᎩ ᎦᏬᏂᎯᏍᏗ", "切罗基语 · Cherokee")
        )

        fun endangeredNative(tag: String): String? =
            ENDANGERED_LANGS.firstOrNull { it.first == tag }?.second
    }

    private var engine by mutableStateOf(MainActivity.UI_ENGINE_MD3)
    private var showEngineDialog by mutableStateOf(false)
    /** 待确认的引擎切换：（目标引擎，显示名）。 */
    private var engineConfirm by mutableStateOf<Pair<Int, String>?>(null)
    private var showEndangeredDialog by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashHandler.install(this)
        super.onCreate(savedInstanceState)
        try {
            prefs = getSharedPreferences("fairu_prefs", MODE_PRIVATE)
            engine = prefs.getInt(MainActivity.PREF_UI_ENGINE, MainActivity.UI_ENGINE_MD3)
            setContent {
                FairuTheme { ExperimentalScreen() }
            }
        } catch (e: Throwable) {
            CrashHandler.writeLog(this, e)
            try { finish() } catch (_: Exception) { }
        }
    }

    private fun endangeredSub(): String {
        val tag = prefs.getString(MainActivity.PREF_LOCALE, null)
        return endangeredNative(tag ?: "") ?: getString(R.string.endangered_language_sub)
    }

    @Composable
    private fun ExperimentalScreen() {
        val scrollState = rememberScrollState()
        Column(Modifier.fillMaxSize().background(fairuPageBackground())) {
            FairuPageTopBar(title = stringResource(R.string.experimental_features), onBack = { finish() })
            Column(Modifier.verticalScroll(scrollState)) {
                FairuCard {
                    // UI引擎：点行弹菜单，选别的先弹确认框，确认才切换。
                    FairuRow(
                        iconRes = R.drawable.ic_theme,
                        title = stringResource(R.string.ui_engine),
                        sub = MainActivity.engineDisplayName(this@ExperimentalActivity, engine),
                        onClick = { showEngineDialog = true }
                    )
                    FairuRow(
                        iconRes = R.drawable.ic_language,
                        title = stringResource(R.string.endangered_language_switch),
                        sub = endangeredSub(),
                        onClick = { showEndangeredDialog = true }
                    )
                    // 卡片底部提示：只留第二行（第一行已删）。
                    FairuHint(stringResource(R.string.experimental_features_hint))
                }
            }
        }
        if (showEngineDialog) EngineDialog()
        // 引擎切换确认框：选了别的引擎才弹，确认才切换。
        engineConfirm?.let { (eng, name) ->
            FairuAlertDialog(
                onDismissRequest = { engineConfirm = null },
                icon = { Icon(painterResource(R.drawable.ic_theme), contentDescription = null) },
                title = { Text(stringResource(R.string.ui_engine_switch_title)) },
                text = { Text(stringResource(R.string.ui_engine_switch_msg, name)) },
                confirmButton = {
                    TextButton(onClick = {
                        engineConfirm = null
                        applyEngine(eng)
                    }) { Text(stringResource(R.string.confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { engineConfirm = null }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
        if (showEndangeredDialog) EndangeredDialog()
    }

    /** 引擎切换（确认后）：只写同一份 PREF_UI_ENGINE，主题设置页的主题风格
     *  单选卡片从同一份偏好读，两边同步。 */
    private fun applyEngine(newEngine: Int) {
        engine = newEngine
        prefs.edit().putInt(MainActivity.PREF_UI_ENGINE, newEngine).apply()
        recreate()
    }

    /** UI引擎菜单：三行（标题 + 一句话说明 + 圆圈选中态），点别的行先弹确认框。 */
    @Composable
    private fun EngineDialog() {
        val options = listOf(
            Triple(MainActivity.UI_ENGINE_MD3, "Material Design 3", stringResource(R.string.ui_engine_desc_md3)),
            Triple(MainActivity.UI_ENGINE_EXPRESSIVE, "M3 Expressive", stringResource(R.string.ui_engine_desc_expressive)),
            Triple(MainActivity.UI_ENGINE_MIUIX, "Miuix", stringResource(R.string.ui_engine_desc_miuix))
        )
        FairuAlertDialog(
            onDismissRequest = { showEngineDialog = false },
            title = { Text(stringResource(R.string.ui_engine)) },
            text = {
                Column {
                    options.forEach { (eng, name, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showEngineDialog = false
                                    if (eng != engine) engineConfirm = eng to name
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = eng == engine, onClick = {
                                showEngineDialog = false
                                if (eng != engine) engineConfirm = eng to name
                            })
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(name, fontSize = 16.sp)
                                Text(desc, fontSize = 13.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { }
        )
    }

    /** 濒危多语言切换：5 个单选（圆圈 + 原语名 + 中文名），点确认才切换。 */
    @Composable
    private fun EndangeredDialog() {
        val current = prefs.getString(MainActivity.PREF_LOCALE, null) ?: ""
        var pendingTag by androidx.compose.runtime.remember {
            mutableStateOf(ENDANGERED_LANGS.firstOrNull { it.first == current }?.first)
        }
        FairuAlertDialog(
            onDismissRequest = { showEndangeredDialog = false },
            icon = { Icon(painterResource(R.drawable.ic_language), contentDescription = null) },
            title = { Text(stringResource(R.string.endangered_language_switch)) },
            text = {
                Column {
                    ENDANGERED_LANGS.forEach { (tag, native, sub) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pendingTag = tag }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = tag == pendingTag, onClick = { pendingTag = tag })
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(native, fontSize = 16.sp)
                                Text(sub, fontSize = 13.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val tag = pendingTag ?: return@TextButton
                    showEndangeredDialog = false
                    if (tag == current) return@TextButton
                    prefs.edit().putString(MainActivity.PREF_LOCALE, tag).apply()
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.create(Locale.forLanguageTag(tag))
                    )
                    recreate()
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showEndangeredDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
