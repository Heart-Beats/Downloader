package com.hl.downloader.utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hl.downloader.bean.SubDownloadTaskBean

/**
 * @Author  张磊  on  2020/10/21 at 17:41
 * Email: 913305160@qq.com
 */

object GsonUtil {

    val gson = Gson()

    @JvmStatic
    inline fun <reified T> fromJson(json: String?): T? {
        val type = object : TypeToken<T>() {}.type
        return gson.fromJson(json, type)
    }

    @JvmStatic
    fun toJson(any: Any?): String {
        return gson.toJson(any)
    }
}