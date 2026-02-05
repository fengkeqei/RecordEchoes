package com.ghhccghk.musicplay.data.dfid

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Data(
    val dfid: String,
    val guid: String,
    val mac: String,
    val mid: String,
    val serverDev: String
)