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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
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
 * 多语言设置页（v1.9.8.1 Compose）：18 种语言列表。
 * 点语言只选中 RadioButton，不立即切换；点右上角 ✓ 确认才
 * AppCompatDelegate.setApplicationLocales 切换；直接按返回则不切换。
 */
class LanguageActivity : BaseActivity() {

    data class Lang(val tag: String, val native: String, val sub: String)

    companion object {
        val LANGUAGES = listOf(
            Lang("zh-CN", "简体中文", "Chinese (Simplified)"),
            Lang("zh-TW", "繁體中文", "Chinese (Traditional)"),
            Lang("en", "English", "英语"),
            Lang("fr", "Français", "法语"),
            Lang("ja", "日本語", "日语"),
            Lang("ko", "한국어", "韩语"),
            Lang("ar", "العربية", "阿拉伯语 · RTL"),
            Lang("es", "Español", "西班牙语"),
            Lang("pt", "Português", "葡萄牙语"),
            Lang("th", "ไทย", "泰语"),
            Lang("eo", "Esperanto", "世界语"),
            Lang("de", "Deutsch", "德语"),
            Lang("ru", "Русский", "俄语"),
            Lang("mn", "Монгол", "蒙古语（西里尔）"),
            Lang("mn-Mong", "ᠮᠣᠩᠭᠣᠯ", "蒙古语（传统蒙古文）"),
            Lang("bo", "བོད་སྐད་", "藏语"),
            Lang("ug", "ئۇيغۇرچە", "维吾尔语 · RTL"),
            Lang("za", "Vahcuengh", "壮语")
        )

        /** 当前生效的语言 tag：优先用保存的设置，没有就按系统语言匹配。 */
        fun currentTag(ctx: android.content.Context): String {
            val sp = ctx.getSharedPreferences("fairu_prefs", MODE_PRIVATE)
            val saved = sp.getString(MainActivity.PREF_LOCALE, null)
            if (!saved.isNullOrBlank()) return saved
            val sys = Locale.getDefault()
            val sysTag = sys.toLanguageTag()
            LANGUAGES.firstOrNull { it.tag.equals(sysTag, ignoreCase = true) }?.let { return it.tag }
            LANGUAGES.firstOrNull { it.tag.substringBefore('-').equals(sys.language, ignoreCase = true) }?.let { return it.tag }
            return "zh-CN"
        }

        fun nativeName(tag: String): String =
            LANGUAGES.firstOrNull { it.tag == tag }?.native ?: tag
    }

    private lateinit var prefs: SharedPreferences
    private var pendingTag by mutableStateOf("zh-CN")

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashHandler.install(this)
        super.onCreate(savedInstanceState)
        try {
            prefs = getSharedPreferences("fairu_prefs", MODE_PRIVATE)
            pendingTag = currentTag(this)
            setContent {
                FairuTheme { LanguageScreen() }
            }
        } catch (e: Throwable) {
            CrashHandler.writeLog(this, e)
            try { finish() } catch (_: Exception) { }
        }
    }

    @Composable
    private fun LanguageScreen() {
        Column(Modifier.fillMaxSize().background(fairuPageBackground())) {
            FairuPageTopBar(
                title = stringResource(R.string.language_settings),
                onBack = { finish() },
                actions = {
                    IconButton(onClick = {
                        prefs.edit().putString(MainActivity.PREF_LOCALE, pendingTag).apply()
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.create(Locale.forLanguageTag(pendingTag))
                        )
                        finish()
                    }) {
                        Icon(
                            painterResource(R.drawable.ic_check),
                            contentDescription = stringResource(R.string.confirm),
                            tint = fairuTopBarIconTint()
                        )
                    }
                }
            )
            LazyColumn {
                items(LANGUAGES, key = { it.tag }) { lang ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pendingTag = lang.tag }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                lang.native,
                                fontSize = 16.sp,
                                fontWeight = if (isMiuixEngine()) androidx.compose.ui.text.font.FontWeight.Bold
                                else androidx.compose.ui.text.font.FontWeight.Normal
                            )
                            Text(
                                lang.sub,
                                fontSize = 13.sp,
                                color = if (isMiuixEngine()) top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceVariantSummary
                                else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        RadioButton(
                            selected = lang.tag == pendingTag,
                            onClick = { pendingTag = lang.tag }
                        )
                    }
                }
            }
        }
    }
}
