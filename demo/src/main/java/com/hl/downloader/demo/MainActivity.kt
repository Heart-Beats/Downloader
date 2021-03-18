package com.hl.downloader.demo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.hl.downloader.DownloadListener
import com.hl.downloader.DownloadManager
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private companion object {
        const val REQUEST_PERMISSIONS_CODE = 0x0001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onStart() {
        super.onStart()
        download.setOnClickListener {
            val needPermissions = listOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE
            ).filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }

            if (needPermissions.isEmpty()) {
                startDownloadTest()
            } else {
                ActivityCompat.requestPermissions(this, needPermissions.toTypedArray(), REQUEST_PERMISSIONS_CODE)
            }
        }

        download.setOnLongClickListener {
            DownloadManager.cancelDownload()
            true
        }
    }

    private fun startDownloadTest() {
        val downloadUrl = "http://down.qq.com/qqweb/QQ_1/android_apk/Androidqq_8.4.10.4875_537065980.apk"
        val externalFilesDir = this.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val saveFilePath = "$externalFilesDir/测试.apk"

        DownloadManager.startDownLoad(
            this.application, downloadUrl,
            saveFilePath = saveFilePath,
            downloadListener = object : DownloadListener() {
                override fun downloadIng(progress: String) {
                    this@MainActivity.displayInfo.text = "下载中$progress%"
                }

                override fun downloadError(error: Throwable?) {
                    this@MainActivity.displayInfo.text = "下载出错:${error?.message}"
                }

                override fun downloadComplete(downLoadFilePath: String) {
                    this@MainActivity.displayInfo.text = "下载完成--->$downLoadFilePath"
                }

                override fun downloadPause() {
                    this@MainActivity.displayInfo.text = "下载暂停"
                }

                override fun downloadCancel() {
                    this@MainActivity.displayInfo.text = "下载取消"
                }
            }
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_PERMISSIONS_CODE) {
            val noGrantResult = grantResults.filter { it != PackageManager.PERMISSION_GRANTED }
            if (noGrantResult.isEmpty()) {
                startDownloadTest()
            } else {
                val noGrantPermissionIndex = mutableListOf<Int>()
                grantResults.forEachIndexed { index, result ->
                    if (result in noGrantResult) noGrantPermissionIndex.add(index)
                }
                val noGrantPermissions = permissions.filterIndexed { index, _ ->
                    index in noGrantPermissionIndex
                }
                Toast.makeText(this, "$noGrantPermissions 这些权限未授予", Toast.LENGTH_SHORT).show()
            }
        }
    }
}