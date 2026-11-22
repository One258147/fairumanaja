package com.fairuman.filemanager

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * v1.9.2：所有 Activity 的基类。
 * 1. onCreate 里在 super.onCreate 之后统一应用语言/主题模式
 *    （MainActivity.applyPrefs：只剩语言 + 夜间模式；配色/引擎/玻璃/
 *    字体颜色改由 Compose 的 FairuTheme 直接从偏好算，不再走主题覆盖层）。
 * 2. 记下本次创建时应用的动态颜色/种子色；onResume 时跟保存的设置比，
 *    不一致就重建——外观页改了配色后，返回的每个页面都能立即变色，
 *    不用杀后台（v1.9.1 的 bug：lastApplied* 是静态的，外观页自己
 *    recreate 时就把它更新了，主界面 onResume 再比永远一致，所以不变色）。
 * v1.9.8.1：液态玻璃的 View 卡片染色（tintGlassCards）已删——玻璃改由
 * Compose 的 HAZE 实现（FairuTheme.fairuGlassStyle），不再遍历 View 树。
 */
open class BaseActivity : AppCompatActivity() {

    private var appliedDynamic = false
    private var appliedSeed = MainActivity.DEFAULT_SEED
    private var appliedEngine = MainActivity.UI_ENGINE_MD3
    private var appliedGlass = false
    private var appliedGlassMode = MainActivity.GLASS_MODE_TRANSLUCENT
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
            appliedGlass = sp.getBoolean(MainActivity.PREF_LIQUID_GLASS, false)
            appliedGlassMode = sp.getInt(MainActivity.PREF_LIQUID_GLASS_MODE, MainActivity.GLASS_MODE_TRANSLUCENT)
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
            val glass = sp.getBoolean(MainActivity.PREF_LIQUID_GLASS, false)
            val glassMode = sp.getInt(MainActivity.PREF_LIQUID_GLASS_MODE, MainActivity.GLASS_MODE_TRANSLUCENT)
            val fontColor = sp.getInt(MainActivity.PREF_FONT_COLOR, MainActivity.DEFAULT_FONT_COLOR)
            // v1.9.7：语言偏好变了（在别的页面切的）就重建，让新语言和
            // 蒙古文字体在所有页面都生效（偏好对偏好比，不碰 Locale API，
            // 不会因语言标签规范化不一致而误触发）。
            val locTag = sp.getString(MainActivity.PREF_LOCALE, null)
            if (locTag != appliedLocaleTag) recreate()
            if (dyn != appliedDynamic || seed != appliedSeed || engine != appliedEngine || glass != appliedGlass || glassMode != appliedGlassMode || fontColor != appliedFontColor) recreate()
        } catch (_: Exception) { }
    }
}
