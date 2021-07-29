package cc.thornbird.tbcamtest

import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.concurrent.Semaphore

/**
 * Created by thornbird on 2018/1/6.
 */
class BaseFuncTestCases(mCIF: CameraInfoCache) : CamTestCases {
    private var mCamTestReport: CamTestReport? = null
    private val BaseTestCaseNum = 1
    private var mApi2Cam: Api2Camera? = null
    private lateinit var mPreviewView: SurfaceView
    private var mPreviewHolder: SurfaceHolder? = null
    private val mPreviewTime = 500 // 500ms
    private val mRecordingTime = 1000
    private val mCamTest: Boolean
    private val mRecorderTest: Boolean
    private val mRawCaptureTest: Boolean
    private val mYuvReprocessTest: Boolean

    private val mSemaphore = Semaphore(0)
    override fun doRunTestCases() {
        CamLogger.i(TAG, "doRunTestCases...")
        mCamTestReport!!.clearLastResult()
        if (mCamTest) {
            testZSLJpegTakePicture()
        } else CamLogger.i(TAG, "WARNING: Can't have CAMERA Permission.")
        CamIsFinish()
        mCamTestReport!!.printTestResult()
        mSemaphore.release()
    }

    private fun testZSLJpegTakePicture() {
        CamLogger.i(TAG, "testZSLJpegTakePicture! ")
        mApi2Cam!!.openCamera()
        if (mApi2Cam!!.OpsResult() == false) {
            mCamTestReport!!.addTestResult("ZSLJpegTakePicture Test", mApi2Cam!!.OpsResult())
            return
        }
        mApi2Cam!!.startPreview(mPreviewHolder!!.surface, true, Api2Camera.ZSL_JPEG)
        if (mApi2Cam!!.OpsResult() == false) {
            mCamTestReport!!.addTestResult("ZSLJpegTakePicture Test", mApi2Cam!!.OpsResult())
            return
        }
        try {
            Thread.sleep(mPreviewTime.toLong())
        } catch (e: InterruptedException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }
        mApi2Cam!!.takePicture(Api2Camera.ZSL_JPEG)
        mCamTestReport!!.addTestResult("ZSLJpegTakePicture Test", mApi2Cam!!.OpsResult())
        mApi2Cam!!.closeCamera()
    }

    private fun CamIsFinish() {
        mApi2Cam!!.OpsIsFinish()
    }

    override fun stop() {
        CamLogger.d(TAG, "Stop camera!!! ")
        mApi2Cam!!.StopCamera()
    }

    override fun testIsFinish(): Boolean {
        try {
            mSemaphore.acquire()
        } catch (e: InterruptedException) {
            CamLogger.e(TAG, "Sem acquire ERROR!")
        }
        return true
    }

    companion object {
        private const val TAG = "TBCamTest_BaseFuncTestCases"
    }

    init {
        mCamTestReport = CamTestReport(BaseTestCaseNum)
        mApi2Cam = Api2Camera(mCIF)
        mPreviewView = mCIF.previewSurface
        mPreviewHolder = mPreviewView.getHolder()
        mCIF.setPreviewVisibility()
        mCamTest = mCIF.camSupport
        mRecorderTest = mCIF.recorderSupport
        mRawCaptureTest = mCIF.rawSupport
        mYuvReprocessTest = mCIF.yuvReprocessSupport
    }
}