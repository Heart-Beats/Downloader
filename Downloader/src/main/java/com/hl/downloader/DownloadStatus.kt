package com.hl.downloader

/**
 * @Author  张磊  on  2020/11/04 at 15:33
 * Email: 913305160@qq.com
 */
enum class DownloadStatus(statusDesc: String) {
    READY_TO_DOWNLOAD("准备下载"),
    DOWNLOADING("下载中"),
    DOWNLOAD_COMPLETE("下载完成"),
    DOWNLOAD_PAUSE("下载暂停"),
    DOWNLOAD_CANCEL("取消下载"),
    DOWNLOAD_ERROR("下载出错")
}