package com.hl.downloader

/**
 * @Author  张磊  on  2020/11/04 at 20:47
 * Email: 913305160@qq.com
 */
open class DownloadListener {

    /**
     * 下载错误
     */
    open fun downloadError() {}

    /**
     * 下载完成
     */
    open fun downloadComplete() {}

    /**
     * 下载中
     * 进度为保留两位小数去除尾数 0 的字符串：如：1.5, 10 , 99.99
     */
    open fun downloadIng(progress: String) {}

    /**
     * 下载暂停
     * 注意：当前下载已完成或已暂停，请求暂停不会受到此通知
     */
    open fun downloadPause() {}

    /**
     * 下载取消
     * 注意：当前下载已完成或已取消，请求取消不会受到此通知
     */
    open fun downloadCancel() {}
}