package com.fairuman.filemanager

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot

/**
 * v1.9.8.1 Compose 主题基础设施。
 * 取代 res/values[-night]/themes_seed.xml + themes_engine.xml + themes_font.xml
 * 的 XML 覆盖层做法：配色直接从偏好算出来，不再经过 Activity 主题。
 * 顺序与旧版 applyPrefs 一致：主题模式 → 动态颜色/种子色 → 引擎形状 →
 * 字体颜色（最后，改 onSurface + onSurfaceVariant）→ 满文/锡伯文蒙古文字体。
 * v1.9.7.5：液态玻璃已删（HAZE 不再用），多彩对话框已删。
 */

/** 本次主题快照（BaseActivity.onResume 拿它比对，变了就 recreate）。 */
data class FairuThemeState(
    val themeMode: Int = 0,          // 0 跟随系统 / 1 浅色 / 2 深色
    val dynamicColor: Boolean = false,
    val seed: Int = MainActivity.DEFAULT_SEED,
    val engine: Int = MainActivity.UI_ENGINE_MD3,
    val fontColor: Int = MainActivity.DEFAULT_FONT_COLOR,
    val localeTag: String? = null,
)

fun SharedPreferences.readFairuTheme(): FairuThemeState = FairuThemeState(
    themeMode = getInt(MainActivity.PREF_THEME_MODE, 0),
    dynamicColor = getBoolean(MainActivity.PREF_DYNAMIC_COLOR, false),
    seed = getInt(MainActivity.PREF_SEED_COLOR, MainActivity.DEFAULT_SEED),
    engine = getInt(MainActivity.PREF_UI_ENGINE, MainActivity.UI_ENGINE_MD3),
    fontColor = getInt(MainActivity.PREF_FONT_COLOR, MainActivity.DEFAULT_FONT_COLOR),
    localeTag = getString(MainActivity.PREF_LOCALE, null),
)

fun Context.readFairuTheme(): FairuThemeState =
    getSharedPreferences("fairu_prefs", Context.MODE_PRIVATE).readFairuTheme()

/** 种子色 → Material 3 配色（HCT）。
 * MD3 / Miuix 用 SchemeTonalSpot（与旧版 XML 种子覆盖层同源）；
 * M3 Expressive 用 SchemeExpressive——更鲜艳饱和的容器色、更强的主色
 * 对比，一眼就能和 MD3 区分开（v1.9.7.4：之前只改了形状，颜色/字型
 * 全一样，Max 说跟 MD3 一点区别都没有）。 */
fun seedColorScheme(seed: Int, dark: Boolean, engine: Int = MainActivity.UI_ENGINE_MD3): ColorScheme {
    val s = if (engine == MainActivity.UI_ENGINE_EXPRESSIVE) {
        com.materialkolor.scheme.SchemeExpressive(Hct.fromInt(seed), dark, 0.0)
    } else {
        SchemeTonalSpot(Hct.fromInt(seed), dark, 0.0)
    }
    return if (dark) darkColorScheme(
        primary = Color(s.primary), onPrimary = Color(s.onPrimary),
        primaryContainer = Color(s.primaryContainer), onPrimaryContainer = Color(s.onPrimaryContainer),
        secondary = Color(s.secondary), onSecondary = Color(s.onSecondary),
        secondaryContainer = Color(s.secondaryContainer), onSecondaryContainer = Color(s.onSecondaryContainer),
        tertiary = Color(s.tertiary), onTertiary = Color(s.onTertiary),
        tertiaryContainer = Color(s.tertiaryContainer), onTertiaryContainer = Color(s.onTertiaryContainer),
        error = Color(s.error), onError = Color(s.onError),
        errorContainer = Color(s.errorContainer), onErrorContainer = Color(s.onErrorContainer),
        background = Color(s.background), onBackground = Color(s.onBackground),
        surface = Color(s.surface), onSurface = Color(s.onSurface),
        surfaceVariant = Color(s.surfaceVariant), onSurfaceVariant = Color(s.onSurfaceVariant),
        surfaceTint = Color(s.surfaceTint),
        inverseSurface = Color(s.inverseSurface), inverseOnSurface = Color(s.inverseOnSurface),
        inversePrimary = Color(s.inversePrimary),
        outline = Color(s.outline), outlineVariant = Color(s.outlineVariant),
        scrim = Color(s.scrim),
        surfaceDim = Color(s.surfaceDim), surfaceBright = Color(s.surfaceBright),
        surfaceContainerLowest = Color(s.surfaceContainerLowest),
        surfaceContainerLow = Color(s.surfaceContainerLow),
        surfaceContainer = Color(s.surfaceContainer),
        surfaceContainerHigh = Color(s.surfaceContainerHigh),
        surfaceContainerHighest = Color(s.surfaceContainerHighest),
    ) else lightColorScheme(
        primary = Color(s.primary), onPrimary = Color(s.onPrimary),
        primaryContainer = Color(s.primaryContainer), onPrimaryContainer = Color(s.onPrimaryContainer),
        secondary = Color(s.secondary), onSecondary = Color(s.onSecondary),
        secondaryContainer = Color(s.secondaryContainer), onSecondaryContainer = Color(s.onSecondaryContainer),
        tertiary = Color(s.tertiary), onTertiary = Color(s.onTertiary),
        tertiaryContainer = Color(s.tertiaryContainer), onTertiaryContainer = Color(s.onTertiaryContainer),
        error = Color(s.error), onError = Color(s.onError),
        errorContainer = Color(s.errorContainer), onErrorContainer = Color(s.onErrorContainer),
        background = Color(s.background), onBackground = Color(s.onBackground),
        surface = Color(s.surface), onSurface = Color(s.onSurface),
        surfaceVariant = Color(s.surfaceVariant), onSurfaceVariant = Color(s.onSurfaceVariant),
        surfaceTint = Color(s.surfaceTint),
        inverseSurface = Color(s.inverseSurface), inverseOnSurface = Color(s.inverseOnSurface),
        inversePrimary = Color(s.inversePrimary),
        outline = Color(s.outline), outlineVariant = Color(s.outlineVariant),
        scrim = Color(s.scrim),
        surfaceDim = Color(s.surfaceDim), surfaceBright = Color(s.surfaceBright),
        surfaceContainerLowest = Color(s.surfaceContainerLowest),
        surfaceContainerLow = Color(s.surfaceContainerLow),
        surfaceContainer = Color(s.surfaceContainer),
        surfaceContainerHigh = Color(s.surfaceContainerHigh),
        surfaceContainerHighest = Color(s.surfaceContainerHighest),
    )
}

/** 当前主题快照（供各页读引擎/玻璃/字体颜色，不用再传参）。 */
val LocalFairuTheme = compositionLocalOf { FairuThemeState() }
/** 满文/锡伯文字体（mnc/sjo 下用随包的 Noto Sans Mongolian）。 */
val LocalMongolianFont = compositionLocalOf<FontFamily?> { null }
/** 引擎形状：MD3 默认；M3 Expressive 更大圆角（卡片 40dp、对话框 48dp、
 * 按钮药丸），配色/字型再拉开差距（v1.9.7.4：旧值 32/36dp 只改了一点，
 * 肉眼几乎看不出区别）。 */
fun engineShapes(engine: Int): Shapes = when (engine) {
    MainActivity.UI_ENGINE_EXPRESSIVE -> Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        small = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
        large = androidx.compose.foundation.shape.RoundedCornerShape(40.dp),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(48.dp),
    )
    else -> Shapes()
}

/** M3 Expressive 字型：标题更大更粗、正文略大（v1.9.7.4，与 MD3 拉开差距）。 */
fun expressiveTypography(): androidx.compose.material3.Typography {
    val base = androidx.compose.material3.Typography()
    return androidx.compose.material3.Typography(
        displayLarge = base.displayLarge.copy(fontSize = 60.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
        displayMedium = base.displayMedium.copy(fontSize = 48.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
        displaySmall = base.displaySmall.copy(fontSize = 38.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
        headlineLarge = base.headlineLarge.copy(fontSize = 34.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontSize = 30.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontSize = 26.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontSize = 26.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
        titleSmall = base.titleSmall.copy(fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
        bodyLarge = base.bodyLarge.copy(fontSize = 18.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 16.sp),
        labelLarge = base.labelLarge.copy(fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
    )
}

/** 满文/锡伯文时全页字体换成随包的 Noto Sans Mongolian（横排，见 v1.9.7）。 */
@Composable
fun mongolianFontFamily(localeTag: String?): FontFamily? {
    if (localeTag != "mnc" && localeTag != "sjo") return null
    return remember { FontFamily(Font(R.font.noto_sans_mongolian)) }
}

/**
 * 应用主题入口（每个 Activity 的 setContent 外层包一层）。
 * Miuix 引擎不在这里走 MaterialTheme——Miuix 页自己用 MiuixTheme（见 MiuixTheme.kt），
 * 这里只负责算出配色并提供给它。
 */
@Composable
fun FairuTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.readFairuTheme() }
    val systemDark = isSystemInDarkTheme()
    val dark = when (prefs.themeMode) {
        1 -> false
        2 -> true
        else -> systemDark
    }
    val baseScheme: ColorScheme = remember(prefs.dynamicColor, prefs.seed, dark, prefs.engine) {
        if (prefs.dynamicColor) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            } else {
                // <31：壁纸取色兜底（v1.9.2 的 wallpaperSeed），再按种子色生成。
                val wSeed = MainActivity.wallpaperSeed(ctx) ?: prefs.seed
                seedColorScheme(wSeed, dark, prefs.engine)
            }
        } else {
            seedColorScheme(prefs.seed, dark, prefs.engine)
        }
    }
    // 字体颜色最后应用（v1.9.6 的规则：字体颜色要赢）。
    // v1.9.8.1：字体颜色管全 App 所有文字——主文字 onSurface 用原色，
    // 副标题/日期/提示等次要文字 onSurfaceVariant 用原色 65%（看得出
    // 是字体颜色、又比标题淡一档，标题/副标题的层级还在）。只改
    // onSurface 时副标题全是种子色的灰，Max 手机上就是黑的——
    // 抽屉标题、实验页标题、警告框标题正文、行标题副标题都不跟，
    // 原因就是这些地方用的是 onSurfaceVariant / 写死的黑灰。
    // 真 Miuix 引擎时再把背景改成 Miuix 风格的浅灰、卡片面改成白色
    // （参考图 46200 的底色；主色仍是种子色/动态颜色，不动）。
    val scheme = remember(baseScheme, prefs.fontColor, prefs.engine) {
        var s = if (prefs.fontColor == MainActivity.DEFAULT_FONT_COLOR) baseScheme
        else baseScheme.copy(
            onSurface = Color(prefs.fontColor),
            onSurfaceVariant = Color(prefs.fontColor).copy(alpha = 0.65f),
        )
        if (prefs.engine == MainActivity.UI_ENGINE_MIUIX) {
            s = s.copy(
                background = if (dark) Color(0xFF1C1C1E) else Color(0xFFF5F5F7),
                surfaceContainerLowest = if (dark) Color(0xFF1C1C1E) else Color(0xFFF5F5F7),
                surfaceContainerLow = if (dark) Color(0xFF2C2C2E) else Color.White,
                surface = if (dark) Color(0xFF2C2C2E) else Color.White,
                surfaceContainer = if (dark) Color(0xFF2C2C2E) else Color.White,
            )
        }
        s
    }
    val mongoFont = mongolianFontFamily(prefs.localeTag)
    // 状态栏/导航栏跟随主题主色（旧版 View 主题里 statusBarColor 就是主色）。
    val activity = ctx as? android.app.Activity
    SideEffect {
        activity?.window?.let { w ->
            w.statusBarColor = scheme.primary.toArgb()
            w.navigationBarColor = scheme.surface.toArgb()
        }
    }
    CompositionLocalProvider(
        LocalFairuTheme provides prefs,
        LocalMongolianFont provides mongoFont,
        // v1.9.8.1：全 App 没写颜色的文字默认跟字体颜色走。
        // LocalContentColor 默认是黑，MaterialTheme 不给它设值，
        // 所以语言页名这种没写颜色的 Text 还是黑；在这里统一指到
        // 主题 onSurface（已是字体颜色）。Surface / Scaffold /
        // Button / 顶栏内部自己会再覆盖，不受影响。
        LocalContentColor provides scheme.onSurface,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = engineShapes(prefs.engine),
            typography = if (prefs.engine == MainActivity.UI_ENGINE_EXPRESSIVE) expressiveTypography()
            else androidx.compose.material3.Typography(),
            content = {
                // 真 Miuix 引擎：在 MaterialTheme 里再包一层 MiuixTheme，
                // Miuix 组件（Card / Switch / SmallTitle…）从这里取颜色与字型。
                if (prefs.engine == MainActivity.UI_ENGINE_MIUIX) {
                    MiuixFairuTheme { content() }
                } else {
                    content()
                }
            },
        )
    }
}
