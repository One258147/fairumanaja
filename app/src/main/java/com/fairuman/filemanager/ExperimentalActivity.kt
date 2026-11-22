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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * 实验性功能页（v1.9.8.1 Compose）：大标题“实验性功能”+ 一张卡片，三行：
 * UI引擎（点弹菜单列三个选项，点一个跟当前不同的先弹确认框，确认才切换）、
 * 液态玻璃（点行弹模式选择，点开关才是开关；关着先弹确认框，确认才开）、
 * 濒危多语言切换（点弹 5 选 1 对话框，选好点确认才切换）。
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
    private var glassOn by mutableStateOf(false)
    private var glassMode by mutableStateOf(MainActivity.GLASS_MODE_TRANSLUCENT)
    private var showEngineDialog by mutableStateOf(false)
    private var showGlassModeDialog by mutableStateOf(false)
    private var showGlassConfirm by mutableStateOf(false)
    private var showEndangeredDialog by mutableStateOf(false)
    private var engineConfirm by mutableStateOf<Pair<Int, String>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashHandler.install(this)
        super.onCreate(savedInstanceState)
        try {
            prefs = getSharedPreferences("fairu_prefs", MODE_PRIVATE)
            engine = prefs.getInt(MainActivity.PREF_UI_ENGINE, MainActivity.UI_ENGINE_MD3)
            glassOn = prefs.getBoolean(MainActivity.PREF_LIQUID_GLASS, false)
            glassMode = prefs.getInt(MainActivity.PREF_LIQUID_GLASS_MODE, MainActivity.GLASS_MODE_TRANSLUCENT)
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
                    FairuRow(
                        iconRes = R.drawable.ic_theme,
                        title = stringResource(R.string.ui_engine),
                        sub = MainActivity.engineDisplayName(this@ExperimentalActivity, engine),
                        onClick = { showEngineDialog = true }
                    )
                    // 液态玻璃：点行弹模式选择（不是开关），点开关才是开关。
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showGlassModeDialog = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_droplet),
                            contentDescription = null,
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.liquid_glass), fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Text(
                                MainActivity.glassModeDisplayName(this@ExperimentalActivity, glassMode),
                                fontSize = 13.sp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        androidx.compose.material3.Switch(
                            checked = glassOn,
                            onCheckedChange = {
                                if (!glassOn) {
                                    showGlassConfirm = true
                                } else {
                                    glassOn = false
                                    prefs.edit().putBoolean(MainActivity.PREF_LIQUID_GLASS, false).apply()
                                    recreate()
                                }
                            }
                        )
                    }
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
        if (showGlassModeDialog) GlassModeDialog()
        if (showGlassConfirm) {
            AlertDialog(
                onDismissRequest = { showGlassConfirm = false },
                icon = { Icon(painterResource(R.drawable.ic_droplet), contentDescription = null) },
                title = { Text(stringResource(R.string.liquid_glass_confirm_title)) },
                text = { Text(stringResource(R.string.liquid_glass_confirm_msg)) },
                confirmButton = {
                    TextButton(onClick = {
                        showGlassConfirm = false
                        glassOn = true
                        prefs.edit().putBoolean(MainActivity.PREF_LIQUID_GLASS, true).apply()
                        recreate()
                    }) { Text(stringResource(R.string.confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showGlassConfirm = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
        engineConfirm?.let { (eng, name) ->
            AlertDialog(
                onDismissRequest = { engineConfirm = null },
                icon = { Icon(painterResource(R.drawable.ic_theme), contentDescription = null) },
                title = { Text(stringResource(R.string.ui_engine_switch_title)) },
                text = { Text(stringResource(R.string.ui_engine_switch_msg, name)) },
                confirmButton = {
                    TextButton(onClick = {
                        engineConfirm = null
                        engine = eng
                        prefs.edit().putInt(MainActivity.PREF_UI_ENGINE, eng).apply()
                        recreate()
                    }) { Text(stringResource(R.string.confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { engineConfirm = null }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
        if (showEndangeredDialog) EndangeredDialog()
    }

    /** UI 引擎菜单：三行（标题 + 一句话说明 + 圆圈选中态），点一行处理。 */
    @Composable
    private fun EngineDialog() {
        val options = listOf(
            Triple(MainActivity.UI_ENGINE_MD3, "Material Design 3", stringResource(R.string.ui_engine_desc_md3)),
            Triple(MainActivity.UI_ENGINE_EXPRESSIVE, "M3 Expressive", stringResource(R.string.ui_engine_desc_expressive)),
            Triple(MainActivity.UI_ENGINE_MIUIX, "Miuix", stringResource(R.string.ui_engine_desc_miuix))
        )
        AlertDialog(
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

    /** 液态玻璃模式菜单：三行（标题 + 一句话说明 + 圆圈选中态），点一行即选即保存。 */
    @Composable
    private fun GlassModeDialog() {
        val options = listOf(
            Triple(MainActivity.GLASS_MODE_TRANSLUCENT, stringResource(R.string.liquid_glass_mode_translucent), stringResource(R.string.liquid_glass_mode_desc_translucent)),
            Triple(MainActivity.GLASS_MODE_BLUR, stringResource(R.string.liquid_glass_mode_blur), stringResource(R.string.liquid_glass_mode_desc_blur)),
            Triple(MainActivity.GLASS_MODE_CLEAR, stringResource(R.string.liquid_glass_mode_clear), stringResource(R.string.liquid_glass_mode_desc_clear))
        )
        AlertDialog(
            onDismissRequest = { showGlassModeDialog = false },
            title = { Text(stringResource(R.string.liquid_glass_mode)) },
            text = {
                Column {
                    options.forEach { (mode, name, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showGlassModeDialog = false
                                    if (mode != glassMode) {
                                        glassMode = mode
                                        prefs.edit().putInt(MainActivity.PREF_LIQUID_GLASS_MODE, mode).apply()
                                        if (glassOn) recreate()
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = mode == glassMode, onClick = {
                                showGlassModeDialog = false
                                if (mode != glassMode) {
                                    glassMode = mode
                                    prefs.edit().putInt(MainActivity.PREF_LIQUID_GLASS_MODE, mode).apply()
                                    if (glassOn) recreate()
                                }
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
        AlertDialog(
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
