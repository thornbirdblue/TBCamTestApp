package cc.thornbird.tbcamtest

import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.hardware.camera2.params.InputConfiguration
import android.media.Image
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.media.ImageWriter
import android.media.MediaRecorder
import android.os.ConditionVariable
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Size
import android.view.Surface
import java.io.IOException
import java.util.*

/**
 * Created by thornbird on 2017/12/21.
 */
class Api2Camera(mCIF: CameraInfoCache) : CameraInterface {
    private enum class CamCmd {
        CAM_NULL, CAM_OPEN, CAM_CLOSE, CAM_STARTPREVIEW, CAM_TAKEPICTURE, CAM_STOPPREVIEW, CAM_STARTRECORDING, CAM_STOPRECORDING
    }

    private enum class CamCmdResult {
        CAM_OP_SUCCESS, CAM_OP_FALSE
    }

    private var mCamCmd: CamCmd? = null
    private lateinit var mCamOpResult: CamCmdResult
    private lateinit var mCamInfo: CameraInfoCache
    private lateinit var mCameraManager: CameraManager
    private var mCameraDevice: CameraDevice? = null
    private var mCurrentCaptureSession: CameraCaptureSession? = null

    @Volatile
    private lateinit var mPreviewSurface: Surface
    private val mOpsThread: HandlerThread
    private val mOpsHandler: Handler
    private var mJpegListenerThread: HandlerThread? = null
    private var mJpegListenerHandler: Handler? = null

    //    private volatile Semaphore mSemaphore = new Semaphore(1);             // 2018-04-22 not use
    @Volatile
    private var mAllThingsInitialized = false
    private var mFirstFrameArrived = false
    private var mZslMode = false
    private var mZslFlag = 0
    private var mZslTPFlag = 0
    private var mYuvReproNeed = false
    private lateinit var mJpegImageReader: ImageReader
    private lateinit var mYuvImageReader: ImageReader
    private var mYuvImageCounter = 0
    private lateinit var mRawImageReader: ImageReader
    private var mYuvLastReceivedImage: Image? = null
    private var mRawLastReceivedImage: Image? = null
    private var mReprocessingRequestNanoTime: Long = 0

    //Reprocess
    private var mLastTotalCaptureResult: TotalCaptureResult? = null
    private var mImageWriter: ImageWriter? = null
    private var mVideoSize: Size? = null
    private lateinit var mMediaRecorder: MediaRecorder
    private var mIsRecordingVideo = false

    // Used for saving JPEGs.
    private val mUtilityThread: HandlerThread
    private val mUtilityHandler: Handler
    private val mOpsCondittion: ConditionVariable
    private val mCameraStateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            CameraTime.t_open_end = SystemClock.elapsedRealtime()
            mCameraDevice = camera
            CamLogger.d(TAG, "STARTUP_REQUIREMENT Done opening camera " + mCamInfo?.cameraId +
                    ". HAL open took: (" + (CameraTime.t_open_end - CameraTime.t_open_start) + " ms)")
            CamOpsFinish(CamCmd.CAM_OPEN, CamCmdResult.CAM_OP_SUCCESS)
        }

        override fun onClosed(camera: CameraDevice) {
            CamLogger.d(TAG, "onClosed: Done Closing camera " + mCamInfo?.cameraId)
            CamOpsFinish(CamCmd.CAM_CLOSE, CamCmdResult.CAM_OP_SUCCESS)
        }

        override fun onDisconnected(camera: CameraDevice) {
            CamLogger.d(TAG, "onDisconnected")
        }

        override fun onError(camera: CameraDevice, error: Int) {
            CamLogger.e(TAG, "CameraDevice onError error val is $error. CMD is $mCamCmd")
            CamOpsFailed()
        }
    }
    private val mSessionStateCallback: CameraCaptureSession.StateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            mCurrentCaptureSession = session
            CamLogger.d(TAG, "capture session onConfigured().")
            PreviewCaptureRequest()
        }

        override fun onReady(session: CameraCaptureSession) {
            CamLogger.d(TAG, "capture session onReady().  HAL capture session took: (" + (SystemClock.elapsedRealtime() - CameraTime.t_session_go) + " ms)")
            mImageWriter = if (mYuvReproNeed) ImageWriter.newInstance(session.inputSurface, 2) else null
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            CamLogger.e(TAG, "onConfigureFailed")
            CamOpsFailed()
        }
    }
    private val mJpegSessionStateCallback: CameraCaptureSession.StateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            mCurrentCaptureSession = session
            CamLogger.d(TAG, "JPEG capture session onConfigured().")
            JpegCaptureRequest()
        }

        override fun onReady(session: CameraCaptureSession) {
            CamLogger.d(TAG, "JPEG capture session onReady().  HAL capture session took: (" + (SystemClock.elapsedRealtime() - CameraTime.t_session_go) + " ms)")
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            CamLogger.e(TAG, "JPEG onConfigureFailed")
            CamOpsFailed()
        }
    }
    private val mVideoSessionStateCallback: CameraCaptureSession.StateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            mCurrentCaptureSession = session
            CamLogger.d(TAG, "VIDEO capture session onConfigured().")
            VideoPreviewCaptureRequest()
        }

        override fun onReady(session: CameraCaptureSession) {
            CamLogger.d(TAG, "VIDEO capture session onReady().  HAL capture session took: (" + (SystemClock.elapsedRealtime() - CameraTime.t_session_go) + " ms)")
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            CamLogger.d(TAG, "VIDEO onConfigureFailed")
            CamOpsFailed()
        }
    }
    private val mCaptureCallback: CaptureCallback = object : CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            if (!mFirstFrameArrived) {
                CamOpsFinish(CamCmd.CAM_STARTPREVIEW, CamCmdResult.CAM_OP_SUCCESS)
                mFirstFrameArrived = true
                val now = SystemClock.elapsedRealtime()
                val dt = now - CameraTime.t0
                val camera_dt = now - CameraTime.t_session_go + CameraTime.t_open_end - CameraTime.t_open_start
                val repeating_req_dt = now - CameraTime.t_burst
                CamLogger.d(TAG, "App control to first frame: ($dt ms)")
                CamLogger.d(TAG, "HAL request to first frame: ($repeating_req_dt ms)  Total HAL wait: ($camera_dt ms)")
            } else CamLogger.v(TAG, "Receive Preview Data!")
        }
    }
    private val mZslCaptureCallback: CaptureCallback = object : CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            CamLogger.d(TAG, "ZSL Capture Complete!!!")
            mLastTotalCaptureResult = result
            if (mYuvReproNeed) takeYuvReprocessPicture()
        }
    }

    var mYuvImageListener: ImageReader.OnImageAvailableListener

    var mJpegImageListener = OnImageAvailableListener { reader ->
        val img = reader.acquireLatestImage()
        if (img == null) {
            CamLogger.e(TAG, "Null image returned JPEG")
            return@OnImageAvailableListener
        }
        val plane0 = img.planes[0]
        val buffer = plane0.buffer
        val dt = System.nanoTime() - mReprocessingRequestNanoTime
        CamLogger.d(TAG, String.format("JPEG buffer available, w=%d h=%d time=%d size=%d dt=%.1f ms",
                img.width, img.height, img.timestamp, buffer.capacity(), 0.000001 * dt))
        // Save JPEG on the utility thread,
        val jpegBuf: ByteArray
        if (buffer.hasArray()) {
            jpegBuf = buffer.array()
        } else {
            jpegBuf = ByteArray(buffer.capacity())
            buffer[jpegBuf]
        }
        mCamInfo!!.saveFile(jpegBuf, img.width, img.height, 0)
        img.close()
        CamOpsFinish(CamCmd.CAM_TAKEPICTURE, CamCmdResult.CAM_OP_SUCCESS)
    }

    var mRawImageListener = OnImageAvailableListener { reader ->
        val img = reader.acquireLatestImage()
        if (img == null) {
            CamLogger.e(TAG, "Null image returned YUV1")
            return@OnImageAvailableListener
        }
        if (mRawLastReceivedImage != null) {
            mRawLastReceivedImage!!.close()
        }
        val plane0 = img.planes[0]
        val buffer = plane0.buffer
        val DateBuf: ByteArray
        if (buffer.hasArray()) {
            DateBuf = buffer.array()
        } else {
            DateBuf = ByteArray(buffer.capacity())
            buffer[DateBuf]
        }
        mCamInfo!!.saveFile(DateBuf, img.width, img.height, 2)
        mRawLastReceivedImage = img
        CamLogger.d(TAG, "mRawImageListener RECIEVE img!!!")
        CamOpsFinish(CamCmd.CAM_TAKEPICTURE, CamCmdResult.CAM_OP_SUCCESS)
    }

    private fun InitApi2Val() {
        if (mCurrentCaptureSession != null) {
            mCurrentCaptureSession!!.close()
            mCurrentCaptureSession = null
        }
        if (mCameraDevice != null) {
            mCameraDevice!!.close()
            mCameraDevice = null
        }
        mZslMode = false
    }

    private fun InitializeAllTheThings() {
        if(mCamInfo.jpegStreamSupport) {
            // Thread to handle returned JPEGs.
            mJpegListenerThread = HandlerThread("CameraJpegThread")
            mJpegListenerThread!!.start()
            mJpegListenerHandler = Handler(mJpegListenerThread!!.looper)

            // Create ImageReader to receive JPEG image buffers via reprocessing.
            mJpegImageReader = ImageReader.newInstance(
                    mCamInfo!!.yuvStreamSize!!.width,
                    mCamInfo!!.yuvStreamSize!!.height,
                    ImageFormat.JPEG,
                    2)
            mJpegImageReader.setOnImageAvailableListener(mJpegImageListener, mJpegListenerHandler)
        }

        if(mCamInfo.yuvStreamSupport) {
            mYuvImageReader = ImageReader.newInstance(
                    mCamInfo!!.yuvStreamSize!!.width,
                    mCamInfo!!.yuvStreamSize!!.height,
                    ImageFormat.YUV_420_888,
                    YUV_IMAGEREADER_SIZE)
            mYuvImageReader.setOnImageAvailableListener(mYuvImageListener, mOpsHandler)
        }

        if(mCamInfo.rawStreamSupport) {
            CamLogger.d(TAG, "RAW SIZE:" + mCamInfo!!.rawStreamSize!!.height + mCamInfo!!.rawStreamSize!!.width)
            mRawImageReader = ImageReader.newInstance(
                mCamInfo!!.rawStreamSize!!.width,
                mCamInfo!!.rawStreamSize!!.height,
                mCamInfo!!.rawStreamFormat,  //ImageFormat.RAW10
                8)
            mRawImageReader.setOnImageAvailableListener(mRawImageListener, null)
        }
    }

    private fun CamOpsReq(cmd: CamCmd) {
//        try {
//            mSemaphore.acquire();
        CamLogger.v(TAG, "CAM CMD:$mCamCmd")
        mOpsCondittion.block()
        mOpsCondittion.close()
        mCamOpResult = CamCmdResult.CAM_OP_FALSE
        mCamCmd = cmd
        //        } catch (InterruptedException e) {
//            CamLogger.e(TAG,"Sem acquire ERROR!");
//        }
    }

    private fun CamOpsFinish() {
        CamLogger.v(TAG, "OP($mCamCmd) Block($OP_TIMEOUT) for finish!: ")
        mOpsCondittion.block(OP_TIMEOUT.toLong())
        if (mCamOpResult == CamCmdResult.CAM_OP_FALSE) {
            CamLogger.e(TAG, "ERROR:OP($mCamCmd) TIMEOUT!!!")
            mOpsCondittion.open()
            CamLogger.e(TAG, "TIMEOUT will StopCamera!!!")
            StopCamera()
        }
    }

    private fun CamOpsFinish(cmd: CamCmd?, result: CamCmdResult) {
        mCamOpResult = result
        if (mCamCmd == cmd) {
            CamLogger.v(TAG, "Release All Condittion!!!!")
            mOpsCondittion.open()
        }
        if (mCamOpResult == CamCmdResult.CAM_OP_SUCCESS) {
            CamLogger.d(TAG, "Cam OP($mCamCmd) SUCCESS!!!")
        } else if (mCamOpResult == CamCmdResult.CAM_OP_FALSE) {
            CamLogger.e(TAG, "Cam CMD: $mCamCmd Result is FALSE!!!")
        } else {
            CamLogger.e(TAG, "Cam result is INVAL!!!")
        }

//        mSemaphore.release();
        CamLogger.v(TAG, "Last Cam cmd is finish!")
    }

    private fun CamOpsFailed() {
        CamLogger.e(TAG, "CMD($mCamCmd) OP is Failed!!!")
        CamOpsFinish(mCamCmd, CamCmdResult.CAM_OP_FALSE)
    }

    override fun openCamera() {
        CamLogger.d(TAG, "STARTUP_REQUIREMENT opening camera " + mCamInfo!!.cameraId)
        CamOpsReq(CamCmd.CAM_OPEN)
        mOpsHandler.post {
            CameraTime.t_open_start = SystemClock.elapsedRealtime()
            try {
                mCameraManager.openCamera(mCamInfo.cameraId, mCameraStateCallback, null)
            } catch (e: CameraAccessException) {
                CamLogger.e(TAG, "Unable to openCamera().")
                CamOpsFailed()
            }
        }
        CamOpsFinish()
    }

    override fun closeCamera() {
        CamLogger.d(TAG, "Closing camera " + mCamInfo!!.cameraId)
        CamOpsReq(CamCmd.CAM_CLOSE)
        mOpsHandler.post {
            if (mCameraDevice != null) {
                if (mCurrentCaptureSession != null) {
                    try {
                        mCurrentCaptureSession!!.abortCaptures()
                    } catch (e: CameraAccessException) {
                        CamLogger.e(TAG, "closeCamera Could not abortCaptures().")
                    }
                    mCurrentCaptureSession = null
                }
                mCameraDevice!!.close()
            }
        }
        CamOpsFinish()
    }

    fun StopCamera() {
        CamLogger.d(TAG, "StopCamera: " + mCamInfo!!.cameraId)
        mUtilityThread.quit()
        mOpsThread.quit()
        if (mCameraDevice != null) {
            if (mCurrentCaptureSession != null) {
                try {
                    mCurrentCaptureSession!!.abortCaptures()
                } catch (e: CameraAccessException) {
                    CamLogger.e(TAG, "StopCamera Could not abortCaptures().")
                    mCurrentCaptureSession = null
                    CamOpsFailed()
                    return
                }
                mCurrentCaptureSession = null
            }
            mCameraDevice!!.close()
        }
        CamLogger.v(TAG, "StopCamera!!! ")
    }

    override fun startPreview(surface: Surface) {
        mPreviewSurface = surface
        mZslMode = false
        mZslFlag = 0
        CamStartPreview()
    }

    fun stopPreview() {
        CamLogger.v(TAG, "stopPreview!!! ")
        if (mZslMode == true) CamLogger.v(TAG, "ZSL Mode stopPreview!!! ") else CamLogger.v(TAG, "NON ZSL Mode stopPreview!!! ")
        CaptureSessionStop(mCurrentCaptureSession)
        CamOpsReq(CamCmd.CAM_STOPPREVIEW)
        mCurrentCaptureSession = null
        CamOpsFinish(CamCmd.CAM_STOPPREVIEW, CamCmdResult.CAM_OP_SUCCESS)
    }

    private fun CaptureSessionStop(mCCS: CameraCaptureSession?) {
        if (mCCS != null) {
            try {
                mCCS.abortCaptures()
                mCCS.stopRepeating()
                mCCS.close()
            } catch (e: CameraAccessException) {
                CamLogger.e(TAG, "Could not abortCaptures().")
            }
        }
    }

    override fun startPreview(surface: Surface, ZslMode: Boolean, ZslFlag: Int) {
        mPreviewSurface = surface
        mZslMode = ZslMode
        mZslFlag = ZslFlag
        mYuvReproNeed = if (mZslFlag and ZSL_YUV_REPROCESS == ZSL_YUV_REPROCESS) true else false
        CamStartPreview()
    }

    private fun CamStartPreview() {
        CamLogger.d(TAG, "Start CamStartPreview..")
        CamOpsReq(CamCmd.CAM_STARTPREVIEW)
        mOpsHandler.post {
            if (mCameraDevice != null && mPreviewSurface != null) {
                // It used to be: this needed to be posted on a Handler.
                startCaptureSession()
            }
        }
        CamOpsFinish()
    }

    private fun startCaptureSession() {
        CameraTime.t_session_go = SystemClock.elapsedRealtime()
        CamLogger.d(TAG, "Start Configuring CaptureSession..")
        val outputSurfaces: MutableList<Surface?> = ArrayList(3)
        outputSurfaces.add(mPreviewSurface)
        ZslCaptureSession(outputSurfaces)
        try {
            if (mYuvReproNeed) {
                CamLogger.d(TAG, "  createReprocessableCaptureSession ...")
                val inputConfig = InputConfiguration(mCamInfo!!.yuvStreamSize!!.width,
                        mCamInfo!!.yuvStreamSize!!.height, ImageFormat.YUV_420_888)
                mCameraDevice!!.createReprocessableCaptureSession(inputConfig, outputSurfaces,
                        mSessionStateCallback, null)
            } else {
                CamLogger.d(TAG, "  createNormalCaptureSession ...")
                mCameraDevice!!.createCaptureSession(outputSurfaces, mSessionStateCallback, null)
            }
            CamLogger.v(TAG, "  Call to createCaptureSession complete.")
        } catch (e: CameraAccessException) {
            CamLogger.e(TAG, "Error configuring ISP.")
            CamOpsFailed()
        }
    }

    private fun ZslCaptureSession(surfaceList: MutableList<Surface?>) {
        if (mYuvReproNeed) {
            CamLogger.d(TAG, "CaptureSession Add YUV REPROCESS Stream.")
            surfaceList.add(mYuvImageReader!!.surface)
            surfaceList.add(mJpegImageReader!!.surface)
        } else {
            if (mZslFlag and ZSL_JPEG == ZSL_JPEG) {
                CamLogger.d(TAG, "CaptureSession Add Jpeg Stream.")
                surfaceList.add(mJpegImageReader!!.surface)
            }
            if (mZslFlag and ZSL_YUV == ZSL_YUV) {
                CamLogger.d(TAG, "CaptureSession Add YUV Stream.")
                surfaceList.add(mYuvImageReader!!.surface)
            }
        }
        if (mZslFlag and ZSL_RAW == ZSL_RAW) {
            CamLogger.d(TAG, "CaptureSession Add RAW Stream.")
            if(mCamInfo.rawStreamSupport)
                surfaceList.add(mRawImageReader!!.surface)
            else
                CamLogger.e(TAG, "ERROR:Can't support RAW")
        }
    }

    private fun PreviewCaptureRequest() {
        CameraTime.t_burst = SystemClock.elapsedRealtime()
        CamLogger.d(TAG, "PreviewCaptureRequest...")
        try {
            mFirstFrameArrived = false
            val b1 = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            b1.addTarget(mPreviewSurface)
            mCurrentCaptureSession!!.setRepeatingRequest(b1.build(), mCaptureCallback, mOpsHandler)
        } catch (e: CameraAccessException) {
            CamLogger.e(TAG, "Could not access camera for issuePreviewCaptureRequest.")
            CamOpsFailed()
        }
    }

    private fun JpegCaptureRequest() {
        CameraTime.t_burst = SystemClock.elapsedRealtime()
        CamLogger.d(TAG, "JpegCaptureRequest...")
        try {
            val b1 = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            b1.addTarget(mJpegImageReader!!.surface)
            mCurrentCaptureSession!!.capture(b1.build(), null, mOpsHandler)
        } catch (e: CameraAccessException) {
            CamLogger.e(TAG, "Could not access camera for issuePreviewCaptureRequest.")
            CamOpsFailed()
        }
    }

    override fun takePicture() {
        CamLogger.v(TAG, "takePicture..")
        CamOpsReq(CamCmd.CAM_TAKEPICTURE)
        mOpsHandler.post {
            mReprocessingRequestNanoTime = System.nanoTime()
            if (mZslMode) {
                CamLogger.e(TAG, "ERROR: Preview is ZSL Mode.Then Use takePicture(int ZslFlag) to Capture!!!")
                CamOpsFailed()
            } else takeCapturePicture()
        }
        CamOpsFinish()
    }

    private fun takeCapturePicture() {
        JpegPictureCaptureSession()
    }

    private fun JpegPictureCaptureSession() {
        CameraTime.t_session_go = SystemClock.elapsedRealtime()
        CamLogger.v(TAG, "Start Configuring JpegPictureCaptureSession..")
        val outputSurfaces: MutableList<Surface> = ArrayList(3)
        outputSurfaces.add(mJpegImageReader!!.surface)
        try {
            mCameraDevice!!.createCaptureSession(outputSurfaces, mJpegSessionStateCallback, null)
            CamLogger.v(TAG, "Call to JpegPictureCaptureSession complete.")
        } catch (e: CameraAccessException) {
            CamLogger.e(TAG, "Error configuring ISP.")
            CamOpsFailed()
        }
    }

    fun takePicture(ZslFlag: Int) {
        CamLogger.v(TAG, "takePicture..")
        CamOpsReq(CamCmd.CAM_TAKEPICTURE)
        mZslTPFlag = ZslFlag
        mOpsHandler.post {
            mReprocessingRequestNanoTime = System.nanoTime()
            if (mZslMode) {
                takeZslPicture(mZslTPFlag)
            } else CamLogger.e(TAG, "ERROR: StartPreview is NON ZSL Mode!!!")
        }
        CamOpsFinish()
    }

    private fun takeZslPicture(Flag: Int) {
        CamLogger.d(TAG, "takeZslPicture Flag: $Flag")
        var bl: CaptureRequest.Builder? = null
        try {
            bl = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        } catch (e: CameraAccessException) {
            CamLogger.e(TAG, "takeZslPicture Error configuring ISP.")
            CamOpsFailed()
        }
        if (bl != null) {
            if (mZslFlag and ZSL_JPEG == ZSL_JPEG) {
                CamLogger.d(TAG, "Jpeg ZslTakePicture...")
                bl.addTarget(mJpegImageReader!!.surface)
            } else if (mZslFlag and ZSL_YUV == ZSL_YUV) {
                CamLogger.d(TAG, "YUV ZslTakePicture...")
                bl.addTarget(mYuvImageReader!!.surface)
            } else if (mZslFlag and ZSL_RAW == ZSL_RAW) {
                CamLogger.d(TAG, "RAW ZslTakePicture...")
                if(mCamInfo.rawStreamSupport)
                    bl.addTarget(mRawImageReader!!.surface)
                else
                    CamLogger.e(TAG, "ERROR:Can't support RAW")
            } else if (mZslFlag and ZSL_YUV_REPROCESS == ZSL_YUV_REPROCESS) {
                CamLogger.d(TAG, "YUV Reprocess ZslTakePicture...")
                bl.addTarget(mYuvImageReader!!.surface)
            }
            try {
                mCurrentCaptureSession!!.capture(bl.build(), mZslCaptureCallback, null)
            } catch (e: CameraAccessException) {
                // TODO Auto-generated catch block
                e.printStackTrace()
                CamLogger.e(TAG, "Could not access camera for issuePreviewCaptureRequest.")
                CamOpsFailed()
            }
        } else {
            CamLogger.e(TAG, "ERROR: takeZslPicture can't createCaptureRequest!!!")
            CamOpsFailed()
        }
        CamLogger.v(TAG, "takeZslPicture------------------------END")
    }

    private fun takeYuvReprocessPicture() {
        CamLogger.d(TAG, "takeReprocessPicture...")
        if (mYuvLastReceivedImage == null) {
            CamLogger.w(TAG, "No Last YUV Image: Can't need to Reprocess!!!")
            return
        }
        mImageWriter!!.queueInputImage(mYuvLastReceivedImage)
        var bl: CaptureRequest.Builder? = null
        try {
            bl = mCameraDevice!!.createReprocessCaptureRequest(mLastTotalCaptureResult)
        } catch (e: CameraAccessException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
            DeviceCreateRequestError()
        }
        bl!!.set(CaptureRequest.JPEG_QUALITY, 95.toByte())
        bl.addTarget(mJpegImageReader!!.surface)
        try {
            mCurrentCaptureSession!!.capture(bl.build(), null, null)
        } catch (e: CameraAccessException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
            SessionCaptureError()
        }
        CamLogger.d(TAG, "takeReprocessPicture------------------------END")
    }

    private fun DeviceCreateRequestError() {
        CamLogger.e(TAG, "takeReprocessPicture Error configuring ISP.")
        CamOpsFailed()
    }

    private fun SessionCaptureError() {
        CamLogger.e(TAG, "ERROR: CaptureSession could not capture!!!.")
        CamOpsFailed()
    }

    override fun startRecordingPreview(surface: Surface) {
        CamLogger.d(TAG, "Start CamStartVideoPreview..")
        CamOpsReq(CamCmd.CAM_STARTPREVIEW)
        mPreviewSurface = surface
        mZslMode = false
        try {
            setUpMediaRecorder()
        } catch (e: IOException) {
            e.printStackTrace()
            CamOpsFailed()
            return
        }
        CamStartVideoPreview()
        CamOpsFinish()
    }

    private fun CamStartVideoPreview() {
        mOpsHandler.post {
            if (mCameraDevice != null && mPreviewSurface != null && mMediaRecorder != null) {
                // It used to be: this needed to be posted on a Handler.
                startVideoCaptureSession()
            }
        }
    }

    @Throws(IOException::class)
    private fun setUpMediaRecorder() {
        mMediaRecorder = MediaRecorder()
        mVideoSize = mCamInfo!!.videoStreamSize
        mMediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
        mMediaRecorder!!.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mMediaRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mMediaRecorder.setOutputFile(mCamInfo!!.videoFilePath)
        mMediaRecorder!!.setVideoEncodingBitRate(10000000)
        mMediaRecorder!!.setVideoFrameRate(30)
        mMediaRecorder!!.setVideoSize(mVideoSize!!.width, mVideoSize!!.height)
        CamLogger.d(TAG, "Video Size: " + mVideoSize!!.width + "x" + mVideoSize!!.height)
        mMediaRecorder!!.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mMediaRecorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        //        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
//        int orientation = ORIENTATIONS.get(rotation);
//        mMediaRecorder.setOrientationHint(orientation);
        mMediaRecorder!!.prepare()
    }

    private fun startVideoCaptureSession() {
        CameraTime.t_session_go = SystemClock.elapsedRealtime()
        CamLogger.d(TAG, "Start Configuring Video CaptureSession..")
        val outputSurfaces: MutableList<Surface?> = ArrayList(3)
        outputSurfaces.add(mPreviewSurface)
        outputSurfaces.add(mMediaRecorder!!.surface)
        try {
            mCameraDevice!!.createCaptureSession(outputSurfaces, mVideoSessionStateCallback, null)
            CamLogger.v(TAG, "  Call to startVideoCaptureSession complete.")
        } catch (e: CameraAccessException) {
            CamLogger.e(TAG, "Error configuring ISP.")
            CamOpsFailed()
        }
    }

    private fun VideoPreviewCaptureRequest() {
        CameraTime.t_burst = SystemClock.elapsedRealtime()
        CamLogger.d(TAG, "VideoPreviewCaptureRequest...")
        try {
            mFirstFrameArrived = false
            val b1 = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            b1.addTarget(mPreviewSurface)
            val recorderSurface = mMediaRecorder!!.surface
            b1.addTarget(recorderSurface)
            mCurrentCaptureSession!!.setRepeatingRequest(b1.build(), mCaptureCallback, mOpsHandler)
        } catch (e: CameraAccessException) {
            CamLogger.e(TAG, "Could not access camera for issuePreviewCaptureRequest.")
            CamOpsFailed()
        }
    }

    override fun startRecording() {
        try {
            CamOpsReq(CamCmd.CAM_STARTRECORDING)
            // UI
            mIsRecordingVideo = true

            // Start recording
            CamLogger.v(TAG, "  startRecording...")
            mMediaRecorder!!.start()
            CamOpsFinish(CamCmd.CAM_STARTRECORDING, CamCmdResult.CAM_OP_SUCCESS)
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            CamOpsFailed()
        }
    }

    override fun stopRecording() {
        CamOpsReq(CamCmd.CAM_STOPRECORDING)
        // UI
        mIsRecordingVideo = false
        // Stop recording
        mMediaRecorder!!.stop()
        mMediaRecorder!!.reset()
        CamOpsFinish(CamCmd.CAM_STOPRECORDING, CamCmdResult.CAM_OP_SUCCESS)
        CamLogger.v(TAG, "  stopRecording!!!")
    }

    override fun OpsIsFinish(): Boolean {
//        CamOpsReq(CamCmd.CAM_NULL);
        mOpsCondittion.block()
        CamLogger.v(TAG, "Op is Finish!")
        CamOpsFinish(CamCmd.CAM_NULL, CamCmdResult.CAM_OP_SUCCESS)
        return true
    }

    fun OpsResult(): Boolean {
//        CamLogger.v(TAG, "Cam WAIT cmd finish!!!");
        return if (mCamOpResult == CamCmdResult.CAM_OP_SUCCESS) {
            CamLogger.v(TAG, "OpsResult OP($mCamCmd) SUCCESS!!!")
            true
        } else if (mCamOpResult == CamCmdResult.CAM_OP_FALSE) {
            CamLogger.e(TAG, "OpsResult CMD: $mCamCmd Result is FALSE!!!")
            false
        } else {
            CamLogger.e(TAG, "OpsResult result is INVAL!!!")
            false
        }
    }

    companion object {
        private const val TAG = "TBCamTest_Api2Camera"
        private const val CamLogger_NTH_FRAME = 30
        const val ZSL_JPEG = 0x1
        const val ZSL_YUV = 0x1 shl 1
        const val ZSL_RAW = 0x1 shl 2
        const val ZSL_YUV_REPROCESS = 0x1 shl 3
        private const val YUV_IMAGEREADER_SIZE = 8
        private const val RAW_IMAGEREADER_SIZE = 8
        private const val IMAGEWRITER_SIZE = 2
        private const val OP_TIMEOUT = 2000 // ms
    }

    init {
        mCamInfo = mCIF
        mCameraManager = mCamInfo.cameraManger
        mOpsThread = HandlerThread("CameraOpsThread")
        mOpsThread.start()
        mOpsHandler = Handler(mOpsThread.looper)
        mUtilityThread = HandlerThread("UtilityThread")
        mUtilityThread.start()
        mUtilityHandler = Handler(mUtilityThread.looper)

        mYuvImageListener = OnImageAvailableListener { reader ->
            val img = reader.acquireLatestImage()
            if (img == null) {
                CamLogger.e(TAG, "Null image returned YUV1")
                return@OnImageAvailableListener
            }
            val plane = img.planes
            val DataBuf = arrayOfNulls<ByteArray>(3)
            for (i in plane.indices) {
                val buffer = plane[i].buffer
                CamLogger.d(TAG, "ByteBuffer: " + buffer.capacity())
                if (buffer.hasArray()) {
                    DataBuf[i] = buffer.array()
                } else {
                    DataBuf[i] = ByteArray(buffer.capacity())
                    buffer[DataBuf[i]]
                }
            }
            //                    saveFile(DataBuf[0],img.getWidth(), img.getHeight(),1);
//                    saveFile(DataBuf[1],img.getWidth(), img.getHeight(),1);
            val saveData = ByteArray(DataBuf[0]!!.size + DataBuf[1]!!.size)
            System.arraycopy(DataBuf[0], 0, saveData, 0, DataBuf[0]!!.size)
            System.arraycopy(DataBuf[1], 0, saveData, DataBuf[0]!!.size, DataBuf[1]!!.size) // NV12
            CamLogger.d(TAG, "Date len:" + DataBuf[0]?.size + " " + DataBuf[1]?.size)
            mUtilityHandler.post { mCamInfo!!.saveFile(saveData, mCamInfo!!.yuvStreamSize!!.width, mCamInfo!!.yuvStreamSize!!.height, 1) }
            CamLogger.d(TAG, "YuvImageListener RECIEVE img!!!")
            if (mYuvLastReceivedImage != null) {
                mYuvLastReceivedImage!!.close()
            }
            mYuvLastReceivedImage = img
            if (++mYuvImageCounter % CamLogger_NTH_FRAME == 0) {
                CamLogger.v(TAG, "YUV buffer available, Frame #=" + mYuvImageCounter + " w=" + img.width + " h=" + img.height + " time=" + img.timestamp)
            }
            CamOpsFinish(CamCmd.CAM_TAKEPICTURE, CamCmdResult.CAM_OP_SUCCESS)
        }

        mUtilityHandler.post {
            InitializeAllTheThings()
            mAllThingsInitialized = true
            CamLogger.v(TAG, "ImageReader initialization done.")
        }
        mOpsCondittion = ConditionVariable(true)



        InitApi2Val()
    }
}