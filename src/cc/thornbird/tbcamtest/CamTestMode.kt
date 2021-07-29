package cc.thornbird.tbcamtest

import android.os.Handler
import android.os.HandlerThread

/**
 * Created by thornbird on 2017/12/21.
 */
class CamTestMode(Mode: Int, mCamInfoCache: CameraInfoCache, private val mCamTestCallBack: CamTestCallBack) {
    private lateinit var mCamTestCases: CamTestCases
    private var isTestRunning = false
    private val mTestCaseThread: HandlerThread
    private val mTestCaseHandler: Handler

    interface CamTestCallBack {
        fun CamTestIsFinish(): Boolean?
    }

    fun run() {
        if (mCamTestCases == null) {
            CamLogger.e(TAG, "CamTestCases is NULL!!!")
            return
        }
        mTestCaseHandler.post {
            isTestRunning = true
            mCamTestCases.doRunTestCases()
            mCamTestCases.testIsFinish()
            mCamTestCallBack.CamTestIsFinish()
        }
    }

    fun stop() {
        if (mCamTestCases == null) {
            CamLogger.e(TAG, "CamTestCases is NULL!!!")
            return
        }
        if (isTestRunning) {
            mCamTestCases.stop()
            mTestCaseThread.quit()
            isTestRunning = false
        }
    }

    companion object {
        private const val TAG = "TBCamTest"
        const val TM_BaseTest_Mode = 1
        const val TM_BaseFuncTest_Mode = 2
        const val TM_FeatureTest_Mode = 3
        const val TM_AutoTest_Mode = 4
        const val TM_PerfTest_Mode = 5
        const val TM_StressTest_Mode = 6
    }

    init {
        mTestCaseThread = HandlerThread("CameraTestCaseThread")
        mTestCaseThread.start()
        mTestCaseHandler = Handler(mTestCaseThread.looper)
        when (Mode) {
            TM_BaseTest_Mode -> mCamTestCases = BaseTestCases(mCamInfoCache)
            TM_BaseFuncTest_Mode -> mCamTestCases = BaseFuncTestCases(mCamInfoCache)
            TM_FeatureTest_Mode -> mCamTestCases = BaseFeatureTestCases(mCamInfoCache)
            TM_AutoTest_Mode -> {
            }
            TM_PerfTest_Mode -> {
            }
            TM_StressTest_Mode -> mCamTestCases = StressTestCases(mCamInfoCache)
            //else -> mCamTestCases = null
        }
    }
}