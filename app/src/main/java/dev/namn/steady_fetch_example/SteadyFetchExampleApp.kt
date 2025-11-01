package dev.namn.steady_fetch_example

import android.app.Application
import dev.namn.steady_fetch.models.SteadyFetch

class SteadyFetchExampleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SteadyFetch.init(this)
    }
}

