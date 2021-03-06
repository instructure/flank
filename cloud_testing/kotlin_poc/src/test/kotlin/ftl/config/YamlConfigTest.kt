package ftl.config

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.contrib.java.lang.system.ExpectedSystemExit
import org.junit.contrib.java.lang.system.SystemErrRule
import org.junit.contrib.java.lang.system.SystemOutRule
import java.nio.file.Paths

class YamlConfigTest {

    init {
        FtlConstants.useMock = true
    }

    @Rule
    @JvmField
    val exit = ExpectedSystemExit.none()!!

    @Rule
    @JvmField
    val systemErrRule = SystemErrRule().muteForSuccessfulTests()!!

    @Rule
    @JvmField
    val systemOutRule = SystemOutRule().muteForSuccessfulTests()!!

    private fun assert(actual: Any, expected: Any) {
        assertEquals(expected, actual)
    }

    private fun getPath(path: String): String {
        return Paths.get(path).normalize().toAbsolutePath().toString()
    }

    private val yamlFile = getPath("flank.yml")
    private val appApk = getPath("../../test_app/apks/app-debug.apk")
    private val testApk = getPath("../../test_app/apks/app-debug-androidTest.apk")
    private val testName = "com.example.app.ExampleUiTest#testPasses"

    // NOTE: Change working dir to '%MODULE_WORKING_DIR%' in IntelliJ to match gradle for this test to pass.
    @Test
    fun configLoadsSuccessfully() {
        val config = YamlConfig.load(yamlFile)

        assert(getPath(config.appApk), appApk)
        assert(getPath(config.testApk), testApk)
        assert(config.rootGcsBucket, "tmp_bucket_2")

        assert(config.autoGoogleLogin, true)
        assert(config.useOrchestrator, true)
        assert(config.disablePerformanceMetrics, true)
        assert(config.disableVideoRecording, false)
        assert(config.testTimeoutMinutes, 60L)

        assert(config.testShards, 1)
        assert(config.testRuns, 1)
        assert(config.waitForResults, true)
        assert(config.testMethods, listOf(testName))
        assert(config.limitBreak, false)
    }

    private val s99_999 = 99_999

    @Test
    fun limitBreakFalseExitsOnLargeShards() {
        exit.expectSystemExitWithStatus(-1)

        val config = YamlConfig.load(yamlFile)
        config.testRuns = s99_999
        config.testShards = s99_999
        assert(config.testRuns, s99_999)
        assert(config.testShards, s99_999)
    }

    @Test
    fun limitBreakTrueAllowsLargeShards() {
        val oldConfig = YamlConfig.load(yamlFile)
        val config = YamlConfig(
                oldConfig.appApk,
                oldConfig.testApk,
                oldConfig.rootGcsBucket,
                limitBreak = true)
        config.testRuns = s99_999
        config.testShards = s99_999
        assert(config.testRuns, s99_999)
        assert(config.testShards, s99_999)
    }

    private fun configWithTestMethods(amount: Int, testShards: Int = 1): YamlConfig {
        val testMethods = mutableListOf<String>()
        repeat(amount) { testMethods.add(testName) }

        return YamlConfig(
                appApk = appApk,
                testApk = testApk,
                rootGcsBucket = "",
                testShards = testShards,
                testMethods = testMethods
        )
    }

    @Test
    fun calculateShards() {
        var config = configWithTestMethods(1)
        assert(config.testShards, 1)
        assert(config.testShardChunks.size, 1)
        assert(config.testShardChunks.first().size, 1)

        config = configWithTestMethods(155)
        assert(config.testShards, 1)
        assert(config.testShardChunks.size, 1)
        assert(config.testShardChunks.first().size, 155)

        config = configWithTestMethods(155, testShards = 40)
        assert(config.testShards, 40)
        assert(config.testShardChunks.size, 39)
        assert(config.testShardChunks.first().size, 4)
    }
}
