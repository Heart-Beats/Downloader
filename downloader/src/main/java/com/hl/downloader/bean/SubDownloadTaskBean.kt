package com.hl.downloader.bean

import com.hl.downloader.DownloadStatus
import java.io.File

/**
 * @author  张磊  on  2023/04/12 at 15:37
 * Email: 913305160@qq.com
 */
data class SubDownloadTaskBean(
	val downLoadUrl: String,
	val startPos: Long? = null,
	val endPos: Long? = null,
	var completeSize: Long = startPos ?: 0,
	var downloadStatus: DownloadStatus = DownloadStatus.READY_TO_DOWNLOAD,
	val saveFile: File
)