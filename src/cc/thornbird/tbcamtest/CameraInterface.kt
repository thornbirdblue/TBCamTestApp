package cc.thornbird.tbcamtest

import android.view.Surface

/**
 * Created by thornbird on 2017/12/21.
 */
interface CameraInterface {
    fun openCamera()
    fun closeCamera()
    fun startPreview(surface: Surface)
    fun startPreview(surface: Surface, ZslMode: Boolean, ZslFlag: Int)
    fun startRecordingPreview(surface: Surface)
    fun takePicture()
    fun startRecording()
    fun stopRecording()
    fun OpsIsFinish(): Boolean
}