package com.ghhccghk.musicplay.data.dfid

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Data(
    val dfid: String? = null,
    val guid: String? = null,
    val mac: String? = null,
    val mid: String? = null,
    val serverDev: String? = null
)