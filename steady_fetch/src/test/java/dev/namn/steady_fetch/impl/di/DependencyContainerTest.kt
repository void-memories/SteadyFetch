package dev.namn.steady_fetch.impl.di

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DependencyContainerTest {

    private val application: Application = ApplicationProvider.getApplicationContext()

    @Before
    @After
    fun resetSingleton() {
        val instanceField = DependencyContainer::class.java.getDeclaredField("instance")
        instanceField.isAccessible = true
        instanceField.set(null, null)
    }

    @Test
    fun getInstance_returnsSameSingleton() {
        val first = DependencyContainer.getInstance(application)
        val second = DependencyContainer.getInstance(application)
        assertSame(first, second)
    }

    @Test
    fun getSteadyFetchController_returnsSameInstance() {
        val container = DependencyContainer.getInstance(application)
        val controller = container.getSteadyFetchController()
        assertSame(controller, container.getSteadyFetchController())
    }
}

