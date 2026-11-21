package com.fairuman.filemanager

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

/**
 * v1.9.2：所有 Activity 的基类。
 * 1. onCreate 里在 super.onCreate 之前统一应用语言/主题/配色
 *    （以前各 Activity 自己调 MainActivity.applyPrefs，容易漏）。
 * 2. 记下本次创建时应用的动态颜色/种子色；onResume 时跟保存的设置比，
 *    不一致就重建——外观页改了配色后，返回的每个页面都能立即变色，
 *    不用杀后台（v1.9.1 的 bug：lastApplied* 是静态的，外观页自己
 *    recreate 时就把它更新了，主界面 onResume 再比永远一致，所以不变色）。
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
        // v1.9.6 液态玻璃跟随主题：玻璃开着时，本页的 MaterialCardView 背景
        // 染上当前主题主色（低透明度叠加）。decorView.post 跑在子类
        // setContentView 之后；主题一换整栈重建，每个 Activity 重建时都会
        // 重跑，色调自然跟上。只跑一次（新实例从 XML 重建，颜色是干净的）。
        if (appliedGlass) {
            window.decorView.post { tintGlassCards() }
        }
    }

    /** 把本页所有 MaterialCardView 的背景按液态玻璃模式染色。
     *  半透明 = 半透明底 + 主题色调；透明模糊 = 很淡的主题色（基本透明）；
     *  全透明 = 全透明。只改色相/透明度，圆角/阴影都不动。 */
    private fun tintGlassCards() {
        if (isFinishing || isDestroyed) return
        try {
            val sp = getSharedPreferences("fairu_prefs", MODE_PRIVATE)
            val mode = sp.getInt(MainActivity.PREF_LIQUID_GLASS_MODE, MainActivity.GLASS_MODE_TRANSLUCENT)
            val tint = when (mode) {
                MainActivity.GLASS_MODE_BLUR -> MainActivity.glassTintColor(this, MainActivity.GLASS_TINT_ALPHA_BLUR)
                else -> MainActivity.glassTintColor(this)
            }
            val root = window.decorView as? ViewGroup ?: return
            tintCardsRecursive(root, mode, tint)
        } catch (_: Exception) { }
    }

    private fun tintCardsRecursive(v: View, mode: Int, tint: Int) {
        if (v is MaterialCardView) {
            try {
                when (mode) {
                    MainActivity.GLASS_MODE_CLEAR -> v.setCardBackgroundColor(Color.TRANSPARENT)
                    MainActivity.GLASS_MODE_BLUR -> v.setCardBackgroundColor(tint)
                    else -> {
                        val cur = v.cardBackgroundColor?.defaultColor
                        if (cur != null) v.setCardBackgroundColor(MainActivity.tintSurfaceColor(cur, tint))
                    }
                }
            } catch (_: Exception) { }
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) tintCardsRecursive(v.getChildAt(i), mode, tint)
        }
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
