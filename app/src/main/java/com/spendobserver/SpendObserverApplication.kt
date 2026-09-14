package com.spendobserver

import android.app.Application
import com.spendobserver.di.AppContainer

class SpendObserverApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
