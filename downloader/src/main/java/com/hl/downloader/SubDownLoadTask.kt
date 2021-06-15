 package com.hl.downloader

import android.util.Log
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.internal.wait
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.SocketException

 /**
 * @Author  张磊  on  2020/11/04 at 15:06
 * Email: 913305160@qq.com
 */
internal data class SubDownLoadTask(
        val downLoadUrl: String,
        val startPos: Long? = null,
        val endPos: Long? = null,
        var completeSize: Long = startPos ?: 0,
        var downloadStatus: DownloadStatus = DownloadStatus.READY_TO_DOWNLOAD,
        val saveFile: File
) : Callback {

     private companion object {
         private const val TAG = "SubDownLoadTask"
     }

     private var isPause = false

     private var requestCall: Call? = null

     private var downloadStatusListener: OnDownloadStatusListener? = null


     fun startDownLoad(downloadListener: OnDownloadStatusListener?) {
         this.downloadStatusListener = downloadListener
         downloadStatus = DownloadStatus.READY_TO_DOWNLOAD
         isPause = false
         startRequest()
     }

     private fun startRequest() {
         //OkHttpClient该对象不可作为属性直接被 Gson 序列化，需注意
         val okHttpClient = OkHttpClient()
         val request = Request.Builder()
             .url(downLoadUrl).apply {
                 if (startPos != null && startPos >= 0) {
                     addHeader("RANGE", "bytes=$completeSize-$endPos")
                 }
             }
             .build()
         requestCall = okHttpClient.newCall(request)
         requestCall?.enqueue(this)
     }

     override fun onFailure(call: Call, e: IOException) {
         downloadStatus = DownloadStatus.DOWNLOAD_ERROR
         downloadStatusListener?.downloadStatusChange(downloadStatus, e)
     }

     override fun onResponse(call: Call, response: Response) {
         val body = response.body
         val randomAccessFile = RandomAccessFile(saveFile, "rwd")
         //randomAccessFile.seek(pos): pos 代表要跳过的字节数，若为 0 则表示文件开头
         randomAccessFile.seek(completeSize)

         val byteArray = ByteArray(1024 * 1024)

         var len: Int
         val inputStream = body?.byteStream() ?: return
         val flag = Any()

         try {
             while (inputStream.read(byteArray).also { len = it } != -1) {
                 synchronized(flag) {
                     if (downloadStatus == DownloadStatus.DOWNLOAD_PAUSE) {
                         downloadStatusListener?.downloadStatusChange(downloadStatus)
                         flag.wait()
                     } else {
                         randomAccessFile.write(byteArray, 0, len)
                         completeSize += len

                         if (!isPause) {
                             downloadStatus = DownloadStatus.DOWNLOADING
                             downloadStatusListener?.downloadStatusChange(downloadStatus)
                         }
                     }

                     if (completeSize >= body.contentLength() && endPos == null || (endPos != null && completeSize > endPos)) {
                         downloadStatus = DownloadStatus.DOWNLOAD_COMPLETE
                         downloadStatusListener?.downloadStatusChange(downloadStatus)
                     }
                 }
             }
         } catch (e: Exception) {
             Log.e(TAG, "onResponse: ", e)

             if (e is SocketException && e.message == "Socket closed") {
                 downloadStatus = DownloadStatus.DOWNLOAD_CANCEL
                 downloadStatusListener?.downloadStatusChange(downloadStatus)
                 downloadStatusListener = null
             } else {
                 downloadStatus = DownloadStatus.DOWNLOAD_PAUSE
                 downloadStatusListener?.downloadStatusChange(downloadStatus)
             }
         } finally {
             inputStream.close()
         }
    }

    fun downLoadPause() {
        if (downloadStatus != DownloadStatus.DOWNLOAD_COMPLETE) {
            downloadStatus = DownloadStatus.DOWNLOAD_PAUSE
            isPause = true
        }
    }

    fun downLoadResume() {
        if (downloadStatus != DownloadStatus.DOWNLOAD_COMPLETE) {
            downloadStatus = DownloadStatus.READY_TO_DOWNLOAD
            downloadStatusListener?.downloadStatusChange(downloadStatus)

            isPause = false

            //短时快速发起请求可能导致三次握手之后客户TCP的请求已关闭，产生 ECONNABORTED错误，需要对此情况处理
            val myCoroutineExceptionHandler = MyCoroutineExceptionHandler(errorPrint = {
                DownloadManager.downloadStatusChange(
                    downloadStatus = DownloadStatus.DOWNLOAD_ERROR,
                    error = it
                )
            }, retryAction = {
                startRequest()
            })

            MainScope().launch(myCoroutineExceptionHandler) {
                //延迟500ms, 确保网络改变唤醒生效
                delay(500)
                startRequest()
            }
        }
    }

    fun downloadCancel() {
        if (requestCall?.isCanceled() != true) {
            requestCall?.cancel()
        }

        downloadStatus = DownloadStatus.DOWNLOAD_CANCEL
        downloadStatusListener?.downloadStatusChange(downloadStatus)
    }
}