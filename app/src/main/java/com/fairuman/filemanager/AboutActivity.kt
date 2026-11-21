package com.fairuman.filemanager

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 关于页（简单版 1.4.1 起，v1.9.0⑩ 重做）：顶部居中应用图标 + 名称；
 * 版本号 / 开源许可证（MIT 全文）/ 用户协议 / 隐私权政策 /
 * 开源组件（逐个列出名称、版本、许可证、链接）/ 免责声明；
 * 特别鸣谢（Muse + MT管理器，点后打开官网；每行下面写清参考与感谢的内容）。
 * 协议正文只有简体中文和英文版；其他语言显示中文版并注明。
 */
class AboutActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashHandler.install(this)
        super.onCreate(savedInstanceState)
        try {
            onCreateInner()
        } catch (e: Throwable) {
            CrashHandler.writeLog(this, e)
            try {
                MaterialAlertDialogBuilder(this)
                    .setTitle("启动失败")
                    .setMessage("启动时出错了，错误已保存到 crash.log。\n\n" + CrashHandler.logPathText(this) + "\n\n错误：" + e.toString())
                    .setPositiveButton(R.string.ok) { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
            } catch (_: Exception) { }
            try { finish() } catch (_: Exception) { }
        }
    }

    private fun onCreateInner() {
        setContentView(R.layout.activity_about)
        val toolbar = findViewById<MaterialToolbar>(R.id.aboutToolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        // 版本号：从 PackageManager 读，版本升级不用改代码。
        val rowVersion = findViewById<View>(R.id.rowVersion)
        rowVersion.findViewById<ImageView>(R.id.rowIcon).setImageResource(R.drawable.ic_info)
        rowVersion.findViewById<TextView>(R.id.rowTitle).setText(R.string.version_number)
        val sub = rowVersion.findViewById<TextView>(R.id.rowSub)
        try {
            val pi = packageManager.getPackageInfo(packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode else pi.versionCode.toLong()
            sub.text = "${pi.versionName} ($code)"
        } catch (_: Exception) {
            sub.text = "1.6.0 (8)"
        }
        rowVersion.findViewById<ImageView>(R.id.rowChevron).visibility = View.GONE
        rowVersion.isClickable = false

        // 开源许可证：MIT，点开看全文。
        val rowLicense = findViewById<View>(R.id.rowLicense)
        rowLicense.findViewById<ImageView>(R.id.rowIcon).setImageResource(R.drawable.ic_drawer_editor)
        rowLicense.findViewById<TextView>(R.id.rowTitle).setText(R.string.open_source_license)
        rowLicense.findViewById<TextView>(R.id.rowSub).text = "MIT License"
        rowLicense.setOnClickListener {
            showTextDialog(getString(R.string.open_source_license), getString(R.string.license_text), R.drawable.ic_drawer_editor)
        }

        // 用户协议 / 隐私权政策：点开看正文（只有中英文版）。
        val rowAgreement = findViewById<View>(R.id.rowAgreement)
        rowAgreement.findViewById<ImageView>(R.id.rowIcon).setImageResource(R.drawable.ic_text)
        rowAgreement.findViewById<TextView>(R.id.rowTitle).setText(R.string.user_agreement)
        rowAgreement.findViewById<TextView>(R.id.rowSub).visibility = View.GONE
        rowAgreement.setOnClickListener {
            showTextDialog(getString(R.string.user_agreement), localizedText(R.string.agreement_text), R.drawable.ic_text)
        }

        val rowPrivacy = findViewById<View>(R.id.rowPrivacy)
        rowPrivacy.findViewById<ImageView>(R.id.rowIcon).setImageResource(R.drawable.ic_shield)
        rowPrivacy.findViewById<TextView>(R.id.rowTitle).setText(R.string.privacy_policy)
        rowPrivacy.findViewById<TextView>(R.id.rowSub).visibility = View.GONE
        rowPrivacy.setOnClickListener {
            showTextDialog(getString(R.string.privacy_policy), localizedText(R.string.privacy_text), R.drawable.ic_shield)
        }

        // v1.9.0⑩ 开源组件：逐个列出实际用到的组件（名称、版本、许可证、链接）。
        val rowOss = findViewById<View>(R.id.rowOssComponents)
        rowOss.findViewById<ImageView>(R.id.rowIcon).setImageResource(R.drawable.ic_code)
        rowOss.findViewById<TextView>(R.id.rowTitle).setText(R.string.oss_components)
        rowOss.findViewById<TextView>(R.id.rowSub).text = "Apache License 2.0 · SIL OFL 1.1"
        rowOss.setOnClickListener {
            showTextDialog(getString(R.string.oss_components), localizedText(R.string.oss_components_text), R.drawable.ic_code)
        }

        // v1.9.0⑩ 免责声明：按“现状”提供、文件操作风险自担、与第三方无关等。
        val rowDisclaimer = findViewById<View>(R.id.rowDisclaimer)
        rowDisclaimer.findViewById<ImageView>(R.id.rowIcon).setImageResource(R.drawable.ic_info)
        rowDisclaimer.findViewById<TextView>(R.id.rowTitle).setText(R.string.disclaimer)
        rowDisclaimer.findViewById<TextView>(R.id.rowSub).visibility = View.GONE
        rowDisclaimer.setOnClickListener {
            showTextDialog(getString(R.string.disclaimer), localizedText(R.string.disclaimer_text), R.drawable.ic_info)
        }

        // 特别鸣谢：Muse 图标 + 名称，点后打开官网 https://muse.ai。
        val rowMuse = findViewById<View>(R.id.rowMuse)
        val museIcon = rowMuse.findViewById<ImageView>(R.id.rowIcon)
        museIcon.setImageResource(R.drawable.ic_muse)
        museIcon.imageTintList = null // Muse logo 自带颜色，别被行图标的 tint 盖掉
        rowMuse.findViewById<TextView>(R.id.rowTitle).text = "Muse"
        rowMuse.findViewById<TextView>(R.id.rowSub).text = "https://muse.ai"
        rowMuse.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://muse.ai")))
            } catch (_: Exception) {
                Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
            }
        }

        // v1.9.0⑩ 特别鸣谢：MT管理器——图标是官网 favicon 的真实“MT”字标
        // （https://mt2.cn/favicon.ico，黑字透明底；不取消 tint，让它随主题变色）。
        val rowMt = findViewById<View>(R.id.rowMtManager)
        rowMt.findViewById<ImageView>(R.id.rowIcon).setImageResource(R.drawable.ic_mt)
        rowMt.findViewById<TextView>(R.id.rowTitle).text = "MT管理器"
        rowMt.findViewById<TextView>(R.id.rowSub).text = "https://mt2.cn/"
        rowMt.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://mt2.cn/")))
            } catch (_: Exception) {
                Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
            }
        }

        // v1.9.4 特别鸣谢新增：MP管理器（MP-Manager）——双窗格开源文件管理器。
        // 图标是其 GitHub 仓库的真实启动图标（teal 文件夹 + 黑底白字 MP，
        // 全彩图标，取消 tint）；点后跳它的 GitHub 仓库。
        // 与上面的 MT管理器 并存，MT 只保留这一个条目。
        val rowMp = findViewById<View>(R.id.rowMpManager)
        rowMp.findViewById<ImageView>(R.id.rowIcon).setImageResource(R.drawable.ic_mp)
        rowMp.findViewById<ImageView>(R.id.rowIcon).imageTintList = null
        rowMp.findViewById<TextView>(R.id.rowTitle).text = "MP管理器"
        rowMp.findViewById<TextView>(R.id.rowSub).text = "https://github.com/AbdurazaaqMohammed/MP-Manager"
        rowMp.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/AbdurazaaqMohammed/MP-Manager")))
            } catch (_: Exception) {
                Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 协议正文：当前语言是中文或英文就直接显示，否则显示中文版并在前面注明。 */
    private fun localizedText(resId: Int): String {
        val tag = LanguageActivity.currentTag(this)
        return if (tag.startsWith("zh", ignoreCase = true) || tag.equals("en", ignoreCase = true)) {
            getString(resId)
        } else {
            getString(R.string.chinese_version_note) + "\n\n" + getString(resId)
        }
    }

    /** 长文本弹窗：可滚动的 TextView；有图标就显示在标题旁。 */
    private fun showTextDialog(title: String, text: String, iconRes: Int? = null) {
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()
        val tv = TextView(this).apply {
            this.text = text
            textSize = 15f
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        val scroll = ScrollView(this).apply { addView(tv) }
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton(R.string.ok, null)
        if (iconRes != null) builder.setIcon(iconRes)
        builder.show()
    }
}
