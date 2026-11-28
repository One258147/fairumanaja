package com.fairuman.filemanager

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 关于页（v1.9.8.1 Compose）：顶部居中应用图标 + 名称；
 * 版本号 / 开源许可证（MIT 全文）/ 用户协议 / 隐私权政策 /
 * 开源组件（逐个列出名称、版本、许可证、链接）/ 免责声明；
 * 特别鸣谢（Muse + MT管理器 + MP管理器，点后打开官网；每行下面写清参考与感谢的内容）。
 * 协议正文只有简体中文和英文版；其他语言显示中文版并注明。
 */
class AboutActivity : BaseActivity() {

    private var textDialog by mutableStateOf<Triple<String, String, Int>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashHandler.install(this)
        super.onCreate(savedInstanceState)
        try {
            setContent {
                FairuTheme { AboutScreen() }
            }
        } catch (e: Throwable) {
            CrashHandler.writeLog(this, e)
            try { finish() } catch (_: Exception) { }
        }
    }

    /** 协议正文：当前语言是中文或英文就直接显示，否则显示中文版并在前面注明。 */
    private fun localizedText(resId: Int): String {
        val tag = LanguageActivity.effectiveTag(this)
        return if (tag.startsWith("zh", ignoreCase = true) || tag.equals("en", ignoreCase = true)) {
            getString(resId)
        } else {
            getString(R.string.chinese_version_note) + "\n\n" + getString(resId)
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
        }
    }

    @Composable
    private fun AboutScreen() {
        val scrollState = rememberScrollState()
        Column(Modifier.fillMaxSize().background(fairuPageBackground())) {
            FairuPageTopBar(title = stringResource(R.string.about), onBack = { finish() })
            Column(
                Modifier
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // 第一张卡片：图标 + 名称（居中）。
                FairuCard {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 注意：不能用 R.mipmap.ic_launcher——那是自适应图标 XML，
                        // painterResource 加载不了会崩（Only VectorDrawables…）。
                        // 用前景矢量 ic_app_icon（开屏动画用的同一个图标）。
                        Image(
                            painterResource(R.drawable.ic_app_icon),
                            contentDescription = null,
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.app_name), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            try {
                                val pi = packageManager.getPackageInfo(packageName, 0)
                                val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode else pi.versionCode.toLong()
                                "${pi.versionName} ($code)"
                            } catch (_: Exception) {
                                "1.9.7.1 (22)"
                            },
                            fontSize = 13.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                // 详细介绍（只出现一次）。
                FairuSectionLabel(stringResource(R.string.about_intro_title))
                FairuCard {
                    Text(
                        stringResource(R.string.about_intro_text),
                        fontSize = 15.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                FairuSectionLabel(stringResource(R.string.special_thanks))
                FairuCard {
                    // Muse：只写感谢，不写参考；图标自带颜色，不染色。
                    FairuRow(
                        iconRes = R.drawable.ic_muse,
                        iconTint = androidx.compose.ui.graphics.Color.Unspecified,
                        title = "Muse",
                        sub = "https://muse.ai",
                        onClick = { openUrl("https://muse.ai") }
                    )
                    Text(
                        stringResource(R.string.thanks_muse_desc),
                        fontSize = 13.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    FairuRow(
                        iconRes = R.drawable.ic_mt,
                        title = "MT管理器",
                        sub = "https://mt2.cn/",
                        onClick = { openUrl("https://mt2.cn/") }
                    )
                    Text(
                        stringResource(R.string.thanks_mt_desc),
                        fontSize = 13.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    // MP 图标是全彩图标，不染色。
                    FairuRow(
                        iconRes = R.drawable.ic_mp,
                        iconTint = androidx.compose.ui.graphics.Color.Unspecified,
                        title = "MP管理器",
                        sub = "https://github.com/AbdurazaaqMohammed/MP-Manager",
                        onClick = { openUrl("https://github.com/AbdurazaaqMohammed/MP-Manager") }
                    )
                    Text(
                        stringResource(R.string.thanks_mp_desc),
                        fontSize = 13.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                FairuSectionLabel(stringResource(R.string.open_source_license))
                FairuCard {
                    FairuRow(
                        iconRes = R.drawable.ic_drawer_editor,
                        title = stringResource(R.string.open_source_license),
                        sub = "MIT License",
                        onClick = { textDialog = Triple(getString(R.string.open_source_license), getString(R.string.license_text), R.drawable.ic_drawer_editor) }
                    )
                    FairuRow(
                        iconRes = R.drawable.ic_text,
                        title = stringResource(R.string.user_agreement),
                        onClick = { textDialog = Triple(getString(R.string.user_agreement), localizedText(R.string.agreement_text), R.drawable.ic_text) }
                    )
                    FairuRow(
                        iconRes = R.drawable.ic_shield,
                        title = stringResource(R.string.privacy_policy),
                        onClick = { textDialog = Triple(getString(R.string.privacy_policy), localizedText(R.string.privacy_text), R.drawable.ic_shield) }
                    )
                    FairuRow(
                        iconRes = R.drawable.ic_code,
                        title = stringResource(R.string.oss_components),
                        sub = "Apache License 2.0 · SIL OFL 1.1",
                        onClick = { textDialog = Triple(getString(R.string.oss_components), localizedText(R.string.oss_components_text), R.drawable.ic_code) }
                    )
                    FairuRow(
                        iconRes = R.drawable.ic_info,
                        title = stringResource(R.string.disclaimer),
                        onClick = { textDialog = Triple(getString(R.string.disclaimer), localizedText(R.string.disclaimer_text), R.drawable.ic_info) }
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        textDialog?.let { (title, text, iconRes) ->
            FairuAlertDialog(
                onDismissRequest = { textDialog = null },
                icon = { Icon(painterResource(iconRes), contentDescription = null) },
                title = { Text(title) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(text, fontSize = 15.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { textDialog = null }) { Text(stringResource(R.string.ok)) }
                }
            )
        }
    }
}
