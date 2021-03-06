package com.hl.downloader

/**
 * @Author  张磊  on  2020/11/04 at 15:33
 * Email: 913305160@qq.com
 */
enum class DownloadStatus {
    /**
     * 准备下载
     */
    READY_TO_DOWNLOAD,

    /**
     * 下载中
     */
    DOWNLOADING,

    /**
     * 下载完成
     */
    DOWNLOAD_COMPLETE,

    /**
     * 下载暂停
     */
    DOWNLOAD_PAUSE,

    /**
     * 取消下载
     */
    DOWNLOAD_CANCEL,

    /**
     * 下载出错
     */
    DOWNLOAD_ERROR
}