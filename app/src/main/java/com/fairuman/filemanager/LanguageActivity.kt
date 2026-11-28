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
 * 多语言设置页（v1.9.8 Compose）：43 种语言列表，按英文名 A-Z 排。
 * 最上方有一行“跟随系统”（不参与字母排序），点语言只选中 RadioButton，
 * 不立即切换；点右上角 ✓ 确认才切换；直接按返回则不切换。
 * 跟随系统 = 不保存 PREF_LOCALE + 清掉 AppCompat 语言覆盖。
 */
class LanguageActivity : BaseActivity() {

    data class Lang(val tag: String, val native: String, val sub: String)

    companion object {
        /** “跟随系统”的哨兵值：只在内存里用，从不写进偏好。 */
        const val TAG_FOLLOW_SYSTEM = "system"

        /** 43 种语言，按英文名 A-Z 固定顺序（v1.9.8：原来 18 种 + 新增 25 种）。 */
        val LANGUAGES = listOf(
            Lang("ar", "العربية", "阿拉伯语 · RTL"),
            Lang("hy", "Հայերեն", "亚美尼亚语"),
            Lang("yue", "粵語", "粤语"),
            Lang("zh-CN", "简体中文", "简体中文"),
            Lang("zh-TW", "繁體中文", "繁體中文"),
            Lang("lzh", "文言文", "文言文"),
            Lang("da", "Dansk", "丹麦语"),
            Lang("nl", "Nederlands", "荷兰语"),
            Lang("ar-EG", "العربية المصرية", "埃及语"),
            Lang("en", "English", "英语"),
            Lang("en-US", "English (US)", "美式英语"),
            Lang("eo", "Esperanto", "世界语"),
            Lang("fil", "Filipino", "菲律宾语"),
            Lang("fr", "Français", "法语"),
            Lang("de", "Deutsch", "德语"),
            Lang("el", "Ελληνικά", "希腊语"),
            Lang("hi", "हिन्दी", "印地语"),
            Lang("hmn", "Hmoob", "苗语"),
            Lang("hu", "Magyar", "匈牙利语"),
            Lang("in", "Bahasa Indonesia", "印度尼西亚语"),
            Lang("it", "Italiano", "意大利语"),
            Lang("ja", "日本語", "日语"),
            Lang("kk-Arab", "قازاقشا", "哈萨克语"),
            Lang("kk-Cyrl", "Қазақша", "西里尔哈萨克语"),
            Lang("ko", "한국어", "韩语"),
            Lang("la", "Latina", "拉丁语"),
            Lang("ms", "Bahasa Melayu", "马来西亚语"),
            Lang("mn", "Монгол", "蒙古语（西里尔）"),
            Lang("mn-Mong", "ᠮᠣᠩᠭᠣᠯ", "蒙古语（传统蒙古文）"),
            Lang("nb", "Norsk", "挪威语"),
            Lang("fa", "فارسی", "波斯语"),
            Lang("pl", "Polski", "波兰语"),
            Lang("pt", "Português", "葡萄牙语"),
            Lang("ru", "Русский", "俄语"),
            Lang("sa", "संस्कृतम्", "梵语"),
            Lang("es", "Español", "西班牙语"),
            Lang("sv", "Svenska", "瑞典语"),
            Lang("th", "ไทย", "泰语"),
            Lang("bo", "བོད་སྐད་", "藏语"),
            Lang("tr", "Türkçe", "土耳其语"),
            Lang("ug", "ئۇيغۇرچە", "维吾尔语 · RTL"),
            Lang("ii", "ꆈꌠ꒿", "彝语"),
            Lang("za", "Vahcuengh", "壮语")
        )

        /** 当前选中的语言 tag：没保存过就是跟随系统；老用户升级后保持原来选的语言。 */
        fun currentTag(ctx: android.content.Context): String {
            val sp = ctx.getSharedPreferences("fairu_prefs", MODE_PRIVATE)
            val saved = sp.getString(MainActivity.PREF_LOCALE, null)
            if (!saved.isNullOrBlank()) return saved
            return TAG_FOLLOW_SYSTEM
        }

        /** 当前实际生效的语言 tag（跟随系统时按系统语言匹配，供 About 页协议正文判断用）。 */
        fun effectiveTag(ctx: android.content.Context): String {
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
                        if (pendingTag == TAG_FOLLOW_SYSTEM) {
                            // 跟随系统：清掉保存的语言偏好 + 清掉 AppCompat 语言覆盖。
                            prefs.edit().remove(MainActivity.PREF_LOCALE).apply()
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                        } else {
                            prefs.edit().putString(MainActivity.PREF_LOCALE, pendingTag).apply()
                            AppCompatDelegate.setApplicationLocales(
                                LocaleListCompat.create(Locale.forLanguageTag(pendingTag))
                            )
                        }
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
                // v1.9.8：最上方“跟随系统”行（不参与字母排序），副标题显示当前系统语言。
                item(key = TAG_FOLLOW_SYSTEM) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pendingTag = TAG_FOLLOW_SYSTEM }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.follow_system_language),
                                fontSize = 16.sp,
                                fontWeight = if (isMiuixEngine()) androidx.compose.ui.text.font.FontWeight.Bold
                                else androidx.compose.ui.text.font.FontWeight.Normal
                            )
                            Text(
                                // 真正的系统语言：Locale.getDefault() 会被 AppCompat 的
                                // 应用语言覆盖，要用 Resources.getSystem() 才拿得到系统语言。
                                run {
                                    val sysLocale = try {
                                        android.content.res.Resources.getSystem().configuration.locales.get(0)
                                    } catch (_: Exception) {
                                        Locale.getDefault()
                                    }
                                    sysLocale.getDisplayLanguage(sysLocale)
                                },
                                fontSize = 13.sp,
                                color = if (isMiuixEngine()) top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceVariantSummary
                                else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        RadioButton(
                            selected = TAG_FOLLOW_SYSTEM == pendingTag,
                            onClick = { pendingTag = TAG_FOLLOW_SYSTEM }
                        )
                    }
                }
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
