package dev.namn.steady_fetch

import android.app.Application
import android.util.Log
import dev.namn.steady_fetch.impl.callbacks.SteadyFetchCallback
import dev.namn.steady_fetch.impl.controllers.SteadyFetchController
import dev.namn.steady_fetch.impl.datamodels.DownloadRequest
import dev.namn.steady_fetch.impl.di.DependencyContainer
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
class SteadyFetchTest {

    private val application = mockk<Application>(relaxed = true)
    private val container = mockk<DependencyContainer>()
    private val controller = mockk<SteadyFetchController>()
    private val callback = mockk<SteadyFetchCallback>(relaxed = true)
    private val tempDir = createTempDir(prefix = "steady-fetch")

    @Before
    fun setUp() {
        mockkObject(DependencyContainer.Companion)
        io.mockk.mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { DependencyContainer.getInstance(application) } returns container
        every { container.getSteadyFetchController() } returns controller
        resetSteadyFetch()
    }

    @After
    fun tearDown() {
        resetSteadyFetch()
        tempDir.deleteRecursively()
        unmockkAll()
    }

    @Test
    fun initialize_setsControllerAndAllowsQueueing() {
        every { controller.queueDownload(any(), any()) } returns 99L
        SteadyFetch.initialize(application)

        val request = DownloadRequest(
            url = "https://example.com/file.bin",
            downloadDir = tempDir,
            fileName = "file.bin"
        )

        val result = SteadyFetch.queueDownload(request, callback)
        assertEquals(99L, result)
    }

    @Test(expected = Exception::class)
    fun queueDownload_throwsWhenNotInitialized() {
        val request = DownloadRequest(
            url = "https://example.com/file.bin",
            downloadDir = tempDir,
            fileName = "file.bin"
        )
        SteadyFetch.queueDownload(request, callback)
    }

    @Test
    fun cancelDownload_delegatesToController() {
        SteadyFetch.initialize(application)
        every { controller.cancel(12L) } returns true

        val result = SteadyFetch.cancelDownload(12L)
        assertTrue(result)
    }

    @Test(expected = RuntimeException::class)
    fun initialize_wrapsFailuresAsRuntimeException() {
        every { DependencyContainer.getInstance(application) } throws IllegalStateException("failed")
        SteadyFetch.initialize(application)
    }

    private fun resetSteadyFetch() {
        val controllerField = SteadyFetch::class.java.getDeclaredField("steadyFetchController")
        controllerField.isAccessible = true
        controllerField.set(null, null)

        val containerField = SteadyFetch::class.java.getDeclaredField("dependencyContainer")
        containerField.isAccessible = true
        containerField.set(null, null)

        val initializedField = SteadyFetch::class.java.getDeclaredField("isInitialized")
        initializedField.isAccessible = true
        val flag = initializedField.get(null) as AtomicBoolean
        flag.set(false)
    }
}

