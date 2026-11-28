package com.fairuman.filemanager

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * v1.9.2：所有 Activity 的基类。
 * 1. onCreate 里在 super.onCreate 之后统一应用语言/主题模式
 *    （MainActivity.applyPrefs：只剩语言 + 夜间模式；配色/引擎/
 *    字体颜色改由 Compose 的 FairuTheme 直接从偏好算，不再走主题覆盖层）。
 * 2. 记下本次创建时应用的动态颜色/种子色；onResume 时跟保存的设置比，
 *    不一致就重建——外观页改了配色后，返回的每个页面都能立即变色，
 *    不用杀后台（v1.9.1 的 bug：lastApplied* 是静态的，外观页自己
 *    recreate 时就把它更新了，主界面 onResume 再比永远一致，所以不变色）。
 * v1.9.7.5：液态玻璃已删，不再跟踪玻璃偏好。
 */
open class BaseActivity : AppCompatActivity() {

    private var appliedDynamic = false
    private var appliedSeed = MainActivity.DEFAULT_SEED
    private var appliedEngine = MainActivity.UI_ENGINE_MD3
    private var appliedFontColor = MainActivity.DEFAULT_FONT_COLOR
    // v1.9.7：本次创建时用的语言偏好；onResume 时跟保存的比，不一致就重建，
    // 否则从实验性功能页切了语言回来、旧页面还留着旧语言的文字和旧字体。
    private var appliedLocaleTag: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // v1.9.3：applyPrefs 必须在 super.onCreate 之后调（在 setContentView 之前）。
        // v1.9.2 放在 super 之前，冷启动时第一个 Activity 的主题覆盖层会被
        // AppCompatDelegate 在 super.onCreate 里重设掉——杀后台重进后主界面
        // 回到默认蓝（种子红/动态紫都不生效），进程内后建的 Activity 却正常。
        super.onCreate(savedInstanceState)
        MainActivity.applyPrefs(this)
        try {
            val sp = getSharedPreferences("fairu_prefs", MODE_PRIVATE)
            appliedDynamic = sp.getBoolean(MainActivity.PREF_DYNAMIC_COLOR, false)
            appliedSeed = sp.getInt(MainActivity.PREF_SEED_COLOR, MainActivity.DEFAULT_SEED)
            appliedEngine = sp.getInt(MainActivity.PREF_UI_ENGINE, MainActivity.UI_ENGINE_MD3)
            appliedFontColor = sp.getInt(MainActivity.PREF_FONT_COLOR, MainActivity.DEFAULT_FONT_COLOR)
            appliedLocaleTag = sp.getString(MainActivity.PREF_LOCALE, null)
        } catch (_: Exception) { }
    }

    override fun onResume() {
        super.onResume()
        try {
            val sp = getSharedPreferences("fairu_prefs", MODE_PRIVATE)
            val dyn = sp.getBoolean(MainActivity.PREF_DYNAMIC_COLOR, false)
            val seed = sp.getInt(MainActivity.PREF_SEED_COLOR, MainActivity.DEFAULT_SEED)
            val engine = sp.getInt(MainActivity.PREF_UI_ENGINE, MainActivity.UI_ENGINE_MD3)
            val fontColor = sp.getInt(MainActivity.PREF_FONT_COLOR, MainActivity.DEFAULT_FONT_COLOR)
            // v1.9.8：语言偏好变了（在别的页面切的）就重建，让新语言和
            // 蒙古文字体在所有页面都生效（偏好对偏好比，不碰 Locale API，
            // 不会因语言标签规范化不一致而误触发）。
            // “跟随系统”和 null 是同一个状态（哨兵 "system" 从不写进偏好），
            // 归一化后再比，避免来回重建。
            val locTag = normLocale(sp.getString(MainActivity.PREF_LOCALE, null))
            if (locTag != normLocale(appliedLocaleTag)) recreate()
            if (dyn != appliedDynamic || seed != appliedSeed || engine != appliedEngine || fontColor != appliedFontColor) recreate()
        } catch (_: Exception) { }
    }

    /** v1.9.8：null / 空 / "system" 都表示跟随系统，归一化成 null 再比较。 */
    private fun normLocale(t: String?): String? =
        if (t.isNullOrBlank() || t == LanguageActivity.TAG_FOLLOW_SYSTEM) null else t
}
