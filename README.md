## Downloader

​		 —— Android 平台简易的单任务多线程断点续传下载器





### 1. 介绍

此项目开发来源于工作中的一个需求： APP 更新下载支持断点续传。

此前因为自己未做过此方面的需求，所以一开始考虑找个三方库，但后来看到相关技术点需求，并非过于复杂，因此就考虑自己实现。

本项目使用 kotlin 语言开发，同时基于 okhttp 、gson、协程完成功能开发，断点续传主要使用请求头 `RANGE: bytes= start - end`结合 `RandomAccessFile` 实现，若您对其感兴趣可以上网查询相关知识。





### 2. 使用说明

- `DownloadManager`：提供下载管理，有以下几个方法：

    - `startDownLoad(context: Application, downloadUrl: String, downloadListener: DownloadListener, maxDownloadCore: Int = 5)`

        开始下载，其中 `downloadListener`  为下载回调通知，`maxDownloadCore` 为最大支持下载线程数

        下载回调通知如下，按需要复写对应方法即可：

        ```kotlin
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
        ```

    

    - `pauseDownload()` ：暂停下载
    - `resumeDownLoad`：恢复下载，需在暂停下载之后调用才会有效果
    - `cancelDownload`：取消下载，请注意取消后下载会从头开始，但杀死APP重新下载不会



通过调用 `DownloadManager` 以上的相关方法，您就已经可以实现简单的下载需求了，下载文件默认保存在：`/storage/emulated/0/Android/data/com.hl.downloader.deomo/files` 目录下，文件名默认以下载地址对应的文件命名，暂未提供修改下载地址和下载文件名的方法。



祝您使用愉快，若有使用问题或者完善建议欢迎提出issues！