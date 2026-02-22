package com.ghhccghk.musicplay.ui.widgets

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

// 共享的 Glance widget preferences key，供 Widget 与外部同步器使用
val PREF_LINE_LAST = stringPreferencesKey("line_last")
val PREF_LINE_CURRENT = stringPreferencesKey("line_current")
val PREF_LINE_NEXT = stringPreferencesKey("line_next")
val PREF_AGGRESSIVE_SCALE = booleanPreferencesKey("lyric_aggressive_scale")
val PREF_TYPEWRITER_INDEX = intPreferencesKey("lyric_typewriter_index")

