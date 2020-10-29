package cc.thornbird.tbcamtest

import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.concurrent.Semaphore

/**
 * Created by thornbird on 2017/12/21.
 */
class BaseTestCases(mCIF: CameraInfoCache) : CamTestCases {
    private val BaseTestCaseNum = 4

    private lateinit var mApi2Cam: Api2Camera
    private lateinit var mCamTestReport: CamTestReport

    private lateinit var mPreviewView: SurfaceView
    private lateinit var mPreviewHolder: SurfaceHolder

    private val mPreviewTime = 500 // 500ms
    private val mRecordingTime = 2000
    private val mCamTest: Boolean
    private val mRecorderTest: Boolean

    private val mSemaphore = Semaphore(0)
    override fun doRunTestCases() {
        CamLogger.i(TAG, "doRunTestCases...")
        mCamTestReport.clearLastResult()
        if (mCamTest) {
            testOpenOneCameraAndClose()
            testStartPreview()
            testTakePicture()
        } else CamLogger.i(TAG, "WARNING: Can't have CAMERA Permission.")
        if (mRecorderTest) testRecording() else CamLogger.i(TAG, "WARNING: Can't have Recording Permission.")
        CamIsFinish()
        mCamTestReport.printTestResult()
        mSemaphore.release()
    }

    private fun testOpenOneCameraAndClose() {
        CamLogger.i(TAG, "testOpenOneCameraAndClose! ")
        mApi2Cam.openCamera()
        mCamTestReport.addTestResult("Open Camera Test", mApi2Cam.OpsResult())
        mApi2Cam.closeCamera()
    }

    private fun testStartPreview() {
        CamLogger.i(TAG, "testStartPreview! ")
        mApi2Cam.openCamera()
        if (mApi2Cam.OpsResult() == false) {
            mCamTestReport.addTestResult("StartPreview Test", mApi2Cam.OpsResult())
            return
        }
        mApi2Cam.startPreview(mPreviewHolder!!.surface)
        mCamTestReport.addTestResult("StartPreview Test", mApi2Cam.OpsResult())
        try {
            Thread.sleep(mPreviewTime.toLong())
        } catch (e: InterruptedException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }
        mApi2Cam.stopPreview()
        mApi2Cam.closeCamera()
    }

    private fun testTakePicture() {
        CamLogger.i(TAG, "testTakePicture! ")
        mApi2Cam.openCamera()
        if (mApi2Cam.OpsResult() == false) {
            mCamTestReport.addTestResult("StartPreview Test", mApi2Cam.OpsResult())
            return
        }
        mApi2Cam.startPreview(mPreviewHolder!!.surface)
        try {
            Thread.sleep(mPreviewTime.toLong())
        } catch (e: InterruptedException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }
        mApi2Cam.takePicture()
        mCamTestReport.addTestResult("takePicture Test", mApi2Cam.OpsResult())
        mApi2Cam.closeCamera()
    }

    private fun testRecording() {
        CamLogger.i(TAG, "testRecording! ")
        mApi2Cam.openCamera()
        if (mApi2Cam.OpsResult() == false) {
            mCamTestReport.addTestResult("testRecording Open Test", false)
            return
        }
        mApi2Cam.startRecordingPreview(mPreviewHolder!!.surface)
        if (mApi2Cam.OpsResult() == true) {
            mApi2Cam.startRecording()
            mCamTestReport.addTestResult("startRecording Test", mApi2Cam.OpsResult())
            try {
                Thread.sleep(mRecordingTime.toLong())
            } catch (e: InterruptedException) {
                // TODO Auto-generated catch block
                e.printStackTrace()
            }
            mApi2Cam.stopRecording()
        } else mCamTestReport.addTestResult("startRecording Test", false)
        mApi2Cam.closeCamera()
    }

    private fun CamIsFinish() {
        mApi2Cam.OpsIsFinish()
    }

    override fun stop() {
        CamLogger.d(TAG, "Stop camera!!! ")
        mApi2Cam.StopCamera()
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
        private const val TAG = "TBCamTest_BaseTestCases"
    }

    init {
        mCamTestReport = CamTestReport(BaseTestCaseNum)
        mApi2Cam = Api2Camera(mCIF)
        mPreviewView = mCIF.previewSurface
        mPreviewHolder = mPreviewView!!.holder
        mCIF.setPreviewVisibility()
        mCamTest = mCIF.camSupport
        mRecorderTest = mCIF.recorderSupport
    }
}