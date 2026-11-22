package com.fairuman.filemanager

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.defaultTextStyles
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 真 Miuix 引擎（v1.9.8.1）：用 compose-miuix-ui/miuix 0.9.4 的
 * MiuixTheme + Card / Switch / SmallTitle 等真实组件，不再是按设计
 * token 做的 View 移植。
 *
 * 颜色跟随应用配色与动态颜色：主色（primary）取当前 Material3 配色的
 * primary（种子色 / 动态颜色算出的主色）；页面背景是 Miuix 风格的浅灰，
 * 卡片白色，行标题粗黑、副标题灰——造型按 Max 的 46200_1_ubxs.jpg。
 */
@Composable
fun MiuixFairuTheme(content: @Composable () -> Unit) {
    val prefs = LocalFairuTheme.current
    val systemDark = isSystemInDarkTheme()
    val dark = when (prefs.themeMode) {
        1 -> false
        2 -> true
        else -> systemDark
    }
    val scheme = MaterialTheme.colorScheme
    // Colors 的属性全是 internal，外部不能赋值——只能在 lightColorScheme /
    // darkColorScheme 调用时用具名参数覆盖（参数名与属性名一致）。
    val colors: Colors = remember(scheme, dark) {
        if (dark) darkColorScheme(
            primary = scheme.primary,
            onPrimary = Color.White,
            background = Color(0xFF1C1C1E),
            onBackground = scheme.onSurface,
            surface = Color(0xFF2C2C2E),
            onSurface = scheme.onSurface,
            surfaceContainer = Color(0xFF2C2C2E),
            surfaceContainerHigh = Color(0xFF3A3A3C),
            // 副标题灰：跟字体颜色走（Material 配色的 onSurfaceVariant
            // 已是字体颜色 65%；以前写死 0xFF98989D，字体颜色改了也不跟）。
            onSurfaceVariantSummary = scheme.onSurfaceVariant,
            // 分区小标签（SmallTitle）用的就是这个色，也跟字体颜色走。
            onBackgroundVariant = scheme.onSurfaceVariant,
        ) else lightColorScheme(
            // 主色跟随应用配色 / 动态颜色（种子色算出的 primary）。
            primary = scheme.primary,
            onPrimary = Color.White,
            // Miuix 风格：浅灰页面背景 + 白色卡片（亮色）。
            background = Color(0xFFF5F5F7),
            onBackground = scheme.onSurface,
            surface = Color.White,
            onSurface = scheme.onSurface,
            surfaceContainer = Color.White,
            surfaceContainerHigh = Color.White,
            // 副标题灰：跟字体颜色走（Material 配色的 onSurfaceVariant
            // 已是字体颜色 65%；以前写死 0xFF8E8E93，字体颜色改了也不跟）。
            onSurfaceVariantSummary = scheme.onSurfaceVariant,
            // 分区小标签（SmallTitle）用的就是这个色，也跟字体颜色走。
            onBackgroundVariant = scheme.onSurfaceVariant,
        )
    }
    MiuixTheme(colors = colors, textStyles = defaultTextStyles()) {
        // 注意：MiuixTheme 会把 LocalIndication 设成 MiuixIndication，它的
        // onAttach 在收到按压时会跑一段 Kotlin 2.4 编出的 typeSwitch
        // invokedynamic（bootstrap 是 Java 21 才有的
        // java.lang.runtime.SwitchBootstraps），d8 不脱糖、Android 运行时
        // 没有这个类，点一下 Miuix 组件就崩（19:08 / 19:22 两次 FATAL）。
        // 这里把 LocalIndication 覆盖成空实现：Card / Switch /
        // SmallTitle 等 Miuix 组件本体仍是真的，按压时的缩放反馈还在
        // （Miuix Pressable 自带的缩放不走 Indication），只不再画
        // MiuixIndication 那层按压遮罩。
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.foundation.LocalIndication provides NoOpIndication
        ) {
            content()
        }
    }
}

/** 空按压反馈：不画任何按压遮罩（新版 IndicationNodeFactory API；
 *  旧 Indication / IndicationInstance 在 CMP 1.12 里是 error 级废弃）。 */
private object NoOpIndication : androidx.compose.foundation.IndicationNodeFactory {
    override fun create(
        interactionSource: androidx.compose.foundation.interaction.InteractionSource
    ): androidx.compose.ui.node.DelegatingNode {
        return NoOpNode()
    }

    override fun hashCode(): Int = 0x4D495558 // "MIUX"
    override fun equals(other: Any?): Boolean = other === this
}

private class NoOpNode : androidx.compose.ui.node.DelegatingNode()

/** 当前引擎是不是真 Miuix（供各页分支渲染 Miuix 组件）。 */
@Composable
fun isMiuixEngine(): Boolean =
    LocalFairuTheme.current.engine == MainActivity.UI_ENGINE_MIUIX
