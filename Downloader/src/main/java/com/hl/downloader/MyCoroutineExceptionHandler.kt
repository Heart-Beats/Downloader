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
 * 捕获协程的异常，并定义重新请求的次数、延迟时间和动作
 * @see CoroutineExceptionHandler
 */
class MyCoroutineExceptionHandler(
    val allowRetryNum: Int = 5,
    val delayTimeMills: Long = 0,
    val retryAction: (CoroutineExceptionHandler) -> Unit = {}
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
        MainScope().launch {
            if (currentRetryNum++ < allowRetryNum) {
                //延迟执行，默认无延迟
                delay(delayTimeMills)
                retryAction(this@MyCoroutineExceptionHandler)
            }
        }
    }
}