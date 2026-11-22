package com.fairuman.filemanager

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * v1.9.4 开屏动画（Max 20:58），v1.9.8.1 改 Compose 动画：每次从桌面冷启动都播，播完才进主界面。
 * 阶段一（v1.9.8.1 Max 13:40：3 秒→1.5 秒快速转整圈）：应用图标顺时针旋转 1.5 秒转 360 度，
 * 期间 5 个半透明图标残影依次滞后、拖在主图标后面（残影/阴影保留，Max 09:49 补；滞后按新速度
 * 等比缩短为 60/120/180/240/300ms）；
 * 阶段二（Max 21:45 纠正 + 13:40 再改）：应用名字从屏幕最底部升上来——起始在屏幕外底部
 * （translationY = 整个屏高），一边向上升、一边转整圈 360 度，1.5 秒后停在图标下面（间隔 24dp，
 * 停住时名字不贴图标，中间有明显空隙），
 * 字体大且粗（40sp 加粗）；名字播完才进主界面。总共 3 秒。
 * 设置里关掉开屏动画（PREF_SPLASH=false）就直接进主界面，不播动画。
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
        setContent {
            FairuTheme { SplashScreen(onDone = { goMain() }) }
        }
    }

    private fun goMain() {
        if (wentMain) return
        wentMain = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

/** Overshoot 插值（到顶轻微回弹，像“弹”出来；animation-core 1.12 没有
 *  OvershootInterpolator，用 Easing lambda 按同款公式实现，tension 1.2）。 */
private val overshootEasing = Easing { x ->
    val s = 1.2f
    val t = x - 1f
    t * t * ((s + 1) * t + s) + 1f
}

@Composable
private fun SplashScreen(onDone: () -> Unit) {
    var phase by remember { mutableStateOf(0) }
    val iconRotation = remember { Animatable(0f) }
    val ghosts = remember { List(5) { Animatable(0f) } }
    // 名字起始在屏幕底部边缘之外：translationY 初始 = 整个屏幕高度（像素），
    // 名字先完全在屏幕外底部，1.5 秒里从底部边缘升到最终位置（以前只下移
    // 200 像素，在 Max 手机上看着像从中间弹出来）。
    val screenH = with(LocalDensity.current) {
        LocalConfiguration.current.screenHeightDp.dp.toPx()
    }
    val nameRise = remember(screenH) { Animatable(screenH) }
    val nameRotation = remember { Animatable(-360f) }
    val nameScale = remember { Animatable(0.5f) }
    val nameAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // 阶段一：主图标顺时针 1.5 秒转整圈 360 度；5 个残影同转、依次
        // 滞后 60ms（按新速度等比缩短，原 120ms 配 3 秒），各自在
        // 1.5 秒整点同时转完（duration = 1500 - 滞后）。
        launch { iconRotation.animateTo(360f, tween(1500, easing = LinearEasing)) }
        ghosts.forEachIndexed { i, g ->
            launch {
                delay(60L * (i + 1))
                g.animateTo(360f, tween((1500 - 60 * (i + 1)), easing = LinearEasing))
            }
        }
        delay(1500)
        // 阶段二：名字从屏幕底部升上来（升起 + 转整圈 360 度 + 放大 + 淡入），1.5 秒。
        phase = 1
        // 升起用 FastOutSlowIn，不用 overshoot：overshoot 会让名字冲过终点、
        // 向上顶到图标上（行程是整个屏高，超调量远大于图标名字间距）；弹的感觉
        // 留在缩放上（scale 仍用 overshoot，只变大小不移位）。
        launch { nameRise.animateTo(0f, tween(1500, easing = FastOutSlowInEasing)) }
        launch { nameScale.animateTo(1f, tween(1500, easing = overshootEasing)) }
        launch { nameAlpha.animateTo(1f, tween(1500, easing = LinearEasing)) }
        launch { nameRotation.animateTo(0f, tween(1500, easing = LinearEasing)) }
        delay(1500)
        // 名字播完才进主界面。
        onDone()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                // 残影：5 个半透明图标副本，透明度递减，放在主图标后面；阶段二开始后藏掉。
                if (phase == 0) {
                    ghosts.forEachIndexed { i, g ->
                        Image(
                            painterResource(R.drawable.ic_app_icon),
                            contentDescription = null,
                            modifier = Modifier
                                .size(120.dp)
                                .graphicsLayer { rotationZ = g.value }
                                .alpha(0.30f - i * 0.05f)
                        )
                    }
                }
                Image(
                    painterResource(R.drawable.ic_app_icon),
                    contentDescription = null,
                    modifier = Modifier
                        .size(120.dp)
                        .graphicsLayer { rotationZ = iconRotation.value }
                )
            }
            // 图标和名字之间的间隔：停住时名字在图标下方，中间留 24dp 明显空隙
            //（Max 13:47：不要贴着图标；以前 8dp 看着像贴在一起）。
            Spacer(Modifier.height(24.dp))
            if (phase == 1) {
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.graphicsLayer {
                        translationY = nameRise.value
                        rotationZ = nameRotation.value
                        scaleX = nameScale.value
                        scaleY = nameScale.value
                        alpha = nameAlpha.value
                    }
                )
            }
        }
    }
}
