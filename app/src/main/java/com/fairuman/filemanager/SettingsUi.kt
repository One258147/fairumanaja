package com.fairuman.filemanager

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SwitchDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ---------- 设置类页面共享组件（v1.9.8.1 Compose） ----------
// Miuix 引擎时用真 miuix 0.9.4 组件渲染（造型按 46200 参考图：
// 大号粗黑标题、灰色小字分区标签、白色大圆角卡片、粗黑行标题、灰色副标题、
// 蓝色 iOS 式开关、浅灰页面背景）；MD3 / M3 Expressive 用下面的默认实现。

/** 当前引擎是不是 M3 Expressive（v1.9.7.4：共享组件按引擎放大/加粗/变圆）。 */
@Composable
fun isExpressiveEngine(): Boolean =
    LocalFairuTheme.current.engine == MainActivity.UI_ENGINE_EXPRESSIVE

/** 顶栏：MD3 是主色底 + 白字标题；Miuix 是浅灰底 + 大号粗黑标题（参考图）。
 * M3 Expressive：主色底 + 更大更粗的标题（24sp）。 */
@Composable
fun FairuPageTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    if (isMiuixEngine()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MiuixTheme.colorScheme.background)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                Icon(
                    painterResource(R.drawable.ic_arrow_back),
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurface
                )
            }
            top.yukonga.miuix.kmp.basic.Text(
                text = title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            actions()
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.IconButton(onClick = onBack) {
            Icon(
                painterResource(R.drawable.ic_arrow_back),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
        Text(
            title,
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = if (isExpressiveEngine()) 24.sp else 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        actions()
    }
}

/** 白色大圆角卡片（设置/外观/实验性功能页的分组容器）。
 * M3 Expressive：圆角 28dp（MD3 是 16dp），一眼能看出区别。 */
@Composable
fun FairuCard(content: @Composable () -> Unit) {
    if (isMiuixEngine()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            cornerRadius = 20.dp,
            colors = CardDefaults.defaultColors()
        ) {
            // v1.9.8.1：卡片里的默认文字色跟字体颜色走。miuix Card 是
            // Surface，默认 contentColor 按白色底算出来是黑，卡片里没写
            // 颜色的 Text（主题模式/应用配色行标题等）就还是黑色；这里
            // 把 LocalContentColor 指到主题 onSurface（已是字体颜色）。
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onSurface
            ) {
                content()
            }
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(if (isExpressiveEngine()) 28.dp else 16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 4.dp)
    ) {
        content()
    }
}

/** 设置行：图标 + 粗黑标题 + 灰色副标题 + 右侧内容（开关/箭头/无）。 */
@Composable
fun FairuRow(
    iconRes: Int,
    title: String,
    sub: String? = null,
    // v1.9.7.2：标准解释小字（显示在 sub 下面一行，更小更淡）。
    hint: String? = null,
    showChevron: Boolean = true,
    iconTint: Color? = null,
    // v1.9.7.8：图标放浅色圆圈里（设置页参考图样式）；默认不开，其他页不受影响。
    iconCircle: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val subColor = if (isMiuixEngine()) {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val titleColor = if (isMiuixEngine()) {
        MiuixTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // v1.9.7.8：设置页图标放浅色圆圈里（圆圈色跟应用配色走，不照抄参考图的绿）。
        val iconTintColor = iconTint ?: MaterialTheme.colorScheme.primary
        if (iconCircle) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconTintColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(iconRes),
                    contentDescription = null,
                    tint = iconTintColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        } else {
            Icon(
                painterResource(iconRes),
                contentDescription = null,
                tint = iconTintColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = if (isExpressiveEngine()) 18.sp else 16.sp, fontWeight = FontWeight.Bold, color = titleColor)
            if (sub != null) {
                Spacer(Modifier.height(2.dp))
                Text(sub, fontSize = 13.sp, color = subColor)
            }
            if (hint != null) {
                Spacer(Modifier.height(2.dp))
                Text(hint, fontSize = 12.sp, color = subColor)
            }
        }
        if (trailing != null) {
            trailing()
        } else if (showChevron) {
            Icon(
                painterResource(R.drawable.ic_arrow_forward),
                contentDescription = null,
                tint = subColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** 开关行（整行和开关都能点，点一下翻转偏好；状态永远以偏好为准）。
 * enabled=false 时整行不可点、开关变灰（主题设置页 Miuix 下 M3 Expressive
 * 开关用）；M3 Expressive 时开关 thumb 里带 ✓/✗（参考图的大开关造型）。 */
@Composable
fun FairuSwitchRow(
    iconRes: Int,
    title: String,
    sub: String? = null,
    // v1.9.7.2：标准解释小字（显示在 sub 下面一行）。
    hint: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    // v1.9.7.8：图标放浅色圆圈里（设置页参考图样式）；默认不开。
    iconCircle: Boolean = false,
    onToggle: () -> Unit
) {
    if (isMiuixEngine()) {
        FairuRow(
            iconRes = iconRes,
            title = title,
            sub = sub,
            hint = hint,
            showChevron = false,
            iconCircle = iconCircle,
            trailing = {
                top.yukonga.miuix.kmp.basic.Switch(
                    checked = checked,
                    onCheckedChange = { if (enabled) onToggle() },
                    colors = SwitchDefaults.switchColors()
                )
            },
            onClick = if (enabled) onToggle else null
        )
        return
    }
    FairuRow(
        iconRes = iconRes,
        title = title,
        sub = sub,
        hint = hint,
        showChevron = false,
        iconCircle = iconCircle,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = { if (enabled) onToggle() },
                enabled = enabled,
                // M3 Expressive：thumb 里带 ✓/✗（参考图）。
                thumbContent = if (isExpressiveEngine()) {
                    {
                        Icon(
                            painterResource(if (checked) R.drawable.ic_check else R.drawable.ic_close),
                            contentDescription = null,
                            tint = if (checked) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else null
            )
        },
        onClick = if (enabled) onToggle else null
    )
}

/** 灰色小字分区标签（Miuix 用真 SmallTitle；MD3 下也是同一灰色小字）。
 * color：传了就用该色（主题设置页的分区标签用主色，像参考图的绿色小字）。 */
@Composable
fun FairuSectionLabel(text: String, color: Color? = null) {
    Box(Modifier.fillMaxWidth()) {
        if (isMiuixEngine()) {
            SmallTitle(text = text)
            return@Box
        }
        Text(
            text,
            fontSize = 13.sp,
            color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

/** 页面底部小提示（灰色小字）。 */
@Composable
fun FairuHint(text: String) {
    val hintColor = if (isMiuixEngine()) {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text,
        fontSize = 13.sp,
        color = hintColor,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

/** 页面根布局：浅灰背景 + 可滚动列（设置类页面通用）。 */
@Composable
fun FairuPage(content: @Composable () -> Unit) {
    val bg = if (isMiuixEngine()) {
        MiuixTheme.colorScheme.background
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
    ) {
        content()
    }
}

/** 设置类页面根布局的背景色：Miuix 用浅灰，MD3 用 surfaceContainerLowest。
 *  各页根 Column 必须刷这个背景，否则窗口底色（淡粉）会从卡片下面露出来。 */
@Composable
fun fairuPageBackground(): Color = if (isMiuixEngine()) {
    MiuixTheme.colorScheme.background
} else {
    MaterialTheme.colorScheme.surfaceContainerLowest
}

// ---------- 对话框 / 菜单壳（v1.9.8.1：所有界面跟 UI 引擎走） ----------
// Miuix 引擎时：对话框白色 20dp 大圆角 + 粗黑标题 + 深灰正文（参考图
// 46200 的白色卡片造型；按钮仍是主色，跟应用配色/动态颜色走）；
// MD3 / M3 Expressive 时直接用默认 AlertDialog，一字不改。
// v1.9.7.5：多彩对话框已删——对话框一律用主题色，不再有中性色包裹。

/** 对话框壳：参数与 AlertDialog 一致，直接替换调用处即可。 */
@Composable
fun FairuAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null
) {
    if (isMiuixEngine()) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White,
            // v1.9.8.1：标题/正文跟字体颜色走（以前写死黑/深灰，
            // 字体颜色改了警告框标题正文还是黑色，Max 1.9.7 截图指出的）。
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text
    )
}

/** 下拉菜单壳：Miuix 时白色 16dp 圆角；MD3 / M3 Expressive 用默认 DropdownMenu。 */
@Composable
fun FairuDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    if (isMiuixEngine()) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White,
            content = content
        )
        return
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        content = content
    )
}

/** 顶栏右侧图标的 tint：Miuix 浅灰底上用深色，MD3 主色底上用白色。 */
@Composable
fun fairuTopBarIconTint(): Color = if (isMiuixEngine()) {
    MiuixTheme.colorScheme.onSurface
} else {
    MaterialTheme.colorScheme.onPrimary
}
