package com.fairuman.filemanager

import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.widget.ImageViewCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * 外观页（v1.9.0 起）：大标题“外观”+ 一张卡片，三行：
 * 主题模式（点行展开横向三选项，点选立即生效）、动态颜色（开关，
 * 打开先弹确认框，确认才开；v1.9.2 起任何版本都能开，12 以下走壁纸取色兜底）、
 * 应用配色（点开 5 列网格选 25 个种子色之一，点确认才保存；动态颜色开着时
 * 这一行变灰、点不了，副标题提示先关动态颜色）。
 * 种子色 / 动态颜色键与 MainActivity.applyPrefs 对接：
 * "dynamic_color"（Boolean，默认 false）、"seed_color"（Int）。
 * 配色应用由 BaseActivity 统一处理；这里只负责保存与 recreate()。
 */
class AppearanceActivity : BaseActivity() {

    companion object {
        const val PREF_DYNAMIC_COLOR = "dynamic_color"
        const val PREF_SEED_COLOR = "seed_color"
        const val DEFAULT_SEED_COLOR = 0xFF0D47A1.toInt()

        /** 52 个种子色（顺序固定，与任务约定一致）：前 25 不动，后面补到 52。 */
        val SEED_COLORS = listOf(
            0xFF0D47A1.toInt(), 0xFFB3261E.toInt(), 0xFFC2185B.toInt(), 0xFF7B1FA2.toInt(),
            0xFF512DA8.toInt(), 0xFF303F9F.toInt(), 0xFF1976D2.toInt(), 0xFF0288D1.toInt(),
            0xFF0097A7.toInt(), 0xFF00796B.toInt(), 0xFF388E3C.toInt(), 0xFF689F38.toInt(),
            0xFFAFB42B.toInt(), 0xFFF9A825.toInt(), 0xFFFF8F00.toInt(), 0xFFF57C00.toInt(),
            0xFFE64A19.toInt(), 0xFF5D4037.toInt(), 0xFF795548.toInt(), 0xFF546E7A.toInt(),
            0xFF6A1B9A.toInt(), 0xFFAD1457.toInt(), 0xFF00897B.toInt(), 0xFF7CB342.toInt(),
            0xFF33691E.toInt(), 0xFF00BCD4.toInt(), 0xFF009688.toInt(), 0xFFFFFFFF.toInt(),
            0xFF000000.toInt(), 0xFF9E9E9E.toInt(), 0xFFF8BBD0.toInt(), 0xFFE91E63.toInt(),
            0xFF616161.toInt(), 0xFF90CAF9.toInt(), 0xFFA5D6A7.toInt(), 0xFFFFF59D.toInt(),
            0xFFCE93D8.toInt(), 0xFFFF9800.toInt(), 0xFF1A237E.toInt(), 0xFF1B5E20.toInt(),
            0xFF4E342E.toInt(), 0xFFFFC107.toInt(), 0xFFF44336.toInt(), 0xFFEC407A.toInt(),
            0xFF9C27B0.toInt(), 0xFF3F51B5.toInt(), 0xFF03A9F4.toInt(), 0xFFCDDC39.toInt(),
            0xFFFFB300.toInt(), 0xFF8D6E63.toInt(), 0xFF607D8B.toInt(), 0xFF00695C.toInt()
        )
    }

    private lateinit var prefs: SharedPreferences

    private lateinit var themeSub: TextView
    private lateinit var themeChevron: ImageView
    private lateinit var themeOptions: View
    private lateinit var themeGroup: RadioGroup
    private var themeExpanded = false
    private var suppressThemeChange = false

    private lateinit var dynamicSwitch: MaterialSwitch

    private lateinit var rowColor: View
    private lateinit var colorSub: TextView

    private lateinit var rowFont: View
    private lateinit var fontSub: TextView

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
        setContentView(R.layout.activity_appearance)
        prefs = getSharedPreferences("fairu_prefs", MODE_PRIVATE)

        val toolbar = findViewById<MaterialToolbar>(R.id.appearanceToolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        // 主题模式：点行展开/收起卡片内的横向三选项，点选立即生效。
        val rowTheme = findViewById<View>(R.id.rowThemeMode)
        rowTheme.findViewById<ImageView>(R.id.rowIcon).setImageResource(R.drawable.ic_theme)
        rowTheme.findViewById<TextView>(R.id.rowTitle).setText(R.string.theme_mode)
        themeSub = rowTheme.findViewById(R.id.rowSub)
        themeChevron = rowTheme.findViewById(R.id.rowChevron)
        themeChevron.setImageResource(R.drawable.ic_arrow_up)
        themeOptions = findViewById(R.id.themeOptions)
        themeGroup = findViewById(R.id.themeGroup)
        rowTheme.setOnClickListener { toggleThemeOptions() }
        // 先按保存值选中，再挂监听，避免初始化时触发一次保存/recreate。
        suppressThemeChange = true
        themeGroup.check(
            when (prefs.getInt(MainActivity.PREF_THEME_MODE, 0)) {
                1 -> R.id.rbThemeLight
                2 -> R.id.rbThemeDark
                else -> R.id.rbThemeSystem
            }
        )
        suppressThemeChange = false
        themeGroup.setOnCheckedChangeListener { _, checkedId ->
            if (suppressThemeChange) return@setOnCheckedChangeListener
            val which = when (checkedId) {
                R.id.rbThemeLight -> 1
                R.id.rbThemeDark -> 2
                else -> 0
            }
            prefs.edit().putInt(MainActivity.PREF_THEME_MODE, which).apply()
            AppCompatDelegate.setDefaultNightMode(
                when (which) {
                    1 -> AppCompatDelegate.MODE_NIGHT_NO
                    2 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
            refreshSubs()
            recreate()
        }

        // 动态颜色（v1.9.2 重改）：取消 Android 12 限制，任何版本都能开
        // （12+ 走系统动态取色，12 以下走壁纸取色兜底）。
        // 开关显示必须以持久化偏好为准：v1.9.1 的 bug 是 recreate 后 Switch 的
        // saved instance state 把旧的 OFF 恢复回来、盖掉了偏好的 ON。
        // 修法：isSaveEnabled=false（不保存/恢复开关状态）+ onResume 里按偏好
        // 再同步一次；行点击和开关点击都按偏好（真实状态）走，不读开关显示值。
        val rowDynamic = findViewById<View>(R.id.rowDynamicColor)
        rowDynamic.findViewById<ImageView>(R.id.rowIcon).setImageResource(R.drawable.ic_image)
        rowDynamic.findViewById<TextView>(R.id.rowTitle).setText(R.string.dynamic_color)
        rowDynamic.findViewById<TextView>(R.id.rowSub).visibility = View.GONE
        rowDynamic.findViewById<ImageView>(R.id.rowChevron).visibility = View.GONE
        dynamicSwitch = rowDynamic.findViewById(R.id.rowSwitch)
        dynamicSwitch.visibility = View.VISIBLE
        dynamicSwitch.isSaveEnabled = false
        dynamicSwitch.isChecked = prefs.getBoolean(PREF_DYNAMIC_COLOR, false)
        rowDynamic.setOnClickListener { onDynamicColorTap() }
        dynamicSwitch.setOnClickListener { onDynamicColorTap() }

        // 应用配色：点开 5 列网格选种子色，点确认才保存。
        // 动态颜色开着时这一行变灰、点不了（互斥联动，见 refreshColorRowState）。
        rowColor = findViewById(R.id.rowAppColor)
        rowColor.findViewById<ImageView>(R.id.rowIcon).setImageResource(R.drawable.ic_palette)
        rowColor.findViewById<TextView>(R.id.rowTitle).setText(R.string.app_color)
        colorSub = rowColor.findViewById(R.id.rowSub)
        rowColor.setOnClickListener { showColorDialog() }

        // 字体颜色（v1.9.6）：点开按基础/浅/深三组列出的 52 色，点确认才保存。
        rowFont = findViewById(R.id.rowFontColor)
        rowFont.findViewById<ImageView>(R.id.rowIcon).setImageResource(R.drawable.ic_font_color)
        rowFont.findViewById<TextView>(R.id.rowTitle).setText(R.string.font_color)
        fontSub = rowFont.findViewById(R.id.rowSub)
        rowFont.setOnClickListener { showFontColorDialog() }

        refreshSubs()
    }

    override fun onResume() {
        super.onResume()
        // 视图状态恢复发生在 onCreate 之后、onResume 之前：这里按偏好再同步
        // 一次开关显示，盖掉可能被恢复回来的旧值（v1.9.1 显示 bug 的根因）。
        syncDynamicSwitch()
        refreshColorRowState()
    }

    private fun syncDynamicSwitch() {
        if (!::dynamicSwitch.isInitialized) return
        dynamicSwitch.isChecked = prefs.getBoolean(PREF_DYNAMIC_COLOR, false)
    }

    /** 应用配色行：动态颜色开着时变灰、点不了，副标题换成提示；
     *  只有关了动态颜色才恢复正常点击（Max 要求）。 */
    private fun refreshColorRowState() {
        if (!::rowColor.isInitialized || !::colorSub.isInitialized) return
        val dynamicOn = prefs.getBoolean(PREF_DYNAMIC_COLOR, false)
        rowColor.isEnabled = !dynamicOn
        rowColor.alpha = if (dynamicOn) 0.38f else 1f
        colorSub.text = if (dynamicOn) getString(R.string.app_color_disabled_hint)
            else "#%06X".format(0xFFFFFF and prefs.getInt(PREF_SEED_COLOR, DEFAULT_SEED_COLOR))
    }

    /** 点动态颜色行/开关：按持久化偏好（真实状态）走，不读开关显示值。
     *  关着就弹确认框（先说明作用再问是否开启，取消不开启、确认才开启）；
     *  开着就直接关掉。入口处先按偏好复位开关显示，抵消点按开关时系统
     *  自动翻转的那一下；确认/取消后由 recreate/onResume 同步。 */
    private fun onDynamicColorTap() {
        val currentlyOn = prefs.getBoolean(PREF_DYNAMIC_COLOR, false)
        dynamicSwitch.isChecked = currentlyOn
        if (!currentlyOn) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dynamic_color_confirm_title)
                .setIcon(R.drawable.ic_image)
                .setMessage(R.string.dynamic_color_confirm_msg)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm) { dialog, _ ->
                    dialog.dismiss()
                    prefs.edit().putBoolean(PREF_DYNAMIC_COLOR, true).apply()
                    dynamicSwitch.isChecked = true
                    recreate()
                }
                .show()
        } else {
            prefs.edit().putBoolean(PREF_DYNAMIC_COLOR, false).apply()
            dynamicSwitch.isChecked = false
            recreate()
        }
    }

    private fun toggleThemeOptions() {
        themeExpanded = !themeExpanded
        themeOptions.visibility = if (themeExpanded) View.VISIBLE else View.GONE
        themeChevron.setImageResource(
            if (themeExpanded) R.drawable.ic_arrow_down else R.drawable.ic_arrow_up
        )
    }

    private fun refreshSubs() {
        if (!::themeSub.isInitialized) return
        themeSub.text = when (prefs.getInt(MainActivity.PREF_THEME_MODE, 0)) {
            1 -> getString(R.string.theme_light)
            2 -> getString(R.string.theme_dark)
            else -> getString(R.string.theme_system)
        }
        if (::fontSub.isInitialized) {
            fontSub.text = "#%06X".format(0xFFFFFF and prefs.getInt(MainActivity.PREF_FONT_COLOR, MainActivity.DEFAULT_FONT_COLOR))
        }
        refreshColorRowState()
    }

    /** 应用配色对话框：5 列网格 52 个色块，当前选中的叠加白色对勾；
     *  选一个颜色后点“确认”才保存 seed_color 并 recreate()，点“取消”不改。
     *  动态颜色开着时这一行是灰的、点不开，这里再拦一道。 */
    private fun showColorDialog() {
        if (prefs.getBoolean(PREF_DYNAMIC_COLOR, false)) return
        val current = prefs.getInt(PREF_SEED_COLOR, DEFAULT_SEED_COLOR)
        var selected = SEED_COLORS.indexOf(current).takeIf { it >= 0 } ?: 0
        val checks = mutableListOf<ImageView>()

        val grid = GridLayout(this).apply {
            columnCount = 5
            rowCount = (SEED_COLORS.size + 4) / 5
            setPadding(dp(16), dp(8), dp(16), dp(8))
            // v1.9.6 修：ScrollView（FrameLayout）addView 默认给 MATCH_PARENT，
            // 网格会被撑满对话框宽（360dp 内容 > 342dp 宽），第 5 列圆点被截。
            // 显式 WRAP_CONTENT + 居中，内容 312dp，留足边距。
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = android.view.Gravity.CENTER_HORIZONTAL }
        }
        SEED_COLORS.forEachIndexed { index, color ->
            val cell = FrameLayout(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = dp(48)
                    height = dp(48)
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }
            }
            val dot = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(dp(44), dp(44)).apply {
                    gravity = android.view.Gravity.CENTER
                }
                setBackgroundResource(R.drawable.circle_bg)
                backgroundTintList = ColorStateList.valueOf(color)
            }
            val check = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(dp(24), dp(24)).apply {
                    gravity = android.view.Gravity.CENTER
                }
                setImageResource(R.drawable.ic_check)
                ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(Color.WHITE))
                visibility = if (index == selected) View.VISIBLE else View.GONE
            }
            checks.add(check)
            cell.addView(dot)
            cell.addView(check)
            cell.setOnClickListener {
                selected = index
                checks.forEachIndexed { i, v ->
                    v.visibility = if (i == selected) View.VISIBLE else View.GONE
                }
            }
            grid.addView(cell)
        }
        val scroll = ScrollView(this).apply { addView(grid) }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.app_color)
            .setIcon(R.drawable.ic_palette)
            .setView(scroll)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                prefs.edit().putInt(PREF_SEED_COLOR, SEED_COLORS[selected]).apply()
                recreate()
            }
            .show()
    }

    /** 字体颜色对话框（v1.9.6）：52 个颜色按基础/浅/深三组列出，每组一个
     *  标题 + 5 列网格，当前选中的叠加白色对勾；选一个颜色后点“确认”才
     *  保存 font_color 并 recreate()，点“取消”不改。默认黑色。 */
    private fun showFontColorDialog() {
        val current = prefs.getInt(MainActivity.PREF_FONT_COLOR, MainActivity.DEFAULT_FONT_COLOR)
        var selected = MainActivity.FONT_COLORS.indexOf(current).takeIf { it >= 0 } ?: 0
        val checks = mutableListOf<ImageView>()
        val groups = listOf(
            0 until 20 to R.string.font_color_group_base,
            20 until 36 to R.string.font_color_group_light,
            36 until 52 to R.string.font_color_group_dark,
        )
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = android.view.Gravity.CENTER_HORIZONTAL }
        }
        for ((range, labelRes) in groups) {
            val label = TextView(this).apply {
                text = getString(labelRes)
                setTextAppearance(android.R.style.TextAppearance_Material_Body2)
                setPadding(dp(24), dp(12), dp(24), dp(4))
            }
            body.addView(label)
            val grid = GridLayout(this).apply {
                columnCount = 5
                rowCount = (range.count() + 4) / 5
                setPadding(dp(16), dp(4), dp(16), dp(4))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            for (index in range) {
                val color = MainActivity.FONT_COLORS[index]
                val cell = FrameLayout(this).apply {
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = dp(48)
                        height = dp(48)
                        setMargins(dp(4), dp(4), dp(4), dp(4))
                    }
                }
                val dot = ImageView(this).apply {
                    layoutParams = FrameLayout.LayoutParams(dp(44), dp(44)).apply {
                        gravity = android.view.Gravity.CENTER
                    }
                    setBackgroundResource(R.drawable.circle_bg)
                    backgroundTintList = ColorStateList.valueOf(color)
                }
                val check = ImageView(this).apply {
                    layoutParams = FrameLayout.LayoutParams(dp(24), dp(24)).apply {
                        gravity = android.view.Gravity.CENTER
                    }
                    setImageResource(R.drawable.ic_check)
                    ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(Color.WHITE))
                    visibility = if (index == selected) View.VISIBLE else View.GONE
                }
                checks.add(check)
                cell.addView(dot)
                cell.addView(check)
                cell.setOnClickListener {
                    selected = index
                    checks.forEachIndexed { i, v ->
                        v.visibility = if (i == selected) View.VISIBLE else View.GONE
                    }
                }
                grid.addView(cell)
            }
            body.addView(grid)
        }
        val scroll = ScrollView(this).apply { addView(body) }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.font_color)
            .setIcon(R.drawable.ic_font_color)
            .setView(scroll)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                prefs.edit().putInt(MainActivity.PREF_FONT_COLOR, MainActivity.FONT_COLORS[selected]).apply()
                recreate()
            }
            .show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
