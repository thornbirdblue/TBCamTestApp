package cc.thornbird.tbcamtest

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import cc.thornbird.tbcamtest.CamTestMode
import cc.thornbird.tbcamtest.CamTestMode.CamTestCallBack

/**
 * Created by thornbird on 2017/12/15.
 */
class TBCamTest : Activity(), CamTestCallBack {
    private lateinit var mInfoLab: TextView
    private lateinit var mCamInfo: CameraInfoCache
    private lateinit var mSurface: SurfaceView
    private lateinit var mCamTestMode: CamTestMode
    private var mhandler: Handler? = null
    private var mBaseTestButton: Button? = null
    private var mBaseFuncTestButton: Button? = null
    private var mFeatTestButton: Button? = null
    private var mAutoTestButton: Button? = null
    private var mPerfTestButton: Button? = null
    private var mStressTestButton: Button? = null
    private var mStopTestButton: Button? = null
    private lateinit var mWakeLock: WakeLock
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mInfoLab = findViewById<View>(R.id.InfoLab) as TextView
        mSurface = findViewById<View>(R.id.preview_view) as SurfaceView
        mBaseTestButton = findViewById<View>(R.id.base_test) as Button
        mBaseFuncTestButton = findViewById<View>(R.id.base_func_test) as Button
        mFeatTestButton = findViewById<View>(R.id.feature_test) as Button
        mAutoTestButton = findViewById<View>(R.id.auto_test) as Button
        mPerfTestButton = findViewById<View>(R.id.perf_test) as Button
        mStressTestButton = findViewById<View>(R.id.stree_test) as Button
        mStopTestButton = findViewById<View>(R.id.stop_test) as Button
        mBaseTestButton!!.setOnClickListener { doTestNum(CamTestMode.TM_BaseTest_Mode) }
        mBaseFuncTestButton!!.setOnClickListener { doTestNum(CamTestMode.TM_BaseFuncTest_Mode) }
        mFeatTestButton!!.setOnClickListener { doTestNum(CamTestMode.TM_FeatureTest_Mode) }
        mAutoTestButton!!.setOnClickListener { doTestNum(CamTestMode.TM_AutoTest_Mode) }
        mPerfTestButton!!.setOnClickListener { doTestNum(CamTestMode.TM_PerfTest_Mode) }
        mStressTestButton!!.setOnClickListener { doTestNum(CamTestMode.TM_StressTest_Mode) }
        mStopTestButton!!.setOnClickListener { doStopTest() }
        mhandler = Handler()
    }

    private fun doTestNum(TestNum: Int) {
        if (false == checkCameraPermission()) {
            CamLogger.e(TAG, "Can't have CAMERA Permission.Can't Test!")
            return
        }
        when (TestNum) {
            CamTestMode.TM_BaseTest_Mode -> doBaseTest()
            CamTestMode.TM_BaseFuncTest_Mode -> doBaseFuncTest()
            CamTestMode.TM_FeatureTest_Mode -> doFeatTest()
            CamTestMode.TM_AutoTest_Mode -> doAutoTest()
            CamTestMode.TM_PerfTest_Mode -> doPerfTest()
            CamTestMode.TM_StressTest_Mode -> doStressTest()
            else -> Toast.makeText(this, "Error Test Num!!!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkCameraPermission(): Boolean {
        val CamStorage = mCamInfo!!.storageSupport
        val CamSupport = mCamInfo!!.camSupport
        val RecorderSupport = mCamInfo!!.recorderSupport
        if (CamStorage == false) {
            Toast.makeText(this, "Can't have STORAGE Permission.", Toast.LENGTH_SHORT).show()
            return false
        }
        if (CamSupport == false) Toast.makeText(this, "Can't have CAMERA Permission.", Toast.LENGTH_SHORT).show()
        if (RecorderSupport == false) Toast.makeText(this, "Can't have Recording Permission.", Toast.LENGTH_SHORT).show()
        return CamSupport
    }

    private fun doBaseTest() {
        setButtonDisable()
        CamLogger.d(TAG, "Do Base Test!!!")
        mCamTestMode = CamTestMode(CamTestMode.TM_BaseTest_Mode, mCamInfo, this)
        mCamTestMode.run()
    }

    private fun doBaseFuncTest() {
        setButtonDisable()
        CamLogger.d(TAG, "Do Base Func Test!!!")
        mCamTestMode = CamTestMode(CamTestMode.TM_BaseFuncTest_Mode, mCamInfo, this)
        mCamTestMode.run()
    }

    private fun doFeatTest() {
        setButtonDisable()
        CamLogger.d(TAG, "Do Feature Test!!!")
        mCamTestMode = CamTestMode(CamTestMode.TM_FeatureTest_Mode, mCamInfo, this)
        mCamTestMode.run()
    }

    private fun doAutoTest() {
        setButtonDisable()
        CamLogger.d(TAG, "Do Auto Test!!!")
        mCamTestMode = CamTestMode(CamTestMode.TM_AutoTest_Mode, mCamInfo, this)
        mCamTestMode.run()
    }

    private fun doPerfTest() {
        setButtonDisable()
        CamLogger.d(TAG, "Do Perf Test!!!")
        mCamTestMode = CamTestMode(CamTestMode.TM_PerfTest_Mode, mCamInfo, this)
        mCamTestMode.run()
    }

    private fun doStressTest() {
        setButtonDisable()
        CamLogger.d(TAG, "Do Stress Test!!!")
        mCamTestMode = CamTestMode(CamTestMode.TM_StressTest_Mode, mCamInfo, this)
        mCamTestMode.run()
    }

    private fun setButtonDisable() {
        mBaseTestButton!!.isEnabled = false
        mBaseFuncTestButton!!.isEnabled = false
        mFeatTestButton!!.isEnabled = false
        mAutoTestButton!!.isEnabled = false
        mPerfTestButton!!.isEnabled = false
        mStressTestButton!!.isEnabled = false
    }

    private fun setButtonEnable() {
        mBaseTestButton!!.isEnabled = true
        mBaseFuncTestButton!!.isEnabled = true
        mFeatTestButton!!.isEnabled = true
        mAutoTestButton!!.isEnabled = true
        mPerfTestButton!!.isEnabled = true
        mStressTestButton!!.isEnabled = true
    }

    private fun doStopTest() {
        if (mCamTestMode != null) mCamTestMode!!.stop()
        setButtonEnable()
    }

    override fun onResume() {
        super.onResume()
        mWakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, TAG)
        mWakeLock.acquire()
    }

    override fun onStart() {
        super.onStart()
        mCamInfo = CameraInfoCache(this, mSurface)
        mCamInfo!!.printToTextView(mInfoLab)
        setButtonEnable()
    }

    override fun onPause() {
        super.onPause()
        CamLogger.v(TAG, "onPause")
        if (null != mWakeLock) {
            mWakeLock!!.release()
        }
        if (mCamTestMode != null) mCamTestMode!!.stop()
        mCamInfo!!.setPreviewInVisibility()
    }

    override fun CamTestIsFinish(): Boolean {
        Toast.makeText(this, "Test is Finish!!!", Toast.LENGTH_SHORT).show()
        mhandler!!.post { setButtonEnable() }
        return true
    }

    companion object {
        private const val TAG = "TBCamTest"
    }
}