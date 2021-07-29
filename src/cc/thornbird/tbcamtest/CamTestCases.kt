package cc.thornbird.tbcamtest

/**
 * Created by thornbird on 2017/12/21.
 */
interface CamTestCases {
    fun doRunTestCases()
    fun stop()
    fun testIsFinish(): Boolean
}