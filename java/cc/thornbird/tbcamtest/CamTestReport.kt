package cc.thornbird.tbcamtest

/**
 * Created by thornbird on 2017/12/29.
 */
class CamTestReport(TestCaseNum: Int) {
    private val mTestResult: Array<TestResult?>
    private var mTestNum = 0
    private val mTotalTestCases: Int
    fun addTestResult(Test: String?, result: Boolean?) {
        if (mTestNum >= mTotalTestCases) {
            CamLogger.e(TAG, "ERROR TestNum is more than REQUEST TestCaseNum($mTotalTestCases)!!!")
            return
        }
        mTestResult[mTestNum++]!!.setValue(Test, result)
    }

    fun printTestResult() {
        for (i in 0 until mTotalTestCases) mTestResult[i]!!.printValue()
    }

    fun clearLastResult() {
        CamLogger.v(TAG, "Reset TestNum recorder!")
        mTestNum = 0
    }

    private inner class TestResult {
        var mCaseName: String? = null
        var mResult: Boolean? = null
        fun setValue(Test: String?, result: Boolean?) {
            mCaseName = Test
            mResult = result
        }

        fun printValue() {
            CamLogger.e(TAG, "TestCase $mCaseName: $mResult")
        }
    }

    companion object {
        private const val TAG = "TBCamTest_CamTestReport"
    }

    init {
        mTestResult = arrayOfNulls(TestCaseNum)
        mTotalTestCases = TestCaseNum
        for (i in 0 until mTotalTestCases) mTestResult[i] = TestResult()
        mTestNum = 0
    }
}