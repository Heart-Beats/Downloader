package com.hl.downloader

/**
 * @Author  张磊  on  2020/11/04 at 20:47
 * Email: 913305160@qq.com
 */
open class DownloadListener {

    open fun downloadError() {}

    open fun downloadComplete() {}

    open fun downloadIng(progress: Int) {}

    open fun downloadPause() {}

    open fun downloadCancel() {}
}