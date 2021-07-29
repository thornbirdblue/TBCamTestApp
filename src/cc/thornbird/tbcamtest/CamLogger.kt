package cc.thornbird.tbcamtest

import android.util.Log

/**
 * Created by thornbird on 2017/12/29.
 */
object CamLogger {
    var LOG_LEVEL = 5
    var ERROR = 1
    var WARN = 2
    var INFO = 3
    var DEBUG = 4
    var VERBOS = 5
    fun e(tag: String?, msg: String?) {
        if (LOG_LEVEL > ERROR) Log.e(tag, msg)
    }

    fun e(tag: String?, msg: String?, tr: Throwable?) {
        if (LOG_LEVEL > ERROR) Log.e(tag, msg, tr)
    }

    fun w(tag: String?, msg: String?) {
        if (LOG_LEVEL > WARN) Log.w(tag, msg)
    }

    fun i(tag: String?, msg: String?) {
        if (LOG_LEVEL > INFO) Log.i(tag, msg)
    }

    fun d(tag: String?, msg: String?) {
        if (LOG_LEVEL > DEBUG) Log.d(tag, msg)
    }

    fun v(tag: String?, msg: String?) {
        if (LOG_LEVEL > VERBOS) Log.v(tag, msg)
    }
}