package com.sambhavdwivedi.callin

import android.app.Application
import com.sambhavdwivedi.callin.core.di.AppContainer

class CallinApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
