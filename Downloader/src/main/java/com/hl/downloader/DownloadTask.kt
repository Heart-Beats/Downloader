package com.hl.downloader

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.util.Patterns
import androidx.core.content.edit
import com.google.gson.GsonBuilder
import com.hl.downloader.utils.gsonParseJson2List
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.text.DecimalFormat

/**
 * @Author  张磊  on  2020/11/04 at 15:03
 * Email: 913305160@qq.com
 */
internal class DownloadTask(private val context: Context, private val downloadUrl: String, private val maxDownloadCore: Int = 1) {

    companion object {
        private const val TAG = "DownloadTask"
        private const val SUB_DOWN_LOAD_TASKS = "下载任务"
    }

    private val subDownLoadTasks = mutableListOf<SubDownLoadTask>()
    private var downloadListener: DownloadStatusListener? = DownloadStatusListener()

    private var fileSize = 0L

    fun startDownload() {
        if (!Patterns.WEB_URL.matcher(downloadUrl).matches()) {
            Log.d(TAG, "startDownload: 下载地址错误（${downloadUrl}），请检查后再尝试！")
            return
        }

        //短时快速发起请求可能导致三次握手之后客户TCP的请求已关闭，产生 ECONNABORTED错误，需要对此情况处理
        val myCoroutineExceptionHandler = MyCoroutineExceptionHandler(delayTimeMills = 5 * 1000) {
            MainScope().launch(it) {
                startRequestDownload()
            }
        }

        MainScope().launch(myCoroutineExceptionHandler) {
            startRequestDownload()
        }
    }

    private suspend fun startRequestDownload() {
        val okHttpClient = OkHttpClient()
        val requestBuilder = Request.Builder().url(downloadUrl)
        requestBuilder.addHeader("RANGE", "bytes=0-")
        val (statusCode, contentLength) = withContext(Dispatchers.IO) {
            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            val statusCode = response.code
            val contentLength = response.header("content-length", "0")?.toLong() ?: 0
            Pair(statusCode, contentLength)
        }
        fileSize = contentLength
        Log.d(TAG, "startDownload: fileSize == $fileSize , statusCode = $statusCode")

        val saveFile = getSaveFile()

        if (saveFile.length() == fileSize) {
            Log.d(TAG, "startDownload: 本地文件已经存在，下载完成！")
            DownloadManager.downloadStatusChange(downloadStatus = DownloadStatus.DOWNLOAD_COMPLETE)
            return
        }

        //恢复下载时是有可能文件不存在的，需要对此情况处理
        if (!existTask() || !saveFile.exists()) {
            when (statusCode) {
                HttpURLConnection.HTTP_PARTIAL -> {
                    //服务端支持断点续传
                    initPartialSubTask(saveFile)
                    downloadListener = DownloadStatusListener()
                }
                HttpURLConnection.HTTP_OK -> {
                    initSubTask(saveFile)
                    downloadListener = DownloadStatusListener(false)
                }
                else -> {
                    Log.d(TAG, "startDownload: 请求的文件不支持下载， statusCode == $statusCode")
                    downloadListener = null
                    return
                }
            }
        }

        startAsyncDownload()
    }

    private fun getSaveFile(): File {
        val fileName = downloadUrl.split("/").last()
        val file = context.getExternalFilesDir(null)
        checkNotNull(file) {
            "下载文件存放目录为空"
        }
        if (file.exists()) {
            check(file.isDirectory) {
                file.absolutePath +
                        " 不是文件夹，无法保存文件"
            }
        } else {
            check(file.mkdirs()) {
                "不能创建文件夹: ${file.absolutePath}"
            }
        }
        return File(file, fileName)
    }

    private fun existTask(): Boolean {
        val subDownLoadTasksString = getSharedPreferences().getString(SUB_DOWN_LOAD_TASKS, "")
        val subDownLoadTasks = gsonParseJson2List<SubDownLoadTask>(subDownLoadTasksString)
        Log.d(TAG, "existTask: 保存的下载task == $subDownLoadTasks")
        return if (subDownLoadTasks?.isNotEmpty() == true && subDownLoadTasks[0].downLoadUrl == downloadUrl) {
            this.subDownLoadTasks.clear()
            //如果保存的下载任务不为空，同时下载地址与此次任务相同时 ======》 任务已存在, 添加下载未完成的任务 ----> 添加任务列表
            this.subDownLoadTasks.addAll(subDownLoadTasks.filter {
                it.downloadStatus != DownloadStatus.DOWNLOAD_COMPLETE
            })
            true
        } else {
            false
        }
    }

    /**
     * 服务器支持断点续传，创建多线程下载任务
     */
    private fun initPartialSubTask(file: File) {
        val partSizeList = mutableListOf<Long>()
        for (i in 0 until maxDownloadCore) {
            // 分配大小原则：平均分配，剩下的依次从头添加1 ， 如：4B 分配给 3个 ：[2B,1B,1B]
            val partSize = fileSize / maxDownloadCore + if (i < fileSize % maxDownloadCore) 1 else 0
            partSizeList.add(partSize)
        }

        subDownLoadTasks.clear()
        partSizeList.forEachIndexed { index, _ ->
            //开始位置：自身在分配大小list 位置的前面所有项和（不包括自身）， 如： [2,1,1] ==》 [0,2,3]
            val startPos = partSizeList.take(index).reduceOrNull { sum, size -> sum + size } ?: 0
            //结束位置：自身在分配大小list 位置的前面所有项和-1（包括自身）， 如： [2,1,1] ==》 [1,2,3]
            val endPos = partSizeList.take(index + 1).reduce { sum, size -> sum + size } - 1

            val subDownLoadTask = SubDownLoadTask(downloadUrl, startPos = startPos, endPos = endPos, saveFile = file)
            subDownLoadTasks.add(subDownLoadTask)
        }
    }

    /**
     * 服务器不支持断点续传，创建单线程下载任务
     */
    private fun initSubTask(file: File) {
        val subDownLoadTask = SubDownLoadTask(downloadUrl, saveFile = file)
        subDownLoadTasks.clear()
        subDownLoadTasks.add(subDownLoadTask)
    }

    private fun startAsyncDownload() {
        if (subDownLoadTasks.all { it.downloadStatus == DownloadStatus.DOWNLOAD_COMPLETE }) {
            DownloadManager.downloadStatusChange(DownloadStatus.DOWNLOAD_COMPLETE)
            return
        }

        subDownLoadTasks.filterAndOperateEach({ this.downloadStatus != DownloadStatus.DOWNLOAD_COMPLETE }) {
            it.startDownLoad(downloadListener)
        }
    }

    fun pauseDownLoad() {
        subDownLoadTasks.filterAndOperateEach({ this.downloadStatus != DownloadStatus.DOWNLOAD_PAUSE }) {
            it.downLoadPause()
        }
    }

    fun resumeDownLoad() {
        //恢复下载时如果文件不存在或者服务器不支持断点续传 需要重新下载
        if (!subDownLoadTasks[0].saveFile.exists() || downloadListener?.needSaveTask == false) {
            startDownload()
            return
        }

        subDownLoadTasks.filterAndOperateEach({ this.downloadStatus == DownloadStatus.DOWNLOAD_PAUSE }) {
            it.downLoadResume()
        }
    }

    fun cancelDownload() {
        subDownLoadTasks.filterAndOperateEach({ this.downloadStatus != DownloadStatus.DOWNLOAD_CANCEL }) {
            it.downloadCancel()
        }
    }

    private inline fun <T, R> List<T>.filterAndOperateEach(filter: T.() -> Boolean, action: (T) -> R): R? {
        var returnValue: R? = null
        this.filter(filter).forEach {
            returnValue = action(it)
        }
        return returnValue
    }

    private inner class DownloadStatusListener(val needSaveTask: Boolean = true) :
            OnDownloadStatusListener {

        private var lastDownloadProgress = -1f

        override fun downloadStatusChange(status: DownloadStatus) {
            when (status) {
                DownloadStatus.DOWNLOAD_ERROR -> {
                    DownloadManager.downloadStatusChange(downloadStatus = status)
                    //下载出错时取消下载
                    cancelDownload()
                }

                DownloadStatus.DOWNLOADING -> {
                    val decimalFormat = DecimalFormat(".##")
                    decimalFormat.roundingMode = RoundingMode.FLOOR

                    val sum = subDownLoadTasks.fold(0L) { sum, subDownLoadTask ->
                        sum + subDownLoadTask.completeSize - (subDownLoadTask.startPos ?: 0)
                    }
                    val currentProgress = sum * 100f / fileSize
                    if (currentProgress != lastDownloadProgress) {
                        DownloadManager.downloadStatusChange(downloadStatus = status, progress = decimalFormat.format(currentProgress))
                        lastDownloadProgress = currentProgress
                        saveSubDownLoadTasks(needSaveTask)
                    }
                }

                DownloadStatus.DOWNLOAD_COMPLETE, DownloadStatus.DOWNLOAD_PAUSE -> {
                    if (subDownLoadTasks.all {
                                it.downloadStatus == status
                            }) {
                        DownloadManager.downloadStatusChange(downloadStatus = status)
                        saveSubDownLoadTasks(needSaveTask)
                    }
                }
                DownloadStatus.DOWNLOAD_CANCEL -> {
                    if (subDownLoadTasks.all {
                                it.downloadStatus == status
                            }) {
                        DownloadManager.downloadStatusChange(downloadStatus = status)
                        //取消下载时，清空所有任务同时清除缓存的任务列表
                        subDownLoadTasks.clear()
                        saveSubDownLoadTasks(needSaveTask)
                    }
                }
                else -> {
                }
            }
        }
    }

    private fun getSharedPreferences(): SharedPreferences {
        return context.getSharedPreferences(context.packageName, Context.MODE_PRIVATE)
    }

    private fun saveSubDownLoadTasks(needSaveTask: Boolean) {
        getSharedPreferences().edit {
            if (needSaveTask) {
                //使用自定义的序列化器创建 Gson 对象
                val gson = GsonBuilder().registerTypeAdapter(SubDownLoadTask::class.java, SubDownLoadTaskSerializer()).create()
                this.putString(SUB_DOWN_LOAD_TASKS, gson.toJson(subDownLoadTasks))
            } else {
                this.putString(SUB_DOWN_LOAD_TASKS, "")
            }
        }
    }
}