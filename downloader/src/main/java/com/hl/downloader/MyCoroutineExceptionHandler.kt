package com.hl.downloader

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * @Author  张磊  on  2020/11/10 at 11:41
 * Email: 913305160@qq.com
 */

/**
 * 捕获协程的异常，并定义重新执行的次数、延迟时间和执行的动作
 *
 * @param allowRetryNum   重新尝试的次数(默认：5次)
 * @param delayTimeMills  每次重新尝试的延迟时间（默认：5000 ms）
 * @param errorPrint      回调即时抛出错误
 * @param retryAction     出错时执行的操作
 *
 * @see CoroutineExceptionHandler
 */
class MyCoroutineExceptionHandler(
    val allowRetryNum: Int = 5,
    val delayTimeMills: Long = 5 * 1000,
    val errorPrint: (Throwable) -> Unit = {},
    val retryAction: suspend () -> Unit = {}
) : AbstractCoroutineContextElement(CoroutineExceptionHandler), CoroutineExceptionHandler {

    companion object {
        private const val TAG = "MyCoroutineExceptionHan"

        private var currentRetryNum = 0
    }

    init {
        //初始化时重置次数
        currentRetryNum = 0
    }


    override fun handleException(context: CoroutineContext, exception: Throwable) {
        Log.e(TAG, "运行异常", exception)
        MainScope().launch(this) {
            if (currentRetryNum++ < allowRetryNum) {
                errorPrint(exception)
                //延迟执行
                delay(delayTimeMills)
                retryAction()
            }
        }
    }
}