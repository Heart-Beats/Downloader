 package com.hl.downloader

import android.util.Log
import com.hl.downloader.bean.SubDownloadTaskBean
import okhttp3.*
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.SocketException
import java.util.concurrent.TimeUnit

 /**
  * @Author  张磊  on  2020/11/04 at 15:06
  * Email: 913305160@qq.com
  */
 internal class SubDownloadTask(val subDownloadTaskBean: SubDownloadTaskBean) : Callback {

     private val TAG = Constants.BASE_TAG + this.javaClass.simpleName

     private val downLoadUrl: String
         get() = subDownloadTaskBean.downLoadUrl

     private val startPos: Long
         get() = subDownloadTaskBean.startPos ?: 0L

     private val endPos: Long
         get() = subDownloadTaskBean.endPos ?: 0L

     private var completeSize: Long
         get() = subDownloadTaskBean.completeSize
         set(value) {
             subDownloadTaskBean.completeSize = value
         }

     var downloadStatus: DownloadStatus
         get() = subDownloadTaskBean.downloadStatus
         set(value) {
             subDownloadTaskBean.downloadStatus = value
         }

     private val saveFile: File
         get() = subDownloadTaskBean.saveFile


     private val okHttpClient = OkHttpClient().newBuilder()
         .writeTimeout(10, TimeUnit.MINUTES)
         .readTimeout(10, TimeUnit.MINUTES)
         .build()

     private var isPause = false

     private var requestCall: Call? = null

     private var downloadStatusListener: OnDownloadStatusListener? = null


     fun startDownLoad(downloadListener: OnDownloadStatusListener?) {
         this.downloadStatusListener = downloadListener
         subDownloadTaskBean.downloadStatus = DownloadStatus.READY_TO_DOWNLOAD
         isPause = false
         startRequest()
     }

     private fun startRequest() {
         val request = Request.Builder()
             .url(downLoadUrl).apply {
                 if (startPos >= 0) {
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

         try {
             while (inputStream.read(byteArray).also { len = it } != -1) {
                 if (downloadStatus == DownloadStatus.DOWNLOAD_PAUSE) {
                     downloadStatusListener?.downloadStatusChange(downloadStatus)

                     // 下载暂停时取消下载
                     Log.e(TAG, "onResponse: 取消下载 ----> $this")
                     break
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
         } catch (e: Exception) {
             Log.e(TAG, "onResponse: ", e)

             if (e is SocketException && e.message == "Socket closed") {
                 downloadStatus = DownloadStatus.DOWNLOAD_CANCEL
                 downloadStatusListener?.downloadStatusChange(downloadStatus)
                 downloadStatusListener = null
             } else {
                 downloadStatus = DownloadStatus.DOWNLOAD_ERROR
                 downloadStatusListener?.downloadStatusChange(downloadStatus, e)
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

             //继续下载时重新下载
             startRequest()
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