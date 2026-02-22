package com.ghhccghk.musicplay.data.dfid

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DfidData(
    val data : Data,
    @field:Json(name = "error_code") val errorCode: Int,
    val status: Int
)