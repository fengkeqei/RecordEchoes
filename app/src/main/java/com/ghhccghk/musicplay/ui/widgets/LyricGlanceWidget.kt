// 对文件做的更改说明：
// 1. 增加中文注释说明。
// 2. 提取 stringPreferencesKey 为常量，避免重复创建。
// 3. 使用 drawable 背景（带圆角）通过 ImageProvider 设置，以实现圆角效果（Glance 中直接 clip 支持有限）。
// 4. 增加内边距与更清晰的文本样式，保留原有布局逻辑与大小判定。

package com.ghhccghk.musicplay.ui.widgets

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.ImageProvider
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlin.math.min

@SuppressLint("RestrictedApi")
class LyricGlanceWidget : GlanceAppWidget() {

    // 小部件尺寸集合
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(120.dp, 60.dp),
            DpSize(240.dp, 120.dp),
            DpSize(480.dp, 200.dp),
    ))

    // 使用共享的 PREF 常量，便于外部同步更新
    @SuppressLint("RestrictedApi")
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // 从 Glance 状态读取歌词行，若不存在则为空字符串
            val prefs = currentState<Preferences>()
            val last = prefs[PREF_LINE_LAST] ?: ""
            val current = prefs[PREF_LINE_CURRENT] ?: ""
            val next = prefs[PREF_LINE_NEXT] ?: ""

            val size = LocalSize.current

            // 根据尺寸判断不同的布局（保持原有逻辑）
            val isCompact = size.width < 120.dp || size.height < 60.dp
            val isMedium = size.width < 200.dp

            // 读取用户/外部偏好：是否启用激进缩放以及逐字显示索引（使用共享常量）
            val aggressive = prefs[PREF_AGGRESSIVE_SCALE] ?: false
            val typewriterIndex = prefs[PREF_TYPEWRITER_INDEX] ?: -1

            // 计算一个基于最小边长（宽/高中取小）的缩放因子，用于动态调整文字大小以适配不同 widget 大小
            // 使用 min(widthDp, heightDp) 作为基准，基准值仍为 200dp；激进模式会扩大缩放区间
            val scale = computeScale(size.width.value, size.height.value, aggressive)

            GlanceTheme {
                // 使用 drawable 背景实现圆角（在 Glance 中 clip 支持有限，使用图片背景是兼容方式）
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        // ImageProvider 需要 Uri，使用 android.resource URI 指向 drawable
                        .background(
                            ImageProvider(
                                "android.resource://${context.packageName}/drawable/lyric_widget_bg".toUri()
                            )
                        ) // 使用我们新增的圆角 drawable
                        .padding(8.dp) // 整体内边距
                ) {
                    // 如果启用了逐字显示（typewriterIndex >= 0），仅展示 current 的前 N 个字符
                    val displayedCurrent = if (typewriterIndex >= 0) {
                        val n = typewriterIndex.coerceAtLeast(0)
                        if (n >= current.length) current else current.substring(0, n)
                    } else current

                    // 调试日志：打印 widget 端读取到的逐字索引与将要展示的文本
                    android.util.Log.d(
                        "LyricGlanceWidget",
                        "typewriterIndex=$typewriterIndex current='$current' displayed='$displayedCurrent'"
                    )

                    if (isCompact) {
                        CompactLayout(displayedCurrent, scale)
                    } else if (isMedium) {
                        MediumLayout(last, displayedCurrent, next, scale)
                    } else {
                        FullLayout(last, displayedCurrent, next, scale)
                    }
                }
            }
        }
    }

    // 根据宽度/高度（dp）计算缩放系数，使用 min(widthDp, heightDp) 作为基准
    // 如果 aggressive == true，则使用更激进的缩放区间（0.5..2.0），否则使用保守区间（0.7..1.6）
    private fun computeScale(widthDp: Float, heightDp: Float, aggressive: Boolean): Float {
        val base = min(widthDp, heightDp)
        val raw = base / 200f
        return if (aggressive) raw.coerceIn(0.5f, 2.0f) else raw.coerceIn(0.7f, 1.6f)
    }

    /**
     * 紧凑布局：仅显示当前行，居中
     */
    @Composable
    fun CompactLayout(current: String, scale: Float) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = current,
                // 使用 scale 调整字号，实现文字自动缩放
                style = TextStyle(fontSize = (16 * scale).sp, color = ColorProvider(Color.White))
            )
        }
    }

    /**
     * 中等布局：显示上一行、当前行、下一行，当前行强调
     */
    @Composable
    fun MediumLayout(last: String, current: String, next: String, scale: Float) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = last,
                style = TextStyle(fontSize = (14 * scale).sp, color = ColorProvider(Color.Gray))
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = current,
                style = TextStyle(fontSize = (16 * scale).sp, color = ColorProvider(Color.White))
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = next,
                style = TextStyle(fontSize = (12 * scale).sp, color = ColorProvider(Color.Gray))
            )
        }
    }

    /**
     * 完整布局：当前行更大字号以示突出
     */
    @Composable
    fun FullLayout(last: String, current: String, next: String, scale: Float) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = last,
                style = TextStyle(fontSize = (16 * scale).sp, color = ColorProvider(Color.Gray))
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = current,
                style = TextStyle(fontSize = (20 * scale).sp, color = ColorProvider(Color.White))
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = next,
                style = TextStyle(fontSize = (16 * scale).sp, color = ColorProvider(Color.Gray))
            )
        }
    }
}