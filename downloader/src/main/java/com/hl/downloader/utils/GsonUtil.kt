package com.hl.downloader.utils

import com.google.gson.Gson
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * @Author  张磊  on  2020/10/21 at 17:41
 * Email: 913305160@qq.com
 */

/**
 * 将字符串数组转换为 List 对象
 */
inline fun <reified T> gsonParseJson2List(json: String?): List<T>? {
    return Gson().fromJson<List<T>>(json, ParameterizedTypeImpl(T::class.java))
}

class ParameterizedTypeImpl(private val clz: Class<*>) : ParameterizedType {
    override fun getRawType(): Type = List::class.java

    override fun getOwnerType(): Type? = null

    override fun getActualTypeArguments(): Array<Type> = arrayOf(clz)
}


/**
 * 个人学习生成，实际不可作为 gson 反序列化使用
 **/
fun Any.toJson(): String {
    val jsonObject = JSONObject()
    this::class.java.declaredFields.forEach {
        it.isAccessible = true
        if (!it.type.isNeedAllFields()) {
            if (!Modifier.isTransient(it.modifiers)) {
                jsonObject.put(it.name, it.get(this))
            }
        } else {
            jsonObject.put(it.name, it.get(this)?.toJson())
        }
        it.isAccessible = false
    }
    return jsonObject.toString()
}


fun <T> Class<T>.isNeedAllFields(): Boolean {
    return when {
        this == java.lang.Byte::class.java || this == java.lang.Short::class.java || this == java.lang.Integer::class.java
                || this == java.lang.Long::class.java || this == java.lang.Character::class.java
                || this == java.lang.Float::class.java || this == java.lang.Double::class.java || this == java.lang.Boolean::class.java
                || this.isPrimitive || this == String::class.java || this.isEnum || this == File::class.java
        -> false
        else -> true
    }
}

inline fun <reified T> List<T>.toJsonList(): String {
    val jsonArray = JSONArray()
    this.forEach {
        jsonArray.put(it?.toJson())
    }
    return jsonArray.toString()
}

