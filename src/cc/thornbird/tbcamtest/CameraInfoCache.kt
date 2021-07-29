package cc.thornbird.tbcamtest

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Size
import android.view.SurfaceView
import android.view.View
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Created by thornbird on 2017/12/15.
 */
class CameraInfoCache {
    private val mDevicehardware = Build.HARDWARE
    private val mSystemModel = Build.MODEL
    private val mPhoneSerial = Build.SERIAL
    var camSupport = false
        private set
    var recorderSupport = false
        private set
    var storageSupport = false
        private set
    private var mCamNum: Int = 0
    var context: Context
        private set
    var cameraManger: CameraManager
        private set
    private lateinit var mCameralist: Array<String>
    private lateinit var mCamInfo: Array<CameraInfo?>
    private var mCurrentCamId: Int = -1
    var previewSurface: SurfaceView
        private set
    private var mVideoFile: File? = null

    internal inner class CameraInfo {
        lateinit var  mCameraId: String
        var facing: Int = 0
        var mCameraCharacteristics: CameraCharacteristics? = null
        var mJpegPicSupport = false
        var mYuvPicSupport = false
        var mRawPicSupport = false
        var mDepthSupport = false
        var mLargestYuvSize: Size? = null
        var mLargestJpegSize: Size? = null
        var mRawSize: Size? = null
        var mRawFormat: Int = -1
        var mDepthCloudSize: Size? = null
        lateinit var mVideoSizes: Array<Size>
        var mVideoSize: Size? = null
        var mHardwareLeve = 0
        var mMaxInputStreams: Int? = null
        var mMaxOutputProc: Int? = null
        var mMaxOutputProcStalling: Int? = null
        var mMaxOutputRaw: Int? = null
        var mMaxOutputStreams: Int? = null
        var mYuvReprocSupport = false
        var mPrivateReprocSupport = false
        var mRawSupport = false
    }

    constructor(context: Context, mSurface: SurfaceView) {
        this.context = context
        previewSurface = mSurface
        val pm = context.packageManager
        camSupport = PackageManager.PERMISSION_GRANTED == pm.checkPermission("android.permission.CAMERA", "cc.thornbird.tbcamtest")
        recorderSupport = PackageManager.PERMISSION_GRANTED == pm.checkPermission("android.permission.RECORD_AUDIO", "cc.thornbird.tbcamtest")
        storageSupport = PackageManager.PERMISSION_GRANTED == pm.checkPermission("android.permission.WRITE_EXTERNAL_STORAGE", "cc.thornbird.tbcamtest")
        cameraManger = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        mCameralist = try {
            cameraManger.cameraIdList
        } catch (e: Exception) {
            CamLogger.e(TAG, "ERROR: Could not get camera ID list / no camera information is available: $e")
            return
        }
        mCamNum = mCameralist.size
        mCamInfo = arrayOfNulls(CamMaxNum)
        for (i in 0 until CamMaxNum) mCamInfo[i] = CameraInfo()
        try {
            for (id in mCameralist) {
                CamLogger.d(TAG, "Find Camera $id")
                val mCameraCharacteristics = cameraManger.getCameraCharacteristics(id)
                mCamInfo[id.toInt()]!!.mCameraId = id
                mCamInfo[id.toInt()]!!.mCameraCharacteristics = mCameraCharacteristics
                mCamInfo[id.toInt()]!!.facing = mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING)
                getPictureSizeList(mCamInfo[id.toInt()], mCameraCharacteristics)
                getStreamInfoList(mCamInfo[id.toInt()], mCameraCharacteristics)
                val facing = mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK)
                     mCurrentCamId= id.toInt()
            }

            if(mCurrentCamId == -1) {
                CamLogger.e(TAG, "ERROR: mCurrentCamId is Wrong!Reset to 0")
                mCurrentCamId = 0
            }
        } catch (e: Exception) {
            CamLogger.e(TAG, "ERROR: Could not getCameraCharacteristics: $e")
            return
        }
        mVideoFile = File(CamPicSaveDir, "video.mp4")
    }

    constructor(mCIF: CameraInfoCache) {
        mCamNum = mCIF.mCamNum
        context = mCIF.context
        cameraManger = mCIF.cameraManger
        mCameralist = mCIF.mCameralist
        mCamInfo = mCIF.mCamInfo
        previewSurface = mCIF.previewSurface
    }

    fun printToTextView(mTextView: TextView) {
        val sb = StringBuffer()
        sb.append("硬件名称： $mDevicehardware")
        sb.append("\n版本： $mSystemModel")
        sb.append("\n硬件序列号： $mPhoneSerial")
        sb.append("\n")
        sb.append("\nCamera数量： $mCamNum")
        sb.append("\n")
        printPicSizeToStringBuffer(sb)
        mTextView.text = sb
    }

    private fun returnLargestSize(sizes: Array<Size>): Size? {
        var largestSize: Size? = null
        var area = 0
        for (j in sizes.indices) {
            if (sizes[j].height * sizes[j].width > area) {
                area = sizes[j].height * sizes[j].width
                largestSize = sizes[j]
            }
        }
        return largestSize
    }

    private fun getPictureSizeList(mCam: CameraInfo?, mCC: CameraCharacteristics) {
        if (mCC == null) return

        // Store YUV_420_888, JPEG, Raw info
        val map = mCC.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val formats = map.outputFormats
        var lowestStall = Long.MAX_VALUE
        for (i in formats.indices) {
            if (formats[i] == ImageFormat.YUV_420_888) {
                mCam?.mLargestYuvSize = returnLargestSize(map.getOutputSizes(formats[i]))
                mCam?.mYuvPicSupport = true
                CamLogger.d(TAG, "YUV support! Format:YUV_420_888! LargertSize: ${mCam?.mLargestYuvSize?.width.toString()}X${mCam?.mLargestYuvSize?.height.toString()}")
            }
            if (formats[i] == ImageFormat.JPEG) {
                mCam?.mLargestJpegSize = returnLargestSize(map.getOutputSizes(formats[i]))
                mCam?.mJpegPicSupport = true
                CamLogger.d(TAG, "JPEG support! LargertSize: ${mCam?.mLargestJpegSize?.width.toString()}X${mCam?.mLargestJpegSize?.height.toString()}")
            }
            if (formats[i] == ImageFormat.RAW10 || formats[i] == ImageFormat.RAW_SENSOR) { // TODO: Add RAW12
                val size = returnLargestSize(map.getOutputSizes(formats[i]))
                val stall = map.getOutputStallDuration(formats[i], size)
                if (stall < lowestStall) {
                    mCam?.mRawFormat = formats[i]
                    mCam?.mRawSize = size
                    lowestStall = stall
                    mCam?.mRawPicSupport = true
                    CamLogger.d(TAG, "RAW support! Format:${formats[i].toString()}! LargertSize: ${mCam?.mRawSize?.width.toString()}X${mCam?.mRawSize?.height.toString()}")
                }
                else
                    CamLogger.e(TAG, "ERROR:RAW NOT support")
            }
            if (formats[i] == ImageFormat.DEPTH_POINT_CLOUD) {
                val size = returnLargestSize(map.getOutputSizes(formats[i]))
                mCam?.mDepthCloudSize = size
                mCam!!.mDepthSupport = true
                CamLogger.d(TAG, "Depth support")
            }
        }
        mCam!!.mVideoSizes = map.getOutputSizes(MediaRecorder::class.java)
        mCam.mVideoSize = chooseVideoSize(mCam.mVideoSizes)
    }

    private fun getStreamInfoList(mCam: CameraInfo?, mCC: CameraCharacteristics?) {
        if (mCC == null) return
        mCam!!.mHardwareLeve = mCC.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        val caps = mCC.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        for (c in caps) {
            if (c == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING) mCam.mYuvReprocSupport = true else if (c == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING) mCam.mPrivateReprocSupport = true else if (c == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) mCam.mRawSupport = true
        }
        mCam.mMaxInputStreams = mCC.get(CameraCharacteristics.REQUEST_MAX_NUM_INPUT_STREAMS)
        mCam.mMaxOutputProc = mCC.get(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_PROC)
        mCam.mMaxOutputProcStalling = mCC.get(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_PROC_STALLING)
        mCam.mMaxOutputRaw = mCC.get(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_RAW)
    }

    private fun printPicSizeToStringBuffer(Sb: StringBuffer) {
        for (id in 0 until mCamNum) {
            Sb.append("""
Camera $id Facing: ${mCamInfo[id]!!.facing}""") // Back:0 Front:1
            if (mCamInfo[id]!!.mJpegPicSupport) Sb.append("""

    Jpeg Max Size： ${mCamInfo[id]!!.mLargestJpegSize.toString()}
    """.trimIndent())
            if (mCamInfo[id]!!.mYuvPicSupport) Sb.append("""

    YUV Max Size： ${mCamInfo[id]!!.mLargestYuvSize.toString()}
    """.trimIndent())
            if (mCamInfo[id]!!.mRawPicSupport) Sb.append("""
RAW Max Size： ${mCamInfo[id]!!.mRawSize.toString()} RAW format: ${mCamInfo[id]!!.mRawFormat}""")
            if (mCamInfo[id]!!.mDepthSupport) Sb.append("""

    Depth Max Size： ${mCamInfo[id]!!.mDepthCloudSize.toString()}
    """.trimIndent())
            Sb.append("""

    Hardware Level： ${mCamInfo[id]!!.mHardwareLeve}
    """.trimIndent())
            if (mCamInfo[id]!!.mYuvReprocSupport) Sb.append("\nYuvReprocess Support! ")
            if (mCamInfo[id]!!.mPrivateReprocSupport) Sb.append("\nPrivateReprocess Support! ")
            Sb.append("""

    MaxInputStreams： ${mCamInfo[id]!!.mMaxInputStreams}
    """.trimIndent())
            Sb.append("""

    MaxOutputProc： ${mCamInfo[id]!!.mMaxOutputProc}
    """.trimIndent())
            Sb.append("""

    MaxOutputProcStalling： ${mCamInfo[id]!!.mMaxOutputProcStalling}
    """.trimIndent())
            Sb.append("""

    MaxOutputRaw： ${mCamInfo[id]!!.mMaxOutputRaw}
    """.trimIndent())
            Sb.append("\n")
        }
        Sb.append("\n")
    }

    val cameraId: String?
        get() {
            return mCamInfo[mCurrentCamId]!!.mCameraId
        }

    fun setPreviewVisibility() {
        previewSurface.visibility = View.VISIBLE
    }

    fun setPreviewInVisibility() {
        previewSurface.visibility = View.INVISIBLE
    }
    val yuvStreamSupport: Boolean
        get() = mCamInfo[mCurrentCamId]!!.mYuvPicSupport

    val jpegStreamSupport: Boolean
        get() = mCamInfo[mCurrentCamId]!!.mJpegPicSupport

    val rawStreamSupport: Boolean
        get() = mCamInfo[mCurrentCamId]!!.mRawPicSupport

    val jpegStreamSize: Size?
        get() = mCamInfo[mCurrentCamId]!!.mLargestJpegSize

    val yuvStreamSize: Size?
        get() = mCamInfo[mCurrentCamId]!!.mLargestYuvSize

    val rawStreamSize: Size?
        get() = mCamInfo[mCurrentCamId]!!.mRawSize

    val rawStreamFormat: Int
        get() = mCamInfo[mCurrentCamId]!!.mRawFormat

    val videoStreamSize: Size?
        get() = mCamInfo[mCurrentCamId]!!.mVideoSize

    val videoFilePath: String
        get() {
            CamLogger.v(TAG, "Save Video FilePath:" + mVideoFile!!.absolutePath)
            return mVideoFile!!.absolutePath
        }

    fun saveFile(Data: ByteArray, w: Int, h: Int, type: Int) {
        var filename = ""
        var filetype = ""
        try {
            when (type) {
                0 -> filetype = "JPG"
                1 -> filetype = "yuv"
                2 -> filetype = "raw"
                else -> CamLogger.w(TAG, "unknow file type")
            }
            filename = String.format("%sTBCam_%dx%d_%d.%s", CamPicSaveDir, w, h, System.currentTimeMillis(), filetype)
            var file: File
            while (true) {
                file = File(filename)
                if (file.createNewFile()) {
                    break
                }
            }
            val t0 = SystemClock.uptimeMillis()
            val os: OutputStream = FileOutputStream(file)
            os.write(Data)
            os.flush()
            os.close()
            val t1 = SystemClock.uptimeMillis()
            CamLogger.d(TAG, String.format("Write data(%d) %d bytes as %s in %.3f seconds;%s", type,
                    Data.size, file, (t1 - t0) * 0.001, filename))
        } catch (e: IOException) {
            CamLogger.e(TAG, "Error creating new file: ", e)
        }
    }

    val rawSupport: Boolean
        get() = mCamInfo[mCurrentCamId]!!.mRawSupport

    val yuvReprocessSupport: Boolean
        get() = mCamInfo[mCurrentCamId]!!.mYuvReprocSupport

    companion object {
        private const val TAG = "TBCamTest_CAMINFO"
        private const val CamPicSaveDir = "/sdcard/DCIM/Camera/"
        private const val CamMaxNum = 6
        private fun chooseVideoSize(choices: Array<Size>): Size {
            for (size in choices) {
                if (size.width == size.height * 4 / 3 && size.width <= 1080) {
                    return size
                }
            }
            CamLogger.e(TAG, "Couldn't find any suitable video size")
            return choices[choices.size - 1]
        }
    }
}