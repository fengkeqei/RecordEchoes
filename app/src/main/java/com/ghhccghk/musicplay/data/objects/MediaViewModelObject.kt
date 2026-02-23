package com.ghhccghk.musicplay.data.objects

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import com.ghhccghk.musicplay.MainActivity
import com.ghhccghk.musicplay.R
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import java.util.concurrent.CopyOnWriteArrayList

@Stable
object MediaViewModelObject {
    // 保持不可变引用的 MutableState，这样每次替换都会触发 Compose 订阅者更新
    val lrcEntries: MutableState<List<List<Pair<Float, String>>>> = mutableStateOf(listOf())

    // 使用自定义 ObservableMutableState 来拦截所有对 .value 的写入并触发监听器。
    private class ObservableMutableState<T>(initial: T, val onSet: (T) -> Unit) : MutableState<T> {
        private val delegate = mutableStateOf(initial)
        private val mainHandler = Handler(Looper.getMainLooper())
        override var value: T
            get() = delegate.value
            set(v) {
                // 如果当前在主线程，直接设置并回调；否则切回主线程设置，避免 Snapshot 变更被忽略
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    delegate.value = v
                    runCatching {
                        // 尝试记录一些有用信息，避免在生产环境打印过多
                        val info = when (v) {
                            is SyncedLyrics -> "lines=${v.lines.let { if (it is Collection<*>) it.size else -1 }}"
                            else -> v?.toString()?.take(100) ?: "null"
                        }
                        Log.d("MediaViewModelObject", "newLrcEntries.set -> $info")
                    }
                    onSet(v)
                } else {
                    mainHandler.post {
                        delegate.value = v
                        runCatching {
                            val info = when (v) {
                                is SyncedLyrics -> "lines=${v.lines.let { if (it is Collection<*>) it.size else -1 }}"
                                else -> v?.toString()?.take(100) ?: "null"
                            }
                            Log.d("MediaViewModelObject", "newLrcEntries.set(post) -> $info")
                        }
                        onSet(v)
                    }
                }
            }

        override fun component1(): T = value
        override fun component2(): (T) -> Unit = { value = it }
    }

    private val newLrcListeners: CopyOnWriteArrayList<(SyncedLyrics) -> Unit> =
        CopyOnWriteArrayList()

    val newLrcEntries: MutableState<SyncedLyrics> =
        ObservableMutableState(SyncedLyrics(listOf())) { lyrics ->
            // 在写入时通知所有监听器（保护调用）
            newLrcListeners.forEach { listener -> runCatching { listener.invoke(lyrics) } }
        }

    fun setNewLrcEntries(lyrics: SyncedLyrics) {
        // 兼容所有写法：显式替换实例也会触发监听器（因为 newLrcEntries 是 ObservableMutableState）
        newLrcEntries.value = lyrics
    }

    /**
     * 注册一个监听器，当 newLrcEntries 被替换时会收到回调（在调用线程执行）。
     * 返回一个 lambda 用于取消注册（方便使用）。
     */
    fun addNewLrcListener(listener: (SyncedLyrics) -> Unit): () -> Unit {
        newLrcListeners.add(listener)
        return { newLrcListeners.remove(listener) }
    }

    /**
     * 移除监听器（如果已经注册过）。
     */
    fun removeNewLrcListener(listener: (SyncedLyrics) -> Unit) {
        newLrcListeners.remove(listener)
    }

    val otherSideForLines = mutableStateListOf<Boolean>()

    // var mainLyricLines = mutableStateListOf<AnnotatedString>()
    val showControl: MutableState<Boolean> = mutableStateOf(false)

    val bitrate = mutableIntStateOf(0)

    //选中字体颜色
    val colorOnSecondaryContainerFinalColor = mutableIntStateOf(ContextCompat.getColor(MainActivity.lontext,R.color.lyric_main_bg))
    //未选中字体颜色
    val colorSecondaryContainerFinalColor = mutableIntStateOf(ContextCompat.getColor(MainActivity.lontext,R.color.lyric_sub_bg))
    //背景色
    val surfaceTransition = mutableIntStateOf(Color.Black.toArgb())

    val mediaItems = mutableStateOf(mutableListOf<MediaItem>())

    // val songSort = mutableStateOf(SettingData.getString("yos_player_song_sort", "MUSIC_TITLE"))
    // val enableDescending = mutableStateOf(SettingData.get("yos_player_enable_descending", false))
}