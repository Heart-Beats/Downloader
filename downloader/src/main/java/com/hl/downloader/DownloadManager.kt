package com.hl.downloader

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * @Author  张磊  on  2020/11/04 at 20:45
 * Email: 913305160@qq.com
 */
object DownloadManager {

    private var downloadListener: DownloadListener? = null
    private var downloadTask: DownloadTask? = null

    private var mainScope: CoroutineScope? = null

    fun startDownLoad(
        context: Application,
        downloadUrl: String,
        maxDownloadCore: Int = 5,
        saveFilePath: String? = null,
        exceptionHandler: CoroutineExceptionHandler? = null,
        downloadListener: DownloadListener
    ) {
        this.downloadListener = downloadListener
        mainScope = MainScope()
        downloadTask = DownloadTask(context, downloadUrl, maxDownloadCore, saveFilePath, exceptionHandler)
        downloadTask?.startDownload()
    }

    fun cancelDownload() {
        downloadTask?.cancelDownload()
    }

    fun pauseDownload() {
        downloadTask?.pauseDownLoad()
    }

    fun resumeDownLoad() {
        downloadTask?.resumeDownLoad()
    }

    internal fun downloadStatusChange(
        downloadStatus: DownloadStatus,
        error: Throwable? = null,
        progress: String? = null,
        downloadFilePath: String? = null
    ) {
        //将通知发回主线程
        mainScope?.launch {
            Log.d("DownloadManager", "下载状态 == $downloadStatus, 下载进度 == $progress")
            when (downloadStatus) {
                DownloadStatus.DOWNLOAD_ERROR -> downloadListener?.downloadError(error)
                DownloadStatus.DOWNLOADING -> downloadListener?.downloadIng(progress ?: "")
                DownloadStatus.DOWNLOAD_COMPLETE -> downloadListener?.downloadComplete(downloadFilePath ?: "")
                DownloadStatus.DOWNLOAD_PAUSE -> downloadListener?.downloadPause()
                DownloadStatus.DOWNLOAD_CANCEL -> {
                    downloadListener?.downloadCancel()
                    downloadTask = null
                    downloadListener = null
                    mainScope = null
                }
                else -> {
                }
            }
        }
    }
}