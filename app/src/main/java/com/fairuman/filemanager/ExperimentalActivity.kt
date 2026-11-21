package com.fairuman.filemanager

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import java.util.Locale

/**
 * 实验性功能页（v1.9.6）：大标题“实验性功能”+ 一张卡片，一行“UI引擎”，
 * 副标题显示当前引擎。点 UI引擎 弹菜单列三个选项（各带一句话说明，
 * 当前选中的打勾圆圈）；点一个跟当前不同的，先弹确认框（说明切换后
 * 整个 App 的界面都会换、会立即重新加载），取消不动，确认才切换。
 * 切换只保存偏好 + recreate()；其它 Activity 在 onResume 时由
 * BaseActivity 比对引擎偏好、不一致就重建（跟应用配色一个路子）。
 */
class ExperimentalActivity : BaseActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var engineSub: TextView
    private lateinit var glassSub: TextView
    private lateinit var glassSwitch: MaterialSwitch
    private lateinit var endangeredSub: TextView

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

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashHandler.install(this)
        // 语言/主题/配色/UI 引擎由 BaseActivity.onCreate 统一应用。
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
        setContentView(R.layout.activity_experimental)
        prefs = getSharedPreferences("fairu_prefs", MODE_PRIVATE)

        val toolbar = findViewById<MaterialToolbar>(R.id.experimentalToolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val rowEngine = findViewById<View>(R.id.rowUiEngine)
        rowEngine.findViewById<ImageView>(R.id.rowIcon).setImageResource(R.drawable.ic_theme)
        rowEngine.findViewById<TextView>(R.id.rowTitle).setText(R.string.ui_engine)
        engineSub = rowEngine.findViewById(R.id.rowSub)
        rowEngine.setOnClickListener { showEngineDialog() }

        // v1.9.6 液态玻璃（默认关）：点行弹模式选择（半透明/透明模糊/全透明），
        // 点开关才是开关（关着先弹确认框，确认才开；开着直接关）。开关显示永远
        // 以偏好为准（isSaveEnabled=false + onResume 同步，照开屏动画开关的修法）。
        // 行上的副标题显示当前模式，箭头表示点行有菜单。
        val rowGlass = findViewById<View>(R.id.rowLiquidGlass)
        rowGlass.findViewById<ImageView>(R.id.rowIcon).setImageResource(R.drawable.ic_droplet)
        rowGlass.findViewById<TextView>(R.id.rowTitle).setText(R.string.liquid_glass)
        glassSub = rowGlass.findViewById(R.id.rowSub)
        glassSub.visibility = View.VISIBLE
        rowGlass.findViewById<ImageView>(R.id.rowChevron).visibility = View.VISIBLE
        glassSwitch = rowGlass.findViewById(R.id.rowSwitch)
        glassSwitch.visibility = View.VISIBLE
        glassSwitch.isSaveEnabled = false
        glassSwitch.isChecked = prefs.getBoolean(MainActivity.PREF_LIQUID_GLASS, false)
        rowGlass.setOnClickListener { showGlassModeDialog() }
        glassSwitch.setOnClickListener { onLiquidGlassTap() }

        // v1.9.7 濒危多语言切换：点行弹 5 选 1 对话框，选好点确认才切换，
        // 取消不切换（跟主语言设置“选好点确认才生效”的流程一致）。
        // 副标题显示当前濒危语言，没切时显示 5 种语言的列表。
        val rowEndangered = findViewById<View>(R.id.rowEndangeredLanguage)
        rowEndangered.findViewById<ImageView>(R.id.rowIcon).setImageResource(R.drawable.ic_language)
        rowEndangered.findViewById<TextView>(R.id.rowTitle).setText(R.string.endangered_language_switch)
        endangeredSub = rowEndangered.findViewById(R.id.rowSub)
        endangeredSub.visibility = View.VISIBLE
        rowEndangered.findViewById<ImageView>(R.id.rowChevron).visibility = View.VISIBLE
        rowEndangered.setOnClickListener { showEndangeredDialog() }

        refreshSubs()
    }

    override fun onResume() {
        super.onResume()
        try {
            refreshSubs()
            if (::glassSwitch.isInitialized) {
                glassSwitch.isChecked = prefs.getBoolean(MainActivity.PREF_LIQUID_GLASS, false)
            }
        } catch (_: Exception) { }
    }

    /** 点液态玻璃开关：关着就先弹提示框，确认才开、取消不开；
     *  开着就直接关掉，不用确认（照动态颜色开关的修法，入口处先按偏好
     *  复位开关显示，抵消点按开关时系统自动翻转的那一下）。点行不走
     *  这里，行是弹模式选择。 */
    private fun onLiquidGlassTap() {
        val currentlyOn = prefs.getBoolean(MainActivity.PREF_LIQUID_GLASS, false)
        glassSwitch.isChecked = currentlyOn
        if (!currentlyOn) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.liquid_glass_confirm_title)
                .setIcon(R.drawable.ic_droplet)
                .setMessage(R.string.liquid_glass_confirm_msg)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm) { dialog, _ ->
                    dialog.dismiss()
                    prefs.edit().putBoolean(MainActivity.PREF_LIQUID_GLASS, true).apply()
                    glassSwitch.isChecked = true
                    recreate()
                }
                .show()
        } else {
            prefs.edit().putBoolean(MainActivity.PREF_LIQUID_GLASS, false).apply()
            glassSwitch.isChecked = false
            recreate()
        }
    }

    private fun refreshSubs() {
        if (!::engineSub.isInitialized) return
        engineSub.text = MainActivity.engineDisplayName(
            this, prefs.getInt(MainActivity.PREF_UI_ENGINE, MainActivity.UI_ENGINE_MD3)
        )
        if (::glassSub.isInitialized) {
            glassSub.text = MainActivity.glassModeDisplayName(
                this, prefs.getInt(MainActivity.PREF_LIQUID_GLASS_MODE, MainActivity.GLASS_MODE_TRANSLUCENT)
            )
        }
        if (::endangeredSub.isInitialized) {
            val tag = prefs.getString(MainActivity.PREF_LOCALE, null)
            endangeredSub.text = endangeredNative(tag ?: "")
                ?: getString(R.string.endangered_language_sub)
        }
    }

    /** 液态玻璃模式菜单：三行（标题 + 一句话说明 + 圆圈选中态），点一行即选
     *  即保存，菜单关闭；开关开着时切模式立即生效（重建本页，其它页在
     *  onResume 时由 BaseActivity 比对模式偏好、不一致就重建）。 */
    private fun showGlassModeDialog() {
        val current = prefs.getInt(MainActivity.PREF_LIQUID_GLASS_MODE, MainActivity.GLASS_MODE_TRANSLUCENT)
        val options = listOf(
            Triple(MainActivity.GLASS_MODE_TRANSLUCENT, getString(R.string.liquid_glass_mode_translucent), getString(R.string.liquid_glass_mode_desc_translucent)),
            Triple(MainActivity.GLASS_MODE_BLUR, getString(R.string.liquid_glass_mode_blur), getString(R.string.liquid_glass_mode_desc_blur)),
            Triple(MainActivity.GLASS_MODE_CLEAR, getString(R.string.liquid_glass_mode_clear), getString(R.string.liquid_glass_mode_desc_clear))
        )
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.liquid_glass_mode)
            .setView(list)
            .create()
        options.forEach { (mode, name, desc) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                val out = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
                setBackgroundResource(out.resourceId)
                isClickable = true
                isFocusable = true
                setPadding(dp(16), dp(12), dp(16), dp(12))
            }
            val radio = RadioButton(this).apply {
                isChecked = mode == current
                isClickable = false
                isFocusable = false
            }
            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
            }
            val title = TextView(this).apply {
                text = name
                setTextAppearance(android.R.style.TextAppearance_Material_Widget_ActionBar_Title)
                textSize = 16f
            }
            val sub = TextView(this).apply {
                text = desc
                setTextAppearance(android.R.style.TextAppearance_Material_Widget_ActionBar_Subtitle)
                textSize = 13f
            }
            texts.addView(title)
            texts.addView(sub)
            row.addView(radio)
            row.addView(texts)
            row.setOnClickListener {
                dialog.dismiss()
                if (mode != current) {
                    prefs.edit().putInt(MainActivity.PREF_LIQUID_GLASS_MODE, mode).apply()
                    refreshSubs()
                    if (prefs.getBoolean(MainActivity.PREF_LIQUID_GLASS, false)) recreate()
                }
            }
            list.addView(row)
        }
        dialog.show()
    }

    /** UI 引擎菜单：三行（标题 + 一句话说明 + 圆圈选中态），点一行处理。 */
    private fun showEngineDialog() {
        val current = prefs.getInt(MainActivity.PREF_UI_ENGINE, MainActivity.UI_ENGINE_MD3)
        val options = listOf(
            Triple(MainActivity.UI_ENGINE_MD3, "Material Design 3", getString(R.string.ui_engine_desc_md3)),
            Triple(MainActivity.UI_ENGINE_EXPRESSIVE, "M3 Expressive", getString(R.string.ui_engine_desc_expressive)),
            Triple(MainActivity.UI_ENGINE_MIUIX, "Miuix", getString(R.string.ui_engine_desc_miuix))
        )
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.ui_engine)
            .setView(list)
            .create()
        options.forEach { (engine, name, desc) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                // 注意：setBackgroundResource 要的是 drawable 资源 id，
                // 不能直接传 attr id（会抛 Resources$NotFoundException）。
                val out = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
                setBackgroundResource(out.resourceId)
                isClickable = true
                isFocusable = true
                setPadding(dp(16), dp(12), dp(16), dp(12))
            }
            val radio = RadioButton(this).apply {
                isChecked = engine == current
                isClickable = false
                isFocusable = false
            }
            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
            }
            val title = TextView(this).apply {
                text = name
                setTextAppearance(android.R.style.TextAppearance_Material_Widget_ActionBar_Title)
                textSize = 16f
            }
            val sub = TextView(this).apply {
                text = desc
                setTextAppearance(android.R.style.TextAppearance_Material_Widget_ActionBar_Subtitle)
                textSize = 13f
            }
            texts.addView(title)
            texts.addView(sub)
            row.addView(radio)
            row.addView(texts)
            row.setOnClickListener {
                dialog.dismiss()
                if (engine != current) showSwitchConfirm(engine, name)
            }
            list.addView(row)
        }
        dialog.show()
    }

    /** 切到另一个引擎：先弹确认框，确认才保存并重建界面。 */
    private fun showSwitchConfirm(engine: Int, name: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.ui_engine_switch_title)
            .setIcon(R.drawable.ic_theme)
            .setMessage(getString(R.string.ui_engine_switch_msg, name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                prefs.edit().putInt(MainActivity.PREF_UI_ENGINE, engine).apply()
                refreshSubs()
                recreate()
            }
            .show()
    }

    /** v1.9.7 濒危多语言切换：5 个单选（圆圈 + 原语名 + 中文名），点一行只
     *  选中、不切换；点确认才保存偏好 + setApplicationLocales + 重建本页
     *  （跟主语言设置同一套机制；主界面在 onResume 时比对语言偏好重建）。
     *  点取消或直接关掉对话框都不切换。切回中文走主语言设置（多语言设置
     *  里选简体中文点 ✓），那里保存 zh-CN 后同样生效，不会回不来。 */
    private fun showEndangeredDialog() {
        val current = prefs.getString(MainActivity.PREF_LOCALE, null) ?: ""
        var pendingTag: String? = ENDANGERED_LANGS.firstOrNull { it.first == current }?.first
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val radios = mutableListOf<RadioButton>()
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.endangered_language_switch)
            .setIcon(R.drawable.ic_language)
            .setView(list)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val tag = pendingTag ?: return@setPositiveButton
                if (tag == current) return@setPositiveButton
                prefs.edit().putString(MainActivity.PREF_LOCALE, tag).apply()
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.create(Locale.forLanguageTag(tag))
                )
                refreshSubs()
                recreate()
            }
            .create()
        ENDANGERED_LANGS.forEach { (tag, native, sub) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                val out = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
                setBackgroundResource(out.resourceId)
                isClickable = true
                isFocusable = true
                setPadding(dp(16), dp(12), dp(16), dp(12))
            }
            val radio = RadioButton(this).apply {
                isChecked = tag == pendingTag
                isClickable = false
                isFocusable = false
            }
            radios.add(radio)
            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
            }
            val title = TextView(this).apply {
                text = native
                setTextAppearance(android.R.style.TextAppearance_Material_Widget_ActionBar_Title)
                textSize = 16f
            }
            val subView = TextView(this).apply {
                text = sub
                setTextAppearance(android.R.style.TextAppearance_Material_Widget_ActionBar_Subtitle)
                textSize = 13f
            }
            texts.addView(title)
            texts.addView(subView)
            row.addView(radio)
            row.addView(texts)
            row.setOnClickListener {
                pendingTag = tag
                radios.forEachIndexed { i, r -> r.isChecked = ENDANGERED_LANGS[i].first == tag }
            }
            list.addView(row)
        }
        dialog.show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
