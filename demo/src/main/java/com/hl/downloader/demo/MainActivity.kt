package com.hl.downloader.demo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.hl.downloader.DownloadListener
import com.hl.downloader.DownloadManager
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onStart() {
        super.onStart()
        download.setOnClickListener {

            val downloadUrl = "http://down.qq.com/qqweb/QQ_1/android_apk/Androidqq_8.4.10.4875_537065980.apk"
            // String downloadUrl = "https://images.pexels.com/photos/4993088/pexels-photo-4993088.jpeg?cs=srgb&dl=pexels-rachel-claire-4993088.jpg&fm=jpg";
            DownloadManager.startDownLoad(
                this.application, downloadUrl, object : DownloadListener() {
                    override fun downloadIng(progress: String) {
                        this@MainActivity.displayInfo.text = "下载中$progress%"
                    }

                    override fun downloadError() {
                        this@MainActivity.displayInfo.text = "下载出错"
                    }

                    override fun downloadComplete() {
                        this@MainActivity.displayInfo.text = "下载完成"
                    }

                    override fun downloadPause() {
                        this@MainActivity.displayInfo.text = "下载暂停"
                    }

                    override fun downloadCancel() {
                        this@MainActivity.displayInfo.text = "下载取消"
                    }
                },
                3
            )
        }

        download.setOnLongClickListener {
            DownloadManager.cancelDownload()
            true
        }
    }
}