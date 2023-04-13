package com.hl.downloader

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.util.Patterns
import androidx.core.content.edit
import com.hl.downloader.bean.SubDownloadTaskBean
import com.hl.downloader.utils.GsonUtil
import kotlinx.coroutines.*
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
internal class DownloadTask(
    private val context: Context,
    private val downloadUrl: String,
    private val maxDownloadCore: Int = 1,
    private val saveFilePath: String?,
    private val exceptionHandler: CoroutineExceptionHandler? = null
) {

    private val TAG = Constants.BASE_TAG + this.javaClass.simpleName

    private val subDownloadTasks = mutableListOf<SubDownloadTask>()
    private var downloadListener: DownloadStatusListener? = DownloadStatusListener()

    private lateinit var saveFile: File
    private var fileSize = 0L
    private var isCanceled = false

    fun startDownload() {
        val isDownloading = subDownloadTasks.any {
            it.downloadStatus == DownloadStatus.DOWNLOADING
                    || it.downloadStatus == DownloadStatus.DOWNLOAD_PAUSE
        }
        if (isDownloading) {
            Log.d(TAG, "startDownload: 已在下载或暂停下载中，忽略此次下载请求")
            return
        }

        if (!Patterns.WEB_URL.matcher(downloadUrl).matches()) {
            Log.d(TAG, "startDownload: 下载地址错误（${downloadUrl}），请检查后再尝试！")

            downloadListener?.downloadStatusChange(DownloadStatus.DOWNLOAD_ERROR, error = Exception("下载地址错误"))
            return
        }

        //短时快速发起请求可能导致三次握手之后客户TCP的请求已关闭，产生 ECONNABORTED错误，需要对此情况处理
        val myCoroutineExceptionHandler = exceptionHandler ?: MyCoroutineExceptionHandler(errorPrint = {
            if (!isCanceled) {
                downloadListener?.downloadStatusChange(DownloadStatus.DOWNLOAD_ERROR, it)
            }
        }, retryAction = {
            if (!isCanceled) {
                startRequestDownload()
            }
        })

        MainScope().launch(myCoroutineExceptionHandler) {
            startRequestDownload()
        }
    }

    private suspend fun startRequestDownload() {
        isCanceled = false

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

        this.saveFile = getSaveFile()

        if (saveFile.exists() && saveFile.length() == fileSize) {
            Log.d(TAG, "startDownload: 本地文件已经存在，下载完成！")
            DownloadManager.downloadStatusChange(
                downloadStatus = DownloadStatus.DOWNLOAD_COMPLETE,
                downloadFilePath = saveFile.path
            )
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
                    val errorReason = "请求的文件不支持下载， statusCode == $statusCode"
                    Log.d(TAG, "startDownload: $errorReason")
                    downloadListener = null
                    DownloadManager.downloadStatusChange(
                        DownloadStatus.DOWNLOAD_ERROR,
                        error = Exception(errorReason)
                    )
                    return
                }
            }
        }

        startAsyncDownload()
    }

    private fun getSaveFile(): File {
        if (saveFilePath == null) {
            val fileName = downloadUrl.split("/").last()
            val file = context.getExternalFilesDir(null)
            checkNotNull(file) {
                "下载文件存放目录为空"
            }
            if (file.exists()) {
                check(file.isDirectory) {
                    file.absolutePath + " 不是文件夹，无法保存文件"
                }
            } else {
                check(file.mkdirs()) {
                    "不能创建文件夹: ${file.absolutePath}"
                }
            }
            return File(file, fileName)
        } else {
            val file = File(saveFilePath)

            if (file.exists()) {
                check(file.isFile) {
                    file.absolutePath + " 不是文件路径，无法下载"
                }
            }
            return file
        }
    }

    private fun existTask(): Boolean {
        val subDownLoadTaskBeansString = getSharedPreferences().getString(createSubDownLoadTaskKey(), "")

        val subDownloadTasks = GsonUtil.fromJson<List<SubDownloadTaskBean>>(subDownLoadTaskBeansString)?.map {
            SubDownloadTask(it)
        }
        Log.d(TAG, "existTask: 保存的下载task == $subDownloadTasks")

        val firstTask = subDownloadTasks?.firstOrNull()
        return if (subDownloadTasks?.isNotEmpty() === true && firstTask?.subDownloadTaskBean?.downLoadUrl == downloadUrl) {
            this.subDownloadTasks.clear()
            //如果保存的下载任务不为空，同时下载地址与此次任务相同时 ======》 任务已存在, 添加下载未完成的任务 ----> 添加任务列表
            this.subDownloadTasks.addAll(subDownloadTasks.filter {
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

        subDownloadTasks.clear()
        partSizeList.forEachIndexed { index, _ ->
            //开始位置：自身在分配大小list 位置的前面所有项和（不包括自身）， 如： [2,1,1] ==》 [0,2,3]
            val startPos = partSizeList.take(index).reduceOrNull { sum, size -> sum + size } ?: 0
            //结束位置：自身在分配大小list 位置的前面所有项和-1（包括自身）， 如： [2,1,1] ==》 [1,2,3]
            val endPos = partSizeList.take(index + 1).reduce { sum, size -> sum + size } - 1

            val subDownLoadTask =
                SubDownloadTask(SubDownloadTaskBean(downloadUrl, startPos = startPos, endPos = endPos, saveFile = file))
            subDownloadTasks.add(subDownLoadTask)
        }
    }

    /**
     * 服务器不支持断点续传，创建单线程下载任务
     */
    private fun initSubTask(file: File) {
        val subDownLoadTask = SubDownloadTask(SubDownloadTaskBean(downloadUrl, saveFile = file))
        subDownloadTasks.clear()
        subDownloadTasks.add(subDownLoadTask)
    }

    private fun startAsyncDownload() {
        if (subDownloadTasks.all { it.downloadStatus == DownloadStatus.DOWNLOAD_COMPLETE }) {
            DownloadManager.downloadStatusChange(DownloadStatus.DOWNLOAD_COMPLETE, downloadFilePath = saveFile.path)
            return
        }

        subDownloadTasks.filterAndOperateEach({ this.downloadStatus != DownloadStatus.DOWNLOAD_COMPLETE }) {
            it.startDownLoad(downloadListener)
        }
    }

    fun pauseDownLoad() {
        isCanceled = false

        subDownloadTasks.filterAndOperateEach({ this.downloadStatus != DownloadStatus.DOWNLOAD_PAUSE }) {
            it.downLoadPause()
        }
    }

    fun resumeDownLoad() {
        isCanceled = false

        //所有任务已完成或者本地已下载完成无下载任务时 -----> 下载完成
        if (subDownloadTasks.all {
                it.downloadStatus == DownloadStatus.DOWNLOAD_COMPLETE
            } || (subDownloadTasks.isEmpty() && downloadListener != null)) {
            DownloadManager.downloadStatusChange(
                downloadStatus = DownloadStatus.DOWNLOAD_COMPLETE, downloadFilePath = saveFile.path
            )
            return
        }

        //恢复下载时如果文件不存在或者服务器不支持断点续传 需要重新下载
        if (!saveFile.exists() || downloadListener?.needSaveTask == false) {
            startDownload()
            return
        }

        subDownloadTasks.filterAndOperateEach({ this.downloadStatus == DownloadStatus.DOWNLOAD_PAUSE }) {
            it.downLoadResume()
        }
    }

    fun cancelDownload() {
        isCanceled = true

        if (subDownloadTasks.isEmpty() || subDownloadTasks.all { it.downloadStatus == DownloadStatus.DOWNLOAD_CANCEL }) {
            DownloadManager.downloadStatusChange(downloadStatus = DownloadStatus.DOWNLOAD_CANCEL)
        }

        subDownloadTasks.filterAndOperateEach({ this.downloadStatus != DownloadStatus.DOWNLOAD_CANCEL }) {
            it.downloadCancel()
        }

        if (::saveFile.isInitialized && saveFile.exists()) {
            saveFile.delete() // 取消下载时删除下载文件
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

        private var  lastDownloadProgress = ""

        override fun downloadStatusChange(status: DownloadStatus, error: Throwable?) {
            when (status) {
                DownloadStatus.DOWNLOAD_ERROR -> {
                    DownloadManager.downloadStatusChange(status, error)
                }

                DownloadStatus.DOWNLOADING -> {
                    val decimalFormat = DecimalFormat("0.##")
                    decimalFormat.roundingMode = RoundingMode.FLOOR

                    val sum = subDownloadTasks.fold(0L) { sum, subDownLoadTask ->
                        sum + subDownLoadTask.subDownloadTaskBean.completeSize - (subDownLoadTask.subDownloadTaskBean.startPos
                            ?: 0)
                    }
                    val currentProgress = decimalFormat.format(sum * 100f / fileSize)
                    synchronized(lastDownloadProgress) {
                        if (currentProgress != lastDownloadProgress) {
                            DownloadManager.downloadStatusChange(downloadStatus = status, progress = currentProgress)
                            lastDownloadProgress = currentProgress
                            saveSubDownLoadTasks(needSaveTask)
                        }
                    }
                }

                DownloadStatus.DOWNLOAD_COMPLETE -> {
                    if (subDownloadTasks.all { it.downloadStatus == status }) {
                        DownloadManager.downloadStatusChange(downloadStatus = status, downloadFilePath = saveFile.path)
                        saveSubDownLoadTasks(needSaveTask)
                    }
                }
                DownloadStatus.DOWNLOAD_PAUSE -> {
                    if (subDownloadTasks.filter {
                            it.downloadStatus != DownloadStatus.DOWNLOAD_COMPLETE
                        }.all {
                            it.downloadStatus == status
                        }) {
                        DownloadManager.downloadStatusChange(downloadStatus = status)
                        saveSubDownLoadTasks(needSaveTask)
                    }
                }
                DownloadStatus.DOWNLOAD_CANCEL -> {
                    if (subDownloadTasks.filter {
                            it.downloadStatus != DownloadStatus.DOWNLOAD_COMPLETE
                        }.all {
                            it.downloadStatus == status
                        }) {
                        DownloadManager.downloadStatusChange(downloadStatus = status)
                        //取消下载时，清空所有任务同时清除缓存的任务列表
                        subDownloadTasks.clear()
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
                this.putString(
                    createSubDownLoadTaskKey(),
                    // 保存下载任务数据
                    GsonUtil.toJson(subDownloadTasks.map { it.subDownloadTaskBean })
                )
            } else {
                this.putString(createSubDownLoadTaskKey(), "")
            }
        }
    }

    private fun createSubDownLoadTaskKey() = "$downloadUrl&${saveFile.path}"
}