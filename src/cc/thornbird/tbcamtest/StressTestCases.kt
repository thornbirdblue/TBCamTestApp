package cc.thornbird.tbcamtest

import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.concurrent.Semaphore

/**
 * Created by thornbird on 2018/1/4.
 */
class StressTestCases(mCIF: CameraInfoCache) : CamTestCases {
    private var mCamTestReport: CamTestReport? = null
    private val StressTestCaseNum = 4
    private val OpenCloseStressNum = 1000
    private val StartStopPreviewStressNum = 1000
    private val takePictureStressNum = 500
    private val RecorderStressNum = 500
    private var mApi2Cam: Api2Camera? = null
    private var mPreviewView: SurfaceView? = null
    private var mPreviewHolder: SurfaceHolder? = null
    private val mPreviewTime = 500 // 500ms
    private val mRecordingTime = 1000

    private val mSemaphore = Semaphore(0)
    private val mCamTest: Boolean
    private val mRecorderTest: Boolean
    override fun doRunTestCases() {
        CamLogger.i(TAG, "doRunTestCases...")
        mCamTestReport!!.clearLastResult()
        if (mCamTest) {
            stressOpenOneCameraAndClose()
            stressStartAndStopPreview()
            stressTakePicture()
        } else CamLogger.i(TAG, "WARNING: Can't have CAMERA Permission.")
        if (mRecorderTest) stressRecording() else CamLogger.i(TAG, "WARNING: Can't have Recording Permission.")
        CamIsFinish()
        mCamTestReport!!.printTestResult()
        mSemaphore.release()
    }

    private fun stressOpenOneCameraAndClose() {
        CamLogger.i(TAG, "stressOpenOneCameraAndClose! ")
        for (i in 0 until OpenCloseStressNum) {
            CamLogger.d(TAG, "stressOpenOneCameraAndClose: $i")
            mApi2Cam!!.openCamera()
            mApi2Cam!!.closeCamera()
        }
        mCamTestReport!!.addTestResult("Stress Open Camera Test", mApi2Cam!!.OpsResult())
    }

    private fun stressStartAndStopPreview() {
        CamLogger.i(TAG, "StartStopPreviewStressNum! ")
        mApi2Cam!!.openCamera()
        for (i in 0 until OpenCloseStressNum) {
            CamLogger.d(TAG, "stressOpenOneCameraAndClose: $i")
            mApi2Cam!!.startPreview(mPreviewHolder!!.surface)
            mApi2Cam!!.stopPreview()
        }
        mApi2Cam!!.closeCamera()
        mCamTestReport!!.addTestResult("Stress Start and Stop Preview Test", mApi2Cam!!.OpsResult())
    }

    private fun stressTakePicture() {
        CamLogger.i(TAG, "stressTakePicture! ")
        mApi2Cam!!.openCamera()
        mApi2Cam!!.startPreview(mPreviewHolder!!.surface)
        try {
            Thread.sleep(mPreviewTime.toLong())
        } catch (e: InterruptedException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }
        for (i in 0 until takePictureStressNum) {
            CamLogger.d(TAG, "stressTakePicture: $i")
            mApi2Cam!!.takePicture()
        }
        mCamTestReport!!.addTestResult("takePicture Test", mApi2Cam!!.OpsResult())
        mApi2Cam!!.closeCamera()
    }

    private fun stressRecording() {
        CamLogger.i(TAG, "stressRecording! ")
        mApi2Cam!!.openCamera()
        for (i in 0 until RecorderStressNum) {
            CamLogger.d(TAG, "stressRecording: $i")
            mApi2Cam!!.startRecordingPreview(mPreviewHolder!!.surface)
            mApi2Cam!!.startRecording()
            try {
                Thread.sleep(mRecordingTime.toLong())
            } catch (e: InterruptedException) {
                // TODO Auto-generated catch block
                e.printStackTrace()
            }
            mApi2Cam!!.stopRecording()
        }
        mCamTestReport!!.addTestResult("Stress Recording Test", mApi2Cam!!.OpsResult())
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
        private const val TAG = "TBCamTest_StressTestCasess"
    }

    init {
        mCamTestReport = CamTestReport(StressTestCaseNum)
        mApi2Cam = Api2Camera(mCIF)
        mPreviewView = mCIF.previewSurface
        mPreviewHolder = mPreviewView!!.holder
        mCIF.setPreviewVisibility()
        mCamTest = mCIF.camSupport
        mRecorderTest = mCIF.recorderSupport
    }
}