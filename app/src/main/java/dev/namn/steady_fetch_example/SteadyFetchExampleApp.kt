package dev.namn.steady_fetch_example

import android.app.Application
import dev.namn.steady_fetch.SteadyFetch

class SteadyFetchExampleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SteadyFetch.initialize(this)
    }
}

