package cc.thornbird.tbcamtest

/**
 * Created by 10910661 on 2017/12/21.
 */
object CameraTime {
    // Got control in onCreate()
    var t0: Long = 0

    // Sent open() to camera.
    var t_open_start: Long = 0

    // Open from camera done.
    var t_open_end: Long = 0

    // Told camera to configure capture session.
    var t_session_go: Long = 0

    // Told session to do repeating request.
    var t_burst: Long = 0
}