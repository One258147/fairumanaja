package com.fairuman.filemanager

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

/**
 * v1.9.4 开屏动画（Max 20:58）：每次从桌面冷启动都播，播完才进主界面。
 * 阶段一（v1.9.5 改成快速旋转 3 秒，原来 5 秒）：应用图标顺时针旋转 3 秒，
 * 期间 5 个半透明图标残影依次滞后、拖在主图标后面（残影/阴影保留，Max
 * 09:49 补）；阶段二（Max 21:45 纠正）：应用名字从下向上弹出——
 * 从图标下方升起，一边向上升、一边向上（逆时针）旋转，3 秒后停在
 * 图标下面（间隔 8dp），字体大且粗（40sp 加粗）。
 * 设置里关掉开屏动画（PREF_SPLASH=false）就直接进主界面，不播动画。
 * 继承 BaseActivity：super.onCreate 之后才调 applyPrefs（v1.9.3 的
 * 冷启动配色修法），杀后台重进配色仍正确。
 */
class SplashActivity : BaseActivity() {

    private var wentMain = false

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashHandler.install(this)
        super.onCreate(savedInstanceState)
        if (!getSharedPreferences("fairu_prefs", MODE_PRIVATE)
                .getBoolean(MainActivity.PREF_SPLASH, true)) {
            goMain()
            return
        }
        setContentView(R.layout.activity_splash)
        val ghostBox = findViewById<FrameLayout>(R.id.splashGhostBox)
        val icon = findViewById<ImageView>(R.id.splashIcon)
        val name = findViewById<TextView>(R.id.splashName)

        // 残影：5 个半透明图标副本，透明度递减，放在主图标后面（index 0）。
        val ghosts = (0 until 5).map { i ->
            ImageView(this).apply {
                setImageResource(R.drawable.ic_app_icon)
                alpha = 0.30f - i * 0.05f
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }.also { ghostBox.addView(it, 0) }
        }

        // 阶段一：图标顺时针快速转 3 秒（360°，匀速）；残影同转、依次滞后，
        // 各自在 3 秒整点同时转完（duration = 3000 - 滞后）。
        val spin = ObjectAnimator.ofFloat(icon, View.ROTATION, 0f, 360f).apply {
            duration = 3000
            interpolator = LinearInterpolator()
        }
        val ghostAnims = ghosts.mapIndexed { i, g ->
            val delay = 120L * (i + 1)
            ObjectAnimator.ofFloat(g, View.ROTATION, 0f, 360f).apply {
                duration = 3000 - delay
                startDelay = delay
                interpolator = LinearInterpolator()
            }
        }
        AnimatorSet().apply {
            playTogether(listOf(spin) + ghostAnims)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    playNamePop(name, ghosts)
                }
            })
            start()
        }
    }

    /** 阶段二（Max 21:45 纠正）：名字从下向上弹出——从图标下方升起，
     *  一边向上升、一边向上（逆时针）旋转，3 秒后停在图标下面；字体大且粗。
     *  升起用 Overshoot 插值（到顶轻微回弹，像“弹”出来），旋转用匀速。
     *  注意：只藏残影，不藏主图标——主图标和残影同在 splashGhostBox 里，
     *  整盒 GONE 会把主图标也藏掉（v1.9.4 首测抓到：名字弹出时图标没了）。 */
    private fun playNamePop(name: TextView, ghosts: List<ImageView>) {
        if (isFinishing || isDestroyed) return
        ghosts.forEach { it.visibility = View.GONE }
        name.visibility = View.VISIBLE
        val density = resources.displayMetrics.density
        // 从下向上升：从最终位置下方 200dp 处升上来。
        val rise = ObjectAnimator.ofFloat(name, View.TRANSLATION_Y, 200f * density, 0f).apply {
            duration = 3000
            interpolator = OvershootInterpolator(1.2f)
        }
        val scaleX = ObjectAnimator.ofFloat(name, View.SCALE_X, 0.5f, 1f).apply {
            duration = 3000
            interpolator = OvershootInterpolator(1.2f)
        }
        val scaleY = ObjectAnimator.ofFloat(name, View.SCALE_Y, 0.5f, 1f).apply {
            duration = 3000
            interpolator = OvershootInterpolator(1.2f)
        }
        val fade = ObjectAnimator.ofFloat(name, View.ALPHA, 0f, 1f).apply {
            duration = 3000
            interpolator = LinearInterpolator()
        }
        // 从下向上旋转：逆时针转满一圈（右侧向上），3 秒回到 0°。
        val rotate = ObjectAnimator.ofFloat(name, View.ROTATION, -360f, 0f).apply {
            duration = 3000
            interpolator = LinearInterpolator()
        }
        AnimatorSet().apply {
            playTogether(rise, scaleX, scaleY, fade, rotate)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    goMain()
                }
            })
            start()
        }
    }

    private fun goMain() {
        if (wentMain) return
        wentMain = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
