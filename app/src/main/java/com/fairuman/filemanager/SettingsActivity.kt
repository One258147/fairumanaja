package com.fairuman.filemanager

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * 设置页（简单版 1.4.0，v1.9.0 改）：大标题“设置”+ 卡片式分组，三行：
 * 多语言设置（点进单独一页选，右上角 ✓ 确认才切换）、外观（点进单独的外观页，
 * 主题模式 / 动态颜色 / 应用配色都在里面）、
 * 关于（进单独的关于页）。动态配色已在 1.4.0 去掉。
 */
class SettingsActivity : BaseActivity() {

    private lateinit var prefs: SharedPreferences

    private lateinit var langSub: TextView
    private lateinit var appearanceSub: TextView
    private lateinit var experimentalSub: TextView
    private lateinit var splashSwitch: MaterialSwitch

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashHandler.install(this)
        // 语言/主题/配色由 BaseActivity.onCreate 在 super.onCreate 之前统一应用。
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
        setContentView(R.layout.activity_settings)
        prefs = getSharedPreferences("fairu_prefs", MODE_PRIVATE)

        val toolbar = findViewById<MaterialToolbar>(R.id.settingsToolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        // 多语言设置：进单独一页选，右上角 ✓ 确认才切换。
        val rowLanguage = findViewById<View>(R.id.rowLanguage)
        rowLanguage.findViewById<ImageView>(R.id.rowIcon).setImageResource(R.drawable.ic_language)
        rowLanguage.findViewById<TextView>(R.id.rowTitle).setText(R.string.language_settings)
        langSub = rowLanguage.findViewById(R.id.rowSub)
        rowLanguage.setOnClickListener {
            startActivity(android.content.Intent(this, LanguageActivity::class.java))
        }

        // 外观：进单独的外观页（主题模式 / 动态颜色 / 应用配色）。
        val rowAppearance = findViewById<View>(R.id.rowAppearance)
        rowAppearance.findViewById<ImageView>(R.id.rowIcon).setImageResource(R.drawable.ic_palette)
        rowAppearance.findViewById<TextView>(R.id.rowTitle).setText(R.string.appearance)
        appearanceSub = rowAppearance.findViewById(R.id.rowSub)
        rowAppearance.setOnClickListener {
            startActivity(android.content.Intent(this, AppearanceActivity::class.java))
        }

        // v1.9.4 开屏动画开关（默认开）：整行和开关本身都能点，点一下翻转
        // 偏好；开关状态永远以偏好为准（isSaveEnabled=false + onResume 同步，
        // 照 AppearanceActivity 动态颜色开关的修法，防状态漂移）。
        val rowSplash = findViewById<View>(R.id.rowSplash)
        rowSplash.findViewById<ImageView>(R.id.rowIcon).setImageResource(R.drawable.ic_play)
        rowSplash.findViewById<TextView>(R.id.rowTitle).setText(R.string.splash_animation)
        rowSplash.findViewById<TextView>(R.id.rowSub).visibility = View.GONE
        rowSplash.findViewById<ImageView>(R.id.rowChevron).visibility = View.GONE
        splashSwitch = rowSplash.findViewById(R.id.rowSwitch)
        splashSwitch.visibility = View.VISIBLE
        splashSwitch.isSaveEnabled = false
        splashSwitch.isChecked = prefs.getBoolean(MainActivity.PREF_SPLASH, true)
        val toggleSplash = {
            val on = !prefs.getBoolean(MainActivity.PREF_SPLASH, true)
            prefs.edit().putBoolean(MainActivity.PREF_SPLASH, on).apply()
            splashSwitch.isChecked = on
        }
        rowSplash.setOnClickListener { toggleSplash() }
        splashSwitch.setOnClickListener { toggleSplash() }

        // 关于：进单独的关于页（版本 / 开源许可证 / 用户协议 / 隐私权政策 / 特别鸣谢）。
        // v1.4.1：版本号改从 PackageManager 读，之前硬编码 "1.4.0"，升版后这里会显示旧版本。
        val rowAbout = findViewById<View>(R.id.rowAbout)
        rowAbout.findViewById<ImageView>(R.id.rowIcon).setImageResource(R.drawable.ic_info)
        rowAbout.findViewById<TextView>(R.id.rowTitle).setText(R.string.about)
        rowAbout.findViewById<TextView>(R.id.rowSub).text = try {
            val pi = packageManager.getPackageInfo(packageName, 0)
            getString(R.string.version) + " " + pi.versionName
        } catch (_: Exception) {
            getString(R.string.version) + " 1.6.0"
        }
        rowAbout.setOnClickListener {
            startActivity(android.content.Intent(this, AboutActivity::class.java))
        }

        // v1.9.6 实验性功能：进实验性功能页（UI 引擎）。
        // Max 20:01：点进之前先弹警告框，详细说明可能造成的后果，
        // 确认才进，取消不进；每次点都弹。
        val rowExperimental = findViewById<View>(R.id.rowExperimental)
        rowExperimental.findViewById<ImageView>(R.id.rowIcon).setImageResource(R.drawable.ic_flask)
        rowExperimental.findViewById<TextView>(R.id.rowTitle).setText(R.string.experimental_features)
        experimentalSub = rowExperimental.findViewById(R.id.rowSub)
        rowExperimental.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.experimental_warning_title)
                .setIcon(R.drawable.ic_flask)
                .setMessage(R.string.experimental_warning_msg)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm) { _, _ ->
                    startActivity(android.content.Intent(this, ExperimentalActivity::class.java))
                }
                .show()
        }

        refreshSubs()
    }

    override fun onResume() {
        super.onResume()
        // 从语言页回来时副标题可能变了，刷新一次；开屏开关也按偏好重同步。
        try {
            refreshSubs()
            if (::splashSwitch.isInitialized) {
                splashSwitch.isChecked = prefs.getBoolean(MainActivity.PREF_SPLASH, true)
            }
        } catch (_: Exception) { }
    }

    private fun refreshSubs() {
        if (!::langSub.isInitialized) return
        langSub.text = LanguageActivity.nativeName(LanguageActivity.currentTag(this))
        appearanceSub.text = when (prefs.getInt(MainActivity.PREF_THEME_MODE, 0)) {
            1 -> getString(R.string.theme_light)
            2 -> getString(R.string.theme_dark)
            else -> getString(R.string.theme_system)
        }
        if (::experimentalSub.isInitialized) {
            experimentalSub.text = MainActivity.engineDisplayName(
                this, prefs.getInt(MainActivity.PREF_UI_ENGINE, MainActivity.UI_ENGINE_MD3)
            )
        }
    }
}
