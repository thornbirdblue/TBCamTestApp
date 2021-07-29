package cc.thornbird.tbcamtest

import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.concurrent.Semaphore

/**
 * Created by thornbird on 2018/1/6.
 */
class BaseFeatureTestCases(mCIF: CameraInfoCache) : CamTestCases {
    private var mCamTestReport: CamTestReport? = null
    private val BaseTestCaseNum = 3
    private var mApi2Cam: Api2Camera? = null
    private var mPreviewView: SurfaceView? = null
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
            testZSLYuvTakePicture()
            testZSLRawTakePicture()
            testZSLYuvReprocess()
        } else CamLogger.i(TAG, "WARNING: Can't have CAMERA Permission.")
        CamIsFinish()
        mCamTestReport!!.printTestResult()
        mSemaphore.release()
    }

    private fun testZSLYuvTakePicture() {
        CamLogger.i(TAG, "testZSLYuvTakePicture! ")
        mApi2Cam!!.openCamera()
        if (mApi2Cam!!.OpsResult() == false) {
            mCamTestReport!!.addTestResult("ZSLYuvTakePicture Test", false)
            return
        }
        mApi2Cam!!.startPreview(mPreviewHolder!!.surface, true, Api2Camera.Companion.ZSL_YUV)
        if (mApi2Cam!!.OpsResult() == false) {
            mCamTestReport!!.addTestResult("ZSLYuvTakePicture Test", false)
            return
        }
        try {
            Thread.sleep(mPreviewTime.toLong())
        } catch (e: InterruptedException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }
        mApi2Cam!!.takePicture(Api2Camera.Companion.ZSL_YUV)
        mCamTestReport!!.addTestResult("ZSLYuvTakePicture Test", mApi2Cam!!.OpsResult())
        mApi2Cam!!.closeCamera()
    }

    private fun testZSLRawTakePicture() {
        CamLogger.i(TAG, "testZSLRawTakePicture! ")
        if (mRawCaptureTest != true) {
            CamLogger.i(TAG, "Sensor can't support RAW TakePicture! ")
            mCamTestReport!!.addTestResult("ZSLRawTakePicture Test", false)
            return
        }
        mApi2Cam!!.openCamera()
        if (mApi2Cam!!.OpsResult() == false) {
            mCamTestReport!!.addTestResult("ZSLRawTakePicture Test", false)
            return
        }
        mApi2Cam!!.startPreview(mPreviewHolder!!.surface, true, Api2Camera.Companion.ZSL_RAW)
        if (mApi2Cam!!.OpsResult() == false) {
            mCamTestReport!!.addTestResult("ZSLRawTakePicture Test", false)
            return
        }
        try {
            Thread.sleep(mPreviewTime.toLong())
        } catch (e: InterruptedException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }
        mApi2Cam!!.takePicture(Api2Camera.Companion.ZSL_RAW)
        mCamTestReport!!.addTestResult("ZSLRawTakePicture Test", mApi2Cam!!.OpsResult())
        mApi2Cam!!.closeCamera()
    }

    private fun testZSLYuvReprocess() {
        CamLogger.i(TAG, "testZSLYuvReprocess! ")
        if (mYuvReprocessTest != true) {
            CamLogger.i(TAG, "Sensor can't support YUV Reprocess! ")
            mCamTestReport!!.addTestResult("testZSLYuvReprocess Test", false)
            return
        }
        mApi2Cam!!.openCamera()
        if (mApi2Cam!!.OpsResult() == false) {
            mCamTestReport!!.addTestResult("testZSLYuvReprocess Test", false)
            return
        }
        mApi2Cam!!.startPreview(mPreviewHolder!!.surface, true, Api2Camera.Companion.ZSL_YUV_REPROCESS)
        if (mApi2Cam!!.OpsResult() == false) {
            mCamTestReport!!.addTestResult("testZSLYuvReprocess Test", false)
            return
        }
        try {
            Thread.sleep(mPreviewTime.toLong())
        } catch (e: InterruptedException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }
        mApi2Cam!!.takePicture(Api2Camera.Companion.ZSL_YUV_REPROCESS)
        mCamTestReport!!.addTestResult("testZSLYuvReprocess Test", mApi2Cam!!.OpsResult())
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
        private const val TAG = "TBCamTest_BaseFeatureTestCases"
    }

    init {
        mCamTestReport = CamTestReport(BaseTestCaseNum)
        mApi2Cam = Api2Camera(mCIF)
        mPreviewView = mCIF.previewSurface
        mPreviewHolder = mPreviewView!!.holder
        mCIF.setPreviewVisibility()
        mCamTest = mCIF.camSupport
        mRecorderTest = mCIF.recorderSupport
        mRawCaptureTest = mCIF.rawSupport
        mYuvReprocessTest = mCIF.yuvReprocessSupport
    }
}