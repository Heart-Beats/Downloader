package com.hl.downloader

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

/**
 * @Author  张磊  on  2020/11/07 at 14:48
 * Email: 913305160@qq.com
 */

internal class SubDownLoadTaskSerializer : JsonSerializer<SubDownLoadTask> {
    override fun serialize(
            src: SubDownLoadTask,
            typeOfSrc: Type,
            context: JsonSerializationContext
    ): JsonElement {
        val subDownLoadTaskObject = JsonObject()
        subDownLoadTaskObject.addProperty(src::downLoadUrl.name, src.downLoadUrl)
        subDownLoadTaskObject.addProperty(src::startPos.name, src.startPos)
        subDownLoadTaskObject.addProperty(src::endPos.name, src.endPos)
        subDownLoadTaskObject.addProperty(src::completeSize.name, src.completeSize)
        subDownLoadTaskObject.addProperty(src::downloadStatus.name, src.downloadStatus.name)

        val fileObject = JsonObject()
        fileObject.addProperty("path", src.saveFile.path)
        subDownLoadTaskObject.add(src::saveFile.name, fileObject)
        return subDownLoadTaskObject
    }
}